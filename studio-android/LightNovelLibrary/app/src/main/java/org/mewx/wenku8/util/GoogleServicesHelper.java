package org.mewx.wenku8.util;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.firebase.analytics.FirebaseAnalytics;

public class GoogleServicesHelper {
    private static final String TAG = GoogleServicesHelper.class.getSimpleName();
    private static Boolean isGmsAvailable = null;

    /** Check if GMS environment is actually available. */
    public static boolean isGmsAvailable(Context context) {
        if (isGmsAvailable == null) {
            int resultCode = GoogleApiAvailability.getInstance()
                    .isGooglePlayServicesAvailable(context.getApplicationContext());
            isGmsAvailable = (resultCode == ConnectionResult.SUCCESS);
            Log.d(TAG, "GMS Availability: " + isGmsAvailable + " (Code: " + resultCode + ")");
        }
        return isGmsAvailable;
    }

    /** Safely initialize AdMob */
    public static void initAdMob(Context context) {
        if (isGmsAvailable(context)) {
            new Thread(() -> {
                try {
                    MobileAds.initialize(context, status -> Log.d(TAG, "AdMob Initialized"));
                } catch (Exception e) {
                    Log.e(TAG, "AdMob init failed even with GMS", e);
                }
            }).start();
        } else {
            Log.w(TAG, "Skipping AdMob init: GMS not available");
        }
    }

    /** Safely initialize Firebase Analytics for the activity entry default logging. */
    public static FirebaseAnalytics initFirebase(Context context) {
        if (isGmsAvailable(context)) {
            try {
                return FirebaseAnalytics.getInstance(context.getApplicationContext());
            } catch (Exception e) {
                Log.e(TAG, "Firebase init failed", e);
            }
        }
        return null;
    }

    /** Safely log event to Firebase Analytics */
    public static void logEvent(FirebaseAnalytics mFirebaseAnalytics, String eventName, Bundle params) {
        if (mFirebaseAnalytics != null) {
            mFirebaseAnalytics.logEvent(eventName, params);
        } else {
            // If GMS is not available, just drop the log here
            Log.v(TAG, "Firebase event dropped: " + eventName);
        }
    }
}
