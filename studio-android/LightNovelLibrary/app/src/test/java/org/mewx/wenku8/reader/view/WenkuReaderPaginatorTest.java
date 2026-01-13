package org.mewx.wenku8.reader.view;

import org.junit.Test;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.reader.loader.WenkuReaderLoaderXML;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import android.graphics.Bitmap;

import java.util.List;
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
    private static final List<OldNovelContentParser.NovelContent> SAMPLE_BOOK_TEXT_NOVEL_CONTENT =
            OldNovelContentParser.parseNovelContent(SAMPLE_BOOK_TEXT, PROGRESSIVE_CONSUMER);
    private static final WenkuReaderLoaderXML XML_LOADER = new WenkuReaderLoaderXML(SAMPLE_BOOK_TEXT_NOVEL_CONTENT);


    @Test
    public void testFirstPagePagination() {
        // Screen Settings:
        // Width: 400px (20 characters per line, assume each character is 20px wide)
        // Height: 800px
        // Font height: 30px
        // Line distance: 10px
        // Paragraph distance: 20px
        WenkuReaderPaginator paginator = new WenkuReaderPaginator(XML_LOADER, text -> text.length() * 20, 400, 800, 30, 10, 20);

        // Start from beginning
        paginator.setPageStart(0, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();
        assertFalse(lines.isEmpty());

        // Check first line: Title "第三卷 第五十八话 猪肉味噌汤再来"
        // Length 17 chars -> 340px. Fits in 400px.
        // But logic adds indentation "　　" if it's the first word of paragraph and type TEXT.
        // Wait, the loader parses "第三卷..." as TEXT.
        // The paginator logic:
        // if(curWordIndex == 0 && mLoader.getCurrentType() == WenkuReaderLoader.ElementType.text()) {
        //     widthSum = 2 * fontHeight; // 60px
        //     tempText = new StringBuilder("　　");
        // }
        // So "　　" + "第三卷..."
        // "第三卷 第五十八话 猪肉味噌汤再来" is 17 chars.
        // Total width: 60px + 17 * 20px = 60 + 340 = 400px.
        // Matches exactly 400px.

        assertEquals("　　第三卷 第五十八话 猪肉味噌汤再来", lines.get(0).text());

        // Next paragraph: "填饱饿了两天的肚子后，堤达满足地吐了口气。"
        // Length 21 chars.
        // Indentation: 60px.
        // Remaining width: 340px -> 17 chars.
        // Line 2: "　　填饱饿了两天的肚子后，堤达满足地吐" (17 chars from text)
        // Line 3: "了口气。" (remaining 4 chars)

        assertTrue(lines.size() > 2);
        assertEquals("　　填饱饿了两天的肚子后，堤达满足地吐", lines.get(1).text());
        assertEquals("了口气。", lines.get(2).text());
    }

    @Test
    public void testPaginationFlow() {
        // Screen Settings:
        // Width: 400px (20 characters per line, assume each character is 20px wide)
        // Height: 800px
        // Font height: 30px
        // Line distance: 10px
        // Paragraph distance: 20px
        WenkuReaderPaginator paginator = new WenkuReaderPaginator(XML_LOADER, text -> text.length() * 20, 400, 800, 30, 10, 20);

        // Start from index 1 (First actual paragraph)
        // "填饱饿了两天的肚子后，堤达满足地吐了口气。"
        paginator.setPageStart(1, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();

        // Line 0: "　　填饱饿了两天的肚子后，堤达满足地吐"
        assertEquals("　　填饱饿了两天的肚子后，堤达满足地吐", lines.get(0).text());

        // Line 1: "了口气。"
        assertEquals("了口气。", lines.get(1).text());

        // Next Paragraph: "「呼……」"
        // Line 2: "　　「呼……」"
        assertEquals("　　「呼……」", lines.get(2).text());
    }

    @Test
    public void testBugReproduced_Page2_MissingChar() {
        // Scenario: Page 1 renders correctly, but Page 2 misses the first character.
        // Paragraph 1: "i" (fits with indent in 23px width).
        // Paragraph 2: "一" (overflows with indent in 23px width).

        String[] paragraphs = new String[] { "i", "一" };
        StubLoader loader = new StubLoader(paragraphs);
        StubMeasurer measurer = new StubMeasurer();

        int fontHeight = 10;
        int width = 23;
        int height = 15; // Fits 1 line (10px) + margin. Cannot fit 2 lines (10+2+10=22px).
        int lineDist = 2;
        int paraDist = 4;

        loader.setCurrentIndex(0);
        WenkuReaderPaginator paginator = new WenkuReaderPaginator(loader, measurer,
                width, height, fontHeight, lineDist, paraDist);

        // --- Page 1 ---
        paginator.setPageStart(0, 0);
        paginator.calcFromFirst();

        List<LineInfo> p1Lines = paginator.getLineInfoList();

        // Assert Page 1 works correctly
        assertFalse("Page 1 should not be empty", p1Lines.isEmpty());
        String p1Text = p1Lines.get(0).text();
        assertTrue("Page 1 should contain 'i'", p1Text.contains("i"));

        int lastLine = paginator.getLastLineIndex();
        int lastWord = paginator.getLastWordIndex();

        // Calculate Page 2 Start
        int p2Line = lastLine;
        int p2Word;

        if (lastWord + 1 < paragraphs[lastLine].length()) {
             p2Word = lastWord + 1;
        } else {
             p2Line++;
             p2Word = 0;
        }

        assertEquals("Page 2 should start at Line 1 (Para 2)", 1, p2Line);
        assertEquals("Page 2 should start at Word 0", 0, p2Word);

        // --- Page 2 ---
        paginator.setPageStart(p2Line, p2Word);
        paginator.calcFromFirst();

        List<LineInfo> p2Lines = paginator.getLineInfoList();
        StringBuilder rawSb = new StringBuilder();
        for (LineInfo l : p2Lines) if (l.type() == WenkuReaderLoader.ElementType.TEXT) rawSb.append(l.text());
        String raw = rawSb.toString();

        // System.out.println("Page 2 Raw content: '" + raw + "'");

        int p2LastWord = paginator.getLastWordIndex();
        // System.out.println("Page 2 lastWordIndex: " + p2LastWord);

        // Bug: "一" causes wrap (Indent 20 + 10 > 23).
        // Line 1: Indent.
        // Height check: 10 + 2 + 10 = 22 > 15. Overflow.
        // "一" is skipped.

        boolean containsCharOne = raw.contains("一");

        if (!containsCharOne && p2LastWord >= 0) {
             fail("Bug Reproduced: First Chinese character '一' not displayed on Page 2, but lastWordIndex claims it is included (" + p2LastWord + ")");
        }
    }

    // Stub for Loader to avoid dependency on real XML/GlobalConfig/Android
    static class StubLoader extends WenkuReaderLoader {
        private String[] paragraphs;
        private int currentIndex = 0;

        public StubLoader(String[] paragraphs) {
            this.paragraphs = paragraphs;
        }

        @Override public void setChapterName(String name) {}
        @Override public String getChapterName() { return "Chapter"; }
        @Override public boolean hasNext(int wordIndex) { return false; }
        @Override public boolean hasPrevious(int wordIndex) { return false; }
        @Override public ElementType getNextType() { return null; }
        @Override public String getNextAsString() { return null; }
        @Override public Bitmap getNextAsBitmap() { return null; }

        @Override public ElementType getCurrentType() { return ElementType.TEXT; }

        @Override public String getCurrentAsString() {
             if (currentIndex >= paragraphs.length) return null;
             return paragraphs[currentIndex];
        }

        @Override public int getCurrentStringLength() {
             if (currentIndex >= paragraphs.length) return 0;
             return paragraphs[currentIndex].length();
        }

        @Override public Bitmap getCurrentAsBitmap() { return null; }
        @Override public ElementType getPreviousType() { return null; }
        @Override public String getPreviousAsString() { return null; }
        @Override public Bitmap getPreviousAsBitmap() { return null; }
        @Override public int getStringLength(int n) { return paragraphs[n].length(); }
        @Override public int getElementCount() { return paragraphs.length; }
        @Override public int getCurrentIndex() { return currentIndex; }
        @Override public void setCurrentIndex(int i) { currentIndex = i; }
        @Override public void closeLoader() {}
    }

    static class StubMeasurer implements TextMeasurer {
        @Override public float measureText(String text) {
            // Variable width: 'i' is narrow (2px), others are wide (10px)
            float sum = 0;
            for (char c : text.toCharArray()) {
                if (c == 'i') sum += 2.0f;
                else sum += 10.0f;
            }
            return sum;
        }
    }
}
