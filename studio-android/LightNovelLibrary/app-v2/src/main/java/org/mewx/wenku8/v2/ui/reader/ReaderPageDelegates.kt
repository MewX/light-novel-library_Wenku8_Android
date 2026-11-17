package org.mewx.wenku8.v2.ui.reader

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
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import org.mewx.wenku8.v2.data.media.WenkuImageRequests
import org.mewx.wenku8.v2.reader.engine.ChapterBlock
import org.mewx.wenku8.v2.reader.engine.ChapterWindowSlot
import org.mewx.wenku8.v2.reader.engine.ReaderPage
import org.mewx.wenku8.v2.reader.engine.ReaderPageElement
import org.mewx.wenku8.v2.reader.engine.ReaderWindowSnapshot
import org.mewx.wenku8.v2.reader.engine.ReadingAnchor
import org.mewx.wenku8.v2.reader.engine.VirtualPageResolution
import org.mewx.wenku8.v2.reader.model.ReaderPageTurnMode
import org.mewx.wenku8.v2.reader.model.ReaderPaperPreset
import org.mewx.wenku8.v2.ui.theme.LocalEinkMode
import org.mewx.wenku8.v2.ui.theme.einkHairlineBorder
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

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
)

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
    val typeface = if (customFontPath.isNotBlank()) {
        val fontFile = File(customFontPath)
        if (fontFile.isFile && fontFile.length() > 0L) {
            runCatching { Typeface.createFromFile(fontFile) }.getOrNull()
        } else {
            null
        }
    } else {
        null
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
    onTapZoneAction: (ReaderTapZoneAction) -> Unit,
    onBrightnessDragDelta: (Float) -> Unit,
    onIllustrationClicked: (imageUrl: String, imageIndexInChapter: Int) -> Unit,
    onContinuousScrollAnchorChanged: (ReadingAnchor, Float) -> Unit,
    onRequestNextChapterInContinuousMode: () -> Unit,
    onRequestPrevChapterInContinuousMode: () -> Unit,
    onRetryFailedSlot: (cid: Int) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.backgroundColor)
            .testTag(ReaderTestTags.VIEWPORT_CONTAINER)
            .pointerInput(effectiveMode, isRtlBunkobon) {
                if (effectiveMode != ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                    detectTapGestures { offset ->
                        val action = resolveNineZoneTapAction(
                            tapX = offset.x,
                            tapY = offset.y,
                            viewportWidth = size.width.toFloat(),
                            viewportHeight = size.height.toFloat(),
                            isRtlBook = isRtlBunkobon,
                        )
                        onTapZoneAction(action)
                    }
                }
            }
            .pointerInput(leftEdgeBrightnessDragEnabled, density.density) {
                if (leftEdgeBrightnessDragEnabled) {
                    var dragActiveInSafeStrip = false
                    detectVerticalDragGestures(
                        onDragStart = { startOffset ->
                            dragActiveInSafeStrip = isWithinPredictiveBackSafeBrightnessStrip(
                                touchXPx = startOffset.x,
                                densityScale = density.density,
                            )
                        },
                        onDragEnd = { dragActiveInSafeStrip = false },
                        onDragCancel = { dragActiveInSafeStrip = false },
                        onVerticalDrag = { change, dragAmount ->
                            if (dragActiveInSafeStrip && size.height > 0) {
                                change.consume()
                                // Dragging upward (`dragAmount < 0`) increases brightness.
                                val deltaFraction = (-dragAmount / (size.height * 0.65f))
                                onBrightnessDragDelta(deltaFraction)
                            }
                        },
                    )
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
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(ReaderTestTags.PAGED_HORIZONTAL_PAGER),
                ) { virtualPageIndex ->
                    val resolution = snapshot.resolveVirtualPage(virtualPageIndex)
                    val pageOffset = (pagerState.currentPage - virtualPageIndex) +
                        pagerState.currentPageOffsetFraction

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
                                onIllustrationClicked = onIllustrationClicked,
                                onRetryFailedSlot = onRetryFailedSlot,
                                onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
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
                        if (pageOffset < 0f) {
                            translationX = pageOffset * size.width
                        }
                    }
                }
                .drawWithContent {
                    if (abs(pageOffset) >= 0.999f) return@drawWithContent
                    if (!hasCustomDayWallpaper) {
                        drawRect(color = paperBackground)
                    }
                    drawContent()
                    // Ambient spine shadow cast onto the underlying incoming page.
                    if (pageOffset < -0.001f && pageOffset > -0.999f) {
                        val shadowPx = spineShadowWidthDp.toPx()
                        val revealEdgeX = (1f + pageOffset) * size.width
                        val shadowAlpha = (abs(pageOffset) * 0.28f).coerceIn(0f, 0.28f)
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = shadowAlpha),
                                    Color.Transparent,
                                ),
                                startX = revealEdgeX,
                                endX = (revealEdgeX + shadowPx).coerceAtMost(size.width),
                            ),
                            topLeft = Offset(revealEdgeX, 0f),
                            size = Size(shadowPx.coerceAtMost(size.width - revealEdgeX), size.height),
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ReaderResolvedPageSurface(
    resolution: VirtualPageResolution,
    palette: ReaderVisualPalette,
    onIllustrationClicked: (imageUrl: String, imageIndexInChapter: Int) -> Unit,
    onRetryFailedSlot: (cid: Int) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
) {
    when (val slot = resolution.slot) {
        is ChapterWindowSlot.Loaded -> {
            val page = resolution.page ?: slot.layout.pages.firstOrNull()
            if (page != null) {
                ReaderCanvasPage(
                    chapterTitle = "${slot.chapter.volumeTitle} · ${slot.chapter.chapterTitle}".trim(' ', '·'),
                    page = page,
                    palette = palette,
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
                    .padding(32.dp)
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
                    )
                }
            }
        }

        is ChapterWindowSlot.Error -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
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
                    )
                    Text(
                        text = slot.message,
                        color = palette.secondaryInkColor,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { onRetryFailedSlot(slot.chapter.cid) }) {
                            Text("重试加载")
                        }
                        OutlinedButton(onClick = onOpenNetworkDiagnostics) {
                            Text("网络检测")
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
    tagSuffix: String,
    onIllustrationClicked: (imageUrl: String, imageIndexInChapter: Int) -> Unit,
) {
    val density = LocalDensity.current
    val clockLabel = remember(page.cid, page.pageIndexInChapter) {
        SimpleDateFormat("HH:mm", Locale.US).format(Date())
    }
    val progressPercent = remember(page.pageIndexInChapter, page.totalPagesInChapter) {
        if (page.totalPagesInChapter <= 1) {
            100
        } else {
            (((page.pageIndexInChapter + 1) * 100f) / page.totalPagesInChapter.toFloat()).roundToInt()
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
                )
            }
        }

        Canvas(modifier = Modifier.fillMaxSize()) {
            val nativeCanvas = drawContext.canvas.nativeCanvas
            for (element in page.elements) {
                when (element) {
                    is ReaderPageElement.TitleLine -> {
                        titlePaint.textSize = element.fontSizePx
                        titlePaint.letterSpacing = 0f
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

/**
 * Mode 4 (`上下卷轴`): Continuous vertical scrolling delegate rendering structured `ChapterBlock`s
 * with sticky chapter title headers and full-width clickable illustrations.
 */
@Composable
private fun ContinuousScrollReaderDelegate(
    snapshot: ReaderWindowSnapshot,
    palette: ReaderVisualPalette,
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
) {
    val currentSlot = snapshot.currentSlot
    if (currentSlot !is ChapterWindowSlot.Loaded) {
        val fallbackRes = snapshot.resolveVirtualPage(snapshot.activeVirtualPageIndex)
        if (fallbackRes != null) {
            ReaderResolvedPageSurface(
                resolution = fallbackRes,
                palette = palette,
                onIllustrationClicked = onIllustrationClicked,
                onRetryFailedSlot = onRetryFailedSlot,
                onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
            )
        }
        return
    }

    val ast = currentSlot.layout.ast
    val initialItemIndex = remember(ast.cid) {
        val anchorBlock = snapshot.activeReaderPage?.startAnchor?.paragraphIndex ?: 0
        anchorBlock.coerceIn(0, (ast.blocks.size - 1).coerceAtLeast(0))
    }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialItemIndex)
    val customFontFamily = remember(palette.customTypeface) {
        palette.customTypeface?.let { FontFamily(it) }
    }

    val scrollProgress by remember(ast.blocks.size) {
        derivedStateOf {
            if (ast.blocks.isEmpty()) {
                1f
            } else {
                ((listState.firstVisibleItemIndex + 1).toFloat() / ast.blocks.size.toFloat())
                    .coerceIn(0f, 1f)
            }
        }
    }

    LaunchedEffect(ast.cid, listState) {
        snapshotFlow { listState.firstVisibleItemIndex }
            .distinctUntilChanged()
            .collect { itemIdx ->
                val block = ast.blocks.getOrNull(itemIdx)
                if (block != null) {
                    val permille = (scrollProgress * 1000f).roundToInt().coerceIn(0, 1000)
                    onAnchorChanged(
                        ReadingAnchor(
                            paragraphIndex = block.blockIndex,
                            charOffset = 0,
                            progressPermille = permille,
                        ),
                        scrollProgress,
                    )
                }
            }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(
            horizontal = pageEdgeDistanceDp.coerceAtLeast(12).dp,
            vertical = 28.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(paragraphDistanceDp.coerceAtLeast(6).dp),
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onToggleHud,
            )
            .testTag(ReaderTestTags.CONTINUOUS_SCROLL_LIST),
    ) {
        if (snapshot.prevSlot != null) {
            item(key = "prev_chapter_btn_${ast.cid}") {
                OutlinedButton(
                    onClick = onRequestPrevChapter,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("▲ 上一章：${snapshot.prevSlot.chapter.chapterTitle}")
                }
            }
        }

        items(
            items = ast.blocks,
            key = { block -> "${ast.cid}_${block.blockIndex}" },
        ) { block ->
            when (block) {
                is ChapterBlock.TitleBlock -> {
                    Text(
                        text = block.title,
                        color = palette.primaryInkColor,
                        fontSize = (fontSizeSp + 4).sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = customFontFamily,
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                    )
                }

                is ChapterBlock.ParagraphBlock -> {
                    Text(
                        text = block.formattedText,
                        color = palette.primaryInkColor,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp + lineDistanceDp).sp,
                        fontFamily = customFontFamily,
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

        if (snapshot.nextSlot != null) {
            item(key = "next_chapter_btn_${ast.cid}") {
                Button(
                    onClick = onRequestNextChapter,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                ) {
                    Text("下一章：${snapshot.nextSlot.chapter.chapterTitle} ▼")
                }
            }
        }
    }
}
