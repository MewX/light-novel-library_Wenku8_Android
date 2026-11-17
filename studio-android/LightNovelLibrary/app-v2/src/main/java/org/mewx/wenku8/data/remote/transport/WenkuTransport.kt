package org.mewx.wenku8.data.remote.transport

import android.content.ContentValues
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.network.LightNetwork

/**
 * Pluggable HTTP form-POST transport boundary for Wenku8 `:api` calls.
 *
 * - In Phase 1, [LegacyLightNetworkTransport] delegates to `:api`'s [LightNetwork] on [Dispatchers.IO].
 * - In Phase 2 (`P2.1`/`P2.2`), `RelayHttpTransport` replaces this default implementation behind
 *   the exact same interface so OkHttp connection pooling, `EventListener` telemetry, and
 *   `"自动优化连接"` candidate-IP routing transparently power all repositories without modifying `:api`.
 */
interface WenkuTransport {
    /**
     * True when the compiled `:api` module provides a real relay/origin endpoint rather than
     * `:api-stub`'s `"Unknown"` placeholder (and native JNI is available in the current runtime).
     */
    val isEndpointConfigured: Boolean

    /**
     * Executes a form-encoded POST against [endpointUrl] with [formParams], returning the raw
     * UTF-8/binary payload bytes on HTTP 200 or `null` when unreachable / unconfigured.
     */
    suspend fun postForm(endpointUrl: String, formParams: ContentValues): ByteArray?

    /**
     * Overload allowing audited read-only operations to opt into at-most-once post-send fallback
     * replay when a pinned candidate route fails mid-flight. Unknown or mutating calls default to
     * `isIdempotentReadOnly = false` (never replayed once `requestHeadersStart` has fired).
     */
    suspend fun postForm(
        endpointUrl: String,
        formParams: ContentValues,
        isIdempotentReadOnly: Boolean,
    ): ByteArray? = postForm(endpointUrl, formParams)

    /**
     * Fetches raw HTTP 200 payload bytes via GET (used for server notices, version checks, and
     * account avatars) without attaching session cookies to third-party or CDN hosts.
     */
    suspend fun getBytes(url: String): ByteArray? = null

    /**
     * Clears any active `PHPSESSID` session cookie held by the transport so explicit logout never
     * leaks a prior session token onto subsequent requests.
     */
    fun clearSessionCookie() {}
}

/**
 * Default Phase 1 transport backed by `:api`'s [LightNetwork.LightHttpPostConnection].
 *
 * Guards both `:api-stub`'s `"Unknown"` sentinel and host-JVM `UnsatisfiedLinkError` /
 * `NoClassDefFoundError` (when running Robolectric unit tests on Linux where Android Bionic
 * `libcpp.so` cannot be loaded) so unit and UI tests reach offline states cleanly without crashing.
 */
class LegacyLightNetworkTransport(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : WenkuTransport {

    override val isEndpointConfigured: Boolean
        get() = try {
            val baseUrl = Wenku8API.BASE_URL
            !baseUrl.isNullOrBlank() && baseUrl != UNKNOWN_ENDPOINT_SENTINEL
        } catch (_: LinkageError) {
            false
        }

    override suspend fun postForm(endpointUrl: String, formParams: ContentValues): ByteArray? {
        if (endpointUrl.isBlank() || endpointUrl == UNKNOWN_ENDPOINT_SENTINEL) {
            return null
        }
        return withContext(ioDispatcher) {
            try {
                LightNetwork.LightHttpPostConnection(endpointUrl, formParams)
            } catch (_: LinkageError) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    override suspend fun getBytes(url: String): ByteArray? {
        if (url.isBlank() || url.equals(UNKNOWN_ENDPOINT_SENTINEL, ignoreCase = true)) {
            return null
        }
        return withContext(ioDispatcher) {
            try {
                LightNetwork.LightHttpDownload(url)
            } catch (_: LinkageError) {
                null
            } catch (_: Exception) {
                null
            }
        }
    }

    companion object {
        const val UNKNOWN_ENDPOINT_SENTINEL: String = "Unknown"
    }
}
