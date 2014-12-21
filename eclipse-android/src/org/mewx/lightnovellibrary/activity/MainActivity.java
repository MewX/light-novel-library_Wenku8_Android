/**
 *  Main Activity
 **
 *  This is the main activity, and it based on ResideMenu.
 *  Its duty is to initial essential conditions, and switch between fragments.
 **/

package org.mewx.lightnovellibrary.activity;

import java.io.File;
import java.util.Timer;
import java.util.TimerTask;

import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.util.LightCache;

import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiscCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;
import com.nostra13.universalimageloader.utils.StorageUtils;
import com.special.ResideMenu.ResideMenu;
import com.special.ResideMenu.ResideMenuItem;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.widget.Toast;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.graphics.Bitmap;

public class MainActivity extends FragmentActivity implements
		View.OnClickListener {

	private ResideMenu resideMenu;
	private ResideMenuItem itemLibrary;
	private ResideMenuItem itemBookshelf;
	private ResideMenuItem itemWenku8;
	private ResideMenuItem itemSettings;
	private View currentViewItemSave;
	private static Boolean isExit = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);

		// ImageLoader Configuration (ImageLoaderConfiguration) is global for
		// application. Display Options (DisplayImageOptions) are local for
		// every display.
		// task (ImageLoader.displayImage(...)).

		// Environment.getExternalStorageState() // can test SDcard exist
		Log.v("MewX", "dir0: " + Environment.getExternalStorageDirectory()
				+ File.separator + "wenku8" + File.separator + "imgs");
		Log.v("MewX", "dir1: " + getCacheDir() + File.separator + "imgs");
		Log.v("MewX", "dir2: " + getFilesDir() + File.separator + "imgs");
		LightCache.saveFile(GlobalConfig.getFirstStoragePath() + "imgs",
				".nomedia", "".getBytes(), false);
		LightCache.saveFile(GlobalConfig.getSecondStoragePath() + "imgs",
				".nomedia", "".getBytes(), false);

		// first: Environment.getExternalStorageDirectory(); then getCacheDir()
		UnlimitedDiscCache localUnlimitedDiscCache = new UnlimitedDiscCache(
				new File(GlobalConfig.getFirstStoragePath() + "cache"),
				new File(getCacheDir() + File.separator + "imgs"));
		DisplayImageOptions localDisplayImageOptions = new DisplayImageOptions.Builder()
				.resetViewBeforeLoading(true).cacheOnDisk(true)
				.cacheInMemory(true).bitmapConfig(Bitmap.Config.RGB_565)
				.resetViewBeforeLoading(true)
				.displayer(new FadeInBitmapDisplayer(250)).build();
		ImageLoaderConfiguration localImageLoaderConfiguration = new ImageLoaderConfiguration.Builder(
				this).diskCache(localUnlimitedDiscCache)
				.defaultDisplayImageOptions(localDisplayImageOptions).build();
		ImageLoader.getInstance().init(localImageLoaderConfiguration);

		// create menu items;
		setUpMenu();
		if (savedInstanceState == null) {
			changeFragment(new LibraryFragment());
			currentViewItemSave = itemLibrary;
		}

		return;
	}

	private void setUpMenu() {

		// attach to current activity;
		resideMenu = new ResideMenu(this);
		resideMenu.setBackground(R.drawable.menu_bg);
		resideMenu.attachToActivity(this);
		// resideMenu.setMenuListener(menuListener);
		resideMenu.setScaleValue(0.60f);

		// create menu items;
		itemLibrary = new ResideMenuItem(this, R.drawable.ic_library, this
				.getResources().getString(R.string.tab_library));
		itemBookshelf = new ResideMenuItem(this, R.drawable.ic_bookshelf, this
				.getResources().getString(R.string.tab_bookshelf));
		itemWenku8 = new ResideMenuItem(this, R.drawable.ic_wenku8, this
				.getResources().getString(R.string.tab_wenku8));
		itemSettings = new ResideMenuItem(this, R.drawable.ic_setting, this
				.getResources().getString(R.string.tab_setting));

		itemLibrary.setOnClickListener(this);
		itemBookshelf.setOnClickListener(this);
		itemWenku8.setOnClickListener(this);
		itemSettings.setOnClickListener(this);

		resideMenu.addMenuItem(itemLibrary, ResideMenu.DIRECTION_LEFT);
		resideMenu.addMenuItem(itemBookshelf, ResideMenu.DIRECTION_LEFT);
		resideMenu.addMenuItem(itemWenku8, ResideMenu.DIRECTION_LEFT);
		resideMenu.addMenuItem(itemSettings, ResideMenu.DIRECTION_LEFT);

		resideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_RIGHT);

	}

	@Override
	public boolean dispatchTouchEvent(MotionEvent ev) {
		return resideMenu.dispatchTouchEvent(ev);
	}

	@Override
	public void onClick(View view) {
		if (view == currentViewItemSave) {
			resideMenu.closeMenu();
			return;
		} else if (view == itemLibrary) {
			changeFragment(new LibraryFragment());
			currentViewItemSave = itemLibrary;
		} else if (view == itemBookshelf) {
			changeFragment(new BookshelfFragment());
			currentViewItemSave = itemBookshelf;
		} else if (view == itemWenku8) {
			// changeFragment(new Wenku8Fragment());
			// currentViewItemSave = itemWenku8;
			Toast.makeText(this,
					getResources().getString(R.string.in_building),
					Toast.LENGTH_SHORT).show();
		} else if (view == itemSettings) {
			changeFragment(new SettingFragment());
			currentViewItemSave = itemSettings;
		}

		resideMenu.closeMenu();
	}

	private void changeFragment(Fragment targetFragment) {
		resideMenu.clearIgnoredViewList();
		getSupportFragmentManager().beginTransaction()
				.replace(R.id.main_fragment, targetFragment, "fragment")
				.setTransitionStyle(FragmentTransaction.TRANSIT_FRAGMENT_FADE)
				.commit();
	}

	// What good method is to access resideMenu？
	public ResideMenu getResideMenu() {
		return resideMenu;
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		// TODO Auto-generated method stub
		if (keyCode == KeyEvent.KEYCODE_BACK) {
			exitBy2Click(); // call exit function
		}
		return false;
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
