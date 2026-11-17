package org.mewx.wenku8.data.repository

import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.room.withTransaction
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.dao.ChapterContentDao
import org.mewx.wenku8.data.local.db.entity.BookmarkNoteEntity
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.data.local.db.entity.ReadingSessionEntity
import org.mewx.wenku8.data.local.db.entity.ReviewDraftEntity
import org.mewx.wenku8.data.local.db.entity.SearchHistoryEntity
import org.mewx.wenku8.data.local.db.entity.VolumeEntity
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.reader.model.ReaderPageTurnMode
import org.mewx.wenku8.reader.model.ReaderPaperPreset
import org.mewx.wenku8.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class BackupArchiveMode(val wireMode: String) {
    /** Compact save (`manifest.json` + DB without `chapter_contents` + `settings.json` + `custom/`). */
    LIGHT_SAVE("light"),

    /** Complete offline archive (adds `chapter_contents`, `saves/imgs/`, and `imgs/`). */
    FULL_ARCHIVE("full"),
}

data class BackupExportSummary(
    val mode: BackupArchiveMode,
    val suggestedFileName: String,
    val includedImageFiles: Int,
)

data class BackupImportSummary(
    val mergedShelfNovels: Int,
    val mergedReadingProgress: Int,
    val mergedBookmarksNotes: Int,
    val mergedOfflineChapters: Int,
    val restoredImageFiles: Int,
)

/**
 * Portable `.wk8backup.zip` Export & Smart-Merge Import engine (`docs/designs/v2/09` §6).
 *
 * Security & Integrity Invariants:
 * 1. **Zero Credential or Local-Routing Leakage**: Strictly excludes `saves/cert.wk8`,
 *    `saves/avatar.jpg`, `PHPSESSID`, and `network_routing_local` on both export and import.
 * 2. **Zip-Slip Protection**: Every ZIP entry path is canonicalized and verified to stay strictly
 *    inside the temporary staging directory before writing a single byte.
 * 3. **SQLite Integrity Gate**: Runs `PRAGMA integrity_check` (`== "ok"`) on the extracted SQLite
 *    snapshot before touching the user's live `wenku8_v2.db`.
 * 4. **Non-Destructive Smart Merge**: Unions `in_bookshelf = 1` novels, retains
 *    `MAX(updated_at_ms)` per `aid` for `reading_progress`, deduplicates `bookmarks_notes` and
 *    `reading_sessions`, and streams `chapter_contents` in `256,000`-char `SUBSTR` slices so
 *    omnibus chapters never overflow Android's `2 MB` `CursorWindow`.
 */
