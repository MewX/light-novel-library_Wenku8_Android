package org.mewx.wenku8.v2.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure-Kotlin perceptual cover accent engine (`CoverThemeEngine`) scoped strictly to `NovelDetailScreen`.
 *
 * Why a custom bounded HSL/luminance generator instead of raw Palette swatch application:
 * Light novel covers frequently feature pure white backgrounds, neon yellow titles, or pitch-black
 * key art. Applying raw dominant cover colors directly to buttons or chips routinely violates WCAG AA
 * (`4.5:1`) contrast. This engine extracts the hue from any cover seed color, clamps saturation to an
 * editorial `[0.22, 0.58]` band, and forces deterministic lightness bands for light vs. dark mode so
 * foreground text contrast is guaranteed `>= 4.5:1` regardless of cover artwork.
 */
object CoverThemeEngine {

    /**
     * Produces a scoped [ColorScheme] overriding primary/secondary/tertiary accent tokens from
     * [coverSeedColor] while preserving the base theme's archival paper or carbon night surfaces.
     *
     * In E-Ink mode ([isEinkMode] = `true`), cover tinting is bypassed completely so crisp
     * monochrome borders remain untouched.
     */
    fun createScopedCoverColorScheme(
        baseColorScheme: ColorScheme,
        coverSeedColor: Color,
        isDarkTheme: Boolean,
        isEinkMode: Boolean,
    ): ColorScheme {
        if (isEinkMode) {
            return Wenku8ColorSchemes.EinkMonochromeColorScheme
        }

        val (rawHueDegrees, rawSaturation, _) = rgbToHsl(coverSeedColor)
        // Achromatic covers (pure white/grey/black sketches) fall back gracefully to warm bronze hue.
        val effectiveHueDegrees = if (rawSaturation < 0.08f) 28f else rawHueDegrees
        val clampedSaturation = rawSaturation.coerceIn(0.24f, 0.56f)
        val companionHueDegrees = (effectiveHueDegrees + 38f) % 360f

        return if (!isDarkTheme) {
            val primaryColor = hslToColor(effectiveHueDegrees, clampedSaturation, lightness = 0.30f)
            val primaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.75f, lightness = 0.90f)
            val onPrimaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation, lightness = 0.14f)
            val secondaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.55f, lightness = 0.88f)
            val onSecondaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.75f, lightness = 0.16f)
            val tertiaryColor = hslToColor(companionHueDegrees, clampedSaturation * 0.80f, lightness = 0.30f)

            baseColorScheme.copy(
                primary = primaryColor,
                onPrimary = Color.White,
                primaryContainer = primaryContainerColor,
                onPrimaryContainer = onPrimaryContainerColor,
                secondaryContainer = secondaryContainerColor,
                onSecondaryContainer = onSecondaryContainerColor,
                tertiary = tertiaryColor,
                onTertiary = Color.White,
            )
        } else {
            val primaryColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.85f, lightness = 0.76f)
            val onPrimaryColor = hslToColor(effectiveHueDegrees, clampedSaturation, lightness = 0.15f)
            val primaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.75f, lightness = 0.25f)
            val onPrimaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.85f, lightness = 0.90f)
            val secondaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.60f, lightness = 0.24f)
            val onSecondaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.70f, lightness = 0.89f)
            val tertiaryColor = hslToColor(companionHueDegrees, clampedSaturation * 0.75f, lightness = 0.76f)
            val onTertiaryColor = hslToColor(companionHueDegrees, clampedSaturation, lightness = 0.15f)

            baseColorScheme.copy(
                primary = primaryColor,
                onPrimary = onPrimaryColor,
                primaryContainer = primaryContainerColor,
                onPrimaryContainer = onPrimaryContainerColor,
                secondaryContainer = secondaryContainerColor,
                onSecondaryContainer = onSecondaryContainerColor,
                tertiary = tertiaryColor,
                onTertiary = onTertiaryColor,
            )
        }
    }

    /**
     * Computes WCAG 2.1 relative luminance contrast ratio (`1.0` to `21.0`) between two colors.
     * Exposed so unit tests can assert `>= 4.5` contrast across extreme white/yellow/black seeds.
     */
    fun calculateContrastRatio(foreground: Color, background: Color): Float {
        val foregroundLuminance = foreground.luminance()
        val backgroundLuminance = background.luminance()
        val lighter = max(foregroundLuminance, backgroundLuminance)
        val darker = min(foregroundLuminance, backgroundLuminance)
        return (lighter + 0.05f) / (darker + 0.05f)
    }

    private fun rgbToHsl(color: Color): Triple<Float, Float, Float> {
        val red = color.red
        val green = color.green
        val blue = color.blue
        val maxChannel = max(red, max(green, blue))
        val minChannel = min(red, min(green, blue))
        val channelDelta = maxChannel - minChannel

        val lightness = (maxChannel + minChannel) / 2f
        if (channelDelta < 1e-5f) {
            return Triple(0f, 0f, lightness)
        }

        val saturation = channelDelta / (1f - abs(2f * lightness - 1f)).coerceAtLeast(1e-5f)
        val rawHue = when (maxChannel) {
            red -> 60f * (((green - blue) / channelDelta) % 6f)
            green -> 60f * (((blue - red) / channelDelta) + 2f)
            else -> 60f * (((red - green) / channelDelta) + 4f)
        }
        val normalizedHue = if (rawHue < 0f) rawHue + 360f else rawHue
        return Triple(normalizedHue, saturation.coerceIn(0f, 1f), lightness.coerceIn(0f, 1f))
    }

    private fun hslToColor(hueDegrees: Float, saturation: Float, lightness: Float): Color {
        val safeHue = ((hueDegrees % 360f) + 360f) % 360f
        val safeSaturation = saturation.coerceIn(0f, 1f)
        val safeLightness = lightness.coerceIn(0f, 1f)

        val chroma = (1f - abs(2f * safeLightness - 1f)) * safeSaturation
        val secondaryComponent = chroma * (1f - abs(((safeHue / 60f) % 2f) - 1f))
        val lightnessMatch = safeLightness - chroma / 2f

        val (redPrime, greenPrime, bluePrime) = when {
            safeHue < 60f -> Triple(chroma, secondaryComponent, 0f)
            safeHue < 120f -> Triple(secondaryComponent, chroma, 0f)
            safeHue < 180f -> Triple(0f, chroma, secondaryComponent)
            safeHue < 240f -> Triple(0f, secondaryComponent, chroma)
            safeHue < 300f -> Triple(secondaryComponent, 0f, chroma)
            else -> Triple(chroma, 0f, secondaryComponent)
        }

        return Color(
            red = (redPrime + lightnessMatch).coerceIn(0f, 1f),
            green = (greenPrime + lightnessMatch).coerceIn(0f, 1f),
            blue = (bluePrime + lightnessMatch).coerceIn(0f, 1f),
            alpha = 1f,
        )
    }
}
