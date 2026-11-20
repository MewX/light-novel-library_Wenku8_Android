package org.mewx.wenku8.ui.bookshelf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.mewx.wenku8.R
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.data.repository.BookshelfItemModel
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.discovery.BookStatusBadge
import org.mewx.wenku8.ui.discovery.DiscoveryTestTags
import org.mewx.wenku8.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import org.mewx.wenku8.ui.theme.wenku8SharedNovelCover
import org.mewx.wenku8.ui.theme.wenku8SharedNovelTitle
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Interactive 3D bookshelf section (`BookshelfViewMode.SPINE_3D`) displaying isometric book volumes
 * (vertical spine title + top page edges + angled real book cover) with continuous extraction-wave
 * scrolling, inertial snapping, a fast scrollbar with floating preview pill, and responsive layout
 * across phones, foldables, tablets, and compact-height landscape screens.
 */
@Composable
internal fun Bookshelf3dShelfSection(
    items: List<BookshelfItemModel>,
    availableWidth: Dp,
    availableHeight: Dp,
    selectedAidSet: Set<Int>,
    isMultiSelectActive: Boolean,
    isEinkMode: Boolean,
    onItemClick: (BookshelfItemModel) -> Unit,
    onItemLongClick: (BookshelfItemModel) -> Unit,
    onContinueReadingClick: (BookshelfItemModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return

    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val metrics = remember(availableWidth, availableHeight) {
        Bookshelf3dGeometry.computeMetrics(
            availableWidthDp = availableWidth.value,
            availableHeightDp = availableHeight.value,
        )
    }

    // Track focused book by aid across filter/sort/deletion mutations so focus stays stable.
    var savedFocusedAid by rememberSaveable { mutableIntStateOf(items.first().aid) }
    val focusAnimatable = remember { Animatable(0f) }
    var isScrubbing by remember { mutableStateOf(false) }

    val itemIdsKey = remember(items) { items.map { it.aid } }
    LaunchedEffect(itemIdsKey) {
        val existingIdx = items.indexOfFirst { it.aid == savedFocusedAid }
        val resolvedIdx = if (existingIdx >= 0) {
            existingIdx
        } else {
            focusAnimatable.value.roundToInt().coerceIn(0, items.lastIndex)
        }
        savedFocusedAid = items[resolvedIdx].aid
        if (abs(focusAnimatable.value - resolvedIdx.toFloat()) > 0.001f) {
            focusAnimatable.snapTo(resolvedIdx.toFloat())
        }
    }

    val focusedIndex by remember(items.size) {
        derivedStateOf {
            focusAnimatable.value.roundToInt().coerceIn(0, items.lastIndex)
        }
    }
    val focusedItem = items[focusedIndex]

    var lastHapticIndex by remember { mutableIntStateOf(focusedIndex) }
    LaunchedEffect(focusedIndex) {
        savedFocusedAid = items[focusedIndex].aid
        if (focusedIndex != lastHapticIndex) {
            lastHapticIndex = focusedIndex
            if (!isEinkMode) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val latestItems by rememberUpdatedState(items)
    val latestIsEinkMode by rememberUpdatedState(isEinkMode)
    val latestIsMultiSelectActive by rememberUpdatedState(isMultiSelectActive)
    val latestOnItemClick by rememberUpdatedState(onItemClick)
    val latestOnItemLongClick by rememberUpdatedState(onItemLongClick)

    val animateFocusTo: (Int) -> Unit = { targetIndex ->
        val currentItems = latestItems
        if (currentItems.isNotEmpty()) {
            val clamped = targetIndex.coerceIn(0, currentItems.lastIndex).toFloat()
            coroutineScope.launch {
                if (latestIsEinkMode) {
                    focusAnimatable.snapTo(clamped)
                } else {
                    focusAnimatable.animateTo(
                        targetValue = clamped,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            }
        }
    }

    val handleBookTap: (Int) -> Unit = { index ->
        val tappedItem = latestItems.getOrNull(index)
        if (tappedItem != null) {
            if (latestIsMultiSelectActive) {
                latestOnItemClick(tappedItem)
            } else if (abs(focusAnimatable.value - index) < 0.08f) {
                latestOnItemClick(tappedItem)
            } else {
                animateFocusTo(index)
            }
        }
    }

    val shelfColumn: @Composable (Modifier) -> Unit = { columnModifier ->
        Column(
            modifier = columnModifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Bookshelf3dStage(
                items = items,
                focusOffset = focusAnimatable.value,
                focusedIndex = focusedIndex,
                metrics = metrics,
                selectedAidSet = selectedAidSet,
                isEinkMode = isEinkMode,
                onDragFocusDelta = { deltaFocus ->
                    val currentItems = latestItems
                    if (currentItems.size > 1) {
                        val next = (focusAnimatable.value + deltaFocus)
                            .coerceIn(0f, currentItems.lastIndex.toFloat())
                        coroutineScope.launch { focusAnimatable.snapTo(next) }
                    }
                },
                onFlingReleased = { velocityPxPerSec, pitchPx ->
                    val currentItems = latestItems
                    if (currentItems.size > 1) {
                        val target = Bookshelf3dSnapPhysics.computeFlingTargetIndex(
                            currentFocus = focusAnimatable.value,
                            velocityPxPerSec = velocityPxPerSec,
                            pitchPx = pitchPx,
                            itemCount = currentItems.size,
                        )
                        animateFocusTo(target)
                    }
                },
                onBookTap = handleBookTap,
                onBookLongPress = { index ->
                    latestItems.getOrNull(index)?.let { latestOnItemLongClick(it) }
                },
            )

            Bookshelf3dFastScroller(
                items = items,
                focusOffset = focusAnimatable.value,
                focusedIndex = focusedIndex,
                isScrubbing = isScrubbing,
                isEinkMode = isEinkMode,
                onScrubStart = { fraction ->
                    isScrubbing = true
                    val next = Bookshelf3dSnapPhysics.fractionToFocus(fraction, items.size)
                    coroutineScope.launch { focusAnimatable.snapTo(next) }
                },
                onScrubMove = { fraction ->
                    val next = Bookshelf3dSnapPhysics.fractionToFocus(fraction, items.size)
                    coroutineScope.launch { focusAnimatable.snapTo(next) }
                },
                onScrubEnd = {
                    isScrubbing = false
                    animateFocusTo(focusAnimatable.value.roundToInt())
                },
                onTapJumpFraction = { fraction ->
                    val target = Bookshelf3dSnapPhysics.fractionToFocus(fraction, items.size).roundToInt()
                    animateFocusTo(target)
                },
                onStepDelta = { delta ->
                    animateFocusTo(focusedIndex + delta)
                },
                modifier = Modifier.width(metrics.stageWidthDp.dp),
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .testTag(BookshelfTestTags.SPINE_3D_CONTAINER),
        contentAlignment = Alignment.Center,
    ) {
        if (metrics.isTwoPane) {
            Row(
                modifier = Modifier
                    .widthIn(max = 1080.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                shelfColumn(Modifier.width(metrics.stageWidthDp.dp))
                Bookshelf3dWideSpotlightPanel(
                    item = focusedItem,
                    index = focusedIndex,
                    totalCount = items.size,
                    isSelected = focusedItem.aid in selectedAidSet,
                    isMultiSelectActive = isMultiSelectActive,
                    isEinkMode = isEinkMode,
                    isCompactHeight = availableHeight.value < Bookshelf3dGeometry.COMPACT_HEIGHT_THRESHOLD_DP,
                    onOpenDetail = { onItemClick(focusedItem) },
                    onLongPress = { onItemLongClick(focusedItem) },
                    onContinueReading = { onContinueReadingClick(focusedItem) },
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .widthIn(max = 580.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                shelfColumn(Modifier.fillMaxWidth())
                Bookshelf3dCompactSpotlightCard(
                    item = focusedItem,
                    isSelected = focusedItem.aid in selectedAidSet,
                    isMultiSelectActive = isMultiSelectActive,
                    isEinkMode = isEinkMode,
                    onClick = { onItemClick(focusedItem) },
                    onLongClick = { onItemLongClick(focusedItem) },
                    onContinueReading = { onContinueReadingClick(focusedItem) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun Bookshelf3dStage(
    items: List<BookshelfItemModel>,
    focusOffset: Float,
    focusedIndex: Int,
    metrics: Bookshelf3dMetrics,
    selectedAidSet: Set<Int>,
    isEinkMode: Boolean,
    onDragFocusDelta: (Float) -> Unit,
    onFlingReleased: (velocityPxPerSec: Float, pitchPx: Float) -> Unit,
    onBookTap: (Int) -> Unit,
    onBookLongPress: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val stageWidthPx = with(density) { metrics.stageWidthDp.dp.toPx() }
    val stageHeightPx = with(density) { metrics.stageHeightDp.dp.toPx() }
    val pitchPx = with(density) { metrics.pitchDp.dp.toPx() }
    val densityFactor = density.density

    val spineMatrix = remember { Bookshelf3dGeometry.buildLocalSpineMatrix() }
    val coverMatrix = remember { Bookshelf3dGeometry.buildLocalCoverMatrix() }

    val visibleRange = Bookshelf3dGeometry.computeVisibleIndexRange(
        itemCount = items.size,
        focus = focusOffset,
        metrics = metrics,
    )
    val visiblePoses = visibleRange.map { idx ->
        Bookshelf3dGeometry.computeBookPosePx(
            index = idx,
            focus = focusOffset,
            metrics = metrics,
            density = densityFactor,
        )
    }

    val latestPoses by rememberUpdatedState(visiblePoses)
    val latestPitchPx by rememberUpdatedState(pitchPx)
    val latestOnDragFocusDelta by rememberUpdatedState(onDragFocusDelta)
    val latestOnFlingReleased by rememberUpdatedState(onFlingReleased)
    val latestOnBookTap by rememberUpdatedState(onBookTap)
    val latestOnBookLongPress by rememberUpdatedState(onBookLongPress)

    val draggableState = rememberDraggableState { deltaPx ->
        if (latestPitchPx > 0f) {
            // Dragging finger LEFT (deltaPx < 0) advances to higher indices (+deltaFocus).
            latestOnDragFocusDelta(-deltaPx / latestPitchPx)
        }
    }

    val colorScheme = MaterialTheme.colorScheme
    val isDark = colorScheme.background.luminance() < 0.5f
    val shadowBaseColor = if (isDark) {
        Color.Black.copy(alpha = 0.36f)
    } else {
        colorScheme.onSurface.copy(alpha = 0.13f)
    }

    Box(
        modifier = Modifier
            .width(metrics.stageWidthDp.dp)
            .height(metrics.stageHeightDp.dp)
            .clipToBounds()
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = items.size > 1,
                onDragStopped = { velocityPxPerSec ->
                    latestOnFlingReleased(velocityPxPerSec, latestPitchPx)
                },
            )
            .pointerInput(items.size) {
                detectTapGestures(
                    onTap = { tapOffset ->
                        Bookshelf3dGeometry.hitTestBookIndex(tapOffset, latestPoses)?.let { hitIdx ->
                            latestOnBookTap(hitIdx)
                        }
                    },
                    onLongPress = { pressOffset ->
                        Bookshelf3dGeometry.hitTestBookIndex(pressOffset, latestPoses)?.let { hitIdx ->
                            latestOnBookLongPress(hitIdx)
                        }
                    },
                )
            }
            .testTag(BookshelfTestTags.SPINE_3D_STAGE),
    ) {
        // Layer 1: Angled floor cast shadows & subtle shelf rail baseline
        Canvas(modifier = Modifier.fillMaxSize()) {
            if (!isEinkMode) {
                visiblePoses.forEach { pose ->
                    val poly = pose.floorShadowPolygon
                    if (poly.size >= 4) {
                        val path = Path().apply {
                            moveTo(poly[0].x, poly[0].y)
                            for (i in 1 until poly.size) {
                                lineTo(poly[i].x, poly[i].y)
                            }
                            close()
                        }
                        val minX = poly.minOf { it.x }
                        val maxX = poly.maxOf { it.x }
                        val minY = poly.minOf { it.y }
                        val maxY = poly.maxOf { it.y }
                        drawPath(
                            path = path,
                            brush = Brush.linearGradient(
                                colors = listOf(shadowBaseColor, Color.Transparent),
                                start = Offset(maxX, minY),
                                end = Offset(minX, maxY),
                            ),
                        )
                    }
                }
            } else {
                // Crisp minimal shelf ground rule for E-Ink panels (zero greyscale dithering).
                val ruleY = (metrics.baseYDp + metrics.bodyHeightDp + 6f) * densityFactor
                drawLine(
                    color = colorScheme.outline,
                    start = Offset(0f, ruleY),
                    end = Offset(size.width, ruleY),
                    strokeWidth = 1f * densityFactor,
                )
            }
        }

        // Layer 2: Fixed back-to-front (ascending index) 3D book solids
        visiblePoses.forEach { pose ->
            val item = items[pose.index]
            key(item.aid) {
                Bookshelf3dBookVolume(
                    item = item,
                    displayNumber = pose.index + 1,
                    pose = pose,
                    metrics = metrics,
                    spineMatrix = spineMatrix,
                    coverMatrix = coverMatrix,
                    isFocused = pose.index == focusedIndex,
                    shouldLoadCover = abs(pose.index - focusedIndex) <= Bookshelf3dGeometry.COVER_LOAD_RADIUS,
                    isSelected = item.aid in selectedAidSet,
                    isDark = isDark,
                    isEinkMode = isEinkMode,
                )
            }
        }

        // Layer 3: Exposed per-book semantics & hit-target anchors (exact unoccluded regions)
        Layout(
            content = {
                visiblePoses.forEach { pose ->
                    val item = items[pose.index]
                    val isSelected = item.aid in selectedAidSet
                    val isFocused = pose.index == focusedIndex
                    val label = "${item.novel.title} ${item.novel.author}".trim()
                    Box(
                        modifier = Modifier
                            .semantics {
                                contentDescription = label
                                role = Role.Button
                                selected = isSelected || isFocused
                                onClick {
                                    onBookTap(pose.index)
                                    true
                                }
                                onLongClick {
                                    onBookLongPress(pose.index)
                                    true
                                }
                            }
                            .testTag(BookshelfTestTags.BOOK_CARD_PREFIX + item.aid),
                    )
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) { measurables, _ ->
            val placeables = measurables.mapIndexed { idx, measurable ->
                val pose = visiblePoses[idx]
                val rect = Bookshelf3dGeometry.computeExposedBoundsPx(
                    pose = pose,
                    visiblePoses = visiblePoses,
                    focusedIndex = focusedIndex,
                    stageWidthPx = stageWidthPx,
                    stageHeightPx = stageHeightPx,
                )
                val w = rect.width.roundToInt().coerceAtLeast(1)
                val h = rect.height.roundToInt().coerceAtLeast(1)
                Triple(
                    measurable.measure(Constraints.fixed(w, h)),
                    rect.left.roundToInt(),
                    rect.top.roundToInt(),
                )
            }
            layout(stageWidthPx.roundToInt(), stageHeightPx.roundToInt()) {
                placeables.forEach { (placeable, x, y) ->
                    placeable.place(x, y)
                }
            }
        }
    }
}

/**
 * Renders a single physical 3D book volume composed of 3 exact-vertex-sharing faces:
 * 1. Projected Cover Face (back-right, receding along `DEPTH_X, DEPTH_Y`)
 * 2. Top Page-Edge Cap (parallelogram connecting spine top and cover top)
 * 3. Front Spine Face (slanted slightly downward-right by `SPINE_SLOPE_Y`)
 */
@Composable
private fun Bookshelf3dBookVolume(
    item: BookshelfItemModel,
    displayNumber: Int,
    pose: Bookshelf3dBookPose,
    metrics: Bookshelf3dMetrics,
    spineMatrix: Matrix,
    coverMatrix: Matrix,
    isFocused: Boolean,
    shouldLoadCover: Boolean,
    isSelected: Boolean,
    isDark: Boolean,
    isEinkMode: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val spineSurfaceColor = when {
        isEinkMode -> colorScheme.surface
        isDark -> colorScheme.surfaceContainerHigh
        else -> colorScheme.surfaceContainerHigh
    }
    val coverSleeveColor = when {
        isEinkMode -> colorScheme.surface
        isDark -> colorScheme.surfaceContainer
        else -> colorScheme.surface
    }
    val edgeBorderColor = when {
        isSelected -> colorScheme.primary
        isEinkMode -> colorScheme.onSurface
        else -> colorScheme.outlineVariant
    }
    val edgeBorderWidth = if (isSelected) 2.dp else 0.8.dp

    val coverAnchor = pose.spineQuad[1]
    val coverPlacedX = coverAnchor.x.roundToInt()
    val coverPlacedY = coverAnchor.y.roundToInt()
    val coverFracX = coverAnchor.x - coverPlacedX
    val coverFracY = coverAnchor.y - coverPlacedY

    val spinePlacedX = pose.origin.x.roundToInt()
    val spinePlacedY = pose.origin.y.roundToInt()
    val spineFracX = pose.origin.x - spinePlacedX
    val spineFracY = pose.origin.y - spinePlacedY

    val capStartColor = when {
        isEinkMode -> colorScheme.surface
        isDark -> colorScheme.surfaceContainerHighest
        else -> colorScheme.surfaceVariant
    }
    val capEndColor = when {
        isEinkMode -> colorScheme.surface
        isDark -> colorScheme.surfaceContainerHigh
        else -> colorScheme.surface
    }
    val capStrokeColor = if (isEinkMode) {
        colorScheme.onSurface
    } else {
        colorScheme.outline.copy(alpha = if (isDark) 0.34f else 0.28f)
    }
    val capPageLineColor = if (isEinkMode) {
        colorScheme.onSurface.copy(alpha = 0.28f)
    } else {
        colorScheme.outline.copy(alpha = 0.18f)
    }

    // 1. Projected Cover Face
    Box(
        modifier = Modifier
            .offset { IntOffset(coverPlacedX, coverPlacedY) }
            .size(width = metrics.coverWidthDp.dp, height = metrics.bodyHeightDp.dp)
            .drawWithContent {
                drawContext.canvas.save()
                drawContext.canvas.translate(coverFracX, coverFracY)
                drawContext.canvas.concat(coverMatrix)
                drawContent()
                drawContext.canvas.restore()
            },
    ) {
        Bookshelf3dCoverFace(
            item = item,
            displayNumber = displayNumber,
            sleeveColor = coverSleeveColor,
            borderColor = edgeBorderColor,
            borderWidth = edgeBorderWidth,
            extractionWeight = pose.weight,
            isFocused = isFocused,
            shouldLoadCover = shouldLoadCover,
            isSelected = isSelected,
            isEinkMode = isEinkMode,
        )
    }

    // 2. Top Page-Edge Cap (parallelogram sharing vertices with both spine & cover)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val quad = pose.topCapQuad
        val path = Path().apply {
            moveTo(quad[0].x, quad[0].y)
            lineTo(quad[1].x, quad[1].y)
            lineTo(quad[2].x, quad[2].y)
            lineTo(quad[3].x, quad[3].y)
            close()
        }
        if (isEinkMode) {
            drawPath(path = path, color = capStartColor)
        } else {
            drawPath(
                path = path,
                brush = Brush.linearGradient(
                    colors = listOf(capStartColor, capEndColor),
                    start = quad[0],
                    end = quad[2],
                ),
            )
        }

        // Subtle inner paper-page edge lines along the depth axis
        for (step in listOf(0.35f, 0.68f)) {
            val startPt = Offset(
                x = quad[0].x + (quad[1].x - quad[0].x) * step,
                y = quad[0].y + (quad[1].y - quad[0].y) * step,
            )
            val endPt = Offset(
                x = quad[3].x + (quad[2].x - quad[3].x) * step,
                y = quad[3].y + (quad[2].y - quad[3].y) * step,
            )
            drawLine(
                color = capPageLineColor,
                start = startPt,
                end = endPt,
                strokeWidth = 0.7f,
            )
        }

        drawPath(
            path = path,
            color = if (isSelected) colorScheme.primary else capStrokeColor,
            style = Stroke(width = if (isSelected) 2.2f else 1f),
        )
    }

    // 3. Front Spine Face
    Box(
        modifier = Modifier
            .offset { IntOffset(spinePlacedX, spinePlacedY) }
            .size(width = metrics.spineWidthDp.dp, height = metrics.bodyHeightDp.dp)
            .drawWithContent {
                drawContext.canvas.save()
                drawContext.canvas.translate(spineFracX, spineFracY)
                drawContext.canvas.concat(spineMatrix)
                drawContent()
                drawContext.canvas.restore()
            },
    ) {
        Bookshelf3dSpineFace(
            item = item,
            displayNumber = displayNumber,
            spineColor = spineSurfaceColor,
            borderColor = edgeBorderColor,
            borderWidth = edgeBorderWidth,
            isFocused = isFocused,
            isSelected = isSelected,
            isEinkMode = isEinkMode,
        )
    }
}

@Composable
private fun Bookshelf3dCoverFace(
    item: BookshelfItemModel,
    displayNumber: Int,
    sleeveColor: Color,
    borderColor: Color,
    borderWidth: Dp,
    extractionWeight: Float,
    isFocused: Boolean,
    shouldLoadCover: Boolean,
    isSelected: Boolean,
    isEinkMode: Boolean,
) {
    val coverClipShape = RoundedCornerShape(3.dp)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(sleeveColor)
            .border(borderWidth, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 7.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "NO.${displayNumber.toString().padStart(3, '0')}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.sp,
                        lineHeight = 10.sp,
                        letterSpacing = 0.6.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(13.dp),
                    )
                } else if (item.novel.hasUnreadUpdate) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .wenku8SharedNovelCover(
                        origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN,
                        aid = item.aid,
                        clipShape = coverClipShape,
                        enabled = isFocused,
                    )
                    .clip(coverClipShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.size(24.dp),
                )
                if (shouldLoadCover && item.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = WenkuImageRequests.rememberCoverImageRequest(
                            aid = item.aid,
                            coverUrl = item.coverUrl,
                            enableCrossfade = false,
                        ),
                        contentDescription = item.novel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }

            Text(
                text = "WK-${item.aid.toString().padStart(5, '0')}",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    lineHeight = 9.sp,
                    letterSpacing = 0.5.sp,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Depth recession scrim on books tucked deeper into the shelf bank
        if (!isEinkMode && extractionWeight < 0.99f) {
            val depthAlpha = ((1f - extractionWeight) * 0.14f).coerceIn(0f, 0.14f)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = depthAlpha)),
            )
        }
    }
}

@Composable
private fun Bookshelf3dSpineFace(
    item: BookshelfItemModel,
    displayNumber: Int,
    spineColor: Color,
    borderColor: Color,
    borderWidth: Dp,
    isFocused: Boolean,
    isSelected: Boolean,
    isEinkMode: Boolean,
) {
    val activeAccent = if (isFocused || isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(spineColor)
            .border(borderWidth, borderColor),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 1.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = displayNumber.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    lineHeight = 9.sp,
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Medium,
                ),
                color = activeAccent,
                maxLines = 1,
                softWrap = false,
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Bookshelf3dSpineTitle(
                    title = item.novel.title,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            if (item.novel.hasUnreadUpdate) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            } else if (item.readingProgressFraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.65f)
                        .height(3.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(item.readingProgressFraction.coerceIn(0.15f, 1f))
                            .background(activeAccent),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .border(
                            width = 0.8.dp,
                            color = MaterialTheme.colorScheme.outline,
                            shape = CircleShape,
                        ),
                )
            }
        }

        // Subtle left-edge crease highlight for physical spine roundness
        if (!isEinkMode) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(2.dp)
                    .align(Alignment.CenterStart)
                    .background(Color.White.copy(alpha = 0.14f)),
            )
        }
    }
}

/**
 * Vertical spine typography matching `Wenku8_Flutter`'s `ArchiveSpineTitle`:
 * stacks CJK characters upright while rotating contiguous Latin/ASCII runs 90° clockwise.
 */
@Composable
private fun Bookshelf3dSpineTitle(
    title: String,
    color: Color,
) {
    val (runs, truncated) = remember(title) { parseSpineTitleRuns(title, maxRuns = 7) }
    val textStyle = MaterialTheme.typography.labelSmall.copy(
        fontSize = 8.5.sp,
        lineHeight = 9.5.sp,
        fontWeight = FontWeight.SemiBold,
        color = color,
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
    ) {
        runs.forEach { run ->
            if (run.isRotatedLatin) {
                Text(
                    text = run.text,
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier.rotateQuarterTurnClockwise(),
                )
            } else {
                Text(
                    text = run.text,
                    style = textStyle,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        if (truncated) {
            Text(
                text = "…",
                style = textStyle,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

/**
 * Rotates a composable 90° clockwise while swapping its measured width and height so vertical
 * `Column` layout accounts for the rotated Latin word length cleanly.
 */
private fun Modifier.rotateQuarterTurnClockwise(): Modifier = this
    .layout { measurable, constraints ->
        val transposed = Constraints(
            minWidth = constraints.minHeight,
            maxWidth = constraints.maxHeight,
            minHeight = constraints.minWidth,
            maxHeight = constraints.maxWidth,
        )
        val placeable = measurable.measure(transposed)
        layout(placeable.height, placeable.width) {
            placeable.placeWithLayer(
                x = (placeable.height - placeable.width) / 2,
                y = (placeable.width - placeable.height) / 2,
            ) {
                rotationZ = 90f
            }
        }
    }

/**
 * Fast horizontal scrollbar + prev/next steppers + floating scrub preview bubble for rapidly
 * navigating across dozens or hundreds of books in the 3D bookshelf.
 */
@Composable
internal fun Bookshelf3dFastScroller(
    items: List<BookshelfItemModel>,
    focusOffset: Float,
    focusedIndex: Int,
    isScrubbing: Boolean,
    isEinkMode: Boolean,
    onScrubStart: (Float) -> Unit,
    onScrubMove: (Float) -> Unit,
    onScrubEnd: () -> Unit,
    onTapJumpFraction: (Float) -> Unit,
    onStepDelta: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemCount = items.size
    val fraction = Bookshelf3dSnapPhysics.focusToFraction(focusOffset, itemCount)
    val digits = if (itemCount >= 100) 3 else 2
    val counterLabel = "${(focusedIndex + 1).toString().padStart(digits, '0')} / ${itemCount.toString().padStart(digits, '0')}"
    val indexAccessibilityDesc = stringResource(
        R.string.bookshelf_spine3d_index_desc_format,
        focusedIndex + 1,
        itemCount,
    )
    val scrollerDesc = stringResource(R.string.bookshelf_spine3d_fast_scroller_desc)
    val focusedTitle = items.getOrNull(focusedIndex)?.novel?.title.orEmpty()

    var trackWidthPx by remember { mutableIntStateOf(1) }
    val latestOnScrubStart by rememberUpdatedState(onScrubStart)
    val latestOnScrubMove by rememberUpdatedState(onScrubMove)
    val latestOnScrubEnd by rememberUpdatedState(onScrubEnd)
    val latestOnTapJumpFraction by rememberUpdatedState(onTapJumpFraction)

    val trackColor = if (isEinkMode) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val activeTrackColor = MaterialTheme.colorScheme.primary
    val thumbColor = MaterialTheme.colorScheme.primary
    val thumbGripColor = MaterialTheme.colorScheme.onPrimary
    val tickColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val trackOutlineColor = MaterialTheme.colorScheme.outline

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag(BookshelfTestTags.SPINE_3D_FAST_SCROLLER),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (isScrubbing && itemCount > 1) {
            Surface(
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                shape = CircleShape,
                shadowElevation = if (isEinkMode) 0.dp else 3.dp,
                modifier = Modifier
                    .einkHairlineBorder(CircleShape)
                    .testTag(BookshelfTestTags.SPINE_3D_SCRUB_PREVIEW),
            ) {
                Text(
                    text = "$counterLabel · $focusedTitle",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 280.dp)
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (itemCount > 1) {
                val prevLabel = stringResource(R.string.bookshelf_spine3d_prev_book)
                TooltipIconButton(
                    tooltipText = prevLabel,
                    onClick = { onStepDelta(-1) },
                    enabled = focusedIndex > 0,
                    modifier = Modifier.testTag(BookshelfTestTags.SPINE_3D_PREV_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronLeft,
                        contentDescription = prevLabel,
                    )
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .onSizeChanged { trackWidthPx = it.width.coerceAtLeast(1) }
                        .pointerInput(itemCount) {
                            detectTapGestures { offset ->
                                val targetFraction = (offset.x / trackWidthPx.toFloat()).coerceIn(0f, 1f)
                                latestOnTapJumpFraction(targetFraction)
                            }
                        }
                        .pointerInput(itemCount) {
                            detectHorizontalDragGestures(
                                onDragStart = { startOffset ->
                                    val targetFraction = (startOffset.x / trackWidthPx.toFloat()).coerceIn(0f, 1f)
                                    latestOnScrubStart(targetFraction)
                                },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    val targetFraction = (change.position.x / trackWidthPx.toFloat()).coerceIn(0f, 1f)
                                    latestOnScrubMove(targetFraction)
                                },
                                onDragEnd = { latestOnScrubEnd() },
                                onDragCancel = { latestOnScrubEnd() },
                            )
                        }
                        .semantics {
                            contentDescription = scrollerDesc
                            progressBarRangeInfo = ProgressBarRangeInfo(
                                current = fraction,
                                range = 0f..1f,
                                steps = (itemCount - 2).coerceAtLeast(0),
                            )
                            setProgress { target ->
                                latestOnTapJumpFraction(target.coerceIn(0f, 1f))
                                true
                            }
                        }
                        .testTag(BookshelfTestTags.SPINE_3D_FAST_SCROLLER_TRACK),
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val thumbWidth = (if (isScrubbing) 30.dp else 24.dp).toPx()
                        val thumbHeight = (if (isScrubbing) 18.dp else 14.dp).toPx()
                        val trackHeight = 6.dp.toPx()
                        val halfThumb = thumbWidth / 2f
                        val usableWidth = (size.width - thumbWidth).coerceAtLeast(1f)
                        val centerY = size.height / 2f
                        val thumbCenterX = halfThumb + usableWidth * fraction

                        // Background track pill
                        drawRoundRect(
                            color = trackColor,
                            topLeft = Offset(halfThumb, centerY - trackHeight / 2f),
                            size = Size(usableWidth, trackHeight),
                            cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                        )
                        if (isEinkMode) {
                            drawRoundRect(
                                color = trackOutlineColor,
                                topLeft = Offset(halfThumb, centerY - trackHeight / 2f),
                                size = Size(usableWidth, trackHeight),
                                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                                style = Stroke(width = 1.dp.toPx()),
                            )
                        }

                        // Active filled segment
                        val activeWidth = (thumbCenterX - halfThumb).coerceAtLeast(0f)
                        if (activeWidth > 0f) {
                            drawRoundRect(
                                color = activeTrackColor,
                                topLeft = Offset(halfThumb, centerY - trackHeight / 2f),
                                size = Size(activeWidth, trackHeight),
                                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
                            )
                        }

                        // Subtle book-position ticks for small-to-medium shelves
                        if (itemCount in 2..20) {
                            val tickRadius = 1.5.dp.toPx()
                            for (i in 0 until itemCount) {
                                val tickX = halfThumb + usableWidth * (i.toFloat() / (itemCount - 1))
                                if (abs(tickX - thumbCenterX) > halfThumb + 2f) {
                                    drawCircle(
                                        color = tickColor,
                                        radius = tickRadius,
                                        center = Offset(tickX, centerY),
                                    )
                                }
                            }
                        }

                        // Tactile pill thumb
                        drawRoundRect(
                            color = thumbColor,
                            topLeft = Offset(thumbCenterX - halfThumb, centerY - thumbHeight / 2f),
                            size = Size(thumbWidth, thumbHeight),
                            cornerRadius = CornerRadius(thumbHeight / 2f, thumbHeight / 2f),
                        )

                        // Center grip line on thumb
                        val gripHalfHeight = thumbHeight * 0.24f
                        drawLine(
                            color = thumbGripColor,
                            start = Offset(thumbCenterX, centerY - gripHalfHeight),
                            end = Offset(thumbCenterX, centerY + gripHalfHeight),
                            strokeWidth = 1.6.dp.toPx(),
                        )
                    }
                }

                val nextLabel = stringResource(R.string.bookshelf_spine3d_next_book)
                TooltipIconButton(
                    tooltipText = nextLabel,
                    onClick = { onStepDelta(1) },
                    enabled = focusedIndex < itemCount - 1,
                    modifier = Modifier.testTag(BookshelfTestTags.SPINE_3D_NEXT_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = nextLabel,
                    )
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shape = CircleShape,
                modifier = Modifier
                    .einkHairlineBorder(CircleShape)
                    .semantics(mergeDescendants = true) { contentDescription = indexAccessibilityDesc }
                    .testTag(BookshelfTestTags.SPINE_3D_INDEX_COUNTER),
            ) {
                Text(
                    text = counterLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Compact & Medium screen spotlight card below the 3D bookshelf showing the focused book's title,
 * metadata, reading progress, unread badge, and quick action button.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bookshelf3dCompactSpotlightCard(
    item: BookshelfItemModel,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    isEinkMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onContinueReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardShape = MaterialTheme.shapes.large
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = cardShape,
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = modifier
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .einkHairlineBorder(cardShape)
            .testTag(BookshelfTestTags.SPINE_3D_SPOTLIGHT_CARD),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = item.novel.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .wenku8SharedNovelTitle(
                            origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN,
                            aid = item.aid,
                        ),
                )
                if (item.novel.hasUnreadUpdate) {
                    NewChapterPillBadge(aid = item.aid)
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            val metaParts = buildList {
                if (item.novel.author.isNotBlank()) add(item.novel.author)
                if (item.novel.pressId.isNotBlank()) add(item.novel.pressId)
            }
            if (metaParts.isNotEmpty() || item.novel.bookStatus.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (metaParts.isNotEmpty()) {
                        Text(
                            text = metaParts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    BookStatusBadge(
                        rawStatus = item.novel.bookStatus,
                        modifier = Modifier.testTag(
                            DiscoveryTestTags.BOOK_STATUS_BADGE_PREFIX + item.aid,
                        ),
                    )
                }
            }

            val progressLabel = item.readingProgress?.let { prog ->
                "${prog.volumeTitle} · ${prog.chapterTitle}".trim(' ', '·')
            }?.takeIf { it.isNotBlank() }
                ?: item.novel.latestSectionName.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.bookshelf_spine3d_not_started)

            Text(
                text = progressLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            BookshelfItemProgressIndicator(
                item = item,
                isEinkMode = isEinkMode,
            )

            if (!isMultiSelectActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (item.cachedChapterCount > 0) {
                        val cacheLabel = if (item.totalChapterCount > 0) {
                            stringResource(
                                R.string.bookshelf_cached_ratio_format,
                                item.cachedChapterCount,
                                item.totalChapterCount,
                            )
                        } else {
                            stringResource(R.string.bookshelf_cached_count_format, item.cachedChapterCount)
                        }
                        Text(
                            text = cacheLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    val hasProgress = item.readingProgress != null
                    FilledTonalButton(
                        onClick = if (hasProgress) onContinueReading else onClick,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag(BookshelfTestTags.SPINE_3D_PRIMARY_ACTION),
                    ) {
                        Icon(
                            imageVector = if (hasProgress) {
                                Icons.Outlined.PlayArrow
                            } else {
                                Icons.AutoMirrored.Outlined.ArrowForward
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(
                                if (hasProgress) {
                                    R.string.bookshelf_continue_reading_action
                                } else {
                                    R.string.bookshelf_spine3d_open_detail
                                },
                            ),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Wide / Tablet / Landscape side-by-side editorial spotlight panel paired with the 3D bookshelf
 * (inspired by `Wenku8_Flutter`'s wide two-pane archive shelf layout).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bookshelf3dWideSpotlightPanel(
    item: BookshelfItemModel,
    index: Int,
    totalCount: Int,
    isSelected: Boolean,
    isMultiSelectActive: Boolean,
    isEinkMode: Boolean,
    isCompactHeight: Boolean = false,
    onOpenDetail: () -> Unit,
    onLongPress: () -> Unit,
    onContinueReading: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardShape = MaterialTheme.shapes.extraLarge
    val digits = if (totalCount >= 100) 3 else 2

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = cardShape,
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = modifier
            .clip(cardShape)
            .combinedClickable(
                onClick = onOpenDetail,
                onLongClick = onLongPress,
            )
            .einkHairlineBorder(cardShape)
            .testTag(BookshelfTestTags.SPINE_3D_WIDE_PANEL),
    ) {
        AnimatedContent(
            targetState = item,
            transitionSpec = {
                if (isEinkMode) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "bookshelf_3d_wide_spotlight",
        ) { target ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isCompactHeight) 14.dp else 22.dp),
                verticalArrangement = Arrangement.spacedBy(if (isCompactHeight) 8.dp else 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${(index + 1).toString().padStart(digits, '0')} / ${totalCount.toString().padStart(digits, '0')} · WK-${target.aid.toString().padStart(5, '0')}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (target.novel.hasUnreadUpdate) {
                        NewChapterPillBadge(aid = target.aid)
                    } else if (isSelected) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }

                Text(
                    text = target.novel.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.wenku8SharedNovelTitle(
                        origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN,
                        aid = target.aid,
                    ),
                )

                val metaParts = buildList {
                    if (target.novel.author.isNotBlank()) add(target.novel.author)
                    if (target.novel.pressId.isNotBlank()) add(target.novel.pressId)
                }
                if (metaParts.isNotEmpty() || target.novel.bookStatus.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (metaParts.isNotEmpty()) {
                            Text(
                                text = metaParts.joinToString(" · "),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        BookStatusBadge(
                            rawStatus = target.novel.bookStatus,
                            modifier = Modifier.testTag(
                                DiscoveryTestTags.BOOK_STATUS_BADGE_PREFIX + target.aid,
                            ),
                        )
                    }
                }

                if (target.tags.isNotEmpty()) {
                    Text(
                        text = target.tags.take(4).joinToString(" · ") { "#$it" },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val intro = target.novel.introShort.trim()
                if (intro.isNotEmpty()) {
                    Text(
                        text = intro,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                val progressLabel = target.readingProgress?.let { prog ->
                    "${prog.volumeTitle} · ${prog.chapterTitle}".trim(' ', '·')
                }?.takeIf { it.isNotBlank() }
                    ?: target.novel.latestSectionName.takeIf { it.isNotBlank() }
                    ?: stringResource(R.string.bookshelf_spine3d_not_started)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = progressLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    BookshelfItemProgressIndicator(
                        item = target,
                        isEinkMode = isEinkMode,
                    )
                }

                if (!isMultiSelectActive) {
                    val hasProgress = target.readingProgress != null
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilledTonalButton(
                            onClick = if (hasProgress) onContinueReading else onOpenDetail,
                            modifier = Modifier.testTag(BookshelfTestTags.SPINE_3D_PRIMARY_ACTION),
                        ) {
                            Icon(
                                imageVector = if (hasProgress) {
                                    Icons.Outlined.PlayArrow
                                } else {
                                    Icons.AutoMirrored.Outlined.ArrowForward
                                },
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(
                                    if (hasProgress) {
                                        R.string.bookshelf_continue_reading_action
                                    } else {
                                        R.string.bookshelf_spine3d_open_detail
                                    },
                                ),
                            )
                        }
                        if (target.cachedChapterCount > 0) {
                            val cacheLabel = if (target.totalChapterCount > 0) {
                                stringResource(
                                    R.string.bookshelf_cached_ratio_format,
                                    target.cachedChapterCount,
                                    target.totalChapterCount,
                                )
                            } else {
                                stringResource(R.string.bookshelf_cached_count_format, target.cachedChapterCount)
                            }
                            Text(
                                text = cacheLabel,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }
    }
}
