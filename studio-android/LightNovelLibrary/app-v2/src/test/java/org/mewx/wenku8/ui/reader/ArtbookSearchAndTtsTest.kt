package org.mewx.wenku8.ui.reader

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Rect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.data.remote.api.OnlineSearchField
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.data.repository.ParsedChapterToc
import org.mewx.wenku8.data.repository.ParsedVolumeToc
import org.mewx.wenku8.reader.engine.DeterministicCellLineBreaker
import org.mewx.wenku8.reader.model.ReaderPageTurnMode
import org.mewx.wenku8.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
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

    @Test
    fun ttsReadAloud_highlightsActiveParagraph_andAutoFollowsAcrossPagesInAllSixModes() {
        seedMultiPageTwoChapterNovel()
        var activeMode by mutableStateOf(ReaderPageTurnMode.COVER_SLIDE)
        var modeIteration by mutableStateOf(0)
        var activeSpeechEngine by mutableStateOf(FakeSpeechEngineAdapter())

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                key(activeMode, modeIteration) {
                    if (modeIteration > 0) {
                        UnifiedReaderScreen(
                            launchRequest = ReaderLaunchRequest(
                                aid = 3096,
                                novelTitle = "银翼编年史",
                                vid = 801,
                                volumeTitle = "第一卷",
                                cid = 8101,
                                chapterTitle = "第一章 星图",
                                initialParagraphIndex = 0,
                                startWithTts = true,
                            ),
                            database = database,
                            catalogGateway = fakeGateway,
                            novelTocSyncRepository = tocSyncRepository,
                            preferences = Wenku8UserPreferences(readerPageTurnMode = activeMode),
                            onReaderTypographyChanged = { _, _, _, _ -> },
                            onNavigateBack = {},
                            lineBreaker = DeterministicCellLineBreaker,
                            speechEngineAdapter = activeSpeechEngine,
                            ioDispatcher = Dispatchers.Main.immediate,
                            computeDispatcher = Dispatchers.Main.immediate,
                        )
                    }
                }
            }
        }

        for (mode in ReaderPageTurnMode.entries) {
            val engineForMode = FakeSpeechEngineAdapter()
            activeSpeechEngine = engineForMode
            activeMode = mode
            modeIteration += 1
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule
                    .onAllNodesWithTag(ReaderTestTags.TTS_CONTROLLER_BAR)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            // Block 0 (chapter title) is spoken first and highlighted on the active viewport!
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule
                    .onAllNodesWithTag(
                        "${ReaderTestTags.TTS_HIGHLIGHTED_BLOCK_PREFIX}0",
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            // Advance 22 utterances to reach deep blockIndex = 22 (off initial page/scroll viewport)
            repeat(22) {
                engineForMode.completeActiveUtterance()
                composeTestRule.waitForIdle()
            }

            // Verify the reader automatically flipped/scrolled so block 22 is now visible & highlighted!
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule
                    .onAllNodesWithTag(
                        "${ReaderTestTags.TTS_HIGHLIGHTED_BLOCK_PREFIX}22",
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
            assertEquals(
                "Block 0 should no longer be highlighted once TTS reaches block 22 in mode=$mode",
                0,
                composeTestRule
                    .onAllNodesWithTag(
                        "${ReaderTestTags.TTS_HIGHLIGHTED_BLOCK_PREFIX}0",
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNodes()
                    .size,
            )

            composeTestRule.onNodeWithTag(ReaderTestTags.TTS_STOP_BUTTON).performClick()
            composeTestRule.waitForIdle()
        }
    }

    @Test
    fun inBookSearch_jumpsToTargetParagraph_andHighlightsMatchInAllSixModes() {
        seedMultiPageTwoChapterNovel()
        var activeMode by mutableStateOf(ReaderPageTurnMode.COVER_SLIDE)
        var modeIteration by mutableStateOf(0)

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                key(activeMode, modeIteration) {
                    UnifiedReaderScreen(
                        launchRequest = ReaderLaunchRequest(
                            aid = 3096,
                            novelTitle = "银翼编年史",
                            vid = 801,
                            volumeTitle = "第一卷",
                            cid = 8101,
                            chapterTitle = "第一章 星图",
                            initialParagraphIndex = 0,
                        ),
                        database = database,
                        catalogGateway = fakeGateway,
                        novelTocSyncRepository = tocSyncRepository,
                        preferences = Wenku8UserPreferences(readerPageTurnMode = activeMode),
                        onReaderTypographyChanged = { _, _, _, _ -> },
                        onNavigateBack = {},
                        lineBreaker = DeterministicCellLineBreaker,
                        ioDispatcher = Dispatchers.Main.immediate,
                        computeDispatcher = Dispatchers.Main.immediate,
                    )
                }
            }
        }

        for (mode in ReaderPageTurnMode.entries) {
            activeMode = mode
            modeIteration += 1
            composeTestRule.waitForIdle()

            val viewportTag = if (mode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                ReaderTestTags.CONTINUOUS_SCROLL_LIST
            } else {
                ReaderTestTags.PAGED_HORIZONTAL_PAGER
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithTag(viewportTag).fetchSemanticsNodes().isNotEmpty()
            }

            // Open HUD -> open In-Book Search -> search for "星界秘银罗盘" (at deep blockIndex = 22)
            composeTestRule.onNodeWithTag(viewportTag).performTouchInput {
                click(center)
            }
            composeTestRule.onNodeWithTag(ReaderTestTags.HUD_SEARCH_BUTTON).performClick()
            composeTestRule.onNodeWithTag(ReaderTestTags.SEARCH_INPUT).performTextInput("星界秘银罗盘")
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule
                    .onAllNodesWithTag("${ReaderTestTags.SEARCH_RESULT_PREFIX}0")
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }

            // Tap result -> reader jumps to block 22 and renders focal highlight in ALL 6 modes!
            composeTestRule.onNodeWithTag("${ReaderTestTags.SEARCH_RESULT_PREFIX}0").performClick()
            composeTestRule.waitForIdle()

            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule
                    .onAllNodesWithTag(
                        "${ReaderTestTags.FOCAL_HIGHLIGHTED_BLOCK_PREFIX}22",
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    @Test
    fun bookmarks_addNavigateAwayAndJumpBack_highlightsBookmarkedParagraphInAllSixModes() {
        seedMultiPageTwoChapterNovel()
        var activeMode by mutableStateOf(ReaderPageTurnMode.COVER_SLIDE)
        var modeIteration by mutableStateOf(0)

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                key(activeMode, modeIteration) {
                    UnifiedReaderScreen(
                        launchRequest = ReaderLaunchRequest(
                            aid = 3096,
                            novelTitle = "银翼编年史",
                            vid = 801,
                            volumeTitle = "第一卷",
                            cid = 8101,
                            chapterTitle = "第一章 星图",
                            initialParagraphIndex = 22,
                        ),
                        database = database,
                        catalogGateway = fakeGateway,
                        novelTocSyncRepository = tocSyncRepository,
                        preferences = Wenku8UserPreferences(readerPageTurnMode = activeMode),
                        onReaderTypographyChanged = { _, _, _, _ -> },
                        onNavigateBack = {},
                        lineBreaker = DeterministicCellLineBreaker,
                        ioDispatcher = Dispatchers.Main.immediate,
                        computeDispatcher = Dispatchers.Main.immediate,
                    )
                }
            }
        }

        for (mode in ReaderPageTurnMode.entries) {
            runBlocking {
                database.bookmarkNoteDao().getAllBookmarkNotes().forEach { note ->
                    database.bookmarkNoteDao().deleteBookmarkNoteById(note.id)
                }
            }
            activeMode = mode
            modeIteration += 1
            composeTestRule.waitForIdle()

            val viewportTag = if (mode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                ReaderTestTags.CONTINUOUS_SCROLL_LIST
            } else {
                ReaderTestTags.PAGED_HORIZONTAL_PAGER
            }
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule.onAllNodesWithTag(viewportTag).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.waitForIdle()

            // 1. Open HUD at deep paragraph 22 and add bookmark
            composeTestRule.onNodeWithTag(viewportTag).performTouchInput {
                click(center)
            }
            composeTestRule.onNodeWithTag(ReaderTestTags.HUD_BOOKMARK_TOGGLE).performClick()
            composeTestRule.waitForIdle()

            val createdNotes = runBlocking {
                database.bookmarkNoteDao().getAllBookmarkNotes().filter { it.aid == 3096 }
            }
            assertEquals("Expected 1 bookmark in mode=$mode", 1, createdNotes.size)
            val expectedHighlightedBlock = createdNotes.single().startParagraph
            assertTrue(
                "Expected deep bookmarked block (>= 15) in mode=$mode, got $expectedHighlightedBlock",
                expectedHighlightedBlock >= 15,
            )

            // 2. Navigate away to Chapter 2 (cid = 8102)
            composeTestRule.onNodeWithTag(ReaderTestTags.HUD_NEXT_CHAPTER_BUTTON).performClick()
            composeTestRule.waitForIdle()
            assertEquals(8102, runBlocking { database.readingProgressDao().getProgressForNovel(3096)?.cid })

            // 3. Open Bookmarks Sheet from Chapter 2 and jump back to the saved bookmark!
            composeTestRule.onNodeWithTag(ReaderTestTags.ACTION_BOOKMARKS).performClick()
            composeTestRule.onNodeWithTag("${ReaderTestTags.BOOKMARK_ITEM_PREFIX}0").performClick()
            composeTestRule.waitForIdle()

            assertEquals(8101, runBlocking { database.readingProgressDao().getProgressForNovel(3096)?.cid })
            composeTestRule.waitUntil(timeoutMillis = 5_000L) {
                composeTestRule
                    .onAllNodesWithTag(
                        "${ReaderTestTags.FOCAL_HIGHLIGHTED_BLOCK_PREFIX}$expectedHighlightedBlock",
                        useUnmergedTree = true,
                    )
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            }
        }
    }

    @Test
    fun returningFromIllustrationChapter_viaChipOrBackwardPageTurn_landsStrictlyOnPreviousChapterWithoutDoubleStep() {
        val imgFile1 = createTestImageFile("illus_back_1.jpg", 400, 560, AndroidColor.rgb(66, 120, 190))
        val imgFile2 = createTestImageFile("illus_back_2.jpg", 400, 560, AndroidColor.rgb(180, 90, 110))
        val urlMap = mapOf(
            "https://pic.wenku8.com/pictures/5120/p1.jpg" to imgFile1,
            "https://pic.wenku8.com/pictures/5120/p2.jpg" to imgFile2,
        )

        runBlocking {
            database.novelDao().upsertNovel(
                NovelEntity(
                    aid = 5120,
                    title = "插画返章测试卷",
                    author = "测试作者",
                    inBookshelf = true,
                    metaSyncedAtMs = 1_000L,
                ),
            )
            tocSyncRepository.syncNovelIndexAtomically(
                aid = 5120,
                language = Wenku8API.AppLanguage.SC,
                requestStartedAtMs = 2_000L,
                parsedVolumes = listOf(
                    ParsedVolumeToc(
                        vid = 901,
                        volumeTitle = "第一卷",
                        chapters = listOf(
                            ParsedChapterToc(cid = 9101, chapterTitle = "第一章 长篇序幕"),
                            ParsedChapterToc(cid = 9102, chapterTitle = "第二章 后记"),
                            ParsedChapterToc(cid = 9103, chapterTitle = "插图"),
                        ),
                    ),
                ),
            )
            val longCh1 = buildString {
                appendLine("第一卷 第一章 长篇序幕")
                for (i in 1..24) {
                    appendLine("　　第一章第${i}段长篇正文，生成远多于后记页数的多页前置窗口以覆盖坐标偏移边界。")
                }
            }
            val shortCh2 = """
                第一卷 第二章 后记
                　　这是紧邻插图之前的单页后记正文。
            """.trimIndent()
            val illusCh3 = buildString {
                appendLine("第一卷 插图")
                urlMap.keys.forEach { u ->
                    appendLine("<!--image-->$u<!--image-->")
                }
            }

            listOf(9101 to longCh1, 9102 to shortCh2, 9103 to illusCh3).forEach { (cid, body) ->
                database.chapterContentDao().upsertChapterContent(
                    ChapterContentEntity(
                        cid = cid,
                        aid = 5120,
                        contentLang = Wenku8API.AppLanguage.SC.name,
                        contentText = body,
                        byteLength = body.toByteArray().size,
                        sha256Checksum = "sha_$cid",
                        downloadedAtMs = 1_700_000_000_000L,
                    ),
                )
            }
        }

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 5120,
                        novelTitle = "插画返章测试卷",
                        vid = 901,
                        volumeTitle = "第一卷",
                        cid = 9103,
                        chapterTitle = "插图",
                        initialParagraphIndex = 0,
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = Wenku8UserPreferences(readerPageTurnMode = ReaderPageTurnMode.COVER_SLIDE),
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onNavigateBack = {},
                    lineBreaker = DeterministicCellLineBreaker,
                    artbookImageFileResolver = { url -> urlMap[url] },
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Pure illustration chapter 9103 auto-opens ArtbookLightboxOverlay.
        composeTestRule.onNodeWithTag(ArtbookTestTags.OVERLAY).assertIsDisplayed()

        // Tap `"上一章"` chip inside ArtbookLightboxOverlay -> must land strictly on 9102 (NOT 9101!).
        composeTestRule.onNodeWithTag(ArtbookTestTags.PREV_CHAPTER_CHIP).performClick()
        composeTestRule.waitForIdle()

        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(ArtbookTestTags.OVERLAY).fetchSemanticsNodes().size,
        )
        assertEquals(
            "Clicking 上一章 from illustration chapter 9103 must stop at 9102 instead of double-stepping to 9101",
            9102,
            runBlocking { database.readingProgressDao().getProgressForNovel(5120)?.cid },
        )

        // 2. Flip forward 1 page from 1-page 9102 back into 9103 (`插图`), which auto-opens Artbook again!
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(Offset(x = width * 0.85f, y = height * 0.5f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ArtbookTestTags.OVERLAY).assertIsDisplayed()
        assertEquals(9103, runBlocking { database.readingProgressDao().getProgressForNovel(5120)?.cid })

        // Switch 9103 to `"小说阅读"` mode and tap top-left zone (`PREV_PAGE`, above the clickable
        // inline illustration card) to return to 9102 -> must land strictly on 9102, never 9101!
        composeTestRule.onNodeWithTag(ArtbookTestTags.READER_MODE_CHIP).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(Offset(x = width * 0.12f, y = height * 0.03f))
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "Stepping backward via tap from illustration chapter 9103 in novel mode must land on 9102, never 9101",
            9102,
            runBlocking { database.readingProgressDao().getProgressForNovel(5120)?.cid },
        )

        // 3. Flip forward into 9103 again (stays in overridden novel mode) and drag-swipe backward
        // (`swipeRight()`) -> must also land strictly on 9102 without double-stepping to 9101!
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(Offset(x = width * 0.85f, y = height * 0.03f))
        }
        composeTestRule.waitForIdle()
        assertEquals(9103, runBlocking { database.readingProgressDao().getProgressForNovel(5120)?.cid })

        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            swipeRight()
        }
        composeTestRule.waitForIdle()

        assertEquals(
            "Swiping backward from illustration chapter 9103 must land strictly on 9102, never 9101",
            9102,
            runBlocking { database.readingProgressDao().getProgressForNovel(5120)?.cid },
        )
    }

    @Test
    fun temporaryVolumeIllustrationPeek_auditsButtonsAndPreservesExactReadingProgress() {
        val imgFile1 = createTestImageFile("vol_peek_1.jpg", 420, 600, AndroidColor.rgb(52, 110, 175))
        val imgFile2 = createTestImageFile("vol_peek_2.jpg", 420, 600, AndroidColor.rgb(175, 88, 120))
        val urlMap = mapOf(
            "https://pic.wenku8.com/pictures/6144/v1_p1.jpg" to imgFile1,
            "https://pic.wenku8.com/pictures/6144/v1_p2.jpg" to imgFile2,
        )

        runBlocking {
            database.novelDao().upsertNovel(
                NovelEntity(
                    aid = 6144,
                    title = "临时插图浏览测试作",
                    author = "测试作者",
                    inBookshelf = true,
                    metaSyncedAtMs = 1_000L,
                ),
            )
            tocSyncRepository.syncNovelIndexAtomically(
                aid = 6144,
                language = Wenku8API.AppLanguage.SC,
                requestStartedAtMs = 2_000L,
                parsedVolumes = listOf(
                    ParsedVolumeToc(
                        vid = 901,
                        volumeTitle = "第一卷",
                        chapters = listOf(
                            ParsedChapterToc(cid = 9101, chapterTitle = "第一章 漫长旅途"),
                            ParsedChapterToc(cid = 9103, chapterTitle = "插图"),
                        ),
                    ),
                    ParsedVolumeToc(
                        vid = 902,
                        volumeTitle = "第二卷",
                        chapters = listOf(
                            ParsedChapterToc(cid = 9201, chapterTitle = "第一章 无图之章"),
                        ),
                    ),
                ),
            )
            val multiPageCh1 = buildString {
                appendLine("第一卷 第一章 漫长旅途")
                for (i in 1..26) {
                    appendLine("　　第一卷第一章第${i}段长篇测试正文，用于验证翻到第二页后临时查看插图不会篡改当前阅读进度锚点。")
                }
            }
            val illusCh = buildString {
                appendLine("第一卷 插图")
                urlMap.keys.forEach { u ->
                    appendLine("<!--image-->$u<!--image-->")
                }
            }
            val vol2NoIllusCh = """
                第二卷 第一章 无图之章
                　　第二卷没有任何插图章节，顶栏不应出现“本卷插图”按钮。
            """.trimIndent()

            listOf(
                9101 to multiPageCh1,
                9103 to illusCh,
                9201 to vol2NoIllusCh,
            ).forEach { (cid, body) ->
                database.chapterContentDao().upsertChapterContent(
                    ChapterContentEntity(
                        cid = cid,
                        aid = 6144,
                        contentLang = Wenku8API.AppLanguage.SC.name,
                        contentText = body,
                        byteLength = body.toByteArray().size,
                        sha256Checksum = "sha_$cid",
                        downloadedAtMs = 1_700_000_000_000L,
                    ),
                )
            }
        }

        var navigateBackCount = 0
        var illustrationClickedCallbackCount = 0

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 6144,
                        novelTitle = "临时插图浏览测试作",
                        vid = 901,
                        volumeTitle = "第一卷",
                        cid = 9101,
                        chapterTitle = "第一章 漫长旅途",
                        initialParagraphIndex = 0,
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = Wenku8UserPreferences(readerPageTurnMode = ReaderPageTurnMode.COVER_SLIDE),
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onIllustrationClicked = { _, _, _ -> illustrationClickedCallbackCount += 1 },
                    onNavigateBack = { navigateBackCount += 1 },
                    lineBreaker = DeterministicCellLineBreaker,
                    artbookImageFileResolver = { url -> urlMap[url] },
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Advance to Page 2 of prose chapter 9101 so we have a non-zero paragraph anchor to protect!
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(Offset(x = width * 0.85f, y = height * 0.5f))
        }
        composeTestRule.waitForIdle()

        val progressBeforePeek = runBlocking { database.readingProgressDao().getProgressForNovel(6144) }
        assertNotNull(progressBeforePeek)
        assertEquals(9101, progressBeforePeek!!.cid)
        assertTrue(
            "Expected Page 2 anchor paragraphIndex > 0 before peeking volume illustrations",
            progressBeforePeek.paragraphIndex > 0,
        )

        // 2. Open HUD in non-illustration chapter 9101 -> "本卷插图" button MUST be displayed!
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(Offset(x = width * 0.5f, y = height * 0.5f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_PEEK_VOLUME_ILLUSTRATIONS_BUTTON)
            .assertIsDisplayed()

        // 3. Tap "本卷插图" -> opens temporary ArtbookLightboxOverlay
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_PEEK_VOLUME_ILLUSTRATIONS_BUTTON)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ArtbookTestTags.OVERLAY).assertIsDisplayed()
        composeTestRule.onNodeWithText("第一卷 · 插图（临时浏览）").assertIsDisplayed()

        // Strict Button Audit: appropriate controls ARE shown...
        composeTestRule.onNodeWithTag(ArtbookTestTags.CLOSE_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ArtbookTestTags.RETURN_TO_READING_CHIP).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ArtbookTestTags.SPREAD_TOGGLE_CHIP).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ArtbookTestTags.ROTATE_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ArtbookTestTags.SAVE_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ArtbookTestTags.SHARE_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ArtbookTestTags.CAROUSEL).assertIsDisplayed()

        // ...while chapter-mutating / mode-overriding chips MUST NOT exist in temporary peek mode!
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(ArtbookTestTags.READER_MODE_CHIP).fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(ArtbookTestTags.PREV_CHAPTER_CHIP).fetchSemanticsNodes().size,
        )
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(ArtbookTestTags.NEXT_CHAPTER_CHIP).fetchSemanticsNodes().size,
        )

        // 4. Browse to image #2 (`2 / 2`) inside temporary peek, then tap "返回阅读" chip
        composeTestRule.onNodeWithTag(ArtbookTestTags.THUMBNAIL_PREFIX + "1").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ArtbookTestTags.PAGE_INDICATOR).assertTextEquals("2 / 2")

        composeTestRule.onNodeWithTag(ArtbookTestTags.RETURN_TO_READING_CHIP).performClick()
        composeTestRule.waitForIdle()

        // Overlay dismissed cleanly WITHOUT exiting reader, WITHOUT undo pill, and WITHOUT mutating progress!
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(ArtbookTestTags.OVERLAY).fetchSemanticsNodes().size,
        )
        assertEquals(0, navigateBackCount)
        assertEquals(0, illustrationClickedCallbackCount)
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(ReaderTestTags.UNDO_JUMP_PILL).fetchSemanticsNodes().size,
        )
        val progressAfterFirstPeek = runBlocking { database.readingProgressDao().getProgressForNovel(6144) }
        assertNotNull(progressAfterFirstPeek)
        assertEquals(9101, progressAfterFirstPeek!!.cid)
        assertEquals(progressBeforePeek.paragraphIndex, progressAfterFirstPeek.paragraphIndex)
        assertEquals(progressBeforePeek.charOffset, progressAfterFirstPeek.charOffset)

        // 5. Re-open "本卷插图" -> remembers image `2 / 2` in the same volume, and top-left `←` also returns to reading!
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(Offset(x = width * 0.5f, y = height * 0.5f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_PEEK_VOLUME_ILLUSTRATIONS_BUTTON)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ArtbookTestTags.PAGE_INDICATOR).assertTextEquals("2 / 2")
        composeTestRule.onNodeWithTag(ArtbookTestTags.CLOSE_BUTTON).performClick()
        composeTestRule.waitForIdle()
        assertEquals(0, navigateBackCount)
        assertEquals(9101, runBlocking { database.readingProgressDao().getProgressForNovel(6144)?.cid })

        // 6. Open TOC in Volume 1 -> MUST only list Volume 1 chapters (`9101`, `9103`), excluding Volume 2 (`9201`),
        //    then advance via `9103` -> Next Chapter into Volume 2 (`9201`) and verify both no "本卷插图" button
        //    and Volume 2 TOC only lists `9201`!
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(Offset(x = width * 0.5f, y = height * 0.5f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.ACTION_TOC).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.TOC_HEADER_TITLE).assertTextEquals("本卷目录 (2)")
        composeTestRule.onNodeWithTag(ReaderTestTags.TOC_VOLUME_SUBTITLE).assertTextEquals("第一卷")
        composeTestRule.onNodeWithTag(ReaderTestTags.TOC_CHAPTER_ITEM_PREFIX + "9101").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReaderTestTags.TOC_CHAPTER_ITEM_PREFIX + "9103").assertIsDisplayed()
        assertEquals(
            "Reader TOC sheet must only show chapters from the current volume",
            0,
            composeTestRule.onAllNodesWithTag(ReaderTestTags.TOC_CHAPTER_ITEM_PREFIX + "9201")
                .fetchSemanticsNodes().size,
        )

        composeTestRule.onNodeWithTag(ReaderTestTags.TOC_CHAPTER_ITEM_PREFIX + "9103").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ArtbookTestTags.NEXT_CHAPTER_CHIP).performClick()
        composeTestRule.waitForIdle()
        assertEquals(9201, runBlocking { database.readingProgressDao().getProgressForNovel(6144)?.cid })

        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(Offset(x = width * 0.5f, y = height * 0.5f))
        }
        composeTestRule.waitForIdle()
        assertEquals(
            "Volumes without any illustration chapter must not show HUD_PEEK_VOLUME_ILLUSTRATIONS_BUTTON",
            0,
            composeTestRule.onAllNodesWithTag(ReaderTestTags.HUD_PEEK_VOLUME_ILLUSTRATIONS_BUTTON)
                .fetchSemanticsNodes().size,
        )

        composeTestRule.onNodeWithTag(ReaderTestTags.ACTION_TOC).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.TOC_HEADER_TITLE).assertTextEquals("本卷目录 (1)")
        composeTestRule.onNodeWithTag(ReaderTestTags.TOC_VOLUME_SUBTITLE).assertTextEquals("第二卷")
        composeTestRule.onNodeWithTag(ReaderTestTags.TOC_CHAPTER_ITEM_PREFIX + "9201").assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(ReaderTestTags.TOC_CHAPTER_ITEM_PREFIX + "9101")
                .fetchSemanticsNodes().size,
        )
    }

    private fun seedMultiPageTwoChapterNovel() = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 3096,
                title = "银翼编年史",
                author = "测试作者",
                inBookshelf = true,
                metaSyncedAtMs = 1_000L,
            ),
        )
        tocSyncRepository.syncNovelIndexAtomically(
            aid = 3096,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 2_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 801,
                    volumeTitle = "第一卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 8101, chapterTitle = "第一章 星图"),
                        ParsedChapterToc(cid = 8102, chapterTitle = "第二章 远航"),
                    ),
                ),
            ),
        )
        val multiPageCh1 = buildString {
            appendLine("第一卷 第一章 星图")
            for (idx in 1..28) {
                if (idx == 22) {
                    appendLine("　　第22段关键剧情：在苍穹观测台的穹顶之下，少女举起了古老的星界秘银罗盘，指针骤然指向北方群星。")
                } else {
                    appendLine("　　第${idx}段长篇测试正文，用于在横排翻页、仿真卷曲、弹簧滑动、上下卷轴、日式竖排与墨水屏直切六种翻页模式下构建充足的多页阅读高度。")
                }
            }
        }
        val ch2 = buildString {
            appendLine("第一卷 第二章 远航")
            for (idx in 1..10) {
                appendLine("　　第二章第${idx}段测试正文，用于验证跨章跳转后再通过书签返回并高亮。")
            }
        }
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 8101,
                aid = 3096,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = multiPageCh1,
                byteLength = multiPageCh1.toByteArray().size,
                sha256Checksum = "sha_8101",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 8102,
                aid = 3096,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = ch2,
                byteLength = ch2.toByteArray().size,
                sha256Checksum = "sha_8102",
                downloadedAtMs = 1_700_000_000_000L,
            ),
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
