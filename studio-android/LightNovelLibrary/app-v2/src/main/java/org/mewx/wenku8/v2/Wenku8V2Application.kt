package org.mewx.wenku8.v2

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import org.mewx.wenku8.v2.di.AppContainer

/**
 * Application entry point for Wenku8 `:app-v2`.
 *
 * Implements Coil 3 [SingletonImageLoader.Factory] so every `AsyncImage` checks permanent offline
 * illustrations (`filesDir/saves/imgs/` & `filesDir/imgs/`) before touching OkHttp, and schedules
 * background `v1.31 -> v2.0` internal-storage migration off the main thread on first upgrade launch.
 */
class Wenku8V2Application : Application(), SingletonImageLoader.Factory {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
        appContainer.triggerAutomaticInternalMigrationIfNeeded()
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return appContainer.buildOfflineFirstImageLoader()
    }
}
