package org.mewx.wenku8.ui.bookshelf

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Matrix
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Pure geometry, projection, hit-testing, snap physics, and spine typography engine for the
 * 3D bookshelf view (`BookshelfViewMode.SPINE_3D`), inspired by `Wenku8_Flutter`'s isometric
 * extraction-wave bookshelf while adapting across compact phones, foldables, tablets, and
 * compact-height landscape windows.
 *
 * Coordinate convention:
 * - Each standing book volume has 3 faces sharing exact physical vertices:
 *   1. Front spine (`width = spineWidth`, `height = bodyHeight`, sheared downward-right by `SPINE_SLOPE_Y`).
 *   2. Top page-edge cap (parallelogram connecting spine top to receding cover top).
 *   3. Projected side cover (`width = coverWidth`, `height = bodyHeight`, foreshortened by `DEPTH_X`
 *      and rising by `DEPTH_Y`).
 * - Books are ordered strictly left-to-right (`index = 0..count-1`), giving a fixed back-to-front
 *   3D occlusion order without mid-scroll z-fighting or layer pops.
 */
object Bookshelf3dGeometry {
    const val DEPTH_X: Float = 0.66f
    const val DEPTH_Y: Float = -0.30f
    const val SPINE_SLOPE_Y: Float = 0.10f
    const val ROW_STEP_DP: Float = 2.5f
    const val ROW_CURVE_DP: Float = 0.45f
    const val EXTRACTION_RADIUS: Float = 1.48f
    const val LIFT_HEIGHT_FRACTION: Float = 0.56f
    const val PULL_COVER_FRACTION: Float = 0.84f

    const val WIDE_LAYOUT_MIN_WIDTH_DP: Float = 720f
    const val WIDE_LAYOUT_MIN_HEIGHT_DP: Float = 300f
    const val COMPACT_HEIGHT_THRESHOLD_DP: Float = 480f

    /**
     * Visibility-aware cover-loading window around `focusedIndex`:
     * - Left-of-focus books (`delta <= -2`) have their cover face completely occluded by the books
     *   in front of them, so only `delta == -1` is preloaded for smooth right-swipe transitions.
     * - Right-of-focus books expose narrow cover slices up to `delta == +2` (and the very last book
     *   on the shelf exposes its entire right cover up to `+4`).
     */
    const val COVER_LOAD_LEFT_RADIUS: Int = 1
    const val COVER_LOAD_RIGHT_RADIUS: Int = 2
    const val COVER_LOAD_SCRUB_RIGHT_RADIUS: Int = 1
    const val COVER_LOAD_LAST_ITEM_RIGHT_RADIUS: Int = 4

    /**
     * Returns `true` when the book at [index] has a visible or immediately adjacent cover face.
     *
     * During active fast-scrollbar scrubbing (`isScrubbing == true`), we still keep the focused
     * book and its immediate neighbors (`[-1..+1]`, plus the exposed trailing book) mounted so
     * covers stay visible instead of reverting to placeholder icons, while capping simultaneous
     * requests and letting Coil serve cached covers synchronously and cancel passed ones.
     */
    fun shouldLoadBookCover(
        index: Int,
        focusedIndex: Int,
        itemCount: Int,
        isScrubbing: Boolean,
    ): Boolean {
        if (itemCount <= 0 || index !in 0 until itemCount) return false
        val delta = index - focusedIndex
        val rightRadius = if (isScrubbing) COVER_LOAD_SCRUB_RIGHT_RADIUS else COVER_LOAD_RIGHT_RADIUS
        if (delta in -COVER_LOAD_LEFT_RADIUS..rightRadius) return true
        return index == itemCount - 1 && delta in 0..COVER_LOAD_LAST_ITEM_RIGHT_RADIUS
    }

