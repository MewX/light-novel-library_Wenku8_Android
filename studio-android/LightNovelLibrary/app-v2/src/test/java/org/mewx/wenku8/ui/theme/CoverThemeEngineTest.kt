package org.mewx.wenku8.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverThemeEngineTest {

    private val extremeCoverSeeds = listOf(
        "PureWhiteCover" to Color(0xFFFFFFFF),
        "NeonYellowTitle" to Color(0xFFFFF700),
        "PitchBlackArt" to Color(0xFF000000),
        "SoftPastelPink" to Color(0xFFFFD6E8),
        "DeepCrimson" to Color(0xFFB80F28),
        "AquaSky" to Color(0xFF00E5FF),
    )

    @Test
    fun scopedLightSchemesMaintainWcagAaContrastAcrossExtremeCovers() {
        for ((coverLabel, seedColor) in extremeCoverSeeds) {
            val scheme = CoverThemeEngine.createScopedCoverColorScheme(
                baseColorScheme = Wenku8ColorSchemes.ArchivalDayColorScheme,
                coverSeedColor = seedColor,
                isDarkTheme = false,
                isEinkMode = false,
            )

            val primaryButtonContrast = CoverThemeEngine.calculateContrastRatio(
                foreground = scheme.onPrimary,
                background = scheme.primary,
            )
            val containerChipContrast = CoverThemeEngine.calculateContrastRatio(
                foreground = scheme.onPrimaryContainer,
                background = scheme.primaryContainer,
            )
            val tertiaryChipContrast = CoverThemeEngine.calculateContrastRatio(
                foreground = scheme.onTertiaryContainer,
                background = scheme.tertiaryContainer,
            )

            assertTrue(
                "Expected >= 4.5 primary button contrast for $coverLabel in light mode, got $primaryButtonContrast",
                primaryButtonContrast >= 4.5f,
            )
            assertTrue(
                "Expected >= 4.5 container chip contrast for $coverLabel in light mode, got $containerChipContrast",
                containerChipContrast >= 4.5f,
            )
            assertTrue(
                "Expected >= 4.5 tertiary chip contrast for $coverLabel in light mode, got $tertiaryChipContrast",
                tertiaryChipContrast >= 4.5f,
            )
        }
    }

    @Test
    fun scopedDarkSchemesMaintainWcagAaContrastAcrossExtremeCovers() {
        for ((coverLabel, seedColor) in extremeCoverSeeds) {
            val scheme = CoverThemeEngine.createScopedCoverColorScheme(
                baseColorScheme = Wenku8ColorSchemes.CarbonNightColorScheme,
                coverSeedColor = seedColor,
                isDarkTheme = true,
                isEinkMode = false,
            )

            val primaryButtonContrast = CoverThemeEngine.calculateContrastRatio(
                foreground = scheme.onPrimary,
                background = scheme.primary,
            )
            val containerChipContrast = CoverThemeEngine.calculateContrastRatio(
                foreground = scheme.onPrimaryContainer,
                background = scheme.primaryContainer,
            )
            val tertiaryChipContrast = CoverThemeEngine.calculateContrastRatio(
                foreground = scheme.onTertiaryContainer,
                background = scheme.tertiaryContainer,
            )

            assertTrue(
                "Expected >= 4.5 primary button contrast for $coverLabel in dark mode, got $primaryButtonContrast",
                primaryButtonContrast >= 4.5f,
            )
            assertTrue(
                "Expected >= 4.5 container chip contrast for $coverLabel in dark mode, got $containerChipContrast",
                containerChipContrast >= 4.5f,
            )
            assertTrue(
                "Expected >= 4.5 tertiary chip contrast for $coverLabel in dark mode, got $tertiaryChipContrast",
                tertiaryChipContrast >= 4.5f,
            )
        }
    }

    @Test
    fun einkModeBypassesCoverTintingCompletely() {
        val scheme = CoverThemeEngine.createScopedCoverColorScheme(
            baseColorScheme = Wenku8ColorSchemes.ArchivalDayColorScheme,
            coverSeedColor = Color(0xFFFF0055),
            isDarkTheme = false,
            isEinkMode = true,
        )

        assertEquals(Color.White, scheme.background)
        assertEquals(Color.Black, scheme.primary)
    }

    @Test
    fun classicBlueColorSchemeMatchesLegacyV1PaletteUsesCalmNonRedTertiaryAndPreservesOtherSchemes() {
        val classicBlue = Wenku8ColorSchemes.ClassicBlueColorScheme
        assertEquals(Color(0xFF4284F5), classicBlue.primary)
        assertEquals(Color(0xFF3870D0), classicBlue.secondary)
        assertEquals(Color(0xFFF5F5F5), classicBlue.background)
        assertEquals(Color(0xFFFFFFFF), classicBlue.surface)

        // Tertiary & tertiaryContainer (used for illustration chapters and bookshelf badges)
        // must be calm cool blue/cyan rather than warning-red, with WCAG AA contrast.
        assertTrue(classicBlue.tertiary.blue > classicBlue.tertiary.red)
        assertTrue(classicBlue.tertiaryContainer.blue > classicBlue.tertiaryContainer.red)
        assertTrue(
            CoverThemeEngine.calculateContrastRatio(
                classicBlue.onTertiaryContainer,
                classicBlue.tertiaryContainer,
            ) >= 4.5f,
        )

        // Ensure existing schemes remain untouched.
        assertEquals(Color(0xFF7A4F2B), Wenku8ColorSchemes.ArchivalDayColorScheme.primary)
        assertEquals(Color(0xFFF6F5EF), Wenku8ColorSchemes.ArchivalDayColorScheme.background)
        assertEquals(Color(0xFF111312), Wenku8ColorSchemes.CarbonNightColorScheme.background)
        assertEquals(Color(0xFF000000), Wenku8ColorSchemes.PureAmoledColorScheme.background)
        assertEquals(Color(0xFFFFFFFF), Wenku8ColorSchemes.EinkMonochromeColorScheme.background)

        assertEquals(Wenku8ThemeMode.CLASSIC_BLUE, Wenku8ThemeMode.entries.first())
    }

    @Test
    fun uiTypographyUsesUnifiedSansSerifAndReaderSupportsBuiltinSerifToken() {
        val t = Wenku8TypographyAndShapes.EditorialTypography
        val allUiStyles = listOf(
            t.displayLarge,
            t.displayMedium,
            t.displaySmall,
            t.headlineLarge,
            t.headlineMedium,
            t.headlineSmall,
            t.titleLarge,
            t.titleMedium,
            t.titleSmall,
            t.bodyLarge,
            t.bodyMedium,
            t.bodySmall,
            t.labelLarge,
            t.labelMedium,
            t.labelSmall,
        )
        allUiStyles.forEach { style ->
            assertEquals(androidx.compose.ui.text.font.FontFamily.SansSerif, style.fontFamily)
        }

        val defaultPrefs = org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences()
        assertEquals(false, defaultPrefs.isBuiltinSerifFont)
        assertEquals(false, defaultPrefs.hasCustomFontFile)

        val serifPrefs = defaultPrefs.copy(
            readerCustomFontPath = org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences.BUILTIN_SERIF_FONT_TOKEN,
        )
        assertEquals(true, serifPrefs.isBuiltinSerifFont)
        assertEquals(false, serifPrefs.hasCustomFontFile)

        val customFilePrefs = defaultPrefs.copy(readerCustomFontPath = "/data/user/0/custom/reader_font")
        assertEquals(false, customFilePrefs.isBuiltinSerifFont)
        assertEquals(true, customFilePrefs.hasCustomFontFile)
    }
}
