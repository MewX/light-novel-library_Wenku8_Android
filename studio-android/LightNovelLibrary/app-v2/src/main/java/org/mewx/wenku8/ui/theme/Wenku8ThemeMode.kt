package org.mewx.wenku8.ui.theme

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import org.mewx.wenku8.R

/**
 * Global visual theme presets for Wenku8 v2.0 ("Editorial Literary Sanctuary").
 *
 * Note that [E_INK] can be activated either as a standalone theme preset OR via the legacy
 * v1.31 `eink_mode` setting toggle; in both cases [LocalEinkMode] resolves to `true`, disabling
 * spring animations, translucent blurs, and drop shadows across the entire Compose tree.
 */
enum class Wenku8ThemeMode(@param:StringRes val displayNameResId: Int) {
    CLASSIC_BLUE(R.string.theme_mode_classic_blue),
    SYSTEM(R.string.theme_mode_system),
    ARCHIVAL_DAY(R.string.theme_mode_archival_day),
    CARBON_NIGHT(R.string.theme_mode_carbon_night),
    PURE_AMOLED(R.string.theme_mode_pure_amoled),
    E_INK(R.string.theme_mode_eink),
    ;

    fun isDarkEffective(systemDarkTheme: Boolean): Boolean = when (this) {
        SYSTEM -> systemDarkTheme
        CLASSIC_BLUE, ARCHIVAL_DAY, E_INK -> false
        CARBON_NIGHT, PURE_AMOLED -> true
    }
}

/**
 * CompositionLocal advertising whether E-Ink / Electronic Paper optimizations are active.
 *
 * Downstream components inspect `LocalEinkMode.current` to:
 * 1. Replace filled tonal elevation with crisp `1.dp` `#111111` hairline borders.
 * 2. Replace animated wavy/indeterminate progress indicators with static high-contrast bars.
 * 3. Default the Reader page-turn engine to instant zero-frame-interpolation cuts (`NONE`).
 */
val LocalEinkMode = staticCompositionLocalOf { false }
