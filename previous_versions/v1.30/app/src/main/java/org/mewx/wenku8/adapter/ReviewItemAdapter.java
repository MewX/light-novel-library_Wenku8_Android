package org.mewx.wenku8.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.api.ReviewList;
import org.mewx.wenku8.listener.MyItemClickListener;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by MewX on 2018/7/12.
 * Review List Item Adapter.
 */
public class ReviewItemAdapter extends RecyclerView.Adapter<ReviewItemAdapter.ViewHolder> {

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    private MyItemClickListener mItemClickListener;
    private ReviewList reviewList;

    public ReviewItemAdapter(ReviewList reviewList) {
        this.reviewList = reviewList;
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.view_review_post_item,viewGroup,false);
        return new ViewHolder(view, mItemClickListener);
    }

    public void setOnItemClickListener(MyItemClickListener listener){
        this.mItemClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        ReviewList.Review review = reviewList.getList().get(position);
        if (viewHolder.tvReviewTitle != null)
            viewHolder.tvReviewTitle.setText(review.getTitle());
        if (viewHolder.tvPostTime != null)
            viewHolder.tvPostTime.setText(DATE_FORMATTER.format(review.getPostTime()));
        if (viewHolder.tvReviewAuthor != null)
            viewHolder.tvReviewAuthor.setText(review.getUserName());
        if (viewHolder.tvNumberOfReplies != null)
            viewHolder.tvNumberOfReplies.setText(String.format(Locale.CHINA, "%d", review.getNoReplies()));
    }

    @Override
    public int getItemCount() {
        return reviewList.getList().size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private MyItemClickListener mClickListener;
        TextView tvReviewTitle, tvPostTime, tvReviewAuthor, tvNumberOfReplies;

        public ViewHolder(View view, MyItemClickListener clickListener){
            super(view);
            this.mClickListener = clickListener;
            view.findViewById(R.id.item_card).setOnClickListener(this);

            // real content
            tvReviewTitle = view.findViewById(R.id.review_title);
            tvPostTime = view.findViewById(R.id.review_item_post_time);
            tvReviewAuthor = view.findViewById(R.id.review_item_author);
            tvNumberOfReplies = view.findViewById(R.id.review_item_number_of_posts);
        }

        @Override
        public void onClick(View v) {
            if (mClickListener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                mClickListener.onItemClick(v, getAdapterPosition());
            }
        }
    }
}
