package org.mewx.wenku8.reader.engine

import android.graphics.Typeface
import android.graphics.text.LineBreakConfig
import android.os.Build
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Paragraph-anchored reading position (`10-next-gen-reader-engine-architecture.md` §2 & §4.3).
 *
 * Unlike screen-dependent page numbers or pixel scroll offsets, `(paragraphIndex, charOffset)` is
 * invariant across font-size changes (`8–36 sp`), line/paragraph spacing adjustments, portrait <->
 * landscape rotation, and horizontal <-> vertical Bunkobon mode switches.
 */
data class ReadingAnchor(
    val paragraphIndex: Int = 0,
    val charOffset: Int = 0,
    val progressPermille: Int = 0,
) : Comparable<ReadingAnchor> {
    override fun compareTo(other: ReadingAnchor): Int {
        val blockDiff = paragraphIndex.compareTo(other.paragraphIndex)
        if (blockDiff != 0) return blockDiff
        return charOffset.compareTo(other.charOffset)
    }
}

/**
 * Viewport + typography configuration driving [StaticLayoutPaginator].
 *
 * Preserves 100% of `v1.31` `WenkuReaderSettingV1` parameters plus `v2.0` vertical Bunkobon mode.
 */
data class ReaderLayoutConfig(
    val viewportWidthPx: Int,
    val viewportHeightPx: Int,
    val fontSizePx: Float,
    val lineDistancePx: Float,
    val paragraphDistancePx: Float,
    val pageEdgeDistancePx: Float,
    val headerReservePx: Float = 0f,
    val footerReservePx: Float = 0f,
    val typeface: Typeface? = null,
    val isVerticalBunkobon: Boolean = false,
    /** Optional per-URL learned `width / height` aspect ratios from decoded images. */
    val knownImageAspectRatios: Map<String, Float> = emptyMap(),
    val defaultImageAspectRatio: Float = DEFAULT_BUNKOBON_PLATE_ASPECT_RATIO,
    /** Number of book columns per viewport (`1` = single page, `2` = open-book facing spread). */
    val columnCount: Int = 1,
    /** Center spine gutter (`px`) separating the two facing pages when [columnCount] >= 2. */
    val columnGapPx: Float = 0f,
    /** Optional spine/foldable-hinge center X coordinate (`px`); defaults to viewport midpoint. */
    val spineCenterXPx: Float? = null,
) {
    val contentLeftPx: Float
        get() = pageEdgeDistancePx.coerceAtLeast(0f)

    val contentTopPx: Float
        get() = (pageEdgeDistancePx + headerReservePx).coerceAtLeast(0f)

    val contentWidthPx: Int
        get() = (viewportWidthPx - (pageEdgeDistancePx * 2f).roundToInt()).coerceAtLeast(MIN_CONTENT_DIMENSION_PX)

    val contentHeightPx: Int
        get() = (
            viewportHeightPx -
                (pageEdgeDistancePx * 2f + headerReservePx + footerReservePx).roundToInt()
            ).coerceAtLeast(MIN_CONTENT_DIMENSION_PX)

    val effectiveColumnCount: Int
        get() = if (columnCount >= 2) 2 else 1

    val effectiveColumnGapPx: Float
        get() = if (effectiveColumnCount >= 2) columnGapPx.coerceAtLeast(0f) else 0f

    val effectiveSpineCenterXPx: Float
        get() {
            val defaultCenter = viewportWidthPx * 0.5f
            val raw = spineCenterXPx ?: defaultCenter
            val maxEdge = (viewportWidthPx - contentLeftPx).coerceAtLeast(contentLeftPx)
            return raw.coerceIn(contentLeftPx, maxEdge)
        }

    /** Balanced width (`px`) of a single reading column; equals [contentWidthPx] in 1-column mode. */
    val singleColumnWidthPx: Int
        get() {
            if (effectiveColumnCount <= 1) return contentWidthPx
            val halfGap = effectiveColumnGapPx * 0.5f
            val leftSpan = (effectiveSpineCenterXPx - halfGap) - contentLeftPx
            val rightSpan = (viewportWidthPx - contentLeftPx) - (effectiveSpineCenterXPx + halfGap)
            return min(leftSpan, rightSpan).roundToInt().coerceAtLeast(MIN_CONTENT_DIMENSION_PX)
        }

    /** Left edge (`px`) of the left facing column (or the sole column in 1-column mode). */
    val leftColumnLeftPx: Float
        get() {
            if (effectiveColumnCount <= 1) return contentLeftPx
            val halfGap = effectiveColumnGapPx * 0.5f
            return (effectiveSpineCenterXPx - halfGap - singleColumnWidthPx).coerceAtLeast(contentLeftPx)
        }

    /** Left edge (`px`) of the right facing column (equals [leftColumnLeftPx] in 1-column mode). */
    val rightColumnLeftPx: Float
        get() {
            if (effectiveColumnCount <= 1) return contentLeftPx
            val halfGap = effectiveColumnGapPx * 0.5f
            val maxLeft = (viewportWidthPx - contentLeftPx - singleColumnWidthPx).coerceAtLeast(contentLeftPx)
            return (effectiveSpineCenterXPx + halfGap).coerceAtMost(maxLeft)
        }

    companion object {
        const val MIN_CONTENT_DIMENSION_PX = 64
        const val DEFAULT_BUNKOBON_PLATE_ASPECT_RATIO = 0.71f
    }
}

/**
 * Renderable element positioned on a single [ReaderPage].
 */
sealed interface ReaderPageElement {
    val blockIndex: Int
    val rawStartChar: Int
    val rawEndChar: Int

    data class TitleLine(
        override val blockIndex: Int,
        override val rawStartChar: Int,
        override val rawEndChar: Int,
        val text: String,
        val xPx: Float,
        val topYPx: Float,
        val baselineYPx: Float,
        val heightPx: Float,
        val fontSizePx: Float,
    ) : ReaderPageElement

    data class TextLine(
        override val blockIndex: Int,
        override val rawStartChar: Int,
        override val rawEndChar: Int,
        val text: String,
        val xPx: Float,
        val topYPx: Float,
        val baselineYPx: Float,
        val heightPx: Float,
        val fontSizePx: Float,
        /** Extra inter-character spacing (`px`) applied to justify wrapped CJK lines cleanly. */
        val extraLetterSpacingPx: Float = 0f,
        val isParagraphFirstLine: Boolean = false,
        val isParagraphLastLine: Boolean = false,
    ) : ReaderPageElement

    /**
     * Single top-to-bottom column inside `VerticalBunkobonDelegate` (`日式文库竖排`), ordered
     * right-to-left across the page.
     */
    data class VerticalColumn(
        override val blockIndex: Int,
        override val rawStartChar: Int,
        override val rawEndChar: Int,
        /** Vertical presentation-mapped characters (`「」→﹁﹂`, `…→⋮`, `—→︱`). */
        val verticalText: String,
        val columnIndexRtl: Int,
        val centerXPx: Float,
        val startYPx: Float,
        val cellAdvanceYPx: Float,
        val fontSizePx: Float,
        val isTitleColumn: Boolean = false,
    ) : ReaderPageElement

    data class ImageSlot(
        override val blockIndex: Int,
        val imageIndexInChapter: Int,
        val imageUrl: String,
        val leftPx: Float,
        val topPx: Float,
        val widthPx: Float,
        val heightPx: Float,
        val aspectRatio: Float,
        val isFullBleedArtbookSlot: Boolean,
    ) : ReaderPageElement {
        override val rawStartChar: Int = 0
        override val rawEndChar: Int = 0
    }
}

