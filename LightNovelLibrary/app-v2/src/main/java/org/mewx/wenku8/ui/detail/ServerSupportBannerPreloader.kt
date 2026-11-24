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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Internal seam abstracting Next-Gen GMA SDK's static [BannerAdPreloader] API so JVM / Robolectric
 * tests can deterministically exercise buffer start, poll-hit, poll-miss, width reconfiguration,
 * post-reading one-shot stop, and consent-revocation teardown.
 */
internal interface BannerPreloaderClient {
    fun start(
        preloadId: String,
        config: PreloadConfiguration,
        callback: PreloadCallback,
    ): Boolean

    fun pollAd(preloadId: String): BannerAd?
    fun isAdAvailable(preloadId: String): Boolean
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

    override fun destroy(preloadId: String): Boolean = BannerAdPreloader.destroy(preloadId)

    override fun destroyAll() {
        BannerAdPreloader.destroyAll()
    }
}

/**
 * Process-scoped coordinator around Next-Gen GMA SDK's [BannerAdPreloader].
 *
 * Design & AdMob policy guarantees:
 * - Strictly fail-closed on UMP consent (`canRequestAds == false`), offline state, and non-GMS
 *   devices (never initializes or starts preloading).
 * - Uses placement-tuned buffer sizes (`5` for Discovery feed, `2` for Novel Detail standard, `1`
 *   for one-shot Post-Reading large) so rapid feed scrolling and consecutive detail visits stay
 *   served from warm memory buffers.
 * - Immediately tears down the one-shot [InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE]
 *   preloader upon polling so the SDK does not auto-refill another 250dp creative during the
 *   30-minute post-reading cooldown.
 * - Reconfigures a placement's preloader automatically when container width shifts by `>= 48dp`
 *   (e.g., phone rotation or foldable/tablet two-pane resize).
 * - Immediately invokes [BannerAdPreloader.destroyAll] whenever consent is revoked.
 */
object ServerSupportBannerPreloader {
    private const val TAG = "BannerAdPreloader"
    internal const val WIDTH_RECONFIGURE_THRESHOLD_DP = 48
    internal const val DISCOVERY_FEED_BUFFER_SIZE = 5
    internal const val NOVEL_DETAIL_STANDARD_BUFFER_SIZE = 2
    internal const val POST_READING_LARGE_BUFFER_SIZE = 1

    private val lock = Any()
    private val activeWidthByPlacement = ConcurrentHashMap<InlineBannerPlacement, Int>()
    private val preloadStartMonoMsByPlacement = ConcurrentHashMap<InlineBannerPlacement, Long>()
    private val consentAllowed = AtomicBoolean(false)

    @Volatile
    internal var client: BannerPreloaderClient = RealBannerPreloaderClient

    @Volatile
    internal var isOnlineProvider: (Context) -> Boolean = { hasActiveInternetCapability(it) }

    @Volatile
    internal var isGmsReadyProvider: (Context) -> Boolean = { isGooglePlayServicesReady(it) }

    @Volatile
    internal var isDebugBuildProvider: () -> Boolean = { org.mewx.wenku8.BuildConfig.DEBUG }

    fun defaultBufferSizeFor(placement: InlineBannerPlacement): Int = when (placement) {
        InlineBannerPlacement.DISCOVERY_FEED_INLINE -> DISCOVERY_FEED_BUFFER_SIZE
        InlineBannerPlacement.NOVEL_DETAIL_STANDARD -> NOVEL_DETAIL_STANDARD_BUFFER_SIZE
        InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE -> POST_READING_LARGE_BUFFER_SIZE
    }

    fun preloadIdFor(placement: InlineBannerPlacement): String = when (placement) {
        InlineBannerPlacement.NOVEL_DETAIL_STANDARD -> "wenku8_banner_novel_detail_standard"
        InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE ->
            "wenku8_banner_novel_detail_post_reading_large"
        InlineBannerPlacement.DISCOVERY_FEED_INLINE -> "wenku8_banner_discovery_feed_inline"
    }

