package org.mewx.wenku8.global.api;

import androidx.test.filters.SmallTest;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import static org.junit.Assert.*;

@SmallTest
public class Wenku8ParserTest {
    private final String REVIEW_LIST_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<metadata>\n" +
            "<page num='12'/>\n" +
            "\n" +
            "<item rid='79800' posttime='20130525171631' replies='1' replytime='20130528184916'>\n" +
            "<user uid='81669'><![CDATA[老衲0轻音]]></user>\n" +
            "<content><![CDATA[前排……]]></content>\n" +
            "</item>\n" +
            "\n" +
            "<item rid='79826' posttime='20130525232002' replies='4' replytime='20130527234259'>\n" +
            "<user uid='34924'><![CDATA[冒险奏鸣]]></user>\n" +
            "<content><![CDATA[有种神曲奏界的既视感]]></content>\n" +
            "</item>\n" +
            "\n" +
            "</metadata>";

    private final String REVIEW_REPLY_LIST_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<metadata>\n" +
            "<page num='2'/>\n" +
            "<item timestamp='20180713101811'>\n" +
            "<user uid='233516'><![CDATA[a1b2c3d4]]></user>\n" +
            "<content><![CDATA[嗯…………至少是一樓發完]]></content>\n" +
            "</item>\n" +
            "\n" +
            "<item timestamp='20180713135735'>\n" +
            "<user uid='230041'><![CDATA[156126]]></user>\n" +
            "<content><![CDATA[滑稽✧(`ῧ′)机智]]></content>\n" +
            "</item>\n" +
            "\n" +
            "</metadata>";

    @Test
    public void testParseNovelItemList() {
        final String XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<result>\n" +
                "<page num='166'/>\n" +
                "<item aid='1143'/>\n" +
                "<item aid='1034'/>\n" +
                "<item aid='1213'/>\n" +
                "<item aid='1'/>\n" +
                "<item aid='1011'/>\n" +
                "<item aid='1192'/>\n" +
                "<item aid='433'/>\n" +
                "<item aid='47'/>\n" +
                "<item aid='7'/>\n" +
                "<item aid='374'/>\n" +
                "</result>";

        List<Integer> list = Wenku8Parser.parseNovelItemList(XML);
        assertEquals(Arrays.asList(166, 1143, 1034, 1213, 1, 1011, 1192, 433, 47, 7, 374), list);
    }

    @Test
    public void testParseNovelItemListInvalid() {
        List<Integer> list = Wenku8Parser.parseNovelItemList("1234");
        assertTrue(list.isEmpty());
    }

    @Test
    public void testParseNovelFullMeta() {
        final String META_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<metadata>\n" +
                "<data name=\"Title\" aid=\"1306\"><![CDATA[向森之魔物献上花束(向森林的魔兽少女献花)]]></data>\n" +
                "<data name=\"Author\" value=\"小木君人\"/>\n" +
                "<data name=\"DayHitsCount\" value=\"26\"/>\n" +
                "<data name=\"TotalHitsCount\" value=\"43984\"/>\n" +
                "<data name=\"PushCount\" value=\"1735\"/>\n" +
                "<data name=\"FavCount\" value=\"848\"/>\n" +
                "<data name=\"PressId\" value=\"小学馆\" sid=\"10\"/>\n" +
                "<data name=\"BookStatus\" value=\"已完成\"/>\n" +
                "<data name=\"BookLength\" value=\"105985\"/>\n" +
                "<data name=\"LastUpdate\" value=\"2012-11-02\"/>\n" +
                "<data name=\"LatestSection\" cid=\"41897\"><![CDATA[第一卷 插图]]></data>\n" +
                "</metadata>";
        NovelItemMeta meta = Wenku8Parser.parseNovelFullMeta(META_XML);
        assertNotNull(meta);

        assertEquals(1306, meta.aid);
        assertEquals("向森之魔物献上花束(向森林的魔兽少女献花)", meta.title);
        assertEquals("小木君人", meta.author);
        assertEquals(26, meta.dayHitsCount);
        assertEquals(43984, meta.totalHitsCount);
        assertEquals(1735, meta.pushCount);
        assertEquals(848, meta.favCount);
        assertEquals("小学馆", meta.pressId);
        assertEquals("已完成", meta.bookStatus);
        assertEquals(105985, meta.bookLength);
        assertEquals("2012-11-02", meta.lastUpdate);
        assertEquals(41897, meta.latestSectionCid);
        assertEquals("第一卷 插图", meta.latestSectionName);
    }

