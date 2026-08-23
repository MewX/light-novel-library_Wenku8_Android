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

    // The methods below build request parameters or URLs -- they do not talk to anything
    // themselves, and LightNetwork refuses every request on a stub build anyway. Returning an
    // inert value instead of throwing lets a screen reach its normal offline state, which is what
    // an instrumented test on CI should be looking at. Anything that would have to invent a
    // *result* still throws; see LightUserSession.
    //
    // NovelInfoActivity hands this straight to ImageLoader in onCreateView, so a throw made the
    // whole screen unstartable. An empty URI loads no image, which is the truth here.
    public static String getCoverURL(int aid) {
        return "";
    }

    public static String getAvatarURL(int uid) {
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

    /**
     * The enum constants are spelled exactly as the server's sort keys, so converting between the
     * two is a rename rather than a lookup and the stub can do it honestly. An unrecognised key
     * still throws out of {@code valueOf}, which is the loud behaviour a genuinely wrong sort
     * order deserves.
     */
    public static NovelSortedBy getNovelSortedBy(String n) {
        return NovelSortedBy.valueOf(n);
    }

    public static String getNovelSortedBy(NovelSortedBy n) {
        return n.name();
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

    /**
     * Returns empty parameters rather than throwing, because the reader calls this from
     * {@code onCreate} before it knows whether it needs the network at all — so a chapter being
     * read straight off disk still passes through here, and a throw takes the Activity down with
     * it. That is not hypothetical: it crashed the whole instrumentation run on CI while every
     * developer machine, which builds against the real submodule, stayed green.
     *
     * <p>Empty is the honest value. These are POST parameters for a server this stub cannot
     * reach, and {@link org.mewx.wenku8.network.LightNetwork} refuses every request anyway, so
     * nothing downstream reads them.
     */
    public static ContentValues getNovelContent(int aid, int cid, AppLanguage l) {
        return new ContentValues();
    }

    public static ContentValues searchNovelByNovelName(String novelName, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    public static ContentValues searchNovelByAuthorName(String authorName, AppLanguage l) {
        throw new UnsupportedOperationException("stub");
    }

    // Reached from NovelItemListFragment and LatestFragment as they start loading, i.e. from the
    // fragments MainActivity shows on launch. Empty parameters for a request that will return null
    // anyway; the list then renders its empty/failed state, which is the point.
    public static ContentValues getNovelList(NovelSortedBy n, int page) {
        return new ContentValues();
    }

    public static ContentValues getMewxNovelList(NovelSortedBy n, int page, AppLanguage l) {
        return new ContentValues();
    }

    public static ContentValues getNovelListWithInfo(NovelSortedBy n, int page, AppLanguage l) {
        return new ContentValues();
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

    /**
     * Inert rather than throwing, and for a different reason than the builders above -- here the
     * throw was not even loud. {@code UserInfoActivity.AsyncGetUserInfo} wraps its whole body in
     * {@code catch (Exception e)} and funnels it to {@code CrashReporter.recordException}, so on a
     * stub build this method filed a non-fatal crash report on every launch of the account screen
     * and then carried on to the same NETWORK_ERROR it reaches anyway. Throwing only pays for
     * itself where it surfaces an unconsidered path; swallowed and reported as a real defect, it
     * buries the reports that are.
     */
    public static ContentValues getUserInfoParams() {
        return new ContentValues();
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
