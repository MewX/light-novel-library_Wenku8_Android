package org.mewx.wenku8.data.update

import android.content.Context
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.data.repository.ServerNoticeAndUpdateRepository

/**
 * Flavor-scoped factory (`default` channel) providing [DirectAppUpdateCoordinator].
 */
object ChannelAppUpdateFactory {
    fun create(
        context: Context,
        serverNoticeAndUpdateRepository: ServerNoticeAndUpdateRepository,
        userPreferencesRepository: UserPreferencesRepository,
    ): AppUpdateCoordinator = DirectAppUpdateCoordinator(
        serverNoticeAndUpdateRepository = serverNoticeAndUpdateRepository,
        userPreferencesRepository = userPreferencesRepository,
    )
}
