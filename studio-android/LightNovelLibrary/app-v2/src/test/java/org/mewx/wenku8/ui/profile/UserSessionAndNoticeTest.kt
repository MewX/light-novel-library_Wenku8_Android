package org.mewx.wenku8.ui.profile

import android.content.ContentValues
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.api.Wenku8Error
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.data.remote.transport.NativeLightUserSessionCookieStore
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import org.mewx.wenku8.data.repository.AccountAuthGateway
import org.mewx.wenku8.data.repository.AccountSessionState
import org.mewx.wenku8.data.repository.AppVersionCheckResult
import org.mewx.wenku8.data.repository.DailySignOutcome
import org.mewx.wenku8.data.repository.LoginOutcome
import org.mewx.wenku8.data.repository.ServerNoticeAndUpdateRepository
import org.mewx.wenku8.data.repository.StoredAccountCredentials
import org.mewx.wenku8.data.repository.UserSessionRepository
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UserSessionAndNoticeTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `parseUserInfo parses valid metadata and rejects captive portals or missing uname`() {
        val validXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <metadata>
                <item name="uname">mewx_reader</item>
                <item name="nickname">MewX 文库读者</item>
                <item name="uid">23333</item>
                <item name="score">128</item>
                <item name="experience">640</item>
                <item name="rank">正式会员</item>
            </metadata>
        """.trimIndent()

        val parsed = WenkuXmlParsers.parseUserInfo(validXml)
        assertNotNull(parsed)
        assertEquals("mewx_reader", parsed?.username)
        assertEquals("MewX 文库读者", parsed?.nickname)
        assertEquals(23333, parsed?.uid)
        assertEquals(128, parsed?.score)
        assertEquals(640, parsed?.experience)
        assertEquals("正式会员", parsed?.rank)

        assertNull(WenkuXmlParsers.parseUserInfo("<html><body>Gateway Login</body></html>"))
        assertNull(WenkuXmlParsers.parseUserInfo("<metadata><item name='uid'>10</item></metadata>"))
    }

    @Test
    fun `ServerNoticeAndUpdateRepository parses links persists notice dismissal and checks updates`() = runTest {
        val rootDir = tempFolder.newFolder("notice_root")
        var remoteNoticeHtml = """欢迎使用新版客户端！<br/><a href="https://wenku8.mewx.org/faq">查看常见问题</a>"""
        var remoteVersionBody = "999"

        val transport = FakeAccountTransport(
            onGet = { url ->
                when (url) {
                    ServerNoticeAndUpdateRepository.NOTICE_URL_SC ->
                        remoteNoticeHtml.toByteArray(StandardCharsets.UTF_8)
                    ServerNoticeAndUpdateRepository.VERSION_CHECK_URL ->
                        remoteVersionBody.toByteArray(StandardCharsets.UTF_8)
                    else -> null
                }
            },
        )

        val repo = ServerNoticeAndUpdateRepository(
            transport = transport,
            assetRootsProvider = { listOf(rootDir) },
        )

        val firstNotice = repo.loadAndRefreshNotice(Wenku8API.AppLanguage.SC)
        assertNotNull(firstNotice)
        assertEquals("欢迎使用新版客户端！\n查看常见问题", firstNotice?.plainDisplayText)
        assertEquals(1, firstNotice?.links?.size)
        assertEquals("https://wenku8.mewx.org/faq", firstNotice?.links?.first()?.url)
        assertTrue(File(rootDir, "saves/notice.wk8").isFile)

        // Dismissing hides the notice even on subsequent reloads of the same content
        repo.dismissCurrentNotice()
        assertNull(repo.activeNotice.value)
        assertNull(repo.loadAndRefreshNotice(Wenku8API.AppLanguage.SC))

        // Publishing a new server notice automatically re-surfaces it
        remoteNoticeHtml = "服务器维护已完成，感谢耐心等待！"
        val updatedNotice = repo.loadAndRefreshNotice(Wenku8API.AppLanguage.SC)
        assertNotNull(updatedNotice)
        assertEquals("服务器维护已完成，感谢耐心等待！", updatedNotice?.plainDisplayText)

        // Version check
        val available = repo.checkForAppUpdate(currentVersionCode = 55)
        assertTrue(available is AppVersionCheckResult.UpdateAvailable)
        assertEquals(999, (available as AppVersionCheckResult.UpdateAvailable).latestVersionCode)

        remoteVersionBody = "55"
        val upToDate = repo.checkForAppUpdate(currentVersionCode = 55)
        assertTrue(upToDate is AppVersionCheckResult.UpToDate)
    }

    @Test
    fun `UserSessionRepository enforces length persists cert preserves offline account and relogins once on sign`() = runTest {
        val rootDir = tempFolder.newFolder("auth_root")
        var currentScore = 100
        var sessionValid = false
        var networkAvailable = true
        var rejectPasswordOnRestore = false
        var loginCallCount = 0

        val transport = FakeAccountTransport(
            onPost = { params ->
                if (!networkAvailable) return@FakeAccountTransport null
                when (params.getAsString("op")) {
                    "login", "login_email" -> {
                        loginCallCount++
                        if (rejectPasswordOnRestore || params.getAsString("pwd") != "validPass123") {
                            "3".toByteArray(StandardCharsets.UTF_8)
                        } else {
                            sessionValid = true
                            "1".toByteArray(StandardCharsets.UTF_8)
                        }
                    }
                    "userinfo" -> {
                        if (!sessionValid) {
                            "4".toByteArray(StandardCharsets.UTF_8)
                        } else {
                            """
                                <metadata>
                                    <item name="uname">${params.getAsString("hint").orEmpty().ifEmpty { "test_user" }}</item>
                                    <item name="nickname">书架守护者</item>
                                    <item name="uid">888</item>
                                    <item name="score">$currentScore</item>
                                    <item name="experience">500</item>
                                    <item name="rank">高级会员</item>
                                </metadata>
                            """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                        }
                    }
                    "sign" -> {
                        if (!sessionValid) {
                            "4".toByteArray(StandardCharsets.UTF_8)
                        } else {
                            currentScore += 2
                            "1".toByteArray(StandardCharsets.UTF_8)
                        }
                    }
                    "logout" -> {
                        sessionValid = false
                        "1".toByteArray(StandardCharsets.UTF_8)
                    }
                    else -> null
                }
            },
            onGet = { url ->
                if (networkAvailable && url.contains("avatar")) {
                    byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
                } else {
                    null
                }
            },
        )

        val gateway = FakeAccountAuthGateway()
        val repo = UserSessionRepository(
            transport = transport,
            assetRootsProvider = { listOf(rootDir) },
            authGateway = gateway,
        )

        // Over-length (> 30 chars) rejected before network
        val tooLongId = "a".repeat(31)
        assertEquals(
            LoginOutcome.InvalidCredentialLength,
            repo.loginWithCredentials(tooLongId, "validPass123"),
        )
        assertEquals(0, loginCallCount)

        // Valid login persists cert.wk8 & avatar.jpg
        val loginSuccess = repo.loginWithCredentials("test_user", "validPass123")
        assertTrue(loginSuccess is LoginOutcome.Success)
        val certFile = File(rootDir, "saves/cert.wk8")
        val avatarFile = File(rootDir, "saves/avatar.jpg")
        assertTrue(certFile.isFile)
        assertTrue(avatarFile.isFile)
        assertTrue(repo.sessionState.value is AccountSessionState.SignedIn)

        // Simulate lapsed PHPSESSID -> performDailySignIn auto-relogins once and refreshes score
        sessionValid = false
        val beforeSignLogins = loginCallCount
        val signOutcome = repo.performDailySignIn()
        assertTrue(signOutcome is DailySignOutcome.SignedAndRefreshed)
        assertEquals(beforeSignLogins + 1, loginCallCount)
        assertEquals(102, (repo.sessionState.value as AccountSessionState.SignedIn).profile.score)

        // Cold-start while offline MUST preserve cert.wk8 & avatar.jpg!
        networkAvailable = false
        val offlineRepo = UserSessionRepository(
            transport = transport,
            assetRootsProvider = { listOf(rootDir) },
            authGateway = gateway,
        )
        offlineRepo.initializeSessionOnStartup()
        assertTrue(offlineRepo.sessionState.value is AccountSessionState.OfflineWithSavedAccount)
        assertTrue(certFile.isFile)
        assertTrue(avatarFile.isFile)

        // Explicit server SYSTEM_3_ERROR_PASSWORD on startup wipes stale cert.wk8
        networkAvailable = true
        rejectPasswordOnRestore = true
        offlineRepo.initializeSessionOnStartup()
        assertEquals(AccountSessionState.SignedOut, offlineRepo.sessionState.value)
        assertFalse(certFile.exists())

        // Re-login then explicit logout clears transport session cookie & files
        // (clearSessionCookieCalls is already 1 from the SYSTEM_3_ERROR_PASSWORD wipe above)
        rejectPasswordOnRestore = false
        assertTrue(repo.loginWithCredentials("test_user", "validPass123") is LoginOutcome.Success)
        assertEquals(1, transport.clearSessionCookieCalls)
        repo.logout()
        assertEquals(2, transport.clearSessionCookieCalls)
        assertEquals(AccountSessionState.SignedOut, repo.sessionState.value)
        assertFalse(certFile.exists())
        assertFalse(avatarFile.exists())

        // NativeLightUserSessionCookieStore local clear suppresses stale session until fresh Set-Cookie
        NativeLightUserSessionCookieStore.setPhpSessionId("sess_abc")
        assertEquals("sess_abc", NativeLightUserSessionCookieStore.getPhpSessionId())
        NativeLightUserSessionCookieStore.clearPhpSessionId()
        assertEquals("", NativeLightUserSessionCookieStore.getPhpSessionId())
    }

    @Test
    fun `AccountProfileCard LoginBottomSheet and NoticeBanner drive full M3e UI flow`() {
        val rootDir = tempFolder.newFolder("ui_auth_root")
        var currentScore = 80
        var signedToday = false
        val openedUrls = ArrayList<String>()

        val transport = FakeAccountTransport(
            onPost = { params ->
                when (params.getAsString("op")) {
                    "login", "login_email" -> {
                        if (params.getAsString("pwd") == "secret") {
                            "1".toByteArray(StandardCharsets.UTF_8)
                        } else {
                            "3".toByteArray(StandardCharsets.UTF_8)
                        }
                    }
                    "userinfo" -> """
                        <metadata>
                            <item name="uname">kino_reader</item>
                            <item name="nickname">奇诺之旅</item>
                            <item name="uid">1306</item>
                            <item name="score">$currentScore</item>
                            <item name="experience">320</item>
                            <item name="rank">资深读者</item>
                        </metadata>
                    """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                    "sign" -> {
                        if (!signedToday) {
                            signedToday = true
                            currentScore += 2
                            "1".toByteArray(StandardCharsets.UTF_8)
                        } else {
                            "9".toByteArray(StandardCharsets.UTF_8)
                        }
                    }
                    "logout" -> "1".toByteArray(StandardCharsets.UTF_8)
                    else -> null
                }
            },
        )

        val userRepo = UserSessionRepository(
            transport = transport,
            assetRootsProvider = { listOf(rootDir) },
            authGateway = FakeAccountAuthGateway(),
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )
        val parsedNotice = ServerNoticeAndUpdateRepository.parseNoticeHtmlOrNull(
            """春季书架同步升级通知 <a href="https://wenku8.mewx.org/blog">阅读详情</a>""",
        )!!

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                val sessionState by userRepo.sessionState.collectAsStateWithLifecycle()
                var showNotice by remember { mutableStateOf(true) }
                var showSheet by remember { mutableStateOf(false) }

                Column {
                    if (showNotice) {
                        ServerNoticeBannerCard(
                            notice = parsedNotice,
                            onDismiss = { showNotice = false },
                            onExternalUrlClicked = { openedUrls.add(it) },
                        )
                    }
                    AccountProfileCard(
                        sessionState = sessionState,
                        registerPageUrl = userRepo.registerPageUrl,
                        onOpenLoginSheet = { showSheet = true },
                        onPerformDailySignIn = { userRepo.performDailySignIn() },
                        onRefreshProfile = { userRepo.refreshProfile() },
                        onLogoutConfirmed = { userRepo.logout() },
                        onOpenExternalUrl = { openedUrls.add(it) },
                    )
                    if (showSheet) {
                        AccountLoginBottomSheet(
                            registerPageUrl = userRepo.registerPageUrl,
                            onSubmitLogin = { id, pwd -> userRepo.loginWithCredentials(id, pwd) },
                            onDismiss = { showSheet = false },
                            onLoginSuccess = { showSheet = false },
                            onOpenExternalUrl = { openedUrls.add(it) },
                        )
                    }
                }
            }
        }

        // Verify Notice banner link + dismiss
        composeRule.onNodeWithTag(ProfileAccountTestTags.SERVER_NOTICE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("阅读详情").performClick()
        assertEquals(listOf("https://wenku8.mewx.org/blog"), openedUrls)
        composeRule.onNodeWithTag(ProfileAccountTestTags.SERVER_NOTICE_DISMISS_BUTTON).performClick()
        composeRule.onNodeWithTag(ProfileAccountTestTags.SERVER_NOTICE_BANNER).assertDoesNotExist()

        // Open login sheet, test wrong password error, then succeed
        composeRule.onNodeWithTag(ProfileAccountTestTags.OPEN_LOGIN_SHEET_BUTTON).performClick()
        composeRule.onNodeWithTag(ProfileAccountTestTags.LOGIN_BOTTOM_SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileAccountTestTags.LOGIN_IDENTIFIER_INPUT)
            .performTextReplacement("kino_reader")
        composeRule.onNodeWithTag(ProfileAccountTestTags.LOGIN_PASSWORD_INPUT)
            .performTextReplacement("wrong")
        composeRule.onNodeWithTag(ProfileAccountTestTags.LOGIN_SUBMIT_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ProfileAccountTestTags.LOGIN_ERROR_TEXT)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ProfileAccountTestTags.LOGIN_ERROR_TEXT).assertIsDisplayed()

        composeRule.onNodeWithTag(ProfileAccountTestTags.LOGIN_PASSWORD_INPUT)
            .performTextReplacement("secret")
        composeRule.onNodeWithTag(ProfileAccountTestTags.LOGIN_SUBMIT_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("80").fetchSemanticsNodes().isNotEmpty()
        }

        // Signed-in profile rendered with nickname, rank, and initial score 80
        composeRule.onNodeWithText("奇诺之旅").assertIsDisplayed()
        composeRule.onNodeWithText("资深读者").assertIsDisplayed()
        composeRule.onNodeWithText("80").assertIsDisplayed()

        // Perform daily sign-in -> score refreshes to 82 & feedback banner shown
        composeRule.onNodeWithTag(ProfileAccountTestTags.DAILY_SIGN_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText("82").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("82").assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileAccountTestTags.DAILY_SIGN_FEEDBACK_TEXT).assertIsDisplayed()

        // Logout with confirmation dialog -> returns to SignedOut
        composeRule.onNodeWithTag(ProfileAccountTestTags.LOGOUT_BUTTON).performClick()
        composeRule.onNodeWithTag(ProfileAccountTestTags.LOGOUT_CONFIRM_BUTTON).performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(ProfileAccountTestTags.OPEN_LOGIN_SHEET_BUTTON)
                .fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(ProfileAccountTestTags.OPEN_LOGIN_SHEET_BUTTON).assertIsDisplayed()
    }

    private class FakeAccountTransport(
        private val onPost: (ContentValues) -> ByteArray? = { null },
        private val onGet: (String) -> ByteArray? = { null },
    ) : WenkuTransport {
        var clearSessionCookieCalls: Int = 0
        override val isEndpointConfigured: Boolean = true

        override suspend fun postForm(endpointUrl: String, formParams: ContentValues): ByteArray? =
            onPost(formParams)

        override suspend fun postForm(
            endpointUrl: String,
            formParams: ContentValues,
            isIdempotentReadOnly: Boolean,
        ): ByteArray? = onPost(formParams)

        override suspend fun getBytes(url: String): ByteArray? = onGet(url)

        override fun clearSessionCookie() {
            clearSessionCookieCalls++
        }
    }

    private class FakeAccountAuthGateway : AccountAuthGateway {
        override val isAuthConfigured: Boolean = true
        override val baseUrlOrEmpty: String = "https://wenku8-relay.mewx.org/android.php"
        override val registerUrlOrEmpty: String = "https://www.wenku8.net/register.php"

        override fun loginParams(identifier: String, password: String): ContentValues =
            ContentValues().apply {
                put("op", if (identifier.contains('@')) "login_email" else "login")
                put("id", identifier)
                put("pwd", password)
            }

        override fun logoutParams(): ContentValues =
            ContentValues().apply { put("op", "logout") }

        override fun userInfoParams(): ContentValues =
            ContentValues().apply { put("op", "userinfo") }

        override fun userSignParams(): ContentValues =
            ContentValues().apply { put("op", "sign") }

        override fun avatarUrlForUid(uid: Int): String =
            if (uid > 0) "https://img.wenku8.com/avatar/$uid.jpg" else ""

        override fun mapServerErrorCode(rawCode: Int): Wenku8Error.ErrorCode =
            when (rawCode) {
                1 -> Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED
                2 -> Wenku8Error.ErrorCode.SYSTEM_2_ERROR_USERNAME
                3 -> Wenku8Error.ErrorCode.SYSTEM_3_ERROR_PASSWORD
                4 -> Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN
                9 -> Wenku8Error.ErrorCode.SYSTEM_9_SIGN_FAILED
                else -> Wenku8Error.ErrorCode.ERROR_DEFAULT
            }

        override fun decodeCertBlob(rawCertContent: String): StoredAccountCredentials? {
            val parts = rawCertContent.split("\n")
            if (parts.size < 2) return null
            return StoredAccountCredentials(parts[0], parts[1])
        }

        override fun encodeCertBlob(identifier: String, password: String): String =
            "$identifier\n$password"

        override fun syncInMemoryCredentials(identifier: String, password: String) {}
        override fun clearInMemorySession() {}
    }
}
