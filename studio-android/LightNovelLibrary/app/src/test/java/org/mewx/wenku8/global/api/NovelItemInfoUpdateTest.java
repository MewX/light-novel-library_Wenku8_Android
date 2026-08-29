package org.mewx.wenku8.global.api;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.*;

// Robolectric is required: this exercises XmlPullParser-based parsing and android.util.LruCache,
// both of which are no-op stubs under the plain JVM test runtime.
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
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

    /** A record carrying every field a list row renders. Distinct aids: the cache is static. */
    private static NovelItemInfoUpdate complete(int aid) {
        NovelItemInfoUpdate info = new NovelItemInfoUpdate(aid);
        info.title = "\u5b8c\u6574";
        info.author = "\u4f5c\u8005";
        info.status = "\u8fde\u8f7d\u4e2d";
        info.update = "2026-08-23";
        info.intro_short = "\u7b80\u4ecb";
        return info;
    }

    /** What a novellist page yields when the response omits a field element entirely. */
    private static NovelItemInfoUpdate sparse(int aid) {
        NovelItemInfoUpdate info = new NovelItemInfoUpdate(aid);
        info.title = "\u53ea\u6709\u6807\u9898";
        return info;
    }

    @Test
    public void isPlaceholderUntilATitleArrives() {
        NovelItemInfoUpdate info = new NovelItemInfoUpdate(4346);
        assertTrue(info.isPlaceholder());

        info.title = "\u770b\u4e0d\u89c1\u7684\u89c4\u5219";
        assertFalse(info.isPlaceholder());
    }

    @Test
    public void populatedFieldCountIgnoresLatestChapter() {
        // latest_chapter is never returned by a list endpoint, so counting it would mark every
        // ranking row incomplete forever.
        NovelItemInfoUpdate info = complete(1);
        assertEquals(5, info.populatedFieldCount());

        info.latest_chapter = NovelItemInfoUpdate.LOADING_STRING;
        assertEquals(5, info.populatedFieldCount());

        assertEquals(1, sparse(2).populatedFieldCount());
        assertEquals(0, new NovelItemInfoUpdate(3).populatedFieldCount());
    }

    @Test
    public void putToCachePrefersTheFullerRecord() {
        // The regression this guards: a sparse page cached first used to own the entry forever,
        // so every other list rendered its "Loading..." fields.
        NovelItemInfoUpdate first = sparse(900001);
        NovelItemInfoUpdate better = complete(900001);

        NovelItemInfoUpdate.putToCache(first);
        assertSame(first, NovelItemInfoUpdate.getFromCache(900001));

        NovelItemInfoUpdate.putToCache(better);
        assertSame(better, NovelItemInfoUpdate.getFromCache(900001));
    }

    @Test
    public void putToCacheKeepsTheFullerRecord() {
        NovelItemInfoUpdate better = complete(900002);
        NovelItemInfoUpdate worse = sparse(900002);

        NovelItemInfoUpdate.putToCache(better);
        NovelItemInfoUpdate.putToCache(worse);

        assertSame(better, NovelItemInfoUpdate.getFromCache(900002));
    }

    @Test
    public void isMissingMatchesTheLoadingSentinelOnly() {
        assertTrue(NovelItemInfoUpdate.isMissing(null));
        assertTrue(NovelItemInfoUpdate.isMissing(NovelItemInfoUpdate.LOADING_STRING));
        // An empty value is an answer -- the novel simply has no synopsis -- not a missing field.
        assertFalse(NovelItemInfoUpdate.isMissing(""));
        assertFalse(NovelItemInfoUpdate.isMissing("\u4f5c\u8005"));
    }
}