    /**
     * Synchronizes current UMP consent state. Transitions to `false` immediately destroy all
     * buffered preloaded ads so no unconsented ad can remain cached or be displayed.
     */
    fun onConsentStateChanged(canRequestAds: Boolean) {
        val previous = consentAllowed.getAndSet(canRequestAds)
        if (!canRequestAds && (previous || activeWidthByPlacement.isNotEmpty())) {
            destroyAll()
        }
    }

    /**
     * Ensures a [BannerAdPreloader] is active for [placement] at [availableWidthDp] using the
     * placement's configured [bufferSize] (`5 / 2 / 1` by default).
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
                return@synchronized true
            }
            if (currentWidth != null) {
                runCatching { client.destroy(preloadId) }
                activeWidthByPlacement.remove(placement)
            }

            val startMonoMs = SystemClock.elapsedRealtime()
            preloadStartMonoMsByPlacement[placement] = startMonoMs
            val started = runCatching {
                client.start(
                    preloadId = preloadId,
                    config = config,
                    callback = object : PreloadCallback {
                        override fun onAdPreloaded(preloadId: String, responseInfo: ResponseInfo) {
                            val nowMono = SystemClock.elapsedRealtime()
                            val refStart = preloadStartMonoMsByPlacement.put(placement, nowMono) ?: startMonoMs
                            val elapsedMs = (nowMono - refStart).coerceAtLeast(0L)
                            runCatching {
                                Log.d(
                                    TAG,
                                    "Preloaded $placement (${safeWidthDp}dp, buf=${config.bufferSize}) in ${elapsedMs}ms [id=$preloadId]",
                                )
                            }
                        }

                        override fun onAdsExhausted(preloadId: String) {
                            preloadStartMonoMsByPlacement[placement] = SystemClock.elapsedRealtime()
                            runCatching {
                                Log.d(TAG, "Preload buffer exhausted for $placement [id=$preloadId]")
                            }
                        }

                        override fun onAdFailedToPreload(preloadId: String, adError: LoadAdError) {
                            val nowMono = SystemClock.elapsedRealtime()
                            val refStart = preloadStartMonoMsByPlacement.put(placement, nowMono) ?: startMonoMs
                            val elapsedMs = (nowMono - refStart).coerceAtLeast(0L)
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
            started
        }
    }

    /**
     * Polls a preloaded [BannerAd] for [placement] if one is ready and matches [requestedWidthDp]
     * within [WIDTH_RECONFIGURE_THRESHOLD_DP].
     *
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
        val polled = synchronized(lock) {
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
                    preloadStartMonoMsByPlacement[placement] = SystemClock.elapsedRealtime()
                }
            }
            ad
        }
        if (polled != null) {
            runCatching { Log.d(TAG, "Preload HIT for $placement (${safeWidthDp}dp)") }
        }
        return polled
    }

    fun isPlacementPreloading(placement: InlineBannerPlacement): Boolean =
        activeWidthByPlacement.containsKey(placement)

    fun isAdReady(placement: InlineBannerPlacement): Boolean =
        activeWidthByPlacement.containsKey(placement) &&
            runCatching { client.isAdAvailable(preloadIdFor(placement)) }.getOrDefault(false)

    fun stopPlacement(placement: InlineBannerPlacement) {
        val preloadId = preloadIdFor(placement)
        synchronized(lock) {
            activeWidthByPlacement.remove(placement)
            preloadStartMonoMsByPlacement.remove(placement)
            runCatching { client.destroy(preloadId) }
        }
    }

    fun destroyAll() {
        synchronized(lock) {
            activeWidthByPlacement.clear()
            preloadStartMonoMsByPlacement.clear()
            runCatching { client.destroyAll() }
        }
    }

    internal fun resetForTests() {
        synchronized(lock) {
            activeWidthByPlacement.clear()
            preloadStartMonoMsByPlacement.clear()
            consentAllowed.set(false)
            client = RealBannerPreloaderClient
            isOnlineProvider = { hasActiveInternetCapability(it) }
            isGmsReadyProvider = { isGooglePlayServicesReady(it) }
            isDebugBuildProvider = { org.mewx.wenku8.BuildConfig.DEBUG }
        }
    }
}
