package org.mewx.wenku8.global.api;

import org.junit.Test;

import static org.junit.Assert.*;

public class NovelItemInfoUpdateTest {

    @Test
    public void convertFromMeta() {
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

        NovelItemInfoUpdate info = NovelItemInfoUpdate.convertFromMeta(meta);
        assertEquals("向森之魔物献上花束(向森林的魔兽少女献花)", info.title);
        assertEquals(1306, info.aid);
        assertEquals("小木君人", info.author);
        assertEquals("已完成", info.status);
        assertEquals("2012-11-02", info.update);
        assertEquals("第一卷 插图", info.latest_chapter);
    }

    @Test
    public void parseNovelItemInfoUpdate() {
        final String XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
                "<metadata>\n" +
                "<data name=\"Title\" aid=\"1305\"><![CDATA[绝对双刃absolute duo]]></data>\n" +
                "<data name=\"Author\" value=\"柊★巧\"/>\n" +
                "<data name=\"BookStatus\" value=\"连载中\"/>\n" +
                "<data name=\"LastUpdate\" value=\"2014-10-01\"/>\n" +
                "<data\n" +
                "name=\"IntroPreview\"><![CDATA[　　「焰牙」——那是藉由超化之后的精神力将自身灵...]]></data>\n" +
                "</metadata>";
        NovelItemInfoUpdate info = NovelItemInfoUpdate.parse(XML);
        assertNotNull(info);

        assertEquals("绝对双刃absolute duo", info.title);
        assertEquals(1305, info.aid);
        assertEquals("柊★巧", info.author);
        assertEquals("连载中", info.status);
        assertEquals("2014-10-01", info.update);
        assertEquals("「焰牙」——那是藉由超化之后的精神力将自身灵...", info.intro_short);
    }

    @Test
    public void parseNovelItemInfoUpdateInvalid() {
        NovelItemInfoUpdate info = NovelItemInfoUpdate.parse("1234");
        assertNull(info);
    }
}