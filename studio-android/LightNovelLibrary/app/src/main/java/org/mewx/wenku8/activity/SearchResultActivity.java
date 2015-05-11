package org.mewx.wenku8.activity;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.MenuItem;
import android.widget.TextView;

import org.mewx.wenku8.R;
import org.mewx.wenku8.fragment.NovelItemListFragment;

/**
 * Created by MewX on 2015/5/11.
 */
public class SearchResultActivity extends AppCompatActivity {

    // private vars
    private String searchKey = null;
    private Toolbar mToolbar = null;
    private Fragment mFragment = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_search_result);

        // get arguments
        searchKey = getIntent().getStringExtra("key");

        // set indicator enable
        mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // set back arrow icon
        final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        upArrow.setColorFilter(getResources().getColor(R.color.mySearchToggleColor), PorterDuff.Mode.SRC_ATOP);
        getSupportActionBar().setHomeAsUpIndicator(upArrow);

        // set action bat title
        TextView mTextView = (TextView) findViewById(R.id.search_result_title);
        if(mTextView != null)
            mTextView.setText(getResources().getString(R.string.title_search) + searchKey);

        // init values
        Bundle bundle = new Bundle();
        bundle.putString("type", "search");
        bundle.putString("key", searchKey);

        // This code will produce more than one activity in stack, so I jump to new SearchActivity to escape it.
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.result_fragment, NovelItemListFragment.newInstance(bundle), "fragment")
                .setTransitionStyle(FragmentTransaction.TRANSIT_NONE)
                .commit();

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

        // leaving animation: left out
        overridePendingTransition( R.anim.hold, android.R.anim.slide_out_right);
    }
}
