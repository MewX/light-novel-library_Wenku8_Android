package org.mewx.wenku8.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Volume index row (`volumes` table) populated from Wenku8 `do=list` XML.
 */
@Entity(
    tableName = "volumes",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["aid"],
            childColumns = ["aid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["aid", "volume_order"]),
    ],
)
data class VolumeEntity(
    @PrimaryKey
    @ColumnInfo(name = "vid")
    val vid: Int,

    @ColumnInfo(name = "aid")
    val aid: Int,

    @ColumnInfo(name = "volume_order")
    val volumeOrder: Int,

    @ColumnInfo(name = "volume_title")
    val volumeTitle: String,
)

/**
 * Chapter index row (`chapters` table) populated from Wenku8 `do=list` XML.
 */
@Entity(
    tableName = "chapters",
    foreignKeys = [
        ForeignKey(
            entity = VolumeEntity::class,
            parentColumns = ["vid"],
            childColumns = ["vid"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["aid"],
            childColumns = ["aid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["vid", "chapter_order"]),
        Index(value = ["aid"]),
    ],
)
data class ChapterEntity(
    @PrimaryKey
    @ColumnInfo(name = "cid")
    val cid: Int,

    @ColumnInfo(name = "vid")
    val vid: Int,

    @ColumnInfo(name = "aid")
    val aid: Int,

    @ColumnInfo(name = "chapter_order")
    val chapterOrder: Int,

    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String,

    @ColumnInfo(name = "is_illustration_chapter")
    val isIllustrationChapter: Boolean = isIllustrationChapterTitle(chapterTitle),
) {
    companion object {
        const val ILLUSTRATION_CHAPTER_TITLE_SC = "插图"
        const val ILLUSTRATION_CHAPTER_TITLE_TC = "插圖"

        private val BRACKET_TRIM_CHARS = charArrayOf(
            ' ', '\t', '\r', '\n', '\u3000',
            '【', '】', '[', ']', '(', ')', '（', '）', '「', '」', '『', '』', '《', '》', '<', '>',
        )

        /**
         * Matches standalone SC/TC illustration chapter labels (`插图`, `插圖`, `插画`, `插畫`,
         * `彩页`, `彩頁`, `口绘`, `口繪`, `Illustrations`) with optional numbering/part suffixes
         * (`插图1`, `插圖（上）`, `彩頁二`, etc.), while never matching prose sentences like
         * `"少女的插画日记"`.
         */
        private val STANDALONE_ILLUSTRATION_TOKEN_REGEX = Regex(
            pattern = "^(插图|插圖|插画|插畫|彩页|彩頁|口绘|口繪|illustrations?)" +
                "([\\s·\\-_]*[0-9一二三四五六七八九十百上下前後前后（）()①②③④⑤]*)?$",
            option = RegexOption.IGNORE_CASE,
        )

        /**
         * Matches unspaced volume-prefixed illustration labels such as `"第一卷插图"`, `"卷二插圖"`,
         * `"Vol.1彩頁"`, or `"序章口繪"`.
         */
        private val PREFIXED_VOLUME_ILLUSTRATION_REGEX = Regex(
            pattern = "^(第[零〇一二三四五六七八九十百千万0-9]+[卷部篇册冊章话話幕]|" +
                "卷[零〇一二三四五六七八九十百千万0-9]+|" +
                "vol\\.?\\s*[0-9]+|序章|终章|終章|特典|附录|附錄)" +
                "\\s*(插图|插圖|插画|插畫|彩页|彩頁|口绘|口繪)" +
                "([0-9一二三四五六七八九十上下（）()]*)?$",
            option = RegexOption.IGNORE_CASE,
        )

        private val TOKEN_SPLIT_DELIMITERS = charArrayOf(' ', '\t', '\u3000', '·', '・', ':', '：', '-', '—', '/')

        fun isIllustrationChapterTitle(rawTitle: String): Boolean {
            val cleaned = rawTitle.trim(*BRACKET_TRIM_CHARS)
            if (cleaned.isEmpty()) return false
            if (STANDALONE_ILLUSTRATION_TOKEN_REGEX.matches(cleaned)) return true
            if (PREFIXED_VOLUME_ILLUSTRATION_REGEX.matches(cleaned)) return true

            val trailingSegment = cleaned
                .substringAfterLastDelimiter(TOKEN_SPLIT_DELIMITERS)
                .trim(*BRACKET_TRIM_CHARS)
            if (trailingSegment.isNotEmpty() &&
                trailingSegment.length < cleaned.length &&
                STANDALONE_ILLUSTRATION_TOKEN_REGEX.matches(trailingSegment)
            ) {
                return true
            }
            return false
        }

        private fun String.substringAfterLastDelimiter(delimiters: CharArray): String {
            val lastIdx = lastIndexOfAny(delimiters)
            return if (lastIdx >= 0 && lastIdx + 1 < length) substring(lastIdx + 1) else ""
        }
    }
}
