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
 * The three review screens, none of which had ever been executed by a test.
 *
 * <p>Together they were 359 uncovered lines — the largest zero-coverage cluster left after the
 * bookshelf — and they form one feature: the list of reviews on a novel, the replies to one
 * review, and the form for posting a new one.
 *
 * <p>Every test here runs against a review thread that does not exist, with no cached copy and no
 * reachable server. That is not an edge case for these screens: reviews are never cached, so being
 * offline or having the novel pulled from the site produces exactly this state, and the contract
 * is that the screen reports the failure rather than closing or crashing.
 *
 * <p>Assertions stay structural for the reason {@link NovelInfoActivityLifecycleTest} sets out —
 * the failure text is untranslated and inconsistent today, and pinning its current appearance down
 * would make fixing it harder rather than safer.
 *
 * <p>Sentinel ids keep this clear of any real novel or review. Nothing is written: these screens
 * only read, and the load fails before there is anything to cache. The posting form is opened but
 * never submitted, so nothing reaches anyone's account.
 */
@LargeTest
public class NovelReviewScreensTest {

    /** Same sentinel range the reader and storage tests use. */
    private static final int MISSING_AID = 999_000_006;
    private static final int MISSING_RID = 999_000_301;

    @Before
    public void requireAnInteractiveDevice() {
        InteractiveDevice.require();
    }

    private static Intent intentFor(Class<?> screen) {
        final Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = new Intent(context, screen);
        intent.putExtra("aid", MISSING_AID);
        intent.putExtra("rid", MISSING_RID);
        intent.putExtra("title", "sentinel review thread that does not exist");
        return intent;
    }

    private static void assertStaysOpen(ActivityScenario<?> scenario, String what) {
        assertEquals(Lifecycle.State.RESUMED, scenario.getState());
        scenario.onActivity(activity -> assertFalse(
                what + " closed itself instead of reporting the failure",
                activity.isFinishing()));
    }

    // ---- the list of reviews on a novel ----------------------------------------------------

    @Test
    public void theReviewListOpensWithNothingToShow() {
        try (ActivityScenario<NovelReviewListActivity> scenario =
                     ActivityScenario.launch(intentFor(NovelReviewListActivity.class))) {
            assertStaysOpen(scenario, "the review list");
        }
    }

    @Test
    public void theReviewListSurvivesRecreation() {
        try (ActivityScenario<NovelReviewListActivity> scenario =
                     ActivityScenario.launch(intentFor(NovelReviewListActivity.class))) {
            scenario.recreate();
            assertStaysOpen(scenario, "the review list");
        }
    }

    /**
     * Backgrounded while its first fetch is still in flight.
     *
     * <p>This is the root-cause-1 shape for this screen: the fetch is started from startup and
     * delivers into the Activity whenever it finishes, which may be after the user has left.
     */
    @Test
    public void theReviewListSurvivesBeingBackgroundedMidFetch() {
        try (ActivityScenario<NovelReviewListActivity> scenario =
                     ActivityScenario.launch(intentFor(NovelReviewListActivity.class))) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            assertStaysOpen(scenario, "the review list");
        }
    }

    // ---- the replies to one review ---------------------------------------------------------

    @Test
    public void theReplyListOpensWithNothingToShow() {
        try (ActivityScenario<NovelReviewReplyListActivity> scenario =
                     ActivityScenario.launch(intentFor(NovelReviewReplyListActivity.class))) {
            assertStaysOpen(scenario, "the reply list");
        }
    }

    @Test
    public void theReplyListSurvivesRecreation() {
        try (ActivityScenario<NovelReviewReplyListActivity> scenario =
                     ActivityScenario.launch(intentFor(NovelReviewReplyListActivity.class))) {
            scenario.recreate();
            assertStaysOpen(scenario, "the reply list");
        }
    }

    @Test
    public void theReplyListSurvivesBeingBackgroundedMidFetch() {
        try (ActivityScenario<NovelReviewReplyListActivity> scenario =
                     ActivityScenario.launch(intentFor(NovelReviewReplyListActivity.class))) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);
            assertStaysOpen(scenario, "the reply list");
        }
    }

    // ---- the new-post form -----------------------------------------------------------------

    /**
     * The posting form, opened but never submitted.
     *
     * <p>Unlike the two lists this screen fetches nothing on startup, so it should reach RESUMED
     * whatever the network is doing. Submitting is deliberately out of scope: it would post to a
     * real account against a real novel.
     */
    @Test
    public void theNewPostFormOpens() {
        try (ActivityScenario<NovelReviewNewPostActivity> scenario =
                     ActivityScenario.launch(intentFor(NovelReviewNewPostActivity.class))) {
            assertStaysOpen(scenario, "the new-post form");
        }
    }

    /**
     * Recreated with whatever the user had typed.
     *
     * <p>Worth covering rather than assuming: a rotation losing a half-written review is the kind
     * of loss users notice, and this screen holds its text in views rather than in saved state.
     */
    @Test
    public void theNewPostFormSurvivesRecreation() {
        try (ActivityScenario<NovelReviewNewPostActivity> scenario =
                     ActivityScenario.launch(intentFor(NovelReviewNewPostActivity.class))) {
            scenario.recreate();
            assertStaysOpen(scenario, "the new-post form");
        }
    }
}
