package org.mewx.wenku8.v2.data.local.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.mewx.wenku8.v2.data.local.db.entity.BookmarkNoteEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingSessionEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReviewDraftEntity
import org.mewx.wenku8.v2.data.local.db.entity.SearchHistoryEntity

/**
 * Joined projection pairing a [ReadingProgressEntity] anchor with its parent novel's title,
 * author, and bookshelf status. Powers both the Profile Reading History list and the Home-Screen
 * "Continue Reading" widget without extra N+1 queries.
 */
data class ReadingHistoryWithNovel(
    @ColumnInfo(name = "aid") val aid: Int,
    @ColumnInfo(name = "vid") val vid: Int,
    @ColumnInfo(name = "cid") val cid: Int,
    @ColumnInfo(name = "volume_title") val volumeTitle: String,
    @ColumnInfo(name = "chapter_title") val chapterTitle: String,
    @ColumnInfo(name = "paragraph_index") val paragraphIndex: Int,
    @ColumnInfo(name = "char_offset") val charOffset: Int,
    @ColumnInfo(name = "progress_permille") val progressPermille: Int,
    @ColumnInfo(name = "updated_at_ms") val updatedAtMs: Long,
    @ColumnInfo(name = "novel_title") val novelTitle: String,
    @ColumnInfo(name = "novel_author") val novelAuthor: String,
    @ColumnInfo(name = "in_bookshelf") val inBookshelf: Boolean,
)

/**
 * Joined projection pairing a [BookmarkNoteEntity] with its parent novel's title/author and
 * optional resolved volume title so the Cross-Book Bookmarks Hub can jump straight into
 * `UnifiedReaderScreen` with full context.
 */
data class BookmarkNoteWithNovel(
    @ColumnInfo(name = "id") val id: Long,
    @ColumnInfo(name = "aid") val aid: Int,
    @ColumnInfo(name = "vid") val vid: Int,
    @ColumnInfo(name = "cid") val cid: Int,
    @ColumnInfo(name = "volume_title") val volumeTitle: String,
    @ColumnInfo(name = "chapter_title") val chapterTitle: String,
    @ColumnInfo(name = "start_paragraph") val startParagraph: Int,
    @ColumnInfo(name = "start_char") val startChar: Int,
    @ColumnInfo(name = "end_paragraph") val endParagraph: Int,
    @ColumnInfo(name = "end_char") val endChar: Int,
    @ColumnInfo(name = "snippet_text") val snippetText: String,
    @ColumnInfo(name = "note_markdown") val noteMarkdown: String,
    @ColumnInfo(name = "style_type") val styleType: String,
    @ColumnInfo(name = "color_hex") val colorHex: String,
    @ColumnInfo(name = "created_at_ms") val createdAtMs: Long,
    @ColumnInfo(name = "novel_title") val novelTitle: String,
    @ColumnInfo(name = "novel_author") val novelAuthor: String,
)

@Dao
interface ReadingProgressDao {

    @Upsert
    suspend fun upsertProgress(progress: ReadingProgressEntity)

    @Upsert
    suspend fun upsertProgressList(progressList: List<ReadingProgressEntity>)

    @Query("SELECT * FROM reading_progress WHERE aid = :aid LIMIT 1")
    suspend fun getProgressForNovel(aid: Int): ReadingProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE aid = :aid LIMIT 1")
    fun observeProgressForNovel(aid: Int): Flow<ReadingProgressEntity?>

    @Query("SELECT * FROM reading_progress ORDER BY updated_at_ms DESC LIMIT 1")
    fun observeMostRecentProgress(): Flow<ReadingProgressEntity?>

    @Query(
        """
        SELECT
            rp.aid AS aid,
            rp.vid AS vid,
            rp.cid AS cid,
            rp.volume_title AS volume_title,
            rp.chapter_title AS chapter_title,
            rp.paragraph_index AS paragraph_index,
            rp.char_offset AS char_offset,
            rp.progress_permille AS progress_permille,
            rp.updated_at_ms AS updated_at_ms,
            n.title AS novel_title,
            n.author AS novel_author,
            n.in_bookshelf AS in_bookshelf
        FROM reading_progress rp
        INNER JOIN novels n ON n.aid = rp.aid
        ORDER BY rp.updated_at_ms DESC
        LIMIT :limit
        """,
    )
    fun observeRecentHistoryWithNovels(limit: Int = 100): Flow<List<ReadingHistoryWithNovel>>

    @Query(
        """
        SELECT
            rp.aid AS aid,
            rp.vid AS vid,
            rp.cid AS cid,
            rp.volume_title AS volume_title,
            rp.chapter_title AS chapter_title,
            rp.paragraph_index AS paragraph_index,
            rp.char_offset AS char_offset,
            rp.progress_permille AS progress_permille,
            rp.updated_at_ms AS updated_at_ms,
            n.title AS novel_title,
            n.author AS novel_author,
            n.in_bookshelf AS in_bookshelf
        FROM reading_progress rp
        INNER JOIN novels n ON n.aid = rp.aid
        ORDER BY rp.updated_at_ms DESC
        LIMIT 1
        """,
    )
    suspend fun getMostRecentHistoryWithNovel(): ReadingHistoryWithNovel?

    @Query("SELECT * FROM reading_progress")
    fun observeAllProgress(): Flow<List<ReadingProgressEntity>>

    @Query("SELECT * FROM reading_progress")
    suspend fun getAllProgress(): List<ReadingProgressEntity>

    @Query("DELETE FROM reading_progress WHERE aid = :aid")
    suspend fun deleteProgressForNovel(aid: Int)

