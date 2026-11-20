package org.mewx.wenku8.ui.bookshelf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.ui.graphics.graphicsLayer
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
import org.mewx.wenku8.ui.theme.LocalWenku8SharedTransition
import org.mewx.wenku8.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import org.mewx.wenku8.ui.theme.expressiveSharedFloatSpec
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
    continueReadingAids: List<Int> = emptyList(),
) {
    if (items.isEmpty()) return

    val entries = remember(items, continueReadingAids) {
        build3dShelfEntries(
            sortedItems = items,
            continueReadingIds = continueReadingAids,
            idSelector = { it.aid },
        )
    }
    val continueReadingCount = remember(entries) {
        entries.count { it.isContinueReading }
    }

    val coroutineScope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current

    val metrics = remember(availableWidth, availableHeight) {
        Bookshelf3dGeometry.computeMetrics(
            availableWidthDp = availableWidth.value,
            availableHeightDp = availableHeight.value,
        )
    }

    // Track focused book by aid across filter/sort/deletion mutations and navigation round-trips.
    // Initialize focusAnimatable synchronously from savedFocusedAid so returning from NovelDetail
    // renders the exact focused book on Frame 1 for SharedTransitionLayout lookahead alignment.
    var savedFocusedAid by rememberSaveable { mutableIntStateOf(entries.first().item.aid) }
    val initialFocusIndex = entries.indexOfFirst { it.item.aid == savedFocusedAid }.coerceAtLeast(0)
    val focusAnimatable = remember { Animatable(initialFocusIndex.toFloat()) }
    var isScrubbing by remember { mutableStateOf(false) }

    val entryIdsKey = remember(entries) { entries.map { it.item.aid } }
    LaunchedEffect(entryIdsKey) {
        val existingIdx = entries.indexOfFirst { it.item.aid == savedFocusedAid }
        val resolvedIdx = if (existingIdx >= 0) {
            existingIdx
        } else {
            focusAnimatable.value.roundToInt().coerceIn(0, entries.lastIndex)
        }
        savedFocusedAid = entries[resolvedIdx].item.aid
        if (abs(focusAnimatable.value - resolvedIdx.toFloat()) > 0.001f) {
            focusAnimatable.snapTo(resolvedIdx.toFloat())
        }
    }

    val focusedIndex by remember(entries.size) {
        derivedStateOf {
            focusAnimatable.value.roundToInt().coerceIn(0, entries.lastIndex)
        }
    }
    val focusedEntry = entries[focusedIndex]
    val focusedItem = focusedEntry.item
    val displayFocusOffset = if (isEinkMode) focusedIndex.toFloat() else focusAnimatable.value

    LaunchedEffect(isEinkMode) {
        if (isEinkMode) {
            val snapped = focusAnimatable.value.roundToInt().coerceIn(0, entries.lastIndex).toFloat()
            if (abs(focusAnimatable.value - snapped) > 0.001f) {
                focusAnimatable.snapTo(snapped)
            }
        }
    }

    var lastHapticIndex by remember { mutableIntStateOf(focusedIndex) }
    LaunchedEffect(focusedIndex) {
        savedFocusedAid = entries[focusedIndex].item.aid
        if (focusedIndex != lastHapticIndex) {
            lastHapticIndex = focusedIndex
            if (!isEinkMode) {
                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        }
    }

    val latestEntries by rememberUpdatedState(entries)
    val latestIsEinkMode by rememberUpdatedState(isEinkMode)
    val latestIsMultiSelectActive by rememberUpdatedState(isMultiSelectActive)
    val latestOnItemClick by rememberUpdatedState(onItemClick)
    val latestOnItemLongClick by rememberUpdatedState(onItemLongClick)

    val animateFocusTo: (Int) -> Unit = { targetIndex ->
        val currentEntries = latestEntries
        if (currentEntries.isNotEmpty()) {
            val clamped = targetIndex.coerceIn(0, currentEntries.lastIndex).toFloat()
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
        val tappedItem = latestEntries.getOrNull(index)?.item
        if (tappedItem != null) {
            if (latestIsMultiSelectActive) {
                latestOnItemClick(tappedItem)
            } else if (abs(focusAnimatable.value - index) < 0.08f) {
                savedFocusedAid = tappedItem.aid
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
                entries = entries,
                focusOffset = displayFocusOffset,
                focusedIndex = focusedIndex,
                metrics = metrics,
                selectedAidSet = selectedAidSet,
                isScrubbing = isScrubbing,
                isEinkMode = isEinkMode,
                onDragFocusDelta = { deltaFocus ->
                    val currentEntries = latestEntries
                    if (currentEntries.size > 1) {
                        val next = (focusAnimatable.value + deltaFocus)
                            .coerceIn(0f, currentEntries.lastIndex.toFloat())
                        coroutineScope.launch { focusAnimatable.snapTo(next) }
                    }
                },
                onFlingReleased = { velocityPxPerSec, pitchPx ->
                    val currentEntries = latestEntries
                    if (currentEntries.size > 1) {
                        val target = Bookshelf3dSnapPhysics.computeFlingTargetIndex(
                            currentFocus = focusAnimatable.value,
                            velocityPxPerSec = velocityPxPerSec,
                            pitchPx = pitchPx,
                            itemCount = currentEntries.size,
                        )
                        animateFocusTo(target)
                    }
                },
                onBookTap = handleBookTap,
                onBookLongPress = { index ->
                    latestEntries.getOrNull(index)?.item?.let { latestOnItemLongClick(it) }
                },
            )

            Bookshelf3dFastScroller(
                entries = entries,
                continueReadingCount = continueReadingCount,
                focusOffset = displayFocusOffset,
                focusedIndex = focusedIndex,
                isScrubbing = isScrubbing,
                isEinkMode = isEinkMode,
                onScrubStart = { fraction ->
                    isScrubbing = true
                    val rawNext = Bookshelf3dSnapPhysics.fractionToFocus(fraction, entries.size)
                    val next = if (latestIsEinkMode) rawNext.roundToInt().toFloat() else rawNext
                    if (abs(focusAnimatable.value - next) > 0.001f) {
                        coroutineScope.launch { focusAnimatable.snapTo(next) }
                    }
                },
                onScrubMove = { fraction ->
                    val rawNext = Bookshelf3dSnapPhysics.fractionToFocus(fraction, entries.size)
                    val next = if (latestIsEinkMode) rawNext.roundToInt().toFloat() else rawNext
                    if (abs(focusAnimatable.value - next) > 0.001f) {
                        coroutineScope.launch { focusAnimatable.snapTo(next) }
                    }
                },
                onScrubEnd = {
                    isScrubbing = false
                    animateFocusTo(focusAnimatable.value.roundToInt())
                },
                onTapJumpFraction = { fraction ->
                    val target = Bookshelf3dSnapPhysics.fractionToFocus(fraction, entries.size).roundToInt()
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
                    shelfOrderNumber = focusedEntry.shelfOrderNumber,
                    totalCount = entries.size,
                    isContinueReading = focusedEntry.isContinueReading,
                    isSelected = focusedItem.aid in selectedAidSet,
                    isMultiSelectActive = isMultiSelectActive,
                    isEinkMode = isEinkMode,
                    isCompactHeight = availableHeight.value < Bookshelf3dGeometry.COMPACT_HEIGHT_THRESHOLD_DP,
                    onOpenDetail = {
                        savedFocusedAid = focusedItem.aid
                        onItemClick(focusedItem)
                    },
                    onLongPress = { onItemLongClick(focusedItem) },
                    onContinueReading = {
                        savedFocusedAid = focusedItem.aid
                        onContinueReadingClick(focusedItem)
                    },
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
                    shelfOrderNumber = focusedEntry.shelfOrderNumber,
                    totalCount = entries.size,
                    isContinueReading = focusedEntry.isContinueReading,
                    isSelected = focusedItem.aid in selectedAidSet,
                    isMultiSelectActive = isMultiSelectActive,
                    isEinkMode = isEinkMode,
                    onClick = {
                        savedFocusedAid = focusedItem.aid
                        onItemClick(focusedItem)
                    },
                    onLongClick = { onItemLongClick(focusedItem) },
                    onContinueReading = {
                        savedFocusedAid = focusedItem.aid
                        onContinueReadingClick(focusedItem)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun Bookshelf3dStage(
    entries: List<Bookshelf3dShelfEntry<BookshelfItemModel>>,
    focusOffset: Float,
    focusedIndex: Int,
    metrics: Bookshelf3dMetrics,
    selectedAidSet: Set<Int>,
    isScrubbing: Boolean,
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
    val reusableShadowPath = remember { Path() }

    val visiblePoses = Bookshelf3dGeometry.computeVisiblePosesPx(
        itemCount = entries.size,
        focus = focusOffset,
        metrics = metrics,
        density = densityFactor,
        discreteExtraction = isEinkMode,
    )

    val focusedPose = visiblePoses.firstOrNull { it.index == focusedIndex }
    val focusedEntry = focusedPose?.let { entries.getOrNull(it.index) }
    val sharedCtx = LocalWenku8SharedTransition.current
    val focusedCoverSharedState = if (sharedCtx != null && !isEinkMode && focusedEntry != null) {
        with(sharedCtx.sharedTransitionScope) {
            rememberSharedContentState(
                key = Wenku8SharedElementKeys.novelCoverKey(
                    Wenku8SharedElementKeys.NOVEL_BOOKSHELF_3D_ORIGIN,
                    focusedEntry.item.aid,
                ),
            )
        }
    } else {
        null
    }
    val isFocusedCoverTransitionActive = sharedCtx != null &&
        focusedCoverSharedState != null &&
        focusedCoverSharedState.isMatchFound &&
        sharedCtx.sharedTransitionScope.isTransitionActive
    val returnSlantedFraction = if (sharedCtx != null && !isEinkMode) {
        val animatedSlant by sharedCtx.animatedVisibilityScope.transition.animateFloat(
            transitionSpec = { expressiveSharedFloatSpec(isEinkMode = false) },
            label = "bookshelf_3d_cover_return_slant",
        ) { enterExitState ->
            if (enterExitState == EnterExitState.Visible) 1f else 0f
        }
        animatedSlant
    } else {
        1f
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
    val shadowGradientColors = remember(shadowBaseColor) {
        listOf(shadowBaseColor, Color.Transparent)
    }

    Box(
        modifier = Modifier
            .width(metrics.stageWidthDp.dp)
            .height(metrics.stageHeightDp.dp)
            .clipToBounds()
            .draggable(
                state = draggableState,
                orientation = Orientation.Horizontal,
                enabled = entries.size > 1,
                onDragStopped = { velocityPxPerSec ->
                    latestOnFlingReleased(velocityPxPerSec, latestPitchPx)
                },
            )
            .pointerInput(entries.size) {
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
                for (poseIdx in visiblePoses.indices) {
                    val poly = visiblePoses[poseIdx].floorShadowPolygon
                    if (poly.size >= 4) {
                        reusableShadowPath.rewind()
                        var minX = poly[0].x
                        var maxX = minX
                        var minY = poly[0].y
                        var maxY = minY
                        reusableShadowPath.moveTo(minX, minY)
                        for (i in 1 until poly.size) {
                            val pt = poly[i]
                            reusableShadowPath.lineTo(pt.x, pt.y)
                            if (pt.x < minX) minX = pt.x
                            if (pt.x > maxX) maxX = pt.x
                            if (pt.y < minY) minY = pt.y
                            if (pt.y > maxY) maxY = pt.y
                        }
                        reusableShadowPath.close()
                        drawPath(
                            path = reusableShadowPath,
                            brush = Brush.linearGradient(
                                colors = shadowGradientColors,
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
            val entry = entries[pose.index]
            val item = entry.item
            val isBookFocused = pose.index == focusedIndex
            key(item.aid) {
                Bookshelf3dBookVolume(
                    item = item,
                    displayNumber = entry.shelfOrderNumber,
                    isContinueReading = entry.isContinueReading,
                    pose = pose,
                    metrics = metrics,
                    spineMatrix = spineMatrix,
                    coverMatrix = coverMatrix,
                    isFocused = isBookFocused,
                    shouldLoadCover = Bookshelf3dGeometry.shouldLoadBookCover(
                        index = pose.index,
                        focusedIndex = focusedIndex,
                        itemCount = entries.size,
                        isScrubbing = isScrubbing,
                    ),
                    hideCoverArtworkForSharedTransition = isBookFocused && isFocusedCoverTransitionActive,
                    isSelected = item.aid in selectedAidSet,
                    isDark = isDark,
                    isEinkMode = isEinkMode,
                )
            }
        }

        // Layer 2b: Screen-space shared-element cover anchor placed at the exact post-matrix
        // projected visual bounds of the focused 3D cover (invisible at rest; renders in the root
        // overlay during Detail <-> Bookshelf transitions, smoothly morphing upright -> 3D slant +
        // horizontal foreshortening so returning covers land seamlessly with zero pop).
        if (focusedPose != null && focusedEntry != null && focusedCoverSharedState != null && sharedCtx != null) {
            val focusedItem = focusedEntry.item
            val visualCoverRect = Bookshelf3dGeometry.computeCoverArtworkVisualBoundsPx(
                pose = focusedPose,
                density = densityFactor,
            )
            val anchorWidthDp = with(density) { visualCoverRect.width.toDp() }
            val anchorHeightDp = with(density) { visualCoverRect.height.toDp() }
            val mountOverlayCover = sharedCtx.sharedTransitionScope.isTransitionActive
            val morphCornerShape = RoundedCornerShape(
                Bookshelf3dGeometry.interpolatedCoverCornerDp(returnSlantedFraction).dp,
            )
            key(focusedItem.aid) {
                Box(
                    modifier = Modifier
                        .offset {
                            IntOffset(
                                x = visualCoverRect.left.roundToInt(),
                                y = visualCoverRect.top.roundToInt(),
                            )
                        }
                        .size(width = anchorWidthDp, height = anchorHeightDp)
                        .testTag(BookshelfTestTags.SPINE_3D_COVER_SHARED_ANCHOR)
                        .wenku8SharedNovelCover(
                            origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_3D_ORIGIN,
                            aid = focusedItem.aid,
                            clipShape = null,
                            enabled = !isEinkMode,
                            sharedContentState = focusedCoverSharedState,
                        )
                        .graphicsLayer {
                            alpha = if (isFocusedCoverTransitionActive) 1f else 0f
                        },
                ) {
                    if (mountOverlayCover) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .bookshelf3dCoverPerspectiveMorph(
                                    slantedFraction = returnSlantedFraction,
                                )
                                .clip(morphCornerShape)
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(24.dp),
                            )
                            if (focusedItem.coverUrl.isNotBlank()) {
                                AsyncImage(
                                    model = WenkuImageRequests.rememberCoverImageRequest(
                                        aid = focusedItem.aid,
                                        coverUrl = focusedItem.coverUrl,
                                        enableCrossfade = false,
                                    ),
                                    contentDescription = focusedItem.novel.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Layer 3: Exposed per-book semantics & hit-target anchors (exact unoccluded regions)
        Layout(
            content = {
                visiblePoses.forEach { pose ->
                    val entry = entries[pose.index]
                    val item = entry.item
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
 * Applies smooth isometric vertical shear and horizontal perspective foreshortening around the
 * cover's center without altering its reported layout bounds (`LayoutCoordinates`), enabling
 * seamless 3D <-> flat shared-element cover morphs in both directions.
 */
internal fun Modifier.bookshelf3dCoverPerspectiveMorph(
    slantedFraction: Float,
): Modifier {
    val t = slantedFraction.coerceIn(0f, 1f)
    if (t <= 0.001f) return this
    return this
        .drawWithContent {
            val matrix = Bookshelf3dGeometry.buildInterpolatedCoverMorphMatrix(
                overlayWidthPx = size.width,
                slantedFraction = t,
            )
            drawContext.canvas.save()
            drawContext.canvas.concat(matrix)
            drawContent()
            drawContext.canvas.restore()
        }
        .layout { measurable, constraints ->
            val targetW = constraints.maxWidth.coerceAtLeast(1)
            val targetH = constraints.maxHeight.coerceAtLeast(1)
            val sx = Bookshelf3dGeometry.interpolatedCoverScaleX(t)
            val unforeshortenedW = (targetW / sx).roundToInt().coerceAtLeast(1)
            val placeable = measurable.measure(Constraints.fixed(unforeshortenedW, targetH))
            layout(targetW, targetH) {
                placeable.place(0, 0)
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
    isContinueReading: Boolean,
    pose: Bookshelf3dBookPose,
    metrics: Bookshelf3dMetrics,
    spineMatrix: Matrix,
    coverMatrix: Matrix,
    isFocused: Boolean,
    shouldLoadCover: Boolean,
    hideCoverArtworkForSharedTransition: Boolean,
    isSelected: Boolean,
    isDark: Boolean,
    isEinkMode: Boolean,
) {
    val colorScheme = MaterialTheme.colorScheme
    val spineSurfaceColor = when {
        isEinkMode -> colorScheme.surface
        isContinueReading -> colorScheme.tertiaryContainer
        else -> colorScheme.surfaceContainerHigh
    }
    val coverSleeveColor = when {
        isEinkMode -> colorScheme.surface
        isContinueReading -> colorScheme.tertiaryContainer
        isDark -> colorScheme.surfaceContainer
        else -> colorScheme.surface
    }
    val edgeBorderColor = when {
        isSelected -> colorScheme.primary
        isEinkMode -> colorScheme.onSurface
        isContinueReading -> colorScheme.tertiary.copy(alpha = 0.72f)
        else -> colorScheme.outlineVariant
    }
    val edgeBorderWidth = when {
        isSelected -> 2.dp
        isContinueReading && isEinkMode -> 1.6.dp
        isContinueReading -> 1.2.dp
        else -> 0.8.dp
    }

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
        isContinueReading -> colorScheme.tertiaryContainer
        isDark -> colorScheme.surfaceContainerHighest
        else -> colorScheme.surfaceVariant
    }
    val capEndColor = when {
        isEinkMode -> colorScheme.surface
        isContinueReading -> colorScheme.surfaceContainerHigh
        isDark -> colorScheme.surfaceContainerHigh
        else -> colorScheme.surface
    }
    val capStrokeColor = when {
        isEinkMode -> colorScheme.onSurface
        isContinueReading -> colorScheme.tertiary.copy(alpha = 0.58f)
        else -> colorScheme.outline.copy(alpha = if (isDark) 0.34f else 0.28f)
    }
    val capPageLineColor = when {
        isEinkMode -> colorScheme.onSurface.copy(alpha = 0.28f)
        isContinueReading -> colorScheme.tertiary.copy(alpha = 0.24f)
        else -> colorScheme.outline.copy(alpha = 0.18f)
    }
    val capPath = remember { Path() }
    val capGradientColors = remember(capStartColor, capEndColor) {
        listOf(capStartColor, capEndColor)
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
            isContinueReading = isContinueReading,
            sleeveColor = coverSleeveColor,
            borderColor = edgeBorderColor,
            borderWidth = edgeBorderWidth,
            extractionWeight = pose.weight,
            shouldLoadCover = shouldLoadCover,
            hideCoverArtworkForSharedTransition = hideCoverArtworkForSharedTransition,
            isSelected = isSelected,
            isEinkMode = isEinkMode,
        )
    }

    // 2. Top Page-Edge Cap (parallelogram sharing vertices with both spine & cover)
    Canvas(modifier = Modifier.fillMaxSize()) {
        val quad = pose.topCapQuad
        capPath.rewind()
        capPath.moveTo(quad[0].x, quad[0].y)
        capPath.lineTo(quad[1].x, quad[1].y)
        capPath.lineTo(quad[2].x, quad[2].y)
        capPath.lineTo(quad[3].x, quad[3].y)
        capPath.close()
        if (isEinkMode) {
            drawPath(path = capPath, color = capStartColor)
        } else {
            drawPath(
                path = capPath,
                brush = Brush.linearGradient(
                    colors = capGradientColors,
                    start = quad[0],
                    end = quad[2],
                ),
            )
        }

        // Subtle inner paper-page edge lines along the depth axis
        for (stepIdx in TOP_CAP_PAGE_LINE_STEPS.indices) {
            val step = TOP_CAP_PAGE_LINE_STEPS[stepIdx]
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
            path = capPath,
            color = if (isSelected) colorScheme.primary else capStrokeColor,
            style = when {
                isSelected -> CAP_STROKE_SELECTED
                isContinueReading -> CAP_STROKE_CONTINUE
                else -> CAP_STROKE_NORMAL
            },
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
            isContinueReading = isContinueReading,
            spineColor = spineSurfaceColor,
            borderColor = edgeBorderColor,
            borderWidth = edgeBorderWidth,
            isFocused = isFocused,
            isSelected = isSelected,
            isEinkMode = isEinkMode,
        )
    }
}

private val TOP_CAP_PAGE_LINE_STEPS = floatArrayOf(0.35f, 0.68f)
private val CAP_STROKE_NORMAL = Stroke(width = 1f)
private val CAP_STROKE_CONTINUE = Stroke(width = 1.5f)
private val CAP_STROKE_SELECTED = Stroke(width = 2.2f)
private val COVER_CLIP_SHAPE = RoundedCornerShape(3.dp)

@Composable
private fun Bookshelf3dCoverFace(
    item: BookshelfItemModel,
    displayNumber: Int,
    isContinueReading: Boolean,
    sleeveColor: Color,
    borderColor: Color,
    borderWidth: Dp,
    extractionWeight: Float,
    shouldLoadCover: Boolean,
    hideCoverArtworkForSharedTransition: Boolean,
    isSelected: Boolean,
    isEinkMode: Boolean,
) {
    val coverClipShape = COVER_CLIP_SHAPE
    val labelColor = if (isContinueReading && !isEinkMode) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .height(14.dp),
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
                        fontWeight = if (isContinueReading) FontWeight.Bold else FontWeight.Normal,
                    ),
                    color = labelColor,
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
                } else if (isContinueReading) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isEinkMode) {
                                    MaterialTheme.colorScheme.onSurface
                                } else {
                                    MaterialTheme.colorScheme.tertiary
                                },
                            ),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(coverClipShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = if (hideCoverArtworkForSharedTransition) 0f else 1f
                        },
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
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(11.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "WK-${item.aid.toString().padStart(5, '0')}",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 7.5.sp,
                        lineHeight = 9.sp,
                        letterSpacing = 0.5.sp,
                    ),
                    color = labelColor.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
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
    isContinueReading: Boolean,
    spineColor: Color,
    borderColor: Color,
    borderWidth: Dp,
    isFocused: Boolean,
    isSelected: Boolean,
    isEinkMode: Boolean,
) {
    val activeAccent = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isContinueReading && !isEinkMode -> MaterialTheme.colorScheme.tertiary
        isContinueReading && isEinkMode -> MaterialTheme.colorScheme.onSurface
        isFocused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val spineTitleColor = if (isContinueReading && !isEinkMode) {
        MaterialTheme.colorScheme.onTertiaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(spineColor)
            .border(borderWidth, borderColor),
    ) {
        // Distinct top bookmark-ribbon stripe for Continue Reading volumes
        if (isContinueReading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isEinkMode) 4.dp else 3.5.dp)
                    .align(Alignment.TopCenter)
                    .background(
                        if (isEinkMode) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.tertiary
                        },
                    ),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 1.dp,
                    end = 1.dp,
                    top = if (isContinueReading) 7.dp else 6.dp,
                    bottom = 6.dp,
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = displayNumber.toString().padStart(2, '0'),
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 7.5.sp,
                    lineHeight = 9.sp,
                    fontWeight = if (isFocused || isContinueReading) FontWeight.Bold else FontWeight.Medium,
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
                    color = spineTitleColor,
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
                        .background(
                            if (isContinueReading && !isEinkMode) {
                                MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            },
                        ),
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
    entries: List<Bookshelf3dShelfEntry<BookshelfItemModel>>,
    continueReadingCount: Int,
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
    val itemCount = entries.size
    val fraction = Bookshelf3dSnapPhysics.focusToFraction(focusOffset, itemCount)
    val digits = if (itemCount >= 100) 3 else 2
    val counterLabel = "${(focusedIndex + 1).toString().padStart(digits, '0')} / ${itemCount.toString().padStart(digits, '0')}"
    val indexAccessibilityDesc = stringResource(
        R.string.bookshelf_spine3d_index_desc_format,
        focusedIndex + 1,
        itemCount,
    )
    val scrollerDesc = stringResource(R.string.bookshelf_spine3d_fast_scroller_desc)
    val focusedEntry = entries.getOrNull(focusedIndex)
    val focusedTitle = focusedEntry?.item?.novel?.title.orEmpty()
    val focusedOrderTag = focusedEntry?.let {
        "#${it.shelfOrderNumber.toString().padStart(digits, '0')}"
    }.orEmpty()

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
    val continueZoneColor = if (isEinkMode) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.tertiary.copy(alpha = 0.34f)
    }
    val continueTickColor = if (isEinkMode) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.tertiary
    }
    val thumbColor = if (focusedEntry?.isContinueReading == true && !isEinkMode) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }
    val thumbGripColor = if (focusedEntry?.isContinueReading == true && !isEinkMode) {
        MaterialTheme.colorScheme.onTertiary
    } else {
        MaterialTheme.colorScheme.onPrimary
    }
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
                    text = "$counterLabel ($focusedOrderTag) · $focusedTitle",
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
                        val thumbWidth = (if (isScrubbing && !isEinkMode) 30.dp else 24.dp).toPx()
                        val thumbHeight = (if (isScrubbing && !isEinkMode) 18.dp else 14.dp).toPx()
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

                        // Leading Continue Reading highlight zone on the track
                        if (continueReadingCount > 0 && itemCount > 1) {
                            val endFraction = ((continueReadingCount - 1).coerceAtLeast(0).toFloat() / (itemCount - 1))
                                .coerceIn(0f, 1f)
                            val zoneWidth = (usableWidth * endFraction).coerceAtLeast(trackHeight)
                            drawRoundRect(
                                color = continueZoneColor,
                                topLeft = Offset(halfThumb, centerY - trackHeight / 2f),
                                size = Size(zoneWidth, trackHeight),
                                cornerRadius = CornerRadius(trackHeight / 2f, trackHeight / 2f),
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
                                    val isContinueTick = i < continueReadingCount
                                    drawCircle(
                                        color = if (isContinueTick) continueTickColor else tickColor,
                                        radius = if (isContinueTick) tickRadius * 1.25f else tickRadius,
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

@Composable
private fun ContinueReadingSpineBadge(
    isEinkMode: Boolean,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isEinkMode) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.tertiaryContainer
    }
    val contentColor = if (isEinkMode) {
        MaterialTheme.colorScheme.surface
    } else {
        MaterialTheme.colorScheme.onTertiaryContainer
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = CircleShape,
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag(BookshelfTestTags.SPINE_3D_CONTINUE_BADGE),
    ) {
        Text(
            text = stringResource(R.string.bookshelf_continue_reading_section),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
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
    shelfOrderNumber: Int,
    totalCount: Int,
    isContinueReading: Boolean,
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
    val digits = if (totalCount >= 100) 3 else 2
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = cardShape,
        border = when {
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isContinueReading && !isEinkMode -> BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
            )
            else -> null
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
                    text = "#${shelfOrderNumber.toString().padStart(digits, '0')}",
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    ),
                    color = if (isContinueReading && !isEinkMode) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                )
                Text(
                    text = item.novel.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .wenku8SharedNovelTitle(
                            origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_3D_ORIGIN,
                            aid = item.aid,
                        ),
                )
                if (isContinueReading) {
                    ContinueReadingSpineBadge(isEinkMode = isEinkMode)
                }
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
    shelfOrderNumber: Int,
    totalCount: Int,
    isContinueReading: Boolean,
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
        border = when {
            isSelected -> BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            isContinueReading && !isEinkMode -> BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
            )
            else -> null
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
            targetState = Triple(item, shelfOrderNumber, isContinueReading),
            transitionSpec = {
                if (isEinkMode) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    fadeIn() togetherWith fadeOut()
                }
            },
            label = "bookshelf_3d_wide_spotlight",
        ) { (target, targetOrderNumber, targetIsContinue) ->
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
                        text = "#${targetOrderNumber.toString().padStart(digits, '0')} / ${totalCount.toString().padStart(digits, '0')} · WK-${target.aid.toString().padStart(5, '0')}",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        color = if (targetIsContinue && !isEinkMode) {
                            MaterialTheme.colorScheme.tertiary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (targetIsContinue) {
                            ContinueReadingSpineBadge(isEinkMode = isEinkMode)
                        }
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
                }

                Text(
                    text = target.novel.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.wenku8SharedNovelTitle(
                        origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_3D_ORIGIN,
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
