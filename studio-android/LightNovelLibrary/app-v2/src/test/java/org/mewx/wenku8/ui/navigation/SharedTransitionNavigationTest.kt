package org.mewx.wenku8.ui.navigation

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.MainActivity
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.download.NovelDownloadRepository
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.data.remote.api.OnlineSearchField
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.data.repository.BookshelfRepository
import org.mewx.wenku8.data.repository.DiscoveryRepository
import org.mewx.wenku8.data.repository.NovelDetailRepository
import org.mewx.wenku8.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.data.repository.ParsedChapterToc
import org.mewx.wenku8.data.repository.ParsedVolumeToc
import org.mewx.wenku8.ui.bookshelf.BookshelfTestTags
import org.mewx.wenku8.ui.detail.NovelDetailTestTags
import org.mewx.wenku8.ui.discovery.DiscoveryTestTags
import org.mewx.wenku8.ui.search.UnifiedSearchTestTags
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executor

/**
 * Verifies the Material 3 Expressive (`SharedTransitionLayout` + `AnimatedContent`) motion system
 * across Searchbox morphing (`PersistentSearchEntryPill` / `BookshelfSearchBarRow` <->
 * `UnifiedSearchScreen`), Novel Cover + Title shared-element morphing (`DiscoveryNovelCard` /
 * `BookshelfScreen` <-> `NovelDetailScreen`), and directional top-level tab transitions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class SharedTransitionNavigationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var database: Wenku8Database
    private lateinit var tempAssetRoot: File
    private lateinit var testScope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directExecutor = Executor { runnable -> runnable.run() }
        database = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        tempAssetRoot = Files.createTempDirectory("wenku8_v2_motion_test").toFile()
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        testScope.cancel()
        database.close()
        tempAssetRoot.deleteRecursively()
    }

    @Test
    fun discoverySearchBoxAndNovelCoverSharedTransitions_morphCleanlyAndReturnHome() {
        val fakeGateway = FakeMotionCatalogGateway()
        val tocSyncRepository = NovelTocSyncRepository(database)
        val discoveryRepository = DiscoveryRepository(
            database = database,
            apiClient = fakeGateway,
        )
        val bookshelfRepository = BookshelfRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            userSessionRepository = null,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val novelDetailRepository = NovelDetailRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val novelDownloadRepository = NovelDownloadRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            assetRootsProvider = { listOf(tempAssetRoot) },
            applicationScope = testScope,
            appContext = null,
            ioDispatcher = Dispatchers.Unconfined,
        )

        composeRule.setContent {
            Wenku8ExpressiveTheme(
                themeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
            ) {
                AdaptiveMainScaffold(
                    preferredHomeDestination = DefaultHomeDestination.LATEST,
                    isNetworkAvailableOnColdStart = true,
                    currentThemeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                    onThemeModeSelected = {},
                    onDefaultHomeSelected = {},
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    discoveryRepository = discoveryRepository,
                    bookshelfRepository = bookshelfRepository,
                    novelDetailRepository = novelDetailRepository,
                    novelDownloadRepository = novelDownloadRepository,
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Tap PersistentSearchEntryPill on LatestScreen -> Searchbox morphs into UnifiedSearchScreen
        composeRule.onNodeWithTag(DiscoveryTestTags.PERSISTENT_SEARCH_PILL)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(UnifiedSearchTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(UnifiedSearchTestTags.QUERY_INPUT)
            .assertIsDisplayed()
            .performTextInput("刀剑")
        composeRule.onNodeWithTag(UnifiedSearchTestTags.SUBMIT_BUTTON).performClick()
        composeRule.waitForIdle()

        // 2. Tap online search result -> Novel cover & title morph into NovelDetailScreen
        composeRule.onNodeWithTag("${DiscoveryTestTags.NOVEL_CARD_PREFIX}1143")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(NovelDetailTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(NovelDetailTestTags.NOVEL_TITLE_TEXT).assertIsDisplayed()

        // 3. Navigate back from NovelDetailScreen -> returns smoothly to UnifiedSearchScreen
        //    with query and online results preserved so user can inspect other search hits
        composeRule.onNodeWithTag(NovelDetailTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(UnifiedSearchTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(UnifiedSearchTestTags.QUERY_INPUT).assertTextContains("刀剑")
        composeRule.onNodeWithTag("${DiscoveryTestTags.NOVEL_CARD_PREFIX}1143").assertIsDisplayed()

        // 4. Navigate back from UnifiedSearchScreen -> Searchbox morphs back to LatestScreen pill
        composeRule.onNodeWithTag(UnifiedSearchTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DiscoveryTestTags.PERSISTENT_SEARCH_PILL).assertIsDisplayed()

        // 5. Switch to Bookshelf tab -> directional tab transition -> tap 搜全站 searchbox morph
        //    and verify fresh search session starts clean without leftover results
        composeRule.onNodeWithTag("${MainScaffoldTestTags.NAV_ITEM_PREFIX}bookshelf").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.ONLINE_SEARCH_TRIGGER)
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(UnifiedSearchTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag("${DiscoveryTestTags.NOVEL_CARD_PREFIX}1143").assertDoesNotExist()
    }

    @Test
    fun bookshelfDuplicateCoverOriginsAndEinkSnap_transitionWithoutKeyCollision(): Unit = runBlocking {
        // Seed the same novel (aid = 1143) into BOTH Bookshelf AND ReadingProgress so its cover
        // appears simultaneously in ContinueReadingCarouselSection ("shelf_carousel") and
        // BookshelfGridItemCard ("shelf_card").
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 1143,
                title = "刀剑神域 Sword Art Online",
                author = "川原砾",
                inBookshelf = true,
            ),
        )
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 1143,
                vid = 1,
                cid = 5001,
                volumeTitle = "第一卷 艾恩葛朗特",
                chapterTitle = "第一章 剑的世界",
                progressPermille = 350,
                updatedAtMs = 1_700_000_000_000L,
            ),
        )

        val fakeGateway = FakeMotionCatalogGateway()
        val tocSyncRepository = NovelTocSyncRepository(database)
        val discoveryRepository = DiscoveryRepository(database = database, apiClient = fakeGateway)
        val bookshelfRepository = BookshelfRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            userSessionRepository = null,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val novelDetailRepository = NovelDetailRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val novelDownloadRepository = NovelDownloadRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            assetRootsProvider = { listOf(tempAssetRoot) },
            applicationScope = testScope,
            appContext = null,
            ioDispatcher = Dispatchers.Unconfined,
        )

        composeRule.setContent {
            Wenku8ExpressiveTheme(
                themeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
            ) {
                AdaptiveMainScaffold(
                    preferredHomeDestination = DefaultHomeDestination.BOOKSHELF,
                    isNetworkAvailableOnColdStart = true,
                    currentThemeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                    onThemeModeSelected = {},
                    onDefaultHomeSelected = {},
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    discoveryRepository = discoveryRepository,
                    bookshelfRepository = bookshelfRepository,
                    novelDetailRepository = novelDetailRepository,
                    novelDownloadRepository = novelDownloadRepository,
                )
            }
        }
        composeRule.waitForIdle()

        // Both Carousel card and Grid card for aid=1143 coexist without SharedContentState key collision
        composeRule.onNodeWithTag("${BookshelfTestTags.CONTINUE_READING_ITEM_PREFIX}1143")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(NovelDetailTestTags.SCREEN_ROOT).assertIsDisplayed()

        // Pop back to Bookshelf, then morph from the Grid card ("shelf_card" origin)
        composeRule.onNodeWithTag(NovelDetailTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1143")
            .assertIsDisplayed()
            .performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(NovelDetailTestTags.SCREEN_ROOT).assertIsDisplayed()
    }

    @Test
    fun bookshelfLocalSearchAndFilter_preservedAcrossNovelDetailNavigationAndTabSwitches(): Unit = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 1143,
                title = "刀剑神域 Sword Art Online",
                author = "川原砾",
                bookStatus = "连载中",
                lastUpdate = "2026-11-03",
                inBookshelf = true,
            ),
        )
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 1500,
                title = "刀剑神域 Progressive",
                author = "川原砾",
                bookStatus = "已完结",
                lastUpdate = "2026-11-02",
                inBookshelf = true,
            ),
        )
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 2906,
                title = "败犬女主太多了！",
                author = "雨森焚火",
                bookStatus = "连载中",
                lastUpdate = "2026-11-01",
                inBookshelf = true,
            ),
        )

        val fakeGateway = FakeMotionCatalogGateway()
        val tocSyncRepository = NovelTocSyncRepository(database)
        val discoveryRepository = DiscoveryRepository(database = database, apiClient = fakeGateway)
        val bookshelfRepository = BookshelfRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            userSessionRepository = null,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val novelDetailRepository = NovelDetailRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val novelDownloadRepository = NovelDownloadRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            assetRootsProvider = { listOf(tempAssetRoot) },
            applicationScope = testScope,
            appContext = null,
            ioDispatcher = Dispatchers.Unconfined,
        )

        composeRule.setContent {
            Wenku8ExpressiveTheme(
                themeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
            ) {
                AdaptiveMainScaffold(
                    preferredHomeDestination = DefaultHomeDestination.BOOKSHELF,
                    isNetworkAvailableOnColdStart = true,
                    currentThemeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                    onThemeModeSelected = {},
                    onDefaultHomeSelected = {},
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    discoveryRepository = discoveryRepository,
                    bookshelfRepository = bookshelfRepository,
                    novelDetailRepository = novelDetailRepository,
                    novelDownloadRepository = novelDownloadRepository,
                )
            }
        }
        composeRule.waitForIdle()

        // All 3 shelf novels are initially visible
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1143").assertIsDisplayed()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1500").assertIsDisplayed()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}2906").assertIsDisplayed()

        // Filter shelf locally by "刀剑" -> 2906 is filtered out, 1143 & 1500 remain
        composeRule.onNodeWithTag(BookshelfTestTags.LOCAL_SEARCH_INPUT)
            .assertIsDisplayed()
            .performTextInput("刀剑")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1143").assertIsDisplayed()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1500").assertIsDisplayed()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}2906").assertDoesNotExist()

        // Tap first matching novel (1143) -> opens NovelDetailScreen
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1143").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(NovelDetailTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(NovelDetailTestTags.NOVEL_TITLE_TEXT)
            .assertTextContains("刀剑神域 Sword Art Online")

        // Navigate back -> local search query "刀剑" MUST remain active so user can tap another match
        composeRule.onNodeWithTag(NovelDetailTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(BookshelfTestTags.LOCAL_SEARCH_INPUT).assertTextContains("刀剑")
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}2906").assertDoesNotExist()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1143").assertIsDisplayed()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1500").assertIsDisplayed()

        // Tap second matching novel (1500) directly from preserved search results
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1500").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(NovelDetailTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(NovelDetailTestTags.NOVEL_TITLE_TEXT)
            .assertTextContains("刀剑神域 Progressive")

        // Navigate back again -> still filtered to "刀剑"
        composeRule.onNodeWithTag(NovelDetailTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}2906").assertDoesNotExist()

        // Switch to Latest tab and back to Bookshelf tab -> local search state still preserved
        composeRule.onNodeWithTag("${MainScaffoldTestTags.NAV_ITEM_PREFIX}latest").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DiscoveryTestTags.PERSISTENT_SEARCH_PILL).assertIsDisplayed()

        composeRule.onNodeWithTag("${MainScaffoldTestTags.NAV_ITEM_PREFIX}bookshelf").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(BookshelfTestTags.LOCAL_SEARCH_INPUT).assertTextContains("刀剑")
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}2906").assertDoesNotExist()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1143").assertIsDisplayed()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1500").assertIsDisplayed()
    }

    @Test
    fun shareBookDeepLink_resolvesInManifest_parsesUrlVariants_andOpensNovelDetailScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // 1. Verify AndroidManifest.xml registers https & http share.wenku8.app/book/<aid> for MainActivity
        for (url in listOf(
            "https://share.wenku8.app/book/2906",
            "http://share.wenku8.app/book/1500",
        )) {
            val viewIntent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addCategory(Intent.CATEGORY_BROWSABLE)
                setPackage(context.packageName)
            }
            val resolved = context.packageManager.queryIntentActivities(
                viewIntent,
                PackageManager.MATCH_DEFAULT_ONLY,
            )
            assertTrue(
                "Expected MainActivity to resolve $url, got $resolved",
                resolved.any { it.activityInfo.name == MainActivity::class.java.name },
            )
        }

        // 2. Verify Wenku8DeepLinks & DiscoveryRepository.parseDirectAidOrNull parsing
        assertEquals(
            2906,
            Wenku8DeepLinks.parseShareBookIntentAid(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://share.wenku8.app/book/2906")),
            ),
        )
        assertEquals(
            1306,
            Wenku8DeepLinks.parseShareBookIntentAid(
                Intent(Intent.ACTION_VIEW, Uri.parse("http://share.wenku8.app/book/1306/?from=share#top")),
            ),
        )
        assertEquals(
            5,
            Wenku8DeepLinks.parseShareBookUriAid(Uri.parse("https://share.wenku8.app/book/5.htm")),
        )
        assertEquals(
            5,
            DiscoveryRepository.parseDirectAidOrNull(
                "《狼与香辛料》（作者：支仓冻砂） https://share.wenku8.app/book/5",
            ),
        )
        assertNull(
            Wenku8DeepLinks.parseShareBookIntentAid(
                Intent(Intent.ACTION_MAIN, Uri.parse("https://share.wenku8.app/book/2906")),
            ),
        )
        assertNull(Wenku8DeepLinks.parseShareBookUriAid(Uri.parse("https://share.wenku8.app/")))
        assertNull(Wenku8DeepLinks.parseShareBookUriAid(Uri.parse("https://share.wenku8.app/book/0")))
        assertNull(Wenku8DeepLinks.parseShareBookUriAid(Uri.parse("https://share.wenku8.app/book/abc")))
        assertNull(Wenku8DeepLinks.parseShareBookUriAid(Uri.parse("https://www.wenku8.net/book/5.htm")))

        // 3. Verify AdaptiveMainScaffold opens NovelDetailScreen on cold-start & onNewIntent deep links
        val fakeGateway = FakeMotionCatalogGateway()
        val tocSyncRepository = NovelTocSyncRepository(database)
        val discoveryRepository = DiscoveryRepository(database = database, apiClient = fakeGateway)
        val bookshelfRepository = BookshelfRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            userSessionRepository = null,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val novelDetailRepository = NovelDetailRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )
        val novelDownloadRepository = NovelDownloadRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            assetRootsProvider = { listOf(tempAssetRoot) },
            applicationScope = testScope,
            appContext = null,
            ioDispatcher = Dispatchers.Unconfined,
        )

        var pendingExternalAid by mutableIntStateOf(2906)

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                AdaptiveMainScaffold(
                    preferredHomeDestination = DefaultHomeDestination.LATEST,
                    isNetworkAvailableOnColdStart = true,
                    currentThemeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                    onThemeModeSelected = {},
                    onDefaultHomeSelected = {},
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    discoveryRepository = discoveryRepository,
                    bookshelfRepository = bookshelfRepository,
                    novelDetailRepository = novelDetailRepository,
                    novelDownloadRepository = novelDownloadRepository,
                    externalOpenNovelAid = pendingExternalAid,
                    onExternalLaunchConsumed = { pendingExternalAid = 0 },
                )
            }
        }
        composeRule.waitForIdle()

        // Cold-start deep link opened aid = 2906 and consumed pendingExternalAid
        assertEquals(0, pendingExternalAid)
        composeRule.onNodeWithTag(NovelDetailTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(NovelDetailTestTags.NOVEL_TITLE_TEXT)
            .assertTextContains("败犬女主太多了！")

        // Navigate back to Home -> open UnifiedSearchScreen -> simulate onNewIntent deep link for aid = 1500
        composeRule.onNodeWithTag(NovelDetailTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DiscoveryTestTags.PERSISTENT_SEARCH_PILL).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(UnifiedSearchTestTags.SCREEN_ROOT).assertIsDisplayed()

        pendingExternalAid = 1500
        composeRule.waitForIdle()

        assertEquals(0, pendingExternalAid)
        composeRule.onNodeWithTag(NovelDetailTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(NovelDetailTestTags.NOVEL_TITLE_TEXT)
            .assertTextContains("刀剑神域 Progressive")

        // Back from deep-linked detail returns directly to Home (Search overlay was cleanly dismissed)
        composeRule.onNodeWithTag(NovelDetailTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(DiscoveryTestTags.PERSISTENT_SEARCH_PILL).assertIsDisplayed()
    }

    @Test
    fun bookshelfLocalQuery_triggersImmediateOnlineSearchOnClickAndClearsOnTabReselect(): Unit = runBlocking {
        database.novelDao().upsertNovels(
            listOf(
                NovelEntity(
                    aid = 2906,
                    title = "败犬女主太多了！",
                    author = "雨森焚火",
                    inBookshelf = true,
                    shelfSortOrder = 1,
                ),
                NovelEntity(
                    aid = 1500,
                    title = "刀剑神域 Progressive",
                    author = "川原砾",
                    inBookshelf = true,
                    shelfSortOrder = 2,
                ),
            ),
        )

        val fakeGateway = FakeMotionCatalogGateway()
        val tocSyncRepository = NovelTocSyncRepository(database)
        val discoveryRepository = DiscoveryRepository(database = database, apiClient = fakeGateway)
        val bookshelfRepository = BookshelfRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            userSessionRepository = null,
            ioDispatcher = Dispatchers.Unconfined,
        )

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                AdaptiveMainScaffold(
                    preferredHomeDestination = DefaultHomeDestination.BOOKSHELF,
                    isNetworkAvailableOnColdStart = true,
                    currentThemeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                    onThemeModeSelected = {},
                    onDefaultHomeSelected = {},
                    database = database,
                    catalogGateway = fakeGateway,
                    novelTocSyncRepository = tocSyncRepository,
                    discoveryRepository = discoveryRepository,
                    bookshelfRepository = bookshelfRepository,
                )
            }
        }
        composeRule.waitForIdle()

        // 1. Both shelf books are initially visible
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}2906").assertIsDisplayed()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1500").assertIsDisplayed()

        // 2. Type keyword into Bookshelf local search -> filters out non-matching book
        composeRule.onNodeWithTag(BookshelfTestTags.LOCAL_SEARCH_INPUT).performTextInput("刀剑")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1500").assertIsDisplayed()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}2906").assertDoesNotExist()
        assertEquals(emptyList<String>(), fakeGateway.recordedSearchQueries)

        // 3. Tap `搜全站` -> carries keyword into UnifiedSearchScreen AND immediately triggers online search
        composeRule.onNodeWithTag(BookshelfTestTags.ONLINE_SEARCH_TRIGGER).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(UnifiedSearchTestTags.SCREEN_ROOT).assertIsDisplayed()
        composeRule.onNodeWithTag(UnifiedSearchTestTags.QUERY_INPUT).assertTextContains("刀剑")
        composeRule.waitUntil(timeoutMillis = 5_000) {
            fakeGateway.recordedSearchQueries.contains("刀剑")
        }
        assertEquals(listOf("刀剑"), fakeGateway.recordedSearchQueries)
        composeRule.onNodeWithTag("${DiscoveryTestTags.NOVEL_CARD_PREFIX}1143").assertIsDisplayed()

        // 4. Return to Bookshelf -> local search is still active, then reselecting Bookshelf tab clears it
        //    so the full bookshelf is displayed again!
        composeRule.onNodeWithTag(UnifiedSearchTestTags.BACK_BUTTON).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}2906").assertDoesNotExist()

        composeRule.onNodeWithTag("${MainScaffoldTestTags.NAV_ITEM_PREFIX}bookshelf").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}2906").assertIsDisplayed()
        composeRule.onNodeWithTag("${BookshelfTestTags.BOOK_CARD_PREFIX}1500").assertIsDisplayed()
    }
}

private class FakeMotionCatalogGateway : WenkuCatalogGateway {
    override val isApiConfigured: Boolean = true
    val recordedSearchQueries = mutableListOf<String>()

    private val sampleItem = ParsedNovelListItem(
        aid = 1143,
        title = "刀剑神域 Sword Art Online",
        author = "川原砾",
        bookStatus = "连载中",
        lastUpdate = "2026-11-01",
        tagsCsv = "科幻 冒险",
        introShort = "次世代 VRMMORPG 冒险物语。",
        totalHits = 1000,
        pushCount = 100,
        favCount = 50,
    )

    private fun resolveTitle(aid: Int): String = when (aid) {
        1500 -> "刀剑神域 Progressive"
        2906 -> "败犬女主太多了！"
        else -> sampleItem.title
    }

    override fun resolveCoverUrl(aid: Int): String = ""

    override suspend fun fetchNovelListPage(
        sortedBy: Wenku8API.NovelSortedBy,
        pageNumber: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListPage> {
        return WenkuApiResult.Success(
            ParsedNovelListPage(
                totalPages = 1,
                items = listOf(sampleItem),
            ),
        )
    }

    override suspend fun searchNovelsByField(
        query: String,
        searchField: OnlineSearchField,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<Int>> {
        recordedSearchQueries.add(query)
        return WenkuApiResult.Success(listOf(sampleItem.aid))
    }

    override suspend fun fetchNovelShortInfo(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelListItem> = WenkuApiResult.Success(
        sampleItem.copy(aid = aid, title = resolveTitle(aid)),
    )

    override suspend fun fetchNovelFullMeta(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelFullMeta> {
        return WenkuApiResult.Success(
            ParsedNovelFullMeta(
                aid = aid,
                title = resolveTitle(aid),
                author = sampleItem.author,
                dayHitsCount = 10,
                totalHitsCount = 1000,
                pushCount = 100,
                favCount = 50,
                pressId = "电击文库",
                bookStatus = sampleItem.bookStatus,
                bookLength = 250_000,
                lastUpdate = sampleItem.lastUpdate,
                latestSectionCid = 5001,
                latestSectionName = "第一卷 第一章",
                tagsCsv = sampleItem.tagsCsv,
            ),
        )
    }

    override suspend fun fetchNovelFullIntro(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> = WenkuApiResult.Success(sampleItem.introShort)

    override suspend fun fetchNovelVolumeToc(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedVolumeToc>> {
        return WenkuApiResult.Success(
            listOf(
                ParsedVolumeToc(
                    vid = 1,
                    volumeTitle = "第一卷 艾恩葛朗特",
                    chapters = listOf(
                        ParsedChapterToc(cid = 5001, chapterTitle = "第一章 剑的世界"),
                    ),
                ),
            ),
        )
    }

    override suspend fun fetchChapterContent(
        aid: Int,
        cid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> = WenkuApiResult.Success("正文")

    override suspend fun downloadBinaryBytes(url: String): ByteArray? = null

    override suspend fun fetchCloudBookshelf(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.Success(emptyList())
}
