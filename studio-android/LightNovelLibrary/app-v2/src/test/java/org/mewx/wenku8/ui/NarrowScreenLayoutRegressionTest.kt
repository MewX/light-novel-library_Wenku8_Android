package org.mewx.wenku8.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.download.NovelDownloadRepository
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.data.remote.api.OnlineSearchField
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.data.remote.parser.ParsedReviewReplyItem
import org.mewx.wenku8.data.remote.parser.ParsedReviewReplyPage
import org.mewx.wenku8.data.remote.parser.ParsedReviewThreadPage
import org.mewx.wenku8.data.repository.BookshelfItemModel
import org.mewx.wenku8.data.repository.BookshelfRepository
import org.mewx.wenku8.data.repository.BookshelfSortMode
import org.mewx.wenku8.data.repository.BookshelfViewMode
import org.mewx.wenku8.data.repository.DiscoveryRepository
import org.mewx.wenku8.data.repository.NovelDetailRepository
import org.mewx.wenku8.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.data.repository.ParsedChapterToc
import org.mewx.wenku8.data.repository.ParsedVolumeToc
import org.mewx.wenku8.data.repository.ReviewThreadCardItem
import org.mewx.wenku8.data.repository.ReviewsRepository
import org.mewx.wenku8.domain.model.RankingMetric
import org.mewx.wenku8.domain.model.UnifiedSearchMode
import org.mewx.wenku8.reader.engine.DeterministicCellLineBreaker
import org.mewx.wenku8.ui.bookshelf.Bookshelf3dShelfSection
import org.mewx.wenku8.ui.bookshelf.BookshelfScreen
import org.mewx.wenku8.ui.bookshelf.BookshelfTestTags
import org.mewx.wenku8.ui.detail.NovelDetailScreen
import org.mewx.wenku8.ui.detail.NovelDetailTestTags
import org.mewx.wenku8.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.ui.discovery.DiscoveryTestTags
import org.mewx.wenku8.ui.discovery.RankingsScreen
import org.mewx.wenku8.ui.reader.ReaderTestTags
import org.mewx.wenku8.ui.reader.ReaderTtsFloatingBar
import org.mewx.wenku8.ui.reader.ReaderTtsUiState
import org.mewx.wenku8.ui.reader.UnifiedReaderScreen
import org.mewx.wenku8.ui.reviews.ReviewScreensTestTags
import org.mewx.wenku8.ui.reviews.ReviewThreadScreen
import org.mewx.wenku8.ui.search.UnifiedSearchScreen
import org.mewx.wenku8.ui.search.UnifiedSearchTestTags
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

