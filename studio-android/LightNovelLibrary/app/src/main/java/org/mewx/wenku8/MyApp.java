package org.mewx.wenku8;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;

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
