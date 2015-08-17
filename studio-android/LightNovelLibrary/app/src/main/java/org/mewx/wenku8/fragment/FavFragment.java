package org.mewx.wenku8.fragment;

import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.util.Pair;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.umeng.analytics.MobclickAgent;

import org.apache.http.NameValuePair;
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
import org.mewx.wenku8.util.LightNetwork;
import org.mewx.wenku8.util.LightTool;
import org.mewx.wenku8.util.LightUserSession;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class FavFragment extends Fragment implements MyItemClickListener, MyItemLongClickListener {

    // local vars
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView = null;
    private int timecount;

    // novel list info
    private List<Integer> listNovelItemAid = null; // aid list
    private NovelItemAdapterUpdate mAdapter = null;

    public static FavFragment newInstance(String param1, String param2) {
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
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_fav, container, false);

        // find view
        mSwipeRefreshLayout = (SwipeRefreshLayout) rootView.findViewById(R.id.swipe_refresh_layout);

        // init values
        timecount = 0;

        // view setting
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.novel_item_list);
        mRecyclerView.setHasFixedSize(false); // set variable size
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
                return null;
            }

            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            }

            @Override
            public int getItemCount() {
                return 0;
            }
        }); // an empty one?

        mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.myAccentColor));
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                AsyncLoadAllCloud alac = new AsyncLoadAllCloud();
                alac.execute(1);
            }
        });

        return rootView;
    }

    @Override
    public void onItemClick(View view, int position) {
        // go to detail activity
        Intent intent = new Intent(getActivity(), NovelInfoActivity.class);
        intent.putExtra("aid", listNovelItemAid.get(position));
        intent.putExtra("from", "fav");
        intent.putExtra("title", ((TextView) view.findViewById(R.id.novel_title)).getText());
        GlobalConfig.accessToLocalBookshelf(listNovelItemAid.get(position)); // sort event

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
    public void onItemLongClick(View view, int position) {
        // Toast.makeText(getActivity(),"item long click detected", Toast.LENGTH_SHORT).show();

    }

    private void refreshList(int time) {
//        listNovelItemAid = GlobalConfig.getLocalBookshelfList(); // reget
//        List<NovelItemInfoUpdate> resort = new ArrayList<NovelItemInfoUpdate>();
//        for(Integer aid : listNovelItemAid) {
//            for(int i = 0; i < listNovelItemInfo.size(); i ++) {
//                if(listNovelItemInfo.get(i).aid == aid) {
//                    resort.add(listNovelItemInfo.get(i));
//                    listNovelItemInfo.remove(i);
//                    break;
//                }
//            }
//        }
//        listNovelItemInfo = resort;

        // awful way
//        if(alal == null || alal.getStatus() != AsyncTask.Status.RUNNING) {
//            listNovelItemAid = GlobalConfig.getLocalBookshelfList();
//            listNovelItemInfo = new ArrayList<NovelItemInfoUpdate>();
//            alal = new AsyncLoadAllLocal();
//            alal.execute();
//        }
        if(time == 0) {
            mSwipeRefreshLayout.setRefreshing(true);
            AsyncLoadAllCloud alac = new AsyncLoadAllCloud();
            alac.execute();
        }
        else {
            loadAllLocal();
        }
    }

    private void loadAllLocal() {
        int retValue = 0;

        // init
        listNovelItemAid = GlobalConfig.getLocalBookshelfList();
        List<NovelItemInfoUpdate> listNovelItemInfo = new ArrayList<>(); // novel info list

        // load all meta file
        for(Integer aid : listNovelItemAid) {
            String xml = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-intro.xml");
            NovelItemInfoUpdate niiu;

            if (xml.equals("")) {
                // the intro file was deleted
                retValue = -2;
                niiu = new NovelItemInfoUpdate(aid);
            }
            else {
                niiu = NovelItemInfoUpdate.parse(xml);
            }

            if(niiu == null) {
                retValue = -1;
                continue;
            }
            listNovelItemInfo.add(niiu);
        }

        // result
        if(retValue != 0) {
            Toast.makeText(getActivity(), "Error: Some intro load failed, please redownload.", Toast.LENGTH_SHORT).show();
            return;
        }

        if(mAdapter == null) {
            mAdapter = new NovelItemAdapterUpdate();
            mRecyclerView.setAdapter(mAdapter);
        }
        mAdapter.RefreshDataset(listNovelItemInfo);
        mAdapter.setOnItemClickListener(FavFragment.this);
        mAdapter.setOnItemLongClickListener(FavFragment.this);
        mAdapter.notifyDataSetChanged();
        mSwipeRefreshLayout.setRefreshing(false);
    }

    private class AsyncLoadAllCloud extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
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
                    .cancelListener(new DialogInterface.OnCancelListener() {
                        @Override
                        public void onCancel(DialogInterface dialog) {
                            isLoading = false;
                            md.dismiss();
                        }
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
            byte[] b = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), Wenku8API.getBookshelfListAid(GlobalConfig.getCurrentLang()));
            if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;

            if(LightTool.isInteger(new String(b))) {
                if(Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(new String(b))) == Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN) {
                    // do log in
                    Wenku8Error.ErrorCode temp = LightUserSession.doLoginFromFile();
                    if(temp != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) return temp; // return an error code

                    // rquest again
                    b = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), Wenku8API.getBookshelfListAid(GlobalConfig.getCurrentLang()));
                    if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                }
            }

            // purify returned data
            List<Integer> listResultList = new ArrayList<>(); // result list
            try {
                Log.e("MewX", new String(b, "UTF-8"));

                Pattern p = Pattern.compile("aid=\"(.*)\""); // match content between "aid=\"" and "\""
                Matcher m = p.matcher(new String(b, "UTF-8"));
                while (m.find())
                    listResultList.add(Integer.valueOf(m.group(1)));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                GlobalConfig.wantDebugLog("MewX", e.toString());
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
                    List<NameValuePair> targVarListVolume = new ArrayList<>();
                    targVarListVolume.add(Wenku8API.getNovelIndex(aid, GlobalConfig.getCurrentLang()));
                    byte[] tempVolumeXml = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), targVarListVolume);
                    if(!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
                    if(tempVolumeXml == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                    volumeXml = new String(tempVolumeXml, "UTF-8");

                    // fetch intro
                    if(!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
//                    List<NameValuePair> targVarList = new ArrayList<>();
//                    targVarList.add(Wenku8API.getNovelFullMeta(aid, GlobalConfig.getCurrentLang()));
//                    byte[] tempIntroXml = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), targVarList);
                    // use short intro
                    byte[] tempIntroXml = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(),
                            Wenku8API.getNovelShortInfoUpdate_CV(aid, GlobalConfig.getCurrentLang()));
                    if (tempIntroXml == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                    introXml = new String(tempIntroXml, "UTF-8");

                    // parse into structures
                    vl = Wenku8Parser.getVolumeList(volumeXml);
                    ni = Wenku8Parser.parsetNovelFullMeta(introXml);
                    if (vl == null || ni == null) return Wenku8Error.ErrorCode.XML_PARSE_FAILED;

                    if(!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
                    List<NameValuePair> targIntro = new ArrayList<>();
                    targIntro.add(Wenku8API.getNovelFullIntro(ni.aid, GlobalConfig.getCurrentLang()));
                    byte[] tempFullIntro = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), targIntro);
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
            List<Integer> copy = new ArrayList<>();
            copy.addAll(localOnly); // make a copy
            for(Integer aid : copy) {
                b = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), Wenku8API.getAddToBookshelfParams(aid));
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

    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd("FavFragment");
        GlobalConfig.LeaveBookshelf();
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart("FavFragment");
        GlobalConfig.EnterBookshelf();

        // refresh list
        refreshList(timecount ++);
//        if(mAdapter != null) {
//            mAdapter.notifyDataSetChanged();
//            mRecyclerView.setAdapter(mAdapter);
//        }
    }

}
