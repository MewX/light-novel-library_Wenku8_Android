package org.mewx.wenku8.activity;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.mewx.wenku8.R;
import org.mewx.wenku8.async.CheckAppNewVersion;
import org.mewx.wenku8.async.UpdateNotificationMessage;
import org.mewx.wenku8.fragment.NavigationDrawerFragment;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightUserSession;
import org.mewx.wenku8.util.SaveFileMigration;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity extends BaseMaterialActivity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final int EXTERNAL_SAVE_MIGRATION_API = Build.VERSION_CODES.Q; // Decrease only.

    // Below request codes can be any value.
    private static final int REQUEST_WRITE_EXTERNAL = 100;
    private static final int REQUEST_READ_EXTERNAL = 101;
    private static final int REQUEST_READ_MEDIA_IMAGES = 102;
    private static final int REQUEST_READ_EXTERNAL_SAVES = 103;

    private static final AtomicBoolean NEW_VERSION_CHECKED = new AtomicBoolean(false);

    // This is for fragment switch
    public enum FragmentMenuOption {
        RKLIST, LATEST, FAV, CONFIG
    }

    private FragmentMenuOption status = FragmentMenuOption.LATEST;

    public FragmentMenuOption getCurrentFragment() {
        return status;
    }

    public void setCurrentFragment(FragmentMenuOption f) {
        status = f;
    }

    private NavigationDrawerFragment mNavigationDrawerFragment;
    private FirebaseAnalytics mFirebaseAnalytics;
    private static Boolean isExit = false; // used for exit by twice

    private void initialApp() {
        // load language
        Locale locale;
        switch (GlobalConfig.getCurrentLang()) {
            case TC:
                locale = Locale.TRADITIONAL_CHINESE;
                break;
            case SC:
            default:
                locale = Locale.SIMPLIFIED_CHINESE;
                break;
        }
        Configuration config = new Configuration();
        config.locale = locale;
        Locale.setDefault(locale);
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());

        // Requests storage RW permissions only when save file migration is not done.
        if (SaveFileMigration.migrationCompleted()) {
            Log.i(TAG, "Save file migration has completed.");
        } else {
            // Write permission.
            if (missingPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL);
            }
        }

        // Read permissions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // FIXME: this doesn't work on the first launch yet (it works on the second+ launch somehow).
            if (missingPermission(Manifest.permission.READ_MEDIA_IMAGES)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQUEST_READ_MEDIA_IMAGES);
            }
        } else {
            if (missingPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL);
            }
        }

        // Create save folder.
        if (Build.VERSION.SDK_INT >= EXTERNAL_SAVE_MIGRATION_API) {
            // Does not support external storage if the file isn't already created.
            if (!LightCache.testFileExist(SaveFileMigration.getExternalStoragePath())) {
                GlobalConfig.setExternalStoragePathAvailable(false);
            }
            // Else, start migration.
        } else {
            // FIXME: these imgs folders are actually no in use.
            LightCache.saveFile(GlobalConfig.getDefaultStoragePath() + "imgs", ".nomedia", "".getBytes(), false);
            LightCache.saveFile(GlobalConfig.getDefaultStoragePath() + GlobalConfig.customFolderName, ".nomedia", "".getBytes(), false);
            LightCache.saveFile(GlobalConfig.getBackupStoragePath() + "imgs", ".nomedia", "".getBytes(), false);
            LightCache.saveFile(GlobalConfig.getBackupStoragePath() + GlobalConfig.customFolderName, ".nomedia", "".getBytes(), false);
            GlobalConfig.setExternalStoragePathAvailable(LightCache.testFileExist(SaveFileMigration.getExternalStoragePath() + "imgs" + File.separator + ".nomedia", true));
        }

        // execute background action
        LightUserSession.aiui = new LightUserSession.AsyncInitUserInfo();
        LightUserSession.aiui.execute();
        GlobalConfig.loadAllSetting();

        // check new version and load notice text
        Wenku8API.NoticeString = GlobalConfig.loadSavedNotice();
    }

    /**
     * For API 29+, migrate saves from external storage to internal storage.
     */
    private void startOldSaveMigration() {
        if (Build.VERSION.SDK_INT < EXTERNAL_SAVE_MIGRATION_API || SaveFileMigration.migrationCompleted()
                || (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                && missingPermission(Manifest.permission.READ_EXTERNAL_STORAGE))) {
            return;
        }

        // The permission issue for Android API 33+.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && SaveFileMigration.migrationEligible()) {
            Log.d(TAG, "startOldSaveMigration: Eligible");
            new MaterialDialog.Builder(MainActivity.this)
                    .theme(Theme.LIGHT)
                    .backgroundColorRes(R.color.dlgBackgroundColor)
                    .contentColorRes(R.color.dlgContentColor)
                    .positiveColorRes(R.color.dlgPositiveButtonColor)
                    .neutralColorRes(R.color.dlgNegativeButtonColor)
                    .content(R.string.system_save_need_to_migrate)
                    .positiveText(R.string.dialog_positive_ok)
                    .neutralText(R.string.dialog_negative_pass_for_now)
                    .onPositive((unused1, unused2) -> {
                        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                        intent.addCategory(Intent.CATEGORY_DEFAULT);
                        startActivityForResult(Intent.createChooser(intent, "Choose directory"), REQUEST_READ_EXTERNAL_SAVES);
                    })
                    .cancelable(false)
                    .show();

            // Return early to wait for the permissions grant on the directory.
            return;
        }

        // The rest Android Q+ devices.
        runExternalSaveMigration();
    }

    private void runExternalSaveMigration() {
        // Directly start migration dialog.
        List<Uri> filesToCopy = SaveFileMigration.generateMigrationPlan();

        // Analysis.
        Bundle saveMigrationFilesTotalParams = new Bundle();
        saveMigrationFilesTotalParams.putString("count", "" + filesToCopy.size());
        mFirebaseAnalytics.logEvent("save_migration_files_total", saveMigrationFilesTotalParams);

        if (filesToCopy.isEmpty()) {
            Log.d(TAG, "Empty list of files to copy");
            SaveFileMigration.markMigrationCompleted();
            return;
        }

        MaterialDialog progressDialog = new MaterialDialog.Builder(MainActivity.this)
                .theme(Theme.LIGHT)
                .content(R.string.system_save_upgrading)
                .progress(false, filesToCopy.size(), true)
                .cancelable(false)
                .show();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper()); // Handles the UI works.
        executor.execute(() -> {
            int progress = 0;
            int failedFiles = 0;
            for (Uri filePath : filesToCopy) {
                try {
                    String targetFilePath = SaveFileMigration.migrateFile(filePath);
                    if (!LightCache.testFileExist(targetFilePath, true)) {
                        Log.d(TAG, String.format("Failed migrating: %s (from %s)", targetFilePath, filePath));
                        failedFiles++;
                    }
                } catch (FileNotFoundException e) {
                    failedFiles++;
                    e.printStackTrace();
                }
                progress++;

                int finalProgress = progress;
                handler.post(() -> progressDialog.setProgress(finalProgress));
            }

            // Adding some delay to prevent UI crashing at the time of reloading.
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            int finalFailedFiles = failedFiles;

            // Analysis.
            Bundle saveMigrationFilesFailedParams = new Bundle();
            saveMigrationFilesFailedParams.putString("failed", "" + finalFailedFiles);
            mFirebaseAnalytics.logEvent("save_migration_files_failed", saveMigrationFilesFailedParams);

            handler.post(() -> {
                SaveFileMigration.markMigrationCompleted();
                progressDialog.dismiss();

                new MaterialDialog.Builder(MainActivity.this)
                        .theme(Theme.LIGHT)
                        .backgroundColorRes(R.color.dlgBackgroundColor)
                        .contentColorRes(R.color.dlgContentColor)
                        .positiveColorRes(R.color.dlgPositiveButtonColor)
                        .content(R.string.system_save_migrated, filesToCopy.size(), finalFailedFiles)
                        .positiveText(R.string.dialog_positive_sure)
                        .onPositive((unused1, unused2) -> reloadApp())
                        .cancelable(false)
                        .show();
            });
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_main, HomeIndicatorStyle.HAMBURGER);
        initialApp();

        // Init Firebase Analytics on GA4.
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(this);

        // UIL setting
        if (ImageLoader.getInstance() == null || !ImageLoader.getInstance().isInited()) {
            GlobalConfig.initImageLoader(this);
        }

        // Updates old save files.
        startOldSaveMigration();

        // set Tool button
        mNavigationDrawerFragment = (NavigationDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_drawer);
        if (mNavigationDrawerFragment != null) {
            mNavigationDrawerFragment.setup(R.id.fragment_drawer, findViewById(R.id.drawer), getToolbar());
        }

        // find search box
        getToolbar().setOnMenuItemClickListener(item -> {
            //Toast.makeText(MyApp.getContext(),"called button",Toast.LENGTH_SHORT).show();
            if (item.getItemId() == R.id.action_search) {
                // start search activity
                startActivity(new Intent(MainActivity.this, SearchActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation
            }
            return true;
        });
    }


    /**
     * Hard menu button works like the soft menu button.
     * And this will control all the menu appearance,
     * I can handle the button list by edit this function.
     *
     * @param menu The options menu in which you place your items, but I ignore this.
     * @return True if shown successfully, False if failed.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // only when the navigation draw closed, I draw the menu bar.
        // the menu items will be drawn automatically
        if (!mNavigationDrawerFragment.isDrawerOpen()) {
            // change title of toolbar
            switch (status) {
                case LATEST:
                    if (getSupportActionBar() != null)
                        getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_latest));
                    getMenuInflater().inflate(R.menu.menu_latest, menu);
                    break;
                case RKLIST:
                    if (getSupportActionBar() != null)
                        getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_rklist));
                    break;
                case FAV:
                    if (getSupportActionBar() != null)
                        getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_fav));
                    break;
                case CONFIG:
                    if (getSupportActionBar() != null)
                        getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_config));
                    break;
            }
        } else {
            if (getSupportActionBar() != null)
                getSupportActionBar().setTitle(getResources().getString(R.string.app_name));
        }

        return true;
    }

    /**
     * This function will be called by NavigationDrawerFragment,
     * once called, change fragment.
     *
     * @param targetFragment target fragment.
     */
    public void changeFragment(Fragment targetFragment) {
        // temporarily set elevation to remove rank list toolbar shadow
        if (getSupportActionBar() != null) {
            if (status == FragmentMenuOption.RKLIST)
                getSupportActionBar().setElevation(0);
            else
                getSupportActionBar().setElevation(getResources().getDimension(R.dimen.toolbar_elevation));
        }

        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, targetFragment, "fragment")
                .setTransitionStyle(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();

    }

    @Override
    protected void onResume() {
        super.onResume();

        // load only the first time this activity is created
        if (!NEW_VERSION_CHECKED.getAndSet(true)) {
            new CheckAppNewVersion(MainActivity.this).execute();
            new UpdateNotificationMessage().execute();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_WRITE_EXTERNAL:
            case REQUEST_READ_EXTERNAL:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    // The result will fall through.
                    break;
                }
            case REQUEST_READ_MEDIA_IMAGES:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    reloadApp();
                } else {
                    Toast.makeText(this, getResources().getText(R.string.missing_permission), Toast.LENGTH_LONG).show();
                }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_READ_EXTERNAL_SAVES && resultCode == Activity.RESULT_OK && data != null) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
                return;
            }

            Uri wenku8Uri = data.getData();
            String wenku8Path = wenku8Uri.getPath();
            if (!wenku8Uri.getLastPathSegment().endsWith("wenku8") || wenku8Path.contains("DCIM") || wenku8Path.contains("Picture")) {
                Log.i(TAG, "LastPathSegment: " + wenku8Uri.getLastPathSegment());
                Log.i(TAG, "Selected path for save migration doesn't look right: " + wenku8Uri);

                Bundle saveMigrationParams = new Bundle();
                saveMigrationParams.putString("path", wenku8Path);
                saveMigrationParams.putString("valid_path", "false");
                mFirebaseAnalytics.logEvent("save_migration_path_selection", saveMigrationParams);

                new MaterialDialog.Builder(MainActivity.this)
                        .theme(Theme.LIGHT)
                        .backgroundColorRes(R.color.dlgBackgroundColor)
                        .contentColorRes(R.color.dlgContentColor)
                        .positiveColorRes(R.color.dlgPositiveButtonColor)
                        .neutralColorRes(R.color.dlgNegativeButtonColor)
                        .negativeColorRes(R.color.myAccentColor)
                        .content(R.string.dialog_content_wrong_path, wenku8Path.replace("/tree/primary:", "/"))
                        .positiveText(R.string.dialog_positive_retry)
                        .neutralText(R.string.dialog_negative_pass_for_now)
                        .negativeText(R.string.dialog_negative_never)
                        .onPositive((unused1, unused2) -> reloadApp())
                        // Do nothing for onNeutral.
                        .onNegative((dialog, which) -> SaveFileMigration.markMigrationCompleted())
                        .cancelable(false)
                        .show();
                return;
            } else {
                Bundle saveMigrationParams = new Bundle();
                saveMigrationParams.putString("path", wenku8Path);
                saveMigrationParams.putString("valid_path", "true");
                mFirebaseAnalytics.logEvent("save_migration_path_selection", saveMigrationParams);
            }

            getContentResolver().takePersistableUriPermission(wenku8Uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);

            Log.i(TAG, "Selected the right directory: " + wenku8Path);
            // Overriding the external path is needed to help generating new paths.
            SaveFileMigration.overrideExternalPath(wenku8Uri);
            runExternalSaveMigration();
        }
    }

    @Override
    public void onBackPressed() {
        if (mNavigationDrawerFragment.isDrawerOpen())
            mNavigationDrawerFragment.closeDrawer();
        else
            exitBy2Click();
    }

    private void exitBy2Click() {
        // press twice to exit
        Timer tExit;
        if (!isExit) {
            isExit = true; // ready to exit
            Toast.makeText(
                    this,
                    this.getResources().getString(R.string.press_twice_to_exit),
                    Toast.LENGTH_SHORT).show();
            tExit = new Timer();
            tExit.schedule(new TimerTask() {
                @Override
                public void run() {
                    isExit = false; // cancel exit
                }
            }, 2000); // 2 seconds cancel exit task
        } else {
            finish();
        }
    }

    private boolean missingPermission(String permissionName) {
        return ContextCompat.checkSelfPermission(this, permissionName) != PackageManager.PERMISSION_GRANTED;
    }

    private void reloadApp() {
        //reload my activity with permission granted or use the features what required the permission
        Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
        if (i != null) {
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(i);
        }
    }

}
