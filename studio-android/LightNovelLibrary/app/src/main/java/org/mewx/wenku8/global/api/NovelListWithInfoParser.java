package org.mewx.wenku8.global.api;

import androidx.annotation.NonNull;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

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

    /**
     * get current 'page' attributes
     * @param xml input xml
     * @return the page in the xml file or 0 by default
     */
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

    @NonNull
    static public List<NovelListWithInfo> getNovelListWithInfo(String xml) {
        List<NovelListWithInfo> l = new ArrayList<>();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            NovelListWithInfo n = new NovelListWithInfo();
            xmlPullParser.setInput(new StringReader(xml));
            int eventType = xmlPullParser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_TAG:
                        if ("item".equals(xmlPullParser.getName())) {
                            n = new NovelListWithInfo();
                            n.aid = Integer.valueOf(xmlPullParser.getAttributeValue(0));
                        } else if ("data".equals(xmlPullParser.getName())) {
                            if ("Title".equals(xmlPullParser.getAttributeValue(0))) {
                                n.name = xmlPullParser.nextText();
                            } else if ("TotalHitsCount".equals(xmlPullParser.getAttributeValue(0))) {
                                n.hit = Integer.valueOf(xmlPullParser.getAttributeValue(1));
                            } else if ("PushCount".equals(xmlPullParser.getAttributeValue(0))) {
                                n.push = Integer.valueOf(xmlPullParser.getAttributeValue(1));
                            } else if ("FavCount".equals(xmlPullParser.getAttributeValue(0))) {
                                n.fav = Integer.valueOf(xmlPullParser.getAttributeValue(1));
                            }
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("item".equals(xmlPullParser.getName())) {
                            Log.v("MewX-XML", n.aid + ";" + n.name + ";" + n.hit
                                    + ";" + n.push + ";" + n.fav);
                            l.add(n);
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return l;
    }
}
