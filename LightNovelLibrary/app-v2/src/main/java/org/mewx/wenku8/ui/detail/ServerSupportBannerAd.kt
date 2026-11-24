package org.mewx.wenku8.ui.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.AdLoadCallback
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.R
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

/**
 * Distinct inline adaptive banner placements across the app.
 *
 * Each placement specifies a Google-compliant maximum height cap for
 * [AdSize.getInlineAdaptiveBannerAdSize] and a reserved minimum slot height so the opaque card
 * avoids layout shift while loading.
 */
enum class InlineBannerPlacement(
    val maxHeightDp: Int,
    val reservedMinHeightDp: Int,
) {
    /** Standard full-width inline adaptive banner between Synopsis and Volume Directory. */
    NOVEL_DETAIL_STANDARD(maxHeightDp = 120, reservedMinHeightDp = 64),

    /**
     * Larger inline adaptive banner requested as a fresh `AdView` (never resizing an existing one)
     * when returning after a qualifying long reading session (`>= 10 min`), allowing higher-eCPM
     * `300x250` / rich inline adaptive creatives while hugging the actual creative height once loaded.
     */
    NOVEL_DETAIL_POST_READING_LARGE(maxHeightDp = 250, reservedMinHeightDp = 120),

    /**
     * Compact in-feed inline adaptive banner placed at page boundaries (every 10 novels) inside
     * Discovery (`最新` / `排行`) lists. Keeps `320x50` / `360x60` / `320x100` creatives eligible
     * while reserving only `60dp` and shrinking tightly to the returned ad's exact height.
     */
    DISCOVERY_FEED_INLINE(maxHeightDp = 100, reservedMinHeightDp = 60),
    ;

    val reservedMinHeight: Dp
        get() = reservedMinHeightDp.dp
}

object ServerSupportAdConstants {
    /**
     * Official Google demo AdMob App ID bound via manifest placeholder on debug builds.
     */
    const val DEV_TEST_ADMOB_APP_ID = "ca-app-pub-3940256099942544~3347511713"

    /**
     * Production AdMob App ID bound via manifest placeholder on release builds (`org.mewx.wenku8`).
     */
    const val PRODUCTION_ADMOB_APP_ID = "ca-app-pub-7333757578973883~9635263460"

    /**
     * Official Google demo Banner Ad Unit ID used exclusively on debug builds so local and
     * open-source contributors' debug builds never request live production ads or trigger
     * AdMob Invalid Traffic policy warnings.
     */
    const val DEV_TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    /**
     * Production Novel Detail standard inline banner Ad Unit ID matching [PRODUCTION_ADMOB_APP_ID]
     * and Wenku8 v1 (`org.mewx.wenku8`), used exclusively on release builds (`!BuildConfig.DEBUG`).
     */
    const val PRODUCTION_BANNER_AD_UNIT_ID = "ca-app-pub-7333757578973883/9658350163"

    /**
     * Dedicated production Ad Unit ID for post-reading large inline adaptive banners
     * (`maxHeight = 250dp`, AdMob unit: `Novel Detail - Post-Reading Large Banner`).
     */
    const val PRODUCTION_POST_READING_LARGE_BANNER_AD_UNIT_ID = "ca-app-pub-7333757578973883/8618142337"

    /**
     * Dedicated production Ad Unit ID for Discovery feed (`最新` / `排行`) inline adaptive banners
     * (AdMob unit: `Discovery Feed - Inline Banner`).
     */
    const val PRODUCTION_DISCOVERY_FEED_BANNER_AD_UNIT_ID = "ca-app-pub-7333757578973883/8426570642"

    /**
     * Build-type-strict AdMob App ID passed programmatically to Next-Gen GMA SDK's
     * [InitializationConfig] (matching the manifest placeholder consumed by UMP).
     */
    fun activeAdMobAppId(isDebugBuild: Boolean = BuildConfig.DEBUG): String =
        if (isDebugBuild) DEV_TEST_ADMOB_APP_ID else PRODUCTION_ADMOB_APP_ID

