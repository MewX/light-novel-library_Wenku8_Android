package org.mewx.wenku8.ui.reader

import org.mewx.wenku8.data.local.db.dao.OrderedChapterWithVolume
import org.mewx.wenku8.reader.engine.ChapterAst
import org.mewx.wenku8.reader.engine.ChapterBlock
import org.mewx.wenku8.reader.engine.ChapterWindowSlot
import org.mewx.wenku8.reader.engine.PaginatedChapterLayout
import org.mewx.wenku8.reader.engine.ReaderLayoutConfig
import org.mewx.wenku8.reader.engine.ReaderPage
import org.mewx.wenku8.reader.engine.ReaderPageElement
import org.mewx.wenku8.reader.engine.ReadingAnchor
import org.mewx.wenku8.reader.engine.ReaderWindowSnapshot
import org.mewx.wenku8.ui.reader.InFlightStepTarget
import org.mewx.wenku8.ui.reader.resolveReflowLockedAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pins the reflow anchor selection semantics for split-screen resizes: the anchor must be the
 * first character of the page the user is currently LOOKING at (the majority-visible page during
 * an in-flight swipe), never the page the finger happens to be moving toward — otherwise a resize
 * landing mid-swipe silently advances the reading position by one page.
 */
class ReflowLockedAnchorTest {

    private fun page(
        cid: Int,
        index: Int,
        paragraphIndex: Int,
        charStart: Int,
        charEnd: Int,
    ): ReaderPage {
        val anchorStart = ReadingAnchor(paragraphIndex = paragraphIndex, charOffset = charStart)
        val anchorEnd = ReadingAnchor(paragraphIndex = paragraphIndex, charOffset = charEnd)
        val element = ReaderPageElement.TextLine(
            blockIndex = paragraphIndex,
            rawStartChar = charStart,
            rawEndChar = charEnd,
            text = "line",
            xPx = 0f,
            topYPx = 0f,
            baselineYPx = 0f,
            heightPx = 10f,
            fontSizePx = 10f,
            extraLetterSpacingPx = 0f,
            isParagraphFirstLine = false,
            isParagraphLastLine = false,
        )
        return ReaderPage(
            cid = cid,
            pageIndexInChapter = index,
            totalPagesInChapter = 3,
            startAnchor = anchorStart,
            endAnchor = anchorEnd,
            characterCountOnPage = charEnd - charStart + 1,
            isFullBleedArtbookPage = false,
            elements = listOf(element),
        )
    }

    private fun threePageSnapshot(revision: Long = 1L): ReaderWindowSnapshot {
        val chapter = OrderedChapterWithVolume(
            aid = 1,
            vid = 1,
            volumeOrder = 0,
            volumeTitle = "第一卷",
            cid = 100,
            chapterOrder = 0,
            chapterTitle = "第一话",
            isIllustrationChapter = false,
        )
        val ast = ChapterAst(
            aid = 1,
            vid = 1,
            cid = 100,
            volumeTitle = "第一卷",
            chapterTitle = "第一话",
            isDedicatedIllustrationChapter = false,
            blocks = listOf(
                ChapterBlock.ParagraphBlock(
                    blockIndex = 0,
                    text = "正文",
                    formattedText = "　　正文",
                    indentLength = 2,
                    isDialogue = false,
                ),
            ),
            illustrationBlocks = emptyList(),
            totalCharacters = 2,
        )
        val config = ReaderLayoutConfig(
            viewportWidthPx = 380,
            viewportHeightPx = 640,
            fontSizePx = 22f,
            lineDistancePx = 10f,
            paragraphDistancePx = 16f,
            pageEdgeDistancePx = 20f,
        )
        // Pages: #0 starts at char 0, #1 at char 50, #2 at char 100 (page-1 keeps its own
        // character window; anchors are (paragraphIndex = 0, charOffset)).
        val pages = listOf(
            page(cid = 100, index = 0, paragraphIndex = 0, charStart = 0, charEnd = 49),
            page(cid = 100, index = 1, paragraphIndex = 0, charStart = 50, charEnd = 99),
            page(cid = 100, index = 2, paragraphIndex = 0, charStart = 100, charEnd = 148),
        )
        val layout = PaginatedChapterLayout(ast = ast, config = config, pages = pages)
        return ReaderWindowSnapshot(
            aid = 1,
            currentSlot = ChapterWindowSlot.Loaded(chapter = chapter, layout = layout),
            layoutConfig = config,
            activeVirtualPageIndex = 0,
            coordinateRevision = revision,
        )
    }

