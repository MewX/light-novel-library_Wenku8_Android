package org.mewx.wenku8.global.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
     * <p><b>This was {@code aid="(.*)"} until it was measured against the live server.</b> The
     * greedy form captures to the last quote on the line, so it reads an id correctly only when
     * {@code aid} is the last quoted attribute there. That happens to hold for the endpoint the
     * bookshelf actually calls — {@code action=bookcase&do=list} returns {@code <book aid="3988" />}
     * one per line, and a real 66-entry shelf parsed 66 of 66 under either form, so the greedy
     * version was never broken in production.
     *
     * <p>It fails completely on the sibling endpoint. {@code action=bookcase} returns
     * {@code <book aid="3988" date="2026-08-23">}, where the capture swallows the date and
     * <i>none</i> of the 66 entries parse. Nothing calls that endpoint today —
     * {@code Wenku8API.getBookshelfListParams} has no caller — but reaching for it looks like a
     * plain optimisation, since it carries names and dates and would save one request per novel.
     * The failure would be silent: an empty listing, read by {@link #plan} as "the cloud has
     * nothing", followed by a pointless re-upload of the entire shelf.
     *
     * <p>So the bound is deliberate rather than incidental. It is verified to produce identical
     * results on the endpoint in use, and it keeps producing correct ones on an endpoint someone
     * may switch to. {@code BookshelfSyncTest} covers both shapes.
     */
    private static final Pattern AID_ATTRIBUTE = Pattern.compile("aid=\"([^\"]*)\"");

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

    /** What the device has recorded for a novel, read back out of its cached metadata. */
    public static final class Snapshot {
        /** The metadata's {@code LastUpdate}; the listing calls the same value {@code date}. */
        public final String lastUpdate;
        /** The metadata's {@code LatestSection} cid. */
        public final int latestChapterCid;

        public Snapshot(String lastUpdate, int latestChapterCid) {
            this.lastUpdate = lastUpdate == null ? "" : lastUpdate;
            this.latestChapterCid = latestChapterCid;
        }
    }

    /** Reads back what the device recorded for a novel, or null when it has nothing usable. */
    public interface LocalSnapshot {
        @Nullable
        Snapshot of(int aid);
    }

    /**
     * Which novels already on the shelf no longer match what the account says about them.
     *
     * <p>This is what makes a refresh cheap. The bookshelf shows metadata captured whenever a
     * novel was downloaded, and nothing ever refreshed it, so the only way to correct a stale
     * "last updated" or latest chapter was to re-download every novel in full. The listing now
     * carries both fields for the whole shelf in one request, and the device already stores both
     * in each novel's cached metadata -- {@code LastUpdate} and {@code LatestSection} cid -- so
     * the comparison needs no new state on either side.
     *
     * <p>A novel with no readable snapshot counts as stale. That is deliberate and does double
     * duty: it catches a cached file that is missing, truncated or not parseable, which is
     * exactly the damage that shows up as a bookshelf row with an id where its title should be.
     *
     * <p>Only novels the device already holds are considered. Ones the account has and the device
     * does not are not stale, they are missing, and {@link #plan} already schedules those.
     *
     * @return the ids to refresh, in the order the account listed them
     */
    public static List<Integer> staleNovels(@Nullable List<BookshelfListParser.Entry> cloud,
                                            @Nullable List<Integer> local,
                                            @NonNull LocalSnapshot snapshot) {
        final List<Integer> stale = new ArrayList<>();
        if (cloud == null || local == null) {
            return stale;
        }

        final Set<Integer> onDevice = new HashSet<>(local);
        for (BookshelfListParser.Entry entry : cloud) {
            if (!onDevice.contains(entry.aid)) {
                continue; // missing rather than stale; plan() covers it
            }

            final Snapshot held = snapshot.of(entry.aid);
            if (held == null) {
                stale.add(entry.aid);
                continue;
            }

            // The cid is the more precise of the two -- a novel updated twice in one day moves it
            // while the date stands still -- but both are compared, because a cached file written
            // before either field was recorded reads as absent rather than as different.
            if (held.latestChapterCid != entry.latestChapterCid
                    || !held.lastUpdate.equals(entry.date)) {
                stale.add(entry.aid);
            }
        }
        return stale;
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
