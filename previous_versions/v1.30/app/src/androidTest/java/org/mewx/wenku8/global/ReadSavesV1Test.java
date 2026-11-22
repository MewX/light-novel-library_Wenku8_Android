package org.mewx.wenku8.global;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * Reading positions — one record per novel, stored as
 * {@code aid:vid:cid:lineId:wordId||aid:vid:cid:lineId:wordId}.
 *
 * <p>This is the most valuable thing in the save folder that had no test. The bookshelf can be
 * rebuilt by re-adding novels and the cached chapters can be re-downloaded, but nobody can
 * reconstruct where they were on page 300 of volume 4. The reader writes this file every time it
 * leaves a chapter, which means it is also the file most often caught mid-write — and per Phase 1
 * item 11, {@code LightCache.saveFile} truncates the destination before it writes, so a process
 * killed at the wrong moment leaves exactly the damaged content the malformed cases below feed in.
 * That combination is why these cases are characterization rather than hypotheticals.
 *
 * <p>The load is deliberately per-record: one unparseable entry costs that novel's position and
 * leaves every other novel's intact. The tests pin that, because the tempting "reject the file"
 * alternative would throw away everyone's progress to protect one corrupt row — the same argument
 * Phase 1 item 9 settled for the bookshelf.
 *
 * <p>Device-only, and it rewrites the real file. See {@link SaveFileFixture}.
 */
@SmallTest
public class ReadSavesV1Test {

    private static final String READ_SAVES_V1_FILE = "read_saves_v1.wk8";

    private final SaveFileFixture file = new SaveFileFixture(READ_SAVES_V1_FILE);

    @Before
    public void captureTheRealProgress() throws IOException {
        file.capture();
    }

    @After
    public void restoreTheRealProgress() throws IOException {
        file.restore();
        // Resync the static, so the next test in this process does not inherit reading positions
        // that no longer exist on disk.
        GlobalConfig.loadReadSavesV1();
    }

    private void given(String content) throws IOException {
        file.arrange(content);
        GlobalConfig.loadReadSavesV1();
    }

    private static void assertRecord(String message, GlobalConfig.ReadSavesV1 record,
                                     int aid, int vid, int cid, int lineId, int wordId) {
        assertNotNull(message, record);
        assertEquals(message + ": aid", aid, record.aid);
        assertEquals(message + ": vid", vid, record.vid);
        assertEquals(message + ": cid", cid, record.cid);
        assertEquals(message + ": lineId", lineId, record.lineId);
        assertEquals(message + ": wordId", wordId, record.wordId);
    }

    @Test
    public void testAStoredPositionLoadsBackWithEveryField() throws IOException {
        given("1306:41748:50471:12:34");

        assertRecord("the stored position", GlobalConfig.getReadSavesRecordV1(1306),
                1306, 41748, 50471, 12, 34);
    }

    @Test
    public void testSeveralNovelsKeepTheirOwnPositions() throws IOException {
        given("1306:41748:50471:12:34||9:10:11:0:0");

        assertRecord("the first novel", GlobalConfig.getReadSavesRecordV1(1306),
                1306, 41748, 50471, 12, 34);
        assertRecord("the second novel", GlobalConfig.getReadSavesRecordV1(9),
                9, 10, 11, 0, 0);
    }

    @Test
    public void testAnUnknownNovelHasNoPosition() throws IOException {
        given("1306:41748:50471:12:34");

        // The reader opens at the top when this is null, so it must be null rather than a
        // zero-filled record -- those are different positions.
        assertNull(GlobalConfig.getReadSavesRecordV1(999));
    }

    @Test
    public void testAMissingFileLeavesEveryNovelUnread() {
        file.deleteAll();
        GlobalConfig.loadReadSavesV1();

        // A fresh install, and also a device whose storage could not be read. Neither may throw:
        // this is reached from the reader's startup path.
        assertNull(GlobalConfig.getReadSavesRecordV1(1306));
    }

    @Test
    public void testAnEmptyFileLeavesEveryNovelUnread() throws IOException {
        given("");

        assertNull(GlobalConfig.getReadSavesRecordV1(1306));
    }

    @Test
    public void testARecordWithTooFewFieldsIsSkipped() throws IOException {
        // What a write cut short partway through a record leaves behind.
        given("1306:41748:50471||9:10:11:0:0");

        assertNull("a truncated record is not a position", GlobalConfig.getReadSavesRecordV1(1306));
        assertRecord("the intact record either side of it", GlobalConfig.getReadSavesRecordV1(9),
                9, 10, 11, 0, 0);
    }

