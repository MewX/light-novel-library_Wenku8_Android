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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.activity.Wenku8Fragment.asyncTask;
import org.mewx.lightnovellibrary.component.EntryElementAdapter;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.component.NovelElement;
import org.mewx.lightnovellibrary.component.NovelElementAdapter;
import org.mewx.lightnovellibrary.component.NovelIcon;
import org.mewx.lightnovellibrary.component.NovelIconAdapter;
import org.mewx.lightnovellibrary.component.XMLParser;
import org.mewx.lightnovellibrary.util.LightNetwork;

import cn.wenku8.api.Wenku8Interface;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

public class NovelListActivity extends ActionBarActivity {
	// get "code" and "plus"
	// "plus" is only used on list_special
	private String code, name;
	private ActionBarActivity parentActivity = null;
	private boolean isLoading;

	// use this is enough
	private List<NovelElement> listResult = new ArrayList<NovelElement>();
	private NovelElementAdapter adapter = null;
	private int currentPage;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_novel_list);

		// get parentActivity
		parentActivity = this;
		currentPage = 1;
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
						finish();
					}
				});

		// Interpret the in-coming "code" and "plus"
		name = getIntent().getStringExtra("title");
		code = getIntent().getStringExtra("code");
		Log.v("MewX", "name=" + name + "; code=" + code);
		if (code == "search_novel") {
			// From Search Button
			((TextView) findViewById(R.id.textTitle)).setText(getResources()
					.getString(R.string.search_result));

			// show search result
			loadSearchResultList();
		} else if (code == "list_special") {
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
				List<XMLParser.NovelListWithInfo> l = XMLParser
						.getNovelListWithInfo(new String(LightNetwork
								.LightHttpPost(Wenku8Interface.BaseURL,
										params[0]), "UTF-8"));
				if (l == null) {
					Log.e("MewX-Main", "getNullFromParser");
					return -1;
				}

				for (int i = 0; i < 10; i++) {
					XMLParser.NovelListWithInfo nlwi = l.get(i);

					// getImage
					// List<NameValuePair> imgP = new
					// ArrayList<NameValuePair>();
					// imgP.add(Wenku8Interface.getNovelCover(nlwi.aid));
					// byte[] img = LightNetwork.LightHttpPost(
					// Wenku8Interface.BaseURL, imgP);

					NovelElement ne = new NovelElement(nlwi.aid, nlwi.name,
							nlwi.hit, nlwi.push, nlwi.fav, null);
					listResult.add(ne);
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
			// result:
			// add imageView, only here can fetch the layout2 id!!!
			if (currentPage <= 2) {
				// This is initial part
				adapter = new NovelElementAdapter(parentActivity, listResult);
				ListView listViewNew = (ListView) parentActivity
						.findViewById(R.id.novel_list);
				listViewNew.setAdapter(adapter);

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
									.getCount() - 1) && !isLoading)
								loadNovelList(currentPage++);
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
				adapter.notifyDataSetChanged();
			}

			isLoading = false; // set loading states

			// load images
			return;
		}

	}
}
