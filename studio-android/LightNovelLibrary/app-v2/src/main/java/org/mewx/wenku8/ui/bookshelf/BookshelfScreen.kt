package org.mewx.wenku8.ui.bookshelf

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.VerticalAlignTop
import androidx.compose.material.icons.outlined.ViewWeek
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.R
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.data.repository.BookshelfFilter
import org.mewx.wenku8.data.repository.BookshelfItemModel
import org.mewx.wenku8.data.repository.BookshelfRepository
import org.mewx.wenku8.data.repository.BookshelfSnapshot
import org.mewx.wenku8.data.repository.BookshelfSortMode
import org.mewx.wenku8.data.repository.BookshelfViewMode
import org.mewx.wenku8.data.repository.CloudBookshelfSyncOutcome
import org.mewx.wenku8.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.ui.components.ActionTooltipBox
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.discovery.BookStatusBadge
import org.mewx.wenku8.ui.discovery.DiscoveryTestTags
import org.mewx.wenku8.ui.discovery.formatBookStatusLabel
import org.mewx.wenku8.ui.navigation.MainScaffoldTestTags
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import org.mewx.wenku8.ui.theme.wenku8SharedNovelCover
import org.mewx.wenku8.ui.theme.wenku8SharedNovelTitle
import org.mewx.wenku8.ui.theme.wenku8SharedSearchBarBounds
import kotlin.math.max

/** Semantic UI test tags for Robolectric and device tests on `BookshelfScreen` (`P4.1`). */
object BookshelfTestTags {
    const val SCREEN = "bookshelf_screen"
    const val PULL_TO_REFRESH_BOX = "bookshelf_pull_to_refresh_box"
    const val LOCAL_SEARCH_INPUT = "bookshelf_local_search_input"
    const val CLEAR_LOCAL_SEARCH_BUTTON = "bookshelf_clear_local_search"
    const val ONLINE_SEARCH_TRIGGER = "bookshelf_online_search_trigger"
    const val CLOUD_SYNC_BUTTON = "bookshelf_cloud_sync_button"
    const val HEADER_ACTIVE_SYNC_PROGRESS = "bookshelf_header_active_sync_progress"
    const val SMART_UPDATE_TRAILING_BUTTON = "bookshelf_smart_update_trailing"
    const val SMART_UPDATE_MENU_ITEM = "bookshelf_smart_update_menu_item"
    const val FORCE_CLOUD_SYNC_MENU_ITEM = "bookshelf_force_cloud_sync_menu_item"
    const val CONFIRM_FORCE_SYNC_BUTTON = "bookshelf_confirm_force_sync_button"
    const val VIEW_MODE_CHIP_PREFIX = "bookshelf_view_mode_"
    const val SORT_MODE_BUTTON = "bookshelf_sort_mode_button"
    const val SORT_MODE_ITEM_PREFIX = "bookshelf_sort_mode_"
    const val CONTINUE_READING_CAROUSEL = "bookshelf_continue_reading_carousel"
    const val CONTINUE_READING_ITEM_PREFIX = "bookshelf_continue_item_"
    const val CONTINUE_READING_COVER_PREFIX = "bookshelf_continue_cover_"
    const val CONTINUE_READING_BUTTON_PREFIX = "bookshelf_continue_btn_"
    const val FILTER_CHIP_ALL = "bookshelf_filter_all"
    const val FILTER_CHIP_NEW_CHAPTERS = "bookshelf_filter_new_chapters"
    const val FILTER_CHIP_CACHED = "bookshelf_filter_cached"
    const val FILTER_CHIP_SERIALIZING = "bookshelf_filter_serializing"
    const val FILTER_CHIP_COMPLETED = "bookshelf_filter_completed"
    const val FILTER_CHIP_CATEGORY_PREFIX = "bookshelf_filter_cat_"
    const val FILTER_CHIP_IMPRINT_PREFIX = "bookshelf_filter_imprint_"
    const val FILTER_CHIP_TAG_PREFIX = "bookshelf_filter_tag_"
    const val BOOK_CARD_PREFIX = "bookshelf_card_"
    const val NEW_CHAPTER_BADGE_PREFIX = "bookshelf_new_badge_"
    const val ACTIVE_WAVY_PROGRESS_PREFIX = "bookshelf_wavy_progress_"
    const val FLAT_READING_PROGRESS_PREFIX = "bookshelf_flat_progress_"
    const val READING_PROGRESS_PERCENT_PREFIX = "bookshelf_progress_pct_"
    const val MULTI_SELECT_TOOLBAR = "bookshelf_multi_select_toolbar"
    const val BATCH_SELECT_ALL = "bookshelf_batch_select_all"
    const val BATCH_SMART_UPDATE = "bookshelf_batch_smart_update"
    const val BATCH_PIN_TOP = "bookshelf_batch_pin_top"
    const val BATCH_CLEAR_CACHE = "bookshelf_batch_clear_cache"
    const val BATCH_REMOVE = "bookshelf_batch_remove"
    const val BATCH_DONE = "bookshelf_batch_done"
    const val CONFIRM_REMOVE_BUTTON = "bookshelf_confirm_remove_button"
    const val EMPTY_SHELF_STATE = "bookshelf_empty_shelf_state"
    const val EMPTY_FILTER_STATE = "bookshelf_empty_filter_state"
}

