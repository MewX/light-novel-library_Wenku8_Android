package org.mewx.wenku8.reader.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ContentValues;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.DisplayCutout;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.nononsenseapps.filepicker.FilePickerActivity;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.adw.library.widgets.discreteseekbar.DiscreteSeekBar;
import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.BaseMaterialActivity;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.ChapterInfo;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import org.mewx.wenku8.reader.loader.WenkuReaderLoaderXML;
import org.mewx.wenku8.reader.setting.WenkuReaderSettingV1;
import org.mewx.wenku8.reader.slider.SlidingAdapter;
import org.mewx.wenku8.reader.slider.SlidingLayout;
import org.mewx.wenku8.reader.slider.base.OverlappedSlider;
import org.mewx.wenku8.reader.view.WenkuReaderPageView;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightNetwork;
import org.mewx.wenku8.util.LightTool;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/7/10.
 * Novel Reader Engine V1.
 */
public class Wenku8ReaderActivityV1 extends BaseMaterialActivity {
    // constant
    static private final String FromLocal = "fav";

    private static final int REQUEST_FONT_PICKER_LEGACY = 0;
    private static final int REQUEST_IMAGE_PICKER_LEGACY = 1;
    private static final int REQUEST_FONT_PICKER = 100;
    private static final int REQUEST_IMAGE_PICKER = 101;

    // vars
    private FirebaseAnalytics mFirebaseAnalytics;
    private String from = "";
    private int aid, cid;
    private String forcejump;
    private VolumeList volumeList= null;
    private List<OldNovelContentParser.NovelContent> nc = new ArrayList<>();
    private RelativeLayout mSliderHolder;
    private SlidingLayout sl;
//    private int tempNavBarHeight;

    // components
    private SlidingPageAdapter mSlidingPageAdapter;
    private WenkuReaderLoader loader;
    private WenkuReaderSettingV1 setting;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_reader_swipe_temp, BaseMaterialActivity.StatusBarColor.DARK);

        // Init Firebase Analytics on GA4.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // fetch values
        aid = getIntent().getIntExtra("aid", 1);
        volumeList = (VolumeList) getIntent().getSerializableExtra("volume");
        cid = getIntent().getIntExtra("cid", 1);
        from = getIntent().getStringExtra("from");
        forcejump = getIntent().getStringExtra("forcejump");
        if(forcejump == null || forcejump.length() == 0) forcejump = "no";
