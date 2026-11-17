package org.mewx.wenku8.v2.data.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import coil3.SingletonImageLoader
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.v2.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.v2.data.media.OfflineFirstWenkuImageFetcher
import org.mewx.wenku8.v2.data.media.WenkuImageRequests
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.remote.api.WenkuApiResult
import org.mewx.wenku8.v2.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.v2.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.v2.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.v2.util.DigestUtils
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * All 4 download modes from `v1.31` `NovelDownloader` (`08-api-grounded-ui-and-expressive-interactions.md` §1).
 */
enum class NovelDownloadMode {
    /** Refreshes metadata + atomic TOC + cover + downloads all new/uncached chapters & illustrations. */
    SMART_UPDATE,

    /** Keeps existing TOC when available and downloads only chapters missing from `chapter_contents`. */
    UNCACHED_ONLY,

    /** Downloads user-selected `selectedVolumeIds`, optionally overwriting already-cached chapters. */
    SELECT_VOLUMES,

    /** Refreshes metadata + atomic TOC + re-downloads every chapter & illustration in the novel. */
    OVERWRITE_ALL,
}

enum class NovelDownloadRunState {
    RUNNING,
    SUCCEEDED,
    PARTIAL_FAILURE,
    FAILED,
    CANCELLED,
}

data class NovelDownloadRequest(
    val aid: Int,
    val appLanguage: Wenku8API.AppLanguage,
    val mode: NovelDownloadMode,
    val selectedVolumeIds: Set<Int> = emptySet(),
    val overwriteSelectedVolumes: Boolean = false,
)

data class NovelDownloadProgress(
    val aid: Int,
    val novelTitle: String,
    val mode: NovelDownloadMode,
    val completedChapters: Int,
    val totalChapters: Int,
    val currentChapterTitle: String,
    val downloadedImages: Int,
    val failedChapters: Int = 0,
    val state: NovelDownloadRunState = NovelDownloadRunState.RUNNING,
)

data class NovelDownloadOutcome(
    val aid: Int,
    val state: NovelDownloadRunState,
    val downloadedChapters: Int,
    val totalTargetChapters: Int,
    val downloadedImages: Int,
    val failedChapters: Int,
)

/**
 * Atomic, race-free 4-Mode Novel Downloader (`NovelDownloadRepository`) for Wenku8 `:app-v2`.
 *
 * Key storage & concurrency invariants:
 * 1. **Per-Novel Serialization**: A per-`aid` [Mutex] guarantees `WorkManager` retries and manual UI
 *    triggers can never run duplicate concurrent downloads for the same novel.
 * 2. **Atomic TOC Before Chapters**: `SMART_UPDATE`, `OVERWRITE_ALL`, and empty-TOC `UNCACHED_ONLY` /
 *    `SELECT_VOLUMES` runs commit `do=list` strictly through [NovelTocSyncRepository] (`@Upsert` +
 *    `toc_version`) so `READING_PROGRESS` and `BOOKMARKS_NOTES` are never cascade-deleted.
 * 3. **Permanent Offline Cover & Illustrations**: Writes novel cover (`saves/imgs/$aid.jpg`) and
 *    inline `<!--image-->` illustrations (`saves/imgs/${generateLegacyImageFileName(url)}`) via
 *    temp-file + rename so [org.mewx.wenku8.v2.data.media.OfflineFirstWenkuImageFetcher] serves them
 *    forever offline without Coil LRU eviction or corrupt partial JPEGs.
 */
