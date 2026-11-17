package org.mewx.wenku8.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.mewx.wenku8.MainActivity
import org.mewx.wenku8.R
import org.mewx.wenku8.Wenku8V2Application
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.ui.detail.ReaderLaunchRequest
import java.io.File
import kotlin.math.max

/**
 * Immutable UI snapshot rendered into the Home-Screen "Continue Reading" widget (`P5.4`).
 */
sealed interface ContinueReadingWidgetSnapshot {

    /** Active reading anchor ready for 1-tap resume in `UnifiedReaderScreen`. */
    data class ActiveBook(
        val aid: Int,
        val novelTitle: String,
        val vid: Int,
        val volumeTitle: String,
        val cid: Int,
        val chapterTitle: String,
        val paragraphIndex: Int,
        val charOffset: Int,
        val progressPermille: Int,
        val localCoverFile: File?,
    ) : ContinueReadingWidgetSnapshot {
        val formattedChapterLine: String
            get() {
                val pct = (progressPermille.coerceIn(0, 1000) / 10)
                val chapterPart = buildString {
                    if (volumeTitle.isNotBlank()) {
                        append(volumeTitle.trim())
                        append(" · ")
                    }
                    append(chapterTitle.ifBlank { "第 $cid 章" })
                }
                return "$chapterPart ($pct%)"
            }
    }

    /** Empty state when no reading progress or cached bookshelf chapters exist yet. */
    data object Empty : ContinueReadingWidgetSnapshot
}

/**
 * Resolves the latest [ContinueReadingWidgetSnapshot] from Room (`reading_progress` joined with
 * `novels`, falling back to the top bookshelf novel's first chapter if `reading_progress` is empty)
 * and checks `filesDir/saves/imgs/{aid}.jpg` + `filesDir/imgs/{aid}.jpg` for an offline cover.
 */
suspend fun resolveContinueReadingWidgetSnapshot(
    database: Wenku8Database,
    internalFilesDir: File,
): ContinueReadingWidgetSnapshot {
    val recent = database.readingProgressDao().getMostRecentHistoryWithNovel()
    if (recent != null && recent.aid > 0 && recent.cid > 0) {
        return ContinueReadingWidgetSnapshot.ActiveBook(
            aid = recent.aid,
            novelTitle = recent.novelTitle.ifBlank { "AID #${recent.aid}" },
            vid = recent.vid,
            volumeTitle = recent.volumeTitle,
            cid = recent.cid,
            chapterTitle = recent.chapterTitle,
            paragraphIndex = recent.paragraphIndex,
            charOffset = recent.charOffset,
            progressPermille = recent.progressPermille.coerceIn(0, 1000),
            localCoverFile = findLocalNovelCoverFile(internalFilesDir, recent.aid),
        )
    }

    val topShelfNovel = database.novelDao().getBookshelfNovelsSnapshot().firstOrNull()
    if (topShelfNovel != null) {
        val firstChapter = database.tocDao()
            .getOrderedChaptersWithVolume(topShelfNovel.aid)
            .firstOrNull()
        if (firstChapter != null) {
            return ContinueReadingWidgetSnapshot.ActiveBook(
                aid = topShelfNovel.aid,
                novelTitle = topShelfNovel.title.ifBlank { "AID #${topShelfNovel.aid}" },
                vid = firstChapter.vid,
                volumeTitle = firstChapter.volumeTitle,
                cid = firstChapter.cid,
                chapterTitle = firstChapter.chapterTitle,
                paragraphIndex = 0,
                charOffset = 0,
                progressPermille = 0,
                localCoverFile = findLocalNovelCoverFile(internalFilesDir, topShelfNovel.aid),
            )
        }
    }

    return ContinueReadingWidgetSnapshot.Empty
}

internal fun findLocalNovelCoverFile(internalFilesDir: File, aid: Int): File? {
    val primarySaveCover = File(internalFilesDir, "saves/imgs/$aid.jpg")
    if (primarySaveCover.isFile && primarySaveCover.length() > 0L) {
        return primarySaveCover
    }
    val legacyCacheCover = File(internalFilesDir, "imgs/$aid.jpg")
    if (legacyCacheCover.isFile && legacyCacheCover.length() > 0L) {
        return legacyCacheCover
    }
    return null
}

