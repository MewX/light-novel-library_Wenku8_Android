package org.mewx.wenku8.reader.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mewx.wenku8.reader.model.ReaderColumnMode

class ReaderSpreadPolicyTest {

    private val density = 2f
    private val normalFontPx = 18f * density
    private val defaultEdgePx = 16f * density

    @Test
    fun phonePortraitAlwaysStaysSingleColumnEvenInDualMode() {
        for (mode in ReaderColumnMode.entries) {
            val result = resolveReaderSpreadGeometry(
                columnMode = mode,
                viewportWidthPx = (412 * density).toInt(),
                viewportHeightPx = (892 * density).toInt(),
                density = density,
                fontSizePx = normalFontPx,
                pageEdgeDistancePx = defaultEdgePx,
            )
            assertEquals("Mode $mode must stay single column on portrait phone", 1, result.columnCount)
            assertFalse(result.isDualColumn)
        }
    }

    @Test
    fun phoneLandscapeStaysSingleInAutoAndAllowsExplicitDual() {
        // Standard modern phone landscape (892x412dp and wide 960x448dp): minDim < 560dp
        for ((wDp, hDp) in listOf(892 to 412, 960 to 448)) {
            val autoResult = resolveReaderSpreadGeometry(
                columnMode = ReaderColumnMode.AUTO,
                viewportWidthPx = (wDp * density).toInt(),
                viewportHeightPx = (hDp * density).toInt(),
                density = density,
                fontSizePx = normalFontPx,
                pageEdgeDistancePx = defaultEdgePx,
            )
            assertEquals("AUTO must NOT enable dual-column on phone landscape ${wDp}x$hDp", 1, autoResult.columnCount)

            val dualResult = resolveReaderSpreadGeometry(
                columnMode = ReaderColumnMode.DUAL,
                viewportWidthPx = (wDp * density).toInt(),
                viewportHeightPx = (hDp * density).toInt(),
                density = density,
                fontSizePx = normalFontPx,
                pageEdgeDistancePx = defaultEdgePx,
            )
            assertEquals("Explicit DUAL should enable dual-column on wide phone landscape ${wDp}x$hDp", 2, dualResult.columnCount)
            assertTrue(dualResult.isDualColumn)
        }
    }

    @Test
    fun narrowSplitScreenPaneStaysSingleColumn() {
        for (mode in ReaderColumnMode.entries) {
            val result = resolveReaderSpreadGeometry(
                columnMode = mode,
                viewportWidthPx = (480 * density).toInt(),
                viewportHeightPx = (800 * density).toInt(),
                density = density,
                fontSizePx = normalFontPx,
                pageEdgeDistancePx = defaultEdgePx,
            )
            assertEquals("Narrow split-screen pane must stay single column in $mode", 1, result.columnCount)
        }
    }

    @Test
    fun tabletPortraitStaysSingleInAutoAndAllowsDualWhenRequested() {
        val autoResult = resolveReaderSpreadGeometry(
            columnMode = ReaderColumnMode.AUTO,
            viewportWidthPx = (800 * density).toInt(),
            viewportHeightPx = (1280 * density).toInt(),
            density = density,
            fontSizePx = normalFontPx,
            pageEdgeDistancePx = defaultEdgePx,
        )
        assertEquals(1, autoResult.columnCount)

        val dualResult = resolveReaderSpreadGeometry(
            columnMode = ReaderColumnMode.DUAL,
            viewportWidthPx = (800 * density).toInt(),
            viewportHeightPx = (1280 * density).toInt(),
            density = density,
            fontSizePx = normalFontPx,
            pageEdgeDistancePx = defaultEdgePx,
        )
        assertEquals(2, dualResult.columnCount)
    }

    @Test
    fun tabletLandscapeActivatesDualColumnInAutoAndRespectsSingleOverride() {
        for ((wDp, hDp) in listOf(960 to 600, 1138 to 712, 1280 to 800)) {
            val autoResult = resolveReaderSpreadGeometry(
                columnMode = ReaderColumnMode.AUTO,
                viewportWidthPx = (wDp * density).toInt(),
                viewportHeightPx = (hDp * density).toInt(),
                density = density,
                fontSizePx = normalFontPx,
                pageEdgeDistancePx = defaultEdgePx,
            )
            assertEquals("AUTO should activate dual column on tablet landscape ${wDp}x$hDp", 2, autoResult.columnCount)
            assertEquals((wDp * density) * 0.5f, autoResult.spineCenterXPx, 0.5f)
            assertTrue(autoResult.columnGapPx >= 36f * density)

            val singleResult = resolveReaderSpreadGeometry(
                columnMode = ReaderColumnMode.SINGLE,
                viewportWidthPx = (wDp * density).toInt(),
                viewportHeightPx = (hDp * density).toInt(),
                density = density,
                fontSizePx = normalFontPx,
                pageEdgeDistancePx = defaultEdgePx,
            )
            assertEquals(1, singleResult.columnCount)
        }
    }

    @Test
    fun unfoldedFoldableActivatesDualColumnAndAlignsToPhysicalVerticalHinge() {
        // Without explicit hinge (e.g. 674x841dp unfolded book-style foldable, aspectRatio ~0.80)
        val withoutHinge = resolveReaderSpreadGeometry(
            columnMode = ReaderColumnMode.AUTO,
            viewportWidthPx = (674 * density).toInt(),
            viewportHeightPx = (841 * density).toInt(),
            density = density,
            fontSizePx = normalFontPx,
            pageEdgeDistancePx = defaultEdgePx,
        )
        assertEquals(2, withoutHinge.columnCount)

        // With physical off-center vertical hinge (40px wide, centered slightly right of midpoint)
        val hinge = ReaderVerticalHingeBounds(
            leftPx = 680f,
            rightPx = 720f,
        )
        val withHinge = resolveReaderSpreadGeometry(
            columnMode = ReaderColumnMode.AUTO,
            viewportWidthPx = (674 * density).toInt(),
            viewportHeightPx = (841 * density).toInt(),
            density = density,
            fontSizePx = normalFontPx,
            pageEdgeDistancePx = defaultEdgePx,
            verticalHinge = hinge,
        )
        assertEquals(2, withHinge.columnCount)
        assertEquals(700f, withHinge.spineCenterXPx, 0.1f)
        assertTrue(
            "Gutter (${withHinge.columnGapPx}) must clear hinge width + 24dp clearance",
            withHinge.columnGapPx >= hinge.widthPx + 24f * density,
        )
    }

    @Test
    fun extremeFontSizeLegibilityGuardFallsBackToSingleColumn() {
        // 674dp unfolded foldable at max 36sp font + 32dp edge margin: each column would be < 36*9 = 324dp
        val result = resolveReaderSpreadGeometry(
            columnMode = ReaderColumnMode.DUAL,
            viewportWidthPx = (674 * density).toInt(),
            viewportHeightPx = (841 * density).toInt(),
            density = density,
            fontSizePx = 36f * density,
            pageEdgeDistancePx = 32f * density,
        )
        assertEquals("Extreme font size must safely fall back to 1 column", 1, result.columnCount)
    }
}
