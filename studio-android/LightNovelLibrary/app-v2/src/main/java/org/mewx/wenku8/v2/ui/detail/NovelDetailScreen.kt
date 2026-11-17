package org.mewx.wenku8.v2.ui.detail

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Chat
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.DownloadDone
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.BitmapImage
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.R
import org.mewx.wenku8.v2.data.download.NovelDownloadMode
import org.mewx.wenku8.v2.data.download.NovelDownloadProgress
import org.mewx.wenku8.v2.data.download.NovelDownloadRepository
import org.mewx.wenku8.v2.data.download.NovelDownloadRequest
import org.mewx.wenku8.v2.data.download.NovelDownloadRunState
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.media.WenkuImageRequests
import org.mewx.wenku8.v2.data.repository.BookshelfRepository
import org.mewx.wenku8.v2.data.repository.ChapterRowModel
import org.mewx.wenku8.v2.data.repository.NovelDetailRefreshOutcome
import org.mewx.wenku8.v2.data.repository.NovelDetailRepository
import org.mewx.wenku8.v2.data.repository.NovelDetailSnapshot
import org.mewx.wenku8.v2.data.repository.VolumeAccordionModel
import org.mewx.wenku8.v2.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.v2.ui.discovery.formatBookStatusLabel
import org.mewx.wenku8.v2.ui.theme.CoverThemeEngine
import org.mewx.wenku8.v2.ui.theme.LocalEinkMode
import org.mewx.wenku8.v2.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.mewx.wenku8.v2.ui.theme.einkHairlineBorder
import org.mewx.wenku8.v2.ui.theme.wenku8SharedNovelCover
import org.mewx.wenku8.v2.ui.theme.wenku8SharedNovelTitle
import java.util.Locale

object NovelDetailTestTags {
    const val SCREEN_ROOT = "novel_detail_screen_root"
    const val BACK_BUTTON = "novel_detail_back_button"
    const val SHARE_BUTTON = "novel_detail_share_button"
    const val REFRESH_BUTTON = "novel_detail_refresh_button"
    const val NOVEL_TITLE_TEXT = "novel_detail_title_text"
    const val TITLE_DIALOG = "novel_detail_title_dialog"
    const val TITLE_DIALOG_COPY_BUTTON = "novel_detail_title_dialog_copy"
    const val AUTHOR_TRIGGER = "novel_detail_author_trigger"
    const val TAG_CHIP_PREFIX = "novel_detail_tag_"
    const val READ_LEADING_BUTTON = "novel_detail_read_leading_button"
    const val READ_TRAILING_BUTTON = "novel_detail_read_trailing_button"
    const val MENU_READ_BEGINNING = "novel_detail_menu_read_beginning"
    const val MENU_READ_LATEST = "novel_detail_menu_read_latest"
    const val MENU_READ_TTS = "novel_detail_menu_read_tts"
    const val SHELF_TOGGLE_BUTTON = "novel_detail_shelf_toggle_button"
    const val DOWNLOAD_OPEN_BUTTON = "novel_detail_download_open_button"
    const val REVIEWS_OPEN_BUTTON = "novel_detail_reviews_open_button"
    const val SYNOPSIS_TOGGLE_BUTTON = "novel_detail_synopsis_toggle_button"
    const val OFFLINE_PRESERVED_BANNER = "novel_detail_offline_preserved_banner"
    const val ACTIVE_DOWNLOAD_BANNER = "novel_detail_active_download_banner"
    const val CANCEL_DOWNLOAD_BUTTON = "novel_detail_cancel_download_button"
    const val SERVER_SUPPORT_AD_CARD = "novel_detail_server_support_ad_card"
    const val SERVER_SUPPORT_AD_SLOT = "novel_detail_server_support_ad_slot"
    const val TOC_SEARCH_TOGGLE = "novel_detail_toc_search_toggle"
    const val TOC_SEARCH_INPUT = "novel_detail_toc_search_input"
    const val TOC_ORDER_TOGGLE = "novel_detail_toc_order_toggle"
    const val VOLUME_HEADER_PREFIX = "novel_detail_volume_header_"
    const val VOLUME_ILLUS_CHIP_PREFIX = "novel_detail_volume_illus_chip_"
    const val CHAPTER_ROW_PREFIX = "novel_detail_chapter_row_"
    const val DOWNLOADER_SHEET = "novel_detail_downloader_sheet"
    const val DOWNLOAD_MODE_PREFIX = "novel_detail_download_mode_"
    const val DOWNLOAD_VOLUME_CHECK_PREFIX = "novel_detail_download_vol_"
    const val DOWNLOAD_OVERWRITE_SWITCH = "novel_detail_download_overwrite_switch"
    const val DOWNLOAD_START_BUTTON = "novel_detail_download_start_button"
}

data class ReaderLaunchRequest(
    val aid: Int,
    val vid: Int,
    val cid: Int,
    val novelTitle: String,
    val volumeTitle: String,
    val chapterTitle: String,
    val startWithTts: Boolean = false,
    val initialParagraphIndex: Int = -1,
    val initialCharOffset: Int = 0,
)

/**
 * Full Material 3 Expressive (`M3e`) `NovelDetailScreen` (`05-screen-by-screen-ui-specifications.md` §Screen 5).
 *
 * Scopes [CoverThemeEngine] strictly to this screen (bypassed automatically in `LocalEinkMode`),
 * separates the primary reading `SplitButtonLayout` from the 3 secondary actions (`已在书架 / 加入书架`,
 * `离线缓存`, `书评`), embeds the single Zero-CLS `ServerSupportBannerAd` between Synopsis and Volume
 * Accordions, and surfaces 1-tap `🖼 插图章节` chips on collapsed volume rows.
 */
