package org.mewx.wenku8.reader.engine

import org.mewx.wenku8.reader.model.ReaderColumnMode

/**
 * Vertical foldable hinge bounds in reader-viewport coordinates (after subtracting display cutouts).
 */
data class ReaderVerticalHingeBounds(
    val leftPx: Float,
    val rightPx: Float,
) {
    val widthPx: Float
        get() = (rightPx - leftPx).coerceAtLeast(0f)

    val centerXPx: Float
        get() = (leftPx + rightPx) * 0.5f
}

/**
 * Resolved column geometry for [ReaderLayoutConfig].
 */
data class ResolvedReaderSpreadGeometry(
    val columnCount: Int,
    val columnGapPx: Float,
    val spineCenterXPx: Float,
) {
    val isDualColumn: Boolean
        get() = columnCount >= 2
}

/**
 * Pure, unit-testable large-screen & foldable dual-column eligibility policy (`Issue #204`).
 *
 * Rules:
 * - [ReaderColumnMode.SINGLE]: Always 1 column.
 * - [ReaderColumnMode.AUTO]: Activates 2 columns on tablets in landscape, unfolded book-style
 *   foldables (square-ish aspect ratio or physical vertical hinge), and wide desktop windows,
 *   while strictly keeping regular phones (both portrait AND landscape, where `min(w, h) < 560dp`)
 *   and narrow split-screen panes in single-column mode.
 * - [ReaderColumnMode.DUAL]: Activates 2 columns whenever `viewportWidthDp >= 560dp` (including
 *   tablet portrait or landscape phones when explicitly requested) while protecting portrait phones.
 * - **Legibility guard**: Always falls back to 1 column if each column would be narrower than
 *   `max(220.dp, fontSizePx * 9)` so large font sizes (`32–36 sp`) never crush lines.
 */
fun resolveReaderSpreadGeometry(
    columnMode: ReaderColumnMode,
    viewportWidthPx: Int,
    viewportHeightPx: Int,
    density: Float,
    fontSizePx: Float,
    pageEdgeDistancePx: Float,
    verticalHinge: ReaderVerticalHingeBounds? = null,
): ResolvedReaderSpreadGeometry {
    val fallback = ResolvedReaderSpreadGeometry(
        columnCount = 1,
        columnGapPx = 0f,
        spineCenterXPx = viewportWidthPx * 0.5f,
    )
    if (columnMode == ReaderColumnMode.SINGLE || viewportWidthPx <= 0 || viewportHeightPx <= 0) {
        return fallback
    }

    val safeDensity = density.coerceAtLeast(0.5f)
    val widthDp = viewportWidthPx / safeDensity
    val heightDp = viewportHeightPx / safeDensity
    val minDimDp = minOf(widthDp, heightDp)
    val aspectRatio = if (heightDp > 0f) widthDp / heightDp else 0f

    val usableHinge = verticalHinge?.takeIf { hinge ->
        hinge.leftPx > 0f &&
            hinge.rightPx < viewportWidthPx &&
            hinge.centerXPx in (viewportWidthPx * 0.35f)..(viewportWidthPx * 0.65f)
    }

    val eligibleByViewport = when (columnMode) {
        ReaderColumnMode.SINGLE -> false
        ReaderColumnMode.AUTO -> {
            val isLargeScreen = minDimDp >= AUTO_DUAL_MIN_SHORT_SIDE_DP
            val isBookSpreadShape = widthDp >= heightDp ||
                (widthDp >= AUTO_DUAL_FOLDABLE_MIN_WIDTH_DP && aspectRatio >= AUTO_DUAL_MIN_ASPECT_RATIO)
            val isBookPostureFoldable = usableHinge != null &&
                widthDp >= AUTO_DUAL_MIN_SHORT_SIDE_DP &&
                heightDp >= AUTO_DUAL_HINGE_MIN_HEIGHT_DP
            (isLargeScreen && isBookSpreadShape) || isBookPostureFoldable
        }
        ReaderColumnMode.DUAL -> widthDp >= EXPLICIT_DUAL_MIN_WIDTH_DP
    }

    if (!eligibleByViewport) {
        return fallback
    }

    val edgePx = pageEdgeDistancePx.coerceAtLeast(0f)
    val baseGutterPx = maxOf(DEFAULT_SPINE_GUTTER_DP * safeDensity, edgePx * 2f)
    val spineCenterPx: Float
    val gutterPx: Float
    if (usableHinge != null) {
        spineCenterPx = usableHinge.centerXPx
        gutterPx = maxOf(baseGutterPx, usableHinge.widthPx + HINGE_CLEARANCE_DP * safeDensity)
    } else {
        spineCenterPx = viewportWidthPx * 0.5f
        gutterPx = baseGutterPx
    }

    val leftAvailablePx = (spineCenterPx - gutterPx * 0.5f) - edgePx
    val rightAvailablePx = (viewportWidthPx - edgePx) - (spineCenterPx + gutterPx * 0.5f)
    val columnContentWidthPx = minOf(leftAvailablePx, rightAvailablePx)
    val minComfortableColumnPx = maxOf(
        MIN_COLUMN_WIDTH_DP * safeDensity,
        fontSizePx.coerceAtLeast(8f) * MIN_CHARS_PER_COLUMN,
    )

    if (columnContentWidthPx < minComfortableColumnPx) {
        return fallback
    }

    return ResolvedReaderSpreadGeometry(
        columnCount = 2,
        columnGapPx = gutterPx,
        spineCenterXPx = spineCenterPx,
    )
}

private const val AUTO_DUAL_MIN_SHORT_SIDE_DP = 560f
private const val AUTO_DUAL_FOLDABLE_MIN_WIDTH_DP = 640f
private const val AUTO_DUAL_MIN_ASPECT_RATIO = 0.78f
private const val AUTO_DUAL_HINGE_MIN_HEIGHT_DP = 480f
private const val EXPLICIT_DUAL_MIN_WIDTH_DP = 560f
private const val DEFAULT_SPINE_GUTTER_DP = 36f
private const val HINGE_CLEARANCE_DP = 24f
private const val MIN_COLUMN_WIDTH_DP = 220f
private const val MIN_CHARS_PER_COLUMN = 9f
