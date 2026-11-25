package org.mewx.wenku8.data.remote.transport

import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.Call
import okhttp3.Callback
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
import org.mewx.wenku8.api.SealedRequest
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.media.WenkuHttpsRedirectUpgradeInterceptor
import org.mewx.wenku8.data.media.WenkuImageUrlPolicy
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsEndpointConfig
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsEndpointResolution
import org.mewx.wenku8.data.remote.diagnostics.OkHttpProbeTransport
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.GZIPInputStream
import kotlin.coroutines.resume

data class PinnedRelayCandidate(
    val candidateId: String,
    val relayHost: String,
    val rawIpBytes: ByteArray,
    val warmConnectionPool: ConnectionPool? = null,
)

data class ActiveRelayRoute(
    val generationId: Long,
    val pinnedCandidate: PinnedRelayCandidate? = null,
)

/**
 * Narrow routing hook consumed by [RelayHttpTransport] and implemented by `RelayRouteController`.
 */
interface RelayRouteProvider {
    fun currentRouteForHost(host: String): ActiveRelayRoute

    fun fallbackRouteAfterCandidateFailure(
        failedGenerationId: Long,
        failedCandidateId: String,
        failureBeforeSend: Boolean,
    ): ActiveRelayRoute

    fun onPassiveCallCompleted(
        generationId: Long,
        candidateId: String?,
        succeeded: Boolean,
        connectionSetupMs: Long?,
    ) {}

    fun onForegroundCallStarted() {}
    fun onForegroundCallFinished() {}
}

object DefaultSystemDnsRouteProvider : RelayRouteProvider {
    private val defaultRoute = ActiveRelayRoute(generationId = 1L, pinnedCandidate = null)

    override fun currentRouteForHost(host: String): ActiveRelayRoute = defaultRoute

    override fun fallbackRouteAfterCandidateFailure(
        failedGenerationId: Long,
        failedCandidateId: String,
        failureBeforeSend: Boolean,
    ): ActiveRelayRoute = defaultRoute
}

/**
 * Abstracts `PHPSESSID` session cookie state for sealed `/v2/api` requests.
 */
interface WenkuSessionCookieStore {
    fun getPhpSessionId(): String
    fun setPhpSessionId(sessionId: String)
    fun clearPhpSessionId() {}
}

/**
 * Thread-safe, pure-Kotlin in-memory `PHPSESSID` store (never logs session tokens to logcat).
 */
class InMemorySessionCookieStore : WenkuSessionCookieStore {
    private val lock = Any()

    @Volatile
    private var sessionId: String = ""

    override fun getPhpSessionId(): String = sessionId

    override fun setPhpSessionId(sessionId: String) {
        val trimmed = sessionId.trim()
        if (trimmed.isEmpty()) return
        synchronized(lock) {
            this.sessionId = trimmed
        }
    }

    override fun clearPhpSessionId() {
        synchronized(lock) {
            sessionId = ""
        }
    }
}

/**
 * Production OkHttp 5 transport implementing [WenkuTransport] with `WK8-RNE-1` per-attempt request
 * sealing, dynamic candidate-IP routing, automatic System-DNS fallback, bounded `401 CLOCK_SKEW`
 * self-healing, and `426 Upgrade Required` / `X-WK8-Update-Hint` signaling.
 */
