package org.mewx.wenku8.global;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

/**
 * Recent search terms, stored as {@code [first][second][third]} — a bracket-delimited format with
 * no escaping of any kind.
 *
 * <p>Lower stakes than reading positions: losing a search history costs a user nothing they cannot
 * retype. It is here because the format is the least defensible one in the save folder and nothing
 * had ever exercised it, and because its content is the only thing in that folder typed directly
 * by the user — so it is the one file whose contents an attacker-shaped input reaches without going
 * through the server first.
 *
 * <p><b>Two of these tests pin behaviour that is wrong.</b> They are written as characterization,
 * not as approval: they assert what the code does today so that a later fix has to state that it is
 * changing something, rather than discovering it by accident. Both are named so they cannot be
 * mistaken for intended behaviour, and both are described in {@code STABILITY_PLAN.md} alongside
 * item 11, which is the same judgement call — a known defect in storage code that is under a
 * standing freeze, recorded rather than patched in place.
 *
 * <p>Device-only, and it rewrites the real file. See {@link SaveFileFixture}.
 */
@SmallTest
public class SearchHistoryTest {

    private static final String SEARCH_HISTORY_FILE = "search_history.wk8";

    private final SaveFileFixture file = new SaveFileFixture(SEARCH_HISTORY_FILE);

    @Before
    public void captureTheRealHistory() throws IOException {
        file.capture();
    }

    @After
    public void restoreTheRealHistory() throws IOException {
        file.restore();
        // Resync the static, so the next test in this process does not inherit this test's terms.
        GlobalConfig.readSearchHistory();
    }

    private void given(String content) throws IOException {
        file.arrange(content);
        GlobalConfig.readSearchHistory();
    }

    @Test
    public void testStoredTermsLoadBackInOrder() throws IOException {
        given("[fate][monogatari][overlord]");

        final List<String> history = GlobalConfig.getSearchHistory();

        assertEquals(3, history.size());
        assertEquals("fate", history.get(0));
        assertEquals("monogatari", history.get(1));
        assertEquals("overlord", history.get(2));
    }

    @Test
    public void testAMissingFileLoadsAnEmptyHistory() {
        file.deleteAll();
        GlobalConfig.readSearchHistory();

        assertTrue(GlobalConfig.getSearchHistory().isEmpty());
    }

    @Test
    public void testAnEmptyFileLoadsAnEmptyHistory() throws IOException {
        given("");

        assertTrue(GlobalConfig.getSearchHistory().isEmpty());
    }

    @Test
    public void testAnUnterminatedTermIsDropped() throws IOException {
        // What a write cut short looks like: the last bracket never landed.
        given("[fate][monogatari");

        final List<String> history = GlobalConfig.getSearchHistory();

        assertEquals(1, history.size());
        assertEquals("fate", history.get(0));
    }

    @Test
    public void testAFileWithNoBracketsAtAllLoadsAnEmptyHistory() throws IOException {
        given("this is not a search history");

        assertTrue(GlobalConfig.getSearchHistory().isEmpty());
    }

    @Test
    public void testCjkTermsSurviveTheRoundTrip() throws IOException {
        // The save folder is UTF-8 throughout and most real searches on this app are CJK, so a
        // round trip that only works for ASCII would be worth nothing.
        given("");

        GlobalConfig.addSearchHistory("刀剑神域");
        GlobalConfig.readSearchHistory();

        assertEquals("刀剑神域", GlobalConfig.getSearchHistory().get(0));
    }

    @Test
    public void testANewTermGoesToTheFrontAndSurvivesAReload() throws IOException {
        given("[fate][monogatari]");

        GlobalConfig.addSearchHistory("overlord");
        // Reload from disk rather than trusting the in-memory list, so this covers the write.
        GlobalConfig.readSearchHistory();

        final List<String> history = GlobalConfig.getSearchHistory();
        assertEquals(3, history.size());
        assertEquals("the most recent search belongs at the top", "overlord", history.get(0));
    }

    @Test
    public void testSearchingSomethingAgainMovesItToTheFrontWithoutDuplicating()
            throws IOException {
        given("[fate][monogatari][overlord]");

        GlobalConfig.addSearchHistory("overlord");
        GlobalConfig.readSearchHistory();

        final List<String> history = GlobalConfig.getSearchHistory();
        assertEquals("repeating a search must not add a second copy", 3, history.size());
        assertEquals("overlord", history.get(0));
    }

    @Test
    public void testADeletedTermStaysDeleted() throws IOException {
        given("[fate][monogatari][overlord]");

        GlobalConfig.deleteSearchHistory("monogatari");
        GlobalConfig.readSearchHistory();

        final List<String> history = GlobalConfig.getSearchHistory();
        assertEquals(2, history.size());
        assertFalse(history.contains("monogatari"));
        assertTrue(history.contains("fate"));
        assertTrue(history.contains("overlord"));
    }

