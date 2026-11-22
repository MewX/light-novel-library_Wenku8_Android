package org.mewx.wenku8.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;
import org.mewx.wenku8.R;

/**
 * The login screen, which is the one account screen that can be tested honestly without a server.
 *
 * <p>Nothing in {@code onCreate} talks to the network or to {@code api/} — it inflates a form and
 * attaches two listeners — so unlike {@link UserInfoActivityLifecycleTest} this screen behaves
 * identically on a stub build and a real one, and the assertions below can be exact rather than
 * hedged.
 *
 * <p><b>No test here submits credentials, deliberately.</b> A successful path would have to reach
 * the real wenku8 server, and an automated suite firing login attempts at someone else's
 * production service is not an acceptable cost for the coverage. Only the empty-form case taps
 * login, and it returns before {@code AsyncLoginTask} is ever constructed, so there is no request
 * for it to make.
 *
 * <p><b>The over-long case cannot be written at this level, and finding that out cost a real
 * request.</b> {@code UserLoginActivity} guards on {@code length() > 30}, but both fields carry
 * {@code android:maxLength="30"}, so an {@code InputFilter} truncates before the listener ever
 * runs. A test that set 31 characters got 30, sailed through the guard and submitted — the length
 * branch is unreachable through the view layer, and what actually protects it is the filter. So
 * {@link #theFieldsCapInputBeforeTheLengthGuardIsReached} pins the filter instead, which is the
 * thing whose removal would let over-long input reach the network.
 */
@LargeTest
public class UserLoginActivityLifecycleTest {

    /** One character past the fields' {@code maxLength}, which is what makes them truncate. */
    private static final String THIRTY_ONE_CHARACTERS = "0123456789012345678901234567890";

    @Before
    public void requireAnInteractiveDevice() {
        InteractiveDevice.require();
    }

    @Test
    public void theLoginScreenOpens() {
        try (ActivityScenario<UserLoginActivity> scenario =
                     ActivityScenario.launch(UserLoginActivity.class)) {
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> {
                assertFalse("the login screen closed itself on startup", activity.isFinishing());
                assertNotNull("the username field is missing",
                        activity.findViewById(R.id.edit_username_or_email));
                assertNotNull("the password field is missing",
                        activity.findViewById(R.id.edit_password));
            });
        }
    }

    /**
     * Rotation with half-filled fields, which is the realistic way this screen gets rebuilt: a
     * password manager or keyboard change takes focus and the configuration changes underneath it.
     */
    @Test
    public void theLoginScreenSurvivesRecreation() {
        try (ActivityScenario<UserLoginActivity> scenario =
                     ActivityScenario.launch(UserLoginActivity.class)) {
            scenario.onActivity(activity -> ((EditText) activity.findViewById(
                    R.id.edit_username_or_email)).setText("someone"));

            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> {
                assertFalse("the login screen finished while being recreated",
                        activity.isFinishing());
                assertNotNull("the login screen lost its form after recreation",
                        activity.findViewById(R.id.edit_username_or_email));
            });
        }
    }

    /** Twice, for the reason the other lifecycle tests do it: half-restored state survives one pass. */
    @Test
    public void theLoginScreenSurvivesRepeatedRecreation() {
        try (ActivityScenario<UserLoginActivity> scenario =
                     ActivityScenario.launch(UserLoginActivity.class)) {
            scenario.recreate();
            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    /** Backgrounded and returned to — {@code onResume} without {@code onCreate}. */
    @Test
    public void theLoginScreenSurvivesBeingBackgrounded() {
        try (ActivityScenario<UserLoginActivity> scenario =
                     ActivityScenario.launch(UserLoginActivity.class)) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    /**
     * Submitting an empty form. The screen must reject it locally and stay open — if the guard ever
     * stopped covering this, the tap would build a request out of two empty strings and send it.
     *
     * <p>{@code performClick} rather than Espresso: this runs the same listener, and the project
     * has no Espresso dependency yet. It also keeps the test off the view hierarchy's rendering,
     * which is not what is being checked here.
     *
     * <p>The assertion is about the Activity rather than about the absence of a request, because
     * nothing observable from here separates "no request was made" from "a request was made and
     * has not answered yet". A broken guard is still caught, just not by this assertion: on a stub
     * build {@code doLoginFromGiven} throws inside {@code AsyncTask.doInBackground} where nothing
     * catches it, and the run fails as a crash — attributed to whichever test is running 500ms
     * later, since {@code AsyncLoginTask} sleeps before it calls anything.
     */
    @Test
    public void anEmptyFormIsRejectedWithoutBeingSubmitted() {
        try (ActivityScenario<UserLoginActivity> scenario =
                     ActivityScenario.launch(UserLoginActivity.class)) {
            scenario.onActivity(activity -> {
                ((EditText) activity.findViewById(R.id.edit_username_or_email)).setText("");
                ((EditText) activity.findViewById(R.id.edit_password)).setText("");
                ((TextView) activity.findViewById(R.id.btn_login)).performClick();
            });

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(
                    "the login screen closed itself on a form it should have rejected",
                    activity.isFinishing()));
        }
    }

    /**
     * The fields refuse to hold more than they should, which is what keeps over-long input away
     * from the network — the Activity's own {@code length() > 30} check never sees a value that
     * long. Asserting the cap directly is the only honest way to cover it: a test cannot hand the
     * listener an over-long string, because the same filter truncates whatever a test sets too.
     *
     * <p>Notably this does <b>not</b> tap login. Thirty characters is valid input as far as the
     * screen is concerned, so a tap here would submit it for real.
     */
    @Test
    public void theFieldsCapInputBeforeTheLengthGuardIsReached() {
        try (ActivityScenario<UserLoginActivity> scenario =
                     ActivityScenario.launch(UserLoginActivity.class)) {
            scenario.onActivity(activity -> {
                final EditText username = activity.findViewById(R.id.edit_username_or_email);
                final EditText password = activity.findViewById(R.id.edit_password);
                username.setText(THIRTY_ONE_CHARACTERS);
                password.setText(THIRTY_ONE_CHARACTERS);

                assertEquals("the username field stopped capping its input",
                        30, username.getText().toString().length());
                assertEquals("the password field stopped capping its input",
                        30, password.getText().toString().length());
            });
        }
    }
}
