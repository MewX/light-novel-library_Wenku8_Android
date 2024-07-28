package org.mewx.wenku8.activity;

import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.ChapterInfo;
import org.mewx.wenku8.global.api.NovelItemMeta;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.global.api.OldNovelContentParser.NovelContentType;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.reader.activity.Wenku8ReaderActivityV1;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightNetwork;
import org.mewx.wenku8.util.LightTool;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;

/**
 * Created by MewX on 2015/5/13.
 * Novel Info Activity.
 */
public class NovelInfoActivity extends BaseMaterialActivity {

    // constant
    private final String FromLocal = "fav";

    // private vars
    private FirebaseAnalytics mFirebaseAnalytics;
    private int aid = 1;
    private String from = "", title = "";
    private boolean isLoading = true;
    private RelativeLayout rlMask = null; // mask layout
    private LinearLayout mLinearLayout = null;
    private TextView tvNovelTitle = null;
    private TextView tvNovelAuthor = null;
    private TextView tvNovelStatus = null;
    private TextView tvNovelUpdate = null;
    private TextView tvLatestChapter = null;
    private TextView tvNovelFullIntro = null;
    private MaterialDialog pDialog = null;
    private FloatingActionButton fabFavorite = null;
    private FloatingActionsMenu famMenu = null;
    private SmoothProgressBar spb = null;
    private NovelItemMeta mNovelItemMeta = null;
    private List<VolumeList> listVolume = new ArrayList<>();
    private String novelFullMeta = null, novelFullIntro = null, novelFullVolume = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_novel_info);

        // Init Firebase Analytics on GA4.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // fetch values
        aid = getIntent().getIntExtra("aid", 1);
        from = getIntent().getStringExtra("from");
        title = getIntent().getStringExtra("title");

        // Analysis.
        Bundle viewItemParams = new Bundle();
        viewItemParams.putString(FirebaseAnalytics.Param.ITEM_ID, "" + aid);
        viewItemParams.putString(FirebaseAnalytics.Param.ITEM_NAME, title);
        viewItemParams.putString("from", from);
        mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, viewItemParams);

        // UIL setting
        if(ImageLoader.getInstance() == null || !ImageLoader.getInstance().isInited()) {
            GlobalConfig.initImageLoader(this);
        }

        // get views
        rlMask = findViewById(R.id.white_mask);
        mLinearLayout = findViewById(R.id.novel_info_scroll);
        LinearLayout llCardLayout = findViewById(R.id.item_card);
        ImageView ivNovelCover = findViewById(R.id.novel_cover);
        tvNovelTitle = findViewById(R.id.novel_title);
        tvNovelAuthor = findViewById(R.id.novel_author);
        tvNovelStatus = findViewById(R.id.novel_status);
        tvNovelUpdate = findViewById(R.id.novel_update);
        TextView tvLatestChapterNameText = findViewById(R.id.novel_item_text_shortinfo);
        tvLatestChapter = findViewById(R.id.novel_intro);
        tvNovelFullIntro = findViewById(R.id.novel_intro_full);
        ImageButton ibNovelOption = findViewById(R.id.novel_option);
        fabFavorite = findViewById(R.id.fab_favorate);
        FloatingActionButton fabDownload = findViewById(R.id.fab_download);
        famMenu = findViewById(R.id.multiple_actions);
        spb = findViewById(R.id.spb);

        // hide view and set colors
        tvNovelTitle.setText(title);
        // FIXME: these imgs folders are actually no in use.
        if(LightCache.testFileExist(GlobalConfig.getDefaultStoragePath() + "imgs" + File.separator + aid + ".jpg"))
            ImageLoader.getInstance().displayImage("file://" + GlobalConfig.getDefaultStoragePath() + "imgs" + File.separator + aid + ".jpg", ivNovelCover);
        else if(LightCache.testFileExist(GlobalConfig.getBackupStoragePath() + "imgs" + File.separator + aid + ".jpg"))
            ImageLoader.getInstance().displayImage("file://" + GlobalConfig.getBackupStoragePath() + "imgs" + File.separator + aid + ".jpg", ivNovelCover);
        else
            ImageLoader.getInstance().displayImage(Wenku8API.getCoverURL(aid), ivNovelCover); // move to onCreateView!
        tvLatestChapterNameText.setText(getResources().getText(R.string.novel_item_latest_chapter));
        ibNovelOption.setVisibility(ImageButton.INVISIBLE);
        fabFavorite.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
        fabDownload.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
        llCardLayout.setBackgroundResource(R.color.menu_transparent);
        if (GlobalConfig.testInLocalBookshelf(aid)) {
            fabFavorite.setIcon(R.drawable.ic_favorate_pressed);
        }

        // fetch all info
        getSupportActionBar().setTitle(R.string.action_novel_info);
        spb.setVisibility(View.INVISIBLE); // wait for runnable
        Handler handler = new Handler();
        handler.postDelayed(() -> {
            spb.setVisibility(View.VISIBLE);
            if (from.equals(FromLocal))
                refreshInfoFromLocal();
            else
                refreshInfoFromCloud();
        }, 500);


        // set on click listeners
        famMenu.setOnFloatingActionsMenuUpdateListener(new FloatingActionsMenu.OnFloatingActionsMenuUpdateListener() {
            @Override
            public void onMenuExpanded() {
                rlMask.setVisibility(View.VISIBLE);
            }

            @Override
            public void onMenuCollapsed() {
                rlMask.setVisibility(View.INVISIBLE);
            }
        });
        rlMask.setOnClickListener(v -> {
            // Collapse the fam
            if (famMenu.isExpanded())
                famMenu.collapse();
        });
        tvNovelTitle.setBackground(getResources().getDrawable(R.drawable.btn_menu_item));
        tvNovelAuthor.setBackground(getResources().getDrawable(R.drawable.btn_menu_item));
        tvLatestChapter.setBackground(getResources().getDrawable(R.drawable.btn_menu_item));
        tvNovelTitle.setOnClickListener(v -> {
            if (runLoadingChecker()) return;

            // show aid: title
            new MaterialDialog.Builder(NovelInfoActivity.this)
                    .theme(Theme.LIGHT)
                    .titleColorRes(R.color.dlgTitleColor)
                    .backgroundColorRes(R.color.dlgBackgroundColor)
                    .contentColorRes(R.color.dlgContentColor)
                    .positiveColorRes(R.color.dlgPositiveButtonColor)
                    .title(R.string.dialog_content_novel_title)
                    .content(aid + ": " + mNovelItemMeta.title)
                    .contentGravity(GravityEnum.CENTER)
                    .positiveText(R.string.dialog_positive_known)
                    .show();
        });
        tvNovelAuthor.setOnClickListener(v -> {
            if (runLoadingChecker()) return;

            new MaterialDialog.Builder(NovelInfoActivity.this)
                    .theme(Theme.LIGHT)
                    .onPositive((ignored1, ignored2) -> {
                        // search author name
                        Intent intent = new Intent(NovelInfoActivity.this, SearchResultActivity.class);
                        intent.putExtra("key", mNovelItemMeta.author);
                        startActivity(intent);
                        overridePendingTransition(R.anim.fade_in, R.anim.hold);
                    })
                    .content(R.string.dialog_content_search_author)
                    .positiveText(R.string.dialog_positive_ok)
                    .negativeText(R.string.dialog_negative_biao)
                    .show();
        });
        fabFavorite.setOnClickListener(v -> {
            if (runLoadingChecker()) return;

            // add to favorite
            if(GlobalConfig.testInLocalBookshelf(aid)) {
                new MaterialDialog.Builder(NovelInfoActivity.this)
                        .onPositive((ignored1, ignored2) -> {
                            // delete from cloud first, if succeed then delete from local
                            AsyncRemoveBookFromCloud arbfc = new AsyncRemoveBookFromCloud();
                            arbfc.execute(aid);
                        })
                        .theme(Theme.LIGHT)
                        .backgroundColorRes(R.color.dlgBackgroundColor)
                        .contentColorRes(R.color.dlgContentColor)
                        .positiveColorRes(R.color.dlgPositiveButtonColor)
                        .negativeColorRes(R.color.dlgNegativeButtonColor)
                        .content(R.string.dialog_content_sure_to_unfav)
                        .contentGravity(GravityEnum.CENTER)
                        .positiveText(R.string.dialog_positive_yes)
                        .negativeText(R.string.dialog_negative_preferno)
                        .show();
            }
            else {
                // not in bookshelf, add it to.
                if (novelFullMeta == null || novelFullIntro == null || novelFullVolume == null) {
                    ArrayList<String> nullStuff = new ArrayList<>();
                    if (novelFullMeta == null) nullStuff.add("meta");
                    if (novelFullIntro == null) nullStuff.add("intro");
                    if (novelFullVolume == null) nullStuff.add("volume");
                    Bundle somethingIsNull = new Bundle();
                    somethingIsNull.putStringArrayList("novel_info_save_null", nullStuff);
                    mFirebaseAnalytics.logEvent(FirebaseAnalytics.Event.VIEW_ITEM, somethingIsNull);

                    Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.system_loading_please_wait), Toast.LENGTH_SHORT).show();
                } else {
                    // No null text.
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", aid + "-intro.xml", novelFullMeta);
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", aid + "-introfull.xml", novelFullIntro);
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", aid + "-volume.xml", novelFullVolume);
                    GlobalConfig.addToLocalBookshelf(aid);
                    if (GlobalConfig.testInLocalBookshelf(aid)) { // in
                        Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.bookshelf_added), Toast.LENGTH_SHORT).show();
                        fabFavorite.setIcon(R.drawable.ic_favorate_pressed);
                    } else {
                        Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.bookshelf_error), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        fabDownload.setOnClickListener(v -> {
            if (runLoadingChecker()) return;

            if(!GlobalConfig.testInLocalBookshelf(aid)) {
                Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.system_fav_it_first), Toast.LENGTH_SHORT).show();
                return;
            }

            // download / update activity or verify downloading action (add to queue)
            // use list dialog to provide more functions
            new MaterialDialog.Builder(NovelInfoActivity.this)
                    .theme(Theme.LIGHT)
                    .title(R.string.dialog_title_choose_download_option)
                    .backgroundColorRes(R.color.dlgBackgroundColor)
                    .titleColorRes(R.color.dlgTitleColor)
                    .negativeText(R.string.dialog_negative_pass)
                    .negativeColorRes(R.color.dlgNegativeButtonColor)
                    .itemsGravity(GravityEnum.CENTER)
                    .items(R.array.download_option)
                    .itemsCallback((dialog, view, which, text) -> {
                        /*
                         * 0 <string name="dialog_option_check_for_update">检查更新</string>
                         * 1 <string name="dialog_option_update_uncached_volumes">更新下载</string>
                         * 2 <string name="dialog_option_force_update_all">覆盖下载</string>
                         * 3 <string name="dialog_option_select_and_update">分卷下载</string>
                         */
                        switch (which) {
                            case 0:
                                optionCheckUpdates();
                                break;

                            case 1:
                                optionDownloadUpdates();
                                break;

                            case 2:
                                optionDownloadOverride();
                                break;

                            case 3:
                                optionDownloadSelected();
                                break;
                        }
                    })
                    .show();
        });
        tvLatestChapter.setOnClickListener(view -> {
            if (runLoadingChecker()) return;

            // no sufficient info
            if (mNovelItemMeta != null && mNovelItemMeta.latestSectionCid != 0)
                showDirectJumpToReaderDialog( mNovelItemMeta.latestSectionCid);
            else
                Toast.makeText(this, getResources().getText(R.string.reader_msg_please_refresh_and_retry), Toast.LENGTH_SHORT).show();
        });
    }

    /**
     * run loading checker
     * @return true if loading; otherwise false
     */
    private boolean runLoadingChecker() {
        if (isLoading) {
            Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.system_loading_please_wait), Toast.LENGTH_SHORT).show();
        }
        return isLoading;
    }

    /**
     * 0 <string name="dialog_option_check_for_update">检查更新</string>
     */
    private void optionCheckUpdates() {
        // async task
        isLoading = true;
        final AsyncUpdateCacheTask auct = new AsyncUpdateCacheTask();
        auct.execute(aid, 0);

        // show progress
        pDialog = new MaterialDialog.Builder(NovelInfoActivity.this)
                .theme(Theme.LIGHT)
                .content(R.string.dialog_content_downloading)
                .progress(false, 1, true)
                .cancelable(true)
                .cancelListener(dialog12 -> {
                    isLoading = false;
                    auct.cancel(true);
                    pDialog.dismiss();
                    pDialog = null;
                })
                .show();

        pDialog.setProgress(0);
        pDialog.setMaxProgress(1);
        pDialog.show();
    }

    /**
     * 1 <string name="dialog_option_update_uncached_volumes">更新下载</string>
     */
    private void optionDownloadUpdates() {
        // async task
        isLoading = true;
        final AsyncUpdateCacheTask auct = new AsyncUpdateCacheTask();
        auct.execute(aid, 1);

        // show progress
        pDialog = new MaterialDialog.Builder(NovelInfoActivity.this)
                .theme(Theme.LIGHT)
                .content(R.string.dialog_content_downloading)
                .progress(false, 1, true)
                .cancelable(true)
                .cancelListener(dialog1 -> {
                    isLoading = false;
                    auct.cancel(true);
                    pDialog.dismiss();
                    pDialog = null;
                })
                .show();

        pDialog.setProgress(0);
        pDialog.setMaxProgress(1);
        pDialog.show();
    }

    /**
     * 2 <string name="dialog_option_force_update_all">覆盖下载</string>
     */
    private void optionDownloadOverride() {
        new MaterialDialog.Builder(NovelInfoActivity.this)
                .onPositive((ignored1, ignored2) -> {
                    // async task
                    isLoading = true;
                    final AsyncUpdateCacheTask auct = new AsyncUpdateCacheTask();
                    auct.execute(aid, 2);

                    // show progress
                    pDialog = new MaterialDialog.Builder(NovelInfoActivity.this)
                            .theme(Theme.LIGHT)
                            .content(R.string.dialog_content_downloading)
                            .progress(false, 1, true)
                            .cancelable(true)
                            .cancelListener(dialog13 -> {
                                isLoading = false;
                                auct.cancel(true);
                                pDialog.dismiss();
                                pDialog = null;
                            })
                            .show();

                    pDialog.setProgress(0);
                    pDialog.setMaxProgress(1);
                    pDialog.show();
                })
                .theme(Theme.LIGHT)
                .backgroundColorRes(R.color.dlgBackgroundColor)
                .contentColorRes(R.color.dlgContentColor)
                .positiveColorRes(R.color.dlgPositiveButtonColor)
                .negativeColorRes(R.color.dlgNegativeButtonColor)
                .content(R.string.dialog_content_verify_force_update)
                .contentGravity(GravityEnum.CENTER)
                .positiveText(R.string.dialog_positive_likethis)
                .negativeText(R.string.dialog_negative_preferno)
                .show();
    }

    /**
     * 3 <string name="dialog_option_select_and_update">分卷下载</string>
     */
    private void optionDownloadSelected() {
        // select volumes
        String[] volumes = new String[listVolume.size()];
        for(int i = 0; i < listVolume.size(); i ++)
            volumes[i] = listVolume.get(i).volumeName;

        new MaterialDialog.Builder(NovelInfoActivity.this)
                .theme(Theme.LIGHT)
                .title(R.string.dialog_option_select_and_update)
                .items(volumes)
                .itemsCallbackMultiChoice(null, (dialog, which, text) -> {
                    if (which == null || which.length == 0) return true;

                    AsyncDownloadVolumes adv = new AsyncDownloadVolumes();
                    adv.execute(which);
                    return true;
                })
                .positiveText(R.string.dialog_positive_ok)
                .show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(getSupportActionBar()!=null)
            getSupportActionBar().setTitle(getResources().getString(R.string.action_novel_info));
        getMenuInflater().inflate(R.menu.menu_novel_info, menu);

        // fill the icon to white color
        for (int i = 0; i < menu.size(); i ++) {
            Drawable drawable = menu.getItem(i).getIcon();
            if (drawable != null) {
                drawable.mutate();
                drawable.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
            }
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            if(Build.VERSION.SDK_INT < 21)
                finish();
            else
                finishAfterTransition(); // end directly
        }
        else if (menuItem.getItemId() == R.id.action_continue_read_progress) {
            if (runLoadingChecker()) return true;

            // show dialog, jump to last read position
            final GlobalConfig.ReadSavesV1 rs = GlobalConfig.getReadSavesRecordV1(aid);
            if (rs != null) {
                showDirectJumpToReaderDialog(rs.cid);
                return true;
            }
            // not found
            Toast.makeText(this, getResources().getText(R.string.reader_msg_no_saved_reading_progress), Toast.LENGTH_SHORT).show();
        } else if (menuItem.getItemId() == R.id.action_go_to_forum) {
            Intent intent = new Intent(NovelInfoActivity.this, NovelReviewListActivity.class);
            intent.putExtra("aid", aid);
            startActivity(intent);
        }
        return super.onOptionsItemSelected(menuItem);
    }

    private void showDirectJumpToReaderDialog(final int cid) {
        // find volumeList
        VolumeList savedVolumeList = null;
        ChapterInfo chapterInfo = null;
        for (VolumeList vl : listVolume) {
            for (ChapterInfo ci : vl.chapterList) {
                if (ci.cid == cid) {
                    chapterInfo = ci;
                    savedVolumeList = vl;
                    break;
                }
            }
        }
        // no sufficient info
        if (savedVolumeList == null) {
            Toast.makeText(this, getResources().getText(R.string.reader_msg_no_available_chapter), Toast.LENGTH_SHORT).show();
            return;
        }
        final VolumeList volumeList_bak = savedVolumeList;

        new MaterialDialog.Builder(this)
                .onPositive((ignored1, ignored2) -> {
                    // jump to reader activity
                    Intent intent = new Intent(NovelInfoActivity.this, Wenku8ReaderActivityV1.class);
                    intent.putExtra("aid", aid);
                    intent.putExtra("volume", volumeList_bak);
                    intent.putExtra("cid", cid);

                    // test does file exist
                    if (from.equals(FromLocal)
                            && !LightCache.testFileExist(GlobalConfig.getDefaultStoragePath() + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + cid + ".xml")
                            && !LightCache.testFileExist(GlobalConfig.getBackupStoragePath() + GlobalConfig.saveFolderName + File.separator + "novel" + File.separator + cid + ".xml")) {
                        intent.putExtra("from", "cloud"); // from cloud
                    } else {
                        intent.putExtra("from", from); // from "fav"
                    }

                    intent.putExtra("forcejump", "yes");
                    startActivity(intent);
                    overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                })
                .theme(Theme.LIGHT)
                .titleColorRes(R.color.default_text_color_black)
                .backgroundColorRes(R.color.dlgBackgroundColor)
                .contentColorRes(R.color.dlgContentColor)
                .positiveColorRes(R.color.dlgPositiveButtonColor)
                .negativeColorRes(R.color.dlgNegativeButtonColor)
                .title(R.string.reader_v1_notice)
                .content(getResources().getString(R.string.reader_jump_last) + "\n" + title + "\n" + savedVolumeList.volumeName + "\n" + chapterInfo.chapterName)
                .contentGravity(GravityEnum.CENTER)
                .positiveText(R.string.dialog_positive_sure)
                .negativeText(R.string.dialog_negative_biao)
                .show();
    }

    @Override
    public void onBackPressed() {
        // end famMenu first
        if(famMenu.isExpanded()) {
            famMenu.collapse();
            return;
        }

        // normal exit
        if(Build.VERSION.SDK_INT < 21)
            finish();
        else
            finishAfterTransition(); // end directly
    }

    private class FetchInfoAsyncTask extends AsyncTask<Integer, Integer, Integer> {
        boolean fromLocal = false;

        @Override
        protected Integer doInBackground(Integer... params) {
            // transfer '1' to this task represent loading from local
            if(params != null && params.length == 1 && params[0] == 1)
                fromLocal = true;

            // get novel full meta
            try {
                if(fromLocal) {
                    novelFullMeta = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-intro.xml");
                    if(novelFullMeta.isEmpty()) return -9;
                }
                else {
                    ContentValues cv = Wenku8API.getNovelFullMeta(aid, GlobalConfig.getCurrentLang());
                    byte[] byteNovelFullMeta = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                    if (byteNovelFullMeta == null) return -1;
                    novelFullMeta = new String(byteNovelFullMeta, "UTF-8"); // save
                }
                mNovelItemMeta = Wenku8Parser.parseNovelFullMeta(novelFullMeta);
                if(mNovelItemMeta == null) return -1;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return -2;
            }
            publishProgress(1); // procedure 1/3

            // get novel full intro
            try {
                if(fromLocal) {
                    novelFullIntro = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-introfull.xml");
                    if(novelFullIntro.isEmpty()) return -9;
                }
                else {
                    ContentValues cvFullIntroRequest = Wenku8API.getNovelFullIntro(aid, GlobalConfig.getCurrentLang());
                    byte[] byteNovelFullInfo = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cvFullIntroRequest);
                    if (byteNovelFullInfo == null) return -1;
                    novelFullIntro = new String(byteNovelFullInfo, "UTF-8"); // save
                }
                mNovelItemMeta.fullIntro = novelFullIntro;
                if(mNovelItemMeta.fullIntro.length() == 0) return -1;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return -2;
            }
            publishProgress(2);

            // get novel chapter list
            try {
                if(fromLocal) {
                    novelFullVolume = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-volume.xml");
                    if(novelFullVolume.isEmpty()) return -9;
                }
                else {
                    ContentValues cv = Wenku8API.getNovelIndex(aid, GlobalConfig.getCurrentLang());
                    byte[] byteNovelChapterList = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                    if (byteNovelChapterList == null) return -1;
                    novelFullVolume = new String(byteNovelChapterList, "UTF-8"); // save
                }

                // update the volume list
                listVolume = Wenku8Parser.getVolumeList(novelFullVolume);
                if(listVolume.isEmpty()) return -1;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return -2;
            }
            publishProgress(3); // procedure 3/3

            // Check local volume files exists, express in another color
            for(VolumeList vl : listVolume) {
                for(ChapterInfo ci : vl.chapterList) {
                    if(!LightCache.testFileExist(GlobalConfig.getFirstFullSaveFilePath() + "novel" + File.separator + ci.cid + ".xml")
                            && !LightCache.testFileExist(GlobalConfig.getSecondFullSaveFilePath() + "novel" + File.separator + ci.cid + ".xml"))
                        break;

                    if(vl.chapterList.indexOf(ci) == vl.chapterList.size() - 1)
                        vl.inLocal = true;
                }
            }

            return 0;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            switch (values[0]) {
                case 1:
                    // update general info
                    tvNovelAuthor.setPaintFlags(tvNovelAuthor.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG); // with hyperlink
                    tvLatestChapter.setPaintFlags(tvLatestChapter.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG); // with hyperlink

                    tvNovelTitle.setText(mNovelItemMeta.title);
                    tvNovelAuthor.setText(mNovelItemMeta.author);
                    tvNovelStatus.setText(mNovelItemMeta.bookStatus);
                    tvNovelUpdate.setText(mNovelItemMeta.lastUpdate);
                    tvLatestChapter.setText(mNovelItemMeta.latestSectionName);
                    if(NovelInfoActivity.this.getSupportActionBar() != null)
                        NovelInfoActivity.this.getSupportActionBar().setTitle(mNovelItemMeta.title); // set action bar title
                    break;

                case 2:
                    //update novel info full
                    tvNovelFullIntro.setText(mNovelItemMeta.fullIntro);
                    break;

                case 3:
                    // let onPostExecute do
                    break;
            }
        }

        @Override
        protected void onPostExecute(Integer integer) {
            isLoading = false;
            spb.progressiveStop();
            super.onPostExecute(integer);

            if( integer == -1 ) {
                Toast.makeText(NovelInfoActivity.this, "FetchInfoAsyncTask:onPostExecute network error", Toast.LENGTH_SHORT).show();
                return;
            }
            else if(integer == -9) {
                Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.bookshelf_intro_load_failed), Toast.LENGTH_SHORT).show();
                // TODO: a better fix with optionCheckUpdates(), but need to avoid recursive calls.
                return;
            }
            else if(integer < 0)
                return; // ignore other exceptions
            buildVolumeList();
        }
    }

    private void buildVolumeList() {
      // remove all TextView(in CardView, in RelativeView)
      if(mLinearLayout.getChildCount() >= 3)
        mLinearLayout.removeViews(2, mLinearLayout.getChildCount() - 2);

      final GlobalConfig.ReadSavesV1 rs = GlobalConfig.getReadSavesRecordV1(aid);
      for(final VolumeList vl : listVolume) {
        // get view
        RelativeLayout rl = (RelativeLayout) LayoutInflater.from(NovelInfoActivity.this).inflate(R.layout.view_novel_chapter_item, null);
        // set text and listeners
        TextView tv = rl.findViewById(R.id.chapter_title);
        tv.setText(vl.volumeName);
        if(vl.inLocal)
          ((TextView) rl.findViewById(R.id.chapter_status)).setText(getResources().getString(R.string.bookshelf_inlocal));

        final RelativeLayout btn = rl.findViewById(R.id.chapter_btn);
        // added indicator for last read volume
        if (rs != null && rs.vid == vl.vid) {
          btn.setBackgroundColor(Color.LTGRAY);
        }
        btn.setOnLongClickListener(v -> {
          new MaterialDialog.Builder(NovelInfoActivity.this)
              .theme(Theme.LIGHT)
              .onPositive((ignored1, ignored2) -> {
                vl.cleanLocalCache();
                ((TextView) rl.findViewById(R.id.chapter_status)).setText("");
              })
              .content(R.string.dialog_sure_to_clear_cache)
              .positiveText(R.string.dialog_positive_want)
              .negativeText(R.string.dialog_negative_biao)
              .show();
          return true;
        });
        btn.setOnClickListener(v -> {
          // jump to chapter select activity
          Intent intent = new Intent(NovelInfoActivity.this, NovelChapterActivity.class);
          intent.putExtra("aid", aid);
          intent.putExtra("volume", vl);
          intent.putExtra("from", from);
          startActivity(intent);
        });

        // add to scroll view
        mLinearLayout.addView(rl);
      }
    }

    class AsyncUpdateCacheTask extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        // in: Aid, OperationType
        // out: current loading
        String volumeXml, introXml;
        List<VolumeList> vl = new ArrayList<>();
        private NovelItemMeta ni;
        int size_a = 0, current = 0;

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            if(params == null || params.length < 2) return Wenku8Error.ErrorCode.PARAM_COUNT_NOT_MATCHED;
            int taskAid = params[0];
            int operationType = params[1]; // type = 0, 1, 2, 3

            // get full range online, always
            try {
                // fetch intro
                if (!isLoading)
                    return Wenku8Error.ErrorCode.USER_CANCELLED_TASK; // cancel
                ContentValues cv = Wenku8API.getNovelIndex(taskAid, GlobalConfig.getCurrentLang());
                byte[] tempVolumeXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                if (tempVolumeXml == null) return Wenku8Error.ErrorCode.NETWORK_ERROR; // network error
                volumeXml = new String(tempVolumeXml, "UTF-8");

                if (!isLoading)
                    return Wenku8Error.ErrorCode.USER_CANCELLED_TASK; // cancel
                cv = Wenku8API.getNovelFullMeta(taskAid, GlobalConfig.getCurrentLang());
                byte[] tempIntroXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                if (tempIntroXml == null) return Wenku8Error.ErrorCode.NETWORK_ERROR; // network error
                introXml = new String(tempIntroXml, "UTF-8");

                // parse into structures
                vl = Wenku8Parser.getVolumeList(volumeXml);
                ni = Wenku8Parser.parseNovelFullMeta(introXml);
                if (vl.isEmpty() || ni == null) return Wenku8Error.ErrorCode.XML_PARSE_FAILED; // parse failed

                if (!isLoading)
                    return Wenku8Error.ErrorCode.USER_CANCELLED_TASK; // calcel
                cv = Wenku8API.getNovelFullIntro(ni.aid, GlobalConfig.getCurrentLang());
                byte[] tempFullIntro = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                if (tempFullIntro == null) return Wenku8Error.ErrorCode.NETWORK_ERROR; // network error
                ni.fullIntro = new String(tempFullIntro, "UTF-8");

                // write into saved file
                GlobalConfig.writeFullFileIntoSaveFolder("intro", taskAid + "-intro.xml", introXml);
                GlobalConfig.writeFullFileIntoSaveFolder("intro", taskAid + "-introfull.xml", ni.fullIntro);
                GlobalConfig.writeFullFileIntoSaveFolder("intro", taskAid + "-volume.xml", volumeXml);

            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return Wenku8Error.ErrorCode.SERVER_RETURN_NOTHING;
            }
            if(operationType == 0) return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED; // update info

            // calc size
            for (VolumeList tempVl : vl) {
                size_a += tempVl.chapterList.size();
            }
            pDialog.setMaxProgress(size_a);

            // cache each cid to save the whole book
            // and will need to download all the images
            for (VolumeList tempVl : vl) {
                for (ChapterInfo tempCi : tempVl.chapterList) {
                    try {
                        ContentValues cv = Wenku8API.getNovelContent(ni.aid, tempCi.cid, GlobalConfig.getCurrentLang());

                        // load from local first
                        if (!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK; // calcel
                        String xml = GlobalConfig.loadFullFileFromSaveFolder("novel", tempCi.cid + ".xml"); // prevent empty file
                        if (xml.length() == 0 || operationType == 2) {
                            byte[] tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                            if (tempXml == null) return Wenku8Error.ErrorCode.NETWORK_ERROR; // network error
                            xml = new String(tempXml, "UTF-8");
                            if(xml.trim().length() == 0) return Wenku8Error.ErrorCode.SERVER_RETURN_NOTHING;
                            GlobalConfig.writeFullFileIntoSaveFolder("novel", tempCi.cid + ".xml", xml);
                        }

                        // cache image
                        if (GlobalConfig.doCacheImage()) {
                            List<OldNovelContentParser.NovelContent> nc = OldNovelContentParser.NovelContentParser_onlyImage(xml);

                            for (int i = 0; i < nc.size(); i++) {
                                if (nc.get(i).type == NovelContentType.IMAGE) {
                                    pDialog.setMaxProgress(++size_a);

                                    // save this images, judge exist first
                                    String imgFileName = GlobalConfig
                                            .generateImageFileNameByURL(nc
                                                    .get(i).content);
                                    if (!LightCache.testFileExist(GlobalConfig.getFirstFullSaveFilePath()
                                            + GlobalConfig.imgsSaveFolderName + File.separator + imgFileName)
                                            && !LightCache.testFileExist(GlobalConfig.getSecondFullSaveFilePath()
                                            + GlobalConfig.imgsSaveFolderName + File.separator + imgFileName)
                                            || operationType == 2) {
                                        // neither of the file exist
                                        byte[] fileContent = LightNetwork.LightHttpDownload(nc.get(i).content);
                                        if (fileContent == null) return Wenku8Error.ErrorCode.NETWORK_ERROR; // network error
                                        if (!LightCache.saveFile(GlobalConfig.getFirstFullSaveFilePath()
                                                        + GlobalConfig.imgsSaveFolderName + File.separator,
                                                imgFileName, fileContent, true)) // fail
                                            // to first path
                                            LightCache.saveFile(GlobalConfig.getSecondFullSaveFilePath()
                                                            + GlobalConfig.imgsSaveFolderName + File.separator,
                                                    imgFileName, fileContent, true);
                                    }

                                    if (!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
                                    publishProgress(++current); // update
                                    // progress
                                }
                            }
                        }
                        publishProgress(++current); // update progress

                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }

            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        @Override
        protected void onProgressUpdate(Integer... values)
        {
            if (pDialog != null)
                pDialog.setProgress(values[0]);
        }

        protected void onPostExecute(Wenku8Error.ErrorCode result)
        {
            if (result == Wenku8Error.ErrorCode.USER_CANCELLED_TASK) {
                // user cancelled
                Toast.makeText(NovelInfoActivity.this, R.string.system_manually_cancelled, Toast.LENGTH_LONG).show();
                if (pDialog != null)
                    pDialog.dismiss();
                onResume();
                isLoading = false;
                return;
            } else if (result == Wenku8Error.ErrorCode.NETWORK_ERROR) {
                Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.system_network_error), Toast.LENGTH_LONG).show();
                if (pDialog != null)
                    pDialog.dismiss();
                onResume();
                isLoading = false;
                return;
            } else if (result == Wenku8Error.ErrorCode.XML_PARSE_FAILED
                    || result == Wenku8Error.ErrorCode.SERVER_RETURN_NOTHING) {
                Toast.makeText(NovelInfoActivity.this, "Server returned strange data! (copyright reason?)", Toast.LENGTH_LONG).show();
                if (pDialog != null)
                    pDialog.dismiss();
                onResume();
                isLoading = false;
                return;
            }

            // cache successfully
            Toast.makeText(NovelInfoActivity.this, "OK", Toast.LENGTH_LONG).show();
            isLoading = false;
            if (pDialog != null)
                pDialog.dismiss();

            refreshInfoFromLocal();
        }
    }

    class AsyncRemoveBookFromCloud extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        MaterialDialog md;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            md = new MaterialDialog.Builder(NovelInfoActivity.this)
                    .theme(Theme.LIGHT)
                    .content(R.string.dialog_content_novel_remove_from_cloud)
                    .contentColorRes(R.color.dlgContentColor)
                    .progress(true, 0)
                    .show();
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            // params: aid
            byte[] bytes = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getDelFromBookshelfParams(params[0]));
            if(bytes == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;

            String result;
            try {
                result = new String(bytes, "UTF-8");
                Log.d("MewX", result);
                if (!LightTool.isInteger(result))
                    return Wenku8Error.ErrorCode.RETURNED_VALUE_EXCEPTION;
                if(Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result)) != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED
                        && Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result)) != Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN
                        && Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result)) != Wenku8Error.ErrorCode.SYSTEM_7_NOVEL_NOT_IN_BOOKSHELF) {
                    return Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result));
                }
                else {
                    // remove from local bookshelf
                    // already in bookshelf
                    for (VolumeList tempVl : listVolume) {
                        for (ChapterInfo tempCi : tempVl.chapterList) {
                            LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "novel" + File.separator + tempCi.cid + ".xml");
                            LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "novel" + File.separator + tempCi.cid + ".xml");
                        }
                    }

                    // delete files
                    LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "intro" + File.separator + aid + "-intro.xml");
                    LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "intro" + File.separator + aid + "-introfull.xml");
                    LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "intro" + File.separator + aid + "-volume.xml");
                    LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "intro" + File.separator + aid + "-intro.xml");
                    LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "intro" + File.separator + aid + "-introfull.xml");
                    LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "intro" + File.separator + aid + "-volume.xml");

                    // remove from bookshelf
                    GlobalConfig.removeFromLocalBookshelf(aid);
                    if (!GlobalConfig.testInLocalBookshelf(aid)) { // not in
                        return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
                    } else {
                        return Wenku8Error.ErrorCode.LOCAL_BOOK_REMOVE_FAILED;
                    }
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return Wenku8Error.ErrorCode.BYTE_TO_STRING_EXCEPTION;
            }
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode err) {
            super.onPostExecute(err);

            md.dismiss();
            if(err == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.bookshelf_removed), Toast.LENGTH_SHORT).show();
                if(fabFavorite != null)
                    fabFavorite.setIcon(R.drawable.ic_favorate);
            }
            else
                Toast.makeText(NovelInfoActivity.this, err.toString(), Toast.LENGTH_SHORT).show();
        }
    }

    private class AsyncDownloadVolumes extends AsyncTask<Integer[], Integer, Wenku8Error.ErrorCode> {
        private MaterialDialog md;
        private boolean loading = false;
        private int size_a;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loading = true;
            md = new MaterialDialog.Builder(NovelInfoActivity.this)
                    .theme(Theme.LIGHT)
                    .content(R.string.dialog_content_downloading)
                    .progress(false, 1, true)
                    .cancelable(true)
                    .cancelListener(dialog -> loading = false)
                    .show();
            md.setProgress(0);
            md.setMaxProgress(1);
            size_a = 0;
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer[]... params) {
            // params[0] is the index list
            int current = 0;
            for(Integer idxVolume : params[0]) {
                // calc size
                size_a += listVolume.get(idxVolume).chapterList.size();

                for (ChapterInfo tempCi : listVolume.get(idxVolume).chapterList) {
                    try {
                        ContentValues cv = Wenku8API.getNovelContent(aid, tempCi.cid, GlobalConfig.getCurrentLang());

                        // load from local first
                        if (!loading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK; // cancel
                        String xml = GlobalConfig.loadFullFileFromSaveFolder("novel", tempCi.cid + ".xml"); // prevent empty file
                        if (xml.length() == 0) {
                            byte[] tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                            if (tempXml == null) return Wenku8Error.ErrorCode.NETWORK_ERROR; // network error
                            xml = new String(tempXml, "UTF-8");
                            if(xml.trim().length() == 0) return Wenku8Error.ErrorCode.SERVER_RETURN_NOTHING;
                            GlobalConfig.writeFullFileIntoSaveFolder("novel", tempCi.cid + ".xml", xml);
                        }

                        // cache image
                        if (GlobalConfig.doCacheImage()) {
                            List<OldNovelContentParser.NovelContent> nc = OldNovelContentParser.NovelContentParser_onlyImage(xml);

                            for (int i = 0; i < nc.size(); i++) {
                                if (nc.get(i).type == NovelContentType.IMAGE) {
                                    size_a ++;

                                    // save this images, judge exist first
                                    String imgFileName = GlobalConfig
                                            .generateImageFileNameByURL(nc
                                                    .get(i).content);
                                    if (!LightCache.testFileExist(GlobalConfig.getFirstFullSaveFilePath()
                                            + GlobalConfig.imgsSaveFolderName + File.separator + imgFileName)
                                            && !LightCache.testFileExist(GlobalConfig.getSecondFullSaveFilePath()
                                            + GlobalConfig.imgsSaveFolderName + File.separator + imgFileName)) {
                                        // neither of the file exist
                                        byte[] fileContent = LightNetwork.LightHttpDownload(nc.get(i).content);
                                        if (fileContent == null) return Wenku8Error.ErrorCode.NETWORK_ERROR; // network error
                                        if (!LightCache.saveFile(GlobalConfig.getFirstFullSaveFilePath()
                                                        + GlobalConfig.imgsSaveFolderName + File.separator,
                                                imgFileName, fileContent, true)) // fail
                                            // to first path
                                            LightCache.saveFile(GlobalConfig.getSecondFullSaveFilePath()
                                                            + GlobalConfig.imgsSaveFolderName + File.separator,
                                                    imgFileName, fileContent, true);
                                    }

                                    if (!loading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
                                    publishProgress(++current); // update
                                    // progress
                                }
                            }
                        }
                        publishProgress(++current); // update progress

                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                    }
                }
            }

            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            md.setMaxProgress(size_a);
            md.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);
            if (errorCode == Wenku8Error.ErrorCode.USER_CANCELLED_TASK) {
                // user cancelled
                Toast.makeText(NovelInfoActivity.this, R.string.system_manually_cancelled, Toast.LENGTH_LONG).show();
                if (md != null) md.dismiss();
                onResume();
                loading = false;
                return;
            } else if (errorCode == Wenku8Error.ErrorCode.NETWORK_ERROR) {
                Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.system_network_error), Toast.LENGTH_LONG).show();
                if (md != null) md.dismiss();
                onResume();
                loading = false;
                return;
            } else if (errorCode == Wenku8Error.ErrorCode.XML_PARSE_FAILED
                    || errorCode == Wenku8Error.ErrorCode.SERVER_RETURN_NOTHING) {
                Toast.makeText(NovelInfoActivity.this, "Server returned strange data! (copyright reason?)", Toast.LENGTH_LONG).show();
                if (md != null) md.dismiss();
                onResume();
                loading = false;
                return;
            }

            // cache successfully
            Toast.makeText(NovelInfoActivity.this, "OK", Toast.LENGTH_LONG).show();
            loading = false;
            if (md != null) md.dismiss();
            refreshInfoFromLocal();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // return from search activity
        final Drawable upArrow = getResources().getDrawable(R.drawable.ic_svg_back);
        if(getSupportActionBar() != null && upArrow != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            upArrow.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }

        // refresh when back from reader activity
        buildVolumeList();
    }

    private void refreshInfoFromLocal() {
        isLoading = true;
        spb.progressiveStart();
        FetchInfoAsyncTask fetchInfoAsyncTask = new FetchInfoAsyncTask();
        fetchInfoAsyncTask.execute(1); // load from local
    }

    private void refreshInfoFromCloud() {
        isLoading = true;
        spb.progressiveStart();
        FetchInfoAsyncTask fetchInfoAsyncTask = new FetchInfoAsyncTask();
        fetchInfoAsyncTask.execute();
    }
}
