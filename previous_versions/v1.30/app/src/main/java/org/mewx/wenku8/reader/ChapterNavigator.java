package org.mewx.wenku8.reader;

import org.mewx.wenku8.global.api.ChapterInfo;

import java.util.List;

/**
 * Works out which chapter a reader should move to, given the volume's chapter list and where the
 * reader currently is.
 *
 * <p>This logic used to be copy-pasted four times inside {@code Wenku8ReaderActivityV1} — once
 * each for the on-screen previous/next buttons and once each for running off the end of the
 * pages. All four were the same loop, which meant a fix had to be applied in four places and
 * every one of them was reachable only by opening a chapter on a device. Pulling it out here
 * costs nothing at runtime and makes the boundary behaviour testable on the JVM.
 *
 * <p>Navigation deliberately stays inside a single volume: the readers are handed one {@code vid}
 * and rebuild that volume from the cached index, so there is no neighbouring volume in scope to
 * move into. Running off either end is {@link Outcome#AT_BOUNDARY}, which the caller reports with
 * the "already the first/last chapter" toast.
 */
public final class ChapterNavigator {

    private ChapterNavigator() {
    }

    public enum Outcome {
        /** A neighbouring chapter exists in this volume; {@link Target#chapter} holds it. */
        MOVE,

        /** The current chapter is the first or last of the volume, so there is nowhere to go. */
        AT_BOUNDARY,

        /**
         * The current chapter is not in the list at all, so no neighbour can be identified.
         *
         * <p>Distinct from {@link #AT_BOUNDARY} because it means the reader and the cached index
         * disagree, rather than the reader simply having reached the end. Callers treat it as
         * "do nothing", which is what the original loops did by falling out without matching.
         */
        UNKNOWN_CHAPTER,
    }

    /** The answer to a navigation request. {@link #chapter} is non-null exactly when moving. */
    public static final class Target {
        public final Outcome outcome;
        public final ChapterInfo chapter;

        private Target(Outcome outcome, ChapterInfo chapter) {
            this.outcome = outcome;
            this.chapter = chapter;
        }

        public boolean canMove() {
            return outcome == Outcome.MOVE;
        }
    }

    private static final Target AT_BOUNDARY = new Target(Outcome.AT_BOUNDARY, null);
    private static final Target UNKNOWN_CHAPTER = new Target(Outcome.UNKNOWN_CHAPTER, null);

    /** The chapter after {@code currentCid} within this volume. */
    public static Target next(List<ChapterInfo> chapters, int currentCid) {
        return step(chapters, currentCid, 1);
    }

    /** The chapter before {@code currentCid} within this volume. */
    public static Target previous(List<ChapterInfo> chapters, int currentCid) {
        return step(chapters, currentCid, -1);
    }

    private static Target step(List<ChapterInfo> chapters, int currentCid, int delta) {
        final int current = indexOf(chapters, currentCid);
        if (current < 0) {
            return UNKNOWN_CHAPTER;
        }

        final int target = current + delta;
        if (target < 0 || target >= chapters.size()) {
            return AT_BOUNDARY;
        }

        final ChapterInfo chapter = chapters.get(target);
        // A null entry cannot be navigated to, and reporting it as a boundary keeps the caller
        // from dereferencing it. A truncated index is the way this happens in practice.
        return chapter == null ? AT_BOUNDARY : new Target(Outcome.MOVE, chapter);
    }

    private static int indexOf(List<ChapterInfo> chapters, int cid) {
        if (chapters == null) {
            return -1;
        }
        for (int i = 0; i < chapters.size(); i++) {
            final ChapterInfo chapter = chapters.get(i);
            if (chapter != null && chapter.cid == cid) {
                return i;
            }
        }
        return -1;
    }
}
