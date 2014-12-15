package org.mewx.lightnovellibrary.activity;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.component.XMLParser;
import org.mewx.lightnovellibrary.util.LightCache;
import org.mewx.lightnovellibrary.util.LightNetwork;

import com.nostra13.universalimageloader.core.ImageLoader;

import cn.wenku8.api.Wenku8Interface;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class NovelInfoActivity extends ActionBarActivity {
	// get "aid" and "plus"
	private String name, from;
	private int aid;
	private ActionBarActivity parentActivity = null;
	private XMLParser.NovelIntro ni = null;
	private List<XMLParser.VolumeList> vl = null;
	private boolean isLoading;

	// save xml
	private String introXml, volumeXml;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_novel_info);

		// get parentActivity
		parentActivity = this;
		isLoading = true;

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
						finish();
					}
				});

		findViewById(R.id.btnEdit).setOnClickListener(
				new View.OnClickListener() {
					@Override
					public void onClick(View view) {
						if (isLoading) {
							Toast.makeText(parentActivity, "Loading...",
									Toast.LENGTH_SHORT).show();
							return;
						}

						// Build alert dialog
						// set options
						new AlertDialog.Builder(parentActivity)
								.setTitle(
										getResources().getString(
												R.string.novel_function))
								.setNegativeButton(
										"Cancel",
										new android.content.DialogInterface.OnClickListener() {

											@Override
											public void onClick(
													DialogInterface dialog,
													int which) {
												return; // empty body
											}

										})
								.setItems(
										new String[] { getResources()
												.getString(
														R.string.novel_add_to_shelf),
										// these two in bookshelf
										// getResources().getString(
										// R.string.novel_cache),
										// getResources()
										// .getString(
										// R.string.novel_force_reload)
										},
										new android.content.DialogInterface.OnClickListener() {
											public void onClick(
													DialogInterface dialog,
													int which) {
												// "which" is arrayId, from 0
												switch (which) {
												case 0:
													// add to shelf
													// save introXml, volumeXml
													if (!GlobalConfig
															.testInLocalBookshelf(aid)) {
														// avoid re-add
														GlobalConfig
																.writeFullFileIntoSaveFolder(
																		"intro",
																		aid
																				+ "-intro.xml",
																		introXml);

														GlobalConfig
																.writeFullFileIntoSaveFolder(
																		"intro",
																		aid
																				+ "-introfull.xml",
																		ni.intro_full);

														GlobalConfig
																.writeFullFileIntoSaveFolder(
																		"intro",
																		aid
																				+ "-volume.xml",
																		volumeXml);
														GlobalConfig
																.addToLocalBookshelf(aid);
													}

													Toast.makeText(
															parentActivity,
															getResources()
																	.getString(
																			R.string.novel_added_to_shelf),
															Toast.LENGTH_SHORT)
															.show();
													break;

												case 1:
													// cache
													// save books/cid.wk8
													// save imgs/cid-imageid.jpg
													// if exist, skip
													break;

												case 2:
													// force reload
													// reload all, even if exist
													break;
												}
											}
										}).show();
					}
				});

		((ImageView) parentActivity.findViewById(R.id.novel_cover))
				.setImageResource(R.drawable.empty_cover);
		// ImageLoader.getInstance().displayImage("drawable://R.drawable.empty_cover",
		// (ImageView) parentActivity.findViewById(R.id.novel_cover));

		// Interpret the in-coming "code" and "plus"
		name = getIntent().getStringExtra("title");
		aid = getIntent().getIntExtra("aid", 0);
		from = getIntent().getStringExtra("from");
		if (from == null)
			from = "";
		((TextView) findViewById(R.id.textTitle)).setText(name);

		if (aid == 0)
			Log.e("MewX", "aid fetch failed."); // failed

		// fetch novel info
		List<NameValuePair> targVarList = new ArrayList<NameValuePair>();
		targVarList.add(Wenku8Interface.getNovelShortInfo(aid,
				GlobalConfig.getFetchLanguage()));

		asyncTask ast = new asyncTask();
		ast.execute(targVarList);

		// fetch volume list
		List<NameValuePair> targVarListVolume = new ArrayList<NameValuePair>();
		targVarListVolume.add(Wenku8Interface.getNovelIndex(aid,
				GlobalConfig.getFetchLanguage()));

		asyncVolumeTask astVolume = new asyncVolumeTask();
		astVolume.execute(targVarListVolume);

		return;
	}

	class asyncTask extends AsyncTask<List<NameValuePair>, Integer, Integer> {
		// fail return -1
		@Override
		protected Integer doInBackground(List<NameValuePair>... params) {

			try {
				if (from.equals(BookshelfFragment.fromid))
					introXml = GlobalConfig.loadFullFileFromSaveFolder("intro",
							aid + "-intro.xml");
				else
					introXml = new String(LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, params[0]), "UTF-8");
				ni = XMLParser.getNovelIntro(introXml);
				if (ni == null) {
					Log.e("MewX-Main",
							"getNullFromParser - XMLParser.NovelIntro");
					return -100; // network error
				}

				List<NameValuePair> targIntro = new ArrayList<NameValuePair>();
				targIntro.add(Wenku8Interface.getNovelFullIntro(ni.aid,
						GlobalConfig.getFetchLanguage()));
				if (from.equals(BookshelfFragment.fromid))
					ni.intro_full = GlobalConfig.loadFullFileFromSaveFolder(
							"intro", aid + "-introfull.xml");
				else
					ni.intro_full = new String(LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, targIntro), "UTF-8");

				return 0;
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
				Toast.makeText(parentActivity,
						getResources().getString(R.string.network_error),
						Toast.LENGTH_LONG).show();
				return;
			}

			ImageView iv = (ImageView) parentActivity
					.findViewById(R.id.novel_cover);

			if (LightCache
					.testFileExist(GlobalConfig.getFirstStoragePath() + "imgs"
							+ File.separator + String.valueOf(ni.aid) + ".jpg") == true) {

				ImageLoader.getInstance().displayImage(
						("file://" + GlobalConfig.getFirstStoragePath()
								+ "imgs" + File.separator
								+ String.valueOf(ni.aid) + ".jpg"), iv);

			} else if (LightCache.testFileExist(GlobalConfig
					.getSecondStoragePath()
					+ "imgs"
					+ File.separator
					+ String.valueOf(ni.aid) + ".jpg") == true) {
				ImageLoader.getInstance().displayImage(
						("file://" + GlobalConfig.getSecondStoragePath()
								+ "imgs" + File.separator
								+ String.valueOf(ni.aid) + ".jpg"), iv);

			} else {
				ImageLoader.getInstance().displayImage(
						("assets://emtpy_cover.png"), iv);
			}

			((TextView) parentActivity.findViewById(R.id.novel_name))
					.setText(ni.title);
			((TextView) parentActivity.findViewById(R.id.novel_author_text))
					.setText(ni.author);
			((TextView) parentActivity.findViewById(R.id.novel_update_text))
					.setText(ni.update);
			((TextView) parentActivity.findViewById(R.id.novel_intro_text))
					.setText(ni.intro_full);

			if (ni.status == 0)
				((TextView) findViewById(R.id.novel_status_text))
						.setText(getResources().getString(
								R.string.novel_status_0));
			else
				((TextView) findViewById(R.id.novel_status_text))
						.setText(getResources().getString(
								R.string.novel_status_1));
			return;
		}
	}

	class asyncVolumeTask extends
			AsyncTask<List<NameValuePair>, Integer, Integer> {
		// fail return -1
		@Override
		protected Integer doInBackground(List<NameValuePair>... params) {

			try {
				if (from.equals(BookshelfFragment.fromid))
					volumeXml = GlobalConfig.loadFullFileFromSaveFolder(
							"intro", aid + "-volume.xml");
				else
					volumeXml = new String(LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, params[0]), "UTF-8");

				vl = XMLParser.getVolumeList(volumeXml);
				if (vl == null) {
					Log.e("MewX-Main",
							"getNullFromParser (vl = XMLParser.getVolumeList(xml);)");
					return -101;
				}

				return 0;
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
			if (result == -1)
				return;
			else if (result == -101) {
				Toast.makeText(parentActivity,
						getResources().getString(R.string.parse_failed),
						Toast.LENGTH_LONG).show();
				return;
			}

			LinearLayout layout = (LinearLayout) parentActivity
					.findViewById(R.id.hostLayout);

			for (int i = 0; i < vl.size(); i++) {
				TextView tempTV = new TextView(parentActivity);
				tempTV.setText("- " + vl.get(i).volumeName + " -");
				tempTV.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20); // 20sp
				tempTV.setPadding(0, 20, 0, 0);
				layout.addView(tempTV);

				Log.v("MewX",
						"vl.get(" + i + ").chapterList.size()="
								+ vl.get(i).chapterList.size());
				for (int j = 0; j < vl.get(i).chapterList.size(); j++) {

					// add button on it
					Button temp = new Button(parentActivity);
					temp.setText(vl.get(i).chapterList.get(j).chapterName);
					temp.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15); // 15sp

					// set click listener
					final String t = vl.get(i).chapterList.get(j).chapterName;
					final int cid = vl.get(i).chapterList.get(j).cid;
					final int vid = vl.get(i).vid;
					temp.setOnClickListener(new OnClickListener() {
						public void onClick(View v) {
							Toast.makeText(parentActivity, t,
									Toast.LENGTH_SHORT).show();
							viewChapter(vid, cid);
							return;
						}
					});
					layout.addView(temp);
				}

			}

			isLoading = false;
			return;
		}
	}

	private void viewChapter(int vid, int cid) {
		// to new activity
		Intent intent = new Intent();
		intent.setClass(parentActivity, NovelReaderActivity.class);
		intent.putExtra("aid", aid);
		intent.putExtra("vid", vid);
		intent.putExtra("cid", cid);
		intent.putExtra("from", from);
		startActivity(intent);
		return;
	}
}
