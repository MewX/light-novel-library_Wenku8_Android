package org.mewx.wenku8.activity;

import android.app.Activity;
import android.content.Intent;
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

        Typeface typeface = Typeface.createFromAsset(getAssets(), "fonts/fzss-gbk.ttf");
//        ((TextView) findViewById(R.id.mewx)).setTypeface(typeface);
//        ((TextView) findViewById(R.id.zero)).setTypeface(typeface);
        ((TextView) findViewById(R.id.architect)).setTypeface(typeface);
        ((TextView) findViewById(R.id.ui_designer)).setTypeface(typeface);

        // execute background action
        LightUserSession.aiui = new LightUserSession.AsyncInitUserInfo();
        LightUserSession.aiui.execute();

        /* This is a delay template */
        new CountDownTimer(2000, 100) {
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
}