class RelayHttpTransport(
    private val baseOkHttpClient: OkHttpClient = OkHttpProbeTransport.defaultSharedBaseOkHttpClient(),
    private val routeProvider: RelayRouteProvider = DefaultSystemDnsRouteProvider,
    private val directGetCallFactory: Call.Factory? = null,
    private val sessionCookieStore: WenkuSessionCookieStore = InMemorySessionCookieStore(),
    private val systemDns: Dns = Dns.SYSTEM,
    private val endpointConfiguredResolver: () -> Boolean = {
        DiagnosticsEndpointConfig.resolveFromCompiledApi() is DiagnosticsEndpointResolution.Available
    },
    private val allowedRelayHostsProvider: () -> Set<String> = { defaultAllowedRelayHosts() },
    private val requestSealer: (rawActionQuery: String, exactCookieHeader: String?) -> SealedRequest? = { action, cookie ->
        try {
            Wenku8API.sealV2Request(action, cookie)
        } catch (_: LinkageError) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
    },
    private val serverClockOffsetUpdater: (Long) -> Unit = { serverTimeMs ->
        try {
            Wenku8API.updateServerClockOffset(serverTimeMs)
        } catch (_: LinkageError) {
            // Ignored on host JVM without JNI
        } catch (_: UnsupportedOperationException) {
            // Ignored on api-stub
        }
    },
    private val onUpgradeRequired: (minVersionCode: Int?, minVersionName: String?) -> Unit = { _, _ -> },
    private val onSoftUpdateHint: (minVersionCode: Int?) -> Unit = {},
    private val monotonicClockMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val connectTimeoutMillis: Long = 3_000L,
    private val readTimeoutMillis: Long = 5_000L,
    private val totalPostDeadlineMillis: Long = 10_000L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WenkuTransport {

    companion object {
        private const val MIN_REMAINING_BUDGET_FOR_FALLBACK_MS: Long = 350L
        private const val MAX_CONTROL_JSON_BYTES: Int = 4096
        private const val HEADER_UPDATE_HINT = "X-WK8-Update-Hint"
        private const val HEADER_MIN_VERSION = "X-WK8-Min-Version"
        private val lenientJson = Json { ignoreUnknownKeys = true }

        /**
         * Returns `true` when [host] belongs to Wenku8's direct website / image CDN origins
         * (`*.wenku8.com`, `*.wenku8.net`, `*.wenku8.cn`) or static notice host (`wenku8.mewx.org`),
         * which must **always** resolve via System DNS and never be pinned to Cloudflare relay IPs.
         */
        fun isDirectWenkuOriginOrCdnHost(host: String): Boolean {
            val normalized = host.trim().trimEnd('.').lowercase(Locale.ROOT)
            if (normalized.isEmpty()) return false
            return normalized == "wenku8.com" ||
                normalized.endsWith(".wenku8.com") ||
                normalized == "wenku8.net" ||
                normalized.endsWith(".wenku8.net") ||
                normalized == "wenku8.cn" ||
                normalized.endsWith(".wenku8.cn") ||
                normalized == "wenku8.mewx.org"
        }

        fun defaultAllowedRelayHosts(): Set<String> {
            val dynamicHost = try {
                val base = Wenku8API.BASE_URL.orEmpty()
                URI(base).host?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)
            } catch (_: Throwable) {
                null
            }
            return buildSet {
                add("mewx-android.wenku8.app")
                add("wenku8-relay.mewx.org")
                if (!dynamicHost.isNullOrBlank() && !isDirectWenkuOriginOrCdnHost(dynamicHost)) {
                    add(dynamicHost)
                }
            }
        }

        internal fun extractPhpSessionId(setCookieHeaders: List<String>): String? {
            for (header in setCookieHeaders) {
                val markerIndex = header.indexOf("PHPSESSID=")
                if (markerIndex >= 0) {
                    val valueStart = markerIndex + "PHPSESSID=".length
                    val semicolonIndex = header.indexOf(';', startIndex = valueStart)
                    val token = if (semicolonIndex >= 0) {
                        header.substring(valueStart, semicolonIndex)
                    } else {
                        header.substring(valueStart)
                    }.trim()
                    if (token.isNotEmpty()) {
                        return token
                    }
                }
            }
            return null
        }

        internal fun parseClockSkewServerTimeMs(rawBody: String): Long? {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
            return try {
                val obj = lenientJson.parseToJsonElement(trimmed).jsonObject
                val error = obj["error"]?.jsonPrimitive?.content
                if (error != "CLOCK_SKEW") return null
                obj["serverTimeMs"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }
            } catch (_: Exception) {
                null
            }
        }

        internal fun parseUpgradeRequiredPayload(rawBody: String): Pair<Int?, String?> {
            val trimmed = rawBody.trim()
            if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null to null
            return try {
                val obj = lenientJson.parseToJsonElement(trimmed).jsonObject
                val minCode = obj["minVersionCode"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
                val minName = obj["minVersionName"]?.jsonPrimitive?.content?.trim()?.takeIf { it.isNotEmpty() }
                minCode to minName
            } catch (_: Exception) {
                null to null
            }
        }
    }

    private class GenerationClientEntry(
        val generationId: Long,
        val candidateId: String?,
        val client: OkHttpClient,
        val connectionPool: ConnectionPool,
    ) {
        val inFlightCount = AtomicInteger(0)
        val retired = AtomicBoolean(false)

        fun acquire() {
            inFlightCount.incrementAndGet()
        }

        fun release() {
            if (inFlightCount.decrementAndGet() <= 0 && retired.get()) {
                OkHttpProbeTransport.evictPoolSafely(connectionPool)
            }
        }

        fun markRetiredAndEvictIfIdle() {
            if (retired.compareAndSet(false, true) && inFlightCount.get() <= 0) {
                OkHttpProbeTransport.evictPoolSafely(connectionPool)
            }
        }
    }

    private val generationLock = Any()
    @Volatile private var activeGenerationEntry: GenerationClientEntry? = null
    private val retiredEntries = ConcurrentHashMap<Long, GenerationClientEntry>()

    override val isEndpointConfigured: Boolean
        get() = endpointConfiguredResolver()

    override suspend fun postSealedAction(
        rawActionQuery: String,
        isIdempotentReadOnly: Boolean,
    ): RelayPostResult {
        if (rawActionQuery.isBlank()) {
            return RelayPostResult.NetworkFailure("empty_action_query")
        }

        val overallStartedAtMs = monotonicClockMillis()
        routeProvider.onForegroundCallStarted()
        return try {
            withContext(ioDispatcher) {
                val firstSealed = sealForCurrentSession(rawActionQuery)
                    ?: return@withContext RelayPostResult.NetworkFailure("sealing_unavailable")

                val targetHost = try {
                    URI(firstSealed.endpointUrl).host?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)
                } catch (_: Exception) {
                    null
                } ?: return@withContext RelayPostResult.NetworkFailure("invalid_endpoint_host")

                val isScopedRelayHost =
                    !isDirectWenkuOriginOrCdnHost(targetHost) && allowedRelayHostsProvider().contains(targetHost)
                val initialRoute = if (isScopedRelayHost) {
                    routeProvider.currentRouteForHost(targetHost)
                } else {
                    ActiveRelayRoute(generationId = 0L, pinnedCandidate = null)
                }

                val firstAttempt = executeSingleAttempt(
                    sealedPair = firstSealed,
                    route = initialRoute,
                    remainingCallBudgetMs = totalPostDeadlineMillis,
                )

                when (firstAttempt) {
                    is AttemptOutcome.HttpSuccess -> RelayPostResult.Success(firstAttempt.bodyBytes)

                    is AttemptOutcome.HttpUpgradeRequired -> {
                        onUpgradeRequired(firstAttempt.minVersionCode, firstAttempt.minVersionName)
                        RelayPostResult.UpgradeRequired(
                            minVersionCode = firstAttempt.minVersionCode,
                            minVersionName = firstAttempt.minVersionName,
                        )
                    }

                    is AttemptOutcome.HttpClockSkew -> {
                        serverClockOffsetUpdater(firstAttempt.serverTimeMs)
                        val remainingMs = remainingBudgetMs(overallStartedAtMs)
                        if (remainingMs < MIN_REMAINING_BUDGET_FOR_FALLBACK_MS) {
                            return@withContext RelayPostResult.AuthRejected
                        }
                        // Re-seal with updated server clock offset + fresh random nonce.
                        val clockRetrySealed = sealForCurrentSession(rawActionQuery)
                            ?: return@withContext RelayPostResult.NetworkFailure("sealing_unavailable")
                        val retryAttempt = executeSingleAttempt(
                            sealedPair = clockRetrySealed,
                            route = initialRoute,
                            remainingCallBudgetMs = remainingMs,
                        )
                        mapTerminalAttemptOutcome(retryAttempt)
                    }

                    AttemptOutcome.HttpAuthRejected -> RelayPostResult.AuthRejected
                    AttemptOutcome.HttpRateLimited -> RelayPostResult.RateLimited
                    is AttemptOutcome.HttpNon200 -> RelayPostResult.NetworkFailure("http_${firstAttempt.statusCode}")

                    is AttemptOutcome.TransportFailure -> {
                        val failedCandidate = initialRoute.pinnedCandidate
                            ?: return@withContext RelayPostResult.NetworkFailure(firstAttempt.cause.message)

                        val fallbackRoute = routeProvider.fallbackRouteAfterCandidateFailure(
                            failedGenerationId = initialRoute.generationId,
                            failedCandidateId = failedCandidate.candidateId,
                            failureBeforeSend = !firstAttempt.didStartSendingRequest,
                        )

                        val mayFallbackInSameCall =
                            !firstAttempt.didStartSendingRequest || isIdempotentReadOnly
                        if (!mayFallbackInSameCall) {
                            return@withContext RelayPostResult.NetworkFailure(firstAttempt.cause.message)
                        }

                        val remainingMs = remainingBudgetMs(overallStartedAtMs)
                        if (remainingMs < MIN_REMAINING_BUDGET_FOR_FALLBACK_MS) {
                            return@withContext RelayPostResult.NetworkFailure("deadline_exhausted")
                        }

                        // Always mint a fresh sealed envelope (new nonce & timestamp) for fallback retry!
                        val fallbackSealed = sealForCurrentSession(rawActionQuery)
                            ?: return@withContext RelayPostResult.NetworkFailure("sealing_unavailable")

                        val secondAttempt = executeSingleAttempt(
                            sealedPair = fallbackSealed,
                            route = fallbackRoute,
                            remainingCallBudgetMs = remainingMs,
                        )
                        if (secondAttempt is AttemptOutcome.HttpClockSkew) {
                            serverClockOffsetUpdater(secondAttempt.serverTimeMs)
                            val retryRemainingMs = remainingBudgetMs(overallStartedAtMs)
                            if (retryRemainingMs >= MIN_REMAINING_BUDGET_FOR_FALLBACK_MS) {
                                val clockRetrySealed = sealForCurrentSession(rawActionQuery)
                                    ?: return@withContext RelayPostResult.NetworkFailure("sealing_unavailable")
                                return@withContext mapTerminalAttemptOutcome(
                                    executeSingleAttempt(
                                        sealedPair = clockRetrySealed,
                                        route = fallbackRoute,
                                        remainingCallBudgetMs = retryRemainingMs,
                                    ),
                                )
                            }
                        }
                        mapTerminalAttemptOutcome(secondAttempt)
                    }
                }
            }
        } finally {
            routeProvider.onForegroundCallFinished()
        }
    }

    private fun remainingBudgetMs(overallStartedAtMs: Long): Long {
        val elapsedMs = (monotonicClockMillis() - overallStartedAtMs).coerceAtLeast(0L)
        return totalPostDeadlineMillis - elapsedMs
    }

    private fun mapTerminalAttemptOutcome(outcome: AttemptOutcome): RelayPostResult {
        return when (outcome) {
            is AttemptOutcome.HttpSuccess -> RelayPostResult.Success(outcome.bodyBytes)
            is AttemptOutcome.HttpUpgradeRequired -> {
                onUpgradeRequired(outcome.minVersionCode, outcome.minVersionName)
                RelayPostResult.UpgradeRequired(outcome.minVersionCode, outcome.minVersionName)
            }
            is AttemptOutcome.HttpClockSkew,
            AttemptOutcome.HttpAuthRejected,
            -> RelayPostResult.AuthRejected
            AttemptOutcome.HttpRateLimited -> RelayPostResult.RateLimited
            is AttemptOutcome.HttpNon200 -> RelayPostResult.NetworkFailure("http_${outcome.statusCode}")
            is AttemptOutcome.TransportFailure -> RelayPostResult.NetworkFailure(outcome.cause.message)
        }
    }

    private data class SealedAttemptPayload(
        val sealedRequest: SealedRequest,
        val exactCookieHeader: String?,
    ) {
        val endpointUrl: String get() = sealedRequest.endpointUrl
    }

    private fun sealForCurrentSession(rawActionQuery: String): SealedAttemptPayload? {
        val sessionId = sessionCookieStore.getPhpSessionId()
        val exactCookieHeader = if (sessionId.isNotEmpty()) "PHPSESSID=$sessionId" else null
        val sealed = requestSealer(rawActionQuery, exactCookieHeader) ?: return null
        if (sealed.endpointUrl.isBlank() ||
            sealed.endpointUrl.equals(WenkuTransport.UNKNOWN_ENDPOINT_SENTINEL, ignoreCase = true)
        ) {
            return null
        }
        return SealedAttemptPayload(sealedRequest = sealed, exactCookieHeader = exactCookieHeader)
    }

    private val cookieFreeGetClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .dns(systemDns)
            .addNetworkInterceptor(WenkuHttpsRedirectUpgradeInterceptor)
            .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(totalPostDeadlineMillis, TimeUnit.MILLISECONDS)
            .build()
    }

    override suspend fun getBytes(url: String): ByteArray? {
        val trimmed = url.trim()
        if (trimmed.isEmpty() ||
            trimmed.equals(WenkuTransport.UNKNOWN_ENDPOINT_SENTINEL, ignoreCase = true)
        ) {
            return null
        }
        val normalizedUrl = WenkuImageUrlPolicy.upgradeToHttpsIfSupported(trimmed)
        return withContext(ioDispatcher) {
            val request = try {
                Request.Builder()
                    .url(normalizedUrl)
                    .header("Accept-Encoding", "gzip")
                    .get()
                    .build()
            } catch (_: IllegalArgumentException) {
                return@withContext null
            }

            val cfFactory = directGetCallFactory as? CloudflareAcceleratedCallFactory
            val pinnedFamily = cfFactory?.activePinnedFamilyForHostOrNull(request.url.host)
            val primaryFactory: Call.Factory = directGetCallFactory ?: cookieFreeGetClient

            val firstAttempt = executeGetBytesAttempt(primaryFactory, request)
            if (firstAttempt != null) {
                return@withContext firstAttempt
            }

            // If the body transfer failed/truncated mid-stream on a pinned Cloudflare edge IP
            // (after HTTP 200 headers arrived), report the body failure and transparently retry
            // once over System DNS Happy Eyeballs.
            if (cfFactory != null && pinnedFamily != null) {
                cfFactory.reportPinnedBodyReadFailure(request.url.host, pinnedFamily)
                return@withContext executeGetBytesAttempt(
                    callFactory = Call.Factory { cfFactory.newDirectSystemDnsCall(it) },
                    request = request,
                )
            }
            null
        }
    }

    private suspend fun executeGetBytesAttempt(
        callFactory: Call.Factory,
        request: Request,
    ): ByteArray? {
        val call = callFactory.newCall(request)
        return try {
            awaitCall(call).use { response ->
                if (response.code != 200) return@use null
                readDecodedResponseBytesStrict(response)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            OkHttpProbeTransport.cancelCallSafely(call, baseOkHttpClient.dispatcher.executorService)
            throw cancelled
        } catch (_: IOException) {
            null
        }
    }

    private fun readDecodedResponseBytesStrict(response: Response): ByteArray {
        val contentEncoding = response.header("Content-Encoding")?.lowercase(Locale.ROOT).orEmpty()
        val expectedWireLength = response.body.contentLength()
        val isGzip = contentEncoding.contains("gzip")
        val decoded = readDecodedResponseBytes(response)
        // For uncompressed payloads (e.g. JPEGs / PNGs / WebP), verify we received the exact
        // declared Content-Length so a clean TCP FIN truncation never silently returns a half-image.
        if (!isGzip && expectedWireLength > 0L && decoded.size.toLong() != expectedWireLength) {
            throw IOException(
                "Truncated response body: expected $expectedWireLength bytes, got ${decoded.size}",
            )
        }
        return decoded
    }

    override fun clearSessionCookie() {
        sessionCookieStore.clearPhpSessionId()
    }

    internal fun activeConnectionPoolForTesting(): ConnectionPool? =
        activeGenerationEntry?.connectionPool

    private sealed interface AttemptOutcome {
        data class HttpSuccess(val bodyBytes: ByteArray) : AttemptOutcome
        data class HttpUpgradeRequired(
            val minVersionCode: Int?,
            val minVersionName: String?,
        ) : AttemptOutcome
        data class HttpClockSkew(val serverTimeMs: Long) : AttemptOutcome
        data object HttpAuthRejected : AttemptOutcome
        data object HttpRateLimited : AttemptOutcome
        data class HttpNon200(val statusCode: Int) : AttemptOutcome
        data class TransportFailure(
            val didStartSendingRequest: Boolean,
            val cause: IOException,
        ) : AttemptOutcome
    }

    private suspend fun executeSingleAttempt(
        sealedPair: SealedAttemptPayload,
        route: ActiveRelayRoute,
        remainingCallBudgetMs: Long,
    ): AttemptOutcome {
        val entry = obtainGenerationClient(route)
        entry.acquire()

        val boundaryTracker = CallSendBoundaryTracker(monotonicClockMillis)
        val perAttemptClient = entry.client.newBuilder()
            .callTimeout(remainingCallBudgetMs.coerceAtLeast(1L), TimeUnit.MILLISECONDS)
            .eventListener(boundaryTracker)
            .build()

        val sealed = sealedPair.sealedRequest
        val requestBuilder = Request.Builder()
            .url(sealed.endpointUrl)
            .header("Accept-Encoding", "gzip")
            .post(sealed.body.toRequestBody(sealed.contentType.toMediaType()))

        for ((headerName, headerValue) in sealed.headers) {
            requestBuilder.header(headerName, headerValue)
        }
        sealedPair.exactCookieHeader?.let { cookieHeader ->
            requestBuilder.header("Cookie", cookieHeader)
        }

        val call = perAttemptClient.newCall(requestBuilder.build())
        try {
            val response = awaitCall(call)
            response.use { httpResponse ->
                extractPhpSessionId(httpResponse.headers("Set-Cookie"))?.let { updatedSession ->
                    sessionCookieStore.setPhpSessionId(updatedSession)
                }

                routeProvider.onPassiveCallCompleted(
                    generationId = route.generationId,
                    candidateId = route.pinnedCandidate?.candidateId,
                    succeeded = true,
                    connectionSetupMs = boundaryTracker.connectSetupDurationMs,
                )

                return when (httpResponse.code) {
                    200 -> {
                        if (httpResponse.header(HEADER_UPDATE_HINT)?.trim() == "1") {
                            val hintMinVersion = httpResponse.header(HEADER_MIN_VERSION)?.trim()?.toIntOrNull()
                            onSoftUpdateHint(hintMinVersion)
                        }
                        AttemptOutcome.HttpSuccess(readDecodedResponseBytes(httpResponse))
                    }

                    426 -> {
                        val controlBody = readBoundedControlBody(httpResponse)
                        val (minCode, minName) = parseUpgradeRequiredPayload(controlBody)
                        AttemptOutcome.HttpUpgradeRequired(minCode, minName)
                    }

                    401 -> {
                        val controlBody = readBoundedControlBody(httpResponse)
                        val serverTimeMs = parseClockSkewServerTimeMs(controlBody)
                        if (serverTimeMs != null) {
                            AttemptOutcome.HttpClockSkew(serverTimeMs)
                        } else {
                            AttemptOutcome.HttpAuthRejected
                        }
                    }

                    403 -> AttemptOutcome.HttpAuthRejected
                    429 -> AttemptOutcome.HttpRateLimited
                    else -> AttemptOutcome.HttpNon200(httpResponse.code)
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            OkHttpProbeTransport.cancelCallSafely(call, baseOkHttpClient.dispatcher.executorService)
            throw cancelled
        } catch (ioError: IOException) {
            routeProvider.onPassiveCallCompleted(
                generationId = route.generationId,
                candidateId = route.pinnedCandidate?.candidateId,
                succeeded = false,
                connectionSetupMs = boundaryTracker.connectSetupDurationMs,
            )
            return AttemptOutcome.TransportFailure(
                didStartSendingRequest = boundaryTracker.sawRequestSendStarted.get(),
                cause = ioError,
            )
        } finally {
            entry.release()
            cleanupRetiredPools()
        }
    }

    private fun readBoundedControlBody(response: Response): String {
        return try {
            val bytes = readDecodedResponseBytes(response, maxBytes = MAX_CONTROL_JSON_BYTES)
            String(bytes, StandardCharsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    private fun obtainGenerationClient(route: ActiveRelayRoute): GenerationClientEntry {
        synchronized(generationLock) {
            val current = activeGenerationEntry
            if (current != null &&
                current.generationId == route.generationId &&
                current.candidateId == route.pinnedCandidate?.candidateId
            ) {
                return current
            }

            if (current != null) {
                retiredEntries[current.generationId] = current
                current.markRetiredAndEvictIfIdle()
            }

            val pinned = route.pinnedCandidate
            val pool = pinned?.warmConnectionPool ?: ConnectionPool(5, 5, TimeUnit.MINUTES)
            val dnsAdapter = if (pinned == null) {
                systemDns
            } else {
                org.mewx.wenku8.data.remote.diagnostics.PinnedHostIpDns(
                    relayHostname = pinned.relayHost,
                    rawIpBytes = pinned.rawIpBytes,
                    fallbackDns = systemDns,
                )
            }

            val client = baseOkHttpClient.newBuilder()
                .cookieJar(CookieJar.NO_COOKIES) // Managed explicitly via WenkuSessionCookieStore
                .connectionPool(pool)
                .dns(dnsAdapter)
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .fastFallback(pinned == null)
                .retryOnConnectionFailure(false)
                // Never follow HTTP redirects on sealed /v2/api POSTs (prevents Cookie/envelope exfiltration).
                .followRedirects(false)
                .followSslRedirects(false)
                .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .build()

            val created = GenerationClientEntry(
                generationId = route.generationId,
                candidateId = pinned?.candidateId,
                client = client,
                connectionPool = pool,
            )
            activeGenerationEntry = created
            return created
        }
    }

    private fun cleanupRetiredPools() {
        val iterator = retiredEntries.entries.iterator()
        while (iterator.hasNext()) {
            val (_, entry) = iterator.next()
            if (entry.inFlightCount.get() <= 0) {
                OkHttpProbeTransport.evictPoolSafely(
                    entry.connectionPool,
                    baseOkHttpClient.dispatcher.executorService,
                )
                iterator.remove()
            }
        }
    }

    private fun readDecodedResponseBytes(response: Response, maxBytes: Int = Int.MAX_VALUE): ByteArray {
        val contentEncoding = response.header("Content-Encoding")?.lowercase(Locale.ROOT).orEmpty()
        val rawStream = response.body.byteStream()
        val effectiveStream = if (contentEncoding.contains("gzip")) {
            val buffered = BufferedInputStream(rawStream)
            buffered.mark(1)
            if (buffered.read() == -1) {
                buffered.close()
                return ByteArray(0)
            }
            buffered.reset()
            GZIPInputStream(buffered)
        } else {
            rawStream
        }
        effectiveStream.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            var total = 0
            while (total < maxBytes) {
                val toRead = minOf(buffer.size, maxBytes - total)
                val read = input.read(buffer, 0, toRead)
                if (read == -1) break
                out.write(buffer, 0, read)
                total += read
            }
            return out.toByteArray()
        }
    }

    private suspend fun awaitCall(call: Call): Response =
        suspendCancellableCoroutine { continuation ->
            continuation.invokeOnCancellation {
                OkHttpProbeTransport.cancelCallSafely(call, baseOkHttpClient.dispatcher.executorService)
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

    private class CallSendBoundaryTracker(
        private val clockMillis: () -> Long,
    ) : EventListener() {
        private var connectStartedAtMs: Long? = null
        @Volatile var connectSetupDurationMs: Long? = null
        val sawRequestSendStarted = AtomicBoolean(false)

        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            connectStartedAtMs = clockMillis()
        }

        override fun connectEnd(
            call: Call,
            inetSocketAddress: InetSocketAddress,
            proxy: Proxy,
            protocol: Protocol?,
        ) {
            connectStartedAtMs?.let { started ->
                connectSetupDurationMs = (clockMillis() - started).coerceAtLeast(0L)
            }
        }

        override fun secureConnectEnd(call: Call, handshake: Handshake?) {
            connectStartedAtMs?.let { started ->
                connectSetupDurationMs = (clockMillis() - started).coerceAtLeast(0L)
            }
        }

        override fun requestHeadersStart(call: Call) {
            sawRequestSendStarted.set(true)
        }

        override fun requestBodyStart(call: Call) {
            sawRequestSendStarted.set(true)
        }
    }
}
