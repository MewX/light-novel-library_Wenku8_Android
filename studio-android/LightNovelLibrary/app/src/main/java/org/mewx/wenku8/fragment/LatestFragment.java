package org.mewx.wenku8.fragment;

import android.content.ContentValues;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.ActivityOptionsCompat;
import android.support.v4.app.Fragment;
import android.support.v4.util.Pair;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.jpardogo.android.googleprogressbar.library.GoogleProgressBar;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.activity.NovelInfoActivity;
import org.mewx.wenku8.adapter.NovelItemAdapter;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfo;
import org.mewx.wenku8.global.api.NovelItemList;
import org.mewx.wenku8.global.api.NovelListWithInfoParser;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.util.LightNetwork;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class LatestFragment extends Fragment implements MyItemClickListener, MyItemLongClickListener {

    static private final String TAG = "LatestFragment";

    // components
    private MainActivity mainActivity = null;
    private LinearLayoutManager mLayoutManager;
    private RecyclerView mRecyclerView;
    private TextView mTextView;

    // Novel Item info
    private NovelItemList novelItemList;
    private List<NovelItemInfo> listNovelItemInfo;
    private NovelItemAdapter mAdapter;
    private int currentPage, totalPage; // currentP stores next reading page num, TODO: fix wrong number

    // switcher
    private boolean isLoading;
    int pastVisiblesItems, visibleItemCount, totalItemCount;

    public LatestFragment() {
        // Required empty public constructor
    }

    public static LatestFragment newInstance() {
        return new LatestFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // get main activity
        while (mainActivity == null)
            mainActivity = (MainActivity) getActivity();
    }

    public NovelItemAdapter getNovelItemAdapter() {
        return mAdapter;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_latest, container, false);

        // get views
        mRecyclerView = (RecyclerView) rootView.findViewById(R.id.novel_item_list);
        mTextView = (TextView) rootView.findViewById(R.id.list_loading_status);

        mRecyclerView.setHasFixedSize(true);
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.setAdapter(new RecyclerView.Adapter() {
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) { return null; }
            @Override
            public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) { }
            @Override
            public int getItemCount() { return 0; }
        });

        // Listener
        mRecyclerView.addOnScrollListener(new MyOnScrollListener());

        // set click event
        rootView.findViewById(R.id.btn_loading).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLoading) {
                    isLoading = false; // set this false as a terminator signal

                } else {
                    // need to reload novel list all
                    currentPage = 1;
                    totalPage = 1;
                    isLoading = false;
                    loadNovelList(currentPage);
                }

            }
        });

        // Load novel list
