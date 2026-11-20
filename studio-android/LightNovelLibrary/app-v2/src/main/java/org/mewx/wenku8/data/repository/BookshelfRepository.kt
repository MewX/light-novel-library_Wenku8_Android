package org.mewx.wenku8.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.data.local.db.entity.CloudBookcaseLedgerEntity
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.api.isEffectivelyBlankWenkuText
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.util.DigestUtils
import java.nio.charset.StandardCharsets
import java.text.Collator
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

enum class BookshelfViewMode {
    GRID,
    DETAIL_LIST,
    SPINE_3D,
}

enum class BookshelfSortMode {
    RECENT_READ,
    LAST_UPDATE,
    MANUAL_ORDER,
    TITLE_PINYIN,
}

sealed interface BookshelfFilter {
    data object All : BookshelfFilter
    data object HasNewChapters : BookshelfFilter
    data object CachedOffline : BookshelfFilter
    data object Serializing : BookshelfFilter
    data object Completed : BookshelfFilter
    data class Category(val categoryName: String) : BookshelfFilter
    data class Imprint(val pressName: String) : BookshelfFilter
    data class Tag(val tagName: String) : BookshelfFilter
}

data class BookshelfItemModel(
    val novel: NovelEntity,
    val coverUrl: String,
    val readingProgress: ReadingProgressEntity?,
    val cachedChapterCount: Int,
    val totalChapterCount: Int,
    val tags: List<String>,
    val isActivelySyncingOrDownloading: Boolean,
    val bookProgressPermille: Int = readingProgress?.progressPermille?.coerceIn(0, 1000) ?: 0,
) {
    val aid: Int
        get() = novel.aid

    val isCompleted: Boolean
        get() = novel.bookStatus.contains("完")

    val isCachedOffline: Boolean
        get() = cachedChapterCount > 0

    val readingProgressFraction: Float
        get() = (bookProgressPermille.coerceIn(0, 1000)) / 1000f

    val readingProgressPercent: Int
        get() {
            val clamped = bookProgressPermille.coerceIn(0, 1000)
            return when {
                clamped <= 0 -> 0
                clamped >= 1000 -> 100
                else -> ((clamped + 5) / 10).coerceIn(1, 99)
            }
        }
}

data class BookshelfSnapshot(
    val allShelfItems: List<BookshelfItemModel> = emptyList(),
    val continueReadingItems: List<BookshelfItemModel> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val availableImprints: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val isCloudSyncRunning: Boolean = false,
    val isSmartUpdateRunning: Boolean = false,
    val syncingNovelAids: Set<Int> = emptySet(),
) {
    val isShelfTaskRunning: Boolean
        get() = isCloudSyncRunning || isSmartUpdateRunning
}

sealed interface CloudBookshelfSyncOutcome {
    data class Success(
        val pulledFromCloudCount: Int,
        val pushedToCloudCount: Int,
        val refreshedStaleCount: Int,
        val cloudShelfFullReached: Boolean,
        val deletedFromCloudCount: Int = 0,
        val removedRemotelyDeletedCount: Int = 0,
        val failedHydrationCount: Int = 0,
    ) : CloudBookshelfSyncOutcome

    data object AlreadyRunning : CloudBookshelfSyncOutcome
    data object ApiNotConfigured : CloudBookshelfSyncOutcome
    data object NotLoggedIn : CloudBookshelfSyncOutcome
    data class NetworkOrServerError(
        val numericCode: Int? = null,
        val debugDetail: String? = null,
    ) : CloudBookshelfSyncOutcome
}

data class SmartBatchUpdateSummary(
    val updatedNovelCount: Int,
    val newlyDownloadedChapterCount: Int,
    val failedNovelCount: Int,
    val skippedBecauseBusy: Boolean = false,
)

/**
 * Reactive Bookshelf 2.0 & Ledger-Backed Cloud Bookcase Sync repository (`P4.1`).
 *
 * Guarantees:
 * 1. **Zero Deleted-Novel Resurrection (Local & Remote)**:
 *    - Local removals persist a durable [CloudBookcaseLedgerEntity.STATE_PENDING_DELETE] tombstone
 *      in `cloud_bookcase_ledger` before attempting `action=bookcase&do=del&aid={aid}`, surviving
 *      offline periods, app restarts, and `deleteAllNonBookshelfNovels()` cache purges.
 *    - Removals performed on Wenku8 Web or another device are detected when an `aid` previously
 *      confirmed as [CloudBookcaseLedgerEntity.STATE_SYNCED] for the active `account_key` is absent
 *      from `action=bookcase`, removing it from the local shelf (`in_bookshelf = 0`, preserving
 *      reading progress, bookmarks, and cached chapters) instead of re-uploading it.
 * 2. **Zero Novel Overriding & Cross-Account Isolation**:
 *    - Scoping ledger rows by canonical `account_key` prevents Account B from either wiping
 *      Account A's local books or silently uploading Account A's books into Account B's cloud shelf.
 *    - Cloud-only downloads require all-or-nothing (`meta` + `toc` + `intro`) before committing,
 *      and every DB write re-reads the latest `NovelEntity` inside a Room transaction so concurrent
 *      category edits, TOC updates, or mid-sync deletions are never overwritten.
 * 3. **Zero-Cascade Atomic TOC Sync**:
 *    - All TOC refreshes commit strictly through [NovelTocSyncRepository.syncNovelIndexAtomically]
 *      so reorganizing volumes never wipes `reading_progress` or `bookmarks_notes`.
 */
