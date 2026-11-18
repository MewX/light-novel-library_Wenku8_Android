package org.mewx.wenku8.data.remote.diagnostics

import android.os.Looper
import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Connection
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Handshake
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InterruptedIOException
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Collections
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.net.ssl.SSLException
import kotlin.coroutines.resume

/**
 * Stable, privacy-safe error categories exported in `wenku8-network-report-v1` (never raw stack
 * traces or IP strings).
 */
enum class ProbeErrorCategory(val wireName: String) {
    ADDRESS_FAMILY_UNAVAILABLE("address_family_unavailable"),
    DNS_FAILED("dns_failed"),
    CONNECT_TIMEOUT("connect_timeout"),
    CONNECT_FAILED("connect_failed"),
    TLS_FAILED("tls_failed"),
    READ_TIMEOUT("read_timeout"),
    HTTP_RATE_LIMITED("rate_limited"),
    HTTP_STATUS_ERROR("http_status"),
    INVALID_PROBE_PAYLOAD("invalid_probe_payload"),
    CANCELLED("cancelled"),
    IO_ERROR("io_error"),
}

class AddressFamilyUnavailableException(family: IpAddressFamily) :
    UnknownHostException("No ${family.wireLabel} DNS records returned")

sealed interface ProbeDnsPolicy {
    data object SystemDefault : ProbeDnsPolicy
    data object Ipv4Only : ProbeDnsPolicy
    data object Ipv6Only : ProbeDnsPolicy
    data class PinnedCandidate(
        val candidateId: String,
        val relayHostname: String,
        val rawIpBytes: ByteArray,
    ) : ProbeDnsPolicy
}

data class ProbeArmConfig(
    val armId: String = "default",
    val dnsPolicy: ProbeDnsPolicy = ProbeDnsPolicy.SystemDefault,
    val fastFallback: Boolean = true,
    val protocols: List<Protocol> = listOf(Protocol.HTTP_2, Protocol.HTTP_1_1),
    val connectionPool: ConnectionPool = ConnectionPool(5, 60, TimeUnit.SECONDS),
    val connectTimeoutMillis: Long = 3_000L,
    val readTimeoutMillis: Long = 5_000L,
    val callTimeoutMillis: Long = 10_000L,
)

/**
 * Parsed server payload from `POST /__diagnostics/latency` (`wenku8-network-probe-v1`) or
 * header-derived telemetry when probing a legacy relay Worker prior to `/__diagnostics/latency`
 * deployment (`wenku8-legacy-relay-probe-v1`).
 */
data class ParsedServerProbePayload(
    val schema: String,
    val nonce: String,
    val endpointRole: String?,
    val ingressColo: String?,
    val country: String?,
    val asn: Long?,
    val clientIpFamily: IpAddressFamily?,
    val httpProtocol: String?,
    val transportRttMs: Long?,
    val transportRttSource: String?,
    val handlerElapsedMs: Long?,
)

/**
 * Complete telemetry of a single diagnostic attempt (`#01..#N` or tournament probe).
 */
data class ProbeSampleResult(
    val sampleIndex: Int,
    val armId: String,
    val candidateId: String?,
    val succeeded: Boolean,
    val httpStatusCode: Int?,
    val retryAfterSeconds: Long?,
    val errorCategory: ProbeErrorCategory?,
    val fullResponseElapsedMs: Long,
    val responseHeadersStartMs: Long?,
    val dnsDurationMs: Long?,
    val tcpConnectDurationMs: Long?,
    val tlsHandshakeDurationMs: Long?,
    val connectionReused: Boolean,
    val connectAttemptCount: Int,
    val observedProxyType: Proxy.Type,
    val localSocketFamily: IpAddressFamily?,
    val negotiatedProtocol: String?,
    val responseBodyBytesRead: Int,
    val bodyComplete: Boolean,
    val serverPayload: ParsedServerProbePayload?,
    val observedEvents: List<String>,
)

interface DiagnosticsProbeTransport {
    suspend fun executeProbe(
        endpoint: ValidatedRelayEndpoint,
        armConfig: ProbeArmConfig,
        sampleIndex: Int = 1,
        explicitNonce: String? = null,
    ): ProbeSampleResult
}

/**
 * Session-free, bounded OkHttp 5 diagnostic adapter (`POST /__diagnostics/latency`, with seamless
 * fallback to `GET /` on legacy placed relay Workers that have not yet deployed
 * `/__diagnostics/latency`).
 *
 * Guarantees per `network-connectivity-and-routing-design.md`:
 * - Uses `CookieJar.NO_COOKIES`, `Accept-Encoding: identity`, `Cache-Control: no-store`,
 *   `followRedirects(false)`, and `retryOnConnectionFailure(false)`.
 * - Bounds request body to `<= 256` bytes and response body read to `<= 2,048` bytes.
 * - Classifies `connectionReused` strictly from `EventListener` (`connectionAcquired` without
 *   `connectStart`), records `Proxy.Type` from `connectStart`, and records both local socket
 *   address family and server-reported `clientIpFamily` separately.
 * - Uses Android/JVM-safe `JSONObject` nullable parsing (`has(k) && !isNull(k)` + runtime type
 *   checks) so `JSONObject.NULL` never turns into the literal string `"null"` or `0 ms`.
 */
