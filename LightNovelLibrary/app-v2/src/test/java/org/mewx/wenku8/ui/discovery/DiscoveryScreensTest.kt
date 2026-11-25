package org.mewx.wenku8.ui.discovery

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.remote.api.OnlineSearchField
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.data.repository.DiscoveryRepository
import org.mewx.wenku8.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.domain.model.FeedScrollPosition
import org.mewx.wenku8.domain.model.LatestFeedCategory
import org.mewx.wenku8.domain.model.RankingMetric
import org.mewx.wenku8.domain.model.RankingPeriod
import org.mewx.wenku8.ui.detail.BannerAdLoadPhase
import org.mewx.wenku8.ui.detail.DiscoveryFeedInlineAdPool
import org.mewx.wenku8.ui.detail.InlineBannerPlacement
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class DiscoveryScreensTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: Wenku8Database

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directExecutor = Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
    }

    @After
    fun tearDown() {
        runCatching { composeTestRule.waitForIdle() }
        database.close()
    }

    @Test
    fun latestScreenRendersThreeTabsBookshelfBadgeAndSwitchesFeedSort() {
        kotlinx.coroutines.runBlocking {
            database.novelDao().upsertNovel(
                NovelEntity(
                    aid = 2500,
                    title = "86-不存在的战区-",
                    author = "安里アサト",
                    inBookshelf = true,
                ),
            )
        }

        val gateway = FakeDiscoveryGateway().apply {
            pagesBySort[Wenku8API.NovelSortedBy.lastUpdate] = WenkuApiResult.Success(
                ParsedNovelListPage(
                    totalPages = 10,
                    items = listOf(
                        ParsedNovelListItem(
                            aid = 2500,
                            title = "86-不存在的战区-",
                            author = "安里アサト",
                            bookStatus = "连载中",
                            lastUpdate = "2026-10-01",
                            tagsCsv = "科幻 战争",
                            introShort = "共和国前线战记。",
                        ),
                    ),
                ),
            )
            pagesBySort[Wenku8API.NovelSortedBy.fullFlag] = WenkuApiResult.Success(
                ParsedNovelListPage(
                    totalPages = 4,
                    items = listOf(
                        ParsedNovelListItem(
                            aid = 1306,
                            title = "向森之魔物献上花束",
                            author = "小木君人",
                            bookStatus = "已完成",
                            lastUpdate = "2012-11-02",
                            tagsCsv = "奇幻 治愈",
                            introShort = "森林深处的温柔故事。",
                        ),
                    ),
                ),
            )
        }

        val repository = DiscoveryRepository(database = database, apiClient = gateway)
        var clickedAid = -1
        var searchOpened = false

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    LatestScreen(
                        discoveryRepository = repository,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onOpenSearch = { searchOpened = true },
                        onNovelClicked = { clickedAid = it.aid },
                        onOpenNetworkDiagnostics = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("86-不存在的战区-").assertIsDisplayed()
        composeTestRule.onNodeWithText("已在书架").assertIsDisplayed()

        // Tap search pill
        composeTestRule.onNodeWithTag(DiscoveryTestTags.PERSISTENT_SEARCH_PILL).performClick()
        assertTrue(searchOpened)

        // Switch to 完结精品 tab
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.LATEST_TAB_PREFIX + LatestFeedCategory.COMPLETED_GEMS.name)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("向森之魔物献上花束").assertIsDisplayed()
        composeTestRule.onNodeWithTag("${DiscoveryTestTags.NOVEL_CARD_PREFIX}1306").performClick()
        assertEquals(1306, clickedAid)
        assertTrue(
            "Jumping from RECENT_UPDATES to COMPLETED_GEMS must not spuriously fetch NEW_ARRIVALS",
            Wenku8API.NovelSortedBy.postDate !in gateway.requestedSorts,
        )
    }

    @Test
    fun latestScreenSupportsHorizontalSwipeBetweenThreeTabsAndPreservesScrollPosition() {
        fun sampleNovel(aid: Int, title: String) = ParsedNovelListItem(
            aid = aid,
            title = title,
            author = "作者$aid",
            bookStatus = "连载中",
            lastUpdate = "2026-11-21",
            tagsCsv = "奇幻 冒险",
            introShort = "作品简介 $aid",
        )

        val gateway = FakeDiscoveryGateway().apply {
            pagesBySort[Wenku8API.NovelSortedBy.lastUpdate] = WenkuApiResult.Success(
                ParsedNovelListPage(
                    totalPages = 3,
                    items = (1..15).map { idx -> sampleNovel(aid = 5000 + idx, title = "最近更新-$idx") },
                ),
            )
            pagesBySort[Wenku8API.NovelSortedBy.postDate] = WenkuApiResult.Success(
                ParsedNovelListPage(
                    totalPages = 3,
                    items = (1..15).map { idx -> sampleNovel(aid = 6000 + idx, title = "最新入库-$idx") },
                ),
            )
            pagesBySort[Wenku8API.NovelSortedBy.fullFlag] = WenkuApiResult.Success(
                ParsedNovelListPage(
                    totalPages = 3,
                    items = (1..15).map { idx -> sampleNovel(aid = 7000 + idx, title = "完结精品-$idx") },
                ),
            )
        }

        val repository = DiscoveryRepository(database = database, apiClient = gateway)

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    LatestScreen(
                        discoveryRepository = repository,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onOpenSearch = {},
                        onNovelClicked = {},
                        onOpenNetworkDiagnostics = {},
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.LATEST_TAB_PREFIX + LatestFeedCategory.RECENT_UPDATES.name)
            .assertIsSelected()
        composeTestRule.onNodeWithText("最近更新-1").assertIsDisplayed()

        // Scroll 最近更新 down before swiping away
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performScrollToIndex(7)
        composeTestRule.waitForIdle()
        val savedRecentScroll = repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.lastUpdate)
        assertTrue(
            "Expected lastUpdate scroll index > 0, got $savedRecentScroll",
            savedRecentScroll.firstVisibleItemIndex >= 4,
        )

        // Swipe left: 最近更新 -> 最新入库
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.LATEST_TAB_PREFIX + LatestFeedCategory.NEW_ARRIVALS.name)
            .assertIsSelected()
        assertEquals(LatestFeedCategory.NEW_ARRIVALS, repository.activeLatestCategory)
        composeTestRule.onNodeWithText("最新入库-1").assertIsDisplayed()

        // Swipe left again: 最新入库 -> 完结精品
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performTouchInput { swipeLeft() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.LATEST_TAB_PREFIX + LatestFeedCategory.COMPLETED_GEMS.name)
            .assertIsSelected()
        assertEquals(LatestFeedCategory.COMPLETED_GEMS, repository.activeLatestCategory)
        composeTestRule.onNodeWithText("完结精品-1").assertIsDisplayed()

        // Swipe right twice back to 最近更新 and confirm scroll position was preserved
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.LATEST_TAB_PREFIX + LatestFeedCategory.NEW_ARRIVALS.name)
            .assertIsSelected()

        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performTouchInput { swipeRight() }
        composeTestRule.waitForIdle()
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.LATEST_TAB_PREFIX + LatestFeedCategory.RECENT_UPDATES.name)
            .assertIsSelected()
        assertEquals(LatestFeedCategory.RECENT_UPDATES, repository.activeLatestCategory)
        assertEquals(
            savedRecentScroll,
            repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.lastUpdate),
        )
    }

    @Test
    fun rankingsScreen2dMatrixSwitchesPeriodHidesForFavoritesAndSurfacesDiagnosticsOnError() {
        val gateway = FakeDiscoveryGateway().apply {
            pagesBySort[Wenku8API.NovelSortedBy.weekVisit] = WenkuApiResult.Success(
                ParsedNovelListPage(
                    totalPages = 5,
                    items = listOf(
                        ParsedNovelListItem(
                            aid = 2600,
                            title = "败犬女主太多了！",
                            author = "雨森焚火",
                            bookStatus = "连载中",
                            lastUpdate = "2026-10-06",
                            tagsCsv = "校园 恋爱",
                            introShort = "八奈见杏菜的青春日常。",
                        ),
                    ),
                ),
            )
            pagesBySort[Wenku8API.NovelSortedBy.monthVote] = WenkuApiResult.Success(
                ParsedNovelListPage(
                    totalPages = 3,
                    items = listOf(
                        ParsedNovelListItem(
                            aid = 3001,
                            title = "义妹生活",
                            author = "三河ごーすと",
                            bookStatus = "连载中",
                            lastUpdate = "2026-09-12",
                            tagsCsv = "日常 恋爱",
                            introShort = "同在一个屋檐下的生活记录。",
                        ),
                    ),
                ),
            )
            pagesBySort[Wenku8API.NovelSortedBy.goodNum] = WenkuApiResult.NetworkError("timeout")
        }

        val repository = DiscoveryRepository(database = database, apiClient = gateway)
        var diagnosticsOpened = false

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.E_INK) {
                    RankingsScreen(
                        discoveryRepository = repository,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onOpenSearch = {},
                        onNovelClicked = {},
                        onOpenNetworkDiagnostics = { diagnosticsOpened = true },
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Default 点击榜 × 周榜 with #1 badge
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + RankingPeriod.WEEK.name)
            .assertIsSelected()
        composeTestRule.onNodeWithText("#1").assertIsDisplayed()
        composeTestRule.onNodeWithText("败犬女主太多了！").assertIsDisplayed()

        // Switch to 推荐榜 × 月榜
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_METRIC_PREFIX + RankingMetric.RECOMMENDATIONS.name)
            .performClick()
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + RankingPeriod.MONTH.name)
            .performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("义妹生活").assertIsDisplayed()

        // Switch to 总收藏 (single all-time metric -> hides period chips, surfaces error + 网络检测 chip)
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_METRIC_PREFIX + RankingMetric.FAVORITES.name)
            .performClick()
        composeTestRule.waitForIdle()

        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + RankingPeriod.WEEK.name)
            .assertDoesNotExist()
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_ERROR_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_DIAGNOSTICS_CHIP).performClick()
        assertTrue(diagnosticsOpened)
    }

    @Test
    fun rankingsScreenPreservesIndependentScrollProgressWithoutCrossAnchoringAndRetapScrollsToTop() {
        fun sampleNovel(aid: Int, title: String) = ParsedNovelListItem(
            aid = aid,
            title = title,
            author = "作者$aid",
            bookStatus = "连载中",
            lastUpdate = "2026-11-16",
            tagsCsv = "校园 奇幻",
            introShort = "作品简介 $aid",
        )

        // Week list has 9001..9018 (`9001` at #1, `9009` at #9).
        // Day list deliberately places `9001` at #14 and `9009` at #15 so any cross-ranking `aid`
        // anchoring bug would jump `日榜` down to #14/#15 instead of starting at #1 (`9101`).
        val weekItems = (1..18).map { rank ->
            val aid = 9000 + rank
            sampleNovel(aid = aid, title = "小说-$aid")
        }
        val dayItems = buildList {
            for (rank in 1..13) {
                val aid = 9100 + rank
                add(sampleNovel(aid = aid, title = "小说-$aid"))
            }
            add(sampleNovel(aid = 9001, title = "小说-9001"))
            add(sampleNovel(aid = 9009, title = "小说-9009"))
            for (rank in 16..18) {
                val aid = 9100 + rank
                add(sampleNovel(aid = aid, title = "小说-$aid"))
            }
        }
        val monthItems = buildList {
            for (rank in 1..14) {
                val aid = 9200 + rank
                add(sampleNovel(aid = aid, title = "小说-$aid"))
            }
            add(sampleNovel(aid = 9001, title = "小说-9001"))
            add(sampleNovel(aid = 9009, title = "小说-9009"))
        }
        val allTimeItems = buildList {
            for (rank in 1..14) {
                val aid = 9300 + rank
                add(sampleNovel(aid = aid, title = "小说-$aid"))
            }
            add(sampleNovel(aid = 9001, title = "小说-9001"))
            add(sampleNovel(aid = 9009, title = "小说-9009"))
        }

        val gateway = FakeDiscoveryGateway().apply {
            pagesBySort[Wenku8API.NovelSortedBy.weekVisit] =
                WenkuApiResult.Success(ParsedNovelListPage(totalPages = 3, items = weekItems))
            pagesBySort[Wenku8API.NovelSortedBy.dayVisit] =
                WenkuApiResult.Success(ParsedNovelListPage(totalPages = 3, items = dayItems))
            pagesBySort[Wenku8API.NovelSortedBy.monthVisit] =
                WenkuApiResult.Success(ParsedNovelListPage(totalPages = 3, items = monthItems))
            pagesBySort[Wenku8API.NovelSortedBy.allVisit] =
                WenkuApiResult.Success(ParsedNovelListPage(totalPages = 3, items = allTimeItems))
        }

        val repository = DiscoveryRepository(database = database, apiClient = gateway)
        var reselectCounter by mutableIntStateOf(0)

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                    RankingsScreen(
                        discoveryRepository = repository,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onOpenSearch = {},
                        onNovelClicked = {},
                        onOpenNetworkDiagnostics = {},
                        reselectScrollToTopTrigger = reselectCounter,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("小说-9001").assertIsDisplayed()

        // 1. Scroll 周榜 (weekVisit) down to index 8 (#9, aid = 9009)
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performScrollToIndex(8)
        composeTestRule.waitForIdle()
        val savedWeekScroll = repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.weekVisit)
        assertTrue(
            "Expected weekVisit scroll index > 0, got $savedWeekScroll",
            savedWeekScroll.firstVisibleItemIndex >= 4,
        )
        composeTestRule.onNodeWithText("小说-9009").assertIsDisplayed()

        // 2. Switch to 日榜 (dayVisit): must start at #1 (9101), NOT anchored to 9001/9009 at #14/#15!
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + RankingPeriod.DAY.name)
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            FeedScrollPosition(0, 0),
            repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.dayVisit),
        )
        composeTestRule.onNodeWithText("小说-9101").assertIsDisplayed()

        // 3. Scroll 日榜 (dayVisit) independently to index 6 (#7, aid = 9107)
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performScrollToIndex(6)
        composeTestRule.waitForIdle()
        val savedDayScroll = repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.dayVisit)
        assertTrue(
            "Expected dayVisit scroll index > 0, got $savedDayScroll",
            savedDayScroll.firstVisibleItemIndex >= 2,
        )

        // 4. Switch to 总榜 (allVisit) and 月榜 (monthVisit): both unvisited rankings start at #1
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + RankingPeriod.ALL_TIME.name)
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            FeedScrollPosition(0, 0),
            repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.allVisit),
        )
        composeTestRule.onNodeWithText("小说-9301").assertIsDisplayed()

        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + RankingPeriod.MONTH.name)
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            FeedScrollPosition(0, 0),
            repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.monthVisit),
        )
        composeTestRule.onNodeWithText("小说-9201").assertIsDisplayed()

        // 5. Switch back to 周榜 (weekVisit): restores exact saved scroll progress!
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + RankingPeriod.WEEK.name)
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            savedWeekScroll,
            repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.weekVisit),
        )
        composeTestRule.onNodeWithText("小说-9009").assertIsDisplayed()

        // 6. Re-tap already-selected 周榜 chip -> smoothly scrolls 周榜 back to top (#1)!
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + RankingPeriod.WEEK.name)
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            FeedScrollPosition(0, 0),
            repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.weekVisit),
        )
        composeTestRule.onNodeWithText("小说-9001").assertIsDisplayed()

        // 7. Switch back to 日榜 (dayVisit): still preserved its own scroll position, then
        //    re-tapping active metric button / bottom-nav reselect returns 日榜 to top (#1)!
        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + RankingPeriod.DAY.name)
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            savedDayScroll,
            repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.dayVisit),
        )

        composeTestRule
            .onNodeWithTag(DiscoveryTestTags.RANKING_METRIC_PREFIX + RankingMetric.VISITS.name)
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            FeedScrollPosition(0, 0),
            repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.dayVisit),
        )
        composeTestRule.onNodeWithText("小说-9101").assertIsDisplayed()

        // Also verify bottom-nav reselect trigger scrolls back to top
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performScrollToIndex(6)
        composeTestRule.waitForIdle()
        assertTrue(repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.dayVisit).firstVisibleItemIndex >= 2)
        reselectCounter++
        composeTestRule.waitForIdle()
        assertEquals(
            FeedScrollPosition(0, 0),
            repository.getFeedScrollPosition(Wenku8API.NovelSortedBy.dayVisit),
        )
    }

    @Test
    fun buildDiscoveryFeedEntries_placesAdsAfterEveryPageOfTenAndSuppressesTrailingErrorSlot() {
        fun sampleItems(count: Int): List<DiscoveryNovelCardItem> = (1..count).map { idx ->
            DiscoveryNovelCardItem(
                aid = 7000 + idx,
                title = "作品-$idx",
                author = "作者-$idx",
                bookStatus = "连载中",
                lastUpdate = "2026-11-22",
                tags = emptyList(),
                introShort = "简介-$idx",
                coverUrl = "",
                inLocalBookshelf = false,
            )
        }

        assertTrue(buildDiscoveryFeedEntries(emptyList(), adsEnabled = true).isEmpty())

        // Page 1 (<= 10 novels) emits 0 inline ads so cold-start prioritizes NovelDetailScreen
        val nineItems = buildDiscoveryFeedEntries(sampleItems(9), adsEnabled = true)
        assertEquals(9, nineItems.size)
        assertTrue(nineItems.none { it is DiscoveryFeedEntry.InlineAd })

        val tenItems = buildDiscoveryFeedEntries(sampleItems(10), adsEnabled = true)
        assertEquals(10, tenItems.size)
        assertTrue(tenItems.none { it is DiscoveryFeedEntry.InlineAd })

        // Page 2 loaded (11..20 novels) -> slot 0 placed as the FIRST item of Page 2 (before #11)
        val twentyItems = buildDiscoveryFeedEntries(sampleItems(20), adsEnabled = true)
        assertEquals(21, twentyItems.size)
        assertEquals(DiscoveryFeedEntry.InlineAd(slotIndex = 0), twentyItems[10])
        assertEquals(10, (twentyItems[11] as DiscoveryFeedEntry.Novel).novelIndex)
        assertEquals(19, (twentyItems[20] as DiscoveryFeedEntry.Novel).novelIndex)

        // Page 3 loaded (25 novels) -> ads at start of Page 2 (index 10, before #11) and Page 3 (index 21, before #21)
        val twentyFiveItems = buildDiscoveryFeedEntries(sampleItems(25), adsEnabled = true)
        assertEquals(27, twentyFiveItems.size)
        assertEquals(DiscoveryFeedEntry.InlineAd(slotIndex = 0), twentyFiveItems[10])
        assertEquals(DiscoveryFeedEntry.InlineAd(slotIndex = 1), twentyFiveItems[21])
        assertEquals(20, (twentyFiveItems[22] as DiscoveryFeedEntry.Novel).novelIndex)
        assertEquals(
            listOf(0, 1),
            twentyFiveItems.filterIsInstance<DiscoveryFeedEntry.InlineAd>().map { it.slotIndex },
        )

        // Cache-gated slot filtering: only boundaries with ready cached ads are inserted
        val onlyPage3Cached = buildDiscoveryFeedEntries(
            items = sampleItems(25),
            adsEnabled = true,
            enabledAdSlotIndices = setOf(1),
        )
        assertEquals(26, onlyPage3Cached.size)
        assertEquals(
            listOf(1),
            onlyPage3Cached.filterIsInstance<DiscoveryFeedEntry.InlineAd>().map { it.slotIndex },
        )
        assertEquals(DiscoveryFeedEntry.InlineAd(slotIndex = 1), onlyPage3Cached[20])

        val noneCached = buildDiscoveryFeedEntries(
            items = sampleItems(25),
            adsEnabled = true,
            enabledAdSlotIndices = emptySet(),
        )
        assertEquals(25, noneCached.size)
        assertTrue(noneCached.none { it is DiscoveryFeedEntry.InlineAd })

        // Disabled ads -> exact 1:1 novel list with zero inline ad entries
        val disabled = buildDiscoveryFeedEntries(sampleItems(25), adsEnabled = false)
        assertEquals(25, disabled.size)
        assertTrue(disabled.none { it is DiscoveryFeedEntry.InlineAd })
    }

    @Test
    fun findUpcomingInlineAdSlotsToPrefetch_triggersOnlyWithinLookaheadWindow() {
        val sampleTwentyFive = (1..25).map { idx ->
            DiscoveryNovelCardItem(
                aid = 7100 + idx,
                title = "作品-$idx",
                author = "作者-$idx",
                bookStatus = "连载中",
                lastUpdate = "2026-11-22",
                tags = emptyList(),
                introShort = "简介-$idx",
                coverUrl = "",
                inLocalBookshelf = false,
            )
        }
        // Entries: 0..9 novels, 10 InlineAd(0), 11..20 novels, 21 InlineAd(1), 22..26 novels
        val entries = buildDiscoveryFeedEntries(sampleTwentyFive, adsEnabled = true)

        assertTrue(findUpcomingInlineAdSlotsToPrefetch(emptyList(), lastVisibleIndex = 0).isEmpty())
        assertTrue(findUpcomingInlineAdSlotsToPrefetch(entries, lastVisibleIndex = -1).isEmpty())

        // At scroll top (e.g., lastVisibleIndex = 4 or 5), slot 0 at index 10 is outside 4-item window -> 0 requests
        assertTrue(findUpcomingInlineAdSlotsToPrefetch(entries, lastVisibleIndex = 4).isEmpty())
        assertTrue(findUpcomingInlineAdSlotsToPrefetch(entries, lastVisibleIndex = 5).isEmpty())

        // Within 4 items of slot 0 (index 6..10) -> prefetches slot 0
        assertEquals(listOf(0), findUpcomingInlineAdSlotsToPrefetch(entries, lastVisibleIndex = 6))
        assertEquals(listOf(0), findUpcomingInlineAdSlotsToPrefetch(entries, lastVisibleIndex = 10))

        // Mid-page 2 (index 11..16) -> no slot within lookahead window
        assertTrue(findUpcomingInlineAdSlotsToPrefetch(entries, lastVisibleIndex = 11).isEmpty())
        assertTrue(findUpcomingInlineAdSlotsToPrefetch(entries, lastVisibleIndex = 16).isEmpty())

        // Within 4 items of slot 1 (index 17..21) -> prefetches slot 1
        assertEquals(listOf(1), findUpcomingInlineAdSlotsToPrefetch(entries, lastVisibleIndex = 17))
        assertEquals(listOf(1), findUpcomingInlineAdSlotsToPrefetch(entries, lastVisibleIndex = 21))
    }

    @Test
    fun discoveryFeedInlineAdPool_enforcesBoundedLruEvictionAndReusesActiveSlots() {
        val pool = DiscoveryFeedInlineAdPool(
            initialPhase = BannerAdLoadPhase.StaticPreview,
            maxCachedSlots = 3,
        )
        assertTrue(pool.isAdSlotEligible)

        val slot0 = pool.getOrCreate("weekVisit_slot_0")
        val slot1 = pool.getOrCreate("weekVisit_slot_1")
        val slot2 = pool.getOrCreate("weekVisit_slot_2")
        assertEquals(InlineBannerPlacement.DISCOVERY_FEED_INLINE, slot0.placement)
        assertEquals(setOf("weekVisit_slot_0", "weekVisit_slot_1", "weekVisit_slot_2"), pool.activeSlotKeys())

        // Touching slot 0 reuses the exact holder and refreshes its LRU recency so slot 1 becomes oldest
        assertSame(slot0, pool.getOrCreate("weekVisit_slot_0"))

        val slot3 = pool.getOrCreate("weekVisit_slot_3")
        assertEquals(setOf("weekVisit_slot_0", "weekVisit_slot_2", "weekVisit_slot_3"), pool.activeSlotKeys())
        assertTrue(slot1.isDisposed)
        assertFalse(slot0.isDisposed)
        assertFalse(slot2.isDisposed)
        assertFalse(slot3.isDisposed)

        pool.destroyAll()
        assertTrue(pool.activeSlotKeys().isEmpty())
        assertTrue(slot0.isDisposed)
        assertTrue(slot2.isDisposed)
        assertTrue(slot3.isDisposed)
    }

    @Test
    fun rankingsScreenInlineAds_renderAfterEveryPageOfTenReserveCompactHeightAndPreserveExactRankNumbers() {
        val weekItems = (1..22).map { rank ->
            val aid = 8000 + rank
            ParsedNovelListItem(
                aid = aid,
                title = "榜单作品-$rank",
                author = "作者-$rank",
                bookStatus = "连载中",
                lastUpdate = "2026-11-22",
                tagsCsv = "奇幻 校园",
                introShort = "作品简介-$rank",
            )
        }
        val gateway = FakeDiscoveryGateway().apply {
            pagesBySort[Wenku8API.NovelSortedBy.weekVisit] =
                WenkuApiResult.Success(ParsedNovelListPage(totalPages = 3, items = weekItems))
        }
        val repository = DiscoveryRepository(database = database, apiClient = gateway)

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    RankingsScreen(
                        discoveryRepository = repository,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onOpenSearch = {},
                        onNovelClicked = {},
                        onOpenNetworkDiagnostics = {},
                        isNetworkAvailableForAdOverride = true,
                        isGmsAvailableForAdOverride = true,
                        canRequestAdsForAdOverride = true,
                        enableLiveAdViewInflation = false,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // Top of list starts cleanly with #1 (never an ad at index 0)
        composeTestRule.onNodeWithText("#1").assertIsDisplayed()
        composeTestRule.onNodeWithText("榜单作品-1").assertIsDisplayed()

        // Scroll to first page-end inline ad (index 10, directly after #10)
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performScrollToIndex(10)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("${DiscoveryTestTags.FEED_AD_CARD_PREFIX}0").assertIsDisplayed()
        composeTestRule
            .onNodeWithTag("${DiscoveryTestTags.FEED_AD_SLOT_PREFIX}0")
            .assertHeightIsAtLeast(InlineBannerPlacement.DISCOVERY_FEED_INLINE.reservedMinHeight)
        composeTestRule.onNodeWithText("广告").assertIsDisplayed()

        // Novel immediately after page-1 ad must preserve unbroken rank #11
        composeTestRule.onNodeWithText("#11").assertIsDisplayed()
        composeTestRule.onNodeWithText("榜单作品-11").assertIsDisplayed()

        // Scroll to second page-end inline ad (index 21, directly after #20) and verify rank #21 follows cleanly
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performScrollToIndex(21)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("${DiscoveryTestTags.FEED_AD_CARD_PREFIX}1").assertIsDisplayed()
        composeTestRule.onNodeWithText("#21").assertIsDisplayed()
        composeTestRule.onNodeWithText("榜单作品-21").assertIsDisplayed()
    }

    @Test
    fun rankingsScreenInlineAds_skipsAllPaginationAdsWhenNoPreloadedAdIsCached() {
        val weekItems = (1..22).map { rank ->
            ParsedNovelListItem(
                aid = 8200 + rank,
                title = "无缓存榜单-$rank",
                author = "作者-$rank",
                bookStatus = "连载中",
                lastUpdate = "2026-11-25",
                tagsCsv = "奇幻",
                introShort = "作品简介-$rank",
            )
        }
        val gateway = FakeDiscoveryGateway().apply {
            pagesBySort[Wenku8API.NovelSortedBy.weekVisit] =
                WenkuApiResult.Success(ParsedNovelListPage(totalPages = 3, items = weekItems))
        }
        val repository = DiscoveryRepository(database = database, apiClient = gateway)

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    RankingsScreen(
                        discoveryRepository = repository,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onOpenSearch = {},
                        onNovelClicked = {},
                        onOpenNetworkDiagnostics = {},
                        isNetworkAvailableForAdOverride = true,
                        isGmsAvailableForAdOverride = true,
                        canRequestAdsForAdOverride = true,
                        enableLiveAdViewInflation = false,
                        hasPreloadedFeedAdOverride = false,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performScrollToIndex(10)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("${DiscoveryTestTags.FEED_AD_CARD_PREFIX}0").assertDoesNotExist()
        composeTestRule.onNodeWithText("无缓存榜单-11").assertIsDisplayed()

        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performScrollToIndex(20)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("${DiscoveryTestTags.FEED_AD_CARD_PREFIX}1").assertDoesNotExist()
        composeTestRule.onNodeWithText("无缓存榜单-21").assertIsDisplayed()
    }

    @Test
    fun rankingsScreenInlineAds_collapseCompletelyWhenConsentNotGranted() {
        val weekItems = (1..15).map { rank ->
            ParsedNovelListItem(
                aid = 8700 + rank,
                title = "无同意榜单-$rank",
                author = "作者-$rank",
                bookStatus = "连载中",
                lastUpdate = "2026-11-22",
                tagsCsv = "奇幻",
                introShort = "简介-$rank",
            )
        }
        val gateway = FakeDiscoveryGateway().apply {
            pagesBySort[Wenku8API.NovelSortedBy.weekVisit] =
                WenkuApiResult.Success(ParsedNovelListPage(totalPages = 2, items = weekItems))
        }
        val repository = DiscoveryRepository(database = database, apiClient = gateway)

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    RankingsScreen(
                        discoveryRepository = repository,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onOpenSearch = {},
                        onNovelClicked = {},
                        onOpenNetworkDiagnostics = {},
                        isNetworkAvailableForAdOverride = true,
                        isGmsAvailableForAdOverride = true,
                        canRequestAdsForAdOverride = false,
                        enableLiveAdViewInflation = false,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performScrollToIndex(10)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("${DiscoveryTestTags.FEED_AD_CARD_PREFIX}0").assertDoesNotExist()
        composeTestRule.onNodeWithText("无同意榜单-11").assertIsDisplayed()
    }

    @Test
    fun latestScreenInlineAds_collapseCompletelyWhenOffline() {
        val recentItems = (1..15).map { idx ->
            ParsedNovelListItem(
                aid = 8500 + idx,
                title = "离线最新-$idx",
                author = "作者-$idx",
                bookStatus = "连载中",
                lastUpdate = "2026-11-22",
                tagsCsv = "奇幻",
                introShort = "简介-$idx",
            )
        }
        val gateway = FakeDiscoveryGateway().apply {
            pagesBySort[Wenku8API.NovelSortedBy.lastUpdate] =
                WenkuApiResult.Success(ParsedNovelListPage(totalPages = 2, items = recentItems))
        }
        val repository = DiscoveryRepository(database = database, apiClient = gateway)

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    LatestScreen(
                        discoveryRepository = repository,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onOpenSearch = {},
                        onNovelClicked = {},
                        onOpenNetworkDiagnostics = {},
                        isNetworkAvailableForAdOverride = false,
                        isGmsAvailableForAdOverride = true,
                        canRequestAdsForAdOverride = true,
                        enableLiveAdViewInflation = false,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(DiscoveryTestTags.FEED_LIST).performScrollToIndex(5)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("${DiscoveryTestTags.FEED_AD_CARD_PREFIX}0").assertDoesNotExist()
        composeTestRule.onNodeWithText("离线最新-6").assertIsDisplayed()
    }

    private class FakeDiscoveryGateway : WenkuCatalogGateway {
        override val isApiConfigured: Boolean = true
        val pagesBySort = mutableMapOf<Wenku8API.NovelSortedBy, WenkuApiResult<ParsedNovelListPage>>()
        val requestedSorts = mutableListOf<Wenku8API.NovelSortedBy>()

        override fun resolveCoverUrl(aid: Int): String = ""

        override suspend fun fetchNovelListPage(
            sortedBy: Wenku8API.NovelSortedBy,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListPage> {
            requestedSorts.add(sortedBy)
            return pagesBySort[sortedBy] ?: WenkuApiResult.NetworkError("unmapped sort")
        }

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
        ) = WenkuApiResult.NetworkError("unused")

        override suspend fun fetchChapterContent(
            aid: Int,
            cid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<String> = WenkuApiResult.NetworkError("unused")

        override suspend fun fetchCloudBookshelf(
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.NetworkError("unused")
    }
}