    /**
     * Computes responsive dp-space metrics for the 3D bookshelf given the available container size.
     */
    fun computeMetrics(
        availableWidthDp: Float,
        availableHeightDp: Float,
    ): Bookshelf3dMetrics {
        val safeWidth = availableWidthDp.coerceAtLeast(240f)
        val safeHeight = availableHeightDp.coerceAtLeast(260f)
        val isTwoPane = safeWidth >= WIDE_LAYOUT_MIN_WIDTH_DP &&
            safeHeight >= WIDE_LAYOUT_MIN_HEIGHT_DP

        val stageWidthDp = if (isTwoPane) {
            ((safeWidth - 32f) * 0.54f).coerceIn(320f, 520f)
        } else {
            safeWidth.coerceIn(240f, 540f)
        }

        val heightScale = if (safeHeight < COMPACT_HEIGHT_THRESHOLD_DP) {
            ((safeHeight - 56f) / 420f).coerceIn(0.62f, 1.0f)
        } else {
            1.0f
        }

        val spineWidthDp = (stageWidthDp * 0.045f).coerceIn(14f, 19f)
        val gapDp = (stageWidthDp * 0.028f).coerceIn(9f, 12f)
        val pitchDp = spineWidthDp + gapDp
        val coverWidthDp = ((stageWidthDp * 0.32f).coerceIn(88f, 130f) * heightScale)
            .coerceAtLeast(72f)
        val bodyHeightDp = (((coverWidthDp - 20f).coerceAtLeast(50f)) * 166f / 118f + 54f * heightScale)
            .coerceIn(116f, 212f)
        val footprintDp = spineWidthDp + coverWidthDp * DEPTH_X
        val pullDistanceDp = coverWidthDp * PULL_COVER_FRACTION
        val baseYDp = -coverWidthDp * DEPTH_Y + bodyHeightDp * LIFT_HEIGHT_FRACTION + 10f * heightScale
        val stageHeightDp = baseYDp + bodyHeightDp + 32f * heightScale

        val maxSideSlots = ceil(
            (stageWidthDp / 2f + footprintDp + pullDistanceDp * DEPTH_X) / pitchDp,
        ).toInt() + 2

        return Bookshelf3dMetrics(
            availableWidthDp = safeWidth,
            availableHeightDp = safeHeight,
            isTwoPane = isTwoPane,
            stageWidthDp = stageWidthDp,
            stageHeightDp = stageHeightDp,
            spineWidthDp = spineWidthDp,
            pitchDp = pitchDp,
            coverWidthDp = coverWidthDp,
            bodyHeightDp = bodyHeightDp,
            footprintDp = footprintDp,
            pullDistanceDp = pullDistanceDp,
            baseYDp = baseYDp,
            maxVisibleSideSlots = maxSideSlots,
        )
    }

    /**
     * Smooth cosine-squared extraction bell curve peaking at `1f` when `delta == 0f` and
     * vanishing smoothly to `0f` for `|delta| >= EXTRACTION_RADIUS`.
     */
    fun extractionWeight(delta: Float): Float {
        val distance = abs(delta)
        if (distance >= EXTRACTION_RADIUS) return 0f
        val c = cos((distance / EXTRACTION_RADIUS) * (PI / 2.0)).toFloat()
        return c * c
    }

    /**
     * Subtle curved rail vertical offset in dp so flanking books arc softly away from the crest.
     */
    fun railOffsetYDp(delta: Float): Float {
        val clamped = delta.coerceIn(-7f, 7f)
        return clamped * ROW_STEP_DP + clamped * clamped * ROW_CURVE_DP
    }

    /**
     * Computes the inclusive visible book index range `[first..last]` to lay out & paint.
     * Keeps large libraries (100+ books) constant-time per frame.
     */
    fun computeVisibleIndexRange(
        itemCount: Int,
        focus: Float,
        metrics: Bookshelf3dMetrics,
    ): IntRange {
        if (itemCount <= 0) return IntRange.EMPTY
        val center = focus.roundToInt().coerceIn(0, itemCount - 1)
        val start = (center - metrics.maxVisibleSideSlots).coerceAtLeast(0)
        val end = (center + metrics.maxVisibleSideSlots).coerceAtMost(itemCount - 1)
        return start..end
    }

