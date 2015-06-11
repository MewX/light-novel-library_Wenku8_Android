package org.mewx.wenku8.activity;

import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.apache.http.NameValuePair;
import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.ChapterInfo;
import org.mewx.wenku8.global.api.NovelItemMeta;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightNetwork;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;

/**
 * Created by MewX on 2015/5/13.
 */
public class NovelInfoActivity extends AppCompatActivity {

    // constant
    private final String FromLocal = "fav";

    // private vars
    private int aid = 1;
    private String from = "";
    private boolean isLoading = true;
    private Toolbar mToolbar = null;
    private RelativeLayout rlMask = null; // mask layout
    private LinearLayout mLinearLayout = null;
    private LinearLayout llCardLayout = null;
    private ImageView ivNovelCover = null;
    private TextView tvNovelTitle = null;
    private TextView tvNovelAuthor = null;
    private TextView tvNovelStatus = null;
    private TextView tvNovelUpdate = null;
    private TableRow tvNovelShortIntro = null; // need hide
    private TextView tvNovelFullIntro = null;
    private ImageButton ibNovelOption = null; // need hide
    private MaterialDialog pDialog = null;
    private FloatingActionButton fabFavorate = null;
    private FloatingActionButton fabDownload = null;
    private FloatingActionsMenu famMenu = null;
    private SmoothProgressBar spb = null;
    private NovelItemMeta mNovelItemMeta = null;
    private List<VolumeList> listVolume = null;
    private String novelFullMeta = null, novelFullIntro = null, novelFullVolume = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_novel_info);

        // fetch values
        aid = getIntent().getIntExtra("aid", 1);
        from = getIntent().getStringExtra("from");

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

        // get views
        rlMask = (RelativeLayout) findViewById(R.id.white_mask);
        mLinearLayout = (LinearLayout) findViewById(R.id.novel_info_scroll);
        llCardLayout = (LinearLayout) findViewById(R.id.item_card);
        ivNovelCover = (ImageView) findViewById(R.id.novel_cover);
        tvNovelTitle = (TextView) findViewById(R.id.novel_title);
        tvNovelAuthor = (TextView) findViewById(R.id.novel_author);
        tvNovelStatus = (TextView) findViewById(R.id.novel_status);
        tvNovelUpdate = (TextView) findViewById(R.id.novel_update);
        tvNovelShortIntro = (TableRow) findViewById(R.id.novel_intro_row);
        tvNovelFullIntro = (TextView) findViewById(R.id.novel_intro_full);
        ibNovelOption = (ImageButton) findViewById(R.id.novel_option);
        fabFavorate = (FloatingActionButton) findViewById(R.id.fab_favorate);
        fabDownload = (FloatingActionButton) findViewById(R.id.fab_download);
        famMenu = (FloatingActionsMenu) findViewById(R.id.multiple_actions);
        spb = (SmoothProgressBar) findViewById(R.id.spb);

        // hide view and set colors
        ImageLoader.getInstance().displayImage(Wenku8API.getCoverURL(aid), ivNovelCover); // move to onCreateView!
        tvNovelShortIntro.setVisibility(TextView.GONE);
        ibNovelOption.setVisibility(ImageButton.INVISIBLE);
        fabFavorate.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
        fabDownload.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
        llCardLayout.setBackgroundResource(R.color.menu_transparent);
        spb.progressiveStart();
        if (GlobalConfig.testInLocalBookshelf(aid)) {
            fabFavorate.setIcon(R.drawable.ic_favorate_pressed);
        }

        // fetch all info
        getSupportActionBar().setTitle(R.string.action_novel_info);
        isLoading = true;
        FetchInfoAsyncTask fetchInfoAsyncTask = new FetchInfoAsyncTask();
        fetchInfoAsyncTask.execute(aid);

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
        rlMask.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Collapse the fam
                if (famMenu.isExpanded())
                    famMenu.collapse();
            }
        });
        fabFavorate.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isLoading) {
                    Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.system_loading_please_wait), Toast.LENGTH_SHORT).show();
                    return;
                }

                // add to favorate
                if(GlobalConfig.testInLocalBookshelf(aid)) {
                    new MaterialDialog.Builder(NovelInfoActivity.this)
                            .callback(new MaterialDialog.ButtonCallback() {
                                @Override
                                public void onPositive(MaterialDialog dialog) {
                                    super.onPositive(dialog);

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
                                        Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.bookshelf_removed), Toast.LENGTH_SHORT).show();
                                        fabFavorate.setIcon(R.drawable.ic_favorate);
                                    } else {
                                        Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.bookshelf_error), Toast.LENGTH_SHORT).show();
                                    }

                                }
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
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", aid + "-intro.xml", novelFullMeta);
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", aid + "-introfull.xml", novelFullIntro);
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", aid+ "-volume.xml", novelFullVolume);
                    GlobalConfig.addToLocalBookshelf(aid);
                    if (GlobalConfig.testInLocalBookshelf(aid)) { // in
                        Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.bookshelf_added), Toast.LENGTH_SHORT).show();
                        fabFavorate.setIcon(R.drawable.ic_favorate_pressed);
                    } else {
                        Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.bookshelf_error), Toast.LENGTH_SHORT).show();
                    }
                }
            }
        });
        fabDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isLoading) {
                    Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.system_loading_please_wait), Toast.LENGTH_SHORT).show();
                    return;
                }
                else if(!GlobalConfig.testInLocalBookshelf(aid)) {
                    Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.system_fav_it_first), Toast.LENGTH_SHORT).show();
                    return;
                }

                // download / update activity or verify downloading action (add to queue)
                // verify download first
                new MaterialDialog.Builder(NovelInfoActivity.this)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);

                                // async task
                                isLoading = true;
                                final AsyncUpdateCacheTask auct = new AsyncUpdateCacheTask();
                                auct.execute(aid);

                                // show progress
                                pDialog = new MaterialDialog.Builder(NovelInfoActivity.this)
                                        .theme(Theme.LIGHT)
                                        .content(R.string.dialog_content_downloading)
                                        .progress(false, 1, true)
                                        .cancelable(true)
                                        .cancelListener(new DialogInterface.OnCancelListener() {
                                            @Override
                                            public void onCancel(DialogInterface dialog) {
                                                isLoading = false;
                                                auct.cancel(true);
                                                pDialog.dismiss();
                                                pDialog = null;
                                            }
                                        })
                                        .show();

                                pDialog.setProgress(0);
                                pDialog.setMaxProgress(1);
                                pDialog.show();


                            }
                        })
                        .theme(Theme.LIGHT)
                        .backgroundColorRes(R.color.dlgBackgroundColor)
                        .contentColorRes(R.color.dlgContentColor)
                        .positiveColorRes(R.color.dlgPositiveButtonColor)
                        .negativeColorRes(R.color.dlgNegativeButtonColor)
                        .content(R.string.dialog_content_verify_download)
                        .contentGravity(GravityEnum.CENTER)
                        .positiveText(R.string.dialog_positive_likethis)
                        .negativeText(R.string.dialog_negative_preferno)
                        .show();
            }
        });

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if(getSupportActionBar()!=null)
            getSupportActionBar().setTitle(getResources().getString(R.string.action_novel_info));
        getMenuInflater().inflate(R.menu.menu_novel_info, menu);

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish(); // end directly
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        // end famMenu first
        if(famMenu.isExpanded()) {
            famMenu.collapse();
            return;
        }

        // normal exit
        super.onBackPressed();
    }

    private class FetchInfoAsyncTask extends AsyncTask<Integer, Integer, Integer> {
        @Override
        protected Integer doInBackground(Integer... params) {

            // get novel full meta
            try {
                if(from.equals(FromLocal)) {
                    novelFullMeta = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-intro.xml");
                    if(novelFullMeta == null || novelFullMeta.equals("")) return -9;
                }
                else {
                    List<NameValuePair> nvpMetaRequest = new ArrayList<NameValuePair>();
                    nvpMetaRequest.add(Wenku8API.getNovelFullMeta(aid, GlobalConfig.getCurrentLang()));
                    byte[] byteNovelFullMeta = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), nvpMetaRequest);
                    if (byteNovelFullMeta == null) return -1;
                    novelFullMeta = new String(byteNovelFullMeta, "UTF-8"); // save
                }
                mNovelItemMeta = Wenku8Parser.parsetNovelFullMeta(novelFullMeta);
                if(mNovelItemMeta == null) return -1;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return -2;
            }
            publishProgress(1); // procedure 1/3

            // get novel full intro
            try {
                if(from.equals(FromLocal)) {
                    novelFullIntro = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-introfull.xml");
                    if(novelFullIntro == null || novelFullIntro.equals("")) return -9;
                }
                else {
                    List<NameValuePair> nvpFullIntroRequest = new ArrayList<NameValuePair>();
                    nvpFullIntroRequest.add(Wenku8API.getNovelFullIntro(aid, GlobalConfig.getCurrentLang()));
                    byte[] byteNovelFullInfo = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), nvpFullIntroRequest);
                    if (byteNovelFullInfo == null) return -1;
                    novelFullIntro = new String(byteNovelFullInfo, "UTF-8"); // save
                }
                mNovelItemMeta.fullIntro = novelFullIntro;
                if(mNovelItemMeta.fullIntro == null) return -1;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return -2;
            }
            publishProgress(2);

            // get novel chapter list
            try {
                if(from.equals(FromLocal)) {
                    novelFullVolume = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-volume.xml");
                    if(novelFullVolume == null || novelFullVolume.equals("")) return -9;
                }
                else {
                    List<NameValuePair> nvpChapterListRequest = new ArrayList<NameValuePair>();
                    nvpChapterListRequest.add(Wenku8API.getNovelIndex(aid, GlobalConfig.getCurrentLang()));
                    byte[] byteNovelChapterList = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), nvpChapterListRequest);
                    if (byteNovelChapterList == null) return -1;
                    novelFullVolume = new String(byteNovelChapterList, "UTF-8"); // save
                }

                listVolume = Wenku8Parser.getVolumeList(novelFullVolume);
                if(listVolume == null) return -1;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return -2;
            }
            publishProgress(3); // procedure 3/3

            return 0;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);

            switch (values[0]) {
                case 1:
                    // update general info
                    tvNovelTitle.setText(mNovelItemMeta.title);
                    tvNovelAuthor.setText(mNovelItemMeta.author);
                    tvNovelStatus.setText(mNovelItemMeta.bookStatus);
                    tvNovelUpdate.setText(mNovelItemMeta.lastUpdate);
                    NovelInfoActivity.this.getSupportActionBar().setTitle(mNovelItemMeta.title); // set action bar title

                    break;

                case 2:
                    //update novel info full
                    tvNovelFullIntro.setText(mNovelItemMeta.fullIntro);

                    break;

                case 3:
                    // let onPostExecute do
                    break;
                default:
                    break;
            }

            return;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            if( integer == -1 ) {
                Toast.makeText(NovelInfoActivity.this, "FetchInfoAsyncTask:onPostExecute network error", Toast.LENGTH_SHORT).show();
                return;
            }
            else if(integer == -9) {
                Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.bookshelf_intro_load_failed), Toast.LENGTH_SHORT).show();
                return;
            }
            else if(integer < 0)
                return; // ignore other exceptions

            for(final VolumeList vl : listVolume) {
                // get view
                RelativeLayout rl = (RelativeLayout) LayoutInflater.from(NovelInfoActivity.this).inflate(R.layout.view_novel_chapter_item, null);

                // set text and listeners
                TextView tv = (TextView) rl.findViewById(R.id.chapter_title);
                tv.setText(vl.volumeName);
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // jump to chapter select activity
                        Intent intent = new Intent(NovelInfoActivity.this, NovelChapterActivity.class);
                        intent.putExtra("aid", aid);
                        intent.putExtra("volume", vl);
                        intent.putExtra("from", from);
                        startActivity(intent);
                    }
                });

                // add to scroll view
                mLinearLayout.addView(rl);
            }

            isLoading = false;
            spb.progressiveStop();
            super.onPostExecute(integer);
        }
    }

    class AsyncUpdateCacheTask extends AsyncTask<Integer, Integer, Integer> {
        // in: Aid
        // out: current loading
        String volumeXml, introXml;
        List<VolumeList> vl = null;
        List<String> imageList = null; // add one and save once
        private NovelItemMeta ni;
        int size_a = 0, current = 0;

        @Override
        protected Integer doInBackground(Integer... params) {
            for (Integer param : params) {
                // get full range online, always
                try {
                    // fetch intro
                    if (!isLoading)
                        return -222; // cancel
                    List<NameValuePair> targVarListVolume = new ArrayList<NameValuePair>();
                    targVarListVolume.add(Wenku8API.getNovelIndex(param, GlobalConfig.getCurrentLang()));
                    byte[] tempVolumeXml = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), targVarListVolume);
                    if (tempVolumeXml == null)
                        return -100; // network error
                    volumeXml = new String(tempVolumeXml, "UTF-8");

                    if (!isLoading)
                        return -222; // cancel
                    List<NameValuePair> targVarList = new ArrayList<NameValuePair>();
                    targVarList.add(Wenku8API.getNovelFullMeta(param, GlobalConfig.getCurrentLang()));
                    byte[] tempIntroXml = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), targVarList);
                    if (tempIntroXml == null)
                        return -100; // network error
                    introXml = new String(tempIntroXml, "UTF-8");

                    // parse into structures
                    vl = Wenku8Parser.getVolumeList(volumeXml);
                    ni = Wenku8Parser.parsetNovelFullMeta(introXml);
                    if (vl == null || ni == null)
                        return -101; // parse failed

                    if (!isLoading)
                        return -222; // calcel
                    List<NameValuePair> targIntro = new ArrayList<NameValuePair>();
                    targIntro.add(Wenku8API.getNovelFullIntro(ni.aid, GlobalConfig.getCurrentLang()));
                    byte[] tempFullIntro = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), targIntro);
                    if (tempFullIntro == null)
                        return -100; // network error
                    ni.fullIntro = new String(tempFullIntro, "UTF-8");

                    // write into saved file
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", param
                            + "-intro.xml", introXml);
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", param
                            + "-introfull.xml", ni.fullIntro);
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", param
                            + "-volume.xml", volumeXml);

                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }

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
                            List<NameValuePair> targVar = new ArrayList<NameValuePair>();
                            targVar.add(Wenku8API.getNovelContent(ni.aid, tempCi.cid, GlobalConfig.getCurrentLang()));

                            // load from local first
                            if (!isLoading)
                                return -222; // calcel
                            String xml = GlobalConfig
                                    .loadFullFileFromSaveFolder("novel",
                                            tempCi.cid + ".xml");
                            if (xml == null || xml.length() == 0) {
                                byte[] tempXml = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), targVar);
                                if (tempXml == null)
                                    return -100; // network error
                                xml = new String(tempXml, "UTF-8");

                                // save file (cid.xml), didn't format it
                                // future version may format it for better
                                // performance
                                GlobalConfig.writeFullFileIntoSaveFolder(
                                        "novel", tempCi.cid + ".xml", xml);
                            }

                            // cache image
                            if (GlobalConfig.doCacheImage()) {
                                List<OldNovelContentParser.NovelContent> nc = OldNovelContentParser.NovelContentParser_onlyImage(xml);
                                if (nc == null)
                                    return -100;

                                for (int i = 0; i < nc.size(); i++) {
                                    if (nc.get(i).type == 'i') {
                                        pDialog.setMaxProgress(++size_a);

                                        // save this images, judge exist first
                                        String imgFileName = GlobalConfig
                                                .generateImageFileNameByURL(nc
                                                        .get(i).content);
                                        if (!LightCache
                                                .testFileExist(GlobalConfig
                                                        .getFirstFullSaveFilePath()
                                                        + GlobalConfig.imgsSaveFolderName
                                                        + File.separator
                                                        + imgFileName)
                                                && !LightCache
                                                .testFileExist(GlobalConfig
                                                        .getSecondFullSaveFilePath()
                                                        + GlobalConfig.imgsSaveFolderName
                                                        + File.separator
                                                        + imgFileName)) {
                                            // neither of the file exist
                                            byte[] fileContent = LightNetwork
                                                    .LightHttpDownload(nc
                                                            .get(i).content);
                                            if (fileContent == null)
                                                return -100; // network error
                                            if (!LightCache
                                                    .saveFile(
                                                            GlobalConfig
                                                                    .getFirstFullSaveFilePath()
                                                                    + GlobalConfig.imgsSaveFolderName
                                                                    + File.separator,
                                                            imgFileName,
                                                            fileContent, true)) // fail
                                                // to
                                                // first
                                                // path
                                                LightCache
                                                        .saveFile(
                                                                GlobalConfig
                                                                        .getSecondFullSaveFilePath()
                                                                        + GlobalConfig.imgsSaveFolderName
                                                                        + File.separator,
                                                                imgFileName,
                                                                fileContent,
                                                                true);
                                        }

                                        if (!isLoading)
                                            return -222;
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
            }

            return 0;
        }

        @Override
        protected void onProgressUpdate(Integer... values)
        {
            if (pDialog != null)
                pDialog.setProgress(values[0]);
            return;
        }

        protected void onPostExecute(Integer result)// 执行耗时操作之后处理UI线程事件
        {
            if (result == -222) {
                // user cancelled
                Toast.makeText(NovelInfoActivity.this, "User cancelled!", Toast.LENGTH_LONG).show();
                if (pDialog != null)
                    pDialog.dismiss();
                onResume();
                return;
            } else if (result == -100) {
                Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.system_network_error), Toast.LENGTH_LONG).show();
                if (pDialog != null)
                    pDialog.dismiss();
                onResume();
                return;
            } else if (result == -101) {
                Toast.makeText(NovelInfoActivity.this, "Parse failed!", Toast.LENGTH_LONG).show();
                if (pDialog != null)
                    pDialog.dismiss();
                onResume();
                return;
            }

            // cache successfully
            Toast.makeText(NovelInfoActivity.this, "OK", Toast.LENGTH_LONG).show();
            if (pDialog != null)
                pDialog.dismiss();
            onResume();
            return;
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
}
