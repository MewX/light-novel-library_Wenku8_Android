package org.mewx.wenku8.global;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;
import org.mewx.wenku8.api.Wenku8API;

import java.io.IOException;
import java.util.List;

/**
 * The remaining reachable corners of {@code GlobalConfig} — image file naming, the bundled
 * licence, the language setting, and moving a book to the front of the bookshelf.
 *
 * <p>Second half of step 1 in the refactor sequenced in {@code STABILITY_PLAN.md}. What is left
 * after this is either deliberately out of scope (credentials, network downloads) or unreachable
 * until step 2 supplies a storage seam, so this is where step 1 stops.
 *
 * <p><b>{@code onSearchClicked} was deleted rather than tested.</b> It was {@code @Deprecated},
 * had no callers anywhere, and carried the same defect already fixed in
 * {@code moveBookToTheTopOfBookshelf}: every sibling guards the lazily-loaded static with
 * {@code if (searchHistory == null) readSearchHistory()} and that one reached straight for
 * {@code searchHistory.size()}. Writing a test for dead code would have raised the coverage number
 * and protected nothing.
 *
 * <p><b>This rewrites the real bookshelf and settings files.</b> Both are captured before each test
 * and put back afterwards through {@link SaveFileFixture}, with the matching {@code GlobalConfig}
 * static reloaded so nothing downstream in the same process sees this test's values.
 */
@LargeTest
public class GlobalConfigMiscTest {

    private static final String BOOKSHELF_FILE = "bookshelf_local.wk8";
    private static final String SETTINGS_FILE = "settings.wk8";

    private static final int FIRST_AID = 999_000_911;
    private static final int SECOND_AID = 999_000_912;
    private static final int THIRD_AID = 999_000_913;
    private static final int ABSENT_AID = 999_000_914;

    private final SaveFileFixture bookshelf = new SaveFileFixture(BOOKSHELF_FILE);
    private final SaveFileFixture settings = new SaveFileFixture(SETTINGS_FILE);

    private Wenku8API.AppLanguage realLanguage;

    @Before
    public void captureTheRealFiles() throws IOException {
        InteractiveDevice.require();

        bookshelf.capture();
        settings.capture();
        realLanguage = GlobalConfig.getCurrentLang();
    }

    @After
    public void restoreTheRealFiles() throws IOException {
        bookshelf.restore();
        settings.restore();

        // Resync both statics from the restored files. Skipping either would leave this test's
        // bookshelf or language in the process for whatever runs next.
        GlobalConfig.loadLocalBookShelf();
        GlobalConfig.loadAllSetting();
        if (realLanguage != null) {
            GlobalConfig.setCurrentLang(realLanguage);
        }
    }

    // ---- image file naming -------------------------------------------------------------------

    /**
     * The documented example, taken from the method's own javadoc: everything after the first
     * path segment containing a dot is concatenated.
     */
    @Test
    public void anImageUrlBecomesTheDocumentedFileName() {
        assertEquals("pictures113054175950471.jpg", GlobalConfig.generateImageFileNameByURL(
                "http://pic.wenku8.cn/pictures/1/1305/41759/50471.jpg"));
    }

    /** The host is what starts the capture, so a different host yields a different name. */
    @Test
    public void theHostIsExcludedFromTheGeneratedName() {
        final String fromOneHost = GlobalConfig.generateImageFileNameByURL(
                "http://a.example.com/pictures/1/2.jpg");
        final String fromAnother = GlobalConfig.generateImageFileNameByURL(
                "http://b.example.org/pictures/1/2.jpg");

        assertEquals("only the path should contribute", fromOneHost, fromAnother);
        assertEquals("pictures12.jpg", fromOneHost);
    }

    /** No dotted segment means nothing ever starts the capture, and the result is empty. */
    @Test
    public void aUrlWithNoDottedSegmentYieldsAnEmptyName() {
        assertEquals("", GlobalConfig.generateImageFileNameByURL("http://localhost/a/b/c"));
    }

    // ---- bundled licence ---------------------------------------------------------------------

