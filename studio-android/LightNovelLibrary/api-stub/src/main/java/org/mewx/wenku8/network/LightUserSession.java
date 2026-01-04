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

    public static boolean getLogStatus() {
        throw new UnsupportedOperationException("stub");
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

    public static boolean isUserInfoSet() {
        throw new UnsupportedOperationException("stub");
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


    public static class AsyncInitUserInfo extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        public AsyncInitUserInfo(Context context, Runnable failureCallback, Runnable loadUserInfoSet) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            throw new UnsupportedOperationException("stub");
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode e) {
            throw new UnsupportedOperationException("stub");
        }
    }
}
