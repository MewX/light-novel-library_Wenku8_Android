package org.mewx.wenku8.v2.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Reflow-invariant reading progress anchor (`reading_progress` table).
 *
 * Keyed by [aid] with a foreign key strictly to [NovelEntity] so TOC volume reorganizations never
 * wipe a user's reading progress; `NovelTocSyncRepository` automatically heals [vid] and
 * [volumeTitle] whenever [cid] moves into a recompiled volume.
 */
@Entity(
    tableName = "reading_progress",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["aid"],
            childColumns = ["aid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["updated_at_ms"]),
    ],
)
data class ReadingProgressEntity(
    @PrimaryKey
    @ColumnInfo(name = "aid")
    val aid: Int,

    @ColumnInfo(name = "vid")
    val vid: Int,

    @ColumnInfo(name = "cid")
    val cid: Int,

    @ColumnInfo(name = "volume_title")
    val volumeTitle: String = "",

    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String = "",

    @ColumnInfo(name = "paragraph_index")
    val paragraphIndex: Int = 0,

    @ColumnInfo(name = "char_offset")
    val charOffset: Int = 0,

    @ColumnInfo(name = "legacy_scroll_px")
    val legacyScrollPx: Int = 0,

    @ColumnInfo(name = "progress_permille")
    val progressPermille: Int = 0,

    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
)

/**
 * User bookmark, text highlight, or markdown reading note (`bookmarks_notes` table).
 * Foreign key points strictly to [NovelEntity] (`aid`) to survive TOC updates.
 */
@Entity(
    tableName = "bookmarks_notes",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["aid"],
            childColumns = ["aid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["aid", "cid"]),
        Index(value = ["created_at_ms"]),
    ],
)
data class BookmarkNoteEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "aid")
    val aid: Int,

    @ColumnInfo(name = "vid")
    val vid: Int,

    @ColumnInfo(name = "cid")
    val cid: Int,

    @ColumnInfo(name = "chapter_title")
    val chapterTitle: String = "",

    @ColumnInfo(name = "start_paragraph")
    val startParagraph: Int,

    @ColumnInfo(name = "start_char")
    val startChar: Int,

    @ColumnInfo(name = "end_paragraph")
    val endParagraph: Int,

    @ColumnInfo(name = "end_char")
    val endChar: Int,

    @ColumnInfo(name = "snippet_text")
    val snippetText: String,

    @ColumnInfo(name = "note_markdown")
    val noteMarkdown: String = "",

    @ColumnInfo(name = "style_type")
    val styleType: String = STYLE_BOOKMARK,

    @ColumnInfo(name = "color_hex")
    val colorHex: String = "#E8B966",

    @ColumnInfo(name = "created_at_ms")
    val createdAtMs: Long,
) {
    companion object {
        const val STYLE_BOOKMARK = "BOOKMARK"
        const val STYLE_HIGHLIGHT = "HIGHLIGHT"
        const val STYLE_UNDERLINE = "UNDERLINE"
        const val STYLE_WAVY = "WAVY"
    }
}

/**
 * Reading duration & character count log (`reading_sessions` table) powering the Profile
 * reading streak & daily heatmap. Kept independent of novel deletion so historical reading
 * stats never vanish when a user removes a finished book from their shelf!
 */
@Entity(
    tableName = "reading_sessions",
    indices = [
        Index(value = ["date_epoch_day"]),
        Index(value = ["aid"]),
    ],
)
data class ReadingSessionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0L,

    @ColumnInfo(name = "aid")
    val aid: Int,

    @ColumnInfo(name = "cid")
    val cid: Int,

    @ColumnInfo(name = "date_epoch_day")
    val dateEpochDay: Long,

    @ColumnInfo(name = "duration_seconds")
    val durationSeconds: Int,

    @ColumnInfo(name = "characters_read")
    val charactersRead: Int,
)

/**
 * Auto-saved community review / reply draft (`review_drafts` table).
 * `rid == 0` represents a new review thread draft for [aid]; `rid > 0` represents a reply draft.
 */
@Entity(
    tableName = "review_drafts",
    primaryKeys = ["aid", "rid"],
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["aid"],
            childColumns = ["aid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ReviewDraftEntity(
    @ColumnInfo(name = "aid")
    val aid: Int,

    @ColumnInfo(name = "rid")
    val rid: Int = 0,

    @ColumnInfo(name = "draft_title")
    val draftTitle: String = "",

    @ColumnInfo(name = "draft_content")
    val draftContent: String,

    @ColumnInfo(name = "updated_at_ms")
    val updatedAtMs: Long,
)

/**
 * Search history keyword record (`search_history` table).
 * Stores clean keywords (without `v1.31`'s raw `'0'` / `'1'` prefix characters) alongside an
 * explicit [searchType] (`articlename`, `author`, or `aid`).
 */
@Entity(
    tableName = "search_history",
    indices = [
        Index(value = ["searched_at_ms"]),
    ],
)
data class SearchHistoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "query")
    val query: String,

    @ColumnInfo(name = "search_type")
    val searchType: String = SEARCH_TYPE_ARTICLE_NAME,

    @ColumnInfo(name = "searched_at_ms")
    val searchedAtMs: Long,
) {
    companion object {
        const val SEARCH_TYPE_ARTICLE_NAME = "articlename"
        const val SEARCH_TYPE_AUTHOR = "author"
        const val SEARCH_TYPE_AID = "aid"
    }
}
