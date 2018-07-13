package org.mewx.wenku8.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v4.content.ContextCompat;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.readystatesoftware.systembartint.SystemBarTintManager;
import com.umeng.analytics.MobclickAgent;

import org.mewx.wenku8.R;
import org.mewx.wenku8.adapter.ReviewReplyItemAdapter;
import org.mewx.wenku8.global.api.ReviewReplyList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.util.LightNetwork;

import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by MewX on 2018/7/12.
 * Novel Review Activity.
 */
public class NovelReviewReplyListActivity extends AppCompatActivity implements MyItemLongClickListener {
    // private vars
    private int rid = 1;
    private String reviewTitle = "";

    // components
    private LinearLayoutManager mLayoutManager;
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private LinearLayout mLoadingLayout;
    private RecyclerView mRecyclerView;
    private TextView mLoadingStatusTextView;
    private TextView mLoadingButton;

    // switcher
    private ReviewReplyItemAdapter mAdapter;
    private ReviewReplyList reviewReplyList = new ReviewReplyList();
    private static AtomicBoolean isLoading = new AtomicBoolean(false);
    int pastVisibleItems, visibleItemCount, totalItemCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_novel_review_list);

        // fetch values
        rid = getIntent().getIntExtra("rid", 1);
        reviewTitle = getIntent().getStringExtra("title");

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
        DividerItemDecoration horizontalDecoration = new DividerItemDecoration(mRecyclerView.getContext(), DividerItemDecoration.VERTICAL);
        Drawable horizontalDivider = ContextCompat.getDrawable(getApplication(), R.drawable.divider_horizontal);
        if (horizontalDivider != null) {
            horizontalDecoration.setDrawable(horizontalDivider);
            mRecyclerView.addItemDecoration(horizontalDecoration);
        }
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);

        // Listener
        mRecyclerView.addOnScrollListener(new MyOnScrollListener());

        // set click event for retry and cancel loading
        mLoadingButton.setOnClickListener(v -> new AsyncReviewReplyListLoader(this, mSwipeRefreshLayout, rid, reviewReplyList).execute()); // retry loading

        mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.myAccentColor));
        mSwipeRefreshLayout.setOnRefreshListener(() -> {
            // reload all
            reviewReplyList = new ReviewReplyList();
            mAdapter = null;
            new AsyncReviewReplyListLoader(this, mSwipeRefreshLayout, rid, reviewReplyList).execute();
        });

        // load initial content
        new AsyncReviewReplyListLoader(this, mSwipeRefreshLayout, rid, reviewReplyList).execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (getSupportActionBar() != null && reviewTitle != null && !reviewTitle.isEmpty())
            getSupportActionBar().setTitle(reviewTitle);
        getMenuInflater().inflate(R.menu.menu_review_reply_list, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        else if (menuItem.getItemId() == R.id.action_new) {
            // TODO: new reply activity (keep or remove)
            Toast.makeText(getApplication(), "new", Toast.LENGTH_SHORT).show();
        }
        return super.onOptionsItemSelected(menuItem);
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

    ReviewReplyItemAdapter getAdapter() {
        return mAdapter;
    }

    void setAdapter(ReviewReplyItemAdapter adapter) {
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
    public void onItemLongClick(View view, int position) {
        String content = reviewReplyList.getList().get(position).getContent();
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText(getResources().getString(R.string.app_name), content);
        if (clipboard != null) {
            clipboard.setPrimaryClip(clip);
            Toast.makeText(NovelReviewReplyListActivity.this,
                    String.format(getResources().getString(R.string.system_copied_to_clipboard), content),
                    Toast.LENGTH_SHORT).show();
        }
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
                // load more thread
                if (reviewReplyList.getCurrentPage() < reviewReplyList.getTotalPage()) {
                    // load more toast
                    Snackbar.make(mRecyclerView, getResources().getString(R.string.list_loading)
                                    + "(" + Integer.toString(reviewReplyList.getCurrentPage() + 1) + "/" + reviewReplyList.getTotalPage() + ")",
                            Snackbar.LENGTH_SHORT).show();

                    new AsyncReviewReplyListLoader(NovelReviewReplyListActivity.this, mSwipeRefreshLayout, rid, reviewReplyList).execute();
                }
            }
        }
    }

    private static class AsyncReviewReplyListLoader extends AsyncTask<Void, Void, Void> {
        private WeakReference<NovelReviewReplyListActivity> novelReviewListActivityWeakReference;
        private WeakReference<SwipeRefreshLayout> swipeRefreshLayoutWeakReference;
        private int rid;
        private ReviewReplyList reviewReplyList;

        private boolean runOrNot = true; // by default, run it
        private boolean metNetworkIssue = false;

        AsyncReviewReplyListLoader(@NonNull NovelReviewReplyListActivity novelReviewListActivity, @NonNull SwipeRefreshLayout swipeRefreshLayout, int rid, @NonNull ReviewReplyList reviewReplyList) {
            this.novelReviewListActivityWeakReference = new WeakReference<>(novelReviewListActivity);
            this.swipeRefreshLayoutWeakReference = new WeakReference<>(swipeRefreshLayout);
            this.rid = rid;
            this.reviewReplyList = reviewReplyList;
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

                NovelReviewReplyListActivity tempActivity = novelReviewListActivityWeakReference.get();
                if (tempActivity != null) tempActivity.hideRetryButton();
            }
        }

        @Override
        protected Void doInBackground(Void... v) {
            if (!runOrNot || reviewReplyList.getCurrentPage() + 1 > reviewReplyList.getTotalPage()) return null;

            // load current page + 1
            byte[] tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getCommentContentParams(rid, reviewReplyList.getCurrentPage() + 1));
            if (tempXml == null) {
                metNetworkIssue = true;
                return null; // network issue
            }
            String xml = new String(tempXml, Charset.forName("UTF-8"));
            Log.d(NovelReviewReplyListActivity.class.getSimpleName(), xml);
            Wenku8Parser.parseReviewReplyList(reviewReplyList, xml);
            return null;
        }

        @Override
        protected void onPostExecute(Void v) {
            // refresh everything when required
            if (!runOrNot) return;

            NovelReviewReplyListActivity tempActivity = novelReviewListActivityWeakReference.get();
            if (metNetworkIssue) {
                // met net work issue, show retry button
                if (tempActivity != null) tempActivity.showRetryButton();
            } else {
                // all good, update list
                if (tempActivity.getAdapter() == null) {
                    ReviewReplyItemAdapter reviewReplyItemAdapter = new ReviewReplyItemAdapter(reviewReplyList);
                    tempActivity.setAdapter(reviewReplyItemAdapter);
                    reviewReplyItemAdapter.setOnItemLongClickListener(tempActivity);
                    tempActivity.getRecyclerView().setAdapter(reviewReplyItemAdapter);
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