    /**
     * Ordered Ad Unit IDs to try for [placement]:
     * - Debug builds (`isDebugBuild = true`): strictly [DEV_TEST_BANNER_AD_UNIT_ID] only.
     * - Release builds (`isDebugBuild = false`): strictly the placement's production unit ID.
     */
    fun candidateBannerAdUnitIds(
        placement: InlineBannerPlacement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
        isDebugBuild: Boolean = BuildConfig.DEBUG,
    ): List<String> =
        if (isDebugBuild) {
            listOf(DEV_TEST_BANNER_AD_UNIT_ID)
        } else {
            val productionUnitId = when (placement) {
                InlineBannerPlacement.NOVEL_DETAIL_STANDARD -> PRODUCTION_BANNER_AD_UNIT_ID
                InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE ->
                    PRODUCTION_POST_READING_LARGE_BANNER_AD_UNIT_ID
                InlineBannerPlacement.DISCOVERY_FEED_INLINE ->
                    PRODUCTION_DISCOVERY_FEED_BANNER_AD_UNIT_ID
            }
            listOf(productionUnitId)
        }

    val RESERVED_BANNER_SLOT_HEIGHT = InlineBannerPlacement.NOVEL_DETAIL_STANDARD.reservedMinHeight

    /** Minimum active reading duration before returning to `NovelDetailScreen` upgrades to a large banner. */
    const val POST_READING_LARGE_AD_MIN_DURATION_MS = 10L * 60_000L

    /** Cooldown between elevated post-reading large banner triggers to keep UX calm. */
    const val POST_READING_LARGE_AD_COOLDOWN_MS = 30L * 60_000L

    /** Freshness window during which a pending elevation token applies to a detail screen visit. */
    const val POST_READING_LARGE_AD_FRESHNESS_MS = 10L * 60_000L
}

/**
 * Immutable token emitted when a user finishes a qualifying long reading session, signalling
 * `NovelDetailScreen` to request a fresh [InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE]
 * banner rather than resizing an existing `AdView`.
 */
data class PostReadingAdElevationToken(
    val nonce: Int,
    val targetAid: Int,
    val issuedAtMonoMs: Long,
) {
    fun isFreshAt(
        nowMonoMs: Long,
        freshnessWindowMs: Long = ServerSupportAdConstants.POST_READING_LARGE_AD_FRESHNESS_MS,
    ): Boolean = nowMonoMs >= issuedAtMonoMs && (nowMonoMs - issuedAtMonoMs) <= freshnessWindowMs
}

/**
 * Pure monotonic-clock tracker that determines when exiting `UnifiedReaderScreen` should elevate
 * the subsequent `NovelDetailScreen` banner to [InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE].
 */
class PostReadingAdElevationTracker(
    private val minReadingDurationMs: Long = ServerSupportAdConstants.POST_READING_LARGE_AD_MIN_DURATION_MS,
    private val cooldownMs: Long = ServerSupportAdConstants.POST_READING_LARGE_AD_COOLDOWN_MS,
) {
    private var activeAid: Int = 0
    private var sessionStartedAtMonoMs: Long = 0L
    private var lastElevatedAtMonoMs: Long = Long.MIN_VALUE
    private var nextNonce: Int = 0

    fun onReaderSessionStarted(aid: Int, nowMonoMs: Long) {
        if (aid <= 0) return
        activeAid = aid
        sessionStartedAtMonoMs = nowMonoMs
    }

    fun onReaderSessionEnded(nowMonoMs: Long): PostReadingAdElevationToken? {
        val endedAid = activeAid
        val startedAt = sessionStartedAtMonoMs
        activeAid = 0
        sessionStartedAtMonoMs = 0L
        if (endedAid <= 0 || startedAt <= 0L) return null

        val elapsedMs = (nowMonoMs - startedAt).coerceAtLeast(0L)
        if (elapsedMs < minReadingDurationMs) return null

        val withinCooldown = lastElevatedAtMonoMs != Long.MIN_VALUE &&
            (nowMonoMs - lastElevatedAtMonoMs) < cooldownMs
        if (withinCooldown) return null

        lastElevatedAtMonoMs = nowMonoMs
        nextNonce += 1
        return PostReadingAdElevationToken(
            nonce = nextNonce,
            targetAid = endedAid,
            issuedAtMonoMs = nowMonoMs,
        )
    }

    fun resetActiveSession() {
        activeAid = 0
        sessionStartedAtMonoMs = 0L
    }
}

