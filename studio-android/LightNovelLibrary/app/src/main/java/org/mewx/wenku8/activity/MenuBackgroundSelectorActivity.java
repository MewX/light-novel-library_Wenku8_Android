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
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.nononsenseapps.filepicker.FilePickerActivity;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightCache;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by MewX on 2015/7/29.
 * Let user select a menu background.
 */
public class MenuBackgroundSelectorActivity extends BaseMaterialActivity {
    private static final Map<Integer, String> viewIdToSettingItemMap = new HashMap<Integer, String>() {{
        put(R.id.bg01, "1");
        put(R.id.bg02, "2");
        put(R.id.bg03, "3");
        put(R.id.bg04, "4");
        put(R.id.bg05, "5");
    }};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_menu_background_selector);

        // Init listeners.
        for (Integer id : viewIdToSettingItemMap.keySet()) {
            findViewById(id).setOnClickListener(v -> {
                GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_id, viewIdToSettingItemMap.get(id));
                MenuBackgroundSelectorActivity.this.finish();
            });
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
            if (Build.VERSION.SDK_INT >= 19) {
                Intent intent = new Intent();
                intent.setAction(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("image/*");
                startActivityForResult(intent, 1);
            } else {
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
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode != Activity.RESULT_OK) {
            // User cancelled action.
            return;
        }

        if (requestCode == 0) {
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
                } else {
                    ArrayList<String> paths = data.getStringArrayListExtra(FilePickerActivity.EXTRA_PATHS);
                    if (paths != null) {
                        for (String path : paths) {
                            Uri uri = Uri.parse(path);
                            // Do something with the URI
                            runSaveCustomMenuBackground(uri.toString().replaceAll("file://", ""));
                        }
                    }
                }
            } else {
                Uri uri = data.getData();
                // Do something with the URI
                if (uri != null) {
                    runSaveCustomMenuBackground(uri.toString().replaceAll("file://", ""));
                }
            }
        } else if (requestCode == 1) {
            // API >= 19, from System file picker.
            Uri mediaUri = data.getData();
            if (mediaUri == null || mediaUri.getPath() == null) {
                return; // shouldn't happen.
            }

            String copiedFilePath = GlobalConfig.getDefaultStoragePath() + GlobalConfig.customFolderName + File.separator + "menu_bg";
            try {
                LightCache.copyFile(getApplicationContext().getContentResolver().openInputStream(mediaUri), copiedFilePath, true);
                runSaveCustomMenuBackground(copiedFilePath.replaceAll("file://", ""));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
                Toast.makeText(this, "Exception: " + e, Toast.LENGTH_SHORT).show();
                // Failed to copy. Just ignore it.
            }
        }
    }

    private void runSaveCustomMenuBackground(String path) {
        BitmapFactory.Options options;
        try {
            BitmapFactory.decodeFile(path);
        } catch (OutOfMemoryError oome) {
            // Ooming, load the smaller bitmap.
            try {
                options = new BitmapFactory.Options();
                options.inSampleSize = 2;
                Bitmap bitmap = BitmapFactory.decodeFile(path, options);
                if(bitmap == null) throw new Exception("PictureDecodeFailedException");
            } catch(Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Exception: " + e, Toast.LENGTH_SHORT).show();
                return;
            }
        }

        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_id, "0");
        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_path, path);
        finish();
    }
}
