package org.mewx.wenku8.ui.update

import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.data.repository.ServerNoticeAndUpdateRepository
import org.mewx.wenku8.data.update.AppUpdateAutoPrompt
import org.mewx.wenku8.data.update.AppUpdateCoordinator
import org.mewx.wenku8.data.update.AppUpdateUiState
import org.mewx.wenku8.ui.navigation.AdaptiveMainScaffold
import org.mewx.wenku8.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.ui.navigation.MainScaffoldTestTags
import org.mewx.wenku8.ui.navigation.TopLevelDestination
import org.mewx.wenku8.ui.profile.AppUpdateCheckCard
import org.mewx.wenku8.ui.profile.ProfileAccountTestTags
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppUpdateUiIntegrationTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun appUpdateCheckCardRendersAllStatesAndInvokesChannelActions() {
        var currentState by mutableStateOf<AppUpdateUiState>(AppUpdateUiState.Idle)
        var checkClicks = 0
        var completeClicks = 0
        var playFallbackClicks = 0
        val openedUrls = mutableListOf<String>()

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                AppUpdateCheckCard(
                    uiState = currentState,
                    onCheckForUpdate = { checkClicks++ },
                    onCompleteUpdate = { completeClicks++ },
                    onOpenDownloadUrl = { openedUrls.add(it) },
                    onOpenPlayStoreFallback = { playFallbackClicks++ },
                )
            }
        }

        // Idle -> button triggers check
        composeRule.onNodeWithTag(ProfileAccountTestTags.CHECK_UPDATE_BUTTON).performClick()
        assertEquals(1, checkClicks)

        // Checking -> button disabled
        currentState = AppUpdateUiState.Checking
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProfileAccountTestTags.CHECK_UPDATE_BUTTON).assertIsNotEnabled()

        // UpToDate -> shows build number
        currentState = AppUpdateUiState.UpToDate(101)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("101", substring = true).assertIsDisplayed()

        // Direct UpdateAvailable -> opens release page URL
        currentState = AppUpdateUiState.UpdateAvailable(
            currentVersionCode = 101,
            targetVersionCode = 105,
            isPlayStoreManaged = false,
            downloadPageUrl = ServerNoticeAndUpdateRepository.BLOG_DOWNLOAD_URL,
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithText("105", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ProfileAccountTestTags.CHECK_UPDATE_BUTTON).performClick()
        assertEquals(listOf(ServerNoticeAndUpdateRepository.BLOG_DOWNLOAD_URL), openedUrls)

        // Play UpdateAvailable -> triggers check/launch flow
        currentState = AppUpdateUiState.UpdateAvailable(
            currentVersionCode = 101,
            targetVersionCode = 106,
            isPlayStoreManaged = true,
            downloadPageUrl = null,
        )
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProfileAccountTestTags.CHECK_UPDATE_BUTTON).performClick()
        assertEquals(2, checkClicks)

        // Downloading -> displays progress bar and percentage
        currentState = AppUpdateUiState.Downloading(bytesDownloaded = 40L, totalBytes = 100L)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProfileAccountTestTags.UPDATE_DOWNLOAD_PROGRESS).assertIsDisplayed()
        composeRule.onNodeWithText("40%", substring = true).assertIsDisplayed()

        // ReadyToInstall -> restart button calls onCompleteUpdate
        currentState = AppUpdateUiState.ReadyToInstall
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProfileAccountTestTags.UPDATE_RESTART_INSTALL_BUTTON)
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, completeClicks)

        // Failed with Play Store fallback -> shows Play Store fallback button
        currentState = AppUpdateUiState.Failed(canOpenPlayStoreFallback = true)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(ProfileAccountTestTags.UPDATE_PLAY_FALLBACK_BUTTON)
            .assertIsDisplayed()
            .performClick()
        assertEquals(1, playFallbackClicks)

        // Failed without Play Store fallback -> hides Play Store fallback button
        currentState = AppUpdateUiState.Failed(canOpenPlayStoreFallback = false)
        composeRule.waitForIdle()
        assertTrue(
            composeRule.onAllNodesWithTag(ProfileAccountTestTags.UPDATE_PLAY_FALLBACK_BUTTON)
                .fetchSemanticsNodes()
                .isEmpty(),
        )
    }

    @Test
    fun adaptiveMainScaffoldTriggersDirectColdStartDialogAndClearsProfileBadgeOnVisit() {
        val fakeCoordinator = FakeAppUpdateCoordinator(isPlayStoreChannel = false).apply {
            onCheck = { isUserInitiated ->
                if (!isUserInitiated) {
                    mutableUiState.value = AppUpdateUiState.UpdateAvailable(
                        currentVersionCode = 101,
                        targetVersionCode = 105,
                        isPlayStoreManaged = false,
                        downloadPageUrl = ServerNoticeAndUpdateRepository.BLOG_DOWNLOAD_URL,
                    )
                    mutableBadge.value = true
                    mutableAutoPrompt.value = AppUpdateAutoPrompt.DirectDownloadDialog(
                        currentVersionCode = 101,
                        targetVersionCode = 105,
                        downloadPageUrl = ServerNoticeAndUpdateRepository.BLOG_DOWNLOAD_URL,
                    )
                }
            }
        }
        val openedUrls = mutableListOf<String>()

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                AdaptiveMainScaffold(
                    preferredHomeDestination = DefaultHomeDestination.LATEST,
                    isNetworkAvailableOnColdStart = true,
                    currentThemeMode = Wenku8ThemeMode.CLASSIC_BLUE,
                    onThemeModeSelected = {},
                    onDefaultHomeSelected = {},
                    appUpdateCoordinator = fakeCoordinator,
                    onOpenDirectUpdateUrl = { openedUrls.add(it) },
                )
            }
        }

        composeRule.waitForIdle()

        // Cold-start silent check ran once and surfaced the direct dialog
        assertEquals(listOf(false), fakeCoordinator.checkCalls)
        assertTrue(fakeCoordinator.showProfileUpdateBadge.value)
        composeRule.onNodeWithTag(ProfileAccountTestTags.UPDATE_AVAILABLE_DIALOG).assertIsDisplayed()

        // Tapping download opens the release page and suppresses auto-prompt for this version
        composeRule.onNodeWithText("前往下载页").performClick()
        composeRule.waitForIdle()
        assertEquals(listOf(ServerNoticeAndUpdateRepository.BLOG_DOWNLOAD_URL), openedUrls)
        assertEquals(listOf(true), fakeCoordinator.dismissCalls)
        assertTrue(
            composeRule.onAllNodesWithTag(ProfileAccountTestTags.UPDATE_AVAILABLE_DIALOG)
                .fetchSemanticsNodes()
                .isEmpty(),
        )

        // Badge remains visible on the Profile nav item until the user visits the Profile tab
        composeRule.onNodeWithTag(
            MainScaffoldTestTags.PROFILE_UPDATE_BADGE,
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag(
            MainScaffoldTestTags.NAV_ITEM_PREFIX + TopLevelDestination.PROFILE.routeKey,
        ).performClick()
        composeRule.waitForIdle()

        assertTrue(fakeCoordinator.clearBadgeCalls >= 1)
        assertFalse(fakeCoordinator.showProfileUpdateBadge.value)
        assertTrue(
            composeRule.onAllNodesWithTag(
                MainScaffoldTestTags.PROFILE_UPDATE_BADGE,
                useUnmergedTree = true,
            ).fetchSemanticsNodes().isEmpty(),
        )
    }

    @Test
    fun adaptiveMainScaffoldLaunchesPlayFlexibleUpdateAndShowsGlobalReadyToInstallSnackbar() {
        val fakeCoordinator = FakeAppUpdateCoordinator(isPlayStoreChannel = true).apply {
            onCheck = { isUserInitiated ->
                if (!isUserInitiated) {
                    mutableUiState.value = AppUpdateUiState.UpdateAvailable(
                        currentVersionCode = 101,
                        targetVersionCode = 110,
                        isPlayStoreManaged = true,
                    )
                    mutableBadge.value = true
                    mutableAutoPrompt.value = AppUpdateAutoPrompt.PlayStoreFlexible(110)
                }
            }
        }
        var launchFlexibleCount = 0

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                AdaptiveMainScaffold(
                    preferredHomeDestination = DefaultHomeDestination.LATEST,
                    isNetworkAvailableOnColdStart = true,
                    currentThemeMode = Wenku8ThemeMode.CLASSIC_BLUE,
                    onThemeModeSelected = {},
                    onDefaultHomeSelected = {},
                    appUpdateCoordinator = fakeCoordinator,
                    onLaunchPlayFlexibleUpdate = {
                        launchFlexibleCount++
                        fakeCoordinator.mutableAutoPrompt.value = null
                        true
                    },
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(1, launchFlexibleCount)

        // Background download finishes -> global Snackbar prompts user to restart & install
        fakeCoordinator.mutableUiState.value = AppUpdateUiState.ReadyToInstall
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(MainScaffoldTestTags.UPDATE_READY_SNACKBAR)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithText("重启安装").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(1, fakeCoordinator.completeUpdateCalls)
    }

    @Test
    fun adaptiveMainScaffoldSuppressesAutoPromptDuringPendingExternalDeepLinkLaunch() {
        val fakeCoordinator = FakeAppUpdateCoordinator(isPlayStoreChannel = true).apply {
            mutableAutoPrompt.value = AppUpdateAutoPrompt.PlayStoreFlexible(112)
        }
        var launchFlexibleCount = 0

        composeRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                AdaptiveMainScaffold(
                    preferredHomeDestination = DefaultHomeDestination.LATEST,
                    isNetworkAvailableOnColdStart = false,
                    currentThemeMode = Wenku8ThemeMode.CLASSIC_BLUE,
                    onThemeModeSelected = {},
                    onDefaultHomeSelected = {},
                    externalOpenNovelAid = 2541,
                    appUpdateCoordinator = fakeCoordinator,
                    onLaunchPlayFlexibleUpdate = {
                        launchFlexibleCount++
                        true
                    },
                )
            }
        }

        composeRule.waitForIdle()
        assertEquals(0, launchFlexibleCount)
    }

    private class FakeAppUpdateCoordinator(
        override val isPlayStoreChannel: Boolean,
    ) : AppUpdateCoordinator {
        val mutableUiState = MutableStateFlow<AppUpdateUiState>(AppUpdateUiState.Idle)
        val mutableAutoPrompt = MutableStateFlow<AppUpdateAutoPrompt?>(null)
        val mutableBadge = MutableStateFlow(false)

        override val uiState: StateFlow<AppUpdateUiState> = mutableUiState.asStateFlow()
        override val pendingAutoPrompt: StateFlow<AppUpdateAutoPrompt?> = mutableAutoPrompt.asStateFlow()
        override val showProfileUpdateBadge: StateFlow<Boolean> = mutableBadge.asStateFlow()

        var onCheck: suspend (Boolean) -> Unit = {}
        val checkCalls = mutableListOf<Boolean>()
        val dismissCalls = mutableListOf<Boolean>()
        var clearBadgeCalls = 0
        var completeUpdateCalls = 0

        override suspend fun checkForUpdate(isUserInitiated: Boolean) {
            checkCalls.add(isUserInitiated)
            onCheck(isUserInitiated)
        }

        override suspend fun onHostResumed() = Unit

        override fun startFlexibleUpdate(launcher: ActivityResultLauncher<IntentSenderRequest>): Boolean = true

        override suspend fun onUpdateFlowResult(resultCode: Int) = Unit

        override fun completeUpdate() {
            completeUpdateCalls++
        }

        override suspend fun dismissAutoPrompt(suppressForThisVersion: Boolean) {
            dismissCalls.add(suppressForThisVersion)
            mutableAutoPrompt.value = null
        }

        override fun clearProfileUpdateBadge() {
            clearBadgeCalls++
            mutableBadge.value = false
        }

        override fun dispose() = Unit
    }
}
