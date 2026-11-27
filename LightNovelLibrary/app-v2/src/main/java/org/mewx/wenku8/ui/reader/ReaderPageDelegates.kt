package org.mewx.wenku8.ui.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Matrix
import android.graphics.Paint as AndroidPaint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.util.LruCache
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import org.mewx.wenku8.R
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.reader.engine.ChapterBlock
import org.mewx.wenku8.reader.engine.ChapterLoadErrorKind
import org.mewx.wenku8.reader.engine.ChapterWindowSlot
import org.mewx.wenku8.reader.engine.ReaderPage
import org.mewx.wenku8.reader.engine.ReaderPageElement
import org.mewx.wenku8.reader.engine.ReaderWindowSnapshot
import org.mewx.wenku8.reader.engine.ReadingAnchor
import org.mewx.wenku8.reader.engine.VirtualPageResolution
import org.mewx.wenku8.reader.model.ReaderPageTurnMode
import org.mewx.wenku8.reader.model.ReaderPaperPreset
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random

/**
 * Injectable wall-clock provider for deterministic unit testing of [rememberSystemClockText].
 */
val LocalReaderClockMillisProvider = staticCompositionLocalOf<() -> Long> {
    { System.currentTimeMillis() }
}

internal fun formatReaderClockMillis(epochMillis: Long): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMillis))

/**
 * Live-updating system clock text (`HH:mm`) that automatically refreshes on
 * [Intent.ACTION_TIME_TICK], [Intent.ACTION_TIME_CHANGED], [Intent.ACTION_TIMEZONE_CHANGED],
 * and [Lifecycle.Event.ON_RESUME] even while the user remains on the same page.
 */
