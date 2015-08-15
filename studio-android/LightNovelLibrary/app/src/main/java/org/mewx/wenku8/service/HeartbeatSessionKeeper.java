package org.mewx.wenku8.service;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.widget.Toast;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.util.LightUserSession;

import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by MewX on 2015/5/17.
 * Heartbeat Session Keeper. Useless.
 */
public class HeartbeatSessionKeeper extends Service {
    private int interval = 60 * 10 * 1000;

    /**
     * Set interval, must cancel it, then set & start.
     * @param i time count
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
        Timer timer = new Timer();
        timer.schedule(new TimerTask() {
            @Override
            public void run() {
                // frequently request
                Wenku8Error.ErrorCode err = LightUserSession.heartbeatLogin();
                Toast.makeText(MyApp.getContext(), err.toString(), Toast.LENGTH_SHORT).show();

            }
        }, 0, interval);


        return super.onStartCommand(intent, flags, startId);
    }
}
