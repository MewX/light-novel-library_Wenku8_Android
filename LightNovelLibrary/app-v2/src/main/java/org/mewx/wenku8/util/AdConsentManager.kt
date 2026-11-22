package org.mewx.wenku8.util

import android.app.Activity
import android.content.Context
import android.util.Log
import com.google.android.ump.ConsentDebugSettings
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mewx.wenku8.BuildConfig
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Optional debug-only UMP geography override (strictly ignored when `!BuildConfig.DEBUG`).
 *
 * Left `null` by default so normal development builds behave identically to the device's real
 * geography without unexpectedly presenting EEA dialogs.
 */
data class AdConsentDebugConfig(
    val debugGeography: Int,
    val testDeviceHashedIds: List<String> = emptyList(),
    val resetConsentBeforeGather: Boolean = false,
)

/**
 * Reactive AdMob / GDPR consent coordinator backed by Google's User Messaging Platform (UMP) SDK.
 *
 * Designed to satisfy Google EU User Consent Policy in regulated regions (EEA, UK, Switzerland)
 * while having zero UX impact on users outside regulated regions, offline devices, or non-GMS
 * devices (Huawei HMS, AOSP, E-Ink readers).
 */
interface AdConsentManager {
    /**
     * `true` iff UMP reports `consentInformation.canRequestAds() == true` (consent obtained or not
     * required for the user's region). Defaults to `false` (fail-closed) until UMP confirms.
     */
    val canRequestAds: StateFlow<Boolean>

    /**
     * `true` iff UMP reports [PrivacyOptionsRequirementStatus.REQUIRED], meaning the app MUST
     * expose a Privacy Options entry point in Settings so the user can update or revoke consent.
     * Remains `false` for users outside regulated regions so non-EEA Settings stay uncluttered.
     */
    val isPrivacyOptionsRequired: StateFlow<Boolean>

    /**
     * Non-blocking entry point invoked from `MainActivity.onCreate`.
     *
     * Requests an asynchronous UMP consent info update, immediately mirrors any synchronously
     * restored prior-session cached consent (zero added latency on returning sessions), and shows
     * the consent form only when required for the user's region.
     */
    fun gatherConsent(
        activity: Activity,
        debugConfig: AdConsentDebugConfig? = null,
        onComplete: ((errorMessage: String?) -> Unit)? = null,
    )

    /**
     * Displays the UMP Privacy Options form so users in regulated regions can review or change
     * their ad consent choices at any time from Settings.
     */
    fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: ((errorMessage: String?) -> Unit)? = null,
    )
}

/**
 * Internal seam abstracting Google's static [UserMessagingPlatform] & [ConsentInformation] APIs
 * so JVM / Robolectric unit tests can deterministically exercise every consent state transition.
 */
internal interface UmpConsentClient {
    fun canRequestAds(): Boolean
    fun isPrivacyOptionsRequired(): Boolean
    fun reset()
    fun requestConsentInfoUpdate(
        activity: Activity,
        params: ConsentRequestParameters,
        onSuccess: () -> Unit,
        onFailure: (errorCode: Int, message: String) -> Unit,
    )

    fun loadAndShowConsentFormIfRequired(
        activity: Activity,
        onDismissed: (errorCode: Int?, message: String?) -> Unit,
    )

    fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: (errorCode: Int?, message: String?) -> Unit,
    )
}

private class RealUmpConsentClient(
    appContext: Context,
) : UmpConsentClient {
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(appContext)

    override fun canRequestAds(): Boolean = consentInformation.canRequestAds()

    override fun isPrivacyOptionsRequired(): Boolean =
        consentInformation.privacyOptionsRequirementStatus == PrivacyOptionsRequirementStatus.REQUIRED

    override fun reset() {
        consentInformation.reset()
    }

    override fun requestConsentInfoUpdate(
        activity: Activity,
        params: ConsentRequestParameters,
        onSuccess: () -> Unit,
        onFailure: (errorCode: Int, message: String) -> Unit,
    ) {
        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            { onSuccess() },
            { formError -> onFailure(formError.errorCode, formError.message) },
        )
    }

    override fun loadAndShowConsentFormIfRequired(
        activity: Activity,
        onDismissed: (errorCode: Int?, message: String?) -> Unit,
    ) {
        UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
            onDismissed(formError?.errorCode, formError?.message)
        }
    }

    override fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: (errorCode: Int?, message: String?) -> Unit,
    ) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            onDismissed(formError?.errorCode, formError?.message)
        }
    }
}

