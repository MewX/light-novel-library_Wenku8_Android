package org.mewx.wenku8.activity;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.BuildConfig;
import org.mewx.wenku8.R;

/**
 * Created by MewX on 2015/7/29.
 * About activity.
 */
public class AboutActivity extends BaseMaterialActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_about);

        // get version code
        TextView tvVersion = findViewById(R.id.app_version);
        tvVersion.setText(String.format(getResources().getString(R.string.about_version_template), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
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
