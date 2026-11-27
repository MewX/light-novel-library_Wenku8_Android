package org.mewx.wenku8.data.local.db.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import org.mewx.wenku8.data.local.db.entity.NovelEntity

/**
 * Lightweight sync header projection read by `NovelTocSyncRepository` before deciding whether a
 * `do=list` response is stale, unchanged, or requires an in-place `@Upsert`.
 */
data class NovelSyncHeader(
    @ColumnInfo(name = "aid") val aid: Int,
    @ColumnInfo(name = "content_lang") val contentLang: String,
    @ColumnInfo(name = "toc_version") val tocVersion: Long,
    @ColumnInfo(name = "toc_sha256") val tocSha256: String,
    @ColumnInfo(name = "meta_synced_at_ms") val metaSyncedAtMs: Long,
)

/**
 * Lightweight projection read by `DiscoveryRepository` to enrich list cards with a locally
 * cached `full_intro` for fuller 2-line previews without loading full novel rows.
 */
data class NovelFullIntroProjection(
    @ColumnInfo(name = "aid") val aid: Int,
    @ColumnInfo(name = "full_intro") val fullIntro: String,
)

/**
 * DAO for `novels` (`OnConflictStrategy.REPLACE` is strictly banned across the entire database so
 * SQLite never executes `DELETE + INSERT` and never triggers `ON DELETE CASCADE` on child tables).
 */
@Dao
interface NovelDao {

    @Upsert
    suspend fun upsertNovel(novel: NovelEntity)

    @Upsert
    suspend fun upsertNovels(novels: List<NovelEntity>)

    @Query("SELECT * FROM novels WHERE aid = :aid LIMIT 1")
    suspend fun getNovelByAid(aid: Int): NovelEntity?

    @Query("SELECT * FROM novels WHERE aid = :aid LIMIT 1")
    fun observeNovelByAid(aid: Int): Flow<NovelEntity?>

    @Query(
        """
        SELECT aid, content_lang, toc_version, toc_sha256, meta_synced_at_ms
        FROM novels
        WHERE aid = :aid
        LIMIT 1
        """,
    )
    suspend fun getSyncHeader(aid: Int): NovelSyncHeader?

    @Query("UPDATE novels SET meta_synced_at_ms = :syncedAtMs WHERE aid = :aid")
    suspend fun touchMetaSyncedAt(aid: Int, syncedAtMs: Long)

    @Query(
        """
        UPDATE novels
        SET toc_version = :tocVersion,
            toc_sha256 = :tocSha256,
            content_lang = :contentLang,
            meta_synced_at_ms = :syncedAtMs
        WHERE aid = :aid
        """,
    )
    suspend fun updateTocVersion(
        aid: Int,
        tocVersion: Long,
        tocSha256: String,
        contentLang: String,
        syncedAtMs: Long,
    )

    @Query(
        """
        SELECT * FROM novels
        WHERE in_bookshelf = 1
        ORDER BY shelf_sort_order ASC, last_accessed_ms DESC
        """,
    )
    fun observeBookshelfNovels(): Flow<List<NovelEntity>>

    @Query("SELECT aid FROM novels WHERE in_bookshelf = 1 ORDER BY shelf_sort_order ASC")
    suspend fun getBookshelfNovelIds(): List<Int>

    @Query("SELECT aid, full_intro FROM novels WHERE aid IN (:aids) AND full_intro != ''")
    suspend fun getCachedFullIntrosByAids(aids: List<Int>): List<NovelFullIntroProjection>

    @Query(
        """
        SELECT * FROM novels
        WHERE in_bookshelf = 1
          AND (
              title LIKE '%' || :keyword || '%'
              OR author LIKE '%' || :keyword || '%'
              OR tags_csv LIKE '%' || :keyword || '%'
              OR CAST(aid AS TEXT) = :keyword
          )
        ORDER BY shelf_sort_order ASC
        """,
    )
    fun observeLocalBookshelfMatches(keyword: String): Flow<List<NovelEntity>>

    @Query(
        """
        UPDATE novels
        SET in_bookshelf = :inBookshelf,
            shelf_category = :shelfCategory,
            shelf_sort_order = :shelfSortOrder,
            cloud_synced = :cloudSynced
        WHERE aid = :aid
        """,
    )
    suspend fun updateBookshelfState(
        aid: Int,
        inBookshelf: Boolean,
        shelfCategory: String,
        shelfSortOrder: Int,
        cloudSynced: Boolean,
    )

