package org.mewx.lightnovellibrary.global.api;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

/**
 * Created by MewX on 2015/1/20.
 */
public class NovelItemInfo {

    // Variables
    private boolean parseStatus; // default false
    private boolean loadingStatus;

    private int aid = 0;
    private String title = "";
    private String author = "";
    private int status = 0; // 0 - not; 1 - finished
    private String update = ""; // last update time
    private String intro_short = "";

    private String intro_full = ""; // not necessary

    private boolean imageReady = false; // image

    /**
     * Init the whole struct with the received XML string
     *
     * @param str only str[0] is available, because I use array for pass by reference
     */
    public NovelItemInfo(String[] str) {
        setNovelItemInfo(str);
        return;
    }

    public NovelItemInfo(int aid) {
        // set aid only, first loading event
        this.aid = aid;
        this.title = Integer.toString(aid); // use aid as title
        return;
    }

    public NovelItemInfo() {
        return;
    }

    public boolean setNovelItemInfo(String[] str) {
        parseStatus = parseNovelItemIntro(str);
        return parseStatus;
    }

    /**
     * get parse status
     *
     * @return true - parsed, else failed.
     */

    public boolean getLoadingStatus(){
        return loadingStatus;
    }

    public void setLoadingStatus(boolean b){
        loadingStatus = b;
        return;
    }

    public boolean getParseStatus() {
        return parseStatus;
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
                            intro_full = "";

                        } else if ("data".equals(xmlPullParser.getName())) {
                            if ("Title".equals(xmlPullParser.getAttributeValue(0))) {
                                aid = new Integer(
                                        xmlPullParser.getAttributeValue(1));
                                title = xmlPullParser.nextText();
                            } else if ("Author".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                author = xmlPullParser.getAttributeValue(1);
                            } else if ("BookStatus".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                        status = new Integer(
                                        xmlPullParser.getAttributeValue(1));
                            } else if ("LastUpdate".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                update = xmlPullParser.getAttributeValue(1);
                            } else if ("IntroPreview".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                intro_short = xmlPullParser.nextText();
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

    public String getIntroFull() {
        return intro_full;
    }

    public void setIntroFull(String str) {
        intro_full = str;
        return;
    }
}
