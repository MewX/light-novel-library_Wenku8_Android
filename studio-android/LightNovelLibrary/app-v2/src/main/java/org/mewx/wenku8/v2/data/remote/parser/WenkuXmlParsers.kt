package org.mewx.wenku8.v2.data.remote.parser

import org.mewx.wenku8.v2.data.repository.ParsedChapterToc
import org.mewx.wenku8.v2.data.repository.ParsedVolumeToc
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

data class ParsedNovelFullMeta(
    val aid: Int,
    val title: String,
    val author: String = "",
    val dayHitsCount: Int = 0,
    val totalHitsCount: Int = 0,
    val pushCount: Int = 0,
    val favCount: Int = 0,
    val pressId: String = "",
    val bookStatus: String = "",
    val bookLength: Int = 0,
    val lastUpdate: String = "",
    val latestSectionCid: Int = 0,
    val latestSectionName: String = "",
    val tagsCsv: String = "",
)

data class ParsedNovelListItem(
    val aid: Int,
    val title: String = "",
    val author: String = "",
    val bookStatus: String = "",
    val lastUpdate: String = "",
    val tagsCsv: String = "",
    val introShort: String = "",
    val totalHits: Int = 0,
    val pushCount: Int = 0,
    val favCount: Int = 0,
)

data class ParsedNovelListPage(
    val totalPages: Int,
    val items: List<ParsedNovelListItem>,
)

data class ParsedCloudBookshelfEntry(
    val aid: Int,
    val lastUpdateDate: String = "",
    val title: String = "",
    val latestChapterCid: Int = 0,
    val latestChapterName: String = "",
)

data class ParsedUserInfo(
    val username: String,
    val nickname: String = "",
    val uid: Int = 0,
    val score: Int = 0,
    val experience: Int = 0,
    val rank: String = "",
)

data class ParsedReviewThreadItem(
    val rid: Int,
    val title: String,
    val uid: Int,
    val userName: String,
    val replyCount: Int,
    val postTimeFormatted: String,
    val lastReplyTimeFormatted: String,
)

data class ParsedReviewThreadPage(
    val totalPages: Int,
    val items: List<ParsedReviewThreadItem>,
)

data class ParsedReviewReplyItem(
    val uid: Int,
    val userName: String,
    val replyTimeFormatted: String,
    val content: String,
)

data class ParsedReviewReplyPage(
    val totalPages: Int,
    val items: List<ParsedReviewReplyItem>,
)

/**
 * Clean, fault-tolerant Kotlin parsers for Wenku8 XML endpoints and legacy `v1.31` cached files.
 *
 * Preserves every real-world protocol quirk documented in `:app`'s `Wenku8Parser`,
 * `NovelListWithInfoParser`, `BookshelfListParser`, and `OldNovelContentParser`:
 * - Named attribute lookups (`getAttributeValue(null, "name")`) with positional fallbacks.
 * - Rejects well-formed non-novel XML lacking a `<data name="Title">` element.
 * - Handles unclosed CDATA text inside `<volume vid="..."><![CDATA[Volume Title]]>\n<chapter ...>`
 *   as well as stray `</volume>` tags.
 * - Flattens ideographic spaces (`U+3000`) and hard line breaks in `IntroPreview`.
 */
object WenkuXmlParsers {

    private const val CHAPTER_IMAGE_DELIMITER = "<!--image-->"
    private val IDEOGRAPHIC_AND_ASCII_WHITESPACE = Regex("[\\s\\u3000]+")

