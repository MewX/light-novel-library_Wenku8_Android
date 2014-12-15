/**
 *  Bookshelf Fragment
 **
 *  This class is a part of main activity, and it will show you bookshelf.
 *  Bookshelf contains the books you've clicked "like".
 *  And if the book is cached, you can read it offline.
 **/

package org.mewx.lightnovellibrary.activity;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.component.NovelContentParser;
import org.mewx.lightnovellibrary.component.XMLParser;
import org.mewx.lightnovellibrary.component.adapter.NovelElement;
import org.mewx.lightnovellibrary.component.adapter.NovelElementAdapter;
import org.mewx.lightnovellibrary.util.LightCache;
import org.mewx.lightnovellibrary.util.LightNetwork;

import cn.wenku8.api.Wenku8Interface;

import com.special.ResideMenu.ResideMenu;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AdapterView.OnItemClickListener;

public class SettingFragment extends Fragment {
	public static String fromid = "bookshelf";
	private View parentView;
	private ResideMenu resideMenu;
	private ProgressDialog pDialog;

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
		parentView = inflater.inflate(R.layout.activity_about, container, false);
		setUpViews();

		// set the two button on the title bar
		((TextView) getActivity().findViewById(R.id.textTitle))
				.setText(getResources().getString(R.string.about));
		((ImageView) getActivity().findViewById(R.id.btnMenu))
				.setVisibility(View.VISIBLE);
		((ImageView) getActivity().findViewById(R.id.btnEdit))
				.setVisibility(View.GONE);
		getActivity().findViewById(R.id.btnMenu).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						resideMenu.openMenu(ResideMenu.DIRECTION_LEFT);
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
