package org.mewx.wenku8.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
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

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by MewX on 2015/1/20.
 */
public class NovelItemAdapterUpdate extends RecyclerView.Adapter<NovelItemAdapterUpdate.ViewHolder> {

    private List<NovelItemInfoUpdate> mDataset;

    // empty list, then use append method to add list elements
    public NovelItemAdapterUpdate() {
        mDataset = new ArrayList<NovelItemInfoUpdate>();

        return;
    }

    public NovelItemAdapterUpdate(List<NovelItemInfoUpdate> dataset) {
        super();
        mDataset = null;
        mDataset = dataset;
    }

    public void RefreshDataset(List<NovelItemInfoUpdate> dataset) {
        int origSize = mDataset.size();

        if(dataset.size()>mDataset.size())
            mDataset.addAll(dataset.subList(origSize,dataset.size()));
        //mDataset = dataset; // reference
        //int currSize = mDataset.size();

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
                            NovelItemInfoUpdate nuui = new NovelItemInfoUpdate(tempAid);
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
                            GlobalConfig.wantDebugLog("VolleyResponse.Error", error.toString());
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

            if(postRequest==null)
                GlobalConfig.wantDebugLog("MewX", "NovelItemAdapterUpdate:onBindViewHolder: postRequest==null");
            GlobalConfig.volleyRequestQueue.add(postRequest); // meet errors
        }

        refreshAllContent(viewHolder, i);


    }

    private void refreshAllContent( final ViewHolder viewHolder, int i ) {

        // set text
        viewHolder.tvNovelTitle.setText(mDataset.get(i).title);
        viewHolder.tvNovelAuthor.setText(mDataset.get(i).author);
        viewHolder.tvNovelStatus.setText(mDataset.get(i).status);
        viewHolder.tvNovelUpdate.setText(mDataset.get(i).update);
        viewHolder.tvNovelIntro.setText(mDataset.get(i).intro_short);

        ImageLoader.getInstance().displayImage(Wenku8API.getCoverURL(mDataset.get(i).aid), viewHolder.ivNovelCover);
    }

    public List<NovelItemInfoUpdate> getDataset() {
        return mDataset;
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