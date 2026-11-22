package org.mewx.wenku8.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.Manifest;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;

/**
 * The launcher screen surviving destruction and rebuild.
 *
 * <p>This is the screen the system is most likely to destroy — it is what sits underneath every
 * other Activity, so it spends the most time in the background — and it does the most work in
 * {@code onCreate}: locale configuration, storage-permission requests, save-file migration, and
 * kicking off the session restore that {@code NavigationDrawerFragment} later inspects.
 *
 * <p>Until recently none of this could be tested on CI at all. {@code BaseMaterialActivity.onResume}
 * samples {@code LightUserSession.getLogStatus()}, and {@code MainActivity.onCreate} constructs
 * {@code LightUserSession.AsyncInitUserInfo} — both threw on a build against {@code api-stub},
 * which is what CI builds, so no Activity in this project could reach RESUMED there. These tests
 * are the reason those stubs now answer instead of throwing, and they are the check that it holds.
 *
 * <p><b>These tests exercise the real device.</b> Launching this Activity runs the same storage
 * setup an ordinary app launch runs, including writing {@code .nomedia} files into the save
 * folders. That is existing launch behaviour rather than anything the tests add, but it is worth
 * knowing before running them against a device carrying a real install.
 */
@LargeTest
public class MainActivityLifecycleTest {

    @Before
    public void requireAnInteractiveDevice() {
        InteractiveDevice.require();

        // A permission dialog would take focus and keep the Activity below RESUMED. Both are
        // best-effort: the app only asks for READ below API 33, and WRITE cannot be granted from
        // 33 onwards. See InteractiveDevice#grantIfPossible.
        InteractiveDevice.grantIfPossible(Manifest.permission.READ_EXTERNAL_STORAGE);
        InteractiveDevice.grantIfPossible(Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    /**
     * That the app opens at all, on the configuration CI builds. Worth stating as its own test:
     * this exact assertion was impossible to satisfy until the stub stopped throwing, and it is
     * the one that would catch that regression returning.
     */
    @Test
    public void theLauncherScreenOpens() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse("the launcher closed itself on startup",
                    activity.isFinishing()));
        }
    }

    /**
     * The case the plan is actually worried about: the system reclaims the launcher while the user
     * is deeper in the app, then rebuilds it on the way back.
     */
    @Test
    public void theLauncherScreenSurvivesRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> {
                assertFalse("the launcher finished while being recreated", activity.isFinishing());
                assertNotNull("the launcher lost its content view", activity.findViewById(
                        android.R.id.content));
            });
        }
    }

    /**
     * Twice, because this codebase keeps mutable global static state — {@code LightUserSession.aiui}
     * among it, which {@code onCreate} reassigns on every build and the navigation drawer reads
     * back. A rebuild that half-restores that kind of state tends to survive the first pass and
     * fail the second.
     */
    @Test
    public void theLauncherScreenSurvivesRepeatedRecreation() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.recreate();
            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    /**
     * Backgrounding and returning, which is far more common than a full rebuild and takes a
     * different path through the lifecycle — {@code onResume} runs again without {@code onCreate},
     * and that is where the session status is re-sampled.
     */
    @Test
    public void theLauncherScreenSurvivesBeingBackgrounded() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }
}
