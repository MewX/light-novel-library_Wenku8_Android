package org.mewx.wenku8.fragment;

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
import androidx.core.app.ActivityCompat;
import androidx.core.app.ActivityOptionsCompat;
import androidx.core.util.Pair;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.activity.NovelInfoActivity;
import org.mewx.wenku8.adapter.NovelItemAdapterUpdate;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.ScreenState;
import org.mewx.wenku8.global.api.BookshelfListParser;
import org.mewx.wenku8.global.api.BookshelfSync;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.NovelDownloader;
import org.mewx.wenku8.global.api.NovelItemMeta;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.api.Wenku8Error;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.listener.MyOptionClickListener;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.network.LightNetwork;
import org.mewx.wenku8.util.ProgressDialogHelper;
import org.mewx.wenku8.util.LightTool;
import org.mewx.wenku8.network.LightUserSession;
import org.mewx.wenku8.util.CrashReporter;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class FavFragment extends Fragment implements MyItemClickListener, MyItemLongClickListener, MyOptionClickListener {

    // local vars
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private RecyclerView mRecyclerView = null;
    private int timecount;

    // novel list info
    private final List<Integer> listNovelItemAid = new ArrayList<>(); // aid list
    private final List<NovelItemInfoUpdate> listNovelItemInfo = new ArrayList<>(); // info list

    public static FavFragment newInstance() {
        return new FavFragment();
    }

    public FavFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View rootView = inflater.inflate(R.layout.fragment_fav, container, false);

        // find view
        mSwipeRefreshLayout = rootView.findViewById(R.id.swipe_refresh_layout);

        // init values
        timecount = 0;

        // view setting
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        mLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerView = rootView.findViewById(R.id.novel_item_list);
        mRecyclerView.setHasFixedSize(false); // set variable size
        mRecyclerView.setItemAnimator(new DefaultItemAnimator());
        mRecyclerView.setLayoutManager(mLayoutManager);

        mSwipeRefreshLayout.setColorSchemeColors(getResources().getColor(R.color.myAccentColor));
        mSwipeRefreshLayout.setOnRefreshListener(() -> new AsyncLoadAllFromCloud().execute(1));

        return rootView;
    }

    @Override
    public void onItemClick(View view, int position) {
        // go to detail activity
        Intent intent = new Intent(getActivity(), NovelInfoActivity.class);
        intent.putExtra("aid", listNovelItemAid.get(position));
        intent.putExtra("from", "fav");
        intent.putExtra("title", ((TextView) view.findViewById(R.id.novel_title)).getText());
        GlobalConfig.moveBookToTheTopOfBookshelf(listNovelItemAid.get(position)); // sort event

        ActivityOptionsCompat options = ActivityOptionsCompat.makeSceneTransitionAnimation(getActivity(),
                Pair.create(view.findViewById(R.id.novel_cover), "novel_cover"),
                Pair.create(view.findViewById(R.id.novel_title), "novel_title"));
        ActivityCompat.startActivity(getActivity(), intent, options.toBundle());
    }

    @Override
    public void onOptionButtonClick(View view, final int position) {
        new MaterialAlertDialogBuilder(getActivity())
                .setTitle(R.string.dialog_title_choose_delete_option)
                .setNegativeButton(R.string.dialog_negative_pass, null)
                .setItems(R.array.cleanup_option, (dialog, which) -> {
                    /*
                     * 0 <string name="dialog_clear_cache">清除缓存</string>
                     * 1 <string name="dialog_delete_book">删除这本书</string>
                     */
                    switch (which) {
                        case 0:
                            new MaterialAlertDialogBuilder(getActivity())
                                    .setMessage(R.string.dialog_sure_to_clear_cache)
                                    .setPositiveButton(R.string.dialog_positive_sure, (d, w) -> {
                                        int aid = listNovelItemAid.get(position);
                                        String novelFullVolume = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-volume.xml");
                                        if(novelFullVolume.isEmpty()) return;
                                        List<VolumeList> listVolume = Wenku8Parser.getVolumeList(novelFullVolume);
                                        if(listVolume.isEmpty()) return;
                                        cleanVolumesCache(listVolume);
                                    })
                                    .setNegativeButton(R.string.dialog_negative_preferno, null)
                                    .show();
                            break;
                        case 1:
                            new MaterialAlertDialogBuilder(getActivity())
                                    .setMessage(R.string.dialog_content_want_to_delete)
                                    .setPositiveButton(R.string.dialog_positive_sure, (d, w) -> {
                                        // Delete operation: delete from in-memory index and cloud first.
                                        // Then, the async task will remove the deleted book from local bookshelf.
                                        int aid = listNovelItemAid.get(position);
                                        listNovelItemAid.remove(position);
                                        new AsyncRemoveBookFromCloud().execute(aid);
                                        refreshList(timecount ++);
                                    })
                                    .setNegativeButton(R.string.dialog_negative_preferno, null)
                                    .show();
                            break;
                    }
                })
                .show();
    }

    @Override
    public void onItemLongClick(View view, int position) {
    }

    private void cleanVolumesCache(List<VolumeList> listVolume) {
        // remove from local bookshelf, already in bookshelf
        for (VolumeList vl : listVolume) {
            LightCache.cleanLocalCache(vl);
        }
    }

    private void refreshList(int time) {
        if(time == 0) {
            mSwipeRefreshLayout.setRefreshing(true);
            new AsyncLoadAllFromCloud().execute();
        }
        else {
            loadAllLocal();
        }
    }

    private void loadAllLocal() {
        int retValue = 0;
        boolean datasetChanged = false;

        // init
        listNovelItemAid.clear();
        listNovelItemAid.addAll(GlobalConfig.getLocalBookshelfList());

        // load all metadata file
        aids:
        for (int j = 0; j < listNovelItemAid.size(); j++) {
            int aid = listNovelItemAid.get(j);
            // See if it's in the list already. Expecting the list will not be more than 100.
            for (int i = j; i < listNovelItemInfo.size(); i++) {
                final NovelItemInfoUpdate info = listNovelItemInfo.get(i);
                if (info.aid == aid) {
                    // Found but in the same place.
                    if (i == j) continue aids;

                    // Found, not in the same place remove and re-insert.
                    listNovelItemInfo.remove(i);
                    listNovelItemInfo.add(j, info);
                    datasetChanged = true;
                    continue aids;
                }
            }

            // Not found.
            final String xml = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-intro.xml");
            NovelItemInfoUpdate info;
            final NovelItemMeta meta = xml.isEmpty() ? null : Wenku8Parser.parseNovelFullMeta(xml);
            if (meta == null) {
                // The intro file was deleted, or it is present but does not parse into a
                // novel -- a truncated or half-written cache file reads as the latter. Both
                // mean the same thing to the user, and retValue == -1 raises the
                // "sync the novel info again" toast below. This used to be
                // Objects.requireNonNull(), which turned a corrupt cache file into a crash.
                retValue = -1;
                info = new NovelItemInfoUpdate(aid);
            }
            else {
                info = NovelItemInfoUpdate.convertFromMeta(meta);
            }
            datasetChanged = true;
            listNovelItemInfo.add(j, info);
        }
        // Trim everything after aid.size().
        if (listNovelItemInfo.size() > listNovelItemAid.size()) {
            listNovelItemInfo.subList(listNovelItemAid.size(), listNovelItemInfo.size()).clear();
        }

        // result
        if(retValue != 0) {
            Toast.makeText(getActivity(), getResources().getString(R.string.bookshelf_intro_load_failed), Toast.LENGTH_SHORT).show();
        }

        // Reuse the adapter and datasets.
        if (mRecyclerView.getAdapter() == null) {
            NovelItemAdapterUpdate adapter = new NovelItemAdapterUpdate();
            adapter.refreshDataset(listNovelItemInfo);
            adapter.setOnItemClickListener(FavFragment.this);
            adapter.setOnDeleteClickListener(FavFragment.this);
            adapter.setOnItemLongClickListener(FavFragment.this);
            mRecyclerView.setAdapter(adapter);
        }
        if (datasetChanged) {
            mRecyclerView.getAdapter().notifyDataSetChanged();
        }
        mSwipeRefreshLayout.setRefreshing(false);
    }

    private class AsyncLoadAllFromCloud extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        private ProgressDialogHelper md;
        private boolean isLoading; // check in "doInBackground" to make sure to continue or not
        private boolean forceLoad = false;

        /** Novels committed and novels left for a later run; reported once the sync ends. */
        private int succeeded = 0;
        private int failed = 0;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            loadAllLocal();

            isLoading = true;
            md = ProgressDialogHelper.show(getActivity(),
                    getString(R.string.dialog_content_sync),
                    /* indeterminate= */ false, /* cancelable= */ true, /* cancelListener= */
                    dialog -> {
                        isLoading = false;
                        md.dismiss();
                    });
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            // if params.length != 0, force async
            if(params != null && params.length != 0) forceLoad = true;

            // ! any network problem will interrupt this procedure
            // load bookshelf list, don't save
            byte[] b = fetchShelfListing();
            if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;

            if(LightTool.isInteger(new String(b))) {
                if(Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(new String(b))) == Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN) {
                    // do log in
                    Wenku8Error.ErrorCode temp = LightUserSession.doLoginFromFile(GlobalConfig::loadUserInfoSet);
                    if(temp != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) return temp; // return an error code

                    // request again
                    b = fetchShelfListing();
                    if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
                }
            }

            // Read the shelf. A null parse means the body was not a usable listing, and that
            // must never be mistaken for an account with nothing on it -- BookshelfSync.plan
            // reads an empty cloud listing as "the cloud has nothing" and schedules the reader's
            // entire device shelf to be uploaded. So fall back to the ids-only endpoint, which is
            // exactly what this did before, rather than carrying on with an empty list.
            List<BookshelfListParser.Entry> shelf = null;
            try {
                String listing = new String(b, "UTF-8");
                Log.d("MewX", listing);
                shelf = BookshelfListParser.parse(listing);
            } catch (UnsupportedEncodingException e) {
                CrashReporter.recordException("FavFragment.AsyncLoadAllFromCloud", e);
            }

            List<Integer> listResultList;
            if (shelf != null) {
                listResultList = new ArrayList<>(shelf.size());
                for (BookshelfListParser.Entry entry : shelf) {
                    listResultList.add(entry.aid);
                }
            } else {
                listResultList = fetchShelfAidsFromIdsOnlyEndpoint();
                if (listResultList == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
            }

            // calc difference -- see BookshelfSync for why this is not done inline any more, and
            // for the guarantee that a novel held only on the device is pushed up rather than lost.
            BookshelfSync.Plan plan =
                    BookshelfSync.plan(GlobalConfig.getLocalBookshelfList(), listResultList, forceLoad);
            List<Integer> localOnly = plan.localOnly;
            List<Integer> listDiff = plan.toDownload;
            if(plan.isUpToDate()) {
                // equal, so exit
                return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
            }

            // Download everything the device is missing. One pool serves the whole sync and is
            // shut down in a finally -- the per-novel pools this replaces were only shut down on
            // the paths that returned normally, and their threads are not daemons.
            //
            // A novel that fails no longer ends the sync. It is left off the bookshelf, which is
            // both what the user sees as "not synced yet" and what makes the next run fetch it
            // again. See issue #114: one flaky response used to cost every novel after it.
            final Wenku8API.AppLanguage lang = GlobalConfig.getCurrentLang();
            final NovelDownloader.Fetcher fetcher = (document, id) -> {
                switch (document) {
                    case VOLUME_INDEX:
                        return LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL,
                                Wenku8API.getNovelIndex(id, lang));
                    case META:
                        return LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL,
                                Wenku8API.getNovelFullMeta(id, lang));
                    case FULL_INTRO:
                        return LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL,
                                Wenku8API.getNovelFullIntro(id, lang));
                    default:
                        return null;
                }
            };
            final NovelDownloader.Store store = new NovelDownloader.Store() {
                @Override
                public boolean write(@NonNull String subFolder, @NonNull String fileName,
                                     @NonNull String content) {
                    return GlobalConfig.writeFullFileIntoSaveFolder(subFolder, fileName, content);
                }

                @Override
                public void commit(int aid) {
                    GlobalConfig.addToLocalBookshelf(aid);
                }
            };
            final NovelDownloader.Cancellation cancellation = () -> !isLoading;

            int count = 0;
            md.setMaxProgress(listDiff.size());
            final ExecutorService executor = Executors.newFixedThreadPool(3);
            try {
                for (Integer aid : listDiff) {
                    if (!isLoading) return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;

                    NovelDownloader.Outcome outcome = NovelDownloader.syncOne(
                            aid, executor, fetcher, store, cancellation);
                    if (outcome == NovelDownloader.Outcome.CANCELLED) {
                        return Wenku8Error.ErrorCode.USER_CANCELLED_TASK;
                    }
                    if (outcome == NovelDownloader.Outcome.COMMITTED) {
                        succeeded++;
                    } else {
                        failed++;
                    }
                    publishProgress(++count);
                }
            } finally {
                executor.shutdown();
            }

            // sync local bookshelf, and set ribbon, sync one, delete one
            List<Integer> copy = new ArrayList<>(localOnly); // make a copy
            for(Integer aid : copy) {
                b = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getAddToBookshelfParams(aid));
                if(b == null) {
                    // Same rule as the download loop: leave this one in localOnly so it is pushed
                    // again next time, rather than abandoning every novel queued behind it.
                    continue;
                }

                try {
                    if(LightTool.isInteger(new String(b, "UTF-8"))) {
                        Wenku8Error.ErrorCode result = Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(new String(b, "UTF-8")));
                        if(result == Wenku8Error.ErrorCode.SYSTEM_6_BOOKSHELF_FULL) {
                            return result;
                        }
                        else if(result == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED || result == Wenku8Error.ErrorCode.SYSTEM_5_ALREADY_IN_BOOKSHELF) {
                            localOnly.remove(aid); // remove Obj
                        }
                    }
                } catch (UnsupportedEncodingException e) {
                    CrashReporter.recordException("FavFragment.AsyncLoadAllFromCloud.localToCloud", e);
                }
            }

            return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
        }

        /**
         * Fetches the account's shelf.
         *
         * <p>This is {@code action=bookcase} rather than the {@code do=list} sibling the sync used
         * to call. It is the same single request, but each book also carries its last update and
         * its latest chapter -- verified equal to the metadata endpoint's {@code LastUpdate} and
         * {@code LatestSection} cid -- which is what a later change needs to tell which novels
         * went stale without re-downloading all of them.
         *
         * <p>Only the ids are used today. The switch lands on its own so it can be verified by
         * itself, before anything depends on the fields it adds.
         */
        @Nullable
        private byte[] fetchShelfListing() {
            return LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL,
                    Wenku8API.getBookshelfListParams(GlobalConfig.getCurrentLang()));
        }

        /**
         * The ids-only endpoint, kept for when the fuller one cannot be read.
         *
         * @return the ids, or null if the request itself failed -- which the caller must treat as
         * a network error rather than as an empty shelf
         */
        @Nullable
        private List<Integer> fetchShelfAidsFromIdsOnlyEndpoint() {
            byte[] raw = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL,
                    Wenku8API.getBookshelfListAid(GlobalConfig.getCurrentLang()));
            if (raw == null) {
                return null;
            }
            try {
                return BookshelfSync.parseCloudAidList(new String(raw, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                CrashReporter.recordException("FavFragment.fetchShelfAidsFromIdsOnlyEndpoint", e);
                return null;
            }
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            super.onProgressUpdate(values);
            md.setProgress(values[0]);
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);

            // Cleared regardless of Fragment state; the rest of this method is view work.
            isLoading = false;

            // This replaces the try/catch that used to wrap md.dismiss(). That catch was the
            // root cause being suppressed rather than fixed -- "View not attached to window
            // manager" is precisely the detached-Fragment case -- and it also swallowed any
            // genuine failure. The dismiss itself stays ahead of the lifecycle check, since
            // ProgressDialogHelper.dismiss() already handles a gone window and skipping it
            // would leak the dialog; the null check covers the case the catch was really
            // hiding, which is md never having been assigned.
            if (md != null) md.dismiss();

            if (!isAdded() || getActivity() == null) return;

            if(errorCode != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                Toast.makeText(MyApp.getContext(), errorCode.toString(), Toast.LENGTH_SHORT).show();
                refreshList(timecount ++);
            }
            else {
                loadAllLocal();
                if (failed > 0) {
                    // The sync itself succeeded; some novels are simply not on the shelf yet and
                    // will be fetched next time. Saying so beats silently coming up short.
                    Toast.makeText(MyApp.getContext(),
                            getString(R.string.bookshelf_sync_partial, succeeded, succeeded + failed),
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    class AsyncRemoveBookFromCloud extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        ProgressDialogHelper md;
        int aid;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            md = ProgressDialogHelper.show(getActivity(),
                    getString(R.string.dialog_content_novel_remove_from_cloud),
                    /* indeterminate= */ true, /* cancelable= */ false, /* cancelListener= */ null);
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            // params: aid
            aid = params[0];
            byte[] bytes = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL, Wenku8API.getDelFromBookshelfParams(aid));
            if(bytes == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;

            String result;
            try {
                result = new String(bytes, "UTF-8");
                Log.d("MewX", result);
                if (!LightTool.isInteger(result))
                    return Wenku8Error.ErrorCode.RETURNED_VALUE_EXCEPTION;
                if(Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result)) != Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED
                        && Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result)) != Wenku8Error.ErrorCode.SYSTEM_4_NOT_LOGGED_IN
                        && Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result)) != Wenku8Error.ErrorCode.SYSTEM_7_NOVEL_NOT_IN_BOOKSHELF) {
                    return Wenku8Error.getSystemDefinedErrorCode(Integer.parseInt(result));
                }
                else {
                    // load volume first
                    // get novel chapter list
                    List<VolumeList> listVolume;
                    String novelFullVolume;
                    novelFullVolume = GlobalConfig.loadFullFileFromSaveFolder("intro", aid + "-volume.xml");
                    if(novelFullVolume.isEmpty()) return Wenku8Error.ErrorCode.ERROR_DEFAULT;
                    listVolume = Wenku8Parser.getVolumeList(novelFullVolume);
                    if(listVolume.isEmpty()) return Wenku8Error.ErrorCode.XML_PARSE_FAILED;

                    cleanVolumesCache(listVolume);
                    // delete files
                    LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "intro" + File.separator + aid + "-intro.xml");
                    LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "intro" + File.separator + aid + "-introfull.xml");
                    LightCache.deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "intro" + File.separator + aid + "-volume.xml");
                    LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "intro" + File.separator + aid + "-intro.xml");
                    LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "intro" + File.separator + aid + "-introfull.xml");
                    LightCache.deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "intro" + File.separator + aid + "-volume.xml");
                    // remove from bookshelf
                    GlobalConfig.removeFromLocalBookshelf(aid);
                    if (!GlobalConfig.testInLocalBookshelf(aid)) { // not in
                        return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
                    } else {
                        return Wenku8Error.ErrorCode.LOCAL_BOOK_REMOVE_FAILED;
                    }
                }
            } catch (UnsupportedEncodingException e) {
                return Wenku8Error.ErrorCode.BYTE_TO_STRING_EXCEPTION;
            }
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode err) {
            super.onPostExecute(err);

            // See AsyncLoadAllFromCloud above: the suppressing try/catch is replaced by a
            // null check plus a lifecycle check, in that order. The removal itself already
            // happened in doInBackground.
            if (md != null) md.dismiss();

            if (!isAdded() || getActivity() == null) return;

            if (err == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                Toast.makeText(getActivity(), getResources().getString(R.string.bookshelf_removed), Toast.LENGTH_SHORT).show();
                loadAllLocal();
            } else {
                Toast.makeText(getActivity(), err.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        ScreenState.leaveBookshelf();
    }

    @Override
    public void onResume() {
        super.onResume();
        ScreenState.enterBookshelf();

        // refresh list
        refreshList(timecount ++);
    }

}
