package org.mewx.wenku8.data.remote.diagnostics

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import okhttp3.ConnectionPool
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.data.remote.transport.ActiveRelayRoute
import org.mewx.wenku8.data.remote.transport.PinnedRelayCandidate
import org.mewx.wenku8.data.remote.transport.RelayHttpTransport
import org.mewx.wenku8.data.remote.transport.RelayRouteProvider
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * UI-visible summary of a verified candidate Cloudflare relay IP recorded on the current network
 * (e.g., after running Quick Check or background candidate screening), ready to be activated via
 * the `"自动优化连接"` toggle or already active.
 *
 * Note: Raw [ipLiteral] is shown **only** on the local device UI and is never exported in shared
 * diagnostic reports (`DiagnosticsReport.toRedactedJsonString`).
 */
data class RecordedCandidateSummary(
    val candidateId: String,
    val ipLiteral: String,
    val medianLatencyMs: Long,
    val ingressColo: String?,
    val manifestRevision: String,
)

sealed interface AutoRoutingModeState {
    data object UnavailableStub : AutoRoutingModeState
    data object DisabledByUser : AutoRoutingModeState
    data object EnabledUsingDefault : AutoRoutingModeState
    data object ComparingConnections : AutoRoutingModeState
    data class OptimizedRouteActive(
        val candidateId: String,
        val manifestRevision: String,
        val medianLatencyMs: Long,
        val controlMedianLatencyMs: Long?,
        val reductionPercent: Int?,
        val confirmedSampleCount: Int,
        val promotedOnReliability: Boolean,
        val selectedIpLiteral: String = "",
        val ingressColo: String? = null,
    ) : AutoRoutingModeState
    data object RestoredDefaultAfterFallback : AutoRoutingModeState
}

/**
 * Process-owned `"自动优化连接"` route selection controller implementing [RelayRouteProvider].
 *
 * ### Routing Lifecycle & Safety Model
 * 1. **Relay-Only Scope & Default-First Safety**:
 *    Pinned candidate IPs apply **exclusively** to our Cloudflare relay service (`wenku8-relay.mewx.org`,
 *    `mewx-android.wenku8.app`). Direct Wenku8 website/image CDNs (`*.wenku8.com`, `pic.wenku8.com`,
 *    `*.wenku8.net`, `*.wenku8.cn`, `wenku8.mewx.org`) always use Default System DNS.
 * 2. **Automatic Good-IP Recording & Toggle-to-Use ([discoverAndRecordPreferredCandidate])**:
 *    - Running manual Quick Check automatically screens & verifies a healthy Cloudflare candidate IP
 *      (including warm HTTP/2 reuse) and records it in [recordedCandidateSummary] for the current network.
 *    - Turning on `"自动优化连接"` immediately activates that recorded IP on the relay service (or
 *      automatically selects a working candidate IP in the background if none was recorded yet).
 * 3. **Compact Idle Tournament ([runCompactTournament]) & Bootstrap Recovery ([runBootstrapRecovery])**:
 *    - Resolves fresh candidate subnets via [ManifestRepository] (`_network_cfg.wenku8.app` DoH TXT
 *      -> local cache -> bundled Cloudflare Anycast subnets) and samples random `.1..254` host IPs.
 *    - Confirms HTTP/2 socket reuse (`connectionReused == true`) on a dedicated [ConnectionPool]
 *      before handing that warm pool directly to [RelayHttpTransport].
 * 4. **Foreground Priority & Automatic DNS Fallback / Re-selection**:
 *    - Pauses background probing whenever a foreground call or manual diagnostics run is active.
 *    - Enforces rolling 24h probe budgets via [RoutingLocalStore] (`<= 120` total, `<= 60` metered).
 *    - Revokes active leases and recorded IPs immediately on Wi-Fi/Cellular/VPN network transitions,
 *      lease expiry (`<= 6h`), remote `en=0` kill-switch, or user toggling Off.
 *    - If a pinned candidate IP fails in [RelayHttpTransport], [fallbackRouteAfterCandidateFailure]
 *      quarantines that candidate for `30 minutes`, immediately falls back to Default System DNS,
 *      and (when [allowAutoSelectOnEnable] is enabled) selects a replacement IP in the background.
 */
