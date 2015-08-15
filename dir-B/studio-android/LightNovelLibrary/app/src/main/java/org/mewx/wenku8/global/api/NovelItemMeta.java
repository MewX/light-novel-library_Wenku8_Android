package org.mewx.wenku8.global.api;

/**
 * Created by MewX on 2015/5/13.
 * Novel Item Meta data.
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

    static private final String Unknown = "Unknown";
    NovelItemMeta() {
        aid = 1;
        title = Integer.toString(aid);
        author = Unknown;
        dayHitsCount = 0;
        totalHitsCount = 0;
        pushCount = 0;
        favCount = 0;
        pressId = Unknown;
        bookStatus = Unknown;
        bookLength = 0;
        lastUpdate = Unknown;
        latestSectionCid = 0;
        latestSectionName = Unknown;
        fullIntro = Unknown;
    }
}
