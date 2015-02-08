package org.mewx.lightnovellibrary.adapter;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.mewx.lightnovellibrary.R;
import org.mewx.lightnovellibrary.global.GlobalConfig;
import org.mewx.lightnovellibrary.global.api.NovelItemInfo;
import org.mewx.lightnovellibrary.global.api.Wenku8API;
import org.mewx.lightnovellibrary.util.LightCache;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

/**
 * Created by MewX on 2015/1/20.
 */
public class NovelItemAdapter extends RecyclerView.Adapter<NovelItemAdapter.ViewHolder> {

    private List<NovelItemInfo> mDataset;

    public NovelItemAdapter(List<NovelItemInfo> dataset) {
        super();
        mDataset = dataset;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        View view = View.inflate(viewGroup.getContext(), R.layout.view_novel_item, null);

        ViewHolder holder = new ViewHolder(view);
        return holder;
    }

    @Override
    public void onBindViewHolder(final ViewHolder viewHolder, int i) {
//        // load on view showing
//        final int i_bak = i;
//        if(!mDataset.get(i).getParseStatus() && !mDataset.get(i).getLoadingStatus()){
//            FinalHttp fhNovelItem = new FinalHttp();
//            fhNovelItem.post(Wenku8API.getBaseURL(), Wenku8API.getNovelItemIntroShort(
//                            mDataset.get(i).getAid(), GlobalConfig.getCurrentLang()),
//                                        new AjaxCallBack<byte[]>() {
//                                            @Override
//                                            public void onStart() {
//                                                mDataset.get(i_bak).setLoadingStatus(true);
//                                            }
//
//                                            @Override
//                                            public void onSuccess(byte[] bytes) {
//                                                String[] sin = new String[1];
//                                                try {
//                                                    sin[0] = new String(bytes, "UTF-8");
//                                                    mDataset.get(i_bak).setNovelItemInfo(sin);
//                                                    if(mDataset.get(i_bak).getParseStatus())
//
//                                                        ((LatestFragment)GlobalConfig.getCurrentFragment())
//                                                                .getNovelItemAdapter().notifyDataSetChanged();
//
//                                                    // release memory
//                                                    sin[0] = null;
//                                                } catch (Exception e) {
////                                                    mTextView.setText(getResources().getString(R.string.system_parse_failed)
////                                                            + e.getMessage());
//                                                    mDataset.get(i_bak).setLoadingStatus(false);
//                                                    return;
//                                                }
//                                            }
//
//                                            @Override
//                                            public void onFailure(Throwable t, int errorNo, String strMsg) {
//                                                mDataset.get(i_bak).setLoadingStatus(false);
//                                            }
//                                        });
//        }

        // set text
        viewHolder.tvNovelTitle.setText(mDataset.get(i).getTitle());
        viewHolder.tvNovelAuthor.setText(mDataset.get(i).getAuthor());
        viewHolder.tvNovelStatus.setText(Wenku8API.getStatusBySTATUS(Wenku8API.getSTATUSByInt(mDataset.get(i).getStatus())));
        viewHolder.tvNovelUpdate.setText(mDataset.get(i).getUpdate());
        viewHolder.tvNovelIntro.setText(mDataset.get(i).getIntroShort());

        // set image cover
        if (LightCache.testFileExist(GlobalConfig.getFirstStoragePath()
                + "imgs" + File.separator + String.valueOf(mDataset.get(i).getAid())
                + ".jpg") == true) {

            Picasso.with(GlobalConfig.getContext()).load(new File(GlobalConfig.getFirstStoragePath()
                    + "imgs" + File.separator + String.valueOf(mDataset.get(i).getAid())
                    + ".jpg")).placeholder(R.drawable.ic_empty_image).into(viewHolder.ivNovelCover);
        } else if (LightCache.testFileExist(GlobalConfig
                .getSecondStoragePath()
                + "imgs"
                + File.separator
                + String.valueOf(mDataset.get(i).getAid()) + ".jpg") == true) {

            Picasso.with(GlobalConfig.getContext()).load(new File(GlobalConfig.getSecondStoragePath()
                    + "imgs" + File.separator + String.valueOf(mDataset.get(i).getAid())
                    + ".jpg")).placeholder(R.drawable.ic_empty_image).into(viewHolder.ivNovelCover);

        } else {
            // load online
            final String fileName = mDataset.get(i).getAid() + ".jpg";
            Target target = new Target() {

                @Override
                public void onPrepareLoad(Drawable arg0) {
                    return;
                }

                @Override
                public void onBitmapLoaded(Bitmap bitmap, Picasso.LoadedFrom arg1) {

                    try {
                        File file = null;

                        // judge "imgs/.nomedia"'s existance to judge whether path available
                        if (LightCache.testFileExist(GlobalConfig.getFirstStoragePath()
                                + "imgs" + File.separator + ".nomedia") == true)
                            file = new File(GlobalConfig.getFirstStoragePath()
                                    + "imgs" + File.separator + fileName);

                        else file = new File(GlobalConfig.getSecondStoragePath()
                                + "imgs" + File.separator + fileName);

                        if(file==null)return;
                        file.createNewFile();
                        FileOutputStream ostream = new FileOutputStream(file);
                        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, ostream);
                        ostream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onBitmapFailed(Drawable arg0) {
                    return;
                }
            };

            Picasso.with(GlobalConfig.getContext())
                    .load(Wenku8API.getCoverURL(mDataset.get(i).getAid()))
                    .placeholder(R.drawable.ic_empty_image)
                    .into(viewHolder.ivNovelCover);//.into(viewHolder.ivNovelCover);
//        viewHolder.ivNovelCover.setAdjustViewBounds(true);
//        viewHolder.ivNovelCover.setScaleType(ImageView.ScaleType.FIT_CENTER);// CENTER_INSIDE

            // add to file queue

        }

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