class RelayRouteController(
    private val applicationScope: CoroutineScope,
    private val endpointResolutionProvider: () -> DiagnosticsEndpointResolution,
    private val probeTransport: DiagnosticsProbeTransport,
    private val manifestRepository: ManifestRepository,
    private val routingLocalStore: RoutingLocalStore,
    private val networkObserver: NetworkConnectivityObserver,
    private val stalledDnsGuard: StalledDnsGuard = StalledDnsGuard(),
    private val appVersionCode: Int = BuildConfig.VERSION_CODE,
    private val autoProbeGapMillis: Long = DEFAULT_AUTO_PROBE_GAP_MS,
    private val initialAutoOptimizeEnabled: Boolean = false,
    private val allowPassiveBackgroundProbing: Boolean = false,
    private val allowBootstrapRecoveryOnDefaultFailure: Boolean = false,
    private val allowAutoSelectOnEnable: Boolean = false,
    private val monotonicClockMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClockEpochMillis: () -> Long = { System.currentTimeMillis() },
) : RelayRouteProvider {

    companion object {
        const val MAX_TOURNAMENT_PROBES: Int = 18
        const val MAX_TOURNAMENT_DURATION_MS: Long = 25_000L
        const val DEFAULT_AUTO_PROBE_GAP_MS: Long = 500L
        const val MAX_LEASE_DURATION_MS: Long = 6L * 60L * 60L * 1_000L
        const val PROVISIONAL_RECOVERY_LEASE_MS: Long = 10L * 60L * 1_000L
        const val CANDIDATE_QUARANTINE_DURATION_MS: Long = 30L * 60L * 1_000L
        const val MIN_TOURNAMENT_COOLDOWN_MS: Long = 30L * 60L * 1_000L
        const val DIAGNOSTICS_DISABLED_COOLDOWN_MS: Long = 60L * 60L * 1_000L
        const val MAX_RECOVERY_CALLS: Int = 6
        const val MAX_RECOVERY_DURATION_MS: Long = 20_000L
        const val MAX_DISCOVERY_SCREEN_CANDIDATES: Int = 4
        const val MAX_DISCOVERY_DURATION_MS: Long = 20_000L
        const val MIN_IMPROVEMENT_ABSOLUTE_MS: Long = 30L
    }

    private data class LeaseRecord(
        val generationId: Long,
        val candidate: CandidateIpEntry,
        val manifestRevision: String,
        val warmPool: ConnectionPool?,
        val leaseExpiresAtMonotonicMs: Long,
        val baselineSetupMs: Long?,
    )

    private data class RecordedCandidateRecord(
        val candidate: CandidateIpEntry,
        val manifestRevision: String,
        val medianLatencyMs: Long,
        val ingressColo: String?,
        val confirmedSampleCount: Int,
        val warmPool: ConnectionPool?,
        val expiresAtMonotonicMs: Long,
        val baselineSetupMs: Long?,
        val networkSnapshot: NetworkSnapshot,
    ) {
        fun toSummary(): RecordedCandidateSummary = RecordedCandidateSummary(
            candidateId = candidate.id,
            ipLiteral = candidate.ipLiteral,
            medianLatencyMs = medianLatencyMs,
            ingressColo = ingressColo,
            manifestRevision = manifestRevision,
        )
    }

    private val routeStateLock = Any()
    private val controllerMutex = Mutex()
    private val generationCounter = AtomicLong(1L)
    private val foregroundInFlightCount = AtomicInteger(0)
    private val manualDiagnosticsActiveCount = AtomicInteger(0)
    private val rotationCursor = AtomicInteger(0)

    @Volatile private var userAutoOptimizeEnabled: Boolean = initialAutoOptimizeEnabled
    @Volatile private var activeLease: LeaseRecord? = null
    @Volatile private var recordedCandidateRecord: RecordedCandidateRecord? = null
    @Volatile private var lastObservedNetwork: NetworkSnapshot? = null
    @Volatile private var lastFullTournamentAtMonotonicMs: Long? = null
    @Volatile private var diagnosticsPausedUntilMonotonicMs: Long? = null
    @Volatile private var activeBackgroundJob: Job? = null

    private val quarantinedUntilMonotonicById = ConcurrentHashMap<String, Long>()
    private val recentPassiveSetupTimesMs = ArrayDeque<Long>(5)

    private val _uiState = MutableStateFlow<AutoRoutingModeState>(computeInitialUiState())
    val uiState: StateFlow<AutoRoutingModeState> = _uiState.asStateFlow()

    private val _recordedCandidateSummary = MutableStateFlow<RecordedCandidateSummary?>(null)
    val recordedCandidateSummary: StateFlow<RecordedCandidateSummary?> = _recordedCandidateSummary.asStateFlow()

    init {
        // Observe live network transitions to revoke stale route leases immediately.
        applicationScope.launch {
            networkObserver.observeDefaultNetworkSnapshots().collect { snapshot ->
                val previous = lastObservedNetwork
                lastObservedNetwork = snapshot
                if (previous != null && snapshot.hasTransitionedFrom(previous)) {
                    revokeActiveOverrideOnNetworkTransition()
                }
            }
        }
    }

    fun setAutoOptimizeEnabled(enabled: Boolean) {
        updateAutoOptimizeEnabled(enabled = enabled, triggeredByUser = false)
    }

    /**
     * Explicit user toggle handler for `"自动优化连接"`.
     *
     * - When turned **ON**: immediately activates any fresh, non-quarantined [recordedCandidateSummary]
     *   on the current network; if none is recorded yet, automatically selects a working candidate IP
     *   in the background.
     * - When turned **OFF**: immediately stops routing via the pinned IP and reverts to System DNS,
     *   while retaining the recorded candidate summary on the current network so the user can toggle
     *   it back on at any time.
     */
    fun onUserToggledAutoOptimize(enabled: Boolean) {
        updateAutoOptimizeEnabled(enabled = enabled, triggeredByUser = true)
    }

    private fun updateAutoOptimizeEnabled(enabled: Boolean, triggeredByUser: Boolean) {
        if (!enabled) {
            userAutoOptimizeEnabled = false
            activeBackgroundJob?.cancel()
            synchronized(routeStateLock) {
                if (activeLease != null) {
                    activeLease = null
                    generationCounter.incrementAndGet()
                }
                pruneRecordedCandidateLocked(monotonicClockMillis(), networkObserver.currentSnapshot())
                refreshUiStateLocked()
            }
            return
        }

        val wasEnabled = userAutoOptimizeEnabled
        userAutoOptimizeEnabled = true
        var activatedFromRecordOrExisting = false
        synchronized(routeStateLock) {
            val nowMono = monotonicClockMillis()
            val currentNet = networkObserver.currentSnapshot()
            val existing = activeLease
            if (existing != null && nowMono < existing.leaseExpiresAtMonotonicMs) {
                activatedFromRecordOrExisting = true
                return@synchronized
            }
            if (existing != null) {
                activeLease = null
                generationCounter.incrementAndGet()
            }

            val validRecord = pruneRecordedCandidateLocked(nowMono, currentNet)
            if (validRecord != null && endpointResolutionProvider() is DiagnosticsEndpointResolution.Available) {
                val handoffPool = validRecord.warmPool
                recordedCandidateRecord = validRecord.copy(warmPool = null)
                val nextGen = generationCounter.incrementAndGet()
                activeLease = LeaseRecord(
                    generationId = nextGen,
                    candidate = validRecord.candidate,
                    manifestRevision = validRecord.manifestRevision,
                    warmPool = handoffPool,
                    leaseExpiresAtMonotonicMs = validRecord.expiresAtMonotonicMs,
                    baselineSetupMs = validRecord.baselineSetupMs,
                )
                _uiState.value = AutoRoutingModeState.OptimizedRouteActive(
                    candidateId = validRecord.candidate.id,
                    manifestRevision = validRecord.manifestRevision,
                    medianLatencyMs = validRecord.medianLatencyMs,
                    controlMedianLatencyMs = null,
                    reductionPercent = null,
                    confirmedSampleCount = validRecord.confirmedSampleCount,
                    promotedOnReliability = true,
                    selectedIpLiteral = validRecord.candidate.ipLiteral,
                    ingressColo = validRecord.ingressColo,
                )
                activatedFromRecordOrExisting = true
            } else {
                refreshUiStateLocked()
            }
        }

        if (!activatedFromRecordOrExisting &&
            (triggeredByUser || (allowAutoSelectOnEnable && !wasEnabled)) &&
            endpointResolutionProvider() is DiagnosticsEndpointResolution.Available &&
            manualDiagnosticsActiveCount.get() == 0 &&
            activeBackgroundJob?.isActive != true
        ) {
            _uiState.value = AutoRoutingModeState.ComparingConnections
            activeBackgroundJob = applicationScope.launch {
                discoverAndRecordPreferredCandidate(
                    applyIfEnabled = true,
                    invokedFromManualCheck = false,
                    userInitiated = triggeredByUser,
                )
            }
        }
    }

    /**
     * Acquires mutual exclusion for a manual diagnostics run, cancelling any in-flight automatic
     * background probe immediately so manual and automatic probes never overlap.
     */
    fun onManualDiagnosticsStarted() {
        manualDiagnosticsActiveCount.incrementAndGet()
        activeBackgroundJob?.cancel()
    }

    fun onManualDiagnosticsFinished() {
        manualDiagnosticsActiveCount.updateAndGet { (it - 1).coerceAtLeast(0) }
        refreshUiState()
    }

    fun currentPinnedCandidateOrNull(): CandidateIpEntry? {
        return synchronized(routeStateLock) {
            val lease = activeLease ?: return@synchronized null
            if (monotonicClockMillis() >= lease.leaseExpiresAtMonotonicMs) {
                activeLease = null
                generationCounter.incrementAndGet()
                refreshUiStateLocked()
                null
            } else {
                lease.candidate
            }
        }
    }

    fun currentManifestRevisionOrNull(): String? = activeLease?.manifestRevision

    /**
     * Restores default system-DNS routing and clears recorded candidate overrides without consuming
     * relay quota unless [triggerTournamentNow] is explicitly requested.
     */
    fun resetToDefaultAndReevaluate(triggerTournamentNow: Boolean = false) {
        activeBackgroundJob?.cancel()
        quarantinedUntilMonotonicById.clear()
        var poolToEvict: ConnectionPool? = null
        synchronized(routeStateLock) {
            activeLease = null
            poolToEvict = recordedCandidateRecord?.warmPool
            recordedCandidateRecord = null
            _recordedCandidateSummary.value = null
            generationCounter.incrementAndGet()
            refreshUiStateLocked()
        }
        poolToEvict?.let { OkHttpProbeTransport.evictPoolSafely(it) }
        if (triggerTournamentNow) {
            activeBackgroundJob = applicationScope.launch {
                discoverAndRecordPreferredCandidate(
                    applyIfEnabled = userAutoOptimizeEnabled,
                    invokedFromManualCheck = false,
                    userInitiated = true,
                )
            }
        }
    }

    private fun isAllowedRelayHost(normalizedHost: String): Boolean {
        if (normalizedHost.isEmpty() || RelayHttpTransport.isDirectWenkuOriginOrCdnHost(normalizedHost)) {
            return false
        }
        if (RelayHttpTransport.defaultAllowedRelayHosts().contains(normalizedHost)) {
            return true
        }
        val configuredHost = (endpointResolutionProvider() as? DiagnosticsEndpointResolution.Available)
            ?.endpoint
            ?.relayHost
            ?.trim()
            ?.trimEnd('.')
            ?.lowercase(Locale.ROOT)
        return !configuredHost.isNullOrEmpty() &&
            !RelayHttpTransport.isDirectWenkuOriginOrCdnHost(configuredHost) &&
            normalizedHost == configuredHost
    }

    override fun currentRouteForHost(host: String): ActiveRelayRoute {
        val normalizedHost = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (!isAllowedRelayHost(normalizedHost)) {
            return ActiveRelayRoute(
                generationId = generationCounter.get(),
                pinnedCandidate = null,
            )
        }
        return synchronized(routeStateLock) {
            val nowMono = monotonicClockMillis()
            val lease = activeLease
            if (!userAutoOptimizeEnabled || lease == null) {
                return@synchronized ActiveRelayRoute(
                    generationId = generationCounter.get(),
                    pinnedCandidate = null,
                )
            }
            if (nowMono >= lease.leaseExpiresAtMonotonicMs) {
                activeLease = null
                val nextGen = generationCounter.incrementAndGet()
                refreshUiStateLocked()
                return@synchronized ActiveRelayRoute(generationId = nextGen, pinnedCandidate = null)
            }
            ActiveRelayRoute(
                generationId = lease.generationId,
                pinnedCandidate = PinnedRelayCandidate(
                    candidateId = lease.candidate.id,
                    relayHost = normalizedHost,
                    rawIpBytes = lease.candidate.rawIpBytes,
                    warmConnectionPool = lease.warmPool,
                ),
            )
        }
    }

    override fun fallbackRouteAfterCandidateFailure(
        failedGenerationId: Long,
        failedCandidateId: String,
        failureBeforeSend: Boolean,
    ): ActiveRelayRoute {
        val quarantineUntil = monotonicClockMillis() + CANDIDATE_QUARANTINE_DURATION_MS
        quarantinedUntilMonotonicById[failedCandidateId] = quarantineUntil

        var stalePoolToEvict: ConnectionPool? = null
        val fallbackRoute = synchronized(routeStateLock) {
            val current = activeLease
            val nextGen = if (current != null && current.generationId == failedGenerationId) {
                activeLease = null
                generationCounter.incrementAndGet()
            } else {
                generationCounter.get()
            }
            val recorded = recordedCandidateRecord
            if (recorded != null && recorded.candidate.id == failedCandidateId) {
                stalePoolToEvict = recorded.warmPool
                recordedCandidateRecord = null
                _recordedCandidateSummary.value = null
            }
            _uiState.value = if (userAutoOptimizeEnabled) {
                AutoRoutingModeState.RestoredDefaultAfterFallback
            } else {
                AutoRoutingModeState.DisabledByUser
            }
            ActiveRelayRoute(generationId = nextGen, pinnedCandidate = null)
        }
        stalePoolToEvict?.let { OkHttpProbeTransport.evictPoolSafely(it) }

        if (userAutoOptimizeEnabled &&
            allowAutoSelectOnEnable &&
            manualDiagnosticsActiveCount.get() == 0 &&
            activeBackgroundJob?.isActive != true
        ) {
            activeBackgroundJob = applicationScope.launch {
                discoverAndRecordPreferredCandidate(
                    applyIfEnabled = true,
                    invokedFromManualCheck = false,
                    userInitiated = false,
                )
            }
        }
        return fallbackRoute
    }

    override fun onPassiveCallCompleted(
        generationId: Long,
        candidateId: String?,
        succeeded: Boolean,
        connectionSetupMs: Long?,
    ) {
        if (isDiagnosticsPausedAt(monotonicClockMillis())) {
            return
        }
        if (!succeeded && candidateId == null && userAutoOptimizeEnabled) {
            if (!allowPassiveBackgroundProbing && !allowBootstrapRecoveryOnDefaultFailure) {
                return
            }
            // Default route failed -> coalesce bounded bootstrap recovery if idle
            if (activeBackgroundJob?.isActive != true && manualDiagnosticsActiveCount.get() == 0) {
                activeBackgroundJob = applicationScope.launch {
                    runBootstrapRecovery()
                }
            }
            return
        }

        if (!allowPassiveBackgroundProbing) {
            return
        }

        if (succeeded && connectionSetupMs != null) {
            val baseline = activeLease?.baselineSetupMs ?: return
            var degraded = false
            synchronized(recentPassiveSetupTimesMs) {
                if (recentPassiveSetupTimesMs.size >= 5) {
                    recentPassiveSetupTimesMs.removeFirst()
                }
                recentPassiveSetupTimesMs.addLast(connectionSetupMs)
                if (recentPassiveSetupTimesMs.size == 5) {
                    val slowCount = recentPassiveSetupTimesMs.count { sample ->
                        sample > baseline * 2L && sample > baseline + 200L
                    }
                    degraded = slowCount >= 3
                }
            }
            if (degraded && userAutoOptimizeEnabled && manualDiagnosticsActiveCount.get() == 0) {
                val lastRun = lastFullTournamentAtMonotonicMs
                if (lastRun == null || monotonicClockMillis() - lastRun >= MIN_TOURNAMENT_COOLDOWN_MS) {
                    activeBackgroundJob = applicationScope.launch {
                        runCompactTournament(manualRecheck = false)
                    }
                }
            }
        }
    }

    override fun onForegroundCallStarted() {
        foregroundInFlightCount.incrementAndGet()
    }

    override fun onForegroundCallFinished() {
        foregroundInFlightCount.updateAndGet { (it - 1).coerceAtLeast(0) }
    }

    /**
     * Runs the bounded Compact Idle Tournament (`<= 18` probes / `25s` deadline):
     * `Screen (<=5)` -> `Confirm (6 or 9)` -> `Win check (20% AND 30ms, or reliability)` ->
     * `Warm-reuse guard (1)`.
     *
     * Returns `true` if a candidate route won and was published.
     */
    suspend fun runCompactTournament(manualRecheck: Boolean = false): Boolean =
        controllerMutex.withLock {
            val resolution = endpointResolutionProvider()
            if (resolution !is DiagnosticsEndpointResolution.Available) {
                _uiState.value = AutoRoutingModeState.UnavailableStub
                return@withLock false
            }
            if (!userAutoOptimizeEnabled) {
                _uiState.value = AutoRoutingModeState.DisabledByUser
                return@withLock false
            }
            if (manualDiagnosticsActiveCount.get() > 0) {
                return@withLock false
            }

            val baselineNetwork = networkObserver.currentSnapshot()
            if (!baselineNetwork.isConnected) return@withLock false
            if (!manualRecheck && (baselineNetwork.isPowerSaveMode || baselineNetwork.isDataSaverRestricted)) {
                return@withLock false
            }
            if (manualRecheck) {
                diagnosticsPausedUntilMonotonicMs = null
            } else {
                val nowCheckMono = monotonicClockMillis()
                if (isDiagnosticsPausedAt(nowCheckMono)) {
                    return@withLock false
                }
                val lastRun = lastFullTournamentAtMonotonicMs
                if (lastRun != null && nowCheckMono - lastRun < MIN_TOURNAMENT_COOLDOWN_MS) {
                    return@withLock false
                }
            }

            val endpoint = resolution.endpoint
            val cycleStartedAtMono = monotonicClockMillis()
            var probesUsed = 0

            fun canContinueTournament(): Boolean {
                if (!userAutoOptimizeEnabled) return false
                if (manualDiagnosticsActiveCount.get() > 0) return false
                if (!manualRecheck && foregroundInFlightCount.get() > 0) return false
                if (probesUsed >= MAX_TOURNAMENT_PROBES) return false
                if (monotonicClockMillis() - cycleStartedAtMono >= MAX_TOURNAMENT_DURATION_MS) return false
                if (networkObserver.currentSnapshot().hasTransitionedFrom(baselineNetwork)) return false
                return true
            }

            val manifest = manifestRepository.refreshFromIndependentOrigins(
                expectedRelayHostname = endpoint.relayHost,
                forceRefresh = manualRecheck,
            ) ?: manifestRepository.loadCachedOrInMemoryManifest(endpoint.relayHost)
                ?: return@withLock false

            if (!manifest.enabled) {
                revokeActiveOverrideLocked()
                return@withLock false
            }

            val rolloutBucket = routingLocalStore.getOrCreateRolloutBucket()
            val nowWall = wallClockEpochMillis()
            val nowMono = monotonicClockMillis()
            if (!manifest.isAuthorizedForOrdinaryTraffic(nowWall, nowMono, appVersionCode, rolloutBucket)) {
                revokeActiveOverrideLocked()
                return@withLock false
            }

            val eligibleCandidates = selectBalancedCandidatesForScreening(
                allCandidates = manifest.sampleCandidates(),
                nowMono = nowMono,
                maxCount = 4,
            )
            if (eligibleCandidates.isEmpty()) {
                refreshUiState()
                return@withLock false
            }

            _uiState.value = AutoRoutingModeState.ComparingConnections
            lastFullTournamentAtMonotonicMs = nowMono
            val temporaryPools = mutableListOf<ConnectionPool>()

            suspend fun runBudgetedTournamentProbe(armConfig: ProbeArmConfig, index: Int): ProbeSampleResult? {
                if (!canContinueTournament()) return null
                if (!manualRecheck) {
                    val budgetOk = routingLocalStore.tryConsumeAutomaticProbeBudget(
                        nowEpochMs = wallClockEpochMillis(),
                        isMeteredOrUnknown = baselineNetwork.isMetered || baselineNetwork.underlyingTransports.isEmpty(),
                    )
                    if (!budgetOk) return null
                }
                probesUsed += 1

                val sample = stalledDnsGuard.semaphore.withPermit {
                    probeTransport.executeProbe(
                        endpoint = endpoint,
                        armConfig = armConfig,
                        sampleIndex = index,
                    )
                }
                if (sample.httpStatusCode == 503) {
                    // Server disabled /__diagnostics/latency: stop cycle immediately and pause automatic probes for 1h.
                    diagnosticsPausedUntilMonotonicMs = monotonicClockMillis() + DIAGNOSTICS_DISABLED_COOLDOWN_MS
                    return null
                }
                if (sample.errorCategory == ProbeErrorCategory.HTTP_RATE_LIMITED) {
                    // Respect HTTP 429 immediately rather than probing remaining candidates.
                    return null
                }
                if (sample.observedProxyType != Proxy.Type.DIRECT) {
                    // Proxy active: direct candidate selection is disallowed per spec!
                    return null
                }
                if (autoProbeGapMillis > 0L && canContinueTournament()) {
                    delay(autoProbeGapMillis)
                }
                return sample
            }

            try {
                // --- Stage 1: Screen (Default + up to 4 candidates = up to 5 probes) ---
                val controlScreenPool = ConnectionPool(1, 30, TimeUnit.SECONDS).also { temporaryPools.add(it) }
                val controlScreen = runBudgetedTournamentProbe(
                    armConfig = ProbeArmConfig(
                        armId = "default",
                        dnsPolicy = ProbeDnsPolicy.SystemDefault,
                        fastFallback = true,
                        connectionPool = controlScreenPool,
                    ),
                    index = 1,
                ) ?: run {
                    refreshUiState()
                    return@withLock false
                }

                data class ScreenedChallenger(
                    val candidate: CandidateIpEntry,
                    val sample: ProbeSampleResult,
                )

                val reachableChallengers = mutableListOf<ScreenedChallenger>()
                for ((idx, candidate) in eligibleCandidates.withIndex()) {
                    val pool = ConnectionPool(1, 30, TimeUnit.SECONDS).also { temporaryPools.add(it) }
                    val sample = runBudgetedTournamentProbe(
                        armConfig = ProbeArmConfig(
                            armId = "screen:${candidate.id}",
                            dnsPolicy = ProbeDnsPolicy.PinnedCandidate(
                                candidateId = candidate.id,
                                relayHostname = endpoint.relayHost,
                                rawIpBytes = candidate.rawIpBytes,
                            ),
                            fastFallback = false,
                            connectionPool = pool,
                        ),
                        index = idx + 2,
                    ) ?: break

                    if (sample.isAuthenticatedCandidateProbeSuccess()) {
                        reachableChallengers.add(ScreenedChallenger(candidate, sample))
                    }
                }

                val bestChallenger = reachableChallengers
                    .minByOrNull { it.sample.fullResponseElapsedMs }
                    ?.takeIf { challenger ->
                        !controlScreen.succeeded ||
                            challenger.sample.fullResponseElapsedMs < controlScreen.fullResponseElapsedMs
                    }

                if (bestChallenger == null) {
                    refreshUiState()
                    return@withLock false
                }

                // --- Stage 2: Counterbalanced Confirmation (C, D, D, C, C, D = up to 6 fresh-pool probes) ---
                val challengerConfirmSamples = mutableListOf<ProbeSampleResult>()
                val controlConfirmSamples = mutableListOf<ProbeSampleResult>()
                var lastChallengerConfirmationPool: ConnectionPool? = null

                val counterbalanceIsChallenger = listOf(true, false, false, true, true, false)
                for ((stepIdx, isChallengerTurn) in counterbalanceIsChallenger.withIndex()) {
                    if (isChallengerTurn) {
                        val cPool = ConnectionPool(2, 60, TimeUnit.SECONDS)
                        lastChallengerConfirmationPool?.let { temporaryPools.add(it) }
                        lastChallengerConfirmationPool = cPool

                        val cSample = runBudgetedTournamentProbe(
                            armConfig = ProbeArmConfig(
                                armId = "confirm:${bestChallenger.candidate.id}",
                                dnsPolicy = ProbeDnsPolicy.PinnedCandidate(
                                    candidateId = bestChallenger.candidate.id,
                                    relayHostname = endpoint.relayHost,
                                    rawIpBytes = bestChallenger.candidate.rawIpBytes,
                                ),
                                fastFallback = false,
                                connectionPool = cPool,
                            ),
                            index = 10 + stepIdx,
                        ) ?: run {
                            OkHttpProbeTransport.evictPoolSafely(cPool)
                            refreshUiState()
                            return@withLock false
                        }
                        challengerConfirmSamples.add(cSample)
                    } else {
                        // When Default DNS already failed in Stage 1 AND failed its first Stage 2 confirmation,
                        // `controlAllSucceeded` is guaranteed false; skip 2nd/3rd doomed default timeouts so a
                        // GFW-blocked default route cannot exhaust the 25s tournament deadline!
                        if (!controlScreen.succeeded && controlConfirmSamples.any { !it.succeeded }) {
                            continue
                        }
                        val dPool = ConnectionPool(1, 30, TimeUnit.SECONDS).also { temporaryPools.add(it) }
                        val dSample = runBudgetedTournamentProbe(
                            armConfig = ProbeArmConfig(
                                armId = "confirm:default",
                                dnsPolicy = ProbeDnsPolicy.SystemDefault,
                                fastFallback = true,
                                connectionPool = dPool,
                            ),
                            index = 10 + stepIdx,
                        ) ?: run {
                            lastChallengerConfirmationPool?.let { OkHttpProbeTransport.evictPoolSafely(it) }
                            refreshUiState()
                            return@withLock false
                        }
                        controlConfirmSamples.add(dSample)
                    }
                }

                // --- Stage 3: Win Threshold Evaluation ---
                if (challengerConfirmSamples.size != 3 ||
                    challengerConfirmSamples.any { !it.isAuthenticatedCandidateProbeSuccess() }
                ) {
                    lastChallengerConfirmationPool?.let { OkHttpProbeTransport.evictPoolSafely(it) }
                    refreshUiState()
                    return@withLock false
                }

                val challengerMedianMs = DiagnosticsSummaryStats.computeMedianSorted(
                    challengerConfirmSamples.map { it.fullResponseElapsedMs }.sorted(),
                )!!

                val controlAllSucceeded =
                    controlConfirmSamples.size == 3 && controlConfirmSamples.all { it.succeeded }
                val controlMedianMs = if (controlAllSucceeded) {
                    DiagnosticsSummaryStats.computeMedianSorted(
                        controlConfirmSamples.map { it.fullResponseElapsedMs }.sorted(),
                    )
                } else {
                    null
                }

                val promotedOnReliability = !controlAllSucceeded
                if (!promotedOnReliability) {
                    val ctrlMed = controlMedianMs!!
                    val absoluteImprovementMs = ctrlMed - challengerMedianMs
                    // Must improve by BOTH >= 20% AND >= 30 ms!
                    val beatsByTwentyPercent = challengerMedianMs * 5L <= ctrlMed * 4L
                    val beatsByThirtyMillis = absoluteImprovementMs >= MIN_IMPROVEMENT_ABSOLUTE_MS
                    if (!beatsByTwentyPercent || !beatsByThirtyMillis) {
                        lastChallengerConfirmationPool?.let { OkHttpProbeTransport.evictPoolSafely(it) }
                        refreshUiState()
                        return@withLock false
                    }
                }

                // --- Stage 4: Warm-Reuse Guard (1 immediate probe on lastChallengerConfirmationPool) ---
                val warmGuardPool = lastChallengerConfirmationPool ?: run {
                    refreshUiState()
                    return@withLock false
                }
                val warmGuardSample = runBudgetedTournamentProbe(
                    armConfig = ProbeArmConfig(
                        armId = "warm_guard:${bestChallenger.candidate.id}",
                        dnsPolicy = ProbeDnsPolicy.PinnedCandidate(
                            candidateId = bestChallenger.candidate.id,
                            relayHostname = endpoint.relayHost,
                            rawIpBytes = bestChallenger.candidate.rawIpBytes,
                        ),
                        fastFallback = false,
                        connectionPool = warmGuardPool,
                    ),
                    index = 20,
                )
                val warmLatencyAcceptable = if (promotedOnReliability || controlMedianMs == null) {
                    true
                } else {
                    // Allow normal upstream/Smart-Placement jitter (+50 ms over cold median) as long as
                    // the reused connection remains meaningfully faster than the Default route.
                    val jitterCeilingMs = challengerMedianMs + 50L
                    warmGuardSample != null &&
                        warmGuardSample.fullResponseElapsedMs <= jitterCeilingMs &&
                        warmGuardSample.fullResponseElapsedMs < controlMedianMs
                }
                if (warmGuardSample == null ||
                    !warmGuardSample.isAuthenticatedCandidateProbeSuccess() ||
                    !warmGuardSample.connectionReused ||
                    !warmLatencyAcceptable
                ) {
                    OkHttpProbeTransport.evictPoolSafely(warmGuardPool)
                    refreshUiState()
                    return@withLock false
                }

                // --- Stage 5: Publish Winning Pinned Route ---
                val publishNowMono = monotonicClockMillis()
                val publishNowWall = wallClockEpochMillis()
                val remainingManifestMs = manifest.remainingAuthorizationMillis(publishNowWall, publishNowMono)
                if (remainingManifestMs <= 0L) {
                    OkHttpProbeTransport.evictPoolSafely(warmGuardPool)
                    refreshUiState()
                    return@withLock false
                }

                val leaseDurationMs = minOf(MAX_LEASE_DURATION_MS, remainingManifestMs)
                val reductionPct = if (controlMedianMs != null && controlMedianMs > 0L) {
                    (((controlMedianMs - challengerMedianMs) * 100L) / controlMedianMs).toInt()
                } else {
                    null
                }
                val baselineSetupMs = challengerConfirmSamples.mapNotNull { samp ->
                    val tcp = samp.tcpConnectDurationMs ?: 0L
                    val tls = samp.tlsHandshakeDurationMs ?: 0L
                    (tcp + tls).takeIf { it > 0L }
                }.minOrNull()

                val winnerColo = warmGuardSample.serverPayload?.ingressColo
                    ?: bestChallenger.sample.serverPayload?.ingressColo
                var oldRecordedPool: ConnectionPool? = null
                synchronized(routeStateLock) {
                    oldRecordedPool = recordedCandidateRecord?.warmPool
                    val newRecord = RecordedCandidateRecord(
                        candidate = bestChallenger.candidate,
                        manifestRevision = manifest.revision,
                        medianLatencyMs = challengerMedianMs,
                        ingressColo = winnerColo,
                        confirmedSampleCount = challengerConfirmSamples.size,
                        warmPool = null,
                        expiresAtMonotonicMs = publishNowMono + leaseDurationMs,
                        baselineSetupMs = baselineSetupMs,
                        networkSnapshot = baselineNetwork,
                    )
                    recordedCandidateRecord = newRecord
                    _recordedCandidateSummary.value = newRecord.toSummary()

                    val newGen = generationCounter.incrementAndGet()
                    activeLease = LeaseRecord(
                        generationId = newGen,
                        candidate = bestChallenger.candidate,
                        manifestRevision = manifest.revision,
                        warmPool = warmGuardPool,
                        leaseExpiresAtMonotonicMs = publishNowMono + leaseDurationMs,
                        baselineSetupMs = baselineSetupMs,
                    )
                    _uiState.value = AutoRoutingModeState.OptimizedRouteActive(
                        candidateId = bestChallenger.candidate.id,
                        manifestRevision = manifest.revision,
                        medianLatencyMs = challengerMedianMs,
                        controlMedianLatencyMs = controlMedianMs,
                        reductionPercent = reductionPct,
                        confirmedSampleCount = challengerConfirmSamples.size,
                        promotedOnReliability = promotedOnReliability,
                        selectedIpLiteral = bestChallenger.candidate.ipLiteral,
                        ingressColo = winnerColo,
                    )
                }
                oldRecordedPool?.let { OkHttpProbeTransport.evictPoolSafely(it) }
                true
            } finally {
                temporaryPools.forEach { OkHttpProbeTransport.evictPoolSafely(it) }
            }
        }

    /**
     * Screens up to [MAX_DISCOVERY_SCREEN_CANDIDATES] authorized Cloudflare relay candidates,
     * verifies HTTP/2 warm socket reuse on the fastest healthy candidate, and records it in
     * [recordedCandidateSummary] for the current network.
     *
     * - When [applyIfEnabled] is `true` and [userAutoOptimizeEnabled] is currently ON, immediately
     *   activates the verified candidate as [activeLease] + [AutoRoutingModeState.OptimizedRouteActive].
     * - When [userAutoOptimizeEnabled] is OFF (e.g. after running manual Quick Check), stores the
     *   verified candidate so the UI can show its IP & latency near the toggle and activate it
     *   instantaneously as soon as the user flips the toggle ON.
     */
    suspend fun discoverAndRecordPreferredCandidate(
        applyIfEnabled: Boolean = true,
        invokedFromManualCheck: Boolean = false,
        userInitiated: Boolean = false,
    ): RecordedCandidateSummary? = controllerMutex.withLock {
        val resolution = endpointResolutionProvider()
        if (resolution !is DiagnosticsEndpointResolution.Available) {
            refreshUiState()
            return@withLock null
        }
        if (!invokedFromManualCheck && manualDiagnosticsActiveCount.get() > 0) {
            return@withLock null
        }
        if (!userInitiated && isDiagnosticsPausedAt(monotonicClockMillis())) {
            refreshUiState()
            return@withLock null
        }

        val baselineNetwork = networkObserver.currentSnapshot()
        if (!baselineNetwork.isConnected) {
            refreshUiState()
            return@withLock null
        }
        if (!userInitiated && (baselineNetwork.isPowerSaveMode || baselineNetwork.isDataSaverRestricted)) {
            refreshUiState()
            return@withLock null
        }

        val endpoint = resolution.endpoint
        val manifest = manifestRepository.refreshFromIndependentOrigins(
            expectedRelayHostname = endpoint.relayHost,
            forceRefresh = false,
        ) ?: manifestRepository.loadCachedOrInMemoryManifest(endpoint.relayHost)
            ?: run {
                refreshUiState()
                return@withLock null
            }

        if (!manifest.enabled) {
            revokeActiveOverrideLocked()
            return@withLock null
        }

        val rolloutBucket = routingLocalStore.getOrCreateRolloutBucket()
        val nowWall = wallClockEpochMillis()
        val nowMono = monotonicClockMillis()
        if (!manifest.isAuthorizedForOrdinaryTraffic(nowWall, nowMono, appVersionCode, rolloutBucket)) {
            revokeActiveOverrideLocked()
            return@withLock null
        }

        val eligibleCandidates = selectBalancedCandidatesForScreening(
            allCandidates = manifest.sampleCandidates(),
            nowMono = nowMono,
            maxCount = MAX_DISCOVERY_SCREEN_CANDIDATES,
        )
        if (eligibleCandidates.isEmpty()) {
            refreshUiState()
            return@withLock null
        }

        if (applyIfEnabled && userAutoOptimizeEnabled && activeLease == null) {
            _uiState.value = AutoRoutingModeState.ComparingConnections
        }

        val startedAtMono = monotonicClockMillis()
        val poolsToEvict = mutableListOf<ConnectionPool>()

        fun canContinueDiscovery(): Boolean {
            if (!invokedFromManualCheck && manualDiagnosticsActiveCount.get() > 0) return false
            if (monotonicClockMillis() - startedAtMono >= MAX_DISCOVERY_DURATION_MS) return false
            if (networkObserver.currentSnapshot().hasTransitionedFrom(baselineNetwork)) return false
            return true
        }

        suspend fun runDiscoveryProbe(armConfig: ProbeArmConfig, sampleIndex: Int): ProbeSampleResult? {
            while (!userInitiated && foregroundInFlightCount.get() > 0) {
                if (!canContinueDiscovery()) return null
                delay(50L)
            }
            if (!canContinueDiscovery()) return null
            if (!userInitiated) {
                val budgetOk = routingLocalStore.tryConsumeAutomaticProbeBudget(
                    nowEpochMs = wallClockEpochMillis(),
                    isMeteredOrUnknown = baselineNetwork.isMetered || baselineNetwork.underlyingTransports.isEmpty(),
                )
                if (!budgetOk) return null
            }
            val sample = stalledDnsGuard.semaphore.withPermit {
                probeTransport.executeProbe(
                    endpoint = endpoint,
                    armConfig = armConfig,
                    sampleIndex = sampleIndex,
                )
            }
            if (sample.httpStatusCode == 503) {
                diagnosticsPausedUntilMonotonicMs = monotonicClockMillis() + DIAGNOSTICS_DISABLED_COOLDOWN_MS
                return null
            }
            if (sample.errorCategory == ProbeErrorCategory.HTTP_RATE_LIMITED) {
                return null
            }
            if (sample.observedProxyType != Proxy.Type.DIRECT) {
                return null
            }
            if (autoProbeGapMillis > 0L && canContinueDiscovery()) {
                delay(autoProbeGapMillis)
            }
            return sample
        }

        data class ScreenedDiscoveryCandidate(
            val candidate: CandidateIpEntry,
            val sample: ProbeSampleResult,
            val pool: ConnectionPool,
        )

        var retainedWinnerPool: ConnectionPool? = null
        try {
            val screened = mutableListOf<ScreenedDiscoveryCandidate>()
            for ((idx, candidate) in eligibleCandidates.withIndex()) {
                val pool = ConnectionPool(2, 60, TimeUnit.SECONDS).also { poolsToEvict.add(it) }
                val sample = runDiscoveryProbe(
                    armConfig = ProbeArmConfig(
                        armId = "discover:${candidate.id}",
                        dnsPolicy = ProbeDnsPolicy.PinnedCandidate(
                            candidateId = candidate.id,
                            relayHostname = endpoint.relayHost,
                            rawIpBytes = candidate.rawIpBytes,
                        ),
                        fastFallback = false,
                        connectionPool = pool,
                    ),
                    sampleIndex = 100 + idx,
                ) ?: break

                if (sample.isAuthenticatedCandidateProbeSuccess()) {
                    screened.add(ScreenedDiscoveryCandidate(candidate, sample, pool))
                }
            }

            val best = screened.minByOrNull { it.sample.fullResponseElapsedMs } ?: run {
                refreshUiState()
                return@withLock null
            }

            // Verify warm HTTP/2 connection reuse on the winning candidate's pool before recording/promoting.
            val warmSample = runDiscoveryProbe(
                armConfig = ProbeArmConfig(
                    armId = "discover_warm:${best.candidate.id}",
                    dnsPolicy = ProbeDnsPolicy.PinnedCandidate(
                        candidateId = best.candidate.id,
                        relayHostname = endpoint.relayHost,
                        rawIpBytes = best.candidate.rawIpBytes,
                    ),
                    fastFallback = false,
                    connectionPool = best.pool,
                ),
                sampleIndex = 110,
            )
            if (warmSample == null ||
                !warmSample.isAuthenticatedCandidateProbeSuccess() ||
                !warmSample.connectionReused
            ) {
                refreshUiState()
                return@withLock null
            }

            val publishNowMono = monotonicClockMillis()
            val publishNowWall = wallClockEpochMillis()
            val remainingManifestMs = manifest.remainingAuthorizationMillis(publishNowWall, publishNowMono)
            if (remainingManifestMs <= 0L) {
                refreshUiState()
                return@withLock null
            }

            // Retain the verified warm pool; remove it from `poolsToEvict` so `finally` only cleans non-winners.
            poolsToEvict.remove(best.pool)
            retainedWinnerPool = best.pool

            val leaseDurationMs = minOf(MAX_LEASE_DURATION_MS, remainingManifestMs)
            val medianMs = DiagnosticsSummaryStats.computeMedianSorted(
                listOf(best.sample.fullResponseElapsedMs, warmSample.fullResponseElapsedMs).sorted(),
            ) ?: warmSample.fullResponseElapsedMs
            val ingressColo = warmSample.serverPayload?.ingressColo ?: best.sample.serverPayload?.ingressColo
            val baselineSetupMs = run {
                val tcp = best.sample.tcpConnectDurationMs ?: 0L
                val tls = best.sample.tlsHandshakeDurationMs ?: 0L
                (tcp + tls).takeIf { it > 0L }
            }

            var oldRecordedPool: ConnectionPool? = null
            val summary = synchronized(routeStateLock) {
                oldRecordedPool = recordedCandidateRecord?.warmPool
                val shouldActivateNow = applyIfEnabled && userAutoOptimizeEnabled
                val newRecord = RecordedCandidateRecord(
                    candidate = best.candidate,
                    manifestRevision = manifest.revision,
                    medianLatencyMs = medianMs,
                    ingressColo = ingressColo,
                    confirmedSampleCount = 2,
                    warmPool = if (shouldActivateNow) null else retainedWinnerPool,
                    expiresAtMonotonicMs = publishNowMono + leaseDurationMs,
                    baselineSetupMs = baselineSetupMs,
                    networkSnapshot = baselineNetwork,
                )
                recordedCandidateRecord = newRecord
                val recordedSummary = newRecord.toSummary()
                _recordedCandidateSummary.value = recordedSummary

                if (shouldActivateNow) {
                    val nextGen = generationCounter.incrementAndGet()
                    activeLease = LeaseRecord(
                        generationId = nextGen,
                        candidate = best.candidate,
                        manifestRevision = manifest.revision,
                        warmPool = retainedWinnerPool,
                        leaseExpiresAtMonotonicMs = publishNowMono + leaseDurationMs,
                        baselineSetupMs = baselineSetupMs,
                    )
                    _uiState.value = AutoRoutingModeState.OptimizedRouteActive(
                        candidateId = best.candidate.id,
                        manifestRevision = manifest.revision,
                        medianLatencyMs = medianMs,
                        controlMedianLatencyMs = null,
                        reductionPercent = null,
                        confirmedSampleCount = 2,
                        promotedOnReliability = true,
                        selectedIpLiteral = best.candidate.ipLiteral,
                        ingressColo = ingressColo,
                    )
                } else {
                    refreshUiStateLocked()
                }
                retainedWinnerPool = null
                recordedSummary
            }
            oldRecordedPool?.let { OkHttpProbeTransport.evictPoolSafely(it) }
            summary
        } finally {
            retainedWinnerPool?.let { OkHttpProbeTransport.evictPoolSafely(it) }
            poolsToEvict.forEach { OkHttpProbeTransport.evictPoolSafely(it) }
        }
    }

    /**
     * Bounded `<= 6`-call / `20s` bootstrap availability recovery granting a `10-minute`
     * provisional lease when the default route is unreachable and an authorized candidate responds.
     */
    suspend fun runBootstrapRecovery(): Boolean = controllerMutex.withLock {
        val resolution = endpointResolutionProvider()
        if (resolution !is DiagnosticsEndpointResolution.Available || !userAutoOptimizeEnabled) {
            return@withLock false
        }
        if (isDiagnosticsPausedAt(monotonicClockMillis())) {
            return@withLock false
        }
        val endpoint = resolution.endpoint
        val baselineNetwork = networkObserver.currentSnapshot()
        if (!baselineNetwork.isConnected) return@withLock false

        val manifest = manifestRepository.refreshFromIndependentOrigins(endpoint.relayHost)
            ?: manifestRepository.loadCachedOrInMemoryManifest(endpoint.relayHost)
            ?: return@withLock false

        val rolloutBucket = routingLocalStore.getOrCreateRolloutBucket()
        val nowWall = wallClockEpochMillis()
        val nowMono = monotonicClockMillis()
        if (!manifest.isAuthorizedForOrdinaryTraffic(nowWall, nowMono, appVersionCode, rolloutBucket)) {
            return@withLock false
        }

        val startedMono = monotonicClockMillis()
        var callsUsed = 0

        for (candidate in selectBalancedCandidatesForScreening(manifest.sampleCandidates(), nowMono, maxCount = MAX_RECOVERY_CALLS)) {
            if (callsUsed >= MAX_RECOVERY_CALLS) break
            if (monotonicClockMillis() - startedMono >= MAX_RECOVERY_DURATION_MS) break
            if (!routingLocalStore.tryConsumeAutomaticProbeBudget(
                    wallClockEpochMillis(),
                    baselineNetwork.isMetered || baselineNetwork.underlyingTransports.isEmpty(),
                )
            ) {
                break
            }
            callsUsed += 1

            val pool = ConnectionPool(2, 60, TimeUnit.SECONDS)
            val sample = stalledDnsGuard.semaphore.withPermit {
                probeTransport.executeProbe(
                    endpoint = endpoint,
                    armConfig = ProbeArmConfig(
                        armId = "recovery:${candidate.id}",
                        dnsPolicy = ProbeDnsPolicy.PinnedCandidate(
                            candidateId = candidate.id,
                            relayHostname = endpoint.relayHost,
                            rawIpBytes = candidate.rawIpBytes,
                        ),
                        fastFallback = false,
                        connectionPool = pool,
                    ),
                    sampleIndex = callsUsed,
                )
            }

            if (sample.httpStatusCode == 503) {
                diagnosticsPausedUntilMonotonicMs = monotonicClockMillis() + DIAGNOSTICS_DISABLED_COOLDOWN_MS
                OkHttpProbeTransport.evictPoolSafely(pool)
                break
            }
            if (sample.errorCategory == ProbeErrorCategory.HTTP_RATE_LIMITED) {
                OkHttpProbeTransport.evictPoolSafely(pool)
                break
            }

            if (sample.isAuthenticatedCandidateProbeSuccess() && sample.observedProxyType == Proxy.Type.DIRECT) {
                val provisionalMono = monotonicClockMillis()
                val maxAuthMs = manifest.remainingAuthorizationMillis(wallClockEpochMillis(), provisionalMono)
                val leaseMs = minOf(PROVISIONAL_RECOVERY_LEASE_MS, maxAuthMs)
                if (leaseMs > 0L) {
                    val ingressColo = sample.serverPayload?.ingressColo
                    var oldRecordedPool: ConnectionPool? = null
                    synchronized(routeStateLock) {
                        oldRecordedPool = recordedCandidateRecord?.warmPool
                        val newRecord = RecordedCandidateRecord(
                            candidate = candidate,
                            manifestRevision = manifest.revision,
                            medianLatencyMs = sample.fullResponseElapsedMs,
                            ingressColo = ingressColo,
                            confirmedSampleCount = 1,
                            warmPool = null,
                            expiresAtMonotonicMs = provisionalMono + leaseMs,
                            baselineSetupMs = null,
                            networkSnapshot = baselineNetwork,
                        )
                        recordedCandidateRecord = newRecord
                        _recordedCandidateSummary.value = newRecord.toSummary()

                        val nextGen = generationCounter.incrementAndGet()
                        activeLease = LeaseRecord(
                            generationId = nextGen,
                            candidate = candidate,
                            manifestRevision = manifest.revision,
                            warmPool = pool,
                            leaseExpiresAtMonotonicMs = provisionalMono + leaseMs,
                            baselineSetupMs = null,
                        )
                        _uiState.value = AutoRoutingModeState.OptimizedRouteActive(
                            candidateId = candidate.id,
                            manifestRevision = manifest.revision,
                            medianLatencyMs = sample.fullResponseElapsedMs,
                            controlMedianLatencyMs = null,
                            reductionPercent = null,
                            confirmedSampleCount = 1,
                            promotedOnReliability = true,
                            selectedIpLiteral = candidate.ipLiteral,
                            ingressColo = ingressColo,
                        )
                    }
                    oldRecordedPool?.let { OkHttpProbeTransport.evictPoolSafely(it) }
                    return@withLock true
                }
            }
            OkHttpProbeTransport.evictPoolSafely(pool)
        }
        false
    }

    private fun isDiagnosticsPausedAt(nowMono: Long): Boolean {
        val pausedUntil = diagnosticsPausedUntilMonotonicMs ?: return false
        return nowMono < pausedUntil
    }

    private fun ProbeSampleResult.isAuthenticatedCandidateProbeSuccess(): Boolean {
        return succeeded && serverPayload?.schema == OkHttpProbeTransport.PROBE_SCHEMA_V1
    }

    private fun selectBalancedCandidatesForScreening(
        allCandidates: List<CandidateIpEntry>,
        nowMono: Long,
        maxCount: Int,
    ): List<CandidateIpEntry> {
        val available = allCandidates.filter { entry ->
            val quarantinedUntil = quarantinedUntilMonotonicById[entry.id] ?: 0L
            nowMono >= quarantinedUntil
        }
        if (available.size <= maxCount) return available

        val offset = (rotationCursor.getAndIncrement() and Int.MAX_VALUE) % available.size
        val rotated = available.drop(offset) + available.take(offset)

        val v4Queue = ArrayDeque(rotated.filter { it.family == IpAddressFamily.IPV4 })
        val v6Queue = ArrayDeque(rotated.filter { it.family == IpAddressFamily.IPV6 })
        val picked = ArrayList<CandidateIpEntry>(maxCount)

        while (picked.size < maxCount && (v4Queue.isNotEmpty() || v6Queue.isNotEmpty())) {
            if (v4Queue.isNotEmpty() && picked.size < maxCount) picked.add(v4Queue.removeFirst())
            if (v6Queue.isNotEmpty() && picked.size < maxCount) picked.add(v6Queue.removeFirst())
        }
        return picked
    }

    private fun pruneRecordedCandidateLocked(
        nowMono: Long,
        currentNetwork: NetworkSnapshot,
    ): RecordedCandidateRecord? {
        val record = recordedCandidateRecord ?: return null
        val quarantinedUntil = quarantinedUntilMonotonicById[record.candidate.id] ?: 0L
        val isInvalid =
            !currentNetwork.isConnected ||
                nowMono >= record.expiresAtMonotonicMs ||
                nowMono < quarantinedUntil ||
                currentNetwork.hasTransitionedFrom(record.networkSnapshot)
        if (isInvalid) {
            record.warmPool?.let { OkHttpProbeTransport.evictPoolSafely(it) }
            recordedCandidateRecord = null
            _recordedCandidateSummary.value = null
            return null
        }
        return record
    }

    private fun revokeActiveOverrideOnNetworkTransition() {
        activeBackgroundJob?.cancel()
        quarantinedUntilMonotonicById.clear()
        synchronized(recentPassiveSetupTimesMs) { recentPassiveSetupTimesMs.clear() }
        var poolToEvict: ConnectionPool? = null
        synchronized(routeStateLock) {
            if (activeLease != null) {
                activeLease = null
                generationCounter.incrementAndGet()
            }
            poolToEvict = recordedCandidateRecord?.warmPool
            recordedCandidateRecord = null
            _recordedCandidateSummary.value = null
            refreshUiStateLocked()
        }
        poolToEvict?.let { OkHttpProbeTransport.evictPoolSafely(it) }

        val currentNet = networkObserver.currentSnapshot()
        if (userAutoOptimizeEnabled &&
            allowAutoSelectOnEnable &&
            currentNet.isConnected &&
            endpointResolutionProvider() is DiagnosticsEndpointResolution.Available &&
            manualDiagnosticsActiveCount.get() == 0
        ) {
            _uiState.value = AutoRoutingModeState.ComparingConnections
            activeBackgroundJob = applicationScope.launch {
                discoverAndRecordPreferredCandidate(
                    applyIfEnabled = true,
                    invokedFromManualCheck = false,
                    userInitiated = false,
                )
            }
        }
    }

    private fun revokeActiveOverrideLocked() {
        var poolToEvict: ConnectionPool? = null
        synchronized(routeStateLock) {
            if (activeLease != null) {
                activeLease = null
                generationCounter.incrementAndGet()
            }
            poolToEvict = recordedCandidateRecord?.warmPool
            recordedCandidateRecord = null
            _recordedCandidateSummary.value = null
            refreshUiStateLocked()
        }
        poolToEvict?.let { OkHttpProbeTransport.evictPoolSafely(it) }
    }

    private fun refreshUiState() {
        synchronized(routeStateLock) {
            refreshUiStateLocked()
        }
    }

    private fun refreshUiStateLocked() {
        val resolution = endpointResolutionProvider()
        if (resolution !is DiagnosticsEndpointResolution.Available) {
            _uiState.value = AutoRoutingModeState.UnavailableStub
            return
        }
        if (!userAutoOptimizeEnabled) {
            _uiState.value = AutoRoutingModeState.DisabledByUser
            return
        }
        if (activeLease == null) {
            _uiState.value = AutoRoutingModeState.EnabledUsingDefault
        }
    }

    private fun computeInitialUiState(): AutoRoutingModeState {
        return if (endpointResolutionProvider() !is DiagnosticsEndpointResolution.Available) {
            AutoRoutingModeState.UnavailableStub
        } else if (userAutoOptimizeEnabled) {
            AutoRoutingModeState.EnabledUsingDefault
        } else {
            AutoRoutingModeState.DisabledByUser
        }
    }
}
