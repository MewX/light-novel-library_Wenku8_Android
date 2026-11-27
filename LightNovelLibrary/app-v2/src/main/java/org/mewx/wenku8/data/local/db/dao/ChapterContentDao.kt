package org.mewx.wenku8.data.local.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity

/**
 * Header metadata for a cached chapter WITHOUT pulling `content_text` into a SQLite `CursorWindow`.
 */
data class ChapterContentHeader(
    @ColumnInfo(name = "cid") val cid: Int,
    @ColumnInfo(name = "aid") val aid: Int,
    @ColumnInfo(name = "content_lang") val contentLang: String,
    @ColumnInfo(name = "char_length") val charLength: Int,
    @ColumnInfo(name = "byte_length") val byteLength: Int,
    @ColumnInfo(name = "image_urls_json") val imageUrlsJson: String,
    @ColumnInfo(name = "image_count") val imageCount: Int,
    @ColumnInfo(name = "sha256_checksum") val sha256Checksum: String,
    @ColumnInfo(name = "downloaded_at_ms") val downloadedAtMs: Long,
)

data class NovelCachedChapterCount(
    @ColumnInfo(name = "aid") val aid: Int,
    @ColumnInfo(name = "cached_count") val cachedCount: Int,
)

data class CachedChapterImagesRow(
    @ColumnInfo(name = "cid") val cid: Int,
    @ColumnInfo(name = "image_urls_json") val imageUrlsJson: String,
)

/**
 * Lightweight CursorWindow-safe projection for storage inventory & fine-grained cache cleanup.
 * Deliberately excludes `content_text` so scanning thousands of cached chapters stays fast & safe.
 */
data class CachedChapterCacheMeta(
    @ColumnInfo(name = "cid") val cid: Int,
    @ColumnInfo(name = "aid") val aid: Int,
    @ColumnInfo(name = "byte_length") val byteLength: Int,
    @ColumnInfo(name = "image_urls_json") val imageUrlsJson: String,
    @ColumnInfo(name = "image_count") val imageCount: Int,
)

/**
 * DAO for `chapter_contents` with strict `2 MB` `CursorWindow` protection.
 *
 * Why [loadChapterTextSafely] uses `SUBSTR` slices instead of `SELECT content_text`:
 * Android's `SQLiteCursor` allocates a fixed `2 MB` shared memory `CursorWindow`. UTF-16/UTF-8
 * omnibus chapters or side-story compilations exceeding `~1.5 MB` throw
 * `SQLiteBlobTooBigException: Row too big to fit into CursorWindow` if fetched in a single column
 * read. Reading `LENGTH(content_text)` first and slicing in `256,000`-character windows (~`750 KB`
 * UTF-8 max per slice) guarantees fast single-query reads for 99.9% of chapters (`<= 256k` chars)
 * while seamlessly handling arbitrarily large omnibus chapters with zero `CursorWindow` crashes!
 */
@Dao
interface ChapterContentDao {

    @Upsert
    suspend fun upsertChapterContent(content: ChapterContentEntity)

    @Upsert
    suspend fun upsertChapterContents(contents: List<ChapterContentEntity>)

    @Query("SELECT cid FROM chapter_contents WHERE aid = :aid")
    fun observeCachedChapterIdsForNovel(aid: Int): Flow<List<Int>>

    @Query("SELECT cid FROM chapter_contents WHERE aid = :aid")
    suspend fun getCachedChapterIdsForNovel(aid: Int): List<Int>

    @Query("SELECT aid, COUNT(*) AS cached_count FROM chapter_contents GROUP BY aid")
    fun observeCachedChapterCountsByNovel(): Flow<List<NovelCachedChapterCount>>

    @Query(
        """
        SELECT cid,
               aid,
               content_lang,
               LENGTH(content_text) AS char_length,
               byte_length,
               image_urls_json,
               image_count,
               sha256_checksum,
               downloaded_at_ms
        FROM chapter_contents
        WHERE cid = :cid
        LIMIT 1
        """,
    )
    suspend fun getChapterHeader(cid: Int): ChapterContentHeader?

    /**
     * SQLite `SUBSTR(text, X, Y)` is **1-indexed** (`startOneIndexed >= 1`).
     */
    @Query(
        """
        SELECT SUBSTR(content_text, :startOneIndexed, :maxChars)
        FROM chapter_contents
        WHERE cid = :cid
        LIMIT 1
        """,
    )
    suspend fun readChapterTextSlice(cid: Int, startOneIndexed: Int, maxChars: Int): String?

    @Query("SELECT COALESCE(SUM(byte_length), 0) FROM chapter_contents")
    suspend fun getTotalCachedTextBytes(): Long

