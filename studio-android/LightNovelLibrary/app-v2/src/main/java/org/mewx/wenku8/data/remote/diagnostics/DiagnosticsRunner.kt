package org.mewx.wenku8.data.remote.diagnostics

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Protocol
import java.util.concurrent.TimeUnit

data class DiagnosticsLiveProgress(
    val profileName: String,
    val completedCount: Int,
    val totalPlannedCount: Int,
    val succeededCount: Int,
    val failedCount: Int,
    val elapsedMillis: Long,
    val completedSamples: List<ProbeSampleResult>,
)

enum class ComparisonAxis(val profileName: String) {
    /** `Default Happy Eyeballs (fastFallback=true)` vs `Sequential DNS (fastFallback=false)`. */
    HAPPY_EYEBALLS_VS_SEQUENTIAL("compare_happy_eyeballs_v1"),

    /** `IPv4 Only` vs `IPv6 Only` (missing AAAA reported as `address_family_unavailable`). */
    IPV4_VS_IPV6("compare_ipv4_vs_ipv6_v1"),

    /** Counterbalanced `A×3, B×3, B×3, A×3` burst comparing `HTTP/2+HTTP/1.1` vs `HTTP/1.1 Only`. */
    HTTP2_VS_HTTP1_1("compare_h2_vs_h1_v1"),
}

/**
 * Capability gate for the optional read-only API diagnostic check so `:api-stub` builds never
 * invoke stub methods or attempt JNI loading (`network-connectivity-and-routing-design.md` §Advanced comparisons).
 */
interface ReadOnlyApiDiagnosticCapability {
    val isSupportedInCurrentBuild: Boolean
    suspend fun runBoundedReadOnlyApiSample(): Result<Long>
}

object StubSafeReadOnlyApiCapability : ReadOnlyApiDiagnosticCapability {
    override val isSupportedInCurrentBuild: Boolean
        get() = DiagnosticsEndpointConfig.resolveFromCompiledApi() is DiagnosticsEndpointResolution.Available

    override suspend fun runBoundedReadOnlyApiSample(): Result<Long> {
        if (!isSupportedInCurrentBuild) {
            return Result.failure(IllegalStateException("此版本不支持实际接口检测"))
        }
        return Result.failure(UnsupportedOperationException("Use ReadOnlyApiDiagnosticCapability adapter"))
    }
}

/**
 * Process-wide 2-slot permit guard bounding concurrent DNS/probe execution so hung OS
 * `getaddrinfo` calls from cancelled runs cannot accumulate unboundedly.
 */
class StalledDnsGuard(maxConcurrentProbePermits: Int = 2) {
    val semaphore = Semaphore(maxConcurrentProbePermits)
}

/**
 * Deterministic, testable runner for Manual Quick Checks (`10` samples, `1s` gap, `60s` deadline)
 * and Single-Axis Comparison Labs (`<= 60` samples, `120s` deadline).
 */
