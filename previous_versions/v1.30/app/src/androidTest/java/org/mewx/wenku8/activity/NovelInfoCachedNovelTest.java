package org.mewx.wenku8.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;
import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;

import java.io.File;

/**
 * The novel detail screen with a novel it can actually display.
 *
 * <p>{@link NovelInfoActivityLifecycleTest} covers this screen when the load fails, which is the
 * only state it had ever been tested in. Everything the screen exists to do — parsing the cached
 * metadata, filling the header, building the volume list, and building a volume's chapter list —
 * sits past that failure and was entirely uncovered: 644 of 817 lines, the largest untested block
 * left in the app.
 *
 * <p><b>These tests make no network call, by construction rather than by luck.</b> Launching with
 * {@code from="fav"} sends the screen down {@code refreshInfoFromLocal}, whose whole fetch is three
 * reads out of the {@code intro} save folder. Planting those three files is therefore enough to
 * drive the entire success path offline — which is also why, unlike the review and search screens,
 * this class needs no {@code RealApi} guard and does count towards the coverage figure CI
 * publishes.
 *
 * <p>Sentinel ids sit far above any real novel, and in a range no other test uses, so nothing here
 * can collide with the device owner's library or with {@code VerticalReaderActivityLifecycleTest}'s
 * chapter fixture. The three planted files are removed afterwards, and nothing else is written:
 * {@code cacheVolumeIndex} is guarded by {@code !fromLocal}, so the local path writes nothing at
 * all.
 *
 * <p>Assertions are structural where the app chooses the text and exact where the fixture does.
 * The volume and chapter names below are this test's own strings, so asserting on them pins down
 * the screen's behaviour rather than the wording of a message — the distinction
 * {@link NovelInfoActivityLifecycleTest} draws about the untranslated error text still holds, and
 * no assertion here touches one.
 */
@LargeTest
public class NovelInfoCachedNovelTest {

    private static final int TEST_AID = 999_000_007;

    /** Volume one's name, asserted on: it is this test's string, not one the app authored. */
    private static final String VOLUME_ONE = "sentinel volume one";
    private static final String VOLUME_TWO = "sentinel volume two";

    private static final String META_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<metadata>\n"
            + "<data name=\"Title\" aid=\"" + TEST_AID + "\">"
            + "<![CDATA[sentinel cached novel]]></data>\n"
            + "<data name=\"Author\" value=\"instrumentation fixture\"/>\n"
            + "<data name=\"BookStatus\" value=\"fixture-status\"/>\n"
            + "<data name=\"LastUpdate\" value=\"2026-01-01\"/>\n"
            + "<data name=\"LatestSection\" cid=\"999000722\">"
            + "<![CDATA[sentinel latest chapter]]></data>\n"
            + "</metadata>";

    /** Assigned straight onto the meta rather than parsed; it only has to be non-empty. */
    private static final String INTRO_TEXT =
            "A cached introduction planted by NovelInfoCachedNovelTest. It is never shown to a "
                    + "real user: the novel it belongs to does not exist.";

    private static final String VOLUME_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<package>\n"
            + "<volume vid=\"999000701\"><![CDATA[" + VOLUME_ONE + "]]>\n"
            + "<chapter cid=\"999000711\"><![CDATA[sentinel chapter 1-1]]></chapter>\n"
            + "<chapter cid=\"999000712\"><![CDATA[sentinel chapter 1-2]]></chapter>\n"
            + "</volume>\n"
            + "<volume vid=\"999000702\"><![CDATA[" + VOLUME_TWO + "]]>\n"
            + "<chapter cid=\"999000721\"><![CDATA[sentinel chapter 2-1]]></chapter>\n"
            + "<chapter cid=\"999000722\"><![CDATA[sentinel chapter 2-2]]></chapter>\n"
            + "<chapter cid=\"999000723\"><![CDATA[sentinel chapter 2-3]]></chapter>\n"
            + "</volume>\n"
            + "</package>";

    private static final String INTRO_FOLDER = "intro";
    private static final String META_FILE = TEST_AID + "-intro.xml";
    private static final String INTRO_FILE = TEST_AID + "-introfull.xml";
    private static final String VOLUME_FILE = TEST_AID + "-volume.xml";

    /**
     * How long the screen is given to display the cached novel.
     *
     * <p>Startup posts its first load 500 ms out and then runs it on an {@code AsyncTask}, so
     * {@code waitForIdleSync} is not enough on its own — it returns while the delayed runnable is
     * still queued. Generous because a cold emulator under an instrumented, JaCoCo-instrumented
     * build is much slower than a warm device, and a flaky timeout would be worse than a slow test.
     */
    private static final long LOAD_TIMEOUT_MS = 20_000;

