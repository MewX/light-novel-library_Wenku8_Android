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
import org.mewx.wenku8.data.remote.transport.RelayRouteProvider
import java.net.Proxy
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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
    ) : AutoRoutingModeState
    data object RestoredDefaultAfterFallback : AutoRoutingModeState
}

/**
 * Process-owned `"自动优化连接"` controller (`network-connectivity-and-routing-design.md` §Candidate-IP selection).
 *
 * Coordinates:
 * - Compact Idle Tournament (`<= 18` probes / `25s` deadline, `500 ms` inter-probe gap, `5` screen +
 *   `6/9` counterbalanced confirm + `1` warm-reuse socket guard, `>= 20%` AND `>= 30 ms` win rule).
 * - Bounded Bootstrap Recovery (`<= 6` calls / `20s`, `10-minute` provisional lease).
 * - Immediate yield when foreground book/chapter requests arrive (`foregroundInFlightCount > 0`)
 *   and strict mutual exclusion with manual diagnostics runs.
 * - Automatic revocation on network/VPN transition, remote `enabled=false` manifest, candidate
 *   failure (`30 min` quarantine), lease expiry (`<= 6h`), or user toggling Off.
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
        const val MAX_RECOVERY_CALLS: Int = 6
        const val MAX_RECOVERY_DURATION_MS: Long = 20_000L
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

    private val routeStateLock = Any()
    private val controllerMutex = Mutex()
    private val generationCounter = AtomicLong(1L)
    private val foregroundInFlightCount = AtomicInteger(0)
    private val manualDiagnosticsActiveCount = AtomicInteger(0)
    private val rotationCursor = AtomicInteger(0)

    @Volatile private var userAutoOptimizeEnabled: Boolean = initialAutoOptimizeEnabled
    @Volatile private var activeLease: LeaseRecord? = null
    @Volatile private var lastObservedNetwork: NetworkSnapshot? = null
    @Volatile private var lastFullTournamentAtMonotonicMs: Long? = null
    @Volatile private var activeBackgroundJob: Job? = null

    private val quarantinedUntilMonotonicById = ConcurrentHashMap<String, Long>()
    private val recentPassiveSetupTimesMs = ArrayDeque<Long>(5)

    private val _uiState = MutableStateFlow<AutoRoutingModeState>(computeInitialUiState())
    val uiState: StateFlow<AutoRoutingModeState> = _uiState.asStateFlow()

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
        userAutoOptimizeEnabled = enabled
        if (!enabled) {
            activeBackgroundJob?.cancel()
            synchronized(routeStateLock) {
                if (activeLease != null) {
                    activeLease = null
                    generationCounter.incrementAndGet()
                }
            }
            refreshUiState()
        } else {
            refreshUiState()
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
     * Restores default system-DNS routing without consuming relay quota unless
     * [triggerTournamentNow] is explicitly requested.
     */
    fun resetToDefaultAndReevaluate(triggerTournamentNow: Boolean = false) {
        activeBackgroundJob?.cancel()
        quarantinedUntilMonotonicById.clear()
        synchronized(routeStateLock) {
            activeLease = null
            generationCounter.incrementAndGet()
            refreshUiStateLocked()
        }
        if (triggerTournamentNow && userAutoOptimizeEnabled) {
            activeBackgroundJob = applicationScope.launch {
                runCompactTournament(manualRecheck = true)
            }
        }
    }

    override fun currentRouteForHost(host: String): ActiveRelayRoute {
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
                    relayHost = host,
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

        return synchronized(routeStateLock) {
            val current = activeLease
            val nextGen = if (current != null && current.generationId == failedGenerationId) {
                activeLease = null
                generationCounter.incrementAndGet()
            } else {
                generationCounter.get()
            }
            _uiState.value = if (userAutoOptimizeEnabled) {
                AutoRoutingModeState.RestoredDefaultAfterFallback
            } else {
                AutoRoutingModeState.DisabledByUser
            }
            ActiveRelayRoute(generationId = nextGen, pinnedCandidate = null)
        }
    }

    override fun onPassiveCallCompleted(
        generationId: Long,
        candidateId: String?,
        succeeded: Boolean,
        connectionSetupMs: Long?,
    ) {
        if (!allowPassiveBackgroundProbing) {
            return
        }
        if (!succeeded && candidateId == null && userAutoOptimizeEnabled) {
            // Default route failed -> coalesce bounded bootstrap recovery if idle
            if (activeBackgroundJob?.isActive != true && manualDiagnosticsActiveCount.get() == 0) {
                activeBackgroundJob = applicationScope.launch {
                    runBootstrapRecovery()
                }
            }
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
            if (!manualRecheck) {
                val lastRun = lastFullTournamentAtMonotonicMs
                if (lastRun != null && monotonicClockMillis() - lastRun < MIN_TOURNAMENT_COOLDOWN_MS) {
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

            val manifest = manifestRepository.refreshFromIndependentOrigins(endpoint.relayHost)
                ?: manifestRepository.loadCachedOrInMemoryManifest(endpoint.relayHost)
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
                allCandidates = manifest.candidates,
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
                val budgetOk = routingLocalStore.tryConsumeAutomaticProbeBudget(
                    nowEpochMs = wallClockEpochMillis(),
                    isMeteredOrUnknown = baselineNetwork.isMetered || baselineNetwork.underlyingTransports.isEmpty(),
                )
                if (!budgetOk) return null
                probesUsed += 1

                val sample = stalledDnsGuard.semaphore.withPermit {
                    probeTransport.executeProbe(
                        endpoint = endpoint,
                        armConfig = armConfig,
                        sampleIndex = index,
                    )
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

                // --- Stage 2: Counterbalanced Confirmation (C, D, D, C, C, D = 6 fresh-pool probes) ---
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
                if (warmGuardSample == null ||
                    !warmGuardSample.isAuthenticatedCandidateProbeSuccess() ||
                    !warmGuardSample.connectionReused ||
                    warmGuardSample.fullResponseElapsedMs > challengerMedianMs
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

                synchronized(routeStateLock) {
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
                    )
                }
                true
            } finally {
                temporaryPools.forEach { OkHttpProbeTransport.evictPoolSafely(it) }
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

        for (candidate in selectBalancedCandidatesForScreening(manifest.candidates, nowMono, maxCount = MAX_RECOVERY_CALLS)) {
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

            if (sample.isAuthenticatedCandidateProbeSuccess() && sample.observedProxyType == Proxy.Type.DIRECT) {
                val provisionalMono = monotonicClockMillis()
                val maxAuthMs = manifest.remainingAuthorizationMillis(wallClockEpochMillis(), provisionalMono)
                val leaseMs = minOf(PROVISIONAL_RECOVERY_LEASE_MS, maxAuthMs)
                if (leaseMs > 0L) {
                    synchronized(routeStateLock) {
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
                        )
                    }
                    return@withLock true
                }
            }
            OkHttpProbeTransport.evictPoolSafely(pool)
        }
        false
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

    private fun revokeActiveOverrideOnNetworkTransition() {
        quarantinedUntilMonotonicById.clear()
        synchronized(recentPassiveSetupTimesMs) { recentPassiveSetupTimesMs.clear() }
        synchronized(routeStateLock) {
            if (activeLease != null) {
                activeLease = null
                generationCounter.incrementAndGet()
            }
            refreshUiStateLocked()
        }
    }

    private fun revokeActiveOverrideLocked() {
        synchronized(routeStateLock) {
            if (activeLease != null) {
                activeLease = null
                generationCounter.incrementAndGet()
            }
            refreshUiStateLocked()
        }
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
