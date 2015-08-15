package org.mewx.wenku8.global.api;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;

/**
 * Created by MewX on 2015/1/20.
 * The updated version of novel item info.
 */
public class NovelItemInfoUpdate {
    private final String LoadingString = "Loading...";

    // Variables
    public int aid = 0;
    public String title = LoadingString;
    public String author = LoadingString;
    public String status = LoadingString;
    public String update = LoadingString; // last update time
    public String intro_short = LoadingString;
    public boolean isLoading = false;

    public String intro_full = ""; // not necessary
    public boolean imageReady = false; // image

    // static function
    public static NovelItemInfoUpdate convertFromMeta(NovelItemMeta nim) {
        NovelItemInfoUpdate niiu = new NovelItemInfoUpdate(0);
        niiu.aid = nim.aid;
        niiu.title = nim.title;
        niiu.author = nim.author;
        niiu.status = nim.bookStatus;
        niiu.update = nim.lastUpdate;

        return niiu;
    }

    public static NovelItemInfoUpdate parse(String xml) {
        NovelItemInfoUpdate niiu = new NovelItemInfoUpdate(0);
        try {
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
                            niiu.intro_full = "";

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
            e.printStackTrace();
            return null;
        }
    }

    public NovelItemInfoUpdate(int aid) {
        this.aid = aid;
        this.title = Integer.toString(aid);
    }

    /**
     * "get" functions
     */
    public void setIntroFull(String str) {
        intro_full = str;
    }
}
