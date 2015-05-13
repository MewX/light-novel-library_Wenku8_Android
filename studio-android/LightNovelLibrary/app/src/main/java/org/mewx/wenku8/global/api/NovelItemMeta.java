package org.mewx.wenku8.global.api;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

/**
 * Created by MewX on 2015/5/13.
 */
public class NovelItemMeta {
    public int aid;
    public String title;
    public String author;
    public int dayHitsCount;
    public int totalHitsCount;
    public int pushCount;
    public int favCount;
    public String pressId;
    public String bookStatus; // just text, differ from "NovelIntro"
    public int bookLength;
    public String lastUpdate;
    public int latestSectionCid;
    public String latestSectionName;
    public String fullIntro; // fetch from another place
}
