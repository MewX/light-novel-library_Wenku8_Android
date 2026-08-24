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
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.NovelInfoActivity;
import org.mewx.wenku8.adapter.NovelItemAdapterUpdate;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.NovelListWithInfoParser;
import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.network.LightNetwork;
import org.mewx.wenku8.util.AsyncTaskTracker;
import org.mewx.wenku8.util.CrashReporter;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.android.material.progressindicator.LinearProgressIndicator;

public class NovelItemListFragment extends Fragment implements MyItemClickListener, MyItemLongClickListener {

    private static final String SEARCH_TYPE = "search";

    private String listType = "";
    private String searchKey = "";
    private final AtomicBoolean isLoading = new AtomicBoolean(false);

    // members
    private ActionBar actionBar = null;
    private LinearLayoutManager mLayoutManager = null;
    private RecyclerView mRecyclerView = null;
    private LinearProgressIndicator spb = null;

    // novel list info
    private List<Integer> listNovelItemAid = new ArrayList<>(); // aid list
    private List<NovelItemInfoUpdate> listNovelItemInfo = new ArrayList<>(); // novel info list
    private NovelItemAdapterUpdate mAdapter = null;

    // page info
    private int currentPage = 1; // default 1
    private int totalPage = 0; // default 0

    public NovelItemListFragment() {
        // Required empty public constructor
    }

    public static NovelItemListFragment newInstance(Bundle args) {
        NovelItemListFragment fragment = new NovelItemListFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private final AsyncTaskTracker tracker = new AsyncTaskTracker();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Bundle args = getArguments();
        listType = args == null ? "" : args.getString("type", "");
        // judge if is 'search'
        searchKey = args != null && SEARCH_TYPE.equals(listType) ? args.getString("key", "") : "";

        actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    }

    @Override
    public void onDestroy() {
        // onDestroy, deliberately not onDestroyView. The Fragment outlives its view in a
        // ViewPager, and its isLoading flag with it; cancelling on view destruction would skip
        // the onPostExecute that clears that flag and leave the list stuck on "Loading..." --
        // the bug 723e93d patched. By onDestroy the flag is going away too.
        tracker.cancelAll();
        super.onDestroy();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_novel_item_list,container,false);
        rootView.setTag(listType); // set TAG

        // Set warning message.
        rootView.findViewById(R.id.relay_warning).setOnClickListener(view -> new MaterialAlertDialogBuilder(getContext())
                .setTitle(getResources().getString(R.string.system_warning))
                .setMessage(getResources().getString(R.string.relay_warning_full))
                .setPositiveButton(R.string.dialog_positive_ok, null)
                .show());

        // init values
        if (totalPage == 0) currentPage = 1; // default 1

        mLayoutManager = new LinearLayoutManager(getActivity());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView = rootView.findViewById(R.id.novel_item_list);
        mRecyclerView.setHasFixedSize(false); // set variable size
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setLayoutManager(mLayoutManager);

