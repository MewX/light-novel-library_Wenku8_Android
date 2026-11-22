package org.mewx.wenku8.ui.theme

import android.graphics.Bitmap
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

    private val extractedSeedCacheLock = Any()
    private val extractedSeedCache = object : LinkedHashMap<Int, Color>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Color>?): Boolean {
            return size > 256
        }
    }

    /**
     * Returns a previously extracted cover bitmap seed color for [aid] if available in session
     * memory, falling back to [deterministicSeedColorForAid].
     */
    fun initialSeedColorForAid(aid: Int): Color {
        synchronized(extractedSeedCacheLock) {
            extractedSeedCache[aid]?.let { return it }
        }
        return deterministicSeedColorForAid(aid)
    }

    fun recordExtractedSeedColor(aid: Int, seedColor: Color) {
        if (aid <= 0) return
        synchronized(extractedSeedCacheLock) {
            extractedSeedCache[aid] = seedColor
        }
    }

    /**
     * Deterministic editorial fallback seed color keyed by [aid] before cover artwork finishes
     * decoding (or when offline without a cached cover JPEG).
     */
    fun deterministicSeedColorForAid(aid: Int): Color {
        val safeAid = abs(aid)
        val hueDegrees = ((safeAid * 47) % 360).toFloat()
        return hslToColor(hueDegrees = hueDegrees, saturation = 0.42f, lightness = 0.46f)
    }

    /**
     * Extracts a chromatic seed [Color] from a decoded cover [Bitmap] using a lightweight `16x16`
     * center-weighted sample grid (zero third-party `androidx.palette` dependency).
     *
     * Favors chromatic mid-luminance pixels (`saturation >= 0.14` and `lightness in 0.12..0.88`)
     * so white Bunkobon obi/margins and black typography never wash out the cover's true hue.
     */
    fun extractSeedColorFromBitmap(bitmap: Bitmap): Color {
        if (bitmap.width <= 0 || bitmap.height <= 0 || bitmap.isRecycled) {
            return hslToColor(hueDegrees = 28f, saturation = 0.40f, lightness = 0.45f)
        }

        val needsSoftwareCopy = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            bitmap.config == Bitmap.Config.HARDWARE
        val readableBitmap = if (needsSoftwareCopy) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false)
                ?: return hslToColor(hueDegrees = 28f, saturation = 0.40f, lightness = 0.45f)
        } else {
            bitmap
        }

        try {
            val width = readableBitmap.width
            val height = readableBitmap.height
            val gridSteps = 16
            var weightedRed = 0f
            var weightedGreen = 0f
            var weightedBlue = 0f
            var totalWeight = 0f

            var fallbackRed = 0f
            var fallbackGreen = 0f
            var fallbackBlue = 0f
            var sampleCount = 0

            for (gx in 0 until gridSteps) {
                val x = ((gx + 0.5f) * width / gridSteps).toInt().coerceIn(0, width - 1)
                val centerWeightX = 1f - 0.45f * abs((gx + 0.5f) / gridSteps * 2f - 1f)
                for (gy in 0 until gridSteps) {
                    val y = ((gy + 0.5f) * height / gridSteps).toInt().coerceIn(0, height - 1)
                    val argb = readableBitmap.getPixel(x, y)
                    val alpha = ((argb ushr 24) and 0xFF) / 255f
                    if (alpha < 0.5f) continue

                    val r = ((argb ushr 16) and 0xFF) / 255f
                    val g = ((argb ushr 8) and 0xFF) / 255f
                    val b = (argb and 0xFF) / 255f

                    fallbackRed += r
                    fallbackGreen += g
                    fallbackBlue += b
                    sampleCount++

                    val (_, sat, light) = rgbToHsl(Color(r, g, b, 1f))
                    if (sat >= 0.14f && light in 0.12f..0.88f) {
                        val centerWeightY = 1f - 0.35f * abs((gy + 0.5f) / gridSteps * 2f - 1f)
                        val pixelWeight = sat * centerWeightX * centerWeightY
                        weightedRed += r * pixelWeight
                        weightedGreen += g * pixelWeight
                        weightedBlue += b * pixelWeight
                        totalWeight += pixelWeight
                    }
                }
            }

            return if (totalWeight > 1e-4f) {
                Color(
                    red = (weightedRed / totalWeight).coerceIn(0f, 1f),
                    green = (weightedGreen / totalWeight).coerceIn(0f, 1f),
                    blue = (weightedBlue / totalWeight).coerceIn(0f, 1f),
                    alpha = 1f,
                )
            } else if (sampleCount > 0) {
                Color(
                    red = (fallbackRed / sampleCount).coerceIn(0f, 1f),
                    green = (fallbackGreen / sampleCount).coerceIn(0f, 1f),
                    blue = (fallbackBlue / sampleCount).coerceIn(0f, 1f),
                    alpha = 1f,
                )
            } else {
                hslToColor(hueDegrees = 28f, saturation = 0.40f, lightness = 0.45f)
            }
        } finally {
            if (readableBitmap !== bitmap && !readableBitmap.isRecycled) {
                readableBitmap.recycle()
            }
        }
    }

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
        val rawCompanionHue = (effectiveHueDegrees + 38f) % 360f
        // Keep tertiary accents (used on illustration chapter chips & informational badges) out of
        // the alarm-red warning band (`345°..360°` and `0°..24°`).
        val companionHueDegrees = if (rawCompanionHue >= 345f || rawCompanionHue <= 24f) {
            204f
        } else {
            rawCompanionHue
        }

        return if (!isDarkTheme) {
            val primaryColor = hslToColor(effectiveHueDegrees, clampedSaturation, lightness = 0.30f)
            val primaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.75f, lightness = 0.90f)
            val onPrimaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation, lightness = 0.14f)
            val secondaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.55f, lightness = 0.88f)
            val onSecondaryContainerColor = hslToColor(effectiveHueDegrees, clampedSaturation * 0.75f, lightness = 0.16f)
            val tertiaryColor = hslToColor(companionHueDegrees, clampedSaturation * 0.80f, lightness = 0.30f)
            val tertiaryContainerColor = hslToColor(companionHueDegrees, clampedSaturation * 0.65f, lightness = 0.90f)
            val onTertiaryContainerColor = hslToColor(companionHueDegrees, clampedSaturation * 0.85f, lightness = 0.15f)

            baseColorScheme.copy(
                primary = primaryColor,
                onPrimary = Color.White,
                primaryContainer = primaryContainerColor,
                onPrimaryContainer = onPrimaryContainerColor,
                secondaryContainer = secondaryContainerColor,
                onSecondaryContainer = onSecondaryContainerColor,
                tertiary = tertiaryColor,
                onTertiary = Color.White,
                tertiaryContainer = tertiaryContainerColor,
                onTertiaryContainer = onTertiaryContainerColor,
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
            val tertiaryContainerColor = hslToColor(companionHueDegrees, clampedSaturation * 0.65f, lightness = 0.25f)
            val onTertiaryContainerColor = hslToColor(companionHueDegrees, clampedSaturation * 0.80f, lightness = 0.90f)

            baseColorScheme.copy(
                primary = primaryColor,
                onPrimary = onPrimaryColor,
                primaryContainer = primaryContainerColor,
                onPrimaryContainer = onPrimaryContainerColor,
                secondaryContainer = secondaryContainerColor,
                onSecondaryContainer = onSecondaryContainerColor,
                tertiary = tertiaryColor,
                onTertiary = onTertiaryColor,
                tertiaryContainer = tertiaryContainerColor,
                onTertiaryContainer = onTertiaryContainerColor,
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