//        tempNavBarHeight = LightTool.getNavigationBarSize(this).y;

        // Analysis.
        Bundle readerParams = new Bundle();
        readerParams.putString(FirebaseAnalytics.Param.ITEM_ID, "" + aid);
        readerParams.putString("chapter_id", "" + cid);
        readerParams.putString("from", from);
        readerParams.putString("jump_to_saved_page", forcejump);
        mFirebaseAnalytics.logEvent("reader_v1", readerParams);


        getTintManager().setTintAlpha(0.0f);
        if(getSupportActionBar() != null) {
            getSupportActionBar().setTitle(volumeList.volumeName);
        }

        // find views
        mSliderHolder = findViewById(R.id.slider_holder);

        // UIL setting
        if(ImageLoader.getInstance() == null || !ImageLoader.getInstance().isInited()) {
            GlobalConfig.initImageLoader(this);
        }

        // async tasks
        ContentValues cv = Wenku8API.getNovelContent(aid, cid, GlobalConfig.getCurrentLang());
        AsyncNovelContentTask ast = new AsyncNovelContentTask();
        ast.execute(cv);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if(findViewById(R.id.reader_bot).getVisibility() != View.VISIBLE)
            hideNavigationBar();
        else
            showNavigationBar();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_reader_v1, menu);

        Drawable drawable = menu.getItem(0).getIcon();
        if (drawable != null) {
            drawable.mutate();
            drawable.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
        }

        return true;
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        // Update display cutout area.
        if (Build.VERSION.SDK_INT >= 28) {
            DisplayCutout cutout = getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
            if (cutout != null) {
                LightTool.setDisplayCutout(
                        new Rect(cutout.getSafeInsetLeft(),
                                cutout.getSafeInsetTop(),
                                cutout.getSafeInsetRight(),
                                cutout.getSafeInsetBottom()));
            }
        }
    }

    private void hideNavigationBar() {
        // This work only for android 4.4+
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            // set navigation bar status, remember to disable "setNavigationBarTintEnabled"
            final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            getWindow().getDecorView().setSystemUiVisibility(flags);

            // Code below is to handle presses of Volume up or Volume down.
            // Without this, after pressing volume buttons, the navigation bar will
            // show up and won't hide
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.setSystemUiVisibility(flags);
                }
            });
        }
    }

    private void showNavigationBar() {
        // set navigation bar status, remember to disable "setNavigationBarTintEnabled"
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        // This work only for android 4.4+
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(flags);

            // Code below is to handle presses of Volume up or Volume down.
            // Without this, after pressing volume buttons, the navigation bar will
            // show up and won't hide
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
                if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                    decorView.setSystemUiVisibility(flags);
                }
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // save record
        if(mSlidingPageAdapter != null && loader != null) {
            loader.setCurrentIndex(mSlidingPageAdapter.getCurrentLastLineIndex());
            if (volumeList.chapterList.size() > 1 && volumeList.chapterList.get(volumeList.chapterList.size() - 1).cid == cid && mSlidingPageAdapter.getCurrentLastWordIndex() == loader.getCurrentStringLength() - 1)
                GlobalConfig.removeReadSavesRecordV1(aid);
            else
                GlobalConfig.addReadSavesRecordV1(aid, volumeList.vid, cid, mSlidingPageAdapter.getCurrentFirstLineIndex(), mSlidingPageAdapter.getCurrentFirstWordIndex());
        }
    }

    @Override
    public boolean dispatchKeyEvent(@NonNull KeyEvent event) {
        switch(event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                gotoNextPage();
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                gotoPreviousPage();
                return true;
        }
        return super.dispatchKeyEvent(event);
    }

    class SlidingPageAdapter extends SlidingAdapter<WenkuReaderPageView> {
        int firstLineIndex = 0; // line index of first index of this page
        int firstWordIndex = 0; // first index of this page
        int lastLineIndex = 0; // line index of last index of this page
        int lastWordIndex = 0; // last index of this page

        WenkuReaderPageView nextPage;
        WenkuReaderPageView previousPage;
        boolean isLoadingNext = false;
        boolean isLoadingPrevious = false;

        public SlidingPageAdapter(int begLineIndex, int begWordIndex) {
            super();

            // init values
            firstLineIndex = begLineIndex;
            firstWordIndex = begWordIndex;

            // check valid first
            if(firstLineIndex + 1 >= loader.getElementCount()) firstLineIndex = loader.getElementCount() - 1; // to last one
            loader.setCurrentIndex(firstLineIndex);
            if(firstWordIndex + 1 >= loader.getCurrentStringLength()) {
                firstLineIndex --;
                firstWordIndex = 0;
                if(firstLineIndex < 0) firstLineIndex = 0;
            }
        }

        @Override
        public View getView(View contentView, WenkuReaderPageView pageView) {
            Log.d("MewX", "-- slider getView");
            if (contentView == null)
                contentView = getLayoutInflater().inflate(R.layout.layout_reader_swipe_page, null);

            // prevent memory leak
            final RelativeLayout rl = contentView.findViewById(R.id.page_holder);
            rl.removeAllViews();
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            rl.addView(pageView, lp);

            return contentView;
        }

        public int getCurrentFirstLineIndex() {
            return firstLineIndex;
        }

        public int getCurrentFirstWordIndex() {
            return firstWordIndex;
        }

        public int getCurrentLastLineIndex() {
            return lastLineIndex;
        }

        public int getCurrentLastWordIndex() {
            return lastWordIndex;
        }

        public void setCurrentIndex(int lineIndex, int wordIndex) {
            firstLineIndex = lineIndex + 1 >= loader.getElementCount() ? loader.getElementCount() - 1 : lineIndex;
            loader.setCurrentIndex(firstLineIndex);
            firstWordIndex = wordIndex + 1 >= loader.getCurrentStringLength() ? loader.getCurrentStringLength() - 1 : wordIndex;

            WenkuReaderPageView temp = new WenkuReaderPageView(Wenku8ReaderActivityV1.this, firstLineIndex, firstWordIndex, WenkuReaderPageView.LOADING_DIRECTION.CURRENT);
            firstLineIndex = temp.getFirstLineIndex();
            firstWordIndex = temp.getFirstWordIndex();
            lastLineIndex = temp.getLastLineIndex();
            lastWordIndex = temp.getLastWordIndex();
        }

        @Override
        public boolean hasNext() {
            Log.d("MewX", "-- slider hasNext");
            loader.setCurrentIndex(lastLineIndex);
            return !isLoadingNext && loader.hasNext(lastWordIndex);
        }

        @Override
        protected void computeNext() {
            Log.d("MewX", "-- slider computeNext");
            // vars change to next
            //if(nextPage == null) return;

            nextPage = new WenkuReaderPageView(Wenku8ReaderActivityV1.this, lastLineIndex, lastWordIndex, WenkuReaderPageView.LOADING_DIRECTION.FORWARDS);
            firstLineIndex = nextPage.getFirstLineIndex();
            firstWordIndex = nextPage.getFirstWordIndex();
            lastLineIndex = nextPage.getLastLineIndex();
            lastWordIndex = nextPage.getLastWordIndex();
            printLog();
        }

        @Override
        protected void computePrevious() {
            Log.d("MewX", "-- slider computePrevious");
            // vars change to previous
//            if(previousPage == null) return;
//            loader.setCurrentIndex(firstLineIndex);

            WenkuReaderPageView previousPage = new WenkuReaderPageView(Wenku8ReaderActivityV1.this, firstLineIndex, firstWordIndex, WenkuReaderPageView.LOADING_DIRECTION.BACKWARDS);
            firstLineIndex = previousPage.getFirstLineIndex();
            firstWordIndex = previousPage.getFirstWordIndex();
            lastLineIndex = previousPage.getLastLineIndex();
            lastWordIndex = previousPage.getLastWordIndex();

            // reset first page
//            if(firstLineIndex == 0 && firstWordIndex == 0)
//                notifyDataSetChanged();
            printLog();
        }

        @Override
        public WenkuReaderPageView getNext() {
            Log.d("MewX", "-- slider getNext");
            nextPage = new WenkuReaderPageView(Wenku8ReaderActivityV1.this, lastLineIndex, lastWordIndex, WenkuReaderPageView.LOADING_DIRECTION.FORWARDS);
            return nextPage;
        }

        @Override
        public boolean hasPrevious() {
            Log.d("MewX", "-- slider hasPrevious");
            loader.setCurrentIndex(firstLineIndex);
            return !isLoadingPrevious && loader.hasPrevious(firstWordIndex);
        }

        @Override
        public WenkuReaderPageView getPrevious() {
            Log.d("MewX", "-- slider getPrevious");
            previousPage = new WenkuReaderPageView(Wenku8ReaderActivityV1.this, firstLineIndex, firstWordIndex, WenkuReaderPageView.LOADING_DIRECTION.BACKWARDS);
            return previousPage;
        }

        @Override
        public WenkuReaderPageView getCurrent() {
            Log.d("MewX", "-- slider getCurrent");
            WenkuReaderPageView temp = new WenkuReaderPageView(Wenku8ReaderActivityV1.this, firstLineIndex, firstWordIndex, WenkuReaderPageView.LOADING_DIRECTION.CURRENT);
            firstLineIndex = temp.getFirstLineIndex();
            firstWordIndex = temp.getFirstWordIndex();
            lastLineIndex = temp.getLastLineIndex();
            lastWordIndex = temp.getLastWordIndex();
            printLog();
            return temp;
        }

        private void printLog() {
            Log.d("MewX", "saved index: " + firstLineIndex + "(" + firstWordIndex + ") -> " + lastLineIndex + "(" + lastWordIndex + ") | Total: " + loader.getCurrentIndex() + " of " + (loader.getElementCount()-1) );
        }
    }


    class AsyncNovelContentTask extends AsyncTask<ContentValues, Integer, Wenku8Error.ErrorCode> {
        private MaterialDialog md;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            md = new MaterialDialog.Builder(Wenku8ReaderActivityV1.this)
                    .theme(WenkuReaderPageView.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                    .title(R.string.reader_please_wait)
                    .content(R.string.reader_engine_v1_parsing)
                    .progress(true, 0)
                    .cancelable(false)
                    .show();
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(ContentValues... params) {
            try {
                String xml;
                if (from.equals(FromLocal)) // or exist
                    xml = GlobalConfig.loadFullFileFromSaveFolder("novel", cid + ".xml");
                else {
                    byte[] tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, params[0]);
                    if (tempXml == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                    xml = new String(tempXml, "UTF-8");
                }

                nc = OldNovelContentParser.parseNovelContent(xml, null);
                if (nc.size() == 0)
                    return xml.length() == 0 ? Wenku8Error.ErrorCode.SERVER_RETURN_NOTHING : Wenku8Error.ErrorCode.XML_PARSE_FAILED;

                return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                return Wenku8Error.ErrorCode.STRING_CONVERSION_ERROR;
            }
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode result) {
            if (result != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                Toast.makeText(Wenku8ReaderActivityV1.this, result.toString(), Toast.LENGTH_LONG).show();
                if (md != null) md.dismiss();
                Wenku8ReaderActivityV1.this.finish(); // return friendly
                return;
            }
            Log.d("MewX", "-- 小说获取完成");

            // init components
            loader = new WenkuReaderLoaderXML(nc);
            setting = new WenkuReaderSettingV1();
            loader.setCurrentIndex(0);
            for(ChapterInfo ci : volumeList.chapterList) {
                // get chapter name
                if(ci.cid == cid) {
                    loader.setChapterName(ci.chapterName);
                    break;
                }
            }

            // config sliding layout
            mSlidingPageAdapter = new SlidingPageAdapter(0, 0);
            WenkuReaderPageView.setViewComponents(loader, setting, false);
            Log.d("MewX", "-- loader, setting 初始化完成");
            sl = new SlidingLayout(Wenku8ReaderActivityV1.this);
            ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            sl.setAdapter(mSlidingPageAdapter);
            sl.setSlider(new OverlappedSlider());
            sl.setOnTapListener(new SlidingLayout.OnTapListener() {
                boolean barStatus = false;
                boolean isSet = false;

                @Override
                public void onSingleTap(MotionEvent event) {
                    int screenWidth = getResources().getDisplayMetrics().widthPixels;
                    int screenHeight = getResources().getDisplayMetrics().heightPixels;
                    int x = (int) event.getX();
                    int y = (int) event.getY();

                    if(x > screenWidth / 3 && x < screenWidth * 2 / 3 && y > screenHeight / 3 && y < screenHeight * 2 / 3) {
                        // first init
                        if(!barStatus) {
                            showNavigationBar();
                            findViewById(R.id.reader_top).setVisibility(View.VISIBLE);
                            findViewById(R.id.reader_bot).setVisibility(View.VISIBLE);

                            if (Build.VERSION.SDK_INT >= 16 ) {
                                getTintManager().setStatusBarAlpha(0.90f);
                                getTintManager().setNavigationBarAlpha(0.80f); // TODO: fix bug
                            }
                            barStatus = true;

                            if(!isSet) {
                                // add action to each
                                findViewById(R.id.btn_daylight).setOnClickListener(v -> {
                                    // switch day/night mode
                                    WenkuReaderPageView.switchDayMode();
                                    WenkuReaderPageView.resetTextColor();
                                    mSlidingPageAdapter.restoreState(null, null);
                                    mSlidingPageAdapter.notifyDataSetChanged();
                                });
                                findViewById(R.id.btn_daylight).setOnLongClickListener(v -> {
                                    Toast.makeText(Wenku8ReaderActivityV1.this, getResources().getString(R.string.reader_daynight), Toast.LENGTH_SHORT).show();
                                    return true;
                                });

                                findViewById(R.id.btn_jump).setOnClickListener(new View.OnClickListener() {
                                    boolean isOpen = false;
                                    @Override
                                    public void onClick(View v) {
                                        // show jump dialog
                                        if(findViewById(R.id.reader_bot_settings).getVisibility() == View.VISIBLE
                                                || findViewById(R.id.reader_bot_seeker).getVisibility() == View.INVISIBLE) {
                                            isOpen = false;
                                            findViewById(R.id.reader_bot_settings).setVisibility(View.INVISIBLE);
                                        }
                                        if(!isOpen)
                                            findViewById(R.id.reader_bot_seeker).setVisibility(View.VISIBLE);
                                        else
                                            findViewById(R.id.reader_bot_seeker).setVisibility(View.INVISIBLE);
                                        isOpen = !isOpen;

                                        DiscreteSeekBar seeker = findViewById(R.id.reader_seekbar);
                                        seeker.setMin(1);
                                        seeker.setProgress(mSlidingPageAdapter.getCurrentFirstLineIndex() + 1); // bug here
                                        seeker.setMax(loader.getElementCount());
                                        seeker.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                                            @Override
                                            public void onProgressChanged(DiscreteSeekBar discreteSeekBar, int i, boolean b) { }

                                            @Override
                                            public void onStartTrackingTouch(DiscreteSeekBar discreteSeekBar) { }

                                            @Override
                                            public void onStopTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                                mSlidingPageAdapter.setCurrentIndex(discreteSeekBar.getProgress() - 1, 0);
                                                mSlidingPageAdapter.restoreState(null, null);
                                                mSlidingPageAdapter.notifyDataSetChanged();
                                            }
                                        });
                                    }
                                });
                                findViewById(R.id.btn_jump).setOnLongClickListener(v -> {
                                    Toast.makeText(Wenku8ReaderActivityV1.this, getResources().getString(R.string.reader_jump), Toast.LENGTH_SHORT).show();
                                    return true;
                                });

                                findViewById(R.id.btn_find).setOnClickListener(v -> {
                                    // show label page
                                    Toast.makeText(Wenku8ReaderActivityV1.this, "查找功能尚未就绪", Toast.LENGTH_SHORT).show();
                                });
                                findViewById(R.id.btn_find).setOnLongClickListener(v -> {
                                    Toast.makeText(Wenku8ReaderActivityV1.this, getResources().getString(R.string.reader_find), Toast.LENGTH_SHORT).show();
                                    return true;
                                });

                                findViewById(R.id.btn_config).setOnClickListener(new View.OnClickListener() {
                                    private boolean isOpen = false;
                                    @Override
                                    public void onClick(View v) {
                                        // show jump dialog
                                        if(findViewById(R.id.reader_bot_seeker).getVisibility() == View.VISIBLE
                                                || findViewById(R.id.reader_bot_settings).getVisibility() == View.INVISIBLE) {
                                            isOpen = false;
                                            findViewById(R.id.reader_bot_seeker).setVisibility(View.INVISIBLE);
                                        }
                                        if(!isOpen)
                                            findViewById(R.id.reader_bot_settings).setVisibility(View.VISIBLE);
                                        else
                                            findViewById(R.id.reader_bot_settings).setVisibility(View.INVISIBLE);
                                        isOpen = !isOpen;

                                        // set all listeners
                                        DiscreteSeekBar seekerFontSize = findViewById(R.id.reader_font_size_seeker),
                                                seekerLineDistance = findViewById(R.id.reader_line_distance_seeker),
                                                seekerParagraphDistance = findViewById(R.id.reader_paragraph_distance_seeker),
                                                seekerParagraphEdgeDistance = findViewById(R.id.reader_paragraph_edge_distance_seeker);

                                        seekerFontSize.setProgress(setting.getFontSize());
                                        seekerFontSize.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                                            @Override
                                            public void onProgressChanged(DiscreteSeekBar discreteSeekBar, int i, boolean b) { }

                                            @Override
                                            public void onStartTrackingTouch(DiscreteSeekBar discreteSeekBar) { }

                                            @Override
                                            public void onStopTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                                setting.setFontSize(discreteSeekBar.getProgress());
                                                WenkuReaderPageView.setViewComponents(loader, setting, false);
                                                mSlidingPageAdapter.restoreState(null, null);
                                                mSlidingPageAdapter.notifyDataSetChanged();
                                            }
                                        });

                                        seekerLineDistance.setProgress(setting.getLineDistance());
                                        seekerLineDistance.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                                            @Override
                                            public void onProgressChanged(DiscreteSeekBar discreteSeekBar, int i, boolean b) { }

                                            @Override
                                            public void onStartTrackingTouch(DiscreteSeekBar discreteSeekBar) { }

                                            @Override
                                            public void onStopTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                                setting.setLineDistance(discreteSeekBar.getProgress());
                                                WenkuReaderPageView.setViewComponents(loader, setting, false);
                                                mSlidingPageAdapter.restoreState(null, null);
                                                mSlidingPageAdapter.notifyDataSetChanged();
                                            }
                                        });

                                        seekerParagraphDistance.setProgress(setting.getParagraphDistance());
                                        seekerParagraphDistance.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                                            @Override
                                            public void onProgressChanged(DiscreteSeekBar discreteSeekBar, int i, boolean b) { }

                                            @Override
                                            public void onStartTrackingTouch(DiscreteSeekBar discreteSeekBar) { }

                                            @Override
                                            public void onStopTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                                setting.setParagraphDistance(discreteSeekBar.getProgress());
                                                WenkuReaderPageView.setViewComponents(loader, setting, false);
                                                mSlidingPageAdapter.restoreState(null, null);
                                                mSlidingPageAdapter.notifyDataSetChanged();
                                            }
                                        });

                                        seekerParagraphEdgeDistance.setProgress(setting.getPageEdgeDistance());
                                        seekerParagraphEdgeDistance.setOnProgressChangeListener(new DiscreteSeekBar.OnProgressChangeListener() {
                                            @Override
                                            public void onProgressChanged(DiscreteSeekBar discreteSeekBar, int i, boolean b) { }

                                            @Override
                                            public void onStartTrackingTouch(DiscreteSeekBar discreteSeekBar) { }

                                            @Override
                                            public void onStopTrackingTouch(DiscreteSeekBar discreteSeekBar) {
                                                setting.setPageEdgeDistance(discreteSeekBar.getProgress());
                                                WenkuReaderPageView.setViewComponents(loader, setting, false);
                                                mSlidingPageAdapter.restoreState(null, null);
                                                mSlidingPageAdapter.notifyDataSetChanged();
                                            }
                                        });

                                        findViewById(R.id.btn_custom_font).setOnClickListener(v1 -> new MaterialDialog.Builder(Wenku8ReaderActivityV1.this)
                                                .theme(WenkuReaderPageView.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                                .title(R.string.reader_custom_font)
                                                .items(R.array.reader_font_option)
                                                .itemsCallback((dialog, view, which, text) -> {
                                                    switch (which) {
                                                        case 0:
                                                            // system default
                                                            setting.setUseCustomFont(false);
                                                            WenkuReaderPageView.setViewComponents(loader, setting, false);
                                                            mSlidingPageAdapter.restoreState(null, null);
                                                            mSlidingPageAdapter.notifyDataSetChanged();
                                                            break;
                                                        case 1:
                                                            // choose a ttf/otf file
                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                                                Intent intent = new Intent();
                                                                intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                                                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                                                intent.setType("font/*");
                                                                startActivityForResult(intent, REQUEST_FONT_PICKER);
                                                            } else {
                                                                Intent i = new Intent(Wenku8ReaderActivityV1.this, FilePickerActivity.class);
                                                                i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
                                                                i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
                                                                i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
                                                                i.putExtra(FilePickerActivity.EXTRA_START_PATH,
                                                                        GlobalConfig.pathPickedSave == null || GlobalConfig.pathPickedSave.length() == 0 ?
                                                                                Environment.getExternalStorageDirectory().getPath() : GlobalConfig.pathPickedSave);
                                                                startActivityForResult(i, REQUEST_FONT_PICKER_LEGACY);
                                                            }
                                                            break;
                                                    }
                                                })
                                                .show());

                                        findViewById(R.id.btn_custom_background).setOnClickListener(v12 -> new MaterialDialog.Builder(Wenku8ReaderActivityV1.this)
                                                .theme(WenkuReaderPageView.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                                .title(R.string.reader_custom_background)
                                                .items(R.array.reader_background_option)
                                                .itemsCallback((dialog, view, which, text) -> {
                                                    switch (which) {
                                                        case 0:
                                                            // system default
                                                            setting.setPageBackgroundType(WenkuReaderSettingV1.PAGE_BACKGROUND_TYPE.SYSTEM_DEFAULT);
                                                            WenkuReaderPageView.setViewComponents(loader, setting, true);
                                                            mSlidingPageAdapter.restoreState(null, null);
                                                            mSlidingPageAdapter.notifyDataSetChanged();
                                                            break;
                                                        case 1:
                                                            // choose a image file
                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                                                                Intent intent = new Intent();
                                                                intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                                                                intent.addCategory(Intent.CATEGORY_OPENABLE);
                                                                intent.setType("image/*");
                                                                startActivityForResult(intent, REQUEST_IMAGE_PICKER);
                                                            } else {
                                                                Intent i = new Intent(Wenku8ReaderActivityV1.this, FilePickerActivity.class);
                                                                i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
                                                                i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
                                                                i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
                                                                i.putExtra(FilePickerActivity.EXTRA_START_PATH,
                                                                        GlobalConfig.pathPickedSave == null || GlobalConfig.pathPickedSave.length() == 0 ?
                                                                                Environment.getExternalStorageDirectory().getPath() : GlobalConfig.pathPickedSave);
                                                                startActivityForResult(i, REQUEST_IMAGE_PICKER_LEGACY);
                                                            }
                                                            break;
                                                    }
                                                })
                                                .show());
                                    }
                                });
                                findViewById(R.id.btn_config).setOnLongClickListener(v -> {
                                    Toast.makeText(Wenku8ReaderActivityV1.this, getResources().getString(R.string.reader_config), Toast.LENGTH_SHORT).show();
                                    return true;
                                });

                                findViewById(R.id.text_previous).setOnClickListener(v -> {
                                    // goto previous chapter
                                    for (int i = 0; i < volumeList.chapterList.size(); i++) {
                                        if (cid == volumeList.chapterList.get(i).cid) {
                                            // found self
                                            if (i == 0) {
                                                // no more previous
                                                Toast.makeText(Wenku8ReaderActivityV1.this, getResources().getString(R.string.reader_already_first_chapter), Toast.LENGTH_SHORT).show();
                                            } else {
                                                // jump to previous
                                                final int i_bak = i;
                                                new MaterialDialog.Builder(Wenku8ReaderActivityV1.this)
                                                        .onPositive((dialog, which) -> {
                                                            Intent intent = new Intent(Wenku8ReaderActivityV1.this, Wenku8ReaderActivityV1.class);
                                                            intent.putExtra("aid", aid);
                                                            intent.putExtra("volume", volumeList);
                                                            intent.putExtra("cid", volumeList.chapterList.get(i_bak - 1).cid);
                                                            intent.putExtra("from", from); // from cloud
                                                            startActivity(intent);
                                                            overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                                            Wenku8ReaderActivityV1.this.finish();
                                                        })
                                                        .theme(WenkuReaderPageView.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                                        .title(R.string.dialog_sure_to_jump_chapter)
                                                        .content(volumeList.chapterList.get(i_bak - 1).chapterName)
                                                        .contentGravity(GravityEnum.CENTER)
                                                        .positiveText(R.string.dialog_positive_yes)
                                                        .negativeText(R.string.dialog_negative_no)
                                                        .show();
                                            }
                                            break;
                                        }
                                    }
                                });

                                findViewById(R.id.text_next).setOnClickListener(v -> {
                                    // goto next chapter
                                    for (int i = 0; i < volumeList.chapterList.size(); i++) {
                                        if (cid == volumeList.chapterList.get(i).cid) {
                                            // found self
                                            if (i + 1 >= volumeList.chapterList.size()) {
                                                // no more previous
                                                Toast.makeText(Wenku8ReaderActivityV1.this, getResources().getString(R.string.reader_already_last_chapter), Toast.LENGTH_SHORT).show();
                                            } else {
                                                // jump to previous
                                                final int i_bak = i;
                                                new MaterialDialog.Builder(Wenku8ReaderActivityV1.this)
                                                        .onPositive((dialog, which) -> {
                                                            Intent intent = new Intent(Wenku8ReaderActivityV1.this, Wenku8ReaderActivityV1.class);
                                                            intent.putExtra("aid", aid);
                                                            intent.putExtra("volume", volumeList);
                                                            intent.putExtra("cid", volumeList.chapterList.get(i_bak + 1).cid);
                                                            intent.putExtra("from", from); // from cloud
                                                            startActivity(intent);
                                                            overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                                            Wenku8ReaderActivityV1.this.finish();
                                                        })
                                                        .theme(WenkuReaderPageView.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                                        .title(R.string.dialog_sure_to_jump_chapter)
                                                        .content(volumeList.chapterList.get(i_bak + 1).chapterName)
                                                        .contentGravity(GravityEnum.CENTER)
                                                        .positiveText(R.string.dialog_positive_yes)
                                                        .negativeText(R.string.dialog_negative_no)
                                                        .show();
                                            }
                                            break;
                                        }
                                    }
                                });
                            }
                        }
                        else {
                            // show menu
                            hideNavigationBar();
                            findViewById(R.id.reader_top).setVisibility(View.INVISIBLE);
                            findViewById(R.id.reader_bot).setVisibility(View.INVISIBLE);
                            findViewById(R.id.reader_bot_seeker).setVisibility(View.INVISIBLE);
                            findViewById(R.id.reader_bot_settings).setVisibility(View.INVISIBLE);
                            if (Build.VERSION.SDK_INT >= 16 ) {
                                getTintManager().setStatusBarAlpha(0.0f);
                                getTintManager().setNavigationBarAlpha(0.0f);
                            }
                            barStatus = false;
                        }
                        return;
                    }

                    if (x > screenWidth / 2) {
                        gotoNextPage();
                    } else if (x <= screenWidth / 2) {
                        gotoPreviousPage();
                    }
                }
            });
            mSliderHolder.addView(sl, 0, lp);
            Log.d("MewX", "-- slider创建完毕");

            // end loading dialog
            if (md != null)
                md.dismiss();

            // show dialog, jump to last read position
            if (GlobalConfig.getReadSavesRecordV1(aid) != null) {
                final GlobalConfig.ReadSavesV1 rs = GlobalConfig.getReadSavesRecordV1(aid);
                if(rs != null && rs.vid == volumeList.vid && rs.cid == cid) {
                    if(forcejump.equals("yes")) {
                        mSlidingPageAdapter.setCurrentIndex(rs.lineId, rs.wordId);
                        mSlidingPageAdapter.restoreState(null, null);
                        mSlidingPageAdapter.notifyDataSetChanged();
                    } else if (mSlidingPageAdapter.getCurrentFirstLineIndex() != rs.lineId ||
                            mSlidingPageAdapter.getCurrentFirstWordIndex() != rs.wordId) {
                        // Popping up jump dialog only when the user didn't exist at the first page.
                        new MaterialDialog.Builder(Wenku8ReaderActivityV1.this)
                                .onPositive((dialog, which) -> {
                                    mSlidingPageAdapter.setCurrentIndex(rs.lineId, rs.wordId);
                                    mSlidingPageAdapter.restoreState(null, null);
                                    mSlidingPageAdapter.notifyDataSetChanged();
                                })
                                .theme(WenkuReaderPageView.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                .title(R.string.reader_v1_notice)
                                .content(R.string.reader_jump_last)
                                .contentGravity(GravityEnum.CENTER)
                                .positiveText(R.string.dialog_positive_sure)
                                .negativeText(R.string.dialog_negative_biao)
                                .show();
                    }
                }
            }
        }
    }

    private void gotoNextPage() {
        if(mSlidingPageAdapter != null && !mSlidingPageAdapter.hasNext()) {
            // goto next chapter
            for (int i = 0; i < volumeList.chapterList.size(); i++) {
                if (cid == volumeList.chapterList.get(i).cid) {
                    // found self
                    if (i + 1 >= volumeList.chapterList.size()) {
                        // no more previous
                        Toast.makeText(Wenku8ReaderActivityV1.this, getResources().getString(R.string.reader_already_last_chapter), Toast.LENGTH_SHORT).show();
                    } else {
                        // jump to previous
                        final int i_bak = i;
                        new MaterialDialog.Builder(Wenku8ReaderActivityV1.this)
                                .onPositive((dialog, which) -> {
                                    Intent intent = new Intent(Wenku8ReaderActivityV1.this, Wenku8ReaderActivityV1.class);
                                    intent.putExtra("aid", aid);
                                    intent.putExtra("volume", volumeList);
                                    intent.putExtra("cid", volumeList.chapterList.get(i_bak + 1).cid);
                                    intent.putExtra("from", from); // from cloud
                                    startActivity(intent);
                                    overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                    Wenku8ReaderActivityV1.this.finish();
                                })
                                .theme(WenkuReaderPageView.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                .title(R.string.dialog_sure_to_jump_chapter)
                                .content(volumeList.chapterList.get(i_bak + 1).chapterName)
                                .contentGravity(GravityEnum.CENTER)
                                .positiveText(R.string.dialog_positive_yes)
                                .negativeText(R.string.dialog_negative_no)
                                .show();
                    }
                    break;
                }
            }
        }
        else {
            if(sl != null)
                sl.slideNext();
        }
    }

    private void gotoPreviousPage() {
        if(mSlidingPageAdapter != null && !mSlidingPageAdapter.hasPrevious()) {
            // goto previous chapter
            for (int i = 0; i < volumeList.chapterList.size(); i++) {
                if (cid == volumeList.chapterList.get(i).cid) {
                    // found self
                    if (i == 0) {
                        // no more previous
                        Toast.makeText(Wenku8ReaderActivityV1.this, getResources().getString(R.string.reader_already_first_chapter), Toast.LENGTH_SHORT).show();
                    } else {
                        // jump to previous
                        final int i_bak = i;
                        new MaterialDialog.Builder(Wenku8ReaderActivityV1.this)
                                .onPositive((dialog, which) -> {
                                    Intent intent = new Intent(Wenku8ReaderActivityV1.this, Wenku8ReaderActivityV1.class);
                                    intent.putExtra("aid", aid);
                                    intent.putExtra("volume", volumeList);
                                    intent.putExtra("cid", volumeList.chapterList.get(i_bak - 1).cid);
                                    intent.putExtra("from", from); // from cloud
                                    startActivity(intent);
                                    overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
                                    Wenku8ReaderActivityV1.this.finish();
                                })
                                .theme(WenkuReaderPageView.getInDayMode() ? Theme.LIGHT : Theme.DARK)
                                .title(R.string.dialog_sure_to_jump_chapter)
                                .content(volumeList.chapterList.get(i_bak - 1).chapterName)
                                .contentGravity(GravityEnum.CENTER)
                                .positiveText(R.string.dialog_positive_yes)
                                .negativeText(R.string.dialog_negative_no)
                                .show();
                    }
                    break;
                }
            }
        }
        else {
            if(sl != null)
                sl.slidePrevious();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_FONT_PICKER_LEGACY && resultCode == Activity.RESULT_OK) {
            // get ttf path
            if (data.getBooleanExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)) {
                // For JellyBean and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clip = data.getClipData();
                    if (clip != null) {
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            Uri uri = clip.getItemAt(i).getUri();
                            // Do something with the URI
                            runSaveCustomFontPath(uri.toString().replaceAll("file://", ""));
                        }
                    }
                } else {
                    // For Ice Cream Sandwich
                    ArrayList<String> paths = data.getStringArrayListExtra(FilePickerActivity.EXTRA_PATHS);
                    if (paths != null) {
                        for (String path : paths) {
                            Uri uri = Uri.parse(path);
                            // Do something with the URI
                            runSaveCustomFontPath(uri.toString().replaceAll("file://", ""));
                        }
                    }
                }
            } else {
                Uri uri = data.getData();
                // Do something with the URI
                runSaveCustomFontPath(uri.toString().replaceAll("file://", ""));
            }
        } else if (requestCode == REQUEST_IMAGE_PICKER_LEGACY && resultCode == Activity.RESULT_OK) {
            // get image path
            if (data.getBooleanExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)) {
                // For JellyBean and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clip = data.getClipData();
                    if (clip != null) {
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            Uri uri = clip.getItemAt(i).getUri();
                            // Do something with the URI
                            runSaveCustomBackgroundPath(uri.toString().replaceAll("file://", ""));
                        }
                    }
                } else {
                    // For Ice Cream Sandwich
                    ArrayList<String> paths = data.getStringArrayListExtra(FilePickerActivity.EXTRA_PATHS);
                    if (paths != null) {
                        for (String path : paths) {
                            Uri uri = Uri.parse(path);
                            // Do something with the URI
                            runSaveCustomBackgroundPath(uri.toString().replaceAll("file://", ""));
                        }
                    }
                }
            } else {
                Uri uri = data.getData();
                // Do something with the URI
                runSaveCustomBackgroundPath(uri.toString().replaceAll("file://", ""));
            }
        } else if (requestCode == REQUEST_FONT_PICKER && resultCode == Activity.RESULT_OK && data != null) {
            Uri fontUri = data.getData();
            String copiedFilePath = GlobalConfig.getDefaultStoragePath() + GlobalConfig.customFolderName + File.separator + "reader_font";
            try {
                LightCache.copyFile(getApplicationContext().getContentResolver().openInputStream(fontUri), copiedFilePath, true);
                runSaveCustomFontPath(copiedFilePath.replaceAll("file://", ""));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, "Exception: " + e, Toast.LENGTH_SHORT).show();
                // Failed to copy. Just ignore it.
            }
        } else if (requestCode == REQUEST_IMAGE_PICKER && resultCode == Activity.RESULT_OK && data != null) {
            Uri mediaUri = data.getData();
            String copiedFilePath = GlobalConfig.getDefaultStoragePath() + GlobalConfig.customFolderName + File.separator + "reader_background";
            try {
                LightCache.copyFile(getApplicationContext().getContentResolver().openInputStream(mediaUri), copiedFilePath, true);
                runSaveCustomBackgroundPath(copiedFilePath.replaceAll("file://", ""));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, "Exception: " + e, Toast.LENGTH_SHORT).show();
                // Failed to copy. Just ignore it.
            }
        }
    }

    private void runSaveCustomFontPath(String path) {
        setting.setCustomFontPath(path);
        WenkuReaderPageView.setViewComponents(loader, setting, false);
        mSlidingPageAdapter.restoreState(null, null);
        mSlidingPageAdapter.notifyDataSetChanged();
    }

    private void runSaveCustomBackgroundPath(String path) {
        try {
            BitmapFactory.decodeFile(path);
        } catch (OutOfMemoryError oome) {
            try {
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                if (bitmap == null) throw new Exception("PictureDecodeFailedException");
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Exception: " + e + "\n可能的原因有：图片不在内置SD卡；图片格式不正确；图片像素尺寸太大，请使用小一点的图，谢谢，此功能为试验性功能；", Toast.LENGTH_LONG).show();
                return;
            }
        }
        setting.setPageBackgroundCustomPath(path);
        WenkuReaderPageView.setViewComponents(loader, setting, true);
        mSlidingPageAdapter.restoreState(null, null);
        mSlidingPageAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        switch (menuItem.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                break;
            case R.id.action_watch_image:
                if(sl != null && sl.getAdapter().getCurrentView() != null && ((RelativeLayout) sl.getAdapter().getCurrentView()).getChildAt(0) instanceof WenkuReaderPageView)
                    ((WenkuReaderPageView) ((RelativeLayout) sl.getAdapter().getCurrentView()).getChildAt(0)).watchImageDetailed(this);
                break;
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, R.anim.fade_out);
    }
}