/**
 * Tab 1 · `BookshelfScreen` (`本地书架 2.0`, `docs/designs/v2/05` Screen 2).
 *
 * Implements:
 * - Instant `< 1ms` local search across title, author, imprint, tags, and `aid`, paired with a
 *   1-tap `[ 搜全站 ]` pill expanding into `UnifiedSearchScreen`.
 * - Folded M3e `SplitButtonLayout` (`云端同步 | ▾`) housing `智能更新` and `强制刷新全部云端书目`,
 *   paired with `PullToRefreshBox`, cold-start first-entry auto-sync (`v1` `FavFragment` parity),
 *   and global mutual exclusion while any shelf sync/update task is active.
 * - `"继续阅读"` M3e `HorizontalMultiBrowseCarousel` with flat `LinearProgressIndicator` (`68%`).
 * - Local filter rail (`全部`, `有新章节`, `已缓存`, `连载中`, `已完结`, custom categories, imprints, `#标签`)
 *   and 4 sort modes (`按最近阅读`, `按更新时间`, `手动置顶排序`, `书名拼音`).
 * - Three view modes (`GRID`, `DETAIL_LIST`, `SPINE_3D`), rendering `LinearWavyProgressIndicator`
 *   strictly on actively syncing/downloading items (and flat in E-Ink mode).
 * - Long-press multi-select mode with contextual M3e `HorizontalFloatingToolbar`.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun BookshelfScreen(
    bookshelfRepository: BookshelfRepository,
    appLanguage: Wenku8API.AppLanguage,
    viewMode: BookshelfViewMode,
    onViewModeSelected: (BookshelfViewMode) -> Unit,
    sortMode: BookshelfSortMode,
    onSortModeSelected: (BookshelfSortMode) -> Unit,
    showOfflineFallbackBanner: Boolean = false,
    onDismissOfflineBanner: () -> Unit = {},
    onOpenSearch: () -> Unit = {},
    onOpenOnlineSearch: (String) -> Unit = { onOpenSearch() },
    onOpenLoginSheet: () -> Unit = {},
    onNovelClicked: (DiscoveryNovelCardItem) -> Unit = {},
    onNovelClickedWithOrigin: ((DiscoveryNovelCardItem, String) -> Unit)? = null,
    onContinueReadingClicked: (BookshelfItemModel) -> Unit = { item ->
        onNovelClicked(item.toDiscoveryCardItem())
    },
    localQueryOverride: String? = null,
    onLocalQueryChanged: ((String) -> Unit)? = null,
    activeFilterKeyOverride: String? = null,
    onActiveFilterKeyChanged: ((String) -> Unit)? = null,
    reselectScrollToTopTrigger: Int = 0,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isEinkMode = LocalEinkMode.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    val snapshot by bookshelfRepository
        .observeBookshelfSnapshot()
        .collectAsStateWithLifecycle(initialValue = bookshelfRepository.latestSnapshot.value)

    var internalLocalQuery by rememberSaveable { mutableStateOf("") }
    var internalActiveFilterKey by rememberSaveable { mutableStateOf("all") }
    val localQuery = localQueryOverride ?: internalLocalQuery
    val activeFilterKey = activeFilterKeyOverride ?: internalActiveFilterKey

    fun updateLocalQuery(nextQuery: String) {
        internalLocalQuery = nextQuery
        onLocalQueryChanged?.invoke(nextQuery)
    }

    fun updateActiveFilterKey(nextKey: String) {
        internalActiveFilterKey = nextKey
        onActiveFilterKeyChanged?.invoke(nextKey)
    }

    var handledReselectTrigger by remember { mutableIntStateOf(reselectScrollToTopTrigger) }
    LaunchedEffect(reselectScrollToTopTrigger) {
        if (reselectScrollToTopTrigger != handledReselectTrigger) {
            handledReselectTrigger = reselectScrollToTopTrigger
            if (localQuery.isNotEmpty()) {
                updateLocalQuery("")
            }
            if (isEinkMode || listState.firstVisibleItemIndex > 12) {
                listState.scrollToItem(0)
            } else {
                listState.animateScrollToItem(0)
            }
        }
    }

    var selectedAidList by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    var isBatchSmartUpdating by rememberSaveable { mutableStateOf(false) }
    var isPullRefreshing by remember { mutableStateOf(false) }
    var showRemoveConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showForceSyncConfirmDialog by rememberSaveable { mutableStateOf(false) }
    var showSmartUpdateMenu by remember { mutableStateOf(false) }
    var showSortMenu by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    val isShelfTaskRunning = snapshot.isShelfTaskRunning || isBatchSmartUpdating

    LaunchedEffect(isShelfTaskRunning) {
        if (isShelfTaskRunning) {
            showSmartUpdateMenu = false
        }
    }

    val activeFilter = remember(activeFilterKey) { decodeFilterKey(activeFilterKey) }
    val selectedAidSet = remember(selectedAidList) { selectedAidList.toSet() }
    val isMultiSelectActive = selectedAidSet.isNotEmpty()

    BackHandler(enabled = isMultiSelectActive || localQuery.isNotEmpty()) {
        if (isMultiSelectActive) {
            selectedAidList = emptyList()
        } else {
            updateLocalQuery("")
        }
    }

    val displayedItems = remember(snapshot.allShelfItems, localQuery, activeFilter, sortMode) {
        BookshelfRepository.filterAndSortItems(
            items = snapshot.allShelfItems,
            localQuery = localQuery,
            activeFilter = activeFilter,
            sortMode = sortMode,
        )
    }

    val unreadUpdateCount = remember(snapshot.allShelfItems) {
        snapshot.allShelfItems.count { it.novel.hasUnreadUpdate }
    }

    fun toggleSelection(aid: Int) {
        selectedAidList = if (aid in selectedAidSet) {
            selectedAidList - aid
        } else {
            selectedAidList + aid
        }
    }

    fun launchCloudSync(
        forceRefreshAll: Boolean,
        isSilentAutoSync: Boolean = false,
        onFinished: (() -> Unit)? = null,
    ) {
        if (isShelfTaskRunning || bookshelfRepository.isShelfTaskRunning) {
            onFinished?.invoke()
            return
        }
        coroutineScope.launch {
            val outcome = try {
                bookshelfRepository.syncWithCloudBookcase(
                    appLanguage = appLanguage,
                    forceRefreshAll = forceRefreshAll,
                )
            } finally {
                onFinished?.invoke()
            }
            when (outcome) {
                CloudBookshelfSyncOutcome.AlreadyRunning -> Unit

                is CloudBookshelfSyncOutcome.Success -> {
                    val hasChangesOrWarnings = outcome.pulledFromCloudCount > 0 ||
                        outcome.pushedToCloudCount > 0 ||
                        outcome.refreshedStaleCount > 0 ||
                        outcome.deletedFromCloudCount > 0 ||
                        outcome.removedRemotelyDeletedCount > 0 ||
                        outcome.cloudShelfFullReached ||
                        outcome.failedHydrationCount > 0
                    if (!isSilentAutoSync || hasChangesOrWarnings) {
                        val message = when {
                            outcome.cloudShelfFullReached -> context.getString(
                                R.string.bookshelf_sync_full_warning_format,
                                outcome.pulledFromCloudCount,
                                outcome.pushedToCloudCount,
                            )

                            outcome.failedHydrationCount > 0 -> context.getString(
                                R.string.bookshelf_sync_partial_warning_format,
                                outcome.pulledFromCloudCount,
                                outcome.pushedToCloudCount,
                                outcome.failedHydrationCount,
                            )

                            outcome.removedRemotelyDeletedCount > 0 || outcome.deletedFromCloudCount > 0 -> context.getString(
                                R.string.bookshelf_sync_success_with_removals_format,
                                outcome.pulledFromCloudCount,
                                outcome.pushedToCloudCount,
                                outcome.deletedFromCloudCount + outcome.removedRemotelyDeletedCount,
                            )

                            else -> context.getString(
                                R.string.bookshelf_sync_success_format,
                                outcome.pulledFromCloudCount,
                                outcome.pushedToCloudCount,
                                outcome.refreshedStaleCount,
                            )
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                }

                CloudBookshelfSyncOutcome.NotLoggedIn -> {
                    if (!isSilentAutoSync) {
                        val result = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.bookshelf_sync_login_required),
                            actionLabel = context.getString(R.string.review_login_action),
                            duration = SnackbarDuration.Short,
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            onOpenLoginSheet()
                        }
                    }
                }

                CloudBookshelfSyncOutcome.ApiNotConfigured,
                is CloudBookshelfSyncOutcome.NetworkOrServerError,
                -> {
                    if (!isSilentAutoSync) {
                        snackbarHostState.showSnackbar(context.getString(R.string.bookshelf_sync_failed))
                    }
                }
            }
        }
    }

    fun launchSmartUpdate(targetAids: List<Int>) {
        if (targetAids.isEmpty() || isShelfTaskRunning || bookshelfRepository.isShelfTaskRunning) return
        coroutineScope.launch {
            isBatchSmartUpdating = true
            val summary = try {
                bookshelfRepository.smartUpdateShelfNovels(
                    aids = targetAids,
                    appLanguage = appLanguage,
                )
            } finally {
                isBatchSmartUpdating = false
            }
            if (!summary.skippedBecauseBusy) {
                snackbarHostState.showSnackbar(
                    context.getString(
                        R.string.bookshelf_smart_update_done_format,
                        summary.updatedNovelCount,
                        summary.newlyDownloadedChapterCount,
                    ),
                )
            }
        }
    }

    // `v1` `FavFragment` (`timecount == 0`) parity: automatically trigger lightweight Cloud Sync
    // once per cold start on the first entry into Bookshelf when signed in.
    LaunchedEffect(bookshelfRepository) {
        val eligible = bookshelfRepository.consumeColdStartAutoSyncEligibility()
        if (eligible && !showOfflineFallbackBanner) {
            launchCloudSync(forceRefreshAll = false, isSilentAutoSync = true)
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(BookshelfTestTags.SCREEN),
    ) {
        val gridColumnCount = max(3, (maxWidth / 128.dp).toInt())
        val spineColumnCount = max(5, (maxWidth / 64.dp).toInt())

        val gridRows = remember(displayedItems, gridColumnCount, viewMode) {
            if (viewMode == BookshelfViewMode.GRID) {
                displayedItems.chunked(gridColumnCount)
            } else {
                emptyList()
            }
        }
        val spineRows = remember(displayedItems, spineColumnCount, viewMode) {
            if (viewMode == BookshelfViewMode.SPINE_3D) {
                displayedItems.chunked(spineColumnCount)
            } else {
                emptyList()
            }
        }

        PullToRefreshBox(
            isRefreshing = isPullRefreshing,
            onRefresh = {
                if (isShelfTaskRunning || bookshelfRepository.isShelfTaskRunning) {
                    isPullRefreshing = false
                    coroutineScope.launch { pullToRefreshState.animateToHidden() }
                } else {
                    isPullRefreshing = true
                    launchCloudSync(
                        forceRefreshAll = false,
                        isSilentAutoSync = false,
                        onFinished = {
                            isPullRefreshing = false
                            coroutineScope.launch { pullToRefreshState.animateToHidden() }
                        },
                    )
                }
            },
            state = pullToRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .testTag(BookshelfTestTags.PULL_TO_REFRESH_BOX),
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = if (isMultiSelectActive) 104.dp else 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (showOfflineFallbackBanner) {
                item(key = "offline_fallback_banner") {
                    OfflineFallbackShelfBanner(onDismiss = onDismissOfflineBanner)
                }
            }

            item(key = "local_and_online_search_bar") {
                BookshelfSearchBarRow(
                    localQuery = localQuery,
                    onLocalQueryChange = ::updateLocalQuery,
                    onOpenOnlineSearch = { onOpenOnlineSearch(localQuery) },
                )
            }

            item(key = "shelf_header_controls") {
                BookshelfHeaderSection(
                    totalShelfCount = snapshot.allShelfItems.size,
                    isShelfTaskRunning = isShelfTaskRunning,
                    isEinkMode = isEinkMode,
                    viewMode = viewMode,
                    onViewModeSelected = onViewModeSelected,
                    sortMode = sortMode,
                    onSortModeSelected = onSortModeSelected,
                    showSmartUpdateMenu = showSmartUpdateMenu,
                    onShowSmartUpdateMenuChange = { showSmartUpdateMenu = it },
                    showSortMenu = showSortMenu,
                    onShowSortMenuChange = { showSortMenu = it },
                    onCloudSyncClick = { launchCloudSync(forceRefreshAll = false) },
                    onSmartUpdateAllClick = {
                        launchSmartUpdate(snapshot.allShelfItems.map { it.aid })
                    },
                    onForceRefreshCloudClick = {
                        showSmartUpdateMenu = false
                        showForceSyncConfirmDialog = true
                    },
                )
            }

            val showContinueCarousel = snapshot.continueReadingItems.isNotEmpty() &&
                localQuery.isBlank() &&
                activeFilter == BookshelfFilter.All
            if (showContinueCarousel) {
                item(key = "continue_reading_carousel") {
                    ContinueReadingCarouselSection(
                        items = snapshot.continueReadingItems,
                        onCardClick = { item ->
                            val cardItem = item.toDiscoveryCardItem()
                            val origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CAROUSEL_ORIGIN
                            onNovelClickedWithOrigin?.invoke(cardItem, origin)
                                ?: onNovelClicked(cardItem)
                        },
                        onContinueReadingClick = onContinueReadingClicked,
                    )
                }
            }

            if (snapshot.allShelfItems.isNotEmpty()) {
                item(key = "filter_chip_rail") {
                    BookshelfFilterChipRail(
                        activeFilter = activeFilter,
                        unreadUpdateCount = unreadUpdateCount,
                        categories = snapshot.availableCategories,
                        imprints = snapshot.availableImprints,
                        tags = snapshot.availableTags,
                        onSelectFilter = { filter ->
                            updateActiveFilterKey(encodeFilterKey(filter))
                        },
                    )
                }
            }

            if (snapshot.allShelfItems.isEmpty()) {
                item(key = "empty_shelf_state") {
                    EmptyBookshelfCard(
                        isSyncOrUpdateRunning = isShelfTaskRunning,
                        onCloudSyncClick = { launchCloudSync(forceRefreshAll = false) },
                        onOpenOnlineSearch = { onOpenOnlineSearch(localQuery) },
                    )
                }
            } else if (displayedItems.isEmpty()) {
                item(key = "empty_filter_state") {
                    EmptyFilteredBookshelfCard(
                        onClearFilters = {
                            updateLocalQuery("")
                            updateActiveFilterKey(encodeFilterKey(BookshelfFilter.All))
                        },
                    )
                }
            } else {
                when (viewMode) {
                    BookshelfViewMode.GRID -> {
                        items(
                            items = gridRows,
                            key = { row -> "grid_row_" + row.joinToString("_") { it.aid.toString() } },
                        ) { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowItems.forEach { item ->
                                    BookshelfGridItemCard(
                                        item = item,
                                        isSelected = item.aid in selectedAidSet,
                                        isEinkMode = isEinkMode,
                                        onClick = {
                                            if (isMultiSelectActive) {
                                                toggleSelection(item.aid)
                                            } else {
                                                coroutineScope.launch {
                                                    bookshelfRepository.clearUnreadUpdateBadge(item.aid)
                                                }
                                                val cardItem = item.toDiscoveryCardItem()
                                                val origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN
                                                onNovelClickedWithOrigin?.invoke(cardItem, origin)
                                                    ?: onNovelClicked(cardItem)
                                            }
                                        },
                                        onLongClick = { toggleSelection(item.aid) },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(gridColumnCount - rowItems.size) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    BookshelfViewMode.DETAIL_LIST -> {
                        items(
                            items = displayedItems,
                            key = { item -> "detail_${item.aid}" },
                        ) { item ->
                            BookshelfDetailListCard(
                                item = item,
                                isSelected = item.aid in selectedAidSet,
                                isEinkMode = isEinkMode,
                                onClick = {
                                    if (isMultiSelectActive) {
                                        toggleSelection(item.aid)
                                    } else {
                                        coroutineScope.launch {
                                            bookshelfRepository.clearUnreadUpdateBadge(item.aid)
                                        }
                                        val cardItem = item.toDiscoveryCardItem()
                                        val origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN
                                        onNovelClickedWithOrigin?.invoke(cardItem, origin)
                                            ?: onNovelClicked(cardItem)
                                    }
                                },
                                onLongClick = { toggleSelection(item.aid) },
                            )
                        }
                    }

                    BookshelfViewMode.SPINE_3D -> {
                        items(
                            items = spineRows,
                            key = { row -> "spine_row_" + row.joinToString("_") { it.aid.toString() } },
                        ) { rowItems ->
                            BookshelfSpineShelfRow(
                                rowItems = rowItems,
                                totalSlots = spineColumnCount,
                                selectedAidSet = selectedAidSet,
                                isEinkMode = isEinkMode,
                                onItemClick = { item ->
                                    if (isMultiSelectActive) {
                                        toggleSelection(item.aid)
                                    } else {
                                        coroutineScope.launch {
                                            bookshelfRepository.clearUnreadUpdateBadge(item.aid)
                                        }
                                        val cardItem = item.toDiscoveryCardItem()
                                        val origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN
                                        onNovelClickedWithOrigin?.invoke(cardItem, origin)
                                            ?: onNovelClicked(cardItem)
                                    }
                                },
                                onItemLongClick = { item -> toggleSelection(item.aid) },
                            )
                        }
                    }
                }
            }
        }
        }

        if (isMultiSelectActive) {
            BookshelfMultiSelectFloatingToolbar(
                selectedCount = selectedAidSet.size,
                isSyncOrUpdateRunning = isShelfTaskRunning,
                onSelectAll = {
                    val visibleAids = displayedItems.map { it.aid }
                    selectedAidList = if (visibleAids.all { it in selectedAidSet }) {
                        emptyList()
                    } else {
                        visibleAids
                    }
                },
                onSmartUpdateSelected = {
                    val targets = selectedAidList
                    selectedAidList = emptyList()
                    launchSmartUpdate(targets)
                },
                onPinToTopSelected = {
                    val targets = selectedAidList
                    selectedAidList = emptyList()
                    coroutineScope.launch {
                        bookshelfRepository.pinNovelsToTop(targets)
                    }
                },
                onClearCacheSelected = {
                    val targets = selectedAidList
                    selectedAidList = emptyList()
                    coroutineScope.launch {
                        bookshelfRepository.clearCachedChaptersForNovels(targets)
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.bookshelf_cache_cleared_snackbar),
                        )
                    }
                },
                onRemoveSelected = { showRemoveConfirmDialog = true },
                onDone = { selectedAidList = emptyList() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = if (isMultiSelectActive) 116.dp else 12.dp),
        )
    }

    if (showForceSyncConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showForceSyncConfirmDialog = false },
            title = { Text(text = stringResource(R.string.bookshelf_force_sync_confirm_title)) },
            text = { Text(text = stringResource(R.string.bookshelf_force_sync_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showForceSyncConfirmDialog = false
                        launchCloudSync(forceRefreshAll = true)
                    },
                    enabled = !isShelfTaskRunning,
                    modifier = Modifier.testTag(BookshelfTestTags.CONFIRM_FORCE_SYNC_BUTTON),
                ) {
                    Text(text = stringResource(R.string.bookshelf_force_sync_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showForceSyncConfirmDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }

    if (showRemoveConfirmDialog && selectedAidList.isNotEmpty()) {
        val pendingRemoveAids = selectedAidList
        AlertDialog(
            onDismissRequest = { showRemoveConfirmDialog = false },
            title = { Text(text = stringResource(R.string.bookshelf_remove_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.bookshelf_remove_confirm_body_format,
                        pendingRemoveAids.size,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRemoveConfirmDialog = false
                        selectedAidList = emptyList()
                        coroutineScope.launch {
                            bookshelfRepository.removeNovelsFromBookshelf(
                                aids = pendingRemoveAids,
                                removeFromCloudIfSignedIn = true,
                                deleteOfflineCache = false,
                            )
                        }
                    },
                    modifier = Modifier.testTag(BookshelfTestTags.CONFIRM_REMOVE_BUTTON),
                ) {
                    Text(text = stringResource(R.string.bookshelf_batch_remove))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveConfirmDialog = false }) {
                    Text(text = stringResource(android.R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun OfflineFallbackShelfBanner(
    onDismiss: () -> Unit,
) {
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
            val dismissBannerLabel = stringResource(R.string.action_dismiss_banner)
            TooltipIconButton(
                tooltipText = dismissBannerLabel,
                onClick = onDismiss,
                modifier = Modifier.testTag(MainScaffoldTestTags.DISMISS_OFFLINE_BANNER_BUTTON),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = dismissBannerLabel,
                )
            }
        }
    }
}

@Composable
private fun BookshelfSearchBarRow(
    localQuery: String,
    onLocalQueryChange: (String) -> Unit,
    onOpenOnlineSearch: () -> Unit,
) {
    val barShape = MaterialTheme.shapes.extraLarge
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = barShape,
        modifier = Modifier
            .fillMaxWidth()
            .wenku8SharedSearchBarBounds(
                origin = Wenku8SharedElementKeys.SEARCH_BAR_BOOKSHELF_ORIGIN,
                clipShape = barShape,
            )
            .einkHairlineBorder(barShape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (localQuery.isEmpty()) {
                    Text(
                        text = stringResource(R.string.bookshelf_local_search_placeholder),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                BasicTextField(
                    value = localQuery,
                    onValueChange = onLocalQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .testTag(BookshelfTestTags.LOCAL_SEARCH_INPUT),
                )
            }
            if (localQuery.isNotEmpty()) {
                val clearLabel = stringResource(R.string.action_clear)
                TooltipIconButton(
                    tooltipText = clearLabel,
                    onClick = { onLocalQueryChange("") },
                    modifier = Modifier.testTag(BookshelfTestTags.CLEAR_LOCAL_SEARCH_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = clearLabel,
                    )
                }
            }
            FilledTonalButton(
                onClick = onOpenOnlineSearch,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag(BookshelfTestTags.ONLINE_SEARCH_TRIGGER),
            ) {
                Text(
                    text = stringResource(R.string.bookshelf_online_search_button),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookshelfHeaderSection(
    totalShelfCount: Int,
    isShelfTaskRunning: Boolean,
    isEinkMode: Boolean,
    viewMode: BookshelfViewMode,
    onViewModeSelected: (BookshelfViewMode) -> Unit,
    sortMode: BookshelfSortMode,
    onSortModeSelected: (BookshelfSortMode) -> Unit,
    showSmartUpdateMenu: Boolean,
    onShowSmartUpdateMenuChange: (Boolean) -> Unit,
    showSortMenu: Boolean,
    onShowSortMenuChange: (Boolean) -> Unit,
    onCloudSyncClick: () -> Unit,
    onSmartUpdateAllClick: () -> Unit,
    onForceRefreshCloudClick: () -> Unit,
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
                text = stringResource(R.string.bookshelf_count_title_format, totalShelfCount),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )

            Box {
                val cloudSyncLabel = stringResource(R.string.bookshelf_cloud_sync_action)
                val moreSyncOptionsLabel = stringResource(R.string.bookshelf_more_sync_options)
                SplitButtonLayout(
                    leadingButton = {
                        SplitButtonDefaults.TonalLeadingButton(
                            onClick = onCloudSyncClick,
                            enabled = !isShelfTaskRunning,
                            modifier = Modifier.testTag(BookshelfTestTags.CLOUD_SYNC_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CloudSync,
                                contentDescription = null,
                                modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = cloudSyncLabel,
                                maxLines = 1,
                            )
                        }
                    },
                    trailingButton = {
                        ActionTooltipBox(tooltipText = moreSyncOptionsLabel) {
                            SplitButtonDefaults.TonalTrailingButton(
                                checked = showSmartUpdateMenu,
                                onCheckedChange = { nextExpanded ->
                                    if (!isShelfTaskRunning) {
                                        onShowSmartUpdateMenuChange(nextExpanded)
                                    }
                                },
                                enabled = !isShelfTaskRunning,
                                modifier = Modifier.testTag(BookshelfTestTags.SMART_UPDATE_TRAILING_BUTTON),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ExpandMore,
                                    contentDescription = moreSyncOptionsLabel,
                                    modifier = Modifier.size(SplitButtonDefaults.TrailingIconSize),
                                )
                            }
                        }
                    },
                )

                DropdownMenu(
                    expanded = showSmartUpdateMenu && !isShelfTaskRunning,
                    onDismissRequest = { onShowSmartUpdateMenuChange(false) },
                ) {
                    DropdownMenuItem(
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = stringResource(R.string.bookshelf_smart_update_action),
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                                Text(
                                    text = stringResource(R.string.bookshelf_smart_update_menu_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.SystemUpdateAlt, contentDescription = null)
                        },
                        enabled = totalShelfCount > 0 && !isShelfTaskRunning,
                        onClick = {
                            onShowSmartUpdateMenuChange(false)
                            onSmartUpdateAllClick()
                        },
                        modifier = Modifier.testTag(BookshelfTestTags.SMART_UPDATE_MENU_ITEM),
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.bookshelf_force_sync_menu)) },
                        leadingIcon = {
                            Icon(imageVector = Icons.Outlined.CloudSync, contentDescription = null)
                        },
                        enabled = !isShelfTaskRunning,
                        onClick = {
                            onShowSmartUpdateMenuChange(false)
                            onForceRefreshCloudClick()
                        },
                        modifier = Modifier.testTag(BookshelfTestTags.FORCE_CLOUD_SYNC_MENU_ITEM),
                    )
                }
            }
        }

        if (isShelfTaskRunning) {
            if (isEinkMode) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BookshelfTestTags.HEADER_ACTIVE_SYNC_PROGRESS),
                )
            } else {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(BookshelfTestTags.HEADER_ACTIVE_SYNC_PROGRESS),
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                BookshelfViewMode.entries.forEach { mode ->
                    val labelRes = when (mode) {
                        BookshelfViewMode.GRID -> R.string.bookshelf_view_grid
                        BookshelfViewMode.DETAIL_LIST -> R.string.bookshelf_view_list
                        BookshelfViewMode.SPINE_3D -> R.string.bookshelf_view_spine
                    }
                    val icon = when (mode) {
                        BookshelfViewMode.GRID -> Icons.Outlined.GridView
                        BookshelfViewMode.DETAIL_LIST -> Icons.AutoMirrored.Outlined.ViewList
                        BookshelfViewMode.SPINE_3D -> Icons.Outlined.ViewWeek
                    }
                    FilterChip(
                        selected = viewMode == mode,
                        onClick = { onViewModeSelected(mode) },
                        label = { Text(text = stringResource(labelRes), maxLines = 1) },
                        leadingIcon = {
                            Icon(
                                imageVector = icon,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                        },
                        modifier = Modifier.testTag(BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + mode.name),
                    )
                }
            }

            Box {
                val activeSortLabel = sortModeLabel(sortMode)
                TextButton(
                    onClick = { onShowSortMenuChange(true) },
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag(BookshelfTestTags.SORT_MODE_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = activeSortLabel,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { onShowSortMenuChange(false) },
                ) {
                    BookshelfSortMode.entries.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(text = sortModeLabel(candidate)) },
                            trailingIcon = if (candidate == sortMode) {
                                { Icon(imageVector = Icons.Outlined.Check, contentDescription = null) }
                            } else {
                                null
                            },
                            onClick = {
                                onShowSortMenuChange(false)
                                onSortModeSelected(candidate)
                            },
                            modifier = Modifier.testTag(
                                BookshelfTestTags.SORT_MODE_ITEM_PREFIX + candidate.name,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCarouselSection(
    items: List<BookshelfItemModel>,
    onCardClick: (BookshelfItemModel) -> Unit,
    onContinueReadingClick: (BookshelfItemModel) -> Unit,
) {
    val layoutDirection = LocalLayoutDirection.current
    val isEinkMode = LocalEinkMode.current
    val carouselCardShape = MaterialTheme.shapes.medium

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(R.string.bookshelf_continue_reading_section),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )

        if (items.size == 1) {
            val singleItem = items.first()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(146.dp)
                    .testTag(BookshelfTestTags.CONTINUE_READING_CAROUSEL),
            ) {
                Card(
                    onClick = { onCardClick(singleItem) },
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                    shape = carouselCardShape,
                    border = if (isEinkMode) {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    } else {
                        null
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(BookshelfTestTags.CONTINUE_READING_ITEM_PREFIX + singleItem.aid),
                ) {
                    ContinueReadingCardBody(
                        item = singleItem,
                        carouselCardShape = carouselCardShape,
                        isMostlyExpanded = true,
                        onContinueReadingClick = onContinueReadingClick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        } else {
            val carouselState = rememberCarouselState { items.size }
            val itemSpacing = 12.dp
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(146.dp),
            ) {
                val evenSplitWidth =
                    (maxWidth - itemSpacing * (items.size - 1).coerceAtLeast(0)) / items.size.coerceAtLeast(1)
                val preferredItemWidth = maxOf(284.dp, evenSplitWidth)

                HorizontalMultiBrowseCarousel(
                    state = carouselState,
                    preferredItemWidth = preferredItemWidth,
                    itemSpacing = itemSpacing,
                    minSmallItemWidth = 56.dp,
                    maxSmallItemWidth = 104.dp,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag(BookshelfTestTags.CONTINUE_READING_CAROUSEL),
                ) { index ->
                    val item = items[index]
                    val drawInfo = carouselItemDrawInfo
                    val isMostlyExpanded =
                        drawInfo.maxSize <= 0f || drawInfo.size >= drawInfo.maxSize * 0.85f

                    Card(
                        onClick = { onCardClick(item) },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                        shape = carouselCardShape,
                        modifier = Modifier
                            .fillMaxSize()
                            .maskClip(carouselCardShape)
                            .then(
                                if (isEinkMode) {
                                    Modifier.maskBorder(
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        carouselCardShape,
                                    )
                                } else {
                                    Modifier
                                },
                            )
                            .testTag(BookshelfTestTags.CONTINUE_READING_ITEM_PREFIX + item.aid),
                    ) {
                        ContinueReadingCardBody(
                            item = item,
                            carouselCardShape = carouselCardShape,
                            isMostlyExpanded = isMostlyExpanded,
                            onContinueReadingClick = onContinueReadingClick,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    // Material3's `HorizontalMultiBrowseCarousel` centers `maskRect` inside
                                    // `[0, itemMainAxisSize]`. Shifting inner content to `maskRect.left`
                                    // makes partially-collapsed cards expand from the left edge so the flush
                                    // book cover is always shown first instead of centered text!
                                    translationX = if (layoutDirection == LayoutDirection.Rtl) {
                                        -(size.width - drawInfo.maskRect.right)
                                    } else {
                                        drawInfo.maskRect.left
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContinueReadingCardBody(
    item: BookshelfItemModel,
    carouselCardShape: androidx.compose.ui.graphics.Shape,
    isMostlyExpanded: Boolean,
    onContinueReadingClick: (BookshelfItemModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Flush edge-to-edge cover (`贴边显示`): zero top/bottom/left margin so collapsed
        // cards frame pure cover art and expanded cards present a clean flush spine.
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(0.71f)
                .wenku8SharedNovelCover(
                    origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CAROUSEL_ORIGIN,
                    aid = item.aid,
                    clipShape = carouselCardShape,
                    enabled = isMostlyExpanded,
                )
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .testTag(BookshelfTestTags.CONTINUE_READING_COVER_PREFIX + item.aid),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
            if (item.coverUrl.isNotBlank()) {
                AsyncImage(
                    model = WenkuImageRequests.rememberCoverImageRequest(
                        aid = item.aid,
                        coverUrl = item.coverUrl,
                        enableCrossfade = false,
                    ),
                    contentDescription = item.novel.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = item.novel.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.wenku8SharedNovelTitle(
                        origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CAROUSEL_ORIGIN,
                        aid = item.aid,
                        enabled = isMostlyExpanded,
                    ),
                )
                val chapterLabel = item.readingProgress?.let { prog ->
                    "${prog.volumeTitle} · ${prog.chapterTitle}".trim(' ', '·')
                }?.takeIf { it.isNotBlank() } ?: item.novel.latestSectionName
                if (chapterLabel.isNotBlank()) {
                    Text(
                        text = chapterLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Resting reading progress ALWAYS uses flat LinearProgressIndicator
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = { item.readingProgressFraction },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(CircleShape),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.bookshelf_progress_percent_format,
                            item.readingProgressPercent,
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                FilledTonalButton(
                    onClick = { onContinueReadingClick(item) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag(BookshelfTestTags.CONTINUE_READING_BUTTON_PREFIX + item.aid),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.bookshelf_continue_reading_action),
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun BookshelfFilterChipRail(
    activeFilter: BookshelfFilter,
    unreadUpdateCount: Int,
    categories: List<String>,
    imprints: List<String>,
    tags: List<String>,
    onSelectFilter: (BookshelfFilter) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "chip_all") {
            FilterChip(
                selected = activeFilter == BookshelfFilter.All,
                onClick = { onSelectFilter(BookshelfFilter.All) },
                label = { Text(text = stringResource(R.string.bookshelf_filter_all)) },
                modifier = Modifier.testTag(BookshelfTestTags.FILTER_CHIP_ALL),
            )
        }
        item(key = "chip_new") {
            val baseLabel = stringResource(R.string.bookshelf_filter_new_chapters)
            val labelText = if (unreadUpdateCount > 0) "$baseLabel ($unreadUpdateCount)" else baseLabel
            FilterChip(
                selected = activeFilter == BookshelfFilter.HasNewChapters,
                onClick = { onSelectFilter(BookshelfFilter.HasNewChapters) },
                label = { Text(text = labelText) },
                modifier = Modifier.testTag(BookshelfTestTags.FILTER_CHIP_NEW_CHAPTERS),
            )
        }
        item(key = "chip_cached") {
            FilterChip(
                selected = activeFilter == BookshelfFilter.CachedOffline,
                onClick = { onSelectFilter(BookshelfFilter.CachedOffline) },
                label = { Text(text = stringResource(R.string.bookshelf_filter_cached)) },
                modifier = Modifier.testTag(BookshelfTestTags.FILTER_CHIP_CACHED),
            )
        }
        item(key = "chip_serializing") {
            FilterChip(
                selected = activeFilter == BookshelfFilter.Serializing,
                onClick = { onSelectFilter(BookshelfFilter.Serializing) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.EditNote,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = { Text(text = stringResource(R.string.bookshelf_filter_serializing)) },
                modifier = Modifier.testTag(BookshelfTestTags.FILTER_CHIP_SERIALIZING),
            )
        }
        item(key = "chip_completed") {
            FilterChip(
                selected = activeFilter == BookshelfFilter.Completed,
                onClick = { onSelectFilter(BookshelfFilter.Completed) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                },
                label = { Text(text = stringResource(R.string.bookshelf_filter_completed)) },
                modifier = Modifier.testTag(BookshelfTestTags.FILTER_CHIP_COMPLETED),
            )
        }

        items(
            items = categories,
            key = { cat -> "chip_cat_$cat" },
        ) { category ->
            val target = BookshelfFilter.Category(category)
            FilterChip(
                selected = activeFilter == target,
                onClick = { onSelectFilter(target) },
                label = { Text(text = category) },
                modifier = Modifier.testTag(BookshelfTestTags.FILTER_CHIP_CATEGORY_PREFIX + category),
            )
        }

        items(
            items = imprints.take(6),
            key = { press -> "chip_press_$press" },
        ) { press ->
            val target = BookshelfFilter.Imprint(press)
            FilterChip(
                selected = activeFilter == target,
                onClick = { onSelectFilter(target) },
                label = { Text(text = press) },
                modifier = Modifier.testTag(BookshelfTestTags.FILTER_CHIP_IMPRINT_PREFIX + press),
            )
        }

        items(
            items = tags,
            key = { tag -> "chip_tag_$tag" },
        ) { tag ->
            val target = BookshelfFilter.Tag(tag)
            FilterChip(
                selected = activeFilter == target,
                onClick = { onSelectFilter(target) },
                label = { Text(text = "#$tag") },
                modifier = Modifier.testTag(BookshelfTestTags.FILTER_CHIP_TAG_PREFIX + tag),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookshelfGridItemCard(
    item: BookshelfItemModel,
    isSelected: Boolean,
    isEinkMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.medium,
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .einkHairlineBorder(MaterialTheme.shapes.medium)
            .testTag(BookshelfTestTags.BOOK_CARD_PREFIX + item.aid),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            val gridCoverShape = MaterialTheme.shapes.small
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.71f)
                    .wenku8SharedNovelCover(
                        origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN,
                        aid = item.aid,
                        clipShape = gridCoverShape,
                    )
                    .clip(gridCoverShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(28.dp),
                )
                if (item.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = WenkuImageRequests.rememberCoverImageRequest(
                            aid = item.aid,
                            coverUrl = item.coverUrl,
                            enableCrossfade = false,
                        ),
                        contentDescription = item.novel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (item.novel.hasUnreadUpdate) {
                    NewChapterPillBadge(
                        aid = item.aid,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(6.dp),
                    )
                }

                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(22.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.novel.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.wenku8SharedNovelTitle(
                    origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN,
                    aid = item.aid,
                ),
            )

            Spacer(modifier = Modifier.height(4.dp))

            BookshelfItemProgressIndicator(
                item = item,
                isEinkMode = isEinkMode,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfDetailListCard(
    item: BookshelfItemModel,
    isSelected: Boolean,
    isEinkMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val containerColor = if (isSelected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large,
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .einkHairlineBorder(MaterialTheme.shapes.large)
            .testTag(BookshelfTestTags.BOOK_CARD_PREFIX + item.aid),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            val detailCoverShape = MaterialTheme.shapes.small
            Box(
                modifier = Modifier
                    .width(76.dp)
                    .aspectRatio(0.71f)
                    .wenku8SharedNovelCover(
                        origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN,
                        aid = item.aid,
                        clipShape = detailCoverShape,
                    )
                    .clip(detailCoverShape)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            ) {
                Icon(
                    imageVector = Icons.Outlined.AutoStories,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                )
                if (item.coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = WenkuImageRequests.rememberCoverImageRequest(
                            aid = item.aid,
                            coverUrl = item.coverUrl,
                            enableCrossfade = false,
                        ),
                        contentDescription = item.novel.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(4.dp)
                            .size(20.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = item.novel.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .wenku8SharedNovelTitle(
                                origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN,
                                aid = item.aid,
                            ),
                    )
                    if (item.novel.hasUnreadUpdate) {
                        NewChapterPillBadge(aid = item.aid)
                    }
                }

                val displayBookStatus = formatBookStatusLabel(item.novel.bookStatus)
                val metaParts = buildList {
                    if (item.novel.author.isNotBlank()) add(item.novel.author)
                    if (item.novel.pressId.isNotBlank()) add(item.novel.pressId)
                }
                if (metaParts.isNotEmpty() || displayBookStatus.isNotBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (metaParts.isNotEmpty()) {
                            Text(
                                text = metaParts.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                        }
                        BookStatusBadge(
                            rawStatus = item.novel.bookStatus,
                            modifier = Modifier.testTag(
                                DiscoveryTestTags.BOOK_STATUS_BADGE_PREFIX + item.aid,
                            ),
                        )
                    }
                }

                val progressLabel = item.readingProgress?.let { prog ->
                    "${prog.volumeTitle} · ${prog.chapterTitle}".trim(' ', '·')
                }?.takeIf { it.isNotBlank() }
                val statusLine = when {
                    progressLabel != null -> progressLabel
                    item.novel.latestSectionName.isNotBlank() -> item.novel.latestSectionName
                    else -> item.novel.lastUpdate
                }
                if (statusLine.isNotBlank()) {
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (item.cachedChapterCount > 0) {
                    val cacheLabel = if (item.totalChapterCount > 0) {
                        stringResource(
                            R.string.bookshelf_cached_ratio_format,
                            item.cachedChapterCount,
                            item.totalChapterCount,
                        )
                    } else {
                        stringResource(R.string.bookshelf_cached_count_format, item.cachedChapterCount)
                    }
                    Text(
                        text = cacheLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
                BookshelfItemProgressIndicator(
                    item = item,
                    isEinkMode = isEinkMode,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookshelfSpineShelfRow(
    rowItems: List<BookshelfItemModel>,
    totalSlots: Int,
    selectedAidSet: Set<Int>,
    isEinkMode: Boolean,
    onItemClick: (BookshelfItemModel) -> Unit,
    onItemLongClick: (BookshelfItemModel) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(176.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            rowItems.forEach { item ->
                val isSelected = item.aid in selectedAidSet
                val spineTone = if (isEinkMode) {
                    MaterialTheme.colorScheme.surface
                } else {
                    val palette = listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.secondaryContainer,
                        MaterialTheme.colorScheme.tertiaryContainer,
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                    )
                    palette[kotlin.math.abs(item.aid) % palette.size]
                }
                val contentTone = if (isEinkMode) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                }
                val verticalChars = remember(item.novel.title) {
                    item.novel.title
                        .take(9)
                        .toCharArray()
                        .joinToString("\n")
                }

                val spineShape = MaterialTheme.shapes.small
                Surface(
                    color = spineTone,
                    contentColor = contentTone,
                    shape = spineShape,
                    border = if (isSelected) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                    } else {
                        BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(if (item.aid % 2 == 0) 0.96f else 1.0f)
                        .wenku8SharedNovelCover(
                            origin = Wenku8SharedElementKeys.NOVEL_BOOKSHELF_CARD_ORIGIN,
                            aid = item.aid,
                            clipShape = spineShape,
                        )
                        .clip(spineShape)
                        .combinedClickable(
                            onClick = { onItemClick(item) },
                            onLongClick = { onItemLongClick(item) },
                        )
                        .einkHairlineBorder(spineShape)
                        .testTag(BookshelfTestTags.BOOK_CARD_PREFIX + item.aid),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        if (item.novel.hasUnreadUpdate) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.error)
                                    .testTag(BookshelfTestTags.NEW_CHAPTER_BADGE_PREFIX + item.aid),
                            )
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            text = verticalChars,
                            style = MaterialTheme.typography.labelMedium.copy(
                                lineHeight = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            textAlign = TextAlign.Center,
                            maxLines = 9,
                            overflow = TextOverflow.Clip,
                        )

                        BookshelfItemProgressIndicator(
                            item = item,
                            isEinkMode = isEinkMode,
                            showPercentageLabel = false,
                        )
                    }
                }
            }
            repeat(totalSlots - rowItems.size) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }

        // Tactile shelf plank ledge beneath the row of vertical spines
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(
                    if (isEinkMode) {
                        Color.Black
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.extraSmall),
        )
    }
}

/**
 * Enforces the `v2.0` Bookshelf progress indicator rule:
 * - Resting shelf cards ALWAYS use flat [LinearProgressIndicator], paired with an inline `%` label
 *   on Grid and Detail List cards.
 * - [LinearWavyProgressIndicator] appears strictly when [BookshelfItemModel.isActivelySyncingOrDownloading]
 *   is `true` AND `!isEinkMode`.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookshelfItemProgressIndicator(
    item: BookshelfItemModel,
    isEinkMode: Boolean,
    showPercentageLabel: Boolean = true,
) {
    if (item.isActivelySyncingOrDownloading) {
        if (isEinkMode) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .testTag(BookshelfTestTags.FLAT_READING_PROGRESS_PREFIX + item.aid),
            )
        } else {
            LinearWavyProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .testTag(BookshelfTestTags.ACTIVE_WAVY_PROGRESS_PREFIX + item.aid),
            )
        }
    } else if (showPercentageLabel) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            LinearProgressIndicator(
                progress = { item.readingProgressFraction },
                modifier = Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(CircleShape)
                    .testTag(BookshelfTestTags.FLAT_READING_PROGRESS_PREFIX + item.aid),
            )
            Text(
                text = stringResource(
                    R.string.bookshelf_progress_percent_format,
                    item.readingProgressPercent,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.testTag(BookshelfTestTags.READING_PROGRESS_PERCENT_PREFIX + item.aid),
            )
        }
    } else {
        LinearProgressIndicator(
            progress = { item.readingProgressFraction },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
                .testTag(BookshelfTestTags.FLAT_READING_PROGRESS_PREFIX + item.aid),
        )
    }
}

@Composable
private fun NewChapterPillBadge(
    aid: Int,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = CircleShape,
        modifier = modifier
            .semantics(mergeDescendants = true) {}
            .testTag(BookshelfTestTags.NEW_CHAPTER_BADGE_PREFIX + aid),
    ) {
        Text(
            text = stringResource(R.string.bookshelf_badge_new_chapter),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun BookshelfMultiSelectFloatingToolbar(
    selectedCount: Int,
    isSyncOrUpdateRunning: Boolean,
    onSelectAll: () -> Unit,
    onSmartUpdateSelected: () -> Unit,
    onPinToTopSelected: () -> Unit,
    onClearCacheSelected: () -> Unit,
    onRemoveSelected: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
            shape = CircleShape,
            modifier = Modifier
                .semantics(mergeDescendants = true) {}
                .einkHairlineBorder(CircleShape),
        ) {
            Text(
                text = stringResource(R.string.bookshelf_batch_selected_count_format, selectedCount),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }

        HorizontalFloatingToolbar(
            expanded = true,
            colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            modifier = Modifier
                .einkHairlineBorder(FloatingToolbarDefaults.ContainerShape)
                .testTag(BookshelfTestTags.MULTI_SELECT_TOOLBAR),
        ) {
            val selectAllLabel = stringResource(R.string.bookshelf_batch_select_all)
            TooltipIconButton(
                tooltipText = selectAllLabel,
                onClick = onSelectAll,
                anchorPosition = TooltipAnchorPosition.Above,
                modifier = Modifier.testTag(BookshelfTestTags.BATCH_SELECT_ALL),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SelectAll,
                    contentDescription = selectAllLabel,
                )
            }
            val smartUpdateLabel = stringResource(R.string.bookshelf_smart_update_action)
            TooltipIconButton(
                tooltipText = smartUpdateLabel,
                onClick = onSmartUpdateSelected,
                enabled = !isSyncOrUpdateRunning,
                anchorPosition = TooltipAnchorPosition.Above,
                modifier = Modifier.testTag(BookshelfTestTags.BATCH_SMART_UPDATE),
            ) {
                Icon(
                    imageVector = Icons.Outlined.SystemUpdateAlt,
                    contentDescription = smartUpdateLabel,
                )
            }
            val pinTopLabel = stringResource(R.string.bookshelf_batch_pin_top)
            TooltipIconButton(
                tooltipText = pinTopLabel,
                onClick = onPinToTopSelected,
                anchorPosition = TooltipAnchorPosition.Above,
                modifier = Modifier.testTag(BookshelfTestTags.BATCH_PIN_TOP),
            ) {
                Icon(
                    imageVector = Icons.Outlined.VerticalAlignTop,
                    contentDescription = pinTopLabel,
                )
            }
            val clearCacheLabel = stringResource(R.string.bookshelf_batch_clear_cache)
            TooltipIconButton(
                tooltipText = clearCacheLabel,
                onClick = onClearCacheSelected,
                anchorPosition = TooltipAnchorPosition.Above,
                modifier = Modifier.testTag(BookshelfTestTags.BATCH_CLEAR_CACHE),
            ) {
                Icon(
                    imageVector = Icons.Outlined.CleaningServices,
                    contentDescription = clearCacheLabel,
                )
            }
            val removeLabel = stringResource(R.string.bookshelf_batch_remove)
            TooltipIconButton(
                tooltipText = removeLabel,
                onClick = onRemoveSelected,
                anchorPosition = TooltipAnchorPosition.Above,
                modifier = Modifier.testTag(BookshelfTestTags.BATCH_REMOVE),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DeleteOutline,
                    contentDescription = removeLabel,
                )
            }
            val doneLabel = stringResource(R.string.bookshelf_batch_done)
            TooltipIconButton(
                tooltipText = doneLabel,
                onClick = onDone,
                anchorPosition = TooltipAnchorPosition.Above,
                modifier = Modifier.testTag(BookshelfTestTags.BATCH_DONE),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Check,
                    contentDescription = doneLabel,
                )
            }
        }
    }
}

@Composable
private fun EmptyBookshelfCard(
    isSyncOrUpdateRunning: Boolean,
    onCloudSyncClick: () -> Unit,
    onOpenOnlineSearch: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.large)
            .testTag(BookshelfTestTags.EMPTY_SHELF_STATE),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoStories,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
            Text(
                text = stringResource(R.string.bookshelf_empty_shelf_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = stringResource(R.string.bookshelf_empty_shelf_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilledTonalButton(
                    onClick = onCloudSyncClick,
                    enabled = !isSyncOrUpdateRunning,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CloudSync,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.bookshelf_cloud_sync_action))
                }
                Button(onClick = onOpenOnlineSearch) {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.bookshelf_online_search_button))
                }
            }
        }
    }
}

@Composable
private fun EmptyFilteredBookshelfCard(
    onClearFilters: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.large)
            .testTag(BookshelfTestTags.EMPTY_FILTER_STATE),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.bookshelf_empty_filter_title),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            FilledTonalButton(onClick = onClearFilters) {
                Text(text = stringResource(R.string.bookshelf_clear_filter_action))
            }
        }
    }
}

@Composable
private fun sortModeLabel(sortMode: BookshelfSortMode): String = when (sortMode) {
    BookshelfSortMode.RECENT_READ -> stringResource(R.string.bookshelf_sort_recent_read)
    BookshelfSortMode.LAST_UPDATE -> stringResource(R.string.bookshelf_sort_last_update)
    BookshelfSortMode.MANUAL_ORDER -> stringResource(R.string.bookshelf_sort_manual)
    BookshelfSortMode.TITLE_PINYIN -> stringResource(R.string.bookshelf_sort_pinyin)
}

private fun encodeFilterKey(filter: BookshelfFilter): String = when (filter) {
    BookshelfFilter.All -> "all"
    BookshelfFilter.HasNewChapters -> "new"
    BookshelfFilter.CachedOffline -> "cached"
    BookshelfFilter.Serializing -> "serial"
    BookshelfFilter.Completed -> "done"
    is BookshelfFilter.Category -> "cat:${filter.categoryName}"
    is BookshelfFilter.Imprint -> "press:${filter.pressName}"
    is BookshelfFilter.Tag -> "tag:${filter.tagName}"
}

private fun decodeFilterKey(raw: String): BookshelfFilter = when {
    raw == "new" -> BookshelfFilter.HasNewChapters
    raw == "cached" -> BookshelfFilter.CachedOffline
    raw == "serial" -> BookshelfFilter.Serializing
    raw == "done" -> BookshelfFilter.Completed
    raw.startsWith("cat:") -> BookshelfFilter.Category(raw.removePrefix("cat:"))
    raw.startsWith("press:") -> BookshelfFilter.Imprint(raw.removePrefix("press:"))
    raw.startsWith("tag:") -> BookshelfFilter.Tag(raw.removePrefix("tag:"))
    else -> BookshelfFilter.All
}

internal fun BookshelfItemModel.toDiscoveryCardItem(): DiscoveryNovelCardItem {
    return DiscoveryNovelCardItem(
        aid = novel.aid,
        title = novel.title,
        author = novel.author,
        bookStatus = novel.bookStatus,
        lastUpdate = novel.lastUpdate,
        tags = tags,
        introShort = novel.introShort,
        coverUrl = coverUrl,
        inLocalBookshelf = novel.inBookshelf,
    )
}
