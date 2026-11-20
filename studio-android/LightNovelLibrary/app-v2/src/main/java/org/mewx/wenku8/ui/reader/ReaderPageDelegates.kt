package org.mewx.wenku8.ui.reader

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Paint as AndroidPaint
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
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
import kotlin.math.abs
import kotlin.math.roundToInt

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
 * Pure, unit-testable 9-zone tap resolver (`10-next-gen-reader-engine-architecture.md` §6):
 * - Top row: `[Prev, Prev, Next]`
 * - Middle row: `[Prev, Toggle HUD, Next]`
 * - Bottom row: `[Next, Next, Next]`
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
): ReaderTapZoneAction {
    if (viewportWidth <= 0f || viewportHeight <= 0f) {
        return ReaderTapZoneAction.TOGGLE_HUD
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
 * E-Ink readers.
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
    )
}

/**
 * Hosts the active page-turning delegate (`1` through `6`) over [ReaderWindowSnapshot].
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
    val clockLabel = rememberSystemClockText()
    val latestOnTapZoneAction by rememberUpdatedState(onTapZoneAction)
    val latestOnBrightnessDragDelta by rememberUpdatedState(onBrightnessDragDelta)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.backgroundColor)
            .testTag(ReaderTestTags.VIEWPORT_CONTAINER)
            .pointerInput(effectiveMode, isRtlBunkobon, pagerState) {
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
                    val pageOffset = (pagerState.currentPage - virtualPageIndex) +
                        pagerState.currentPageOffsetFraction
                    val isActiveViewportPage = virtualPageIndex == pagerState.currentPage

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .applyPageTurnTransition(
                                mode = effectiveMode,
                                virtualPageIndex = virtualPageIndex,
                                pageOffset = pageOffset,
                                paperBackground = palette.backgroundColor,
                                hasCustomDayWallpaper = palette.customDayBackgroundFile != null,
                            ),
                    ) {
                        if (resolution != null) {
                            ReaderResolvedPageSurface(
                                resolution = resolution,
                                palette = palette,
                                clockLabel = clockLabel,
                                contentHighlight = contentHighlight,
                                isActiveViewportPage = isActiveViewportPage,
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

/**
 * Applies the selected official-AndroidX page-turn transition (`CoverSlide`, `SimulationCurl`
 * with API 33+ AGSL `RuntimeShader` + `Canvas` Bezier fallback, `SpringSlide`, `VerticalBunkobon`,
 * or `EInkInstant`).
 */
