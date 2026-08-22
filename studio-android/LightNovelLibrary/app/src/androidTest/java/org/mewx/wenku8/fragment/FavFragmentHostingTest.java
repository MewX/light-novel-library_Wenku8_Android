package org.mewx.wenku8.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;
import org.mewx.wenku8.RealApi;
import org.mewx.wenku8.activity.MainActivity;

/**
 * The bookshelf, driven through the screen that really hosts it.
 *
 * <p>{@code FavFragment} was 275 uncovered lines of 280 — the largest untested thing in the app,
 * and the first screen most users see. These do not use {@code FragmentScenario}: that would mean
 * adding {@code fragment-testing}, and it would host the Fragment in a bare container rather than
 * in {@link MainActivity}, which is the only place it is ever hosted. {@code changeFragment} is
 * public and is the seam the app itself uses.
 *
 * <p><b>These tests do change the device's bookshelf, and an earlier version of this comment
 * wrongly promised they did not.</b> Hosting the Fragment runs its {@code onResume}, whose first
 * pass always takes the cloud branch, so a run on a logged-in device performs a real bookshelf
 * sync. There is no way to host it and avoid that: the branch is chosen by a counter the Fragment
 * owns. Observed on the development device, 50 entries became 64.
 *
 * <p>What makes that acceptable rather than merely unavoidable is that the sync is <i>additive</i>.
 * {@code AsyncLoadAllFromCloud} unions the local and cloud lists rather than replacing one with the
 * other, so it cannot drop a novel that exists only on the device, and the result was verified to
 * contain no duplicates and no sentinel ids. It is also exactly what the app does whenever a
 * logged-in user opens the bookshelf — this runs the same code the same way, not a test-only path.
 *
 * <p>Nothing is written on purpose, and no fixture is planted: these read whatever the owner
 * already has, which exercises the populated path for free. The empty-bookshelf case is left to
 * {@code LocalBookshelfTest}, which owns it at the storage level without touching the network.
 *
 * <p>Assertions are structural, matching the other lifecycle tests: the host survives, the
 * Fragment is attached, and nothing finishes itself. What the bookshelf renders depends on the
 * owner's library and on network reachability, neither of which a test can pin down.
 */
@LargeTest
public class FavFragmentHostingTest {

    @Before
    public void requireAnInteractiveDevice() {
        InteractiveDevice.require();
        RealApi.require();

        // The bookshelf reads cached novel metadata from the save folder, so it needs the same
        // grants MainActivityLifecycleTest asks for on the API levels that still gate them.
        InteractiveDevice.grantIfPossible(Manifest.permission.READ_EXTERNAL_STORAGE);
        InteractiveDevice.grantIfPossible(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    /**
     * Swaps the bookshelf in and lets its startup work run.
     *
     * <p>{@code changeFragment} has to happen on the main thread, and the Fragment's
     * {@code onResume} starts an {@code AsyncTask} immediately, so the caller has to wait for the
     * main thread to go idle before asserting — otherwise this races the very work it is meant to
     * be covering.
     */
    private static FavFragment showBookshelf(ActivityScenario<MainActivity> scenario) {
        scenario.onActivity(activity -> activity.changeFragment(FavFragment.newInstance()));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        final Fragment[] holder = new Fragment[1];
        scenario.onActivity(activity ->
                holder[0] = activity.getSupportFragmentManager().findFragmentByTag("fragment"));
        return (FavFragment) holder[0];
    }

    private static void assertStillUsable(ActivityScenario<MainActivity> scenario, String when) {
        assertEquals(Lifecycle.State.RESUMED, scenario.getState());
        scenario.onActivity(activity ->
                assertFalse("the launcher finished itself " + when, activity.isFinishing()));
    }

    /**
     * Opening the bookshelf at all.
     *
     * <p>Most of the value is in getting here: the Fragment inflates its list, reads the local
     * bookshelf, and starts its first cloud refresh during this call, and none of that had ever
     * been executed by a test.
     */
    @Test
    public void theBookshelfOpensInsideTheLauncher() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            final FavFragment bookshelf = showBookshelf(scenario);

            assertNotNull("the bookshelf was never attached to the launcher", bookshelf);
            assertTrue("the bookshelf is not attached", bookshelf.isAdded());
            assertStillUsable(scenario, "on opening the bookshelf");
        }
    }

    /**
     * Backgrounded and brought back, which is a different code path rather than a repeat.
     *
     * <p>{@code onResume} calls {@code refreshList(timecount++)}: the first pass takes the cloud
     * branch, every later one takes {@code loadAllLocal}. So this is the only way to reach the
     * local-load branch, and it is also the branch that runs when a user returns to the app.
     * {@code onPause} runs too, which is what balances {@code EnterBookshelf}/{@code
     * LeaveBookshelf}.
     */
    @Test
    public void theBookshelfTakesItsLocalPathOnASecondResume() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            showBookshelf(scenario);

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertStillUsable(scenario, "after returning to the bookshelf");
        }
    }

    /**
     * Destroyed and rebuilt underneath the Fragment.
     *
     * <p>This is where a Fragment that holds its host, its adapter or its list statically shows
     * it — and the bookshelf holds a list it clears and refills on every load.
     */
    @Test
    public void theBookshelfSurvivesRecreationOfItsHost() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            showBookshelf(scenario);

            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertStillUsable(scenario, "during recreation with the bookshelf shown");
        }
    }

    /** Twice, for the reason the other recreation tests give: one pass can survive and two not. */
    @Test
    public void theBookshelfSurvivesRepeatedRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            showBookshelf(scenario);

            scenario.recreate();
            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertStillUsable(scenario, "during repeated recreation");
        }
    }

    /**
     * The bookshelf is shown, then replaced while its background work may still be running.
     *
     * <p>This is the shape of root cause 1: an {@code AsyncTask} started in {@code onResume}
     * delivering into a Fragment that has since been detached. Swapping straight to another
     * Fragment is how a user reaches it — a tap on the drawer while the bookshelf is still
     * loading.
     */
    @Test
    public void theBookshelfCanBeReplacedWhileItIsStillLoading() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            // Deliberately no idle wait here: replacing it immediately is the point.
            scenario.onActivity(activity -> activity.changeFragment(FavFragment.newInstance()));
            scenario.onActivity(activity -> activity.changeFragment(FavFragment.newInstance()));
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertStillUsable(scenario, "after the bookshelf was replaced mid-load");
        }
    }
}
