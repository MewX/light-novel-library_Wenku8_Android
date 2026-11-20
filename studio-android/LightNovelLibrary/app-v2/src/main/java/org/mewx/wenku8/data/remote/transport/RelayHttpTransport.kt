package org.mewx.wenku8.data.remote.transport

import android.content.ContentValues
import android.os.SystemClock
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
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
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.network.LightUserSession
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsEndpointConfig
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsEndpointResolution
import org.mewx.wenku8.data.remote.diagnostics.OkHttpProbeTransport
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
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
 * Narrow routing hook consumed by [RelayHttpTransport] (`P2.1`) and implemented by
 * `RelayRouteController` (`P2.2`).
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
 * Abstracts `LightUserSession` `PHPSESSID` cookie state so JVM `MockWebServer` tests run cleanly
 * while production requests stay 100% synchronized with `:api`'s [LightUserSession].
 */
interface WenkuSessionCookieStore {
    fun getPhpSessionId(): String
    fun setPhpSessionId(sessionId: String)
    fun clearPhpSessionId() {}
}

object NativeLightUserSessionCookieStore : WenkuSessionCookieStore {
    /**
     * `LightUserSession.setSession("")` ignores empty strings (`if (s != null && !s.isEmpty())`),
     * so we track an explicit local clear flag on logout and only re-enable reading `PHPSESSID`
     * once a fresh `Set-Cookie: PHPSESSID=...` arrives via [setPhpSessionId].
     */
    @Volatile
    private var clearedLocally: Boolean = false

    @Volatile
    private var localSessionOverride: String? = null

    override fun getPhpSessionId(): String {
        if (clearedLocally) return ""
        localSessionOverride?.let { if (it.isNotEmpty()) return it }
        return try {
            LightUserSession.getSession().orEmpty()
        } catch (_: LinkageError) {
            ""
        } catch (_: UnsupportedOperationException) {
            ""
        }
    }

    override fun setPhpSessionId(sessionId: String) {
        if (sessionId.isBlank()) return
        clearedLocally = false
        localSessionOverride = sessionId
        try {
            LightUserSession.setSession(sessionId)
        } catch (_: LinkageError) {
            // Ignored on host JVM without JNI
        } catch (_: UnsupportedOperationException) {
            // Ignored on api-stub
        }
    }

    override fun clearPhpSessionId() {
        clearedLocally = true
        localSessionOverride = null
    }
}

/**
 * Production OkHttp 5 transport implementing [WenkuTransport] with dynamic candidate-IP routing
 * and automatic System-DNS fallback.
 *
 * ### Transport & Security Invariants
 * - **Full TLS SNI & Certificate Pinning to Relay Domain**:
 *   Even when [RelayRouteProvider] supplies a [PinnedRelayCandidate], the HTTP request URL and
 *   `Host` header remain `https://wenku8-relay.mewx.org/...` (or `wenku8-android.mewx.org`). Only
 *   the socket-level DNS lookup is overridden via `PinnedHostIpDns` (`InetAddress.getByAddress`),
 *   so TLS 1.3 SNI and X.509 certificate hostname verification are **never weakened**.
 * - **Per-Generation `ConnectionPool` & Warm HTTP/2 Handoff**:
 *   Maintains one dedicated `OkHttpClient` + `ConnectionPool` per route `generationId` (`fastFallback(true)`
 *   Happy Eyeballs on Default System DNS; `fastFallback(false)` + value-equal `PinnedHostIpDns` on
 *   pinned candidate routes so warm HTTP/2 sockets validated by `RelayRouteController` are reused
 *   without an extra TCP+TLS handshake) and safely evicts retired pools once in-flight calls finish.
 * - **Safe Pre-Send Automatic Fallback vs Post-Send Non-Replay**:
 *   Tracks exact byte transmission via `EventListener.requestHeadersStart`:
 *   1. If a pinned candidate IP fails *before* any HTTP request bytes leave the device (TCP connect
 *      timeout, RST, or TLS handshake failure), calls [RelayRouteProvider.fallbackRouteAfterCandidateFailure]
 *      (`failureBeforeSend = true`) to quarantine the broken IP and **transparently retries once**
 *      on Default System DNS within the remaining `10s` total POST deadline.
 *   2. If a failure occurs *after* request headers started transmitting (`failureBeforeSend = false`),
 *      revokes the pinned route for future calls without replaying potentially non-idempotent POST
 *      actions (such as login, bookshelf mutation, or review posting).
 * - **Legacy Wire & Session Compatibility**:
 *   Preserves `LightNetwork.LightHttpPostConnection`'s exact `&k=v` form encoding, transparent gzip
 *   handling, and `PHPSESSID` synchronization with `:api`'s [LightUserSession].
 */
