package org.mewx.wenku8.ui.detail

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
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.BitmapImage
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.R
import org.mewx.wenku8.data.download.NovelDownloadMode
import org.mewx.wenku8.data.download.NovelDownloadProgress
import org.mewx.wenku8.data.download.NovelDownloadRepository
import org.mewx.wenku8.data.download.NovelDownloadRequest
import org.mewx.wenku8.data.download.NovelDownloadRunState
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.data.repository.BookshelfRepository
import org.mewx.wenku8.data.repository.ChapterRowModel
import org.mewx.wenku8.data.repository.NovelDetailRefreshOutcome
import org.mewx.wenku8.data.repository.NovelDetailRepository
import org.mewx.wenku8.data.repository.NovelDetailSnapshot
import org.mewx.wenku8.data.repository.VolumeAccordionModel
import org.mewx.wenku8.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.ui.bookshelf.Bookshelf3dGeometry
import org.mewx.wenku8.ui.bookshelf.bookshelf3dCoverPerspectiveMorph
import org.mewx.wenku8.ui.components.ActionTooltipBox
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.discovery.bookStatusLeadingIcon
import org.mewx.wenku8.ui.discovery.formatBookStatusLabel
import org.mewx.wenku8.ui.reader.ArtbookLightboxOverlay
import org.mewx.wenku8.ui.theme.CoverThemeEngine
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.LocalWenku8SharedTransition
import org.mewx.wenku8.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import org.mewx.wenku8.ui.theme.expressiveSharedFloatSpec
import org.mewx.wenku8.ui.theme.wenku8SharedNovelCover
import org.mewx.wenku8.ui.theme.wenku8SharedNovelTitle
import java.io.File
import java.util.Locale

object NovelDetailTestTags {
    const val SCREEN_ROOT = "novel_detail_screen_root"
    const val BACK_BUTTON = "novel_detail_back_button"
    const val SHARE_BUTTON = "novel_detail_share_button"
    const val REFRESH_BUTTON = "novel_detail_refresh_button"
    const val COVER_IMAGE_TRIGGER = "novel_detail_cover_image_trigger"
    const val NOVEL_TITLE_TEXT = "novel_detail_title_text"
    const val BOOK_STATUS_BADGE = "novel_detail_book_status_badge"
    const val UNRESOLVED_STATE = "novel_detail_unresolved_state"
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
    onOpenCoverLightbox: ((title: String, coverUrl: String) -> Unit)? = null,
    coverLightboxImageFileResolver: (suspend (String) -> File?)? = null,
    bannerPlacement: InlineBannerPlacement = InlineBannerPlacement.NOVEL_DETAIL_STANDARD,
    bannerRequestNonce: Int = 0,
    isNetworkAvailableForAdOverride: Boolean? = null,
    isGmsAvailableForAdOverride: Boolean? = null,
    canRequestAdsForAdOverride: Boolean? = null,
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
    val initialSnapshot = remember(aid, seedDiscoveryItem, initialCoverUrl, novelDetailRepository) {
        novelDetailRepository.peekCachedSnapshot(aid) ?: NovelDetailSnapshot(
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
        )
    }
    val detailFlow = remember(aid, novelDetailRepository) {
        novelDetailRepository.observeNovelDetail(aid)
    }
    val snapshot by detailFlow.collectAsStateWithLifecycle(
        initialValue = initialSnapshot,
    )

    val activeDownloadsMap by novelDownloadRepository.activeDownloadsFlow.collectAsStateWithLifecycle()
    val currentDownloadProgress = activeDownloadsMap[aid]

    var isRefreshing by rememberSaveable(aid) { mutableStateOf(false) }
    var isManualRefreshInFlight by rememberSaveable(aid) { mutableStateOf(false) }
    var showDebouncedAutoRefreshBar by remember(aid) { mutableStateOf(false) }
    var showOfflinePreservedBanner by rememberSaveable(aid) { mutableStateOf(false) }
    var showFullTitleDialog by rememberSaveable(aid) { mutableStateOf(false) }
    var showReadDropdownMenu by remember { mutableStateOf(false) }
    var isSynopsisExpanded by rememberSaveable(aid) { mutableStateOf(false) }
    var isChapterFilterVisible by rememberSaveable(aid) { mutableStateOf(false) }
    var chapterFilterQuery by rememberSaveable(aid) { mutableStateOf("") }
    var isReverseVolumeOrder by rememberSaveable(aid) { mutableStateOf(false) }
    var isDownloaderSheetOpen by rememberSaveable(aid) { mutableStateOf(false) }
    var showInlineCoverLightbox by rememberSaveable(aid) { mutableStateOf(false) }