    /**
     * Computes exact pixel-space poses strictly culled to books intersecting the stage viewport,
     * avoiding composing off-screen 3D volumes that would otherwise be clipped away.
     */
    fun computeVisiblePosesPx(
        itemCount: Int,
        focus: Float,
        metrics: Bookshelf3dMetrics,
        density: Float,
    ): List<Bookshelf3dBookPose> {
        val range = computeVisibleIndexRange(itemCount, focus, metrics)
        if (range.isEmpty()) return emptyList()
        val scale = density.coerceAtLeast(0.5f)
        val stageWidthPx = metrics.stageWidthDp * scale
        val marginPx = 2f * scale
        val result = ArrayList<Bookshelf3dBookPose>(range.count())
        for (idx in range) {
            val pose = computeBookPosePx(
                index = idx,
                focus = focus,
                metrics = metrics,
                density = scale,
            )
            if (pose.solidBounds.right >= -marginPx && pose.solidBounds.left <= stageWidthPx + marginPx) {
                result.add(pose)
            }
        }
        return result
    }

    /**
     * Computes the exact pixel-space 3D pose for a single book volume at `index`.
     */
    fun computeBookPosePx(
        index: Int,
        focus: Float,
        metrics: Bookshelf3dMetrics,
        density: Float,
    ): Bookshelf3dBookPose {
        val scale = density.coerceAtLeast(0.5f)
        val stageWidthPx = metrics.stageWidthDp * scale
        val spinePx = metrics.spineWidthDp * scale
        val pitchPx = metrics.pitchDp * scale
        val coverPx = metrics.coverWidthDp * scale
        val heightPx = metrics.bodyHeightDp * scale
        val footprintPx = metrics.footprintDp * scale
        val pullDistPx = metrics.pullDistanceDp * scale
        val baseYPx = metrics.baseYDp * scale

        val delta = index - focus
        val weight = extractionWeight(delta)
        val pullPx = pullDistPx * weight * (1.24f - 0.24f * weight)
        val lift = weight * weight * (3f - 2f * weight)
        val railYPx = railOffsetYDp(delta) * scale

        val anchorCenterX = stageWidthPx / 2f - footprintPx / 2f + pullDistPx * DEPTH_X
        val slotBaseX = anchorCenterX + delta * pitchPx
        val originX = slotBaseX - pullPx * DEPTH_X
        val originY = baseYPx - pullPx * DEPTH_Y - heightPx * LIFT_HEIGHT_FRACTION * lift + railYPx

        val spineSlopeDy = spinePx * SPINE_SLOPE_Y
        val depthDx = coverPx * DEPTH_X
        val depthDy = coverPx * DEPTH_Y

        val p0 = Offset(originX, originY)
        val p1 = Offset(originX + spinePx, originY + spineSlopeDy)
        val p2 = Offset(p1.x + depthDx, p1.y + depthDy)
        val p3 = Offset(p0.x + depthDx, p0.y + depthDy)
        val b0 = Offset(p0.x, p0.y + heightPx)
        val b1 = Offset(p1.x, p1.y + heightPx)
        val b2 = Offset(p2.x, p2.y + heightPx)

        val spineQuad = listOf(p0, p1, b1, b0)
        val coverQuad = listOf(p1, p2, b2, b1)
        val topCapQuad = listOf(p0, p1, p2, p3)

        val floorOrigin = Offset(slotBaseX, baseYPx + heightPx + railYPx)
        val floorFront = Offset(spinePx, 1.4f * scale)
        val floorDepth = Offset(depthDx, depthDy)
        val floorTail = Offset(-10f * scale, 10f * scale)
        val floorShadowPoly = listOf(
            floorOrigin,
            floorOrigin + floorFront,
            floorOrigin + floorFront + floorDepth,
            floorOrigin + floorDepth,
            floorOrigin + floorDepth + floorTail,
            floorOrigin + floorTail,
        )

        val solidBounds = Rect(
            left = p0.x,
            top = p3.y,
            right = p2.x,
            bottom = b1.y,
        )

        return Bookshelf3dBookPose(
            index = index,
            delta = delta,
            weight = weight,
            origin = p0,
            spineWidthPx = spinePx,
            coverWidthPx = coverPx,
            bodyHeightPx = heightPx,
            spineQuad = spineQuad,
            coverQuad = coverQuad,
            topCapQuad = topCapQuad,
            floorShadowPolygon = floorShadowPoly,
            solidBounds = solidBounds,
        )
    }

