package org.mewx.wenku8.data.media

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.util.Locale

/**
 * URL normalization, HTTPS upgrade, illustration mirror fallback, and diagnostic sanitization
 * policy for Wenku8 static media hosts.
 *
 * ### Why this exists (Issue #218)
 * 1. Wenku8 chapter content (`<!--image-->...<!--image-->`) historically emits plaintext
 *    `http://pic.wenku8.com/pictures/...` (port 80) URLs even though every Wenku8 image host on
 *    Cloudflare natively supports `HTTPS / HTTP/2`. On mainland China cellular networks, cleartext
 *    HTTP port 80 requests carrying `Host: pic.wenku8.com` are immediately severed by DPI/GFW with
 *    a TCP RST (`SocketException: Connection reset`), even over Cloudflare preferred IPs.
 * 2. Even after upgrading to `https://pic.wenku8.com/pictures/...`, some regional ISPs reset TLS
 *    handshakes carrying SNI `pic.wenku8.com`. Because `pic.wenku8.com/pictures/{path}` is purely
 *    a Cloudflare 302 redirector to `https://pic.777743.xyz/{path}` (and `pic.wenku8.cn` is an
 *    independent Cloudflare zone that dynamically 301-redirects to the live picture CDN), falling
 *    back to those mirrors ONLY when `pic.wenku8.com` encounters a transport/TLS/edge error
 *    restores illustration loading without breaking future upstream CDN migrations.
 */
object WenkuImageUrlPolicy {

    /**
     * Wenku8 static media hosts verified to support HTTPS (TLS/HTTP2) natively on Cloudflare.
     * Cleartext `http://` requests on default port 80 are transparently upgraded to `https://`.
     */
    private val HTTPS_CAPABLE_WENKU_HOSTS: Set<String> = setOf(
        "pic.wenku8.com",
        "pic.wenku8.cn",
        "pic.777743.xyz",
        "img.wenku8.com",
        "avatar.wenku8.com",
    )

    private const val HOST_PIC_WENKU8_COM = "pic.wenku8.com"
    private const val HOST_PIC_WENKU8_CN = "pic.wenku8.cn"
    private const val HOST_PIC_777743_XYZ = "pic.777743.xyz"
    private const val PICTURES_PREFIX = "/pictures/"

    private val NUMERIC_DIRECT_PICTURE_PATH_REGEX = Regex("^/\\d+/.+")
    private val IPV4_LITERAL_REGEX = Regex("\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d+)?\\b")
    private val IPV6_LITERAL_REGEX = Regex("\\[?[0-9a-fA-F:]{2,}:[0-9a-fA-F:]*\\]?(?::\\d+)?")
    private val HEX_POINTER_REGEX = Regex("\\b0x[0-9a-fA-F]+\\b")
    private val SSL_STRUCT_REGEX = Regex("ssl=\\S+", RegexOption.IGNORE_CASE)
    private val URL_TOKEN_REGEX = Regex("https?://\\S+", RegexOption.IGNORE_CASE)

    enum class IllustrationMirror(val host: String) {
        PIC_777743(HOST_PIC_777743_XYZ),
        PIC_WENKU8_CN(HOST_PIC_WENKU8_CN),
        PIC_WENKU8_COM(HOST_PIC_WENKU8_COM),
    }

