package org.mewx.wenku8.data.remote.diagnostics

import android.content.ContentValues
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.data.media.WenkuImageUrlPolicy
import org.mewx.wenku8.data.remote.transport.ActiveRelayRoute
import org.mewx.wenku8.data.remote.transport.PinnedRelayCandidate
import org.mewx.wenku8.data.remote.transport.RelayHttpTransport
import org.mewx.wenku8.data.remote.transport.RelayRouteProvider
import org.mewx.wenku8.data.remote.transport.WenkuSessionCookieStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.net.InetAddress
import java.net.Proxy
import java.util.Random
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DohTxtSubnetRoutingAndLiveServiceTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storeScope: CoroutineScope
    private lateinit var routingLocalStore: RoutingLocalStore

    private val relayEndpoint = (
        DiagnosticsEndpointConfig.parseAndValidate("https://wenku8-relay.mewx.org/")
            as DiagnosticsEndpointResolution.Available
        ).endpoint

    private val androidEndpoint = (
        DiagnosticsEndpointConfig.parseAndValidate("https://mewx-android.wenku8.app/")
            as DiagnosticsEndpointResolution.Available
        ).endpoint

    @Before
    fun setUp() {
        storeScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        val dsFile = File(tempFolder.newFolder("datastore"), "doh_subnet_test.preferences_pb")
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
    fun cidrSubnetSpecValidationAndRandomHostSamplingStaysInsidePublicUnicastBounds() {
        // 1. Non-zero host bits in CIDR input are masked cleanly to canonical network base
        val v4Spec = IpLiteralParser.parseCandidateSpec("cf-a", "104.18.32.99/24")
        assertNotNull(v4Spec)
        assertEquals("104.18.32.0/24", v4Spec!!.canonicalSpec)
        assertFalse(v4Spec.isSingleHost)

        val deterministicRng = Random(42L)
        val sampledLastOctets = HashSet<Int>()
        repeat(1_000) {
            val sampled = v4Spec.sampleCandidateEntry(deterministicRng)
            assertNotNull(sampled)
            assertEquals(104, sampled!!.rawIpBytes[0].toInt() and 0xFF)
            assertEquals(18, sampled.rawIpBytes[1].toInt() and 0xFF)
            assertEquals(32, sampled.rawIpBytes[2].toInt() and 0xFF)
            val hostOctet = sampled.rawIpBytes[3].toInt() and 0xFF
            assertTrue("Must never emit .0 network or .255 broadcast: got $hostOctet", hostOctet in 1..254)
            sampledLastOctets.add(hostOctet)
        }
        assertTrue("1,000 random draws across /24 should hit >150 distinct IPs", sampledLastOctets.size > 150)

        // 2. IPv6 /48 sampling stays inside prefix and never emits all-zero Subnet-Router Anycast (::0)
        val v6Spec = IpLiteralParser.parseCandidateSpec("cf-v6", "2606:4700:3030:abcd::1/48")
        assertNotNull(v6Spec)
        assertEquals("2606:4700:3030::/48", v6Spec!!.canonicalSpec)
        repeat(200) {
            val sampled = v6Spec.sampleCandidateEntry(deterministicRng)
            assertNotNull(sampled)
            assertEquals(0x26, sampled!!.rawIpBytes[0].toInt() and 0xFF)
            assertEquals(0x06, sampled.rawIpBytes[1].toInt() and 0xFF)
            assertEquals(0x47, sampled.rawIpBytes[2].toInt() and 0xFF)
            assertEquals(0x00, sampled.rawIpBytes[3].toInt() and 0xFF)
            assertEquals(0x30, sampled.rawIpBytes[4].toInt() and 0xFF)
            assertEquals(0x30, sampled.rawIpBytes[5].toInt() and 0xFF)
            val anyNonZeroHostByte = (6 until 16).any { idx -> sampled.rawIpBytes[idx].toInt() != 0 }
            assertTrue("IPv6 subnet sample must avoid ::0 all-zero host ID", anyNonZeroHostByte)
        }

        // 3. Reject overly broad prefixes, non-public ranges, and boundary-spanning blocks
        assertNull(IpLiteralParser.parseCandidateSpec("bad-broad-v4", "104.16.0.0/12"))
        assertNull(IpLiteralParser.parseCandidateSpec("bad-broad-v6", "2606:4700::/24"))
        assertNull(IpLiteralParser.parseCandidateSpec("bad-rfc1918", "10.0.0.0/24"))
        assertNull(IpLiteralParser.parseCandidateSpec("bad-cgnat", "100.64.10.0/24"))
        assertNull(IpLiteralParser.parseCandidateSpec("bad-loopback", "127.0.0.0/24"))
        // 192.0.1.0/23 starts at public 192.0.1.0, but its upper half is 192.0.2.0/24 (TEST-NET-1)!
        assertNull(IpLiteralParser.parseCandidateSpec("bad-boundary", "192.0.1.0/23"))

        // 4. All bundled verified Cloudflare subnet seeds parse and sample cleanly
        val defaultSeeds = ManifestRepository.defaultVerifiedCloudflareSubnetSpecs()
        assertEquals(24, defaultSeeds.size)
        defaultSeeds.forEach { spec ->
            assertNotNull("Failed to sample ${spec.canonicalSpec}", spec.sampleCandidateEntry(deterministicRng))
        }
    }

    @Test
    fun dohTxtParserSupportsOptionalDynamicPictureMirrorsAndPersistsAcrossRestarts() = runBlocking {
        val nowWall = 1_763_460_000_000L
        val monoClock = AtomicLong(5_000L)

        val txtWithMirrors = listOf(
            "v=wk8-txt-v1;rev=20261126-pm;en=1;iat=1763459500;exp=1763546400;c=104.16.80.0/24;" +
                "pm=pic.newcdn.xyz,127.0.0.1,localhost,pic.backup.org/pictures,bad:8080",
        )
        val pmOutcome = ManifestRepository.parseTxtConfigStrings(
            txtStrings = txtWithMirrors,
            minTtlSeconds = 300,
            expectedRelayHostname = "wenku8-relay.mewx.org",
            nowWallEpochMs = nowWall,
            nowMonotonicMs = monoClock.get(),
        )
        assertTrue(pmOutcome is ManifestRepository.DohTxtParseOutcome.ValidManifest)
        val pmValid = pmOutcome as ManifestRepository.DohTxtParseOutcome.ValidManifest
        val expectedDynamicMirrors = listOf(
            WenkuImageUrlPolicy.PictureMirrorEndpoint("pic.newcdn.xyz", usesPicturesPrefix = false),
            WenkuImageUrlPolicy.PictureMirrorEndpoint("pic.backup.org", usesPicturesPrefix = true),
        )
        assertEquals(expectedDynamicMirrors, pmValid.manifest.pictureMirrors)

        val repoForPm = ManifestRepository(
            localStore = routingLocalStore,
            wallClockEpochMillis = { nowWall },
            monotonicClockMillis = { monoClock.get() },
        )
        assertNotNull(repoForPm.ingestManifestPayload(pmValid.canonicalJson, "wenku8-relay.mewx.org"))
        assertEquals(expectedDynamicMirrors, repoForPm.currentDynamicPictureMirrorsOrEmpty())

        // Cold-start ManifestRepository instance recovers persisted `pictureMirrors` from RoutingLocalStore!
        val coldStartRepo = ManifestRepository(
            localStore = routingLocalStore,
            wallClockEpochMillis = { nowWall },
            monotonicClockMillis = { monoClock.get() },
        )
        assertNotNull(coldStartRepo.loadCachedOrInMemoryManifest("wenku8-relay.mewx.org"))
        assertEquals(expectedDynamicMirrors, coldStartRepo.currentDynamicPictureMirrorsOrEmpty())
    }

    @Test
    fun dohTxtParserAndMockResolverHandleAlidnsDnspodShardsNxdomainAndKillSwitch() = runBlocking {
        val nowWall = 1_763_460_000_000L // 2025-11-18T10:00:00Z
        val monoClock = AtomicLong(5_000L)

        // 1. Multi-chunk RFC 1035 TXT unquoting (>255B split strings)
        val chunkedTxt = "\"v=wk8-txt-v1;rev=20261118-a;en=1;iat=1763459000;\" \"exp=1763546400;c=hk@104.16.80.0/24,172.67.150.0/24\""
        val unquoted = ManifestRepository.unquoteRfc1035TxtData(chunkedTxt)
        assertEquals(
            "v=wk8-txt-v1;rev=20261118-a;en=1;iat=1763459000;exp=1763546400;c=hk@104.16.80.0/24,172.67.150.0/24",
            unquoted,
        )

        // 2. Parse DNSPod array-Question JSON with primary + continuation `wk8-txt-ext1` shards
        val dnspodMultiShardJson = """
            {
              "Status": 0,
              "TC": false,
              "RD": true,
              "RA": true,
              "Question": [{"name": "_network_cfg.wenku8.app.", "type": 16}],
              "Answer": [
                {
                  "name": "_network_cfg.wenku8.app.",
                  "type": 16,
                  "TTL": 300,
                  "data": "\"v=wk8-txt-ext1;rev=20261118-b;p=2;c=2606:4700:3030::/48\""
                },
                {
                  "name": "_network_cfg.wenku8.app.",
                  "type": 16,
                  "TTL": 180,
                  "data": "\"v=wk8-txt-v1;rev=20261118-b;en=1;iat=1763459500;exp=1763546400;\" \"c=cf1@104.16.80.0/24\""
                },
                {
                  "name": "_network_cfg.wenku8.app.",
                  "type": 16,
                  "TTL": 300,
                  "data": "\"v=wk8-txt-ext1;rev=20261118-b;p=1;c=172.67.150.0/24\""
                }
              ]
            }
        """.trimIndent()

        val parsedOutcome = ManifestRepository.parseDohJsonResponse(
            rawDohJson = dnspodMultiShardJson,
            expectedRelayHostname = "wenku8-relay.mewx.org",
            nowWallEpochMs = nowWall,
            nowMonotonicMs = monoClock.get(),
        )
        assertTrue(parsedOutcome is ManifestRepository.DohTxtParseOutcome.ValidManifest)
        val valid = parsedOutcome as ManifestRepository.DohTxtParseOutcome.ValidManifest
        assertEquals(180, valid.minTtlSeconds)
        assertEquals("20261118-b", valid.manifest.revision)
        assertEquals(
            listOf("104.16.80.0/24", "172.67.150.0/24", "2606:4700:3030::/48"),
            valid.manifest.candidateSpecs.map { it.canonicalSpec },
        )

        // 3. MockWebServer failover: Resolver #1 returns 502 -> Resolver #2 returns NXDOMAIN -> falls back to bundled subnets!
        MockWebServer().use { deadDoh ->
            MockWebServer().use { liveDoh ->
                deadDoh.start(InetAddress.getByName("127.0.0.1"), 0)
                liveDoh.start(InetAddress.getByName("127.0.0.1"), 0)

                val endpoints = listOf(
                    DohTxtResolverEndpoint(
                        id = "mock-doh-1",
                        resolverIpLiteral = "127.0.0.1",
                        resolveUrlTemplate = "http://127.0.0.1:${deadDoh.port}/resolve?name=%s&type=TXT",
                    ),
                    DohTxtResolverEndpoint(
                        id = "mock-doh-2",
                        resolverIpLiteral = "127.0.0.1",
                        resolveUrlTemplate = "http://127.0.0.1:${liveDoh.port}/resolve?name=%s&type=TXT",
                    ),
                )

                val repo = ManifestRepository(
                    localStore = routingLocalStore,
                    dohTxtDomain = ManifestRepository.DEFAULT_DOH_TXT_CONFIG_DOMAIN,
                    dohEndpoints = endpoints,
                    bundledCandidateSpecs = ManifestRepository.defaultVerifiedCloudflareSubnetSpecs(),
                    requirePublicUnicastForCandidates = false, // allow 127.0.0.1 mock DoH resolver IP in unit test
                    wallClockEpochMillis = { nowWall },
                    monotonicClockMillis = { monoClock.get() },
                )

                // Step A: NXDOMAIN on `_network_cfg.wenku8.app` -> seamlessly serves bundled Cloudflare /24 subnets
                deadDoh.enqueue(MockResponse.Builder().code(502).build())
                liveDoh.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/dns-json")
                        .body(
                            """{"Status":3,"Question":{"name":"_network_cfg.wenku8.app.","type":16},"Authority":[{"name":"wenku8.app.","type":6,"TTL":600,"data":"ns"}]}""",
                        )
                        .build(),
                )

                val fallbackManifest = repo.refreshFromIndependentOrigins("wenku8-relay.mewx.org")
                assertNotNull(fallbackManifest)
                assertEquals("bundled-cf-subnets-v1", fallbackManifest!!.revision)
                assertTrue(fallbackManifest.enabled)
                assertEquals(24, fallbackManifest.candidateSpecs.size)

                // Step B: Operator publishes live TXT record on `_network_cfg.wenku8.app` and user forces recheck
                deadDoh.enqueue(MockResponse.Builder().code(502).build())
                liveDoh.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/dns-json")
                        .body(dnspodMultiShardJson)
                        .build(),
                )
                val liveTxtManifest = repo.refreshFromIndependentOrigins(
                    expectedRelayHostname = "wenku8-relay.mewx.org",
                    forceRefresh = true,
                )
                assertNotNull(liveTxtManifest)
                assertEquals("20261118-b", liveTxtManifest!!.revision)
                assertEquals(3, liveTxtManifest.candidateSpecs.size)

                // Step C: Operator publishes remote kill-switch (`en=0`) -> MUST NOT fall back to bundled seeds!
                val killSwitchDohJson = """
                    {
                      "Status": 0,
                      "Question": {"name": "_network_cfg.wenku8.app.", "type": 16},
                      "Answer": [
                        {
                          "name": "_network_cfg.wenku8.app.",
                          "type": 16,
                          "TTL": 120,
                          "data": "\"v=wk8-txt-v1;rev=20261118-kill;en=0;iat=1763459900;exp=1763546400;c=\""
                        }
                      ]
                    }
                """.trimIndent()
                deadDoh.enqueue(MockResponse.Builder().code(502).build())
                liveDoh.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "application/dns-json")
                        .body(killSwitchDohJson)
                        .build(),
                )
                val revoked = repo.refreshFromIndependentOrigins(
                    expectedRelayHostname = "wenku8-relay.mewx.org",
                    forceRefresh = true,
                )
                assertNotNull(revoked)
                assertFalse("Remote TXT en=0 kill-switch must disable routing and never bypass to bundled seeds", revoked!!.enabled)

                // Even if all DoH endpoints go offline afterward, persisted kill-switch remains enforced!
                monoClock.addAndGet(600_000L)
                deadDoh.enqueue(MockResponse.Builder().code(502).build())
                liveDoh.enqueue(MockResponse.Builder().code(502).build())
                val afterOffline = repo.refreshFromIndependentOrigins(
                    expectedRelayHostname = "wenku8-relay.mewx.org",
                    forceRefresh = true,
                )
                assertNotNull(afterOffline)
                assertFalse("Persisted kill-switch must survive DoH outage without falling back to bundled seeds", afterOffline!!.enabled)
            }
        }
    }

    @Test
    fun deadDefaultDnsAndBlockedCandidatesStillPromoteWorkingSubnetIpAndFallbackCleanly() = runBlocking {
        val nowWall = 1_763_460_000_000L
        val monoClock = AtomicLong(10_000L)
        val networkFlow = MutableStateFlow(
            NetworkSnapshot(
                internalNetworkHandleKey = "wifi-cn",
                isConnected = true,
                underlyingTransports = setOf(UnderlyingTransportKind.WIFI),
                isMetered = false,
            ),
        )
        val fakeObserver = object : NetworkConnectivityObserver {
            override fun currentSnapshot(): NetworkSnapshot = networkFlow.value
            override fun observeDefaultNetworkSnapshots(): Flow<NetworkSnapshot> = networkFlow
        }

        val specs = listOf(
            IpLiteralParser.parseCandidateSpec("blocked-a", "104.16.80.0/24")!!,
            IpLiteralParser.parseCandidateSpec("blocked-b", "104.17.120.0/24")!!,
            IpLiteralParser.parseCandidateSpec("good-hk", "172.67.150.0/24")!!,
            IpLiteralParser.parseCandidateSpec("blocked-c", "162.159.135.0/24")!!,
        )
        val manifestRepo = ManifestRepository(
            localStore = routingLocalStore,
            bundledCandidateSpecs = specs,
            random = Random(7L),
            wallClockEpochMillis = { nowWall },
            monotonicClockMillis = { monoClock.get() },
            ioDispatcher = Dispatchers.Unconfined,
        )

        val defaultConfirmProbes = AtomicInteger(0)
        val simulatedGfwProbeTransport = object : DiagnosticsProbeTransport {
            override suspend fun executeProbe(
                endpoint: ValidatedRelayEndpoint,
                armConfig: ProbeArmConfig,
                sampleIndex: Int,
                explicitNonce: String?,
            ): ProbeSampleResult {
                val pinned = armConfig.dnsPolicy as? ProbeDnsPolicy.PinnedCandidate
                if (armConfig.armId == "confirm:default") {
                    defaultConfirmProbes.incrementAndGet()
                }
                val isGoodSubnet = pinned != null &&
                    (pinned.rawIpBytes[0].toInt() and 0xFF) == 172 &&
                    (pinned.rawIpBytes[1].toInt() and 0xFF) == 67

                if (!isGoodSubnet) {
                    // Simulate 4,000 ms GFW TCP SYN blackhole timeout on Default DNS AND 3 blocked subnets!
                    // Without skipping redundant 2nd/3rd confirm:default timeouts, 6 * 4,000ms = 24,000ms +
                    // challenger probes would exceed MAX_TOURNAMENT_DURATION_MS (25,000ms)!
                    monoClock.addAndGet(4_000L)
                    return ProbeSampleResult(
                        sampleIndex = sampleIndex,
                        armId = armConfig.armId,
                        candidateId = pinned?.candidateId,
                        succeeded = false,
                        httpStatusCode = null,
                        retryAfterSeconds = null,
                        errorCategory = ProbeErrorCategory.CONNECT_TIMEOUT,
                        fullResponseElapsedMs = 4_000L,
                        responseHeadersStartMs = null,
                        dnsDurationMs = if (pinned == null) 25L else null,
                        tcpConnectDurationMs = null,
                        tlsHandshakeDurationMs = null,
                        connectionReused = false,
                        connectAttemptCount = 1,
                        observedProxyType = Proxy.Type.DIRECT,
                        localSocketFamily = IpAddressFamily.IPV4,
                        negotiatedProtocol = null,
                        responseBodyBytesRead = 0,
                        bodyComplete = false,
                        serverPayload = null,
                        observedEvents = listOf("callStart", "connectFailed"),
                    )
                }

                val reused = armConfig.armId.startsWith("warm_guard:")
                val elapsed = if (reused) 42L else 95L
                monoClock.addAndGet(elapsed)
                return ProbeSampleResult(
                    sampleIndex = sampleIndex,
                    armId = armConfig.armId,
                    candidateId = pinned.candidateId,
                    succeeded = true,
                    httpStatusCode = 200,
                    retryAfterSeconds = null,
                    errorCategory = null,
                    fullResponseElapsedMs = elapsed,
                    responseHeadersStartMs = elapsed - 8L,
                    dnsDurationMs = null,
                    tcpConnectDurationMs = if (reused) null else 35L,
                    tlsHandshakeDurationMs = if (reused) null else 45L,
                    connectionReused = reused,
                    connectAttemptCount = if (reused) 0 else 1,
                    observedProxyType = Proxy.Type.DIRECT,
                    localSocketFamily = IpAddressFamily.IPV4,
                    negotiatedProtocol = "h2",
                    responseBodyBytesRead = 185,
                    bodyComplete = true,
                    serverPayload = ParsedServerProbePayload(
                        schema = OkHttpProbeTransport.PROBE_SCHEMA_V1,
                        nonce = "0123456789abcdef0123456789abcdef",
                        endpointRole = "placed-relay",
                        ingressColo = "HKG",
                        country = "CN",
                        asn = 4134L,
                        clientIpFamily = IpAddressFamily.IPV4,
                        httpProtocol = "HTTP/2",
                        transportRttMs = 34L,
                        transportRttSource = "tcp",
                        handlerElapsedMs = 1L,
                    ),
                    observedEvents = listOf("callStart", "connectionAcquired", "callEnd"),
                )
            }
        }

        val controller = RelayRouteController(
            applicationScope = storeScope,
            endpointResolutionProvider = { DiagnosticsEndpointResolution.Available(relayEndpoint) },
            probeTransport = simulatedGfwProbeTransport,
            manifestRepository = manifestRepo,
            routingLocalStore = routingLocalStore,
            networkObserver = fakeObserver,
            autoProbeGapMillis = 0L,
            initialAutoOptimizeEnabled = true,
            allowBootstrapRecoveryOnDefaultFailure = true,
            monotonicClockMillis = { monoClock.get() },
            wallClockEpochMillis = { nowWall },
        )

        // 1. Compact tournament promotes sampled IP from `172.67.150.0/24` despite dead Default DNS + 3 dead candidates!
        assertTrue(controller.runCompactTournament(manualRecheck = true))
        assertEquals(
            "Stage 2 must stop confirming Default DNS after its 1st confirmation also fails",
            1,
            defaultConfirmProbes.get(),
        )
        val firstPinned = controller.currentPinnedCandidateOrNull()
        assertNotNull(firstPinned)
        assertTrue(firstPinned!!.ipLiteral.startsWith("172.67.150."))
        val activeState = controller.uiState.value as AutoRoutingModeState.OptimizedRouteActive
        assertTrue(activeState.promotedOnReliability)

        // 2. Re-evaluating draws a fresh random host IP from `172.67.150.0/24`
        controller.resetToDefaultAndReevaluate(triggerTournamentNow = false)
        assertNull(controller.currentPinnedCandidateOrNull())

        // 3. When Default DNS fails on an ordinary API call (`onPassiveCallCompleted`),
        // `allowBootstrapRecoveryOnDefaultFailure = true` automatically runs bounded recovery and pins a working IP!
        controller.onPassiveCallCompleted(
            generationId = controller.currentRouteForHost("wenku8-relay.mewx.org").generationId,
            candidateId = null,
            succeeded = false,
            connectionSetupMs = null,
        )
        val recoveredCandidate = controller.currentPinnedCandidateOrNull()
        assertNotNull("Default-route failure must auto-recover onto working /24 candidate", recoveredCandidate)
        assertTrue(recoveredCandidate!!.ipLiteral.startsWith("172.67.150."))
        assertNotEquals(firstPinned.ipLiteral, recoveredCandidate.ipLiteral)

        // 4. If the pinned IP later fails pre-send, fallbackRouteAfterCandidateFailure quarantines it and restores Default DNS!
        val activeRoute = controller.currentRouteForHost("wenku8-relay.mewx.org")
        val fallbackRoute = controller.fallbackRouteAfterCandidateFailure(
            failedGenerationId = activeRoute.generationId,
            failedCandidateId = recoveredCandidate.id,
            failureBeforeSend = true,
        )
        assertNull(fallbackRoute.pinnedCandidate)
        assertTrue(controller.uiState.value is AutoRoutingModeState.RestoredDefaultAfterFallback)
    }

    /**
     * Real-service end-to-end verification against live domestic DoH (`223.5.5.5`, `1.12.12.12`),
     * `_network_cfg.wenku8.app`, and live Cloudflare Workers (`wenku8-relay.mewx.org` &
     * `mewx-android.wenku8.app` `/__diagnostics/latency`).
     *
     * Opt-in via `-Dwenku8.liveNetworkTest=true` so offline CI builds remain hermetic.
     */
    @Test
    fun liveServiceEndToEndDohTxtAndCloudflareSubnetRoutingAgainstRealRelay() = runBlocking {
        assumeTrue(
            "Skipped unless -Dwenku8.liveNetworkTest=true is passed",
            java.lang.Boolean.getBoolean("wenku8.liveNetworkTest"),
        )

        val sharedClient = OkHttpProbeTransport.defaultSharedBaseOkHttpClient()

        // --- Part 1: Verify real pure-IP HTTPS JSON DoH against domestic resolvers (`223.5.5.5`, `1.12.12.12`, etc.) ---
        val domesticEndpoints = ManifestRepository.defaultDomesticDohEndpoints()
        var verifiedLiveEndpointCount = 0
        val endpointDiagnostics = mutableListOf<String>()
        for (ep in domesticEndpoints) {
            val rawBytes = IpLiteralParser.parseNumericIpBytes(ep.resolverIpLiteral!!)!!
            val targetHost = ep.resolverIpLiteral
            val dohClient = sharedClient.newBuilder()
                .cookieJar(CookieJar.NO_COOKIES)
                .dns(PinnedHostIpDns(targetHost, rawBytes))
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            try {
                // 1a. Live query for `_network_cfg.wenku8.app`
                val cfgUrl = String.format(ep.resolveUrlTemplate, ManifestRepository.DEFAULT_DOH_TXT_CONFIG_DOMAIN)
                val validCfg = dohClient.newCall(
                    Request.Builder()
                        .url(cfgUrl)
                        .header("Accept", "application/dns-json, application/json")
                        .build(),
                ).execute().use { resp ->
                    assertEquals("Live DoH ${ep.id} HTTP status must be 200", 200, resp.code)
                    val body = resp.body.string()
                    val outcome = ManifestRepository.parseDohJsonResponse(
                        rawDohJson = body,
                        expectedRelayHostname = relayEndpoint.relayHost,
                        nowWallEpochMs = System.currentTimeMillis(),
                        nowMonotonicMs = 1_000L,
                    )
                    assertTrue(
                        "Live _network_cfg.wenku8.app on ${ep.id} must parse as ValidManifest, got: $outcome",
                        outcome is ManifestRepository.DohTxtParseOutcome.ValidManifest,
                    )
                    (outcome as ManifestRepository.DohTxtParseOutcome.ValidManifest).manifest
                }

                // 1b. Live query for `cloudflare.com` TXT to prove real multi-TXT RFC 1035 unquoting on live DoH wire payloads
                val cfTxtUrl = String.format(ep.resolveUrlTemplate, "cloudflare.com")
                dohClient.newCall(
                    Request.Builder()
                        .url(cfTxtUrl)
                        .header("Accept", "application/dns-json, application/json")
                        .build(),
                ).execute().use { resp ->
                    assertEquals(200, resp.code)
                    val body = resp.body.string()
                    assertTrue("Live DoH TXT answer from ${ep.id} must include Answer array", body.contains("\"Answer\""))
                }

                verifiedLiveEndpointCount++
                endpointDiagnostics.add("${ep.id}: OK (rev=${validCfg.revision}, specs=${validCfg.candidateSpecs.size})")
                if (verifiedLiveEndpointCount >= 2) break
            } catch (ioe: java.io.IOException) {
                endpointDiagnostics.add("${ep.id}: transient ${ioe.javaClass.simpleName}(${ioe.message})")
            }
        }
        assertTrue(
            "At least one pure-IP domestic DoH endpoint must resolve live _network_cfg.wenku8.app ValidManifest; results=$endpointDiagnostics",
            verifiedLiveEndpointCount >= 1,
        )

        // --- Part 2: Real ManifestRepository + simulated GFW-blocked Default DNS -> promotes real Cloudflare /24 IP ---
        val ipv4OnlyBundledSpecs = ManifestRepository.defaultVerifiedCloudflareSubnetSpecs()
            .filter { it.family == IpAddressFamily.IPV4 }

        // Robolectric shadows SystemClock.elapsedRealtime() as frozen unless manually advanced;
        // bind real JVM monotonic nanoseconds for genuine live-network latency & timeout measurement!
        val realMonotonicMs: () -> Long = { System.nanoTime() / 1_000_000L }

        val liveManifestRepo = ManifestRepository(
            localStore = routingLocalStore,
            baseOkHttpClient = sharedClient,
            dohTxtDomain = ManifestRepository.DEFAULT_DOH_TXT_CONFIG_DOMAIN,
            dohEndpoints = domesticEndpoints,
            bundledCandidateSpecs = ipv4OnlyBundledSpecs,
            monotonicClockMillis = realMonotonicMs,
        )

        val resolvedManifest = liveManifestRepo.refreshFromIndependentOrigins(
            expectedRelayHostname = relayEndpoint.relayHost,
            forceRefresh = true,
        )
        assertNotNull("Live ManifestRepository must resolve live _network_cfg.wenku8.app TXT", resolvedManifest)
        assertTrue(resolvedManifest!!.enabled)
        assertTrue(resolvedManifest.candidateSpecs.isNotEmpty())
        assertTrue(
            "Expected live DoH TXT revision (not bundled fallback), got revision=${resolvedManifest.revision}",
            resolvedManifest.revision != "bundled-cf-subnets-v1",
        )

        // Blackhole Default DNS to TEST-NET-1 (`192.0.2.1`) to simulate GFW DNS poisoning/TCP blackhole!
        val gfwBlackholeSystemDns = Dns { hostname ->
            listOf(InetAddress.getByAddress(hostname, byteArrayOf(192.toByte(), 0, 2, 1)))
        }
        val rawLiveProbeTransport = OkHttpProbeTransport(
            baseOkHttpClient = sharedClient,
            systemDns = gfwBlackholeSystemDns,
            monotonicClockMillis = realMonotonicMs,
        )
        val recordedLiveProbes = java.util.Collections.synchronizedList(mutableListOf<String>())
        val liveProbeTransportWithBlockedDefaultDns = object : DiagnosticsProbeTransport {
            override suspend fun executeProbe(
                endpoint: ValidatedRelayEndpoint,
                armConfig: ProbeArmConfig,
                sampleIndex: Int,
                explicitNonce: String?,
            ): ProbeSampleResult {
                val res = rawLiveProbeTransport.executeProbe(endpoint, armConfig, sampleIndex, explicitNonce)
                recordedLiveProbes.add(
                    "#${res.sampleIndex} ${res.armId}: ok=${res.succeeded} status=${res.httpStatusCode} " +
                        "err=${res.errorCategory} ms=${res.fullResponseElapsedMs} reused=${res.connectionReused} " +
                        "connAttempts=${res.connectAttemptCount} proxy=${res.observedProxyType} " +
                        "schema=${res.serverPayload?.schema} events=${res.observedEvents}",
                )
                return res
            }
        }

        val networkFlow = MutableStateFlow(
            NetworkSnapshot(
                internalNetworkHandleKey = "live-net-1",
                isConnected = true,
                underlyingTransports = setOf(UnderlyingTransportKind.WIFI),
                isMetered = false,
            ),
        )
        val liveNetworkObserver = object : NetworkConnectivityObserver {
            override fun currentSnapshot(): NetworkSnapshot = networkFlow.value
            override fun observeDefaultNetworkSnapshots(): Flow<NetworkSnapshot> = networkFlow
        }

        val liveController = RelayRouteController(
            applicationScope = storeScope,
            endpointResolutionProvider = { DiagnosticsEndpointResolution.Available(relayEndpoint) },
            probeTransport = liveProbeTransportWithBlockedDefaultDns,
            manifestRepository = liveManifestRepo,
            routingLocalStore = routingLocalStore,
            networkObserver = liveNetworkObserver,
            autoProbeGapMillis = 100L,
            initialAutoOptimizeEnabled = true,
            allowBootstrapRecoveryOnDefaultFailure = true,
            monotonicClockMillis = realMonotonicMs,
        )

        val tournamentWon = liveController.runCompactTournament(manualRecheck = true)
        assertTrue(
            "Real compact tournament failed! uiState=${liveController.uiState.value}\nProbes:\n" +
                recordedLiveProbes.joinToString("\n"),
            tournamentWon,
        )

        val winningCandidate = liveController.currentPinnedCandidateOrNull()
        assertNotNull(winningCandidate)
        val winningState = liveController.uiState.value as AutoRoutingModeState.OptimizedRouteActive
        assertTrue(winningState.promotedOnReliability)
        println(
            "LIVE TOURNAMENT WON: pinned=${winningCandidate!!.ipLiteral} (${winningCandidate.id}), " +
                "medianMs=${winningState.medianLatencyMs}, manifest=${winningState.manifestRevision}",
        )

        // Verify the exact winning sampled IP also authenticates cleanly against the second relay hostname (`mewx-android.wenku8.app`)!
        val realProbeTransport = OkHttpProbeTransport(
            baseOkHttpClient = sharedClient,
            monotonicClockMillis = realMonotonicMs,
        )
        val androidVerifySample = realProbeTransport.executeProbe(
            endpoint = androidEndpoint,
            armConfig = ProbeArmConfig(
                armId = "verify-android-host",
                dnsPolicy = ProbeDnsPolicy.PinnedCandidate(
                    candidateId = winningCandidate.id,
                    relayHostname = androidEndpoint.relayHost,
                    rawIpBytes = winningCandidate.rawIpBytes,
                ),
                fastFallback = false,
            ),
            sampleIndex = 1,
        )
        assertTrue(
            "Winning sampled IP ${winningCandidate.ipLiteral} must also serve https://mewx-android.wenku8.app/__diagnostics/latency",
            androidVerifySample.succeeded &&
                androidVerifySample.serverPayload?.schema == OkHttpProbeTransport.PROBE_SCHEMA_V1,
        )

        // --- Part 3: Real RelayHttpTransport fallback from a dead pinned IP back to live System DNS ---
        val fallbackTriggered = AtomicBoolean(false)
        val deadPinnedThenRealDnsProvider = object : RelayRouteProvider {
            @Volatile
            private var useDeadCandidate = true

            override fun currentRouteForHost(host: String): ActiveRelayRoute {
                return if (useDeadCandidate) {
                    ActiveRelayRoute(
                        generationId = 10L,
                        pinnedCandidate = PinnedRelayCandidate(
                            candidateId = "simulated-dead-cf-ip",
                            relayHost = host,
                            // 192.0.2.9 (TEST-NET-1 unreachable blackhole -> triggers pre-send connect timeout!)
                            rawIpBytes = byteArrayOf(192.toByte(), 0, 2, 9),
                        ),
                    )
                } else {
                    ActiveRelayRoute(generationId = 11L, pinnedCandidate = null)
                }
            }

            override fun fallbackRouteAfterCandidateFailure(
                failedGenerationId: Long,
                failedCandidateId: String,
                failureBeforeSend: Boolean,
            ): ActiveRelayRoute {
                assertTrue("Connect timeout on dead pinned IP must be classified as pre-send", failureBeforeSend)
                fallbackTriggered.set(true)
                useDeadCandidate = false
                return ActiveRelayRoute(generationId = 11L, pinnedCandidate = null)
            }
        }

        val liveRelayTransport = RelayHttpTransport(
            baseOkHttpClient = sharedClient,
            routeProvider = deadPinnedThenRealDnsProvider,
            sessionCookieStore = object : WenkuSessionCookieStore {
                private var sid = ""
                override fun getPhpSessionId(): String = sid
                override fun setPhpSessionId(sessionId: String) { sid = sessionId }
                override fun clearPhpSessionId() { sid = "" }
            },
            systemDns = Dns.SYSTEM,
            endpointConfiguredResolver = { true },
            requestSealer = { _, _ ->
                org.mewx.wenku8.api.SealedRequest(
                    "https://wenku8-relay.mewx.org/v2/api",
                    "application/octet-stream",
                    mapOf("X-WK8-Proto" to "2"),
                    byteArrayOf(0x01),
                )
            },
            monotonicClockMillis = realMonotonicMs,
            connectTimeoutMillis = 1_200L,
            readTimeoutMillis = 5_000L,
            totalPostDeadlineMillis = 9_000L,
        )

        // Send a benign sealed POST to live https://wenku8-relay.mewx.org/ (without valid envelope,
        // the live Worker returns HTTP 400/403/200 over real TLS after falling back from dead 192.0.2.9 -> real Dns.SYSTEM).
        // Because `fallbackTriggered` only fires when attempt #1 fails pre-send AND attempt #2 is executed on `Dns.SYSTEM`,
        // we also verify a real `GET` / probe on `Dns.SYSTEM` succeeds!
        liveRelayTransport.postSealedAction(
            rawActionQuery = "ping=1",
            isIdempotentReadOnly = true,
        )
        assertTrue(
            "RelayHttpTransport must catch dead pinned IP connect timeout and invoke fallbackRouteAfterCandidateFailure -> System DNS",
            fallbackTriggered.get(),
        )

        // Confirm live System DNS probe to `https://wenku8-relay.mewx.org/__diagnostics/latency` succeeds right after fallback!
        val realDefaultSample = realProbeTransport.executeProbe(
            endpoint = relayEndpoint,
            armConfig = ProbeArmConfig(
                armId = "default-after-fallback",
                dnsPolicy = ProbeDnsPolicy.SystemDefault,
                fastFallback = true,
            ),
            sampleIndex = 1,
        )
        assertTrue(
            "Live System DNS probe to https://wenku8-relay.mewx.org/__diagnostics/latency must succeed",
            realDefaultSample.succeeded &&
                realDefaultSample.serverPayload?.schema == OkHttpProbeTransport.PROBE_SCHEMA_V1,
        )
    }
}
