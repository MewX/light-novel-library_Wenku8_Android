package org.mewx.wenku8.fragment;

import android.content.ContentValues;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.NovelInfoActivity;
import org.mewx.wenku8.adapter.NovelItemAdapterUpdate;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.NovelItemMeta;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.listener.MyOptionClickListener;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.util.LightNetwork;
import org.mewx.wenku8.util.LightTool;
import org.mewx.wenku8.util.LightUserSession;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FavFragment extends Fragment implements MyItemClickListener, MyItemLongClickListener, MyOptionClickListener {

    // local vars
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView = null;
    private int timecount;

    // novel list info
    private final List<Integer> listNovelItemAid = new ArrayList<>(); // aid list
    private final List<NovelItemInfoUpdate> listNovelItemInfo = new ArrayList<>(); // info list

    public static FavFragment newInstance() {
        return new FavFragment();
    }

    public FavFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_fav, container, false);

        // find view
        mSwipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout);

        // init values
        timecount = 0;

        // view setting
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView = rootView.findViewById(R.id.novel_item_list);
        mRecyclerView.setHasFixedSize(false); // set variable size
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setLayoutManager(mLayoutManager);

        mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.myAccentColor));
        mSwipeRefreshLayout.setOnRefreshListener(() -> new AsyncLoadAllFromCloud().execute(1));

        return rootView;
    }

    @Override
    public void onItemClick(View view, int position) {
        // go to detail activity
        Intent intent = new Intent(getActivity(), NovelInfoActivity.class);
        intent.putExtra("aid", listNovelItemAid.get(position));
        intent.putExtra("from", "fav");
        intent.putExtra("title", ((TextView) view.findViewById(R.id.novel_title)).getText());
        GlobalConfig.moveBookToTheTopOfBookshelf(listNovelItemAid.get(position)); // sort event

        if(Build.VERSION.SDK_INT < 21) {
            startActivity(intent);
        }
        else {
            ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                    Pair.create(view.findViewById(R.id.novel_cover), "novel_cover"),
                    Pair.create(view.findViewById(R.id.novel_title), "novel_title"));
            ActivityCompat.startActivity(getActivity(), intent, options.toBundle());
        }
    }

    @Override
    public void onOptionButtonClick(View view, final int position) {
        new MaterialDialog.Builder(getActivity())
                .theme(Theme.LIGHT)
                .title(R.string.dialog_title_choose_delete_option)
                .backgroundColorRes(R.color.dlgBackgroundColor)
                .titleColorRes(R.color.dlgTitleColor)
                .negativeText(R.string.dialog_negative_pass)
                .negativeColorRes(R.color.dlgNegativeButtonColor)
                .itemsGravity(GravityEnum.CENTER)
                .items(R.array.cleanup_option)
                .itemsCallback(new MaterialDialog.ListCallback() {
                    @Override
                    public void onSelection(MaterialDialog dialog, View view, int which, CharSequence text) {
                        /*
                         * 0 <string name="dialog_clear_cache">清除缓存</string>
                         * 1 <string name="dialog_delete_book">删除这本书</string>
                         */
                        switch (which) {
                            case 0:
                                new MaterialDialog.Builder(getActivity())
                                        .callback(new MaterialDialog.ButtonCallback() {
                                            @Override
                                            public void onPositive(MaterialDialog dialog) {
                                                super.onPositive(dialog);
                                                int aid = listNovelItemAid.get(position);
                                                String novelFullVolume = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-volume.xml");
                                                if(novelFullVolume.isEmpty()) return;
                                                List<VolumeList> listVolume = Wenku8Parser.getVolumeList(novelFullVolume);
                                                if(listVolume.isEmpty()) return;
                                                cleanVolumesCache(listVolume);
                                            }
                                        })
                                        .theme(Theme.LIGHT)
                                        .content(R.string.dialog_sure_to_clear_cache)
                                        .contentGravity(GravityEnum.CENTER)
                                        .positiveText(R.string.dialog_positive_sure)
                                        .negativeText(R.string.dialog_negative_preferno)
                                        .show();
                                break;
                            case 1:
                                new MaterialDialog.Builder(getActivity())
                                        .callback(new MaterialDialog.ButtonCallback() {
                                            @Override
                                            public void onPositive(MaterialDialog dialog) {
                                                super.onPositive(dialog);
                                                // Delete operation: delete from in-memory index and cloud first.
                                                // Then, the async task will remove the deleted book from local bookshelf.
                                                int aid = listNovelItemAid.get(position);
                                                listNovelItemAid.remove(position);
                                                new AsyncRemoveBookFromCloud().execute(aid);
                                                refreshList(timecount ++);
                                            }
                                        })
                                        .theme(Theme.LIGHT)
                                        .content(R.string.dialog_content_want_to_delete)
                                        .contentGravity(GravityEnum.CENTER)
                                        .positiveText(R.string.dialog_positive_sure)
                                        .negativeText(R.string.dialog_negative_preferno)
                                        .show();
                                break;
                        }
                    }
                })
                .show();
    }

    @Override
    public void onItemLongClick(View view, int position) {
    }

    private void cleanVolumesCache(List<VolumeList> listVolume) {
        // remove from local bookshelf, already in bookshelf
        for (VolumeList vl : listVolume) {
            vl.cleanLocalCache();
        }
    }

    private void refreshList(int time) {
        if(time == 0) {
            mSwipeRefreshLayout.setRefreshing(true);
            new AsyncLoadAllFromCloud().execute();
        }
        else {
            loadAllLocal();
        }
    }

    private void loadAllLocal() {
        int retValue = 0;
        boolean datasetChanged = false;

        // init
        listNovelItemAid.clear();
        listNovelItemAid.addAll(GlobalConfig.getLocalBookshelfList());

        // load all metadata file
        aids:
        for (int j = 0; j < listNovelItemAid.size(); j++) {
            int aid = listNovelItemAid.get(j);
            // See if it's in the list already. Expecting the list will not be more than 100.
            for (int i = j; i < listNovelItemInfo.size(); i++) {
                final NovelItemInfoUpdate info = listNovelItemInfo.get(i);
                if (info.aid == aid) {
                    // Found but in the same place.
                    if (i == j) continue aids;

                    // Found, not in the same place remove and re-insert.
                    listNovelItemInfo.remove(i);
                    listNovelItemInfo.add(j, info);
                    datasetChanged = true;
                    continue aids;
                }
            }

            // Not found.
            final String xml = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-intro.xml");
            NovelItemInfoUpdate info;
            if (xml.isEmpty()) {
                // the intro file was deleted
                retValue = -1;
                info = new NovelItemInfoUpdate(aid);
            }
            else {
                info = NovelItemInfoUpdate.convertFromMeta(Objects.requireNonNull(Wenku8Parser.parseNovelFullMeta(xml)));
            }
            datasetChanged = true;
            listNovelItemInfo.add(j, info);
        }
        // Trim everything after aid.size().
        if (listNovelItemInfo.size() > listNovelItemAid.size()) {
            listNovelItemInfo.subList(listNovelItemAid.size(), listNovelItemInfo.size()).clear();
        }

        // result
        if(retValue != 0) {
            Toast.makeText(getActivity(), getResources().getString(R.string.bookshelf_intro_load_failed), Toast.LENGTH_SHORT).show();
        }

        // Reuse the adapter and datasets.
        if (mRecyclerView.getAdapter() == null) {
            NovelItemAdapterUpdate adapter = new NovelItemAdapterUpdate();
            adapter.refreshDataset(listNovelItemInfo);
            adapter.setOnItemClickListener(FavFragment.this);
            adapter.setOnDeleteClickListener(FavFragment.this);
            adapter.setOnItemLongClickListener(FavFragment.this);
            mRecyclerView.setAdapter(adapter);
        }
        if (datasetChanged) {
            mRecyclerView.getAdapter().notifyDataSetChanged();
        }
        mSwipeRefreshLayout.setRefreshing(false);
    }

    private class AsyncLoadAllFromCloud extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        private MaterialDialog md;
        private boolean isLoading; // check in "doInBackground" to make sure to continue or not
        private boolean forceLoad = false;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            loadAllLocal();

            isLoading = true;
            md = new MaterialDialog.Builder(getActivity())
                    .theme(Theme.LIGHT)
                    .content(R.string.dialog_content_sync)
                    .progress(false, 1, true)
                    .cancelable(true)
                    .cancelListener(dialog -> {
                        isLoading = false;
                        md.dismiss();
                    })
                    .show();
            md.setProgress(0);
            md.setMaxProgress(0);
            md.show();
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            // if params.length != 0, force async
            if(params != null && params.length != 0) forceLoad = true;

            // ! any network problem will interrupt this procedure
            // load bookshelf list, don't save
            byte[] b = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getBookshelfListAid(GlobalConfig.getCurrentLang()));
            if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;

            if(LightTool.isInteger(new String(b))) {
                if(Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(new String(b))) == Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN) {
                    // do log in
                    Wenku8Error.ErrorCode temp = LightUserSession.doLoginFromFile();
                    if(temp != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) return temp; // return an error code

                    // request again
                    b = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getBookshelfListAid(GlobalConfig.getCurrentLang()));
                    if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                }
            }

            // purify returned data
            List<Integer> listResultList = new ArrayList<>(); // result list
            try {
                Log.d("MewX", new String(b, "UTF-8"));

                Pattern p = Pattern.compile("aid=\"(.*)\""); // match content between "aid=\"" and "\""
                Matcher m = p.matcher(new String(b, "UTF-8"));
                while (m.find()) {
                    try {
                        listResultList.add(Integer.valueOf(m.group(1)));
                    } catch (NumberFormatException e) {
                        Log.e(FavFragment.class.getSimpleName(), "Found and skipped broken aid.");
                    }
                }
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            // calc difference
            List<Integer> listAll = new ArrayList<>();
            listAll.addAll(GlobalConfig.getLocalBookshelfList()); // make a copy
            listAll.addAll(listResultList);

            List<Integer> localOnly = new ArrayList<>();
            localOnly.addAll(listAll);
            localOnly.removeAll(listResultList); // local only

            List<Integer> listDiff = new ArrayList<>();
            listDiff.addAll(listAll);
            if(!forceLoad) {
                // cloud only
                listDiff.removeAll(GlobalConfig.getLocalBookshelfList());
            }
            else {
                // local and cloud together
                HashSet<Integer> hs = new HashSet<>(listDiff);
                listDiff.clear();
                listDiff.addAll(hs);
            }
            if(listDiff.size() == 0 && localOnly.size() == 0) {
                // equal, so exit
                return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
            }

            // load all cloud only book
            int count = 0;
            md.setMaxProgress(listDiff.size());
            for(Integer aid : listDiff) {
                if(!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;

                // download general file
                String volumeXml, introXml;
                List<VolumeList> vl;
                NovelItemMeta ni;
                try {
                    // fetch volumes
                    ContentValues cv = Wenku8API.getNovelIndex(aid, GlobalConfig.getCurrentLang());
                    byte[] tempVolumeXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                    if(!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
                    if(tempVolumeXml == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                    volumeXml = new String(tempVolumeXml, "UTF-8");

                    // fetch intro
                    if(!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;

                    // use short intro
                    byte[] tempIntroXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL,
                            Wenku8API.getNovelFullMeta(aid, GlobalConfig.getCurrentLang()));
                    if (tempIntroXml == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                    introXml = new String(tempIntroXml, "UTF-8");

                    // parse into structures
                    vl = Wenku8Parser.getVolumeList(volumeXml);
                    ni = Wenku8Parser.parseNovelFullMeta(introXml);
                    if (vl.isEmpty() || ni == null) return Wenku8Error.ErrorCode.XML_PARSE_FAILED;

                    if(!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
                    cv = Wenku8API.getNovelFullIntro(ni.aid, GlobalConfig.getCurrentLang());
                    byte[] tempFullIntro = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
                    if (tempFullIntro == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                    ni.fullIntro = new String(tempFullIntro, "UTF-8");

                    // write into saved file, save from volum -> meta -> add2bookshelf
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", aid + "-volume.xml", volumeXml);
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", aid + "-introfull.xml", ni.fullIntro);
                    GlobalConfig.writeFullFileIntoSaveFolder("intro", aid + "-intro.xml", introXml);

                } catch (Exception e) {
                    e.printStackTrace();
                }

                // last, add to local
                GlobalConfig.addToLocalBookshelf(aid);
                publishProgress(++ count);
            }

            // sync local bookshelf, and set ribbon, sync one, delete one
            List<Integer> copy = new ArrayList<>(localOnly); // make a copy
            for(Integer aid : copy) {
                b = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getAddToBookshelfParams(aid));
                if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;

                try {
                    if(LightTool.isInteger(new String(b, "UTF-8"))) {
                        Wenku8Error.ErrorCode result = Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(new String(b, "UTF-8")));
                        if(result == Wenku8Error.ErrorCode.SYSTEM_6_BOOKSHELF_FULL) {
                            return result;
                        }
                        else if(result == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED || result == Wenku8Error.ErrorCode.SYSTEM_5_ALREADY_IN_BOOKSHELF) {
                            localOnly.remove(aid); // remove Obj
                        }
                    }
                } catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
            }

            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            md.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);

            isLoading = false;
            md.dismiss();
            if(errorCode != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                Toast.makeText(MyApp.getContext(), errorCode.toString(), Toast.LENGTH_SHORT).show();
                refreshList(timecount ++);
            }
            else {
                loadAllLocal();
            }
        }
    }

    class AsyncRemoveBookFromCloud extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        MaterialDialog md;
        int aid;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            md = new MaterialDialog.Builder(getActivity())
                    .theme(Theme.LIGHT)
                    .content(R.string.dialog_content_novel_remove_from_cloud)
                    .contentColorRes(R.color.dlgContentColor)
                    .progress(true, 0)
                    .show();
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            // params: aid
            aid = params[0];
            byte[] bytes = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getDelFromBookshelfParams(aid));
            if(bytes == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;

            String result;
            try {
                result = new String(bytes, "UTF-8");
                Log.d("MewX", result);
                if (!LightTool.isInteger(result))
                    return Wenku8Error.ErrorCode.RETURNED_VALUE_EXCEPTION;
                if(Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result)) != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED
                        && Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result)) != Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN
                        && Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result)) != Wenku8Error.ErrorCode.SYSTEM_7_NOVEL_NOT_IN_BOOKSHELF) {
                    return Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result));
                }
                else {
                    // load volume first
                    // get novel chapter list
                    List<VolumeList> listVolume;
                    String novelFullVolume;
                    novelFullVolume = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-volume.xml");
                    if(novelFullVolume.isEmpty()) return Wenku8Error.ErrorCode.ERROR_DEFAULT;
                    listVolume = Wenku8Parser.getVolumeList(novelFullVolume);
                    if(listVolume.isEmpty()) return Wenku8Error.ErrorCode.XML_PARSE_FAILED;

                    cleanVolumesCache(listVolume);
                    // delete files
                    LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "intro" + File.separator + aid + "-intro.xml");
                    LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "intro" + File.separator + aid + "-introfull.xml");
                    LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "intro" + File.separator + aid + "-volume.xml");
                    LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "intro" + File.separator + aid + "-intro.xml");
                    LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "intro" + File.separator + aid + "-introfull.xml");
                    LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "intro" + File.separator + aid + "-volume.xml");
                    // remove from bookshelf
                    GlobalConfig.removeFromLocalBookshelf(aid);
                    if (!GlobalConfig.testInLocalBookshelf(aid)) { // not in
                        return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
                    } else {
                        return Wenku8Error.ErrorCode.LOCAL_BOOK_REMOVE_FAILED;
                    }
                }
            } catch (UnsupportedEncodingException e) {
                return Wenku8Error.ErrorCode.BYTE_TO_STRING_EXCEPTION;
            }
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode err) {
            super.onPostExecute(err);

            md.dismiss();
            if (err == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                Toast.makeText(getActivity(), getResources().getString(R.string.bookshelf_removed), Toast.LENGTH_SHORT).show();
                loadAllLocal();
            } else {
                Toast.makeText(getActivity(), err.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        GlobalConfig.LeaveBookshelf();
    }

    @Override
    public void onResume() {
        super.onResume();
        GlobalConfig.EnterBookshelf();

        // refresh list
        refreshList(timecount ++);
    }

}
