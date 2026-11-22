package org.mewx.wenku8.data.local.db.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Offline chapter body record (`chapter_contents` table).
 *
 * Architectural rules (`docs/designs/v2/09-room-database-and-backup-archive-design.md` §3 & §4.3):
 * 1. Foreign key points strictly to [NovelEntity] (`aid`), **NOT** `volumes` or `chapters`. When
 *    Wenku8 editors move serialized web chapters into a newly compiled Bunkobon volume (`vid` changes),
 *    updating `volumes`/`chapters` never deletes downloaded offline text!
 * 2. [contentText] must NEVER be selected in multi-row list queries because Android's SQLite
 *    `CursorWindow` caps a result window at `2 MB`. Always read [contentText] one `cid` at a time
 *    via `ChapterContentDao`'s `SUBSTR` chunk reader.
 * 3. [imageUrlsJson] pre-indexes every `<!--image-->` URL at download/import time so Deep Cache
 *    Clean (`深度清理`) computes referenced illustrations in milliseconds without scanning XML text.
 */
@Entity(
    tableName = "chapter_contents",
    foreignKeys = [
        ForeignKey(
            entity = NovelEntity::class,
            parentColumns = ["aid"],
            childColumns = ["aid"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["aid"]),
    ],
)
data class ChapterContentEntity(
    @PrimaryKey
    @ColumnInfo(name = "cid")
    val cid: Int,

    @ColumnInfo(name = "aid")
    val aid: Int,

    @ColumnInfo(name = "content_lang")
    val contentLang: String,

    @ColumnInfo(name = "content_text")
    val contentText: String,

    @ColumnInfo(name = "byte_length")
    val byteLength: Int,

    @ColumnInfo(name = "image_urls_json")
    val imageUrlsJson: String = "[]",

    @ColumnInfo(name = "image_count")
    val imageCount: Int = 0,

    @ColumnInfo(name = "sha256_checksum")
    val sha256Checksum: String,

    @ColumnInfo(name = "downloaded_at_ms")
    val downloadedAtMs: Long,
)
