package org.mewx.wenku8.activity;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.adapter.ReviewItemAdapter;
import org.mewx.wenku8.global.api.ReviewList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.util.LightNetwork;

import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by MewX on 2018/7/12.
 * Novel Review Activity.
 */
public class NovelReviewListActivity extends AppCompatActivity implements MyItemClickListener {
    // private vars
    private int aid = 1;

    // components
    private LinearLayoutManager mLayoutManager;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private LinearLayout mLoadingLayout;
    private RecyclerView mRecyclerView;
    private TextView mLoadingStatusTextView;
    private TextView mLoadingButton;

    // switcher
    private ReviewItemAdapter mAdapter;
    private ReviewList reviewList = new ReviewList();
    private static AtomicBoolean isLoading = new AtomicBoolean(false);
    int pastVisibleItems, visibleItemCount, totalItemCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_novel_review_list);

        // fetch values
        aid = getIntent().getIntExtra("aid", 1);

        // set indicator enable
        Toolbar mToolbar = findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        final Drawable upArrow = getResources().getDrawable(R.drawable.ic_svg_back);
        if(getSupportActionBar() != null && upArrow != null) {
            getSupportActionBar().setHomeButtonEnabled(true);
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            upArrow.setColorFilter(getResources().getColor(R.color.default_white), PorterDuff.Mode.SRC_ATOP);
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }

        // change status bar color tint, and this require SDK16
        if (Build.VERSION.SDK_INT >= 16 ) {
            // create our manager instance after the content view is set
            SystemBarTintManager tintManager = new SystemBarTintManager(this);
            // enable all tint
            tintManager.setStatusBarTintEnabled(true);
            tintManager.setNavigationBarTintEnabled(true);
            tintManager.setTintAlpha(0.15f);
            tintManager.setNavigationBarAlpha(0.0f);
            // set all color
            tintManager.setTintColor(getResources().getColor(android.R.color.black));
            // set Navigation bar color
            if(Build.VERSION.SDK_INT >= 21)
                getWindow().setNavigationBarColor(getResources().getColor(R.color.myNavigationColor));
        }

        // get views and set title
        // get views
        mLoadingLayout = findViewById(R.id.list_loading);
        mSwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        mRecyclerView = findViewById(R.id.review_item_list);
        mLoadingStatusTextView = findViewById(R.id.list_loading_status);
        mLoadingButton = findViewById(R.id.btn_loading);

        mRecyclerView.setHasFixedSize(false);
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        // Listener
        mRecyclerView.addOnScrollListener(new MyOnScrollListener());

        // set click event for retry and cancel loading
        mLoadingButton.setOnClickListener(v -> new AsyncReviewListLoader(this, mSwipeRefreshLayout, aid, reviewList).execute()); // retry loading

        mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.myAccentColor));
        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            // reload all
            reviewList = new ReviewList();
            mAdapter = null;
            new AsyncReviewListLoader(this, mSwipeRefreshLayout, aid, reviewList).execute();
        });

        // load initial content
        new AsyncReviewListLoader(this, mSwipeRefreshLayout, aid, reviewList).execute();
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(menuItem);
    }

    ReviewItemAdapter getAdapter() {
        return mAdapter;
    }

    void setAdapter(ReviewItemAdapter adapter) {
        this.mAdapter = adapter;
    }

    RecyclerView getRecyclerView() {
        return mRecyclerView;
    }

    void showRetryButton() {
        mLoadingStatusTextView.setText(getResources().getString(R.string.system_parse_failed));
        mLoadingButton.setVisibility(View.VISIBLE);
    }

    void hideRetryButton() {
        mLoadingStatusTextView.setText(getResources().getString(R.string.list_loading));
        mLoadingButton.setVisibility(View.GONE);
    }

    void hideListLoading() {
        hideRetryButton();
        mLoadingLayout.setVisibility(View.GONE);
    }

    @Override
    public void onItemClick(View view, int position) {
        // TODO
        Toast.makeText(NovelReviewListActivity.this, "Clicked " + position, Toast.LENGTH_SHORT).show();
    }

    private class MyOnScrollListener extends RecyclerView.OnScrollListener {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
            super.onScrolled(recyclerView, dx, dy);

            visibleItemCount = mLayoutManager.getChildCount();
            totalItemCount = mLayoutManager.getItemCount();
            pastVisibleItems = mLayoutManager.findFirstVisibleItemPosition();

            // 剩余3个元素的时候就加载
            if (!isLoading.get() && visibleItemCount + pastVisibleItems >= totalItemCount) { // can be +1/2/3 >= total
                // load more toast
                Snackbar.make(mRecyclerView, getResources().getString(R.string.list_loading)
                                + "(" + Integer.toString(reviewList.getCurrentPage() + 1) + "/" + reviewList.getTotalPage() + ")",
                        Snackbar.LENGTH_SHORT).show();

                // load more thread
                if (reviewList.getCurrentPage() < reviewList.getTotalPage()) {
                    new AsyncReviewListLoader(NovelReviewListActivity.this, mSwipeRefreshLayout, aid, reviewList).execute();
                } else {
                    Snackbar.make(mRecyclerView, getResources().getText(R.string.loading_done), Snackbar.LENGTH_SHORT).show();
                }
            }
        }
    }

    private static class AsyncReviewListLoader extends AsyncTask<Void, Void, Void> {
        private WeakReference<NovelReviewListActivity> novelReviewListActivityWeakReference;
        private WeakReference<SwipeRefreshLayout> swipeRefreshLayoutWeakReference;
        private int aid;
        private ReviewList reviewList;

        private boolean runOrNot = true; // by default, run it
        private boolean metNetworkIssue = false;

        AsyncReviewListLoader(@NonNull NovelReviewListActivity novelReviewListActivity, @NonNull SwipeRefreshLayout swipeRefreshLayout, int aid, @NonNull ReviewList reviewList) {
            this.novelReviewListActivityWeakReference = new WeakReference<>(novelReviewListActivity);
            this.swipeRefreshLayoutWeakReference = new WeakReference<>(swipeRefreshLayout);
            this.aid = aid;
            this.reviewList = reviewList;
        }

        @Override
        protected void onPreExecute() {
            if (isLoading.getAndSet(true)) {
                // is running, do not run again
                runOrNot = false;
            } else {
                // not running, so run it
                SwipeRefreshLayout tempSwipeLayout = swipeRefreshLayoutWeakReference.get();
                if (tempSwipeLayout != null) tempSwipeLayout.setRefreshing(true);

                NovelReviewListActivity tempActivity = novelReviewListActivityWeakReference.get();
                if (tempActivity != null) tempActivity.hideRetryButton();
            }
        }

        @Override
        protected Void doInBackground(Void... v) {
            if (!runOrNot || reviewList.getCurrentPage() + 1 > reviewList.getTotalPage()) return null;

            // load current page + 1
            byte[] tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getCommentListParams(aid, reviewList.getCurrentPage() + 1));
            if (tempXml == null) {
                metNetworkIssue = true;
                return null; // network issue
            }
            String xml = new String(tempXml, Charset.forName("UTF-8"));
            Log.d(NovelReviewListActivity.class.getSimpleName(), xml);
            Wenku8Parser.parseReviewList(reviewList, xml);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            // refresh everything when required
            if (!runOrNot) return;

            NovelReviewListActivity tempActivity = novelReviewListActivityWeakReference.get();
            if (metNetworkIssue) {
                // met net work issue, show retry button
                if (tempActivity != null) tempActivity.showRetryButton();
            } else {
                // all good, update list
                if (tempActivity.getAdapter() == null) {
                    ReviewItemAdapter reviewItemAdapter = new ReviewItemAdapter(reviewList);
                    tempActivity.setAdapter(reviewItemAdapter);
                    reviewItemAdapter.setOnItemClickListener(tempActivity);
                    tempActivity.getRecyclerView().setAdapter(reviewItemAdapter);
                }
                tempActivity.getAdapter().notifyDataSetChanged();

                tempActivity.hideListLoading();
            }

            // stop spinning
            SwipeRefreshLayout tempSwipeLayout = swipeRefreshLayoutWeakReference.get();
            if (tempSwipeLayout != null) tempSwipeLayout.setRefreshing(false);

            // reset loading status
            isLoading.set(false);
        }
    }

}