/**
 * Production UMP-backed [AdConsentManager].
 *
 * Safety & non-EEA guarantees:
 * - Never touches [UserMessagingPlatform] when [GoogleServicesHelper.isGmsAvailable] is `false`.
 * - Wraps every SDK interaction in `runCatching` so Robolectric, `:api-stub`, and custom ROMs
 *   never crash or show error dialogs.
 * - Immediately reads cached previous-session consent right after calling `requestConsentInfoUpdate`,
 *   matching Google's official quickstart so returning sessions have zero added ad-init delay.
 * - Offline / blocked-network failures fail silently while preserving cached previous-session state.
 */
class UmpAdConsentManager internal constructor(
    context: Context,
    private val isGmsAvailableProvider: (Context) -> Boolean = { GoogleServicesHelper.isGmsAvailable(it) },
    private val clientFactory: (Context) -> UmpConsentClient = { RealUmpConsentClient(it) },
    private val isDebugBuild: Boolean = BuildConfig.DEBUG,
    private val isActivityAliveProvider: (Activity) -> Boolean = { !it.isFinishing && !it.isDestroyed },
) : AdConsentManager {

    constructor(context: Context) : this(
        context = context,
        isGmsAvailableProvider = { GoogleServicesHelper.isGmsAvailable(it) },
        clientFactory = { RealUmpConsentClient(it) },
        isDebugBuild = BuildConfig.DEBUG,
        isActivityAliveProvider = { !it.isFinishing && !it.isDestroyed },
    )

    private val appContext: Context = context.applicationContext ?: context

    private val _canRequestAds = MutableStateFlow(false)
    override val canRequestAds: StateFlow<Boolean> = _canRequestAds.asStateFlow()

    private val _isPrivacyOptionsRequired = MutableStateFlow(false)
    override val isPrivacyOptionsRequired: StateFlow<Boolean> = _isPrivacyOptionsRequired.asStateFlow()

    @Volatile
    private var cachedClient: UmpConsentClient? = null

    private val sessionGatherInFlight = AtomicBoolean(false)
    private val sessionGatherCompleted = AtomicBoolean(false)

    private fun getOrCreateClient(): UmpConsentClient? {
        if (!isGmsAvailableProvider(appContext)) {
            return null
        }
        cachedClient?.let { return it }
        return synchronized(this) {
            cachedClient?.let { return it }
            runCatching { clientFactory(appContext) }
                .onFailure { t ->
                    runCatching { Log.w(TAG, "Failed to initialize UMP ConsentInformation", t) }
                }
                .getOrNull()
                ?.also { cachedClient = it }
        }
    }

    override fun gatherConsent(
        activity: Activity,
        debugConfig: AdConsentDebugConfig?,
        onComplete: ((errorMessage: String?) -> Unit)?,
    ) {
        val client = getOrCreateClient()
        if (client == null) {
            _canRequestAds.value = false
            _isPrivacyOptionsRequired.value = false
            onComplete?.invoke(null)
            return
        }
        if (!isActivityAliveProvider(activity)) {
            onComplete?.invoke(null)
            return
        }

        val effectiveDebugConfig = debugConfig?.takeIf { isDebugBuild }
        val shouldResetForDebug = effectiveDebugConfig?.resetConsentBeforeGather == true
        if (shouldResetForDebug) {
            sessionGatherCompleted.set(false)
            sessionGatherInFlight.set(false)
        }

        if (sessionGatherCompleted.get()) {
            syncStateFromClient(client)
            onComplete?.invoke(null)
            return
        }
        if (!sessionGatherInFlight.compareAndSet(false, true)) {
            return
        }

        runCatching {
            if (shouldResetForDebug) {
                client.reset()
                _canRequestAds.value = false
                _isPrivacyOptionsRequired.value = false
            }

            val params = buildConsentRequestParameters(activity, effectiveDebugConfig)
            client.requestConsentInfoUpdate(
                activity = activity,
                params = params,
                onSuccess = {
                    syncStateFromClient(client)
                    if (!isActivityAliveProvider(activity)) {
                        sessionGatherInFlight.set(false)
                        onComplete?.invoke(null)
                        return@requestConsentInfoUpdate
                    }
                    runCatching {
                        client.loadAndShowConsentFormIfRequired(activity) { errorCode, errorMessage ->
                            syncStateFromClient(client)
                            if (errorCode == null || _canRequestAds.value) {
                                sessionGatherCompleted.set(true)
                            }
                            sessionGatherInFlight.set(false)
                            if (errorCode != null) {
                                runCatching {
                                    Log.d(
                                        TAG,
                                        "UMP consent form dismissed with error($errorCode): $errorMessage",
                                    )
                                }
                            }
                            onComplete?.invoke(errorMessage)
                        }
                    }.onFailure { t ->
                        syncStateFromClient(client)
                        sessionGatherInFlight.set(false)
                        runCatching { Log.w(TAG, "UMP loadAndShowConsentFormIfRequired failed", t) }
                        onComplete?.invoke(t.message)
                    }
                },
                onFailure = { errorCode, message ->
                    // Respect cached prior-session consent when offline or when UMP endpoint is
                    // unreachable (e.g. mainland China), without ever surfacing error dialogs.
                    syncStateFromClient(client)
                    sessionGatherInFlight.set(false)
                    runCatching {
                        Log.d(TAG, "UMP requestConsentInfoUpdate failed($errorCode): $message")
                    }
                    onComplete?.invoke(message)
                },
            )

            // Per official UMP Android quickstart: requestConsentInfoUpdate() synchronously
            // restores cached previous-session consent state before the network callback returns,
            // allowing returning sessions to unlock ad warm-up immediately with zero added latency.
            syncStateFromClient(client)
        }.onFailure { t ->
            syncStateFromClient(client)
            sessionGatherInFlight.set(false)
            runCatching { Log.w(TAG, "UMP gatherConsent failed unexpectedly", t) }
            onComplete?.invoke(t.message)
        }
    }

    override fun showPrivacyOptionsForm(
        activity: Activity,
        onDismissed: ((errorMessage: String?) -> Unit)?,
    ) {
        val client = getOrCreateClient()
        if (client == null || !isActivityAliveProvider(activity)) {
            onDismissed?.invoke(null)
            return
        }
        runCatching {
            client.showPrivacyOptionsForm(activity) { errorCode, errorMessage ->
                syncStateFromClient(client)
                if (errorCode != null) {
                    runCatching {
                        Log.d(
                            TAG,
                            "UMP showPrivacyOptionsForm failed($errorCode): $errorMessage",
                        )
                    }
                }
                onDismissed?.invoke(errorMessage)
            }
        }.onFailure { t ->
            syncStateFromClient(client)
            runCatching { Log.w(TAG, "UMP showPrivacyOptionsForm threw unexpectedly", t) }
            onDismissed?.invoke(t.message)
        }
    }

    private fun buildConsentRequestParameters(
        activity: Activity,
        effectiveDebugConfig: AdConsentDebugConfig?,
    ): ConsentRequestParameters {
        val builder = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
        if (isDebugBuild && effectiveDebugConfig != null) {
            val debugBuilder = ConsentDebugSettings.Builder(activity)
                .setDebugGeography(effectiveDebugConfig.debugGeography)
            effectiveDebugConfig.testDeviceHashedIds
                .filter { it.isNotBlank() }
                .forEach { debugBuilder.addTestDeviceHashedId(it) }
            builder.setConsentDebugSettings(debugBuilder.build())
        }
        return builder.build()
    }

    private fun syncStateFromClient(client: UmpConsentClient) {
        val canRequest = runCatching { client.canRequestAds() }.getOrDefault(false)
        val privacyRequired = runCatching { client.isPrivacyOptionsRequired() }.getOrDefault(false)
        _canRequestAds.value = canRequest
        _isPrivacyOptionsRequired.value = privacyRequired
    }

    companion object {
        private const val TAG = "AdConsentManager"
    }
}