@Composable
fun rememberSystemClockText(): String {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val clockProvider = LocalReaderClockMillisProvider.current
    var clockText by remember(clockProvider) {
        mutableStateOf(formatReaderClockMillis(clockProvider()))
    }

    DisposableEffect(context, lifecycleOwner, clockProvider) {
        val refreshClock = {
            clockText = formatReaderClockMillis(clockProvider())
        }
        refreshClock()

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                refreshClock()
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshClock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    return clockText
}

/**
 * Unified paragraph & character-span highlight state shared across all 6 page-turn modes:
 * - [ttsCid] / [ttsBlockIndex]: active paragraph currently spoken (or paused) in TTS read-aloud.
 * - [focalCid] / [focalBlockIndex] / [focalStartChar] / [focalEndCharExclusive]: matched keyword or
 *   bookmarked anchor highlighted upon jumping from In-Book Search or Bookmarks.
 */
data class ReaderContentHighlight(
    val ttsCid: Int = 0,
    val ttsBlockIndex: Int = -1,
    val focalCid: Int = 0,
    val focalBlockIndex: Int = -1,
    val focalStartChar: Int = -1,
    val focalEndCharExclusive: Int = -1,
)

/**
 * Imperative scroll jump command for `CONTINUOUS_SCROLL` (`上下卷轴`) mode so scrubber drags,
 * in-chapter search jumps, bookmark jumps, undo restores, volume keys, and TTS auto-follow scroll
 * the `LazyColumn` deterministically within the same chapter.
 */
data class ContinuousScrollJumpCommand(
    val revision: Long,
    val cid: Int,
    val paragraphIndex: Int,
    val animated: Boolean = false,
)

/**
 * Pure geometry specification for the drop shadow cast by the sliding upper page onto the revealed
 * lower page in [ReaderPageTurnMode.COVER_SLIDE] (`覆盖翻页`).
 */
data class CoverSlideShadowSpec(
    val startX: Float,
    val widthPx: Float,
    val startAlpha: Float,
)

/**
 * Computes the horizontal drop-shadow placement on the pinned underlying page (`pageOffset` in
 * `(-1f, 0f)`) during a `COVER_SLIDE` transition.
 *
 * When turning forward from Page `k` to Page `k + 1` with left-swipe progress `p` in `(0f, 1f)`:
 * - Page `k` slides left so its right edge sits at `(1f - p) * viewportWidthPx` (moving **right to left**).
 * - Page `k + 1` has `pageOffset = p - 1f` and is pinned at `x = 0f`.
 * - Therefore Page `k`'s right edge in Page `k + 1`'s coordinates is `-pageOffset * viewportWidthPx`,
 *   NOT `(1f + pageOffset) * viewportWidthPx`!
 */
internal fun computeCoverSlideShadowSpec(
    pageOffset: Float,
    viewportWidthPx: Float,
    shadowWidthPx: Float,
): CoverSlideShadowSpec? {
    if (viewportWidthPx <= 0f || shadowWidthPx <= 0f) return null
    if (pageOffset >= -0.001f || pageOffset <= -0.999f) return null
    val upperPageRightEdgeX = (-pageOffset * viewportWidthPx).coerceIn(0f, viewportWidthPx)
    val clampedWidth = shadowWidthPx.coerceAtMost(viewportWidthPx - upperPageRightEdgeX)
    if (clampedWidth <= 0.5f) return null
    val remainingFraction = (-pageOffset).coerceIn(0f, 1f)
    val fadeNearExit = (remainingFraction * 4f).coerceIn(0f, 1f)
    val alpha = (0.28f * fadeNearExit).coerceIn(0f, 0.28f)
    return CoverSlideShadowSpec(
        startX = upperPageRightEdgeX,
        widthPx = clampedWidth,
        startAlpha = alpha,
    )
}

/**
 * Horizontal clip interval `[leftX, rightX]` (in page-local pixels) for the revealed portion of a
 * pinned underlying page in overlapping page-turn modes ([ReaderPageTurnMode.COVER_SLIDE] and
 * [ReaderPageTurnMode.SIMULATION_CURL]).
 */
data class PageTransitionClipBounds(
    val leftX: Float,
    val rightX: Float,
)

/**
 * Computes the revealed right-hand clip region for a pinned underlying incoming page
 * (`pageOffset` in `(-0.999f, -0.001f)`) during a [ReaderPageTurnMode.COVER_SLIDE] or
 * [ReaderPageTurnMode.SIMULATION_CURL] transition.
 *
 * Clipping the underlying page strictly to `[upperPageRightEdgeX, viewportWidthPx]` ensures the
 * upper and lower pages occupy disjoint horizontal partitions and can never bleed through or
 * overlap each other even when the upper page has a transparent canvas over a custom day wallpaper.
 */
internal fun computeLowerPageRevealClip(
    pageOffset: Float,
    viewportWidthPx: Float,
): PageTransitionClipBounds? {
    if (viewportWidthPx <= 0f) return null
    if (pageOffset >= -0.001f || pageOffset <= -0.999f) return null
    val revealLeftX = (-pageOffset * viewportWidthPx).coerceIn(0f, viewportWidthPx)
    return PageTransitionClipBounds(
        leftX = revealLeftX,
        rightX = viewportWidthPx,
    )
}

/**
 * Layer role for a dual-column book spread participating in a center-spine hinged leaf-flip
 * transition ([ReaderPageTurnMode.SIMULATION_CURL] when dual-column spreads are active).
 *
 * For every transition progress `p` in `(0, 1)` between Spread `k` (`pageOffset = p`) and
 * Spread `k + 1` (`pageOffset = p - 1`), the two spreads occupy strictly disjoint horizontal
 * partitions `[0, leafOuterEdgeX]` and `[leafOuterEdgeX, viewportWidthPx]`, guaranteeing zero
 * midpoint flicker and zero bleed-through over custom day wallpapers.
 */
internal enum class DualColumnFlipLayerRole {
    /** Spread is settled (`|pageOffset| <= 0.001f`) — render full spread normally. */
    SETTLED,
    /** Spread is off-stage (`|pageOffset| >= 0.999f`) — skip drawing. */
    OFF_STAGE,
    /**
     * Lower-index spread in first half (`pageOffset` in `(0.001f, 0.5f]`):
     * static left half `[0, spineX]` + hinged right leaf `[spineX, width]` folding toward `spineX`.
     */
    STATIC_LEFT_WITH_FOLDING_RIGHT_LEAF,
    /**
     * Higher-index spread in first half (`pageOffset` in `(-0.999f, -0.5f]`):
     * revealed right region `[leafOuterEdgeX, width]` underneath the lifting right leaf, with cast shadow.
     */
    REVEALED_RIGHT_UNDERNEATH,
    /**
     * Lower-index spread in second half (`pageOffset` in `(0.5f, 0.999f)`):
     * remaining left region `[0, leafOuterEdgeX]` underneath the unfolding left leaf, with cast shadow.
     */
    REVEALED_LEFT_UNDERNEATH,
    /**
     * Higher-index spread in second half (`pageOffset` in `(-0.5f, -0.001f)`):
     * static right half `[spineX, width]` + hinged left leaf `[0, spineX]` unfolding outward from `spineX`.
     */
    STATIC_RIGHT_WITH_UNFOLDING_LEFT_LEAF,
}

/**
 * Pure, unit-testable frame geometry for a center-spine hinged leaf flip in dual-column
 * [ReaderPageTurnMode.SIMULATION_CURL].
 */
internal data class DualColumnBookFlipSpec(
    val role: DualColumnFlipLayerRole,
    val spineX: Float,
    val flatClipLeftX: Float = 0f,
    val flatClipRightX: Float = 0f,
    val leafScaleX: Float = 0f,
    val leafOuterEdgeX: Float = 0f,
    val leafShadeAlpha: Float = 0f,
    val castShadowLeftX: Float = 0f,
    val castShadowRightX: Float = 0f,
    val castShadowAlpha: Float = 0f,
    val castShadowFadesRight: Boolean = true,
)

/**
 * Computes the exact center-spine hinged leaf-flip geometry for a dual-column spread at
 * [pageOffset] (`(currentPage - virtualPageIndex) + currentPageOffsetFraction`).
 *
 * Works symmetrically for forward flips (`k -> k + 1`), backward flips (`k + 1 -> k`), and
 * mid-drag reversals, and honors asymmetric foldable hinge coordinates via [spineCenterXPx].
 */
internal fun computeDualColumnBookFlipSpec(
    pageOffset: Float,
    viewportWidthPx: Float,
    spineCenterXPx: Float,
): DualColumnBookFlipSpec {
    if (viewportWidthPx <= 0f || abs(pageOffset) >= 0.999f) {
        return DualColumnBookFlipSpec(
            role = DualColumnFlipLayerRole.OFF_STAGE,
            spineX = 0f,
        )
    }
    val spineX = spineCenterXPx.coerceIn(1f, (viewportWidthPx - 1f).coerceAtLeast(1f))
    if (abs(pageOffset) <= 0.001f) {
        return DualColumnBookFlipSpec(
            role = DualColumnFlipLayerRole.SETTLED,
            spineX = spineX,
            flatClipLeftX = 0f,
            flatClipRightX = viewportWidthPx,
        )
    }

    val leftHalfWidth = spineX.coerceAtLeast(1f)
    val rightHalfWidth = (viewportWidthPx - spineX).coerceAtLeast(1f)

    return when {
        // First half, lower-index spread k (`pageOffset` in `(0.001f, 0.5f]`):
        // left page stays flat (receiving a smooth ambient spine shadow as the leaf rises toward 90°);
        // right leaf lifts from flat (0°) to vertical at spine (90°).
        pageOffset > 0.001f && pageOffset <= 0.5f -> {
            val t = (pageOffset * 2f).coerceIn(0f, 1f)
            val theta = t * (PI / 2.0)
            val leafScaleX = cos(theta).toFloat().coerceIn(0f, 1f)
            val lift = (1f - leafScaleX * leafScaleX).coerceIn(0f, 1f)
            val outerEdgeX = (spineX + rightHalfWidth * leafScaleX).coerceIn(spineX, viewportWidthPx)
            val leafShadeAlpha = ((1f - leafScaleX) * DUAL_FLIP_MAX_LEAF_SHADE_ALPHA)
                .coerceIn(0f, DUAL_FLIP_MAX_LEAF_SHADE_ALPHA)
            val staticShadowWidth = (leftHalfWidth * DUAL_FLIP_MAX_CAST_SHADOW_WIDTH_FRACTION * lift)
                .coerceAtMost(leftHalfWidth)
            val staticShadowAlpha = (DUAL_FLIP_MAX_CAST_SHADOW_ALPHA * lift * lift)
                .coerceIn(0f, DUAL_FLIP_MAX_CAST_SHADOW_ALPHA)
            DualColumnBookFlipSpec(
                role = DualColumnFlipLayerRole.STATIC_LEFT_WITH_FOLDING_RIGHT_LEAF,
                spineX = spineX,
                flatClipLeftX = 0f,
                flatClipRightX = spineX,
                leafScaleX = leafScaleX,
                leafOuterEdgeX = outerEdgeX,
                leafShadeAlpha = leafShadeAlpha,
                castShadowLeftX = (spineX - staticShadowWidth).coerceAtLeast(0f),
                castShadowRightX = spineX,
                castShadowAlpha = staticShadowAlpha,
                castShadowFadesRight = false,
            )
        }

        // First half, higher-index spread k + 1 (`pageOffset` in `(-0.999f, -0.5f]`):
        // right page revealed strictly in `[outerEdgeX, width]` with continuous cast shadow.
        pageOffset <= -0.5f -> {
            val p = (1f + pageOffset).coerceIn(0f, 0.5f)
            val t = (p * 2f).coerceIn(0f, 1f)
            val theta = t * (PI / 2.0)
            val leafScaleX = cos(theta).toFloat().coerceIn(0f, 1f)
            val sinTheta = sin(theta).toFloat().coerceIn(0f, 1f)
            val lift = (1f - leafScaleX * leafScaleX).coerceIn(0f, 1f)
            val outerEdgeX = (spineX + rightHalfWidth * leafScaleX).coerceIn(spineX, viewportWidthPx)
            val remainingRight = (viewportWidthPx - outerEdgeX).coerceAtLeast(0f)
            val rawShadowWidth = rightHalfWidth * DUAL_FLIP_MAX_CAST_SHADOW_WIDTH_FRACTION * sinTheta
            val shadowWidth = rawShadowWidth.coerceAtMost(remainingRight)
            val castShadowAlpha = (DUAL_FLIP_MAX_CAST_SHADOW_ALPHA * lift)
                .coerceIn(0f, DUAL_FLIP_MAX_CAST_SHADOW_ALPHA)
            DualColumnBookFlipSpec(
                role = DualColumnFlipLayerRole.REVEALED_RIGHT_UNDERNEATH,
                spineX = spineX,
                flatClipLeftX = outerEdgeX,
                flatClipRightX = viewportWidthPx,
                leafScaleX = leafScaleX,
                leafOuterEdgeX = outerEdgeX,
                castShadowLeftX = outerEdgeX,
                castShadowRightX = (outerEdgeX + shadowWidth).coerceAtMost(viewportWidthPx),
                castShadowAlpha = castShadowAlpha,
                castShadowFadesRight = true,
            )
        }

        // Second half, lower-index spread k (`pageOffset` in `(0.5f, 0.999f)`):
        // left page progressively covered by k + 1's unfolding left leaf; clipped to `[0, outerEdgeX]`.
        pageOffset > 0.5f -> {
            val u = ((pageOffset - 0.5f) * 2f).coerceIn(0f, 1f)
            val angle = u * (PI / 2.0)
            val leafScaleX = sin(angle).toFloat().coerceIn(0f, 1f)
            val sinFromFlat = cos(angle).toFloat().coerceIn(0f, 1f)
            val lift = (1f - leafScaleX * leafScaleX).coerceIn(0f, 1f)
            val outerEdgeX = (spineX - leftHalfWidth * leafScaleX).coerceIn(0f, spineX)
            val rawShadowWidth = leftHalfWidth * DUAL_FLIP_MAX_CAST_SHADOW_WIDTH_FRACTION * sinFromFlat
            val shadowWidth = rawShadowWidth.coerceAtMost(outerEdgeX.coerceAtLeast(0f))
            val castShadowAlpha = (DUAL_FLIP_MAX_CAST_SHADOW_ALPHA * lift)
                .coerceIn(0f, DUAL_FLIP_MAX_CAST_SHADOW_ALPHA)
            DualColumnBookFlipSpec(
                role = DualColumnFlipLayerRole.REVEALED_LEFT_UNDERNEATH,
                spineX = spineX,
                flatClipLeftX = 0f,
                flatClipRightX = outerEdgeX,
                leafScaleX = leafScaleX,
                leafOuterEdgeX = outerEdgeX,
                castShadowLeftX = (outerEdgeX - shadowWidth).coerceAtLeast(0f),
                castShadowRightX = outerEdgeX,
                castShadowAlpha = castShadowAlpha,
                castShadowFadesRight = false,
            )
        }

        // Second half, higher-index spread k + 1 (`pageOffset` in `(-0.5f, -0.001f)`):
        // right page stays flat (with spine ambient shadow seamlessly decaying from midpoint to 0);
        // left leaf unfolds outward from vertical at spine (90°) to flat (0°).
        else -> {
            val u = (1f + pageOffset * 2f).coerceIn(0f, 1f)
            val angle = u * (PI / 2.0)
            val leafScaleX = sin(angle).toFloat().coerceIn(0f, 1f)
            val lift = (1f - leafScaleX * leafScaleX).coerceIn(0f, 1f)
            val outerEdgeX = (spineX - leftHalfWidth * leafScaleX).coerceIn(0f, spineX)
            val leafShadeAlpha = ((1f - leafScaleX) * DUAL_FLIP_MAX_LEAF_SHADE_ALPHA)
                .coerceIn(0f, DUAL_FLIP_MAX_LEAF_SHADE_ALPHA)
            val staticShadowWidth = (rightHalfWidth * DUAL_FLIP_MAX_CAST_SHADOW_WIDTH_FRACTION * lift)
                .coerceAtMost(rightHalfWidth)
            val staticShadowAlpha = (DUAL_FLIP_MAX_CAST_SHADOW_ALPHA * lift * lift)
                .coerceIn(0f, DUAL_FLIP_MAX_CAST_SHADOW_ALPHA)
            DualColumnBookFlipSpec(
                role = DualColumnFlipLayerRole.STATIC_RIGHT_WITH_UNFOLDING_LEFT_LEAF,
                spineX = spineX,
                flatClipLeftX = spineX,
                flatClipRightX = viewportWidthPx,
                leafScaleX = leafScaleX,
                leafOuterEdgeX = outerEdgeX,
                leafShadeAlpha = leafShadeAlpha,
                castShadowLeftX = spineX,
                castShadowRightX = (spineX + staticShadowWidth).coerceAtMost(viewportWidthPx),
                castShadowAlpha = staticShadowAlpha,
                castShadowFadesRight = true,
            )
        }
    }
}

private const val DUAL_FLIP_MAX_LEAF_SHADE_ALPHA = 0.22f
private const val DUAL_FLIP_MAX_CAST_SHADOW_ALPHA = 0.24f
private const val DUAL_FLIP_MAX_CAST_SHADOW_WIDTH_FRACTION = 0.18f

/**
 * Tap actions emitted by the 3x3 reader tap-zone matrix (`10-next-gen-reader-engine-architecture.md` §6).
 */
enum class ReaderTapZoneAction {
    PREV_PAGE,
    NEXT_PAGE,
    TOGGLE_HUD,
}

/**
 * Resolved ink & paper colors + optional custom font / day background file for the reader canvas.
 */
data class ReaderVisualPalette(
    val backgroundColor: Color,
    val primaryInkColor: Color,
    val secondaryInkColor: Color,
    val isDarkPalette: Boolean,
    val isEinkMonochrome: Boolean,
    val customDayBackgroundFile: File?,
    val customTypeface: Typeface?,
    val useSimulatedParchmentTexture: Boolean = false,
) {
    val ttsParagraphBgColor: Color
        get() = when {
            isEinkMonochrome -> Color(0xFFEAEAEA)
            isDarkPalette -> Color(0xFF4F8BFF).copy(alpha = 0.22f)
            else -> Color(0xFF1E64B4).copy(alpha = 0.13f)
        }

    val ttsAccentBarColor: Color
        get() = when {
            isEinkMonochrome -> Color(0xFF111111)
            isDarkPalette -> Color(0xFF82B1FF)
            else -> Color(0xFF1E64B4)
        }

    val focalParagraphBgColor: Color
        get() = when {
            isEinkMonochrome -> Color(0xFFF2F2F2)
            isDarkPalette -> Color(0xFFFFB74D).copy(alpha = 0.14f)
            else -> Color(0xFFFFB300).copy(alpha = 0.14f)
        }

    val focalKeywordBgColor: Color
        get() = when {
            isEinkMonochrome -> Color(0xFFD6D6D6)
            isDarkPalette -> Color(0xFFFFB300).copy(alpha = 0.44f)
            else -> Color(0xFFFFD54F).copy(alpha = 0.72f)
        }

    val focalAccentBarColor: Color
        get() = when {
            isEinkMonochrome -> Color(0xFF111111)
            isDarkPalette -> Color(0xFFFFCA28)
            else -> Color(0xFFE65100)
        }
}

/**
 * Cached app-v1 simulated parchment paper textures reused for [ReaderPaperPreset.PARCHMENT]:
 * - [tileShaderBrush]: tiled warm paper grain (`reader_bg_yellow1` / `yellow2` / `yellow3`),
 *   scaled by display density so the 200px nodpi tile matches app-v1's `200.dp` paper grain scale
 *   without upscaling bitmaps in memory.
 * - [edgeVignetteBitmap]: aged-paper border vignette (`reader_bg_yellow_edge`) with transparent center.
 */
data class ReaderParchmentBackgroundAssets(
    val tileShaderBrush: ShaderBrush,
    val edgeVignetteBitmap: ImageBitmap?,
)

internal object ReaderParchmentAssetCache {
    internal val PARCHMENT_TILE_RES_IDS: IntArray = intArrayOf(
        R.drawable.reader_bg_yellow1,
        R.drawable.reader_bg_yellow2,
        R.drawable.reader_bg_yellow3,
    )

    private val lock = Any()
    private var selectedTileResId: Int? = null
    private var cachedTileBitmap: Bitmap? = null
    private var cachedEdgeBitmap: ImageBitmap? = null
    private var edgeDecodeAttempted: Boolean = false

    fun load(
        context: Context,
        density: Float,
    ): ReaderParchmentBackgroundAssets? {
        val resources = context.applicationContext?.resources ?: context.resources
        val tileBitmap: Bitmap
        val edgeBitmap: ImageBitmap?
        synchronized(lock) {
            val resId = selectedTileResId ?: PARCHMENT_TILE_RES_IDS[
                Random.nextInt(PARCHMENT_TILE_RES_IDS.size),
            ].also { selectedTileResId = it }

            val existingTile = cachedTileBitmap?.takeUnless { it.isRecycled }
            tileBitmap = existingTile ?: runCatching {
                BitmapFactory.decodeResource(resources, resId)
            }.getOrNull()?.also { cachedTileBitmap = it } ?: return null

            if (!edgeDecodeAttempted) {
                edgeDecodeAttempted = true
                // Edge overlay is a soft vignette gradient; inSampleSize=2 keeps smooth edges at 1/4 RAM (~2 MB).
                val edgeOptions = BitmapFactory.Options().apply {
                    inSampleSize = 2
                }
                cachedEdgeBitmap = runCatching {
                    BitmapFactory.decodeResource(
                        resources,
                        R.drawable.reader_bg_yellow_edge,
                        edgeOptions,
                    )?.asImageBitmap()
                }.getOrNull()
            }
            edgeBitmap = cachedEdgeBitmap
        }

        val tileScale = density.coerceIn(1f, 3.5f)
        val shader = BitmapShader(
            tileBitmap,
            Shader.TileMode.REPEAT,
            Shader.TileMode.REPEAT,
        ).apply {
            setLocalMatrix(Matrix().apply { setScale(tileScale, tileScale) })
        }
        return ReaderParchmentBackgroundAssets(
            tileShaderBrush = ShaderBrush(shader),
            edgeVignetteBitmap = edgeBitmap,
        )
    }
}

@Composable
fun rememberReaderParchmentAssets(enabled: Boolean): ReaderParchmentBackgroundAssets? {
    if (!enabled) return null
    val context = LocalContext.current
    val density = LocalDensity.current.density
    return remember(context, density) {
        ReaderParchmentAssetCache.load(context = context, density = density)
    }
}

internal fun DrawScope.drawReaderPaperBackground(
    paperBackground: Color,
    parchmentAssets: ReaderParchmentBackgroundAssets?,
    dualColumnSpineCenterXPx: Float? = null,
) {
    drawRect(color = paperBackground)
    if (parchmentAssets == null) return

    val widthPx = size.width.roundToInt()
    val heightPx = size.height.roundToInt()
    if (widthPx <= 0 || heightPx <= 0) return

    drawRect(brush = parchmentAssets.tileShaderBrush)

    val edgeBitmap = parchmentAssets.edgeVignetteBitmap ?: return
    if (dualColumnSpineCenterXPx != null && widthPx >= 4) {
        val spineX = dualColumnSpineCenterXPx.roundToInt().coerceIn(1, widthPx - 1)
        drawImage(
            image = edgeBitmap,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(spineX, heightPx),
            filterQuality = FilterQuality.Low,
        )
        drawImage(
            image = edgeBitmap,
            dstOffset = IntOffset(spineX, 0),
            dstSize = IntSize(widthPx - spineX, heightPx),
            filterQuality = FilterQuality.Low,
        )
    } else {
        drawImage(
            image = edgeBitmap,
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(widthPx, heightPx),
            filterQuality = FilterQuality.Low,
        )
    }
}

/**
 * Pure, unit-testable tap-zone resolver (`10-next-gen-reader-engine-architecture.md` §6):
 * - Single-column 3x3 matrix:
 *   - Top row: `[Prev, Prev, Next]`
 *   - Middle row: `[Prev, Toggle HUD, Next]`
 *   - Bottom row: `[Next, Next, Next]`
 * - Dual-column open-book spread (`isDualColumnSpread == true`):
 *   - Two-handed book ergonomics: left `30%` = `Prev`, center `40%` = `Toggle HUD`, right `30%` = `Next`.
 *
 * When [isRtlBook] is `true` (`日式竖排`), horizontal columns mirror so tapping the left side of a
 * right-bound Bunkobon advances to the next page!
 */
fun resolveNineZoneTapAction(
    tapX: Float,
    tapY: Float,
    viewportWidth: Float,
    viewportHeight: Float,
    isRtlBook: Boolean = false,
    isDualColumnSpread: Boolean = false,
): ReaderTapZoneAction {
    if (viewportWidth <= 0f || viewportHeight <= 0f) {
        return ReaderTapZoneAction.TOGGLE_HUD
    }
    if (isDualColumnSpread) {
        val xFraction = (tapX / viewportWidth).coerceIn(0f, 1f)
        val normalizedX = if (isRtlBook) 1f - xFraction else xFraction
        return when {
            normalizedX < 0.30f -> ReaderTapZoneAction.PREV_PAGE
            normalizedX > 0.70f -> ReaderTapZoneAction.NEXT_PAGE
            else -> ReaderTapZoneAction.TOGGLE_HUD
        }
    }
    val rawCol = ((tapX / viewportWidth) * 3f).toInt().coerceIn(0, 2)
    val col = if (isRtlBook) 2 - rawCol else rawCol
    val row = ((tapY / viewportHeight) * 3f).toInt().coerceIn(0, 2)

    return when (row) {
        0 -> if (col <= 1) ReaderTapZoneAction.PREV_PAGE else ReaderTapZoneAction.NEXT_PAGE
        1 -> when (col) {
            0 -> ReaderTapZoneAction.PREV_PAGE
            1 -> ReaderTapZoneAction.TOGGLE_HUD
            else -> ReaderTapZoneAction.NEXT_PAGE
        }
        else -> ReaderTapZoneAction.NEXT_PAGE
    }
}

/**
 * Predictive-Back-safe left-edge brightness gesture check (`10-next-gen-reader-engine-architecture.md` §6):
 * strictly inset to `24.dp .. 72.dp` so the outer `0 .. 24.dp` bezel always belongs to Android 14–16
 * system Predictive Back!
 */
fun isWithinPredictiveBackSafeBrightnessStrip(
    touchXPx: Float,
    densityScale: Float,
): Boolean {
    val safeScale = densityScale.coerceAtLeast(0.5f)
    val minX = 24f * safeScale
    val maxX = 72f * safeScale
    return touchXPx in minX..maxX
}

/**
 * Builds the active [ReaderVisualPalette], ensuring custom day backgrounds never blind night or
 * E-Ink readers, and activating the reused app-v1 simulated parchment paper texture for
 * [ReaderPaperPreset.PARCHMENT] whenever E-Ink mode and custom wallpapers are off.
 */
fun resolveReaderVisualPalette(
    paperPreset: ReaderPaperPreset,
    isEinkMode: Boolean,
    customBackgroundPath: String,
    customFontPath: String,
): ReaderVisualPalette {
    val typeface = when {
        customFontPath == org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences.BUILTIN_SERIF_FONT_TOKEN -> Typeface.SERIF
        customFontPath.isNotBlank() -> {
            val fontFile = File(customFontPath)
            if (fontFile.isFile && fontFile.length() > 0L) {
                runCatching { Typeface.createFromFile(fontFile) }.getOrNull()
            } else {
                null
            }
        }
        else -> null
    }

    if (isEinkMode) {
        return ReaderVisualPalette(
            backgroundColor = Color(0xFFFFFFFF),
            primaryInkColor = Color(0xFF111111),
            secondaryInkColor = Color(0xFF333333),
            isDarkPalette = false,
            isEinkMonochrome = true,
            customDayBackgroundFile = null,
            customTypeface = typeface,
            useSimulatedParchmentTexture = false,
        )
    }

    val customBgFile = if (!paperPreset.isDark && customBackgroundPath.isNotBlank()) {
        File(customBackgroundPath).takeIf { it.isFile && it.length() > 0L }
    } else {
        null
    }

    return ReaderVisualPalette(
        backgroundColor = Color(paperPreset.backgroundArgb),
        primaryInkColor = Color(paperPreset.primaryTextArgb),
        secondaryInkColor = Color(paperPreset.secondaryTextArgb),
        isDarkPalette = paperPreset.isDark,
        isEinkMonochrome = false,
        customDayBackgroundFile = customBgFile,
        customTypeface = typeface,
        useSimulatedParchmentTexture = paperPreset == ReaderPaperPreset.PARCHMENT && customBgFile == null,
    )
}

/**
 * Hosts the active page-turning delegate (`1` through `6`) over [ReaderWindowSnapshot].
 *
 * The pager and page-turn transition layer span full edge-to-edge (including the top status bar
 * and display cutout) so cover-slide shadows and simulation curls never clip at the status bar,
 * while [contentSafeInsets] keeps all text, captions, and illustrations cleanly outside cutouts.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReaderViewportHost(
    pageTurnMode: ReaderPageTurnMode,
    snapshot: ReaderWindowSnapshot,
    pagerState: PagerState,
    palette: ReaderVisualPalette,
    fontSizeSp: Int,
    lineDistanceDp: Int,
    paragraphDistanceDp: Int,
    pageEdgeDistanceDp: Int,
    leftEdgeBrightnessDragEnabled: Boolean,
    contentHighlight: ReaderContentHighlight = ReaderContentHighlight(),
    continuousScrollJumpCommand: ContinuousScrollJumpCommand? = null,
    parchmentAssets: ReaderParchmentBackgroundAssets? = null,
    contentSafeInsets: WindowInsets = WindowInsets.displayCutout,
    isDualColumnSpread: Boolean = false,
    dualColumnSpineCenterXPx: Float? = null,
    novelTitle: String = "",
    onTapZoneAction: (ReaderTapZoneAction) -> Unit,
    onBrightnessDragDelta: (Float) -> Unit,
    onIllustrationClicked: (imageUrl: String, imageIndexInChapter: Int) -> Unit,
    onContinuousScrollAnchorChanged: (ReadingAnchor, Float) -> Unit,
    onRequestNextChapterInContinuousMode: () -> Unit,
    onRequestPrevChapterInContinuousMode: () -> Unit,
    onRetryFailedSlot: (cid: Int) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    onOpenChapterInBrowser: (url: String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isEink = LocalEinkMode.current
    val effectiveMode = if (isEink && pageTurnMode != ReaderPageTurnMode.CONTINUOUS_SCROLL &&
        pageTurnMode != ReaderPageTurnMode.VERTICAL_BUNKOBON
    ) {
        ReaderPageTurnMode.EINK_INSTANT
    } else {
        pageTurnMode
    }

    val density = LocalDensity.current
    val isRtlBunkobon = effectiveMode == ReaderPageTurnMode.VERTICAL_BUNKOBON
    val effectivePagedDualColumn = isDualColumnSpread && effectiveMode != ReaderPageTurnMode.CONTINUOUS_SCROLL
    val effectiveSpineCenterPx = if (effectivePagedDualColumn) dualColumnSpineCenterXPx else null
    val clockLabel = rememberSystemClockText()
    val fallbackParchmentAssets = rememberReaderParchmentAssets(
        enabled = parchmentAssets == null && palette.useSimulatedParchmentTexture,
    )
    val activeParchmentAssets = if (palette.useSimulatedParchmentTexture) {
        parchmentAssets ?: fallbackParchmentAssets
    } else {
        null
    }
    val latestOnTapZoneAction by rememberUpdatedState(onTapZoneAction)
    val latestOnBrightnessDragDelta by rememberUpdatedState(onBrightnessDragDelta)

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                if (palette.customDayBackgroundFile != null) {
                    drawRect(color = palette.backgroundColor)
                } else {
                    drawReaderPaperBackground(
                        paperBackground = palette.backgroundColor,
                        parchmentAssets = activeParchmentAssets,
                        dualColumnSpineCenterXPx = effectiveSpineCenterPx,
                    )
                }
            }
            .testTag(ReaderTestTags.VIEWPORT_CONTAINER)
            .pointerInput(effectiveMode, isRtlBunkobon, effectivePagedDualColumn, pagerState) {
                if (effectiveMode != ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        val wasScrollingAtDown = pagerState.isScrollInProgress
                        if (!wasScrollingAtDown) {
                            // Idle viewport: wait on `PointerEventPass.Main` so child clickables
                            // (inline illustrations, Error-slot retry/diagnostics buttons) consume `up` first.
                            val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                waitForUpOrCancellation(pass = PointerEventPass.Main)
                            }
                            if (up != null) {
                                val action = resolveNineZoneTapAction(
                                    tapX = up.position.x,
                                    tapY = up.position.y,
                                    viewportWidth = size.width.toFloat(),
                                    viewportHeight = size.height.toFloat(),
                                    isRtlBook = isRtlBunkobon,
                                    isDualColumnSpread = effectivePagedDualColumn,
                                )
                                latestOnTapZoneAction(action)
                            }
                        } else {
                            // Pager is mid-turn (`animateScrollToPage` or settling fling): `HorizontalPager`
                            // consumes `down` immediately on `Main` (`shouldScrollImmediately == true`) and
                            // holds `MutatePriority.UserInput` until `Main` processes `UP`. Observe on
                            // `PointerEventPass.Final` so `UserInput` is already released when `stepPageBy` runs!
                            val pointerId = down.id
                            val touchSlop = viewConfiguration.touchSlop
                            val upPos = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                var resultPos: Offset? = null
                                while (true) {
                                    val event = awaitPointerEvent(pass = PointerEventPass.Final)
                                    if (event.changes.count { it.pressed } > 1) {
                                        break
                                    }
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                                    val dx = change.position.x - down.position.x
                                    val dy = change.position.y - down.position.y
                                    if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                                        break
                                    }
                                    if (!change.pressed) {
                                        resultPos = change.position
                                        break
                                    }
                                }
                                resultPos
                            }
                            if (upPos != null) {
                                val action = resolveNineZoneTapAction(
                                    tapX = upPos.x,
                                    tapY = upPos.y,
                                    viewportWidth = size.width.toFloat(),
                                    viewportHeight = size.height.toFloat(),
                                    isRtlBook = isRtlBunkobon,
                                    isDualColumnSpread = effectivePagedDualColumn,
                                )
                                latestOnTapZoneAction(action)
                            }
                        }
                    }
                }
            }
            .pointerInput(leftEdgeBrightnessDragEnabled, density.density) {
                if (leftEdgeBrightnessDragEnabled) {
                    // Intercept vertical drags inside `24..72dp` during `PointerEventPass.Initial`
                    // (top-down, BEFORE child `LazyColumn` in `CONTINUOUS_SCROLL` consumes vertical
                    // scroll events on `PointerEventPass.Main`), while letting taps, horizontal page
                    // swipes, multi-touch pinches, and normal scrolling outside `24..72dp` pass through!
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Initial,
                        )
                        if (!isWithinPredictiveBackSafeBrightnessStrip(
                                touchXPx = down.position.x,
                                densityScale = density.density,
                            )
                        ) {
                            return@awaitEachGesture
                        }

                        val pointerId = down.id
                        val touchSlop = viewConfiguration.touchSlop
                        var lastY = down.position.y
                        var lockedBrightnessDrag = false

                        while (true) {
                            val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                            if (!lockedBrightnessDrag && event.changes.count { it.pressed } > 1) {
                                break
                            }
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (!change.pressed) {
                                if (lockedBrightnessDrag) {
                                    change.consume()
                                }
                                break
                            }

                            if (!lockedBrightnessDrag) {
                                val totalDx = change.position.x - down.position.x
                                val totalDy = change.position.y - down.position.y
                                if (kotlin.math.abs(totalDx) > touchSlop &&
                                    kotlin.math.abs(totalDx) > kotlin.math.abs(totalDy)
                                ) {
                                    break
                                }
                                if (kotlin.math.abs(totalDy) > touchSlop) {
                                    lockedBrightnessDrag = true
                                    val slopSign = if (totalDy > 0f) 1f else -1f
                                    lastY = down.position.y + slopSign * touchSlop
                                }
                            }

                            if (lockedBrightnessDrag) {
                                val dragAmountY = change.position.y - lastY
                                lastY = change.position.y
                                change.consume()
                                if (size.height > 0 && dragAmountY != 0f) {
                                    // Dragging upward (`dragAmountY < 0`) increases brightness.
                                    val deltaFraction = (-dragAmountY / (size.height * 0.65f))
                                    latestOnBrightnessDragDelta(deltaFraction)
                                }
                            }
                        }
                    }
                }
            },
    ) {
        // Optional custom day-mode wallpaper rendered behind transparent page canvases.
        if (palette.customDayBackgroundFile != null) {
            AsyncImage(
                model = palette.customDayBackgroundFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Subtle warm veil guarantees WCAG-compliant ink legibility on bright custom photos.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(palette.backgroundColor.copy(alpha = 0.72f)),
            )
        }

        when (effectiveMode) {
            ReaderPageTurnMode.CONTINUOUS_SCROLL -> {
                ContinuousScrollReaderDelegate(
                    snapshot = snapshot,
                    palette = palette,
                    clockLabel = clockLabel,
                    contentHighlight = contentHighlight,
                    jumpCommand = continuousScrollJumpCommand,
                    contentSafeInsets = contentSafeInsets,
                    fontSizeSp = fontSizeSp,
                    lineDistanceDp = lineDistanceDp,
                    paragraphDistanceDp = paragraphDistanceDp,
                    pageEdgeDistanceDp = pageEdgeDistanceDp,
                    onToggleHud = { onTapZoneAction(ReaderTapZoneAction.TOGGLE_HUD) },
                    onIllustrationClicked = onIllustrationClicked,
                    onAnchorChanged = onContinuousScrollAnchorChanged,
                    onRequestNextChapter = onRequestNextChapterInContinuousMode,
                    onRequestPrevChapter = onRequestPrevChapterInContinuousMode,
                    onRetryFailedSlot = onRetryFailedSlot,
                    onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                    onOpenChapterInBrowser = onOpenChapterInBrowser,
                )
            }

            ReaderPageTurnMode.COVER_SLIDE,
            ReaderPageTurnMode.SIMULATION_CURL,
            ReaderPageTurnMode.SPRING_SLIDE,
            ReaderPageTurnMode.VERTICAL_BUNKOBON,
            ReaderPageTurnMode.EINK_INSTANT,
            -> {
                val flingBehavior = when (effectiveMode) {
                    ReaderPageTurnMode.EINK_INSTANT -> PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = tween(durationMillis = 0),
                    )
                    ReaderPageTurnMode.SPRING_SLIDE -> PagerDefaults.flingBehavior(
                        state = pagerState,
                        snapAnimationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                    )
                    else -> PagerDefaults.flingBehavior(state = pagerState)
                }

                HorizontalPager(
                    state = pagerState,
                    reverseLayout = isRtlBunkobon,
                    beyondViewportPageCount = 1,
                    flingBehavior = flingBehavior,
                    key = { virtualPageIndex -> snapshot.virtualPagePagerKey(virtualPageIndex) },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER),
                ) { virtualPageIndex ->
                    val resolution = snapshot.resolveVirtualPage(virtualPageIndex)
                    val isActiveViewportPage = virtualPageIndex == pagerState.currentPage
                    val isArtbookSpread = resolution?.page?.isFullBleedArtbookPage == true
                    val pageSpineCenterPx = if (effectivePagedDualColumn && !isArtbookSpread) {
                        effectiveSpineCenterPx
                    } else {
                        null
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .applyPageTurnTransition(
                                mode = effectiveMode,
                                virtualPageIndex = virtualPageIndex,
                                pageOffsetProvider = {
                                    (pagerState.currentPage - virtualPageIndex) +
                                        pagerState.currentPageOffsetFraction
                                },
                                paperBackground = palette.backgroundColor,
                                isDarkPalette = palette.isDarkPalette,
                                hasCustomDayWallpaper = palette.customDayBackgroundFile != null,
                                parchmentAssets = activeParchmentAssets,
                                dualColumnSpineCenterXPx = pageSpineCenterPx,
                                isDualColumnSpread = effectivePagedDualColumn,
                                dualColumnFlipSpineCenterXPx = effectiveSpineCenterPx,
                            ),
                    ) {
                        if (resolution != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .windowInsetsPadding(contentSafeInsets),
                            ) {
                                ReaderResolvedPageSurface(
                                    resolution = resolution,
                                    palette = palette,
                                    clockLabel = clockLabel,
                                    contentHighlight = contentHighlight,
                                    isActiveViewportPage = isActiveViewportPage,
                                    novelTitle = novelTitle,
                                    isRtlBunkobon = isRtlBunkobon,
                                    onIllustrationClicked = onIllustrationClicked,
                                    onRetryFailedSlot = onRetryFailedSlot,
                                    onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                                    onOpenChapterInBrowser = onOpenChapterInBrowser,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Applies the selected official-AndroidX page-turn transition (`CoverSlide`, `SimulationCurl`
 * with API 33+ AGSL `RuntimeShader` + `Canvas` Bezier fallback and center-spine hinged leaf-flip
 * in dual-column spreads, `SpringSlide`, `VerticalBunkobon`, or `EInkInstant`).
 */
private fun Modifier.applyPageTurnTransition(
    mode: ReaderPageTurnMode,
    virtualPageIndex: Int,
    pageOffsetProvider: () -> Float,
    paperBackground: Color,
    isDarkPalette: Boolean,
    hasCustomDayWallpaper: Boolean,
    parchmentAssets: ReaderParchmentBackgroundAssets?,
    dualColumnSpineCenterXPx: Float? = null,
    isDualColumnSpread: Boolean = false,
    dualColumnFlipSpineCenterXPx: Float? = null,
): Modifier {
    return when (mode) {
        ReaderPageTurnMode.COVER_SLIDE -> {
            val spineShadowWidthDp = 24.dp
            this
                .zIndex(-virtualPageIndex.toFloat())
                .graphicsLayer {
                    val pageOffset = pageOffsetProvider()
                    // Hide idle off-stage beyondViewportPageCount neighbors so they never bleed through.
                    if (abs(pageOffset) >= 0.999f) {
                        alpha = 0f
                        translationX = 0f
                    } else {
                        alpha = 1f
                        // `pageOffset > 0f`: outgoing upper page slides off to the left naturally.
                        // `pageOffset < 0f`: incoming underlying page (`virtualPageIndex > currentPage`) stays pinned!
                        translationX = if (pageOffset < 0f) pageOffset * size.width else 0f
                    }
                }
                .drawWithContent {
                    val pageOffset = pageOffsetProvider()
                    if (abs(pageOffset) >= 0.999f) return@drawWithContent
                    val lowerRevealClip = computeLowerPageRevealClip(
                        pageOffset = pageOffset,
                        viewportWidthPx = size.width,
                    )
                    if (lowerRevealClip != null) {
                        // Incoming pinned page (`pageOffset < 0f`): clip strictly to the revealed
                        // right-hand strip `[upperPageRightEdgeX, width]` so the lower page can
                        // never bleed through a transparent custom-wallpaper upper page.
                        if (lowerRevealClip.rightX - lowerRevealClip.leftX > 0.5f) {
                            drawContext.canvas.save()
                            drawContext.canvas.clipRect(
                                left = lowerRevealClip.leftX,
                                top = 0f,
                                right = lowerRevealClip.rightX,
                                bottom = size.height,
                            )
                            if (!hasCustomDayWallpaper) {
                                drawReaderPaperBackground(
                                    paperBackground = paperBackground,
                                    parchmentAssets = parchmentAssets,
                                    dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                                )
                            }
                            drawContent()
                            // Ambient drop shadow cast by the sliding upper page's right edge onto
                            // the pinned underlying page, moving right-to-left (`size.width -> 0f`)
                            // in lockstep with the upper page's edge!
                            val shadowSpec = computeCoverSlideShadowSpec(
                                pageOffset = pageOffset,
                                viewportWidthPx = size.width,
                                shadowWidthPx = spineShadowWidthDp.toPx(),
                            )
                            if (shadowSpec != null) {
                                drawRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.Black.copy(alpha = shadowSpec.startAlpha),
                                            Color.Transparent,
                                        ),
                                        startX = shadowSpec.startX,
                                        endX = shadowSpec.startX + shadowSpec.widthPx,
                                    ),
                                    topLeft = Offset(shadowSpec.startX, 0f),
                                    size = Size(shadowSpec.widthPx, size.height),
                                )
                            }
                            drawContext.canvas.restore()
                        }
                    } else {
                        // Outgoing upper page (`pageOffset > 0f`) or settled current page.
                        // Clip upper sliding page to its own `[0, width]` bounds so glyph overhang
                        // never bleeds past its right edge into the revealed lower page.
                        val clipUpperBounds = pageOffset > 0.001f
                        if (clipUpperBounds) {
                            drawContext.canvas.save()
                            drawContext.canvas.clipRect(0f, 0f, size.width, size.height)
                        }
                        if (!hasCustomDayWallpaper) {
                            drawReaderPaperBackground(
                                paperBackground = paperBackground,
                                parchmentAssets = parchmentAssets,
                                dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                            )
                        }
                        drawContent()
                        if (clipUpperBounds) {
                            drawContext.canvas.restore()
                        }
                    }
                }
        }

        ReaderPageTurnMode.SIMULATION_CURL -> {
            this
                .zIndex(-virtualPageIndex.toFloat())
                .graphicsLayer {
                    val pageOffset = pageOffsetProvider()
                    if (abs(pageOffset) >= 0.999f) {
                        alpha = 0f
                        translationX = 0f
                    } else {
                        alpha = 1f
                        // Both outgoing (`pageOffset > 0f`) and incoming (`pageOffset < 0f`) pages stay
                        // anchored at the book spine while the turning page/leaf animates in place.
                        translationX = pageOffset * size.width
                    }
                }
                .drawWithContent {
                    val pageOffset = pageOffsetProvider()
                    if (abs(pageOffset) >= 0.999f) return@drawWithContent

                    if (isDualColumnSpread) {
                        val hingeSpineX = dualColumnFlipSpineCenterXPx ?: (size.width * 0.5f)
                        val spec = computeDualColumnBookFlipSpec(
                            pageOffset = pageOffset,
                            viewportWidthPx = size.width,
                            spineCenterXPx = hingeSpineX,
                        )
                        when (spec.role) {
                            DualColumnFlipLayerRole.OFF_STAGE -> Unit

                            DualColumnFlipLayerRole.SETTLED -> {
                                if (!hasCustomDayWallpaper) {
                                    drawReaderPaperBackground(
                                        paperBackground = paperBackground,
                                        parchmentAssets = parchmentAssets,
                                        dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                                    )
                                }
                                drawContent()
                            }

                            DualColumnFlipLayerRole.STATIC_LEFT_WITH_FOLDING_RIGHT_LEAF -> {
                                // 1. Static left half `[0, spineX]`.
                                if (spec.flatClipRightX - spec.flatClipLeftX > 0.5f) {
                                    drawContext.canvas.save()
                                    drawContext.canvas.clipRect(
                                        left = spec.flatClipLeftX,
                                        top = 0f,
                                        right = spec.flatClipRightX,
                                        bottom = size.height,
                                    )
                                    if (!hasCustomDayWallpaper) {
                                        drawReaderPaperBackground(
                                            paperBackground = paperBackground,
                                            parchmentAssets = parchmentAssets,
                                            dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                                        )
                                    }
                                    drawContent()
                                    drawDualColumnHingedCastShadow(spec = spec)
                                    drawContext.canvas.restore()
                                }

                                // 2. Center-spine hinged right leaf folding inward `[spineX, leafOuterEdgeX]`.
                                if (spec.leafScaleX > 0.004f && spec.leafOuterEdgeX - spec.spineX > 0.5f) {
                                    drawContext.canvas.save()
                                    drawContext.canvas.translate(spec.spineX, 0f)
                                    drawContext.canvas.scale(spec.leafScaleX, 1f)
                                    drawContext.canvas.translate(-spec.spineX, 0f)
                                    drawContext.canvas.clipRect(
                                        left = spec.spineX,
                                        top = 0f,
                                        right = size.width,
                                        bottom = size.height,
                                    )
                                    if (!hasCustomDayWallpaper) {
                                        drawReaderPaperBackground(
                                            paperBackground = paperBackground,
                                            parchmentAssets = parchmentAssets,
                                            dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                                        )
                                    }
                                    drawContent()
                                    drawContext.canvas.restore()

                                    drawDualColumnHingedLeafShading(
                                        leafLeftX = spec.spineX,
                                        leafRightX = spec.leafOuterEdgeX,
                                        isRightLeaf = true,
                                        leafShadeAlpha = spec.leafShadeAlpha,
                                        isDarkPalette = isDarkPalette,
                                    )
                                }
                            }

                            DualColumnFlipLayerRole.REVEALED_RIGHT_UNDERNEATH -> {
                                if (spec.flatClipRightX - spec.flatClipLeftX > 0.5f) {
                                    drawContext.canvas.save()
                                    drawContext.canvas.clipRect(
                                        left = spec.flatClipLeftX,
                                        top = 0f,
                                        right = spec.flatClipRightX,
                                        bottom = size.height,
                                    )
                                    if (!hasCustomDayWallpaper) {
                                        drawReaderPaperBackground(
                                            paperBackground = paperBackground,
                                            parchmentAssets = parchmentAssets,
                                            dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                                        )
                                    }
                                    drawContent()
                                    drawDualColumnHingedCastShadow(spec = spec)
                                    drawContext.canvas.restore()
                                }
                            }

                            DualColumnFlipLayerRole.REVEALED_LEFT_UNDERNEATH -> {
                                if (spec.flatClipRightX - spec.flatClipLeftX > 0.5f) {
                                    drawContext.canvas.save()
                                    drawContext.canvas.clipRect(
                                        left = spec.flatClipLeftX,
                                        top = 0f,
                                        right = spec.flatClipRightX,
                                        bottom = size.height,
                                    )
                                    if (!hasCustomDayWallpaper) {
                                        drawReaderPaperBackground(
                                            paperBackground = paperBackground,
                                            parchmentAssets = parchmentAssets,
                                            dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                                        )
                                    }
                                    drawContent()
                                    drawDualColumnHingedCastShadow(spec = spec)
                                    drawContext.canvas.restore()
                                }
                            }

                            DualColumnFlipLayerRole.STATIC_RIGHT_WITH_UNFOLDING_LEFT_LEAF -> {
                                // 1. Static right half `[spineX, width]`.
                                if (spec.flatClipRightX - spec.flatClipLeftX > 0.5f) {
                                    drawContext.canvas.save()
                                    drawContext.canvas.clipRect(
                                        left = spec.flatClipLeftX,
                                        top = 0f,
                                        right = spec.flatClipRightX,
                                        bottom = size.height,
                                    )
                                    if (!hasCustomDayWallpaper) {
                                        drawReaderPaperBackground(
                                            paperBackground = paperBackground,
                                            parchmentAssets = parchmentAssets,
                                            dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                                        )
                                    }
                                    drawContent()
                                    drawDualColumnHingedCastShadow(spec = spec)
                                    drawContext.canvas.restore()
                                }

                                // 2. Center-spine hinged left leaf unfolding outward `[leafOuterEdgeX, spineX]`.
                                if (spec.leafScaleX > 0.004f && spec.spineX - spec.leafOuterEdgeX > 0.5f) {
                                    drawContext.canvas.save()
                                    drawContext.canvas.translate(spec.spineX, 0f)
                                    drawContext.canvas.scale(spec.leafScaleX, 1f)
                                    drawContext.canvas.translate(-spec.spineX, 0f)
                                    drawContext.canvas.clipRect(
                                        left = 0f,
                                        top = 0f,
                                        right = spec.spineX,
                                        bottom = size.height,
                                    )
                                    if (!hasCustomDayWallpaper) {
                                        drawReaderPaperBackground(
                                            paperBackground = paperBackground,
                                            parchmentAssets = parchmentAssets,
                                            dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                                        )
                                    }
                                    drawContent()
                                    drawContext.canvas.restore()

                                    drawDualColumnHingedLeafShading(
                                        leafLeftX = spec.leafOuterEdgeX,
                                        leafRightX = spec.spineX,
                                        isRightLeaf = false,
                                        leafShadeAlpha = spec.leafShadeAlpha,
                                        isDarkPalette = isDarkPalette,
                                    )
                                }
                            }
                        }
                        return@drawWithContent
                    }

                    // Single-column classic cylinder curl.
                    if (pageOffset <= 0.001f) {
                        val lowerRevealClip = computeLowerPageRevealClip(
                            pageOffset = pageOffset,
                            viewportWidthPx = size.width,
                        )
                        if (lowerRevealClip != null) {
                            if (lowerRevealClip.rightX - lowerRevealClip.leftX > 0.5f) {
                                drawContext.canvas.save()
                                drawContext.canvas.clipRect(
                                    left = lowerRevealClip.leftX,
                                    top = 0f,
                                    right = lowerRevealClip.rightX,
                                    bottom = size.height,
                                )
                                if (!hasCustomDayWallpaper) {
                                    drawReaderPaperBackground(
                                        paperBackground = paperBackground,
                                        parchmentAssets = parchmentAssets,
                                        dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                                    )
                                }
                                drawContent()
                                drawContext.canvas.restore()
                            }
                        } else {
                            if (!hasCustomDayWallpaper) {
                                drawReaderPaperBackground(
                                    paperBackground = paperBackground,
                                    parchmentAssets = parchmentAssets,
                                    dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                                )
                            }
                            drawContent()
                        }
                    } else {
                        // Outgoing curling page: clip paper & ink together to cylinder fold `foldX`
                        // so the underlying incoming page shows cleanly to the right of `foldX`!
                        val foldX = ((1f - pageOffset) * size.width).coerceIn(0f, size.width)
                        val curlWidth = (size.width * 0.16f * (1f - abs(pageOffset * 2f - 1f)) + 18f)
                            .coerceAtMost(foldX.coerceAtLeast(1f))

                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(0f, 0f, foldX, size.height)
                        if (!hasCustomDayWallpaper) {
                            drawReaderPaperBackground(
                                paperBackground = paperBackground,
                                parchmentAssets = parchmentAssets,
                                dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                            )
                        }
                        drawContent()
                        drawContext.canvas.restore()

                        // Specular paper crease via Android 13+ AGSL RuntimeShader (with Canvas gradient fallback).
                        drawSimulationCreaseAndBackOfPage(
                            foldX = foldX,
                            curlWidth = curlWidth,
                            paperBackground = paperBackground,
                        )
                    }
                }
        }

        ReaderPageTurnMode.SPRING_SLIDE,
        ReaderPageTurnMode.VERTICAL_BUNKOBON,
        ReaderPageTurnMode.EINK_INSTANT,
        ReaderPageTurnMode.CONTINUOUS_SCROLL,
        -> {
            this.drawWithContent {
                if (!hasCustomDayWallpaper) {
                    drawReaderPaperBackground(
                        paperBackground = paperBackground,
                        parchmentAssets = parchmentAssets,
                        dualColumnSpineCenterXPx = dualColumnSpineCenterXPx,
                    )
                }
                drawContent()
            }
        }
    }
}

