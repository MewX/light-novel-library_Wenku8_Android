package org.mewx.wenku8.data.media

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.mewx.wenku8.data.remote.diagnostics.IpLiteralParser
import java.util.Locale

/**
 * URL normalization, HTTPS upgrade, interchangeable illustration mirror pool (Option A),
 * dynamic `_network_cfg.wenku8.app` (`pm=`) mirror parsing, and diagnostic sanitization policy
 * for Wenku8 static media hosts.
 *
 * ### Interchangeable Illustration Pool (Verified Live, Issue #218)
 * All three Wenku8 picture domains front the exact same illustration storage on Cloudflare:
 * 1. `https://pic.777743.xyz/{path}` — primary (HTTP/2 200 direct, zero redirect, no `wenku8.*` SNI).
 * 2. `https://pic.wenku8.com/{path}` — direct fallback (HTTP/2 200 direct without 302-bouncing back
 *    to `pic.777743.xyz` if `pic.777743.xyz` is ever blocked).
 * 3. `https://pic.wenku8.cn/pictures/{path}` — legacy last-resort fallback (demoted to bottom
 *    because China Mobile blocks `pic.wenku8.cn` and it requires two redirects).
 *
 * Dynamic mirrors pushed via `_network_cfg.wenku8.app`'s optional `pm=` TXT field (e.g.
 * `pm=pic.newcdn.xyz,pic.777743.xyz,pic.wenku8.com`) are strictly validated and prepended ahead
 * of the bundled defaults so future upstream CDN migrations take effect without an APK update.
 *
 * Cover URLs (`img.wenku8.com/image/...`) are NOT interchangeable with `pic.*` hosts and only
 * receive default-port HTTP -> HTTPS upgrade.
 */
object WenkuImageUrlPolicy {

    /**
     * A validated illustration mirror endpoint inside the interchangeable Wenku8 picture pool.
     *
     * @property host Lowercase public DNS hostname (e.g. `"pic.777743.xyz"`).
     * @property usesPicturesPrefix When `true`, requests use `/pictures/{relativePath}`; when
     * `false`, requests use `/{relativePath}` directly (zero redirect).
     */
    data class PictureMirrorEndpoint(
        val host: String,
        val usesPicturesPrefix: Boolean = false,
    ) {
        fun toWireSpec(): String = if (usesPicturesPrefix) "$host/pictures" else host
    }

    const val HOST_PIC_777743_XYZ = "pic.777743.xyz"
    const val HOST_PIC_WENKU8_COM = "pic.wenku8.com"
    const val HOST_PIC_WENKU8_CN = "pic.wenku8.cn"
    const val MAX_DYNAMIC_PICTURE_MIRRORS: Int = 6

    private const val PICTURES_PREFIX = "/pictures/"

    val MIRROR_PIC_777743 = PictureMirrorEndpoint(HOST_PIC_777743_XYZ, usesPicturesPrefix = false)
    val MIRROR_PIC_WENKU8_COM = PictureMirrorEndpoint(HOST_PIC_WENKU8_COM, usesPicturesPrefix = false)
    val MIRROR_PIC_WENKU8_CN = PictureMirrorEndpoint(HOST_PIC_WENKU8_CN, usesPicturesPrefix = true)

    /**
     * Option A default illustration mirror order:
     * `pic.777743.xyz/{path}` -> `pic.wenku8.com/{path}` -> `pic.wenku8.cn/pictures/{path}`.
     */
    val DEFAULT_PICTURE_MIRRORS: List<PictureMirrorEndpoint> = listOf(
        MIRROR_PIC_777743,
        MIRROR_PIC_WENKU8_COM,
        MIRROR_PIC_WENKU8_CN,
    )

    /**
     * Built-in Wenku8 static media hosts verified to support HTTPS (TLS/HTTP2) natively on Cloudflare.
     * Cleartext `http://` requests on default port 80 are transparently upgraded to `https://`.
     */
    private val BUILTIN_HTTPS_CAPABLE_WENKU_HOSTS: Set<String> = setOf(
        HOST_PIC_WENKU8_COM,
        HOST_PIC_WENKU8_CN,
        HOST_PIC_777743_XYZ,
        "img.wenku8.com",
        "avatar.wenku8.com",
    )

    private val BUILTIN_ILLUSTRATION_POOL_HOSTS: Set<String> = setOf(
        HOST_PIC_777743_XYZ,
        HOST_PIC_WENKU8_COM,
        HOST_PIC_WENKU8_CN,
    )