class OkHttpProbeTransport(
    private val baseOkHttpClient: OkHttpClient = defaultSharedBaseOkHttpClient(),
    private val systemDns: Dns = Dns.SYSTEM,
    private val monotonicClockMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DiagnosticsProbeTransport {

    companion object {
        const val PROBE_SCHEMA_V1: String = "wenku8-network-probe-v1"
        const val LEGACY_RELAY_PROBE_SCHEMA_V1: String = "wenku8-legacy-relay-probe-v1"
        const val LEGACY_FALLBACK_TTL_MILLIS: Long = 10L * 60L * 1_000L
        const val MAX_REQUEST_BODY_BYTES: Int = 256
        const val MAX_RESPONSE_BODY_BYTES: Int = 2_048
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val NONCE_HEX_REGEX = Regex("^[0-9a-f]{32}$")
        private val CF_RAY_COLO_REGEX = Regex("^[A-Z]{3}$")
        private val secureRandom = SecureRandom()

        fun defaultSharedBaseOkHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .connectTimeout(3_000L, TimeUnit.MILLISECONDS)
                .readTimeout(5_000L, TimeUnit.MILLISECONDS)
                .writeTimeout(5_000L, TimeUnit.MILLISECONDS)
                .build()
        }

        /**
         * Safely cancels an OkHttp [Call] without ever closing a TLS/TCP socket on Android's Main
         * Thread (`RealCall.cancel()` -> `Socket.closeQuietly()` throws `NetworkOnMainThreadException`
         * when invoked synchronously from a `viewModelScope` cancellation handler on Main).
         */
        fun cancelCallSafely(call: Call, fallbackExecutor: Executor) {
            if (isOnAndroidMainThread()) {
                try {
                    fallbackExecutor.execute {
                        runCatching { call.cancel() }
                    }
                } catch (_: Throwable) {
                    Thread { runCatching { call.cancel() } }.start()
                }
            } else {
                runCatching { call.cancel() }
            }
        }

        /**
         * Safely evicts idle sockets from [pool] off Android's Main Thread (`RealConnectionPool.evictAll()`
         * closes idle `ConscryptEngineSocket`s synchronously, which writes TLS `close_notify` and
         * throws `NetworkOnMainThreadException` if invoked on Main).
         */
        fun evictPoolSafely(pool: ConnectionPool, fallbackExecutor: Executor? = null) {
            if (isOnAndroidMainThread()) {
                if (fallbackExecutor != null) {
                    try {
                        fallbackExecutor.execute {
                            runCatching { pool.evictAll() }
                        }
                        return
                    } catch (_: Throwable) {
                        // Fall through to dedicated short-lived thread
                    }
                }
                Thread { runCatching { pool.evictAll() } }.start()
            } else {
                runCatching { pool.evictAll() }
            }
        }

        private fun isOnAndroidMainThread(): Boolean {
            return try {
                val mainLooper = Looper.getMainLooper()
                mainLooper != null && Looper.myLooper() === mainLooper
            } catch (_: Throwable) {
                false
            }
        }

        /**
         * Extracts the 3-letter IATA ingress colo code from a Cloudflare `cf-ray` header
         * (e.g. `"a5c2473da8ad62aa-SYD"` -> `"SYD"`).
         */
        internal fun parseCfRayIngressColo(cfRayHeader: String?): String? {
            val trimmed = cfRayHeader?.trim().orEmpty()
            val dashIndex = trimmed.lastIndexOf('-')
            if (dashIndex <= 0 || dashIndex >= trimmed.lastIndex) return null
            val coloCandidate = trimmed.substring(dashIndex + 1).uppercase(Locale.ROOT)
            return coloCandidate.takeIf { CF_RAY_COLO_REGEX.matches(it) }
        }

        fun generateProbeNonceHex(): String {
            val bytes = ByteArray(16)
            secureRandom.nextBytes(bytes)
            val sb = StringBuilder(32)
            for (b in bytes) {
                val unsigned = b.toInt() and 0xFF
                sb.append(Character.forDigit(unsigned ushr 4, 16))
                sb.append(Character.forDigit(unsigned and 0x0F, 16))
            }
            return sb.toString()
        }

        /**
         * Strictly parses `wenku8-network-probe-v1` JSON across both Android `org.json` and host
         * JVM `org.json:json` without coercing `JSONObject.NULL` into `"null"` or `0`.
         */
        fun parseAndValidateProbeJson(
            rawJsonText: String,
            expectedNonce: String,
        ): ParsedServerProbePayload? {
            if (!NONCE_HEX_REGEX.matches(expectedNonce)) return null
            val root = try {
                JSONObject(rawJsonText)
            } catch (_: Exception) {
                return null
            }

            val schema = readStrictNullableString(root, "schema") ?: return null
            if (schema != PROBE_SCHEMA_V1) return null

            val echoedNonce = readStrictNullableString(root, "nonce") ?: return null
            if (echoedNonce != expectedNonce) return null

            val endpointRoleResult = readOptionalStringField(root, "endpointRole") ?: return null
            val ingressColoResult = readOptionalStringField(root, "ingressColo") ?: return null
            val countryResult = readOptionalStringField(root, "country") ?: return null
            val asnResult = readOptionalLongField(root, "asn", requirePositive = true) ?: return null
            val clientFamilyString = readOptionalStringField(root, "clientIpFamily") ?: return null
            val clientIpFamily = if (clientFamilyString.value == null) {
                null
            } else {
                IpAddressFamily.fromWireLabelOrNull(clientFamilyString.value) ?: return null
            }
            val httpProtocolResult = readOptionalStringField(root, "httpProtocol") ?: return null
            val rttResult = readOptionalLongField(root, "transportRttMs", requirePositive = true) ?: return null
            val rttSourceResult = readOptionalStringField(root, "transportRttSource") ?: return null
            val handlerElapsedResult =
                readOptionalLongField(root, "handlerElapsedMs", requirePositive = false) ?: return null

            return ParsedServerProbePayload(
                schema = schema,
                nonce = echoedNonce,
                endpointRole = endpointRoleResult.value,
                ingressColo = ingressColoResult.value,
                country = countryResult.value,
                asn = asnResult.value,
                clientIpFamily = clientIpFamily,
                httpProtocol = httpProtocolResult.value,
                transportRttMs = rttResult.value,
                transportRttSource = rttSourceResult.value,
                handlerElapsedMs = handlerElapsedResult.value,
            )
        }

        private data class OptionalValue<T>(val value: T?)

        private fun readStrictNullableString(obj: JSONObject, key: String): String? {
            if (!obj.has(key) || obj.isNull(key)) return null
            val raw = obj.opt(key)
            return (raw as? String)?.takeIf { it.isNotBlank() && it != "null" }
        }

        /**
         * Returns `null` when the field has a **wrong JSON type** (invalid payload), or
         * `OptionalValue(null)` when absent / explicit `JSONObject.NULL`.
         */
        private fun readOptionalStringField(obj: JSONObject, key: String): OptionalValue<String>? {
            if (!obj.has(key) || obj.isNull(key)) return OptionalValue(null)
            val raw = obj.opt(key)
            if (raw !is String) return null
            if (raw == "null") return null
            return OptionalValue(raw.ifBlank { null })
        }

        private fun readOptionalLongField(
            obj: JSONObject,
            key: String,
            requirePositive: Boolean,
        ): OptionalValue<Long>? {
            if (!obj.has(key) || obj.isNull(key)) return OptionalValue(null)
            val raw = obj.opt(key)
            if (raw is Boolean || raw !is Number) return null
            val doubleVal = raw.toDouble()
            if (!doubleVal.isFinite()) return null
            val longVal = raw.toLong()
            if (requirePositive && longVal <= 0L) return null
            if (!requirePositive && longVal < 0L) return null
            return OptionalValue(longVal)
        }
    }

    private val legacyRelayFallbackUntilMonoByHost = ConcurrentHashMap<String, Long>()

    override suspend fun executeProbe(
        endpoint: ValidatedRelayEndpoint,
        armConfig: ProbeArmConfig,
        sampleIndex: Int,
        explicitNonce: String?,
    ): ProbeSampleResult = withContext(ioDispatcher) {
        val nonce = explicitNonce ?: generateProbeNonceHex()
        val hostKey = endpoint.relayHost.trim().lowercase(Locale.ROOT)
        val fallbackUntil = legacyRelayFallbackUntilMonoByHost[hostKey]
        val useCachedLegacyFallback = fallbackUntil != null && monotonicClockMillis() < fallbackUntil

        if (useCachedLegacyFallback) {
            return@withContext executeLegacyRelayGetProbe(
                endpoint = endpoint,
                armConfig = armConfig,
                sampleIndex = sampleIndex,
                nonce = nonce,
            )
        }

        val requestJson = JSONObject()
            .put("schema", PROBE_SCHEMA_V1)
            .put("nonce", nonce)
            .toString()
        val requestBytes = requestJson.toByteArray(StandardCharsets.UTF_8)
        require(requestBytes.size <= MAX_REQUEST_BODY_BYTES) {
            "Diagnostic probe body exceeded $MAX_REQUEST_BODY_BYTES bytes"
        }

        val recorder = ProbeEventRecorder(monotonicClockMillis)
        val dnsAdapter = buildDnsForPolicy(armConfig.dnsPolicy)
        val candidateId = (armConfig.dnsPolicy as? ProbeDnsPolicy.PinnedCandidate)?.candidateId
        val probeClient = buildArmOkHttpClient(armConfig, dnsAdapter, recorder)

        val request = Request.Builder()
            .url(endpoint.latencyProbeUrl)
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-store")
            .post(requestBytes.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        val startedAtMs = monotonicClockMillis()
        val call = probeClient.newCall(request)

        var shouldFallbackToLegacyGet = false
        val primaryResult: ProbeSampleResult? = try {
            val response = awaitOkHttpCall(call)
            response.use { httpResponse ->
                val statusCode = httpResponse.code
                val retryAfterSeconds = httpResponse.header("Retry-After")?.trim()?.toLongOrNull()
                val boundedRead = readBoundedBody(httpResponse, MAX_RESPONSE_BODY_BYTES)
                val fullElapsedMs = (monotonicClockMillis() - startedAtMs).coerceAtLeast(0L)
                val protocolLabel = recorder.negotiatedProtocol ?: httpResponse.protocol.toString()

                if (statusCode == 429) {
                    return@use recorder.toResult(
                        sampleIndex = sampleIndex,
                        armId = armConfig.armId,
                        candidateId = candidateId,
                        succeeded = false,
                        httpStatusCode = statusCode,
                        retryAfterSeconds = retryAfterSeconds,
                        errorCategory = ProbeErrorCategory.HTTP_RATE_LIMITED,
                        fullElapsedMs = fullElapsedMs,
                        negotiatedProtocol = protocolLabel,
                        responseBodyBytesRead = boundedRead.bytes.size,
                        bodyComplete = !boundedRead.exceededCap,
                        serverPayload = null,
                    )
                }

                if (statusCode != 200) {
                    val rawText = String(boundedRead.bytes, StandardCharsets.UTF_8)
                    if (isLegacyRelayUnsupportedLatencyRoute(statusCode, httpResponse, rawText)) {
                        shouldFallbackToLegacyGet = true
                        return@use null
                    }
                    return@use recorder.toResult(
                        sampleIndex = sampleIndex,
                        armId = armConfig.armId,
                        candidateId = candidateId,
                        succeeded = false,
                        httpStatusCode = statusCode,
                        retryAfterSeconds = retryAfterSeconds,
                        errorCategory = ProbeErrorCategory.HTTP_STATUS_ERROR,
                        fullElapsedMs = fullElapsedMs,
                        negotiatedProtocol = protocolLabel,
                        responseBodyBytesRead = boundedRead.bytes.size,
                        bodyComplete = !boundedRead.exceededCap,
                        serverPayload = null,
                    )
                }

                if (boundedRead.exceededCap) {
                    return@use recorder.toResult(
                        sampleIndex = sampleIndex,
                        armId = armConfig.armId,
                        candidateId = candidateId,
                        succeeded = false,
                        httpStatusCode = statusCode,
                        retryAfterSeconds = null,
                        errorCategory = ProbeErrorCategory.INVALID_PROBE_PAYLOAD,
                        fullElapsedMs = fullElapsedMs,
                        negotiatedProtocol = protocolLabel,
                        responseBodyBytesRead = boundedRead.bytes.size,
                        bodyComplete = false,
                        serverPayload = null,
                    )
                }

                val rawBodyString = String(boundedRead.bytes, StandardCharsets.UTF_8)
                val validatedPayload = parseAndValidateProbeJson(
                    rawJsonText = rawBodyString,
                    expectedNonce = nonce,
                )

                if (validatedPayload == null) {
                    return@use recorder.toResult(
                        sampleIndex = sampleIndex,
                        armId = armConfig.armId,
                        candidateId = candidateId,
                        succeeded = false,
                        httpStatusCode = statusCode,
                        retryAfterSeconds = null,
                        errorCategory = ProbeErrorCategory.INVALID_PROBE_PAYLOAD,
                        fullElapsedMs = fullElapsedMs,
                        negotiatedProtocol = protocolLabel,
                        responseBodyBytesRead = boundedRead.bytes.size,
                        bodyComplete = true,
                        serverPayload = null,
                    )
                }

                recorder.toResult(
                    sampleIndex = sampleIndex,
                    armId = armConfig.armId,
                    candidateId = candidateId,
                    succeeded = true,
                    httpStatusCode = statusCode,
                    retryAfterSeconds = null,
                    errorCategory = null,
                    fullElapsedMs = fullElapsedMs,
                    negotiatedProtocol = protocolLabel,
                    responseBodyBytesRead = boundedRead.bytes.size,
                    bodyComplete = true,
                    serverPayload = validatedPayload,
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            cancelCallSafely(call, baseOkHttpClient.dispatcher.executorService)
            throw cancelled
        } catch (ioError: IOException) {
            val fullElapsedMs = (monotonicClockMillis() - startedAtMs).coerceAtLeast(0L)
            val category = classifyProbeIoException(ioError, call.isCanceled(), recorder)
            recorder.toResult(
                sampleIndex = sampleIndex,
                armId = armConfig.armId,
                candidateId = candidateId,
                succeeded = false,
                httpStatusCode = null,
                retryAfterSeconds = null,
                errorCategory = category,
                fullElapsedMs = fullElapsedMs,
                negotiatedProtocol = recorder.negotiatedProtocol,
                responseBodyBytesRead = 0,
                bodyComplete = false,
                serverPayload = null,
            )
        }

        if (shouldFallbackToLegacyGet) {
            legacyRelayFallbackUntilMonoByHost[hostKey] =
                monotonicClockMillis() + LEGACY_FALLBACK_TTL_MILLIS
            // Evict the preliminary POST socket so Sample #01 measures a genuine cold handshake,
            // while Samples #02..#N reuse the warm GET connection on armConfig.connectionPool.
            evictPoolSafely(armConfig.connectionPool, baseOkHttpClient.dispatcher.executorService)
            return@withContext executeLegacyRelayGetProbe(
                endpoint = endpoint,
                armConfig = armConfig,
                sampleIndex = sampleIndex,
                nonce = nonce,
            )
        }

        primaryResult!!
    }

    /**
     * Detects when `POST /__diagnostics/latency` hits a legacy `wenku8-relay` Cloudflare Worker
     * that has not yet deployed `/__diagnostics/latency` (which returns `404`, `405`, or the
     * Worker's form-parser `500` `"Something went wrong...."` with `cf-ray` / `cf-placement`).
     * Never matches `429` rate-limiting or `503` `diagnostics_disabled`.
     */
    private fun isLegacyRelayUnsupportedLatencyRoute(
        statusCode: Int,
        response: Response,
        bodyPreview: String,
    ): Boolean {
        val hasCloudflareRelayHeaders =
            !response.header("cf-ray").isNullOrBlank() ||
                !response.header("cf-placement").isNullOrBlank()
        if (statusCode == 404 || statusCode == 405) {
            return true
        }
        if (statusCode == 500 &&
            (hasCloudflareRelayHeaders || bodyPreview.contains("Something went wrong", ignoreCase = true))
        ) {
            return true
        }
        return false
    }

    /**
     * Session-free `GET <origin>/` (`followRedirects = false`) against a legacy `wenku8-relay`
     * Cloudflare Worker: invokes the placed Worker (`cf-placement: remote-TPE`, `HTTP 302`) with
     * zero cookies and zero upstream Wenku8 PHP traffic while preserving cold vs warm reuse
     * telemetry and `cf-ray` ingress colo extraction.
     */
    private suspend fun executeLegacyRelayGetProbe(
        endpoint: ValidatedRelayEndpoint,
        armConfig: ProbeArmConfig,
        sampleIndex: Int,
        nonce: String,
    ): ProbeSampleResult {
        val recorder = ProbeEventRecorder(monotonicClockMillis)
        val dnsAdapter = buildDnsForPolicy(armConfig.dnsPolicy)
        val candidateId = (armConfig.dnsPolicy as? ProbeDnsPolicy.PinnedCandidate)?.candidateId
        val probeClient = buildArmOkHttpClient(armConfig, dnsAdapter, recorder)

        val request = Request.Builder()
            .url("${endpoint.canonicalOrigin}/")
            .header("Accept-Encoding", "identity")
            .header("Cache-Control", "no-store")
            .get()
            .build()

        val startedAtMs = monotonicClockMillis()
        val call = probeClient.newCall(request)

        return try {
            val response = awaitOkHttpCall(call)
            response.use { httpResponse ->
                val statusCode = httpResponse.code
                val retryAfterSeconds = httpResponse.header("Retry-After")?.trim()?.toLongOrNull()
                val boundedRead = readBoundedBody(httpResponse, MAX_RESPONSE_BODY_BYTES)
                val fullElapsedMs = (monotonicClockMillis() - startedAtMs).coerceAtLeast(0L)
                val protocolLabel = recorder.negotiatedProtocol ?: httpResponse.protocol.toString()

                if (statusCode == 429) {
                    return@use recorder.toResult(
                        sampleIndex = sampleIndex,
                        armId = armConfig.armId,
                        candidateId = candidateId,
                        succeeded = false,
                        httpStatusCode = statusCode,
                        retryAfterSeconds = retryAfterSeconds,
                        errorCategory = ProbeErrorCategory.HTTP_RATE_LIMITED,
                        fullElapsedMs = fullElapsedMs,
                        negotiatedProtocol = protocolLabel,
                        responseBodyBytesRead = boundedRead.bytes.size,
                        bodyComplete = !boundedRead.exceededCap,
                        serverPayload = null,
                    )
                }

                val cfRay = httpResponse.header("cf-ray")
                val cfPlacement = httpResponse.header("cf-placement")?.trim()?.takeIf { it.isNotEmpty() }
                val locationHeader = httpResponse.header("Location")
                val isExpectedLegacyRelayResponse = statusCode in 200..204 ||
                    (
                        statusCode in listOf(301, 302, 303, 307, 308) &&
                            (!cfRay.isNullOrBlank() || cfPlacement != null || !locationHeader.isNullOrBlank())
                        )

                if (!isExpectedLegacyRelayResponse) {
                    return@use recorder.toResult(
                        sampleIndex = sampleIndex,
                        armId = armConfig.armId,
                        candidateId = candidateId,
                        succeeded = false,
                        httpStatusCode = statusCode,
                        retryAfterSeconds = retryAfterSeconds,
                        errorCategory = ProbeErrorCategory.HTTP_STATUS_ERROR,
                        fullElapsedMs = fullElapsedMs,
                        negotiatedProtocol = protocolLabel,
                        responseBodyBytesRead = boundedRead.bytes.size,
                        bodyComplete = !boundedRead.exceededCap,
                        serverPayload = null,
                    )
                }

                val legacyPayload = ParsedServerProbePayload(
                    schema = LEGACY_RELAY_PROBE_SCHEMA_V1,
                    nonce = nonce,
                    endpointRole = cfPlacement?.let { "${endpoint.endpointRole}:$it" } ?: endpoint.endpointRole,
                    ingressColo = parseCfRayIngressColo(cfRay),
                    country = null,
                    asn = null,
                    clientIpFamily = null,
                    httpProtocol = protocolLabel,
                    transportRttMs = null,
                    transportRttSource = null,
                    handlerElapsedMs = null,
                )

                recorder.toResult(
                    sampleIndex = sampleIndex,
                    armId = armConfig.armId,
                    candidateId = candidateId,
                    succeeded = true,
                    httpStatusCode = statusCode,
                    retryAfterSeconds = null,
                    errorCategory = null,
                    fullElapsedMs = fullElapsedMs,
                    negotiatedProtocol = protocolLabel,
                    responseBodyBytesRead = boundedRead.bytes.size,
                    bodyComplete = !boundedRead.exceededCap,
                    serverPayload = legacyPayload,
                )
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            cancelCallSafely(call, baseOkHttpClient.dispatcher.executorService)
            throw cancelled
        } catch (ioError: IOException) {
            val fullElapsedMs = (monotonicClockMillis() - startedAtMs).coerceAtLeast(0L)
            val category = classifyProbeIoException(ioError, call.isCanceled(), recorder)
            recorder.toResult(
                sampleIndex = sampleIndex,
                armId = armConfig.armId,
                candidateId = candidateId,
                succeeded = false,
                httpStatusCode = null,
                retryAfterSeconds = null,
                errorCategory = category,
                fullElapsedMs = fullElapsedMs,
                negotiatedProtocol = recorder.negotiatedProtocol,
                responseBodyBytesRead = 0,
                bodyComplete = false,
                serverPayload = null,
            )
        }
    }

    private fun buildArmOkHttpClient(
        armConfig: ProbeArmConfig,
        dnsAdapter: Dns,
        recorder: ProbeEventRecorder,
    ): OkHttpClient {
        return baseOkHttpClient.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .connectionPool(armConfig.connectionPool)
            .dns(dnsAdapter)
            .fastFallback(armConfig.fastFallback)
            .protocols(armConfig.protocols)
            .followRedirects(false)
            .followSslRedirects(false)
            .retryOnConnectionFailure(false)
            .connectTimeout(armConfig.connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(armConfig.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(armConfig.readTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(armConfig.callTimeoutMillis, TimeUnit.MILLISECONDS)
            .eventListener(recorder)
            .build()
    }

    private fun buildDnsForPolicy(policy: ProbeDnsPolicy): Dns {
        return when (policy) {
            ProbeDnsPolicy.SystemDefault -> systemDns

            ProbeDnsPolicy.Ipv4Only -> AddressFamilyFilteredDns(systemDns, IpAddressFamily.IPV4)

            ProbeDnsPolicy.Ipv6Only -> AddressFamilyFilteredDns(systemDns, IpAddressFamily.IPV6)

            is ProbeDnsPolicy.PinnedCandidate -> PinnedHostIpDns(
                relayHostname = policy.relayHostname,
                rawIpBytes = policy.rawIpBytes,
            )
        }
    }

    private data class BoundedReadResult(
        val bytes: ByteArray,
        val exceededCap: Boolean,
    )

    private fun readBoundedBody(response: Response, maxBytes: Int): BoundedReadResult {
        val stream = response.body.byteStream()
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(512)
        var total = 0
        while (true) {
            val readCount = stream.read(buffer)
            if (readCount == -1) break
            if (total + readCount > maxBytes) {
                val allowed = (maxBytes - total).coerceAtLeast(0)
                if (allowed > 0) {
                    out.write(buffer, 0, allowed)
                }
                return BoundedReadResult(out.toByteArray(), exceededCap = true)
            }
            out.write(buffer, 0, readCount)
            total += readCount
        }
        return BoundedReadResult(out.toByteArray(), exceededCap = false)
    }

    private fun classifyProbeIoException(
        error: IOException,
        wasCanceled: Boolean,
        recorder: ProbeEventRecorder,
    ): ProbeErrorCategory {
        if (wasCanceled) return ProbeErrorCategory.CANCELLED
        val rootCauses = generateSequence<Throwable>(error) { it.cause }.toList()
        if (rootCauses.any { it is AddressFamilyUnavailableException }) {
            return ProbeErrorCategory.ADDRESS_FAMILY_UNAVAILABLE
        }
        if (rootCauses.any { it is UnknownHostException }) {
            return ProbeErrorCategory.DNS_FAILED
        }
        if (rootCauses.any { it is SSLException }) {
            return ProbeErrorCategory.TLS_FAILED
        }
        if (rootCauses.any { it is SocketTimeoutException || it is InterruptedIOException }) {
            return if (recorder.sawRequestHeadersStart.get()) {
                ProbeErrorCategory.READ_TIMEOUT
            } else {
                ProbeErrorCategory.CONNECT_TIMEOUT
            }
        }
        if (!recorder.sawConnectionAcquired.get()) {
            return ProbeErrorCategory.CONNECT_FAILED
        }
        return ProbeErrorCategory.IO_ERROR
    }

    private suspend fun awaitOkHttpCall(call: Call): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                cancelCallSafely(call, baseOkHttpClient.dispatcher.executorService)
            }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) {
                            continuation.resumeWith(Result.failure(e))
                        }
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (continuation.isActive) {
                            continuation.resume(response)
                        } else {
                            response.close()
                        }
                    }
                },
            )
        }

    private class ProbeEventRecorder(
        private val clockMillis: () -> Long,
    ) : EventListener() {
        private var callStartAtMs: Long = 0L
        private var dnsStartAtMs: Long? = null
        private var connectStartAtMs: Long? = null
        private var tlsStartAtMs: Long? = null

        @Volatile var dnsDurationMs: Long? = null
        @Volatile var tcpConnectDurationMs: Long? = null
        @Volatile var tlsHandshakeDurationMs: Long? = null
        @Volatile var responseHeadersStartMs: Long? = null
        @Volatile var observedProxyType: Proxy.Type = Proxy.Type.DIRECT
        @Volatile var localSocketFamily: IpAddressFamily? = null
        @Volatile var negotiatedProtocol: String? = null

        val connectAttemptCount = AtomicInteger(0)
        val sawConnectionAcquired = AtomicBoolean(false)
        val sawRequestHeadersStart = AtomicBoolean(false)
        val events: MutableList<String> = Collections.synchronizedList(mutableListOf())

        override fun callStart(call: Call) {
            callStartAtMs = clockMillis()
            events.add("callStart")
        }

        override fun dnsStart(call: Call, domainName: String) {
            dnsStartAtMs = clockMillis()
            events.add("dnsStart")
        }

        override fun dnsEnd(
            call: Call,
            domainName: String,
            inetAddressList: List<InetAddress>,
        ) {
            dnsStartAtMs?.let { started ->
                dnsDurationMs = (clockMillis() - started).coerceAtLeast(0L)
            }
            events.add("dnsEnd(${inetAddressList.size})")
        }

        override fun connectStart(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
        ) {
            connectAttemptCount.incrementAndGet()
            connectStartAtMs = clockMillis()
            if (proxy.type() != Proxy.Type.DIRECT) {
                observedProxyType = proxy.type()
            }
            localSocketFamily = addressToFamily(inetSocketAddress.address)
            events.add("connectStart(${proxy.type()})")
        }

        override fun secureConnectStart(call: Call) {
            tlsStartAtMs = clockMillis()
            events.add("secureConnectStart")
        }

        override fun secureConnectEnd(call: Call, handshake: Handshake?) {
            tlsStartAtMs?.let { started ->
                tlsHandshakeDurationMs = (clockMillis() - started).coerceAtLeast(0L)
            }
            events.add("secureConnectEnd")
        }

        override fun connectEnd(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
            protocol: Protocol?,
        ) {
            connectStartAtMs?.let { started ->
                val totalConnect = (clockMillis() - started).coerceAtLeast(0L)
                val tlsPart = tlsHandshakeDurationMs ?: 0L
                tcpConnectDurationMs = (totalConnect - tlsPart).coerceAtLeast(0L)
            }
            localSocketFamily = addressToFamily(inetSocketAddress.address)
            if (protocol != null) {
                negotiatedProtocol = protocol.toString()
            }
            events.add("connectEnd(${protocol ?: "unknown"})")
        }

        override fun connectFailed(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
            protocol: Protocol?,
            ioe: IOException,
        ) {
            if (proxy.type() != Proxy.Type.DIRECT) {
                observedProxyType = proxy.type()
            }
            events.add("connectFailed")
        }

        override fun connectionAcquired(call: Call, connection: Connection) {
            sawConnectionAcquired.set(true)
            connection.socket().inetAddress?.let { remoteAddr ->
                localSocketFamily = addressToFamily(remoteAddr)
            }
            negotiatedProtocol = connection.protocol().toString()
            events.add("connectionAcquired")
        }

        override fun requestHeadersStart(call: Call) {
            sawRequestHeadersStart.set(true)
            events.add("requestHeadersStart")
        }

        override fun responseHeadersStart(call: Call) {
            responseHeadersStartMs = (clockMillis() - callStartAtMs).coerceAtLeast(0L)
            events.add("responseHeadersStart")
        }

        override fun callEnd(call: Call) {
            events.add("callEnd")
        }

        override fun callFailed(call: Call, ioe: IOException) {
            events.add("callFailed")
        }

        private fun addressToFamily(address: InetAddress?): IpAddressFamily? {
            return when (address) {
                is Inet4Address -> IpAddressFamily.IPV4
                is Inet6Address -> IpAddressFamily.IPV6
                else -> null
            }
        }

        fun toResult(
            sampleIndex: Int,
            armId: String,
            candidateId: String?,
            succeeded: Boolean,
            httpStatusCode: Int?,
            retryAfterSeconds: Long?,
            errorCategory: ProbeErrorCategory?,
            fullElapsedMs: Long,
            negotiatedProtocol: String?,
            responseBodyBytesRead: Int,
            bodyComplete: Boolean,
            serverPayload: ParsedServerProbePayload?,
        ): ProbeSampleResult {
            val reused = sawConnectionAcquired.get() && connectAttemptCount.get() == 0
            val snapshotEvents = synchronized(events) { events.toList() }
            return ProbeSampleResult(
                sampleIndex = sampleIndex,
                armId = armId,
                candidateId = candidateId,
                succeeded = succeeded,
                httpStatusCode = httpStatusCode,
                retryAfterSeconds = retryAfterSeconds,
                errorCategory = errorCategory,
                fullResponseElapsedMs = fullElapsedMs,
                responseHeadersStartMs = responseHeadersStartMs,
                dnsDurationMs = dnsDurationMs,
                tcpConnectDurationMs = tcpConnectDurationMs,
                tlsHandshakeDurationMs = tlsHandshakeDurationMs,
                connectionReused = reused,
                connectAttemptCount = connectAttemptCount.get(),
                observedProxyType = observedProxyType,
                localSocketFamily = localSocketFamily,
                negotiatedProtocol = negotiatedProtocol,
                responseBodyBytesRead = responseBodyBytesRead,
                bodyComplete = bodyComplete,
                serverPayload = serverPayload,
                observedEvents = snapshotEvents,
            )
        }
    }
}

/**
 * Value-equal DNS-free [Dns] adapter that maps [relayHostname] directly to [rawIpBytes] via
 * `InetAddress.getByAddress` without invoking OS DNS.
 *
 * Implementing value-based [equals] and [hashCode] is essential for OkHttp connection pooling:
 * `okhttp3.Address.equalsNonHost` compares `this.dns == that.dns`, so anonymous `Dns { ... }`
 * lambdas would fail equality and force a cold TCP+TLS handshake on every call!
 */
internal class PinnedHostIpDns(
    relayHostname: String,
    rawIpBytes: ByteArray,
    private val fallbackDns: Dns? = null,
) : Dns {
    private val normalizedHost: String = relayHostname.trim().trimEnd('.').lowercase(Locale.ROOT)
    private val ipBytes: ByteArray = rawIpBytes.clone()

    override fun lookup(hostname: String): List<InetAddress> {
        if (hostname.trim().trimEnd('.').equals(normalizedHost, ignoreCase = true)) {
            return listOf(InetAddress.getByAddress(normalizedHost, ipBytes.clone()))
        }
        val fb = fallbackDns ?: throw UnknownHostException("Pinned DNS refused unexpected host: $hostname")
        return fb.lookup(hostname)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PinnedHostIpDns) return false
        return normalizedHost == other.normalizedHost && ipBytes.contentEquals(other.ipBytes)
    }

    override fun hashCode(): Int {
        return 31 * normalizedHost.hashCode() + ipBytes.contentHashCode()
    }
}

internal class AddressFamilyFilteredDns(
    private val baseDns: Dns,
    private val family: IpAddressFamily,
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val all = baseDns.lookup(hostname)
        val filtered = when (family) {
            IpAddressFamily.IPV4 -> all.filterIsInstance<Inet4Address>()
            IpAddressFamily.IPV6 -> all.filterIsInstance<Inet6Address>()
        }
        if (filtered.isEmpty()) {
            throw AddressFamilyUnavailableException(family)
        }
        return filtered
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is AddressFamilyFilteredDns) return false
        return baseDns == other.baseDns && family == other.family
    }

    override fun hashCode(): Int {
        return 31 * baseDns.hashCode() + family.hashCode()
    }
}

