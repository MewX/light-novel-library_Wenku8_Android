package org.mewx.wenku8.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.os.Bundle;
import android.os.SystemClock;

import androidx.lifecycle.Lifecycle;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;
import org.mewx.wenku8.R;
import org.mewx.wenku8.RealApi;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.api.Wenku8API;

/**
 * The ranking-list Fragment, hosted with a list type it accepts.
 *
 * <p>{@code NovelItemListFragment} was 206 uncovered lines and the largest untested Fragment left.
 * {@code ConfigFragmentHostingTest} records an earlier attempt that was abandoned, and its reasons
 * were sound — both crashes it hit were the test building states the app never produces. This does
 * neither of them: it passes a real list type rather than {@code ""}, and hosts a ranking list
 * rather than {@code type=search}, whose progress indicator lives in the host Activity's layout and
 * so only exists under {@code SearchResultActivity}.
 *
 * <p>The blocker named there was the api-stub trap: a valid list type comes from the private
 * {@code api/} module, so a test written around one passes locally and dies on CI. {@link RealApi}
 * is the answer to that and did not exist when the attempt was made — these skip on CI rather than
 * failing there. The cost is the honest one: this coverage is not in the published figure.
 *
 * <p><b>The type string must be derived, never hardcoded</b>, because the two API implementations
 * spell it differently. The real module maps {@code NovelSortedBy.allVote} to {@code "allvote"} and
 * back; {@code api-stub} implements the same pair as {@code valueOf}/{@code name}, giving
 * {@code "allVote"}. Each round-trips against itself, so deriving the string the way
 * {@code RKListFragment} does is correct under either, while a literal {@code "allvote"} would
 * reach {@code valueOf} on CI and throw {@code IllegalArgumentException} from a background thread.
 *
 * <p><b>What this touches.</b> Ranking lists are public data and the Fragment only reads them —
 * nothing here writes to the bookshelf, the reading positions or the search history. It does make
 * real network requests, which is inherent to the screen: the list is fetched in
 * {@code onCreateView} and there is no seam to prevent it.
 */
@LargeTest
public class NovelItemListFragmentHostingTest {

    /** How long to let a page arrive before treating the network as unavailable. */
    private static final long LOAD_TIMEOUT_MS = 20_000;

    @Before
    public void requireAnInteractiveDeviceAndTheRealApi() {
        InteractiveDevice.require();
        RealApi.require();
    }

    /**
     * A list type spelled the way the running API implementation spells it — see the class note.
     * This mirrors {@code RKListFragment}'s pager, which builds its arguments the same way.
     */
    private static Bundle rankingArguments() {
        final Bundle args = new Bundle();
        args.putString("type", Wenku8API.getNovelSortedBy(Wenku8API.NovelSortedBy.allVote));
        return args;
    }

    private static NovelItemListFragment show(ActivityScenario<MainActivity> scenario) {
        final NovelItemListFragment fragment =
                NovelItemListFragment.newInstance(rankingArguments());
        scenario.onActivity(activity -> activity.changeFragment(fragment));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        return fragment;
    }

    private static void assertStillUsable(ActivityScenario<MainActivity> scenario, String when) {
        assertEquals(Lifecycle.State.RESUMED, scenario.getState());
        scenario.onActivity(activity ->
                assertFalse("the launcher finished itself " + when, activity.isFinishing()));
    }

    /**
     * Waits for the first page to arrive, and reports how many rows it produced.
     *
     * <p>Returns 0 rather than failing when nothing arrives: whether the device can reach the
     * server is not what most of these tests are about, and the ones that do need it say so with
     * {@code assumeTrue} so an offline run reads as skipped rather than as passed.
     */
    private static int awaitRowCount(NovelItemListFragment fragment) {
        final long deadline = SystemClock.uptimeMillis() + LOAD_TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            final int rows = rowCount(fragment);
            if (rows > 0) {
                return rows;
            }
            SystemClock.sleep(200);
        }
        return rowCount(fragment);
    }

    private static int rowCount(NovelItemListFragment fragment) {
        final android.view.View view = fragment.getView();
        if (view == null) {
            return 0;
        }
        final RecyclerView list = view.findViewById(R.id.novel_item_list);
        if (list == null || list.getAdapter() == null) {
            return 0;
        }
        return list.getAdapter().getItemCount();
    }

    /**
     * Opening a ranking list, which is the whole startup path: {@code onCreate} reading the
     * arguments, {@code onCreateView} inflating and dispatching the fetch, and
     * {@code AsyncGetNovelItemList} running to {@code onPostExecute}. None of it had ever run.
     */
    @Test
    public void aRankingListOpens() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            show(scenario);
            assertStillUsable(scenario, "on opening a ranking list");
        }
    }

    /**
     * The page actually loads and populates the list.
     *
     * <p>This is the case that reaches {@code refreshPartialInfoList} and the adapter, so it is the
     * one carrying most of the coverage. It needs the network, and says so rather than passing
     * vacuously when there is none.
     */
    @Test
    public void aRankingListShowsTheNovelsItFetched() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            final NovelItemListFragment fragment = show(scenario);

            final int rows = awaitRowCount(fragment);
            Assume.assumeTrue(
                    "no page arrived within " + LOAD_TIMEOUT_MS + "ms; this case needs the server",
                    rows > 0);

            assertTrue("a loaded ranking page should hold rows", rows > 0);
            assertStillUsable(scenario, "after loading a ranking page");
        }
    }

    /**
     * Recreated after the page has settled. The Fragment keeps its parsed list in fields and
     * rebuilds the adapter from them in {@code onCreateView}, so this is the branch at the top of
     * that method rather than a second fetch.
     */
    @Test
    public void aLoadedRankingListSurvivesRecreationOfItsHost() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            final NovelItemListFragment fragment = show(scenario);
            awaitRowCount(fragment);

            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertStillUsable(scenario, "during recreation with a ranking list shown");
        }
    }

    /**
     * Recreated while the fetch is still in flight, which is the case the {@code AsyncTaskTracker}
     * and the {@code isAdded()} guard in {@code onPostExecute} exist for: a result must not be
     * delivered into a Fragment that no longer has an Activity.
     *
     * <p>Deliberately no wait before recreating — the point is to land inside the window.
     */
    @Test
    public void aRankingListSurvivesItsHostBeingRecreatedMidFetch() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            show(scenario);

            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(2_000); // let any in-flight result come back into the old Fragment

            assertStillUsable(scenario, "after being recreated mid-fetch");
        }
    }

    /** Backgrounded and returned to, which runs the host's onPause and onResume around the list. */
    @Test
    public void aRankingListSurvivesBeingBackgrounded() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            final NovelItemListFragment fragment = show(scenario);
            awaitRowCount(fragment);

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertStillUsable(scenario, "after returning to a ranking list");
        }
    }

    /**
     * The real host rather than a bare one: {@code RKListFragment} builds a pager of these lists,
     * one per sort order, and is how a user actually reaches them.
     */
    @Test
    public void theRankingHostBuildsItsPagerOfLists() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            final RKListFragment host = new RKListFragment();
            scenario.onActivity(activity -> activity.changeFragment(host));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();
            SystemClock.sleep(3_000); // the pager creates and starts fetching several pages

            assertNotNull("the ranking host built no view", host.getView());
            assertStillUsable(scenario, "on opening the ranking pager");
        }
    }
}
