package org.mewx.wenku8.api;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

@SuppressWarnings("unused")
public class Wenku8API {
    public static final String UNKNOWN = "Unknown";
    public static AppLanguage CurrentLang = AppLanguage.SC;
    public static String NoticeString = UNKNOWN;
    public static final String REGISTER_URL = UNKNOWN;
    public static final String BASE_URL = UNKNOWN;
    public static final String V2_API_URL = UNKNOWN;

    public static String getCoverURL(int aid) {
        return "";
    }

    public static String getAvatarURL(int uid) {
        throw new UnsupportedOperationException("stub");
    }

    public static boolean initCrypto(
            @NonNull Context appContext,
            int versionCode,
            @NonNull String distributionChannel) {
        return false;
    }

    public static boolean initCrypto(
            @NonNull Context appContext,
            int versionCode,
            @NonNull String distributionChannel,
            boolean isDebug) {
        return false;
    }

    public static boolean isCryptoInitialized() {
        return false;
    }

    public static void updateServerClockOffset(long serverTimeMs) {
        // No-op on stub builds.
    }

    @Nullable
    public static SealedRequest sealV2Request(
            @NonNull String rawActionQuery,
            @Nullable String exactCookieHeader) {
        return null;
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

    public static String getStatusBySTATUS(NovelPublicationStatus s) {
        throw new UnsupportedOperationException("stub");
    }

    public enum NovelSortedBy {
        allVisit, allVote, monthVisit, monthVote, weekVisit, weekVote, dayVisit, dayVote,
        postDate, lastUpdate, goodNum, size, fullFlag
    }

    public static NovelSortedBy getNovelSortedBy(String n) {
        return NovelSortedBy.valueOf(n);
    }

    public static String getNovelSortedBy(NovelSortedBy n) {
        return n.name();
    }

    @NonNull
    public static String buildNovelCoverQuery(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildNovelShortInfoQuery(int aid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildNovelShortInfoUpdateQuery(int aid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildNovelFullIntroQuery(int aid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildNovelFullMetaQuery(int aid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildNovelIndexQuery(int aid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildNovelContentQuery(int aid, int cid, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildSearchNovelByNameQuery(String novelName, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildSearchNovelByAuthorQuery(String authorName, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildNovelListQuery(NovelSortedBy n, int page) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildMewxNovelListQuery(NovelSortedBy n, int page, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildNovelListWithInfoQuery(NovelSortedBy n, int page, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildLibraryListQuery() {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildNovelListByLibraryQuery(int sortId, int page) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildNovelListByLibraryWithInfoQuery(int sortId, int page, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildUserLoginQuery(String username, String password) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildUserLoginEmailQuery(String email, String password) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildUserAvatarQuery() {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildUserLogoutQuery() {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildUserInfoQuery() {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildUserSignQuery() {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildVoteNovelQuery(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildBookshelfListAidQuery(AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildBookshelfListQuery(AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildAddToBookshelfQuery(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildDelFromBookshelfQuery(int aid) {
        throw new UnsupportedOperationException("stub");
    }

    @Nullable
    public static String searchBadWords(String source) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildCommentListQuery(int aid, int page, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildCommentContentQuery(int rid, int page, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildCommentNewThreadQuery(int aid, String title, String content) {
        throw new UnsupportedOperationException("stub");
    }

    @NonNull
    public static String buildCommentReplyQuery(int rid, String content) {
        throw new UnsupportedOperationException("stub");
    }
}
