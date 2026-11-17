package org.mewx.wenku8.v2.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.v2.R
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.BookmarkNoteEntity
import org.mewx.wenku8.v2.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingSessionEntity
import org.mewx.wenku8.v2.data.local.db.entity.VolumeEntity
import org.mewx.wenku8.v2.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.mewx.wenku8.v2.widget.ContinueReadingAppWidgetProvider
import org.mewx.wenku8.v2.widget.ContinueReadingWidgetSnapshot
import org.mewx.wenku8.v2.widget.buildContinueReadingRemoteViews
import org.mewx.wenku8.v2.widget.buildWidgetLaunchIntent
import org.mewx.wenku8.v2.widget.parseWidgetReaderLaunchRequest
import org.mewx.wenku8.v2.widget.resolveContinueReadingWidgetSnapshot
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class ReadingFootprintAndWidgetTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: Wenku8Database

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { runnable -> runnable.run() }
        database = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
    }

    @After
    fun tearDown() {
        runCatching { composeTestRule.waitForIdle() }
        database.close()
    }

    @Test
    fun streakAndHeatmapMath_keepsYesterdayStreakAlive_andQuantizes16WeekGrid() {
        // Pick a Wednesday (`2026-11-18`) so trailing Thu..Sun cells in week 15 are future days!
        val wednesday = LocalDate.of(2026, 11, 18)
        val todayDay = wednesday.toEpochDay()

        val sessions = listOf(
            // Older 5-day historical streak 30 days ago
            ReadingSessionEntity(aid = 1, cid = 10, dateEpochDay = todayDay - 34, durationSeconds = 600, charactersRead = 3_000),
            ReadingSessionEntity(aid = 1, cid = 10, dateEpochDay = todayDay - 33, durationSeconds = 950, charactersRead = 5_500),
            ReadingSessionEntity(aid = 1, cid = 10, dateEpochDay = todayDay - 32, durationSeconds = 1_900, charactersRead = 13_000),
            ReadingSessionEntity(aid = 1, cid = 10, dateEpochDay = todayDay - 31, durationSeconds = 3_700, charactersRead = 25_000),
            ReadingSessionEntity(aid = 1, cid = 10, dateEpochDay = todayDay - 30, durationSeconds = 1_200, charactersRead = 7_000),
            // Current 3-day streak ending YESTERDAY (today has 0s so far — streak must stay alive = 3!)
            ReadingSessionEntity(aid = 2, cid = 20, dateEpochDay = todayDay - 3, durationSeconds = 800, charactersRead = 4_000),
            ReadingSessionEntity(aid = 2, cid = 20, dateEpochDay = todayDay - 2, durationSeconds = 1_000, charactersRead = 6_000),
            ReadingSessionEntity(aid = 2, cid = 20, dateEpochDay = todayDay - 2, durationSeconds = 900, charactersRead = 6_500), // sums to 1900s -> level 3
            ReadingSessionEntity(aid = 2, cid = 21, dateEpochDay = todayDay - 1, durationSeconds = 3_800, charactersRead = 26_000), // level 4
        )

        val stats = computeReadingActivityStats(
            sessions = sessions,
            todayEpochDay = todayDay,
            weeksCount = 16,
        )

        assertEquals(3, stats.currentStreakDays)
        assertEquals(5, stats.longestStreakDays)
        assertEquals(8, stats.activeDaysCount)
        assertEquals(16, stats.heatmapWeeks.size)
        assertTrue(stats.heatmapWeeks.all { it.size == 7 })
        assertTrue(stats.averageSpeedCharsPerMinute > 300)

        // Verify intensity levels & future-day flags in the rightmost week
        val currentWeek = stats.heatmapWeeks.last()
        // Monday (`todayDay - 2` -> 1900s -> level 3)
        assertEquals(3, currentWeek[0].intensityLevel)
        assertEquals(false, currentWeek[0].isFutureDay)
        // Tuesday (`todayDay - 1` -> 3800s -> level 4)
        assertEquals(4, currentWeek[1].intensityLevel)
        // Wednesday (`todayDay` -> 0s -> level 0, not future)
        assertEquals(0, currentWeek[2].intensityLevel)
        assertEquals(false, currentWeek[2].isFutureDay)
        // Thursday (`todayDay + 1` -> future day!)
        assertTrue(currentWeek[3].isFutureDay)

        // Verify shareable poster bitmap renders cleanly at 1080x1350
        val poster = renderReadingPosterBitmap(
            stats = stats,
            bookmarkCount = 12,
            generatedDateText = "2026-11-18",
        )
        assertEquals(1080, poster.width)
        assertEquals(1350, poster.height)
        poster.recycle()
    }

    @Test
    fun readingFootprintScreen_displaysHeatmap_resumesHistory_andSearchesDeletesBookmarks() {
        val todayDay = LocalDate.of(2026, 11, 17).toEpochDay()
        runBlocking {
            database.novelDao().upsertNovels(
                listOf(
                    NovelEntity(
                        aid = 1306,
                        title = "奇诺之旅",
                        author = "时雨泽惠一",
                        inBookshelf = true,
                        metaSyncedAtMs = 1_700_000_000_000L,
                    ),
                    NovelEntity(
                        aid = 2900,
                        title = "败北女角太多了！",
                        author = "雨森焚火",
                        inBookshelf = true,
                        metaSyncedAtMs = 1_700_000_000_000L,
                    ),
                ),
            )
            database.tocDao().upsertVolumes(
                listOf(
                    VolumeEntity(aid = 1306, vid = 101, volumeTitle = "第一卷", volumeOrder = 0),
                    VolumeEntity(aid = 2900, vid = 201, volumeTitle = "第一卷", volumeOrder = 0),
                ),
            )
            database.readingSessionDao().insertSession(
                ReadingSessionEntity(
                    aid = 1306,
                    cid = 1001,
                    dateEpochDay = todayDay - 1,
                    durationSeconds = 1_500,
                    charactersRead = 9_200,
                ),
            )
            database.readingSessionDao().insertSession(
                ReadingSessionEntity(
                    aid = 1306,
                    cid = 1002,
                    dateEpochDay = todayDay,
                    durationSeconds = 2_400,
                    charactersRead = 15_600,
                ),
            )
            database.readingProgressDao().upsertProgress(
                ReadingProgressEntity(
                    aid = 1306,
                    vid = 101,
                    cid = 1002,
                    volumeTitle = "第一卷",
                    chapterTitle = "第二话 多数决之国",
                    paragraphIndex = 14,
                    charOffset = 6,
                    progressPermille = 640,
                    updatedAtMs = 1_700_050_000_000L,
                ),
            )
            database.bookmarkNoteDao().upsertBookmarkNote(
                BookmarkNoteEntity(
                    id = 501L,
                    aid = 1306,
                    vid = 101,
                    cid = 1001,
                    chapterTitle = "第一话 懂得人类痛楚之国",
                    startParagraph = 8,
                    startChar = 2,
                    endParagraph = 8,
                    endChar = 24,
                    snippetText = "世界并不美丽，但也因此美丽无比。",
                    noteMarkdown = "经典台词",
                    createdAtMs = 1_700_010_000_000L,
                ),
            )
            database.bookmarkNoteDao().upsertBookmarkNote(
                BookmarkNoteEntity(
                    id = 502L,
                    aid = 2900,
                    vid = 201,
                    cid = 2001,
                    chapterTitle = "第一败 青梅竹马的败北",
                    startParagraph = 3,
                    startChar = 0,
                    endParagraph = 3,
                    endChar = 18,
                    snippetText = "八奈见杏菜今天也在吃素面。",
                    createdAtMs = 1_700_020_000_000L,
                ),
            )
        }

        val capturedLaunches = mutableListOf<ReaderLaunchRequest>()

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                ReadingFootprintScreen(
                    database = database,
                    initialTab = ReadingFootprintTab.STATS,
                    todayEpochDay = todayDay,
                    onNavigateBack = {},
                    onOpenReader = { req -> capturedLaunches.add(req) },
                    onOpenNovelDetail = {},
                )
            }
        }

        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            composeTestRule
                .onAllNodesWithTag(ReadingFootprintTestTags.CURRENT_STREAK_VALUE)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Verify 2-day streak & tap yesterday's heatmap cell to inspect its readout!
        composeTestRule.onNodeWithTag(ReadingFootprintTestTags.CURRENT_STREAK_VALUE)
            .assertTextEquals("2 天")
        composeTestRule.onNodeWithTag("${ReadingFootprintTestTags.HEATMAP_CELL_PREFIX}${todayDay - 1}")
            .performScrollTo()
            .performClick()
        composeTestRule.onNodeWithTag(ReadingFootprintTestTags.HEATMAP_SELECTED_LABEL)
            .assertTextEquals("2026-11-16 · 阅读 25 分钟 · 9200 字")

        // Switch to HISTORY tab and click Continue Reading
        composeTestRule.onNodeWithTag(ReadingFootprintTestTags.TAB_HISTORY).performClick()
        composeTestRule.onNodeWithTag("${ReadingFootprintTestTags.HISTORY_RESUME_PREFIX}1306")
            .assertIsDisplayed()
            .performClick()

        assertEquals(1, capturedLaunches.size)
        assertEquals(1306, capturedLaunches.first().aid)
        assertEquals(1002, capturedLaunches.first().cid)
        assertEquals(14, capturedLaunches.first().initialParagraphIndex)
        assertEquals(6, capturedLaunches.first().initialCharOffset)

        // Switch to BOOKMARKS tab, search for "美丽无比", jump to anchor, then delete it!
        composeTestRule.onNodeWithTag(ReadingFootprintTestTags.TAB_BOOKMARKS).performClick()
        composeTestRule.onNodeWithTag(ReadingFootprintTestTags.BOOKMARK_SEARCH_INPUT)
            .performTextInput("美丽无比")

        composeTestRule.onNodeWithTag("${ReadingFootprintTestTags.BOOKMARK_ITEM_PREFIX}501")
            .assertIsDisplayed()
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag("${ReadingFootprintTestTags.BOOKMARK_ITEM_PREFIX}502")
                .fetchSemanticsNodes()
                .size,
        )

        composeTestRule.onNodeWithTag("${ReadingFootprintTestTags.BOOKMARK_JUMP_PREFIX}501")
            .performClick()
        assertEquals(2, capturedLaunches.size)
        assertEquals(1001, capturedLaunches[1].cid)
        assertEquals(8, capturedLaunches[1].initialParagraphIndex)
        assertEquals(2, capturedLaunches[1].initialCharOffset)

        composeTestRule.onNodeWithTag("${ReadingFootprintTestTags.BOOKMARK_DELETE_PREFIX}501")
            .performClick()
        composeTestRule.waitUntil(timeoutMillis = 5_000L) {
            runBlocking { database.bookmarkNoteDao().getAllBookmarkNotes().size == 1 }
        }
    }

    @Test
    fun continueReadingWidget_resolvesProgressFallback_buildsRemoteViews_andRoundTripsDeepLinkIntent() {
        val tempFilesDir = File(context.cacheDir, "widget_test_files").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            runBlocking {
                // 1. Empty database -> Empty snapshot + open bookshelf intent
                val emptySnap = resolveContinueReadingWidgetSnapshot(database, tempFilesDir)
                assertEquals(ContinueReadingWidgetSnapshot.Empty, emptySnap)
                val emptyIntent = buildWidgetLaunchIntent(context, emptySnap)
                assertNull(parseWidgetReaderLaunchRequest(emptyIntent))
                assertTrue(
                    emptyIntent.getBooleanExtra(
                        ContinueReadingAppWidgetProvider.EXTRA_WIDGET_OPEN_BOOKSHELF,
                        false,
                    ),
                )

                // 2. Bookshelf novel + TOC without reading progress -> falls back to Chapter 1 at 0%
                database.novelDao().upsertNovel(
                    NovelEntity(
                        aid = 4525,
                        title = "奥尔克森王国史",
                        author = "樽见京一郎",
                        inBookshelf = true,
                        shelfSortOrder = 1,
                    ),
                )
                database.tocDao().upsertVolumes(
                    listOf(VolumeEntity(aid = 4525, vid = 50, volumeTitle = "第一卷", volumeOrder = 0)),
                )
                database.tocDao().upsertChapters(
                    listOf(
                        ChapterEntity(
                            cid = 5001,
                            aid = 4525,
                            vid = 50,
                            chapterTitle = "第一章 国境的大河",
                            chapterOrder = 0,
                        ),
                    ),
                )

                val fallbackSnap = resolveContinueReadingWidgetSnapshot(database, tempFilesDir)
                    as ContinueReadingWidgetSnapshot.ActiveBook
                assertEquals(4525, fallbackSnap.aid)
                assertEquals(5001, fallbackSnap.cid)
                assertEquals(0, fallbackSnap.progressPermille)

                // 3. Saved reading progress + local cover JPEG -> ActiveBook with cover & exact anchor
                val coverFile = File(tempFilesDir, "saves/imgs/4525.jpg").apply {
                    parentFile?.mkdirs()
                }
                val tinyCover = Bitmap.createBitmap(120, 170, Bitmap.Config.ARGB_8888)
                FileOutputStream(coverFile).use { out ->
                    tinyCover.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                tinyCover.recycle()

                database.readingProgressDao().upsertProgress(
                    ReadingProgressEntity(
                        aid = 4525,
                        vid = 50,
                        cid = 5001,
                        volumeTitle = "第一卷",
                        chapterTitle = "第一章 国境的大河",
                        paragraphIndex = 22,
                        charOffset = 10,
                        progressPermille = 750,
                        updatedAtMs = 1_700_099_000_000L,
                    ),
                )

                val activeSnap = resolveContinueReadingWidgetSnapshot(database, tempFilesDir)
                    as ContinueReadingWidgetSnapshot.ActiveBook
                assertEquals(750, activeSnap.progressPermille)
                assertNotNull(activeSnap.localCoverFile)

                // Inflate RemoteViews into a real Android View hierarchy and verify rendered text & progress!
                val remoteViews = buildContinueReadingRemoteViews(context, activeSnap)
                val hostParent = FrameLayout(context)
                val inflatedView = remoteViews.apply(context, hostParent)
                val titleView = inflatedView.findViewById<TextView>(R.id.widget_novel_title)
                val chapterView = inflatedView.findViewById<TextView>(R.id.widget_chapter_subtitle)
                val progressBar = inflatedView.findViewById<ProgressBar>(R.id.widget_progress_bar)

                assertEquals("奥尔克森王国史", titleView.text.toString())
                assertEquals("第一卷 · 第一章 国境的大河 (75%)", chapterView.text.toString())
                assertEquals(750, progressBar.progress)

                // Round-trip deep-link Intent -> ReaderLaunchRequest
                val deepLinkIntent = buildWidgetLaunchIntent(context, activeSnap)
                val parsedRequest = parseWidgetReaderLaunchRequest(deepLinkIntent)
                assertNotNull(parsedRequest)
                assertEquals(4525, parsedRequest!!.aid)
                assertEquals(50, parsedRequest.vid)
                assertEquals(5001, parsedRequest.cid)
                assertEquals(22, parsedRequest.initialParagraphIndex)
                assertEquals(10, parsedRequest.initialCharOffset)
            }
        } finally {
            tempFilesDir.deleteRecursively()
        }
    }
}