    /**
     * Builds the 4×4 Compose [Matrix] for transforming the flat `[0..spineWidth] × [0..bodyHeight]`
     * spine face onto its sheared 3D screen position (relative to `(0, 0)` of the face's own box).
     */
    fun buildLocalSpineMatrix(): Matrix = buildAffine2dMatrix(
        scaleX = 1f,
        skewX = 0f,
        transX = 0f,
        skewY = SPINE_SLOPE_Y,
        scaleY = 1f,
        transY = 0f,
    )

    /**
     * Builds the 4×4 Compose [Matrix] for transforming the flat `[0..coverWidth] × [0..bodyHeight]`
     * cover face onto its isometric 3D projection (relative to `(0, 0)` of the cover box placed at
     * `p1 = (origin.x + spineWidth, origin.y + spineWidth * SPINE_SLOPE_Y)`).
     */
    fun buildLocalCoverMatrix(): Matrix = buildAffine2dMatrix(
        scaleX = DEPTH_X,
        skewX = 0f,
        transX = 0f,
        skewY = DEPTH_Y,
        scaleY = 1f,
        transY = 0f,
    )

    /**
     * Builds a 4×4 Compose [Matrix] representing the 2D affine transform:
     * `x' = scaleX * x + skewX * y + transX`
     * `y' = skewY * x + scaleY * y + transY`
     *
     * Note on Compose [Matrix] storage: Compose stores column-major values where `matrix[row, col]`
     * is `values[row * 4 + col]`... Wait: `Matrix.map(Offset)` computes:
     * `x' = values[0]*x + values[4]*y + values[12]`
     * `y' = values[1]*x + values[5]*y + values[13]`
     * (standard column-major OpenGL layout: `values[col * 4 + row]`).
     */
    internal fun buildAffine2dMatrix(
        scaleX: Float,
        skewX: Float,
        transX: Float,
        skewY: Float,
        scaleY: Float,
        transY: Float,
    ): Matrix {
        val values = FloatArray(16)
        // Column 0 (X basis vector)
        values[0] = scaleX
        values[1] = skewY
        // Column 1 (Y basis vector)
        values[4] = skewX
        values[5] = scaleY
        // Column 2 (Z basis vector)
        values[10] = 1f
        // Column 3 (Translation)
        values[12] = transX
        values[13] = transY
        values[15] = 1f
        return Matrix(values)
    }

    /**
     * Exact point-in-convex-quadrilateral test (works for both CW and CCW vertex order).
     */
    fun isPointInConvexQuad(point: Offset, quad: List<Offset>): Boolean {
        if (quad.size != 4) return false
        var expectedSign = 0f
        for (i in 0 until 4) {
            val a = quad[i]
            val b = quad[(i + 1) % 4]
            val cross = (b.x - a.x) * (point.y - a.y) - (b.y - a.y) * (point.x - a.x)
            if (abs(cross) < 1e-4f) continue
            if (expectedSign == 0f) {
                expectedSign = sign(cross)
            } else if (sign(cross) != expectedSign) {
                return false
            }
        }
        return true
    }

    /**
     * Front-to-back exact 3D solid hit test matching ascending paint order.
     * Returns the topmost book index whose spine, cover, or top cap contains [point], or `null`.
     */
    fun hitTestBookIndex(
        point: Offset,
        visiblePoses: List<Bookshelf3dBookPose>,
    ): Int? {
        for (i in visiblePoses.lastIndex downTo 0) {
            val pose = visiblePoses[i]
            if (!pose.solidBounds.contains(point)) continue
            if (
                isPointInConvexQuad(point, pose.spineQuad) ||
                isPointInConvexQuad(point, pose.coverQuad) ||
                isPointInConvexQuad(point, pose.topCapQuad)
            ) {
                return pose.index
            }
        }
        return null
    }

