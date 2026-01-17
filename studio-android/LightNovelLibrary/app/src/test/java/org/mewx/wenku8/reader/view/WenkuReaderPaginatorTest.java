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

    // Parse the sample text similar to OldNovelContentParser
    private List<OldNovelContentParser.NovelContent> parseSampleText(String text) {
        List<OldNovelContentParser.NovelContent> list = new ArrayList<>();
        String[] lines = text.split("\r\n");
        for(String s : lines) {
            String trimmed = s.trim();
            if (trimmed.isEmpty()) continue;

            OldNovelContentParser.NovelContent nc = new OldNovelContentParser.NovelContent();
            nc.type = OldNovelContentParser.NovelContentType.TEXT;
            nc.content = trimmed;
            list.add(nc);
        }
        return list;
    }

    @Test
    public void testFirstPagePagination() {
        List<OldNovelContentParser.NovelContent> content = parseSampleText(SAMPLE_BOOK_TEXT);
        WenkuReaderLoaderXML loader = createLoader(content);

        WenkuReaderPaginator paginator = new WenkuReaderPaginator(loader, text -> text.length() * 20, 400, 800, 30, 10, 20);

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
        List<OldNovelContentParser.NovelContent> content = parseSampleText(SAMPLE_BOOK_TEXT);
        WenkuReaderLoaderXML loader = createLoader(content);

        WenkuReaderPaginator paginator = new WenkuReaderPaginator(loader, text -> text.length() * 20, 400, 800, 30, 10, 20);

        paginator.setPageStart(1, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();

        assertEquals("　　填饱饿了两天的肚子后，堤达满足地吐", lines.get(0).text());
        assertEquals("了口气。", lines.get(1).text());
        assertEquals("　　「呼……」", lines.get(2).text());
    }

    @Test
    public void testBugReproduced_startOfSecondPage() {
        // Reproduce "missing first character" bug using the provided sample text.
        // We target the second paragraph: "填饱饿了两天的肚子后，堤达满足地吐了口气。"
        // To reproduce the bug (skipping char), we must treat this as the Start of the Loader (Line 0).

        List<OldNovelContentParser.NovelContent> allContent = parseSampleText(SAMPLE_BOOK_TEXT);
        // Index 0: Title
        // Index 1: "填饱..."

        List<OldNovelContentParser.NovelContent> targetContent = new ArrayList<>();
        targetContent.add(allContent.get(1)); // Add only the target paragraph

        WenkuReaderLoaderXML loader = createLoader(targetContent);

        // Dimensions to satisfy "Width can contain 3 characters":
        // CharWidth = 10.
        // FontHeight = 15. Indent = 2 * 15 = 30.
        // Width = 35.
        // 35 > 3 * 10 (30). Satisfies constraint.
        // 35 > Indent (30). Indent fits.
        // Indent + FirstChar ("填" = 10) = 40 > 35. Wraps.

        // Height = 30.
        // Line 1 (Indent): 15px. Fits (15 < 30).
        // Line 2 ("填"): 15px.
        // Line Distance: 2px.
        // Needed for Line 2: 15 (L1) + 2 (Dist) + 15 (L2) = 32.
        // 32 > 30. Overflow.

        // Result:
        // Paginator adds Indent. Wraps "填". Checks height. Overflows.
        // Logic hits `else { lastLineIndex = lastWordIndex = 0; }`.
        // Page ends at Word 0 ("填").
        // Displayed: Only Indent.
        // Next page starts at Word 1 ("饱").
        // "填" is skipped.

        int fontHeight = 15;
        int width = 35;
        int height = 30;
        int lineDist = 2;
        int paraDist = 4;

        TextMeasurer measurer = text -> text.length() * 10;

        loader.setCurrentIndex(0);
        WenkuReaderPaginator paginator = new WenkuReaderPaginator(loader, measurer,
                width, height, fontHeight, lineDist, paraDist);

        // Start at Line 0
        paginator.setPageStart(0, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();
        StringBuilder rawSb = new StringBuilder();
        for (LineInfo l : lines) if (l.type() == WenkuReaderLoader.ElementType.TEXT) rawSb.append(l.text());
        String raw = rawSb.toString();

        int lastWord = paginator.getLastWordIndex();

        System.out.println("Page Content: '" + raw + "'");
        System.out.println("Last Word Index: " + lastWord);

        boolean containsFirstChar = raw.contains("填");

        // Failure: '填' is not in display list, but lastWordIndex includes it (0).
        if (!containsFirstChar && lastWord >= 0) {
             fail("Bug Reproduced: First character '填' not displayed but lastWordIndex claims it is included (" + lastWord + ")");
        }
    }
}