@OptIn(
    ExperimentalLayoutApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun NovelDetailScreen(
    aid: Int,
    novelDetailRepository: NovelDetailRepository,
    bookshelfRepository: BookshelfRepository,
    novelDownloadRepository: NovelDownloadRepository,
    appLanguage: Wenku8API.AppLanguage,
    themeMode: Wenku8ThemeMode,
    useCoverTheme: Boolean = true,
    seedDiscoveryItem: DiscoveryNovelCardItem? = null,
    sharedTransitionOrigin: String = Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN,
    showBackButton: Boolean = true,
    onNavigateBack: () -> Unit = {},
    onOpenReader: (ReaderLaunchRequest) -> Unit = {},
    onOpenReviews: (aid: Int, novelTitle: String) -> Unit = { _, _ -> },
    onAuthorClicked: (String) -> Unit = {},
    onTagClicked: (String) -> Unit = {},
    isNetworkAvailableForAdOverride: Boolean? = null,
    isGmsAvailableForAdOverride: Boolean? = null,
    enableLiveAdViewInflation: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (showBackButton) {
        BackHandler(onBack = onNavigateBack)
    }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isEinkMode = LocalEinkMode.current
    val systemDark = isSystemInDarkTheme()
    val isDarkEffective = themeMode.isDarkEffective(systemDark)

    val initialCoverUrl = remember(aid, seedDiscoveryItem, novelDetailRepository) {
        seedDiscoveryItem?.coverUrl?.takeIf { it.isNotBlank() }
            ?: novelDetailRepository.resolveCoverUrl(aid)
    }
    val detailFlow = remember(aid, novelDetailRepository) {
        novelDetailRepository.observeNovelDetail(aid)
    }
    val snapshot by detailFlow.collectAsStateWithLifecycle(
        initialValue = NovelDetailSnapshot(
            aid = aid,
            novel = seedDiscoveryItem?.let {
                NovelEntity(
                    aid = it.aid,
                    title = it.title,
                    author = it.author,
                    bookStatus = it.bookStatus,
                    lastUpdate = it.lastUpdate,
                    tagsCsv = it.tags.joinToString(" "),
                    introShort = it.introShort,
                )
            },
            coverUrl = initialCoverUrl,
            volumes = emptyList(),
            readingProgress = null,
            totalCachedChapters = 0,
            totalChapters = 0,
            tags = seedDiscoveryItem?.tags.orEmpty(),
        ),
    )

    val activeDownloadsMap by novelDownloadRepository.activeDownloadsFlow.collectAsStateWithLifecycle()
    val currentDownloadProgress = activeDownloadsMap[aid]

    var isRefreshing by rememberSaveable(aid) { mutableStateOf(false) }
    var showOfflinePreservedBanner by rememberSaveable(aid) { mutableStateOf(false) }
    var showFullTitleDialog by rememberSaveable(aid) { mutableStateOf(false) }
    var showReadDropdownMenu by remember { mutableStateOf(false) }
    var isSynopsisExpanded by rememberSaveable(aid) { mutableStateOf(false) }
    var isChapterFilterVisible by rememberSaveable(aid) { mutableStateOf(false) }
    var chapterFilterQuery by rememberSaveable(aid) { mutableStateOf("") }
    var isReverseVolumeOrder by rememberSaveable(aid) { mutableStateOf(false) }
    var isDownloaderSheetOpen by rememberSaveable(aid) { mutableStateOf(false) }

    // Auto-expand either the volume containing reading progress or the first volume on initial load.
    var expandedVolumeIds by rememberSaveable(aid) { mutableStateOf<List<Int>>(emptyList()) }
    var initializedDefaultAccordion by rememberSaveable(aid) { mutableStateOf(false) }

    LaunchedEffect(aid, snapshot.volumes.size, snapshot.readingProgress?.vid) {
        if (!initializedDefaultAccordion && snapshot.volumes.isNotEmpty()) {
            val targetVid = snapshot.readingProgress?.vid
                ?.takeIf { progressVid -> snapshot.volumes.any { it.vid == progressVid } }
                ?: snapshot.volumes.first().vid
            expandedVolumeIds = listOf(targetVid)
            initializedDefaultAccordion = true
        }
    }

    var coverSeedColor by remember(aid) {
        mutableStateOf(CoverThemeEngine.deterministicSeedColorForAid(aid))
    }

    val triggerRefresh: () -> Unit = {
        coroutineScope.launch {
            isRefreshing = true
            try {
                when (
                    novelDetailRepository.refreshNovelDetail(
                        aid = aid,
                        appLanguage = appLanguage,
                        fallbackSeedItem = seedDiscoveryItem,
                    )
                ) {
                    NovelDetailRefreshOutcome.Success -> {
                        showOfflinePreservedBanner = false
                    }
                    NovelDetailRefreshOutcome.OfflineOrCachedOnly -> {
                        showOfflinePreservedBanner = true
                    }
                    NovelDetailRefreshOutcome.ApiNotConfigured,
                    is NovelDetailRefreshOutcome.Failed,
                    -> Unit
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    LaunchedEffect(aid, appLanguage) {
        novelDetailRepository.acknowledgeUnreadUpdateIfPresent(aid)
        triggerRefresh()
    }

    val doneFormat = stringResource(R.string.download_done_snackbar_format)
    val partialFormat = stringResource(R.string.download_partial_snackbar_format)
    val cancelledText = stringResource(R.string.download_cancelled_snackbar)

    LaunchedEffect(currentDownloadProgress?.state) {
        val dl = currentDownloadProgress ?: return@LaunchedEffect
        when (dl.state) {
            NovelDownloadRunState.SUCCEEDED -> {
                snackbarHostState.showSnackbar(
                    String.format(Locale.getDefault(), doneFormat, dl.completedChapters),
                )
                novelDownloadRepository.clearFinishedStatus(aid)
            }
            NovelDownloadRunState.PARTIAL_FAILURE -> {
                snackbarHostState.showSnackbar(
                    String.format(
                        Locale.getDefault(),
                        partialFormat,
                        dl.completedChapters,
                        dl.failedChapters,
                    ),
                )
                novelDownloadRepository.clearFinishedStatus(aid)
            }
            NovelDownloadRunState.CANCELLED -> {
                snackbarHostState.showSnackbar(cancelledText)
                novelDownloadRepository.clearFinishedStatus(aid)
            }
            NovelDownloadRunState.FAILED -> {
                novelDownloadRepository.clearFinishedStatus(aid)
            }
            NovelDownloadRunState.RUNNING -> Unit
        }
    }

    val baseScheme = MaterialTheme.colorScheme
    val effectiveScheme = remember(baseScheme, coverSeedColor, isDarkEffective, isEinkMode, useCoverTheme) {
        if (useCoverTheme && !isEinkMode) {
            CoverThemeEngine.createScopedCoverColorScheme(
                baseColorScheme = baseScheme,
                coverSeedColor = coverSeedColor,
                isDarkTheme = isDarkEffective,
                isEinkMode = false,
            )
        } else {
            baseScheme
        }
    }

    val displayTitle = snapshot.novel?.title?.ifBlank { null }
        ?: seedDiscoveryItem?.title?.ifBlank { null }
        ?: "AID #$aid"
    val displayAuthor = snapshot.novel?.author?.ifBlank { null }
        ?: seedDiscoveryItem?.author.orEmpty()
    val inBookshelf = snapshot.novel?.inBookshelf == true

    val launchReaderSnackbarTemplate = stringResource(R.string.detail_reader_launch_snackbar_format)
    val invokeReaderTarget: (
        vid: Int,
        cid: Int,
        volumeTitle: String,
        chapterTitle: String,
        tts: Boolean,
        initialParagraphIndex: Int,
    ) -> Unit = { vid, cid, volTitle, chapTitle, tts, initialParaIdx ->
        val req = ReaderLaunchRequest(
            aid = aid,
            vid = vid,
            cid = cid,
            novelTitle = displayTitle,
            volumeTitle = volTitle,
            chapterTitle = chapTitle,
            startWithTts = tts,
            initialParagraphIndex = initialParaIdx,
        )
        onOpenReader(req)
        coroutineScope.launch {
            snackbarHostState.showSnackbar(
                String.format(
                    Locale.getDefault(),
                    launchReaderSnackbarTemplate,
                    "$volTitle · $chapTitle".trim(' ', '·'),
                ),
            )
        }
    }

    val filteredAndOrderedVolumes = remember(
        snapshot.volumes,
        chapterFilterQuery,
        isReverseVolumeOrder,
    ) {
        val baseOrdered = if (isReverseVolumeOrder) {
            snapshot.volumes.asReversed()
        } else {
            snapshot.volumes
        }
        val cleanFilter = chapterFilterQuery.trim()
        if (cleanFilter.isEmpty()) {
            baseOrdered
        } else {
            baseOrdered.mapNotNull { vol ->
                if (vol.volumeTitle.contains(cleanFilter, ignoreCase = true)) {
                    vol
                } else {
                    val matchedChapters = vol.chapters.filter {
                        it.chapterTitle.contains(cleanFilter, ignoreCase = true)
                    }
                    if (matchedChapters.isEmpty()) null else vol.copy(chapters = matchedChapters)
                }
            }
        }
    }

    MaterialTheme(
        colorScheme = effectiveScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .testTag(NovelDetailTestTags.SCREEN_ROOT),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.detail_top_bar_title_format, aid),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        if (showBackButton) {
                            IconButton(
                                onClick = onNavigateBack,
                                modifier = Modifier.testTag(NovelDetailTestTags.BACK_BUTTON),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    actions = {
                        val shareMessage = stringResource(
                            R.string.detail_share_text_format,
                            displayTitle,
                            displayAuthor.ifBlank { "-" },
                            aid,
                        )
                        IconButton(
                            onClick = { shareNovelExternally(context, shareMessage) },
                            modifier = Modifier.testTag(NovelDetailTestTags.SHARE_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = stringResource(R.string.detail_action_share),
                            )
                        }
                        IconButton(
                            onClick = triggerRefresh,
                            enabled = !isRefreshing,
                            modifier = Modifier.testTag(NovelDetailTestTags.REFRESH_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = stringResource(R.string.detail_action_refresh),
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag(NovelDetailTestTags.SCREEN_ROOT + "_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (isRefreshing) {
                    item(key = "detail_refresh_progress") {
                        if (isEinkMode) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        } else {
                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }

                if (showOfflinePreservedBanner) {
                    item(key = "detail_offline_banner") {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(NovelDetailTestTags.OFFLINE_PRESERVED_BANNER)
                                .einkHairlineBorder(MaterialTheme.shapes.medium),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = stringResource(R.string.detail_offline_preserved_banner),
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { showOfflinePreservedBanner = false }) {
                                    Icon(imageVector = Icons.Outlined.Close, contentDescription = null)
                                }
                            }
                        }
                    }
                }

                // 1. Editorial Hero Header with CoverThemeEngine ambient glow & shared-element morph
                item(key = "detail_hero_header") {
                    NovelHeroHeaderCard(
                        aid = aid,
                        title = displayTitle,
                        author = displayAuthor,
                        novel = snapshot.novel,
                        appLanguage = appLanguage,
                        coverUrl = snapshot.coverUrl.ifBlank { initialCoverUrl },
                        tags = snapshot.tags,
                        isEinkMode = isEinkMode,
                        sharedTransitionOrigin = sharedTransitionOrigin,
                        onCoverSeedExtracted = { extracted -> coverSeedColor = extracted },
                        onTitleClicked = { showFullTitleDialog = true },
                        onAuthorClicked = {
                            if (displayAuthor.isNotBlank()) onAuthorClicked(displayAuthor)
                        },
                        onTagClicked = onTagClicked,
                    )
                }

                // 2. 4-Column Stat Strip
                item(key = "detail_stats_strip") {
                    NovelStatsStrip(novel = snapshot.novel)
                }

                // 3. Primary Reading-Only SplitButtonLayout + Secondary 3-Action Row
                item(key = "detail_action_deck") {
                    NovelActionDeck(
                        snapshot = snapshot,
                        inBookshelf = inBookshelf,
                        activeDownload = currentDownloadProgress?.takeIf {
                            it.state == NovelDownloadRunState.RUNNING
                        },
                        showReadDropdownMenu = showReadDropdownMenu,
                        onShowReadDropdownMenuChange = { showReadDropdownMenu = it },
                        onResumeOrStartReading = {
                            val prog = snapshot.readingProgress
                            if (prog != null && prog.cid > 0) {
                                invokeReaderTarget(
                                    prog.vid,
                                    prog.cid,
                                    prog.volumeTitle,
                                    prog.chapterTitle,
                                    false,
                                    -1,
                                )
                            } else {
                                val firstVol = snapshot.volumes.firstOrNull { it.chapters.isNotEmpty() }
                                val firstChap = firstVol?.chapters?.firstOrNull()
                                if (firstVol != null && firstChap != null) {
                                    invokeReaderTarget(
                                        firstVol.vid,
                                        firstChap.cid,
                                        firstVol.volumeTitle,
                                        firstChap.chapterTitle,
                                        false,
                                        0,
                                    )
                                }
                            }
                        },
                        onStartFromBeginning = {
                            showReadDropdownMenu = false
                            val firstVol = snapshot.volumes.firstOrNull { it.chapters.isNotEmpty() }
                            val firstChap = firstVol?.chapters?.firstOrNull()
                            if (firstVol != null && firstChap != null) {
                                invokeReaderTarget(
                                    firstVol.vid,
                                    firstChap.cid,
                                    firstVol.volumeTitle,
                                    firstChap.chapterTitle,
                                    false,
                                    0,
                                )
                            }
                        },
                        onJumpToLatestChapter = {
                            showReadDropdownMenu = false
                            val lastVol = snapshot.volumes.lastOrNull { it.chapters.isNotEmpty() }
                            val lastChap = lastVol?.chapters?.lastOrNull()
                            if (lastVol != null && lastChap != null) {
                                invokeReaderTarget(
                                    lastVol.vid,
                                    lastChap.cid,
                                    lastVol.volumeTitle,
                                    lastChap.chapterTitle,
                                    false,
                                    0,
                                )
                            }
                        },
                        onReadAloudTts = {
                            showReadDropdownMenu = false
                            val prog = snapshot.readingProgress
                            val firstVol = snapshot.volumes.firstOrNull { it.chapters.isNotEmpty() }
                            val firstChap = firstVol?.chapters?.firstOrNull()
                            if (prog != null && prog.cid > 0) {
                                invokeReaderTarget(
                                    prog.vid,
                                    prog.cid,
                                    prog.volumeTitle,
                                    prog.chapterTitle,
                                    true,
                                    -1,
                                )
                            } else if (firstVol != null && firstChap != null) {
                                invokeReaderTarget(
                                    firstVol.vid,
                                    firstChap.cid,
                                    firstVol.volumeTitle,
                                    firstChap.chapterTitle,
                                    true,
                                    0,
                                )
                            }
                        },
                        onToggleBookshelf = {
                            val addedSnack = context.getString(R.string.detail_shelf_added_snackbar)
                            val removedSnack = context.getString(R.string.detail_shelf_removed_snackbar)
                            coroutineScope.launch {
                                if (inBookshelf) {
                                    bookshelfRepository.removeNovelsFromBookshelf(
                                        aids = listOf(aid),
                                        removeFromCloudIfSignedIn = true,
                                        deleteOfflineCache = false,
                                    )
                                    snackbarHostState.showSnackbar(removedSnack)
                                } else {
                                    val baseEntity = snapshot.novel ?: NovelEntity(
                                        aid = aid,
                                        title = displayTitle,
                                        author = displayAuthor,
                                    )
                                    bookshelfRepository.addNovelToBookshelf(
                                        novel = baseEntity,
                                        syncToCloudIfSignedIn = true,
                                    )
                                    snackbarHostState.showSnackbar(addedSnack)
                                }
                            }
                        },
                        onOpenDownloader = { isDownloaderSheetOpen = true },
                        onOpenReviews = { onOpenReviews(aid, displayTitle) },
                    )
                }

                // 4. Active Download Progress Banner (when downloading this novel)
                if (currentDownloadProgress != null &&
                    currentDownloadProgress.state == NovelDownloadRunState.RUNNING
                ) {
                    item(key = "detail_active_download_banner") {
                        ActiveNovelDownloadCard(
                            progress = currentDownloadProgress,
                            isEinkMode = isEinkMode,
                            onCancel = { novelDownloadRepository.cancelDownload(aid) },
                        )
                    }
                }

                // 5. Expandable Synopsis
                item(key = "detail_synopsis_card") {
                    val synopsisText = snapshot.novel?.fullIntro?.ifBlank { null }
                        ?: snapshot.novel?.introShort?.ifBlank { null }
                        ?: seedDiscoveryItem?.introShort.orEmpty()
                    NovelSynopsisCard(
                        synopsis = synopsisText,
                        expanded = isSynopsisExpanded,
                        onToggleExpanded = { isSynopsisExpanded = !isSynopsisExpanded },
                    )
                }

                // 6. Single Zero-CLS Server-Support Inline Banner (between Synopsis and Volume Directory)
                item(key = "detail_server_support_ad") {
                    ServerSupportBannerAd(
                        isNetworkAvailableOverride = isNetworkAvailableForAdOverride,
                        isGmsAvailableOverride = isGmsAvailableForAdOverride,
                        enableLiveAdViewInflation = enableLiveAdViewInflation,
                    )
                }

                // 7. Volume Directory Header + Filter + Order Controls
                item(key = "detail_toc_header") {
                    VolumeDirectoryHeader(
                        volumeCount = snapshot.volumes.size,
                        isSearchVisible = isChapterFilterVisible,
                        searchQuery = chapterFilterQuery,
                        isReverseOrder = isReverseVolumeOrder,
                        onToggleSearch = {
                            isChapterFilterVisible = !isChapterFilterVisible
                            if (!isChapterFilterVisible) chapterFilterQuery = ""
                        },
                        onSearchQueryChange = { chapterFilterQuery = it },
                        onToggleReverseOrder = { isReverseVolumeOrder = !isReverseVolumeOrder },
                    )
                }

                if (filteredAndOrderedVolumes.isEmpty()) {
                    item(key = "detail_toc_empty") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .einkHairlineBorder(MaterialTheme.shapes.medium),
                        ) {
                            Text(
                                text = if (isRefreshing) {
                                    stringResource(R.string.detail_toc_empty_loading)
                                } else {
                                    stringResource(R.string.detail_toc_empty_offline)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(18.dp),
                            )
                        }
                    }
                } else {
                    items(
                        items = filteredAndOrderedVolumes,
                        key = { volume -> "volume_${volume.vid}" },
                    ) { volume ->
                        val isExpanded = chapterFilterQuery.isNotBlank() ||
                            volume.vid in expandedVolumeIds
                        VolumeAccordionCard(
                            volume = volume,
                            isExpanded = isExpanded,
                            readingProgressPercent = snapshot.readingProgressPercent,
                            onToggleExpand = {
                                expandedVolumeIds = if (volume.vid in expandedVolumeIds) {
                                    expandedVolumeIds - volume.vid
                                } else {
                                    expandedVolumeIds + volume.vid
                                }
                            },
                            onJumpToIllustrationChapter = { illusChap ->
                                invokeReaderTarget(
                                    volume.vid,
                                    illusChap.cid,
                                    volume.volumeTitle,
                                    illusChap.chapterTitle,
                                    false,
                                    0,
                                )
                            },
                            onChapterClicked = { chap ->
                                invokeReaderTarget(
                                    volume.vid,
                                    chap.cid,
                                    volume.volumeTitle,
                                    chap.chapterTitle,
                                    false,
                                    -1,
                                )
                            },
                        )
                    }
                }
            }
        }

        if (showFullTitleDialog) {
            val copiedSnack = stringResource(R.string.detail_title_copied_snackbar)
            AlertDialog(
                onDismissRequest = { showFullTitleDialog = false },
                modifier = Modifier.testTag(NovelDetailTestTags.TITLE_DIALOG),
                title = { Text(text = stringResource(R.string.detail_title_dialog_header)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = displayTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (displayAuthor.isNotBlank()) {
                            Text(
                                text = stringResource(R.string.detail_author_prefix_format, displayAuthor),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Text(
                            text = stringResource(R.string.detail_title_dialog_aid_format, aid),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
                confirmButton = {
                    FilledTonalButton(
                        onClick = {
                            copyTextToClipboard(context, "$displayTitle (#$aid)")
                            showFullTitleDialog = false
                            coroutineScope.launch {
                                snackbarHostState.showSnackbar(copiedSnack)
                            }
                        },
                        modifier = Modifier.testTag(NovelDetailTestTags.TITLE_DIALOG_COPY_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.detail_title_dialog_copy))
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFullTitleDialog = false }) {
                        Text(text = stringResource(android.R.string.ok))
                    }
                },
            )
        }

        if (isDownloaderSheetOpen) {
            NovelDownloaderBottomSheet(
                aid = aid,
                volumes = snapshot.volumes,
                totalChapters = snapshot.totalChapters,
                totalCachedChapters = snapshot.totalCachedChapters,
                onDismiss = { isDownloaderSheetOpen = false },
                onStartDownload = { mode, selectedVids, overwriteSelected ->
                    isDownloaderSheetOpen = false
                    // Auto-add to local bookshelf when caching offline so the user's downloaded
                    // book is always reachable offline from Tab 1 · 书架 (matching v1.31 UX!).
                    if (!inBookshelf) {
                        coroutineScope.launch {
                            val baseEntity = snapshot.novel ?: NovelEntity(
                                aid = aid,
                                title = displayTitle,
                                author = displayAuthor,
                            )
                            bookshelfRepository.addNovelToBookshelf(
                                novel = baseEntity,
                                syncToCloudIfSignedIn = true,
                            )
                        }
                    }
                    novelDownloadRepository.startDownload(
                        NovelDownloadRequest(
                            aid = aid,
                            appLanguage = appLanguage,
                            mode = mode,
                            selectedVolumeIds = selectedVids,
                            overwriteSelectedVolumes = overwriteSelected,
                        ),
                    )
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NovelHeroHeaderCard(
    aid: Int,
    title: String,
    author: String,
    novel: NovelEntity?,
    appLanguage: Wenku8API.AppLanguage,
    coverUrl: String,
    tags: List<String>,
    isEinkMode: Boolean,
    sharedTransitionOrigin: String,
    onCoverSeedExtracted: (Color) -> Unit,
    onTitleClicked: () -> Unit,
    onAuthorClicked: () -> Unit,
    onTagClicked: (String) -> Unit,
) {
    val gradientColors = if (isEinkMode) {
        listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surface)
    } else {
        listOf(
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
            MaterialTheme.colorScheme.surfaceContainerLow,
        )
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(gradientColors))
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val heroCoverShape = MaterialTheme.shapes.medium
            Surface(
                shape = heroCoverShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                tonalElevation = if (isEinkMode) 0.dp else 4.dp,
                modifier = Modifier
                    .width(116.dp)
                    .height(164.dp)
                    .wenku8SharedNovelCover(
                        origin = sharedTransitionOrigin,
                        aid = aid,
                        clipShape = heroCoverShape,
                    )
                    .einkHairlineBorder(heroCoverShape),
            ) {
                if (coverUrl.isNotBlank()) {
                    val coverRequest = WenkuImageRequests.rememberCoverImageRequest(
                        aid = aid,
                        coverUrl = coverUrl,
                        enableCrossfade = false,
                    )
                    AsyncImage(
                        model = coverRequest,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        onSuccess = { success ->
                            val bmp = (success.result.image as? BitmapImage)?.bitmap
                            if (bmp != null) {
                                onCoverSeedExtracted(CoverThemeEngine.extractSeedColorFromBitmap(bmp))
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Outlined.AutoStories,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(36.dp),
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "#$aid",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val pressId = novel?.pressId.orEmpty()
                val displayBookStatus = formatBookStatusLabel(
                    rawStatus = novel?.bookStatus.orEmpty(),
                    appLanguage = appLanguage,
                )
                if (pressId.isNotBlank() || displayBookStatus.isNotBlank()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (pressId.isNotBlank()) {
                            BadgePill(
                                text = pressId,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (displayBookStatus.isNotBlank()) {
                            BadgePill(
                                text = displayBookStatus,
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .wenku8SharedNovelTitle(
                            origin = sharedTransitionOrigin,
                            aid = aid,
                        )
                        .clip(MaterialTheme.shapes.small)
                        .clickable(onClick = onTitleClicked)
                        .padding(vertical = 2.dp)
                        .testTag(NovelDetailTestTags.NOVEL_TITLE_TEXT),
                )

                if (author.isNotBlank()) {
                    Surface(
                        color = Color.Transparent,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(onClick = onAuthorClicked)
                            .testTag(NovelDetailTestTags.AUTHOR_TRIGGER),
                    ) {
                        Text(
                            text = "${stringResource(R.string.detail_author_prefix_format, author)} ›",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }

                val lengthChars = novel?.bookLength ?: 0
                val lastUpdate = novel?.lastUpdate.orEmpty()
                val metaParts = buildList {
                    if (lengthChars >= 10_000) {
                        add(stringResource(R.string.detail_word_count_wan_format, lengthChars / 10_000f))
                    } else if (lengthChars > 0) {
                        add(stringResource(R.string.detail_word_count_char_format, lengthChars))
                    }
                    if (lastUpdate.isNotBlank()) {
                        add(stringResource(R.string.detail_last_update_format, lastUpdate))
                    }
                }
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        tags.forEach { tag ->
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                shape = MaterialTheme.shapes.small,
                                modifier = Modifier
                                    .clip(MaterialTheme.shapes.small)
                                    .clickable { onTagClicked(tag) }
                                    .einkHairlineBorder(MaterialTheme.shapes.small)
                                    .testTag(NovelDetailTestTags.TAG_CHIP_PREFIX + tag),
                            ) {
                                Text(
                                    text = "#$tag",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelStatsStrip(novel: NovelEntity?) {
    val totalHits = formatCompactCount(novel?.totalHits ?: 0)
    val dayHits = novel?.dayHits ?: 0
    val pushCount = formatCompactCount(novel?.pushCount ?: 0)
    val favCount = formatCompactCount(novel?.favCount ?: 0)
    val latestChapter = novel?.latestSectionName?.ifBlank { "—" } ?: "—"

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatCell(
                label = if (dayHits > 0) {
                    "${stringResource(R.string.detail_stat_total_hits)} (${
                        stringResource(R.string.detail_stat_day_hits_format, dayHits)
                    })"
                } else {
                    stringResource(R.string.detail_stat_total_hits)
                },
                value = totalHits,
                modifier = Modifier.weight(1f),
            )
            StatCell(
                label = stringResource(R.string.detail_stat_push_count),
                value = pushCount,
                modifier = Modifier.weight(0.85f),
            )
            StatCell(
                label = stringResource(R.string.detail_stat_fav_count),
                value = favCount,
                modifier = Modifier.weight(0.85f),
            )
            StatCell(
                label = stringResource(R.string.detail_stat_latest_chapter),
                value = latestChapter,
                modifier = Modifier.weight(1.3f),
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NovelActionDeck(
    snapshot: NovelDetailSnapshot,
    inBookshelf: Boolean,
    activeDownload: NovelDownloadProgress?,
    showReadDropdownMenu: Boolean,
    onShowReadDropdownMenuChange: (Boolean) -> Unit,
    onResumeOrStartReading: () -> Unit,
    onStartFromBeginning: () -> Unit,
    onJumpToLatestChapter: () -> Unit,
    onReadAloudTts: () -> Unit,
    onToggleBookshelf: () -> Unit,
    onOpenDownloader: () -> Unit,
    onOpenReviews: () -> Unit,
) {
    val hasReadableToc = snapshot.firstReadableChapter != null || snapshot.readingProgress != null
    val readingButtonLabel = snapshot.readingProgress?.let { prog ->
        val chapterLabel = prog.chapterTitle.ifBlank { prog.volumeTitle }.ifBlank { "#${prog.cid}" }
        stringResource(
            R.string.detail_read_continue_format,
            chapterLabel,
            snapshot.readingProgressPercent,
        )
    } ?: stringResource(R.string.detail_read_start_action)

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            SplitButtonLayout(
                modifier = Modifier.fillMaxWidth(),
                leadingButton = {
                    SplitButtonDefaults.LeadingButton(
                        onClick = onResumeOrStartReading,
                        enabled = hasReadableToc,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(NovelDetailTestTags.READ_LEADING_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = readingButtonLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                trailingButton = {
                    SplitButtonDefaults.TrailingButton(
                        checked = showReadDropdownMenu,
                        onCheckedChange = onShowReadDropdownMenuChange,
                        enabled = hasReadableToc,
                        modifier = Modifier.testTag(NovelDetailTestTags.READ_TRAILING_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ExpandMore,
                            contentDescription = stringResource(R.string.detail_read_menu_description),
                            modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                        )
                    }
                },
            )

            DropdownMenu(
                expanded = showReadDropdownMenu,
                onDismissRequest = { onShowReadDropdownMenuChange(false) },
            ) {
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.detail_read_from_beginning)) },
                    leadingIcon = { Icon(Icons.Outlined.RestartAlt, contentDescription = null) },
                    onClick = onStartFromBeginning,
                    modifier = Modifier.testTag(NovelDetailTestTags.MENU_READ_BEGINNING),
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.detail_read_jump_latest)) },
                    leadingIcon = { Icon(Icons.Outlined.SkipNext, contentDescription = null) },
                    onClick = onJumpToLatestChapter,
                    modifier = Modifier.testTag(NovelDetailTestTags.MENU_READ_LATEST),
                )
                DropdownMenuItem(
                    text = { Text(text = stringResource(R.string.detail_read_tts_current)) },
                    leadingIcon = {
                        Icon(Icons.AutoMirrored.Outlined.VolumeUp, contentDescription = null)
                    },
                    onClick = onReadAloudTts,
                    modifier = Modifier.testTag(NovelDetailTestTags.MENU_READ_TTS),
                )
            }
        }

        // Balanced 3-Button Secondary Action Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (inBookshelf) {
                FilledTonalButton(
                    onClick = onToggleBookshelf,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag(NovelDetailTestTags.SHELF_TOGGLE_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkAdded,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.detail_shelf_added),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onToggleBookshelf,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                    modifier = Modifier
                        .weight(1f)
                        .minimumInteractiveComponentSize()
                        .testTag(NovelDetailTestTags.SHELF_TOGGLE_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.detail_shelf_add),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            OutlinedButton(
                onClick = onOpenDownloader,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag(NovelDetailTestTags.DOWNLOAD_OPEN_BUTTON),
            ) {
                Icon(
                    imageVector = if (snapshot.totalCachedChapters > 0 &&
                        snapshot.totalCachedChapters >= snapshot.totalChapters &&
                        snapshot.totalChapters > 0
                    ) {
                        Icons.Outlined.DownloadDone
                    } else {
                        Icons.Outlined.Download
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = if (activeDownload != null && activeDownload.totalChapters > 0) {
                        "${activeDownload.completedChapters}/${activeDownload.totalChapters}"
                    } else {
                        stringResource(R.string.detail_action_download)
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            OutlinedButton(
                onClick = onOpenReviews,
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .testTag(NovelDetailTestTags.REVIEWS_OPEN_BUTTON),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Chat,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.detail_action_reviews),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ActiveNovelDownloadCard(
    progress: NovelDownloadProgress,
    isEinkMode: Boolean,
    onCancel: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NovelDetailTestTags.ACTIVE_DOWNLOAD_BANNER)
            .einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(
                        R.string.download_progress_running_format,
                        progress.completedChapters,
                        progress.totalChapters,
                        progress.currentChapterTitle.ifBlank { "…" },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag(NovelDetailTestTags.CANCEL_DOWNLOAD_BUTTON),
                ) {
                    Text(text = stringResource(R.string.download_cancel_action))
                }
            }

            val ratio = if (progress.totalChapters > 0) {
                (progress.completedChapters.toFloat() / progress.totalChapters.toFloat()).coerceIn(0f, 1f)
            } else {
                0f
            }
            if (isEinkMode) {
                LinearProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else if (progress.totalChapters > 0) {
                LinearWavyProgressIndicator(
                    progress = { ratio },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun NovelSynopsisCard(
    synopsis: String,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.detail_synopsis_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (synopsis.length > 90 || synopsis.count { it == '\n' } >= 3) {
                    TextButton(
                        onClick = onToggleExpanded,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.testTag(NovelDetailTestTags.SYNOPSIS_TOGGLE_BUTTON),
                    ) {
                        Text(
                            text = if (expanded) {
                                stringResource(R.string.detail_synopsis_collapse)
                            } else {
                                stringResource(R.string.detail_synopsis_expand)
                            },
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Text(
                text = synopsis.ifBlank { stringResource(R.string.detail_synopsis_empty) },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (expanded) Int.MAX_VALUE else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun VolumeDirectoryHeader(
    volumeCount: Int,
    isSearchVisible: Boolean,
    searchQuery: String,
    isReverseOrder: Boolean,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onToggleReverseOrder: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.detail_toc_header_format, volumeCount),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )

            FilterChip(
                selected = isSearchVisible,
                onClick = onToggleSearch,
                label = { Text(text = stringResource(R.string.detail_toc_search_action)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.testTag(NovelDetailTestTags.TOC_SEARCH_TOGGLE),
            )
            Spacer(modifier = Modifier.width(8.dp))
            AssistChip(
                onClick = onToggleReverseOrder,
                label = {
                    Text(
                        text = if (isReverseOrder) {
                            stringResource(R.string.detail_toc_order_desc)
                        } else {
                            stringResource(R.string.detail_toc_order_asc)
                        },
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.SwapVert,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                modifier = Modifier.testTag(NovelDetailTestTags.TOC_ORDER_TOGGLE),
            )
        }

        if (isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                placeholder = { Text(text = stringResource(R.string.detail_toc_search_placeholder)) },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = null)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(NovelDetailTestTags.TOC_SEARCH_INPUT),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VolumeAccordionCard(
    volume: VolumeAccordionModel,
    isExpanded: Boolean,
    readingProgressPercent: Int,
    onToggleExpand: () -> Unit,
    onJumpToIllustrationChapter: (ChapterRowModel) -> Unit,
    onChapterClicked: (ChapterRowModel) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpand)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .testTag(NovelDetailTestTags.VOLUME_HEADER_PREFIX + volume.vid),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = volume.volumeTitle,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (volume.containsReadingProgress) {
                            BadgePill(
                                text = stringResource(R.string.detail_volume_last_read_badge),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        if (volume.cachedChapterCount > 0) {
                            BadgePill(
                                text = stringResource(
                                    R.string.detail_volume_cached_ratio_format,
                                    volume.cachedChapterCount,
                                    volume.totalChapterCount,
                                ),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                        val illus = volume.illustrationChapter
                        if (illus != null) {
                            AssistChip(
                                onClick = { onJumpToIllustrationChapter(illus) },
                                label = {
                                    Text(
                                        text = stringResource(R.string.detail_volume_illustration_chip),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp),
                                    )
                                },
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                    leadingIconContentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                ),
                                border = AssistChipDefaults.assistChipBorder(
                                    enabled = true,
                                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                                ),
                                modifier = Modifier
                                    .height(28.dp)
                                    .testTag(NovelDetailTestTags.VOLUME_ILLUS_CHIP_PREFIX + volume.vid),
                            )
                        }
                    }
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isExpanded && volume.chapters.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                volume.chapters.forEachIndexed { index, chapter ->
                    if (index > 0) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                            modifier = Modifier.padding(horizontal = 14.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChapterClicked(chapter) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag(NovelDetailTestTags.CHAPTER_ROW_PREFIX + chapter.cid),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (chapter.isIllustrationChapter) {
                            Icon(
                                imageVector = Icons.Outlined.Image,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Text(
                            text = chapter.chapterTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (chapter.isCurrentReadingChapter) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            color = if (chapter.isCurrentReadingChapter) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

                        if (chapter.isCurrentReadingChapter) {
                            BadgePill(
                                text = stringResource(
                                    R.string.detail_chapter_reading_now_format,
                                    readingProgressPercent,
                                ),
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        } else if (chapter.isIllustrationChapter) {
                            BadgePill(
                                text = stringResource(R.string.detail_chapter_illustration_badge),
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            )
                        }

                        if (chapter.isCached) {
                            BadgePill(
                                text = stringResource(R.string.detail_chapter_cached_badge),
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NovelDownloaderBottomSheet(
    aid: Int,
    volumes: List<VolumeAccordionModel>,
    totalChapters: Int,
    totalCachedChapters: Int,
    onDismiss: () -> Unit,
    onStartDownload: (
        mode: NovelDownloadMode,
        selectedVolumeIds: Set<Int>,
        overwriteSelectedVolumes: Boolean,
    ) -> Unit,
) {
    var selectedMode by rememberSaveable(aid) { mutableStateOf(NovelDownloadMode.SMART_UPDATE) }
    var selectedVolumeIds by rememberSaveable(aid) {
        mutableStateOf(
            volumes.filter { it.cachedChapterCount < it.totalChapterCount }
                .map { it.vid }
                .ifEmpty { volumes.map { it.vid } },
        )
    }
    var overwriteSelectedVolumes by rememberSaveable(aid) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(NovelDetailTestTags.DOWNLOADER_SHEET),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "dl_header") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.download_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = stringResource(
                            R.string.download_sheet_subtitle_format,
                            volumes.size,
                            totalChapters,
                            totalCachedChapters,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val modeEntries = listOf(
                Triple(
                    NovelDownloadMode.SMART_UPDATE,
                    R.string.download_mode_smart_update,
                    R.string.download_mode_smart_update_desc,
                ),
                Triple(
                    NovelDownloadMode.UNCACHED_ONLY,
                    R.string.download_mode_uncached_only,
                    R.string.download_mode_uncached_only_desc,
                ),
                Triple(
                    NovelDownloadMode.SELECT_VOLUMES,
                    R.string.download_mode_select_volumes,
                    R.string.download_mode_select_volumes_desc,
                ),
                Triple(
                    NovelDownloadMode.OVERWRITE_ALL,
                    R.string.download_mode_overwrite_all,
                    R.string.download_mode_overwrite_all_desc,
                ),
            )

            items(modeEntries, key = { it.first.name }) { (mode, titleRes, descRes) ->
                val isSelected = selectedMode == mode
                Surface(
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { selectedMode = mode }
                        .einkHairlineBorder(MaterialTheme.shapes.medium)
                        .testTag(NovelDetailTestTags.DOWNLOAD_MODE_PREFIX + mode.name),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = isSelected, onClick = null)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(titleRes),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = stringResource(descRes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            if (selectedMode == NovelDownloadMode.SELECT_VOLUMES && volumes.isNotEmpty()) {
                item(key = "dl_select_volumes_controls") {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.download_select_volumes_overwrite_toggle),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Switch(
                                checked = overwriteSelectedVolumes,
                                onCheckedChange = { overwriteSelectedVolumes = it },
                                modifier = Modifier.testTag(NovelDetailTestTags.DOWNLOAD_OVERWRITE_SWITCH),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(
                                onClick = { selectedVolumeIds = volumes.map { it.vid } },
                            ) {
                                Text(text = stringResource(R.string.download_select_all_volumes))
                            }
                            TextButton(
                                onClick = { selectedVolumeIds = emptyList() },
                            ) {
                                Text(text = stringResource(R.string.download_clear_selected_volumes))
                            }
                        }
                    }
                }

                items(volumes, key = { "dl_vol_${it.vid}" }) { volume ->
                    val checked = volume.vid in selectedVolumeIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable {
                                selectedVolumeIds = if (checked) {
                                    selectedVolumeIds - volume.vid
                                } else {
                                    selectedVolumeIds + volume.vid
                                }
                            }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                            .testTag(NovelDetailTestTags.DOWNLOAD_VOLUME_CHECK_PREFIX + volume.vid),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = volume.volumeTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${volume.cachedChapterCount}/${volume.totalChapterCount}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item(key = "dl_start_button") {
                val canSubmit = selectedMode != NovelDownloadMode.SELECT_VOLUMES ||
                    selectedVolumeIds.isNotEmpty()
                Button(
                    onClick = {
                        onStartDownload(
                            selectedMode,
                            selectedVolumeIds.toSet(),
                            overwriteSelectedVolumes,
                        )
                    },
                    enabled = canSubmit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(NovelDetailTestTags.DOWNLOAD_START_BUTTON),
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = stringResource(R.string.download_start_button))
                }
            }
        }
    }
}

@Composable
private fun BadgePill(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = Modifier.einkHairlineBorder(MaterialTheme.shapes.extraSmall),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

private fun formatCompactCount(count: Int): String {
    if (count <= 0) return "—"
    if (count >= 10_000) {
        return String.format(Locale.US, "%.1f 万", count / 10_000f)
    }
    return String.format(Locale.US, "%,d", count)
}

private fun copyTextToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Wenku8 Novel", text))
}

private fun shareNovelExternally(context: Context, shareText: String) {
    runCatching {
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
        }
        val chooser = Intent.createChooser(sendIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