private fun DrawScope.drawDualColumnHingedCastShadow(
    spec: DualColumnBookFlipSpec,
) {
    val shadowWidth = spec.castShadowRightX - spec.castShadowLeftX
    if (shadowWidth <= 0.5f || spec.castShadowAlpha <= 0.002f) return
    val a = spec.castShadowAlpha
    val brush = if (spec.castShadowFadesRight) {
        Brush.horizontalGradient(
            0.0f to Color.Black.copy(alpha = a),
            0.28f to Color.Black.copy(alpha = a * 0.48f),
            0.62f to Color.Black.copy(alpha = a * 0.14f),
            1.0f to Color.Transparent,
            startX = spec.castShadowLeftX,
            endX = spec.castShadowRightX,
        )
    } else {
        Brush.horizontalGradient(
            0.0f to Color.Transparent,
            0.38f to Color.Black.copy(alpha = a * 0.14f),
            0.72f to Color.Black.copy(alpha = a * 0.48f),
            1.0f to Color.Black.copy(alpha = a),
            startX = spec.castShadowLeftX,
            endX = spec.castShadowRightX,
        )
    }
    drawRect(
        brush = brush,
        topLeft = Offset(spec.castShadowLeftX, 0f),
        size = Size(shadowWidth, size.height),
    )
}

