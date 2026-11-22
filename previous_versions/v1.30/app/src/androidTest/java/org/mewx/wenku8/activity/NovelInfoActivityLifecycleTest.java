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
 * The novel detail screen when there is nothing to show it.
 *
 * <p>Every test here opens a novel that does not exist, with no cached copy and no reachable
 * server, which is the state this screen has to handle without closing or crashing: a stale
 * bookshelf entry, a novel pulled from the site, or simply being offline. It never calls
 * {@code finish()}, so the correct outcome is that it stays open showing its failed state — these
 * assert that rather than that it loads anything.
 *
 * <p>Deliberately not asserting on what the screen displays. What "failed" looks like here is
 * untranslated and inconsistent (raw error-code names reach the user in places), and pinning the
 * current appearance down in a test would make fixing that harder rather than safer. The contract
 * worth holding is structural: the Activity survives, on every lifecycle path.
 *
 * <p>The aid is a sentinel far above any real one, so nothing this test does can collide with a
 * novel the device owner actually has. Nothing is cleaned up afterwards because nothing is
 * written: the load fails before there is anything to cache.
 */
@LargeTest
public class NovelInfoActivityLifecycleTest {

    /** Matches the sentinel range used by the reader and storage tests. */
    private static final int MISSING_AID = 999_000_004;

    @Before
    public void requireAnInteractiveDevice() {
        InteractiveDevice.require();
    }

    private static Intent infoIntent() {
        final Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = new Intent(context, NovelInfoActivity.class);
        intent.putExtra("aid", MISSING_AID);
        intent.putExtra("from", "fav");
        intent.putExtra("title", "sentinel novel that does not exist");
        return intent;
    }

    /**
     * Opening a novel that cannot be loaded. Launching at all is most of the assertion: the cover
     * URL is built during view creation, and until the stub stopped throwing there this screen
     * could not start on the configuration CI builds.
     */
    @Test
    public void theDetailScreenOpensWithNothingToShow() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(
                    "the detail screen closed itself instead of reporting the failure",
                    activity.isFinishing()));
        }
    }

    /** Destroyed and rebuilt while still holding nothing, which is when a half-set field shows. */
    @Test
    public void theDetailScreenSurvivesRecreation() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse("the detail screen finished during "
                    + "recreation", activity.isFinishing()));
        }
    }

    /**
     * Twice, for the same reason the launcher test does it: a rebuild that leaks or half-restores
     * state often survives one pass and fails the next.
     */
    @Test
    public void theDetailScreenSurvivesRepeatedRecreation() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            scenario.recreate();
            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    /**
     * Backgrounded and brought back — the common case, and a different path: {@code onResume} runs
     * again without {@code onCreate}, on a screen whose load already failed.
     */
    @Test
    public void theDetailScreenSurvivesBeingBackgrounded() {
        try (ActivityScenario<NovelInfoActivity> scenario = ActivityScenario.launch(infoIntent())) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }
}