    /**
     * Computes a visible, unoccluded screen-space bounding rectangle inside the stage viewport for
     * semantics / accessibility / touch targets of book [pose] amidst [visiblePoses].
     *
     * Guarantees that when a book has exposed pixels inside the viewport, its returned rectangle's
     * center hits that exact book under [hitTestBookIndex].
     */
    fun computeExposedBoundsPx(
        pose: Bookshelf3dBookPose,
        visiblePoses: List<Bookshelf3dBookPose>,
        focusedIndex: Int,
        stageWidthPx: Float,
        stageHeightPx: Float,
    ): Rect {
        val viewport = Rect(0f, 0f, stageWidthPx, stageHeightPx)
        val clampedSolid = pose.solidBounds.intersect(viewport)
        if (clampedSolid.width <= 1f || clampedSolid.height <= 1f) {
            return Rect.Zero
        }

        val listPos = visiblePoses.indexOfFirst { it.index == pose.index }
        val nextPose = if (listPos in 0 until visiblePoses.lastIndex) visiblePoses[listPos + 1] else null
        val focusedPose = visiblePoses.firstOrNull { it.index == focusedIndex }

        // Build ordered candidate exposed strips on this book's spine / cover and pick the first
        // whose center genuinely resolves back to this book under exact 3D occlusion hit-testing.
        val candidates = buildList {
            if (pose.index == focusedIndex) {
                val exposedRight = nextPose?.origin?.x?.coerceAtLeast(pose.origin.x + pose.spineWidthPx)
                    ?: pose.solidBounds.right
                add(
                    Rect(
                        left = pose.origin.x,
                        top = pose.origin.y,
                        right = min(pose.solidBounds.right, exposedRight),
                        bottom = pose.origin.y + pose.bodyHeightPx,
                    ),
                )
                add(
                    Rect(
                        left = pose.origin.x + pose.spineWidthPx,
                        top = pose.solidBounds.top,
                        right = pose.solidBounds.right,
                        bottom = nextPose?.solidBounds?.top ?: (pose.origin.y + pose.bodyHeightPx),
                    ),
                )
            } else if (pose.index > focusedIndex) {
                val exposedRight = nextPose?.origin?.x ?: pose.solidBounds.right
                add(
                    Rect(
                        left = pose.origin.x,
                        top = pose.origin.y,
                        right = min(pose.solidBounds.right, max(exposedRight, pose.origin.x + pose.spineWidthPx)),
                        bottom = pose.origin.y + pose.bodyHeightPx,
                    ),
                )
                add(
                    Rect(
                        left = pose.origin.x,
                        top = pose.origin.y,
                        right = pose.origin.x + pose.spineWidthPx,
                        bottom = pose.origin.y + pose.bodyHeightPx,
                    ),
                )
            } else {
                // Book to the left of focus: try unobstructed full spine first, then left spine
                // strip bounded by the next neighbor, and finally lower spine peeking below the
                // lifted focal book.
                val fullSpineRight = pose.origin.x + pose.spineWidthPx
                val clippedSpineRight = nextPose?.origin?.x?.let { min(fullSpineRight, it) } ?: fullSpineRight
                add(
                    Rect(
                        left = pose.origin.x,
                        top = pose.origin.y,
                        right = fullSpineRight,
                        bottom = pose.origin.y + pose.bodyHeightPx,
                    ),
                )
                if (clippedSpineRight - pose.origin.x > 2f && clippedSpineRight < fullSpineRight) {
                    add(
                        Rect(
                            left = pose.origin.x,
                            top = pose.origin.y,
                            right = clippedSpineRight,
                            bottom = pose.origin.y + pose.bodyHeightPx,
                        ),
                    )
                }
                if (focusedPose != null) {
                    val belowFocusTop = focusedPose.solidBounds.bottom
                    val bottom = pose.origin.y + pose.bodyHeightPx
                    if (bottom - belowFocusTop > 4f) {
                        add(
                            Rect(
                                left = pose.origin.x,
                                top = belowFocusTop,
                                right = fullSpineRight,
                                bottom = bottom,
                            ),
                        )
                        if (clippedSpineRight - pose.origin.x > 2f && clippedSpineRight < fullSpineRight) {
                            add(
                                Rect(
                                    left = pose.origin.x,
                                    top = belowFocusTop,
                                    right = clippedSpineRight,
                                    bottom = bottom,
                                ),
                            )
                        }
                    }
                }
            }
        }

        for (candidate in candidates) {
            val clipped = candidate.intersect(viewport)
            if (clipped.width > 2f && clipped.height > 2f) {
                if (hitTestBookIndex(clipped.center, visiblePoses) == pose.index) {
                    return clipped
                }
            }
        }

        return clampedSolid
    }
}

/**
 * Responsive 3D bookshelf dimensions in dp for a given viewport size.
 */
