package org.mewx.wenku8.ui.theme

import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.core.view.WindowCompat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@Suppress("DEPRECATION")
class Wenku8ExpressiveThemeEdgeToEdgeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun lightThemesDisableNavBarContrastScrimAndUseDarkSystemBarIcons() {
        var activeTheme by mutableStateOf(Wenku8ThemeMode.CLASSIC_BLUE)

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = activeTheme) { }
        }
        composeTestRule.waitForIdle()

        val window = composeTestRule.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)

        assertEquals(AndroidColor.TRANSPARENT, window.statusBarColor)
        assertEquals(AndroidColor.TRANSPARENT, window.navigationBarColor)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertFalse(window.isNavigationBarContrastEnforced)
        }
        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)

        activeTheme = Wenku8ThemeMode.ARCHIVAL_DAY
        composeTestRule.waitForIdle()

        assertEquals(AndroidColor.TRANSPARENT, window.navigationBarColor)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertFalse(window.isNavigationBarContrastEnforced)
        }
        assertTrue(controller.isAppearanceLightNavigationBars)
    }

    @Test
    fun switchingBetweenLightAndDarkThemesUpdatesIconContrastWithoutRestoringScrim() {
        var activeTheme by mutableStateOf(Wenku8ThemeMode.CLASSIC_BLUE)

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = activeTheme) { }
        }
        composeTestRule.waitForIdle()

        val window = composeTestRule.activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        assertTrue(controller.isAppearanceLightNavigationBars)

        activeTheme = Wenku8ThemeMode.CARBON_NIGHT
        composeTestRule.waitForIdle()

        assertEquals(AndroidColor.TRANSPARENT, window.statusBarColor)
        assertEquals(AndroidColor.TRANSPARENT, window.navigationBarColor)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertFalse(window.isNavigationBarContrastEnforced)
        }
        assertFalse(controller.isAppearanceLightStatusBars)
        assertFalse(controller.isAppearanceLightNavigationBars)

        activeTheme = Wenku8ThemeMode.PURE_AMOLED
        composeTestRule.waitForIdle()
        assertFalse(controller.isAppearanceLightNavigationBars)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertFalse(window.isNavigationBarContrastEnforced)
        }

        activeTheme = Wenku8ThemeMode.E_INK
        composeTestRule.waitForIdle()
        assertTrue(controller.isAppearanceLightStatusBars)
        assertTrue(controller.isAppearanceLightNavigationBars)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertFalse(window.isNavigationBarContrastEnforced)
        }
    }
}
