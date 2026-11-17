package org.mewx.wenku8.data.remote.diagnostics

import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import org.mewx.wenku8.BuildConfig
import java.net.Proxy
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.UUID

enum class DiagnosticsStopReason(val wireName: String) {
    COMPLETED("completed"),
    USER_CANCELLED("user_cancelled"),
    BACKGROUNDED("backgrounded"),
    NETWORK_TRANSITION("network_transition"),
    RATE_LIMITED("rate_limited"),
    DEADLINE_EXCEEDED("deadline_exceeded"),
    OFFLINE("offline"),
}

data class DiagnosticsSummaryStats(
    val attemptedCount: Int,
    val succeededCount: Int,
    val failedCount: Int,
    val medianFullResponseMs: Long?,
    val medianHeadersStartMs: Long?,
    val minFullResponseMs: Long?,
    val maxFullResponseMs: Long?,
) {
    companion object {
        fun computeFromSamples(samples: List<ProbeSampleResult>): DiagnosticsSummaryStats {
            val attempted = samples.size
            val successes = samples.filter { it.succeeded }
            val succeeded = successes.size
            val failed = attempted - succeeded
            if (succeeded == 0) {
                return DiagnosticsSummaryStats(
                    attemptedCount = attempted,
                    succeededCount = 0,
                    failedCount = failed,
                    medianFullResponseMs = null,
                    medianHeadersStartMs = null,
                    minFullResponseMs = null,
                    maxFullResponseMs = null,
                )
            }

            val fullTimes = successes.map { it.fullResponseElapsedMs }.sorted()
            val headerTimes = successes.mapNotNull { it.responseHeadersStartMs }.sorted()

            return DiagnosticsSummaryStats(
                attemptedCount = attempted,
                succeededCount = succeeded,
                failedCount = failed,
                medianFullResponseMs = computeMedianSorted(fullTimes),
                medianHeadersStartMs = computeMedianSorted(headerTimes),
                minFullResponseMs = fullTimes.firstOrNull(),
                maxFullResponseMs = fullTimes.lastOrNull(),
            )
        }

        internal fun computeMedianSorted(sortedValues: List<Long>): Long? {
            if (sortedValues.isEmpty()) return null
            val mid = sortedValues.size / 2
            return if (sortedValues.size % 2 == 1) {
                sortedValues[mid]
            } else {
                (sortedValues[mid - 1] + sortedValues[mid]) / 2L
            }
        }
    }
}

/**
 * Immutable, privacy-redacted report implementing the `wenku8-network-report-v1` schema.
 *
 * Excludes all raw client/candidate IP literals (exporting only candidate IDs like `"cf-v4-01"`),
 * SSID/BSSID, OS network handles, device serials, cookies, tokens, and bookshelf content.
 */
