package org.mewx.wenku8.v2.ui.reader

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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

    @Test
    fun coverSlideShadow_movesRightToLeftFollowingUpperPageEdge() {
        // At rest (0f) and completed (-1f), no shadow should be cast.
        assertNull(computeCoverSlideShadowSpec(pageOffset = 0f, viewportWidthPx = 1000f, shadowWidthPx = 24f))
        assertNull(computeCoverSlideShadowSpec(pageOffset = -1f, viewportWidthPx = 1000f, shadowWidthPx = 24f))

        // Swiping left to advance page:
        // - 10% swipe -> incoming page has pageOffset = -0.90f -> upper page right edge is at x = 900f
        // - 50% swipe -> incoming page has pageOffset = -0.50f -> upper page right edge is at x = 500f
        // - 90% swipe -> incoming page has pageOffset = -0.10f -> upper page right edge is at x = 100f
        val spec10 = computeCoverSlideShadowSpec(pageOffset = -0.90f, viewportWidthPx = 1000f, shadowWidthPx = 24f)
        val spec50 = computeCoverSlideShadowSpec(pageOffset = -0.50f, viewportWidthPx = 1000f, shadowWidthPx = 24f)
        val spec90 = computeCoverSlideShadowSpec(pageOffset = -0.10f, viewportWidthPx = 1000f, shadowWidthPx = 24f)

        assertNotNull(spec10)
        assertNotNull(spec50)
        assertNotNull(spec90)

        assertEquals(900f, spec10!!.startX, 0.01f)
        assertEquals(500f, spec50!!.startX, 0.01f)
        assertEquals(100f, spec90!!.startX, 0.01f)
        assertTrue(spec10.startX > spec50.startX && spec50.startX > spec90.startX)
        assertEquals(24f, spec50.widthPx, 0.01f)
    }

    @Test
    fun liveSystemClock_updatesOnTimeChangedAndTimeTickInPagedAndContinuousModes() {
        seedTwoChapterNovel()
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 9)
            set(java.util.Calendar.MINUTE, 15)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val fakeMillis = java.util.concurrent.atomic.AtomicLong(cal.timeInMillis)
        var currentPrefs by mutableStateOf(Wenku8UserPreferences())

        composeTestRule.setContent {
            CompositionLocalProvider(LocalReaderClockMillisProvider provides { fakeMillis.get() }) {
                Wenku8ExpressiveTheme(themeMode = currentPrefs.themeMode) {
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
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.READER_CLOCK_LABEL)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag(ReaderTestTags.READER_CLOCK_LABEL).assertTextEquals("09:15")

        // Advance system clock to 14:42 and broadcast ACTION_TIME_CHANGED -> updates in paged mode without page flip!
        cal.set(java.util.Calendar.HOUR_OF_DAY, 14)
        cal.set(java.util.Calendar.MINUTE, 42)
        fakeMillis.set(cal.timeInMillis)
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .sendBroadcast(Intent(Intent.ACTION_TIME_CHANGED))
        composeTestRule.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.READER_CLOCK_LABEL).assertTextEquals("14:42")

        // Switch to CONTINUOUS_SCROLL and broadcast ACTION_TIME_TICK at 23:08 -> updates live in scroll mode too!
        currentPrefs = currentPrefs.copy(readerPageTurnMode = ReaderPageTurnMode.CONTINUOUS_SCROLL)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.READER_CLOCK_LABEL).assertTextEquals("14:42")

        cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
        cal.set(java.util.Calendar.MINUTE, 8)
        fakeMillis.set(cal.timeInMillis)
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .sendBroadcast(Intent(Intent.ACTION_TIME_TICK))
        composeTestRule.waitForIdle()
        org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.READER_CLOCK_LABEL).assertTextEquals("23:08")
    }

    @Test
    fun continuousScrollMode_scrubberSliderJumpsToTargetParagraph_andUndoRestores() {
        seedTwoChapterNovel()
        var currentPrefs by mutableStateOf(
            Wenku8UserPreferences(readerPageTurnMode = ReaderPageTurnMode.CONTINUOUS_SCROLL),
        )

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = currentPrefs.themeMode) {
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
                    onNavigateBack = {},
                    lineBreaker = DeterministicCellLineBreaker,
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.CONTINUOUS_SCROLL_LIST)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.waitForIdle()

        val initialProgress = runBlocking { database.readingProgressDao().getProgressForNovel(1587) }
        assertNotNull(initialProgress)
        assertEquals(0, initialProgress!!.paragraphIndex)

        // Tap continuous scroll viewport to open HUD
        composeTestRule.onNodeWithTag(ReaderTestTags.CONTINUOUS_SCROLL_LIST).performClick()
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_SCRUBBER_SLIDER).assertIsDisplayed()

        // Pause auto-advance so the 5s Undo Jump Pill stays visible, then scrub to ~85% of the chapter!
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(ReaderTestTags.HUD_SCRUBBER_SLIDER).performTouchInput {
            down(Offset(width * 0.08f, centerY))
            moveTo(Offset(width * 0.85f, centerY))
            up()
        }
        composeTestRule.mainClock.advanceTimeBy(300L)

        val scrubbedProgress = runBlocking { database.readingProgressDao().getProgressForNovel(1587) }
        assertNotNull(scrubbedProgress)
        assertTrue(
            "Expected continuous scroll scrubber to jump past paragraph 18, got ${scrubbedProgress!!.paragraphIndex}",
            scrubbedProgress.paragraphIndex >= 18,
        )
        composeTestRule.onNodeWithTag(ReaderTestTags.UNDO_JUMP_PILL).assertIsDisplayed()

        // Tap Undo Jump Pill -> restores scroll position back to paragraph 0!
        composeTestRule.onNodeWithTag(ReaderTestTags.UNDO_JUMP_PILL).performClick()
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        val restoredProgress = runBlocking { database.readingProgressDao().getProgressForNovel(1587) }
        assertNotNull(restoredProgress)
        assertEquals(0, restoredProgress!!.paragraphIndex)
    }

    @Test
    fun continuousScrollMode_leftEdgeVerticalDragAdjustsBrightnessWithoutScrollingContent() {
        seedTwoChapterNovel()
        var hostActivityRef: Activity? = null

        composeTestRule.setContent {
            val ctx = LocalContext.current
            hostActivityRef = generateSequence(ctx) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
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
                    preferences = Wenku8UserPreferences(
                        readerPageTurnMode = ReaderPageTurnMode.CONTINUOUS_SCROLL,
                        readerEdgeBrightnessDragEnabled = true,
                    ),
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onNavigateBack = {},
                    lineBreaker = DeterministicCellLineBreaker,
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.CONTINUOUS_SCROLL_LIST)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // 1. Drag upward inside the 24..72dp safe left-edge brightness strip (45dp) in CONTINUOUS_SCROLL mode:
        // must adjust screen brightness and display BRIGHTNESS_PILL WITHOUT scrolling LazyColumn or opening HUD!
        val stripXPx = with(composeTestRule.density) { 45.dp.toPx() }
        composeTestRule.onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER).performTouchInput {
            down(Offset(stripXPx, height * 0.75f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER).performTouchInput {
            moveTo(Offset(stripXPx, height * 0.55f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false
        composeTestRule.onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER).performTouchInput {
            moveTo(Offset(stripXPx, height * 0.30f))
            up()
        }
        composeTestRule.mainClock.advanceTimeBy(350L)

        composeTestRule.onNodeWithTag(ReaderTestTags.BRIGHTNESS_PILL).assertIsDisplayed()
        val updatedBrightness = hostActivityRef?.window?.attributes?.screenBrightness ?: -1f
        assertTrue(
            "Expected upward drag in 24..72dp strip during CONTINUOUS_SCROLL to raise brightness above 0.55f, got $updatedBrightness",
            updatedBrightness > 0.65f,
        )
        assertEquals(
            "Left-edge brightness drag must not scroll CONTINUOUS_SCROLL chapter text",
            0,
            runBlocking { database.readingProgressDao().getProgressForNovel(1587)?.paragraphIndex },
        )
        assertEquals(
            "Left-edge brightness drag must not accidentally toggle HUD open on pointer up",
            0,
            composeTestRule.onAllNodesWithTag(ReaderTestTags.HUD_SCRUBBER_SLIDER).fetchSemanticsNodes().size,
        )

        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        // 2. Dragging vertically in the center (outside 24..72dp) still scrolls CONTINUOUS_SCROLL content normally!
        composeTestRule.onNodeWithTag(ReaderTestTags.CONTINUOUS_SCROLL_LIST).performTouchInput {
            val midX = width * 0.55f
            down(Offset(midX, height * 0.80f))
            moveTo(Offset(midX, height * 0.50f))
            moveTo(Offset(midX, height * 0.18f))
            up()
        }
        composeTestRule.waitForIdle()

        val scrolledParagraph = runBlocking { database.readingProgressDao().getProgressForNovel(1587)?.paragraphIndex ?: 0 }
        assertTrue(
            "Center vertical drag must continue scrolling CONTINUOUS_SCROLL normally (got paragraphIndex=$scrolledParagraph)",
            scrolledParagraph > 0,
        )
    }

    @Test
    fun readerDayNightToggle_synchronizesGlobalThemeAndHidesSystemNavigationBar() {
        seedTwoChapterNovel()
        var currentPrefs by mutableStateOf(
            Wenku8UserPreferences(
                themeMode = Wenku8ThemeMode.CLASSIC_BLUE,
                lastDayThemeMode = Wenku8ThemeMode.CLASSIC_BLUE,
                readerPaperPreset = ReaderPaperPreset.EYE_CARE_GREEN,
                lastDayPaperPreset = ReaderPaperPreset.EYE_CARE_GREEN,
            ),
        )
        var isInReader by mutableStateOf(true)
        var observedSurfaceColor by mutableStateOf(Color.White)
        var hostActivityRef: Activity? = null

        composeTestRule.setContent {
            val ctx = LocalContext.current
            val view = LocalView.current
            hostActivityRef = generateSequence(ctx) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull()
            Wenku8ExpressiveTheme(themeMode = currentPrefs.themeMode) {
                observedSurfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
                if (isInReader) {
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
                        onReaderPaperPresetChanged = { preset ->
                            currentPrefs = currentPrefs.copy(
                                readerPaperPreset = preset,
                                lastDayPaperPreset = if (!preset.isDark) preset else currentPrefs.lastDayPaperPreset,
                            )
                        },
                        onThemeModeSelected = { nextTheme ->
                            currentPrefs = currentPrefs.copy(
                                themeMode = nextTheme,
                                lastDayThemeMode = if (nextTheme == Wenku8ThemeMode.CLASSIC_BLUE ||
                                    nextTheme == Wenku8ThemeMode.ARCHIVAL_DAY
                                ) {
                                    nextTheme
                                } else {
                                    currentPrefs.lastDayThemeMode
                                },
                            )
                        },
                        onNavigateBack = { isInReader = false },
                        lineBreaker = DeterministicCellLineBreaker,
                        ioDispatcher = Dispatchers.Main.immediate,
                        computeDispatcher = Dispatchers.Main.immediate,
                    )
                } else {
                    val _unused = view
                }
            }
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Verify Android Q+ navigation bar contrast enforcement is disabled and navigationBars() hidden in reader
        val window = hostActivityRef?.window
        assertNotNull(window)
        assertFalse(window!!.isNavigationBarContrastEnforced)
        val rootInsetsInReader = ViewCompat.getRootWindowInsets(window.decorView)
        if (rootInsetsInReader != null) {
            assertFalse(rootInsetsInReader.isVisible(WindowInsetsCompat.Type.navigationBars()))
        }

        // Initial HUD surface in Classic Blue is bright (day)
        val initialLuminance =
            0.299f * observedSurfaceColor.red +
                0.587f * observedSurfaceColor.green +
                0.114f * observedSurfaceColor.blue
        assertTrue("Expected bright day surface initially, luminance=$initialLuminance", initialLuminance > 0.7f)

        // Open HUD and tap Night Mode -> BOTH readerPaperPreset AND global themeMode switch to CARBON_NIGHT!
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(center)
        }
        composeTestRule.onNodeWithTag(ReaderTestTags.ACTION_DAY_NIGHT).performClick()
        composeTestRule.waitForIdle()

        assertEquals(ReaderPaperPreset.CARBON_NIGHT, currentPrefs.readerPaperPreset)
        assertEquals(Wenku8ThemeMode.CARBON_NIGHT, currentPrefs.themeMode)
        val nightLuminance =
            0.299f * observedSurfaceColor.red +
                0.587f * observedSurfaceColor.green +
                0.114f * observedSurfaceColor.blue
        assertTrue("Expected dark HUD surface in night mode, luminance=$nightLuminance", nightLuminance < 0.25f)

        // Switch paper preset chip to PURE_AMOLED inside Typography Studio -> global theme updates to PURE_AMOLED!
        composeTestRule.onNodeWithTag(ReaderTestTags.ACTION_TYPOGRAPHY).performClick()
        composeTestRule
            .onNodeWithTag("${ReaderTestTags.PAPER_PRESET_CHIP_PREFIX}${ReaderPaperPreset.PURE_AMOLED.name}")
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(Wenku8ThemeMode.PURE_AMOLED, currentPrefs.themeMode)

        // Close Typography Studio (tap upper viewport above panel; HUD remains open!) and tap Day/Night button again -> restores EYE_CARE_GREEN + CLASSIC_BLUE!
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(Offset(centerX, height * 0.22f))
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.ACTION_DAY_NIGHT).performClick()
        composeTestRule.waitForIdle()
        assertEquals(ReaderPaperPreset.EYE_CARE_GREEN, currentPrefs.readerPaperPreset)
        assertEquals(Wenku8ThemeMode.CLASSIC_BLUE, currentPrefs.themeMode)

        // Exit reader -> system bars are restored on dispose
        isInReader = false
        composeTestRule.waitForIdle()
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
