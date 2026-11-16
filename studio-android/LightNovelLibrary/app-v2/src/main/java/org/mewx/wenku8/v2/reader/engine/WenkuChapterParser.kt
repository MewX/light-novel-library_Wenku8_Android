package org.mewx.wenku8.v2.reader.engine

import org.mewx.wenku8.v2.data.local.db.entity.ChapterEntity

/**
 * Immutable structured Abstract Syntax Tree for a single Wenku8 chapter (`do=text`).
 *
 * Separating parsing from pagination (`10-next-gen-reader-engine-architecture.md` §4.1) ensures:
 * 1. Raw `<!--image-->URL<!--image-->` markers are tokenized once on IO load rather than scanned on
 *    every Canvas draw or font-size slider reflow.
 * 2. [ChapterBlock.blockIndex] aligns 1:1 with `v1.31` `OldNovelContentParser` element indices so
 *    migrated `read_saves_v1.wk8` `(lineId, wordId)` reading anchors land on the exact sentence.
 */
data class ChapterAst(
    val aid: Int,
    val vid: Int,
    val cid: Int,
    val volumeTitle: String,
    val chapterTitle: String,
    val isDedicatedIllustrationChapter: Boolean,
    val blocks: List<ChapterBlock>,
    val illustrationBlocks: List<ChapterBlock.IllustrationBlock>,
    val totalCharacters: Int,
) {
    /**
     * Searches this chapter's title and body paragraphs for [query], returning paragraph-anchored
     * character ranges suitable for in-book search jump & highlight (`P5.3`).
     */
    fun findOccurrences(
        query: String,
        ignoreCase: Boolean = true,
        snippetContextChars: Int = 18,
    ): List<ChapterTextMatch> {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return emptyList()

        val matches = ArrayList<ChapterTextMatch>()
        for (block in blocks) {
            val searchableText = when (block) {
                is ChapterBlock.TitleBlock -> block.title
                is ChapterBlock.ParagraphBlock -> block.text
                is ChapterBlock.IllustrationBlock -> continue
            }
            var searchFrom = 0
            while (searchFrom < searchableText.length) {
                val hitIndex = searchableText.indexOf(
                    string = cleanQuery,
                    startIndex = searchFrom,
                    ignoreCase = ignoreCase,
                )
                if (hitIndex < 0) break
                val hitEndExclusive = hitIndex + cleanQuery.length
                val snippetStart = (hitIndex - snippetContextChars).coerceAtLeast(0)
                val snippetEnd = (hitEndExclusive + snippetContextChars).coerceAtMost(searchableText.length)
                val prefixEllipsis = if (snippetStart > 0) "…" else ""
                val suffixEllipsis = if (snippetEnd < searchableText.length) "…" else ""
                matches.add(
                    ChapterTextMatch(
                        cid = cid,
                        blockIndex = block.blockIndex,
                        startCharOffset = hitIndex,
                        endCharOffsetExclusive = hitEndExclusive,
                        matchedText = searchableText.substring(hitIndex, hitEndExclusive),
                        contextSnippet = prefixEllipsis +
                            searchableText.substring(snippetStart, snippetEnd) +
                            suffixEllipsis,
                    ),
                )
                searchFrom = hitIndex + cleanQuery.length.coerceAtLeast(1)
            }
        }
        return matches
    }
}

data class ChapterTextMatch(
    val cid: Int,
    val blockIndex: Int,
    val startCharOffset: Int,
    val endCharOffsetExclusive: Int,
    val matchedText: String,
    val contextSnippet: String,
)

/**
 * Typed AST node inside a [ChapterAst].
 */
sealed interface ChapterBlock {
    val blockIndex: Int

    data class TitleBlock(
        override val blockIndex: Int,
        val title: String,
    ) : ChapterBlock

    data class ParagraphBlock(
        override val blockIndex: Int,
        /** Clean trimmed paragraph prose without leading ideographic indentation. */
        val text: String,
        /** Bunkobon-indented paragraph text (`"　　" + text`) fed into `StaticLayout`. */
        val formattedText: String,
        /** Number of prepended indentation characters in [formattedText] (`2` for `"　　"`). */
        val indentLength: Int,
        /** `true` when the paragraph opens with CJK/Western dialogue quotation marks. */
        val isDialogue: Boolean,
    ) : ChapterBlock

    data class IllustrationBlock(
        override val blockIndex: Int,
        val imageIndexInChapter: Int,
        val imageUrl: String,
        val cachedAspectRatio: Float? = null,
    ) : ChapterBlock
}

/**
 * Layer 1 parser converting raw Wenku8 `do=text` strings into immutable [ChapterAst] instances.
 */