/**
 * Builds the [MainActivity] deep-link [Intent] triggered when the user taps the widget.
 */
fun buildWidgetLaunchIntent(
    context: Context,
    snapshot: ContinueReadingWidgetSnapshot,
): Intent {
    return Intent(context, MainActivity::class.java).apply {
        action = ContinueReadingAppWidgetProvider.ACTION_OPEN_FROM_WIDGET
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
            Intent.FLAG_ACTIVITY_SINGLE_TOP
        if (snapshot is ContinueReadingWidgetSnapshot.ActiveBook) {
            putExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_AID, snapshot.aid)
            putExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_NOVEL_TITLE, snapshot.novelTitle)
            putExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_VID, snapshot.vid)
            putExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_VOLUME_TITLE, snapshot.volumeTitle)
            putExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_CID, snapshot.cid)
            putExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_CHAPTER_TITLE, snapshot.chapterTitle)
            putExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_PARAGRAPH_INDEX, snapshot.paragraphIndex)
            putExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_CHAR_OFFSET, snapshot.charOffset)
        } else {
            putExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_OPEN_BOOKSHELF, true)
        }
    }
}

/**
 * Extracts a [ReaderLaunchRequest] from a widget deep-link [Intent], or `null` if the intent does
 * not carry a valid chapter anchor.
 */
fun parseWidgetReaderLaunchRequest(intent: Intent?): ReaderLaunchRequest? {
    if (intent == null) return null
    val aid = intent.getIntExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_AID, 0)
    val cid = intent.getIntExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_CID, 0)
    if (aid <= 0 || cid <= 0) return null
    return ReaderLaunchRequest(
        aid = aid,
        novelTitle = intent.getStringExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_NOVEL_TITLE)
            .orEmpty()
            .ifBlank { "AID #$aid" },
        vid = intent.getIntExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_VID, 0),
        volumeTitle = intent.getStringExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_VOLUME_TITLE)
            .orEmpty(),
        cid = cid,
        chapterTitle = intent.getStringExtra(ContinueReadingAppWidgetProvider.EXTRA_WIDGET_CHAPTER_TITLE)
            .orEmpty(),
        initialParagraphIndex = intent.getIntExtra(
            ContinueReadingAppWidgetProvider.EXTRA_WIDGET_PARAGRAPH_INDEX,
            0,
        ).coerceAtLeast(0),
        initialCharOffset = intent.getIntExtra(
            ContinueReadingAppWidgetProvider.EXTRA_WIDGET_CHAR_OFFSET,
            0,
        ).coerceAtLeast(0),
    )
}

/**
 * Builds the [RemoteViews] hierarchy for a single widget instance. Kept public/internal for
 * deterministic Robolectric testing.
 */
fun buildContinueReadingRemoteViews(
    context: Context,
    snapshot: ContinueReadingWidgetSnapshot,
): RemoteViews {
    val views = RemoteViews(context.packageName, R.layout.widget_continue_reading)
    val launchIntent = buildWidgetLaunchIntent(context, snapshot)
    val pendingIntent = PendingIntent.getActivity(
        context,
        when (snapshot) {
            is ContinueReadingWidgetSnapshot.ActiveBook -> snapshot.aid
            ContinueReadingWidgetSnapshot.Empty -> 0
        },
        launchIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)
    views.setOnClickPendingIntent(R.id.widget_cta_button, pendingIntent)

    when (snapshot) {
        is ContinueReadingWidgetSnapshot.ActiveBook -> {
            views.setTextViewText(R.id.widget_novel_title, snapshot.novelTitle)
            views.setTextViewText(R.id.widget_chapter_subtitle, snapshot.formattedChapterLine)
            views.setViewVisibility(R.id.widget_progress_bar, View.VISIBLE)
            views.setProgressBar(
                R.id.widget_progress_bar,
                1000,
                snapshot.progressPermille.coerceIn(0, 1000),
                false,
            )
            views.setTextViewText(
                R.id.widget_cta_button,
                context.getString(R.string.widget_continue_action_resume),
            )
            val coverBitmap = snapshot.localCoverFile?.let { decodeDownsampledWidgetCover(it) }
            if (coverBitmap != null) {
                views.setImageViewBitmap(R.id.widget_cover_image, coverBitmap)
            } else {
                views.setImageViewResource(R.id.widget_cover_image, R.drawable.ic_launcher)
            }
        }

        ContinueReadingWidgetSnapshot.Empty -> {
            views.setTextViewText(
                R.id.widget_novel_title,
                context.getString(R.string.widget_continue_empty_title),
            )
            views.setTextViewText(
                R.id.widget_chapter_subtitle,
                context.getString(R.string.widget_continue_empty_subtitle),
            )
            views.setViewVisibility(R.id.widget_progress_bar, View.INVISIBLE)
            views.setTextViewText(
                R.id.widget_cta_button,
                context.getString(R.string.widget_continue_action_open_shelf),
            )
            views.setImageViewResource(R.id.widget_cover_image, R.drawable.ic_launcher)
        }
    }
    return views
}

