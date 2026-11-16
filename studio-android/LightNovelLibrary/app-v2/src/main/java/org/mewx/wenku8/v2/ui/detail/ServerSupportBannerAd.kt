package org.mewx.wenku8.v2.ui.detail

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mewx.wenku8.v2.R
import org.mewx.wenku8.v2.ui.theme.einkHairlineBorder
import java.util.concurrent.atomic.AtomicBoolean

object ServerSupportAdConstants {
    /**
     * Official Google test Banner Ad Unit ID used strictly while `applicationId` is
     * `org.mewx.wenku8.v2dev`. Replace alongside `AndroidManifest.xml`'s `APPLICATION_ID` with
     * `PRODUCTION_BANNER_AD_UNIT_ID` (`ca-app-pub-7333757578973883/2739686526`) at final cutover.
     */
    const val DEV_TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"

    val RESERVED_BANNER_SLOT_HEIGHT = 50.dp
}

private val mobileAdsInitializedOnce = AtomicBoolean(false)

/**
 * Minimal, non-UX-harming server-support inline banner (`05-screen-by-screen-ui-specifications.md` §Screen 5).
 *
 * Strict UX & layout guarantees:
 * 1. **Exclusive Placement**: Rendered ONLY on `NovelDetailScreen` between Synopsis and Volume
 *    Accordions — never inside `UnifiedReaderScreen` or `BookshelfScreen`, and separated from
 *    primary reading actions so accidental taps cannot happen.
 * 2. **Zero Cumulative Layout Shift (`CLS = 0`)**: When online and GMS is available, pre-reserves
 *    exact `50.dp` (`AdSize.BANNER`) slot height from frame 1 so Volume Accordions never jump when
 *    the banner finishes loading.
 * 3. **Instant Offline / GMS-Free Collapse**: Collapses to `0.dp` immediately when offline, on
 *    GMS-free custom ROMs, or if `onAdFailedToLoad` fires (e.g., DNS ad-blocker or no fill).
 * 4. **Post-First-Frame Lazy Init**: Waits for `withFrameNanos {}` before calling
 *    `MobileAds.initialize` off the main thread so `NovelDetailScreen` transition stays 120Hz.
 */
@Composable
fun ServerSupportBannerAd(
    modifier: Modifier = Modifier,
    isNetworkAvailableOverride: Boolean? = null,
    isGmsAvailableOverride: Boolean? = null,
    enableLiveAdViewInflation: Boolean = true,
) {
    val context = LocalContext.current
    val isOnline = isNetworkAvailableOverride ?: remember(context) {
        hasActiveInternetCapability(context)
    }
    val isGmsReady = isGmsAvailableOverride ?: remember(context) {
        isGooglePlayServicesReady(context)
    }

    var adLoadFailed by remember { mutableStateOf(false) }
    var postFirstFrameReady by remember { mutableStateOf(false) }

    val shouldDisplaySlot = isOnline && isGmsReady && !adLoadFailed
    if (!shouldDisplaySlot) {
        return
    }

    LaunchedEffect(enableLiveAdViewInflation) {
        // Yield until the first Compose frame of NovelDetailScreen has rendered on screen.
        withFrameNanos { }
        if (enableLiveAdViewInflation && mobileAdsInitializedOnce.compareAndSet(false, true)) {
            withContext(Dispatchers.IO) {
                runCatching {
                    MobileAds.initialize(context.applicationContext)
                }
            }
        }
        postFirstFrameReady = true
    }

    val createdAdView = remember(context, postFirstFrameReady, enableLiveAdViewInflation) {
        if (!postFirstFrameReady || !enableLiveAdViewInflation) {
            null
        } else {
            runCatching {
                AdView(context).apply {
                    setAdSize(AdSize.BANNER)
                    adUnitId = ServerSupportAdConstants.DEV_TEST_BANNER_AD_UNIT_ID
                    adListener = object : AdListener() {
                        override fun onAdFailedToLoad(error: LoadAdError) {
                            adLoadFailed = true
                        }
                    }
                    loadAd(AdRequest.Builder().build())
                }
            }.onFailure {
                adLoadFailed = true
            }.getOrNull()
        }
    }

    DisposableEffect(createdAdView) {
        onDispose {
            runCatching { createdAdView?.destroy() }
        }
    }

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
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.detail_ad_support_label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(ServerSupportAdConstants.RESERVED_BANNER_SLOT_HEIGHT)
                    .testTag(NovelDetailTestTags.SERVER_SUPPORT_AD_SLOT),
                contentAlignment = Alignment.Center,
            ) {
                if (createdAdView != null) {
                    AndroidView(
                        factory = { createdAdView },
                        modifier = Modifier.height(ServerSupportAdConstants.RESERVED_BANNER_SLOT_HEIGHT),
                    )
                }
            }
        }
    }
}

private fun hasActiveInternetCapability(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val active = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(active) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

private fun isGooglePlayServicesReady(context: Context): Boolean {
    return runCatching {
        GoogleApiAvailabilityLight.getInstance()
            .isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)
}
