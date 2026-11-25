package org.mewx.wenku8.data.remote.transport

import android.os.SystemClock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.EventListener
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.mewx.wenku8.data.media.WenkuHttpsRedirectUpgradeInterceptor
import org.mewx.wenku8.data.media.WenkuImageUrlPolicy
import org.mewx.wenku8.data.remote.diagnostics.IpAddressFamily
import org.mewx.wenku8.data.remote.diagnostics.IpLiteralParser
import org.mewx.wenku8.data.remote.diagnostics.OkHttpProbeTransport
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.net.ssl.SSLException
import kotlin.reflect.KClass

/**
 * Snapshot of the currently active verified Cloudflare Anycast edge IP selected by
 * `RelayRouteController` for general Cloudflare-hosted GET traffic (image CDNs, redirect targets,
 * notices, version checks).
 */
data class ActiveCloudflareEdgeRoute(
    val generationId: Long,
    val candidateId: String,
    val rawIpBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ActiveCloudflareEdgeRoute) return false
        return generationId == other.generationId &&
            candidateId == other.candidateId &&
            rawIpBytes.contentEquals(other.rawIpBytes)
    }

    override fun hashCode(): Int {
        var result = generationId.hashCode()
        result = 31 * result + candidateId.hashCode()
        result = 31 * result + rawIpBytes.contentHashCode()
        return result
    }
}

/**
 * Read-only provider exposing the active preferred Cloudflare edge IP without allowing direct GET
 * traffic to mutate or quarantine the relay's route lease.
 */
fun interface CloudflareEdgeRouteProvider {
    fun currentActiveEdgeRouteOrNull(): ActiveCloudflareEdgeRoute?
}

/**
 * Redacted trace of attempted endpoints (`scheme://host`) attached via `Call.tag(MirrorAttemptTrace::class)`
 * when illustration mirror fallback runs, enabling accurate collapsed Artbook diagnostics without
 * exposing raw IPs, paths, or cookies.
 */
data class MirrorAttemptTrace(
    val attemptedEndpoints: List<String>,
)

/**
 * OkHttp [Call.Factory] that routes general app `GET`/`HEAD` requests (covers, chapter
 * illustrations, redirect targets like `pic.777743.xyz`, avatars, notices, and version checks on
 * `wenku8.mewx.org` / `wenku8.app`) through the active verified Cloudflare Anycast preferred IP
 * whenever `"自动优化连接"` is active, while maintaining strict fault isolation and automatic
 * fallback to [Dns.SYSTEM].
 *
 * ### Safety & Isolation Guarantees
 * 1. **Dedicated Connection Pool**: Pinned generations own their own [ConnectionPool], completely
 *    isolated from `RelayHttpTransport`'s sealed API pool.
 * 2. **Strict Non-Cloudflare / Local Exclusion**: Numeric IP literals (e.g. domestic DoH
 *    `223.5.5.5`, `127.0.0.1`), `localhost`, `.local`, `.internal`, and single-label hostnames
 *    always resolve via [systemDns]. Non-`GET`/`HEAD` methods always execute directly on
 *    [systemDns].
 * 3. **Dynamic Redirect Acceleration**: Because DNS override is applied inside the pinned
 *    generation's [Dns], multi-hop redirects such as `pic.wenku8.com` -> `pic.777743.xyz` (or any
 *    future Cloudflare-hosted image domain) are accelerated end-to-end without hardcoding redirect
 *    domains.
 * 4. **Zero Relay Lease Pollution + Transparent System-DNS Fallback**: If a host fails TLS/connect
 *    or returns a Cloudflare edge routing error (`409`/`421`/`520..530` or non-image CF `403`),
 *    that specific host is cooled down locally onto [systemDns] and the call is transparently
 *    retried once via [systemDns] without ever revoking or quarantining the relay's preferred IP.
 */