/**
 * Reactive UMP consent gate provided by `MainActivity` from `AdConsentManager.canRequestAds`.
 *
 * Defaults to `false` (fail-closed) so no AdMob SDK initialization or banner request can occur
 * unless consent has been confirmed (or explicitly overridden in unit/Compose tests).
 */
val LocalAdConsentCanRequestAds = compositionLocalOf { false }

object ServerSupportAdInitializer {
    private val initStarted = AtomicBoolean(false)

    @Volatile
    private var initCompleted = CompletableDeferred<Unit>()

    @Volatile
    internal var testInitAction: ((Context, () -> Unit) -> Unit)? = null

    internal val hasStartedInitialization: Boolean
        get() = initStarted.get()

    fun warmUpInBackground(context: Context, scope: CoroutineScope) {
        val appContext = context.applicationContext
        scope.launch(Dispatchers.IO) {
            if (isGooglePlayServicesReady(appContext)) {
                ensureInitialized(appContext)
            }
        }
    }

    suspend fun ensureInitialized(context: Context) {
        val appContext = context.applicationContext
        val completion = initCompleted
        if (initStarted.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) {
                val override = testInitAction
                if (override != null) {
                    runCatching {
                        override(appContext) {
                            completion.complete(Unit)
                        }
                    }.onFailure {
                        completion.complete(Unit)
                    }
                } else {
                    runCatching {
                        val initConfig = InitializationConfig.Builder(
                            ServerSupportAdConstants.activeAdMobAppId(),
                        )
                            .disableMediationAdapterInitialization()
                            .build()
                        MobileAds.initialize(appContext, initConfig) {
                            completion.complete(Unit)
                        }
                    }.onFailure { t ->
                        runCatching { Log.w(AD_LOG_TAG, "Next-Gen MobileAds.initialize failed", t) }
                        completion.complete(Unit)
                    }
                }
            }
        }
        withTimeoutOrNull(4_000L) {
            completion.await()
        }
    }

    internal fun resetForTests() {
        initStarted.set(false)
        testInitAction = null
        initCompleted = CompletableDeferred()
    }
}

enum class BannerAdLoadPhase {
    Collapsed,
    Loading,
    Loaded,
    StaticPreview,
    Failed,
}

/**
 * Screen-scoped holder for an inline adaptive banner ad.
 *
 * Loads strictly on-demand inside the visible, opaque reserved card container once the slot is
 * measured on screen (never preloaded into an off-screen/0-alpha container during cold start),
 * survives `LazyColumn` scrolling within the visit without re-requesting, and destroys its `AdView`
 * cleanly when leaving the screen or when a fresh placement/nonce is requested.
 */
@Stable
class NovelDetailBannerAdHolder internal constructor(
    initialPhase: BannerAdLoadPhase,
    val placement: InlineBannerPlacement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
) {
    var phase by mutableStateOf(initialPhase)
        internal set

    var adView by mutableStateOf<AdView?>(null)
        internal set

    internal var loadInProgress: Boolean = false
    internal var loadedWidthDp: Int = 0
    internal var isDisposed: Boolean = false

    /**
     * Render the visible, opaque reserved card during [BannerAdLoadPhase.Loading] (preventing
     * layout shift and keeping `AdView` visibly mounted per AdMob policy) as well as when
     * [BannerAdLoadPhase.Loaded] or [BannerAdLoadPhase.StaticPreview]. Collapses completely when
     * offline, GMS-unavailable ([BannerAdLoadPhase.Collapsed]), or on load failure
     * ([BannerAdLoadPhase.Failed]).
     */
    val shouldRenderVisibleCard: Boolean
        get() = phase == BannerAdLoadPhase.Loading ||
            phase == BannerAdLoadPhase.Loaded ||
            phase == BannerAdLoadPhase.StaticPreview

    fun markLoadedForTest() {
        phase = BannerAdLoadPhase.Loaded
    }

    fun markFailedForTest() {
        phase = BannerAdLoadPhase.Failed
    }

    fun markCollapsedForTest() {
        phase = BannerAdLoadPhase.Collapsed
    }

    internal fun destroy(markDisposed: Boolean = false) {
        if (markDisposed) {
            isDisposed = true
        }
        loadInProgress = false
        loadedWidthDp = 0
        val existing = adView
        adView = null
        if (existing != null) {
            runCatching {
                (existing.parent as? ViewGroup)?.removeView(existing)
                existing.destroy()
            }
        }
    }
}

