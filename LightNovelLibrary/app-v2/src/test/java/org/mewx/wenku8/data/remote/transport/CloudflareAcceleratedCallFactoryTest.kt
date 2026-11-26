package org.mewx.wenku8.data.remote.transport

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
import mockwebserver3.SocketEffect
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.data.media.WenkuImageUrlPolicy
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsEndpointConfig
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsEndpointResolution
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsProbeTransport
import org.mewx.wenku8.data.remote.diagnostics.IpAddressFamily
import org.mewx.wenku8.data.remote.diagnostics.IpLiteralParser
import org.mewx.wenku8.data.remote.diagnostics.ManifestRepository
import org.mewx.wenku8.data.remote.diagnostics.NetworkConnectivityObserver
import org.mewx.wenku8.data.remote.diagnostics.NetworkSnapshot
import org.mewx.wenku8.data.remote.diagnostics.OkHttpProbeTransport
import org.mewx.wenku8.data.remote.diagnostics.ParsedServerProbePayload
import org.mewx.wenku8.data.remote.diagnostics.ProbeArmConfig
import org.mewx.wenku8.data.remote.diagnostics.ProbeDnsPolicy
import org.mewx.wenku8.data.remote.diagnostics.ProbeSampleResult
import org.mewx.wenku8.data.remote.diagnostics.RelayRouteController
import org.mewx.wenku8.data.remote.diagnostics.RoutingLocalStore
import org.mewx.wenku8.data.remote.diagnostics.UnderlyingTransportKind
import org.mewx.wenku8.data.remote.diagnostics.ValidatedRelayEndpoint
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.Proxy
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CloudflareAcceleratedCallFactoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun hostnameFilterExcludesIpLiteralsLocalhostAndSingleLabelHosts() {
        assertTrue(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("img.wenku8.com"))
        assertTrue(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("pic.wenku8.com"))
        assertTrue(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("pic.wenku8.cn"))
        assertTrue(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("pic.777743.xyz"))
        assertTrue(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("avatar.wenku8.com"))
        assertTrue(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("wenku8.mewx.org"))
        assertTrue(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("wenku8.app"))

        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("223.5.5.5"))
        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("1.12.12.12"))
        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("127.0.0.1"))
        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("::1"))
        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("[::1]"))
        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("localhost"))
        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("api.localhost"))
        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("printer.local"))
        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("service.internal"))
        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname("intranet"))
        assertFalse(CloudflareAcceleratedCallFactory.isPinningCandidateHostname(""))
    }

    @Test
    fun routesCoversRedirectsAndVersionChecksThroughPinnedEdgeIpAcrossExecuteAndEnqueue() = runBlocking {
        MockWebServer().use { systemOrigin ->
            MockWebServer().use { pinnedEdge ->
                systemOrigin.start(InetAddress.getByName("127.0.0.1"), 0)
                val sharedPort = systemOrigin.port
                pinnedEdge.start(InetAddress.getByName("127.0.0.2"), sharedPort)

                val pinnedBytes = IpLiteralParser.parseNumericIpBytes("127.0.0.2", requirePublicUnicast = false)!!
                val systemBytes = IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false)!!

                val activeEdgeRef = AtomicReference<ActiveCloudflareEdgeRoute?>(
                    ActiveCloudflareEdgeRoute(
                        generationId = 10L,
                        candidateId = "cf-edge-1",
                        rawIpBytes = pinnedBytes,
                    ),
                )

                val fakeSystemDns = Dns { host ->
                    listOf(InetAddress.getByAddress(host, systemBytes))
                }

                val factory = CloudflareAcceleratedCallFactory(
                    edgeRouteProvider = { activeEdgeRef.get() },
                    systemDns = fakeSystemDns,
                )

                // 1. Multi-hop Cloudflare redirect (`img.wenku8.com` -> `cdn.777743.xyz`) both hit pinned edge!
                pinnedEdge.enqueue(
                    MockResponse.Builder()
                        .code(302)
                        .addHeader("Server", "cloudflare")
                        .addHeader("Location", "http://cdn.777743.xyz:$sharedPort/1/1305/41759/50471.jpg")
                        .build(),
                )
                pinnedEdge.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Server", "cloudflare")
                        .addHeader("Content-Type", "image/jpeg")
                        .body("JPEG-BYTES-VIA-CF-EDGE")
                        .build(),
                )

                factory.newCall(
                    Request.Builder()
                        .url("http://img.wenku8.com:$sharedPort/image/1/1305/1305s.jpg")
                        .get()
                        .build(),
                ).execute().use { resp ->
                    assertEquals(200, resp.code)
                    assertEquals("JPEG-BYTES-VIA-CF-EDGE", resp.body.string())
                }
                assertEquals(0, systemOrigin.requestCount)
                assertEquals(2, pinnedEdge.requestCount)
                val hop1 = pinnedEdge.takeRequest()
                val hop2 = pinnedEdge.takeRequest()
                assertTrue(hop1.headers["Host"].orEmpty().startsWith("img.wenku8.com"))
                assertTrue(hop2.headers["Host"].orEmpty().startsWith("cdn.777743.xyz"))

                // 1b. Illustration URL (`pic.wenku8.com/pictures/...`) goes DIRECT to Option A primary `pic.777743.xyz` on pinned edge!
                pinnedEdge.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Server", "cloudflare")
                        .addHeader("Content-Type", "image/jpeg")
                        .body("DIRECT-OPTION-A-VIA-CF-EDGE")
                        .build(),
                )
                factory.newCall(
                    Request.Builder()
                        .url("http://pic.wenku8.com:$sharedPort/pictures/1/1305/41759/50471.jpg")
                        .get()
                        .build(),
                ).execute().use { resp ->
                    assertEquals(200, resp.code)
                    assertEquals("DIRECT-OPTION-A-VIA-CF-EDGE", resp.body.string())
                    assertEquals("pic.777743.xyz", resp.request.url.host)
                    assertEquals("/1/1305/41759/50471.jpg", resp.request.url.encodedPath)
                }
                assertEquals(0, systemOrigin.requestCount)
                assertEquals(3, pinnedEdge.requestCount)
                val directReq = pinnedEdge.takeRequest()
                assertTrue(directReq.headers["Host"].orEmpty().startsWith("pic.777743.xyz"))

                // 2. Async enqueue() (as used by Coil 3) also routes img.wenku8.com through pinned edge!
                pinnedEdge.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Server", "cloudflare")
                        .addHeader("Content-Type", "image/jpeg")
                        .body("COVER-ASYNC-CF")
                        .build(),
                )
                val latch = CountDownLatch(1)
                val asyncBody = AtomicReference<String?>(null)
                factory.newCall(
                    Request.Builder()
                        .url("http://img.wenku8.com:$sharedPort/image/3/3027/3027s.jpg")
                        .get()
                        .build(),
                ).enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            latch.countDown()
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use { asyncBody.set(it.body.string()) }
                            latch.countDown()
                        }
                    },
                )
                assertTrue(latch.await(5, TimeUnit.SECONDS))
                assertEquals("COVER-ASYNC-CF", asyncBody.get())
                assertEquals(0, systemOrigin.requestCount)

                // 3. RelayHttpTransport.getBytes() routes wenku8.app / wenku8.mewx.org version check via pinned edge!
                pinnedEdge.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Server", "cloudflare")
                        .body("73")
                        .build(),
                )
                val relayTransport = RelayHttpTransport(
                    directGetCallFactory = factory,
                    systemDns = fakeSystemDns,
                )
                val versionBytes = relayTransport.getBytes("http://wenku8.app:$sharedPort/version")
                assertEquals("73", versionBytes?.toString(StandardCharsets.UTF_8))
                assertEquals(0, systemOrigin.requestCount)

                // 4. Direct numeric IP literal & POST requests bypass Cloudflare pinning cleanly
                systemOrigin.enqueue(MockResponse.Builder().code(200).body("DIRECT-IP").build())
                factory.newCall(
                    Request.Builder()
                        .url("http://127.0.0.1:$sharedPort/resolve")
                        .get()
                        .build(),
                ).execute().use { resp ->
                    assertEquals("DIRECT-IP", resp.body.string())
                }

                systemOrigin.enqueue(MockResponse.Builder().code(200).body("POST-SYSTEM").build())
                factory.newCall(
                    Request.Builder()
                        .url("http://wenku8.app:$sharedPort/submit")
                        .post("data".toRequestBody("text/plain".toMediaType()))
                        .build(),
                ).execute().use { resp ->
                    assertEquals("POST-SYSTEM", resp.body.string())
                }
                assertEquals(2, systemOrigin.requestCount)

                // 5. Turning optimization OFF retires pinned pool and routes directly via System DNS
                val poolWhileActive = factory.activePinnedConnectionPoolForTesting()
                assertNotNull(poolWhileActive)
                activeEdgeRef.set(null)

                systemOrigin.enqueue(MockResponse.Builder().code(200).body("SYSTEM-AFTER-OFF").build())
                factory.newCall(
                    Request.Builder()
                        .url("http://wenku8.mewx.org:$sharedPort/args/notice_sc")
                        .get()
                        .build(),
                ).execute().use { resp ->
                    assertEquals("SYSTEM-AFTER-OFF", resp.body.string())
                }
                assertNull(factory.activePinnedConnectionPoolForTesting())
            }
        }
    }

    @Test
    fun fallsBackTransparentlyToSystemDnsOnCfRoutingErrorAndIsolatesPerHostBypass() {
        MockWebServer().use { systemOrigin ->
            MockWebServer().use { pinnedEdge ->
                systemOrigin.start(InetAddress.getByName("127.0.0.1"), 0)
                val sharedPort = systemOrigin.port
                pinnedEdge.start(InetAddress.getByName("127.0.0.2"), sharedPort)

                val pinnedBytes = IpLiteralParser.parseNumericIpBytes("127.0.0.2", requirePublicUnicast = false)!!
                val systemBytes = IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false)!!
                val monoClock = AtomicLong(100_000L)

                val activeEdgeRef = AtomicReference(
                    ActiveCloudflareEdgeRoute(
                        generationId = 1L,
                        candidateId = "cf-v4-edge",
                        rawIpBytes = pinnedBytes,
                    ),
                )

                val fakeSystemDns = Dns { host ->
                    listOf(InetAddress.getByAddress(host, systemBytes))
                }

                val factory = CloudflareAcceleratedCallFactory(
                    edgeRouteProvider = { activeEdgeRef.get() },
                    systemDns = fakeSystemDns,
                    monotonicClockMillis = { monoClock.get() },
                )

                // Scenario A: Redirect hop 1 (`cdn.wenku8.app`) succeeds on CF, hop 2 (`oss.non-cf.example`)
                // gets Cloudflare Error 530 (host not on CF) -> transparently retries via System DNS and succeeds,
                // bypassing ONLY `oss.non-cf.example` while keeping `cdn.wenku8.app` accelerated!
                pinnedEdge.enqueue(
                    MockResponse.Builder()
                        .code(302)
                        .addHeader("Server", "cloudflare")
                        .addHeader("Location", "http://oss.non-cf.example:$sharedPort/photo.jpg")
                        .build(),
                )
                pinnedEdge.enqueue(
                    MockResponse.Builder()
                        .code(530)
                        .addHeader("Server", "cloudflare")
                        .body("Error 1016")
                        .build(),
                )
                systemOrigin.enqueue(
                    MockResponse.Builder()
                        .code(302)
                        .addHeader("Location", "http://oss.non-cf.example:$sharedPort/photo.jpg")
                        .build(),
                )
                systemOrigin.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Type", "image/jpeg")
                        .body("NON-CF-IMAGE-OK")
                        .build(),
                )

                factory.newCall(
                    Request.Builder()
                        .url("http://cdn.wenku8.app:$sharedPort/pictures/9/9/9.jpg")
                        .get()
                        .build(),
                ).execute().use { resp ->
                    assertEquals(200, resp.code)
                    assertEquals("NON-CF-IMAGE-OK", resp.body.string())
                }

                assertTrue(
                    "Non-CF redirect target must be bypassed after CF 530",
                    factory.isHostCurrentlyBypassed("oss.non-cf.example"),
                )
                assertFalse(
                    "Healthy CF redirector cdn.wenku8.app must NOT be bypassed",
                    factory.isHostCurrentlyBypassed("cdn.wenku8.app"),
                )

                // Subsequent direct request to bypassed `oss.non-cf.example` skips pinnedEdge immediately!
                val pinnedCountBefore = pinnedEdge.requestCount
                systemOrigin.enqueue(MockResponse.Builder().code(200).body("DIRECT-BYPASS-HIT").build())
                factory.newCall(
                    Request.Builder()
                        .url("http://oss.non-cf.example:$sharedPort/photo2.jpg")
                        .get()
                        .build(),
                ).execute().use { resp ->
                    assertEquals("DIRECT-BYPASS-HIT", resp.body.string())
                }
                assertEquals(pinnedCountBefore, pinnedEdge.requestCount)

                // Scenario B: Real origin 404 on Cloudflare does NOT trigger fallback or bypass
                val systemCountBefore404 = systemOrigin.requestCount
                pinnedEdge.enqueue(
                    MockResponse.Builder()
                        .code(404)
                        .addHeader("Server", "cloudflare")
                        .body("Not Found")
                        .build(),
                )
                factory.newCall(
                    Request.Builder()
                        .url("http://img.wenku8.com:$sharedPort/image/9/999999/999999s.jpg")
                        .get()
                        .build(),
                ).execute().use { resp ->
                    assertEquals(404, resp.code)
                }
                assertEquals("Genuine 404 must not trigger redundant System DNS retry", systemCountBefore404, systemOrigin.requestCount)
                assertFalse(factory.isHostCurrentlyBypassed("img.wenku8.com"))

                // Scenario C: Dead/unbound pinned IP causes IOException -> async enqueue() falls back to System DNS!
                val deadBytes = IpLiteralParser.parseNumericIpBytes("127.0.0.3", requirePublicUnicast = false)!!
                val oldPool = factory.activePinnedConnectionPoolForTesting()
                activeEdgeRef.set(
                    ActiveCloudflareEdgeRoute(
                        generationId = 2L,
                        candidateId = "cf-dead-edge",
                        rawIpBytes = deadBytes,
                    ),
                )

                systemOrigin.enqueue(MockResponse.Builder().code(200).body("ASYNC-FALLBACK-OK").build())
                val latch = CountDownLatch(1)
                val fallbackBody = AtomicReference<String?>(null)
                factory.newCall(
                    Request.Builder()
                        .url("http://img.wenku8.com:$sharedPort/image/3/3027/3027s.jpg")
                        .get()
                        .build(),
                ).enqueue(
                    object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            latch.countDown()
                        }

                        override fun onResponse(call: Call, response: Response) {
                            response.use { fallbackBody.set(it.body.string()) }
                            latch.countDown()
                        }
                    },
                )
                assertTrue(latch.await(5, TimeUnit.SECONDS))
                assertEquals("ASYNC-FALLBACK-OK", fallbackBody.get())
                assertNotSame(oldPool, factory.activePinnedConnectionPoolForTesting())
            }
        }
    }

    @Test
    fun relayRouteControllerExposesActiveEdgeIpOnlyWhenEnabledWhileKeepingRelayPostScopeStrict() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
        try {
            val dsFile = File(tempFolder.newFolder("ds"), "edge_provider.preferences_pb")
            val store = RoutingLocalStore(
                PreferenceDataStoreFactory.create(scope = scope, produceFile = { dsFile }),
            )
            val nowWall = 1_762_740_000_000L
            val monoClock = AtomicLong(10_000L)
            val networkFlow = MutableStateFlow(
                NetworkSnapshot(
                    internalNetworkHandleKey = "wifi-1",
                    isConnected = true,
                    underlyingTransports = setOf(UnderlyingTransportKind.WIFI),
                    isMetered = false,
                ),
            )
            val observer = object : NetworkConnectivityObserver {
                override fun currentSnapshot(): NetworkSnapshot = networkFlow.value
                override fun observeDefaultNetworkSnapshots(): Flow<NetworkSnapshot> = networkFlow
            }
            val endpoint = (
                DiagnosticsEndpointConfig.parseAndValidate("https://wenku8-relay.mewx.org/")
                    as DiagnosticsEndpointResolution.Available
                ).endpoint
            val manifestRepo = ManifestRepository(
                localStore = store,
                wallClockEpochMillis = { nowWall },
                monotonicClockMillis = { monoClock.get() },
            )
            manifestRepo.ingestManifestPayload(
                rawJson = """
                    {
                      "schema": "wenku8-ip-candidates-v1",
                      "revision": "rev-edge-test",
                      "hostname": "wenku8-relay.mewx.org",
                      "enabled": true,
                      "minAppVersionCode": 0,
                      "rolloutPermille": 1000,
                      "issuedAt": "2025-11-10T00:00:00Z",
                      "expiresAt": "2025-11-10T12:00:00Z",
                      "candidates": [
                        { "id": "cf-v4-edge-1", "ip": "188.114.97.42" }
                      ]
                    }
                """.trimIndent(),
                expectedRelayHostname = "wenku8-relay.mewx.org",
            )

            val probeTransport = object : DiagnosticsProbeTransport {
                override suspend fun executeProbe(
                    endpoint: ValidatedRelayEndpoint,
                    armConfig: ProbeArmConfig,
                    sampleIndex: Int,
                    explicitNonce: String?,
                ): ProbeSampleResult {
                    val candidateId = (armConfig.dnsPolicy as? ProbeDnsPolicy.PinnedCandidate)?.candidateId
                    val isWarm = armConfig.armId.startsWith("discover_warm:")
                    return ProbeSampleResult(
                        sampleIndex = sampleIndex,
                        armId = armConfig.armId,
                        candidateId = candidateId,
                        succeeded = true,
                        httpStatusCode = 200,
                        retryAfterSeconds = null,
                        errorCategory = null,
                        fullResponseElapsedMs = if (isWarm) 65L else 78L,
                        responseHeadersStartMs = 50L,
                        dnsDurationMs = null,
                        tcpConnectDurationMs = if (isWarm) null else 20L,
                        tlsHandshakeDurationMs = if (isWarm) null else 25L,
                        connectionReused = isWarm,
                        connectAttemptCount = if (isWarm) 0 else 1,
                        observedProxyType = Proxy.Type.DIRECT,
                        localSocketFamily = IpAddressFamily.IPV4,
                        negotiatedProtocol = "h2",
                        responseBodyBytesRead = 180,
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
                            transportRttMs = 20L,
                            transportRttSource = "tcp",
                            handlerElapsedMs = 1L,
                        ),
                        observedEvents = listOf("callStart", "connectionAcquired", "callEnd"),
                    )
                }
            }

            val controller = RelayRouteController(
                applicationScope = scope,
                endpointResolutionProvider = { DiagnosticsEndpointResolution.Available(endpoint) },
                probeTransport = probeTransport,
                manifestRepository = manifestRepo,
                routingLocalStore = store,
                networkObserver = observer,
                autoProbeGapMillis = 0L,
                initialAutoOptimizeEnabled = false,
                allowAutoSelectOnEnable = true,
                monotonicClockMillis = { monoClock.get() },
                wallClockEpochMillis = { nowWall },
            )

            // Disabled -> null edge route even after recording candidate
            controller.discoverAndRecordPreferredCandidate(
                applyIfEnabled = true,
                invokedFromManualCheck = true,
                userInitiated = true,
            )
            assertNull(controller.currentActiveEdgeRouteOrNull())

            // Toggle ON -> exposes active Cloudflare edge IP for images/notices/version checks
            controller.onUserToggledAutoOptimize(true)
            val edgeRoute = controller.currentActiveEdgeRouteOrNull()
            assertNotNull(edgeRoute)
            assertEquals("cf-v4-edge-1", edgeRoute!!.candidateId)
            assertEquals("188.114.97.42", InetAddress.getByAddress(edgeRoute.rawIpBytes).hostAddress)

            // Sealed POST relay route remains strictly scoped to relay hostnames
            assertNotNull(controller.currentRouteForHost("wenku8-relay.mewx.org").pinnedCandidate)
            assertNull(controller.currentRouteForHost("pic.wenku8.com").pinnedCandidate)
            assertNull(controller.currentRouteForHost("wenku8.mewx.org").pinnedCandidate)

            // Toggle OFF -> edge route immediately clears
            controller.onUserToggledAutoOptimize(false)
            assertNull(controller.currentActiveEdgeRouteOrNull())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun midBodyTruncationOnPinnedIpv6Edge_tripsIpv6Breaker_fallsBackToSystemDns_andIgnoresCanceledCalls() =
        runBlocking {
            MockWebServer().use { systemOrigin ->
                MockWebServer().use { pinnedEdge ->
                    systemOrigin.start(InetAddress.getByName("127.0.0.1"), 0)
                    val sharedPort = systemOrigin.port
                    pinnedEdge.start(InetAddress.getByName("127.0.0.2"), sharedPort)

                    // 16-byte IPv4-mapped IPv6 address `::ffff:127.0.0.2`: treated as IpAddressFamily.IPV6
                    // while dialing local MockWebServer on 127.0.0.2 even on IPv4-only CI hosts!
                    val pinnedIpv6MappedBytes = byteArrayOf(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF.toByte(), 0xFF.toByte(), 127, 0, 0, 2,
                    )
                    val systemBytes = IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false)!!
                    val monoClock = AtomicLong(200_000L)

                    val activeEdgeRef = AtomicReference(
                        ActiveCloudflareEdgeRoute(
                            generationId = 99L,
                            candidateId = "cf-v6-edge-flaky",
                            rawIpBytes = pinnedIpv6MappedBytes,
                        ),
                    )
                    val fakeSystemDns = Dns { host ->
                        listOf(InetAddress.getByAddress(host, systemBytes))
                    }
                    val factory = CloudflareAcceleratedCallFactory(
                        edgeRouteProvider = { activeEdgeRef.get() },
                        systemDns = fakeSystemDns,
                        monotonicClockMillis = { monoClock.get() },
                    )

                    assertEquals(
                        IpAddressFamily.IPV6,
                        factory.activePinnedFamilyForHostOrNull("pic.777743.xyz"),
                    )
                    assertFalse(factory.isIpv6EdgeCurrentlyBypassed())

                    // 1. Pinned IPv6 edge sends HTTP 200 headers with Content-Length: 4096 but closes mid-body!
                    //    RelayHttpTransport.getBytes() must detect the mid-body truncation, trip the IPv6
                    //    bulk breaker, and transparently succeed via System DNS on the same call!
                    pinnedEdge.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Server", "cloudflare")
                            .addHeader("Content-Type", "image/jpeg")
                            .body("TRUNCATED")
                            .setHeader("Content-Length", "4096")
                            .setHeader("Connection", "close")
                            .build(),
                    )
                    systemOrigin.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Content-Type", "image/jpeg")
                            .body("FULL-IMAGE-VIA-SYSTEM-DNS")
                            .build(),
                    )

                    val relayTransport = RelayHttpTransport(
                        directGetCallFactory = factory,
                        systemDns = fakeSystemDns,
                    )
                    val downloaded = relayTransport.getBytes("http://pic.777743.xyz:$sharedPort/3/3770/1.jpg")
                    assertEquals("FULL-IMAGE-VIA-SYSTEM-DNS", downloaded?.toString(StandardCharsets.UTF_8))
                    assertEquals(1, pinnedEdge.requestCount)
                    assertEquals(1, systemOrigin.requestCount)
                    assertTrue(
                        "Single mid-body failure on pinned IPv6 edge must trip 30-min IPv6 bulk bypass",
                        factory.isIpv6EdgeCurrentlyBypassed(),
                    )
                    assertNull(factory.activePinnedFamilyForHostOrNull("pic.777743.xyz"))

                    // 2. Subsequent Coil/image GETs immediately route via System DNS while IPv6 cooldown is active
                    systemOrigin.enqueue(MockResponse.Builder().code(200).body("COIL-SYSTEM-DIRECT").build())
                    factory.newCall(
                        Request.Builder()
                            .url("http://img.wenku8.com:$sharedPort/image/3/3770/3770s.jpg")
                            .get()
                            .build(),
                    ).execute().use { resp ->
                        assertEquals("COIL-SYSTEM-DIRECT", resp.body.string())
                    }
                    assertEquals(1, pinnedEdge.requestCount)
                    assertEquals(2, systemOrigin.requestCount)

                    // 3. After 30-min cooldown expires, IPv6 pinning re-enables; canceling a call mid-body
                    //    MUST NOT trip the IPv6 breaker!
                    monoClock.addAndGet(CloudflareAcceleratedCallFactory.DEFAULT_IPV6_BULK_BYPASS_MS + 1_000L)
                    assertFalse(factory.isIpv6EdgeCurrentlyBypassed())

                    pinnedEdge.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Server", "cloudflare")
                            .body("PARTIAL-BEFORE-CANCEL")
                            .setHeader("Content-Length", "4096")
                            .setHeader("Connection", "close")
                            .build(),
                    )
                    val cancelableCall = factory.newCall(
                        Request.Builder()
                            .url("http://pic.777743.xyz:$sharedPort/3/3770/2.jpg")
                            .get()
                            .build(),
                    )
                    cancelableCall.execute().use { resp ->
                        cancelableCall.cancel()
                        runCatching { resp.body.bytes() }
                    }
                    assertFalse(
                        "User-canceled call must not falsely trip IPv6 bulk breaker",
                        factory.isIpv6EdgeCurrentlyBypassed(),
                    )
                }
            }
        }

    @Test
    fun relayRouteControllerDiscovery_prefersComparableIpv4OverIpv6_andKeepsIpv6WhenIpv4SlowerOrFailed() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            try {
                val dsFile = File(tempFolder.newFolder("ds_v4pref"), "v4pref.preferences_pb")
                val store = RoutingLocalStore(
                    PreferenceDataStoreFactory.create(scope = scope, produceFile = { dsFile }),
                )
                val nowWall = 1_762_740_000_000L
                val monoClock = AtomicLong(10_000L)
                val networkFlow = MutableStateFlow(
                    NetworkSnapshot(
                        internalNetworkHandleKey = "cell-5g-vivo",
                        isConnected = true,
                        underlyingTransports = setOf(UnderlyingTransportKind.CELLULAR),
                        isMetered = true,
                    ),
                )
                val observer = object : NetworkConnectivityObserver {
                    override fun currentSnapshot(): NetworkSnapshot = networkFlow.value
                    override fun observeDefaultNetworkSnapshots(): Flow<NetworkSnapshot> = networkFlow
                }
                val endpoint = (
                    DiagnosticsEndpointConfig.parseAndValidate("https://wenku8-relay.mewx.org/")
                        as DiagnosticsEndpointResolution.Available
                    ).endpoint
                val manifestRepo = ManifestRepository(
                    localStore = store,
                    wallClockEpochMillis = { nowWall },
                    monotonicClockMillis = { monoClock.get() },
                )
                manifestRepo.ingestManifestPayload(
                    rawJson = """
                        {
                          "schema": "wenku8-ip-candidates-v1",
                          "revision": "rev-v4-tiebreak",
                          "hostname": "wenku8-relay.mewx.org",
                          "enabled": true,
                          "minAppVersionCode": 0,
                          "rolloutPermille": 1000,
                          "issuedAt": "2025-11-10T00:00:00Z",
                          "expiresAt": "2025-11-10T12:00:00Z",
                          "candidates": [
                            { "id": "cf-v6-fast", "ip": "2606:4700::6810:84e5" },
                            { "id": "cf-v4-near", "ip": "104.16.132.229" }
                          ]
                        }
                    """.trimIndent(),
                    expectedRelayHostname = "wenku8-relay.mewx.org",
                )

                var v4LatencyMs = 110L // +30ms slower than v6 (80ms), within 40ms margin -> prefer IPv4!
                var v4Succeeded = true
                val probeTransport = object : DiagnosticsProbeTransport {
                    override suspend fun executeProbe(
                        endpoint: ValidatedRelayEndpoint,
                        armConfig: ProbeArmConfig,
                        sampleIndex: Int,
                        explicitNonce: String?,
                    ): ProbeSampleResult {
                        val candidateId = (armConfig.dnsPolicy as? ProbeDnsPolicy.PinnedCandidate)?.candidateId
                        val isWarm = armConfig.armId.startsWith("discover_warm:")
                        val isV4 = candidateId == "cf-v4-near"
                        return ProbeSampleResult(
                            sampleIndex = sampleIndex,
                            armId = armConfig.armId,
                            candidateId = candidateId,
                            succeeded = if (isV4) v4Succeeded else true,
                            httpStatusCode = if (isV4 && !v4Succeeded) null else 200,
                            retryAfterSeconds = null,
                            errorCategory = null,
                            fullResponseElapsedMs = if (isV4) v4LatencyMs else 80L,
                            responseHeadersStartMs = 40L,
                            dnsDurationMs = null,
                            tcpConnectDurationMs = if (isWarm) null else 20L,
                            tlsHandshakeDurationMs = if (isWarm) null else 20L,
                            connectionReused = isWarm,
                            connectAttemptCount = if (isWarm) 0 else 1,
                            observedProxyType = Proxy.Type.DIRECT,
                            localSocketFamily = if (isV4) IpAddressFamily.IPV4 else IpAddressFamily.IPV6,
                            negotiatedProtocol = "h2",
                            responseBodyBytesRead = 180,
                            bodyComplete = true,
                            serverPayload = if (isV4 && !v4Succeeded) {
                                null
                            } else {
                                ParsedServerProbePayload(
                                    schema = OkHttpProbeTransport.PROBE_SCHEMA_V1,
                                    nonce = "0123456789abcdef0123456789abcdef",
                                    endpointRole = "placed-relay",
                                    ingressColo = "HKG",
                                    country = "CN",
                                    asn = 4134L,
                                    clientIpFamily = if (isV4) IpAddressFamily.IPV4 else IpAddressFamily.IPV6,
                                    httpProtocol = "HTTP/2",
                                    transportRttMs = 20L,
                                    transportRttSource = "tcp",
                                    handlerElapsedMs = 1L,
                                )
                            },
                            observedEvents = listOf("callStart", "connectionAcquired", "callEnd"),
                        )
                    }
                }

                val controller = RelayRouteController(
                    applicationScope = scope,
                    endpointResolutionProvider = { DiagnosticsEndpointResolution.Available(endpoint) },
                    probeTransport = probeTransport,
                    manifestRepository = manifestRepo,
                    routingLocalStore = store,
                    networkObserver = observer,
                    autoProbeGapMillis = 0L,
                    initialAutoOptimizeEnabled = true,
                    allowAutoSelectOnEnable = true,
                    monotonicClockMillis = { monoClock.get() },
                    wallClockEpochMillis = { nowWall },
                )

                // 1. IPv4 is 110ms vs IPv6 80ms (+30ms <= 40ms) -> selects IPv4 for cellular bulk stability!
                val pickedComparable = controller.discoverAndRecordPreferredCandidate(
                    applyIfEnabled = true,
                    invokedFromManualCheck = true,
                    userInitiated = true,
                )
                assertEquals("cf-v4-near", pickedComparable?.candidateId)
                assertEquals("104.16.132.229", pickedComparable?.ipLiteral)

                // 2. When IPv4 is much slower (260ms vs 80ms), IPv6 still wins cleanly!
                v4LatencyMs = 260L
                val pickedWhenV4Slow = controller.discoverAndRecordPreferredCandidate(
                    applyIfEnabled = true,
                    invokedFromManualCheck = true,
                    userInitiated = true,
                )
                assertEquals("cf-v6-fast", pickedWhenV4Slow?.candidateId)
                assertEquals("2606:4700::6810:84e5", pickedWhenV4Slow?.ipLiteral)

                // 3. When IPv4 fails completely, IPv6 still wins cleanly!
                v4Succeeded = false
                val pickedWhenV4Blocked = controller.discoverAndRecordPreferredCandidate(
                    applyIfEnabled = true,
                    invokedFromManualCheck = true,
                    userInitiated = true,
                )
                assertEquals("cf-v6-fast", pickedWhenV4Blocked?.candidateId)
            } finally {
                scope.cancel()
            }
        }

    @Test
    fun illustrationMirrorPool_usesOptionADirectFirst_sharesWinnerAcrossOldAndNewHosts_andSupportsDynamicPmMirrors() =
        runBlocking {
            MockWebServer().use { healthyServer ->
                MockWebServer().use { blockedServer ->
                    healthyServer.start(InetAddress.getByName("127.0.0.1"), 0)
                    val sharedPort = healthyServer.port
                    blockedServer.start(InetAddress.getByName("127.0.0.2"), sharedPort)

                    val healthyBytes = IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false)!!
                    val blockedBytes = IpLiteralParser.parseNumericIpBytes("127.0.0.2", requirePublicUnicast = false)!!
                    val monoClock = AtomicLong(500_000L)

                    val blockedHosts = mutableSetOf("pic.wenku8.cn", "pic.wenku8.com")
                    val activeEdgeRef = AtomicReference<ActiveCloudflareEdgeRoute?>(null)
                    val dynamicMirrorsRef = AtomicReference<List<WenkuImageUrlPolicy.PictureMirrorEndpoint>>(emptyList())
                    val perHostSystemDns = Dns { host ->
                        val ip = if (host.lowercase() in blockedHosts) blockedBytes else healthyBytes
                        listOf(InetAddress.getByAddress(host, ip))
                    }

                    val factory = CloudflareAcceleratedCallFactory(
                        edgeRouteProvider = { activeEdgeRef.get() },
                        systemDns = perHostSystemDns,
                        dynamicPictureMirrorsProvider = { dynamicMirrorsRef.get() },
                        monotonicClockMillis = { monoClock.get() },
                    )

                    // 1. Option A: BOTH `pic.wenku8.com` and legacy China-Mobile-blocked `pic.wenku8.cn`
                    //    go straight to `pic.777743.xyz/{path}` with ZERO hits to `blockedServer`!
                    healthyServer.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Content-Type", "image/jpeg")
                            .addHeader("Connection", "close")
                            .body("OPTION-A-FROM-COM")
                            .build(),
                    )
                    healthyServer.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Content-Type", "image/jpeg")
                            .addHeader("Connection", "close")
                            .body("OPTION-A-FROM-LEGACY-CN")
                            .build(),
                    )

                    factory.newCall(
                        Request.Builder()
                            .url("http://pic.wenku8.com:$sharedPort/pictures/3/3770/141001/1.jpg")
                            .get()
                            .build(),
                    ).execute().use { resp ->
                        assertEquals(200, resp.code)
                        assertEquals("OPTION-A-FROM-COM", resp.body.string())
                        assertEquals("pic.777743.xyz", resp.request.url.host)
                        assertEquals("/3/3770/141001/1.jpg", resp.request.url.encodedPath)
                    }
                    factory.newCall(
                        Request.Builder()
                            .url("http://pic.wenku8.cn:$sharedPort/pictures/1/1305/41759/50471.jpg")
                            .get()
                            .build(),
                    ).execute().use { resp ->
                        assertEquals(200, resp.code)
                        assertEquals("OPTION-A-FROM-LEGACY-CN", resp.body.string())
                        assertEquals("pic.777743.xyz", resp.request.url.host)
                        assertEquals("/1/1305/41759/50471.jpg", resp.request.url.encodedPath)
                    }
                    assertEquals("Blocked pic.wenku8.com/.cn must not be touched when Option A primary is healthy", 0, blockedServer.requestCount)
                    assertEquals(2, healthyServer.requestCount)

                    // 2. Bidirectional failover + shared pool memory: if `pic.777743.xyz` gets blocked,
                    //    falls over to direct `pic.wenku8.com/{path}` and remembers it globally across
                    //    BOTH `pic.wenku8.com` and legacy `pic.wenku8.cn` chapters!
                    blockedHosts.clear()
                    blockedHosts.add("pic.777743.xyz")
                    blockedHosts.add("pic.wenku8.cn")

                    blockedServer.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
                    healthyServer.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Content-Type", "image/jpeg")
                            .body("RESCUED-VIA-DIRECT-WENKU8-COM")
                            .build(),
                    )
                    factory.newCall(
                        Request.Builder()
                            .url("http://pic.777743.xyz:$sharedPort/3/3770/141001/2.jpg")
                            .get()
                            .build(),
                    ).execute().use { resp ->
                        assertEquals(200, resp.code)
                        assertEquals("RESCUED-VIA-DIRECT-WENKU8-COM", resp.body.string())
                        assertEquals("pic.wenku8.com", resp.request.url.host)
                        assertEquals("/3/3770/141001/2.jpg", resp.request.url.encodedPath)
                    }
                    assertEquals(
                        WenkuImageUrlPolicy.MIRROR_PIC_WENKU8_COM,
                        factory.currentPreferredIllustrationMirrorOrNull(),
                    )

                    // Subsequent async Coil enqueue() on a LEGACY `pic.wenku8.cn` chapter reuses the
                    // shared remembered winner (`pic.wenku8.com`) with ZERO hits to `blockedServer`!
                    val blockedCountBeforeSharedReuse = blockedServer.requestCount
                    healthyServer.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Content-Type", "image/jpeg")
                            .body("SHARED-POOL-ASYNC-HIT")
                            .build(),
                    )
                    val latch = CountDownLatch(1)
                    val asyncBody = AtomicReference<String?>(null)
                    factory.newCall(
                        Request.Builder()
                            .url("http://pic.wenku8.cn:$sharedPort/pictures/1/1305/41759/50472.jpg")
                            .get()
                            .build(),
                    ).enqueue(
                        object : Callback {
                            override fun onFailure(call: Call, e: IOException) = latch.countDown()
                            override fun onResponse(call: Call, response: Response) {
                                response.use { asyncBody.set(it.body.string()) }
                                latch.countDown()
                            }
                        },
                    )
                    assertTrue(latch.await(5, TimeUnit.SECONDS))
                    assertEquals("SHARED-POOL-ASYNC-HIT", asyncBody.get())
                    assertEquals(blockedCountBeforeSharedReuse, blockedServer.requestCount)

                    // 3. Two consecutive 404s (`pic.777743.xyz` + `pic.wenku8.com`) stop immediately
                    //    without touching legacy China-Mobile-blocked `pic.wenku8.cn`!
                    factory.clearHostBypasses()
                    blockedHosts.clear()
                    blockedHosts.add("pic.wenku8.cn")
                    val blockedBefore404 = blockedServer.requestCount
                    healthyServer.enqueue(
                        MockResponse.Builder()
                            .code(404)
                            .addHeader("Connection", "close")
                            .body("Not Found 1")
                            .build(),
                    )
                    healthyServer.enqueue(
                        MockResponse.Builder()
                            .code(404)
                            .addHeader("Connection", "close")
                            .body("Not Found 2")
                            .build(),
                    )
                    factory.newCall(
                        Request.Builder()
                            .url("http://pic.wenku8.com:$sharedPort/pictures/3/3770/141001/missing.jpg")
                            .get()
                            .build(),
                    ).execute().use { resp ->
                        assertEquals(404, resp.code)
                    }
                    assertEquals(
                        "2 consecutive 404s must stop without hitting China-Mobile-blocked pic.wenku8.cn",
                        blockedBefore404,
                        blockedServer.requestCount,
                    )

                    // 4. Dynamic `pm=` mirrors from `_network_cfg.wenku8.app` take priority ahead of defaults!
                    dynamicMirrorsRef.set(
                        listOf(
                            WenkuImageUrlPolicy.PictureMirrorEndpoint("pic.newcdn.xyz", usesPicturesPrefix = false),
                        ),
                    )
                    healthyServer.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Content-Type", "image/jpeg")
                            .body("DYNAMIC-PM-MIRROR-OK")
                            .build(),
                    )
                    factory.newCall(
                        Request.Builder()
                            .url("http://pic.wenku8.com:$sharedPort/pictures/3/3770/141001/dynamic.jpg")
                            .get()
                            .build(),
                    ).execute().use { resp ->
                        assertEquals(200, resp.code)
                        assertEquals("DYNAMIC-PM-MIRROR-OK", resp.body.string())
                        assertEquals("pic.newcdn.xyz", resp.request.url.host)
                        assertEquals("/3/3770/141001/dynamic.jpg", resp.request.url.encodedPath)
                    }

                    // 5. When Cloudflare IPv6 edge is active and BOTH pinned & System DNS fail on
                    //    primary mirror `pic.777743.xyz`, IPv6 breaker MUST NOT falsely trip, so
                    //    fallback `pic.wenku8.com/{path}` still rides the healthy pinned IPv6 edge!
                    dynamicMirrorsRef.set(emptyList())
                    factory.clearHostBypasses()
                    blockedHosts.clear()
                    blockedHosts.add("pic.777743.xyz")
                    val pinnedIpv6Healthy = byteArrayOf(
                        0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF.toByte(), 0xFF.toByte(), 127, 0, 0, 1,
                    )
                    activeEdgeRef.set(
                        ActiveCloudflareEdgeRoute(
                            generationId = 55L,
                            candidateId = "cf-v6-healthy",
                            rawIpBytes = pinnedIpv6Healthy,
                        ),
                    )
                    // Attempt 0 (pic.777743.xyz): pinned (127.0.0.1) resets AND System DNS (127.0.0.2) resets
                    healthyServer.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
                    blockedServer.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.CloseSocket()).build())
                    // Attempt 1 (pic.wenku8.com/{path}): succeeds right on the healthy pinned IPv6 edge!
                    healthyServer.enqueue(
                        MockResponse.Builder()
                            .code(200)
                            .addHeader("Content-Type", "image/jpeg")
                            .body("MIRROR-OK-VIA-PINNED-IPV6")
                            .build(),
                    )
                    factory.newCall(
                        Request.Builder()
                            .url("http://pic.wenku8.com:$sharedPort/pictures/3/3770/141001/5.jpg")
                            .get()
                            .build(),
                    ).execute().use { resp ->
                        assertEquals(200, resp.code)
                        assertEquals("MIRROR-OK-VIA-PINNED-IPV6", resp.body.string())
                    }
                    assertFalse(
                        "Double-route RST on blocked mirror must not trip IPv6 breaker",
                        factory.isIpv6EdgeCurrentlyBypassed(),
                    )
                    assertEquals(
                        WenkuImageUrlPolicy.MIRROR_PIC_WENKU8_COM,
                        factory.currentPreferredIllustrationMirrorOrNull(),
                    )
                    assertEquals(
                        IpAddressFamily.IPV6,
                        factory.activePinnedFamilyForHostOrNull("pic.wenku8.cn"),
                    )
                }
            }
        }
}
