package org.mewx.wenku8.adapter;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mewx.wenku8.R;
import org.mewx.wenku8.global.api.ReviewReplyList;
import org.mewx.wenku8.listener.MyItemLongClickListener;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * Created by MewX on 2018/7/12.
 * Review List Item Adapter.
 */
public class ReviewReplyItemAdapter extends RecyclerView.Adapter<ReviewReplyItemAdapter.ViewHolder> {

    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA);

    private MyItemLongClickListener mItemLongClickListener;
    private ReviewReplyList reviewReplyList;

    public ReviewReplyItemAdapter(ReviewReplyList reviewReplyList) {
        this.reviewReplyList = reviewReplyList;
    }

    @Override
    @NonNull
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.view_review_reply_item,viewGroup,false);
        return new ViewHolder(view, mItemLongClickListener);
    }

    public void setOnItemLongClickListener(MyItemLongClickListener listener){
        this.mItemLongClickListener = listener;
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder viewHolder, int position) {
        ReviewReplyList.ReviewReply reviewReply = reviewReplyList.getList().get(position);
        if (viewHolder.tvUserName != null)
            viewHolder.tvUserName.setText(String.format("[%s]", reviewReply.getUserName()));
        if (viewHolder.tvReplyTime != null)
            viewHolder.tvReplyTime.setText(DATE_FORMATTER.format(reviewReply.getReplyTime()));
        if (viewHolder.tvNumberedId != null)
            viewHolder.tvNumberedId.setText(String.format(Locale.CHINA, "%d", position + 1));
        if (viewHolder.tvContent != null)
            viewHolder.tvContent.setText(reviewReply.getContent());
    }

    @Override
    public int getItemCount() {
        return reviewReplyList.getList().size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnLongClickListener {
        private MyItemLongClickListener mClickListener;
        TextView tvUserName, tvReplyTime, tvNumberedId, tvContent;

        public ViewHolder(View view, MyItemLongClickListener longClickListener){
            super(view);
            this.mClickListener = longClickListener;
            view.findViewById(R.id.review_reply_item).setOnLongClickListener(this);

            // real content
            tvUserName = view.findViewById(R.id.review_reply_item_user);
            tvReplyTime = view.findViewById(R.id.review_reply_item_time);
            tvNumberedId = view.findViewById(R.id.review_reply_item_numbered_id);
            tvContent = view.findViewById(R.id.review_reply_content);
        }

        @Override
        public boolean onLongClick(View v) {
            if(mClickListener != null){
                mClickListener.onItemLongClick(v,getAdapterPosition());
            }
            return true;
        }
    }
}
