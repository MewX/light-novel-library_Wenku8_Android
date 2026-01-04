package org.mewx.wenku8.api;

import android.content.ContentValues;

import androidx.annotation.Nullable;

@SuppressWarnings("unused")
public class Wenku8API {
    public static final String UNKNOWN = "Unknown";
    public static LANG CurrentLang = LANG.SC;
    public static String AppVer = UNKNOWN;
    public static String NoticeString = UNKNOWN;
    public static final String REGISTER_URL = UNKNOWN;
    public static final String BASE_URL = UNKNOWN;

    public static String getCoverURL(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    public static final int MIN_REPLY_TEXT = -1;

    public enum LANG {
        SC, TC
    }

    public enum STATUS {
        FINISHED, NOT_FINISHED
    }

    public static STATUS getSTATUSByInt(int i) {
        throw new UnsupportedOperationException("stub");
    }

    public static STATUS getSTATUSByString(String s) {
        throw new UnsupportedOperationException("stub");
    }

    public static String getStatusBySTATUS(STATUS s) {
        throw new UnsupportedOperationException("stub");
    }

    public enum NOVELSORTBY {
        allVisit, allVote, monthVisit, monthVote, weekVisit, weekVote, dayVisit, dayVote, postDate, lastUpdate, goodNum, size, fullFlag
    }

    public static NOVELSORTBY getNOVELSORTBY(String n) {
        throw new UnsupportedOperationException("stub");
    }

    public static String getNOVELSORTBY(NOVELSORTBY n) {
        throw new UnsupportedOperationException("stub");
    }


    public static ContentValues getNovelCover(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelShortInfo(int aid, LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelShortInfoUpdate_CV(int aid, LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelFullIntro(int aid, LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelFullMeta(int aid, LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelIndex(int aid, LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelContent(int aid, int cid, LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues searchNovelByNovelName(String novelName, LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues searchNovelByAuthorName(String authorName, LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelList(NOVELSORTBY n, int page) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelListWithInfo(NOVELSORTBY n, int page, LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getLibraryList() {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelListByLibrary(int sortId, int page) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelListByLibraryWithInfo(int sortId, int page, LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getUserLoginParams(String username, String password) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getUserLoginEmailParams(String email, String password) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getUserAvatar() {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getUserLogoutParams() {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getUserInfoParams() {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getUserSignParams() {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getVoteNovelParams(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getBookshelfListAid(LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getBookshelfListParams(LANG l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getAddToBookshelfParams(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getDelFromBookshelfParams(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    @Nullable
    public static String searchBadWords(String source) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getCommentListParams(int aid, int page) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getCommentContentParams(int rid, int page) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getCommentNewThreadParams(int aid, String title, String content) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getCommentReplyParams(int rid, String content) {
        throw new UnsupportedOperationException("stub");
    }
}
