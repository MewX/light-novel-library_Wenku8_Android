package org.mewx.wenku8.activity;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.content.ContextCompat;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;

import org.mewx.wenku8.BuildConfig;
import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.fragment.NavigationDrawerFragment;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightNetwork;
import org.mewx.wenku8.util.LightUserSession;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;


public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_WRITE_EXTERNAL = 100;
    private static final int REQUEST_READ_EXTERNAL = 101;

    private static final AtomicBoolean NEW_VERSION_CHECKED = new AtomicBoolean(false);

    // This is for fragment switch
    public enum FRAGMENT_LIST {
        RKLIST, LATEST, FAV, CONFIG
    }

    private FRAGMENT_LIST status = FRAGMENT_LIST.LATEST;

    public FRAGMENT_LIST getCurrentFragment() {
        return status;
    }

    public void setCurrentFragment(FRAGMENT_LIST f) {
        status = f;
    }

    private NavigationDrawerFragment mNavigationDrawerFragment;
    private static Boolean isExit = false; // used for exit by twice

    private void initialApp() {
        // load language
        Locale locale;
        switch (GlobalConfig.getCurrentLang()) {
            case SC:
                locale = Locale.SIMPLIFIED_CHINESE;
                break;
            case TC:
                locale = Locale.TRADITIONAL_CHINESE;
                break;
            default:
                locale = Locale.SIMPLIFIED_CHINESE;
                break;
        }
        Configuration config = new Configuration();
        config.locale = locale;
        Locale.setDefault(locale);
        getBaseContext().getResources().updateConfiguration(config, getBaseContext().getResources().getDisplayMetrics());

        // tint
        SystemBarTintManager tintManager = new SystemBarTintManager(this);
        tintManager.setTintAlpha(0.0f);

        // request write permission (112 write permission)
        boolean hasPermission = (ContextCompat.checkSelfPermission(this,
                Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
        if (!hasPermission) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL);
        }

        // request read permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            hasPermission = (ContextCompat.checkSelfPermission(this,
                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED);
            if (!hasPermission) {
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
        setContentView(R.layout.layout_main); // have 3 styles
        initialApp();

        // UIL setting
        if (ImageLoader.getInstance() == null || !ImageLoader.getInstance().isInited()) {
            GlobalConfig.initImageLoader(this);
        }

        // UMeng initialization
        UMConfigure.init(MyApp.getContext(), UMConfigure.DEVICE_TYPE_PHONE, null);

        // Update old save files ----------------


        // set Toolbar
        Toolbar mToolbar = findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        // getSupportActionBar().setDisplayShowHomeEnabled(true);

        // set Tool button
        mNavigationDrawerFragment = (NavigationDrawerFragment) getSupportFragmentManager().findFragmentById(R.id.fragment_drawer);
        mNavigationDrawerFragment.setup(R.id.fragment_drawer, findViewById(R.id.drawer), mToolbar);

        // find search box
        mToolbar.setOnMenuItemClickListener(item -> {
            //Toast.makeText(MyApp.getContext(),"called button",Toast.LENGTH_SHORT).show();
            if (item.getItemId() == R.id.action_search) {
                // start search activity
                startActivity(new Intent(MainActivity.this, SearchActivity.class));
                overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation

            }
            return true;
        });

        // change status bar color tint, and this require SDK16
        if (Build.VERSION.SDK_INT >= 16) { //&& Build.VERSION.SDK_INT <= 21) {
            // Android API 22 has more effects on status bar, so ignore

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
            if (Build.VERSION.SDK_INT >= 21)
                getWindow().setNavigationBarColor(getResources().getColor(R.color.myNavigationColor));
        }
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
            if (status == FRAGMENT_LIST.RKLIST)
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
            if (!NEW_VERSION_CHECKED.get()) new ArgsInitializer(MainActivity.this).execute();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_WRITE_EXTERNAL:
            case REQUEST_READ_EXTERNAL: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    //reload my activity with permission granted or use the features what required the permission
                    Intent i = getBaseContext().getPackageManager().getLaunchIntentForPackage(getBaseContext().getPackageName());
                    if (i != null) {
                        i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                        startActivity(i);
                    }
                } else {
                    Toast.makeText(this, getResources().getText(R.string.missing_permission), Toast.LENGTH_LONG).show();
                }
            }
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

    /**
     * this class is used for checking new versions and new notice text,
     * only when the app is loaded at the beginning
     */
    private static class ArgsInitializer extends AsyncTask<Void, Void, Void> {
        private final WeakReference<Context> contextReference;
        private int newVersionCode = 0;

        ArgsInitializer(Context c) {
            contextReference = new WeakReference<>(c);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected Void doInBackground(Void... voids) {
            // load new version
            byte[] codeByte = LightNetwork.LightHttpDownload(GlobalConfig.versionCheckUrl);
            if (codeByte != null) {
                String code = new String(codeByte);
                Log.d("MewX", "version code: " + code);
                if (!code.trim().isEmpty() && TextUtils.isDigitsOnly(code.trim())) {
                    newVersionCode = Integer.parseInt(code);
                }
            }

            // load parameters
            codeByte = LightNetwork.LightHttpDownload(
                    GlobalConfig.getCurrentLang() != Wenku8API.LANG.SC ?
                            GlobalConfig.noticeCheckTc : GlobalConfig.noticeCheckSc
            );
            if (codeByte != null) {
                String notice = new String(codeByte);
                Log.d("MewX", "notice text: " + notice);
                if (!notice.trim().isEmpty()) {
                    // update the latest string
                    Wenku8API.NoticeString = notice;
                    // save to local file
                    GlobalConfig.writeTheNotice(notice);
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            Context context = contextReference.get();
            if (context == null) return;

            // check whether there's new version
            int current = BuildConfig.VERSION_CODE;
            Log.d("MewX", "current version code: " + current);
            if (current < newVersionCode) {
                // update to new version
                new MaterialDialog.Builder(context)
                        .theme(Theme.LIGHT)
                        .title(R.string.system_update_found_new)
                        .content(R.string.system_update_jump_to_page)
                        .positiveText(R.string.dialog_positive_sure)
                        .negativeText(R.string.dialog_negative_no)
                        .negativeColorRes(R.color.menu_text_color)
                        .onPositive((dialog, which) -> {
                            Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(GlobalConfig.blogPageUrl));
                            context.startActivity(browserIntent);
                        })
                        .show();

            }
        }
    }
}
