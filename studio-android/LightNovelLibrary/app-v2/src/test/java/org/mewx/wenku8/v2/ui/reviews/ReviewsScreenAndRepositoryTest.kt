package org.mewx.wenku8.v2.ui.reviews

import android.content.ContentValues
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
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
import org.mewx.wenku8.api.Wenku8Error
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.remote.api.NativeWenkuParamsFactory
import org.mewx.wenku8.v2.data.remote.api.OnlineSearchField
import org.mewx.wenku8.v2.data.remote.api.WenkuApiResult
import org.mewx.wenku8.v2.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.v2.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.v2.data.remote.parser.ParsedReviewReplyItem
import org.mewx.wenku8.v2.data.remote.parser.ParsedReviewReplyPage
import org.mewx.wenku8.v2.data.remote.parser.ParsedReviewThreadItem
import org.mewx.wenku8.v2.data.remote.parser.ParsedReviewThreadPage
import org.mewx.wenku8.v2.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.v2.data.remote.transport.WenkuTransport
import org.mewx.wenku8.v2.data.repository.AccountAuthGateway
import org.mewx.wenku8.v2.data.repository.ParsedVolumeToc
import org.mewx.wenku8.v2.data.repository.ReviewSubmitOutcome
import org.mewx.wenku8.v2.data.repository.ReviewThreadCardItem
import org.mewx.wenku8.v2.data.repository.ReviewsRepository
import org.mewx.wenku8.v2.data.repository.StoredAccountCredentials
import org.mewx.wenku8.v2.data.repository.UserSessionRepository
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class ReviewsScreenAndRepositoryTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: Wenku8Database
    private lateinit var tempAssetRoot: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .build()
        tempAssetRoot = File(context.cacheDir, "review_test_assets_${System.nanoTime()}").apply {
            mkdirs()
        }
    }

    @After
    fun tearDown() {
        database.close()
        tempAssetRoot.deleteRecursively()
    }

    @Test
    fun xmlParsers_parseReviewListAndReplies_andRejectInterstitialHtml() {
        val listXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <metadata>
              <page num="3"/>
              <item rid="9001" posttime="20240518143022" replies="12" replytime="20240519091500">
                <user uid="12345">奇诺旅人</user>
                <content>第一卷读后感：温柔而冷冽的寓言</content>
              </item>
            </metadata>
        """.trimIndent()

        val parsedList = WenkuXmlParsers.parseReviewThreadListPage(listXml)
        assertNotNull(parsedList)
        assertEquals(3, parsedList!!.totalPages)
        assertEquals(1, parsedList.items.size)
        val firstThread = parsedList.items.single()
        assertEquals(9001, firstThread.rid)
        assertEquals(12345, firstThread.uid)
        assertEquals("奇诺旅人", firstThread.userName)
        assertEquals(12, firstThread.replyCount)
        assertEquals("2024-05-18 14:30", firstThread.postTimeFormatted)
        assertEquals("2024-05-19 09:15", firstThread.lastReplyTimeFormatted)
        assertEquals("第一卷读后感：温柔而冷冽的寓言", firstThread.title)

        val replyXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <metadata>
              <page num="2"/>
              <item timestamp="20240518143022">
                <user uid="12345">奇诺旅人</user>
                <content>世界并不美丽，但也因此美丽无比。</content>
              </item>
              <item timestamp="20240518150109">
                <user uid="67890">艾尔梅斯</user>
                <content>非常赞同楼主的解读，尤其是第三章！</content>
              </item>
            </metadata>
        """.trimIndent()

        val parsedReplies = WenkuXmlParsers.parseReviewReplyPage(replyXml)
        assertNotNull(parsedReplies)
        assertEquals(2, parsedReplies!!.totalPages)
        assertEquals(2, parsedReplies.items.size)
        assertEquals("2024-05-18 14:30", parsedReplies.items[0].replyTimeFormatted)
        assertEquals("艾尔梅斯", parsedReplies.items[1].userName)

        val interstitialHtml = "<html><body><h1>Gateway Captive Portal</h1></body></html>"
        assertNull(WenkuXmlParsers.parseReviewThreadListPage(interstitialHtml))
        assertNull(WenkuXmlParsers.parseReviewReplyPage(interstitialHtml))
    }

    @Test
    fun nativeParamsFactory_isStubAndHostJvmSafe() {
        // Must never throw UnsupportedOperationException or UnsatisfiedLinkError on either variant.
        assertEquals(7, NativeWenkuParamsFactory.minReplyTextLength())
        val avatar = NativeWenkuParamsFactory.resolveAvatarUrl(12345)
        assertTrue(avatar.isEmpty() || avatar.contains("12345.jpg"))
        val badWordResult = NativeWenkuParamsFactory.findBadWord("奇诺之旅真好看")
        assertNull(badWordResult)
    }

    @Test
    fun repository_validatesLengthBadWordsCooldown_reauthenticatesOnceOnCode4_andPersistsDraftsSafely() = runTest {
        var fakeClockMs = 50_000L
        val gateway = FakeReviewCatalogGateway()

        // Set up UserSessionRepository with saved cert.wk8 so ServerErrorCode(4) triggers single re-login.
        var loginCallCount = 0
        val fakeTransport = object : WenkuTransport {
            override val isEndpointConfigured: Boolean = true
            override suspend fun postForm(
                endpointUrl: String,
                formParams: ContentValues,
            ): ByteArray? {
                val action = formParams.getAsString("action")
                return when (action) {
                    "login" -> {
                        loginCallCount++
                        "1".toByteArray(StandardCharsets.UTF_8)
                    }
                    "userinfo" -> """
                        <metadata>
                          <item name="uname">kino</item>
                          <item name="nickname">奇诺</item>
                          <item name="uid" value="12345"/>
                          <item name="score" value="200"/>
                          <item name="experience" value="500"/>
                          <item name="rank">轻国骑士</item>
                        </metadata>
                    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                    else -> null
                }
            }

            override suspend fun getBytes(url: String): ByteArray? = null
        }
        val fakeAuthGateway = object : AccountAuthGateway {
            override val isAuthConfigured: Boolean = true
            override val baseUrlOrEmpty: String = "https://relay.example/api.php"
            override val registerUrlOrEmpty: String = ""
            override fun loginParams(identifier: String, password: String) =
                ContentValues().apply { put("action", "login") }
            override fun logoutParams(): ContentValues? = null
            override fun userInfoParams() = ContentValues().apply { put("action", "userinfo") }
            override fun userSignParams(): ContentValues? = null
            override fun avatarUrlForUid(uid: Int): String = ""
            override fun mapServerErrorCode(rawCode: Int): Wenku8Error.ErrorCode =
                if (rawCode == 1) Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED else Wenku8Error.ErrorCode.ERROR_DEFAULT
            override fun decodeCertBlob(rawCertContent: String) =
                StoredAccountCredentials("kino", "secret123")
            override fun encodeCertBlob(identifier: String, password: String): String = "ENC_CERT"
            override fun syncInMemoryCredentials(identifier: String, password: String) = Unit
            override fun clearInMemorySession() = Unit
        }
        File(tempAssetRoot, "saves").apply { mkdirs() }
            .let { File(it, "cert.wk8").writeText("ENC_CERT") }

        val sessionRepository = UserSessionRepository(
            transport = fakeTransport,
            assetRootsProvider = { listOf(tempAssetRoot) },
            authGateway = fakeAuthGateway,
        )
        sessionRepository.initializeSessionOnStartup()
        assertEquals(1, loginCallCount)

        val repo = ReviewsRepository(
            database = database,
            catalogGateway = gateway,
            userSessionRepository = sessionRepository,
            postCooldownMs = 10_000L,
            monotonicClockMs = { fakeClockMs },
            wallClockMs = { fakeClockMs },
        )

        // 1. FK-safe draft persistence even when NovelEntity(aid = 1306) does not yet exist in `novels`.
        repo.saveOrClearDraft(
            aid = 1306,
            novelTitleHint = "奇诺之旅",
            rid = 9001,
            draftContent = "这是一段自动保存的草稿内容",
        )
        val loadedDraft = repo.loadDraft(aid = 1306, rid = 9001)
        assertNotNull(loadedDraft)
        assertEquals("这是一段自动保存的草稿内容", loadedDraft!!.draftContent)
        assertNotNull(database.novelDao().getNovelByAid(1306))

        // 2. Too short (< 7 chars) and bad-word rejection.
        val shortCheck = repo.evaluateComposer(content = "太短了", requireTitle = false)
        assertFalse(shortCheck.lengthSatisfied)
        assertFalse(shortCheck.canSubmit)

        val badWordCheck = repo.evaluateComposer(
            title = "正常书评标题满七个字",
            content = "正文包含测试违禁词满七个字",
            requireTitle = true,
        )
        assertEquals("测试违禁词", badWordCheck.detectedBadWord)
        assertFalse(badWordCheck.canSubmit)

        // 3. First reply returns ServerErrorCode(4) -> single re-login -> retry succeeds -> clears draft + activates 10s cooldown.
        gateway.replyResponses.addLast(WenkuApiResult.ServerErrorCode(4))
        gateway.replyResponses.addLast(WenkuApiResult.Success(Unit))

        val submitResult = repo.submitReply(
            aid = 1306,
            novelTitleHint = "奇诺之旅",
            rid = 9001,
            content = "非常精彩的书评分析，感谢分享！",
        )
        assertEquals(ReviewSubmitOutcome.Success, submitResult)
        assertEquals(2, loginCallCount)
        assertNull(repo.loadDraft(aid = 1306, rid = 9001))
        assertEquals(10, repo.remainingCooldownSeconds(fakeClockMs))

        // 4. Attempting another post 3s later is blocked by 10s cooldown; after 10s it succeeds.
        fakeClockMs += 3_000L
        val duringCooldown = repo.submitReply(
            aid = 1306,
            rid = 9001,
            content = "间隔不足十秒的第二次回复测试",
        )
        assertTrue(duringCooldown is ReviewSubmitOutcome.PostLockedOrCooldown)

        fakeClockMs += 7_500L
        gateway.replyResponses.addLast(WenkuApiResult.Success(Unit))
        val afterCooldown = repo.submitReply(
            aid = 1306,
            rid = 9001,
            content = "十秒冷却结束后的第二次正常回复",
        )
        assertEquals(ReviewSubmitOutcome.Success, afterCooldown)
    }

    @Test
    fun composeScreens_renderThreadsQuoteFloorsAutoSaveDraftsAndUpdateStatusChips() = runTest {
        val gateway = FakeReviewCatalogGateway().apply {
            threadPages[1] = ParsedReviewThreadPage(
                totalPages = 1,
                items = listOf(
                    ParsedReviewThreadItem(
                        rid = 9001,
                        title = "关于奇诺与摩托车艾尔梅斯的对话哲学",
                        uid = 12345,
                        userName = "奇诺旅人",
                        replyCount = 2,
                        postTimeFormatted = "2024-05-18 14:30",
                        lastReplyTimeFormatted = "2024-05-19 09:15",
                    ),
                ),
            )
            replyPages[1] = ParsedReviewReplyPage(
                totalPages = 1,
                items = listOf(
                    ParsedReviewReplyItem(
                        uid = 12345,
                        userName = "奇诺旅人",
                        replyTimeFormatted = "2024-05-18 14:30",
                        content = "一楼楼主正文：三天的停留恰到好处。",
                    ),
                    ParsedReviewReplyItem(
                        uid = 67890,
                        userName = "西兹",
                        replyTimeFormatted = "2024-05-18 15:00",
                        content = "二楼回复：陆也是很好的同伴。",
                    ),
                ),
            )
        }

        val repo = ReviewsRepository(
            database = database,
            catalogGateway = gateway,
            userSessionRepository = null,
            postCooldownMs = 10_000L,
        )

        var openedThread by androidx.compose.runtime.mutableStateOf<ReviewThreadCardItem?>(null)

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                val activeThread = openedThread
                if (activeThread == null) {
                    ReviewListScreen(
                        aid = 1306,
                        novelTitle = "奇诺之旅",
                        reviewsRepository = repo,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onNavigateBack = {},
                        onOpenThread = { openedThread = it },
                    )
                } else {
                    ReviewThreadScreen(
                        aid = 1306,
                        novelTitle = "奇诺之旅",
                        thread = activeThread,
                        reviewsRepository = repo,
                        appLanguage = Wenku8API.AppLanguage.SC,
                        onNavigateBack = { openedThread = null },
                    )
                }
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("关于奇诺与摩托车艾尔梅斯的对话哲学").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreensTestTags.REVIEW_LIST_NEW_THREAD_FAB).assertIsDisplayed()

        // Tap thread card -> verify 1楼 · 楼主 and 2楼 render on ReviewThreadScreen.
        composeTestRule.onNodeWithTag("${ReviewScreensTestTags.REVIEW_THREAD_CARD_PREFIX}9001").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("1楼 · 楼主").assertIsDisplayed()
        composeTestRule.onNodeWithText("2楼").assertIsDisplayed()
        composeTestRule.onNodeWithTag(ReviewScreensTestTags.REVIEW_REPLY_SUBMIT_BUTTON).assertIsNotEnabled()

        // Tap `[ 回复 2楼 ]` -> inserts floor quote prefix into composer -> 400ms auto-saves draft!
        composeTestRule.onNodeWithTag("${ReviewScreensTestTags.REVIEW_FLOOR_QUOTE_PREFIX}2").performClick()
        composeTestRule.onNodeWithTag(ReviewScreensTestTags.REVIEW_REPLY_INPUT)
            .assertTextContains("回复 2楼 @西兹：", substring = true)

        composeTestRule.onNodeWithTag(ReviewScreensTestTags.REVIEW_REPLY_INPUT)
            .performTextInput("同意你的看法，这段真的很温暖。")

        composeTestRule.mainClock.advanceTimeBy(500L)
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ReviewScreensTestTags.COMPOSER_CHIP_LENGTH)
            .assertTextContains("✓ 字数 ≥ 7")
        composeTestRule.onNodeWithTag(ReviewScreensTestTags.COMPOSER_CHIP_BAD_WORD)
            .assertTextContains("✓ 无敏感词")
        composeTestRule.onNodeWithTag(ReviewScreensTestTags.COMPOSER_CHIP_COOLDOWN)
            .assertTextContains("✓ 10秒间隔就绪")
        composeTestRule.onNodeWithTag(ReviewScreensTestTags.REVIEW_REPLY_SUBMIT_BUTTON).assertIsEnabled()

        composeTestRule.waitUntil(timeoutMillis = 3_000L) {
            composeTestRule
                .onAllNodes(androidx.compose.ui.test.hasTestTag(ReviewScreensTestTags.COMPOSER_DRAFT_SAVED_BADGE))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        val savedDraft = repo.loadDraft(aid = 1306, rid = 9001)
        assertNotNull(savedDraft)
        assertTrue(savedDraft!!.draftContent.contains("回复 2楼 @西兹："))
    }

    private class FakeReviewCatalogGateway : WenkuCatalogGateway {
        override val isApiConfigured: Boolean = true
        override val minReplyTextLength: Int = 7

        val threadPages = mutableMapOf<Int, ParsedReviewThreadPage>()
        val replyPages = mutableMapOf<Int, ParsedReviewReplyPage>()
        val replyResponses = ArrayDeque<WenkuApiResult<Unit>>()
        val newThreadResponses = ArrayDeque<WenkuApiResult<Unit>>()

        override fun resolveCoverUrl(aid: Int): String = "https://img.wenku8.com/$aid.jpg"
        override fun resolveAvatarUrl(uid: Int): String =
            if (uid > 0) "https://avatar.wenku8.com/${uid / 1000}/$uid.jpg" else ""

        override fun findBadWord(text: String): String? =
            if (text.contains("测试违禁词")) "测试违禁词" else null

        override suspend fun fetchReviewThreadListPage(
            aid: Int,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedReviewThreadPage> {
            val page = threadPages[pageNumber] ?: ParsedReviewThreadPage(totalPages = 1, items = emptyList())
            return WenkuApiResult.Success(page)
        }

        override suspend fun fetchReviewReplyPage(
            rid: Int,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedReviewReplyPage> {
            val page = replyPages[pageNumber] ?: ParsedReviewReplyPage(totalPages = 1, items = emptyList())
            return WenkuApiResult.Success(page)
        }

        override suspend fun postNewReviewThread(
            aid: Int,
            title: String,
            content: String,
        ): WenkuApiResult<Unit> =
            if (newThreadResponses.isNotEmpty()) newThreadResponses.removeFirst() else WenkuApiResult.Success(Unit)

        override suspend fun postReviewReply(
            rid: Int,
            content: String,
        ): WenkuApiResult<Unit> =
            if (replyResponses.isNotEmpty()) replyResponses.removeFirst() else WenkuApiResult.Success(Unit)

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
        ): WenkuApiResult<String> = WenkuApiResult.ApiNotConfigured

        override suspend fun fetchCloudBookshelf(
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.ApiNotConfigured
    }
}
