package org.mewx.wenku8.v2.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import android.view.KeyEvent as AndroidKeyEvent
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.R
import org.mewx.wenku8.v2.Wenku8V2Application
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.dao.OrderedChapterWithVolume
import org.mewx.wenku8.v2.data.local.db.entity.BookmarkNoteEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingSessionEntity
import org.mewx.wenku8.v2.widget.ContinueReadingWidgetUpdater
import org.mewx.wenku8.v2.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.v2.data.local.preferences.resolveSyncedThemeForPaperPreset
import org.mewx.wenku8.v2.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.v2.data.repository.CustomAssetSlot
import org.mewx.wenku8.v2.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.v2.data.repository.StorageAndCacheRepository
import org.mewx.wenku8.v2.reader.engine.ChapterBlock
import org.mewx.wenku8.v2.reader.engine.ChapterWindowBuffer
import org.mewx.wenku8.v2.reader.engine.ChapterWindowSlot
import org.mewx.wenku8.v2.reader.engine.ParagraphLineBreaker
import org.mewx.wenku8.v2.reader.engine.AndroidStaticLayoutLineBreaker
import org.mewx.wenku8.v2.reader.engine.ReaderLayoutConfig
import org.mewx.wenku8.v2.reader.engine.ReaderPageElement
import org.mewx.wenku8.v2.reader.engine.ReaderWindowSnapshot
import org.mewx.wenku8.v2.reader.engine.ReadingAnchor
import org.mewx.wenku8.v2.reader.engine.StaticLayoutPaginator
import org.mewx.wenku8.v2.reader.model.ReaderPageTurnMode
import org.mewx.wenku8.v2.reader.model.ReaderPaperPreset
import org.mewx.wenku8.v2.ui.components.ActionTooltipBox
import org.mewx.wenku8.v2.ui.components.TooltipIconButton
import org.mewx.wenku8.v2.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.v2.ui.theme.LocalEinkMode
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.mewx.wenku8.v2.ui.theme.einkHairlineBorder
import java.io.File
import kotlin.math.roundToInt

/** Semantic UI test tags for Robolectric & instrumented tests on `UnifiedReaderScreen` (`P5.2`). */
object ReaderTestTags {
    const val SCREEN = "reader_screen"
    const val VIEWPORT_CONTAINER = "reader_viewport_container"
    const val PAGED_HORIZONTAL_PAGER = "reader_paged_horizontal_pager"
    const val CONTINUOUS_SCROLL_LIST = "reader_continuous_scroll_list"
    const val PAGE_CANVAS_PREFIX = "reader_page_canvas_"
    const val INLINE_IMAGE_PREFIX = "reader_inline_image_"
    const val LOADING_SLOT = "reader_loading_slot"
    const val ERROR_SLOT = "reader_error_slot"
    const val HUD_TOP_BAR = "reader_hud_top_bar"
    const val HUD_BACK_BUTTON = "reader_hud_back_button"
    const val HUD_BOOKMARK_TOGGLE = "reader_hud_bookmark_toggle"
    const val HUD_VIEW_ILLUSTRATIONS_CHIP = "reader_hud_view_illustrations_chip"
    const val HUD_PEEK_VOLUME_ILLUSTRATIONS_BUTTON = "reader_hud_peek_volume_illustrations_button"
    const val HUD_SCRUBBER_SLIDER = "reader_hud_scrubber_slider"
    const val HUD_PREV_CHAPTER_BUTTON = "reader_hud_prev_chapter_button"
    const val HUD_NEXT_CHAPTER_BUTTON = "reader_hud_next_chapter_button"
    const val UNDO_JUMP_PILL = "reader_undo_jump_pill"
    const val HUD_FLOATING_TOOLBAR = "reader_hud_floating_toolbar"
    const val ACTION_TOC = "reader_action_toc"
    const val ACTION_TYPOGRAPHY = "reader_action_typography"
    const val ACTION_PAGE_MODE = "reader_action_page_mode"
    const val ACTION_BOOKMARKS = "reader_action_bookmarks"
    const val ACTION_DAY_NIGHT = "reader_action_day_night"
    const val TYPOGRAPHY_PANEL = "reader_typography_panel"
    const val ACTIVE_SLIDER_OPAQUE_CAPSULE = "reader_active_slider_opaque_capsule"
    const val SLIDER_FONT_SIZE = "reader_slider_font_size"
    const val SLIDER_LINE_DISTANCE = "reader_slider_line_distance"
    const val SLIDER_PARAGRAPH_DISTANCE = "reader_slider_paragraph_distance"
    const val SLIDER_PAGE_EDGE = "reader_slider_page_edge"
    const val PAGE_MODE_ITEM_PREFIX = "reader_page_mode_item_"
    const val PAPER_PRESET_CHIP_PREFIX = "reader_paper_preset_"
    const val TOC_SHEET = "reader_toc_sheet"
    const val TOC_CHAPTER_ITEM_PREFIX = "reader_toc_chapter_"
    const val BOOKMARKS_SHEET = "reader_bookmarks_sheet"
    const val BRIGHTNESS_PILL = "reader_brightness_pill"
    const val SWITCH_VOLUME_KEYS = "reader_switch_volume_keys"
    const val SWITCH_KEEP_SCREEN_ON = "reader_switch_keep_screen_on"
    const val SWITCH_EDGE_BRIGHTNESS = "reader_switch_edge_brightness"
    const val HUD_SEARCH_BUTTON = "reader_hud_search_button"
    const val ACTION_TTS = "reader_action_tts"
    const val SEARCH_SHEET = "reader_search_sheet"
    const val SEARCH_INPUT = "reader_search_input"
    const val SEARCH_SCOPE_CURRENT_CHIP = "reader_search_scope_current"
    const val SEARCH_SCOPE_CACHED_CHIP = "reader_search_scope_cached"
    const val SEARCH_RESULT_PREFIX = "reader_search_result_"
    const val TTS_CONTROLLER_BAR = "reader_tts_controller_bar"
    const val TTS_RATE_CHIP = "reader_tts_rate_chip"
    const val TTS_PREV_BUTTON = "reader_tts_prev_button"
    const val TTS_PLAY_PAUSE_BUTTON = "reader_tts_play_pause_button"
    const val TTS_NEXT_BUTTON = "reader_tts_next_button"
    const val TTS_STOP_BUTTON = "reader_tts_stop_button"
    const val READER_CLOCK_LABEL = "reader_clock_label"
    const val TTS_HIGHLIGHTED_BLOCK_PREFIX = "reader_tts_highlight_block_"
    const val FOCAL_HIGHLIGHTED_BLOCK_PREFIX = "reader_focal_highlight_block_"
    const val BOOKMARK_ITEM_PREFIX = "reader_bookmark_item_"
}

enum class ReaderTypographySliderField(val label: String) {
    FONT_SIZE("字号大小"),
    LINE_DISTANCE("行间距"),
    PARAGRAPH_DISTANCE("段间距"),
    PAGE_EDGE("页边距"),
}

private data class ReaderUndoJumpState(
    val cid: Int,
    val pageIndexOneBased: Int,
    val anchor: ReadingAnchor,
    val createdAtElapsedMs: Long,
)

private enum class ReaderArtbookEntryMode {
    INLINE_CHAPTER_GALLERY,
    PURE_ILLUSTRATION_CHAPTER,
    TEMPORARY_VOLUME_PEEK,
}

private data class ReaderActiveArtbookState(
    val cid: Int,
    val volumeVid: Int = 0,
    val chapterTitle: String,
    val imageUrls: List<String>,
    val initialIndex: Int,
    val entryMode: ReaderArtbookEntryMode = ReaderArtbookEntryMode.INLINE_CHAPTER_GALLERY,
)

private data class ActiveFocalHighlightState(
    val cid: Int,
    val blockIndex: Int,
    val startChar: Int = -1,
    val endCharExclusive: Int = -1,
)

/**
 * Implemented by [org.mewx.wenku8.v2.MainActivity] so hardware `KEYCODE_VOLUME_UP` / `DOWN` events
 * reach [UnifiedReaderScreen] reliably even when no Compose node holds explicit focus.
 */
interface HardwareVolumeKeyHost {
    var activeVolumeKeyInterceptor: ((AndroidKeyEvent) -> Boolean)?
}

/**
 * Pure, unit-testable hardware volume-key interceptor rule (`10-next-gen-reader-engine-architecture.md` §7):
 * - Consumes BOTH `ACTION_DOWN` and `ACTION_UP` for `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN`
 *   when [volumeKeyPagingEnabled] is `true` so Android's system volume HUD never intrudes.
 * - Emits a single page step strictly on `ACTION_DOWN` with `repeatCount == 0` (non-repeating).
 */
fun evaluateHardwareVolumeKeyPaging(
    volumeKeyPagingEnabled: Boolean,
    isModalOverlayOpen: Boolean,
    keyCode: Int,
    action: Int,
    repeatCount: Int,
): ReaderTapZoneAction? {
    if (!volumeKeyPagingEnabled || isModalOverlayOpen) return null
    val isVolumeUp = keyCode == AndroidKeyEvent.KEYCODE_VOLUME_UP
    val isVolumeDown = keyCode == AndroidKeyEvent.KEYCODE_VOLUME_DOWN
    if (!isVolumeUp && !isVolumeDown) return null
    if (action != AndroidKeyEvent.ACTION_DOWN || repeatCount != 0) {
        return null
    }
    return if (isVolumeUp) ReaderTapZoneAction.PREV_PAGE else ReaderTapZoneAction.NEXT_PAGE
}

fun shouldConsumeHardwareVolumeKeyEvent(
    volumeKeyPagingEnabled: Boolean,
    isModalOverlayOpen: Boolean,
    keyCode: Int,
): Boolean {
    if (!volumeKeyPagingEnabled || isModalOverlayOpen) return false
    return keyCode == AndroidKeyEvent.KEYCODE_VOLUME_UP ||
        keyCode == AndroidKeyEvent.KEYCODE_VOLUME_DOWN
}

/**
 * Full-screen Material 3 Expressive (`M3e`) `UnifiedReaderScreen` (`P5.2` + `P5.3`).
 *
 * Integrates:
 * - `ChapterWindowBuffer` (3-chapter continuous virtual paging + 70% pre-fetch + zero-IO reflow)
 * - 6 Official-AndroidX page-turn delegates (`ReaderViewportHost`)
 * - 9-zone tap matrix + Predictive-Back-safe (`24..72dp`) left-edge brightness drag
 * - Hardware `KEYCODE_VOLUME_UP` / `KEYCODE_VOLUME_DOWN` non-repeating page turning
 * - Contextual M3e `HorizontalFloatingToolbar` HUD + chapter scrubber + `5s` `"↩ 还原至第 N 页"` pill
 * - **Opaque Active-Slider Reflow Capsule** (`92%`-opaque `surfaceContainerHighest` pill while
 *   dragging `8–36 sp` font size or spacing sliders, zero text-on-text overlap)
 * - **In-Reader `ArtbookLightboxOverlay`** (`BitmapRegionDecoder` 4× tiling zoom + `跨页拼合` spreads)
 * - **In-Book Search** (`当前章节` & `已缓存全书`) and **System `TextToSpeech`** read-aloud (`P5.3`)
 * - Auto-saved `ReadingProgressDao` + `ReadingSessionDao` duration/character telemetry
 */
