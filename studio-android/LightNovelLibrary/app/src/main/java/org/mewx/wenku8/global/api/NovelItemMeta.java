package org.mewx.wenku8.global.api;

import org.mewx.wenku8.api.Wenku8API;

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

    NovelItemMeta() {
        aid = 1;
        title = Integer.toString(aid);
        author = Wenku8API.UNKNOWN;
        dayHitsCount = 0;
        totalHitsCount = 0;
        pushCount = 0;
        favCount = 0;
        pressId = Wenku8API.UNKNOWN;
        bookStatus = Wenku8API.UNKNOWN;
        bookLength = 0;
        lastUpdate = Wenku8API.UNKNOWN;
        latestSectionCid = 0;
        latestSectionName = Wenku8API.UNKNOWN;
        fullIntro = Wenku8API.UNKNOWN;
    }
}
