package org.mewx.wenku8.activity;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.pm.PackageManager;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import org.mewx.wenku8.util.GoogleServicesHelper;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightCache;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;

/**
 * Created by MewX on 2015/7/28.
 * View large image activity.
 */
public class ViewImageDetailActivity extends BaseMaterialActivity {
    private static final String TAG = ViewImageDetailActivity.class.getSimpleName();

    private static final int WRITE_EXTERNAL_STORAGE_REQUEST = 100;

    private String path;
    private String fileName;
    private SubsamplingScaleImageView imageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_view_image_detail, StatusBarColor.DARK);

        // Init Firebase Analytics on GA4.
        GoogleServicesHelper.initFirebase(this);

        // fetch value
        path = getIntent().getStringExtra("path");
        fileName = path.contains("/") ? path.split("/")[path.split("/").length - 1] : "default.jpg";
        Log.d(TAG, "onCreate: path = " + path);
        Log.d(TAG, "onCreate: fileName = " + fileName);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(fileName);
        }

        // set image
        imageView = findViewById(R.id.image_scalable);
        imageView.setImage(ImageSource.uri(path));
        imageView.setMaxScale(4.0f);
        imageView.setOnClickListener(new View.OnClickListener() {
            private boolean shown = true;

            @Override
            public void onClick(View v) {
                if (shown) {
                    // hide
                    shown = false;
                    hideNavigationBar();
                    findViewById(R.id.toolbar_actionbar).setVisibility(View.INVISIBLE);
                    findViewById(R.id.image_detail_bot).setVisibility(View.INVISIBLE);
                    setStatusBarAlpha(0.0f);
                    setNavigationBarAlpha(0.0f);
                } else {
                    shown = true;
                    showNavigationBar();
                    findViewById(R.id.toolbar_actionbar).setVisibility(View.VISIBLE);
                    findViewById(R.id.image_detail_bot).setVisibility(View.VISIBLE);
                    setStatusBarAlpha(0.9f);
                    setNavigationBarAlpha(0.8f);
                }
            }
        });

        // set on click listeners
        findViewById(R.id.btn_rotate).setOnClickListener(v -> {
            switch (imageView.getOrientation()) {
                case SubsamplingScaleImageView.ORIENTATION_0:
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_90);
                    break;
                case SubsamplingScaleImageView.ORIENTATION_90:
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_180);
                    break;
                case SubsamplingScaleImageView.ORIENTATION_180:
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_270);
                    break;
                case SubsamplingScaleImageView.ORIENTATION_270:
                    imageView.setOrientation(SubsamplingScaleImageView.ORIENTATION_0);
                    break;
            }
        });
        findViewById(R.id.btn_rotate).setOnLongClickListener(v -> {
            Toast.makeText(ViewImageDetailActivity.this, getResources().getString(R.string.reader_rotate), Toast.LENGTH_SHORT).show();
            return true;
        });
        findViewById(R.id.btn_download).setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q &&
                    ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, WRITE_EXTERNAL_STORAGE_REQUEST);
            } else {
                performSaveImage();
            }
        });
        findViewById(R.id.btn_download).setOnLongClickListener(v -> {
            Toast.makeText(ViewImageDetailActivity.this, getResources().getString(R.string.reader_download), Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        showNavigationBar();
    }

    private void hideNavigationBar() {
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
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                decorView.setSystemUiVisibility(flags);
            }
        });
    }

    private void showNavigationBar() {
        // set navigation bar status, remember to disable "setNavigationBarTintEnabled"
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        getWindow().getDecorView().setSystemUiVisibility(flags);

        // Code below is to handle presses of Volume up or Volume down.
        // Without this, after pressing volume buttons, the navigation bar will
        // show up and won't hide
        final View decorView = getWindow().getDecorView();
        decorView.setOnSystemUiVisibilityChangeListener(visibility -> {
            if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                decorView.setSystemUiVisibility(flags);
            }
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(0, R.anim.fade_out);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == WRITE_EXTERNAL_STORAGE_REQUEST && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            performSaveImage();
        }
    }

    private void performSaveImage() {
        if (saveImageToGallery(path, fileName)) {
            Toast.makeText(ViewImageDetailActivity.this, "已保存： DCIM/wenku8/" + fileName, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(ViewImageDetailActivity.this, "保存失败", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean saveImageToGallery(String sourcePath, String fileName) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, fileName);
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg");
            contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + File.separator + "wenku8");
            contentValues.put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000);
            contentValues.put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis());

            ContentResolver resolver = getContentResolver();
            Uri imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
            if (imageUri == null) return false;

            try (OutputStream outputStream = resolver.openOutputStream(imageUri)) {
                byte[] data = LightCache.loadFile(sourcePath);
                if (data == null) return false;
                outputStream.write(data);
                return true;
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            File dcimDir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), "wenku8");
            if (!dcimDir.exists() && !dcimDir.mkdirs()) return false;

            File destFile = new File(dcimDir, fileName);
            LightCache.copyFile(sourcePath, destFile.getAbsolutePath(), true);

            if (destFile.exists()) {
                MediaScannerConnection.scanFile(this, new String[]{destFile.getAbsolutePath()}, null, null);
                return true;
            }
            return false;
        }
    }
}