data class Bookshelf3dMetrics(
    val availableWidthDp: Float,
    val availableHeightDp: Float,
    val isTwoPane: Boolean,
    val stageWidthDp: Float,
    val stageHeightDp: Float,
    val spineWidthDp: Float,
    val pitchDp: Float,
    val coverWidthDp: Float,
    val bodyHeightDp: Float,
    val footprintDp: Float,
    val pullDistanceDp: Float,
    val baseYDp: Float,
    val maxVisibleSideSlots: Int,
)

/**
 * Pixel-space 3D geometry of one book volume in the bookshelf scene.
 */
data class Bookshelf3dBookPose(
    val index: Int,
    val delta: Float,
    val weight: Float,
    val origin: Offset,
    val spineWidthPx: Float,
    val coverWidthPx: Float,
    val bodyHeightPx: Float,
    val spineQuad: List<Offset>,
    val coverQuad: List<Offset>,
    val topCapQuad: List<Offset>,
    val floorShadowPolygon: List<Offset>,
    val solidBounds: Rect,
)

/**
 * Inertial fling target & momentum helper so horizontal swipes settle on an exact integer book
 * index without reversing against a genuine user flick.
 */
object Bookshelf3dSnapPhysics {
    private const val DRAG_RATE: Float = 4.4f
    private const val MIN_RUNWAY_RATE: Float = 12.0f

    /**
     * Computes the target integer book index `[0..itemCount - 1]` for a horizontal release.
     *
     * Sign convention matches natural touch scrolling:
     * - Swiping finger LEFT (`velocityPxPerSec < 0`) increases the book index (`delta > 0`).
     * - Swiping finger RIGHT (`velocityPxPerSec > 0`) decreases the book index (`delta < 0`).
     */
    fun computeFlingTargetIndex(
        currentFocus: Float,
        velocityPxPerSec: Float,
        pitchPx: Float,
        itemCount: Int,
        minFlingVelocityPxPerSec: Float = 120f,
    ): Int {
        if (itemCount <= 1) return 0
        if (pitchPx <= 0f) return currentFocus.roundToInt().coerceIn(0, itemCount - 1)

        // Convert screen swipe velocity into index-space scroll velocity (left swipe -> +index).
        val indexVelocity = -velocityPxPerSec / pitchPx
        val absScreenSpeed = abs(velocityPxPerSec)
        val isDirectional = absScreenSpeed >= minFlingVelocityPxPerSec

        val rawTarget = if (!isDirectional) {
            currentFocus.roundToInt()
        } else {
            val projected = currentFocus + indexVelocity / DRAG_RATE
            var candidate = projected.roundToInt()
            val minRunway = abs(indexVelocity) / MIN_RUNWAY_RATE
            val dir = sign(indexVelocity)
            if ((candidate - currentFocus) * dir < minRunway) {
                val runwayFocus = currentFocus + indexVelocity / MIN_RUNWAY_RATE
                candidate = if (dir > 0f) ceil(runwayFocus).toInt() else floor(runwayFocus).toInt()
            }
            // Guarantee a genuine flick moves at least 1 slot in the flick direction if not at edge.
            val currentRounded = currentFocus.roundToInt()
            if (candidate == currentRounded) {
                candidate = currentRounded + dir.toInt()
            }
            candidate
        }

        return rawTarget.coerceIn(0, itemCount - 1)
    }

    /**
     * Maps a normalized scrub fraction `[0f..1f]` on the fast scrollbar to a continuous focus value
     * in `[0f..(itemCount - 1).toFloat()]`.
     */
    fun fractionToFocus(fraction: Float, itemCount: Int): Float {
        if (itemCount <= 1) return 0f
        return fraction.coerceIn(0f, 1f) * (itemCount - 1)
    }

    /**
     * Maps a continuous focus value `[0f..(itemCount - 1)]` to a normalized `[0f..1f]` fraction.
     */
    fun focusToFraction(focus: Float, itemCount: Int): Float {
        if (itemCount <= 1) return 0f
        return (focus / (itemCount - 1)).coerceIn(0f, 1f)
    }
}