    @Query("SELECT image_urls_json FROM chapter_contents WHERE image_count > 0")
    suspend fun getAllNonEmptyImageUrlsJson(): List<String>

    @Query("SELECT image_urls_json FROM chapter_contents WHERE aid IN (:aids) AND image_count > 0")
    suspend fun getImageUrlsJsonForNovels(aids: List<Int>): List<String>

    @Query("SELECT cid, image_urls_json FROM chapter_contents WHERE aid = :aid AND image_count > 0")
    suspend fun getCachedImageChaptersForNovel(aid: Int): List<CachedChapterImagesRow>

    @Query("SELECT cid, aid, byte_length, image_urls_json, image_count FROM chapter_contents")
    suspend fun getAllCachedChapterCacheMeta(): List<CachedChapterCacheMeta>

    @Query(
        """
        SELECT cid, aid, byte_length, image_urls_json, image_count
        FROM chapter_contents
        WHERE cid IN (:cids)
        """,
    )
    suspend fun getCachedChapterCacheMetaByCidsChunk(cids: List<Int>): List<CachedChapterCacheMeta>

    @Query("SELECT cid FROM chapter_contents WHERE aid IN (:aids)")
    suspend fun getCachedChapterIdsForNovelsChunk(aids: List<Int>): List<Int>

    @Query("SELECT COUNT(*) FROM chapter_contents WHERE aid = :aid")
    suspend fun getCachedChapterCountForNovel(aid: Int): Int

    @Query("DELETE FROM chapter_contents WHERE cid = :cid")
    suspend fun deleteChapterContent(cid: Int)

    @Query("DELETE FROM chapter_contents WHERE cid IN (:cids)")
    suspend fun deleteChapterContentsByCidsChunk(cids: List<Int>): Int

    @Query("DELETE FROM chapter_contents WHERE aid = :aid")
    suspend fun deleteAllChapterContentsForNovel(aid: Int)

    @Query("DELETE FROM chapter_contents WHERE aid IN (:aids)")
    suspend fun deleteChapterContentsForNovels(aids: List<Int>)

    suspend fun getCachedChapterCacheMetaByCids(cids: List<Int>): List<CachedChapterCacheMeta> {
        if (cids.isEmpty()) return emptyList()
        return cids.distinct().chunked(SQLITE_SAFE_BIND_CHUNK_SIZE).flatMap { chunk ->
            getCachedChapterCacheMetaByCidsChunk(chunk)
        }
    }

    suspend fun getCachedChapterIdsForNovels(aids: List<Int>): List<Int> {
        if (aids.isEmpty()) return emptyList()
        return aids.distinct().chunked(SQLITE_SAFE_BIND_CHUNK_SIZE).flatMap { chunk ->
            getCachedChapterIdsForNovelsChunk(chunk)
        }
    }

    suspend fun deleteChapterContentsByCids(cids: List<Int>): Int {
        if (cids.isEmpty()) return 0
        var totalDeleted = 0
        cids.distinct().chunked(SQLITE_SAFE_BIND_CHUNK_SIZE).forEach { chunk ->
            totalDeleted += deleteChapterContentsByCidsChunk(chunk)
        }
        return totalDeleted
    }

    /**
     * CursorWindow-safe single-chapter reader: fetches normal chapters (`<= 256,000` chars) in one
     * slice and transparently stitches multi-slice omnibus chapters (`> 256,000` chars).
     */
    suspend fun loadChapterTextSafely(cid: Int): String? {
        val header = getChapterHeader(cid) ?: return null
        if (header.charLength <= 0) return ""
        if (header.charLength <= SAFE_SLICE_CHAR_COUNT) {
            return readChapterTextSlice(cid, startOneIndexed = 1, maxChars = header.charLength)
        }

        val assembled = StringBuilder(header.charLength)
        var offsetOneIndexed = 1
        while (offsetOneIndexed <= header.charLength) {
            val remainingChars = header.charLength - offsetOneIndexed + 1
            val sliceLength = minOf(SAFE_SLICE_CHAR_COUNT, remainingChars)
            val slice = readChapterTextSlice(
                cid = cid,
                startOneIndexed = offsetOneIndexed,
                maxChars = sliceLength,
            ) ?: break
            if (slice.isEmpty()) break
            assembled.append(slice)
            offsetOneIndexed += slice.length
        }
        return assembled.toString()
    }

    companion object {
        const val SAFE_SLICE_CHAR_COUNT = 256_000
        private const val SQLITE_SAFE_BIND_CHUNK_SIZE = 400
    }
}
