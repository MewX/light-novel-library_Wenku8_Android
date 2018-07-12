package org.mewx.wenku8.adapter;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.toolbox.StringRequest;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.listener.MyOptionClickListener;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.util.LightCache;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by MewX on 2015/1/20.
 * Updated version of Novel Item Adapter.
 */
public class NovelItemAdapterUpdate extends RecyclerView.Adapter<NovelItemAdapterUpdate.ViewHolder> {

    private MyItemClickListener mItemClickListener;
    private MyOptionClickListener mMyOptionClickListener;
    private MyItemLongClickListener mItemLongClickListener;
    private List<NovelItemInfoUpdate> mDataset;

    // empty list, then use append method to add list elements
    public NovelItemAdapterUpdate() {
        mDataset = new ArrayList<>();
    }

    public NovelItemAdapterUpdate(List<NovelItemInfoUpdate> dataset) {
        super();
        mDataset = null;
        mDataset = dataset;
    }

    public void RefreshDataset(List<NovelItemInfoUpdate> dataset) {
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

        // judge if empty
        if(Integer.toString(mDataset.get(i).aid).equals(mDataset.get(i).title) && !viewHolder.isLoading) {

            // this is empty viewholder
            viewHolder.isLoading = true;
            final int tempAid = i;

            StringRequest postRequest = new StringRequest(Request.Method.POST, Wenku8API.BASE_URL,
                    response -> {
                        // response
                        try {
                            response = new String(response.getBytes(), "UTF-8");
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }

                        // update info
                        mDataset.set(tempAid,NovelItemInfoUpdate.parse(response));
                        refreshAllContent(viewHolder, tempAid);
                        viewHolder.isLoading = false;
                    },
                    error -> {
                        // error
                        viewHolder.isLoading = false;
                    }
            ) {
                @Override
                protected Map<String, String> getParams()
                {
                    return Wenku8API.getNovelShortInfoUpdate(mDataset.get(tempAid).aid, GlobalConfig.getCurrentLang());
                }
            };

            if(GlobalConfig.volleyRequestQueue != null)
                GlobalConfig.volleyRequestQueue.add(postRequest); // meet errors
        }

        refreshAllContent(viewHolder, i);
    }

    private void refreshAllContent( final ViewHolder viewHolder, int i ) {
        // unknown NPE, just make
        if(viewHolder == null || mDataset == null || mDataset.get(i) == null)
            return;

        // set text
        viewHolder.tvNovelTitle.setText(mDataset.get(i).title);
        viewHolder.tvNovelAuthor.setText(mDataset.get(i).author);
        viewHolder.tvNovelStatus.setText(mDataset.get(i).status);
        viewHolder.tvNovelUpdate.setText(mDataset.get(i).update);
        if(!GlobalConfig.testInBookshelf())
            // show short intro
            viewHolder.tvNovelIntro.setText(mDataset.get(i).intro_short);
        else if (mDataset.get(i).latest_chapter.isEmpty()){
            // latest chapter not set, hide it
            viewHolder.tvNovelIntro.setVisibility(View.GONE);
        } else {
            // latest chapter is set, show it
            viewHolder.tvLatestChapterNameText.setText(viewHolder.tvLatestChapterNameText.getResources().getText(R.string.novel_item_latest_chapter));
            viewHolder.tvNovelIntro.setText(mDataset.get(i).latest_chapter);
        }

        if(LightCache.testFileExist(GlobalConfig.getFirstStoragePath() + "imgs" + File.separator + mDataset.get(i).aid + ".jpg"))
            ImageLoader.getInstance().displayImage("file://" + GlobalConfig.getFirstStoragePath() + "imgs" + File.separator + mDataset.get(i).aid + ".jpg", viewHolder.ivNovelCover);
        else if(LightCache.testFileExist(GlobalConfig.getSecondStoragePath() + "imgs" + File.separator + mDataset.get(i).aid + ".jpg"))
            ImageLoader.getInstance().displayImage("file://" + GlobalConfig.getSecondStoragePath() + "imgs" + File.separator + mDataset.get(i).aid + ".jpg", viewHolder.ivNovelCover);
        else
            ImageLoader.getInstance().displayImage(Wenku8API.getCoverURL(mDataset.get(i).aid), viewHolder.ivNovelCover);
    }

    public List<NovelItemInfoUpdate> getDataset() {
        // reference
        return mDataset;
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
        public int position;
        public boolean isLoading = false;

        //public View loadingLayout;
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

}
