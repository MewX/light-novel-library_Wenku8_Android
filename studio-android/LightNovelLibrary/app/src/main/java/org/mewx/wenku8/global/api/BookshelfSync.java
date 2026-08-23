package org.mewx.wenku8.global.api;

import android.util.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Works out what a bookshelf sync has to do: which novels to pull down, and which to push up.
 *
 * <p>This decision used to live inline in {@code FavFragment.AsyncLoadAllFromCloud.doInBackground},
 * between the network call that fetches the cloud list and the loop that downloads each novel. It
 * was reachable only by opening the bookshelf on a device while logged in — and, as
 * {@code FavFragmentHostingTest} found out, hosting that Fragment performs a real sync against the
 * user's real account. So the one piece of logic that decides whether a reader's novels survive a
 * sync was the piece that could not be exercised without risking the thing it protects.
 *
 * <p>The guarantee worth having a test for is that <b>a novel on the device but not in the cloud is
 * never dropped</b>. It is pushed up instead. {@code STABILITY_PLAN.md} claims this, and until now
 * the claim rested on reading the code and watching one sync by hand.
 *
 * <p>Nothing here changes what the sync does. One incidental difference is worth naming: the
 * original consulted {@code GlobalConfig.getLocalBookshelfList()} twice, once to build the combined
 * list and again to subtract it, so a bookshelf that changed between those two calls could produce
 * a plan that matched neither state. The caller now passes a single snapshot, which cannot.
 */
public final class BookshelfSync {

    private BookshelfSync() {
    }

    /**
     * Matches the {@code aid} attributes in a bookshelf listing.
     *
     * <p><b>The greedy {@code (.*)} is the original expression and is kept deliberately, but it is
     * narrower than it looks.</b> Because {@code .} does not match a line terminator, it captures
     * to the last quote <i>on the same line</i> — so it reads an id correctly only when {@code aid}
     * is the last quoted attribute on its line. Two records sharing a line, or a single record with
     * any attribute after {@code aid}, both yield a capture that will not parse, and the ids are
     * skipped. {@code BookshelfSyncTest} pins all three cases.
     *
     * <p>What saves this from being a data-loss bug is the shape of the failure: skipped ids make
     * the cloud list come back short or empty, and {@link #plan} reads a missing id as "the cloud
     * does not have it" and pushes the local copy up. The cost is wasted uploads, not a lost novel.
     * Tightening this to {@code ([^"]*)} would be a one-character fix, and is left undone on
     * purpose under the standing preference for coverage over logical patches — it is recorded in
     * {@code STABILITY_PLAN.md} for whoever picks it up.
     */
    private static final Pattern AID_ATTRIBUTE = Pattern.compile("aid=\"(.*)\"");

    /**
     * The ids in a cloud bookshelf listing, in the order they appear.
     *
     * <p>An entry whose id is not a number is skipped rather than failing the parse, which is what
     * the original did: a single broken record must not cost the reader the rest of the shelf.
     */
    public static List<Integer> parseCloudAidList(String listing) {
        final List<Integer> aids = new ArrayList<>();
        if (listing == null) {
            return aids;
        }

        final Matcher matcher = AID_ATTRIBUTE.matcher(listing);
        while (matcher.find()) {
            try {
                aids.add(Integer.valueOf(matcher.group(1)));
            } catch (NumberFormatException e) {
                // Logged rather than reported to Crashlytics: a malformed record is the server's
                // problem, and the greedy pattern documented above makes this reachable for a
                // well-formed response too, so it is not an app fault worth an exception record.
                Log.e(BookshelfSync.class.getSimpleName(), "Found and skipped broken aid.");
            }
        }
        return aids;
    }

    /** What a sync should do. Both lists are mutable — the caller empties {@link #localOnly}. */
    public static final class Plan {

        /** Novels to fetch and add to the device. */
        public final List<Integer> toDownload;

        /**
         * Novels on the device that the cloud does not have, to be pushed up.
         *
         * <p>Mutable on purpose: the caller removes each id as the server accepts it, so whatever
         * is left is what failed to upload.
         */
        public final List<Integer> localOnly;

        private Plan(List<Integer> toDownload, List<Integer> localOnly) {
            this.toDownload = toDownload;
            this.localOnly = localOnly;
        }

        /** Nothing to pull and nothing to push, so the two sides already agree. */
        public boolean isUpToDate() {
            return toDownload.isEmpty() && localOnly.isEmpty();
        }
    }

    /**
     * @param local     the device's bookshelf
     * @param cloud     the account's bookshelf, as returned by the server
     * @param forceLoad a user-requested refresh, which re-downloads everything on both sides rather
     *                  than only what the device is missing
     */
    public static Plan plan(List<Integer> local, List<Integer> cloud, boolean forceLoad) {
        final List<Integer> combined = new ArrayList<>();
        if (local != null) {
            combined.addAll(local);
        }
        if (cloud != null) {
            combined.addAll(cloud);
        }

        // Subtracting the cloud list removes every copy of each id, so what survives is exactly the
        // ids the cloud never mentioned. This is the step the no-novel-is-dropped guarantee rests
        // on, and it is why the two lists are combined rather than one replacing the other.
        final List<Integer> localOnly = new ArrayList<>(combined);
        if (cloud != null) {
            localOnly.removeAll(cloud);
        }

        final List<Integer> toDownload = new ArrayList<>(combined);
        if (!forceLoad) {
            if (local != null) {
                toDownload.removeAll(local);
            }
        } else {
            // A forced refresh wants both sides, so the only thing to remove is the duplication
            // caused by combining them. Order is unspecified here, as it was originally: this
            // decides download order and nothing else.
            final Set<Integer> unique = new HashSet<>(toDownload);
            toDownload.clear();
            toDownload.addAll(unique);
        }

        return new Plan(toDownload, localOnly);
    }
}
