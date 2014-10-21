package org.mewx.lightnovellibrary;

import java.util.ArrayList;
import java.util.List;

import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.support.v7.app.ActionBarActivity;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TextView;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.os.Bundle;
import android.os.Parcelable;

public class MainActivity extends ActionBarActivity {

	private ViewPager mPager; // ViewPager
	private List<View> listViews; // Tab Page List
	private ImageView cursor; // Animating cursor picture
	private TextView t1, t2, t3; // TextView of tab title
	private int offset = 0; // cursor offset
	private int bmpW, screenW; // the width of the cursor picture

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_main);

		// Hide the two button on title bar
		((TextView) findViewById(R.id.textBack)).setVisibility(View.GONE);
		((TextView) findViewById(R.id.textMenu)).setVisibility(View.GONE);

		// Init
		InitTextView();
		InitImageView();
		InitViewPager();

		return;
	}

	/**
	 * Initial TextView
	 */
	private void InitTextView() {
		t1 = (TextView) findViewById(R.id.tabText1);
		t2 = (TextView) findViewById(R.id.tabText2);
		t3 = (TextView) findViewById(R.id.tabText3);

		t1.setOnClickListener(new TabOnClickListener(0));
		t2.setOnClickListener(new TabOnClickListener(1));
		t3.setOnClickListener(new TabOnClickListener(2));
		return;
	}

	/**
	 * Initial tab_cursor
	 */
	private void InitImageView() {
		cursor = (ImageView) findViewById(R.id.tabCursor);
		bmpW = BitmapFactory.decodeResource(getResources(),
				R.drawable.tab_cursor).getWidth();// get picture width
		// bmpW = 5; // 5 dp width
		DisplayMetrics dm = new DisplayMetrics();
		getWindowManager().getDefaultDisplay().getMetrics(dm);
		screenW = dm.widthPixels;
		offset = ( screenW / 3 - bmpW) / 2;

		Matrix matrix = new Matrix();
		matrix.postTranslate(offset, 0);
		cursor.setImageMatrix(matrix);

		// LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT,
		// LayoutParams.WRAP_CONTENT);
		// lp.setMargins(offset, 0, 0, 0);
		// cursor.setLayoutParams(lp);

		Log.v("MewX-Main", "bmpW=" + bmpW + "; screenW=" + dm.widthPixels
				+ "; offset=" + offset);
		return;
	}
	
	/**
	 * Initial ViewPager
	 */
	private void InitViewPager() {
		mPager = (ViewPager) findViewById(R.id.vPager);
		listViews = new ArrayList<View>();
		LayoutInflater mInflater = getLayoutInflater();
		listViews.add(mInflater.inflate(R.layout.activity_tab1, null));
		listViews.add(mInflater.inflate(R.layout.activity_tab2, null));
		listViews.add(mInflater.inflate(R.layout.activity_tab3, null));
		mPager.setAdapter(new TabPagerAdapter(listViews));
		mPager.setCurrentItem(0);
		mPager.setOnPageChangeListener(new TabOnPageChangeListener());
		return;
	}
	
	public class TabOnClickListener implements View.OnClickListener {
		private int index = 0;

		public TabOnClickListener(int i) {
			index = i;
			return;
		}

		@Override
		public void onClick(View v) {
			mPager.setCurrentItem(index);
			return;
		}
	};

	public class TabPagerAdapter extends PagerAdapter {
		public List<View> mListViews;

		public TabPagerAdapter(List<View> mListViews) {
			this.mListViews = mListViews;
			return;
		}

		@Override
		public void destroyItem(View arg0, int arg1, Object arg2) {
			((ViewPager) arg0).removeView(mListViews.get(arg1));
			return;
		}

		@Override
		public void finishUpdate(View arg0) {
			return;
		}

		@Override
		public int getCount() {
			return mListViews.size();
		}

		@Override
		public Object instantiateItem(View arg0, int arg1) {
			((ViewPager) arg0).addView(mListViews.get(arg1), 0);
			return mListViews.get(arg1);
		}

		@Override
		public boolean isViewFromObject(View arg0, Object arg1) {
			return arg0 == (arg1);
		}

		@Override
		public void restoreState(Parcelable arg0, ClassLoader arg1) {
			return;
		}

		@Override
		public Parcelable saveState() {
			return null;
		}

		@Override
		public void startUpdate(View arg0) {
			return;
		}
	}

	public class TabOnPageChangeListener implements OnPageChangeListener {

		@Override
		public void onPageSelected(int arg0) {
			return;
		}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2) {
			//Log.v( "MewX", "position="+arg0+"; positionOffset="+arg1+"; positionOffsetPixels="+arg2);
			
			// position - visible left page
			// positionOffsetPixels from 0 <-> screenWidth
			final int delta = bmpW + offset * 2;
			
			Matrix matrix = new Matrix();
			matrix.postTranslate(offset+arg0*delta+arg2*delta/screenW, 0);
			cursor.setImageMatrix(matrix);
		}

		@Override
		public void onPageScrollStateChanged(int arg0) {
			return;
		}
	}

}
