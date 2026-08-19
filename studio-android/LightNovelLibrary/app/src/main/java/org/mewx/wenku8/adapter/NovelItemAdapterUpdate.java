package org.mewx.wenku8.adapter;

import android.annotation.SuppressLint;
import android.os.AsyncTask;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.api.Wenku8Error;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.listener.MyOptionClickListener;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.network.LightNetwork;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.util.CrashReporter;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by MewX on 2015/1/20.
 * Updated version of Novel Item Adapter.
 */
public class NovelItemAdapterUpdate extends RecyclerView.Adapter<NovelItemAdapterUpdate.ViewHolder> {

    private MyItemClickListener mItemClickListener;
    private MyOptionClickListener mMyOptionClickListener;
    private MyItemLongClickListener mItemLongClickListener;
    private List<NovelItemInfoUpdate> mDataset;
    private Set<Integer> loadingAids = Collections.synchronizedSet(new HashSet<>());

    // empty list, then use append method to add list elements
    public NovelItemAdapterUpdate() {
        mDataset = new ArrayList<>();
    }

    public NovelItemAdapterUpdate(List<NovelItemInfoUpdate> dataset) {
        super();
        mDataset = dataset;
    }

    public void refreshDataset(List<NovelItemInfoUpdate> dataset) {
        mDataset = dataset;
    }


    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = View.inflate(viewGroup.getContext(), R.layout.view_novel_item, null);
        return new ViewHolder(view, mItemClickListener, mMyOptionClickListener, mItemLongClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder viewHolder, int i) {
        // Check cache first
        NovelItemInfoUpdate cached = NovelItemInfoUpdate.getFromCache(mDataset.get(i).aid);
        if (cached != null) {
            if (GlobalConfig.testInBookshelf() && NovelItemInfoUpdate.LOADING_STRING.equals(cached.latest_chapter) && !NovelItemInfoUpdate.LOADING_STRING.equals(mDataset.get(i).latest_chapter)) {
                cached.latest_chapter = mDataset.get(i).latest_chapter;
            }
            mDataset.set(i, cached);
            refreshAllFields(viewHolder, cached);
        } else {
            // ALWAYS refresh all fields even if it's "Loading..." to avoid ghosting from recycled views.
            refreshAllFields(viewHolder, mDataset.get(i));

            // Check if we need to load current item.
            checkAndLoad(mDataset.get(i).aid, i);
        }

        // Prefetch next 10 items.
        for (int k = 1; k <= 10; k++) {
            if (i + k < mDataset.size()) {
                checkAndLoad(mDataset.get(i + k).aid, i + k);
            }
        }
    }

    private void checkAndLoad(int aid, int position) {
        if (mDataset.get(position).isInitialized() && !loadingAids.contains(aid) && NovelItemInfoUpdate.getFromCache(aid) == null) {
            new AsyncLoadNovelIntro(aid).executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        }
    }

    private void refreshAllFields(final ViewHolder viewHolder, NovelItemInfoUpdate info) {
        // unknown NPE, just make
        if (viewHolder == null || mDataset == null || info == null)
            return;

        // set text
        viewHolder.tvNovelTitle.setText(info.title);
        viewHolder.tvNovelAuthor.setText(info.author);
        viewHolder.tvNovelStatus.setText(info.status);
        viewHolder.tvNovelUpdate.setText(info.update);
        if(!GlobalConfig.testInBookshelf())
            // show short intro
            viewHolder.tvNovelIntro.setText(info.intro_short);
        else if (info.latest_chapter.isEmpty()) {
            // latest chapter not set, hide it
            viewHolder.tvNovelIntro.setVisibility(View.GONE);
        } else {
            // latest chapter is set, show it
            viewHolder.tvLatestChapterNameText.setText(viewHolder.tvLatestChapterNameText.getResources().getText(R.string.novel_item_latest_chapter));
            viewHolder.tvNovelIntro.setText(info.latest_chapter);
        }

        // FIXME: these imgs folders are actually no in use.
        if (LightCache.testFileExist(GlobalConfig.getDefaultStoragePath() + "imgs" + File.separator + info.aid + ".jpg"))
            ImageLoader.getInstance().displayImage("file://" + GlobalConfig.getDefaultStoragePath() + "imgs" + File.separator + info.aid + ".jpg", viewHolder.ivNovelCover);
        else if (LightCache.testFileExist(GlobalConfig.getBackupStoragePath() + "imgs" + File.separator + info.aid + ".jpg"))
            ImageLoader.getInstance().displayImage("file://" + GlobalConfig.getBackupStoragePath() + "imgs" + File.separator + info.aid + ".jpg", viewHolder.ivNovelCover);
        else
            ImageLoader.getInstance().displayImage(Wenku8API.getCoverURL(info.aid), viewHolder.ivNovelCover);
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }


