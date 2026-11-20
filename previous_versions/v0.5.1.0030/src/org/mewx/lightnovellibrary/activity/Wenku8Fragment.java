/**
 *  Wenku8 Fragment
 **
 *  This class is a part of main activity, and it will show the
 *  community functions. It provides the same functions which is provided
 *  on the site.
 **/

package org.mewx.lightnovellibrary.activity;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.api.Wenku8Interface;
import org.mewx.lightnovellibrary.component.XMLParser;
import org.mewx.lightnovellibrary.component.XMLParser.NovelListWithInfo;
import org.mewx.lightnovellibrary.component.adapter.NovelIcon;
import org.mewx.lightnovellibrary.component.adapter.NovelIconAdapter;
import org.mewx.lightnovellibrary.util.LightNetwork;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.ImageView.ScaleType;

import com.special.ResideMenu.ResideMenu;

public class Wenku8Fragment extends Fragment {
	private View parentView;
	private ResideMenu resideMenu;
	private LinearLayout layout2;
	private ImageView image1;
	private MainActivity parentActivity = null;

	private ResideMenu.OnMenuListener menuListener = new ResideMenu.OnMenuListener() {
		@Override
		public void openMenu() {
			//
		}

		@Override
		public void closeMenu() {
			//
		}
	};

	private List<NovelIcon> listTopNew = new ArrayList<NovelIcon>(); // j = 0
	private List<NovelIcon> listTopHit = new ArrayList<NovelIcon>(); // j = 1
	private List<NovelIcon> listTopFav = new ArrayList<NovelIcon>(); // j = 2

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		parentView = inflater.inflate(R.layout.activity_tab3, container, false);
		setUpViews();

		// load top new list
		List<NameValuePair> pairTopNew = new ArrayList<NameValuePair>();
		pairTopNew.add(Wenku8Interface.getNovelListWithInfo(
				Wenku8Interface.NOVELSORTBY.lastUpdate, 1,
				Wenku8Interface.LANG.SC));
		List<NameValuePair> pairTopHit = new ArrayList<NameValuePair>();
		pairTopHit.add(Wenku8Interface.getNovelListWithInfo(
				Wenku8Interface.NOVELSORTBY.allVisit, 1,
				Wenku8Interface.LANG.SC));
		List<NameValuePair> pairTopFav = new ArrayList<NameValuePair>();
		pairTopFav.add(Wenku8Interface
				.getNovelListWithInfo(Wenku8Interface.NOVELSORTBY.goodNum, 1,
						Wenku8Interface.LANG.SC));
		asyncTask ast = new asyncTask();
		ast.execute(pairTopNew, pairTopHit, pairTopFav);

		// image addition test
		// image1 = new ImageView(parentActivity);
		// List<NameValuePair> testP = new ArrayList<NameValuePair>();
		// testP.add(Wenku8Interface.getNovelCover(1));
		// jpgTask bgt2 = new jpgTask();
		// bgt2.execute(testP);

		List<NameValuePair> testP2 = new ArrayList<NameValuePair>();
		testP2.add(Wenku8Interface.getNovelListByLibraryWithInfo(2, 1,
				Wenku8Interface.LANG.SC));
		bgTask bgt = new bgTask();
		bgt.execute(testP2);

