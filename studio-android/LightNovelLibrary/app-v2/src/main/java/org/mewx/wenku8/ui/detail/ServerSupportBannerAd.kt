package org.mewx.wenku8.ui.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.R
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicBoolean

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
     * Production Novel Info inline banner Ad Unit ID matching [PRODUCTION_ADMOB_APP_ID]
     * and Wenku8 v1 (`org.mewx.wenku8`), used exclusively on release builds (`!BuildConfig.DEBUG`).
     */
    const val PRODUCTION_BANNER_AD_UNIT_ID = "ca-app-pub-7333757578973883/9658350163"

    /**
     * Ordered Ad Unit IDs to try:
     * - Debug builds (`isDebugBuild = true`): strictly [DEV_TEST_BANNER_AD_UNIT_ID] only.
     * - Release builds (`isDebugBuild = false`): strictly [PRODUCTION_BANNER_AD_UNIT_ID] only.
     */
    fun candidateBannerAdUnitIds(isDebugBuild: Boolean = BuildConfig.DEBUG): List<String> =
        if (isDebugBuild) {
            listOf(DEV_TEST_BANNER_AD_UNIT_ID)
        } else {
            listOf(PRODUCTION_BANNER_AD_UNIT_ID)
        }

    val RESERVED_BANNER_SLOT_HEIGHT = 50.dp
    internal const val RETRY_AFTER_FAILURE_COOLDOWN_MS = 30_000L
    internal const val COLD_START_PREWARM_DELAY_MS = 350L
}

object ServerSupportAdInitializer {
    private val initStarted = AtomicBoolean(false)
    private val initCompleted = CompletableDeferred<Unit>()

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
        if (initStarted.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) {
                runCatching {
                    MobileAds.initialize(appContext) {
                        initCompleted.complete(Unit)
                    }
                }.onFailure {
                    initCompleted.complete(Unit)
                }
            }
        }
        withTimeoutOrNull(4_000L) {
            initCompleted.await()
        }
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
 * Holder for `NovelDetailScreen`'s inline banner ad. When pooled at the host `Activity` level via
 * [ActivityBannerAdPool], a single prewarmed `AdView` + WebView stays alive across novel visits
 * (`pause()`d off-screen, `resume()`d on `NovelDetailScreen`), rendering in `0ms` on open and
 * surviving `LazyColumn` scrolling without re-requesting or flashing.
 */
@Stable
class NovelDetailBannerAdHolder internal constructor(
    initialPhase: BannerAdLoadPhase,
    internal val ownedByActivityPool: Boolean = false,
) {
    var phase by mutableStateOf(initialPhase)
        internal set

    var adView by mutableStateOf<AdView?>(null)
        internal set

    var activeDetailConsumers by mutableIntStateOf(0)
        internal set

    internal var lastFailureAtMonoMs: Long = 0L
    internal var loadInProgress: Boolean = false
    internal var loadedWidthDp: Int = 0

    val shouldRenderVisibleCard: Boolean
        get() = phase == BannerAdLoadPhase.Loaded || phase == BannerAdLoadPhase.StaticPreview

    val shouldMountHiddenStagingView: Boolean
        get() = phase == BannerAdLoadPhase.Loading && adView != null

    fun markLoadedForTest() {
        phase = BannerAdLoadPhase.Loaded
    }

    fun markFailedForTest() {
        phase = BannerAdLoadPhase.Failed
    }

    internal fun onDetailConsumerAttached() {
        activeDetailConsumers += 1
        runCatching { adView?.resume() }
    }

    internal fun onDetailConsumerDetached() {
        activeDetailConsumers = (activeDetailConsumers - 1).coerceAtLeast(0)
        if (activeDetailConsumers == 0) {
            val current = adView
            if (current != null) {
                runCatching {
                    (current.parent as? ViewGroup)?.removeView(current)
                    current.pause()
                }
            }
        }
    }

    internal fun destroy() {
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

private object ActivityBannerAdPool {
    private val holdersByActivity = WeakHashMap<Activity, NovelDetailBannerAdHolder>()

    @Synchronized
    fun getOrCreateForActivity(
        activity: Activity,
        isOnline: Boolean,
        isGmsReady: Boolean,
    ): NovelDetailBannerAdHolder {
        val existing = holdersByActivity[activity]
        if (existing != null) {
            val nowMono = SystemClock.elapsedRealtime()
            val canRetryAfterFailure = existing.phase == BannerAdLoadPhase.Failed &&
                nowMono - existing.lastFailureAtMonoMs >= ServerSupportAdConstants.RETRY_AFTER_FAILURE_COOLDOWN_MS
            if (isOnline && isGmsReady && (existing.phase == BannerAdLoadPhase.Collapsed || canRetryAfterFailure)) {
                existing.phase = BannerAdLoadPhase.Loading
            }
            return existing
        }

        val initialPhase = if (isOnline && isGmsReady) {
            BannerAdLoadPhase.Loading
        } else {
            BannerAdLoadPhase.Collapsed
        }
        val created = NovelDetailBannerAdHolder(
            initialPhase = initialPhase,
            ownedByActivityPool = true,
        )
        holdersByActivity[activity] = created

        if (activity is ComponentActivity) {
            activity.lifecycle.addObserver(
                object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        synchronized(this@ActivityBannerAdPool) {
                            holdersByActivity.remove(activity)?.destroy()
                        }
                    }
                },
            )
        }
        return created
    }
}

