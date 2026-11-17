package org.mewx.wenku8.v2.data.repository

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.dao.SearchHistoryDao
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.remote.api.WenkuApiResult
import org.mewx.wenku8.v2.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.v2.domain.model.DiscoveryFeedErrorKind
import org.mewx.wenku8.v2.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.v2.domain.model.FeedScrollPosition
import org.mewx.wenku8.v2.domain.model.LatestFeedCategory
import org.mewx.wenku8.v2.domain.model.OnlineSearchExecutionState
import org.mewx.wenku8.v2.domain.model.PaginatedNovelFeedState
import org.mewx.wenku8.v2.domain.model.RankingMetric
import org.mewx.wenku8.v2.domain.model.RankingPeriod
import org.mewx.wenku8.v2.domain.model.UnifiedSearchMode
import java.util.concurrent.ConcurrentHashMap

/**
 * Coordinates Discovery feeds (`最新` & `排行`), completeness-monotonic card metadata caching,
 * instant `< 1ms` local Room bookshelf filtering, Room search history, and the **auto-queued `5s`
 * online search cooldown**.
 */
class DiscoveryRepository(
    private val database: Wenku8Database,
    private val apiClient: WenkuCatalogGateway,
    private val monotonicClockMillis: () -> Long = { SystemClock.elapsedRealtime() },
) {

    companion object {
        const val ONLINE_SEARCH_COOLDOWN_MILLIS: Long = 5_000L
        const val COOLDOWN_TICK_INTERVAL_MILLIS: Long = 100L
        const val MAX_SEARCH_HISTORY_ITEMS: Int = SearchHistoryDao.DEFAULT_MAX_HISTORY_ITEMS
        private const val SEARCH_HYDRATION_CONCURRENCY: Int = 4
        private const val INITIAL_HYDRATION_BATCH_SIZE: Int = 12
        private val TAG_SPLITTER = Regex("[\\s,/]+")

        fun parseDirectAidOrNull(rawQuery: String): Int? {
            val normalized = rawQuery.trim()
                .removePrefix("#")
                .removePrefix("aid")
                .removePrefix("AID")
                .trim()
            return normalized.toIntOrNull()?.takeIf { it > 0 }
        }
    }

    private val feedStates = ConcurrentHashMap<Wenku8API.NovelSortedBy, MutableStateFlow<PaginatedNovelFeedState>>()
    private val feedLoadMutexes = ConcurrentHashMap<Wenku8API.NovelSortedBy, Mutex>()
    private val feedScrollPositions = ConcurrentHashMap<Wenku8API.NovelSortedBy, FeedScrollPosition>()

    @Volatile
    var activeLatestCategory: LatestFeedCategory = LatestFeedCategory.RECENT_UPDATES

    @Volatile
    var activeRankingMetric: RankingMetric = RankingMetric.VISITS

    @Volatile
    var activeRankingPeriod: RankingPeriod = RankingPeriod.WEEK

    /**
     * Process-wide completeness-monotonic novel summary cache.
     *
     * Merges incoming fields with any previously cached record so a sparser endpoint (such as
     * `do=bookinfo` which omits `Tags`) never wipes out tags or synopsis already fetched from
     * `novellist` or Room.
     */
    private val cardCache = ConcurrentHashMap<Int, DiscoveryNovelCardItem>()

    private val searchMutex = Mutex()
    private var lastOnlineSearchTimestampMillis: Long? = null
    private var activeSearchJob: Job? = null
    private val _onlineSearchState = MutableStateFlow(OnlineSearchExecutionState())
    val onlineSearchState: StateFlow<OnlineSearchExecutionState> = _onlineSearchState.asStateFlow()

    fun observeFeedState(sortedBy: Wenku8API.NovelSortedBy): StateFlow<PaginatedNovelFeedState> {
        return getOrCreateFeedFlow(sortedBy).asStateFlow()
    }

    fun getFeedScrollPosition(sortedBy: Wenku8API.NovelSortedBy): FeedScrollPosition {
        return feedScrollPositions[sortedBy] ?: FeedScrollPosition()
    }

    fun updateFeedScrollPosition(
        sortedBy: Wenku8API.NovelSortedBy,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ) {
        feedScrollPositions[sortedBy] = FeedScrollPosition(
            firstVisibleItemIndex = firstVisibleItemIndex.coerceAtLeast(0),
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset.coerceAtLeast(0),
        )
    }

    /**
     * Loads page 1 if the feed has not loaded yet, or resets & reloads page 1 when [forceRefresh]
     * is true. Concurrent duplicate calls for the same [sortedBy] are coalesced safely.
     */
    suspend fun loadInitialOrRefreshFeed(
        sortedBy: Wenku8API.NovelSortedBy,
        appLanguage: Wenku8API.AppLanguage,
        forceRefresh: Boolean = false,
    ) {
        val stateFlow = getOrCreateFeedFlow(sortedBy)
        val mutex = feedLoadMutexes.getOrPut(sortedBy) { Mutex() }

        mutex.withLock {
            val snapshot = stateFlow.value
            if (!forceRefresh && snapshot.items.isNotEmpty()) {
                return
            }

            stateFlow.update { current ->
                if (current.items.isEmpty()) {
                    current.copy(
                        isInitialLoading = true,
                        isRefreshing = false,
                        isAppendingNextPage = false,
                        errorKind = null,
                        errorServerCode = null,
                    )
                } else {
                    current.copy(
                        isInitialLoading = false,
                        isRefreshing = true,
                        isAppendingNextPage = false,
                        errorKind = null,
                        errorServerCode = null,
                    )
                }
            }

            val bookshelfAidSet = database.novelDao().getBookshelfNovelIds().toHashSet()

            when (val response = apiClient.fetchNovelListPage(sortedBy, pageNumber = 1, appLanguage = appLanguage)) {
                is WenkuApiResult.Success -> {
                    val mappedItems = response.data.items.map { parsed ->
                        mergeAndCacheParsedItem(parsed, bookshelfAidSet.contains(parsed.aid))
                    }
                    if (forceRefresh) {
                        feedScrollPositions[sortedBy] = FeedScrollPosition(0, 0)
                    }
                    stateFlow.update { current ->
                        current.copy(
                            items = mappedItems,
                            loadedPage = 1,
                            totalPages = response.data.totalPages.coerceAtLeast(if (mappedItems.isNotEmpty()) 1 else 0),
                            isInitialLoading = false,
                            isRefreshing = false,
                            isAppendingNextPage = false,
                            errorKind = null,
                            errorServerCode = null,
                        )
                    }
                }
                else -> {
                    val (errorKind, serverCode) = response.toFeedError()
                    stateFlow.update { current ->
                        current.copy(
                            isInitialLoading = false,
                            isRefreshing = false,
                            isAppendingNextPage = false,
                            errorKind = errorKind,
                            errorServerCode = serverCode,
                        )
                    }
                }
            }
        }
    }

    /**
     * Appends `loadedPage + 1` to [sortedBy]. If the network fails, existing items and [loadedPage]
     * remain untouched so the user never loses scroll context or skips a page on retry.
     */
    suspend fun loadNextFeedPage(
        sortedBy: Wenku8API.NovelSortedBy,
        appLanguage: Wenku8API.AppLanguage,
    ) {
        val stateFlow = getOrCreateFeedFlow(sortedBy)
        val mutex = feedLoadMutexes.getOrPut(sortedBy) { Mutex() }

        mutex.withLock {
            val snapshot = stateFlow.value
            if (!snapshot.canLoadMorePages) {
                return
            }
            val targetPage = snapshot.loadedPage + 1

            stateFlow.update {
                it.copy(
                    isAppendingNextPage = true,
                    errorKind = null,
                    errorServerCode = null,
                )
            }

            val bookshelfAidSet = database.novelDao().getBookshelfNovelIds().toHashSet()

            when (val response = apiClient.fetchNovelListPage(sortedBy, pageNumber = targetPage, appLanguage = appLanguage)) {
                is WenkuApiResult.Success -> {
                    val incomingCards = response.data.items.map { parsed ->
                        mergeAndCacheParsedItem(parsed, bookshelfAidSet.contains(parsed.aid))
                    }
                    stateFlow.update { current ->
                        val mergedByAid = LinkedHashMap<Int, DiscoveryNovelCardItem>(
                            current.items.size + incomingCards.size,
                        )
                        for (existing in current.items) {
                            mergedByAid[existing.aid] = existing
                        }
                        for (incoming in incomingCards) {
                            mergedByAid[incoming.aid] = incoming
                        }
                        current.copy(
                            items = mergedByAid.values.toList(),
                            loadedPage = targetPage,
                            totalPages = maxOf(current.totalPages, response.data.totalPages),
                            isAppendingNextPage = false,
                            errorKind = null,
                            errorServerCode = null,
                        )
                    }
                }
                else -> {
                    val (errorKind, serverCode) = response.toFeedError()
                    stateFlow.update { current ->
                        current.copy(
                            isAppendingNextPage = false,
                            errorKind = errorKind,
                            errorServerCode = serverCode,
                        )
                    }
                }
            }
        }
    }

    @Volatile
    private var cachedLocalShelfQuery: String = ""

    @Volatile
    private var cachedLocalShelfMatches: List<DiscoveryNovelCardItem> = emptyList()

    fun latestLocalShelfMatchesFor(rawQuery: String): List<DiscoveryNovelCardItem> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) return emptyList()
        return if (trimmed == cachedLocalShelfQuery) cachedLocalShelfMatches else emptyList()
    }

    /**
     * Instant `< 1ms` local Room bookshelf search (`0ms` network, immune to server cooldown).
     */
    suspend fun filterLocalLibraryInstant(rawQuery: String): List<DiscoveryNovelCardItem> {
        val trimmed = rawQuery.trim()
        if (trimmed.isEmpty()) {
            cachedLocalShelfQuery = ""
            cachedLocalShelfMatches = emptyList()
            return emptyList()
        }
        val directAidCandidate = parseDirectAidOrNull(trimmed)?.toString() ?: trimmed
        val matches = database.novelDao().observeLocalBookshelfMatches(trimmed).first()
        val aidMatches = if (directAidCandidate != trimmed) {
            database.novelDao().observeLocalBookshelfMatches(directAidCandidate).first()
        } else {
            emptyList()
        }
        val resolved = (matches + aidMatches)
            .distinctBy { it.aid }
            .map { entity -> entity.toCardItem() }
        cachedLocalShelfQuery = trimmed
        cachedLocalShelfMatches = resolved
        return resolved
    }

    fun observeSearchHistory(): Flow<List<String>> {
        return database.searchHistoryDao()
            .observeRecentHistory(limit = MAX_SEARCH_HISTORY_ITEMS)
            .map { entries -> entries.map { it.query } }
    }

    suspend fun recordSearchHistory(
        query: String,
        mode: UnifiedSearchMode = UnifiedSearchMode.NOVEL_TITLE,
    ) {
        database.searchHistoryDao().recordSearchAndTrim(
            query = query,
            searchType = mode.name,
            searchedAtMs = monotonicClockMillis().coerceAtLeast(1L),
            maxEntries = MAX_SEARCH_HISTORY_ITEMS,
        )
    }

    suspend fun deleteSearchHistoryItem(query: String) {
        database.searchHistoryDao().deleteHistoryEntry(query)
    }

    suspend fun clearAllSearchHistory() {
        database.searchHistoryDao().clearAllHistory()
    }

    /**
     * Submits an online search inside [scope], automatically enforcing Wenku8's `5s` server search
     * cooldown via a superseding single-slot countdown queue:
     *
     * - If `mode == DIRECT_AID`, fetches `do=meta` directly with **zero `5s` search cooldown**.
     * - For `NOVEL_TITLE` / `AUTHOR_NAME`, if `< 5,000ms` has elapsed since the previous
     *   `action=search` network call, cancels any earlier queued query, publishes live
     *   `remainingCooldownMillis` ticks (`100ms` cadence), and executes automatically once the
     *   window clears.
     */
    fun submitOnlineSearch(
        scope: CoroutineScope,
        rawQuery: String,
        mode: UnifiedSearchMode,
        appLanguage: Wenku8API.AppLanguage,
    ) {
        val trimmedQuery = rawQuery.trim()
        if (trimmedQuery.isEmpty()) return

        activeSearchJob?.cancel()
        activeSearchJob = scope.launch {
            recordSearchHistory(trimmedQuery, mode)

            val resolvedAid = parseDirectAidOrNull(trimmedQuery)
            if (mode == UnifiedSearchMode.DIRECT_AID && resolvedAid != null) {
                executeDirectAidLookup(trimmedQuery, resolvedAid, appLanguage)
                return@launch
            }

            val onlineField = mode.toOnlineSearchFieldOrNull() ?: return@launch

            // Wait out any remaining 5s server cooldown while emitting live countdown updates.
            while (true) {
                val remaining = computeRemainingCooldownMillis()
                if (remaining <= 0L) break
                _onlineSearchState.update {
                    OnlineSearchExecutionState(
                        submittedQuery = trimmedQuery,
                        mode = mode,
                        isQueuedForCooldown = true,
                        remainingCooldownMillis = remaining,
                        totalCooldownMillis = ONLINE_SEARCH_COOLDOWN_MILLIS,
                        isSearchingOnline = false,
                        results = it.results,
                        hasCompletedSearch = false,
                    )
                }
                delay(minOf(COOLDOWN_TICK_INTERVAL_MILLIS, remaining))
            }

            // Stamp cooldown start right as the network request is dispatched.
            searchMutex.withLock {
                lastOnlineSearchTimestampMillis = monotonicClockMillis()
            }

            _onlineSearchState.value = OnlineSearchExecutionState(
                submittedQuery = trimmedQuery,
                mode = mode,
                isQueuedForCooldown = false,
                remainingCooldownMillis = 0L,
                totalCooldownMillis = ONLINE_SEARCH_COOLDOWN_MILLIS,
                isSearchingOnline = true,
            )

            when (val aidListResponse = apiClient.searchNovelsByField(trimmedQuery, onlineField, appLanguage)) {
                is WenkuApiResult.Success -> {
                    val aids = aidListResponse.data
                    if (aids.isEmpty()) {
                        _onlineSearchState.value = OnlineSearchExecutionState(
                            submittedQuery = trimmedQuery,
                            mode = mode,
                            hasCompletedSearch = true,
                            results = emptyList(),
                        )
                        return@launch
                    }

                    val bookshelfMap = database.novelDao().observeBookshelfNovels().first()
                        .associateBy { it.aid }

                    // Immediately render cached/Room metadata or clean loading cards, then hydrate.
                    val initialCards = aids.map { aid ->
                        bookshelfMap[aid]?.toCardItem()
                            ?: cardCache[aid]?.copy(inLocalBookshelf = bookshelfMap.containsKey(aid))
                            ?: DiscoveryNovelCardItem(
                                aid = aid,
                                title = "AID #$aid",
                                author = "",
                                bookStatus = "",
                                lastUpdate = "",
                                tags = emptyList(),
                                introShort = "",
                                coverUrl = apiClient.resolveCoverUrl(aid),
                                inLocalBookshelf = false,
                                isHydratingMetadata = true,
                            )
                    }

                    _onlineSearchState.value = OnlineSearchExecutionState(
                        submittedQuery = trimmedQuery,
                        mode = mode,
                        isSearchingOnline = false,
                        hasCompletedSearch = true,
                        results = initialCards,
                    )

                    hydrateSparseSearchCards(
                        submittedQuery = trimmedQuery,
                        mode = mode,
                        cards = initialCards,
                        bookshelfAids = bookshelfMap.keys,
                        appLanguage = appLanguage,
                    )
                }
                else -> {
                    val (errorKind, serverCode) = aidListResponse.toFeedError()
                    _onlineSearchState.value = OnlineSearchExecutionState(
                        submittedQuery = trimmedQuery,
                        mode = mode,
                        isSearchingOnline = false,
                        hasCompletedSearch = true,
                        errorKind = errorKind,
                        errorServerCode = serverCode,
                    )
                }
            }
        }
    }

    fun cancelPendingOnlineSearch() {
        activeSearchJob?.cancel()
        activeSearchJob = null
        _onlineSearchState.update {
            it.copy(
                isQueuedForCooldown = false,
                remainingCooldownMillis = 0L,
                isSearchingOnline = false,
            )
        }
    }

    fun resetOnlineSearchSession() {
        activeSearchJob?.cancel()
        activeSearchJob = null
        cachedLocalShelfQuery = ""
        cachedLocalShelfMatches = emptyList()
        _onlineSearchState.value = OnlineSearchExecutionState()
    }

    private suspend fun executeDirectAidLookup(
        submittedQuery: String,
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ) {
        _onlineSearchState.value = OnlineSearchExecutionState(
            submittedQuery = submittedQuery,
            mode = UnifiedSearchMode.DIRECT_AID,
            isSearchingOnline = true,
        )

        val localNovel = database.novelDao().getNovelByAid(aid)
        when (val metaResult = apiClient.fetchNovelFullMeta(aid, appLanguage)) {
            is WenkuApiResult.Success -> {
                val card = metaResult.data.toCardItem(
                    inBookshelf = localNovel?.inBookshelf ?: false,
                )
                cardCache[aid] = mergeCardsMonotonically(cardCache[aid], card)
                _onlineSearchState.value = OnlineSearchExecutionState(
                    submittedQuery = submittedQuery,
                    mode = UnifiedSearchMode.DIRECT_AID,
                    hasCompletedSearch = true,
                    results = listOf(cardCache[aid] ?: card),
                )
            }
            else -> {
                if (localNovel != null) {
                    _onlineSearchState.value = OnlineSearchExecutionState(
                        submittedQuery = submittedQuery,
                        mode = UnifiedSearchMode.DIRECT_AID,
                        hasCompletedSearch = true,
                        results = listOf(localNovel.toCardItem()),
                    )
                } else {
                    val (errorKind, serverCode) = metaResult.toFeedError()
                    _onlineSearchState.value = OnlineSearchExecutionState(
                        submittedQuery = submittedQuery,
                        mode = UnifiedSearchMode.DIRECT_AID,
                        hasCompletedSearch = true,
                        errorKind = errorKind,
                        errorServerCode = serverCode,
                    )
                }
            }
        }
    }

    private suspend fun hydrateSparseSearchCards(
        submittedQuery: String,
        mode: UnifiedSearchMode,
        cards: List<DiscoveryNovelCardItem>,
        bookshelfAids: Set<Int>,
        appLanguage: Wenku8API.AppLanguage,
    ) {
        val candidatesToHydrate = cards
            .filter { it.isHydratingMetadata }
            .take(INITIAL_HYDRATION_BATCH_SIZE)
        if (candidatesToHydrate.isEmpty()) return

        val semaphore = Semaphore(SEARCH_HYDRATION_CONCURRENCY)
        val hydratedByAid = coroutineScope {
            candidatesToHydrate.map { placeholder ->
                async {
                    semaphore.withPermit {
                        val response = apiClient.fetchNovelShortInfo(placeholder.aid, appLanguage)
                        if (response is WenkuApiResult.Success) {
                            placeholder.aid to mergeAndCacheParsedItem(
                                parsed = response.data,
                                inBookshelf = bookshelfAids.contains(placeholder.aid),
                            )
                        } else {
                            placeholder.aid to placeholder.copy(isHydratingMetadata = false)
                        }
                    }
                }
            }.awaitAll().toMap()
        }

        _onlineSearchState.update { current ->
            if (current.submittedQuery != submittedQuery || current.mode != mode) {
                current
            } else {
                current.copy(
                    results = current.results.map { existing ->
                        hydratedByAid[existing.aid] ?: existing
                    },
                )
            }
        }
    }

    private suspend fun computeRemainingCooldownMillis(): Long {
        return searchMutex.withLock {
            val lastTimestamp = lastOnlineSearchTimestampMillis ?: return@withLock 0L
            val elapsed = (monotonicClockMillis() - lastTimestamp).coerceAtLeast(0L)
            (ONLINE_SEARCH_COOLDOWN_MILLIS - elapsed).coerceAtLeast(0L)
        }
    }

    private fun getOrCreateFeedFlow(
        sortedBy: Wenku8API.NovelSortedBy,
    ): MutableStateFlow<PaginatedNovelFeedState> {
        return feedStates.getOrPut(sortedBy) {
            MutableStateFlow(PaginatedNovelFeedState(sortedBy = sortedBy))
        }
    }

    private fun mergeAndCacheParsedItem(
        parsed: ParsedNovelListItem,
        inBookshelf: Boolean,
    ): DiscoveryNovelCardItem {
        val incoming = DiscoveryNovelCardItem(
            aid = parsed.aid,
            title = parsed.title.ifBlank { "AID #${parsed.aid}" },
            author = parsed.author,
            bookStatus = parsed.bookStatus,
            lastUpdate = parsed.lastUpdate,
            tags = splitTags(parsed.tagsCsv),
            introShort = parsed.introShort,
            coverUrl = apiClient.resolveCoverUrl(parsed.aid),
            inLocalBookshelf = inBookshelf,
            isHydratingMetadata = false,
        )
        val merged = mergeCardsMonotonically(cardCache[parsed.aid], incoming)
        cardCache[parsed.aid] = merged
        return merged
    }

    private fun mergeCardsMonotonically(
        existing: DiscoveryNovelCardItem?,
        incoming: DiscoveryNovelCardItem,
    ): DiscoveryNovelCardItem {
        if (existing == null) return incoming
        val preferIncomingTitle = incoming.title.isNotBlank() && !incoming.title.startsWith("AID #")
        return DiscoveryNovelCardItem(
            aid = incoming.aid,
            title = if (preferIncomingTitle) incoming.title else existing.title,
            author = incoming.author.ifBlank { existing.author },
            bookStatus = incoming.bookStatus.ifBlank { existing.bookStatus },
            lastUpdate = incoming.lastUpdate.ifBlank { existing.lastUpdate },
            tags = if (incoming.tags.isNotEmpty()) incoming.tags else existing.tags,
            introShort = incoming.introShort.ifBlank { existing.introShort },
            coverUrl = incoming.coverUrl.ifBlank { existing.coverUrl },
            inLocalBookshelf = incoming.inLocalBookshelf,
            isHydratingMetadata = false,
        )
    }

    private fun NovelEntity.toCardItem(): DiscoveryNovelCardItem {
        val resolvedIntro = introShort.ifBlank {
            fullIntro.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty()
        }
        return DiscoveryNovelCardItem(
            aid = aid,
            title = title,
            author = author,
            bookStatus = bookStatus,
            lastUpdate = lastUpdate,
            tags = splitTags(tagsCsv),
            introShort = resolvedIntro,
            coverUrl = apiClient.resolveCoverUrl(aid),
            inLocalBookshelf = inBookshelf,
            isHydratingMetadata = false,
        )
    }

    private fun ParsedNovelFullMeta.toCardItem(inBookshelf: Boolean): DiscoveryNovelCardItem {
        return DiscoveryNovelCardItem(
            aid = aid,
            title = title,
            author = author,
            bookStatus = bookStatus,
            lastUpdate = lastUpdate,
            tags = listOfNotNull(pressId.takeIf { it.isNotBlank() }),
            introShort = latestSectionName,
            coverUrl = apiClient.resolveCoverUrl(aid),
            inLocalBookshelf = inBookshelf,
            isHydratingMetadata = false,
        )
    }

    private fun splitTags(rawTagsCsv: String): List<String> {
        if (rawTagsCsv.isBlank()) return emptyList()
        return rawTagsCsv
            .split(TAG_SPLITTER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun WenkuApiResult<*>.toFeedError(): Pair<DiscoveryFeedErrorKind, Int?> {
        return when (this) {
            is WenkuApiResult.ApiNotConfigured -> DiscoveryFeedErrorKind.API_NOT_CONFIGURED to null
            is WenkuApiResult.NetworkError -> DiscoveryFeedErrorKind.NETWORK_UNREACHABLE to null
            is WenkuApiResult.ServerErrorCode -> DiscoveryFeedErrorKind.SERVER_ERROR to numericCode
            is WenkuApiResult.ParseError -> DiscoveryFeedErrorKind.PARSE_FAILURE to null
            is WenkuApiResult.Success -> DiscoveryFeedErrorKind.PARSE_FAILURE to null
        }
    }
}