        // Reuse existing data if available
        if (!listNovelItemInfo.isEmpty()) {
            mAdapter = new NovelItemAdapterUpdate(listNovelItemInfo);
            mAdapter.setOnItemClickListener(this);
            mAdapter.setOnItemLongClickListener(this);
            mRecyclerView.setAdapter(mAdapter);
        }
        else {
            // List request
            if(listType.equals(SEARCH_TYPE)) {
                // update UI
                spb = getActivity().findViewById(R.id.spb);
                spb.setVisibility(View.VISIBLE);
    
                // execute task
                Toast.makeText(getActivity(),"search",Toast.LENGTH_SHORT).show();
                AsyncGetSearchResultList asyncGetSearchResultList = tracker.track(new AsyncGetSearchResultList());
                asyncGetSearchResultList.execute(searchKey);
            }
            else {
                // Listener
                mRecyclerView.addOnScrollListener(new MyOnScrollListener());
                mRecyclerView.addOnScrollListener(new OnHidingScrollListener());
                AsyncGetNovelItemList asyncGetNovelItemList = tracker.track(new AsyncGetNovelItemList());
                asyncGetNovelItemList.execute(currentPage);
            }
        }
        return rootView;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
    }


    @Override
    public void onItemClick(View view, final int position) {
        //Toast.makeText(getActivity(),"item click detected", Toast.LENGTH_SHORT).show();
        if(position < 0 || position >= listNovelItemAid.size()) {
            // ArrayIndexOutOfBoundsException
            Toast.makeText(getActivity(), "ArrayIndexOutOfBoundsException: " + position + " in size " + listNovelItemAid.size(), Toast.LENGTH_SHORT).show();
            return;
        }

        // go to detail activity
        Intent intent = new Intent(getActivity(), NovelInfoActivity.class);
        intent.putExtra("aid", listNovelItemAid.get(position));
        intent.putExtra("from", "list");
        intent.putExtra("title", ((TextView) view.findViewById(R.id.novel_title)).getText());

        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                Pair.create(view.findViewById(R.id.novel_cover), "novel_cover"),
                Pair.create(view.findViewById(R.id.novel_title), "novel_title"));
        ActivityCompat.startActivity(getActivity(), intent, options.toBundle());
    }

    @Override
    public void onItemLongClick(View view, int position) {
        // empty
    }

    private class OnHidingScrollListener extends RecyclerView.OnScrollListener {
        int toolbarMarginOffset = 0;

        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            toolbarMarginOffset += dy;
            if (toolbarMarginOffset > actionBar.getHeight())
                actionBar.hide();
            if (toolbarMarginOffset == 0)
                actionBar.show();
        }
    }


    /**
     * Appends a page that already carries its novel details, so no row has to fetch its own.
     * The search path still arrives as bare ids and goes through {@link #refreshEntireIdList()}.
     */
    private void refreshPartialInfoList(List<NovelItemInfoUpdate> newNovelItems) {
        // Some sanity checks.
        if (newNovelItems == null || newNovelItems.isEmpty()) {
            return;
        }

        // Just append new updates.
        int startIndex = listNovelItemInfo.size();

        // The aid list backs onItemClick, so it has to grow alongside the info list.
        for(NovelItemInfoUpdate info : newNovelItems) {
            listNovelItemAid.add(info.aid);
            listNovelItemInfo.add(info);
        }

        if(mAdapter == null) {
            mAdapter = new NovelItemAdapterUpdate();
            mAdapter.setOnItemClickListener(this);
            mAdapter.setOnItemLongClickListener(this);
        }
        mAdapter.refreshDataset(listNovelItemInfo);

        if(currentPage == 1 && mRecyclerView != null) {
            mRecyclerView.setAdapter(mAdapter);
        }
        else {
            mAdapter.notifyItemRangeInserted(startIndex, newNovelItems.size());
        }
    }

    /**
     * Refresh all the list with Integer array.
     * If empty, create;
     */
    private void refreshEntireIdList() {
        // Not creating new list for incremental data update.
        listNovelItemInfo.clear();

        // set empty
        for(Integer temp : listNovelItemAid) {
            listNovelItemInfo.add(new NovelItemInfoUpdate(temp));
        }

        if(mAdapter == null) {
            mAdapter = new NovelItemAdapterUpdate();
            mAdapter.setOnItemClickListener(this);
            mAdapter.setOnItemLongClickListener(this);
        }
        mAdapter.refreshDataset(listNovelItemInfo);

        if(currentPage == 1 && mRecyclerView != null) {
            mRecyclerView.setAdapter(mAdapter);
        }
        else
            mAdapter.notifyDataSetChanged();
    }

    private class MyOnScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            int pastVisiblesItems, visibleItemCount, totalItemCount;
            visibleItemCount = mLayoutManager.getChildCount();
            totalItemCount = mLayoutManager.getItemCount();
            pastVisiblesItems = mLayoutManager.findFirstVisibleItemPosition();

            if (!isLoading.get()) {
                // 滚动到一半的时候加载，即：剩余2个元素的时候就加载
                if (visibleItemCount + pastVisiblesItems + 2 >= totalItemCount && (totalPage==0 || currentPage < totalPage)) {
                    // load more toast
                    Snackbar.make(mRecyclerView, getResources().getString(R.string.list_loading)
                                    + "(" + (currentPage + 1) + "/" + totalPage + ")",
                            Snackbar.LENGTH_SHORT).show();

                    // load more thread
                    tracker.track(new AsyncGetNovelItemList()).execute(currentPage + 1);
                }
            }
        }
    }

    private class AsyncGetNovelItemList extends AsyncTask<Integer, Integer, Integer> {
        private boolean usingWenku8Relay = false;

        private List<NovelItemInfoUpdate> tempNovelItems = new ArrayList<>();

        private boolean raceCondition;

        AsyncGetNovelItemList() {
            raceCondition = !isLoading.compareAndSet(false, true);
        }

        @Override
        protected Integer doInBackground(Integer... params) {
            // Check if another loading happening.
            if(raceCondition) {
                Log.d("MewX", "doInBackground: blocking change");
                return -1;
            }

            // Update the current page to the new page.
            currentPage = params[0];

            // params[0] is current page number
            ContentValues cv = Wenku8API.getNovelListWithInfo(Wenku8API.getNovelSortedBy(listType),
                    currentPage, GlobalConfig.getCurrentLang());
            byte[] temp = LightNetwork.LightHttpPostConnection( Wenku8API.BASE_URL, cv);
            if (temp == null) {
                return -1;
            }
            try {
                Log.d("MewX", "doInBackground: loading page " + currentPage);
                NovelListWithInfoParser.Result result =
                        NovelListWithInfoParser.parse(new String(temp, "UTF-8"));
                if (result == null) {
                    return -1;
                }
                totalPage = result.pageNum;
                tempNovelItems = result.items;
            }
            catch (UnsupportedEncodingException e) {
                CrashReporter.recordException("NovelItemListFragment.AsyncGetNovelItemList", e);
            }

            // judge result
            if (tempNovelItems.isEmpty()) {
                Log.d("MewX", "in AsyncGetNovelItemList: doInBackground: tempNovelItems is empty");
            }
            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            // Always reset loading status first, regardless of fragment state.
            isLoading.set(false);

            // Updating the results only when the fragment is attached correctly.
            if (!isAdded() || getActivity() == null) {
                return;
            }

            if (integer == -1) {
                // network error
                return;
            }
            if (tempNovelItems.isEmpty()) {
                Log.d("MewX", "in AsyncGetNovelItemList: onPostExecute: tempNovelItems is empty");
                return;
            }

            refreshPartialInfoList(tempNovelItems);

            // TODO: remove this warning view because all traffic will come from the relay.
            View relayWarningView = getActivity().findViewById(R.id.relay_warning);
            if (relayWarningView != null) {
                relayWarningView.setVisibility(usingWenku8Relay ? View.VISIBLE : View.GONE);
            }
        }
    }

    private class AsyncGetSearchResultList extends AsyncTask<String, Integer, Integer> {

        @Override
        protected Integer doInBackground(String... params) {

            // get search result by novel title
            ContentValues cv = Wenku8API.searchNovelByNovelName(params[0], GlobalConfig.getCurrentLang());
            byte[] tempListTitle = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
            if(tempListTitle == null) return -1;

            // purify returned data
            List<Integer> listResultList = new ArrayList<>(); // result list
            try {
                Log.d("MewX", new String(tempListTitle, "UTF-8"));
                Pattern p = Pattern.compile("aid=\'(.*)\'"); // match content between "aid=\'" and "\'"
                Matcher m = p.matcher(new String(tempListTitle, "UTF-8"));
                while (m.find())
                    listResultList.add(Integer.valueOf(m.group(1)));
            } catch (UnsupportedEncodingException e) {
                CrashReporter.recordException("NovelItemListFragment.AsyncGetSearchResultList", e);
            }

            // get search result by author name
            cv = Wenku8API.searchNovelByAuthorName(params[0], GlobalConfig.getCurrentLang());
            byte[] tempListName = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, cv);
            if(tempListName == null) return -1;

            // purify returned data
            List<Integer> listResultList2 = new ArrayList<>(); // result list
            try {
                Log.d("MewX", new String(tempListName, "UTF-8"));
                Pattern p = Pattern.compile("aid=\'(.*)\'"); // match content between "aid=\'" and "\'"
                Matcher m = p.matcher(new String(tempListName, "UTF-8"));
                while (m.find()) {
                    listResultList2.add(Integer.valueOf(m.group(1)));
                    Log.d("MewX", listResultList2.get(listResultList2.size()-1).toString());
                }
            } catch (UnsupportedEncodingException e) {
                CrashReporter.recordException("NovelItemListFragment.AsyncGetSearchResultList", e);
            }

            // set migrate
            listNovelItemAid = new ArrayList<>();
            listNovelItemAid.addAll(listResultList);
            listNovelItemAid.removeAll(listResultList2);
            listNovelItemAid.addAll(listResultList2);
            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            super.onPostExecute(integer);

            // Updating the results only when the fragment is attached correctly.
            if (!isAdded() || getActivity() == null) {
                return;
            }

            spb.setVisibility(View.INVISIBLE);
            if(integer == -1) {
                Toast.makeText(getActivity(), getResources().getString(R.string.system_network_error),Toast.LENGTH_LONG).show();
                return;
            }
            if(listNovelItemAid.isEmpty()) {
                Toast.makeText(getActivity(), getResources().getString(R.string.task_null),Toast.LENGTH_LONG).show();
                return;
            }
            // show all items
            refreshEntireIdList();
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        if(actionBar != null)
            actionBar.show();
    }
}
