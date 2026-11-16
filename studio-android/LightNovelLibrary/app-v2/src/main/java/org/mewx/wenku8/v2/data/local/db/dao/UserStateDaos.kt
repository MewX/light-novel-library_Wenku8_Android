package org.mewx.wenku8.v2.data.local.db.dao

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

    @Query("SELECT * FROM reading_progress")
    fun observeAllProgress(): Flow<List<ReadingProgressEntity>>

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

    @Query("DELETE FROM bookmarks_notes WHERE id = :id")
    suspend fun deleteBookmarkNoteById(id: Long)
}

@Dao
interface ReadingSessionDao {

    @Upsert
    suspend fun insertSession(session: ReadingSessionEntity): Long

    @Query("SELECT * FROM reading_sessions WHERE date_epoch_day >= :fromEpochDay ORDER BY date_epoch_day ASC")
    fun observeSessionsSince(fromEpochDay: Long): Flow<List<ReadingSessionEntity>>

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
