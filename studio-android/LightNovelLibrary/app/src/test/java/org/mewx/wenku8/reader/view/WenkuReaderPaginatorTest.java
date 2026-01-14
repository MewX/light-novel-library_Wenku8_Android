package org.mewx.wenku8.reader.view;

import org.junit.Test;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.reader.loader.WenkuReaderLoaderXML;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import android.graphics.Bitmap;

import java.util.List;
import java.util.ArrayList;
import java.util.function.IntConsumer;

import static org.junit.Assert.*;

public class WenkuReaderPaginatorTest {

    private static final IntConsumer PROGRESSIVE_CONSUMER = unused -> {};

    private static final String SAMPLE_BOOK_TEXT =
            " 第三卷 第五十八话 猪肉味噌汤再来  \r\n" +
            " \r\n" +
            "  \r\n" +
            "   \r\n" +
            "  \r\n" +
            " \r\n" +
            "  \r\n" +
            "    填饱饿了两天的肚子后，堤达满足地吐了口气。  \r\n" +
            "  \r\n" +
            "    「呼……」  \r\n" +
            "  \r\n" +
            "    即使遭到风吹雨打，堤达依然努力寻找粮食，并在最后发现了这个神奇的地方。  \r\n" +
            "  \r\n" +
            "    一扇东大陆风格的门丝毫没受到暴风雨的影响，屹立在海边的沙滩上。  \r\n" +
            "  \r\n" +
            "    堤达穿过那扇门后，就来到异世界的餐厅。  ";

    // Stub implementation to populate the Loader without external parser dependency logic
    private WenkuReaderLoaderXML createLoader(List<OldNovelContentParser.NovelContent> contentList) {
        return new WenkuReaderLoaderXML(contentList);
    }

    // Stub for parser logic
    private List<OldNovelContentParser.NovelContent> parseSampleTextStub() {
        List<OldNovelContentParser.NovelContent> list = new ArrayList<>();
        // Manually parsed sample (Simplified stub)
        String[] lines = new String[] {
            "第三卷 第五十八话 猪肉味噌汤再来",
            "填饱饿了两天的肚子后，堤达满足地吐了口气。",
            "「呼……」"
        };
        for(String s : lines) {
            OldNovelContentParser.NovelContent nc = new OldNovelContentParser.NovelContent();
            nc.type = OldNovelContentParser.NovelContentType.TEXT;
            nc.content = s;
            list.add(nc);
        }
        return list;
    }

    private final WenkuReaderLoaderXML XML_LOADER = createLoader(parseSampleTextStub());

    @Test
    public void testFirstPagePagination() {
        WenkuReaderPaginator paginator = new WenkuReaderPaginator(XML_LOADER, text -> text.length() * 20, 400, 800, 30, 10, 20);

        paginator.setPageStart(0, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();
        assertFalse(lines.isEmpty());

        assertEquals("　　第三卷 第五十八话 猪肉味噌汤再来", lines.get(0).text());
        assertTrue(lines.size() > 1);
        assertEquals("　　填饱饿了两天的肚子后，堤达满足地吐", lines.get(1).text());
        assertEquals("了口气。", lines.get(2).text());
    }

    @Test
    public void testPaginationFlow() {
        WenkuReaderPaginator paginator = new WenkuReaderPaginator(XML_LOADER, text -> text.length() * 20, 400, 800, 30, 10, 20);

        paginator.setPageStart(1, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();

        assertEquals("　　填饱饿了两天的肚子后，堤达满足地吐", lines.get(0).text());
        assertEquals("了口气。", lines.get(1).text());
        assertEquals("　　「呼……」", lines.get(2).text());
    }

    @Test
    public void testBugReproduced_startOfSecondPage() {
        // Reproduce "missing first character" bug.
        // This simulates a "middle page" scenario which corresponds to the start of a loader segment (Line 0).
        // Condition: Indent + First Char > Width (Wrap) AND Wrapped Line > Remaining Height (Overflow).

        List<OldNovelContentParser.NovelContent> content = new ArrayList<>();

        OldNovelContentParser.NovelContent target = new OldNovelContentParser.NovelContent();
        target.type = OldNovelContentParser.NovelContentType.TEXT;
        target.content = "一二三四五";
        content.add(target);

        WenkuReaderLoaderXML loader = createLoader(content);

        // Dimensions:
        // Width 23px. Indent (20px) fits.
        // Char "一" (10px). Indent+Char = 30px > 23px. Wrap.
        // Line 1 (Indent) fits height (15px).
        // Line 2 ("一") needs 10px + 2px (dist) = 12px.
        // Total 22px > 15px. Overflow.
        // The paginator hits overflow on the first character of the paragraph.
        // It hits the `else { lastLineIndex = lastWordIndex = 0; }` block.
        // This sets the page end to Word 0 ("一").
        // But "一" was NOT displayed (it overflowed).
        // So the user sees a page with only Indent.
        // And the NEXT page starts at Word 1 ("二").
        // "一" is missing.

        int fontHeight = 10;
        int width = 23;
        int height = 15;
        int lineDist = 2;
        int paraDist = 4;

        TextMeasurer measurer = text -> text.length() * 10;

        loader.setCurrentIndex(0);
        WenkuReaderPaginator paginator = new WenkuReaderPaginator(loader, measurer,
                width, height, fontHeight, lineDist, paraDist);

        // Start at Line 0 (Start of this segment/chapter)
        paginator.setPageStart(0, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();
        StringBuilder rawSb = new StringBuilder();
        for (LineInfo l : lines) if (l.type() == WenkuReaderLoader.ElementType.TEXT) rawSb.append(l.text());
        String raw = rawSb.toString();

        int lastWord = paginator.getLastWordIndex();

        System.out.println("Page Content: '" + raw + "'");
        System.out.println("Last Word Index: " + lastWord);

        boolean containsFirstChar = raw.contains("一");

        // Failure: '一' is not in display list, but lastWordIndex includes it (0).
        if (!containsFirstChar && lastWord >= 0) {
             fail("Bug Reproduced: First Chinese character '一' not displayed but lastWordIndex claims it is included (" + lastWord + ")");
        }
    }
}
