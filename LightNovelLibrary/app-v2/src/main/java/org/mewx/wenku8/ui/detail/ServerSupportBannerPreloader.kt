package org.mewx.wenku8.ui.detail

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdPreloader
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Internal seam abstracting Next-Gen GMA SDK's static [BannerAdPreloader] API so JVM / Robolectric
 * tests can deterministically exercise buffer start, poll-hit, poll-miss, width reconfiguration,
 * priority staging, unimpressed recycling, post-reading one-shot stop, and consent-revocation
 * teardown.
 */
internal interface BannerPreloaderClient {
    fun start(
        preloadId: String,
        config: PreloadConfiguration,
        callback: PreloadCallback,
    ): Boolean

    fun pollAd(preloadId: String): BannerAd?
    fun isAdAvailable(preloadId: String): Boolean
    fun getNumAdsAvailable(preloadId: String): Int
    fun destroy(preloadId: String): Boolean
    fun destroyAll()
}

private object RealBannerPreloaderClient : BannerPreloaderClient {
    override fun start(
        preloadId: String,
        config: PreloadConfiguration,
        callback: PreloadCallback,
    ): Boolean = BannerAdPreloader.start(preloadId, config, callback)

    override fun pollAd(preloadId: String): BannerAd? = BannerAdPreloader.pollAd(preloadId)

    override fun isAdAvailable(preloadId: String): Boolean = BannerAdPreloader.isAdAvailable(preloadId)

    override fun getNumAdsAvailable(preloadId: String): Int =
        BannerAdPreloader.getNumAdsAvailable(preloadId)

    override fun destroy(preloadId: String): Boolean = BannerAdPreloader.destroy(preloadId)

    override fun destroyAll() {
        BannerAdPreloader.destroyAll()
    }
}

private data class RecycledBannerEntry(
    val bannerAd: BannerAd,
    val widthDp: Int,
    val recycledAtMonoMs: Long,
)

/**
 * Process-scoped coordinator around Next-Gen GMA SDK's [BannerAdPreloader].
 *
 * Design & AdMob policy guarantees:
 * - Strictly fail-closed on UMP consent (`canRequestAds == false`), offline state, and non-GMS
 *   devices (never initializes or starts preloading).
 * - Uses priority-balanced buffer sizes (`3` for Novel Detail standard, `2` for Discovery feed,
 *   `1` for one-shot Post-Reading large -> total `6`, matching Next-Gen SDK's 6-ad global in-memory
 *   buffer cap).
 * - Exposes reactive [readyCountFlow] per placement so secondary placements (`DISCOVERY_FEED_INLINE`
 *   and `NOVEL_DETAIL_POST_READING_LARGE`) wait until `NOVEL_DETAIL_STANDARD` has at least one
 *   ready ad buffered, protecting cold-start reliability on the highest-priority surface.
 * - Recycles unimpressed [BannerAd]s (e.g., when a user enters `NovelDetailScreen` and taps
 *   "开始阅读" or backs out before `onAdImpression` fires) back into a bounded 1-slot stash per
 *   placement with a 45-minute freshness window so preloaded inventory is never wasted.
 * - Immediately tears down the one-shot [InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE]
 *   preloader upon polling so the SDK does not auto-refill another 250dp creative during the
 *   30-minute post-reading cooldown.
 * - Reconfigures a placement's preloader automatically when container width shifts by `>= 48dp`
 *   (e.g., phone rotation or foldable/tablet two-pane resize).
 * - Immediately invokes [BannerAdPreloader.destroyAll] and destroys any recycled unimpressed ads
 *   whenever consent is revoked.
 */
object ServerSupportBannerPreloader {
    private const val TAG = "BannerAdPreloader"
    internal const val WIDTH_RECONFIGURE_THRESHOLD_DP = 48
    internal const val NOVEL_DETAIL_STANDARD_BUFFER_SIZE = 3
    internal const val DISCOVERY_FEED_BUFFER_SIZE = 2
    internal const val POST_READING_LARGE_BUFFER_SIZE = 1
    internal const val RECYCLED_AD_TTL_MS = 45L * 60_000L

