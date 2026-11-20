package org.mewx.lightnovellibrary.activity;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import me.imid.swipebacklayout.lib.SwipeBackLayout;
import me.imid.swipebacklayout.lib.app.SwipeBackActivity;

import org.apache.http.NameValuePair;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.api.Wenku8Interface;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.component.NovelContentParser;
import org.mewx.lightnovellibrary.component.XMLParser;
import org.mewx.lightnovellibrary.util.LightNetwork;

import com.nostra13.universalimageloader.core.ImageLoader;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.ImageView.ScaleType;

public class NovelReaderActivity extends SwipeBackActivity {
	private String from;
	private int currentAid, currentVid, currentCid;
	private List<NovelContentParser.NovelContent> nc = null;
	private List<XMLParser.VolumeList> vl = null;

	private Activity parentActivity = null;
	private ProgressDialog pDialog = null;

	// slide back
	private SwipeBackLayout mSwipeBackLayout;

	// Scroll runnable
	private Runnable runnableScroll = new Runnable() {
		@Override
		public void run() {
			((ScrollView) parentActivity.findViewById(R.id.content_scrollview))
					.scrollTo(0, GlobalConfig.getReadSavesRecord(currentCid,
							((LinearLayout) parentActivity
									.findViewById(R.id.novel_content_layout))
									.getMeasuredHeight()));
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
				WindowManager.LayoutParams.FLAG_FULLSCREEN);
		setContentView(R.layout.activity_novel_content);

		mSwipeBackLayout = getSwipeBackLayout();
		mSwipeBackLayout.setScrimColor(Color.TRANSPARENT);
		mSwipeBackLayout.setEdgeTrackingEnabled(SwipeBackLayout.EDGE_LEFT);

		// fetch vars
		parentActivity = this;
		currentAid = getIntent().getIntExtra("aid", 1);
		currentVid = getIntent().getIntExtra("vid", 1);
		currentCid = getIntent().getIntExtra("cid", 1);
		from = getIntent().getStringExtra("from");
		if (from == null)
			from = "";
		vl = (List<XMLParser.VolumeList>) getIntent().getSerializableExtra(
				"volumes");
		if (vl == null)
			Log.v("MewX", "vl is null from getIntentExtra");

		// menu
		((LinearLayout) parentActivity.findViewById(R.id.novel_content_layout))
				.setOnClickListener(new OnClickListener() {
					@Override
					public void onClick(View v) {
						// TODO Auto-generated method stub
						((RelativeLayout) parentActivity
								.findViewById(R.id.floating_frame))
								.setVisibility(View.VISIBLE);
					}
				});
		((RelativeLayout) parentActivity.findViewById(R.id.floating_frame))
				.setOnTouchListener(new OnTouchListener() {
					@Override
					public boolean onTouch(View v, MotionEvent event) {
						// TODO Auto-generated method stub
						((RelativeLayout) parentActivity
								.findViewById(R.id.floating_frame))
								.setVisibility(View.GONE);
						return true;
					}
				});

		// previous and next chapter button event
		((Button) parentActivity.findViewById(R.id.previous_chapter))
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						Log.v("MewX", "[P0]currentCid: " + currentCid);

						// save read record
						saveRecord();

						// goto previous chapter
						int pre = 0, next = 0, current = 0; // by default
						outer: for (XMLParser.VolumeList tempVl : vl) {
							for (XMLParser.ChapterInfo tempCi : tempVl.chapterList) {
								if (tempCi.cid == currentCid && current != 0) {
									pre = current; // get previous
									currentCid = pre;
									currentVid = tempVl.vid;
									break outer;
								}
								current = tempCi.cid;
							}
						}

						Log.v("MewX", "[P1]currentCid: " + currentCid
								+ "; pre: " + pre + "; current: " + current);
						if (pre == 0)
							Toast.makeText(parentActivity, "No more",
									Toast.LENGTH_SHORT).show();
						else {
							// refresh view
							((LinearLayout) parentActivity
									.findViewById(R.id.novel_content_layout))
									.removeAllViews();
							getNC();
						}

						return;
					}

				});
		((Button) parentActivity.findViewById(R.id.next_chapter))
				.setOnClickListener(new OnClickListener() {

					@Override
					public void onClick(View v) {
						// save read record
						saveRecord();
						Log.v("MewX", "[N0]currentCid: " + currentCid);

						// goto previous chapter
						int pre = 0, next = 0, current = 0; // by default
						outer: for (XMLParser.VolumeList tempVl : vl) {
							for (XMLParser.ChapterInfo tempCi : tempVl.chapterList) {
								if (tempCi.cid == currentCid) {
									pre = current; // get previous
								} else if (pre != 0 || pre == 0
										&& current == currentCid) {
									next = tempCi.cid; // get next
									currentCid = next;
									currentVid = tempVl.vid;
									break outer;
								}
								current = tempCi.cid;
							}
						}

						Log.v("MewX", "[N1]currentCid: " + currentCid
								+ "; pre: " + pre + "; current: " + current);
						if (next == 0)
							Toast.makeText(parentActivity, "No more",
									Toast.LENGTH_SHORT).show();
						else {
							// refresh view
							((LinearLayout) parentActivity
									.findViewById(R.id.novel_content_layout))
									.removeAllViews();
							getNC();
						}
						return;
					}

				});

		// fill text
		getNC(); // get novel content
	}

	private void getNC() {
		List<NameValuePair> targVar = new ArrayList<NameValuePair>();
		targVar.add(Wenku8Interface.getNovelContent(currentAid, currentCid,
				GlobalConfig.getFetchLanguage()));

		final asyncNovelContentTask ast = new asyncNovelContentTask();
		ast.execute(targVar);

		pDialog = new ProgressDialog(parentActivity);
		pDialog.setTitle(getResources().getString(R.string.load_status));
		pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
		pDialog.setCancelable(true);
		pDialog.setOnCancelListener(new OnCancelListener() {
			@Override
			public void onCancel(DialogInterface dialog) {
				// TODO Auto-generated method stub
				ast.cancel(true);
				pDialog.dismiss();
				pDialog = null;
			}

		});
		pDialog.setMessage(getResources().getString(R.string.load_loading));
		pDialog.setProgress(0);
		pDialog.setMax(1);
		pDialog.show();

		return;
	}

	class asyncNovelContentTask extends
			AsyncTask<List<NameValuePair>, Integer, Integer> {
		// fail return -1
		@Override
		protected Integer doInBackground(List<NameValuePair>... params) {

			try {
				String xml;
				if (from.equals(BookshelfFragment.fromid))
					xml = GlobalConfig.loadFullFileFromSaveFolder("novel",
							currentCid + ".xml");
				else {
					byte[] tempXml = LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, params[0]);
					if (tempXml == null)
						return -100;
					xml = new String(tempXml, "UTF-8");
				}

				nc = NovelContentParser.parseNovelContent(xml, pDialog);
				if (nc == null || nc.size() == 0) {
					Log.e("MewX-Main",
							"getNullFromParser (NovelContentParser.parseNovelContent(xml);)");

					// network error or parse failed
					return -100;
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
				if (from.equals(BookshelfFragment.fromid)) {
					Toast.makeText(
							parentActivity,
							getResources().getString(
									R.string.bookshelf_not_cached),
							Toast.LENGTH_LONG).show();

					new AlertDialog.Builder(parentActivity)
							.setTitle(
									getResources()
											.getString(
													R.string.bookshelf_did_not_find_cache))
							.setMessage(
									getResources()
											.getString(
													R.string.bookshelf_want_to_connect_to_Internet))
							.setPositiveButton(
									"YES",
									new android.content.DialogInterface.OnClickListener() {

										@Override
										public void onClick(
												DialogInterface dialog,
												int which) {
											if (pDialog != null)
												pDialog.dismiss();

											// connect to the Internet to load
											from = "";
											List<NameValuePair> targVar = new ArrayList<NameValuePair>();
											targVar.add(Wenku8Interface
													.getNovelContent(
															currentAid,
															currentCid,
															GlobalConfig
																	.getFetchLanguage()));

											final asyncNovelContentTask ast = new asyncNovelContentTask();
											ast.execute(targVar);
											return;
										}

									})
							.setNegativeButton(
									"NO",
									new android.content.DialogInterface.OnClickListener() {

										@Override
										public void onClick(
												DialogInterface dialog,
												int which) {
											onBackPressed();
										}
									}).show();
				} else
					Toast.makeText(parentActivity,
							getResources().getString(R.string.network_error),
							Toast.LENGTH_LONG).show();
				if (pDialog != null)
					pDialog.dismiss();
				return;
			}

			// generate listview to contain the texts and images
			// ListView lv = (ListView) parentActivity
			// .findViewById(R.id.content_list);
			// if (lv == null) {
			// Log.e("MewX", "NovelReaderActivity ListView == null!");
			// return;
			// }
			// lv.setDivider(null);
			// lv.setAdapter(new NovelContentAdapter(parentActivity, nc));
			// pDialog.setProgress(nc.size());

			// The abandoned way - dynamically addign textview into layout
			LinearLayout layout = (LinearLayout) parentActivity
					.findViewById(R.id.novel_content_layout);

			for (int i = 0; i < nc.size(); i++) {
				if (pDialog != null)
					pDialog.setProgress(i);

				switch (nc.get(i).type) {
				case 't':
					TextView tempTV = new TextView(parentActivity);
					if (i == 0) {
						tempTV.setTextSize(TypedValue.COMPLEX_UNIT_SP,
								GlobalConfig.getShowTextSize() + 6);
						Shader shader = new LinearGradient(0, 0, 0,
								tempTV.getTextSize(), 0xFF003399, 0xFF6699FF,
								Shader.TileMode.CLAMP);
						tempTV.getPaint().setShader(shader);
					} else {
						tempTV.setTextSize(TypedValue.COMPLEX_UNIT_SP,
								GlobalConfig.getShowTextSize());
					}
					tempTV.setText(nc.get(i).content);
					tempTV.setPadding(GlobalConfig.getShowTextPaddingLeft(),
							GlobalConfig.getShowTextPaddingTop(),
							GlobalConfig.getShowTextPaddingRight(), 0);
					layout.addView(tempTV);
					break;

				case 'i':
					final ImageView tempIV = new ImageView(parentActivity);
					tempIV.setClickable(true);
					tempIV.setAdjustViewBounds(true);
					tempIV.setScaleType(ScaleType.FIT_CENTER);// CENTER_INSIDE
					tempIV.setPadding(0, GlobalConfig.getShowTextPaddingTop(),
							0, 0);
					tempIV.setImageResource(R.drawable.empty_cover); // default

					// async loader
					final String imgFileName = GlobalConfig
							.generateImageFileNameByURL(nc.get(i).content);
					final String path = GlobalConfig
							.getAvailableNovolContentImagePath(imgFileName);

					if (path != null) {
						ImageLoader.getInstance().displayImage(
								"file://" + path, tempIV);

						tempIV.setOnClickListener(new OnClickListener() {
							@Override
							public void onClick(View v) {
								// TODO Auto-generated method stub
								Intent intent = new Intent();
								intent.setClass(parentActivity,
										NovelImageActivity.class);
								intent.putExtra("path", path);
								startActivity(intent);
								parentActivity.overridePendingTransition(
										R.anim.in_from_right, R.anim.keep);
							}
						});
					} else {
						// define another asynctask to load image
						// need to access local var - tempIV
						class asyncDownloadImage extends
								AsyncTask<String, Integer, String> {
							@Override
							protected String doInBackground(String... params) {
								GlobalConfig.saveNovelContentImage(params[0]);
								String name = GlobalConfig
										.generateImageFileNameByURL(params[0]);
								return GlobalConfig
										.getAvailableNovolContentImagePath(name);
							}

							@Override
							protected void onPostExecute(final String result) {
								ImageLoader.getInstance().displayImage(
										"file://" + result, tempIV);

								tempIV.setOnClickListener(new OnClickListener() {
									@Override
									public void onClick(View v) {
										Intent intent = new Intent();
										intent.setClass(parentActivity,
												NovelImageActivity.class);
										intent.putExtra("path", result);
										startActivity(intent);
										parentActivity
												.overridePendingTransition(
														R.anim.in_from_right,
														R.anim.keep);
									}
								});

								return;
							}

						}
						asyncDownloadImage async = new asyncDownloadImage();
						async.execute(nc.get(i).content);
					}

					layout.addView(tempIV);
					break;

				}
			}

			// end loading dialog
			if (pDialog != null)
				pDialog.dismiss();

			// show dialog
			if (GlobalConfig.getReadSavesRecord(currentCid,
					((LinearLayout) parentActivity
							.findViewById(R.id.novel_content_layout))
							.getMeasuredHeight()) > 100) {
				new AlertDialog.Builder(parentActivity)
						.setTitle(getResources().getString(R.string.novel_load))
						.setMessage(
								getResources().getString(
										R.string.novel_load_question))
						.setPositiveButton(
								"YES",
								new android.content.DialogInterface.OnClickListener() {

									@Override
									public void onClick(DialogInterface dialog,
											int which) {
										// set scroll view
										Handler handler = new Handler();
										handler.postDelayed(runnableScroll, 200);

										Toast.makeText(
												parentActivity,
												"Scroll to = "
														+ GlobalConfig
																.getReadSavesRecord(
																		currentCid,
																		((LinearLayout) parentActivity
																				.findViewById(R.id.novel_content_layout))
																				.getMeasuredHeight()),
												Toast.LENGTH_SHORT).show();

									}

								}).setNegativeButton("NO", null).show();
			}

			return;
		}
	}

	@Override
	protected void onPause() {
		super.onPause();

		saveRecord();
		return;
	}

	private void saveRecord() {
		// cannot get height easily, except sum one by one
		GlobalConfig.addReadSavesRecord(currentCid,
				((ScrollView) parentActivity
						.findViewById(R.id.content_scrollview)).getScrollY(),
				((LinearLayout) parentActivity
						.findViewById(R.id.novel_content_layout))
						.getMeasuredHeight());
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

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event) {
		switch (keyCode) {
		case KeyEvent.KEYCODE_VOLUME_DOWN:
			((LinearLayout) parentActivity
					.findViewById(R.id.novel_content_layout))
					.setBackgroundColor(0xff666666);
			return true;
		case KeyEvent.KEYCODE_VOLUME_UP:
			((LinearLayout) parentActivity
					.findViewById(R.id.novel_content_layout))
					.setBackgroundColor(0xffeeeeee);
			return true;
		}
		return super.onKeyDown(keyCode, event);
	}

	@Override
	public void onBackPressed() {
		scrollToFinishActivity();
	}
}