private fun Modifier.applyPageTurnTransition(
    mode: ReaderPageTurnMode,
    virtualPageIndex: Int,
    pageOffset: Float,
    paperBackground: Color,
    hasCustomDayWallpaper: Boolean,
): Modifier {
    return when (mode) {
        ReaderPageTurnMode.COVER_SLIDE -> {
            val spineShadowWidthDp = 24.dp
            this
                .zIndex(-virtualPageIndex.toFloat())
                .graphicsLayer {
                    // Hide idle off-stage beyondViewportPageCount neighbors so they never bleed through.
                    if (abs(pageOffset) >= 0.999f) {
                        alpha = 0f
                    } else {
                        alpha = 1f
                        // `pageOffset > 0f`: outgoing upper page slides off to the left naturally.
                        // `pageOffset < 0f`: incoming underlying page (`virtualPageIndex > currentPage`) stays pinned!
                        translationX = if (pageOffset < 0f) pageOffset * size.width else 0f
                    }
                }
                .drawWithContent {
                    if (abs(pageOffset) >= 0.999f) return@drawWithContent
                    if (!hasCustomDayWallpaper) {
                        drawRect(color = paperBackground)
                    }
                    drawContent()
                    // Ambient drop shadow cast by the sliding upper page's right edge onto the
                    // pinned underlying page, moving right-to-left (`size.width -> 0f`) in lockstep
                    // with the upper page's edge!
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
                }
        }

        ReaderPageTurnMode.SIMULATION_CURL -> {
            this
                .zIndex(-virtualPageIndex.toFloat())
                .graphicsLayer {
                    if (abs(pageOffset) >= 0.999f) {
                        alpha = 0f
                    } else {
                        alpha = 1f
                        // Both outgoing (`pageOffset > 0f`) and incoming (`pageOffset < 0f`) pages stay
                        // anchored at the book spine while the outgoing page clips & curls inward.
                        translationX = pageOffset * size.width
                    }
                }
                .drawWithContent {
                    if (abs(pageOffset) >= 0.999f) return@drawWithContent
                    if (pageOffset <= 0.002f) {
                        if (!hasCustomDayWallpaper) {
                            drawRect(color = paperBackground)
                        }
                        drawContent()
                    } else {
                        // Outgoing curling page: clip paper & ink together to cylinder fold `foldX`
                        // so the underlying incoming page shows cleanly to the right of `foldX`!
                        val foldX = ((1f - pageOffset) * size.width).coerceIn(0f, size.width)
                        val curlWidth = (size.width * 0.16f * (1f - abs(pageOffset * 2f - 1f)) + 18f)
                            .coerceAtMost(foldX.coerceAtLeast(1f))

                        drawContext.canvas.save()
                        drawContext.canvas.clipRect(0f, 0f, foldX, size.height)
                        if (!hasCustomDayWallpaper) {
                            drawRect(color = paperBackground)
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
                    drawRect(color = paperBackground)
                }
                drawContent()
            }
        }
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
            append(page.pageIndexInChapter + 1)
            append(" / ")
            append(page.totalPagesInChapter)
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

        Canvas(modifier = Modifier.fillMaxSize()) {
            val nativeCanvas = drawContext.canvas.nativeCanvas
            val accentBarWidthPx = 3.dp.toPx()
            val highlightCorner = CornerRadius(4.dp.toPx(), 4.dp.toPx())
            val keywordCorner = CornerRadius(3.dp.toPx(), 3.dp.toPx())

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

        // Interactive inline / full-bleed `<!--image-->` illustration overlays.
        for (element in page.elements) {
            if (element is ReaderPageElement.ImageSlot) {
                val widthDp = with(density) { element.widthPx.toDp() }
                val heightDp = with(density) { element.heightPx.toDp() }
                Surface(
                    onClick = {
                        onIllustrationClicked(element.imageUrl, element.imageIndexInChapter)
                    },
                    color = palette.backgroundColor.copy(alpha = 0.4f),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = element.leftPx.roundToInt(),
                                y = element.topPx.roundToInt(),
                            )
                        }
                        .size(width = widthDp, height = heightDp)
                        .einkHairlineBorder(MaterialTheme.shapes.small)
                        .clip(MaterialTheme.shapes.small)
                        .testTag("${ReaderTestTags.INLINE_IMAGE_PREFIX}${element.imageIndexInChapter}"),
                ) {
                    if (element.imageUrl.isNotBlank()) {
                        AsyncImage(
                            model = WenkuImageRequests.rememberIllustrationImageRequest(element.imageUrl),
                            contentDescription = "插图 ${element.imageIndexInChapter + 1}",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
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

    Box(modifier = Modifier.fillMaxSize()) {
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
                .fillMaxSize()
                .pointerInput(onToggleHud) {
                    detectTapGestures { onToggleHud() }
                }
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
                            val ratio = (block.cachedAspectRatio ?: 0.71f).coerceIn(0.3f, 2.5f)
                            Surface(
                                onClick = {
                                    onIllustrationClicked(block.imageUrl, block.imageIndexInChapter)
                                },
                                color = palette.backgroundColor.copy(alpha = 0.5f),
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(ratio)
                                    .einkHairlineBorder(MaterialTheme.shapes.medium)
                                    .clip(MaterialTheme.shapes.medium)
                                    .testTag("${ReaderTestTags.INLINE_IMAGE_PREFIX}${block.imageIndexInChapter}"),
                            ) {
                                if (block.imageUrl.isNotBlank()) {
                                    AsyncImage(
                                        model = WenkuImageRequests.rememberIllustrationImageRequest(block.imageUrl),
                                        contentDescription = "插图 ${block.imageIndexInChapter + 1}",
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
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
        Text(
            text = chapterHeaderTitle,
            color = palette.secondaryInkColor,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .background(palette.backgroundColor.copy(alpha = 0.86f))
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        )
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(palette.backgroundColor.copy(alpha = 0.86f))
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