/**
 * A typography run on a vertical book spine:
 * - CJK / non-ASCII glyphs are rendered upright (`isRotatedLatin = false`), stacked vertically.
 * - Contiguous printable ASCII / Latin runs are grouped and rotated 90° clockwise
 *   (`isRotatedLatin = true`) so English titles/numbers read naturally down the spine.
 */
data class SpineTitleRun(
    val text: String,
    val isRotatedLatin: Boolean,
)

/**
 * Splits a book title into vertical spine typography runs (grapheme-safe for emoji & CJK).
 */
fun parseSpineTitleRuns(
    title: String,
    maxRuns: Int = 8,
): Pair<List<SpineTitleRun>, Boolean> {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return emptyList<SpineTitleRun>() to false

    val allRuns = mutableListOf<SpineTitleRun>()
    val latinBuffer = StringBuilder()

    fun flushLatin() {
        val flushed = latinBuffer.toString().trim()
        if (flushed.isNotEmpty()) {
            val clipped = if (flushed.length > 20) flushed.take(19) + "…" else flushed
            allRuns.add(SpineTitleRun(text = clipped, isRotatedLatin = true))
        }
        latinBuffer.setLength(0)
    }

    val iterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    iterator.setText(trimmed)
    var start = iterator.first()
    var end = iterator.next()
    while (end != BreakIterator.DONE) {
        val grapheme = trimmed.substring(start, end)
        val isAsciiPrintable = grapheme.length == 1 && grapheme[0].code in 0x20..0x7E
        if (isAsciiPrintable) {
            latinBuffer.append(grapheme)
        } else {
            flushLatin()
            if (grapheme.isNotBlank()) {
                allRuns.add(SpineTitleRun(text = grapheme, isRotatedLatin = false))
            }
        }
        start = end
        end = iterator.next()
    }
    flushLatin()

    val truncated = allRuns.size > maxRuns
    return allRuns.take(maxRuns) to truncated
}

/**
 * One book slot placed on the 3D bookshelf, preserving its original 1-based [shelfOrderNumber]
 * from the active shelf sort order even when promoted to the front as a Continue Reading entry.
 */
data class Bookshelf3dShelfEntry<T>(
    val item: T,
    val shelfOrderNumber: Int,
    val isContinueReading: Boolean,
)

/**
 * Reorders [sortedItems] for the 3D bookshelf by promoting books whose IDs appear in
 * [continueReadingIds] (preserving recency order in [continueReadingIds]) to the front of the
 * shelf, while retaining each book's canonical 1-based `shelfOrderNumber` from [sortedItems]
 * without duplicating any book.
 */
fun <T> build3dShelfEntries(
    sortedItems: List<T>,
    continueReadingIds: List<Int>,
    idSelector: (T) -> Int,
): List<Bookshelf3dShelfEntry<T>> {
    if (sortedItems.isEmpty()) return emptyList()
    if (continueReadingIds.isEmpty()) {
        return sortedItems.mapIndexed { idx, item ->
            Bookshelf3dShelfEntry(
                item = item,
                shelfOrderNumber = idx + 1,
                isContinueReading = false,
            )
        }
    }

    val rankById = HashMap<Int, Pair<T, Int>>(sortedItems.size)
    sortedItems.forEachIndexed { idx, item ->
        rankById[idSelector(item)] = item to (idx + 1)
    }

    val promotedIds = HashSet<Int>(continueReadingIds.size)
    val promoted = ArrayList<Bookshelf3dShelfEntry<T>>(continueReadingIds.size)
    for (id in continueReadingIds) {
        if (!promotedIds.add(id)) continue
        val pair = rankById[id] ?: continue
        promoted.add(
            Bookshelf3dShelfEntry(
                item = pair.first,
                shelfOrderNumber = pair.second,
                isContinueReading = true,
            ),
        )
    }

    val remaining = ArrayList<Bookshelf3dShelfEntry<T>>(
        (sortedItems.size - promoted.size).coerceAtLeast(0),
    )
    sortedItems.forEachIndexed { idx, item ->
        val id = idSelector(item)
        if (id !in promotedIds) {
            remaining.add(
                Bookshelf3dShelfEntry(
                    item = item,
                    shelfOrderNumber = idx + 1,
                    isContinueReading = false,
                ),
            )
        }
    }

    return promoted + remaining
}