class BackupArchiveRepository(
    private val database: Wenku8Database,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val cacheRootProvider: () -> File,
    private val assetRootsProvider: () -> List<File>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val BACKUP_SCHEMA_ID: String = "wenku8-v2-backup-v1"
        const val ENTRY_MANIFEST_JSON: String = "manifest.json"
        const val ENTRY_SETTINGS_JSON: String = "settings.json"
        const val ENTRY_SNAPSHOT_DB: String = "wenku8_v2_snapshot.db"

        private val FORBIDDEN_FILE_NAMES = setOf(
            "cert.wk8",
            "account_uname.wk8",
            "avatar.jpg",
            "notice_dismissed.sha256",
            "network_routing_local.preferences_pb",
        )

        fun suggestedFileName(mode: BackupArchiveMode, nowMs: Long = System.currentTimeMillis()): String {
            val stamp = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(nowMs))
            val prefix = when (mode) {
                BackupArchiveMode.LIGHT_SAVE -> "wenku8-save"
                BackupArchiveMode.FULL_ARCHIVE -> "wenku8-full"
            }
            return "$prefix-$stamp.wk8backup.zip"
        }
    }

    suspend fun exportBackupArchive(
        mode: BackupArchiveMode,
        destinationStream: OutputStream,
        nowMs: Long = System.currentTimeMillis(),
    ): Result<BackupExportSummary> = withContext(ioDispatcher) {
        val stagingDir = File(cacheRootProvider(), "backup_export_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            val snapshotDbFile = File(stagingDir, ENTRY_SNAPSHOT_DB)
            createDatabaseSnapshot(snapshotDbFile, includeChapterContents = (mode == BackupArchiveMode.FULL_ARCHIVE))

            val currentPrefs = userPreferencesRepository.preferencesFlow.first()
            val primaryRoot = assetRootsProvider().firstOrNull()
            val manifestJson = buildManifestJson(mode, nowMs)
            val settingsJson = encodePortableSettingsJson(currentPrefs, primaryRoot)

            var exportedImages = 0
            ZipOutputStream(BufferedOutputStream(destinationStream)).use { zipOut ->
                writeUtf8Entry(zipOut, ENTRY_MANIFEST_JSON, manifestJson)
                writeUtf8Entry(zipOut, ENTRY_SETTINGS_JSON, settingsJson)
                writeFileEntry(zipOut, ENTRY_SNAPSHOT_DB, snapshotDbFile)

                // Include custom font / wallpaper files (`custom/menu_bg`, `custom/reader_font`, `custom/reader_background`)
                if (primaryRoot != null) {
                    val customDir = File(primaryRoot, StorageAndCacheRepository.CUSTOM_DIR_NAME)
                    for (slot in CustomAssetSlot.entries) {
                        val slotFile = File(customDir, slot.fileName)
                        if (slotFile.isFile && slotFile.length() > 0L) {
                            writeFileEntry(zipOut, "custom/${slot.fileName}", slotFile)
                        }
                    }
                }

                // Full archive mode additionally streams offline covers & illustrations
                if (mode == BackupArchiveMode.FULL_ARCHIVE) {
                    val seenArchivePaths = HashSet<String>()
                    for (root in assetRootsProvider()) {
                        exportedImages += appendImageDirectoryToZip(
                            zipOut = zipOut,
                            dir = File(root, StorageAndCacheRepository.IMGS_DIR_NAME),
                            zipPrefix = "imgs/",
                            seenArchivePaths = seenArchivePaths,
                        )
                        exportedImages += appendImageDirectoryToZip(
                            zipOut = zipOut,
                            dir = File(File(root, StorageAndCacheRepository.SAVES_DIR_NAME), StorageAndCacheRepository.IMGS_DIR_NAME),
                            zipPrefix = "saves/imgs/",
                            seenArchivePaths = seenArchivePaths,
                        )
                    }
                }
            }

            Result.success(
                BackupExportSummary(
                    mode = mode,
                    suggestedFileName = suggestedFileName(mode, nowMs),
                    includedImageFiles = exportedImages,
                ),
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    suspend fun importBackupArchiveSmartMerge(
        sourceStream: InputStream,
    ): Result<BackupImportSummary> = withContext(ioDispatcher) {
        val stagingDir = File(cacheRootProvider(), "backup_import_${UUID.randomUUID()}").apply { mkdirs() }
        try {
            extractArchiveSafely(sourceStream, stagingDir)

            val manifestFile = File(stagingDir, ENTRY_MANIFEST_JSON)
            if (!manifestFile.isFile) {
                return@withContext Result.failure(IllegalArgumentException("Missing manifest.json in backup archive"))
            }
            val manifestObj = JSONObject(manifestFile.readText(StandardCharsets.UTF_8))
            if (manifestObj.optString("schema") != BACKUP_SCHEMA_ID) {
                return@withContext Result.failure(IllegalArgumentException("Unsupported backup schema"))
            }

            val snapshotDbFile = File(stagingDir, ENTRY_SNAPSHOT_DB)
            if (!snapshotDbFile.isFile || snapshotDbFile.length() <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("Missing database snapshot in backup archive"))
            }

            // Verify PRAGMA integrity_check before touching any live user data!
            verifySqliteIntegrityOrThrow(snapshotDbFile)

            val mergeCounts = mergeVerifiedDatabaseSnapshot(snapshotDbFile)

            // Restore custom/* and merge imgs/* & saves/imgs/* into primary internal storage root
            val primaryRoot = assetRootsProvider().firstOrNull()
            var restoredImages = 0
            if (primaryRoot != null) {
                val stagedCustomDir = File(stagingDir, "custom")
                if (stagedCustomDir.isDirectory) {
                    val destCustomDir = File(primaryRoot, StorageAndCacheRepository.CUSTOM_DIR_NAME).apply { mkdirs() }
                    for (slot in CustomAssetSlot.entries) {
                        val src = File(stagedCustomDir, slot.fileName)
                        if (src.isFile && src.length() > 0L) {
                            src.copyTo(File(destCustomDir, slot.fileName), overwrite = true)
                        }
                    }
                }

                restoredImages += mergeImageDirectoryFromStaging(
                    stagedDir = File(stagingDir, "imgs"),
                    destDir = File(primaryRoot, StorageAndCacheRepository.IMGS_DIR_NAME),
                )
                restoredImages += mergeImageDirectoryFromStaging(
                    stagedDir = File(File(stagingDir, "saves"), "imgs"),
                    destDir = File(File(primaryRoot, StorageAndCacheRepository.SAVES_DIR_NAME), StorageAndCacheRepository.IMGS_DIR_NAME),
                )
            }

            // Apply portable settings.json (re-anchoring relative custom/* paths)
            val settingsFile = File(stagingDir, ENTRY_SETTINGS_JSON)
            if (settingsFile.isFile && primaryRoot != null) {
                val existingPrefs = userPreferencesRepository.preferencesFlow.first()
                val parsedPrefs = decodePortableSettingsJson(
                    rawJson = settingsFile.readText(StandardCharsets.UTF_8),
                    primaryRoot = primaryRoot,
                    fallbackPrefs = existingPrefs,
                )
                userPreferencesRepository.applyImportedPortablePreferences(parsedPrefs)
            }

            Result.success(
                BackupImportSummary(
                    mergedShelfNovels = mergeCounts.mergedShelfNovels,
                    mergedReadingProgress = mergeCounts.mergedProgress,
                    mergedBookmarksNotes = mergeCounts.mergedBookmarks,
                    mergedOfflineChapters = mergeCounts.mergedChapters,
                    restoredImageFiles = restoredImages,
                ),
            )
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun createDatabaseSnapshot(targetFile: File, includeChapterContents: Boolean) {
        val supportDb = database.openHelper.writableDatabase
        runCatching {
            supportDb.query("PRAGMA wal_checkpoint(TRUNCATE)").close()
        }

        val escapedPath = targetFile.absolutePath.replace("'", "''")
        try {
            supportDb.execSQL("VACUUM INTO '$escapedPath'")
        } catch (vacuumError: Throwable) {
            val liveDbPath = supportDb.path
            val liveFile = liveDbPath?.let(::File)
            if (liveFile != null && liveFile.isFile) {
                liveFile.copyTo(targetFile, overwrite = true)
            } else {
                throw IOException("Failed to snapshot SQLite database", vacuumError)
            }
        }

        SQLiteDatabase.openDatabase(
            targetFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READWRITE,
        ).use { snapshotDb ->
            // Portable backups deliberately omit account credentials (cert.wk8), so strip
            // per-account cloud sync ledger rows as well.
            runCatching { snapshotDb.execSQL("DELETE FROM cloud_bookcase_ledger") }
            if (!includeChapterContents) {
                snapshotDb.execSQL("DELETE FROM chapter_contents")
                snapshotDb.execSQL("VACUUM")
            }
        }
    }

    private fun verifySqliteIntegrityOrThrow(snapshotDbFile: File) {
        SQLiteDatabase.openDatabase(
            snapshotDbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { sqliteDb ->
            sqliteDb.rawQuery("PRAGMA integrity_check", null).use { cursor ->
                if (!cursor.moveToFirst()) {
                    throw IOException("SQLite integrity_check returned no rows")
                }
                val status = cursor.getString(0)?.trim().orEmpty()
                if (!status.equals("ok", ignoreCase = true)) {
                    throw IOException("Backup SQLite failed integrity_check: $status")
                }
            }
        }
    }

    private data class DbMergeCounts(
        val mergedShelfNovels: Int,
        val mergedProgress: Int,
        val mergedBookmarks: Int,
        val mergedChapters: Int,
    )

    private suspend fun mergeVerifiedDatabaseSnapshot(snapshotDbFile: File): DbMergeCounts {
        SQLiteDatabase.openDatabase(
            snapshotDbFile.absolutePath,
            null,
            SQLiteDatabase.OPEN_READONLY,
        ).use { backupDb ->
            val incomingNovels = readBackupNovels(backupDb)
            val incomingVolumes = readBackupVolumes(backupDb)
            val incomingChapters = readBackupChapters(backupDb)
            val incomingProgress = readBackupReadingProgress(backupDb)
            val incomingBookmarks = readBackupBookmarks(backupDb)
            val incomingSessions = readBackupSessions(backupDb)
            val incomingDrafts = readBackupDrafts(backupDb)
            val incomingHistory = readBackupSearchHistory(backupDb)
            val incomingChapterHeaders = readBackupChapterContentHeaders(backupDb)

            var mergedShelfCount = 0
            var mergedProgressCount = 0
            var mergedBookmarkCount = 0
            var mergedChapterCount = 0

            database.withTransaction {
                val novelDao = database.novelDao()
                val tocDao = database.tocDao()
                val progressDao = database.readingProgressDao()
                val bookmarkDao = database.bookmarkNoteDao()
                val sessionDao = database.readingSessionDao()
                val draftDao = database.reviewDraftDao()
                val historyDao = database.searchHistoryDao()
                val ledgerDao = database.cloudBookcaseLedgerDao()

                val aidsAcceptingBackupToc = HashSet<Int>()
                val newlyRestoredShelfAids = ArrayList<Int>()
                val existingShelfSnapshot = novelDao.getBookshelfNovelsSnapshot()
                val occupiedShelfOrders = existingShelfSnapshot.mapTo(HashSet()) { it.shelfSortOrder }
                var nextAppendedShelfOrder = (existingShelfSnapshot.maxOfOrNull { it.shelfSortOrder } ?: 0) + 1

                fun isPlaceholderTitle(title: String, aid: Int): Boolean {
                    val trimmed = title.trim()
                    return trimmed.isEmpty() || trimmed == "AID $aid" || trimmed == "AID #$aid"
                }

                // 1. Smart-merge novels (union in_bookshelf = 1, never drop shelf membership)
                for (incoming in incomingNovels) {
                    currentCoroutineContext().ensureActive()
                    val local = novelDao.getNovelByAid(incoming.aid)
                    val wasAlreadyOnLocalShelf = local?.inBookshelf == true
                    val unionShelf = wasAlreadyOnLocalShelf || incoming.inBookshelf
                    if (unionShelf) mergedShelfCount++
                    if (incoming.inBookshelf && !wasAlreadyOnLocalShelf) {
                        newlyRestoredShelfAids.add(incoming.aid)
                    }

                    val resolvedShelfSortOrder = when {
                        local != null && local.inBookshelf -> local.shelfSortOrder
                        incoming.inBookshelf -> {
                            if (occupiedShelfOrders.add(incoming.shelfSortOrder)) {
                                nextAppendedShelfOrder = maxOf(nextAppendedShelfOrder, incoming.shelfSortOrder + 1)
                                incoming.shelfSortOrder
                            } else {
                                val assigned = nextAppendedShelfOrder++
                                occupiedShelfOrders.add(assigned)
                                assigned
                            }
                        }
                        else -> local?.shelfSortOrder ?: incoming.shelfSortOrder
                    }

                    if (local == null) {
                        novelDao.upsertNovel(
                            incoming.copy(
                                inBookshelf = unionShelf,
                                shelfSortOrder = resolvedShelfSortOrder,
                                cloudSynced = false,
                            ),
                        )
                        aidsAcceptingBackupToc.add(incoming.aid)
                    } else {
                        val incomingIsNewer = incoming.metaSyncedAtMs > local.metaSyncedAtMs
                        if (incoming.tocVersion > local.tocVersion) {
                            aidsAcceptingBackupToc.add(incoming.aid)
                        }
                        val primary = if (incomingIsNewer) incoming else local
                        val secondary = if (incomingIsNewer) local else incoming
                        val resolvedTitle = when {
                            !isPlaceholderTitle(primary.title, incoming.aid) -> primary.title
                            !isPlaceholderTitle(secondary.title, incoming.aid) -> secondary.title
                            else -> primary.title.ifBlank { "AID ${incoming.aid}" }
                        }
                        val detectedNewerSectionOnLocalShelf = wasAlreadyOnLocalShelf &&
                            local.latestSectionCid > 0 &&
                            incoming.latestSectionCid > local.latestSectionCid

                        val mergedNovel = primary.copy(
                            contentLang = primary.contentLang.ifBlank { secondary.contentLang },
                            title = resolvedTitle,
                            author = primary.author.ifBlank { secondary.author },
                            bookStatus = primary.bookStatus.ifBlank { secondary.bookStatus },
                            pressId = primary.pressId.ifBlank { secondary.pressId },
                            bookLength = if (primary.bookLength > 0) primary.bookLength else secondary.bookLength,
                            lastUpdate = primary.lastUpdate.ifBlank { secondary.lastUpdate },
                            latestSectionCid = maxOf(local.latestSectionCid, incoming.latestSectionCid),
                            latestSectionName = if (incoming.latestSectionCid >= local.latestSectionCid &&
                                incoming.latestSectionName.isNotBlank()
                            ) {
                                incoming.latestSectionName
                            } else {
                                local.latestSectionName.ifBlank { incoming.latestSectionName }
                            },
                            dayHits = if (primary.dayHits > 0) primary.dayHits else secondary.dayHits,
                            totalHits = if (primary.totalHits > 0) primary.totalHits else secondary.totalHits,
                            pushCount = if (primary.pushCount > 0) primary.pushCount else secondary.pushCount,
                            favCount = if (primary.favCount > 0) primary.favCount else secondary.favCount,
                            tagsCsv = primary.tagsCsv.ifBlank { secondary.tagsCsv },
                            inBookshelf = unionShelf,
                            shelfCategory = if (wasAlreadyOnLocalShelf) local.shelfCategory else incoming.shelfCategory,
                            shelfSortOrder = resolvedShelfSortOrder,
                            cloudSynced = if (wasAlreadyOnLocalShelf) local.cloudSynced else false,
                            hasUnreadUpdate = if (wasAlreadyOnLocalShelf) {
                                local.hasUnreadUpdate || detectedNewerSectionOnLocalShelf
                            } else {
                                incoming.hasUnreadUpdate
                            },
                            tocVersion = maxOf(local.tocVersion, incoming.tocVersion),
                            tocSha256 = if (incoming.tocVersion >= local.tocVersion && incoming.tocSha256.isNotEmpty()) {
                                incoming.tocSha256
                            } else {
                                local.tocSha256
                            },
                            metaSyncedAtMs = maxOf(local.metaSyncedAtMs, incoming.metaSyncedAtMs),
                            fullIntro = primary.fullIntro.ifEmpty { secondary.fullIntro },
                            introShort = primary.introShort.ifEmpty { secondary.introShort },
                            lastAccessedMs = maxOf(local.lastAccessedMs, incoming.lastAccessedMs),
                        )
                        novelDao.upsertNovel(mergedNovel)
                    }
                }
                if (newlyRestoredShelfAids.isNotEmpty()) {
                    ledgerDao.clearEntriesForAids(newlyRestoredShelfAids)
                }

                // Also accept TOC rows for novels that locally had 0 volumes
                for (novel in incomingNovels) {
                    if (novel.aid !in aidsAcceptingBackupToc && tocDao.getVolumeIdsForNovel(novel.aid).isEmpty()) {
                        aidsAcceptingBackupToc.add(novel.aid)
                    }
                }

                // 2. Upsert TOC volumes & chapters for accepted novels
                val volumesToUpsert = incomingVolumes.filter { it.aid in aidsAcceptingBackupToc }
                val chaptersToUpsert = incomingChapters.filter { it.aid in aidsAcceptingBackupToc }
                if (volumesToUpsert.isNotEmpty()) {
                    tocDao.upsertVolumes(volumesToUpsert)
                }
                if (chaptersToUpsert.isNotEmpty()) {
                    tocDao.upsertChapters(chaptersToUpsert)
                }

                suspend fun ensureParentNovelExists(aid: Int) {
                    if (novelDao.getNovelByAid(aid) == null) {
                        novelDao.upsertNovel(NovelEntity(aid = aid, title = "AID $aid"))
                    }
                }

                // 3. Reading progress: keep MAX(updated_at_ms) per aid
                for (incomingProg in incomingProgress) {
                    ensureParentNovelExists(incomingProg.aid)
                    val localProg = progressDao.getProgressForNovel(incomingProg.aid)
                    if (localProg == null || incomingProg.updatedAtMs > localProg.updatedAtMs) {
                        progressDao.upsertProgress(incomingProg)
                        mergedProgressCount++
                    }
                }

                // 4. Bookmarks & notes: idempotent union deduplicated by content signature
                val existingNoteKeys = bookmarkDao.getAllBookmarkNotes()
                    .mapTo(HashSet()) { bookmarkDedupKey(it) }
                for (note in incomingBookmarks) {
                    if (existingNoteKeys.add(bookmarkDedupKey(note))) {
                        ensureParentNovelExists(note.aid)
                        bookmarkDao.upsertBookmarkNote(note.copy(id = 0L))
                        mergedBookmarkCount++
                    }
                }

                // 5. Reading sessions: idempotent union
                val existingSessionKeys = sessionDao.getAllSessions()
                    .mapTo(HashSet()) { sessionDedupKey(it) }
                for (session in incomingSessions) {
                    if (existingSessionKeys.add(sessionDedupKey(session))) {
                        ensureParentNovelExists(session.aid)
                        sessionDao.insertSession(session.copy(id = 0L))
                    }
                }

                // 6. Review drafts: keep MAX(updated_at_ms) per (aid, rid)
                for (draft in incomingDrafts) {
                    val localDraft = draftDao.getDraft(draft.aid, draft.rid)
                    if (localDraft == null || draft.updatedAtMs > localDraft.updatedAtMs) {
                        ensureParentNovelExists(draft.aid)
                        draftDao.upsertDraft(draft)
                    }
                }

                // 7. Search history: keep MAX(searched_at_ms) per query, trimmed to 20
                val localHistoryByQuery = historyDao.getAllHistory().associateBy { it.query }
                val historyToUpsert = incomingHistory.filter { inc ->
                    val existing = localHistoryByQuery[inc.query]
                    existing == null || inc.searchedAtMs > existing.searchedAtMs
                }
                if (historyToUpsert.isNotEmpty()) {
                    historyDao.upsertHistoryEntries(historyToUpsert)
                    historyDao.trimHistoryToMax()
                }
            }

            // 8. Stream offline chapter_contents in batches of 20 using 256k-char SUBSTR slices
            val novelDao = database.novelDao()
            val chapterDao = database.chapterContentDao()
            val batch = ArrayList<ChapterContentEntity>(20)
            for (header in incomingChapterHeaders) {
                currentCoroutineContext().ensureActive()
                val localHeader = chapterDao.getChapterHeader(header.cid)
                if (localHeader == null || header.downloadedAtMs > localHeader.downloadedAtMs) {
                    val bodyText = readBackupChapterTextSafely(backupDb, header.cid, header.charLength)
                    if (bodyText != null) {
                        if (novelDao.getNovelByAid(header.aid) == null) {
                            novelDao.upsertNovel(NovelEntity(aid = header.aid, title = "AID ${header.aid}"))
                        }
                        batch.add(
                            ChapterContentEntity(
                                cid = header.cid,
                                aid = header.aid,
                                contentLang = header.contentLang,
                                contentText = bodyText,
                                byteLength = header.byteLength,
                                imageUrlsJson = header.imageUrlsJson,
                                imageCount = header.imageCount,
                                sha256Checksum = header.sha256Checksum,
                                downloadedAtMs = header.downloadedAtMs,
                            ),
                        )
                        mergedChapterCount++
                        if (batch.size >= 20) {
                            chapterDao.upsertChapterContents(batch.toList())
                            batch.clear()
                        }
                    }
                }
            }
            if (batch.isNotEmpty()) {
                chapterDao.upsertChapterContents(batch)
            }

            return DbMergeCounts(
                mergedShelfNovels = mergedShelfCount,
                mergedProgress = mergedProgressCount,
                mergedBookmarks = mergedBookmarkCount,
                mergedChapters = mergedChapterCount,
            )
        }
    }

    private fun extractArchiveSafely(sourceStream: InputStream, stagingDir: File) {
        val canonicalRootPrefix = stagingDir.canonicalPath + File.separator
        ZipInputStream(BufferedInputStream(sourceStream)).use { zipIn ->
            while (true) {
                val entry = zipIn.nextEntry ?: break
                val rawName = entry.name.replace('\\', '/').trimStart('/')
                if (rawName.isEmpty()) {
                    zipIn.closeEntry()
                    continue
                }
                val baseName = rawName.substringAfterLast('/')
                if (baseName in FORBIDDEN_FILE_NAMES || rawName.contains("network_routing_local")) {
                    zipIn.closeEntry()
                    continue
                }

                val target = File(stagingDir, rawName)
                if (!target.canonicalPath.startsWith(canonicalRootPrefix)) {
                    throw SecurityException("Rejected zip-slip entry path: ${entry.name}")
                }

                if (entry.isDirectory) {
                    target.mkdirs()
                } else {
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        zipIn.copyTo(out)
                    }
                }
                zipIn.closeEntry()
            }
        }
    }

    private suspend fun appendImageDirectoryToZip(
        zipOut: ZipOutputStream,
        dir: File,
        zipPrefix: String,
        seenArchivePaths: MutableSet<String>,
    ): Int {
        if (!dir.isDirectory) return 0
        var count = 0
        for (file in dir.listFiles().orEmpty()) {
            currentCoroutineContext().ensureActive()
            if (!file.isFile || file.length() <= 0L) continue
            if (file.name in FORBIDDEN_FILE_NAMES) continue
            val entryPath = zipPrefix + file.name
            if (seenArchivePaths.add(entryPath)) {
                writeFileEntry(zipOut, entryPath, file)
                count++
            }
        }
        return count
    }

    private suspend fun mergeImageDirectoryFromStaging(stagedDir: File, destDir: File): Int {
        if (!stagedDir.isDirectory) return 0
        destDir.mkdirs()
        var copied = 0
        for (file in stagedDir.listFiles().orEmpty()) {
            currentCoroutineContext().ensureActive()
            if (!file.isFile || file.length() <= 0L || file.name in FORBIDDEN_FILE_NAMES) continue
            val dest = File(destDir, file.name)
            if (!dest.isFile || dest.length() <= 0L) {
                file.copyTo(dest, overwrite = true)
                copied++
            }
        }
        return copied
    }

    private fun buildManifestJson(mode: BackupArchiveMode, nowMs: Long): String {
        return JSONObject().apply {
            put("schema", BACKUP_SCHEMA_ID)
            put("mode", mode.wireMode)
            put("createdAtMs", nowMs)
            put("appVersionName", BuildConfig.VERSION_NAME)
            put("dbVersion", 1)
        }.toString(2)
    }

    private fun encodePortableSettingsJson(
        prefs: Wenku8UserPreferences,
        primaryRoot: File?,
    ): String {
        fun portableCustomToken(rawPath: String, slot: CustomAssetSlot): String {
            if (rawPath.isBlank()) return ""
            if (primaryRoot != null) {
                val expected = File(File(primaryRoot, StorageAndCacheRepository.CUSTOM_DIR_NAME), slot.fileName)
                if (File(rawPath).absolutePath == expected.absolutePath || rawPath.endsWith("/custom/${slot.fileName}")) {
                    return "custom/${slot.fileName}"
                }
            }
            return ""
        }

        return JSONObject().apply {
            put("appLanguage", prefs.appLanguage.name)
            put("themeMode", prefs.themeMode.name)
            put("einkModeEnabled", prefs.einkModeEnabled)
            put("useDynamicColor", prefs.useDynamicColor)
            put("defaultHomeDestination", prefs.defaultHomeDestination.name)
            put("bookshelfViewMode", prefs.bookshelfViewMode.name)
            put("bookshelfSortMode", prefs.bookshelfSortMode.name)
            put("menuBackgroundPresetId", prefs.menuBackgroundPresetId)
            put("menuBackgroundCustomToken", portableCustomToken(prefs.menuBackgroundCustomPath, CustomAssetSlot.MENU_BACKGROUND))
            put("readerFontSizeSp", prefs.readerFontSizeSp)
            put("readerLineDistanceDp", prefs.readerLineDistanceDp)
            put("readerParagraphDistanceDp", prefs.readerParagraphDistanceDp)
            put("readerPageEdgeDistanceDp", prefs.readerPageEdgeDistanceDp)
            put("readerCustomFontToken", portableCustomToken(prefs.readerCustomFontPath, CustomAssetSlot.READER_FONT))
            put("readerCustomBgToken", portableCustomToken(prefs.readerCustomBackgroundPath, CustomAssetSlot.READER_BACKGROUND))
            put("readerPageTurnMode", prefs.readerPageTurnMode.name)
            put("readerPaperPreset", prefs.readerPaperPreset.name)
            put("readerKeepScreenOn", prefs.readerKeepScreenOn)
            put("readerEdgeBrightnessDragEnabled", prefs.readerEdgeBrightnessDragEnabled)
            put("volumeKeyPagingEnabled", prefs.volumeKeyPagingEnabled)
            put("autoOptimizeConnectionEnabled", prefs.autoOptimizeConnectionEnabled)
        }.toString(2)
    }

    private fun decodePortableSettingsJson(
        rawJson: String,
        primaryRoot: File,
        fallbackPrefs: Wenku8UserPreferences = Wenku8UserPreferences(),
    ): Wenku8UserPreferences {
        val obj = JSONObject(rawJson)
        fun resolveCustomToken(token: String, slot: CustomAssetSlot): String {
            if (token != "custom/${slot.fileName}") return ""
            val candidate = File(File(primaryRoot, StorageAndCacheRepository.CUSTOM_DIR_NAME), slot.fileName)
            return if (candidate.isFile && candidate.length() > 0L) candidate.absolutePath else ""
        }

        val lang = runCatching {
            Wenku8API.AppLanguage.valueOf(obj.optString("appLanguage", fallbackPrefs.appLanguage.name))
        }.getOrDefault(fallbackPrefs.appLanguage)
        val theme = runCatching {
            Wenku8ThemeMode.valueOf(obj.optString("themeMode", fallbackPrefs.themeMode.name))
        }.getOrDefault(fallbackPrefs.themeMode)
        val home = runCatching {
            DefaultHomeDestination.valueOf(obj.optString("defaultHomeDestination", fallbackPrefs.defaultHomeDestination.name))
        }.getOrDefault(fallbackPrefs.defaultHomeDestination)
        val shelfView = runCatching {
            BookshelfViewMode.valueOf(obj.optString("bookshelfViewMode", fallbackPrefs.bookshelfViewMode.name))
        }.getOrDefault(fallbackPrefs.bookshelfViewMode)
        val shelfSort = runCatching {
            BookshelfSortMode.valueOf(obj.optString("bookshelfSortMode", fallbackPrefs.bookshelfSortMode.name))
        }.getOrDefault(fallbackPrefs.bookshelfSortMode)
        val pageTurnMode = runCatching {
            ReaderPageTurnMode.valueOf(obj.optString("readerPageTurnMode", fallbackPrefs.readerPageTurnMode.name))
        }.getOrDefault(fallbackPrefs.readerPageTurnMode)
        val paperPreset = runCatching {
            ReaderPaperPreset.valueOf(obj.optString("readerPaperPreset", fallbackPrefs.readerPaperPreset.name))
        }.getOrDefault(fallbackPrefs.readerPaperPreset)

        return Wenku8UserPreferences(
            appLanguage = lang,
            themeMode = theme,
            einkModeEnabled = obj.optBoolean("einkModeEnabled", theme == Wenku8ThemeMode.E_INK),
            useDynamicColor = obj.optBoolean("useDynamicColor", fallbackPrefs.useDynamicColor),
            defaultHomeDestination = home,
            bookshelfViewMode = shelfView,
            bookshelfSortMode = shelfSort,
            menuBackgroundPresetId = obj.optInt("menuBackgroundPresetId", fallbackPrefs.menuBackgroundPresetId).coerceIn(0, 5),
            menuBackgroundCustomPath = resolveCustomToken(obj.optString("menuBackgroundCustomToken", ""), CustomAssetSlot.MENU_BACKGROUND),
            readerFontSizeSp = obj.optInt("readerFontSizeSp", fallbackPrefs.readerFontSizeSp),
            readerLineDistanceDp = obj.optInt("readerLineDistanceDp", fallbackPrefs.readerLineDistanceDp),
            readerParagraphDistanceDp = obj.optInt("readerParagraphDistanceDp", fallbackPrefs.readerParagraphDistanceDp),
            readerPageEdgeDistanceDp = obj.optInt("readerPageEdgeDistanceDp", fallbackPrefs.readerPageEdgeDistanceDp),
            readerCustomFontPath = resolveCustomToken(obj.optString("readerCustomFontToken", ""), CustomAssetSlot.READER_FONT),
            readerCustomBackgroundPath = resolveCustomToken(obj.optString("readerCustomBgToken", ""), CustomAssetSlot.READER_BACKGROUND),
            readerPageTurnMode = pageTurnMode,
            readerPaperPreset = paperPreset,
            readerKeepScreenOn = obj.optBoolean("readerKeepScreenOn", fallbackPrefs.readerKeepScreenOn),
            readerEdgeBrightnessDragEnabled = obj.optBoolean("readerEdgeBrightnessDragEnabled", fallbackPrefs.readerEdgeBrightnessDragEnabled),
            volumeKeyPagingEnabled = obj.optBoolean("volumeKeyPagingEnabled", fallbackPrefs.volumeKeyPagingEnabled),
            autoOptimizeConnectionEnabled = obj.optBoolean("autoOptimizeConnectionEnabled", fallbackPrefs.autoOptimizeConnectionEnabled),
        )
    }

    private fun writeUtf8Entry(zipOut: ZipOutputStream, entryName: String, text: String) {
        zipOut.putNextEntry(ZipEntry(entryName))
        zipOut.write(text.toByteArray(StandardCharsets.UTF_8))
        zipOut.closeEntry()
    }

    private fun writeFileEntry(zipOut: ZipOutputStream, entryName: String, file: File) {
        zipOut.putNextEntry(ZipEntry(entryName))
        file.inputStream().use { it.copyTo(zipOut) }
        zipOut.closeEntry()
    }

    private fun bookmarkDedupKey(n: BookmarkNoteEntity): String =
        "${n.aid}:${n.cid}:${n.startParagraph}:${n.startChar}:${n.snippetText}:${n.noteMarkdown}"

    private fun sessionDedupKey(s: ReadingSessionEntity): String =
        "${s.aid}:${s.cid}:${s.dateEpochDay}:${s.durationSeconds}:${s.charactersRead}"

    private data class BackupChapterHeaderRow(
        val cid: Int,
        val aid: Int,
        val contentLang: String,
        val charLength: Int,
        val byteLength: Int,
        val imageUrlsJson: String,
        val imageCount: Int,
        val sha256Checksum: String,
        val downloadedAtMs: Long,
    )

    private fun readBackupNovels(db: SQLiteDatabase): List<NovelEntity> =
        db.queryList("SELECT * FROM novels") { c ->
            NovelEntity(
                aid = c.intCol("aid"),
                contentLang = c.strCol("content_lang", "SC"),
                tocVersion = c.longCol("toc_version"),
                tocSha256 = c.strCol("toc_sha256"),
                metaSyncedAtMs = c.longCol("meta_synced_at_ms"),
                title = c.strCol("title"),
                author = c.strCol("author"),
                bookStatus = c.strCol("book_status"),
                pressId = c.strCol("press_id"),
                bookLength = c.intCol("book_length"),
                lastUpdate = c.strCol("last_update"),
                latestSectionCid = c.intCol("latest_section_cid"),
                latestSectionName = c.strCol("latest_section_name"),
                dayHits = c.intCol("day_hits"),
                totalHits = c.intCol("total_hits"),
                pushCount = c.intCol("push_count"),
                favCount = c.intCol("fav_count"),
                tagsCsv = c.strCol("tags_csv"),
                introShort = c.strCol("intro_short"),
                fullIntro = c.strCol("full_intro"),
                inBookshelf = c.intCol("in_bookshelf") != 0,
                shelfCategory = c.strCol("shelf_category", NovelEntity.DEFAULT_SHELF_CATEGORY),
                cloudSynced = c.intCol("cloud_synced") != 0,
                hasUnreadUpdate = c.intCol("has_unread_update") != 0,
                shelfSortOrder = c.intCol("shelf_sort_order"),
                lastAccessedMs = c.longCol("last_accessed_ms"),
            )
        }

    private fun readBackupVolumes(db: SQLiteDatabase): List<VolumeEntity> =
        db.queryList("SELECT * FROM volumes") { c ->
            VolumeEntity(
                vid = c.intCol("vid"),
                aid = c.intCol("aid"),
                volumeOrder = c.intCol("volume_order"),
                volumeTitle = c.strCol("volume_title"),
            )
        }

    private fun readBackupChapters(db: SQLiteDatabase): List<ChapterEntity> =
        db.queryList("SELECT * FROM chapters") { c ->
            ChapterEntity(
                cid = c.intCol("cid"),
                vid = c.intCol("vid"),
                aid = c.intCol("aid"),
                chapterOrder = c.intCol("chapter_order"),
                chapterTitle = c.strCol("chapter_title"),
                isIllustrationChapter = c.intCol("is_illustration_chapter") != 0,
            )
        }

    private fun readBackupReadingProgress(db: SQLiteDatabase): List<ReadingProgressEntity> =
        db.queryList("SELECT * FROM reading_progress") { c ->
            ReadingProgressEntity(
                aid = c.intCol("aid"),
                vid = c.intCol("vid"),
                cid = c.intCol("cid"),
                volumeTitle = c.strCol("volume_title"),
                chapterTitle = c.strCol("chapter_title"),
                paragraphIndex = c.intCol("paragraph_index"),
                charOffset = c.intCol("char_offset"),
                legacyScrollPx = c.intCol("legacy_scroll_px"),
                progressPermille = c.intCol("progress_permille"),
                updatedAtMs = c.longCol("updated_at_ms"),
            )
        }

    private fun readBackupBookmarks(db: SQLiteDatabase): List<BookmarkNoteEntity> =
        db.queryList("SELECT * FROM bookmarks_notes") { c ->
            BookmarkNoteEntity(
                id = c.longCol("id"),
                aid = c.intCol("aid"),
                vid = c.intCol("vid"),
                cid = c.intCol("cid"),
                chapterTitle = c.strCol("chapter_title"),
                startParagraph = c.intCol("start_paragraph"),
                startChar = c.intCol("start_char"),
                endParagraph = c.intCol("end_paragraph"),
                endChar = c.intCol("end_char"),
                snippetText = c.strCol("snippet_text"),
                noteMarkdown = c.strCol("note_markdown"),
                styleType = c.strCol("style_type", BookmarkNoteEntity.STYLE_BOOKMARK),
                colorHex = c.strCol("color_hex", "#E8B966"),
                createdAtMs = c.longCol("created_at_ms"),
            )
        }

    private fun readBackupSessions(db: SQLiteDatabase): List<ReadingSessionEntity> =
        db.queryList("SELECT * FROM reading_sessions") { c ->
            ReadingSessionEntity(
                id = c.longCol("id"),
                aid = c.intCol("aid"),
                cid = c.intCol("cid"),
                dateEpochDay = c.longCol("date_epoch_day"),
                durationSeconds = c.intCol("duration_seconds"),
                charactersRead = c.intCol("characters_read"),
            )
        }

    private fun readBackupDrafts(db: SQLiteDatabase): List<ReviewDraftEntity> =
        db.queryList("SELECT * FROM review_drafts") { c ->
            ReviewDraftEntity(
                aid = c.intCol("aid"),
                rid = c.intCol("rid"),
                draftTitle = c.strCol("draft_title"),
                draftContent = c.strCol("draft_content"),
                updatedAtMs = c.longCol("updated_at_ms"),
            )
        }

    private fun readBackupSearchHistory(db: SQLiteDatabase): List<SearchHistoryEntity> =
        db.queryList("SELECT * FROM search_history") { c ->
            SearchHistoryEntity(
                query = c.strCol("query"),
                searchType = c.strCol("search_type", SearchHistoryEntity.SEARCH_TYPE_ARTICLE_NAME),
                searchedAtMs = c.longCol("searched_at_ms"),
            )
        }

    private fun readBackupChapterContentHeaders(db: SQLiteDatabase): List<BackupChapterHeaderRow> =
        db.queryList(
            """
            SELECT cid, aid, content_lang, LENGTH(content_text) AS char_len,
                   byte_length, image_urls_json, image_count, sha256_checksum, downloaded_at_ms
            FROM chapter_contents
            """.trimIndent(),
        ) { c ->
            BackupChapterHeaderRow(
                cid = c.intCol("cid"),
                aid = c.intCol("aid"),
                contentLang = c.strCol("content_lang", "SC"),
                charLength = c.intCol("char_len"),
                byteLength = c.intCol("byte_length"),
                imageUrlsJson = c.strCol("image_urls_json", "[]"),
                imageCount = c.intCol("image_count"),
                sha256Checksum = c.strCol("sha256_checksum"),
                downloadedAtMs = c.longCol("downloaded_at_ms"),
            )
        }

    private fun readBackupChapterTextSafely(db: SQLiteDatabase, cid: Int, charLen: Int): String? {
        if (charLen <= 0) return ""
        val sb = StringBuilder(charLen)
        var offsetOneIndexed = 1
        while (offsetOneIndexed <= charLen) {
            val maxChars = minOf(ChapterContentDao.SAFE_SLICE_CHAR_COUNT, charLen - offsetOneIndexed + 1)
            val slice = db.rawQuery(
                "SELECT SUBSTR(content_text, ?, ?) FROM chapter_contents WHERE cid = ? LIMIT 1",
                arrayOf(offsetOneIndexed.toString(), maxChars.toString(), cid.toString()),
            ).use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null
            } ?: return null
            if (slice.isEmpty()) break
            sb.append(slice)
            offsetOneIndexed += slice.length
        }
        return sb.toString()
    }

    private inline fun <T> SQLiteDatabase.queryList(sql: String, rowMapper: (Cursor) -> T): List<T> {
        return try {
            rawQuery(sql, null).use { cursor ->
                val list = ArrayList<T>(cursor.count.coerceAtLeast(0))
                while (cursor.moveToNext()) {
                    list.add(rowMapper(cursor))
                }
                list
            }
        } catch (_: Throwable) {
            emptyList()
        }
    }

    private fun Cursor.intCol(name: String, default: Int = 0): Int {
        val idx = getColumnIndex(name)
        return if (idx >= 0 && !isNull(idx)) getInt(idx) else default
    }

    private fun Cursor.longCol(name: String, default: Long = 0L): Long {
        val idx = getColumnIndex(name)
        return if (idx >= 0 && !isNull(idx)) getLong(idx) else default
    }

    private fun Cursor.strCol(name: String, default: String = ""): String {
        val idx = getColumnIndex(name)
        return if (idx >= 0 && !isNull(idx)) getString(idx) ?: default else default
    }
}
