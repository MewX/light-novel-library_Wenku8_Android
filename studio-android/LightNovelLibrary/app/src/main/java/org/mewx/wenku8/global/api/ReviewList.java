package org.mewx.wenku8.global.api;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ReviewList {

    public static class Review {
        private int rid; // review id
        private Date postTime;
        private int noReplies;
        private Date lastReplyTime;
        private int uid; // post user
        private String title; // review title

        public Review(int rid, Date postTime, int noReplies, Date lastReplyTime, int uid, String title) {
            this.rid = rid;
            this.postTime = postTime;
            this.noReplies = noReplies;
            this.lastReplyTime = lastReplyTime;
            this.uid = uid;
            this.title = title;
        }

        public int getRid() {
            return rid;
        }

        public void setRid(int rid) {
            this.rid = rid;
        }

        public Date getPostTime() {
            return postTime;
        }

        public void setPostTime(Date postTime) {
            this.postTime = postTime;
        }

        public int getNoReplies() {
            return noReplies;
        }

        public void setNoReplies(int noReplies) {
            this.noReplies = noReplies;
        }

        public Date getLastReplyTime() {
            return lastReplyTime;
        }

        public void setLastReplyTime(Date lastReplyTime) {
            this.lastReplyTime = lastReplyTime;
        }

        public int getUid() {
            return uid;
        }

        public void setUid(int uid) {
            this.uid = uid;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }

    private List<Review> list = new ArrayList<>();
    private int totalPage = 1;
    private int currentPage = 1; // 1-totalPage

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