    @Test
    public void testClearingLeavesNothingBehindOnDisk() throws IOException {
        given("[fate][monogatari][overlord]");

        GlobalConfig.clearSearchHistory();
        GlobalConfig.readSearchHistory();

        assertTrue(GlobalConfig.getSearchHistory().isEmpty());
        // Either an empty file or no file at all -- both load as an empty history, and which one
        // happens depends on which storage root is live. Worth knowing while reading this: in the
        // test process the default root resolves to the legacy external path, which is unwritable
        // on API 29+, because the flag that redirects it to internal is only set inside
        // loadAllSetting() and nothing here calls that. So every write below takes the fallback
        // branch. Pinning the exact on-disk shape would pin the environment instead of the
        // contract; what must hold is that no file is left holding the cleared terms.
        final String onDisk = file.readBack();
        assertTrue("clearing must not leave the old terms on disk, but found: " + onDisk,
                onDisk == null || onDisk.isEmpty());
    }

    @Test
    public void testTheHistoryIsCappedAtTheConfiguredMaximum() throws IOException {
        final int max = GlobalConfig.getMaxSearchHistory();
        final StringBuilder full = new StringBuilder();
        for (int i = 0; i < max; i++) {
            full.append("[term").append(i).append("]");
        }
        given(full.toString());

        GlobalConfig.addSearchHistory("the newest one");
        GlobalConfig.readSearchHistory();

        final List<String> history = GlobalConfig.getSearchHistory();
        assertEquals("an unbounded history would grow this file forever", max, history.size());
        assertEquals("the newest one", history.get(0));
        assertFalse("the oldest term is the one that should have been dropped",
                history.contains("term" + (max - 1)));
    }

    /**
     * <b>Known defect, pinned rather than endorsed.</b> The format wraps each term in brackets and
     * escapes nothing, so a term containing {@code ]} closes its own entry early and everything
     * after it in that term is lost on the next load. Typing {@code ]} into the search box is all
     * it takes.
     *
     * <p>Contained, which is why it is recorded rather than fixed: only the offending term is
     * damaged, and the terms either side of it still load. A user loses part of one search they can
     * retype.
     */
    @Test
    public void testKnownDefectATermContainingAClosingBracketIsTruncatedOnReload()
            throws IOException {
        given("");

        GlobalConfig.addSearchHistory("fate]zero");
        GlobalConfig.addSearchHistory("monogatari");
        GlobalConfig.readSearchHistory();

        final List<String> history = GlobalConfig.getSearchHistory();
        assertTrue("the unaffected term must still load", history.contains("monogatari"));
        assertFalse("today the term does not survive intact", history.contains("fate]zero"));
        assertTrue("...it comes back truncated at the bracket", history.contains("fate"));
    }

    /**
     * <b>Known defect, pinned rather than endorsed.</b> The scanner in {@code readSearchHistory}
     * advances its cursor to just past each <i>opening</i> bracket and never past the closing one,
     * so the next iteration re-scans the inside of the term it just read. For well-formed terms
     * that is harmless — the next {@code [} it finds is the real one. For a term that itself
     * contains {@code [}, the bracket inside it is read as the start of another entry, and the tail
     * of the term reappears as a phantom nobody ever searched for.
     *
     * <p>It compounds, which is what makes it worth pinning: the phantom is written back on the
     * next save, and the term that produced it is still there to produce another one, so the
     * history grows by one entry per round trip until the cap evicts the user's real searches.
     */
    @Test
    public void testKnownDefectATermContainingAnOpeningBracketSpawnsAPhantomEntry()
            throws IOException {
        given("");

        GlobalConfig.addSearchHistory("fate[zero");
        GlobalConfig.readSearchHistory();

        final List<String> history = GlobalConfig.getSearchHistory();
        assertTrue("the term itself does survive intact", history.contains("fate[zero"));
        assertTrue("...but its tail comes back as a search that was never made",
                history.contains("zero"));
        assertEquals("one search, two entries", 2, history.size());
    }

    /**
     * <b>Known defect, pinned rather than endorsed.</b> {@code addSearchHistory} and
     * {@code deleteSearchHistory} both open with {@code if (searchHistory.contains("[")) return;},
     * which asks whether the <i>list</i> holds a term equal to {@code "["} — almost certainly meant
     * as a check on the incoming {@code record}. As written the guard is dead for its apparent
     * purpose and live for a different one.
     *
     * <p>This is the worse of the two, because it is not contained. A single search for {@code [}
     * round-trips into the list as a term equal to {@code "["} (plus an empty phantom, by the
     * mechanism above), and from that point the guard fires on every call: the history silently
     * stops recording, and stops accepting deletions too, for the life of the install. Clearing the
     * history is the only way out, and nothing tells the user that.
     */
    @Test
    public void testKnownDefectABareBracketTermFreezesTheHistoryPermanently() throws IOException {
        // What one search for "[" leaves on disk. The empty second entry is the phantom.
        given("[[]");
        assertTrue("precondition: a bare bracket really is one of the loaded terms",
                GlobalConfig.getSearchHistory().contains("["));

        GlobalConfig.addSearchHistory("fate");
        GlobalConfig.deleteSearchHistory("[");
        GlobalConfig.readSearchHistory();

        final List<String> history = GlobalConfig.getSearchHistory();
        assertFalse("today the new search is silently dropped", history.contains("fate"));
        assertTrue("...and the term causing it cannot be deleted either", history.contains("["));
    }
}
