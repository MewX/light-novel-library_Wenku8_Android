package org.mewx.wenku8.activity;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.snackbar.Snackbar;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.mewx.wenku8.R;
import org.mewx.wenku8.adapter.ReviewReplyItemAdapter;
import org.mewx.wenku8.global.api.ReviewReplyList;
import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.network.LightNetwork;
import org.mewx.wenku8.network.LightUserSession;

import java.lang.ref.WeakReference;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by MewX on 2018/7/12.
 * Novel Review Activity.
 */
public class NovelReviewReplyListActivity extends BaseMaterialActivity implements MyItemLongClickListener {
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
    private EditText etReplyText;

    // switcher
    private ReviewReplyItemAdapter mAdapter;
    private ReviewReplyList reviewReplyList = new ReviewReplyList();
    private static AtomicBoolean isLoading = new AtomicBoolean(false);
    int pastVisibleItems, visibleItemCount, totalItemCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_novel_review_reply_list);

        // Init Firebase Analytics on GA4.
        FirebaseAnalytics.getInstance(this);

        // fetch values
        rid = getIntent().getIntExtra("rid", 1);
        reviewTitle = getIntent().getStringExtra("title");

        // get views and set title
        // get views
        mLoadingLayout = findViewById(R.id.list_loading);
        mSwipeRefreshLayout = findViewById(R.id.swipe_refresh_layout);
        mRecyclerView = findViewById(R.id.review_item_list);
        mLoadingStatusTextView = findViewById(R.id.list_loading_status);
        mLoadingButton = findViewById(R.id.btn_loading);
        etReplyText = findViewById(R.id.review_reply_edit_text);
        LinearLayout llReplyButton = findViewById(R.id.review_reply_send);

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
        mSwipeRefreshLayout.setOnRefreshListener(this::refreshReviewReplyList);

        llReplyButton.setOnClickListener(ignored -> {
            if (!LightUserSession.getLogStatus()) {
                Toast.makeText(this, R.string.system_not_logged_in, Toast.LENGTH_SHORT).show();
                return;
            }
            String temp = etReplyText.getText().toString();
            String badWord = Wenku8API.searchBadWords(temp);
            if (badWord != null) {
                Toast.makeText(getApplication(), String.format(getResources().getString(R.string.system_containing_bad_word), badWord), Toast.LENGTH_SHORT).show();
            } else if (temp.length() < Wenku8API.MIN_REPLY_TEXT) {
                Toast.makeText(getApplication(), getResources().getString(R.string.system_review_too_short), Toast.LENGTH_SHORT).show();
            } else {
                // hide ime
                InputMethodManager imm = (InputMethodManager) getSystemService(Activity.INPUT_METHOD_SERVICE);
                View view = getCurrentFocus();
                if (view == null) view = new View(this);
                if (imm != null) imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

                // submit
                new AsyncPublishReply(etReplyText, this, rid, temp).execute();
            }
        });

        // load initial content
        new AsyncReviewReplyListLoader(this, mSwipeRefreshLayout, rid, reviewReplyList).execute();
    }

    void refreshReviewReplyList() {
        // reload all
        reviewReplyList = new ReviewReplyList();
        mAdapter = null;
        new AsyncReviewReplyListLoader(this, mSwipeRefreshLayout, rid, reviewReplyList).execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (getSupportActionBar() != null && reviewTitle != null && !reviewTitle.isEmpty())
            getSupportActionBar().setTitle(reviewTitle);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            onBackPressed();
        }
        return super.onOptionsItemSelected(menuItem);
    }

    @Override
    public void onBackPressed() {
        String temp = etReplyText.getText().toString().trim();
        if (!temp.isEmpty()) {
            // TODO: new window to verify exit or not
        } else {
            super.onBackPressed();
        }
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
                                    + "(" + (reviewReplyList.getCurrentPage() + 1) + "/" + reviewReplyList.getTotalPage() + ")",
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


    private static class AsyncPublishReply extends AsyncTask<String, Void, Integer> {
        private static AtomicBoolean isLoading = new AtomicBoolean(false);

        private WeakReference<EditText> editTextWeakReference;
        private WeakReference<NovelReviewReplyListActivity> activityWeakReference;
        private int rid;
        private String content;

        private boolean runOrNot = true; // true - run

        AsyncPublishReply(@NonNull EditText editText, @NonNull NovelReviewReplyListActivity activity, int rid, @NonNull String content) {
            this.editTextWeakReference = new WeakReference<>(editText);
            this.activityWeakReference = new WeakReference<>(activity);
            this.rid = rid;
            this.content = content;
        }

        @Override
        protected void onPreExecute() {
            if (isLoading.getAndSet(true)) {
                runOrNot = false; // do not run
            } else {
                // disable text and submit
                EditText editText = editTextWeakReference.get();
                if (editText != null) {
                    editText.setEnabled(false);
                }
            }
        }

        @Override
        protected Integer doInBackground(String... strings) {
            byte[] tempXml = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getCommentReplyParams(rid, content));
            if (tempXml == null) return 0; // network issue
            String xml = new String(tempXml, Charset.forName("UTF-8")).trim();
            Log.d(NovelReviewReplyListActivity.class.getSimpleName(), xml);
            return Integer.valueOf(xml);
        }

        @Override
        protected void onPostExecute(Integer i) {
            if (runOrNot) {
                EditText editText = editTextWeakReference.get();
                NovelReviewReplyListActivity activity = activityWeakReference.get();
                switch (i) {
                    case 1:
                        // successful -> clear and enable edit text
                        if (editText != null) {
                            editText.setText("");
                        }

                        // refresh page
                        if (activity != null) {
                            activity.refreshReviewReplyList();
                        }
                        break;

                    case 11:
                        if (activity != null) {
                            Toast.makeText(activity, activity.getResources().getString(R.string.system_post_locked), Toast.LENGTH_SHORT).show();
                        }
                        break;

                    default:
                        // met network or other issue
                        if (activity != null) {
                            Toast.makeText(activity, activity.getResources().getString(R.string.system_network_error), Toast.LENGTH_SHORT).show();
                        }
                        break;
                }

                // enable edit text
                if (editText != null) {
                    editText.setEnabled(true);
                }
                isLoading.set(false);
            }
        }
    }

}
