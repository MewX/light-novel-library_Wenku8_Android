package org.mewx.wenku8.v2.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.v2.data.remote.api.WenkuApiResult
import org.mewx.wenku8.v2.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.v2.domain.model.DiscoveryNovelCardItem

data class ChapterRowModel(
    val cid: Int,
    val vid: Int,
    val chapterOrder: Int,
    val chapterTitle: String,
    val isCached: Boolean,
    val isIllustrationChapter: Boolean,
    val isCurrentReadingChapter: Boolean,
)

data class VolumeAccordionModel(
    val vid: Int,
    val volumeOrder: Int,
    val volumeTitle: String,
    val chapters: List<ChapterRowModel>,
    val illustrationChapter: ChapterRowModel?,
    val cachedChapterCount: Int,
    val totalChapterCount: Int,
    val containsReadingProgress: Boolean,
)

data class NovelDetailSnapshot(
    val aid: Int,
    val novel: NovelEntity?,
    val coverUrl: String,
    val volumes: List<VolumeAccordionModel>,
    val readingProgress: ReadingProgressEntity?,
    val totalCachedChapters: Int,
    val totalChapters: Int,
    val tags: List<String>,
) {
    val firstReadableChapter: ChapterRowModel?
        get() = volumes.firstOrNull { it.chapters.isNotEmpty() }?.chapters?.firstOrNull()

    val latestReadableChapter: ChapterRowModel?
        get() = volumes.lastOrNull { it.chapters.isNotEmpty() }?.chapters?.lastOrNull()

    val readingProgressPercent: Int
        get() = ((readingProgress?.progressPermille ?: 0).coerceIn(0, 1000) / 10)
}

sealed interface NovelDetailRefreshOutcome {
    data object Success : NovelDetailRefreshOutcome
    data object OfflineOrCachedOnly : NovelDetailRefreshOutcome
    data object ApiNotConfigured : NovelDetailRefreshOutcome
    data class Failed(val debugDetail: String? = null) : NovelDetailRefreshOutcome
}

/**
 * Offline-first repository powering `NovelDetailScreen` (`05-screen-by-screen-ui-specifications.md` §Screen 5).
 *
 * Architectural guarantees:
 * 1. **Instant Local Rendering**: Combines Room's `novels`, `volumes`, `chapters`, `chapter_contents`,
 *    and `reading_progress` streams immediately so cached/bookshelf novels open in `< 5ms` even in
 *    airplane mode.
 * 2. **Non-Destructive Metadata Merge**: Refreshes `do=meta` + `do=intro` + `do=list` concurrently,
 *    merging server metadata via `mergeServerMetadataPreservingShelfState` so `in_bookshelf`,
 *    `shelf_category`, `cloud_synced`, and `shelf_sort_order` are never overwritten.
 * 3. **Atomic Monotonic TOC Sync**: Commits `do=list` strictly through [NovelTocSyncRepository]
 *    (`@Upsert` + `toc_version` + automatic `reading_progress.vid` healing).
 */
