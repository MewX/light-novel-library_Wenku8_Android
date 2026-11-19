package org.mewx.wenku8.data.update

import android.content.Context
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.data.repository.ServerNoticeAndUpdateRepository

/**
 * Flavor-scoped factory (`playstore` channel) providing [PlayStoreAppUpdateCoordinator].
 */
object ChannelAppUpdateFactory {
    fun create(
        context: Context,
        @Suppress("UNUSED_PARAMETER")
        serverNoticeAndUpdateRepository: ServerNoticeAndUpdateRepository,
        userPreferencesRepository: UserPreferencesRepository,
    ): AppUpdateCoordinator = PlayStoreAppUpdateCoordinator(
        appUpdateManager = AppUpdateManagerFactory.create(context.applicationContext),
        userPreferencesRepository = userPreferencesRepository,
    )
}
