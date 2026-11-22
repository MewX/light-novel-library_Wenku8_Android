package org.mewx.wenku8.network;

import android.content.Context;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import org.mewx.wenku8.api.Wenku8Error;


@SuppressWarnings("unused")
public class LightUserSession {
    public static AsyncInitUserInfo aiui;

    @NonNull
    public static String getLoggedAs() {
        throw new UnsupportedOperationException("stub");
    }

    public static String getUsernameOrEmail() {
        throw new UnsupportedOperationException("stub");
    }

    public static String getPassword() {
        throw new UnsupportedOperationException("stub");
    }

    public static String getSession() {
        throw new UnsupportedOperationException("stub");
    }

    public static void setSession(String s) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * Reports "not logged in" rather than throwing. A stub holds no session, so false is the
     * truthful answer and not a convenient pretence.
     *
     * <p>This one is load-bearing for every instrumented test in the project, not just the ones
     * that care about accounts: {@code BaseMaterialActivity.onResume} samples it on every screen
     * entry, and it is the base class of every Activity in the app. Throwing here meant no
     * Activity could reach RESUMED on a stub build, which is what CI runs.
     */
    public static boolean getLogStatus() {
        return false;
    }

    public static Wenku8Error.ErrorCode doLoginFromFile(Runnable loadUserInfoSet) {
        throw new UnsupportedOperationException("stub");
    }

    public static Wenku8Error.ErrorCode doLoginFromGiven(String name, String pwd, Runnable saveUserInfoSet) {
        throw new UnsupportedOperationException("stub");
    }

    public static void logOut(Runnable fileDeletionCallback) {
        throw new UnsupportedOperationException("stub");
    }

    public static Wenku8Error.ErrorCode heartbeatLogin(Runnable loadUserInfoSet) {
        throw new UnsupportedOperationException("stub");
    }

    /**
     * False for the same reason as {@link #getLogStatus()}: a stub has no stored credentials, so
     * "no user info" is the honest answer. Everything below still throws — logging in, logging
     * out and the user-file crypto have no truthful inert result, and a test that reaches them on
     * a stub build should fail loudly rather than quietly succeed against a made-up one.
     */
    public static boolean isUserInfoSet() {
        return false;
    }

    public static void setUserInfo(String username, String password) {
        throw new UnsupportedOperationException("stub");
    }

    public static void decAndSetUserFile(String raw) {
        throw new UnsupportedOperationException("stub");
    }

    public static String encUserFile() {
        throw new UnsupportedOperationException("stub");
    }

    public static boolean isInteger(@NonNull String value) {
        throw new UnsupportedOperationException("stub");
    }


    /**
     * Does nothing, successfully. MainActivity constructs and executes this during {@code onCreate}
     * and NavigationDrawerFragment later calls {@code aiui.getStatus()} on it, so on a stub build
     * it has to be a real, runnable AsyncTask that simply has no session to restore — a throwing
     * constructor made the launcher Activity unstartable, and a null {@code aiui} would move the
     * crash into the drawer instead.
     *
     * <p><b>Neither callback is invoked, and that is the important part.</b> {@code loadUserInfoSet}
     * would announce a logged-in user this stub does not have. {@code failureCallback} is worse:
     * at both call sites it <b>deletes the saved account and avatar files</b> and tells the user
     * their login expired. That is the right response to credentials the server rejected, and the
     * wrong response to a build with no server — reporting failure here would destroy real saved
     * data every time the suite ran. Doing nothing leaves the app in the not-logged-in state that
     * {@link #getLogStatus()} already reports, which is consistent and true.
     */
    public static class AsyncInitUserInfo extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        public AsyncInitUserInfo(Context context, Runnable failureCallback, Runnable loadUserInfoSet) {
            // Deliberately keeps no reference to either callback, so neither can fire by accident.
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            return Wenku8Error.ErrorCode.USER_INFO_EMPTY;
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode e) {
            // No callback, by design. See the class comment.
        }
    }
}
