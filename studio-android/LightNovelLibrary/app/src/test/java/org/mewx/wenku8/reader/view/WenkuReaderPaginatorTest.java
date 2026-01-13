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
            return text.length() * 10;
        }
    }

    @Test
    public void testPageContinuity() {
        // Setup
        String text = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
        String[] paragraphs = { text };
        StubLoader loader = new StubLoader(paragraphs);
        StubMeasurer measurer = new StubMeasurer();

        int fontHeight = 10;
        int textAreaWidth = 100; // Fits 10 chars. Indent "　　" takes 20.
        int textAreaHeight = 35; // Fits 3 lines (30).
        int pxLineDistance = 0;
        int pxParagraphDistance = 0;

        WenkuReaderPaginator paginator = new WenkuReaderPaginator(loader, measurer,
            textAreaWidth, textAreaHeight, fontHeight, pxLineDistance, pxParagraphDistance);

        // Page 1 calculation
        paginator.setPageStart(0, 0);
        paginator.calcFromFirst();

        int p1LastLine = paginator.getLastLineIndex();
        int p1LastWord = paginator.getLastWordIndex();
        List<LineInfo> p1Lines = paginator.getLineInfoList();

        // Verify Page 1 content.
        // Line 1: Indent + 0..7. (8 chars). "　　01234567"
        // Line 2: 8..17. (10 chars). "89ABCDEFGH"
        // Line 3: 18..27. (10 chars). "IJKLMNOPQR"
        // Line 4 overflow.
        // Page 1 should end at 'R' (index 27).

        char charAt27 = text.charAt(27); // 'R'
        assertEquals("Page 1 should end at 27", 27, p1LastWord);

        // Calculate Page 2 Start
        int p2LineIndex = p1LastLine;
        int p2WordIndex = (p1LastLine == 0 && p1LastWord == 0) ? 0 : p1LastWord + 1;

        assertEquals("Page 2 should start at 28", 28, p2WordIndex);

        // Page 2 calculation
        paginator.setPageStart(p2LineIndex, p2WordIndex);
        paginator.calcFromFirst();

        List<LineInfo> p2Lines = paginator.getLineInfoList();
        assertFalse("Page 2 should not be empty", p2Lines.isEmpty());

        String p2FirstLine = p2Lines.get(0).text();
        char firstCharP2 = p2FirstLine.charAt(0);
        char expectedChar = text.charAt(p2WordIndex); // 'S'

        assertEquals("Page 2 should start with expected char", expectedChar, firstCharP2);
    }

    @Test
    public void testBugReproduced_NarrowWidthWithIndent() {
        String text = "0123456789";
        StubLoader loader = new StubLoader(new String[]{text});
        StubMeasurer measurer = new StubMeasurer();
        int fontHeight = 10;
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

        boolean containsChar0 = raw.contains("0");
        if (!containsChar0) {
            System.out.println("Page content does not contain '0'.");
        }

        System.out.println("lastWordIndex: " + lastWord);

        // The bug is that '0' is not displayed (because of overflow), but lastWordIndex includes it (0).
        if (!containsChar0 && lastWord >= 0) {
             fail("Bug Reproduced: '0' not displayed but lastWordIndex claims it is included (" + lastWord + ")");
        }
    }
}
