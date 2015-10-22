package org.mewx.wenku8.activity;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.widget.ImageView;
import android.widget.TextView;

import com.readystatesoftware.systembartint.SystemBarTintManager;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightUserSession;

import java.io.File;
import java.util.Locale;

/**
 * Created by MewX on 2015/6/13.
 * Welcome Activity.
 */
public class WelcomeActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_welcome);

        // get views
        findViewById(R.id.version_layout).setBackgroundColor(Color.argb(0x80, 0xFF, 0xFF, 0xFF));
        findViewById(R.id.copyright_layout).setBackgroundColor(Color.argb(0x80, 0xFF, 0xFF, 0xFF));

        // load language
        Locale locale;
        switch (GlobalConfig.getCurrentLang()) {
            case SC:
                locale = Locale.SIMPLIFIED_CHINESE;
                ((ImageView) findViewById(R.id.startbg)).setImageDrawable(getResources().getDrawable(R.drawable.startbg_default));
                break;
            case TC:
                locale = Locale.TRADITIONAL_CHINESE;
                ((ImageView) findViewById(R.id.startbg)).setImageDrawable(getResources().getDrawable(R.drawable.startbg_default_tc));
                break;
            default:
                locale = Locale.SIMPLIFIED_CHINESE;
                break;
        }
        android.content.res.Configuration config = new android.content.res.Configuration();
        config.locale = locale;
        Locale.setDefault(locale);
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());



        // get version code
        PackageManager manager;
        PackageInfo info;
        manager = this.getPackageManager();
        try {
            info = manager.getPackageInfo(this.getPackageName(), 0);
            ((TextView) findViewById(R.id.version_code)).setText("Ver: " + info.versionName + " (" + info.versionCode + ")");
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // tint
        SystemBarTintManager tintManager = new SystemBarTintManager(this);
        tintManager.setTintAlpha(0.0f);

        // execute background action
        LightUserSession.aiui = new LightUserSession.AsyncInitUserInfo();
        LightUserSession.aiui.execute();
        GlobalConfig.loadAllSetting();

        // create save folder
        LightCache.saveFile(GlobalConfig.getFirstStoragePath() + "imgs", ".nomedia", "".getBytes(), false);
        LightCache.saveFile(GlobalConfig.getSecondStoragePath() + "imgs", ".nomedia", "".getBytes(), false);
        LightCache.saveFile(GlobalConfig.getFirstStoragePath() + GlobalConfig.customFolderName, ".nomedia", "".getBytes(), false);
        LightCache.saveFile(GlobalConfig.getSecondStoragePath() + GlobalConfig.customFolderName, ".nomedia", "".getBytes(), false);
        GlobalConfig.setFirstStoragePathStatus(LightCache.testFileExist(GlobalConfig.getFirstStoragePath() + "imgs" + File.separator + ".nomedia")); // TODO: set status?
        LightCache.saveFile(GlobalConfig.getFirstFullSaveFilePath() + "imgs", ".nomedia", "".getBytes(), false);
        LightCache.saveFile(GlobalConfig.getSecondFullSaveFilePath() + "imgs", ".nomedia", "".getBytes(), false);

        /* This is a delay template */
        new CountDownTimer(1000, 100) {
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
