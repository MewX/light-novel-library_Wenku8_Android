package org.mewx.wenku8.v2.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import org.mewx.wenku8.v2.data.local.db.dao.BookmarkNoteDao
import org.mewx.wenku8.v2.data.local.db.dao.ChapterContentDao
import org.mewx.wenku8.v2.data.local.db.dao.NovelDao
import org.mewx.wenku8.v2.data.local.db.dao.ReadingProgressDao
import org.mewx.wenku8.v2.data.local.db.dao.ReadingSessionDao
import org.mewx.wenku8.v2.data.local.db.dao.ReviewDraftDao
import org.mewx.wenku8.v2.data.local.db.dao.SearchHistoryDao
import org.mewx.wenku8.v2.data.local.db.dao.TocDao
import org.mewx.wenku8.v2.data.local.db.entity.BookmarkNoteEntity
import org.mewx.wenku8.v2.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.v2.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingSessionEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReviewDraftEntity
import org.mewx.wenku8.v2.data.local.db.entity.SearchHistoryEntity
import org.mewx.wenku8.v2.data.local.db.entity.VolumeEntity

/**
 * Unified Room SQLite database (`wenku8_v2.db`, WAL mode) replacing legacy `v1.31` fragmented
 * `.wk8` and thousands of per-chapter `.xml` files.
 */
@Database(
    entities = [
        NovelEntity::class,
        VolumeEntity::class,
        ChapterEntity::class,
        ChapterContentEntity::class,
        ReadingProgressEntity::class,
        BookmarkNoteEntity::class,
        ReadingSessionEntity::class,
        ReviewDraftEntity::class,
        SearchHistoryEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class Wenku8Database : RoomDatabase() {

    abstract fun novelDao(): NovelDao
    abstract fun tocDao(): TocDao
    abstract fun chapterContentDao(): ChapterContentDao
    abstract fun readingProgressDao(): ReadingProgressDao
    abstract fun bookmarkNoteDao(): BookmarkNoteDao
    abstract fun readingSessionDao(): ReadingSessionDao
    abstract fun reviewDraftDao(): ReviewDraftDao
    abstract fun searchHistoryDao(): SearchHistoryDao

    companion object {
        const val DATABASE_FILE_NAME = "wenku8_v2.db"

        fun createWalDatabase(context: Context): Wenku8Database {
            return Room.databaseBuilder(
                context.applicationContext,
                Wenku8Database::class.java,
                DATABASE_FILE_NAME,
            )
                .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
                .build()
        }
    }
}
