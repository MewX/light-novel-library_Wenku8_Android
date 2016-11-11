package org.mewx.wenku8.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import com.nononsenseapps.filepicker.FilePickerActivity;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;

import java.util.ArrayList;

/**
 * Created by MewX on 2015/7/29.
 * Let user select a menu background.
 */
public class MenuBackgroundSelectorActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_menu_background_selector);

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

        // listeners
        findViewById(R.id.bg01).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_id, "1");
                MenuBackgroundSelectorActivity.this.finish();
            }
        });
        findViewById(R.id.bg02).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_id, "2");
                MenuBackgroundSelectorActivity.this.finish();
            }
        });
        findViewById(R.id.bg03).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_id, "3");
                MenuBackgroundSelectorActivity.this.finish();
            }
        });
        findViewById(R.id.bg04).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_id, "4");
                MenuBackgroundSelectorActivity.this.finish();
            }
        });
        findViewById(R.id.bg05).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_id, "5");
                MenuBackgroundSelectorActivity.this.finish();
            }
        });
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
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_bg_selector, menu);
        Drawable drawable = menu.getItem(0).getIcon();
        if (drawable != null) {
            drawable.mutate();
            drawable.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        else if (menuItem.getItemId() == R.id.action_find) {
            // load custom image
            Intent i = new Intent(this, FilePickerActivity.class);
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false);
            i.putExtra(FilePickerActivity.EXTRA_ALLOW_CREATE_DIR, true);
            i.putExtra(FilePickerActivity.EXTRA_MODE, FilePickerActivity.MODE_FILE);
            i.putExtra(FilePickerActivity.EXTRA_START_PATH,
                    GlobalConfig.pathPickedSave == null || GlobalConfig.pathPickedSave.length() == 0 ?
                            Environment.getExternalStorageDirectory().getPath() : GlobalConfig.pathPickedSave);
            startActivityForResult(i, 0);
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 0 && resultCode == Activity.RESULT_OK) {
            // get ttf path
            if (data.getBooleanExtra(FilePickerActivity.EXTRA_ALLOW_MULTIPLE, false)) {
                // For JellyBean and above
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                    ClipData clip = data.getClipData();
                    if (clip != null) {
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            Uri uri = clip.getItemAt(i).getUri();
                            // Do something with the URI
                            runSaveCustomMenuBackground(uri.toString().replaceAll("file://", ""));
                        }
                    }
                    // For Ice Cream Sandwich
                } else {
                    ArrayList<String> paths = data.getStringArrayListExtra(FilePickerActivity.EXTRA_PATHS);
                    if (paths != null) {
                        for (String path: paths) {
                            Uri uri = Uri.parse(path);
                            // Do something with the URI
                            runSaveCustomMenuBackground(uri.toString().replaceAll("file://", ""));
                        }
                    }
                }
            } else {
                Uri uri = data.getData();
                // Do something with the URI
                runSaveCustomMenuBackground(uri.toString().replaceAll("file://", ""));
            }
        }
    }

    private void runSaveCustomMenuBackground(String path) {
        BitmapFactory.Options options;
        try {
            Bitmap bitmap = BitmapFactory.decodeFile(path);
        } catch (OutOfMemoryError oome) {
            try {
                options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                if(bitmap == null) throw new Exception("PictureDecodeFailedException");
            } catch(Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Exception: " + e.toString(), Toast.LENGTH_SHORT).show();
                return;
            }
        }
        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_id, "0");
        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_path, path);
        finish();


        try {
            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inPreferredConfig = Bitmap.Config.RGB_565;
            opt.inPurgeable = true;
            opt.inInputShareable = true;
            Bitmap bm = BitmapFactory.decodeFile(path);
            if(bm == null) throw new Exception("PictureDecodeFailedException: " + path);
        }
        catch (Exception e) {
            Toast.makeText(this, "Exception: " + e.toString(), Toast.LENGTH_SHORT).show();
        }
    }
}
