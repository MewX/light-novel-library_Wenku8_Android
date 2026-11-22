package org.mewx.wenku8.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Unified CJK typography scale and Material 3 Expressive geometry tokens
 * (`docs/designs/v2/01-design-system-and-theming.md` §3 & §4).
 *
 * Typography rule across `:app-v2`:
 * 1. **App UI Shell**: 100% unified **Sans-Serif** ([FontFamily.SansSerif]) across all headings,
 *    dialogs, bottom sheets, cards, stats, body copy, and controls — never mixing Serif and Sans-Serif
 *    in chrome or dialogs.
 * 2. **Technical Telemetry**: `FontFamily.Monospace` strictly for IP addresses, HTTP status codes,
 *    and latency numbers in `NetworkDiagnosticsScreen`.
 * 3. **Reader Content**: User-selectable in Reader Typography Studio between **Sans-Serif** (default),
 *    **Serif**, and **Custom `.ttf`/`.otf` Font**.
 *
 * Uses generous CJK line heights (`1.45x`–`1.65x`) so dense Simplified/Traditional Chinese
 * novel titles, synopses, and review paragraphs breathe comfortably without glyph collisions.
 */
object Wenku8TypographyAndShapes {

    private val UiSansFontFamily = FontFamily.SansSerif

    val EditorialTypography = Typography(
        displayLarge = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 57.sp,
            lineHeight = 64.sp,
        ),
        displayMedium = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp,
        ),
        displaySmall = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp,
        ),
        headlineLarge = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
        ),
        headlineMedium = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
        ),
        headlineSmall = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            lineHeight = 28.sp,
        ),
        titleLarge = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            lineHeight = 26.sp,
        ),
        titleMedium = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            lineHeight = 23.sp,
        ),
        titleSmall = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
        ),
        bodyLarge = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 26.sp,
        ),
        bodyMedium = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 22.sp,
        ),
        bodySmall = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 18.sp,
        ),
        labelLarge = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        ),
        labelMedium = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        ),
        labelSmall = TextStyle(
            fontFamily = UiSansFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 14.sp,
        ),
    )

    /** Standard Material 3 Expressive rounded geometry (`4/8/16/24/28 dp`). */
    val ExpressiveShapes = Shapes(
        extraSmall = RoundedCornerShape(4.dp),
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(16.dp),
        large = RoundedCornerShape(24.dp),
        extraLarge = RoundedCornerShape(28.dp),
    )

    /** Crisp low-radius geometry for E-Ink panels to avoid anti-aliased corner shimmer. */
    val EinkCrispShapes = Shapes(
        extraSmall = RoundedCornerShape(2.dp),
        small = RoundedCornerShape(4.dp),
        medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp),
        extraLarge = RoundedCornerShape(8.dp),
    )
}
