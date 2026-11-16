package org.mewx.wenku8.v2.ui.search

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
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
import org.mewx.wenku8.v2.domain.model.UnifiedSearchMode
import org.mewx.wenku8.v2.ui.discovery.DiscoveryTestTags
import org.mewx.wenku8.v2.ui.navigation.AdaptiveMainScaffold
import org.mewx.wenku8.v2.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class UnifiedSearchScreenTest {

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
    fun instantLocalShelfFilterHistoryManagementAndDirectAidValidationWork() {
        runBlocking {
            database.novelDao().upsertNovel(
                NovelEntity(
                    aid = 5,
                    title = "狼与香辛料",
                    author = "支仓冻砂",
                    bookStatus = "已完成",
                    inBookshelf = true,
                ),
            )
        }

        val gateway = FakeSearchGateway().apply {
            fullMetaByAid[1306] = ParsedNovelFullMeta(
                aid = 1306,
                title = "向森之魔物献上花束",
                author = "小木君人",
                dayHitsCount = 12,
                totalHitsCount = 50000,
                pushCount = 3200,
                favCount = 1800,
                pressId = "GAGAGA文库",
                bookStatus = "已完成",
                bookLength = 125000,
                lastUpdate = "2012-11-02",
                latestSectionCid = 41001,
                latestSectionName = "第一卷 插图",
            )
        }
        val clockMillis = AtomicLong(10_000L)
        val repository = DiscoveryRepository(
            database = database,
            apiClient = gateway,
            monotonicClockMillis = { clockMillis.get() },
        )
        runBlocking {
            repository.recordSearchHistory("支仓冻砂")
            repository.recordSearchHistory("刀剑神域")
        }

        var selectedNovelAid = -1

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    UnifiedSearchScreen(
                        discoveryRepository = repository,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onNavigateBack = {},
                        onNovelClicked = { selectedNovelAid = it.aid },
                        onOpenNetworkDiagnostics = {},
                        autoFocusInputOnEntry = false,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        // History chips rendered
        composeTestRule.onNodeWithTag("${UnifiedSearchTestTags.HISTORY_CHIP_PREFIX}刀剑神域").assertIsDisplayed()
        composeTestRule.onNodeWithTag("${UnifiedSearchTestTags.HISTORY_CHIP_PREFIX}支仓冻砂").assertIsDisplayed()

        // Delete single history chip
        composeTestRule.onNodeWithTag("${UnifiedSearchTestTags.DELETE_HISTORY_PREFIX}刀剑神域").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("${UnifiedSearchTestTags.HISTORY_CHIP_PREFIX}刀剑神域").assertDoesNotExist()

        // Type partial title -> instant local bookshelf match without online search!
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.QUERY_INPUT).performTextReplacement("香辛料")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.LOCAL_SECTION_HEADER).assertIsDisplayed()
        composeTestRule.onNodeWithText("狼与香辛料").assertIsDisplayed()
        assertEquals(0, gateway.onlineSearchCallCount)

        // Switch to DIRECT_AID mode with non-numeric text -> shows validation message
        composeTestRule
            .onNodeWithTag(UnifiedSearchTestTags.MODE_BUTTON_PREFIX + UnifiedSearchMode.DIRECT_AID.name)
            .performClick()
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.SUBMIT_BUTTON).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.DIRECT_AID_ERROR).assertIsDisplayed()

        // Valid Direct AID ("AID 1306") -> immediate zero-cooldown meta lookup
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.QUERY_INPUT).performTextReplacement("AID 1306")
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.SUBMIT_BUTTON).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("向森之魔物献上花束").assertIsDisplayed()
        composeTestRule.onNodeWithTag("${DiscoveryTestTags.NOVEL_CARD_PREFIX}1306").performClick()
        assertEquals(1306, selectedNovelAid)
    }

    @Test
    fun secondOnlineSearchWithinFiveSecondsQueuesWithCountdownAndSupportsCancel() {
        val gateway = FakeSearchGateway().apply {
            searchResultsByQuery["败犬女主"] = listOf(2600)
            shortInfoByAid[2600] = ParsedNovelListItem(
                aid = 2600,
                title = "败犬女主太多了！",
                author = "雨森焚火",
                bookStatus = "连载中",
                lastUpdate = "2026-10-06",
                tagsCsv = "",
                introShort = "八奈见杏菜的青春故事。",
            )
            searchResultsByQuery["100% 恋爱"] = listOf(2600)
        }

        // Fixed clock keeps remaining cooldown at 4,000ms until cancelled.
        val clockMillis = AtomicLong(50_000L)
        val repository = DiscoveryRepository(
            database = database,
            apiClient = gateway,
            monotonicClockMillis = { clockMillis.get() },
        )

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.E_INK) {
                    UnifiedSearchScreen(
                        discoveryRepository = repository,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onNavigateBack = {},
                        onNovelClicked = {},
                        onOpenNetworkDiagnostics = {},
                        autoFocusInputOnEntry = false,
                    )
                }
            }
        }

        // First search executes immediately (no cooldown)
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.QUERY_INPUT).performTextReplacement("败犬女主")
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.SUBMIT_BUTTON).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("败犬女主太多了！").assertIsDisplayed()
        assertEquals(1, gateway.onlineSearchCallCount)

        // Advance monotonic clock by only 1,000ms (4.0s cooldown left) and submit query containing '%'
        clockMillis.set(51_000L)
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.QUERY_INPUT).performTextReplacement("100% 恋爱")
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.SUBMIT_BUTTON).performClick()
        composeTestRule.waitForIdle()

        // Cooldown banner is displayed with static E-Ink progress and [取消] button
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.COOLDOWN_BANNER).assertIsDisplayed()
        assertEquals(1, gateway.onlineSearchCallCount)

        // Cancel queued cooldown search cleanly
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.CANCEL_COOLDOWN_BUTTON).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.COOLDOWN_BANNER).assertDoesNotExist()
    }

    @Test
    fun adaptiveMainScaffoldOpensAndClosesUnifiedSearchOverlay() {
        val gateway = FakeSearchGateway()
        val repository = DiscoveryRepository(database = database, apiClient = gateway)

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    AdaptiveMainScaffold(
                        preferredHomeDestination = DefaultHomeDestination.BOOKSHELF,
                        isNetworkAvailableOnColdStart = true,
                        currentThemeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                        onThemeModeSelected = {},
                        onDefaultHomeSelected = {},
                        discoveryRepository = repository,
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(DiscoveryTestTags.PERSISTENT_SEARCH_PILL).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.BACK_BUTTON).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(UnifiedSearchTestTags.SCREEN_ROOT).assertDoesNotExist()
    }

    private class FakeSearchGateway : WenkuCatalogGateway {
        override val isApiConfigured: Boolean = true
        var onlineSearchCallCount: Int = 0
        val searchResultsByQuery = mutableMapOf<String, List<Int>>()
        val shortInfoByAid = mutableMapOf<Int, ParsedNovelListItem>()
        val fullMetaByAid = mutableMapOf<Int, ParsedNovelFullMeta>()

        override fun resolveCoverUrl(aid: Int): String = ""

        override suspend fun fetchNovelListPage(
            sortedBy: Wenku8API.NovelSortedBy,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListPage> = WenkuApiResult.Success(
            ParsedNovelListPage(totalPages = 1, items = emptyList()),
        )

        override suspend fun searchNovelsByField(
            query: String,
            searchField: OnlineSearchField,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<Int>> {
            onlineSearchCallCount += 1
            return WenkuApiResult.Success(searchResultsByQuery[query].orEmpty())
        }

        override suspend fun fetchNovelShortInfo(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListItem> {
            return shortInfoByAid[aid]?.let { WenkuApiResult.Success(it) }
                ?: WenkuApiResult.NetworkError("missing short info")
        }

        override suspend fun fetchNovelFullMeta(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelFullMeta> {
            return fullMetaByAid[aid]?.let { WenkuApiResult.Success(it) }
                ?: WenkuApiResult.NetworkError("missing full meta")
        }

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