object WenkuChapterParser {

    private const val IMAGE_MARKER = "<!--image-->"
    private const val BUNKOBON_PARAGRAPH_INDENT = "\u3000\u3000"
    private const val MAX_FALLBACK_HEADING_LENGTH = 64

    private val DIALOGUE_OPENERS = charArrayOf(
        '「', '『', '“', '"', '‘', '\'', '（', '(', '《', '〈', '【', '〔',
    )

    fun parse(
        rawChapterText: String,
        aid: Int = 0,
        vid: Int = 0,
        cid: Int = 0,
        volumeTitle: String = "",
        chapterTitle: String = "",
        knownImageAspectRatios: Map<String, Float> = emptyMap(),
    ): ChapterAst {
        val sanitized = rawChapterText.trimStart('\uFEFF')
        val rawLines = sanitized.split('\n')

        val blocks = ArrayList<ChapterBlock>(rawLines.size.coerceAtLeast(4))
        val illustrationBlocks = ArrayList<ChapterBlock.IllustrationBlock>()
        var totalCharacters = 0
        var paragraphCount = 0
        var sawFirstContentElement = false

        for (rawLineWithCr in rawLines) {
            val rawLine = rawLineWithCr.trimEnd('\r')
            if (isBlankOrIdeographicWhitespace(rawLine)) continue

            if (!rawLine.contains(IMAGE_MARKER)) {
                val cleanText = trimAsciiAndIdeographicSpaces(rawLine)
                if (cleanText.isEmpty()) continue

                if (!sawFirstContentElement && isLeadingTitleLine(cleanText, volumeTitle, chapterTitle)) {
                    blocks.add(
                        ChapterBlock.TitleBlock(
                            blockIndex = blocks.size,
                            title = cleanText,
                        ),
                    )
                    totalCharacters += cleanText.length
                    sawFirstContentElement = true
                } else {
                    val paragraphBlock = buildParagraphBlock(
                        blockIndex = blocks.size,
                        cleanText = cleanText,
                    )
                    blocks.add(paragraphBlock)
                    totalCharacters += cleanText.length
                    paragraphCount++
                    sawFirstContentElement = true
                }
            } else {
                // Tokenize one or more inline `<!--image-->URL<!--image-->` markers while preserving
                // any non-blank prose before, between, or after markers on the same line.
                var cursor = 0
                while (cursor < rawLine.length) {
                    val openMarkerIndex = rawLine.indexOf(IMAGE_MARKER, startIndex = cursor)
                    if (openMarkerIndex < 0) {
                        val trailingText = trimAsciiAndIdeographicSpaces(rawLine.substring(cursor))
                        if (trailingText.isNotEmpty()) {
                            blocks.add(buildParagraphBlock(blocks.size, trailingText))
                            totalCharacters += trailingText.length
                            paragraphCount++
                            sawFirstContentElement = true
                        }
                        break
                    }

                    if (openMarkerIndex > cursor) {
                        val leadingText = trimAsciiAndIdeographicSpaces(
                            rawLine.substring(cursor, openMarkerIndex),
                        )
                        if (leadingText.isNotEmpty()) {
                            if (!sawFirstContentElement &&
                                isLeadingTitleLine(leadingText, volumeTitle, chapterTitle)
                            ) {
                                blocks.add(
                                    ChapterBlock.TitleBlock(
                                        blockIndex = blocks.size,
                                        title = leadingText,
                                    ),
                                )
                            } else {
                                blocks.add(buildParagraphBlock(blocks.size, leadingText))
                                paragraphCount++
                            }
                            totalCharacters += leadingText.length
                            sawFirstContentElement = true
                        }
                    }

                    val urlStart = openMarkerIndex + IMAGE_MARKER.length
                    val closeMarkerIndex = rawLine.indexOf(IMAGE_MARKER, startIndex = urlStart)
                    if (closeMarkerIndex < 0) {
                        // Malformed unclosed marker: preserve remaining text defensively rather
                        // than dropping the line.
                        val fallbackText = trimAsciiAndIdeographicSpaces(rawLine.substring(openMarkerIndex))
                        if (fallbackText.isNotEmpty()) {
                            blocks.add(buildParagraphBlock(blocks.size, fallbackText))
                            totalCharacters += fallbackText.length
                            paragraphCount++
                            sawFirstContentElement = true
                        }
                        break
                    }

                    val extractedUrl = rawLine.substring(urlStart, closeMarkerIndex).trim()
                    if (extractedUrl.isNotEmpty()) {
                        val aspectRatio = knownImageAspectRatios[extractedUrl]
                            ?.takeIf { it.isFinite() && it > 0.05f }
                        val illustration = ChapterBlock.IllustrationBlock(
                            blockIndex = blocks.size,
                            imageIndexInChapter = illustrationBlocks.size,
                            imageUrl = extractedUrl,
                            cachedAspectRatio = aspectRatio,
                        )
                        blocks.add(illustration)
                        illustrationBlocks.add(illustration)
                        sawFirstContentElement = true
                    }
                    cursor = closeMarkerIndex + IMAGE_MARKER.length
                }
            }
        }

        val normalizedChapterTitle = chapterTitle.trim()
        val isExplicitIllustrationTitle =
            normalizedChapterTitle == ChapterEntity.ILLUSTRATION_CHAPTER_TITLE_SC ||
                normalizedChapterTitle == ChapterEntity.ILLUSTRATION_CHAPTER_TITLE_TC
        val isDedicatedIllustrationChapter = isExplicitIllustrationTitle ||
            (illustrationBlocks.isNotEmpty() && paragraphCount == 0)

        return ChapterAst(
            aid = aid,
            vid = vid,
            cid = cid,
            volumeTitle = volumeTitle.trim(),
            chapterTitle = normalizedChapterTitle,
            isDedicatedIllustrationChapter = isDedicatedIllustrationChapter,
            blocks = blocks,
            illustrationBlocks = illustrationBlocks,
            totalCharacters = totalCharacters,
        )
    }

