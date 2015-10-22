package org.mewx.wenku8.global.api;

import android.util.Log;

import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.LightTool;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/4/21.
 * Wenku8 parsers.
 */
public class Wenku8Parser {

    public static List<Integer> parseNovelItemList(String str, int page) {
        List<Integer> list = new ArrayList<>();

        // <?xml version="1.0" encoding="utf-8"?>
        // <result>
        // <page num='166'/>
        // <item aid='1143'/>
        // <item aid='1034'/>
        // <item aid='1213'/>
        // <item aid='1'/>
        // <item aid='1011'/>
        // <item aid='1192'/>
        // <item aid='433'/>
        // <item aid='47'/>
        // <item aid='7'/>
        // <item aid='374'/>
        // </result>

        // The returning list of this xml is: (total page, aids)
        // { 166, 1143, 1034, 1213, 1, 1011, 1192, 433, 47, 7, 374 }

        final char SEPERATOR = '\''; // seperator

        // get total page
        int beg, temp;
        beg = str.indexOf(SEPERATOR);
        temp = str.indexOf(SEPERATOR, beg + 1);
        if (beg == -1 || temp == -1) return null; // this is an exception
        if(LightTool.isInteger(str.substring(beg + 1, temp)))
            list.add(Integer.parseInt(str.substring(beg + 1, temp)));
        beg = temp + 1; // prepare for loop

        // init array
        while (true) {
            beg = str.indexOf(SEPERATOR, beg);
            temp = str.indexOf(SEPERATOR, beg + 1);
            if (beg == -1 || temp == -1) break;

            if(LightTool.isInteger(str.substring(beg + 1, temp)))
                list.add(Integer.parseInt(str.substring(beg + 1, temp)));
            Log.v("MewX", "Add novel aid: " + list.get(list.size() - 1));

            beg = temp + 1; // prepare for next round
        }

        return list;
    }


    static public NovelItemMeta parsetNovelFullMeta(String xml) {
        // get full XML metadata of a novel, here is an example:
        // -----------------------------------------------------
        // <?xml version="1.0" encoding="utf-8"?>
        // <metadata>
        // <data name="Title" aid="1306"><![CDATA[向森之魔物献上花束(向森林的魔兽少女献花)]]></data>
        // <data name="Author" value="小木君人"/>
        // <data name="DayHitsCount" value="26"/>
        // <data name="TotalHitsCount" value="43984"/>
        // <data name="PushCount" value="1735"/>
        // <data name="FavCount" value="848"/>
        // <data name="PressId" value="小学馆" sid="10"/>
        // <data name="BookStatus" value="已完成"/>
        // <data name="BookLength" value="105985"/>
        // <data name="LastUpdate" value="2012-11-02"/>
        // <data name="LatestSection" cid="41897"><![CDATA[第一卷 插图]]></data>
        // </metadata>

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            NovelItemMeta nfi = new NovelItemMeta();
            xmlPullParser.setInput(new StringReader(xml));
            int eventType = xmlPullParser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:

                        if ("metadata".equals(xmlPullParser.getName())) {
                            break;
                        } else if ("data".equals(xmlPullParser.getName())) {
                            if ("Title".equals(xmlPullParser.getAttributeValue(0))) {
                                nfi.aid = Integer.valueOf(xmlPullParser.getAttributeValue(1));
                                nfi.title = xmlPullParser.nextText();
                            } else if ("Author".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                nfi.author = xmlPullParser.getAttributeValue(1);
                            } else if ("DayHitsCount".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                nfi.dayHitsCount = Integer.valueOf(xmlPullParser.getAttributeValue(1));
                            } else if ("TotalHitsCount".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                nfi.totalHitsCount = Integer.valueOf(xmlPullParser.getAttributeValue(1));
                            } else if ("PushCount".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                nfi.pushCount = Integer.valueOf(xmlPullParser.getAttributeValue(1));
                            } else if ("FavCount".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                nfi.favCount = Integer.valueOf(xmlPullParser.getAttributeValue(1));
                            } else if ("PressId".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                nfi.pressId = xmlPullParser.getAttributeValue(1);
                            } else if ("BookStatus".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                nfi.bookStatus = xmlPullParser.getAttributeValue(1);
                            } else if ("BookLength".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                nfi.bookLength = Integer.valueOf(xmlPullParser.getAttributeValue(1));
                            } else if ("LastUpdate".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                nfi.lastUpdate = xmlPullParser.getAttributeValue(1);
                            } else if ("LatestSection".equals(xmlPullParser
                                    .getAttributeValue(0))) {
                                nfi.latestSectionCid = Integer.valueOf(xmlPullParser.getAttributeValue(1));
                                nfi.latestSectionName=xmlPullParser.nextText();
                            }
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }
            return nfi;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }


    static public ArrayList<VolumeList> getVolumeList(String xml) {
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            ArrayList<VolumeList> l = new ArrayList<>();
            VolumeList vl = null;
            ChapterInfo ci;
            xmlPullParser.setInput(new StringReader(xml));
            int eventType = xmlPullParser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:
                        if ("volume".equals(xmlPullParser.getName())) {
                            vl = new VolumeList();
                            vl.chapterList = new ArrayList<>();
                            vl.vid = Integer.valueOf(xmlPullParser.getAttributeValue(0));

                            // Here the returned text has some format error
                            // And I will handle them then
                            Log.v("MewX-XML", "+ " + vl.vid + "; ");
                        } else if ("chapter".equals(xmlPullParser.getName())) {
                            ci = new ChapterInfo();
                            ci.cid = Integer.valueOf(xmlPullParser.getAttributeValue(0));
                            ci.chapterName = xmlPullParser.nextText();
                            Log.v("MewX-XML", ci.cid + "; " + ci.chapterName);
                            if(vl != null) vl.chapterList.add(ci);
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("volume".equals(xmlPullParser.getName())) {
                            l.add(vl);
                            vl = null;
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }

            /** Handle the rest problem */
            // Problem like this:
            // <volume vid="41748"><![CDATA[第一卷 告白于苍刻之夜]]>
            // <chapter cid="41749"><![CDATA[序章]]></chapter>
            int currentIndex = 0;
            for (int i = 0; i < l.size(); i++) {
                currentIndex = xml.indexOf("volume", currentIndex);
                if (currentIndex != -1) {
                    currentIndex = xml.indexOf("CDATA[", currentIndex);
                    if (xml.indexOf("volume", currentIndex) != -1) {
                        int beg = currentIndex + 6;
                        int end = xml.indexOf("]]", currentIndex);

                        if (end != -1) {
                            l.get(i).volumeName = xml.substring(beg, end);
                            Log.v("MewX-XML", "+ " + l.get(i).volumeName + "; ");
                            currentIndex = end + 1;
                        } else
                            break;

                    } else
                        break;
                } else
                    break;
            }

            return l;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