class CloudflareAcceleratedCallFactory(
    private val baseOkHttpClient: OkHttpClient = OkHttpProbeTransport.defaultSharedBaseOkHttpClient(),
    private val edgeRouteProvider: CloudflareEdgeRouteProvider,
    private val systemDns: Dns = Dns.SYSTEM,
    private val networkInterceptors: List<Interceptor> = emptyList(),
    private val connectTimeoutMillis: Long = DEFAULT_CONNECT_TIMEOUT_MS,
    private val pinnedConnectTimeoutMillis: Long = DEFAULT_PINNED_CONNECT_TIMEOUT_MS,
    private val readTimeoutMillis: Long = DEFAULT_READ_TIMEOUT_MS,
    private val writeTimeoutMillis: Long = DEFAULT_WRITE_TIMEOUT_MS,
    private val followRedirects: Boolean = true,
    private val followSslRedirects: Boolean = true,
    private val hardHostBypassDurationMillis: Long = DEFAULT_HARD_HOST_BYPASS_MS,
    private val transientHostBypassDurationMillis: Long = DEFAULT_TRANSIENT_HOST_BYPASS_MS,
    private val ipv6BulkBypassDurationMillis: Long = DEFAULT_IPV6_BULK_BYPASS_MS,
    private val mirrorCooldownDurationMillis: Long = DEFAULT_MIRROR_COOLDOWN_MS,
    private val transientFailuresBeforeBypass: Int = DEFAULT_TRANSIENT_FAILURES_BEFORE_BYPASS,
    private val monotonicClockMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val fallbackExecutor: Executor = baseOkHttpClient.dispatcher.executorService,
) : Call.Factory {

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS: Long = 12_000L
        const val DEFAULT_PINNED_CONNECT_TIMEOUT_MS: Long = 6_000L
        const val DEFAULT_READ_TIMEOUT_MS: Long = 25_000L
        const val DEFAULT_WRITE_TIMEOUT_MS: Long = 15_000L
        const val DEFAULT_HARD_HOST_BYPASS_MS: Long = 30L * 60L * 1_000L
        const val DEFAULT_TRANSIENT_HOST_BYPASS_MS: Long = 10L * 60L * 1_000L
        const val DEFAULT_IPV6_BULK_BYPASS_MS: Long = 30L * 60L * 1_000L
        const val DEFAULT_MIRROR_COOLDOWN_MS: Long = 30L * 60L * 1_000L
        const val DEFAULT_TRANSIENT_FAILURES_BEFORE_BYPASS: Int = 2

        private val SAFE_IDEMPOTENT_METHODS = setOf("GET", "HEAD")

        // Cloudflare edge errors that indicate the target host is not served on Cloudflare or
        // encountered an edge-origin routing failure (Error 1001/1016/52x/530).
        private val CF_HARD_ROUTING_ERROR_CODES = setOf(409, 421, 525, 526, 530)
        private val CF_TRANSIENT_EDGE_ERROR_CODES = setOf(520, 521, 522, 523, 524)

        internal fun normalizeHost(host: String): String =
            host.trim().trimEnd('.').lowercase(Locale.ROOT)

        /**
         * Returns `true` when [rawHost] is a public-looking domain name eligible for Cloudflare
         * Anycast IP pinning (rejecting numeric IP literals, `localhost`, `.local`, `.internal`,
         * and single-label names).
         */
        internal fun isPinningCandidateHostname(rawHost: String): Boolean {
            val normalized = normalizeHost(rawHost)
            if (normalized.isEmpty()) return false
            val unbracketed = normalized.removePrefix("[").removeSuffix("]")
            if (IpLiteralParser.parseNumericIpBytes(unbracketed, requirePublicUnicast = false) != null) {
                return false
            }
            if (normalized == "localhost" ||
                normalized.endsWith(".localhost") ||
                normalized.endsWith(".local") ||
                normalized.endsWith(".internal") ||
                normalized.endsWith(".home.arpa")
            ) {
                return false
            }
            return normalized.contains('.')
        }

        internal fun classifyCloudflareFallbackResponse(response: Response): FallbackReason? {
            val serverHeader = response.header("Server").orEmpty()
            if (!serverHeader.equals("cloudflare", ignoreCase = true)) {
                return null
            }
            val code = response.code
            if (code in CF_HARD_ROUTING_ERROR_CODES) {
                return FallbackReason.HARD_NON_CF_OR_ROUTING_ERROR
            }
            if (code in CF_TRANSIENT_EDGE_ERROR_CODES) {
                return FallbackReason.TRANSIENT_EDGE_ERROR
            }
            if (code == 403) {
                val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                val isImagePayload = contentType.startsWith("image/")
                val isOriginOssError = !response.header("x-oss-ec").isNullOrBlank()
                if (!isImagePayload && !isOriginOssError) {
                    return FallbackReason.HARD_NON_CF_OR_ROUTING_ERROR
                }
            }
            return null
        }
    }

    internal enum class FallbackReason {
        HARD_NON_CF_OR_ROUTING_ERROR,
        TRANSIENT_EDGE_ERROR,
    }

    private class PinnedGenerationEntry(
        val generationId: Long,
        val candidateId: String,
        val family: IpAddressFamily,
        val client: OkHttpClient,
        val connectionPool: ConnectionPool,
    ) {
        val inFlightCount = AtomicInteger(0)
        val retired = AtomicBoolean(false)

        fun acquire() {
            inFlightCount.incrementAndGet()
        }

        fun release(executor: Executor) {
            if (inFlightCount.decrementAndGet() <= 0 && retired.get()) {
                OkHttpProbeTransport.evictPoolSafely(connectionPool, executor)
            }
        }

        fun markRetiredAndEvictIfIdle(executor: Executor) {
            if (retired.compareAndSet(false, true) && inFlightCount.get() <= 0) {
                OkHttpProbeTransport.evictPoolSafely(connectionPool, executor)
            }
        }
    }

    private val generationLock = Any()
    @Volatile private var activePinnedGeneration: PinnedGenerationEntry? = null
    private val retiredGenerations = ConcurrentHashMap<Long, PinnedGenerationEntry>()

    // Hosts known to be non-Cloudflare (TLS/SNI mismatch or CF routing error) -> bypassed for 30m.
    private val hardBypassedHostsUntilMonoMs = ConcurrentHashMap<String, Long>()

    // Hosts bypassed temporarily after repeated transient failures on the current pinned IP -> 10m.
    private val transientBypassedHostsUntilMonoMs = ConcurrentHashMap<String, Long>()
    private val consecutiveTransientFailuresByHost = ConcurrentHashMap<String, AtomicInteger>()

    // Circuit breaker when a pinned IPv6 edge fails mid-body on bulk media transfers (e.g. cellular
    // IPv6 PMTU blackholes / bulk throttling where 180B probes succeed but 1MB JPEGs stall).
    @Volatile private var ipv6EdgeBypassedUntilMonoMs: Long = 0L

    // Remembered healthy fallback mirror (e.g. `pic.wenku8.com` -> `pic.777743.xyz`) when the
    // primary illustration host is reset by DPI/SNI filtering, avoiding repeated RST stalls across
    // the remaining illustrations and thumbnails in a chapter.
    private data class ActiveMirrorOverride(
        val mirror: WenkuImageUrlPolicy.IllustrationMirror,
        val expiresAtMonoMs: Long,
    )

    private val preferredMirrorByHost = ConcurrentHashMap<String, ActiveMirrorOverride>()

    private val systemDnsClient: OkHttpClient by lazy {
        baseOkHttpClient.newBuilder()
            .cookieJar(CookieJar.NO_COOKIES)
            .dns(systemDns)
            .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
            .fastFallback(true)
            .followRedirects(followRedirects)
            .followSslRedirects(followSslRedirects)
            .connectTimeout(connectTimeoutMillis, TimeUnit.MILLISECONDS)
            .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
            .writeTimeout(writeTimeoutMillis, TimeUnit.MILLISECONDS)
            .addNetworkInterceptor(WenkuHttpsRedirectUpgradeInterceptor)
            .apply {
                for (interceptor in networkInterceptors) {
                    addNetworkInterceptor(interceptor)
                }
            }
            .build()
    }

    override fun newCall(request: Request): Call {
        val upgradedRequest = WenkuImageUrlPolicy.upgradeRequestUrlIfSupported(request)
        val method = upgradedRequest.method.uppercase(Locale.ROOT)
        if (method !in SAFE_IDEMPOTENT_METHODS) {
            return systemDnsClient.newCall(upgradedRequest)
        }

        val primaryHost = normalizeHost(upgradedRequest.url.host)
        val rememberedMirror = currentPreferredMirrorForHostOrNull(primaryHost)
        val candidateUrls = WenkuImageUrlPolicy.buildOrderedIllustrationAttemptUrls(
            url = upgradedRequest.url,
            preferredMirror = rememberedMirror,
        )
        if (candidateUrls.size > 1) {
            val attemptRequests = candidateUrls.map { candidateUrl ->
                if (candidateUrl == upgradedRequest.url) {
                    upgradedRequest
                } else {
                    upgradedRequest.newBuilder().url(candidateUrl).build()
                }
            }
            return MirrorFallbackCall(
                factory = this,
                originalRequest = upgradedRequest,
                primaryHost = primaryHost,
                attemptRequests = attemptRequests,
                directSystemDnsOnly = false,
            )
        }

        return newSingleUrlCall(upgradedRequest)
    }

    private fun newSingleUrlCall(request: Request): Call {
        val edgeRoute = edgeRouteProvider.currentActiveEdgeRouteOrNull()
        if (edgeRoute == null) {
            retireActivePinnedGenerationIfAny()
            return systemDnsClient.newCall(request)
        }

        val isIpv6Edge = edgeRoute.rawIpBytes.size == 16
        if (isIpv6Edge && isIpv6EdgeCurrentlyBypassed()) {
            return systemDnsClient.newCall(request)
        }

        val initialHost = normalizeHost(request.url.host)
        if (!shouldPinHost(initialHost)) {
            return systemDnsClient.newCall(request)
        }

        val pinnedGeneration = obtainPinnedGeneration(edgeRoute)
        return FallbackDelegatingCall(
            factory = this,
            originalRequest = request,
            pinnedGeneration = pinnedGeneration,
            systemDnsClient = systemDnsClient,
        )
    }

    /**
     * Creates a direct call using [systemDns] (`fastFallback(true)` Happy Eyeballs), bypassing any
     * pinned Cloudflare edge IP while preserving HTTPS upgrade and illustration mirror fallback.
     * Used for mid-body stream retries and explicit user reload actions.
     */
    fun newDirectSystemDnsCall(request: Request): Call {
        val upgradedRequest = WenkuImageUrlPolicy.upgradeRequestUrlIfSupported(request)
        val method = upgradedRequest.method.uppercase(Locale.ROOT)
        if (method in SAFE_IDEMPOTENT_METHODS) {
            val primaryHost = normalizeHost(upgradedRequest.url.host)
            val rememberedMirror = currentPreferredMirrorForHostOrNull(primaryHost)
            val candidateUrls = WenkuImageUrlPolicy.buildOrderedIllustrationAttemptUrls(
                url = upgradedRequest.url,
                preferredMirror = rememberedMirror,
            )
            if (candidateUrls.size > 1) {
                val attemptRequests = candidateUrls.map { candidateUrl ->
                    if (candidateUrl == upgradedRequest.url) {
                        upgradedRequest
                    } else {
                        upgradedRequest.newBuilder().url(candidateUrl).build()
                    }
                }
                return MirrorFallbackCall(
                    factory = this,
                    originalRequest = upgradedRequest,
                    primaryHost = primaryHost,
                    attemptRequests = attemptRequests,
                    directSystemDnsOnly = true,
                )
            }
        }
        return systemDnsClient.newCall(upgradedRequest)
    }

    /**
     * Returns the address family (`IPV4` or `IPV6`) that will be used if [host] is dialed through
     * the currently active pinned Cloudflare edge IP, or `null` when System DNS will be used.
     */
    fun activePinnedFamilyForHostOrNull(host: String): IpAddressFamily? {
        val edgeRoute = edgeRouteProvider.currentActiveEdgeRouteOrNull() ?: return null
        val family = IpAddressFamily.fromRawBytesOrNull(edgeRoute.rawIpBytes) ?: return null
        if (family == IpAddressFamily.IPV6 && isIpv6EdgeCurrentlyBypassed()) return null
        val normalized = normalizeHost(host)
        val effectiveHost = currentPreferredMirrorForHostOrNull(normalized)?.host ?: normalized
        return if (shouldPinHost(effectiveHost)) family else null
    }

    /**
     * Reports a mid-body stream failure (read timeout, truncated `Content-Length`, reset, or
     * non-image/corrupt payload) observed AFTER HTTP response headers arrived via a pinned edge IP.
     *
     * On IPv6 edges, a single bulk-body failure immediately trips [ipv6EdgeBypassedUntilMonoMs] so
     * subsequent image fetches fall back to System DNS Happy Eyeballs instead of stalling for 6h,
     * without ever revoking the relay's small-packet API lease.
     */
    fun reportPinnedBodyReadFailure(host: String, family: IpAddressFamily?) {
        if (family == IpAddressFamily.IPV6) {
            ipv6EdgeBypassedUntilMonoMs = monotonicClockMillis() + ipv6BulkBypassDurationMillis
        }
        recordTransientHostFailure(host)
    }

    /** Clears all host, mirror, & IPv6 bulk cooldown entries (e.g. when the user resets network routing). */
    fun clearHostBypasses() {
        hardBypassedHostsUntilMonoMs.clear()
        transientBypassedHostsUntilMonoMs.clear()
        consecutiveTransientFailuresByHost.clear()
        preferredMirrorByHost.clear()
        ipv6EdgeBypassedUntilMonoMs = 0L
    }

    internal fun currentPreferredMirrorForHostOrNull(host: String): WenkuImageUrlPolicy.IllustrationMirror? {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty()) return null
        val entry = preferredMirrorByHost[normalized] ?: return null
        return if (monotonicClockMillis() < entry.expiresAtMonoMs) {
            entry.mirror
        } else {
            preferredMirrorByHost.remove(normalized, entry)
            null
        }
    }

    private fun rememberPreferredMirror(
        primaryHost: String,
        mirror: WenkuImageUrlPolicy.IllustrationMirror,
    ) {
        val normalized = normalizeHost(primaryHost)
        if (normalized.isEmpty() || mirror.host == normalized) return
        preferredMirrorByHost[normalized] = ActiveMirrorOverride(
            mirror = mirror,
            expiresAtMonoMs = monotonicClockMillis() + mirrorCooldownDurationMillis,
        )
    }

    private fun clearPreferredMirror(primaryHost: String) {
        val normalized = normalizeHost(primaryHost)
        if (normalized.isNotEmpty()) {
            preferredMirrorByHost.remove(normalized)
        }
    }

    internal fun isIpv6EdgeCurrentlyBypassed(): Boolean {
        val until = ipv6EdgeBypassedUntilMonoMs
        if (until <= 0L) return false
        return if (monotonicClockMillis() < until) {
            true
        } else {
            ipv6EdgeBypassedUntilMonoMs = 0L
            false
        }
    }

    internal fun isHostCurrentlyBypassed(host: String): Boolean {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty()) return false
        val now = monotonicClockMillis()
        return isBypassActive(hardBypassedHostsUntilMonoMs, normalized, now) ||
            isBypassActive(transientBypassedHostsUntilMonoMs, normalized, now)
    }

    internal fun activePinnedConnectionPoolForTesting(): ConnectionPool? =
        activePinnedGeneration?.connectionPool

    private fun shouldPinHost(normalizedHost: String): Boolean {
        if (!isPinningCandidateHostname(normalizedHost)) return false
        return !isHostCurrentlyBypassed(normalizedHost)
    }

    private fun isBypassActive(
        map: ConcurrentHashMap<String, Long>,
        normalizedHost: String,
        nowMonoMs: Long,
    ): Boolean {
        val until = map[normalizedHost] ?: return false
        return if (nowMonoMs < until) {
            true
        } else {
            map.remove(normalizedHost, until)
            false
        }
    }

    private fun recordPinnedSuccess(host: String) {
        val normalized = normalizeHost(host)
        if (normalized.isNotEmpty()) {
            consecutiveTransientFailuresByHost.remove(normalized)
        }
    }

    private fun recordHardHostBypass(host: String) {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty() || !isPinningCandidateHostname(normalized)) return
        hardBypassedHostsUntilMonoMs[normalized] = monotonicClockMillis() + hardHostBypassDurationMillis
        consecutiveTransientFailuresByHost.remove(normalized)
    }

    private fun recordTransientHostFailure(host: String) {
        val normalized = normalizeHost(host)
        if (normalized.isEmpty() || !isPinningCandidateHostname(normalized)) return
        val counter = consecutiveTransientFailuresByHost.computeIfAbsent(normalized) { AtomicInteger(0) }
        if (counter.incrementAndGet() >= transientFailuresBeforeBypass) {
            transientBypassedHostsUntilMonoMs[normalized] =
                monotonicClockMillis() + transientHostBypassDurationMillis
            counter.set(0)
        }
    }

    private fun obtainPinnedGeneration(edgeRoute: ActiveCloudflareEdgeRoute): PinnedGenerationEntry {
        synchronized(generationLock) {
            val current = activePinnedGeneration
            if (current != null &&
                current.generationId == edgeRoute.generationId &&
                current.candidateId == edgeRoute.candidateId
            ) {
                return current
            }

            if (current != null) {
                retiredGenerations[current.generationId] = current
                current.markRetiredAndEvictIfIdle(fallbackExecutor)
            }

            // A fresh Cloudflare IP generation gets a clean slate for transient timeouts & IPv6
            // bulk cooldown while keeping hard non-CF host bypasses intact.
            transientBypassedHostsUntilMonoMs.clear()
            consecutiveTransientFailuresByHost.clear()
            ipv6EdgeBypassedUntilMonoMs = 0L

            val family = IpAddressFamily.fromRawBytesOrNull(edgeRoute.rawIpBytes) ?: IpAddressFamily.IPV4
            val pool = ConnectionPool(8, 5, TimeUnit.MINUTES)
            val dnsAdapter = DynamicCloudflareEdgeDns(
                generationId = edgeRoute.generationId,
                candidateId = edgeRoute.candidateId,
                rawIpBytes = edgeRoute.rawIpBytes.copyOf(),
                shouldPinHost = ::shouldPinHost,
                systemDns = systemDns,
            )

            val client = baseOkHttpClient.newBuilder()
                .cookieJar(CookieJar.NO_COOKIES)
                .connectionPool(pool)
                .dns(dnsAdapter)
                .protocols(listOf(Protocol.HTTP_2, Protocol.HTTP_1_1))
                .fastFallback(false)
                .retryOnConnectionFailure(false)
                .followRedirects(followRedirects)
                .followSslRedirects(followSslRedirects)
                .connectTimeout(pinnedConnectTimeoutMillis, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMillis, TimeUnit.MILLISECONDS)
                .writeTimeout(writeTimeoutMillis, TimeUnit.MILLISECONDS)
                .addNetworkInterceptor(WenkuHttpsRedirectUpgradeInterceptor)
                .apply {
                    for (interceptor in networkInterceptors) {
                        addNetworkInterceptor(interceptor)
                    }
                }
                .build()

            val created = PinnedGenerationEntry(
                generationId = edgeRoute.generationId,
                candidateId = edgeRoute.candidateId,
                family = family,
                client = client,
                connectionPool = pool,
            )
            activePinnedGeneration = created
            cleanupRetiredGenerations()
            return created
        }
    }

    private fun retireActivePinnedGenerationIfAny() {
        synchronized(generationLock) {
            val current = activePinnedGeneration ?: return
            activePinnedGeneration = null
            retiredGenerations[current.generationId] = current
            current.markRetiredAndEvictIfIdle(fallbackExecutor)
            clearHostBypasses()
            cleanupRetiredGenerations()
        }
    }

    private fun cleanupRetiredGenerations() {
        val iterator = retiredGenerations.entries.iterator()
        while (iterator.hasNext()) {
            val (_, entry) = iterator.next()
            if (entry.inFlightCount.get() <= 0) {
                OkHttpProbeTransport.evictPoolSafely(entry.connectionPool, fallbackExecutor)
                iterator.remove()
            }
        }
    }

    private class LastHostTracker(initialHost: String) : EventListener() {
        @Volatile var lastAttemptedHost: String = normalizeHost(initialHost)
        @Volatile var observedNonDirectProxy: Boolean = false

        override fun dnsStart(call: Call, domainName: String) {
            val normalized = normalizeHost(domainName)
            if (normalized.isNotEmpty()) {
                lastAttemptedHost = normalized
            }
        }

        override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
            if (proxy.type() != Proxy.Type.DIRECT) {
                observedNonDirectProxy = true
            }
        }
    }

    /**
     * Value-equal [Dns] adapter for a pinned Cloudflare generation. Resolves eligible hostnames
     * (including redirect targets) directly to [rawIpBytes] with zero OS DNS lookups, while
     * delegating bypassed/local/IP-literal hosts to [systemDns].
     */
    private class DynamicCloudflareEdgeDns(
        private val generationId: Long,
        private val candidateId: String,
        private val rawIpBytes: ByteArray,
        private val shouldPinHost: (String) -> Boolean,
        private val systemDns: Dns,
    ) : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val normalized = normalizeHost(hostname)
            return if (shouldPinHost(normalized)) {
                listOf(InetAddress.getByAddress(hostname, rawIpBytes))
            } else {
                systemDns.lookup(hostname)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is DynamicCloudflareEdgeDns) return false
            return generationId == other.generationId &&
                candidateId == other.candidateId &&
                rawIpBytes.contentEquals(other.rawIpBytes) &&
                systemDns == other.systemDns
        }

        override fun hashCode(): Int {
            var result = generationId.hashCode()
            result = 31 * result + candidateId.hashCode()
            result = 31 * result + rawIpBytes.contentHashCode()
            result = 31 * result + systemDns.hashCode()
            return result
        }
    }

    /**
     * Thread-safe OkHttp [Call] that attempts [pinnedGeneration] first and transparently retries
     * once on [systemDnsClient] if the pinned Cloudflare attempt fails or encounters a Cloudflare
     * edge routing error.
     */
    private class FallbackDelegatingCall(
        private val factory: CloudflareAcceleratedCallFactory,
        private val originalRequest: Request,
        private val pinnedGeneration: PinnedGenerationEntry,
        private val systemDnsClient: OkHttpClient,
    ) : Call {

        private val executed = AtomicBoolean(false)
        private val canceled = AtomicBoolean(false)
        private val activeCallRef = AtomicReference<Call?>(null)
        private val extraEventListeners = CopyOnWriteArrayList<EventListener>()
        private val tags = ConcurrentHashMap<KClass<*>, Any>()

        override fun request(): Request = originalRequest

        override fun isExecuted(): Boolean = executed.get()

        override fun isCanceled(): Boolean = canceled.get()

        override fun cancel() {
            if (canceled.compareAndSet(false, true)) {
                activeCallRef.get()?.let { active ->
                    OkHttpProbeTransport.cancelCallSafely(active, factory.fallbackExecutor)
                }
            }
        }

        override fun clone(): Call = factory.newCall(originalRequest)

        override fun timeout(): Timeout = activeCallRef.get()?.timeout() ?: Timeout.NONE

        override fun addEventListener(eventListener: EventListener) {
            extraEventListeners.add(eventListener)
            activeCallRef.get()?.addEventListener(eventListener)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> tag(type: KClass<T>): T? = tags[type] as T?

        @Suppress("UNCHECKED_CAST")
        override fun <T> tag(type: Class<out T>): T? = tags[type.kotlin] as T?

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T {
            return tags.computeIfAbsent(type) { computeIfAbsent() } as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T {
            return tags.computeIfAbsent(type.kotlin) { computeIfAbsent() } as T
        }

        private fun activateInnerCall(innerCall: Call): Boolean {
            for (listener in extraEventListeners) {
                innerCall.addEventListener(listener)
            }
            activeCallRef.set(innerCall)
            if (canceled.get()) {
                OkHttpProbeTransport.cancelCallSafely(innerCall, factory.fallbackExecutor)
                return false
            }
            return true
        }

        private fun buildPinnedAttemptCall(tracker: LastHostTracker): Call {
            return pinnedGeneration.client.newBuilder()
                .eventListener(tracker)
                .build()
                .newCall(originalRequest)
        }

        override fun execute(): Response {
            check(executed.compareAndSet(false, true)) { "Already Executed" }
            if (canceled.get()) throw IOException("Canceled")

            val tracker = LastHostTracker(originalRequest.url.host)
            val pinnedCall = buildPinnedAttemptCall(tracker)
            var deferredPinnedIoFailure: Pair<String, IOException>? = null
            pinnedGeneration.acquire()
            try {
                if (!activateInnerCall(pinnedCall)) {
                    throw IOException("Canceled")
                }
                val response = pinnedCall.execute()
                val fallbackReason = classifyCloudflareFallbackResponse(response)
                if (fallbackReason == null) {
                    factory.recordPinnedSuccess(response.request.url.host)
                    return wrapResponseBodyForFailureMonitoring(
                        response = response,
                        observedNonDirectProxy = tracker.observedNonDirectProxy,
                    )
                }
                val failedHost = response.request.url.host
                response.close()
                if (!tracker.observedNonDirectProxy) {
                    recordResponseFallback(failedHost, fallbackReason)
                }
            } catch (ioError: IOException) {
                if (canceled.get()) {
                    throw ioError
                }
                if (!tracker.observedNonDirectProxy) {
                    deferredPinnedIoFailure = tracker.lastAttemptedHost to ioError
                }
            } finally {
                pinnedGeneration.release(factory.fallbackExecutor)
                factory.cleanupRetiredGenerations()
            }

            if (canceled.get()) throw IOException("Canceled")
            val fallbackCall = systemDnsClient.newCall(originalRequest)
            if (!activateInnerCall(fallbackCall)) {
                throw IOException("Canceled")
            }
            val fallbackResponse = fallbackCall.execute()
            // Only blame the pinned edge IP if System DNS actually succeeded; if BOTH pinned and
            // System DNS fail (e.g. GFW RST against `pic.wenku8.com` or temporary loss of signal),
            // avoid falsely poisoning the IPv6 edge for healthy hosts like `pic.777743.xyz`!
            deferredPinnedIoFailure?.let { (failedHost, err) ->
                recordIoFailure(failedHost, err)
            }
            return fallbackResponse
        }

        override fun enqueue(responseCallback: Callback) {
            check(executed.compareAndSet(false, true)) { "Already Executed" }
            if (canceled.get()) {
                responseCallback.onFailure(this, IOException("Canceled"))
                return
            }

            val tracker = LastHostTracker(originalRequest.url.host)
            val pinnedCall = buildPinnedAttemptCall(tracker)
            pinnedGeneration.acquire()
            if (!activateInnerCall(pinnedCall)) {
                pinnedGeneration.release(factory.fallbackExecutor)
                factory.cleanupRetiredGenerations()
                responseCallback.onFailure(this, IOException("Canceled"))
                return
            }

            pinnedCall.enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        pinnedGeneration.release(factory.fallbackExecutor)
                        factory.cleanupRetiredGenerations()

                        if (canceled.get()) {
                            response.close()
                            responseCallback.onFailure(this@FallbackDelegatingCall, IOException("Canceled"))
                            return
                        }

                        val fallbackReason = classifyCloudflareFallbackResponse(response)
                        if (fallbackReason == null) {
                            factory.recordPinnedSuccess(response.request.url.host)
                            val monitored = wrapResponseBodyForFailureMonitoring(
                                response = response,
                                observedNonDirectProxy = tracker.observedNonDirectProxy,
                            )
                            responseCallback.onResponse(this@FallbackDelegatingCall, monitored)
                            return
                        }

                        val failedHost = response.request.url.host
                        response.close()
                        if (!tracker.observedNonDirectProxy) {
                            recordResponseFallback(failedHost, fallbackReason)
                        }
                        enqueueSystemDnsFallback(responseCallback, deferredPinnedIoFailure = null)
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        pinnedGeneration.release(factory.fallbackExecutor)
                        factory.cleanupRetiredGenerations()

                        if (canceled.get()) {
                            responseCallback.onFailure(this@FallbackDelegatingCall, e)
                            return
                        }

                        val deferredFailure = if (!tracker.observedNonDirectProxy) {
                            tracker.lastAttemptedHost to e
                        } else {
                            null
                        }
                        enqueueSystemDnsFallback(responseCallback, deferredPinnedIoFailure = deferredFailure)
                    }
                },
            )
        }

        private fun enqueueSystemDnsFallback(
            responseCallback: Callback,
            deferredPinnedIoFailure: Pair<String, IOException>?,
        ) {
            if (canceled.get()) {
                responseCallback.onFailure(this, IOException("Canceled"))
                return
            }
            val fallbackCall = systemDnsClient.newCall(originalRequest)
            if (!activateInnerCall(fallbackCall)) {
                responseCallback.onFailure(this, IOException("Canceled"))
                return
            }
            fallbackCall.enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        deferredPinnedIoFailure?.let { (failedHost, err) ->
                            recordIoFailure(failedHost, err)
                        }
                        responseCallback.onResponse(this@FallbackDelegatingCall, response)
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        responseCallback.onFailure(this@FallbackDelegatingCall, e)
                    }
                },
            )
        }

        private fun recordResponseFallback(host: String, reason: FallbackReason) {
            when (reason) {
                FallbackReason.HARD_NON_CF_OR_ROUTING_ERROR -> factory.recordHardHostBypass(host)
                FallbackReason.TRANSIENT_EDGE_ERROR -> factory.recordTransientHostFailure(host)
            }
        }

        private fun recordIoFailure(host: String, error: IOException) {
            if (error is SSLException) {
                factory.recordHardHostBypass(host)
            } else {
                if (pinnedGeneration.family == IpAddressFamily.IPV6) {
                    factory.reportPinnedBodyReadFailure(host, IpAddressFamily.IPV6)
                } else {
                    factory.recordTransientHostFailure(host)
                }
            }
        }

        private fun wrapResponseBodyForFailureMonitoring(
            response: Response,
            observedNonDirectProxy: Boolean,
        ): Response {
            if (observedNonDirectProxy) return response
            val delegateBody = response.body
            val finalHost = response.request.url.host
            val monitoredBody = BodyFailureMonitoringResponseBody(
                delegate = delegateBody,
                onBodyIoFailure = {
                    if (!canceled.get()) {
                        factory.reportPinnedBodyReadFailure(finalHost, pinnedGeneration.family)
                    }
                },
            )
            return response.newBuilder()
                .body(monitoredBody)
                .build()
        }
    }

    private class BodyFailureMonitoringResponseBody(
        private val delegate: ResponseBody,
        private val onBodyIoFailure: () -> Unit,
    ) : ResponseBody() {
        private val reportedFailure = AtomicBoolean(false)
        private val wrappedSource: BufferedSource by lazy {
            object : ForwardingSource(delegate.source()) {
                override fun read(sink: Buffer, byteCount: Long): Long {
                    return try {
                        super.read(sink, byteCount)
                    } catch (ioError: IOException) {
                        if (reportedFailure.compareAndSet(false, true)) {
                            onBodyIoFailure()
                        }
                        throw ioError
                    }
                }
            }.buffer()
        }

        override fun contentType() = delegate.contentType()

        override fun contentLength() = delegate.contentLength()

        override fun source(): BufferedSource = wrappedSource

        override fun close() {
            delegate.close()
        }
    }

    /**
     * Thread-safe OkHttp [Call] wrapping illustration requests (`pic.wenku8.com` / `pic.wenku8.cn`)
     * with automatic mirror fallback (`pic.777743.xyz`, `pic.wenku8.cn`) across both synchronous
     * [execute] (Artbook, offline downloads) and asynchronous [enqueue] (Coil 3 thumbnails & inline
     * reader illustrations).
     *
     * Authoritative 404 responses from the primary host stop immediately; transport/TLS resets
     * (such as GFW SNI/HTTP RST against `pic.wenku8.com`) or 5xx/CF edge errors advance seamlessly
     * to the next mirror, remembering the working mirror for 30 minutes to eliminate repeat stalls.
     */
    private class MirrorFallbackCall(
        private val factory: CloudflareAcceleratedCallFactory,
        private val originalRequest: Request,
        private val primaryHost: String,
        private val attemptRequests: List<Request>,
        private val directSystemDnsOnly: Boolean,
    ) : Call {

        private val executed = AtomicBoolean(false)
        private val canceled = AtomicBoolean(false)
        private val activeCallRef = AtomicReference<Call?>(null)
        private val extraEventListeners = CopyOnWriteArrayList<EventListener>()
        private val tags = ConcurrentHashMap<KClass<*>, Any>()
        private val attemptedEndpoints = CopyOnWriteArrayList<String>()

        override fun request(): Request = originalRequest

        override fun isExecuted(): Boolean = executed.get()

        override fun isCanceled(): Boolean = canceled.get()

        override fun cancel() {
            if (canceled.compareAndSet(false, true)) {
                activeCallRef.get()?.let { active ->
                    OkHttpProbeTransport.cancelCallSafely(active, factory.fallbackExecutor)
                }
            }
        }

        override fun clone(): Call = if (directSystemDnsOnly) {
            factory.newDirectSystemDnsCall(originalRequest)
        } else {
            factory.newCall(originalRequest)
        }

        override fun timeout(): Timeout = activeCallRef.get()?.timeout() ?: Timeout.NONE

        override fun addEventListener(eventListener: EventListener) {
            extraEventListeners.add(eventListener)
            activeCallRef.get()?.addEventListener(eventListener)
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> tag(type: KClass<T>): T? {
            if (type == MirrorAttemptTrace::class) {
                return MirrorAttemptTrace(attemptedEndpoints.toList()) as T
            }
            return tags[type] as T?
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T> tag(type: Class<out T>): T? {
            if (type == MirrorAttemptTrace::class.java) {
                return MirrorAttemptTrace(attemptedEndpoints.toList()) as T
            }
            return tags[type.kotlin] as T?
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> tag(type: KClass<T>, computeIfAbsent: () -> T): T {
            if (type == MirrorAttemptTrace::class) {
                return MirrorAttemptTrace(attemptedEndpoints.toList()) as T
            }
            return tags.computeIfAbsent(type) { computeIfAbsent() } as T
        }

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> tag(type: Class<T>, computeIfAbsent: () -> T): T {
            if (type == MirrorAttemptTrace::class.java) {
                return MirrorAttemptTrace(attemptedEndpoints.toList()) as T
            }
            return tags.computeIfAbsent(type.kotlin) { computeIfAbsent() } as T
        }

        private fun createAttemptCall(attemptRequest: Request): Call {
            return if (directSystemDnsOnly) {
                factory.systemDnsClient.newCall(attemptRequest)
            } else {
                factory.newSingleUrlCall(attemptRequest)
            }
        }

        private fun activateInnerCall(innerCall: Call): Boolean {
            for (listener in extraEventListeners) {
                innerCall.addEventListener(listener)
            }
            activeCallRef.set(innerCall)
            if (canceled.get()) {
                OkHttpProbeTransport.cancelCallSafely(innerCall, factory.fallbackExecutor)
                return false
            }
            return true
        }

        private fun shouldAdvanceToNextMirror(response: Response, attemptHost: String): Boolean {
            val code = response.code
            if (code in 500..599) return true
            if (code == 409 || code == 421) return true
            if (code == 403) {
                val contentType = response.header("Content-Type").orEmpty().lowercase(Locale.ROOT)
                if (!contentType.startsWith("image/")) return true
            }
            // A 404 on a non-authoritative fallback mirror may mean the upstream CDN migrated
            // away from that mirror, so keep trying remaining candidates (including the primary).
            if (code == 404 && attemptHost != primaryHost) return true
            return false
        }

        private fun recordSuccessMirrorState(attemptHost: String) {
            if (attemptHost != primaryHost) {
                WenkuImageUrlPolicy.mirrorForHostOrNull(attemptHost)?.let { mirror ->
                    factory.rememberPreferredMirror(primaryHost, mirror)
                }
            } else {
                factory.clearPreferredMirror(primaryHost)
            }
        }

        override fun execute(): Response {
            check(executed.compareAndSet(false, true)) { "Already Executed" }
            if (canceled.get()) throw IOException("Canceled")

            var lastIoError: IOException? = null
            for (index in attemptRequests.indices) {
                if (canceled.get()) throw IOException("Canceled")
                val attemptRequest = attemptRequests[index]
                val attemptHost = normalizeHost(attemptRequest.url.host)
                attemptedEndpoints.add("${attemptRequest.url.scheme}://$attemptHost")
                val innerCall = createAttemptCall(attemptRequest)
                if (!activateInnerCall(innerCall)) {
                    throw IOException("Canceled")
                }
                try {
                    val response = innerCall.execute()
                    if (response.isSuccessful) {
                        recordSuccessMirrorState(attemptHost)
                        return response
                    }
                    val hasNext = index < attemptRequests.lastIndex
                    if (hasNext && !canceled.get() && shouldAdvanceToNextMirror(response, attemptHost)) {
                        response.close()
                        continue
                    }
                    return response
                } catch (ioError: IOException) {
                    if (canceled.get()) throw ioError
                    lastIoError = ioError
                    if (index == attemptRequests.lastIndex) {
                        throw ioError
                    }
                }
            }
            throw lastIoError ?: IOException("All illustration mirrors failed")
        }

        override fun enqueue(responseCallback: Callback) {
            check(executed.compareAndSet(false, true)) { "Already Executed" }
            if (canceled.get()) {
                responseCallback.onFailure(this, IOException("Canceled"))
                return
            }
            enqueueAttempt(index = 0, responseCallback = responseCallback)
        }

        private fun enqueueAttempt(index: Int, responseCallback: Callback) {
            if (canceled.get()) {
                responseCallback.onFailure(this, IOException("Canceled"))
                return
            }
            val attemptRequest = attemptRequests[index]
            val attemptHost = normalizeHost(attemptRequest.url.host)
            attemptedEndpoints.add("${attemptRequest.url.scheme}://$attemptHost")
            val innerCall = createAttemptCall(attemptRequest)
            if (!activateInnerCall(innerCall)) {
                responseCallback.onFailure(this, IOException("Canceled"))
                return
            }
            innerCall.enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        if (canceled.get()) {
                            response.close()
                            responseCallback.onFailure(this@MirrorFallbackCall, IOException("Canceled"))
                            return
                        }
                        if (response.isSuccessful) {
                            recordSuccessMirrorState(attemptHost)
                            responseCallback.onResponse(this@MirrorFallbackCall, response)
                            return
                        }
                        val hasNext = index < attemptRequests.lastIndex
                        if (hasNext && shouldAdvanceToNextMirror(response, attemptHost)) {
                            response.close()
                            enqueueAttempt(index + 1, responseCallback)
                            return
                        }
                        responseCallback.onResponse(this@MirrorFallbackCall, response)
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        if (canceled.get()) {
                            responseCallback.onFailure(this@MirrorFallbackCall, e)
                            return
                        }
                        val hasNext = index < attemptRequests.lastIndex
                        if (hasNext) {
                            enqueueAttempt(index + 1, responseCallback)
                        } else {
                            responseCallback.onFailure(this@MirrorFallbackCall, e)
                        }
                    }
                },
            )
        }
    }
}
