package org.mewx.wenku8.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;

/**
 * The search entry screen and the results it opens.
 *
 * <p>Both were entirely uncovered — 68 and 33 lines — despite search history being where two real
 * storage defects were found. Those defects are characterized at the storage level by
 * {@code SearchHistoryTest}; nothing had ever executed the screens sitting on top of them.
 *
 * <p><b>No search is performed and no term is submitted</b>, so nothing is added to the device
 * owner's search history. The entry screen is opened, which reads the existing history to display
 * it, and the results screen is launched directly with a sentinel term — the same thing the entry
 * screen would do, without going through the input that would record it.
 *
 * <p>Assertions are structural, as elsewhere: these screens are being checked for surviving their
 * lifecycle, not for what they render, which depends on the owner's history and on the network.
 */
@LargeTest
public class SearchScreensTest {

    /**
     * A term chosen to match nothing and to be recognisable if it ever does leak into a real
     * history file. It is never submitted through the input, so it should not appear at all.
     */
    private static final String SENTINEL_TERM = "zzz-wenku8-test-999000401";

    @Before
    public void requireAnInteractiveDevice() {
        InteractiveDevice.require();
    }

    private static void assertStaysOpen(ActivityScenario<?> scenario, String what) {
        assertEquals(Lifecycle.State.RESUMED, scenario.getState());
        scenario.onActivity(activity ->
                assertFalse(what + " closed itself", activity.isFinishing()));
    }

    private static Intent resultsIntent() {
        final Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = new Intent(context, SearchResultActivity.class);
        intent.putExtra("key", SENTINEL_TERM);
        return intent;
    }

    /**
     * Opening search, which reads and lists the existing history.
     *
     * <p>This is the screen above the two history defects, so it is worth having executed even
     * while the defects themselves stay characterized rather than fixed: it confirms a history
     * containing whatever it contains does not stop the screen opening.
     */
    @Test
    public void theSearchScreenOpens() {
        try (ActivityScenario<SearchActivity> scenario =
                     ActivityScenario.launch(SearchActivity.class)) {
            assertStaysOpen(scenario, "the search screen");
        }
    }

    @Test
    public void theSearchScreenSurvivesRecreation() {
        try (ActivityScenario<SearchActivity> scenario =
                     ActivityScenario.launch(SearchActivity.class)) {
            scenario.recreate();
            assertStaysOpen(scenario, "the search screen");
        }
    }

    /** Backgrounded and returned to, the path that re-reads the history without re-creating. */
    @Test
    public void theSearchScreenSurvivesBeingBackgrounded() {
        try (ActivityScenario<SearchActivity> scenario =
                     ActivityScenario.launch(SearchActivity.class)) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            assertStaysOpen(scenario, "the search screen");
        }
    }

    /**
     * Results for a term that matches nothing, with no reachable server.
     *
     * <p>Empty results and a failed search are the same screen state here, and the contract is
     * that it stays open reporting it rather than closing.
     */
    @Test
    public void theResultsScreenOpensWithNoMatches() {
        try (ActivityScenario<SearchResultActivity> scenario =
                     ActivityScenario.launch(resultsIntent())) {
            assertStaysOpen(scenario, "the search results screen");
        }
    }

    @Test
    public void theResultsScreenSurvivesRecreation() {
        try (ActivityScenario<SearchResultActivity> scenario =
                     ActivityScenario.launch(resultsIntent())) {
            scenario.recreate();
            assertStaysOpen(scenario, "the search results screen");
        }
    }

    /**
     * Backgrounded while the search is still in flight.
     *
     * <p>The root-cause-1 shape for this screen: the query is started from startup and delivers
     * whenever it completes, which may be after the user has gone.
     */
    @Test
    public void theResultsScreenSurvivesBeingBackgroundedMidSearch() {
        try (ActivityScenario<SearchResultActivity> scenario =
                     ActivityScenario.launch(resultsIntent())) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            assertStaysOpen(scenario, "the search results screen");
        }
    }
}
