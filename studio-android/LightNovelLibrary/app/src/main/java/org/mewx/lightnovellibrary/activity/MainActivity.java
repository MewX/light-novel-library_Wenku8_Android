package org.mewx.lightnovellibrary.activity;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;

import com.readystatesoftware.systembartint.SystemBarTintManager;

import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.fragment.NavigationDrawerFragment;
import org.mewx.lightnovellibrary.global.GlobalConfig;
import org.mewx.lightnovellibrary.util.LightCache;


public class MainActivity extends ActionBarActivity {
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
        return;
    }

    private Toolbar mToolbar;
    private NavigationDrawerFragment mNavigationDrawerFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_main); // have 3 styles

        // set context, activity itself is a context
        GlobalConfig.setContext((Context)this);

        // create save folder
        LightCache.saveFile(GlobalConfig.getFirstStoragePath() + "imgs",
                ".nomedia", "".getBytes(), false);
        LightCache.saveFile(GlobalConfig.getSecondStoragePath() + "imgs",
                ".nomedia", "".getBytes(), false);

        // set Toolbar
        mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        // getSupportActionBar().setDisplayShowHomeEnabled(true);

        // set Tool button
        mNavigationDrawerFragment = (NavigationDrawerFragment) getFragmentManager().findFragmentById(R.id.fragment_drawer);
        mNavigationDrawerFragment.setup(R.id.fragment_drawer, (DrawerLayout) findViewById(R.id.drawer), mToolbar);

        // change status bar color, and this require SDK16
        if (Build.VERSION.SDK_INT >= 16) {
            // create our manager instance after the content view is set
            SystemBarTintManager tintManager = new SystemBarTintManager(this);
            // enable all tint
            tintManager.setStatusBarTintEnabled(true);
            tintManager.setNavigationBarTintEnabled(true);
            // set all color
            tintManager.setTintColor(getResources().getColor(R.color.myPrimaryDarkColor));
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
            switch(status){
                case RKLIST:
                    if(getSupportActionBar()!=null)
                        getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_rklist));

                    break;

                case LATEST:
                    if(getSupportActionBar()!=null)
                        getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_latest));
                    getMenuInflater().inflate(R.menu.main, menu);
                    super.onCreateOptionsMenu(menu);
                    break;

                case FAV:
                    if(getSupportActionBar()!=null)
                        getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_fav));

                    break;

                case CONFIG:
                    if(getSupportActionBar()!=null)
                        getSupportActionBar().setTitle(getResources().getString(R.string.main_menu_config));

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
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.container, targetFragment, "fragment")
                .setTransitionStyle(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
                .commit();
    }

    @Override
    public void onBackPressed() {
        if (mNavigationDrawerFragment.isDrawerOpen())
            mNavigationDrawerFragment.closeDrawer();
        else
            super.onBackPressed();
    }
}
