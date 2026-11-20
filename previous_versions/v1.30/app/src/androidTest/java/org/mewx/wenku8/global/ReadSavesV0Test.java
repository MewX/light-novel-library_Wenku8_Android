package org.mewx.wenku8.global;

import static org.junit.Assert.assertEquals;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

/**
 * The superseded reading-position format, {@code cid,,pos,,height||cid,,pos,,height}.
 *
 * <p>Deprecated but very much alive: {@code VerticalReaderActivity} is the one reader that still
 * uses it, reading the position at {@code :66} and {@code :363} and writing it from {@code onPause}
 * at {@code :381}. Anyone who has ever opened that reader has this file, and it stays on disk
 * forever — the V1 format did not replace it, it was added alongside.
 *
 * <p>These tests exist because reading the loader next to its V1 successor showed the two doing
 * the same job with different care: V1 checks every field with {@code LightTool.isInteger} before
 * parsing, V0 checks the field <i>count</i> and then parses regardless. See
 * {@link #testACorruptRecordDoesNotCrashTheReader}, which is the whole reason this class was
 * written.
 *
 * <p>Device-only, and it rewrites the real file. See {@link SaveFileFixture}.
 */
@SmallTest
public class ReadSavesV0Test {

    private static final String READ_SAVES_FILE = "read_saves.wk8";

    /** The guard in {@code addReadSavesRecord}: anything under this is not worth storing. */
    private static final int BELOW_THE_SAVE_THRESHOLD = 99;

    private final SaveFileFixture file = new SaveFileFixture(READ_SAVES_FILE);

    @Before
    public void captureTheRealProgress() throws IOException {
        file.capture();
    }

    @After
    public void restoreTheRealProgress() throws IOException {
        file.restore();
        // Resync the static, so the next test in this process does not inherit these positions.
        GlobalConfig.loadReadSaves();
    }

    private void given(String content) throws IOException {
        file.arrange(content);
        GlobalConfig.loadReadSaves();
    }

    /**
     * The height argument is accepted and then ignored — the scaling that would have used it is
     * commented out in {@code getReadSavesRecord}, which returns the raw stored offset. Passing a
     * fixed value keeps that visible rather than implying it matters.
     */
    private static int positionFor(int cid) {
        return GlobalConfig.getReadSavesRecord(cid, 1000);
    }

    @Test
    public void testAStoredPositionLoadsBack() throws IOException {
        given("50471,,16117,,18481");

        assertEquals(16117, positionFor(50471));
    }

    @Test
    public void testSeveralChaptersKeepTheirOwnPositions() throws IOException {
        given("50471,,16117,,18481||50472,,200,,4000");

        assertEquals(16117, positionFor(50471));
        assertEquals(200, positionFor(50472));
    }

    @Test
    public void testAnUnreadChapterStartsAtTheTop() throws IOException {
        given("50471,,16117,,18481");

        assertEquals("an unknown chapter opens at the top, not somewhere arbitrary",
                0, positionFor(99999));
    }

    @Test
    public void testAMissingFileStartsEveryChapterAtTheTop() {
        file.deleteAll();
        GlobalConfig.loadReadSaves();

        assertEquals(0, positionFor(50471));
    }

    @Test
    public void testAnEmptyFileStartsEveryChapterAtTheTop() throws IOException {
        given("");

        assertEquals(0, positionFor(50471));
    }

    @Test
    public void testARecordWithTooFewFieldsIsSkipped() throws IOException {
        given("50471,,16117||50472,,200,,4000");

        assertEquals(0, positionFor(50471));
        assertEquals("the intact record after it still loads", 200, positionFor(50472));
    }

    /**
     * <b>This is the point of the class.</b> {@code loadReadSaves} validates the field count and
     * then calls {@code Integer.valueOf} on each field without checking it is a number — where
     * {@code loadReadSavesV1}, doing the identical job, tests every field with
     * {@code LightTool.isInteger} first and skips the record if any fails.
     *
     * <p>So a damaged record throws {@link NumberFormatException} out of {@code loadReadSaves},
     * and every caller reaches it lazily with nothing catching on the way:
     * {@code getReadSavesRecord} is called from {@code VerticalReaderActivity:66} and from an
     * unguarded {@code onPostExecute} at {@code :363}, and {@code addReadSavesRecord} from
     * {@code onPause} at {@code :381}. The reader therefore crashes both when opening a chapter
     * and when leaving one, every time, until the file is deleted — and the user has no way to
     * delete it.
     *
     * <p>That is Phase 1 item 9 exactly, in a second location: the bookshelf had the same
     * unguarded parse, found the same way, and the fix here is the same one — drop the record that
     * cannot be read and report it, rather than rejecting the file and throwing away every other
     * chapter's position.
     */
    @Test
    public void testACorruptRecordDoesNotCrashTheReader() throws IOException {
        given("50471,,not-a-position,,18481||50472,,200,,4000");

        assertEquals("the damaged record is the only casualty", 0, positionFor(50471));
        assertEquals("the chapter after it keeps its position", 200, positionFor(50472));
    }

    @Test
    public void testAnEntirelyCorruptFileDoesNotCrashTheReader() throws IOException {
        given("this is not a progress file at all");

        assertEquals(0, positionFor(50471));
    }

    @Test
    public void testANewPositionSurvivesAReload() throws IOException {
        given("");

        GlobalConfig.addReadSavesRecord(50471, 16117, 18481);
        // Reload from disk rather than trusting the in-memory list, so this covers the write.
        GlobalConfig.loadReadSaves();

        assertEquals(16117, positionFor(50471));
    }

    @Test
    public void testScrollingOnUpdatesThePositionInPlace() throws IOException {
        given("50471,,16117,,18481");

        GlobalConfig.addReadSavesRecord(50471, 17000, 18481);
        GlobalConfig.loadReadSaves();

        assertEquals(17000, positionFor(50471));
        assertEquals("the record must be updated rather than appended",
                "50471,,17000,,18481", file.readBack());
    }

    /**
     * Barely scrolling is not a position worth restoring — {@code addReadSavesRecord} returns
     * early below 100. Worth pinning because the threshold is a bare literal with no name and
     * nothing else documents it.
     */
    @Test
    public void testATrivialScrollIsNotWorthSaving() throws IOException {
        given("");

        GlobalConfig.addReadSavesRecord(50471, BELOW_THE_SAVE_THRESHOLD, 18481);
        GlobalConfig.loadReadSaves();

        assertEquals(0, positionFor(50471));
    }
}
