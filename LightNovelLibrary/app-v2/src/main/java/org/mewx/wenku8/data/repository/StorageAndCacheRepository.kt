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
import org.mewx.wenku8.data.local.db.Wenku8Database
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

/**
 * Implements `v1.31`-parity Two-Mode Cache Cleanup (`快速清理` & cancellable `深度清理`) plus safe
 * SAF custom asset import (`filesDir/custom/{reader_font, reader_background}`).
 */
class StorageAndCacheRepository(
    private val database: Wenku8Database,
    private val cacheRootProvider: () -> File,
    private val assetRootsProvider: () -> List<File>,
    private val appContext: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
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
}
