package org.mewx.wenku8.v2.ui.navigation

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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.WideNavigationRail
import androidx.compose.material3.WideNavigationRailItem
import androidx.compose.material3.WideNavigationRailValue
import androidx.compose.material3.rememberWideNavigationRailState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.R
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.v2.data.migration.LegacyV1StorageMigrator
import org.mewx.wenku8.v2.data.remote.diagnostics.AutoRoutingModeState
import org.mewx.wenku8.v2.data.remote.diagnostics.RelayRouteController
import org.mewx.wenku8.v2.data.repository.BackupArchiveRepository
import org.mewx.wenku8.v2.data.repository.BookshelfRepository
import org.mewx.wenku8.v2.data.repository.BookshelfSortMode
import org.mewx.wenku8.v2.data.repository.BookshelfViewMode
import org.mewx.wenku8.v2.data.repository.DiscoveryRepository
import org.mewx.wenku8.v2.data.repository.ReviewThreadCardItem
import org.mewx.wenku8.v2.data.repository.ReviewsRepository
import org.mewx.wenku8.v2.data.repository.ServerNoticeAndUpdateRepository
import org.mewx.wenku8.v2.data.repository.StorageAndCacheRepository
import org.mewx.wenku8.v2.data.repository.UserSessionRepository
import org.mewx.wenku8.v2.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.v2.ui.bookshelf.BookshelfScreen
import org.mewx.wenku8.v2.ui.diagnostics.NetworkDiagnosticsScreen
import org.mewx.wenku8.v2.ui.diagnostics.NetworkDiagnosticsViewModel
import org.mewx.wenku8.v2.ui.discovery.LatestScreen
import org.mewx.wenku8.v2.ui.discovery.PersistentSearchEntryPill
import org.mewx.wenku8.v2.ui.discovery.RankingsScreen
import org.mewx.wenku8.v2.ui.profile.AccountLoginBottomSheet
import org.mewx.wenku8.v2.ui.profile.ProfileAndStudioScreen
import org.mewx.wenku8.v2.ui.reviews.ReviewListScreen
import org.mewx.wenku8.v2.ui.reviews.ReviewThreadScreen
import org.mewx.wenku8.v2.ui.search.UnifiedSearchScreen
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.mewx.wenku8.v2.ui.theme.einkHairlineBorder

/** Semantic UI test tags used by Robolectric & instrumented Compose tests. */
object MainScaffoldTestTags {
    const val COMPACT_SHORT_NAV_BAR = "main_compact_short_nav_bar"
    const val EXPANDED_WIDE_NAV_RAIL = "main_expanded_wide_nav_rail"
    const val OFFLINE_FALLBACK_BANNER = "main_offline_fallback_banner"
    const val DISMISS_OFFLINE_BANNER_BUTTON = "main_dismiss_offline_banner"
    const val DESTINATION_SCREEN_PREFIX = "destination_screen_"
    const val NAV_ITEM_PREFIX = "nav_item_"
}

private val MediumWindowWidthBreakpoint = 600.dp
private val ExpandedWindowWidthBreakpoint = 840.dp

