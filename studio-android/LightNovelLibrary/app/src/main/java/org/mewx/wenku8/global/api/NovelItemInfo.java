package org.mewx.wenku8.global.api;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

/**
 * Created by MewX on 2015/1/20.
 * Novel item info short.
 */
public class NovelItemInfo {

    // Variables
    private boolean parseStatus; // default false
//    private boolean loadingStatus;

    private int aid = 0;
    private String title = "";
    private String author = "";
    private int status = 0; // 0 - not; 1 - finished
    private String update = ""; // last update time
    private String intro_short = "";

//    private String intro_full = ""; // not necessary

//    private boolean imageReady = false; // image

    /**
     * Init the whole struct with the received XML string
     *
     * @param str only str[0] is available, because I use array for pass by reference
     */
    public NovelItemInfo(String[] str) {
        setNovelItemInfo(str);
    }

    public NovelItemInfo(int aid) {
        // set aid only, first loading event
        this.aid = aid;
        this.title = Integer.toString(aid); // use aid as title
    }

    public NovelItemInfo() {
        // this construct is for manually set all info
        parseStatus = true;
    }

    public boolean setNovelItemInfo(String[] str) {
        parseStatus = parseNovelItemIntro(str);
        return parseStatus;
    }

//    public boolean getLoadingStatus(){
//        return loadingStatus;
//    }
//
//    public void setLoadingStatus(boolean b){
//        loadingStatus = b;
//    }

    public boolean getParseStatus() {
        return parseStatus; // true - parsed, else failed.
    }

    private boolean parseNovelItemIntro(String[] str) {
        // only str[0] is available

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            xmlPullParser.setInput(new StringReader(str[0]));
            int eventType = xmlPullParser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:// all start
                        break;

                    case XmlPullParser.START_TAG:

                        if ("metadata".equals(xmlPullParser.getName())) {
                            // Init all the value
                            aid = 0;
                            title = "";
                            author = "";
                            status = 0;
                            update = "";
                            intro_short = "";

                        } else if ("data".equals(xmlPullParser.getName())) {
                            if ("Title".equals(xmlPullParser.getAttributeValue(0))) {
                                aid = Integer.valueOf(
                                        xmlPullParser.getAttributeValue(1));
                                title = xmlPullParser.nextText();
                            } else if ("Author".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                author = xmlPullParser.getAttributeValue(1);
                            } else if ("BookStatus".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                status = Integer.valueOf(
                                        xmlPullParser.getAttributeValue(1));
                            } else if ("LastUpdate".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                update = xmlPullParser.getAttributeValue(1);
                            } else if ("IntroPreview".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                intro_short = xmlPullParser.nextText().trim().replaceAll("\u3000","");
                            }
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * "get" functions
     */
    public int getAid() {
        return aid;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public int getStatus() {
        return status;
    }

    public String getUpdate() {
        return update;
    }

    public String getIntroShort() {
        return intro_short;
    }

    public void setAid(int aid) {
        this.aid = aid;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public void setUpdate(String update) {
        this.update = update;
    }

    public void setIntro_short(String intro_short) {
        this.intro_short = intro_short;
    }

    //    public String getIntroFull() {
//        return intro_full;
//    }
//
//    public void setIntroFull(String str) {
//        intro_full = str;
//    }
}