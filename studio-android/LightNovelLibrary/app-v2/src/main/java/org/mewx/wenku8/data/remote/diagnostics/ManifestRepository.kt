package org.mewx.wenku8.data.remote.diagnostics

import android.content.Context
import android.os.SystemClock
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.InetAddress
import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.util.Locale
import java.util.Random
import java.util.concurrent.TimeUnit

data class DohTxtResolverEndpoint(
    val id: String,
    val resolverIpLiteral: String?,
    val resolveUrlTemplate: String,
)

data class CandidateIpEntry(
    val id: String,
    val ipLiteral: String,
    val rawIpBytes: ByteArray,
    val family: IpAddressFamily,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CandidateIpEntry) return false
        return id == other.id && ipLiteral == other.ipLiteral && rawIpBytes.contentEquals(other.rawIpBytes)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + ipLiteral.hashCode()
        result = 31 * result + rawIpBytes.contentHashCode()
        return result
    }
}

data class ValidatedCandidateManifest(
    val schema: String,
    val revision: String,
    val hostname: String,
    val enabled: Boolean,
    val minAppVersionCode: Int,
    val rolloutPermille: Int,
    val fallbackEndpointIds: List<String>,
    val issuedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val candidates: List<CandidateIpEntry>,
    val acceptedAtMonotonicMs: Long,
    val authorizedLifetimeMs: Long,
    val candidateSpecs: List<CandidateAddressSpec> = emptyList(),
) {
    fun sampleCandidates(
        random: Random = defaultManifestRandom,
        requirePublicUnicast: Boolean = true,
    ): List<CandidateIpEntry> {
        if (candidateSpecs.isEmpty()) {
            return candidates
        }
        return candidateSpecs.mapNotNull { spec ->
            spec.sampleCandidateEntry(random = random, requirePublicUnicast = requirePublicUnicast)
        }
    }

    fun isAuthorizedForOrdinaryTraffic(
        nowWallEpochMs: Long,
        nowMonotonicMs: Long,
        appVersionCode: Int,
        installRolloutBucket: Int,
    ): Boolean {
        if (!enabled) return false
        if (appVersionCode < minAppVersionCode) return false
        if (installRolloutBucket !in 0 until rolloutPermille) return false
        if (nowWallEpochMs >= expiresAtEpochMs) return false
        val elapsedMonotonic = (nowMonotonicMs - acceptedAtMonotonicMs).coerceAtLeast(0L)
        return elapsedMonotonic < authorizedLifetimeMs
    }

    fun remainingAuthorizationMillis(nowWallEpochMs: Long, nowMonotonicMs: Long): Long {
        val wallRemaining = (expiresAtEpochMs - nowWallEpochMs).coerceAtLeast(0L)
        val monoElapsed = (nowMonotonicMs - acceptedAtMonotonicMs).coerceAtLeast(0L)
        val monoRemaining = (authorizedLifetimeMs - monoElapsed).coerceAtLeast(0L)
        return minOf(wallRemaining, monoRemaining)
    }

    companion object {
        private val defaultManifestRandom = SecureRandom()
    }
}

/**
 * Local routing budget & manifest cache persisted in `network_routing_local.preferences_pb`
 * (strictly separated from `settings.preferences_pb` and excluded from `.wk8backup.zip`).
 *
 * Enforces `network-connectivity-and-routing-design.md` §Automatic controller:
 * - At most **120** automatic diagnostic/bootstrap calls per rolling 24 hours.
 * - Of which at most **60** occur on metered/unknown networks.
 * - Counters survive network transitions and process restarts.
 */
