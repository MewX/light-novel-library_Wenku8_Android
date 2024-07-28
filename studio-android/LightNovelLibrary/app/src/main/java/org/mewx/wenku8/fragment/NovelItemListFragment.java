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

import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.google.android.material.snackbar.Snackbar;

import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.NovelInfoActivity;
import org.mewx.wenku8.adapter.NovelItemAdapterUpdate;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.util.LightNetwork;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import fr.castorflex.android.smoothprogressbar.SmoothProgressBar;

public class NovelItemListFragment extends Fragment implements MyItemClickListener, MyItemLongClickListener {

    private static final String SEARCH_TYPE = "search";

    private String listType = "";
    private String searchKey = "";
    private final AtomicBoolean isLoading = new AtomicBoolean(false);

    // members
    private ActionBar actionBar = null;
    private LinearLayoutManager mLayoutManager = null;
    private RecyclerView mRecyclerView = null;
    private SmoothProgressBar spb = null;

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

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        listType = getArguments().getString("type");
        // judge if is 'search'
        searchKey = listType.equals(SEARCH_TYPE) ? getArguments().getString("key") : "";

        actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_novel_item_list,container,false);
        rootView.setTag(listType); // set TAG

        // Set warning message.
        rootView.findViewById(R.id.relay_warning).setOnClickListener(view -> new MaterialDialog.Builder(getContext())
                .theme(Theme.LIGHT)
                .backgroundColorRes(R.color.dlgBackgroundColor)
                .contentColorRes(R.color.dlgContentColor)
                .positiveColorRes(R.color.dlgPositiveButtonColor)
                .negativeColorRes(R.color.dlgNegativeButtonColor)
                .title(getResources().getString(R.string.system_warning))
                .content(getResources().getString(R.string.relay_warning_full))
                .positiveText(R.string.dialog_positive_ok)
                .show());

        // init values
        listNovelItemAid = new ArrayList<>();
        listNovelItemInfo = new ArrayList<>();
        currentPage = 1; // default 1
        totalPage = 0; // default 0

        mLayoutManager = new LinearLayoutManager(getActivity());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView = rootView.findViewById(R.id.novel_item_list);
        mRecyclerView.setHasFixedSize(false); // set variable size
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setLayoutManager(mLayoutManager);

        // List request
        if(listType.equals(SEARCH_TYPE)) {
            // update UI
            spb = getActivity().findViewById(R.id.spb);
            spb.progressiveStart();

            // execute task
            Toast.makeText(getActivity(),"search",Toast.LENGTH_SHORT).show();
            AsyncGetSearchResultList asyncGetSearchResultList = new AsyncGetSearchResultList();
            asyncGetSearchResultList.execute(searchKey);
        }
        else {
            // Listener
            mRecyclerView.addOnScrollListener(new MyOnScrollListener());
            mRecyclerView.addOnScrollListener(new OnHidingScrollListener());
            AsyncGetNovelItemList asyncGetNovelItemList = new AsyncGetNovelItemList();
            asyncGetNovelItemList.execute(currentPage);
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


    private void refreshPartialIdList(List<Integer> newNovelItemAids) {
        // Some sanity checks.
        if (newNovelItemAids == null || newNovelItemAids.isEmpty()) {
            return;
        }

        // add to total list
        listNovelItemAid.addAll(newNovelItemAids);

        // Just append new updates.
        int startIndex = listNovelItemInfo.size();

        // set empty
        for(Integer aid : newNovelItemAids) {
            listNovelItemInfo.add(new NovelItemInfoUpdate(aid));
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
            mAdapter.notifyItemRangeInserted(startIndex, newNovelItemAids.size());
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
                    new AsyncGetNovelItemList().execute(currentPage + 1);
                }
            }
        }
    }

    private class AsyncGetNovelItemList extends AsyncTask<Integer, Integer, Integer> {
        private boolean usingWenku8Relay = false;

        private List<Integer> tempNovelList = new ArrayList<>();

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
            ContentValues cv = Wenku8API.getNovelList(Wenku8API.getNOVELSORTBY(listType), currentPage);
            byte[] temp = LightNetwork.LightHttpPostConnection( Wenku8API.BASE_URL, cv);
            if(temp == null) {
                // Try requesting from the relay.
                temp = LightNetwork.LightHttpPostConnection(Wenku8API.RELAY_URL, cv, false);
                if (temp == null) {
                    // Still failed, return the error code.
                    return -1;
                }
                usingWenku8Relay = true;
            }
            try {
                Log.d("MewX", "doInBackground: loading page " + currentPage);
                tempNovelList = Wenku8Parser.parseNovelItemList(new String(temp, "UTF-8"));
            }
            catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }

            // judge result
            if (tempNovelList.isEmpty()) {
                Log.d("MewX", "in AsyncGetNovelItemList: doInBackground: tempNovelList == null || tempNovelList.size() == 0");
                // Try requesting from the relay.
                temp = LightNetwork.LightHttpPostConnection(Wenku8API.RELAY_URL, cv, false);
                if (temp == null) {
                    // Still failed, returns no error code.
                    return 0;
                }
                try {
                    tempNovelList = Wenku8Parser.parseNovelItemList(new String(temp, "UTF-8"));
                }
                catch (UnsupportedEncodingException e) {
                    e.printStackTrace();
                }
                if (tempNovelList.isEmpty()) {
                    // Still failed.
                    return 0;
                }
                usingWenku8Relay = true;
            }

            totalPage = tempNovelList.get(0);
            tempNovelList.remove(0);
            return 0;
        }

        @Override
        protected void onPostExecute(Integer integer) {
            if (integer == -1) {
                // network error
                return;
            }
            if (tempNovelList.isEmpty()) {
                Log.d("MewX", "in AsyncGetNovelItemList: onPostExecute: tempNovelList == null || tempNovelList.size() == 0");
                return;
            }

            refreshPartialIdList(tempNovelList);
            isLoading.set(false);

            if (getActivity() != null) {
                View relayWarningView = getActivity().findViewById(R.id.relay_warning);
                if (relayWarningView != null) {
                    relayWarningView.setVisibility(usingWenku8Relay ? View.VISIBLE : View.GONE);
                }
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
                e.printStackTrace();
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
                e.printStackTrace();
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

            spb.progressiveStop();
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
