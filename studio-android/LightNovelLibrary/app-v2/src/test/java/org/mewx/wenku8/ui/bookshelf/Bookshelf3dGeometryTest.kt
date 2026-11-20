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
    fun visibleIndexRange_cullsLargeLibraryToConstantWindow() {
        val metrics = Bookshelf3dGeometry.computeMetrics(availableWidthDp = 412f, availableHeightDp = 892f)
        val range = Bookshelf3dGeometry.computeVisibleIndexRange(
            itemCount = 200,
            focus = 100f,
            metrics = metrics,
        )
        assertTrue("Visible range should stay bounded (was ${range.count()})", range.count() <= 35)
        assertTrue(100 in range)

        assertTrue(Bookshelf3dGeometry.computeVisibleIndexRange(0, 0f, metrics).isEmpty())
        assertEquals(0..0, Bookshelf3dGeometry.computeVisibleIndexRange(1, 0f, metrics))
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
}