@Composable
fun rememberNovelDetailBannerAdHolder(
    aid: Int,
    placement: InlineBannerPlacement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
    requestNonce: Int = 0,
    isNetworkAvailableOverride: Boolean? = null,
    isGmsAvailableOverride: Boolean? = null,
    canRequestAdsOverride: Boolean? = null,
    enableLiveAdViewInflation: Boolean = true,
): NovelDetailBannerAdHolder {
    val context = LocalContext.current
    val isOnline = isNetworkAvailableOverride ?: remember(aid, placement, requestNonce, context) {
        hasActiveInternetCapability(context)
    }
    val isGmsReady = isGmsAvailableOverride ?: remember(aid, placement, requestNonce, context) {
        isGooglePlayServicesReady(context)
    }
    val canRequestAds = canRequestAdsOverride ?: LocalAdConsentCanRequestAds.current

    val holder = remember(
        aid,
        placement,
        requestNonce,
        isOnline,
        isGmsReady,
        canRequestAds,
        enableLiveAdViewInflation,
    ) {
        val initialPhase = when {
            !canRequestAds || !isOnline || !isGmsReady -> BannerAdLoadPhase.Collapsed
            !enableLiveAdViewInflation -> BannerAdLoadPhase.StaticPreview
            else -> BannerAdLoadPhase.Loading
        }
        NovelDetailBannerAdHolder(
            initialPhase = initialPhase,
            placement = placement,
        )
    }

    DisposableEffect(holder) {
        onDispose {
            holder.destroy(markDisposed = true)
        }
    }

    return holder
}

/**
 * Bounded LRU pool of [NovelDetailBannerAdHolder] instances scoped to a Discovery feed (`最新` /
 * `排行`), capping concurrent live WebView-backed `AdView`s to [maxCachedSlots] while keeping
 * recently scrolled slots warm so scrolling back up reuses the loaded banner without re-requesting.
 */
@Stable
class DiscoveryFeedInlineAdPool internal constructor(
    val initialPhase: BannerAdLoadPhase,
    val maxCachedSlots: Int = DEFAULT_MAX_CACHED_SLOTS,
) {
    private val holders = LinkedHashMap<String, NovelDetailBannerAdHolder>(
        /* initialCapacity = */ (maxCachedSlots.coerceAtLeast(1) * 2),
        /* loadFactor = */ 0.75f,
        /* accessOrder = */ true,
    )

    val isAdSlotEligible: Boolean
        get() = initialPhase != BannerAdLoadPhase.Collapsed

    fun getOrCreate(slotKey: String): NovelDetailBannerAdHolder {
        val existing = holders[slotKey]
        if (existing != null && !existing.isDisposed) {
            return existing
        }
        val created = NovelDetailBannerAdHolder(
            initialPhase = initialPhase,
            placement = InlineBannerPlacement.DISCOVERY_FEED_INLINE,
        )
        holders[slotKey] = created
        val limit = maxCachedSlots.coerceAtLeast(1)
        while (holders.size > limit) {
            val eldest = holders.entries.iterator().next()
            holders.remove(eldest.key)
            eldest.value.destroy(markDisposed = true)
        }
        return created
    }

    internal fun activeSlotKeys(): Set<String> = holders.keys.toSet()

    internal fun destroyAll() {
        val snapshot = holders.values.toList()
        holders.clear()
        snapshot.forEach { it.destroy(markDisposed = true) }
    }

    companion object {
        const val DEFAULT_MAX_CACHED_SLOTS = 3
    }
}

