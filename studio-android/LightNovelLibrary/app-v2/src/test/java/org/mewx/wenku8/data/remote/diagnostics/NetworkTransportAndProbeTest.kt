package org.mewx.wenku8.data.remote.diagnostics

import android.content.ContentValues
import android.os.Looper
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.SocketEffect
import okhttp3.Call
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.Request
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.data.remote.transport.ActiveRelayRoute
import org.mewx.wenku8.data.remote.transport.PinnedRelayCandidate
import org.mewx.wenku8.data.remote.transport.RelayHttpTransport
import org.mewx.wenku8.data.remote.transport.RelayRouteProvider
import org.mewx.wenku8.data.remote.transport.WenkuSessionCookieStore
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NetworkTransportAndProbeTest {

    @Test
    fun ipLiteralParserAcceptsPublicUnicastAndRejectsPrivateScopedOrMalformedInputs() {
        val cfIpv4 = IpLiteralParser.parseNumericIpBytes("104.16.132.229")
        assertNotNull(cfIpv4)
        assertArrayEquals(
            byteArrayOf(104.toByte(), 16.toByte(), 132.toByte(), 229.toByte()),
            cfIpv4,
        )

        val cfIpv6 = IpLiteralParser.parseNumericIpBytes("2606:4700::6810:84e5")
        assertNotNull(cfIpv6)
        assertEquals(16, cfIpv6!!.size)

        val pinned = IpLiteralParser.toPinnedInetAddress(
            relayHostname = "wenku8-relay.mewx.org",
            rawLiteral = "172.67.10.25",
        )
        assertNotNull(pinned)
        assertEquals("wenku8-relay.mewx.org", pinned!!.hostName)

        // Rejected private / special / malformed inputs
        val rejectedInputs = listOf(
            "",
            "Unknown",
            "wenku8-relay.mewx.org",
            "010.1.1.1",
            "256.0.0.1",
            "127.0.0.1",
            "0.0.0.0",
            "10.0.0.1",
            "100.64.1.1",
            "169.254.10.1",
            "172.16.0.1",
            "172.31.255.254",
            "192.168.1.1",
            "192.0.2.1",
            "198.18.0.1",
            "198.51.100.5",
            "203.0.113.9",
            "224.0.0.1",
            "255.255.255.255",
            "104.16.132.229:443",
            "104.16.132.0/24",
            "::",
            "::1",
            "::ffff:104.16.132.229",
            "64:ff9b::104.16.132.229",
            "fe80::1",
            "fe80::1%eth0",
            "fd00::1",
            "ff02::1",
            "2001:db8::1",
            "[2606:4700::1]",
            "2606::4700::1",
            ":2606:4700::1",
            "2606:4700::1:",
        )
        for (input in rejectedInputs) {
            assertNull("Expected rejection for '$input'", IpLiteralParser.parseNumericIpBytes(input))
        }

        // Loopback allowed ONLY when explicitly requested for local MockWebServer testing
        assertNotNull(IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false))
        assertNotNull(IpLiteralParser.parseNumericIpBytes("::1", requirePublicUnicast = false))
    }

    @Test
    fun diagnosticsEndpointConfigValidatesRelayBaseUrlAndRejectsStubOrUnsafeUris() {
        assertTrue(
            DiagnosticsEndpointConfig.parseAndValidate(null) is DiagnosticsEndpointResolution.Unavailable,
        )
        assertTrue(
            DiagnosticsEndpointConfig.parseAndValidate("Unknown") is DiagnosticsEndpointResolution.Unavailable,
        )
        assertTrue(
            DiagnosticsEndpointConfig.parseAndValidate("http://wenku8-relay.mewx.org/")
                is DiagnosticsEndpointResolution.Unavailable,
        )
        assertTrue(
            DiagnosticsEndpointConfig.parseAndValidate("https://user:pass@wenku8-relay.mewx.org/")
                is DiagnosticsEndpointResolution.Unavailable,
        )
        assertTrue(
            DiagnosticsEndpointConfig.parseAndValidate("https://wenku8-relay.mewx.org/?foo=1")
                is DiagnosticsEndpointResolution.Unavailable,
        )
        assertTrue(
            DiagnosticsEndpointConfig.parseAndValidate("https://wenku8-relay.mewx.org/#frag")
                is DiagnosticsEndpointResolution.Unavailable,
        )

        val withSlash = DiagnosticsEndpointConfig.parseAndValidate("https://wenku8-relay.mewx.org/")
            as DiagnosticsEndpointResolution.Available
        val withoutSlash = DiagnosticsEndpointConfig.parseAndValidate("https://wenku8-relay.mewx.org")
            as DiagnosticsEndpointResolution.Available

        assertEquals(
            "https://wenku8-relay.mewx.org/__diagnostics/latency",
            withSlash.endpoint.latencyProbeUrl,
        )
        assertEquals(
            "https://wenku8-relay.mewx.org/__diagnostics/latency",
            withoutSlash.endpoint.latencyProbeUrl,
        )
        assertEquals(
            "https://wenku8-relay.mewx.org/__diagnostics/ip-candidates",
            withSlash.endpoint.bootstrapManifestMirrorUrl,
        )
    }

    @Test
    fun probeJsonContractHandlesExplicitNullsAbsentKeysLiteralNullStringsAndWrongTypes() {
        val validNonce = "0123456789abcdef0123456789abcdef"

        // Explicit JSONObject.NULL fields must stay Kotlin null (never "null" or 0)
        val explicitNullJson = JSONObject()
            .put("schema", "wenku8-network-probe-v1")
            .put("nonce", validNonce)
            .put("endpointRole", "placed-relay")
            .put("ingressColo", JSONObject.NULL)
            .put("country", JSONObject.NULL)
            .put("asn", JSONObject.NULL)
            .put("clientIpFamily", "ipv6")
            .put("httpProtocol", "HTTP/2")
            .put("transportRttMs", JSONObject.NULL)
            .put("transportRttSource", JSONObject.NULL)
            .put("handlerElapsedMs", 0)
            .toString()

        val parsed = OkHttpProbeTransport.parseAndValidateProbeJson(explicitNullJson, validNonce)
        assertNotNull(parsed)
        assertNull(parsed!!.ingressColo)
        assertNull(parsed.country)
        assertNull(parsed.asn)
        assertNull(parsed.transportRttMs)
        assertEquals(IpAddressFamily.IPV6, parsed.clientIpFamily)
        assertEquals(0L, parsed.handlerElapsedMs)

        // Literal string "null" in location/colo is rejected
        val literalNullStringJson = JSONObject(explicitNullJson)
            .put("ingressColo", "null")
            .toString()
        assertNull(OkHttpProbeTransport.parseAndValidateProbeJson(literalNullStringJson, validNonce))

        // Zero RTT is rejected (RTT must be positive or null)
        val zeroRttJson = JSONObject(explicitNullJson)
            .put("transportRttMs", 0)
            .toString()
        assertNull(OkHttpProbeTransport.parseAndValidateProbeJson(zeroRttJson, validNonce))

        // Wrong type (boolean in numeric field) is rejected
        val wrongTypeJson = JSONObject(explicitNullJson)
            .put("asn", true)
            .toString()
        assertNull(OkHttpProbeTransport.parseAndValidateProbeJson(wrongTypeJson, validNonce))

        // Nonce mismatch is rejected
        assertNull(
            OkHttpProbeTransport.parseAndValidateProbeJson(
                explicitNullJson,
                "ffffffffffffffffffffffffffffffff",
            ),
        )
    }

    @Test
    fun okHttpProbeTransportDistinguishesColdVsReusedSocketAndMissingIpv6() = runBlocking {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val endpoint = (
                DiagnosticsEndpointConfig.parseAndValidate(
                    rawBaseUrl = "http://wenku8-relay.mewx.org:${server.port}/",
                    allowCleartextForLocalTests = true,
                ) as DiagnosticsEndpointResolution.Available
                ).endpoint

            val fakeDns = Dns { host ->
                if (host == "wenku8-relay.mewx.org") {
                    listOf(
                        InetAddress.getByAddress(
                            host,
                            IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false)!!,
                        ),
                    )
                } else {
                    throw UnknownHostException(host)
                }
            }

            val nonce1 = "11111111111111111111111111111111"
            val nonce2 = "22222222222222222222222222222222"

            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Set-Cookie", "PHPSESSID=should_be_ignored;")
                    .body("""{"schema":"wenku8-network-probe-v1","nonce":"$nonce1","ingressColo":"SYD","country":"AU","asn":13335,"clientIpFamily":"ipv4","httpProtocol":"HTTP/1.1","transportRttMs":18,"transportRttSource":"tcp","handlerElapsedMs":1}""")
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body("""{"schema":"wenku8-network-probe-v1","nonce":"$nonce2","ingressColo":"SYD","country":"AU","asn":13335,"clientIpFamily":"ipv4","httpProtocol":"HTTP/1.1","transportRttMs":16,"transportRttSource":"tcp","handlerElapsedMs":0}""")
                    .build(),
            )
            server.enqueue(
                MockResponse.Builder()
                    .code(429)
                    .addHeader("Retry-After", "15")
                    .body("rate limited")
                    .build(),
            )

            val sharedPool = ConnectionPool(5, 60, TimeUnit.SECONDS)
            val probeTransport = OkHttpProbeTransport(systemDns = fakeDns)
            val arm = ProbeArmConfig(
                armId = "test-arm",
                dnsPolicy = ProbeDnsPolicy.SystemDefault,
                connectionPool = sharedPool,
            )

            // Sample #01: Cold connection
            val first = probeTransport.executeProbe(endpoint, arm, sampleIndex = 1, explicitNonce = nonce1)
            assertTrue(first.succeeded)
            assertFalse(first.connectionReused)
            assertEquals(1, first.connectAttemptCount)
            assertEquals("SYD", first.serverPayload?.ingressColo)
            assertEquals(IpAddressFamily.IPV4, first.localSocketFamily)

            val req1 = server.takeRequest()
            assertEquals("/__diagnostics/latency", req1.target)
            assertEquals("identity", req1.headers["Accept-Encoding"])
            assertNull(req1.headers["Cookie"])

            // Sample #02: Reused socket on same ConnectionPool (`connectionAcquired` without `connectStart`)
            val second = probeTransport.executeProbe(endpoint, arm, sampleIndex = 2, explicitNonce = nonce2)
            assertTrue(second.succeeded)
            assertTrue(second.connectionReused)
            assertEquals(0, second.connectAttemptCount)
            val req2 = server.takeRequest()
            assertNull("Probe transport must never send cookies", req2.headers["Cookie"])

            // Sample #03: HTTP 429 rate-limited with Retry-After
            val rateLimited = probeTransport.executeProbe(endpoint, arm, sampleIndex = 3)
            assertFalse(rateLimited.succeeded)
            assertEquals(429, rateLimited.httpStatusCode)
            assertEquals(15L, rateLimited.retryAfterSeconds)
            assertEquals(ProbeErrorCategory.HTTP_RATE_LIMITED, rateLimited.errorCategory)

            // Sample #04: IPv6-only against IPv4-only DNS -> ADDRESS_FAMILY_UNAVAILABLE (never 0 ms success)
            val ipv6Missing = probeTransport.executeProbe(
                endpoint = endpoint,
                armConfig = arm.copy(dnsPolicy = ProbeDnsPolicy.Ipv6Only),
                sampleIndex = 4,
            )
            assertFalse(ipv6Missing.succeeded)
            assertEquals(ProbeErrorCategory.ADDRESS_FAMILY_UNAVAILABLE, ipv6Missing.errorCategory)
        }
    }

    @Test
    fun relayHttpTransportSyncsPhpSessionGzipIsolatesPoolsAndEnforcesPreVsPostSendReplayBoundary() = runBlocking {
        MockWebServer().use { primaryServer ->
            MockWebServer().use { deadOrDroppingCandidateServer ->
                primaryServer.start(InetAddress.getByName("127.0.0.1"), 0)
                val sharedPort = primaryServer.port
                deadOrDroppingCandidateServer.start(InetAddress.getByName("127.0.0.2"), sharedPort)

                val cookieMemory = object : WenkuSessionCookieStore {
                    var session: String = ""
                    override fun getPhpSessionId(): String = session
                    override fun setPhpSessionId(sessionId: String) {
                        session = sessionId
                    }
                }

                val fakeSystemDns = Dns { host ->
                    if (host == "wenku8-relay.mewx.org") {
                        listOf(
                            InetAddress.getByAddress(
                                host,
                                IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false)!!,
                            ),
                        )
                    } else {
                        throw UnknownHostException(host)
                    }
                }

                val candidate127002Bytes =
                    IpLiteralParser.parseNumericIpBytes("127.0.0.2", requirePublicUnicast = false)!!
                val candidate127003UnboundBytes =
                    IpLiteralParser.parseNumericIpBytes("127.0.0.3", requirePublicUnicast = false)!!

                val currentGeneration = AtomicLong(1L)
                var activePinned: PinnedRelayCandidate? = null
                var fallbackCount = 0

                val routeControllerStub = object : RelayRouteProvider {
                    override fun currentRouteForHost(host: String): ActiveRelayRoute {
                        return ActiveRelayRoute(
                            generationId = currentGeneration.get(),
                            pinnedCandidate = activePinned,
                        )
                    }

                    override fun fallbackRouteAfterCandidateFailure(
                        failedGenerationId: Long,
                        failedCandidateId: String,
                        failureBeforeSend: Boolean,
                    ): ActiveRelayRoute {
                        fallbackCount += 1
                        activePinned = null
                        return ActiveRelayRoute(
                            generationId = currentGeneration.incrementAndGet(),
                            pinnedCandidate = null,
                        )
                    }
                }

                val transport = RelayHttpTransport(
                    routeProvider = routeControllerStub,
                    sessionCookieStore = cookieMemory,
                    systemDns = fakeSystemDns,
                    endpointConfiguredResolver = { true },
                    allowedRelayHostsProvider = { setOf("wenku8-relay.mewx.org") },
                )

                val endpointUrl = "http://wenku8-relay.mewx.org:$sharedPort/"
                val params = ContentValues().apply {
                    put("action", "book")
                    put("aid", "1306")
                }

                // 1. Default route: gzipped response + Set-Cookie PHPSESSID round-trip
                val gzippedPayload = gzipUtf8("<metadata>ok-gzip</metadata>")
                primaryServer.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .addHeader("Content-Encoding", "gzip")
                        .addHeader("Set-Cookie", "PHPSESSID=sess_abc_999; path=/; HttpOnly")
                        .body(Buffer().write(gzippedPayload))
                        .build(),
                )

                val res1 = transport.postForm(endpointUrl, params, isIdempotentReadOnly = true)
                assertEquals("<metadata>ok-gzip</metadata>", String(res1!!, StandardCharsets.UTF_8))
                assertEquals("sess_abc_999", cookieMemory.session)
                val poolGen1 = transport.activeConnectionPoolForTesting()
                assertNotNull(poolGen1)

                val recorded1 = primaryServer.takeRequest()
                assertTrue(recorded1.body?.utf8().orEmpty().startsWith("&"))
                assertNull(recorded1.headers["Cookie"])

                // 2. Pre-send failure on unbound candidate 127.0.0.3 (connection refused BEFORE requestHeadersStart):
                //    Even a NON-idempotent mutation (`isIdempotentReadOnly = false`) safely falls back once!
                currentGeneration.set(2L)
                activePinned = PinnedRelayCandidate(
                    candidateId = "cf-dead-presend",
                    relayHost = "wenku8-relay.mewx.org",
                    rawIpBytes = candidate127003UnboundBytes,
                )
                primaryServer.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .body("1")
                        .build(),
                )

                val mutationPreSendFallback = transport.postForm(
                    endpointUrl = endpointUrl,
                    formParams = params,
                    isIdempotentReadOnly = false,
                )
                assertEquals("1", String(mutationPreSendFallback!!, StandardCharsets.UTF_8))
                assertEquals(1, fallbackCount)
                val poolAfterFallback = transport.activeConnectionPoolForTesting()
                assertNotSame(poolGen1, poolAfterFallback)

                val recorded2 = primaryServer.takeRequest()
                assertEquals("PHPSESSID=sess_abc_999", recorded2.headers["Cookie"])

                // 3. Post-send failure (`onResponseStart(CloseSocket)` AFTER requestHeadersStart):
                //    3a. Mutation (`isIdempotentReadOnly = false`) MUST NOT replay on primaryServer!
                currentGeneration.set(10L)
                activePinned = PinnedRelayCandidate(
                    candidateId = "cf-drop-postsend",
                    relayHost = "wenku8-relay.mewx.org",
                    rawIpBytes = candidate127002Bytes,
                )
                deadOrDroppingCandidateServer.enqueue(
                    MockResponse.Builder()
                        .onResponseStart(SocketEffect.CloseSocket())
                        .build(),
                )
                val primaryCountBeforeMutationDrop = primaryServer.requestCount
                val mutationDroppedResult = transport.postForm(
                    endpointUrl = endpointUrl,
                    formParams = params,
                    isIdempotentReadOnly = false,
                )
                assertNull("Ambiguous post-send mutation must never replay", mutationDroppedResult)
                assertEquals(primaryCountBeforeMutationDrop, primaryServer.requestCount)
                assertNull("Failed candidate must still be revoked for subsequent calls", activePinned)

                //    3b. Audited read-only (`isIdempotentReadOnly = true`) DOES replay once on primaryServer!
                currentGeneration.set(20L)
                activePinned = PinnedRelayCandidate(
                    candidateId = "cf-drop-readonly",
                    relayHost = "wenku8-relay.mewx.org",
                    rawIpBytes = candidate127002Bytes,
                )
                deadOrDroppingCandidateServer.enqueue(
                    MockResponse.Builder()
                        .onResponseStart(SocketEffect.CloseSocket())
                        .build(),
                )
                primaryServer.enqueue(
                    MockResponse.Builder()
                        .code(200)
                        .body("<result>replayed-readonly-ok</result>")
                        .build(),
                )
                val readOnlyReplayResult = transport.postForm(
                    endpointUrl = endpointUrl,
                    formParams = params,
                    isIdempotentReadOnly = true,
                )
                assertEquals(
                    "<result>replayed-readonly-ok</result>",
                    String(readOnlyReplayResult!!, StandardCharsets.UTF_8),
                )

                // 4. HTTP 500 application error on pinned candidate: does NOT rotate candidate IP or replay!
                currentGeneration.set(30L)
                activePinned = PinnedRelayCandidate(
                    candidateId = "cf-http-500",
                    relayHost = "wenku8-relay.mewx.org",
                    rawIpBytes = candidate127002Bytes,
                )
                val fallbacksBefore500 = fallbackCount
                deadOrDroppingCandidateServer.enqueue(
                    MockResponse.Builder()
                        .code(500)
                        .body("upstream error")
                        .build(),
                )
                val http500Result = transport.postForm(
                    endpointUrl = endpointUrl,
                    formParams = params,
                    isIdempotentReadOnly = true,
                )
                assertNull(http500Result)
                assertEquals("HTTP 500 must not trigger candidate rotation", fallbacksBefore500, fallbackCount)
                assertEquals("cf-http-500", activePinned?.candidateId)
            }
        }
    }

    @Test
    fun legacyRelayWorker500FallsBackToColdGetAndReusesConnectionOnSubsequentSamples() = runBlocking {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)

            val endpoint = (
                DiagnosticsEndpointConfig.parseAndValidate(
                    rawBaseUrl = "http://wenku8-relay.mewx.org:${server.port}/",
                    allowCleartextForLocalTests = true,
                ) as DiagnosticsEndpointResolution.Available
                ).endpoint

            val fakeDns = Dns { host ->
                if (host == "wenku8-relay.mewx.org") {
                    listOf(
                        InetAddress.getByAddress(
                            host,
                            IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false)!!,
                        ),
                    )
                } else {
                    throw UnknownHostException(host)
                }
            }

            // Sample #01 preliminary POST /__diagnostics/latency -> Cloudflare Worker 500 ("Something went wrong....")
            server.enqueue(
                MockResponse.Builder()
                    .code(500)
                    .addHeader("cf-ray", "a5c236683ef3e7e8-SYD")
                    .addHeader("cf-placement", "remote-TPE")
                    .body("Something went wrong....")
                    .build(),
            )
            // Sample #01 automatic cold GET / -> 302 redirect on placed Worker
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", "https://buymeacoffee.com/mewx")
                    .addHeader("cf-ray", "a5c2473da8ad62aa-SYD")
                    .addHeader("cf-placement", "remote-TPE")
                    .build(),
            )
            // Sample #02 cached legacy GET / -> 302 on warm reused socket
            server.enqueue(
                MockResponse.Builder()
                    .code(302)
                    .addHeader("Location", "https://buymeacoffee.com/mewx")
                    .addHeader("cf-ray", "a5c2479999999999-SYD")
                    .addHeader("cf-placement", "remote-TPE")
                    .build(),
            )

            val nowMono = AtomicLong(50_000L)
            val sharedPool = ConnectionPool(5, 60, TimeUnit.SECONDS)
            val probeTransport = OkHttpProbeTransport(
                systemDns = fakeDns,
                monotonicClockMillis = { nowMono.addAndGet(12L) },
            )
            val arm = ProbeArmConfig(
                armId = "legacy-arm",
                dnsPolicy = ProbeDnsPolicy.SystemDefault,
                connectionPool = sharedPool,
            )

            val first = probeTransport.executeProbe(
                endpoint = endpoint,
                armConfig = arm,
                sampleIndex = 1,
                explicitNonce = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            )
            assertTrue("Legacy relay Worker must succeed via GET / fallback", first.succeeded)
            assertEquals(302, first.httpStatusCode)
            assertFalse("Sample #01 must measure a genuine cold socket after evicting preliminary POST", first.connectionReused)
            assertEquals(1, first.connectAttemptCount)
            assertEquals(OkHttpProbeTransport.LEGACY_RELAY_PROBE_SCHEMA_V1, first.serverPayload?.schema)
            assertEquals("SYD", first.serverPayload?.ingressColo)
            assertEquals("placed-relay:remote-TPE", first.serverPayload?.endpointRole)

            val reqPost = server.takeRequest()
            assertEquals("POST", reqPost.method)
            assertEquals("/__diagnostics/latency", reqPost.target)

            val reqGet1 = server.takeRequest()
            assertEquals("GET", reqGet1.method)
            assertEquals("/", reqGet1.target)
            assertNull(reqGet1.headers["Cookie"])

            val second = probeTransport.executeProbe(
                endpoint = endpoint,
                armConfig = arm,
                sampleIndex = 2,
                explicitNonce = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
            )
            assertTrue(second.succeeded)
            assertTrue("Sample #02 must reuse the warm keep-alive connection", second.connectionReused)
            assertEquals(0, second.connectAttemptCount)
            assertEquals("SYD", second.serverPayload?.ingressColo)

            val reqGet2 = server.takeRequest()
            assertEquals("GET", reqGet2.method)
            assertEquals("/", reqGet2.target)
            assertEquals("No second POST should be sent while legacy cache TTL is active", 3, server.requestCount)
        }

        // Verify a generic non-Cloudflare HTTP 500 without relay markers does NOT fallback to GET /
        MockWebServer().use { plain500Server ->
            plain500Server.start(InetAddress.getByName("127.0.0.1"), 0)
            val plainEndpoint = (
                DiagnosticsEndpointConfig.parseAndValidate(
                    rawBaseUrl = "http://wenku8-android.mewx.org:${plain500Server.port}/",
                    allowCleartextForLocalTests = true,
                ) as DiagnosticsEndpointResolution.Available
                ).endpoint
            val fakeDns2 = Dns { host ->
                listOf(
                    InetAddress.getByAddress(
                        host,
                        IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false)!!,
                    ),
                )
            }
            plain500Server.enqueue(
                MockResponse.Builder()
                    .code(500)
                    .body("plain origin internal error")
                    .build(),
            )
            val probeTransport2 = OkHttpProbeTransport(systemDns = fakeDns2)
            val failed = probeTransport2.executeProbe(
                endpoint = plainEndpoint,
                armConfig = ProbeArmConfig(),
                sampleIndex = 1,
            )
            assertFalse(failed.succeeded)
            assertEquals(500, failed.httpStatusCode)
            assertEquals(ProbeErrorCategory.HTTP_STATUS_ERROR, failed.errorCategory)
            assertEquals(1, plain500Server.requestCount)
        }
    }

    @Test
    fun mainThreadSafeCancelAndPoolEvictionOffloadWorkOffAndroidMainLooper() {
        // Robolectric unit tests execute on Android's Main Looper by default.
        assertSame(Looper.getMainLooper(), Looper.myLooper())

        var dispatchedCancelCount = 0
        var cancelRanInlineOnCaller = false
        val recordingExecutor = java.util.concurrent.Executor { runnable ->
            dispatchedCancelCount += 1
            val worker = Thread {
                // Must execute off the Android Main Looper!
                assertNotSame(Looper.getMainLooper(), Looper.myLooper())
                runnable.run()
            }
            worker.start()
            worker.join(2_000L)
        }

        val dummyClient = OkHttpProbeTransport.defaultSharedBaseOkHttpClient()
        val dummyCall: Call = dummyClient.newCall(
            Request.Builder().url("https://wenku8-relay.mewx.org/").build(),
        )
        OkHttpProbeTransport.cancelCallSafely(dummyCall, recordingExecutor)
        assertEquals(1, dispatchedCancelCount)
        assertTrue(dummyCall.isCanceled())
        assertFalse(cancelRanInlineOnCaller)

        var dispatchedEvictCount = 0
        val evictExecutor = java.util.concurrent.Executor { runnable ->
            dispatchedEvictCount += 1
            val worker = Thread {
                assertNotSame(Looper.getMainLooper(), Looper.myLooper())
                runnable.run()
            }
            worker.start()
            worker.join(2_000L)
        }
        val pool = ConnectionPool(5, 60, TimeUnit.SECONDS)
        OkHttpProbeTransport.evictPoolSafely(pool, evictExecutor)
        assertEquals(1, dispatchedEvictCount)
    }

    @Test
    fun relayHttpTransportHandlesZeroByteAndEmptyGzipBodiesWithoutTrippingFallback() = runBlocking {
        MockWebServer().use { server ->
            server.start(InetAddress.getByName("127.0.0.1"), 0)
            val port = server.port

            val cookieMemory = object : WenkuSessionCookieStore {
                override fun getPhpSessionId(): String = ""
                override fun setPhpSessionId(sessionId: String) = Unit
            }
            val fakeSystemDns = Dns { host ->
                if (host == "wenku8-relay.mewx.org") {
                    listOf(
                        InetAddress.getByAddress(
                            host,
                            IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false)!!,
                        ),
                    )
                } else {
                    throw UnknownHostException(host)
                }
            }

            var fallbackCount = 0
            val pinnedCandidate = PinnedRelayCandidate(
                candidateId = "cf-loopback",
                relayHost = "wenku8-relay.mewx.org",
                rawIpBytes = IpLiteralParser.parseNumericIpBytes("127.0.0.1", requirePublicUnicast = false)!!,
            )
            val routeProvider = object : RelayRouteProvider {
                override fun currentRouteForHost(host: String): ActiveRelayRoute {
                    return ActiveRelayRoute(generationId = 1L, pinnedCandidate = pinnedCandidate)
                }

                override fun fallbackRouteAfterCandidateFailure(
                    failedGenerationId: Long,
                    failedCandidateId: String,
                    failureBeforeSend: Boolean,
                ): ActiveRelayRoute {
                    fallbackCount += 1
                    return ActiveRelayRoute(generationId = 2L, pinnedCandidate = null)
                }
            }

            val transport = RelayHttpTransport(
                routeProvider = routeProvider,
                sessionCookieStore = cookieMemory,
                systemDns = fakeSystemDns,
                endpointConfiguredResolver = { true },
                allowedRelayHostsProvider = { setOf("wenku8-relay.mewx.org") },
            )

            val endpointUrl = "http://wenku8-relay.mewx.org:$port/"
            val params = ContentValues().apply {
                put("action", "book")
                put("do", "text")
                put("aid", "4358")
                put("cid", "183108")
            }

            // 1. Upstream HTTP 200 with 0-byte body (exact relay behavior for aid=4358 cid=183108)
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .body(Buffer())
                    .build(),
            )
            val plainEmpty = transport.postForm(endpointUrl, params, isIdempotentReadOnly = true)
            assertNotNull("HTTP 200 with 0 bytes must return empty ByteArray, not null", plainEmpty)
            assertEquals(0, plainEmpty!!.size)
            assertEquals(0, fallbackCount)

            // 2. HTTP 200 with Content-Encoding: gzip and a truly 0-byte raw stream (must NOT throw EOFException)
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Encoding", "gzip")
                    .body(Buffer())
                    .build(),
            )
            val emptyRawGzip = transport.postForm(endpointUrl, params, isIdempotentReadOnly = true)
            assertNotNull("Zero-byte stream with Content-Encoding: gzip must return empty ByteArray", emptyRawGzip)
            assertEquals(0, emptyRawGzip!!.size)
            assertEquals(0, fallbackCount)

            // 3. HTTP 200 with Content-Encoding: gzip and a valid gzip-compressed empty string
            server.enqueue(
                MockResponse.Builder()
                    .code(200)
                    .addHeader("Content-Encoding", "gzip")
                    .body(Buffer().write(gzipUtf8("")))
                    .build(),
            )
            val validEmptyGzip = transport.postForm(endpointUrl, params, isIdempotentReadOnly = true)
            assertNotNull("Valid empty gzip payload must decode to empty ByteArray", validEmptyGzip)
            assertEquals(0, validEmptyGzip!!.size)
            assertEquals(0, fallbackCount)
            assertEquals(3, server.requestCount)
        }
    }

    private fun gzipUtf8(text: String): ByteArray {
        val raw = ByteArrayOutputStream()
        GZIPOutputStream(raw).use { gzip ->
            gzip.write(text.toByteArray(StandardCharsets.UTF_8))
        }
        return raw.toByteArray()
    }
}
