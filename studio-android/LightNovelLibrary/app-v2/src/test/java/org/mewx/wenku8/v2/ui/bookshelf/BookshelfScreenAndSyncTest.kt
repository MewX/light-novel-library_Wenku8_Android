package org.mewx.wenku8.v2.ui.bookshelf

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.v2.data.remote.api.OnlineSearchField
import org.mewx.wenku8.v2.data.remote.api.WenkuApiResult
import org.mewx.wenku8.v2.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.v2.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.v2.data.repository.BookshelfFilter
import org.mewx.wenku8.v2.data.repository.BookshelfRepository
import org.mewx.wenku8.v2.data.repository.BookshelfSortMode
import org.mewx.wenku8.v2.data.repository.BookshelfViewMode
import org.mewx.wenku8.v2.data.repository.CloudBookshelfSyncOutcome
import org.mewx.wenku8.v2.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.v2.data.repository.ParsedChapterToc
import org.mewx.wenku8.v2.data.repository.ParsedVolumeToc
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class BookshelfScreenAndSyncTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: Wenku8Database
    private lateinit var tocSyncRepository: NovelTocSyncRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        tocSyncRepository = NovelTocSyncRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cloudBookcaseSync_isNonDestructive_pushesLocalOnly_handlesShelfFull_andFlagsUnreadUpdates() = runTest {
        // Seed 3 local bookshelf novels:
        // - 1001: shared with cloud, stale latestSectionCid (10 -> 12 on cloud)
        // - 1002: local-only, succeeds when pushed to cloud
        // - 1003: local-only, hits SYSTEM_6_BOOKSHELF_FULL (must NEVER be deleted locally!)
        database.novelDao().upsertNovels(
            listOf(
                NovelEntity(
                    aid = 1001,
                    title = "狼与香辛料",
                    author = "支仓冻砂",
                    pressId = "电击文库",
                    bookStatus = "已完成",
                    lastUpdate = "2026-01-10",
                    latestSectionCid = 10,
                    latestSectionName = "第十六卷 后记",
                    tocVersion = 1L,
                    inBookshelf = true,
                    shelfSortOrder = 1,
                    cloudSynced = true,
                ),
                NovelEntity(
                    aid = 1002,
                    title = "奇诺之旅",
                    author = "时雨泽惠一",
                    pressId = "电击文库",
                    bookStatus = "连载中",
                    inBookshelf = true,
                    shelfSortOrder = 2,
                    cloudSynced = false,
                ),
                NovelEntity(
                    aid = 1003,
                    title = "文学少女",
                    author = "野村美月",
                    pressId = "Fami通文库",
                    bookStatus = "已完成",
                    inBookshelf = true,
                    shelfSortOrder = 3,
                    cloudSynced = false,
                ),
            ),
        )

        val gateway = FakeBookshelfCatalogGateway().apply {
            cloudBookshelfEntries = listOf(
                ParsedCloudBookshelfEntry(
                    aid = 1001,
                    lastUpdateDate = "2026-02-18",
                    title = "狼与香辛料",
                    latestChapterCid = 12,
                    latestChapterName = "第十七卷 尾声",
                ),
                ParsedCloudBookshelfEntry(
                    aid = 2001,
                    lastUpdateDate = "2026-02-20",
                    title = "败犬女主太多了！",
                    latestChapterCid = 88,
                    latestChapterName = "第七卷 插图",
                ),
            )
            addResponsesByAid[1002] = WenkuApiResult.Success(Unit)
            addResponsesByAid[1003] = WenkuApiResult.ServerErrorCode(
                numericCode = BookshelfRepository.SERVER_CODE_BOOKSHELF_FULL,
            )
        }

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
        )

        val outcome = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC)
        assertTrue(outcome is CloudBookshelfSyncOutcome.Success)
        val success = outcome as CloudBookshelfSyncOutcome.Success
        assertEquals(1, success.pulledFromCloudCount)
        assertEquals(1, success.pushedToCloudCount)
        assertEquals(1, success.refreshedStaleCount)
        assertTrue(success.cloudShelfFullReached)

        val shelfAfter = database.novelDao().getBookshelfNovelsSnapshot().associateBy { it.aid }
        // All 4 novels must remain on the local bookshelf (zero data loss on cloud shelf full!)
        assertEquals(setOf(1001, 1002, 1003, 2001), shelfAfter.keys)

        val updatedShared = shelfAfter.getValue(1001)
        assertTrue(updatedShared.hasUnreadUpdate)
        assertEquals(12, updatedShared.latestSectionCid)

        val pushedLocal = shelfAfter.getValue(1002)
        assertTrue(pushedLocal.cloudSynced)

        val overflowLocal = shelfAfter.getValue(1003)
        assertTrue(overflowLocal.inBookshelf)
        assertFalse(overflowLocal.cloudSynced)

        val pulledCloud = shelfAfter.getValue(2001)
        assertTrue(pulledCloud.inBookshelf)
        assertTrue(pulledCloud.cloudSynced)
        assertEquals("败犬女主太多了！", pulledCloud.title)
    }

    @Test
    fun filterAndSortItems_recentReadIgnoresLastAccessedMs_andFiltersByTagAndCategory() = runTest {
        database.novelDao().upsertNovels(
            listOf(
                NovelEntity(
                    aid = 10,
                    title = "刚在详情页浏览但未读的作品",
                    author = "作者甲",
                    pressId = "电击文库",
                    bookStatus = "连载中",
                    tagsCsv = "奇幻 冒险",
                    inBookshelf = true,
                    shelfCategory = "待读清单",
                    shelfSortOrder = 1,
                    // Very high lastAccessedMs from opening NovelDetailScreen, but no chapter read!
                    lastAccessedMs = 999_999_999L,
                ),
                NovelEntity(
                    aid = 20,
                    title = "真正最近打开阅读器读过的作品",
                    author = "作者乙",
                    pressId = "GA文库",
                    bookStatus = "已完成",
                    tagsCsv = "恋爱 校园",
                    inBookshelf = true,
                    shelfCategory = NovelEntity.DEFAULT_SHELF_CATEGORY,
                    shelfSortOrder = 2,
                    lastAccessedMs = 100L,
                ),
            ),
        )
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 20,
                vid = 1,
                cid = 101,
                volumeTitle = "第一卷",
                chapterTitle = "第一章 春天的邂逅",
                progressPermille = 680,
                updatedAtMs = 500_000L,
            ),
        )

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = FakeBookshelfCatalogGateway(),
            novelTocSyncRepository = tocSyncRepository,
        )
        val snapshot = repository.observeBookshelfSnapshot().first { it.allShelfItems.size == 2 }

        val recentReadSorted = BookshelfRepository.filterAndSortItems(
            items = snapshot.allShelfItems,
            localQuery = "",
            activeFilter = BookshelfFilter.All,
            sortMode = BookshelfSortMode.RECENT_READ,
        )
        assertEquals(listOf(20, 10), recentReadSorted.map { it.aid })

        val tagFiltered = BookshelfRepository.filterAndSortItems(
            items = snapshot.allShelfItems,
            localQuery = "",
            activeFilter = BookshelfFilter.Tag("恋爱"),
            sortMode = BookshelfSortMode.MANUAL_ORDER,
        )
        assertEquals(listOf(20), tagFiltered.map { it.aid })

        val categoryFiltered = BookshelfRepository.filterAndSortItems(
            items = snapshot.allShelfItems,
            localQuery = "",
            activeFilter = BookshelfFilter.Category("待读清单"),
            sortMode = BookshelfSortMode.MANUAL_ORDER,
        )
        assertEquals(listOf(10), categoryFiltered.map { it.aid })
    }

    @Test
    fun smartUpdateShelfNovels_downloadsOnlyMissingChaptersForOfflineCachedNovels() = runTest {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 1749,
                title = "无职转生",
                inBookshelf = true,
            ),
        )
        // Seed chapter 501 as already cached offline; chapter 502 is new in TOC
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 501,
                aid = 1749,
                contentLang = "SC",
                contentText = "已缓存的第一章正文",
                byteLength = 27,
                imageUrlsJson = "[]",
                imageCount = 0,
                sha256Checksum = "abc",
                downloadedAtMs = 1_000L,
            ),
        )

        val gateway = FakeBookshelfCatalogGateway().apply {
            tocByAid[1749] = listOf(
                ParsedVolumeToc(
                    vid = 500,
                    volumeTitle = "第一卷 幼年期",
                    chapters = listOf(
                        ParsedChapterToc(cid = 501, chapterTitle = "第一话"),
                        ParsedChapterToc(cid = 502, chapterTitle = "第二话 新章节"),
                    ),
                ),
            )
            chapterTextByCid[502] = "新下载的第二话正文\n<!--image-->https://img.wenku8.com/image/1/1749/1749s.jpg<!--image-->"
        }

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
        )

        val summary = repository.smartUpdateShelfNovels(
            aids = listOf(1749),
            appLanguage = Wenku8API.AppLanguage.SC,
        )
        assertEquals(1, summary.updatedNovelCount)
        assertEquals(1, summary.newlyDownloadedChapterCount)
        assertEquals(listOf(502), gateway.fetchedChapterCids)
        val newlyCached = database.chapterContentDao().getChapterHeader(502)
        assertNotNull(newlyCached)
        assertEquals(1, newlyCached!!.imageCount)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun bookshelfScreen_rendersCarousel_localSearch_viewModes_andMultiSelectToolbar() = runBlocking {
        database.novelDao().upsertNovels(
            listOf(
                NovelEntity(
                    aid = 5,
                    title = "狼与香辛料",
                    author = "支仓冻砂",
                    pressId = "电击文库",
                    bookStatus = "已完成",
                    tagsCsv = "奇幻 旅行 经商",
                    hasUnreadUpdate = true,
                    inBookshelf = true,
                    shelfSortOrder = 1,
                ),
                NovelEntity(
                    aid = 2906,
                    title = "败犬女主太多了！",
                    author = "雨森焚火",
                    pressId = "GAGAGA文库",
                    bookStatus = "连载中",
                    tagsCsv = "青春 恋爱 校园",
                    hasUnreadUpdate = false,
                    inBookshelf = true,
                    shelfSortOrder = 2,
                ),
            ),
        )
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 5,
                vid = 30,
                cid = 302,
                volumeTitle = "第三卷",
                chapterTitle = "第二话 真实与冒牌货",
                progressPermille = 680,
                updatedAtMs = 1_700_000_000_000L,
            ),
        )
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 302,
                aid = 5,
                contentLang = "SC",
                contentText = "离线正文内容",
                byteLength = 18,
                imageUrlsJson = "[]",
                imageCount = 0,
                sha256Checksum = "hash302",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = FakeBookshelfCatalogGateway(),
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )

        var activeViewMode by mutableStateOf(BookshelfViewMode.GRID)
        var activeSortMode by mutableStateOf(BookshelfSortMode.MANUAL_ORDER)
        var openedOnlineSearchCount = 0

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                BookshelfScreen(
                    bookshelfRepository = repository,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    viewMode = activeViewMode,
                    onViewModeSelected = { activeViewMode = it },
                    sortMode = activeSortMode,
                    onSortModeSelected = { activeSortMode = it },
                    onOpenSearch = { openedOnlineSearchCount++ },
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Continue Reading carousel, flat reading progress, and 新章节 badge render cleanly
        composeTestRule.onNodeWithTag(BookshelfTestTags.CONTINUE_READING_CAROUSEL).assertIsDisplayed()
        composeTestRule.onNodeWithText("68%").assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.NEW_CHAPTER_BADGE_PREFIX + 5).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.FLAT_READING_PROGRESS_PREFIX + 5).assertExists()
        composeTestRule.onNodeWithTag(BookshelfTestTags.ACTIVE_WAVY_PROGRESS_PREFIX + 5).assertDoesNotExist()

        // 2. Online search button triggers UnifiedSearchScreen callback
        composeTestRule.onNodeWithTag(BookshelfTestTags.ONLINE_SEARCH_TRIGGER).performClick()
        assertEquals(1, openedOnlineSearchCount)

        // 3. Instant local shelf search filters down to matching book
        composeTestRule.onNodeWithTag(BookshelfTestTags.LOCAL_SEARCH_INPUT).performTextInput("败犬")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 2906).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5).assertDoesNotExist()
        composeTestRule.onNodeWithTag(BookshelfTestTags.CLEAR_LOCAL_SEARCH_BUTTON).performClick()
        composeTestRule.waitForIdle()

        // 4. Switch across DETAIL_LIST and SPINE_3D view modes
        composeTestRule.onNodeWithTag(
            BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + BookshelfViewMode.DETAIL_LIST.name,
        ).performClick()
        composeTestRule.waitForIdle()
        assertEquals(BookshelfViewMode.DETAIL_LIST, activeViewMode)
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5).assertIsDisplayed()

        composeTestRule.onNodeWithTag(
            BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + BookshelfViewMode.SPINE_3D.name,
        ).performClick()
        composeTestRule.waitForIdle()
        assertEquals(BookshelfViewMode.SPINE_3D, activeViewMode)
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5).assertIsDisplayed()

        // 5. Long-press multi-select summons HorizontalFloatingToolbar; clear cache & remove work
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.MULTI_SELECT_TOOLBAR).assertIsDisplayed()

        composeTestRule.onNodeWithTag(BookshelfTestTags.BATCH_CLEAR_CACHE).performClick()
        composeTestRule.waitForIdle()
        assertTrue(database.chapterContentDao().getCachedChapterIdsForNovel(5).isEmpty())

        // Long-press aid=2906 and remove from bookshelf via confirmation dialog
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 2906)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.BATCH_REMOVE).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.CONFIRM_REMOVE_BUTTON).performClick()
        composeTestRule.waitForIdle()

        val remainingAids = database.novelDao().getBookshelfNovelsSnapshot().map { it.aid }
        assertEquals(listOf(5), remainingAids)
    }

    private class FakeBookshelfCatalogGateway : WenkuCatalogGateway {
        override val isApiConfigured: Boolean = true

        var cloudBookshelfEntries: List<ParsedCloudBookshelfEntry> = emptyList()
        val addResponsesByAid = mutableMapOf<Int, WenkuApiResult<Unit>>()
        val tocByAid = mutableMapOf<Int, List<ParsedVolumeToc>>()
        val chapterTextByCid = mutableMapOf<Int, String>()
        val fetchedChapterCids = mutableListOf<Int>()

        override fun resolveCoverUrl(aid: Int): String = ""

        override suspend fun fetchNovelListPage(
            sortedBy: Wenku8API.NovelSortedBy,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListPage> = WenkuApiResult.ApiNotConfigured

        override suspend fun searchNovelsByField(
            query: String,
            searchField: OnlineSearchField,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<Int>> = WenkuApiResult.ApiNotConfigured

        override suspend fun fetchNovelShortInfo(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListItem> = WenkuApiResult.ApiNotConfigured

        override suspend fun fetchNovelFullMeta(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelFullMeta> {
            val cloudEntry = cloudBookshelfEntries.firstOrNull { it.aid == aid }
            return WenkuApiResult.Success(
                ParsedNovelFullMeta(
                    aid = aid,
                    title = cloudEntry?.title ?: "作品 $aid",
                    author = "测试作者",
                    pressId = "电击文库",
                    bookStatus = "连载中",
                    lastUpdate = cloudEntry?.lastUpdateDate ?: "2026-02-20",
                    latestSectionCid = cloudEntry?.latestChapterCid ?: 1,
                    latestSectionName = cloudEntry?.latestChapterName ?: "第一话",
                ),
            )
        }

        override suspend fun fetchNovelFullIntro(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<String> = WenkuApiResult.Success("测试作品简介 $aid")

        override suspend fun fetchNovelVolumeToc(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<ParsedVolumeToc>> {
            val volumes = tocByAid[aid] ?: listOf(
                ParsedVolumeToc(
                    vid = aid * 10,
                    volumeTitle = "第一卷",
                    chapters = listOf(ParsedChapterToc(cid = aid * 10 + 1, chapterTitle = "第一话")),
                ),
            )
            return WenkuApiResult.Success(volumes)
        }

        override suspend fun fetchChapterContent(
            aid: Int,
            cid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<String> {
            fetchedChapterCids += cid
            val text = chapterTextByCid[cid] ?: "正文内容 $cid"
            return WenkuApiResult.Success(text)
        }

        override suspend fun fetchCloudBookshelf(
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.Success(cloudBookshelfEntries)

        override suspend fun addToCloudBookshelf(aid: Int): WenkuApiResult<Unit> {
            return addResponsesByAid[aid] ?: WenkuApiResult.Success(Unit)
        }

        override suspend fun removeFromCloudBookshelf(aid: Int): WenkuApiResult<Unit> {
            return WenkuApiResult.Success(Unit)
        }
    }
}
