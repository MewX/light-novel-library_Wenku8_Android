package org.mewx.lightnovellibrary.activity;

import java.util.ArrayList;
import java.util.List;

import me.imid.swipebacklayout.lib.SwipeBackLayout;
import me.imid.swipebacklayout.lib.app.SwipeBackActivity;

import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.component.GlobalConfig;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.Toast;

public class NovelSearchActivity extends SwipeBackActivity {

	private Activity parentActivity = null;
	private Spinner spinner = null;
	private ArrayList<String> history = null;

	// slide back
	private SwipeBackLayout mSwipeBackLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_novel_search);

		mSwipeBackLayout = getSwipeBackLayout();
		mSwipeBackLayout.setScrimColor(Color.TRANSPARENT);
		mSwipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_LEFT);

		// get parentActivity
		parentActivity = this;

		// set the two button on the title bar
		((ImageView) findViewById(R.id.btnMenu))
				.setImageResource(R.drawable.ic_back);
		((ImageView) findViewById(R.id.btnMenu)).setVisibility(View.VISIBLE);
		((ImageView) findViewById(R.id.btnEdit)).setVisibility(View.VISIBLE);

		// set button actions
		findViewById(R.id.btnMenu).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						onBackPressed();
						// finish();
					}
				});
		findViewById(R.id.btnEdit).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						// clear history
						new AlertDialog.Builder(parentActivity)
								.setTitle(
										getResources()
												.getString(
														R.string.search_delete_history_title))
								.setMessage(
										getResources()
												.getString(
														R.string.search_delete_history_question))
								.setPositiveButton(
										"YES",
										new android.content.DialogInterface.OnClickListener() {

											@Override
											public void onClick(
													DialogInterface dialog,
													int which) {
												// TODO Auto-generated method
												// stub
												GlobalConfig
														.clearSearchHistory();
												onResume();

											}

										}).setNegativeButton("NO", null).show();
					}
				});

		// set spinner
		spinner = (Spinner) findViewById(R.id.spinner);

		List<String> dicts = new ArrayList<String>();
		dicts.add(getResources().getString(R.string.search_by_title));
		dicts.add(getResources().getString(R.string.search_by_author));
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, dicts);
		spinner.setAdapter(adapter);
		spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
			public void onItemSelected(AdapterView<?> parent, View view,
					int position, long id) {
				return;
			}

			public void onNothingSelected(AdapterView<?> parent) {
				return;
			}
		});

		// Button Event

		((Button) findViewById(R.id.button_search))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						// get values
						String sel = (String) spinner.getSelectedItem();
						String temp = ((EditText) findViewById(R.id.editText))
								.getText().toString();
						if (temp.length() == 0) {
							Toast.makeText(
									parentActivity,
									getResources().getString(
											R.string.search_keyword),
									Toast.LENGTH_SHORT).show();
							return;
						}

						// call list view
						callSearch(sel, temp, true);

					}
				});

		// List View Event
		// ListView listView = (ListView) findViewById(R.id.list_history);
		// history = GlobalConfig.getSearchHistory();
		// listView.setAdapter(new ArrayAdapter<String>(parentActivity,
		// android.R.layout.simple_list_item_1, history));

		return;
	}

	@Override
	protected void onResume() {
		super.onResume();

		// update history list
		ListView listView = (ListView) findViewById(R.id.list_history);
		final ArrayList<String> temp = GlobalConfig.getSearchHistory();
		history = new ArrayList<String>();

		// translate number
		for (int i = 0; i < temp.size(); i++) {
			switch (temp.get(i).charAt(0)) {
			case '1':
				history.add("["
						+ getResources().getString(R.string.search_by_title)
						+ "]" + temp.get(i).substring(1, temp.get(i).length()));
				break;
			case '2':
				history.add("["
						+ getResources().getString(R.string.search_by_author)
						+ "]" + temp.get(i).substring(1, temp.get(i).length()));
				break;

			default: // exception
				history.add("["
						+ getResources().getString(R.string.search_by_title)
						+ "]" + temp.get(i).substring(1, temp.get(i).length()));
				break;
			}
		}
		listView.setAdapter(new ArrayAdapter<String>(parentActivity,
				android.R.layout.simple_list_item_1, history));

		((EditText) findViewById(R.id.editText)).setText("");

		listView.setOnItemClickListener(new OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view,
					int position, long id) {
				// TODO Auto-generated method stub
				int pos = position; // from 0
				if (pos == -1)
					return;

				// update order
				GlobalConfig.onSearchClicked(pos);

				// do click event
				String sel = "";
				switch (temp.get(0).charAt(0)) {
				case '1':
					callSearch(
							getResources().getString(R.string.search_by_title),
							temp.get(0).substring(1, temp.get(0).length()),
							false);
					break;
				case '2':
					callSearch(
							getResources().getString(R.string.search_by_author),
							temp.get(0).substring(1, temp.get(0).length()),
							false);
					break;
				default:
					callSearch(
							getResources().getString(R.string.search_by_title),
							temp.get(0).substring(1, temp.get(0).length()),
							false);
					break;
				}
			}
		});
		return;
	}

	private void callSearch(String sel, String text, boolean addHistory) {
		Intent intent = new Intent();
		intent.setClass(parentActivity, NovelListActivity.class);
		if (sel == getResources().getString(R.string.search_by_title)) {
			// title = 1
			intent.putExtra("plus", 1);
			if (addHistory)
				GlobalConfig.addSearchHistory("1" + text);

		} else if (sel == getResources().getString(R.string.search_by_author)) {
			// author = 2
			intent.putExtra("plus", 2);
			if (addHistory)
				GlobalConfig.addSearchHistory("2" + text);
		} else {
			Toast.makeText(parentActivity, "Unknown selected item",
					Toast.LENGTH_SHORT).show();
		}

		intent.putExtra("title", text);
		intent.putExtra("code", getIntent().getStringExtra("code"));
		startActivity(intent);
		parentActivity.overridePendingTransition(R.anim.in_from_right,
				R.anim.keep);
	}

	@Override
	public void onBackPressed() {
		scrollToFinishActivity();
	}
}
