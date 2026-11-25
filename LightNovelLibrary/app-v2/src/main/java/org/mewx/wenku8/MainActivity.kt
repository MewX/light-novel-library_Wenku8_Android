package org.mewx.wenku8

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.google.android.ump.ConsentDebugSettings
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.ui.detail.LocalAdConsentCanRequestAds
import org.mewx.wenku8.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.ui.navigation.AdaptiveMainScaffold
import org.mewx.wenku8.ui.navigation.Wenku8DeepLinks
import org.mewx.wenku8.ui.profile.TtsPreferencesActions
import org.mewx.wenku8.ui.reader.HardwareVolumeKeyHost
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.applyTransparentEdgeToEdge
import org.mewx.wenku8.util.AdConsentDebugConfig
import org.mewx.wenku8.widget.ContinueReadingAppWidgetProvider
import org.mewx.wenku8.widget.parseWidgetReaderLaunchRequest

/**
 * Single-Activity Compose host for Wenku8 `:app-v2`.
 *
 * Extends [AppCompatActivity] rather than bare `ComponentActivity` so `AppCompatDelegate.setApplicationLocales`
 * provides seamless in-app Simplified / Traditional Chinese (`zh-CN` / `zh-TW` / `zh-HK`) switching
 * across API 24–37 without restarting the process on Android 13+.
 */
class MainActivity : AppCompatActivity(), HardwareVolumeKeyHost {

    override var activeVolumeKeyInterceptor: ((KeyEvent) -> Boolean)? = null

    private var pendingWidgetReaderRequest by mutableStateOf<ReaderLaunchRequest?>(null)
    private var pendingWidgetOpenBookshelf by mutableStateOf(false)
    private var pendingExternalNovelAid by mutableIntStateOf(0)

