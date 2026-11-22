package org.mewx.wenku8.data.remote.transport

/**
 * Typed result of a sealed `WK8-RNE-1` POST request against `/v2/api`.
 */
sealed interface RelayPostResult {
    /** HTTP 200 with decoded UTF-8/binary response body. */
    data class Success(val bodyBytes: ByteArray) : RelayPostResult {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Success) return false
            return bodyBytes.contentEquals(other.bodyBytes)
        }

        override fun hashCode(): Int = bodyBytes.contentHashCode()
    }

    /** HTTP 426 Upgrade Required (`SCHEME_RETIRED` or `VERSION_TOO_OLD`). Never retried. */
    data class UpgradeRequired(
        val minVersionCode: Int? = null,
        val minVersionName: String? = null,
    ) : RelayPostResult

    /** HTTP 403 Forbidden (or non-clock-skew 401) — request rejected by server policy/auth. */
    data object AuthRejected : RelayPostResult

    /** HTTP 429 Too Many Requests. Never retried or rotated. */
    data object RateLimited : RelayPostResult

    /** Transport I/O failure, timeout, non-200 server error, or unavailable native sealer. */
    data class NetworkFailure(val debugDetail: String? = null) : RelayPostResult
}

/**
 * Pluggable HTTP transport boundary for Wenku8 `:app-v2` calls.
 *
 * All relay API actions route uniformly through [postSealedAction] (`POST /v2/api` sealed via
 * `:api`'s `WK8-RNE-1` native envelope), while [getBytes] handles direct unauthenticated GETs
 * (covers, illustrations, server notices, and direct-channel version metadata).
 */
interface WenkuTransport {
    /**
     * True when the compiled `:api` module provides a real relay endpoint rather than
     * `:api-stub`'s `"Unknown"` placeholder.
     */
    val isEndpointConfigured: Boolean

    /**
     * Seals [rawActionQuery] (binding the active `PHPSESSID` cookie header if present) and POSTs
     * the binary envelope to `/v2/api`.
     *
     * Audited read-only calls may pass `isIdempotentReadOnly = true` to permit at-most-once
     * post-send fallback replay when a pinned candidate IP fails mid-flight. Every retry mints a
     * fresh sealed envelope with a new nonce and timestamp.
     */
    suspend fun postSealedAction(
        rawActionQuery: String,
        isIdempotentReadOnly: Boolean = false,
    ): RelayPostResult

    /**
     * Fetches raw HTTP 200 payload bytes via GET (used for server notices, version checks, and
     * account avatars / cover images) without attaching session cookies to third-party or CDN hosts.
     */
    suspend fun getBytes(url: String): ByteArray? = null

    /**
     * Clears any active `PHPSESSID` session cookie held by the transport so explicit logout never
     * leaks a prior session token onto subsequent requests.
     */
    fun clearSessionCookie() {}

    companion object {
        const val UNKNOWN_ENDPOINT_SENTINEL: String = "Unknown"
    }
}