/**
 * Adaptive Material 3 Expressive (`M3e`) 4-destination shell for Wenku8 `:app-v2`.
 *
 * Layout adaptation (`02-information-architecture-and-navigation.md` §3):
 * - **Compact width (`< 600.dp`, portrait phones)**: Renders M3e [ShortNavigationBar] at the bottom.
 * - **Medium / Expanded width (`>= 600.dp`, foldables, tablets, landscape)**: Switches seamlessly
 *   to M3e [WideNavigationRail] (collapsed on `600..839.dp`, expanded with labels on `>= 840.dp`)
 *   while preserving active destination state.
 */
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
    relayRouteController: RelayRouteController? = null,
    discoveryRepository: DiscoveryRepository? = null,
    userSessionRepository: UserSessionRepository? = null,
    serverNoticeAndUpdateRepository: ServerNoticeAndUpdateRepository? = null,
    storageAndCacheRepository: StorageAndCacheRepository? = null,
    backupArchiveRepository: BackupArchiveRepository? = null,
    reviewsRepository: ReviewsRepository? = null,
    bookshelfRepository: BookshelfRepository? = null,
    legacyV1StorageMigrator: LegacyV1StorageMigrator? = null,
    appLanguage: Wenku8API.AppLanguage = Wenku8API.AppLanguage.SC,
    networkDiagnosticsViewModelFactory: NetworkDiagnosticsViewModel.Factory? = null,
    autoOptimizeConnectionEnabled: Boolean = true,
    onAutoOptimizeConnectionChanged: (Boolean) -> Unit = {},
    onBookshelfViewModeSelected: (BookshelfViewMode) -> Unit = {},
    onBookshelfSortModeSelected: (BookshelfSortMode) -> Unit = {},
    onAppLanguageSelected: (Wenku8API.AppLanguage) -> Unit = {},
    onEinkModeChanged: (Boolean) -> Unit = {},
    onUseDynamicColorChanged: (Boolean) -> Unit = {},
    onMenuBackgroundChanged: (presetId: Int, customPath: String) -> Unit = { _, _ -> },
    onReaderTypographyChanged: (
        fontSizeSp: Int,
        lineDistanceDp: Int,
        paragraphDistanceDp: Int,
        pageEdgeDistanceDp: Int,
    ) -> Unit = { _, _, _, _ -> },
    onReaderCustomFontPathChanged: (String) -> Unit = {},
    onReaderCustomBackgroundPathChanged: (String) -> Unit = {},
    onVolumeKeyPagingChanged: (Boolean) -> Unit = {},
    initialOpenReviewsAid: Int = 0,
    initialOpenReviewsTitle: String = "",
    onOpenSearch: () -> Unit = {},
    onNovelClicked: (DiscoveryNovelCardItem) -> Unit = {},
    onOpenNetworkDiagnostics: () -> Unit = {},
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
    var localBookshelfViewMode by rememberSaveable(effectivePreferences.bookshelfViewMode) {
        mutableStateOf(effectivePreferences.bookshelfViewMode)
    }
    var localBookshelfSortMode by rememberSaveable(effectivePreferences.bookshelfSortMode) {
        mutableStateOf(effectivePreferences.bookshelfSortMode)
    }
    var isSearchStudioOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var isNetworkDiagnosticsOpen by rememberSaveable {
        mutableStateOf(false)
    }
    var isLoginBottomSheetOpen by rememberSaveable {
        mutableStateOf(false)
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

    val handleOpenSearch = {
        onOpenSearch()
        if (discoveryRepository != null) {
            isSearchStudioOpen = true
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
                                onClick = { selectedDestination = destination },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) {
                                            destination.selectedIcon
                                        } else {
                                            destination.unselectedIcon
                                        },
                                        contentDescription = labelText,
                                    )
                                },
                                label = { Text(text = labelText) },
                                modifier = Modifier.testTag(
                                    MainScaffoldTestTags.NAV_ITEM_PREFIX + destination.routeKey,
                                ),
                            )
                        }
                    }
                },
            ) { innerPadding ->
                MainDestinationBody(
                    selectedDestination = selectedDestination,
                    showOfflineFallbackBanner = showOfflineFallbackBanner,
                    onDismissOfflineBanner = { showOfflineFallbackBanner = false },
                    preferences = effectivePreferences,
                    onThemeModeSelected = onThemeModeSelected,
                    onDefaultHomeSelected = onDefaultHomeSelected,
                    onAutoOptimizeConnectionChanged = onAutoOptimizeConnectionChanged,
                    onAppLanguageSelected = onAppLanguageSelected,
                    onEinkModeChanged = onEinkModeChanged,
                    onUseDynamicColorChanged = onUseDynamicColorChanged,
                    onMenuBackgroundChanged = onMenuBackgroundChanged,
                    onReaderTypographyChanged = onReaderTypographyChanged,
                    onReaderCustomFontPathChanged = onReaderCustomFontPathChanged,
                    onReaderCustomBackgroundPathChanged = onReaderCustomBackgroundPathChanged,
                    onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
                    database = database,
                    relayRouteController = relayRouteController,
                    discoveryRepository = discoveryRepository,
                    userSessionRepository = userSessionRepository,
                    serverNoticeAndUpdateRepository = serverNoticeAndUpdateRepository,
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
                    legacyV1StorageMigrator = legacyV1StorageMigrator,
                    appLanguage = appLanguage,
                    onOpenSearch = handleOpenSearch,
                    onOpenLoginSheet = { isLoginBottomSheetOpen = true },
                    onNovelClicked = onNovelClicked,
                    onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
                    contentPadding = innerPadding,
                )
            }
        } else {
            val railState = rememberWideNavigationRailState(
                initialValue = if (isExpandedRailWidth) {
                    WideNavigationRailValue.Expanded
                } else {
                    WideNavigationRailValue.Collapsed
                },
            )
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
                            railExpanded = isExpandedRailWidth,
                            selected = isSelected,
                            onClick = { selectedDestination = destination },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = labelText,
                                )
                            },
                            label = { Text(text = labelText) },
                            modifier = Modifier.testTag(
                                MainScaffoldTestTags.NAV_ITEM_PREFIX + destination.routeKey,
                            ),
                        )
                    }
                }

                Scaffold(
                    modifier = Modifier.weight(1f),
                    contentWindowInsets = WindowInsets.safeDrawing,
                ) { innerPadding ->
                    MainDestinationBody(
                        selectedDestination = selectedDestination,
                        showOfflineFallbackBanner = showOfflineFallbackBanner,
                        onDismissOfflineBanner = { showOfflineFallbackBanner = false },
                        preferences = effectivePreferences,
                        onThemeModeSelected = onThemeModeSelected,
                        onDefaultHomeSelected = onDefaultHomeSelected,
                        onAutoOptimizeConnectionChanged = onAutoOptimizeConnectionChanged,
                        onAppLanguageSelected = onAppLanguageSelected,
                        onEinkModeChanged = onEinkModeChanged,
                        onUseDynamicColorChanged = onUseDynamicColorChanged,
                        onMenuBackgroundChanged = onMenuBackgroundChanged,
                        onReaderTypographyChanged = onReaderTypographyChanged,
                        onReaderCustomFontPathChanged = onReaderCustomFontPathChanged,
                        onReaderCustomBackgroundPathChanged = onReaderCustomBackgroundPathChanged,
                        onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
                        database = database,
                        relayRouteController = relayRouteController,
                        discoveryRepository = discoveryRepository,
                        userSessionRepository = userSessionRepository,
                        serverNoticeAndUpdateRepository = serverNoticeAndUpdateRepository,
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
                        legacyV1StorageMigrator = legacyV1StorageMigrator,
                        appLanguage = appLanguage,
                        onOpenSearch = handleOpenSearch,
                        onOpenLoginSheet = { isLoginBottomSheetOpen = true },
                        onNovelClicked = onNovelClicked,
                        onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
                        contentPadding = innerPadding,
                    )
                }
            }
        }

        if (isSearchStudioOpen && discoveryRepository != null) {
            UnifiedSearchScreen(
                discoveryRepository = discoveryRepository,
                appLanguage = appLanguage,
                onNavigateBack = { isSearchStudioOpen = false },
                onNovelClicked = onNovelClicked,
                onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
            )
        }

        if (activeReviewsAid > 0 && reviewsRepository != null) {
            val selectedThread = activeReviewThread
            if (selectedThread == null) {
                ReviewListScreen(
                    aid = activeReviewsAid,
                    novelTitle = activeReviewsNovelTitle,
                    reviewsRepository = reviewsRepository,
                    appLanguage = appLanguage,
                    userSessionRepository = userSessionRepository,
                    onNavigateBack = {
                        activeReviewsAid = 0
                        activeReviewsNovelTitle = ""
                        activeReviewThread = null
                    },
                    onOpenThread = { thread -> activeReviewThread = thread },
                    onOpenLoginSheet = { isLoginBottomSheetOpen = true },
                    onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
                )
            } else {
                ReviewThreadScreen(
                    aid = activeReviewsAid,
                    novelTitle = activeReviewsNovelTitle,
                    thread = selectedThread,
                    reviewsRepository = reviewsRepository,
                    appLanguage = appLanguage,
                    userSessionRepository = userSessionRepository,
                    onNavigateBack = { activeReviewThread = null },
                    onOpenLoginSheet = { isLoginBottomSheetOpen = true },
                    onOpenNetworkDiagnostics = handleOpenNetworkDiagnostics,
                )
            }
        }

        if (isNetworkDiagnosticsOpen && networkDiagnosticsViewModelFactory != null) {
            val diagnosticsViewModel: NetworkDiagnosticsViewModel = viewModel(
                factory = networkDiagnosticsViewModelFactory,
            )
            NetworkDiagnosticsScreen(
                viewModel = diagnosticsViewModel,
                autoOptimizeEnabled = effectivePreferences.autoOptimizeConnectionEnabled,
                onAutoOptimizeChanged = onAutoOptimizeConnectionChanged,
                onNavigateBack = { isNetworkDiagnosticsOpen = false },
            )
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
    }
}

