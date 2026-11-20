package org.mewx.wenku8.reader.engine

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.dao.ChapterContentDao
import org.mewx.wenku8.data.local.db.dao.OrderedChapterWithVolume
import org.mewx.wenku8.data.local.db.dao.TocDao
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.api.isEffectivelyBlankWenkuText
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.util.DigestUtils

enum class WindowSlotRole {
    PREV,
    CURRENT,
    NEXT,
}

/**
 * State of one of the 3 chapter slots (`prev`, `current`, `next`) inside [ChapterWindowBuffer].
 */
sealed interface ChapterWindowSlot {
    val chapter: OrderedChapterWithVolume
    val virtualPageCount: Int

    data class Unloaded(
        override val chapter: OrderedChapterWithVolume,
    ) : ChapterWindowSlot {
        override val virtualPageCount: Int = 1
    }

    data class Loading(
        override val chapter: OrderedChapterWithVolume,
    ) : ChapterWindowSlot {
        override val virtualPageCount: Int = 1
    }

    data class Loaded(
        override val chapter: OrderedChapterWithVolume,
        val layout: PaginatedChapterLayout,
    ) : ChapterWindowSlot {
        override val virtualPageCount: Int
            get() = layout.pages.size.coerceAtLeast(1)
    }

    data class Error(
        override val chapter: OrderedChapterWithVolume,
        val message: String,
        val kind: ChapterLoadErrorKind = ChapterLoadErrorKind.GENERIC,
    ) : ChapterWindowSlot {
        override val virtualPageCount: Int = 1
    }
}

enum class ChapterLoadErrorKind {
    GENERIC,
    EMPTY_SERVER_CONTENT,
    NETWORK_UNAVAILABLE,
    SERVER_ERROR,
    PARSE_ERROR,
    API_NOT_CONFIGURED,
}

internal class ChapterLoadFailureException(
    val kind: ChapterLoadErrorKind,
    override val message: String,
) : IllegalStateException(message)

/**
 * Maps a continuous virtual Pager index (`0 until totalVirtualPages`) to its owning chapter slot
 * and local `0`-based page index within that chapter.
 */
data class VirtualPageResolution(
    val virtualPageIndex: Int,
    val role: WindowSlotRole,
    val slot: ChapterWindowSlot,
    val pageIndexInSlot: Int,
    val page: ReaderPage?,
)

/**
 * Immutable snapshot emitted by [ChapterWindowBuffer.snapshotFlow].
 */
data class ReaderWindowSnapshot(
    val aid: Int = 0,
    val orderedToc: List<OrderedChapterWithVolume> = emptyList(),
    val currentTocIndex: Int = 0,
    val prevSlot: ChapterWindowSlot? = null,
    val currentSlot: ChapterWindowSlot? = null,
    val nextSlot: ChapterWindowSlot? = null,
    val layoutConfig: ReaderLayoutConfig? = null,
    val activeVirtualPageIndex: Int = 0,
    /** Increments whenever slot boundaries shift or reflow so Pager can snap cleanly without animation. */
    val coordinateRevision: Long = 0L,
) {
    val prevPageCount: Int
        get() = prevSlot?.virtualPageCount ?: 0

    val currentPageCount: Int
        get() = currentSlot?.virtualPageCount ?: 0

    val nextPageCount: Int
        get() = nextSlot?.virtualPageCount ?: 0

    val totalVirtualPages: Int
        get() = prevPageCount + currentPageCount + nextPageCount

    val activePageIndexInCurrentChapter: Int
        get() = (activeVirtualPageIndex - prevPageCount)
            .coerceIn(0, (currentPageCount - 1).coerceAtLeast(0))

    val activeChapter: OrderedChapterWithVolume?
        get() = currentSlot?.chapter

    val activeReaderPage: ReaderPage?
        get() = (currentSlot as? ChapterWindowSlot.Loaded)
            ?.layout
            ?.pages
            ?.getOrNull(activePageIndexInCurrentChapter)

    val currentChapterProgressRatio: Float
        get() {
            if (currentPageCount <= 0) return 0f
            return ((activePageIndexInCurrentChapter + 1).toFloat() / currentPageCount.toFloat())
                .coerceIn(0f, 1f)
        }

    fun resolveVirtualPage(virtualPageIndex: Int): VirtualPageResolution? {
        if (totalVirtualPages <= 0 || currentSlot == null) return null
        val clamped = virtualPageIndex.coerceIn(0, totalVirtualPages - 1)

        if (prevSlot != null && clamped < prevPageCount) {
            val loadedPage = (prevSlot as? ChapterWindowSlot.Loaded)?.layout?.pages?.getOrNull(clamped)
            return VirtualPageResolution(
                virtualPageIndex = clamped,
                role = WindowSlotRole.PREV,
                slot = prevSlot,
                pageIndexInSlot = clamped,
                page = loadedPage,
            )
        }

        val currentStart = prevPageCount
        val currentEndExclusive = currentStart + currentPageCount
        if (clamped < currentEndExclusive) {
            val localIndex = clamped - currentStart
            val loadedPage = (currentSlot as? ChapterWindowSlot.Loaded)?.layout?.pages?.getOrNull(localIndex)
            return VirtualPageResolution(
                virtualPageIndex = clamped,
                role = WindowSlotRole.CURRENT,
                slot = currentSlot,
                pageIndexInSlot = localIndex,
                page = loadedPage,
            )
        }

        if (nextSlot != null) {
            val localIndex = (clamped - currentEndExclusive).coerceAtLeast(0)
            val loadedPage = (nextSlot as? ChapterWindowSlot.Loaded)?.layout?.pages?.getOrNull(localIndex)
            return VirtualPageResolution(
                virtualPageIndex = clamped,
                role = WindowSlotRole.NEXT,
                slot = nextSlot,
                pageIndexInSlot = localIndex,
                page = loadedPage,
            )
        }

        return null
    }

    /**
     * Stable, collision-free page identity for `HorizontalPager` so cross-chapter/volume window
     * shifts and `prevSlot` expansions preserve subcompositions without aliasing across chapters.
     */
    fun virtualPagePagerKey(virtualPageIndex: Int): Any {
        if (virtualPageIndex !in 0 until totalVirtualPages) {
            return "oob_$virtualPageIndex"
        }
        val res = resolveVirtualPage(virtualPageIndex) ?: return "empty_$virtualPageIndex"
        val slotKind = when (res.slot) {
            is ChapterWindowSlot.Loaded -> 'L'
            is ChapterWindowSlot.Loading -> 'P'
            is ChapterWindowSlot.Unloaded -> 'U'
            is ChapterWindowSlot.Error -> 'E'
        }
        return "${res.slot.chapter.cid}_${slotKind}_${res.pageIndexInSlot}"
    }
}

