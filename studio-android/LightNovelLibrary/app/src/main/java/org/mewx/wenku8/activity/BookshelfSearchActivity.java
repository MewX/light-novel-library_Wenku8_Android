package org.mewx.wenku8.activity;

import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.mewx.wenku8.R;
import org.mewx.wenku8.adapter.NovelItemAdapterUpdate;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.ScreenState;
import org.mewx.wenku8.global.api.BookshelfFilter;
import org.mewx.wenku8.global.api.NovelDownloader;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.NovelItemMeta;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.util.GoogleServicesHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Searches the novels already on the device.
 *
 * <p>A separate screen rather than a filter inside the bookshelf, which buys two things. It reads
 * the shelf straight out of {@link GlobalConfig} and its cached metadata, so it shares no state
 * with {@code FavFragment} and cannot desynchronise from it. And because it only navigates, it
 * carries none of the bookshelf's per-row actions -- where a stale position would delete the wrong
 * novel from the account, which is not something a sync can undo.
 *
 * <p>Opening a result behaves exactly as opening the same row on the bookshelf does: the novel
 * moves to the top of the shelf, and {@code NovelInfoActivity} is entered on the local path rather
 * than the cloud one.
 */
public class BookshelfSearchActivity extends BaseMaterialActivity
        implements MyItemClickListener, MyItemLongClickListener {

    private EditText searchField;
    private TextView emptyView;
    private NovelItemAdapterUpdate adapter;

    /** Every novel on the device, rebuilt on entry. */
    private final List<NovelItemInfoUpdate> shelf = new ArrayList<>();

    /** What the list is showing, and therefore what a tapped position refers to. */
    private List<NovelItemInfoUpdate> shown = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        initMaterialStyle(R.layout.layout_bookshelf_search, StatusBarColor.WHITE);

        GoogleServicesHelper.initFirebase(this);

        searchField = findViewById(R.id.search_view);
        // The toolbar is shared with the site-wide search, which hints at searching novels in
        // general; here only what is already downloaded can be found.
        searchField.setHint(R.string.bookshelf_search_hint);
        emptyView = findViewById(R.id.bookshelf_search_empty);

        findViewById(R.id.search_clear).setOnClickListener(v -> searchField.setText(""));
        ImageView clearIcon = findViewById(R.id.search_clear_icon);
        clearIcon.setColorFilter(getResources().getColor(R.color.mySearchToggleColor),
                PorterDuff.Mode.SRC_ATOP);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);

        RecyclerView list = findViewById(R.id.bookshelf_search_list);
        list.setHasFixedSize(false);
        list.setItemAnimator(new DefaultItemAnimator());
        list.setLayoutManager(layoutManager);

        adapter = new NovelItemAdapterUpdate(shown);
        adapter.setOnItemClickListener(this);
        adapter.setOnItemLongClickListener(this);
        // Navigation only. Offering to delete a novel from a filtered list is how the wrong one
        // gets deleted, and there is nothing here a reader cannot do on the bookshelf itself.
        adapter.setOptionButtonVisible(false);
        list.setAdapter(adapter);

        searchField.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                // A shelf is a few dozen novels, so filtering on each keystroke is cheaper than
                // the machinery that would avoid it.
                applyFilter();
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // The adapter asks this to decide whether a row shows a latest chapter or a synopsis, and
        // shelf rows only have the former. Entering the bookshelf state is what makes these rows
        // render as they do on the bookshelf; the per-row menu stays hidden by its own flag.
        ScreenState.enterBookshelf();

        final Drawable upArrow = getResources().getDrawable(R.drawable.ic_svg_back);
        if (upArrow != null && getSupportActionBar() != null) {
            upArrow.setColorFilter(getResources().getColor(R.color.mySearchToggleColor),
                    PorterDuff.Mode.SRC_ATOP);
            getSupportActionBar().setHomeAsUpIndicator(upArrow);
        }

        // Rebuilt on every entry: opening a novel reorders the shelf, and a sync can change what
        // is on it while this screen is in the background.
        reloadShelf();
        applyFilter();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ScreenState.leaveBookshelf();
    }

    /**
     * Reads the shelf out of the cached metadata each novel was downloaded with.
     *
     * <p>A novel whose cache cannot be read is left out rather than shown as a row with an id
     * where its title belongs. It is not lost: the bookshelf's own Check Updates treats an
     * unreadable cache as stale and refetches it.
     */
    private void reloadShelf() {
        shelf.clear();
        for (Integer aid : GlobalConfig.getLocalBookshelfList()) {
            final String xml = GlobalConfig.loadFullFileFromSaveFolder(
                    NovelDownloader.SUB_FOLDER, NovelDownloader.introFileName(aid));
            if (xml.isEmpty()) {
                continue;
            }
            final NovelItemMeta meta = Wenku8Parser.parseNovelFullMeta(xml);
            if (meta != null) {
                shelf.add(NovelItemInfoUpdate.convertFromMeta(meta));
            }
        }
    }

    private void applyFilter() {
        shown = BookshelfFilter.filter(shelf, searchField.getText().toString());
        adapter.refreshDataset(shown);
        adapter.notifyDataSetChanged();

        // A list filtered down to nothing is indistinguishable from one that failed to load.
        emptyView.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onItemClick(View view, int position) {
        // The tapped novel is taken from the list being shown, never from a parallel one: what is
        // displayed here changes with every keystroke.
        if (position < 0 || position >= shown.size()) {
            return;
        }
        final NovelItemInfoUpdate novel = shown.get(position);

        // Same two things opening a row on the bookshelf does.
        GlobalConfig.moveBookToTheTopOfBookshelf(novel.aid);

        Intent intent = new Intent(this, NovelInfoActivity.class);
        intent.putExtra("aid", novel.aid);
        intent.putExtra("from", "fav"); // the local path, as NovelInfoActivity's FromLocal
        intent.putExtra("title", novel.title);

        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(this,
                Pair.create(view.findViewById(R.id.novel_cover), "novel_cover"),
                Pair.create(view.findViewById(R.id.novel_title), "novel_title"));
        ActivityCompat.startActivity(this, intent, options.toBundle());
    }

    @Override
    public void onItemLongClick(View view, int position) {
        // Deliberately empty, as on the bookshelf.
    }

    /**
     * initMaterialStyle turns the up indicator on, but nothing in the base class acts on it --
     * every screen wires its own, and without this the back arrow is inert.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem menuItem) {
        if (menuItem.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(menuItem);
    }
}