private fun DrawScope.drawDualColumnHingedLeafShading(
    leafLeftX: Float,
    leafRightX: Float,
    isRightLeaf: Boolean,
    leafShadeAlpha: Float,
    isDarkPalette: Boolean,
) {
    val leafWidth = leafRightX - leafLeftX
    if (leafWidth <= 0.5f || leafShadeAlpha <= 0.002f) return

    val effectiveShadeAlpha = if (isDarkPalette) leafShadeAlpha * 1.15f else leafShadeAlpha
    val usedAgslShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching {
            val shader = RuntimeShader(AGSL_HINGED_LEAF_SHADING_SHADER).apply {
                setFloatUniform("uLeafRange", leafLeftX, leafRightX)
                setFloatUniform("uIsRightLeaf", if (isRightLeaf) 1f else 0f)
                setFloatUniform("uShadeAlpha", effectiveShadeAlpha)
            }
            val shaderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                this.shader = shader as Shader
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(leafLeftX, 0f, leafRightX, size.height, shaderPaint)
            }
            true
        }.getOrDefault(false)
    } else {
        false
    }

    if (!usedAgslShader) {
        val spineDark = Color.Black.copy(alpha = (effectiveShadeAlpha * 1.55f).coerceAtMost(0.38f))
        val nearSpineShade = Color.Black.copy(alpha = (effectiveShadeAlpha * 0.92f).coerceAtMost(0.24f))
        val midShade = Color.Black.copy(alpha = (effectiveShadeAlpha * 0.78f).coerceAtMost(0.22f))
        val outerShade = Color.Black.copy(alpha = (effectiveShadeAlpha * 0.95f).coerceAtMost(0.26f))
        val brush = if (isRightLeaf) {
            Brush.horizontalGradient(
                0.0f to spineDark,
                0.20f to nearSpineShade,
                0.55f to midShade,
                1.0f to outerShade,
                startX = leafLeftX,
                endX = leafRightX,
            )
        } else {
            Brush.horizontalGradient(
                0.0f to outerShade,
                0.45f to midShade,
                0.80f to nearSpineShade,
                1.0f to spineDark,
                startX = leafLeftX,
                endX = leafRightX,
            )
        }
        drawRect(
            brush = brush,
            topLeft = Offset(leafLeftX, 0f),
            size = Size(leafWidth, size.height),
        )
    }

    // Crisp tactile paper-edge rim along the moving outer border of the turning sheet,
    // modulated by tilt so it fades in smoothly at flip start and disappears before settle.
    val rimVisibility = (leafShadeAlpha / 0.08f).coerceIn(0f, 1f)
    val rimAlpha = (if (isDarkPalette) 0.14f else 0.34f) * rimVisibility
    if (rimAlpha > 0.004f) {
        val rimWidthPx = (1.5.dp.toPx()).coerceAtMost(leafWidth)
        val outerEdgeLeft = if (isRightLeaf) {
            (leafRightX - rimWidthPx).coerceAtLeast(leafLeftX)
        } else {
            leafLeftX
        }
        drawRect(
            color = Color.White.copy(alpha = rimAlpha),
            topLeft = Offset(outerEdgeLeft, 0f),
            size = Size(rimWidthPx, size.height),
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSimulationCreaseAndBackOfPage(
    foldX: Float,
    curlWidth: Float,
    paperBackground: Color,
) {
    val backLeft = (foldX - curlWidth).coerceAtLeast(0f)

    // Cast drop shadow onto revealed next page to the right of `foldX`.
    val castShadowWidth = (curlWidth * 0.65f).coerceAtMost((size.width - foldX).coerceAtLeast(0f))
    if (castShadowWidth > 1f) {
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black.copy(alpha = 0.26f), Color.Transparent),
                startX = foldX,
                endX = foldX + castShadowWidth,
            ),
            topLeft = Offset(foldX, 0f),
            size = Size(castShadowWidth, size.height),
        )
    }

    // 2D Bezier curled paper back polygon.
    val curlPath = Path().apply {
        moveTo(foldX, 0f)
        quadraticTo(
            x1 = backLeft - curlWidth * 0.12f,
            y1 = size.height * 0.5f,
            x2 = foldX,
            y2 = size.height,
        )
        lineTo(backLeft, size.height)
        quadraticTo(
            x1 = backLeft + curlWidth * 0.10f,
            y1 = size.height * 0.5f,
            x2 = backLeft,
            y2 = 0f,
        )
        close()
    }

    val usedAgslShader = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        runCatching {
            val shader = RuntimeShader(AGSL_PAPER_CURL_SHADER).apply {
                setFloatUniform("uFoldRange", backLeft, foldX.coerceAtLeast(backLeft + 1f))
                setColorUniform("uPaperColor", paperBackground.toArgb())
            }
            val shaderPaint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
                this.shader = shader as Shader
            }
            drawIntoCanvas { canvas ->
                canvas.nativeCanvas.drawRect(backLeft, 0f, foldX, size.height, shaderPaint)
            }
            true
        }.getOrDefault(false)
    } else {
        false
    }

    if (!usedAgslShader) {
        drawPath(
            path = curlPath,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.14f),
                    paperBackground.copy(alpha = 0.96f),
                    Color.White.copy(alpha = 0.28f),
                ),
                startX = backLeft,
                endX = foldX.coerceAtLeast(backLeft + 1f),
            ),
        )
    }
}

