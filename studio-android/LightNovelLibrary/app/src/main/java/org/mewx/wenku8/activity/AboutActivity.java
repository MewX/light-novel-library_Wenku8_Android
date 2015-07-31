package org.mewx.wenku8.activity;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.TypedValue;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;

/**
 * Created by MewX on 2015/7/29.
 * About activity.
 */
public class AboutActivity extends AppCompatActivity {
    private int count = -10000;
    private RelativeLayout rlMewX;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_about);

        // set indicator enable
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        if(getSupportActionBar() != null && upArrow != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            upArrow.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }

        // change status bar color tint, and this require SDK16
        if (Build.VERSION.SDK_INT >= 16 ) {
            // create our manager instance after the content view is set
            SystemBarTintManager tintManager = new SystemBarTintManager(this);
            // enable all tint
            tintManager.setStatusBarTintEnabled(true);
            tintManager.setNavigationBarTintEnabled(true);
            tintManager.setTintAlpha(0.15f);
            tintManager.setNavigationBarAlpha(0.0f);
            // set all color
            tintManager.setTintColor(getResources().getColor(android.R.color.black));
            // set Navigation bar color
            if(Build.VERSION.SDK_INT >= 21)
                getWindow().setNavigationBarColor(getResources().getColor(R.color.myNavigationColor));
        }

        // get version code
        PackageManager manager;
        PackageInfo info;
        manager = this.getPackageManager();
        try {
            info = manager.getPackageInfo(this.getPackageName(), 0);
            ((TextView) findViewById(R.id.app_version)).setText("Version: " + info.versionName + " (" + info.versionCode + ")"
                    + (GlobalConfig.inAlphaBuild() ? " 内测版" : "正式版"));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        // game
        rlMewX = (RelativeLayout) findViewById(R.id.mewx_layout);
        rlMewX.setOnClickListener(new View.OnClickListener() {
            TextView tv = new TextView(AboutActivity.this);
            RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

            @Override
            public void onClick(View v) {
                if(count == -10000) {
                    // add textview
//                    <TextView
//                    android:id="@+id/mewx_name"
//                    android:layout_width="wrap_content"
//                    android:layout_height="wrap_content"
//                    android:gravity="center_vertical"
//                    android:textSize="14sp"
//                    android:paddingTop="12dp"
//                    android:paddingBottom="12dp"
//                    android:text="MewX\t&lt;imewx@qq.com&gt;"
//                    android:singleLine="true"
//                    android:textColor="@color/menu_text_color"
//                    android:layout_below="@+id/divider1"
//                    android:layout_centerHorizontal="true" />
                    tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                    tv.setPadding(0, 12, 0, 12);
                    tv.setTextColor(getResources().getColor(R.color.menu_text_color));
                    tv.setText("再点击" + (-count) + "次可以查看工程师的秘密！ :P");
                    lp.addRule(RelativeLayout.BELOW, R.id.mewx_blog);
                    lp.addRule(RelativeLayout.CENTER_VERTICAL);
                    rlMewX.addView(tv, lp);
                    count ++;
                }
                else if(count < 0) {
                    tv.setText("再点击" + (-count) + "次可以查看工程师的秘密 :)");
                    count ++;
                }
                else {
                    new MaterialDialog.Builder(AboutActivity.this)
                            .theme(Theme.LIGHT)
                            .content("哇哦，有诚意的用户大人哦~\n攻城狮患有直男癌，偏好飞机场，已加入卫士道豪华套餐，找不到女盆友，注孤生！\n征婚热线：QQ307740614 :-)")
                            .positiveText(R.string.dialog_positive_ok)
                            .show();
                }
            }
        });
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(menuItem);
    }
}
