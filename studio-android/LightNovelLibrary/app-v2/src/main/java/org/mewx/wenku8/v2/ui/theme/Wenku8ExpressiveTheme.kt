package org.mewx.wenku8.v2.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Zero-duration [MotionScheme] injected into [MaterialExpressiveTheme] whenever E-Ink mode is active.
 *
 * Returning [snap] (`delayMillis = 0`) for every spatial and effects token suppresses spring
 * physics on M3e navigation indicators, dialogs, bottom sheets, and switches at the root level
 * so E-Ink panels perform a single crisp frame update instead of multi-frame ghosting flashes.
 */
object EinkZeroMotionScheme : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
}

/**
 * Root Material 3 Expressive (`M3e`) theme wrapper for Wenku8 `:app-v2`.
 *
 * @param themeMode Selected theme preset (defaults to [Wenku8ThemeMode.CLASSIC_BLUE] matching `v1.31`).
 * @param einkModeOverride Explicit legacy `eink_mode` setting toggle; forces E-Ink monochrome & zero-motion
 *   even when [themeMode] is set to another preset.
 * @param useDynamicColor Optional Android 12+ (`API 31+`) Material You wallpaper color extraction
 *   (disabled by default so Wenku8 launches with its signature Classic Blue identity).
 */
@Composable
fun Wenku8ExpressiveTheme(
    themeMode: Wenku8ThemeMode = Wenku8ThemeMode.CLASSIC_BLUE,
    einkModeOverride: Boolean = false,
    useDynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val isEinkActive = einkModeOverride || themeMode == Wenku8ThemeMode.E_INK
    val systemDarkTheme = isSystemInDarkTheme()
    val isDarkEffective = !isEinkActive && themeMode.isDarkEffective(systemDarkTheme)
    val context = LocalContext.current
    val view = LocalView.current

    val resolvedColorScheme: ColorScheme = when {
        isEinkActive -> Wenku8ColorSchemes.EinkMonochromeColorScheme
        themeMode == Wenku8ThemeMode.ARCHIVAL_DAY -> Wenku8ColorSchemes.ArchivalDayColorScheme
        themeMode == Wenku8ThemeMode.CARBON_NIGHT -> Wenku8ColorSchemes.CarbonNightColorScheme
        themeMode == Wenku8ThemeMode.PURE_AMOLED -> Wenku8ColorSchemes.PureAmoledColorScheme
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (isDarkEffective) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        themeMode == Wenku8ThemeMode.CLASSIC_BLUE -> Wenku8ColorSchemes.ClassicBlueColorScheme
        systemDarkTheme -> Wenku8ColorSchemes.CarbonNightColorScheme
        else -> Wenku8ColorSchemes.ClassicBlueColorScheme
    }

    if (!view.isInEditMode) {
        SideEffect {
            val window = (context as? Activity)?.window ?: return@SideEffect
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.isAppearanceLightStatusBars = !isDarkEffective
            insetsController.isAppearanceLightNavigationBars = !isDarkEffective
        }
    }

    val resolvedShapes = if (isEinkActive) {
        Wenku8TypographyAndShapes.EinkCrispShapes
    } else {
        Wenku8TypographyAndShapes.ExpressiveShapes
    }

    val resolvedMotionScheme = if (isEinkActive) {
        EinkZeroMotionScheme
    } else {
        MotionScheme.expressive()
    }

    CompositionLocalProvider(LocalEinkMode provides isEinkActive) {
        MaterialExpressiveTheme(
            colorScheme = resolvedColorScheme,
            motionScheme = resolvedMotionScheme,
            shapes = resolvedShapes,
            typography = Wenku8TypographyAndShapes.EditorialTypography,
            content = content,
        )
    }
}

/**
 * Applies the mandatory `1.dp` `#111111` border when `LocalEinkMode.current == true` so cards and
 * containers stay sharply delineated without relying on tonal surface fills or drop shadows.
 */
@Composable
fun Modifier.einkHairlineBorder(shape: Shape): Modifier {
    return if (LocalEinkMode.current) {
        this.border(BorderStroke(1.dp, Wenku8ColorSchemes.EinkHairlineBorderColor), shape)
    } else {
        this
    }
}
