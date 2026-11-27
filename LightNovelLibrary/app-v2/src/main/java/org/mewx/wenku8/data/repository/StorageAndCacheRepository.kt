package org.mewx.wenku8.data.repository

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import coil3.SingletonImageLoader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.data.media.WenkuImageValidator
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import java.io.File
import java.io.IOException

enum class CustomAssetSlot(val fileName: String) {
    READER_FONT("reader_font"),
    READER_BACKGROUND("reader_background"),
}

data class CacheCleanSummary(
    val deletedFileCount: Int,
    val reclaimedBytes: Long,
    val prunedTransientNovelRows: Int,
)

data class DeepCleanProgress(
    val scannedFiles: Int,
    val totalFiles: Int,
    val deletedOrphanFiles: Int,
    val reclaimedBytes: Long,
)

data class CustomCacheChapterItem(
    val cid: Int,
    val aid: Int,
    val vid: Int,
    val chapterOrder: Int,
    val chapterTitle: String,
    val textBytes: Long,
    val imageBytes: Long,
    val imageCount: Int,
) {
    val totalBytes: Long
        get() = textBytes + imageBytes
}

data class CustomCacheVolumeItem(
    val vid: Int,
    val aid: Int,
    val volumeOrder: Int,
    val volumeTitle: String,
    val chapters: List<CustomCacheChapterItem>,
) {
    val cachedChapterCount: Int
        get() = chapters.size

    val totalBytes: Long
        get() = chapters.sumOf { it.totalBytes }

    val allCids: Set<Int>
        get() = chapters.mapTo(LinkedHashSet()) { it.cid }
}

data class CustomCacheBookItem(
    val aid: Int,
    val title: String,
    val coverUrl: String,
    val inBookshelf: Boolean,
    val volumes: List<CustomCacheVolumeItem>,
) {
    val cachedChapterCount: Int
        get() = volumes.sumOf { it.cachedChapterCount }

    val totalBytes: Long
        get() = volumes.sumOf { it.totalBytes }

    val allCids: Set<Int>
        get() = volumes.flatMapTo(LinkedHashSet()) { it.allCids }
}

data class CustomCacheInventory(
    val tempCacheBytes: Long,
    val tempCacheFileCount: Int,
    val nonBookshelfBrowseBytes: Long,
    val nonBookshelfCoverCount: Int,
    val nonBookshelfNovelCount: Int,
    val orphanImageBytes: Long,
    val orphanImageFileCount: Int,
    val books: List<CustomCacheBookItem>,
) {
    val booksTotalBytes: Long
        get() = books.sumOf { it.totalBytes }

    val totalBytes: Long
        get() = tempCacheBytes + nonBookshelfBrowseBytes + orphanImageBytes + booksTotalBytes

    val totalCachedChapters: Int
        get() = books.sumOf { it.cachedChapterCount }
}

data class CustomCacheCleanRequest(
    val cleanTempCache: Boolean = false,
    val cleanNonBookshelfBrowse: Boolean = false,
    val cleanOrphanImages: Boolean = false,
    val selectedChapterCids: Set<Int> = emptySet(),
) {
    val hasAnySelection: Boolean
        get() = cleanTempCache || cleanNonBookshelfBrowse || cleanOrphanImages || selectedChapterCids.isNotEmpty()
}

data class CustomCacheCleanResult(
    val deletedChaptersCount: Int = 0,
    val deletedFileCount: Int = 0,
    val reclaimedBytes: Long = 0L,
    val prunedTransientNovelRows: Int = 0,
    val purgedCoverCount: Int = 0,
)

/**
 * Implements `v1.31`-parity Two-Mode Cache Cleanup (`快速清理` & cancellable `深度清理`),
 * fine-grained chapter/volume/custom cache inspection & cleanup (`#223`), plus safe SAF custom
 * asset import (`filesDir/custom/{reader_font, reader_background}`).
 */
