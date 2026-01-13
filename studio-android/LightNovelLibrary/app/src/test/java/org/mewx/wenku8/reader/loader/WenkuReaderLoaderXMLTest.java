package org.mewx.wenku8.reader.loader;

import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import org.mewx.wenku8.reader.loader.WenkuReaderLoaderXML;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class WenkuReaderLoaderXMLTest {

    private WenkuReaderLoaderXML loader;
    private List<OldNovelContentParser.NovelContent> contentList;

    @Before
    public void setUp() {
        contentList = new ArrayList<>();

        // Add sample text
        OldNovelContentParser.NovelContent textContent = new OldNovelContentParser.NovelContent();
        textContent.type = OldNovelContentParser.NovelContentType.TEXT;
        textContent.content = "This is a sample text line.";
        contentList.add(textContent);

        // Add sample image
        OldNovelContentParser.NovelContent imageContent = new OldNovelContentParser.NovelContent();
        imageContent.type = OldNovelContentParser.NovelContentType.IMAGE;
        imageContent.content = "http://example.com/image.jpg";
        contentList.add(imageContent);

        // Add another text
        OldNovelContentParser.NovelContent textContent2 = new OldNovelContentParser.NovelContent();
        textContent2.type = OldNovelContentParser.NovelContentType.TEXT;
        textContent2.content = "Another text line.";
        contentList.add(textContent2);

        loader = new WenkuReaderLoaderXML(contentList);
    }

    @Test
    public void testInitialization() {
        assertEquals(3, loader.getElementCount());
        assertEquals(0, loader.getCurrentIndex());
    }

    @Test
    public void testNavigation() {
        // Current (Index 0)
        assertEquals(WenkuReaderLoader.ElementType.TEXT, loader.getCurrentType());
        assertEquals("This is a sample text line.", loader.getCurrentAsString());

        // Next (Index 1)
        assertEquals(WenkuReaderLoader.ElementType.IMAGE_DEPENDENT, loader.getNextType());
        assertEquals("http://example.com/image.jpg", loader.getNextAsString());
        assertEquals(1, loader.getCurrentIndex());

        // Next (Index 2)
        assertEquals(WenkuReaderLoader.ElementType.TEXT, loader.getNextType());
        assertEquals("Another text line.", loader.getNextAsString());
        assertEquals(2, loader.getCurrentIndex());

        // Previous (Index 1)
        assertEquals(WenkuReaderLoader.ElementType.IMAGE_DEPENDENT, loader.getPreviousType());
        assertEquals("http://example.com/image.jpg", loader.getPreviousAsString());
        assertEquals(1, loader.getCurrentIndex());
    }

    @Test
    public void testHasNext() {
        loader.setCurrentIndex(0);
        assertTrue(loader.hasNext(0)); // Has more chars in line 0
        assertTrue(loader.hasNext("This is a sample text line.".length() - 1)); // Has next element

        loader.setCurrentIndex(2);
        assertTrue(loader.hasNext(0)); // Has more chars in line 2
        assertFalse(loader.hasNext("Another text line.".length() - 1)); // End of content
    }

    @Test
    public void testHasPrevious() {
        loader.setCurrentIndex(2);
        assertTrue(loader.hasPrevious(10));
        assertTrue(loader.hasPrevious(0)); // Has previous element

        loader.setCurrentIndex(0);
        assertTrue(loader.hasPrevious(10));
        assertFalse(loader.hasPrevious(0)); // Start of content
    }
}
