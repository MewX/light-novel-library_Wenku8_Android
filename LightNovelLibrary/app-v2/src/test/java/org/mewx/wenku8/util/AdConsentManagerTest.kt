package org.mewx.wenku8.util

import android.app.Activity
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentRequestParameters
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.ui.detail.ServerSupportAdInitializer
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AdConsentManagerTest {

    private lateinit var appContext: Context
    private lateinit var activity: Activity

    @Before
    fun setUp() {
        appContext = ApplicationProvider.getApplicationContext()
        activity = Robolectric.buildActivity(Activity::class.java).create().get()
        GoogleServicesHelper.resetForTests()
        ServerSupportAdInitializer.resetForTests()
    }

    @After
    fun tearDown() {
        GoogleServicesHelper.resetForTests()
        ServerSupportAdInitializer.resetForTests()
    }

    @Test
    fun eeaFirstLaunch_consentRequiredThenGranted_updatesBothFlows() {
        val fakeClient = FakeUmpConsentClient(
            initialCanRequestAds = false,
            initialPrivacyOptionsRequired = false,
        )
        val manager = UmpAdConsentManager(
            context = appContext,
            isGmsAvailableProvider = { true },
            clientFactory = { fakeClient },
            isDebugBuild = false,
        )

        assertFalse(manager.canRequestAds.value)
        assertFalse(manager.isPrivacyOptionsRequired.value)

        var completionError: String? = "unset"
        manager.gatherConsent(activity) { completionError = it }

        // Pending async UMP network response: still fail-closed
        assertEquals(1, fakeClient.updateRequestCount)
        assertFalse(manager.canRequestAds.value)
        assertFalse(manager.isPrivacyOptionsRequired.value)

        // UMP responds that EEA GDPR consent is required -> loads consent form
        fakeClient.completeUpdateSuccess(
            canRequestAdsAfterUpdate = false,
            privacyOptionsRequiredAfterUpdate = true,
        )
        assertEquals(1, fakeClient.loadAndShowFormCount)
        assertFalse(manager.canRequestAds.value)
        assertTrue(manager.isPrivacyOptionsRequired.value)

        // User submits consent choice -> canRequestAds flips to true, privacy options stay required
        fakeClient.completeConsentForm(
            canRequestAdsAfterDismiss = true,
            privacyOptionsRequiredAfterDismiss = true,
            errorCode = null,
            errorMessage = null,
        )
        assertTrue(manager.canRequestAds.value)
        assertTrue(manager.isPrivacyOptionsRequired.value)
        assertNull(completionError)
    }

    @Test
    fun nonEeaFirstLaunch_notRequired_enablesAdsWithoutPrivacyOptionsButton() {
        val fakeClient = FakeUmpConsentClient(
            initialCanRequestAds = false,
            initialPrivacyOptionsRequired = false,
        )
        val manager = UmpAdConsentManager(
            context = appContext,
            isGmsAvailableProvider = { true },
            clientFactory = { fakeClient },
            isDebugBuild = false,
        )

        manager.gatherConsent(activity)
        fakeClient.completeUpdateSuccess(
            canRequestAdsAfterUpdate = true,
            privacyOptionsRequiredAfterUpdate = false,
        )
        // UMP's loadAndShowConsentFormIfRequired is a silent no-op for NOT_REQUIRED regions
        fakeClient.completeConsentForm(
            canRequestAdsAfterDismiss = true,
            privacyOptionsRequiredAfterDismiss = false,
            errorCode = null,
            errorMessage = null,
        )

        assertTrue(manager.canRequestAds.value)
        assertFalse(
            "Non-EEA users must NOT see the GDPR Privacy Options entry in Settings",
            manager.isPrivacyOptionsRequired.value,
        )
    }

    @Test
    fun returningSession_cachedConsentUnlocksCanRequestAdsImmediatelyBeforeNetworkCallback() {
        val fakeClient = FakeUmpConsentClient(
            initialCanRequestAds = false,
            initialPrivacyOptionsRequired = false,
            cachedCanRequestAdsOnUpdateStart = true,
            cachedPrivacyOptionsRequiredOnUpdateStart = false,
        )
        val manager = UmpAdConsentManager(
            context = appContext,
            isGmsAvailableProvider = { true },
            clientFactory = { fakeClient },
            isDebugBuild = false,
        )

        assertFalse(manager.canRequestAds.value)
        manager.gatherConsent(activity)

        // Immediately after invoking requestConsentInfoUpdate (before async network callback returns),
        // cached previous-session consent must already unlock canRequestAds!
        assertTrue(manager.canRequestAds.value)
        assertFalse(manager.isPrivacyOptionsRequired.value)
        assertEquals(0, fakeClient.loadAndShowFormCount)
    }

    @Test
    fun nonGmsDevice_neverInstantiatesOrInvokesUmpSdk() {
        var factoryCalls = 0
        val manager = UmpAdConsentManager(
            context = appContext,
            isGmsAvailableProvider = { false },
            clientFactory = {
                factoryCalls += 1
                FakeUmpConsentClient()
            },
            isDebugBuild = false,
        )

        var gatherCompleted = false
        manager.gatherConsent(activity) { gatherCompleted = true }
        var privacyDismissed = false
        manager.showPrivacyOptionsForm(activity) { privacyDismissed = true }

        assertEquals(0, factoryCalls)
        assertTrue(gatherCompleted)
        assertTrue(privacyDismissed)
        assertFalse(manager.canRequestAds.value)
        assertFalse(manager.isPrivacyOptionsRequired.value)
    }

    @Test
    fun offlineOrUmpFailure_respectsCachedConsentAndAllowsRetryWhenUncached() {
        // Case 1: Fresh install offline -> stays false gracefully, and retries on next call
        val freshOfflineClient = FakeUmpConsentClient(
            initialCanRequestAds = false,
            cachedCanRequestAdsOnUpdateStart = false,
        )
        val freshManager = UmpAdConsentManager(
            context = appContext,
            isGmsAvailableProvider = { true },
            clientFactory = { freshOfflineClient },
            isDebugBuild = false,
        )

        var errorMsg: String? = null
        freshManager.gatherConsent(activity) { errorMsg = it }
        freshOfflineClient.completeUpdateFailure(errorCode = 2, message = "Network unavailable")
        assertEquals("Network unavailable", errorMsg)
        assertFalse(freshManager.canRequestAds.value)
        assertEquals(0, freshOfflineClient.loadAndShowFormCount)

        // Because the previous update failed, a subsequent gatherConsent call retries cleanly
        freshManager.gatherConsent(activity)
        assertEquals(2, freshOfflineClient.updateRequestCount)

        // Case 2: Returning user offline -> preserves cached previous-session canRequestAds == true
        val cachedOfflineClient = FakeUmpConsentClient(
            initialCanRequestAds = false,
            cachedCanRequestAdsOnUpdateStart = true,
            cachedPrivacyOptionsRequiredOnUpdateStart = true,
        )
        val cachedManager = UmpAdConsentManager(
            context = appContext,
            isGmsAvailableProvider = { true },
            clientFactory = { cachedOfflineClient },
            isDebugBuild = false,
        )
        cachedManager.gatherConsent(activity)
        cachedOfflineClient.completeUpdateFailure(errorCode = 2, message = "Offline")
        assertTrue(cachedManager.canRequestAds.value)
        assertTrue(cachedManager.isPrivacyOptionsRequired.value)
    }

    @Test
    fun privacyOptionsForm_updatesStateReactivelyOnConsentRevocation() {
        val fakeClient = FakeUmpConsentClient(
            initialCanRequestAds = false,
            cachedCanRequestAdsOnUpdateStart = true,
            cachedPrivacyOptionsRequiredOnUpdateStart = true,
        )
        val manager = UmpAdConsentManager(
            context = appContext,
            isGmsAvailableProvider = { true },
            clientFactory = { fakeClient },
            isDebugBuild = false,
        )

        manager.gatherConsent(activity)
        assertTrue(manager.canRequestAds.value)
        assertTrue(manager.isPrivacyOptionsRequired.value)

        // User opens Privacy & Ad Options from Settings and revokes essential consent
        var dismissed = false
        manager.showPrivacyOptionsForm(activity) { dismissed = true }
        assertEquals(1, fakeClient.showPrivacyOptionsCount)

        fakeClient.completePrivacyOptionsForm(
            canRequestAdsAfterDismiss = false,
            privacyOptionsRequiredAfterDismiss = true,
            errorCode = null,
            errorMessage = null,
        )
        assertTrue(dismissed)
        assertFalse(manager.canRequestAds.value)
        assertTrue(manager.isPrivacyOptionsRequired.value)
    }

    @Test
    fun debugGeographyHook_onlyAppliesWhenDebugBuildIsTrue() {
        val releaseClient = FakeUmpConsentClient()
        val releaseManager = UmpAdConsentManager(
            context = appContext,
            isGmsAvailableProvider = { true },
            clientFactory = { releaseClient },
            isDebugBuild = false,
        )
        val debugConfig = AdConsentDebugConfig(
            debugGeography = ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA,
            testDeviceHashedIds = listOf("TEST-HASH-ID"),
            resetConsentBeforeGather = true,
        )
        releaseManager.gatherConsent(activity, debugConfig = debugConfig)
        assertEquals(0, releaseClient.resetCount)
        assertNull(releaseClient.lastParams?.consentDebugSettings)

        val debugClient = FakeUmpConsentClient()
        val debugManager = UmpAdConsentManager(
            context = appContext,
            isGmsAvailableProvider = { true },
            clientFactory = { debugClient },
            isDebugBuild = true,
        )
        debugManager.gatherConsent(activity, debugConfig = debugConfig)
        assertEquals(1, debugClient.resetCount)
        assertNotNull(debugClient.lastParams?.consentDebugSettings)
        assertEquals(
            ConsentDebugSettings.DebugGeography.DEBUG_GEOGRAPHY_EEA,
            debugClient.lastParams?.consentDebugSettings?.debugGeography,
        )
    }

    @Test
    fun consentGatedWarmUp_doesNotInitializeMobileAdsUntilCanRequestAdsBecomesTrueAndRunsOnce() = runBlocking {
        GoogleServicesHelper.setTestAvailabilityOverride { true }
        val initCallCount = AtomicInteger(0)
        ServerSupportAdInitializer.testInitAction = { _, onComplete ->
            initCallCount.incrementAndGet()
            onComplete()
        }

        val fakeClient = FakeUmpConsentClient(initialCanRequestAds = false)
        val manager = UmpAdConsentManager(
            context = appContext,
            isGmsAvailableProvider = { true },
            clientFactory = { fakeClient },
            isDebugBuild = false,
        )

        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            // Mirrors AppContainer.triggerConsentGatedAdWarmUp()
            scope.launch {
                manager.canRequestAds.first { it }
                ServerSupportAdInitializer.warmUpInBackground(appContext, scope)
            }

            delay(100)
            assertEquals(0, initCallCount.get())
            assertFalse(ServerSupportAdInitializer.hasStartedInitialization)

            manager.gatherConsent(activity)
            fakeClient.completeUpdateSuccess(
                canRequestAdsAfterUpdate = true,
                privacyOptionsRequiredAfterUpdate = false,
            )
            fakeClient.completeConsentForm(
                canRequestAdsAfterDismiss = true,
                privacyOptionsRequiredAfterDismiss = false,
                errorCode = null,
                errorMessage = null,
            )

            withTimeout(3_000L) {
                while (initCallCount.get() == 0) {
                    delay(20)
                }
            }
            assertEquals(1, initCallCount.get())
            assertTrue(ServerSupportAdInitializer.hasStartedInitialization)

            // Idempotent: additional warmUp calls do not re-initialize MobileAds
            ServerSupportAdInitializer.warmUpInBackground(appContext, scope)
            delay(100)
            assertEquals(1, initCallCount.get())
        } finally {
            scope.cancel()
        }
    }

    private class FakeUmpConsentClient(
        initialCanRequestAds: Boolean = false,
        initialPrivacyOptionsRequired: Boolean = false,
        private val cachedCanRequestAdsOnUpdateStart: Boolean = initialCanRequestAds,
        private val cachedPrivacyOptionsRequiredOnUpdateStart: Boolean = initialPrivacyOptionsRequired,
    ) : UmpConsentClient {
        private var currentCanRequestAds = initialCanRequestAds
        private var currentPrivacyOptionsRequired = initialPrivacyOptionsRequired

        var updateRequestCount = 0
        var loadAndShowFormCount = 0
        var showPrivacyOptionsCount = 0
        var resetCount = 0
        var lastParams: ConsentRequestParameters? = null

        private var pendingUpdateSuccess: (() -> Unit)? = null
        private var pendingUpdateFailure: ((Int, String) -> Unit)? = null
        private var pendingFormDismissed: ((Int?, String?) -> Unit)? = null
        private var pendingPrivacyDismissed: ((Int?, String?) -> Unit)? = null

        override fun canRequestAds(): Boolean = currentCanRequestAds

        override fun isPrivacyOptionsRequired(): Boolean = currentPrivacyOptionsRequired

        override fun reset() {
            resetCount += 1
            currentCanRequestAds = false
            currentPrivacyOptionsRequired = false
        }

        override fun requestConsentInfoUpdate(
            activity: Activity,
            params: ConsentRequestParameters,
            onSuccess: () -> Unit,
            onFailure: (errorCode: Int, message: String) -> Unit,
        ) {
            updateRequestCount += 1
            lastParams = params
            // Simulate UMP SDK's synchronous previous-session cache restoration
            currentCanRequestAds = cachedCanRequestAdsOnUpdateStart
            currentPrivacyOptionsRequired = cachedPrivacyOptionsRequiredOnUpdateStart
            pendingUpdateSuccess = onSuccess
            pendingUpdateFailure = onFailure
        }

        override fun loadAndShowConsentFormIfRequired(
            activity: Activity,
            onDismissed: (errorCode: Int?, message: String?) -> Unit,
        ) {
            loadAndShowFormCount += 1
            pendingFormDismissed = onDismissed
        }

        override fun showPrivacyOptionsForm(
            activity: Activity,
            onDismissed: (errorCode: Int?, message: String?) -> Unit,
        ) {
            showPrivacyOptionsCount += 1
            pendingPrivacyDismissed = onDismissed
        }

        fun completeUpdateSuccess(
            canRequestAdsAfterUpdate: Boolean,
            privacyOptionsRequiredAfterUpdate: Boolean,
        ) {
            currentCanRequestAds = canRequestAdsAfterUpdate
            currentPrivacyOptionsRequired = privacyOptionsRequiredAfterUpdate
            val cb = pendingUpdateSuccess
            pendingUpdateSuccess = null
            pendingUpdateFailure = null
            cb?.invoke()
        }

        fun completeUpdateFailure(errorCode: Int, message: String) {
            val cb = pendingUpdateFailure
            pendingUpdateSuccess = null
            pendingUpdateFailure = null
            cb?.invoke(errorCode, message)
        }

        fun completeConsentForm(
            canRequestAdsAfterDismiss: Boolean,
            privacyOptionsRequiredAfterDismiss: Boolean,
            errorCode: Int?,
            errorMessage: String?,
        ) {
            currentCanRequestAds = canRequestAdsAfterDismiss
            currentPrivacyOptionsRequired = privacyOptionsRequiredAfterDismiss
            val cb = pendingFormDismissed
            pendingFormDismissed = null
            cb?.invoke(errorCode, errorMessage)
        }

        fun completePrivacyOptionsForm(
            canRequestAdsAfterDismiss: Boolean,
            privacyOptionsRequiredAfterDismiss: Boolean,
            errorCode: Int?,
            errorMessage: String?,
        ) {
            currentCanRequestAds = canRequestAdsAfterDismiss
            currentPrivacyOptionsRequired = privacyOptionsRequiredAfterDismiss
            val cb = pendingPrivacyDismissed
            pendingPrivacyDismissed = null
            cb?.invoke(errorCode, errorMessage)
        }
    }
}
