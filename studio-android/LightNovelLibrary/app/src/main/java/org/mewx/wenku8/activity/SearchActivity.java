package org.mewx.wenku8.activity;

import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.adapter.SearchHistoryAdapter;
import org.mewx.wenku8.global.GlobalConfig;

import java.util.List;

/**
 * Created by MewX on 2015/5/7.
 */
public class SearchActivity extends AppCompatActivity implements SearchHistoryAdapter.MyItemClickListener, SearchHistoryAdapter.MyItemLongClickListener {

    // private vars
    private LinearLayout searchContainer = null;
    private EditText toolbarSearchView = null;
    private View searchClearButton = null;
    private ImageView searchClearIcon = null;
    private Toolbar mToolbar = null;
    private LinearLayoutManager mLayoutManager = null;
    private RecyclerView mRecyclerView = null;
    private SearchHistoryAdapter adapter = null;
    private List<String> historyList = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_search);

        // bind views
        searchContainer = (LinearLayout)findViewById(R.id.search_container);
        toolbarSearchView = (EditText) findViewById(R.id.search_view);
        searchClearButton = (View) findViewById(R.id.search_clear);

        // Clear search text when clear button is tapped
        searchClearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toolbarSearchView.setText("");
            }
        });

        // set indicator enable
        mToolbar = (Toolbar) findViewById(R.id.toolbar_actionbar);
        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);

        // set back arrow icon
        final Drawable upArrow = getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha);
        upArrow.setColorFilter(getResources().getColor(R.color.mySearchToggleColor), PorterDuff.Mode.SRC_ATOP);
        getSupportActionBar().setHomeAsUpIndicator(upArrow);

        // set search clear icon color
        searchClearIcon = (ImageView)findViewById(R.id.search_clear_icon);
        searchClearIcon.setColorFilter(getResources().getColor(R.color.mySearchToggleColor), PorterDuff.Mode.SRC_ATOP);

        // set history list
        mLayoutManager = null;
        mLayoutManager = new LinearLayoutManager(this);
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);

        mRecyclerView = null;
        mRecyclerView = (RecyclerView) this.findViewById(R.id.search_history_list);
        mRecyclerView.setHasFixedSize(true); // set variable size
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setLayoutManager(mLayoutManager);

        historyList = GlobalConfig.getSearchHistory();
        adapter = new SearchHistoryAdapter(historyList);
        adapter.setOnItemClickListener(this); // add item click listener
        adapter.setOnItemLongClickListener(this); // add item click listener
        mRecyclerView.setAdapter(adapter);

        // set search action
        toolbarSearchView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                // purify
                String temp = toolbarSearchView.getText().toString().trim();
                if(temp.length()==0) return false;

                // real action
                Toast.makeText(MyApp.getContext(), toolbarSearchView.getText().toString(), Toast.LENGTH_SHORT).show();
                GlobalConfig.addSearchHistory(toolbarSearchView.getText().toString());
                refreshHistoryList();

                // jump to search


                return false;
            }
        });

        return;
    }

    @Override
    protected void onResume() {
        super.onResume();

        refreshHistoryList();

        return;
    }

    private void refreshHistoryList() {
        historyList = GlobalConfig.getSearchHistory();
        if(adapter != null) {
            adapter.notifyDataSetChanged(); // may be back from search list
        }

        return;
    }

    @Override
    public void onItemClick(View view, final int postion) {
        //Toast.makeText(this,postion+"",Toast.LENGTH_SHORT).show();
        toolbarSearchView.setText(historyList.get(postion)); // copy text to editText
        toolbarSearchView.setSelection(historyList.get(postion).length()); // move cursor
    }

    @Override
    public void onItemLongClick(View view, final int postion) {
        //Toast.makeText(this, postion + ": Long Click Detected", Toast.LENGTH_SHORT).show();
        new MaterialDialog.Builder(this)
                .callback(new MaterialDialog.ButtonCallback() {
                    @Override
                    public void onPositive(MaterialDialog dialog) {
                        super.onPositive(dialog);
                        GlobalConfig.deleteSearchHistory(historyList.get(postion));
                        refreshHistoryList();
                    }
                })
                .theme(Theme.LIGHT)
                .dividerColorRes(R.color.dlgDividerColor)
                .titleColorRes(R.color.dlgTitleColor)
                .contentColorRes(R.color.dlgContentColor)
                .positiveColorRes(R.color.dlgPositiveButtonColor)
                .negativeColorRes(R.color.dlgNegativeButtonColor)
                .title(R.string.dialog_title_delete_one_search_record)
                .content(getResources().getString(R.string.dialog_content_delete_one_search_record) + "\n" + historyList.get(postion))
                .positiveText(R.string.dialog_positive_likethis)
                .negativeText(R.string.dialog_negative_pass)
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