    private val lock = Any()
    private val activeWidthByPlacement = ConcurrentHashMap<InlineBannerPlacement, Int>()
    private val preloadStartMonoMsByPlacement = ConcurrentHashMap<InlineBannerPlacement, Long>()
    private val recycledByPlacement = ConcurrentHashMap<InlineBannerPlacement, RecycledBannerEntry>()
    private val readyCountFlows = InlineBannerPlacement.entries.associateWith { MutableStateFlow(0) }
    private val consentAllowed = AtomicBoolean(false)

    @Volatile
    internal var client: BannerPreloaderClient = RealBannerPreloaderClient

    @Volatile
    internal var isOnlineProvider: (Context) -> Boolean = { hasActiveInternetCapability(it) }

    @Volatile
    internal var isGmsReadyProvider: (Context) -> Boolean = { isGooglePlayServicesReady(it) }

    @Volatile
    internal var isDebugBuildProvider: () -> Boolean = { org.mewx.wenku8.BuildConfig.DEBUG }

    @Volatile
    internal var monotonicTimeMsProvider: () -> Long = { SystemClock.elapsedRealtime() }

    fun defaultBufferSizeFor(placement: InlineBannerPlacement): Int = when (placement) {
        InlineBannerPlacement.NOVEL_DETAIL_STANDARD -> NOVEL_DETAIL_STANDARD_BUFFER_SIZE
        InlineBannerPlacement.DISCOVERY_FEED_INLINE -> DISCOVERY_FEED_BUFFER_SIZE
        InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE -> POST_READING_LARGE_BUFFER_SIZE
    }

    fun preloadIdFor(placement: InlineBannerPlacement): String = when (placement) {
        InlineBannerPlacement.NOVEL_DETAIL_STANDARD -> "wenku8_banner_novel_detail_standard"
        InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE ->
            "wenku8_banner_novel_detail_post_reading_large"
        InlineBannerPlacement.DISCOVERY_FEED_INLINE -> "wenku8_banner_discovery_feed_inline"
    }

    /**
     * Reactive count of ready-to-display buffered banners (SDK preloader queue + any fresh
     * recycled unimpressed banner) for [placement].
     */
    fun readyCountFlow(placement: InlineBannerPlacement): StateFlow<Int> =
        readyCountFlows.getValue(placement).asStateFlow()

    fun readyCount(placement: InlineBannerPlacement): Int =
        readyCountFlows.getValue(placement).value

    /**
     * Synchronizes current UMP consent state. Transitions to `false` immediately destroy all
     * buffered preloaded ads and recycled unimpressed ads so no unconsented ad can remain cached.
     */
    fun onConsentStateChanged(canRequestAds: Boolean) {
        val previous = consentAllowed.getAndSet(canRequestAds)
        if (!canRequestAds && (previous || activeWidthByPlacement.isNotEmpty() || recycledByPlacement.isNotEmpty())) {
            destroyAll()
        }
    }

