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
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.time.Instant
import java.util.Locale
import java.util.concurrent.TimeUnit

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
) {
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
 * Parser, validator, and multi-origin fetcher for `wenku8-ip-candidates-v1` manifests.
 */
class ManifestRepository(
    private val localStore: RoutingLocalStore,
    private val baseOkHttpClient: OkHttpClient = OkHttpProbeTransport.defaultSharedBaseOkHttpClient(),
    private val configurationOrigins: List<String> = emptyList(),
    private val shippedFallbackEndpointIds: Set<String> = emptySet(),
    private val bundledReleaseSeeds: List<CandidateIpEntry> = emptyList(),
    private val requirePublicUnicastForCandidates: Boolean = true,
    private val wallClockEpochMillis: () -> Long = { System.currentTimeMillis() },
    private val monotonicClockMillis: () -> Long = { SystemClock.elapsedRealtime() },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val MANIFEST_SCHEMA_V1: String = "wenku8-ip-candidates-v1"
        const val MAX_MANIFEST_BYTES: Int = 16 * 1024
        const val MAX_CANDIDATE_COUNT: Int = 16
        const val MAX_AUTHORIZATION_LIFETIME_MS: Long = 24L * 60L * 60L * 1_000L
        private val CANDIDATE_ID_REGEX = Regex("^[a-zA-Z0-9._-]{1,40}$")

        fun parseAndValidateManifestJson(
            rawJson: String,
            expectedRelayHostname: String,
            nowWallEpochMs: Long,
            nowMonotonicMs: Long,
            shippedFallbackIds: Set<String> = emptySet(),
            requirePublicUnicast: Boolean = true,
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

            val seenIps = HashSet<String>()
            val seenIds = HashSet<String>()
            val parsedCandidates = ArrayList<CandidateIpEntry>(candidatesArray.length())

            for (i in 0 until candidatesArray.length()) {
                val entryObj = candidatesArray.opt(i) as? JSONObject ?: return null
                val id = readRequiredString(entryObj, "id") ?: return null
                if (!CANDIDATE_ID_REGEX.matches(id) || !seenIds.add(id)) return null

                val ipStr = readRequiredString(entryObj, "ip")?.trim() ?: return null
                val ipBytes = IpLiteralParser.parseNumericIpBytes(
                    rawLiteral = ipStr,
                    requirePublicUnicast = requirePublicUnicast,
                ) ?: return null
                val canonicalKey = ipBytes.joinToString(",") { (it.toInt() and 0xFF).toString() }
                if (!seenIps.add(canonicalKey)) return null

                val family = IpAddressFamily.fromRawBytesOrNull(ipBytes) ?: return null
                parsedCandidates.add(
                    CandidateIpEntry(
                        id = id,
                        ipLiteral = ipStr,
                        rawIpBytes = ipBytes,
                        family = family,
                    ),
                )
            }

            if (enabledRaw && parsedCandidates.isEmpty() && rolloutPermille > 0) {
                // Enabled manifest with zero candidates is valid (operates as default-only).
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
                candidates = parsedCandidates,
                acceptedAtMonotonicMs = nowMonotonicMs,
                authorizedLifetimeMs = authorizedLifetimeMs,
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

    fun bundledDiscoverySeeds(): List<CandidateIpEntry> = bundledReleaseSeeds

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
        ) ?: return null
        if (parsed.issuedAtEpochMs < record.revokedIssuedAtMs) {
            return null
        }
        inMemoryManifest = parsed
        return parsed
    }

    /**
     * Ingests a manifest JSON payload (from an independent configuration origin or bootstrap mirror),
     * enforcing anti-rollback so an older mirror cannot undo a newer revocation.
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
        ) ?: return@withLock null

        val record = localStore.readCachedManifestRecord()
        if (parsed.issuedAtEpochMs < record.newestIssuedAtMs) {
            // Reject stale rollback from a lagging mirror; return current newest if available.
            return@withLock inMemoryManifest
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
     * Fetches the candidate manifest across configured independent HTTPS origins using system DNS
     * (`CookieJar.NO_COOKIES`, `5s` per-origin deadline, conditional `If-None-Match` where cached,
     * never extending `expiresAt` on HTTP 304).
     */
    suspend fun refreshFromIndependentOrigins(
        expectedRelayHostname: String,
    ): ValidatedCandidateManifest? = withContext(ioDispatcher) {
        if (configurationOrigins.isEmpty()) {
            return@withContext loadCachedOrInMemoryManifest(expectedRelayHostname)
        }
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
                        return@withContext loadCachedOrInMemoryManifest(expectedRelayHostname)
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
        loadCachedOrInMemoryManifest(expectedRelayHostname)
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
