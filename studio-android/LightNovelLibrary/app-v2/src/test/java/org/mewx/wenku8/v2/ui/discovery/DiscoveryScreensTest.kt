package org.mewx.wenku8.v2.ui.discovery

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.remote.api.OnlineSearchField
import org.mewx.wenku8.v2.data.remote.api.WenkuApiResult
import org.mewx.wenku8.v2.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.v2.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.v2.data.repository.DiscoveryRepository
import org.mewx.wenku8.v2.domain.model.LatestFeedCategory
import org.mewx.wenku8.v2.domain.model.RankingMetric
import org.mewx.wenku8.v2.domain.model.RankingPeriod
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
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

    private class FakeDiscoveryGateway : WenkuCatalogGateway {
        override val isApiConfigured: Boolean = true
        val pagesBySort = mutableMapOf<Wenku8API.NovelSortedBy, WenkuApiResult<ParsedNovelListPage>>()

        override fun resolveCoverUrl(aid: Int): String = ""

        override suspend fun fetchNovelListPage(
            sortedBy: Wenku8API.NovelSortedBy,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListPage> {
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