    @Before
    public void requireAnInteractiveDeviceAndPlantTheCache() {
        InteractiveDevice.require();

        // A run that died mid-test would otherwise leave these behind and decide the next run for
        // the wrong reason, the same guard VerticalReaderActivityLifecycleTest applies.
        deleteTheCache();

        assertTrue("could not plant the cached metadata", GlobalConfig
                .writeFullFileIntoSaveFolder(INTRO_FOLDER, META_FILE, META_XML));
        assertTrue("could not plant the cached introduction", GlobalConfig
                .writeFullFileIntoSaveFolder(INTRO_FOLDER, INTRO_FILE, INTRO_TEXT));
        assertTrue("could not plant the cached volume index", GlobalConfig
                .writeFullFileIntoSaveFolder(INTRO_FOLDER, VOLUME_FILE, VOLUME_XML));

        // Written and read back through the same pair of helpers, but they resolve the storage
        // root independently on every call, so proving the screen will find what was just written
        // is worth one assertion. Without it a root that moved mid-run would surface as the
        // failed-load state and read as a bug in the screen.
        assertFalse("the planted volume index is not readable back through the save folder",
                GlobalConfig.loadFullFileFromSaveFolder(INTRO_FOLDER, VOLUME_FILE).isEmpty());
    }

    @After
    public void removeTheCache() {
        deleteTheCache();
    }

    private static void deleteTheCache() {
        for (String name : new String[]{META_FILE, INTRO_FILE, VOLUME_FILE}) {
            for (String root : new String[]{
                    GlobalConfig.getFirstFullSaveFilePath(),
                    GlobalConfig.getSecondFullSaveFilePath()}) {
                //noinspection ResultOfMethodCallIgnored
                new File(root + INTRO_FOLDER + File.separator + name).delete();
            }
        }
    }

    private static Intent infoIntent() {
        final Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = new Intent(context, NovelInfoActivity.class);
        intent.putExtra("aid", TEST_AID);
        // "fav" is what sends the screen to refreshInfoFromLocal; any other value would go to the
        // network and this whole class would depend on reachability.
        intent.putExtra("from", "fav");
        intent.putExtra("title", "sentinel cached novel");
        return intent;
    }

    private static ViewGroup volumeListOf(NovelInfoActivity activity) {
        return activity.findViewById(R.id.novel_info_scroll);
    }

    /**
     * The two views the layout starts with, before any volume is added.
     *
     * <p>{@code buildVolumeList} appends one row per volume after these, and clears from index 2
     * when it runs again — so "loaded" means more than two children, and the count above two is
     * the number of volumes.
     */
    private static final int STATIC_HEADER_VIEWS = 2;

    /** Blocks until the volume list has been built, or fails saying what the screen settled on. */
    private static void awaitTheVolumeList(ActivityScenario<NovelInfoActivity> scenario) {
        final long deadline = SystemClock.uptimeMillis() + LOAD_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final int[] children = new int[1];
            scenario.onActivity(activity ->
                    children[0] = volumeListOf(activity).getChildCount());
            if (children[0] > STATIC_HEADER_VIEWS) {
                return;
            }
            SystemClock.sleep(100);
        }

