package org.mewx.wenku8.ui.profile

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.api.Wenku8Error
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.BookmarkNoteEntity
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.data.local.db.entity.CloudBookcaseLedgerEntity
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.data.local.db.entity.ReadingSessionEntity
import org.mewx.wenku8.data.local.db.entity.VolumeEntity
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.data.remote.api.OnlineSearchField
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import org.mewx.wenku8.data.repository.AccountAuthGateway
import org.mewx.wenku8.data.repository.BackupArchiveMode
import org.mewx.wenku8.data.repository.BackupArchiveRepository
import org.mewx.wenku8.data.repository.BookshelfRepository
import org.mewx.wenku8.data.repository.BookshelfSortMode
import org.mewx.wenku8.data.repository.BookshelfViewMode
import org.mewx.wenku8.data.repository.CloudBookshelfSyncOutcome
import org.mewx.wenku8.data.repository.CustomAssetSlot
import org.mewx.wenku8.data.repository.CustomCacheCleanRequest
import org.mewx.wenku8.data.repository.LoginOutcome
import org.mewx.wenku8.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.data.repository.ParsedChapterToc
import org.mewx.wenku8.data.repository.ParsedVolumeToc
import org.mewx.wenku8.data.repository.StorageAndCacheRepository
import org.mewx.wenku8.data.repository.StoredAccountCredentials
import org.mewx.wenku8.data.repository.AccountSessionState
import org.mewx.wenku8.data.remote.diagnostics.AutoRoutingModeState
import org.mewx.wenku8.data.repository.UserSessionRepository
import org.mewx.wenku8.ui.navigation.AdaptiveMainScaffold
import org.mewx.wenku8.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.ui.navigation.MainScaffoldTestTags
import org.mewx.wenku8.ui.navigation.TopLevelDestination
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class ProfileAndBackupTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @get:Rule
    val composeTestRule = createComposeRule()

    private fun buildFileBackedDb(name: String): Wenku8Database {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(tempFolder.newFolder("db_$name"), "$name.db")
        return Room.databaseBuilder(context, Wenku8Database::class.java, dbFile.absolutePath)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun fontMagicSignatureAcceptsTrueTypeOpenTypeAndRejectsArbitraryFiles() {
        val ttfHeader = byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x12)
        val otfHeader = "OTTO_extra".toByteArray(StandardCharsets.US_ASCII)
        val ttcHeader = "ttcf_extra".toByteArray(StandardCharsets.US_ASCII)
        val pngHeader = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val shortHeader = byteArrayOf(0x00, 0x01)

        assertTrue(StorageAndCacheRepository.hasValidFontSignature(ttfHeader, ttfHeader.size))
        assertTrue(StorageAndCacheRepository.hasValidFontSignature(otfHeader, otfHeader.size))
        assertTrue(StorageAndCacheRepository.hasValidFontSignature(ttcHeader, ttcHeader.size))
        assertFalse(StorageAndCacheRepository.hasValidFontSignature(pngHeader, pngHeader.size))
        assertFalse(StorageAndCacheRepository.hasValidFontSignature(shortHeader, shortHeader.size))
    }

    @Test
    fun fastAndDeepCacheCleanupPreserveBookshelfCoversAndReferencedIllustrations() =
        runTest(UnconfinedTestDispatcher()) {
            val db = buildFileBackedDb("cache_clean")
            try {
                val cacheDir = tempFolder.newFolder("cache_root")
                val filesDir = tempFolder.newFolder("files_root")

                // Temp HTTP cache + fresh (<24h) diagnostic report
                val staleHttpCache = File(cacheDir, "http_tmp.bin").apply { writeBytes(ByteArray(512)) }
                val diagDir = File(cacheDir, "diagnostics").apply { mkdirs() }
                val freshDiagReport = File(diagDir, "wenku8-diag-recent.json").apply {
                    writeText("{\"schema\":\"ok\"}")
                }

                // Cover files: 1306 is in bookshelf, 2002 is transient non-bookshelf
                val imgsDir = File(filesDir, "imgs").apply { mkdirs() }
                val shelfCover = File(imgsDir, "1306.jpg").apply { writeBytes(ByteArray(1024)) }
                val transientCover = File(imgsDir, "2002.jpg").apply { writeBytes(ByteArray(2048)) }

                db.novelDao().upsertNovel(
                    NovelEntity(aid = 1306, title = "奇诺之旅", inBookshelf = true),
                )
                db.novelDao().upsertNovel(
                    NovelEntity(aid = 2002, title = "路过作品", inBookshelf = false),
                )

                // Offline chapter referencing one valid illustration + one corrupt illustration, plus an orphan file
                val liveIllustUrl = "https://pic.wenku8.com/pictures/1/1306/42001/55001.jpg"
                val corruptLiveIllustUrl = "https://pic.wenku8.com/pictures/1/1306/42001/55002.jpg"
                val liveLegacyName = WenkuXmlParsers.generateLegacyImageFileName(liveIllustUrl)
                val corruptLiveLegacyName = WenkuXmlParsers.generateLegacyImageFileName(corruptLiveIllustUrl)
                val savesImgsDir = File(File(filesDir, "saves"), "imgs").apply { mkdirs() }
                val validJpegPayload = ByteArray(4096).also { buf ->
                    buf[0] = 0xFF.toByte()
                    buf[1] = 0xD8.toByte()
                    buf[2] = 0xFF.toByte()
                    buf[buf.lastIndex - 1] = 0xFF.toByte()
                    buf[buf.lastIndex] = 0xD9.toByte()
                }
                val liveIllustFile = File(savesImgsDir, liveLegacyName).apply {
                    writeBytes(validJpegPayload)
                }
                val corruptLiveIllustFile = File(savesImgsDir, corruptLiveLegacyName).apply {
                    writeText("<html>Corrupt cached response</html>")
                }
                val orphanIllustFile = File(savesImgsDir, "orphan_unreferenced.jpg").apply {
                    writeBytes(validJpegPayload)
                }

                db.chapterContentDao().upsertChapterContent(
                    ChapterContentEntity(
                        cid = 42001,
                        aid = 1306,
                        contentLang = "SC",
                        contentText = "正文插图测试",
                        byteLength = 18,
                        imageUrlsJson = "[\"$liveIllustUrl\",\"$corruptLiveIllustUrl\"]",
                        imageCount = 2,
                        sha256Checksum = "abc",
                        downloadedAtMs = 1000L,
                    ),
                )

                val repo = StorageAndCacheRepository(
                    database = db,
                    cacheRootProvider = { cacheDir },
                    assetRootsProvider = { listOf(filesDir) },
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

                // 1. Fast Clean deletes temp HTTP + transient cover + transient novel row, keeps shelf cover & orphan illust
                val fastSummary = repo.runFastCacheClean()
                assertFalse(staleHttpCache.exists())
                assertTrue("Fresh <24h diagnostic export must survive fast clean", freshDiagReport.exists())
                assertTrue(shelfCover.exists())
                assertFalse(transientCover.exists())
                assertTrue("Fast clean must not touch saves/imgs/", orphanIllustFile.exists())
                assertEquals(1, fastSummary.prunedTransientNovelRows)
                assertNull(db.novelDao().getNovelByAid(2002))

                // 2. Deep Orphan Clean deletes BOTH unreferenced orphan files AND corrupt referenced files
                //    while preserving valid referenced illustrations!
                val progressUpdates = mutableListOf<Int>()
                val deepSummary = repo.runDeepOrphanCacheClean { progress ->
                    progressUpdates.add(progress.scannedFiles)
                }
                assertTrue(liveIllustFile.exists())
                assertFalse(orphanIllustFile.exists())
                assertFalse("Corrupt referenced illustration must be removed during deep clean", corruptLiveIllustFile.exists())
                assertEquals(2, deepSummary.deletedFileCount)
                assertTrue(progressUpdates.isNotEmpty())
            } finally {
                db.close()
            }
        }

    @Test
    fun backupArchiveLightAndFullSmartMergePreserveCredentialsDeduplicateAndRejectZipSlipOrCorruption() =
        runTest(UnconfinedTestDispatcher()) {
            val sourceDb = buildFileBackedDb("backup_source")
            val destDb = buildFileBackedDb("backup_dest")
            try {
                val sourceFilesDir = tempFolder.newFolder("source_files")
                val sourceCacheDir = tempFolder.newFolder("source_cache")
                val sourcePrefsFile = File(tempFolder.newFolder("source_prefs"), "settings.preferences_pb")
                val sourcePrefsRepo = UserPreferencesRepository(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { sourcePrefsFile },
                )

                // Populate sensitive files that MUST NEVER leak into .wk8backup.zip
                val sourceSavesDir = File(sourceFilesDir, "saves").apply { mkdirs() }
                File(sourceSavesDir, "cert.wk8").writeBytes(byteArrayOf(1, 2, 3, 4))
                File(sourceSavesDir, "avatar.jpg").writeBytes(byteArrayOf(5, 6, 7))
                File(sourceFilesDir, "network_routing_local.preferences_pb").writeText("secret-route")

                // Populate custom font + cover + illustration
                val sourceCustomDir = File(sourceFilesDir, "custom").apply { mkdirs() }
                val customFontFile = File(sourceCustomDir, CustomAssetSlot.READER_FONT.fileName).apply {
                    writeBytes(byteArrayOf(0x00, 0x01, 0x00, 0x00, 0x42))
                }
                File(File(sourceFilesDir, "imgs").apply { mkdirs() }, "1306.jpg")
                    .writeBytes(byteArrayOf(10, 20, 30))
                File(File(sourceSavesDir, "imgs").apply { mkdirs() }, "illust_1306.jpg")
                    .writeBytes(byteArrayOf(40, 50, 60))

                sourcePrefsRepo.setAppLanguage(Wenku8API.AppLanguage.TC)
                sourcePrefsRepo.setDefaultHomeDestination(DefaultHomeDestination.BOOKSHELF)
                sourcePrefsRepo.setReaderTypography(
                    fontSizeSp = 24,
                    lineDistanceDp = 20,
                    paragraphDistanceDp = 26,
                    pageEdgeDistanceDp = 14,
                )
                sourcePrefsRepo.setReaderCustomFontPath(customFontFile.absolutePath)
                sourcePrefsRepo.setReaderColumnMode(org.mewx.wenku8.reader.model.ReaderColumnMode.DUAL)

                // Populate source database (including a >300,000-char omnibus chapter to exercise 256k SUBSTR slicing)
                sourceDb.novelDao().upsertNovel(
                    NovelEntity(aid = 1306, title = "奇诺之旅", inBookshelf = true, tocVersion = 2L, metaSyncedAtMs = 5000L),
                )
                sourceDb.novelDao().upsertNovel(
                    NovelEntity(aid = 1587, title = "文学少女", inBookshelf = true, tocVersion = 1L, metaSyncedAtMs = 4000L),
                )
                sourceDb.tocDao().upsertVolumes(
                    listOf(VolumeEntity(vid = 41000, aid = 1306, volumeOrder = 0, volumeTitle = "第一卷")),
                )
                sourceDb.tocDao().upsertChapters(
                    listOf(ChapterEntity(cid = 41001, vid = 41000, aid = 1306, chapterOrder = 0, chapterTitle = "序章", isIllustrationChapter = false)),
                )
                sourceDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(
                        aid = 1306,
                        vid = 41000,
                        cid = 41001,
                        volumeTitle = "第一卷",
                        chapterTitle = "序章",
                        paragraphIndex = 18,
                        charOffset = 4,
                        legacyScrollPx = 0,
                        progressPermille = 450,
                        updatedAtMs = 9000L,
                    ),
                )
                sourceDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(
                        aid = 1587,
                        vid = 51000,
                        cid = 51001,
                        volumeTitle = "第一卷",
                        chapterTitle = "第一章",
                        paragraphIndex = 3,
                        charOffset = 0,
                        legacyScrollPx = 0,
                        progressPermille = 100,
                        updatedAtMs = 2000L,
                    ),
                )
                sourceDb.bookmarkNoteDao().upsertBookmarkNote(
                    BookmarkNoteEntity(
                        aid = 1306,
                        vid = 41000,
                        cid = 41001,
                        chapterTitle = "序章",
                        startParagraph = 5,
                        startChar = 0,
                        endParagraph = 5,
                        endChar = 12,
                        snippetText = "世界并不美丽，却也因此美丽。",
                        noteMarkdown = "经典台词",
                        createdAtMs = 8000L,
                    ),
                )
                sourceDb.readingSessionDao().insertSession(
                    ReadingSessionEntity(
                        aid = 1306,
                        cid = 41001,
                        dateEpochDay = 20000L,
                        durationSeconds = 1800,
                        charactersRead = 9500,
                    ),
                )

                val largeBody = "奇诺与艾尔梅斯的旅途。".repeat(30_000) // 300,000 chars -> 2 SUBSTR slices
                sourceDb.chapterContentDao().upsertChapterContent(
                    ChapterContentEntity(
                        cid = 41001,
                        aid = 1306,
                        contentLang = "TC",
                        contentText = largeBody,
                        byteLength = largeBody.toByteArray().size,
                        imageUrlsJson = "[]",
                        imageCount = 0,
                        sha256Checksum = "sha-large",
                        downloadedAtMs = 7000L,
                    ),
                )

                val sourceBackupRepo = BackupArchiveRepository(
                    database = sourceDb,
                    userPreferencesRepository = sourcePrefsRepo,
                    cacheRootProvider = { sourceCacheDir },
                    assetRootsProvider = { listOf(sourceFilesDir) },
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

                // 1. Export LIGHT_SAVE and inspect ZIP entries
                val lightZipBytes = ByteArrayOutputStream().also { out ->
                    val res = sourceBackupRepo.exportBackupArchive(BackupArchiveMode.LIGHT_SAVE, out)
                    assertTrue(res.isSuccess)
                    assertEquals(0, res.getOrThrow().includedImageFiles)
                }.toByteArray()

                val lightEntries = listZipEntryNames(lightZipBytes)
                assertTrue("manifest.json" in lightEntries)
                assertTrue("settings.json" in lightEntries)
                assertTrue("wenku8_v2_snapshot.db" in lightEntries)
                assertTrue("custom/reader_font" in lightEntries)
                assertFalse("LIGHT_SAVE must omit imgs/", lightEntries.any { it.startsWith("imgs/") })
                assertFalse("cert.wk8 must NEVER be exported", lightEntries.any { it.contains("cert.wk8") })
                assertFalse("avatar.jpg must NEVER be exported", lightEntries.any { it.contains("avatar.jpg") })
                assertFalse("network_routing_local must NEVER be exported", lightEntries.any { it.contains("network_routing_local") })

                // 2. Export FULL_ARCHIVE
                val fullZipBytes = ByteArrayOutputStream().also { out ->
                    val res = sourceBackupRepo.exportBackupArchive(BackupArchiveMode.FULL_ARCHIVE, out)
                    assertTrue(res.isSuccess)
                    assertEquals(2, res.getOrThrow().includedImageFiles)
                }.toByteArray()

                val fullEntries = listZipEntryNames(fullZipBytes)
                assertTrue("imgs/1306.jpg" in fullEntries)
                assertTrue("saves/imgs/illust_1306.jpg" in fullEntries)
                assertFalse(fullEntries.any { it.contains("cert.wk8") })

                // 3. Prepare destination DB with pre-existing data to verify non-destructive Smart-Merge
                val destFilesDir = tempFolder.newFolder("dest_files")
                val destCacheDir = tempFolder.newFolder("dest_cache")
                val destPrefsFile = File(tempFolder.newFolder("dest_prefs"), "settings.preferences_pb")
                val destPrefsRepo = UserPreferencesRepository(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { destPrefsFile },
                )

                // Local-only shelf book 2999 + newer local progress on 1587 (updatedAtMs = 99999 > 2000)
                // + older local progress on 1306 (updatedAtMs = 1000 < 9000)
                destDb.novelDao().upsertNovel(
                    NovelEntity(aid = 2999, title = "本地独有藏书", inBookshelf = true),
                )
                destDb.novelDao().upsertNovel(
                    NovelEntity(aid = 1306, title = "奇诺之旅", inBookshelf = false, metaSyncedAtMs = 1000L),
                )
                destDb.novelDao().upsertNovel(
                    NovelEntity(aid = 1587, title = "文学少女", inBookshelf = false, metaSyncedAtMs = 1000L),
                )
                destDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(
                        aid = 1587,
                        vid = 51000,
                        cid = 51099,
                        volumeTitle = "第八卷",
                        chapterTitle = "终章",
                        paragraphIndex = 42,
                        charOffset = 9,
                        legacyScrollPx = 0,
                        progressPermille = 980,
                        updatedAtMs = 99_999L,
                    ),
                )
                destDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(
                        aid = 1306,
                        vid = 41000,
                        cid = 41001,
                        volumeTitle = "第一卷",
                        chapterTitle = "序章",
                        paragraphIndex = 1,
                        charOffset = 0,
                        legacyScrollPx = 0,
                        progressPermille = 20,
                        updatedAtMs = 1_000L,
                    ),
                )

                val destBackupRepo = BackupArchiveRepository(
                    database = destDb,
                    userPreferencesRepository = destPrefsRepo,
                    cacheRootProvider = { destCacheDir },
                    assetRootsProvider = { listOf(destFilesDir) },
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

                // 3a. Verify Zip-Slip archive is rejected safely
                val zipSlipBytes = ByteArrayOutputStream().also { out ->
                    ZipOutputStream(out).use { zos ->
                        zos.putNextEntry(ZipEntry("../../escaped.txt"))
                        zos.write("malicious".toByteArray())
                        zos.closeEntry()
                    }
                }.toByteArray()
                val zipSlipResult = destBackupRepo.importBackupArchiveSmartMerge(ByteArrayInputStream(zipSlipBytes))
                assertTrue("Zip-Slip archive must fail", zipSlipResult.isFailure)

                // 3b. Verify corrupted SQLite archive fails PRAGMA integrity_check safely
                val corruptDbZip = ByteArrayOutputStream().also { out ->
                    ZipOutputStream(out).use { zos ->
                        zos.putNextEntry(ZipEntry("manifest.json"))
                        zos.write("{\"schema\":\"wenku8-v2-backup-v1\",\"mode\":\"light\"}".toByteArray())
                        zos.closeEntry()
                        zos.putNextEntry(ZipEntry("wenku8_v2_snapshot.db"))
                        zos.write("NOT_A_VALID_SQLITE_HEADER_BYTES".toByteArray())
                        zos.closeEntry()
                    }
                }.toByteArray()
                val corruptResult = destBackupRepo.importBackupArchiveSmartMerge(ByteArrayInputStream(corruptDbZip))
                assertTrue("Corrupted SQLite snapshot must be rejected", corruptResult.isFailure)

                // 3c. Import FULL_ARCHIVE and verify Smart-Merge invariants
                val firstImport = destBackupRepo.importBackupArchiveSmartMerge(ByteArrayInputStream(fullZipBytes))
                assertTrue("Full archive smart merge must succeed: ${firstImport.exceptionOrNull()}", firstImport.isSuccess)
                val summary1 = firstImport.getOrThrow()
                assertEquals(2, summary1.mergedShelfNovels)
                assertEquals(1, summary1.mergedReadingProgress) // 1306 updated, 1587 kept local newer
                assertEquals(1, summary1.mergedBookmarksNotes)
                assertEquals(1, summary1.mergedOfflineChapters)
                assertEquals(2, summary1.restoredImageFiles)

                // Shelf union: 1306, 1587, and local-only 2999 are ALL in_bookshelf = true!
                val shelfIds = destDb.novelDao().getBookshelfNovelIds().toSet()
                assertEquals(setOf(1306, 1587, 2999), shelfIds)

                // MAX(updated_at_ms) reading progress:
                assertEquals(18, destDb.readingProgressDao().getProgressForNovel(1306)?.paragraphIndex)
                assertEquals(42, destDb.readingProgressDao().getProgressForNovel(1587)?.paragraphIndex)

                // Large 300k-char chapter restored intact across SUBSTR slices:
                assertEquals(largeBody, destDb.chapterContentDao().loadChapterTextSafely(41001))

                // Portable settings & re-anchored custom font path restored:
                val restoredPrefs = destPrefsRepo.preferencesFlow.first()
                assertEquals(Wenku8API.AppLanguage.TC, restoredPrefs.appLanguage)
                assertEquals(DefaultHomeDestination.BOOKSHELF, restoredPrefs.defaultHomeDestination)
                assertEquals(24, restoredPrefs.readerFontSizeSp)
                assertEquals(
                    org.mewx.wenku8.reader.model.ReaderColumnMode.DUAL,
                    restoredPrefs.readerColumnMode,
                )
                assertTrue(restoredPrefs.readerCustomFontPath.endsWith("/custom/reader_font"))
                assertTrue(File(restoredPrefs.readerCustomFontPath).isFile)

                // 3d. Re-importing the exact same archive is 100% idempotent (0 duplicate bookmarks/sessions)
                val secondImport = destBackupRepo.importBackupArchiveSmartMerge(ByteArrayInputStream(fullZipBytes))
                assertTrue(secondImport.isSuccess)
                assertEquals(0, secondImport.getOrThrow().mergedBookmarksNotes)
                assertEquals(1, destDb.bookmarkNoteDao().getAllBookmarkNotes().size)
                assertEquals(1, destDb.readingSessionDao().getAllSessions().size)
            } finally {
                sourceDb.close()
                destDb.close()
            }
        }

    @Test
    fun profileAndStudioScreenRendersAllCardsTriggersFastCleanLicenseAndAboutEasterEgg() {
        val db = buildFileBackedDb("ui_profile")
        try {
            val cacheDir = tempFolder.newFolder("ui_cache").apply {
                File(this, "stale.tmp").writeBytes(ByteArray(256))
            }
            val filesDir = tempFolder.newFolder("ui_files")
            val storageRepo = StorageAndCacheRepository(
                database = db,
                cacheRootProvider = { cacheDir },
                assetRootsProvider = { listOf(filesDir) },
            )

            var prefsState by mutableStateOf(
                Wenku8UserPreferences(
                    readerFontSizeSp = 22,
                    readerLineDistanceDp = 18,
                ),
            )
            var diagnosticsOpened = 0

            composeTestRule.setContent {
                Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                    Wenku8ExpressiveTheme(themeMode = prefsState.themeMode) {
                        ProfileAndStudioScreen(
                            preferences = prefsState,
                            onThemeModeSelected = { prefsState = prefsState.copy(themeMode = it) },
                            onDefaultHomeSelected = { prefsState = prefsState.copy(defaultHomeDestination = it) },
                            totalReadingSeconds = 7200L, // 120 minutes
                            totalCharactersRead = 25_000L, // 2.5w
                            totalBookmarkNoteCount = 3,
                            onAutoOptimizeConnectionChanged = { enabled ->
                                prefsState = prefsState.copy(autoOptimizeConnectionEnabled = enabled)
                            },
                            onOpenNetworkDiagnostics = { diagnosticsOpened++ },
                            onAppLanguageSelected = { prefsState = prefsState.copy(appLanguage = it) },
                            onEinkModeChanged = { enabled ->
                                prefsState = prefsState.copy(einkModeEnabled = enabled)
                            },
                            onReaderTypographyChanged = { size, line, para, edge ->
                                prefsState = prefsState.copy(
                                    readerFontSizeSp = size,
                                    readerLineDistanceDp = line,
                                    readerParagraphDistanceDp = para,
                                    readerPageEdgeDistanceDp = edge,
                                )
                            },
                            storageAndCacheRepository = storageRepo,
                            modifier = Modifier.composeTestTagForLazyList(),
                        )
                    }
                }
            }

            // 0. Removed header card & standalone dynamic-color / header-wallpaper options stay absent
            composeTestRule.onNodeWithText("我的与工作室").assertDoesNotExist()
            composeTestRule.onNodeWithText("个人主页头图壁纸").assertDoesNotExist()
            composeTestRule.onNodeWithText("系统壁纸动态取色 (Material You)").assertDoesNotExist()

            // 1. Reading footprint summary & MD3e segmented group headers visible on Hub
            composeTestRule.onNodeWithText("120 分钟").assertIsDisplayed()
            composeTestRule.onNodeWithText("2.5w").assertIsDisplayed()
            composeTestRule.onNodeWithText("偏好与阅读").assertIsDisplayed()
            composeTestRule.onNodeWithText("22 sp · 系统无衬线 · 音量键翻页开").assertIsDisplayed()

            // 2. Network master-switch split row: body opens diagnostics, trailing switch toggles auto-optimize
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.ENTRY_NETWORK))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.OPEN_DIAGNOSTICS_BUTTON).performClick()
            assertEquals(1, diagnosticsOpened)
            val initialAutoOpt = prefsState.autoOptimizeConnectionEnabled
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.AUTO_ROUTING_SWITCH).performClick()
            composeTestRule.waitForIdle()
            assertEquals(!initialAutoOpt, prefsState.autoOptimizeConnectionEnabled)

            // 3. Appearance subscreen: language switch updates preference and live Hub summary
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.ENTRY_APPEARANCE))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ENTRY_APPEARANCE).performClick()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_ROOT).assertIsDisplayed()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LANG_CHIP_TC).performClick()
            assertEquals(Wenku8API.AppLanguage.TC, prefsState.appLanguage)
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_BACK_BUTTON).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.ENTRY_APPEARANCE))
            composeTestRule.onNodeWithText("繁體中文", substring = true).assertIsDisplayed()

            // 4. Reader typography subscreen: pinned live multi-paragraph preview & typography reset
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.ENTRY_READER_TYPOGRAPHY))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ENTRY_READER_TYPOGRAPHY).performClick()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.TYPOGRAPHY_PREVIEW_BOX).assertIsDisplayed()
            prefsState = prefsState.copy(readerParagraphDistanceDp = 0)
            composeTestRule.waitForIdle()
            val zeroParaHeight = composeTestRule
                .onNodeWithTag(ProfileStudioTestTags.TYPOGRAPHY_PREVIEW_BOX)
                .fetchSemanticsNode()
                .size
                .height
            prefsState = prefsState.copy(readerParagraphDistanceDp = 40)
            composeTestRule.waitForIdle()
            val wideParaHeight = composeTestRule
                .onNodeWithTag(ProfileStudioTestTags.TYPOGRAPHY_PREVIEW_BOX)
                .fetchSemanticsNode()
                .size
                .height
            assertTrue(
                "Multi-paragraph preview height must grow when paragraph spacing increases ($wideParaHeight > $zeroParaHeight)",
                wideParaHeight > zeroParaHeight,
            )

            composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.RESET_TYPOGRAPHY_BUTTON))
            composeTestRule.onNodeWithText("字号大小：22 sp").assertIsDisplayed()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.RESET_TYPOGRAPHY_BUTTON).performClick()
            composeTestRule.waitForIdle()
            assertEquals(Wenku8UserPreferences.DEFAULT_READER_FONT_SIZE_SP, prefsState.readerFontSizeSp)
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_BACK_BUTTON).performClick()
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.ENTRY_READER_TYPOGRAPHY))
            composeTestRule.onNodeWithText(
                "${Wenku8UserPreferences.DEFAULT_READER_FONT_SIZE_SP} sp",
                substring = true,
            ).assertIsDisplayed()

            // 5. Storage subscreen: Fast Clean executes and renders completion banner
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.ENTRY_STORAGE))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ENTRY_STORAGE).performClick()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.FAST_CLEAN_BUTTON))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.FAST_CLEAN_BUTTON).performClick()
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                !File(cacheDir, "stale.tmp").exists() &&
                    composeTestRule
                        .onAllNodesWithTag(ProfileStudioTestTags.STORAGE_STATUS_BANNER)
                        .fetchSemanticsNodes()
                        .isNotEmpty()
            }
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.STORAGE_STATUS_BANNER))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.STORAGE_STATUS_BANNER).assertIsDisplayed()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_BACK_BUTTON).performClick()
            composeTestRule.waitForIdle()

            // 6. About & Feedback subscreen: Open-Source License dialog + About dialog with 5-tap Easter egg
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.ENTRY_ABOUT))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ENTRY_ABOUT).performClick()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.OPEN_LICENSE_BUTTON))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.OPEN_LICENSE_BUTTON).performClick()
            composeTestRule.onNodeWithText("开源许可与版权声明").assertIsDisplayed()
            composeTestRule.onNodeWithText("https://git.io/v8Ck3", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("Jetpack Compose", substring = true).assertIsDisplayed()
            composeTestRule.onNodeWithText("关闭").performClick()

            composeTestRule.onNodeWithTag(ProfileStudioTestTags.OPEN_ABOUT_BUTTON).performClick()
            composeTestRule.onNodeWithText(
                "版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            ).assertIsDisplayed()
            val expectedChannel = if (BuildConfig.DISTRIBUTION_CHANNEL == "playstore") {
                "Google Play"
            } else {
                "APK直装版"
            }
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ABOUT_CHANNEL_TEXT).assertIsDisplayed()
            composeTestRule.onNodeWithText("发行渠道：$expectedChannel").assertIsDisplayed()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ABOUT_HOMEPAGE_LINK).assertIsDisplayed()
            composeTestRule.onNodeWithText("QQ交流群: 2166068158")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ABOUT_EASTER_EGG_TEXT).assertDoesNotExist()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ABOUT_VERSION_TAP_TARGET).performScrollTo()
            repeat(5) {
                composeTestRule.onNodeWithTag(ProfileStudioTestTags.ABOUT_VERSION_TAP_TARGET).performClick()
            }
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ABOUT_EASTER_EGG_TEXT).assertIsDisplayed()
            composeTestRule.onNodeWithText("MewX <https://mewx.org>")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("Yanix <https://github.com/SeraphRoy>")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("Jack Liu <https://github.com/aaassseee>")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("ZERO (原界面设计) <78283358@qq.com>")
                .performScrollTo()
                .assertIsDisplayed()
            composeTestRule.onNodeWithText("妻兔姬 (原反馈协助) <1270263248@qq.com>")
                .performScrollTo()
                .assertIsDisplayed()
        } finally {
            db.close()
        }
    }

    @Test
    fun backupImportConflictMatrix_preservesCredentialsSessionLocalShelfTombstoneResetAndProgress() =
        runTest(UnconfinedTestDispatcher()) {
            val sourceDb = buildFileBackedDb("matrix_source")
            val destDb = buildFileBackedDb("matrix_dest")
            try {
                val sourceFilesDir = tempFolder.newFolder("matrix_src_files")
                val sourceCacheDir = tempFolder.newFolder("matrix_src_cache")
                val sourcePrefsFile = File(tempFolder.newFolder("matrix_src_prefs"), "settings.preferences_pb")
                val sourcePrefsRepo = UserPreferencesRepository(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { sourcePrefsFile },
                )

                val destFilesDir = tempFolder.newFolder("matrix_dest_files")
                val destCacheDir = tempFolder.newFolder("matrix_dest_cache")
                val destPrefsFile = File(tempFolder.newFolder("matrix_dest_prefs"), "settings.preferences_pb")
                val destPrefsRepo = UserPreferencesRepository(
                    PreferenceDataStoreFactory.create(scope = backgroundScope) { destPrefsFile },
                )

                // 1. Source device is logged in as "alice" with cert.wk8, account_uname.wk8, avatar.jpg, and ledger
                val sourceSaves = File(sourceFilesDir, "saves").apply { mkdirs() }
                File(sourceSaves, "cert.wk8").writeText("alice:alice_secret")
                File(sourceSaves, "account_uname.wk8").writeText("alice")
                File(sourceSaves, "avatar.jpg").writeBytes(byteArrayOf(1, 2, 3))
                sourceDb.cloudBookcaseLedgerDao().upsertEntry(
                    CloudBookcaseLedgerEntity("alice", 9001, CloudBookcaseLedgerEntity.STATE_SYNCED, 1_000L),
                )

                sourcePrefsRepo.setBookshelfViewMode(BookshelfViewMode.DETAIL_LIST)
                sourcePrefsRepo.setBookshelfSortMode(BookshelfSortMode.RECENT_READ)

                // Populate Source DB novels + progress + cover + chapter:
                // - 9001: shared shelf book (source category "备份分类", older progress 20_000L)
                // - 9002: on shelf in backup (restores a previously deleted book on destination, newer progress 60_000L)
                // - 9003: equal-timestamp progress 30_000L
                // - 9004: transient non-shelf book in backup (inBookshelf = false, backup-only progress 40_000L)
                // - 9005: unhydrated "AID 9005" stub in backup (metaSyncedAtMs = 0) -> must NOT overwrite real local title!
                sourceDb.novelDao().upsertNovels(
                    listOf(
                        NovelEntity(
                            aid = 9001,
                            title = "共享书架作品",
                            author = "作者甲",
                            inBookshelf = true,
                            shelfCategory = "备份分类",
                            shelfSortOrder = 1,
                            cloudSynced = true,
                            metaSyncedAtMs = 5_000L,
                        ),
                        NovelEntity(
                            aid = 9002,
                            title = "备份恢复书架作品",
                            author = "作者乙",
                            inBookshelf = true,
                            shelfCategory = "备份恢复分类",
                            shelfSortOrder = 1, // intentionally collides with local 9001 sortOrder=1!
                            cloudSynced = true,
                            metaSyncedAtMs = 6_000L,
                        ),
                        NovelEntity(
                            aid = 9003,
                            title = "本地书架且时间戳相同作品",
                            author = "作者丙",
                            inBookshelf = false, // non-shelf in backup, shelf locally -> union keeps shelf=true!
                            metaSyncedAtMs = 3_000L,
                        ),
                        NovelEntity(
                            aid = 9004,
                            title = "备份路过非书架作品",
                            author = "作者丁",
                            inBookshelf = false,
                            metaSyncedAtMs = 4_000L,
                        ),
                        NovelEntity(
                            aid = 9005,
                            title = "AID 9005",
                            author = "",
                            inBookshelf = false,
                            metaSyncedAtMs = 0L,
                        ),
                    ),
                )
                sourceDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(aid = 9001, vid = 10, cid = 101, progressPermille = 200, updatedAtMs = 20_000L),
                )
                sourceDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(aid = 9002, vid = 20, cid = 202, progressPermille = 720, updatedAtMs = 60_000L),
                )
                sourceDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(aid = 9003, vid = 30, cid = 301, progressPermille = 111, updatedAtMs = 30_000L),
                )
                sourceDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(aid = 9004, vid = 40, cid = 401, progressPermille = 310, updatedAtMs = 40_000L),
                )
                File(File(sourceFilesDir, "imgs").apply { mkdirs() }, "9001.jpg")
                    .writeBytes(byteArrayOf(11, 11, 11))
                File(File(sourceFilesDir, "imgs"), "9002.jpg")
                    .writeBytes(byteArrayOf(22, 22, 22))

                val sourceBackupRepo = BackupArchiveRepository(
                    database = sourceDb,
                    userPreferencesRepository = sourcePrefsRepo,
                    cacheRootProvider = { sourceCacheDir },
                    assetRootsProvider = { listOf(sourceFilesDir) },
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
                val cleanFullZip = ByteArrayOutputStream().also { out ->
                    assertTrue(sourceBackupRepo.exportBackupArchive(BackupArchiveMode.FULL_ARCHIVE, out).isSuccess)
                }.toByteArray()
                assertFalse(listZipEntryNames(cleanFullZip).any { it.contains("account_uname.wk8") })

                // Inject malicious/tampered credential files into a copy of the archive to verify import defense-in-depth!
                val tamperedZipBytes = injectZipEntries(
                    baseZip = cleanFullZip,
                    extraEntries = mapOf(
                        "saves/cert.wk8" to "attacker:stolen_pw".toByteArray(),
                        "saves/account_uname.wk8" to "attacker".toByteArray(),
                        "saves/avatar.jpg" to byteArrayOf(99, 99, 99),
                        "network_routing_local.preferences_pb" to "evil_proxy".toByteArray(),
                    ),
                )

                // 2. Destination device is actively signed in as "bob"!
                val bobTransport = object : WenkuTransport {
                    override val isEndpointConfigured: Boolean = true
                    override suspend fun postSealedAction(
                        rawActionQuery: String,
                        isIdempotentReadOnly: Boolean,
                    ): org.mewx.wenku8.data.remote.transport.RelayPostResult {
                        val payload = when (rawActionQuery) {
                            "action=userinfo" -> """
                                <?xml version="1.0" encoding="utf-8"?>
                                <metadata>
                                    <item name="uid">202</item>
                                    <item name="uname"><![CDATA[bob]]></item>
                                    <item name="nickname"><![CDATA[Bob读者]]></item>
                                    <item name="score">88</item>
                                    <item name="experience">120</item>
                                    <item name="rank">正式会员</item>
                                </metadata>
                            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                            else -> "1".toByteArray(StandardCharsets.UTF_8)
                        }
                        return org.mewx.wenku8.data.remote.transport.RelayPostResult.Success(payload)
                    }
                    override suspend fun getBytes(url: String): ByteArray? = byteArrayOf(7, 8, 9)
                    override fun clearSessionCookie() = Unit
                }
                val bobAuthGateway = object : AccountAuthGateway {
                    override val isAuthConfigured: Boolean = true
                    override val baseUrlOrEmpty: String = "https://relay.example.test/v2/api"
                    override val registerUrlOrEmpty: String = ""
                    override fun loginQuery(identifier: String, password: String): String = "action=login"
                    override fun logoutQuery(): String = "action=logout"
                    override fun userInfoQuery(): String = "action=userinfo"
                    override fun userSignQuery(): String = "action=sign"
                    override fun avatarUrlForUid(uid: Int): String = "https://img.example.test/bob.jpg"
                    override fun mapServerErrorCode(rawCode: Int): Wenku8Error.ErrorCode =
                        Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED
                    override fun decodeCertBlob(rawCertContent: String): StoredAccountCredentials? {
                        val parts = rawCertContent.split(":", limit = 2)
                        return if (parts.size == 2) StoredAccountCredentials(parts[0], parts[1]) else null
                    }
                    override fun encodeCertBlob(identifier: String, password: String): String = "$identifier:$password"
                    override fun syncInMemoryCredentials(identifier: String, password: String) = Unit
                    override fun clearInMemorySession() = Unit
                }
                val bobSessionRepo = UserSessionRepository(
                    transport = bobTransport,
                    assetRootsProvider = { listOf(destFilesDir) },
                    authGateway = bobAuthGateway,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
                assertTrue(bobSessionRepo.loginWithCredentials("bob", "bob_secret") is LoginOutcome.Success)
                assertTrue(bobSessionRepo.sessionState.value is AccountSessionState.SignedIn)

                // Pre-existing local cover for 9001 must NOT be overwritten by backup's 9001.jpg
                val destCover9001 = File(File(destFilesDir, "imgs").apply { mkdirs() }, "9001.jpg").apply {
                    writeBytes(byteArrayOf(77, 77, 77))
                }

                // Seed Destination DB:
                // - 9001: on Bob's shelf (category "本地精选", sortOrder 1, cloudSynced=true, STATE_SYNCED) + newer progress 50_000L
                // - 9002: previously deleted locally by Bob (inBookshelf=false, STATE_PENDING_DELETE tombstone!) + older progress 10_000L
                // - 9003: on Bob's shelf (sortOrder 2) while non-shelf in backup + equal-timestamp progress 30_000L (permille 555)
                // - 9005: local populated metadata ("冰菓" / "米泽穗信", metaSyncedAtMs = 0) + local-only progress 45_000L
                // - 9006: local-only shelf book completely absent from backup (sortOrder 3)
                destDb.novelDao().upsertNovels(
                    listOf(
                        NovelEntity(
                            aid = 9001,
                            title = "共享书架作品",
                            author = "作者甲",
                            inBookshelf = true,
                            shelfCategory = "本地精选",
                            shelfSortOrder = 1,
                            cloudSynced = true,
                            metaSyncedAtMs = 2_000L,
                        ),
                        NovelEntity(
                            aid = 9002,
                            title = "备份恢复书架作品",
                            author = "作者乙",
                            inBookshelf = false,
                            cloudSynced = false,
                            metaSyncedAtMs = 1_000L,
                        ),
                        NovelEntity(
                            aid = 9003,
                            title = "本地书架且时间戳相同作品",
                            author = "作者丙",
                            inBookshelf = true,
                            shelfCategory = "本地分类",
                            shelfSortOrder = 2,
                            cloudSynced = true,
                            metaSyncedAtMs = 3_000L,
                        ),
                        NovelEntity(
                            aid = 9005,
                            title = "冰菓",
                            author = "米泽穗信",
                            inBookshelf = false,
                            metaSyncedAtMs = 0L,
                        ),
                        NovelEntity(
                            aid = 9006,
                            title = "备份完全不存在的本地独有藏书",
                            author = "作者己",
                            inBookshelf = true,
                            shelfCategory = "独有分类",
                            shelfSortOrder = 3,
                            cloudSynced = true,
                            metaSyncedAtMs = 3_500L,
                        ),
                    ),
                )
                destDb.cloudBookcaseLedgerDao().upsertEntries(
                    listOf(
                        CloudBookcaseLedgerEntity("bob", 9001, CloudBookcaseLedgerEntity.STATE_SYNCED, 2_000L),
                        CloudBookcaseLedgerEntity("bob", 9002, CloudBookcaseLedgerEntity.STATE_PENDING_DELETE, 2_500L),
                        CloudBookcaseLedgerEntity("bob", 9003, CloudBookcaseLedgerEntity.STATE_SYNCED, 3_000L),
                        CloudBookcaseLedgerEntity("bob", 9006, CloudBookcaseLedgerEntity.STATE_SYNCED, 3_500L),
                    ),
                )
                destDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(aid = 9001, vid = 10, cid = 109, progressPermille = 880, updatedAtMs = 50_000L),
                )
                destDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(aid = 9002, vid = 20, cid = 201, progressPermille = 50, updatedAtMs = 10_000L),
                )
                destDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(aid = 9003, vid = 30, cid = 301, progressPermille = 555, updatedAtMs = 30_000L),
                )
                destDb.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(aid = 9005, vid = 50, cid = 501, progressPermille = 910, updatedAtMs = 45_000L),
                )

                val destBackupRepo = BackupArchiveRepository(
                    database = destDb,
                    userPreferencesRepository = destPrefsRepo,
                    cacheRootProvider = { destCacheDir },
                    assetRootsProvider = { listOf(destFilesDir) },
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )

                // 3. Import tampered archive onto Bob's device!
                val importRes = destBackupRepo.importBackupArchiveSmartMerge(ByteArrayInputStream(tamperedZipBytes))
                assertTrue("Import must succeed cleanly: ${importRes.exceptionOrNull()}", importRes.isSuccess)

                // A. Credentials & Active Login State: 100% untouched!
                val bobStateAfter = bobSessionRepo.sessionState.value
                assertTrue(bobStateAfter is AccountSessionState.SignedIn)
                assertEquals("bob", (bobStateAfter as AccountSessionState.SignedIn).profile.username)
                assertEquals("bob:bob_secret", File(destFilesDir, "saves/cert.wk8").readText())
                assertEquals("bob", File(destFilesDir, "saves/account_uname.wk8").readText())
                assertEquals(listOf<Byte>(7, 8, 9), File(destFilesDir, "saves/avatar.jpg").readBytes().toList())
                assertFalse(File(destFilesDir, "network_routing_local.preferences_pb").exists())
                assertTrue(destDb.cloudBookcaseLedgerDao().getEntriesForAccount("alice").isEmpty())

                // B. Bookshelf Membership, Categories, Sort Order & Stub Protection:
                val shelfMap = destDb.novelDao().getBookshelfNovelsSnapshot().associateBy { it.aid }
                assertEquals(setOf(9001, 9002, 9003, 9006), shelfMap.keys)

                // 9001 kept local category, local sortOrder=1, and cloudSynced=true!
                assertEquals("本地精选", shelfMap.getValue(9001).shelfCategory)
                assertEquals(1, shelfMap.getValue(9001).shelfSortOrder)
                assertTrue(shelfMap.getValue(9001).cloudSynced)

                // 9002 restored onto shelf with backup category, cloudSynced=false, non-colliding sortOrder (>3),
                // AND its STATE_PENDING_DELETE tombstone under "bob" was cleared so next sync pushes it up!
                assertEquals("备份恢复分类", shelfMap.getValue(9002).shelfCategory)
                assertFalse(shelfMap.getValue(9002).cloudSynced)
                assertTrue(shelfMap.getValue(9002).shelfSortOrder > 3)
                assertNull(destDb.cloudBookcaseLedgerDao().getEntry("bob", 9002))

                // 9004 remained non-bookshelf; 9005 kept real title "冰菓" & author "米泽穗信"!
                assertFalse(destDb.novelDao().getNovelByAid(9004)!!.inBookshelf)
                assertEquals("冰菓", destDb.novelDao().getNovelByAid(9005)?.title)
                assertEquals("米泽穗信", destDb.novelDao().getNovelByAid(9005)?.author)

                // Verify subsequent Cloud Bookcase Sync pushes restored 9002 up to Bob's cloud shelf!
                val pushedBySync = mutableListOf<Int>()
                val syncGateway = object : WenkuCatalogGateway {
                    override val isApiConfigured: Boolean = true
                    override fun resolveCoverUrl(aid: Int): String = ""
                    override suspend fun fetchNovelListPage(sortedBy: Wenku8API.NovelSortedBy, pageNumber: Int, appLanguage: Wenku8API.AppLanguage) =
                        WenkuApiResult.ApiNotConfigured
                    override suspend fun searchNovelsByField(query: String, searchField: OnlineSearchField, appLanguage: Wenku8API.AppLanguage) =
                        WenkuApiResult.ApiNotConfigured
                    override suspend fun fetchNovelShortInfo(aid: Int, appLanguage: Wenku8API.AppLanguage) =
                        WenkuApiResult.ApiNotConfigured
                    override suspend fun fetchNovelFullMeta(aid: Int, appLanguage: Wenku8API.AppLanguage) =
                        WenkuApiResult.Success(ParsedNovelFullMeta(aid = aid, title = "作品 $aid", lastUpdate = "2026-03-01"))
                    override suspend fun fetchNovelFullIntro(aid: Int, appLanguage: Wenku8API.AppLanguage) =
                        WenkuApiResult.Success("简介")
                    override suspend fun fetchNovelVolumeToc(aid: Int, appLanguage: Wenku8API.AppLanguage) =
                        WenkuApiResult.Success(listOf(ParsedVolumeToc(vid = 1, volumeTitle = "第一卷", chapters = listOf(ParsedChapterToc(cid = 2, chapterTitle = "第一话")))))
                    override suspend fun fetchChapterContent(aid: Int, cid: Int, appLanguage: Wenku8API.AppLanguage) =
                        WenkuApiResult.Success("正文")
                    override suspend fun fetchCloudBookshelf(appLanguage: Wenku8API.AppLanguage) =
                        WenkuApiResult.Success(
                            listOf(
                                ParsedCloudBookshelfEntry(aid = 9001, lastUpdateDate = "2026-03-01", title = "共享书架作品", latestChapterCid = 109),
                                ParsedCloudBookshelfEntry(aid = 9003, lastUpdateDate = "2026-03-01", title = "本地书架且时间戳相同作品", latestChapterCid = 301),
                                ParsedCloudBookshelfEntry(aid = 9006, lastUpdateDate = "2026-03-01", title = "备份完全不存在的本地独有藏书", latestChapterCid = 601),
                            ),
                        )
                    override suspend fun addToCloudBookshelf(aid: Int): WenkuApiResult<Unit> {
                        pushedBySync += aid
                        return WenkuApiResult.Success(Unit)
                    }
                }
                val bobShelfRepo = BookshelfRepository(
                    database = destDb,
                    catalogGateway = syncGateway,
                    novelTocSyncRepository = NovelTocSyncRepository(destDb),
                    userSessionRepository = bobSessionRepo,
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                )
                val syncAfterImport = bobShelfRepo.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success
                assertEquals(1, syncAfterImport.pushedToCloudCount)
                assertEquals(listOf(9002), pushedBySync)

                // C. Reading Progress Conflict Matrix (`MAX(updated_at_ms)`):
                assertEquals(880, destDb.readingProgressDao().getProgressForNovel(9001)?.progressPermille) // local newer kept
                assertEquals(720, destDb.readingProgressDao().getProgressForNovel(9002)?.progressPermille) // backup newer won
                assertEquals(555, destDb.readingProgressDao().getProgressForNovel(9003)?.progressPermille) // equal timestamp kept local
                assertEquals(310, destDb.readingProgressDao().getProgressForNovel(9004)?.progressPermille) // backup-only inserted
                assertEquals(910, destDb.readingProgressDao().getProgressForNovel(9005)?.progressPermille) // local-only preserved

                // D. Cover Non-Overwrite & Portable Bookshelf View/Sort Preferences:
                assertEquals(listOf<Byte>(77, 77, 77), destCover9001.readBytes().toList())
                assertEquals(listOf<Byte>(22, 22, 22), File(destFilesDir, "imgs/9002.jpg").readBytes().toList())

                val prefsAfter = destPrefsRepo.preferencesFlow.first()
                assertEquals(BookshelfViewMode.DETAIL_LIST, prefsAfter.bookshelfViewMode)
                assertEquals(BookshelfSortMode.RECENT_READ, prefsAfter.bookshelfSortMode)
            } finally {
                sourceDb.close()
                destDb.close()
            }
        }

    @Test
    fun profileFeedbackActionsLaunchGitHubIssuesAndVersionedEmailClient() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.clearNextStartedActivities()

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.SYSTEM) {
                    ProfileAndStudioScreen(
                        preferences = Wenku8UserPreferences(),
                        onThemeModeSelected = {},
                        onDefaultHomeSelected = {},
                        modifier = Modifier.composeTestTagForLazyList(),
                    )
                }
            }
        }

        // 0. Open About & Feedback subscreen from Tab 4 segmented list
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
            .performScrollToNode(hasTestTag(ProfileStudioTestTags.ENTRY_ABOUT))
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.ENTRY_ABOUT).performClick()
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_ROOT).assertIsDisplayed()

        // 1. GitHub Issues button opens https://github.com/MewX/light-novel-library_Wenku8_Android/issues
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
            .performScrollToNode(hasTestTag(ProfileStudioTestTags.REPORT_BUG_GITHUB_BUTTON))
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.REPORT_BUG_GITHUB_BUTTON)
            .assertIsDisplayed()
            .performClick()

        val githubIntent = shadowApp.nextStartedActivity
        assertNotNull("Expected browser intent for GitHub Issues", githubIntent)
        assertEquals(Intent.ACTION_VIEW, githubIntent.action)
        assertEquals(GITHUB_ISSUES_URL, githubIntent.dataString)

        // 2. Email feedback button opens default email client for wenku8@mewx.org with version info
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
            .performScrollToNode(hasTestTag(ProfileStudioTestTags.FEEDBACK_EMAIL_BUTTON))
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.FEEDBACK_EMAIL_BUTTON)
            .assertIsDisplayed()
            .performClick()

        val emailIntent = shadowApp.nextStartedActivity
        assertNotNull("Expected ACTION_SENDTO intent for feedback email", emailIntent)
        assertEquals(Intent.ACTION_SENDTO, emailIntent.action)
        val emailUri = emailIntent.dataString.orEmpty()
        assertTrue(
            "Expected mailto:$FEEDBACK_EMAIL_ADDRESS URI, got: $emailUri",
            emailUri.startsWith("mailto:$FEEDBACK_EMAIL_ADDRESS?"),
        )
        val subject = emailIntent.getStringExtra(Intent.EXTRA_SUBJECT).orEmpty()
        val body = emailIntent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
        assertTrue("Subject must mention version name", subject.contains(BuildConfig.VERSION_NAME))
        assertTrue("Subject must mention version code", subject.contains(BuildConfig.VERSION_CODE.toString()))
        assertTrue("Body must mention version name", body.contains(BuildConfig.VERSION_NAME))
        assertTrue("Body must mention version code", body.contains(BuildConfig.VERSION_CODE.toString()))

        // 3. Fallback copies email address to clipboard if no email app is installed
        val noMailContext = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent?) {
                throw ActivityNotFoundException("No email client")
            }
        }
        val launched = openFeedbackEmail(
            context = noMailContext,
            subject = subject,
            body = body,
        )
        assertFalse(launched)
        val clipboard = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        assertEquals(FEEDBACK_EMAIL_ADDRESS, clipboard.primaryClip?.getItemAt(0)?.text?.toString())

        // 4. Device model formatting avoids duplicating manufacturer prefix
        assertEquals("Google Pixel 8", formatDeviceModel("Google", "Pixel 8"))
        assertEquals("Pixel 8", formatDeviceModel("Pixel", "Pixel 8"))
        assertEquals("Unknown", formatDeviceModel("", ""))
    }

    @Test
    fun profileSegmentedSummariesReflectLiveRoutingAndPreferencesOnNarrowScreens() {
        val optimizedRouting = AutoRoutingModeState.OptimizedRouteActive(
            candidateId = "cf-nrt",
            manifestRevision = "rev-1",
            medianLatencyMs = 38L,
            controlMedianLatencyMs = 70L,
            reductionPercent = 45,
            confirmedSampleCount = 4,
            promotedOnReliability = false,
            selectedIpLiteral = "104.16.1.2",
            ingressColo = "NRT",
        )
        val customPrefs = Wenku8UserPreferences(
            appLanguage = Wenku8API.AppLanguage.TC,
            themeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
            einkModeEnabled = true,
            defaultHomeDestination = DefaultHomeDestination.BOOKSHELF,
            readerFontSizeSp = 24,
            readerCustomFontPath = Wenku8UserPreferences.BUILTIN_SERIF_FONT_TOKEN,
            volumeKeyPagingEnabled = true,
            autoOptimizeConnectionEnabled = true,
        )

        composeTestRule.setContent {
            val baseDensity = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = baseDensity.density, fontScale = 1.3f),
            ) {
                Box(modifier = Modifier.size(width = 320.dp, height = 760.dp)) {
                    Wenku8ExpressiveTheme(
                        themeMode = customPrefs.themeMode,
                        einkModeOverride = customPrefs.einkModeEnabled,
                    ) {
                        ProfileAndStudioScreen(
                            preferences = customPrefs,
                            onThemeModeSelected = {},
                            onDefaultHomeSelected = {},
                            autoRoutingState = optimizedRouting,
                            modifier = Modifier.composeTestTagForLazyList(),
                        )
                    }
                }
            }
        }

        val rootBounds = composeTestRule
            .onNodeWithTag(ProfileStudioTestTags.LIST)
            .fetchSemanticsNode()
            .boundsInRoot

        composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
            .performScrollToNode(hasTestTag(ProfileStudioTestTags.ENTRY_APPEARANCE))
        composeTestRule.onNodeWithText("墨水屏", substring = true).assertIsDisplayed()
        composeTestRule.onNodeWithText("24 sp · 系统衬线 · 音量键翻页开").assertIsDisplayed()

        composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
            .performScrollToNode(hasTestTag(ProfileStudioTestTags.ENTRY_NETWORK))
        composeTestRule.onNodeWithText("优选 104.16.1.2 (38 ms) · NRT").assertIsDisplayed()
        val networkRowBounds = composeTestRule
            .onNodeWithTag(ProfileStudioTestTags.ENTRY_NETWORK)
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(
            "Segmented network row must stay inside 320dp viewport (${networkRowBounds.right} <= ${rootBounds.right + 1f})",
            networkRowBounds.right <= rootBounds.right + 1f,
        )
    }

    @Test
    fun adaptiveMainScaffoldRoutesProfileSettingsSubscreenAndReturnsToProfileTab() {
        var prefs by mutableStateOf(Wenku8UserPreferences())

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = prefs.themeMode) {
                    AdaptiveMainScaffold(
                        preferredHomeDestination = prefs.defaultHomeDestination,
                        isNetworkAvailableOnColdStart = true,
                        currentThemeMode = prefs.themeMode,
                        userPreferences = prefs,
                        onThemeModeSelected = { prefs = prefs.copy(themeMode = it) },
                        onDefaultHomeSelected = { prefs = prefs.copy(defaultHomeDestination = it) },
                        onAppLanguageSelected = { prefs = prefs.copy(appLanguage = it) },
                        onEinkModeChanged = { prefs = prefs.copy(einkModeEnabled = it) },
                    )
                }
            }
        }

        // Navigate to Tab 4 · 我的
        composeTestRule
            .onNodeWithTag(MainScaffoldTestTags.NAV_ITEM_PREFIX + TopLevelDestination.PROFILE.routeKey)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(MainScaffoldTestTags.COMPACT_SHORT_NAV_BAR).assertIsDisplayed()

        // Drill into Appearance subscreen via AdaptiveMainScaffold scene transition
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.ENTRY_APPEARANCE).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_ROOT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(MainScaffoldTestTags.COMPACT_SHORT_NAV_BAR).assertDoesNotExist()

        // Toggle language inside hosted subscreen and navigate back to Tab 4
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.LANG_CHIP_TC).performClick()
        assertEquals(Wenku8API.AppLanguage.TC, prefs.appLanguage)
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_BACK_BUTTON).performClick()
        composeTestRule.waitForIdle()

        // Bottom bar and Profile Hub return with updated live summary
        composeTestRule.onNodeWithTag(MainScaffoldTestTags.COMPACT_SHORT_NAV_BAR).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.ENTRY_APPEARANCE).assertIsDisplayed()
        composeTestRule.onNodeWithText("繁體中文", substring = true).assertIsDisplayed()
    }

    @Test
    fun privacyPolicyAndAdOptions_showConditionallyAndOpenExpectedTargets() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val shadowApp = Shadows.shadowOf(app)
        shadowApp.clearNextStartedActivities()

        var privacyOptionsRequired by mutableStateOf(false)
        var privacyOptionsClicks = 0

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.SYSTEM) {
                    ProfileSettingsSubscreen(
                        section = ProfileSettingsSection.ABOUT_AND_FEEDBACK,
                        preferences = Wenku8UserPreferences(),
                        onNavigateBack = {},
                        onThemeModeSelected = {},
                        onDefaultHomeSelected = {},
                        onAppLanguageSelected = {},
                        onEinkModeChanged = {},
                        onReaderTypographyChanged = { _, _, _, _ -> },
                        onReaderCustomFontPathChanged = {},
                        onReaderCustomBackgroundPathChanged = {},
                        onVolumeKeyPagingChanged = {},
                        isPrivacyOptionsRequired = privacyOptionsRequired,
                        onOpenPrivacyOptions = { privacyOptionsClicks += 1 },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. Non-EEA (isPrivacyOptionsRequired == false): Privacy Policy visible, Privacy & Ad Options hidden
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
            .performScrollToNode(hasTestTag(ProfileStudioTestTags.PRIVACY_POLICY_BUTTON))
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.PRIVACY_POLICY_BUTTON)
            .assertIsDisplayed()
            .performClick()

        val privacyIntent = shadowApp.nextStartedActivity
        assertNotNull("Expected browser intent for Privacy Policy", privacyIntent)
        assertEquals(Intent.ACTION_VIEW, privacyIntent.action)
        assertEquals(PRIVACY_POLICY_URL, privacyIntent.dataString)
        assertEquals("https://wenku8.app/privacy/", privacyIntent.dataString)

        composeTestRule.onNodeWithTag(ProfileStudioTestTags.PRIVACY_OPTIONS_BUTTON)
            .assertDoesNotExist()

        // 2. EEA/UK (isPrivacyOptionsRequired == true): Privacy & Ad Options becomes visible & clickable
        composeTestRule.runOnIdle { privacyOptionsRequired = true }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
            .performScrollToNode(hasTestTag(ProfileStudioTestTags.PRIVACY_OPTIONS_BUTTON))
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.PRIVACY_OPTIONS_BUTTON)
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, privacyOptionsClicks)

        // 3. Verify SC / zh-TW / zh-HK string resources for both privacy items
        val expectedByLocale = listOf(
            java.util.Locale.SIMPLIFIED_CHINESE to ("隐私政策" to "隐私与广告选项"),
            java.util.Locale.TAIWAN to ("隱私政策" to "隱私與廣告選項"),
            java.util.Locale("zh", "HK") to ("隱私政策" to "隱私與廣告選項"),
        )
        for ((locale, expectedPair) in expectedByLocale) {
            val localizedConfig = android.content.res.Configuration(app.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = app.createConfigurationContext(localizedConfig)
            assertEquals(
                expectedPair.first,
                localizedContext.getString(org.mewx.wenku8.R.string.profile_privacy_policy_action),
            )
            assertEquals(
                expectedPair.second,
                localizedContext.getString(org.mewx.wenku8.R.string.profile_privacy_options_action),
            )
        }
    }

    @Test
    fun customCacheInventoryAndFineGrainedCleanup_preservesSharedImagesProgress_andRemovesEmptiedBooks() =
        runTest(UnconfinedTestDispatcher()) {
            val db = buildFileBackedDb("custom_cache_inventory")
            try {
                val filesDir = tempFolder.newFolder("custom_clean_files")
                val cacheDir = tempFolder.newFolder("custom_clean_cache")
                val imgsDir = File(filesDir, "imgs").apply { mkdirs() }
                val savesImgsDir = File(filesDir, "saves/imgs").apply { mkdirs() }
                val diagDir = File(cacheDir, "diagnostics").apply { mkdirs() }
                val artbookCacheDir = File(
                    cacheDir,
                    org.mewx.wenku8.data.media.WenkuImageRequests.ARTBOOK_CACHE_DIR_NAME,
                ).apply { mkdirs() }

                val tempFile = File(cacheDir, "http_tmp.bin").apply { writeBytes(ByteArray(512) { 1 }) }
                val freshDiag = File(diagDir, "diag.txt").apply { writeText("keep-me") }
                val validJpegSmall = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10, 0xFF.toByte(), 0xD9.toByte())
                val validJpegLarge = ByteArray(2048) { 0x2A }.apply {
                    this[0] = 0xFF.toByte()
                    this[1] = 0xD8.toByte()
                    this[2] = 0xFF.toByte()
                    this[3] = 0xE0.toByte()
                    this[size - 2] = 0xFF.toByte()
                    this[size - 1] = 0xD9.toByte()
                }

                // Book 101 (in bookshelf): 2 volumes, 3 cached chapters (shared image + exclusive image)
                // Book 202 (not in bookshelf): 1 volume, 1 cached chapter + uncategorized chapter (no TOC row) + cover
                db.novelDao().upsertNovels(
                    listOf(
                        NovelEntity(aid = 101, title = "书架大作", inBookshelf = true),
                        NovelEntity(aid = 202, title = "临时浏览轻小说", inBookshelf = false),
                    ),
                )
                db.tocDao().upsertVolumes(
                    listOf(
                        VolumeEntity(vid = 1010, aid = 101, volumeOrder = 0, volumeTitle = "第一卷"),
                        VolumeEntity(vid = 1011, aid = 101, volumeOrder = 1, volumeTitle = "第二卷"),
                        VolumeEntity(vid = 2020, aid = 202, volumeOrder = 0, volumeTitle = "第一卷"),
                    ),
                )
                db.tocDao().upsertChapters(
                    listOf(
                        ChapterEntity(cid = 10101, vid = 1010, aid = 101, chapterOrder = 0, chapterTitle = "第一章", isIllustrationChapter = false),
                        ChapterEntity(cid = 10102, vid = 1010, aid = 101, chapterOrder = 1, chapterTitle = "插图", isIllustrationChapter = true),
                        ChapterEntity(cid = 10111, vid = 1011, aid = 101, chapterOrder = 0, chapterTitle = "二卷第一章", isIllustrationChapter = false),
                        ChapterEntity(cid = 20201, vid = 2020, aid = 202, chapterOrder = 0, chapterTitle = "序章", isIllustrationChapter = false),
                    ),
                )
                db.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(
                        aid = 101,
                        vid = 1010,
                        cid = 10101,
                        volumeTitle = "第一卷",
                        chapterTitle = "第一章",
                        paragraphIndex = 12,
                        charOffset = 0,
                        legacyScrollPx = 0,
                        progressPermille = 330,
                        updatedAtMs = 5_000L,
                    ),
                )

                val exclusiveImgUrl = "https://pic.wenku8.com/pictures/1/101/exclusive.jpg"
                val sharedImgUrl = "https://pic.wenku8.com/pictures/1/101/shared.jpg"
                val exclusiveLegacy = org.mewx.wenku8.data.remote.parser.WenkuXmlParsers.generateLegacyImageFileName(exclusiveImgUrl)
                val sharedLegacy = org.mewx.wenku8.data.remote.parser.WenkuXmlParsers.generateLegacyImageFileName(sharedImgUrl)
                val exclusiveImgFile = File(savesImgsDir, exclusiveLegacy).apply { writeBytes(validJpegLarge) }
                val artbookExclusiveFile = File(artbookCacheDir, exclusiveLegacy).apply { writeBytes(validJpegSmall) }
                val sharedImgFile = File(savesImgsDir, sharedLegacy).apply { writeBytes(validJpegSmall) }
                val orphanImgFile = File(savesImgsDir, "orphan_leftover.jpg").apply { writeBytes(validJpegSmall) }
                val shelfCover = File(imgsDir, "101.jpg").apply { writeBytes(validJpegSmall) }
                val nonShelfCover = File(imgsDir, "202.jpg").apply { writeBytes(validJpegSmall) }

                db.chapterContentDao().upsertChapterContents(
                    listOf(
                        ChapterContentEntity(
                            cid = 10101,
                            aid = 101,
                            contentLang = "SC",
                            contentText = "正文内容一",
                            byteLength = 300,
                            imageUrlsJson = "[\"$sharedImgUrl\"]",
                            imageCount = 1,
                            sha256Checksum = "s1",
                            downloadedAtMs = 1_000L,
                        ),
                        ChapterContentEntity(
                            cid = 10102,
                            aid = 101,
                            contentLang = "SC",
                            contentText = "插图页",
                            byteLength = 120,
                            imageUrlsJson = "[\"$exclusiveImgUrl\",\"$sharedImgUrl\"]",
                            imageCount = 2,
                            sha256Checksum = "s2",
                            downloadedAtMs = 1_000L,
                        ),
                        ChapterContentEntity(
                            cid = 10111,
                            aid = 101,
                            contentLang = "SC",
                            contentText = "第二卷正文",
                            byteLength = 400,
                            imageUrlsJson = "[]",
                            imageCount = 0,
                            sha256Checksum = "s3",
                            downloadedAtMs = 1_000L,
                        ),
                        ChapterContentEntity(
                            cid = 20201,
                            aid = 202,
                            contentLang = "SC",
                            contentText = "非书架序章",
                            byteLength = 200,
                            imageUrlsJson = "[]",
                            imageCount = 0,
                            sha256Checksum = "s4",
                            downloadedAtMs = 1_000L,
                        ),
                        // Unmapped cid without TOC entry -> falls back to "未归类章节"
                        ChapterContentEntity(
                            cid = 20299,
                            aid = 202,
                            contentLang = "SC",
                            contentText = "未归类正文",
                            byteLength = 100,
                            imageUrlsJson = "[]",
                            imageCount = 0,
                            sha256Checksum = "s5",
                            downloadedAtMs = 1_000L,
                        ),
                    ),
                )

                val repo = StorageAndCacheRepository(
                    database = db,
                    cacheRootProvider = { cacheDir },
                    assetRootsProvider = { listOf(filesDir) },
                    ioDispatcher = UnconfinedTestDispatcher(testScheduler),
                    coverUrlProvider = { aid -> "https://img.wenku8.com/image/0/$aid/${aid}s.jpg" },
                )

                // 1. Inspect inventory: books sorted by totalBytes desc (Book 101 > Book 202), categories measured accurately
                val inv1 = repo.inspectCustomCacheInventory()
                assertEquals(1, inv1.tempCacheFileCount - 1) // tempFile + artbookExclusiveFile (diagnostics excluded)
                assertEquals(512L + validJpegSmall.size, inv1.tempCacheBytes)
                assertEquals(1, inv1.nonBookshelfCoverCount)
                assertEquals(1, inv1.nonBookshelfNovelCount)
                assertEquals(1, inv1.orphanImageFileCount)
                assertEquals(2, inv1.books.size)
                assertEquals(101, inv1.books[0].aid)
                assertEquals("https://img.wenku8.com/image/0/101/101s.jpg", inv1.books[0].coverUrl)
                assertEquals(2, inv1.books[0].volumes.size)
                assertEquals(3, inv1.books[0].cachedChapterCount)
                assertEquals(202, inv1.books[1].aid)
                assertEquals(2, inv1.books[1].volumes.size) // 第一卷 + 未归类章节
                assertTrue(inv1.books[1].volumes.any { it.volumeTitle == "未归类章节" })

                // 2. Clear single chapter 10102 -> deletes exclusiveImgFile + artbookExclusiveFile, KEEPS sharedImgFile
                //    (still referenced by 10101) and KEEPS reading progress!
                val singleRes = repo.clearCachedChapters(listOf(10102))
                assertEquals(1, singleRes.deletedChaptersCount)
                assertFalse("Exclusive illustration must be deleted", exclusiveImgFile.exists())
                assertFalse("Artbook temp cache for exclusive illustration must be deleted", artbookExclusiveFile.exists())
                assertTrue("Shared illustration still referenced by 10101 must survive", sharedImgFile.exists())
                assertNotNull("Reading progress must never be deleted by cache cleanup", db.readingProgressDao().getProgressForNovel(101))
                assertTrue("Text + image bytes must be counted", singleRes.reclaimedBytes >= 120L + validJpegLarge.size)

                // 3. Clearing ALL remaining chapters of non-bookshelf book 202 removes the book from inventory AND purges its cover
                val clearBook202 = repo.clearCachedChapters(listOf(20201, 20299))
                assertEquals(2, clearBook202.deletedChaptersCount)
                assertEquals(1, clearBook202.purgedCoverCount)
                assertFalse("Non-bookshelf cover must be purged once all chapters are cleared", nonShelfCover.exists())
                assertTrue("Bookshelf cover must remain intact", shelfCover.exists())

                val inv2 = repo.inspectCustomCacheInventory()
                assertEquals("Emptied book 202 must vanish from inventory", listOf(101), inv2.books.map { it.aid })

                // 4. runCustomCacheClean with remaining Book 101 chapters + temp + non-bookshelf + orphan
                val emptyRes = repo.runCustomCacheClean(CustomCacheCleanRequest())
                assertEquals(0, emptyRes.deletedChaptersCount)

                val fullCustomRes = repo.runCustomCacheClean(
                    CustomCacheCleanRequest(
                        cleanTempCache = true,
                        cleanNonBookshelfBrowse = true,
                        cleanOrphanImages = true,
                        selectedChapterCids = setOf(10101, 10111),
                    ),
                )
                assertEquals(2, fullCustomRes.deletedChaptersCount)
                assertEquals(1, fullCustomRes.prunedTransientNovelRows)
                assertFalse(tempFile.exists())
                assertTrue("Fresh diagnostic report must still be preserved", freshDiag.exists())
                assertFalse("Shared illustration now unreferenced must be deleted", sharedImgFile.exists())
                assertFalse("Orphan image must be deleted", orphanImgFile.exists())
                assertTrue("Bookshelf cover stays on shelf even when book chapters are emptied", shelfCover.exists())

                val inv3 = repo.inspectCustomCacheInventory()
                assertTrue("All emptied books must disappear completely from custom cache list", inv3.books.isEmpty())
                assertEquals(0L, inv3.totalBytes)
            } finally {
                db.close()
            }
        }

    private fun Modifier.composeTestTagForLazyList(): Modifier =
        this.testTag(ProfileStudioTestTags.LIST)

    private fun injectZipEntries(baseZip: ByteArray, extraEntries: Map<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            ZipInputStream(ByteArrayInputStream(baseZip)).use { zis ->
                while (true) {
                    val entry = zis.nextEntry ?: break
                    zos.putNextEntry(ZipEntry(entry.name))
                    zis.copyTo(zos)
                    zos.closeEntry()
                    zis.closeEntry()
                }
            }
            for ((name, bytes) in extraEntries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun listZipEntryNames(zipBytes: ByteArray): Set<String> {
        val names = LinkedHashSet<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                names.add(entry.name)
                zis.closeEntry()
            }
        }
        return names
    }
}
