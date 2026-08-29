package org.mewx.wenku8.global.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Robolectric is required: NovelItemInfoUpdate holds an android.util.LruCache, which is a no-op
 * stub under the plain JVM test runtime.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BookshelfFilterTest {

    /** A shelf row as convertFromMeta builds one: no synopsis, a latest chapter instead. */
    private static NovelItemInfoUpdate shelfRow(int aid, String title, String author,
                                                String latestChapter) {
        NovelItemInfoUpdate row = new NovelItemInfoUpdate(aid);
        row.title = title;
        row.author = author;
        row.latest_chapter = latestChapter;
        return row;
    }

    private static List<NovelItemInfoUpdate> shelf() {
        return new ArrayList<>(Arrays.asList(
                shelfRow(1, "Re:Zero", "Tappei", "Chapter 40"),
                shelfRow(2, "冰雪少女", "山田有", "第二卷 插图"),
                shelfRow(3, "关于我转生变成史莱姆这档事", "伏濑", "第十卷")));
    }

    private static List<Integer> aidsOf(List<NovelItemInfoUpdate> result) {
        List<Integer> aids = new ArrayList<>();
        for (NovelItemInfoUpdate n : result) {
            aids.add(n.aid);
        }
        return aids;
    }

    @Test
    public void aBlankQueryKeepsTheWholeShelfInOrder() {
        assertEquals(Arrays.asList(1, 2, 3), aidsOf(BookshelfFilter.filter(shelf(), "")));
        assertEquals(Arrays.asList(1, 2, 3), aidsOf(BookshelfFilter.filter(shelf(), "   ")));
        assertEquals(Arrays.asList(1, 2, 3), aidsOf(BookshelfFilter.filter(shelf(), null)));
    }

    @Test
    public void matchesPartOfATitle() {
        assertEquals(Collections.singletonList(2), aidsOf(BookshelfFilter.filter(shelf(), "少女")));
    }

    @Test
    public void matchesAnAuthor() {
        assertEquals(Collections.singletonList(3), aidsOf(BookshelfFilter.filter(shelf(), "伏濑")));
    }

    /** The bookshelf shows the latest chapter where other lists show a synopsis, so it is searched. */
    @Test
    public void matchesTheLatestChapter() {
        assertEquals(Collections.singletonList(2), aidsOf(BookshelfFilter.filter(shelf(), "插图")));
    }

    @Test
    public void latinMatchingIgnoresCase() {
        assertEquals(Collections.singletonList(1), aidsOf(BookshelfFilter.filter(shelf(), "re")));
        assertEquals(Collections.singletonList(1), aidsOf(BookshelfFilter.filter(shelf(), "TAPPEI")));
    }

    @Test
    public void surroundingSpacesAreIgnored() {
        assertEquals(Collections.singletonList(2), aidsOf(BookshelfFilter.filter(shelf(), "  少女  ")));
    }

    @Test
    public void aQueryMatchingNothingGivesNothing() {
        assertTrue(BookshelfFilter.filter(shelf(), "そんな小説はない").isEmpty());
    }

    @Test
    public void severalMatchesKeepTheShelfOrder() {
        List<NovelItemInfoUpdate> rows = shelf();
        rows.get(0).author = "同一个作者";
        rows.get(2).author = "同一个作者";

        assertEquals(Arrays.asList(1, 3), aidsOf(BookshelfFilter.filter(rows, "同一个作者")));
    }

    /**
     * The sentinel trap. convertFromMeta never sets intro_short, so every shelf row carries
     * "Loading..." in a field the bookshelf does not display. Searching it would make that one
     * word select the entire shelf while matching nothing on screen.
     */
    @Test
    public void theLoadingSentinelIsNotSearchable() {
        assertTrue("the placeholder must not be matchable",
                BookshelfFilter.filter(shelf(), "loading").isEmpty());
        assertTrue(BookshelfFilter.filter(shelf(), NovelItemInfoUpdate.LOADING_STRING).isEmpty());
    }

    @Test
    public void aRowStillWaitingOnItsChapterIsNotMatchedByTheSentinel() {
        NovelItemInfoUpdate pending = shelfRow(9, "一本小说", "作者", NovelItemInfoUpdate.LOADING_STRING);

        assertTrue(BookshelfFilter.filter(Collections.singletonList(pending), "load").isEmpty());
        assertEquals(Collections.singletonList(9),
                aidsOf(BookshelfFilter.filter(Collections.singletonList(pending), "一本")));
    }

    @Test
    public void aNullShelfGivesNothingRatherThanFailing() {
        assertTrue(BookshelfFilter.filter(null, "anything").isEmpty());
    }

    @Test
    public void theResultIsASeparateListFromTheShelf() {
        List<NovelItemInfoUpdate> rows = shelf();
        List<NovelItemInfoUpdate> all = BookshelfFilter.filter(rows, "");
        all.clear();

        assertEquals("filtering must not disturb the caller's shelf", 3, rows.size());
    }
}
