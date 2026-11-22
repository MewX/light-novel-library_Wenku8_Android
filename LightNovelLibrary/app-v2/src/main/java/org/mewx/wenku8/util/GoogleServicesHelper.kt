package org.mewx.wenku8.util

import android.content.Context
import android.util.Log
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailabilityLight

/**
 * Process-cached Google Play Services (GMS) availability helper for `:app-v2`.
 *
 * Ports `v1.30`'s `GoogleServicesHelper` safety contract: devices without GMS (e.g., AOSP,
 * Huawei HMS, or host-JVM unit tests) resolve to `false` without throwing, and the resolved flag
 * is mirrored into [CrashReporter.Keys.GMS_AVAILABLE] for crash triage.
 */
object GoogleServicesHelper {
    private const val TAG = "GoogleServicesHelper"

    @Volatile
    private var cachedGmsAvailable: Boolean? = null

    @Volatile
    private var testAvailabilityOverride: ((Context) -> Boolean)? = null

    fun isGmsAvailable(context: Context): Boolean {
        cachedGmsAvailable?.let { return it }
        synchronized(this) {
            cachedGmsAvailable?.let { return it }
            val appContext = context.applicationContext ?: context
            val override = testAvailabilityOverride
            val available = if (override != null) {
                override(appContext)
            } else {
                runCatching {
                    val code = GoogleApiAvailabilityLight.getInstance()
                        .isGooglePlayServicesAvailable(appContext)
                    Log.d(TAG, "GMS availability: ${code == ConnectionResult.SUCCESS} (code=$code)")
                    code == ConnectionResult.SUCCESS
                }.getOrDefault(false)
            }
            cachedGmsAvailable = available
            CrashReporter.setKey(CrashReporter.Keys.GMS_AVAILABLE, available)
            return available
        }
    }

    internal fun setTestAvailabilityOverride(override: ((Context) -> Boolean)?) {
        synchronized(this) {
            testAvailabilityOverride = override
            cachedGmsAvailable = null
        }
    }

    internal fun resetForTests() {
        synchronized(this) {
            testAvailabilityOverride = null
            cachedGmsAvailable = null
        }
    }
}
