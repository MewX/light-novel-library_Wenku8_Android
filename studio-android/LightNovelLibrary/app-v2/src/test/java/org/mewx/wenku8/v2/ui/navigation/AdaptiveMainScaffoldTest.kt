package org.mewx.wenku8.v2.ui.navigation

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
import org.mewx.wenku8.v2.ui.theme.LocalEinkMode
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
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
}
