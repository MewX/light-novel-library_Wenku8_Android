package org.mewx.wenku8.global.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mewx.wenku8.util.CrashReporter;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses the {@code action=bookcase} listing: the account's shelf, with each novel's last update
 * and latest chapter attached.
 *
 * <p>The sync calls the poorer sibling {@code bookcase&do=list}, which returns bare ids, and then
 * has no way to tell which novels changed -- so bookshelf rows keep whatever metadata they were
 * downloaded with, and the only way to refresh them is to re-download every novel in full. This
 * endpoint answers the same question in the same single request while also carrying the two
 * fields that go stale.
 *
 * <p>Verified against the live endpoint rather than assumed: for a 69-novel shelf it returned the
 * same ids as {@code do=list}, every book carrying a date and exactly one chapter, and for three
 * novels spanning 2011 to 2026 the {@code date} equalled the metadata's {@code LastUpdate} and the
 * chapter's {@code cid} equalled its {@code LatestSection} cid. The one difference worth knowing:
 * the chapter name here omits the volume prefix that {@code LatestSection} carries, so this is a
 * good detector of change and a poorer source for display.
 *
 * <p>Attributes are read by name rather than by position, unlike the older parsers here. The
 * shape has already changed once -- {@code do=list} emits {@code <book aid="..." />} while this
 * one puts a date after the id -- and positional reads are how that kind of change turns into
 * silent nonsense.
 */
public class BookshelfListParser {

    /** One novel on the account's shelf. */
    public static class Entry {
        public int aid;
        /** {@code YYYY-MM-DD}. The same value the metadata endpoint calls {@code LastUpdate}. */
        public String date = "";
        public String name = "";
        /** Matches the metadata endpoint's {@code LatestSection} cid; 0 when absent. */
        public int latestChapterCid;
        /** The chapter title <b>without</b> the volume prefix that {@code LatestSection} has. */
        public String latestChapterName = "";
    }

    /**
     * @param xml the raw response body
     * @return the shelf in the order returned, or null if this was not a usable listing
     *
     * <p>An account with an empty shelf gives an empty list, <b>not</b> null. The difference
     * matters: {@link BookshelfSync#plan} reads an empty cloud listing as "the account has
     * nothing" and schedules the whole device shelf to be uploaded, so a failed parse reported as
     * empty would turn into a pointless re-upload of everything.
     */
    @Nullable
    public static List<Entry> parse(@Nullable String xml) {
        if (xml == null || xml.isEmpty()) {
            return null;
        }

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));

            final List<Entry> entries = new ArrayList<>();
            boolean sawMetadata = false;
            Entry current = null;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    final String tag = parser.getName();
                    if ("metadata".equals(tag)) {
                        sawMetadata = true;
                    } else if ("book".equals(tag)) {
                        current = readBook(parser);
                    } else if (current != null && "name".equals(tag)) {
                        current.name = parser.nextText().trim();
                    } else if (current != null && "chapter".equals(tag)) {
                        current.latestChapterCid =
                                parseIntOrZero(parser.getAttributeValue(null, "cid"));
                        current.latestChapterName = parser.nextText().trim();
                    }
                } else if (eventType == XmlPullParser.END_TAG && "book".equals(parser.getName())) {
                    if (current != null) {
                        entries.add(current);
                        current = null;
                    }
                }
                eventType = parser.next();
            }

            // Well-formed XML that is not a bookshelf listing must not read as an empty shelf.
            return sawMetadata ? entries : null;
        } catch (Exception e) {
            CrashReporter.recordException("BookshelfListParser.parse", e);
            return null;
        }
    }

    /**
     * @return the entry, or null when the id is not a number -- one broken record must not cost
     * the reader the rest of the shelf, which is the rule {@link BookshelfSync#parseCloudAidList}
     * already follows.
     */
    @Nullable
    private static Entry readBook(@NonNull XmlPullParser parser) {
        final String rawAid = parser.getAttributeValue(null, "aid");
        if (rawAid == null) {
            return null;
        }
        try {
            final Entry entry = new Entry();
            entry.aid = Integer.parseInt(rawAid.trim());
            final String date = parser.getAttributeValue(null, "date");
            entry.date = date == null ? "" : date.trim();
            return entry;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int parseIntOrZero(@Nullable String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
