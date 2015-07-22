package org.mewx.wenku8.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.TextView;

import org.mewx.wenku8.R;
import org.mewx.wenku8.util.LightUserSession;

/**
 * Created by MewX on 2015/6/13.
 */
public class WelcomeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_welcome);

        // get views
        Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/fzss-gbk.ttf");
        ((TextView) findViewById(R.id.architect)).setTypeface(typeface);
        ((TextView) findViewById(R.id.ui_designer)).setTypeface(typeface);
        findViewById(R.id.version_layout).setBackgroundColor(Color.argb(0x80, 0xFF, 0xFF, 0xFF));
        findViewById(R.id.copyright_layout).setBackgroundColor(Color.argb(0x80, 0xFF, 0xFF, 0xFF));

        // get version code
        PackageManager manager;
        PackageInfo info = null;
        manager = this.getPackageManager();
        try {
            info = manager.getPackageInfo(this.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        ((TextView) findViewById(R.id.version_code)).setText("Ver: " + info.versionName + " (" + info.versionCode + ")");

        // execute background action
        LightUserSession.aiui = new LightUserSession.AsyncInitUserInfo();
        LightUserSession.aiui.execute();

        /* This is a delay template */
        new CountDownTimer(1500, 100) {
            @Override
            public void onTick(long millisUntilFinished) {
                // Animation can be here.
            }

            @Override
            public void onFinish() {
                // time-up, and jump
                Intent intent = new Intent();
                intent.setClass(WelcomeActivity.this, MainActivity.class);
                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                finish(); // destroy itself
            }
        }.start();
    }

    @Override
    public void onBackPressed() {
        // hijack
    }
}
