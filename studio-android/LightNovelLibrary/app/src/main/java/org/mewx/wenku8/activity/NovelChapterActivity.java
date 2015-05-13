package org.mewx.wenku8.activity;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.readystatesoftware.systembartint.SystemBarTintManager;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.api.ChapterInfo;
import org.mewx.wenku8.global.api.VolumeList;

import java.util.List;

/**
 * Created by MewX on 2015/5/14.
 */
public class NovelChapterActivity extends AppCompatActivity {

    // private vars
    private LinearLayout mLinearLayout = null;
    private Toolbar mToolbar = null;
    private VolumeList volumeList= null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_novel_chapter);

        // fetch values
        volumeList = (VolumeList) getIntent().getSerializableExtra("volume");

        // set indicator enable
        mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        upArrow.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
        getSupportActionBar().setHomeAsUpIndicator(upArrow);

        // change status bar color tint, and this require SDK16
        if (Build.VERSION.SDK_INT >= 16 ) { //&& Build.VERSION.SDK_INT <= 21) {
            // Android API 22 has more effects on status bar, so ignore

            // create our manager instance after the content view is set
            SystemBarTintManager tintManager = new SystemBarTintManager(this);
            // enable all tint
            tintManager.setStatusBarTintEnabled(true);
            tintManager.setNavigationBarTintEnabled(true);
            tintManager.setTintAlpha(0.15f);
            // set all color
            tintManager.setTintColor(getResources().getColor(android.R.color.black));
        }

        // get views and set title
        mLinearLayout = (LinearLayout) findViewById(R.id.novel_chapter_scroll);
        getSupportActionBar().setTitle(volumeList.volumeName);

        for(final ChapterInfo ci : volumeList.chapterList) {
            // get view
            RelativeLayout rl = (RelativeLayout) LayoutInflater.from(NovelChapterActivity.this).inflate(R.layout.view_novel_chapter_item, null);

            TextView tv = (TextView) rl.findViewById(R.id.chapter_title);
            tv.setText(ci.chapterName);
            tv.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // jump to reader activity

                }
            });

            // add to scroll view
            mLinearLayout.addView(rl);
        }

    }


    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(menuItem);
    }
}
