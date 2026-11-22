package org.mewx.wenku8.data.local.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.mewx.wenku8.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.data.local.db.entity.VolumeEntity

/**
 * Projection returned when looking up which volume currently owns a chapter (`cid`) so
 * `NovelTocSyncRepository` can heal `reading_progress.vid` & `volume_title` after TOC updates.
 */
data class ChapterVolumeOwner(
    @ColumnInfo(name = "vid") val vid: Int,
    @ColumnInfo(name = "volume_title") val volumeTitle: String,
    @ColumnInfo(name = "chapter_title") val chapterTitle: String,
)

/**
 * DAO for `volumes` and `chapters` TOC index tables.
 *
 * Note on SQLite bind-parameter limits:
 * Android API 24–30 bundles SQLite with `SQLITE_MAX_VARIABLE_NUMBER = 999`. Long-running series can
 * exceed 1,000 chapters, so pruning removed chapters via `WHERE cid NOT IN (:liveCids)` would throw
 * `SQLiteException: too many SQL variables`. Instead, [pruneObsoleteChapters] and
 * [pruneObsoleteVolumes] diff existing IDs against the live set in Kotlin and delete only obsolete
 * IDs in safe 400-ID chunks!
 */
@Dao
interface TocDao {

    @Upsert
    suspend fun upsertVolumes(volumes: List<VolumeEntity>)

    @Upsert
    suspend fun upsertChapters(chapters: List<ChapterEntity>)

    @Query("SELECT * FROM volumes WHERE aid = :aid ORDER BY volume_order ASC")
    fun observeVolumesForNovel(aid: Int): Flow<List<VolumeEntity>>

    @Query(
        """
        SELECT c.*
        FROM chapters AS c
        LEFT JOIN volumes AS v ON c.vid = v.vid
        WHERE c.aid = :aid
        ORDER BY COALESCE(v.volume_order, c.vid) ASC, c.chapter_order ASC
        """,
    )
    fun observeChaptersForNovel(aid: Int): Flow<List<ChapterEntity>>

    @Query("SELECT * FROM volumes WHERE aid = :aid ORDER BY volume_order ASC")
    suspend fun getVolumesForNovel(aid: Int): List<VolumeEntity>

    @Query(
        """
        SELECT c.*
        FROM chapters AS c
        LEFT JOIN volumes AS v ON c.vid = v.vid
        WHERE c.aid = :aid
        ORDER BY COALESCE(v.volume_order, c.vid) ASC, c.chapter_order ASC
        """,
    )
    suspend fun getChaptersForNovel(aid: Int): List<ChapterEntity>

    @Query(
        """
        SELECT c.aid AS aid,
               c.vid AS vid,
               v.volume_order AS volume_order,
               v.volume_title AS volume_title,
               c.cid AS cid,
               c.chapter_order AS chapter_order,
               c.chapter_title AS chapter_title,
               c.is_illustration_chapter AS is_illustration_chapter
        FROM chapters AS c
        INNER JOIN volumes AS v ON c.vid = v.vid
        WHERE c.aid = :aid
        ORDER BY v.volume_order ASC, c.chapter_order ASC
        """,
    )
    suspend fun getOrderedChaptersWithVolume(aid: Int): List<OrderedChapterWithVolume>

    @Query(
        """
        SELECT c.aid AS aid,
               c.vid AS vid,
               v.volume_order AS volume_order,
               v.volume_title AS volume_title,
               c.cid AS cid,
               c.chapter_order AS chapter_order,
               c.chapter_title AS chapter_title,
               c.is_illustration_chapter AS is_illustration_chapter
        FROM chapters AS c
        INNER JOIN volumes AS v ON c.vid = v.vid
        WHERE c.aid = :aid
        ORDER BY v.volume_order ASC, c.chapter_order ASC
        """,
    )
    fun observeOrderedChaptersWithVolume(aid: Int): Flow<List<OrderedChapterWithVolume>>

    @Query("SELECT vid FROM volumes WHERE aid = :aid")
    suspend fun getVolumeIdsForNovel(aid: Int): List<Int>

