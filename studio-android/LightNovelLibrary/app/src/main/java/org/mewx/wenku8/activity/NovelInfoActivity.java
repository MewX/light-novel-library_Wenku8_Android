package org.mewx.wenku8.activity;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
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

import com.getbase.floatingactionbutton.FloatingActionButton;
import com.getbase.floatingactionbutton.FloatingActionsMenu;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.readystatesoftware.systembartint.SystemBarTintManager;

import net.tsz.afinal.core.AsyncTask;

import org.apache.http.NameValuePair;
import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemMeta;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.util.LightNetwork;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/5/13.
 */
public class NovelInfoActivity extends AppCompatActivity {

    // private vars
    private int aid = 1;
    private boolean isLoading = true;
    private Toolbar mToolbar = null;
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
    private FloatingActionButton fabFavorate = null;
    private FloatingActionButton fabDownload = null;
    private FloatingActionsMenu famMenu = null;
    private NovelItemMeta mNovelItemMeta = null;
    private List<VolumeList> listVolume = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_novel_info);

        // fetch values
        aid = getIntent().getIntExtra("aid", 1);

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

        // hide view and set colors
        ImageLoader.getInstance().displayImage(Wenku8API.getCoverURL(aid), ivNovelCover);
        tvNovelShortIntro.setVisibility(TextView.GONE);
        ibNovelOption.setVisibility(ImageButton.INVISIBLE);
        fabFavorate.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
        fabDownload.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
        llCardLayout.setBackgroundResource(R.color.menu_transparent);

        // fetch all info
        getSupportActionBar().setTitle(R.string.action_novel_info);
        isLoading = true;
        FetchInfoAsyncTask fetchInfoAsyncTask = new FetchInfoAsyncTask();
        fetchInfoAsyncTask.execute(aid);

        // set on click listeners
        mLinearLayout.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Collapse the fam 1
                if(famMenu.isExpanded())
                    famMenu.collapse();
                return false;
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
            }
        });
        fabDownload.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(isLoading) {
                    Toast.makeText(NovelInfoActivity.this, getResources().getString(R.string.system_loading_please_wait), Toast.LENGTH_SHORT).show();
                    return;
                }

                // goto downloading activity or verify downloading action
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
            onBackPressed();
        }
        return super.onOptionsItemSelected(menuItem);
    }


    private class FetchInfoAsyncTask extends AsyncTask<Integer, Integer, Integer> {
        @Override
        protected Integer doInBackground(Integer... params) {

            // get novel full meta
            List<NameValuePair> nvpMetaRequest = new ArrayList<NameValuePair>();
            nvpMetaRequest.add(Wenku8API.getNovelFullMeta(aid, GlobalConfig.getCurrentLang()));
            byte[] byteNovelFullMeta = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), nvpMetaRequest);
            if(byteNovelFullMeta == null) return -1;

            try {
                mNovelItemMeta = Wenku8Parser.parsetNovelFullMeta(new String(byteNovelFullMeta, "UTF-8"));
                if(mNovelItemMeta == null)
                    return -1;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return -2;
            }
            nvpMetaRequest = null;
            byteNovelFullMeta = null;
            publishProgress(1); // procedure 1/3

            // get novel full intro
            List<NameValuePair> nvpFullIntroRequest = new ArrayList<NameValuePair>();
            nvpFullIntroRequest.add(Wenku8API.getNovelFullIntro(aid, GlobalConfig.getCurrentLang()));
            byte[] byteNovelFullInfo = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), nvpFullIntroRequest);
            if(byteNovelFullInfo == null) return -1;

            try {
                mNovelItemMeta.fullIntro = new String(byteNovelFullInfo, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return -2;
            }
            nvpFullIntroRequest = null;
            byteNovelFullInfo = null;
            publishProgress(2);

            // get novel chapter list
            List<NameValuePair> nvpChapterListRequest = new ArrayList<NameValuePair>();
            nvpChapterListRequest.add(Wenku8API.getNovelIndex(aid, GlobalConfig.getCurrentLang()));
            byte[] byteNovelChapterList = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), nvpChapterListRequest);
            if(byteNovelChapterList == null) return -1;

            try {
                listVolume = Wenku8Parser.getVolumeList(new String(byteNovelChapterList, "UTF-8"));
                if(listVolume == null) return -1;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return -2;
            }
            nvpChapterListRequest = null;
            byteNovelChapterList = null;
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
            else if(integer < 0)
                return; // ignore other exceptions

            for(final VolumeList vl : listVolume) {
                // get view
                RelativeLayout rl = (RelativeLayout) LayoutInflater.from(NovelInfoActivity.this).inflate(R.layout.view_novel_chapter_item, null);

                // set text and listeners
                TextView tv = (TextView) rl.findViewById(R.id.chapter_title);
                tv.setText(vl.volumeName);
                tv.setOnTouchListener(new View.OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        // Collapse the fam2
                        if(famMenu.isExpanded())
                            famMenu.collapse();
                        return false;
                    }
                });
                tv.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // jump to chapter select activity
                        Intent intent = new Intent(NovelInfoActivity.this, NovelChapterActivity.class);
                        intent.putExtra("volume", vl);
                        startActivity(intent);
                    }
                });

                // add to scroll view
                mLinearLayout.addView(rl);
            }

            isLoading = false;
            super.onPostExecute(integer);
        }
    }
}
