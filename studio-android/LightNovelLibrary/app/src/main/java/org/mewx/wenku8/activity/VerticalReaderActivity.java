package org.mewx.wenku8.activity;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nostra13.universalimageloader.core.ImageLoader;
import com.umeng.analytics.MobclickAgent;

import org.apache.http.NameValuePair;
import org.mewx.wenku8.R;
import org.mewx.wenku8.component.ScrollViewNoFling;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.util.LightNetwork;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/6/6.
 */
public class VerticalReaderActivity extends AppCompatActivity {

    private int aid, vid, cid;
    private VolumeList volumeList= null;
    private ProgressDialog pDialog = null;
    private List<OldNovelContentParser.NovelContent> nc = null;
    private Typeface typeface;

    // Scroll runnable to last read position
    private Runnable runnableScroll = new Runnable() {
        @Override
        public void run() {
            ((ScrollViewNoFling) VerticalReaderActivity.this.findViewById(R.id.content_scrollview))
                    .scrollTo(0, GlobalConfig.getReadSavesRecord(cid,
                            ((LinearLayout) VerticalReaderActivity.this
                                    .findViewById(R.id.novel_content_layout))
                                    .getMeasuredHeight()));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.layout_vertical_reader_temp);

        // fetch values
        aid = getIntent().getIntExtra("aid", 1);
        volumeList = (VolumeList) getIntent().getSerializableExtra("volume");
        cid = getIntent().getIntExtra("cid",1);

        // get Novel Content
        typeface = Typeface.createFromAsset(getAssets(), "fonts/fzss-gbk.ttf");
        getNovelContent();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);

        // set navigation bar status, remember to disable "setNavigationBarTintEnabled"
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        // This work only for android 4.4+
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(flags);

