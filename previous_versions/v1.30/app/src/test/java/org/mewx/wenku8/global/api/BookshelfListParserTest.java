package org.mewx.wenku8.global.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.List;

/**
 * Robolectric is required: the parser uses XmlPullParser, which is a no-op stub under the plain
 * JVM runtime and makes every parse silently return null.
 *
 * <p>The fixtures mirror the live response's shape -- CRLF line endings, blank lines between
 * books, indented children, CDATA for both text nodes, and the id followed by a date on the book
 * element -- with placeholder titles. They are structural copies, not captured content.
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class BookshelfListParserTest {

    private static final String TWO_BOOKS =
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n"
                    + "<metadata>\r\n"
                    + "\r\n"
                    + "<book aid=\"3988\" date=\"2026-08-27\">\r\n"
                    + "     <name><![CDATA[the first novel]]></name>\r\n"
                    + "     <chapter cid=\"178219\"><![CDATA[the first chapter]]></chapter>\r\n"
                    + "</book>\r\n"
                    + "\r\n"
                    + "<book aid=\"1508\" date=\"2026-08-21\">\r\n"
                    + "     <name><![CDATA[the second novel]]></name>\r\n"
                    + "     <chapter cid=\"178076\"><![CDATA[the second chapter]]></chapter>\r\n"
                    + "</book>\r\n"
                    + "\r\n"
                    + "</metadata>";

    @Test
    public void readsEveryFieldOfABook() {
        List<BookshelfListParser.Entry> shelf = BookshelfListParser.parse(TWO_BOOKS);

        assertNotNull(shelf);
        assertEquals(2, shelf.size());
        BookshelfListParser.Entry first = shelf.get(0);
        assertEquals(3988, first.aid);
        assertEquals("2026-08-27", first.date);
        assertEquals("the first novel", first.name);
        assertEquals(178219, first.latestChapterCid);
        assertEquals("the first chapter", first.latestChapterName);
    }

    @Test
    public void preservesTheOrderTheAccountReturned() {
        List<BookshelfListParser.Entry> shelf = BookshelfListParser.parse(TWO_BOOKS);

        assertNotNull(shelf);
        assertEquals(3988, shelf.get(0).aid);
        assertEquals(1508, shelf.get(1).aid);
    }

    /**
     * The distinction that matters most here. {@link BookshelfSync#plan} reads an empty cloud
     * listing as "the account has nothing" and schedules the entire device shelf for upload, so a
     * parse failure reported as an empty shelf would re-upload everything the reader owns.
     */
    @Test
    public void anEmptyShelfIsAnEmptyListRatherThanNull() {
        List<BookshelfListParser.Entry> shelf = BookshelfListParser.parse(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?>\r\n<metadata>\r\n</metadata>");

        assertNotNull("an empty shelf is a successful parse", shelf);
        assertTrue(shelf.isEmpty());
    }

    @Test
    public void wellFormedXmlThatIsNotAListingIsNull() {
        assertNull(BookshelfListParser.parse("<package><volume vid=\"1\"/></package>"));
    }

    /** The server answers some failures with a bare status code rather than a document. */
    @Test
    public void aBareStatusCodeIsNull() {
        assertNull(BookshelfListParser.parse("1"));
    }

    @Test
    public void nullAndEmptyInputAreNull() {
        assertNull(BookshelfListParser.parse(null));
        assertNull(BookshelfListParser.parse(""));
    }

    @Test
    public void aBookWithoutAChapterStillParses() {
        List<BookshelfListParser.Entry> shelf = BookshelfListParser.parse(
                "<metadata><book aid=\"7\" date=\"2020-01-01\">"
                        + "<name><![CDATA[a novel]]></name></book></metadata>");

        assertNotNull(shelf);
        assertEquals(1, shelf.size());
        assertEquals(7, shelf.get(0).aid);
        assertEquals(0, shelf.get(0).latestChapterCid);
        assertEquals("", shelf.get(0).latestChapterName);
    }

    @Test
    public void aBookWithNoDateStillParses() {
        List<BookshelfListParser.Entry> shelf =
                BookshelfListParser.parse("<metadata><book aid=\"7\"/></metadata>");

        assertNotNull(shelf);
        assertEquals(1, shelf.size());
        assertEquals("", shelf.get(0).date);
    }

    /** One broken record must not cost the reader the rest of the shelf. */
    @Test
    public void aBookWithANonNumericIdIsSkippedAndTheRestSurvive() {
        List<BookshelfListParser.Entry> shelf = BookshelfListParser.parse(
                "<metadata>"
                        + "<book aid=\"oops\" date=\"2020-01-01\"/>"
                        + "<book aid=\"7\" date=\"2020-01-02\"/>"
                        + "</metadata>");

        assertNotNull(shelf);
        assertEquals(1, shelf.size());
        assertEquals(7, shelf.get(0).aid);
    }

    /**
     * The ids here must stay identical to what {@link BookshelfSync#parseCloudAidList} reads from
     * the sibling endpoint, since that is what the sync's plan is still computed from.
     */
    @Test
    public void theIdsAgreeWithTheRegexUsedOnTheSiblingEndpoint() {
        List<BookshelfListParser.Entry> shelf = BookshelfListParser.parse(TWO_BOOKS);

        assertNotNull(shelf);
        assertEquals(BookshelfSync.parseCloudAidList(TWO_BOOKS).size(), shelf.size());
        for (int i = 0; i < shelf.size(); i++) {
            assertEquals(BookshelfSync.parseCloudAidList(TWO_BOOKS).get(i).intValue(),
                    shelf.get(i).aid);
        }
    }
}
