package org.mewx.wenku8.v2.ui.reader

import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
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
import org.mewx.wenku8.v2.reader.model.ReaderPageTurnMode
import org.mewx.wenku8.v2.reader.model.ReaderPaperPreset
import org.mewx.wenku8.v2.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class UnifiedReaderScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: Wenku8Database
    private lateinit var tocSyncRepository: NovelTocSyncRepository
    private lateinit var fakeGateway: FakeUnifiedReaderGateway

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
        fakeGateway = FakeUnifiedReaderGateway()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun nineZoneMatrix_predictiveBackStrip_andVolumeKeyNonRepeatingRules() {
        // 1. LTR 9-zone matrix: Top = [Prev, Prev, Next], Middle = [Prev, HUD, Next], Bottom = [Next, Next, Next]
        assertEquals(
            ReaderTapZoneAction.PREV_PAGE,
            resolveNineZoneTapAction(50f, 50f, 300f, 600f, isRtlBook = false),
        )
        assertEquals(
            ReaderTapZoneAction.PREV_PAGE,
            resolveNineZoneTapAction(150f, 50f, 300f, 600f, isRtlBook = false),
        )
        assertEquals(
            ReaderTapZoneAction.NEXT_PAGE,
            resolveNineZoneTapAction(250f, 50f, 300f, 600f, isRtlBook = false),
        )
        assertEquals(
            ReaderTapZoneAction.PREV_PAGE,
            resolveNineZoneTapAction(50f, 300f, 300f, 600f, isRtlBook = false),
        )
        assertEquals(
            ReaderTapZoneAction.TOGGLE_HUD,
            resolveNineZoneTapAction(150f, 300f, 300f, 600f, isRtlBook = false),
        )
        assertEquals(
            ReaderTapZoneAction.NEXT_PAGE,
            resolveNineZoneTapAction(250f, 300f, 300f, 600f, isRtlBook = false),
        )
        assertEquals(
            ReaderTapZoneAction.NEXT_PAGE,
            resolveNineZoneTapAction(50f, 520f, 300f, 600f, isRtlBook = false),
        )

        // 2. RTL Bunkobon mirrors horizontal columns so left tap advances NEXT_PAGE and right tap goes PREV_PAGE
        assertEquals(
            ReaderTapZoneAction.NEXT_PAGE,
            resolveNineZoneTapAction(50f, 300f, 300f, 600f, isRtlBook = true),
        )
        assertEquals(
            ReaderTapZoneAction.PREV_PAGE,
            resolveNineZoneTapAction(250f, 300f, 300f, 600f, isRtlBook = true),
        )
        assertEquals(
            ReaderTapZoneAction.TOGGLE_HUD,
            resolveNineZoneTapAction(150f, 300f, 300f, 600f, isRtlBook = true),
        )

        // 3. Predictive-Back-safe brightness strip strictly inset to 24..72dp (never stealing 0..24dp bezel)
        assertFalse(isWithinPredictiveBackSafeBrightnessStrip(touchXPx = 20f, densityScale = 1f))
        assertTrue(isWithinPredictiveBackSafeBrightnessStrip(touchXPx = 24f, densityScale = 1f))
        assertTrue(isWithinPredictiveBackSafeBrightnessStrip(touchXPx = 60f, densityScale = 1f))
        assertFalse(isWithinPredictiveBackSafeBrightnessStrip(touchXPx = 80f, densityScale = 1f))

        // 4. Non-repeating hardware volume keys: single page step on repeatCount == 0, consumes UP & repeats
        assertEquals(
            ReaderTapZoneAction.NEXT_PAGE,
            evaluateHardwareVolumeKeyPaging(
                volumeKeyPagingEnabled = true,
                isModalOverlayOpen = false,
                keyCode = AndroidKeyEvent.KEYCODE_VOLUME_DOWN,
                action = AndroidKeyEvent.ACTION_DOWN,
                repeatCount = 0,
            ),
        )
        assertEquals(
            ReaderTapZoneAction.PREV_PAGE,
            evaluateHardwareVolumeKeyPaging(
                volumeKeyPagingEnabled = true,
                isModalOverlayOpen = false,
                keyCode = AndroidKeyEvent.KEYCODE_VOLUME_UP,
                action = AndroidKeyEvent.ACTION_DOWN,
                repeatCount = 0,
            ),
        )
        assertNull(
            evaluateHardwareVolumeKeyPaging(
                volumeKeyPagingEnabled = true,
                isModalOverlayOpen = false,
                keyCode = AndroidKeyEvent.KEYCODE_VOLUME_DOWN,
                action = AndroidKeyEvent.ACTION_DOWN,
                repeatCount = 3,
            ),
        )
        assertTrue(
            shouldConsumeHardwareVolumeKeyEvent(
                volumeKeyPagingEnabled = true,
                isModalOverlayOpen = false,
                keyCode = AndroidKeyEvent.KEYCODE_VOLUME_DOWN,
            ),
        )
        assertFalse(
            shouldConsumeHardwareVolumeKeyEvent(
                volumeKeyPagingEnabled = true,
                isModalOverlayOpen = true,
                keyCode = AndroidKeyEvent.KEYCODE_VOLUME_DOWN,
            ),
        )
    }

    @Test
    fun unifiedReaderScreen_hudToggle_opaqueSliderReflowCapsule_bookmarkAndUndoJump() {
        seedTwoChapterNovel()
        var currentPrefs by mutableStateOf(Wenku8UserPreferences())

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 1587,
                        vid = 501,
                        cid = 6001,
                        novelTitle = "无职转生",
                        volumeTitle = "第一卷",
                        chapterTitle = "第一章 序章",
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = currentPrefs,
                    onReaderTypographyChanged = { fontSize, lineDist, paraDist, edgeDist ->
                        currentPrefs = currentPrefs.copy(
                            readerFontSizeSp = fontSize,
                            readerLineDistanceDp = lineDist,
                            readerParagraphDistanceDp = paraDist,
                            readerPageEdgeDistanceDp = edgeDist,
                        )
                    },
                    onReaderPageTurnModeChanged = { mode ->
                        currentPrefs = currentPrefs.copy(readerPageTurnMode = mode)
                    },
                    onReaderPaperPresetChanged = { preset ->
                        currentPrefs = currentPrefs.copy(readerPaperPreset = preset)
                    },
                    onNavigateBack = {},
                    lineBreaker = DeterministicCellLineBreaker,
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // HUD starts hidden for zero-clutter immersion
        composeTestRule.onAllNodesWithTag(ReaderTestTags.HUD_FLOATING_TOOLBAR).assertCountEquals(0)

        // Tap center zone -> HUD floating toolbar and top bar appear
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(center)
        }
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_FLOATING_TOOLBAR).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_TOP_BAR).assertIsDisplayed()

        // Toggle bookmark -> persists into BookmarkNoteDao
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_BOOKMARK_TOGGLE).performClick()
        composeTestRule.waitForIdle()
        val bookmarksAfterAdd = runBlocking {
            database.bookmarkNoteDao().getAllBookmarkNotes().filter { it.aid == 1587 && it.cid == 6001 }
        }
        assertEquals(1, bookmarksAfterAdd.size)

        // Jump to next chapter via HUD -> 5s Undo Jump Pill ("↩ 还原至第 1 页") appears within the 5s window
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_NEXT_CHAPTER_BUTTON).assertIsDisplayed()
        val nextBtnNode = composeTestRule.onNodeWithTag(ReaderTestTags.HUD_NEXT_CHAPTER_BUTTON).fetchSemanticsNode()
        assertFalse(
            "Expected HUD_NEXT_CHAPTER_BUTTON to be enabled, config=${nextBtnNode.config}",
            androidx.compose.ui.semantics.SemanticsProperties.Disabled in nextBtnNode.config,
        )
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_NEXT_CHAPTER_BUTTON).performClick()
        composeTestRule.mainClock.advanceTimeBy(200L)
        val undoNodes = composeTestRule.onAllNodesWithTag(ReaderTestTags.UNDO_JUMP_PILL).fetchSemanticsNodes()
        assertEquals("Undo pill nodes count after click", 1, undoNodes.size)
        composeTestRule.onNodeWithTag(ReaderTestTags.UNDO_JUMP_PILL).assertIsDisplayed()

        // Tap Undo Jump Pill -> returns to chapter 6001
        composeTestRule.onNodeWithTag(ReaderTestTags.UNDO_JUMP_PILL).performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        val savedProgress = runBlocking { database.readingProgressDao().getProgressForNovel(1587) }
        assertNotNull(savedProgress)
        assertEquals(6001, savedProgress!!.cid)

        // Open Typography Studio -> full panel visible
        composeTestRule.onNodeWithTag(ReaderTestTags.ACTION_TYPOGRAPHY).performClick()
        composeTestRule.onNodeWithTag(ReaderTestTags.TYPOGRAPHY_PANEL).assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReaderTestTags.SLIDER_LINE_DISTANCE).assertIsDisplayed()

        // Begin dragging Font Size Slider (finger stays down!) -> collapses into 92%-Opaque Reflow Capsule
        composeTestRule.onNodeWithTag(ReaderTestTags.SLIDER_FONT_SIZE).performTouchInput {
            down(Offset(width * 0.2f, centerY))
            moveBy(Offset(width * 0.55f, 0f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.ACTIVE_SLIDER_OPAQUE_CAPSULE).assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(ReaderTestTags.TYPOGRAPHY_PANEL).assertCountEquals(0)
        composeTestRule.onAllNodesWithTag(ReaderTestTags.SLIDER_LINE_DISTANCE).assertCountEquals(0)

        // Release finger -> full Typography Panel returns and updated font size is persisted
        composeTestRule.onNodeWithTag(ReaderTestTags.SLIDER_FONT_SIZE).performTouchInput {
            up()
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.TYPOGRAPHY_PANEL).assertIsDisplayed()
        assertTrue(currentPrefs.readerFontSizeSp > Wenku8UserPreferences.DEFAULT_READER_FONT_SIZE_SP)
    }

    @Test
    fun unifiedReaderScreen_switchesAllSixPageTurnModesCleanly() {
        seedTwoChapterNovel()
        var currentPrefs by mutableStateOf(Wenku8UserPreferences())

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 1587,
                        vid = 501,
                        cid = 6001,
                        novelTitle = "无职转生",
                        volumeTitle = "第一卷",
                        chapterTitle = "第一章 序章",
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = currentPrefs,
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onReaderPageTurnModeChanged = { mode ->
                        currentPrefs = currentPrefs.copy(readerPageTurnMode = mode)
                    },
                    onNavigateBack = {},
                    lineBreaker = DeterministicCellLineBreaker,
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Switch to CONTINUOUS_SCROLL via Mode bottom sheet
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(center)
        }
        composeTestRule.onNodeWithTag(ReaderTestTags.ACTION_PAGE_MODE).performClick()
        composeTestRule
            .onNodeWithTag("${ReaderTestTags.PAGE_MODE_ITEM_PREFIX}${ReaderPageTurnMode.CONTINUOUS_SCROLL.name}")
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.CONTINUOUS_SCROLL_LIST).assertIsDisplayed()

        // Switch through SIMULATION_CURL, VERTICAL_BUNKOBON, EINK_INSTANT, and SPRING_SLIDE cleanly
        for (mode in listOf(
            ReaderPageTurnMode.SIMULATION_CURL,
            ReaderPageTurnMode.VERTICAL_BUNKOBON,
            ReaderPageTurnMode.EINK_INSTANT,
            ReaderPageTurnMode.SPRING_SLIDE,
        )) {
            currentPrefs = currentPrefs.copy(readerPageTurnMode = mode)
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).assertIsDisplayed()
        }
    }

    private fun seedTwoChapterNovel() = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 1587,
                title = "无职转生",
                author = "理不尽な孫の手",
                inBookshelf = true,
                metaSyncedAtMs = 1_000L,
            ),
        )
        tocSyncRepository.syncNovelIndexAtomically(
            aid = 1587,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 2_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 501,
                    volumeTitle = "第一卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 6001, chapterTitle = "第一章 序章"),
                        ParsedChapterToc(cid = 6002, chapterTitle = "第二章 异世界"),
                    ),
                ),
            ),
        )
        val longBodyOne = buildString {
            appendLine("第一卷 第一章 序章")
            repeat(28) { idx ->
                appendLine("　　这是第${idx + 1}段测试正文，用于验证多页轻小说排版、字号实时重排胶囊与书签锚点定位。")
            }
        }
        val longBodyTwo = buildString {
            appendLine("第一卷 第二章 异世界")
            repeat(20) { idx ->
                appendLine("　　第二章第${idx + 1}段测试正文，用于验证跨章预取与五秒撤销跳章胶囊。")
            }
        }
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 6001,
                aid = 1587,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = longBodyOne,
                byteLength = longBodyOne.toByteArray().size,
                sha256Checksum = "sha_6001",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 6002,
                aid = 1587,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = longBodyTwo,
                byteLength = longBodyTwo.toByteArray().size,
                sha256Checksum = "sha_6002",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )
    }
}

private class FakeUnifiedReaderGateway : WenkuCatalogGateway {
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
