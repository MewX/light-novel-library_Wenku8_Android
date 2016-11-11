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

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.ChapterInfo;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.reader.activity.Wenku8ReaderActivityV1;
import org.mewx.wenku8.util.LightCache;

import java.io.File;

/**
 * Created by MewX on 2015/5/14.
 * Novel Chapter Activity.
 */
public class NovelChapterActivity extends AppCompatActivity {

    // constant
    private final String FromLocal = "fav";

    // private vars
    private int aid = 1;
    private String from = "";
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
        Toolbar mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_material);
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

        // get views and set title
        LinearLayout mLinearLayout = (LinearLayout) findViewById(R.id.novel_chapter_scroll);
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
                    if (from.equals(FromLocal) &&
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
                                        Intent intent = new Intent(NovelChapterActivity.this, Wenku8ReaderActivityV1.class); //VerticalReaderActivity.class);
                                        intent.putExtra("aid", aid);
                                        intent.putExtra("volume", volumeList);
                                        intent.putExtra("cid", ci.cid);
                                        intent.putExtra("from", "cloud"); // from cloud
                                        startActivity(intent);
                                        overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
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
                    Intent intent = new Intent(NovelChapterActivity.this, Wenku8ReaderActivityV1.class); //VerticalReaderActivity.class);
                    intent.putExtra("aid", aid);
                    intent.putExtra("volume", volumeList);
                    intent.putExtra("cid", ci.cid);
                    intent.putExtra("from", from); // from "fav"
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                }
            });
            rl.findViewById(R.id.chapter_btn).setOnLongClickListener(new View.OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    new MaterialDialog.Builder(NovelChapterActivity.this)
                            .theme(Theme.LIGHT)
                            .title(R.string.system_choose_reader_engine)
                            .items(R.array.reader_engine_option)
                            .itemsCallback(new MaterialDialog.ListCallback() {
                                @Override
                                public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                                    switch (which) {
                                        case 0:
                                            // V1
                                            // test does file exist
                                            if (from.equals(FromLocal) &&
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
                                                                Intent intent = new Intent(NovelChapterActivity.this, Wenku8ReaderActivityV1.class); //VerticalReaderActivity.class);
                                                                intent.putExtra("aid", aid);
                                                                intent.putExtra("volume", volumeList);
                                                                intent.putExtra("cid", ci.cid);
                                                                intent.putExtra("from", "cloud"); // from cloud
                                                                startActivity(intent);
                                                                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
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
                                            else {
                                                // jump to reader activity
                                                Intent intent = new Intent(NovelChapterActivity.this, Wenku8ReaderActivityV1.class); //VerticalReaderActivity.class);
                                                intent.putExtra("aid", aid);
                                                intent.putExtra("volume", volumeList);
                                                intent.putExtra("cid", ci.cid);
                                                intent.putExtra("from", from); // from "fav"
                                                startActivity(intent);
                                                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                            }
                                            break;

                                        case 1:
                                            // old
                                            // test does file exist
                                            if (from.equals(FromLocal) &&
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
                                                                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
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
                                            else {
                                                // jump to reader activity
                                                Intent intent = new Intent(NovelChapterActivity.this, VerticalReaderActivity.class);
                                                intent.putExtra("aid", aid);
                                                intent.putExtra("volume", volumeList);
                                                intent.putExtra("cid", ci.cid);
                                                intent.putExtra("from", from); // from "fav"
                                                startActivity(intent);
                                                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                            }
                                            break;
                                    }
                                }
                            })
                            .show();
                    return true;
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
