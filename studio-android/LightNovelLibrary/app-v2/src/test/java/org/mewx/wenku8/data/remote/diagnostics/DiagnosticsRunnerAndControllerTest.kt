package org.mewx.wenku8.data.remote.diagnostics

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.Proxy
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiagnosticsRunnerAndControllerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var routingLocalStore: RoutingLocalStore

    private val testEndpoint = (
        DiagnosticsEndpointConfig.parseAndValidate("https://wenku8-relay.mewx.org/")
            as DiagnosticsEndpointResolution.Available
        ).endpoint

    @Before
    fun setUp() {
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val dsFile = File(tempFolder.newFolder("datastore"), "routing_test.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = storeScope,
            produceFile = { dsFile },
        )
        routingLocalStore = RoutingLocalStore(dataStore)
    }

    @After
    fun tearDown() {
        storeScope.cancel()
    }

    @Test
    fun manifestRepositoryValidatesSchemaRejectsHostMismatchAndPreventsRollbackAfterRevocation() = runBlocking {
        val nowWall = 1_762_732_800_000L // 2025-11-10T00:00:00Z
        val nowMono = 10_000L

        val repo = ManifestRepository(
            localStore = routingLocalStore,
            wallClockEpochMillis = { nowWall },
            monotonicClockMillis = { nowMono },
        )

        val validV1Json = """
            {
              "schema": "wenku8-ip-candidates-v1",
              "revision": "rev-1",
              "hostname": "wenku8-relay.mewx.org",
              "enabled": true,
              "minAppVersionCode": 0,
              "rolloutPermille": 1000,
              "fallbackEndpointIds": ["unknown-gw"],
              "issuedAt": "2025-11-10T00:00:00Z",
              "expiresAt": "2025-11-10T12:00:00Z",
              "candidates": [
                { "id": "cf-v4-01", "ip": "104.16.132.229" },
                { "id": "cf-v6-01", "ip": "2606:4700::6810:84e5" }
              ]
            }
        """.trimIndent()

        // Hostname mismatch rejected
        assertNull(repo.ingestManifestPayload(validV1Json, expectedRelayHostname = "evil.example.com"))

        // Valid ingestion succeeds and strips unknown fallbackEndpointIds
        val accepted = repo.ingestManifestPayload(validV1Json, expectedRelayHostname = "wenku8-relay.mewx.org")
        assertNotNull(accepted)
        assertEquals(2, accepted!!.candidates.size)
        assertTrue(accepted.fallbackEndpointIds.isEmpty())

        // Newer revocation (`enabled = false`) disables routing
        val revokeV2Json = """
            {
              "schema": "wenku8-ip-candidates-v1",
              "revision": "rev-2-killswitch",
              "hostname": "wenku8-relay.mewx.org",
              "enabled": false,
              "minAppVersionCode": 0,
              "rolloutPermille": 1000,
              "issuedAt": "2025-11-10T00:03:00Z",
              "expiresAt": "2025-11-10T12:00:00Z",
              "candidates": []
            }
        """.trimIndent()
        val revoked = repo.ingestManifestPayload(revokeV2Json, expectedRelayHostname = "wenku8-relay.mewx.org")
        assertNotNull(revoked)
        assertFalse(revoked!!.enabled)

        // Lagging mirror serving older `rev-1` cannot undo `rev-2-killswitch`!
        val afterStaleAttempt = repo.ingestManifestPayload(validV1Json, expectedRelayHostname = "wenku8-relay.mewx.org")
        assertFalse(afterStaleAttempt!!.enabled)
        assertEquals("rev-2-killswitch", afterStaleAttempt.revision)
    }

    @Test
    fun routingLocalStoreEnforcesRolling24hMetered60AndTotal120Budgets() = runBlocking {
        val baseEpoch = 1_762_732_800_000L

        // Consume all 60 metered probe slots
        repeat(60) { i ->
            assertTrue(
                routingLocalStore.tryConsumeAutomaticProbeBudget(
                    nowEpochMs = baseEpoch + i * 1_000L,
                    isMeteredOrUnknown = true,
                ),
            )
        }
        // 61st metered probe within 24h is rejected
        assertFalse(
            routingLocalStore.tryConsumeAutomaticProbeBudget(
                nowEpochMs = baseEpoch + 61_000L,
                isMeteredOrUnknown = true,
            ),
        )

        // Unmetered Wi-Fi still has remaining 60 slots up to the 120 total cap
        repeat(60) { i ->
            assertTrue(
                routingLocalStore.tryConsumeAutomaticProbeBudget(
                    nowEpochMs = baseEpoch + 100_000L + i * 1_000L,
                    isMeteredOrUnknown = false,
                ),
            )
        }
        // 121st total probe within 24h is rejected even on unmetered Wi-Fi
        assertFalse(
            routingLocalStore.tryConsumeAutomaticProbeBudget(
                nowEpochMs = baseEpoch + 200_000L,
                isMeteredOrUnknown = false,
            ),
        )

        // Advance past rolling 24h window -> budget replenishes!
        assertTrue(
            routingLocalStore.tryConsumeAutomaticProbeBudget(
                nowEpochMs = baseEpoch + RoutingLocalStore.ROLLING_WINDOW_MILLIS + 5_000L,
                isMeteredOrUnknown = true,
            ),
        )
    }

    @Test
    fun diagnosticsRunnerStopsOnNetworkTransitionAndRateLimitAndExportsExplicitNullJson() = runBlocking {
        val networkFlow = MutableStateFlow(
            NetworkSnapshot(
                internalNetworkHandleKey = "wifi-100",
                isConnected = true,
                underlyingTransports = setOf(UnderlyingTransportKind.WIFI),
                isVpnActive = false,
                isMetered = false,
            ),
        )
        val fakeObserver = object : NetworkConnectivityObserver {
            override fun currentSnapshot(): NetworkSnapshot = networkFlow.value
            override fun observeDefaultNetworkSnapshots(): Flow<NetworkSnapshot> = networkFlow
        }

        // Scenario A: Wi-Fi -> Cellular switch after Sample #2 stops run cleanly with partial results
        val callCounter = AtomicInteger(0)
        val transitionProbeTransport = object : DiagnosticsProbeTransport {
            override suspend fun executeProbe(
                endpoint: ValidatedRelayEndpoint,
                armConfig: ProbeArmConfig,
                sampleIndex: Int,
                explicitNonce: String?,
            ): ProbeSampleResult {
                val n = callCounter.incrementAndGet()
                if (n == 3) {
                    // Simulate OS switching to Cellular during Probe #3
                    networkFlow.value = NetworkSnapshot(
                        internalNetworkHandleKey = "cell-200",
                        isConnected = true,
                        underlyingTransports = setOf(UnderlyingTransportKind.CELLULAR),
                        isMetered = true,
                    )
                }
                return syntheticProbeSample(
                    index = sampleIndex,
                    armId = armConfig.armId,
                    candidateId = null,
                    succeeded = true,
                    elapsedMs = 150L,
                    reused = sampleIndex > 1,
                )
            }
        }

        val runner = DiagnosticsRunner(
            probeTransport = transitionProbeTransport,
            networkObserver = fakeObserver,
        )
        val partialReport = runner.runQuickCheck(
            endpoint = testEndpoint,
            plannedAttempts = 10,
            interSampleGapMillis = 0L,
        )
        assertEquals(DiagnosticsStopReason.NETWORK_TRANSITION, partialReport.stopReason)
        assertTrue(partialReport.networkTransitionInterrupted)
        assertEquals("Only pre-transition samples must be retained", 2, partialReport.samples.size)

        // Scenario B: HTTP 429 on Sample #1 stops immediately with 0 successes (`"—"`) and explicit JSON nulls
        val rateLimitedTransport = object : DiagnosticsProbeTransport {
            var count = 0
            override suspend fun executeProbe(
                endpoint: ValidatedRelayEndpoint,
                armConfig: ProbeArmConfig,
                sampleIndex: Int,
                explicitNonce: String?,
            ): ProbeSampleResult {
                count += 1
                return ProbeSampleResult(
                    sampleIndex = sampleIndex,
                    armId = armConfig.armId,
                    candidateId = "cf-v4-01",
                    succeeded = false,
                    httpStatusCode = 429,
                    retryAfterSeconds = 30L,
                    errorCategory = ProbeErrorCategory.HTTP_RATE_LIMITED,
                    fullResponseElapsedMs = 95L,
                    responseHeadersStartMs = 90L,
                    dnsDurationMs = null,
                    tcpConnectDurationMs = null,
                    tlsHandshakeDurationMs = null,
                    connectionReused = false,
                    connectAttemptCount = 1,
                    observedProxyType = Proxy.Type.DIRECT,
                    localSocketFamily = IpAddressFamily.IPV4,
                    negotiatedProtocol = "h2",
                    responseBodyBytesRead = 12,
                    bodyComplete = true,
                    serverPayload = null,
                    observedEvents = listOf("callStart", "responseHeadersStart"),
                )
            }
        }

        val runner429 = DiagnosticsRunner(
            probeTransport = rateLimitedTransport,
            networkObserver = fakeObserver,
        )
        val report429 = runner429.runQuickCheck(
            endpoint = testEndpoint,
            plannedAttempts = 10,
            interSampleGapMillis = 0L,
        )
        assertEquals(1, rateLimitedTransport.count)
        assertEquals(DiagnosticsStopReason.RATE_LIMITED, report429.stopReason)
        assertNull("0-success run must have null median, never 0ms", report429.summary.medianFullResponseMs)
        assertEquals("—", DiagnosticsReport.formatNullableMillisForUi(report429.summary.medianFullResponseMs))

        val exportedJson = report429.toRedactedJsonString(optionalUserNote = null)
        val parsedExport = JSONObject(exportedJson)
        assertEquals("wenku8-network-report-v1", parsedExport.getString("schema"))
        assertTrue(parsedExport.has("userNote") && parsedExport.isNull("userNote"))
        val summaryJson = parsedExport.getJSONObject("summary")
        assertTrue(summaryJson.has("medianFullResponseMs") && summaryJson.isNull("medianFullResponseMs"))
        assertFalse("Internal network handle must never be exported", exportedJson.contains("cell-200"))
        assertFalse("Raw IP literal must never be exported", exportedJson.contains("104.16.132.229"))
    }

    @Test
    fun relayRouteControllerCompactTournamentEnforcesThresholdWarmGuardAndNetworkInvalidation() = runBlocking {
        val nowWall = 1_762_732_800_000L
        val monoClock = AtomicLong(20_000L)
        val networkFlow = MutableStateFlow(
            NetworkSnapshot(
                internalNetworkHandleKey = "wifi-home",
                isConnected = true,
                underlyingTransports = setOf(UnderlyingTransportKind.WIFI),
                isMetered = false,
            ),
        )
        val fakeObserver = object : NetworkConnectivityObserver {
            override fun currentSnapshot(): NetworkSnapshot = networkFlow.value
            override fun observeDefaultNetworkSnapshots(): Flow<NetworkSnapshot> = networkFlow
        }

        val manifestRepo = ManifestRepository(
            localStore = routingLocalStore,
            wallClockEpochMillis = { nowWall },
            monotonicClockMillis = { monoClock.get() },
        )
        manifestRepo.ingestManifestPayload(
            rawJson = """
                {
                  "schema": "wenku8-ip-candidates-v1",
                  "revision": "rev-fast",
                  "hostname": "wenku8-relay.mewx.org",
                  "enabled": true,
                  "minAppVersionCode": 0,
                  "rolloutPermille": 1000,
                  "issuedAt": "2025-11-10T00:00:00Z",
                  "expiresAt": "2025-11-10T12:00:00Z",
                  "candidates": [
                    { "id": "cf-v4-fast", "ip": "104.16.132.229" },
                    { "id": "cf-v6-slow", "ip": "2606:4700::6810:84e5" }
                  ]
                }
            """.trimIndent(),
            expectedRelayHostname = "wenku8-relay.mewx.org",
        )

        var warmReuseAllowed = true
        var defaultLatencyMs = 200L
        var fastCandidateLatencyMs = 90L
        var totalProbesSent = 0

        val tournamentTransport = object : DiagnosticsProbeTransport {
            override suspend fun executeProbe(
                endpoint: ValidatedRelayEndpoint,
                armConfig: ProbeArmConfig,
                sampleIndex: Int,
                explicitNonce: String?,
            ): ProbeSampleResult {
                totalProbesSent += 1
                val candidateId = (armConfig.dnsPolicy as? ProbeDnsPolicy.PinnedCandidate)?.candidateId
                val isWarmGuard = armConfig.armId.startsWith("warm_guard:")
                val latency = when (candidateId) {
                    "cf-v4-fast" -> fastCandidateLatencyMs
                    "cf-v6-slow" -> 260L
                    else -> defaultLatencyMs
                }
                return syntheticProbeSample(
                    index = sampleIndex,
                    armId = armConfig.armId,
                    candidateId = candidateId,
                    succeeded = true,
                    elapsedMs = latency,
                    reused = isWarmGuard && warmReuseAllowed,
                )
            }
        }

        val controller = RelayRouteController(
            applicationScope = storeScope,
            endpointResolutionProvider = { DiagnosticsEndpointResolution.Available(testEndpoint) },
            probeTransport = tournamentTransport,
            manifestRepository = manifestRepo,
            routingLocalStore = routingLocalStore,
            networkObserver = fakeObserver,
            autoProbeGapMillis = 0L,
            monotonicClockMillis = { monoClock.get() },
            wallClockEpochMillis = { nowWall },
        )

        // 1. Sub-threshold improvement (Default 120ms vs Challenger 98ms -> 22ms < 30ms): NOT promoted!
        defaultLatencyMs = 120L
        fastCandidateLatencyMs = 98L
        assertFalse(controller.runCompactTournament(manualRecheck = true))
        assertNull(controller.currentPinnedCandidateOrNull())
        assertTrue(controller.uiState.value is AutoRoutingModeState.EnabledUsingDefault)

        // 2. Broken warm-reuse guard (`connectionReused = false`): NOT promoted!
        defaultLatencyMs = 200L
        fastCandidateLatencyMs = 90L
        warmReuseAllowed = false
        assertFalse(controller.runCompactTournament(manualRecheck = true))
        assertNull(controller.currentPinnedCandidateOrNull())

        // 3. Valid win (Default 200ms vs Challenger 90ms = 55% & 110ms improvement + warm socket reused): PROMOTED!
        totalProbesSent = 0
        warmReuseAllowed = true
        assertTrue(controller.runCompactTournament(manualRecheck = true))
        assertTrue("Tournament must stay well within <=18 probe cap", totalProbesSent <= 18)
        assertEquals("cf-v4-fast", controller.currentPinnedCandidateOrNull()?.id)
        val activeState = controller.uiState.value as AutoRoutingModeState.OptimizedRouteActive
        assertEquals(55, activeState.reductionPercent)

        val pinnedGen = controller.currentRouteForHost("wenku8-relay.mewx.org").generationId

        // 4. Network transition (Wi-Fi -> Cellular) immediately revokes pinned candidate & bumps generation!
        networkFlow.value = NetworkSnapshot(
            internalNetworkHandleKey = "cellular-5g",
            isConnected = true,
            underlyingTransports = setOf(UnderlyingTransportKind.CELLULAR),
            isMetered = true,
        )
        assertNull(controller.currentPinnedCandidateOrNull())
        val afterTransitionRoute = controller.currentRouteForHost("wenku8-relay.mewx.org")
        assertNull(afterTransitionRoute.pinnedCandidate)
        assertTrue(afterTransitionRoute.generationId > pinnedGen)
    }

    private fun syntheticProbeSample(
        index: Int,
        armId: String,
        candidateId: String?,
        succeeded: Boolean,
        elapsedMs: Long,
        reused: Boolean,
    ): ProbeSampleResult {
        return ProbeSampleResult(
            sampleIndex = index,
            armId = armId,
            candidateId = candidateId,
            succeeded = succeeded,
            httpStatusCode = if (succeeded) 200 else 502,
            retryAfterSeconds = null,
            errorCategory = if (succeeded) null else ProbeErrorCategory.HTTP_STATUS_ERROR,
            fullResponseElapsedMs = elapsedMs,
            responseHeadersStartMs = (elapsedMs - 10L).coerceAtLeast(1L),
            dnsDurationMs = if (reused) null else 5L,
            tcpConnectDurationMs = if (reused) null else 20L,
            tlsHandshakeDurationMs = if (reused) null else 30L,
            connectionReused = reused,
            connectAttemptCount = if (reused) 0 else 1,
            observedProxyType = Proxy.Type.DIRECT,
            localSocketFamily = IpAddressFamily.IPV4,
            negotiatedProtocol = "h2",
            responseBodyBytesRead = 180,
            bodyComplete = true,
            serverPayload = ParsedServerProbePayload(
                schema = "wenku8-network-probe-v1",
                nonce = "0123456789abcdef0123456789abcdef",
                endpointRole = "placed-relay",
                ingressColo = "HKG",
                country = "AU",
                asn = 13335L,
                clientIpFamily = IpAddressFamily.IPV4,
                httpProtocol = "HTTP/2",
                transportRttMs = 22L,
                transportRttSource = "tcp",
                handlerElapsedMs = 1L,
            ),
            observedEvents = if (reused) {
                listOf("callStart", "connectionAcquired", "callEnd")
            } else {
                listOf("callStart", "connectStart(DIRECT)", "connectionAcquired", "callEnd")
            },
        )
    }
}