/**
 * Background prewarmer mounted once inside `MainActivity` after initial preferences load: waits
 * for the home screen's first frame + [ServerSupportAdConstants.COLD_START_PREWARM_DELAY_MS] so
 * cold start stays 120Hz, then preloads the single pooled banner `AdView` in the background so
 * opening any `NovelDetailScreen` displays the ad on Frame 1 (`0ms` wait).
 */
@Composable
fun PrewarmServerSupportBannerAdEffect() {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val hostActivity = remember(context) { context.findHostActivity() } ?: return
    val isOnline = remember(context) { hasActiveInternetCapability(context) }
    val isGmsReady = remember(context) { isGooglePlayServicesReady(context) }
    if (!isOnline || !isGmsReady) return

    val holder = remember(hostActivity) {
        ActivityBannerAdPool.getOrCreateForActivity(
            activity = hostActivity,
            isOnline = true,
            isGmsReady = true,
        )
    }
    val availableWidthDp = (configuration.screenWidthDp - 32).coerceAtLeast(160)

    LaunchedEffect(holder, availableWidthDp) {
        if (holder.phase != BannerAdLoadPhase.Loading || holder.loadInProgress || holder.adView != null) {
            return@LaunchedEffect
        }
        withFrameNanos { }
        delay(ServerSupportAdConstants.COLD_START_PREWARM_DELAY_MS)
        startHolderBannerLoad(
            holder = holder,
            context = context,
            hostContext = hostActivity,
            availableWidthDp = availableWidthDp,
        )
    }

    // While prewarming off-detail, provide a 0-alpha window attachment so WebView finishes layout,
    // then immediately detach & pause once `onAdLoaded` transitions phase -> Loaded.
    if (holder.activeDetailConsumers == 0 && holder.shouldMountHiddenStagingView) {
        ServerSupportBannerAdStagingHost(holder = holder)
    }
}

