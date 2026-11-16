package org.mewx.wenku8.v2

import android.app.Application
import org.mewx.wenku8.v2.di.AppContainer

/**
 * Application entry point for Wenku8 `:app-v2`.
 *
 * Exposes lazy [appContainer] (`wenku8_v2.db` WAL + `settings.preferences_pb` DataStore) with zero
 * blocking I/O on `onCreate`.
 */
class Wenku8V2Application : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}