    @Test
    fun `idle pager anchors to the visible page itself`() {
        val snapshot = threePageSnapshot()
        val anchor = resolveReflowLockedAnchor(
            snapshot = snapshot,
            pagerCurrentPage = 1,
            pagerOffsetFraction = 0f,
            trackerInSync = true,
            inFlightStep = null,
            interruptedStep = null,
        )
        assertEquals(ReadingAnchor(paragraphIndex = 0, charOffset = 50), anchor)
    }

    @Test
    fun `mid-swipe under half viewport anchors to the page being read`() {
        // The user is flipping from page 1 toward page 2 but has crossed less than half: page 1
        // still occupies the majority of the viewport, so the reflow must keep page 1.
        val snapshot = threePageSnapshot()
        val anchor = resolveReflowLockedAnchor(
            snapshot = snapshot,
            pagerCurrentPage = 1,
            pagerOffsetFraction = 0.3f,
            trackerInSync = true,
            inFlightStep = null,
            interruptedStep = null,
        )
        assertEquals(ReadingAnchor(paragraphIndex = 0, charOffset = 50), anchor)
    }

    @Test
    fun `mid-swipe past half viewport anchors to the majority page`() {
        // The user has dragged past half toward page 2: page 2 now occupies the majority, so
        // anchoring to its first character matches what they are looking at.
        val snapshot = threePageSnapshot()
        val anchor = resolveReflowLockedAnchor(
            snapshot = snapshot,
            pagerCurrentPage = 1,
            pagerOffsetFraction = 0.7f,
            trackerInSync = true,
            inFlightStep = null,
            interruptedStep = null,
        )
        assertEquals(ReadingAnchor(paragraphIndex = 0, charOffset = 100), anchor)
    }

    @Test
    fun `explicit page turn commits to its target even against a partial swipe`() {
        val snapshot = threePageSnapshot()
        val anchor = resolveReflowLockedAnchor(
            snapshot = snapshot,
            pagerCurrentPage = 1,
            pagerOffsetFraction = -0.2f,
            trackerInSync = true,
            inFlightStep = InFlightStepTarget(targetVirtualPage = 2, coordinateRevision = 1L),
            interruptedStep = null,
        )
        assertEquals(ReadingAnchor(paragraphIndex = 0, charOffset = 100), anchor)
    }

    @Test
    fun `interrupted page turn with stale revision is ignored`() {
        val snapshot = threePageSnapshot(revision = 2L)
        val anchor = resolveReflowLockedAnchor(
            snapshot = snapshot,
            pagerCurrentPage = 1,
            pagerOffsetFraction = 0f,
            trackerInSync = false,
            inFlightStep = null,
            interruptedStep = InFlightStepTarget(targetVirtualPage = 2, coordinateRevision = 1L),
        )
        assertNull(anchor)
    }

    @Test
    fun `out-of-sync tracker defers to the snapshot anchor fallback`() {
        val snapshot = threePageSnapshot()
        val anchor = resolveReflowLockedAnchor(
            snapshot = snapshot,
            pagerCurrentPage = 1,
            pagerOffsetFraction = 0f,
            trackerInSync = false,
            inFlightStep = null,
            interruptedStep = null,
        )
        assertNull(anchor)
    }

    @Test
    fun `in-flight step for an older revision does not override the pager`() {
        val snapshot = threePageSnapshot(revision = 3L)
        val anchor = resolveReflowLockedAnchor(
            snapshot = snapshot,
            pagerCurrentPage = 1,
            pagerOffsetFraction = 0f,
            trackerInSync = true,
            inFlightStep = InFlightStepTarget(targetVirtualPage = 0, coordinateRevision = 2L),
            interruptedStep = null,
        )
        assertEquals(ReadingAnchor(paragraphIndex = 0, charOffset = 50), anchor)
    }
}