private const val AGSL_PAPER_CURL_SHADER = """
uniform float2 uFoldRange;
layout(color) uniform half4 uPaperColor;

half4 main(float2 fragCoord) {
    float span = max(1.0, uFoldRange.y - uFoldRange.x);
    float t = clamp((fragCoord.x - uFoldRange.x) / span, 0.0, 1.0);
    float cylinderLight = sin(t * 3.14159265);
    half3 base = mix(uPaperColor.rgb * 0.86, min(half3(1.0), uPaperColor.rgb * 1.08), half(cylinderLight));
    return half4(base, 0.96);
}
"""

private const val AGSL_HINGED_LEAF_SHADING_SHADER = """
uniform float2 uLeafRange;
uniform float uIsRightLeaf;
uniform float uShadeAlpha;

half4 main(float2 fragCoord) {
    float span = max(1.0, uLeafRange.y - uLeafRange.x);
    float rawU = clamp((fragCoord.x - uLeafRange.x) / span, 0.0, 1.0);
    // u = 0 at the bound center spine, u = 1 at the moving outer edge of the leaf.
    float u = mix(1.0 - rawU, rawU, uIsRightLeaf);
    // All terms scale strictly with uShadeAlpha so extra shading is 0 when the leaf lies flat.
    float spineCrease = uShadeAlpha * 0.65 * exp(-u * 6.0);
    float sheetTilt = uShadeAlpha * (0.45 + 0.55 * (1.0 - sin(u * 3.14159265) * 0.35));
    float shadowA = clamp(spineCrease + sheetTilt, 0.0, 0.38);
    // Valid premultiplied black shadow alpha for smooth compositing over paper + ink.
    return half4(0.0, 0.0, 0.0, half(shadowA));
}
"""