@Composable
fun rememberDiscoveryFeedInlineAdPool(
    poolScopeKey: Any,
    isNetworkAvailableOverride: Boolean? = null,
    isGmsAvailableOverride: Boolean? = null,
    canRequestAdsOverride: Boolean? = null,
    enableLiveAdViewInflation: Boolean = true,
    maxCachedSlots: Int = DiscoveryFeedInlineAdPool.DEFAULT_MAX_CACHED_SLOTS,
): DiscoveryFeedInlineAdPool {
    val context = LocalContext.current
    val isOnline = isNetworkAvailableOverride ?: remember(poolScopeKey, context) {
        hasActiveInternetCapability(context)
    }
    val isGmsReady = isGmsAvailableOverride ?: remember(poolScopeKey, context) {
        isGooglePlayServicesReady(context)
    }
    val canRequestAds = canRequestAdsOverride ?: LocalAdConsentCanRequestAds.current

    val pool = remember(
        poolScopeKey,
        isOnline,
        isGmsReady,
        canRequestAds,
        enableLiveAdViewInflation,
        maxCachedSlots,
    ) {
        val initialPhase = when {
            !canRequestAds || !isOnline || !isGmsReady -> BannerAdLoadPhase.Collapsed
            !enableLiveAdViewInflation -> BannerAdLoadPhase.StaticPreview
            else -> BannerAdLoadPhase.Loading
        }
        DiscoveryFeedInlineAdPool(
            initialPhase = initialPhase,
            maxCachedSlots = maxCachedSlots,
        )
    }

    DisposableEffect(pool) {
        onDispose {
            pool.destroyAll()
        }
    }

    return pool
}


internal var testBindPreloadedAdAction: ((Context, Activity, BannerAd) -> AdView?)? = null

internal suspend fun startHolderBannerLoad(
    holder: NovelDetailBannerAdHolder,
    context: Context,
    hostContext: Context,
    availableWidthDp: Int,
    canRequestAds: Boolean = false,
) {
    if (!canRequestAds) {
        return
    }
    val safeWidthDp = availableWidthDp.coerceAtLeast(160)
    if (holder.isDisposed || holder.phase != BannerAdLoadPhase.Loading || holder.loadInProgress || holder.adView != null) {
        return
    }
    holder.loadInProgress = true
    try {
        ServerSupportAdInitializer.ensureInitialized(context)
    } catch (ce: CancellationException) {
        holder.loadInProgress = false
        throw ce
    }

    if (holder.isDisposed || holder.phase != BannerAdLoadPhase.Loading || holder.adView != null) {
        holder.loadInProgress = false
        return
    }

    // Fast path: consume an SDK-preloaded BannerAd from BannerAdPreloader when available so the
    // visible card renders immediately with zero network wait.
    val hostActivity = hostContext.findHostActivity()
    if (hostActivity != null) {
        val preloadedAd = ServerSupportBannerPreloader.pollReadyAd(
            placement = holder.placement,
            requestedWidthDp = safeWidthDp,
            canRequestAds = canRequestAds,
        )
        if (preloadedAd != null) {
            val bound = withContext(Dispatchers.Main.immediate) {
                if (holder.isDisposed || holder.phase != BannerAdLoadPhase.Loading || holder.adView != null) {
                    runCatching { preloadedAd.destroy() }
                    holder.loadInProgress = false
                    return@withContext true
                }
                val bindOverride = testBindPreloadedAdAction
                val preloadedAdView = runCatching {
                    if (bindOverride != null) {
                        bindOverride(hostContext, hostActivity, preloadedAd)
                    } else {
                        AdView(hostContext).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT,
                            )
                            registerBannerAd(preloadedAd, hostActivity)
                        }
                    }
                }.onFailure { t ->
                    runCatching {
                        Log.w(AD_LOG_TAG, "registerBannerAd failed; falling back to live load", t)
                    }
                    runCatching { preloadedAd.destroy() }
                }.getOrNull()

                if (preloadedAdView != null) {
                    holder.loadedWidthDp = safeWidthDp
                    holder.adView = preloadedAdView
                    holder.loadInProgress = false
                    holder.phase = BannerAdLoadPhase.Loaded
                    true
                } else {
                    false
                }
            }
            if (bound) {
                return
            }
        }
    }

    val candidateUnits = ServerSupportAdConstants.candidateBannerAdUnitIds(placement = holder.placement)
    val resolvedAdSize = resolveInlineAdaptiveBannerAdSize(
        availableWidthDp = safeWidthDp,
        placement = holder.placement,
    )

    fun tryLoadUnitAt(unitIndex: Int) {
        if (holder.isDisposed || holder.phase == BannerAdLoadPhase.Loaded) {
            holder.loadInProgress = false
            return
        }
        val adUnit = candidateUnits.getOrNull(unitIndex)
        if (adUnit == null) {
            holder.destroy()
            holder.phase = BannerAdLoadPhase.Failed
            return
        }

        holder.destroy()
        holder.loadInProgress = true
        holder.loadedWidthDp = safeWidthDp
        val request = BannerAdRequest.Builder(adUnit, resolvedAdSize).build()
        val nextAdView = runCatching {
            AdView(hostContext).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                loadAd(
                    request,
                    object : AdLoadCallback<BannerAd> {
                        override fun onAdLoaded(ad: BannerAd) {
                            runOnMainThread {
                                if (holder.isDisposed) {
                                    runCatching { ad.destroy() }
                                    holder.destroy(markDisposed = true)
                                    return@runOnMainThread
                                }
                                holder.loadInProgress = false
                                holder.phase = BannerAdLoadPhase.Loaded
                            }
                        }

                        override fun onAdFailedToLoad(adError: LoadAdError) {
                            runOnMainThread {
                                if (holder.isDisposed) return@runOnMainThread
                                runCatching {
                                    Log.d(
                                        AD_LOG_TAG,
                                        "Banner load failed (${holder.placement}, code=${adError.code}): ${adError.message}",
                                    )
                                }
                                if (unitIndex + 1 < candidateUnits.size) {
                                    tryLoadUnitAt(unitIndex + 1)
                                } else {
                                    holder.destroy()
                                    holder.phase = BannerAdLoadPhase.Failed
                                }
                            }
                        }
                    },
                )
            }
        }.getOrElse {
            holder.loadInProgress = false
            holder.loadedWidthDp = 0
            holder.phase = BannerAdLoadPhase.Failed
            null
        }
        holder.adView = nextAdView
    }

    withContext(Dispatchers.Main.immediate) {
        tryLoadUnitAt(0)
    }
}

