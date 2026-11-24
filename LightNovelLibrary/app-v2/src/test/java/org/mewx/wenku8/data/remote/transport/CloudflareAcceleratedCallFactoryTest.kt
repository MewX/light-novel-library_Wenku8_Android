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

                // 1. Multi-hop illustration redirect: pic.wenku8.com -> pic.777743.xyz both hit pinned edge!
                pinnedEdge.enqueue(
                    MockResponse.Builder()
                        .code(302)
                        .addHeader("Server", "cloudflare")
                        .addHeader("Location", "http://pic.777743.xyz:$sharedPort/1/1305/41759/50471.jpg")
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
                        .url("http://pic.wenku8.com:$sharedPort/pictures/1/1305/41759/50471.jpg")
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
                assertTrue(hop1.headers["Host"].orEmpty().startsWith("pic.wenku8.com"))
                assertTrue(hop2.headers["Host"].orEmpty().startsWith("pic.777743.xyz"))

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

                // Scenario A: Redirect hop 1 (`pic.wenku8.com`) succeeds on CF, hop 2 (`oss.non-cf.example`)
                // gets Cloudflare Error 530 (host not on CF) -> transparently retries via System DNS and succeeds,
                // bypassing ONLY `oss.non-cf.example` while keeping `pic.wenku8.com` accelerated!
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
                        .url("http://pic.wenku8.com:$sharedPort/pictures/9/9/9.jpg")
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
                    "Healthy CF redirector pic.wenku8.com must NOT be bypassed",
                    factory.isHostCurrentlyBypassed("pic.wenku8.com"),
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
}