    fun parseNovelFullMeta(rawXml: String?): ParsedNovelFullMeta? {
        if (rawXml.isNullOrBlank()) return null

        return try {
            val parser = newPullParser(rawXml)
            var foundTitle = false
            var aid = 0
            var title = ""
            var author = ""
            var dayHits = 0
            var totalHits = 0
            var pushCount = 0
            var favCount = 0
            var pressId = ""
            var bookStatus = ""
            var bookLength = 0
            var lastUpdate = ""
            var latestCid = 0
            var latestName = ""
            var tagsCsv = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "data") {
                    val fieldName = parser.getAttributeValue(null, "name")
                        ?: parser.safePositionalAttribute(0)
                    when (fieldName) {
                        "Title" -> {
                            val rawAid = parser.getAttributeValue(null, "aid")
                                ?: parser.safePositionalAttribute(1)
                            aid = rawAid.toSafeInt()
                            title = parser.nextText().trim()
                            if (aid > 0 && title.isNotEmpty()) {
                                foundTitle = true
                            }
                        }
                        "Author" -> author = parser.readValueAttributeOrText()
                        "DayHitsCount" -> dayHits = parser.readValueAttributeOrText().toSafeInt()
                        "TotalHitsCount" -> totalHits = parser.readValueAttributeOrText().toSafeInt()
                        "PushCount" -> pushCount = parser.readValueAttributeOrText().toSafeInt()
                        "FavCount" -> favCount = parser.readValueAttributeOrText().toSafeInt()
                        "PressId" -> pressId = parser.readValueAttributeOrText()
                        "BookStatus" -> bookStatus = parser.readValueAttributeOrText()
                        "BookLength" -> bookLength = parser.readValueAttributeOrText().toSafeInt()
                        "LastUpdate" -> lastUpdate = parser.readValueAttributeOrText()
                        "Tags" -> tagsCsv = parser.readValueAttributeOrText()
                        "LatestSection" -> {
                            val rawCid = parser.getAttributeValue(null, "cid")
                                ?: parser.safePositionalAttribute(1)
                            latestCid = rawCid.toSafeInt()
                            latestName = parser.nextText().trim()
                        }
                    }
                }
                eventType = parser.next()
            }

