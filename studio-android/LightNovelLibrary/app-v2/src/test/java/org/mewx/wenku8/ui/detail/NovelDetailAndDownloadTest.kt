package org.mewx.wenku8.ui.detail

import android.app.Application
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.R
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.download.NovelDownloadMode
import org.mewx.wenku8.data.download.NovelDownloadRepository
import org.mewx.wenku8.data.download.NovelDownloadRequest
import org.mewx.wenku8.data.download.NovelDownloadRunState
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
import org.mewx.wenku8.data.repository.NovelDetailRefreshOutcome
import org.mewx.wenku8.data.repository.NovelDetailRepository
import org.mewx.wenku8.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.data.repository.ParsedChapterToc
import org.mewx.wenku8.data.repository.ParsedVolumeToc
import org.mewx.wenku8.ui.theme.CoverThemeEngine
import org.mewx.wenku8.ui.theme.Wenku8ColorSchemes
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files
import java.util.Locale
import java.util.concurrent.Executor

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class NovelDetailAndDownloadTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var database: Wenku8Database
    private lateinit var tempAssetRoot: File
    private lateinit var testScope: CoroutineScope
    private lateinit var fakeGateway: FakeDetailAndDownloadGateway
    private lateinit var tocSyncRepository: NovelTocSyncRepository
    private lateinit var detailRepository: NovelDetailRepository
    private lateinit var bookshelfRepository: BookshelfRepository
    private lateinit var downloadRepository: NovelDownloadRepository

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

        tempAssetRoot = Files.createTempDirectory("wenku8_v2_detail_test").toFile()
        testScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        fakeGateway = FakeDetailAndDownloadGateway()
        tocSyncRepository = NovelTocSyncRepository(database)

        detailRepository = NovelDetailRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = Dispatchers.Unconfined,
        )
        bookshelfRepository = BookshelfRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            userSessionRepository = null,
            ioDispatcher = Dispatchers.Unconfined,
        )
        downloadRepository = NovelDownloadRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            assetRootsProvider = { listOf(tempAssetRoot) },
            applicationScope = testScope,
            appContext = null,
            ioDispatcher = Dispatchers.Unconfined,
        )
    }

    @After
    fun tearDown() {
        testScope.cancel()
        database.close()
        tempAssetRoot.deleteRecursively()
    }

    @Test
    fun fourModeDownloader_persistsAtomicTocChaptersIllustrationsAndPreservesReadingProgress() = runBlocking {
        fakeGateway.coverUrlForBinaryTest = "http://img.wenku8.com/image/0/5/5s.jpg"
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 5,
                title = "狼与香辛料",
                inBookshelf = true,
            ),
        )
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 5,
                vid = 10,
                cid = 102,
                volumeTitle = "第一卷",
                chapterTitle = "第二话",
                progressPermille = 420,
                updatedAtMs = 1_700_000_000_000L,
            ),
        )

        // 1. SELECT_VOLUMES on Vol 10 only (2 chapters: 101 + 102 illus)
        val vol10Outcome = downloadRepository.executeDownload(
            NovelDownloadRequest(
                aid = 5,
                appLanguage = Wenku8API.AppLanguage.SC,
                mode = NovelDownloadMode.SELECT_VOLUMES,
                selectedVolumeIds = setOf(10),
                overwriteSelectedVolumes = false,
            ),
        )
        assertEquals(NovelDownloadRunState.SUCCEEDED, vol10Outcome.state)
        assertEquals(2, vol10Outcome.downloadedChapters)
        assertEquals(1, vol10Outcome.downloadedImages)

        // Cover and inline chapter illustration saved permanently under saves/imgs/
        assertTrue(File(tempAssetRoot, "saves/imgs/5.jpg").isFile)
        assertTrue(File(tempAssetRoot, "saves/imgs/pictures05102999.jpg").isFile)

        // 2. UNCACHED_ONLY skips Vol 10 (already cached) and downloads only Vol 20 (cid 201)
        fakeGateway.requestedChapterCids.clear()
        val uncachedOutcome = downloadRepository.executeDownload(
            NovelDownloadRequest(
                aid = 5,
                appLanguage = Wenku8API.AppLanguage.SC,
                mode = NovelDownloadMode.UNCACHED_ONLY,
            ),
        )
        assertEquals(NovelDownloadRunState.SUCCEEDED, uncachedOutcome.state)
        assertEquals(listOf(201), fakeGateway.requestedChapterCids)

        // 3. SMART_UPDATE when server adds cid 202 fetches only cid 202 and bumps toc_version
        fakeGateway.includeExtraChapter202 = true
        fakeGateway.requestedChapterCids.clear()
        val smartOutcome = downloadRepository.executeDownload(
            NovelDownloadRequest(
                aid = 5,
                appLanguage = Wenku8API.AppLanguage.SC,
                mode = NovelDownloadMode.SMART_UPDATE,
            ),
        )
        assertEquals(NovelDownloadRunState.SUCCEEDED, smartOutcome.state)
        assertEquals(listOf(202), fakeGateway.requestedChapterCids)

        // 4. OVERWRITE_ALL re-downloads all 4 chapters in place without dropping reading progress!
        fakeGateway.chapterTextPrefix = "修订版正文："
        fakeGateway.requestedChapterCids.clear()
        val overwriteOutcome = downloadRepository.executeDownload(
            NovelDownloadRequest(
                aid = 5,
                appLanguage = Wenku8API.AppLanguage.SC,
                mode = NovelDownloadMode.OVERWRITE_ALL,
            ),
        )
        assertEquals(4, overwriteOutcome.downloadedChapters)
        val refreshedText = database.chapterContentDao().loadChapterTextSafely(101).orEmpty()
        assertTrue(refreshedText.startsWith("修订版正文："))

        val preservedProgress = database.readingProgressDao().getProgressForNovel(5)
        assertNotNull(preservedProgress)
        assertEquals(102, preservedProgress?.cid)
        assertEquals(420, preservedProgress?.progressPermille)
    }

    @Test
    fun offlineDetailRefresh_preservesCachedMetadataSynopsisAndVolumes() = runBlocking {
        // Initial online sync populates full metadata, synopsis, and TOC
        val onlineOutcome = detailRepository.refreshNovelDetail(5, Wenku8API.AppLanguage.SC)
        assertEquals(NovelDetailRefreshOutcome.Success, onlineOutcome)

        // Simulate airplane mode / network failure
        fakeGateway.simulateNetworkOffline = true
        val offlineOutcome = detailRepository.refreshNovelDetail(5, Wenku8API.AppLanguage.SC)
        assertEquals(NovelDetailRefreshOutcome.OfflineOrCachedOnly, offlineOutcome)

        val snapshot = detailRepository.observeNovelDetail(5).first()
        assertEquals("狼与香辛料", snapshot.novel?.title)
        assertTrue(snapshot.novel?.fullIntro.orEmpty().contains("贤狼赫萝"))
        assertEquals(2, snapshot.volumes.size)
    }

    @Test
    fun coverThemeEngine_extractsChromaticBitmapHueAndBypassesInEinkMode() {
        val coverBitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888)
        for (x in 0 until 24) {
            for (y in 0 until 24) {
                // White margin border + rich crimson center
                val pixel = if (x in 4..19 && y in 4..19) {
                    AndroidColor.rgb(196, 48, 64)
                } else {
                    AndroidColor.WHITE
                }
                coverBitmap.setPixel(x, y, pixel)
            }
        }
        val seed = CoverThemeEngine.extractSeedColorFromBitmap(coverBitmap)
        assertTrue(seed.red > seed.blue)

        val lightScheme = CoverThemeEngine.createScopedCoverColorScheme(
            baseColorScheme = Wenku8ColorSchemes.ArchivalDayColorScheme,
            coverSeedColor = seed,
            isDarkTheme = false,
            isEinkMode = false,
        )
        assertTrue(
            CoverThemeEngine.calculateContrastRatio(lightScheme.onPrimary, lightScheme.primary) >= 4.5f,
        )

        val einkScheme = CoverThemeEngine.createScopedCoverColorScheme(
            baseColorScheme = Wenku8ColorSchemes.ArchivalDayColorScheme,
            coverSeedColor = seed,
            isDarkTheme = false,
            isEinkMode = true,
        )
        assertEquals(Wenku8ColorSchemes.EinkMonochromeColorScheme, einkScheme)
    }

    @Test
    fun novelDetailScreen_rendersZeroClsAdOnlineAndCollapsesOfflineAndSupportsInteractiveFlows(): Unit = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 5,
                title = "狼与香辛料",
                author = "支仓冻砂",
                inBookshelf = false,
            ),
        )
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 5,
                vid = 10,
                cid = 101,
                volumeTitle = "第一卷 旅途的开端",
                chapterTitle = "第一话 贤狼与商人",
                progressPermille = 420,
                updatedAtMs = 1_700_000_000_000L,
            ),
        )

        var clickedAuthor = ""
        var clickedTag = ""
        var openedReviewsAid = 0
        var lastReaderLaunch: ReaderLaunchRequest? = null

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                NovelDetailScreen(
                    aid = 5,
                    novelDetailRepository = detailRepository,
                    bookshelfRepository = bookshelfRepository,
                    novelDownloadRepository = downloadRepository,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    themeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                    onOpenReader = { req -> lastReaderLaunch = req },
                    onOpenReviews = { aid, _ -> openedReviewsAid = aid },
                    onAuthorClicked = { author -> clickedAuthor = author },
                    onTagClicked = { tag -> clickedTag = tag },
                    isNetworkAvailableForAdOverride = true,
                    isGmsAvailableForAdOverride = true,
                    enableLiveAdViewInflation = false,
                )
            }
        }
        composeTestRule.waitForIdle()

        // Server `do=meta` returns BookStatus="已完成", which must normalize to "已完结"
        // consistently with the novel list and render with its leading status icon badge!
        composeTestRule.onNodeWithTag(NovelDetailTestTags.BOOK_STATUS_BADGE).assertIsDisplayed()
        composeTestRule.onNodeWithText("已完结").assertIsDisplayed()
        composeTestRule.onNodeWithText("已完成").assertDoesNotExist()
        assertEquals(
            "已完結",
            org.mewx.wenku8.domain.model.normalizeBookStatusText(
                rawStatus = "已完成",
                preferTraditionalChinese = true,
            ),
        )

        // Long-pressing top-right action bar buttons shows their descriptive tooltips
        composeTestRule.onNodeWithTag(NovelDetailTestTags.SHARE_BUTTON)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("分享作品").assertIsDisplayed()

        composeTestRule.onNodeWithTag(NovelDetailTestTags.REFRESH_BUTTON)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("刷新详情与目录").assertIsDisplayed()

        // Title dialog & copy
        composeTestRule.onNodeWithTag(NovelDetailTestTags.NOVEL_TITLE_TEXT).performClick()
        composeTestRule.onNodeWithTag(NovelDetailTestTags.TITLE_DIALOG).assertIsDisplayed()
        composeTestRule.onNodeWithTag(NovelDetailTestTags.TITLE_DIALOG_COPY_BUTTON).performClick()

        // Author & #Tag click triggers
        composeTestRule.onNodeWithTag(NovelDetailTestTags.AUTHOR_TRIGGER).performClick()
        assertEquals("支仓冻砂", clickedAuthor)

        composeTestRule.onNodeWithTag(NovelDetailTestTags.TAG_CHIP_PREFIX + "奇幻").performClick()
        assertEquals("奇幻", clickedTag)

        // Bookshelf toggle + Reviews button
        composeTestRule.onNodeWithTag(NovelDetailTestTags.SHELF_TOGGLE_BUTTON).performClick()
        composeTestRule.waitForIdle()
        assertTrue(database.novelDao().getNovelByAid(5)?.inBookshelf == true)

        composeTestRule.onNodeWithTag(NovelDetailTestTags.REVIEWS_OPEN_BUTTON).performClick()
        assertEquals(5, openedReviewsAid)

        val listNode = composeTestRule.onNodeWithTag(NovelDetailTestTags.SCREEN_ROOT + "_list")

        // Zero-CLS pre-reserved 50dp server-support slot when online + GMS available
        listNode.performScrollToNode(hasTestTag(NovelDetailTestTags.SERVER_SUPPORT_AD_CARD))
        composeTestRule.onNodeWithTag(NovelDetailTestTags.SERVER_SUPPORT_AD_SLOT)
            .assertHeightIsAtLeast(50.dp)

        // Volume headers must NOT show an underneath "插图章节" chip and must keep uniform height
        // regardless of whether a volume contains an illustration chapter!
        listNode.performScrollToNode(hasTestTag(NovelDetailTestTags.VOLUME_HEADER_PREFIX + "10"))
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(NovelDetailTestTags.VOLUME_ILLUS_CHIP_PREFIX + "10")
                .fetchSemanticsNodes().size,
        )
        composeTestRule.onNodeWithText("插图章节").assertDoesNotExist()
        val vol10HeaderHeight = composeTestRule
            .onNodeWithTag(NovelDetailTestTags.VOLUME_HEADER_PREFIX + "10")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        listNode.performScrollToNode(hasTestTag(NovelDetailTestTags.VOLUME_HEADER_PREFIX + "20"))
        val vol20HeaderHeight = composeTestRule
            .onNodeWithTag(NovelDetailTestTags.VOLUME_HEADER_PREFIX + "20")
            .fetchSemanticsNode()
            .boundsInRoot
            .height
        assertTrue(
            "Volume headers with and without illustrations must have uniform height (got $vol10HeaderHeight vs $vol20HeaderHeight)",
            kotlin.math.abs(vol10HeaderHeight - vol20HeaderHeight) <= 1f,
        )

        // Clicking the illustration chapter row inside expanded Volume 10 launches cid 102
        if (composeTestRule.onAllNodesWithTag(NovelDetailTestTags.CHAPTER_ROW_PREFIX + "102")
                .fetchSemanticsNodes().isEmpty()
        ) {
            listNode.performScrollToNode(hasTestTag(NovelDetailTestTags.VOLUME_HEADER_PREFIX + "10"))
            composeTestRule.onNodeWithTag(NovelDetailTestTags.VOLUME_HEADER_PREFIX + "10").performClick()
            composeTestRule.waitForIdle()
        }
        listNode.performScrollToNode(hasTestTag(NovelDetailTestTags.CHAPTER_ROW_PREFIX + "102"))
        composeTestRule.onNodeWithTag(NovelDetailTestTags.CHAPTER_ROW_PREFIX + "102")
            .performClick()
        assertEquals(102, lastReaderLaunch?.cid)

        // Downloader 4-mode bottom sheet opens cleanly
        listNode.performScrollToNode(hasTestTag(NovelDetailTestTags.DOWNLOAD_OPEN_BUTTON))
        composeTestRule.onNodeWithTag(NovelDetailTestTags.DOWNLOAD_OPEN_BUTTON).performClick()
        composeTestRule.onNodeWithTag(NovelDetailTestTags.DOWNLOADER_SHEET).assertIsDisplayed()
        composeTestRule.onNodeWithText("分卷选择下载").assertIsDisplayed()
    }

    @Test
    fun autoRefreshTtl_skipsRedundantFetchWithinFiveMinutes_clearsUnreadBadge_andKeepsBookcaseSyncIntact() = runBlocking {
        var nowMs = 1_000_000L
        val ttlDetailRepository = NovelDetailRepository(
            database = database,
            catalogGateway = fakeGateway,
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = Dispatchers.Unconfined,
            wallClockMs = { nowMs },
        )

        // 1. Initial automatic screen entry (`forceRefresh = false`) fetches meta + intro + toc.
        val initialOutcome = ttlDetailRepository.refreshNovelDetail(
            aid = 5,
            appLanguage = Wenku8API.AppLanguage.SC,
            forceRefresh = false,
        )
        assertEquals(NovelDetailRefreshOutcome.Success, initialOutcome)
        assertEquals(1, fakeGateway.metaRequestCount)
        assertEquals(1, fakeGateway.introRequestCount)
        assertEquals(1, fakeGateway.tocRequestCount)

        // 2. Re-entering within 5 minutes (`+4m59s`) with `forceRefresh = false` hits fresh cache
        //    with ZERO network round-trips, while still clearing `hasUnreadUpdate` (`新章节` badge)!
        database.novelDao().setHasUnreadUpdate(aid = 5, hasUnreadUpdate = true)
        nowMs += 299_000L
        val cachedOutcome = ttlDetailRepository.refreshNovelDetail(
            aid = 5,
            appLanguage = Wenku8API.AppLanguage.SC,
            forceRefresh = false,
        )
        assertEquals(NovelDetailRefreshOutcome.SkippedFreshCache, cachedOutcome)
        assertEquals(1, fakeGateway.metaRequestCount)
        assertEquals(1, fakeGateway.introRequestCount)
        assertEquals(1, fakeGateway.tocRequestCount)
        assertFalse(database.novelDao().getNovelByAid(5)!!.hasUnreadUpdate)

        // 3. Explicit manual refresh (`forceRefresh = true`) always bypasses the 5-minute TTL.
        nowMs += 500L
        val manualOutcome = ttlDetailRepository.refreshNovelDetail(
            aid = 5,
            appLanguage = Wenku8API.AppLanguage.SC,
            forceRefresh = true,
        )
        assertEquals(NovelDetailRefreshOutcome.Success, manualOutcome)
        assertEquals(2, fakeGateway.metaRequestCount)

        // 4. Language switch (`SC -> TC`) bypasses the TTL on automatic entry, then caches `TC`.
        nowMs += 1_000L
        val langSwitchOutcome = ttlDetailRepository.refreshNovelDetail(
            aid = 5,
            appLanguage = Wenku8API.AppLanguage.TC,
            forceRefresh = false,
        )
        assertEquals(NovelDetailRefreshOutcome.Success, langSwitchOutcome)
        assertEquals(3, fakeGateway.metaRequestCount)

        nowMs += 10_000L
        assertEquals(
            NovelDetailRefreshOutcome.SkippedFreshCache,
            ttlDetailRepository.refreshNovelDetail(
                aid = 5,
                appLanguage = Wenku8API.AppLanguage.TC,
                forceRefresh = false,
            ),
        )
        assertEquals(3, fakeGateway.metaRequestCount)

        // 5. Discovery card advertising a newer `lastUpdate` bypasses TTL immediately.
        nowMs += 5_000L
        fakeGateway.serverLastUpdate = "2026-03-01"
        val newerSeedOutcome = ttlDetailRepository.refreshNovelDetail(
            aid = 5,
            appLanguage = Wenku8API.AppLanguage.TC,
            fallbackSeedItem = org.mewx.wenku8.domain.model.DiscoveryNovelCardItem(
                aid = 5,
                title = "狼与香辛料",
                author = "支仓冻砂",
                bookStatus = "已完结",
                lastUpdate = "2026-03-01",
                tags = listOf("奇幻"),
                introShort = "商人罗伦斯与贤狼赫萝的旅途。",
                coverUrl = "",
            ),
            forceRefresh = false,
        )
        assertEquals(NovelDetailRefreshOutcome.Success, newerSeedOutcome)
        assertEquals(4, fakeGateway.metaRequestCount)

        // 6. After 5 minutes (`+5m01s`), automatic entry refreshes from server again.
        nowMs += NovelDetailRepository.DEFAULT_AUTO_REFRESH_TTL_MS + 1_000L
        val expiredTtlOutcome = ttlDetailRepository.refreshNovelDetail(
            aid = 5,
            appLanguage = Wenku8API.AppLanguage.TC,
            forceRefresh = false,
        )
        assertEquals(NovelDetailRefreshOutcome.Success, expiredTtlOutcome)
        assertEquals(5, fakeGateway.metaRequestCount)

        // 7. Bookcase Sync & Smart Update MUST work 100% normally even 10s after a detail refresh!
        nowMs += 10_000L
        bookshelfRepository.addNovelToBookshelf(
            database.novelDao().getNovelByAid(5)!!.copy(inBookshelf = true, cloudSynced = true),
            syncToCloudIfSignedIn = false,
        )
        database.novelDao().upsertNovel(
            database.novelDao().getNovelByAid(5)!!.copy(inBookshelf = true, cloudSynced = true),
        )
        fakeGateway.includeExtraChapter202 = true
        fakeGateway.serverLastUpdate = "2026-03-02"
        fakeGateway.cloudBookshelfForTest = listOf(
            ParsedCloudBookshelfEntry(
                aid = 5,
                lastUpdateDate = "2026-03-02",
                title = "狼与香辛料",
                latestChapterCid = 202,
                latestChapterName = "第二话 新航路",
            ),
        )

        val syncOutcome = bookshelfRepository.syncWithCloudBookcase(Wenku8API.AppLanguage.TC)
        assertTrue(syncOutcome is org.mewx.wenku8.data.repository.CloudBookshelfSyncOutcome.Success)
        val syncSuccess = syncOutcome as org.mewx.wenku8.data.repository.CloudBookshelfSyncOutcome.Success
        assertEquals(1, syncSuccess.refreshedStaleCount)
        assertTrue(database.novelDao().getNovelByAid(5)!!.hasUnreadUpdate)
        assertTrue(database.tocDao().getChaptersForNovel(5).any { it.cid == 202 })

        // Seed 1 cached chapter so `smartUpdateShelfNovels` downloads the remaining missing chapters (including 202)
        database.chapterContentDao().upsertChapterContent(
            org.mewx.wenku8.data.local.db.entity.ChapterContentEntity(
                cid = 101,
                aid = 5,
                contentLang = "TC",
                contentText = "第一话正文",
                byteLength = 15,
                imageUrlsJson = "[]",
                imageCount = 0,
                sha256Checksum = "sha101",
                downloadedAtMs = nowMs,
            ),
        )
        fakeGateway.requestedChapterCids.clear()
        val smartSummary = bookshelfRepository.smartUpdateShelfNovels(
            aids = listOf(5),
            appLanguage = Wenku8API.AppLanguage.TC,
        )
        assertEquals(1, smartSummary.updatedNovelCount)
        assertTrue(202 in fakeGateway.requestedChapterCids)

        // Opening NovelDetailScreen right after Bookcase Sync clears `hasUnreadUpdate` and shows cid 202
        val postSyncDetailOutcome = ttlDetailRepository.refreshNovelDetail(
            aid = 5,
            appLanguage = Wenku8API.AppLanguage.TC,
            forceRefresh = false,
        )
        assertTrue(
            postSyncDetailOutcome == NovelDetailRefreshOutcome.SkippedFreshCache ||
                postSyncDetailOutcome == NovelDetailRefreshOutcome.Success,
        )
        assertFalse(database.novelDao().getNovelByAid(5)!!.hasUnreadUpdate)
        val finalSnapshot = ttlDetailRepository.observeNovelDetail(5).first()
        assertEquals(4, finalSnapshot.totalChapters)
    }

    @Test
    fun serverSupportBannerAd_collapsesCompletelyWhenOffline() {
        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                ServerSupportBannerAd(
                    isNetworkAvailableOverride = false,
                    isGmsAvailableOverride = true,
                    enableLiveAdViewInflation = false,
                )
            }
        }
        composeTestRule.waitForIdle()
        assertEquals(
            0,
            composeTestRule.onAllNodesWithTag(NovelDetailTestTags.SERVER_SUPPORT_AD_CARD)
                .fetchSemanticsNodes().size,
        )
    }

    @Test
    fun shareButton_sharesShareWenku8AppBookUrlAcrossLocales() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        Shadows.shadowOf(app).clearNextStartedActivities()

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                NovelDetailScreen(
                    aid = 5,
                    novelDetailRepository = detailRepository,
                    bookshelfRepository = bookshelfRepository,
                    novelDownloadRepository = downloadRepository,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    themeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                    useCoverTheme = false,
                    onNavigateBack = {},
                    onOpenReader = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(NovelDetailTestTags.SHARE_BUTTON).performClick()
        composeTestRule.waitForIdle()

        val chooserIntent = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(chooserIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)
        @Suppress("DEPRECATION")
        val sendIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull(sendIntent)
        assertEquals(Intent.ACTION_SEND, sendIntent!!.action)
        assertEquals(
            "《狼与香辛料》（作者：支仓冻砂） https://share.wenku8.app/book/5",
            sendIntent.getStringExtra(Intent.EXTRA_TEXT),
        )

        for (locale in listOf(Locale.SIMPLIFIED_CHINESE, Locale.TAIWAN, Locale("zh", "HK"))) {
            val localizedConfig = Configuration(app.resources.configuration).apply {
                setLocale(locale)
            }
            val localizedContext = app.createConfigurationContext(localizedConfig)
            val formatted = localizedContext.getString(
                R.string.detail_share_text_format,
                "狼与香辛料",
                "支仓冻砂",
                5,
            )
            assertEquals(
                "《狼与香辛料》（作者：支仓冻砂） https://share.wenku8.app/book/5",
                formatted,
            )
        }
    }

    @Test
    fun serverSupportBannerAd_hidesBoxWhileLoadingOrFailedAndPreservesStateAcrossScroll() {
        ServerSupportAdConstants.resetSessionPreferredAdUnitForTest()
        assertEquals(
            listOf(ServerSupportAdConstants.PRODUCTION_BANNER_AD_UNIT_ID),
            ServerSupportAdConstants.candidateBannerAdUnitIds(isDebugBuild = false),
        )
        assertEquals(
            listOf(
                ServerSupportAdConstants.PRODUCTION_BANNER_AD_UNIT_ID,
                ServerSupportAdConstants.DEV_TEST_BANNER_AD_UNIT_ID,
            ),
            ServerSupportAdConstants.candidateBannerAdUnitIds(isDebugBuild = true),
        )

        // Caching a successful debug fallback promotes it to index 0 so subsequent loads skip
        // waiting on a failing round-trip, while release builds strictly keep PRODUCTION only.
        ServerSupportAdConstants.recordSuccessfulAdUnitId(ServerSupportAdConstants.DEV_TEST_BANNER_AD_UNIT_ID)
        assertEquals(
            listOf(
                ServerSupportAdConstants.DEV_TEST_BANNER_AD_UNIT_ID,
                ServerSupportAdConstants.PRODUCTION_BANNER_AD_UNIT_ID,
            ),
            ServerSupportAdConstants.candidateBannerAdUnitIds(isDebugBuild = true),
        )
        assertEquals(
            listOf(ServerSupportAdConstants.PRODUCTION_BANNER_AD_UNIT_ID),
            ServerSupportAdConstants.candidateBannerAdUnitIds(isDebugBuild = false),
        )
        ServerSupportAdConstants.resetSessionPreferredAdUnitForTest()

        // Activity-pooled holder retains Loaded phase across detail screen detach -> next novel attach
        val pooledHolder = NovelDetailBannerAdHolder(
            initialPhase = BannerAdLoadPhase.Loaded,
            ownedByActivityPool = true,
        )
        pooledHolder.onDetailConsumerAttached()
        assertEquals(1, pooledHolder.activeDetailConsumers)
        assertTrue(pooledHolder.shouldRenderVisibleCard)
        pooledHolder.onDetailConsumerDetached()
        assertEquals(0, pooledHolder.activeDetailConsumers)
        assertTrue("Preloaded Activity-pooled holder must stay Loaded for 0ms next-book open", pooledHolder.shouldRenderVisibleCard)

        val holder = NovelDetailBannerAdHolder(initialPhase = BannerAdLoadPhase.Loading)
        assertEquals(false, holder.shouldRenderVisibleCard)

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                androidx.compose.foundation.lazy.LazyColumn(
                    modifier = androidx.compose.ui.Modifier
                        .testTag("ad_scroll_test_list"),
                ) {
                    item(key = "top") {
                        androidx.compose.material3.Text("顶部简介")
                    }
                    if (holder.shouldRenderVisibleCard) {
                        item(key = "detail_server_support_ad") {
                            ServerSupportBannerAd(holder = holder)
                        }
                    }
                    items(40) { idx ->
                        androidx.compose.material3.Text(
                            text = "章节行 $idx",
                            modifier = androidx.compose.ui.Modifier
                                .testTag("scroll_row_$idx"),
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. While Loading: no empty box rendered on open
        composeTestRule.onNodeWithTag(NovelDetailTestTags.SERVER_SUPPORT_AD_CARD)
            .assertDoesNotExist()

        // 2. When Failed: remains invisible both initially and after scrolling down & back up
        composeTestRule.runOnIdle { holder.markFailedForTest() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NovelDetailTestTags.SERVER_SUPPORT_AD_CARD)
            .assertDoesNotExist()

        val scrollList = composeTestRule.onNodeWithTag("ad_scroll_test_list")
        scrollList.performScrollToNode(hasTestTag("scroll_row_35"))
        composeTestRule.waitForIdle()
        scrollList.performScrollToNode(hasTestTag("scroll_row_0"))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NovelDetailTestTags.SERVER_SUPPORT_AD_CARD)
            .assertDoesNotExist()

        // 3. When Loaded: card renders and remains Loaded across scrolling down & back up
        composeTestRule.runOnIdle { holder.markLoadedForTest() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NovelDetailTestTags.SERVER_SUPPORT_AD_CARD)
            .assertIsDisplayed()

        scrollList.performScrollToNode(hasTestTag("scroll_row_35"))
        composeTestRule.waitForIdle()
        scrollList.performScrollToNode(hasTestTag(NovelDetailTestTags.SERVER_SUPPORT_AD_CARD))
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(NovelDetailTestTags.SERVER_SUPPORT_AD_CARD)
            .assertIsDisplayed()
    }
}

private class FakeDetailAndDownloadGateway : WenkuCatalogGateway {
    override val isApiConfigured: Boolean = true

    var coverUrlForBinaryTest: String = ""
    var simulateNetworkOffline: Boolean = false
    var includeExtraChapter202: Boolean = false
    var chapterTextPrefix: String = ""
    var serverLastUpdate: String = "2026-02-18"
    var cloudBookshelfForTest: List<ParsedCloudBookshelfEntry> = emptyList()
    var metaRequestCount: Int = 0
    var introRequestCount: Int = 0
    var tocRequestCount: Int = 0
    val requestedChapterCids = mutableListOf<Int>()

    override fun resolveCoverUrl(aid: Int): String = coverUrlForBinaryTest

    override suspend fun downloadBinaryBytes(url: String): ByteArray? {
        if (simulateNetworkOffline) return null
        return byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
    }

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
    ): WenkuApiResult<ParsedNovelListItem> {
        if (simulateNetworkOffline) return WenkuApiResult.NetworkError("offline")
        return WenkuApiResult.Success(
            ParsedNovelListItem(
                aid = aid,
                title = "狼与香辛料",
                author = "支仓冻砂",
                bookStatus = "已完成",
                lastUpdate = serverLastUpdate,
                tagsCsv = "奇幻 旅行 经商",
                introShort = "商人罗伦斯与贤狼赫萝的旅途。",
                totalHits = 2_316_000,
                pushCount = 153_000,
                favCount = 14_416,
            ),
        )
    }

    override suspend fun fetchNovelFullMeta(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<ParsedNovelFullMeta> {
        if (simulateNetworkOffline) return WenkuApiResult.NetworkError("offline")
        metaRequestCount++
        return WenkuApiResult.Success(
            ParsedNovelFullMeta(
                aid = aid,
                title = "狼与香辛料",
                author = "支仓冻砂",
                dayHitsCount = 26,
                totalHitsCount = 2_316_000,
                pushCount = 153_000,
                favCount = 14_416,
                pressId = "电击文库",
                bookStatus = "已完成",
                bookLength = 2_004_000,
                lastUpdate = serverLastUpdate,
                latestSectionCid = if (includeExtraChapter202) 202 else 201,
                latestSectionName = if (includeExtraChapter202) "第二话 新航路" else "第二卷 第一话",
                tagsCsv = "奇幻 旅行 经商",
            ),
        )
    }

    override suspend fun fetchNovelFullIntro(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> {
        if (simulateNetworkOffline) return WenkuApiResult.NetworkError("offline")
        introRequestCount++
        return WenkuApiResult.Success(
            "从教会都市出发的商人罗伦斯，在装满麦子的马车上发现了熟睡的贤狼赫萝……",
        )
    }

    override suspend fun fetchNovelVolumeToc(
        aid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedVolumeToc>> {
        if (simulateNetworkOffline) return WenkuApiResult.NetworkError("offline")
        tocRequestCount++
        val vol2Chapters = buildList {
            add(ParsedChapterToc(cid = 201, chapterTitle = "第一话 旅途再启"))
            if (includeExtraChapter202) {
                add(ParsedChapterToc(cid = 202, chapterTitle = "第二话 新航路"))
            }
        }
        return WenkuApiResult.Success(
            listOf(
                ParsedVolumeToc(
                    vid = 10,
                    volumeTitle = "第一卷 旅途的开端",
                    chapters = listOf(
                        ParsedChapterToc(cid = 101, chapterTitle = "第一话 贤狼与商人"),
                        ParsedChapterToc(cid = 102, chapterTitle = "插图"),
                    ),
                ),
                ParsedVolumeToc(
                    vid = 20,
                    volumeTitle = "第二卷 狼与琥珀色的忧郁",
                    chapters = vol2Chapters,
                ),
            ),
        )
    }

    override suspend fun fetchChapterContent(
        aid: Int,
        cid: Int,
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<String> {
        if (simulateNetworkOffline) return WenkuApiResult.NetworkError("offline")
        requestedChapterCids.add(cid)
        val body = if (cid == 102) {
            "${chapterTextPrefix}第一卷插图\n<!--image-->http://pic.wenku8.cn/pictures/0/5/102/999.jpg<!--image-->"
        } else {
            "${chapterTextPrefix}章节 $cid 正文内容。"
        }
        return WenkuApiResult.Success(body)
    }

    override suspend fun fetchCloudBookshelf(
        appLanguage: Wenku8API.AppLanguage,
    ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.Success(cloudBookshelfForTest)
}