    public void setOnItemClickListener(MyItemClickListener listener){
        this.mItemClickListener = listener;
    }

    public void setOnDeleteClickListener(MyOptionClickListener listener) {
        this.mMyOptionClickListener = listener;
    }

    public void setOnItemLongClickListener(MyItemLongClickListener listener){
        this.mItemLongClickListener = listener;
    }


    /**
     * View Holder:
     * Called by RecyclerView to display the data at the specified position.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        private MyItemClickListener mClickListener;
        private MyOptionClickListener mMyOptionClickListener;
        private MyItemLongClickListener mLongClickListener;

        private ImageButton ibNovelOption;
        private TableRow trNovelIntro;
        public ImageView ivNovelCover;
        public TextView tvNovelTitle;
        public TextView tvNovelStatus;
        public TextView tvNovelAuthor;
        public TextView tvNovelUpdate;
        public TextView tvNovelIntro;
        TextView tvLatestChapterNameText;

        public ViewHolder(View itemView, MyItemClickListener clickListener, MyOptionClickListener myOptionClickListener, MyItemLongClickListener longClickListener) {
            super(itemView);
            this.mClickListener = clickListener;
            this.mMyOptionClickListener = myOptionClickListener;
            this.mLongClickListener = longClickListener;
            itemView.findViewById(R.id.item_card).setOnClickListener(this);
            itemView.findViewById(R.id.item_card).setOnLongClickListener(this);
            itemView.findViewById(R.id.novel_option).setOnClickListener(this);

            // get all views
            ibNovelOption = itemView.findViewById(R.id.novel_option);
            trNovelIntro = itemView.findViewById(R.id.novel_intro_row);
            ivNovelCover = itemView.findViewById(R.id.novel_cover);
            tvNovelTitle = itemView.findViewById(R.id.novel_title);
            tvNovelAuthor = itemView.findViewById(R.id.novel_author);
            tvNovelStatus = itemView.findViewById(R.id.novel_status);
            tvNovelUpdate = itemView.findViewById(R.id.novel_update);
            tvNovelIntro = itemView.findViewById(R.id.novel_intro);
            tvLatestChapterNameText = itemView.findViewById(R.id.novel_item_text_shortinfo);

            // test current fragment
            if(!GlobalConfig.testInBookshelf())
                ibNovelOption.setVisibility(View.INVISIBLE);
        }

        @Override
        public void onClick(View v) {
            switch (v.getId()){
                case R.id.item_card:
                    if(mClickListener != null){
                        mClickListener.onItemClick(v,getAdapterPosition());
                    }
                    break;
                case R.id.novel_option:
                    if(mClickListener != null){
                        mMyOptionClickListener.onOptionButtonClick(v, getAdapterPosition());
                    }
                    break;
            }
        }

        @Override
        public boolean onLongClick(View v) {
            if(mLongClickListener != null){
                mLongClickListener.onItemLongClick(v, getAdapterPosition());
            }
            return true;
        }
    }


    @SuppressLint("StaticFieldLeak")
    private class AsyncLoadNovelIntro extends AsyncTask<Void, Void, Wenku8Error.ErrorCode> {
        private final int aid;
        private String novelIntro;

        AsyncLoadNovelIntro(int aid) {
            this.aid = aid;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingAids.add(aid); // Mark as loading
        }

        @Override
        protected Wenku8Error.ErrorCode doInBackground(Void... params) {
            try {
                byte[] res = LightNetwork.LightHttpPostConnection(Wenku8API.BASE_URL,
                        Wenku8API.getNovelShortInfoUpdate_CV(aid, GlobalConfig.getCurrentLang()));
                if (res == null) {
                    return Wenku8Error.ErrorCode.ERROR_DEFAULT;
                }

                novelIntro = new String(res, "UTF-8");
                return Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED;
            } catch (UnsupportedEncodingException e) {
                CrashReporter.recordException("NovelItemAdapterUpdate.doInBackground", e);
                return Wenku8Error.ErrorCode.ERROR_DEFAULT;
            }
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode errorCode) {
            super.onPostExecute(errorCode);
            loadingAids.remove(aid); // Mark as finished

            if(errorCode == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                // The index might have been changed. We need to find the correct index again.
                int currentIndex = -1;
                for (int j = 0; j < mDataset.size(); j ++) {
                    if (mDataset.get(j).aid == aid) {
                        currentIndex = j;
                        break;
                    }
                }

                // Update info, but we need to validate the index first.
                if (currentIndex >= 0) {
                    NovelItemInfoUpdate info = NovelItemInfoUpdate.parse(novelIntro);
                    if (info != null) {
                       NovelItemInfoUpdate.putToCache(info); // Cache the result!
                       mDataset.set(currentIndex, info);
                       notifyItemChanged(currentIndex);
                    }
                }
            }
        }
    }

}
