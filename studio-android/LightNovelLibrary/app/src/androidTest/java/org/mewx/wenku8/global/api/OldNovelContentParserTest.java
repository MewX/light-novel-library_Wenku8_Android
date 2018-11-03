package org.mewx.wenku8.global.api;

import org.junit.Test;

import java.util.List;

import static org.mewx.wenku8.global.api.OldNovelContentParser.NovelContentType.*;

import static org.junit.Assert.assertEquals;

public class OldNovelContentParserTest {
    private static final String NOVEL_CONTENT = "line 1\r\n" +
            "    <!--image-->http://bbbbb.com/pictures/1/1339/90903/107724.jpg<!--image-->   \r\n" +
            "    line 2     \r\n\r\n" +
            "<!--image-->http://bbbbb.com/pictures/1/1339/90903/107725.jpg<!--image-->\r\n" +
            "line 3\r\n";

    private static final String NOVEL_CONTENt_BROKEN_IMAGE = "line 1\r\n" +
            "    <!--image-->http://bbbbb.com/pictures/1/1339/90903/107724.jpg<!--image-->   \r\n" +
            "    line 2     \r\n\r\n" +
            "  <!--image-->http://bbbbb.com/pictures/1/1339/90903/107725.jpg \r\n" +
            "line 3\r\n";

    @Test
    public void parseNovelContent() {
        List<OldNovelContentParser.NovelContent> contents = OldNovelContentParser.parseNovelContent(NOVEL_CONTENT, null);
        assertEquals(5, contents.size());

        OldNovelContentParser.NovelContent tempContent = contents.get(0);
        assertEquals(TEXT, tempContent.type);
        assertEquals("line 1", tempContent.content);

        tempContent = contents.get(1);
        assertEquals(IMAGE, tempContent.type);
        assertEquals("http://bbbbb.com/pictures/1/1339/90903/107724.jpg", tempContent.content);

        tempContent = contents.get(2);
        assertEquals(TEXT, tempContent.type);
        assertEquals("line 2", tempContent.content);

        tempContent = contents.get(3);
        assertEquals(IMAGE, tempContent.type);
        assertEquals("http://bbbbb.com/pictures/1/1339/90903/107725.jpg", tempContent.content);

        tempContent = contents.get(4);
        assertEquals(TEXT, tempContent.type);
        assertEquals("line 3", tempContent.content);
    }

    @Test
    public void parseNovelContentWithIncompleteImageTag() {
        List<OldNovelContentParser.NovelContent> contents = OldNovelContentParser.parseNovelContent(NOVEL_CONTENt_BROKEN_IMAGE, null);
        assertEquals(5, contents.size());

        OldNovelContentParser.NovelContent tempContent = contents.get(0);
        assertEquals(TEXT, tempContent.type);
        assertEquals("line 1", tempContent.content);

        tempContent = contents.get(1);
        assertEquals(IMAGE, tempContent.type);
        assertEquals("http://bbbbb.com/pictures/1/1339/90903/107724.jpg", tempContent.content);

        tempContent = contents.get(2);
        assertEquals(TEXT, tempContent.type);
        assertEquals("line 2", tempContent.content);

        tempContent = contents.get(3);
        assertEquals(TEXT, tempContent.type);
        assertEquals("<!--image-->http://bbbbb.com/pictures/1/1339/90903/107725.jpg", tempContent.content);

        tempContent = contents.get(4);
        assertEquals(TEXT, tempContent.type);
        assertEquals("line 3", tempContent.content);
    }

    @Test
    public void novelContentParser_onlyImage() {
        List<OldNovelContentParser.NovelContent> contents = OldNovelContentParser.NovelContentParser_onlyImage(NOVEL_CONTENT);
        assertEquals(2, contents.size());

        OldNovelContentParser.NovelContent tempContent = contents.get(0);
        assertEquals(IMAGE, tempContent.type);
        assertEquals("http://bbbbb.com/pictures/1/1339/90903/107724.jpg", tempContent.content);

        tempContent = contents.get(1);
        assertEquals(IMAGE, tempContent.type);
        assertEquals("http://bbbbb.com/pictures/1/1339/90903/107725.jpg", tempContent.content);
    }

    @Test
    public void novelContentParser_onlyImageBroken() {
        List<OldNovelContentParser.NovelContent> contents = OldNovelContentParser.NovelContentParser_onlyImage(NOVEL_CONTENt_BROKEN_IMAGE);
        assertEquals(1, contents.size());

        OldNovelContentParser.NovelContent tempContent = contents.get(0);
        assertEquals(IMAGE, tempContent.type);
        assertEquals("http://bbbbb.com/pictures/1/1339/90903/107724.jpg", tempContent.content);
    }
}