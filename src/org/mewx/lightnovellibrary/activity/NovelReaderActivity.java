package org.mewx.lightnovellibrary.activity;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.component.NovelContentParser;
import org.mewx.lightnovellibrary.util.LightNetwork;

import com.nostra13.universalimageloader.core.ImageLoader;

import cn.wenku8.api.Wenku8Interface;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.Window;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ImageView.ScaleType;

public class NovelReaderActivity extends ActionBarActivity {
	private String from;
	private int currentAid, currentVid, currentCid;
	private List<NovelContentParser.NovelContent> nc = null;

	private ActionBarActivity parentActivity = null;
	private ProgressDialog pDialog = null;

	// Scroll runnable
	private Runnable runnableScroll = new Runnable() {
		@Override
		public void run() {
			((ScrollView) parentActivity.findViewById(R.id.content_scrollview))
					.scrollTo(0, GlobalConfig.getReadSavesRecord(currentCid,
							((LinearLayout) parentActivity
									.findViewById(R.id.novel_layout))
									.getMeasuredHeight()));
		}
	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.activity_novel_content);

		// fetch vars
		parentActivity = this;
		currentAid = getIntent().getIntExtra("aid", 1);
		currentVid = getIntent().getIntExtra("vid", 1);
		currentCid = getIntent().getIntExtra("cid", 1);
		from = getIntent().getStringExtra("from");
		if (from == null)
			from = "";

		// fill text
		getNC();
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
				else
					xml = new String(LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, params[0]), "UTF-8");

				nc = NovelContentParser.parseNovelContent(xml, pDialog);
				if (nc == null || nc.size() == 0) {
					Log.e("MewX-Main",
							"getNullFromParser (NovelContentParser.parseNovelContent(xml);)");
					return -100; // network error or parse failed
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
				if (from.equals(BookshelfFragment.fromid))
					Toast.makeText(
							parentActivity,
							getResources().getString(
									R.string.bookshelf_not_cached),
							Toast.LENGTH_LONG).show();
				else
					Toast.makeText(parentActivity,
							getResources().getString(R.string.network_error),
							Toast.LENGTH_LONG).show();
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
				pDialog.setProgress(i);

				switch (nc.get(i).type) {
				case 't':
					TextView tempTV = new TextView(parentActivity);
					tempTV.setText(nc.get(i).content);
					tempTV.setTextSize(TypedValue.COMPLEX_UNIT_SP,
							GlobalConfig.getShowTextSize());
					tempTV.setPadding(GlobalConfig.getShowTextPaddingLeft(),
							GlobalConfig.getShowTextPaddingTop(),
							GlobalConfig.getShowTextPaddingRight(), 0);
					layout.addView(tempTV);
					break;

				case 'i':
					ImageView tempIV = new ImageView(parentActivity);
					tempIV.setClickable(true);
					tempIV.setScaleType(ScaleType.CENTER_INSIDE); // .FIT_CENTER
					tempIV.setPadding(0, GlobalConfig.getShowTextPaddingTop(),
							0, 0);
					tempIV.setImageResource(R.drawable.empty_cover);
					layout.addView(tempIV);

					// async loader
					ImageLoader.getInstance().displayImage(nc.get(i).content,
							tempIV);

					break;

				}
			}

			// end loading dialog
			pDialog.dismiss();

			// show dialog
			if (GlobalConfig.getReadSavesRecord(currentCid,
					((LinearLayout) parentActivity
							.findViewById(R.id.novel_layout))
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
																				.findViewById(R.id.novel_layout))
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

		// cannot get height easily, except sum one by one
		GlobalConfig.addReadSavesRecord(currentCid,
				((ScrollView) parentActivity
						.findViewById(R.id.content_scrollview)).getScrollY(),
				((LinearLayout) parentActivity.findViewById(R.id.novel_layout))
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
}