    private val playUpdateLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        val coordinator =
            (application as? Wenku8V2Application)?.appContainer?.appUpdateCoordinator ?: return@registerForActivityResult
        lifecycleScope.launch {
            coordinator.onUpdateFlowResult(result.resultCode)
        }
    }

    override fun onResume() {
        super.onResume()
        org.mewx.wenku8.util.CrashReporter.log("MainActivity#onResume")
        val coordinator =
            (application as? Wenku8V2Application)?.appContainer?.appUpdateCoordinator ?: return
        lifecycleScope.launch {
            coordinator.onHostResumed()
        }
    }

    override fun onDestroy() {
        if (isFinishing) {
            org.mewx.wenku8.ui.detail.ServerSupportBannerPreloader.destroyAll()
            (application as? Wenku8V2Application)?.appContainer?.appUpdateCoordinator?.dispose()
        }
        super.onDestroy()
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
            event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN
        ) {
            if (activeVolumeKeyInterceptor?.invoke(event) == true) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeExternalDeepLinkIfPresent(intent)
    }

    private fun consumeExternalDeepLinkIfPresent(incomingIntent: Intent?) {
        val sharedBookAid = Wenku8DeepLinks.parseShareBookIntentAid(incomingIntent)
        if (sharedBookAid != null) {
            pendingExternalNovelAid = sharedBookAid
            pendingWidgetReaderRequest = null
            pendingWidgetOpenBookshelf = false
            return
        }
        val readerRequest = parseWidgetReaderLaunchRequest(incomingIntent)
        if (readerRequest != null) {
            pendingWidgetReaderRequest = readerRequest
            pendingWidgetOpenBookshelf = false
            pendingExternalNovelAid = 0
            return
        }
        if (incomingIntent?.getBooleanExtra(
                ContinueReadingAppWidgetProvider.EXTRA_WIDGET_OPEN_BOOKSHELF,
                false,
            ) == true
        ) {
            pendingWidgetOpenBookshelf = true
            pendingExternalNovelAid = 0
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.DEBUG && intent?.getBooleanExtra(EXTRA_DEBUG_SHOW_WHEN_LOCKED, false) == true) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            }
        }
        val isSystemDark =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        applyTransparentEdgeToEdge(isDark = isSystemDark)
        super.onCreate(savedInstanceState)
        org.mewx.wenku8.util.CrashReporter.log(
            if (savedInstanceState == null) "MainActivity#onCreate(fresh)" else "MainActivity#onCreate(recreated)",
        )

        val launchedFromHistory =
            ((intent?.flags ?: 0) and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) != 0
        if (savedInstanceState == null && !launchedFromHistory) {
            consumeExternalDeepLinkIfPresent(intent)
        }

        val appContainer = (application as Wenku8V2Application).appContainer
        val adConsentManager = appContainer.adConsentManager
        adConsentManager.gatherConsent(
            activity = this,
            debugConfig = resolveDebugConsentConfig(intent),
        )
        val preferencesRepository = appContainer.userPreferencesRepository
        val discoveryRepository = appContainer.discoveryRepository
        val hasActiveNetworkOnColdStart = checkActiveNetworkConnectivity(this)

        setContent {
            val nullablePreferences by preferencesRepository.preferencesFlow
                .collectAsStateWithLifecycle(initialValue = null)
            val canRequestAds by adConsentManager.canRequestAds
                .collectAsStateWithLifecycle()
            val isPrivacyOptionsRequired by adConsentManager.isPrivacyOptionsRequired
                .collectAsStateWithLifecycle()
            val coroutineScope = rememberCoroutineScope()
            val activePreferences = nullablePreferences ?: Wenku8UserPreferences()

            Wenku8ExpressiveTheme(
                themeMode = activePreferences.themeMode,
                einkModeOverride = activePreferences.einkModeEnabled,
            ) {
                CompositionLocalProvider(LocalAdConsentCanRequestAds provides canRequestAds) {
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
                        Box(modifier = Modifier.fillMaxSize()) {
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
                                catalogGateway = appContainer.wenkuApiClient,
                                novelTocSyncRepository = appContainer.novelTocSyncRepository,
                                externalReaderLaunchRequest = pendingWidgetReaderRequest,
                                externalOpenBookshelf = pendingWidgetOpenBookshelf,
                                externalOpenNovelAid = pendingExternalNovelAid,
                                onExternalLaunchConsumed = {
                                    pendingWidgetReaderRequest = null
                                    pendingWidgetOpenBookshelf = false
                                    pendingExternalNovelAid = 0
                                },
                                relayRouteController = appContainer.relayRouteController,
                                discoveryRepository = discoveryRepository,
                                userSessionRepository = appContainer.userSessionRepository,
                                serverNoticeAndUpdateRepository = appContainer.serverNoticeAndUpdateRepository,
                                appUpdateCoordinator = appContainer.appUpdateCoordinator,
                                onLaunchPlayFlexibleUpdate = {
                                    appContainer.appUpdateCoordinator.startFlexibleUpdate(playUpdateLauncher)
                                },
                                storageAndCacheRepository = appContainer.storageAndCacheRepository,
                                backupArchiveRepository = appContainer.backupArchiveRepository,
                                reviewsRepository = appContainer.reviewsRepository,
                                bookshelfRepository = appContainer.bookshelfRepository,
                                novelDetailRepository = appContainer.novelDetailRepository,
                                novelDownloadRepository = appContainer.novelDownloadRepository,
                                legacyV1StorageMigrator = appContainer.legacyV1StorageMigrator,
                                isPrivacyOptionsRequired = isPrivacyOptionsRequired,
                                onOpenPrivacyOptions = {
                                    adConsentManager.showPrivacyOptionsForm(this@MainActivity)
                                },
                                appLanguage = loadedPreferences.appLanguage,
                                networkDiagnosticsViewModelFactory = appContainer.networkDiagnosticsViewModelFactory,
                                autoOptimizeConnectionEnabled = loadedPreferences.autoOptimizeConnectionEnabled,
                                onAutoOptimizeConnectionChanged = { enabled ->
                                    appContainer.relayRouteController.onUserToggledAutoOptimize(enabled)
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
                                onReaderPageTurnModeChanged = { mode ->
                                    coroutineScope.launch {
                                        preferencesRepository.setReaderPageTurnMode(mode)
                                    }
                                },
                                onReaderColumnModeChanged = { columnMode ->
                                    coroutineScope.launch {
                                        preferencesRepository.setReaderColumnMode(columnMode)
                                    }
                                },
                                onReaderPaperPresetChanged = { preset ->
                                    coroutineScope.launch {
                                        preferencesRepository.setReaderPaperPreset(preset)
                                    }
                                },
                                onReaderKeepScreenOnChanged = { enabled ->
                                    coroutineScope.launch {
                                        preferencesRepository.setReaderKeepScreenOn(enabled)
                                    }
                                },
                                onReaderEdgeBrightnessDragChanged = { enabled ->
                                    coroutineScope.launch {
                                        preferencesRepository.setReaderEdgeBrightnessDragEnabled(enabled)
                                    }
                                },
                                onVolumeKeyPagingChanged = { enabled ->
                                    coroutineScope.launch {
                                        preferencesRepository.setVolumeKeyPagingEnabled(enabled)
                                    }
                                },
                                ttsPreferencesActions = TtsPreferencesActions(
                                    onPreferredEngineChanged = { pkg ->
                                        coroutineScope.launch {
                                            preferencesRepository.setTtsPreferredEnginePackage(pkg)
                                        }
                                    },
                                    onFallbackEngineChanged = { pkg ->
                                        coroutineScope.launch {
                                            preferencesRepository.setTtsFallbackEnginePackage(pkg)
                                        }
                                    },
                                    onAutoFallbackChanged = { enabled ->
                                        coroutineScope.launch {
                                            preferencesRepository.setTtsAutoFallbackEnabled(enabled)
                                        }
                                    },
                                    onPreferredVoiceChanged = { voice ->
                                        coroutineScope.launch {
                                            preferencesRepository.setTtsPreferredVoiceName(voice)
                                        }
                                    },
                                    onSpeechRateChanged = { rate ->
                                        coroutineScope.launch {
                                            preferencesRepository.setTtsSpeechRate(rate)
                                        }
                                    },
                                    onPitchChanged = { pitch ->
                                        coroutineScope.launch {
                                            preferencesRepository.setTtsPitch(pitch)
                                        }
                                    },
                                    onDefaultSleepTimerChanged = { minutes ->
                                        coroutineScope.launch {
                                            preferencesRepository.setTtsDefaultSleepTimerMinutes(minutes)
                                        }
                                    },
                                    onFollowReadingPageChanged = { enabled ->
                                        coroutineScope.launch {
                                            preferencesRepository.setTtsFollowReadingPage(enabled)
                                        }
                                    },
                                    onPauseOnAudioFocusLossChanged = { enabled ->
                                        coroutineScope.launch {
                                            preferencesRepository.setTtsPauseOnAudioFocusLoss(enabled)
                                        }
                                    },
                                    onResetDefaults = {
                                        coroutineScope.launch {
                                            preferencesRepository.resetTtsDefaults()
                                        }
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Optional debug-only UMP geography override via adb launch extras (strictly disabled in
     * release builds and off by default during normal dev runs).
     */
    private fun resolveDebugConsentConfig(incomingIntent: Intent?): AdConsentDebugConfig? {
        if (!BuildConfig.DEBUG || incomingIntent == null) return null
        val geographyName = incomingIntent.getStringExtra(EXTRA_DEBUG_UMP_GEOGRAPHY)?.trim()?.lowercase()
            ?: return null
        val geography = when (geographyName) {
            "eea" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA
            "other" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_OTHER
            "regulated_us_state" -> ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_REGULATED_US_STATE
            else -> return null
        }
        val hashedId = incomingIntent.getStringExtra(EXTRA_DEBUG_UMP_TEST_DEVICE_ID)?.trim().orEmpty()
        val resetBeforeGather = incomingIntent.getBooleanExtra(EXTRA_DEBUG_UMP_RESET, false)
        return AdConsentDebugConfig(
            debugGeography = geography,
            testDeviceHashedIds = listOfNotNull(hashedId.takeIf { it.isNotEmpty() }),
            resetConsentBeforeGather = resetBeforeGather,
        )
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
        const val EXTRA_DEBUG_UMP_GEOGRAPHY = "debug_ump_geography"
        const val EXTRA_DEBUG_UMP_TEST_DEVICE_ID = "debug_ump_test_device_id"
        const val EXTRA_DEBUG_UMP_RESET = "debug_ump_reset"
    }
}
