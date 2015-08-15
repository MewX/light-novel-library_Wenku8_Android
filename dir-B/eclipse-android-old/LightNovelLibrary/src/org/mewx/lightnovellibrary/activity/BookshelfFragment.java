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
import org.mewx.lightnovellibrary.api.Wenku8Interface;
import org.mewx.lightnovellibrary.component.GlobalConfig;
import org.mewx.lightnovellibrary.component.NovelContentParser;
import org.mewx.lightnovellibrary.component.XMLParser;
import org.mewx.lightnovellibrary.component.adapter.NovelElement;
import org.mewx.lightnovellibrary.component.adapter.NovelElementAdapter;
import org.mewx.lightnovellibrary.component.adapter.NovelElementSearch;
import org.mewx.lightnovellibrary.component.adapter.NovelElementSearchAdapter;
import org.mewx.lightnovellibrary.util.LightCache;
import org.mewx.lightnovellibrary.util.LightNetwork;

import com.special.ResideMenu.ResideMenu;
import com.umeng.analytics.MobclickAgent;

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

public class BookshelfFragment extends Fragment {
	public static String fromid = "bookshelf";
	private View parentView;
	private ResideMenu resideMenu;
	private ProgressDialog pDialog;
	private boolean isLoading;

	// list item
	private ArrayList<Integer> al;
	private ArrayList<NovelElementSearch> alne;

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
				btnEditOnClickListener);

		return parentView;
	}

	private OnClickListener btnEditOnClickListener = new View.OnClickListener() {
		@Override
		public void onClick(View view) {
			// Edit button menu
			new AlertDialog.Builder(getActivity())
					.setTitle(
							getResources().getString(R.string.novels_function))
					.setNegativeButton(
							"Cancel",
							new android.content.DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									return; // empty body
								}

							})
					.setItems(
							new String[] {
									getResources().getString(
											R.string.bookshelf_cache_all),
									getResources().getString(
											R.string.bookshelf_statistics),
									getResources()
											.getString(
													R.string.bookshelf_sync_from_online),
									getResources().getString(
											R.string.bookshelf_sync_to_online),
									getResources()
											.getString(
													R.string.bookshelf_sync_to_online_strict) },
							new android.content.DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									// "which" is arrayId, from 0
									switch (which) {
									case 0:
										// cache all
										// save books/cid.wk8
										// save imgs/cid-imageid.jpg
										// if exist, skip
										isLoading = true;
										ArrayList<Integer> tempAL = GlobalConfig
												.getLocalBookshelfList();
										Integer[] aidList = new Integer[tempAL
												.size()];
										for (int i = 0; i < aidList.length; i++)
											aidList[i] = tempAL.get(i);
										final asyncUpdateCacheTask auct = new asyncUpdateCacheTask();
										auct.execute(aidList); // a list

										// show progress dialog
										pDialog = new ProgressDialog(
												getActivity());
										pDialog.setTitle(getResources()
												.getString(
														R.string.bookshelf_caching));
										pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
										pDialog.setCancelable(true);
										pDialog.setOnCancelListener(new OnCancelListener() {
											@Override
											public void onCancel(
													DialogInterface dialog) {
												// TODO Auto-generated
												// method stub
												auct.cancel(true);
												isLoading = false;
												Toast.makeText(
														getActivity(),
														getResources()
																.getString(
																		R.string.cancelled),
														Toast.LENGTH_SHORT)
														.show();
												pDialog = null;
											}

										});
										pDialog.setMessage(getResources()
												.getString(
														R.string.search_fetching)
												+ getResources()
														.getString(
																R.string.bookshelf_all_book));
										pDialog.setProgress(0);
										pDialog.setMax(1);
										pDialog.show();
										break;

									case 1:
										// check out statistics
										Toast.makeText(
												getActivity(),
												getResources().getString(
														R.string.in_building),
												Toast.LENGTH_SHORT).show();
										break;

									case 2:
										// sync from online bookshelf
										Toast.makeText(
												getActivity(),
												getResources().getString(
														R.string.in_building),
												Toast.LENGTH_SHORT).show();
										break;

									case 3:
										// sync to online bookshelf (only new)
										Toast.makeText(
												getActivity(),
												getResources().getString(
														R.string.in_building),
												Toast.LENGTH_SHORT).show();
										break;

									case 4:
										// sync to online bookshelf (strictly)
										// empty cloud, and sync to cloud
										Toast.makeText(
												getActivity(),
												getResources().getString(
														R.string.in_building),
												Toast.LENGTH_SHORT).show();
										break;
									}
								}
							}).show();
		}
	};

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

	public void onPause() {
		super.onPause();
		MobclickAgent.onPageEnd("Bookshelf");
	}

	@Override
	public void onResume() {
		super.onResume();
		MobclickAgent.onPageStart("Bookshelf");

		// fill list
		al = GlobalConfig.getLocalBookshelfList();
		alne = new ArrayList<NovelElementSearch>();

		for (int i = 0; i < al.size(); i++) {
			String xml = GlobalConfig.loadFullFileFromSaveFolder("intro",
					al.get(i) + "-intro.xml");
			if (xml.equals("")) {
				// the intro file was deleted
				Toast.makeText(
						getActivity(),
						al.get(i)
								+ getResources().getString(
										R.string.bookshelf_intro_load_failed),
						Toast.LENGTH_SHORT).show();
				GlobalConfig.removeFromLocalBookshelf(al.get(i));
				continue;
			}

			XMLParser.NovelFullInfo nfi = XMLParser.getNovelFullInfo(xml);

			// int aid, String title, String author, int status,
			// String update, String intro, String imgUrl
			// !! judge this status, API has some awful features !!
			NovelElementSearch ne = new NovelElementSearch(al.get(i),
					nfi.title, nfi.author, nfi.bookStatus.equals(getResources()
							.getString(R.string.novel_status_1_sc))
							|| nfi.bookStatus.equals(getResources().getString(
									R.string.novel_status_1_tc)) ? 1 : 0,
					nfi.lastUpdate, nfi.fullIntro, null);
			alne.add(ne);
		}

		if (alne.size() == 0) {
			Toast.makeText(getActivity(),
					getResources().getString(R.string.search_result_none),
					Toast.LENGTH_SHORT).show();
			// return; // this will cause a bug: when list is empty by deletion
		}

		NovelElementSearchAdapter adapter = new NovelElementSearchAdapter(
				getActivity(), alne);
		ListView listViewNew = (ListView) parentView
				.findViewById(R.id.novel_list);
		listViewNew.setDivider(null);
		listViewNew.setAdapter(adapter);
		listViewNew.setOnItemClickListener(localBookOnItemClickListener);
		listViewNew
				.setOnItemLongClickListener(localBookOnItemLongClickListener);

		return;
	}

	private OnItemClickListener localBookOnItemClickListener = new OnItemClickListener() {
		// Click on ListView
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			NovelElementSearch ne = alne.get(position);
			GlobalConfig.accessToLocalBookshelf(al.get(position));
			Log.v("MewX", "NovelElement clicked: position=" + position
					+ "; getName=" + ne.getTitle());

			// to new activity
			Intent intent = new Intent();
			intent.setClass(getActivity(), NovelInfoActivity.class);
			intent.putExtra("title", ne.getTitle());
			intent.putExtra("aid", ne.getAid());
			intent.putExtra("from", fromid);
			startActivity(intent);
			getActivity().overridePendingTransition(R.anim.in_from_right,
					R.anim.keep);
		}
	};

	private OnItemLongClickListener localBookOnItemLongClickListener = new OnItemLongClickListener() {
		// Long click to pop up menu
		@Override
		public boolean onItemLongClick(AdapterView<?> parent, View view,
				int position, long id) {
			final NovelElementSearch ne = alne.get(position);

			// popup book menu
			new AlertDialog.Builder(getActivity())
					.setTitle(getResources().getString(R.string.novel_function))
					.setNegativeButton(
							"Cancel",
							new android.content.DialogInterface.OnClickListener() {

								@Override
								public void onClick(DialogInterface dialog,
										int which) {
									return; // empty body
								}

							})
					.setItems(
							new String[] {
									getResources().getString(
											R.string.bookshelf_cache_one),
									getResources()
											.getString(
													R.string.bookshelf_force_reload_one),
									getResources().getString(
											R.string.bookshelf_remove) },
							new android.content.DialogInterface.OnClickListener() {
								public void onClick(DialogInterface dialog,
										int which) {
									// "which" is arrayId, from 0
									switch (which) {
									case 0:
										// cache all
										// save books/cid.wk8
										// save imgs/cid-imageid.jpg
										// if exist, skip
										isLoading = true;
										final asyncUpdateCacheTask auct = new asyncUpdateCacheTask();
										auct.execute(ne.getAid());

										// show progress dialog
										pDialog = new ProgressDialog(
												getActivity());
										pDialog.setTitle(getResources()
												.getString(
														R.string.bookshelf_caching));
										pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
										pDialog.setCancelable(true);
										pDialog.setOnCancelListener(new OnCancelListener() {
											@Override
											public void onCancel(
													DialogInterface dialog) {
												// TODO Auto-generated
												// method stub
												auct.cancel(true);
												isLoading = false;
												Toast.makeText(
														getActivity(),
														getResources()
																.getString(
																		R.string.cancelled),
														Toast.LENGTH_SHORT)
														.show();
												pDialog = null;
											}

										});
										pDialog.setMessage(getResources()
												.getString(
														R.string.search_fetching)
												+ ne.getTitle());
										pDialog.setProgress(0);
										pDialog.setMax(1);
										pDialog.show();

										break;

									case 1:
										// force reload all
										// reload all, even if exist
										isLoading = true;
										final asyncForceReloadTask afrt = new asyncForceReloadTask();
										afrt.execute(ne.getAid());

										// show progress dialog
										pDialog = new ProgressDialog(
												getActivity());
										pDialog.setTitle(getResources()
												.getString(
														R.string.bookshelf_caching));
										pDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
										pDialog.setCancelable(true);
										pDialog.setOnCancelListener(new OnCancelListener() {
											@Override
											public void onCancel(
													DialogInterface dialog) {
												// TODO Auto-generated
												// method stub
												isLoading = false;
												Toast.makeText(
														getActivity(),
														getResources()
																.getString(
																		R.string.cancelled),
														Toast.LENGTH_SHORT)
														.show();
												pDialog = null;
											}

										});
										pDialog.setMessage(getResources()
												.getString(
														R.string.search_fetching)
												+ ne.getTitle());
										pDialog.setProgress(0);
										pDialog.setMax(1);
										pDialog.show();

										break;

									case 2:
										// remove from bookshelf
										// need to alert again
										new AlertDialog.Builder(getActivity())
												.setTitle(
														getResources()
																.getString(
																		R.string.bookshelf_remove_confirm_title))
												.setMessage(
														getResources()
																.getString(
																		R.string.bookshelf_remove_confirm))
												.setPositiveButton(
														"YES",
														new android.content.DialogInterface.OnClickListener() {

															@Override
															public void onClick(
																	DialogInterface dialog,
																	int which) {

																// remove
																GlobalConfig
																		.removeFromLocalBookshelf(ne
																				.getAid());

																// related
																// volumes
																// and
																// images
																String volumeXml = GlobalConfig
																		.loadFullFileFromSaveFolder(
																				"intro",
																				ne.getAid()
																						+ "-volume.xml");
																List<XMLParser.VolumeList> vl = XMLParser
																		.getVolumeList(volumeXml);

																if (vl != null) {
																	for (XMLParser.VolumeList tempVl : vl) {
																		for (XMLParser.ChapterInfo tempCi : tempVl.chapterList) {
																			LightCache
																					.deleteFile(
																							GlobalConfig
																									.getFirstFullSaveFilePath(),
																							"novel"
																									+ File.separator
																									+ tempCi.cid
																									+ ".xml");

																			LightCache
																					.deleteFile(
																							GlobalConfig
																									.getSecondFullSaveFilePath(),
																							"novel"
																									+ File.separator
																									+ tempCi.cid
																									+ ".xml");
																		}

																	}
																}

																// delete
																// files
																LightCache
																		.deleteFile(
																				GlobalConfig
																						.getFirstFullSaveFilePath(),
																				"intro"
																						+ File.separator
																						+ ne.getAid()
																						+ "-intro.xml");
																LightCache
																		.deleteFile(
																				GlobalConfig
																						.getFirstFullSaveFilePath(),
																				"intro"
																						+ File.separator
																						+ ne.getAid()
																						+ "-introfull.xml");
																LightCache
																		.deleteFile(
																				GlobalConfig
																						.getFirstFullSaveFilePath(),
																				"intro"
																						+ File.separator
																						+ ne.getAid()
																						+ "-volume.xml");
																LightCache
																		.deleteFile(
																				GlobalConfig
																						.getSecondFullSaveFilePath(),
																				"intro"
																						+ File.separator
																						+ ne.getAid()
																						+ "-intro.xml");
																LightCache
																		.deleteFile(
																				GlobalConfig
																						.getSecondFullSaveFilePath(),
																				"intro"
																						+ File.separator
																						+ ne.getAid()
																						+ "-introfull.xml");
																LightCache
																		.deleteFile(
																				GlobalConfig
																						.getSecondFullSaveFilePath(),
																				"intro"
																						+ File.separator
																						+ ne.getAid()
																						+ "-volume.xml");

																onResume();
															}

														})
												.setNegativeButton("NO", null)
												.show();

										break;
									}
								}
							}).show();

			// Toast.makeText(getActivity(), "Clicked: " + position,
			// Toast.LENGTH_SHORT).show();

			return true; // disable onItemClickListener
		}

	};

	class asyncForceReloadTask extends AsyncTask<Integer, Integer, Integer> {
		// in: Aid
		// out: current loading
		String volumeXml, introXml;
		List<XMLParser.VolumeList> vl = null;
		List<String> imageList = null; // add one and save once
		private XMLParser.NovelFullInfo ni;

		@Override
		protected Integer doInBackground(Integer... params) {
			// get full range online, always
			try {
				// fetch intro
				if (!isLoading)
					return -222; // calcel
				List<NameValuePair> targVarListVolume = new ArrayList<NameValuePair>();
				targVarListVolume.add(Wenku8Interface.getNovelIndex(params[0],
						GlobalConfig.getFetchLanguage()));
				byte[] tempvVolumeXml = LightNetwork.LightHttpPost(
						Wenku8Interface.BaseURL, targVarListVolume);
				if (tempvVolumeXml == null)
					return -100; // network error
				volumeXml = new String(tempvVolumeXml, "UTF-8");

				if (!isLoading)
					return -222; // calcel
				List<NameValuePair> targVarList = new ArrayList<NameValuePair>();
				targVarList.add(Wenku8Interface.getNovelFullMeta(params[0],
						GlobalConfig.getFetchLanguage()));
				byte[] tempIntroXml = LightNetwork.LightHttpPost(
						Wenku8Interface.BaseURL, targVarList);
				if (tempIntroXml == null)
					return -100; // network error
				introXml = new String(tempIntroXml, "UTF-8");

				// parse into structures
				vl = XMLParser.getVolumeList(volumeXml);
				ni = XMLParser.getNovelFullInfo(introXml);
				if (vl == null || ni == null)
					return -101; // parse failed

				if (!isLoading)
					return -222; // calcel
				List<NameValuePair> targIntro = new ArrayList<NameValuePair>();
				targIntro.add(Wenku8Interface.getNovelFullIntro(ni.aid,
						GlobalConfig.getFetchLanguage()));
				byte[] tempFullIntro = LightNetwork.LightHttpPost(
						Wenku8Interface.BaseURL, targIntro);
				if (tempFullIntro == null)
					return -100; // network error
				ni.fullIntro = new String(tempFullIntro, "UTF-8");

				// write into saved file
				GlobalConfig.writeFullFileIntoSaveFolder("intro", params[0]
						+ "-intro.xml", introXml);
				GlobalConfig.writeFullFileIntoSaveFolder("intro", params[0]
						+ "-introfull.xml", ni.fullIntro);
				GlobalConfig.writeFullFileIntoSaveFolder("intro", params[0]
						+ "-volume.xml", volumeXml);

			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

			// calc size
			int size_a = 0;
			for (XMLParser.VolumeList tempVl : vl) {
				size_a += tempVl.chapterList.size();
			}
			pDialog.setMax(size_a);
			pDialog.setProgress(0);

			// cache each cid to save the whole book
			// and will need to download all the images
			int current = 0;
			for (XMLParser.VolumeList tempVl : vl) {
				for (XMLParser.ChapterInfo tempCi : tempVl.chapterList) {
					try {
						if (!isLoading)
							return -222; // calcel
						List<NameValuePair> targVar = new ArrayList<NameValuePair>();
						targVar.add(Wenku8Interface.getNovelContent(ni.aid,
								tempCi.cid, GlobalConfig.getFetchLanguage()));
						byte[] tempXml = LightNetwork.LightHttpPost(
								Wenku8Interface.BaseURL, targVar);
						if (tempXml == null)
							return -100; // network error
						String xml = new String(tempXml, "UTF-8");

						// save file (cid.xml), didn't format it
						// future version may format it for better performance
						GlobalConfig.writeFullFileIntoSaveFolder("novel",
								tempCi.cid + ".xml", xml);

						// cache image
						if (GlobalConfig.doCacheImage()) {
							List<NovelContentParser.NovelContent> nc = NovelContentParser
									.NovelContentParser_onlyImage(xml);
							if (nc == null)
								return -100;

							for (int i = 0; i < nc.size(); i++) {
								if (nc.get(i).type == 'i') {
									pDialog.setMax(++size_a);

									// save this images, judge exist first
									GlobalConfig.saveNovelContentImage(nc
											.get(i).content);

									if (!isLoading)
										return -222; // calcel
									publishProgress(++current); // update
																// progress
								}
							}
						}

						publishProgress(++current); // update progress

					} catch (UnsupportedEncodingException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}

			return 0;
		}

		@Override
		protected void onProgressUpdate(Integer... values)// 执行操作中，发布进度后
		{
			if (pDialog != null)
				pDialog.setProgress(values[0]);
			return;
		}

		protected void onPostExecute(Integer result)// 执行耗时操作之后处理UI线程事件
		{
			if (result == -222) {
				// user cancelled
				Toast.makeText(getActivity(),
						getResources().getString(R.string.user_cancelled),
						Toast.LENGTH_LONG).show();
				if (pDialog != null)
					pDialog.dismiss();
				onResume();
				return;
			} else if (result == -100) {
				Toast.makeText(getActivity(),
						getResources().getString(R.string.network_error),
						Toast.LENGTH_LONG).show();
				if (pDialog != null)
					pDialog.dismiss();
				onResume();
				return;
			} else if (result == -101) {
				Toast.makeText(getActivity(),
						getResources().getString(R.string.parse_failed),
						Toast.LENGTH_LONG).show();
				if (pDialog != null)
					pDialog.dismiss();
				onResume();
				return;
			}

			// cache successfully
			Toast.makeText(getActivity(),
					getResources().getString(R.string.bookshelf_cached),
					Toast.LENGTH_LONG).show();
			if (pDialog != null)
				pDialog.dismiss();
			onResume();
			return;
		}

	}

	class asyncUpdateCacheTask extends AsyncTask<Integer, Integer, Integer> {
		// in: Aid
		// out: current loading
		String volumeXml, introXml;
		List<XMLParser.VolumeList> vl = null;
		List<String> imageList = null; // add one and save once
		private XMLParser.NovelFullInfo ni;
		int size_a = 0, current = 0;

		@Override
		protected Integer doInBackground(Integer... params) {
			for (Integer param : params) {
				// get full range online, always
				try {
					// fetch intro
					if (!isLoading)
						return -222; // calcel
					List<NameValuePair> targVarListVolume = new ArrayList<NameValuePair>();
					targVarListVolume.add(Wenku8Interface.getNovelIndex(param,
							GlobalConfig.getFetchLanguage()));
					byte[] tempvVolumeXml = LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, targVarListVolume);
					if (tempvVolumeXml == null)
						return -100; // network error
					volumeXml = new String(tempvVolumeXml, "UTF-8");

					if (!isLoading)
						return -222; // calcel
					List<NameValuePair> targVarList = new ArrayList<NameValuePair>();
					targVarList.add(Wenku8Interface.getNovelShortInfo(param,
							GlobalConfig.getFetchLanguage()));
					byte[] tempIntroXml = LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, targVarList);
					if (tempIntroXml == null)
						return -100; // network error
					introXml = new String(tempIntroXml, "UTF-8");

					// parse into structures
					vl = XMLParser.getVolumeList(volumeXml);
					ni = XMLParser.getNovelFullInfo(introXml);
					if (vl == null || ni == null)
						return -101; // parse failed

					if (!isLoading)
						return -222; // calcel
					List<NameValuePair> targIntro = new ArrayList<NameValuePair>();
					targIntro.add(Wenku8Interface.getNovelFullIntro(ni.aid,
							GlobalConfig.getFetchLanguage()));
					byte[] tempFullIntro = LightNetwork.LightHttpPost(
							Wenku8Interface.BaseURL, targIntro);
					if (tempFullIntro == null)
						return -100; // network error
					ni.fullIntro = new String(tempFullIntro, "UTF-8");

					// write into saved file
					GlobalConfig.writeFullFileIntoSaveFolder("intro", param
							+ "-intro.xml", introXml);
					GlobalConfig.writeFullFileIntoSaveFolder("intro", param
							+ "-introfull.xml", ni.fullIntro);
					GlobalConfig.writeFullFileIntoSaveFolder("intro", param
							+ "-volume.xml", volumeXml);

				} catch (UnsupportedEncodingException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}

				// calc size
				for (XMLParser.VolumeList tempVl : vl) {
					size_a += tempVl.chapterList.size();
				}
				pDialog.setMax(size_a);
				pDialog.setProgress(0);

				// cache each cid to save the whole book
				// and will need to download all the images
				for (XMLParser.VolumeList tempVl : vl) {
					for (XMLParser.ChapterInfo tempCi : tempVl.chapterList) {
						try {
							List<NameValuePair> targVar = new ArrayList<NameValuePair>();
							targVar.add(Wenku8Interface.getNovelContent(ni.aid,
									tempCi.cid, GlobalConfig.getFetchLanguage()));

							// load from local first
							if (!isLoading)
								return -222; // calcel
							String xml = GlobalConfig
									.loadFullFileFromSaveFolder("novel",
											tempCi.cid + ".xml");
							if (xml == null || xml.length() == 0) {
								byte[] tempXml = LightNetwork.LightHttpPost(
										Wenku8Interface.BaseURL, targVar);
								if (tempXml == null)
									return -100; // network error
								xml = new String(tempXml, "UTF-8");

								// save file (cid.xml), didn't format it
								// future version may format it for better
								// performance
								GlobalConfig.writeFullFileIntoSaveFolder(
										"novel", tempCi.cid + ".xml", xml);
							}

							// cache image
							if (GlobalConfig.doCacheImage()) {
								List<NovelContentParser.NovelContent> nc = NovelContentParser
										.NovelContentParser_onlyImage(xml);
								if (nc == null)
									return -100;

								for (int i = 0; i < nc.size(); i++) {
									if (nc.get(i).type == 'i') {
										pDialog.setMax(++size_a);

										// save this images, judge exist first
										String imgFileName = GlobalConfig
												.generateImageFileNameByURL(nc
														.get(i).content);
										if (!LightCache
												.testFileExist(GlobalConfig
														.getFirstFullSaveFilePath()
														+ GlobalConfig.imgsSaveFolderName
														+ File.separator
														+ imgFileName)
												&& !LightCache
														.testFileExist(GlobalConfig
																.getSecondFullSaveFilePath()
																+ GlobalConfig.imgsSaveFolderName
																+ File.separator
																+ imgFileName)) {
											// neither of the file exist
											byte[] fileContent = LightNetwork
													.LightHttpDownload(nc
															.get(i).content);
											if (fileContent == null)
												return -100; // network error
											if (!LightCache
													.saveFile(
															GlobalConfig
																	.getFirstFullSaveFilePath()
																	+ GlobalConfig.imgsSaveFolderName
																	+ File.separator,
															imgFileName,
															fileContent, true)) // fail
																				// to
																				// first
																				// path
												LightCache
														.saveFile(
																GlobalConfig
																		.getSecondFullSaveFilePath()
																		+ GlobalConfig.imgsSaveFolderName
																		+ File.separator,
																imgFileName,
																fileContent,
																true);
										}

										if (!isLoading)
											return -222;
										publishProgress(++current); // update
																	// progress
									}
								}
							}

							publishProgress(++current); // update progress

						} catch (UnsupportedEncodingException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}

			return 0;
		}

		@Override
		protected void onProgressUpdate(Integer... values)// 执行操作中，发布进度后
		{
			if (pDialog != null)
				pDialog.setProgress(values[0]);
			return;
		}

		protected void onPostExecute(Integer result)// 执行耗时操作之后处理UI线程事件
		{
			if (result == -222) {
				// user cancelled
				Toast.makeText(getActivity(),
						getResources().getString(R.string.user_cancelled),
						Toast.LENGTH_LONG).show();
				if (pDialog != null)
					pDialog.dismiss();
				onResume();
				return;
			} else if (result == -100) {
				Toast.makeText(getActivity(),
						getResources().getString(R.string.network_error),
						Toast.LENGTH_LONG).show();
				if (pDialog != null)
					pDialog.dismiss();
				onResume();
				return;
			} else if (result == -101) {
				Toast.makeText(getActivity(),
						getResources().getString(R.string.parse_failed),
						Toast.LENGTH_LONG).show();
				if (pDialog != null)
					pDialog.dismiss();
				onResume();
				return;
			}

			// cache successfully
			Toast.makeText(getActivity(),
					getResources().getString(R.string.bookshelf_cached),
					Toast.LENGTH_LONG).show();
			if (pDialog != null)
				pDialog.dismiss();
			onResume();
			return;
		}

	}
}