    @Query("SELECT cid FROM chapters WHERE aid = :aid")
    suspend fun getChapterIdsForNovel(aid: Int): List<Int>

    @Query("SELECT cid, aid FROM chapters")
    suspend fun getAllChapterToNovelMappings(): List<ChapterNovelMapping>

    @Query("SELECT aid, COUNT(*) AS total_count FROM chapters GROUP BY aid")
    fun observeTotalChapterCountsByNovel(): Flow<List<NovelTotalChapterCount>>

    @Query(
        """
        SELECT c.aid AS aid, c.cid AS cid
        FROM chapters AS c
        LEFT JOIN volumes AS v ON c.vid = v.vid
        INNER JOIN novels AS n ON c.aid = n.aid
        WHERE n.in_bookshelf = 1
        ORDER BY c.aid ASC, COALESCE(v.volume_order, c.vid) ASC, c.chapter_order ASC
        """,
    )
    fun observeOrderedBookshelfChapterIds(): Flow<List<OrderedNovelChapterId>>

    @Query("DELETE FROM chapters WHERE aid = :aid AND cid IN (:obsoleteCids)")
    suspend fun deleteChaptersByIdsChunk(aid: Int, obsoleteCids: List<Int>)

    @Query("DELETE FROM volumes WHERE aid = :aid AND vid IN (:obsoleteVids)")
    suspend fun deleteVolumesByIdsChunk(aid: Int, obsoleteVids: List<Int>)

    @Query(
        """
        SELECT c.vid AS vid, v.volume_title AS volume_title, c.chapter_title AS chapter_title
        FROM chapters AS c
        INNER JOIN volumes AS v ON c.vid = v.vid
        WHERE c.aid = :aid AND c.cid = :cid
        LIMIT 1
        """,
    )
    suspend fun findVolumeOwnerForChapter(aid: Int, cid: Int): ChapterVolumeOwner?

    suspend fun pruneObsoleteChapters(aid: Int, liveCids: Set<Int>) {
        if (liveCids.isEmpty()) return
        val existingCids = getChapterIdsForNovel(aid)
        val obsoleteCids = existingCids.filter { it !in liveCids }
        obsoleteCids.chunked(SQLITE_SAFE_BIND_CHUNK_SIZE).forEach { chunk ->
            deleteChaptersByIdsChunk(aid, chunk)
        }
    }

    suspend fun pruneObsoleteVolumes(aid: Int, liveVids: Set<Int>) {
        if (liveVids.isEmpty()) return
        val existingVids = getVolumeIdsForNovel(aid)
        val obsoleteVids = existingVids.filter { it !in liveVids }
        obsoleteVids.chunked(SQLITE_SAFE_BIND_CHUNK_SIZE).forEach { chunk ->
            deleteVolumesByIdsChunk(aid, chunk)
        }
    }

    companion object {
        private const val SQLITE_SAFE_BIND_CHUNK_SIZE = 400
    }
}

data class OrderedNovelChapterId(
    @ColumnInfo(name = "aid") val aid: Int,
    @ColumnInfo(name = "cid") val cid: Int,
)

data class ChapterNovelMapping(
    @ColumnInfo(name = "cid") val cid: Int,
    @ColumnInfo(name = "aid") val aid: Int,
)

data class NovelTotalChapterCount(
    @ColumnInfo(name = "aid") val aid: Int,
    @ColumnInfo(name = "total_count") val totalCount: Int,
)

data class OrderedChapterWithVolume(
    @ColumnInfo(name = "aid") val aid: Int,
    @ColumnInfo(name = "vid") val vid: Int,
    @ColumnInfo(name = "volume_order") val volumeOrder: Int,
    @ColumnInfo(name = "volume_title") val volumeTitle: String,
    @ColumnInfo(name = "cid") val cid: Int,
    @ColumnInfo(name = "chapter_order") val chapterOrder: Int,
    @ColumnInfo(name = "chapter_title") val chapterTitle: String,
    @ColumnInfo(name = "is_illustration_chapter") val isIllustrationChapter: Boolean,
)
