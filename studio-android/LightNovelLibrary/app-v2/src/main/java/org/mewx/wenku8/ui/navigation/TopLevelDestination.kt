package org.mewx.wenku8.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CollectionsBookmark
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CollectionsBookmark
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import org.mewx.wenku8.R

/**
 * The 4 top-level Material 3 Expressive (`M3e`) destinations replacing legacy `v1.31`'s
 * 6-item Hamburger `NavigationDrawerFragment` (`docs/designs/v2/02-information-architecture-and-navigation.md`):
 *
 * 1. [BOOKSHELF] (`书架`) — Offline-first local bookshelf, cloud sync, tags & hero resume card.
 * 2. [LATEST] (`最新`, **Default Startup Tab**) — `最近更新 · 最新入库 · 完结精品` feeds + Search bar.
 * 3. [RANKINGS] (`排行`) — 2D `Metric × Period` leaderboard matrix (`总榜 / 月榜 / 周榜 / 日榜`).
 * 4. [PROFILE] (`我的`) — Account sign-in, daily check-in, Reading Studio, Network Routing & Backups.
 */
enum class TopLevelDestination(
    val routeKey: String,
    @param:StringRes val labelResId: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
) {
    BOOKSHELF(
        routeKey = "bookshelf",
        labelResId = R.string.nav_bookshelf,
        selectedIcon = Icons.Filled.CollectionsBookmark,
        unselectedIcon = Icons.Outlined.CollectionsBookmark,
    ),
    LATEST(
        routeKey = "latest",
        labelResId = R.string.nav_latest,
        selectedIcon = Icons.Filled.AutoAwesome,
        unselectedIcon = Icons.Outlined.AutoAwesome,
    ),
    RANKINGS(
        routeKey = "rankings",
        labelResId = R.string.nav_rankings,
        selectedIcon = Icons.Filled.EmojiEvents,
        unselectedIcon = Icons.Outlined.EmojiEvents,
    ),
    PROFILE(
        routeKey = "profile",
        labelResId = R.string.nav_profile,
        selectedIcon = Icons.Filled.Person,
        unselectedIcon = Icons.Outlined.Person,
    ),
}