internal fun buildWenku8WebChapterUrl(aid: Int, cid: Int): String? {
    if (aid <= 0 || cid <= 0) return null
    return "https://www.wenku8.net/novel/${aid / 1000}/$aid/$cid.htm"
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ReaderResolvedPageSurface(
    resolution: VirtualPageResolution,
    palette: ReaderVisualPalette,
    clockLabel: String,
    contentHighlight: ReaderContentHighlight,
    isActiveViewportPage: Boolean,
    novelTitle: String = "",
    isRtlBunkobon: Boolean = false,
    onIllustrationClicked: (imageUrl: String, imageIndexInChapter: Int) -> Unit,
    onRetryFailedSlot: (cid: Int) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    onOpenChapterInBrowser: (url: String) -> Unit = {},
) {
    when (val slot = resolution.slot) {
        is ChapterWindowSlot.Loaded -> {
            val page = resolution.page ?: slot.layout.pages.firstOrNull()
            if (page != null) {
                ReaderCanvasPage(
                    chapterTitle = "${slot.chapter.volumeTitle} · ${slot.chapter.chapterTitle}".trim(' ', '·'),
                    novelTitle = novelTitle,
                    isRtlBunkobon = isRtlBunkobon,
                    page = page,
                    palette = palette,
                    clockLabel = clockLabel,
                    contentHighlight = contentHighlight,
                    isActiveViewportPage = isActiveViewportPage,
                    tagSuffix = "${resolution.role.name}_${resolution.pageIndexInSlot}",
                    onIllustrationClicked = onIllustrationClicked,
                )
            }
        }

        is ChapterWindowSlot.Loading,
        is ChapterWindowSlot.Unloaded,
        -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag(ReaderTestTags.LOADING_SLOT),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (palette.isEinkMonochrome) {
                        CircularProgressIndicator(color = palette.primaryInkColor)
                    } else {
                        CircularWavyProgressIndicator(color = palette.primaryInkColor)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "正在排版：${slot.chapter.chapterTitle}",
                        color = palette.secondaryInkColor,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        is ChapterWindowSlot.Error -> {
            val webChapterUrl = if (slot.kind == ChapterLoadErrorKind.EMPTY_SERVER_CONTENT) {
                buildWenku8WebChapterUrl(aid = slot.chapter.aid, cid = slot.chapter.cid)
            } else {
                null
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
                    .testTag(ReaderTestTags.ERROR_SLOT),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = slot.chapter.chapterTitle,
                        color = palette.primaryInkColor,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = slot.message,
                        color = palette.secondaryInkColor,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (webChapterUrl != null) {
                            Button(
                                onClick = { onOpenChapterInBrowser(webChapterUrl) },
                                modifier = Modifier.testTag(ReaderTestTags.ERROR_OPEN_WEB_BUTTON),
                            ) {
                                Text(
                                    text = "网页端阅读",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            OutlinedButton(onClick = { onRetryFailedSlot(slot.chapter.cid) }) {
                                Text(
                                    text = "重试加载",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        } else {
                            Button(onClick = { onRetryFailedSlot(slot.chapter.cid) }) {
                                Text(
                                    text = "重试加载",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            OutlinedButton(onClick = onOpenNetworkDiagnostics) {
                                Text(
                                    text = "网络检测",
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Hardware-accelerated single-page Bunkobon renderer drawing text/vertical columns on Android
 * `Canvas` and overlaying Coil 3 `AsyncImage` slots for inline & full-bleed illustrations.
 */
@Composable
private fun ReaderCanvasPage(
    chapterTitle: String,
    novelTitle: String = "",
    isRtlBunkobon: Boolean = false,
    page: ReaderPage,
    palette: ReaderVisualPalette,
    clockLabel: String,
    contentHighlight: ReaderContentHighlight,
    isActiveViewportPage: Boolean,
    tagSuffix: String,
    onIllustrationClicked: (imageUrl: String, imageIndexInChapter: Int) -> Unit,
) {
    val density = LocalDensity.current
    val progressPercent = remember(page.pageIndexInChapter, page.totalPagesInChapter) {
        if (page.totalPagesInChapter <= 1) {
            100
        } else {
            (((page.pageIndexInChapter + 1) * 100f) / page.totalPagesInChapter.toFloat()).roundToInt()
        }
    }

    val hasTtsHighlightOnPage = remember(page, contentHighlight.ttsCid, contentHighlight.ttsBlockIndex) {
        page.cid == contentHighlight.ttsCid &&
            contentHighlight.ttsBlockIndex >= 0 &&
            page.elements.any { el ->
                el !is ReaderPageElement.ImageSlot && el.blockIndex == contentHighlight.ttsBlockIndex
            }
    }

    val hasFocalHighlightOnPage = remember(page, contentHighlight.focalCid, contentHighlight.focalBlockIndex) {
        page.cid == contentHighlight.focalCid &&
            contentHighlight.focalBlockIndex >= 0 &&
            page.elements.any { el ->
                el !is ReaderPageElement.ImageSlot && el.blockIndex == contentHighlight.focalBlockIndex
            }
    }

    val accessibilitySummary = remember(chapterTitle, page) {
        val textPreview = buildString {
            append(chapterTitle)
            append(" 第 ")
            if (page.isDualColumnSpread &&
                !page.isFullBleedArtbookPage &&
                page.logicalEndPageIndex > page.logicalStartPageIndex
            ) {
                append(page.logicalStartPageIndex + 1)
                append("–")
                append(page.logicalEndPageIndex + 1)
                append(" / ")
                append(page.totalLogicalPagesInChapter)
            } else if (page.isDualColumnSpread) {
                append(page.logicalStartPageIndex + 1)
                append(" / ")
                append(page.totalLogicalPagesInChapter)
            } else {
                append(page.pageIndexInChapter + 1)
                append(" / ")
                append(page.totalPagesInChapter)
            }
            append(" 页 ")
            for (element in page.elements) {
                when (element) {
                    is ReaderPageElement.TitleLine -> append(element.text).append(' ')
                    is ReaderPageElement.TextLine -> append(element.text)
                    is ReaderPageElement.VerticalColumn -> append(element.verticalText)
                    is ReaderPageElement.ImageSlot -> append("[插图#${element.imageIndexInChapter + 1}]")
                }
                if (length > 260) break
            }
        }
        textPreview
    }

    val bodyPaint = remember(palette.primaryInkColor, palette.customTypeface) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.SUBPIXEL_TEXT_FLAG).apply {
            color = palette.primaryInkColor.toArgb()
            palette.customTypeface?.let { typeface = it }
        }
    }
    val titlePaint = remember(palette.primaryInkColor, palette.customTypeface) {
        AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG or AndroidPaint.SUBPIXEL_TEXT_FLAG).apply {
            color = palette.primaryInkColor.toArgb()
            isFakeBoldText = true
            palette.customTypeface?.let { typeface = it }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag(ReaderTestTags.PAGE_CANVAS_PREFIX + tagSuffix)
            .semantics { contentDescription = accessibilitySummary },
    ) {
        if (isActiveViewportPage && page.isDualColumnSpread && !page.isFullBleedArtbookPage) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .align(Alignment.TopCenter)
                    .testTag(ReaderTestTags.DUAL_COLUMN_SPINE_DIVIDER),
            )
        }
        if (isActiveViewportPage && hasTtsHighlightOnPage) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .testTag("${ReaderTestTags.TTS_HIGHLIGHTED_BLOCK_PREFIX}${contentHighlight.ttsBlockIndex}"),
            )
        }
        if (isActiveViewportPage && hasFocalHighlightOnPage) {
            Box(
                modifier = Modifier
                    .size(1.dp)
                    .testTag("${ReaderTestTags.FOCAL_HIGHLIGHTED_BLOCK_PREFIX}${contentHighlight.focalBlockIndex}"),
            )
        }

        // Running header & footer captions (suppressed on full-bleed dedicated Artbook plates).
        if (!page.isFullBleedArtbookPage) {
            if (page.isDualColumnSpread) {
                val leftHeader = if (isRtlBunkobon) {
                    chapterTitle
                } else {
                    novelTitle.ifBlank { chapterTitle }
                }
                val rightHeader = if (isRtlBunkobon) {
                    novelTitle.ifBlank { chapterTitle }
                } else {
                    chapterTitle
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = leftHeader,
                        color = palette.secondaryInkColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(modifier = Modifier.width(36.dp))
                    Text(
                        text = rightHeader,
                        color = palette.secondaryInkColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                    )
                }

                val firstLogical = page.logicalStartPageIndex + 1
                val secondLogical = if (page.hasSecondaryColumnContent) {
                    page.logicalEndPageIndex + 1
                } else {
                    null
                }
                val totalLogical = page.totalLogicalPagesInChapter.coerceAtLeast(1)
                val leftFooterText = if (isRtlBunkobon) {
                    secondLogical?.let { "$it / $totalLogical" } ?: ""
                } else {
                    "$firstLogical / $totalLogical"
                }
                val rightPageNumText = if (isRtlBunkobon) {
                    "$firstLogical / $totalLogical ($progressPercent%)"
                } else if (secondLogical != null) {
                    "$secondLogical / $totalLogical ($progressPercent%)"
                } else {
                    "($progressPercent%)"
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = leftFooterText,
                        color = palette.secondaryInkColor,
                        fontSize = 11.sp,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = rightPageNumText,
                            color = palette.secondaryInkColor,
                            fontSize = 11.sp,
                        )
                        Text(
                            text = clockLabel,
                            color = palette.secondaryInkColor,
                            fontSize = 11.sp,
                            modifier = if (isActiveViewportPage) {
                                Modifier.testTag(ReaderTestTags.READER_CLOCK_LABEL)
                            } else {
                                Modifier
                            },
                        )
                    }
                }
            } else {
                Text(
                    text = chapterTitle,
                    color = palette.secondaryInkColor,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${page.pageIndexInChapter + 1} / ${page.totalPagesInChapter} ($progressPercent%)",
                        color = palette.secondaryInkColor,
                        fontSize = 11.sp,
                    )
                    Text(
                        text = clockLabel,
                        color = palette.secondaryInkColor,
                        fontSize = 11.sp,
                        modifier = if (isActiveViewportPage) {
                            Modifier.testTag(ReaderTestTags.READER_CLOCK_LABEL)
                        } else {
                            Modifier
                        },
                    )
                }
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val accentBarWidthPx = 3.dp.toPx()
            val highlightCorner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            val keywordCorner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

            // Subtle physical open-book spine binding crease on dual-column prose spreads.
            if (page.isDualColumnSpread && !page.isFullBleedArtbookPage && size.width > 4f) {
                val spineX = page.spineCenterXPx.coerceIn(2f, size.width - 2f)
                if (palette.isEinkMonochrome) {
                    drawLine(
                        color = Color(0xFFCCCCCC),
                        start = Offset(spineX, 0f),
                        end = Offset(spineX, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                } else {
                    val creaseHalfWidth = 18.dp.toPx().coerceAtMost(size.width * 0.12f)
                    val creaseAlpha = if (palette.isDarkPalette) 0.22f else 0.09f
                    val shadowColor = Color.Black.copy(alpha = creaseAlpha)
                    val leftStart = (spineX - creaseHalfWidth).coerceAtLeast(0f)
                    val rightEnd = (spineX + creaseHalfWidth).coerceAtMost(size.width)
                    if (spineX > leftStart) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, shadowColor),
                                startX = leftStart,
                                endX = spineX,
                            ),
                            topLeft = Offset(leftStart, 0f),
                            size = Size(spineX - leftStart, size.height),
                        )
                    }
                    if (rightEnd > spineX) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(shadowColor, Color.Transparent),
                                startX = spineX,
                                endX = rightEnd,
                            ),
                            topLeft = Offset(spineX, 0f),
                            size = Size(rightEnd - spineX, size.height),
                        )
                    }
                    drawLine(
                        color = palette.secondaryInkColor.copy(alpha = if (palette.isDarkPalette) 0.22f else 0.14f),
                        start = Offset(spineX, 0f),
                        end = Offset(spineX, size.height),
                        strokeWidth = 1.dp.toPx(),
                    )
                }
            }

            for (element in page.elements) {
                when (element) {
                    is ReaderPageElement.TitleLine -> {
                        titlePaint.textSize = element.fontSizePx
                        titlePaint.letterSpacing = 0f
                        drawHorizontalLineHighlights(
                            pageCid = page.cid,
                            blockIndex = element.blockIndex,
                            rawStartChar = element.rawStartChar,
                            lineText = element.text,
                            leadingIndentChars = 0,
                            xPx = element.xPx,
                            topYPx = element.topYPx,
                            heightPx = element.heightPx,
                            paint = titlePaint,
                            palette = palette,
                            highlight = contentHighlight,
                            accentBarWidthPx = accentBarWidthPx,
                            lineCorner = highlightCorner,
                            keywordCorner = keywordCorner,
                        )
                        nativeCanvas.drawText(
                            element.text,
                            element.xPx,
                            element.baselineYPx,
                            titlePaint,
                        )
                    }

                    is ReaderPageElement.TextLine -> {
                        bodyPaint.textSize = element.fontSizePx
                        // Android Paint.letterSpacing is measured in `em` units (`px / textSize`).
                        bodyPaint.letterSpacing = if (element.fontSizePx > 0f) {
                            element.extraLetterSpacingPx / element.fontSizePx
                        } else {
                            0f
                        }
                        val leadingIndent = if (element.isParagraphFirstLine) {
                            element.text.takeWhile { it == '\u3000' }.length
                        } else {
                            0
                        }
                        drawHorizontalLineHighlights(
                            pageCid = page.cid,
                            blockIndex = element.blockIndex,
                            rawStartChar = element.rawStartChar,
                            lineText = element.text,
                            leadingIndentChars = leadingIndent,
                            xPx = element.xPx,
                            topYPx = element.topYPx,
                            heightPx = element.heightPx,
                            paint = bodyPaint,
                            palette = palette,
                            highlight = contentHighlight,
                            accentBarWidthPx = accentBarWidthPx,
                            lineCorner = highlightCorner,
                            keywordCorner = keywordCorner,
                        )
                        nativeCanvas.drawText(
                            element.text,
                            element.xPx,
                            element.baselineYPx,
                            bodyPaint,
                        )
                    }

                    is ReaderPageElement.VerticalColumn -> {
                        val paint = if (element.isTitleColumn) titlePaint else bodyPaint
                        paint.textSize = element.fontSizePx
                        paint.letterSpacing = 0f

                        val colHalfW = element.fontSizePx * 0.72f
                        val colTop = element.startYPx - element.fontSizePx * 0.90f
                        val colHeight = (element.verticalText.length * element.cellAdvanceYPx +
                            element.fontSizePx * 0.16f).coerceAtLeast(element.fontSizePx)

                        if (page.cid == contentHighlight.ttsCid &&
                            element.blockIndex == contentHighlight.ttsBlockIndex
                        ) {
                            drawRoundRect(
                                color = palette.ttsParagraphBgColor,
                                topLeft = Offset(element.centerXPx - colHalfW, colTop),
                                size = Size(colHalfW * 2f, colHeight),
                                cornerRadius = highlightCorner,
                            )
                            drawRect(
                                color = palette.ttsAccentBarColor,
                                topLeft = Offset(element.centerXPx - colHalfW, colTop - accentBarWidthPx),
                                size = Size(colHalfW * 2f, accentBarWidthPx),
                            )
                        }

                        if (page.cid == contentHighlight.focalCid &&
                            element.blockIndex == contentHighlight.focalBlockIndex
                        ) {
                            drawRoundRect(
                                color = palette.focalParagraphBgColor,
                                topLeft = Offset(element.centerXPx - colHalfW, colTop),
                                size = Size(colHalfW * 2f, colHeight),
                                cornerRadius = highlightCorner,
                            )
                            if (contentHighlight.focalStartChar >= 0 &&
                                contentHighlight.focalEndCharExclusive > contentHighlight.focalStartChar
                            ) {
                                val leadingIndent = if (!element.isTitleColumn && element.rawStartChar == 0) {
                                    element.verticalText.takeWhile { it == '\u3000' }.length
                                } else {
                                    0
                                }
                                val rawLenInCol = (element.verticalText.length - leadingIndent).coerceAtLeast(0)
                                val colRawStart = element.rawStartChar
                                val colRawEndExclusive = colRawStart + rawLenInCol
                                val ovStart = maxOf(contentHighlight.focalStartChar, colRawStart)
                                val ovEnd = minOf(contentHighlight.focalEndCharExclusive, colRawEndExclusive)
                                if (ovEnd > ovStart) {
                                    val c0 = (leadingIndent + (ovStart - colRawStart))
                                        .coerceIn(0, element.verticalText.length)
                                    val c1 = (leadingIndent + (ovEnd - colRawStart))
                                        .coerceIn(c0, element.verticalText.length)
                                    val y0 = element.startYPx + c0 * element.cellAdvanceYPx - element.fontSizePx * 0.88f
                                    val yH = ((c1 - c0) * element.cellAdvanceYPx + element.fontSizePx * 0.12f)
                                        .coerceAtLeast(element.fontSizePx)
                                    drawRoundRect(
                                        color = palette.focalKeywordBgColor,
                                        topLeft = Offset(element.centerXPx - colHalfW * 0.94f, y0),
                                        size = Size(colHalfW * 1.88f, yH),
                                        cornerRadius = keywordCorner,
                                    )
                                }
                            }
                        }

                        var cellY = element.startYPx
                        for (index in element.verticalText.indices) {
                            val glyph = element.verticalText.substring(index, index + 1)
                            val glyphW = paint.measureText(glyph).takeIf { it > 0f }
                                ?: element.fontSizePx
                            nativeCanvas.drawText(
                                glyph,
                                element.centerXPx - glyphW * 0.5f,
                                cellY,
                                paint,
                            )
                            cellY += element.cellAdvanceYPx
                        }
                    }

                    is ReaderPageElement.ImageSlot -> {
                        // Rendered by interactive AsyncImage overlay below.
                    }
                }
            }
        }

        // Inline / full-bleed `<!--image-->` illustration overlays.
        // Single taps pass through to the reader's nine-zone tap / HUD toggle handler;
        // the Material 3 bottom-right corner button or a long press opens the lightbox.
        for (element in page.elements) {
            if (element is ReaderPageElement.ImageSlot) {
                val widthDp = with(density) { element.widthPx.toDp() }
                val heightDp = with(density) { element.heightPx.toDp() }
                ReaderInlineIllustrationCard(
                    imageUrl = element.imageUrl,
                    imageIndexInChapter = element.imageIndexInChapter,
                    palette = palette,
                    shape = MaterialTheme.shapes.small,
                    surfaceAlpha = 0.4f,
                    onOpenLightbox = {
                        onIllustrationClicked(element.imageUrl, element.imageIndexInChapter)
                    },
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = element.leftPx.roundToInt(),
                                y = element.topPx.roundToInt(),
                            )
                        }
                        .size(width = widthDp, height = heightDp),
                )
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawHorizontalLineHighlights(
    pageCid: Int,
    blockIndex: Int,
    rawStartChar: Int,
    lineText: String,
    leadingIndentChars: Int,
    xPx: Float,
    topYPx: Float,
    heightPx: Float,
    paint: AndroidPaint,
    palette: ReaderVisualPalette,
    highlight: ReaderContentHighlight,
    accentBarWidthPx: Float,
    lineCorner: CornerRadius,
    keywordCorner: CornerRadius,
) {
    val isTtsBlock = pageCid == highlight.ttsCid && blockIndex == highlight.ttsBlockIndex
    val isFocalBlock = pageCid == highlight.focalCid && blockIndex == highlight.focalBlockIndex
    if (!isTtsBlock && !isFocalBlock) return

    val lineWidthPx = paint.measureText(lineText).coerceAtLeast(paint.textSize)

    if (isTtsBlock) {
        drawRoundRect(
            color = palette.ttsParagraphBgColor,
            topLeft = Offset((xPx - 4f).coerceAtLeast(0f), topYPx - 2f),
            size = Size(lineWidthPx + 8f, heightPx + 4f),
            cornerRadius = lineCorner,
        )
        drawRect(
            color = palette.ttsAccentBarColor,
            topLeft = Offset((xPx - 8f).coerceAtLeast(0f), topYPx),
            size = Size(accentBarWidthPx, heightPx),
        )
    }

    if (isFocalBlock) {
        drawRoundRect(
            color = palette.focalParagraphBgColor,
            topLeft = Offset((xPx - 4f).coerceAtLeast(0f), topYPx - 2f),
            size = Size(lineWidthPx + 8f, heightPx + 4f),
            cornerRadius = lineCorner,
        )
        drawRect(
            color = palette.focalAccentBarColor,
            topLeft = Offset((xPx - 8f).coerceAtLeast(0f), topYPx),
            size = Size(accentBarWidthPx, heightPx),
        )

        if (highlight.focalStartChar >= 0 && highlight.focalEndCharExclusive > highlight.focalStartChar) {
            val rawLenInLine = (lineText.length - leadingIndentChars).coerceAtLeast(0)
            val lineRawEndExclusive = rawStartChar + rawLenInLine
            val ovStart = maxOf(highlight.focalStartChar, rawStartChar)
            val ovEnd = minOf(highlight.focalEndCharExclusive, lineRawEndExclusive)
            if (ovEnd > ovStart) {
                val subStart = (leadingIndentChars + (ovStart - rawStartChar)).coerceIn(0, lineText.length)
                val subEnd = (leadingIndentChars + (ovEnd - rawStartChar)).coerceIn(subStart, lineText.length)
                val startOffsetX = if (subStart > 0) paint.measureText(lineText, 0, subStart) else 0f
                val endOffsetX = paint.measureText(lineText, 0, subEnd)
                val spanWidth = (endOffsetX - startOffsetX).coerceAtLeast(paint.textSize * 0.45f)
                drawRoundRect(
                    color = palette.focalKeywordBgColor,
                    topLeft = Offset(xPx + startOffsetX - 2f, topYPx - 1f),
                    size = Size(spanWidth + 4f, heightPx + 2f),
                    cornerRadius = keywordCorner,
                )
                drawRect(
                    color = palette.focalAccentBarColor,
                    topLeft = Offset(xPx + startOffsetX, topYPx + heightPx - 2f),
                    size = Size(spanWidth, 2f),
                )
            }
        }
    }
}

/**
 * Mode 4 (`上下卷轴`): Continuous vertical scrolling delegate rendering structured `ChapterBlock`s
 * with live clock/progress status, imperative scrubber/search/bookmark/TTS jumps, and inline
 * paragraph + keyword highlighting.
 */
@Composable
private fun ContinuousScrollReaderDelegate(
    snapshot: ReaderWindowSnapshot,
    palette: ReaderVisualPalette,
    clockLabel: String,
    contentHighlight: ReaderContentHighlight,
    jumpCommand: ContinuousScrollJumpCommand?,
    contentSafeInsets: WindowInsets,
    fontSizeSp: Int,
    lineDistanceDp: Int,
    paragraphDistanceDp: Int,
    pageEdgeDistanceDp: Int,
    onToggleHud: () -> Unit,
    onIllustrationClicked: (imageUrl: String, imageIndexInChapter: Int) -> Unit,
    onAnchorChanged: (ReadingAnchor, Float) -> Unit,
    onRequestNextChapter: () -> Unit,
    onRequestPrevChapter: () -> Unit,
    onRetryFailedSlot: (cid: Int) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    onOpenChapterInBrowser: (url: String) -> Unit = {},
) {
    val currentSlot = snapshot.currentSlot
    if (currentSlot !is ChapterWindowSlot.Loaded) {
        val fallbackRes = snapshot.resolveVirtualPage(snapshot.activeVirtualPageIndex)
        if (fallbackRes != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(contentSafeInsets),
            ) {
                ReaderResolvedPageSurface(
                    resolution = fallbackRes,
                    palette = palette,
                    clockLabel = clockLabel,
                    contentHighlight = contentHighlight,
                    isActiveViewportPage = true,
                    onIllustrationClicked = onIllustrationClicked,
                    onRetryFailedSlot = onRetryFailedSlot,
                    onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                    onOpenChapterInBrowser = onOpenChapterInBrowser,
                )
            }
        }
        return
    }

    val ast = currentSlot.layout.ast
    val blockItemOffset = if (snapshot.prevSlot != null) 1 else 0
    val initialItemIndex = remember(ast.cid) {
        val commandTarget = jumpCommand?.takeIf { it.cid == ast.cid }?.paragraphIndex
        val anchorBlock = commandTarget ?: snapshot.activeReaderPage?.startAnchor?.paragraphIndex ?: 0
        val blockPos = ast.blocks.indexOfFirst { it.blockIndex >= anchorBlock }
            .let { if (it >= 0) it else 0 }
        (blockItemOffset + blockPos).coerceAtLeast(0)
    }
    val listState = key(ast.cid) {
        rememberLazyListState(initialFirstVisibleItemIndex = initialItemIndex)
    }
    val customFontFamily = remember(palette.customTypeface) {
        when {
            palette.customTypeface === Typeface.SERIF -> FontFamily.Serif
            palette.customTypeface != null -> FontFamily(palette.customTypeface)
            else -> FontFamily.SansSerif
        }
    }

    // Execute programmatic jumps from HUD scrubber, In-Book Search, Bookmarks, Undo Pill, Volume Keys, and TTS!
    LaunchedEffect(jumpCommand?.revision, ast.cid, blockItemOffset) {
        val cmd = jumpCommand ?: return@LaunchedEffect
        if (cmd.cid != ast.cid || ast.blocks.isEmpty()) return@LaunchedEffect
        val blockPos = ast.blocks.indexOfFirst { it.blockIndex >= cmd.paragraphIndex }
            .let { if (it >= 0) it else ast.blocks.lastIndex }
        val targetItemIndex = (blockItemOffset + blockPos).coerceAtLeast(0)
        if (cmd.animated) {
            runCatching { listState.animateScrollToItem(targetItemIndex) }
                .onFailure { listState.scrollToItem(targetItemIndex) }
        } else {
            listState.scrollToItem(targetItemIndex)
        }
    }

    val currentBlockPosAndProgress by remember(ast, listState, blockItemOffset) {
        derivedStateOf {
            if (ast.blocks.isEmpty()) {
                0 to 1f
            } else {
                val reachedBottom = !listState.canScrollForward && listState.canScrollBackward
                val blockPos = if (reachedBottom) {
                    ast.blocks.lastIndex
                } else {
                    (listState.firstVisibleItemIndex - blockItemOffset).coerceIn(0, ast.blocks.lastIndex)
                }
                val progress = if (ast.blocks.size <= 1) {
                    1f
                } else {
                    (blockPos.toFloat() / (ast.blocks.size - 1).toFloat()).coerceIn(0f, 1f)
                }
                blockPos to progress
            }
        }
    }

    LaunchedEffect(ast.cid, listState, blockItemOffset) {
        snapshotFlow { currentBlockPosAndProgress }
            .distinctUntilChanged()
            .collect { (blockPos, progress) ->
                val block = ast.blocks.getOrNull(blockPos)
                if (block != null) {
                    val permille = (progress * 1000f).roundToInt().coerceIn(0, 1000)
                    onAnchorChanged(
                        ReadingAnchor(
                            paragraphIndex = block.blockIndex,
                            charOffset = 0,
                            progressPermille = permille,
                        ),
                        progress,
                    )
                }
            }
    }

    val chapterHeaderTitle = remember(currentSlot.chapter) {
        "${currentSlot.chapter.volumeTitle} · ${currentSlot.chapter.chapterTitle}".trim(' ', '·')
    }
    val progressPercent = ((currentBlockPosAndProgress.second * 100f).roundToInt()).coerceIn(0, 100)

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(contentSafeInsets)
            .pointerInput(onToggleHud) {
                detectTapGestures { onToggleHud() }
            },
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = pageEdgeDistanceDp.coerceAtLeast(12).dp,
                end = pageEdgeDistanceDp.coerceAtLeast(12).dp,
                top = 30.dp,
                bottom = 34.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(paragraphDistanceDp.coerceAtLeast(6).dp),
            modifier = Modifier
                .widthIn(max = CONTINUOUS_SCROLL_MAX_READABLE_WIDTH_DP)
                .fillMaxSize()
                .testTag(ReaderTestTags.CONTINUOUS_SCROLL_LIST),
        ) {
            if (snapshot.prevSlot != null) {
                item(key = "prev_chapter_btn_${ast.cid}") {
                    OutlinedButton(
                        onClick = onRequestPrevChapter,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "▲ 上一章：${snapshot.prevSlot.chapter.chapterTitle}",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            items(
                items = ast.blocks,
                key = { block -> "${ast.cid}_${block.blockIndex}" },
            ) { block ->
                val isTtsHighlighted = ast.cid == contentHighlight.ttsCid &&
                    block.blockIndex == contentHighlight.ttsBlockIndex
                val isFocalHighlighted = ast.cid == contentHighlight.focalCid &&
                    block.blockIndex == contentHighlight.focalBlockIndex

                val blockHighlightModifier = Modifier
                    .let { base ->
                        if (isTtsHighlighted || isFocalHighlighted) {
                            val bg = if (isTtsHighlighted) {
                                palette.ttsParagraphBgColor
                            } else {
                                palette.focalParagraphBgColor
                            }
                            val barColor = if (isTtsHighlighted) {
                                palette.ttsAccentBarColor
                            } else {
                                palette.focalAccentBarColor
                            }
                            base
                                .clip(MaterialTheme.shapes.extraSmall)
                                .background(bg)
                                .drawBehind {
                                    drawRect(
                                        color = barColor,
                                        topLeft = Offset.Zero,
                                        size = Size(3.dp.toPx(), size.height),
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        } else {
                            base
                        }
                    }
                    .let { mod ->
                        if (isTtsHighlighted) {
                            mod.testTag("${ReaderTestTags.TTS_HIGHLIGHTED_BLOCK_PREFIX}${block.blockIndex}")
                        } else {
                            mod
                        }
                    }
                    .let { mod ->
                        if (isFocalHighlighted && !isTtsHighlighted) {
                            mod.testTag("${ReaderTestTags.FOCAL_HIGHLIGHTED_BLOCK_PREFIX}${block.blockIndex}")
                        } else {
                            mod
                        }
                    }

                Box(modifier = Modifier.fillMaxWidth()) {
                    if (isTtsHighlighted && isFocalHighlighted) {
                        // Emit secondary marker when both TTS and Search/Bookmark target the exact same block.
                        Box(
                            modifier = Modifier
                                .size(1.dp)
                                .testTag("${ReaderTestTags.FOCAL_HIGHLIGHTED_BLOCK_PREFIX}${block.blockIndex}"),
                        )
                    }
                    when (block) {
                        is ChapterBlock.TitleBlock -> {
                            val annotatedTitle = buildHighlightedScrollText(
                                displayText = block.title,
                                indentLength = 0,
                                isFocalBlock = isFocalHighlighted,
                                focalStartChar = contentHighlight.focalStartChar,
                                focalEndCharExclusive = contentHighlight.focalEndCharExclusive,
                                palette = palette,
                            )
                            Text(
                                text = annotatedTitle,
                                color = palette.primaryInkColor,
                                fontSize = (fontSizeSp + 4).sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = customFontFamily,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp, bottom = 8.dp)
                                    .then(blockHighlightModifier),
                            )
                        }

                        is ChapterBlock.ParagraphBlock -> {
                            val annotatedBody = buildHighlightedScrollText(
                                displayText = block.formattedText,
                                indentLength = block.indentLength,
                                isFocalBlock = isFocalHighlighted,
                                focalStartChar = contentHighlight.focalStartChar,
                                focalEndCharExclusive = contentHighlight.focalEndCharExclusive,
                                palette = palette,
                            )
                            Text(
                                text = annotatedBody,
                                color = palette.primaryInkColor,
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp + lineDistanceDp).sp,
                                fontFamily = customFontFamily,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(blockHighlightModifier),
                            )
                        }

                        is ChapterBlock.IllustrationBlock -> {
                            val cachedRatio = ReaderIllustrationAspectRatioCache.observe(block.imageUrl)
                            var localResolvedRatio by remember(block.imageUrl) { mutableStateOf<Float?>(null) }
                            val ratio = (localResolvedRatio ?: cachedRatio ?: block.cachedAspectRatio ?: 0.71f)
                                .coerceIn(0.25f, 4.0f)
                            ReaderInlineIllustrationCard(
                                imageUrl = block.imageUrl,
                                imageIndexInChapter = block.imageIndexInChapter,
                                palette = palette,
                                shape = MaterialTheme.shapes.medium,
                                surfaceAlpha = 0.5f,
                                onOpenLightbox = {
                                    onIllustrationClicked(block.imageUrl, block.imageIndexInChapter)
                                },
                                onImageAspectRatioResolved = { aspect ->
                                    localResolvedRatio = aspect
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ratio),
                            )
                        }
                    }
                }
            }

            if (snapshot.nextSlot != null) {
                item(key = "next_chapter_btn_${ast.cid}") {
                    Button(
                        onClick = onRequestNextChapter,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                    ) {
                        Text(
                            text = "下一章：${snapshot.nextSlot.chapter.chapterTitle} ▼",
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Running header & footer captions matching paged Bunkobon modes.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .background(palette.backgroundColor.copy(alpha = 0.86f))
                .fillMaxWidth(),
        ) {
            Text(
                text = chapterHeaderTitle,
                color = palette.secondaryInkColor,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = CONTINUOUS_SCROLL_MAX_READABLE_WIDTH_DP)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(palette.backgroundColor.copy(alpha = 0.86f))
                .fillMaxWidth(),
        ) {
            Row(
                modifier = Modifier
                    .widthIn(max = CONTINUOUS_SCROLL_MAX_READABLE_WIDTH_DP)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "阅读进度 $progressPercent%",
                    color = palette.secondaryInkColor,
                    fontSize = 11.sp,
                )
                Text(
                    text = clockLabel,
                    color = palette.secondaryInkColor,
                    fontSize = 11.sp,
                    modifier = Modifier.testTag(ReaderTestTags.READER_CLOCK_LABEL),
                )
            }
        }
    }
}

internal val CONTINUOUS_SCROLL_MAX_READABLE_WIDTH_DP = 680.dp

internal data class ReaderIllustrationBadgeStyle(
    val containerColor: Color,
    val contentColor: Color,
    val borderColor: Color,
    val outerHaloColor: Color,
    val borderWidth: Dp,
    val shadowElevation: Dp,
)

/**
 * Resolves Material 3 overlay colors for the inline illustration corner lightbox button so it
 * stays unmistakably legible across pure-white illustrations, pure-black illustrations, and
 * monochrome E-Ink panels (zero grey dithering on E-Ink).
 */
internal fun resolveReaderIllustrationBadgeStyle(
    isEinkMode: Boolean,
    einkSurfaceColor: Color,
    einkContentColor: Color,
    einkBorderColor: Color,
): ReaderIllustrationBadgeStyle = if (isEinkMode) {
    ReaderIllustrationBadgeStyle(
        containerColor = einkSurfaceColor,
        contentColor = einkContentColor,
        borderColor = einkBorderColor,
        outerHaloColor = einkSurfaceColor,
        borderWidth = 1.5.dp,
        shadowElevation = 0.dp,
    )
} else {
    ReaderIllustrationBadgeStyle(
        containerColor = Color(0xA812151C),
        contentColor = Color(0xFFF8FAFC),
        borderColor = Color(0x73FFFFFF),
        outerHaloColor = Color.Transparent,
        borderWidth = 1.dp,
        shadowElevation = 3.dp,
    )
}

/**
 * Detects a stationary single-finger long press to open the full-screen Artbook lightbox
 * WITHOUT consuming `down`, drag, or short-tap `up` events so single taps pass straight through
 * to the reader's nine-zone tap / HUD toggle handlers with zero delay (no double-tap gesture).
 */
private fun Modifier.readerIllustrationLongPress(
    key: Any?,
    onLongPress: () -> Unit,
): Modifier = pointerInput(key) {
    awaitEachGesture {
        val down = awaitFirstDown(
            requireUnconsumed = false,
            pass = PointerEventPass.Main,
        )
        if (down.isConsumed) return@awaitEachGesture

        val pointerId = down.id
        val touchSlop = viewConfiguration.touchSlop
        val longPressTimeoutMs = viewConfiguration.longPressTimeoutMillis

        var cancelledOrReleasedBeforeTimeout = false
        withTimeoutOrNull(longPressTimeoutMs) {
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                if (event.changes.count { it.pressed } > 1) {
                    cancelledOrReleasedBeforeTimeout = true
                    break
                }
                val change = event.changes.firstOrNull { it.id == pointerId }
                if (change == null || !change.pressed || change.isConsumed) {
                    cancelledOrReleasedBeforeTimeout = true
                    break
                }
                val dx = change.position.x - down.position.x
                val dy = change.position.y - down.position.y
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    cancelledOrReleasedBeforeTimeout = true
                    break
                }
            }
        }

        if (!cancelledOrReleasedBeforeTimeout) {
            onLongPress()
            while (true) {
                val event = awaitPointerEvent(pass = PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                change.consume()
                if (!change.pressed) break
            }
        }
    }
}

/**
 * Bounded thread-safe in-memory cache of decoded illustration aspect ratios (`width / height`),
 * normalized across Wenku8 image mirror URLs so revisited pages and lightbox-to-reader transitions
 * immediately fit inline illustration frames and corner badges to exact image bounds on Frame 0.
 */
internal object ReaderIllustrationAspectRatioCache {
    private const val MAX_ENTRIES = 256
    private val cache = LruCache<String, Float>(MAX_ENTRIES)
    private var revision by mutableIntStateOf(0)

    private fun normalizeKey(imageUrl: String): String {
        val trimmed = imageUrl.trim()
        if (trimmed.isEmpty()) return ""
        return WenkuImageRequests.canonicalIllustrationCacheKey(trimmed) ?: trimmed
    }

    /**
     * Reactive lookup for Composables: subscribes the caller to cache updates so an already-composed
     * reader page immediately adopts an illustration's true aspect ratio once decoded in the
     * lightbox or another page slot.
     */
    fun observe(imageUrl: String): Float? {
        @Suppress("UNUSED_VARIABLE")
        val currentRevision = revision
        return get(imageUrl)
    }

    fun get(imageUrl: String): Float? {
        val key = normalizeKey(imageUrl)
        if (key.isEmpty()) return null
        return synchronized(cache) { cache.get(key) }
    }

    fun put(imageUrl: String, aspectRatio: Float) {
        val key = normalizeKey(imageUrl)
        if (key.isEmpty() || !aspectRatio.isFinite() || aspectRatio <= 0.05f) return
        val changed = synchronized(cache) {
            val previous = cache.get(key)
            cache.put(key, aspectRatio)
            previous == null || abs(previous - aspectRatio) > 0.001f
        }
        if (changed) {
            revision++
        }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
        revision++
    }
}

/**
 * Resolves the displayed aspect ratio (`width / height`) of a loaded Coil image, preferring
 * [painterIntrinsicSize] (which matches `ContentScale.Fit` exactly) with fallback to raw image
 * dimensions.
 */
internal fun resolveLoadedImageAspectRatio(
    imageWidth: Int,
    imageHeight: Int,
    painterIntrinsicSize: Size,
): Float? {
    if (painterIntrinsicSize.isSpecified &&
        painterIntrinsicSize.width.isFinite() &&
        painterIntrinsicSize.height.isFinite() &&
        painterIntrinsicSize.width > 0f &&
        painterIntrinsicSize.height > 0f
    ) {
        val ratio = painterIntrinsicSize.width / painterIntrinsicSize.height
        if (ratio.isFinite() && ratio > 0.05f) return ratio
    }
    if (imageWidth > 0 && imageHeight > 0) {
        val ratio = imageWidth.toFloat() / imageHeight.toFloat()
        if (ratio.isFinite() && ratio > 0.05f) return ratio
    }
    return null
}

/**
 * Computes the exact pixel dimensions painted by `ContentScale.Fit` inside a container slot of
 * `[containerWidthPx] × [containerHeightPx]` for an image with [imageAspectRatio]. When the image
 * aspect ratio is not yet known (`null` or invalid), falls back to filling the slot.
 */
internal fun computeFittedIllustrationSize(
    containerWidthPx: Float,
    containerHeightPx: Float,
    imageAspectRatio: Float?,
): Size {
    val safeW = containerWidthPx.coerceAtLeast(1f)
    val safeH = containerHeightPx.coerceAtLeast(1f)
    if (imageAspectRatio == null || !imageAspectRatio.isFinite() || imageAspectRatio <= 0.05f) {
        return Size(safeW, safeH)
    }
    val widthByHeight = safeH * imageAspectRatio
    return if (widthByHeight <= safeW) {
        Size(width = widthByHeight.coerceAtLeast(1f), height = safeH)
    } else {
        Size(width = safeW, height = (safeW / imageAspectRatio).coerceAtLeast(1f))
    }
}

@Composable
internal fun ReaderInlineIllustrationCard(
    imageUrl: String,
    imageIndexInChapter: Int,
    palette: ReaderVisualPalette,
    shape: Shape,
    surfaceAlpha: Float,
    onOpenLightbox: () -> Unit,
    modifier: Modifier = Modifier,
    onImageAspectRatioResolved: ((Float) -> Unit)? = null,
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val latestOnOpenLightbox by rememberUpdatedState(onOpenLightbox)
    val latestOnAspectRatioResolved by rememberUpdatedState(onImageAspectRatioResolved)
    val openLightboxLabel = stringResource(R.string.reader_action_open_illustration_lightbox)
    val hasValidUrl = imageUrl.isNotBlank()
    val cachedAspectRatio = ReaderIllustrationAspectRatioCache.observe(imageUrl)
    var localLoadedAspectRatio by remember(imageUrl) { mutableStateOf<Float?>(null) }
    val loadedAspectRatio = localLoadedAspectRatio ?: cachedAspectRatio

    // Outer container preserves the paginator's exact slot bounds while the inner frame shrinks to
    // the image's exact `ContentScale.Fit` rendered size so the bottom-right lightbox badge (and
    // borders / corners) NEVER float outside the actual image into letterbox or pillarbox margins.
    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = modifier,
    ) {
        val fittedSizePx = computeFittedIllustrationSize(
            containerWidthPx = constraints.maxWidth.toFloat(),
            containerHeightPx = constraints.maxHeight.toFloat(),
            imageAspectRatio = loadedAspectRatio,
        )
        val fittedWidthDp = with(density) { fittedSizePx.width.toDp() }
        val fittedHeightDp = with(density) { fittedSizePx.height.toDp() }

        Box(
            modifier = Modifier
                .size(width = fittedWidthDp, height = fittedHeightDp)
                .then(
                    if (hasValidUrl) {
                        Modifier.readerIllustrationLongPress(
                            key = imageUrl to imageIndexInChapter,
                            onLongPress = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                latestOnOpenLightbox()
                            },
                        )
                    } else {
                        Modifier
                    },
                )
                .testTag("${ReaderTestTags.INLINE_IMAGE_PREFIX}$imageIndexInChapter"),
        ) {
            Surface(
                color = palette.backgroundColor.copy(alpha = surfaceAlpha),
                shape = shape,
                modifier = Modifier
                    .fillMaxSize()
                    .einkHairlineBorder(shape)
                    .clip(shape),
            ) {
                if (hasValidUrl) {
                    AsyncImage(
                        model = WenkuImageRequests.rememberIllustrationImageRequest(imageUrl),
                        contentDescription = "插图 ${imageIndexInChapter + 1}",
                        contentScale = ContentScale.Fit,
                        onSuccess = { success ->
                            val ratio = resolveLoadedImageAspectRatio(
                                imageWidth = success.result.image.width,
                                imageHeight = success.result.image.height,
                                painterIntrinsicSize = success.painter.intrinsicSize,
                            )
                            if (ratio != null) {
                                ReaderIllustrationAspectRatioCache.put(imageUrl, ratio)
                                localLoadedAspectRatio = ratio
                                latestOnAspectRatioResolved?.invoke(ratio)
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            if (hasValidUrl) {
                ReaderIllustrationLightboxBadge(
                    contentDescription = openLightboxLabel,
                    onClick = { latestOnOpenLightbox() },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .testTag("${ReaderTestTags.INLINE_IMAGE_LIGHTBOX_BUTTON_PREFIX}$imageIndexInChapter"),
                )
            }
        }
    }
}

@Composable
private fun ReaderIllustrationLightboxBadge(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEinkMode = LocalEinkMode.current
    val badgeStyle = resolveReaderIllustrationBadgeStyle(
        isEinkMode = isEinkMode,
        einkSurfaceColor = MaterialTheme.colorScheme.surface,
        einkContentColor = MaterialTheme.colorScheme.onSurface,
        einkBorderColor = MaterialTheme.colorScheme.outline,
    )
    val interactionSource = remember { MutableInteractionSource() }

    // 48dp Material 3 minimum touch target wrapping a compact 36dp high-contrast badge.
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .requiredSize(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = if (isEinkMode) {
                    null
                } else {
                    ripple(bounded = true, radius = 24.dp, color = badgeStyle.contentColor)
                },
                role = Role.Button,
                onClickLabel = contentDescription,
                onClick = onClick,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .then(
                    if (badgeStyle.outerHaloColor != Color.Transparent) {
                        Modifier
                            .background(badgeStyle.outerHaloColor, CircleShape)
                            .padding(1.5.dp)
                    } else {
                        Modifier
                    },
                ),
        ) {
            Surface(
                shape = CircleShape,
                color = badgeStyle.containerColor,
                contentColor = badgeStyle.contentColor,
                border = BorderStroke(width = badgeStyle.borderWidth, color = badgeStyle.borderColor),
                shadowElevation = badgeStyle.shadowElevation,
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.OpenInFull,
                        contentDescription = contentDescription,
                        tint = badgeStyle.contentColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

private fun buildHighlightedScrollText(
    displayText: String,
    indentLength: Int,
    isFocalBlock: Boolean,
    focalStartChar: Int,
    focalEndCharExclusive: Int,
    palette: ReaderVisualPalette,
): AnnotatedString {
    if (!isFocalBlock || focalStartChar < 0 || focalEndCharExclusive <= focalStartChar) {
        return AnnotatedString(displayText)
    }
    val spanStart = (indentLength + focalStartChar).coerceIn(0, displayText.length)
    val spanEnd = (indentLength + focalEndCharExclusive).coerceIn(spanStart, displayText.length)
    if (spanEnd <= spanStart) {
        return AnnotatedString(displayText)
    }
    return buildAnnotatedString {
        append(displayText)
        addStyle(
            style = SpanStyle(
                background = palette.focalKeywordBgColor,
                color = palette.primaryInkColor,
                fontWeight = FontWeight.Bold,
                textDecoration = TextDecoration.Underline,
            ),
            start = spanStart,
            end = spanEnd,
        )
    }
}
