package org.mewx.wenku8.api;

import android.content.ContentValues;

import androidx.annotation.Nullable;

@SuppressWarnings("unused")
public class Wenku8API {
    public static final String UNKNOWN = "Unknown";
    public static AppLanguage CurrentLang = AppLanguage.SC;
    public static String AppVer = UNKNOWN;
    public static String NoticeString = UNKNOWN;
    public static final String REGISTER_URL = UNKNOWN;
    public static final String BASE_URL = UNKNOWN;

    public static String getCoverURL(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    public static final int MIN_REPLY_TEXT = -1;

    public enum AppLanguage {
        SC, TC
    }

    public enum NovelPublicationStatus {
        FINISHED, NOT_FINISHED
    }

    public static NovelPublicationStatus getNovelPublicationStatusByInt(int i) {
        throw new UnsupportedOperationException("stub");
    }

    public static NovelPublicationStatus getNovelPublicationStatusByString(String s) {
        throw new UnsupportedOperationException("stub");
    }

    public static String getStatusByNovelPublicationStatus(NovelPublicationStatus s) {
        throw new UnsupportedOperationException("stub");
    }

    public enum NovelSortedBy {
        allVisit, allVote, monthVisit, monthVote, weekVisit, weekVote, dayVisit, dayVote, postDate, lastUpdate, goodNum, size, fullFlag
    }

    public static NovelSortedBy getNovelSortedBy(String n) {
        throw new UnsupportedOperationException("stub");
    }

    public static String getNovelSortedBy(NovelSortedBy n) {
        throw new UnsupportedOperationException("stub");
    }


    public static ContentValues getNovelCover(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelShortInfo(int aid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelShortInfoUpdate_CV(int aid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelFullIntro(int aid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelFullMeta(int aid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelIndex(int aid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelContent(int aid, int cid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues searchNovelByNovelName(String novelName, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues searchNovelByAuthorName(String authorName, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelList(NovelSortedBy n, int page) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelListWithInfo(NovelSortedBy n, int page, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getLibraryList() {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelListByLibrary(int sortId, int page) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getNovelListByLibraryWithInfo(int sortId, int page, AppLanguage l) {
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

    public static ContentValues getBookshelfListAid(AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues getBookshelfListParams(AppLanguage l) {
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
