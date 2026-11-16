package org.mewx.wenku8.v2.ui.navigation

import androidx.annotation.StringRes
import org.mewx.wenku8.v2.R

/**
 * User-configurable default home destination (`默认启动页` in `我的` -> `常规设置`).
 *
 * Preserves legacy `v1.31` behavior where Wenku8 opens to `最新更新` ([LATEST]) by default, while
 * letting offline-first commuters switch cold-start directly to `本地书架` ([BOOKSHELF]).
 */
enum class DefaultHomeDestination(
    val destination: TopLevelDestination,
    @param:StringRes val optionLabelResId: Int,
) {
    LATEST(
        destination = TopLevelDestination.LATEST,
        optionLabelResId = R.string.setting_default_home_latest,
    ),
    BOOKSHELF(
        destination = TopLevelDestination.BOOKSHELF,
        optionLabelResId = R.string.setting_default_home_bookshelf,
    ),
}

/**
 * Outcome of cold-start home resolution, combining the resolved [TopLevelDestination] with
 * whether the smart offline fallback notice banner should be shown.
 */
data class InitialHomeResolution(
    val initialDestination: TopLevelDestination,
    val triggeredOfflineFallbackToBookshelf: Boolean,
)

/**
 * Resolves which top-level destination should open on cold start (`02-information-architecture-and-navigation.md` §1):
 * - When [preferredHome] is [DefaultHomeDestination.BOOKSHELF], always open [TopLevelDestination.BOOKSHELF].
 * - When [preferredHome] is [DefaultHomeDestination.LATEST] (the default) and [isNetworkAvailable] is `true`,
 *   open [TopLevelDestination.LATEST].
 * - When [preferredHome] is [DefaultHomeDestination.LATEST] and the device has **zero active network**,
 *   automatically fall back to [TopLevelDestination.BOOKSHELF] with `triggeredOfflineFallbackToBookshelf = true`
 *   so commuters on airplanes/subways never stare at an empty network error wall on cold start.
 */
fun resolveInitialHomeDestination(
    preferredHome: DefaultHomeDestination,
    isNetworkAvailable: Boolean,
): InitialHomeResolution {
    if (preferredHome == DefaultHomeDestination.LATEST && !isNetworkAvailable) {
        return InitialHomeResolution(
            initialDestination = TopLevelDestination.BOOKSHELF,
            triggeredOfflineFallbackToBookshelf = true,
        )
    }
    return InitialHomeResolution(
        initialDestination = preferredHome.destination,
        triggeredOfflineFallbackToBookshelf = false,
    )
}