    /**
     * The licence is read from a raw resource on every call. Worth one test because a missing or
     * unreadable resource would otherwise surface as an empty About screen rather than a failure.
     */
    @Test
    public void theBundledLicenceIsReadable() {
        final String licence = GlobalConfig.getOpensourceLicense();

        assertNotNull(licence);
        assertFalse("the bundled licence resource read back empty", licence.trim().isEmpty());
    }

    // ---- language ----------------------------------------------------------------------------

    /** Setting the language persists it, so the next start comes back in the same one. */
    @Test
    public void theLanguageSurvivesBeingSetAndReloaded() {
        GlobalConfig.setCurrentLang(Wenku8API.AppLanguage.TC);

        GlobalConfig.loadAllSetting();

        assertEquals(Wenku8API.AppLanguage.TC, GlobalConfig.getCurrentLang());
        assertEquals("the API's copy should track the app's",
                Wenku8API.AppLanguage.TC, Wenku8API.CurrentLang);
    }

    @Test
    public void theLanguageCanBeSetBackAgain() {
        GlobalConfig.setCurrentLang(Wenku8API.AppLanguage.TC);
        GlobalConfig.setCurrentLang(Wenku8API.AppLanguage.SC);

        GlobalConfig.loadAllSetting();

        assertEquals(Wenku8API.AppLanguage.SC, GlobalConfig.getCurrentLang());
    }

    // ---- moving a book to the front ----------------------------------------------------------

    private void arrangeBookshelf() throws IOException {
        bookshelf.arrange(FIRST_AID + "||" + SECOND_AID + "||" + THIRD_AID);
        GlobalConfig.loadLocalBookShelf();
    }

    /**
     * The reordering the bookshelf performs when a novel is opened, so the most recently read sits
     * at the top.
     */
    @Test
    public void openingABookMovesItToTheFront() throws IOException {
        arrangeBookshelf();

        GlobalConfig.moveBookToTheTopOfBookshelf(THIRD_AID);

        final List<Integer> order = GlobalConfig.getLocalBookshelfList();
        assertEquals(THIRD_AID, (int) order.get(0));
        assertEquals(FIRST_AID, (int) order.get(1));
        assertEquals(SECOND_AID, (int) order.get(2));
        assertEquals("nothing should have been added or dropped", 3, order.size());
    }

    /** Moving the one already at the front is a no-op rather than a duplicate. */
    @Test
    public void movingTheFrontBookLeavesTheOrderAlone() throws IOException {
        arrangeBookshelf();

        GlobalConfig.moveBookToTheTopOfBookshelf(FIRST_AID);

        final List<Integer> order = GlobalConfig.getLocalBookshelfList();
        assertEquals(FIRST_AID, (int) order.get(0));
        assertEquals(3, order.size());
    }

    /** A novel that is not on the shelf must not be added by being opened. */
    @Test
    public void movingABookThatIsNotOnTheShelfChangesNothing() throws IOException {
        arrangeBookshelf();

        GlobalConfig.moveBookToTheTopOfBookshelf(ABSENT_AID);

        final List<Integer> order = GlobalConfig.getLocalBookshelfList();
        assertEquals(3, order.size());
        assertFalse(order.contains(ABSENT_AID));
        assertEquals(FIRST_AID, (int) order.get(0));
    }

    /**
     * The reordering is persisted, not merely applied in memory — which is the whole point, since
     * the ordering has to survive the process.
     */
    @Test
    public void theNewOrderIsWrittenToDisk() throws IOException {
        arrangeBookshelf();

        GlobalConfig.moveBookToTheTopOfBookshelf(SECOND_AID);
        GlobalConfig.loadLocalBookShelf();

        assertEquals(SECOND_AID, (int) GlobalConfig.getLocalBookshelfList().get(0));
        final String onDisk = bookshelf.readBack();
        assertNotNull(bookshelf.describe(), onDisk);
        assertTrue("the reordered shelf was not written back: " + onDisk,
                onDisk.startsWith(String.valueOf(SECOND_AID)));
    }
}