/**
 * Regression test matrix covering narrow phones (`320dp @ 1.0x`), large accessibility fonts
 * (`360dp @ 1.3x` / `320dp @ 1.3x`), and normal-width phone preservation (`412dp @ 1.0x`)
 * for GitHub Issue #200.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class NarrowScreenLayoutRegressionTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: Wenku8Database
    private lateinit var tempAssetRoot: File
    private lateinit var testScope: CoroutineScope
    private lateinit var gateway: FakeNarrowScreenGateway
    private lateinit var tocSyncRepository: NovelTocSyncRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        tempAssetRoot = Files.createTempDirectory("wenku8_v2_narrow_screen_test").toFile()
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        gateway = FakeNarrowScreenGateway()
        tocSyncRepository = NovelTocSyncRepository(database)
    }

    @After
    fun tearDown() {
        runCatching { composeTestRule.waitForIdle() }
        testScope.cancel()
        database.close()
        tempAssetRoot.deleteRecursively()
    }

    @Test
    fun bookshelfViewModeRow_preservesLabeledChipsOnNormalScreen_andSwitchesToIconOnlyOnNarrowScreen() {
        runBlocking {
            database.novelDao().upsertNovel(
                NovelEntity(
                    aid = 1001,
                    title = "狼与香辛料",
                    author = "支仓冻砂",
                    bookStatus = "已完成",
                    inBookshelf = true,
                ),
            )
        }
        val bookshelfRepo = BookshelfRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
            userSessionRepository = null,
            ioDispatcher = Dispatchers.Unconfined,
        )

        var widthDp by mutableStateOf(412.dp)
        var fontScale by mutableFloatStateOf(1.0f)
        var viewMode by mutableStateOf(BookshelfViewMode.GRID)
        var sortMode by mutableStateOf(BookshelfSortMode.RECENT_READ)

        composeTestRule.setContent {
            ViewportHarness(widthDp = widthDp, fontScale = fontScale) {
                BookshelfScreen(
                    bookshelfRepository = bookshelfRepo,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    viewMode = viewMode,
                    onViewModeSelected = { viewMode = it },
                    sortMode = sortMode,
                    onSortModeSelected = { sortMode = it },
                    onOpenOnlineSearch = {},
                    onNovelClicked = {},
                    onContinueReadingClicked = {},
                    onOpenLoginSheet = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Normal 412dp @ 1.0x keeps labeled FilterChips ("网格", "列表", "书脊") unchanged.
        composeTestRule.onNodeWithText("网格").assertIsDisplayed()
        composeTestRule.onNodeWithText("列表").assertIsDisplayed()
        composeTestRule.onNodeWithText("书脊").assertIsDisplayed()
        BookshelfViewMode.entries.forEach { mode ->
            composeTestRule
                .onNodeWithTag(BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + mode.name)
                .assertWithinViewport(412.dp)
        }

        // 2. Narrow 320dp @ 1.0x switches to icon-only SingleChoiceSegmentedButtonRow without text labels.
        widthDp = 320.dp
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("书脊").assertCountEquals(0)
        composeTestRule.onNodeWithContentDescription("书脊").assertIsDisplayed()
        BookshelfViewMode.entries.forEach { mode ->
            composeTestRule
                .onNodeWithTag(BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + mode.name)
                .assertIsDisplayed()
                .assertWithinViewport(320.dp)
        }
        composeTestRule
            .onNodeWithTag(BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + BookshelfViewMode.SPINE_3D.name)
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(BookshelfViewMode.SPINE_3D, viewMode)

        // 3. 360dp @ 1.3x font scale also uses icon-only segmented buttons within viewport bounds.
        widthDp = 360.dp
        fontScale = 1.3f
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("书脊").assertCountEquals(0)
        BookshelfViewMode.entries.forEach { mode ->
            composeTestRule
                .onNodeWithTag(BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + mode.name)
                .assertIsDisplayed()
                .assertWithinViewport(360.dp)
        }
    }

    @Test
    fun rankingsMetricSelector_preservesSegmentedRowOnNormalScreen_andUsesScrollableChipsOnNarrowScreen() {
        val discoveryRepo = DiscoveryRepository(
            database = database,
            apiClient = gateway,
        )

        var widthDp by mutableStateOf(412.dp)
        var fontScale by mutableFloatStateOf(1.0f)

        composeTestRule.setContent {
            ViewportHarness(widthDp = widthDp, fontScale = fontScale) {
                RankingsScreen(
                    discoveryRepository = discoveryRepo,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    onOpenSearch = {},
                    onNovelClicked = {},
                    onOpenNetworkDiagnostics = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Normal 412dp @ 1.0x renders full-width SingleChoiceSegmentedButtonRow across 4 metrics.
        val firstNormalBounds = composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_METRIC_PREFIX + RankingMetric.VISITS.name)
            .assertIsDisplayed()
            .assertWithinViewport(412.dp)
            .getUnclippedBoundsInRoot()
        val lastNormalBounds = composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_METRIC_PREFIX + RankingMetric.WORD_COUNT.name)
            .assertIsDisplayed()
            .assertWithinViewport(412.dp)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Expected segmented button row to span normal viewport width, got ${lastNormalBounds.right - firstNormalBounds.left}",
            (lastNormalBounds.right - firstNormalBounds.left) >= 350.dp,
        )

        // 2. Narrow 320dp @ 1.0x uses horizontally scrollable FilterChips where trailing metrics stay reachable.
        widthDp = 320.dp
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_METRIC_PREFIX + RankingMetric.WORD_COUNT.name)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_METRIC_PREFIX + RankingMetric.WORD_COUNT.name)
            .assertIsSelected()

        // 3. 360dp @ 1.3x font scale keeps scrollable FilterChips selectable without clipping.
        widthDp = 360.dp
        fontScale = 1.3f
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_METRIC_PREFIX + RankingMetric.FAVORITES.name)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_METRIC_PREFIX + RankingMetric.FAVORITES.name)
            .assertIsSelected()
    }

    @Test
    fun unifiedSearchScreen_preservesFullBarOnNormalScreen_andCompactsCleanlyOnNarrowScreen() {
        val discoveryRepo = DiscoveryRepository(
            database = database,
            apiClient = gateway,
        )

        var widthDp by mutableStateOf(412.dp)

        composeTestRule.setContent {
            ViewportHarness(widthDp = widthDp) {
                UnifiedSearchScreen(
                    discoveryRepository = discoveryRepo,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    onNavigateBack = {},
                    onNovelClicked = {},
                    onOpenNetworkDiagnostics = {},
                    autoFocusInputOnEntry = false,
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Normal 412dp @ 1.0x preserves full text submit Button ("搜索") and segmented scopes.
        val normalSubmitBounds = composeTestRule
            .onNodeWithTag(UnifiedSearchTestTags.SUBMIT_BUTTON)
            .assertIsDisplayed()
            .assertWithinViewport(412.dp)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Normal screen submit button should keep full text button width, got ${normalSubmitBounds.width}",
            normalSubmitBounds.width > 56.dp,
        )
        UnifiedSearchMode.entries.forEach { mode ->
            composeTestRule
                .onNodeWithTag(UnifiedSearchTestTags.MODE_BUTTON_PREFIX + mode.name)
                .assertIsDisplayed()
                .assertWithinViewport(412.dp)
        }

        // 2. Narrow 320dp @ 1.0x uses compact icon submit button + generous input width + scrollable scopes.
        widthDp = 320.dp
        composeTestRule.waitForIdle()

        val narrowSubmitBounds = composeTestRule
            .onNodeWithTag(UnifiedSearchTestTags.SUBMIT_BUTTON)
            .assertIsDisplayed()
            .assertWithinViewport(320.dp)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Narrow screen submit button should be compact icon button, got ${narrowSubmitBounds.width}",
            narrowSubmitBounds.width <= 52.dp,
        )
        val narrowInputBounds = composeTestRule
            .onNodeWithTag(UnifiedSearchTestTags.QUERY_INPUT)
            .assertIsDisplayed()
            .assertWithinViewport(320.dp)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Narrow search input should retain usable width >= 180dp, got ${narrowInputBounds.width}",
            narrowInputBounds.width >= 180.dp,
        )
        composeTestRule
            .onNodeWithTag(UnifiedSearchTestTags.MODE_BUTTON_PREFIX + UnifiedSearchMode.DIRECT_AID.name)
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(UnifiedSearchTestTags.MODE_BUTTON_PREFIX + UnifiedSearchMode.DIRECT_AID.name)
            .assertIsSelected()
    }

    @Test
    fun novelDetailActionDeck_preservesInlineButtonsOnNormalScreen_andStacksIconTilesOnNarrowScreen() {
        seedNovelDetailFixtures()
        val detailRepo = NovelDetailRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val bookshelfRepo = BookshelfRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
            userSessionRepository = null,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val downloadRepo = NovelDownloadRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
            assetRootsProvider = { listOf(tempAssetRoot) },
            applicationScope = testScope,
            appContext = null,
            ioDispatcher = Dispatchers.Unconfined,
        )

        var widthDp by mutableStateOf(412.dp)
        var fontScale by mutableFloatStateOf(1.0f)

        composeTestRule.setContent {
            ViewportHarness(widthDp = widthDp, fontScale = fontScale) {
                NovelDetailScreen(
                    aid = 1587,
                    novelDetailRepository = detailRepo,
                    bookshelfRepository = bookshelfRepo,
                    novelDownloadRepository = downloadRepo,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    themeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                    useCoverTheme = false,
                )
            }
        }
        composeTestRule.waitForIdle()

        val actionTags = listOf(
            NovelDetailTestTags.SHELF_TOGGLE_BUTTON,
            NovelDetailTestTags.DOWNLOAD_OPEN_BUTTON,
            NovelDetailTestTags.REVIEWS_OPEN_BUTTON,
        )

        // 1. Normal 412dp @ 1.0x preserves standard inline Outlined/Tonal buttons (height < 56dp).
        actionTags.forEach { tag ->
            val bounds = composeTestRule
                .onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertWithinViewport(412.dp)
                .getUnclippedBoundsInRoot()
            assertTrue(
                "Normal screen $tag should preserve inline button height (< 56dp), got ${bounds.height}",
                bounds.height < 56.dp,
            )
        }

        // 2. Narrow 320dp @ 1.0x switches to 3 equal-width stacked icon-above-label tiles (height >= 56dp).
        widthDp = 320.dp
        composeTestRule.waitForIdle()

        val narrowWidths = actionTags.map { tag ->
            val bounds = composeTestRule
                .onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertWithinViewport(320.dp)
                .getUnclippedBoundsInRoot()
            assertTrue(
                "Narrow screen $tag should render stacked icon-above-label tile (>= 56dp), got ${bounds.height}",
                bounds.height >= 56.dp,
            )
            assertTrue(
                "Narrow screen $tag should have non-zero balanced width (>= 80dp), got ${bounds.width}",
                bounds.width >= 80.dp,
            )
            bounds.width
        }
        assertTrue(
            "3 stacked tiles should share equal widths, got $narrowWidths",
            (narrowWidths.max() - narrowWidths.min()) <= 2.dp,
        )

        // 3. 360dp @ 1.3x font scale also uses stacked tiles cleanly within viewport bounds.
        widthDp = 360.dp
        fontScale = 1.3f
        composeTestRule.waitForIdle()

        actionTags.forEach { tag ->
            val bounds = composeTestRule
                .onNodeWithTag(tag)
                .assertIsDisplayed()
                .assertWithinViewport(360.dp)
                .getUnclippedBoundsInRoot()
            assertTrue(
                "Large-font $tag should use stacked tile (>= 56dp), got ${bounds.height}",
                bounds.height >= 56.dp,
            )
        }
    }

    @Test
    fun readerHud_preservesBottomToolbarOnNormalScreen_andOverflowsCleanlyOnNarrowScreen() {
        seedReaderTwoChapterNovel()
        var widthDp by mutableStateOf(412.dp)

        composeTestRule.setContent {
            ViewportHarness(widthDp = widthDp) {
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
                    catalogGateway = gateway,
                    novelTocSyncRepository = tocSyncRepository,
                    preferences = Wenku8UserPreferences(),
                    onReaderTypographyChanged = { _, _, _, _ -> },
                    onReaderPageTurnModeChanged = {},
                    onNavigateBack = {},
                    lineBreaker = DeterministicCellLineBreaker,
                    ioDispatcher = Dispatchers.Main.immediate,
                    computeDispatcher = Dispatchers.Main.immediate,
                )
            }
        }
        composeTestRule.waitForIdle()

        // Tap center of reader to open HUD.
        composeTestRule.onNodeWithTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER).performTouchInput {
            click(center)
        }
        composeTestRule.waitForIdle()

        // 1. Normal 412dp @ 1.0x displays floating toolbar and core actions within viewport.
        composeTestRule
            .onNodeWithTag(ReaderTestTags.HUD_FLOATING_TOOLBAR)
            .assertIsDisplayed()
            .assertWithinViewport(412.dp)
        listOf(
            ReaderTestTags.ACTION_TOC,
            ReaderTestTags.ACTION_TYPOGRAPHY,
            ReaderTestTags.ACTION_PAGE_MODE,
            ReaderTestTags.ACTION_BOOKMARKS,
            ReaderTestTags.ACTION_TTS,
            ReaderTestTags.ACTION_DAY_NIGHT,
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed().assertWithinViewport(412.dp)
        }

        // 2. Narrow 320dp @ 1.0x keeps floating toolbar strictly inside 320dp via AppBarRow overflow
        // and keeps scrubber slider wide and usable.
        widthDp = 320.dp
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(ReaderTestTags.HUD_FLOATING_TOOLBAR)
            .assertIsDisplayed()
            .assertWithinViewport(320.dp)
        composeTestRule
            .onNodeWithTag(ReaderTestTags.HUD_TOP_BAR)
            .assertIsDisplayed()
            .assertWithinViewport(320.dp)
        listOf(
            ReaderTestTags.ACTION_TOC,
            ReaderTestTags.ACTION_TYPOGRAPHY,
            ReaderTestTags.ACTION_PAGE_MODE,
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed().assertWithinViewport(320.dp)
        }
        val scrubberBounds = composeTestRule
            .onNodeWithTag(ReaderTestTags.HUD_SCRUBBER_SLIDER)
            .assertIsDisplayed()
            .assertWithinViewport(320.dp)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Narrow reader scrubber slider should retain usable width >= 100dp, got ${scrubberBounds.width}",
            scrubberBounds.width >= 100.dp,
        )
    }

    @Test
    fun reviewReplyComposer_preservesTextSendOnNormalScreen_andCompactsToIconButtonOnNarrowScreen() {
        gateway.replyPages[1] = ParsedReviewReplyPage(
            totalPages = 1,
            items = listOf(
                ParsedReviewReplyItem(
                    replyTimeFormatted = "2026-05-01 12:00",
                    uid = 1001,
                    userName = "奇诺旅人",
                    content = "这是一段用来测试窄屏书评回复栏的书评正文内容。",
                ),
            ),
        )
        val reviewsRepo = ReviewsRepository(
            database = database,
            catalogGateway = gateway,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val thread = ReviewThreadCardItem(
            rid = 9001,
            uid = 1001,
            userName = "奇诺旅人",
            avatarUrl = "",
            title = "第一卷读后感",
            replyCount = 1,
            postTimeFormatted = "2026-05-01 12:00",
            lastReplyTimeFormatted = "2026-05-01 12:00",
        )

        var widthDp by mutableStateOf(412.dp)

        composeTestRule.setContent {
            ViewportHarness(widthDp = widthDp) {
                ReviewThreadScreen(
                    aid = 1306,
                    novelTitle = "奇诺之旅",
                    thread = thread,
                    reviewsRepository = reviewsRepo,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    onNavigateBack = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Normal 412dp @ 1.0x preserves full text "发送回复" button.
        composeTestRule.onNodeWithText("发送回复").assertIsDisplayed()
        val normalSendBounds = composeTestRule
            .onNodeWithTag(ReviewScreensTestTags.REVIEW_REPLY_SUBMIT_BUTTON)
            .assertIsDisplayed()
            .assertWithinViewport(412.dp)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Normal screen reply submit button should keep text button width (> 60dp), got ${normalSendBounds.width}",
            normalSendBounds.width > 60.dp,
        )

        // 2. Narrow 320dp @ 1.0x switches to compact 48dp icon-only send button while keeping wide input field.
        widthDp = 320.dp
        composeTestRule.waitForIdle()

        composeTestRule.onAllNodesWithText("发送回复").assertCountEquals(0)
        val narrowSendBounds = composeTestRule
            .onNodeWithTag(ReviewScreensTestTags.REVIEW_REPLY_SUBMIT_BUTTON)
            .assertIsDisplayed()
            .assertWithinViewport(320.dp)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Narrow screen reply submit button should be compact icon button (<= 52dp), got ${narrowSendBounds.width}",
            narrowSendBounds.width <= 52.dp,
        )
        val narrowInputBounds = composeTestRule
            .onNodeWithTag(ReviewScreensTestTags.REVIEW_REPLY_INPUT)
            .assertIsDisplayed()
            .assertWithinViewport(320.dp)
            .getUnclippedBoundsInRoot()
        assertTrue(
            "Narrow reply input field should retain wide usable width (>= 200dp), got ${narrowInputBounds.width}",
            narrowInputBounds.width >= 200.dp,
        )
    }

    @Test
    fun bookshelf3dSpotlightAndReaderTtsBar_fitWithin320dpAtLargeFontScale() {
        val sampleItems = List(5) { idx ->
            val aid = 2001 + idx
            BookshelfItemModel(
                novel = NovelEntity(
                    aid = aid,
                    title = "这是一部标题非常长的轻小说测试作品第${idx + 1}部",
                    author = "测试作者名${idx + 1}",
                    pressId = "电击文库",
                    bookStatus = "连载中",
                    tagsCsv = "奇幻 冒险",
                    introShort = "简介内容",
                    latestSectionName = "第${idx + 1}卷 尾声",
                    hasUnreadUpdate = true,
                    inBookshelf = true,
                    shelfSortOrder = idx + 1,
                ),
                readingProgress = ReadingProgressEntity(
                    aid = aid,
                    vid = aid * 10,
                    cid = aid * 10 + 1,
                    volumeTitle = "第一卷",
                    chapterTitle = "第一章 启程",
                    progressPermille = 480,
                    updatedAtMs = 1_700_000_000_000L,
                ),
                cachedChapterCount = 12,
                totalChapterCount = 24,
                tags = listOf("奇幻", "冒险"),
                isActivelySyncingOrDownloading = false,
                coverUrl = "",
            )
        }

        var showTtsOnly by mutableStateOf(false)

        composeTestRule.setContent {
            ViewportHarness(widthDp = 320.dp, heightDp = 760.dp, fontScale = 1.3f) {
                if (!showTtsOnly) {
                    Bookshelf3dShelfSection(
                        items = sampleItems,
                        availableWidth = 320.dp,
                        availableHeight = 760.dp,
                        selectedAidSet = emptySet(),
                        isMultiSelectActive = false,
                        isEinkMode = false,
                        onItemClick = {},
                        onItemLongClick = {},
                        onContinueReadingClick = {},
                    )
                } else {
                    ReaderTtsFloatingBar(
                        state = ReaderTtsUiState(
                            isSessionOpen = true,
                            isSpeaking = true,
                            speechRate = 1.25f,
                            activeUtteranceIndex = 3,
                            totalUtterances = 42,
                            currentSnippet = "正在朗读一段非常长的轻小说正文语句用于测试窄屏与大字号布局。",
                        ),
                        onTogglePlayPause = {},
                        onSkipPrevious = {},
                        onSkipNext = {},
                        onCycleSpeechRate = {},
                        onStopAndDismiss = {},
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. 3D Bookshelf fast scroller & compact spotlight card stay strictly inside 320dp @ 1.3x.
        listOf(
            BookshelfTestTags.SPINE_3D_SPOTLIGHT_CARD,
            BookshelfTestTags.SPINE_3D_PRIMARY_ACTION,
            BookshelfTestTags.SPINE_3D_FAST_SCROLLER,
            BookshelfTestTags.SPINE_3D_PREV_BUTTON,
            BookshelfTestTags.SPINE_3D_NEXT_BUTTON,
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed().assertWithinViewport(320.dp)
        }

        // 2. Reader TTS floating bar stays strictly inside 320dp @ 1.3x with all controls non-zero width.
        showTtsOnly = true
        composeTestRule.waitForIdle()

        listOf(
            ReaderTestTags.TTS_CONTROLLER_BAR,
            ReaderTestTags.TTS_RATE_CHIP,
            ReaderTestTags.TTS_PREV_BUTTON,
            ReaderTestTags.TTS_PLAY_PAUSE_BUTTON,
            ReaderTestTags.TTS_NEXT_BUTTON,
            ReaderTestTags.TTS_STOP_BUTTON,
        ).forEach { tag ->
            composeTestRule.onNodeWithTag(tag).assertIsDisplayed().assertWithinViewport(320.dp)
        }
    }

    private fun SemanticsNodeInteraction.assertWithinViewport(
        viewportWidth: Dp,
        tolerance: Dp = 1.dp,
    ): SemanticsNodeInteraction {
        val bounds = getUnclippedBoundsInRoot()
        assertTrue(
            "Expected node width > 0dp, got ${bounds.width}",
            bounds.width > 0.dp,
        )
        assertTrue(
            "Expected node height > 0dp, got ${bounds.height}",
            bounds.height > 0.dp,
        )
        assertTrue(
            "Expected node left >= -$tolerance, got ${bounds.left}",
            bounds.left >= -tolerance,
        )
        assertTrue(
            "Expected node right <= ${viewportWidth + tolerance}, got ${bounds.right}",
            bounds.right <= viewportWidth + tolerance,
        )
        return this
    }

    private fun seedNovelDetailFixtures(): Unit = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 1587,
                title = "无职转生 ～到了异世界就拿出真本事～",
                author = "理不尽な孫の手",
                pressId = "MF文库J",
                bookStatus = "已完成",
                lastUpdate = "2026-01-15",
                tagsCsv = "异世界 奇幻 冒险",
                introShort = "转生到剑与魔法的世界重新开始人生的故事。",
                inBookshelf = false,
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
                        ParsedChapterToc(cid = 6002, chapterTitle = "第二章 启程"),
                    ),
                ),
            ),
        )
    }

    private fun seedReaderTwoChapterNovel(): Unit = runBlocking {
        seedNovelDetailFixtures()
        val ch1Body = "第一章 序章\n\n" + List(18) { idx ->
            "这是第 ${idx + 1} 段正文内容，用于验证阅读器在正常屏与窄屏下的分页和工具栏自适应效果。"
        }.joinToString("\n\n")
        val ch2Body = "第二章 启程\n\n这是第二章的正文内容。"
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 6001,
                aid = 1587,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = ch1Body,
                byteLength = ch1Body.toByteArray().size,
                sha256Checksum = "sha_6001",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 6002,
                aid = 1587,
                contentLang = Wenku8API.AppLanguage.SC.name,
                contentText = ch2Body,
                byteLength = ch2Body.toByteArray().size,
                sha256Checksum = "sha_6002",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )
    }

    @Composable
    private fun ViewportHarness(
        widthDp: Dp,
        heightDp: Dp = 800.dp,
        fontScale: Float = 1.0f,
        content: @Composable () -> Unit,
    ) {
        val baseDensity = LocalDensity.current
        CompositionLocalProvider(
            LocalDensity provides Density(density = baseDensity.density, fontScale = fontScale),
        ) {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                Box(modifier = Modifier.size(width = widthDp, height = heightDp)) {
                    content()
                }
            }
        }
    }

    private class FakeNarrowScreenGateway : WenkuCatalogGateway {
        override val isApiConfigured: Boolean = true
        override val minReplyTextLength: Int = 7
        val replyPages = mutableMapOf<Int, ParsedReviewReplyPage>()

        override fun resolveCoverUrl(aid: Int): String = ""

        override suspend fun fetchNovelListPage(
            sortedBy: Wenku8API.NovelSortedBy,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListPage> = WenkuApiResult.Success(
            ParsedNovelListPage(
                totalPages = 1,
                items = listOf(
                    ParsedNovelListItem(
                        aid = 1587,
                        title = "无职转生",
                        author = "理不尽な孫の手",
                        bookStatus = "已完成",
                        lastUpdate = "2026-01-15",
                        tagsCsv = "奇幻 异世界",
                        introShort = "异世界冒险故事。",
                    ),
                ),
            ),
        )

        override suspend fun searchNovelsByField(
            query: String,
            searchField: OnlineSearchField,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<Int>> = WenkuApiResult.Success(emptyList())

        override suspend fun fetchNovelShortInfo(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListItem> = WenkuApiResult.NetworkError("unused")

        override suspend fun fetchNovelFullMeta(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelFullMeta> = WenkuApiResult.NetworkError("unused")

        override suspend fun fetchNovelFullIntro(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<String> = WenkuApiResult.NetworkError("unused")

        override suspend fun fetchNovelVolumeToc(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<ParsedVolumeToc>> = WenkuApiResult.NetworkError("unused")

        override suspend fun fetchChapterContent(
            aid: Int,
            cid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<String> = WenkuApiResult.NetworkError("unused")

        override suspend fun fetchCloudBookshelf(
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.Success(emptyList())

        override suspend fun fetchReviewThreadListPage(
            aid: Int,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedReviewThreadPage> = WenkuApiResult.Success(
            ParsedReviewThreadPage(totalPages = 1, items = emptyList()),
        )

        override suspend fun fetchReviewReplyPage(
            rid: Int,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedReviewReplyPage> = WenkuApiResult.Success(
            replyPages[pageNumber] ?: ParsedReviewReplyPage(totalPages = 1, items = emptyList()),
        )
    }
}
