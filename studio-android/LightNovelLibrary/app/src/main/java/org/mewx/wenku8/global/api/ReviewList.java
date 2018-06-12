package org.mewx.wenku8.global.api;

import android.support.annotation.NonNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ReviewList {

    public static class Review {
        private int rid; // review id
        @NonNull private Date postTime;
        private int noReplies;
        @NonNull private Date lastReplyTime;
        @NonNull private String userName;
        private int uid; // post user
        @NonNull private String title; // review title

        public Review(int rid, @NonNull Date postTime, int noReplies, @NonNull Date lastReplyTime, @NonNull String userName, int uid, @NonNull String title) {
            this.rid = rid;
            this.postTime = postTime;
            this.noReplies = noReplies;
            this.lastReplyTime = lastReplyTime;
            this.userName = userName;
            this.uid = uid;
            this.title = title;
        }

        public int getRid() {
            return rid;
        }

        public void setRid(int rid) {
            this.rid = rid;
        }

        @NonNull
        public Date getPostTime() {
            return postTime;
        }

        public void setPostTime(@NonNull Date postTime) {
            this.postTime = postTime;
        }

        public int getNoReplies() {
            return noReplies;
        }

        public void setNoReplies(int noReplies) {
            this.noReplies = noReplies;
        }

        @NonNull
        public Date getLastReplyTime() {
            return lastReplyTime;
        }

        public void setLastReplyTime(@NonNull Date lastReplyTime) {
            this.lastReplyTime = lastReplyTime;
        }

        public int getUid() {
            return uid;
        }

        public void setUid(int uid) {
            this.uid = uid;
        }

        @NonNull
        public String getTitle() {
            return title;
        }

        public void setTitle(@NonNull String title) {
            this.title = title;
        }

        @NonNull
        public String getUserName() {
            return userName;
        }

        public void setUserName(@NonNull String userName) {
            this.userName = userName;
        }
    }

    private List<Review> list = new ArrayList<>();
    private int totalPage = 1;
    private int currentPage = 0; // 1-totalPage, 0 means not yet loaded

    public List<Review> getList() {
        return list;
    }

    public int getTotalPage() {
        return totalPage;
    }

    public void setTotalPage(int totalPage) {
        this.totalPage = totalPage;
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public void setCurrentPage(int currentPage) {
        this.currentPage = currentPage;
    }
}
