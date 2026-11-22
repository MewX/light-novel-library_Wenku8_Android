package org.mewx.wenku8.fragment;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import android.Manifest;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;
import org.mewx.wenku8.activity.MainActivity;

/**
 * The settings Fragment, hosted the way the app hosts it.
 *
 * <p>{@code ConfigFragment} was 166 uncovered lines of 168. It is reached through {@link
 * MainActivity}'s drawer, so it is driven through {@code changeFragment} rather than through a
 * bare test container — the same approach {@code FavFragmentHostingTest} established.
 *
 * <p><b>Settings are read, never written.</b> The Fragment populates its controls from the stored
 * preferences; nothing here taps a control, so nothing changes. That is stated rather than assumed
 * because these run against the device owner's real settings, and because the bookshelf test
 * turned out to sync on startup — a hosted screen's own behaviour is inherited by the test.
 *
 * <p>{@code NovelItemListFragment} was attempted here too and abandoned. The block comment below
 * records what it found and why nothing was patched, since both crashes turned out to be the test
 * rather than the app.
 */
@LargeTest
public class ConfigFragmentHostingTest {

    @Before
    public void requireAnInteractiveDevice() {
        InteractiveDevice.require();
        InteractiveDevice.grantIfPossible(Manifest.permission.READ_EXTERNAL_STORAGE);
        InteractiveDevice.grantIfPossible(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    private static void show(ActivityScenario<MainActivity> scenario,
                             androidx.fragment.app.Fragment fragment) {
        scenario.onActivity(activity -> activity.changeFragment(fragment));
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
    }

    private static void assertStillUsable(ActivityScenario<MainActivity> scenario, String when) {
        assertEquals(Lifecycle.State.RESUMED, scenario.getState());
        scenario.onActivity(activity ->
                assertFalse("the launcher finished itself " + when, activity.isFinishing()));
    }

    /*
     * NovelItemListFragment is deliberately NOT tested here, after an attempt to.
     *
     * Two separate crashes came out of hosting it, and both were the test constructing states the
     * app does not produce rather than defects:
     *
     *   1. type=search inside MainActivity ->
     *          NullPointerException: LinearProgressIndicator.setVisibility on a null object
     *          at NovelItemListFragment.onCreateView(NovelItemListFragment.java:142)
     *      The search branch resolves its progress indicator with
     *      getActivity().findViewById(R.id.spb) -- the host Activity's view tree, not the rootView
     *      it just inflated -- and R.id.spb exists only in layout_search_result.xml and
     *      layout_novel_info.xml. SearchResultActivity is the sole caller passing type=search and
     *      its layout has the view, so this is unreachable in production. SearchScreensTest covers
     *      that listing through its real host.
     *
     *   2. type="" (and null arguments, which the Fragment itself defaults to "") ->
     *          IllegalStateException: Unknown NovelSortedBy:
     *          at Wenku8API.getNovelSortedBy(Wenku8API.java:135)
     *          at NovelItemListFragment$AsyncGetNovelItemList.doInBackground:315
     *
     * The second is the more interesting one and is written up in STABILITY_PLAN.md: the
     * Fragment's null-argument defence produces "" and its own background task then rejects "" by
     * throwing, so the defence converts a missing argument into a crash one layer down instead of
     * into a safe empty state. It is not reachable today -- the framework retains setArguments
     * across recreation and every caller passes a real value -- so it is recorded rather than
     * patched, per the standing preference for coverage over logical patches.
     *
     * Testing it properly needs a valid NovelSortedBy, which comes from the private api/ module.
     * CI builds against api-stub, so a test written that way risks passing here and failing there
     * -- the api-stub trap. That is why this is left rather than bodged.
     *
     * RESOLVED, and the reasoning above still holds -- it is why the fix took the shape it did.
     * NovelItemListFragmentHostingTest covers it with a real list type, hosted as a ranking list
     * rather than as type=search, and guarded by RealApi.require() so it skips on CI instead of
     * dying there. RealApi did not exist when the attempt recorded above was made.
     *
     * One thing that attempt did not reach, and which the new test documents: the type string has
     * to be derived via Wenku8API rather than written literally. The real module maps
     * NovelSortedBy.allVote to "allvote", while api-stub implements the same pair as
     * valueOf/name and so produces "allVote". A hardcoded "allvote" reaches valueOf on CI and
     * throws IllegalArgumentException from a background thread.
     */

    // ---- settings --------------------------------------------------------------------------

    /**
     * Opening settings, which reads every stored preference to populate its controls.
     *
     * <p>Nothing here taps a control, so no preference changes. That matters on a real device:
     * these tests run against the owner's actual settings.
     */
    @Test
    public void theSettingsScreenOpens() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            show(scenario, new ConfigFragment());
            assertStillUsable(scenario, "on opening settings");
        }
    }

    @Test
    public void theSettingsScreenSurvivesRecreationOfItsHost() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            show(scenario, new ConfigFragment());

            scenario.recreate();
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertStillUsable(scenario, "during recreation with settings shown");
        }
    }

    /** Backgrounded and returned to, which runs onResume without onCreate. */
    @Test
    public void theSettingsScreenSurvivesBeingBackgrounded() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            show(scenario, new ConfigFragment());

            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            InstrumentationRegistry.getInstrumentation().waitForIdleSync();

            assertStillUsable(scenario, "after returning to settings");
        }
    }
}