/**
 * Single pre-computed page (or open-book facing spread when [columnCount] == 2) inside a
 * [PaginatedChapterLayout].
 */
data class ReaderPage(
    val cid: Int,
    val pageIndexInChapter: Int,
    val totalPagesInChapter: Int,
    val startAnchor: ReadingAnchor,
    val endAnchor: ReadingAnchor,
    val characterCountOnPage: Int,
    val isFullBleedArtbookPage: Boolean,
    val elements: List<ReaderPageElement>,
    val columnCount: Int = 1,
    val spineCenterXPx: Float = 0f,
    val columnGapPx: Float = 0f,
    val logicalStartPageIndex: Int = pageIndexInChapter,
    val logicalEndPageIndex: Int = pageIndexInChapter,
    val totalLogicalPagesInChapter: Int = totalPagesInChapter,
    val hasSecondaryColumnContent: Boolean = false,
) {
    val isDualColumnSpread: Boolean
        get() = columnCount >= 2

    fun toBookmarkOrProgressAnchor(): ReadingAnchor {
        val permille = if (totalPagesInChapter <= 1) {
            1000
        } else {
            ((pageIndexInChapter.toLong() * 1000L) / (totalPagesInChapter - 1).toLong())
                .toInt()
                .coerceIn(0, 1000)
        }
        return ReadingAnchor(
            paragraphIndex = startAnchor.paragraphIndex,
            charOffset = startAnchor.charOffset,
            progressPermille = permille,
        )
    }
}

/**
 * Immutable paginated output for a single [ChapterAst] + [ReaderLayoutConfig].
 */
data class PaginatedChapterLayout(
    val ast: ChapterAst,
    val config: ReaderLayoutConfig,
    val pages: List<ReaderPage>,
) {
    /**
     * Resolves a reflow-invariant [ReadingAnchor] back to its exact `0`-based page index in [pages].
     *
     * Handles:
     * 1. Legacy `v0` scroll-save records (`paragraphIndex == 0 && charOffset == 0 && progressPermille > 0`).
     * 2. Exact `(paragraphIndex, charOffset)` containment inside `[page.startAnchor .. page.endAnchor]`.
     * 3. Graceful nearest-preceding-page fallback when a paragraph index was clamped after edits.
     */
    fun resolvePageIndexForAnchor(anchor: ReadingAnchor): Int {
        if (pages.isEmpty()) return 0

        if (anchor.paragraphIndex == 0 && anchor.charOffset == 0 && anchor.progressPermille > 0) {
            return (((pages.size - 1).toLong() * anchor.progressPermille.coerceIn(0, 1000).toLong()) / 1000L)
                .toInt()
                .coerceIn(0, pages.lastIndex)
        }

        val target = ReadingAnchor(
            paragraphIndex = anchor.paragraphIndex.coerceAtLeast(0),
            charOffset = anchor.charOffset.coerceAtLeast(0),
        )

        for (page in pages) {
            if (target >= page.startAnchor && target <= page.endAnchor) {
                return page.pageIndexInChapter
            }
        }

        var fallbackIndex = 0
        for (page in pages) {
            if (page.startAnchor <= target) {
                fallbackIndex = page.pageIndexInChapter
            } else {
                break
            }
        }
        return fallbackIndex
    }
}

/**
 * CJK horizontal -> vertical Bunkobon presentation glyph mapper (`日式文库竖排`).
 */
object VerticalCjkGlyphMapper {
    fun mapChar(ch: Char): Char = when (ch) {
        '「' -> '﹁'
        '」' -> '﹂'
        '『' -> '﹃'
        '』' -> '﹄'
        '（', '(' -> '︵'
        '）', ')' -> '︶'
        '【', '[' -> '︻'
        '】', ']' -> '︼'
        '《' -> '︽'
        '》' -> '︾'
        '〈' -> '︿'
        '〉' -> '﹀'
        '〔' -> '︹'
        '〕' -> '︺'
        '｛', '{' -> '︷'
        '｝', '}' -> '︸'
        '…' -> '⋮'
        '‥' -> '︰'
        '—', '―' -> '︱'
        '–', '-' -> '︲'
        '～', '〜' -> '︴'
        else -> ch
    }

    fun mapString(text: String): String {
        if (text.isEmpty()) return text
        val builder = StringBuilder(text.length)
        for (index in text.indices) {
            builder.append(mapChar(text[index]))
        }
        return builder.toString()
    }
}

/**
 * Pluggable text width / initial line-break backend.
 *
 * Production uses Android's hardware/C++ Minikin [StaticLayout] + [TextPaint] with automatic
 * fallback when running inside headless JVM/Robolectric tests where FreeType font metrics return `0f`.
 */
fun interface ParagraphLineBreaker {
    fun measureAndBreak(
        text: String,
        fontSizePx: Float,
        maxWidthPx: Int,
        typeface: Typeface?,
    ): MeasuredParagraphLines
}

data class MeasuredParagraphLines(
    val initialLineRanges: List<IntRange>,
    val charWidthLookup: (startInclusive: Int, endExclusive: Int) -> Float,
)

/**
 * Layer 2 paginator (`StaticLayoutPaginator` + `VerticalBunkobonPaginator`) for `Wenku8ReaderEngineV2`.
 *
 * Key typography guarantees (`10-next-gen-reader-engine-architecture.md` §4.2):
 * 1. **Native C++ Minikin `StaticLayout.Builder`** on Android (`LINE_BREAK_STYLE_STRICT` on API 33+)
 *    paired with a deterministic **O(N) Kinsoku Shori (`避头尾`) verifier** across all API levels
 *    (API 24–37) so closing punctuation (`」』），。！？、；：》】`) never begins a line and opening
 *    brackets (`「『（《【`) never hang alone at line end.
 * 2. **Full-Width CJK Justification**: Wrapped lines distribute trailing Kinsoku slack evenly across
 *    `extraLetterSpacingPx` (clamped to `0.28em`) for crisp Bunkobon right margins.
 * 3. **Smart Inline vs Artbook Illustration Placement**: Places `<!--image-->` illustrations inline
 *    below preceding prose when `remainingHeight >= minInlineImageHeight`; otherwise flushes prose
 *    and allocates a full-bleed Artbook Page so artwork is never squished.
 * 4. **Vertical Bunkobon Mode (`日式文库竖排`)**: Right-to-left columns, top-to-bottom glyph cells,
 *    and CJK vertical presentation substitutions (`「」→﹁﹂`, `…→⋮`, `—→︱`).
 */
