package org.mewx.wenku8

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.di.AppContainer

/**
 * Application entry point for Wenku8 `:app-v2`.
 *
 * Implements Coil 3 [SingletonImageLoader.Factory] so every `AsyncImage` checks permanent offline
 * illustrations (`filesDir/saves/imgs/` & `filesDir/imgs/`) before touching OkHttp, initializes
 * `:api`'s protocol version token, and schedules background `v1.31 -> v2.0` internal-storage
 * migration off the main thread on first upgrade launch.
 */
class Wenku8V2Application : Application(), SingletonImageLoader.Factory {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        try {
            Wenku8API.AppVer = BuildConfig.API_PROTOCOL_VERSION
        } catch (_: LinkageError) {
            // Host-JVM Robolectric tests do not load Android Bionic JNI libraries.
        }
        appContainer = AppContainer(this)
        appContainer.triggerAutomaticInternalMigrationIfNeeded()
        appContainer.triggerStartupAccountAndNoticeSync()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return appContainer.buildOfflineFirstImageLoader()
    }
}
