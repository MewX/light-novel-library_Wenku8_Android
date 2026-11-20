package org.mewx.wenku8.ui.bookshelf

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Bookshelf3dGeometryTest {

    @Test
    fun computeMetrics_adaptsAcrossCompactPhone_standardPhone_foldable_tablet_andShortLandscape() {
        val compactPhone = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 320f, availableHeightDp = 568f)
        val standardPhone = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 412f, availableHeightDp = 892f)
        val foldableMedium = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 640f, availableHeightDp = 840f)
        val tabletLandscape = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 960f, availableHeightDp = 700f)
        val shortLandscapePhone = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 800f, availableHeightDp = 360f)
        val narrowSplitScreen = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 360f, availableHeightDp = 360f)

        // Compact & medium screens use single-column layout; wide tablets & landscape phones activate two-pane spotlight
        assertFalse(compactPhone.isTwoPane)
        assertFalse(standardPhone.isTwoPane)
        assertFalse(foldableMedium.isTwoPane)
        assertFalse(narrowSplitScreen.isTwoPane)
        assertTrue(tabletLandscape.isTwoPane)
        assertTrue(shortLandscapePhone.isTwoPane)
        // Short landscape phone scales cover & stage height down so shelf + scroller fit inside 360dp
        assertTrue(
            "Short landscape cover (${shortLandscapePhone.coverWidthDp}) should scale below standard (${standardPhone.coverWidthDp})",
            shortLandscapePhone.coverWidthDp < standardPhone.coverWidthDp,
        )
        assertTrue(
            "Short landscape stageHeight (${shortLandscapePhone.stageHeightDp}) should be more compact than standard (${standardPhone.stageHeightDp})",
            shortLandscapePhone.stageHeightDp < standardPhone.stageHeightDp,
        )
        assertTrue(
            "Short landscape stageHeight + scroller (${shortLandscapePhone.stageHeightDp + 46f}) should fit within 360dp",
            shortLandscapePhone.stageHeightDp + 46f <= 360f,
        )

        // Stage width never exceeds available width and caps cleanly on large screens
        assertTrue(compactPhone.stageWidthDp <= 320f)
        assertTrue(standardPhone.stageWidthDp <= 412f)
        assertTrue(foldableMedium.stageWidthDp <= 540f)
        assertTrue(tabletLandscape.stageWidthDp <= 520f)
        assertTrue(tabletLandscape.stageWidthDp >= 340f)

        // Book dimensions scale monotonically from compact phone up to standard phone
        assertTrue(compactPhone.spineWidthDp <= standardPhone.spineWidthDp)
        assertTrue(compactPhone.coverWidthDp <= standardPhone.coverWidthDp)
        assertTrue(compactPhone.bodyHeightDp <= standardPhone.bodyHeightDp)
        assertTrue(compactPhone.pitchDp > compactPhone.spineWidthDp)
        assertTrue(tabletLandscape.maxVisibleSideSlots >= 5)
    }

    @Test
    fun extractionWeight_isSymmetric_peaksAtZero_andVanishesAtRadius() {
        assertEquals(1f, Bookshelf3dGeometry.extractionWeight(0f), 1e-4f)
        assertEquals(0f, Bookshelf3dGeometry.extractionWeight(Bookshelf3dGeometry.EXTRACTION_RADIUS), 1e-4f)
        assertEquals(0f, Bookshelf3dGeometry.extractionWeight(-Bookshelf3dGeometry.EXTRACTION_RADIUS - 1f), 1e-4f)

        val wHalfPos = Bookshelf3dGeometry.extractionWeight(0.5f)
        val wHalfNeg = Bookshelf3dGeometry.extractionWeight(-0.5f)
        val wOne = Bookshelf3dGeometry.extractionWeight(1.0f)
        assertEquals(wHalfPos, wHalfNeg, 1e-5f)
        assertTrue("Weight must strictly decrease away from focus", 1f > wHalfPos && wHalfPos > wOne && wOne > 0f)
    }

    @Test
    fun localSpineAndCoverMatrices_mapFlatRectCornersToExactShared3dVertices() {
        val metrics = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 412f, availableHeightDp = 892f)
        val pose = Bookshelf3dGeometry.computeBookPosePx(
            index = 2,
            focus = 2f,
            metrics = metrics,
            density = 2.5f,
        )

        val spineMatrix = Bookshelf3dGeometry.buildLocalSpineMatrix()
        val coverMatrix = Bookshelf3dGeometry.buildLocalCoverMatrix()

        // Spine local corners offset by pose.origin must equal pose.spineQuad [p0, p1, b1, b0]
        val mappedSpineCorners = listOf(
            Offset(0f, 0f),
            Offset(pose.spineWidthPx, 0f),
            Offset(pose.spineWidthPx, pose.bodyHeightPx),
            Offset(0f, pose.bodyHeightPx),
        ).map { pose.origin + spineMatrix.map(it) }

        mappedSpineCorners.zip(pose.spineQuad).forEach { (mapped, expected) ->
            assertEquals(expected.x, mapped.x, 0.05f)
            assertEquals(expected.y, mapped.y, 0.05f)
        }

        // Cover local corners offset by p1 (= spineQuad[1]) must equal pose.coverQuad [p1, p2, b2, b1]
        val coverAnchor = pose.spineQuad[1]
        val mappedCoverCorners = listOf(
            Offset(0f, 0f),
            Offset(pose.coverWidthPx, 0f),
            Offset(pose.coverWidthPx, pose.bodyHeightPx),
            Offset(0f, pose.bodyHeightPx),
        ).map { coverAnchor + coverMatrix.map(it) }

        mappedCoverCorners.zip(pose.coverQuad).forEach { (mapped, expected) ->
            assertEquals(expected.x, mapped.x, 0.05f)
            assertEquals(expected.y, mapped.y, 0.05f)
        }

        // Top cap shares p0, p1 with spine and p1, p2 with cover seamlessly
        assertEquals(pose.spineQuad[0], pose.topCapQuad[0])
        assertEquals(pose.spineQuad[1], pose.topCapQuad[1])
        assertEquals(pose.coverQuad[1], pose.topCapQuad[2])
    }

    @Test
    fun hitTestBookIndex_respectsFrontToBackOcclusion_andExposedBoundsAlwaysHitOwnBook() {
        val metrics = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 412f, availableHeightDp = 892f)
        val density = 2f
        val stageW = metrics.stageWidthDp * density
        val stageH = metrics.stageHeightDp * density

        for (count in listOf(1, 2, 6, 20)) {
            val focus = if (count >= 6) 3f else 0f
            val visibleRange = Bookshelf3dGeometry.computeVisibleIndexRange(count, focus, metrics)
            val poses = visibleRange.map { idx ->
                Bookshelf3dGeometry.computeBookPosePx(idx, focus, metrics, density)
            }
            val focusedIndex = focus.toInt()

            // Verify focused book's cover center hits focused book
            val focusedPose = poses.first { it.index == focusedIndex }
            val focusedExposed = Bookshelf3dGeometry.computeExposedBoundsPx(
                pose = focusedPose,
                visiblePoses = poses,
                focusedIndex = focusedIndex,
                stageWidthPx = stageW,
                stageHeightPx = stageH,
            )
            assertTrue(focusedExposed.width > 10f && focusedExposed.height > 10f)
            assertEquals(
                focusedIndex,
                Bookshelf3dGeometry.hitTestBookIndex(focusedExposed.center, poses),
            )

            // Verify immediate neighbors' exposed rects resolve to themselves
            listOf(focusedIndex - 1, focusedIndex + 1)
                .filter { it in visibleRange }
                .forEach { neighborIdx ->
                    val neighborPose = poses.first { it.index == neighborIdx }
                    val exposed = Bookshelf3dGeometry.computeExposedBoundsPx(
                        pose = neighborPose,
                        visiblePoses = poses,
                        focusedIndex = focusedIndex,
                        stageWidthPx = stageW,
                        stageHeightPx = stageH,
                    )
                    if (exposed.width > 4f && exposed.height > 4f) {
                        assertEquals(
                            "Expected exposed center of neighbor $neighborIdx (count=$count) to hit itself",
                            neighborIdx,
                            Bookshelf3dGeometry.hitTestBookIndex(exposed.center, poses),
                        )
                    }
                }
        }

        // Far outside stage hits nothing
        val samplePoses = listOf(
            Bookshelf3dGeometry.computeBookPosePx(0, 0f, metrics, density),
        )
        // Top page-edge cap hit-testing on the focused book
        val focusedPose = samplePoses.first()
        val topCapCenter = Offset(
            x = focusedPose.topCapQuad.map { it.x }.average().toFloat(),
            y = focusedPose.topCapQuad.map { it.y }.average().toFloat(),
        )
        assertEquals(0, Bookshelf3dGeometry.hitTestBookIndex(topCapCenter, samplePoses))

        assertNull(Bookshelf3dGeometry.hitTestBookIndex(Offset(-500f, -500f), samplePoses))
        assertNull(Bookshelf3dGeometry.hitTestBookIndex(Offset(stageW + 500f, stageH + 500f), samplePoses))
    }

    @Test
    fun visibleIndexRangeAndPoses_cullLargeLibraryStrictlyToViewport() {
        val metrics = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 412f, availableHeightDp = 892f)
        val range = Bookshelf3dGeometry.computeVisibleIndexRange(
            itemCount = 200,
            focus = 100f,
            metrics = metrics,
        )
        assertTrue("Visible range should stay bounded (was ${range.count()})", range.count() <= 35)
        assertTrue(100 in range)

        val density = 2f
        val stageW = metrics.stageWidthDp * density
        val poses = Bookshelf3dGeometry.computeVisiblePosesPx(
            itemCount = 200,
            focus = 100f,
            metrics = metrics,
            density = density,
        )
        assertTrue("Viewport culling should drop off-screen books (${poses.size} < ${range.count()})", poses.size < range.count())
        assertTrue("Focused book must always be included", poses.any { it.index == 100 })
        poses.forEach { pose ->
            assertTrue("Culled pose ${pose.index} right edge must enter viewport", pose.solidBounds.right >= -4f)
            assertTrue("Culled pose ${pose.index} left edge must enter viewport", pose.solidBounds.left <= stageW + 4f)
        }

        assertTrue(Bookshelf3dGeometry.computeVisibleIndexRange(0, 0f, metrics).isEmpty())
        assertTrue(Bookshelf3dGeometry.computeVisiblePosesPx(0, 0f, metrics, density).isEmpty())
        assertEquals(0..0, Bookshelf3dGeometry.computeVisibleIndexRange(1, 0f, metrics))
    }

    @Test
    fun shouldLoadBookCover_keepsVisibleCoversLoadedDuringScrubbingAndCapsWindow() {
        val total = 50
        val focus = 20

        // Active fast-scrollbar scrubbing keeps the focused book + immediate neighbors [-1..+1]
        // loaded so covers stay visible instead of dropping to placeholder icons, while excluding
        // distant occluded books to cap concurrent bitmap/network churn.
        val loadedWhileScrubbing = (0 until total).filter {
            Bookshelf3dGeometry.shouldLoadBookCover(it, focus, total, isScrubbing = true)
        }
        assertEquals(listOf(19, 20, 21), loadedWhileScrubbing)

        // Settled browsing loads at most [-1..+2] = 4 covers in the middle of the shelf
        val loadedMiddle = (0 until total).filter {
            Bookshelf3dGeometry.shouldLoadBookCover(it, focus, total, isScrubbing = false)
        }
        assertEquals(listOf(19, 20, 21, 22), loadedMiddle)

        // Near the end of the shelf, the exposed last book cover (index 49) stays eligible within +4
        // both when settled and while scrubbing.
        assertTrue(Bookshelf3dGeometry.shouldLoadBookCover(49, focusedIndex = 45, itemCount = total, isScrubbing = false))
        assertTrue(Bookshelf3dGeometry.shouldLoadBookCover(49, focusedIndex = 45, itemCount = total, isScrubbing = true))
        assertFalse(Bookshelf3dGeometry.shouldLoadBookCover(49, focusedIndex = 44, itemCount = total, isScrubbing = false))
        assertFalse(Bookshelf3dGeometry.shouldLoadBookCover(49, focusedIndex = 44, itemCount = total, isScrubbing = true))

        // Out-of-bounds & empty guards
        assertFalse(Bookshelf3dGeometry.shouldLoadBookCover(0, 0, itemCount = 0, isScrubbing = false))
        assertFalse(Bookshelf3dGeometry.shouldLoadBookCover(-1, 0, itemCount = total, isScrubbing = false))
        assertFalse(Bookshelf3dGeometry.shouldLoadBookCover(total, 0, itemCount = total, isScrubbing = false))
    }

    @Test
    fun snapPhysics_handlesDirectionalFling_lowVelocitySnap_edgeClamping_andFractionMapping() {
        val pitchPx = 80f

        // Stationary release snaps to nearest integer
        assertEquals(
            3,
            Bookshelf3dSnapPhysics.computeFlingTargetIndex(
                currentFocus = 2.6f,
                velocityPxPerSec = 10f,
                pitchPx = pitchPx,
                itemCount = 10,
            ),
        )

        // Moderate directional flick right above threshold guarantees at least 1 slot advance
        assertEquals(
            3,
            Bookshelf3dSnapPhysics.computeFlingTargetIndex(
                currentFocus = 2.0f,
                velocityPxPerSec = -200f,
                pitchPx = pitchPx,
                itemCount = 20,
            ),
        )
        assertEquals(
            7,
            Bookshelf3dSnapPhysics.computeFlingTargetIndex(
                currentFocus = 8.0f,
                velocityPxPerSec = 200f,
                pitchPx = pitchPx,
                itemCount = 20,
            ),
        )
        // Flick when slightly off-integer with runway requirement (covers minRunway floor/ceil)
        assertEquals(
            4,
            Bookshelf3dSnapPhysics.computeFlingTargetIndex(
                currentFocus = 2.4f,
                velocityPxPerSec = -420f,
                pitchPx = pitchPx,
                itemCount = 20,
            ),
        )
        assertEquals(
            6,
            Bookshelf3dSnapPhysics.computeFlingTargetIndex(
                currentFocus = 7.6f,
                velocityPxPerSec = 420f,
                pitchPx = pitchPx,
                itemCount = 20,
            ),
        )

        // Left swipe (negative px/s) moves forward to a higher index
        val forwardTarget = Bookshelf3dSnapPhysics.computeFlingTargetIndex(
            currentFocus = 2.0f,
            velocityPxPerSec = -900f,
            pitchPx = pitchPx,
            itemCount = 20,
        )
        assertTrue("Left flick should advance past index 2 (got $forwardTarget)", forwardTarget > 2)

        // Right swipe (positive px/s) moves backward to a lower index
        val backwardTarget = Bookshelf3dSnapPhysics.computeFlingTargetIndex(
            currentFocus = 8.0f,
            velocityPxPerSec = 900f,
            pitchPx = pitchPx,
            itemCount = 20,
        )
        assertTrue("Right flick should retreat below index 8 (got $backwardTarget)", backwardTarget < 8)

        // Clamping at boundaries & single-book edge case
        assertEquals(
            9,
            Bookshelf3dSnapPhysics.computeFlingTargetIndex(8.5f, -5000f, pitchPx, itemCount = 10),
        )
        assertEquals(
            0,
            Bookshelf3dSnapPhysics.computeFlingTargetIndex(0.5f, 5000f, pitchPx, itemCount = 10),
        )
        assertEquals(
            0,
            Bookshelf3dSnapPhysics.computeFlingTargetIndex(0f, -1200f, pitchPx, itemCount = 1),
        )

        // Fraction <-> focus round-trip
        assertEquals(0f, Bookshelf3dSnapPhysics.focusToFraction(0f, 1), 1e-5f)
        assertEquals(0f, Bookshelf3dSnapPhysics.fractionToFocus(0.75f, 1), 1e-5f)
        assertEquals(0.5f, Bookshelf3dSnapPhysics.focusToFraction(10f, 21), 1e-5f)
        assertEquals(10f, Bookshelf3dSnapPhysics.fractionToFocus(0.5f, 21), 1e-5f)
        assertEquals(20f, Bookshelf3dSnapPhysics.fractionToFocus(1.5f, 21), 1e-5f)
    }

    @Test
    fun parseSpineTitleRuns_keepsLatinWordsIntact_stacksCjk_andClipsLongTitles() {
        val (mixedRuns, mixedTruncated) = parseSpineTitleRuns("绝对双刃absolute duo")
        assertFalse(mixedTruncated)
        assertEquals(
            listOf(
                SpineTitleRun("绝", false),
                SpineTitleRun("对", false),
                SpineTitleRun("双", false),
                SpineTitleRun("刃", false),
                SpineTitleRun("absolute duo", true),
            ),
            mixedRuns,
        )

        val (longRuns, longTruncated) = parseSpineTitleRuns("关于我转生变成史莱姆这档事特别篇", maxRuns = 7)
        assertTrue(longTruncated)
        assertEquals(7, longRuns.size)
        assertTrue(longRuns.all { !it.isRotatedLatin })

        val (emptyRuns, emptyTruncated) = parseSpineTitleRuns("   ")
        assertTrue(emptyRuns.isEmpty())
        assertFalse(emptyTruncated)

        val (longAsciiRuns, _) = parseSpineTitleRuns("Sword Art Online Progressive Aria of a Starless Night")
        assertEquals(1, longAsciiRuns.size)
        assertTrue(longAsciiRuns[0].isRotatedLatin)
        assertTrue(longAsciiRuns[0].text.endsWith("…"))
    }

    @Test
    fun build3dShelfEntries_promotesContinueReadingToFront_preservesOriginalSortNumbers_andDeduplicates() {
        val sortedBooks = listOf(101, 102, 103, 104, 105)

        // Empty continue-reading list preserves original order and 1-based numbers
        val plain = build3dShelfEntries(sortedBooks, emptyList()) { it }
        assertEquals(sortedBooks, plain.map { it.item })
        assertEquals(listOf(1, 2, 3, 4, 5), plain.map { it.shelfOrderNumber })
        assertTrue(plain.none { it.isContinueReading })

        // Empty sorted list yields empty result even if continue-reading IDs are provided
        assertTrue(build3dShelfEntries(emptyList<Int>(), listOf(105, 103)) { it }.isEmpty())

        // Continue-reading books move to the front in recency order, ignoring unknown/duplicate IDs,
        // while keeping every book's canonical 1-based sort number from the original shelf order.
        val promoted = build3dShelfEntries(
            sortedItems = sortedBooks,
            continueReadingIds = listOf(105, 999, 103, 105),
            idSelector = { it },
        )
        assertEquals(listOf(105, 103, 101, 102, 104), promoted.map { it.item })
        assertEquals(listOf(5, 3, 1, 2, 4), promoted.map { it.shelfOrderNumber })
        assertEquals(listOf(true, true, false, false, false), promoted.map { it.isContinueReading })
    }

    @Test
    fun discreteExtractionMode_quantizesFocus_liftsOnlyFocusedBook_andShiftsSlotsIdenticallyInPlace() {
        val metrics = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 412f, availableHeightDp = 760f)
        val density = 2.5f

        // Fractional focus 2.35f quantizes to integer 2f; ONLY book 2 is lifted (weight = 1f),
        // while immediate neighbors 1 and 3 stay completely flush on the shelf rail (weight = 0f).
        val posesAtFractional = Bookshelf3dGeometry.computeVisiblePosesPx(
            itemCount = 12,
            focus = 2.35f,
            metrics = metrics,
            density = density,
            discreteExtraction = true,
        )
        val posesAtTwo = Bookshelf3dGeometry.computeVisiblePosesPx(
            itemCount = 12,
            focus = 2f,
            metrics = metrics,
            density = density,
            discreteExtraction = true,
        )
        assertEquals(posesAtTwo.map { it.index }, posesAtFractional.map { it.index })
        posesAtFractional.forEachIndexed { i, pose ->
            assertEquals(posesAtTwo[i].origin.x, pose.origin.x, 1e-4f)
            assertEquals(posesAtTwo[i].origin.y, pose.origin.y, 1e-4f)
            val expectedWeight = if (pose.index == 2) 1f else 0f
            assertEquals(expectedWeight, pose.weight, 1e-5f)
        }

        // Shifting focus from 2 -> 3 shifts every book into the exact screen coordinates of the
        // preceding slot (book-by-book in-place shift with zero geometry drift).
        for (slotOffset in -2..2) {
            val poseBefore = Bookshelf3dGeometry.computeBookPosePx(
                index = 2 + slotOffset,
                focus = 2f,
                metrics = metrics,
                density = density,
                discreteExtraction = true,
            )
            val poseAfter = Bookshelf3dGeometry.computeBookPosePx(
                index = 3 + slotOffset,
                focus = 3f,
                metrics = metrics,
                density = density,
                discreteExtraction = true,
            )
            assertEquals(poseBefore.origin.x, poseAfter.origin.x, 1e-4f)
            assertEquals(poseBefore.origin.y, poseAfter.origin.y, 1e-4f)
            assertEquals(poseBefore.weight, poseAfter.weight, 1e-5f)
        }
    }

    @Test
    fun discreteStepPhysics_advancesOrRetreatsStrictlyOneBookPerSwipeAndClampsEdges() {
        val threshold = 48f

        // Large left drag + high velocity still steps strictly +1 (no multi-book inertia fling)
        assertEquals(
            4,
            Bookshelf3dSnapPhysics.computeDiscreteStepTargetIndex(
                currentIndex = 3,
                totalDragPx = -420f,
                velocityPxPerSec = -2400f,
                stepThresholdPx = threshold,
                itemCount = 20,
            ),
        )
        // Large right drag + high velocity still steps strictly -1
        assertEquals(
            2,
            Bookshelf3dSnapPhysics.computeDiscreteStepTargetIndex(
                currentIndex = 3,
                totalDragPx = 420f,
                velocityPxPerSec = 2400f,
                stepThresholdPx = threshold,
                itemCount = 20,
            ),
        )
        // Short fast flick left/right below distance threshold still steps +/-1
        assertEquals(
            4,
            Bookshelf3dSnapPhysics.computeDiscreteStepTargetIndex(
                currentIndex = 3,
                totalDragPx = -12f,
                velocityPxPerSec = -220f,
                stepThresholdPx = threshold,
                itemCount = 20,
            ),
        )
        assertEquals(
            2,
            Bookshelf3dSnapPhysics.computeDiscreteStepTargetIndex(
                currentIndex = 3,
                totalDragPx = 12f,
                velocityPxPerSec = 220f,
                stepThresholdPx = threshold,
                itemCount = 20,
            ),
        )
        // Tiny micro-jitter stays on current book
        assertEquals(
            3,
            Bookshelf3dSnapPhysics.computeDiscreteStepTargetIndex(
                currentIndex = 3,
                totalDragPx = -5f,
                velocityPxPerSec = -40f,
                stepThresholdPx = threshold,
                itemCount = 20,
            ),
        )
        // Edge clamping & single-item guard
        assertEquals(
            0,
            Bookshelf3dSnapPhysics.computeDiscreteStepTargetIndex(
                currentIndex = 0,
                totalDragPx = 120f,
                velocityPxPerSec = 500f,
                stepThresholdPx = threshold,
                itemCount = 20,
            ),
        )
        assertEquals(
            19,
            Bookshelf3dSnapPhysics.computeDiscreteStepTargetIndex(
                currentIndex = 19,
                totalDragPx = -120f,
                velocityPxPerSec = -500f,
                stepThresholdPx = threshold,
                itemCount = 20,
            ),
        )
        assertEquals(
            0,
            Bookshelf3dSnapPhysics.computeDiscreteStepTargetIndex(
                currentIndex = 0,
                totalDragPx = -120f,
                velocityPxPerSec = -500f,
                stepThresholdPx = threshold,
                itemCount = 1,
            ),
        )
    }
}