class StaticLayoutPaginator(
    private val lineBreaker: ParagraphLineBreaker = AndroidStaticLayoutLineBreaker,
) {

    fun paginate(
        ast: ChapterAst,
        config: ReaderLayoutConfig,
    ): PaginatedChapterLayout {
        val effectiveColumnCount = config.effectiveColumnCount
        val spineCenterX = config.effectiveSpineCenterXPx
        val columnGapPx = config.effectiveColumnGapPx

        if (ast.blocks.isEmpty()) {
            val emptyAnchor = ReadingAnchor(paragraphIndex = 0, charOffset = 0, progressPermille = 1000)
            val placeholderPage = ReaderPage(
                cid = ast.cid,
                pageIndexInChapter = 0,
                totalPagesInChapter = 1,
                startAnchor = emptyAnchor,
                endAnchor = emptyAnchor,
                characterCountOnPage = 0,
                isFullBleedArtbookPage = false,
                elements = emptyList(),
                columnCount = effectiveColumnCount,
                spineCenterXPx = spineCenterX,
                columnGapPx = columnGapPx,
                logicalStartPageIndex = 0,
                logicalEndPageIndex = 0,
                totalLogicalPagesInChapter = 1,
                hasSecondaryColumnContent = false,
            )
            return PaginatedChapterLayout(
                ast = ast,
                config = config,
                pages = listOf(placeholderPage),
            )
        }

        val rawPages = if (config.isVerticalBunkobon) {
            paginateVerticalBunkobon(ast, config)
        } else {
            paginateHorizontalBunkobon(ast, config)
        }

        val totalPages = rawPages.size.coerceAtLeast(1)
        val logicalRanges = ArrayList<Pair<Int, Int>>(rawPages.size)
        var logicalCursor = 0
        for (draft in rawPages) {
            val occupiesTwoLogicalPages = effectiveColumnCount >= 2 &&
                !draft.isFullBleedArtbookPage &&
                draft.hasSecondaryColumnContent
            val startLogical = logicalCursor
            val endLogical = if (occupiesTwoLogicalPages) logicalCursor + 1 else logicalCursor
            logicalRanges.add(startLogical to endLogical)
            logicalCursor = endLogical + 1
        }
        val totalLogicalPages = logicalCursor.coerceAtLeast(1)

        val finalizedPages = rawPages.mapIndexed { pageIndex, draft ->
            val (logicalStart, logicalEnd) = logicalRanges[pageIndex]
            ReaderPage(
                cid = ast.cid,
                pageIndexInChapter = pageIndex,
                totalPagesInChapter = totalPages,
                startAnchor = draft.startAnchor,
                endAnchor = draft.endAnchor,
                characterCountOnPage = draft.characterCount,
                isFullBleedArtbookPage = draft.isFullBleedArtbookPage,
                elements = draft.elements,
                columnCount = effectiveColumnCount,
                spineCenterXPx = spineCenterX,
                columnGapPx = columnGapPx,
                logicalStartPageIndex = logicalStart,
                logicalEndPageIndex = logicalEnd,
                totalLogicalPagesInChapter = totalLogicalPages,
                hasSecondaryColumnContent = draft.hasSecondaryColumnContent,
            )
        }

        return PaginatedChapterLayout(
            ast = ast,
            config = config,
            pages = finalizedPages,
        )
    }

    private fun paginateHorizontalBunkobon(
        ast: ChapterAst,
        config: ReaderLayoutConfig,
    ): List<DraftPage> {
        val pages = ArrayList<DraftPage>()
        val currentElements = ArrayList<ReaderPageElement>()
        var currentCharCount = 0

        val columnCount = config.effectiveColumnCount
        val contentLeft = config.contentLeftPx
        val contentTop = config.contentTopPx
        val fullContentWidth = config.contentWidthPx
        val columnWidth = config.singleColumnWidthPx
        val contentHeight = config.contentHeightPx.toFloat()
        val contentBottomLimit = contentTop + contentHeight

        fun columnLeftPx(colIdx: Int): Float =
            if (columnCount <= 1 || colIdx == 0) config.leftColumnLeftPx else config.rightColumnLeftPx

        val bodyFontSize = config.fontSizePx.coerceAtLeast(8f)
        val bodyLineHeight = bodyFontSize * BODY_GLYPH_HEIGHT_FACTOR
        val titleFontSize = (bodyFontSize * TITLE_FONT_SCALE).coerceAtMost(bodyFontSize + 14f)
        val titleLineHeight = titleFontSize * TITLE_GLYPH_HEIGHT_FACTOR
        val lineGap = config.lineDistancePx.coerceAtLeast(0f)
        val paragraphGap = config.paragraphDistancePx.coerceAtLeast(0f)

        val minInlineImageHeight = max(
            bodyFontSize * MIN_INLINE_IMAGE_EM_MULTIPLIER,
            contentHeight * MIN_INLINE_IMAGE_VIEWPORT_FRACTION,
        ).coerceAtMost(contentHeight)

        var currentColumnIndex = 0
        var currentColumnElementStartIndex = 0
        var hasSecondaryColumn = false
        var cursorY = contentTop

        fun currentColumnHasElements(): Boolean = currentElements.size > currentColumnElementStartIndex

        fun flushCurrentPage(isFullBleedArtbook: Boolean = false) {
            if (currentElements.isEmpty()) return
            val firstEl = currentElements.first()
            val lastEl = currentElements.last()
            pages.add(
                DraftPage(
                    startAnchor = ReadingAnchor(
                        paragraphIndex = firstEl.blockIndex,
                        charOffset = firstEl.rawStartChar,
                    ),
                    endAnchor = ReadingAnchor(
                        paragraphIndex = lastEl.blockIndex,
                        charOffset = lastEl.rawEndChar,
                    ),
                    characterCount = currentCharCount,
                    isFullBleedArtbookPage = isFullBleedArtbook,
                    hasSecondaryColumnContent = hasSecondaryColumn && !isFullBleedArtbook,
                    elements = ArrayList(currentElements),
                ),
            )
            currentElements.clear()
            currentCharCount = 0
            currentColumnIndex = 0
            currentColumnElementStartIndex = 0
            hasSecondaryColumn = false
            cursorY = contentTop
        }

        fun advanceColumnOrFlush() {
            if (columnCount >= 2 && currentColumnIndex == 0) {
                currentColumnIndex = 1
                currentColumnElementStartIndex = currentElements.size
                cursorY = contentTop
            } else {
                flushCurrentPage()
            }
        }

        fun appendToCurrentColumn(element: ReaderPageElement) {
            currentElements.add(element)
            if (currentColumnIndex > 0) {
                hasSecondaryColumn = true
            }
        }

        for (block in ast.blocks) {
            when (block) {
                is ChapterBlock.TitleBlock -> {
                    val titleLines = wrapAndApplyKinsoku(
                        text = block.title,
                        fontSizePx = titleFontSize,
                        maxWidthPx = columnWidth,
                        typeface = config.typeface,
                    )
                    for ((lineIdx, slice) in titleLines.lines.withIndex()) {
                        if (currentColumnHasElements() && cursorY + titleLineHeight > contentBottomLimit) {
                            advanceColumnOrFlush()
                        }
                        val lineText = block.title.substring(slice.startInclusive, slice.endExclusive)
                        val rawStart = slice.startInclusive.coerceIn(0, (block.title.length - 1).coerceAtLeast(0))
                        val rawEnd = (slice.endExclusive - 1).coerceIn(rawStart, (block.title.length - 1).coerceAtLeast(0))
                        appendToCurrentColumn(
                            ReaderPageElement.TitleLine(
                                blockIndex = block.blockIndex,
                                rawStartChar = rawStart,
                                rawEndChar = rawEnd,
                                text = lineText,
                                xPx = columnLeftPx(currentColumnIndex),
                                topYPx = cursorY,
                                baselineYPx = cursorY + titleFontSize * BASELINE_ASCENT_RATIO,
                                heightPx = titleLineHeight,
                                fontSizePx = titleFontSize,
                            ),
                        )
                        currentCharCount += lineText.length
                        cursorY += if (lineIdx < titleLines.lines.lastIndex) {
                            titleLineHeight + lineGap
                        } else {
                            titleLineHeight + max(paragraphGap, bodyFontSize * 0.8f)
                        }
                    }
                }

                is ChapterBlock.ParagraphBlock -> {
                    val formatted = block.formattedText
                    val wrapped = wrapAndApplyKinsoku(
                        text = formatted,
                        fontSizePx = bodyFontSize,
                        maxWidthPx = columnWidth,
                        typeface = config.typeface,
                    )
                    val lastLineIndex = wrapped.lines.lastIndex

                    for ((lineIdx, slice) in wrapped.lines.withIndex()) {
                        if (currentColumnHasElements() && cursorY + bodyLineHeight > contentBottomLimit) {
                            advanceColumnOrFlush()
                        }

                        val lineString = formatted.substring(slice.startInclusive, slice.endExclusive)
                        val rawMaxIndex = (block.text.length - 1).coerceAtLeast(0)
                        val rawStart = (slice.startInclusive - block.indentLength)
                            .coerceIn(0, rawMaxIndex)
                        val rawEnd = (slice.endExclusive - 1 - block.indentLength)
                            .coerceIn(rawStart, rawMaxIndex)
                        val rawCharsInSlice = (
                            (slice.endExclusive - block.indentLength).coerceAtLeast(0) -
                                (slice.startInclusive - block.indentLength).coerceAtLeast(0)
                            ).coerceAtLeast(0)

                        val isLastLine = lineIdx == lastLineIndex
                        val extraLetterSpacing = if (!isLastLine && lineString.length > 2) {
                            val slackPx = columnWidth.toFloat() - slice.measuredWidthPx
                            if (slackPx > 0.5f) {
                                (slackPx / (lineString.length - 1).toFloat())
                                    .coerceIn(0f, bodyFontSize * MAX_JUSTIFICATION_LETTER_SPACING_EM)
                            } else {
                                0f
                            }
                        } else {
                            0f
                        }

                        appendToCurrentColumn(
                            ReaderPageElement.TextLine(
                                blockIndex = block.blockIndex,
                                rawStartChar = rawStart,
                                rawEndChar = rawEnd,
                                text = lineString,
                                xPx = columnLeftPx(currentColumnIndex),
                                topYPx = cursorY,
                                baselineYPx = cursorY + bodyFontSize * BASELINE_ASCENT_RATIO,
                                heightPx = bodyLineHeight,
                                fontSizePx = bodyFontSize,
                                extraLetterSpacingPx = extraLetterSpacing,
                                isParagraphFirstLine = lineIdx == 0,
                                isParagraphLastLine = isLastLine,
                            ),
                        )
                        currentCharCount += rawCharsInSlice
                        cursorY += if (isLastLine) {
                            bodyLineHeight + paragraphGap
                        } else {
                            bodyLineHeight + lineGap
                        }
                    }
                }

                is ChapterBlock.IllustrationBlock -> {
                    val aspectRatio = resolveAspectRatio(block, config)
                    val naturalWidth = columnWidth.toFloat()
                    val naturalHeight = (naturalWidth / aspectRatio).coerceAtLeast(bodyFontSize * 3f)
                    val hasOnlyTitleOnCurrentPage = currentElements.isNotEmpty() &&
                        currentElements.all { it is ReaderPageElement.TitleLine }
                    val hasOnlyTitleInCurrentColumn = currentColumnHasElements() &&
                        (currentColumnElementStartIndex until currentElements.size).all { idx ->
                            currentElements[idx] is ReaderPageElement.TitleLine
                        }

                    if (ast.isDedicatedIllustrationChapter) {
                        // Dedicated `<chapter>插图</chapter>` gallery: never flush a Page 0 that
                        // contains only the chapter heading ("插图" / "第一卷 插图")! Keep the
                        // heading on Page 0 and fit the first plate cleanly below it, while giving
                        // every subsequent plate its own full-bleed Artbook Page.
                        if (hasOnlyTitleOnCurrentPage) {
                            val bottomLimit = (config.viewportHeightPx.toFloat() -
                                config.footerReservePx.coerceAtLeast(0f)).coerceAtLeast(cursorY + bodyFontSize * 3f)
                            val availableHeightBelowTitle = (bottomLimit - cursorY)
                                .coerceAtLeast(bodyFontSize * 3f)
                            val fitted = fitBoxInside(
                                aspectRatio = aspectRatio,
                                maxWidth = config.viewportWidthPx.toFloat(),
                                maxHeight = availableHeightBelowTitle,
                            )
                            val left = (config.viewportWidthPx - fitted.first) * 0.5f
                            val top = cursorY + (availableHeightBelowTitle - fitted.second) * 0.5f
                            currentElements.add(
                                ReaderPageElement.ImageSlot(
                                    blockIndex = block.blockIndex,
                                    imageIndexInChapter = block.imageIndexInChapter,
                                    imageUrl = block.imageUrl,
                                    leftPx = left,
                                    topPx = top,
                                    widthPx = fitted.first,
                                    heightPx = fitted.second,
                                    aspectRatio = aspectRatio,
                                    isFullBleedArtbookSlot = true,
                                ),
                            )
                            flushCurrentPage(isFullBleedArtbook = true)
                        } else {
                            flushCurrentPage()
                            val fitted = fitBoxInside(
                                aspectRatio = aspectRatio,
                                maxWidth = config.viewportWidthPx.toFloat(),
                                maxHeight = config.viewportHeightPx.toFloat(),
                            )
                            val left = (config.viewportWidthPx - fitted.first) * 0.5f
                            val top = (config.viewportHeightPx - fitted.second) * 0.5f
                            currentElements.add(
                                ReaderPageElement.ImageSlot(
                                    blockIndex = block.blockIndex,
                                    imageIndexInChapter = block.imageIndexInChapter,
                                    imageUrl = block.imageUrl,
                                    leftPx = left,
                                    topPx = top,
                                    widthPx = fitted.first,
                                    heightPx = fitted.second,
                                    aspectRatio = aspectRatio,
                                    isFullBleedArtbookSlot = true,
                                ),
                            )
                            flushCurrentPage(isFullBleedArtbook = true)
                        }
                    } else if (columnCount >= 2 && aspectRatio >= WIDE_SPREAD_IMAGE_ASPECT_RATIO) {
                        // Wide / double-page spread artwork spans the entire dual-column spread.
                        if (hasOnlyTitleOnCurrentPage && (contentBottomLimit - cursorY) >= bodyFontSize * 3f) {
                            val availableHeight = (contentBottomLimit - cursorY).coerceAtLeast(bodyFontSize * 3f)
                            val fitted = fitBoxInside(
                                aspectRatio = aspectRatio,
                                maxWidth = fullContentWidth.toFloat(),
                                maxHeight = availableHeight,
                            )
                            currentElements.add(
                                ReaderPageElement.ImageSlot(
                                    blockIndex = block.blockIndex,
                                    imageIndexInChapter = block.imageIndexInChapter,
                                    imageUrl = block.imageUrl,
                                    leftPx = contentLeft + (fullContentWidth - fitted.first) * 0.5f,
                                    topPx = cursorY + (availableHeight - fitted.second) * 0.5f,
                                    widthPx = fitted.first,
                                    heightPx = fitted.second,
                                    aspectRatio = aspectRatio,
                                    isFullBleedArtbookSlot = true,
                                ),
                            )
                            flushCurrentPage(isFullBleedArtbook = true)
                        } else {
                            flushCurrentPage()
                            val fitted = fitBoxInside(
                                aspectRatio = aspectRatio,
                                maxWidth = fullContentWidth.toFloat(),
                                maxHeight = contentHeight,
                            )
                            currentElements.add(
                                ReaderPageElement.ImageSlot(
                                    blockIndex = block.blockIndex,
                                    imageIndexInChapter = block.imageIndexInChapter,
                                    imageUrl = block.imageUrl,
                                    leftPx = contentLeft + (fullContentWidth - fitted.first) * 0.5f,
                                    topPx = contentTop + (contentHeight - fitted.second) * 0.5f,
                                    widthPx = fitted.first,
                                    heightPx = fitted.second,
                                    aspectRatio = aspectRatio,
                                    isFullBleedArtbookSlot = true,
                                ),
                            )
                            flushCurrentPage(isFullBleedArtbook = true)
                        }
                    } else {
                        val remainingHeight = contentBottomLimit - cursorY
                        val canPlaceBelowExisting = currentColumnHasElements() && (
                            remainingHeight >= minInlineImageHeight ||
                                (hasOnlyTitleInCurrentColumn && remainingHeight >= bodyFontSize * 3f)
                            )
                        if (canPlaceBelowExisting) {
                            // Fits comfortably inline below the preceding paragraph or chapter title!
                            val maxInlineHeight = min(remainingHeight, naturalHeight)
                            val fitted = fitBoxInside(
                                aspectRatio = aspectRatio,
                                maxWidth = columnWidth.toFloat(),
                                maxHeight = maxInlineHeight,
                            )
                            val colLeft = columnLeftPx(currentColumnIndex)
                            val centeredLeft = colLeft + (columnWidth - fitted.first) * 0.5f
                            appendToCurrentColumn(
                                ReaderPageElement.ImageSlot(
                                    blockIndex = block.blockIndex,
                                    imageIndexInChapter = block.imageIndexInChapter,
                                    imageUrl = block.imageUrl,
                                    leftPx = centeredLeft,
                                    topPx = cursorY,
                                    widthPx = fitted.first,
                                    heightPx = fitted.second,
                                    aspectRatio = aspectRatio,
                                    isFullBleedArtbookSlot = false,
                                ),
                            )
                            cursorY += fitted.second + paragraphGap
                        } else if (columnCount >= 2) {
                            // Dual-column facing-page Bunkobon placement for portrait artwork:
                            // if current column already has prose, advance to the facing column
                            // (or start a fresh spread's left column) so text & artwork face each other!
                            if (currentColumnHasElements()) {
                                advanceColumnOrFlush()
                            }
                            val fitted = fitBoxInside(
                                aspectRatio = aspectRatio,
                                maxWidth = columnWidth.toFloat(),
                                maxHeight = contentHeight,
                            )
                            val colLeft = columnLeftPx(currentColumnIndex)
                            val centeredLeft = colLeft + (columnWidth - fitted.first) * 0.5f
                            val centeredTop = contentTop + (contentHeight - fitted.second) * 0.5f
                            appendToCurrentColumn(
                                ReaderPageElement.ImageSlot(
                                    blockIndex = block.blockIndex,
                                    imageIndexInChapter = block.imageIndexInChapter,
                                    imageUrl = block.imageUrl,
                                    leftPx = centeredLeft,
                                    topPx = centeredTop,
                                    widthPx = fitted.first,
                                    heightPx = fitted.second,
                                    aspectRatio = aspectRatio,
                                    isFullBleedArtbookSlot = false,
                                ),
                            )
                            advanceColumnOrFlush()
                        } else {
                            // Single-column: remaining space is too short (or page is empty): flush
                            // preceding prose and give the illustration a dedicated Artbook Page.
                            flushCurrentPage()
                            val fitted = fitBoxInside(
                                aspectRatio = aspectRatio,
                                maxWidth = fullContentWidth.toFloat(),
                                maxHeight = contentHeight,
                            )
                            val centeredLeft = contentLeft + (fullContentWidth - fitted.first) * 0.5f
                            val centeredTop = contentTop + (contentHeight - fitted.second) * 0.5f
                            currentElements.add(
                                ReaderPageElement.ImageSlot(
                                    blockIndex = block.blockIndex,
                                    imageIndexInChapter = block.imageIndexInChapter,
                                    imageUrl = block.imageUrl,
                                    leftPx = centeredLeft,
                                    topPx = centeredTop,
                                    widthPx = fitted.first,
                                    heightPx = fitted.second,
                                    aspectRatio = aspectRatio,
                                    isFullBleedArtbookSlot = true,
                                ),
                            )
                            flushCurrentPage(isFullBleedArtbook = true)
                        }
                    }
                }
            }
        }

        flushCurrentPage()
        return pages
    }

    private fun paginateVerticalBunkobon(
        ast: ChapterAst,
        config: ReaderLayoutConfig,
    ): List<DraftPage> {
        val pages = ArrayList<DraftPage>()
        val currentColumns = ArrayList<ReaderPageElement>()
        var currentCharCount = 0

        val spreadColumnCount = config.effectiveColumnCount
        val contentLeft = config.contentLeftPx
        val contentTop = config.contentTopPx
        val fullContentWidth = config.contentWidthPx.toFloat()
        val halfWidth = config.singleColumnWidthPx.toFloat()
        val contentHeight = config.contentHeightPx.toFloat()

        // Traditional Japanese Bunkobon spread reads Right Page (half 0) -> Left Page (half 1).
        fun halfLeftPx(halfIdx: Int): Float = when {
            spreadColumnCount <= 1 -> config.leftColumnLeftPx
            halfIdx == 0 -> config.rightColumnLeftPx
            else -> config.leftColumnLeftPx
        }

        fun halfRightEdgePx(halfIdx: Int): Float = halfLeftPx(halfIdx) + halfWidth

        val bodyFontSize = config.fontSizePx.coerceAtLeast(8f)
        val cellAdvanceY = bodyFontSize * VERTICAL_CELL_ADVANCE_FACTOR
        val maxCellsPerColumn = floor(contentHeight / cellAdvanceY).toInt().coerceAtLeast(4)

        val columnWidth = bodyFontSize * VERTICAL_COLUMN_WIDTH_FACTOR
        val columnStepX = columnWidth + config.lineDistancePx.coerceAtLeast(4f)
        val maxColumnsPerHalf = max(
            1,
            floor((halfWidth + config.lineDistancePx.coerceAtLeast(4f)) / columnStepX).toInt(),
        )

        var currentHalfIndex = 0
        var columnIndexInHalf = 0
        var totalColumnIndexInSpread = 0
        var hasSecondaryHalf = false

        fun flushVerticalPage(isFullBleedArtbook: Boolean = false) {
            if (currentColumns.isEmpty()) return
            val firstEl = currentColumns.first()
            val lastEl = currentColumns.last()
            pages.add(
                DraftPage(
                    startAnchor = ReadingAnchor(firstEl.blockIndex, firstEl.rawStartChar),
                    endAnchor = ReadingAnchor(lastEl.blockIndex, lastEl.rawEndChar),
                    characterCount = currentCharCount,
                    isFullBleedArtbookPage = isFullBleedArtbook,
                    hasSecondaryColumnContent = hasSecondaryHalf && !isFullBleedArtbook,
                    elements = ArrayList(currentColumns),
                ),
            )
            currentColumns.clear()
            currentCharCount = 0
            currentHalfIndex = 0
            columnIndexInHalf = 0
            totalColumnIndexInSpread = 0
            hasSecondaryHalf = false
        }

        fun advanceHalfOrFlush() {
            if (spreadColumnCount >= 2 && currentHalfIndex == 0) {
                currentHalfIndex = 1
                columnIndexInHalf = 0
            } else {
                flushVerticalPage()
            }
        }

        fun appendToCurrentHalf(element: ReaderPageElement) {
            currentColumns.add(element)
            if (currentHalfIndex > 0) {
                hasSecondaryHalf = true
            }
        }

        for (block in ast.blocks) {
            when (block) {
                is ChapterBlock.TitleBlock -> {
                    val mappedTitle = VerticalCjkGlyphMapper.mapString(block.title)
                    val colSlices = sliceVerticalColumnsWithKinsoku(mappedTitle, maxCellsPerColumn)
                    for (range in colSlices) {
                        if (columnIndexInHalf >= maxColumnsPerHalf) {
                            advanceHalfOrFlush()
                        }
                        val colText = mappedTitle.substring(range.first, range.last + 1)
                        val centerX = halfRightEdgePx(currentHalfIndex) -
                            (columnIndexInHalf * columnStepX) - columnWidth * 0.5f
                        appendToCurrentHalf(
                            ReaderPageElement.VerticalColumn(
                                blockIndex = block.blockIndex,
                                rawStartChar = range.first.coerceIn(0, (block.title.length - 1).coerceAtLeast(0)),
                                rawEndChar = range.last.coerceIn(0, (block.title.length - 1).coerceAtLeast(0)),
                                verticalText = colText,
                                columnIndexRtl = totalColumnIndexInSpread,
                                centerXPx = centerX,
                                startYPx = contentTop + bodyFontSize,
                                cellAdvanceYPx = cellAdvanceY,
                                fontSizePx = bodyFontSize * 1.12f,
                                isTitleColumn = true,
                            ),
                        )
                        currentCharCount += colText.length
                        // Leave one blank breathing column after the chapter title when space allows.
                        columnIndexInHalf += 2
                        totalColumnIndexInSpread += 2
                    }
                }

                is ChapterBlock.ParagraphBlock -> {
                    val mappedFormatted = VerticalCjkGlyphMapper.mapString(block.formattedText)
                    val colSlices = sliceVerticalColumnsWithKinsoku(mappedFormatted, maxCellsPerColumn)
                    val rawMaxIndex = (block.text.length - 1).coerceAtLeast(0)
                    for (range in colSlices) {
                        if (columnIndexInHalf >= maxColumnsPerHalf) {
                            advanceHalfOrFlush()
                        }
                        val startInFormatted = range.first
                        val endExclusiveInFormatted = range.last + 1
                        val colText = mappedFormatted.substring(startInFormatted, endExclusiveInFormatted)
                        val rawStart = (startInFormatted - block.indentLength).coerceIn(0, rawMaxIndex)
                        val rawEnd = (endExclusiveInFormatted - 1 - block.indentLength).coerceIn(rawStart, rawMaxIndex)
                        val rawChars = (
                            (endExclusiveInFormatted - block.indentLength).coerceAtLeast(0) -
                                (startInFormatted - block.indentLength).coerceAtLeast(0)
                            ).coerceAtLeast(0)

                        val centerX = halfRightEdgePx(currentHalfIndex) -
                            (columnIndexInHalf * columnStepX) - columnWidth * 0.5f
                        appendToCurrentHalf(
                            ReaderPageElement.VerticalColumn(
                                blockIndex = block.blockIndex,
                                rawStartChar = rawStart,
                                rawEndChar = rawEnd,
                                verticalText = colText,
                                columnIndexRtl = totalColumnIndexInSpread,
                                centerXPx = centerX,
                                startYPx = contentTop + bodyFontSize,
                                cellAdvanceYPx = cellAdvanceY,
                                fontSizePx = bodyFontSize,
                                isTitleColumn = false,
                            ),
                        )
                        currentCharCount += rawChars
                        columnIndexInHalf++
                        totalColumnIndexInSpread++
                    }
                }

                is ChapterBlock.IllustrationBlock -> {
                    val aspectRatio = resolveAspectRatio(block, config)
                    val hasOnlyTitleColumns = currentColumns.isNotEmpty() &&
                        currentColumns.all {
                            it is ReaderPageElement.VerticalColumn && it.isTitleColumn
                        }

                    if (spreadColumnCount >= 2 &&
                        !ast.isDedicatedIllustrationChapter &&
                        aspectRatio < WIDE_SPREAD_IMAGE_ASPECT_RATIO
                    ) {
                        // Portrait artwork occupies a facing page in the vertical Bunkobon spread.
                        if (columnIndexInHalf > 0) {
                            advanceHalfOrFlush()
                        }
                        val fitted = fitBoxInside(
                            aspectRatio = aspectRatio,
                            maxWidth = halfWidth,
                            maxHeight = contentHeight,
                        )
                        val curHalfLeft = halfLeftPx(currentHalfIndex)
                        appendToCurrentHalf(
                            ReaderPageElement.ImageSlot(
                                blockIndex = block.blockIndex,
                                imageIndexInChapter = block.imageIndexInChapter,
                                imageUrl = block.imageUrl,
                                leftPx = curHalfLeft + (halfWidth - fitted.first) * 0.5f,
                                topPx = contentTop + (contentHeight - fitted.second) * 0.5f,
                                widthPx = fitted.first,
                                heightPx = fitted.second,
                                aspectRatio = aspectRatio,
                                isFullBleedArtbookSlot = false,
                            ),
                        )
                        advanceHalfOrFlush()
                    } else {
                        val remainingWidthLeftOfTitle =
                            (halfRightEdgePx(currentHalfIndex) - columnIndexInHalf * columnStepX) - contentLeft

                        if (spreadColumnCount == 1 &&
                            hasOnlyTitleColumns &&
                            remainingWidthLeftOfTitle >= fullContentWidth * 0.45f
                        ) {
                            val fitted = fitBoxInside(
                                aspectRatio = aspectRatio,
                                maxWidth = remainingWidthLeftOfTitle,
                                maxHeight = contentHeight,
                            )
                            currentColumns.add(
                                ReaderPageElement.ImageSlot(
                                    blockIndex = block.blockIndex,
                                    imageIndexInChapter = block.imageIndexInChapter,
                                    imageUrl = block.imageUrl,
                                    leftPx = contentLeft + (remainingWidthLeftOfTitle - fitted.first) * 0.5f,
                                    topPx = contentTop + (contentHeight - fitted.second) * 0.5f,
                                    widthPx = fitted.first,
                                    heightPx = fitted.second,
                                    aspectRatio = aspectRatio,
                                    isFullBleedArtbookSlot = true,
                                ),
                            )
                            flushVerticalPage(isFullBleedArtbook = ast.isDedicatedIllustrationChapter)
                        } else if (spreadColumnCount >= 2 && hasOnlyTitleColumns) {
                            // Dedicated gallery Page 0 in dual-column vertical mode: place plate on facing Left half.
                            currentHalfIndex = 1
                            columnIndexInHalf = 0
                            val fitted = fitBoxInside(
                                aspectRatio = aspectRatio,
                                maxWidth = halfWidth,
                                maxHeight = contentHeight,
                            )
                            val curHalfLeft = halfLeftPx(1)
                            appendToCurrentHalf(
                                ReaderPageElement.ImageSlot(
                                    blockIndex = block.blockIndex,
                                    imageIndexInChapter = block.imageIndexInChapter,
                                    imageUrl = block.imageUrl,
                                    leftPx = curHalfLeft + (halfWidth - fitted.first) * 0.5f,
                                    topPx = contentTop + (contentHeight - fitted.second) * 0.5f,
                                    widthPx = fitted.first,
                                    heightPx = fitted.second,
                                    aspectRatio = aspectRatio,
                                    isFullBleedArtbookSlot = true,
                                ),
                            )
                            flushVerticalPage(isFullBleedArtbook = ast.isDedicatedIllustrationChapter)
                        } else {
                            flushVerticalPage()
                            val fitted = fitBoxInside(
                                aspectRatio = aspectRatio,
                                maxWidth = fullContentWidth,
                                maxHeight = contentHeight,
                            )
                            currentColumns.add(
                                ReaderPageElement.ImageSlot(
                                    blockIndex = block.blockIndex,
                                    imageIndexInChapter = block.imageIndexInChapter,
                                    imageUrl = block.imageUrl,
                                    leftPx = contentLeft + (fullContentWidth - fitted.first) * 0.5f,
                                    topPx = contentTop + (contentHeight - fitted.second) * 0.5f,
                                    widthPx = fitted.first,
                                    heightPx = fitted.second,
                                    aspectRatio = aspectRatio,
                                    isFullBleedArtbookSlot = true,
                                ),
                            )
                            flushVerticalPage(isFullBleedArtbook = true)
                        }
                    }
                }
            }
        }

        flushVerticalPage()
        return pages
    }

    /**
     * Runs initial line breaking via [lineBreaker] (`StaticLayout.Builder` on Android) and enforces
     * deterministic CJK Kinsoku Shori (`避头尾`) across all API levels.
     */
    internal fun wrapAndApplyKinsoku(
        text: String,
        fontSizePx: Float,
        maxWidthPx: Int,
        typeface: Typeface?,
    ): WrappedParagraph {
        if (text.isEmpty()) {
            return WrappedParagraph(emptyList())
        }

        val measured = lineBreaker.measureAndBreak(
            text = text,
            fontSizePx = fontSizePx,
            maxWidthPx = maxWidthPx,
            typeface = typeface,
        )
        if (measured.initialLineRanges.isEmpty()) {
            return WrappedParagraph(
                listOf(
                    WrappedLineSlice(
                        startInclusive = 0,
                        endExclusive = text.length,
                        measuredWidthPx = measured.charWidthLookup(0, text.length),
                    ),
                ),
            )
        }

        // Mutable [startInclusive, endExclusive) pairs adjusted forward in a single O(N) pass.
        val bounds = ArrayList<IntArray>(measured.initialLineRanges.size + 2)
        for (range in measured.initialLineRanges) {
            if (range.first <= range.last) {
                bounds.add(intArrayOf(range.first, range.last + 1))
            }
        }

        val maxWidthF = maxWidthPx.toFloat()
        var lineIndex = 0
        while (lineIndex < bounds.size) {
            // Step 1: If a previous Kinsoku pullback pushed characters onto `bounds[lineIndex]`
            // such that it now exceeds `maxWidthPx`, spill excess trailing characters forward onto
            // `bounds[lineIndex + 1]` (never backward, guaranteeing termination).
            while (bounds[lineIndex][1] - bounds[lineIndex][0] > 1 &&
                measured.charWidthLookup(bounds[lineIndex][0], bounds[lineIndex][1]) > maxWidthF + 0.5f
            ) {
                val spillStart = shrinkEndToFitWidth(
                    startInclusive = bounds[lineIndex][0],
                    currentEndExclusive = bounds[lineIndex][1],
                    maxWidthPx = maxWidthF,
                    widthLookup = measured.charWidthLookup,
                )
                val spilledFrom = spillStart.coerceAtLeast(bounds[lineIndex][0] + 1)
                val oldEnd = bounds[lineIndex][1]
                bounds[lineIndex][1] = spilledFrom
                if (lineIndex + 1 < bounds.size) {
                    bounds[lineIndex + 1][0] = spilledFrom
                } else {
                    bounds.add(intArrayOf(spilledFrom, oldEnd))
                }
                break
            }

            // Step 2: Enforce CJK Kinsoku (`避头尾`) at the boundary between `lineIndex` and `lineIndex + 1`.
            if (lineIndex + 1 < bounds.size) {
                var pullCount = 0
                val minCharsToKeep = max(1, (bounds[lineIndex][1] - bounds[lineIndex][0]) / 3)
                while (pullCount < MAX_KINSOKU_PULLBACK_CHARS &&
                    (bounds[lineIndex][1] - bounds[lineIndex][0]) > minCharsToKeep
                ) {
                    val curEndChar = text[bounds[lineIndex][1] - 1]
                    val nextStartChar = text[bounds[lineIndex + 1][0]]
                    val violatesLineEnd = isForbiddenLineEndChar(curEndChar)
                    val violatesLineStart = isForbiddenLineStartChar(nextStartChar)
                    if (!violatesLineEnd && !violatesLineStart) {
                        break
                    }
                    bounds[lineIndex][1] -= 1
                    bounds[lineIndex + 1][0] -= 1
                    pullCount++
                }
            }

            lineIndex++
        }

        val slices = ArrayList<WrappedLineSlice>(bounds.size)
        for (pair in bounds) {
            if (pair[1] > pair[0]) {
                slices.add(
                    WrappedLineSlice(
                        startInclusive = pair[0],
                        endExclusive = pair[1],
                        measuredWidthPx = measured.charWidthLookup(pair[0], pair[1]),
                    ),
                )
            }
        }
        return WrappedParagraph(slices)
    }

    private fun sliceVerticalColumnsWithKinsoku(
        verticalMappedText: String,
        maxCellsPerColumn: Int,
    ): List<IntRange> {
        if (verticalMappedText.isEmpty()) return emptyList()
        val bounds = ArrayList<IntArray>()
        var cursor = 0
        while (cursor < verticalMappedText.length) {
            val end = min(verticalMappedText.length, cursor + maxCellsPerColumn)
            bounds.add(intArrayOf(cursor, end))
            cursor = end
        }

        var idx = 0
        while (idx < bounds.size) {
            while (bounds[idx][1] - bounds[idx][0] > maxCellsPerColumn) {
                val overflowStart = bounds[idx][0] + maxCellsPerColumn
                val oldEnd = bounds[idx][1]
                bounds[idx][1] = overflowStart
                if (idx + 1 < bounds.size) {
                    bounds[idx + 1][0] = overflowStart
                } else {
                    bounds.add(intArrayOf(overflowStart, oldEnd))
                }
            }

            if (idx + 1 < bounds.size) {
                var pullCount = 0
                val minKeep = max(1, maxCellsPerColumn / 3)
                while (pullCount < MAX_KINSOKU_PULLBACK_CHARS &&
                    (bounds[idx][1] - bounds[idx][0]) > minKeep
                ) {
                    val bottomChar = verticalMappedText[bounds[idx][1] - 1]
                    val nextTopChar = verticalMappedText[bounds[idx + 1][0]]
                    if (!isForbiddenLineEndChar(bottomChar) && !isForbiddenLineStartChar(nextTopChar)) {
                        break
                    }
                    bounds[idx][1] -= 1
                    bounds[idx + 1][0] -= 1
                    pullCount++
                }
            }
            idx++
        }

        return bounds.mapNotNull { pair ->
            if (pair[1] > pair[0]) pair[0] until pair[1] else null
        }
    }

    private fun shrinkEndToFitWidth(
        startInclusive: Int,
        currentEndExclusive: Int,
        maxWidthPx: Float,
        widthLookup: (Int, Int) -> Float,
    ): Int {
        var low = startInclusive + 1
        var high = currentEndExclusive
        var best = low
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (widthLookup(startInclusive, mid) <= maxWidthPx + 0.5f) {
                best = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return best
    }

    private fun resolveAspectRatio(
        block: ChapterBlock.IllustrationBlock,
        config: ReaderLayoutConfig,
    ): Float {
        val candidate = block.cachedAspectRatio
            ?: config.knownImageAspectRatios[block.imageUrl]
            ?: config.defaultImageAspectRatio
        return candidate.takeIf { it.isFinite() && it > 0.05f }
            ?: ReaderLayoutConfig.DEFAULT_BUNKOBON_PLATE_ASPECT_RATIO
    }

    private fun fitBoxInside(
        aspectRatio: Float,
        maxWidth: Float,
        maxHeight: Float,
    ): Pair<Float, Float> {
        val safeMaxW = maxWidth.coerceAtLeast(1f)
        val safeMaxH = maxHeight.coerceAtLeast(1f)
        val widthByHeight = safeMaxH * aspectRatio
        return if (widthByHeight <= safeMaxW) {
            widthByHeight to safeMaxH
        } else {
            safeMaxW to (safeMaxW / aspectRatio)
        }
    }

    internal data class WrappedLineSlice(
        val startInclusive: Int,
        val endExclusive: Int,
        val measuredWidthPx: Float,
    )

    internal data class WrappedParagraph(
        val lines: List<WrappedLineSlice>,
    )

    private data class DraftPage(
        val startAnchor: ReadingAnchor,
        val endAnchor: ReadingAnchor,
        val characterCount: Int,
        val isFullBleedArtbookPage: Boolean,
        val hasSecondaryColumnContent: Boolean = false,
        val elements: List<ReaderPageElement>,
    )

    companion object {
        private const val BODY_GLYPH_HEIGHT_FACTOR = 1.22f
        private const val TITLE_FONT_SCALE = 1.32f
        private const val TITLE_GLYPH_HEIGHT_FACTOR = 1.30f
        private const val BASELINE_ASCENT_RATIO = 0.88f
        private const val MIN_INLINE_IMAGE_EM_MULTIPLIER = 7.0f
        private const val MIN_INLINE_IMAGE_VIEWPORT_FRACTION = 0.28f
        private const val WIDE_SPREAD_IMAGE_ASPECT_RATIO = 1.25f
        private const val MAX_JUSTIFICATION_LETTER_SPACING_EM = 0.28f
        private const val MAX_KINSOKU_PULLBACK_CHARS = 3

        private const val VERTICAL_CELL_ADVANCE_FACTOR = 1.12f
        private const val VERTICAL_COLUMN_WIDTH_FACTOR = 1.25f

        private const val KINSOKU_FORBIDDEN_START_CHARS =
            "，。！？、；：·…—～〜」』）】》〉〕〗〙〛”’!%),.:;?]}¢°ˇˉ―‖‰′″›℃∶〃〞" +
                "︶︺︾﹀﹄﹚﹜﹞！％），．：；？］｝﹂﹄︼︸⋮︰︱︲︴"

        private const val KINSOKU_FORBIDDEN_END_CHARS =
            "「『（【《〈〔〖〘〚“‘([{‹«￥＄" +
                "︵︹︽︿﹁﹙﹛﹝（［｛﹃︻︷"

        fun isForbiddenLineStartChar(ch: Char): Boolean {
            return KINSOKU_FORBIDDEN_START_CHARS.indexOf(ch) >= 0
        }

        fun isForbiddenLineEndChar(ch: Char): Boolean {
            return KINSOKU_FORBIDDEN_END_CHARS.indexOf(ch) >= 0
        }
    }
}

/**
 * Default Android [ParagraphLineBreaker] backed by `android.text.StaticLayout.Builder` +
 * `LineBreakConfig.LINE_BREAK_STYLE_STRICT` (API 33+).
 *
 * Automatically falls back to deterministic CJK/Latin character-cell metrics when invoked inside
 * unit-test JVM shadows where `TextPaint.measureText` returns `<= 1f` without native FreeType.
 */
object AndroidStaticLayoutLineBreaker : ParagraphLineBreaker {

    override fun measureAndBreak(
        text: String,
        fontSizePx: Float,
        maxWidthPx: Int,
        typeface: Typeface?,
    ): MeasuredParagraphLines {
        val safeWidth = maxWidthPx.coerceAtLeast(1)
        val textPaint = runCatching {
            TextPaint(TextPaint.ANTI_ALIAS_FLAG or TextPaint.SUBPIXEL_TEXT_FLAG).apply {
                textSize = fontSizePx
                if (typeface != null) {
                    this.typeface = typeface
                }
            }
        }.getOrNull()

        val probeIdeographWidth = runCatching { textPaint?.measureText("国") ?: 0f }.getOrDefault(0f)
        if (textPaint != null && probeIdeographWidth > 1f) {
            val staticLayout = runCatching {
                buildPlatformStaticLayout(
                    text = text,
                    textPaint = textPaint,
                    contentWidthPx = safeWidth,
                )
            }.getOrNull()

            if (staticLayout != null && staticLayout.lineCount > 0) {
                val ranges = ArrayList<IntRange>(staticLayout.lineCount)
                for (line in 0 until staticLayout.lineCount) {
                    val start = staticLayout.getLineStart(line)
                    val endExclusive = staticLayout.getLineEnd(line)
                    if (endExclusive > start) {
                        ranges.add(start until endExclusive)
                    }
                }
                if (ranges.isNotEmpty()) {
                    return MeasuredParagraphLines(
                        initialLineRanges = ranges,
                        charWidthLookup = { start, endExclusive ->
                            if (endExclusive <= start) 0f else textPaint.measureText(text, start, endExclusive)
                        },
                    )
                }
            }
        }

        return DeterministicCellLineBreaker.measureAndBreak(
            text = text,
            fontSizePx = fontSizePx,
            maxWidthPx = safeWidth,
            typeface = typeface,
        )
    }

    private fun buildPlatformStaticLayout(
        text: CharSequence,
        textPaint: TextPaint,
        contentWidthPx: Int,
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, textPaint, contentWidthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(false)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setLineBreakConfig(
                        LineBreakConfig.Builder()
                            .setLineBreakStyle(LineBreakConfig.LINE_BREAK_STYLE_STRICT)
                            .setLineBreakWordStyle(LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE)
                            .build(),
                    )
                }
            }
            .build()
    }
}