    @Test
    public void testParseNovelFullMetaInvalid() {
        NovelItemMeta meta = Wenku8Parser.parseNovelFullMeta("1234");
        assertNull(meta);
    }

    @Test
    public void testGetVolumeList() {
        final String XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<package>\n" +
                "<volume vid=\"41748\"><![CDATA[第一卷 告白于苍刻之夜]]>\n" +
                "<chapter cid=\"41749\"><![CDATA[序章]]></chapter>\n" +
                "<chapter cid=\"41750\"><![CDATA[第一章「去对我的『楯』说吧——」]]></chapter>\n" +
                "<chapter cid=\"41751\"><![CDATA[第二章「我真的对你非常感兴趣」]]></chapter>\n" +
                "<chapter cid=\"41752\"><![CDATA[第三章「揍我吧！」]]></chapter>\n" +
                "<chapter cid=\"41753\"><![CDATA[第四章「下次，再来喝苹果茶」]]></chapter>\n" +
                "<chapter cid=\"41754\"><![CDATA[第五章「这是约定」]]></chapter>\n" +
                "<chapter cid=\"41755\"><![CDATA[第六章「你的背后——由我来守护！」]]></chapter>\n" +
                "<chapter cid=\"41756\"><![CDATA[第七章「茱莉——爱交给你！」]]></chapter>\n" +
                "<chapter cid=\"41757\"><![CDATA[尾声]]></chapter>\n" +
                "<chapter cid=\"41758\"><![CDATA[后记]]></chapter>\n" +
                "<chapter cid=\"41759\"><![CDATA[插图]]></chapter>\n" +
                "</volume>\n" +
                "<volume vid=\"45090\"><![CDATA[第二卷 谎言、真相与赤红]]>\n" +
                "<chapter cid=\"45091\"><![CDATA[序章]]></chapter>\n" +
                "<chapter cid=\"45092\"><![CDATA[第一章「莉莉丝·布里斯托」]]></chapter>\n" +
                "</volume>\n" +
                "</package>";

        List<VolumeList> volumeLists = Wenku8Parser.getVolumeList(XML);
        assertEquals(2, volumeLists.size());

        // ----
        VolumeList vList = volumeLists.get(0);
        assertEquals(41748, vList.vid);
        assertEquals("第一卷 告白于苍刻之夜", vList.volumeName);

        assertEquals(11, vList.chapterList.size());
        ChapterInfo cInfo = vList.chapterList.get(0);
        assertEquals(41749, cInfo.cid);
        assertEquals("序章", cInfo.chapterName);
        cInfo = vList.chapterList.get(1);
        assertEquals(41750, cInfo.cid);
        assertEquals("第一章「去对我的『楯』说吧——」", cInfo.chapterName);
        cInfo = vList.chapterList.get(2);
        assertEquals(41751, cInfo.cid);
        assertEquals("第二章「我真的对你非常感兴趣」", cInfo.chapterName);
        cInfo = vList.chapterList.get(3);
        assertEquals(41752, cInfo.cid);
        assertEquals("第三章「揍我吧！」", cInfo.chapterName);
        cInfo = vList.chapterList.get(4);
        assertEquals(41753, cInfo.cid);
        assertEquals("第四章「下次，再来喝苹果茶」", cInfo.chapterName);
        cInfo = vList.chapterList.get(5);
        assertEquals(41754, cInfo.cid);
        assertEquals("第五章「这是约定」", cInfo.chapterName);
        cInfo = vList.chapterList.get(6);
        assertEquals(41755, cInfo.cid);
        assertEquals("第六章「你的背后——由我来守护！」", cInfo.chapterName);
        cInfo = vList.chapterList.get(7);
        assertEquals(41756, cInfo.cid);
        assertEquals("第七章「茱莉——爱交给你！」", cInfo.chapterName);
        cInfo = vList.chapterList.get(8);
        assertEquals(41757, cInfo.cid);
        assertEquals("尾声", cInfo.chapterName);
        cInfo = vList.chapterList.get(9);
        assertEquals(41758, cInfo.cid);
        assertEquals("后记", cInfo.chapterName);
        cInfo = vList.chapterList.get(10);
        assertEquals(41759, cInfo.cid);
        assertEquals("插图", cInfo.chapterName);


        // ----
        vList = volumeLists.get(1);
        assertEquals(45090, vList.vid);
        assertEquals("第二卷 谎言、真相与赤红", vList.volumeName);

        assertEquals(2, vList.chapterList.size());
        cInfo = vList.chapterList.get(0);
        assertEquals(45091, cInfo.cid);
        assertEquals("序章", cInfo.chapterName);
        cInfo = vList.chapterList.get(1);
        assertEquals(45092, cInfo.cid);
        assertEquals("第一章「莉莉丝·布里斯托」", cInfo.chapterName);
    }

