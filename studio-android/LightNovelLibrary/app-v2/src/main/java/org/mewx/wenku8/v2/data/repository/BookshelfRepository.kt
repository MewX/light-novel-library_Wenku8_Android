package org.mewx.wenku8.v2.data.repository

import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.v2.data.remote.api.WenkuApiResult
import org.mewx.wenku8.v2.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.v2.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.v2.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.v2.util.DigestUtils
import java.nio.charset.StandardCharsets
import java.text.Collator
import java.util.Locale

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
) {
    val aid: Int
        get() = novel.aid

    val isCompleted: Boolean
        get() = novel.bookStatus.contains("完")

    val isCachedOffline: Boolean
        get() = cachedChapterCount > 0

    val readingProgressFraction: Float
        get() = ((readingProgress?.progressPermille ?: 0).coerceIn(0, 1000)) / 1000f

    val readingProgressPercent: Int
        get() = ((readingProgress?.progressPermille ?: 0).coerceIn(0, 1000) + 5) / 10
}

data class BookshelfSnapshot(
    val allShelfItems: List<BookshelfItemModel> = emptyList(),
    val continueReadingItems: List<BookshelfItemModel> = emptyList(),
    val availableCategories: List<String> = emptyList(),
    val availableImprints: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val isCloudSyncRunning: Boolean = false,
    val syncingNovelAids: Set<Int> = emptySet(),
)

sealed interface CloudBookshelfSyncOutcome {
    data class Success(
        val pulledFromCloudCount: Int,
        val pushedToCloudCount: Int,
        val refreshedStaleCount: Int,
        val cloudShelfFullReached: Boolean,
    ) : CloudBookshelfSyncOutcome

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
)

