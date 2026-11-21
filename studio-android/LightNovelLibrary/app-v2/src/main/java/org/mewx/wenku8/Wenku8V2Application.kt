package org.mewx.wenku8

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.di.AppContainer
import org.mewx.wenku8.ui.detail.ServerSupportAdInitializer

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
        }
        appContainer = AppContainer(this)
        appContainer.triggerAutomaticInternalMigrationIfNeeded()
        appContainer.triggerStartupAccountAndNoticeSync()
        ServerSupportAdInitializer.warmUpInBackground(this, appContainer.applicationScope)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return appContainer.buildOfflineFirstImageLoader()
    }
}