        final boolean[] failed = new boolean[1];
        scenario.onActivity(activity ->
                failed[0] = activity.findViewById(R.id.ll_error).getVisibility() == View.VISIBLE);
        fail(failed[0]
                ? "the screen showed its failed-load state instead of the planted cached novel, "
                        + "so the fixture was not where refreshInfoFromLocal looked for it"
                : "the volume list was never built within " + LOAD_TIMEOUT_MS + "ms");
    }

    /**
     * The whole point of the class: a cached novel is parsed and displayed with no network.
     *
     * <p>Reaching the assertions means {@code doInBackground} took its local branch and returned
     * success, {@code onPostExecute} took its success branch, and {@code buildVolumeList} ran —
     * none of which any test had executed before.
     */
    @Test
    public void aCachedNovelIsDisplayedWithoutTheNetwork() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            awaitTheVolumeList(scenario);

            scenario.onActivity(activity -> {
                assertEquals("both cached volumes should be listed", 2,
                        volumeListOf(activity).getChildCount() - STATIC_HEADER_VIEWS);
                assertEquals("the failed-load panel is still showing over a novel that loaded",
                        View.GONE, activity.findViewById(R.id.ll_error).getVisibility());
                assertEquals("the novel body was not made visible", View.VISIBLE,
                        activity.findViewById(R.id.novel_info_scroll_view).getVisibility());
                assertFalse(activity.isFinishing());
            });
        }
    }

    /** The header fields, which are filled from the parsed metadata rather than from the Intent. */
    @Test
    public void theHeaderIsFilledFromTheCachedMetadata() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            awaitTheVolumeList(scenario);

            scenario.onActivity(activity -> {
                assertEquals("instrumentation fixture",
                        ((TextView) activity.findViewById(R.id.novel_author)).getText().toString());
                assertEquals("fixture-status",
                        ((TextView) activity.findViewById(R.id.novel_status)).getText().toString());
                assertEquals(INTRO_TEXT, ((TextView) activity
                        .findViewById(R.id.novel_intro_full)).getText().toString());
            });
        }
    }

    /**
     * Opening a volume, which is the only way {@code buildChapterList} ever runs.
     *
     * <p>Driven by clicking the row the screen built rather than by calling anything directly:
     * the click listener is where the volume is bound, so calling past it would cover the method
     * without covering the wiring that reaches it.
     */
    @Test
    public void openingAVolumeBuildsItsChapterList() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            awaitTheVolumeList(scenario);
            openVolume(scenario, 0);

            scenario.onActivity(activity -> {
                assertEquals("volume one's three-chapter sibling was listed instead", 2,
                        ((ViewGroup) activity.findViewById(R.id.novel_chapter_scroll))
                                .getChildCount());
                assertEquals(VOLUME_ONE, ((TextView) activity
                        .findViewById(R.id.side_sheet_header)).getText().toString());
            });
        }
    }

    /**
     * A second volume opened after the first.
     *
     * <p>{@code buildChapterList} clears the panel before refilling it, and a panel that appended
     * instead would still look right after one click. The two fixture volumes have different
     * chapter counts precisely so this can tell the difference.
     */
    @Test
    public void openingASecondVolumeReplacesTheChapterList() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            awaitTheVolumeList(scenario);
            openVolume(scenario, 0);
            openVolume(scenario, 1);

            scenario.onActivity(activity -> {
                assertEquals("the second volume's chapters were appended to the first volume's "
                                + "rather than replacing them", 3,
                        ((ViewGroup) activity.findViewById(R.id.novel_chapter_scroll))
                                .getChildCount());
                assertEquals(VOLUME_TWO, ((TextView) activity
                        .findViewById(R.id.side_sheet_header)).getText().toString());
            });
        }
    }

    private static void openVolume(ActivityScenario<NovelInfoActivity> scenario, int index) {
        scenario.onActivity(activity -> {
            final View row = volumeListOf(activity).getChildAt(STATIC_HEADER_VIEWS + index);
            assertNotNull("no row for volume " + index, row);
            row.findViewById(R.id.chapter_btn).performClick();
        });
    }

    /**
     * Rebuilt from scratch with the cache still in place.
     *
     * <p>Different from the failed-load recreation test: here the screen has parsed state to lose,
     * and {@code buildVolumeList}'s "remove everything after index 2" runs against a list that a
     * previous pass already filled.
     */
    @Test
    public void theCachedNovelSurvivesRecreation() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            awaitTheVolumeList(scenario);
            scenario.recreate();
            awaitTheVolumeList(scenario);

            scenario.onActivity(activity -> assertEquals(
                    "recreation left a duplicated or truncated volume list", 2,
                    volumeListOf(activity).getChildCount() - STATIC_HEADER_VIEWS));
        }
    }

    /**
     * Backgrounded and brought back with a volume open.
     *
     * <p>The side panel holds the selected volume in a field, so this is where a rebuild that
     * half-restores it would show.
     */
    @Test
    public void theOpenChapterListSurvivesBeingBackgrounded() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            awaitTheVolumeList(scenario);
            openVolume(scenario, 0);

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(
                    "the screen finished itself while a volume was open", activity.isFinishing()));
        }
    }

    /**
     * "Continue reading" on a novel that has never been read.
     *
     * <p>Covers the loading check and the no-saved-position branch. The sibling menu item, "go to
     * forum", is deliberately left alone: it starts {@code NovelReviewListActivity}, which is one
     * of the screens that cannot run against api-stub, so exercising it here would drag this class
     * behind the same guard and out of the published coverage figure for no real gain.
     */
    @Test
    public void continueReadingReportsThatThereIsNoSavedPosition() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            awaitTheVolumeList(scenario);

            scenario.onActivity(activity -> {
                final PopupMenu host = new PopupMenu(activity, volumeListOf(activity));
                activity.getMenuInflater().inflate(R.menu.menu_novel_info, host.getMenu());
                final MenuItem item = host.getMenu()
                        .findItem(R.id.action_continue_read_progress);
                assertNotNull("the menu no longer has a continue-reading item", item);

                activity.onOptionsItemSelected(item);

                assertFalse("selecting continue-reading closed the screen; with no saved position "
                        + "it should only report that there is nothing to continue",
                        activity.isFinishing());
            });
        }
    }
}
