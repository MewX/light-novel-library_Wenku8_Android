/**
 *  Library Fragment
 **
 *  This class is a part of main activity, and it will show you the library.
 *  It shows all the entry first, and clicks on entry will take you into
 *    "NovelListActivity" or search activity, or special list activity.
 **/

package org.mewx.lightnovellibrary.activity;

import java.util.ArrayList;
import java.util.List;

import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.component.adapter.EntryElement;
import org.mewx.lightnovellibrary.component.adapter.EntryElementAdapter;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.special.ResideMenu.ResideMenu;

public class LibraryFragment extends Fragment {
	private View parentView;
	private ResideMenu resideMenu;
	private MainActivity parentActivity = null;

	private List<EntryElement> entryElementList = new ArrayList<EntryElement>();

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

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState) {
		parentView = inflater.inflate(R.layout.activity_tab1, container, false);
		setUpViews();

		// load full list
		initList();
		// EntryElementAdapter adapter = new EntryElementAdapter(parentActivity,
		// R.layout.layout_entry_button, entryElementList);
		ListView lv = (ListView) parentView.findViewById(R.id.list_all);
		if (lv == null) {
			Log.e("MewX", "ListView == null!");
			return parentView;
		}
		lv.setAdapter(new EntryElementAdapter(parentActivity, entryElementList));
		lv.setOnItemClickListener(new OnItemClickListener() {
			// Click on ListView
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				EntryElement ele = entryElementList.get(position);
				Log.v("MewX", "lv clicked: position=" + position + "; getName="
						+ ele.getName());

				// to new activity
				if (ele.getCode().equals("search_novel")) {
					// call search activity first
					Intent intent = new Intent();
					intent.setClass(parentActivity, NovelSearchActivity.class);
					intent.putExtra("code", ele.getCode());
					startActivity(intent);
					getActivity().overridePendingTransition(R.anim.in_from_right, R.anim.keep);

				} else if (ele.getCode().equals("list_special")) {
					// call list special first
					Toast.makeText(parentActivity,
							getResources().getString(R.string.in_building),
							Toast.LENGTH_SHORT).show();

				} else {
					// just the rest lists
					Intent intent = new Intent();
					intent.setClass(parentActivity, NovelListActivity.class);
					intent.putExtra("title", ele.getName());
					intent.putExtra("code", ele.getCode());
					startActivity(intent);
					getActivity().overridePendingTransition(R.anim.in_from_right, R.anim.keep);
				}
			}
		});

		// set the two button on the title bar
		((TextView) getActivity().findViewById(R.id.textTitle))
		.setText(getResources().getString(R.string.tab_library));
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

		return;
	}

	private void initList() {
		// <string name="search_novel">搜索轻小说</string>
		// <string name="list_special">查看专题列表</string>
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

		EntryElement search_novel = new EntryElement("search_novel",
				getResources().getString(R.string.search_novel),
				"assets://ic_entry/ic_01s.png");
		entryElementList.add(search_novel);

		EntryElement list_special = new EntryElement("list_special",
				getResources().getString(R.string.list_special),
				"assets://ic_entry/ic_02s.png");
		entryElementList.add(list_special);

		EntryElement postdate = new EntryElement("postdate", getResources()
				.getString(R.string.postdate), "assets://ic_entry/ic_03s.png");
		entryElementList.add(postdate);

		EntryElement goodnum = new EntryElement("goodnum", getResources()
				.getString(R.string.goodnum), "assets://ic_entry/ic_04s.png");
		entryElementList.add(goodnum);

		EntryElement fullflag = new EntryElement("fullflag", getResources()
				.getString(R.string.fullflag), "assets://ic_entry/ic_05s.png");
		entryElementList.add(fullflag);

		EntryElement lastupdate = new EntryElement("lastupdate", getResources()
				.getString(R.string.lastupdate), "assets://ic_entry/ic_06s.png");
		entryElementList.add(lastupdate);

		EntryElement allvisit = new EntryElement("allvisit", getResources()
				.getString(R.string.allvisit), "assets://ic_entry/ic_07s.png");
		entryElementList.add(allvisit);

		EntryElement allvote = new EntryElement("allvote", getResources()
				.getString(R.string.allvote), "assets://ic_entry/ic_08s.png");
		entryElementList.add(allvote);

		EntryElement monthvisit = new EntryElement("monthvisit", getResources()
				.getString(R.string.monthvisit), "assets://ic_entry/ic_09s.png");
		entryElementList.add(monthvisit);

		EntryElement monthvote = new EntryElement("monthvote", getResources()
				.getString(R.string.monthvote), "assets://ic_entry/ic_10s.png");
		entryElementList.add(monthvote);

		EntryElement weekvisit = new EntryElement("weekvisit", getResources()
				.getString(R.string.weekvisit), "assets://ic_entry/ic_11s.png");
		entryElementList.add(weekvisit);

		EntryElement weekvote = new EntryElement("weekvote", getResources()
				.getString(R.string.weekvote), "assets://ic_entry/ic_12s.png");
		entryElementList.add(weekvote);

		EntryElement dayvisit = new EntryElement("dayvisit", getResources()
				.getString(R.string.dayvisit), "assets://ic_entry/ic_13s.png");
		entryElementList.add(dayvisit);

		EntryElement dayvote = new EntryElement("dayvote", getResources()
				.getString(R.string.dayvote), "assets://ic_entry/ic_14s.png");
		entryElementList.add(dayvote);

		EntryElement size = new EntryElement("size", getResources().getString(
				R.string.size), "assets://ic_entry/ic_15s.png");
		entryElementList.add(size);

		return;
	}

}
