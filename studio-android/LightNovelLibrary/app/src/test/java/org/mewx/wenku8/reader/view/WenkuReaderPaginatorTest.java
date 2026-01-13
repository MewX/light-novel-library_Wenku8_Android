package org.mewx.wenku8.reader.view;

import org.junit.Test;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import android.graphics.Bitmap;

import java.util.List;
import static org.junit.Assert.*;

public class WenkuReaderPaginatorTest {

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
            // Assume 10px per char (Chinese or English, simplified)
            return text.length() * 10;
        }
    }

    private static final String SAMPLE_BOOK_TEXT =
            "第三卷 第五十八话 猪肉味噌汤再来\n" +
            "填饱饿了两天的肚子后，堤达满足地吐了口气。\n" +
            "「呼……」\n" +
            "即使遭到风吹雨打，堤达依然努力寻找粮食，并在最后发现了这个神奇的地方。\n" +
            "一扇东大陆风格的门丝毫没受到暴风雨的影响，屹立在海边的沙滩上。";

    @Test
    public void testPageContinuity() {
        String[] paragraphs = SAMPLE_BOOK_TEXT.split("\n");
        StubLoader loader = new StubLoader(paragraphs);
        StubMeasurer measurer = new StubMeasurer();

        int fontHeight = 10;
        int textAreaWidth = 200;
        int textAreaHeight = 100;
        int pxLineDistance = 0;
        int pxParagraphDistance = 0;

        WenkuReaderPaginator paginator = new WenkuReaderPaginator(loader, measurer,
            textAreaWidth, textAreaHeight, fontHeight, pxLineDistance, pxParagraphDistance);

        paginator.setPageStart(0, 0);
        paginator.calcFromFirst();

        List<LineInfo> p1Lines = paginator.getLineInfoList();
        assertFalse("Page 1 should not be empty", p1Lines.isEmpty());
        assertEquals("　　第三卷 第五十八话 猪肉味噌汤再来", p1Lines.get(0).text());
    }

    @Test
    public void testBugReproduced() {
        // Reproduce using Chinese characters "一二三四五"
        // This simulates a "middle page" scenario (Start of a Chapter in middle of book).
        // The bug occurs at the start of the chapter (Line 0).

        String text = "一二三四五";
        StubLoader loader = new StubLoader(new String[]{text});
        StubMeasurer measurer = new StubMeasurer();
        int fontHeight = 10;

        // Setup conditions for immediate overflow after indent wrap
        // Indent (20px) + "一" (10px) = 30px.
        // Width 23px < 30px. Forces wrap of "一".
        // Line 1 contains only Indent ("　　").
        // Height 15px. Fits Line 1 (10px).
        // Line 2 (containing "一") needs 10px + 2px distance = 12px.
        // Total height needed 22px > 15px. Overflow.

        int width = 23;
        int height = 15;
        int lineDist = 2;
        int paraDist = 4;

        loader.setCurrentIndex(0);
        WenkuReaderPaginator paginator = new WenkuReaderPaginator(loader, measurer,
                width, height, fontHeight, lineDist, paraDist);

        paginator.setPageStart(0, 0);
        paginator.calcFromFirst();

        int lastWord = paginator.getLastWordIndex();

        // Check displayed content
        List<LineInfo> lines = paginator.getLineInfoList();
        StringBuilder rawSb = new StringBuilder();
        for (LineInfo l : lines) if (l.type() == WenkuReaderLoader.ElementType.TEXT) rawSb.append(l.text());
        String raw = rawSb.toString();

        System.out.println("Raw content: '" + raw + "'");

        // Expectation: '一' should be visible OR lastWordIndex should exclude it.
        // Actual Bug: '一' is not visible, but lastWordIndex includes it (0).

        boolean containsCharOne = raw.contains("一");
        if (!containsCharOne && lastWord >= 0) {
             fail("Bug Reproduced: First Chinese character '一' not displayed but lastWordIndex claims it is included (" + lastWord + ")");
        }
    }
}
