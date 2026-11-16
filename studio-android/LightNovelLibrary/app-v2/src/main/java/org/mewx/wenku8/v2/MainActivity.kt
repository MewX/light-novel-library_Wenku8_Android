package org.mewx.wenku8.v2

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.v2.ui.navigation.AdaptiveMainScaffold
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme

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

        val appContainer = (application as Wenku8V2Application).appContainer
        val preferencesRepository = appContainer.userPreferencesRepository
        val discoveryRepository = appContainer.discoveryRepository
        val hasActiveNetworkOnColdStart = checkActiveNetworkConnectivity(this)

        setContent {
            val nullablePreferences by preferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = null)
            val coroutineScope = rememberCoroutineScope()
            val activePreferences = nullablePreferences ?: Wenku8UserPreferences()

            Wenku8ExpressiveTheme(
                themeMode = activePreferences.themeMode,
                einkModeOverride = activePreferences.einkModeEnabled,
                useDynamicColor = activePreferences.useDynamicColor,
            ) {
                // Wait for the initial DataStore snapshot (~1-3ms off disk) before locking in the
                // cold-start destination so a user who chose "本地书架" never flashes "最新" first.
                val loadedPreferences = nullablePreferences
                if (loadedPreferences == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                    )
                } else {
                    AdaptiveMainScaffold(
                        preferredHomeDestination = loadedPreferences.defaultHomeDestination,
                        isNetworkAvailableOnColdStart = hasActiveNetworkOnColdStart,
                        currentThemeMode = loadedPreferences.themeMode,
                        onThemeModeSelected = { selectedMode ->
                            coroutineScope.launch {
                                preferencesRepository.setThemeMode(selectedMode)
                            }
                        },
                        onDefaultHomeSelected = { selectedHome ->
                            coroutineScope.launch {
                                preferencesRepository.setDefaultHomeDestination(selectedHome)
                            }
                        },
                        userPreferences = loadedPreferences,
                        database = appContainer.database,
                        relayRouteController = appContainer.relayRouteController,
                        discoveryRepository = discoveryRepository,
                        userSessionRepository = appContainer.userSessionRepository,
                        serverNoticeAndUpdateRepository = appContainer.serverNoticeAndUpdateRepository,
                        storageAndCacheRepository = appContainer.storageAndCacheRepository,
                        backupArchiveRepository = appContainer.backupArchiveRepository,
                        reviewsRepository = appContainer.reviewsRepository,
                        bookshelfRepository = appContainer.bookshelfRepository,
                        legacyV1StorageMigrator = appContainer.legacyV1StorageMigrator,
                        appLanguage = loadedPreferences.appLanguage,
                        networkDiagnosticsViewModelFactory = appContainer.networkDiagnosticsViewModelFactory,
                        autoOptimizeConnectionEnabled = loadedPreferences.autoOptimizeConnectionEnabled,
                        onAutoOptimizeConnectionChanged = { enabled ->
                            coroutineScope.launch {
                                preferencesRepository.setAutoOptimizeConnectionEnabled(enabled)
                            }
                        },
                        onBookshelfViewModeSelected = { selectedViewMode ->
                            coroutineScope.launch {
                                preferencesRepository.setBookshelfViewMode(selectedViewMode)
                            }
                        },
                        onBookshelfSortModeSelected = { selectedSortMode ->
                            coroutineScope.launch {
                                preferencesRepository.setBookshelfSortMode(selectedSortMode)
                            }
                        },
                        onAppLanguageSelected = { newLanguage ->
                            coroutineScope.launch {
                                preferencesRepository.setAppLanguage(newLanguage)
                            }
                            val bcp47Tag = if (newLanguage == Wenku8API.AppLanguage.TC) "zh-TW" else "zh-CN"
                            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(bcp47Tag))
                        },
                        onEinkModeChanged = { enabled ->
                            coroutineScope.launch {
                                preferencesRepository.setEinkModeEnabled(enabled)
                            }
                        },
                        onUseDynamicColorChanged = { enabled ->
                            coroutineScope.launch {
                                preferencesRepository.setUseDynamicColor(enabled)
                            }
                        },
                        onMenuBackgroundChanged = { presetId, customPath ->
                            coroutineScope.launch {
                                preferencesRepository.setMenuBackground(presetId, customPath)
                            }
                        },
                        onReaderTypographyChanged = { fontSizeSp, lineDistDp, paraDistDp, edgeDistDp ->
                            coroutineScope.launch {
                                preferencesRepository.setReaderTypography(
                                    fontSizeSp = fontSizeSp,
                                    lineDistanceDp = lineDistDp,
                                    paragraphDistanceDp = paraDistDp,
                                    pageEdgeDistanceDp = edgeDistDp,
                                )
                            }
                        },
                        onReaderCustomFontPathChanged = { path ->
                            coroutineScope.launch {
                                preferencesRepository.setReaderCustomFontPath(path)
                            }
                        },
                        onReaderCustomBackgroundPathChanged = { path ->
                            coroutineScope.launch {
                                preferencesRepository.setReaderCustomBackgroundPath(path)
                            }
                        },
                        onVolumeKeyPagingChanged = { enabled ->
                            coroutineScope.launch {
                                preferencesRepository.setVolumeKeyPagingEnabled(enabled)
                            }
                        },
                    )
                }
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
