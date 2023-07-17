package org.mewx.wenku8.activity;

import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import com.google.firebase.analytics.FirebaseAnalytics;

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

        // Init Firebase Analytics on GA4.
        FirebaseAnalytics.getInstance(this);

        // get version code
        TextView tvVersion = findViewById(R.id.app_version);
        tvVersion.setText(String.format(getResources().getString(R.string.about_version_template), BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE));
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(menuItem);
    }
}
