package org.mewx.wenku8.v2.ui.profile

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
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
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.BookmarkNoteEntity
import org.mewx.wenku8.v2.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.v2.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingSessionEntity
import org.mewx.wenku8.v2.data.local.db.entity.VolumeEntity
import org.mewx.wenku8.v2.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.v2.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.v2.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.v2.data.repository.BackupArchiveMode
import org.mewx.wenku8.v2.data.repository.BackupArchiveRepository
import org.mewx.wenku8.v2.data.repository.CustomAssetSlot
import org.mewx.wenku8.v2.data.repository.StorageAndCacheRepository
import org.mewx.wenku8.v2.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
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

                // Offline chapter referencing one illustration URL, plus an orphan illustration file
                val liveIllustUrl = "https://pic.wenku8.com/pictures/1/1306/42001/55001.jpg"
                val liveLegacyName = WenkuXmlParsers.generateLegacyImageFileName(liveIllustUrl)
                val savesImgsDir = File(File(filesDir, "saves"), "imgs").apply { mkdirs() }
                val liveIllustFile = File(savesImgsDir, liveLegacyName).apply {
                    writeBytes(ByteArray(4096))
                }
                val orphanIllustFile = File(savesImgsDir, "orphan_unreferenced.jpg").apply {
                    writeBytes(ByteArray(3072))
                }

                db.chapterContentDao().upsertChapterContent(
                    ChapterContentEntity(
                        cid = 42001,
                        aid = 1306,
                        contentLang = "SC",
                        contentText = "正文插图测试",
                        byteLength = 18,
                        imageUrlsJson = "[\"$liveIllustUrl\"]",
                        imageCount = 1,
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

                // 2. Deep Orphan Clean deletes unreferenced saves/imgs/ file while preserving live illustration
                val progressUpdates = mutableListOf<Int>()
                val deepSummary = repo.runDeepOrphanCacheClean { progress ->
                    progressUpdates.add(progress.scannedFiles)
                }
                assertTrue(liveIllustFile.exists())
                assertFalse(orphanIllustFile.exists())
                assertEquals(1, deepSummary.deletedFileCount)
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
                            onOpenNetworkDiagnostics = { diagnosticsOpened++ },
                            onAppLanguageSelected = { prefsState = prefsState.copy(appLanguage = it) },
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

            // 1. Reading footprint summary visible
            composeTestRule.onNodeWithText("120 分钟").assertIsDisplayed()
            composeTestRule.onNodeWithText("2.5w").assertIsDisplayed()

            // 2. Network diagnostics shortcut works
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.OPEN_DIAGNOSTICS_BUTTON))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.OPEN_DIAGNOSTICS_BUTTON).performClick()
            assertEquals(1, diagnosticsOpened)

            // 3. Language switch & Typography reset
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.LANG_CHIP_TC))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LANG_CHIP_TC).performClick()
            assertEquals(Wenku8API.AppLanguage.TC, prefsState.appLanguage)

            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.RESET_TYPOGRAPHY_BUTTON))
            composeTestRule.onNodeWithText("字号大小：22 sp").assertIsDisplayed()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.RESET_TYPOGRAPHY_BUTTON).performClick()
            composeTestRule.waitForIdle()
            assertEquals(Wenku8UserPreferences.DEFAULT_READER_FONT_SIZE_SP, prefsState.readerFontSizeSp)

            // 4. Fast Clean executes and renders completion banner
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.FAST_CLEAN_BUTTON))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.FAST_CLEAN_BUTTON).performClick()
            composeTestRule.waitUntil(timeoutMillis = 3_000L) {
                !File(cacheDir, "stale.tmp").exists()
            }
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.STORAGE_STATUS_BANNER))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.STORAGE_STATUS_BANNER).assertIsDisplayed()

            // 5. Open-Source License dialog displays R.raw.license content
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.LIST)
                .performScrollToNode(hasTestTag(ProfileStudioTestTags.OPEN_LICENSE_BUTTON))
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.OPEN_LICENSE_BUTTON).performClick()
            composeTestRule.onNodeWithText("开源许可与版权声明").assertIsDisplayed()
            composeTestRule.onNodeWithText("关闭").performClick()

            // 6. About dialog 5-tap version Easter egg
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.OPEN_ABOUT_BUTTON).performClick()
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ABOUT_EASTER_EGG_TEXT).assertDoesNotExist()
            repeat(5) {
                composeTestRule.onNodeWithTag(ProfileStudioTestTags.ABOUT_VERSION_TAP_TARGET).performClick()
            }
            composeTestRule.onNodeWithTag(ProfileStudioTestTags.ABOUT_EASTER_EGG_TEXT).assertIsDisplayed()
        } finally {
            db.close()
        }
    }

    private fun Modifier.composeTestTagForLazyList(): Modifier =
        this.testTag(ProfileStudioTestTags.LIST)

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
