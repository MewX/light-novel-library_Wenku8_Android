/**
 *  Novel List Activity
 **
 *  This activity will be called from one of them:
 *    SearchActivity, SpecialListIndexActivity,
 *    SpecialListItemActivity, LibraryFragment.
 *    
 *  So, it fetches "code" and "plus" to determine what to do.
 *  This is a list of light novel fetch result.
 *  And this list dynamically load more into the list.
 **/

package org.mewx.lightnovellibrary.activity;

import org.mewx.lightnovellibrary.R;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import me.imid.swipebacklayout.lib.SwipeBackLayout;
import me.imid.swipebacklayout.lib.app.SwipeBackActivity;

import org.apache.http.NameValuePair;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.activity.Wenku8Fragment.asyncTask;
import org.mewx.lightnovellibrary.api.Wenku8Interface;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.component.MyApp;
import org.mewx.lightnovellibrary.component.XMLParser;
import org.mewx.lightnovellibrary.component.adapter.EntryElement;
import org.mewx.lightnovellibrary.component.adapter.EntryElementAdapter;
import org.mewx.lightnovellibrary.component.adapter.NovelElement;
import org.mewx.lightnovellibrary.component.adapter.NovelElementAdapter;
import org.mewx.lightnovellibrary.component.adapter.NovelElementSearch;
import org.mewx.lightnovellibrary.component.adapter.NovelElementSearchAdapter;
import org.mewx.lightnovellibrary.component.adapter.NovelIcon;
import org.mewx.lightnovellibrary.component.adapter.NovelIconAdapter;
import org.mewx.lightnovellibrary.util.LightNetwork;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewGroup;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.umeng.analytics.MobclickAgent;

public class NovelListActivity extends SwipeBackActivity {// implements
															// OnTouchListener {
	// get "code" and "plus"
	// "plus" is only used on list_special
	private String code, name;
	private int plus;
	private Activity parentActivity = null;
	private boolean isLoading;
	private boolean isSearching;

	// use this is enough
	private List<NovelElementSearch> listResult_search = new ArrayList<NovelElementSearch>();
	private List<NovelElement> listResult_normal = new ArrayList<NovelElement>();
	private NovelElementSearchAdapter adapter_search = null;
	private NovelElementAdapter adapter_normal = null;
	private int currentPage, totalPage; // currentP stores next reading page num

	// Process
	private ProgressDialog pDialog = null;

