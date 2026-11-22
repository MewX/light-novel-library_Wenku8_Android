package org.mewx.wenku8.activity;

import static org.junit.Assert.assertNotNull;

import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;

import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;

/**
 * The account screen starting up. One test, and the thin coverage is the honest amount available
 * until there is a seam in front of {@code LightNetwork} — the reasoning is worth recording so the
 * next person does not mistake it for an oversight.
 *
 * <p><b>Why not the usual four.</b> {@code onCreate} immediately fires {@code AsyncGetUserInfo},
 * and every failure ending in that task calls {@code finish()}. So on a stub build this screen is
 * on its way to closing from the moment it opens, and the lifecycle shapes the other tests use
 * cannot express that: {@code recreate()} and {@code moveToState(RESUMED)} both block until the
 * Activity reaches RESUMED, which one that has already finished never will, so they would not fail
 * — they would hang for the full 45-second timeout and then report something that reads like a
 * startup defect. A launch is the only move that stays meaningful in both outcomes.
 *
 * <p><b>Why the assertion is so weak.</b> Whether this screen stays open depends on whether a
 * session and a server exist, which differs between CI and a developer's device — so any assertion
 * about the final state would encode the environment rather than the contract. Launching is
 * nonetheless most of the value: startup is where the account screen has actually broken before,
 * and an exception on this path fails the run rather than passing quietly.
 *
 * <p><b>The logic this screen runs is tested elsewhere, and better.</b> The decision behind
 * {@code AsyncGetUserInfo} — sign in, fetch, re-login and retry once on a lapsed session, or report
 * one of four failures — now lives in {@code AccountInfoLoader} with its I/O injected, covered by
 * JVM tests that run in milliseconds and can produce server behaviour no real server would produce
 * on cue. Nothing is gained by trying to reproduce those cases through this Activity.
 *
 * <p><b>What this test will not do is log anybody out.</b> {@code AsyncLogout} deletes the stored
 * account and avatar files, and it is reachable only from a dialog behind a button tap. Nothing
 * here taps it. On a device with a real account, launching this screen performs the same session
 * restore an ordinary launch does — existing behaviour, but worth knowing before running the suite
 * against a device you are logged in on.
 */
@LargeTest
public class UserInfoActivityLifecycleTest {

    @Before
    public void requireAnInteractiveDevice() {
        InteractiveDevice.require();
    }

    /**
     * That the account screen starts without throwing. Deliberately does not assert a state:
     * {@code launch} returning at all means RESUMED was reached, and by the time it is read the
     * screen may legitimately have closed itself already.
     */
    @Test
    public void theAccountScreenOpensWithoutASession() {
        try (ActivityScenario<UserInfoActivity> scenario =
                     ActivityScenario.launch(UserInfoActivity.class)) {
            assertNotNull("the account screen did not reach a lifecycle state at all",
                    scenario.getState());
        }
    }
}
