package org.mewx.wenku8.global.api;

import androidx.annotation.NonNull;
import android.util.Log;

import org.mewx.wenku8.util.LightTool;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

/**
 * Created by MewX on 2015/4/21.
 * Wenku8 parsers.
 */
public class Wenku8Parser {

    @NonNull
    public static List<Integer> parseNovelItemList(@NonNull String str) {
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

        final char SEPARATOR = '\''; // seperator

        // get total page
        int beg, temp;
        beg = str.indexOf(SEPARATOR);
        temp = str.indexOf(SEPARATOR, beg + 1);
        if (beg == -1 || temp == -1) return list; // empty, this is an exception
        if(LightTool.isInteger(str.substring(beg + 1, temp)))
            list.add(Integer.parseInt(str.substring(beg + 1, temp)));
        beg = temp + 1; // prepare for loop

        // init array
        while (true) {
            beg = str.indexOf(SEPARATOR, beg);
            temp = str.indexOf(SEPARATOR, beg + 1);
            if (beg == -1 || temp == -1) break;

            if(LightTool.isInteger(str.substring(beg + 1, temp)))
                list.add(Integer.parseInt(str.substring(beg + 1, temp)));
            Log.v("MewX", "Add novel aid: " + list.get(list.size() - 1));

            beg = temp + 1; // prepare for next round
        }

        return list;
    }


    static public NovelItemMeta parseNovelFullMeta(String xml) {
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
        Log.d(Wenku8Parser.class.getSimpleName(), xml);

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


    @NonNull
    static public ArrayList<VolumeList> getVolumeList(@NonNull String xml) {
        ArrayList<VolumeList> l = new ArrayList<>();
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
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

            /* Handle the rest problem */
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
        } catch (Exception e) {
            e.printStackTrace();
        }
        return l;
    }

    /**
     * save the new xsl into an existing review list
     * @param reviewList the existing review list object
     * @param xml the fetched xml
     */
    static public void parseReviewList(ReviewList reviewList, String xml) {
        reviewList.setCurrentPage(reviewList.getCurrentPage() + 1);

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            xmlPullParser.setInput(new StringReader(xml));
            int eventType = xmlPullParser.getEventType();

            int rid = 0; // review id
            Date postTime = new Date();
            int noReplies = 0;
            Date lastReplyTime = new Date();
            String userName = "";
            int uid = 0; // post user
            String title = ""; // review title

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:
                        if ("page".equals(xmlPullParser.getName())) {
                            reviewList.setTotalPage(Integer.valueOf(xmlPullParser.getAttributeValue(null, "num")));
                        } else if ("item".equals(xmlPullParser.getName())) {
                            rid = Integer.valueOf(xmlPullParser.getAttributeValue(null, "rid"));
                            noReplies = Integer.valueOf(xmlPullParser.getAttributeValue(null, "replies"));
                            String postTimeStr = xmlPullParser.getAttributeValue(null, "posttime");
                            postTime = new GregorianCalendar(
                                    Integer.valueOf(postTimeStr.substring(0, 4), 10),
                                    Integer.valueOf(postTimeStr.substring(4, 6), 10) - 1, // start from 0 - Calendar.JANUARY
                                    Integer.valueOf(postTimeStr.substring(6, 8), 10),
                                    Integer.valueOf(postTimeStr.substring(8, 10), 10),
                                    Integer.valueOf(postTimeStr.substring(10, 12), 10),
                                    Integer.valueOf(postTimeStr.substring(12), 10)
                            ).getTime();
                            String replyTimeStr = xmlPullParser.getAttributeValue(null, "replytime");
                            lastReplyTime = new GregorianCalendar(
                                    Integer.valueOf(replyTimeStr.substring(0, 4), 10),
                                    Integer.valueOf(replyTimeStr.substring(4, 6), 10) - 1,
                                    Integer.valueOf(replyTimeStr.substring(6, 8), 10),
                                    Integer.valueOf(replyTimeStr.substring(8, 10), 10),
                                    Integer.valueOf(replyTimeStr.substring(10, 12), 10),
                                    Integer.valueOf(replyTimeStr.substring(12), 10)
                            ).getTime();
                        } else if ("user".equals(xmlPullParser.getName())) {
                            uid = Integer.valueOf(xmlPullParser.getAttributeValue(null, "uid"));
                            userName = xmlPullParser.nextText();
                        } else if ("content".equals(xmlPullParser.getName())) {
                            title = xmlPullParser.nextText().trim();
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("item".equals(xmlPullParser.getName())) {
                            reviewList.getList().add(
                                    new ReviewList.Review(rid, postTime, noReplies, lastReplyTime, userName, uid, title));
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * save the new xsl into an existing review reply list
     * @param reviewReplyList the existing review reply list object
     * @param xml the fetched xml
     */
    static public void parseReviewReplyList(ReviewReplyList reviewReplyList, String xml) {
        reviewReplyList.setCurrentPage(reviewReplyList.getCurrentPage() + 1);

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            xmlPullParser.setInput(new StringReader(xml));
            int eventType = xmlPullParser.getEventType();

            Date replyTime = new Date();
            String userName = "";
            int uid = 0; // post user
            String content = "";

            while (eventType != XmlPullParser.END_DOCUMENT) {
                switch (eventType) {
                    case XmlPullParser.START_DOCUMENT:
                        break;

                    case XmlPullParser.START_TAG:
                        if ("page".equals(xmlPullParser.getName())) {
                            reviewReplyList.setTotalPage(Integer.valueOf(xmlPullParser.getAttributeValue(null, "num")));
                        } else if ("item".equals(xmlPullParser.getName())) {
                            String replyTimeStr = xmlPullParser.getAttributeValue(null, "timestamp");
                            replyTime = new GregorianCalendar(
                                    Integer.valueOf(replyTimeStr.substring(0, 4), 10),
                                    Integer.valueOf(replyTimeStr.substring(4, 6), 10) - 1, // start from 0 - Calendar.JANUARY
                                    Integer.valueOf(replyTimeStr.substring(6, 8), 10),
                                    Integer.valueOf(replyTimeStr.substring(8, 10), 10),
                                    Integer.valueOf(replyTimeStr.substring(10, 12), 10),
                                    Integer.valueOf(replyTimeStr.substring(12), 10)
                            ).getTime();
                        } else if ("user".equals(xmlPullParser.getName())) {
                            uid = Integer.valueOf(xmlPullParser.getAttributeValue(null, "uid"));
                            userName = xmlPullParser.nextText();
                        } else if ("content".equals(xmlPullParser.getName())) {
                            content = xmlPullParser.nextText().trim();
                        }
                        break;

                    case XmlPullParser.END_TAG:
                        if ("item".equals(xmlPullParser.getName())) {
                            reviewReplyList.getList().add(
                                    new ReviewReplyList.ReviewReply(replyTime, userName, uid, content));
                        }
                        break;
                }
                eventType = xmlPullParser.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
