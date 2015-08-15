package org.mewx.wenku8.fragment;

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
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.Toast;

import com.umeng.analytics.MobclickAgent;

import org.apache.http.NameValuePair;
import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.activity.NovelInfoActivity;
import org.mewx.wenku8.adapter.NovelItemAdapter;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfo;
import org.mewx.wenku8.global.api.NovelItemList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.util.LightNetwork;

import java.util.ArrayList;
import java.util.List;

public class LatestFragment extends Fragment implements MyItemClickListener, MyItemLongClickListener {

    static private final String TAG = "LatestFragment";

    // components
    private MainActivity mainActivity = null;
    //private RecyclerView.LayoutManager mLayoutManager;
    private LinearLayoutManager mLayoutManager;
    private RecyclerView mRecyclerView;
    private TextView mTextView;

    // Novel Item info
    private NovelItemList novelItemList;
    private List<Integer> listNovelItem;
    private List<NovelItemInfo> listNovelItemInfo;
    private NovelItemAdapter mAdapter;

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
                    AsyncGetNovelItem agni = new AsyncGetNovelItem();
                    List<NameValuePair> list = new ArrayList<>();
                    list.add(Wenku8API.getNovelList(Wenku8API.NOVELSORTBY.lastUpdate, 1));
                    agni.execute(list);
                }

            }
        });

        // Load novel list
        AsyncGetNovelItem agni = new AsyncGetNovelItem();
        List<NameValuePair> list = new ArrayList<>();
        list.add(Wenku8API.getNovelList(Wenku8API.NOVELSORTBY.lastUpdate, 1));
        agni.execute(list);

        return rootView;
    }

    @Override
    public void onItemClick(View view, final int position) {
        //Toast.makeText(getActivity(),"item click detected", Toast.LENGTH_SHORT).show();
        if(position < 0 || position >= listNovelItem.size()) {
            // ArrayIndexOutOfBoundsException
            Toast.makeText(getActivity(), "ArrayIndexOutOfBoundsException: " + position + " in size " + listNovelItem.size(), Toast.LENGTH_SHORT).show();
            return;
        }

        // go to detail activity
        Intent intent = new Intent(getActivity(), NovelInfoActivity.class);
        intent.putExtra("aid", listNovelItem.get(position));
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

    private class AsyncGetNovelItem extends AsyncTask<List<NameValuePair>, Integer, Wenku8Error.ErrorCode> {
        private byte[] b;

        @Override
        protected Wenku8Error.ErrorCode doInBackground(List<NameValuePair>... params) {
            b = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), params[0]);
            if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);
            if(errorCode == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                String[] s = new String[1];
                try {
                    // convert byte[] to String
                    s[0] = new String(b, "UTF-8");
                    if (GlobalConfig.inDebugMode())
                        Log.i("MewX", "in AjaxGetNovelItemCallBack.");

                    // add elements to RecylerView
                    if (novelItemList == null)
                        novelItemList = new NovelItemList(s, 1);
                    else
                        novelItemList.setNovelItemList(s, novelItemList.getCurrentPage() + 1);
                    if (!novelItemList.getParseStatus())
                        throw new Exception("MewX Exception: novelItemList failed to parse.");
                    listNovelItem = novelItemList.getNovelItemList();
                    if (listNovelItemInfo == null)
                        listNovelItemInfo = new ArrayList<>();

                    // asc task
                    Integer[] li = listNovelItem.subList((listNovelItem.size() / 10) * 10 - 10, listNovelItem.size())
                            .toArray(new Integer[listNovelItem.size() - ((listNovelItem.size() / 10) * 10 - 10)]);
                    AsyncGetNovelItemList asc = new AsyncGetNovelItemList();
                    hideRetryButton();
                    isLoading = true;
                    asc.execute(li);

                    // release memory
                    s[0] = null;
                } catch (Exception e) {
                    e.printStackTrace();
                    if (isAdded() && mTextView != null)
                        mTextView.setText(getResources().getString(R.string.system_parse_failed) + e.getMessage());
                    showRetryButton();
                    isLoading = false;
                }
            }
            else if(errorCode == Wenku8Error.ErrorCode.NETWORK_ERROR) {
                if(isAdded()) {
                    mTextView.setText(getResources().getString(R.string.system_network_error));
                    showRetryButton();
                }
                isLoading = false;
            }
        }
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
                    if (GlobalConfig.inDebugMode())
                        Log.i("MewX", "Loading more...");

                    // load more toast
                    Snackbar.make(mRecyclerView, getResources().getString(R.string.list_loading)
                                    + "(" + Integer.toString(novelItemList.getCurrentPage() + 1) + "/" + novelItemList.getTotalPage() + ")",
                            Snackbar.LENGTH_SHORT).show();

                    // load more thread
                    AsyncGetNovelItem agni = new AsyncGetNovelItem();
                    List<NameValuePair> list = new ArrayList<>();
                    list.add(Wenku8API.getNovelList(Wenku8API.NOVELSORTBY.lastUpdate, novelItemList.getCurrentPage() + 1));
                    agni.execute(list);
                }
            }
        }
    }

    private class AsyncGetNovelItemList extends AsyncTask<Integer, Integer, Integer> {
        private int totalNumber;
        private int baseNumber;

        @Override
        protected Integer doInBackground(Integer... params) {

            // init
            listNovelItem = novelItemList.getNovelItemList();
            //totalNumber = listNovelItem.size();
            totalNumber = params.length;
            baseNumber = (listNovelItem.size() / 10) * 10 - 10;

            if (GlobalConfig.inDebugMode())
                Log.i("MewX", "totalNumber: " + Integer.toString(totalNumber)
                        + "baseNumber: " + Integer.toString(baseNumber));

            try {
                //for (int i = listNovelItemInfo.size(); i < listNovelItem.size(); i++) {
                for (int i = 0; i < totalNumber; i++) {
                    if (!isLoading)
                        return -1;
                    if (GlobalConfig.inDebugMode())
                        Log.i("MewX", "Loading: " + Integer.toString(i + 1) + " / " + Integer.toString(totalNumber));

                    listNovelItemInfo.add(new NovelItemInfo(params[i]));

                    // generate list name value pair
                    List<NameValuePair> list = new ArrayList<>();
                    list.add(Wenku8API.getNovelShortInfo(listNovelItemInfo.get(baseNumber + i).getAid(), GlobalConfig.getCurrentLang()));

                    byte[] bytes = null;
                    while (bytes == null) // SocketTimeoutException, ConnectTimeoutException
                        bytes = LightNetwork.LightHttpPost(Wenku8API.getBaseURL(), list);
                    String[] sin = new String[1];
                    sin[0] = new String(bytes, "UTF-8");
                    listNovelItemInfo.get(baseNumber + i).setNovelItemInfo(sin);

                    // release memory
                    sin[0] = null;

                    // update progress
                    publishProgress(i);
                }

                return 0;
            } catch (Exception e) {
                e.printStackTrace();
            }

            return -1;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            mTextView.setText("Loading ... " + "(" + Integer.toString(values[0]) + "/" + Integer.toString(totalNumber) + ")");
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);

            // This means manually cancelled this task
            if (result == -1) {
                if(!isAdded())
                    return; // detached

                doReverseOperation();
                mTextView.setText(getResources().getString(R.string.system_parse_failed));
                showRetryButton();
                isLoading = false;
                return;
            }

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

            hideRetryButton();
            isLoading = false;
        }


    }

    private void doReverseOperation() {
        // reverse operation
        novelItemList.requestForReverse();

        listNovelItem = novelItemList.getNovelItemList();
        for (int i = listNovelItem.size(); i < listNovelItemInfo.size(); i++)
            listNovelItemInfo.remove(i);
    }

    private void showRetryButton() {
        if (mainActivity.findViewById(R.id.btn_loading) == null || !isAdded()) return;
        ((TextView) mainActivity.findViewById(R.id.btn_loading)).setText(getResources().getString(R.string.task_retry));
        mainActivity.findViewById(R.id.btn_loading).setVisibility(TextView.VISIBLE);
    }

    @Override
    public void onPause() {
        super.onPause();
        MobclickAgent.onPageEnd(TAG);
    }

    @Override
    public void onResume() {
        super.onResume();
        MobclickAgent.onPageStart(TAG);
    }

    /**
     * After button pressed, should hide the "retry" button
     */
    private void hideRetryButton() {
        if (mainActivity.findViewById(R.id.btn_loading) == null) return;

        mainActivity.findViewById(R.id.btn_loading).setVisibility(TextView.GONE);
    }


}
