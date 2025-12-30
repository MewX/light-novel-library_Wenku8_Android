package org.mewx.wenku8;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import com.google.android.gms.ads.MobileAds;

/**
 * The class is for getting context everywhere
 */
public class MyApp extends Application {
    @SuppressLint("StaticFieldLeak")
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContextLocal();

        MobileAds.initialize(this, initializationStatus -> {});
    }

    /**
     * wrap the getApplicationContext() function for easier unit testing
     * @return the results from getApplicationContext()
     */
    Context getApplicationContextLocal() {
        return getApplicationContext();
    }

    public static Context getContext(){
        return context;
    }
}
