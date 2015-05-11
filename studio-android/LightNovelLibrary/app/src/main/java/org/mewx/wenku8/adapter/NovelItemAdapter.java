package org.mewx.wenku8.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.nostra13.universalimageloader.core.ImageLoader;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.api.NovelItemInfo;
import org.mewx.wenku8.global.api.Wenku8API;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/1/20.
 */
public class NovelItemAdapter extends RecyclerView.Adapter<NovelItemAdapter.ViewHolder> {

    private List<NovelItemInfo> mDataset;

    // empty list, then use append method to add list elements
    public NovelItemAdapter() {
        mDataset = new ArrayList<NovelItemInfo>();

        return;
    }

    public NovelItemAdapter(List<NovelItemInfo> dataset) {
        super();
        mDataset = dataset;
    }

    public void RefreshDataset(List<NovelItemInfo> dataset) {
        int origSize = mDataset.size();

        mDataset = dataset; // reference
        int currSize = mDataset.size();

        return;
    }


    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = View.inflate(viewGroup.getContext(), R.layout.view_novel_item, null);

        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, int i) {

        // set text
        if(viewHolder.tvNovelTitle != null)
            viewHolder.tvNovelTitle.setText(mDataset.get(i).getTitle());
        if(viewHolder.tvNovelAuthor != null)
            viewHolder.tvNovelAuthor.setText(mDataset.get(i).getAuthor());
        if(viewHolder.tvNovelStatus != null)
            viewHolder.tvNovelStatus.setText(Wenku8API.getStatusBySTATUS(Wenku8API.getSTATUSByInt(mDataset.get(i).getStatus())));
        if(viewHolder.tvNovelUpdate != null)
            viewHolder.tvNovelUpdate.setText(mDataset.get(i).getUpdate());
        if(viewHolder.tvNovelIntro != null)
            viewHolder.tvNovelIntro.setText(mDataset.get(i).getIntroShort());

        if(viewHolder.ivNovelCover != null)
            ImageLoader.getInstance().displayImage(Wenku8API.getCoverURL(mDataset.get(i).getAid()), viewHolder.ivNovelCover);


    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }

    /**
     * View Holder:
     * Called by RecyclerView to display the data at the specified position.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {

        public int position;
        public boolean isLoading = false;

        //public View loadingLayout;
        public ImageView ivNovelCover;
        public TextView tvNovelTitle;
        public TextView tvNovelStatus;
        public TextView tvNovelAuthor;
        public TextView tvNovelUpdate;
        public TextView tvNovelIntro;

        public ViewHolder(View itemView) {
            super(itemView);

            // get all views
            //loadingLayout = (View) itemView.findViewById(R.id.novel_loading);
            ivNovelCover = (ImageView) itemView.findViewById(R.id.novel_cover);
            tvNovelTitle = (TextView) itemView.findViewById(R.id.novel_title);
            tvNovelAuthor = (TextView) itemView.findViewById(R.id.novel_author);
            tvNovelStatus = (TextView) itemView.findViewById(R.id.novel_status);
            tvNovelUpdate = (TextView) itemView.findViewById(R.id.novel_update);
            tvNovelIntro = (TextView) itemView.findViewById(R.id.novel_intro);

            return;
        }
    }

}