/**
 * Pure Kotlin CJK/ASCII cell width fallback used when FreeType native metrics are absent in JVM tests.
 */
object DeterministicCellLineBreaker : ParagraphLineBreaker {

    override fun measureAndBreak(
        text: String,
        fontSizePx: Float,
        maxWidthPx: Int,
        typeface: Typeface?,
    ): MeasuredParagraphLines {
        val prefixWidths = FloatArray(text.length + 1)
        for (i in text.indices) {
            prefixWidths[i + 1] = prefixWidths[i] + estimateGlyphWidthPx(text[i], fontSizePx)
        }

        val widthLookup: (Int, Int) -> Float = { start, endExclusive ->
            val s = start.coerceIn(0, text.length)
            val e = endExclusive.coerceIn(s, text.length)
            prefixWidths[e] - prefixWidths[s]
        }

        val maxW = maxWidthPx.coerceAtLeast(ceil(fontSizePx).toInt()).toFloat()
        val ranges = ArrayList<IntRange>()
        var lineStart = 0
        while (lineStart < text.length) {
            var cursor = lineStart + 1
            while (cursor <= text.length && widthLookup(lineStart, cursor) <= maxW + 0.01f) {
                cursor++
            }
            val lineEndExclusive = (cursor - 1).coerceAtLeast(lineStart + 1)
            ranges.add(lineStart until lineEndExclusive)
            lineStart = lineEndExclusive
        }

        return MeasuredParagraphLines(
            initialLineRanges = ranges,
            charWidthLookup = widthLookup,
        )
    }

    private fun estimateGlyphWidthPx(ch: Char, fontSizePx: Float): Float {
        return when {
            ch == '\u3000' -> fontSizePx
            ch.code in 0x20..0x7E -> fontSizePx * 0.55f
            else -> fontSizePx
        }
    }
}
