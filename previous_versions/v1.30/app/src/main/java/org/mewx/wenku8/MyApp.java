package org.mewx.wenku8;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.util.CrashReporter;
import org.mewx.wenku8.util.GoogleServicesHelper;

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

        // Crash report context. Only the values known this early are set here; the ones that
        // depend on loaded settings or on a screen are set where they become known.
        CrashReporter.setKey(CrashReporter.Keys.BUILD_FLAVOR, BuildConfig.FLAVOR);
        CrashReporter.log("MyApp#onCreate");

        // Init AdMob
        GoogleServicesHelper.initAdMob(this);
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