class StorageAndCacheRepository(
    private val database: Wenku8Database,
    private val cacheRootProvider: () -> File,
    private val assetRootsProvider: () -> List<File>,
    private val appContext: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val coverUrlProvider: (Int) -> String = { aid ->
        runCatching { Wenku8API.getCoverURL(aid).orEmpty() }.getOrDefault("")
    },
) {
    companion object {
        private const val DIAGNOSTICS_RETENTION_MS: Long = 24L * 60L * 60L * 1000L
        const val CUSTOM_DIR_NAME: String = "custom"
        const val IMGS_DIR_NAME: String = "imgs"
        const val SAVES_DIR_NAME: String = "saves"

        /**
         * Validates TrueType / OpenType / TrueType Collection magic headers:
         * - `0x00 0x01 0x00 0x00` (TrueType)
         * - `OTTO` (OpenType CFF)
         * - `true` (Apple TrueType)
         * - `ttcf` (TrueType Collection)
         */
        internal fun hasValidFontSignature(headerBytes: ByteArray, length: Int): Boolean {
            if (length < 4) return false
            val b0 = headerBytes[0].toInt() and 0xFF
            val b1 = headerBytes[1].toInt() and 0xFF
            val b2 = headerBytes[2].toInt() and 0xFF
            val b3 = headerBytes[3].toInt() and 0xFF
            if (b0 == 0x00 && b1 == 0x01 && b2 == 0x00 && b3 == 0x00) return true
            val ascii = String(headerBytes, 0, 4, Charsets.US_ASCII)
            return ascii == "OTTO" || ascii == "true" || ascii == "ttcf"
        }

        internal fun extractImageUrlsFromJson(rawJson: String): List<String> {
            if (rawJson.isBlank() || rawJson == "[]") return emptyList()
            val urls = ArrayList<String>()
            runCatching {
                val array = JSONArray(rawJson)
                for (index in 0 until array.length()) {
                    val url = array.optString(index).trim()
                    if (url.isNotEmpty()) {
                        urls.add(url)
                    }
                }
            }
            return urls
        }

        internal fun imageFileNamesForUrl(url: String): Set<String> {
            val trimmed = url.trim()
            if (trimmed.isEmpty()) return emptySet()
            val names = LinkedHashSet<String>(2)
            val legacyName = WenkuXmlParsers.generateLegacyImageFileName(trimmed)
            if (legacyName.isNotEmpty()) {
                names.add(legacyName)
            }
            val tailName = trimmed.substringBefore('?').substringAfterLast('/').trim()
            if (tailName.isNotEmpty()) {
                names.add(tailName)
            }
            return names
        }

        internal suspend fun buildLiveImageFileNameSet(database: Wenku8Database): Set<String> {
            val liveFileNames = HashSet<String>()
            val allJsonLists = database.chapterContentDao().getAllNonEmptyImageUrlsJson()
            for (rawJson in allJsonLists) {
                currentCoroutineContext().ensureActive()
                for (url in extractImageUrlsFromJson(rawJson)) {
                    liveFileNames.addAll(imageFileNamesForUrl(url))
                }
            }
            return liveFileNames
        }

        /**
         * Shared reference-safe chapter cache cleaner used by [StorageAndCacheRepository],
         * [BookshelfRepository], and `NovelDetailScreen`.
         *
         * Deletes `chapter_contents` rows for [cids] in SQLite-safe chunks, purges offline/cached
         * illustrations that are no longer referenced by any remaining cached chapter, and
         * optionally removes stray non-bookshelf cover files once a non-bookshelf novel has zero
         * remaining cached chapters. Never modifies reading progress, bookmarks, or shelf entries.
         */
        internal suspend fun clearCachedChaptersInternal(
            database: Wenku8Database,
            cids: Collection<Int>,
            assetRoots: List<File>,
            cacheRoot: File?,
            appContext: Context?,
            purgeNonBookshelfCoverIfEmptied: Boolean = true,
            coverUrlProvider: (Int) -> String = { aid ->
                runCatching { Wenku8API.getCoverURL(aid).orEmpty() }.getOrDefault("")
            },
        ): CustomCacheCleanResult {
            val targetCids = cids.distinct().filter { it > 0 }
            if (targetCids.isEmpty()) return CustomCacheCleanResult()

            val metaRows = database.chapterContentDao().getCachedChapterCacheMetaByCids(targetCids)
            if (metaRows.isEmpty()) return CustomCacheCleanResult()

            val affectedAids = metaRows.mapTo(LinkedHashSet()) { it.aid }
            val reclaimedTextBytes = metaRows.sumOf { it.byteLength.coerceAtLeast(0).toLong() }
            val candidateImageUrls = LinkedHashSet<String>()
            for (row in metaRows) {
                if (row.imageCount > 0 || row.imageUrlsJson.isNotBlank()) {
                    candidateImageUrls.addAll(extractImageUrlsFromJson(row.imageUrlsJson))
                }
            }

            val deletedChapters = database.chapterContentDao()
                .deleteChapterContentsByCids(metaRows.map { it.cid })

            var deletedFileCount = 0
            var reclaimedFileBytes = 0L

            if (candidateImageUrls.isNotEmpty() && (assetRoots.isNotEmpty() || cacheRoot != null || appContext != null)) {
                val stillLiveFileNames = buildLiveImageFileNameSet(database)
                for (url in candidateImageUrls) {
                    currentCoroutineContext().ensureActive()
                    val names = imageFileNamesForUrl(url)
                    if (names.any { it in stillLiveFileNames }) continue

                    val (measuredCount, measuredBytes) = measureImageFilesOnDisk(
                        imageUrl = url,
                        storageRoots = assetRoots,
                        cacheRoot = cacheRoot,
                    )
                    WenkuImageValidator.purgeCachedImageForUrl(
                        imageUrl = url,
                        storageRoots = assetRoots,
                        cacheRoot = cacheRoot,
                        appContext = appContext,
                        includePermanentOfflineFiles = true,
                    )
                    deletedFileCount += measuredCount
                    reclaimedFileBytes += measuredBytes
                }
            }

            var purgedCovers = 0
            if (purgeNonBookshelfCoverIfEmptied && affectedAids.isNotEmpty()) {
                val shelfAids = database.novelDao().getBookshelfNovelIds().toHashSet()
                for (aid in affectedAids) {
                    currentCoroutineContext().ensureActive()
                    if (aid in shelfAids) continue
                    val remaining = database.chapterContentDao().getCachedChapterCountForNovel(aid)
                    if (remaining == 0) {
                        val (coverDeletedCount, coverBytes) = purgeCoverFilesForNovel(
                            aid = aid,
                            storageRoots = assetRoots,
                            appContext = appContext,
                            coverUrl = coverUrlProvider(aid),
                        )
                        if (coverDeletedCount > 0) {
                            purgedCovers += coverDeletedCount
                            deletedFileCount += coverDeletedCount
                            reclaimedFileBytes += coverBytes
                        }
                    }
                }
            }

            return CustomCacheCleanResult(
                deletedChaptersCount = deletedChapters,
                deletedFileCount = deletedFileCount,
                reclaimedBytes = reclaimedTextBytes + reclaimedFileBytes,
                purgedCoverCount = purgedCovers,
            )
        }

        private fun measureImageFilesOnDisk(
            imageUrl: String,
            storageRoots: List<File>,
            cacheRoot: File?,
        ): Pair<Int, Long> {
            val legacyName = WenkuXmlParsers.generateLegacyImageFileName(imageUrl.trim())
            if (legacyName.isEmpty()) return 0 to 0L
            var count = 0
            var bytes = 0L
            for (root in storageRoots) {
                val savesImg = File(root, "$SAVES_DIR_NAME/$IMGS_DIR_NAME/$legacyName")
                if (savesImg.isFile) {
                    count++
                    bytes += savesImg.length().coerceAtLeast(0L)
                }
                val rootImg = File(root, "$IMGS_DIR_NAME/$legacyName")
                if (rootImg.isFile) {
                    count++
                    bytes += rootImg.length().coerceAtLeast(0L)
                }
            }
            if (cacheRoot != null) {
                val artbookCached = File(File(cacheRoot, WenkuImageRequests.ARTBOOK_CACHE_DIR_NAME), legacyName)
                if (artbookCached.isFile) {
                    count++
                    bytes += artbookCached.length().coerceAtLeast(0L)
                }
            }
            return count to bytes
        }

        private fun purgeCoverFilesForNovel(
            aid: Int,
            storageRoots: List<File>,
            appContext: Context?,
            coverUrl: String,
        ): Pair<Int, Long> {
            var count = 0
            var bytes = 0L
            for (root in storageRoots) {
                for (candidate in listOf(
                    File(root, "$IMGS_DIR_NAME/$aid.jpg"),
                    File(root, "$SAVES_DIR_NAME/$IMGS_DIR_NAME/$aid.jpg"),
                )) {
                    if (candidate.isFile) {
                        val len = candidate.length().coerceAtLeast(0L)
                        if (candidate.delete()) {
                            count++
                            bytes += len
                        }
                    }
                }
            }
            if (appContext != null && coverUrl.isNotBlank()) {
                WenkuImageValidator.evictCoilCachesForUrl(appContext, coverUrl)
            }
            return count to bytes
        }
    }

    /**
     * **Fast Clean (`快速清理`)**:
     * 1. Deletes temporary HTTP/image cache files under `cacheDir` (preserving fresh `< 24h`
     *    `cache/diagnostics/` exports).
     * 2. Deletes cached cover files (`imgs/{aid}.jpg`) for novels NOT in the user's bookshelf.
     * 3. Prunes transient non-bookshelf `novels` rows from Room.
     */
    suspend fun runFastCacheClean(): CacheCleanSummary = withContext(ioDispatcher) {
        var deletedCount = 0
        var reclaimedBytes = 0L
        val nowMs = System.currentTimeMillis()

        // 0. Safely clear Coil's live MemoryCache & DiskCache before sweeping cacheDir files so
        //    DiskLruCache's open journal never drifts out of sync with deleted files on disk.
        appContext?.let { ctx ->
            runCatching {
                val loader = SingletonImageLoader.get(ctx)
                loader.memoryCache?.clear()
                loader.diskCache?.clear()
            }
        }

        // 1. Clean temporary cache directory
        val cacheRoot = cacheRootProvider()
        val diagnosticsDir = File(cacheRoot, "diagnostics")
        if (cacheRoot.isDirectory) {
            cleanCacheDirectoryRecursively(
                dir = cacheRoot,
                diagnosticsDir = diagnosticsDir,
                nowMs = nowMs,
                onDeleted = { bytes ->
                    deletedCount++
                    reclaimedBytes += bytes
                },
            )
        }

        // 2. Clean non-bookshelf cover files (`imgs/{aid}.jpg`)
        val shelfAids = database.novelDao().getBookshelfNovelIds().toHashSet()
        for (root in assetRootsProvider()) {
            val imgsDir = File(root, IMGS_DIR_NAME)
            val coverFiles = imgsDir.listFiles().orEmpty()
            for (file in coverFiles) {
                currentCoroutineContext().ensureActive()
                if (!file.isFile) continue
                val stem = file.nameWithoutExtension
                val parsedAid = stem.toIntOrNull()
                if (parsedAid != null && parsedAid !in shelfAids) {
                    val fileLen = file.length().coerceAtLeast(0L)
                    if (file.delete()) {
                        deletedCount++
                        reclaimedBytes += fileLen
                    }
                }
            }
        }

        // 3. Prune non-bookshelf transient novel metadata rows
        val prunedRows = database.novelDao().deleteAllNonBookshelfNovels()

        CacheCleanSummary(
            deletedFileCount = deletedCount,
            reclaimedBytes = reclaimedBytes,
            prunedTransientNovelRows = prunedRows,
        )
    }

    /**
     * **Deep Orphan Clean (`深度清理`, Cancellable)**:
     * Runs [runFastCacheClean], then computes the live illustration filename set from
     * `ChapterContentDao.getAllNonEmptyImageUrlsJson()` (zero `CursorWindow` `2 MB` risk!) and
     * deletes unreferenced orphan image files inside `saves/imgs/`.
     */
    suspend fun runDeepOrphanCacheClean(
        onProgress: (DeepCleanProgress) -> Unit = {},
    ): CacheCleanSummary = withContext(ioDispatcher) {
        val fastSummary = runFastCacheClean()
        currentCoroutineContext().ensureActive()

        val liveFileNames = HashSet<String>()
        val allJsonLists = database.chapterContentDao().getAllNonEmptyImageUrlsJson()
        for (rawJson in allJsonLists) {
            currentCoroutineContext().ensureActive()
            runCatching {
                val array = JSONArray(rawJson)
                for (index in 0 until array.length()) {
                    val url = array.optString(index).trim()
                    if (url.isNotEmpty()) {
                        val legacyName = WenkuXmlParsers.generateLegacyImageFileName(url)
                        if (legacyName.isNotEmpty()) {
                            liveFileNames.add(legacyName)
                        }
                        val tailName = url.substringBefore('?').substringAfterLast('/').trim()
                        if (tailName.isNotEmpty()) {
                            liveFileNames.add(tailName)
                        }
                    }
                }
            }
        }

        val candidateFiles = ArrayList<File>()
        for (root in assetRootsProvider()) {
            val savesImgsDir = File(File(root, SAVES_DIR_NAME), IMGS_DIR_NAME)
            savesImgsDir.listFiles()?.filterTo(candidateFiles) { it.isFile }
        }

        val totalToScan = candidateFiles.size
        var scanned = 0
        var orphanDeleted = 0
        var orphanReclaimedBytes = 0L

        onProgress(
            DeepCleanProgress(
                scannedFiles = 0,
                totalFiles = totalToScan,
                deletedOrphanFiles = 0,
                reclaimedBytes = fastSummary.reclaimedBytes,
            ),
        )

        for (file in candidateFiles) {
            currentCoroutineContext().ensureActive()
            scanned++
            val isOrphan = file.name !in liveFileNames
            val isCorrupt = !isOrphan && !WenkuImageValidator.hasValidImageStructure(file)
            if (isOrphan || isCorrupt) {
                val len = file.length().coerceAtLeast(0L)
                if (file.delete()) {
                    orphanDeleted++
                    orphanReclaimedBytes += len
                }
            }
            if (scanned % 10 == 0 || scanned == totalToScan) {
                onProgress(
                    DeepCleanProgress(
                        scannedFiles = scanned,
                        totalFiles = totalToScan,
                        deletedOrphanFiles = orphanDeleted,
                        reclaimedBytes = fastSummary.reclaimedBytes + orphanReclaimedBytes,
                    ),
                )
            }
        }

        CacheCleanSummary(
            deletedFileCount = fastSummary.deletedFileCount + orphanDeleted,
            reclaimedBytes = fastSummary.reclaimedBytes + orphanReclaimedBytes,
            prunedTransientNovelRows = fastSummary.prunedTransientNovelRows,
        )
    }

    /**
     * Clears the cached offline chapters specified by [cids] (single chapter, single volume, or
     * arbitrary selection) along with any offline illustrations no longer referenced by other
     * cached chapters. Reading progress, bookmarks, and bookshelf entries remain untouched.
     */
    suspend fun clearCachedChapters(
        cids: Collection<Int>,
        purgeNonBookshelfCoverIfEmptied: Boolean = true,
    ): CustomCacheCleanResult = withContext(ioDispatcher) {
        clearCachedChaptersInternal(
            database = database,
            cids = cids,
            assetRoots = assetRootsProvider(),
            cacheRoot = cacheRootProvider(),
            appContext = appContext,
            purgeNonBookshelfCoverIfEmptied = purgeNonBookshelfCoverIfEmptied,
            coverUrlProvider = coverUrlProvider,
        )
    }

    /**
     * Scans temporary cache, non-bookshelf browse data, orphan/corrupt offline images, and all
     * cached books -> volumes -> chapters so the M3e Custom Cache Cleanup UI can display accurate
     * per-category and per-book/volume/chapter storage sizes without reading chapter body blobs.
     */
    suspend fun inspectCustomCacheInventory(): CustomCacheInventory = withContext(ioDispatcher) {
        val nowMs = System.currentTimeMillis()
        val cacheRoot = cacheRootProvider()
        val diagnosticsDir = File(cacheRoot, "diagnostics")
        var tempCacheBytes = 0L
        var tempCacheFileCount = 0
        if (cacheRoot.isDirectory) {
            measureCacheDirectoryRecursively(
                dir = cacheRoot,
                diagnosticsDir = diagnosticsDir,
                nowMs = nowMs,
                onFile = { bytes ->
                    tempCacheFileCount++
                    tempCacheBytes += bytes
                },
            )
        }

        val shelfAids = database.novelDao().getBookshelfNovelIds().toHashSet()
        val nonBookshelfNovelCount = database.novelDao().countNonBookshelfNovels()
        val assetRoots = assetRootsProvider()

        var nonBookshelfCoverCount = 0
        var nonBookshelfBrowseBytes = 0L
        for (root in assetRoots) {
            val imgsDir = File(root, IMGS_DIR_NAME)
            for (file in imgsDir.listFiles().orEmpty()) {
                currentCoroutineContext().ensureActive()
                if (!file.isFile) continue
                val parsedAid = file.nameWithoutExtension.toIntOrNull()
                if (parsedAid != null && parsedAid !in shelfAids) {
                    nonBookshelfCoverCount++
                    nonBookshelfBrowseBytes += file.length().coerceAtLeast(0L)
                }
            }
        }

        val allCachedMeta = database.chapterContentDao().getAllCachedChapterCacheMeta()
        val liveImageFileNames = HashSet<String>()
        val chapterImageNamesByCid = HashMap<Int, Set<String>>(allCachedMeta.size)
        for (meta in allCachedMeta) {
            currentCoroutineContext().ensureActive()
            val urls = extractImageUrlsFromJson(meta.imageUrlsJson)
            if (urls.isNotEmpty()) {
                val namesForChapter = LinkedHashSet<String>(urls.size)
                for (url in urls) {
                    val names = imageFileNamesForUrl(url)
                    liveImageFileNames.addAll(names)
                    val primaryName = WenkuXmlParsers.generateLegacyImageFileName(url)
                        .ifEmpty { url.substringBefore('?').substringAfterLast('/').trim() }
                    if (primaryName.isNotEmpty()) {
                        namesForChapter.add(primaryName)
                    }
                }
                chapterImageNamesByCid[meta.cid] = namesForChapter
            }
        }

        var orphanImageFileCount = 0
        var orphanImageBytes = 0L
        val liveSavesImgSizes = HashMap<String, Long>()
        for (root in assetRoots) {
            val savesImgsDir = File(File(root, SAVES_DIR_NAME), IMGS_DIR_NAME)
            for (file in savesImgsDir.listFiles().orEmpty()) {
                currentCoroutineContext().ensureActive()
                if (!file.isFile) continue
                val fileLen = file.length().coerceAtLeast(0L)
                val isOrphan = file.name !in liveImageFileNames
                val isCorrupt = !isOrphan && !WenkuImageValidator.hasValidImageStructure(file)
                if (isOrphan || isCorrupt) {
                    orphanImageFileCount++
                    orphanImageBytes += fileLen
                } else {
                    liveSavesImgSizes[file.name] = (liveSavesImgSizes[file.name] ?: 0L) + fileLen
                }
            }
        }

        val books = if (allCachedMeta.isEmpty()) {
            emptyList()
        } else {
            val cachedAids = allCachedMeta.map { it.aid }.distinct()
            val novelSummariesByAid = database.novelDao()
                .getCachedNovelSummariesByAids(cachedAids)
                .associateBy { it.aid }
            val volumesByAid = database.tocDao()
                .getVolumesForNovels(cachedAids)
                .groupBy { it.aid }
            val chaptersByAid = database.tocDao()
                .getChaptersForNovels(cachedAids)
                .groupBy { it.aid }
            val metaByAid = allCachedMeta.groupBy { it.aid }

            cachedAids.mapNotNull { aid ->
                val novelMeta = metaByAid[aid].orEmpty()
                if (novelMeta.isEmpty()) return@mapNotNull null

                val summary = novelSummariesByAid[aid]
                val volumesForAid = volumesByAid[aid].orEmpty().associateBy { it.vid }
                val tocChaptersByCid = chaptersByAid[aid].orEmpty().associateBy { it.cid }

                val groupedByVid = LinkedHashMap<Int, MutableList<CustomCacheChapterItem>>()
                for (meta in novelMeta) {
                    val tocChap = tocChaptersByCid[meta.cid]
                    val vid = tocChap?.vid ?: -aid
                    val imgBytes = chapterImageNamesByCid[meta.cid].orEmpty().sumOf { name ->
                        liveSavesImgSizes[name] ?: 0L
                    }
                    val item = CustomCacheChapterItem(
                        cid = meta.cid,
                        aid = aid,
                        vid = vid,
                        chapterOrder = tocChap?.chapterOrder ?: Int.MAX_VALUE,
                        chapterTitle = tocChap?.chapterTitle?.takeIf { it.isNotBlank() }
                            ?: "CID #${meta.cid}",
                        textBytes = meta.byteLength.coerceAtLeast(0).toLong(),
                        imageBytes = imgBytes,
                        imageCount = meta.imageCount.coerceAtLeast(0),
                    )
                    groupedByVid.getOrPut(vid) { ArrayList() }.add(item)
                }

                val volumeItems = groupedByVid.entries.map { (vid, chapterItems) ->
                    val volEntity = volumesForAid[vid]
                    CustomCacheVolumeItem(
                        vid = vid,
                        aid = aid,
                        volumeOrder = volEntity?.volumeOrder ?: Int.MAX_VALUE,
                        volumeTitle = volEntity?.volumeTitle?.takeIf { it.isNotBlank() }
                            ?: "未归类章节",
                        chapters = chapterItems.sortedWith(
                            compareBy<CustomCacheChapterItem> { it.chapterOrder }.thenBy { it.cid },
                        ),
                    )
                }.sortedWith(
                    compareBy<CustomCacheVolumeItem> { it.volumeOrder }.thenBy { it.vid },
                )

                CustomCacheBookItem(
                    aid = aid,
                    title = summary?.title?.takeIf { it.isNotBlank() } ?: "AID #$aid",
                    coverUrl = coverUrlProvider(aid),
                    inBookshelf = summary?.inBookshelf ?: (aid in shelfAids),
                    volumes = volumeItems,
                )
            }.sortedWith(
                compareByDescending<CustomCacheBookItem> { it.totalBytes }
                    .thenByDescending { it.cachedChapterCount }
                    .thenBy { it.aid },
            )
        }

        CustomCacheInventory(
            tempCacheBytes = tempCacheBytes,
            tempCacheFileCount = tempCacheFileCount,
            nonBookshelfBrowseBytes = nonBookshelfBrowseBytes,
            nonBookshelfCoverCount = nonBookshelfCoverCount,
            nonBookshelfNovelCount = nonBookshelfNovelCount,
            orphanImageBytes = orphanImageBytes,
            orphanImageFileCount = orphanImageFileCount,
            books = books,
        )
    }

    /**
     * Executes a user-curated [CustomCacheCleanRequest] across categories and/or chapter selections.
     * Minimum offline cleanup unit is a chapter (deleting a chapter removes its text + any
     * exclusively referenced offline illustrations).
     */
    suspend fun runCustomCacheClean(
        request: CustomCacheCleanRequest,
    ): CustomCacheCleanResult = withContext(ioDispatcher) {
        if (!request.hasAnySelection) return@withContext CustomCacheCleanResult()

        var deletedChapters = 0
        var deletedFiles = 0
        var reclaimedBytes = 0L
        var prunedRows = 0
        var purgedCovers = 0

        // 1. Clean selected chapters (plus any cached chapters belonging to non-bookshelf novels
        //    if the user also opted to clean non-bookshelf browse data, so their offline images
        //    are properly deleted instead of orphaned by CASCADE!).
        val cidsToClean = LinkedHashSet<Int>(request.selectedChapterCids)
        if (request.cleanNonBookshelfBrowse) {
            val nonShelfAids = database.novelDao().getNonBookshelfNovelIds()
            if (nonShelfAids.isNotEmpty()) {
                cidsToClean.addAll(database.chapterContentDao().getCachedChapterIdsForNovels(nonShelfAids))
            }
        }
        if (cidsToClean.isNotEmpty()) {
            val chapRes = clearCachedChaptersInternal(
                database = database,
                cids = cidsToClean,
                assetRoots = assetRootsProvider(),
                cacheRoot = cacheRootProvider(),
                appContext = appContext,
                purgeNonBookshelfCoverIfEmptied = !request.cleanNonBookshelfBrowse,
                coverUrlProvider = coverUrlProvider,
            )
            deletedChapters += chapRes.deletedChaptersCount
            deletedFiles += chapRes.deletedFileCount
            reclaimedBytes += chapRes.reclaimedBytes
            purgedCovers += chapRes.purgedCoverCount
        }

        // 2. Clean temporary HTTP/image cache (`cacheDir` + Coil caches, keeping `< 24h` diagnostics)
        if (request.cleanTempCache) {
            appContext?.let { ctx ->
                runCatching {
                    val loader = SingletonImageLoader.get(ctx)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
            }
            val cacheRoot = cacheRootProvider()
            val diagnosticsDir = File(cacheRoot, "diagnostics")
            if (cacheRoot.isDirectory) {
                cleanCacheDirectoryRecursively(
                    dir = cacheRoot,
                    diagnosticsDir = diagnosticsDir,
                    nowMs = System.currentTimeMillis(),
                    onDeleted = { bytes ->
                        deletedFiles++
                        reclaimedBytes += bytes
                    },
                )
            }
        }

        // 3. Clean non-bookshelf covers + transient metadata rows
        if (request.cleanNonBookshelfBrowse) {
            val shelfAids = database.novelDao().getBookshelfNovelIds().toHashSet()
            for (root in assetRootsProvider()) {
                val imgsDir = File(root, IMGS_DIR_NAME)
                for (file in imgsDir.listFiles().orEmpty()) {
                    currentCoroutineContext().ensureActive()
                    if (!file.isFile) continue
                    val parsedAid = file.nameWithoutExtension.toIntOrNull()
                    if (parsedAid != null && parsedAid !in shelfAids) {
                        val len = file.length().coerceAtLeast(0L)
                        if (file.delete()) {
                            deletedFiles++
                            purgedCovers++
                            reclaimedBytes += len
                        }
                    }
                }
            }
            prunedRows += database.novelDao().deleteAllNonBookshelfNovels()
        }

        // 4. Clean orphan & corrupt offline images under `saves/imgs/`
        if (request.cleanOrphanImages) {
            val liveFileNames = buildLiveImageFileNameSet(database)
            for (root in assetRootsProvider()) {
                val savesImgsDir = File(File(root, SAVES_DIR_NAME), IMGS_DIR_NAME)
                for (file in savesImgsDir.listFiles().orEmpty()) {
                    currentCoroutineContext().ensureActive()
                    if (!file.isFile) continue
                    val isOrphan = file.name !in liveFileNames
                    val isCorrupt = !isOrphan && !WenkuImageValidator.hasValidImageStructure(file)
                    if (isOrphan || isCorrupt) {
                        val len = file.length().coerceAtLeast(0L)
                        if (file.delete()) {
                            deletedFiles++
                            reclaimedBytes += len
                        }
                    }
                }
            }
        }

        CustomCacheCleanResult(
            deletedChaptersCount = deletedChapters,
            deletedFileCount = deletedFiles,
            reclaimedBytes = reclaimedBytes,
            prunedTransientNovelRows = prunedRows,
            purgedCoverCount = purgedCovers,
        )
    }

    /**
     * Copies a user-selected SAF [sourceUri] into `filesDir/custom/{slot}` (`menu_bg`,
     * `reader_font`, or `reader_background`), validating font magic bytes for `READER_FONT`.
     */
    suspend fun importCustomAsset(
        contentResolver: ContentResolver,
        sourceUri: Uri,
        slot: CustomAssetSlot,
    ): Result<File> = withContext(ioDispatcher) {
        val primaryRoot = assetRootsProvider().firstOrNull()
            ?: return@withContext Result.failure(IOException("Missing internal storage root"))
        val customDir = File(primaryRoot, CUSTOM_DIR_NAME).apply { mkdirs() }
        val stagingFile = File(customDir, "${slot.fileName}.tmp")
        val targetFile = File(customDir, slot.fileName)

        try {
            val input = contentResolver.openInputStream(sourceUri)
                ?: return@withContext Result.failure(IOException("Unable to open selected file"))
            input.use { inStream ->
                stagingFile.outputStream().use { outStream ->
                    inStream.copyTo(outStream)
                }
            }

            if (stagingFile.length() <= 0L) {
                stagingFile.delete()
                return@withContext Result.failure(IOException("Selected file is empty"))
            }

            if (slot == CustomAssetSlot.READER_FONT) {
                val header = ByteArray(4)
                val readCount = stagingFile.inputStream().use { it.read(header) }
                if (!hasValidFontSignature(header, readCount)) {
                    stagingFile.delete()
                    return@withContext Result.failure(
                        IllegalArgumentException("Invalid TTF/OTF font header"),
                    )
                }
            }

            if (targetFile.exists()) {
                targetFile.delete()
            }
            if (!stagingFile.renameTo(targetFile)) {
                stagingFile.copyTo(targetFile, overwrite = true)
                stagingFile.delete()
            }
            Result.success(targetFile)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            stagingFile.delete()
            throw cancelled
        } catch (error: Throwable) {
            stagingFile.delete()
            Result.failure(error)
        }
    }

    private suspend fun cleanCacheDirectoryRecursively(
        dir: File,
        diagnosticsDir: File,
        nowMs: Long,
        onDeleted: (Long) -> Unit,
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (child.isDirectory) {
                cleanCacheDirectoryRecursively(child, diagnosticsDir, nowMs, onDeleted)
                if (child != diagnosticsDir && child.list()?.isEmpty() == true) {
                    child.delete()
                }
            } else if (child.isFile) {
                val isFreshDiagnosticReport = child.parentFile == diagnosticsDir &&
                    (nowMs - child.lastModified()) < DIAGNOSTICS_RETENTION_MS
                if (!isFreshDiagnosticReport) {
                    val bytes = child.length().coerceAtLeast(0L)
                    if (child.delete()) {
                        onDeleted(bytes)
                    }
                }
            }
        }
    }

    private suspend fun measureCacheDirectoryRecursively(
        dir: File,
        diagnosticsDir: File,
        nowMs: Long,
        onFile: (Long) -> Unit,
    ) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (child.isDirectory) {
                measureCacheDirectoryRecursively(child, diagnosticsDir, nowMs, onFile)
            } else if (child.isFile) {
                val isFreshDiagnosticReport = child.parentFile == diagnosticsDir &&
                    (nowMs - child.lastModified()) < DIAGNOSTICS_RETENTION_MS
                if (!isFreshDiagnosticReport) {
                    onFile(child.length().coerceAtLeast(0L))
                }
            }
        }
    }
}
