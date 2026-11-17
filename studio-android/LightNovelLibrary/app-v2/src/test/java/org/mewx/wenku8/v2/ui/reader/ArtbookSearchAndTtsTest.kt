package org.mewx.wenku8.v2.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.v2.data.remote.api.OnlineSearchField
import org.mewx.wenku8.v2.data.remote.api.WenkuApiResult
import org.mewx.wenku8.v2.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.v2.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.v2.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.v2.data.repository.ParsedChapterToc
import org.mewx.wenku8.v2.data.repository.ParsedVolumeToc
import org.mewx.wenku8.v2.reader.engine.DeterministicCellLineBreaker
import org.mewx.wenku8.v2.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class ArtbookSearchAndTtsTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: Wenku8Database
    private lateinit var tocSyncRepository: NovelTocSyncRepository
    private lateinit var fakeGateway: NoOpCatalogGateway

    @Before
    fun setUp() {
        val directExecutor = Executor { runnable -> runnable.run() }
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            Wenku8Database::class.java,
        )
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        tocSyncRepository = NovelTocSyncRepository(database)
        fakeGateway = NoOpCatalogGateway()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun artbookSpreadPairing_tileGridMath_andStitchedSpreadJpegExport() = runBlocking {
        val urls = listOf(
            "https://pic.wenku8.com/pictures/1/001.jpg",
            "https://pic.wenku8.com/pictures/1/002.jpg",
            "https://pic.wenku8.com/pictures/1/003.jpg",
            "https://pic.wenku8.com/pictures/1/004.jpg",
        )

        // 1. Single-page mode keeps 1:1 entries
        val singles = buildArtbookEntries(urls, spreadEnabled = false)
        assertEquals(4, singles.size)
        assertTrue(singles[0] is ArtbookEntry.Single)

        // 2. RTL Japanese bunkobon spread pairs (0, 1) with 0 on the right and 1 on the left!
        val rtlSpreads = buildArtbookEntries(
            imageUrls = urls,
            spreadEnabled = true,
            firstPageSolo = false,
            isRtlSpread = true,
        )
        assertEquals(2, rtlSpreads.size)
        val firstRtl = rtlSpreads[0] as ArtbookEntry.DoubleSpread
        assertEquals(urls[1], firstRtl.leftImageUrl)
        assertEquals(urls[0], firstRtl.rightImageUrl)

        // 3. `首张单页` frontispiece offset keeps cover (0) solo and pairs (1, 2) into a spread!
        val offsetSpreads = buildArtbookEntries(
            imageUrls = urls,
            spreadEnabled = true,
            firstPageSolo = true,
            isRtlSpread = false,
        )
        assertEquals(3, offsetSpreads.size)
        assertTrue(offsetSpreads[0] is ArtbookEntry.Single)
        val midLtr = offsetSpreads[1] as ArtbookEntry.DoubleSpread
        assertEquals(urls[1], midLtr.leftImageUrl)
        assertEquals(urls[2], midLtr.rightImageUrl)
        assertTrue(offsetSpreads[2] is ArtbookEntry.Single)

        // 4. `BitmapRegionDecoder` tile grid math: 0 tiles at 1.0x, sampleSize=2 at 1.5x, sampleSize=1 at 2.5x
        assertTrue(
            computeVisibleHighResTiles(
                partKey = "L",
                imageWidth = 2400,
                imageHeight = 3400,
                visibleSourceRect = Rect(0, 0, 2400, 3400),
                effectiveZoomScale = 1.0f,
            ).isEmpty(),
        )
        val midZoomTiles = computeVisibleHighResTiles(
            partKey = "L",
            imageWidth = 2400,
            imageHeight = 3400,
            visibleSourceRect = Rect(200, 200, 1400, 1800),
            effectiveZoomScale = 1.5f,
        )
        assertTrue(midZoomTiles.isNotEmpty())
        assertTrue(midZoomTiles.all { it.sampleSize == 2 })

        val fullZoomTiles = computeVisibleHighResTiles(
            partKey = "R",
            imageWidth = 2400,
            imageHeight = 3400,
            visibleSourceRect = Rect(0, 0, 1000, 1000),
            effectiveZoomScale = 2.5f,
        )
        assertEquals(4, fullZoomTiles.size) // 2x2 grid of 512px native tiles covering 0..1000
        assertTrue(fullZoomTiles.all { it.sampleSize == 1 })

        // 5. Stitched 2-page spread export + 90-degree rotation
        val imgFileA = createTestImageFile("spread_a.jpg", width = 300, height = 400, color = AndroidColor.RED)
        val imgFileB = createTestImageFile("spread_b.jpg", width = 300, height = 400, color = AndroidColor.BLUE)
        val resolver: suspend (String) -> File? = { u ->
            if (u.endsWith("001.jpg")) imgFileA else imgFileB
        }

        val stitchedOut = tempFolder.newFile("stitched_out.jpg")
        val stitchedOk = exportArtbookEntryToJpegFile(
            targetFile = stitchedOut,
            entry = firstRtl,
            rotationQuarterTurns = 0,
            imageFileResolver = resolver,
            ioDispatcher = Dispatchers.Unconfined,
        )
        assertTrue(stitchedOk)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(stitchedOut.absolutePath, bounds)
        assertEquals(600, bounds.outWidth)
        assertEquals(400, bounds.outHeight)

        val rotatedOut = tempFolder.newFile("stitched_rot90.jpg")
        val rotatedOk = exportArtbookEntryToJpegFile(
            targetFile = rotatedOut,
            entry = firstRtl,
            rotationQuarterTurns = 1,
            imageFileResolver = resolver,
            ioDispatcher = Dispatchers.Unconfined,
        )
        assertTrue(rotatedOk)
        val rotBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(rotatedOut.absolutePath, rotBounds)
        assertEquals(400, rotBounds.outWidth)
        assertEquals(600, rotBounds.outHeight)
    }

    @Test
    fun inlineIllustrationTap_opensArtbookLightbox_togglesDoublePageSpread_andRotates() {
        val file1 = createTestImageFile("ill_1.jpg", 400, 560, AndroidColor.CYAN)
        val file2 = createTestImageFile("ill_2.jpg", 400, 560, AndroidColor.MAGENTA)
        val file3 = createTestImageFile("ill_3.jpg", 400, 560, AndroidColor.YELLOW)
        val urlMap = mapOf(
            "https://pic.wenku8.com/pictures/4526/p1.jpg" to file1,
            "https://pic.wenku8.com/pictures/4526/p2.jpg" to file2,
            "https://pic.wenku8.com/pictures/4526/p3.jpg" to file3,
        )

        runBlocking {
            database.novelDao().upsertNovel(
                NovelEntity(
                    aid = 4526,
                    title = "愚者们的方舟",
                    author = "绫崎隼",
                    metaSyncedAtMs = 1_000L,
                ),
            )
            tocSyncRepository.syncNovelIndexAtomically(
                aid = 4526,
                language = Wenku8API.AppLanguage.SC,
                requestStartedAtMs = 2_000L,
                parsedVolumes = listOf(
                    ParsedVolumeToc(
                        vid = 901,
                        volumeTitle = "第一卷",
                        chapters = listOf(
                            ParsedChapterToc(cid = 9101, chapterTitle = "插图"),
                        ),
                    ),
                ),
            )
            val illRaw = buildString {
                appendLine("第一卷 插图")
                urlMap.keys.forEach { u ->
                    appendLine("<!--image-->$u<!--image-->")
                }
            }
            database.chapterContentDao().upsertChapterContent(
                ChapterContentEntity(
                    cid = 9101,
                    aid = 4526,
                    contentLang = Wenku8API.AppLanguage.SC.name,
                    contentText = illRaw,
                    byteLength = illRaw.toByteArray().size,
                    sha256Checksum = "sha_9101",
                    downloadedAtMs = 1_700_000_000_000L,
                ),
            )
        }

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 4526,
                        novelTitle = "愚者们的方舟",
                        vid = 901,
                        volumeTitle = "第一卷",
                        cid = 9101,
                        chapterTitle = "插图",
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = Wenku8UserPreferences(),
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onNavigateBack = {},
                    lineBreaker = DeterministicCellLineBreaker,
                    artbookImageFileResolver = { u -> urlMap[u] },
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }

        // Pure-illustration chapter automatically enters ArtbookLightboxOverlay directly!
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule
                .onAllNodesWithTag(ArtbookTestTags.OVERLAY)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag(ArtbookTestTags.OVERLAY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ArtbookTestTags.PAGE_INDICATOR).assertTextEquals("1 / 3")
        composeTestRule.onNodeWithTag(ArtbookTestTags.READER_MODE_CHIP).assertIsDisplayed()

        // 1-tap `"小说阅读"` switches back to Novel Reading Mode where Page 1 ALREADY shows
        // the first illustration alongside the chapter heading (no title-only Page 1!).
        composeTestRule.onNodeWithTag(ArtbookTestTags.READER_MODE_CHIP).performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(ArtbookTestTags.OVERLAY).fetchSemanticsNodes().size,
        )

        // Tap inline illustration card on Page 1 -> re-opens ArtbookLightboxOverlay!
        composeTestRule
            .onNodeWithTag("${ReaderTestTags.INLINE_IMAGE_PREFIX}0")
            .assertIsDisplayed()
            .performClick()

        composeTestRule.onNodeWithTag(ArtbookTestTags.OVERLAY).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ArtbookTestTags.PAGE_INDICATOR).assertTextEquals("1 / 3")
        composeTestRule.onNodeWithTag(ArtbookTestTags.CAROUSEL).assertIsDisplayed()

        // Toggle `跨页拼合` (`口绘` double-page spread) -> indicator updates to "1-2 / 3"
        composeTestRule.onNodeWithTag(ArtbookTestTags.SPREAD_TOGGLE_CHIP).performClick()
        composeTestRule.onNodeWithTag(ArtbookTestTags.PAGE_INDICATOR).assertTextEquals("1-2 / 3")

        // Toggle `首张单页` frontispiece offset -> cover 1 becomes solo ("1 / 3") while (2, 3) pair up!
        composeTestRule.onNodeWithTag(ArtbookTestTags.SPREAD_OFFSET_CHIP).performClick()
        composeTestRule.onNodeWithTag(ArtbookTestTags.PAGE_INDICATOR).assertTextEquals("1 / 3")

        // Rotate 90 degrees and close overlay cleanly back to Novel Reading Mode
        composeTestRule.onNodeWithTag(ArtbookTestTags.ROTATE_BUTTON).performClick()
        composeTestRule.onNodeWithTag(ArtbookTestTags.CLOSE_BUTTON).performClick()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(ArtbookTestTags.OVERLAY).fetchSemanticsNodes().size,
        )
    }

    @Test
    fun inBookSearch_acrossCurrentAndAllCachedChapters_jumpsToAnchorWithUndoPill() {
        seedTwoChapterNovelWithDistinctKeyword()

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 2048,
                        novelTitle = "星海方舟",
                        vid = 701,
                        volumeTitle = "第一卷",
                        cid = 7101,
                        chapterTitle = "第一章 启航",
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = Wenku8UserPreferences(),
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onNavigateBack = {},
                    lineBreaker = DeterministicCellLineBreaker,
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Open HUD via center tap, then open In-Book Search sheet
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(Offset(width / 2f, height / 2f))
        }
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_SEARCH_BUTTON).performClick()
        composeTestRule.onNodeWithTag(ReaderTestTags.SEARCH_SHEET).assertIsDisplayed()

        // Phrase "终焉黎明的约定" exists ONLY in Chapter 2 (cid=7102)
        composeTestRule.onNodeWithTag(ReaderTestTags.SEARCH_INPUT).performTextInput("终焉黎明的约定")
        composeTestRule.waitForIdle()
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag("${ReaderTestTags.SEARCH_RESULT_PREFIX}0")
                .fetchSemanticsNodes()
                .size,
        )

        // Switch scope to `已缓存全书` -> finds hit in Chapter 2!
        composeTestRule.onNodeWithTag(ReaderTestTags.SEARCH_SCOPE_CACHED_CHIP).performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule
                .onAllNodesWithTag("${ReaderTestTags.SEARCH_RESULT_PREFIX}0")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Click result -> jumps to Chapter 2 (cid=7102) and surfaces 5s Undo Jump Pill!
        composeTestRule.onNodeWithTag("${ReaderTestTags.SEARCH_RESULT_PREFIX}0").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ReaderTestTags.UNDO_JUMP_PILL).assertIsDisplayed()
        val savedProgress = runBlocking {
            database.readingProgressDao().getProgressForNovel(2048)
        }
        assertNotNull(savedProgress)
        assertEquals(7102, savedProgress?.cid)
    }

    @Test
    fun systemTts_autoStartsOnLaunchFlag_andAdvancesAcrossUtterancesIntoNextChapter() {
        seedTwoChapterNovelWithDistinctKeyword()
        val fakeSpeechEngine = FakeSpeechEngineAdapter()

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 2048,
                        novelTitle = "星海方舟",
                        vid = 701,
                        volumeTitle = "第一卷",
                        cid = 7101,
                        chapterTitle = "第一章 启航",
                        startWithTts = true,
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = Wenku8UserPreferences(),
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onNavigateBack = {},
                    lineBreaker = DeterministicCellLineBreaker,
                    speechEngineAdapter = fakeSpeechEngine,
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.TTS_CONTROLLER_BAR)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag(ReaderTestTags.TTS_CONTROLLER_BAR).assertIsDisplayed()
        assertTrue(fakeSpeechEngine.spokenTexts.first().contains("第一章"))

        // Complete all 3 utterances in Chapter 1 (title + 2 paragraphs) -> auto-advances to Chapter 2!
        repeat(3) {
            fakeSpeechEngine.completeActiveUtterance()
            composeTestRule.waitForIdle()
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            fakeSpeechEngine.spokenTexts.any { it.contains("第二章") }
        }
        assertTrue(fakeSpeechEngine.spokenTexts.last().contains("第二章"))

        // Stop TTS cleanly via floating bar close button
        composeTestRule.onNodeWithTag(ReaderTestTags.TTS_STOP_BUTTON).performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.TTS_CONTROLLER_BAR)
                .fetchSemanticsNodes()
                .size,
        )
    }

    private fun createTestImageFile(name: String, width: Int, height: Int, color: Int): File {
        val file = tempFolder.newFile(name)
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Canvas(bmp).drawColor(color)
        FileOutputStream(file).use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 92, out)
        }
        bmp.recycle()
        return file
    }

    private fun seedTwoChapterNovelWithDistinctKeyword() = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 2048,
                title = "星海方舟",
                author = "测试作者",
                inBookshelf = true,
                metaSyncedAtMs = 1_000L,
            ),
        )
        tocSyncRepository.syncNovelIndexAtomically(
            aid = 2048,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 2_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 701,
                    volumeTitle = "第一卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 7101, chapterTitle = "第一章 启航"),
                        ParsedChapterToc(cid = 7102, chapterTitle = "第二章 誓言"),
                    ),
                ),
            ),
        )
        val ch1 = """
            第一卷 第一章 启航
            　　晨光穿过云海，飞空艇静静停泊在白亚港口。
            　　少年翻开航海日志，检查引擎气压。
        """.trimIndent()
        val ch2 = """
            第一卷 第二章 誓言
            　　夜幕降临时，甲板上响起了久违的风琴声。
            　　少女轻声念出了终焉黎明的约定，星辰随之闪耀。
        """.trimIndent()
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 7101,
                aid = 2048,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = ch1,
                byteLength = ch1.toByteArray().size,
                sha256Checksum = "sha_7101",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 7102,
                aid = 2048,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = ch2,
                byteLength = ch2.toByteArray().size,
                sha256Checksum = "sha_7102",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )
    }
}

