package org.mewx.wenku8.di

import android.content.Context
import android.os.Environment
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.data.media.OfflineFirstWenkuImageFetcher
import org.mewx.wenku8.data.media.WenkuCanonicalUriKeyer
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.data.media.WenkuImmutableImageCacheInterceptor
import org.mewx.wenku8.data.migration.InternalFileSaveSource
import org.mewx.wenku8.data.migration.LegacyMigrationProgress
import org.mewx.wenku8.data.migration.LegacyV1StorageMigrator
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.remote.api.WenkuApiClient
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsEndpointConfig
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsEndpointResolution
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsNetworkMonitor
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsRunner
import org.mewx.wenku8.data.remote.diagnostics.ManifestRepository
import org.mewx.wenku8.data.remote.diagnostics.OkHttpProbeTransport
import org.mewx.wenku8.data.remote.diagnostics.ReadOnlyApiDiagnosticCapability
import org.mewx.wenku8.data.remote.diagnostics.RelayRouteController
import org.mewx.wenku8.data.remote.diagnostics.RoutingLocalStore
import org.mewx.wenku8.data.remote.diagnostics.StalledDnsGuard
import org.mewx.wenku8.data.remote.transport.CloudflareAcceleratedCallFactory
import org.mewx.wenku8.data.remote.transport.RelayHttpTransport
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import org.mewx.wenku8.data.repository.DiscoveryRepository
import org.mewx.wenku8.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.data.repository.ServerNoticeAndUpdateRepository
import org.mewx.wenku8.data.repository.UserSessionRepository
import org.mewx.wenku8.data.update.AppUpdateCoordinator
import org.mewx.wenku8.data.update.ChannelAppUpdateFactory
import org.mewx.wenku8.ui.diagnostics.NetworkDiagnosticsViewModel
import android.os.SystemClock
import java.io.File

/**
 * Lightweight, explicit constructor-injection graph for Wenku8 `:app-v2`.
 */
class AppContainer(context: Context) {