/**
 * Policy-compliant detached in-memory prefetch for an upcoming visible banner slot (e.g. when
 * entering `NovelDetailScreen` where the banner sits just below the Synopsis fold).
 *
 * Unlike cold-start hidden `alpha(0f)` view staging, this never attaches an invisible `AdView` to
 * the window hierarchy: AdMob does not record an impression until the `AdView` is actually bound
 * into the visible `ServerSupportBannerAd` card on screen, eliminating blank-card latency when
 * scrolling the slot into view.
 */
@Composable
fun PrefetchBannerAdEffect(
    holder: NovelDetailBannerAdHolder,
    availableWidthDp: Int,
    canRequestAdsOverride: Boolean? = null,
) {
    val context = LocalContext.current
    val hostActivity = remember(context) { context.findHostActivity() }
    val safeWidthDp = availableWidthDp.coerceAtLeast(160)
    val canRequestAds = canRequestAdsOverride ?: LocalAdConsentCanRequestAds.current
    LaunchedEffect(holder, safeWidthDp, canRequestAds) {
        if (!canRequestAds ||
            holder.isDisposed ||
            holder.phase != BannerAdLoadPhase.Loading ||
            holder.adView != null ||
            holder.loadInProgress
        ) {
            return@LaunchedEffect
        }
        startHolderBannerLoad(
            holder = holder,
            context = context,
            hostContext = hostActivity ?: context,
            availableWidthDp = safeWidthDp,
            canRequestAds = canRequestAds,
        )
    }
}

@Composable
fun ServerSupportBannerAd(
    modifier: Modifier = Modifier,
    placement: InlineBannerPlacement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
    isNetworkAvailableOverride: Boolean? = null,
    isGmsAvailableOverride: Boolean? = null,
    canRequestAdsOverride: Boolean? = null,
    enableLiveAdViewInflation: Boolean = true,
) {
    val holder = rememberNovelDetailBannerAdHolder(
        aid = 0,
        placement = placement,
        isNetworkAvailableOverride = isNetworkAvailableOverride,
        isGmsAvailableOverride = isGmsAvailableOverride,
        canRequestAdsOverride = canRequestAdsOverride,
        enableLiveAdViewInflation = enableLiveAdViewInflation,
    )
    ServerSupportBannerAd(
        holder = holder,
        modifier = modifier,
        canRequestAdsOverride = canRequestAdsOverride,
    )
}