    @Query(
        """
        UPDATE reading_progress
        SET vid = :newVid,
            volume_title = :newVolumeTitle,
            chapter_title = :newChapterTitle
        WHERE aid = :aid
        """,
    )
    suspend fun healVolumeOwner(
        aid: Int,
        newVid: Int,
        newVolumeTitle: String,
        newChapterTitle: String,
    )
}

@Dao
interface BookmarkNoteDao {

    @Upsert
    suspend fun upsertBookmarkNote(note: BookmarkNoteEntity): Long

    @Query("SELECT * FROM bookmarks_notes WHERE aid = :aid ORDER BY created_at_ms DESC")
    fun observeNotesForNovel(aid: Int): Flow<List<BookmarkNoteEntity>>

    @Query("SELECT * FROM bookmarks_notes WHERE aid = :aid AND cid = :cid ORDER BY start_paragraph ASC, start_char ASC")
    fun observeNotesForChapter(aid: Int, cid: Int): Flow<List<BookmarkNoteEntity>>

    @Query(
        """
        SELECT
            bn.id AS id,
            bn.aid AS aid,
            bn.vid AS vid,
            bn.cid AS cid,
            COALESCE(v.volume_title, '') AS volume_title,
            bn.chapter_title AS chapter_title,
            bn.start_paragraph AS start_paragraph,
            bn.start_char AS start_char,
            bn.end_paragraph AS end_paragraph,
            bn.end_char AS end_char,
            bn.snippet_text AS snippet_text,
            bn.note_markdown AS note_markdown,
            bn.style_type AS style_type,
            bn.color_hex AS color_hex,
            bn.created_at_ms AS created_at_ms,
            n.title AS novel_title,
            n.author AS novel_author
        FROM bookmarks_notes bn
        INNER JOIN novels n ON n.aid = bn.aid
        LEFT JOIN volumes v ON v.aid = bn.aid AND v.vid = bn.vid
        ORDER BY bn.created_at_ms DESC
        """,
    )
    fun observeAllBookmarksWithNovels(): Flow<List<BookmarkNoteWithNovel>>

    @Query("SELECT COUNT(*) FROM bookmarks_notes")
    fun observeTotalBookmarkNoteCount(): Flow<Int>

    @Query("SELECT * FROM bookmarks_notes")
    suspend fun getAllBookmarkNotes(): List<BookmarkNoteEntity>

    @Query("DELETE FROM bookmarks_notes WHERE id = :id")
    suspend fun deleteBookmarkNoteById(id: Long)
}

@Dao
interface ReadingSessionDao {

    @Upsert
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Query("SELECT * FROM reading_sessions WHERE date_epoch_day >= :fromEpochDay ORDER BY date_epoch_day ASC")
    fun observeSessionsSince(fromEpochDay: Long): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY date_epoch_day ASC")
    fun observeAllSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions")
    suspend fun getAllSessions(): List<ReadingSessionEntity>

    @Query("SELECT COALESCE(SUM(duration_seconds), 0) FROM reading_sessions")
    fun observeTotalReadingSeconds(): Flow<Long>

    @Query("SELECT COALESCE(SUM(characters_read), 0) FROM reading_sessions")
    fun observeTotalCharactersRead(): Flow<Long>
}

@Dao
interface ReviewDraftDao {

    @Upsert
    suspend fun upsertDraft(draft: ReviewDraftEntity)

    @Query("SELECT * FROM review_drafts WHERE aid = :aid AND rid = :rid LIMIT 1")
    suspend fun getDraft(aid: Int, rid: Int): ReviewDraftEntity?

    @Query("SELECT * FROM review_drafts")
    suspend fun getAllDrafts(): List<ReviewDraftEntity>

    @Query("DELETE FROM review_drafts WHERE aid = :aid AND rid = :rid")
    suspend fun deleteDraft(aid: Int, rid: Int)
}

@Dao
interface SearchHistoryDao {

    @Upsert
    suspend fun upsertHistoryEntry(entry: SearchHistoryEntity)

    @Upsert
    suspend fun upsertHistoryEntries(entries: List<SearchHistoryEntity>)

    @Query("SELECT * FROM search_history ORDER BY searched_at_ms DESC LIMIT :limit")
    fun observeRecentHistory(limit: Int = DEFAULT_MAX_HISTORY_ITEMS): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history ORDER BY searched_at_ms DESC")
    suspend fun getAllHistory(): List<SearchHistoryEntity>

    @Query("DELETE FROM search_history WHERE query = :query")
    suspend fun deleteHistoryEntry(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearAllHistory()

    @Query(
        """
        DELETE FROM search_history
        WHERE query NOT IN (
            SELECT query FROM search_history
            ORDER BY searched_at_ms DESC
            LIMIT :maxEntries
        )
        """,
    )
    suspend fun trimHistoryToMax(maxEntries: Int = DEFAULT_MAX_HISTORY_ITEMS)

    /**
     * Inserts/updates a search keyword and trims older entries past 20 items (matching `v1.31`
     * `GlobalConfig.maxSearchHistory = 20`).
     */
    @Transaction
    suspend fun recordSearchAndTrim(
        query: String,
        searchType: String,
        searchedAtMs: Long,
        maxEntries: Int = DEFAULT_MAX_HISTORY_ITEMS,
    ) {
        val cleanQuery = query.trim()
        if (cleanQuery.isEmpty()) return
        upsertHistoryEntry(
            SearchHistoryEntity(
                query = cleanQuery,
                searchType = searchType,
                searchedAtMs = searchedAtMs,
            ),
        )
        trimHistoryToMax(maxEntries)
    }

    companion object {
        const val DEFAULT_MAX_HISTORY_ITEMS = 20
    }
}
