package org.mewx.wenku8.global.api;

import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;

/** Novel List With Info for improve loading speed.
 * Created by MewX on 2015/10/20.
 */
public class NovelListWithInfoParser {
    static public class NovelListWithInfo {
        public int aid = 0;
        public String name = "";
        public int hit = 0;
        public int push = 0;
        public int fav = 0;
    }

    static public int getNovelListWithInfoPageNum(String xml) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            xmlPullParser.setInput(new StringReader(xml));
            int eventType = xmlPullParser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:
                        if ("page".equals(xmlPullParser.getName())) {
                            return Integer.valueOf(xmlPullParser.getAttributeValue(0));
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0; // default
    }

    static public ArrayList<NovelListWithInfo> getNovelListWithInfo(String xml) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            ArrayList<NovelListWithInfo> l = null;
            NovelListWithInfo n = null;
            xmlPullParser.setInput(new StringReader(xml));
            int eventType = xmlPullParser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        l = new ArrayList<NovelListWithInfo>();
                        break;

                    case XmlPullParser.START_TAG:

                        if ("item".equals(xmlPullParser.getName())) {
                            n = new NovelListWithInfo();
                            n.aid = Integer.valueOf(xmlPullParser.getAttributeValue(0));
                            // Log.v("MewX-XML", "aid=" + n.aid);
                        } else if ("data".equals(xmlPullParser.getName())) {
                            if ("Title".equals(xmlPullParser.getAttributeValue(0))) {
                                n.name = xmlPullParser.nextText();
                                // Log.v("MewX-XML", n.name);
                            } else if ("TotalHitsCount".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                n.hit = Integer.valueOf(
                                        xmlPullParser.getAttributeValue(1));
                                // Log.v("MewX-XML", "hit=" + n.hit);
                            } else if ("PushCount".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                n.push = Integer.valueOf(
                                        xmlPullParser.getAttributeValue(1));
                                // Log.v("MewX-XML", "push=" + n.push);
                            } else if ("FavCount".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                n.fav = Integer.valueOf(
                                        xmlPullParser.getAttributeValue(1));
                                // Log.v("MewX-XML", "fav=" + n.fav);
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("item".equals(xmlPullParser.getName())) {
                            Log.v("MewX-XML", n.aid + ";" + n.name + ";" + n.hit
                                    + ";" + n.push + ";" + n.fav);
                            l.add(n);
                            n = null;
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }
            return l;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