    /**
     * Ensures a [BannerAdPreloader] is active for [placement] at [availableWidthDp] using the
     * placement's configured [bufferSize] (`3 / 2 / 1` by default).
     *
     * Idempotent when already active for a compatible width (`< 48dp` delta). Safe to call from
     * any coroutine dispatcher.
     */
    suspend fun ensurePreloading(
        context: Context,
        placement: InlineBannerPlacement,
        availableWidthDp: Int,
        canRequestAds: Boolean,
        bufferSize: Int = defaultBufferSizeFor(placement),
    ): Boolean {
        if (!canRequestAds) {
            return false
        }
        consentAllowed.set(true)
        val appContext = context.applicationContext ?: context
        if (!isGmsReadyProvider(appContext) || !isOnlineProvider(appContext)) {
            return false
        }

        val safeWidthDp = availableWidthDp.coerceAtLeast(160)
        val existingWidth = activeWidthByPlacement[placement]
        if (existingWidth != null && abs(existingWidth - safeWidthDp) < WIDTH_RECONFIGURE_THRESHOLD_DP) {
            refreshReadyCountLocked(placement)
            return true
        }

        ServerSupportAdInitializer.ensureInitialized(appContext)

        // Re-check consent & connectivity after initialization suspends.
        if (!consentAllowed.get() || !isOnlineProvider(appContext)) {
            return false
        }

        val adUnitId = ServerSupportAdConstants.candidateBannerAdUnitIds(
            placement = placement,
            isDebugBuild = isDebugBuildProvider(),
        ).firstOrNull() ?: return false

        val adSize = resolveInlineAdaptiveBannerAdSize(
            availableWidthDp = safeWidthDp,
            placement = placement,
        )
        val request = BannerAdRequest.Builder(adUnitId, adSize).build()
        val preloadId = preloadIdFor(placement)
        val config = PreloadConfiguration(
            request = request,
            bufferSize = bufferSize.coerceAtLeast(1),
        )

        return synchronized(lock) {
            if (!consentAllowed.get()) {
                return@synchronized false
            }
            val currentWidth = activeWidthByPlacement[placement]
            if (currentWidth != null && abs(currentWidth - safeWidthDp) < WIDTH_RECONFIGURE_THRESHOLD_DP) {
                refreshReadyCountLocked(placement)
                return@synchronized true
            }
            if (currentWidth != null) {
                runCatching { client.destroy(preloadId) }
                activeWidthByPlacement.remove(placement)
                discardIncompatibleRecycledLocked(placement, safeWidthDp)
            }

            val startMonoMs = monotonicTimeMsProvider()
            preloadStartMonoMsByPlacement[placement] = startMonoMs
            val started = runCatching {
                client.start(
                    preloadId = preloadId,
                    config = config,
                    callback = object : PreloadCallback {
                        override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                            val nowMono = monotonicTimeMsProvider()
                            val refStart = preloadStartMonoMsByPlacement.put(placement, nowMono) ?: startMonoMs
                            val elapsedMs = (nowMono - refStart).coerceAtLeast(0L)
                            synchronized(lock) {
                                refreshReadyCountLocked(placement)
                            }
                            runCatching {
                                Log.d(
                                    TAG,
                                    "Preloaded $placement (${safeWidthDp}dp, buf=${config.bufferSize}, ready=${readyCount(placement)}) in ${elapsedMs}ms [id=$preloadId]",
                                )
                            }
                        }

                        override fun onAdsExhausted(preloadId: String) {
                            preloadStartMonoMsByPlacement[placement] = monotonicTimeMsProvider()
                            synchronized(lock) {
                                refreshReadyCountLocked(placement)
                            }
                            runCatching {
                                Log.d(TAG, "Preload buffer exhausted for $placement [id=$preloadId]")
                            }
                        }

                        override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
                            val nowMono = monotonicTimeMsProvider()
                            val refStart = preloadStartMonoMsByPlacement.put(placement, nowMono) ?: startMonoMs
                            val elapsedMs = (nowMono - refStart).coerceAtLeast(0L)
                            synchronized(lock) {
                                refreshReadyCountLocked(placement)
                            }
                            runCatching {
                                Log.d(
                                    TAG,
                                    "Preload failed for $placement after ${elapsedMs}ms (code=${adError.code}): ${adError.message}",
                                )
                            }
                        }
                    },
                )
            }.onFailure { t ->
                runCatching { Log.w(TAG, "BannerAdPreloader.start threw for $placement", t) }
            }.getOrDefault(false)

