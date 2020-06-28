package org.mewx.wenku8;

import android.annotation.SuppressLint;
import android.content.Context;

import androidx.multidex.MultiDexApplication;

/**
 * The class is for getting context everywhere
 */
public class MyApp extends MultiDexApplication {
    @SuppressLint("StaticFieldLeak")
    private static Context context;

    @Override
    public void onCreate() {
        super.onCreate();
        context = getApplicationContextLocal();
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
