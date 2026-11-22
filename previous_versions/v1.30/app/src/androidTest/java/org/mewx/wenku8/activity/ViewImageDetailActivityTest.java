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
 * The full-screen image viewer, reached by tapping an illustration in a reader.
 *
 * <p>117 lines, none of them previously executed. It is the one screen in this round whose
 * contract is the opposite of the others: it is given a path and has nothing to show without one,
 * so a missing extra makes it close rather than stay open reporting a failure.
 *
 * <p>That branch is the reason this file exists rather than the screen being folded into another
 * test. It is the same shape as the reader's missing-{@code volume} extra that Phase 1 dealt with,
 * and it is reachable in practice: this Activity is started from a reader with a path the reader
 * built, so anything that leaves that path unset arrives here.
 */
@LargeTest
public class ViewImageDetailActivityTest {

    /**
     * A path that is well-formed but refers to nothing.
     *
     * <p>The distinction that matters for this screen is between "no path at all", which closes
     * it, and "a path that does not resolve", which must not — a deleted or half-written cache
     * file has to leave the viewer open rather than closing under the user.
     */
    private static final String MISSING_IMAGE_PATH =
            "/sdcard/wenku8/saves/imgs/999000501-does-not-exist.jpg";

    @Before
    public void requireAnInteractiveDevice() {
        InteractiveDevice.require();
    }

    private static Intent viewerIntent(String path) {
        final Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = new Intent(context, ViewImageDetailActivity.class);
        if (path != null) {
            intent.putExtra("path", path);
        }
        return intent;
    }

    /**
     * Started with no path at all, which is the one case where closing is correct.
     *
     * <p>Asserted directly rather than left implicit: every use of the path below the check
     * dereferences it, so a change that removed the guard would turn this into a crash on a screen
     * the user reached by tapping a picture.
     */
    @Test
    public void theViewerClosesWhenItIsGivenNoPath() {
        try (ActivityScenario<ViewImageDetailActivity> scenario =
                     ActivityScenario.launch(viewerIntent(null))) {
            // Asserted through the scenario's state rather than through onActivity. An Activity
            // that finishes inside onCreate is already gone by the time launch() returns, and
            // onActivity then throws "Cannot run onActivity since Activity has been destroyed"
            // -- which reads like a broken test rather than like the screen doing its job.
            assertEquals("the viewer stayed open with nothing to display, which is the crash "
                            + "the null check exists to prevent",
                    Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    /**
     * Started with a path that does not resolve to a file.
     *
     * <p>The opposite expectation to the test above, and the more common one in practice: an
     * illustration that failed to download, or a cache file removed underneath the app.
     */
    @Test
    public void theViewerStaysOpenForAnImageThatIsMissing() {
        try (ActivityScenario<ViewImageDetailActivity> scenario =
                     ActivityScenario.launch(viewerIntent(MISSING_IMAGE_PATH))) {
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(
                    "the viewer closed itself over an image it could not load",
                    activity.isFinishing()));
        }
    }

    /** Rebuilt while holding a path it cannot load, which is when a half-set field shows. */
    @Test
    public void theViewerSurvivesRecreation() {
        try (ActivityScenario<ViewImageDetailActivity> scenario =
                     ActivityScenario.launch(viewerIntent(MISSING_IMAGE_PATH))) {
            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    /** Backgrounded and returned to, the path that runs onResume without onCreate. */
    @Test
    public void theViewerSurvivesBeingBackgrounded() {
        try (ActivityScenario<ViewImageDetailActivity> scenario =
                     ActivityScenario.launch(viewerIntent(MISSING_IMAGE_PATH))) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }
}
