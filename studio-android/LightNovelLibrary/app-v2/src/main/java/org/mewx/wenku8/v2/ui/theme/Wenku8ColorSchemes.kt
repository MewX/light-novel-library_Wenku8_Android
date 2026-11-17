package org.mewx.wenku8.v2.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * Hand-crafted Material 3 Expressive color schemes tailored for multi-hour CJK light novel reading
 * (see `docs/designs/v2/01-design-system-and-theming.md`).
 *
 * Design rationale:
 * - Pure `#FFFFFF` backgrounds in standard LCD/OLED day mode cause harsh blue-light glare during
 *   multi-hour reading sessions, so [ArchivalDayColorScheme] uses warm paper ivory (`#F6F5EF`)
 *   paired with Bunkobon bronze-amber (`#7A4F2B`) and bookmark jade (`#3B665E`).
 * - [EinkMonochromeColorScheme] deliberately DOES use pure `#FFFFFF` and `#000000` / `#111111`
 *   to prevent 16-level greyscale dithering shimmer on Carta / Kaleido E-Ink panels.
 */
object Wenku8ColorSchemes {

    /**
     * Default light theme ("经典蓝色"): Faithful to legacy Wenku8 `v1.31`
     * (`myPrimaryColor = #4284F5`, `myPrimaryDarkColor = #3870D0`, `myAccentColor = #FE5151`,
     * `myWindowBackground = #F5F5F5`, `default_white = #FFFFFF`, `default_divider = #DADADA`),
     * paired with Material 3 Expressive blue tonal containers for navigation & filter chips.
     */
    val ClassicBlueColorScheme: ColorScheme = lightColorScheme(
        primary = Color(0xFF4284F5),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFD8E6FF),
        onPrimaryContainer = Color(0xFF0B2960),
        secondary = Color(0xFF3870D0),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFDCE8FC),
        onSecondaryContainer = Color(0xFF102C57),
        tertiary = Color(0xFFE04848),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFFFDAD8),
        onTertiaryContainer = Color(0xFF410005),
        background = Color(0xFFF5F5F5),
        onBackground = Color(0xFF22252A),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF22252A),
        surfaceVariant = Color(0xFFE4E9F2),
        onSurfaceVariant = Color(0xFF44484F),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF8FAFD),
        surfaceContainer = Color(0xFFF0F4FA),
        surfaceContainerHigh = Color(0xFFE8EEF7),
        surfaceContainerHighest = Color(0xFFDFE7F2),
        outline = Color(0xFF747982),
        outlineVariant = Color(0xFFDADDE3),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

    /** Warm Archival Paper (`#F6F5EF`) + Bunkobon Bronze (`#7A4F2B`). */
    val ArchivalDayColorScheme: ColorScheme = lightColorScheme(
        primary = Color(0xFF7A4F2B),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDCC2),
        onPrimaryContainer = Color(0xFF2C1602),
        secondary = Color(0xFF6D5B4C),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF2DFCE),
        onSecondaryContainer = Color(0xFF26190D),
        tertiary = Color(0xFF3B665E),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFBEECE2),
        onTertiaryContainer = Color(0xFF00201B),
        background = Color(0xFFF6F5EF),
        onBackground = Color(0xFF1C1B18),
        surface = Color(0xFFFFFDF9),
        onSurface = Color(0xFF1C1B18),
        surfaceVariant = Color(0xFFE8E2D9),
        onSurfaceVariant = Color(0xFF4B463E),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFF5F3EC),
        surfaceContainer = Color(0xFFECEAE2),
        surfaceContainerHigh = Color(0xFFE6E3DB),
        surfaceContainerHighest = Color(0xFFE0DDD4),
        outline = Color(0xFF7C766D),
        outlineVariant = Color(0xFFCFC6B9),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

    /** Default dark theme: Carbon Night (`#111312`) preventing OLED black-smearing during scroll. */
    val CarbonNightColorScheme: ColorScheme = darkColorScheme(
        primary = Color(0xFFEBB88B),
        onPrimary = Color(0xFF462909),
        primaryContainer = Color(0xFF5F3D1C),
        onPrimaryContainer = Color(0xFFFFDCC2),
        secondary = Color(0xFFDBC2AE),
        onSecondary = Color(0xFF3D2D20),
        secondaryContainer = Color(0xFF5B3B1E),
        onSecondaryContainer = Color(0xFFF8DEC9),
        tertiary = Color(0xFFA2D0C6),
        onTertiary = Color(0xFF063730),
        tertiaryContainer = Color(0xFF224E47),
        onTertiaryContainer = Color(0xFFBEECE2),
        background = Color(0xFF111312),
        onBackground = Color(0xFFE4E2DD),
        surface = Color(0xFF191C1A),
        onSurface = Color(0xFFE4E2DD),
        surfaceVariant = Color(0xFF4B463E),
        onSurfaceVariant = Color(0xFFC9C6BE),
        surfaceContainerLowest = Color(0xFF0C0E0D),
        surfaceContainerLow = Color(0xFF171A18),
        surfaceContainer = Color(0xFF222624),
        surfaceContainerHigh = Color(0xFF2C302E),
        surfaceContainerHighest = Color(0xFF373B39),
        outline = Color(0xFF948F86),
        outlineVariant = Color(0xFF4B463E),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )

    /** Optional True-Black OLED dark theme (`#000000`) with softened `#CFCAC2` ink to curb halation. */
    val PureAmoledColorScheme: ColorScheme = CarbonNightColorScheme.copy(
        background = Color(0xFF000000),
        onBackground = Color(0xFFCFCAC2),
        surface = Color(0xFF000000),
        onSurface = Color(0xFFCFCAC2),
        surfaceContainerLowest = Color(0xFF000000),
        surfaceContainerLow = Color(0xFF0A0C0B),
        surfaceContainer = Color(0xFF141615),
        surfaceContainerHigh = Color(0xFF1D201E),
        surfaceContainerHighest = Color(0xFF262A28),
    )

    /**
     * Dedicated E-Ink / Electronic Paper monochrome palette (Boox / Kindle / Hisense / Meebook).
     * Eliminates mid-tone pastel washes so E-Ink controllers never trigger partial-refresh ghosting.
     */
    val EinkMonochromeColorScheme: ColorScheme = lightColorScheme(
        primary = Color(0xFF000000),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFEAEAEA),
        onPrimaryContainer = Color(0xFF000000),
        secondary = Color(0xFF111111),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFE0E0E0),
        onSecondaryContainer = Color(0xFF000000),
        tertiary = Color(0xFF111111),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFEAEAEA),
        onTertiaryContainer = Color(0xFF000000),
        background = Color(0xFFFFFFFF),
        onBackground = Color(0xFF000000),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF000000),
        surfaceVariant = Color(0xFFF2F2F2),
        onSurfaceVariant = Color(0xFF111111),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceContainerLow = Color(0xFFFFFFFF),
        surfaceContainer = Color(0xFFFFFFFF),
        surfaceContainerHigh = Color(0xFFF5F5F5),
        surfaceContainerHighest = Color(0xFFEBEBEB),
        outline = Color(0xFF111111),
        outlineVariant = Color(0xFF333333),
        error = Color(0xFF000000),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFE5E5E5),
        onErrorContainer = Color(0xFF000000),
    )

    val EinkHairlineBorderColor = Color(0xFF111111)
}