class RoutingLocalStore(
    private val dataStore: DataStore<Preferences>,
) {
    companion object {
        const val DATASTORE_NAME: String = "network_routing_local"
        const val ROLLING_WINDOW_MILLIS: Long = 24L * 60L * 60L * 1_000L
        const val MAX_AUTOMATIC_PROBES_PER_24H: Int = 120
        const val MAX_METERED_PROBES_PER_24H: Int = 60

        private val KEY_INSTALL_ROLLOUT_BUCKET = intPreferencesKey("install_rollout_bucket")
        private val KEY_PROBE_LOG_CSV = stringPreferencesKey("auto_probe_timestamps_csv")
        private val KEY_CACHED_MANIFEST_JSON = stringPreferencesKey("cached_manifest_json")
        private val KEY_CACHED_MANIFEST_ETAG = stringPreferencesKey("cached_manifest_etag")
        private val KEY_NEWEST_ISSUED_AT_MS = longPreferencesKey("newest_accepted_issued_at_ms")
        private val KEY_REVOKED_ISSUED_AT_MS = longPreferencesKey("revoked_at_issued_at_ms")

        private val secureRandom = SecureRandom()

        fun create(context: Context, scope: CoroutineScope): RoutingLocalStore {
            val ds = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { context.applicationContext.preferencesDataStoreFile(DATASTORE_NAME) },
            )
            return RoutingLocalStore(ds)
        }
    }

    private val budgetMutex = Mutex()

    suspend fun getOrCreateRolloutBucket(): Int {
        val existing = readPrefs()[KEY_INSTALL_ROLLOUT_BUCKET]
        if (existing != null && existing in 0..999) {
            return existing
        }
        val generated = secureRandom.nextInt(1000)
        dataStore.edit { prefs ->
            val current = prefs[KEY_INSTALL_ROLLOUT_BUCKET]
            if (current == null || current !in 0..999) {
                prefs[KEY_INSTALL_ROLLOUT_BUCKET] = generated
            }
        }
        return readPrefs()[KEY_INSTALL_ROLLOUT_BUCKET] ?: generated
    }

    /**
     * Atomically checks and consumes 1 automatic probe slot within the rolling 24-hour window.
     * Returns `true` if allowed and recorded, or `false` if the 24h cap is exhausted.
     */
    suspend fun tryConsumeAutomaticProbeBudget(
        nowEpochMs: Long,
        isMeteredOrUnknown: Boolean,
    ): Boolean = budgetMutex.withLock {
        var allowed = false
        dataStore.edit { prefs ->
            val cutoff = nowEpochMs - ROLLING_WINDOW_MILLIS
            val activeEntries = parseBudgetLog(prefs[KEY_PROBE_LOG_CSV].orEmpty())
                .filter { it.timestampMs > cutoff }
                .toMutableList()

            val totalUsed = activeEntries.size
            val meteredUsed = activeEntries.count { it.metered }

            val withinTotal = totalUsed < MAX_AUTOMATIC_PROBES_PER_24H
            val withinMetered = !isMeteredOrUnknown || meteredUsed < MAX_METERED_PROBES_PER_24H

            if (withinTotal && withinMetered) {
                activeEntries.add(BudgetEntry(nowEpochMs, isMeteredOrUnknown))
                allowed = true
            }
            prefs[KEY_PROBE_LOG_CSV] = formatBudgetLog(activeEntries)
        }
        allowed
    }

    suspend fun remainingAutomaticBudgetSnapshot(
        nowEpochMs: Long,
        isMeteredOrUnknown: Boolean,
    ): Int {
        val cutoff = nowEpochMs - ROLLING_WINDOW_MILLIS
        val entries = parseBudgetLog(readPrefs()[KEY_PROBE_LOG_CSV].orEmpty())
            .filter { it.timestampMs > cutoff }
        val totalRemaining = (MAX_AUTOMATIC_PROBES_PER_24H - entries.size).coerceAtLeast(0)
        return if (isMeteredOrUnknown) {
            val meteredRemaining =
                (MAX_METERED_PROBES_PER_24H - entries.count { it.metered }).coerceAtLeast(0)
            minOf(totalRemaining, meteredRemaining)
        } else {
            totalRemaining
        }
    }

    internal suspend fun saveValidatedManifest(
        rawJson: String,
        etag: String?,
        issuedAtEpochMs: Long,
        enabled: Boolean,
    ) {
        dataStore.edit { prefs ->
            val newestPrev = prefs[KEY_NEWEST_ISSUED_AT_MS] ?: 0L
            if (issuedAtEpochMs >= newestPrev) {
                prefs[KEY_NEWEST_ISSUED_AT_MS] = issuedAtEpochMs
                prefs[KEY_CACHED_MANIFEST_JSON] = rawJson
                if (etag != null) {
                    prefs[KEY_CACHED_MANIFEST_ETAG] = etag
                }
                if (!enabled) {
                    prefs[KEY_REVOKED_ISSUED_AT_MS] = issuedAtEpochMs
                }
            }
        }
    }

    internal suspend fun readCachedManifestRecord(): CachedManifestRecord {
        val prefs = readPrefs()
        return CachedManifestRecord(
            rawJson = prefs[KEY_CACHED_MANIFEST_JSON],
            etag = prefs[KEY_CACHED_MANIFEST_ETAG],
            newestIssuedAtMs = prefs[KEY_NEWEST_ISSUED_AT_MS] ?: 0L,
            revokedIssuedAtMs = prefs[KEY_REVOKED_ISSUED_AT_MS] ?: 0L,
        )
    }

    internal data class CachedManifestRecord(
        val rawJson: String?,
        val etag: String?,
        val newestIssuedAtMs: Long,
        val revokedIssuedAtMs: Long,
    )

    private data class BudgetEntry(
        val timestampMs: Long,
        val metered: Boolean,
    )

    private fun parseBudgetLog(csv: String): List<BudgetEntry> {
        if (csv.isBlank()) return emptyList()
        return csv.split(',')
            .mapNotNull { token ->
                val colon = token.indexOf(':')
                if (colon <= 0) return@mapNotNull null
                val ts = token.substring(0, colon).toLongOrNull() ?: return@mapNotNull null
                val metered = token.substring(colon + 1) == "1"
                BudgetEntry(ts, metered)
            }
    }

    private fun formatBudgetLog(entries: List<BudgetEntry>): String {
        return entries.joinToString(",") { "${it.timestampMs}:${if (it.metered) "1" else "0"}" }
    }

    private suspend fun readPrefs(): Preferences {
        return dataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .first()
    }
}

/**
 * Parser, validator, and multi-origin fetcher for `wenku8-ip-candidates-v1` JSON manifests and
 * `_network_cfg.wenku8.app` DNS `TXT` records resolved over pure-IP domestic DoH (`223.5.5.5`,
 * `1.12.12.12`, `223.6.6.6`, `120.53.53.53`).
 */
