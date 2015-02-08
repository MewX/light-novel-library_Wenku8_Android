package org.mewx.lightnovellibrary.activity;

import java.io.File;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import me.imid.swipebacklayout.lib.SwipeBackLayout;
import me.imid.swipebacklayout.lib.app.SwipeBackActivity;

import org.apache.http.NameValuePair;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.api.Wenku8Interface;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.component.XMLParser;
import org.mewx.lightnovellibrary.util.LightCache;
import org.mewx.lightnovellibrary.util.LightNetwork;

import com.nostra13.universalimageloader.core.ImageLoader;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class NovelInfoActivity extends SwipeBackActivity {
	// get "aid" and "plus"
	private String name, from;
	private int aid;
	private Activity parentActivity = null;
	private XMLParser.NovelFullInfo ni = null;
	private List<XMLParser.VolumeList> vl = null;
	private boolean isLoading;

	// save xml
	private String introXml, volumeXml;

	// slide back
	private SwipeBackLayout mSwipeBackLayout;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		// requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_novel_info);

		mSwipeBackLayout = getSwipeBackLayout();
		mSwipeBackLayout.setScrimColor(Color.TRANSPARENT);
		mSwipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_LEFT);

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
						onBackPressed();
						// finish();
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
																		ni.fullIntro);

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

		// fetch novel info ( full info )
		List<NameValuePair> targVarList = new ArrayList<NameValuePair>();
		targVarList.add(Wenku8Interface.getNovelFullMeta(aid,
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
				else {
					byte[] tempIntroXml = LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, params[0]);
					if (tempIntroXml == null) {
						Log.e("MewX-Main", "getNullFromHTTPPost");
						return -100; // network error
					}
					introXml = new String(tempIntroXml, "UTF-8");
				}
				ni = XMLParser.getNovelFullInfo(introXml); // get NovelInfo

				List<NameValuePair> targIntro = new ArrayList<NameValuePair>();
				targIntro.add(Wenku8Interface.getNovelFullIntro(ni.aid,
						GlobalConfig.getFetchLanguage()));
				if (from.equals(BookshelfFragment.fromid))
					ni.fullIntro = GlobalConfig.loadFullFileFromSaveFolder(
							"intro", aid + "-introfull.xml");
				else {
					byte[] tempFullIntro = LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, targIntro);
					if (tempFullIntro == null) {
						Log.e("MewX-Main", "getNullFromHTTPPost");
						return -100; // network error
					}
					ni.fullIntro = new String(tempFullIntro, "UTF-8");
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
					.setText(ni.lastUpdate);
			((TextView) parentActivity.findViewById(R.id.novel_latest_text))
					.setText(ni.latestSectionName);
			((TextView) parentActivity.findViewById(R.id.novel_intro_text))
					.setText(ni.fullIntro);
			((TextView) findViewById(R.id.novel_status_text))
					.setText(ni.bookStatus);

			// old status
			// if (ni.status == 0)
			// ((TextView) findViewById(R.id.novel_status_text))
			// .setText(getResources().getString(
			// R.string.novel_status_0));
			// else
			// ((TextView) findViewById(R.id.novel_status_text))
			// .setText(getResources().getString(
			// R.string.novel_status_1));
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
				else {
					byte[] tempVolumeXml = LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, params[0]);
					if (tempVolumeXml == null) {
						Log.e("MewX-Main", "getNullFromHTTPPost");
						return -100; // network error
					}
					volumeXml = new String(tempVolumeXml, "UTF-8");
				}

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
			} else if (result == -100) {
				Toast.makeText(parentActivity,
						getResources().getString(R.string.network_error),
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
		intent.putExtra("volumes", (Serializable) vl); // force convert
		startActivity(intent);
		overridePendingTransition(R.anim.in_from_right, R.anim.keep);
		return;
	}

	@Override
	public void onBackPressed() {
		scrollToFinishActivity();
	}
}
