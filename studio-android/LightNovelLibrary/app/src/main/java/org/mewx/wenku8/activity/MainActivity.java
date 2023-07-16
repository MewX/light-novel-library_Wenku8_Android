package org.mewx.wenku8.activity;

import static android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;

import org.mewx.wenku8.BuildConfig;
import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.async.CheckAppNewVersion;
import org.mewx.wenku8.async.UpdateNotificationMessage;
import org.mewx.wenku8.fragment.NavigationDrawerFragment;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightUserSession;

import java.io.File;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity extends BaseMaterialActivity {
    // Below request codes can be any value.
    private static final int REQUEST_WRITE_EXTERNAL = 100;
    private static final int REQUEST_READ_EXTERNAL = 101;
    private static final int APP_STORAGE_ACCESS_REQUEST_CODE = 501;

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

        // Requests storage RW permissions (112 write permission).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            // Shows a dialog to confirm requesting for the permission.
            new MaterialDialog.Builder(MainActivity.this)
                    .theme(Theme.LIGHT)
                    .onPositive((ignored1, ignored2) -> {
                        Intent intent = new Intent(ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + BuildConfig.APPLICATION_ID));
                        startActivityForResult(intent, APP_STORAGE_ACCESS_REQUEST_CODE);
                    })
                    .onNegative((ignored1, ignored2) -> {
                        Toast.makeText(this, getResources().getText(R.string.missing_permission), Toast.LENGTH_LONG).show();
                    })
                    .content(R.string.dialog_content_ask_for_storage_permission)
                    .positiveText(R.string.dialog_positive_ok)
                    .negativeText(R.string.dialog_negative_preferno)
                    .show();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            // Write permission.
            if (missingPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL);
            }
            // Read permission.
            if (missingPermission(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_READ_EXTERNAL);
            }
        }

        // execute background action
        LightUserSession.aiui = new LightUserSession.AsyncInitUserInfo();
        LightUserSession.aiui.execute();
        GlobalConfig.loadAllSetting();

        // check new version and load notice text
        Wenku8API.NoticeString = GlobalConfig.loadSavedNotice();

        // create save folder
        LightCache.saveFile(GlobalConfig.getFirstStoragePath() + "imgs", ".nomedia", "".getBytes(), false);
        LightCache.saveFile(GlobalConfig.getSecondStoragePath() + "imgs", ".nomedia", "".getBytes(), false);
        LightCache.saveFile(GlobalConfig.getFirstStoragePath() + GlobalConfig.customFolderName, ".nomedia", "".getBytes(), false);
        LightCache.saveFile(GlobalConfig.getSecondStoragePath() + GlobalConfig.customFolderName, ".nomedia", "".getBytes(), false);
        GlobalConfig.setFirstStoragePathStatus(LightCache.testFileExist(GlobalConfig.getFirstStoragePath() + "imgs" + File.separator + ".nomedia"));
        // TODO: set status? tell app where is available
        LightCache.saveFile(GlobalConfig.getFirstFullSaveFilePath() + "imgs", ".nomedia", "".getBytes(), false);
        LightCache.saveFile(GlobalConfig.getSecondFullSaveFilePath() + "imgs", ".nomedia", "".getBytes(), false);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_main, HomeIndicatorStyle.HAMBURGER);
        initialApp();

        // UIL setting
        if (ImageLoader.getInstance() == null || !ImageLoader.getInstance().isInited()) {
            GlobalConfig.initImageLoader(this);
        }

        // UMeng initialization
        UMConfigure.init(MyApp.getContext(), UMConfigure.DEVICE_TYPE_PHONE, null);

        // Update old save files ----------------


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
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd("MainActivity");
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("MainActivity");
        MobclickAgent.onResume(this);

        // load only the first time this activity is created
        if (!NEW_VERSION_CHECKED.getAndSet(true)) {
            new CheckAppNewVersion(MainActivity.this).execute();
            new UpdateNotificationMessage().execute();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && requestCode == APP_STORAGE_ACCESS_REQUEST_CODE) {
            // Note that the result Code could either be OK or CANCELLED.
            if (Environment.isExternalStorageManager()) {
                // Permission granted.
                reloadApp();
            } else {
                Toast.makeText(this, getResources().getText(R.string.missing_permission), Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_WRITE_EXTERNAL:
            case REQUEST_READ_EXTERNAL:
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
                    if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                        reloadApp();
                    } else {
                        Toast.makeText(this, getResources().getText(R.string.missing_permission), Toast.LENGTH_LONG).show();
                    }
                }
                break;
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