    private val VALID_PUBLIC_DNS_HOST_REGEX =
        Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+$")
    private val NUMERIC_DIRECT_PICTURE_PATH_REGEX = Regex("^/\\d+/.+")
    private val IPV4_LITERAL_REGEX = Regex("\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?\\b")
    private val IPV6_LITERAL_REGEX = Regex("\\[?[0-9a-fA-F:]{2,}:[0-9a-fA-F:]*\\]?(?::\\d+)?")
    private val HEX_POINTER_REGEX = Regex("\\b0x[0-9a-fA-F]+\\b")
    private val SSL_STRUCT_REGEX = Regex("ssl=\\S+", RegexOption.IGNORE_CASE)
    private val URL_TOKEN_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    /**
     * Strictly parses a single dynamic mirror spec from `_network_cfg.wenku8.app` (`pm=` field) or
     * manifest JSON (`pictureMirrors`).
     *
     * Accepted formats:
     * - `pic.777743.xyz` (direct `/{path}`)
     * - `pic.wenku8.cn/pictures` (prefixed `/pictures/{path}`)
     *
     * Rejects numeric IP literals, `localhost`/`.local`/`.internal`/`.home.arpa`, schemes, ports,
     * credentials, query strings, fragments, and unexpected paths.
     */
    fun parseMirrorEndpointSpecOrNull(rawSpec: String): PictureMirrorEndpoint? {
        val trimmed = rawSpec.trim()
        if (trimmed.isEmpty() || trimmed.length > 140) return null
        if (trimmed.contains("://") ||
            trimmed.contains(':') ||
            trimmed.contains('@') ||
            trimmed.contains('?') ||
            trimmed.contains('#') ||
            trimmed.any { it.isWhitespace() }
        ) {
            return null
        }

        val slashIdx = trimmed.indexOf('/')
        val rawHost: String
        val usesPicturesPrefix: Boolean
        if (slashIdx < 0) {
            rawHost = trimmed
            usesPicturesPrefix = false
        } else {
            rawHost = trimmed.substring(0, slashIdx)
            val suffix = trimmed.substring(slashIdx).trimEnd('/')
            if (suffix.equals("/pictures", ignoreCase = true)) {
                usesPicturesPrefix = true
            } else if (suffix.isEmpty()) {
                usesPicturesPrefix = false
            } else {
                return null
            }
        }

        val normalizedHost = rawHost.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalizedHost.isEmpty() || normalizedHost.length > 128) return null
        if (IpLiteralParser.parseNumericIpBytes(normalizedHost, requirePublicUnicast = false) != null) {
            return null
        }
        if (normalizedHost == "localhost" ||
            normalizedHost.endsWith(".localhost") ||
            normalizedHost.endsWith(".local") ||
            normalizedHost.endsWith(".internal") ||
            normalizedHost.endsWith(".home.arpa")
        ) {
            return null
        }
        if (!VALID_PUBLIC_DNS_HOST_REGEX.matches(normalizedHost)) return null
        return PictureMirrorEndpoint(
            host = normalizedHost,
            usesPicturesPrefix = usesPicturesPrefix,
        )
    }

    /**
     * Parses a comma-separated `pm=` list from DNS TXT or JSON strings, discarding any invalid
     * entries, deduplicating by endpoint, and capping at [MAX_DYNAMIC_PICTURE_MIRRORS].
     */
    fun parseDynamicMirrorSpecs(rawSpecs: Iterable<String>): List<PictureMirrorEndpoint> {
        val result = ArrayList<PictureMirrorEndpoint>(MAX_DYNAMIC_PICTURE_MIRRORS)
        val seen = HashSet<PictureMirrorEndpoint>()
        for (raw in rawSpecs) {
            if (result.size >= MAX_DYNAMIC_PICTURE_MIRRORS) break
            val parsed = parseMirrorEndpointSpecOrNull(raw) ?: continue
            if (seen.add(parsed)) {
                result.add(parsed)
            }
        }
        return result
    }

    /**
     * Builds the effective ordered mirror pool by prepending any dynamic `pm=` mirrors ahead of
     * [DEFAULT_PICTURE_MIRRORS] while deduplicating by `(host, usesPicturesPrefix)`.
     */
    fun effectiveMirrorPool(
        dynamicMirrors: List<PictureMirrorEndpoint> = emptyList(),
    ): List<PictureMirrorEndpoint> {
        if (dynamicMirrors.isEmpty()) return DEFAULT_PICTURE_MIRRORS
        val merged = ArrayList<PictureMirrorEndpoint>(dynamicMirrors.size + DEFAULT_PICTURE_MIRRORS.size)
        val seenHosts = HashSet<String>()
        for (mirror in dynamicMirrors) {
            if (seenHosts.add(mirror.host)) {
                merged.add(mirror)
            }
        }
        for (fallback in DEFAULT_PICTURE_MIRRORS) {
            if (seenHosts.add(fallback.host)) {
                merged.add(fallback)
            }
        }
        return merged
    }

    fun isIllustrationPoolHost(
        host: String,
        dynamicMirrors: List<PictureMirrorEndpoint> = emptyList(),
    ): Boolean {
        val normalized = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return false
        if (normalized in BUILTIN_ILLUSTRATION_POOL_HOSTS) return true
        return dynamicMirrors.any { it.host == normalized }
    }

    /**
     * Upgrades cleartext `http://` (default port 80 ONLY) to `https://` (port 443) for known
     * HTTPS-capable Wenku8 image hosts and any active dynamic `pm=` mirrors. Custom non-80 ports
     * (such as local test servers) and unrelated third-party domains are preserved unchanged.
     */
    fun upgradeToHttpsIfSupported(
        url: HttpUrl,
        dynamicMirrors: List<PictureMirrorEndpoint> = emptyList(),
    ): HttpUrl {
        if (!url.scheme.equals("http", ignoreCase = true)) return url
        if (url.port != 80) return url
        val normalizedHost = url.host.trim().trimEnd('.').lowercase(Locale.ROOT)
        val isSupported = normalizedHost in BUILTIN_HTTPS_CAPABLE_WENKU_HOSTS ||
            dynamicMirrors.any { it.host == normalizedHost }
        if (!isSupported) return url
        return url.newBuilder()
            .scheme("https")
            .port(443)
            .build()
    }

    fun upgradeToHttpsIfSupported(
        rawUrl: String,
        dynamicMirrors: List<PictureMirrorEndpoint> = emptyList(),
    ): String {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true)) return trimmed
        val parsed = trimmed.toHttpUrlOrNull() ?: return trimmed
        val upgraded = upgradeToHttpsIfSupported(parsed, dynamicMirrors)
        return if (upgraded === parsed) trimmed else upgraded.toString()
    }

    fun upgradeRequestUrlIfSupported(
        request: Request,
        dynamicMirrors: List<PictureMirrorEndpoint> = emptyList(),
    ): Request {
        val upgraded = upgradeToHttpsIfSupported(request.url, dynamicMirrors)
        return if (upgraded === request.url) {
            request
        } else {
            request.newBuilder().url(upgraded).build()
        }
    }

    /**
     * Returns the ordered list of interchangeable mirror URLs to try for an illustration URL:
     * - Default (Option A): `[dynamic pm=... , pic.777743.xyz/{path}, pic.wenku8.com/{path}, pic.wenku8.cn/pictures/{path}]`
     *   (even when the chapter text specifies legacy China-Mobile-blocked `pic.wenku8.cn` or
     *   `pic.wenku8.com`!).
     * - When [preferredMirror] is currently remembered as healthy for the current network, it is
     *   placed FIRST, followed by the remaining mirrors in priority order.
     * - For non-illustration URLs (covers on `img.wenku8.com`, avatars, API/notices, non-Wenku8
     *   hosts), returns a single-element list containing only [url].
     */
    fun buildOrderedIllustrationAttemptUrls(
        url: HttpUrl,
        preferredMirror: PictureMirrorEndpoint? = null,
        dynamicMirrors: List<PictureMirrorEndpoint> = emptyList(),
    ): List<HttpUrl> {
        val normalizedHost = url.host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (!isIllustrationPoolHost(normalizedHost, dynamicMirrors)) {
            return listOf(url)
        }
        val canonicalRelativePath = extractCanonicalPictureRelativePath(url.encodedPath)
            ?: return listOf(url)

        val pool = effectiveMirrorPool(dynamicMirrors)
        val orderedMirrors = if (preferredMirror != null && preferredMirror in pool) {
            val defaultPrimary = pool.first()
            if (preferredMirror == defaultPrimary) {
                pool
            } else {
                // Keep the previously-failed default primary LAST among active mirrors? Wait:
                // place remembered healthy mirror FIRST, followed by other non-legacy mirrors,
                // then the previously-failed primary, keeping legacy `pic.wenku8.cn` at the bottom.
                val rest = pool.filter { it != preferredMirror }
                val legacyTail = rest.filter { it.host == HOST_PIC_WENKU8_CN }
                val nonLegacyRest = rest.filter { it.host != HOST_PIC_WENKU8_CN }
                val demotedPrimary = nonLegacyRest.filter { it == defaultPrimary }
                val alternateHealthy = nonLegacyRest.filter { it != defaultPrimary }
                listOf(preferredMirror) + alternateHealthy + demotedPrimary + legacyTail
            }
        } else {
            pool
        }

        return orderedMirrors.map { mirror ->
            buildMirrorUrl(
                template = url,
                mirror = mirror,
                canonicalRelativePath = canonicalRelativePath,
            )
        }
    }

    /**
     * Resolves the [PictureMirrorEndpoint] corresponding to [host] inside the effective mirror
     * pool, or `null` if [host] is outside the illustration mirror pool.
     */
    fun mirrorForHostOrNull(
        host: String,
        dynamicMirrors: List<PictureMirrorEndpoint> = emptyList(),
    ): PictureMirrorEndpoint? {
        val normalized = host.trim().trimEnd('.').lowercase(Locale.ROOT)
        return effectiveMirrorPool(dynamicMirrors).firstOrNull { it.host == normalized }
    }

    /**
     * Extracts the canonical relative picture path (e.g. `"3/3770/141001/1.jpg"`) from either
     * `/pictures/3/3770/141001/1.jpg` or direct `/3/3770/141001/1.jpg`.
     */
    internal fun extractCanonicalPictureRelativePath(encodedPath: String): String? {
        val lower = encodedPath.lowercase(Locale.ROOT)
        if (lower.startsWith(PICTURES_PREFIX)) {
            val rest = encodedPath.substring(PICTURES_PREFIX.length).trimStart('/')
            return rest.takeIf { it.isNotEmpty() }
        }
        if (NUMERIC_DIRECT_PICTURE_PATH_REGEX.matches(encodedPath)) {
            val rest = encodedPath.removePrefix("/").trimStart('/')
            return rest.takeIf { it.isNotEmpty() }
        }
        return null
    }

    /**
     * Extracts a short, privacy-redacted ASCII reason phrase from a [Throwable] (e.g.
     * `"Connection reset"`, `"Permission denied"`, `"unexpected end of stream"`) for the
     * collapsed Artbook diagnostic drawer. Strips IP literals, memory pointers, and URLs.
     */
    fun sanitizeIoExceptionReason(error: Throwable): String? {
        val candidates = listOfNotNull(
            error.message,
            error.cause?.message,
        )
        for (raw in candidates) {
            val cleaned = raw
                .replace(URL_TOKEN_REGEX, "")
                .replace(SSL_STRUCT_REGEX, "")
                .replace(HEX_POINTER_REGEX, "")
                .replace(IPV4_LITERAL_REGEX, "")
                .replace(IPV6_LITERAL_REGEX, "")
                .replace(Regex("I/O error during system call,\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("Read error:\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("Write error:\\s*", RegexOption.IGNORE_CASE), "")
                .replace(Regex("\\s+"), " ")
                .trim(' ', ':', ',', ';', '-', '(', ')')
            if (cleaned.isNotEmpty()) {
                return if (cleaned.length > 44) cleaned.take(44).trimEnd() + "..." else cleaned
            }
        }
        return null
    }

    private fun buildMirrorUrl(
        template: HttpUrl,
        mirror: PictureMirrorEndpoint,
        canonicalRelativePath: String,
    ): HttpUrl {
        val targetPath = if (mirror.usesPicturesPrefix) {
            "$PICTURES_PREFIX$canonicalRelativePath"
        } else {
            "/$canonicalRelativePath"
        }
        val normalizedHost = template.host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalizedHost == mirror.host && template.encodedPath == targetPath) {
            return template
        }
        return template.newBuilder()
            .host(mirror.host)
            .encodedPath(targetPath)
            .build()
    }
}

/**
 * OkHttp network interceptor that upgrades cleartext `http://` redirect `Location` headers
 * (e.g. `pic.wenku8.cn` returning `301 Location: http://pic.777743.xyz/pictures/...`) to `https://`
 * before OkHttp's `RetryAndFollowUpInterceptor` opens a cleartext port-80 connection.
 */
class WenkuHttpsRedirectUpgradeInterceptor(
    private val dynamicMirrorsProvider: () -> List<WenkuImageUrlPolicy.PictureMirrorEndpoint> = { emptyList() },
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isRedirect) return response
        val rawLocation = response.header("Location")?.trim() ?: return response
        val resolved = request.url.resolve(rawLocation) ?: return response
        val upgraded = WenkuImageUrlPolicy.upgradeToHttpsIfSupported(resolved, dynamicMirrorsProvider())
        if (upgraded === resolved) return response
        return response.newBuilder()
            .header("Location", upgraded.toString())
            .build()
    }

    companion object {
        val DEFAULT: WenkuHttpsRedirectUpgradeInterceptor = WenkuHttpsRedirectUpgradeInterceptor()
    }
}
