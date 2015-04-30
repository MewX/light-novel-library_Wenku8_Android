package org.mewx.wenku8;

import android.app.Application;
import android.content.Context;

public class MyApp extends Application {
    private static Context context;

    @Override
    public void onCreate() {
        //获取Context
        context = getApplicationContext();

        return;
    }

    public static Context getContext(){
        return context;
    }
}
