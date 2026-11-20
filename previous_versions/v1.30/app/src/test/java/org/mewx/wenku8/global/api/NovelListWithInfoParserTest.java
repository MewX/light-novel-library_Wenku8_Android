package org.mewx.wenku8.global.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

// Robolectric is required: this parser uses XmlPullParser, which is a no-op stub under the
// plain JVM test runtime and makes every parse silently return null.
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class NovelListWithInfoParserTest {
    // Trimmed from a live novellist response. Note the shapes the server actually mixes: single
    // and double quoted attribute names, Title and IntroPreview as CDATA, everything else as a
    // value attribute, and blank lines between items.
    private static final String XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<result>\n" +
            "<page num='417'/>\n" +
            "\n" +
            "<item aid='4346'>\n" +
            "<data name='Title'><![CDATA[看不见的规则]]></data>\n" +
            "<data name='TotalHitsCount' value='51'/>\n" +
            "<data name='PushCount' value='1'/>\n" +
            "<data name='FavCount' value='0'/>\n" +
            "<data name=\"Author\" value='佐藤いつ子'/>\n" +
            "<data name='BookStatus' value='已完结'/>\n" +
            "<data name=\"LastUpdate\" value='2026-08-23'/>\n" +
            "<data name=\"Tags\" value='校园 青春 女性视角'/>\n" +
            "<data name=\"IntroPreview\"><![CDATA[平凡的国中生优希，为了维持在小团体里的位置]]></data>\n" +
            "</item>\n" +
            "\n" +
            "<item aid='3092'>\n" +
            "<data name='Title'><![CDATA[请别来管我]]></data>\n" +
            "<data name='TotalHitsCount' value='145183'/>\n" +
            "<data name='PushCount' value='1965'/>\n" +
            "<data name='FavCount' value='4654'/>\n" +
            "<data name=\"Author\" value='相崎壁际'/>\n" +
            "<data name='BookStatus' value='连载中'/>\n" +
            "<data name=\"LastUpdate\" value='2026-08-23'/>\n" +
            "<data name=\"Tags\" value='穿越 校园 青春'/>\n" +
            "<data name=\"IntroPreview\"><![CDATA[「开个脱孤的作战会议吧。」\r\n这是个关于我与]]></data>\n" +
            "</item>\n" +
            "\n" +
            "</result>";

    @Test
    public void parseNullOrEmpty() {
        assertNull(NovelListWithInfoParser.parse(null));
        assertNull(NovelListWithInfoParser.parse(""));
    }

    @Test
    public void parseNonXml() {
        assertNull(NovelListWithInfoParser.parse("1234"));
        assertNull(NovelListWithInfoParser.parse("{\"page_num\": 1}"));
    }

    @Test
    public void parseTruncatedXml() {
        // A response cut off mid-stream must not yield a half-built page.
        assertNull(NovelListWithInfoParser.parse(XML.substring(0, XML.length() / 2)));
    }

    @Test
    public void parsePageNum() {
        NovelListWithInfoParser.Result result = NovelListWithInfoParser.parse(XML);

        assertNotNull(result);
        assertEquals(417, result.pageNum);
    }

    @Test
    public void parseItems() {
        NovelListWithInfoParser.Result result = NovelListWithInfoParser.parse(XML);

        assertNotNull(result);
        assertEquals(2, result.items.size());

        NovelItemInfoUpdate info = result.items.get(0);
        assertEquals(4346, info.aid);
        assertEquals("看不见的规则", info.title);
        assertEquals("佐藤いつ子", info.author);
        assertEquals("已完结", info.status);
        assertEquals("2026-08-23", info.update);
        assertEquals("校园 青春 女性视角", info.tags);
        assertEquals("平凡的国中生优希，为了维持在小团体里的位置", info.intro_short);

        info = result.items.get(1);
        assertEquals(3092, info.aid);
        assertEquals("请别来管我", info.title);
        assertEquals("相崎壁际", info.author);
        assertEquals("连载中", info.status);
        assertEquals("2026-08-23", info.update);
        assertEquals("穿越 校园 青春", info.tags);
    }

    @Test
    public void parseFlattensIntroWhitespace() {
        // The row is a single ellipsized line, so the hard line break in item 2's preview has to
        // come out as a plain space rather than reaching the TextView.
        NovelListWithInfoParser.Result result = NovelListWithInfoParser.parse(XML);

        assertNotNull(result);
        assertEquals("「开个脱孤的作战会议吧。」 这是个关于我与", result.items.get(1).intro_short);
    }

    @Test
    public void parseTrimsIdeographicSpaces() {
        // Many previews are padded with U+3000, which Java's \s does not match.
        String xml = "<result><page num='1'/><item aid='1191'>" +
                "<data name='Title'><![CDATA[国王游戏]]></data>" +
                "<data name='IntroPreview'><![CDATA[　　日本某高中　某年某班　]]></data>" +
                "</item></result>";

        NovelListWithInfoParser.Result result = NovelListWithInfoParser.parse(xml);

        assertNotNull(result);
        assertEquals("日本某高中 某年某班", result.items.get(0).intro_short);
    }

    @Test
    public void parseReadsLegacyAttributeShape() {
        // Title and IntroPreview have historically been served as value attributes rather than
        // CDATA; both shapes have to keep parsing.
        String xml = "<result><page num='166'/><item aid='1034'>" +
                "<data name='Title' value='恶魔高校DxD'/>" +
                "<data name='Author' value='石踏一荣'/>" +
                "<data name='IntroPreview' value='　人类与恶魔'/>" +
                "</item></result>";

        NovelListWithInfoParser.Result result = NovelListWithInfoParser.parse(xml);

        assertNotNull(result);
        assertEquals(1, result.items.size());
        assertEquals("恶魔高校DxD", result.items.get(0).title);
        assertEquals("石踏一荣", result.items.get(0).author);
        assertEquals("人类与恶魔", result.items.get(0).intro_short);
    }

    @Test
    public void parseEmptyPage() {
        // Paging past the end returns a well-formed document with no items.
        NovelListWithInfoParser.Result result =
                NovelListWithInfoParser.parse("<result><page num='417'/>\n</result>");

        assertNotNull(result);
        assertEquals(417, result.pageNum);
        assertTrue(result.items.isEmpty());
    }

    @Test
    public void parseMissingFieldsKeepsDefaults() {
        // A sparse item must still be usable rather than null out the row's fields.
        NovelListWithInfoParser.Result result = NovelListWithInfoParser.parse(
                "<result><item aid='777'>" +
                        "<data name='Title'><![CDATA[只有标题]]></data>" +
                        "</item></result>");

        assertNotNull(result);
        assertEquals(0, result.pageNum);
        assertEquals(1, result.items.size());

        NovelItemInfoUpdate info = result.items.get(0);
        assertEquals(777, info.aid);
        assertEquals("只有标题", info.title);
        assertEquals(NovelItemInfoUpdate.LOADING_STRING, info.author);
        assertEquals(NovelItemInfoUpdate.LOADING_STRING, info.status);
        assertEquals(NovelItemInfoUpdate.LOADING_STRING, info.update);
        assertEquals(NovelItemInfoUpdate.LOADING_STRING, info.intro_short);
        assertEquals("", info.tags);
    }

    @Test
    public void parseNonNumericAidAndPage() {
        // A junk number must not abort the whole page.
        NovelListWithInfoParser.Result result = NovelListWithInfoParser.parse(
                "<result><page num='abc'/><item aid='xyz'>" +
                        "<data name='Title'><![CDATA[坏号码]]></data>" +
                        "</item></result>");

        assertNotNull(result);
        assertEquals(0, result.pageNum);
        assertEquals(1, result.items.size());
        assertEquals(0, result.items.get(0).aid);
        assertEquals("坏号码", result.items.get(0).title);
    }

    @Test
    public void parseIgnoresUnknownFields() {
        // Counts are parsed past without being mistaken for a row field.
        NovelListWithInfoParser.Result result = NovelListWithInfoParser.parse(
                "<result><item aid='55'>" +
                        "<data name='TotalHitsCount' value='51'/>" +
                        "<data name='Something' value='new'/>" +
                        "<data name='Title'><![CDATA[未知字段]]></data>" +
                        "</item></result>");

        assertNotNull(result);
        assertEquals(1, result.items.size());
        assertEquals("未知字段", result.items.get(0).title);
    }

    @Test
    public void parseCachesItems() {
        // The list relies on this cache so NovelInfoActivity can open without a round trip.
        // A dedicated aid, since the cache is static and shared across tests.
        NovelListWithInfoParser.Result result = NovelListWithInfoParser.parse(
                "<result><item aid='909091'>" +
                        "<data name='Title'><![CDATA[缓存测试]]></data>" +
                        "</item></result>");

        assertNotNull(result);
        NovelItemInfoUpdate cached = NovelItemInfoUpdate.getFromCache(909091);
        assertNotNull(cached);
        assertEquals("缓存测试", cached.title);
    }
}