class DiagnosticsRunner(
    private val probeTransport: DiagnosticsProbeTransport,
    private val networkObserver: NetworkConnectivityObserver,
    private val stalledDnsGuard: StalledDnsGuard = StalledDnsGuard(),
    private val monotonicClockMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val wallClockEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val DEFAULT_QUICK_CHECK_ATTEMPTS: Int = 10
        const val DEFAULT_QUICK_CHECK_GAP_MS: Long = 1_000L
        const val DEFAULT_QUICK_CHECK_DEADLINE_MS: Long = 60_000L

        const val MAX_COMPARISON_ATTEMPTS: Int = 60
        const val MAX_COMPARISON_DEADLINE_MS: Long = 120_000L
    }

    /**
     * Executes the standard 10-probe Quick Check (`#01` cold handshake + `#02..#10` reuse on one
     * isolated `ConnectionPool`).
     */
    suspend fun runQuickCheck(
        endpoint: ValidatedRelayEndpoint,
        activeCandidate: CandidateIpEntry? = null,
        manifestRevision: String? = null,
        plannedAttempts: Int = DEFAULT_QUICK_CHECK_ATTEMPTS,
        interSampleGapMillis: Long = DEFAULT_QUICK_CHECK_GAP_MS,
        overallDeadlineMillis: Long = DEFAULT_QUICK_CHECK_DEADLINE_MS,
        stopReasonOnCancellation: () -> DiagnosticsStopReason = { DiagnosticsStopReason.USER_CANCELLED },
        onProgress: (DiagnosticsLiveProgress) -> Unit = {},
    ): DiagnosticsReport = withContext(ioDispatcher) {
        val safePlanned = plannedAttempts.coerceIn(1, MAX_COMPARISON_ATTEMPTS)
        val (utcStart, tzOffset) = DiagnosticsReport.currentUtcAndOffset(wallClockEpochMillis())
        val baselineNetwork = networkObserver.currentSnapshot()

        if (!baselineNetwork.isConnected) {
            return@withContext DiagnosticsReport(
                utcStartedAtIso = utcStart,
                localTimezoneOffset = tzOffset,
                stopReason = DiagnosticsStopReason.OFFLINE,
                profileName = "quick_check_v1",
                baselineNetwork = baselineNetwork,
                networkTransitionInterrupted = false,
                targetHostname = endpoint.relayHost,
                endpointRole = endpoint.endpointRole,
                plannedSampleCount = safePlanned,
                manifestRevision = manifestRevision,
                activeCandidateId = activeCandidate?.id,
                samples = emptyList(),
            )
        }

        val sharedPool = ConnectionPool(5, 60, TimeUnit.SECONDS)
        val armConfig = if (activeCandidate == null) {
            ProbeArmConfig(
                armId = "default",
                dnsPolicy = ProbeDnsPolicy.SystemDefault,
                fastFallback = true,
                connectionPool = sharedPool,
            )
        } else {
            ProbeArmConfig(
                armId = "pinned:${activeCandidate.id}",
                dnsPolicy = ProbeDnsPolicy.PinnedCandidate(
                    candidateId = activeCandidate.id,
                    relayHostname = endpoint.relayHost,
                    rawIpBytes = activeCandidate.rawIpBytes,
                ),
                fastFallback = false,
                connectionPool = sharedPool,
            )
        }

        val armsSequence = List(safePlanned) { armConfig }
        try {
            executePlannedSequence(
                profileName = "quick_check_v1",
                utcStart = utcStart,
                tzOffset = tzOffset,
                endpoint = endpoint,
                baselineNetwork = baselineNetwork,
                manifestRevision = manifestRevision,
                activeCandidateId = activeCandidate?.id,
                plannedArms = armsSequence,
                interSampleGapMillis = interSampleGapMillis,
                overallDeadlineMillis = overallDeadlineMillis,
                stopReasonOnCancellation = stopReasonOnCancellation,
                onProgress = onProgress,
            )
        } finally {
            withContext(NonCancellable) {
                OkHttpProbeTransport.evictPoolSafely(sharedPool)
            }
        }
    }

    /**
     * Executes a bounded single-axis comparison (`<= 60` samples, `120s` deadline).
     */
    suspend fun runSingleAxisComparison(
        endpoint: ValidatedRelayEndpoint,
        axis: ComparisonAxis,
        interSampleGapMillis: Long = 500L,
        overallDeadlineMillis: Long = MAX_COMPARISON_DEADLINE_MS,
        stopReasonOnCancellation: () -> DiagnosticsStopReason = { DiagnosticsStopReason.USER_CANCELLED },
        onProgress: (DiagnosticsLiveProgress) -> Unit = {},
    ): DiagnosticsReport = withContext(ioDispatcher) {
        val (utcStart, tzOffset) = DiagnosticsReport.currentUtcAndOffset(wallClockEpochMillis())
        val baselineNetwork = networkObserver.currentSnapshot()
        if (!baselineNetwork.isConnected) {
            return@withContext DiagnosticsReport(
                utcStartedAtIso = utcStart,
                localTimezoneOffset = tzOffset,
                stopReason = DiagnosticsStopReason.OFFLINE,
                profileName = axis.profileName,
                baselineNetwork = baselineNetwork,
                networkTransitionInterrupted = false,
                targetHostname = endpoint.relayHost,
                endpointRole = endpoint.endpointRole,
                plannedSampleCount = 12,
                samples = emptyList(),
            )
        }

        val poolsToCleanup = mutableListOf<ConnectionPool>()
        fun freshOneShotPool(): ConnectionPool =
            ConnectionPool(1, 30, TimeUnit.SECONDS).also { poolsToCleanup.add(it) }

        val plannedArms: List<ProbeArmConfig> = when (axis) {
            ComparisonAxis.HAPPY_EYEBALLS_VS_SEQUENTIAL -> {
                // Counterbalanced fresh-connection pairs: A, B, B, A, A, B (6 samples)
                listOf(true, false, false, true, true, false).map { happyEyeballs ->
                    ProbeArmConfig(
                        armId = if (happyEyeballs) "happy_eyeballs" else "sequential_dns",
                        dnsPolicy = ProbeDnsPolicy.SystemDefault,
                        fastFallback = happyEyeballs,
                        connectionPool = freshOneShotPool(),
                    )
                }
            }

            ComparisonAxis.IPV4_VS_IPV6 -> {
                // Counterbalanced fresh-connection pairs: v4, v6, v6, v4, v4, v6 (6 samples)
                listOf(true, false, false, true, true, false).map { isIpv4 ->
                    ProbeArmConfig(
                        armId = if (isIpv4) "ipv4_only" else "ipv6_only",
                        dnsPolicy = if (isIpv4) ProbeDnsPolicy.Ipv4Only else ProbeDnsPolicy.Ipv6Only,
                        fastFallback = false,
                        connectionPool = freshOneShotPool(),
                    )
                }
            }

            ComparisonAxis.HTTP2_VS_HTTP1_1 -> {
                // Spec §4.2: Counterbalanced bursts A×3, B×3, B×3, A×3 (12 samples)
                val h2Burst1 = ConnectionPool(2, 60, TimeUnit.SECONDS).also { poolsToCleanup.add(it) }
                val h1Burst1 = ConnectionPool(2, 60, TimeUnit.SECONDS).also { poolsToCleanup.add(it) }
                val h1Burst2 = ConnectionPool(2, 60, TimeUnit.SECONDS).also { poolsToCleanup.add(it) }
                val h2Burst2 = ConnectionPool(2, 60, TimeUnit.SECONDS).also { poolsToCleanup.add(it) }

                buildList {
                    repeat(3) {
                        add(
                            ProbeArmConfig(
                                armId = "http2_preferred",
                                protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1),
                                connectionPool = h2Burst1,
                            ),
                        )
                    }
                    repeat(3) {
                        add(
                            ProbeArmConfig(
                                armId = "http1_1_only",
                                protocols = listOf(Protocol.HTTP_1_1),
                                connectionPool = h1Burst1,
                            ),
                        )
                    }
                    repeat(3) {
                        add(
                            ProbeArmConfig(
                                armId = "http1_1_only",
                                protocols = listOf(Protocol.HTTP_1_1),
                                connectionPool = h1Burst2,
                            ),
                        )
                    }
                    repeat(3) {
                        add(
                            ProbeArmConfig(
                                armId = "http2_preferred",
                                protocols = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1),
                                connectionPool = h2Burst2,
                            ),
                        )
                    }
                }
            }
        }

        try {
            executePlannedSequence(
                profileName = axis.profileName,
                utcStart = utcStart,
                tzOffset = tzOffset,
                endpoint = endpoint,
                baselineNetwork = baselineNetwork,
                manifestRevision = null,
                activeCandidateId = null,
                plannedArms = plannedArms.take(MAX_COMPARISON_ATTEMPTS),
                interSampleGapMillis = interSampleGapMillis,
                overallDeadlineMillis = overallDeadlineMillis.coerceAtMost(MAX_COMPARISON_DEADLINE_MS),
                stopReasonOnCancellation = stopReasonOnCancellation,
                onProgress = onProgress,
            )
        } finally {
            withContext(NonCancellable) {
                poolsToCleanup.forEach { OkHttpProbeTransport.evictPoolSafely(it) }
            }
        }
    }

    private suspend fun executePlannedSequence(
        profileName: String,
        utcStart: String,
        tzOffset: String,
        endpoint: ValidatedRelayEndpoint,
        baselineNetwork: NetworkSnapshot,
        manifestRevision: String?,
        activeCandidateId: String?,
        plannedArms: List<ProbeArmConfig>,
        interSampleGapMillis: Long,
        overallDeadlineMillis: Long,
        stopReasonOnCancellation: () -> DiagnosticsStopReason,
        onProgress: (DiagnosticsLiveProgress) -> Unit,
    ): DiagnosticsReport {
        val runStartedAtMs = monotonicClockMillis()
        val collectedSamples = ArrayList<ProbeSampleResult>(plannedArms.size)
        var stopReason = DiagnosticsStopReason.COMPLETED
        var transitionDetected = false

        fun publishProgress() {
            val succeeded = collectedSamples.count { it.succeeded }
            onProgress(
                DiagnosticsLiveProgress(
                    profileName = profileName,
                    completedCount = collectedSamples.size,
                    totalPlannedCount = plannedArms.size,
                    succeededCount = succeeded,
                    failedCount = collectedSamples.size - succeeded,
                    elapsedMillis = (monotonicClockMillis() - runStartedAtMs).coerceAtLeast(0L),
                    completedSamples = collectedSamples.toList(),
                ),
            )
        }

        publishProgress()

        try {
            for ((zeroIndex, arm) in plannedArms.withIndex()) {
                val elapsedBeforeSample = (monotonicClockMillis() - runStartedAtMs).coerceAtLeast(0L)
                val remainingDeadline = overallDeadlineMillis - elapsedBeforeSample
                if (remainingDeadline <= 0L) {
                    stopReason = DiagnosticsStopReason.DEADLINE_EXCEEDED
                    break
                }

                // Recheck baseline network before dispatching probe
                if (networkObserver.currentSnapshot().hasTransitionedFrom(baselineNetwork)) {
                    stopReason = DiagnosticsStopReason.NETWORK_TRANSITION
                    transitionDetected = true
                    break
                }

                val perCallBudget = minOf(arm.callTimeoutMillis, remainingDeadline)
                val boundedArm = arm.copy(callTimeoutMillis = perCallBudget)

                val sampleOrTimeout = withTimeoutOrNull(remainingDeadline) {
                    stalledDnsGuard.semaphore.withPermit {
                        probeTransport.executeProbe(
                            endpoint = endpoint,
                            armConfig = boundedArm,
                            sampleIndex = zeroIndex + 1,
                        )
                    }
                }

                if (sampleOrTimeout == null) {
                    stopReason = DiagnosticsStopReason.DEADLINE_EXCEEDED
                    break
                }

                // Recheck baseline network immediately after probe completes
                if (networkObserver.currentSnapshot().hasTransitionedFrom(baselineNetwork)) {
                    stopReason = DiagnosticsStopReason.NETWORK_TRANSITION
                    transitionDetected = true
                    break
                }

                collectedSamples.add(sampleOrTimeout)
                publishProgress()

                // Never retry around HTTP 429 rate limiting or HTTP 503 server-side disable!
                if (sampleOrTimeout.errorCategory == ProbeErrorCategory.HTTP_RATE_LIMITED) {
                    stopReason = DiagnosticsStopReason.RATE_LIMITED
                    break
                }
                if (sampleOrTimeout.httpStatusCode == 503) {
                    stopReason = DiagnosticsStopReason.DIAGNOSTICS_DISABLED
                    break
                }

                val isLastSample = zeroIndex == plannedArms.lastIndex
                if (!isLastSample && interSampleGapMillis > 0L) {
                    val remainingAfterSample =
                        overallDeadlineMillis - (monotonicClockMillis() - runStartedAtMs).coerceAtLeast(0L)
                    if (remainingAfterSample <= 0L) {
                        stopReason = DiagnosticsStopReason.DEADLINE_EXCEEDED
                        break
                    }
                    delay(minOf(interSampleGapMillis, remainingAfterSample))
                }
            }
        } catch (_: CancellationException) {
            // Preserve all already-completed samples when user taps Stop or backgrounds the app!
            stopReason = stopReasonOnCancellation()
        }

        return DiagnosticsReport(
            utcStartedAtIso = utcStart,
            localTimezoneOffset = tzOffset,
            stopReason = stopReason,
            profileName = profileName,
            baselineNetwork = baselineNetwork,
            networkTransitionInterrupted = transitionDetected,
            targetHostname = endpoint.relayHost,
            endpointRole = endpoint.endpointRole,
            plannedSampleCount = plannedArms.size,
            manifestRevision = manifestRevision,
            activeCandidateId = activeCandidateId,
            samples = collectedSamples.toList(),
        )
    }
}
