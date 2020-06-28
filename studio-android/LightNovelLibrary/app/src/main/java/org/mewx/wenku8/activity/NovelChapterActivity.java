package org.mewx.wenku8.activity;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

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
        Toolbar mToolbar = findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        final Drawable upArrow = getResources().getDrawable(R.drawable.ic_svg_back);
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
        LinearLayout mLinearLayout = findViewById(R.id.novel_chapter_scroll);
        getSupportActionBar().setTitle(volumeList.volumeName);

        for(final ChapterInfo ci : volumeList.chapterList) {
            // get view
            RelativeLayout rl = (RelativeLayout) LayoutInflater.from(NovelChapterActivity.this).inflate(R.layout.view_novel_chapter_item, null);

            TextView tv = rl.findViewById(R.id.chapter_title);
            tv.setText(ci.chapterName);
            rl.findViewById(R.id.chapter_btn).setOnClickListener(ignored -> {
                // jump to reader activity
                Intent intent = new Intent(NovelChapterActivity.this, Wenku8ReaderActivityV1.class);
                intent.putExtra("aid", aid);
                intent.putExtra("volume", volumeList);
                intent.putExtra("cid", ci.cid);

                // test does file exist
                if (from.equals(FromLocal)
                        && !LightCache.testFileExist(GlobalConfig.getFirstStoragePath() + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + ci.cid + ".xml")
                        && !LightCache.testFileExist(GlobalConfig.getSecondStoragePath() + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + ci.cid + ".xml")) {
                    intent.putExtra("from", "cloud"); // from cloud
                }
                else {
                    intent.putExtra("from", from); // from "fav"
                }

                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
            });
            rl.findViewById(R.id.chapter_btn).setOnLongClickListener(ignored -> {
                new MaterialDialog.Builder(NovelChapterActivity.this)
                        .theme(Theme.LIGHT)
                        .title(R.string.system_choose_reader_engine)
                        .items(R.array.reader_engine_option)
                        .itemsCallback((ignored1, ignored2, which, ignored3) -> {
                            Class readerClass = Wenku8ReaderActivityV1.class;
                            switch (which) {
                                case 0:
                                    // V1
                                    readerClass = Wenku8ReaderActivityV1.class;
                                    break;

                                case 1:
                                    // old
                                    readerClass = VerticalReaderActivity.class;
                                    break;
                            }

                            Intent intent = new Intent(NovelChapterActivity.this, readerClass);
                            intent.putExtra("aid", aid);
                            intent.putExtra("volume", volumeList);
                            intent.putExtra("cid", ci.cid);

                            // test does file exist
                            if (from.equals(FromLocal)
                                    && !LightCache.testFileExist(GlobalConfig.getFirstStoragePath() + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + ci.cid + ".xml")
                                    && !LightCache.testFileExist(GlobalConfig.getSecondStoragePath() + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + ci.cid + ".xml")) {
                                // jump to reader activity
                                intent.putExtra("from", "cloud"); // from cloud
                            } else {
                                intent.putExtra("from", from); // from "fav"
                            }

                            startActivity(intent);
                            overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                        })
                        .show();
                return true;
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
