package org.mewx.wenku8.v2.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Canonical novel metadata record (`novels` table in `wenku8_v2.db`).
 *
 * Serves as the **sole foreign-key parent** for offline chapter contents, reading progress,
 * bookmarks/notes, and review drafts so reorganizing volumes/chapters (`do=list`) never triggers
 * cascading deletion of user data.
 */
@Entity(
    tableName = "novels",
    indices = [
        Index(value = ["in_bookshelf", "shelf_sort_order"]),
        Index(value = ["last_accessed_ms"]),
        Index(value = ["title"]),
        Index(value = ["author"]),
    ],
)
data class NovelEntity(
    @PrimaryKey
    @ColumnInfo(name = "aid")
    val aid: Int,

    @ColumnInfo(name = "content_lang")
    val contentLang: String = "SC",

    @ColumnInfo(name = "toc_version")
    val tocVersion: Long = 0L,

    @ColumnInfo(name = "toc_sha256")
    val tocSha256: String = "",

    @ColumnInfo(name = "meta_synced_at_ms")
    val metaSyncedAtMs: Long = 0L,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "author")
    val author: String = "",

    @ColumnInfo(name = "book_status")
    val bookStatus: String = "",

    @ColumnInfo(name = "press_id")
    val pressId: String = "",

    @ColumnInfo(name = "book_length")
    val bookLength: Int = 0,

    @ColumnInfo(name = "last_update")
    val lastUpdate: String = "",

    @ColumnInfo(name = "latest_section_cid")
    val latestSectionCid: Int = 0,

    @ColumnInfo(name = "latest_section_name")
    val latestSectionName: String = "",

    @ColumnInfo(name = "day_hits")
    val dayHits: Int = 0,

    @ColumnInfo(name = "total_hits")
    val totalHits: Int = 0,

    @ColumnInfo(name = "push_count")
    val pushCount: Int = 0,

    @ColumnInfo(name = "fav_count")
    val favCount: Int = 0,

    @ColumnInfo(name = "tags_csv")
    val tagsCsv: String = "",

    @ColumnInfo(name = "intro_short")
    val introShort: String = "",

    @ColumnInfo(name = "full_intro")
    val fullIntro: String = "",

    @ColumnInfo(name = "in_bookshelf")
    val inBookshelf: Boolean = false,

    @ColumnInfo(name = "shelf_category")
    val shelfCategory: String = DEFAULT_SHELF_CATEGORY,

    @ColumnInfo(name = "cloud_synced")
    val cloudSynced: Boolean = false,

    @ColumnInfo(name = "has_unread_update")
    val hasUnreadUpdate: Boolean = false,

    @ColumnInfo(name = "shelf_sort_order")
    val shelfSortOrder: Int = 0,

    @ColumnInfo(name = "last_accessed_ms")
    val lastAccessedMs: Long = 0L,
) {
    companion object {
        const val DEFAULT_SHELF_CATEGORY = "默认书架"
    }
}
