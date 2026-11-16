package org.mewx.wenku8.v2

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import org.mewx.wenku8.v2.ui.navigation.AdaptiveMainScaffold
import org.mewx.wenku8.v2.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode

/**
 * Single-Activity Compose host for Wenku8 `:app-v2`.
 *
 * Extends [AppCompatActivity] rather than bare `ComponentActivity` so `AppCompatDelegate.setApplicationLocales`
 * provides seamless in-app Simplified / Traditional Chinese (`zh-CN` / `zh-TW` / `zh-HK`) switching
 * across API 24–37 without restarting the process on Android 13+.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_DEBUG_SHOW_WHEN_LOCKED, false) == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val hasActiveNetworkOnColdStart = checkActiveNetworkConnectivity(this)

        setContent {
            var currentThemeMode by rememberSaveable {
                mutableStateOf(Wenku8ThemeMode.SYSTEM)
            }
            var preferredHomeDestination by rememberSaveable {
                mutableStateOf(DefaultHomeDestination.LATEST)
            }

            Wenku8ExpressiveTheme(
                themeMode = currentThemeMode,
            ) {
                AdaptiveMainScaffold(
                    preferredHomeDestination = preferredHomeDestination,
                    isNetworkAvailableOnColdStart = hasActiveNetworkOnColdStart,
                    currentThemeMode = currentThemeMode,
                    onThemeModeSelected = { selectedMode -> currentThemeMode = selectedMode },
                    onDefaultHomeSelected = { selectedHome -> preferredHomeDestination = selectedHome },
                )
            }
        }
    }

    /**
     * Non-blocking snapshot check of whether an active internet-capable network interface exists
     * at cold start so [AdaptiveMainScaffold] can auto-fallback from `最新` to `本地书架` offline.
     */
    private fun checkActiveNetworkConnectivity(context: Context): Boolean {
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    companion object {
        const val EXTRA_DEBUG_SHOW_WHEN_LOCKED = "debug_show_when_locked"
    }
}
