package org.mewx.wenku8.util;

import android.os.AsyncTask;

import java.util.ArrayList;
import java.util.List;

/**
 * Keeps hold of the AsyncTasks a screen has started so they can be cancelled when it goes away.
 *
 * <p>Only 4 of the app's 24 tasks were ever cancelled, and those only from a dialog's cancel
 * listener. Everything else ran to completion and delivered {@code onPostExecute} into an
 * Activity or Fragment that might already be gone. The lifecycle guards in those callbacks stop
 * that being a crash; cancelling stops the callback happening at all, which also releases the
 * task's implicit reference to its host.
 *
 * <p><b>Cancellation is deliberately non-interrupting.</b> {@link AsyncTask#cancel(boolean)} is
 * called with {@code false}, so a task already inside {@code doInBackground} runs to the end of
 * its work -- a download finishes writing its file, a cache write is not torn in half -- while
 * AsyncTask still routes the result to {@code onCancelled} instead of {@code onPostExecute}.
 * That is the whole point: the UI callback is what is unsafe, not the background work. Passing
 * {@code true} would interrupt mid-write and trade a crash for a corrupt cache file.
 *
 * <p>Not thread-safe: track and cancel from the main thread, which is where the lifecycle
 * callbacks run.
 */
public class AsyncTaskTracker {

    private final List<AsyncTask<?, ?, ?>> tasks = new ArrayList<>();

    /**
     * Record a task so it can be cancelled later.
     *
     * @param task the task, returned unchanged so this can wrap the construction
     * @return the same task
     */
    public <T extends AsyncTask<?, ?, ?>> T track(T task) {
        // Drop the tasks that have already finished, so a long-lived screen that starts a task
        // per scroll page does not accumulate them.
        for (int i = tasks.size() - 1; i >= 0; i--) {
            if (tasks.get(i).getStatus() == AsyncTask.Status.FINISHED) {
                tasks.remove(i);
            }
        }
        tasks.add(task);
        return task;
    }

    /**
     * Cancel every tracked task that has not finished, and forget all of them.
     * Safe to call more than once.
     */
    public void cancelAll() {
        for (AsyncTask<?, ?, ?> task : tasks) {
            if (task.getStatus() != AsyncTask.Status.FINISHED) {
                task.cancel(false); // see the class comment: deliberately not interrupting
            }
        }
        tasks.clear();
    }

    /** Number of tasks currently tracked. Exposed for tests. */
    public int size() {
        return tasks.size();
    }
}
