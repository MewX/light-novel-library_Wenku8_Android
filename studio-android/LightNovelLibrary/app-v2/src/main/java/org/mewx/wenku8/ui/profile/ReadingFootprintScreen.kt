package org.mewx.wenku8.ui.profile

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.R
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.dao.BookmarkNoteWithNovel
import org.mewx.wenku8.data.local.db.dao.ReadingHistoryWithNovel
import org.mewx.wenku8.data.local.db.entity.ReadingSessionEntity
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import org.mewx.wenku8.ui.theme.wenku8SharedNovelCover
import org.mewx.wenku8.ui.theme.wenku8SharedNovelTitle
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.max

/** Entry tab selection when opening [ReadingFootprintScreen] from Profile. */
enum class ReadingFootprintTab {
    STATS,
    HISTORY,
    BOOKMARKS,
}

/** Semantic UI test tags for Robolectric & Compose UI verification of [ReadingFootprintScreen]. */
object ReadingFootprintTestTags {
    const val SCREEN = "reading_footprint_screen"
    const val SHARE_POSTER_BUTTON = "reading_footprint_share_poster_button"
    const val TAB_STATS = "reading_footprint_tab_stats"
    const val TAB_HISTORY = "reading_footprint_tab_history"
    const val TAB_BOOKMARKS = "reading_footprint_tab_bookmarks"
    const val CURRENT_STREAK_VALUE = "reading_footprint_current_streak"
    const val LONGEST_STREAK_VALUE = "reading_footprint_longest_streak"
    const val HEATMAP_GRID = "reading_footprint_heatmap_grid"
    const val HEATMAP_CELL_PREFIX = "reading_footprint_cell_"
    const val HEATMAP_SELECTED_LABEL = "reading_footprint_selected_day_label"
    const val HISTORY_ITEM_PREFIX = "reading_footprint_history_"
    const val HISTORY_RESUME_PREFIX = "reading_footprint_resume_"
    const val BOOKMARK_SEARCH_INPUT = "reading_footprint_bookmark_search"
    const val BOOKMARK_ITEM_PREFIX = "reading_footprint_bookmark_"
    const val BOOKMARK_JUMP_PREFIX = "reading_footprint_bookmark_jump_"
    const val BOOKMARK_DELETE_PREFIX = "reading_footprint_bookmark_delete_"
}

/**
 * Single calendar day cell inside the `16 × 7` reading activity heatmap.
 *
 * @property epochDay Local `LocalDate.toEpochDay()` identifier.
 * @property durationSeconds Aggregated reading duration for that calendar day.
 * @property charactersRead Aggregated characters read for that calendar day.
 * @property intensityLevel Quantized bucket `0..4` (`0` = none, `1` = `<15m`, `2` = `15..29m`,
 *   `3` = `30..59m`, `4` = `>=60m`).
 * @property isFutureDay `true` for trailing weekday slots after `todayEpochDay` in the rightmost column.
 */
data class HeatmapDayCell(
    val epochDay: Long,
    val durationSeconds: Long,
    val charactersRead: Long,
    val intensityLevel: Int,
    val isFutureDay: Boolean,
)

/**
 * Deterministic reading statistics & calendar heatmap summary computed from `reading_sessions`.
 */
data class ReadingActivityStats(
    val currentStreakDays: Int,
    val longestStreakDays: Int,
    val activeDaysCount: Int,
    val totalReadingSeconds: Long,
    val totalCharactersRead: Long,
    val averageSpeedCharsPerMinute: Int,
    val heatmapWeeks: List<List<HeatmapDayCell>>,
)

/**
 * Aggregates raw [ReadingSessionEntity] rows into streaks, cumulative totals, reading speed, and
 * an ISO Monday-aligned `weeksCount × 7` calendar heatmap ending on the week containing [todayEpochDay].
 */
