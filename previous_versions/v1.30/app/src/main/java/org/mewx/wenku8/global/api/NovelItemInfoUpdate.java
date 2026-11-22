package org.mewx.wenku8.global.api;

import android.util.LruCache;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import org.mewx.wenku8.util.CrashReporter;

/**
 * Created by MewX on 2015/1/20.
 * The updated version of novel item info.
 */
public class NovelItemInfoUpdate {
    public static final String LOADING_STRING = "Loading...";

    // Global cache for novel item info across ranking lists and search results.
    private static final LruCache<Integer, NovelItemInfoUpdate> mCache = new LruCache<>(500);

    // Variables
    public int aid;
    public String title;
    public String author = LOADING_STRING;
    public String status = LOADING_STRING;
    public String update = LOADING_STRING; // last update time
    public String intro_short = LOADING_STRING;
    public String tags = ""; // loaded from new API
    public String latest_chapter = LOADING_STRING; // only used in bookshelf

    // static function
    @NonNull
    public static NovelItemInfoUpdate convertFromMeta(@NonNull NovelItemMeta nim) {
        NovelItemInfoUpdate niiu = new NovelItemInfoUpdate(0);
        niiu.title = nim.title;
        niiu.aid = nim.aid;
        niiu.author = nim.author;
        niiu.status = nim.bookStatus;
        niiu.update = nim.lastUpdate;
        niiu.latest_chapter = nim.latestSectionName;

        return niiu;
    }

    @Nullable
    public static NovelItemInfoUpdate parse(@NonNull String xml) {
        try {
            NovelItemInfoUpdate niiu = new NovelItemInfoUpdate(0);
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            xmlPullParser.setInput(new StringReader(xml));
            int eventType = xmlPullParser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:// all start
                        break;

                    case XmlPullParser.START_TAG:

                        if ("metadata".equals(xmlPullParser.getName())) {
                            // Init all the value
                            niiu.aid = 0;
                            niiu.title = "";
                            niiu.author = "";
                            niiu.status = "";
                            niiu.update = "";
                            niiu.intro_short = "";
                            niiu.tags = "";
                            niiu.latest_chapter = "";

                        } else if ("data".equals(xmlPullParser.getName())) {
                            if ("Title".equals(xmlPullParser.getAttributeValue(0))) {
                                niiu.aid = Integer.valueOf(
                                        xmlPullParser.getAttributeValue(1));
                                niiu.title = xmlPullParser.nextText();
                            } else if ("Author".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                niiu.author = xmlPullParser.getAttributeValue(1);
                            } else if ("BookStatus".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                niiu.status = xmlPullParser.getAttributeValue(1);
                            } else if ("LastUpdate".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                niiu.update = xmlPullParser.getAttributeValue(1);
                            } else if ("IntroPreview".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                // need to remove leading space '\u3000'
                                niiu.intro_short = xmlPullParser.nextText().replaceAll("[ |　]", " ").trim();//.trim().replaceAll("\u3000","");
                            }
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }
            return niiu;
        } catch (Exception e) {
            CrashReporter.recordException("NovelItemInfoUpdate.parse", e);
            return null;
        }
    }

    public NovelItemInfoUpdate(int aid) {
        this.aid = aid;
        this.title = Integer.toString(aid);
    }

    /**
     * True while this carries no real data yet -- the constructor above puts the aid in the title
     * as a stand-in, so that is what "not loaded" looks like.
     *
     * <p>This was called {@code isInitialized()}, which read as the exact opposite of what it
     * returns. The adapter's load gate was written against the old name and happened to be correct
     * anyway, but the name is why the gate survived c347711 unexamined.
     */
    public boolean isPlaceholder() {
        return title == null || title.equals(Integer.toString(aid));
    }

    /** A field that a list row would render as the literal text "Loading...". */
    public static boolean isMissing(@Nullable String value) {
        return value == null || LOADING_STRING.equals(value);
    }

    /**
     * How many of the five fields a list row shows are actually populated.
     *
     * <p>Used to compare two records for the same novel, which is needed because they no longer
     * all come from the same place, and the three sources do not agree on completeness:
     * {@code novellist} fills a whole page at once, {@code book&do=bookinfo} fills one novel but
     * carries no Tags, and {@link #convertFromMeta} builds a bookshelf row from the saved intro
     * file and never sets {@link #intro_short} at all. The sparser record must never displace the
     * fuller one.
     *
     * <p>A note on {@code novellist}, since an earlier version of this comment claimed otherwise:
     * a survey of roughly 150 novels across six sorts, both languages and pages 1 to 418 found it
     * returning all five fields every time. Its rows are treated as potentially sparse because the
     * parser leaves the sentinel in place for anything absent, not because omissions were observed.
     *
     * <p>{@link #latest_chapter} is deliberately excluded. Neither list endpoint returns it -- it
     * comes from the saved volume index, so only the bookshelf ever has it -- and counting it would
     * mark every ranking row incomplete forever.
     */
    public int populatedFieldCount() {
        int count = 0;
        if (!isPlaceholder()) count++;
        if (!isMissing(author)) count++;
        if (!isMissing(status)) count++;
        if (!isMissing(update)) count++;
        if (!isMissing(intro_short)) count++;
        return count;
    }

    @Nullable
    public static NovelItemInfoUpdate getFromCache(int aid) {
        return mCache.get(aid);
    }

    /**
     * Caches {@code item} unless the cache already holds a more complete record for that novel.
     *
     * <p>This used to be first-write-wins, which stopped being safe once
     * {@link NovelListWithInfoParser} started caching every row of every page it parses. Twelve
     * ranking tabs, the latest list, search and the bookshelf all write here, from sources of
     * differing completeness, so whichever screen reached a novel first owned its entry for the
     * rest of the process -- and every other list then rendered that copy, because
     * {@code NovelItemAdapterUpdate} preferred the cache over the page it had just parsed itself.
     *
     * <p>Which of those writers produced the records that showed "Loading..." in practice was never
     * pinned down; the reproduction predates the fix and the fix removed it. So this is a
     * correctness rule rather than a post-mortem: the cache must not lose information it already
     * holds, whatever put it there.
     */
    public static void putToCache(@NonNull NovelItemInfoUpdate item) {
        final NovelItemInfoUpdate existing = getFromCache(item.aid);
        if (existing == null || item.populatedFieldCount() > existing.populatedFieldCount()) {
            mCache.put(item.aid, item);
        }
    }

}