    // Auto-expand either the volume containing reading progress or the first volume on the very
    // first frame `volumes` arrives so there is never a 1-frame collapsed-then-pop-open flash.
    var expandedVolumeIds by rememberSaveable(aid) { mutableStateOf<List<Int>>(emptyList()) }
    var initializedDefaultAccordion by rememberSaveable(aid) { mutableStateOf(false) }

    val defaultInitialExpandedVid = if (!initializedDefaultAccordion && snapshot.volumes.isNotEmpty()) {
        snapshot.readingProgress?.vid
            ?.takeIf { progressVid -> snapshot.volumes.any { it.vid == progressVid } }
            ?: snapshot.volumes.first().vid
    } else {
        null
    }
    val effectiveExpandedVolumeIds = if (defaultInitialExpandedVid != null) {
        listOf(defaultInitialExpandedVid)
    } else {
        expandedVolumeIds
    }

    LaunchedEffect(aid, defaultInitialExpandedVid) {
        if (defaultInitialExpandedVid != null && !initializedDefaultAccordion) {
            expandedVolumeIds = listOf(defaultInitialExpandedVid)
            initializedDefaultAccordion = true
        }
    }

    var loggedViewItemAid by rememberSaveable(aid) { mutableStateOf(0) }
    val resolvedAnalyticsTitle = snapshot.novel?.title?.takeIf { it.isNotBlank() }
        ?: seedDiscoveryItem?.title?.takeIf { it.isNotBlank() }
    LaunchedEffect(aid, resolvedAnalyticsTitle) {
        if (aid > 0 && loggedViewItemAid != aid && resolvedAnalyticsTitle != null) {
            loggedViewItemAid = aid
            org.mewx.wenku8.util.AnalyticsTracker.logViewNovel(
                aid = aid,
                title = resolvedAnalyticsTitle,
                from = sharedTransitionOrigin,
            )
        }
    }

    LaunchedEffect(isRefreshing, isManualRefreshInFlight, snapshot.volumes.isEmpty()) {
        if (!isRefreshing) {
            showDebouncedAutoRefreshBar = false
        } else if (isManualRefreshInFlight) {
            showDebouncedAutoRefreshBar = true
        } else if (snapshot.volumes.isEmpty()) {
            delay(220L)
            if (isRefreshing && snapshot.volumes.isEmpty()) {
                showDebouncedAutoRefreshBar = true
            }
        } else {
            showDebouncedAutoRefreshBar = false
        }
    }
    val showRefreshProgressBar = isRefreshing && (isManualRefreshInFlight || showDebouncedAutoRefreshBar)

    var coverSeedColor by remember(aid) {
        mutableStateOf(CoverThemeEngine.initialSeedColorForAid(aid))
    }
    val animatedCoverSeedColor by animateColorAsState(
        targetValue = coverSeedColor,
        animationSpec = if (isEinkMode) snap() else tween(durationMillis = 220),
        label = "detailCoverSeedColor",
    )

    val runRefresh: (manual: Boolean) -> Unit = { manual ->
        coroutineScope.launch {
            isRefreshing = true
            if (manual) isManualRefreshInFlight = true
            try {
                when (
                    novelDetailRepository.refreshNovelDetail(
                        aid = aid,
                        appLanguage = appLanguage,
                        fallbackSeedItem = seedDiscoveryItem,
                        forceRefresh = manual,
                    )
                ) {
                    NovelDetailRefreshOutcome.Success,
                    NovelDetailRefreshOutcome.SkippedFreshCache,
                    -> {
                        showOfflinePreservedBanner = false
                    }
                    NovelDetailRefreshOutcome.OfflineOrCachedOnly -> {
                        novelDetailRepository.acknowledgeUnreadUpdateIfPresent(aid)
                        showOfflinePreservedBanner = true
                    }
                    NovelDetailRefreshOutcome.ApiNotConfigured,
                    is NovelDetailRefreshOutcome.Failed,
                    -> {
                        novelDetailRepository.acknowledgeUnreadUpdateIfPresent(aid)
                    }
                }
            } finally {
                isRefreshing = false
                isManualRefreshInFlight = false
            }
        }
    }

