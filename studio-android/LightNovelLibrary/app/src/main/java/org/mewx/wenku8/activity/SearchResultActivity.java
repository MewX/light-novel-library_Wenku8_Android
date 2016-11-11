package org.mewx.wenku8.activity;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.fragment.NovelItemListFragment;
import org.mewx.wenku8.global.GlobalConfig;

/**
 * Created by MewX on 2015/5/11.
 * Search Result Activity.
 */
public class SearchResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_search_result);

        // get arguments
        String searchKey = getIntent().getStringExtra("key");

        // set indicator enable
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        if(getSupportActionBar() != null ) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        // change status bar color tint, and this require SDK16
        if (Build.VERSION.SDK_INT >= 16 ) { //&& Build.VERSION.SDK_INT <= 21) {
            // Android API 22 has more effects on status bar, so ignore

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
                getWindow().setNavigationBarColor(getResources().getColor(R.color.myNavigationColorWhite));
        }

        // set action bat title
        TextView mTextView = (TextView) findViewById(R.id.search_result_title);
        if(mTextView != null)
            mTextView.setText(getResources().getString(R.string.title_search) + searchKey);

        // init values
        Bundle bundle = new Bundle();
        bundle.putString("type", "search");
        bundle.putString("key", searchKey);

        // UIL setting
        if(ImageLoader.getInstance() == null || !ImageLoader.getInstance().isInited()) {
            GlobalConfig.initImageLoader(this);
        }

        // This code will produce more than one activity in stack, so I jump to new SearchActivity to escape it.
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.result_fragment, NovelItemListFragment.newInstance(bundle), "fragment")
                .setTransitionStyle(FragmentTransaction.TRANSIT_NONE)
                .commit();

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

        // set back arrow icon
        final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_material);
        if(upArrow != null && getSupportActionBar() != null) {
            upArrow.setColorFilter(getResources().getColor(R.color.mySearchToggleColor), PorterDuff.Mode.SRC_ATOP);
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();

        // leave animation: fade out
        overridePendingTransition(0, R.anim.fade_out);
    }
}