class ManifestRepository(
    private val localStore: RoutingLocalStore,
    private val baseOkHttpClient: OkHttpClient = OkHttpProbeTransport.defaultSharedBaseOkHttpClient(),
    private val configurationOrigins: List<String> = emptyList(),
    private val dohTxtDomain: String? = null,
    private val dohEndpoints: List<DohTxtResolverEndpoint> = emptyList(),
    private val shippedFallbackEndpointIds: Set<String> = emptySet(),
    private val bundledReleaseSeeds: List<CandidateIpEntry> = emptyList(),
    private val bundledCandidateSpecs: List<CandidateAddressSpec> = emptyList(),
    private val requirePublicUnicastForCandidates: Boolean = true,
    private val random: Random = SecureRandom(),
    private val wallClockEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val monotonicClockMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    sealed interface DohTxtParseOutcome {
        data class ValidManifest(
            val manifest: ValidatedCandidateManifest,
            val canonicalJson: String,
            val minTtlSeconds: Int,
        ) : DohTxtParseOutcome

        data class NoConfigRecordPresent(val negativeTtlSeconds: Int) : DohTxtParseOutcome
        data object InvalidOrServerFailure : DohTxtParseOutcome
    }

    companion object {
        const val MANIFEST_SCHEMA_V1: String = "wenku8-ip-candidates-v1"
        const val TXT_SCHEMA_V1: String = "wk8-txt-v1"
        const val TXT_CONTINUATION_SCHEMA_V1: String = "wk8-txt-ext1"
        const val DEFAULT_DOH_TXT_CONFIG_DOMAIN: String = "_network_cfg.wenku8.app"

        const val MAX_MANIFEST_BYTES: Int = 16 * 1024
        const val MAX_CANDIDATE_COUNT: Int = 64
        const val MAX_AUTHORIZATION_LIFETIME_MS: Long = 24L * 60L * 60L * 1_000L
        private const val OMITTED_IAT_SENTINEL_EPOCH_MS: Long = 1_000L
        private const val MIN_DOH_CACHE_TTL_SEC: Int = 60
        private const val MAX_DOH_CACHE_TTL_SEC: Int = 6 * 3600
        private const val DEFAULT_NEGATIVE_DOH_TTL_SEC: Int = 300
        private val CANDIDATE_ID_REGEX = Regex("^[a-zA-Z0-9._-]{1,40}$")

        /**
         * Domestic pure-IP HTTPS JSON DoH endpoints whose X.509 certificates include the numeric IP
         * in Subject Alternative Names (SAN), requiring **zero bootstrap OS DNS lookups**.
         */
        fun defaultDomesticDohEndpoints(): List<DohTxtResolverEndpoint> = listOf(
            DohTxtResolverEndpoint(
                id = "alidns-primary",
                resolverIpLiteral = "223.5.5.5",
                resolveUrlTemplate = "https://223.5.5.5/resolve?name=%s&type=TXT",
            ),
            DohTxtResolverEndpoint(
                id = "dnspod-primary",
                resolverIpLiteral = "1.12.12.12",
                resolveUrlTemplate = "https://1.12.12.12/resolve?name=%s&type=TXT",
            ),
            DohTxtResolverEndpoint(
                id = "alidns-secondary",
                resolverIpLiteral = "223.6.6.6",
                resolveUrlTemplate = "https://223.6.6.6/resolve?name=%s&type=TXT",
            ),
            DohTxtResolverEndpoint(
                id = "dnspod-secondary",
                resolverIpLiteral = "120.53.53.53",
                resolveUrlTemplate = "https://120.53.53.53/resolve?name=%s&type=TXT",
            ),
        )

        /**
         * Live-verified Cloudflare Web Edge Anycast subnets across 7 independent supernets
         * (`104.16/13`, `104.24/14`, `141.101/18`, `162.159/16`, `172.64/13`, `188.114/20`,
         * `190.93/20`, plus `2606:4700:3030::/48` & `2606:4700:3031::/48` IPv6), representing
         * `5,588+` verified IPv4 edge addresses and `2^160` IPv6 edge addresses as offline seed
         * fallback when `_network_cfg.wenku8.app` has not been published yet or is unreachable.
         */
        fun defaultVerifiedCloudflareSubnetSpecs(): List<CandidateAddressSpec> {
            val rawCidrs = listOf(
                "104.16.80.0/24",
                "104.17.120.0/24",
                "104.18.32.0/24",
                "104.19.144.0/24",
                "104.20.40.0/24",
                "104.21.48.0/24",
                "104.24.32.0/24",
                "104.25.80.0/24",
                "104.26.10.0/24",
                "104.27.64.0/24",
                "172.64.144.0/24",
                "172.66.40.0/24",
                "172.67.150.0/24",
                "172.67.200.0/24",
                "162.159.135.0/24",
                "162.159.140.0/24",
                "162.159.152.0/24",
                "188.114.97.0/24",
                "188.114.98.0/24",
                "190.93.244.0/24",
                "190.93.245.0/24",
                "141.101.120.0/24",
                "2606:4700:3030::/48",
                "2606:4700:3031::/48",
            )
            return rawCidrs.mapIndexedNotNull { index, cidr ->
                val idPrefix = String.format(Locale.ROOT, "cf-seed-%02d", index + 1)
                IpLiteralParser.parseCandidateSpec(
                    idPrefix = idPrefix,
                    rawIpOrCidr = cidr,
                    requirePublicUnicast = true,
                )
            }
        }

        /**
         * Unquotes an RFC 1035 DNS TXT `data` presentation string (e.g. `"\"chunk1\" \"chunk2\""`
         * or unquoted raw string), handling `\"`, `\\`, and 3-digit `\DDD` escapes.
         */
        fun unquoteRfc1035TxtData(rawData: String): String {
            val trimmed = rawData.trim()
            if (trimmed.isEmpty()) return ""
            if (!trimmed.startsWith('"')) {
                return trimmed
            }
            val out = ByteArrayOutputStream(trimmed.length)
            var i = 0
            val n = trimmed.length
            while (i < n) {
                while (i < n && trimmed[i].isWhitespace()) i++
                if (i >= n) break
                if (trimmed[i] != '"') {
                    // Malformed segment boundary; return best-effort stripped fallback
                    return trimmed.removeSurrounding("\"")
                }
                i++ // consume opening quote
                while (i < n && trimmed[i] != '"') {
                    val ch = trimmed[i]
                    if (ch == '\\' && i + 1 < n) {
                        val next = trimmed[i + 1]
                        if (next in '0'..'9' && i + 3 < n &&
                            trimmed[i + 2] in '0'..'9' &&
                            trimmed[i + 3] in '0'..'9'
                        ) {
                            val decimalVal = (next - '0') * 100 +
                                (trimmed[i + 2] - '0') * 10 +
                                (trimmed[i + 3] - '0')
                            out.write(decimalVal and 0xFF)
                            i += 4
                        } else {
                            val utf8Bytes = next.toString().toByteArray(StandardCharsets.UTF_8)
                            out.write(utf8Bytes, 0, utf8Bytes.size)
                            i += 2
                        }
                    } else {
                        val utf8Bytes = ch.toString().toByteArray(StandardCharsets.UTF_8)
                        out.write(utf8Bytes, 0, utf8Bytes.size)
                        i++
                    }
                }
                if (i < n && trimmed[i] == '"') {
                    i++ // consume closing quote
                }
            }
            return String(out.toByteArray(), StandardCharsets.UTF_8)
        }

        /**
         * Parses an Alidns (`223.5.5.5`) or DNSPod (`1.12.12.12`) JSON DoH response for `type=TXT` (`16`)
         * and extracts a validated `wk8-txt-v1` (+ optional `wk8-txt-ext1` continuation shards) config.
         *
         * TXT record format on `_network_cfg.wenku8.app`:
         * `v=wk8-txt-v1;rev=20261118-1;en=1;[min=0];[ro=1000];[hosts=*];[iat=1763460000];[exp=1795000000];c=104.16.80.0/24,172.67.150.0/24`
         * Optional extra shards:
         * `v=wk8-txt-ext1;rev=20261118-1;p=1;c=162.159.135.0/24,188.114.97.0/24`
         */
        fun parseDohJsonResponse(
            rawDohJson: String,
            expectedRelayHostname: String,
            nowWallEpochMs: Long,
            nowMonotonicMs: Long,
            shippedFallbackIds: Set<String> = emptySet(),
            requirePublicUnicast: Boolean = true,
        ): DohTxtParseOutcome {
            if (rawDohJson.isBlank() || rawDohJson.length > MAX_MANIFEST_BYTES * 2) {
                return DohTxtParseOutcome.InvalidOrServerFailure
            }
            val root = try {
                JSONObject(rawDohJson)
            } catch (_: Exception) {
                return DohTxtParseOutcome.InvalidOrServerFailure
            }

            if (!root.has("Status")) return DohTxtParseOutcome.InvalidOrServerFailure
            val status = root.optInt("Status", -1)
            if (status == 3) {
                // NXDOMAIN: DNS record not created yet; inspect Authority SOA TTL if available
                val negTtl = extractAuthorityMinTtl(root) ?: DEFAULT_NEGATIVE_DOH_TTL_SEC
                return DohTxtParseOutcome.NoConfigRecordPresent(negTtl)
            }
            if (status != 0) {
                return DohTxtParseOutcome.InvalidOrServerFailure
            }

            val answerArray = root.optJSONArray("Answer")
                ?: return DohTxtParseOutcome.NoConfigRecordPresent(DEFAULT_NEGATIVE_DOH_TTL_SEC)

            val txtRecords = mutableListOf<String>()
            var minTtl = Int.MAX_VALUE
            for (i in 0 until answerArray.length()) {
                val rr = answerArray.optJSONObject(i) ?: continue
                if (rr.optInt("type", -1) != 16) continue
                val dataStr = rr.optString("data", "")
                if (dataStr.isBlank()) continue
                val ttl = rr.optInt("TTL", 300).coerceAtLeast(MIN_DOH_CACHE_TTL_SEC)
                if (ttl < minTtl) minTtl = ttl
                txtRecords.add(unquoteRfc1035TxtData(dataStr))
            }

            if (txtRecords.isEmpty()) {
                return DohTxtParseOutcome.NoConfigRecordPresent(DEFAULT_NEGATIVE_DOH_TTL_SEC)
            }

            val effectiveTtl = if (minTtl == Int.MAX_VALUE) 300 else minTtl.coerceIn(MIN_DOH_CACHE_TTL_SEC, MAX_DOH_CACHE_TTL_SEC)
            return parseTxtConfigStrings(
                txtStrings = txtRecords,
                minTtlSeconds = effectiveTtl,
                expectedRelayHostname = expectedRelayHostname,
                nowWallEpochMs = nowWallEpochMs,
                nowMonotonicMs = nowMonotonicMs,
                shippedFallbackIds = shippedFallbackIds,
                requirePublicUnicast = requirePublicUnicast,
            )
        }

        fun parseTxtConfigStrings(
            txtStrings: List<String>,
            minTtlSeconds: Int,
            expectedRelayHostname: String,
            nowWallEpochMs: Long,
            nowMonotonicMs: Long,
            shippedFallbackIds: Set<String> = emptySet(),
            requirePublicUnicast: Boolean = true,
        ): DohTxtParseOutcome {
            val normalizedExpectedHost = expectedRelayHostname.trim().trimEnd('.').lowercase(Locale.ROOT)
            if (normalizedExpectedHost.isEmpty()) return DohTxtParseOutcome.InvalidOrServerFailure

            val parsedKvMaps = txtStrings.map { parseSemicolonKvMap(it) }
            val primaryCandidates = parsedKvMaps.filter { it["v"] == TXT_SCHEMA_V1 }
            if (primaryCandidates.isEmpty()) {
                return DohTxtParseOutcome.NoConfigRecordPresent(minTtlSeconds)
            }

            // Pick newest primary record by `iat` if multiple exist transiently during DNS propagation.
            val primary = primaryCandidates.maxByOrNull { kv ->
                kv["iat"]?.toLongOrNull() ?: 0L
            }!!

            val revision = primary["rev"]?.trim().orEmpty()
            if (!CANDIDATE_ID_REGEX.matches(revision)) {
                return DohTxtParseOutcome.InvalidOrServerFailure
            }

            val enabled = when (primary["en"]?.trim()?.lowercase(Locale.ROOT)) {
                "1", "true" -> true
                "0", "false" -> false
                else -> return DohTxtParseOutcome.InvalidOrServerFailure
            }

            val hostsSpec = primary["hosts"]?.trim().orEmpty()
            if (hostsSpec.isNotEmpty() && hostsSpec != "*") {
                val allowedHosts = hostsSpec.split(',')
                    .map { it.trim().trimEnd('.').lowercase(Locale.ROOT) }
                    .filter { it.isNotEmpty() }
                if (!allowedHosts.contains(normalizedExpectedHost)) {
                    return DohTxtParseOutcome.InvalidOrServerFailure
                }
            }

            val minVer = primary["min"]?.trim()?.let {
                it.toIntOrNull()?.takeIf { v -> v >= 0 } ?: return DohTxtParseOutcome.InvalidOrServerFailure
            } ?: 0

            val rollout = primary["ro"]?.trim()?.let {
                it.toIntOrNull()?.takeIf { v -> v in 0..1000 } ?: return DohTxtParseOutcome.InvalidOrServerFailure
            } ?: 1000

            val issuedAtMs = primary["iat"]?.trim()?.let { rawIat ->
                val sec = rawIat.toLongOrNull()?.takeIf { it >= 0L }
                    ?: return DohTxtParseOutcome.InvalidOrServerFailure
                sec * 1_000L
            } ?: OMITTED_IAT_SENTINEL_EPOCH_MS

            if (issuedAtMs > nowWallEpochMs + 5 * 60 * 1_000L) {
                return DohTxtParseOutcome.InvalidOrServerFailure
            }

            val expiresAtMs = primary["exp"]?.trim()?.let { rawExp ->
                val sec = rawExp.toLongOrNull()?.takeIf { it > 0L }
                    ?: return DohTxtParseOutcome.InvalidOrServerFailure
                sec * 1_000L
            } ?: (nowWallEpochMs + minTtlSeconds.coerceIn(MIN_DOH_CACHE_TTL_SEC, 86_400) * 1_000L)

            if (expiresAtMs <= issuedAtMs || nowWallEpochMs >= expiresAtMs) {
                return DohTxtParseOutcome.InvalidOrServerFailure
            }

            val fallbackIds = primary["fb"]?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() && shippedFallbackIds.contains(it) }
                .orEmpty()

            // Gather `c=` tokens from primary + matching `wk8-txt-ext1` continuation records sorted by `p`.
            val extShards = parsedKvMaps
                .filter { it["v"] == TXT_CONTINUATION_SCHEMA_V1 && it["rev"]?.trim() == revision }
                .mapNotNull { kv ->
                    val partNum = kv["p"]?.trim()?.toIntOrNull() ?: return@mapNotNull null
                    partNum to kv["c"].orEmpty()
                }
                .sortedBy { it.first }

            val rawTokens = buildList {
                primary["c"]?.split(',')?.forEach { token ->
                    val clean = token.trim()
                    if (clean.isNotEmpty()) add(clean)
                }
                extShards.forEach { (_, shardCsv) ->
                    shardCsv.split(',').forEach { token ->
                        val clean = token.trim()
                        if (clean.isNotEmpty()) add(clean)
                    }
                }
            }

            if (rawTokens.size > MAX_CANDIDATE_COUNT) {
                return DohTxtParseOutcome.InvalidOrServerFailure
            }

            val canonicalJsonCandidates = JSONArray()
            val seenIds = HashSet<String>()
            val seenCanonicalSpecs = HashSet<String>()
            for ((index, token) in rawTokens.withIndex()) {
                val atIdx = token.indexOf('@')
                val explicitId: String?
                val specText: String
                if (atIdx > 0) {
                    explicitId = token.substring(0, atIdx).trim()
                    specText = token.substring(atIdx + 1).trim()
                    if (!CANDIDATE_ID_REGEX.matches(explicitId)) {
                        return DohTxtParseOutcome.InvalidOrServerFailure
                    }
                } else {
                    explicitId = null
                    specText = token
                }
                val idPrefix = explicitId ?: String.format(Locale.ROOT, "cf-txt-%02d", index + 1)
                if (!seenIds.add(idPrefix)) return DohTxtParseOutcome.InvalidOrServerFailure

                val spec = IpLiteralParser.parseCandidateSpec(
                    idPrefix = idPrefix,
                    rawIpOrCidr = specText,
                    requirePublicUnicast = requirePublicUnicast,
                ) ?: return DohTxtParseOutcome.InvalidOrServerFailure

                if (!seenCanonicalSpecs.add(spec.canonicalSpec)) {
                    continue
                }
                canonicalJsonCandidates.put(
                    JSONObject()
                        .put("id", idPrefix)
                        .put("ip", spec.canonicalSpec),
                )
            }

            val canonicalJsonObj = JSONObject()
                .put("schema", MANIFEST_SCHEMA_V1)
                .put("revision", revision)
                .put("hostname", normalizedExpectedHost)
                .put("enabled", enabled)
                .put("minAppVersionCode", minVer)
                .put("rolloutPermille", rollout)
                .put("fallbackEndpointIds", JSONArray(fallbackIds))
                .put("issuedAt", Instant.ofEpochMilli(issuedAtMs).toString())
                .put("expiresAt", Instant.ofEpochMilli(expiresAtMs).toString())
                .put("candidates", canonicalJsonCandidates)
            val canonicalJson = canonicalJsonObj.toString()

            val validated = parseAndValidateManifestJson(
                rawJson = canonicalJson,
                expectedRelayHostname = normalizedExpectedHost,
                nowWallEpochMs = nowWallEpochMs,
                nowMonotonicMs = nowMonotonicMs,
                shippedFallbackIds = shippedFallbackIds,
                requirePublicUnicast = requirePublicUnicast,
            ) ?: return DohTxtParseOutcome.InvalidOrServerFailure

            return DohTxtParseOutcome.ValidManifest(
                manifest = validated,
                canonicalJson = canonicalJson,
                minTtlSeconds = minTtlSeconds,
            )
        }

        private fun parseSemicolonKvMap(record: String): Map<String, String> {
            val result = LinkedHashMap<String, String>()
            for (part in record.split(';')) {
                val trimmed = part.trim()
                if (trimmed.isEmpty()) continue
                val eq = trimmed.indexOf('=')
                if (eq <= 0) continue
                val key = trimmed.substring(0, eq).trim().lowercase(Locale.ROOT)
                val value = trimmed.substring(eq + 1).trim()
                result[key] = value
            }
            return result
        }

        private fun extractAuthorityMinTtl(root: JSONObject): Int? {
            val auth = root.optJSONArray("Authority") ?: return null
            var minTtl = Int.MAX_VALUE
            for (i in 0 until auth.length()) {
                val rr = auth.optJSONObject(i) ?: continue
                val ttl = rr.optInt("TTL", -1)
                if (ttl > 0 && ttl < minTtl) minTtl = ttl
            }
            return if (minTtl == Int.MAX_VALUE) null else minTtl.coerceIn(MIN_DOH_CACHE_TTL_SEC, 1800)
        }

        fun parseAndValidateManifestJson(
            rawJson: String,
            expectedRelayHostname: String,
            nowWallEpochMs: Long,
            nowMonotonicMs: Long,
            shippedFallbackIds: Set<String> = emptySet(),
            requirePublicUnicast: Boolean = true,
            sampleRandom: Random = SecureRandom(),
        ): ValidatedCandidateManifest? {
            val utf8Length = rawJson.toByteArray(StandardCharsets.UTF_8).size
            if (utf8Length == 0 || utf8Length > MAX_MANIFEST_BYTES) return null

            val root = try {
                JSONObject(rawJson)
            } catch (_: Exception) {
                return null
            }

            val schema = readRequiredString(root, "schema") ?: return null
            if (schema != MANIFEST_SCHEMA_V1) return null

            val revision = readRequiredString(root, "revision") ?: return null
            val hostname = readRequiredString(root, "hostname")?.trim()?.trimEnd('.')?.lowercase(Locale.ROOT)
                ?: return null
            if (!hostname.equals(expectedRelayHostname.trim().trimEnd('.'), ignoreCase = true)) {
                return null
            }

            if (!root.has("enabled") || root.isNull("enabled")) return null
            val enabledRaw = root.opt("enabled")
            if (enabledRaw !is Boolean) return null

            val minAppVersionCode = readRequiredNonNegativeInt(root, "minAppVersionCode") ?: return null
            val rolloutPermille = readRequiredNonNegativeInt(root, "rolloutPermille") ?: return null
            if (rolloutPermille > 1000) return null

            val issuedAtStr = readRequiredString(root, "issuedAt") ?: return null
            val expiresAtStr = readRequiredString(root, "expiresAt") ?: return null
            val issuedAtMs = runCatching { Instant.parse(issuedAtStr).toEpochMilli() }.getOrNull()
                ?: return null
            val expiresAtMs = runCatching { Instant.parse(expiresAtStr).toEpochMilli() }.getOrNull()
                ?: return null
            if (expiresAtMs <= issuedAtMs) return null

            // Allow up to 5 minutes clock skew on issuedAt, reject manifests from arbitrary future
            if (issuedAtMs > nowWallEpochMs + 5 * 60 * 1_000L) return null

            val fallbackIds = mutableListOf<String>()
            if (root.has("fallbackEndpointIds") && !root.isNull("fallbackEndpointIds")) {
                val arr = root.opt("fallbackEndpointIds") as? JSONArray ?: return null
                for (i in 0 until arr.length()) {
                    val item = arr.opt(i) as? String ?: return null
                    if (shippedFallbackIds.contains(item)) {
                        fallbackIds.add(item)
                    }
                }
            }

            val candidatesArray = root.opt("candidates") as? JSONArray ?: return null
            if (candidatesArray.length() > MAX_CANDIDATE_COUNT) return null

            val seenSpecs = HashSet<String>()
            val seenIds = HashSet<String>()
            val parsedSpecs = ArrayList<CandidateAddressSpec>(candidatesArray.length())
            val initialSampledCandidates = ArrayList<CandidateIpEntry>(candidatesArray.length())

            for (i in 0 until candidatesArray.length()) {
                val entryObj = candidatesArray.opt(i) as? JSONObject ?: return null
                val id = readRequiredString(entryObj, "id") ?: return null
                if (!CANDIDATE_ID_REGEX.matches(id) || !seenIds.add(id)) return null

                val ipOrCidrStr = readRequiredString(entryObj, "ip")?.trim() ?: return null
                val spec = IpLiteralParser.parseCandidateSpec(
                    idPrefix = id,
                    rawIpOrCidr = ipOrCidrStr,
                    requirePublicUnicast = requirePublicUnicast,
                ) ?: return null

                if (!seenSpecs.add(spec.canonicalSpec)) return null
                val sampledEntry = spec.sampleCandidateEntry(
                    random = sampleRandom,
                    requirePublicUnicast = requirePublicUnicast,
                ) ?: return null

                parsedSpecs.add(spec)
                initialSampledCandidates.add(sampledEntry)
            }

            val rawRemainingMs = (expiresAtMs - nowWallEpochMs).coerceAtLeast(0L)
            val authorizedLifetimeMs = minOf(MAX_AUTHORIZATION_LIFETIME_MS, rawRemainingMs)

            return ValidatedCandidateManifest(
                schema = schema,
                revision = revision,
                hostname = hostname,
                enabled = enabledRaw,
                minAppVersionCode = minAppVersionCode,
                rolloutPermille = rolloutPermille,
                fallbackEndpointIds = fallbackIds,
                issuedAtEpochMs = issuedAtMs,
                expiresAtEpochMs = expiresAtMs,
                candidates = initialSampledCandidates,
                acceptedAtMonotonicMs = nowMonotonicMs,
                authorizedLifetimeMs = authorizedLifetimeMs,
                candidateSpecs = parsedSpecs,
            )
        }

        private fun readRequiredString(obj: JSONObject, key: String): String? {
            if (!obj.has(key) || obj.isNull(key)) return null
            val v = obj.opt(key) as? String ?: return null
            return v.takeIf { it.isNotBlank() && it != "null" }
        }

        private fun readRequiredNonNegativeInt(obj: JSONObject, key: String): Int? {
            if (!obj.has(key) || obj.isNull(key)) return null
            val v = obj.opt(key)
            if (v is Boolean || v !is Number) return null
            val intVal = v.toInt()
            return intVal.takeIf { it >= 0 }
        }
    }

    private val refreshMutex = Mutex()
    @Volatile private var inMemoryManifest: ValidatedCandidateManifest? = null
    @Volatile private var inMemoryDohValidUntilMonotonicMs: Long = 0L
    @Volatile private var inMemoryDohNegativeUntilMonotonicMs: Long = 0L

    fun bundledDiscoverySeeds(): List<CandidateIpEntry> {
        if (bundledCandidateSpecs.isNotEmpty()) {
            return bundledCandidateSpecs.mapNotNull { spec ->
                spec.sampleCandidateEntry(
                    random = random,
                    requirePublicUnicast = requirePublicUnicastForCandidates,
                )
            }
        }
        return bundledReleaseSeeds
    }

    suspend fun loadCachedOrInMemoryManifest(expectedRelayHostname: String): ValidatedCandidateManifest? {
        inMemoryManifest?.let { current ->
            if (current.hostname.equals(expectedRelayHostname, ignoreCase = true)) {
                return current
            }
        }
        val record = localStore.readCachedManifestRecord()
        val raw = record.rawJson ?: return null
        val parsed = parseAndValidateManifestJson(
            rawJson = raw,
            expectedRelayHostname = expectedRelayHostname,
            nowWallEpochMs = wallClockEpochMillis(),
            nowMonotonicMs = monotonicClockMillis(),
            shippedFallbackIds = shippedFallbackEndpointIds,
            requirePublicUnicast = requirePublicUnicastForCandidates,
            sampleRandom = random,
        ) ?: return null
        if (parsed.issuedAtEpochMs < record.revokedIssuedAtMs) {
            return null
        }
        inMemoryManifest = parsed
        return parsed
    }

    /**
     * Ingests a manifest JSON payload (from an independent configuration origin, DoH TXT record,
     * or bootstrap mirror), enforcing anti-rollback so an older mirror cannot undo a newer revocation.
     */
    suspend fun ingestManifestPayload(
        rawJson: String,
        expectedRelayHostname: String,
        etag: String? = null,
    ): ValidatedCandidateManifest? = refreshMutex.withLock {
        val nowWall = wallClockEpochMillis()
        val nowMono = monotonicClockMillis()
        val parsed = parseAndValidateManifestJson(
            rawJson = rawJson,
            expectedRelayHostname = expectedRelayHostname,
            nowWallEpochMs = nowWall,
            nowMonotonicMs = nowMono,
            shippedFallbackIds = shippedFallbackEndpointIds,
            requirePublicUnicast = requirePublicUnicastForCandidates,
            sampleRandom = random,
        ) ?: return@withLock null

        val record = localStore.readCachedManifestRecord()
        if (parsed.issuedAtEpochMs < record.newestIssuedAtMs) {
            // Reject stale rollback from a lagging mirror; return current newest if available.
            return@withLock inMemoryManifest ?: loadCachedOrInMemoryManifest(expectedRelayHostname)
        }

        localStore.saveValidatedManifest(
            rawJson = rawJson,
            etag = etag,
            issuedAtEpochMs = parsed.issuedAtEpochMs,
            enabled = parsed.enabled,
        )
        inMemoryManifest = parsed
        parsed
    }

    /**
     * Resolves the freshest candidate manifest across:
     * 1. Pure-IP domestic DoH `TXT` record (`_network_cfg.wenku8.app` via `223.5.5.5` / `1.12.12.12`),
     * 2. Configured independent HTTPS JSON origins (`configurationOrigins`),
     * 3. Locally persisted manifest cache (`RoutingLocalStore`, preserving remote `enabled=false` revocations),
     * 4. Bundled verified Cloudflare Web Edge `/24` & `/48` Anycast subnet seeds (`5,588+` IPs).
     */
    suspend fun refreshFromIndependentOrigins(
        expectedRelayHostname: String,
        forceRefresh: Boolean = false,
    ): ValidatedCandidateManifest? = withContext(ioDispatcher) {
        val nowMono = monotonicClockMillis()
        if (!forceRefresh && nowMono < inMemoryDohValidUntilMonotonicMs) {
            loadCachedOrInMemoryManifest(expectedRelayHostname)?.let { return@withContext it }
        }

        // 1. Pure-IP DoH TXT config lookup (`_network_cfg.wenku8.app`)
        val trimmedTxtDomain = dohTxtDomain?.trim()?.trimEnd('.')
        if (!trimmedTxtDomain.isNullOrEmpty() &&
            dohEndpoints.isNotEmpty() &&
            (forceRefresh || nowMono >= inMemoryDohNegativeUntilMonotonicMs)
        ) {
            when (val dohResult = queryDohTxtEndpoints(trimmedTxtDomain, expectedRelayHostname)) {
                is DohTxtParseOutcome.ValidManifest -> {
                    val ingested = ingestManifestPayload(
                        rawJson = dohResult.canonicalJson,
                        expectedRelayHostname = expectedRelayHostname,
                    )
                    if (ingested != null) {
                        inMemoryDohValidUntilMonotonicMs =
                            monotonicClockMillis() + dohResult.minTtlSeconds * 1_000L
                        inMemoryDohNegativeUntilMonotonicMs = 0L
                        return@withContext ingested
                    }
                }
                is DohTxtParseOutcome.NoConfigRecordPresent -> {
                    inMemoryDohNegativeUntilMonotonicMs =
                        monotonicClockMillis() + dohResult.negativeTtlSeconds * 1_000L
                }
                DohTxtParseOutcome.InvalidOrServerFailure -> {
                    // Fall through to HTTP JSON origins / cached / bundled subnet seeds
                }
            }
        }

        // 2. Optional independent HTTPS JSON configuration origins
        if (configurationOrigins.isNotEmpty()) {
            val cachedRecord = localStore.readCachedManifestRecord()
            val fetchClient = baseOkHttpClient.newBuilder()
                .cookieJar(CookieJar.NO_COOKIES)
                .dns(Dns.SYSTEM)
                .followRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(3_000L, TimeUnit.MILLISECONDS)
                .readTimeout(4_000L, TimeUnit.MILLISECONDS)
                .callTimeout(5_000L, TimeUnit.MILLISECONDS)
                .build()

            for (originUrl in configurationOrigins) {
                val reqBuilder = Request.Builder()
                    .url(originUrl)
                    .get()
                if (!cachedRecord.etag.isNullOrBlank() && !cachedRecord.rawJson.isNullOrBlank()) {
                    reqBuilder.header("If-None-Match", cachedRecord.etag)
                }
                try {
                    fetchClient.newCall(reqBuilder.build()).execute().use { response ->
                        if (response.code == 304) {
                            // 304 saves transfer, never extends expiresAt!
                            loadCachedOrInMemoryManifest(expectedRelayHostname)?.let { return@withContext it }
                        }
                        if (response.code == 200) {
                            val bodyBytes = readBoundedManifestBytes(response.body.byteStream())
                                ?: continue
                            val jsonText = String(bodyBytes, StandardCharsets.UTF_8)
                            val validated = ingestManifestPayload(
                                rawJson = jsonText,
                                expectedRelayHostname = expectedRelayHostname,
                                etag = response.header("ETag"),
                            )
                            if (validated != null) {
                                return@withContext validated
                            }
                        }
                    }
                } catch (_: IOException) {
                    // Try alternate independent origin
                }
            }
        }

        // 3. Cached or in-memory manifest (strictly honors persisted `enabled = false` kill-switches)
        val cachedManifest = loadCachedOrInMemoryManifest(expectedRelayHostname)
        if (cachedManifest != null) {
            if (!cachedManifest.enabled) {
                // Active remote kill-switch: NEVER bypass with bundled seeds!
                return@withContext cachedManifest
            }
            val nowWall = wallClockEpochMillis()
            val curMono = monotonicClockMillis()
            if (cachedManifest.remainingAuthorizationMillis(nowWall, curMono) > 0L) {
                return@withContext cachedManifest
            }
        }

        // 4. Offline / bootstrap seed fallback (`defaultVerifiedCloudflareSubnetSpecs`)
        buildBundledSeedManifest(expectedRelayHostname)
    }

    private fun queryDohTxtEndpoints(
        txtDomain: String,
        expectedRelayHostname: String,
    ): DohTxtParseOutcome {
        val encodedDomain = URLEncoder.encode(txtDomain, StandardCharsets.UTF_8.name())
        for (endpoint in dohEndpoints) {
            val requestUrl = String.format(Locale.ROOT, endpoint.resolveUrlTemplate, encodedDomain)
            val targetHost = runCatching { URI(requestUrl).host?.trim()?.trimEnd('.') }.getOrNull()
                ?: continue

            val dnsAdapter = if (!endpoint.resolverIpLiteral.isNullOrBlank()) {
                val rawBytes = IpLiteralParser.parseNumericIpBytes(
                    rawLiteral = endpoint.resolverIpLiteral,
                    requirePublicUnicast = requirePublicUnicastForCandidates,
                ) ?: continue
                Dns { lookupHost ->
                    if (lookupHost.trimEnd('.').equals(targetHost, ignoreCase = true)) {
                        listOf(InetAddress.getByAddress(targetHost, rawBytes.clone()))
                    } else {
                        Dns.SYSTEM.lookup(lookupHost)
                    }
                }
            } else {
                Dns.SYSTEM
            }

            val dohClient = baseOkHttpClient.newBuilder()
                .cookieJar(CookieJar.NO_COOKIES)
                .dns(dnsAdapter)
                .followRedirects(false)
                .retryOnConnectionFailure(false)
                .connectTimeout(3_000L, TimeUnit.MILLISECONDS)
                .readTimeout(3_500L, TimeUnit.MILLISECONDS)
                .callTimeout(4_500L, TimeUnit.MILLISECONDS)
                .build()

            val request = Request.Builder()
                .url(requestUrl)
                .header("Accept", "application/dns-json, application/json")
                .get()
                .build()

            try {
                dohClient.newCall(request).execute().use { response ->
                    if (response.code != 200) continue
                    val bodyBytes = readBoundedManifestBytes(response.body.byteStream()) ?: continue
                    val jsonText = String(bodyBytes, StandardCharsets.UTF_8)
                    val outcome = parseDohJsonResponse(
                        rawDohJson = jsonText,
                        expectedRelayHostname = expectedRelayHostname,
                        nowWallEpochMs = wallClockEpochMillis(),
                        nowMonotonicMs = monotonicClockMillis(),
                        shippedFallbackIds = shippedFallbackEndpointIds,
                        requirePublicUnicast = requirePublicUnicastForCandidates,
                    )
                    when (outcome) {
                        is DohTxtParseOutcome.ValidManifest -> return outcome
                        is DohTxtParseOutcome.NoConfigRecordPresent -> return outcome
                        DohTxtParseOutcome.InvalidOrServerFailure -> continue
                    }
                }
            } catch (_: IOException) {
                // Try next independent pure-IP DoH resolver (e.g. 223.5.5.5 -> 1.12.12.12)
            }
        }
        return DohTxtParseOutcome.InvalidOrServerFailure
    }

    private fun buildBundledSeedManifest(expectedRelayHostname: String): ValidatedCandidateManifest? {
        val normalizedHost = expectedRelayHostname.trim().trimEnd('.').lowercase(Locale.ROOT)
        if (normalizedHost.isEmpty()) return null
        if (bundledCandidateSpecs.isEmpty() && bundledReleaseSeeds.isEmpty()) {
            return null
        }

        val nowWall = wallClockEpochMillis()
        val nowMono = monotonicClockMillis()
        val sampled = if (bundledCandidateSpecs.isNotEmpty()) {
            bundledCandidateSpecs.mapNotNull { spec ->
                spec.sampleCandidateEntry(
                    random = random,
                    requirePublicUnicast = requirePublicUnicastForCandidates,
                )
            }
        } else {
            bundledReleaseSeeds
        }
        if (sampled.isEmpty()) return null

        return ValidatedCandidateManifest(
            schema = MANIFEST_SCHEMA_V1,
            revision = "bundled-cf-subnets-v1",
            hostname = normalizedHost,
            enabled = true,
            minAppVersionCode = 0,
            rolloutPermille = 1000,
            fallbackEndpointIds = emptyList(),
            issuedAtEpochMs = OMITTED_IAT_SENTINEL_EPOCH_MS,
            expiresAtEpochMs = nowWall + MAX_AUTHORIZATION_LIFETIME_MS,
            candidates = sampled,
            acceptedAtMonotonicMs = nowMono,
            authorizedLifetimeMs = MAX_AUTHORIZATION_LIFETIME_MS,
            candidateSpecs = bundledCandidateSpecs,
        )
    }

    private fun readBoundedManifestBytes(stream: java.io.InputStream): ByteArray? {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(1024)
        var total = 0
        while (true) {
            val n = stream.read(buf)
            if (n == -1) break
            total += n
            if (total > MAX_MANIFEST_BYTES) return null
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }
}