    val triggerRefresh: () -> Unit = { runRefresh(true) }

    LaunchedEffect(aid, appLanguage) {
        runRefresh(false)
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
    val effectiveScheme = remember(baseScheme, animatedCoverSeedColor, isDarkEffective, isEinkMode, useCoverTheme) {
        if (useCoverTheme && !isEinkMode) {
            CoverThemeEngine.createScopedCoverColorScheme(
                baseColorScheme = baseScheme,
                coverSeedColor = animatedCoverSeedColor,
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

    val invokeReaderTarget: (
        vid: Int,
        cid: Int,
        volumeTitle: String,
        chapterTitle: String,
        tts: Boolean,
        initialParagraphIndex: Int,
        initialCharOffset: Int,
    ) -> Unit = { vid, cid, volTitle, chapTitle, tts, initialParaIdx, initialCharOff ->
        val req = ReaderLaunchRequest(
            aid = aid,
            vid = vid,
            cid = cid,
            novelTitle = displayTitle,
            volumeTitle = volTitle,
            chapterTitle = chapTitle,
            startWithTts = tts,
            initialParagraphIndex = initialParaIdx,
            initialCharOffset = initialCharOff,
        )
        onOpenReader(req)
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

    val effectiveCoverUrl = snapshot.coverUrl.ifBlank { initialCoverUrl }

    MaterialTheme(
        colorScheme = effectiveScheme,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
    ) {
        Box(modifier = modifier.fillMaxSize()) {
            Scaffold(
                modifier = Modifier
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
                            val backLabel = stringResource(R.string.action_back)
                            TooltipIconButton(
                                tooltipText = backLabel,
                                onClick = onNavigateBack,
                                modifier = Modifier.testTag(NovelDetailTestTags.BACK_BUTTON),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                    contentDescription = backLabel,
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
                        val shareLabel = stringResource(R.string.detail_action_share)
                        TooltipIconButton(
                            tooltipText = shareLabel,
                            onClick = {
                                org.mewx.wenku8.util.AnalyticsTracker.logShareNovel(aid)
                                shareNovelExternally(context, shareMessage)
                            },
                            modifier = Modifier.testTag(NovelDetailTestTags.SHARE_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = shareLabel,
                            )
                        }
                        val refreshLabel = stringResource(R.string.detail_action_refresh)
                        TooltipIconButton(
                            tooltipText = refreshLabel,
                            onClick = triggerRefresh,
                            enabled = !isRefreshing,
                            modifier = Modifier.testTag(NovelDetailTestTags.REFRESH_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = refreshLabel,
                            )
                        }
                    },
                )
            },
        ) { innerPadding ->
            val bannerAdHolder = rememberNovelDetailBannerAdHolder(
                aid = aid,
                placement = bannerPlacement,
                requestNonce = bannerRequestNonce,
                isNetworkAvailableOverride = isNetworkAvailableForAdOverride,
                isGmsAvailableOverride = isGmsAvailableForAdOverride,
                canRequestAdsOverride = canRequestAdsForAdOverride,
                enableLiveAdViewInflation = enableLiveAdViewInflation,
            )
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val bannerSlotWidthDp = (maxWidth - 32.dp).value.toInt().coerceAtLeast(160)
                PrefetchBannerAdEffect(
                    holder = bannerAdHolder,
                    availableWidthDp = bannerSlotWidthDp,
                    canRequestAdsOverride = canRequestAdsForAdOverride,
                )
                // Unresolved-book overlay: when the repository has no local snapshot AND no seed
                // card, the pane would otherwise render a bare zeroed skeleton (the reported
                // "blank detail pane"). Cover it with an explicit loading / retry state until the
                // first Room emission or a successful refresh resolves the book.
                if (snapshot.novel == null && snapshot.volumes.isEmpty()) {
                    NovelDetailUnresolvedOverlay(
                        isRefreshing = isRefreshing,
                        isEinkMode = isEinkMode,
                        onRetry = triggerRefresh,
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(1f)
                            .background(MaterialTheme.colorScheme.background),
                    )
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .testTag(NovelDetailTestTags.SCREEN_ROOT + "_list"),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
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
                                    val dismissLabel = stringResource(R.string.action_dismiss_banner)
                                    TooltipIconButton(
                                        tooltipText = dismissLabel,
                                        onClick = { showOfflinePreservedBanner = false },
                                    ) {
                                        Icon(imageVector = Icons.Outlined.Close, contentDescription = dismissLabel)
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
                            coverUrl = effectiveCoverUrl,
                            tags = snapshot.tags,
                            isEinkMode = isEinkMode,
                            sharedTransitionOrigin = sharedTransitionOrigin,
                            onCoverSeedExtracted = { extracted ->
                                CoverThemeEngine.recordExtractedSeedColor(aid, extracted)
                                coverSeedColor = extracted
                            },
                            onCoverClicked = {
                                if (effectiveCoverUrl.isNotBlank()) {
                                    if (onOpenCoverLightbox != null) {
                                        onOpenCoverLightbox(displayTitle, effectiveCoverUrl)
                                    } else {
                                        showInlineCoverLightbox = true
                                    }
                                }
                            },
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
                                        prog.paragraphIndex.coerceAtLeast(0),
                                        prog.charOffset.coerceAtLeast(0),
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
                                        prog.paragraphIndex.coerceAtLeast(0),
                                        prog.charOffset.coerceAtLeast(0),
                                    )
                                } else if (firstVol != null && firstChap != null) {
                                    invokeReaderTarget(
                                        firstVol.vid,
                                        firstChap.cid,
                                        firstVol.volumeTitle,
                                        firstChap.chapterTitle,
                                        true,
                                        0,
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

                    // 6. Single Inline Adaptive Banner (between Synopsis and Volume Directory)
                    if (bannerAdHolder.shouldRenderVisibleCard) {
                        item(key = "detail_server_support_ad") {
                            ServerSupportBannerAd(
                                holder = bannerAdHolder,
                                canRequestAdsOverride = canRequestAdsForAdOverride,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
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
                                volume.vid in effectiveExpandedVolumeIds
                            VolumeAccordionCard(
                                volume = volume,
                                isExpanded = isExpanded,
                                readingProgressPercent = snapshot.readingProgressPercent,
                                onToggleExpand = {
                                    val baseIds = effectiveExpandedVolumeIds
                                    initializedDefaultAccordion = true
                                    expandedVolumeIds = if (volume.vid in baseIds) {
                                        baseIds - volume.vid
                                    } else {
                                        baseIds + volume.vid
                                    }
                                },
                                onChapterClicked = { chap ->
                                    val prog = snapshot.readingProgress?.takeIf { it.cid == chap.cid }
                                    invokeReaderTarget(
                                        volume.vid,
                                        chap.cid,
                                        volume.volumeTitle,
                                        chap.chapterTitle,
                                        false,
                                        prog?.paragraphIndex ?: -1,
                                        prog?.charOffset ?: 0,
                                    )
                                },
                            )
                        }
                    }
                }

                if (showRefreshProgressBar) {
                    val overlayMod = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = innerPadding.calculateTopPadding())
                        .fillMaxWidth()
                    if (isEinkMode) {
                        LinearProgressIndicator(modifier = overlayMod)
                    } else {
                        LinearWavyProgressIndicator(modifier = overlayMod)
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

            if (showInlineCoverLightbox && effectiveCoverUrl.isNotBlank()) {
                ArtbookLightboxOverlay(
                    novelTitle = displayTitle,
                    chapterTitle = displayTitle,
                    imageUrls = listOf(effectiveCoverUrl),
                    subtitleOverride = stringResource(R.string.detail_cover_lightbox_subtitle),
                    isSingleImagePreview = true,
                    saveFileNameSuffix = "cover",
                    imageFileResolver = coverLightboxImageFileResolver,
                    onDismiss = { showInlineCoverLightbox = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
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
    onCoverClicked: () -> Unit,
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(gradientColors))
                .padding(16.dp),
        ) {
            // Inside 16dp outer LazyColumn padding + 16dp inner card padding:
            // 412dp phone -> maxWidth = 348dp (normal); <= 360dp phone -> maxWidth <= 296dp (narrow).
            val isNarrowHero = maxWidth < 312.dp
            val coverWidth = if (isNarrowHero) 96.dp else 116.dp
            val coverHeight = if (isNarrowHero) 136.dp else 164.dp
            val heroTagFlowRow: @Composable () -> Unit = {
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
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(if (isNarrowHero) 12.dp else 16.dp),
                ) {
                    val heroCoverShape = MaterialTheme.shapes.medium
                    val isFrom3dShelf = sharedTransitionOrigin == Wenku8SharedElementKeys.NOVEL_BOOKSHELF_3D_ORIGIN
                    val sharedCtx = LocalWenku8SharedTransition.current
                    val enterSlantedFractionState = if (isFrom3dShelf && sharedCtx != null && !isEinkMode) {
                        sharedCtx.animatedVisibilityScope.transition.animateFloat(
                            transitionSpec = { expressiveSharedFloatSpec(isEinkMode = false) },
                            label = "detail_cover_3d_enter_slant",
                        ) { enterExitState ->
                            if (enterExitState == EnterExitState.PreEnter) 1f else 0f
                        }
                    } else {
                        null
                    }
                    val enterSlantedFractionProvider: () -> Float = remember(enterSlantedFractionState) {
                        { enterSlantedFractionState?.value ?: 0f }
                    }
                    val enterSlantedValue = enterSlantedFractionState?.value ?: 0f
                    val effectiveCoverShape = if (isFrom3dShelf && enterSlantedValue > 0.001f) {
                        RoundedCornerShape(
                            Bookshelf3dGeometry.interpolatedCoverCornerDp(enterSlantedValue).dp,
                        )
                    } else {
                        heroCoverShape
                    }
                    Box(
                        modifier = Modifier
                            .width(coverWidth)
                            .height(coverHeight)
                            .wenku8SharedNovelCover(
                                origin = sharedTransitionOrigin,
                                aid = aid,
                                clipShape = if (isFrom3dShelf) null else heroCoverShape,
                            ),
                    ) {
                        Surface(
                            shape = effectiveCoverShape,
                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            tonalElevation = if (isEinkMode) 0.dp else 4.dp,
                            modifier = Modifier
                                .fillMaxSize()
                                .bookshelf3dCoverPerspectiveMorph(
                                    enabled = isFrom3dShelf && !isEinkMode,
                                    slantedFractionProvider = enterSlantedFractionProvider,
                                )
                                .einkHairlineBorder(effectiveCoverShape),
                        ) {
                            val viewCoverLabel = stringResource(R.string.detail_cover_view_action)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag(NovelDetailTestTags.COVER_IMAGE_TRIGGER)
                                    .clickable(
                                        enabled = coverUrl.isNotBlank(),
                                        onClickLabel = viewCoverLabel,
                                        onClick = onCoverClicked,
                                    ),
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
                                        leadingIcon = bookStatusLeadingIcon(displayBookStatus),
                                        modifier = Modifier.testTag(NovelDetailTestTags.BOOK_STATUS_BADGE),
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

                        if (!isNarrowHero && tags.isNotEmpty()) {
                            heroTagFlowRow()
                        }
                    }
                }

                if (isNarrowHero && tags.isNotEmpty()) {
                    heroTagFlowRow()
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
    val totalHitsLabel = if (dayHits > 0) {
        "${stringResource(R.string.detail_stat_total_hits)} (${
            stringResource(R.string.detail_stat_day_hits_format, dayHits)
        })"
    } else {
        stringResource(R.string.detail_stat_total_hits)
    }
    val pushLabel = stringResource(R.string.detail_stat_push_count)
    val favLabel = stringResource(R.string.detail_stat_fav_count)
    val latestLabel = stringResource(R.string.detail_stat_latest_chapter)
    val fontScale = LocalDensity.current.fontScale

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
        ) {
            if (maxWidth < 332.dp || fontScale > 1.1f) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatCell(
                            label = totalHitsLabel,
                            value = totalHits,
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            label = pushLabel,
                            value = pushCount,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatCell(
                            label = favLabel,
                            value = favCount,
                            modifier = Modifier.weight(1f),
                        )
                        StatCell(
                            label = latestLabel,
                            value = latestChapter,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatCell(
                        label = totalHitsLabel,
                        value = totalHits,
                        modifier = Modifier.weight(1f),
                    )
                    StatCell(
                        label = pushLabel,
                        value = pushCount,
                        modifier = Modifier.weight(0.85f),
                    )
                    StatCell(
                        label = favLabel,
                        value = favCount,
                        modifier = Modifier.weight(0.85f),
                    )
                    StatCell(
                        label = latestLabel,
                        value = latestChapter,
                        modifier = Modifier.weight(1.3f),
                    )
                }
            }
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
                    val readMenuLabel = stringResource(R.string.detail_read_menu_description)
                    ActionTooltipBox(tooltipText = readMenuLabel) {
                        SplitButtonDefaults.TrailingButton(
                            checked = showReadDropdownMenu,
                            onCheckedChange = onShowReadDropdownMenuChange,
                            enabled = hasReadableToc,
                            modifier = Modifier.testTag(NovelDetailTestTags.READ_TRAILING_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ExpandMore,
                                contentDescription = readMenuLabel,
                                modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                            )
                        }
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

        // Balanced 3-Button Secondary Action Row: normal screens keep inline icon+label buttons;
        // small/narrow screens or enlarged fonts switch to 3-column icon-above-label tonal tiles.
        val fontScale = LocalDensity.current.fontScale
        val shelfLabel = stringResource(
            if (inBookshelf) R.string.detail_shelf_added else R.string.detail_shelf_add,
        )
        val shelfIcon = if (inBookshelf) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkAdd
        val downloadIcon = if (snapshot.totalCachedChapters > 0 &&
            snapshot.totalCachedChapters >= snapshot.totalChapters &&
            snapshot.totalChapters > 0
        ) {
            Icons.Outlined.DownloadDone
        } else {
            Icons.Outlined.Download
        }
        val downloadLabel = if (activeDownload != null && activeDownload.totalChapters > 0) {
            "${activeDownload.completedChapters}/${activeDownload.totalChapters}"
        } else {
            stringResource(R.string.detail_action_download)
        }
        val reviewsLabel = stringResource(R.string.detail_action_reviews)

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useCompactStackedTiles = maxWidth < 348.dp || fontScale > 1.05f

            if (useCompactStackedTiles) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CompactStackedActionTile(
                        icon = shelfIcon,
                        label = shelfLabel,
                        isPrimarySelected = inBookshelf,
                        onClick = onToggleBookshelf,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(NovelDetailTestTags.SHELF_TOGGLE_BUTTON),
                    )
                    CompactStackedActionTile(
                        icon = downloadIcon,
                        label = downloadLabel,
                        isPrimarySelected = false,
                        onClick = onOpenDownloader,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(NovelDetailTestTags.DOWNLOAD_OPEN_BUTTON),
                    )
                    CompactStackedActionTile(
                        icon = Icons.AutoMirrored.Outlined.Chat,
                        label = reviewsLabel,
                        isPrimarySelected = false,
                        onClick = onOpenReviews,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(NovelDetailTestTags.REVIEWS_OPEN_BUTTON),
                    )
                }
            } else {
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
                                imageVector = shelfIcon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = shelfLabel,
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
                                imageVector = shelfIcon,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = shelfLabel,
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
                            imageVector = downloadIcon,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = downloadLabel,
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
                            text = reviewsLabel,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactStackedActionTile(
    icon: ImageVector,
    label: String,
    isPrimarySelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tileShape = MaterialTheme.shapes.medium
    val tileContent: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }

    if (isPrimarySelected) {
        FilledTonalButton(
            onClick = onClick,
            shape = tileShape,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
            modifier = modifier.minimumInteractiveComponentSize(),
        ) {
            tileContent()
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            shape = tileShape,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
            modifier = modifier.minimumInteractiveComponentSize(),
        ) {
            tileContent()
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(
                        R.string.download_progress_running_format,
                        progress.completedChapters,
                        progress.totalChapters,
                        progress.currentChapterTitle.ifBlank { "…" },
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onCancel,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    modifier = Modifier.testTag(NovelDetailTestTags.CANCEL_DOWNLOAD_BUTTON),
                ) {
                    Text(
                        text = stringResource(R.string.download_cancel_action),
                        maxLines = 1,
                        softWrap = false,
                    )
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
    val fontScale = LocalDensity.current.fontScale
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useStackedActions = maxWidth < 348.dp || fontScale > 1.05f
            if (useStackedActions) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.detail_toc_header_format, volumeCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        FilterChip(
                            selected = isSearchVisible,
                            onClick = onToggleSearch,
                            label = {
                                Text(
                                    text = stringResource(R.string.detail_toc_search_action),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Outlined.Search,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = Modifier.testTag(NovelDetailTestTags.TOC_SEARCH_TOGGLE),
                        )
                        AssistChip(
                            onClick = onToggleReverseOrder,
                            label = {
                                Text(
                                    text = if (isReverseOrder) {
                                        stringResource(R.string.detail_toc_order_desc)
                                    } else {
                                        stringResource(R.string.detail_toc_order_asc)
                                    },
                                    maxLines = 1,
                                    softWrap = false,
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
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.detail_toc_header_format, volumeCount),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    FilterChip(
                        selected = isSearchVisible,
                        onClick = onToggleSearch,
                        label = {
                            Text(
                                text = stringResource(R.string.detail_toc_search_action),
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
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
                                maxLines = 1,
                                softWrap = false,
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
            }
        }

        if (isSearchVisible) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                singleLine = true,
                placeholder = {
                    Text(
                        text = stringResource(R.string.detail_toc_search_placeholder),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        val clearLabel = stringResource(R.string.action_clear)
                        TooltipIconButton(
                            tooltipText = clearLabel,
                            onClick = { onSearchQueryChange("") },
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = clearLabel)
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
    onChapterClicked: (ChapterRowModel) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useStackedBadges = maxWidth < 348.dp || fontScale > 1.05f
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clickable(onClick = onToggleExpand)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                        .testTag(NovelDetailTestTags.VOLUME_HEADER_PREFIX + volume.vid),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (useStackedBadges) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = volume.volumeTitle,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (volume.containsReadingProgress || volume.cachedChapterCount > 0) {
                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
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
                                }
                            }
                        }
                    } else {
                        Text(
                            text = volume.volumeTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )

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
                        val hasChapterBadges = chapter.isCurrentReadingChapter ||
                            chapter.isIllustrationChapter ||
                            chapter.isCached
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
                            if (useStackedBadges) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
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
                                    )
                                    if (hasChapterBadges) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp),
                                        ) {
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
                            } else {
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
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
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
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Checkbox(checked = checked, onCheckedChange = null)
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
                            maxLines = 1,
                            softWrap = false,
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
    leadingIcon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.einkHairlineBorder(MaterialTheme.shapes.extraSmall),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
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

/**
 * Full-pane loading / retry state for a detail pane whose book has no local snapshot and no seed
 * card (typically: opened by `aid` after process death, or a two-pane switch to a book that is
 * neither shelved nor cached and whose first network refresh has not resolved yet). Replaces the
 * bare zeroed skeleton previously rendered in that state.
 */
@Composable
private fun NovelDetailUnresolvedOverlay(
    isRefreshing: Boolean,
    isEinkMode: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.testTag(NovelDetailTestTags.UNRESOLVED_STATE),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            if (isRefreshing) {
                if (isEinkMode) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.55f))
                } else {
                    LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(0.55f))
                }
                Text(
                    text = stringResource(R.string.detail_unresolved_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = stringResource(R.string.detail_unresolved_failed),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FilledTonalButton(onClick = onRetry) {
                    Text(text = stringResource(R.string.detail_unresolved_retry))
                }
            }
        }
    }
}
