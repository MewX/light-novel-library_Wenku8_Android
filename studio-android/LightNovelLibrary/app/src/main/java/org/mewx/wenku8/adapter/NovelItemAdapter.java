package org.mewx.wenku8.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.listener.MyOptionClickListener;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.util.LightCache;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/1/20.
 * Olde Novel Item Adapter.
 */
public class NovelItemAdapter extends RecyclerView.Adapter<NovelItemAdapter.ViewHolder> {

    private MyItemClickListener mItemClickListener;
    private MyOptionClickListener mMyOptionClickListener;
    private MyItemLongClickListener mItemLongClickListener;
    private List<NovelItemInfoUpdate> mDataset;

    // empty list, then use append method to add list elements
    public NovelItemAdapter() {
        mDataset = new ArrayList<>();
    }

    public NovelItemAdapter(List<NovelItemInfoUpdate> dataset) {
        super();
        mDataset = dataset; // reference
    }

    public void RefreshDataset(List<NovelItemInfoUpdate> dataset) {
        mDataset = dataset; // reference
    }


    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int i) {
        View view = View.inflate(viewGroup.getContext(), R.layout.view_novel_item, null);
        return new ViewHolder(view, mItemClickListener, mMyOptionClickListener, mItemLongClickListener);
    }

    @Override
    public void onBindViewHolder(@NonNull final ViewHolder viewHolder, int i) {

        // set text
        if(viewHolder.tvNovelTitle != null)
            viewHolder.tvNovelTitle.setText(mDataset.get(i).title);
        if(viewHolder.tvNovelAuthor != null)
            viewHolder.tvNovelAuthor.setText(mDataset.get(i).author);
        if(viewHolder.tvNovelStatus != null)
            viewHolder.tvNovelStatus.setText(mDataset.get(i).status);
        if(viewHolder.tvNovelUpdate != null)
            viewHolder.tvNovelUpdate.setText(mDataset.get(i).update);
        if(viewHolder.tvNovelIntro != null)
            viewHolder.tvNovelIntro.setText(mDataset.get(i).intro_short);

        // need to solve flicking problem
        if(LightCache.testFileExist(GlobalConfig.getFirstStoragePath() + "imgs" + File.separator + mDataset.get(i).aid + ".jpg"))
            ImageLoader.getInstance().displayImage("file://" + GlobalConfig.getFirstStoragePath() + "imgs" + File.separator + mDataset.get(i).aid + ".jpg", viewHolder.ivNovelCover);
        else if(LightCache.testFileExist(GlobalConfig.getSecondStoragePath() + "imgs" + File.separator + mDataset.get(i).aid + ".jpg"))
            ImageLoader.getInstance().displayImage("file://" + GlobalConfig.getSecondStoragePath() + "imgs" + File.separator + mDataset.get(i).aid + ".jpg", viewHolder.ivNovelCover);
        else
            ImageLoader.getInstance().displayImage(Wenku8API.getCoverURL(mDataset.get(i).aid), viewHolder.ivNovelCover);

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

        private ImageButton ibNovelOption;
        ImageView ivNovelCover;
        TextView tvNovelTitle;
        TextView tvNovelStatus;
        TextView tvNovelAuthor;
        TextView tvNovelUpdate;
        TextView tvNovelIntro;

        ViewHolder(View itemView, MyItemClickListener clickListener, MyOptionClickListener myOptionClickListener, MyItemLongClickListener longClickListener) {
            super(itemView);
            this.mClickListener = clickListener;
            this.mMyOptionClickListener = myOptionClickListener;
            this.mLongClickListener = longClickListener;
            itemView.findViewById(R.id.item_card).setOnClickListener(this);
            itemView.findViewById(R.id.item_card).setOnLongClickListener(this);
            itemView.findViewById(R.id.novel_option).setOnClickListener(this);

            // get all views
            ibNovelOption = itemView.findViewById(R.id.novel_option);
            ivNovelCover = itemView.findViewById(R.id.novel_cover);
            tvNovelTitle = itemView.findViewById(R.id.novel_title);
            tvNovelAuthor = itemView.findViewById(R.id.novel_author);
            tvNovelStatus = itemView.findViewById(R.id.novel_status);
            tvNovelUpdate = itemView.findViewById(R.id.novel_update);
            tvNovelIntro = itemView.findViewById(R.id.novel_intro);

            // test current fragment
            if(!GlobalConfig.testInBookshelf()) {
                ibNovelOption.setVisibility(View.INVISIBLE);
            }
            if(GlobalConfig.testInLatest()) {
                itemView.findViewById(R.id.novel_status_row).setVisibility(View.GONE);
                ((TextView)itemView.findViewById(R.id.novel_item_text_author)).setText(MyApp.getContext().getResources().getString(R.string.novel_item_hit_with_colon));
                ((TextView)itemView.findViewById(R.id.novel_item_text_update)).setText(MyApp.getContext().getResources().getString(R.string.novel_item_push_with_colon));
                ((TextView)itemView.findViewById(R.id.novel_item_text_shortinfo)).setText(MyApp.getContext().getResources().getString(R.string.novel_item_fav_with_colon));
            }
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