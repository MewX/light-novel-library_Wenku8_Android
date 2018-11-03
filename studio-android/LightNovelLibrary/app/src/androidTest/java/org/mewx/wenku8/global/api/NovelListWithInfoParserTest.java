package org.mewx.wenku8.global.api;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class NovelListWithInfoParserTest {
    private static final String XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
            "<result>\n" +
            "<page num='166'/>\n" +
            "\n" +
            "<item aid='1034'>\n" +
            "<data name='Title'><![CDATA[恶魔高校DxD(High School DxD)]]></data>\n" +
            "<data name='TotalHitsCount' value='2316361'/>\n" +
            "<data name='PushCount' value='153422'/>\n" +
            "<data name='FavCount' value='14416'/>\n" +
            "<data name='Author' value='xxx1'/>\n" +
            "<data name='BookStatus' value='xxx2'/>\n" +
            "<data name='LastUpdate' value='xxx3'/>\n" +
            "<data name='IntroPreview' value='xxx4'/>\n" +
            "</item>\n" +
            "\n" +
            "<item aid='1035'>\n" +
            "<data name='Title'><![CDATA[High School DxD]]></data>\n" +
            "<data name='TotalHitsCount' value='1234'/>\n" +
            "<data name='PushCount' value='4567'/>\n" +
            "<data name='FavCount' value='789'/>\n" +
            "<data name='Author' value='yyy1'/>\n" +
            "<data name='BookStatus' value='yyy2'/>\n" +
            "<data name='LastUpdate' value='yyy3'/>\n" +
            "<data name='IntroPreview' value='yyy4'/>\n" +
            "</item>\n" +
            "\n" +
            "</result>";

    @Test
    public void getNovelListWithInfoPageNumInvalid() {
        assertEquals(0, NovelListWithInfoParser.getNovelListWithInfoPageNum("1234"));
    }

    @Test
    public void getNovelListWithInfoPageNum() {
        assertEquals(166, NovelListWithInfoParser.getNovelListWithInfoPageNum(XML));
    }


    @Test
    public void getNovelListWithInfoInvalid() {
        List<NovelListWithInfoParser.NovelListWithInfo> list = NovelListWithInfoParser.getNovelListWithInfo("1234");
        assertTrue(list.isEmpty());
    }

    @Test
    public void getNovelListWithInfo() {
        List<NovelListWithInfoParser.NovelListWithInfo> list = NovelListWithInfoParser.getNovelListWithInfo(XML);
        assertEquals(2, list.size());

        NovelListWithInfoParser.NovelListWithInfo info = list.get(0);
        assertEquals(1034, info.aid);
        assertEquals(14416, info.fav);
        assertEquals(2316361, info.hit);
        assertEquals(153422, info.push);
        assertEquals("恶魔高校DxD(High School DxD)", info.name);

        info = list.get(1);
        assertEquals(1035, info.aid);
        assertEquals(789, info.fav);
        assertEquals(1234, info.hit);
        assertEquals(4567, info.push);
        assertEquals("High School DxD", info.name);
    }
}