    @Query("SELECT * FROM novels WHERE in_bookshelf = 1 ORDER BY shelf_sort_order ASC, last_accessed_ms DESC")
    suspend fun getBookshelfNovelsSnapshot(): List<NovelEntity>

    @Query("SELECT COALESCE(MAX(shelf_sort_order), 0) FROM novels WHERE in_bookshelf = 1")
    suspend fun getMaxBookshelfSortOrder(): Int

    @Query("SELECT COALESCE(MIN(shelf_sort_order), 0) FROM novels WHERE in_bookshelf = 1")
    suspend fun getMinBookshelfSortOrder(): Int

    @Query("UPDATE novels SET cloud_synced = :cloudSynced WHERE aid = :aid")
    suspend fun setCloudSynced(aid: Int, cloudSynced: Boolean)

    @Query("UPDATE novels SET shelf_sort_order = :sortOrder WHERE aid = :aid")
    suspend fun updateShelfSortOrder(aid: Int, sortOrder: Int)

    @Query("UPDATE novels SET shelf_category = :category WHERE aid IN (:aids)")
    suspend fun updateShelfCategoryForNovels(aids: List<Int>, category: String)

    @Query(
        """
        UPDATE novels
        SET in_bookshelf = 0,
            cloud_synced = 0,
            has_unread_update = 0
        WHERE aid IN (:aids)
        """,
    )
    suspend fun removeNovelsFromBookshelf(aids: List<Int>)

    @Query("UPDATE novels SET has_unread_update = :hasUnreadUpdate WHERE aid = :aid")
    suspend fun setHasUnreadUpdate(aid: Int, hasUnreadUpdate: Boolean)

    @Query("SELECT aid FROM novels WHERE in_bookshelf = 0")
    suspend fun getNonBookshelfNovelIds(): List<Int>

    @Query("DELETE FROM novels WHERE in_bookshelf = 0")
    suspend fun deleteAllNonBookshelfNovels(): Int

    /**
     * Merges fresh server metadata (`do=meta` / `novellist` / `do=intro`) into `novels` WITHOUT
     * overwriting user-owned columns (`in_bookshelf`, `shelf_category`, `cloud_synced`,
     * `shelf_sort_order`, `toc_version`, `toc_sha256`).
     *
     * Also raises `has_unread_update = true` automatically when a bookshelf novel's server
     * `latestSectionCid` advances past the previously stored non-zero `latestSectionCid`.
     */
    @Transaction
    suspend fun mergeServerMetadataPreservingShelfState(incoming: NovelEntity) {
        val existing = getNovelByAid(incoming.aid)
        if (existing == null) {
            upsertNovel(incoming)
            return
        }

        val detectedNewChapterOnShelf = existing.inBookshelf &&
            existing.latestSectionCid > 0 &&
            incoming.latestSectionCid > existing.latestSectionCid

        val merged = existing.copy(
            contentLang = incoming.contentLang.ifEmpty { existing.contentLang },
            metaSyncedAtMs = maxOf(existing.metaSyncedAtMs, incoming.metaSyncedAtMs),
            title = incoming.title.ifEmpty { existing.title },
            author = incoming.author.ifEmpty { existing.author },
            bookStatus = incoming.bookStatus.ifEmpty { existing.bookStatus },
            pressId = incoming.pressId.ifEmpty { existing.pressId },
            bookLength = if (incoming.bookLength > 0) incoming.bookLength else existing.bookLength,
            lastUpdate = incoming.lastUpdate.ifEmpty { existing.lastUpdate },
            latestSectionCid = if (incoming.latestSectionCid > 0) {
                incoming.latestSectionCid
            } else {
                existing.latestSectionCid
            },
            latestSectionName = incoming.latestSectionName.ifEmpty { existing.latestSectionName },
            dayHits = if (incoming.dayHits > 0) incoming.dayHits else existing.dayHits,
            totalHits = if (incoming.totalHits > 0) incoming.totalHits else existing.totalHits,
            pushCount = if (incoming.pushCount > 0) incoming.pushCount else existing.pushCount,
            favCount = if (incoming.favCount > 0) incoming.favCount else existing.favCount,
            tagsCsv = incoming.tagsCsv.ifEmpty { existing.tagsCsv },
            introShort = incoming.introShort.ifEmpty { existing.introShort },
            fullIntro = incoming.fullIntro.ifEmpty { existing.fullIntro },
            hasUnreadUpdate = existing.hasUnreadUpdate || detectedNewChapterOnShelf,
            lastAccessedMs = maxOf(existing.lastAccessedMs, incoming.lastAccessedMs),
        )
        upsertNovel(merged)
    }
}
