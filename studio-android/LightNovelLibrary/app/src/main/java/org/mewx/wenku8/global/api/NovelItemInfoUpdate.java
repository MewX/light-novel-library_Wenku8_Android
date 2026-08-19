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

    public boolean isInitialized() {
        return title.equals(Integer.toString(aid));
    }

    @Nullable
    public static NovelItemInfoUpdate getFromCache(int aid) {
        return mCache.get(aid);
    }

    public static void putToCache(@NonNull NovelItemInfoUpdate item) {
        if (getFromCache(item.aid) == null) {
            mCache.put(item.aid, item);
        }
    }

}