    /**
     * Upgrades cleartext `http://` (default port 80 ONLY) to `https://` (port 443) for known
     * HTTPS-capable Wenku8 image hosts. Custom non-80 ports (such as local test servers) and
     * non-Wenku8 domains are preserved unchanged.
     */
    fun upgradeToHttpsIfSupported(url: HttpUrl): HttpUrl {
        if (!url.scheme.equals("http", ignoreCase = true)) return url
        if (url.port != 80) return url
        val normalizedHost = url.host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalizedHost !in HTTPS_CAPABLE_WENKU_HOSTS) return url
        return url.newBuilder()
            .scheme("https")
            .port(443)
            .build()
    }

    fun upgradeToHttpsIfSupported(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true)) return trimmed
        val parsed = trimmed.toHttpUrlOrNull() ?: return trimmed
        val upgraded = upgradeToHttpsIfSupported(parsed)
        return if (upgraded === parsed) trimmed else upgraded.toString()
    }

    fun upgradeRequestUrlIfSupported(request: Request): Request {
        val upgraded = upgradeToHttpsIfSupported(request.url)
        return if (upgraded === request.url) {
            request
        } else {
            request.newBuilder().url(upgraded).build()
        }
    }

    /**
     * Returns the ordered list of candidate URLs to try for [url], starting with [preferredMirror]
     * when a fallback mirror is currently remembered as healthy for this host, or starting with
     * [url] itself otherwise.
     *
     * For non-illustration URLs (covers, avatars, API/notices, non-Wenku8 hosts), returns a
     * single-element list containing only [url].
     */
    fun buildOrderedIllustrationAttemptUrls(
        url: HttpUrl,
        preferredMirror: IllustrationMirror? = null,
    ): List<HttpUrl> {
        val normalizedHost = url.host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalizedHost != HOST_PIC_WENKU8_COM && normalizedHost != HOST_PIC_WENKU8_CN) {
            return listOf(url)
        }
        val canonicalRelativePath = extractCanonicalPictureRelativePath(url.encodedPath)
            ?: return listOf(url)

        val defaultMirrors = if (normalizedHost == HOST_PIC_WENKU8_COM) {
            listOf(
                IllustrationMirror.PIC_WENKU8_COM,
                IllustrationMirror.PIC_777743,
                IllustrationMirror.PIC_WENKU8_CN,
            )
        } else {
            listOf(
                IllustrationMirror.PIC_WENKU8_CN,
                IllustrationMirror.PIC_777743,
                IllustrationMirror.PIC_WENKU8_COM,
            )
        }

        val orderedMirrors = if (preferredMirror != null && preferredMirror in defaultMirrors) {
            val primaryMirror = defaultMirrors.first()
            if (preferredMirror == primaryMirror) {
                defaultMirrors
            } else {
                // Keep the previously-failed primary host LAST so healthy alternate mirrors
                // (e.g. `pic.wenku8.cn`) are tried before re-hitting a GFW-blocked `pic.wenku8.com`.
                listOf(preferredMirror) +
                    defaultMirrors.filter { it != preferredMirror && it != primaryMirror } +
                    primaryMirror
            }
        } else {
            defaultMirrors
        }

        return orderedMirrors.map { mirror ->
            buildMirrorUrl(
                template = url,
                mirror = mirror,
                canonicalRelativePath = canonicalRelativePath,
            )
        }
    }

    fun mirrorForHostOrNull(host: String): IllustrationMirror? {
        return when (host.trim().trimEnd('.').lowercase(Locale.ROOT)) {
            HOST_PIC_777743_XYZ -> IllustrationMirror.PIC_777743
            HOST_PIC_WENKU8_CN -> IllustrationMirror.PIC_WENKU8_CN
            HOST_PIC_WENKU8_COM -> IllustrationMirror.PIC_WENKU8_COM
            else -> null
        }
    }

    /**
     * Extracts a short, privacy-redacted ASCII reason phrase from an [Throwable] (e.g.
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

    private fun extractCanonicalPictureRelativePath(encodedPath: String): String? {
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

    private fun buildMirrorUrl(
        template: HttpUrl,
        mirror: IllustrationMirror,
        canonicalRelativePath: String,
    ): HttpUrl {
        val normalizedHost = template.host.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalizedHost == mirror.host) {
            return template
        }
        val targetPath = when (mirror) {
            IllustrationMirror.PIC_777743 -> "/$canonicalRelativePath"
            IllustrationMirror.PIC_WENKU8_CN,
            IllustrationMirror.PIC_WENKU8_COM,
            -> "$PICTURES_PREFIX$canonicalRelativePath"
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
object WenkuHttpsRedirectUpgradeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isRedirect) return response
        val rawLocation = response.header("Location")?.trim() ?: return response
        val resolved = request.url.resolve(rawLocation) ?: return response
        val upgraded = WenkuImageUrlPolicy.upgradeToHttpsIfSupported(resolved)
        if (upgraded === resolved) return response
        return response.newBuilder()
            .header("Location", upgraded.toString())
            .build()
    }
}
