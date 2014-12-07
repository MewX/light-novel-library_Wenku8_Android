/**
 *  Bookshelf Fragment
 **
 *  This class is a part of main activity, and it will show you bookshelf.
 *  Bookshelf contains the books you've clicked "like".
 *  And if the book is cached, you can read it offline.
 **/

package org.mewx.lightnovellibrary.activity;

import java.util.ArrayList;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.component.NovelElement;
import org.mewx.lightnovellibrary.component.NovelElementAdapter;
import org.mewx.lightnovellibrary.component.XMLParser;
import com.special.ResideMenu.ResideMenu;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class BookshelfFragment extends Fragment {
	private View parentView;
	private ResideMenu resideMenu;

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
		parentView = inflater.inflate(R.layout.activity_tab2, container, false);
		setUpViews();

		// set the two button on the title bar
		((TextView) getActivity().findViewById(R.id.textTitle))
				.setText(getResources().getString(R.string.tab_bookshelf));
		((ImageView) getActivity().findViewById(R.id.btnMenu))
				.setVisibility(View.VISIBLE);
		((ImageView) getActivity().findViewById(R.id.btnEdit))
				.setVisibility(View.VISIBLE);
		getActivity().findViewById(R.id.btnMenu).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						resideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
					}
				});

		getActivity().findViewById(R.id.btnEdit).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						Toast.makeText(getActivity(), "Edit clicked",
								Toast.LENGTH_SHORT).show();
					}
				});

		return parentView;
	}

	private void setUpViews() {
		MainActivity parentActivity = (MainActivity) getActivity();
		resideMenu = parentActivity.getResideMenu();

		// Button action
		// parentView.findViewById(R.id.btn_open_menu).setOnClickListener(
		// new View.OnClickListener() {
		// @Override
		// public void onClick(View view) {
		// resideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
		// }
		// });

		resideMenu.setMenuListener(menuListener);
		resideMenu.setSwipeDirectionDisable(ResideMenu.DIRECTION_RIGHT);

		// add gesture operation's ignored views
		// FrameLayout ignored_view = (FrameLayout) parentView
		// .findViewById(R.id.ignored_view);
		// resideMenu.addIgnoredView(ignored_view);
	}

	@Override
	public void onResume() {
		super.onResume();

		// fill list
		final ArrayList<Integer> al = GlobalConfig.getLocalBookshelfList();
		ArrayList<NovelElement> alne = new ArrayList<NovelElement>();

		for (int i = 0; i < al.size(); i++) {
			String xml = GlobalConfig.loadFullFileFromSaveFolder("intro",
					al.get(i) + "-intro.xml");
			if (xml.equals("")) {
				Toast.makeText(getActivity(), "xml = \"\"", Toast.LENGTH_SHORT)
						.show();
				continue;
			}

			XMLParser.NovelListWithInfo nlwi = XMLParser
					.getNovelShortInfoBySearching(xml);

			NovelElement ne = new NovelElement(al.get(i), nlwi.name, nlwi.hit,
					nlwi.push, nlwi.fav, null);
			alne.add(ne);
		}

		if (alne.size() == 0) {
			Toast.makeText(getActivity(),
					getResources().getString(R.string.search_result_none),
					Toast.LENGTH_SHORT).show();
			return;
		}

		final ArrayList<NovelElement> alne_cp = alne;
		NovelElementAdapter adapter = new NovelElementAdapter(getActivity(),
				alne_cp);
		ListView listViewNew = (ListView) parentView
				.findViewById(R.id.novel_list);
		listViewNew.setDivider(null);
		listViewNew.setAdapter(adapter);
		
		listViewNew.setOnItemClickListener(new OnItemClickListener() {
			// Click on ListView
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				NovelElement ne = alne_cp.get(position);
				GlobalConfig.accessToLocalBookshelf(al.get(position));
				Log.v("MewX", "NovelElement clicked: position=" + position
						+ "; getName=" + ne.getName());

				// to new activity
				Intent intent = new Intent();
				intent.setClass(getActivity(), NovelInfoActivity.class);
				intent.putExtra("title", ne.getName());
				intent.putExtra("aid", ne.getAid());
				startActivity(intent);
			}
		});
		
		listViewNew.setOnItemLongClickListener(new OnItemLongClickListener(){
			// Long click to pop up menu
			@Override
			public boolean onItemLongClick(AdapterView<?> parent, View view,
					int position, long id) {
				// TODO Auto-generated method stub
				
				return false;
			}
			
		});
		
		return;
	}
}
