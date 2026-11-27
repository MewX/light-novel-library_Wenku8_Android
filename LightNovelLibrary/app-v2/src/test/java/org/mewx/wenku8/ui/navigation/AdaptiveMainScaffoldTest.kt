package org.mewx.wenku8.ui.navigation

import android.content.ComponentName
import android.content.Context
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.MainActivity
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.data.local.db.entity.NovelEntity
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
import org.mewx.wenku8.data.repository.ReviewThreadCardItem
import org.mewx.wenku8.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.ui.reader.ReaderTestTags
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class AdaptiveMainScaffoldTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun coldStartOnlineDefaultsToLatestTabWithCompactShortNavigationBar() {
        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    AdaptiveMainScaffold(
                        preferredHomeDestination = DefaultHomeDestination.LATEST,
                        isNetworkAvailableOnColdStart = true,
                        currentThemeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                        onThemeModeSelected = {},
                        onDefaultHomeSelected = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(MainScaffoldTestTags.COMPACT_SHORT_NAV_BAR).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${MainScaffoldTestTags.NAV_ITEM_PREFIX}latest").assertIsSelected()
        composeTestRule.onNodeWithTag("${MainScaffoldTestTags.DESTINATION_SCREEN_PREFIX}latest").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MainScaffoldTestTags.OFFLINE_FALLBACK_BANNER).assertDoesNotExist()
    }

    @Test
    fun coldStartWithBookshelfHomePreferenceOpensBookshelfOnline() {
        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    AdaptiveMainScaffold(
                        preferredHomeDestination = DefaultHomeDestination.BOOKSHELF,
                        isNetworkAvailableOnColdStart = true,
                        currentThemeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                        onThemeModeSelected = {},
                        onDefaultHomeSelected = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("${MainScaffoldTestTags.NAV_ITEM_PREFIX}bookshelf").assertIsSelected()
        composeTestRule.onNodeWithTag("${MainScaffoldTestTags.DESTINATION_SCREEN_PREFIX}bookshelf").assertIsDisplayed()
        composeTestRule.onNodeWithTag(MainScaffoldTestTags.OFFLINE_FALLBACK_BANNER).assertDoesNotExist()
    }

    @Test
    fun coldStartOfflineAutomaticallyFallsBackToBookshelfWithDismissibleNotice() {
        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    AdaptiveMainScaffold(
                        preferredHomeDestination = DefaultHomeDestination.LATEST,
                        isNetworkAvailableOnColdStart = false,
                        currentThemeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                        onThemeModeSelected = {},
                        onDefaultHomeSelected = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("${MainScaffoldTestTags.NAV_ITEM_PREFIX}bookshelf").assertIsSelected()
        composeTestRule.onNodeWithTag(MainScaffoldTestTags.OFFLINE_FALLBACK_BANNER).assertIsDisplayed()

        composeTestRule.onNodeWithTag(MainScaffoldTestTags.DISMISS_OFFLINE_BANNER_BUTTON).performClick()
        composeTestRule.onNodeWithTag(MainScaffoldTestTags.OFFLINE_FALLBACK_BANNER).assertDoesNotExist()
    }

    @Test
    fun tappingAllFourTopLevelDestinationsSwitchesActiveScreen() {
        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CARBON_NIGHT) {
                    AdaptiveMainScaffold(
                        preferredHomeDestination = DefaultHomeDestination.LATEST,
                        isNetworkAvailableOnColdStart = true,
                        currentThemeMode = Wenku8ThemeMode.CARBON_NIGHT,
                        onThemeModeSelected = {},
                        onDefaultHomeSelected = {},
                    )
                }
            }
        }

        for (destination in listOf(
            TopLevelDestination.BOOKSHELF,
            TopLevelDestination.RANKINGS,
            TopLevelDestination.PROFILE,
            TopLevelDestination.LATEST,
        )) {
            composeTestRule
                .onNodeWithTag(MainScaffoldTestTags.NAV_ITEM_PREFIX + destination.routeKey)
                .performClick()
            composeTestRule
                .onNodeWithTag(MainScaffoldTestTags.DESTINATION_SCREEN_PREFIX + destination.routeKey)
                .assertIsDisplayed()
        }
    }

    @Test
    @Config(sdk = [34], qualifiers = "w900dp-h700dp")
    fun expandedTabletWidthUsesWideNavigationRailAndEinkModeSetsMonochromeFlag() {
        var observedEinkFlag = false
        var observedBackgroundColor: Color = Color.Unspecified

        composeTestRule.setContent {
            Box(modifier = Modifier.size(width = 900.dp, height = 700.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.E_INK) {
                    observedEinkFlag = LocalEinkMode.current
                    observedBackgroundColor = MaterialTheme.colorScheme.background
                    AdaptiveMainScaffold(
                        preferredHomeDestination = DefaultHomeDestination.LATEST,
                        isNetworkAvailableOnColdStart = true,
                        currentThemeMode = Wenku8ThemeMode.E_INK,
                        onThemeModeSelected = {},
                        onDefaultHomeSelected = {},
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag(MainScaffoldTestTags.EXPANDED_WIDE_NAV_RAIL).assertIsDisplayed()
        assertTrue(observedEinkFlag)
        assertEquals(Color.White, observedBackgroundColor)
    }

    @Test
    fun doubleBackToExit_showsClassicToastExpiresAfterTwoSecondsAndResetsOnTabSwitch() {
        var fakeMonoMs = 10_000L
        var exitCount = 0
        var backDispatcher: androidx.activity.OnBackPressedDispatcher? = null

        composeTestRule.setContent {
            backDispatcher = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
                ?.onBackPressedDispatcher
            Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                    AdaptiveMainScaffold(
                        preferredHomeDestination = DefaultHomeDestination.LATEST,
                        isNetworkAvailableOnColdStart = true,
                        currentThemeMode = Wenku8ThemeMode.CLASSIC_BLUE,
                        onThemeModeSelected = {},
                        onDefaultHomeSelected = {},
                        exitBackClockMonoMs = { fakeMonoMs },
                        onRequestExitApp = { exitCount++ },
                    )
                }
            }
        }
        composeTestRule.waitForIdle()

        // 1. First back press shows v1's classic kaomoji toast and does NOT exit
        composeTestRule.runOnIdle { backDispatcher!!.onBackPressed() }
        assertEquals(0, exitCount)
        val toastText = org.robolectric.shadows.ShadowToast.getTextOfLatestToast().orEmpty()
        assertTrue(
            "Expected classic v1 press_twice_to_exit toast, got: $toastText",
            toastText.contains("退出请再戳一下返回键哦"),
        )

        // 2. Waiting >= 2000ms disarms exit; pressing Back again re-prompts instead of exiting
        fakeMonoMs += 2_100L
        composeTestRule.runOnIdle { backDispatcher!!.onBackPressed() }
        assertEquals(0, exitCount)

        // 3. Switching top-level tabs also resets the armed state
        fakeMonoMs += 400L
        composeTestRule
            .onNodeWithTag("${MainScaffoldTestTags.NAV_ITEM_PREFIX}bookshelf")
            .performClick()
        composeTestRule.waitForIdle()
        fakeMonoMs += 300L
        composeTestRule.runOnIdle { backDispatcher!!.onBackPressed() }
        assertEquals(0, exitCount)

        // 4. Second back press within 2000ms exits cleanly
        fakeMonoMs += 600L
        composeTestRule.runOnIdle { backDispatcher!!.onBackPressed() }
        assertEquals(1, exitCount)
    }

    @Test
    fun manifestMainActivity_declaresRotationAndSizeConfigChanges_withoutInterceptingUiModeOrLocale() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val activityInfo = context.packageManager.getActivityInfo(
            ComponentName(context, MainActivity::class.java),
            0,
        )
        val requiredFlags = ActivityInfo.CONFIG_ORIENTATION or
            ActivityInfo.CONFIG_SCREEN_SIZE or
            ActivityInfo.CONFIG_SCREEN_LAYOUT or
            ActivityInfo.CONFIG_SMALLEST_SCREEN_SIZE or
            ActivityInfo.CONFIG_KEYBOARD or
            ActivityInfo.CONFIG_KEYBOARD_HIDDEN
        assertEquals(
            "MainActivity must handle orientation/size configChanges seamlessly (#229)",
            requiredFlags,
            activityInfo.configChanges and requiredFlags,
        )
        assertEquals(
            "uiMode must not be intercepted so values-night continues switching cleanly",
            0,
            activityInfo.configChanges and ActivityInfo.CONFIG_UI_MODE,
        )
        assertEquals(
            "locale must not be intercepted so AppCompatDelegate.setApplicationLocales switches cleanly",
            0,
            activityInfo.configChanges and ActivityInfo.CONFIG_LOCALE,
        )
    }

    @Test
    fun scaffoldNullableSavers_roundTripValidItems_andSafelyReturnNullOnMalformedData() {
        val scope = SaverScope { true }

        // 1. ReaderLaunchRequestNullableSaver
        assertNull(with(ReaderLaunchRequestNullableSaver) { scope.save(null) })
        val req = ReaderLaunchRequest(
            aid = 1306,
            vid = 41000,
            cid = 41001,
            novelTitle = "奇诺之旅",
            volumeTitle = "第一卷",
            chapterTitle = "序章",
            startWithTts = true,
            initialParagraphIndex = 12,
            initialCharOffset = 5,
        )
        val savedReq = with(ReaderLaunchRequestNullableSaver) { scope.save(req) }!!
        assertEquals(req, ReaderLaunchRequestNullableSaver.restore(savedReq))
        assertNull(ReaderLaunchRequestNullableSaver.restore("corrupt"))
        assertNull(ReaderLaunchRequestNullableSaver.restore(listOf(1, 2)))
        assertNull(ReaderLaunchRequestNullableSaver.restore(listOf("bad", 2, 3, "a", "b", "c", false, 0, 0)))
        assertNull(ReaderLaunchRequestNullableSaver.restore(listOf(1, "bad", 3, "a", "b", "c", false, 0, 0)))
        assertNull(ReaderLaunchRequestNullableSaver.restore(listOf(1, 2, "bad", "a", "b", "c", false, 0, 0)))
        assertNull(ReaderLaunchRequestNullableSaver.restore(listOf(1, 2, 3, 999, "b", "c", false, 0, 0)))
        assertNull(ReaderLaunchRequestNullableSaver.restore(listOf(1, 2, 3, "a", 999, "c", false, 0, 0)))
        assertNull(ReaderLaunchRequestNullableSaver.restore(listOf(1, 2, 3, "a", "b", 999, false, 0, 0)))
        assertNull(ReaderLaunchRequestNullableSaver.restore(listOf(1, 2, 3, "a", "b", "c", "notBool", 0, 0)))
        assertNull(ReaderLaunchRequestNullableSaver.restore(listOf(1, 2, 3, "a", "b", "c", false, "bad", 0)))
        assertNull(ReaderLaunchRequestNullableSaver.restore(listOf(1, 2, 3, "a", "b", "c", false, 0, "bad")))

        // 2. ReviewThreadCardItemNullableSaver
        assertNull(with(ReviewThreadCardItemNullableSaver) { scope.save(null) })
        val thread = ReviewThreadCardItem(
            rid = 88,
            title = "读后感",
            uid = 1001,
            userName = "读者甲",
            avatarUrl = "https://img.example.test/a.jpg",
            replyCount = 42,
            postTimeFormatted = "2026-01-01",
            lastReplyTimeFormatted = "2026-01-02",
        )
        val savedThread = with(ReviewThreadCardItemNullableSaver) { scope.save(thread) }!!
        assertEquals(thread, ReviewThreadCardItemNullableSaver.restore(savedThread))
        assertNull(ReviewThreadCardItemNullableSaver.restore("corrupt"))
        assertNull(ReviewThreadCardItemNullableSaver.restore(listOf(1, 2)))
        assertNull(ReviewThreadCardItemNullableSaver.restore(listOf("bad", "t", 1, "u", "a", 2, "p", "l")))
        assertNull(ReviewThreadCardItemNullableSaver.restore(listOf(1, 999, 1, "u", "a", 2, "p", "l")))
        assertNull(ReviewThreadCardItemNullableSaver.restore(listOf(1, "t", "bad", "u", "a", 2, "p", "l")))
        assertNull(ReviewThreadCardItemNullableSaver.restore(listOf(1, "t", 1, 999, "a", 2, "p", "l")))
        assertNull(ReviewThreadCardItemNullableSaver.restore(listOf(1, "t", 1, "u", 999, 2, "p", "l")))
        assertNull(ReviewThreadCardItemNullableSaver.restore(listOf(1, "t", 1, "u", "a", "bad", "p", "l")))
        assertNull(ReviewThreadCardItemNullableSaver.restore(listOf(1, "t", 1, "u", "a", 2, 999, "l")))
        assertNull(ReviewThreadCardItemNullableSaver.restore(listOf(1, "t", 1, "u", "a", 2, "p", 999)))
    }

    @Test
    fun activeReader_survivesSavedInstanceStateRecreation_withoutKickingBackToPreviousScreen() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val tocRepo = NovelTocSyncRepository(db)
            val gateway = object : WenkuCatalogGateway {
                override val isApiConfigured: Boolean = true
                override fun resolveCoverUrl(aid: Int): String = ""
                override suspend fun fetchNovelListPage(sortedBy: Wenku8API.NovelSortedBy, pageNumber: Int, appLanguage: Wenku8API.AppLanguage) =
                    WenkuApiResult.ApiNotConfigured
                override suspend fun searchNovelsByField(query: String, searchField: OnlineSearchField, appLanguage: Wenku8API.AppLanguage) =
                    WenkuApiResult.Success(emptyList<Int>())
                override suspend fun fetchNovelShortInfo(aid: Int, appLanguage: Wenku8API.AppLanguage) =
                    WenkuApiResult.ApiNotConfigured
                override suspend fun fetchNovelFullMeta(aid: Int, appLanguage: Wenku8API.AppLanguage) =
                    WenkuApiResult.ApiNotConfigured
                override suspend fun fetchNovelFullIntro(aid: Int, appLanguage: Wenku8API.AppLanguage) =
                    WenkuApiResult.ApiNotConfigured
                override suspend fun fetchNovelVolumeToc(aid: Int, appLanguage: Wenku8API.AppLanguage) =
                    WenkuApiResult.ApiNotConfigured
                override suspend fun fetchChapterContent(aid: Int, cid: Int, appLanguage: Wenku8API.AppLanguage) =
                    WenkuApiResult.ApiNotConfigured
                override suspend fun fetchCloudBookshelf(appLanguage: Wenku8API.AppLanguage) =
                    WenkuApiResult.Success(emptyList<ParsedCloudBookshelfEntry>())
            }
            runBlocking {
                db.novelDao().upsertNovel(
                    NovelEntity(aid = 5001, title = "重建恢复测试小说", inBookshelf = true, metaSyncedAtMs = 1_000L),
                )
                tocRepo.syncNovelIndexAtomically(
                    aid = 5001,
                    language = Wenku8API.AppLanguage.SC,
                    requestStartedAtMs = 2_000L,
                    parsedVolumes = listOf(
                        ParsedVolumeToc(
                            vid = 50010,
                            volumeTitle = "第一卷",
                            chapters = listOf(ParsedChapterToc(cid = 50011, chapterTitle = "第一章")),
                        ),
                    ),
                )
                val body = "第一卷 第一章\n" + "    旋转与状态恢复测试正文内容。\n".repeat(30)
                db.chapterContentDao().upsertChapterContent(
                    ChapterContentEntity(
                        cid = 50011,
                        aid = 5001,
                        contentLang = "SC",
                        contentText = body,
                        byteLength = body.toByteArray().size,
                        sha256Checksum = "sha_50011",
                        downloadedAtMs = 1_000L,
                    ),
                )
            }

            val initialRequest = ReaderLaunchRequest(
                aid = 5001,
                vid = 50010,
                cid = 50011,
                novelTitle = "重建恢复测试小说",
                volumeTitle = "第一卷",
                chapterTitle = "第一章",
            )
            var pendingExternalRequest: ReaderLaunchRequest? = initialRequest
            val restorationTester = StateRestorationTester(composeTestRule)
            restorationTester.setContent {
                Box(modifier = Modifier.size(width = 412.dp, height = 892.dp)) {
                    Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                        AdaptiveMainScaffold(
                            preferredHomeDestination = DefaultHomeDestination.BOOKSHELF,
                            isNetworkAvailableOnColdStart = true,
                            currentThemeMode = Wenku8ThemeMode.ARCHIVAL_DAY,
                            onThemeModeSelected = {},
                            onDefaultHomeSelected = {},
                            database = db,
                            catalogGateway = gateway,
                            novelTocSyncRepository = tocRepo,
                            externalReaderLaunchRequest = pendingExternalRequest,
                            onExternalLaunchConsumed = { pendingExternalRequest = null },
                        )
                    }
                }
            }

            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithTag(ReaderTestTags.VIEWPORT_CONTAINER).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER).assertIsDisplayed()
            assertNull("External launch request should already be consumed before recreation", pendingExternalRequest)

            // Emulate full Activity destruction & saved-instance-state recreation (#229)
            restorationTester.emulateSavedInstanceStateRestore()
            composeTestRule.waitForIdle()

            // Reader MUST stay mounted instead of kicking the user back to Bookshelf/Detail!
            composeTestRule.waitUntil(5_000) {
                composeTestRule.onAllNodesWithTag(ReaderTestTags.VIEWPORT_CONTAINER).fetchSemanticsNodes().isNotEmpty()
            }
            composeTestRule.onNodeWithTag(ReaderTestTags.VIEWPORT_CONTAINER).assertIsDisplayed()
            composeTestRule.onNodeWithTag(MainScaffoldTestTags.COMPACT_SHORT_NAV_BAR).assertDoesNotExist()
        } finally {
            db.close()
        }
    }
}
