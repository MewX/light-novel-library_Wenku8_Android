package org.mewx.wenku8;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.KeyguardManager;
import android.app.UiAutomation;
import android.content.Context;
import android.os.PowerManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.platform.app.InstrumentationRegistry;

/**
 * Preconditions every {@code ActivityScenario} test in this project depends on, in one place.
 *
 * <p>Extracted rather than copied because the cost of getting it wrong is paid in confusion, not
 * in a red test. A dozing device parks activities at STOPPED, so {@code RESUMED} is unreachable no
 * matter what the app does, and the failure surfaces as {@code Activity never becomes requested
 * state "[RESUMED]"} after a <b>45-second</b> timeout — which reads exactly like the Activity
 * failing to start. That cost real debugging time once already; every lifecycle test should fail
 * in milliseconds with a message that names the actual problem instead.
 */
public final class InteractiveDevice {

    private InteractiveDevice() {
    }

    /** Fails immediately, and legibly, if the screen is off or locked. */
    public static void require() {
        final Context context = ApplicationProvider.getApplicationContext();
        final PowerManager power = context.getSystemService(PowerManager.class);
        final KeyguardManager keyguard = context.getSystemService(KeyguardManager.class);

        assertTrue("the device screen is off -- these tests need it awake "
                + "(adb shell input keyevent KEYCODE_WAKEUP)", power.isInteractive());
        assertFalse("the device is locked -- these tests need it unlocked; a secure lock cannot "
                        + "be dismissed with `adb shell wm dismiss-keyguard`, so unlock it by hand",
                keyguard.isKeyguardLocked());
    }

    /**
     * Grants a runtime permission if the platform allows it, and shrugs if it does not.
     *
     * <p>Best-effort on purpose. A permission dialog steals focus and stops the Activity under
     * test from reaching RESUMED, so pre-granting removes a real source of flakiness — but the
     * permissions involved here are not grantable on every API level the suite runs on.
     * {@code WRITE_EXTERNAL_STORAGE} in particular is inert from API 33 and {@code pm grant}
     * rejects it outright, while the app only asks for {@code READ_EXTERNAL_STORAGE} below 33. So
     * a failure to grant is expected on exactly the configuration CI uses, and must not be an
     * error: where the grant is refused the request is either skipped by the app or auto-denied by
     * the platform without UI.
     */
    public static void grantIfPossible(String permission) {
        final UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        try {
            automation.grantRuntimePermission(
                    ApplicationProvider.getApplicationContext().getPackageName(), permission);
        } catch (Throwable expectedOnSomeApiLevels) {
            // Deliberately ignored -- see above.
        }
    }
}