    @Test
    public void testARecordWithTooManyFieldsIsSkipped() throws IOException {
        given("1306:41748:50471:12:34:99||9:10:11:0:0");

        assertNull(GlobalConfig.getReadSavesRecordV1(1306));
        assertNotNull(GlobalConfig.getReadSavesRecordV1(9));
    }

    /**
     * The non-numeric case, which is the one worth having: the skip is a labelled {@code continue}
     * out of an inner loop, and a labelled jump landing on the wrong loop would silently keep a
     * record built from garbage instead of dropping it.
     */
    @Test
    public void testARecordWithANonNumericFieldIsSkippedWholesale() throws IOException {
        given("1306:41748:xx:12:34||9:10:11:0:0");

        assertNull("a record with an unparseable field must be dropped, not partly built",
                GlobalConfig.getReadSavesRecordV1(1306));
        assertRecord("the record after it still loads", GlobalConfig.getReadSavesRecordV1(9),
                9, 10, 11, 0, 0);
    }

    @Test
    public void testAnEntirelyCorruptFileLoadsNothingWithoutThrowing() throws IOException {
        given("this is not a progress file at all");

        assertNull(GlobalConfig.getReadSavesRecordV1(1306));
    }

    @Test
    public void testANewPositionSurvivesAReload() throws IOException {
        given("");

        GlobalConfig.addReadSavesRecordV1(1306, 41748, 50471, 12, 34);
        // Reload from disk rather than trusting the in-memory list, so this covers the write and
        // proves the writer's format is one the reader accepts.
        GlobalConfig.loadReadSavesV1();

        assertRecord("the position just saved", GlobalConfig.getReadSavesRecordV1(1306),
                1306, 41748, 50471, 12, 34);
    }

    @Test
    public void testReadingOnUpdatesThePositionInPlace() throws IOException {
        given("1306:41748:50471:12:34");

        GlobalConfig.addReadSavesRecordV1(1306, 41748, 50472, 0, 0);
        GlobalConfig.loadReadSavesV1();

        // One novel, one position: moving to the next chapter must overwrite, not accumulate, or
        // the file grows without bound and the first match wins forever.
        assertRecord("the updated position", GlobalConfig.getReadSavesRecordV1(1306),
                1306, 41748, 50472, 0, 0);
        assertEquals("the record must be updated rather than appended",
                "1306:41748:50472:0:0", file.readBack());
    }

    @Test
    public void testASecondNovelIsAppendedRatherThanReplacing() throws IOException {
        given("1306:41748:50471:12:34");

        GlobalConfig.addReadSavesRecordV1(9, 10, 11, 0, 0);
        GlobalConfig.loadReadSavesV1();

        assertNotNull(GlobalConfig.getReadSavesRecordV1(1306));
        assertNotNull(GlobalConfig.getReadSavesRecordV1(9));
    }

    @Test
    public void testARemovedPositionStaysRemoved() throws IOException {
        given("1306:41748:50471:12:34||9:10:11:0:0");

        GlobalConfig.removeReadSavesRecordV1(1306);
        GlobalConfig.loadReadSavesV1();

        assertNull(GlobalConfig.getReadSavesRecordV1(1306));
        assertNotNull("removing one novel must not disturb another",
                GlobalConfig.getReadSavesRecordV1(9));
    }

    @Test
    public void testRemovingANovelWithNoPositionRemovesNothing() throws IOException {
        // The search loop leaves its index at size() when there is no match, so the guard after it
        // is the only thing standing between this and deleting whatever sits at that index.
        given("1306:41748:50471:12:34||9:10:11:0:0");

        GlobalConfig.removeReadSavesRecordV1(999);
        GlobalConfig.loadReadSavesV1();

        assertNotNull(GlobalConfig.getReadSavesRecordV1(1306));
        assertNotNull(GlobalConfig.getReadSavesRecordV1(9));
    }

    @Test
    public void testACorruptRecordDoesNotComeBackAfterARewrite() throws IOException {
        given("1306:41748:xx:12:34||9:10:11:0:0");

        GlobalConfig.addReadSavesRecordV1(7, 8, 9, 1, 2);
        GlobalConfig.loadReadSavesV1();

        assertNull("the unparseable record must not be carried into the rewrite",
                GlobalConfig.getReadSavesRecordV1(1306));
        assertNotNull(GlobalConfig.getReadSavesRecordV1(9));
        assertNotNull(GlobalConfig.getReadSavesRecordV1(7));
    }
}