/**
 * Reactive Bookshelf 2.0 & Non-Destructive Cloud Bookcase Sync repository (`P4.1`).
 *
 * Guarantees:
 * 1. **Non-Destructive 2-Way Cloud Sync (`BookshelfSync` Parity)**:
 *    - Local-only books (`in_bookshelf = 1` on device, absent from `action=bookcase`) are **never**
 *      dropped locally; they are pushed up via `action=bookcase&do=add&aid={aid}`.
 *    - Handles `SYSTEM_5_ALREADY_IN_BOOKSHELF` (`5`) as success, stops pushing gracefully on
 *      `SYSTEM_6_BOOKSHELF_FULL` (`6`) while keeping all local books intact, and re-authenticates
 *      at most once on `SYSTEM_4_NOT_LOGGED_IN` (`4`).
 * 2. **Single-Request Cloud Update Diff**:
 *    - Compares cloud `date` and `latestChapterCid` against `novels` in `wenku8_v2.db`, setting
 *      `has_unread_update = true` (`新章节` badge under `有新章节` `FilterChip`) whenever cloud
 *      reports a newer chapter or update date.
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
    }

    private val syncMutex = Mutex()
    private val _isCloudSyncRunning = MutableStateFlow(false)
    val isCloudSyncRunning: StateFlow<Boolean> = _isCloudSyncRunning.asStateFlow()

    private val _syncingNovelAids = MutableStateFlow<Set<Int>>(emptySet())
    val syncingNovelAids: StateFlow<Set<Int>> = _syncingNovelAids.asStateFlow()

    val isApiConfigured: Boolean
        get() = catalogGateway.isApiConfigured

    fun observeBookshelfSnapshot(): Flow<BookshelfSnapshot> {
        val dbSnapshotFlow = combine(
            database.novelDao().observeBookshelfNovels(),
            database.readingProgressDao().observeAllProgress(),
            database.chapterContentDao().observeCachedChapterCountsByNovel(),
            database.tocDao().observeTotalChapterCountsByNovel(),
        ) { shelfNovels, progressList, cachedCounts, totalCounts ->
            DbShelfBundle(
                shelfNovels = shelfNovels,
                progressByAid = progressList.associateBy { it.aid },
                cachedCountByAid = cachedCounts.associate { it.aid to it.cachedCount },
                totalCountByAid = totalCounts.associate { it.aid to it.totalCount },
            )
        }

        return combine(
            dbSnapshotFlow,
            _isCloudSyncRunning,
            _syncingNovelAids,
        ) { bundle, cloudSyncRunning, activeSyncAids ->
            val items = bundle.shelfNovels.map { novel ->
                BookshelfItemModel(
                    novel = novel,
                    coverUrl = catalogGateway.resolveCoverUrl(novel.aid),
                    readingProgress = bundle.progressByAid[novel.aid],
                    cachedChapterCount = bundle.cachedCountByAid[novel.aid] ?: 0,
                    totalChapterCount = bundle.totalCountByAid[novel.aid] ?: 0,
                    tags = splitTagsCsv(novel.tagsCsv),
                    isActivelySyncingOrDownloading = novel.aid in activeSyncAids,
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
                syncingNovelAids = activeSyncAids,
            )
        }
    }

    /**
     * Executes non-destructive 2-way Cloud Bookcase synchronization (`action=bookcase`).
     */
    suspend fun syncWithCloudBookcase(
        appLanguage: Wenku8API.AppLanguage,
        forceRefreshAll: Boolean = false,
    ): CloudBookshelfSyncOutcome = withContext(ioDispatcher) {
        syncMutex.withLock {
            if (!catalogGateway.isApiConfigured) {
                return@withLock CloudBookshelfSyncOutcome.ApiNotConfigured
            }
            if (!ensureAuthenticatedForCloudSync()) {
                return@withLock CloudBookshelfSyncOutcome.NotLoggedIn
            }

            _isCloudSyncRunning.value = true
            try {
                var cloudResult = catalogGateway.fetchCloudBookshelf(appLanguage)
                if (cloudResult is WenkuApiResult.ServerErrorCode &&
                    cloudResult.numericCode == SERVER_CODE_NOT_LOGGED_IN &&
                    userSessionRepository != null &&
                    userSessionRepository.reauthenticateFromSavedCredentialsIfPossible()
                ) {
                    cloudResult = catalogGateway.fetchCloudBookshelf(appLanguage)
                }

                val cloudEntries = when (cloudResult) {
                    is WenkuApiResult.Success -> cloudResult.data
                    WenkuApiResult.ApiNotConfigured -> return@withLock CloudBookshelfSyncOutcome.ApiNotConfigured
                    is WenkuApiResult.ServerErrorCode -> {
                        return@withLock if (cloudResult.numericCode == SERVER_CODE_NOT_LOGGED_IN) {
                            CloudBookshelfSyncOutcome.NotLoggedIn
                        } else {
                            CloudBookshelfSyncOutcome.NetworkOrServerError(numericCode = cloudResult.numericCode)
                        }
                    }
                    is WenkuApiResult.NetworkError -> {
                        return@withLock CloudBookshelfSyncOutcome.NetworkOrServerError(debugDetail = cloudResult.debugDetail)
                    }
                    is WenkuApiResult.ParseError -> {
                        return@withLock CloudBookshelfSyncOutcome.NetworkOrServerError(debugDetail = cloudResult.rawPreview)
                    }
                }

                val localSnapshot = database.novelDao().getBookshelfNovelsSnapshot()
                val localByAid = localSnapshot.associateBy { it.aid }
                val cloudAidSet = cloudEntries.asSequence().map { it.aid }.toHashSet()

                var nextSortOrder = (database.novelDao().getMaxBookshelfSortOrder() + 1).coerceAtLeast(1)
                var pulledCount = 0
                var refreshedStaleCount = 0

                // 1. Pull cloud-only novels & refresh stale shared novels in cloud order.
                for (entry in cloudEntries) {
                    val held = localByAid[entry.aid]
                    if (held == null) {
                        markNovelSyncing(entry.aid, active = true)
                        try {
                            hydrateCloudEntryOntoLocalShelf(
                                entry = entry,
                                sortOrder = nextSortOrder++,
                                appLanguage = appLanguage,
                            )
                            pulledCount++
                        } finally {
                            markNovelSyncing(entry.aid, active = false)
                        }
                    } else if (isLocalNovelStaleAgainstCloud(held, entry, forceRefreshAll)) {
                        markNovelSyncing(entry.aid, active = true)
                        try {
                            refreshExistingShelfNovelFromCloud(
                                held = held,
                                entry = entry,
                                appLanguage = appLanguage,
                            )
                            refreshedStaleCount++
                        } finally {
                            markNovelSyncing(entry.aid, active = false)
                        }
                    } else if (!held.cloudSynced) {
                        database.novelDao().setCloudSynced(entry.aid, true)
                    }
                }

                // 2. Push local-only novels up to cloud (NEVER deleting them locally!).
                var pushedCount = 0
                var cloudFullReached = false
                val localOnlyNovels = localSnapshot.filter { it.aid !in cloudAidSet }
                for (localOnly in localOnlyNovels) {
                    when (val pushResult = addNovelToCloudWithSingleRelogin(localOnly.aid)) {
                        is WenkuApiResult.Success -> {
                            database.novelDao().setCloudSynced(localOnly.aid, true)
                            pushedCount++
                        }
                        is WenkuApiResult.ServerErrorCode -> when (pushResult.numericCode) {
                            SERVER_CODE_ALREADY_IN_BOOKSHELF -> {
                                database.novelDao().setCloudSynced(localOnly.aid, true)
                                pushedCount++
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
                )
            } finally {
                _isCloudSyncRunning.value = false
            }
        }
    }

    /**
     * Smart-updates metadata, atomic TOC, and any missing chapters for novels that already have
     * offline chapter caches (powering the shelf header & multi-select `智能更新` action).
     */
    suspend fun smartUpdateShelfNovels(
        aids: List<Int>,
        appLanguage: Wenku8API.AppLanguage,
    ): SmartBatchUpdateSummary = withContext(ioDispatcher) {
        val targetAids = aids.distinct().filter { it > 0 }
        if (targetAids.isEmpty() || !catalogGateway.isApiConfigured) {
            return@withContext SmartBatchUpdateSummary(0, 0, 0)
        }

        var updatedNovels = 0
        var downloadedChapters = 0
        var failedNovels = 0

        for (aid in targetAids) {
            markNovelSyncing(aid, active = true)
            try {
                val startedAt = wallClockMs()
                val existing = database.novelDao().getNovelByAid(aid)
                val metaRes = catalogGateway.fetchNovelFullMeta(aid, appLanguage)
                val tocRes = catalogGateway.fetchNovelVolumeToc(aid, appLanguage)

                if (metaRes !is WenkuApiResult.Success && tocRes !is WenkuApiResult.Success) {
                    failedNovels++
                    continue
                }

                if (metaRes is WenkuApiResult.Success) {
                    val m = metaRes.data
                    val incoming = (existing ?: NovelEntity(aid = aid, title = m.title, inBookshelf = true)).copy(
                        contentLang = appLanguage.name,
                        metaSyncedAtMs = startedAt,
                        title = m.title.ifBlank { existing?.title ?: "AID $aid" },
                        author = m.author.ifBlank { existing?.author.orEmpty() },
                        bookStatus = m.bookStatus.ifBlank { existing?.bookStatus.orEmpty() },
                        pressId = m.pressId.ifBlank { existing?.pressId.orEmpty() },
                        bookLength = if (m.bookLength > 0) m.bookLength else (existing?.bookLength ?: 0),
                        lastUpdate = m.lastUpdate.ifBlank { existing?.lastUpdate.orEmpty() },
                        latestSectionCid = if (m.latestSectionCid > 0) m.latestSectionCid else (existing?.latestSectionCid ?: 0),
                        latestSectionName = m.latestSectionName.ifBlank { existing?.latestSectionName.orEmpty() },
                        dayHits = m.dayHitsCount,
                        totalHits = m.totalHitsCount,
                        pushCount = m.pushCount,
                        favCount = m.favCount,
                    )
                    database.novelDao().mergeServerMetadataPreservingShelfState(incoming)
                }

                if (tocRes is WenkuApiResult.Success) {
                    novelTocSyncRepository.syncNovelIndexAtomically(
                        aid = aid,
                        language = appLanguage,
                        requestStartedAtMs = startedAt,
                        parsedVolumes = tocRes.data,
                        fallbackNovelTitle = existing?.title ?: "AID $aid",
                    )

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
                            val contentRes = catalogGateway.fetchChapterContent(aid, cid, appLanguage)
                            if (contentRes is WenkuApiResult.Success) {
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

    /**
     * Adds a novel to the local bookshelf immediately (unlimited capacity) and best-effort pushes
     * it to the cloud bookcase when signed in.
     */
    suspend fun addNovelToBookshelf(
        novel: NovelEntity,
        category: String = NovelEntity.DEFAULT_SHELF_CATEGORY,
        syncToCloudIfSignedIn: Boolean = true,
    ) = withContext(ioDispatcher) {
        val sortOrder = database.withTransaction {
            val existing = database.novelDao().getNovelByAid(novel.aid)
            val nextOrder = if (existing?.inBookshelf == true) {
                existing.shelfSortOrder
            } else {
                (database.novelDao().getMinBookshelfSortOrder() - 1)
            }
            val merged = (existing ?: novel).copy(
                title = novel.title.ifBlank { existing?.title ?: "AID ${novel.aid}" },
                author = novel.author.ifBlank { existing?.author.orEmpty() },
                bookStatus = novel.bookStatus.ifBlank { existing?.bookStatus.orEmpty() },
                pressId = novel.pressId.ifBlank { existing?.pressId.orEmpty() },
                lastUpdate = novel.lastUpdate.ifBlank { existing?.lastUpdate.orEmpty() },
                tagsCsv = novel.tagsCsv.ifBlank { existing?.tagsCsv.orEmpty() },
                introShort = novel.introShort.ifBlank { existing?.introShort.orEmpty() },
                inBookshelf = true,
                shelfCategory = category.ifBlank { NovelEntity.DEFAULT_SHELF_CATEGORY },
                shelfSortOrder = nextOrder,
            )
            database.novelDao().upsertNovel(merged)
            nextOrder
        }

        if (syncToCloudIfSignedIn && catalogGateway.isApiConfigured && isSignedInOrSaved()) {
            val res = addNovelToCloudWithSingleRelogin(novel.aid)
            if (res is WenkuApiResult.Success ||
                (res is WenkuApiResult.ServerErrorCode && res.numericCode == SERVER_CODE_ALREADY_IN_BOOKSHELF)
            ) {
                database.novelDao().updateBookshelfState(
                    aid = novel.aid,
                    inBookshelf = true,
                    shelfCategory = category.ifBlank { NovelEntity.DEFAULT_SHELF_CATEGORY },
                    shelfSortOrder = sortOrder,
                    cloudSynced = true,
                )
            }
        }
    }

    /**
     * Removes one or more novels from the local bookshelf (`in_bookshelf = 0`), optionally deleting
     * offline cached chapter texts and best-effort removing from the cloud bookcase when signed in.
     */
    suspend fun removeNovelsFromBookshelf(
        aids: List<Int>,
        removeFromCloudIfSignedIn: Boolean = true,
        deleteOfflineCache: Boolean = false,
    ) = withContext(ioDispatcher) {
        val targetAids = aids.distinct().filter { it > 0 }
        if (targetAids.isEmpty()) return@withContext

        database.withTransaction {
            database.novelDao().removeNovelsFromBookshelf(targetAids)
            if (deleteOfflineCache) {
                database.chapterContentDao().deleteChapterContentsForNovels(targetAids)
            }
        }

        if (removeFromCloudIfSignedIn && catalogGateway.isApiConfigured && isSignedInOrSaved()) {
            for (aid in targetAids) {
                removeNovelFromCloudWithSingleRelogin(aid)
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

    private suspend fun hydrateCloudEntryOntoLocalShelf(
        entry: ParsedCloudBookshelfEntry,
        sortOrder: Int,
        appLanguage: Wenku8API.AppLanguage,
    ) {
        val startedAt = wallClockMs()
        val metaResult = catalogGateway.fetchNovelFullMeta(entry.aid, appLanguage)
        val introResult = catalogGateway.fetchNovelFullIntro(entry.aid, appLanguage)
        val tocResult = catalogGateway.fetchNovelVolumeToc(entry.aid, appLanguage)

        val existing = database.novelDao().getNovelByAid(entry.aid)
        val meta = (metaResult as? WenkuApiResult.Success)?.data
        val fullIntro = (introResult as? WenkuApiResult.Success)?.data.orEmpty()

        val resolvedTitle = meta?.title?.takeIf { it.isNotBlank() }
            ?: entry.title.takeIf { it.isNotBlank() }
            ?: existing?.title?.takeIf { it.isNotBlank() }
            ?: "AID ${entry.aid}"

        val entity = (existing ?: NovelEntity(aid = entry.aid, title = resolvedTitle)).copy(
            contentLang = appLanguage.name,
            metaSyncedAtMs = startedAt,
            title = resolvedTitle,
            author = meta?.author?.ifBlank { existing?.author.orEmpty() } ?: existing?.author.orEmpty(),
            bookStatus = meta?.bookStatus?.ifBlank { existing?.bookStatus.orEmpty() } ?: existing?.bookStatus.orEmpty(),
            pressId = meta?.pressId?.ifBlank { existing?.pressId.orEmpty() } ?: existing?.pressId.orEmpty(),
            bookLength = meta?.bookLength?.takeIf { it > 0 } ?: existing?.bookLength ?: 0,
            lastUpdate = meta?.lastUpdate?.ifBlank { entry.lastUpdateDate } ?: entry.lastUpdateDate,
            latestSectionCid = meta?.latestSectionCid?.takeIf { it > 0 } ?: entry.latestChapterCid,
            latestSectionName = meta?.latestSectionName?.ifBlank { entry.latestChapterName } ?: entry.latestChapterName,
            dayHits = meta?.dayHitsCount ?: existing?.dayHits ?: 0,
            totalHits = meta?.totalHitsCount ?: existing?.totalHits ?: 0,
            pushCount = meta?.pushCount ?: existing?.pushCount ?: 0,
            favCount = meta?.favCount ?: existing?.favCount ?: 0,
            fullIntro = fullIntro.ifBlank { existing?.fullIntro.orEmpty() },
            introShort = existing?.introShort?.ifBlank { fullIntro.take(120) } ?: fullIntro.take(120),
            inBookshelf = true,
            shelfCategory = existing?.shelfCategory?.ifBlank { NovelEntity.DEFAULT_SHELF_CATEGORY }
                ?: NovelEntity.DEFAULT_SHELF_CATEGORY,
            shelfSortOrder = if (existing?.inBookshelf == true) existing.shelfSortOrder else sortOrder,
            cloudSynced = true,
        )
        database.novelDao().upsertNovel(entity)

        if (tocResult is WenkuApiResult.Success) {
            novelTocSyncRepository.syncNovelIndexAtomically(
                aid = entry.aid,
                language = appLanguage,
                requestStartedAtMs = startedAt,
                parsedVolumes = tocResult.data,
                fallbackNovelTitle = resolvedTitle,
            )
        }
    }

    private suspend fun refreshExistingShelfNovelFromCloud(
        held: NovelEntity,
        entry: ParsedCloudBookshelfEntry,
        appLanguage: Wenku8API.AppLanguage,
    ) {
        val startedAt = wallClockMs()
        val hasNewerCloudChapter = (held.latestSectionCid > 0 && entry.latestChapterCid > held.latestSectionCid) ||
            (held.latestSectionCid == 0 && held.lastUpdate.isNotEmpty() && entry.lastUpdateDate > held.lastUpdate)

        val metaResult = catalogGateway.fetchNovelFullMeta(entry.aid, appLanguage)
        val tocResult = catalogGateway.fetchNovelVolumeToc(entry.aid, appLanguage)
        val meta = (metaResult as? WenkuApiResult.Success)?.data

        val updated = held.copy(
            contentLang = appLanguage.name,
            metaSyncedAtMs = maxOf(held.metaSyncedAtMs, startedAt),
            title = meta?.title?.takeIf { it.isNotBlank() }
                ?: entry.title.takeIf { it.isNotBlank() }
                ?: held.title,
            author = meta?.author?.takeIf { it.isNotBlank() } ?: held.author,
            bookStatus = meta?.bookStatus?.takeIf { it.isNotBlank() } ?: held.bookStatus,
            pressId = meta?.pressId?.takeIf { it.isNotBlank() } ?: held.pressId,
            bookLength = meta?.bookLength?.takeIf { it > 0 } ?: held.bookLength,
            lastUpdate = meta?.lastUpdate?.takeIf { it.isNotBlank() }
                ?: entry.lastUpdateDate.ifBlank { held.lastUpdate },
            latestSectionCid = meta?.latestSectionCid?.takeIf { it > 0 }
                ?: entry.latestChapterCid.takeIf { it > 0 }
                ?: held.latestSectionCid,
            latestSectionName = meta?.latestSectionName?.takeIf { it.isNotBlank() }
                ?: entry.latestChapterName.ifBlank { held.latestSectionName },
            dayHits = meta?.dayHitsCount?.takeIf { it > 0 } ?: held.dayHits,
            totalHits = meta?.totalHitsCount?.takeIf { it > 0 } ?: held.totalHits,
            pushCount = meta?.pushCount?.takeIf { it > 0 } ?: held.pushCount,
            favCount = meta?.favCount?.takeIf { it > 0 } ?: held.favCount,
            cloudSynced = true,
            hasUnreadUpdate = held.hasUnreadUpdate || hasNewerCloudChapter,
        )
        database.novelDao().upsertNovel(updated)

        if (tocResult is WenkuApiResult.Success) {
            novelTocSyncRepository.syncNovelIndexAtomically(
                aid = entry.aid,
                language = appLanguage,
                requestStartedAtMs = startedAt,
                parsedVolumes = tocResult.data,
                fallbackNovelTitle = updated.title,
            )
        }
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
            -> repo.reauthenticateFromSavedCredentialsIfPossible()
            AccountSessionState.SignedOut,
            AccountSessionState.UnavailableStub,
            -> false
        }
    }

    private fun isSignedInOrSaved(): Boolean {
        val repo = userSessionRepository ?: return false
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
        _syncingNovelAids.value = if (active) {
            _syncingNovelAids.value + aid
        } else {
            _syncingNovelAids.value - aid
        }
    }

    private data class DbShelfBundle(
        val shelfNovels: List<NovelEntity>,
        val progressByAid: Map<Int, ReadingProgressEntity>,
        val cachedCountByAid: Map<Int, Int>,
        val totalCountByAid: Map<Int, Int>,
    )
}