            if (started) {
                activeWidthByPlacement[placement] = safeWidthDp
            } else {
                preloadStartMonoMsByPlacement.remove(placement)
            }
            refreshReadyCountLocked(placement)
            started
        }
    }

    /**
     * Polls a preloaded [BannerAd] for [placement] if one is ready and matches [requestedWidthDp]
     * within [WIDTH_RECONFIGURE_THRESHOLD_DP].
     *
     * Prefers a fresh recycled unimpressed ad (if any) before consuming from the SDK's buffer.
     * For [InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE], stops the preloader right after
     * a successful poll so the SDK does not auto-refill during the 30-minute cooldown window.
     */
    fun pollReadyAd(
        placement: InlineBannerPlacement,
        requestedWidthDp: Int,
        canRequestAds: Boolean,
    ): BannerAd? {
        if (!canRequestAds || !consentAllowed.get()) {
            return null
        }
        val safeWidthDp = requestedWidthDp.coerceAtLeast(160)
        val preloadId = preloadIdFor(placement)
        val nowMono = monotonicTimeMsProvider()
        val polled = synchronized(lock) {
            // 1. Prefer a fresh unimpressed recycled ad if width-compatible.
            val recycled = recycledByPlacement[placement]
            if (recycled != null) {
                val isFresh = nowMono >= recycled.recycledAtMonoMs &&
                    (nowMono - recycled.recycledAtMonoMs) <= RECYCLED_AD_TTL_MS
                val isWidthCompatible = abs(recycled.widthDp - safeWidthDp) < WIDTH_RECONFIGURE_THRESHOLD_DP
                if (!isFresh) {
                    recycledByPlacement.remove(placement)
                    runCatching { recycled.bannerAd.destroy() }
                } else if (isWidthCompatible) {
                    recycledByPlacement.remove(placement)
                    refreshReadyCountLocked(placement)
                    runCatching {
                        Log.d(TAG, "Preload HIT (recycled unimpressed) for $placement (${safeWidthDp}dp)")
                    }
                    return@synchronized recycled.bannerAd
                }
            }

            // 2. Otherwise poll from Next-Gen SDK's BannerAdPreloader buffer.
            val activeWidth = activeWidthByPlacement[placement] ?: return@synchronized null
            if (abs(activeWidth - safeWidthDp) >= WIDTH_RECONFIGURE_THRESHOLD_DP) {
                return@synchronized null
            }
            val ad = runCatching { client.pollAd(preloadId) }
                .onFailure { t ->
                    runCatching { Log.w(TAG, "BannerAdPreloader.pollAd threw for $placement", t) }
                }
                .getOrNull()
            if (ad != null) {
                if (placement == InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE) {
                    // One-shot consumption: prevent background auto-refill during 30-min cooldown.
                    runCatching { client.destroy(preloadId) }
                    activeWidthByPlacement.remove(placement)
                    preloadStartMonoMsByPlacement.remove(placement)
                } else {
                    preloadStartMonoMsByPlacement[placement] = nowMono
                }
            }
            refreshReadyCountLocked(placement)
            ad
        }
        if (polled != null) {
            runCatching { Log.d(TAG, "Preload HIT for $placement (${safeWidthDp}dp)") }
        }
        return polled
    }

    /**
     * Returns an unimpressed [BannerAd] back to the 1-slot placement stash so leaving a screen
     * before the ad was ever displayed does not waste preloaded inventory.
     *
     * If consent has been revoked or another unimpressed ad is already stashed, the displaced ad
     * is destroyed cleanly.
     */
    fun recycleUnimpressedAd(
        placement: InlineBannerPlacement,
        widthDp: Int,
        bannerAd: BannerAd,
    ) {
        val safeWidthDp = widthDp.coerceAtLeast(160)
        synchronized(lock) {
            if (!consentAllowed.get()) {
                runCatching { bannerAd.destroy() }
                return
            }
            val previous = recycledByPlacement.put(
                placement,
                RecycledBannerEntry(
                    bannerAd = bannerAd,
                    widthDp = safeWidthDp,
                    recycledAtMonoMs = monotonicTimeMsProvider(),
                ),
            )
            if (previous != null && previous.bannerAd !== bannerAd) {
                runCatching { previous.bannerAd.destroy() }
            }
            refreshReadyCountLocked(placement)
        }
        runCatching {
            Log.d(TAG, "Recycled unimpressed banner for $placement (${safeWidthDp}dp)")
        }
    }

    fun isPlacementPreloading(placement: InlineBannerPlacement): Boolean =
        activeWidthByPlacement.containsKey(placement)

    fun isAdReady(
        placement: InlineBannerPlacement,
        requestedWidthDp: Int? = null,
    ): Boolean {
        if (!consentAllowed.get()) return false
        val nowMono = monotonicTimeMsProvider()
        return synchronized(lock) {
            val recycled = recycledByPlacement[placement]
            if (recycled != null) {
                val isFresh = nowMono >= recycled.recycledAtMonoMs &&
                    (nowMono - recycled.recycledAtMonoMs) <= RECYCLED_AD_TTL_MS
                val widthOk = requestedWidthDp == null ||
                    abs(recycled.widthDp - requestedWidthDp.coerceAtLeast(160)) < WIDTH_RECONFIGURE_THRESHOLD_DP
                if (isFresh && widthOk) {
                    return@synchronized true
                }
            }
            val activeWidth = activeWidthByPlacement[placement] ?: return@synchronized false
            if (requestedWidthDp != null &&
                abs(activeWidth - requestedWidthDp.coerceAtLeast(160)) >= WIDTH_RECONFIGURE_THRESHOLD_DP
            ) {
                return@synchronized false
            }
            runCatching { client.isAdAvailable(preloadIdFor(placement)) }.getOrDefault(false)
        }
    }

    fun stopPlacement(placement: InlineBannerPlacement) {
        val preloadId = preloadIdFor(placement)
        synchronized(lock) {
            activeWidthByPlacement.remove(placement)
            preloadStartMonoMsByPlacement.remove(placement)
            recycledByPlacement.remove(placement)?.let { runCatching { it.bannerAd.destroy() } }
            runCatching { client.destroy(preloadId) }
            refreshReadyCountLocked(placement)
        }
    }

    fun destroyAll() {
        synchronized(lock) {
            activeWidthByPlacement.clear()
            preloadStartMonoMsByPlacement.clear()
            val recycledSnapshot = recycledByPlacement.values.toList()
            recycledByPlacement.clear()
            recycledSnapshot.forEach { runCatching { it.bannerAd.destroy() } }
            runCatching { client.destroyAll() }
            InlineBannerPlacement.entries.forEach { readyCountFlows.getValue(it).value = 0 }
        }
    }

    private fun discardIncompatibleRecycledLocked(placement: InlineBannerPlacement, safeWidthDp: Int) {
        val existing = recycledByPlacement[placement] ?: return
        if (abs(existing.widthDp - safeWidthDp) >= WIDTH_RECONFIGURE_THRESHOLD_DP) {
            recycledByPlacement.remove(placement)
            runCatching { existing.bannerAd.destroy() }
        }
    }

    private fun refreshReadyCountLocked(placement: InlineBannerPlacement) {
        val nowMono = monotonicTimeMsProvider()
        val recycledCount = recycledByPlacement[placement]?.let { entry ->
            if (nowMono >= entry.recycledAtMonoMs &&
                (nowMono - entry.recycledAtMonoMs) <= RECYCLED_AD_TTL_MS
            ) {
                1
            } else {
                recycledByPlacement.remove(placement)
                runCatching { entry.bannerAd.destroy() }
                0
            }
        } ?: 0
        val sdkCount = if (activeWidthByPlacement.containsKey(placement)) {
            runCatching { client.getNumAdsAvailable(preloadIdFor(placement)).coerceAtLeast(0) }
                .getOrDefault(0)
        } else {
            0
        }
        readyCountFlows.getValue(placement).value = sdkCount + recycledCount
    }

    internal fun resetForTests() {
        synchronized(lock) {
            activeWidthByPlacement.clear()
            preloadStartMonoMsByPlacement.clear()
            val recycledSnapshot = recycledByPlacement.values.toList()
            recycledByPlacement.clear()
            recycledSnapshot.forEach { runCatching { it.bannerAd.destroy() } }
            consentAllowed.set(false)
            client = RealBannerPreloaderClient
            isOnlineProvider = { hasActiveInternetCapability(it) }
            isGmsReadyProvider = { isGooglePlayServicesReady(it) }
            isDebugBuildProvider = { org.mewx.wenku8.BuildConfig.DEBUG }
            monotonicTimeMsProvider = { SystemClock.elapsedRealtime() }
            InlineBannerPlacement.entries.forEach { readyCountFlows.getValue(it).value = 0 }
        }
    }
}