	// slide back
	private SwipeBackLayout mSwipeBackLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_novel_list);

		mSwipeBackLayout = getSwipeBackLayout();
		mSwipeBackLayout.setScrimColor(Color.TRANSPARENT);
		mSwipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_LEFT);

		// get parentActivity
		parentActivity = this;
		currentPage = 1;
		totalPage = 1;
		isLoading = false;

		// set the two button on the title bar
		((ImageView) findViewById(R.id.btnMenu))
				.setImageResource(R.drawable.ic_back);
		((ImageView) findViewById(R.id.btnMenu)).setVisibility(View.VISIBLE);
		((ImageView) findViewById(R.id.btnEdit)).setVisibility(View.GONE);

		// set button actions
		findViewById(R.id.btnMenu).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						onBackPressed();
						// finish();
					}
				});

		// Interpret the in-coming "code" and "plus"
		name = getIntent().getStringExtra("title");
		code = getIntent().getStringExtra("code");
		Log.v("MewX", "name=" + name + "; code=" + code);
		if (code.equals("search_novel")) {
			// From Search Button
			plus = getIntent().getIntExtra("plus", 1);

			((TextView) findViewById(R.id.textTitle)).setText(getResources()
					.getString(R.string.search) + name);

			// show search result
			loadSearchResultList();
		} else if (code.equals("list_special")) {
			// From one click on List Special
			((TextView) findViewById(R.id.textTitle))
					.setText("something special");

			// load the specific list result, and get "plus" from Intent
			loadSpecialResult();
		} else {
			// Show provided novel list

			CharSequence fetch_title = name;
			((TextView) findViewById(R.id.textTitle)).setText(fetch_title);

			// switch category
			// <string name="postdate">最新入库</string>
			// <string name="goodnum">总收藏榜</string>
			// <string name="fullflag">完结列表</string>
			// <string name="lastupdate">最近更新</string>
			// <string name="allvisit">总排行榜</string>
			// <string name="allvote">总推荐榜</string>
			// <string name="monthvisit">月排行榜</string>
			// <string name="monthvote">月推荐榜</string>
			// <string name="weekvisit">周排行榜</string>
			// <string name="weekvote">周推荐榜</string>
			// <string name="dayvisit">日排行榜</string>
			// <string name="dayvote">日推荐榜</string>
			// <string name="size">字数排行</string>

			loadNovelList(currentPage++);
		}

		return;
	}

	@Override
	protected void onDestroy() {
		super.onDestroy();

		if (pDialog != null)
			pDialog.dismiss();
		pDialog = null;
		return;
	}

	private void loadNovelList(int page) {
		// In fact, I don't need to know what it really is.
		// I just need to get the NOVELSORTBY
		isLoading = true; // set loading states

		// EN code, not CHS code
		Wenku8Interface.NOVELSORTBY nsb = Wenku8Interface.getNOVELSORTBY(code);

		// fetch list
		List<NameValuePair> targVarList = new ArrayList<NameValuePair>();
		targVarList.add(Wenku8Interface.getNovelListWithInfo(nsb, page,
				GlobalConfig.getFetchLanguage()));

		asyncTask ast = new asyncTask();
		ast.execute(targVarList);

		// need to get page_all number

	}

	private void loadSearchResultList() {
		// the result is just 10 records, so don't need "isLoading"
		List<NameValuePair> targVarList = new ArrayList<NameValuePair>();
		if (plus == 1) {
			targVarList.add(Wenku8Interface.searchNovelByNovelName(name,
					GlobalConfig.getFetchLanguage()));
		} else if (plus == 2) {
			targVarList.add(Wenku8Interface.searchNovelByAuthorName(name,
					GlobalConfig.getFetchLanguage()));

		}

		isSearching = true;
		final asyncSearchTask ast = new asyncSearchTask();
		ast.execute(targVarList);

		pDialog = new ProgressDialog(parentActivity);
		pDialog.setTitle(getResources().getString(R.string.search_ing));
		pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pDialog.setCancelable(true);
		pDialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				// TODO Auto-generated method stub
				isSearching = false;
				if (pDialog != null)
					pDialog.dismiss();
				pDialog = null;
			}

		});
		pDialog.setMessage(getResources().getString(R.string.search_fetching));
		pDialog.setProgress(0);
		pDialog.setMax(1);
		pDialog.show();

		return;
	}

	private void loadSpecialResult() {
		return;
	}

	// Async Tasks
	class asyncTask extends AsyncTask<List<NameValuePair>, Integer, Integer> {
		// fail return -1
		@Override
		protected Integer doInBackground(List<NameValuePair>... params) {

			try {
				byte[] tempXml = LightNetwork.LightHttpPost(
						Wenku8Interface.BaseURL, params[0]);
				if (tempXml == null)
					return -100;
				String xml = new String(tempXml, "UTF-8");
				totalPage = XMLParser.getNovelListWithInfoPageNum(xml);
				List<XMLParser.NovelListWithInfo> l = XMLParser
						.getNovelListWithInfo(xml);
				if (l == null)
					return -100; // network error

				for (int i = 0; i < l.size(); i++) {
					XMLParser.NovelListWithInfo nlwi = l.get(i);

					// getImage
					// List<NameValuePair> imgP = new
					// ArrayList<NameValuePair>();
					// imgP.add(Wenku8Interface.getNovelCover(nlwi.aid));
					// byte[] img = LightNetwork.LightHttpPost(
					// Wenku8Interface.BaseURL, imgP);

					NovelElement ne = new NovelElement(nlwi.aid, nlwi.name,
							nlwi.hit, nlwi.push, nlwi.fav, null);
					listResult_normal.add(ne);
				}

				// onProgressUpdate(j);

			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return -1;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			return;
		}

		@Override
		protected void onPostExecute(Integer result) {
			if (result == -100) {
				Toast.makeText(parentActivity, R.string.network_error,
						Toast.LENGTH_LONG).show();
				Log.e("MewX-Main", "getNullFromParser");
				return;
			}

			// result:
			// add imageView, only here can fetch the layout2 id!!!
			if (currentPage <= 2) {
				// This is initial part
				adapter_normal = new NovelElementAdapter(parentActivity,
						listResult_normal);
				ListView listViewNew = (ListView) parentActivity
						.findViewById(R.id.novel_list);
				listViewNew.setDivider(null);
				listViewNew.setAdapter(adapter_normal);
				listViewNew.setOnItemClickListener(new OnItemClickListener() {
					// Click on ListView
					@Override
					public void onItemClick(AdapterView<?> parent, View view,
							int position, long id) {
						NovelElement ne = listResult_normal.get(position);
						Log.v("MewX", "NovelElement clicked: position="
								+ position + "; getName=" + ne.getName());

						// to new activity
						Intent intent = new Intent();
						intent.setClass(parentActivity, NovelInfoActivity.class);
						intent.putExtra("title", ne.getName());
						intent.putExtra("aid", ne.getAid());
						startActivity(intent);
						overridePendingTransition(R.anim.in_from_right,
								R.anim.keep);
					}
				});

				// OnScrollListener
				listViewNew.setOnScrollListener(new OnScrollListener() {
					@Override
					public void onScrollStateChanged(AbsListView view,
							int scrollState) {
						switch (scrollState) {
						// When stopped
						case OnScrollListener.SCROLL_STATE_IDLE:
							// judge if at bottom
							if (view.getLastVisiblePosition() == (view
									.getCount() - 1) && !isLoading) {

								// ((TextView) parentActivity
								// .findViewById(R.id.novel_load_status))
								// .setText(getResources().getString(
								// R.string.load_loading)
								// + "( "
								// + currentPage
								// + " / "
								// + totalPage + " )");

								if (currentPage <= totalPage) {
									Toast.makeText(
											MyApp.getContext(),
											getResources().getString(
													R.string.load_loading)
													+ "( "
													+ currentPage
													+ " / " + totalPage + " )",
											Toast.LENGTH_SHORT).show();

									loadNovelList(currentPage++);
								} else {
									Toast.makeText(
											MyApp.getContext(),
											getResources().getString(
													R.string.load_finished)
													+ "( "
													+ (currentPage - 1)
													+ " / " + totalPage + " )",
											Toast.LENGTH_SHORT).show();
								}
							}

							break;
						}
					}

					@Override
					public void onScroll(AbsListView view,
							int firstVisibleItem, int visibleItemCount,
							int totalItemCount) {
					}
				});
			} else {
				// This part is for update list
				adapter_normal.notifyDataSetChanged();

			}

			isLoading = false; // set loading states

			// load images
			return;
		}
	}

	// AsyncSearchTask Tasks
	class asyncSearchTask extends
			AsyncTask<List<NameValuePair>, Integer, Integer> {
		// fail return -1
		@Override
		protected Integer doInBackground(List<NameValuePair>... params) {

			try {
				if (!isSearching)
					return -222; // cancel
				byte[] tempXml = LightNetwork.LightHttpPost(
						Wenku8Interface.BaseURL, params[0]);
				if (tempXml == null) {
					return -100; // network error
				}
				String xml = new String(tempXml, "UTF-8");

				List<Integer> l = XMLParser.getSearchResult(xml);
				Log.v("MewX-Main", "end fetch XML");
				if (l == null)
					return -100; // network error

				Log.v("MewX-Main", "goto search fetch loop");
				if (pDialog != null)
					pDialog.setMax(l.size());
				for (int i = 0; i < l.size(); i++) {
					// pDialog.setMessage(getResources().getString(
					// R.string.search_fetching)
					// + "( " + i + " / " + l.size() + " )");
					if (pDialog != null)
						pDialog.setProgress(i);
					Log.v("MewX-Main", "In loop: " + i);
					List<NameValuePair> targVarList = new ArrayList<NameValuePair>();
					targVarList.add(Wenku8Interface.getNovelShortInfo(l.get(i),
							GlobalConfig.getFetchLanguage()));

					// must use short info, because this database is
					// heavy-loaded
					// XMLParser.NovelIntro ni = XMLParser.getNovelIntro(xml);

					// This is not full info
					if (!isSearching)
						return -222; // cancel
					byte[] temoNovelIntro = LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, targVarList);
					if (temoNovelIntro == null)
						return -100; // network error
					XMLParser.NovelIntro ni = XMLParser
							.getNovelIntro(new String(temoNovelIntro, "UTF-8"));

					if (ni == null) {
						Log.v("MewX", "XMLParser.NovelIntro ni == null");
						return -100;
					}
					NovelElementSearch ne = new NovelElementSearch(ni.aid,
							ni.title, ni.author, ni.status, ni.update,
							ni.intro_short, null);
					listResult_search.add(ne);
				}

			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			return -1;
		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			return;
		}

		@Override
		protected void onPostExecute(Integer result) {
			if (pDialog != null)
				pDialog.dismiss();

			// check the result
			if (result == -222) {
				// still run the rest to display temp results
				// return; // user cancelled
			} else if (result == -100) {
				Toast.makeText(parentActivity,
						getResources().getString(R.string.network_error),
						Toast.LENGTH_LONG).show();
				Log.e("MewX-Main", "getNullFromParser");
				return;
			} else if (listResult_search.size() == 0) {
				Toast.makeText(parentActivity,
						getResources().getString(R.string.search_result_none),
						Toast.LENGTH_LONG).show();
				return;
			}

			Log.v("MewX",
					"listResult_search.size(): " + listResult_search.size());
			adapter_search = new NovelElementSearchAdapter(parentActivity,
					listResult_search);
			ListView listViewNew = (ListView) parentActivity
					.findViewById(R.id.novel_list);
			listViewNew.setDivider(null);
			listViewNew.setAdapter(adapter_search);
			listViewNew.setOnItemClickListener(new OnItemClickListener() {
				// Click on ListView
				@Override
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					NovelElementSearch ne = listResult_search.get(position);
					Log.v("MewX", "NovelElement clicked: position=" + position
							+ "; getName=" + ne.getTitle());

					// to new activity
					Intent intent = new Intent();
					intent.setClass(parentActivity, NovelInfoActivity.class);
					intent.putExtra("title", ne.getTitle());
					intent.putExtra("aid", ne.getAid());
					startActivity(intent);
					overridePendingTransition(R.anim.in_from_right, R.anim.keep);
				}
			});
			return;
		}
	}

	public void onResume() {
		super.onResume();
		MobclickAgent.onResume(this);
	}

	public void onPause() {
		super.onPause();
		MobclickAgent.onPause(this);
	}

	@Override
	public void onBackPressed() {
		scrollToFinishActivity();
	}
}
