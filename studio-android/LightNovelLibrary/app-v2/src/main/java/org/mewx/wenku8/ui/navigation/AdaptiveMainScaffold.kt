package org.mewx.wenku8.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.SystemClock
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.R
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.data.migration.LegacyV1StorageMigrator
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.diagnostics.AutoRoutingModeState
import org.mewx.wenku8.data.remote.diagnostics.RelayRouteController
import org.mewx.wenku8.data.download.NovelDownloadRepository
import org.mewx.wenku8.data.repository.BackupArchiveRepository
import org.mewx.wenku8.data.repository.BookshelfItemModel
import org.mewx.wenku8.data.repository.BookshelfRepository
import org.mewx.wenku8.data.repository.BookshelfSortMode
import org.mewx.wenku8.data.repository.BookshelfViewMode
import org.mewx.wenku8.data.repository.DiscoveryRepository
import org.mewx.wenku8.data.repository.NovelDetailRepository
import org.mewx.wenku8.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.data.repository.ReviewThreadCardItem
import org.mewx.wenku8.data.repository.ReviewsRepository
import org.mewx.wenku8.data.repository.ServerNoticeAndUpdateRepository
import org.mewx.wenku8.data.repository.StorageAndCacheRepository
import org.mewx.wenku8.data.repository.UserSessionRepository
import org.mewx.wenku8.data.update.AppUpdateAutoPrompt
import org.mewx.wenku8.data.update.AppUpdateCoordinator
import org.mewx.wenku8.data.update.AppUpdateUiState
import org.mewx.wenku8.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.domain.model.UnifiedSearchMode
import org.mewx.wenku8.reader.model.ReaderPageTurnMode
import org.mewx.wenku8.reader.model.ReaderPaperPreset
import org.mewx.wenku8.ui.bookshelf.BookshelfScreen
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.detail.NovelDetailScreen
import org.mewx.wenku8.ui.detail.ReaderLaunchRequest
import org.mewx.wenku8.ui.diagnostics.NetworkDiagnosticsScreen
import org.mewx.wenku8.ui.diagnostics.NetworkDiagnosticsViewModel
import org.mewx.wenku8.ui.discovery.LatestScreen
import org.mewx.wenku8.ui.discovery.PersistentSearchEntryPill
import org.mewx.wenku8.ui.discovery.RankingsScreen
import org.mewx.wenku8.ui.profile.AccountLoginBottomSheet
import org.mewx.wenku8.ui.profile.DirectUpdateAvailableDialog
import org.mewx.wenku8.ui.profile.ProfileAndStudioScreen
import org.mewx.wenku8.ui.profile.ReadingFootprintScreen
import org.mewx.wenku8.ui.profile.ReadingFootprintTab
import org.mewx.wenku8.ui.profile.openSafeBrowserUrl
import org.mewx.wenku8.ui.reader.UnifiedReaderScreen
import org.mewx.wenku8.ui.reviews.ReviewListScreen
import org.mewx.wenku8.ui.reviews.ReviewThreadScreen
import org.mewx.wenku8.ui.search.UnifiedSearchScreen
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.ProvideWenku8SharedTransition
import org.mewx.wenku8.ui.theme.Wenku8ScreenTransitions
import org.mewx.wenku8.ui.theme.Wenku8ScreenTransitions.hierarchicalScreenTransform
import org.mewx.wenku8.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder

/** Semantic UI test tags used by Robolectric & instrumented Compose tests. */
object MainScaffoldTestTags {
    const val COMPACT_SHORT_NAV_BAR = "main_compact_short_nav_bar"
    const val EXPANDED_WIDE_NAV_RAIL = "main_expanded_wide_nav_rail"
    const val OFFLINE_FALLBACK_BANNER = "main_offline_fallback_banner"
    const val DISMISS_OFFLINE_BANNER_BUTTON = "main_dismiss_offline_banner"
    const val DESTINATION_SCREEN_PREFIX = "destination_screen_"
    const val NAV_ITEM_PREFIX = "nav_item_"
    const val PROFILE_UPDATE_BADGE = "main_profile_update_badge"
    const val UPDATE_READY_SNACKBAR = "main_update_ready_snackbar"
}

private val MediumWindowWidthBreakpoint = 600.dp
private val ExpandedWindowWidthBreakpoint = 840.dp
private val TwoPaneDetailWindowWidthBreakpoint = 960.dp

@OptIn(ExperimentalSharedTransitionApi::class)
private sealed interface ScaffoldSceneState {
    val depth: Int

    data object HomeShell : ScaffoldSceneState {
        override val depth: Int = 0
    }

    data class SearchStudio(
        val sessionId: Int,
        val initialQuery: String,
        val initialMode: UnifiedSearchMode,
        val searchBarOrigin: String,
    ) : ScaffoldSceneState {
        override val depth: Int = 1
    }

    data class ReadingFootprint(
        val initialTab: ReadingFootprintTab,
    ) : ScaffoldSceneState {
        override val depth: Int = 1
    }

    data class CompactNovelDetail(
        val aid: Int,
        val sharedOrigin: String,
    ) : ScaffoldSceneState {
        override val depth: Int = 2
    }

    data class ReviewList(
        val aid: Int,
        val novelTitle: String,
    ) : ScaffoldSceneState {
        override val depth: Int = 3
    }

    data class ReviewThread(
        val aid: Int,
        val novelTitle: String,
        val thread: ReviewThreadCardItem,
    ) : ScaffoldSceneState {
        override val depth: Int = 4
    }

    data class Reader(
        val request: ReaderLaunchRequest,
    ) : ScaffoldSceneState {
        override val depth: Int = 5
    }

    data object NetworkDiagnostics : ScaffoldSceneState {
        override val depth: Int = 6
    }
}

private fun ScaffoldSceneState.saveableKey(): String = when (this) {
    ScaffoldSceneState.HomeShell -> "scene_home"
    is ScaffoldSceneState.SearchStudio -> {
        "scene_search_${sessionId}_${initialMode.name}_${searchBarOrigin}_$initialQuery"
    }
    is ScaffoldSceneState.ReadingFootprint -> "scene_footprint_${initialTab.name}"
    is ScaffoldSceneState.CompactNovelDetail -> "scene_detail_${aid}_$sharedOrigin"
    is ScaffoldSceneState.ReviewList -> "scene_review_list_${aid}_$novelTitle"
    is ScaffoldSceneState.ReviewThread -> "scene_review_thread_${aid}_${thread.rid}"
    is ScaffoldSceneState.Reader -> {
        "scene_reader_${request.aid}_${request.vid}_${request.cid}_${request.startWithTts}_" +
            "${request.initialParagraphIndex}_${request.initialCharOffset}"
    }
    ScaffoldSceneState.NetworkDiagnostics -> "scene_diagnostics"
}

