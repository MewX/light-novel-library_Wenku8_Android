package org.mewx.wenku8.reader.view;

import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import org.mewx.wenku8.reader.loader.WenkuReaderLoaderXML;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class WenkuReaderPaginatorTest {

    private WenkuReaderPaginator paginator;
    private WenkuReaderLoaderXML loader;
    private List<OldNovelContentParser.NovelContent> contentList;
    private TextMeasurer textMeasurer;

    @Before
    public void setUp() {
        contentList = new ArrayList<>();

        // Add a long text
        OldNovelContentParser.NovelContent textContent = new OldNovelContentParser.NovelContent();
        textContent.type = OldNovelContentParser.NovelContentType.TEXT;
        textContent.content = "12345678901234567890"; // 20 chars
        contentList.add(textContent);

        loader = new WenkuReaderLoaderXML(contentList);

        // Mock measurer: each char is 10px wide
        textMeasurer = new TextMeasurer() {
            @Override
            public float measureText(String text) {
                return text.length() * 10;
            }
        };

        // Layout: 100px width (10 chars per line), 200px height.
        // Font height 20px. Line distance 10px. Paragraph distance 20px.
        // First line: indentation 2 chars = 2 * 20 = 40px width.
        // "123456" (6 chars = 60px). Total 100px.
        paginator = new WenkuReaderPaginator(loader, textMeasurer, 100, 200, 20, 10, 20);
    }

    @Test
    public void testCalcFromFirst_SimpleText() {
        paginator.setPageStart(0, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();
        assertNotNull(lines);
        assertFalse(lines.isEmpty());

        // First line should have indentation "　　" (2 chars space)
        // With 100px width, font height 20px (indentation 40px).
        // Remaining 60px can fit 6 chars.
        // "123456"
        assertEquals("　　123456", lines.get(0).text);

        // Second line: 100px width -> 10 chars
        // "7890123456"
        assertEquals("7890123456", lines.get(1).text);

        // Third line: remaining
        // "7890"
        assertEquals("7890", lines.get(2).text);
    }

    @Test
    public void testCalcFromLast_SimpleText() {
        // We know from previous test that it fits in one page.
        // Last index: line 0, char 19 (length - 1)
        paginator.setPageEnd(0, 19);
        paginator.calcFromLast();

        List<LineInfo> lines = paginator.getLineInfoList();
        assertFalse(lines.isEmpty());

        assertEquals("　　123456", lines.get(0).text);
        assertEquals("7890", lines.get(lines.size() - 1).text);

        assertEquals(0, paginator.getFirstLineIndex());
        assertEquals(0, paginator.getFirstWordIndex());
    }

    @Test
    public void testImagePagination() {
        // Add image content
        OldNovelContentParser.NovelContent imageContent = new OldNovelContentParser.NovelContent();
        imageContent.type = OldNovelContentParser.NovelContentType.IMAGE;
        imageContent.content = "http://example.com/img.jpg";
        contentList.add(imageContent);
        loader = new WenkuReaderLoaderXML(contentList); // Re-init loader with image

        paginator = new WenkuReaderPaginator(loader, textMeasurer, 100, 200, 20, 10, 20);

        // Start from image (index 1)
        paginator.setPageStart(1, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();
        assertEquals(1, lines.size());
        assertEquals(WenkuReaderLoader.ElementType.IMAGE_DEPENDENT, lines.get(0).type);
        assertEquals("http://example.com/img.jpg", lines.get(0).text);
    }
}
