package org.mewx.wenku8.global.api;

import android.support.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

import java.util.Calendar;
import java.util.GregorianCalendar;

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

    // TODO: more tests

    @Test
    public void testParseReviewList() {
        ReviewList reviewList = new ReviewList();
        Wenku8Parser.parseReviewList(reviewList, REVIEW_LIST_XML);

        Assert.assertEquals(reviewList.getTotalPage(), 12);
        Assert.assertEquals(reviewList.getCurrentPage(), 1);
        Assert.assertEquals(reviewList.getList().size(), 2);
        ReviewList.Review review = reviewList.getList().get(0);
        Assert.assertEquals(review.getRid(), 79800);
        Assert.assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 17, 16, 31).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 1);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 28, 18, 49, 16).getTime().getTime());
        Assert.assertEquals(review.getUid(), 81669);
        Assert.assertEquals(review.getUserName(), "老衲0轻音");
        Assert.assertEquals(review.getTitle(), "前排……");

        review = reviewList.getList().get(1);
        Assert.assertEquals(review.getRid(), 79826);
        Assert.assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 23, 20, 2).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 4);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 27, 23, 42, 59).getTime().getTime());
        Assert.assertEquals(review.getUid(), 34924);
        Assert.assertEquals(review.getUserName(), "冒险奏鸣");
        Assert.assertEquals(review.getTitle(), "有种神曲奏界的既视感");
    }

    @Test
    public void testParseReviewListInvalid() {
        ReviewList reviewList = new ReviewList();
        Wenku8Parser.parseReviewList(reviewList, "1324");
        Assert.assertTrue(reviewList.getList().isEmpty());
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

        Assert.assertEquals(reviewList.getTotalPage(), 12);
        Assert.assertEquals(reviewList.getCurrentPage(), 2);
        Assert.assertEquals(reviewList.getList().size(), 4);

        ReviewList.Review review = reviewList.getList().get(0);
        Assert.assertEquals(review.getRid(), 79800);
        Assert.assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 17, 16, 31).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 1);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 28, 18, 49, 16).getTime().getTime());
        Assert.assertEquals(review.getUid(), 81669);
        Assert.assertEquals(review.getUserName(), "老衲0轻音");
        Assert.assertEquals(review.getTitle(), "前排……");

        review = reviewList.getList().get(1);
        Assert.assertEquals(review.getRid(), 79826);
        Assert.assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 23, 20, 2).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 4);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 27, 23, 42, 59).getTime().getTime());
        Assert.assertEquals(review.getUid(), 34924);
        Assert.assertEquals(review.getUserName(), "冒险奏鸣");
        Assert.assertEquals(review.getTitle(), "有种神曲奏界的既视感");

        review = reviewList.getList().get(2);
        Assert.assertEquals(review.getRid(), 79801);
        Assert.assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 17, 16, 31).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 1);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 28, 18, 49, 16).getTime().getTime());
        Assert.assertEquals(review.getUid(), 81670);
        Assert.assertEquals(review.getUserName(), "老衲0轻音");
        Assert.assertEquals(review.getTitle(), "前排……");

        review = reviewList.getList().get(3);
        Assert.assertEquals(review.getRid(), 79827);
        Assert.assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 25, 23, 20, 2).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 4);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, Calendar.MAY, 27, 23, 42, 59).getTime().getTime());
        Assert.assertEquals(review.getUid(), 34925);
        Assert.assertEquals(review.getUserName(), "冒险奏鸣");
        Assert.assertEquals(review.getTitle(), "有种神曲奏界的既视感");
    }

    @Test
    public void testParseReviewReplyList() {
        ReviewReplyList reviewReplyList = new ReviewReplyList();
        Wenku8Parser.parseReviewReplyList(reviewReplyList, REVIEW_REPLY_LIST_XML);

        Assert.assertEquals(reviewReplyList.getTotalPage(), 2);
        Assert.assertEquals(reviewReplyList.getCurrentPage(), 1);
        Assert.assertEquals(reviewReplyList.getList().size(), 2);

        ReviewReplyList.ReviewReply reviewReply = reviewReplyList.getList().get(0);
        Assert.assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 10, 18, 11).getTime().getTime());
        Assert.assertEquals(reviewReply.getUid(), 233516);
        Assert.assertEquals(reviewReply.getUserName(), "a1b2c3d4");
        Assert.assertEquals(reviewReply.getContent(), "嗯…………至少是一樓發完");

        reviewReply = reviewReplyList.getList().get(1);
        Assert.assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 13, 57, 35).getTime().getTime());
        Assert.assertEquals(reviewReply.getUid(), 230041);
        Assert.assertEquals(reviewReply.getUserName(), "156126");
        Assert.assertEquals(reviewReply.getContent(), "滑稽✧(`ῧ′)机智");
    }

    @Test
    public void testParseReviewReplyListInvalid() {
        ReviewReplyList reviewReplyList = new ReviewReplyList();
        Wenku8Parser.parseReviewReplyList(reviewReplyList, "1234");
        Assert.assertTrue(reviewReplyList.getList().isEmpty());
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

        Assert.assertEquals(reviewReplyList.getTotalPage(), 2);
        Assert.assertEquals(reviewReplyList.getCurrentPage(), 2);
        Assert.assertEquals(reviewReplyList.getList().size(), 4);

        ReviewReplyList.ReviewReply reviewReply = reviewReplyList.getList().get(0);
        Assert.assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 10, 18, 11).getTime().getTime());
        Assert.assertEquals(reviewReply.getUid(), 233516);
        Assert.assertEquals(reviewReply.getUserName(), "a1b2c3d4");
        Assert.assertEquals(reviewReply.getContent(), "嗯…………至少是一樓發完");

        reviewReply = reviewReplyList.getList().get(1);
        Assert.assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 13, 57, 35).getTime().getTime());
        Assert.assertEquals(reviewReply.getUid(), 230041);
        Assert.assertEquals(reviewReply.getUserName(), "156126");
        Assert.assertEquals(reviewReply.getContent(), "滑稽✧(`ῧ′)机智");

        reviewReply = reviewReplyList.getList().get(2);
        Assert.assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 10, 18, 11).getTime().getTime());
        Assert.assertEquals(reviewReply.getUid(), 233517);
        Assert.assertEquals(reviewReply.getUserName(), "a1b2c3d4");
        Assert.assertEquals(reviewReply.getContent(), "嗯…………至少是一樓發完");

        reviewReply = reviewReplyList.getList().get(3);
        Assert.assertEquals(reviewReply.getReplyTime().getTime(),
                new GregorianCalendar(2018, Calendar.JULY, 13, 13, 57, 35).getTime().getTime());
        Assert.assertEquals(reviewReply.getUid(), 230042);
        Assert.assertEquals(reviewReply.getUserName(), "156126");
        Assert.assertEquals(reviewReply.getContent(), "滑稽✧(`ῧ′)机智");
    }
}
