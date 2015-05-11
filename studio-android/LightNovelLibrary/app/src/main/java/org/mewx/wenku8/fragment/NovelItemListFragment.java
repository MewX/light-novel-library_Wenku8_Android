package org.mewx.wenku8.fragment;

import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.apache.http.NameValuePair;
import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.MainActivity;
import org.mewx.wenku8.adapter.NovelItemAdapterUpdate;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.util.LightNetwork;
import org.mewx.wenku8.util.Logger;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

public class NovelItemListFragment extends Fragment {

    // type def
    private final String searchType = "search";
    private MainActivity mainActivity = null;
    private String type, key;
    private boolean isLoading = false; // judge network thread continue
    private int pastVisiblesItems, visibleItemCount, totalItemCount;

    // members
    LinearLayoutManager mLayoutManager = null;
    RecyclerView mRecyclerView = null;

    // novel list info
    private List<Integer> listNovelItemAid = null; // aid list
    private List<NovelItemInfoUpdate> listNovelItemInfo = null; // novel info list
    private NovelItemAdapterUpdate mAdapter = null;

    // page info
    private int currentPage = 1; // default 1
    private int totalPage = 0; // default 0

    /**
     * Each position stands an specific list type.
     * Each type represent a specific
     * @param args
     * @return
     */
    public static NovelItemListFragment newInstance(Bundle args) {
        NovelItemListFragment fragment = new NovelItemListFragment();
        fragment.setArguments(args);

        return fragment;
    }

    /**
     * On create
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        type = getArguments().getString("type");

        // judge if is 'search'
        key = type.equals(searchType) ? getArguments().getString("key") : "";

        // get main activity
        if(getActivity() instanceof MainActivity)
            mainActivity = (MainActivity) getActivity();

        return;
    }

    /**
     * On create view
     * @param inflater
     * @param container
     * @param savedInstanceState
     * @return
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_novel_item_list,container,false);
        rootView.setTag(type); // set TAG

        // init values
        listNovelItemAid = null;
        listNovelItemInfo = null;
        currentPage = 1; // default 1
        totalPage = 0; // default 0

        mLayoutManager = null;
        mLayoutManager = new LinearLayoutManager(getActivity());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);

        mRecyclerView = null;
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
        });

        // List request
        if(type.equals(searchType)) {

            Toast.makeText(getActivity(),"search",Toast.LENGTH_SHORT).show();
        }
        else {
            // Listener
            mRecyclerView.addOnScrollListener(new MyOnScrollListener());
            AsyncGetNovelItemList asyncGetNovelItemList = new AsyncGetNovelItemList();
            asyncGetNovelItemList.execute(currentPage);
        }

        return rootView;
    }

    /**
     * on Activity Created
     * @param savedInstanceState
     */
    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        return;
    }


    private void refreshIdList() {
        if(listNovelItemAid==null)
            listNovelItemAid = new ArrayList<Integer>();

        // set empty list with id only
        if(listNovelItemInfo == null)
            listNovelItemInfo = new ArrayList<NovelItemInfoUpdate>();
        else
            listNovelItemInfo.clear();

        // set empty
        for(Integer temp : listNovelItemAid) {
            listNovelItemInfo.add(new NovelItemInfoUpdate(temp));
        }

        //if(mAdapter == null) {
        if(mAdapter == null)
            mAdapter = new NovelItemAdapterUpdate();
        mAdapter.RefreshDataset(listNovelItemInfo);
            //mAdapter = new NovelItemAdapterUpdate(listNovelItemInfo);

        if(currentPage == 1 && mRecyclerView != null) {
            mRecyclerView.setAdapter(mAdapter);
        }
        else
            mAdapter.notifyDataSetChanged();


        return;
    }

    private void appendToIdList(List<Integer> l) {
        if(listNovelItemAid==null)
            listNovelItemAid = new ArrayList<>();
        listNovelItemAid.addAll(l);

        return;
    }

    private class MyOnScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            visibleItemCount = mLayoutManager.getChildCount();
            totalItemCount = mLayoutManager.getItemCount();
            pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition();

            if (!isLoading) {
                // 滚动到一半的时候加载，即：剩余5个元素的时候就加载
                if (visibleItemCount + pastVisiblesItems + 5 >= totalItemCount && (totalPage==0 || currentPage < totalPage)) {
                    GlobalConfig.wantDebugLog("MewX", "Loading more...");

                    // load more toast
                    Toast.makeText(MyApp.getContext(),
                            getResources().getString(R.string.list_loading) + "(" + Integer.toString(currentPage + 1) + "/" + Integer.toString(totalPage) + ")",
                            Toast.LENGTH_SHORT).show();

                    // load more thread
                    AsyncGetNovelItemList asynctask = new AsyncGetNovelItemList();
                    asynctask.execute(currentPage + 1);
                }
            }
        }
    }

    private class AsyncGetNovelItemList extends AsyncTask<Integer, Integer, Integer> {
        private List<Integer> tempNovelList = null;

        @Override
        protected Integer doInBackground(Integer... params) {
            if(isLoading)
                return -1;

            isLoading = true;
            currentPage = params[0];

            // params[0] is current page number
            if (GlobalConfig.inDebugMode())
                Log.v("MewX", "background starts");
            List<NameValuePair> l = new ArrayList<NameValuePair>();
            l.add(Wenku8API.getNovelList(Wenku8API.getNOVELSORTBY(type), currentPage));

            byte[] temp = LightNetwork.LightHttpPost( Wenku8API.getBaseURL(), l );
            if(temp == null) return -1;
            try {
                tempNovelList = Wenku8Parser.parseNovelItemList(new String(temp, "UTF-8"), currentPage);
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
                GlobalConfig.wantDebugLog("MewX", e.toString());
            }

            // judge result
            if( tempNovelList == null || tempNovelList.size() == 0 ) {
                String error = "in AsyncGetNovelItemList: doInBackground: tempNovelList == null || tempNovelList.size() == 0";
                GlobalConfig.wantDebugLog("MewX", error);
                Logger.writeLogger(error);
            }

            totalPage = tempNovelList.get(0);
            tempNovelList.remove(0);


            if (GlobalConfig.inDebugMode())
                Log.v("MewX", "background ends");

            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            if(integer == -1) {
                // network error
                GlobalConfig.wantDebugLog("MewX", "AsyncGetNovelItemList:onPostExecute network error");
                return;
            }

            if(tempNovelList != null && tempNovelList.size()==0) {
                String error = "in AsyncGetNovelItemList: doInBackground: tempNovelList == null || tempNovelList.size() == 0";
                GlobalConfig.wantDebugLog("MewX", error);
                Logger.writeLogger(error);
                return;
            }

            // add to total list
            appendToIdList(tempNovelList);
            tempNovelList = null;
//            if(listNovelItemAid==null)
//                listNovelItemAid = new ArrayList<>();
//            listNovelItemAid.addAll(tempNovelList);

            // refresh listif ((View) mainActivity.findViewById(R.id.list_loading) != null)
            //((View) mainActivity.findViewById(R.id.list_loading)).setVisibility(View.GONE);
            refreshIdList();
            GlobalConfig.wantDebugLog("MewX", "refresh over");

            isLoading = false;


            super.onPostExecute(integer);
        }
    }

    private class AsyncGetSearchResultList extends AsyncTask<String, Integer, Integer> {
        private int resultSize = 0;

        @Override
        protected Integer doInBackground(String... params) {
            return null;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);

            if(integer == -1) {
                // network error
                GlobalConfig.wantDebugLog("MewX", "AsyncGetSearchResultList:onPostExecute network error");

                // redo button

                return;
            }
        }
    }


}