            if (!foundTitle) {
                null
            } else {
                ParsedNovelFullMeta(
                    aid = aid,
                    title = title,
                    author = author,
                    dayHitsCount = dayHits,
                    totalHitsCount = totalHits,
                    pushCount = pushCount,
                    favCount = favCount,
                    pressId = pressId,
                    bookStatus = bookStatus,
                    bookLength = bookLength,
                    lastUpdate = lastUpdate,
                    latestSectionCid = latestCid,
                    latestSectionName = latestName,
                    tagsCsv = tagsCsv,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses `do=list` (`{aid}-volume.xml`) into ordered [ParsedVolumeToc] entries.
     *
     * Wenku8's `do=list` XML embeds each volume's title as mixed text/CDATA directly inside
     * `<volume vid="41748"><![CDATA[第一卷 ...]]>` immediately followed by child `<chapter>`
     * elements. Capturing `XmlPullParser.TEXT` events while inside `<volume>` (before `<chapter>`
     * starts) cleanly extracts the volume title without fragile substring indexing, while keeping
     * `Wenku8Parser`'s `CDATA[` fallback as a secondary safety net.
     */
    fun parseVolumeTocList(rawXml: String?): List<ParsedVolumeToc> {
        if (rawXml.isNullOrBlank()) return emptyList()

        val parsedVolumes = ArrayList<MutableVolumeAccumulator>()
        var activeVolume: MutableVolumeAccumulator? = null
        try {
            val parser = newPullParser(rawXml)
            var insideChapterTag = false

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "volume" -> {
                                val vid = (parser.getAttributeValue(null, "vid")
                                    ?: parser.safePositionalAttribute(0)).toSafeInt()
                                activeVolume = if (vid > 0) MutableVolumeAccumulator(vid) else null
                            }
                            "chapter" -> {
                                insideChapterTag = true
                                val cid = (parser.getAttributeValue(null, "cid")
                                    ?: parser.safePositionalAttribute(0)).toSafeInt()
                                val chapterTitle = parser.nextText().trim()
                                insideChapterTag = false
                                if (cid > 0) {
                                    activeVolume?.chapters?.add(
                                        ParsedChapterToc(cid = cid, chapterTitle = chapterTitle),
                                    )
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (!insideChapterTag && activeVolume != null && activeVolume.titleBuilder.isEmpty()) {
                            val trimmedText = parser.text?.trim().orEmpty()
                            if (trimmedText.isNotEmpty()) {
                                activeVolume.titleBuilder.append(trimmedText)
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "volume") {
                            activeVolume?.let { parsedVolumes.add(it) }
                            activeVolume = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (_: Exception) {
            // Preserve all completed volumes (and any in-progress volume that already collected
            // valid chapters) when server-side malformed tail XML or stray closing tags occur.
            activeVolume?.takeIf { it.chapters.isNotEmpty() }?.let { parsedVolumes.add(it) }
        }

        // Secondary fallback matching legacy Wenku8Parser in case a custom pull parser skipped
        // mixed CDATA text before child tags.
        var searchCursor = 0
        for (accumulator in parsedVolumes) {
            val volumeTagPos = rawXml.indexOf("volume", searchCursor)
            if (volumeTagPos == -1) break
            val cdataPos = rawXml.indexOf("CDATA[", volumeTagPos)
            if (cdataPos == -1) break
            val cdataEnd = rawXml.indexOf("]]", cdataPos + 6)
            if (cdataEnd == -1) break
            if (accumulator.titleBuilder.isEmpty()) {
                accumulator.titleBuilder.append(rawXml.substring(cdataPos + 6, cdataEnd).trim())
            }
            searchCursor = cdataEnd + 2
        }

        return parsedVolumes.map { accumulator ->
            ParsedVolumeToc(
                vid = accumulator.vid,
                volumeTitle = accumulator.titleBuilder.toString(),
                chapters = accumulator.chapters.toList(),
            )
        }
    }

    fun parseNovelListWithInfo(rawXml: String?): ParsedNovelListPage? {
        if (rawXml.isNullOrBlank()) return null

        return try {
            val parser = newPullParser(rawXml)
            var sawResultRoot = false
            var totalPages = 0
            val items = ArrayList<ParsedNovelListItem>()
            var currentAid = 0
            var insideItem = false
            var title = ""
            var author = ""
            var status = ""
            var lastUpdate = ""
            var tags = ""
            var introShort = ""
            var totalHits = 0
            var pushCount = 0
            var favCount = 0

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "result" -> sawResultRoot = true
                            "page" -> {
                                sawResultRoot = true
                                totalPages = parser.getAttributeValue(null, "num").toSafeInt()
                            }
                            "item" -> {
                                currentAid = parser.getAttributeValue(null, "aid").toSafeInt()
                                insideItem = currentAid > 0
                                title = ""
                                author = ""
                                status = ""
                                lastUpdate = ""
                                tags = ""
                                introShort = ""
                                totalHits = 0
                                pushCount = 0
                                favCount = 0
                            }
                            "data" -> if (insideItem) {
                                when (parser.getAttributeValue(null, "name")) {
                                    "Title" -> title = parser.readValueAttributeOrText()
                                    "Author" -> author = parser.readValueAttributeOrText()
                                    "BookStatus" -> status = parser.readValueAttributeOrText()
                                    "LastUpdate" -> lastUpdate = parser.readValueAttributeOrText()
                                    "Tags" -> tags = parser.readValueAttributeOrText()
                                    "IntroPreview" -> {
                                        introShort = parser.readValueAttributeOrText()
                                            .replace(IDEOGRAPHIC_AND_ASCII_WHITESPACE, " ")
                                            .trim()
                                    }
                                    "TotalHitsCount" -> totalHits = parser.readValueAttributeOrText().toSafeInt()
                                    "PushCount" -> pushCount = parser.readValueAttributeOrText().toSafeInt()
                                    "FavCount" -> favCount = parser.readValueAttributeOrText().toSafeInt()
                                }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "item" && insideItem) {
                            items.add(
                                ParsedNovelListItem(
                                    aid = currentAid,
                                    title = title,
                                    author = author,
                                    bookStatus = status,
                                    lastUpdate = lastUpdate,
                                    tagsCsv = tags,
                                    introShort = introShort,
                                    totalHits = totalHits,
                                    pushCount = pushCount,
                                    favCount = favCount,
                                ),
                            )
                            insideItem = false
                        }
                    }
                }
                eventType = parser.next()
            }

            if (sawResultRoot) ParsedNovelListPage(totalPages = totalPages, items = items) else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses `action=bookcase` XML. Returns `emptyList()` for a genuinely empty shelf (`<metadata/>`)
     * and `null` for non-bookshelf or malformed XML so cloud sync never misinterprets an error page
     * as an empty account shelf.
     */
    fun parseCloudBookshelfList(rawXml: String?): List<ParsedCloudBookshelfEntry>? {
        if (rawXml.isNullOrBlank()) return null

        return try {
            val parser = newPullParser(rawXml)
            var sawMetadataRoot = false
            val entries = ArrayList<ParsedCloudBookshelfEntry>()
            var activeAid = 0
            var activeDate = ""
            var activeName = ""
            var activeLatestCid = 0
            var activeLatestChapterName = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "metadata" -> sawMetadataRoot = true
                            "book" -> {
                                activeAid = parser.getAttributeValue(null, "aid").toSafeInt()
                                activeDate = parser.getAttributeValue(null, "date")?.trim().orEmpty()
                                activeName = ""
                                activeLatestCid = 0
                                activeLatestChapterName = ""
                            }
                            "name" -> if (activeAid > 0) {
                                activeName = parser.nextText().trim()
                            }
                            "chapter" -> if (activeAid > 0) {
                                activeLatestCid = parser.getAttributeValue(null, "cid").toSafeInt()
                                activeLatestChapterName = parser.nextText().trim()
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "book" && activeAid > 0) {
                            entries.add(
                                ParsedCloudBookshelfEntry(
                                    aid = activeAid,
                                    lastUpdateDate = activeDate,
                                    title = activeName,
                                    latestChapterCid = activeLatestCid,
                                    latestChapterName = activeLatestChapterName,
                                ),
                            )
                            activeAid = 0
                        }
                    }
                }
                eventType = parser.next()
            }

            if (sawMetadataRoot) entries else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses `do=bookinfo` (`Wenku8API.getNovelShortInfoUpdate_CV`) used to hydrate bare `aid`
     * search results and repair sparse feed rows. Rejects non-`<metadata>` or title-less XML.
     */
    fun parseNovelShortInfoUpdate(rawXml: String?): ParsedNovelListItem? {
        if (rawXml.isNullOrBlank()) return null

        return try {
            val parser = newPullParser(rawXml)
            var sawMetadataRoot = false
            var aid = 0
            var title = ""
            var author = ""
            var bookStatus = ""
            var lastUpdate = ""
            var introShort = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "metadata" -> sawMetadataRoot = true
                        "data" -> if (sawMetadataRoot) {
                            val fieldName = parser.getAttributeValue(null, "name")
                                ?: parser.safePositionalAttribute(0)
                            when (fieldName) {
                                "Title" -> {
                                    val rawAid = parser.getAttributeValue(null, "aid")
                                        ?: parser.safePositionalAttribute(1)
                                    aid = rawAid.toSafeInt()
                                    title = parser.nextText().trim()
                                }
                                "Author" -> {
                                    author = (parser.getAttributeValue(null, "value")
                                        ?: parser.safePositionalAttribute(1)
                                        ?: parser.nextText()).trim()
                                }
                                "BookStatus" -> {
                                    bookStatus = (parser.getAttributeValue(null, "value")
                                        ?: parser.safePositionalAttribute(1)
                                        ?: parser.nextText()).trim()
                                }
                                "LastUpdate" -> {
                                    lastUpdate = (parser.getAttributeValue(null, "value")
                                        ?: parser.safePositionalAttribute(1)
                                        ?: parser.nextText()).trim()
                                }
                                "IntroPreview" -> {
                                    introShort = parser.readValueAttributeOrText()
                                        .replace(IDEOGRAPHIC_AND_ASCII_WHITESPACE, " ")
                                        .trim()
                                }
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (!sawMetadataRoot || aid <= 0 || title.isEmpty()) {
                null
            } else {
                ParsedNovelListItem(
                    aid = aid,
                    title = title,
                    author = author,
                    bookStatus = bookStatus,
                    lastUpdate = lastUpdate,
                    introShort = introShort,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses `action=search` XML (`<result><item aid='1306'/>...</result>`).
     *
     * Returns `emptyList()` when a valid `<result>` document contains zero matches, and `null`
     * when the payload is an error code (e.g. `"0"` / `"11"` cooldown) or gateway HTML.
     */
    fun parseSearchResultAidList(rawXml: String?): List<Int>? {
        if (rawXml.isNullOrBlank()) return null

        return try {
            val parser = newPullParser(rawXml)
            var sawResultRoot = false
            val orderedUniqueAids = LinkedHashSet<Int>()

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "result", "metadata", "package" -> sawResultRoot = true
                        "item" -> {
                            sawResultRoot = true
                            val aid = (parser.getAttributeValue(null, "aid")
                                ?: parser.safePositionalAttribute(0)).toSafeInt()
                            if (aid > 0) {
                                orderedUniqueAids.add(aid)
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            if (sawResultRoot) orderedUniqueAids.toList() else null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses `Wenku8API.getUserInfoParams()` (`<metadata><item name="uname">...</item>...</metadata>`).
     *
     * Rejects well-formed non-userinfo XML (such as captive portals or CDN interstitials) unless
     * both `<metadata>` and a non-empty `<item name="uname">` element are present.
     */
    fun parseUserInfo(rawXml: String?): ParsedUserInfo? {
        if (rawXml.isNullOrBlank()) return null

        return try {
            val parser = newPullParser(rawXml)
            var sawMetadataRoot = false
            var username: String? = null
            var nickname = ""
            var uid = 0
            var score = 0
            var experience = 0
            var rank = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "metadata" -> sawMetadataRoot = true
                        "item" -> if (sawMetadataRoot) {
                            val attrName = parser.getAttributeValue(null, "name")
                                ?: parser.safePositionalAttribute(0)
                            when (attrName) {
                                "uname" -> username = parser.nextText().trim()
                                "nickname" -> nickname = parser.nextText().trim()
                                "uid" -> uid = parser.nextText().toSafeInt()
                                "score" -> score = parser.nextText().toSafeInt()
                                "experience" -> experience = parser.nextText().toSafeInt()
                                "rank" -> rank = parser.nextText().trim()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            val resolvedUsername = username?.takeIf { it.isNotEmpty() } ?: return null
            if (!sawMetadataRoot) return null

            ParsedUserInfo(
                username = resolvedUsername,
                nickname = nickname.ifEmpty { resolvedUsername },
                uid = uid,
                score = score,
                experience = experience,
                rank = rank,
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses `Wenku8API.getCommentListParams(aid, page)` (`action=review&do=list`).
     *
     * Rejects captive-portal / interstitial HTML unless `<page>`, `<metadata>`, `<package>`, or
     * `<result>` is encountered.
     */
    fun parseReviewThreadListPage(rawXml: String?): ParsedReviewThreadPage? {
        if (rawXml.isNullOrBlank()) return null

        return try {
            val parser = newPullParser(rawXml)
            var sawValidRoot = false
            var totalPages = 1
            val items = ArrayList<ParsedReviewThreadItem>()

            var inItem = false
            var currentRid = 0
            var currentReplyCount = 0
            var currentPostTime = ""
            var currentReplyTime = ""
            var currentUid = 0
            var currentUserName = ""
            var currentTitle = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "metadata", "package", "result" -> sawValidRoot = true
                        "page" -> {
                            sawValidRoot = true
                            val parsedPages = (parser.getAttributeValue(null, "num")
                                ?: parser.safePositionalAttribute(0)).toSafeInt()
                            if (parsedPages >= 0) {
                                totalPages = parsedPages.coerceAtLeast(1)
                            }
                        }
                        "item" -> {
                            sawValidRoot = true
                            inItem = true
                            currentRid = parser.getAttributeValue(null, "rid").toSafeInt()
                            currentReplyCount = parser.getAttributeValue(null, "replies").toSafeInt()
                            currentPostTime = formatCompactWenkuTimestamp(
                                parser.getAttributeValue(null, "posttime"),
                            )
                            currentReplyTime = formatCompactWenkuTimestamp(
                                parser.getAttributeValue(null, "replytime"),
                            )
                            currentUid = 0
                            currentUserName = ""
                            currentTitle = ""
                        }
                        "user" -> if (inItem) {
                            currentUid = (parser.getAttributeValue(null, "uid")
                                ?: parser.safePositionalAttribute(0)).toSafeInt()
                            currentUserName = parser.nextText().trim()
                        }
                        "content" -> if (inItem) {
                            currentTitle = parser.nextText().trim()
                        }
                    }

                    XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                        inItem = false
                        if (currentRid > 0) {
                            items.add(
                                ParsedReviewThreadItem(
                                    rid = currentRid,
                                    title = currentTitle,
                                    uid = currentUid,
                                    userName = currentUserName,
                                    replyCount = currentReplyCount.coerceAtLeast(0),
                                    postTimeFormatted = currentPostTime,
                                    lastReplyTimeFormatted = currentReplyTime.ifEmpty { currentPostTime },
                                ),
                            )
                        }
                    }
                }
                eventType = parser.next()
            }

            if (sawValidRoot) {
                ParsedReviewThreadPage(
                    totalPages = totalPages,
                    items = items,
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses `Wenku8API.getCommentContentParams(rid, page)` (`action=review&do=show`).
     */
    fun parseReviewReplyPage(rawXml: String?): ParsedReviewReplyPage? {
        if (rawXml.isNullOrBlank()) return null

        return try {
            val parser = newPullParser(rawXml)
            var sawValidRoot = false
            var totalPages = 1
            val items = ArrayList<ParsedReviewReplyItem>()

            var inItem = false
            var currentReplyTime = ""
            var currentUid = 0
            var currentUserName = ""
            var currentContent = ""

            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> when (parser.name) {
                        "metadata", "package", "result" -> sawValidRoot = true
                        "page" -> {
                            sawValidRoot = true
                            val parsedPages = (parser.getAttributeValue(null, "num")
                                ?: parser.safePositionalAttribute(0)).toSafeInt()
                            if (parsedPages >= 0) {
                                totalPages = parsedPages.coerceAtLeast(1)
                            }
                        }
                        "item" -> {
                            sawValidRoot = true
                            inItem = true
                            currentReplyTime = formatCompactWenkuTimestamp(
                                parser.getAttributeValue(null, "timestamp"),
                            )
                            currentUid = 0
                            currentUserName = ""
                            currentContent = ""
                        }
                        "user" -> if (inItem) {
                            currentUid = (parser.getAttributeValue(null, "uid")
                                ?: parser.safePositionalAttribute(0)).toSafeInt()
                            currentUserName = parser.nextText().trim()
                        }
                        "content" -> if (inItem) {
                            currentContent = parser.nextText().trim()
                        }
                    }

                    XmlPullParser.END_TAG -> if (parser.name == "item" && inItem) {
                        inItem = false
                        items.add(
                            ParsedReviewReplyItem(
                                uid = currentUid,
                                userName = currentUserName,
                                replyTimeFormatted = currentReplyTime,
                                content = currentContent,
                            ),
                        )
                    }
                }
                eventType = parser.next()
            }

            if (sawValidRoot) {
                ParsedReviewReplyPage(
                    totalPages = totalPages,
                    items = items,
                )
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Converts Wenku8's compact `YYYYMMDDHHmmss` timestamp (`20240518143022`) into
     * `"2024-05-18 14:30"` without legacy `GregorianCalendar` mutability or timezone drift.
     */
    internal fun formatCompactWenkuTimestamp(raw: String?): String {
        val clean = raw?.trim().orEmpty()
        if (clean.length >= 12 && clean.take(12).all { it.isDigit() }) {
            val year = clean.substring(0, 4)
            val month = clean.substring(4, 6)
            val day = clean.substring(6, 8)
            val hour = clean.substring(8, 10)
            val minute = clean.substring(10, 12)
            return "$year-$month-$day $hour:$minute"
        }
        return clean
    }

    /**
     * Extracts all inline `<!--image-->URL<!--image-->` illustration URLs from a chapter body.
     */
    fun extractChapterImageUrls(rawChapterText: String): List<String> {
        if (!rawChapterText.contains(CHAPTER_IMAGE_DELIMITER)) return emptyList()
        val urls = ArrayList<String>()
        var searchOffset = 0
        while (true) {
            val openMarkerIndex = rawChapterText.indexOf(CHAPTER_IMAGE_DELIMITER, searchOffset)
            if (openMarkerIndex == -1) break
            val urlStart = openMarkerIndex + CHAPTER_IMAGE_DELIMITER.length
            val closeMarkerIndex = rawChapterText.indexOf(CHAPTER_IMAGE_DELIMITER, urlStart)
            if (closeMarkerIndex == -1) break
            val extractedUrl = rawChapterText.substring(urlStart, closeMarkerIndex).trim()
            if (extractedUrl.isNotEmpty()) {
                urls.add(extractedUrl)
            }
            searchOffset = closeMarkerIndex + CHAPTER_IMAGE_DELIMITER.length
        }
        return urls
    }

    /**
     * Exact 1:1 port of `GlobalConfig.generateImageFileNameByURL(url)` so `v2.0` locates every
     * offline illustration saved by `v1.31` in `saves/imgs/` without renaming or copying files!
     *
     * Example:
     * `http://pic.wenku8.cn/pictures/1/1305/41759/50471.jpg` -> `pictures113054175950471.jpg`
     */
    fun generateLegacyImageFileName(imageUrl: String): String {
        val cleanUrl = imageUrl.substringBefore('?').trim()
        val segments = cleanUrl.split("/")
        val builder = StringBuilder()
        var passedDomainSegment = false
        for (segment in segments) {
            if (!passedDomainSegment && segment.contains(".")) {
                passedDomainSegment = true
            } else if (passedDomainSegment) {
                builder.append(segment)
            }
        }
        return builder.toString()
    }

    private class MutableVolumeAccumulator(val vid: Int) {
        val titleBuilder = StringBuilder()
        val chapters = ArrayList<ParsedChapterToc>()
    }

    private fun newPullParser(xml: String): XmlPullParser {
        val sanitizedXml = xml.trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        val factory = XmlPullParserFactory.newInstance()
        return factory.newPullParser().apply {
            setInput(StringReader(sanitizedXml))
        }
    }

    private fun XmlPullParser.safePositionalAttribute(index: Int): String? {
        return if (index in 0 until attributeCount) getAttributeValue(index) else null
    }

    private fun XmlPullParser.readValueAttributeOrText(): String {
        val attrValue = getAttributeValue(null, "value")
        return (attrValue ?: nextText()).trim()
    }

    private fun String?.toSafeInt(): Int {
        if (this.isNullOrBlank()) return 0
        return this.trim().toIntOrNull() ?: 0
    }
}
