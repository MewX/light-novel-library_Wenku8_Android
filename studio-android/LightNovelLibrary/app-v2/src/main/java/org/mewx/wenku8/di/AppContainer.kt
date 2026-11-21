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
import java.util.concurrent.TimeUnit
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

    val sharedBaseOkHttpClient: OkHttpClient by lazy {
        OkHttpProbeTransport.defaultSharedBaseOkHttpClient()
    }

    /**
     * Shared OkHttpClient for Coil 3 & Artbook image loading: reuses [sharedBaseOkHttpClient]'s
     * TCP/TLS connection pool and dispatcher while stamping immutable cache headers onto Wenku8
     * static covers and illustrations so DiskCache never re-requests cached assets.
     */
    val sharedImageOkHttpClient: OkHttpClient by lazy {
        sharedBaseOkHttpClient.newBuilder()
            .dns(Dns.SYSTEM)
            .connectTimeout(12L, TimeUnit.SECONDS)
            .readTimeout(25L, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addNetworkInterceptor(WenkuImmutableImageCacheInterceptor)
            .build()
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
    //   if a pinned candidate IP ever becomes unreachable. (Note: `sharedImageOkHttpClient` above
    //   serves `pic.wenku8.com` cover/illustration CDN assets and intentionally stays on `Dns.SYSTEM`.)
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
        )
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
                        callFactory = { sharedImageOkHttpClient },
                    ),
                )
            }
            .build()
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
                legacyV1StorageMigrator.migrateFromSource(
                    source = source,
                    onProgress = { progress -> _migrationProgressFlow.value = progress },
                )
            } finally {
                _migrationProgressFlow.value = null
            }
        }
    }
}
