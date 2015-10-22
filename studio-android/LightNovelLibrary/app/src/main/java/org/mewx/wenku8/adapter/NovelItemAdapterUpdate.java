package org.mewx.wenku8.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.listener.MyDeleteClickListener;
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
    private MyDeleteClickListener mMyDeleteClickListener;
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
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = View.inflate(viewGroup.getContext(), R.layout.view_novel_item, null);
        return new ViewHolder(view, mItemClickListener, mMyDeleteClickListener, mItemLongClickListener);
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, int i) {

        // judge if empty
        if(Integer.toString(mDataset.get(i).aid).equals(mDataset.get(i).title) && !viewHolder.isLoading) {

            // this is empty viewholder
            viewHolder.isLoading = true;
            final int tempAid = i;

            StringRequest postRequest = new StringRequest(Request.Method.POST, Wenku8API.getBaseURL(),
                    new Response.Listener<String>()
                    {
                        @Override
                        public void onResponse(String response) {
                            // response
                            try {
                                response = new String(response.getBytes(), "UTF-8");
                            } catch (UnsupportedEncodingException e) {
                                e.printStackTrace();
                            }
                            //GlobalConfig.wantDebugLog("VolleyResponse", response);

                            // update info
                            //NovelItemInfoUpdate nuui = new NovelItemInfoUpdate(tempAid);
                            mDataset.set(tempAid,NovelItemInfoUpdate.parse(response));
                            refreshAllContent(viewHolder, tempAid);
                            viewHolder.isLoading = false;
                        }
                    },
                    new Response.ErrorListener()
                    {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            // error
                            viewHolder.isLoading = false;
                        }
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
            viewHolder.tvNovelIntro.setText(mDataset.get(i).intro_short);

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

    public void setOnDeleteClickListener(MyDeleteClickListener listener) {
        this.mMyDeleteClickListener = listener;
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
        private MyDeleteClickListener mMyDeleteClickListener;
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

        public ViewHolder(View itemView, MyItemClickListener clickListener, MyDeleteClickListener myDeleteClickListener, MyItemLongClickListener longClickListener) {
            super(itemView);
            this.mClickListener = clickListener;
            this.mMyDeleteClickListener = myDeleteClickListener;
            this.mLongClickListener = longClickListener;
            itemView.findViewById(R.id.item_card).setOnClickListener(this);
            itemView.findViewById(R.id.item_card).setOnLongClickListener(this);
            itemView.findViewById(R.id.novel_option).setOnClickListener(this);

            // get all views
            ibNovelOption = (ImageButton) itemView.findViewById(R.id.novel_option);
            trNovelIntro = (TableRow) itemView.findViewById(R.id.novel_intro_row);
            ivNovelCover = (ImageView) itemView.findViewById(R.id.novel_cover);
            tvNovelTitle = (TextView) itemView.findViewById(R.id.novel_title);
            tvNovelAuthor = (TextView) itemView.findViewById(R.id.novel_author);
            tvNovelStatus = (TextView) itemView.findViewById(R.id.novel_status);
            tvNovelUpdate = (TextView) itemView.findViewById(R.id.novel_update);
            tvNovelIntro = (TextView) itemView.findViewById(R.id.novel_intro);

            // test current fragment
            if(!GlobalConfig.testInBookshelf())
                ibNovelOption.setVisibility(View.INVISIBLE);
            else
                trNovelIntro.setVisibility(View.GONE);
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
                        mMyDeleteClickListener.onDeleteClick(v, getAdapterPosition());
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