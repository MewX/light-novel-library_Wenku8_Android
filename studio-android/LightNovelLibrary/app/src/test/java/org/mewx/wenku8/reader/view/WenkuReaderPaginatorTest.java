package org.mewx.wenku8.reader.view;

import org.junit.Test;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.reader.loader.WenkuReaderLoaderXML;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;

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

    private WenkuReaderLoaderXML createLoader(List<OldNovelContentParser.NovelContent> contentList) {
        return new WenkuReaderLoaderXML(contentList);
    }

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
        List<OldNovelContentParser.NovelContent> targetContent = new ArrayList<>();
        targetContent.add(allContent.get(1));

        WenkuReaderLoaderXML loader = createLoader(targetContent);

        // Dimensions Setup:
        // FontHeight = 15. Indent = 2 * 15 = 30px.
        // CharWidth = 10 (via StubMeasurer).
        // Width = 35.
        //   - Width > Indent (35 > 30). Indent fits alone.
        //   - Width >= 3 * Char (35 > 30). Fits "3 characters".
        //   - Indent + First Char = 30 + 10 = 40 > 35. Forces Wrap.

        // Height = 30.
        //   - Line 1 (Indent): 15px. Fits.
        //   - Line 2 (First Char "填"): 15px.
        //   - Line Dist: 2px.
        //   - Total Height Needed: 15 + 2 + 15 = 32 > 30. Overflow.

        // Paginator Behavior (Bug):
        // 1. Adds Indent.
        // 2. Wraps "填".
        // 3. Overflow check fails.
        // 4. Hits `else { lastLineIndex = lastWordIndex = 0; }` because curLineIndex=0.
        // 5. Result: Page 1 contains only Indent. Paginator claims it processed up to index 0 ("填").

        int fontHeight = 15;
        int width = 35;
        int height = 30;
        int lineDist = 2;
        int paraDist = 4;

        TextMeasurer measurer = text -> text.length() * 10;

        loader.setCurrentIndex(0);
        WenkuReaderPaginator paginator = new WenkuReaderPaginator(loader, measurer,
                width, height, fontHeight, lineDist, paraDist);

        // --- Page 1 ---
        paginator.setPageStart(0, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();
        StringBuilder rawSb = new StringBuilder();
        for (LineInfo l : lines) if (l.type() == WenkuReaderLoader.ElementType.TEXT) rawSb.append(l.text());
        String page1Text = rawSb.toString();

        int lastLine = paginator.getLastLineIndex();
        int lastWord = paginator.getLastWordIndex();

        System.out.println("Page 1 Content: '" + page1Text + "'");
        System.out.println("Last Word Index: " + lastWord);

        // --- Page 2 ---
        // Calculate next start based on Paginator's output.
        // Paginator claims lastWord = 0. So next word is 1.
        int p2Line = lastLine;
        int p2Word = lastWord + 1;

        paginator.setPageStart(p2Line, p2Word);
        paginator.calcFromFirst();

        List<LineInfo> lines2 = paginator.getLineInfoList();
        StringBuilder rawSb2 = new StringBuilder();
        for (LineInfo l : lines2) if (l.type() == WenkuReaderLoader.ElementType.TEXT) rawSb2.append(l.text());
        String page2Text = rawSb2.toString();

        System.out.println("Page 2 Content: '" + page2Text + "'");

        // Assertions
        // 1. Page 2 should not be empty (it should contain "饱...").
        assertFalse("Page 2 should not be empty", page2Text.isEmpty());
        assertTrue("Page 2 should contain content (e.g. '饱')", page2Text.contains("饱"));

        // 2. The first character "填" should be missing from BOTH pages.
        boolean p1HasChar = page1Text.contains("填");
        boolean p2HasChar = page2Text.contains("填");

        if (!p1HasChar && !p2HasChar) {
             fail("Bug Reproduced: First character '填' is missing from both Page 1 and Page 2.");
        }
    }
}