fun computeReadingActivityStats(
    sessions: List<ReadingSessionEntity>,
    todayEpochDay: Long = LocalDate.now().toEpochDay(),
    weeksCount: Int = 16,
): ReadingActivityStats {
    val safeWeeks = weeksCount.coerceIn(4, 52)
    val durationByDay = HashMap<Long, Long>()
    val charsByDay = HashMap<Long, Long>()
    var totalSeconds = 0L
    var totalChars = 0L

    for (session in sessions) {
        val dur = session.durationSeconds.coerceAtLeast(0).toLong()
        val ch = session.charactersRead.coerceAtLeast(0).toLong()
        if (dur == 0L && ch == 0L) continue
        totalSeconds += dur
        totalChars += ch
        durationByDay[session.dateEpochDay] = (durationByDay[session.dateEpochDay] ?: 0L) + dur
        charsByDay[session.dateEpochDay] = (charsByDay[session.dateEpochDay] ?: 0L) + ch
    }

    val sortedActiveDays = durationByDay.keys
        .union(charsByDay.keys)
        .filter { day -> (durationByDay[day] ?: 0L) > 0L || (charsByDay[day] ?: 0L) > 0L }
        .sorted()

    // Longest streak across all recorded days
    var longestStreak = 0
    var runLength = 0
    var prevDay: Long? = null
    for (day in sortedActiveDays) {
        if (prevDay != null && day == prevDay + 1L) {
            runLength += 1
        } else {
            runLength = 1
        }
        if (runLength > longestStreak) {
            longestStreak = runLength
        }
        prevDay = day
    }

    // Current streak: stays alive if the user read today OR read yesterday (hasn't broken streak yet today)
    val activeDaySet = sortedActiveDays.toHashSet()
    var currentStreak = 0
    val streakAnchorDay = when {
        activeDaySet.contains(todayEpochDay) -> todayEpochDay
        activeDaySet.contains(todayEpochDay - 1L) -> todayEpochDay - 1L
        else -> null
    }
    if (streakAnchorDay != null) {
        var cursor = streakAnchorDay
        while (activeDaySet.contains(cursor)) {
            currentStreak += 1
            cursor -= 1L
        }
    }

    // Align rightmost column to the Monday..Sunday week containing `todayEpochDay`
    val todayDate = LocalDate.ofEpochDay(todayEpochDay)
    val todayIsoDow = todayDate.dayOfWeek.value // 1 (Monday) .. 7 (Sunday)
    val currentWeekMondayEpochDay = todayEpochDay - (todayIsoDow - 1).toLong()
    val firstWeekMondayEpochDay = currentWeekMondayEpochDay - ((safeWeeks - 1).toLong() * 7L)

    val weeks = ArrayList<List<HeatmapDayCell>>(safeWeeks)
    for (weekIdx in 0 until safeWeeks) {
        val weekCells = ArrayList<HeatmapDayCell>(7)
        for (dowOffset in 0 until 7) {
            val cellDay = firstWeekMondayEpochDay + (weekIdx * 7L) + dowOffset.toLong()
            val isFuture = cellDay > todayEpochDay
            val daySeconds = if (isFuture) 0L else (durationByDay[cellDay] ?: 0L)
            val dayChars = if (isFuture) 0L else (charsByDay[cellDay] ?: 0L)
            val level = quantizeReadingIntensity(daySeconds, dayChars)
            weekCells.add(
                HeatmapDayCell(
                    epochDay = cellDay,
                    durationSeconds = daySeconds,
                    charactersRead = dayChars,
                    intensityLevel = if (isFuture) 0 else level,
                    isFutureDay = isFuture,
                ),
            )
        }
        weeks.add(weekCells)
    }

    val speedCpm = if (totalSeconds >= 10L && totalChars > 0L) {
        ((totalChars * 60L) / totalSeconds).coerceIn(1L, 99_999L).toInt()
    } else {
        0
    }

    return ReadingActivityStats(
        currentStreakDays = currentStreak,
        longestStreakDays = longestStreak,
        activeDaysCount = sortedActiveDays.size,
        totalReadingSeconds = totalSeconds,
        totalCharactersRead = totalChars,
        averageSpeedCharsPerMinute = speedCpm,
        heatmapWeeks = weeks,
    )
}

internal fun quantizeReadingIntensity(durationSeconds: Long, charactersRead: Long): Int {
    if (durationSeconds <= 0L && charactersRead <= 0L) return 0
    return when {
        durationSeconds >= 3_600L || charactersRead >= 24_000L -> 4
        durationSeconds >= 1_800L || charactersRead >= 12_000L -> 3
        durationSeconds >= 900L || charactersRead >= 5_000L -> 2
        else -> 1
    }
}

/**
 * Renders a shareable `1080 × 1350` (`4:5`) Reading Footprint Poster PNG via native Android [Canvas].
 */
