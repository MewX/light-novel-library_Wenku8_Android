/**
 *  Bookshelf Fragment
 **
 *  This class is a part of main activity, and it will show you bookshelf.
 *  Bookshelf contains the books you've clicked "like".
 *  And if the book is cached, you can read it offline.
 **/

package org.mewx.lightnovellibrary.activity;

import org.mewx.lightnovellibrary.R;

import com.special.ResideMenu.ResideMenu;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

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

		getActivity().findViewById(R.id.btnEdit)
				.setOnClickListener(new View.OnClickListener() {
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
}
