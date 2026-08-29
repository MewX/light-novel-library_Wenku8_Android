package org.mewx.wenku8.global.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Covers the bookshelf sync decision that used to sit inside {@code AsyncLoadAllFromCloud}.
 *
 * <p>The case that matters most is {@link #aNovelOnlyOnTheDeviceIsPushedUpRatherThanDropped()}.
 * {@code STABILITY_PLAN.md} promises that syncing cannot lose a novel the device holds and the
 * account does not, and until this file existed that promise rested on reading the code and
 * watching a single sync by hand — on a screen that cannot be hosted in a test without performing
 * a real sync against the user's real account.
 */
public class BookshelfSyncTest {

    private static List<Integer> aids(Integer... values) {
        return new ArrayList<>(Arrays.asList(values));
    }

    private static List<Integer> sorted(List<Integer> values) {
        List<Integer> copy = new ArrayList<>(values);
        Collections.sort(copy);
        return copy;
    }

    // ---- parseCloudAidList -------------------------------------------------------------------

    @Test
    public void theIdsAreReadFromAOneRecordPerLineListing() {
        String listing = "<result>\n"
                + "<item aid=\"1213\"/>\n"
                + "<item aid=\"2455\"/>\n"
                + "<item aid=\"88\"/>\n"
                + "</result>";

        assertEquals(aids(1213, 2455, 88), BookshelfSync.parseCloudAidList(listing));
    }

    /** One unusable record must not cost the reader the rest of the shelf. */
    @Test
    public void aRecordWithANonNumericIdIsSkippedAndTheRestSurvive() {
        String listing = "<item aid=\"1213\"/>\n"
                + "<item aid=\"not-a-number\"/>\n"
                + "<item aid=\"88\"/>";

        assertEquals(aids(1213, 88), BookshelfSync.parseCloudAidList(listing));
    }

    @Test
    public void aListingWithNoRecordsYieldsNoIds() {
        assertTrue(BookshelfSync.parseCloudAidList("<result></result>").isEmpty());
        assertTrue(BookshelfSync.parseCloudAidList("").isEmpty());
    }

    /** The network layer hands back null on failure, and the caller should not have to check. */
    @Test
    public void aNullListingYieldsNoIds() {
        assertTrue(BookshelfSync.parseCloudAidList(null).isEmpty());
    }

    /**
     * Two records sharing a line. The greedy {@code (.*)} this replaced captured across both and
     * lost the pair; the bounded form reads them independently.
     */
    @Test
    public void twoRecordsOnOneLineAreBothRead() {
        assertEquals(aids(11, 22),
                BookshelfSync.parseCloudAidList("<item aid=\"11\"/><item aid=\"22\"/>"));
    }

    /**
     * An attribute after the id, which is the shape that matters in practice: it is exactly what
     * {@code action=bookcase} returns, and under the greedy pattern none of a 66-entry shelf
     * parsed. See the note on {@code AID_ATTRIBUTE}.
     */
    @Test
    public void anAttributeAfterTheIdDoesNotSwallowIt() {
        assertEquals(aids(33),
                BookshelfSync.parseCloudAidList("<item aid=\"33\" name=\"x\"/>"));
    }

    /**
     * The live endpoint's real shape, copied from a genuine {@code action=bookcase&do=list}
     * response. This is the format the bookshelf depends on today.
     */
    @Test
    public void theShapeTheBookshelfEndpointActuallyReturnsIsRead() {
        String listing = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<metadata>\n"
                + "<book aid=\"3988\" />\n"
                + "<book aid=\"1508\" />\n"
                + "<book aid=\"1861\" />\n"
                + "</metadata>";

        assertEquals(aids(3988, 1508, 1861), BookshelfSync.parseCloudAidList(listing));
    }

    /**
     * The sibling endpoint's shape, from a genuine {@code action=bookcase} response. Nothing calls
     * it today, and this test is the reason switching to it would not silently empty the shelf.
     */
    @Test
    public void theFullerListingShapeIsAlsoRead() {
        String listing = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<metadata>\n"
                + "<book aid=\"3988\" date=\"2026-08-23\">\n"
                + "<name><![CDATA[a novel]]></name>\n"
                + "</book>\n"
                + "<book aid=\"1508\" date=\"2026-08-21\">\n"
                + "</book>\n"
                + "</metadata>";

        assertEquals("the date attribute must not be swallowed into the capture",
                aids(3988, 1508), BookshelfSync.parseCloudAidList(listing));
    }

    // ---- plan --------------------------------------------------------------------------------

    /**
     * The guarantee the whole extraction exists for: the device has a novel the account does not,
     * and the sync must schedule it to be uploaded rather than treat the cloud as authoritative.
     */
    @Test
    public void aNovelOnlyOnTheDeviceIsPushedUpRatherThanDropped() {
        BookshelfSync.Plan plan = BookshelfSync.plan(aids(1, 2, 3), aids(1, 2), false);

        assertEquals("the device-only novel should be queued for upload",
                aids(3), plan.localOnly);
        assertTrue("nothing needed downloading", plan.toDownload.isEmpty());
        assertFalse(plan.isUpToDate());
    }

    @Test
    public void aNovelOnlyInTheCloudIsScheduledForDownload() {
        BookshelfSync.Plan plan = BookshelfSync.plan(aids(1), aids(1, 9), false);

        assertEquals(aids(9), plan.toDownload);
        assertTrue("nothing was device-only", plan.localOnly.isEmpty());
    }

    /** A novel both sides already have is neither fetched again nor re-uploaded. */
    @Test
    public void aNovelOnBothSidesIsLeftAlone() {
        BookshelfSync.Plan plan = BookshelfSync.plan(aids(1, 2), aids(1, 2), false);

        assertTrue(plan.toDownload.isEmpty());
        assertTrue(plan.localOnly.isEmpty());
        assertTrue("identical shelves are up to date", plan.isUpToDate());
    }

    @Test
    public void bothSidesEmptyIsUpToDate() {
        assertTrue(BookshelfSync.plan(aids(), aids(), false).isUpToDate());
    }

    /**
     * The consequence of the parse flaw above, asserted end to end: an empty cloud listing must
     * push the entire device shelf up, not wipe it.
     */
    @Test
    public void anEmptyCloudListingPushesTheWholeDeviceShelfUp() {
        BookshelfSync.Plan plan = BookshelfSync.plan(aids(4, 5, 6), aids(), false);

        assertEquals(aids(4, 5, 6), plan.localOnly);
        assertTrue(plan.toDownload.isEmpty());
    }

    @Test
    public void anEmptyDeviceShelfDownloadsEverythingTheAccountHas() {
        BookshelfSync.Plan plan = BookshelfSync.plan(aids(), aids(7, 8), false);

        assertEquals(aids(7, 8), plan.toDownload);
        assertTrue(plan.localOnly.isEmpty());
    }

    /**
     * A user-requested refresh re-downloads both sides rather than only what is missing, and must
     * not list anything twice — the combined list holds every shared id twice before deduplication.
     */
    @Test
    public void aForcedRefreshRedownloadsBothSidesWithoutDuplicates() {
        BookshelfSync.Plan plan = BookshelfSync.plan(aids(1, 2), aids(2, 3), true);

        assertEquals("every id from both sides, exactly once",
                aids(1, 2, 3), sorted(plan.toDownload));
    }

    /** A forced refresh still has to push device-only novels up; it is a sync, not just a fetch. */
    @Test
    public void aForcedRefreshStillPushesDeviceOnlyNovelsUp() {
        BookshelfSync.Plan plan = BookshelfSync.plan(aids(1, 2), aids(1), true);

        assertEquals(aids(2), plan.localOnly);
    }

    /**
     * The caller removes each id from {@code localOnly} as the server accepts it, so what remains
     * is what failed to upload. A list that rejected removal would break that loop.
     */
    @Test
    public void theLocalOnlyListCanBeEmptiedByTheCaller() {
        BookshelfSync.Plan plan = BookshelfSync.plan(aids(1, 2, 3), aids(1), false);

        plan.localOnly.remove(Integer.valueOf(2));

        assertEquals(aids(3), plan.localOnly);
    }

    /** Either side can be absent before the first sync, and neither should throw. */
    @Test
    public void nullShelvesAreTreatedAsEmpty() {
        assertTrue(BookshelfSync.plan(null, null, false).isUpToDate());
        assertEquals(aids(5), BookshelfSync.plan(aids(5), null, false).localOnly);
        assertEquals(aids(6), BookshelfSync.plan(null, aids(6), false).toDownload);
    }

    /**
     * A duplicated id on the device is not mistaken for a device-only novel. The combined list
     * holds it twice and the cloud subtraction removes every copy, which is the property that makes
     * the shared-novel case above work at all.
     */
    @Test
    public void aDuplicatedDeviceIdThatTheCloudHasIsNotTreatedAsDeviceOnly() {
        BookshelfSync.Plan plan = BookshelfSync.plan(aids(1, 1), aids(1), false);

        assertTrue("both copies should have been subtracted", plan.localOnly.isEmpty());
    }

    // ---- staleNovels -------------------------------------------------------------------------

    /** Builds a cloud listing entry without going through the parser. */
    private static BookshelfListParser.Entry entry(int aid, String date, int cid) {
        BookshelfListParser.Entry e = new BookshelfListParser.Entry();
        e.aid = aid;
        e.date = date;
        e.latestChapterCid = cid;
        return e;
    }

    /** A device whose recorded state is whatever this map says, and nothing otherwise. */
    private static BookshelfSync.LocalSnapshot deviceHolding(
            final java.util.Map<Integer, BookshelfSync.Snapshot> held) {
        return aid -> held.get(aid);
    }

    private static java.util.Map<Integer, BookshelfSync.Snapshot> snapshots() {
        return new java.util.HashMap<>();
    }

    @Test
    public void aNovelMatchingTheAccountIsNotStale() {
        java.util.Map<Integer, BookshelfSync.Snapshot> held = snapshots();
        held.put(7, new BookshelfSync.Snapshot("2026-08-27", 178219));

        assertEquals(aids(),
                BookshelfSync.staleNovels(
                        Collections.singletonList(entry(7, "2026-08-27", 178219)),
                        aids(7), deviceHolding(held)));
    }

    @Test
    public void aNewerChapterMakesANovelStale() {
        java.util.Map<Integer, BookshelfSync.Snapshot> held = snapshots();
        held.put(7, new BookshelfSync.Snapshot("2026-08-27", 178219));

        assertEquals(aids(7),
                BookshelfSync.staleNovels(
                        Collections.singletonList(entry(7, "2026-08-27", 178300)),
                        aids(7), deviceHolding(held)));
    }

    /**
     * The cid is the more precise signal -- a novel updated twice in a day moves it while the date
     * stands still -- but a changed date alone still counts, since a cached file written before
     * either field was recorded reads as absent rather than as different.
     */
    @Test
    public void aChangedDateAloneMakesANovelStale() {
        java.util.Map<Integer, BookshelfSync.Snapshot> held = snapshots();
        held.put(7, new BookshelfSync.Snapshot("2026-08-20", 178219));

        assertEquals(aids(7),
                BookshelfSync.staleNovels(
                        Collections.singletonList(entry(7, "2026-08-27", 178219)),
                        aids(7), deviceHolding(held)));
    }

    /**
     * The damage check. A novel on the shelf whose cached metadata cannot be read is exactly the
     * row that renders with an id where its title should be, and it must be repaired rather than
     * left alone.
     */
    @Test
    public void aNovelWithNoReadableSnapshotIsStale() {
        assertEquals(aids(7),
                BookshelfSync.staleNovels(
                        Collections.singletonList(entry(7, "2026-08-27", 178219)),
                        aids(7), deviceHolding(snapshots())));
    }

    /** A novel the device does not hold is missing, not stale; plan() already schedules it. */
    @Test
    public void aNovelTheDeviceDoesNotHoldIsNotReportedAsStale() {
        assertEquals(aids(),
                BookshelfSync.staleNovels(
                        Collections.singletonList(entry(7, "2026-08-27", 178219)),
                        aids(), deviceHolding(snapshots())));
    }

    @Test
    public void staleNovelsKeepTheOrderTheAccountListedThem() {
        java.util.Map<Integer, BookshelfSync.Snapshot> held = snapshots();
        held.put(1, new BookshelfSync.Snapshot("2020-01-01", 10));
        held.put(2, new BookshelfSync.Snapshot("2020-01-01", 20));
        held.put(3, new BookshelfSync.Snapshot("2020-01-01", 30));

        assertEquals(aids(3, 1),
                BookshelfSync.staleNovels(
                        java.util.Arrays.asList(
                                entry(3, "2026-01-01", 31),
                                entry(2, "2020-01-01", 20),
                                entry(1, "2020-01-02", 10)),
                        aids(1, 2, 3), deviceHolding(held)));
    }

    @Test
    public void nullInputsYieldNothingToRefresh() {
        assertTrue(BookshelfSync.staleNovels(null, aids(7), deviceHolding(snapshots())).isEmpty());
        assertTrue(BookshelfSync.staleNovels(
                Collections.singletonList(entry(7, "d", 1)), null,
                deviceHolding(snapshots())).isEmpty());
    }
}