fun renderReadingPosterBitmap(
    stats: ReadingActivityStats,
    bookmarkCount: Int,
    generatedDateText: String,
): Bitmap {
    val width = 1080
    val height = 1350
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Warm archival gradient background
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height.toFloat(),
            intArrayOf(0xFFFDF8F0.toInt(), 0xFFF3E6D3.toInt()),
            null,
            Shader.TileMode.CLAMP,
        )
    }
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

    // Card frame
    val cardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFCF7.toInt()
    }
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x338A4F28
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    val frameRect = RectF(56f, 56f, width - 56f, height - 56f)
    canvas.drawRoundRect(frameRect, 48f, 48f, cardPaint)
    canvas.drawRoundRect(frameRect, 48f, 48f, borderPaint)

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF241A12.toInt()
        textSize = 54f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    val subtitlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF8A4F28.toInt()
        textSize = 28f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val metricValuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF241A12.toInt()
        textSize = 52f
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    val metricLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF6E5D4F.toInt()
        textSize = 26f
    }

    canvas.drawText("轻小说文库 · 阅读足迹", 112f, 168f, titlePaint)
    canvas.drawText("WENKU8 ARCHIVAL READING REPORT · $generatedDateText", 112f, 220f, subtitlePaint)

    // 2x2 Metric Grid
    val totalMinutes = stats.totalReadingSeconds / 60L
    val hoursPart = totalMinutes / 60L
    val minsPart = totalMinutes % 60L
    val durationText = if (hoursPart > 0L) "${hoursPart}h ${minsPart}m" else "${minsPart}m"
    val metrics = listOf(
        "${stats.currentStreakDays} 天" to "当前连续阅读（最长 ${stats.longestStreakDays} 天）",
        durationText to "累计沉浸阅读时长",
        formatPosterCompactCount(stats.totalCharactersRead) to "累计阅读字数",
        "${stats.activeDaysCount} 天 / $bookmarkCount 条" to "活跃天数 · 书签摘录",
    )

    val tileBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFF6EFE4.toInt()
    }
    metrics.forEachIndexed { index, (valStr, labelStr) ->
        val col = index % 2
        val row = index / 2
        val left = 112f + col * 438f
        val top = 280f + row * 210f
        val rect = RectF(left, top, left + 418f, top + 186f)
        canvas.drawRoundRect(rect, 28f, 28f, tileBgPaint)
        canvas.drawText(valStr, left + 32f, top + 92f, metricValuePaint)
        canvas.drawText(labelStr, left + 32f, top + 144f, metricLabelPaint)
    }

    // Mini 16x7 Heatmap
    canvas.drawText("近 ${stats.heatmapWeeks.size} 周阅读活跃热力图", 112f, 760f, subtitlePaint)
    val cellPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    val cellSize = 42f
    val cellGap = 11f
    val startX = 112f
    val startY = 804f
    stats.heatmapWeeks.forEachIndexed { colIdx, week ->
        week.forEachIndexed { rowIdx, cell ->
            cellPaint.color = when {
                cell.isFutureDay -> 0x108A4F28
                cell.intensityLevel == 0 -> 0xFFEBE1D3.toInt()
                cell.intensityLevel == 1 -> 0xFFD9B28F.toInt()
                cell.intensityLevel == 2 -> 0xFFC28758.toInt()
                cell.intensityLevel == 3 -> 0xFFA46636.toInt()
                else -> 0xFF7D4318.toInt()
            }
            val cx = startX + colIdx * (cellSize + cellGap)
            val cy = startY + rowIdx * (cellSize + cellGap)
            canvas.drawRoundRect(
                RectF(cx, cy, cx + cellSize, cy + cellSize),
                10f,
                10f,
                cellPaint,
            )
        }
    }

    canvas.drawText(
        "Wenku8 Android v${BuildConfig.VERSION_NAME} · Material 3 Expressive",
        112f,
        height - 104f,
        metricLabelPaint,
    )
    return bitmap
}

private fun formatPosterCompactCount(count: Long): String {
    val safe = count.coerceAtLeast(0L)
    return when {
        safe >= 10_000L -> String.format(Locale.US, "%.1f 万", safe / 10_000.0)
        else -> safe.toString()
    }
}

