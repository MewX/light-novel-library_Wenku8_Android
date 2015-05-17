package org.mewx.wenku8.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by MewX on 2015/5/17.
 */
public class HeartbeatSessionKeeper extends Service {
    private int interval = 10 * 60 * 1000;
    private Timer timer;

    /**
     * Set interval, must cancel it, then set & start.
     * @param i
     */
    public void setInterval(int i) {
        if(i > 0)
            interval = i;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {

            }
        }, 0, interval);


        return super.onStartCommand(intent, flags, startId);
    }
}
