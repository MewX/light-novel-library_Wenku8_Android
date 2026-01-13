package org.mewx.wenku8.reader.view;

import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.reader.loader.WenkuReaderLoader;
import org.mewx.wenku8.reader.loader.WenkuReaderLoaderXML;

import java.util.List;
import java.util.function.IntConsumer;

import static org.junit.Assert.*;

public class WenkuReaderPaginatorTest {

    private WenkuReaderPaginator paginator;
    private WenkuReaderLoaderXML loader;
    private TextMeasurer textMeasurer;

    private final String sampleBookText =
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

    @Before
    public void setUp() {
        IntConsumer progressConsumer = (i) -> {};
        List<OldNovelContentParser.NovelContent> contentList = OldNovelContentParser.parseNovelContent(sampleBookText, progressConsumer);
        loader = new WenkuReaderLoaderXML(contentList);

        // Mock measurer: assume each character is 20px wide
        textMeasurer = new TextMeasurer() {
            @Override
            public float measureText(String text) {
                return text.length() * 20;
            }
        };

        // Screen Settings:
        // Width: 400px (20 characters per line)
        // Height: 800px
        // Font height: 30px
        // Line distance: 10px
        // Paragraph distance: 20px
        paginator = new WenkuReaderPaginator(loader, textMeasurer, 400, 800, 30, 10, 20);
    }

    @Test
    public void testFirstPagePagination() {
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
        // if(curWordIndex == 0 && mLoader.getCurrentType() == WenkuReaderLoader.ElementType.TEXT) {
        //     widthSum = 2 * fontHeight; // 60px
        //     tempText = new StringBuilder("　　");
        // }
        // So "　　" + "第三卷..."
        // "第三卷 第五十八话 猪肉味噌汤再来" is 17 chars.
        // Total width: 60px + 17 * 20px = 60 + 340 = 400px.
        // Matches exactly 400px.

        assertEquals("　　第三卷 第五十八话 猪肉味噌汤再来", lines.get(0).text);

        // Next paragraph: "填饱饿了两天的肚子后，堤达满足地吐了口气。"
        // Length 21 chars.
        // Indentation: 60px.
        // Remaining width: 340px -> 17 chars.
        // Line 2: "　　填饱饿了两天的肚子后，堤达满足地吐" (17 chars from text)
        // Line 3: "了口气。" (remaining 4 chars)

        assertTrue(lines.size() > 2);
        assertEquals("　　填饱饿了两天的肚子后，堤达满足地吐", lines.get(1).text);
        assertEquals("了口气。", lines.get(2).text);
    }

    @Test
    public void testPaginationFlow() {
        // Start from index 1 (First actual paragraph)
        // "填饱饿了两天的肚子后，堤达满足地吐了口气。"
        paginator.setPageStart(1, 0);
        paginator.calcFromFirst();

        List<LineInfo> lines = paginator.getLineInfoList();

        // Line 0: "　　填饱饿了两天的肚子后，堤达满足地吐"
        assertEquals("　　填饱饿了两天的肚子后，堤达满足地吐", lines.get(0).text);

        // Line 1: "了口气。"
        assertEquals("了口气。", lines.get(1).text);

        // Next Paragraph: "「呼……」"
        // Line 2: "　　「呼……」"
        assertEquals("　　「呼……」", lines.get(2).text);
    }
}
