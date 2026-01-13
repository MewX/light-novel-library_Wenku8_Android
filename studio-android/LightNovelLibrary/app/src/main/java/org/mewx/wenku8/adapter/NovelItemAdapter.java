package org.mewx.wenku8.adapter;

import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.ads.nativead.NativeAd;
import com.google.android.gms.ads.nativead.NativeAdView;
import com.nostra13.universalimageloader.core.ImageLoader;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.NovelItemInfoUpdate;
import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;
import org.mewx.wenku8.listener.MyOptionClickListener;
import org.mewx.wenku8.util.LightCache;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/1/20.
 * Olde Novel Item Adapter.
 */
public class NovelItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final int VIEW_TYPE_ITEM = 0;
    private static final int VIEW_TYPE_AD = 1;

    private MyItemClickListener mItemClickListener;
    private MyOptionClickListener mMyOptionClickListener;
    private MyItemLongClickListener mItemLongClickListener;
    private List<NovelItemInfoUpdate> mDataset;
    private List<NativeAd> mAdList = new ArrayList<>();

    // empty list, then use append method to add list elements
    public NovelItemAdapter() {
        mDataset = new ArrayList<>();
    }

    public NovelItemAdapter(List<NovelItemInfoUpdate> dataset) {
        super();
        mDataset = dataset; // reference
    }

    public void addAd(NativeAd ad) {
        mAdList.add(ad);
    }

    public void destroyAds() {
        for (NativeAd ad : mAdList) {
            ad.destroy();
        }
        mAdList.clear();
    }

    @Override
    public int getItemViewType(int position) {
        if ((position + 1) % 11 == 0) {
            return VIEW_TYPE_AD;
        }
        return VIEW_TYPE_ITEM;
    }

    @Override
    @NonNull
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        if (viewType == VIEW_TYPE_AD) {
            View view = View.inflate(viewGroup.getContext(), R.layout.view_novel_ad_item, null);
            return new AdViewHolder(view);
        } else {
            View view = View.inflate(viewGroup.getContext(), R.layout.view_novel_item, null);
            return new ViewHolder(view, mItemClickListener, mMyOptionClickListener, mItemLongClickListener);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull final RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_AD) {
            AdViewHolder adViewHolder = (AdViewHolder) holder;
            int adIndex = (position + 1) / 11 - 1;
            if (adIndex < mAdList.size()) {
                adViewHolder.bind(mAdList.get(adIndex));
                adViewHolder.itemView.setVisibility(View.VISIBLE);
                adViewHolder.itemView.setLayoutParams(new RecyclerView.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            } else {
                adViewHolder.itemView.setVisibility(View.GONE);
                adViewHolder.itemView.setLayoutParams(new RecyclerView.LayoutParams(0, 0));
            }
        } else {
            ViewHolder viewHolder = (ViewHolder) holder;
            int realIndex = position - (position + 1) / 11;

            if (realIndex >= mDataset.size()) return;

            // set text
            if(viewHolder.tvNovelTitle != null)
                viewHolder.tvNovelTitle.setText(mDataset.get(realIndex).title);
            if(viewHolder.tvNovelAuthor != null)
                viewHolder.tvNovelAuthor.setText(mDataset.get(realIndex).author);
            if(viewHolder.tvNovelStatus != null)
                viewHolder.tvNovelStatus.setText(mDataset.get(realIndex).status);
            if(viewHolder.tvNovelUpdate != null)
                viewHolder.tvNovelUpdate.setText(mDataset.get(realIndex).update);
            if(viewHolder.tvNovelIntro != null)
                viewHolder.tvNovelIntro.setText(mDataset.get(realIndex).intro_short);

            // need to solve flicking problem
            // FIXME: these imgs folders are actually no in use.
            if(LightCache.testFileExist(GlobalConfig.getDefaultStoragePath() + "imgs" + File.separator + mDataset.get(realIndex).aid + ".jpg"))
                ImageLoader.getInstance().displayImage("file://" + GlobalConfig.getDefaultStoragePath() + "imgs" + File.separator + mDataset.get(realIndex).aid + ".jpg", viewHolder.ivNovelCover);
            else if(LightCache.testFileExist(GlobalConfig.getBackupStoragePath() + "imgs" + File.separator + mDataset.get(realIndex).aid + ".jpg"))
                ImageLoader.getInstance().displayImage("file://" + GlobalConfig.getBackupStoragePath() + "imgs" + File.separator + mDataset.get(realIndex).aid + ".jpg", viewHolder.ivNovelCover);
            else
                ImageLoader.getInstance().displayImage(Wenku8API.getCoverURL(mDataset.get(realIndex).aid), viewHolder.ivNovelCover);
        }
    }

    @Override
    public int getItemCount() {
        if (mDataset.isEmpty()) return 0;
        int count = mDataset.size();
        return count + count / 10;
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
            // Adjust position for click events
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return;

            // map to real index
            int realIndex = position - (position + 1) / 11;

            switch (v.getId()){
                case R.id.item_card:
                    if(mClickListener != null){
                        mClickListener.onItemClick(v,realIndex);
                    }
                    break;
                case R.id.novel_option:
                    if(mClickListener != null){
                        mMyOptionClickListener.onOptionButtonClick(v, realIndex);
                    }
                    break;
            }
        }

        @Override
        public boolean onLongClick(View v) {
            // Adjust position for long click events
            int position = getAdapterPosition();
            if (position == RecyclerView.NO_POSITION) return true;

            // map to real index
            int realIndex = position - (position + 1) / 11;

            if(mLongClickListener != null){
                mLongClickListener.onItemLongClick(v, realIndex);
            }
            return true;
        }
    }

    public static class AdViewHolder extends RecyclerView.ViewHolder {
        public AdViewHolder(View itemView) {
            super(itemView);
        }

        public void bind(NativeAd nativeAd) {
            NativeAdView adView = (NativeAdView) itemView;

            // Set the media view.
            adView.setMediaView(adView.findViewById(R.id.ad_media));

            // Set other ad assets.
            adView.setHeadlineView(adView.findViewById(R.id.ad_headline));
            adView.setBodyView(adView.findViewById(R.id.ad_body));
            adView.setCallToActionView(adView.findViewById(R.id.ad_call_to_action));
            adView.setPriceView(adView.findViewById(R.id.ad_price));
            adView.setStarRatingView(adView.findViewById(R.id.ad_stars));
            adView.setStoreView(adView.findViewById(R.id.ad_store));
            adView.setAdvertiserView(adView.findViewById(R.id.ad_advertiser));

            // The headline and mediaContent are guaranteed to be in every NativeAd.
            ((TextView) adView.getHeadlineView()).setText(nativeAd.getHeadline());
            adView.getMediaView().setMediaContent(nativeAd.getMediaContent());

            // These assets aren't guaranteed to be in every NativeAd, so it's important to
            // check before trying to display them.
            if (nativeAd.getBody() == null) {
                adView.getBodyView().setVisibility(View.INVISIBLE);
            } else {
                adView.getBodyView().setVisibility(View.VISIBLE);
                ((TextView) adView.getBodyView()).setText(nativeAd.getBody());
            }

            if (nativeAd.getCallToAction() == null) {
                adView.getCallToActionView().setVisibility(View.INVISIBLE);
            } else {
                adView.getCallToActionView().setVisibility(View.VISIBLE);
                ((Button) adView.getCallToActionView()).setText(nativeAd.getCallToAction());
            }

            if (nativeAd.getPrice() == null) {
                adView.getPriceView().setVisibility(View.INVISIBLE);
            } else {
                adView.getPriceView().setVisibility(View.VISIBLE);
                ((TextView) adView.getPriceView()).setText(nativeAd.getPrice());
            }

            if (nativeAd.getStore() == null) {
                adView.getStoreView().setVisibility(View.INVISIBLE);
            } else {
                adView.getStoreView().setVisibility(View.VISIBLE);
                ((TextView) adView.getStoreView()).setText(nativeAd.getStore());
            }

            if (nativeAd.getStarRating() == null) {
                adView.getStarRatingView().setVisibility(View.INVISIBLE);
            } else {
                ((RatingBar) adView.getStarRatingView())
                        .setRating(nativeAd.getStarRating().floatValue());
                adView.getStarRatingView().setVisibility(View.VISIBLE);
            }

            if (nativeAd.getAdvertiser() == null) {
                adView.getAdvertiserView().setVisibility(View.INVISIBLE);
            } else {
                ((TextView) adView.getAdvertiserView()).setText(nativeAd.getAdvertiser());
                adView.getAdvertiserView().setVisibility(View.VISIBLE);
            }

            // This method tells the Google Mobile Ads SDK that you have finished populating your
            // native ad view with this native ad.
            adView.setNativeAd(nativeAd);
        }
    }

}