            // Code below is to handle presses of Volume up or Volume down.
            // Without this, after pressing volume buttons, the navigation bar will
            // show up and won't hide
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        decorView.setSystemUiVisibility(flags);
                    }
                }
            });
        }
    }

    private void getNovelContent() {
        List<NameValuePair> targVar = new ArrayList<NameValuePair>();
        targVar.add(Wenku8API.getNovelContent(aid, cid, GlobalConfig.getCurrentLang()));

        final asyncNovelContentTask ast = new asyncNovelContentTask();
        ast.execute(targVar);

        pDialog = new ProgressDialog(this);
        pDialog.setTitle(getResources().getString(R.string.sorry_old_engine_preprocess));
        pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        pDialog.setCancelable(true);
        pDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                // TODO Auto-generated method stub
                ast.cancel(true);
                pDialog.dismiss();
                pDialog = null;
            }

        });
        pDialog.setMessage(getResources().getString(R.string.sorry_old_engine_merging));
        pDialog.setProgress(0);
        pDialog.setMax(1);
        pDialog.show();

        return;
    }

    class asyncNovelContentTask extends
            AsyncTask<List<NameValuePair>, Integer, Integer> {
        // fail return -1
        @Override
        protected Integer doInBackground(List<NameValuePair>... params) {

            try {
                String xml;
//                if (from.equals(BookshelfFragment.fromid))
//                    xml = GlobalConfig.loadFullFileFromSaveFolder("novel",
//                            currentCid + ".xml");
//                else {
                    byte[] tempXml = LightNetwork.LightHttpPost(
                            Wenku8API.getBaseURL(), params[0]);
                    if (tempXml == null)
                        return -100;
                    xml = new String(tempXml, "UTF-8");
//                }

                nc = OldNovelContentParser.parseNovelContent(xml, pDialog);
                if (nc == null || nc.size() == 0) {
                    Log.e("MewX-Main",
                            "getNullFromParser (NovelContentParser.parseNovelContent(xml);)");

                    // network error or parse failed
                    return -100;
                }

                return 0;
            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            return -1;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            return;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == -100) {
//                if (from.equals(BookshelfFragment.fromid)) {
//                    Toast.makeText(
//                            parentActivity,
//                            getResources().getString(
//                                    R.string.bookshelf_not_cached),
//                            Toast.LENGTH_LONG).show();
//
//                    new AlertDialog.Builder(parentActivity)
//                            .setTitle(
//                                    getResources()
//                                            .getString(
//                                                    R.string.bookshelf_did_not_find_cache))
//                            .setMessage(
//                                    getResources()
//                                            .getString(
//                                                    R.string.bookshelf_want_to_connect_to_Internet))
//                            .setPositiveButton(
//                                    "YES",
//                                    new android.content.DialogInterface.OnClickListener() {
//
//                                        @Override
//                                        public void onClick(
//                                                DialogInterface dialog,
//                                                int which) {
//                                            if (pDialog != null)
//                                                pDialog.dismiss();
//
//                                            // connect to the Internet to load
//                                            from = "";
//                                            List<NameValuePair> targVar = new ArrayList<NameValuePair>();
//                                            targVar.add(Wenku8Interface
//                                                    .getNovelContent(
//                                                            currentAid,
//                                                            currentCid,
//                                                            GlobalConfig
//                                                                    .getFetchLanguage()));
//
//                                            final asyncNovelContentTask ast = new asyncNovelContentTask();
//                                            ast.execute(targVar);
//                                            return;
//                                        }
//
//                                    })
//                            .setNegativeButton(
//                                    "NO",
//                                    new android.content.DialogInterface.OnClickListener() {
//
//                                        @Override
//                                        public void onClick(
//                                                DialogInterface dialog,
//                                                int which) {
//                                            onBackPressed();
//                                        }
//                                    }).show();
//                } else
                    Toast.makeText(VerticalReaderActivity.this,
                            getResources().getString(R.string.system_network_error),
                            Toast.LENGTH_LONG).show();
                if (pDialog != null)
                    pDialog.dismiss();
                return;
            }

            // generate listview to contain the texts and images
            // ListView lv = (ListView) parentActivity
            // .findViewById(R.id.content_list);
            // if (lv == null) {
            // Log.e("MewX", "NovelReaderActivity ListView == null!");
            // return;
            // }
            // lv.setDivider(null);
            // lv.setAdapter(new NovelContentAdapter(parentActivity, nc));
            // pDialog.setProgress(nc.size());

            // The abandoned way - dynamically addign textview into layout
            LinearLayout layout = (LinearLayout) VerticalReaderActivity.this
                    .findViewById(R.id.novel_content_layout);

            for (int i = 0; i < nc.size(); i++) {
                if (pDialog != null)
                    pDialog.setProgress(i);

                switch (nc.get(i).type) {
                    case 't':
                        TextView tempTV = new TextView(VerticalReaderActivity.this);
                        if (i == 0) {
                            tempTV.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                                    GlobalConfig.getShowTextSize() + 6);
                            Shader shader = new LinearGradient(0, 0, 0,
                                    tempTV.getTextSize(), 0xFF003399, 0xFF6699FF,
                                    Shader.TileMode.CLAMP);
                            tempTV.getPaint().setShader(shader);
                        } else {
                            tempTV.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                                    GlobalConfig.getShowTextSize());
                        }
                        tempTV.setText(nc.get(i).content);
                        tempTV.setTypeface(typeface);
                        tempTV.setTextColor(getResources().getColor(R.color.default_text_color_black));
                        tempTV.setPadding(GlobalConfig.getShowTextPaddingLeft(),
                                GlobalConfig.getShowTextPaddingTop(),
                                GlobalConfig.getShowTextPaddingRight(), 0);
                        layout.addView(tempTV);
                        break;

                    case 'i':
                        final ImageView tempIV = new ImageView(VerticalReaderActivity.this);
                        tempIV.setClickable(true);
                        tempIV.setAdjustViewBounds(true);
                        tempIV.setScaleType(ImageView.ScaleType.FIT_CENTER);// CENTER_INSIDE
                        tempIV.setPadding(0, GlobalConfig.getShowTextPaddingTop(),
                                0, 0);
                        tempIV.setImageResource(R.drawable.ic_empty_image); // default

                        // async loader
                        final String imgFileName = GlobalConfig
                                .generateImageFileNameByURL(nc.get(i).content);
                        final String path = GlobalConfig
                                .getAvailableNovolContentImagePath(imgFileName);

                        if (path != null) {
                            ImageLoader.getInstance().displayImage(
                                    "file://" + path, tempIV);

                            tempIV.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // TODO Auto-generated method stub
//                                    Intent intent = new Intent();
//                                    intent.setClass(VerticalReaderActivity.this,
//                                            NovelImageActivity.class);
//                                    intent.putExtra("path", path);
//                                    startActivity(intent);
                                    Toast.makeText(VerticalReaderActivity.this, getResources().getString(R.string.sorry_old_engine_no_image_preview), Toast.LENGTH_SHORT).show();
//                                    VerticalReaderActivity.this.overridePendingTransition(
//                                            R.anim.in_from_right, R.anim.keep);
                                }
                            });
                        } else {
                            // define another asynctask to load image
                            // need to access local var - tempIV
                            class asyncDownloadImage extends
                                    AsyncTask<String, Integer, String> {
                                @Override
                                protected String doInBackground(String... params) {
                                    GlobalConfig.saveNovelContentImage(params[0]);
                                    String name = GlobalConfig
                                            .generateImageFileNameByURL(params[0]);
                                    return GlobalConfig
                                            .getAvailableNovolContentImagePath(name);
                                }

                                @Override
                                protected void onPostExecute(final String result) {
                                    ImageLoader.getInstance().displayImage(
                                            "file://" + result, tempIV);

                                    tempIV.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
//                                            Intent intent(intent); = new Intent();
//                                            intent.setClass(VerticalReaderActivity.this,
//                                                    NovelImageActivity.class);
//                                            intent.putExtra("path", result);
                                            Toast.makeText(VerticalReaderActivity.this, getResources().getString(R.string.sorry_old_engine_no_image_preview), Toast.LENGTH_SHORT).show();
//                                            startActivity
//                                            VerticalReaderActivity.this
//                                                    .overridePendingTransition(
//                                                            R.anim.in_from_right,
//                                                            R.anim.keep);
                                        }
                                    });

                                    return;
                                }

                            }
                            asyncDownloadImage async = new asyncDownloadImage();
                            async.execute(nc.get(i).content);
                        }

                        layout.addView(tempIV);
                        break;

                }
            }

            // end loading dialog
            if (pDialog != null)
                pDialog.dismiss();

            // show dialog
            if (GlobalConfig.getReadSavesRecord(cid,
                    ((LinearLayout) VerticalReaderActivity.this
                            .findViewById(R.id.novel_content_layout))
                            .getMeasuredHeight()) > 100) {
                new AlertDialog.Builder(VerticalReaderActivity.this)
                        .setTitle(getResources().getString(R.string.sorry_old_engine_notify))
                        .setMessage(getResources().getString(R.string.sorry_old_engine_jump))
                        .setPositiveButton(
                                "YES",
                                new android.content.DialogInterface.OnClickListener() {

                                    @Override
                                    public void onClick(DialogInterface dialog,
                                                        int which) {
                                        // set scroll view
                                        Handler handler = new Handler();
                                        handler.postDelayed(runnableScroll, 200);

                                        Toast.makeText(
                                                VerticalReaderActivity.this,
                                                "Scroll to = "
                                                        + GlobalConfig
                                                        .getReadSavesRecord(
                                                                cid,
                                                                ((LinearLayout) VerticalReaderActivity.this
                                                                        .findViewById(R.id.novel_content_layout))
                                                                        .getMeasuredHeight()),
                                                Toast.LENGTH_SHORT).show();

                                    }

                                }).setNegativeButton("NO", null).show();
            }

            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);

        saveRecord();
        return;
    }

    private void saveRecord() {
        // cannot get height easily, except sum one by one
        GlobalConfig.addReadSavesRecord(cid,
                ((ScrollViewNoFling) VerticalReaderActivity.this
                        .findViewById(R.id.content_scrollview)).getScrollY(),
                ((LinearLayout) VerticalReaderActivity.this
                        .findViewById(R.id.novel_content_layout))
                        .getMeasuredHeight());
        return;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (pDialog != null)
            pDialog.dismiss();
        pDialog = null;
        return;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                ((LinearLayout) VerticalReaderActivity.this
                        .findViewById(R.id.novel_content_layout))
                        .setBackgroundColor(0xff666666);
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                ((LinearLayout) VerticalReaderActivity.this
                        .findViewById(R.id.novel_content_layout))
                        .setBackgroundColor(0xffeeeeee);
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