@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun UnifiedReaderScreen(
    launchRequest: ReaderLaunchRequest,
    database: Wenku8Database,
    catalogGateway: WenkuCatalogGateway,
    novelTocSyncRepository: NovelTocSyncRepository,
    preferences: Wenku8UserPreferences,
    storageAndCacheRepository: StorageAndCacheRepository? = null,
    onReaderTypographyChanged: (
        fontSizeSp: Int,
        lineDistanceDp: Int,
        paragraphDistanceDp: Int,
        pageEdgeDistanceDp: Int,
    ) -> Unit,
    onReaderCustomFontPathChanged: (String) -> Unit = {},
    onReaderCustomBackgroundPathChanged: (String) -> Unit = {},
    onReaderPageTurnModeChanged: (ReaderPageTurnMode) -> Unit = {},
    onReaderPaperPresetChanged: (ReaderPaperPreset) -> Unit = {},
    onThemeModeSelected: (Wenku8ThemeMode) -> Unit = {},
    onReaderKeepScreenOnChanged: (Boolean) -> Unit = {},
    onReaderEdgeBrightnessDragChanged: (Boolean) -> Unit = {},
    onVolumeKeyPagingChanged: (Boolean) -> Unit = {},
    onIllustrationClicked: (
        chapterTitle: String,
        imageUrlsInChapter: List<String>,
        clickedIndex: Int,
    ) -> Unit = { _, _, _ -> },
    onOpenNetworkDiagnostics: () -> Unit = {},
    onNavigateBack: () -> Unit,
    lineBreaker: ParagraphLineBreaker = AndroidStaticLayoutLineBreaker,
    speechEngineAdapter: SpeechEngineAdapter? = null,
    artbookImageFileResolver: (suspend (String) -> File?)? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isEinkMode = LocalEinkMode.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val focusRequester = remember { FocusRequester() }

    val latestAppLang by rememberUpdatedState(preferences.appLanguage)

    // Scoped FLAG_KEEP_SCREEN_ON while inside Reader if enabled by user.
    DisposableEffect(context, preferences.readerKeepScreenOn) {
        val window = context.findHostActivity()?.window
        if (preferences.readerKeepScreenOn) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (preferences.readerKeepScreenOn) {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    // Live typography working state (updated immediately during slider drag for 120fps live reflow).
    var liveFontSizeSp by remember(preferences.readerFontSizeSp) {
        mutableIntStateOf(preferences.readerFontSizeSp)
    }
    var liveLineDistanceDp by remember(preferences.readerLineDistanceDp) {
        mutableIntStateOf(preferences.readerLineDistanceDp)
    }
    var liveParagraphDistanceDp by remember(preferences.readerParagraphDistanceDp) {
        mutableIntStateOf(preferences.readerParagraphDistanceDp)
    }
    var livePageEdgeDistanceDp by remember(preferences.readerPageEdgeDistanceDp) {
        mutableIntStateOf(preferences.readerPageEdgeDistanceDp)
    }

    // HUD & overlay panel states.
    var isHudVisible by rememberSaveable { mutableStateOf(false) }
    var isTypographyPanelOpen by rememberSaveable { mutableStateOf(false) }
    var activeDraggingSlider by remember { mutableStateOf<ReaderTypographySliderField?>(null) }
    var dragLockedAnchor by remember { mutableStateOf<ReadingAnchor?>(null) }

    var isTocSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isPageModeSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isBookmarksSheetOpen by rememberSaveable { mutableStateOf(false) }
    var isSearchSheetOpen by rememberSaveable { mutableStateOf(false) }
    var activeArtbookState by remember { mutableStateOf<ReaderActiveArtbookState?>(null) }
    // Chapter IDs where the user tapped "小说阅读" inside an auto-opened pure-illustration chapter.
    var novelModeOverriddenCids by rememberSaveable(launchRequest.aid) { mutableStateOf(intArrayOf()) }
    var lastAutoOpenedGalleryCid by rememberSaveable(launchRequest.aid) { mutableIntStateOf(0) }
    val cachedVolumePeekUrlsByVid = remember(launchRequest.aid) { mutableMapOf<Int, List<String>>() }
    val lastVolumePeekIndexByVid = remember(launchRequest.aid) { mutableMapOf<Int, Int>() }
    var isPeekingVolumeIllustrationsLoading by remember { mutableStateOf(false) }

    // Continuous-scroll (`上下卷轴`) live anchor, progress, and imperative jump channel.
    var continuousScrollAnchor by remember(launchRequest.aid) { mutableStateOf(ReadingAnchor()) }
    var continuousScrollProgress by remember(launchRequest.aid) { mutableFloatStateOf(0f) }
    var scrollJumpRevision by remember { mutableLongStateOf(0L) }
    var continuousScrollJumpCommand by remember { mutableStateOf<ContinuousScrollJumpCommand?>(null) }
    fun dispatchContinuousScrollJump(cid: Int, paragraphIndex: Int, animated: Boolean = false) {
        scrollJumpRevision += 1L
        continuousScrollJumpCommand = ContinuousScrollJumpCommand(
            revision = scrollJumpRevision,
            cid = cid,
            paragraphIndex = paragraphIndex.coerceAtLeast(0),
            animated = animated,
        )
    }

    // Search / Bookmark jump highlight state shared across all 6 page-turn modes.
    var activeFocalHighlight by remember(launchRequest.aid) {
        mutableStateOf<ActiveFocalHighlightState?>(null)
    }

    val applyPaperPresetAndSyncGlobalTheme: (ReaderPaperPreset) -> Unit = { nextPreset ->
        val syncedTheme = resolveSyncedThemeForPaperPreset(
            preset = nextPreset,
            currentThemeMode = preferences.themeMode,
            lastDayThemeMode = preferences.lastDayThemeMode,
        )
        onReaderPaperPresetChanged(nextPreset)
        if (syncedTheme != preferences.themeMode) {
            onThemeModeSelected(syncedTheme)
        }
    }

    // Brightness HUD pill state.
    var brightnessFraction by remember {
        val initialWinBrightness = context.findHostActivity()?.window?.attributes?.screenBrightness ?: -1f
        mutableFloatStateOf(if (initialWinBrightness in 0.01f..1f) initialWinBrightness else 0.55f)
    }
    var brightnessDragTick by remember { mutableLongStateOf(0L) }
    var showBrightnessPill by remember { mutableStateOf(false) }

    LaunchedEffect(brightnessDragTick) {
        if (brightnessDragTick > 0L) {
            showBrightnessPill = true
            delay(1_200L)
            showBrightnessPill = false
        }
    }

    // 5-second Undo Jump Pill state (`↩ 还原至第 N 页`).
    var undoJumpState by remember { mutableStateOf<ReaderUndoJumpState?>(null) }
    LaunchedEffect(undoJumpState) {
        val currentUndo = undoJumpState ?: return@LaunchedEffect
        delay(5_000L)
        if (undoJumpState === currentUndo) {
            undoJumpState = null
        }
    }

    val palette = remember(
        preferences.readerPaperPreset,
        isEinkMode,
        preferences.readerCustomBackgroundPath,
        preferences.readerCustomFontPath,
    ) {
        resolveReaderVisualPalette(
            paperPreset = preferences.readerPaperPreset,
            isEinkMode = isEinkMode,
            customBackgroundPath = preferences.readerCustomBackgroundPath,
            customFontPath = preferences.readerCustomFontPath,
        )
    }

    val windowBuffer = remember(launchRequest.aid) {
        ChapterWindowBuffer(
            aid = launchRequest.aid,
            appLanguageProvider = { latestAppLang },
            tocDao = database.tocDao(),
            chapterContentDao = database.chapterContentDao(),
            catalogGateway = catalogGateway,
            novelTocSyncRepository = novelTocSyncRepository,
            paginator = StaticLayoutPaginator(lineBreaker = lineBreaker),
            scope = coroutineScope,
            ioDispatcher = ioDispatcher,
            computeDispatcher = computeDispatcher,
        )
    }
    DisposableEffect(windowBuffer) {
        onDispose { windowBuffer.close() }
    }

    val snapshot by windowBuffer.snapshotFlow.collectAsStateWithLifecycle()
    val cachedChapterIdList by remember(launchRequest.aid) {
        database.chapterContentDao().observeCachedChapterIdsForNovel(launchRequest.aid)
    }.collectAsStateWithLifecycle(initialValue = emptyList())
    val cachedChapterIds = remember(cachedChapterIdList) { cachedChapterIdList.toHashSet() }

    val novelBookmarks by remember(launchRequest.aid) {
        database.bookmarkNoteDao().observeNotesForNovel(launchRequest.aid)
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    // Reading session telemetry (`READING_SESSIONS` table for Profile streak & heatmap).
    var sessionCid by remember { mutableIntStateOf(launchRequest.cid) }
    var sessionStartedElapsedMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    val visitedPagesForSession = remember { HashSet<Long>() }
    var accumulatedSessionChars by remember { mutableIntStateOf(0) }

    suspend fun flushReadingSessionIfNonTrivial(completedCid: Int) {
        if (completedCid <= 0) return
        val nowElapsed = SystemClock.elapsedRealtime()
        val elapsedSec = ((nowElapsed - sessionStartedElapsedMs) / 1000L).toInt().coerceAtLeast(0)
        val chars = accumulatedSessionChars
        sessionStartedElapsedMs = nowElapsed
        visitedPagesForSession.clear()
        accumulatedSessionChars = 0

        if (elapsedSec >= 1 || chars > 0) {
            val epochDay = System.currentTimeMillis() / MILLIS_PER_DAY
            withContext(ioDispatcher) {
                database.readingSessionDao().insertSession(
                    ReadingSessionEntity(
                        aid = launchRequest.aid,
                        cid = completedCid,
                        dateEpochDay = epochDay,
                        durationSeconds = elapsedSec.coerceAtLeast(1),
                        charactersRead = chars,
                    ),
                )
            }
        }
    }

    suspend fun persistReadingProgress(
        chapter: OrderedChapterWithVolume,
        anchor: ReadingAnchor,
    ) {
        withContext(ioDispatcher) {
            val existingNovel = database.novelDao().getSyncHeader(launchRequest.aid)
            if (existingNovel == null) {
                database.novelDao().upsertNovel(
                    NovelEntity(
                        aid = launchRequest.aid,
                        contentLang = latestAppLang.name,
                        title = launchRequest.novelTitle.ifBlank { "AID #${launchRequest.aid}" },
                        metaSyncedAtMs = System.currentTimeMillis(),
                    ),
                )
            }
            database.readingProgressDao().upsertProgress(
                ReadingProgressEntity(
                    aid = launchRequest.aid,
                    vid = chapter.vid,
                    cid = chapter.cid,
                    volumeTitle = chapter.volumeTitle,
                    chapterTitle = chapter.chapterTitle,
                    paragraphIndex = anchor.paragraphIndex,
                    charOffset = anchor.charOffset,
                    legacyScrollPx = 0,
                    progressPermille = anchor.progressPermille,
                    updatedAtMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    // Flush active reading session cleanly and refresh placed Home-Screen Widgets when exiting.
    DisposableEffect(launchRequest.aid) {
        onDispose {
            val closingCid = sessionCid
            coroutineScope.launch(NonCancellable) {
                flushReadingSessionIfNonTrivial(closingCid)
                ContinueReadingWidgetUpdater.requestUpdate(context)
            }
        }
    }

    // Track active page / chapter changes to auto-save `reading_progress` and accumulate read chars.
    LaunchedEffect(
        snapshot.activeChapter?.cid,
        snapshot.activePageIndexInCurrentChapter,
        (snapshot.currentSlot as? ChapterWindowSlot.Loaded)?.layout,
        preferences.readerPageTurnMode,
    ) {
        val activeChapter = snapshot.activeChapter ?: return@LaunchedEffect
        if (activeChapter.cid != sessionCid) {
            flushReadingSessionIfNonTrivial(sessionCid)
            sessionCid = activeChapter.cid
        }
        val activePage = snapshot.activeReaderPage ?: return@LaunchedEffect
        val pageVisitKey = (activeChapter.cid.toLong() shl 32) or
            (activePage.pageIndexInChapter.toLong() and 0xFFFFFFFFL)
        if (visitedPagesForSession.add(pageVisitKey)) {
            accumulatedSessionChars += activePage.characterCountOnPage
        }
        if (preferences.readerPageTurnMode != ReaderPageTurnMode.CONTINUOUS_SCROLL) {
            persistReadingProgress(
                chapter = activeChapter,
                anchor = activePage.toBookmarkOrProgressAnchor(),
            )
        }
    }

    // SAF custom `.ttf/.otf` font & custom day wallpaper pickers inside Typography Studio.
    val customFontPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && storageAndCacheRepository != null) {
            coroutineScope.launch {
                storageAndCacheRepository.importCustomAsset(
                    contentResolver = context.contentResolver,
                    sourceUri = uri,
                    slot = CustomAssetSlot.READER_FONT,
                ).onSuccess { file ->
                    onReaderCustomFontPathChanged(file.absolutePath)
                    snackbarHostState.showSnackbar("已应用自定义阅读字体")
                }.onFailure {
                    snackbarHostState.showSnackbar("字体文件无效，请选择标准 .ttf 或 .otf 字体")
                }
            }
        }
    }

    val customBackgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && storageAndCacheRepository != null) {
            coroutineScope.launch {
                storageAndCacheRepository.importCustomAsset(
                    contentResolver = context.contentResolver,
                    sourceUri = uri,
                    slot = CustomAssetSlot.READER_BACKGROUND,
                ).onSuccess { file ->
                    onReaderCustomBackgroundPathChanged(file.absolutePath)
                    snackbarHostState.showSnackbar("已更新日间阅读背景图")
                }.onFailure {
                    snackbarHostState.showSnackbar("背景图片导入失败")
                }
            }
        }
    }

    var pendingTtsAutoContinueAfterCid by remember { mutableIntStateOf(0) }
    val ttsController = remember(launchRequest.aid, speechEngineAdapter) {
        val adapter = speechEngineAdapter ?: AndroidTextToSpeechEngineAdapter(context.applicationContext)
        ReaderTtsController(
            engineAdapter = adapter,
            scope = coroutineScope,
            onChapterFinished = { completedCid ->
                pendingTtsAutoContinueAfterCid = completedCid
            },
        )
    }
    DisposableEffect(ttsController) {
        onDispose { ttsController.close() }
    }
    val ttsUiState by ttsController.uiState.collectAsStateWithLifecycle()

    val isAnyModalOpen =
        isTocSheetOpen || isPageModeSheetOpen || isBookmarksSheetOpen || isSearchSheetOpen ||
            activeArtbookState != null

    BackHandler(enabled = activeArtbookState == null) {
        when {
            isTypographyPanelOpen -> {
                activeDraggingSlider = null
                dragLockedAnchor = null
                isTypographyPanelOpen = false
            }
            isHudVisible -> {
                isHudVisible = false
            }
            else -> {
                ttsController.stopAndDismiss()
                onNavigateBack()
            }
        }
    }

    val latestVolumeKeyPagingEnabled by rememberUpdatedState(preferences.volumeKeyPagingEnabled)
    // Bypass volume-key page turning while any modal/artbook is open OR while TTS is actively
    // speaking so hardware volume keys naturally adjust speech audio volume!
    val latestIsModalOrStudioOpen by rememberUpdatedState(
        isAnyModalOpen || isTypographyPanelOpen || ttsUiState.isSpeaking,
    )
    val latestSnapshotForKey by rememberUpdatedState(snapshot)
    val latestPageTurnMode by rememberUpdatedState(preferences.readerPageTurnMode)
    val latestIsEinkMode by rememberUpdatedState(isEinkMode)

    val handleAndroidVolumeKeyEvent: (AndroidKeyEvent) -> Boolean = remember(windowBuffer, coroutineScope) {
        { nativeEvent ->
            val actionToRun = evaluateHardwareVolumeKeyPaging(
                volumeKeyPagingEnabled = latestVolumeKeyPagingEnabled,
                isModalOverlayOpen = latestIsModalOrStudioOpen,
                keyCode = nativeEvent.keyCode,
                action = nativeEvent.action,
                repeatCount = nativeEvent.repeatCount,
            )
            if (actionToRun != null) {
                val snap = latestSnapshotForKey
                val targetVirtual = when (actionToRun) {
                    ReaderTapZoneAction.PREV_PAGE -> snap.activeVirtualPageIndex - 1
                    ReaderTapZoneAction.NEXT_PAGE -> snap.activeVirtualPageIndex + 1
                    ReaderTapZoneAction.TOGGLE_HUD -> snap.activeVirtualPageIndex
                }
                if (targetVirtual in 0 until snap.totalVirtualPages) {
                    val originRevision = snap.coordinateRevision
                    val resolvedTarget = snap.resolveVirtualPage(targetVirtual)
                    coroutineScope.launch {
                        windowBuffer.onVirtualPageSettled(
                            virtualPageIndex = targetVirtual,
                            expectedCoordinateRevision = originRevision,
                        )
                        if (latestPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL &&
                            resolvedTarget?.page != null
                        ) {
                            dispatchContinuousScrollJump(
                                cid = resolvedTarget.slot.chapter.cid,
                                paragraphIndex = resolvedTarget.page.startAnchor.paragraphIndex,
                                animated = !latestIsEinkMode,
                            )
                        }
                    }
                }
                true
            } else {
                shouldConsumeHardwareVolumeKeyEvent(
                    volumeKeyPagingEnabled = latestVolumeKeyPagingEnabled,
                    isModalOverlayOpen = latestIsModalOrStudioOpen,
                    keyCode = nativeEvent.keyCode,
                )
            }
        }
    }

    DisposableEffect(context, handleAndroidVolumeKeyEvent) {
        val host = context.findHostActivity() as? HardwareVolumeKeyHost
        host?.activeVolumeKeyInterceptor = handleAndroidVolumeKeyEvent
        onDispose {
            if (host?.activeVolumeKeyInterceptor === handleAndroidVolumeKeyEvent) {
                host.activeVolumeKeyInterceptor = null
            }
        }
    }

    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val surfaceBgLuminance = (
        0.299f * MaterialTheme.colorScheme.surfaceContainer.red +
            0.587f * MaterialTheme.colorScheme.surfaceContainer.green +
            0.114f * MaterialTheme.colorScheme.surfaceContainer.blue
        )
    val shouldUseLightBarAppearance = when {
        activeArtbookState != null -> false
        isHudVisible && activeDraggingSlider == null -> surfaceBgLuminance > 0.5f
        else -> !palette.isDarkPalette
    }
    val shouldShowStatusBar = isHudVisible || activeArtbookState != null
    val latestShouldUseLightBars by rememberUpdatedState(shouldUseLightBarAppearance)
    val latestShouldShowStatusBar by rememberUpdatedState(shouldShowStatusBar)

    val applyReaderSystemBarsState = remember(context, view) {
        {
            val window = context.findHostActivity()?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.isAppearanceLightStatusBars = latestShouldUseLightBars
                controller.isAppearanceLightNavigationBars = latestShouldUseLightBars
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    window.isNavigationBarContrastEnforced = false
                }
                // Always hide the bottom system navigation bar in reading mode so night mode is
                // never ruined by a bright/contrasting gesture bar; swipe up reveals transiently.
                controller.hide(WindowInsetsCompat.Type.navigationBars())
                if (latestShouldShowStatusBar) {
                    controller.show(WindowInsetsCompat.Type.statusBars())
                } else {
                    controller.hide(WindowInsetsCompat.Type.statusBars())
                }
            }
        }
    }

    DisposableEffect(context, view, lifecycleOwner) {
        val window = context.findHostActivity()?.window
        val controller = if (window != null) WindowCompat.getInsetsController(window, view) else null
        val prevLightStatus = controller?.isAppearanceLightStatusBars
        val prevLightNav = controller?.isAppearanceLightNavigationBars
        val prevBehavior = controller?.systemBarsBehavior
        val prevNavContrastEnforced = if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        }

        applyReaderSystemBarsState()

        val focusListener = ViewTreeObserver.OnWindowFocusChangeListener { hasFocus ->
            if (hasFocus) applyReaderSystemBarsState()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(focusListener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                applyReaderSystemBarsState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            runCatching { view.viewTreeObserver.removeOnWindowFocusChangeListener(focusListener) }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && prevNavContrastEnforced != null) {
                window.isNavigationBarContrastEnforced = prevNavContrastEnforced
            }
            if (controller != null) {
                if (prevBehavior != null) controller.systemBarsBehavior = prevBehavior
                if (prevLightStatus != null) controller.isAppearanceLightStatusBars = prevLightStatus
                if (prevLightNav != null) controller.isAppearanceLightNavigationBars = prevLightNav
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    LaunchedEffect(shouldUseLightBarAppearance, shouldShowStatusBar) {
        applyReaderSystemBarsState()
    }

    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    val cutoutInsets = WindowInsets.displayCutout
    val safeLeftPx = cutoutInsets.getLeft(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val safeRightPx = cutoutInsets.getRight(density, androidx.compose.ui.unit.LayoutDirection.Ltr)
    val safeTopPx = cutoutInsets.getTop(density)
    val safeBottomPx = cutoutInsets.getBottom(density)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(palette.backgroundColor)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { keyEvent ->
                handleAndroidVolumeKeyEvent(keyEvent.nativeKeyEvent)
            }
            .testTag(ReaderTestTags.SCREEN),
    ) {
        val viewportWidthPx = (constraints.maxWidth - safeLeftPx - safeRightPx).coerceAtLeast(320)
        val viewportHeightPx = (constraints.maxHeight - safeTopPx - safeBottomPx).coerceAtLeast(360)

        val currentLayoutConfig = remember(
            viewportWidthPx,
            viewportHeightPx,
            liveFontSizeSp,
            liveLineDistanceDp,
            liveParagraphDistanceDp,
            livePageEdgeDistanceDp,
            palette.customTypeface,
            preferences.readerPageTurnMode,
        ) {
            with(density) {
                ReaderLayoutConfig(
                    viewportWidthPx = viewportWidthPx,
                    viewportHeightPx = viewportHeightPx,
                    fontSizePx = liveFontSizeSp.sp.toPx(),
                    lineDistancePx = liveLineDistanceDp.dp.toPx(),
                    paragraphDistancePx = liveParagraphDistanceDp.dp.toPx(),
                    pageEdgeDistancePx = livePageEdgeDistanceDp.dp.toPx(),
                    headerReservePx = 26.dp.toPx(),
                    footerReservePx = 26.dp.toPx(),
                    typeface = palette.customTypeface,
                    isVerticalBunkobon = preferences.readerPageTurnMode == ReaderPageTurnMode.VERTICAL_BUNKOBON,
                )
            }
        }

        // Open requested chapter on initial mount; re-paginate in memory on subsequent config changes!
        var didInitialOpen by remember(launchRequest.aid, launchRequest.cid) { mutableStateOf(false) }
        LaunchedEffect(launchRequest.aid, launchRequest.cid, currentLayoutConfig) {
            if (!didInitialOpen) {
                didInitialOpen = true
                val resolvedInitialAnchor = if (launchRequest.initialParagraphIndex >= 0) {
                    ReadingAnchor(
                        paragraphIndex = launchRequest.initialParagraphIndex,
                        charOffset = launchRequest.initialCharOffset.coerceAtLeast(0),
                    )
                } else {
                    val saved = withContext(ioDispatcher) {
                        database.readingProgressDao().getProgressForNovel(launchRequest.aid)
                    }
                    if (saved != null && saved.cid == launchRequest.cid) {
                        ReadingAnchor(
                            paragraphIndex = saved.paragraphIndex,
                            charOffset = saved.charOffset,
                            progressPermille = saved.progressPermille,
                        )
                    } else {
                        ReadingAnchor()
                    }
                }
                continuousScrollAnchor = resolvedInitialAnchor
                if (resolvedInitialAnchor.paragraphIndex > 0) {
                    dispatchContinuousScrollJump(
                        cid = launchRequest.cid,
                        paragraphIndex = resolvedInitialAnchor.paragraphIndex,
                        animated = false,
                    )
                }
                windowBuffer.openChapter(
                    cid = launchRequest.cid,
                    initialAnchor = resolvedInitialAnchor,
                    layoutConfig = currentLayoutConfig,
                    fallbackVid = launchRequest.vid,
                    fallbackVolumeTitle = launchRequest.volumeTitle,
                    fallbackChapterTitle = launchRequest.chapterTitle,
                )
            } else {
                windowBuffer.updateLayoutConfig(
                    newConfig = currentLayoutConfig,
                    lockedAnchorOverride = dragLockedAnchor,
                )
            }
        }

        val boundedInitialVirtualPage = if (snapshot.totalVirtualPages > 0) {
            snapshot.activeVirtualPageIndex.coerceIn(0, snapshot.totalVirtualPages - 1)
        } else {
            0
        }
        val pagerState = rememberPagerState(
            initialPage = boundedInitialVirtualPage,
        ) {
            snapshot.totalVirtualPages.coerceAtLeast(1)
        }

        var isProgrammaticPagerSnap by remember { mutableStateOf(false) }
        val latestWindowSnapshot by rememberUpdatedState(snapshot)
        val syncedRevisionHolder = remember { longArrayOf(snapshot.coordinateRevision) }
        if (syncedRevisionHolder[0] != snapshot.coordinateRevision) {
            syncedRevisionHolder[0] = snapshot.coordinateRevision
            if (snapshot.totalVirtualPages > 0) {
                val target = snapshot.activeVirtualPageIndex.coerceIn(0, snapshot.totalVirtualPages - 1)
                if (pagerState.currentPage != target || pagerState.currentPageOffsetFraction != 0f) {
                    pagerState.requestScrollToPage(page = target, pageOffsetFraction = 0f)
                }
            }
        }

        LaunchedEffect(snapshot.coordinateRevision, snapshot.activeVirtualPageIndex, snapshot.totalVirtualPages) {
            if (snapshot.totalVirtualPages > 0) {
                val target = snapshot.activeVirtualPageIndex.coerceIn(0, snapshot.totalVirtualPages - 1)
                if (pagerState.currentPage != target || pagerState.currentPageOffsetFraction != 0f) {
                    isProgrammaticPagerSnap = true
                    try {
                        pagerState.scrollToPage(target)
                    } finally {
                        isProgrammaticPagerSnap = false
                    }
                }
            }
        }

        LaunchedEffect(pagerState) {
            snapshotFlow {
                Triple(
                    pagerState.settledPage,
                    pagerState.isScrollInProgress,
                    latestWindowSnapshot.coordinateRevision,
                )
            }
                .distinctUntilChanged()
                .collect { (settledPage, scrolling, rev) ->
                    val curSnap = latestWindowSnapshot
                    if (!scrolling &&
                        !isProgrammaticPagerSnap &&
                        curSnap.currentSlot != null &&
                        curSnap.coordinateRevision == rev &&
                        syncedRevisionHolder[0] == rev &&
                        pagerState.currentPage == settledPage &&
                        settledPage != curSnap.activeVirtualPageIndex
                    ) {
                        windowBuffer.onVirtualPageSettled(
                            virtualPageIndex = settledPage,
                            expectedCoordinateRevision = rev,
                        )
                    }
                }
        }

        fun stepPageBy(delta: Int) {
            val originRevision = snapshot.coordinateRevision
            val targetVirtual = snapshot.activeVirtualPageIndex + delta
            if (targetVirtual !in 0 until snapshot.totalVirtualPages) return
            coroutineScope.launch {
                val instantFlip = isEinkMode ||
                    preferences.readerPageTurnMode == ReaderPageTurnMode.EINK_INSTANT
                isProgrammaticPagerSnap = true
                try {
                    if (instantFlip) {
                        pagerState.scrollToPage(targetVirtual)
                    } else {
                        runCatching { pagerState.animateScrollToPage(targetVirtual) }
                            .onFailure { pagerState.scrollToPage(targetVirtual) }
                    }
                } finally {
                    isProgrammaticPagerSnap = false
                }
                windowBuffer.onVirtualPageSettled(
                    virtualPageIndex = targetVirtual,
                    expectedCoordinateRevision = originRevision,
                )
            }
        }

        fun jumpToChapterWithUndo(
            targetCid: Int,
            targetAnchor: ReadingAnchor = ReadingAnchor(),
            clearFocalHighlight: Boolean = true,
        ) {
            if (clearFocalHighlight) {
                activeFocalHighlight = null
            }
            val prevChapter = snapshot.activeChapter
            val prevPage = snapshot.activeReaderPage
            if (prevChapter != null && prevPage != null) {
                val savedOriginAnchor =
                    if (preferences.readerPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                        continuousScrollAnchor
                    } else {
                        prevPage.toBookmarkOrProgressAnchor()
                    }
                undoJumpState = ReaderUndoJumpState(
                    cid = prevChapter.cid,
                    pageIndexOneBased = prevPage.pageIndexInChapter + 1,
                    anchor = savedOriginAnchor,
                    createdAtElapsedMs = SystemClock.elapsedRealtime(),
                )
            }
            coroutineScope.launch {
                val loadedCur = snapshot.currentSlot as? ChapterWindowSlot.Loaded
                if (prevChapter?.cid == targetCid && loadedCur != null) {
                    if (preferences.readerPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                        continuousScrollAnchor = targetAnchor
                        dispatchContinuousScrollJump(
                            cid = targetCid,
                            paragraphIndex = targetAnchor.paragraphIndex,
                            animated = false,
                        )
                        windowBuffer.syncActiveAnchorInCurrentChapter(targetAnchor)
                        persistReadingProgress(prevChapter, targetAnchor)
                    } else {
                        val originRevision = snapshot.coordinateRevision
                        val resolvedLocal = loadedCur.layout.resolvePageIndexForAnchor(targetAnchor)
                        val targetVirtual = snapshot.prevPageCount + resolvedLocal
                        isProgrammaticPagerSnap = true
                        try {
                            pagerState.scrollToPage(targetVirtual)
                        } finally {
                            isProgrammaticPagerSnap = false
                        }
                        windowBuffer.onVirtualPageSettled(
                            virtualPageIndex = targetVirtual,
                            expectedCoordinateRevision = originRevision,
                        )
                    }
                } else {
                    continuousScrollAnchor = targetAnchor
                    continuousScrollProgress = 0f
                    dispatchContinuousScrollJump(
                        cid = targetCid,
                        paragraphIndex = targetAnchor.paragraphIndex,
                        animated = false,
                    )
                    val meta = snapshot.orderedToc.firstOrNull { it.cid == targetCid }
                    windowBuffer.openChapter(
                        cid = targetCid,
                        initialAnchor = targetAnchor,
                        layoutConfig = currentLayoutConfig,
                        fallbackVid = meta?.vid ?: launchRequest.vid,
                        fallbackVolumeTitle = meta?.volumeTitle ?: launchRequest.volumeTitle,
                        fallbackChapterTitle = meta?.chapterTitle ?: launchRequest.chapterTitle,
                    )
                }
            }
        }

        val activeChapterAst = remember(snapshot.currentSlot) {
            (snapshot.currentSlot as? ChapterWindowSlot.Loaded)?.layout?.ast
        }

        val currentIllustrations = remember(activeChapterAst) {
            activeChapterAst?.illustrationBlocks?.map { it.imageUrl }.orEmpty()
        }

        fun openArtbookGallery(
            cid: Int,
            chapterTitle: String,
            urls: List<String>,
            clickedIdx: Int,
            isAutoEnteredForChapter: Boolean = false,
        ) {
            if (urls.isEmpty()) return
            val safeIdx = clickedIdx.coerceIn(0, urls.lastIndex)
            activeArtbookState = ReaderActiveArtbookState(
                cid = cid,
                chapterTitle = chapterTitle,
                imageUrls = urls,
                initialIndex = safeIdx,
                entryMode = if (isAutoEnteredForChapter) {
                    ReaderArtbookEntryMode.PURE_ILLUSTRATION_CHAPTER
                } else {
                    ReaderArtbookEntryMode.INLINE_CHAPTER_GALLERY
                },
            )
            if (!isAutoEnteredForChapter) {
                onIllustrationClicked(chapterTitle, urls, safeIdx)
            }
        }

        val sameVolumeIllustrationChapters = remember(
            snapshot.activeChapter?.vid,
            snapshot.activeChapter?.cid,
            snapshot.activeChapter?.isIllustrationChapter,
            activeChapterAst?.isPureIllustrationContent,
            snapshot.orderedToc,
        ) {
            val activeMeta = snapshot.activeChapter
            if (activeMeta == null ||
                activeMeta.isIllustrationChapter ||
                activeChapterAst?.isPureIllustrationContent == true
            ) {
                emptyList()
            } else {
                snapshot.orderedToc.filter { entry ->
                    entry.vid == activeMeta.vid &&
                        entry.cid != activeMeta.cid &&
                        entry.isIllustrationChapter
                }
            }
        }

        fun openSameVolumeIllustrationsPeek() {
            if (isPeekingVolumeIllustrationsLoading || sameVolumeIllustrationChapters.isEmpty()) {
                return
            }
            val targetChapters = sameVolumeIllustrationChapters
            val firstIllus = targetChapters.first()
            val targetVid = firstIllus.vid
            val baseLabel = "${firstIllus.volumeTitle} · ${firstIllus.chapterTitle}"
                .trim(' ', '·')
                .ifBlank { firstIllus.chapterTitle }
            val peekTitle = context.getString(R.string.artbook_temporary_peek_title_format, baseLabel)

            val cachedUrls = cachedVolumePeekUrlsByVid[targetVid]
            if (!cachedUrls.isNullOrEmpty()) {
                val rememberedIdx = (lastVolumePeekIndexByVid[targetVid] ?: 0)
                    .coerceIn(0, cachedUrls.lastIndex)
                isHudVisible = false
                activeArtbookState = ReaderActiveArtbookState(
                    cid = firstIllus.cid,
                    volumeVid = targetVid,
                    chapterTitle = peekTitle,
                    imageUrls = cachedUrls,
                    initialIndex = rememberedIdx,
                    entryMode = ReaderArtbookEntryMode.TEMPORARY_VOLUME_PEEK,
                )
                return
            }

            coroutineScope.launch {
                isPeekingVolumeIllustrationsLoading = true
                try {
                    val result = windowBuffer.loadVolumeIllustrationUrlsForPeek(targetChapters)
                    val urls = result.getOrNull().orEmpty()
                    if (urls.isNotEmpty()) {
                        cachedVolumePeekUrlsByVid[targetVid] = urls
                        val rememberedIdx = (lastVolumePeekIndexByVid[targetVid] ?: 0)
                            .coerceIn(0, urls.lastIndex)
                        isHudVisible = false
                        activeArtbookState = ReaderActiveArtbookState(
                            cid = firstIllus.cid,
                            volumeVid = targetVid,
                            chapterTitle = peekTitle,
                            imageUrls = urls,
                            initialIndex = rememberedIdx,
                            entryMode = ReaderArtbookEntryMode.TEMPORARY_VOLUME_PEEK,
                        )
                    } else {
                        val errMsg = result.exceptionOrNull()?.localizedMessage?.takeIf { it.isNotBlank() }
                            ?: "未能在该插图章节中解析到图片"
                        snackbarHostState.showSnackbar(errMsg)
                    }
                } finally {
                    isPeekingVolumeIllustrationsLoading = false
                }
            }
        }

        // Automatically open the full-screen illustration gallery (`ArtbookLightboxOverlay`) when
        // the active chapter body consists purely of illustrations (no prose paragraphs), while
        // remembering per-chapter `"小说阅读"` overrides so the user can toggle back in 1 tap.
        LaunchedEffect(
            activeChapterAst?.cid,
            activeChapterAst?.isPureIllustrationContent,
            launchRequest.startWithTts,
        ) {
            val ast = activeChapterAst ?: return@LaunchedEffect
            if (lastAutoOpenedGalleryCid != 0 && ast.cid != lastAutoOpenedGalleryCid) {
                lastAutoOpenedGalleryCid = 0
            }
            if (ast.isPureIllustrationContent &&
                !launchRequest.startWithTts &&
                ast.cid !in novelModeOverriddenCids &&
                lastAutoOpenedGalleryCid != ast.cid
            ) {
                val urls = ast.illustrationBlocks.map { it.imageUrl }
                if (urls.isNotEmpty()) {
                    lastAutoOpenedGalleryCid = ast.cid
                    val resumedImgIdx = snapshot.activeReaderPage?.elements?.firstNotNullOfOrNull { el ->
                        (el as? ReaderPageElement.ImageSlot)?.imageIndexInChapter
                    } ?: 0
                    val resolvedTitle = snapshot.activeChapter?.chapterTitle
                        ?.takeIf { it.isNotBlank() }
                        ?: ast.chapterTitle.ifBlank { launchRequest.chapterTitle }
                    openArtbookGallery(
                        cid = ast.cid,
                        chapterTitle = resolvedTitle,
                        urls = urls,
                        clickedIdx = resumedImgIdx,
                        isAutoEnteredForChapter = true,
                    )
                }
            }
        }

        // Honor `ReaderLaunchRequest.startWithTts` from NovelDetailScreen's `"朗读本章 (TTS)"` action,
        // and continue automatically into the next chapter when an utterance stream finishes.
        var didAutoStartInitialTts by remember(launchRequest.aid, launchRequest.cid, launchRequest.startWithTts) {
            mutableStateOf(false)
        }
        var pendingTtsStartOnCid by remember { mutableIntStateOf(0) }

        LaunchedEffect(activeChapterAst, launchRequest.startWithTts, pendingTtsStartOnCid) {
            val ast = activeChapterAst ?: return@LaunchedEffect
            if (launchRequest.startWithTts && !didAutoStartInitialTts && ast.cid == launchRequest.cid) {
                didAutoStartInitialTts = true
                val startBlock =
                    if (preferences.readerPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                        continuousScrollAnchor.paragraphIndex
                    } else {
                        snapshot.activeReaderPage?.startAnchor?.paragraphIndex ?: 0
                    }
                ttsController.startChapter(
                    ast = ast,
                    startFromBlockIndex = startBlock,
                    appLanguage = latestAppLang,
                )
            } else if (pendingTtsStartOnCid != 0 && ast.cid == pendingTtsStartOnCid) {
                pendingTtsStartOnCid = 0
                ttsController.startChapter(
                    ast = ast,
                    startFromBlockIndex = 0,
                    appLanguage = latestAppLang,
                )
            }
        }

        LaunchedEffect(pendingTtsAutoContinueAfterCid) {
            val finishedCid = pendingTtsAutoContinueAfterCid
            if (finishedCid == 0) return@LaunchedEffect
            pendingTtsAutoContinueAfterCid = 0
            val curIdx = snapshot.orderedToc.indexOfFirst { it.cid == finishedCid }
            val nextCid = snapshot.nextSlot?.chapter?.cid
                ?: if (curIdx >= 0 && curIdx + 1 < snapshot.orderedToc.size) {
                    snapshot.orderedToc[curIdx + 1].cid
                } else {
                    null
                }
            if (nextCid != null) {
                pendingTtsStartOnCid = nextCid
                continuousScrollAnchor = ReadingAnchor()
                continuousScrollProgress = 0f
                dispatchContinuousScrollJump(cid = nextCid, paragraphIndex = 0, animated = false)
                val meta = snapshot.orderedToc.firstOrNull { it.cid == nextCid }
                windowBuffer.openChapter(
                    cid = nextCid,
                    initialAnchor = ReadingAnchor(),
                    layoutConfig = currentLayoutConfig,
                    fallbackVid = meta?.vid ?: launchRequest.vid,
                    fallbackVolumeTitle = meta?.volumeTitle ?: launchRequest.volumeTitle,
                    fallbackChapterTitle = meta?.chapterTitle ?: launchRequest.chapterTitle,
                )
            } else {
                ttsController.stopAndDismiss()
                snackbarHostState.showSnackbar("本章已朗读完毕")
            }
        }

        // Automatically flip pages or scroll to follow the currently active TTS paragraph across ALL 6 modes!
        LaunchedEffect(
            ttsUiState.isSessionOpen,
            ttsUiState.activeCid,
            ttsUiState.activeBlockIndex,
            preferences.readerPageTurnMode,
            (snapshot.currentSlot as? ChapterWindowSlot.Loaded)?.chapter?.cid,
        ) {
            if (!ttsUiState.isSessionOpen || ttsUiState.totalUtterances <= 0 || ttsUiState.activeBlockIndex < 0) {
                return@LaunchedEffect
            }
            val loadedCur = snapshot.currentSlot as? ChapterWindowSlot.Loaded ?: return@LaunchedEffect
            if (loadedCur.chapter.cid != ttsUiState.activeCid) return@LaunchedEffect
            val spokenAnchor = ReadingAnchor(paragraphIndex = ttsUiState.activeBlockIndex, charOffset = 0)
            if (preferences.readerPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                dispatchContinuousScrollJump(
                    cid = ttsUiState.activeCid,
                    paragraphIndex = ttsUiState.activeBlockIndex,
                    animated = !isEinkMode,
                )
                windowBuffer.syncActiveAnchorInCurrentChapter(spokenAnchor)
            } else {
                val originRevision = snapshot.coordinateRevision
                val targetLocalPage = loadedCur.layout.resolvePageIndexForAnchor(spokenAnchor)
                val targetVirtual = snapshot.prevPageCount + targetLocalPage
                if (targetVirtual in 0 until snapshot.totalVirtualPages &&
                    (targetLocalPage != snapshot.activePageIndexInCurrentChapter ||
                        pagerState.currentPage != targetVirtual)
                ) {
                    val instantFlip = isEinkMode ||
                        preferences.readerPageTurnMode == ReaderPageTurnMode.EINK_INSTANT
                    isProgrammaticPagerSnap = true
                    try {
                        if (instantFlip) {
                            pagerState.scrollToPage(targetVirtual)
                        } else {
                            runCatching { pagerState.animateScrollToPage(targetVirtual) }
                                .onFailure { pagerState.scrollToPage(targetVirtual) }
                        }
                    } finally {
                        isProgrammaticPagerSnap = false
                    }
                    windowBuffer.onVirtualPageSettled(
                        virtualPageIndex = targetVirtual,
                        expectedCoordinateRevision = originRevision,
                    )
                }
            }
        }

        val contentHighlight = remember(ttsUiState, activeFocalHighlight) {
            val showTts = ttsUiState.isSessionOpen &&
                ttsUiState.totalUtterances > 0 &&
                ttsUiState.activeBlockIndex >= 0
            ReaderContentHighlight(
                ttsCid = if (showTts) ttsUiState.activeCid else 0,
                ttsBlockIndex = if (showTts) ttsUiState.activeBlockIndex else -1,
                focalCid = activeFocalHighlight?.cid ?: 0,
                focalBlockIndex = activeFocalHighlight?.blockIndex ?: -1,
                focalStartChar = activeFocalHighlight?.startChar ?: -1,
                focalEndCharExclusive = activeFocalHighlight?.endCharExclusive ?: -1,
            )
        }

        val currentChapterBookmark = remember(
            novelBookmarks,
            snapshot.activeChapter?.cid,
            snapshot.activeReaderPage?.startAnchor,
            snapshot.activeReaderPage?.endAnchor,
            preferences.readerPageTurnMode,
            continuousScrollAnchor,
        ) {
            val curCid = snapshot.activeChapter?.cid ?: return@remember null
            if (preferences.readerPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                novelBookmarks.firstOrNull { note ->
                    note.cid == curCid && note.startParagraph == continuousScrollAnchor.paragraphIndex
                }
            } else {
                val curPage = snapshot.activeReaderPage ?: return@remember null
                novelBookmarks.firstOrNull { note ->
                    note.cid == curCid &&
                        ReadingAnchor(note.startParagraph, note.startChar) >= curPage.startAnchor &&
                        ReadingAnchor(note.startParagraph, note.startChar) <= curPage.endAnchor
                }
            }
        }

        ReaderViewportHost(
            pageTurnMode = preferences.readerPageTurnMode,
            snapshot = snapshot,
            pagerState = pagerState,
            palette = palette,
            fontSizeSp = liveFontSizeSp,
            lineDistanceDp = liveLineDistanceDp,
            paragraphDistanceDp = liveParagraphDistanceDp,
            pageEdgeDistanceDp = livePageEdgeDistanceDp,
            leftEdgeBrightnessDragEnabled = preferences.readerEdgeBrightnessDragEnabled,
            contentHighlight = contentHighlight,
            continuousScrollJumpCommand = continuousScrollJumpCommand,
            onTapZoneAction = { action ->
                if (isTypographyPanelOpen) {
                    activeDraggingSlider = null
                    dragLockedAnchor = null
                    isTypographyPanelOpen = false
                } else if (isHudVisible && action != ReaderTapZoneAction.TOGGLE_HUD) {
                    isHudVisible = false
                } else {
                    when (action) {
                        ReaderTapZoneAction.PREV_PAGE -> stepPageBy(-1)
                        ReaderTapZoneAction.NEXT_PAGE -> stepPageBy(+1)
                        ReaderTapZoneAction.TOGGLE_HUD -> isHudVisible = !isHudVisible
                    }
                }
            },
            onBrightnessDragDelta = { delta ->
                val updated = (brightnessFraction + delta).coerceIn(0.04f, 1f)
                brightnessFraction = updated
                showBrightnessPill = true
                brightnessDragTick += 1L
                context.findHostActivity()?.window?.let { win ->
                    val attrs = win.attributes
                    attrs.screenBrightness = updated
                    win.attributes = attrs
                }
            },
            onIllustrationClicked = { clickedUrl, clickedIdx ->
                val urls = if (currentIllustrations.isNotEmpty()) {
                    currentIllustrations
                } else {
                    listOf(clickedUrl)
                }
                openArtbookGallery(
                    cid = snapshot.activeChapter?.cid ?: launchRequest.cid,
                    chapterTitle = snapshot.activeChapter?.chapterTitle ?: launchRequest.chapterTitle,
                    urls = urls,
                    clickedIdx = clickedIdx,
                )
            },
            onContinuousScrollAnchorChanged = { scrollAnchor, progressFraction ->
                continuousScrollAnchor = scrollAnchor
                continuousScrollProgress = progressFraction
                val activeChapter = snapshot.activeChapter
                if (activeChapter != null) {
                    coroutineScope.launch {
                        windowBuffer.syncActiveAnchorInCurrentChapter(scrollAnchor)
                        persistReadingProgress(activeChapter, scrollAnchor)
                    }
                }
            },
            onRequestNextChapterInContinuousMode = {
                snapshot.nextSlot?.chapter?.cid?.let { nextCid ->
                    jumpToChapterWithUndo(nextCid)
                }
            },
            onRequestPrevChapterInContinuousMode = {
                snapshot.prevSlot?.chapter?.cid?.let { prevCid ->
                    jumpToChapterWithUndo(prevCid)
                }
            },
            onRetryFailedSlot = { failedCid ->
                coroutineScope.launch { windowBuffer.retrySlot(failedCid) }
            },
            onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
            modifier = Modifier.windowInsetsPadding(WindowInsets.displayCutout),
        )

        // Floating brightness indicator pill (`24..72dp` left-edge drag feedback).
        AnimatedVisibility(
            visible = showBrightnessPill,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 18.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.90f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier
                    .einkHairlineBorder(CircleShape)
                    .testTag(ReaderTestTags.BRIGHTNESS_PILL),
            ) {
                Text(
                    text = "☀ 屏幕亮度 ${(brightnessFraction * 100f).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }

        // Top HUD Bar (hidden while dragging a live-reflow slider).
        AnimatedVisibility(
            visible = isHudVisible && activeDraggingSlider == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (isEinkMode) 1f else 0.95f),
                contentColor = MaterialTheme.colorScheme.onSurface,
                tonalElevation = 3.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkHairlineBorder(MaterialTheme.shapes.extraSmall)
                    .testTag(ReaderTestTags.HUD_TOP_BAR),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .statusBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    val backLabel = stringResource(R.string.action_back)
                    TooltipIconButton(
                        tooltipText = backLabel,
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag(ReaderTestTags.HUD_BACK_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = backLabel,
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp),
                    ) {
                        Text(
                            text = launchRequest.novelTitle,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val chapterSubtitle = snapshot.activeChapter?.let {
                            "${it.volumeTitle} · ${it.chapterTitle}".trim(' ', '·')
                        } ?: launchRequest.chapterTitle
                        Text(
                            text = chapterSubtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    if (currentIllustrations.isNotEmpty()) {
                        AssistChip(
                            onClick = {
                                val curImgIdx = snapshot.activeReaderPage?.elements?.firstNotNullOfOrNull { el ->
                                    (el as? ReaderPageElement.ImageSlot)?.imageIndexInChapter
                                } ?: 0
                                isHudVisible = false
                                openArtbookGallery(
                                    cid = snapshot.activeChapter?.cid ?: launchRequest.cid,
                                    chapterTitle = snapshot.activeChapter?.chapterTitle ?: launchRequest.chapterTitle,
                                    urls = currentIllustrations,
                                    clickedIdx = curImgIdx,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Collections,
                                    contentDescription = null,
                                )
                            },
                            label = { Text("插图 (${currentIllustrations.size})") },
                            modifier = Modifier
                                .padding(end = 2.dp)
                                .testTag(ReaderTestTags.HUD_VIEW_ILLUSTRATIONS_CHIP),
                        )
                    }

                    if (sameVolumeIllustrationChapters.isNotEmpty()) {
                        val peekLabel = stringResource(R.string.reader_action_peek_volume_illustrations)
                        val peekTooltip = stringResource(R.string.reader_action_peek_volume_illustrations_tooltip)
                        val peekLoadingLabel = stringResource(R.string.reader_peek_volume_illustrations_loading)
                        if (currentIllustrations.isEmpty()) {
                            ActionTooltipBox(tooltipText = peekTooltip) {
                                AssistChip(
                                    onClick = { openSameVolumeIllustrationsPeek() },
                                    enabled = !isPeekingVolumeIllustrationsLoading,
                                    leadingIcon = {
                                        if (isPeekingVolumeIllustrationsLoading) {
                                            CircularProgressIndicator(
                                                strokeWidth = 2.dp,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Outlined.PhotoLibrary,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        }
                                    },
                                    label = {
                                        Text(
                                            text = if (isPeekingVolumeIllustrationsLoading) {
                                                peekLoadingLabel
                                            } else {
                                                peekLabel
                                            },
                                        )
                                    },
                                    modifier = Modifier
                                        .padding(end = 2.dp)
                                        .testTag(ReaderTestTags.HUD_PEEK_VOLUME_ILLUSTRATIONS_BUTTON),
                                )
                            }
                        } else {
                            TooltipIconButton(
                                tooltipText = peekTooltip,
                                enabled = !isPeekingVolumeIllustrationsLoading,
                                onClick = { openSameVolumeIllustrationsPeek() },
                                modifier = Modifier.testTag(ReaderTestTags.HUD_PEEK_VOLUME_ILLUSTRATIONS_BUTTON),
                            ) {
                                if (isPeekingVolumeIllustrationsLoading) {
                                    CircularProgressIndicator(
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.PhotoLibrary,
                                        contentDescription = peekLabel,
                                    )
                                }
                            }
                        }
                    }

                    val searchInBookLabel = stringResource(R.string.reader_action_search_in_book)
                    TooltipIconButton(
                        tooltipText = searchInBookLabel,
                        onClick = { isSearchSheetOpen = true },
                        modifier = Modifier.testTag(ReaderTestTags.HUD_SEARCH_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = searchInBookLabel,
                        )
                    }

                    val bookmarkToggleLabel = if (currentChapterBookmark != null) {
                        stringResource(R.string.reader_action_remove_bookmark)
                    } else {
                        stringResource(R.string.reader_action_add_bookmark)
                    }
                    TooltipIconButton(
                        tooltipText = bookmarkToggleLabel,
                        onClick = {
                            val curChapter = snapshot.activeChapter ?: return@TooltipIconButton
                            val curPage = snapshot.activeReaderPage
                            val isScrollMode =
                                preferences.readerPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL
                            if (!isScrollMode && curPage == null) return@TooltipIconButton
                            coroutineScope.launch {
                                if (currentChapterBookmark != null) {
                                    val removedNote = currentChapterBookmark
                                    if (activeFocalHighlight?.cid == removedNote.cid &&
                                        activeFocalHighlight?.blockIndex == removedNote.startParagraph
                                    ) {
                                        activeFocalHighlight = null
                                    }
                                    withContext(ioDispatcher) {
                                        database.bookmarkNoteDao()
                                            .deleteBookmarkNoteById(removedNote.id)
                                    }
                                    snackbarHostState.showSnackbar("已移除当前页书签")
                                } else {
                                    val startParagraph: Int
                                    val startChar: Int
                                    val endParagraph: Int
                                    val endChar: Int
                                    val snippet: String
                                    if (isScrollMode) {
                                        startParagraph = continuousScrollAnchor.paragraphIndex
                                        startChar = continuousScrollAnchor.charOffset
                                        endParagraph = startParagraph
                                        endChar = startChar
                                        val blockText = activeChapterAst?.blocks
                                            ?.firstOrNull { it.blockIndex >= startParagraph }
                                            ?.let { blk ->
                                                when (blk) {
                                                    is ChapterBlock.TitleBlock -> blk.title
                                                    is ChapterBlock.ParagraphBlock -> blk.formattedText.trim('\u3000')
                                                    is ChapterBlock.IllustrationBlock -> null
                                                }
                                            }
                                            ?.takeIf { it.isNotBlank() }
                                        snippet = blockText?.take(72) ?: curChapter.chapterTitle
                                    } else {
                                        val page = curPage ?: return@launch
                                        startParagraph = page.startAnchor.paragraphIndex
                                        startChar = page.startAnchor.charOffset
                                        endParagraph = page.endAnchor.paragraphIndex
                                        endChar = page.endAnchor.charOffset
                                        snippet = page.elements.firstNotNullOfOrNull { el ->
                                            when (el) {
                                                is ReaderPageElement.TextLine -> el.text.trim('\u3000')
                                                is ReaderPageElement.VerticalColumn -> el.verticalText.trim('\u3000')
                                                is ReaderPageElement.TitleLine -> el.text
                                                else -> null
                                            }?.takeIf { it.isNotBlank() }
                                        }?.take(72) ?: curChapter.chapterTitle
                                    }

                                    activeFocalHighlight = ActiveFocalHighlightState(
                                        cid = curChapter.cid,
                                        blockIndex = startParagraph,
                                    )

                                    withContext(ioDispatcher) {
                                        if (database.novelDao().getSyncHeader(launchRequest.aid) == null) {
                                            database.novelDao().upsertNovel(
                                                NovelEntity(
                                                    aid = launchRequest.aid,
                                                    contentLang = latestAppLang.name,
                                                    title = launchRequest.novelTitle.ifBlank { "AID #${launchRequest.aid}" },
                                                    metaSyncedAtMs = System.currentTimeMillis(),
                                                ),
                                            )
                                        }
                                        database.bookmarkNoteDao().upsertBookmarkNote(
                                            BookmarkNoteEntity(
                                                aid = launchRequest.aid,
                                                vid = curChapter.vid,
                                                cid = curChapter.cid,
                                                chapterTitle = "${curChapter.volumeTitle} · ${curChapter.chapterTitle}".trim(' ', '·'),
                                                startParagraph = startParagraph,
                                                startChar = startChar,
                                                endParagraph = endParagraph,
                                                endChar = endChar,
                                                snippetText = snippet,
                                                styleType = BookmarkNoteEntity.STYLE_BOOKMARK,
                                                createdAtMs = System.currentTimeMillis(),
                                            ),
                                        )
                                    }
                                    snackbarHostState.showSnackbar("已添加书签")
                                }
                            }
                        },
                        modifier = Modifier.testTag(ReaderTestTags.HUD_BOOKMARK_TOGGLE),
                    ) {
                        Icon(
                            imageVector = if (currentChapterBookmark != null) {
                                Icons.Outlined.Bookmark
                            } else {
                                Icons.Outlined.BookmarkBorder
                            },
                            contentDescription = bookmarkToggleLabel,
                            tint = if (currentChapterBookmark != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }

                    val networkDiagLabel = stringResource(R.string.reader_action_network_diagnostics)
                    TooltipIconButton(
                        tooltipText = networkDiagLabel,
                        onClick = onOpenNetworkDiagnostics,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.NetworkCheck,
                            contentDescription = networkDiagLabel,
                        )
                    }
                }
            }
        }

        // Bottom HUD: Snackbar + TTS Playback Bar + Undo Pill + Scrubber + M3e HorizontalFloatingToolbar OR Typography Studio!
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(bottom = 8.dp),
            )

            if (ttsUiState.isSessionOpen && activeDraggingSlider == null && !isTypographyPanelOpen) {
                ReaderTtsFloatingBar(
                    state = ttsUiState,
                    onTogglePlayPause = { ttsController.togglePlayPause(latestAppLang) },
                    onSkipPrevious = { ttsController.skipPrevious(latestAppLang) },
                    onSkipNext = { ttsController.skipNext(latestAppLang) },
                    onCycleSpeechRate = { ttsController.cycleSpeechRate(latestAppLang) },
                    onStopAndDismiss = { ttsController.stopAndDismiss() },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            // 5-second `"↩ 还原至第 N 页"` pill after scrubbing or jumping chapters.
            val activeUndo = undoJumpState
            if (activeUndo != null && activeDraggingSlider == null) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .einkHairlineBorder(CircleShape)
                        .clickable {
                            val targetUndo = activeUndo
                            undoJumpState = null
                            activeFocalHighlight = null
                            coroutineScope.launch {
                                if (snapshot.activeChapter?.cid == targetUndo.cid) {
                                    if (preferences.readerPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                                        continuousScrollAnchor = targetUndo.anchor
                                        dispatchContinuousScrollJump(
                                            cid = targetUndo.cid,
                                            paragraphIndex = targetUndo.anchor.paragraphIndex,
                                            animated = false,
                                        )
                                        windowBuffer.syncActiveAnchorInCurrentChapter(targetUndo.anchor)
                                        snapshot.activeChapter?.let { ch ->
                                            persistReadingProgress(ch, targetUndo.anchor)
                                        }
                                    } else {
                                        val originRevision = snapshot.coordinateRevision
                                        val resolvedIdx = (snapshot.currentSlot as? ChapterWindowSlot.Loaded)
                                            ?.layout
                                            ?.resolvePageIndexForAnchor(targetUndo.anchor)
                                            ?: (targetUndo.pageIndexOneBased - 1).coerceAtLeast(0)
                                        val targetVirtual = snapshot.prevPageCount + resolvedIdx
                                        isProgrammaticPagerSnap = true
                                        try {
                                            pagerState.scrollToPage(targetVirtual)
                                        } finally {
                                            isProgrammaticPagerSnap = false
                                        }
                                        windowBuffer.onVirtualPageSettled(
                                            virtualPageIndex = targetVirtual,
                                            expectedCoordinateRevision = originRevision,
                                        )
                                    }
                                } else {
                                    continuousScrollAnchor = targetUndo.anchor
                                    dispatchContinuousScrollJump(
                                        cid = targetUndo.cid,
                                        paragraphIndex = targetUndo.anchor.paragraphIndex,
                                        animated = false,
                                    )
                                    val meta = snapshot.orderedToc.firstOrNull { it.cid == targetUndo.cid }
                                    windowBuffer.openChapter(
                                        cid = targetUndo.cid,
                                        initialAnchor = targetUndo.anchor,
                                        layoutConfig = currentLayoutConfig,
                                        fallbackVid = meta?.vid ?: launchRequest.vid,
                                        fallbackVolumeTitle = meta?.volumeTitle ?: launchRequest.volumeTitle,
                                        fallbackChapterTitle = meta?.chapterTitle ?: launchRequest.chapterTitle,
                                    )
                                }
                            }
                        }
                        .testTag(ReaderTestTags.UNDO_JUMP_PILL),
                ) {
                    Text(
                        text = "↩ 还原至第 ${activeUndo.pageIndexOneBased} 页",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }

            if (isTypographyPanelOpen) {
                ReaderTypographyReflowOverlay(
                    fontSizeSp = liveFontSizeSp,
                    lineDistanceDp = liveLineDistanceDp,
                    paragraphDistanceDp = liveParagraphDistanceDp,
                    pageEdgeDistanceDp = livePageEdgeDistanceDp,
                    activeDraggingSlider = activeDraggingSlider,
                    selectedPaperPreset = preferences.readerPaperPreset,
                    hasCustomFont = preferences.readerCustomFontPath.isNotBlank(),
                    hasCustomDayBackground = preferences.readerCustomBackgroundPath.isNotBlank(),
                    volumeKeyPagingEnabled = preferences.volumeKeyPagingEnabled,
                    keepScreenOnEnabled = preferences.readerKeepScreenOn,
                    edgeBrightnessDragEnabled = preferences.readerEdgeBrightnessDragEnabled,
                    canImportCustomAssets = storageAndCacheRepository != null,
                    onSliderDragValueChanged = { field, newValue ->
                        if (activeDraggingSlider != field) {
                            activeDraggingSlider = field
                            dragLockedAnchor =
                                if (preferences.readerPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                                    continuousScrollAnchor
                                } else {
                                    snapshot.activeReaderPage?.startAnchor ?: ReadingAnchor()
                                }
                        }
                        when (field) {
                            ReaderTypographySliderField.FONT_SIZE -> liveFontSizeSp = newValue
                            ReaderTypographySliderField.LINE_DISTANCE -> liveLineDistanceDp = newValue
                            ReaderTypographySliderField.PARAGRAPH_DISTANCE -> liveParagraphDistanceDp = newValue
                            ReaderTypographySliderField.PAGE_EDGE -> livePageEdgeDistanceDp = newValue
                        }
                    },
                    onSliderDragFinished = {
                        activeDraggingSlider = null
                        dragLockedAnchor = null
                        onReaderTypographyChanged(
                            liveFontSizeSp,
                            liveLineDistanceDp,
                            liveParagraphDistanceDp,
                            livePageEdgeDistanceDp,
                        )
                    },
                    onSelectPaperPreset = applyPaperPresetAndSyncGlobalTheme,
                    onPickCustomFont = {
                        customFontPicker.launch(arrayOf("font/*", "application/octet-stream", "*/*"))
                    },
                    onResetCustomFont = { onReaderCustomFontPathChanged("") },
                    onPickCustomBackground = {
                        customBackgroundPicker.launch(arrayOf("image/*"))
                    },
                    onResetCustomBackground = { onReaderCustomBackgroundPathChanged("") },
                    onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
                    onKeepScreenOnChanged = onReaderKeepScreenOnChanged,
                    onEdgeBrightnessDragChanged = onReaderEdgeBrightnessDragChanged,
                )
            } else if (isHudVisible) {
                // Chapter Scrubber Card + M3e HorizontalFloatingToolbar
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                            alpha = if (isEinkMode) 1f else 0.95f,
                        ),
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .einkHairlineBorder(MaterialTheme.shapes.large),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        TextButton(
                            enabled = snapshot.prevSlot != null,
                            onClick = {
                                snapshot.prevSlot?.chapter?.cid?.let { jumpToChapterWithUndo(it) }
                            },
                            modifier = Modifier.testTag(ReaderTestTags.HUD_PREV_CHAPTER_BUTTON),
                        ) {
                            Text("上一章")
                        }

                        if (preferences.readerPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                            val scrollBlocks = activeChapterAst?.blocks.orEmpty()
                            val totalBlocks = scrollBlocks.size.coerceAtLeast(1)
                            val curBlockPos = scrollBlocks
                                .indexOfFirst { it.blockIndex >= continuousScrollAnchor.paragraphIndex }
                                .let { if (it >= 0) it else 0 }
                            var scrubBlockPreviewPos by remember(curBlockPos, totalBlocks) {
                                mutableFloatStateOf(curBlockPos.toFloat())
                            }
                            val previewPosInt = scrubBlockPreviewPos.roundToInt().coerceIn(0, totalBlocks - 1)
                            val previewPct = if (totalBlocks <= 1) {
                                100
                            } else {
                                ((previewPosInt.toFloat() / (totalBlocks - 1).toFloat()) * 100f)
                                    .roundToInt()
                                    .coerceIn(0, 100)
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "阅读进度 $previewPct% (${previewPosInt + 1} / $totalBlocks 段)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = scrubBlockPreviewPos,
                                    enabled = totalBlocks > 1,
                                    valueRange = 0f..(totalBlocks - 1).coerceAtLeast(1).toFloat(),
                                    onValueChange = { raw ->
                                        scrubBlockPreviewPos = raw
                                    },
                                    onValueChangeFinished = {
                                        val targetPos = scrubBlockPreviewPos.roundToInt()
                                            .coerceIn(0, totalBlocks - 1)
                                        val targetBlock = scrollBlocks.getOrNull(targetPos)
                                        if (targetBlock != null && targetPos != curBlockPos) {
                                            activeFocalHighlight = null
                                            val prevChapter = snapshot.activeChapter
                                            if (prevChapter != null) {
                                                undoJumpState = ReaderUndoJumpState(
                                                    cid = prevChapter.cid,
                                                    pageIndexOneBased = snapshot.activePageIndexInCurrentChapter + 1,
                                                    anchor = continuousScrollAnchor,
                                                    createdAtElapsedMs = SystemClock.elapsedRealtime(),
                                                )
                                            }
                                            val permille = if (totalBlocks <= 1) {
                                                1000
                                            } else {
                                                ((targetPos.toFloat() / (totalBlocks - 1).toFloat()) * 1000f)
                                                    .roundToInt()
                                                    .coerceIn(0, 1000)
                                            }
                                            val targetAnchor = ReadingAnchor(
                                                paragraphIndex = targetBlock.blockIndex,
                                                charOffset = 0,
                                                progressPermille = permille,
                                            )
                                            continuousScrollAnchor = targetAnchor
                                            continuousScrollProgress = permille / 1000f
                                            dispatchContinuousScrollJump(
                                                cid = activeChapterAst?.cid ?: launchRequest.cid,
                                                paragraphIndex = targetBlock.blockIndex,
                                                animated = false,
                                            )
                                            coroutineScope.launch {
                                                windowBuffer.syncActiveAnchorInCurrentChapter(targetAnchor)
                                                snapshot.activeChapter?.let { ch ->
                                                    persistReadingProgress(ch, targetAnchor)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag(ReaderTestTags.HUD_SCRUBBER_SLIDER),
                                )
                            }
                        } else {
                            val totalPagesInCur = snapshot.currentPageCount.coerceAtLeast(1)
                            val curPageZeroBased = snapshot.activePageIndexInCurrentChapter
                            var scrubPreviewIndex by remember(curPageZeroBased, totalPagesInCur) {
                                mutableFloatStateOf(curPageZeroBased.toFloat())
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = "第 ${scrubPreviewIndex.roundToInt() + 1} / $totalPagesInCur 页",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Slider(
                                    value = scrubPreviewIndex,
                                    enabled = totalPagesInCur > 1,
                                    valueRange = 0f..(totalPagesInCur - 1).coerceAtLeast(1).toFloat(),
                                    onValueChange = { raw ->
                                        scrubPreviewIndex = raw
                                    },
                                    onValueChangeFinished = {
                                        val targetLocalPage = scrubPreviewIndex.roundToInt()
                                            .coerceIn(0, totalPagesInCur - 1)
                                        if (targetLocalPage != curPageZeroBased) {
                                            activeFocalHighlight = null
                                            val prevChapter = snapshot.activeChapter
                                            val prevPage = snapshot.activeReaderPage
                                            if (prevChapter != null && prevPage != null) {
                                                undoJumpState = ReaderUndoJumpState(
                                                    cid = prevChapter.cid,
                                                    pageIndexOneBased = curPageZeroBased + 1,
                                                    anchor = prevPage.toBookmarkOrProgressAnchor(),
                                                    createdAtElapsedMs = SystemClock.elapsedRealtime(),
                                                )
                                            }
                                            val originRevision = snapshot.coordinateRevision
                                            val targetVirtual = snapshot.prevPageCount + targetLocalPage
                                            coroutineScope.launch {
                                                isProgrammaticPagerSnap = true
                                                try {
                                                    pagerState.scrollToPage(targetVirtual)
                                                } finally {
                                                    isProgrammaticPagerSnap = false
                                                }
                                                windowBuffer.onVirtualPageSettled(
                                                    virtualPageIndex = targetVirtual,
                                                    expectedCoordinateRevision = originRevision,
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.testTag(ReaderTestTags.HUD_SCRUBBER_SLIDER),
                                )
                            }
                        }

                        TextButton(
                            enabled = snapshot.nextSlot != null,
                            onClick = {
                                snapshot.nextSlot?.chapter?.cid?.let { jumpToChapterWithUndo(it) }
                            },
                            modifier = Modifier.testTag(ReaderTestTags.HUD_NEXT_CHAPTER_BUTTON),
                        ) {
                            Text("下一章")
                        }
                    }
                }

                HorizontalFloatingToolbar(
                    expanded = true,
                    colors = FloatingToolbarDefaults.standardFloatingToolbarColors(),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .einkHairlineBorder(FloatingToolbarDefaults.ContainerShape)
                        .testTag(ReaderTestTags.HUD_FLOATING_TOOLBAR),
                ) {
                    ReaderToolbarActionButton(
                        icon = Icons.AutoMirrored.Outlined.FormatListBulleted,
                        label = "目录",
                        tag = ReaderTestTags.ACTION_TOC,
                        onClick = { isTocSheetOpen = true },
                    )
                    ReaderToolbarActionButton(
                        icon = Icons.Outlined.TextFields,
                        label = "排版",
                        tag = ReaderTestTags.ACTION_TYPOGRAPHY,
                        onClick = { isTypographyPanelOpen = true },
                    )
                    ReaderToolbarActionButton(
                        icon = Icons.Outlined.AutoStories,
                        label = "模式",
                        tag = ReaderTestTags.ACTION_PAGE_MODE,
                        onClick = { isPageModeSheetOpen = true },
                    )
                    ReaderToolbarActionButton(
                        icon = Icons.Outlined.BookmarkBorder,
                        label = "书签",
                        tag = ReaderTestTags.ACTION_BOOKMARKS,
                        onClick = { isBookmarksSheetOpen = true },
                    )
                    ReaderToolbarActionButton(
                        icon = Icons.Outlined.GraphicEq,
                        label = if (ttsUiState.isSpeaking) "朗读中" else "朗读",
                        tag = ReaderTestTags.ACTION_TTS,
                        onClick = {
                            if (ttsUiState.isSessionOpen) {
                                ttsController.togglePlayPause(latestAppLang)
                            } else {
                                val ast = activeChapterAst
                                if (ast != null) {
                                    val startBlock =
                                        if (preferences.readerPageTurnMode == ReaderPageTurnMode.CONTINUOUS_SCROLL) {
                                            continuousScrollAnchor.paragraphIndex
                                        } else {
                                            snapshot.activeReaderPage?.startAnchor?.paragraphIndex ?: 0
                                        }
                                    isHudVisible = false
                                    ttsController.startChapter(
                                        ast = ast,
                                        startFromBlockIndex = startBlock,
                                        appLanguage = latestAppLang,
                                    )
                                }
                            }
                        },
                    )
                    val isCurrentlyNight = preferences.readerPaperPreset.isDark
                    ReaderToolbarActionButton(
                        icon = if (isCurrentlyNight) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                        label = if (isCurrentlyNight) "日间" else "夜间",
                        tag = ReaderTestTags.ACTION_DAY_NIGHT,
                        onClick = {
                            val nextPreset = if (isCurrentlyNight) {
                                preferences.lastDayPaperPreset.takeIf { !it.isDark }
                                    ?: ReaderPaperPreset.PARCHMENT
                            } else {
                                ReaderPaperPreset.CARBON_NIGHT
                            }
                            applyPaperPresetAndSyncGlobalTheme(nextPreset)
                        },
                    )
                }
            }
        }

        // Volume & Chapter TOC ModalBottomSheet
        if (isTocSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isTocSheetOpen = false },
                modifier = Modifier.testTag(ReaderTestTags.TOC_SHEET),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "章节目录 (${snapshot.orderedToc.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        items(
                            items = snapshot.orderedToc,
                            key = { it.cid },
                        ) { entry ->
                            val isCurrent = entry.cid == snapshot.activeChapter?.cid
                            val isCached = entry.cid in cachedChapterIds
                            Surface(
                                color = if (isCurrent) {
                                    MaterialTheme.colorScheme.secondaryContainer
                                } else {
                                    Color.Transparent
                                },
                                shape = MaterialTheme.shapes.medium,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        isTocSheetOpen = false
                                        isHudVisible = false
                                        if (!isCurrent) {
                                            jumpToChapterWithUndo(entry.cid)
                                        }
                                    }
                                    .testTag("${ReaderTestTags.TOC_CHAPTER_ITEM_PREFIX}${entry.cid}"),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = entry.volumeTitle,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = entry.chapterTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        )
                                    }
                                    if (entry.isIllustrationChapter) {
                                        Text(
                                            text = "🖼 插图",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.padding(end = 8.dp),
                                        )
                                    }
                                    if (isCached) {
                                        Text(
                                            text = "已缓存",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // 6 Page-Turn Modes Selector ModalBottomSheet
        if (isPageModeSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isPageModeSheetOpen = false },
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "翻页引擎模式",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    ReaderPageTurnMode.entries.forEach { mode ->
                        val selected = preferences.readerPageTurnMode == mode
                        Surface(
                            color = if (selected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                Color.Transparent
                            },
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .einkHairlineBorder(MaterialTheme.shapes.medium)
                                .clickable {
                                    if (mode == ReaderPageTurnMode.CONTINUOUS_SCROLL &&
                                        preferences.readerPageTurnMode != ReaderPageTurnMode.CONTINUOUS_SCROLL
                                    ) {
                                        val curAnchor = snapshot.activeReaderPage?.startAnchor ?: ReadingAnchor()
                                        continuousScrollAnchor = curAnchor
                                        snapshot.activeChapter?.cid?.let { curCid ->
                                            dispatchContinuousScrollJump(
                                                cid = curCid,
                                                paragraphIndex = curAnchor.paragraphIndex,
                                                animated = false,
                                            )
                                        }
                                    }
                                    onReaderPageTurnModeChanged(mode)
                                    isPageModeSheetOpen = false
                                }
                                .testTag("${ReaderTestTags.PAGE_MODE_ITEM_PREFIX}${mode.name}"),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                RadioButton(selected = selected, onClick = null)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column {
                                    Text(
                                        text = mode.displayTitle,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Text(
                                        text = mode.shortSubtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        // Bookmarks & Notes Sheet (`BOOKMARKS_NOTES` quick jump & delete)
        if (isBookmarksSheetOpen) {
            ModalBottomSheet(
                onDismissRequest = { isBookmarksSheetOpen = false },
                modifier = Modifier.testTag(ReaderTestTags.BOOKMARKS_SHEET),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "本书书签 (${novelBookmarks.size})",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                    if (novelBookmarks.isEmpty()) {
                        Text(
                            text = "暂无书签，点击顶部工具栏书签图标即可随时记录阅读锚点。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    } else {
                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            itemsIndexed(
                                items = novelBookmarks,
                                key = { _, note -> note.id },
                            ) { index, note ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .einkHairlineBorder(MaterialTheme.shapes.medium)
                                        .clickable {
                                            isBookmarksSheetOpen = false
                                            isHudVisible = false
                                            activeFocalHighlight = ActiveFocalHighlightState(
                                                cid = note.cid,
                                                blockIndex = note.startParagraph,
                                            )
                                            jumpToChapterWithUndo(
                                                targetCid = note.cid,
                                                targetAnchor = ReadingAnchor(
                                                    paragraphIndex = note.startParagraph,
                                                    charOffset = note.startChar,
                                                ),
                                                clearFocalHighlight = false,
                                            )
                                        }
                                        .testTag("${ReaderTestTags.BOOKMARK_ITEM_PREFIX}$index"),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(12.dp),
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = note.chapterTitle,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = note.snippetText,
                                                style = MaterialTheme.typography.bodyMedium,
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                        val deleteBookmarkLabel = stringResource(R.string.footprint_bookmarks_delete_desc)
                                        TooltipIconButton(
                                            tooltipText = deleteBookmarkLabel,
                                            onClick = {
                                                if (activeFocalHighlight?.cid == note.cid &&
                                                    activeFocalHighlight?.blockIndex == note.startParagraph
                                                ) {
                                                    activeFocalHighlight = null
                                                }
                                                coroutineScope.launch(ioDispatcher) {
                                                    database.bookmarkNoteDao().deleteBookmarkNoteById(note.id)
                                                }
                                            },
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Delete,
                                                contentDescription = deleteBookmarkLabel,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // In-Book Search Sheet (`当前章节` & `已缓存全书`)
        if (isSearchSheetOpen) {
            ReaderInBookSearchSheet(
                activeChapterAst = activeChapterAst,
                orderedToc = snapshot.orderedToc,
                cachedChapterIds = cachedChapterIds,
                chapterContentDao = database.chapterContentDao(),
                ioDispatcher = ioDispatcher,
                onJumpToResult = { hit ->
                    isSearchSheetOpen = false
                    isHudVisible = false
                    activeFocalHighlight = ActiveFocalHighlightState(
                        cid = hit.cid,
                        blockIndex = hit.match.blockIndex,
                        startChar = hit.match.startCharOffset,
                        endCharExclusive = hit.match.endCharOffsetExclusive,
                    )
                    jumpToChapterWithUndo(
                        targetCid = hit.cid,
                        targetAnchor = ReadingAnchor(
                            paragraphIndex = hit.match.blockIndex,
                            charOffset = hit.match.startCharOffset,
                        ),
                        clearFocalHighlight = false,
                    )
                },
                onDismiss = { isSearchSheetOpen = false },
            )
        }

        // Full-Screen Artbook Lightbox (`BitmapRegionDecoder` 4x Tiling + `跨页拼合` Spreads)
        val openArtbook = activeArtbookState
        if (openArtbook != null) {
            val appAssetRoots = remember(context) {
                (context.applicationContext as? Wenku8V2Application)
                    ?.appContainer
                    ?.resolveLocalAssetRoots()
                    ?: listOf(context.filesDir)
            }
            val isTempPeek = openArtbook.entryMode == ReaderArtbookEntryMode.TEMPORARY_VOLUME_PEEK
            val isAutoEntered =
                openArtbook.entryMode == ReaderArtbookEntryMode.PURE_ILLUSTRATION_CHAPTER
            val isPureIllustrationChapter = !isTempPeek &&
                activeChapterAst?.cid == openArtbook.cid &&
                activeChapterAst.isPureIllustrationContent
            val canShowChapterControls = !isTempPeek && (isAutoEntered || isPureIllustrationChapter)
            val prevChapterCid = snapshot.prevSlot?.chapter?.cid
            val nextChapterCid = snapshot.nextSlot?.chapter?.cid
            ArtbookLightboxOverlay(
                novelTitle = launchRequest.novelTitle,
                chapterTitle = openArtbook.chapterTitle,
                imageUrls = openArtbook.imageUrls,
                initialImageIndex = openArtbook.initialIndex,
                isAutoEnteredIllustrationChapter = isAutoEntered,
                isTemporaryPeek = isTempPeek,
                onPrimaryImageIndexChanged = if (isTempPeek) {
                    { idx -> lastVolumePeekIndexByVid[openArtbook.volumeVid] = idx }
                } else {
                    null
                },
                onSwitchToNovelReadingMode = if (canShowChapterControls) {
                    { currentImgIdx ->
                        if (openArtbook.cid > 0) {
                            novelModeOverriddenCids =
                                (novelModeOverriddenCids + openArtbook.cid).distinct().toIntArray()
                        }
                        activeArtbookState = null
                        val loadedCur = snapshot.currentSlot as? ChapterWindowSlot.Loaded
                        if (loadedCur != null && loadedCur.chapter.cid == openArtbook.cid) {
                            val targetLocalPage = loadedCur.layout.pages.indexOfFirst { page ->
                                page.elements.any { el ->
                                    el is ReaderPageElement.ImageSlot &&
                                        el.imageIndexInChapter == currentImgIdx
                                }
                            }
                            if (targetLocalPage >= 0 && targetLocalPage != snapshot.activePageIndexInCurrentChapter) {
                                val originRevision = snapshot.coordinateRevision
                                val targetVirtual = snapshot.prevPageCount + targetLocalPage
                                if (targetVirtual in 0 until snapshot.totalVirtualPages) {
                                    coroutineScope.launch {
                                        isProgrammaticPagerSnap = true
                                        try {
                                            pagerState.scrollToPage(targetVirtual)
                                        } finally {
                                            isProgrammaticPagerSnap = false
                                        }
                                        windowBuffer.onVirtualPageSettled(
                                            virtualPageIndex = targetVirtual,
                                            expectedCoordinateRevision = originRevision,
                                        )
                                    }
                                }
                            }
                        }
                    }
                } else {
                    null
                },
                onPreviousChapter = if (canShowChapterControls && prevChapterCid != null) {
                    {
                        activeArtbookState = null
                        jumpToChapterWithUndo(prevChapterCid)
                    }
                } else {
                    null
                },
                onNextChapter = if (canShowChapterControls && nextChapterCid != null) {
                    {
                        activeArtbookState = null
                        jumpToChapterWithUndo(nextChapterCid)
                    }
                } else {
                    null
                },
                assetSearchRoots = appAssetRoots,
                imageFileResolver = artbookImageFileResolver,
                ioDispatcher = ioDispatcher,
                onDismiss = {
                    activeArtbookState = null
                    if (isAutoEntered) {
                        ttsController.stopAndDismiss()
                        onNavigateBack()
                    }
                },
            )
        }
    }
}

@Composable
private fun ReaderToolbarActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tag: String,
    onClick: () -> Unit,
) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        modifier = Modifier.testTag(tag),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(imageVector = icon, contentDescription = label)
            Text(text = label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * Typography & Paper Studio featuring the **Opaque Active-Slider Reflow Capsule**
 * (`10-next-gen-reader-engine-architecture.md` §6):
 *
 * When [activeDraggingSlider] is non-null, all non-active rows hide completely and ONLY the active
 * slider renders inside a **`92%`-opaque `surfaceContainerHighest` pill capsule** so the reader
 * watches live Bunkobon text reflow underneath with zero text-on-text overlap!
 */
@Composable
private fun ReaderTypographyReflowOverlay(
    fontSizeSp: Int,
    lineDistanceDp: Int,
    paragraphDistanceDp: Int,
    pageEdgeDistanceDp: Int,
    activeDraggingSlider: ReaderTypographySliderField?,
    selectedPaperPreset: ReaderPaperPreset,
    hasCustomFont: Boolean,
    hasCustomDayBackground: Boolean,
    volumeKeyPagingEnabled: Boolean,
    keepScreenOnEnabled: Boolean,
    edgeBrightnessDragEnabled: Boolean,
    canImportCustomAssets: Boolean,
    onSliderDragValueChanged: (ReaderTypographySliderField, Int) -> Unit,
    onSliderDragFinished: () -> Unit,
    onSelectPaperPreset: (ReaderPaperPreset) -> Unit,
    onPickCustomFont: () -> Unit,
    onResetCustomFont: () -> Unit,
    onPickCustomBackground: () -> Unit,
    onResetCustomBackground: () -> Unit,
    onVolumeKeyPagingChanged: (Boolean) -> Unit,
    onKeepScreenOnChanged: (Boolean) -> Unit,
    onEdgeBrightnessDragChanged: (Boolean) -> Unit,
) {
    val isEinkMode = LocalEinkMode.current
    val isCapsuleMode = activeDraggingSlider != null
    val panelScrollState = rememberScrollState()
    val containerShape = if (isCapsuleMode) CircleShape else MaterialTheme.shapes.extraLarge
    val containerColor = if (isCapsuleMode) {
        MaterialTheme.colorScheme.surfaceContainerHighest.copy(
            alpha = if (isEinkMode) 1f else 0.92f,
        )
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = if (isEinkMode) 1f else 0.96f,
        )
    }

    val sliderDefinitions = listOf(
        ReaderTypographySliderField.FONT_SIZE to SliderSpec(
            value = fontSizeSp,
            min = Wenku8UserPreferences.MIN_READER_FONT_SIZE_SP,
            max = Wenku8UserPreferences.MAX_READER_FONT_SIZE_SP,
            unit = "sp",
            tag = ReaderTestTags.SLIDER_FONT_SIZE,
        ),
        ReaderTypographySliderField.LINE_DISTANCE to SliderSpec(
            value = lineDistanceDp,
            min = Wenku8UserPreferences.MIN_READER_LINE_DISTANCE_DP,
            max = Wenku8UserPreferences.MAX_READER_LINE_DISTANCE_DP,
            unit = "dp",
            tag = ReaderTestTags.SLIDER_LINE_DISTANCE,
        ),
        ReaderTypographySliderField.PARAGRAPH_DISTANCE to SliderSpec(
            value = paragraphDistanceDp,
            min = Wenku8UserPreferences.MIN_READER_PARAGRAPH_DISTANCE_DP,
            max = Wenku8UserPreferences.MAX_READER_PARAGRAPH_DISTANCE_DP,
            unit = "dp",
            tag = ReaderTestTags.SLIDER_PARAGRAPH_DISTANCE,
        ),
        ReaderTypographySliderField.PAGE_EDGE to SliderSpec(
            value = pageEdgeDistanceDp,
            min = Wenku8UserPreferences.MIN_READER_PAGE_EDGE_DISTANCE_DP,
            max = Wenku8UserPreferences.MAX_READER_PAGE_EDGE_DISTANCE_DP,
            unit = "dp",
            tag = ReaderTestTags.SLIDER_PAGE_EDGE,
        ),
    )

    Surface(
        shape = containerShape,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = if (isCapsuleMode) 6.dp else 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (isCapsuleMode) Modifier else Modifier.heightIn(max = 460.dp))
            .einkHairlineBorder(containerShape)
            .testTag(
                if (isCapsuleMode) {
                    ReaderTestTags.ACTIVE_SLIDER_OPAQUE_CAPSULE
                } else {
                    ReaderTestTags.TYPOGRAPHY_PANEL
                },
            ),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(if (isCapsuleMode) 4.dp else 10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(panelScrollState, enabled = !isCapsuleMode)
                .padding(
                    horizontal = if (isCapsuleMode) 20.dp else 16.dp,
                    vertical = if (isCapsuleMode) 10.dp else 16.dp,
                ),
        ) {
            if (!isCapsuleMode) {
                Text(
                    text = "排版与纸张工作室",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                )

                // Paper preset swatches
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                ) {
                    ReaderPaperPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedPaperPreset == preset,
                            onClick = { onSelectPaperPreset(preset) },
                            label = { Text(preset.displayTitle) },
                            modifier = Modifier.testTag("${ReaderTestTags.PAPER_PRESET_CHIP_PREFIX}${preset.name}"),
                        )
                    }
                }
            }

            sliderDefinitions.forEach { (field, spec) ->
                androidx.compose.runtime.key(field) {
                    if (activeDraggingSlider == null || activeDraggingSlider == field) {
                        TypographySliderRow(
                            label = if (isCapsuleMode) {
                                "${field.label} ${spec.value} ${spec.unit}"
                            } else {
                                "${field.label} (${spec.value} ${spec.unit})"
                            },
                            value = spec.value,
                            min = spec.min,
                            max = spec.max,
                            sliderTag = spec.tag,
                            isCapsuleMode = isCapsuleMode,
                            onValueChange = { onSliderDragValueChanged(field, it) },
                            onValueChangeFinished = onSliderDragFinished,
                        )
                    }
                }
            }

            if (!isCapsuleMode) {
                HorizontalDivider()

                // Custom Font & Custom Day Background buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        enabled = canImportCustomAssets,
                        onClick = onPickCustomFont,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (hasCustomFont) "更换字体 (.ttf/.otf)" else "导入自定义字体")
                    }
                    if (hasCustomFont) {
                        TextButton(onClick = onResetCustomFont) {
                            Text("默认字体")
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    OutlinedButton(
                        enabled = canImportCustomAssets,
                        onClick = onPickCustomBackground,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(if (hasCustomDayBackground) "更换日间背景图" else "自定义日间背景")
                    }
                    if (hasCustomDayBackground) {
                        TextButton(onClick = onResetCustomBackground) {
                            Text("清除背景")
                        }
                    }
                }

                HorizontalDivider()

                ReaderToggleSettingRow(
                    title = "音量键翻页",
                    subtitle = "单步不连跳，自动拦截系统音量弹窗",
                    checked = volumeKeyPagingEnabled,
                    switchTag = ReaderTestTags.SWITCH_VOLUME_KEYS,
                    onCheckedChange = onVolumeKeyPagingChanged,
                )
                ReaderToggleSettingRow(
                    title = "阅读时屏幕常亮",
                    subtitle = "仅在阅读器界面内保持屏幕唤醒",
                    checked = keepScreenOnEnabled,
                    switchTag = ReaderTestTags.SWITCH_KEEP_SCREEN_ON,
                    onCheckedChange = onKeepScreenOnChanged,
                )
                ReaderToggleSettingRow(
                    title = "左侧边缘上下滑动调节亮度",
                    subtitle = "避让 0–24dp 系统预测性返回手势区 (24–72dp)",
                    checked = edgeBrightnessDragEnabled,
                    switchTag = ReaderTestTags.SWITCH_EDGE_BRIGHTNESS,
                    onCheckedChange = onEdgeBrightnessDragChanged,
                )
            }
        }
    }
}

@Composable
private fun TypographySliderRow(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    sliderTag: String,
    isCapsuleMode: Boolean,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (isCapsuleMode) FontWeight.Bold else FontWeight.Normal,
            )
            if (!isCapsuleMode) {
                Row {
                    TextButton(
                        enabled = value > min,
                        onClick = {
                            onValueChange((value - 1).coerceAtLeast(min))
                            onValueChangeFinished()
                        },
                    ) {
                        Text("−")
                    }
                    TextButton(
                        enabled = value < max,
                        onClick = {
                            onValueChange((value + 1).coerceAtMost(max))
                            onValueChangeFinished()
                        },
                    ) {
                        Text("+")
                    }
                }
            }
        }
        Slider(
            value = value.toFloat(),
            valueRange = min.toFloat()..max.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            onValueChangeFinished = onValueChangeFinished,
            modifier = Modifier.testTag(sliderTag),
        )
    }
}

@Composable
private fun ReaderToggleSettingRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    switchTag: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(switchTag),
        )
    }
}

private data class SliderSpec(
    val value: Int,
    val min: Int,
    val max: Int,
    val unit: String,
    val tag: String,
)

private fun Context.findHostActivity(): Activity? {
    var cur: Context? = this
    while (cur is ContextWrapper) {
        if (cur is Activity) return cur
        cur = cur.baseContext
    }
    return null
}

private const val MILLIS_PER_DAY = 86_400_000L