data class DiagnosticsReport(
    val schema: String = REPORT_SCHEMA_V1,
    val runId: String = UUID.randomUUID().toString(),
    val utcStartedAtIso: String,
    val localTimezoneOffset: String,
    val stopReason: DiagnosticsStopReason,
    val profileName: String,
    val appVersionName: String = BuildConfig.VERSION_NAME,
    val buildChannel: String = BuildConfig.DISTRIBUTION_CHANNEL,
    val androidApiLevel: Int = Build.VERSION.SDK_INT,
    val baselineNetwork: NetworkSnapshot,
    val networkTransitionInterrupted: Boolean,
    val targetHostname: String,
    val endpointRole: String,
    val plannedSampleCount: Int,
    val manifestRevision: String? = null,
    val activeCandidateId: String? = null,
    val samples: List<ProbeSampleResult>,
    val summary: DiagnosticsSummaryStats = DiagnosticsSummaryStats.computeFromSamples(samples),
) {
    companion object {
        const val REPORT_SCHEMA_V1: String = "wenku8-network-report-v1"

        fun formatNullableMillisForUi(millis: Long?): String {
            return if (millis == null) "—" else "$millis ms"
        }

        fun currentUtcAndOffset(epochMillis: Long = System.currentTimeMillis()): Pair<String, String> {
            val instant = Instant.ofEpochMilli(epochMillis)
            val offset = OffsetDateTime.ofInstant(instant, ZoneId.systemDefault()).offset.id
            return instant.toString() to (if (offset == "Z") "+00:00" else offset)
        }
    }

    val observedProxyPresent: Boolean
        get() = samples.any { it.observedProxyType != Proxy.Type.DIRECT }

    val disclosedCountries: List<String>
        get() = samples.mapNotNull { it.serverPayload?.country }.distinct()

    val disclosedAsns: List<Long>
        get() = samples.mapNotNull { it.serverPayload?.asn }.distinct()

    val observedIngressColos: List<String>
        get() = samples.mapNotNull { it.serverPayload?.ingressColo }.distinct()

    /**
     * Produces the `wenku8-network-report-v1` JSON string using `JSONObject.NULL` for every
     * nullable property so both Android `org.json` and JVM `org.json:json` preserve explicit nulls.
     */
    fun toRedactedJsonString(optionalUserNote: String? = null): String {
        val root = JSONObject()
        root.put("schema", schema)
        root.put("runId", runId)
        root.put("utcStartedAt", utcStartedAtIso)
        root.put("localTimezoneOffset", localTimezoneOffset)
        root.put("stopReason", stopReason.wireName)
        root.put("profile", profileName)
        root.put("appVersion", appVersionName)
        root.put("buildChannel", buildChannel)
        root.put("androidApiLevel", androidApiLevel)
        root.put("okhttpVersion", "5.5.1")
        root.putNullable("userNote", optionalUserNote?.trim()?.takeIf { it.isNotEmpty() }?.take(200))

        val networkObj = JSONObject()
            .put("transport", baselineNetwork.wireTransportLabel())
            .put("metered", baselineNetwork.isMetered)
            .put("vpnActive", baselineNetwork.isVpnActive)
            .put("proxyObserved", observedProxyPresent)
            .put("transitionInterrupted", networkTransitionInterrupted)
        root.put("network", networkObj)

        val targetObj = JSONObject()
            .put("hostname", targetHostname)
            .put("endpointRole", endpointRole)
            .put("plannedAttempts", plannedSampleCount)
            .putNullable("manifestRevision", manifestRevision)
            .putNullable("activeCandidateId", activeCandidateId)
        root.put("target", targetObj)

        val summaryObj = JSONObject()
            .put("attempted", summary.attemptedCount)
            .put("succeeded", summary.succeededCount)
            .put("failed", summary.failedCount)
            .putNullable("medianFullResponseMs", summary.medianFullResponseMs)
            .putNullable("medianHeadersStartMs", summary.medianHeadersStartMs)
            .putNullable("minFullResponseMs", summary.minFullResponseMs)
            .putNullable("maxFullResponseMs", summary.maxFullResponseMs)
        root.put("summary", summaryObj)

        val samplesArray = JSONArray()
        for (sample in samples) {
            val sObj = JSONObject()
                .put("index", sample.sampleIndex)
                .put("armId", sample.armId)
                .putNullable("candidateId", sample.candidateId)
                .put("succeeded", sample.succeeded)
                .putNullable("httpStatus", sample.httpStatusCode)
                .putNullable("retryAfterSeconds", sample.retryAfterSeconds)
                .putNullable("errorCategory", sample.errorCategory?.wireName)
                .put("fullResponseElapsedMs", sample.fullResponseElapsedMs)
                .putNullable("responseHeadersStartMs", sample.responseHeadersStartMs)
                .putNullable("dnsDurationMs", sample.dnsDurationMs)
                .putNullable("tcpConnectDurationMs", sample.tcpConnectDurationMs)
                .putNullable("tlsHandshakeDurationMs", sample.tlsHandshakeDurationMs)
                .put("connectionReused", sample.connectionReused)
                .put("connectAttemptCount", sample.connectAttemptCount)
                .put("proxyType", sample.observedProxyType.name)
                .putNullable("localSocketFamily", sample.localSocketFamily?.wireLabel)
                .putNullable("negotiatedProtocol", sample.negotiatedProtocol)
                .put("responseBodyBytesRead", sample.responseBodyBytesRead)
                .put("bodyComplete", sample.bodyComplete)
                .put("events", JSONArray(sample.observedEvents))

            val serverPayload = sample.serverPayload
            if (serverPayload == null) {
                sObj.put("server", JSONObject.NULL)
            } else {
                val srvObj = JSONObject()
                    .putNullable("endpointRole", serverPayload.endpointRole)
                    .putNullable("ingressColo", serverPayload.ingressColo)
                    .putNullable("country", serverPayload.country)
                    .putNullable("asn", serverPayload.asn)
                    .putNullable("clientIpFamily", serverPayload.clientIpFamily?.wireLabel)
                    .putNullable("httpProtocol", serverPayload.httpProtocol)
                    .putNullable("transportRttMs", serverPayload.transportRttMs)
                    .putNullable("transportRttSource", serverPayload.transportRttSource)
                    .putNullable("handlerElapsedMs", serverPayload.handlerElapsedMs)
                sObj.put("server", srvObj)
            }
            samplesArray.put(sObj)
        }
        root.put("samples", samplesArray)
        return root.toString(2)
    }

    /**
     * Human-readable summary copied by `[ 📋 复制摘要 ]`.
     */
    fun toPlainTextSummary(): String {
        val colos = observedIngressColos.joinToString("/").ifEmpty { "—" }
        return buildString {
            appendLine("轻小说文库 · 网络连接诊断摘要 ($profileName)")
            appendLine("网络环境: ${baselineNetwork.wireTransportLabel()} | VPN=${if (baselineNetwork.isVpnActive) "是" else "否"} | 计费=${if (baselineNetwork.isMetered) "是" else "否"}")
            appendLine("成功 / 尝试: ${summary.succeededCount} / ${summary.attemptedCount} 次 (结束原因: ${stopReason.wireName})")
            appendLine("完整响应中位数: ${formatNullableMillisForUi(summary.medianFullResponseMs)} | 响应头中位数: ${formatNullableMillisForUi(summary.medianHeadersStartMs)}")
            appendLine("最快 / 最慢: ${formatNullableMillisForUi(summary.minFullResponseMs)} / ${formatNullableMillisForUi(summary.maxFullResponseMs)} | 入境节点: $colos")
            if (!activeCandidateId.isNullOrBlank()) {
                appendLine("当前优选节点 ID: $activeCandidateId (清单版本: ${manifestRevision ?: "—"})")
            }
        }.trimEnd()
    }

    private fun JSONObject.putNullable(key: String, value: Any?): JSONObject {
        put(key, value ?: JSONObject.NULL)
        return this
    }
}
