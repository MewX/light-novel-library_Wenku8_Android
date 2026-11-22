package org.mewx.wenku8

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.di.AppContainer
import org.mewx.wenku8.util.AnalyticsTracker
import org.mewx.wenku8.util.CrashReporter

/**
 * Application entry point for Wenku8 `:app-v2`.
 *
 * Implements Coil 3 [SingletonImageLoader.Factory] so every `AsyncImage` checks permanent offline
 * illustrations (`filesDir/saves/imgs/` & `filesDir/imgs/`) before touching OkHttp, initializes
 * `:api`'s native request-sealing engine, and schedules background `v1.31 -> v2.0` internal-storage
 * migration off the main thread on first upgrade launch.
 */
class Wenku8V2Application : Application(), SingletonImageLoader.Factory {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        CrashReporter.setKey(CrashReporter.Keys.BUILD_FLAVOR, BuildConfig.DISTRIBUTION_CHANNEL)
        CrashReporter.log("Wenku8V2Application#onCreate")
        AnalyticsTracker.initialize(this)

        try {
            Wenku8API.initCrypto(
                this,
                BuildConfig.VERSION_CODE,
                BuildConfig.DISTRIBUTION_CHANNEL,
                BuildConfig.DEBUG,
            )
        } catch (_: LinkageError) {
            // Host-JVM Robolectric tests do not load Android Bionic JNI libraries.
        } catch (_: UnsupportedOperationException) {
            // Open-source :api-stub builds do not include private crypto.
        } catch (t: Throwable) {
            CrashReporter.recordException("Wenku8V2Application.initCrypto", t)
        }
        appContainer = AppContainer(this)
        appContainer.triggerTelemetryStateSync()
        appContainer.triggerAutomaticInternalMigrationIfNeeded()
        appContainer.triggerStartupAccountAndNoticeSync()
        appContainer.triggerConsentGatedAdWarmUp()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return appContainer.buildOfflineFirstImageLoader()
    }
}
