package org.mewx.wenku8.activity;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;
import com.umeng.update.UmengUpdateAgent;
import com.umeng.update.UmengUpdateListener;
import com.umeng.update.UpdateResponse;
import com.umeng.update.UpdateStatus;

import org.mewx.wenku8.R;
import org.mewx.wenku8.fragment.NavigationDrawerFragment;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightCache;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;


public class MainActivity extends AppCompatActivity {
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

    private Toolbar mToolbar;
    private NavigationDrawerFragment mNavigationDrawerFragment;
    private static Boolean isExit = false; // used for exit by twice

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main); // have 3 styles

        // UIL setting
        if(ImageLoader.getInstance() == null || !ImageLoader.getInstance().isInited()) {
            GlobalConfig.initImageLoader(this);
        }

        // global settings
        GlobalConfig.initVolleyNetwork();

        // UMeng settings
        MobclickAgent.updateOnlineConfig(this);
        UmengUpdateAgent.setUpdateCheckConfig(false); // disable res check
        UmengUpdateAgent.setDeltaUpdate(true);
        UmengUpdateAgent.setUpdateOnlyWifi(false);
        if(!GlobalConfig.inAlphaBuild()) {
            // alpha version does not contains auto-update function
            UmengUpdateAgent.update(this);
//            Toast.makeText(this, "该软件尚处内测阶段，对外发布的为稳定版，带有检查更新功能。\n★请勿上传应用市场！\n☆内测群：427577610 有最新内测版", Toast.LENGTH_LONG).show();
        }
        else {
            // update dialog show up
            UmengUpdateAgent.setUpdateAutoPopup(false);
            UmengUpdateAgent.setUpdateListener(new UmengUpdateListener() {
                @Override
                public void onUpdateReturned(int updateStatus, final UpdateResponse updateInfo) {
                    switch (updateStatus) {
                        case UpdateStatus.Yes: // has update
                            if (UmengUpdateAgent.isIgnore(MainActivity.this, updateInfo)) {
                                //Toast.makeText(MainActivity.this, getResources().getString(R.string.system_update_ignored), Toast.LENGTH_SHORT).show();
                                break;
                            } else {
                                new MaterialDialog.Builder(MainActivity.this)
                                        .callback(new MaterialDialog.ButtonCallback() {
                                            @Override
                                            public void onNegative(MaterialDialog dialog) {
                                                super.onNegative(dialog);
                                                UmengUpdateAgent.ignoreUpdate(MainActivity.this, updateInfo);
                                            }
                                        })
                                        .forceStacking(true)
                                        .theme(Theme.LIGHT)
                                        .titleColor(R.color.default_text_color_black)
                                        .backgroundColorRes(R.color.dlgBackgroundColor)
                                        .contentColorRes(R.color.dlgContentColor)
                                        .positiveColorRes(R.color.dlgPositiveButtonColor)
                                        .negativeColorRes(R.color.dlgNegativeButtonColor)
                                        .title("New: " + updateInfo.version)
                                        .content(updateInfo.updateLog)
                                        .titleGravity(GravityEnum.CENTER)
                                        .positiveText(R.string.dialog_positive_gotit)
                                        .negativeText(R.string.dialog_negative_ignore_this_version)
                                        .show();
                            }
                            break;
                    }
                }
            });
            UmengUpdateAgent.update(this);
        }

        // Update old save files ----------------



        // set Toolbar
        mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        // getSupportActionBar().setDisplayShowHomeEnabled(true);

        // set Tool button
        mNavigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.fragment_drawer);
        mNavigationDrawerFragment.setup(R.id.fragment_drawer, (DrawerLayout) findViewById(R.id.drawer), mToolbar);

        // find search box
        mToolbar.setOnMenuItemClickListener(new Toolbar.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                //Toast.makeText(MyApp.getContext(),"called button",Toast.LENGTH_SHORT).show();
                if (item.getItemId() == R.id.action_search) {
                    // start search activity
                    startActivity(new Intent(MainActivity.this, SearchActivity.class));
                    overridePendingTransition(R.anim.fade_in, R.anim.hold); // fade in animation

                }
                return true;
            }
        });

        // change status bar color tint, and this require SDK16
        if (Build.VERSION.SDK_INT >= 16 ) { //&& Build.VERSION.SDK_INT <= 21) {
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
            if(Build.VERSION.SDK_INT >= 21)
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
        if(!mNavigationDrawerFragment.isDrawerOpen()) {
            // change title of toolbar
            switch(status){
                case LATEST:
                    if(getSupportActionBar()!=null) getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_latest));
                    getMenuInflater().inflate(R.menu.menu_latest, menu);
                    break;
                case RKLIST:
                    if(getSupportActionBar()!=null) getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_rklist));
                    break;
                case FAV:
                    if(getSupportActionBar()!=null) getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_fav));
                    break;
                case CONFIG:
                    if(getSupportActionBar()!=null) getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_config));
                    break;
            }
        }
        else {
            if(getSupportActionBar()!=null)
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
        if(status == FRAGMENT_LIST.RKLIST)
            getSupportActionBar().setElevation(0);
        else
            getSupportActionBar().setElevation(getResources().getDimension(R.dimen.toolbar_elevation));

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

        return;
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("MainActivity");
        MobclickAgent.onResume(this);

        return;
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
        Timer tExit = null;
        if (isExit == false) {
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
            // call fragments and end streams and services
            System.exit(0);
        }
    }
}