class NovelDetailRepository(
    private val database: Wenku8Database,
    private val catalogGateway: WenkuCatalogGateway,
    private val novelTocSyncRepository: NovelTocSyncRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val wallClockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val novelDao = database.novelDao()
    private val tocDao = database.tocDao()
    private val chapterContentDao = database.chapterContentDao()
    private val readingProgressDao = database.readingProgressDao()

    fun observeNovelDetail(aid: Int): Flow<NovelDetailSnapshot> {
        val coverUrl = catalogGateway.resolveCoverUrl(aid)
        return combine(
            novelDao.observeNovelByAid(aid),
            tocDao.observeVolumesForNovel(aid),
            tocDao.observeChaptersForNovel(aid),
            chapterContentDao.observeCachedChapterIdsForNovel(aid),
            readingProgressDao.observeProgressForNovel(aid),
        ) { novel, volumes, chapters, cachedCidList, progress ->
            val cachedCidSet = cachedCidList.toHashSet()
            val chaptersByVid = chapters.groupBy { it.vid }

            val volumeModels = volumes
                .sortedBy { it.volumeOrder }
                .map { volume ->
                    val orderedChapters = chaptersByVid[volume.vid]
                        .orEmpty()
                        .sortedBy { it.chapterOrder }
                        .map { chapter ->
                            val isIllus = chapter.isIllustrationChapter ||
                                isIllustrationChapterTitle(chapter.chapterTitle)
                            ChapterRowModel(
                                cid = chapter.cid,
                                vid = volume.vid,
                                chapterOrder = chapter.chapterOrder,
                                chapterTitle = chapter.chapterTitle,
                                isCached = chapter.cid in cachedCidSet,
                                isIllustrationChapter = isIllus,
                                isCurrentReadingChapter = progress?.cid == chapter.cid,
                            )
                        }

                    val cachedInVolume = orderedChapters.count { it.isCached }
                    val illustrationRow = orderedChapters.firstOrNull { it.isIllustrationChapter }
                    val hasProgress = progress != null &&
                        (progress.vid == volume.vid || orderedChapters.any { it.cid == progress.cid })

                    VolumeAccordionModel(
                        vid = volume.vid,
                        volumeOrder = volume.volumeOrder,
                        volumeTitle = volume.volumeTitle,
                        chapters = orderedChapters,
                        illustrationChapter = illustrationRow,
                        cachedChapterCount = cachedInVolume,
                        totalChapterCount = orderedChapters.size,
                        containsReadingProgress = hasProgress,
                    )
                }

            val totalChaptersCount = volumeModels.sumOf { it.totalChapterCount }
            val totalCachedCount = volumeModels.sumOf { it.cachedChapterCount }
                .coerceAtLeast(cachedCidSet.size.takeIf { volumeModels.isEmpty() } ?: 0)

            NovelDetailSnapshot(
                aid = aid,
                novel = novel,
                coverUrl = coverUrl,
                volumes = volumeModels,
                readingProgress = progress,
                totalCachedChapters = totalCachedCount,
                totalChapters = totalChaptersCount,
                tags = parseNormalizedTags(novel?.tagsCsv.orEmpty()),
            )
        }
    }

    /**
     * Seeds initial discovery card fields into `novels` (preserving any existing shelf state) so
     * tapping a card from `最新` / `排行` / `搜索` renders title, author, tags, and short intro on
     * frame 1 while full metadata and volume TOC stream in.
     */
    suspend fun seedFromDiscoveryItemIfNeeded(
        item: DiscoveryNovelCardItem,
        appLanguage: Wenku8API.AppLanguage,
    ) = withContext(ioDispatcher) {
        if (item.aid <= 0) return@withContext
        val nowMs = wallClockMs()
        val existing = novelDao.getNovelByAid(item.aid)
        val seedEntity = NovelEntity(
            aid = item.aid,
            contentLang = existing?.contentLang?.ifBlank { appLanguage.name } ?: appLanguage.name,
            metaSyncedAtMs = existing?.metaSyncedAtMs ?: 0L,
            title = item.title.ifBlank { existing?.title ?: "AID #${item.aid}" },
            author = item.author.ifBlank { existing?.author.orEmpty() },
            bookStatus = item.bookStatus.ifBlank { existing?.bookStatus.orEmpty() },
            lastUpdate = item.lastUpdate.ifBlank { existing?.lastUpdate.orEmpty() },
            totalHits = existing?.totalHits ?: 0,
            pushCount = existing?.pushCount ?: 0,
            favCount = existing?.favCount ?: 0,
            tagsCsv = item.tags.joinToString(" ").ifBlank { existing?.tagsCsv.orEmpty() },
            introShort = item.introShort.ifBlank { existing?.introShort.orEmpty() },
            lastAccessedMs = nowMs,
        )
        novelDao.mergeServerMetadataPreservingShelfState(seedEntity)
    }

    /**
     * Clears the `新章节` unread-update flag once the user inspects or opens the novel.
     */
    suspend fun acknowledgeUnreadUpdateIfPresent(aid: Int) = withContext(ioDispatcher) {
        val existing = novelDao.getNovelByAid(aid) ?: return@withContext
        if (existing.hasUnreadUpdate) {
            novelDao.setHasUnreadUpdate(aid = aid, hasUnreadUpdate = false)
        }
    }

    /**
     * Refreshes full metadata (`do=meta`), full synopsis (`do=intro`), `#Tags` fallback
     * (`do=bookinfo`), and volume/chapter index (`do=list`) without ever wiping cached offline data
     * when the network is unreachable.
     */
    suspend fun refreshNovelDetail(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
        fallbackSeedItem: DiscoveryNovelCardItem? = null,
    ): NovelDetailRefreshOutcome = withContext(ioDispatcher) {
        if (aid <= 0) return@withContext NovelDetailRefreshOutcome.Failed("invalid_aid")
        if (fallbackSeedItem != null) {
            seedFromDiscoveryItemIfNeeded(fallbackSeedItem, appLanguage)
        }

        if (!catalogGateway.isApiConfigured) {
            return@withContext NovelDetailRefreshOutcome.ApiNotConfigured
        }

        val requestStartedAtMs = wallClockMs()
        val existingBeforeRefresh = novelDao.getNovelByAid(aid)

        val (metaRes, introRes, tocRes) = coroutineScope {
            val deferredMeta = async { catalogGateway.fetchNovelFullMeta(aid, appLanguage) }
            val deferredIntro = async { catalogGateway.fetchNovelFullIntro(aid, appLanguage) }
            val deferredToc = async { catalogGateway.fetchNovelVolumeToc(aid, appLanguage) }
            Triple(deferredMeta.await(), deferredIntro.await(), deferredToc.await())
        }

        val anySuccess = metaRes is WenkuApiResult.Success ||
            introRes is WenkuApiResult.Success ||
            tocRes is WenkuApiResult.Success

        if (!anySuccess) {
            val existingVolumes = tocDao.getVolumesForNovel(aid)
            if (existingBeforeRefresh != null || existingVolumes.isNotEmpty()) {
                return@withContext NovelDetailRefreshOutcome.OfflineOrCachedOnly
            }
            val debugMsg = (metaRes as? WenkuApiResult.NetworkError)?.debugDetail
                ?: (tocRes as? WenkuApiResult.NetworkError)?.debugDetail
            return@withContext if (metaRes is WenkuApiResult.NetworkError || tocRes is WenkuApiResult.NetworkError) {
                NovelDetailRefreshOutcome.OfflineOrCachedOnly
            } else {
                NovelDetailRefreshOutcome.Failed(debugMsg)
            }
        }

        val fullMeta = (metaRes as? WenkuApiResult.Success)?.data
        val fullIntroText = (introRes as? WenkuApiResult.Success)?.data?.trim().orEmpty()

        // If neither the local row nor do=meta carried #Tags (e.g., direct AID search or cloud shelf),
        // best-effort hydrate tags via do=bookinfo so #Tag chips always appear on NovelDetailScreen.
        var resolvedTagsCsv = fullMeta?.tagsCsv?.takeIf { it.isNotBlank() }
            ?: existingBeforeRefresh?.tagsCsv.orEmpty()
        var resolvedIntroShort = existingBeforeRefresh?.introShort.orEmpty()
        if (resolvedTagsCsv.isBlank()) {
            val shortRes = catalogGateway.fetchNovelShortInfo(aid, appLanguage)
            if (shortRes is WenkuApiResult.Success) {
                resolvedTagsCsv = shortRes.data.tagsCsv
                if (resolvedIntroShort.isBlank()) {
                    resolvedIntroShort = shortRes.data.introShort
                }
            }
        }

        val currentStored = novelDao.getNovelByAid(aid)
        val mergedTitle = fullMeta?.title?.takeIf { it.isNotBlank() }
            ?: currentStored?.title
            ?: fallbackSeedItem?.title
            ?: "AID #$aid"

        val incomingNovel = NovelEntity(
            aid = aid,
            contentLang = appLanguage.name,
            metaSyncedAtMs = requestStartedAtMs,
            title = mergedTitle,
            author = fullMeta?.author.orEmpty().ifBlank { currentStored?.author.orEmpty() },
            bookStatus = fullMeta?.bookStatus.orEmpty().ifBlank { currentStored?.bookStatus.orEmpty() },
            pressId = fullMeta?.pressId.orEmpty().ifBlank { currentStored?.pressId.orEmpty() },
            bookLength = fullMeta?.bookLength?.takeIf { it > 0 } ?: (currentStored?.bookLength ?: 0),
            lastUpdate = fullMeta?.lastUpdate.orEmpty().ifBlank { currentStored?.lastUpdate.orEmpty() },
            latestSectionCid = fullMeta?.latestSectionCid?.takeIf { it > 0 }
                ?: (currentStored?.latestSectionCid ?: 0),
            latestSectionName = fullMeta?.latestSectionName.orEmpty()
                .ifBlank { currentStored?.latestSectionName.orEmpty() },
            dayHits = fullMeta?.dayHitsCount?.takeIf { it > 0 } ?: (currentStored?.dayHits ?: 0),
            totalHits = fullMeta?.totalHitsCount?.takeIf { it > 0 } ?: (currentStored?.totalHits ?: 0),
            pushCount = fullMeta?.pushCount?.takeIf { it > 0 } ?: (currentStored?.pushCount ?: 0),
            favCount = fullMeta?.favCount?.takeIf { it > 0 } ?: (currentStored?.favCount ?: 0),
            tagsCsv = resolvedTagsCsv,
            introShort = resolvedIntroShort.ifBlank {
                fullIntroText.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(120).orEmpty()
            },
            fullIntro = fullIntroText.ifBlank { currentStored?.fullIntro.orEmpty() },
            lastAccessedMs = requestStartedAtMs,
        )
        novelDao.mergeServerMetadataPreservingShelfState(incomingNovel)
        if (currentStored?.hasUnreadUpdate == true) {
            novelDao.setHasUnreadUpdate(aid = aid, hasUnreadUpdate = false)
        }

        if (tocRes is WenkuApiResult.Success && tocRes.data.isNotEmpty()) {
            novelTocSyncRepository.syncNovelIndexAtomically(
                aid = aid,
                language = appLanguage,
                requestStartedAtMs = requestStartedAtMs,
                parsedVolumes = tocRes.data,
                fallbackNovelTitle = mergedTitle,
            )
        }

        NovelDetailRefreshOutcome.Success
    }

    companion object {
        private val TAG_SPLITTER_REGEX = Regex("[\\s,，/、]+")

        fun parseNormalizedTags(tagsCsv: String): List<String> {
            if (tagsCsv.isBlank()) return emptyList()
            return tagsCsv
                .split(TAG_SPLITTER_REGEX)
                .map { it.trim().trimStart('#') }
                .filter { it.isNotEmpty() }
                .distinct()
        }

        fun isIllustrationChapterTitle(rawTitle: String): Boolean {
            val trimmed = rawTitle.trim()
            if (trimmed.isEmpty()) return false
            return trimmed == ChapterEntity.ILLUSTRATION_CHAPTER_TITLE_SC ||
                trimmed == ChapterEntity.ILLUSTRATION_CHAPTER_TITLE_TC ||
                trimmed.endsWith("插图") ||
                trimmed.endsWith("插圖") ||
                trimmed == "彩页" ||
                trimmed == "彩頁"
        }
    }
}