/**
 * Layer 3: 3-Chapter Sliding Ring Buffer (`ChapterWindowBuffer`) for `Wenku8ReaderEngineV2`
 * (`10-next-gen-reader-engine-architecture.md` §4.3).
 *
 * Responsibilities:
 * 1. **Offline-First Chapter Loading + Auto-Caching**: Reads `chapter_contents` via
 *    [ChapterContentDao.loadChapterTextSafely] (`256,000`-char `SUBSTR` chunking, immune to
 *    Android's `2 MB` `CursorWindow`). On cache miss, fetches `do=text` via [WenkuCatalogGateway]
 *    and persists into `chapter_contents` automatically.
 * 2. **Continuous 3-Chapter Virtual Page Space**: Projects `[prevSlot, currentSlot, nextSlot]` into
 *    a single continuous virtual page range (`totalVirtualPages`) so swiping across chapter and
 *    volume boundaries is a seamless 120fps gesture without loading dialogs.
 * 3. **70% Pre-Fetch & Pre-Pagination**: Automatically pre-fetches and pre-paginates `nextChapter`
 *    in the background as soon as the reader reaches `>= 70%` of `currentChapter`.
 * 4. **Zero-IO Reflow**: Retains parsed [ChapterAst]s for nearby chapters in a bounded cache so
 *    font-size slider drags and orientation changes re-paginate purely in memory on
 *    [computeDispatcher] while preserving the exact [ReadingAnchor].
 */
