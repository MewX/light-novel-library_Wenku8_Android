package org.mewx.wenku8.v2.data.local.db.entity

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
    val isIllustrationChapter: Boolean = chapterTitle.trim() == ILLUSTRATION_CHAPTER_TITLE_SC ||
        chapterTitle.trim() == ILLUSTRATION_CHAPTER_TITLE_TC,
) {
    companion object {
        const val ILLUSTRATION_CHAPTER_TITLE_SC = "插图"
        const val ILLUSTRATION_CHAPTER_TITLE_TC = "插圖"
    }
}