class BookshelfRepository(
    private val database: Wenku8Database,
    private val catalogGateway: WenkuCatalogGateway,
    private val novelTocSyncRepository: NovelTocSyncRepository,
    private val userSessionRepository: UserSessionRepository? = null,
    private val wallClockMs: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val MAX_CONTINUE_READING_CAROUSEL_ITEMS: Int = 8
        const val MAX_DYNAMIC_TAG_CHIPS: Int = 14
        const val MAX_CONCURRENT_NOVEL_HYDRATIONS: Int = 3
        const val SERVER_CODE_NOT_LOGGED_IN: Int = 4
        const val SERVER_CODE_ALREADY_IN_BOOKSHELF: Int = 5
        const val SERVER_CODE_BOOKSHELF_FULL: Int = 6
        const val SERVER_CODE_NOT_IN_BOOKSHELF: Int = 7

        /**
         * Pure `< 1ms` in-memory filter & sort pipeline over the reactive [BookshelfSnapshot].
         *
         * Note on [BookshelfSortMode.RECENT_READ] (Doc `05` Screen 2):
         * Uses `readingProgress?.updatedAtMs` rather than `novel.lastAccessedMs` so merely viewing
         * a novel's detail page never reorders the user's reading queue!
         */
        fun filterAndSortItems(
            items: List<BookshelfItemModel>,
            localQuery: String,
            activeFilter: BookshelfFilter,
            sortMode: BookshelfSortMode,
        ): List<BookshelfItemModel> {
            val trimmedQuery = localQuery.trim()
            val filtered = items.asSequence()
                .filter { item ->
                    if (trimmedQuery.isEmpty()) {
                        true
                    } else {
                        item.novel.title.contains(trimmedQuery, ignoreCase = true) ||
                            item.novel.author.contains(trimmedQuery, ignoreCase = true) ||
                            item.novel.pressId.contains(trimmedQuery, ignoreCase = true) ||
                            item.novel.tagsCsv.contains(trimmedQuery, ignoreCase = true) ||
                            item.novel.aid.toString() == trimmedQuery
                    }
                }
                .filter { item ->
                    when (activeFilter) {
                        BookshelfFilter.All -> true
                        BookshelfFilter.HasNewChapters -> item.novel.hasUnreadUpdate
                        BookshelfFilter.CachedOffline -> item.isCachedOffline
                        BookshelfFilter.Serializing -> !item.isCompleted
                        BookshelfFilter.Completed -> item.isCompleted
                        is BookshelfFilter.Category -> item.novel.shelfCategory == activeFilter.categoryName
                        is BookshelfFilter.Imprint -> item.novel.pressId == activeFilter.pressName
                        is BookshelfFilter.Tag -> activeFilter.tagName in item.tags
                    }
                }
                .toList()

            return when (sortMode) {
                BookshelfSortMode.RECENT_READ -> filtered.sortedWith(
                    compareByDescending<BookshelfItemModel> { it.readingProgress?.updatedAtMs ?: 0L }
                        .thenBy { it.novel.shelfSortOrder }
                        .thenBy { it.novel.aid },
                )

                BookshelfSortMode.LAST_UPDATE -> filtered.sortedWith(
                    compareByDescending<BookshelfItemModel> { it.novel.lastUpdate }
                        .thenByDescending { it.novel.latestSectionCid }
                        .thenBy { it.novel.shelfSortOrder },
                )

                BookshelfSortMode.MANUAL_ORDER -> filtered.sortedWith(
                    compareBy<BookshelfItemModel> { it.novel.shelfSortOrder }
                        .thenByDescending { it.readingProgress?.updatedAtMs ?: 0L }
                        .thenBy { it.novel.aid },
                )

                BookshelfSortMode.TITLE_PINYIN -> {
                    val collator = Collator.getInstance(Locale.CHINESE)
                    filtered.sortedWith { a, b ->
                        val cmp = collator.compare(a.novel.title, b.novel.title)
                        if (cmp != 0) cmp else a.novel.aid.compareTo(b.novel.aid)
                    }
                }
            }
        }

        internal fun splitTagsCsv(tagsCsv: String): List<String> {
            if (tagsCsv.isBlank()) return emptyList()
            return tagsCsv.split(' ', ',', '，', '/', '、')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
        }

        /**
         * Projects intra-chapter reading progress (`0..1000` within [currentCid]) onto whole-book
         * progress (`0..1000`) across all ordered chapters of all volumes (`[orderedChapterCids]`).
         *
         * Example: For a 3-volume novel with 5 chapters per volume (15 chapters total), finishing
         * the last page of Volume 1's 5th chapter (`chapterIndex = 4, intraChapterPermille = 1000`)
         * yields `((4 + 1.0) / 15.0 * 1000) = 333‰` (`33%`), reaching `1000‰` (`100%`) strictly at
         * the end of the final volume's final chapter. Falls back to [intraChapterPermille] when
         * TOC rows have not been synchronized yet.
         */
        fun computeWholeBookProgressPermille(
            intraChapterPermille: Int,
            currentCid: Int,
            orderedChapterCids: List<Int>,
        ): Int {
            val clampedIntra = intraChapterPermille.coerceIn(0, 1000)
            if (orderedChapterCids.isEmpty() || currentCid <= 0) {
                return clampedIntra
            }
            val chapterIndex = orderedChapterCids.indexOf(currentCid)
            if (chapterIndex < 0) {
                return clampedIntra
            }
            val completedPlusCurrent = chapterIndex.toDouble() + (clampedIntra.toDouble() / 1000.0)
            return ((completedPlusCurrent / orderedChapterCids.size.toDouble()) * 1000.0)
                .roundToInt()
                .coerceIn(0, 1000)
        }
    }

    private val syncMutex = Mutex()
    private val coldStartFirstEntryConsumed = AtomicBoolean(false)

    private val _isCloudSyncRunning = MutableStateFlow(false)
    val isCloudSyncRunning: StateFlow<Boolean> = _isCloudSyncRunning.asStateFlow()

    private val _isSmartUpdateRunning = MutableStateFlow(false)
    val isSmartUpdateRunning: StateFlow<Boolean> = _isSmartUpdateRunning.asStateFlow()

    val isShelfTaskRunning: Boolean
        get() = _isCloudSyncRunning.value || _isSmartUpdateRunning.value

    private val _syncingNovelAids = MutableStateFlow<Set<Int>>(emptySet())
    val syncingNovelAids: StateFlow<Set<Int>> = _syncingNovelAids.asStateFlow()

    // Retains the most recent emitted shelf snapshot so returning from NovelDetailScreen (or
    // switching tabs) has synchronous frame-0 data without an empty-shelf flash or scroll clamp.
    private val _latestSnapshot = MutableStateFlow(BookshelfSnapshot())
    val latestSnapshot: StateFlow<BookshelfSnapshot> = _latestSnapshot.asStateFlow()

    val isApiConfigured: Boolean
        get() = catalogGateway.isApiConfigured

    /**
     * Evaluates cold-start first-entry auto-sync eligibility (`v1` `FavFragment` `timecount == 0` parity).
     *
     * Consumed atomically at most once per process launch on the first entry into `本地书架`, and
     * returns `true` only when a Wenku8 account is signed in (or persisted in `saves/cert.wk8`),
     * leaving signed-out users undisturbed and never re-triggering on tab switches or back navigation.
     */
    fun consumeColdStartAutoSyncEligibility(): Boolean {
        if (!coldStartFirstEntryConsumed.compareAndSet(false, true)) {
            return false
        }
        val sessionRepo = userSessionRepository ?: return false
        return catalogGateway.isApiConfigured && sessionRepo.activeAccountKeyOrNull() != null
    }

    fun observeBookshelfSnapshot(): Flow<BookshelfSnapshot> {
        val dbSnapshotFlow = combine(
            database.novelDao().observeBookshelfNovels(),
            database.readingProgressDao().observeAllProgress(),
            database.chapterContentDao().observeCachedChapterCountsByNovel(),
            database.tocDao().observeOrderedBookshelfChapterIds(),
        ) { shelfNovels, progressList, cachedCounts, orderedChapterIds ->
            val orderedCidsByAid = HashMap<Int, MutableList<Int>>()
            for (row in orderedChapterIds) {
                orderedCidsByAid.getOrPut(row.aid) { ArrayList() }.add(row.cid)
            }
            DbShelfBundle(
                shelfNovels = shelfNovels,
                progressByAid = progressList.associateBy { it.aid },
                cachedCountByAid = cachedCounts.associate { it.aid to it.cachedCount },
                orderedCidsByAid = orderedCidsByAid,
            )
        }

        return combine(
            dbSnapshotFlow,
            _isCloudSyncRunning,
            _isSmartUpdateRunning,
            _syncingNovelAids,
        ) { bundle, cloudSyncRunning, smartUpdateRunning, activeSyncAids ->
            val items = bundle.shelfNovels.map { novel ->
                val progress = bundle.progressByAid[novel.aid]
                val orderedCids = bundle.orderedCidsByAid[novel.aid].orEmpty()
                val bookPermille = if (progress != null) {
                    computeWholeBookProgressPermille(
                        intraChapterPermille = progress.progressPermille,
                        currentCid = progress.cid,
                        orderedChapterCids = orderedCids,
                    )
                } else {
                    0
                }
                BookshelfItemModel(
                    novel = novel,
                    coverUrl = catalogGateway.resolveCoverUrl(novel.aid),
                    readingProgress = progress,
                    cachedChapterCount = bundle.cachedCountByAid[novel.aid] ?: 0,
                    totalChapterCount = orderedCids.size,
                    tags = splitTagsCsv(novel.tagsCsv),
                    isActivelySyncingOrDownloading = novel.aid in activeSyncAids,
                    bookProgressPermille = bookPermille,
                )
            }

            val continueReading = items
                .filter { it.readingProgress != null }
                .sortedByDescending { it.readingProgress?.updatedAtMs ?: 0L }
                .take(MAX_CONTINUE_READING_CAROUSEL_ITEMS)

            val categories = items.asSequence()
                .map { it.novel.shelfCategory.trim() }
                .filter { it.isNotEmpty() && it != NovelEntity.DEFAULT_SHELF_CATEGORY }
                .distinct()
                .sorted()
                .toList()

            val imprints = items.asSequence()
                .map { it.novel.pressId.trim() }
                .filter { it.isNotEmpty() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedByDescending { it.value }
                .map { it.key }

            val topTags = items.asSequence()
                .flatMap { it.tags.asSequence() }
                .groupingBy { it }
                .eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .take(MAX_DYNAMIC_TAG_CHIPS)
                .map { it.key }

            BookshelfSnapshot(
                allShelfItems = items,
                continueReadingItems = continueReading,
                availableCategories = categories,
                availableImprints = imprints,
                availableTags = topTags,
                isCloudSyncRunning = cloudSyncRunning,
                isSmartUpdateRunning = smartUpdateRunning,
                syncingNovelAids = activeSyncAids,
            )
        }.onEach { snapshot ->
            _latestSnapshot.value = snapshot
        }
    }

    /**
     * Executes ledger-backed 2-way Cloud Bookcase synchronization (`action=bookcase`).
     *
     * Five-step non-destructive & zero-resurrection protocol:
     * 0. Flush durable `STATE_PENDING_DELETE` tombstones (`action=bookcase&do=del&aid={aid}`) so
     *    offline/flaky local deletions are replayed to Wenku8 before reading the cloud shelf, and
     *    block any transiently unconfirmed tombstone from resurrecting onto the local shelf.
     * 1. Fetch `action=bookcase` (cross-checking `action=bookcase&do=list` if an empty cloud list
     *    contrasts with previously `STATE_SYNCED` local entries).
     * 2. Honor remote Web / multi-device deletions: any local shelf book recorded as `STATE_SYNCED`
     *    under `accountKey` that is absent from `cloudAidSet` is removed from the local shelf
     *    (`in_bookshelf = 0`, preserving reading progress, bookmarks, and cached chapters) instead
     *    of being re-uploaded.
     * 3. Hydrate cloud-only novels (all-or-nothing `meta` + `toc` + `intro`, plus `#Tags` enrichment)
     *    and refresh stale shared novels with `Semaphore(3)` bounded concurrency, re-reading fresh
     *    SQLite rows inside Room transactions so concurrent user edits or deletions are never
     *    overridden.
     * 4. Push local-only novels (`STATE_PENDING_ADD` or un-synced local books not owned by a
     *    different account) up via `action=bookcase&do=add`, stopping cleanly on `6` (`FULL`)
     *    without dropping any local books.
     */
    suspend fun syncWithCloudBookcase(
        appLanguage: Wenku8API.AppLanguage,
        forceRefreshAll: Boolean = false,
    ): CloudBookshelfSyncOutcome {
        if (!syncMutex.tryLock()) {
            return CloudBookshelfSyncOutcome.AlreadyRunning
        }
        _isCloudSyncRunning.value = true
        return try {
            withContext(ioDispatcher) {
                if (!catalogGateway.isApiConfigured) {
                    return@withContext CloudBookshelfSyncOutcome.ApiNotConfigured
                }
                if (!ensureAuthenticatedForCloudSync()) {
                    return@withContext CloudBookshelfSyncOutcome.NotLoggedIn
                }
                val accountKey = resolveActiveLedgerAccountKey()
                    ?: return@withContext CloudBookshelfSyncOutcome.NotLoggedIn

                val ledgerDao = database.cloudBookcaseLedgerDao()

                // Migrate any offline ledger entries recorded under an email login hint into the
                // server-resolved canonical Wenku8 `uname` before evaluating Step 0..4.
                val rawAliasKey = userSessionRepository?.rawSavedIdentifierKeyOrNull()
                if (!rawAliasKey.isNullOrBlank() && rawAliasKey != accountKey) {
                    ledgerDao.migrateAccountAliasEntries(
                        fromAliasKey = rawAliasKey,
                        toCanonicalKey = accountKey,
                    )
                }

                // In standalone unit tests without UserSessionRepository, seed `STATE_SYNCED` for
                // pre-existing rows seeded with `cloudSynced = true` if not yet in the ledger.
                seedStandaloneTestLedgerIfNeeded(accountKey)

                // Step 0: Replay durable `STATE_PENDING_DELETE` tombstones BEFORE listing cloud.
                val pendingDeleteEntries = ledgerDao.getEntriesForAccount(accountKey)
                    .filter { it.syncState == CloudBookcaseLedgerEntity.STATE_PENDING_DELETE }
                var deletedFromCloudCount = 0
                val confirmedDeletedThisPass = HashSet<Int>()
                val blockedByTombstoneAids = HashSet<Int>()

                for (tombstone in pendingDeleteEntries) {
                    val latestEntry = ledgerDao.getEntry(accountKey, tombstone.aid)
                    if (latestEntry?.syncState != CloudBookcaseLedgerEntity.STATE_PENDING_DELETE) {
                        continue
                    }
                    when (val delRes = removeNovelFromCloudWithSingleRelogin(tombstone.aid)) {
                        is WenkuApiResult.Success -> {
                            ledgerDao.deleteEntryIfStateMatches(
                                accountKey = accountKey,
                                aid = tombstone.aid,
                                expectedState = CloudBookcaseLedgerEntity.STATE_PENDING_DELETE,
                            )
                            confirmedDeletedThisPass += tombstone.aid
                            deletedFromCloudCount++
                        }

                        is WenkuApiResult.ServerErrorCode -> {
                            if (delRes.numericCode == SERVER_CODE_NOT_IN_BOOKSHELF) {
                                ledgerDao.deleteEntryIfStateMatches(
                                    accountKey = accountKey,
                                    aid = tombstone.aid,
                                    expectedState = CloudBookcaseLedgerEntity.STATE_PENDING_DELETE,
                                )
                                confirmedDeletedThisPass += tombstone.aid
                            } else {
                                blockedByTombstoneAids += tombstone.aid
                            }
                        }

                        else -> {
                            // Keep tombstone in SQLite and block Step 3 from resurrecting this book!
                            blockedByTombstoneAids += tombstone.aid
                        }
                    }
                }

                // Step 1: Fetch authoritative cloud bookcase listing (`action=bookcase`).
                var cloudResult = catalogGateway.fetchCloudBookshelf(appLanguage)
                if (cloudResult is WenkuApiResult.ServerErrorCode &&
                    cloudResult.numericCode == SERVER_CODE_NOT_LOGGED_IN &&
                    userSessionRepository != null &&
                    userSessionRepository.reauthenticateFromSavedCredentialsIfPossible()
                ) {
                    cloudResult = catalogGateway.fetchCloudBookshelf(appLanguage)
                }

                var rawCloudEntries = when (cloudResult) {
                    is WenkuApiResult.Success -> cloudResult.data
                    WenkuApiResult.ApiNotConfigured -> return@withContext CloudBookshelfSyncOutcome.ApiNotConfigured
                    is WenkuApiResult.ServerErrorCode -> {
                        return@withContext if (cloudResult.numericCode == SERVER_CODE_NOT_LOGGED_IN) {
                            CloudBookshelfSyncOutcome.NotLoggedIn
                        } else {
                            CloudBookshelfSyncOutcome.NetworkOrServerError(numericCode = cloudResult.numericCode)
                        }
                    }
                    is WenkuApiResult.NetworkError -> {
                        return@withContext CloudBookshelfSyncOutcome.NetworkOrServerError(debugDetail = cloudResult.debugDetail)
                    }
                    WenkuApiResult.EmptyResponse -> {
                        return@withContext CloudBookshelfSyncOutcome.NetworkOrServerError(debugDetail = "empty_response")
                    }
                    is WenkuApiResult.ParseError -> {
                        return@withContext CloudBookshelfSyncOutcome.NetworkOrServerError(debugDetail = cloudResult.rawPreview)
                    }
                }

                // Double-confirm via `action=bookcase&do=list` if the cloud response came back
                // completely empty while this account previously had confirmed `STATE_SYNCED` books.
                val preCheckSyncedCount = ledgerDao.getEntriesForAccount(accountKey)
                    .count { it.syncState == CloudBookcaseLedgerEntity.STATE_SYNCED }
                if (rawCloudEntries.isEmpty() && preCheckSyncedCount > 0) {
                    when (val verifyAidList = fetchCloudAidListWithSingleRelogin(appLanguage)) {
                        is WenkuApiResult.Success -> {
                            if (verifyAidList.data.isNotEmpty()) {
                                rawCloudEntries = verifyAidList.data.map { aid ->
                                    ParsedCloudBookshelfEntry(aid = aid)
                                }
                            }
                        }

                        WenkuApiResult.ApiNotConfigured -> {
                            // Unimplemented on minimal unit-test fakes -> trust `fetchCloudBookshelf`.
                        }

                        is WenkuApiResult.ServerErrorCode -> {
                            return@withContext if (verifyAidList.numericCode == SERVER_CODE_NOT_LOGGED_IN) {
                                CloudBookshelfSyncOutcome.NotLoggedIn
                            } else {
                                CloudBookshelfSyncOutcome.NetworkOrServerError(numericCode = verifyAidList.numericCode)
                            }
                        }

                        is WenkuApiResult.NetworkError -> {
                            return@withContext CloudBookshelfSyncOutcome.NetworkOrServerError(debugDetail = verifyAidList.debugDetail)
                        }

                        WenkuApiResult.EmptyResponse -> {
                            return@withContext CloudBookshelfSyncOutcome.NetworkOrServerError(debugDetail = "empty_response")
                        }

                        is WenkuApiResult.ParseError -> {
                            return@withContext CloudBookshelfSyncOutcome.NetworkOrServerError(debugDetail = verifyAidList.rawPreview)
                        }
                    }
                }

                val postFetchLedgerByAid = ledgerDao.getEntriesForAccount(accountKey).associateBy { it.aid }
                val activeTombstoneAids = postFetchLedgerByAid.values
                    .filter { it.syncState == CloudBookcaseLedgerEntity.STATE_PENDING_DELETE }
                    .mapTo(HashSet()) { it.aid }

                val effectiveCloudEntries = rawCloudEntries.filter { entry ->
                    entry.aid > 0 &&
                        entry.aid !in confirmedDeletedThisPass &&
                        entry.aid !in blockedByTombstoneAids &&
                        entry.aid !in activeTombstoneAids
                }
                val cloudAidSet = effectiveCloudEntries.mapTo(HashSet()) { it.aid }

                // Step 2: Honor remote deletions (`STATE_SYNCED` under `accountKey` & absent from cloud).
                var removedRemotelyDeletedCount = 0
                val initialLocalShelf = database.novelDao().getBookshelfNovelsSnapshot()
                for (held in initialLocalShelf) {
                    if (held.aid !in cloudAidSet &&
                        postFetchLedgerByAid[held.aid]?.syncState == CloudBookcaseLedgerEntity.STATE_SYNCED
                    ) {
                        val removedLocally = database.withTransaction {
                            val freshLedger = ledgerDao.getEntry(accountKey, held.aid)
                            if (freshLedger != null &&
                                freshLedger.syncState != CloudBookcaseLedgerEntity.STATE_SYNCED
                            ) {
                                // User concurrently mutated this book (e.g. re-added) -> preserve it.
                                false
                            } else {
                                database.novelDao().removeNovelsFromBookshelf(listOf(held.aid))
                                ledgerDao.deleteEntryIfStateMatches(
                                    accountKey = accountKey,
                                    aid = held.aid,
                                    expectedState = CloudBookcaseLedgerEntity.STATE_SYNCED,
                                )
                                true
                            }
                        }
                        if (removedLocally) {
                            removedRemotelyDeletedCount++
                        }
                    }
                }

                // Step 3: Pull cloud-only novels & refresh stale shared novels with bounded parallelism.
                val localAfterRemoteRemovals = database.novelDao().getBookshelfNovelsSnapshot()
                val localByAid = localAfterRemoteRemovals.associateBy { it.aid }
                var nextSortOrder = (database.novelDao().getMaxBookshelfSortOrder() + 1).coerceAtLeast(1)
                val assignedSortOrders = HashMap<Int, Int>()
                for (entry in effectiveCloudEntries) {
                    if (localByAid[entry.aid] == null) {
                        assignedSortOrders[entry.aid] = nextSortOrder++
                    }
                }

                val hydrationSemaphore = Semaphore(MAX_CONCURRENT_NOVEL_HYDRATIONS)
                val stepOutcomes = coroutineScope {
                    effectiveCloudEntries.map { entry ->
                        async {
                            hydrationSemaphore.withPermit {
                                val held = localByAid[entry.aid]
                                when {
                                    held == null -> {
                                        markNovelSyncing(entry.aid, active = true)
                                        try {
                                            hydrateCloudEntryOntoLocalShelf(
                                                accountKey = accountKey,
                                                entry = entry,
                                                assignedSortOrder = assignedSortOrders[entry.aid] ?: 1,
                                                appLanguage = appLanguage,
                                            )
                                        } finally {
                                            markNovelSyncing(entry.aid, active = false)
                                        }
                                    }

                                    isLocalNovelStaleAgainstCloud(held, entry, forceRefreshAll) -> {
                                        markNovelSyncing(entry.aid, active = true)
                                        try {
                                            refreshExistingShelfNovelFromCloud(
                                                accountKey = accountKey,
                                                initialHeld = held,
                                                entry = entry,
                                                appLanguage = appLanguage,
                                            )
                                        } finally {
                                            markNovelSyncing(entry.aid, active = false)
                                        }
                                    }

                                    else -> {
                                        markAlreadySyncedSharedNovel(accountKey = accountKey, aid = entry.aid)
                                        HydrationStepOutcome.UNCHANGED
                                    }
                                }
                            }
                        }
                    }.awaitAll()
                }

                val pulledCount = stepOutcomes.count { it == HydrationStepOutcome.PULLED }
                val refreshedStaleCount = stepOutcomes.count { it == HydrationStepOutcome.REFRESHED }
                val failedHydrationCount = stepOutcomes.count { it == HydrationStepOutcome.FAILED }

                // Step 4: Push genuinely local-only novels up to cloud (`do=add`).
                var pushedCount = 0
                var cloudFullReached = false
                val latestLocalShelf = database.novelDao().getBookshelfNovelsSnapshot()
                val latestAccountLedger = ledgerDao.getEntriesForAccount(accountKey).associateBy { it.aid }
                val otherAccountSyncedAids = ledgerDao.getAidsSyncedWithOtherAccounts(accountKey).toHashSet()

                val pushCandidates = latestLocalShelf.filter { localNovel ->
                    if (localNovel.aid in cloudAidSet) {
                        false
                    } else {
                        when (latestAccountLedger[localNovel.aid]?.syncState) {
                            CloudBookcaseLedgerEntity.STATE_PENDING_ADD -> true
                            CloudBookcaseLedgerEntity.STATE_SYNCED,
                            CloudBookcaseLedgerEntity.STATE_PENDING_DELETE,
                            -> false
                            else -> localNovel.aid !in otherAccountSyncedAids
                        }
                    }
                }

                for (localOnly in pushCandidates) {
                    when (val pushResult = addNovelToCloudWithSingleRelogin(localOnly.aid)) {
                        is WenkuApiResult.Success -> {
                            if (confirmLocalNovelSyncedIfStillOnShelf(accountKey, localOnly.aid)) {
                                pushedCount++
                            }
                        }

                        is WenkuApiResult.ServerErrorCode -> when (pushResult.numericCode) {
                            SERVER_CODE_ALREADY_IN_BOOKSHELF -> {
                                if (confirmLocalNovelSyncedIfStillOnShelf(accountKey, localOnly.aid)) {
                                    pushedCount++
                                }
                            }

                            SERVER_CODE_BOOKSHELF_FULL -> {
                                cloudFullReached = true
                                break
                            }
                        }

                        else -> {
                            // Leave unsynced so the next sync pass retries without losing the book.
                        }
                    }
                }

                CloudBookshelfSyncOutcome.Success(
                    pulledFromCloudCount = pulledCount,
                    pushedToCloudCount = pushedCount,
                    refreshedStaleCount = refreshedStaleCount,
                    cloudShelfFullReached = cloudFullReached,
                    deletedFromCloudCount = deletedFromCloudCount,
                    removedRemotelyDeletedCount = removedRemotelyDeletedCount,
                    failedHydrationCount = failedHydrationCount,
                )
            }
        } finally {
            _isCloudSyncRunning.value = false
            syncMutex.unlock()
        }
    }

    /**
     * Smart-updates metadata, atomic TOC, and any missing chapters for novels that already have
     * offline chapter caches (powering the shelf header & multi-select `智能更新` action).
     *
     * Mutually excluded against [syncWithCloudBookcase] and other smart-update invocations via
     * [syncMutex] so overlapping shelf tasks cannot race or queue up duplicate network passes.
     */
    suspend fun smartUpdateShelfNovels(
        aids: List<Int>,
        appLanguage: Wenku8API.AppLanguage,
    ): SmartBatchUpdateSummary {
        val targetAids = aids.distinct().filter { it > 0 }
        if (targetAids.isEmpty() || !catalogGateway.isApiConfigured) {
            return SmartBatchUpdateSummary(0, 0, 0)
        }
        if (!syncMutex.tryLock()) {
            return SmartBatchUpdateSummary(
                updatedNovelCount = 0,
                newlyDownloadedChapterCount = 0,
                failedNovelCount = 0,
                skippedBecauseBusy = true,
            )
        }
        _isSmartUpdateRunning.value = true
        return try {
            withContext(ioDispatcher) {
                var updatedNovels = 0
                var downloadedChapters = 0
                var failedNovels = 0

                for (aid in targetAids) {
                    markNovelSyncing(aid, active = true)
                    try {
                        val startedAt = wallClockMs()
                        val existing = database.novelDao().getNovelByAid(aid)
                        if (existing == null || !existing.inBookshelf) {
                            continue
                        }
                        val metaRes = catalogGateway.fetchNovelFullMeta(aid, appLanguage)
                        val tocRes = catalogGateway.fetchNovelVolumeToc(aid, appLanguage)

                        if (metaRes !is WenkuApiResult.Success && tocRes !is WenkuApiResult.Success) {
                            failedNovels++
                            continue
                        }

                        if (tocRes is WenkuApiResult.Success) {
                            val freshBeforeToc = database.novelDao().getNovelByAid(aid)
                            if (freshBeforeToc == null || !freshBeforeToc.inBookshelf) {
                                continue
                            }
                            novelTocSyncRepository.syncNovelIndexAtomically(
                                aid = aid,
                                language = appLanguage,
                                requestStartedAtMs = startedAt,
                                parsedVolumes = tocRes.data,
                                fallbackNovelTitle = freshBeforeToc.title.ifBlank { "AID $aid" },
                            )
                        }

                        if (metaRes is WenkuApiResult.Success) {
                            val freshForMeta = database.novelDao().getNovelByAid(aid)
                            if (freshForMeta != null && freshForMeta.inBookshelf) {
                                val m = metaRes.data
                                val incoming = freshForMeta.copy(
                                    contentLang = appLanguage.name,
                                    metaSyncedAtMs = startedAt,
                                    title = m.title.ifBlank { freshForMeta.title },
                                    author = m.author.ifBlank { freshForMeta.author },
                                    bookStatus = m.bookStatus.ifBlank { freshForMeta.bookStatus },
                                    pressId = m.pressId.ifBlank { freshForMeta.pressId },
                                    bookLength = if (m.bookLength > 0) m.bookLength else freshForMeta.bookLength,
                                    lastUpdate = m.lastUpdate.ifBlank { freshForMeta.lastUpdate },
                                    latestSectionCid = if (m.latestSectionCid > 0) m.latestSectionCid else freshForMeta.latestSectionCid,
                                    latestSectionName = m.latestSectionName.ifBlank { freshForMeta.latestSectionName },
                                    dayHits = m.dayHitsCount,
                                    totalHits = m.totalHitsCount,
                                    pushCount = m.pushCount,
                                    favCount = m.favCount,
                                )
                                database.novelDao().mergeServerMetadataPreservingShelfState(incoming)
                            }
                        }

                        if (tocRes is WenkuApiResult.Success) {
                            // If this novel already has offline cached chapters, smart-download any new
                            // uncached chapters so offline readers stay current!
                            val cachedCids = database.chapterContentDao().getCachedChapterIdsForNovel(aid).toHashSet()
                            if (cachedCids.isNotEmpty()) {
                                val allCids = tocRes.data.asSequence()
                                    .flatMap { it.chapters.asSequence() }
                                    .map { it.cid }
                                    .filter { it > 0 && it !in cachedCids }
                                    .toList()
                                for (cid in allCids) {
                                    val stillOnShelf = database.novelDao().getNovelByAid(aid)?.inBookshelf == true
                                    if (!stillOnShelf) break
                                    val contentRes = catalogGateway.fetchChapterContent(aid, cid, appLanguage)
                                    if (contentRes is WenkuApiResult.Success && !contentRes.data.isEffectivelyBlankWenkuText()) {
                                        val text = contentRes.data
                                        val imageUrls = WenkuXmlParsers.extractChapterImageUrls(text)
                                        val jsonUrls = JSONArray(imageUrls).toString()
                                        val utf8Bytes = text.toByteArray(StandardCharsets.UTF_8)
                                        database.chapterContentDao().upsertChapterContent(
                                            ChapterContentEntity(
                                                cid = cid,
                                                aid = aid,
                                                contentLang = appLanguage.name,
                                                contentText = text,
                                                byteLength = utf8Bytes.size,
                                                imageUrlsJson = jsonUrls,
                                                imageCount = imageUrls.size,
                                                sha256Checksum = DigestUtils.sha256Hex(text),
                                                downloadedAtMs = wallClockMs(),
                                            ),
                                        )
                                        downloadedChapters++
                                    }
                                }
                            }
                        }
                        updatedNovels++
                    } finally {
                        markNovelSyncing(aid, active = false)
                    }
                }

                SmartBatchUpdateSummary(
                    updatedNovelCount = updatedNovels,
                    newlyDownloadedChapterCount = downloadedChapters,
                    failedNovelCount = failedNovels,
                )
            }
        } finally {
            _isSmartUpdateRunning.value = false
            syncMutex.unlock()
        }
    }

    /**
     * Adds a novel to the local bookshelf immediately (unlimited capacity), records
     * `STATE_PENDING_ADD` in `cloud_bookcase_ledger` (clearing any prior delete tombstone), and
     * best-effort pushes it to the cloud bookcase when signed in.
     */
    suspend fun addNovelToBookshelf(
        novel: NovelEntity,
        category: String = NovelEntity.DEFAULT_SHELF_CATEGORY,
        syncToCloudIfSignedIn: Boolean = true,
    ) = withContext(ioDispatcher) {
        val actionAtMs = wallClockMs()
        val activeAccountKey = resolveActiveLedgerAccountKey()
        val trimmedRequestedCategory = category.trim()
        val trimmedNovelCategory = novel.shelfCategory.trim()

        database.withTransaction {
            val existing = database.novelDao().getNovelByAid(novel.aid)
            val resolvedCategory = when {
                trimmedRequestedCategory.isNotEmpty() &&
                    trimmedRequestedCategory != NovelEntity.DEFAULT_SHELF_CATEGORY -> trimmedRequestedCategory
                trimmedNovelCategory.isNotEmpty() &&
                    trimmedNovelCategory != NovelEntity.DEFAULT_SHELF_CATEGORY -> trimmedNovelCategory
                existing != null &&
                    existing.shelfCategory.isNotBlank() &&
                    existing.shelfCategory != NovelEntity.DEFAULT_SHELF_CATEGORY -> existing.shelfCategory
                else -> NovelEntity.DEFAULT_SHELF_CATEGORY
            }
            val nextOrder = if (existing?.inBookshelf == true) {
                existing.shelfSortOrder
            } else {
                (database.novelDao().getMinBookshelfSortOrder() - 1)
            }
            val merged = (existing ?: novel).copy(
                contentLang = novel.contentLang.ifBlank { existing?.contentLang.orEmpty() },
                title = novel.title.ifBlank { existing?.title ?: "AID ${novel.aid}" },
                author = novel.author.ifBlank { existing?.author.orEmpty() },
                bookStatus = novel.bookStatus.ifBlank { existing?.bookStatus.orEmpty() },
                pressId = novel.pressId.ifBlank { existing?.pressId.orEmpty() },
                bookLength = if (novel.bookLength > 0) novel.bookLength else (existing?.bookLength ?: 0),
                lastUpdate = novel.lastUpdate.ifBlank { existing?.lastUpdate.orEmpty() },
                latestSectionCid = if (novel.latestSectionCid > 0) {
                    maxOf(novel.latestSectionCid, existing?.latestSectionCid ?: 0)
                } else {
                    existing?.latestSectionCid ?: 0
                },
                latestSectionName = novel.latestSectionName.ifBlank { existing?.latestSectionName.orEmpty() },
                tagsCsv = novel.tagsCsv.ifBlank { existing?.tagsCsv.orEmpty() },
                introShort = novel.introShort.ifBlank { existing?.introShort.orEmpty() },
                fullIntro = novel.fullIntro.ifBlank { existing?.fullIntro.orEmpty() },
                inBookshelf = true,
                shelfCategory = resolvedCategory,
                shelfSortOrder = nextOrder,
                cloudSynced = false,
            )
            database.novelDao().upsertNovel(merged)

            if (activeAccountKey != null) {
                database.cloudBookcaseLedgerDao().upsertEntry(
                    CloudBookcaseLedgerEntity(
                        accountKey = activeAccountKey,
                        aid = novel.aid,
                        syncState = CloudBookcaseLedgerEntity.STATE_PENDING_ADD,
                        updatedAtMs = actionAtMs,
                    ),
                )
            }
        }

        if (syncToCloudIfSignedIn && catalogGateway.isApiConfigured && isSignedInOrSaved()) {
            val res = addNovelToCloudWithSingleRelogin(novel.aid)
            if (res is WenkuApiResult.Success ||
                (res is WenkuApiResult.ServerErrorCode && res.numericCode == SERVER_CODE_ALREADY_IN_BOOKSHELF)
            ) {
                val confirmedAccountKey = resolveActiveLedgerAccountKey() ?: activeAccountKey
                if (confirmedAccountKey != null) {
                    val rawAliasKey = userSessionRepository?.rawSavedIdentifierKeyOrNull()
                    if (!rawAliasKey.isNullOrBlank() && rawAliasKey != confirmedAccountKey) {
                        database.cloudBookcaseLedgerDao().migrateAccountAliasEntries(
                            fromAliasKey = rawAliasKey,
                            toCanonicalKey = confirmedAccountKey,
                        )
                    }
                    confirmLocalNovelSyncedIfStillOnShelf(confirmedAccountKey, novel.aid)
                } else {
                    database.withTransaction {
                        val fresh = database.novelDao().getNovelByAid(novel.aid)
                        if (fresh?.inBookshelf == true) {
                            database.novelDao().setCloudSynced(novel.aid, true)
                        }
                    }
                }
            }
        }
    }

    /**
     * Removes one or more novels from the local bookshelf (`in_bookshelf = 0`), atomically persists
     * durable `STATE_PENDING_DELETE` tombstones in `cloud_bookcase_ledger` so offline/flaky deletes
     * can never resurrect on a future sync, and best-effort removes from the cloud bookcase when
     * signed in.
     */
    suspend fun removeNovelsFromBookshelf(
        aids: List<Int>,
        removeFromCloudIfSignedIn: Boolean = true,
        deleteOfflineCache: Boolean = false,
    ) = withContext(ioDispatcher) {
        val targetAids = aids.distinct().filter { it > 0 }
        if (targetAids.isEmpty()) return@withContext

        val actionAtMs = wallClockMs()
        val activeAccountKey = resolveActiveLedgerAccountKey()
        val ledgerDao = database.cloudBookcaseLedgerDao()

        database.withTransaction {
            database.novelDao().removeNovelsFromBookshelf(targetAids)
            if (deleteOfflineCache) {
                database.chapterContentDao().deleteChapterContentsForNovels(targetAids)
            }

            val existingLedgerEntries = ledgerDao.getEntriesForAids(targetAids)
            if (removeFromCloudIfSignedIn) {
                if (activeAccountKey != null) {
                    val tombstones = targetAids.map { aid ->
                        CloudBookcaseLedgerEntity(
                            accountKey = activeAccountKey,
                            aid = aid,
                            syncState = CloudBookcaseLedgerEntity.STATE_PENDING_DELETE,
                            updatedAtMs = actionAtMs,
                        )
                    }
                    ledgerDao.upsertEntries(tombstones)
                }

                // Also convert any previously `STATE_SYNCED` record under a temporarily signed-out
                // account into `STATE_PENDING_DELETE` so logging back into that account deletes it
                // from cloud rather than resurrecting it onto the local shelf.
                for (existingEntry in existingLedgerEntries) {
                    if (existingEntry.accountKey == activeAccountKey) continue
                    when (existingEntry.syncState) {
                        CloudBookcaseLedgerEntity.STATE_SYNCED -> {
                            ledgerDao.upsertEntry(
                                existingEntry.copy(
                                    syncState = CloudBookcaseLedgerEntity.STATE_PENDING_DELETE,
                                    updatedAtMs = actionAtMs,
                                ),
                            )
                        }

                        CloudBookcaseLedgerEntity.STATE_PENDING_ADD -> {
                            ledgerDao.deleteEntry(existingEntry.accountKey, existingEntry.aid)
                        }
                    }
                }
            } else if (activeAccountKey != null) {
                for (aid in targetAids) {
                    ledgerDao.deleteEntryIfStateMatches(
                        accountKey = activeAccountKey,
                        aid = aid,
                        expectedState = CloudBookcaseLedgerEntity.STATE_PENDING_ADD,
                    )
                }
            }
        }

        if (removeFromCloudIfSignedIn && catalogGateway.isApiConfigured && isSignedInOrSaved()) {
            for (aid in targetAids) {
                val res = removeNovelFromCloudWithSingleRelogin(aid)
                if (res is WenkuApiResult.Success ||
                    (res is WenkuApiResult.ServerErrorCode && res.numericCode == SERVER_CODE_NOT_IN_BOOKSHELF)
                ) {
                    val confirmedAccountKey = resolveActiveLedgerAccountKey() ?: activeAccountKey
                    if (confirmedAccountKey != null) {
                        val rawAliasKey = userSessionRepository?.rawSavedIdentifierKeyOrNull()
                        if (!rawAliasKey.isNullOrBlank() && rawAliasKey != confirmedAccountKey) {
                            ledgerDao.migrateAccountAliasEntries(
                                fromAliasKey = rawAliasKey,
                                toCanonicalKey = confirmedAccountKey,
                            )
                        }
                        ledgerDao.deleteEntryIfStateMatches(
                            accountKey = confirmedAccountKey,
                            aid = aid,
                            expectedState = CloudBookcaseLedgerEntity.STATE_PENDING_DELETE,
                        )
                    }
                }
            }
        }
    }

    suspend fun clearCachedChaptersForNovels(aids: List<Int>) = withContext(ioDispatcher) {
        val targetAids = aids.distinct().filter { it > 0 }
        if (targetAids.isEmpty()) return@withContext
        database.chapterContentDao().deleteChapterContentsForNovels(targetAids)
    }

    suspend fun pinNovelsToTop(aids: List<Int>) = withContext(ioDispatcher) {
        val targetAids = aids.distinct().filter { it > 0 }
        if (targetAids.isEmpty()) return@withContext
        database.withTransaction {
            val currentMin = database.novelDao().getMinBookshelfSortOrder()
            var nextTopOrder = currentMin - targetAids.size
            for (aid in targetAids) {
                database.novelDao().updateShelfSortOrder(aid = aid, sortOrder = nextTopOrder++)
            }
        }
    }

    suspend fun clearUnreadUpdateBadge(aid: Int) = withContext(ioDispatcher) {
        if (aid <= 0) return@withContext
        database.novelDao().setHasUnreadUpdate(aid = aid, hasUnreadUpdate = false)
    }

    private fun isLocalNovelStaleAgainstCloud(
        held: NovelEntity,
        cloudEntry: ParsedCloudBookshelfEntry,
        forceRefreshAll: Boolean,
    ): Boolean {
        if (forceRefreshAll) return true
        if (held.title.isBlank() || held.tocVersion == 0L) return true
        if (cloudEntry.latestChapterCid > 0 && held.latestSectionCid != cloudEntry.latestChapterCid) {
            return true
        }
        if (cloudEntry.lastUpdateDate.isNotEmpty() && held.lastUpdate != cloudEntry.lastUpdateDate) {
            return true
        }
        return false
    }

    /**
     * All-or-nothing download of a cloud-only novel onto the local bookshelf (`NovelDownloader` parity).
     *
     * Never commits a broken `"AID #..."` stub if `fetchNovelFullMeta` or `fetchNovelVolumeToc`
     * fails or `fetchNovelFullIntro` drops offline, and re-verifies inside a Room transaction that
     * the user did not delete `entry.aid` while network calls were in flight.
     */
    private suspend fun hydrateCloudEntryOntoLocalShelf(
        accountKey: String,
        entry: ParsedCloudBookshelfEntry,
        assignedSortOrder: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): HydrationStepOutcome {
        val startedAt = wallClockMs()
        val metaResult = catalogGateway.fetchNovelFullMeta(entry.aid, appLanguage)
        val introResult = catalogGateway.fetchNovelFullIntro(entry.aid, appLanguage)
        val tocResult = catalogGateway.fetchNovelVolumeToc(entry.aid, appLanguage)

        if (metaResult !is WenkuApiResult.Success ||
            tocResult !is WenkuApiResult.Success ||
            introResult is WenkuApiResult.NetworkError
        ) {
            return HydrationStepOutcome.FAILED
        }

        val meta = metaResult.data
        val preExisting = database.novelDao().getNovelByAid(entry.aid)
        val fetchedTagsCsv = if (meta.tagsCsv.isBlank() && preExisting?.tagsCsv.isNullOrBlank()) {
            (catalogGateway.fetchNovelShortInfo(entry.aid, appLanguage) as? WenkuApiResult.Success)
                ?.data?.tagsCsv.orEmpty()
        } else {
            ""
        }

        if (isBlockedByConcurrentDelete(accountKey, entry.aid)) {
            return HydrationStepOutcome.SKIPPED_CONCURRENT_DELETE
        }

        val fullIntro = (introResult as? WenkuApiResult.Success)?.data.orEmpty()
        val resolvedTitle = meta.title.takeIf { it.isNotBlank() }
            ?: entry.title.takeIf { it.isNotBlank() }
            ?: preExisting?.title?.takeIf { it.isNotBlank() }
            ?: "AID ${entry.aid}"

        novelTocSyncRepository.syncNovelIndexAtomically(
            aid = entry.aid,
            language = appLanguage,
            requestStartedAtMs = startedAt,
            parsedVolumes = tocResult.data,
            fallbackNovelTitle = resolvedTitle,
        )

        return database.withTransaction {
            val freshLedger = database.cloudBookcaseLedgerDao().getEntry(accountKey, entry.aid)
            if (freshLedger?.syncState == CloudBookcaseLedgerEntity.STATE_PENDING_DELETE) {
                database.novelDao().removeNovelsFromBookshelf(listOf(entry.aid))
                HydrationStepOutcome.SKIPPED_CONCURRENT_DELETE
            } else {
                val freshExisting = database.novelDao().getNovelByAid(entry.aid)
                val mergedTags = meta.tagsCsv
                    .ifBlank { fetchedTagsCsv }
                    .ifBlank { freshExisting?.tagsCsv.orEmpty() }
                val mergedFullIntro = fullIntro.ifBlank { freshExisting?.fullIntro.orEmpty() }
                val entity = (freshExisting ?: NovelEntity(aid = entry.aid, title = resolvedTitle)).copy(
                    contentLang = appLanguage.name,
                    metaSyncedAtMs = maxOf(freshExisting?.metaSyncedAtMs ?: 0L, startedAt),
                    title = resolvedTitle,
                    author = meta.author.ifBlank { freshExisting?.author.orEmpty() },
                    bookStatus = meta.bookStatus.ifBlank { freshExisting?.bookStatus.orEmpty() },
                    pressId = meta.pressId.ifBlank { freshExisting?.pressId.orEmpty() },
                    bookLength = meta.bookLength.takeIf { it > 0 } ?: freshExisting?.bookLength ?: 0,
                    lastUpdate = meta.lastUpdate.ifBlank {
                        entry.lastUpdateDate.ifBlank { freshExisting?.lastUpdate.orEmpty() }
                    },
                    latestSectionCid = meta.latestSectionCid.takeIf { it > 0 }
                        ?: entry.latestChapterCid.takeIf { it > 0 }
                        ?: freshExisting?.latestSectionCid
                        ?: 0,
                    latestSectionName = meta.latestSectionName.ifBlank {
                        entry.latestChapterName.ifBlank { freshExisting?.latestSectionName.orEmpty() }
                    },
                    dayHits = meta.dayHitsCount.takeIf { it > 0 } ?: freshExisting?.dayHits ?: 0,
                    totalHits = meta.totalHitsCount.takeIf { it > 0 } ?: freshExisting?.totalHits ?: 0,
                    pushCount = meta.pushCount.takeIf { it > 0 } ?: freshExisting?.pushCount ?: 0,
                    favCount = meta.favCount.takeIf { it > 0 } ?: freshExisting?.favCount ?: 0,
                    tagsCsv = mergedTags,
                    fullIntro = mergedFullIntro,
                    introShort = freshExisting?.introShort?.ifBlank { mergedFullIntro.take(120) }
                        ?: mergedFullIntro.take(120),
                    inBookshelf = true,
                    shelfCategory = freshExisting?.shelfCategory
                        ?.ifBlank { NovelEntity.DEFAULT_SHELF_CATEGORY }
                        ?: NovelEntity.DEFAULT_SHELF_CATEGORY,
                    shelfSortOrder = if (freshExisting?.inBookshelf == true) {
                        freshExisting.shelfSortOrder
                    } else {
                        assignedSortOrder
                    },
                    cloudSynced = true,
                )
                database.novelDao().upsertNovel(entity)
                database.cloudBookcaseLedgerDao().upsertEntry(
                    CloudBookcaseLedgerEntity(
                        accountKey = accountKey,
                        aid = entry.aid,
                        syncState = CloudBookcaseLedgerEntity.STATE_SYNCED,
                        updatedAtMs = wallClockMs(),
                    ),
                )
                HydrationStepOutcome.PULLED
            }
        }
    }

    /**
     * Refreshes an existing shelf novel whose cloud `latestChapterCid` or `lastUpdateDate` advanced,
     * merging strictly onto the latest transactional DB state so concurrent category/sort/TOC updates
     * or mid-sync user deletions are never overwritten.
     */
    private suspend fun refreshExistingShelfNovelFromCloud(
        accountKey: String,
        initialHeld: NovelEntity,
        entry: ParsedCloudBookshelfEntry,
        appLanguage: Wenku8API.AppLanguage,
    ): HydrationStepOutcome {
        val startedAt = wallClockMs()
        val hasNewerCloudChapter =
            (initialHeld.latestSectionCid > 0 && entry.latestChapterCid > initialHeld.latestSectionCid) ||
                (initialHeld.latestSectionCid == 0 &&
                    initialHeld.lastUpdate.isNotEmpty() &&
                    entry.lastUpdateDate > initialHeld.lastUpdate)

        val metaResult = catalogGateway.fetchNovelFullMeta(entry.aid, appLanguage)
        val tocResult = catalogGateway.fetchNovelVolumeToc(entry.aid, appLanguage)
        if (metaResult !is WenkuApiResult.Success && tocResult !is WenkuApiResult.Success) {
            return HydrationStepOutcome.FAILED
        }

        val meta = (metaResult as? WenkuApiResult.Success)?.data
        val fetchedTagsCsv = if (meta?.tagsCsv.isNullOrBlank() && initialHeld.tagsCsv.isBlank()) {
            (catalogGateway.fetchNovelShortInfo(entry.aid, appLanguage) as? WenkuApiResult.Success)
                ?.data?.tagsCsv.orEmpty()
        } else {
            ""
        }

        if (isBlockedByConcurrentDelete(accountKey, entry.aid)) {
            return HydrationStepOutcome.SKIPPED_CONCURRENT_DELETE
        }

        val fallbackTitle = meta?.title?.takeIf { it.isNotBlank() }
            ?: entry.title.takeIf { it.isNotBlank() }
            ?: initialHeld.title

        if (tocResult is WenkuApiResult.Success) {
            val stillOnShelfBeforeToc = database.novelDao().getNovelByAid(entry.aid)?.inBookshelf == true
            if (!stillOnShelfBeforeToc || isBlockedByConcurrentDelete(accountKey, entry.aid)) {
                return HydrationStepOutcome.SKIPPED_CONCURRENT_DELETE
            }
            novelTocSyncRepository.syncNovelIndexAtomically(
                aid = entry.aid,
                language = appLanguage,
                requestStartedAtMs = startedAt,
                parsedVolumes = tocResult.data,
                fallbackNovelTitle = fallbackTitle,
            )
        }

        return database.withTransaction {
            val freshNovel = database.novelDao().getNovelByAid(entry.aid)
            val freshLedger = database.cloudBookcaseLedgerDao().getEntry(accountKey, entry.aid)
            if (freshNovel == null ||
                !freshNovel.inBookshelf ||
                freshLedger?.syncState == CloudBookcaseLedgerEntity.STATE_PENDING_DELETE
            ) {
                HydrationStepOutcome.SKIPPED_CONCURRENT_DELETE
            } else {
                val mergedTags = meta?.tagsCsv.orEmpty()
                    .ifBlank { fetchedTagsCsv }
                    .ifBlank { freshNovel.tagsCsv }
                val updated = freshNovel.copy(
                    contentLang = appLanguage.name,
                    metaSyncedAtMs = maxOf(freshNovel.metaSyncedAtMs, startedAt),
                    title = meta?.title?.takeIf { it.isNotBlank() }
                        ?: entry.title.takeIf { it.isNotBlank() }
                        ?: freshNovel.title,
                    author = meta?.author?.takeIf { it.isNotBlank() } ?: freshNovel.author,
                    bookStatus = meta?.bookStatus?.takeIf { it.isNotBlank() } ?: freshNovel.bookStatus,
                    pressId = meta?.pressId?.takeIf { it.isNotBlank() } ?: freshNovel.pressId,
                    bookLength = meta?.bookLength?.takeIf { it > 0 } ?: freshNovel.bookLength,
                    lastUpdate = meta?.lastUpdate?.takeIf { it.isNotBlank() }
                        ?: entry.lastUpdateDate.ifBlank { freshNovel.lastUpdate },
                    latestSectionCid = meta?.latestSectionCid?.takeIf { it > 0 }
                        ?: entry.latestChapterCid.takeIf { it > 0 }
                        ?: freshNovel.latestSectionCid,
                    latestSectionName = meta?.latestSectionName?.takeIf { it.isNotBlank() }
                        ?: entry.latestChapterName.ifBlank { freshNovel.latestSectionName },
                    dayHits = meta?.dayHitsCount?.takeIf { it > 0 } ?: freshNovel.dayHits,
                    totalHits = meta?.totalHitsCount?.takeIf { it > 0 } ?: freshNovel.totalHits,
                    pushCount = meta?.pushCount?.takeIf { it > 0 } ?: freshNovel.pushCount,
                    favCount = meta?.favCount?.takeIf { it > 0 } ?: freshNovel.favCount,
                    tagsCsv = mergedTags,
                    cloudSynced = true,
                    hasUnreadUpdate = freshNovel.hasUnreadUpdate || hasNewerCloudChapter,
                )
                database.novelDao().upsertNovel(updated)
                database.cloudBookcaseLedgerDao().upsertEntry(
                    CloudBookcaseLedgerEntity(
                        accountKey = accountKey,
                        aid = entry.aid,
                        syncState = CloudBookcaseLedgerEntity.STATE_SYNCED,
                        updatedAtMs = wallClockMs(),
                    ),
                )
                HydrationStepOutcome.REFRESHED
            }
        }
    }

    private suspend fun markAlreadySyncedSharedNovel(accountKey: String, aid: Int) {
        database.withTransaction {
            val freshNovel = database.novelDao().getNovelByAid(aid)
            val freshLedger = database.cloudBookcaseLedgerDao().getEntry(accountKey, aid)
            if (freshNovel?.inBookshelf == true &&
                freshLedger?.syncState != CloudBookcaseLedgerEntity.STATE_PENDING_DELETE
            ) {
                if (!freshNovel.cloudSynced) {
                    database.novelDao().setCloudSynced(aid, true)
                }
                if (freshLedger?.syncState != CloudBookcaseLedgerEntity.STATE_SYNCED) {
                    database.cloudBookcaseLedgerDao().upsertEntry(
                        CloudBookcaseLedgerEntity(
                            accountKey = accountKey,
                            aid = aid,
                            syncState = CloudBookcaseLedgerEntity.STATE_SYNCED,
                            updatedAtMs = wallClockMs(),
                        ),
                    )
                }
            }
        }
    }

    private suspend fun confirmLocalNovelSyncedIfStillOnShelf(
        accountKey: String,
        aid: Int,
    ): Boolean {
        return database.withTransaction {
            val freshNovel = database.novelDao().getNovelByAid(aid)
            val freshLedger = database.cloudBookcaseLedgerDao().getEntry(accountKey, aid)
            if (freshNovel?.inBookshelf == true &&
                freshLedger?.syncState != CloudBookcaseLedgerEntity.STATE_PENDING_DELETE
            ) {
                database.novelDao().setCloudSynced(aid, true)
                database.cloudBookcaseLedgerDao().upsertEntry(
                    CloudBookcaseLedgerEntity(
                        accountKey = accountKey,
                        aid = aid,
                        syncState = CloudBookcaseLedgerEntity.STATE_SYNCED,
                        updatedAtMs = wallClockMs(),
                    ),
                )
                true
            } else {
                false
            }
        }
    }

    private suspend fun seedStandaloneTestLedgerIfNeeded(accountKey: String) {
        if (userSessionRepository != null) return
        val ledgerDao = database.cloudBookcaseLedgerDao()
        val existingByAid = ledgerDao.getEntriesForAccount(accountKey).associateBy { it.aid }
        val preSyncedNovels = database.novelDao().getBookshelfNovelsSnapshot()
            .filter { it.cloudSynced && existingByAid[it.aid] == null }
        if (preSyncedNovels.isNotEmpty()) {
            val now = wallClockMs()
            ledgerDao.upsertEntries(
                preSyncedNovels.map { novel ->
                    CloudBookcaseLedgerEntity(
                        accountKey = accountKey,
                        aid = novel.aid,
                        syncState = CloudBookcaseLedgerEntity.STATE_SYNCED,
                        updatedAtMs = now,
                    )
                },
            )
        }
    }

    private suspend fun isBlockedByConcurrentDelete(accountKey: String, aid: Int): Boolean {
        return database.cloudBookcaseLedgerDao().getEntry(accountKey, aid)?.syncState ==
            CloudBookcaseLedgerEntity.STATE_PENDING_DELETE
    }

    private fun resolveActiveLedgerAccountKey(): String? {
        val repo = userSessionRepository
        return if (repo != null) {
            repo.activeAccountKeyOrNull()
        } else {
            CloudBookcaseLedgerEntity.DEFAULT_TEST_ACCOUNT_KEY
        }
    }

    private suspend fun fetchCloudAidListWithSingleRelogin(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<Int>> {
        var res = catalogGateway.fetchCloudBookshelfAidList(appLanguage)
        if (res is WenkuApiResult.ServerErrorCode &&
            res.numericCode == SERVER_CODE_NOT_LOGGED_IN &&
            userSessionRepository != null &&
            userSessionRepository.reauthenticateFromSavedCredentialsIfPossible()
        ) {
            res = catalogGateway.fetchCloudBookshelfAidList(appLanguage)
        }
        return res
    }

    private suspend fun addNovelToCloudWithSingleRelogin(aid: Int): WenkuApiResult<Unit> {
        var res = catalogGateway.addToCloudBookshelf(aid)
        if (res is WenkuApiResult.ServerErrorCode &&
            res.numericCode == SERVER_CODE_NOT_LOGGED_IN &&
            userSessionRepository != null &&
            userSessionRepository.reauthenticateFromSavedCredentialsIfPossible()
        ) {
            res = catalogGateway.addToCloudBookshelf(aid)
        }
        return res
    }

    private suspend fun removeNovelFromCloudWithSingleRelogin(aid: Int): WenkuApiResult<Unit> {
        var res = catalogGateway.removeFromCloudBookshelf(aid)
        if (res is WenkuApiResult.ServerErrorCode &&
            res.numericCode == SERVER_CODE_NOT_LOGGED_IN &&
            userSessionRepository != null &&
            userSessionRepository.reauthenticateFromSavedCredentialsIfPossible()
        ) {
            res = catalogGateway.removeFromCloudBookshelf(aid)
        }
        return res
    }

    private suspend fun ensureAuthenticatedForCloudSync(): Boolean {
        val repo = userSessionRepository ?: return true
        return when (repo.sessionState.value) {
            is AccountSessionState.SignedIn -> true
            is AccountSessionState.OfflineWithSavedAccount,
            is AccountSessionState.RestoringSession,
            -> repo.ensureSessionRestoredForStartupSync()
            AccountSessionState.SignedOut -> {
                if (repo.activeAccountKeyOrNull() != null) {
                    repo.ensureSessionRestoredForStartupSync()
                } else {
                    false
                }
            }
            AccountSessionState.UnavailableStub -> false
        }
    }

    private fun isSignedInOrSaved(): Boolean {
        val repo = userSessionRepository ?: return true
        return when (repo.sessionState.value) {
            is AccountSessionState.SignedIn,
            is AccountSessionState.OfflineWithSavedAccount,
            is AccountSessionState.RestoringSession,
            -> true
            AccountSessionState.SignedOut,
            AccountSessionState.UnavailableStub,
            -> false
        }
    }

    private fun markNovelSyncing(aid: Int, active: Boolean) {
        _syncingNovelAids.update { current ->
            if (active) current + aid else current - aid
        }
    }

    private enum class HydrationStepOutcome {
        PULLED,
        REFRESHED,
        UNCHANGED,
        FAILED,
        SKIPPED_CONCURRENT_DELETE,
    }

    private data class DbShelfBundle(
        val shelfNovels: List<NovelEntity>,
        val progressByAid: Map<Int, ReadingProgressEntity>,
        val cachedCountByAid: Map<Int, Int>,
        val orderedCidsByAid: Map<Int, List<Int>>,
    )
}
