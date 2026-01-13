package org.mewx.wenku8.reader.loader;

import org.junit.Test;
import org.mewx.wenku8.global.api.OldNovelContentParser;

import java.util.List;
import java.util.function.IntConsumer;

import static org.junit.Assert.*;

public class WenkuReaderLoaderXMLTest {

    private static final IntConsumer PROGRESSIVE_CONSUMER = unused -> {};

    // Sample text from the user request
    private static final String SAMPLE_BOOK_TEXT =
            " 第三卷 第五十八话 猪肉味噌汤再来  \r\n" +
            " \r\n" +
            "  \r\n" +
            "   \r\n" +
            "  \r\n" +
            " \r\n" +
            "  \r\n" +
            "     \r\n" +
            "     \r\n" +
            "     \r\n" +
            "  \r\n" +
            " \r\n" +
            "  \r\n" +
            "  \r\n" +
            "    填饱饿了两天的肚子后，堤达满足地吐了口气。  \r\n" +
            "  \r\n" +
            "    「呼……」  \r\n" +
            "  \r\n" +
            "    即使遭到风吹雨打，堤达依然努力寻找粮食，并在最后发现了这个神奇的地方。  \r\n" +
            "  \r\n" +
            "    一扇东大陆风格的门丝毫没受到暴风雨的影响，屹立在海边的沙滩上。  ";
    private static final List<OldNovelContentParser.NovelContent> SAMPLE_BOOK_TEXT_NOVEL_CONTENT =
            OldNovelContentParser.parseNovelContent(SAMPLE_BOOK_TEXT, PROGRESSIVE_CONSUMER);

    // Sample image chapter from the user request
    private static final String SAMPLE_IMAGE_TEXT =
            " 第一卷 插图  \r\n" +
            " \r\n" +
            "  \r\n" +
            "   \r\n" +
            "  \r\n" +
            " \r\n" +
            "  \r\n" +
            "     \r\n" +
            "     \r\n" +
            "     \r\n" +
            "  \r\n" +
            " \r\n" +
            "  \r\n" +
            "  \r\n" +
            "   <!--image-->https://pic.777743.xyz/2/2451/91509/108506.jpg<!--image-->       <!--image-->https://pic.777743.xyz/2/2451/91509/108507.jpg<!--image-->       <!--image-->https://pic.777743.xyz/2/2451/91509/108508.jpg<!--image-->";
    private static final List<OldNovelContentParser.NovelContent> SAMPLE_BOOK_IMAGE_NOVEL_CONTENT =
            OldNovelContentParser.parseNovelContent(SAMPLE_IMAGE_TEXT, PROGRESSIVE_CONSUMER);


    @Test
    public void testTextLoaderParsing() {
        // The parser filters out empty lines (lines with only spaces).
        // Expected content:
        // 0: 第三卷 第五十八话 猪肉味噌汤再来
        // 1: 填饱饿了两天的肚子后，堤达满足地吐了口气。
        // 2: 「呼……」
        // 3: 即使遭到风吹雨打，堤达依然努力寻找粮食，并在最后发现了这个神奇的地方。
        // 4: 一扇东大陆风格的门丝毫没受到暴风雨的影响，屹立在海边的沙滩上
        WenkuReaderLoaderXML textLoader = new WenkuReaderLoaderXML(SAMPLE_BOOK_TEXT_NOVEL_CONTENT);
        assertEquals(5, textLoader.getElementCount());

        textLoader.setCurrentIndex(0);
        assertEquals("第三卷 第五十八话 猪肉味噌汤再来", textLoader.getCurrentAsString());

        textLoader.setCurrentIndex(1);
        assertEquals("填饱饿了两天的肚子后，堤达满足地吐了口气。", textLoader.getCurrentAsString());
        assertEquals(WenkuReaderLoader.ElementType.TEXT, textLoader.getCurrentType());

        textLoader.setCurrentIndex(2);
        assertEquals("「呼……」", textLoader.getCurrentAsString());
    }

    @Test
    public void testImageLoaderParsing() {
        // Expected content:
        // 0: 第一卷 插图
        // 1: https://pic.777743.xyz/2/2451/91509/108506.jpg (IMAGE)
        // 2: https://pic.777743.xyz/2/2451/91509/108507.jpg (IMAGE)
        // 3: https://pic.777743.xyz/2/2451/91509/108508.jpg (IMAGE)
        WenkuReaderLoaderXML imageLoader = new WenkuReaderLoaderXML(SAMPLE_BOOK_IMAGE_NOVEL_CONTENT);
        assertEquals(4, imageLoader.getElementCount());

        imageLoader.setCurrentIndex(0);
        assertEquals("第一卷 插图", imageLoader.getCurrentAsString());
        assertEquals(WenkuReaderLoader.ElementType.TEXT, imageLoader.getCurrentType());

        imageLoader.setCurrentIndex(1);
        assertEquals("https://pic.777743.xyz/2/2451/91509/108506.jpg", imageLoader.getCurrentAsString());
        assertEquals(WenkuReaderLoader.ElementType.IMAGE_DEPENDENT, imageLoader.getCurrentType());

        imageLoader.setCurrentIndex(2);
        assertEquals("https://pic.777743.xyz/2/2451/91509/108507.jpg", imageLoader.getCurrentAsString());
        assertEquals(WenkuReaderLoader.ElementType.IMAGE_DEPENDENT, imageLoader.getCurrentType());
    }

    @Test
    public void testNavigationOnRealText() {
        WenkuReaderLoaderXML textLoader = new WenkuReaderLoaderXML(SAMPLE_BOOK_TEXT_NOVEL_CONTENT);
        textLoader.setCurrentIndex(0);
        assertTrue(textLoader.hasNext(0));

        // Advance to next element
        assertEquals("填饱饿了两天的肚子后，堤达满足地吐了口气。", textLoader.getNextAsString());
        assertEquals(1, textLoader.getCurrentIndex());

        // Advance again
        assertEquals("「呼……」", textLoader.getNextAsString());
        assertEquals(2, textLoader.getCurrentIndex());

        // Go back
        assertEquals("填饱饿了两天的肚子后，堤达满足地吐了口气。", textLoader.getPreviousAsString());
        assertEquals(1, textLoader.getCurrentIndex());
    }
}