@Composable
fun rememberNovelDetailBannerAdHolder(
    aid: Int,
    isNetworkAvailableOverride: Boolean? = null,
    isGmsAvailableOverride: Boolean? = null,
    enableLiveAdViewInflation: Boolean = true,
): NovelDetailBannerAdHolder {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isOnline = isNetworkAvailableOverride ?: remember(aid, context) {
        hasActiveInternetCapability(context)
    }
    val isGmsReady = isGmsAvailableOverride ?: remember(aid, context) {
        isGooglePlayServicesReady(context)
    }

    val hostActivity = remember(context) { context.findHostActivity() }
    val useActivityPool = enableLiveAdViewInflation &&
        hostActivity != null &&
        isNetworkAvailableOverride == null &&
        isGmsAvailableOverride == null

    val holder = remember(aid, isOnline, isGmsReady, enableLiveAdViewInflation, hostActivity) {
        if (useActivityPool) {
            ActivityBannerAdPool.getOrCreateForActivity(
                activity = hostActivity,
                isOnline = isOnline,
                isGmsReady = isGmsReady,
            )
        } else {
            val initialPhase = when {
                !isOnline || !isGmsReady -> BannerAdLoadPhase.Collapsed
                !enableLiveAdViewInflation -> BannerAdLoadPhase.StaticPreview
                else -> BannerAdLoadPhase.Loading
            }
            NovelDetailBannerAdHolder(
                initialPhase = initialPhase,
                ownedByActivityPool = false,
            )
        }
    }

    DisposableEffect(holder, aid) {
        holder.onDetailConsumerAttached()
        onDispose {
            holder.onDetailConsumerDetached()
            if (!holder.ownedByActivityPool) {
                holder.destroy()
            }
        }
    }

    val availableWidthDp = (configuration.screenWidthDp - 32).coerceAtLeast(160)

    LaunchedEffect(holder, isOnline, isGmsReady, enableLiveAdViewInflation, availableWidthDp) {
        if (!isOnline || !isGmsReady || !enableLiveAdViewInflation) {
            return@LaunchedEffect
        }
        val needsNarrowReload = holder.adView != null &&
            holder.loadedWidthDp >= 320 &&
            availableWidthDp < 320 &&
            !holder.loadInProgress
        if (needsNarrowReload) {
            holder.destroy()
            holder.phase = BannerAdLoadPhase.Loading
        }
        if (holder.phase != BannerAdLoadPhase.Loading || holder.loadInProgress || holder.adView != null) {
            return@LaunchedEffect
        }

        withFrameNanos { }
        startHolderBannerLoad(
            holder = holder,
            context = context,
            hostContext = hostActivity ?: context,
            availableWidthDp = availableWidthDp,
        )
    }

    return holder
}

private suspend fun startHolderBannerLoad(
    holder: NovelDetailBannerAdHolder,
    context: Context,
    hostContext: Context,
    availableWidthDp: Int,
) {
    if (holder.phase != BannerAdLoadPhase.Loading || holder.loadInProgress || holder.adView != null) {
        return
    }
    holder.loadInProgress = true
    ServerSupportAdInitializer.ensureInitialized(context)

    if (holder.phase != BannerAdLoadPhase.Loading || holder.adView != null) {
        holder.loadInProgress = false
        return
    }

    val candidateUnits = ServerSupportAdConstants.candidateBannerAdUnitIds()
    val resolvedAdSize = resolveBannerAdSize(hostContext, availableWidthDp)

    fun tryLoadUnitAt(unitIndex: Int) {
        if (holder.phase == BannerAdLoadPhase.Loaded) {
            holder.loadInProgress = false
            return
        }
        val adUnit = candidateUnits.getOrNull(unitIndex)
        if (adUnit == null) {
            holder.destroy()
            holder.lastFailureAtMonoMs = SystemClock.elapsedRealtime()
            holder.phase = BannerAdLoadPhase.Failed
            return
        }

        holder.destroy()
        holder.loadInProgress = true
        holder.loadedWidthDp = availableWidthDp
        val nextAdView = runCatching {
            AdView(hostContext).apply {
                setAdSize(resolvedAdSize)
                adUnitId = adUnit
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        holder.loadInProgress = false
                        holder.phase = BannerAdLoadPhase.Loaded
                        if (holder.activeDetailConsumers == 0) {
                            runCatching {
                                (parent as? ViewGroup)?.removeView(this@apply)
                                pause()
                            }
                        }
                    }

                    override fun onAdFailedToLoad(error: LoadAdError) {
                        if (unitIndex + 1 < candidateUnits.size) {
                            tryLoadUnitAt(unitIndex + 1)
                        } else {
                            holder.destroy()
                            holder.lastFailureAtMonoMs = SystemClock.elapsedRealtime()
                            holder.phase = BannerAdLoadPhase.Failed
                        }
                    }
                }
                loadAd(AdRequest.Builder().build())
            }
        }.getOrElse {
            holder.loadInProgress = false
            holder.loadedWidthDp = 0
            holder.lastFailureAtMonoMs = SystemClock.elapsedRealtime()
            holder.phase = BannerAdLoadPhase.Failed
            null
        }
        holder.adView = nextAdView
    }

    tryLoadUnitAt(0)
}

