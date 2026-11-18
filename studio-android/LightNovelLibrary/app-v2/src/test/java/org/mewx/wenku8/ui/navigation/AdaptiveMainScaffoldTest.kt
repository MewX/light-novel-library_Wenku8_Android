package org.mewx.wenku8.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
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
}