/**
 * Adaptive Material 3 Expressive (`M3e`) 4-destination shell for Wenku8 `:app-v2`.
 *
 * Layout adaptation (`02-information-architecture-and-navigation.md` §3):
 * - **Compact width (`< 600.dp`, portrait phones)**: Renders M3e [ShortNavigationBar] at the bottom.
 * - **Medium / Expanded width (`>= 600.dp`, foldables, tablets, landscape)**: Switches seamlessly
 *   to M3e [WideNavigationRail] (collapsed on `600..839.dp`, expanded with labels on `>= 840.dp`)
 *   while preserving active destination state.
 * - **Shared-Element & Hierarchical Motion**: Encloses both the 4-tab home shell and full-screen
 *   sub-destinations in [SharedTransitionLayout] + [AnimatedContent] so the Searchbox pill/input
 *   and Light Novel Cover + Title morph smoothly across screens (and snap in `0ms` in E-Ink mode).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun AdaptiveMainScaffold(
    preferredHomeDestination: DefaultHomeDestination,
    isNetworkAvailableOnColdStart: Boolean,
    currentThemeMode: Wenku8ThemeMode,
    onThemeModeSelected: (Wenku8ThemeMode) -> Unit,
    onDefaultHomeSelected: (DefaultHomeDestination) -> Unit,
    modifier: Modifier = Modifier,
    userPreferences: Wenku8UserPreferences? = null,
    database: Wenku8Database? = null,
    catalogGateway: WenkuCatalogGateway? = null,
    novelTocSyncRepository: NovelTocSyncRepository? = null,
    relayRouteController: RelayRouteController? = null,
    discoveryRepository: DiscoveryRepository? = null,
    userSessionRepository: UserSessionRepository? = null,
    serverNoticeAndUpdateRepository: ServerNoticeAndUpdateRepository? = null,
    appUpdateCoordinator: AppUpdateCoordinator? = null,
    onLaunchPlayFlexibleUpdate: (() -> Boolean)? = null,
    onOpenDirectUpdateUrl: ((String) -> Unit)? = null,
    storageAndCacheRepository: StorageAndCacheRepository? = null,
    backupArchiveRepository: BackupArchiveRepository? = null,
    reviewsRepository: ReviewsRepository? = null,
    bookshelfRepository: BookshelfRepository? = null,
    novelDetailRepository: NovelDetailRepository? = null,
    novelDownloadRepository: NovelDownloadRepository? = null,
    legacyV1StorageMigrator: LegacyV1StorageMigrator? = null,
    appLanguage: Wenku8API.AppLanguage = Wenku8API.AppLanguage.SC,
    networkDiagnosticsViewModelFactory: NetworkDiagnosticsViewModel.Factory? = null,
    autoOptimizeConnectionEnabled: Boolean = false,
    onAutoOptimizeConnectionChanged: (Boolean) -> Unit = {},
    onBookshelfViewModeSelected: (BookshelfViewMode) -> Unit = {},
    onBookshelfSortModeSelected: (BookshelfSortMode) -> Unit = {},
    onAppLanguageSelected: (Wenku8API.AppLanguage) -> Unit = {},
    onEinkModeChanged: (Boolean) -> Unit = {},
    onReaderTypographyChanged: (
        fontSizeSp: Int,
        lineDistanceDp: Int,
        paragraphDistanceDp: Int,
        pageEdgeDistanceDp: Int,
    ) -> Unit = { _, _, _, _ -> },
    onReaderCustomFontPathChanged: (String) -> Unit = {},
    onReaderCustomBackgroundPathChanged: (String) -> Unit = {},
    onReaderPageTurnModeChanged: (ReaderPageTurnMode) -> Unit = {},
    onReaderPaperPresetChanged: (ReaderPaperPreset) -> Unit = {},
    onReaderKeepScreenOnChanged: (Boolean) -> Unit = {},
    onReaderEdgeBrightnessDragChanged: (Boolean) -> Unit = {},
    onVolumeKeyPagingChanged: (Boolean) -> Unit = {},
    initialOpenReviewsAid: Int = 0,
    initialOpenReviewsTitle: String = "",
    externalReaderLaunchRequest: ReaderLaunchRequest? = null,
    externalOpenBookshelf: Boolean = false,
    externalOpenNovelAid: Int = 0,
    onExternalLaunchConsumed: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onNovelClicked: (DiscoveryNovelCardItem) -> Unit = {},
    onOpenReader: (ReaderLaunchRequest) -> Unit = {},
    onOpenNetworkDiagnostics: () -> Unit = {},
    exitBackClockMonoMs: () -> Long = SystemClock::elapsedRealtime,
    onRequestExitApp: (() -> Unit)? = null,
) {
    val effectivePreferences = userPreferences ?: Wenku8UserPreferences(
        appLanguage = appLanguage,
        themeMode = currentThemeMode,
        einkModeEnabled = currentThemeMode == Wenku8ThemeMode.E_INK,
        defaultHomeDestination = preferredHomeDestination,
        autoOptimizeConnectionEnabled = autoOptimizeConnectionEnabled,
    )

    val initialResolution = rememberSaveable(
        inputs = arrayOf(preferredHomeDestination.name),
        saver = androidx.compose.runtime.saveable.Saver(
            save = { listOf(it.initialDestination.name, it.triggeredOfflineFallbackToBookshelf) },
            restore = { saved ->
                InitialHomeResolution(
                    initialDestination = TopLevelDestination.valueOf(saved[0] as String),
                    triggeredOfflineFallbackToBookshelf = saved[1] as Boolean,
                )
            },
        ),
    ) {
        resolveInitialHomeDestination(
            preferredHome = preferredHomeDestination,
            isNetworkAvailable = isNetworkAvailableOnColdStart,
        )
    }

    var selectedDestination by rememberSaveable {
        mutableStateOf(initialResolution.initialDestination)
    }
    var showOfflineFallbackBanner by rememberSaveable {
        mutableStateOf(initialResolution.triggeredOfflineFallbackToBookshelf)
    }
    val sceneSaveableStateHolder = rememberSaveableStateHolder()
    val homeTabSaveableStateHolder = rememberSaveableStateHolder()

    var localBookshelfViewMode by rememberSaveable(effectivePreferences.bookshelfViewMode) {
        mutableStateOf(effectivePreferences.bookshelfViewMode)
    }
    var localBookshelfSortMode by rememberSaveable(effectivePreferences.bookshelfSortMode) {
        mutableStateOf(effectivePreferences.bookshelfSortMode)
    }
    var bookshelfLocalQuery by rememberSaveable {
        mutableStateOf("")
    }
    var bookshelfActiveFilterKey by rememberSaveable {
        mutableStateOf("all")
    }
    var isSearchStudioOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var searchSessionId by rememberSaveable {
        mutableIntStateOf(0)
    }
    var searchInitialQuery by rememberSaveable {
        mutableStateOf("")
    }
    var searchInitialMode by rememberSaveable {
        mutableStateOf(UnifiedSearchMode.NOVEL_TITLE)
    }
    var activeSearchBarOrigin by rememberSaveable {
        mutableStateOf(Wenku8SharedElementKeys.SEARCH_BAR_DEFAULT_ORIGIN)
    }
    var isNetworkDiagnosticsOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var isLoginBottomSheetOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var activeDetailNovelAid by rememberSaveable {
        mutableStateOf(0)
    }
    var activeDetailSharedOrigin by rememberSaveable {
        mutableStateOf(Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN)
    }
    var activeDetailSeedItem by androidx.compose.runtime.remember {
        mutableStateOf<DiscoveryNovelCardItem?>(null)
    }
    var activeReviewsAid by rememberSaveable {
        mutableStateOf(initialOpenReviewsAid)
    }
    var activeReviewsNovelTitle by rememberSaveable {
        mutableStateOf(initialOpenReviewsTitle)
    }
    var activeReviewThread by androidx.compose.runtime.remember {
        mutableStateOf<ReviewThreadCardItem?>(null)
    }
    var activeReaderRequest by androidx.compose.runtime.remember {
        mutableStateOf<ReaderLaunchRequest?>(null)
    }
    var activeFootprintTab by rememberSaveable {
        mutableStateOf<ReadingFootprintTab?>(null)
    }
    var homeTabReselectCounter by androidx.compose.runtime.remember {
        mutableIntStateOf(0)
    }

    val handleOpenReader: (ReaderLaunchRequest) -> Unit = { request ->
        onOpenReader(request)
        if (database != null && catalogGateway != null && novelTocSyncRepository != null) {
            activeReaderRequest = request
        }
    }

    LaunchedEffect(externalReaderLaunchRequest, externalOpenBookshelf, externalOpenNovelAid) {
        if (externalOpenNovelAid > 0) {
            if (activeDetailNovelAid > 0 && activeDetailNovelAid != externalOpenNovelAid) {
                sceneSaveableStateHolder.removeState(
                    ScaffoldSceneState.CompactNovelDetail(
                        aid = activeDetailNovelAid,
                        sharedOrigin = activeDetailSharedOrigin,
                    ).saveableKey(),
                )
            }
            if (isSearchStudioOpen) {
                sceneSaveableStateHolder.removeState(
                    ScaffoldSceneState.SearchStudio(
                        sessionId = searchSessionId,
                        initialQuery = searchInitialQuery,
                        initialMode = searchInitialMode,
                        searchBarOrigin = activeSearchBarOrigin,
                    ).saveableKey(),
                )
                discoveryRepository?.resetOnlineSearchSession()
                isSearchStudioOpen = false
                searchInitialQuery = ""
            }
            activeFootprintTab?.let { tab ->
                sceneSaveableStateHolder.removeState(
                    ScaffoldSceneState.ReadingFootprint(tab).saveableKey(),
                )
            }
            activeFootprintTab = null
            isNetworkDiagnosticsOpen = false
            activeReaderRequest = null
            activeReviewsAid = 0
            activeReviewThread = null
            isLoginBottomSheetOpen = false

            if (novelDetailRepository != null &&
                bookshelfRepository != null &&
                novelDownloadRepository != null
            ) {
                activeDetailSeedItem = null
                activeDetailSharedOrigin = Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN
                activeDetailNovelAid = externalOpenNovelAid
            }
            onExternalLaunchConsumed()
        } else if (externalReaderLaunchRequest != null) {
            handleOpenReader(externalReaderLaunchRequest)
            onExternalLaunchConsumed()
        } else if (externalOpenBookshelf) {
            selectedDestination = TopLevelDestination.BOOKSHELF
            onExternalLaunchConsumed()
        }
    }

    val handleOpenSearchWithOrigin: (String) -> Unit = { barOrigin ->
        onOpenSearch()
        if (discoveryRepository != null) {
            if (isSearchStudioOpen) {
                sceneSaveableStateHolder.removeState(
                    ScaffoldSceneState.SearchStudio(
                        sessionId = searchSessionId,
                        initialQuery = searchInitialQuery,
                        initialMode = searchInitialMode,
                        searchBarOrigin = activeSearchBarOrigin,
                    ).saveableKey(),
                )
            }
            discoveryRepository.resetOnlineSearchSession()
            searchSessionId++
            activeSearchBarOrigin = barOrigin
            searchInitialQuery = ""
            searchInitialMode = UnifiedSearchMode.NOVEL_TITLE
            isSearchStudioOpen = true
        }
    }

    val handleOpenSearch = {
        val inferredOrigin = if (selectedDestination == TopLevelDestination.BOOKSHELF) {
            Wenku8SharedElementKeys.SEARCH_BAR_BOOKSHELF_ORIGIN
        } else {
            Wenku8SharedElementKeys.SEARCH_BAR_DEFAULT_ORIGIN
        }
        handleOpenSearchWithOrigin(inferredOrigin)
    }

    val handleOpenSearchWithSeed: (String, UnifiedSearchMode) -> Unit = { query, mode ->
        onOpenSearch()
        if (discoveryRepository != null) {
            if (activeDetailNovelAid > 0) {
                sceneSaveableStateHolder.removeState(
                    ScaffoldSceneState.CompactNovelDetail(
                        aid = activeDetailNovelAid,
                        sharedOrigin = activeDetailSharedOrigin,
                    ).saveableKey(),
                )
            }
            if (isSearchStudioOpen) {
                sceneSaveableStateHolder.removeState(
                    ScaffoldSceneState.SearchStudio(
                        sessionId = searchSessionId,
                        initialQuery = searchInitialQuery,
                        initialMode = searchInitialMode,
                        searchBarOrigin = activeSearchBarOrigin,
                    ).saveableKey(),
                )
            }
            discoveryRepository.resetOnlineSearchSession()
            searchSessionId++
            activeDetailNovelAid = 0
            activeDetailSeedItem = null
            activeSearchBarOrigin = if (selectedDestination == TopLevelDestination.BOOKSHELF) {
                Wenku8SharedElementKeys.SEARCH_BAR_BOOKSHELF_ORIGIN
            } else {
                Wenku8SharedElementKeys.SEARCH_BAR_DEFAULT_ORIGIN
            }
            searchInitialQuery = query
            searchInitialMode = mode
            isSearchStudioOpen = true
        }
    }

    val handleNovelClickedWithOrigin: (DiscoveryNovelCardItem, String) -> Unit = { item, origin ->
        onNovelClicked(item)
        if (novelDetailRepository != null && bookshelfRepository != null && novelDownloadRepository != null) {
            activeDetailSeedItem = item
            activeDetailSharedOrigin = origin
            activeDetailNovelAid = item.aid
        }
    }

    val handleNovelClicked: (DiscoveryNovelCardItem) -> Unit = { item ->
        handleNovelClickedWithOrigin(item, Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN)
    }

    val handleContinueReadingClicked: (BookshelfItemModel) -> Unit = { item ->
        val prog = item.readingProgress
        if (prog != null && prog.cid > 0 &&
            database != null && catalogGateway != null && novelTocSyncRepository != null
        ) {
            handleOpenReader(
                ReaderLaunchRequest(
                    aid = item.aid,
                    vid = prog.vid,
                    cid = prog.cid,
                    novelTitle = item.novel.title,
                    volumeTitle = prog.volumeTitle,
                    chapterTitle = prog.chapterTitle,
                    initialParagraphIndex = prog.paragraphIndex,
                    initialCharOffset = prog.charOffset,
                ),
            )
        } else {
            handleNovelClickedWithOrigin(
                DiscoveryNovelCardItem(
                    aid = item.aid,
                    title = item.novel.title,
                    coverUrl = item.coverUrl,
                    author = item.novel.author,
                    bookStatus = item.novel.bookStatus,
                    lastUpdate = item.novel.lastUpdate,
                    tags = item.tags,
                    introShort = item.novel.introShort,
                ),
                Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CAROUSEL_ORIGIN,
            )
        }
    }

    val handleOpenNetworkDiagnostics = {
        onOpenNetworkDiagnostics()
        if (networkDiagnosticsViewModelFactory != null) {
            isNetworkDiagnosticsOpen = true
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val isCompactWidth = maxWidth < MediumWindowWidthBreakpoint
        val isExpandedRailWidth = maxWidth >= ExpandedWindowWidthBreakpoint
        val isEink = LocalEinkMode.current
        val showTwoPaneExpandedDetail = maxWidth >= TwoPaneDetailWindowWidthBreakpoint &&
            !isSearchStudioOpen &&
            activeDetailNovelAid > 0 &&
            novelDetailRepository != null &&
            bookshelfRepository != null &&
            novelDownloadRepository != null

        val activeScene: ScaffoldSceneState = when {
            isNetworkDiagnosticsOpen && networkDiagnosticsViewModelFactory != null -> {
                ScaffoldSceneState.NetworkDiagnostics
            }
            activeReaderRequest != null &&
                database != null &&
                catalogGateway != null &&
                novelTocSyncRepository != null -> {
                ScaffoldSceneState.Reader(activeReaderRequest!!)
            }
            activeReviewsAid > 0 && reviewsRepository != null -> {
                val thread = activeReviewThread
                if (thread != null) {
                    ScaffoldSceneState.ReviewThread(
                        aid = activeReviewsAid,
                        novelTitle = activeReviewsNovelTitle,
                        thread = thread,
                    )
                } else {
                    ScaffoldSceneState.ReviewList(
                        aid = activeReviewsAid,
                        novelTitle = activeReviewsNovelTitle,
                    )
                }
            }
            activeDetailNovelAid > 0 &&
                !showTwoPaneExpandedDetail &&
                novelDetailRepository != null &&
                bookshelfRepository != null &&
                novelDownloadRepository != null -> {
                ScaffoldSceneState.CompactNovelDetail(
                    aid = activeDetailNovelAid,
                    sharedOrigin = activeDetailSharedOrigin,
                )
            }
            activeFootprintTab != null && database != null -> {
                ScaffoldSceneState.ReadingFootprint(activeFootprintTab!!)
            }
            isSearchStudioOpen && discoveryRepository != null -> {
                ScaffoldSceneState.SearchStudio(
                    sessionId = searchSessionId,
                    initialQuery = searchInitialQuery,
                    initialMode = searchInitialMode,
                    searchBarOrigin = activeSearchBarOrigin,
                )
            }
            else -> ScaffoldSceneState.HomeShell
        }

        // Preserve v1's classic double-back-to-exit (`MainActivity.exitBy2Click`, 2000ms window).
        // Registered outer-most so inner BackHandlers (child scenes, two-pane detail, bookshelf
        // multi-select/local filter, bottom sheets, and dialogs) always take precedence first.
        val scaffoldContext = LocalContext.current
        val scaffoldCoroutineScope = rememberCoroutineScope()
        var lastExitBackPressMonoMs by remember { mutableLongStateOf(-DOUBLE_BACK_EXIT_WINDOW_MS) }
        var activeExitToast by remember { mutableStateOf<Toast?>(null) }
        val isAtRootHomeShell = activeScene == ScaffoldSceneState.HomeShell &&
            !showTwoPaneExpandedDetail &&
            !isLoginBottomSheetOpen

        val appUpdateUiState: AppUpdateUiState = if (appUpdateCoordinator != null) {
            val state by appUpdateCoordinator.uiState.collectAsStateWithLifecycle()
            state
        } else {
            AppUpdateUiState.Idle
        }
        val pendingAutoUpdatePrompt: AppUpdateAutoPrompt? = if (appUpdateCoordinator != null) {
            val prompt by appUpdateCoordinator.pendingAutoPrompt.collectAsStateWithLifecycle()
            prompt
        } else {
            null
        }
        val showProfileUpdateBadge: Boolean = if (appUpdateCoordinator != null) {
            val badge by appUpdateCoordinator.showProfileUpdateBadge.collectAsStateWithLifecycle()
            badge
        } else {
            false
        }

        var hasTriggeredColdStartUpdateCheck by rememberSaveable { mutableStateOf(false) }
        LaunchedEffect(appUpdateCoordinator, isNetworkAvailableOnColdStart) {
            if (appUpdateCoordinator != null &&
                isNetworkAvailableOnColdStart &&
                !hasTriggeredColdStartUpdateCheck
            ) {
                hasTriggeredColdStartUpdateCheck = true
                appUpdateCoordinator.checkForUpdate(isUserInitiated = false)
            }
        }

        LaunchedEffect(appUpdateCoordinator, activeScene, selectedDestination, showProfileUpdateBadge) {
            if (appUpdateCoordinator != null &&
                activeScene == ScaffoldSceneState.HomeShell &&
                selectedDestination == TopLevelDestination.PROFILE &&
                showProfileUpdateBadge
            ) {
                appUpdateCoordinator.clearProfileUpdateBadge()
            }
        }

        val canSurfaceAppUpdatePrompt = isAtRootHomeShell &&
            externalReaderLaunchRequest == null &&
            externalOpenNovelAid <= 0

        LaunchedEffect(appUpdateCoordinator, pendingAutoUpdatePrompt, canSurfaceAppUpdatePrompt) {
            if (appUpdateCoordinator != null &&
                canSurfaceAppUpdatePrompt &&
                pendingAutoUpdatePrompt is AppUpdateAutoPrompt.PlayStoreFlexible
            ) {
                onLaunchPlayFlexibleUpdate?.invoke()
            }
        }

        val updateSnackbarHostState = remember { SnackbarHostState() }
        val readyToInstallMessage = stringResource(R.string.update_ready_to_install)
        val restartInstallLabel = stringResource(R.string.update_action_restart_install)
        val updateBadgeContentDescription = stringResource(R.string.update_badge_content_desc)
        val canShowReadyUpdateSnackbar = appUpdateUiState is AppUpdateUiState.ReadyToInstall &&
            activeScene !is ScaffoldSceneState.Reader

        LaunchedEffect(canShowReadyUpdateSnackbar, readyToInstallMessage, restartInstallLabel) {
            if (canShowReadyUpdateSnackbar) {
                val snackbarResult = updateSnackbarHostState.showSnackbar(
                    message = readyToInstallMessage,
                    actionLabel = restartInstallLabel,
                    duration = SnackbarDuration.Indefinite,
                )
                if (snackbarResult == SnackbarResult.ActionPerformed) {
                    appUpdateCoordinator?.completeUpdate()
                }
            } else {
                updateSnackbarHostState.currentSnackbarData?.dismiss()
            }
        }

        LaunchedEffect(isAtRootHomeShell, selectedDestination) {
            lastExitBackPressMonoMs = -DOUBLE_BACK_EXIT_WINDOW_MS
        }

        BackHandler(enabled = isAtRootHomeShell) {
            val nowMono = exitBackClockMonoMs()
            if (nowMono - lastExitBackPressMonoMs in 0L until DOUBLE_BACK_EXIT_WINDOW_MS) {
                lastExitBackPressMonoMs = -DOUBLE_BACK_EXIT_WINDOW_MS
                activeExitToast?.cancel()
                activeExitToast = null
                if (onRequestExitApp != null) {
                    onRequestExitApp()
                } else {
                    scaffoldContext.findScaffoldHostActivity()?.finish()
                }
            } else {
                lastExitBackPressMonoMs = nowMono
                activeExitToast?.cancel()
                activeExitToast = Toast.makeText(
                    scaffoldContext,
                    scaffoldContext.getText(R.string.press_twice_to_exit),
                    Toast.LENGTH_SHORT,
                ).also { it.show() }
            }
        }

        SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = activeScene,
                transitionSpec = {
                    val isSearchMorph = initialState is ScaffoldSceneState.SearchStudio ||
                        targetState is ScaffoldSceneState.SearchStudio
                    val isReaderMorph = initialState is ScaffoldSceneState.Reader ||
                        targetState is ScaffoldSceneState.Reader
                    hierarchicalScreenTransform(
                        initialDepth = initialState.depth,
                        targetDepth = targetState.depth,
                        isEinkMode = isEink,
                        isSearchTransition = isSearchMorph,
                        isReaderTransition = isReaderMorph,
                    )
                },
                label = "Wenku8HierarchicalSceneTransition",
                modifier = Modifier.fillMaxSize(),
            ) { scene ->
                sceneSaveableStateHolder.SaveableStateProvider(key = scene.saveableKey()) {
                    ProvideWenku8SharedTransition(
                        sharedTransitionScope = this@SharedTransitionLayout,
                        animatedVisibilityScope = this@AnimatedContent,
                    ) {
                    when (scene) {
                        ScaffoldSceneState.HomeShell -> {
                            if (isCompactWidth) {
                                Scaffold(
                                    contentWindowInsets = WindowInsets.safeDrawing,
                                    bottomBar = {
                                        ShortNavigationBar(
                                            modifier = Modifier
                                                .testTag(MainScaffoldTestTags.COMPACT_SHORT_NAV_BAR)
                                                .einkHairlineBorder(MaterialTheme.shapes.extraSmall),
                                        ) {
                                            TopLevelDestination.entries.forEach { destination ->
                                                val isSelected = destination == selectedDestination
                                                val labelText = stringResource(destination.labelResId)
                                                ShortNavigationBarItem(
                                                    selected = isSelected,
                                                    onClick = {
                                                        if (destination == selectedDestination) {
                                                            if (destination == TopLevelDestination.BOOKSHELF &&
                                                                bookshelfLocalQuery.isNotEmpty()
                                                            ) {
                                                                bookshelfLocalQuery = ""
                                                            }
                                                            homeTabReselectCounter++
                                                        } else {
                                                            selectedDestination = destination
                                                        }
                                                    },
                                                    icon = {
                                                        val navIcon: @Composable () -> Unit = {
                                                            Icon(
                                                                imageVector = if (isSelected) {
                                                                    destination.selectedIcon
                                                                } else {
                                                                    destination.unselectedIcon
                                                                },
                                                                contentDescription = labelText,
                                                            )
                                                        }
                                                        if (destination == TopLevelDestination.PROFILE &&
                                                            showProfileUpdateBadge
                                                        ) {
                                                            BadgedBox(
                                                                badge = {
                                                                    Badge(
                                                                        modifier = Modifier
                                                                            .testTag(MainScaffoldTestTags.PROFILE_UPDATE_BADGE)
                                                                            .semantics {
                                                                                contentDescription = updateBadgeContentDescription
                                                                            },
                                                                    )
                                                                },
                                                            ) {
                                                                navIcon()
                                                            }
                                                        } else {
                                                            navIcon()
                                                        }
                                                    },
                                                    label = {
                                                        Text(
                                                            text = labelText,
                                                            maxLines = 1,
                                                            overflow = TextOverflow.Ellipsis,
                                                        )
                                                    },
                                                    modifier = Modifier.testTag(
                                                        MainScaffoldTestTags.NAV_ITEM_PREFIX + destination.routeKey,
                                                    ),
                                                )
                                            }
                                        }
                                    },
                                ) { innerPadding ->
                                    AnimatedContent(
                                        targetState = selectedDestination,
                                        transitionSpec = {
                                            Wenku8ScreenTransitions.topLevelTabTransform(
                                                initialOrdinal = initialState.ordinal,
                                                targetOrdinal = targetState.ordinal,
                                                isEinkMode = isEink,
                                            )
                                        },
                                        label = "Wenku8CompactTabTransition",
                                        modifier = Modifier.fillMaxSize(),
                                    ) { destination ->
                                        homeTabSaveableStateHolder.SaveableStateProvider(
                                            key = "home_tab_${destination.routeKey}",
                                        ) {
                                            MainDestinationBody(
                                                selectedDestination = destination,
                                                reselectScrollToTopTrigger = homeTabReselectCounter,
                                                showOfflineFallbackBanner = showOfflineFallbackBanner,
                                                onDismissOfflineBanner = { showOfflineFallbackBanner = false },
                                                preferences = effectivePreferences,
                                                onThemeModeSelected = onThemeModeSelected,
                                                onDefaultHomeSelected = onDefaultHomeSelected,
                                                onAutoOptimizeConnectionChanged = onAutoOptimizeConnectionChanged,
                                                onAppLanguageSelected = onAppLanguageSelected,
                                                onEinkModeChanged = onEinkModeChanged,
                                                onReaderTypographyChanged = onReaderTypographyChanged,
                                                onReaderCustomFontPathChanged = onReaderCustomFontPathChanged,
                                                onReaderCustomBackgroundPathChanged = onReaderCustomBackgroundPathChanged,
                                                onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
                                                database = database,
                                                relayRouteController = relayRouteController,
                                                discoveryRepository = discoveryRepository,
                                                userSessionRepository = userSessionRepository,
                                                serverNoticeAndUpdateRepository = serverNoticeAndUpdateRepository,
                                                appUpdateCoordinator = appUpdateCoordinator,
                                                storageAndCacheRepository = storageAndCacheRepository,
                                                backupArchiveRepository = backupArchiveRepository,
                                                bookshelfRepository = bookshelfRepository,
                                                bookshelfViewMode = localBookshelfViewMode,
                                                onBookshelfViewModeSelected = { mode ->
                                                    localBookshelfViewMode = mode
                                                    onBookshelfViewModeSelected(mode)
                                                },
                                                bookshelfSortMode = localBookshelfSortMode,
                                                onBookshelfSortModeSelected = { sort ->
                                                    localBookshelfSortMode = sort
                                                    onBookshelfSortModeSelected(sort)
                                                },
                                                bookshelfLocalQuery = bookshelfLocalQuery,
                                                onBookshelfLocalQueryChanged = { bookshelfLocalQuery = it },
                                                bookshelfActiveFilterKey = bookshelfActiveFilterKey,
                                                onBookshelfActiveFilterKeyChanged = { bookshelfActiveFilterKey = it },
                                                legacyV1StorageMigrator = legacyV1StorageMigrator,
                                                appLanguage = appLanguage,
                                                onOpenSearch = handleOpenSearch,
                                                onOpenSearchWithOrigin = handleOpenSearchWithOrigin,
                                                onOpenSearchWithSeed = handleOpenSearchWithSeed,
                                                onOpenLoginSheet = { isLoginBottomSheetOpen = true },
                                                onOpenReadingFootprint = { tab -> activeFootprintTab = tab },
                                                onNovelClicked = handleNovelClicked,
                                                onNovelClickedWithOrigin = handleNovelClickedWithOrigin,
                                                onContinueReadingClicked = handleContinueReadingClicked,
                                                onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
                                                contentPadding = innerPadding,
                                            )
                                        }
                                    }
                                }
                            } else {
                                val shouldExpandRail = isExpandedRailWidth && !showTwoPaneExpandedDetail
                                val railState = rememberWideNavigationRailState(
                                    initialValue = if (shouldExpandRail) {
                                        WideNavigationRailValue.Expanded
                                    } else {
                                        WideNavigationRailValue.Collapsed
                                    },
                                )
                                LaunchedEffect(shouldExpandRail) {
                                    if (shouldExpandRail) {
                                        railState.expand()
                                    } else {
                                        railState.collapse()
                                    }
                                }
                                Row(modifier = Modifier.fillMaxSize()) {
                                    WideNavigationRail(
                                        state = railState,
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .testTag(MainScaffoldTestTags.EXPANDED_WIDE_NAV_RAIL)
                                            .einkHairlineBorder(MaterialTheme.shapes.extraSmall),
                                    ) {
                                        TopLevelDestination.entries.forEach { destination ->
                                            val isSelected = destination == selectedDestination
                                            val labelText = stringResource(destination.labelResId)
                                            WideNavigationRailItem(
                                                railExpanded = railState.targetValue == WideNavigationRailValue.Expanded,
                                                selected = isSelected,
                                                onClick = {
                                                    if (destination == selectedDestination) {
                                                        if (destination == TopLevelDestination.BOOKSHELF &&
                                                            bookshelfLocalQuery.isNotEmpty()
                                                        ) {
                                                            bookshelfLocalQuery = ""
                                                        }
                                                        homeTabReselectCounter++
                                                    } else {
                                                        selectedDestination = destination
                                                        activeDetailNovelAid = 0
                                                        activeDetailSeedItem = null
                                                    }
                                                },
                                                icon = {
                                                    val navIcon: @Composable () -> Unit = {
                                                        Icon(
                                                            imageVector = if (isSelected) {
                                                                destination.selectedIcon
                                                            } else {
                                                                destination.unselectedIcon
                                                            },
                                                            contentDescription = labelText,
                                                        )
                                                    }
                                                    if (destination == TopLevelDestination.PROFILE &&
                                                        showProfileUpdateBadge
                                                    ) {
                                                        BadgedBox(
                                                            badge = {
                                                                Badge(
                                                                    modifier = Modifier
                                                                        .testTag(MainScaffoldTestTags.PROFILE_UPDATE_BADGE)
                                                                        .semantics {
                                                                            contentDescription = updateBadgeContentDescription
                                                                        },
                                                                )
                                                            },
                                                        ) {
                                                            navIcon()
                                                        }
                                                    } else {
                                                        navIcon()
                                                    }
                                                },
                                                label = {
                                                    Text(
                                                        text = labelText,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis,
                                                    )
                                                },
                                                modifier = Modifier.testTag(
                                                    MainScaffoldTestTags.NAV_ITEM_PREFIX + destination.routeKey,
                                                ),
                                            )
                                        }
                                    }

                                    Scaffold(
                                        modifier = Modifier.weight(if (showTwoPaneExpandedDetail) 0.44f else 1f),
                                        contentWindowInsets = WindowInsets.safeDrawing.only(
                                            WindowInsetsSides.Top + WindowInsetsSides.Bottom + WindowInsetsSides.End,
                                        ),
                                    ) { innerPadding ->
                                        AnimatedContent(
                                            targetState = selectedDestination,
                                            transitionSpec = {
                                                Wenku8ScreenTransitions.topLevelTabTransform(
                                                    initialOrdinal = initialState.ordinal,
                                                    targetOrdinal = targetState.ordinal,
                                                    isEinkMode = isEink,
                                                )
                                            },
                                            label = "Wenku8ExpandedTabTransition",
                                            modifier = Modifier.fillMaxSize(),
                                        ) { destination ->
                                            homeTabSaveableStateHolder.SaveableStateProvider(
                                                key = "home_tab_${destination.routeKey}",
                                            ) {
                                                MainDestinationBody(
                                                    selectedDestination = destination,
                                                    reselectScrollToTopTrigger = homeTabReselectCounter,
                                                    showOfflineFallbackBanner = showOfflineFallbackBanner,
                                                    onDismissOfflineBanner = { showOfflineFallbackBanner = false },
                                                    preferences = effectivePreferences,
                                                    onThemeModeSelected = onThemeModeSelected,
                                                    onDefaultHomeSelected = onDefaultHomeSelected,
                                                    onAutoOptimizeConnectionChanged = onAutoOptimizeConnectionChanged,
                                                    onAppLanguageSelected = onAppLanguageSelected,
                                                    onEinkModeChanged = onEinkModeChanged,
                                                    onReaderTypographyChanged = onReaderTypographyChanged,
                                                    onReaderCustomFontPathChanged = onReaderCustomFontPathChanged,
                                                    onReaderCustomBackgroundPathChanged = onReaderCustomBackgroundPathChanged,
                                                    onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
                                                    database = database,
                                                    relayRouteController = relayRouteController,
                                                    discoveryRepository = discoveryRepository,
                                                    userSessionRepository = userSessionRepository,
                                                    serverNoticeAndUpdateRepository = serverNoticeAndUpdateRepository,
                                                    appUpdateCoordinator = appUpdateCoordinator,
                                                    storageAndCacheRepository = storageAndCacheRepository,
                                                    backupArchiveRepository = backupArchiveRepository,
                                                    bookshelfRepository = bookshelfRepository,
                                                    bookshelfViewMode = localBookshelfViewMode,
                                                    onBookshelfViewModeSelected = { mode ->
                                                        localBookshelfViewMode = mode
                                                        onBookshelfViewModeSelected(mode)
                                                    },
                                                    bookshelfSortMode = localBookshelfSortMode,
                                                    onBookshelfSortModeSelected = { sort ->
                                                        localBookshelfSortMode = sort
                                                        onBookshelfSortModeSelected(sort)
                                                    },
                                                    bookshelfLocalQuery = bookshelfLocalQuery,
                                                    onBookshelfLocalQueryChanged = { bookshelfLocalQuery = it },
                                                    bookshelfActiveFilterKey = bookshelfActiveFilterKey,
                                                    onBookshelfActiveFilterKeyChanged = { bookshelfActiveFilterKey = it },
                                                    legacyV1StorageMigrator = legacyV1StorageMigrator,
                                                    appLanguage = appLanguage,
                                                    onOpenSearch = handleOpenSearch,
                                                    onOpenSearchWithOrigin = handleOpenSearchWithOrigin,
                                                    onOpenSearchWithSeed = handleOpenSearchWithSeed,
                                                    onOpenLoginSheet = { isLoginBottomSheetOpen = true },
                                                    onOpenReadingFootprint = { tab -> activeFootprintTab = tab },
                                                    onNovelClicked = handleNovelClicked,
                                                    onNovelClickedWithOrigin = handleNovelClickedWithOrigin,
                                                    onContinueReadingClicked = handleContinueReadingClicked,
                                                    onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
                                                    contentPadding = innerPadding,
                                                )
                                            }
                                        }
                                    }

                                    AnimatedVisibility(
                                        visible = showTwoPaneExpandedDetail,
                                        enter = if (isEink) {
                                            androidx.compose.animation.EnterTransition.None
                                        } else {
                                            fadeIn(tween(180)) + slideInHorizontally(
                                                animationSpec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 420f),
                                                initialOffsetX = { it / 6 },
                                            )
                                        },
                                        exit = if (isEink) {
                                            androidx.compose.animation.ExitTransition.None
                                        } else {
                                            fadeOut(tween(140)) + slideOutHorizontally(
                                                animationSpec = spring<IntOffset>(dampingRatio = 0.9f, stiffness = 420f),
                                                targetOffsetX = { it / 6 },
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(0.56f)
                                            .fillMaxHeight(),
                                    ) {
                                        if (novelDetailRepository != null &&
                                            bookshelfRepository != null &&
                                            novelDownloadRepository != null &&
                                            activeDetailNovelAid > 0
                                        ) {
                                            NovelDetailScreen(
                                                aid = activeDetailNovelAid,
                                                novelDetailRepository = novelDetailRepository,
                                                bookshelfRepository = bookshelfRepository,
                                                novelDownloadRepository = novelDownloadRepository,
                                                appLanguage = appLanguage,
                                                themeMode = effectivePreferences.themeMode,
                                                useCoverTheme = effectivePreferences.themeMode == Wenku8ThemeMode.SYSTEM,
                                                seedDiscoveryItem = activeDetailSeedItem?.takeIf {
                                                    it.aid == activeDetailNovelAid
                                                },
                                                sharedTransitionOrigin = activeDetailSharedOrigin,
                                                showBackButton = true,
                                                onNavigateBack = {
                                                    activeDetailNovelAid = 0
                                                    activeDetailSeedItem = null
                                                },
                                                onOpenReader = handleOpenReader,
                                                onOpenReviews = { aid, title ->
                                                    activeReviewsAid = aid
                                                    activeReviewsNovelTitle = title
                                                    activeReviewThread = null
                                                },
                                                onAuthorClicked = { author ->
                                                    handleOpenSearchWithSeed(author, UnifiedSearchMode.AUTHOR_NAME)
                                                },
                                                onTagClicked = { tag ->
                                                    handleOpenSearchWithSeed(tag, UnifiedSearchMode.NOVEL_TITLE)
                                                },
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        is ScaffoldSceneState.SearchStudio -> {
                            if (discoveryRepository != null) {
                                UnifiedSearchScreen(
                                    discoveryRepository = discoveryRepository,
                                    appLanguage = appLanguage,
                                    initialQuery = scene.initialQuery,
                                    initialMode = scene.initialMode,
                                    searchBarSharedOrigin = scene.searchBarOrigin,
                                    onNavigateBack = {
                                        sceneSaveableStateHolder.removeState(scene.saveableKey())
                                        discoveryRepository.resetOnlineSearchSession()
                                        isSearchStudioOpen = false
                                        searchInitialQuery = ""
                                    },
                                    onNovelClicked = handleNovelClicked,
                                    onNovelClickedWithOrigin = handleNovelClickedWithOrigin,
                                    onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
                                )
                            }
                        }

                        is ScaffoldSceneState.ReadingFootprint -> {
                            if (database != null) {
                                ReadingFootprintScreen(
                                    database = database,
                                    initialTab = scene.initialTab,
                                    onNavigateBack = {
                                        sceneSaveableStateHolder.removeState(scene.saveableKey())
                                        activeFootprintTab = null
                                    },
                                    onOpenReader = handleOpenReader,
                                    onOpenNovelDetail = { item ->
                                        if (isExpandedRailWidth) {
                                            sceneSaveableStateHolder.removeState(scene.saveableKey())
                                            activeFootprintTab = null
                                        }
                                        handleNovelClickedWithOrigin(
                                            item,
                                            Wenku8SharedElementKeys.NOVEL_FOOTPRINT_HISTORY_ORIGIN,
                                        )
                                    },
                                )
                            }
                        }

                        is ScaffoldSceneState.CompactNovelDetail -> {
                            if (novelDetailRepository != null &&
                                bookshelfRepository != null &&
                                novelDownloadRepository != null
                            ) {
                                NovelDetailScreen(
                                    aid = scene.aid,
                                    novelDetailRepository = novelDetailRepository,
                                    bookshelfRepository = bookshelfRepository,
                                    novelDownloadRepository = novelDownloadRepository,
                                    appLanguage = appLanguage,
                                    themeMode = effectivePreferences.themeMode,
                                    useCoverTheme = effectivePreferences.themeMode == Wenku8ThemeMode.SYSTEM,
                                    seedDiscoveryItem = activeDetailSeedItem?.takeIf { it.aid == scene.aid },
                                    sharedTransitionOrigin = scene.sharedOrigin,
                                    onNavigateBack = {
                                        sceneSaveableStateHolder.removeState(scene.saveableKey())
                                        activeDetailNovelAid = 0
                                        activeDetailSeedItem = null
                                    },
                                    onOpenReader = handleOpenReader,
                                    onOpenReviews = { aid, title ->
                                        activeReviewsAid = aid
                                        activeReviewsNovelTitle = title
                                        activeReviewThread = null
                                    },
                                    onAuthorClicked = { author ->
                                        handleOpenSearchWithSeed(author, UnifiedSearchMode.AUTHOR_NAME)
                                    },
                                    onTagClicked = { tag ->
                                        handleOpenSearchWithSeed(tag, UnifiedSearchMode.NOVEL_TITLE)
                                    },
                                )
                            }
                        }

                        is ScaffoldSceneState.ReviewList -> {
                            if (reviewsRepository != null) {
                                ReviewListScreen(
                                    aid = scene.aid,
                                    novelTitle = scene.novelTitle,
                                    reviewsRepository = reviewsRepository,
                                    appLanguage = appLanguage,
                                    userSessionRepository = userSessionRepository,
                                    onNavigateBack = {
                                        sceneSaveableStateHolder.removeState(scene.saveableKey())
                                        activeReviewsAid = 0
                                        activeReviewsNovelTitle = ""
                                        activeReviewThread = null
                                    },
                                    onOpenThread = { thread -> activeReviewThread = thread },
                                    onOpenLoginSheet = { isLoginBottomSheetOpen = true },
                                    onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
                                )
                            }
                        }

                        is ScaffoldSceneState.ReviewThread -> {
                            if (reviewsRepository != null) {
                                ReviewThreadScreen(
                                    aid = scene.aid,
                                    novelTitle = scene.novelTitle,
                                    thread = scene.thread,
                                    reviewsRepository = reviewsRepository,
                                    appLanguage = appLanguage,
                                    userSessionRepository = userSessionRepository,
                                    onNavigateBack = {
                                        sceneSaveableStateHolder.removeState(scene.saveableKey())
                                        activeReviewThread = null
                                    },
                                    onOpenLoginSheet = { isLoginBottomSheetOpen = true },
                                    onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
                                )
                            }
                        }

                        is ScaffoldSceneState.Reader -> {
                            if (database != null &&
                                catalogGateway != null &&
                                novelTocSyncRepository != null
                            ) {
                                UnifiedReaderScreen(
                                    launchRequest = scene.request,
                                    database = database,
                                    catalogGateway = catalogGateway,
                                    novelTocSyncRepository = novelTocSyncRepository,
                                    preferences = effectivePreferences,
                                    storageAndCacheRepository = storageAndCacheRepository,
                                    onReaderTypographyChanged = onReaderTypographyChanged,
                                    onReaderCustomFontPathChanged = onReaderCustomFontPathChanged,
                                    onReaderCustomBackgroundPathChanged = onReaderCustomBackgroundPathChanged,
                                    onReaderPageTurnModeChanged = onReaderPageTurnModeChanged,
                                    onReaderPaperPresetChanged = onReaderPaperPresetChanged,
                                    onThemeModeSelected = onThemeModeSelected,
                                    onReaderKeepScreenOnChanged = onReaderKeepScreenOnChanged,
                                    onReaderEdgeBrightnessDragChanged = onReaderEdgeBrightnessDragChanged,
                                    onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
                                    onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
                                    onNavigateBack = {
                                        sceneSaveableStateHolder.removeState(scene.saveableKey())
                                        activeReaderRequest = null
                                    },
                                )
                            }
                        }

                        ScaffoldSceneState.NetworkDiagnostics -> {
                            if (networkDiagnosticsViewModelFactory != null) {
                                val diagnosticsViewModel: NetworkDiagnosticsViewModel = viewModel(
                                    factory = networkDiagnosticsViewModelFactory,
                                )
                                NetworkDiagnosticsScreen(
                                    viewModel = diagnosticsViewModel,
                                    autoOptimizeEnabled = effectivePreferences.autoOptimizeConnectionEnabled,
                                    onAutoOptimizeChanged = onAutoOptimizeConnectionChanged,
                                    onNavigateBack = {
                                        sceneSaveableStateHolder.removeState(scene.saveableKey())
                                        isNetworkDiagnosticsOpen = false
                                    },
                                )
                            }
                        }
                    }
                    }
                }
            }
        }

        if (isLoginBottomSheetOpen && userSessionRepository != null) {
            AccountLoginBottomSheet(
                registerPageUrl = userSessionRepository.registerPageUrl,
                onSubmitLogin = { identifier, password ->
                    userSessionRepository.loginWithCredentials(identifier, password)
                },
                onDismiss = { isLoginBottomSheetOpen = false },
                onLoginSuccess = { isLoginBottomSheetOpen = false },
            )
        }

        if (canSurfaceAppUpdatePrompt &&
            pendingAutoUpdatePrompt is AppUpdateAutoPrompt.DirectDownloadDialog &&
            appUpdateCoordinator != null
        ) {
            DirectUpdateAvailableDialog(
                currentVersionCode = pendingAutoUpdatePrompt.currentVersionCode,
                latestVersionCode = pendingAutoUpdatePrompt.targetVersionCode,
                downloadPageUrl = pendingAutoUpdatePrompt.downloadPageUrl,
                onDismiss = {
                    scaffoldCoroutineScope.launch {
                        appUpdateCoordinator.dismissAutoPrompt(suppressForThisVersion = true)
                    }
                },
                onOpenDownloadUrl = { url ->
                    scaffoldCoroutineScope.launch {
                        appUpdateCoordinator.dismissAutoPrompt(suppressForThisVersion = true)
                    }
                    if (onOpenDirectUpdateUrl != null) {
                        onOpenDirectUpdateUrl(url)
                    } else {
                        openSafeBrowserUrl(scaffoldContext, url)
                    }
                },
            )
        }

        SnackbarHost(
            hostState = updateSnackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = if (activeScene == ScaffoldSceneState.HomeShell && isCompactWidth) 88.dp else 16.dp,
                )
                .testTag(MainScaffoldTestTags.UPDATE_READY_SNACKBAR),
        ) { snackbarData ->
            Snackbar(
                snackbarData = snackbarData,
                modifier = Modifier.einkHairlineBorder(MaterialTheme.shapes.small),
            )
        }
    }
}

@Composable
private fun MainDestinationBody(
    selectedDestination: TopLevelDestination,
    reselectScrollToTopTrigger: Int,
    showOfflineFallbackBanner: Boolean,
    onDismissOfflineBanner: () -> Unit,
    preferences: Wenku8UserPreferences,
    onThemeModeSelected: (Wenku8ThemeMode) -> Unit,
    onDefaultHomeSelected: (DefaultHomeDestination) -> Unit,
    onAutoOptimizeConnectionChanged: (Boolean) -> Unit,
    onAppLanguageSelected: (Wenku8API.AppLanguage) -> Unit,
    onEinkModeChanged: (Boolean) -> Unit,
    onReaderTypographyChanged: (
        fontSizeSp: Int,
        lineDistanceDp: Int,
        paragraphDistanceDp: Int,
        pageEdgeDistanceDp: Int,
    ) -> Unit,
    onReaderCustomFontPathChanged: (String) -> Unit,
    onReaderCustomBackgroundPathChanged: (String) -> Unit,
    onVolumeKeyPagingChanged: (Boolean) -> Unit,
    database: Wenku8Database?,
    relayRouteController: RelayRouteController?,
    discoveryRepository: DiscoveryRepository?,
    userSessionRepository: UserSessionRepository?,
    serverNoticeAndUpdateRepository: ServerNoticeAndUpdateRepository?,
    appUpdateCoordinator: AppUpdateCoordinator?,
    storageAndCacheRepository: StorageAndCacheRepository?,
    backupArchiveRepository: BackupArchiveRepository?,
    bookshelfRepository: BookshelfRepository?,
    bookshelfViewMode: BookshelfViewMode,
    onBookshelfViewModeSelected: (BookshelfViewMode) -> Unit,
    bookshelfSortMode: BookshelfSortMode,
    onBookshelfSortModeSelected: (BookshelfSortMode) -> Unit,
    bookshelfLocalQuery: String,
    onBookshelfLocalQueryChanged: (String) -> Unit,
    bookshelfActiveFilterKey: String,
    onBookshelfActiveFilterKeyChanged: (String) -> Unit,
    legacyV1StorageMigrator: LegacyV1StorageMigrator?,
    appLanguage: Wenku8API.AppLanguage,
    onOpenSearch: () -> Unit,
    onOpenSearchWithOrigin: (String) -> Unit,
    onOpenSearchWithSeed: (String, UnifiedSearchMode) -> Unit,
    onOpenLoginSheet: () -> Unit,
    onOpenReadingFootprint: (ReadingFootprintTab) -> Unit,
    onNovelClicked: (DiscoveryNovelCardItem) -> Unit,
    onNovelClickedWithOrigin: (DiscoveryNovelCardItem, String) -> Unit,
    onContinueReadingClicked: (BookshelfItemModel) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    contentPadding: PaddingValues,
) {
    if (bookshelfRepository != null && selectedDestination == TopLevelDestination.BOOKSHELF) {
        BookshelfScreen(
            bookshelfRepository = bookshelfRepository,
            appLanguage = appLanguage,
            viewMode = bookshelfViewMode,
            onViewModeSelected = onBookshelfViewModeSelected,
            sortMode = bookshelfSortMode,
            onSortModeSelected = onBookshelfSortModeSelected,
            showOfflineFallbackBanner = showOfflineFallbackBanner,
            onDismissOfflineBanner = onDismissOfflineBanner,
            onOpenSearch = {
                onOpenSearchWithOrigin(Wenku8SharedElementKeys.SEARCH_BAR_BOOKSHELF_ORIGIN)
            },
            onOpenOnlineSearch = { rawQuery ->
                val trimmed = rawQuery.trim()
                if (trimmed.isNotEmpty()) {
                    onOpenSearchWithSeed(trimmed, UnifiedSearchMode.NOVEL_TITLE)
                } else {
                    onOpenSearchWithOrigin(Wenku8SharedElementKeys.SEARCH_BAR_BOOKSHELF_ORIGIN)
                }
            },
            onOpenLoginSheet = onOpenLoginSheet,
            onNovelClicked = onNovelClicked,
            onNovelClickedWithOrigin = onNovelClickedWithOrigin,
            onContinueReadingClicked = onContinueReadingClicked,
            localQueryOverride = bookshelfLocalQuery,
            onLocalQueryChanged = onBookshelfLocalQueryChanged,
            activeFilterKeyOverride = bookshelfActiveFilterKey,
            onActiveFilterKeyChanged = onBookshelfActiveFilterKeyChanged,
            reselectScrollToTopTrigger = reselectScrollToTopTrigger,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag(MainScaffoldTestTags.DESTINATION_SCREEN_PREFIX + selectedDestination.routeKey),
        )
        return
    }

    if (discoveryRepository != null && selectedDestination == TopLevelDestination.LATEST) {
        LatestScreen(
            discoveryRepository = discoveryRepository,
            appLanguage = appLanguage,
            onOpenSearch = {
                onOpenSearchWithOrigin(Wenku8SharedElementKeys.SEARCH_BAR_DEFAULT_ORIGIN)
            },
            onNovelClicked = { item ->
                onNovelClickedWithOrigin(item, Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN)
            },
            onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
            reselectScrollToTopTrigger = reselectScrollToTopTrigger,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag(MainScaffoldTestTags.DESTINATION_SCREEN_PREFIX + selectedDestination.routeKey),
        )
        return
    }

    if (discoveryRepository != null && selectedDestination == TopLevelDestination.RANKINGS) {
        RankingsScreen(
            discoveryRepository = discoveryRepository,
            appLanguage = appLanguage,
            onOpenSearch = {
                onOpenSearchWithOrigin(Wenku8SharedElementKeys.SEARCH_BAR_DEFAULT_ORIGIN)
            },
            onNovelClicked = { item ->
                onNovelClickedWithOrigin(item, Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN)
            },
            onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
            reselectScrollToTopTrigger = reselectScrollToTopTrigger,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag(MainScaffoldTestTags.DESTINATION_SCREEN_PREFIX + selectedDestination.routeKey),
        )
        return
    }

    if (selectedDestination == TopLevelDestination.PROFILE) {
        val totalReadingSeconds = if (database != null) {
            val s by database.readingSessionDao()
                .observeTotalReadingSeconds()
                .collectAsStateWithLifecycle(initialValue = 0L)
            s
        } else {
            0L
        }
        val totalCharactersRead = if (database != null) {
            val c by database.readingSessionDao()
                .observeTotalCharactersRead()
                .collectAsStateWithLifecycle(initialValue = 0L)
            c
        } else {
            0L
        }
        val totalBookmarkNotes = if (database != null) {
            val n by database.bookmarkNoteDao()
                .observeTotalBookmarkNoteCount()
                .collectAsStateWithLifecycle(initialValue = 0)
            n
        } else {
            0
        }
        val autoRoutingState = if (relayRouteController != null) {
            val r by relayRouteController.uiState.collectAsStateWithLifecycle()
            r
        } else if (preferences.autoOptimizeConnectionEnabled) {
            AutoRoutingModeState.EnabledUsingDefault
        } else {
            AutoRoutingModeState.DisabledByUser
        }
        val recordedCandidate = if (relayRouteController != null) {
            val rec by relayRouteController.recordedCandidateSummary.collectAsStateWithLifecycle()
            rec
        } else {
            null
        }

        ProfileAndStudioScreen(
            preferences = preferences,
            onThemeModeSelected = onThemeModeSelected,
            onDefaultHomeSelected = onDefaultHomeSelected,
            showOfflineFallbackBanner = showOfflineFallbackBanner,
            onDismissOfflineBanner = onDismissOfflineBanner,
            totalReadingSeconds = totalReadingSeconds,
            totalCharactersRead = totalCharactersRead,
            totalBookmarkNoteCount = totalBookmarkNotes,
            onOpenReadingFootprint = onOpenReadingFootprint,
            autoRoutingState = autoRoutingState,
            recordedCandidate = recordedCandidate,
            onAutoOptimizeConnectionChanged = { enabled ->
                relayRouteController?.onUserToggledAutoOptimize(enabled)
                onAutoOptimizeConnectionChanged(enabled)
            },
            onResetDefaultConnection = {
                relayRouteController?.resetToDefaultAndReevaluate(triggerTournamentNow = false)
            },
            onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
            onAppLanguageSelected = onAppLanguageSelected,
            onEinkModeChanged = onEinkModeChanged,
            onReaderTypographyChanged = onReaderTypographyChanged,
            onReaderCustomFontPathChanged = onReaderCustomFontPathChanged,
            onReaderCustomBackgroundPathChanged = onReaderCustomBackgroundPathChanged,
            onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
            userSessionRepository = userSessionRepository,
            serverNoticeAndUpdateRepository = serverNoticeAndUpdateRepository,
            appUpdateCoordinator = appUpdateCoordinator,
            storageAndCacheRepository = storageAndCacheRepository,
            backupArchiveRepository = backupArchiveRepository,
            legacyV1StorageMigrator = legacyV1StorageMigrator,
            onOpenLoginSheet = onOpenLoginSheet,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .testTag(MainScaffoldTestTags.DESTINATION_SCREEN_PREFIX + selectedDestination.routeKey),
        )
        return
    }

    val headlineText = when (selectedDestination) {
        TopLevelDestination.BOOKSHELF -> stringResource(R.string.bookshelf_headline)
        TopLevelDestination.LATEST -> stringResource(R.string.latest_headline)
        TopLevelDestination.RANKINGS -> stringResource(R.string.rankings_headline)
        TopLevelDestination.PROFILE -> stringResource(R.string.nav_profile)
    }
    val subtitleText = when (selectedDestination) {
        TopLevelDestination.BOOKSHELF -> stringResource(R.string.bookshelf_subtitle)
        TopLevelDestination.LATEST -> stringResource(R.string.latest_subtitle)
        TopLevelDestination.RANKINGS -> stringResource(R.string.rankings_subtitle)
        TopLevelDestination.PROFILE -> ""
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .testTag(MainScaffoldTestTags.DESTINATION_SCREEN_PREFIX + selectedDestination.routeKey),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showOfflineFallbackBanner) {
            item(key = "offline_fallback_banner") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MainScaffoldTestTags.OFFLINE_FALLBACK_BANNER)
                        .einkHairlineBorder(MaterialTheme.shapes.medium),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CloudOff,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.offline_fallback_bookshelf_banner),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        val dismissLabel = stringResource(R.string.action_dismiss_banner)
                        TooltipIconButton(
                            tooltipText = dismissLabel,
                            onClick = onDismissOfflineBanner,
                            modifier = Modifier.testTag(
                                MainScaffoldTestTags.DISMISS_OFFLINE_BANNER_BUTTON,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = dismissLabel,
                            )
                        }
                    }
                }
            }
        }

        item(key = "persistent_search_pill") {
            PersistentSearchEntryPill(onOpenSearch = onOpenSearch)
        }

        item(key = "destination_editorial_header") {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                ),
                shape = MaterialTheme.shapes.large,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkHairlineBorder(MaterialTheme.shapes.large),
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = headlineText,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    AssistChip(
                        onClick = onOpenNetworkDiagnostics,
                        label = { Text(text = stringResource(R.string.action_network_diagnostics)) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.NetworkCheck,
                                contentDescription = null,
                            )
                        },
                    )
                }
            }
        }
    }
}

internal const val DOUBLE_BACK_EXIT_WINDOW_MS = 2_000L

private tailrec fun Context.findScaffoldHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findScaffoldHostActivity()
    else -> null
}