class RelayHttpTransport(
    private val baseOkHttpClient: OkHttpClient = OkHttpProbeTransport.defaultSharedBaseOkHttpClient(),
    private val routeProvider: RelayRouteProvider = DefaultSystemDnsRouteProvider,
    private val sessionCookieStore: WenkuSessionCookieStore = NativeLightUserSessionCookieStore,
    private val systemDns: Dns = Dns.SYSTEM,
    private val endpointConfiguredResolver: () -> Boolean = {
        DiagnosticsEndpointConfig.resolveFromCompiledApi() is DiagnosticsEndpointResolution.Available
    },
    private val allowedRelayHostsProvider: () -> Set<String> = { defaultAllowedRelayHosts() },
    private val monotonicClockMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val connectTimeoutMillis: Long = 3_000L,
    private val readTimeoutMillis: Long = 5_000L,
    private val totalPostDeadlineMillis: Long = 10_000L,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WenkuTransport {

    companion object {
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded".toMediaType()
        private const val MIN_REMAINING_BUDGET_FOR_FALLBACK_MS: Long = 350L

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
                add("wenku8-relay.mewx.org")
                add("wenku8-android.mewx.org")
                if (!dynamicHost.isNullOrBlank() && !isDirectWenkuOriginOrCdnHost(dynamicHost)) {
                    add(dynamicHost)
                }
            }
        }

        internal fun encodeFormBodyBytes(values: ContentValues): ByteArray {
            // Preserves LightNetwork.LightHttpPostConnection's exact `&k1=v1&k2=v2` wire format.
            val sb = StringBuilder()
            for (key in values.keySet()) {
                val rawVal = values.get(key)
                if (rawVal is String) {
                    sb.append('&').append(key).append('=').append(rawVal)
                }
            }
            return sb.toString().toByteArray(StandardCharsets.UTF_8)
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

    override suspend fun postForm(endpointUrl: String, formParams: ContentValues): ByteArray? {
        return postForm(
            endpointUrl = endpointUrl,
            formParams = formParams,
            isIdempotentReadOnly = false,
        )
    }

    override suspend fun postForm(
        endpointUrl: String,
        formParams: ContentValues,
        isIdempotentReadOnly: Boolean,
    ): ByteArray? {
        if (endpointUrl.isBlank() ||
            endpointUrl.equals(LegacyLightNetworkTransport.UNKNOWN_ENDPOINT_SENTINEL, ignoreCase = true)
        ) {
            return null
        }

        val targetHost = try {
            URI(endpointUrl).host?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)
        } catch (_: Exception) {
            null
        } ?: return null

        val requestBodyBytes = encodeFormBodyBytes(formParams)
        val overallStartedAtMs = monotonicClockMillis()
        val isScopedRelayHost =
            !isDirectWenkuOriginOrCdnHost(targetHost) && allowedRelayHostsProvider().contains(targetHost)

        routeProvider.onForegroundCallStarted()
        return try {
            withContext(ioDispatcher) {
                val initialRoute = if (isScopedRelayHost) {
                    routeProvider.currentRouteForHost(targetHost)
                } else {
                    ActiveRelayRoute(generationId = 0L, pinnedCandidate = null)
                }

                val firstAttempt = executeSingleAttempt(
                    endpointUrl = endpointUrl,
                    requestBodyBytes = requestBodyBytes,
                    route = initialRoute,
                    remainingCallBudgetMs = totalPostDeadlineMillis,
                )

                when (firstAttempt) {
                    is AttemptOutcome.HttpSuccess -> firstAttempt.bodyBytes

                    is AttemptOutcome.HttpNon200 -> {
                        // Never rotate candidate IPs or replay on HTTP 4xx/5xx/429!
                        null
                    }

                    is AttemptOutcome.TransportFailure -> {
                        val failedCandidate = initialRoute.pinnedCandidate
                        if (failedCandidate == null) {
                            return@withContext null
                        }

                        // Notify RelayRouteController to revoke/quarantine the broken candidate.
                        val fallbackRoute = routeProvider.fallbackRouteAfterCandidateFailure(
                            failedGenerationId = initialRoute.generationId,
                            failedCandidateId = failedCandidate.candidateId,
                            failureBeforeSend = !firstAttempt.didStartSendingRequest,
                        )

                        // Replay policy:
                        // - Pre-send failure (`!didStartSendingRequest`): safe to fall back once for ALL calls.
                        // - Post-send failure (`didStartSendingRequest`): replay once ONLY if `isIdempotentReadOnly`.
                        val mayFallbackInSameCall =
                            !firstAttempt.didStartSendingRequest || isIdempotentReadOnly
                        if (!mayFallbackInSameCall) {
                            return@withContext null
                        }

                        val elapsedMs = (monotonicClockMillis() - overallStartedAtMs).coerceAtLeast(0L)
                        val remainingMs = totalPostDeadlineMillis - elapsedMs
                        if (remainingMs < MIN_REMAINING_BUDGET_FOR_FALLBACK_MS) {
                            return@withContext null
                        }

                        val secondAttempt = executeSingleAttempt(
                            endpointUrl = endpointUrl,
                            requestBodyBytes = requestBodyBytes,
                            route = fallbackRoute,
                            remainingCallBudgetMs = remainingMs,
                        )
                        (secondAttempt as? AttemptOutcome.HttpSuccess)?.bodyBytes
                    }
                }
            }
        } finally {
            routeProvider.onForegroundCallFinished()
        }
    }

    private val cookieFreeGetClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .dns(systemDns)
            .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .callTimeout(totalPostDeadlineMillis, TimeUnit.MILLISECONDS)
            .build()
    }

    override suspend fun getBytes(url: String): ByteArray? {
        val trimmed = url.trim()
        if (trimmed.isEmpty() ||
            trimmed.equals(LegacyLightNetworkTransport.UNKNOWN_ENDPOINT_SENTINEL, ignoreCase = true)
        ) {
            return null
        }
        return withContext(ioDispatcher) {
            val request = try {
                Request.Builder()
                    .url(trimmed)
                    .header("Accept-Encoding", "gzip")
                    .get()
                    .build()
            } catch (_: IllegalArgumentException) {
                return@withContext null
            }

            val call = cookieFreeGetClient.newCall(request)
            try {
                awaitCall(call).use { response ->
                    if (response.code != 200) return@use null
                    readDecodedResponseBytes(response)
                }
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                OkHttpProbeTransport.cancelCallSafely(call, baseOkHttpClient.dispatcher.executorService)
                throw cancelled
            } catch (_: IOException) {
                null
            }
        }
    }

    override fun clearSessionCookie() {
        sessionCookieStore.clearPhpSessionId()
    }

    internal fun activeConnectionPoolForTesting(): ConnectionPool? =
        activeGenerationEntry?.connectionPool

    private sealed interface AttemptOutcome {
        data class HttpSuccess(val bodyBytes: ByteArray) : AttemptOutcome
        data class HttpNon200(val statusCode: Int) : AttemptOutcome
        data class TransportFailure(
            val didStartSendingRequest: Boolean,
            val cause: IOException,
        ) : AttemptOutcome
    }

    private suspend fun executeSingleAttempt(
        endpointUrl: String,
        requestBodyBytes: ByteArray,
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

        val requestBuilder = Request.Builder()
            .url(endpointUrl)
            .header("Accept-Encoding", "gzip")
            .post(requestBodyBytes.toRequestBody(FORM_MEDIA_TYPE))

        val sessionId = sessionCookieStore.getPhpSessionId()
        if (sessionId.isNotEmpty()) {
            requestBuilder.header("Cookie", "PHPSESSID=$sessionId")
        }

        val call = perAttemptClient.newCall(requestBuilder.build())
        try {
            val response = awaitCall(call)
            response.use { httpResponse ->
                // Sync updated PHPSESSID session cookie back to LightUserSession
                extractPhpSessionId(httpResponse.headers("Set-Cookie"))?.let { updatedSession ->
                    sessionCookieStore.setPhpSessionId(updatedSession)
                }

                if (httpResponse.code != 200) {
                    routeProvider.onPassiveCallCompleted(
                        generationId = route.generationId,
                        candidateId = route.pinnedCandidate?.candidateId,
                        succeeded = true,
                        connectionSetupMs = boundaryTracker.connectSetupDurationMs,
                    )
                    return AttemptOutcome.HttpNon200(httpResponse.code)
                }

                val payloadBytes = readDecodedResponseBytes(httpResponse)
                routeProvider.onPassiveCallCompleted(
                    generationId = route.generationId,
                    candidateId = route.pinnedCandidate?.candidateId,
                    succeeded = true,
                    connectionSetupMs = boundaryTracker.connectSetupDurationMs,
                )
                return AttemptOutcome.HttpSuccess(payloadBytes)
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
                .followRedirects(pinned == null)
                .followSslRedirects(pinned == null)
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

    private fun readDecodedResponseBytes(response: Response): ByteArray {
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
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                out.write(buffer, 0, read)
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
