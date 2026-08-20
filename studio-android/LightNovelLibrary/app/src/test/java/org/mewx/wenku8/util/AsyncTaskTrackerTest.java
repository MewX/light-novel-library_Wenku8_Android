package org.mewx.wenku8.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.os.AsyncTask;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Robolectric is required: AsyncTask needs a real Looper, which the plain JVM runtime does not
 * provide (and {@code unitTests.returnDefaultValues} would quietly hand back a null one).
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AsyncTaskTrackerTest {

    /** Never executed -- the tracker only reads getStatus() and calls cancel(). */
    private static class NoopTask extends AsyncTask<Void, Void, Void> {
        @Override
        protected Void doInBackground(Void... voids) {
            return null;
        }
    }

    @Test
    public void cancelAllCancelsUnfinishedTasks() {
        AsyncTaskTracker tracker = new AsyncTaskTracker();
        NoopTask task = tracker.track(new NoopTask());

        tracker.cancelAll();

        assertTrue(task.isCancelled());
    }

    @Test
    public void cancelAllForgetsEverything() {
        AsyncTaskTracker tracker = new AsyncTaskTracker();
        tracker.track(new NoopTask());
        tracker.track(new NoopTask());

        tracker.cancelAll();

        assertEquals(0, tracker.size());
    }

    @Test
    public void cancelAllIsIdempotent() {
        AsyncTaskTracker tracker = new AsyncTaskTracker();
        tracker.track(new NoopTask());

        tracker.cancelAll();
        tracker.cancelAll(); // must not throw

        assertEquals(0, tracker.size());
    }

    @Test
    public void cancelAllOnAnEmptyTrackerIsSafe() {
        // onDestroy runs on screens that never started a task.
        new AsyncTaskTracker().cancelAll();
    }

    @Test
    public void trackReturnsTheSameInstance() {
        AsyncTaskTracker tracker = new AsyncTaskTracker();
        NoopTask task = new NoopTask();

        assertEquals(task, tracker.track(task));
    }

    @Test
    public void trackedTasksAccumulateWhileUnfinished() {
        // The pruning in track() only drops FINISHED tasks; anything still pending or running
        // has to stay, or cancelAll would miss it.
        AsyncTaskTracker tracker = new AsyncTaskTracker();
        for (int i = 0; i < 5; i++) {
            tracker.track(new NoopTask());
        }

        assertEquals(5, tracker.size());
    }

    // Deliberately not covered here: that cancelAll() skips a task already in state FINISHED,
    // and that track() prunes finished tasks. Both need a task actually driven to completion,
    // which under Robolectric depends on looper-mode scheduling rather than on this class.
    // The branch is a plain getStatus() comparison in AsyncTaskTracker.
}