/**
 * Off-flow `0`-alpha staging host mounted ONLY while [holder] is in [BannerAdLoadPhase.Loading],
 * giving `AdView` a real window attachment and full width without adding an empty visible frame,
 * intercepting touches, or shifting `LazyColumn` spacing before `onAdLoaded` succeeds.
 */
@Composable
fun ServerSupportBannerAdStagingHost(
    holder: NovelDetailBannerAdHolder,
    modifier: Modifier = Modifier,
) {
    val liveAdView = holder.adView
    if (!holder.shouldMountHiddenStagingView || liveAdView == null) {
        return
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .alpha(0f),
        contentAlignment = Alignment.TopCenter,
    ) {
        AndroidView(
            factory = { ctx ->
                object : android.widget.FrameLayout(ctx) {
                    override fun dispatchTouchEvent(ev: android.view.MotionEvent?): Boolean = false
                }.apply {
                    importantForAccessibility =
                        android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    (liveAdView.parent as? ViewGroup)?.removeView(liveAdView)
                    addView(
                        liveAdView,
                        android.widget.FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT,
                        ),
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
        )
    }
}

@Composable
fun ServerSupportBannerAd(
    modifier: Modifier = Modifier,
    isNetworkAvailableOverride: Boolean? = null,
    isGmsAvailableOverride: Boolean? = null,
    enableLiveAdViewInflation: Boolean = true,
) {
    val holder = rememberNovelDetailBannerAdHolder(
        aid = 0,
        isNetworkAvailableOverride = isNetworkAvailableOverride,
        isGmsAvailableOverride = isGmsAvailableOverride,
        enableLiveAdViewInflation = enableLiveAdViewInflation,
    )
    ServerSupportBannerAdStagingHost(holder = holder)
    ServerSupportBannerAd(
        holder = holder,
        modifier = modifier,
    )
}

/**
 * Minimal, non-UX-harming server-support inline banner (`05-screen-by-screen-ui-specifications.md` §Screen 5).
 */
@Composable
fun ServerSupportBannerAd(
    holder: NovelDetailBannerAdHolder,
    modifier: Modifier = Modifier,
) {
    if (!holder.shouldRenderVisibleCard) {
        return
    }

    val context = LocalContext.current
    val hostActivity = remember(context) { context.findHostActivity() }
    val liveAdView = holder.adView
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = modifier
            .fillMaxWidth()
            .testTag(NovelDetailTestTags.SERVER_SUPPORT_AD_CARD)
            .einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
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
                    .heightIn(min = ServerSupportAdConstants.RESERVED_BANNER_SLOT_HEIGHT)
                    .testTag(NovelDetailTestTags.SERVER_SUPPORT_AD_SLOT),
                contentAlignment = Alignment.Center,
            ) {
                val slotWidthDp = maxWidth.value.toInt()
                LaunchedEffect(holder, slotWidthDp, liveAdView) {
                    if (
                        liveAdView != null &&
                        slotWidthDp in 160 until 320 &&
                        holder.loadedWidthDp >= 320 &&
                        !holder.loadInProgress
                    ) {
                        holder.destroy()
                        holder.phase = BannerAdLoadPhase.Loading
                        startHolderBannerLoad(
                            holder = holder,
                            context = context,
                            hostContext = hostActivity ?: context,
                            availableWidthDp = slotWidthDp,
                        )
                    }
                }
                if (liveAdView != null) {
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

private fun resolveBannerAdSize(context: Context, availableWidthDp: Int): AdSize {
    return if (availableWidthDp >= 320) {
        AdSize.BANNER
    } else {
        AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(context, availableWidthDp)
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
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

internal fun isGooglePlayServicesReady(context: Context): Boolean {
    return runCatching {
        GoogleApiAvailabilityLight.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)
}
