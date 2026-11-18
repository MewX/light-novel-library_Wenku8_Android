package org.mewx.wenku8.ui.detail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.view.ViewGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.R
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.util.concurrent.atomic.AtomicBoolean

object ServerSupportAdConstants {
    /**
     * Official Google demo Banner Ad Unit ID used only as a fallback in debug builds when the
     * primary unit reports no-fill on an unverified emulator/dev environment.
     */
    const val DEV_TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    /**
     * Production Novel Info inline banner Ad Unit ID matching `com.google.android.gms.ads.APPLICATION_ID`
     * (`ca-app-pub-7333757578973883~9635263460`) and Wenku8 v1 (`org.mewx.wenku8`).
     */
    const val PRODUCTION_BANNER_AD_UNIT_ID = "ca-app-pub-7333757578973883/9658350163"

    /**
     * Ordered Ad Unit IDs to try for a single screen visit. Using [PRODUCTION_BANNER_AD_UNIT_ID]
     * first avoids publisher-ID mismatch (`3940256099942544` vs `7333757578973883`) now that `:app-v2`
     * uses the Play-Store-linked `org.mewx.wenku8` application ID, while still allowing debug
     * emulators to fall back once to [DEV_TEST_BANNER_AD_UNIT_ID].
     */
    fun candidateBannerAdUnitIds(isDebugBuild: Boolean = BuildConfig.DEBUG): List<String> =
        if (isDebugBuild) {
            listOf(PRODUCTION_BANNER_AD_UNIT_ID, DEV_TEST_BANNER_AD_UNIT_ID)
        } else {
            listOf(PRODUCTION_BANNER_AD_UNIT_ID)
        }

    val RESERVED_BANNER_SLOT_HEIGHT = 50.dp
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
 * Screen-scoped holder for `NovelDetailScreen`'s inline banner ad so scrolling the `LazyColumn`
 * past the synopsis and back up never destroys `AdView`, never re-requests an ad, and never
 * flashes an empty box.
 */
@Stable
class NovelDetailBannerAdHolder internal constructor(
    initialPhase: BannerAdLoadPhase,
) {
    var phase by mutableStateOf(initialPhase)
        internal set

    var adView by mutableStateOf<AdView?>(null)
        internal set

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

    internal fun destroy() {
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

    val initialPhase = when {
        !isOnline || !isGmsReady -> BannerAdLoadPhase.Collapsed
        !enableLiveAdViewInflation -> BannerAdLoadPhase.StaticPreview
        else -> BannerAdLoadPhase.Loading
    }

    val holder = remember(aid, isOnline, isGmsReady, enableLiveAdViewInflation) {
        NovelDetailBannerAdHolder(initialPhase = initialPhase)
    }

    DisposableEffect(holder) {
        onDispose {
            holder.destroy()
        }
    }

    val availableWidthDp = (configuration.screenWidthDp - 32).coerceAtLeast(160)

    LaunchedEffect(holder, isOnline, isGmsReady, enableLiveAdViewInflation) {
        if (!isOnline || !isGmsReady || !enableLiveAdViewInflation) {
            return@LaunchedEffect
        }
        if (holder.adView != null || holder.phase != BannerAdLoadPhase.Loading) {
            return@LaunchedEffect
        }

        // Let the first frame of NovelDetailScreen draw smoothly before touching GMS / WebView.
        withFrameNanos { }
        ServerSupportAdInitializer.ensureInitialized(context)

        val candidateUnits = ServerSupportAdConstants.candidateBannerAdUnitIds()
        val hostContext = context.findHostActivity() ?: context
        val resolvedAdSize = resolveBannerAdSize(hostContext, availableWidthDp)

        fun tryLoadUnitAt(unitIndex: Int) {
            if (holder.phase == BannerAdLoadPhase.Loaded) return
            val adUnit = candidateUnits.getOrNull(unitIndex)
            if (adUnit == null) {
                holder.destroy()
                holder.phase = BannerAdLoadPhase.Failed
                return
            }

            // AdView.adUnitId can only be set once per AdView instance, so create a fresh AdView
            // if falling back from production unit to debug demo unit.
            holder.destroy()
            val nextAdView = runCatching {
                AdView(hostContext).apply {
                    setAdSize(resolvedAdSize)
                    adUnitId = adUnit
                    adListener = object : AdListener() {
                        override fun onAdLoaded() {
                            holder.phase = BannerAdLoadPhase.Loaded
                        }

                        override fun onAdFailedToLoad(error: LoadAdError) {
                            if (unitIndex + 1 < candidateUnits.size) {
                                tryLoadUnitAt(unitIndex + 1)
                            } else {
                                holder.destroy()
                                holder.phase = BannerAdLoadPhase.Failed
                            }
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }.getOrElse {
                holder.phase = BannerAdLoadPhase.Failed
                null
            }
            holder.adView = nextAdView
        }

        tryLoadUnitAt(0)
    }

    return holder
}

/**
 * Off-flow `0`-alpha staging host mounted at `NovelDetailScreen` root ONLY while [holder] is in
 * [BannerAdLoadPhase.Loading], giving `AdView` a real window attachment and full width without
 * adding an empty visible frame or extra `LazyColumn` spacing before `onAdLoaded` succeeds.
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = ServerSupportAdConstants.RESERVED_BANNER_SLOT_HEIGHT)
                    .testTag(NovelDetailTestTags.SERVER_SUPPORT_AD_SLOT),
                contentAlignment = Alignment.Center,
            ) {
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