class NovelDownloadRepository(
    private val database: Wenku8Database,
    private val catalogGateway: WenkuCatalogGateway,
    private val novelTocSyncRepository: NovelTocSyncRepository,
    private val assetRootsProvider: () -> List<File>,
    private val applicationScope: CoroutineScope,
    private val appContext: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val wallClockMs: () -> Long = { System.currentTimeMillis() },
) {
    private val novelDao = database.novelDao()
    private val tocDao = database.tocDao()
    private val chapterContentDao = database.chapterContentDao()

    private val perNovelMutexes = ConcurrentHashMap<Int, Mutex>()
    private val activeJobs = ConcurrentHashMap<Int, Job>()

    private val _activeDownloadsFlow = MutableStateFlow<Map<Int, NovelDownloadProgress>>(emptyMap())
    val activeDownloadsFlow: StateFlow<Map<Int, NovelDownloadProgress>> = _activeDownloadsFlow.asStateFlow()

    /**
     * Enqueues a background download via AndroidX [WorkManager] when initialized in a live Android
     * process, while also tracking cancellation cleanly and falling back to [applicationScope] in
     * lightweight test environments where `WorkManager` is not initialized.
     */
    fun startDownload(request: NovelDownloadRequest) {
        if (request.aid <= 0) return
        activeJobs.remove(request.aid)?.cancel()

        val scheduledInWorkManager = enqueueWorkManagerTaskIfAvailable(request)
        if (!scheduledInWorkManager) {
            val job = applicationScope.launch(ioDispatcher) {
                executeDownload(request)
            }
            activeJobs[request.aid] = job
        }
    }

    fun cancelDownload(aid: Int) {
        activeJobs.remove(aid)?.cancel()
        val ctx = appContext
        if (ctx != null) {
            runCatching {
                WorkManager.getInstance(ctx).cancelUniqueWork(uniqueWorkName(aid))
            }
        }
        _activeDownloadsFlow.update { current ->
            val existing = current[aid] ?: return@update current
            current + (aid to existing.copy(state = NovelDownloadRunState.CANCELLED))
        }
    }

    fun clearFinishedStatus(aid: Int) {
        _activeDownloadsFlow.update { current ->
            val entry = current[aid]
            if (entry != null && entry.state != NovelDownloadRunState.RUNNING) {
                current - aid
            } else {
                current
            }
        }
    }

    /**
     * Executes a 4-mode download synchronously inside the caller's coroutine (invoked both by
     * [NovelDownloadWorker.doWork] and directly by deterministic unit tests).
     */
    suspend fun executeDownload(request: NovelDownloadRequest): NovelDownloadOutcome = withContext(ioDispatcher) {
        val aid = request.aid
        if (aid <= 0 || !catalogGateway.isApiConfigured) {
            return@withContext NovelDownloadOutcome(
                aid = aid,
                state = NovelDownloadRunState.FAILED,
                downloadedChapters = 0,
                totalTargetChapters = 0,
                downloadedImages = 0,
                failedChapters = 0,
            )
        }

        val mutex = perNovelMutexes.computeIfAbsent(aid) { Mutex() }
        mutex.withLock {
            val startedAtMs = wallClockMs()
            val initialTitle = novelDao.getNovelByAid(aid)?.title ?: "AID #$aid"
            publishProgress(
                NovelDownloadProgress(
                    aid = aid,
                    novelTitle = initialTitle,
                    mode = request.mode,
                    completedChapters = 0,
                    totalChapters = 0,
                    currentChapterTitle = "",
                    downloadedImages = 0,
                    state = NovelDownloadRunState.RUNNING,
                ),
            )

            try {
                val mustRefreshTocFromServer = request.mode == NovelDownloadMode.SMART_UPDATE ||
                    request.mode == NovelDownloadMode.OVERWRITE_ALL ||
                    tocDao.getVolumesForNovel(aid).isEmpty()

                if (mustRefreshTocFromServer) {
                    val refreshedOk = refreshMetadataAndTocForDownload(
                        aid = aid,
                        appLanguage = request.appLanguage,
                        startedAtMs = startedAtMs,
                    )
                    if (!refreshedOk && tocDao.getVolumesForNovel(aid).isEmpty()) {
                        val failedOutcome = NovelDownloadOutcome(
                            aid = aid,
                            state = NovelDownloadRunState.FAILED,
                            downloadedChapters = 0,
                            totalTargetChapters = 0,
                            downloadedImages = 0,
                            failedChapters = 0,
                        )
                        updateTerminalState(aid, NovelDownloadRunState.FAILED, 0, 0, 0, 0)
                        return@withLock failedOutcome
                    }
                }

                currentCoroutineContext().ensureActive()

                // Persist novel cover JPEG offline (`saves/imgs/{aid}.jpg`) so Bookshelf & Detail
                // always display artwork offline.
                downloadCoverImageOfflineBestEffort(aid)

                val resolvedNovelTitle = novelDao.getNovelByAid(aid)?.title ?: initialTitle
                val orderedChapters = loadOrderedChaptersForNovel(aid)
                val cachedCids = chapterContentDao.getCachedChapterIdsForNovel(aid).toHashSet()

                val targetChapters: List<ChapterEntity> = when (request.mode) {
                    NovelDownloadMode.SMART_UPDATE,
                    NovelDownloadMode.UNCACHED_ONLY,
                    -> orderedChapters.filter { it.cid !in cachedCids }

                    NovelDownloadMode.SELECT_VOLUMES -> {
                        val inSelectedVolumes = orderedChapters.filter { it.vid in request.selectedVolumeIds }
                        if (request.overwriteSelectedVolumes) {
                            inSelectedVolumes
                        } else {
                            inSelectedVolumes.filter { it.cid !in cachedCids }
                        }
                    }

                    NovelDownloadMode.OVERWRITE_ALL -> orderedChapters
                }

                if (targetChapters.isEmpty()) {
                    updateTerminalState(
                        aid = aid,
                        state = NovelDownloadRunState.SUCCEEDED,
                        completed = 0,
                        total = 0,
                        images = 0,
                        failed = 0,
                    )
                    return@withLock NovelDownloadOutcome(
                        aid = aid,
                        state = NovelDownloadRunState.SUCCEEDED,
                        downloadedChapters = 0,
                        totalTargetChapters = 0,
                        downloadedImages = 0,
                        failedChapters = 0,
                    )
                }

                var completedCount = 0
                var failedCount = 0
                var savedImageCount = 0

                for (chapter in targetChapters) {
                    currentCoroutineContext().ensureActive()
                    publishProgress(
                        NovelDownloadProgress(
                            aid = aid,
                            novelTitle = resolvedNovelTitle,
                            mode = request.mode,
                            completedChapters = completedCount,
                            totalChapters = targetChapters.size,
                            currentChapterTitle = chapter.chapterTitle,
                            downloadedImages = savedImageCount,
                            failedChapters = failedCount,
                            state = NovelDownloadRunState.RUNNING,
                        ),
                    )

                    val contentResult = catalogGateway.fetchChapterContent(
                        aid = aid,
                        cid = chapter.cid,
                        appLanguage = request.appLanguage,
                    )
                    if (contentResult is WenkuApiResult.Success && contentResult.data.isNotBlank()) {
                        val rawChapterText = contentResult.data
                        val extractedImageUrls = WenkuXmlParsers.extractChapterImageUrls(rawChapterText)
                        val encodedUrlsJson = Json.encodeToString(extractedImageUrls)

                        chapterContentDao.upsertChapterContent(
                            ChapterContentEntity(
                                cid = chapter.cid,
                                aid = aid,
                                contentLang = request.appLanguage.name,
                                contentText = rawChapterText,
                                byteLength = rawChapterText.toByteArray(Charsets.UTF_8).size,
                                imageUrlsJson = encodedUrlsJson,
                                imageCount = extractedImageUrls.size,
                                sha256Checksum = DigestUtils.sha256Hex(rawChapterText),
                                downloadedAtMs = wallClockMs(),
                            ),
                        )

                        for (imageUrl in extractedImageUrls) {
                            currentCoroutineContext().ensureActive()
                            val savedNewOrExisting = downloadChapterIllustrationOffline(
                                imageUrl = imageUrl,
                                overwriteExisting = request.mode == NovelDownloadMode.OVERWRITE_ALL ||
                                    (request.mode == NovelDownloadMode.SELECT_VOLUMES && request.overwriteSelectedVolumes),
                            )
                            if (savedNewOrExisting) {
                                savedImageCount++
                            }
                        }

                        completedCount++
                    } else {
                        failedCount++
                    }
                }

                val finalState = when {
                    completedCount == 0 && failedCount > 0 -> NovelDownloadRunState.FAILED
                    failedCount > 0 -> NovelDownloadRunState.PARTIAL_FAILURE
                    else -> NovelDownloadRunState.SUCCEEDED
                }

                updateTerminalState(
                    aid = aid,
                    state = finalState,
                    completed = completedCount,
                    total = targetChapters.size,
                    images = savedImageCount,
                    failed = failedCount,
                )

                NovelDownloadOutcome(
                    aid = aid,
                    state = finalState,
                    downloadedChapters = completedCount,
                    totalTargetChapters = targetChapters.size,
                    downloadedImages = savedImageCount,
                    failedChapters = failedCount,
                )
            } catch (cancelled: CancellationException) {
                updateTerminalState(aid, NovelDownloadRunState.CANCELLED, 0, 0, 0, 0)
                throw cancelled
            }
        }
    }

    private suspend fun refreshMetadataAndTocForDownload(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
        startedAtMs: Long,
    ): Boolean {
        val existing = novelDao.getNovelByAid(aid)
        val metaRes = catalogGateway.fetchNovelFullMeta(aid, appLanguage)
        val introRes = catalogGateway.fetchNovelFullIntro(aid, appLanguage)
        val tocRes = catalogGateway.fetchNovelVolumeToc(aid, appLanguage)

        if (metaRes !is WenkuApiResult.Success && tocRes !is WenkuApiResult.Success) {
            return false
        }

        val fullMeta = (metaRes as? WenkuApiResult.Success)?.data
        val fullIntro = (introRes as? WenkuApiResult.Success)?.data?.trim().orEmpty()
        val resolvedTitle = fullMeta?.title?.takeIf { it.isNotBlank() }
            ?: existing?.title
            ?: "AID #$aid"

        val incoming = NovelEntity(
            aid = aid,
            contentLang = appLanguage.name,
            metaSyncedAtMs = startedAtMs,
            title = resolvedTitle,
            author = fullMeta?.author.orEmpty().ifBlank { existing?.author.orEmpty() },
            bookStatus = fullMeta?.bookStatus.orEmpty().ifBlank { existing?.bookStatus.orEmpty() },
            pressId = fullMeta?.pressId.orEmpty().ifBlank { existing?.pressId.orEmpty() },
            bookLength = fullMeta?.bookLength?.takeIf { it > 0 } ?: (existing?.bookLength ?: 0),
            lastUpdate = fullMeta?.lastUpdate.orEmpty().ifBlank { existing?.lastUpdate.orEmpty() },
            latestSectionCid = fullMeta?.latestSectionCid?.takeIf { it > 0 } ?: (existing?.latestSectionCid ?: 0),
            latestSectionName = fullMeta?.latestSectionName.orEmpty().ifBlank { existing?.latestSectionName.orEmpty() },
            dayHits = fullMeta?.dayHitsCount?.takeIf { it > 0 } ?: (existing?.dayHits ?: 0),
            totalHits = fullMeta?.totalHitsCount?.takeIf { it > 0 } ?: (existing?.totalHits ?: 0),
            pushCount = fullMeta?.pushCount?.takeIf { it > 0 } ?: (existing?.pushCount ?: 0),
            favCount = fullMeta?.favCount?.takeIf { it > 0 } ?: (existing?.favCount ?: 0),
            tagsCsv = fullMeta?.tagsCsv.orEmpty().ifBlank { existing?.tagsCsv.orEmpty() },
            introShort = existing?.introShort.orEmpty(),
            fullIntro = fullIntro.ifBlank { existing?.fullIntro.orEmpty() },
            lastAccessedMs = startedAtMs,
        )
        novelDao.mergeServerMetadataPreservingShelfState(incoming)

        if (tocRes is WenkuApiResult.Success && tocRes.data.isNotEmpty()) {
            novelTocSyncRepository.syncNovelIndexAtomically(
                aid = aid,
                language = appLanguage,
                requestStartedAtMs = startedAtMs,
                parsedVolumes = tocRes.data,
                fallbackNovelTitle = resolvedTitle,
            )
            return true
        }
        return false
    }

    private suspend fun loadOrderedChaptersForNovel(aid: Int): List<ChapterEntity> {
        val volumes = tocDao.getVolumesForNovel(aid).sortedBy { it.volumeOrder }
        val chaptersByVid = tocDao.getChaptersForNovel(aid).groupBy { it.vid }
        return volumes.flatMap { volume ->
            chaptersByVid[volume.vid].orEmpty().sortedBy { it.chapterOrder }
        }
    }

    private suspend fun downloadCoverImageOfflineBestEffort(aid: Int) {
        val primaryRoot = assetRootsProvider().firstOrNull() ?: return
        val targetFile = File(primaryRoot, "saves/imgs/$aid.jpg")
        if (targetFile.isFile && targetFile.length() > 0L) return

        val coverUrl = catalogGateway.resolveCoverUrl(aid)
        if (coverUrl.isBlank()) return

        if (tryCopyFromExistingLocalCaches(
                imageUrl = coverUrl,
                canonicalDiskKey = WenkuImageRequests.canonicalCoverCacheKey(aid),
                targetFile = targetFile,
            )
        ) {
            return
        }

        val bytes = catalogGateway.downloadBinaryBytes(coverUrl) ?: return
        if (bytes.isEmpty()) return
        writeBytesAtomically(targetFile, bytes)
    }

    private suspend fun downloadChapterIllustrationOffline(
        imageUrl: String,
        overwriteExisting: Boolean,
    ): Boolean {
        val primaryRoot = assetRootsProvider().firstOrNull() ?: return false
        val fileName = WenkuXmlParsers.generateLegacyImageFileName(imageUrl)
        if (fileName.isBlank()) return false

        val targetFile = File(primaryRoot, "saves/imgs/$fileName")
        if (!overwriteExisting && targetFile.isFile && targetFile.length() > 0L) {
            return true
        }

        if (!overwriteExisting &&
            tryCopyFromExistingLocalCaches(
                imageUrl = imageUrl,
                canonicalDiskKey = WenkuImageRequests.canonicalIllustrationCacheKey(imageUrl),
                targetFile = targetFile,
            )
        ) {
            return true
        }

        val bytes = catalogGateway.downloadBinaryBytes(imageUrl) ?: return false
        if (bytes.isEmpty()) return false
        return writeBytesAtomically(targetFile, bytes)
    }

    /**
     * Checks local `imgs/` / `artbook_images/` and Coil 3's persistent `DiskCache` so offline
     * downloads promote already-viewed covers & illustrations without re-requesting Wenku8 servers.
     */
    private fun tryCopyFromExistingLocalCaches(
        imageUrl: String,
        canonicalDiskKey: String?,
        targetFile: File,
    ): Boolean {
        val ctx = appContext
        val existingFile = OfflineFirstWenkuImageFetcher.findExistingOfflineImageFile(
            imageUrl = imageUrl,
            storageRoots = assetRootsProvider(),
            cacheRoot = ctx?.cacheDir,
        )
        if (existingFile != null &&
            existingFile.absolutePath != targetFile.absolutePath &&
            existingFile.isFile &&
            existingFile.length() > 0L
        ) {
            val copied = runCatching {
                writeBytesAtomically(targetFile, existingFile.readBytes())
            }.getOrDefault(false)
            if (copied) return true
        }

        if (ctx != null) {
            val diskCache = runCatching { SingletonImageLoader.get(ctx).diskCache }.getOrNull()
            val candidateKeys = listOfNotNull(canonicalDiskKey, imageUrl.trim()).distinct()
            for (key in candidateKeys) {
                val snapshot = runCatching { diskCache?.openSnapshot(key) }.getOrNull() ?: continue
                val copied = snapshot.use { snap ->
                    val cachedDataFile = snap.data.toFile()
                    if (cachedDataFile.isFile && cachedDataFile.length() > 0L) {
                        runCatching {
                            writeBytesAtomically(targetFile, cachedDataFile.readBytes())
                        }.getOrDefault(false)
                    } else {
                        false
                    }
                }
                if (copied) return true
            }
        }
        return false
    }

    private fun writeBytesAtomically(targetFile: File, bytes: ByteArray): Boolean {
        return try {
            val parent = targetFile.parentFile ?: return false
            if (!parent.exists() && !parent.mkdirs()) return false
            val tempFile = File(parent, "${targetFile.name}.tmp")
            tempFile.writeBytes(bytes)
            if (targetFile.exists() && !targetFile.delete()) {
                tempFile.delete()
                return false
            }
            if (!tempFile.renameTo(targetFile)) {
                targetFile.writeBytes(bytes)
                tempFile.delete()
            }
            targetFile.isFile && targetFile.length() > 0L
        } catch (_: Exception) {
            false
        }
    }

    private fun publishProgress(progress: NovelDownloadProgress) {
        _activeDownloadsFlow.update { current -> current + (progress.aid to progress) }
    }

    private fun updateTerminalState(
        aid: Int,
        state: NovelDownloadRunState,
        completed: Int,
        total: Int,
        images: Int,
        failed: Int,
    ) {
        _activeDownloadsFlow.update { current ->
            val previous = current[aid]
            val updated = NovelDownloadProgress(
                aid = aid,
                novelTitle = previous?.novelTitle ?: "AID #$aid",
                mode = previous?.mode ?: NovelDownloadMode.SMART_UPDATE,
                completedChapters = completed,
                totalChapters = total,
                currentChapterTitle = "",
                downloadedImages = images,
                failedChapters = failed,
                state = state,
            )
            current + (aid to updated)
        }
    }

    private fun enqueueWorkManagerTaskIfAvailable(request: NovelDownloadRequest): Boolean {
        val ctx = appContext ?: return false
        return try {
            val workManager = WorkManager.getInstance(ctx)
            val inputData = Data.Builder()
                .putInt(NovelDownloadWorker.KEY_AID, request.aid)
                .putString(NovelDownloadWorker.KEY_APP_LANGUAGE, request.appLanguage.name)
                .putString(NovelDownloadWorker.KEY_DOWNLOAD_MODE, request.mode.name)
                .putIntArray(NovelDownloadWorker.KEY_SELECTED_VIDS, request.selectedVolumeIds.toIntArray())
                .putBoolean(NovelDownloadWorker.KEY_OVERWRITE_SELECTED, request.overwriteSelectedVolumes)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<NovelDownloadWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .addTag(WORK_TAG_ALL_DOWNLOADS)
                .build()

            workManager.enqueueUniqueWork(
                uniqueWorkName(request.aid),
                ExistingWorkPolicy.REPLACE,
                workRequest,
            )
            true
        } catch (_: IllegalStateException) {
            // WorkManager is not initialized in plain JVM / Robolectric unit tests unless explicitly set up.
            false
        }
    }

    companion object {
        const val WORK_TAG_ALL_DOWNLOADS = "wenku8_v2_novel_download"

        fun uniqueWorkName(aid: Int): String = "wenku8_v2_novel_download_$aid"
    }
}