/**
 * Downsamples local cover JPEGs into a compact `<= 192x272` `ARGB_8888` [Bitmap] so Binder
 * `RemoteViews` transactions stay well under Android's `1 MB` Binder IPC limit.
 */
private fun decodeDownsampledWidgetCover(file: File): Bitmap? {
    return runCatching {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
        if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) return null

        var sampleSize = 1
        val targetW = 192
        val targetH = 272
        while ((boundsOpts.outWidth / (sampleSize * 2)) >= targetW &&
            (boundsOpts.outHeight / (sampleSize * 2)) >= targetH
        ) {
            sampleSize *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply {
            inSampleSize = max(1, sampleSize)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
    }.getOrNull()
}

/**
 * Official Android Home-Screen Widget (`4×2` resizable) displaying the user's active light novel
 * cover, chapter progress bar, and 1-tap `继续阅读` deep-link into `UnifiedReaderScreen`.
 */
class ContinueReadingAppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        if (appWidgetIds.isEmpty()) return
        val appContext = context.applicationContext
        val application = appContext as? Wenku8V2Application ?: return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val snapshot = resolveContinueReadingWidgetSnapshot(
                    database = application.appContainer.database,
                    internalFilesDir = appContext.filesDir,
                )
                val views = buildContinueReadingRemoteViews(appContext, snapshot)
                for (widgetId in appWidgetIds) {
                    appWidgetManager.updateAppWidget(widgetId, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_OPEN_FROM_WIDGET = "org.mewx.wenku8.action.OPEN_FROM_WIDGET"
        const val EXTRA_WIDGET_OPEN_BOOKSHELF = "widget_open_bookshelf"
        const val EXTRA_WIDGET_AID = "widget_aid"
        const val EXTRA_WIDGET_NOVEL_TITLE = "widget_novel_title"
        const val EXTRA_WIDGET_VID = "widget_vid"
        const val EXTRA_WIDGET_VOLUME_TITLE = "widget_volume_title"
        const val EXTRA_WIDGET_CID = "widget_cid"
        const val EXTRA_WIDGET_CHAPTER_TITLE = "widget_chapter_title"
        const val EXTRA_WIDGET_PARAGRAPH_INDEX = "widget_paragraph_index"
        const val EXTRA_WIDGET_CHAR_OFFSET = "widget_char_offset"
    }
}

/**
 * Helper triggered whenever reading progress is saved so placed home-screen widgets reflect the
 * latest chapter & percentage immediately without waiting for `updatePeriodMillis`.
 */
object ContinueReadingWidgetUpdater {

    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        val manager = runCatching { AppWidgetManager.getInstance(appContext) }.getOrNull() ?: return
        val component = ComponentName(appContext, ContinueReadingAppWidgetProvider::class.java)
        val widgetIds = runCatching { manager.getAppWidgetIds(component) }.getOrNull() ?: return
        if (widgetIds.isEmpty()) return

        val application = appContext as? Wenku8V2Application
        if (application != null) {
            CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                runCatching {
                    val snapshot = resolveContinueReadingWidgetSnapshot(
                        database = application.appContainer.database,
                        internalFilesDir = appContext.filesDir,
                    )
                    val views = buildContinueReadingRemoteViews(appContext, snapshot)
                    for (id in widgetIds) {
                        manager.updateAppWidget(id, views)
                    }
                }
            }
        } else {
            val updateIntent = Intent(appContext, ContinueReadingAppWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
            }
            appContext.sendBroadcast(updateIntent)
        }
    }
}