    private fun buildParagraphBlock(
        blockIndex: Int,
        cleanText: String,
    ): ChapterBlock.ParagraphBlock {
        val firstChar = cleanText.firstOrNull()
        val isDialogue = firstChar != null && DIALOGUE_OPENERS.any { it == firstChar }
        return ChapterBlock.ParagraphBlock(
            blockIndex = blockIndex,
            text = cleanText,
            formattedText = BUNKOBON_PARAGRAPH_INDENT + cleanText,
            indentLength = BUNKOBON_PARAGRAPH_INDENT.length,
            isDialogue = isDialogue,
        )
    }

    /**
     * Determines whether the first non-blank line in `do=text` is the chapter heading line
     * (Wenku8 `do=text` always starts with e.g. `"第一卷 序章"` or `"序章"`).
     */
    private fun isLeadingTitleLine(
        firstLine: String,
        volumeTitle: String,
        chapterTitle: String,
    ): Boolean {
        val cleanVolume = volumeTitle.trim()
        val cleanChapter = chapterTitle.trim()

        if (cleanChapter.isNotEmpty()) {
            if (firstLine.equals(cleanChapter, ignoreCase = true)) return true
            if (cleanVolume.isNotEmpty()) {
                val combinedWithSpace = "$cleanVolume $cleanChapter"
                val combinedCompact = "$cleanVolume$cleanChapter"
                if (firstLine.equals(combinedWithSpace, ignoreCase = true) ||
                    firstLine.equals(combinedCompact, ignoreCase = true)
                ) {
                    return true
                }
            }
            if (firstLine.length <= MAX_FALLBACK_HEADING_LENGTH &&
                firstLine.endsWith(cleanChapter, ignoreCase = true)
            ) {
                return true
            }
        }

        if (cleanVolume.isNotEmpty() &&
            firstLine.length <= MAX_FALLBACK_HEADING_LENGTH &&
            firstLine.startsWith(cleanVolume, ignoreCase = true) &&
            !firstLine.contains('。')
        ) {
            return true
        }

        // When no external chapterTitle metadata was supplied, treat a short non-sentence heading
        // without terminal period/dialogue opener as the chapter title.
        if (cleanChapter.isEmpty() &&
            firstLine.length <= MAX_FALLBACK_HEADING_LENGTH &&
            !firstLine.contains('。') &&
            DIALOGUE_OPENERS.none { firstLine.startsWith(it) }
        ) {
            return true
        }

        return false
    }

    private fun isBlankOrIdeographicWhitespace(text: String): Boolean {
        for (index in text.indices) {
            val ch = text[index]
            if (ch > ' ' && ch != '\u3000' && ch != '\uFEFF') {
                return false
            }
        }
        return true
    }

    internal fun trimAsciiAndIdeographicSpaces(text: String): String {
        var start = 0
        var end = text.length
        while (start < end && isTrimmableSpace(text[start])) {
            start++
        }
        while (end > start && isTrimmableSpace(text[end - 1])) {
            end--
        }
        return if (start == 0 && end == text.length) text else text.substring(start, end)
    }

    private fun isTrimmableSpace(ch: Char): Boolean {
        return ch <= ' ' || ch == '\u3000' || ch == '\uFEFF'
    }
}
