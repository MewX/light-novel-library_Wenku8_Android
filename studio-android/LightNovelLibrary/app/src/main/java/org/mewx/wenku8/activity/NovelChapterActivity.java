package org.mewx.wenku8.activity;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.google.firebase.analytics.FirebaseAnalytics;

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
public class NovelChapterActivity extends BaseMaterialActivity {

    // constant
    private final String FromLocal = "fav";

    // private vars
    private int aid = 1;
    private String from = "";
    private VolumeList volumeList= null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_novel_chapter);

        // Init Firebase Analytics on GA4.
        FirebaseAnalytics.getInstance(this);

        // fetch values
        aid = getIntent().getIntExtra("aid", 1);
        from = getIntent().getStringExtra("from");
        volumeList = (VolumeList) getIntent().getSerializableExtra("volume");

        // set title
        getSupportActionBar().setTitle(volumeList.volumeName);
        buildChapterList();
    }

    private void buildChapterList() {
        // get views
        LinearLayout mLinearLayout = findViewById(R.id.novel_chapter_scroll);
        mLinearLayout.removeAllViews();

        final GlobalConfig.ReadSavesV1 rs = GlobalConfig.getReadSavesRecordV1(aid);
        for(final ChapterInfo ci : volumeList.chapterList) {
            // get view
            RelativeLayout rl = (RelativeLayout) LayoutInflater.from(NovelChapterActivity.this).inflate(R.layout.view_novel_chapter_item, null);

            TextView tv = rl.findViewById(R.id.chapter_title);
            tv.setText(ci.chapterName);

            final RelativeLayout btn = rl.findViewById(R.id.chapter_btn);
            // added indicator for last read chapter
            if (rs != null && rs.cid == ci.cid) {
                btn.setBackgroundColor(Color.LTGRAY);
            }
            btn.setOnClickListener(ignored -> {
                // jump to reader activity
                Intent intent = new Intent(NovelChapterActivity.this, Wenku8ReaderActivityV1.class);
                intent.putExtra("aid", aid);
                intent.putExtra("volume", volumeList);
                intent.putExtra("cid", ci.cid);

                // test does file exist
                if (from.equals(FromLocal)
                    && !LightCache.testFileExist(GlobalConfig.getDefaultStoragePath() + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + ci.cid + ".xml")
                    && !LightCache.testFileExist(GlobalConfig.getBackupStoragePath() + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + ci.cid + ".xml")) {
                    intent.putExtra("from", "cloud"); // from cloud
                }
                else {
                    intent.putExtra("from", from); // from "fav"
                }

                startActivity(intent);
                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
            });
            btn.setOnLongClickListener(ignored -> {
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
                            && !LightCache.testFileExist(GlobalConfig.getDefaultStoragePath() + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + ci.cid + ".xml")
                            && !LightCache.testFileExist(GlobalConfig.getBackupStoragePath() + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + ci.cid + ".xml")) {
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
    protected void onResume() {
        super.onResume();

        // refresh when back from reader activity
        buildChapterList();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(menuItem);
    }
}