		// set the two button on the title bar
		((TextView) getActivity().findViewById(R.id.textTitle))
				.setText(getResources().getString(R.string.tab_wenku8));
		((ImageView) parentActivity.findViewById(R.id.btnMenu))
				.setVisibility(View.VISIBLE);
		((ImageView) parentActivity.findViewById(R.id.btnEdit))
				.setVisibility(View.GONE);
		parentActivity.findViewById(R.id.btnMenu).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						resideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
					}
				});

		return parentView;
	}

	private void setUpViews() {
		while (parentActivity == null) {
			// this step is necessary
			parentActivity = (MainActivity) getActivity();
		}
		resideMenu = parentActivity.getResideMenu();

		resideMenu.setMenuListener(menuListener);
		resideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_RIGHT);

		// add gesture operation's ignored views
		// FrameLayout ignored_view = (FrameLayout) parentView
		// .findViewById(R.id.ignored_view);
		// resideMenu.addIgnoredView(ignored_view);
	}

	class bgTask extends AsyncTask<List<NameValuePair>, Integer, byte[]> {
		@Override
		protected byte[] doInBackground(List<NameValuePair>... params) {
			// TODO Auto-generated method stub
			return LightNetwork.LightHttpPost(Wenku8Interface.BaseURL,
					params[0]);
		}

		@Override
		protected void onPostExecute(byte[] result) {
			TextView ttt = (TextView) parentActivity
					.findViewById(R.id.textTestMain);
			if (ttt == null) {
				Log.v("MewX-Main", "TextView ttt == null");
				return;
			}
			try {
				if (result == null)
					return;
				ttt.setText(new String(result, "UTF-8"));
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				ttt.setText(e.getMessage());
			}
			return;

		}

	}

	class jpgTask extends AsyncTask<List<NameValuePair>, Integer, byte[]> {

		@Override
		protected byte[] doInBackground(List<NameValuePair>... params) {
			// TODO Auto-generated method stub
			return LightNetwork.LightHttpPost(Wenku8Interface.BaseURL,
					params[0]);
		}

		@Override
		protected void onPostExecute(byte[] result) {
			// result:
			// add imageView, only here can fetch the layout2 id!!!
			layout2 = (LinearLayout) parentActivity
					.findViewById(R.id.layout_tab1);
			if (layout2 == null) {
				Log.v("MewX-Main", "LinearLayout == null");
				return;
			}
			if (result == null)
				return;
			Bitmap bitmap = BitmapFactory.decodeByteArray(result, 0,
					result.length);
			if (bitmap == null) {
				Log.v("MewX-Main", "Bitmap == null");
				return;
			}
			BitmapDrawable bmp1 = new BitmapDrawable(bitmap); // BitmapDrawable(context.getResources(),
																// bitmap);
			if (bmp1 == null) {
				Log.v("MewX-Main", "BitmapDrawable == null");
				return;
			}
			image1.setImageDrawable(bmp1);
			image1.setClickable(true);
			int height = (int) ((float) layout2.getWidth()
					/ bmp1.getMinimumWidth() * bmp1.getMinimumHeight());
			image1.setLayoutParams(new LinearLayout.LayoutParams(
					LinearLayout.LayoutParams.MATCH_PARENT, height));
			image1.setScaleType(ScaleType.FIT_XY);
			layout2.addView(image1);
			return;

		}

	}

	class asyncTask extends AsyncTask<List<NameValuePair>, Integer, Integer> {
		// fail return -1
		@Override
		protected Integer doInBackground(List<NameValuePair>... params) {

			try {
				for (int j = 0; j < 3; j++) {
					byte[] tempInfo = LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, params[j]);
					if (tempInfo == null)
						return -100; // network error
					List<XMLParser.NovelListWithInfo> lNew = XMLParser
							.getNovelListWithInfo(new String(tempInfo, "UTF-8"));

					if (lNew == null) {
						Log.e("MewX-Main", "getNullFromParser");
						continue;
					}
					for (int i = 0; i < 3; i++) {
						XMLParser.NovelListWithInfo tempNLWI = lNew.get(i);

						// getImage
						List<NameValuePair> imgP = new ArrayList<NameValuePair>();
						imgP.add(Wenku8Interface.getNovelCover(tempNLWI.aid));
						byte[] img = LightNetwork.LightHttpPost(
								Wenku8Interface.BaseURL, imgP);
						if (img == null)
							return -100;// network error

						NovelIcon tempNI = new NovelIcon(tempNLWI.aid,
								tempNLWI.name, tempNLWI.hit, tempNLWI.push,
								tempNLWI.fav, img);
						switch (j) {
						case 0:
							listTopNew.add(tempNI);
							break;
						case 1:
							listTopHit.add(tempNI);
							break;
						case 2:
							listTopFav.add(tempNI);
							break;
						}
					}
					onProgressUpdate(j);
				}

			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return -1;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			switch (values[0]) {
			case 0:
				break;
			case 1:
				break;
			case 2:
				break;
			}
		}

		@Override
		protected void onPostExecute(Integer result) {
			// result:
			// add imageView, only here can fetch the layout2 id!!!

			// (0)
			int totalHeight = 0;
			NovelIconAdapter TopNewAdapter = new NovelIconAdapter(
					parentActivity, R.layout.activity_novel_button, listTopNew);
			ListView listViewNew = (ListView) parentActivity
					.findViewById(R.id.list_top_new);

			totalHeight = 0;
			// Here need to recalc the height of ListView,
			// Or, in the scrollView, the listView can only show one line
			for (int i = 0, len = TopNewAdapter.getCount(); i < len; i++) {
				// listAdapter.getCount()返回数据项的数目
				View listItem = TopNewAdapter.getView(i, null, listViewNew);
				// 计算子项View 的宽高
				listItem.measure(0, 0);
				// 统计所有子项的总高度
				totalHeight += listItem.getMeasuredHeight();
			}
			// sometime cause crash, get null pointer
			ViewGroup.LayoutParams paramsNew = listViewNew.getLayoutParams();

			paramsNew.height = totalHeight
					+ (listViewNew.getDividerHeight() * (TopNewAdapter
							.getCount() - 1));
			// listView.getDividerHeight()获取子项间分隔符占用的高度
			// params.height最后得到整个ListView完整显示需要的高度
			listViewNew.setLayoutParams(paramsNew);

			listViewNew.setAdapter(TopNewAdapter);

			// (1)
			NovelIconAdapter TopHitAdapter = new NovelIconAdapter(
					parentActivity, R.layout.activity_novel_button, listTopHit);
			ListView listViewHit = (ListView) parentActivity
					.findViewById(R.id.list_top_hit);

			// Here need to recalc the height of ListView,
			// Or, in the scrollView, the listView can only show one line
			totalHeight = 0;
			for (int i = 0, len = TopHitAdapter.getCount(); i < len; i++) {
				// listAdapter.getCount()返回数据项的数目
				View listItem = TopHitAdapter.getView(i, null, listViewHit);
				// 计算子项View 的宽高
				listItem.measure(0, 0);
				// 统计所有子项的总高度
				totalHeight += listItem.getMeasuredHeight();
			}
			ViewGroup.LayoutParams paramsHit = listViewHit.getLayoutParams();
			paramsHit.height = totalHeight
					+ (listViewHit.getDividerHeight() * (TopHitAdapter
							.getCount() - 1));
			// listView.getDividerHeight()获取子项间分隔符占用的高度
			// params.height最后得到整个ListView完整显示需要的高度
			listViewHit.setLayoutParams(paramsHit);

			listViewHit.setAdapter(TopHitAdapter);

			// (2)
			NovelIconAdapter TopFavAdapter = new NovelIconAdapter(
					parentActivity, R.layout.activity_novel_button, listTopFav);
			ListView listViewFav = (ListView) parentActivity
					.findViewById(R.id.list_top_fav);

			// Here need to recalc the height of ListView,
			// Or, in the scrollView, the listView can only show one line
			totalHeight = 0;
			for (int i = 0, len = TopFavAdapter.getCount(); i < len; i++) {
				// listAdapter.getCount()返回数据项的数目
				View listItem = TopFavAdapter.getView(i, null, listViewFav);
				// 计算子项View 的宽高
				listItem.measure(0, 0);
				// 统计所有子项的总高度
				totalHeight += listItem.getMeasuredHeight();
			}
			ViewGroup.LayoutParams paramsFav = listViewFav.getLayoutParams();
			paramsFav.height = totalHeight
					+ (listViewFav.getDividerHeight() * (listViewFav.getCount() - 1));
			// listView.getDividerHeight()获取子项间分隔符占用的高度
			// params.height最后得到整个ListView完整显示需要的高度
			listViewFav.setLayoutParams(paramsFav);

			listViewFav.setAdapter(TopFavAdapter);

			return;
		}

	}
}
