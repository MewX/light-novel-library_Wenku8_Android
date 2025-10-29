package org.mewx.wenku8.activity;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.google.firebase.analytics.FirebaseAnalytics;

import org.mewx.wenku8.R;
import org.mewx.wenku8.adapter.SearchHistoryAdapter;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;

import java.util.List;

/**
 * Created by MewX on 2015/5/7.
 * Search Activity.
 */
public class SearchActivity extends BaseMaterialActivity implements MyItemClickListener, MyItemLongClickListener {

    // private vars
    private EditText toolbarSearchView = null;
    private SearchHistoryAdapter adapter = null;
    private List<String> historyList = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_search, StatusBarColor.WHITE);

        // Init Firebase Analytics on GA4.
        FirebaseAnalytics.getInstance(this);

        // bind views
        toolbarSearchView = findViewById(R.id.search_view);
        View searchClearButton = findViewById(R.id.search_clear);

        // Clear search text when clear button is tapped
        searchClearButton.setOnClickListener(v -> toolbarSearchView.setText(""));

        // set search clear icon color
        ImageView searchClearIcon = findViewById(R.id.search_clear_icon);
        searchClearIcon.setColorFilter(getResources().getColor(R.color.mySearchToggleColor), PorterDuff.Mode.SRC_ATOP);

        // set history list
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(this);
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);

        RecyclerView mRecyclerView = this.findViewById(R.id.search_history_list);
        mRecyclerView.setHasFixedSize(true); // set variable size
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setLayoutManager(mLayoutManager);

        historyList = GlobalConfig.getSearchHistory();
        adapter = new SearchHistoryAdapter(historyList);
        adapter.setOnItemClickListener(this); // add item click listener
        adapter.setOnItemLongClickListener(this); // add item click listener
        mRecyclerView.setAdapter(adapter);

        // set search action
        toolbarSearchView.setOnEditorActionListener((v, actionId, event) -> {
            // purify
            String temp = toolbarSearchView.getText().toString().trim();
            if(temp.isEmpty()) return false;

            // real action
            //Toast.makeText(MyApp.getContext(), temp, Toast.LENGTH_SHORT).show();
            GlobalConfig.addSearchHistory(temp);
            refreshHistoryList();

            // jump to search
            Intent intent = new Intent(SearchActivity.this, SearchResultActivity.class);
            intent.putExtra("key", temp);
            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT); // long-press will cause repetitions
            startActivity(intent);
            overridePendingTransition( R.anim.fade_in, R.anim.hold);

            return false;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // set back arrow icon
        final Drawable upArrow = getResources().getDrawable(R.drawable.ic_svg_back);
        if(upArrow != null && getSupportActionBar() != null) {
            upArrow.setColorFilter(getResources().getColor(R.color.mySearchToggleColor), PorterDuff.Mode.SRC_ATOP);
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }

        refreshHistoryList();
    }

    private void refreshHistoryList() {
        historyList = GlobalConfig.getSearchHistory();
        if(adapter != null) {
            adapter.notifyDataSetChanged(); // may be back from search list
        }
    }

    @Override
    public void onItemClick(View view, final int position) {
        if(position < 0 || position >= historyList.size()) {
            // ArrayIndexOutOfBoundsException
            Toast.makeText(this, "ArrayIndexOutOfBoundsException: " + position + " in size " + historyList.size(), Toast.LENGTH_SHORT).show();
            return;
        }

        toolbarSearchView.setText(historyList.get(position)); // copy text to editText
        toolbarSearchView.setSelection(historyList.get(position).length()); // move cursor
    }

    @Override
    public void onItemLongClick(View view, final int position) {
        new MaterialDialog.Builder(this)
                .onPositive((ignored1, ignored2) -> {
                    GlobalConfig.deleteSearchHistory(historyList.get(position));
                    refreshHistoryList();
                })
                .theme(Theme.LIGHT)
                .backgroundColorRes(R.color.dlgBackgroundColor)
                .contentColorRes(R.color.dlgContentColor)
                .positiveColorRes(R.color.dlgPositiveButtonColor)
                .negativeColorRes(R.color.dlgNegativeButtonColor)
                .title(getResources().getString(R.string.dialog_content_delete_one_search_record))
                .content(historyList.get(position))
                .contentGravity(GravityEnum.CENTER)
                .positiveText(R.string.dialog_positive_likethis)
                .negativeText(R.string.dialog_negative_preferno)
                .show();
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
        super.onBackPressed();

        // leave animation: fade out
        overridePendingTransition(0, R.anim.fade_out);
    }

}
