package org.mewx.wenku8.global.api;

import android.support.test.filters.SmallTest;

import org.junit.Assert;
import org.junit.Test;

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
                new GregorianCalendar(2013, 5, 25, 17, 16, 31).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 1);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, 5, 28, 18, 49, 16).getTime().getTime());
        Assert.assertEquals(review.getUid(), 81669);
        Assert.assertEquals(review.getUserName(), "老衲0轻音");
        Assert.assertEquals(review.getTitle(), "前排……");

        review = reviewList.getList().get(1);
        Assert.assertEquals(review.getRid(), 79826);
        Assert.assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, 5, 25, 23, 20, 2).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 4);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, 5, 27, 23, 42, 59).getTime().getTime());
        Assert.assertEquals(review.getUid(), 34924);
        Assert.assertEquals(review.getUserName(), "冒险奏鸣");
        Assert.assertEquals(review.getTitle(), "有种神曲奏界的既视感");
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
                new GregorianCalendar(2013, 5, 25, 17, 16, 31).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 1);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, 5, 28, 18, 49, 16).getTime().getTime());
        Assert.assertEquals(review.getUid(), 81669);
        Assert.assertEquals(review.getUserName(), "老衲0轻音");
        Assert.assertEquals(review.getTitle(), "前排……");

        review = reviewList.getList().get(1);
        Assert.assertEquals(review.getRid(), 79826);
        Assert.assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, 5, 25, 23, 20, 2).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 4);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, 5, 27, 23, 42, 59).getTime().getTime());
        Assert.assertEquals(review.getUid(), 34924);
        Assert.assertEquals(review.getUserName(), "冒险奏鸣");
        Assert.assertEquals(review.getTitle(), "有种神曲奏界的既视感");

        review = reviewList.getList().get(2);
        Assert.assertEquals(review.getRid(), 79801);
        Assert.assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, 5, 25, 17, 16, 31).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 1);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, 5, 28, 18, 49, 16).getTime().getTime());
        Assert.assertEquals(review.getUid(), 81670);
        Assert.assertEquals(review.getUserName(), "老衲0轻音");
        Assert.assertEquals(review.getTitle(), "前排……");

        review = reviewList.getList().get(3);
        Assert.assertEquals(review.getRid(), 79827);
        Assert.assertEquals(review.getPostTime().getTime(),
                new GregorianCalendar(2013, 5, 25, 23, 20, 2).getTime().getTime());
        Assert.assertEquals(review.getNoReplies(), 4);
        Assert.assertEquals(review.getLastReplyTime().getTime(),
                new GregorianCalendar(2013, 5, 27, 23, 42, 59).getTime().getTime());
        Assert.assertEquals(review.getUid(), 34925);
        Assert.assertEquals(review.getUserName(), "冒险奏鸣");
        Assert.assertEquals(review.getTitle(), "有种神曲奏界的既视感");
    }
}