//        AsyncGetNovelItem agni = new AsyncGetNovelItem();
//        List<NameValuePair> list = new ArrayList<>();
//        list.add(Wenku8API.getNovelList(Wenku8API.NOVELSORTBY.lastUpdate, 1));
//        agni.execute(list);

        // new load novel list
        // fetch list
        currentPage = 1;
        totalPage = 1;
        isLoading = false;
        loadNovelList(currentPage);

        return rootView;
    }

    private void loadNovelList(int page) {
        // In fact, I don't need to know what it really is.
        // I just need to get the NOVELSORTBY
        isLoading = true; // set loading states
        hideRetryButton();

        // fetch list
        AsyncLoadLastestList ast = new AsyncLoadLastestList();
        ast.execute(Wenku8API.getNovelListWithInfo(Wenku8API.NOVELSORTBY.lastUpdate, page,
                GlobalConfig.getCurrentLang()));
    }

    @Override
    public void onItemClick(View view, final int position) {
        //Toast.makeText(getActivity(),"item click detected", Toast.LENGTH_SHORT).show();
        if(position < 0 || position >= listNovelItemInfo.size()) {
            // ArrayIndexOutOfBoundsException
            Toast.makeText(getActivity(), "ArrayIndexOutOfBoundsException: " + position + " in size " + listNovelItemInfo.size(), Toast.LENGTH_SHORT).show();
            return;
        }

        // go to detail activity
        Intent intent = new Intent(getActivity(), NovelInfoActivity.class);
        intent.putExtra("aid", listNovelItemInfo.get(position).getAid());
        intent.putExtra("from", "latest");
        intent.putExtra("title", listNovelItemInfo.get(position).getTitle());
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
    public void onItemLongClick(View view, int postion) {
        // empty
        onItemClick(view, postion);
    }


    @Override
    public void onDetach() {
        isLoading = false;
        super.onDetach();
    }

    private class MyOnScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            visibleItemCount = mLayoutManager.getChildCount();
            totalItemCount = mLayoutManager.getItemCount();
            pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition();

            if (!isLoading) {
                // 滚动到一半的时候加载，即：剩余3个元素的时候就加载
                if (visibleItemCount + pastVisiblesItems + 3 >= totalItemCount) {
                    isLoading = true;

                    // load more toast
                    Snackbar.make(mRecyclerView, getResources().getString(R.string.list_loading)
                                    + "(" + Integer.toString(currentPage) + "/" + totalPage + ")",
                            Snackbar.LENGTH_SHORT).show();

                    // load more thread
//                    AsyncGetNovelItem agni = new AsyncGetNovelItem();
//                    List<NameValuePair> list = new ArrayList<>();
//                    list.add(Wenku8API.getNovelList(Wenku8API.NOVELSORTBY.lastUpdate, novelItemList.getCurrentPage() + 1));
//                    agni.execute(list);
                    if (currentPage <= totalPage) {
                        loadNovelList(currentPage);
                    } else {
                        Snackbar.make(mRecyclerView, "Every page is loaded!",
                                Snackbar.LENGTH_SHORT).show();
                    }
                }
            }
        }
    }

    class AsyncLoadLastestList extends AsyncTask<ContentValues, Integer, Integer> {
        // fail return -1
        @Override
        protected Integer doInBackground(ContentValues... params) {

            try {
                byte[] tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), params[0]);
                if (tempXml == null)
                    return -100;
                String xml = new String(tempXml, "UTF-8");
                totalPage = NovelListWithInfoParser.getNovelListWithInfoPageNum(xml);
                List<NovelListWithInfoParser.NovelListWithInfo> l = NovelListWithInfoParser.getNovelListWithInfo(xml);
                if (l == null)
                    return -100; // network error

                if (listNovelItemInfo == null)
                    listNovelItemInfo = new ArrayList<>();
                for (int i = 0; i < l.size(); i++) {
                    NovelListWithInfoParser.NovelListWithInfo nlwi = l.get(i);

                    // getImage
                    // List<NameValuePair> imgP = new
                    // ArrayList<NameValuePair>();
                    // imgP.add(Wenku8Interface.getNovelCover(nlwi.aid));
                    // byte[] img = LightNetwork.LightHttpPost(
                    // Wenku8Interface.BaseURL, imgP);

                    NovelItemInfo ni = new NovelItemInfo();
                    ni.setAid(nlwi.aid);
                    ni.setTitle(nlwi.name);
                    ni.setAuthor(nlwi.hit + ""); // hit
                    ni.setUpdate(nlwi.push + ""); // push
                    ni.setIntro_short(nlwi.fav + ""); // fav
                    listNovelItemInfo.add(ni);
                }

                // onProgressUpdate(j);

            } catch (UnsupportedEncodingException e) {
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
                if(!isAdded())
                    return; // detached

                mTextView.setText(getResources().getString(R.string.system_parse_failed));
                showRetryButton();
                isLoading = false;
                return;
            }

            // result:
            // add imageView, only here can fetch the layout2 id!!!
            // hide loading layout
            if (mAdapter == null) {
                mAdapter = new NovelItemAdapter(listNovelItemInfo);
                mAdapter.setOnItemClickListener(LatestFragment.this);
                mAdapter.setOnItemLongClickListener(LatestFragment.this);
                mRecyclerView.setAdapter(mAdapter);
            }
            if (mainActivity.findViewById(R.id.list_loading) != null)
                mainActivity.findViewById(R.id.list_loading).setVisibility(View.GONE);
            mAdapter.notifyDataSetChanged();

            currentPage ++; // add when loaded
            isLoading = false;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd(TAG);
        GlobalConfig.LeaveLatest();
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart(TAG);
        GlobalConfig.EnterLatest();
    }

    private void showRetryButton() {
        if (mainActivity.findViewById(R.id.btn_loading) == null || !isAdded()) return;
        ((TextView) mainActivity.findViewById(R.id.btn_loading)).setText(getResources().getString(R.string.task_retry));
        mainActivity.findViewById(R.id.google_progress).setVisibility(View.GONE);
        mainActivity.findViewById(R.id.btn_loading).setVisibility(View.VISIBLE);
    }

    /**
     * After button pressed, should hide the "retry" button
     */
    private void hideRetryButton() {
        if (mainActivity.findViewById(R.id.btn_loading) == null) return;
        mTextView.setText(getResources().getString(R.string.list_loading));
        mainActivity.findViewById(R.id.google_progress).setVisibility(View.VISIBLE);
        mainActivity.findViewById(R.id.btn_loading).setVisibility(View.GONE);
    }


}
