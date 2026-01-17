package org.mewx.wenku8;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import com.google.android.gms.ads.MobileAds;

import org.mewx.wenku8.api.Wenku8API;

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

        // TODO: use a better dependency injection for this value.
        Wenku8API.AppVer = BuildConfig.VERSION_NAME;

        // Init AdMob
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
