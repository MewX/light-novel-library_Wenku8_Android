package org.mewx.wenku8.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.mewx.wenku8.global.api.ChapterInfo;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Covers the chapter-to-chapter navigation that used to live inline in the reader, where the only
 * way to exercise it was to open a novel on a device and tap to the end of a volume.
 */
public class ChapterNavigatorTest {

    private static ChapterInfo chapter(int cid, String name) {
        ChapterInfo info = new ChapterInfo();
        info.cid = cid;
        info.chapterName = name;
        return info;
    }

    /** A three-chapter volume, which is the smallest list with a genuine middle. */
    private static List<ChapterInfo> volume() {
        return new ArrayList<>(Arrays.asList(
                chapter(101, "第一话 出发"),
                chapter(102, "第二话 相遇"),
                chapter(103, "第三话 别离")));
    }

    @Test
    public void nextFromTheMiddleMovesForwardOne() {
        ChapterNavigator.Target target = ChapterNavigator.next(volume(), 102);

        assertTrue(target.canMove());
        assertEquals(ChapterNavigator.Outcome.MOVE, target.outcome);
        assertEquals(103, target.chapter.cid);
        assertEquals("第三话 别离", target.chapter.chapterName);
    }

    @Test
    public void previousFromTheMiddleMovesBackOne() {
        ChapterNavigator.Target target = ChapterNavigator.previous(volume(), 102);

        assertTrue(target.canMove());
        assertEquals(101, target.chapter.cid);
        assertEquals("第一话 出发", target.chapter.chapterName);
    }

    /** The 已是最后一章 case: last chapter of the volume, nothing after it. */
    @Test
    public void nextFromTheLastChapterReportsTheBoundary() {
        ChapterNavigator.Target target = ChapterNavigator.next(volume(), 103);

        assertFalse(target.canMove());
        assertEquals(ChapterNavigator.Outcome.AT_BOUNDARY, target.outcome);
        assertNull(target.chapter);
    }

    /** The 已是第一章 case: first chapter of the volume, nothing before it. */
    @Test
    public void previousFromTheFirstChapterReportsTheBoundary() {
        ChapterNavigator.Target target = ChapterNavigator.previous(volume(), 101);

        assertFalse(target.canMove());
        assertEquals(ChapterNavigator.Outcome.AT_BOUNDARY, target.outcome);
        assertNull(target.chapter);
    }

    /** A one-chapter volume is both ends at once, so neither direction may move. */
    @Test
    public void aSingleChapterVolumeIsAtBothBoundaries() {
        List<ChapterInfo> single = Collections.singletonList(chapter(500, "短篇"));

        assertEquals(ChapterNavigator.Outcome.AT_BOUNDARY,
                ChapterNavigator.next(single, 500).outcome);
        assertEquals(ChapterNavigator.Outcome.AT_BOUNDARY,
                ChapterNavigator.previous(single, 500).outcome);
    }

    /**
     * The reader and the cached index disagreeing is reported separately from reaching an end,
     * because the two want different handling: a boundary is normal, this is not.
     */
    @Test
    public void anUnlistedChapterIsNotMistakenForABoundary() {
        assertEquals(ChapterNavigator.Outcome.UNKNOWN_CHAPTER,
                ChapterNavigator.next(volume(), 999).outcome);
        assertEquals(ChapterNavigator.Outcome.UNKNOWN_CHAPTER,
                ChapterNavigator.previous(volume(), 999).outcome);
    }

    @Test
    public void anEmptyVolumeCannotLocateTheCurrentChapter() {
        List<ChapterInfo> empty = new ArrayList<>();

        assertEquals(ChapterNavigator.Outcome.UNKNOWN_CHAPTER,
                ChapterNavigator.next(empty, 101).outcome);
        assertEquals(ChapterNavigator.Outcome.UNKNOWN_CHAPTER,
                ChapterNavigator.previous(empty, 101).outcome);
    }

    /**
     * A null chapter list reaches here whenever the cached index was missing or unparseable. The
     * inline loops dereferenced it and crashed; answering UNKNOWN_CHAPTER makes the buttons inert
     * instead.
     */
    @Test
    public void aNullVolumeIsAnsweredRatherThanThrown() {
        assertEquals(ChapterNavigator.Outcome.UNKNOWN_CHAPTER,
                ChapterNavigator.next(null, 101).outcome);
        assertEquals(ChapterNavigator.Outcome.UNKNOWN_CHAPTER,
                ChapterNavigator.previous(null, 101).outcome);
    }

    /** A truncated index can leave a hole in the list; it must not be navigated into. */
    @Test
    public void aNullEntryIsNeverReturnedAsATarget() {
        List<ChapterInfo> holed = new ArrayList<>(Arrays.asList(
                chapter(101, "第一话"),
                null,
                chapter(103, "第三话")));

        assertEquals(ChapterNavigator.Outcome.AT_BOUNDARY,
                ChapterNavigator.next(holed, 101).outcome);
        assertEquals(ChapterNavigator.Outcome.AT_BOUNDARY,
                ChapterNavigator.previous(holed, 103).outcome);
    }

    /** Matching is by cid, so the list order rather than the cid ordering decides the neighbour. */
    @Test
    public void neighboursFollowListOrderNotCidOrder() {
        List<ChapterInfo> outOfOrder = new ArrayList<>(Arrays.asList(
                chapter(900, "序章"),
                chapter(100, "第一话"),
                chapter(500, "第二话")));

        assertEquals(100, ChapterNavigator.next(outOfOrder, 900).chapter.cid);
        assertEquals(500, ChapterNavigator.next(outOfOrder, 100).chapter.cid);
        assertEquals(900, ChapterNavigator.previous(outOfOrder, 100).chapter.cid);
    }

    /**
     * Duplicate cids resolve to the first match, matching the {@code break} in the original loops.
     * Pinned because a volume index with a repeated chapter would otherwise make navigation
     * depend on which copy the search happened to reach.
     */
    @Test
    public void aDuplicateCidResolvesToTheFirstOccurrence() {
        List<ChapterInfo> duplicated = new ArrayList<>(Arrays.asList(
                chapter(101, "第一话"),
                chapter(202, "第二话"),
                chapter(101, "第一话 重复"),
                chapter(303, "第三话")));

        assertEquals(202, ChapterNavigator.next(duplicated, 101).chapter.cid);
        assertEquals(ChapterNavigator.Outcome.AT_BOUNDARY,
                ChapterNavigator.previous(duplicated, 101).outcome);
    }

    /** Walking the whole volume end to end, then back, lands on every chapter in order. */
    @Test
    public void walkingForwardAndBackTraversesTheWholeVolume() {
        List<ChapterInfo> chapters = volume();

        List<Integer> forward = new ArrayList<>();
        int cid = 101;
        forward.add(cid);
        for (ChapterNavigator.Target t = ChapterNavigator.next(chapters, cid);
                t.canMove();
                t = ChapterNavigator.next(chapters, cid)) {
            cid = t.chapter.cid;
            forward.add(cid);
        }
        assertEquals(Arrays.asList(101, 102, 103), forward);

        List<Integer> backward = new ArrayList<>();
        backward.add(cid);
        for (ChapterNavigator.Target t = ChapterNavigator.previous(chapters, cid);
                t.canMove();
                t = ChapterNavigator.previous(chapters, cid)) {
            cid = t.chapter.cid;
            backward.add(cid);
        }
        assertEquals(Arrays.asList(103, 102, 101), backward);
    }
}
