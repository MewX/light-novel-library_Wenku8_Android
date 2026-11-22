package org.mewx.wenku8.global.api;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mewx.wenku8.util.CrashReporter;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/** Parses the "novellist" API: one page of novels, each carrying enough detail to fill a list
 * row, so the list does not need a follow-up request per novel.
 * Created by MewX on 2015/10/20.
 */
public class NovelListWithInfoParser {
    public static class Result {
        public int pageNum;
        public List<NovelItemInfoUpdate> items;

        public Result() {
            this.pageNum = 0;
            this.items = new ArrayList<>();
        }
    }

    /**
     * @param xml the raw response body
     * @return the parsed page, or null if the response was empty or not XML at all
     */
    @Nullable
    public static Result parse(@Nullable String xml) {
        if (xml == null || xml.isEmpty()) {
            return null;
        }

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser parser = factory.newPullParser();
            parser.setInput(new StringReader(xml));

            Result result = new Result();
            NovelItemInfoUpdate current = null;

            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if ("page".equals(parser.getName())) {
                        result.pageNum = parseIntOrZero(parser.getAttributeValue(null, "num"));
                    } else if ("item".equals(parser.getName())) {
                        current = new NovelItemInfoUpdate(
                                parseIntOrZero(parser.getAttributeValue(null, "aid")));
                    } else if ("data".equals(parser.getName()) && current != null) {
                        readData(parser, current);
                    }
                } else if (eventType == XmlPullParser.END_TAG
                        && "item".equals(parser.getName()) && current != null) {
                    // Cache the result so other components can reuse it
                    NovelItemInfoUpdate.putToCache(current);
                    result.items.add(current);
                    current = null;
                }
                eventType = parser.next();
            }
            return result;
        } catch (Exception e) {
            CrashReporter.recordException("NovelListWithInfoParser.parse", e);
            return null;
        }
    }

    private static void readData(@NonNull XmlPullParser parser, @NonNull NovelItemInfoUpdate info)
            throws XmlPullParserException, IOException {
        String name = parser.getAttributeValue(null, "name");
        if (name == null) {
            return;
        }

        switch (name) {
            case "Title":
                info.title = readValue(parser);
                break;
            case "Author":
                info.author = readValue(parser);
                break;
            case "BookStatus":
                info.status = readValue(parser);
                break;
            case "LastUpdate":
                info.update = readValue(parser);
                break;
            case "Tags":
                info.tags = readValue(parser);
                break;
            case "IntroPreview":
                info.intro_short = normalizeIntro(readValue(parser));
                break;
            default:
                // TotalHitsCount, PushCount and FavCount also arrive here; no list row shows them.
                break;
        }
    }

    /**
     * The server is not consistent about how it carries a field: most arrive as a value attribute
     * (&lt;data name="Author" value="..."/&gt;), while Title and IntroPreview currently arrive as
     * CDATA (&lt;data name="Title"&gt;&lt;![CDATA[...]]&gt;&lt;/data&gt;) -- and IntroPreview has
     * been served both ways. Take the attribute where there is one and fall back to the element's
     * text, so either shape parses.
     */
    @NonNull
    private static String readValue(@NonNull XmlPullParser parser)
            throws XmlPullParserException, IOException {
        String value = parser.getAttributeValue(null, "value");
        return value != null ? value : parser.nextText();
    }

    /**
     * Previews come padded with ideographic spaces and, for some novels, hard line breaks. The row
     * showing this is a single ellipsized line, so flatten every run of whitespace down to one
     * plain space -- U+3000 spelled out, since Java's \s covers ASCII whitespace only.
     */
    @NonNull
    private static String normalizeIntro(@NonNull String intro) {
        return intro.replaceAll("[\\s　]+", " ").trim();
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
