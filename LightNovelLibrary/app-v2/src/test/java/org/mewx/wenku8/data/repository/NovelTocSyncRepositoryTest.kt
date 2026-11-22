package org.mewx.wenku8.data.repository

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.BookmarkNoteEntity
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.util.DigestUtils
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NovelTocSyncRepositoryTest {

    private lateinit var database: Wenku8Database
    private lateinit var repository: NovelTocSyncRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .build()
        repository = NovelTocSyncRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun syncNovelIndexHandlesInitialWriteUnchangedDigestAndStaleResponses() = runTest {
        val aid = 1587
        val initialVolumes = listOf(
            ParsedVolumeToc(
                vid = 50001,
                volumeTitle = "第一卷",
                chapters = listOf(
                    ParsedChapterToc(cid = 50002, chapterTitle = "序章"),
                    ParsedChapterToc(cid = 50003, chapterTitle = "插图"),
                ),
            ),
        )

        val firstSync = repository.syncNovelIndexAtomically(
            aid = aid,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 1_000L,
            parsedVolumes = initialVolumes,
            fallbackNovelTitle = "测试小说",
        )
        assertTrue(firstSync is TocSyncResult.Updated)
        assertEquals(1L, firstSync.tocVersion)

        val storedChapters = database.tocDao().getChaptersForNovel(aid)
        assertEquals(2, storedChapters.size)
        assertEquals(false, storedChapters[0].isIllustrationChapter)
        assertEquals(true, storedChapters[1].isIllustrationChapter)

        // Identical TOC at t = 2000ms hits SHA-256 fast-path without bumping toc_version.
        val unchangedSync = repository.syncNovelIndexAtomically(
            aid = aid,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 2_000L,
            parsedVolumes = initialVolumes,
        )
        assertEquals(TocSyncResult.Unchanged(tocVersion = 1L), unchangedSync)
        assertEquals(2_000L, database.novelDao().getSyncHeader(aid)?.metaSyncedAtMs)

        // Out-of-order response started at t = 1500ms (< 2000ms) is rejected cleanly.
        val staleSync = repository.syncNovelIndexAtomically(
            aid = aid,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 1_500L,
            parsedVolumes = emptyList(),
        )
        assertEquals(TocSyncResult.SkippedStaleResponse(tocVersion = 1L), staleSync)
        assertEquals(2, database.tocDao().getChaptersForNovel(aid).size)
    }

    @Test
    fun volumeReorganizationPreservesOfflineChaptersBookmarksAndHealsReadingProgressVid() = runTest {
        val aid = 1306
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = aid,
                title = "向森之魔物献上花束",
                inBookshelf = true,
                shelfCategory = "最爱",
                shelfSortOrder = 2,
                latestSectionCid = 1002,
            ),
        )

        // Step 1: Initial serialized web volume (vid = 100) with chapters 1001 & 1002.
        repository.syncNovelIndexAtomically(
            aid = aid,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 10_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 100,
                    volumeTitle = "Web连载暂定卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 1001, chapterTitle = "第一话"),
                        ParsedChapterToc(cid = 1002, chapterTitle = "第二话 森林"),
                    ),
                ),
            ),
        )

        // User downloads chapter 1002 offline, reads to paragraph 17 char 9, and adds a highlight.
        val chapterBody = "第二话正文内容\r\n<!--image-->https://pic.wenku8.com/pictures/1/1306/1002/01.jpg<!--image-->"
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 1002,
                aid = aid,
                contentLang = "SC",
                contentText = chapterBody,
                byteLength = chapterBody.toByteArray().size,
                imageUrlsJson = "[\"https://pic.wenku8.com/pictures/1/1306/1002/01.jpg\"]",
                imageCount = 1,
                sha256Checksum = DigestUtils.sha256Hex(chapterBody),
                downloadedAtMs = 11_000L,
            ),
        )
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = aid,
                vid = 100,
                cid = 1002,
                volumeTitle = "Web连载暂定卷",
                chapterTitle = "第二话 森林",
                paragraphIndex = 17,
                charOffset = 9,
                progressPermille = 640,
                updatedAtMs = 12_000L,
            ),
        )
        database.bookmarkNoteDao().upsertBookmarkNote(
            BookmarkNoteEntity(
                aid = aid,
                vid = 100,
                cid = 1002,
                chapterTitle = "第二话 森林",
                startParagraph = 17,
                startChar = 0,
                endParagraph = 17,
                endChar = 6,
                snippetText = "第二话正文",
                noteMarkdown = "伏笔回收",
                styleType = BookmarkNoteEntity.STYLE_HIGHLIGHT,
                createdAtMs = 12_500L,
            ),
        )

        // Step 2: Upstream Wenku8 editors delete vid=100 and recompile cid=1001..1003 into Bunkobon vid=200!
        val reorgResult = repository.syncNovelIndexAtomically(
            aid = aid,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 20_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 200,
                    volumeTitle = "第一卷 正式文库版",
                    chapters = listOf(
                        ParsedChapterToc(cid = 1001, chapterTitle = "第一章"),
                        ParsedChapterToc(cid = 1002, chapterTitle = "第二章 森林的魔兽少女"),
                        ParsedChapterToc(cid = 1003, chapterTitle = "插图"),
                    ),
                ),
            ),
        )

        assertEquals(
            TocSyncResult.Updated(tocVersion = 2L, healedReadingProgressVid = 200),
            reorgResult,
        )

        // Old volume 100 is gone, replaced by volume 200.
        val volumesAfterReorg = database.tocDao().getVolumesForNovel(aid)
        assertEquals(listOf(200), volumesAfterReorg.map { it.vid })

        // Offline chapter 1002 text & bookmark survived 100% intact!
        assertEquals(chapterBody, database.chapterContentDao().loadChapterTextSafely(1002))
        val survivingNotes = database.bookmarkNoteDao().observeNotesForNovel(aid).first()
        assertEquals(1, survivingNotes.size)
        assertEquals("伏笔回收", survivingNotes.single().noteMarkdown)

        // Reading progress kept exact paragraph/char anchor AND auto-healed vid=200 + updated titles!
        val healedProgress = database.readingProgressDao().getProgressForNovel(aid)
        assertNotNull(healedProgress)
        assertEquals(200, healedProgress!!.vid)
        assertEquals("第一卷 正式文库版", healedProgress.volumeTitle)
        assertEquals("第二章 森林的魔兽少女", healedProgress.chapterTitle)
        assertEquals(17, healedProgress.paragraphIndex)
        assertEquals(9, healedProgress.charOffset)
    }

    @Test
    fun cursorWindowSafeReaderStitchesMultiMegabyteOmnibusChaptersAndMetadataMergePreservesShelf() = runTest {
        val aid = 2999
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = aid,
                title = "初始标题",
                inBookshelf = true,
                shelfCategory = "追更",
                shelfSortOrder = 4,
                latestSectionCid = 9001,
            ),
        )

        // Merge fresh server metadata with a newer latestSectionCid = 9005:
        // Shelf membership, category, and sort order must survive while hasUnreadUpdate turns true!
        database.novelDao().mergeServerMetadataPreservingShelfState(
            NovelEntity(
                aid = aid,
                title = "更新后完整标题",
                author = "川原砾",
                latestSectionCid = 9005,
                latestSectionName = "第四卷 终章",
                metaSyncedAtMs = 50_000L,
            ),
        )
        val mergedNovel = database.novelDao().getNovelByAid(aid)!!
        assertTrue(mergedNovel.inBookshelf)
        assertEquals("追更", mergedNovel.shelfCategory)
        assertEquals(4, mergedNovel.shelfSortOrder)
        assertTrue(mergedNovel.hasUnreadUpdate)
        assertEquals("川原砾", mergedNovel.author)

        // Build a 600,000-character (~1.8 MB UTF-8) CJK omnibus chapter (> 2x SAFE_SLICE_CHAR_COUNT)
        val paragraphUnit = "这是一段用于验证超长合集章节分片读取不会触发CursorWindow溢出的测试文本。" // 35 chars
        val repeatCount = 18_000 // 630,000 chars (> 256,000 * 2)
        val giantChapterText = buildString(paragraphUnit.length * repeatCount) {
            repeat(repeatCount) { index ->
                append(paragraphUnit).append(index % 10)
            }
        }

        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 9005,
                aid = aid,
                contentLang = "SC",
                contentText = giantChapterText,
                byteLength = giantChapterText.toByteArray(Charsets.UTF_8).size,
                sha256Checksum = DigestUtils.sha256Hex(giantChapterText),
                downloadedAtMs = 55_000L,
            ),
        )

        val stitchedText = database.chapterContentDao().loadChapterTextSafely(9005)
        assertEquals(giantChapterText.length, stitchedText?.length)
        assertEquals(giantChapterText, stitchedText)
    }
}