    @Test
    public void testGetVolumeListInvalid() {
        List<VolumeList> volumeLists = Wenku8Parser.getVolumeList("1234");
        assertTrue(volumeLists.isEmpty());
    }

    @Test
    public void testParseReviewList() {
        ReviewList reviewList = new ReviewList();
        Wenku8Parser.parseReviewList(reviewList, REVIEW_LIST_XML);

        assertEquals(reviewList.getTotalPage(), 12);
        assertEquals(reviewList.getCurrentPage(), 1);
        assertEquals(reviewList.getList().size(), 2);
        ReviewList.Review review = reviewList.getList().get(0);
        assertEquals(review.getRid(), 79800);
        assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 17, 16, 31).getTime().getTime());
        assertEquals(review.getNoReplies(), 1);
        assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 28, 18, 49, 16).getTime().getTime());
        assertEquals(review.getUid(), 81669);
        assertEquals(review.getUserName(), "老衲0轻音");
        assertEquals(review.getTitle(), "前排……");

        review = reviewList.getList().get(1);
        assertEquals(review.getRid(), 79826);
        assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 23, 20, 2).getTime().getTime());
        assertEquals(review.getNoReplies(), 4);
        assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 27, 23, 42, 59).getTime().getTime());
        assertEquals(review.getUid(), 34924);
        assertEquals(review.getUserName(), "冒险奏鸣");
        assertEquals(review.getTitle(), "有种神曲奏界的既视感");
    }

    @Test
    public void testParseReviewListInvalid() {
        ReviewList reviewList = new ReviewList();
        Wenku8Parser.parseReviewList(reviewList, "1324");
        assertTrue(reviewList.getList().isEmpty());
    }

    @Test
    public void testParseReviewListPageTwo() {
        final String REVIEW_LIST_XML_PAGE_2 = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<metadata>\n" +
                "<page num='12'/>\n" +
                "\n" +
                "<item rid='79801' posttime='20130525171631' replies='1' replytime='20130528184916'>\n" + // rid changed
                "<user uid='81670'><![CDATA[老衲0轻音]]></user>\n" + // uid changed
                "<content><![CDATA[前排……]]></content>\n" +
                "</item>\n" +
                "\n" +
                "<item rid='79827' posttime='20130525232002' replies='4' replytime='20130527234259'>\n" + // rid changed
                "<user uid='34925'><![CDATA[冒险奏鸣]]></user>\n" + // uid changed
                "<content><![CDATA[有种神曲奏界的既视感]]></content>\n" +
                "</item>\n" +
                "\n" +
                "</metadata>";

        ReviewList reviewList = new ReviewList();
        Wenku8Parser.parseReviewList(reviewList, REVIEW_LIST_XML);
        Wenku8Parser.parseReviewList(reviewList, REVIEW_LIST_XML_PAGE_2);

        assertEquals(reviewList.getTotalPage(), 12);
        assertEquals(reviewList.getCurrentPage(), 2);
        assertEquals(reviewList.getList().size(), 4);

        ReviewList.Review review = reviewList.getList().get(0);
        assertEquals(review.getRid(), 79800);
        assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 17, 16, 31).getTime().getTime());
        assertEquals(review.getNoReplies(), 1);
        assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 28, 18, 49, 16).getTime().getTime());
        assertEquals(review.getUid(), 81669);
        assertEquals(review.getUserName(), "老衲0轻音");
        assertEquals(review.getTitle(), "前排……");

        review = reviewList.getList().get(1);
        assertEquals(review.getRid(), 79826);
        assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 23, 20, 2).getTime().getTime());
        assertEquals(review.getNoReplies(), 4);
        assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 27, 23, 42, 59).getTime().getTime());
        assertEquals(review.getUid(), 34924);
        assertEquals(review.getUserName(), "冒险奏鸣");
        assertEquals(review.getTitle(), "有种神曲奏界的既视感");

        review = reviewList.getList().get(2);
        assertEquals(review.getRid(), 79801);
        assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 17, 16, 31).getTime().getTime());
        assertEquals(review.getNoReplies(), 1);
        assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 28, 18, 49, 16).getTime().getTime());
        assertEquals(review.getUid(), 81670);
        assertEquals(review.getUserName(), "老衲0轻音");
        assertEquals(review.getTitle(), "前排……");

        review = reviewList.getList().get(3);
        assertEquals(review.getRid(), 79827);
        assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 23, 20, 2).getTime().getTime());
        assertEquals(review.getNoReplies(), 4);
        assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 27, 23, 42, 59).getTime().getTime());
        assertEquals(review.getUid(), 34925);
        assertEquals(review.getUserName(), "冒险奏鸣");
        assertEquals(review.getTitle(), "有种神曲奏界的既视感");
    }

    @Test
    public void testParseReviewReplyList() {
        ReviewReplyList reviewReplyList = new ReviewReplyList();
        Wenku8Parser.parseReviewReplyList(reviewReplyList, REVIEW_REPLY_LIST_XML);

        assertEquals(reviewReplyList.getTotalPage(), 2);
        assertEquals(reviewReplyList.getCurrentPage(), 1);
        assertEquals(reviewReplyList.getList().size(), 2);

        ReviewReplyList.ReviewReply reviewReply = reviewReplyList.getList().get(0);
        assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 10, 18, 11).getTime().getTime());
        assertEquals(reviewReply.getUid(), 233516);
        assertEquals(reviewReply.getUserName(), "a1b2c3d4");
        assertEquals(reviewReply.getContent(), "嗯…………至少是一樓發完");

        reviewReply = reviewReplyList.getList().get(1);
        assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 13, 57, 35).getTime().getTime());
        assertEquals(reviewReply.getUid(), 230041);
        assertEquals(reviewReply.getUserName(), "156126");
        assertEquals(reviewReply.getContent(), "滑稽✧(`ῧ′)机智");
    }

    @Test
    public void testParseReviewReplyListInvalid() {
        ReviewReplyList reviewReplyList = new ReviewReplyList();
        Wenku8Parser.parseReviewReplyList(reviewReplyList, "1234");
        assertTrue(reviewReplyList.getList().isEmpty());
    }

    @Test
    public void testParseReviewReplyListPage2() {
        final String REVIEW_REPLY_LIST_XML_PAGE_2 = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<metadata>\n" +
                "<page num='2'/>\n" +
                "<item timestamp='20180713101811'>\n" +
                "<user uid='233517'><![CDATA[a1b2c3d4]]></user>\n" + // changed
                "<content><![CDATA[嗯…………至少是一樓發完]]></content>\n" +
                "</item>\n" +
                "\n" +
                "<item timestamp='20180713135735'>\n" +
                "<user uid='230042'><![CDATA[156126]]></user>\n" + // changed
                "<content><![CDATA[滑稽✧(`ῧ′)机智]]></content>\n" +
                "</item>\n" +
                "\n" +
                "</metadata>";

        ReviewReplyList reviewReplyList = new ReviewReplyList();
        Wenku8Parser.parseReviewReplyList(reviewReplyList, REVIEW_REPLY_LIST_XML);
        Wenku8Parser.parseReviewReplyList(reviewReplyList, REVIEW_REPLY_LIST_XML_PAGE_2);

        assertEquals(reviewReplyList.getTotalPage(), 2);
        assertEquals(reviewReplyList.getCurrentPage(), 2);
        assertEquals(reviewReplyList.getList().size(), 4);

        ReviewReplyList.ReviewReply reviewReply = reviewReplyList.getList().get(0);
        assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 10, 18, 11).getTime().getTime());
        assertEquals(reviewReply.getUid(), 233516);
        assertEquals(reviewReply.getUserName(), "a1b2c3d4");
        assertEquals(reviewReply.getContent(), "嗯…………至少是一樓發完");

        reviewReply = reviewReplyList.getList().get(1);
        assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 13, 57, 35).getTime().getTime());
        assertEquals(reviewReply.getUid(), 230041);
        assertEquals(reviewReply.getUserName(), "156126");
        assertEquals(reviewReply.getContent(), "滑稽✧(`ῧ′)机智");

        reviewReply = reviewReplyList.getList().get(2);
        assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 10, 18, 11).getTime().getTime());
        assertEquals(reviewReply.getUid(), 233517);
        assertEquals(reviewReply.getUserName(), "a1b2c3d4");
        assertEquals(reviewReply.getContent(), "嗯…………至少是一樓發完");

        reviewReply = reviewReplyList.getList().get(3);
        assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 13, 57, 35).getTime().getTime());
        assertEquals(reviewReply.getUid(), 230042);
        assertEquals(reviewReply.getUserName(), "156126");
        assertEquals(reviewReply.getContent(), "滑稽✧(`ῧ′)机智");
    }
}