private suspend fun exportAndShareReadingPoster(
    context: Context,
    stats: ReadingActivityStats,
    bookmarkCount: Int,
) {
    val shareFile = withContext(Dispatchers.IO) {
        val shareDir = File(context.cacheDir, "artbook_share").apply { mkdirs() }
        // Opportunistically clean files older than 24h
        val cutoff = System.currentTimeMillis() - 86_400_000L
        shareDir.listFiles()?.forEach { old ->
            if (old.lastModified() < cutoff) {
                old.delete()
            }
        }
        val dateStr = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)
        val outFile = File(shareDir, "wenku8_reading_footprint_$dateStr.png")
        val bitmap = renderReadingPosterBitmap(
            stats = stats,
            bookmarkCount = bookmarkCount,
            generatedDateText = dateStr,
        )
        try {
            FileOutputStream(outFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
        } finally {
            bitmap.recycle()
        }
        outFile
    }

    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.diagnostics.fileprovider",
        shareFile,
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.footprint_action_share_poster),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
}

/**
 * Full-screen Material 3 Expressive (`M3e`) Reading Statistics, Activity Heatmap, Reading History,
 * and Cross-Book Bookmarks & Notes Hub (`P5.4`).
 */
@Composable
fun ReadingFootprintScreen(
    database: Wenku8Database,
    initialTab: ReadingFootprintTab = ReadingFootprintTab.STATS,
    todayEpochDay: Long = remember { LocalDate.now().toEpochDay() },
    onNavigateBack: () -> Unit,
    onOpenReader: (ReaderLaunchRequest) -> Unit,
    onOpenNovelDetail: (DiscoveryNovelCardItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedTab by rememberSaveable(initialTab) { mutableStateOf(initialTab) }
    val sessions by database.readingSessionDao()
        .observeAllSessions()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val recentHistory by database.readingProgressDao()
        .observeRecentHistoryWithNovels(limit = 100)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val allBookmarks by database.bookmarkNoteDao()
        .observeAllBookmarksWithNovels()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val stats = remember(sessions, todayEpochDay) {
        computeReadingActivityStats(
            sessions = sessions,
            todayEpochDay = todayEpochDay,
            weeksCount = 16,
        )
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(ReadingFootprintTestTags.SCREEN),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.footprint_screen_title),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        val backLabel = stringResource(R.string.action_back)
                        TooltipIconButton(
                            tooltipText = backLabel,
                            onClick = onNavigateBack,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = backLabel,
                            )
                        }
                    },
                    actions = {
                        val sharePosterLabel = stringResource(R.string.footprint_action_share_poster)
                        TooltipIconButton(
                            tooltipText = sharePosterLabel,
                            onClick = {
                                coroutineScope.launch {
                                    runCatching {
                                        exportAndShareReadingPoster(
                                            context = context,
                                            stats = stats,
                                            bookmarkCount = allBookmarks.size,
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.testTag(ReadingFootprintTestTags.SHARE_POSTER_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = sharePosterLabel,
                            )
                        }
                    },
                )

                val fontScale = LocalDensity.current.fontScale
                val tabsContent: @Composable () -> Unit = {
                    Tab(
                        selected = selectedTab == ReadingFootprintTab.STATS,
                        onClick = { selectedTab = ReadingFootprintTab.STATS },
                        icon = { Icon(imageVector = Icons.Outlined.Insights, contentDescription = null) },
                        text = {
                            Text(
                                text = stringResource(R.string.footprint_tab_stats),
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        modifier = Modifier.testTag(ReadingFootprintTestTags.TAB_STATS),
                    )
                    Tab(
                        selected = selectedTab == ReadingFootprintTab.HISTORY,
                        onClick = { selectedTab = ReadingFootprintTab.HISTORY },
                        icon = { Icon(imageVector = Icons.Outlined.History, contentDescription = null) },
                        text = {
                            Text(
                                text = "${stringResource(R.string.footprint_tab_history)} (${recentHistory.size})",
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        modifier = Modifier.testTag(ReadingFootprintTestTags.TAB_HISTORY),
                    )
                    Tab(
                        selected = selectedTab == ReadingFootprintTab.BOOKMARKS,
                        onClick = { selectedTab = ReadingFootprintTab.BOOKMARKS },
                        icon = { Icon(imageVector = Icons.Outlined.BookmarkBorder, contentDescription = null) },
                        text = {
                            Text(
                                text = "${stringResource(R.string.footprint_tab_bookmarks)} (${allBookmarks.size})",
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        modifier = Modifier.testTag(ReadingFootprintTestTags.TAB_BOOKMARKS),
                    )
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    if (maxWidth < 400.dp || fontScale > 1.05f) {
                        PrimaryScrollableTabRow(
                            selectedTabIndex = selectedTab.ordinal,
                            edgePadding = 16.dp,
                            tabs = tabsContent,
                        )
                    } else {
                        PrimaryTabRow(
                            selectedTabIndex = selectedTab.ordinal,
                            tabs = tabsContent,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        when (selectedTab) {
            ReadingFootprintTab.STATS -> {
                ReadingStatsHeatmapTabContent(
                    stats = stats,
                    todayEpochDay = todayEpochDay,
                    bookmarkCount = allBookmarks.size,
                    onSharePoster = {
                        coroutineScope.launch {
                            runCatching {
                                exportAndShareReadingPoster(
                                    context = context,
                                    stats = stats,
                                    bookmarkCount = allBookmarks.size,
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            ReadingFootprintTab.HISTORY -> {
                ReadingHistoryTabContent(
                    historyItems = recentHistory,
                    onOpenReader = onOpenReader,
                    onOpenNovelDetail = onOpenNovelDetail,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }

            ReadingFootprintTab.BOOKMARKS -> {
                CrossBookBookmarksHubTabContent(
                    bookmarks = allBookmarks,
                    onJumpToBookmark = { item ->
                        onOpenReader(
                            ReaderLaunchRequest(
                                aid = item.aid,
                                novelTitle = item.novelTitle.ifBlank { "AID #${item.aid}" },
                                vid = item.vid,
                                volumeTitle = item.volumeTitle,
                                cid = item.cid,
                                chapterTitle = item.chapterTitle,
                                initialParagraphIndex = item.startParagraph.coerceAtLeast(0),
                                initialCharOffset = item.startChar.coerceAtLeast(0),
                            ),
                        )
                    },
                    onDeleteBookmark = { bookmarkId ->
                        coroutineScope.launch {
                            database.bookmarkNoteDao().deleteBookmarkNoteById(bookmarkId)
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                )
            }
        }
    }
}

@Composable
private fun ReadingStatsHeatmapTabContent(
    stats: ReadingActivityStats,
    todayEpochDay: Long,
    bookmarkCount: Int,
    onSharePoster: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEinkMode = LocalEinkMode.current
    var selectedCellEpochDay by rememberSaveable(todayEpochDay) { mutableLongStateOf(todayEpochDay) }

    val selectedCell = remember(stats, selectedCellEpochDay) {
        stats.heatmapWeeks
            .asSequence()
            .flatten()
            .firstOrNull { it.epochDay == selectedCellEpochDay }
    }

    val totalMinutes = (stats.totalReadingSeconds / 60L).coerceAtLeast(0L)

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Streak & Cumulative Overview Card
        item(key = "stats_streak_hero_card") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkHairlineBorder(MaterialTheme.shapes.large),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier.einkHairlineBorder(MaterialTheme.shapes.medium),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.LocalFireDepartment,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.footprint_streak_current_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.footprint_streak_days_format,
                                    stats.currentStreakDays,
                                ),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag(ReadingFootprintTestTags.CURRENT_STREAK_VALUE),
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = stringResource(R.string.footprint_streak_longest_label),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    R.string.footprint_streak_days_format,
                                    stats.longestStreakDays,
                                ),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag(ReadingFootprintTestTags.LONGEST_STREAK_VALUE),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatsMetricBox(
                            valueText = stringResource(R.string.profile_footprint_minutes_format, totalMinutes),
                            labelText = stringResource(R.string.profile_footprint_minutes_label),
                            modifier = Modifier.weight(1f),
                        )
                        StatsMetricBox(
                            valueText = formatPosterCompactCount(stats.totalCharactersRead),
                            labelText = stringResource(R.string.profile_footprint_chars_label),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        StatsMetricBox(
                            valueText = stringResource(
                                R.string.footprint_streak_days_format,
                                stats.activeDaysCount,
                            ),
                            labelText = stringResource(R.string.footprint_active_days_label),
                            modifier = Modifier.weight(1f),
                        )
                        StatsMetricBox(
                            valueText = if (stats.averageSpeedCharsPerMinute > 0) {
                                stringResource(
                                    R.string.footprint_speed_cpm_format,
                                    stats.averageSpeedCharsPerMinute,
                                )
                            } else {
                                "$bookmarkCount"
                            },
                            labelText = if (stats.averageSpeedCharsPerMinute > 0) {
                                stringResource(R.string.footprint_speed_label)
                            } else {
                                stringResource(R.string.profile_footprint_notes_label)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // 2. Interactive 16-Week Daily Reading Activity Heatmap
        item(key = "stats_calendar_heatmap_card") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkHairlineBorder(MaterialTheme.shapes.large),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = stringResource(R.string.footprint_heatmap_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState(initial = Int.MAX_VALUE))
                            .testTag(ReadingFootprintTestTags.HEATMAP_GRID),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        // Weekday legend column (Mon..Sun)
                        val weekdayLabels = listOf("一", "", "三", "", "五", "", "日")
                        Column(
                            verticalArrangement = Arrangement.spacedBy(5.dp),
                            modifier = Modifier.padding(end = 4.dp),
                        ) {
                            weekdayLabels.forEach { label ->
                                Box(
                                    modifier = Modifier.size(16.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (label.isNotEmpty()) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        stats.heatmapWeeks.forEach { weekColumn ->
                            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                weekColumn.forEach { cell ->
                                    val isSelected = cell.epochDay == selectedCellEpochDay
                                    val cellColor = heatmapCellColor(
                                        level = cell.intensityLevel,
                                        isFutureDay = cell.isFutureDay,
                                        isEinkMode = isEinkMode,
                                    )
                                    val cellShape = RoundedCornerShape(4.dp)
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(cellShape)
                                            .background(cellColor)
                                            .then(
                                                if (isSelected) {
                                                    Modifier.border(
                                                        width = 1.5.dp,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        shape = cellShape,
                                                    )
                                                } else if (isEinkMode && !cell.isFutureDay) {
                                                    Modifier.border(
                                                        width = 0.75.dp,
                                                        color = MaterialTheme.colorScheme.outline,
                                                        shape = cellShape,
                                                    )
                                                } else {
                                                    Modifier
                                                },
                                            )
                                            .clickable(enabled = !cell.isFutureDay) {
                                                selectedCellEpochDay = cell.epochDay
                                            }
                                            .testTag("${ReadingFootprintTestTags.HEATMAP_CELL_PREFIX}${cell.epochDay}"),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Legend + Selected Day Readout
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        val inspected = selectedCell
                        val readoutText = if (inspected != null) {
                            val dateStr = LocalDate.ofEpochDay(inspected.epochDay)
                                .format(DateTimeFormatter.ISO_LOCAL_DATE)
                            val mins = max(
                                if (inspected.durationSeconds > 0L) 1L else 0L,
                                inspected.durationSeconds / 60L,
                            )
                            stringResource(
                                R.string.footprint_heatmap_selected_day_format,
                                dateStr,
                                mins,
                                formatPosterCompactCount(inspected.charactersRead),
                            )
                        } else {
                            ""
                        }
                        Text(
                            text = readoutText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .testTag(ReadingFootprintTestTags.HEATMAP_SELECTED_LABEL),
                        )

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.footprint_heatmap_legend_less),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            for (lvl in 0..4) {
                                Box(
                                    modifier = Modifier
                                        .size(11.dp)
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(
                                            heatmapCellColor(
                                                level = lvl,
                                                isFutureDay = false,
                                                isEinkMode = isEinkMode,
                                            ),
                                        ),
                                )
                            }
                            Text(
                                text = stringResource(R.string.footprint_heatmap_legend_more),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    FilledTonalButton(
                        onClick = onSharePoster,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.footprint_action_share_poster))
                    }
                }
            }
        }
    }
}

@Composable
private fun heatmapCellColor(
    level: Int,
    isFutureDay: Boolean,
    isEinkMode: Boolean,
): Color {
    if (isFutureDay) {
        return MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.25f)
    }
    if (isEinkMode) {
        val ink = MaterialTheme.colorScheme.onSurface
        return when (level) {
            0 -> MaterialTheme.colorScheme.surface
            1 -> ink.copy(alpha = 0.22f)
            2 -> ink.copy(alpha = 0.45f)
            3 -> ink.copy(alpha = 0.72f)
            else -> ink
        }
    }
    val primary = MaterialTheme.colorScheme.primary
    return when (level) {
        0 -> MaterialTheme.colorScheme.surfaceContainerHighest
        1 -> primary.copy(alpha = 0.28f)
        2 -> primary.copy(alpha = 0.50f)
        3 -> primary.copy(alpha = 0.74f)
        else -> primary
    }
}

@Composable
private fun StatsMetricBox(
    valueText: String,
    labelText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ReadingHistoryTabContent(
    historyItems: List<ReadingHistoryWithNovel>,
    onOpenReader: (ReaderLaunchRequest) -> Unit,
    onOpenNovelDetail: (DiscoveryNovelCardItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (historyItems.isEmpty()) {
        Box(
            modifier = modifier.padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.footprint_history_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val timeFormatter = remember {
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(
            items = historyItems,
            key = { "history_${it.aid}" },
        ) { item ->
            val coverUrl = "https://img.wenku8.com/image/${item.aid / 1000}/${item.aid}/${item.aid}s.jpg"
            val progressFraction = (item.progressPermille.coerceIn(0, 1000) / 1000f)
            val progressPercent = (item.progressPermille.coerceIn(0, 1000) / 10)
            val updatedText = remember(item.updatedAtMs) {
                runCatching {
                    Instant.ofEpochMilli(item.updatedAtMs)
                        .atZone(ZoneId.systemDefault())
                        .format(timeFormatter)
                }.getOrDefault("")
            }

            Card(
                onClick = {
                    onOpenNovelDetail(
                        DiscoveryNovelCardItem(
                            aid = item.aid,
                            title = item.novelTitle.ifBlank { "AID #${item.aid}" },
                            author = item.novelAuthor,
                            bookStatus = "",
                            lastUpdate = "",
                            tags = emptyList(),
                            introShort = "",
                            coverUrl = coverUrl,
                            inLocalBookshelf = item.inBookshelf,
                        ),
                    )
                },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkHairlineBorder(MaterialTheme.shapes.large)
                    .testTag("${ReadingFootprintTestTags.HISTORY_ITEM_PREFIX}${item.aid}"),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val historyCoverShape = MaterialTheme.shapes.small
                    AsyncImage(
                        model = WenkuImageRequests.rememberCoverImageRequest(
                            aid = item.aid,
                            coverUrl = coverUrl,
                            enableCrossfade = false,
                        ),
                        contentDescription = item.novelTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(width = 60.dp, height = 84.dp)
                            .wenku8SharedNovelCover(
                                origin = Wenku8SharedElementKeys.NOVEL_FOOTPRINT_HISTORY_ORIGIN,
                                aid = item.aid,
                                clipShape = historyCoverShape,
                            )
                            .clip(historyCoverShape)
                            .einkHairlineBorder(historyCoverShape),
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.novelTitle.ifBlank { "AID #${item.aid}" },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.wenku8SharedNovelTitle(
                                origin = Wenku8SharedElementKeys.NOVEL_FOOTPRINT_HISTORY_ORIGIN,
                                aid = item.aid,
                            ),
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = buildString {
                                if (item.volumeTitle.isNotBlank()) {
                                    append(item.volumeTitle)
                                    append(" · ")
                                }
                                append(item.chapterTitle.ifBlank { "第 ${item.cid} 章" })
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { progressFraction },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(5.dp),
                            )
                            Text(
                                text = "$progressPercent%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (updatedText.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = updatedText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            onOpenReader(
                                ReaderLaunchRequest(
                                    aid = item.aid,
                                    novelTitle = item.novelTitle.ifBlank { "AID #${item.aid}" },
                                    vid = item.vid,
                                    volumeTitle = item.volumeTitle,
                                    cid = item.cid,
                                    chapterTitle = item.chapterTitle,
                                    initialParagraphIndex = item.paragraphIndex.coerceAtLeast(0),
                                    initialCharOffset = item.charOffset.coerceAtLeast(0),
                                ),
                            )
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("${ReadingFootprintTestTags.HISTORY_RESUME_PREFIX}${item.aid}"),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.footprint_history_continue_action),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CrossBookBookmarksHubTabContent(
    bookmarks: List<BookmarkNoteWithNovel>,
    onJumpToBookmark: (BookmarkNoteWithNovel) -> Unit,
    onDeleteBookmark: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedNovelAidFilter by rememberSaveable { mutableStateOf(0) }

    val distinctNovels = remember(bookmarks) {
        bookmarks
            .groupBy { it.aid }
            .map { (aid, items) ->
                Triple(aid, items.first().novelTitle.ifBlank { "AID #$aid" }, items.size)
            }
    }

    val filteredBookmarks = remember(bookmarks, searchQuery, selectedNovelAidFilter) {
        val needle = searchQuery.trim().lowercase(Locale.ROOT)
        bookmarks.filter { bm ->
            val matchesNovel = selectedNovelAidFilter == 0 || bm.aid == selectedNovelAidFilter
            val matchesSearch = needle.isEmpty() ||
                bm.novelTitle.lowercase(Locale.ROOT).contains(needle) ||
                bm.chapterTitle.lowercase(Locale.ROOT).contains(needle) ||
                bm.snippetText.lowercase(Locale.ROOT).contains(needle) ||
                bm.noteMarkdown.lowercase(Locale.ROOT).contains(needle)
            matchesNovel && matchesSearch
        }
    }

    Column(modifier = modifier) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            singleLine = true,
            leadingIcon = {
                Icon(imageVector = Icons.Outlined.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    val clearLabel = stringResource(R.string.action_clear)
                    TooltipIconButton(
                        tooltipText = clearLabel,
                        onClick = { searchQuery = "" },
                    ) {
                        Icon(imageVector = Icons.Outlined.Close, contentDescription = clearLabel)
                    }
                }
            },
            placeholder = {
                Text(text = stringResource(R.string.footprint_bookmarks_search_placeholder))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag(ReadingFootprintTestTags.BOOKMARK_SEARCH_INPUT),
        )

        if (distinctNovels.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = selectedNovelAidFilter == 0,
                    onClick = { selectedNovelAidFilter = 0 },
                    label = {
                        Text(
                            text = "${stringResource(R.string.footprint_bookmarks_filter_all)} (${bookmarks.size})",
                        )
                    },
                )
                distinctNovels.forEach { (aid, title, count) ->
                    FilterChip(
                        selected = selectedNovelAidFilter == aid,
                        onClick = {
                            selectedNovelAidFilter = if (selectedNovelAidFilter == aid) 0 else aid
                        },
                        label = {
                            Text(
                                text = "$title ($count)",
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                    )
                }
            }
        }

        if (filteredBookmarks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.footprint_bookmarks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return
        }

        val dateFormatter = remember {
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.getDefault())
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = filteredBookmarks,
                key = { "bm_${it.id}" },
            ) { item ->
                val createdText = remember(item.createdAtMs) {
                    runCatching {
                        Instant.ofEpochMilli(item.createdAtMs)
                            .atZone(ZoneId.systemDefault())
                            .format(dateFormatter)
                    }.getOrDefault("")
                }

                Card(
                    onClick = { onJumpToBookmark(item) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .einkHairlineBorder(MaterialTheme.shapes.large)
                        .testTag("${ReadingFootprintTestTags.BOOKMARK_ITEM_PREFIX}${item.id}"),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.novelTitle.ifBlank { "AID #${item.aid}" },
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = buildString {
                                        if (item.volumeTitle.isNotBlank()) {
                                            append(item.volumeTitle)
                                            append(" · ")
                                        }
                                        append(item.chapterTitle.ifBlank { "第 ${item.cid} 章" })
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            val deleteLabel = stringResource(R.string.footprint_bookmarks_delete_desc)
                            TooltipIconButton(
                                tooltipText = deleteLabel,
                                onClick = { onDeleteBookmark(item.id) },
                                modifier = Modifier.testTag(
                                    "${ReadingFootprintTestTags.BOOKMARK_DELETE_PREFIX}${item.id}",
                                ),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.DeleteOutline,
                                    contentDescription = deleteLabel,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }

                        if (item.snippetText.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .einkHairlineBorder(MaterialTheme.shapes.small),
                            ) {
                                Text(
                                    text = "「${item.snippetText.trim()}」",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(12.dp),
                                )
                            }
                        }

                        if (item.noteMarkdown.isNotBlank()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = item.noteMarkdown,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = createdText,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            FilledTonalButton(
                                onClick = { onJumpToBookmark(item) },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag(
                                    "${ReadingFootprintTestTags.BOOKMARK_JUMP_PREFIX}${item.id}",
                                ),
                            ) {
                                Text(
                                    text = stringResource(R.string.footprint_bookmarks_jump_action),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
