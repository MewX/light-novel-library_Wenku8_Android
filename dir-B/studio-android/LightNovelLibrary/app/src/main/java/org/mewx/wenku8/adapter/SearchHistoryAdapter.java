package org.mewx.wenku8.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.mewx.wenku8.R;
import org.mewx.wenku8.listener.MyItemClickListener;
import org.mewx.wenku8.listener.MyItemLongClickListener;

import java.util.List;

/**
 * Created by MewX on 2015/5/10.
 * Search History Adapter.
 */
public class SearchHistoryAdapter extends RecyclerView.Adapter<SearchHistoryAdapter.ViewHolder> {

    private MyItemClickListener mItemClickListener;
    private MyItemLongClickListener mItemLongClickListener;
    private List<String> history = null;

    public SearchHistoryAdapter(List<String> h) {
        this.history = h;
    }

    @Override
    public ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view = LayoutInflater.from(viewGroup.getContext()).inflate(R.layout.view_search_history_item,viewGroup,false);
        return new ViewHolder(view, mItemClickListener, mItemLongClickListener);
    }

    public void setOnItemClickListener(MyItemClickListener listener){
        this.mItemClickListener = listener;
    }

    public void setOnItemLongClickListener(MyItemLongClickListener listener){
        this.mItemLongClickListener = listener;
    }


    @Override
    public void onBindViewHolder(ViewHolder viewHolder, int position) {
        if(viewHolder.mTextView != null)
            viewHolder.mTextView.setText(history.get(position));
    }

    @Override
    public int getItemCount() {
        return history.size();
    }

    public static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {
        private MyItemClickListener mClickListener;
        private MyItemLongClickListener mLongClickListener;
        public TextView mTextView;

        public ViewHolder(View view, MyItemClickListener clickListener, MyItemLongClickListener longClickListener){
            super(view);
            this.mClickListener = clickListener;
            this.mLongClickListener = longClickListener;
            view.findViewById(R.id.item_card).setOnClickListener(this);
            view.findViewById(R.id.item_card).setOnLongClickListener(this);

            // real content
            mTextView = (TextView) view.findViewById(R.id.search_history_text);
        }

        @Override
        public void onClick(View v) {
            if(mClickListener != null){
                mClickListener.onItemClick(v,getAdapterPosition());
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
