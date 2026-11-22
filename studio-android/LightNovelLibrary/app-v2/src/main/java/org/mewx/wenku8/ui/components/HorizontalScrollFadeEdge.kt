package org.mewx.wenku8.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Draws subtle horizontal edge-fade cues (or crisp 1dp hairline indicators in E-Ink mode)
 * whenever a horizontally scrollable strip has off-screen content in the backward/forward direction.
 *
 * When all children fit without scrolling (`!canScrollBackward && !canScrollForward`), this modifier
 * is a zero-draw no-op so normal/wide screens remain visually untouched.
 */
fun Modifier.horizontalScrollFadeEdge(
    scrollState: ScrollState,
    fadeColor: Color,
    fadeWidth: Dp = 20.dp,
    isEink: Boolean = false,
    einkIndicatorColor: Color = Color.Black,
): Modifier = drawWithContent {
    drawContent()
    drawHorizontalScrollAffordance(
        canScrollBackward = scrollState.canScrollBackward,
        canScrollForward = scrollState.canScrollForward,
        fadeColor = fadeColor,
        fadeWidth = fadeWidth,
        isEink = isEink,
        einkIndicatorColor = einkIndicatorColor,
    )
}

/**
 * [LazyListState] overload of [horizontalScrollFadeEdge].
 */
fun Modifier.horizontalScrollFadeEdge(
    listState: LazyListState,
    fadeColor: Color,
    fadeWidth: Dp = 20.dp,
    isEink: Boolean = false,
    einkIndicatorColor: Color = Color.Black,
): Modifier = drawWithContent {
    drawContent()
    drawHorizontalScrollAffordance(
        canScrollBackward = listState.canScrollBackward,
        canScrollForward = listState.canScrollForward,
        fadeColor = fadeColor,
        fadeWidth = fadeWidth,
        isEink = isEink,
        einkIndicatorColor = einkIndicatorColor,
    )
}

private fun ContentDrawScope.drawHorizontalScrollAffordance(
    canScrollBackward: Boolean,
    canScrollForward: Boolean,
    fadeColor: Color,
    fadeWidth: Dp,
    isEink: Boolean,
    einkIndicatorColor: Color,
) {
    if (!canScrollBackward && !canScrollForward) return
    val isRtl = layoutDirection == LayoutDirection.Rtl
    val showLeftEdge = if (isRtl) canScrollForward else canScrollBackward
    val showRightEdge = if (isRtl) canScrollBackward else canScrollForward

    if (isEink) {
        val strokePx = 1.dp.toPx()
        val insetY = (size.height * 0.18f).coerceAtLeast(2.dp.toPx())
        if (showLeftEdge) {
            drawLine(
                color = einkIndicatorColor,
                start = Offset(strokePx * 0.5f, insetY),
                end = Offset(strokePx * 0.5f, size.height - insetY),
                strokeWidth = strokePx,
            )
        }
        if (showRightEdge) {
            drawLine(
                color = einkIndicatorColor,
                start = Offset(size.width - strokePx * 0.5f, insetY),
                end = Offset(size.width - strokePx * 0.5f, size.height - insetY),
                strokeWidth = strokePx,
            )
        }
        return
    }

    val fadePx = fadeWidth.toPx().coerceAtMost(size.width * 0.25f)
    if (fadePx <= 0f) return

    if (showLeftEdge) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(fadeColor, Color.Transparent),
                startX = 0f,
                endX = fadePx,
            ),
            topLeft = Offset.Zero,
            size = Size(fadePx, size.height),
        )
    }
    if (showRightEdge) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, fadeColor),
                startX = size.width - fadePx,
                endX = size.width,
            ),
            topLeft = Offset(size.width - fadePx, 0f),
            size = Size(fadePx, size.height),
        )
    }
}