@Composable
private fun MainDestinationBody(
    selectedDestination: TopLevelDestination,
    showOfflineFallbackBanner: Boolean,
    onDismissOfflineBanner: () -> Unit,
    preferences: Wenku8UserPreferences,
    onThemeModeSelected: (Wenku8ThemeMode) -> Unit,
    onDefaultHomeSelected: (DefaultHomeDestination) -> Unit,
    onAutoOptimizeConnectionChanged: (Boolean) -> Unit,
    onAppLanguageSelected: (Wenku8API.AppLanguage) -> Unit,
    onEinkModeChanged: (Boolean) -> Unit,
    onUseDynamicColorChanged: (Boolean) -> Unit,
    onMenuBackgroundChanged: (presetId: Int, customPath: String) -> Unit,
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
    storageAndCacheRepository: StorageAndCacheRepository?,
    backupArchiveRepository: BackupArchiveRepository?,
    bookshelfRepository: BookshelfRepository?,
    bookshelfViewMode: BookshelfViewMode,
    onBookshelfViewModeSelected: (BookshelfViewMode) -> Unit,
    bookshelfSortMode: BookshelfSortMode,
    onBookshelfSortModeSelected: (BookshelfSortMode) -> Unit,
    legacyV1StorageMigrator: LegacyV1StorageMigrator?,
    appLanguage: Wenku8API.AppLanguage,
    onOpenSearch: () -> Unit,
    onOpenLoginSheet: () -> Unit,
    onNovelClicked: (DiscoveryNovelCardItem) -> Unit,
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
            onOpenSearch = onOpenSearch,
            onOpenLoginSheet = onOpenLoginSheet,
            onNovelClicked = onNovelClicked,
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
            onOpenSearch = onOpenSearch,
            onNovelClicked = onNovelClicked,
            onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
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
            onOpenSearch = onOpenSearch,
            onNovelClicked = onNovelClicked,
            onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
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

        ProfileAndStudioScreen(
            preferences = preferences,
            onThemeModeSelected = onThemeModeSelected,
            onDefaultHomeSelected = onDefaultHomeSelected,
            showOfflineFallbackBanner = showOfflineFallbackBanner,
            onDismissOfflineBanner = onDismissOfflineBanner,
            totalReadingSeconds = totalReadingSeconds,
            totalCharactersRead = totalCharactersRead,
            totalBookmarkNoteCount = totalBookmarkNotes,
            autoRoutingState = autoRoutingState,
            onAutoOptimizeConnectionChanged = onAutoOptimizeConnectionChanged,
            onResetDefaultConnection = {
                relayRouteController?.resetToDefaultAndReevaluate(triggerTournamentNow = true)
            },
            onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
            onAppLanguageSelected = onAppLanguageSelected,
            onEinkModeChanged = onEinkModeChanged,
            onUseDynamicColorChanged = onUseDynamicColorChanged,
            onMenuBackgroundChanged = onMenuBackgroundChanged,
            onReaderTypographyChanged = onReaderTypographyChanged,
            onReaderCustomFontPathChanged = onReaderCustomFontPathChanged,
            onReaderCustomBackgroundPathChanged = onReaderCustomBackgroundPathChanged,
            onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
            userSessionRepository = userSessionRepository,
            serverNoticeAndUpdateRepository = serverNoticeAndUpdateRepository,
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
        TopLevelDestination.PROFILE -> stringResource(R.string.profile_headline)
    }
    val subtitleText = when (selectedDestination) {
        TopLevelDestination.BOOKSHELF -> stringResource(R.string.bookshelf_subtitle)
        TopLevelDestination.LATEST -> stringResource(R.string.latest_subtitle)
        TopLevelDestination.RANKINGS -> stringResource(R.string.rankings_subtitle)
        TopLevelDestination.PROFILE -> stringResource(R.string.profile_subtitle)
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
                        IconButton(
                            onClick = onDismissOfflineBanner,
                            modifier = Modifier.testTag(
                                MainScaffoldTestTags.DISMISS_OFFLINE_BANNER_BUTTON,
                            ),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = null,
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
