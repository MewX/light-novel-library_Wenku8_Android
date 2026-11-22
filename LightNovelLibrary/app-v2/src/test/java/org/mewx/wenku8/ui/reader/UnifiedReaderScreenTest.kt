package org.mewx.wenku8.ui.reader

import android.app.Activity
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.view.KeyEvent as AndroidKeyEvent
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
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
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.dao.OrderedChapterWithVolume
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
import org.mewx.wenku8.reader.engine.ChapterWindowBuffer
import org.mewx.wenku8.reader.engine.ChapterWindowSlot
import org.mewx.wenku8.reader.engine.DeterministicCellLineBreaker
import org.mewx.wenku8.reader.engine.PaginatedChapterLayout
import org.mewx.wenku8.reader.engine.ReaderLayoutConfig
import org.mewx.wenku8.reader.engine.ReaderPage
import org.mewx.wenku8.reader.engine.ReaderPageElement
import org.mewx.wenku8.reader.engine.ReaderWindowSnapshot
import org.mewx.wenku8.reader.engine.ReadingAnchor
import org.mewx.wenku8.reader.engine.StaticLayoutPaginator
import org.mewx.wenku8.reader.engine.WenkuChapterParser
import org.mewx.wenku8.reader.model.ReaderColumnMode
import org.mewx.wenku8.reader.model.ReaderPageTurnMode
import org.mewx.wenku8.reader.model.ReaderPaperPreset
import org.mewx.wenku8.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
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

    @Test
    fun crossChapterFlip_whileNextChapterPrefetchCompletes_neverFlashesPreviousChapterLastPage(): Unit = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 2200,
                title = "跨卷翻页测试",
                author = "测试作者",
                inBookshelf = true,
                metaSyncedAtMs = 1_000L,
            ),
        )
        tocSyncRepository.syncNovelIndexAtomically(
            aid = 2200,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 2_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 701,
                    volumeTitle = "第一卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 7001, chapterTitle = "终章"),
                    ),
                ),
                ParsedVolumeToc(
                    vid = 702,
                    volumeTitle = "第二卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 8001, chapterTitle = "第一章 新篇"),
                    ),
                ),
            ),
        )
        // Short 2-page chapter for Volume 1 end so flipping to page 2 triggers 70% prefetch of Volume 2 (`8001`).
        val vol1EndBody = buildString {
            appendLine("第一卷 终章")
            repeat(12) { idx ->
                appendLine("　　第一卷终章第${idx + 1}段测试正文，用于验证跨卷翻页不会闪回上一章最后一页。")
            }
        }
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 7001,
                aid = 2200,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = vol1EndBody,
                byteLength = vol1EndBody.toByteArray().size,
                sha256Checksum = "sha_7001",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )

        val nextVolumeGate = CompletableDeferred<String>()
        fakeGateway.gatedChapterResponses[8001] = nextVolumeGate

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 2200,
                        vid = 701,
                        cid = 7001,
                        novelTitle = "跨卷翻页测试",
                        volumeTitle = "第一卷",
                        chapterTitle = "终章",
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

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag("${ReaderTestTags.PAGE_CANVAS_PREFIX}CURRENT_0")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.waitForIdle()

        // Advance through Volume 1's pages until `LOADING_SLOT` is the immediate next page.
        var guardSteps = 0
        while (guardSteps < 8 &&
            composeTestRule.onAllNodesWithTag(ReaderTestTags.LOADING_SLOT).fetchSemanticsNodes().isEmpty()
        ) {
            composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
                click(Offset(width * 0.85f, centerY))
            }
            composeTestRule.waitForIdle()
            guardSteps++
        }

        // Now we are on the LAST page of `7001`, and 70% prefetch for `8001` is waiting on `nextVolumeGate`.
        assertEquals(1, fakeGateway.fetchCountByCid[8001] ?: 0)

        composeTestRule.mainClock.autoAdvance = false
        try {
            // Tap right zone to flip across volume boundary onto `8001` (`Loading` slot).
            composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
                click(Offset(width * 0.85f, centerY))
            }
            composeTestRule.mainClock.advanceTimeBy(80)

            // Complete `8001` background prefetch MID-FLIP (`totalVirtualPages` expands while activeVirtualPageIndex is still on `7001`!).
            val vol2StartBody = buildString {
                appendLine("第二卷 第一章 新篇")
                repeat(24) { idx ->
                    appendLine("　　第二卷第一章第${idx + 1}段测试正文，跨卷加载完成后应稳定停留在新卷首页。")
                }
            }
            nextVolumeGate.complete(vol2StartBody)

            // Advance frame-by-frame to completion.
            repeat(30) {
                composeTestRule.mainClock.advanceTimeBy(32)
            }
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()

        // Coalesced AST fetch: `8001` was fetched over the network ONLY ONCE, and reader landed on `8001` Page 1!
        assertEquals(1, fakeGateway.fetchCountByCid[8001] ?: 0)
        val savedProgress = database.readingProgressDao().getProgressForNovel(2200)
        assertNotNull(savedProgress)
        assertEquals(8001, savedProgress?.cid)
        assertEquals(0, savedProgress?.paragraphIndex)
        composeTestRule.onNodeWithTag("${ReaderTestTags.PAGE_CANVAS_PREFIX}CURRENT_0").assertIsDisplayed()
    }

    @Test
    fun openingFirstPage_fastFlipToPageTwoWhilePrevChapterLoads_staysOnPageTwo(): Unit = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 2300,
                title = "快速翻页防回跳测试",
                author = "测试作者",
                inBookshelf = true,
                metaSyncedAtMs = 1_000L,
            ),
        )
        tocSyncRepository.syncNovelIndexAtomically(
            aid = 2300,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 2_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 901,
                    volumeTitle = "第一卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 9001, chapterTitle = "第一章 前篇"),
                        ParsedChapterToc(cid = 9002, chapterTitle = "第二章 本篇"),
                    ),
                ),
            ),
        )
        // Cache ONLY Chapter 2 (`9002`) locally; keep Chapter 1 (`9001`, `prevSlot`) gated on network!
        val ch2Body = buildString {
            appendLine("第一卷 第二章 本篇")
            repeat(60) { idx ->
                appendLine("　　第二章本篇第${idx + 1}段测试正文，用于验证刚打开第一页立即向后翻页不会被前章加载拉回第一页。")
            }
        }
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 9002,
                aid = 2300,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = ch2Body,
                byteLength = ch2Body.toByteArray().size,
                sha256Checksum = "sha_9002",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )

        val prevChapterGate = CompletableDeferred<String>()
        fakeGateway.gatedChapterResponses[9001] = prevChapterGate

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 2300,
                        vid = 901,
                        cid = 9002,
                        novelTitle = "快速翻页防回跳测试",
                        volumeTitle = "第一卷",
                        chapterTitle = "第二章 本篇",
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

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag("${ReaderTestTags.PAGE_CANVAS_PREFIX}CURRENT_0")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.mainClock.autoAdvance = false
        try {
            // Immediately tap NEXT on Page 1 -> Page 2 while `prevSlot` (`9001`) is still downloading!
            composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
                click(Offset(width * 0.85f, centerY))
            }
            composeTestRule.mainClock.advanceTimeBy(64)

            // Now `prevSlot` (`9001`) finishes downloading a large multi-page chapter mid-animation!
            val ch1LongBody = buildString {
                appendLine("第一卷 第一章 前篇")
                repeat(40) { idx ->
                    appendLine("　　第一章前篇第${idx + 1}段测试正文，占满十余页从而触发大幅虚拟页码偏移。")
                }
            }
            prevChapterGate.complete(ch1LongBody)

            repeat(30) {
                composeTestRule.mainClock.advanceTimeBy(32)
            }
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()

        // Reader MUST stay on Chapter 2 Page 2 (`CURRENT_1`), NOT snap back to Page 1 (`CURRENT_0`)!
        composeTestRule.onNodeWithTag("${ReaderTestTags.PAGE_CANVAS_PREFIX}CURRENT_1").assertIsDisplayed()
        val progressAfterPage2 = database.readingProgressDao().getProgressForNovel(2300)
        assertEquals(9002, progressAfterPage2?.cid)
        assertTrue(
            "Expected Page 2 paragraphIndex > 0, got ${progressAfterPage2?.paragraphIndex}",
            (progressAfterPage2?.paragraphIndex ?: 0) > 0,
        )

        // Rapid double-tap (`Page 2 -> Page 3 -> Page 4`) advances cumulatively without losing the second tap!
        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
                click(Offset(width * 0.85f, centerY))
            }
            composeTestRule.mainClock.advanceTimeBy(48)
            composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
                click(Offset(width * 0.85f, centerY))
            }
            repeat(30) {
                composeTestRule.mainClock.advanceTimeBy(32)
            }
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("${ReaderTestTags.PAGE_CANVAS_PREFIX}CURRENT_3").assertIsDisplayed()
    }

    @Test
    fun unifiedReaderScreen_emptyServerChapterShowsClearMessageAndOpensWebReaderUrl() {
        runBlocking {
            database.novelDao().upsertNovel(
                NovelEntity(aid = 4358, title = "魔法使之夜 小说", author = "奈须蘑菇"),
            )
            tocSyncRepository.syncNovelIndexAtomically(
                aid = 4358,
                language = Wenku8API.AppLanguage.SC,
                requestStartedAtMs = 6_000L,
                parsedVolumes = listOf(
                    ParsedVolumeToc(
                        vid = 183107,
                        volumeTitle = "第三卷 下",
                        chapters = listOf(
                            ParsedChapterToc(cid = 183108, chapterTitle = "8.5 鸭内基事件"),
                            ParsedChapterToc(cid = 183223, chapterTitle = "9 橙色的魔法使"),
                        ),
                    ),
                ),
            )
        }
        fakeGateway.customChapterResults[183108] = WenkuApiResult.EmptyResponse
        fakeGateway.customChapterResults[183223] = WenkuApiResult.Success(
            "第三卷 下 9 橙色的魔法使\n\n　　三咲市的暮色沉入丘陵之中。",
        )

        val openedUrls = mutableListOf<String>()
        var currentPrefs by mutableStateOf(Wenku8UserPreferences())

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 4358,
                        vid = 183107,
                        cid = 183108,
                        novelTitle = "魔法使之夜 小说",
                        volumeTitle = "第三卷 下",
                        chapterTitle = "8.5 鸭内基事件",
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = currentPrefs,
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onNavigateBack = {},
                    onOpenExternalUrl = { openedUrls.add(it) },
                    lineBreaker = DeterministicCellLineBreaker,
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.ERROR_SLOT)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithTag(ReaderTestTags.ERROR_SLOT).assertIsDisplayed()
        composeTestRule.onNodeWithText("8.5 鸭内基事件").assertIsDisplayed()
        composeTestRule.onNodeWithText(ChapterWindowBuffer.EMPTY_CHAPTER_CONTENT_MESSAGE).assertIsDisplayed()
        composeTestRule.onNodeWithText("重试加载").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReaderTestTags.ERROR_OPEN_WEB_BUTTON).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("网络检测").assertCountEquals(0)
        composeTestRule.onAllNodesWithText("章节数据校验异常").assertCountEquals(0)

        composeTestRule.onNodeWithTag(ReaderTestTags.ERROR_OPEN_WEB_BUTTON).performClick()
        assertEquals(listOf("https://www.wenku8.net/novel/4/4358/183108.htm"), openedUrls)

        // Switching to continuous scroll mode must also forward onOpenChapterInBrowser cleanly.
        currentPrefs = currentPrefs.copy(readerPageTurnMode = ReaderPageTurnMode.CONTINUOUS_SCROLL)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ReaderTestTags.ERROR_OPEN_WEB_BUTTON).performClick()
        assertEquals(
            listOf(
                "https://www.wenku8.net/novel/4/4358/183108.htm",
                "https://www.wenku8.net/novel/4/4358/183108.htm",
            ),
            openedUrls,
        )
    }

    @Test
    fun unifiedReaderScreen_parseErrorShowsDataValidationMessageAndNetworkDiagnostics() {
        runBlocking {
            database.novelDao().upsertNovel(
                NovelEntity(aid = 4358, title = "魔法使之夜 小说", author = "奈须蘑菇"),
            )
            tocSyncRepository.syncNovelIndexAtomically(
                aid = 4358,
                language = Wenku8API.AppLanguage.SC,
                requestStartedAtMs = 6_100L,
                parsedVolumes = listOf(
                    ParsedVolumeToc(
                        vid = 183107,
                        volumeTitle = "第三卷 下",
                        chapters = listOf(
                            ParsedChapterToc(cid = 183108, chapterTitle = "8.5 鸭内基事件"),
                        ),
                    ),
                ),
            )
        }
        fakeGateway.customChapterResults[183108] = WenkuApiResult.ParseError("<html>Welcome</html>")

        var diagnosticsClicks = 0
        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 4358,
                        vid = 183107,
                        cid = 183108,
                        novelTitle = "魔法使之夜 小说",
                        volumeTitle = "第三卷 下",
                        chapterTitle = "8.5 鸭内基事件",
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = Wenku8UserPreferences(),
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onNavigateBack = {},
                    onOpenNetworkDiagnostics = { diagnosticsClicks += 1 },
                    lineBreaker = DeterministicCellLineBreaker,
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.ERROR_SLOT)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.onNodeWithText("章节数据校验异常").assertIsDisplayed()
        composeTestRule.onAllNodesWithTag(ReaderTestTags.ERROR_OPEN_WEB_BUTTON).assertCountEquals(0)
        composeTestRule.onNodeWithText("网络检测").assertIsDisplayed().performClick()
        assertEquals(1, diagnosticsClicks)
    }

    @Test
    fun resolveReaderVisualPalette_enablesSimulatedParchmentTextureOnlyForParchmentDayWithoutCustomBgOrEink() {
        val parchmentDefault = resolveReaderVisualPalette(
            paperPreset = ReaderPaperPreset.PARCHMENT,
            isEinkMode = false,
            customBackgroundPath = "",
            customFontPath = "",
        )
        assertTrue(parchmentDefault.useSimulatedParchmentTexture)
        assertNull(parchmentDefault.customDayBackgroundFile)

        val parchmentEink = resolveReaderVisualPalette(
            paperPreset = ReaderPaperPreset.PARCHMENT,
            isEinkMode = true,
            customBackgroundPath = "",
            customFontPath = "",
        )
        assertFalse(parchmentEink.useSimulatedParchmentTexture)
        assertTrue(parchmentEink.isEinkMonochrome)

        val tempCustomBg = File.createTempFile("custom_day_bg_", ".jpg").apply {
            writeBytes(byteArrayOf(1, 2, 3, 4))
            deleteOnExit()
        }
        try {
            val parchmentWithCustomBg = resolveReaderVisualPalette(
                paperPreset = ReaderPaperPreset.PARCHMENT,
                isEinkMode = false,
                customBackgroundPath = tempCustomBg.absolutePath,
                customFontPath = "",
            )
            assertFalse(parchmentWithCustomBg.useSimulatedParchmentTexture)
            assertNotNull(parchmentWithCustomBg.customDayBackgroundFile)
        } finally {
            tempCustomBg.delete()
        }

        listOf(
            ReaderPaperPreset.PURE_WHITE,
            ReaderPaperPreset.EYE_CARE_GREEN,
            ReaderPaperPreset.CARBON_NIGHT,
            ReaderPaperPreset.PURE_AMOLED,
        ).forEach { preset ->
            val resolved = resolveReaderVisualPalette(
                paperPreset = preset,
                isEinkMode = false,
                customBackgroundPath = "",
                customFontPath = "",
            )
            assertFalse("Expected no parchment texture for $preset", resolved.useSimulatedParchmentTexture)
        }
    }

    @Test
    fun readerParchmentAssetCache_loadsAppV1PaperTileAndEdgeVignetteAssets() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val assets = ReaderParchmentAssetCache.load(context = context, density = 2.75f)
        assertNotNull("Expected app-v1 parchment paper tile to decode cleanly", assets)
        assertNotNull("Expected app-v1 parchment edge vignette to decode cleanly", assets?.edgeVignetteBitmap)
    }

    @Test
    fun readerViewportHost_extendsPageTurnLayerThroughDisplayCutoutWhileKeepingTextInset() {
        val chapter = OrderedChapterWithVolume(
            aid = 1587,
            vid = 501,
            volumeOrder = 0,
            volumeTitle = "第一卷",
            cid = 6001,
            chapterOrder = 0,
            chapterTitle = "第一章 序章",
            isIllustrationChapter = false,
        )
        val rawBody = buildString {
            appendLine("第一卷 第一章 序章")
            repeat(18) { idx ->
                appendLine("　　这是第${idx + 1}段正文，用于验证顶部状态栏挖孔区域保留完整翻页阴影且正文严格避让。")
            }
        }
        val ast = WenkuChapterParser.parse(
            rawChapterText = rawBody,
            aid = chapter.aid,
            vid = chapter.vid,
            cid = chapter.cid,
            volumeTitle = chapter.volumeTitle,
            chapterTitle = chapter.chapterTitle,
        )
        val config = ReaderLayoutConfig(
            viewportWidthPx = 412,
            viewportHeightPx = 844,
            fontSizePx = 18f,
            lineDistancePx = 10f,
            paragraphDistancePx = 12f,
            pageEdgeDistancePx = 16f,
        )
        val layout = StaticLayoutPaginator(DeterministicCellLineBreaker).paginate(ast, config)
        val snapshot = ReaderWindowSnapshot(
            aid = chapter.aid,
            orderedToc = listOf(chapter),
            currentTocIndex = 0,
            currentSlot = ChapterWindowSlot.Loaded(chapter = chapter, layout = layout),
            layoutConfig = config,
            activeVirtualPageIndex = 0,
        )
        val palette = resolveReaderVisualPalette(
            paperPreset = ReaderPaperPreset.PARCHMENT,
            isEinkMode = false,
            customBackgroundPath = "",
            customFontPath = "",
        )
        val simulatedTopCutout = 48.dp
        var mode by mutableStateOf(ReaderPageTurnMode.COVER_SLIDE)

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                val pagerState = rememberPagerState(initialPage = 0) { snapshot.totalVirtualPages.coerceAtLeast(1) }
                ReaderViewportHost(
                    pageTurnMode = mode,
                    snapshot = snapshot,
                    pagerState = pagerState,
                    palette = palette,
                    fontSizeSp = 18,
                    lineDistanceDp = 10,
                    paragraphDistanceDp = 12,
                    pageEdgeDistanceDp = 16,
                    leftEdgeBrightnessDragEnabled = true,
                    contentSafeInsets = WindowInsets(top = simulatedTopCutout),
                    onTapZoneAction = {},
                    onBrightnessDragDelta = {},
                    onIllustrationClicked = { _, _ -> },
                    onContinuousScrollAnchorChanged = { _, _ -> },
                    onRequestNextChapterInContinuousMode = {},
                    onRequestPrevChapterInContinuousMode = {},
                    onRetryFailedSlot = {},
                    onOpenNetworkDiagnostics = {},
                )
            }
        }

        composeTestRule.waitForIdle()

        // Paged mode: viewport & HorizontalPager (which hosts page-turn shadow/curl) start at y=0,
        // while the page text/header/footer canvas is inset cleanly by the 48.dp top cutout.
        val viewportBounds = composeTestRule
            .onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER)
            .getUnclippedBoundsInRoot()
        val pagerBounds = composeTestRule
            .onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER)
            .getUnclippedBoundsInRoot()
        val pageCanvasBounds = composeTestRule
            .onNodeWithTag("${ReaderTestTags.PAGE_CANVAS_PREFIX}CURRENT_0")
            .getUnclippedBoundsInRoot()

        assertEquals(0f, viewportBounds.top.value, 0.5f)
        assertEquals(0f, pagerBounds.top.value, 0.5f)
        assertEquals(viewportBounds.bottom.value, pagerBounds.bottom.value, 0.5f)
        assertEquals(simulatedTopCutout.value, pageCanvasBounds.top.value, 1f)

        // Continuous scroll mode: background container still starts at y=0 while the scroll list stays inset.
        mode = ReaderPageTurnMode.CONTINUOUS_SCROLL
        composeTestRule.waitForIdle()

        val scrollViewportBounds = composeTestRule
            .onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER)
            .getUnclippedBoundsInRoot()
        val scrollListBounds = composeTestRule
            .onNodeWithTag(ReaderTestTags.CONTINUOUS_SCROLL_LIST)
            .getUnclippedBoundsInRoot()
        assertEquals(0f, scrollViewportBounds.top.value, 0.5f)
        assertEquals(simulatedTopCutout.value, scrollListBounds.top.value, 1f)
    }

    @Test
    fun lowerPageRevealClip_partitionsOverlappingTransitionsWithoutOverlap() {
        // Settled, off-stage, upper-page, and degenerate widths require no lower-page reveal clip.
        assertNull(computeLowerPageRevealClip(pageOffset = 0f, viewportWidthPx = 1000f))
        assertNull(computeLowerPageRevealClip(pageOffset = -1f, viewportWidthPx = 1000f))
        assertNull(computeLowerPageRevealClip(pageOffset = -0.999f, viewportWidthPx = 1000f))
        assertNull(computeLowerPageRevealClip(pageOffset = 0.25f, viewportWidthPx = 1000f))
        assertNull(computeLowerPageRevealClip(pageOffset = -0.5f, viewportWidthPx = 0f))

        // Issue #205 exact geometry (~3.2% left shift -> incoming pageOffset = -0.968f):
        // lower page is clipped strictly to [968, 1000], matching the cast shadow's startX.
        val clipNearStart = computeLowerPageRevealClip(pageOffset = -0.968f, viewportWidthPx = 1000f)
        val shadowNearStart = computeCoverSlideShadowSpec(
            pageOffset = -0.968f,
            viewportWidthPx = 1000f,
            shadowWidthPx = 24f,
        )
        assertNotNull(clipNearStart)
        assertNotNull(shadowNearStart)
        assertEquals(968f, clipNearStart!!.leftX, 0.01f)
        assertEquals(1000f, clipNearStart.rightX, 0.01f)
        assertEquals(shadowNearStart!!.startX, clipNearStart.leftX, 0.01f)

        // Mid-turn (50%) and near-finish (90%): upper page covers [0, (1 - p) * W] and lower page
        // is clipped to [(1 - p) * W, W], forming a strictly disjoint horizontal partition.
        val clipMid = computeLowerPageRevealClip(pageOffset = -0.50f, viewportWidthPx = 1000f)
        val clipLate = computeLowerPageRevealClip(pageOffset = -0.10f, viewportWidthPx = 1000f)
        assertEquals(500f, clipMid!!.leftX, 0.01f)
        assertEquals(1000f, clipMid.rightX, 0.01f)
        assertEquals(100f, clipLate!!.leftX, 0.01f)
        assertEquals(1000f, clipLate.rightX, 0.01f)
    }

    @Test
    fun customDayWallpaper_midTurnInCoverSlideAndSimulationCurl_neverBleedsLowerPageIntoUpperRegion() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val customBgFile = File.createTempFile("wenku8_issue205_custom_bg_", ".png", context.cacheDir)
        try {
            // Write a valid solid warm-cream custom day wallpaper image.
            val bgBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(AndroidColor.rgb(245, 232, 210))
            }
            customBgFile.outputStream().use { out ->
                bgBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bgBitmap.recycle()

            val chapter = OrderedChapterWithVolume(
                aid = 2050,
                vid = 601,
                volumeOrder = 0,
                volumeTitle = "第二卷",
                cid = 6050,
                chapterOrder = 0,
                chapterTitle = "第六章 绯红的誓言",
                isIllustrationChapter = false,
            )
            val config = ReaderLayoutConfig(
                viewportWidthPx = 412,
                viewportHeightPx = 892,
                fontSizePx = 28f,
                lineDistancePx = 10f,
                paragraphDistancePx = 12f,
                pageEdgeDistancePx = 16f,
            )
            val ast = WenkuChapterParser.parse(
                rawChapterText = "第二卷 第六章 绯红的誓言\n　　测试正文。",
                aid = chapter.aid,
                vid = chapter.vid,
                cid = chapter.cid,
                volumeTitle = chapter.volumeTitle,
                chapterTitle = chapter.chapterTitle,
            )
            // Page 0 (upper sliding page): intentionally empty so ANY dark ink in [0, 0.85 * W]
            // can only come from illegal bleed-through of Page 1 underneath!
            val emptyUpperPage = ReaderPage(
                cid = chapter.cid,
                pageIndexInChapter = 0,
                totalPagesInChapter = 2,
                startAnchor = ReadingAnchor(paragraphIndex = 0, charOffset = 0, progressPermille = 0),
                endAnchor = ReadingAnchor(paragraphIndex = 0, charOffset = 0, progressPermille = 0),
                characterCountOnPage = 0,
                isFullBleedArtbookPage = true,
                elements = emptyList(),
            )
            // Page 1 (pinned lower page): dense dark ink spanning the full viewport width.
            val denseLowerElements = (0 until 6).map { row ->
                val topY = 240f + row * 36f
                ReaderPageElement.TextLine(
                    blockIndex = 1,
                    rawStartChar = row * 20,
                    rawEndChar = (row + 1) * 20,
                    text = "████████████████████████████████",
                    xPx = 0f,
                    topYPx = topY,
                    baselineYPx = topY + 26f,
                    heightPx = 32f,
                    fontSizePx = 28f,
                )
            }
            val denseLowerPage = ReaderPage(
                cid = chapter.cid,
                pageIndexInChapter = 1,
                totalPagesInChapter = 2,
                startAnchor = ReadingAnchor(paragraphIndex = 1, charOffset = 0, progressPermille = 1000),
                endAnchor = ReadingAnchor(paragraphIndex = 1, charOffset = 120, progressPermille = 1000),
                characterCountOnPage = 120,
                isFullBleedArtbookPage = true,
                elements = denseLowerElements,
            )
            val layout = PaginatedChapterLayout(
                ast = ast,
                config = config,
                pages = listOf(emptyUpperPage, denseLowerPage),
            )
            val snapshot = ReaderWindowSnapshot(
                aid = chapter.aid,
                orderedToc = listOf(chapter),
                currentTocIndex = 0,
                currentSlot = ChapterWindowSlot.Loaded(chapter = chapter, layout = layout),
                layoutConfig = config,
                activeVirtualPageIndex = 0,
            )
            val palette = resolveReaderVisualPalette(
                paperPreset = ReaderPaperPreset.PARCHMENT,
                isEinkMode = false,
                customBackgroundPath = customBgFile.absolutePath,
                customFontPath = "",
            )
            assertNotNull(palette.customDayBackgroundFile)

            var mode by mutableStateOf(ReaderPageTurnMode.COVER_SLIDE)
            composeTestRule.setContent {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    // Hold pager mid-turn at 15% forward swipe: upper Page 0 covers [0, 0.85 * W],
                    // lower Page 1 is pinned at [0, W] and must ONLY be visible in [0.85 * W, W].
                    val pagerState = rememberPagerState(
                        initialPage = 0,
                        initialPageOffsetFraction = 0.15f,
                    ) { snapshot.totalVirtualPages }
                    ReaderViewportHost(
                        pageTurnMode = mode,
                        snapshot = snapshot,
                        pagerState = pagerState,
                        palette = palette,
                        fontSizeSp = 28,
                        lineDistanceDp = 10,
                        paragraphDistanceDp = 12,
                        pageEdgeDistanceDp = 16,
                        leftEdgeBrightnessDragEnabled = false,
                        contentSafeInsets = WindowInsets(0, 0, 0, 0),
                        onTapZoneAction = {},
                        onBrightnessDragDelta = {},
                        onIllustrationClicked = { _, _ -> },
                        onContinuousScrollAnchorChanged = { _, _ -> },
                        onRequestNextChapterInContinuousMode = {},
                        onRequestPrevChapterInContinuousMode = {},
                        onRetryFailedSlot = {},
                        onOpenNetworkDiagnostics = {},
                    )
                }
            }

            fun countDarkInkPixels(bitmap: Bitmap, xStart: Int, xEnd: Int, yStart: Int, yEnd: Int): Int {
                var count = 0
                for (y in yStart until yEnd) {
                    for (x in xStart until xEnd) {
                        val px = bitmap.getPixel(x, y)
                        val luma = (AndroidColor.red(px) + AndroidColor.green(px) + AndroidColor.blue(px)) / 3
                        if (luma < 120) count++
                    }
                }
                return count
            }

            for (testMode in listOf(ReaderPageTurnMode.COVER_SLIDE, ReaderPageTurnMode.SIMULATION_CURL)) {
                mode = testMode
                composeTestRule.waitForIdle()
                val rendered = composeTestRule
                    .onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER)
                    .captureToImage()
                    .asAndroidBitmap()
                val w = rendered.width
                val h = rendered.height
                val yStart = (240f.toInt()).coerceIn(0, h - 1)
                val yEnd = (450f.toInt()).coerceIn(yStart + 1, h)
                // Upper-page covered region (well left of 0.85 * W and SIMULATION_CURL crease):
                // MUST have zero dark ink bleeding through from Page 1 underneath!
                val upperRegionInk = countDarkInkPixels(
                    bitmap = rendered,
                    xStart = (w * 0.10f).toInt(),
                    xEnd = (w * 0.65f).toInt(),
                    yStart = yStart,
                    yEnd = yEnd,
                )
                assertEquals(
                    "Lower page ink bled through transparent custom-wallpaper upper page in $testMode!",
                    0,
                    upperRegionInk,
                )

                // Revealed right-hand strip [0.88 * W, 0.98 * W]: lower Page 1 MUST render cleanly!
                val revealedStripInk = countDarkInkPixels(
                    bitmap = rendered,
                    xStart = (w * 0.88f).toInt(),
                    xEnd = (w * 0.98f).toInt(),
                    yStart = yStart,
                    yEnd = yEnd,
                )
                assertTrue(
                    "Expected revealed lower page ink in right strip for $testMode, got $revealedStripInk",
                    revealedStripInk > 0,
                )
            }
        } finally {
            customBgFile.delete()
        }
    }

    @Test
    fun manualSwipe_whilePrevChapterLoads_settlesCleanlyWithoutStrandedFractionalOffset(): Unit = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 2305,
                title = "手势滑页防卡顿偏移测试",
                author = "测试作者",
                inBookshelf = true,
                metaSyncedAtMs = 1_000L,
            ),
        )
        tocSyncRepository.syncNovelIndexAtomically(
            aid = 2305,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 2_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 905,
                    volumeTitle = "第一卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 9051, chapterTitle = "第一章 前篇"),
                        ParsedChapterToc(cid = 9052, chapterTitle = "第二章 本篇"),
                    ),
                ),
            ),
        )
        val ch2Body = buildString {
            appendLine("第一卷 第二章 本篇")
            repeat(60) { idx ->
                appendLine("　　第二章本篇第${idx + 1}段测试正文，用于验证手势拖动期间前章加载完成不会导致页面永久停留在小数偏移。")
            }
        }
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 9052,
                aid = 2305,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = ch2Body,
                byteLength = ch2Body.toByteArray().size,
                sha256Checksum = "sha_9052",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )

        val prevChapterGate = CompletableDeferred<String>()
        fakeGateway.gatedChapterResponses[9051] = prevChapterGate

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 2305,
                        vid = 905,
                        cid = 9052,
                        novelTitle = "手势滑页防卡顿偏移测试",
                        volumeTitle = "第一卷",
                        chapterTitle = "第二章 本篇",
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

        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag("${ReaderTestTags.PAGE_CANVAS_PREFIX}CURRENT_0")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        composeTestRule.mainClock.autoAdvance = false
        try {
            // Start a small manual horizontal drag (~12% viewport width) while `prevSlot` is still loading.
            composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
                down(Offset(width * 0.70f, centerY))
                moveBy(Offset(-width * 0.12f, 0f), delayMillis = 32)
            }
            composeTestRule.mainClock.advanceTimeBy(32)

            // `prevSlot` finishes loading mid-drag, triggering `isPrevSlotTranslationOnly` + `requestScrollToPage`!
            val ch1LongBody = buildString {
                appendLine("第一卷 第一章 前篇")
                repeat(40) { idx ->
                    appendLine("　　第一章前篇第${idx + 1}段测试正文，占满十余页从而触发大幅虚拟页码偏移。")
                }
            }
            prevChapterGate.complete(ch1LongBody)
            composeTestRule.mainClock.advanceTimeBy(32)

            composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
                up()
            }
            repeat(40) {
                composeTestRule.mainClock.advanceTimeBy(32)
            }
        } finally {
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()

        // The settled page canvas MUST align flush with x = 0.dp (zero stranded fractional offset)!
        val settledBounds = composeTestRule
            .onNodeWithTag("${ReaderTestTags.PAGE_CANVAS_PREFIX}CURRENT_0")
            .getUnclippedBoundsInRoot()
        assertEquals(
            "Expected CURRENT_0 to settle flush at left=0.dp without a stranded fractional offset",
            0f,
            settledBounds.left.value,
            0.5f,
        )
    }

    @Test
    fun dualColumnTapZones_useTwoHandedBookMarginsInLtrAndRtl() {
        val width = 1000f
        val height = 640f
        // LTR book spread: left 30% = PREV, middle 40% = HUD (all vertical rows), right 30% = NEXT.
        for (y in listOf(60f, 320f, 580f)) {
            assertEquals(
                ReaderTapZoneAction.PREV_PAGE,
                resolveNineZoneTapAction(150f, y, width, height, isRtlBook = false, isDualColumnSpread = true),
            )
            assertEquals(
                ReaderTapZoneAction.TOGGLE_HUD,
                resolveNineZoneTapAction(500f, y, width, height, isRtlBook = false, isDualColumnSpread = true),
            )
            assertEquals(
                ReaderTapZoneAction.NEXT_PAGE,
                resolveNineZoneTapAction(850f, y, width, height, isRtlBook = false, isDualColumnSpread = true),
            )
        }
        // RTL Bunkobon spread mirrors horizontal edges: left 30% = NEXT, middle 40% = HUD, right 30% = PREV.
        assertEquals(
            ReaderTapZoneAction.NEXT_PAGE,
            resolveNineZoneTapAction(150f, 320f, width, height, isRtlBook = true, isDualColumnSpread = true),
        )
        assertEquals(
            ReaderTapZoneAction.TOGGLE_HUD,
            resolveNineZoneTapAction(500f, 320f, width, height, isRtlBook = true, isDualColumnSpread = true),
        )
        assertEquals(
            ReaderTapZoneAction.PREV_PAGE,
            resolveNineZoneTapAction(850f, 320f, width, height, isRtlBook = true, isDualColumnSpread = true),
        )
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1024dp-h640dp")
    fun tabletLandscape_autoActivatesDualColumnSpread_constrainsHudAndAllowsSingleColumnToggle(): Unit = runBlocking {
        seedDualColumnChapter(aid = 2401, vid = 941, cid = 9411, novelTitle = "大屏双栏排版测试")

        var prefs by mutableStateOf(
            Wenku8UserPreferences(
                readerColumnMode = ReaderColumnMode.AUTO,
                readerPageTurnMode = ReaderPageTurnMode.COVER_SLIDE,
            ),
        )

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 2401,
                        vid = 941,
                        cid = 9411,
                        novelTitle = "大屏双栏排版测试",
                        volumeTitle = "第一卷",
                        chapterTitle = "第一章 双栏序幕",
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = prefs,
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onReaderColumnModeChanged = { newMode ->
                        prefs = prefs.copy(readerColumnMode = newMode)
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
                .onAllNodesWithTag(ReaderTestTags.DUAL_COLUMN_SPINE_DIVIDER)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag(ReaderTestTags.DUAL_COLUMN_SPINE_DIVIDER).assertIsDisplayed()

        // Tap center spine region to toggle HUD; scrubber card must be constrained to <= 640dp.
        composeTestRule.onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER).performTouchInput {
            click(Offset(width * 0.50f, height * 0.50f))
        }
        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.HUD_SCRUBBER_CARD)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        val scrubberBounds = composeTestRule
            .onNodeWithTag(ReaderTestTags.HUD_SCRUBBER_CARD)
            .getUnclippedBoundsInRoot()
        val scrubberWidthDp = scrubberBounds.right.value - scrubberBounds.left.value
        assertTrue(
            "Expected wide-screen scrubber card width <= 640dp, got ${scrubberWidthDp}dp",
            scrubberWidthDp <= 641f,
        )

        // Open Typography Studio and switch to SINGLE column; center spine divider must disappear.
        composeTestRule.onNodeWithTag(ReaderTestTags.ACTION_TYPOGRAPHY).performClick()
        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag("${ReaderTestTags.COLUMN_MODE_CHIP_PREFIX}${ReaderColumnMode.SINGLE.name}")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule
            .onNodeWithTag("${ReaderTestTags.COLUMN_MODE_CHIP_PREFIX}${ReaderColumnMode.SINGLE.name}")
            .performClick()
        composeTestRule.waitUntil(5_000) {
            prefs.readerColumnMode == ReaderColumnMode.SINGLE &&
                composeTestRule
                    .onAllNodesWithTag(ReaderTestTags.DUAL_COLUMN_SPINE_DIVIDER)
                    .fetchSemanticsNodes()
                    .isEmpty()
        }
        assertEquals(ReaderColumnMode.SINGLE, prefs.readerColumnMode)
        composeTestRule
            .onAllNodesWithTag(ReaderTestTags.DUAL_COLUMN_SPINE_DIVIDER)
            .assertCountEquals(0)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w892dp-h412dp")
    fun phoneLandscape_autoStaysSingleColumn_andExplicitDualActivatesSpread(): Unit = runBlocking {
        seedDualColumnChapter(aid = 2402, vid = 942, cid = 9421, novelTitle = "普通横屏防双栏测试")

        var prefs by mutableStateOf(
            Wenku8UserPreferences(
                readerColumnMode = ReaderColumnMode.AUTO,
                readerPageTurnMode = ReaderPageTurnMode.COVER_SLIDE,
            ),
        )

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 2402,
                        vid = 942,
                        cid = 9421,
                        novelTitle = "普通横屏防双栏测试",
                        volumeTitle = "第一卷",
                        chapterTitle = "第一章 横屏测试",
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = prefs,
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onReaderColumnModeChanged = { newMode ->
                        prefs = prefs.copy(readerColumnMode = newMode)
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
                .onAllNodesWithTag("${ReaderTestTags.PAGE_CANVAS_PREFIX}CURRENT_0")
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        // Issue #204: ordinary phone landscape MUST remain single-column under AUTO.
        composeTestRule
            .onAllNodesWithTag(ReaderTestTags.DUAL_COLUMN_SPINE_DIVIDER)
            .assertCountEquals(0)

        // Explicit DUAL user override activates dual-column spread when columns still fit comfortably.
        prefs = prefs.copy(readerColumnMode = ReaderColumnMode.DUAL)
        composeTestRule.waitUntil(5_000) {
            composeTestRule
                .onAllNodesWithTag(ReaderTestTags.DUAL_COLUMN_SPINE_DIVIDER)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeTestRule.onNodeWithTag(ReaderTestTags.DUAL_COLUMN_SPINE_DIVIDER).assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1024dp-h640dp")
    fun continuousScroll_onTabletLandscape_centersReadableColumnAtMax680dp(): Unit = runBlocking {
        seedDualColumnChapter(aid = 2403, vid = 943, cid = 9431, novelTitle = "大屏卷轴行宽测试")

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                UnifiedReaderScreen(
                    launchRequest = ReaderLaunchRequest(
                        aid = 2403,
                        vid = 943,
                        cid = 9431,
                        novelTitle = "大屏卷轴行宽测试",
                        volumeTitle = "第一卷",
                        chapterTitle = "第一章 卷轴居中",
                    ),
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = Wenku8UserPreferences(
                        readerColumnMode = ReaderColumnMode.AUTO,
                        readerPageTurnMode = ReaderPageTurnMode.CONTINUOUS_SCROLL,
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
        val listBounds = composeTestRule
            .onNodeWithTag(ReaderTestTags.CONTINUOUS_SCROLL_LIST)
            .getUnclippedBoundsInRoot()
        val listWidthDp = listBounds.right.value - listBounds.left.value
        assertTrue(
            "Expected continuous scroll list width <= 680dp on 1024dp tablet, got ${listWidthDp}dp",
            listWidthDp <= 681f,
        )
        assertTrue(
            "Expected continuous scroll list to be centered with horizontal margins, left=${listBounds.left.value}dp",
            listBounds.left.value > 100f,
        )
        composeTestRule
            .onAllNodesWithTag(ReaderTestTags.DUAL_COLUMN_SPINE_DIVIDER)
            .assertCountEquals(0)
    }

    @Test
    fun dualColumnBookFlipSpec_partitionsDisjointlyAcrossForwardMidpointBackwardAndAsymmetricHinge() {
        val width = 1000f
        val centerSpine = 500f

        // 1. Settled and off-stage bounds
        assertEquals(
            DualColumnFlipLayerRole.SETTLED,
            computeDualColumnBookFlipSpec(0f, width, centerSpine).role,
        )
        assertEquals(
            DualColumnFlipLayerRole.OFF_STAGE,
            computeDualColumnBookFlipSpec(1f, width, centerSpine).role,
        )
        assertEquals(
            DualColumnFlipLayerRole.OFF_STAGE,
            computeDualColumnBookFlipSpec(-1f, width, centerSpine).role,
        )
        assertEquals(
            DualColumnFlipLayerRole.OFF_STAGE,
            computeDualColumnBookFlipSpec(0.25f, 0f, centerSpine).role,
        )

        // 2. First half (p = 0.25f): k folds right leaf, k + 1 reveals [outerEdgeX, W] with cast shadow.
        val kFirstHalf = computeDualColumnBookFlipSpec(0.25f, width, centerSpine)
        val kNextFirstHalf = computeDualColumnBookFlipSpec(-0.75f, width, centerSpine)
        assertEquals(DualColumnFlipLayerRole.STATIC_LEFT_WITH_FOLDING_RIGHT_LEAF, kFirstHalf.role)
        assertEquals(DualColumnFlipLayerRole.REVEALED_RIGHT_UNDERNEATH, kNextFirstHalf.role)
        assertEquals(0f, kFirstHalf.flatClipLeftX, 0.01f)
        assertEquals(centerSpine, kFirstHalf.flatClipRightX, 0.01f)
        val expectedCos45 = kotlin.math.cos(kotlin.math.PI / 4.0).toFloat()
        assertEquals(expectedCos45, kFirstHalf.leafScaleX, 0.001f)
        assertEquals(kFirstHalf.leafOuterEdgeX, kNextFirstHalf.flatClipLeftX, 0.01f)
        assertEquals(width, kNextFirstHalf.flatClipRightX, 0.01f)
        assertTrue(kNextFirstHalf.castShadowFadesRight)
        assertTrue(kNextFirstHalf.castShadowAlpha > 0.10f)
        assertTrue(kNextFirstHalf.castShadowRightX > kNextFirstHalf.castShadowLeftX)

        // 3. Exact midpoint (p = 0.50f): leaf stands vertical at spineX with continuous disjoint halves.
        val kMid = computeDualColumnBookFlipSpec(0.50f, width, centerSpine)
        val kNextMid = computeDualColumnBookFlipSpec(-0.50f, width, centerSpine)
        assertEquals(0f, kMid.leafScaleX, 0.001f)
        assertEquals(centerSpine, kMid.leafOuterEdgeX, 0.01f)
        assertEquals(centerSpine, kNextMid.flatClipLeftX, 0.01f)
        assertEquals(width, kNextMid.flatClipRightX, 0.01f)

        // 4. Second half (p = 0.75f, also symmetric to first quarter of backward drag from k + 1 -> k):
        // k stays underneath in [0, outerEdgeX] with left-fading shadow; k + 1 unfolds left leaf from spineX.
        val kSecondHalf = computeDualColumnBookFlipSpec(0.75f, width, centerSpine)
        val kNextSecondHalf = computeDualColumnBookFlipSpec(-0.25f, width, centerSpine)
        assertEquals(DualColumnFlipLayerRole.REVEALED_LEFT_UNDERNEATH, kSecondHalf.role)
        assertEquals(DualColumnFlipLayerRole.STATIC_RIGHT_WITH_UNFOLDING_LEFT_LEAF, kNextSecondHalf.role)
        val expectedSin45 = kotlin.math.sin(kotlin.math.PI / 4.0).toFloat()
        assertEquals(expectedSin45, kNextSecondHalf.leafScaleX, 0.001f)
        assertEquals(0f, kSecondHalf.flatClipLeftX, 0.01f)
        assertEquals(kNextSecondHalf.leafOuterEdgeX, kSecondHalf.flatClipRightX, 0.01f)
        assertEquals(centerSpine, kNextSecondHalf.flatClipLeftX, 0.01f)
        assertEquals(width, kNextSecondHalf.flatClipRightX, 0.01f)
        assertFalse(kSecondHalf.castShadowFadesRight)
        assertTrue(kSecondHalf.castShadowAlpha > 0.10f)
        assertTrue(kSecondHalf.castShadowRightX > kSecondHalf.castShadowLeftX)

        // 5. Asymmetric foldable hinge (spineX = 580f in 1000f viewport): both halves stay strictly disjoint.
        val asymSpine = 580f
        val asymLeftFirst = computeDualColumnBookFlipSpec(0.30f, width, asymSpine)
        val asymRightFirst = computeDualColumnBookFlipSpec(-0.70f, width, asymSpine)
        assertEquals(asymSpine, asymLeftFirst.spineX, 0.01f)
        assertEquals(asymLeftFirst.leafOuterEdgeX, asymRightFirst.flatClipLeftX, 0.01f)
        val asymLeftSecond = computeDualColumnBookFlipSpec(0.70f, width, asymSpine)
        val asymRightSecond = computeDualColumnBookFlipSpec(-0.30f, width, asymSpine)
        assertEquals(asymSpine, asymRightSecond.spineX, 0.01f)
        assertEquals(asymRightSecond.leafOuterEdgeX, asymLeftSecond.flatClipRightX, 0.01f)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w1024dp-h640dp")
    fun dualColumnSimulationCurl_midFlipHingedLeavesRenderCleanDisjointPartitionsWithoutBleed() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val customBgFile = File.createTempFile("wenku8_dual_hinge_bg_", ".png", context.cacheDir)
        try {
            val bgBitmap = Bitmap.createBitmap(32, 32, Bitmap.Config.ARGB_8888).apply {
                eraseColor(AndroidColor.rgb(245, 234, 214))
            }
            customBgFile.outputStream().use { out ->
                bgBitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            bgBitmap.recycle()

            val chapter = OrderedChapterWithVolume(
                aid = 2404,
                vid = 944,
                volumeOrder = 0,
                volumeTitle = "第一卷",
                cid = 9441,
                chapterOrder = 0,
                chapterTitle = "第一章 中缝折页测试",
                isIllustrationChapter = false,
            )
            val config = ReaderLayoutConfig(
                viewportWidthPx = 1024,
                viewportHeightPx = 640,
                fontSizePx = 28f,
                lineDistancePx = 10f,
                paragraphDistancePx = 12f,
                pageEdgeDistancePx = 20f,
                columnCount = 2,
                columnGapPx = 48f,
                spineCenterXPx = 512f,
            )
            val ast = WenkuChapterParser.parse(
                rawChapterText = "第一卷 第一章 中缝折页测试\n　　折页正文。",
                aid = chapter.aid,
                vid = chapter.vid,
                cid = chapter.cid,
                volumeTitle = chapter.volumeTitle,
                chapterTitle = chapter.chapterTitle,
            )
            // Spread 0: blank canvas so any dark ink in Spread 0's territory proves bleed-through.
            val blankSpread0 = ReaderPage(
                cid = chapter.cid,
                pageIndexInChapter = 0,
                totalPagesInChapter = 2,
                startAnchor = ReadingAnchor(paragraphIndex = 0, charOffset = 0, progressPermille = 0),
                endAnchor = ReadingAnchor(paragraphIndex = 0, charOffset = 0, progressPermille = 500),
                characterCountOnPage = 0,
                isFullBleedArtbookPage = true,
                elements = emptyList(),
                columnCount = 2,
                spineCenterXPx = 512f,
                columnGapPx = 48f,
                logicalStartPageIndex = 0,
                logicalEndPageIndex = 1,
                totalLogicalPagesInChapter = 4,
            )
            // Spread 1: dense dark ink spanning both left and right facing pages.
            val denseSpread1Elements = (0 until 6).flatMap { row ->
                val topY = 180f + row * 40f
                listOf(
                    ReaderPageElement.TextLine(
                        blockIndex = 1,
                        rawStartChar = row * 20,
                        rawEndChar = (row + 1) * 20,
                        text = "████████████████████",
                        xPx = 16f,
                        topYPx = topY,
                        baselineYPx = topY + 26f,
                        heightPx = 32f,
                        fontSizePx = 28f,
                    ),
                    ReaderPageElement.TextLine(
                        blockIndex = 1,
                        rawStartChar = 200 + row * 20,
                        rawEndChar = 200 + (row + 1) * 20,
                        text = "████████████████████",
                        xPx = 540f,
                        topYPx = topY,
                        baselineYPx = topY + 26f,
                        heightPx = 32f,
                        fontSizePx = 28f,
                    ),
                )
            }
            val denseSpread1 = ReaderPage(
                cid = chapter.cid,
                pageIndexInChapter = 1,
                totalPagesInChapter = 2,
                startAnchor = ReadingAnchor(paragraphIndex = 1, charOffset = 0, progressPermille = 500),
                endAnchor = ReadingAnchor(paragraphIndex = 1, charOffset = 400, progressPermille = 1000),
                characterCountOnPage = 400,
                isFullBleedArtbookPage = true,
                elements = denseSpread1Elements,
                columnCount = 2,
                spineCenterXPx = 512f,
                columnGapPx = 48f,
                logicalStartPageIndex = 2,
                logicalEndPageIndex = 3,
                totalLogicalPagesInChapter = 4,
            )
            val layout = PaginatedChapterLayout(
                ast = ast,
                config = config,
                pages = listOf(blankSpread0, denseSpread1),
            )
            val snapshot = ReaderWindowSnapshot(
                aid = chapter.aid,
                orderedToc = listOf(chapter),
                currentTocIndex = 0,
                currentSlot = ChapterWindowSlot.Loaded(chapter = chapter, layout = layout),
                layoutConfig = config,
                activeVirtualPageIndex = 0,
            )
            val palette = resolveReaderVisualPalette(
                paperPreset = ReaderPaperPreset.PARCHMENT,
                isEinkMode = false,
                customBackgroundPath = customBgFile.absolutePath,
                customFontPath = "",
            )

            fun countDarkInkPixels(bitmap: Bitmap, xStart: Int, xEnd: Int, yStart: Int, yEnd: Int): Int {
                var count = 0
                for (y in yStart until yEnd) {
                    for (x in xStart until xEnd) {
                        val px = bitmap.getPixel(x, y)
                        val luma = (AndroidColor.red(px) + AndroidColor.green(px) + AndroidColor.blue(px)) / 3
                        if (luma < 120) count++
                    }
                }
                return count
            }

            var stage by mutableStateOf(0)
            composeTestRule.setContent {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    androidx.compose.runtime.key(stage) {
                        val pagerState = if (stage == 0) {
                            // First half: Spread 0 right leaf folds to ~85.4% of W (512 + 512*0.707 = 874px).
                            rememberPagerState(
                                initialPage = 0,
                                initialPageOffsetFraction = 0.25f,
                            ) { snapshot.totalVirtualPages }
                        } else {
                            // Second half: Spread 1 left leaf unfolds to ~14.6% of W (512 - 512*0.707 = 150px).
                            rememberPagerState(
                                initialPage = 1,
                                initialPageOffsetFraction = -0.25f,
                            ) { snapshot.totalVirtualPages }
                        }
                        ReaderViewportHost(
                            pageTurnMode = ReaderPageTurnMode.SIMULATION_CURL,
                            snapshot = snapshot,
                            pagerState = pagerState,
                            palette = palette,
                            fontSizeSp = 28,
                            lineDistanceDp = 10,
                            paragraphDistanceDp = 12,
                            pageEdgeDistanceDp = 20,
                            leftEdgeBrightnessDragEnabled = false,
                            contentSafeInsets = WindowInsets(0, 0, 0, 0),
                            isDualColumnSpread = true,
                            dualColumnSpineCenterXPx = 512f,
                            onTapZoneAction = {},
                            onBrightnessDragDelta = {},
                            onIllustrationClicked = { _, _ -> },
                            onContinuousScrollAnchorChanged = { _, _ -> },
                            onRequestNextChapterInContinuousMode = {},
                            onRequestPrevChapterInContinuousMode = {},
                            onRetryFailedSlot = {},
                            onOpenNetworkDiagnostics = {},
                        )
                    }
                }
            }

            // Stage 0 (first half, p = 0.25f):
            composeTestRule.waitForIdle()
            val firstHalfBitmap = composeTestRule
                .onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER)
                .captureToImage()
                .asAndroidBitmap()
            val w = firstHalfBitmap.width
            val h = firstHalfBitmap.height
            val yStart = (h * 0.30f).toInt().coerceIn(0, h - 1)
            val yEnd = (h * 0.65f).toInt().coerceIn(yStart + 1, h)

            assertEquals(
                "Spread 1 ink bled into Spread 0's static left page during first half!",
                0,
                countDarkInkPixels(firstHalfBitmap, (w * 0.08f).toInt(), (w * 0.42f).toInt(), yStart, yEnd),
            )
            assertEquals(
                "Spread 1 ink bled into Spread 0's folding right leaf during first half!",
                0,
                countDarkInkPixels(firstHalfBitmap, (w * 0.54f).toInt(), (w * 0.78f).toInt(), yStart, yEnd),
            )
            assertTrue(
                "Expected Spread 1's revealed right edge [0.89W..0.97W] to render ink during first half",
                countDarkInkPixels(firstHalfBitmap, (w * 0.89f).toInt(), (w * 0.97f).toInt(), yStart, yEnd) > 0,
            )

            // Stage 1 (second half, p = 0.75f -> page 1 at -0.25f):
            stage = 1
            composeTestRule.waitForIdle()
            val secondHalfBitmap = composeTestRule
                .onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER)
                .captureToImage()
                .asAndroidBitmap()

            assertEquals(
                "Spread 1 unfolding left leaf bled past outerEdgeX (~0.146W) into Spread 0's remaining left strip!",
                0,
                countDarkInkPixels(secondHalfBitmap, (w * 0.02f).toInt(), (w * 0.11f).toInt(), yStart, yEnd),
            )
            assertTrue(
                "Expected Spread 1's unfolding left leaf [0.22W..0.46W] to render foreshortened ink",
                countDarkInkPixels(secondHalfBitmap, (w * 0.22f).toInt(), (w * 0.46f).toInt(), yStart, yEnd) > 0,
            )
            assertTrue(
                "Expected Spread 1's static right half [0.58W..0.92W] to render ink",
                countDarkInkPixels(secondHalfBitmap, (w * 0.58f).toInt(), (w * 0.92f).toInt(), yStart, yEnd) > 0,
            )
        } finally {
            customBgFile.delete()
        }
    }

    private suspend fun seedDualColumnChapter(
        aid: Int,
        vid: Int,
        cid: Int,
        novelTitle: String,
    ) {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = aid,
                title = novelTitle,
                author = "测试作者",
                inBookshelf = true,
                metaSyncedAtMs = 1_000L,
            ),
        )
        tocSyncRepository.syncNovelIndexAtomically(
            aid = aid,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 2_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = vid,
                    volumeTitle = "第一卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = cid, chapterTitle = "第一章 双栏序幕"),
                    ),
                ),
            ),
        )
        val longBody = buildString {
            appendLine("第一卷 第一章 双栏序幕")
            repeat(80) { idx ->
                appendLine(
                    "　　这是第${idx + 1}段双栏排版与实体书对开阅读验证正文，用来确保左右两页均有充足文字内容，并生成多页书页对开。",
                )
            }
        }
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = cid,
                aid = aid,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = longBody,
                byteLength = longBody.toByteArray().size,
                sha256Checksum = "sha_$cid",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )
    }
}

private class FakeUnifiedReaderGateway : WenkuCatalogGateway {
    override val isApiConfigured: Boolean = true
    val customChapterResults = mutableMapOf<Int, WenkuApiResult<String>>()
    val gatedChapterResponses = mutableMapOf<Int, CompletableDeferred<String>>()
    val fetchCountByCid = mutableMapOf<Int, Int>()

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
    ): WenkuApiResult<String> {
        fetchCountByCid[cid] = (fetchCountByCid[cid] ?: 0) + 1
        customChapterResults[cid]?.let { return it }
        val gate = gatedChapterResponses[cid] ?: return WenkuApiResult.ApiNotConfigured
        return WenkuApiResult.Success(gate.await())
    }

    override suspend fun fetchCloudBookshelf(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.Success(emptyList())
}
