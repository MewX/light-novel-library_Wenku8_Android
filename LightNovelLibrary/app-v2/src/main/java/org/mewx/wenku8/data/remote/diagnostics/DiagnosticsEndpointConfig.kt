package org.mewx.wenku8.data.remote.diagnostics

import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import java.net.URI
import java.util.Locale

enum class EndpointUnavailableReason {
    API_STUB_UNCONFIGURED,
    MALFORMED_URI,
    NON_HTTPS_SCHEME,
    MISSING_HOST,
    DISALLOWED_URI_COMPONENTS,
}

data class ValidatedRelayEndpoint(
    val canonicalOrigin: String,
    val relayHost: String,
    val port: Int,
    val apiBaseUrl: String,
    val latencyProbeUrl: String,
    val bootstrapManifestMirrorUrl: String,
    val endpointRole: String = DEFAULT_ENDPOINT_ROLE,
) {
    companion object {
        const val DEFAULT_ENDPOINT_ROLE: String = "placed-relay"
        const val LATENCY_PROBE_PATH: String = "/__diagnostics/latency"
        const val BOOTSTRAP_MIRROR_PATH: String = "/__diagnostics/ip-candidates"
    }
}

sealed interface DiagnosticsEndpointResolution {
    data class Available(val endpoint: ValidatedRelayEndpoint) : DiagnosticsEndpointResolution
    data class Unavailable(val reason: EndpointUnavailableReason) : DiagnosticsEndpointResolution
}

/**
 * Resolves and validates the Wenku8 relay endpoint without ever falling back to a hardcoded
 * production hostname when building against `:api-stub` (`BASE_URL == "Unknown"`).
 */
object DiagnosticsEndpointConfig {

    /**
     * Resolves the runtime `:api` endpoint safely (catching JNI `LinkageError` on host JVMs and
     * `UnsupportedOperationException` on stubs).
     */
    fun resolveFromCompiledApi(): DiagnosticsEndpointResolution {
        val rawBaseUrl = try {
            Wenku8API.BASE_URL
        } catch (_: LinkageError) {
            null
        } catch (_: UnsupportedOperationException) {
            null
        }
        return parseAndValidate(rawBaseUrl = rawBaseUrl, allowCleartextForLocalTests = false)
    }

    /**
     * Validates an explicit [rawBaseUrl] (or test-injected `MockWebServer` URL when
     * [allowCleartextForLocalTests] is `true`).
     *
     * Note per spec: `URI("Unknown")` parses as a valid *relative* URI! Always check `isAbsolute`,
     * scheme, host, and absence of `userInfo`, `query`, and `fragment`.
     */
    fun parseAndValidate(
        rawBaseUrl: String?,
        allowCleartextForLocalTests: Boolean = false,
        endpointRole: String = ValidatedRelayEndpoint.DEFAULT_ENDPOINT_ROLE,
    ): DiagnosticsEndpointResolution {
        val trimmed = rawBaseUrl?.trim().orEmpty()
        if (trimmed.isEmpty() ||
            trimmed.equals(WenkuTransport.UNKNOWN_ENDPOINT_SENTINEL, ignoreCase = true)
        ) {
            return DiagnosticsEndpointResolution.Unavailable(
                EndpointUnavailableReason.API_STUB_UNCONFIGURED,
            )
        }

        val parsedUri = try {
            URI(trimmed)
        } catch (_: Exception) {
            return DiagnosticsEndpointResolution.Unavailable(EndpointUnavailableReason.MALFORMED_URI)
        }

        if (!parsedUri.isAbsolute) {
            return DiagnosticsEndpointResolution.Unavailable(EndpointUnavailableReason.MALFORMED_URI)
        }

        val schemeLower = parsedUri.scheme?.lowercase(Locale.ROOT).orEmpty()
        val isAllowedScheme = schemeLower == "https" ||
            (allowCleartextForLocalTests && schemeLower == "http")
        if (!isAllowedScheme) {
            return DiagnosticsEndpointResolution.Unavailable(
                EndpointUnavailableReason.NON_HTTPS_SCHEME,
            )
        }

        val rawHost = parsedUri.host?.trim()?.trimEnd('.').orEmpty()
        if (rawHost.isEmpty()) {
            return DiagnosticsEndpointResolution.Unavailable(EndpointUnavailableReason.MISSING_HOST)
        }

        if (parsedUri.rawUserInfo != null ||
            parsedUri.rawQuery != null ||
            parsedUri.rawFragment != null
        ) {
            return DiagnosticsEndpointResolution.Unavailable(
                EndpointUnavailableReason.DISALLOWED_URI_COMPONENTS,
            )
        }

        val normalizedHost = rawHost.lowercase(Locale.ROOT)
        val effectivePort = if (parsedUri.port != -1) {
            parsedUri.port
        } else if (schemeLower == "http") {
            80
        } else {
            443
        }

        val defaultPortForScheme = if (schemeLower == "http") 80 else 443
        val authorityString = if (effectivePort == defaultPortForScheme) {
            normalizedHost
        } else {
            "$normalizedHost:$effectivePort"
        }
        val originUri = URI("$schemeLower://$authorityString/")

        val normalizedPath = parsedUri.rawPath.orEmpty().let { path ->
            when {
                path.isEmpty() -> "/"
                path.endsWith('/') -> path
                else -> path
            }
        }
        val apiBaseUrl = originUri.resolve(normalizedPath).toString()
        val latencyProbeUrl = originUri
            .resolve(ValidatedRelayEndpoint.LATENCY_PROBE_PATH)
            .toString()
        val bootstrapMirrorUrl = originUri
            .resolve(ValidatedRelayEndpoint.BOOTSTRAP_MIRROR_PATH)
            .toString()

        return DiagnosticsEndpointResolution.Available(
            ValidatedRelayEndpoint(
                canonicalOrigin = "$schemeLower://$authorityString",
                relayHost = normalizedHost,
                port = effectivePort,
                apiBaseUrl = apiBaseUrl,
                latencyProbeUrl = latencyProbeUrl,
                bootstrapManifestMirrorUrl = bootstrapMirrorUrl,
                endpointRole = endpointRole,
            ),
        )
    }
}
