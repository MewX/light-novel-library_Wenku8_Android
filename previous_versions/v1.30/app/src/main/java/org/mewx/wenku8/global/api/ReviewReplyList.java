package org.mewx.wenku8.global.api;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ReviewReplyList {

    public static class ReviewReply {
        @NonNull private Date replyTime;
        @NonNull private String userName;
        private int uid; // post user
        @NonNull private String content;

        public ReviewReply(@NonNull Date replyTime, @NonNull String userName, int uid, @NonNull String content) {
            setReplyTime(replyTime);
            setUserName(userName);
            setUid(uid);
            setContent(content);
        }

        @NonNull
        public Date getReplyTime() {
            return replyTime;
        }

        public void setReplyTime(@NonNull Date replyTime) {
            this.replyTime = replyTime;
        }

        @NonNull
        public String getUserName() {
            return userName;
        }

        public void setUserName(@NonNull String userName) {
            this.userName = userName;
        }

        public int getUid() {
            return uid;
        }

        public void setUid(int uid) {
            this.uid = uid;
        }

        @NonNull
        public String getContent() {
            return content;
        }

        public void setContent(@NonNull String content) {
            this.content = content;
        }
    }

    private List<ReviewReply> list = new ArrayList<>();
    private int totalPage = 1;
    private int currentPage = 0; // 1-totalPage, 0 means not yet loaded

    public List<ReviewReply> getList() {
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