    private val appContext: Context = context.applicationContext

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: Wenku8Database by lazy {
        Wenku8Database.createWalDatabase(appContext)
    }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository.create(appContext, applicationScope)
    }

    val adConsentManager: org.mewx.wenku8.util.AdConsentManager by lazy {
        org.mewx.wenku8.util.UmpAdConsentManager(appContext)
    }

    private val hasStartedConsentGatedAdWarmUp = java.util.concurrent.atomic.AtomicBoolean(false)

    val sharedBaseOkHttpClient: OkHttpClient by lazy {
        OkHttpProbeTransport.defaultSharedBaseOkHttpClient()
    }

    /**
     * Shared [Call.Factory] for all direct app `GET` traffic: Coil 3 covers & chapter illustrations
     * (`img.wenku8.com`, `pic.wenku8.com`, `pic.wenku8.cn`, and redirect targets like
     * `pic.777743.xyz`), Artbook high-res downloads, offline novel downloads, user avatars, and
     * notice/version checks (`wenku8.mewx.org` / `wenku8.app`).
     *
     * When `"自动优化连接"` has an active verified Cloudflare IP in [relayRouteController], requests
     * automatically ride that preferred Cloudflare Anycast IP on an isolated connection pool with
     * transparent per-host fallback to [Dns.SYSTEM] (never polluting the relay lease).
     */
    val sharedDirectGetCallFactory: CloudflareAcceleratedCallFactory by lazy {
        CloudflareAcceleratedCallFactory(
            baseOkHttpClient = sharedBaseOkHttpClient,
            edgeRouteProvider = relayRouteController,
            systemDns = Dns.SYSTEM,
            networkInterceptors = listOf(WenkuImmutableImageCacheInterceptor),
            dynamicPictureMirrorsProvider = { manifestRepository.currentDynamicPictureMirrorsOrEmpty() },
        )
    }

    // =========================================================================
    // Network Diagnostics & Anti-Blocking Relay Routing Stack
    // =========================================================================
    // - `manifestRepository`: Resolves live rotated Cloudflare /24 & /48 Anycast subnets from
    //   `_network_cfg.wenku8.app` via pure-IP domestic HTTPS DoH (`223.5.5.5`, `1.12.12.12`, etc.),
    //   falling back to `routingLocalStore` cache and 24 bundled Cloudflare subnets offline.
    // - `relayRouteController`: Runs bounded diagnostics tournaments & bootstrap recovery when
    //   Default System DNS is poisoned/degraded, promoting verified Cloudflare IPs with warm HTTP/2
    //   connection pools while respecting user opt-in (`autoOptimizeConnectionEnabled`) and 24h budgets.
    // - `wenkuTransport` (`RelayHttpTransport`): Executes all Wenku8 relay POST calls over the
    //   active route with full TLS hostname verification and automatic pre-send fallback to System DNS
    //   if a pinned candidate IP ever becomes unreachable, and routes direct GETs (images, avatars,
    //   notices, version checks) through `sharedDirectGetCallFactory`.
    val stalledDnsGuard: StalledDnsGuard by lazy {
        StalledDnsGuard(maxConcurrentProbePermits = 2)
    }

    val diagnosticsNetworkMonitor: DiagnosticsNetworkMonitor by lazy {
        DiagnosticsNetworkMonitor(appContext)
    }

    val routingLocalStore: RoutingLocalStore by lazy {
        RoutingLocalStore.create(appContext, applicationScope)
    }

    val manifestRepository: ManifestRepository by lazy {
        ManifestRepository(
            localStore = routingLocalStore,
            baseOkHttpClient = sharedBaseOkHttpClient,
            dohTxtDomain = ManifestRepository.DEFAULT_DOH_TXT_CONFIG_DOMAIN,
            dohEndpoints = ManifestRepository.defaultDomesticDohEndpoints(),
            bundledCandidateSpecs = ManifestRepository.defaultVerifiedCloudflareSubnetSpecs(),
        ).also { repo ->
            // Warm any locally persisted manifest (including dynamic `pm=` picture mirrors)
            // asynchronously so cold-start image loads can pick them up immediately.
            applicationScope.launch {
                val resolved = DiagnosticsEndpointConfig.resolveFromCompiledApi()
                if (resolved is DiagnosticsEndpointResolution.Available) {
                    runCatching { repo.loadCachedOrInMemoryManifest(resolved.endpoint.relayHost) }
                }
            }
        }
    }

    val okHttpProbeTransport: OkHttpProbeTransport by lazy {
        OkHttpProbeTransport(baseOkHttpClient = sharedBaseOkHttpClient)
    }

    val diagnosticsRunner: DiagnosticsRunner by lazy {
        DiagnosticsRunner(
            probeTransport = okHttpProbeTransport,
            networkObserver = diagnosticsNetworkMonitor,
            stalledDnsGuard = stalledDnsGuard,
        )
    }

    val relayRouteController: RelayRouteController by lazy {
        RelayRouteController(
            applicationScope = applicationScope,
            endpointResolutionProvider = { DiagnosticsEndpointConfig.resolveFromCompiledApi() },
            probeTransport = okHttpProbeTransport,
            manifestRepository = manifestRepository,
            routingLocalStore = routingLocalStore,
            networkObserver = diagnosticsNetworkMonitor,
            stalledDnsGuard = stalledDnsGuard,
            allowBootstrapRecoveryOnDefaultFailure = true,
            allowAutoSelectOnEnable = true,
        ).also { controller ->
            applicationScope.launch {
                userPreferencesRepository.preferencesFlow
                    .map { it.autoOptimizeConnectionEnabled }
                    .distinctUntilChanged()
                    .collect { enabled ->
                        controller.setAutoOptimizeEnabled(enabled)
                    }
            }
        }
    }

    private val hasTriggeredSoftUpdateHintCheck = java.util.concurrent.atomic.AtomicBoolean(false)

    val wenkuTransport: WenkuTransport by lazy {
        RelayHttpTransport(
            baseOkHttpClient = sharedBaseOkHttpClient,
            routeProvider = relayRouteController,
            directGetCallFactory = sharedDirectGetCallFactory,
            onUpgradeRequired = { minVersionCode, minVersionName ->
                appUpdateCoordinator.onServerUpgradeRequired(minVersionCode, minVersionName)
            },
            onSoftUpdateHint = { minVersionCode ->
                appUpdateCoordinator.onServerUpdateHint(minVersionCode)
                if (hasTriggeredSoftUpdateHintCheck.compareAndSet(false, true)) {
                    applicationScope.launch {
                        runCatching {
                            appUpdateCoordinator.checkForUpdate(isUserInitiated = false)
                        }
                    }
                }
            },
        )
    }

    val wenkuApiClient: WenkuApiClient by lazy {
        WenkuApiClient(transport = wenkuTransport)
    }

    val readOnlyApiDiagnosticCapability: ReadOnlyApiDiagnosticCapability by lazy {
        object : ReadOnlyApiDiagnosticCapability {
            override val isSupportedInCurrentBuild: Boolean
                get() = DiagnosticsEndpointConfig.resolveFromCompiledApi() is DiagnosticsEndpointResolution.Available &&
                    wenkuApiClient.isApiConfigured

            override suspend fun runBoundedReadOnlyApiSample(): Result<Long> {
                if (!isSupportedInCurrentBuild) {
                    return Result.failure(IllegalStateException("此版本不支持实际接口检测"))
                }
                val startMs = SystemClock.elapsedRealtime()
                return when (
                    val res = wenkuApiClient.fetchNovelListPage(
                        sortedBy = Wenku8API.NovelSortedBy.lastUpdate,
                        pageNumber = 1,
                        appLanguage = Wenku8API.AppLanguage.SC,
                    )
                ) {
                    is WenkuApiResult.Success -> Result.success((SystemClock.elapsedRealtime() - startMs).coerceAtLeast(0L))
                    WenkuApiResult.ApiNotConfigured -> Result.failure(IllegalStateException("此版本不支持实际接口检测"))
                    is WenkuApiResult.UpgradeRequired -> Result.failure(IllegalStateException("upgrade_required_426"))
                    WenkuApiResult.AuthRejected -> Result.failure(IllegalStateException("auth_rejected_403"))
                    is WenkuApiResult.NetworkError -> Result.failure(IllegalStateException(res.debugDetail ?: "network_error"))
                    is WenkuApiResult.ServerErrorCode -> Result.failure(IllegalStateException("server_code_${res.numericCode}"))
                    WenkuApiResult.EmptyResponse -> Result.failure(IllegalStateException("empty_response"))
                    is WenkuApiResult.ParseError -> Result.failure(IllegalStateException("parse_error"))
                }
            }
        }
    }

    val networkDiagnosticsViewModelFactory: NetworkDiagnosticsViewModel.Factory by lazy {
        NetworkDiagnosticsViewModel.Factory(
            endpointResolutionProvider = { DiagnosticsEndpointConfig.resolveFromCompiledApi() },
            diagnosticsRunner = diagnosticsRunner,
            relayRouteController = relayRouteController,
            networkObserver = diagnosticsNetworkMonitor,
            readOnlyApiCapability = readOnlyApiDiagnosticCapability,
        )
    }

    val discoveryRepository: DiscoveryRepository by lazy {
        DiscoveryRepository(
            database = database,
            apiClient = wenkuApiClient,
        )
    }

    val novelTocSyncRepository: NovelTocSyncRepository by lazy {
        NovelTocSyncRepository(database)
    }

    val userSessionRepository: UserSessionRepository by lazy {
        UserSessionRepository(
            transport = wenkuTransport,
            assetRootsProvider = ::resolveLocalAssetRoots,
        )
    }

    val serverNoticeAndUpdateRepository: ServerNoticeAndUpdateRepository by lazy {
        ServerNoticeAndUpdateRepository(
            transport = wenkuTransport,
            assetRootsProvider = ::resolveLocalAssetRoots,
        )
    }

    val appUpdateCoordinator: AppUpdateCoordinator by lazy {
        ChannelAppUpdateFactory.create(
            context = appContext,
            serverNoticeAndUpdateRepository = serverNoticeAndUpdateRepository,
            userPreferencesRepository = userPreferencesRepository,
        )
    }

    val storageAndCacheRepository: org.mewx.wenku8.data.repository.StorageAndCacheRepository by lazy {
        org.mewx.wenku8.data.repository.StorageAndCacheRepository(
            database = database,
            cacheRootProvider = { appContext.cacheDir },
            assetRootsProvider = ::resolveLocalAssetRoots,
            appContext = appContext,
        )
    }

    val backupArchiveRepository: org.mewx.wenku8.data.repository.BackupArchiveRepository by lazy {
        org.mewx.wenku8.data.repository.BackupArchiveRepository(
            database = database,
            userPreferencesRepository = userPreferencesRepository,
            cacheRootProvider = { appContext.cacheDir },
            assetRootsProvider = ::resolveLocalAssetRoots,
        )
    }

    val reviewsRepository: org.mewx.wenku8.data.repository.ReviewsRepository by lazy {
        org.mewx.wenku8.data.repository.ReviewsRepository(
            database = database,
            catalogGateway = wenkuApiClient,
            userSessionRepository = userSessionRepository,
        )
    }

    val bookshelfRepository: org.mewx.wenku8.data.repository.BookshelfRepository by lazy {
        org.mewx.wenku8.data.repository.BookshelfRepository(
            database = database,
            catalogGateway = wenkuApiClient,
            novelTocSyncRepository = novelTocSyncRepository,
            userSessionRepository = userSessionRepository,
            assetRootsProvider = ::resolveLocalAssetRoots,
            cacheRootProvider = { appContext.cacheDir },
            appContext = appContext,
        )
    }

    val novelDetailRepository: org.mewx.wenku8.data.repository.NovelDetailRepository by lazy {
        org.mewx.wenku8.data.repository.NovelDetailRepository(
            database = database,
            catalogGateway = wenkuApiClient,
            novelTocSyncRepository = novelTocSyncRepository,
        )
    }

    val novelDownloadRepository: org.mewx.wenku8.data.download.NovelDownloadRepository by lazy {
        org.mewx.wenku8.data.download.NovelDownloadRepository(
            database = database,
            catalogGateway = wenkuApiClient,
            novelTocSyncRepository = novelTocSyncRepository,
            assetRootsProvider = ::resolveLocalAssetRoots,
            applicationScope = applicationScope,
            appContext = appContext,
        )
    }

    val legacyV1StorageMigrator: LegacyV1StorageMigrator by lazy {
        LegacyV1StorageMigrator(database, userPreferencesRepository)
    }

    private val _migrationProgressFlow = MutableStateFlow<LegacyMigrationProgress?>(null)
    val migrationProgressFlow: StateFlow<LegacyMigrationProgress?> = _migrationProgressFlow.asStateFlow()

    /**
     * Ordered local asset roots (`filesDir` first, optional legacy `/sdcard/wenku8` second)
     * searched by [OfflineFirstWenkuImageFetcher] and [InternalFileSaveSource].
     */
    fun resolveLocalAssetRoots(): List<File> {
        val legacyExternalDir = runCatching {
            File(Environment.getExternalStorageDirectory(), "wenku8")
        }.getOrNull()
        return listOfNotNull(
            appContext.filesDir,
            legacyExternalDir?.takeIf { it.isDirectory && it.canRead() },
        )
    }

    fun buildOfflineFirstImageLoader(): ImageLoader {
        return ImageLoader.Builder(appContext)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(appContext, 0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(
                        File(
                            appContext.cacheDir,
                            WenkuImageRequests.COIL_DISK_CACHE_DIR_NAME,
                        ).toOkioPath(),
                    )
                    .maxSizeBytes(WenkuImageRequests.COIL_DISK_CACHE_MAX_BYTES)
                    .build()
            }
            .components {
                add(WenkuCanonicalUriKeyer)
                add(
                    OfflineFirstWenkuImageFetcher.Factory(
                        assetSearchRootsProvider = ::resolveLocalAssetRoots,
                        cacheRootProvider = { appContext.cacheDir },
                    ),
                )
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { sharedDirectGetCallFactory },
                    ),
                )
            }
            .build()
    }

    /**
     * Observes [adConsentManager] continuously:
     * - Mirrors every `canRequestAds` transition into [ServerSupportBannerPreloader] so GDPR
     *   consent revocation immediately calls `BannerAdPreloader.destroyAll()`.
     * - Warms up the Next-Gen Google Mobile Ads SDK in the background at most once, strictly after
     *   `canRequestAds` becomes `true`.
     */
    fun triggerConsentGatedAdWarmUp() {
        if (!hasStartedConsentGatedAdWarmUp.compareAndSet(false, true)) return
        applicationScope.launch {
            var hasWarmedUpSdk = false
            adConsentManager.canRequestAds.collect { canRequest ->
                org.mewx.wenku8.ui.detail.ServerSupportBannerPreloader.onConsentStateChanged(canRequest)
                if (canRequest && !hasWarmedUpSdk) {
                    hasWarmedUpSdk = true
                    org.mewx.wenku8.ui.detail.ServerSupportAdInitializer.warmUpInBackground(
                        context = appContext,
                        scope = applicationScope,
                    )
                }
            }
        }
    }

    /**
     * Restores any saved `saves/cert.wk8` session and keeps `NoticeString` synchronized with the
     * user's active Simplified/Traditional Chinese language preference in the background.
     */
    fun triggerStartupAccountAndNoticeSync() {
        applicationScope.launch {
            userSessionRepository.initializeSessionOnStartup()
        }
        applicationScope.launch {
            userPreferencesRepository.preferencesFlow
                .map { it.appLanguage }
                .distinctUntilChanged()
                .collect { appLanguage ->
                    serverNoticeAndUpdateRepository.loadAndRefreshNotice(appLanguage)
                }
        }
    }

    /**
     * Keeps Crashlytics custom keys and GA4 non-PII user properties synchronized with the user's
     * active preferences and authentication state (boolean only — never account names).
     */
    fun triggerTelemetryStateSync() {
        applicationScope.launch {
            userPreferencesRepository.preferencesFlow
                .map { prefs ->
                    listOf(
                        prefs.appLanguage.name,
                        prefs.themeMode.name.lowercase(),
                        prefs.readerPageTurnMode.name.lowercase(),
                        prefs.readerColumnMode.name.lowercase(),
                        prefs.einkModeEnabled.toString(),
                        prefs.autoOptimizeConnectionEnabled.toString(),
                    )
                }
                .distinctUntilChanged()
                .collect { snapshot ->
                    val language = snapshot[0]
                    val themeMode = snapshot[1]
                    val readerMode = snapshot[2]
                    val readerColumns = snapshot[3]
                    val einkEnabled = snapshot[4].toBoolean()
                    val autoRouting = snapshot[5].toBoolean()

                    org.mewx.wenku8.util.CrashReporter.setKey(
                        org.mewx.wenku8.util.CrashReporter.Keys.LANGUAGE,
                        language,
                    )
                    org.mewx.wenku8.util.CrashReporter.setKey(
                        org.mewx.wenku8.util.CrashReporter.Keys.THEME_MODE,
                        themeMode,
                    )
                    org.mewx.wenku8.util.CrashReporter.setKey(
                        org.mewx.wenku8.util.CrashReporter.Keys.READER_MODE,
                        readerMode,
                    )
                    org.mewx.wenku8.util.CrashReporter.setKey(
                        org.mewx.wenku8.util.CrashReporter.Keys.READER_COLUMNS,
                        readerColumns,
                    )
                    org.mewx.wenku8.util.CrashReporter.setKey(
                        org.mewx.wenku8.util.CrashReporter.Keys.AUTO_ROUTING,
                        autoRouting,
                    )
                    org.mewx.wenku8.util.AnalyticsTracker.syncUserPreferenceProperties(
                        appLanguage = language,
                        themeMode = themeMode,
                        readerTurnMode = readerMode,
                        readerColumnMode = readerColumns,
                        einkModeEnabled = einkEnabled,
                        distributionChannel = org.mewx.wenku8.BuildConfig.DISTRIBUTION_CHANNEL,
                    )
                }
        }
        applicationScope.launch {
            userSessionRepository.sessionState
                .map { it is org.mewx.wenku8.data.repository.AccountSessionState.SignedIn }
                .distinctUntilChanged()
                .collect { loggedIn ->
                    org.mewx.wenku8.util.CrashReporter.setLoggedIn(loggedIn)
                }
        }
    }

    /**
     * Runs automatic non-blocking `v1.31 -> v2.0` internal-storage migration once on cold start
     * when upgrading an existing installation (`legacyV1InternalMigrationVersion < 2`).
     */
    fun triggerAutomaticInternalMigrationIfNeeded() {
        applicationScope.launch {
            val prefs = userPreferencesRepository.preferencesFlow.first()
            if (prefs.legacyV1InternalMigrationVersion >= UserPreferencesRepository.CURRENT_V1_MIGRATION_VERSION) {
                return@launch
            }
            val roots = resolveLocalAssetRoots()
            val primaryRoot = roots.firstOrNull() ?: return@launch
            val fallbackRoot = roots.getOrNull(1)
            val source = InternalFileSaveSource(
                primaryInternalRoot = primaryRoot,
                fallbackExternalRoot = fallbackRoot,
            )
            try {
                val report = legacyV1StorageMigrator.migrateFromSource(
                    source = source,
                    onProgress = { progress -> _migrationProgressFlow.value = progress },
                )
                val migratedAnyLegacyData = report.migratedSettingsCount > 0 ||
                    report.migratedBookshelfNovelsCount > 0 ||
                    report.migratedChaptersCount > 0 ||
                    report.migratedReadingProgressCount > 0 ||
                    report.restoredAccountCredential
                if (migratedAnyLegacyData) {
                    org.mewx.wenku8.util.AnalyticsTracker.logLegacyMigration(
                        source = "auto_internal",
                        status = "completed",
                        novelsCount = report.migratedBookshelfNovelsCount,
                        chaptersCount = report.migratedChaptersCount,
                        progressCount = report.migratedReadingProgressCount,
                        restoredAccount = report.restoredAccountCredential,
                    )
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (e: Throwable) {
                org.mewx.wenku8.util.CrashReporter.recordException(
                    "AppContainer.automaticInternalMigration",
                    e,
                )
                org.mewx.wenku8.util.AnalyticsTracker.logLegacyMigration(
                    source = "auto_internal",
                    status = "failed",
                )
            } finally {
                _migrationProgressFlow.value = null
            }
        }
    }
}