/**
 * Policy-compliant, zero-CLS inline adaptive banner card rendered on-demand inside scrollable
 * content. Measures the exact slot width via [BoxWithConstraints] (correctly supporting both phones
 * and two-pane tablets) before requesting the `AdView`.
 */
@Composable
fun ServerSupportBannerAd(
    holder: NovelDetailBannerAdHolder,
    modifier: Modifier = Modifier,
    cardTestTag: String = NovelDetailTestTags.SERVER_SUPPORT_AD_CARD,
    slotTestTag: String = NovelDetailTestTags.SERVER_SUPPORT_AD_SLOT,
    useElevatedTonalContainer: Boolean = false,
    canRequestAdsOverride: Boolean? = null,
) {
    if (!holder.shouldRenderVisibleCard) {
        return
    }

    val context = LocalContext.current
    val hostActivity = remember(context) { context.findHostActivity() }
    val canRequestAds = canRequestAdsOverride ?: LocalAdConsentCanRequestAds.current
    val liveAdView = holder.adView
    val cardShape = MaterialTheme.shapes.medium
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (useElevatedTonalContainer) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        shape = cardShape,
        modifier = modifier
            .fillMaxWidth()
            .testTag(cardTestTag)
            .einkHairlineBorder(cardShape),
    ) {
        val isCompactFeedCard = holder.placement == InlineBannerPlacement.DISCOVERY_FEED_INLINE
        val slotMinHeight = if (holder.phase == BannerAdLoadPhase.Loaded) {
            50.dp
        } else {
            holder.placement.reservedMinHeight
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = if (isCompactFeedCard) 6.dp else 8.dp),
            verticalArrangement = Arrangement.spacedBy(if (isCompactFeedCard) 4.dp else 6.dp),
        ) {
            Text(
                text = stringResource(R.string.detail_ad_support_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = slotMinHeight)
                    .testTag(slotTestTag),
                contentAlignment = Alignment.Center,
            ) {
                val slotWidthDp = maxWidth.value.toInt().coerceAtLeast(160)
                LaunchedEffect(holder, slotWidthDp, canRequestAds) {
                    if (!canRequestAds ||
                        holder.phase == BannerAdLoadPhase.StaticPreview ||
                        holder.phase == BannerAdLoadPhase.Collapsed ||
                        holder.phase == BannerAdLoadPhase.Failed
                    ) {
                        return@LaunchedEffect
                    }
                    val needsWidthReload = holder.adView != null &&
                        holder.loadedWidthDp > 0 &&
                        abs(slotWidthDp - holder.loadedWidthDp) >= 48 &&
                        !holder.loadInProgress
                    if (needsWidthReload) {
                        holder.destroy()
                        holder.phase = BannerAdLoadPhase.Loading
                    }
                    if (holder.phase == BannerAdLoadPhase.Loading && holder.adView == null && !holder.loadInProgress) {
                        withFrameNanos { }
                        startHolderBannerLoad(
                            holder = holder,
                            context = context,
                            hostContext = hostActivity ?: context,
                            availableWidthDp = slotWidthDp,
                            canRequestAds = canRequestAds,
                        )
                    }
                }
                if (liveAdView != null) {
                    key(liveAdView) {
                        AndroidView(
                            factory = {
                                (liveAdView.parent as? ViewGroup)?.removeView(liveAdView)
                                liveAdView
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .wrapContentHeight(),
                        )
                    }
                }
            }
        }
    }
}

internal fun resolveInlineAdaptiveBannerAdSize(
    availableWidthDp: Int,
    placement: InlineBannerPlacement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
): AdSize {
    val safeWidthDp = availableWidthDp.coerceAtLeast(160)
    return AdSize.getInlineAdaptiveBannerAdSize(safeWidthDp, placement.maxHeightDp)
}

internal tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

internal fun hasActiveInternetCapability(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val active = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(active) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private const val AD_LOG_TAG = "ServerSupportAd"

private val mainThreadHandler by lazy { Handler(Looper.getMainLooper()) }

internal inline fun runOnMainThread(crossinline action: () -> Unit) {
    if (Looper.myLooper() == Looper.getMainLooper()) {
        action()
    } else {
        mainThreadHandler.post { action() }
    }
}

internal fun isGooglePlayServicesReady(context: Context): Boolean {
    return org.mewx.wenku8.util.GoogleServicesHelper.isGmsAvailable(context)
}
