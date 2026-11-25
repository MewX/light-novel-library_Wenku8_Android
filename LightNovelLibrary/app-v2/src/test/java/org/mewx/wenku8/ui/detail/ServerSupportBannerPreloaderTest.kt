package org.mewx.wenku8.ui.detail

import android.app.Activity
import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.google.android.libraries.ads.mobile.sdk.banner.AdSize
import com.google.android.libraries.ads.mobile.sdk.banner.AdView
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAd
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdEventCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRefreshCallback
import com.google.android.libraries.ads.mobile.sdk.banner.BannerAdRequest
import com.google.android.libraries.ads.mobile.sdk.common.PreloadCallback
import com.google.android.libraries.ads.mobile.sdk.common.PreloadConfiguration
import com.google.android.libraries.ads.mobile.sdk.common.ResponseInfo
import com.google.android.libraries.ads.mobile.sdk.common.VideoController
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ServerSupportBannerPreloaderTest {

    private lateinit var appContext: Context
    private lateinit var activity: Activity
    private lateinit var fakeClient: FakeBannerPreloaderClient
    private var initCalls = 0
    private var fakeMonoClockMs = 10_000L

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        initCalls = 0
        fakeMonoClockMs = 10_000L
        ServerSupportAdInitializer.resetForTests()
        ServerSupportAdInitializer.testInitAction = { _, onComplete ->
            initCalls += 1
            onComplete()
        }
        ServerSupportBannerPreloader.resetForTests()
        fakeClient = FakeBannerPreloaderClient()
        ServerSupportBannerPreloader.client = fakeClient
        ServerSupportBannerPreloader.isOnlineProvider = { true }
        ServerSupportBannerPreloader.isGmsReadyProvider = { true }
        ServerSupportBannerPreloader.isDebugBuildProvider = { false }
        ServerSupportBannerPreloader.monotonicTimeMsProvider = { fakeMonoClockMs }
        testBindPreloadedAdAction = null
    }

    @After
    fun tearDown() {
        testBindPreloadedAdAction = null
        ServerSupportBannerPreloader.resetForTests()
        ServerSupportAdInitializer.resetForTests()
    }

    @Test
    fun ensurePreloading_strictlyFailClosedWithoutConsentOfflineOrNonGms() = runBlocking {
        // 1. Without consent: zero SDK init, zero preloader starts
        assertFalse(
            ServerSupportBannerPreloader.ensurePreloading(
                context = appContext,
                placement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
                availableWidthDp = 380,
                canRequestAds = false,
            ),
        )
        assertEquals(0, initCalls)
        assertEquals(0, fakeClient.startCalls.size)

        // 2. Offline: zero SDK init, zero preloader starts
        ServerSupportBannerPreloader.isOnlineProvider = { false }
        assertFalse(
            ServerSupportBannerPreloader.ensurePreloading(
                context = appContext,
                placement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
                availableWidthDp = 380,
                canRequestAds = true,
            ),
        )
        assertEquals(0, initCalls)
        assertEquals(0, fakeClient.startCalls.size)

        // 3. Non-GMS device: zero SDK init, zero preloader starts
        ServerSupportBannerPreloader.isOnlineProvider = { true }
        ServerSupportBannerPreloader.isGmsReadyProvider = { false }
        assertFalse(
            ServerSupportBannerPreloader.ensurePreloading(
                context = appContext,
                placement = InlineBannerPlacement.DISCOVERY_FEED_INLINE,
                availableWidthDp = 380,
                canRequestAds = true,
            ),
        )
        assertEquals(0, initCalls)
        assertEquals(0, fakeClient.startCalls.size)
    }

    @Test
    fun ensurePreloading_isIdempotentWithinWidthToleranceAndReconfiguresOnLargeWidthShift() = runBlocking {
        assertTrue(
            ServerSupportBannerPreloader.ensurePreloading(
                context = appContext,
                placement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
                availableWidthDp = 380,
                canRequestAds = true,
            ),
        )
        assertEquals(1, initCalls)
        assertEquals(1, fakeClient.startCalls.size)
        val firstCall = fakeClient.startCalls.single()
        assertEquals(
            ServerSupportBannerPreloader.preloadIdFor(InlineBannerPlacement.NOVEL_DETAIL_STANDARD),
            firstCall.preloadId,
        )
        assertEquals(3, firstCall.config.bufferSize)
        val firstRequest = firstCall.config.request as BannerAdRequest
        assertEquals(ServerSupportAdConstants.PRODUCTION_BANNER_AD_UNIT_ID, firstRequest.adUnitId)
        assertEquals(380, firstRequest.adSize.width)

        // Small width delta (< 48dp): idempotent no-op
        assertTrue(
            ServerSupportBannerPreloader.ensurePreloading(
                context = appContext,
                placement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
                availableWidthDp = 400,
                canRequestAds = true,
            ),
        )
        assertEquals(1, fakeClient.startCalls.size)
        assertEquals(0, fakeClient.destroyedIds.size)

        // Significant width shift (>= 48dp, e.g., rotation / tablet pane): destroys old & restarts
        assertTrue(
            ServerSupportBannerPreloader.ensurePreloading(
                context = appContext,
                placement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
                availableWidthDp = 520,
                canRequestAds = true,
            ),
        )
        assertEquals(1, fakeClient.destroyedIds.size)
        assertEquals(2, fakeClient.startCalls.size)
        val secondRequest = fakeClient.startCalls.last().config.request as BannerAdRequest
        assertEquals(520, secondRequest.adSize.width)
    }

    @Test
    fun pollReadyAd_postReadingLargeStopsPreloaderImmediatelyToPreventCooldownRefill() = runBlocking {
        val placement = InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE
        val preloadId = ServerSupportBannerPreloader.preloadIdFor(placement)
        assertTrue(
            ServerSupportBannerPreloader.ensurePreloading(
                context = appContext,
                placement = placement,
                availableWidthDp = 380,
                canRequestAds = true,
            ),
        )
        val fakeAd = FakeBannerAd(ServerSupportAdConstants.PRODUCTION_POST_READING_LARGE_BANNER_AD_UNIT_ID)
        fakeClient.enqueueAd(preloadId, fakeAd)

        // Mismatched width skips polling stale-width creative
        assertNull(
            ServerSupportBannerPreloader.pollReadyAd(
                placement = placement,
                requestedWidthDp = 700,
                canRequestAds = true,
            ),
        )
        assertTrue(ServerSupportBannerPreloader.isPlacementPreloading(placement))

        // Matching width polls the ad AND tears down the one-shot preloader immediately
        val polled = ServerSupportBannerPreloader.pollReadyAd(
            placement = placement,
            requestedWidthDp = 380,
            canRequestAds = true,
        )
        assertSame(fakeAd, polled)
        assertFalse(ServerSupportBannerPreloader.isPlacementPreloading(placement))
        assertTrue(fakeClient.destroyedIds.contains(preloadId))
        assertEquals(0, ServerSupportBannerPreloader.readyCount(placement))
    }

    @Test
    fun consentRevocation_destroysAllBufferedPreloadersAndRecycledAdsImmediately() = runBlocking {
        ServerSupportBannerPreloader.ensurePreloading(
            context = appContext,
            placement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
            availableWidthDp = 380,
            canRequestAds = true,
        )
        ServerSupportBannerPreloader.ensurePreloading(
            context = appContext,
            placement = InlineBannerPlacement.DISCOVERY_FEED_INLINE,
            availableWidthDp = 380,
            canRequestAds = true,
        )
        val recycledAd = FakeBannerAd(ServerSupportAdConstants.PRODUCTION_BANNER_AD_UNIT_ID)
        ServerSupportBannerPreloader.recycleUnimpressedAd(
            placement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
            widthDp = 380,
            bannerAd = recycledAd,
        )

        assertTrue(ServerSupportBannerPreloader.isPlacementPreloading(InlineBannerPlacement.NOVEL_DETAIL_STANDARD))
        assertTrue(ServerSupportBannerPreloader.isPlacementPreloading(InlineBannerPlacement.DISCOVERY_FEED_INLINE))
        // Total global buffer cap: 3 + 2 + 1 = 6 (matches Next-Gen SDK's 6-ad cap)
        assertEquals(3, ServerSupportBannerPreloader.defaultBufferSizeFor(InlineBannerPlacement.NOVEL_DETAIL_STANDARD))
        assertEquals(2, ServerSupportBannerPreloader.defaultBufferSizeFor(InlineBannerPlacement.DISCOVERY_FEED_INLINE))
        assertEquals(1, ServerSupportBannerPreloader.defaultBufferSizeFor(InlineBannerPlacement.NOVEL_DETAIL_POST_READING_LARGE))
        assertEquals(2, fakeClient.startCalls.last().config.bufferSize)
        assertEquals(1, ServerSupportBannerPreloader.readyCount(InlineBannerPlacement.NOVEL_DETAIL_STANDARD))

        ServerSupportBannerPreloader.onConsentStateChanged(canRequestAds = false)

        assertEquals(1, fakeClient.destroyAllCalls)
        assertTrue(recycledAd.destroyed)
        assertFalse(ServerSupportBannerPreloader.isPlacementPreloading(InlineBannerPlacement.NOVEL_DETAIL_STANDARD))
        assertFalse(ServerSupportBannerPreloader.isPlacementPreloading(InlineBannerPlacement.DISCOVERY_FEED_INLINE))
        assertEquals(0, ServerSupportBannerPreloader.readyCount(InlineBannerPlacement.NOVEL_DETAIL_STANDARD))
    }

    @Test
    fun readyCountFlow_tracksPreloadFillPollExhaustAndRecycledUnimpressedAds() = runBlocking {
        val placement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD
        val preloadId = ServerSupportBannerPreloader.preloadIdFor(placement)
        assertEquals(0, ServerSupportBannerPreloader.readyCountFlow(placement).value)

        ServerSupportBannerPreloader.ensurePreloading(
            context = appContext,
            placement = placement,
            availableWidthDp = 380,
            canRequestAds = true,
        )
        val ad1 = FakeBannerAd(ServerSupportAdConstants.PRODUCTION_BANNER_AD_UNIT_ID)
        val ad2 = FakeBannerAd(ServerSupportAdConstants.PRODUCTION_BANNER_AD_UNIT_ID)
        fakeClient.enqueueAd(preloadId, ad1)
        fakeClient.enqueueAd(preloadId, ad2)
        // Simulate SDK PreloadCallback.onAdPreloaded notification
        fakeClient.notifyPreloaded(preloadId)
        assertEquals(2, ServerSupportBannerPreloader.readyCountFlow(placement).value)
        assertTrue(ServerSupportBannerPreloader.isAdReady(placement, requestedWidthDp = 380))

        // Poll 1 ad -> readyCount drops to 1
        assertSame(ad1, ServerSupportBannerPreloader.pollReadyAd(placement, 380, canRequestAds = true))
        assertEquals(1, ServerSupportBannerPreloader.readyCountFlow(placement).value)

        // Recycle ad1 (unimpressed) -> readyCount returns to 2 and next poll returns ad1 first
        ServerSupportBannerPreloader.recycleUnimpressedAd(placement, 380, ad1)
        assertEquals(2, ServerSupportBannerPreloader.readyCountFlow(placement).value)
        assertSame(ad1, ServerSupportBannerPreloader.pollReadyAd(placement, 380, canRequestAds = true))
        assertEquals(1, ServerSupportBannerPreloader.readyCountFlow(placement).value)

        // Expired recycled ad is destroyed instead of returned
        ServerSupportBannerPreloader.recycleUnimpressedAd(placement, 380, ad1)
        fakeMonoClockMs += ServerSupportBannerPreloader.RECYCLED_AD_TTL_MS + 1_000L
        assertSame(ad2, ServerSupportBannerPreloader.pollReadyAd(placement, 380, canRequestAds = true))
        assertTrue(ad1.destroyed)
        assertEquals(0, ServerSupportBannerPreloader.readyCountFlow(placement).value)
    }

    @Test
    fun startHolderBannerLoad_usesPreloadedBannerImmediatelyWithoutLiveNetworkRequest() = runBlocking {
        val placement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD
        val preloadId = ServerSupportBannerPreloader.preloadIdFor(placement)
        ServerSupportBannerPreloader.ensurePreloading(
            context = appContext,
            placement = placement,
            availableWidthDp = 380,
            canRequestAds = true,
        )
        val fakeBannerAd = FakeBannerAd(ServerSupportAdConstants.PRODUCTION_BANNER_AD_UNIT_ID)
        fakeClient.enqueueAd(preloadId, fakeBannerAd)

        var registeredActivity: Activity? = null
        var registeredAd: BannerAd? = null
        val stubAdView = AdView(activity)
        testBindPreloadedAdAction = { _, act, ad ->
            registeredActivity = act
            registeredAd = ad
            stubAdView
        }

        val holder = NovelDetailBannerAdHolder(
            initialPhase = BannerAdLoadPhase.Loading,
            placement = placement,
        )
        startHolderBannerLoad(
            holder = holder,
            context = appContext,
            hostContext = activity,
            availableWidthDp = 380,
            canRequestAds = true,
        )

        assertEquals(BannerAdLoadPhase.Loaded, holder.phase)
        assertFalse(holder.loadInProgress)
        assertEquals(380, holder.loadedWidthDp)
        assertSame(stubAdView, holder.adView)
        assertSame(activity, registeredActivity)
        assertSame(fakeBannerAd, registeredAd)
        assertNotNull(holder.adView)
        // Standard placement remains active so BannerAdPreloader refills the next visit's slot
        assertTrue(ServerSupportBannerPreloader.isPlacementPreloading(placement))
    }

    @Test
    fun postReadingAdElevationTracker_remainingMsUntilPreloadEligible_tracksDurationAndCooldown() {
        val tracker = PostReadingAdElevationTracker()
        // No active session -> null
        assertNull(tracker.remainingMsUntilPreloadEligible(nowMonoMs = 1_000L))

        // Session started at t = 10_000 -> eligible at t = 10_000 + 10 min
        val start = 10_000L
        tracker.onReaderSessionStarted(aid = 42, nowMonoMs = start)
        assertEquals(
            10L * 60_000L,
            tracker.remainingMsUntilPreloadEligible(nowMonoMs = start),
        )
        assertEquals(
            3L * 60_000L,
            tracker.remainingMsUntilPreloadEligible(nowMonoMs = start + 7L * 60_000L),
        )
        assertEquals(
            0L,
            tracker.remainingMsUntilPreloadEligible(nowMonoMs = start + 10L * 60_000L),
        )

        // End qualifying session -> triggers 30-min cooldown from t = end
        val end = start + 10L * 60_000L
        val token = tracker.onReaderSessionEnded(nowMonoMs = end)
        assertNotNull(token)
        assertNull(tracker.remainingMsUntilPreloadEligible(nowMonoMs = end + 1_000L))

        // New session starts 5 min into the 30-min cooldown:
        // even after 10 min of reading, 15 min of cooldown still remains (total 25 min from start2).
        val start2 = end + 5L * 60_000L
        tracker.onReaderSessionStarted(aid = 42, nowMonoMs = start2)
        assertEquals(
            25L * 60_000L,
            tracker.remainingMsUntilPreloadEligible(nowMonoMs = start2),
        )
        assertEquals(
            15L * 60_000L,
            tracker.remainingMsUntilPreloadEligible(nowMonoMs = start2 + 10L * 60_000L),
        )
        assertEquals(
            0L,
            tracker.remainingMsUntilPreloadEligible(nowMonoMs = end + 30L * 60_000L),
        )
    }

    private data class RecordedStart(
        val preloadId: String,
        val config: PreloadConfiguration,
        val callback: PreloadCallback,
    )

    private class FakeBannerPreloaderClient : BannerPreloaderClient {
        val startCalls = mutableListOf<RecordedStart>()
        val destroyedIds = mutableListOf<String>()
        var destroyAllCalls = 0
        private val queues = mutableMapOf<String, ArrayDeque<BannerAd>>()
        private val callbacks = mutableMapOf<String, PreloadCallback>()

        fun enqueueAd(preloadId: String, ad: BannerAd) {
            queues.getOrPut(preloadId) { ArrayDeque() }.addLast(ad)
        }

        fun notifyPreloaded(preloadId: String) {
            // Trigger ready-count refresh without needing a real ResponseInfo instance
            callbacks[preloadId]?.onAdsExhausted(preloadId)
        }

        override fun start(
            preloadId: String,
            config: PreloadConfiguration,
            callback: PreloadCallback,
        ): Boolean {
            startCalls.add(RecordedStart(preloadId, config, callback))
            callbacks[preloadId] = callback
            return true
        }

        override fun pollAd(preloadId: String): BannerAd? = queues[preloadId]?.removeFirstOrNull()

        override fun isAdAvailable(preloadId: String): Boolean = !queues[preloadId].isNullOrEmpty()

        override fun getNumAdsAvailable(preloadId: String): Int = queues[preloadId]?.size ?: 0

        override fun destroy(preloadId: String): Boolean {
            destroyedIds.add(preloadId)
            queues.remove(preloadId)
            callbacks.remove(preloadId)
            return true
        }

        override fun destroyAll() {
            destroyAllCalls += 1
            queues.clear()
            callbacks.clear()
        }
    }

    private class FakeBannerAd(
        override val adUnitId: String,
    ) : BannerAd {
        var destroyed = false
        override var placementId: Long = 0L
        override var adEventCallback: BannerAdEventCallback? = null
        override var bannerAdRefreshCallback: BannerAdRefreshCallback? = null
        override val videoController: VideoController? = null

        override fun getResponseInfo(): ResponseInfo =
            throw UnsupportedOperationException("Not used in fake")

        override fun destroy() {
            destroyed = true
        }

        @Suppress("OVERRIDE_DEPRECATION")
        override fun getView(activity: Activity): View = View(activity)

        override fun getAdSize(): AdSize = AdSize.getInlineAdaptiveBannerAdSize(380, 120)

        override fun recordManualImpression() = Unit

        override fun isCollapsible(): Boolean = false
    }
}
