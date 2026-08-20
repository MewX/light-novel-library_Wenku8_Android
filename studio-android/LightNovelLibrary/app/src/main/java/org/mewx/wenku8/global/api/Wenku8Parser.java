package org.mewx.wenku8.global.api;

import androidx.annotation.NonNull;
import android.util.Log;

import org.mewx.wenku8.util.LightTool;
import org.mewx.wenku8.util.CrashReporter;
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

    /**
     * Parse a novel list response into (total page, aid, aid, ...).
     *
     * Element 0 is the page count and the caller removes it; 0 means "unknown", which
     * NovelItemListFragment already treats as "keep paging".
     *
     * <pre>
     * &lt;?xml version="1.0" encoding="utf-8"?&gt;
     * &lt;result&gt;
     * &lt;page num='166'/&gt;
     * &lt;item aid='1143'/&gt;
     * &lt;item aid='1034'/&gt;
     * &lt;/result&gt;
     * </pre>
     *
     * gives { 166, 1143, 1034 }.
     */
    @NonNull
    public static List<Integer> parseNovelItemList(@NonNull String str) {
        List<Integer> list = parseNovelItemListAsXml(str);
        if (!list.isEmpty()) return list;

        // XmlPullParser stops at the first byte of anything that is not the document, so a
        // response that is well-formed only after some leading noise -- a PHP notice, a relay
        // banner -- parses to nothing, where the quoted-value scan this replaced read straight
        // past it. Retry from the document start rather than keeping that scan as a fallback:
        // it cannot tell an aid from any other quoted integer, so recovering the list that way
        // would put the phantom novels back on exactly the responses nobody can see.
        int documentStart = indexOfDocumentStart(str);
        if (documentStart > 0) {
            list = parseNovelItemListAsXml(str.substring(documentStart));
            if (!list.isEmpty()) {
                // How we find out whether this case is real. If a release goes by without this
                // breadcrumb appearing, delete the retry and indexOfDocumentStart with it.
                CrashReporter.log("parseNovelItemList: parsed after skipping " + documentStart
                        + " leading bytes, length=" + str.length());
            }
        }
        return list;
    }

    /**
     * Parse a novel list response into (total page, aid, aid, ...), or an empty list if it is
     * not a novel list response at all.
     *
     * Element 0 is the page count and the caller removes it; 0 means "unknown", which
     * NovelItemListFragment already treats as "keep paging".
     *
     * <pre>
     * &lt;?xml version="1.0" encoding="utf-8"?&gt;
     * &lt;result&gt;
     * &lt;page num='166'/&gt;
     * &lt;item aid='1143'/&gt;
     * &lt;item aid='1034'/&gt;
     * &lt;/result&gt;
     * </pre>
     *
     * gives { 166, 1143, 1034 }.
     */
    @NonNull
    private static List<Integer> parseNovelItemListAsXml(@NonNull String xml) {
        int totalPage = 0;
        boolean foundPage = false;
        List<Integer> aids = new ArrayList<>();

        // Read by attribute name rather than by scanning for quoted values. The scan this
        // replaced took any single-quoted value in document order, so it could not tell an
        // aid from anything else quoted: <page num='166' cached='2'/> yielded a phantom novel
        // 2, and <item type='9' aid='1143'/> a phantom novel 9. Since the API now sits behind
        // a relay, an added attribute is a live possibility rather than a hypothetical, and
        // it would have been invisible -- phantom aids fail to load one by one rather than
        // failing as a list.
        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xmlPullParser = factory.newPullParser();
            xmlPullParser.setInput(new StringReader(xml));
            int eventType = xmlPullParser.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if ("page".equals(xmlPullParser.getName())) {
                        foundPage = true;
                        String num = xmlPullParser.getAttributeValue(null, "num");
                        if (num != null && LightTool.isInteger(num)) {
                            totalPage = Integer.parseInt(num);
                        }
                    } else if ("item".equals(xmlPullParser.getName())) {
                        String aid = xmlPullParser.getAttributeValue(null, "aid");
                        if (aid != null && LightTool.isInteger(aid)) {
                            aids.add(Integer.parseInt(aid));
                            Log.v("MewX", "Add novel aid: " + aid);
                        }
                    }
                }
                eventType = xmlPullParser.next();
            }
        } catch (Exception e) {
            // Deliberately not rethrown, and deliberately not discarding what was collected: a
            // response truncated mid-list still yields the items ahead of the cut, as the scan
            // did, so the caller renders a short list rather than an error. When nothing was
            // collected the caller retries from the document start.
            CrashReporter.recordException("Wenku8Parser.parseNovelItemList", e);
        }

        if (aids.isEmpty() && !foundPage) {
            // Not a novel list response -- an HTML error page, a captive-portal interstitial,
            // or a document that never started. Empty is what the caller already handles.
            return new ArrayList<>();
        }

        List<Integer> list = new ArrayList<>();
        list.add(totalPage);
        list.addAll(aids);
        return list;
    }

    /**
     * Index of the first byte of the XML document within a response, or -1 if no start marker
     * is present. Only used to skip leading noise ahead of an otherwise well-formed response.
     */
    private static int indexOfDocumentStart(@NonNull String str) {
        int declaration = str.indexOf("<?xml");
        return declaration != -1 ? declaration : str.indexOf("<result");
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
            // Tracked explicitly rather than by inspecting nfi afterwards: NovelItemMeta's
            // constructor pre-fills every field (title defaults to "1", author to UNKNOWN),
            // so a never-populated object is indistinguishable from a populated one by value.
            boolean foundTitle = false;
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
                                foundTitle = true;
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

            // See UserInfo.parseUserInfo: well-formed XML that is not a novel-metadata
            // response used to come back as a default-constructed NovelItemMeta rather than
            // as null, which reaches the UI as a novel titled "1" by an unknown author. aid is
            // read in the same branch as the title, so this one flag covers both.
            if (!foundTitle) {
                CrashReporter.log("parseNovelFullMeta: well-formed XML with no Title data, "
                        + "length=" + (xml == null ? -1 : xml.length()));
                return null;
            }
            return nfi;
        } catch (Exception e) {
            CrashReporter.recordException("Wenku8Parser.parseNovelFullMeta", e);
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
            CrashReporter.recordException("Wenku8Parser.getVolumeList", e);
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
            CrashReporter.recordException("Wenku8Parser.parseReviewList", e);
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
            CrashReporter.recordException("Wenku8Parser.parseReviewReplyList", e);
        }
    }

}
