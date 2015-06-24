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
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.ChapterInfo;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.util.LightCache;

import java.io.File;
import java.util.List;

/**
 * Created by MewX on 2015/5/14.
 */
public class NovelChapterActivity extends AppCompatActivity {

    // constant
    private final String FromLocal = "fav";

    // private vars
    private int aid = 1;
    private String from = "";
    private LinearLayout mLinearLayout = null;
    private Toolbar mToolbar = null;
    private VolumeList volumeList= null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_novel_chapter);

        // fetch values
        aid = getIntent().getIntExtra("aid", 1);
        from = getIntent().getStringExtra("from");
        volumeList = (VolumeList) getIntent().getSerializableExtra("volume");

        // set indicator enable
        mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        if(getSupportActionBar() != null && upArrow != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
            upArrow.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
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
            rl.findViewById(R.id.chapter_btn).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    // test does file exist
                    if(from.equals(FromLocal) &&
                            !LightCache.testFileExist(GlobalConfig.getFirstStoragePath()
                                    + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + ci.cid + ".xml") &&
                            !LightCache.testFileExist(GlobalConfig.getSecondStoragePath()
                                    + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + ci.cid + ".xml")) {
                        // local file not download, ask to download an read or cancel
                        new MaterialDialog.Builder(NovelChapterActivity.this)
                                .callback(new MaterialDialog.ButtonCallback() {
                                    @Override
                                    public void onPositive(MaterialDialog dialog) {
                                        super.onPositive(dialog);

                                        // jump to reader activity
                                        Intent intent = new Intent(NovelChapterActivity.this, VerticalReaderActivity.class);
                                        intent.putExtra("aid", aid);
                                        intent.putExtra("volume", volumeList);
                                        intent.putExtra("cid", ci.cid);
                                        intent.putExtra("from", "cloud"); // from cloud
                                        startActivity(intent);
                                    }
                                })
                                .theme(Theme.LIGHT)
                                .backgroundColorRes(R.color.dlgBackgroundColor)
                                .contentColorRes(R.color.dlgContentColor)
                                .positiveColorRes(R.color.dlgPositiveButtonColor)
                                .negativeColorRes(R.color.dlgNegativeButtonColor)
                                .content(getResources().getString(R.string.dialog_content_load_from_cloud))
                                .contentGravity(GravityEnum.CENTER)
                                .positiveText(R.string.dialog_positive_likethis)
                                .negativeText(R.string.dialog_negative_preferno)
                                .show();
                        return;
                    }

                    // jump to reader activity
                    Intent intent = new Intent(NovelChapterActivity.this, VerticalReaderActivity.class);
                    intent.putExtra("aid", aid);
                    intent.putExtra("volume", volumeList);
                    intent.putExtra("cid", ci.cid);
                    intent.putExtra("from", from); // from "fav"
                    startActivity(intent);
                }
            });

            // add to scroll view
            mLinearLayout.addView(rl);
        }
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
