package org.mewx.wenku8.network;

import android.content.ContentValues;

import androidx.annotation.Nullable;

@SuppressWarnings("unused")
public class LightNetwork {

    public static String encodeToHttp(String str) {
        throw new UnsupportedOperationException("stub");
    }

    public static String encodeToHttp(String str, String encoding) {
        throw new UnsupportedOperationException("stub");
    }

    // The two request methods below return null rather than throwing. This is not a pretend
    // result: null is what the real implementation already returns for a request that failed,
    // which is exactly true of a stub with no server behind it. Callers are written to handle it
    // -- ChapterContentLoader maps null onto NETWORK_UNAVAILABLE -- so a build against this stub
    // exercises the app's offline behaviour instead of dying inside an AsyncTask.
    //
    // Deliberately not applied to every method in this file. Where a stubbed call has no truthful
    // failure value, it still throws, so that a code path nobody has thought about stays loud
    // rather than quietly succeeding against a made-up answer.

    @Nullable
    public static byte[] LightHttpPostConnection(String u, ContentValues values) {
        return null;
    }

    @Nullable
    public static byte[] LightHttpDownload(String url) {
        return null;
    }

}
