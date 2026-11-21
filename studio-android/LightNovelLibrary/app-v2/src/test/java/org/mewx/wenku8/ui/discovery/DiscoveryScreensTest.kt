package org.mewx.wenku8.ui.discovery

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
import org.mewx.wenku8.domain.model.FeedScrollPosition
import org.mewx.wenku8.domain.model.LatestFeedCategory
import org.mewx.wenku8.domain.model.RankingMetric
import org.mewx.wenku8.domain.model.RankingPeriod
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