class ChapterWindowBuffer(
    private val aid: Int,
    private val appLanguageProvider: () -> Wenku8API.AppLanguage,
    private val tocDao: TocDao,
    private val chapterContentDao: ChapterContentDao,
    private val catalogGateway: WenkuCatalogGateway,
    private val novelTocSyncRepository: NovelTocSyncRepository? = null,
    private val paginator: StaticLayoutPaginator = StaticLayoutPaginator(),
    private val scope: CoroutineScope,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val wallClockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val stateMutex = Mutex()
    private val astCache = LinkedHashMap<Int, ChapterAst>()
    private val activeSlotJobs = HashMap<Int, Job>()
    private val inFlightAstLoads = HashMap<Int, Deferred<Result<ChapterAst>>>()

    private val _snapshotFlow = MutableStateFlow(ReaderWindowSnapshot(aid = aid))
    val snapshotFlow: StateFlow<ReaderWindowSnapshot> = _snapshotFlow.asStateFlow()

    /**
     * Opens [cid] at [initialAnchor], loads and paginates `currentSlot`, and schedules neighbor
     * pre-fetching (`prevSlot` immediately, `nextSlot` when initial position is already `>= 70%`).
     */
    suspend fun openChapter(
        cid: Int,
        initialAnchor: ReadingAnchor = ReadingAnchor(),
        layoutConfig: ReaderLayoutConfig,
        fallbackVid: Int = 0,
        fallbackVolumeTitle: String = "",
        fallbackChapterTitle: String = "",
    ): ReaderWindowSnapshot {
        cancelAllBackgroundSlotJobs()

        val orderedToc = resolveOrderedToc(
            targetCid = cid,
            fallbackVid = fallbackVid,
            fallbackVolumeTitle = fallbackVolumeTitle,
            fallbackChapterTitle = fallbackChapterTitle,
        )
        val targetIndex = orderedToc.indexOfFirst { it.cid == cid }.coerceAtLeast(0)
        val currentMeta = orderedToc[targetIndex]
        val prevMeta = orderedToc.getOrNull(targetIndex - 1)
        val nextMeta = orderedToc.getOrNull(targetIndex + 1)

        // Check local memory / SQLite availability first so locally cached chapters render in a
        // single atomic emission without flashing an intermediate Loading skeleton.
        val cachedCurrentAst = stateMutex.withLock { astCache[currentMeta.cid] }
        val preloadedCurrentText = if (cachedCurrentAst == null) {
            withContext(ioDispatcher) { chapterContentDao.loadChapterTextSafely(currentMeta.cid) }
                ?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val needsRemoteFetchForCurrent = cachedCurrentAst == null && preloadedCurrentText == null

        if (needsRemoteFetchForCurrent) {
            stateMutex.withLock {
                val prevSlotInitial = prevMeta?.let { ChapterWindowSlot.Unloaded(it) }
                val nextSlotInitial = nextMeta?.let { ChapterWindowSlot.Unloaded(it) }
                val prevCount = prevSlotInitial?.virtualPageCount ?: 0
                _snapshotFlow.value = ReaderWindowSnapshot(
                    aid = aid,
                    orderedToc = orderedToc,
                    currentTocIndex = targetIndex,
                    prevSlot = prevSlotInitial,
                    currentSlot = ChapterWindowSlot.Loading(currentMeta),
                    nextSlot = nextSlotInitial,
                    layoutConfig = layoutConfig,
                    activeVirtualPageIndex = prevCount,
                    coordinateRevision = _snapshotFlow.value.coordinateRevision + 1L,
                )
            }
        }

        val loadedCurrentSlot = loadAndPaginateSlot(
            chapterMeta = currentMeta,
            config = layoutConfig,
            preloadedLocalText = preloadedCurrentText,
        )
        val initialPageInCurrent = (loadedCurrentSlot as? ChapterWindowSlot.Loaded)
            ?.layout
            ?.resolvePageIndexForAnchor(initialAnchor)
            ?: 0

        // If `prevMeta` is already in memory or cached in local SQLite, paginate it right away
        // (zero network wait!) so `prevSlot` starts `Loaded` without a second coordinate shift.
        val preloadedPrevSlot: ChapterWindowSlot? = if (prevMeta != null) {
            val cachedPrevAst = stateMutex.withLock { astCache[prevMeta.cid] }
            val localPrevText = if (cachedPrevAst == null) {
                withContext(ioDispatcher) { chapterContentDao.loadChapterTextSafely(prevMeta.cid) }
                    ?.takeIf { it.isNotBlank() }
            } else {
                null
            }
            if (cachedPrevAst != null || localPrevText != null) {
                loadAndPaginateSlot(
                    chapterMeta = prevMeta,
                    config = layoutConfig,
                    preloadedLocalText = localPrevText,
                )
            } else {
                null
            }
        } else {
            null
        }

        val postLoadSnapshot = stateMutex.withLock {
            val prevSlot = preloadedPrevSlot ?: prevMeta?.let { ChapterWindowSlot.Loading(it) }
            val nextSlot = nextMeta?.let { ChapterWindowSlot.Unloaded(it) }
            val prevCount = prevSlot?.virtualPageCount ?: 0
            val nextRevision = _snapshotFlow.value.coordinateRevision + 1L
            val updated = ReaderWindowSnapshot(
                aid = aid,
                orderedToc = orderedToc,
                currentTocIndex = targetIndex,
                prevSlot = prevSlot,
                currentSlot = loadedCurrentSlot,
                nextSlot = nextSlot,
                layoutConfig = layoutConfig,
                activeVirtualPageIndex = prevCount + initialPageInCurrent,
                coordinateRevision = nextRevision,
            )
            _snapshotFlow.value = updated
            pruneAstCacheLocked(centerTocIndex = targetIndex, orderedToc = orderedToc)
            updated
        }

        if (prevMeta != null && preloadedPrevSlot == null) {
            scheduleNeighborSlotLoad(prevMeta, layoutConfig)
        }
        if (nextMeta != null && postLoadSnapshot.currentChapterProgressRatio >= PREFETCH_THRESHOLD_RATIO) {
            scheduleNeighborSlotLoad(nextMeta, layoutConfig)
        }

        return _snapshotFlow.value
    }

    /**
     * Called when the Pager settles on [virtualPageIndex].
     *
     * - Within `CURRENT`: updates active page and triggers `nextSlot` pre-fetch at `>= 70%`.
     * - Within `NEXT`: shifts the 3-chapter window forward (`prev = oldCur`, `cur = oldNext`).
     * - Within `PREV`: shifts the 3-chapter window backward (`next = oldCur`, `cur = oldPrev`),
     *   preserving final-page landing when swiping backward across a chapter boundary!
     * - When [expectedCoordinateRevision] is supplied, ignores stale settle callbacks emitted from
     *   an older coordinate space (preventing double chapter-shifts across `prevSlot` updates).
     */
    suspend fun onVirtualPageSettled(
        virtualPageIndex: Int,
        expectedCoordinateRevision: Long? = null,
    ): ReaderWindowSnapshot {
        var neighborToSchedule: Pair<OrderedChapterWithVolume, ReaderLayoutConfig>? = null
        var synchronousCurrentLoadNeeded: Triple<OrderedChapterWithVolume, ReaderLayoutConfig, Boolean>? = null

        stateMutex.withLock {
            val snap = _snapshotFlow.value
            if (expectedCoordinateRevision != null &&
                snap.coordinateRevision != expectedCoordinateRevision
            ) {
                return snap
            }
            val config = snap.layoutConfig ?: return snap
            val resolution = snap.resolveVirtualPage(virtualPageIndex) ?: return snap

            when (resolution.role) {
                WindowSlotRole.CURRENT -> {
                    val updated = snap.copy(activeVirtualPageIndex = resolution.virtualPageIndex)
                    _snapshotFlow.value = updated
                    val nextSlot = updated.nextSlot
                    if (nextSlot is ChapterWindowSlot.Unloaded &&
                        updated.currentChapterProgressRatio >= PREFETCH_THRESHOLD_RATIO
                    ) {
                        _snapshotFlow.value = updated.copy(
                            nextSlot = ChapterWindowSlot.Loading(nextSlot.chapter),
                        )
                        neighborToSchedule = nextSlot.chapter to config
                    }
                }

                WindowSlotRole.NEXT -> {
                    val newTocIndex = snap.currentTocIndex + 1
                    val oldCurrent = snap.currentSlot ?: return snap
                    val oldNext = snap.nextSlot ?: return snap
                    val newNextMeta = snap.orderedToc.getOrNull(newTocIndex + 1)
                    val newNextSlot = newNextMeta?.let { ChapterWindowSlot.Unloaded(it) }

                    val newPrevCount = oldCurrent.virtualPageCount
                    val localPageInNewCurrent = resolution.pageIndexInSlot
                        .coerceIn(0, (oldNext.virtualPageCount - 1).coerceAtLeast(0))

                    val shifted = snap.copy(
                        currentTocIndex = newTocIndex,
                        prevSlot = oldCurrent,
                        currentSlot = if (oldNext is ChapterWindowSlot.Unloaded) {
                            ChapterWindowSlot.Loading(oldNext.chapter)
                        } else {
                            oldNext
                        },
                        nextSlot = newNextSlot,
                        activeVirtualPageIndex = newPrevCount + localPageInNewCurrent,
                        coordinateRevision = snap.coordinateRevision + 1L,
                    )
                    _snapshotFlow.value = shifted
                    pruneAstCacheLocked(centerTocIndex = newTocIndex, orderedToc = snap.orderedToc)

                    if (oldNext !is ChapterWindowSlot.Loaded) {
                        synchronousCurrentLoadNeeded = Triple(oldNext.chapter, config, false)
                    } else if (newNextMeta != null &&
                        shifted.currentChapterProgressRatio >= PREFETCH_THRESHOLD_RATIO
                    ) {
                        _snapshotFlow.value = shifted.copy(
                            nextSlot = ChapterWindowSlot.Loading(newNextMeta),
                        )
                        neighborToSchedule = newNextMeta to config
                    }
                }

                WindowSlotRole.PREV -> {
                    val newTocIndex = (snap.currentTocIndex - 1).coerceAtLeast(0)
                    val oldCurrent = snap.currentSlot ?: return snap
                    val oldPrev = snap.prevSlot ?: return snap
                    val newPrevMeta = snap.orderedToc.getOrNull(newTocIndex - 1)
                    val newPrevSlot = newPrevMeta?.let { ChapterWindowSlot.Loading(it) }

                    val newPrevCount = newPrevSlot?.virtualPageCount ?: 0
                    val localPageInNewCurrent = if (oldPrev is ChapterWindowSlot.Loaded) {
                        resolution.pageIndexInSlot.coerceIn(0, oldPrev.virtualPageCount - 1)
                    } else {
                        0
                    }

                    val shifted = snap.copy(
                        currentTocIndex = newTocIndex,
                        prevSlot = newPrevSlot,
                        currentSlot = if (oldPrev is ChapterWindowSlot.Unloaded) {
                            ChapterWindowSlot.Loading(oldPrev.chapter)
                        } else {
                            oldPrev
                        },
                        nextSlot = oldCurrent,
                        activeVirtualPageIndex = newPrevCount + localPageInNewCurrent,
                        coordinateRevision = snap.coordinateRevision + 1L,
                    )
                    _snapshotFlow.value = shifted
                    pruneAstCacheLocked(centerTocIndex = newTocIndex, orderedToc = snap.orderedToc)

                    if (oldPrev !is ChapterWindowSlot.Loaded) {
                        // User swiped backward before prevSlot finished loading: once loaded, land
                        // on its FINAL page (`landOnLastPage = true`) so backward page-turning feels natural!
                        synchronousCurrentLoadNeeded = Triple(oldPrev.chapter, config, true)
                    }
                    if (newPrevMeta != null) {
                        neighborToSchedule = newPrevMeta to config
                    }
                }
            }
        }

        synchronousCurrentLoadNeeded?.let { (chapterMeta, config, landOnLastPage) ->
            val loaded = loadAndPaginateSlot(chapterMeta, config)
            stateMutex.withLock {
                val curSnap = _snapshotFlow.value
                if (curSnap.currentSlot?.chapter?.cid == chapterMeta.cid) {
                    val targetLocalPage = if (landOnLastPage && loaded is ChapterWindowSlot.Loaded) {
                        (loaded.layout.pages.size - 1).coerceAtLeast(0)
                    } else {
                        0
                    }
                    val updated = curSnap.copy(
                        currentSlot = loaded,
                        activeVirtualPageIndex = curSnap.prevPageCount + targetLocalPage,
                        coordinateRevision = curSnap.coordinateRevision + 1L,
                    )
                    _snapshotFlow.value = updated
                    val nextSlot = updated.nextSlot
                    if (nextSlot is ChapterWindowSlot.Unloaded &&
                        updated.currentChapterProgressRatio >= PREFETCH_THRESHOLD_RATIO
                    ) {
                        _snapshotFlow.value = updated.copy(
                            nextSlot = ChapterWindowSlot.Loading(nextSlot.chapter),
                        )
                        scheduleNeighborSlotLoad(nextSlot.chapter, config)
                    }
                }
            }
        }

        neighborToSchedule?.let { (neighborMeta, config) ->
            scheduleNeighborSlotLoad(neighborMeta, config)
        }

        return _snapshotFlow.value
    }

    /**
     * Synchronizes [ReaderWindowSnapshot.activeVirtualPageIndex] to [anchor] within `currentSlot`
     * while scrolling in `CONTINUOUS_SCROLL` (`上下卷轴`) mode, keeping chapter progress, bookmark
     * detection, 70% `nextSlot` pre-fetching, and mode-switch anchors aligned in real time.
     */
    suspend fun syncActiveAnchorInCurrentChapter(anchor: ReadingAnchor): ReaderWindowSnapshot {
        var neighborToSchedule: Pair<OrderedChapterWithVolume, ReaderLayoutConfig>? = null
        stateMutex.withLock {
            val snap = _snapshotFlow.value
            val config = snap.layoutConfig ?: return snap
            val loadedCur = snap.currentSlot as? ChapterWindowSlot.Loaded ?: return snap
            val resolvedLocalPage = loadedCur.layout.resolvePageIndexForAnchor(anchor)
            val targetVirtual = snap.prevPageCount + resolvedLocalPage
            val updated = if (targetVirtual != snap.activeVirtualPageIndex) {
                snap.copy(activeVirtualPageIndex = targetVirtual).also { _snapshotFlow.value = it }
            } else {
                snap
            }
            val nextSlot = updated.nextSlot
            if (nextSlot is ChapterWindowSlot.Unloaded &&
                updated.currentChapterProgressRatio >= PREFETCH_THRESHOLD_RATIO
            ) {
                _snapshotFlow.value = updated.copy(
                    nextSlot = ChapterWindowSlot.Loading(nextSlot.chapter),
                )
                neighborToSchedule = nextSlot.chapter to config
            }
        }
        neighborToSchedule?.let { (neighborMeta, config) ->
            scheduleNeighborSlotLoad(neighborMeta, config)
        }
        return _snapshotFlow.value
    }

    /**
     * Re-paginates all loaded slots in memory under [newConfig] while preserving the exact
     * paragraph & character reading anchor (`lockedAnchorOverride` or the current active page's
     * start anchor).
     */
    suspend fun updateLayoutConfig(
        newConfig: ReaderLayoutConfig,
        lockedAnchorOverride: ReadingAnchor? = null,
    ): ReaderWindowSnapshot {
        val (prevAst, currentAst, nextAst, anchorToPreserve, snapBefore) = stateMutex.withLock {
            val snap = _snapshotFlow.value
            val anchor = lockedAnchorOverride
                ?: snap.activeReaderPage?.startAnchor
                ?: ReadingAnchor()
            val pAst = (snap.prevSlot as? ChapterWindowSlot.Loaded)?.layout?.ast
                ?: snap.prevSlot?.chapter?.cid?.let { astCache[it] }
            val cAst = (snap.currentSlot as? ChapterWindowSlot.Loaded)?.layout?.ast
                ?: snap.currentSlot?.chapter?.cid?.let { astCache[it] }
            val nAst = (snap.nextSlot as? ChapterWindowSlot.Loaded)?.layout?.ast
                ?: snap.nextSlot?.chapter?.cid?.let { astCache[it] }
            ReflowInputs(pAst, cAst, nAst, anchor, snap)
        }

        if (snapBefore.currentSlot == null) {
            stateMutex.withLock {
                _snapshotFlow.value = _snapshotFlow.value.copy(layoutConfig = newConfig)
            }
            return _snapshotFlow.value
        }

        val repaginatedPrev = prevAst?.let {
            withContext(computeDispatcher) { paginator.paginate(it, newConfig) }
        }
        val repaginatedCurrent = currentAst?.let {
            withContext(computeDispatcher) { paginator.paginate(it, newConfig) }
        }
        val repaginatedNext = nextAst?.let {
            withContext(computeDispatcher) { paginator.paginate(it, newConfig) }
        }

        return stateMutex.withLock {
            val latest = _snapshotFlow.value
            val updatedPrev = when (val p = latest.prevSlot) {
                is ChapterWindowSlot.Loaded -> if (repaginatedPrev != null && repaginatedPrev.ast.cid == p.chapter.cid) {
                    ChapterWindowSlot.Loaded(p.chapter, repaginatedPrev)
                } else {
                    p
                }
                else -> p
            }
            val updatedCurrent = when (val c = latest.currentSlot) {
                is ChapterWindowSlot.Loaded -> if (repaginatedCurrent != null && repaginatedCurrent.ast.cid == c.chapter.cid) {
                    ChapterWindowSlot.Loaded(c.chapter, repaginatedCurrent)
                } else {
                    c
                }
                else -> c
            }
            val updatedNext = when (val n = latest.nextSlot) {
                is ChapterWindowSlot.Loaded -> if (repaginatedNext != null && repaginatedNext.ast.cid == n.chapter.cid) {
                    ChapterWindowSlot.Loaded(n.chapter, repaginatedNext)
                } else {
                    n
                }
                else -> n
            }

            val newPrevCount = updatedPrev?.virtualPageCount ?: 0
            val resolvedPageInCurrent = (updatedCurrent as? ChapterWindowSlot.Loaded)
                ?.layout
                ?.resolvePageIndexForAnchor(anchorToPreserve)
                ?: 0

            val updatedSnapshot = latest.copy(
                prevSlot = updatedPrev,
                currentSlot = updatedCurrent,
                nextSlot = updatedNext,
                layoutConfig = newConfig,
                activeVirtualPageIndex = newPrevCount + resolvedPageInCurrent,
                coordinateRevision = latest.coordinateRevision + 1L,
            )
            _snapshotFlow.value = updatedSnapshot
            updatedSnapshot
        }
    }

    /**
     * Resolves illustration image URLs for [illustrationChapters] (offline SQLite cache first,
     * falling back to remote fetch) **without** modifying `_snapshotFlow`, active chapter,
     * coordinate revision, or reading progress!
     */
    suspend fun loadVolumeIllustrationUrlsForPeek(
        illustrationChapters: List<OrderedChapterWithVolume>,
    ): Result<List<String>> {
        if (illustrationChapters.isEmpty()) {
            return Result.success(emptyList())
        }
        val collectedUrls = ArrayList<String>()
        var lastFailure: Throwable? = null
        for (chapterMeta in illustrationChapters) {
            val inMemoryAst = stateMutex.withLock { astCache[chapterMeta.cid] }
            if (inMemoryAst != null) {
                inMemoryAst.illustrationBlocks.forEach { block ->
                    if (block.imageUrl.isNotBlank()) {
                        collectedUrls.add(block.imageUrl)
                    }
                }
                continue
            }
            val rawTextResult = fetchChapterTextOfflineFirst(chapterMeta)
            rawTextResult.onSuccess { rawText ->
                val parsed = withContext(computeDispatcher) {
                    WenkuChapterParser.parse(
                        rawChapterText = rawText,
                        aid = aid,
                        vid = chapterMeta.vid,
                        cid = chapterMeta.cid,
                        volumeTitle = chapterMeta.volumeTitle,
                        chapterTitle = chapterMeta.chapterTitle,
                    )
                }
                parsed.illustrationBlocks.forEach { block ->
                    if (block.imageUrl.isNotBlank()) {
                        collectedUrls.add(block.imageUrl)
                    }
                }
            }.onFailure { err ->
                lastFailure = err
            }
        }
        val deduplicated = collectedUrls.distinct()
        return if (deduplicated.isNotEmpty() || lastFailure == null) {
            Result.success(deduplicated)
        } else {
            Result.failure(lastFailure)
        }
    }

    /**
     * Retries loading a failed slot (current or neighbor) after network recovery.
     */
    suspend fun retrySlot(cid: Int): ReaderWindowSnapshot {
        val (targetMeta, config, isCurrent) = stateMutex.withLock {
            val snap = _snapshotFlow.value
            val cfg = snap.layoutConfig ?: return snap
            when {
                snap.currentSlot?.chapter?.cid == cid -> {
                    val meta = snap.currentSlot.chapter
                    _snapshotFlow.value = snap.copy(currentSlot = ChapterWindowSlot.Loading(meta))
                    Triple(meta, cfg, true)
                }
                snap.prevSlot?.chapter?.cid == cid -> {
                    val meta = snap.prevSlot.chapter
                    _snapshotFlow.value = snap.copy(prevSlot = ChapterWindowSlot.Loading(meta))
                    Triple(meta, cfg, false)
                }
                snap.nextSlot?.chapter?.cid == cid -> {
                    val meta = snap.nextSlot.chapter
                    _snapshotFlow.value = snap.copy(nextSlot = ChapterWindowSlot.Loading(meta))
                    Triple(meta, cfg, false)
                }
                else -> return snap
            }
        }

        if (isCurrent) {
            val reloaded = loadAndPaginateSlot(targetMeta, config)
            stateMutex.withLock {
                val latest = _snapshotFlow.value
                if (latest.currentSlot?.chapter?.cid == cid) {
                    _snapshotFlow.value = latest.copy(
                        currentSlot = reloaded,
                        activeVirtualPageIndex = latest.prevPageCount,
                        coordinateRevision = latest.coordinateRevision + 1L,
                    )
                }
            }
        } else {
            scheduleNeighborSlotLoad(targetMeta, config)
        }
        return _snapshotFlow.value
    }

    fun close() {
        cancelAllBackgroundSlotJobs()
    }

    private fun scheduleNeighborSlotLoad(
        chapterMeta: OrderedChapterWithVolume,
        config: ReaderLayoutConfig,
    ) {
        val cid = chapterMeta.cid
        activeSlotJobs[cid]?.let { existing ->
            if (existing.isActive) return
        }

        val job = scope.launch {
            val loadedSlot = loadAndPaginateSlot(chapterMeta, config)
            stateMutex.withLock {
                val latest = _snapshotFlow.value
                val latestConfig = latest.layoutConfig ?: config
                val finalSlot = if (loadedSlot is ChapterWindowSlot.Loaded && latestConfig != config) {
                    val repaginated = withContext(computeDispatcher) {
                        paginator.paginate(loadedSlot.layout.ast, latestConfig)
                    }
                    ChapterWindowSlot.Loaded(chapterMeta, repaginated)
                } else {
                    loadedSlot
                }

                when {
                    latest.prevSlot?.chapter?.cid == cid -> {
                        val oldPrevPages = latest.prevPageCount
                        val newPrevPages = finalSlot.virtualPageCount
                        val deltaPrev = newPrevPages - oldPrevPages
                        _snapshotFlow.value = latest.copy(
                            prevSlot = finalSlot,
                            activeVirtualPageIndex = (latest.activeVirtualPageIndex + deltaPrev)
                                .coerceAtLeast(0),
                            coordinateRevision = if (deltaPrev != 0) {
                                latest.coordinateRevision + 1L
                            } else {
                                latest.coordinateRevision
                            },
                        )
                    }

                    latest.nextSlot?.chapter?.cid == cid -> {
                        _snapshotFlow.value = latest.copy(
                            nextSlot = finalSlot,
                        )
                    }
                }
            }
        }
        activeSlotJobs[cid] = job
    }

    private suspend fun loadAndPaginateSlot(
        chapterMeta: OrderedChapterWithVolume,
        config: ReaderLayoutConfig,
        preloadedLocalText: String? = null,
    ): ChapterWindowSlot {
        return try {
            val cachedAst = stateMutex.withLock { astCache[chapterMeta.cid] }
            val ast = if (cachedAst != null) {
                cachedAst
            } else {
                val astResult = resolveOrLoadChapterAst(
                    chapterMeta = chapterMeta,
                    knownImageAspectRatios = config.knownImageAspectRatios,
                    preloadedLocalText = preloadedLocalText,
                )
                astResult.getOrElse { error ->
                    return ChapterWindowSlot.Error(
                        chapter = chapterMeta,
                        message = error.message ?: "章节加载失败",
                        kind = (error as? ChapterLoadFailureException)?.kind
                            ?: ChapterLoadErrorKind.GENERIC,
                    )
                }
            }

            val paginatedLayout = withContext(computeDispatcher) {
                paginator.paginate(ast = ast, config = config)
            }
            ChapterWindowSlot.Loaded(
                chapter = chapterMeta,
                layout = paginatedLayout,
            )
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (unexpected: Throwable) {
            ChapterWindowSlot.Error(
                chapter = chapterMeta,
                message = unexpected.message ?: "章节排版失败",
            )
        }
    }

    private suspend fun resolveOrLoadChapterAst(
        chapterMeta: OrderedChapterWithVolume,
        knownImageAspectRatios: Map<String, Float>,
        preloadedLocalText: String?,
    ): Result<ChapterAst> {
        val cid = chapterMeta.cid
        var newlyCreated: Deferred<Result<ChapterAst>>? = null
        val deferred = synchronized(inFlightAstLoads) {
            val existing = inFlightAstLoads[cid]
            if (existing != null && !existing.isCompleted && !existing.isCancelled) {
                existing
            } else {
                val created = scope.async(start = CoroutineStart.LAZY) {
                    val rawText = if (!preloadedLocalText.isNullOrBlank()) {
                        preloadedLocalText
                    } else {
                        fetchChapterTextOfflineFirst(chapterMeta).getOrElse { err ->
                            return@async Result.failure(err)
                        }
                    }
                    val parsed = withContext(computeDispatcher) {
                        WenkuChapterParser.parse(
                            rawChapterText = rawText,
                            aid = aid,
                            vid = chapterMeta.vid,
                            cid = cid,
                            volumeTitle = chapterMeta.volumeTitle,
                            chapterTitle = chapterMeta.chapterTitle,
                            knownImageAspectRatios = knownImageAspectRatios,
                        )
                    }
                    stateMutex.withLock {
                        astCache[cid] = parsed
                    }
                    Result.success(parsed)
                }
                inFlightAstLoads[cid] = created
                newlyCreated = created
                created
            }
        }
        newlyCreated?.invokeOnCompletion {
            synchronized(inFlightAstLoads) {
                if (inFlightAstLoads[cid] === newlyCreated) {
                    inFlightAstLoads.remove(cid)
                }
            }
        }
        deferred.start()
        return deferred.await()
    }

    private suspend fun fetchChapterTextOfflineFirst(
        chapterMeta: OrderedChapterWithVolume,
    ): Result<String> = withContext(ioDispatcher) {
        val localText = chapterContentDao.loadChapterTextSafely(chapterMeta.cid)
        if (!localText.isEffectivelyBlankWenkuText()) {
            return@withContext Result.success(localText!!)
        }

        val activeLang = appLanguageProvider()
        when (
            val remote = catalogGateway.fetchChapterContent(
                aid = aid,
                cid = chapterMeta.cid,
                appLanguage = activeLang,
            )
        ) {
            is WenkuApiResult.Success -> {
                val rawText = remote.data
                if (rawText.isEffectivelyBlankWenkuText()) {
                    Result.failure(
                        ChapterLoadFailureException(
                            kind = ChapterLoadErrorKind.EMPTY_SERVER_CONTENT,
                            message = EMPTY_CHAPTER_CONTENT_MESSAGE,
                        ),
                    )
                } else {
                    // Cache into Room `chapter_contents` (with pre-indexed image URLs) so offline
                    // re-opens and backward swipes are instant.
                    runCatching {
                        val imageUrls = WenkuXmlParsers.extractChapterImageUrls(rawText)
                        chapterContentDao.upsertChapterContent(
                            ChapterContentEntity(
                                cid = chapterMeta.cid,
                                aid = aid,
                                contentLang = activeLang.name,
                                contentText = rawText,
                                byteLength = rawText.toByteArray(Charsets.UTF_8).size,
                                imageUrlsJson = Json.encodeToString(imageUrls),
                                imageCount = imageUrls.size,
                                sha256Checksum = DigestUtils.sha256Hex(rawText),
                                downloadedAtMs = wallClockMs(),
                            ),
                        )
                    }
                    Result.success(rawText)
                }
            }

            WenkuApiResult.EmptyResponse -> Result.failure(
                ChapterLoadFailureException(
                    kind = ChapterLoadErrorKind.EMPTY_SERVER_CONTENT,
                    message = EMPTY_CHAPTER_CONTENT_MESSAGE,
                ),
            )

            WenkuApiResult.ApiNotConfigured -> Result.failure(
                ChapterLoadFailureException(
                    kind = ChapterLoadErrorKind.API_NOT_CONFIGURED,
                    message = "当前为未配置 API 的离线构建，且本地尚未缓存该章节",
                ),
            )

            is WenkuApiResult.NetworkError -> Result.failure(
                ChapterLoadFailureException(
                    kind = ChapterLoadErrorKind.NETWORK_UNAVAILABLE,
                    message = "网络连接不可用，无法加载章节 #${chapterMeta.cid}",
                ),
            )

            is WenkuApiResult.ServerErrorCode -> Result.failure(
                ChapterLoadFailureException(
                    kind = ChapterLoadErrorKind.SERVER_ERROR,
                    message = "服务器错误码 (${remote.numericCode})",
                ),
            )

            is WenkuApiResult.ParseError -> Result.failure(
                ChapterLoadFailureException(
                    kind = ChapterLoadErrorKind.PARSE_ERROR,
                    message = "章节数据校验异常",
                ),
            )
        }
    }

    private suspend fun resolveOrderedToc(
        targetCid: Int,
        fallbackVid: Int,
        fallbackVolumeTitle: String,
        fallbackChapterTitle: String,
    ): List<OrderedChapterWithVolume> = withContext(ioDispatcher) {
        var toc = tocDao.getOrderedChaptersWithVolume(aid)
        if (toc.isEmpty() && novelTocSyncRepository != null) {
            val startedAtMs = wallClockMs()
            val remoteToc = catalogGateway.fetchNovelVolumeToc(aid, appLanguageProvider())
            if (remoteToc is WenkuApiResult.Success && remoteToc.data.isNotEmpty()) {
                novelTocSyncRepository.syncNovelIndexAtomically(
                    aid = aid,
                    language = appLanguageProvider(),
                    requestStartedAtMs = startedAtMs,
                    parsedVolumes = remoteToc.data,
                )
                toc = tocDao.getOrderedChaptersWithVolume(aid)
            }
        }

        if (toc.isNotEmpty()) {
            return@withContext toc
        }

        val cleanChapterTitle = fallbackChapterTitle.ifBlank { "章节 #$targetCid" }
        listOf(
            OrderedChapterWithVolume(
                aid = aid,
                vid = fallbackVid,
                volumeOrder = 0,
                volumeTitle = fallbackVolumeTitle,
                cid = targetCid,
                chapterOrder = 0,
                chapterTitle = cleanChapterTitle,
                isIllustrationChapter = ChapterEntity.isIllustrationChapterTitle(cleanChapterTitle),
            ),
        )
    }

    private fun pruneAstCacheLocked(
        centerTocIndex: Int,
        orderedToc: List<OrderedChapterWithVolume>,
    ) {
        if (astCache.size <= MAX_CACHED_AST_CHAPTERS) return
        val keepWindowCids = HashSet<Int>()
        val from = (centerTocIndex - 2).coerceAtLeast(0)
        val to = (centerTocIndex + 2).coerceAtMost(orderedToc.lastIndex)
        for (idx in from..to) {
            keepWindowCids.add(orderedToc[idx].cid)
        }
        val iterator = astCache.entries.iterator()
        while (iterator.hasNext() && astCache.size > MAX_CACHED_AST_CHAPTERS) {
            val entry = iterator.next()
            if (entry.key !in keepWindowCids) {
                iterator.remove()
            }
        }
    }

    private fun cancelAllBackgroundSlotJobs() {
        for (job in activeSlotJobs.values) {
            job.cancel()
        }
        activeSlotJobs.clear()
        synchronized(inFlightAstLoads) {
            for (deferred in inFlightAstLoads.values) {
                deferred.cancel()
            }
            inFlightAstLoads.clear()
        }
    }

    private data class ReflowInputs(
        val prevAst: ChapterAst?,
        val currentAst: ChapterAst?,
        val nextAst: ChapterAst?,
        val anchorToPreserve: ReadingAnchor,
        val snapshotBefore: ReaderWindowSnapshot,
    )

    companion object {
        const val PREFETCH_THRESHOLD_RATIO = 0.70f
        private const val MAX_CACHED_AST_CHAPTERS = 6
        const val EMPTY_CHAPTER_CONTENT_MESSAGE =
            "源站接口返回空白章节内容，该章节正文在轻小说文库 API 端暂不可用，可稍后重试或通过网页端阅读。"
    }
}
