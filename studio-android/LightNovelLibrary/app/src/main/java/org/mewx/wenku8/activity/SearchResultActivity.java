package org.mewx.wenku8.activity;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;

import androidx.fragment.app.FragmentTransaction;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.mewx.wenku8.R;
import org.mewx.wenku8.fragment.NovelItemListFragment;
import org.mewx.wenku8.global.GlobalConfig;

/**
 * Created by MewX on 2015/5/11.
 * Search Result Activity.
 */
public class SearchResultActivity extends BaseMaterialActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_search_result, StatusBarColor.WHITE);

        // get arguments
        String searchKey = getIntent().getStringExtra("key");

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
    protected void onResume() {
        super.onResume();

        // set back arrow icon
        final Drawable upArrow = getResources().getDrawable(R.drawable.ic_svg_back);
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