private class FakeSpeechEngineAdapter : SpeechEngineAdapter {
    val spokenTexts = ArrayList<String>()
    var activeUtteranceId: String? = null
    var lastRate: Float = 1.0f
    private var onStarted: ((String) -> Unit)? = null
    private var onDone: ((String) -> Unit)? = null

    override fun initialize(
        appLanguage: Wenku8API.AppLanguage,
        onInitialized: (Boolean) -> Unit,
        onUtteranceStarted: (String) -> Unit,
        onUtteranceDone: (String) -> Unit,
        onUtteranceError: (String) -> Unit,
    ) {
        onStarted = onUtteranceStarted
        onDone = onUtteranceDone
        onInitialized(true)
    }

    override fun setSpeechRate(rate: Float) {
        lastRate = rate
    }

    override fun speakFlush(text: String, utteranceId: String): Boolean {
        spokenTexts.add(text)
        activeUtteranceId = utteranceId
        onStarted?.invoke(utteranceId)
        return true
    }

    fun completeActiveUtterance() {
        val id = activeUtteranceId ?: return
        onDone?.invoke(id)
    }

    override fun stop() {
        activeUtteranceId = null
    }

    override fun shutdown() {
        activeUtteranceId = null
    }
}

private class NoOpCatalogGateway : WenkuCatalogGateway {
    override val isApiConfigured: Boolean = true
    override fun resolveCoverUrl(aid: Int): String = ""
    override suspend fun downloadBinaryBytes(url: String): ByteArray? = null
    override suspend fun fetchNovelListPage(
        sortedBy: Wenku8API.NovelSortedBy,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListPage> = WenkuApiResult.ApiNotConfigured

    override suspend fun searchNovelsByField(
        query: String,
        searchField: OnlineSearchField,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<Int>> = WenkuApiResult.Success(emptyList())

    override suspend fun fetchNovelShortInfo(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListItem> = WenkuApiResult.ApiNotConfigured

    override suspend fun fetchNovelFullMeta(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelFullMeta> = WenkuApiResult.ApiNotConfigured

    override suspend fun fetchNovelFullIntro(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> = WenkuApiResult.ApiNotConfigured

    override suspend fun fetchNovelVolumeToc(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedVolumeToc>> = WenkuApiResult.ApiNotConfigured

    override suspend fun fetchChapterContent(
        aid: Int,
        cid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> = WenkuApiResult.ApiNotConfigured

    override suspend fun fetchCloudBookshelf(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.Success(emptyList())
}
