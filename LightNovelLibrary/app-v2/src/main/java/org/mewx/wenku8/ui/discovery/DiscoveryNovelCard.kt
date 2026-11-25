package org.mewx.wenku8.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.R
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.data.repository.DiscoveryRepository
import org.mewx.wenku8.domain.model.DiscoveryFeedErrorKind
import org.mewx.wenku8.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.domain.model.FeedScrollPosition
import org.mewx.wenku8.domain.model.PaginatedNovelFeedState
import org.mewx.wenku8.domain.model.normalizeBookStatusText
import org.mewx.wenku8.ui.detail.BannerAdLoadPhase
import org.mewx.wenku8.ui.detail.LocalDiscoveryFeedInlineAdPool
import org.mewx.wenku8.ui.detail.ServerSupportBannerAd
import org.mewx.wenku8.ui.detail.findHostActivity
import org.mewx.wenku8.ui.detail.rememberDiscoveryFeedInlineAdPool
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import org.mewx.wenku8.ui.theme.wenku8SharedNovelCover
import org.mewx.wenku8.ui.theme.wenku8SharedNovelTitle
import org.mewx.wenku8.ui.theme.wenku8SharedSearchBarBounds

/**
 * Formats Wenku8's raw `BookStatus` (`"已完成"` from `do=meta` vs `"已完结"` / `"已完結"` from
 * `novellist`) into a consistent localized status label (`"已完结"` in SC, `"已完結"` in TC).
 */
@Composable
fun formatBookStatusLabel(
    rawStatus: String,
    appLanguage: Wenku8API.AppLanguage? = null,
): String {
    val trimmed = rawStatus.trim()
    if (trimmed.isEmpty()) return ""
    val localizedCompleted = stringResource(R.string.bookshelf_filter_completed)
    val preferTraditional = when (appLanguage) {
        Wenku8API.AppLanguage.TC -> true
        Wenku8API.AppLanguage.SC -> false
        null -> localizedCompleted == "已完結" ||
            trimmed == "已完結" ||
            trimmed == "完結" ||
            trimmed == "連載中"
    }
    return normalizeBookStatusText(
        rawStatus = trimmed,
        preferTraditionalChinese = preferTraditional,
    )
}

/**
 * Resolves a clear leading icon for serialized vs completed novel status labels (`已完结` / `连载中`).
 */
fun bookStatusLeadingIcon(statusText: String): ImageVector? {
    val trimmed = statusText.trim()
    if (trimmed.isEmpty()) return null
    return if (trimmed.contains("完")) {
        Icons.Outlined.CheckCircle
    } else {
        Icons.Outlined.EditNote
    }
}

@Composable
fun BookStatusBadge(
    rawStatus: String,
    modifier: Modifier = Modifier,
    appLanguage: Wenku8API.AppLanguage? = null,
) {
    val displayBookStatus = formatBookStatusLabel(
        rawStatus = rawStatus,
        appLanguage = appLanguage,
    )
    if (displayBookStatus.isBlank()) return
    val icon = bookStatusLeadingIcon(displayBookStatus)
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.extraSmall,
        modifier = modifier.einkHairlineBorder(MaterialTheme.shapes.extraSmall),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                )
            }
            Text(
                text = displayBookStatus,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

object DiscoveryTestTags {
    const val PERSISTENT_SEARCH_PILL = "discovery_persistent_search_pill"
    const val BOOK_STATUS_BADGE_PREFIX = "discovery_book_status_badge_"
    const val LATEST_TAB_PREFIX = "latest_tab_"
    const val RANKING_METRIC_PREFIX = "ranking_metric_"
    const val RANKING_PERIOD_PREFIX = "ranking_period_"
    const val NOVEL_CARD_PREFIX = "novel_card_"
    const val FEED_LIST = "discovery_feed_list"
    const val FEED_ERROR_CARD = "discovery_feed_error_card"
    const val FEED_RETRY_BUTTON = "discovery_feed_retry_button"
    const val FEED_DIAGNOSTICS_CHIP = "discovery_feed_diagnostics_chip"
    const val FEED_AD_CARD_PREFIX = "discovery_feed_ad_card_"
    const val FEED_AD_SLOT_PREFIX = "discovery_feed_ad_slot_"
}

@Composable
fun PersistentSearchEntryPill(
    onOpenSearch: () -> Unit,
    modifier: Modifier = Modifier,
    sharedTransitionOrigin: String = Wenku8SharedElementKeys.SEARCH_BAR_DEFAULT_ORIGIN,
) {
    val pillShape = MaterialTheme.shapes.extraLarge
    Surface(
        onClick = onOpenSearch,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = pillShape,
        modifier = modifier
            .fillMaxWidth()
            .wenku8SharedSearchBarBounds(
                origin = sharedTransitionOrigin,
                clipShape = pillShape,
            )
            .testTag(DiscoveryTestTags.PERSISTENT_SEARCH_PILL)
            .einkHairlineBorder(pillShape),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = null,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = stringResource(R.string.action_search_novels),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoveryNovelCard(
    item: DiscoveryNovelCardItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    rankNumber: Int? = null,
    sharedTransitionOrigin: String = Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN,
) {
    val isEink = LocalEinkMode.current
    val coverShape = MaterialTheme.shapes.medium
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(
            containerColor = if (isEink) {
                MaterialTheme.colorScheme.surface
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag(DiscoveryTestTags.NOVEL_CARD_PREFIX + item.aid)
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useCompactCover = maxWidth < 320.dp
            val coverWidth = if (useCompactCover) 68.dp else 76.dp
            val coverHeight = if (useCompactCover) 98.dp else 110.dp
            val contentPadding = if (useCompactCover) 12.dp else 14.dp
            val contentGap = if (useCompactCover) 10.dp else 14.dp

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(contentPadding),
                horizontalArrangement = Arrangement.spacedBy(contentGap),
                verticalAlignment = Alignment.Top,
            ) {
                // 2:3 Light Novel Book Cover with Offline-First Coil 3 + Shared-Element Morph
                Box(
                    modifier = Modifier
                        .size(width = coverWidth, height = coverHeight)
                        .wenku8SharedNovelCover(
                            origin = sharedTransitionOrigin,
                            aid = item.aid,
                            clipShape = coverShape,
                        )
                        .clip(coverShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .einkHairlineBorder(coverShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AutoStories,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
                        modifier = Modifier.size(26.dp),
                    )
                    if (item.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = WenkuImageRequests.rememberCoverImageRequest(
                                aid = item.aid,
                                coverUrl = item.coverUrl,
                                enableCrossfade = false,
                            ),
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    if (rankNumber != null) {
                        val isTopThree = rankNumber <= 3
                        Surface(
                            color = if (isTopThree) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.92f)
                            },
                            contentColor = if (isTopThree) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(4.dp)
                                .einkHairlineBorder(MaterialTheme.shapes.small),
                        ) {
                            Text(
                                text = "#$rankNumber",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.wenku8SharedNovelTitle(
                            origin = sharedTransitionOrigin,
                            aid = item.aid,
                        ),
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        itemVerticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (item.author.isNotBlank()) {
                            Text(
                                text = item.author,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BookStatusBadge(
                            rawStatus = item.bookStatus,
                            modifier = Modifier.testTag(DiscoveryTestTags.BOOK_STATUS_BADGE_PREFIX + item.aid),
                        )
                        if (item.inLocalBookshelf) {
                            Surface(
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                                shape = MaterialTheme.shapes.extraSmall,
                                modifier = Modifier.einkHairlineBorder(MaterialTheme.shapes.extraSmall),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.BookmarkAdded,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = stringResource(R.string.badge_in_bookshelf),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }

                    if (item.tags.isNotEmpty() || item.lastUpdate.isNotBlank()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            item.tags.take(3).forEach { tag ->
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.einkHairlineBorder(MaterialTheme.shapes.extraSmall),
                                ) {
                                    Text(
                                        text = tag,
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    )
                                }
                            }
                            if (item.lastUpdate.isNotBlank()) {
                                Text(
                                    text = item.lastUpdate,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false,
                                    modifier = Modifier.padding(vertical = 2.dp),
                                )
                            }
                        }
                    }

                    if (item.introShort.isNotBlank()) {
                        Text(
                            text = item.introShort,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Maintains an isolated [LazyListState] for each [Wenku8API.NovelSortedBy] feed (`周榜`, `日榜`,
 * `月榜`, `总榜`, `点击榜`, `推荐榜`, `总收藏`, `字数榜`, and `最新` tabs) so:
 * 1. Switching between rankings NEVER anchors to an overlapping novel `aid` from the previous list.
 * 2. Each ranking independently remembers its exact `(firstVisibleItemIndex, firstVisibleItemScrollOffset)`
 *    across sub-tab switches, `NovelDetailScreen` / `Reader` transitions ([DiscoveryRepository]),
 *    and Activity/configuration recreation (`rememberSaveable`).
 * 3. Re-tapping an already-selected ranking button smoothly returns that ranking to top (`0, 0`).
 */
@Stable
class DiscoveryFeedScrollStateHolder internal constructor(
    private val saveablePositions: MutableMap<String, FeedScrollPosition>,
    private val discoveryRepository: DiscoveryRepository,
) {
    private val listStates = HashMap<Wenku8API.NovelSortedBy, LazyListState>()

    fun listStateFor(sortedBy: Wenku8API.NovelSortedBy): LazyListState {
        return listStates.getOrPut(sortedBy) {
            val saved = saveablePositions[sortedBy.name]
                ?: discoveryRepository.getFeedScrollPosition(sortedBy)
            LazyListState(
                firstVisibleItemIndex = saved.firstVisibleItemIndex,
                firstVisibleItemScrollOffset = saved.firstVisibleItemScrollOffset,
            )
        }
    }

    internal fun recordScrollPosition(
        sortedBy: Wenku8API.NovelSortedBy,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int,
    ) {
        val clampedIndex = firstVisibleItemIndex.coerceAtLeast(0)
        val clampedOffset = firstVisibleItemScrollOffset.coerceAtLeast(0)
        saveablePositions[sortedBy.name] = FeedScrollPosition(clampedIndex, clampedOffset)
        discoveryRepository.updateFeedScrollPosition(sortedBy, clampedIndex, clampedOffset)
    }

    suspend fun scrollToTop(
        sortedBy: Wenku8API.NovelSortedBy,
        isEinkMode: Boolean,
    ) {
        val state = listStateFor(sortedBy)
        recordScrollPosition(sortedBy, 0, 0)
        if (state.firstVisibleItemIndex == 0 && state.firstVisibleItemScrollOffset == 0) {
            return
        }
        if (isEinkMode) {
            state.scrollToItem(0, 0)
        } else {
            if (state.firstVisibleItemIndex > 10) {
                state.scrollToItem(10, 0)
            }
            state.animateScrollToItem(0, 0)
        }
    }
}

private val SaveableFeedScrollMapSaver: Saver<MutableMap<String, FeedScrollPosition>, ArrayList<String>> =
    Saver(
        save = { map ->
            val encoded = ArrayList<String>(map.size)
            for ((sortName, pos) in map) {
                encoded.add("$sortName:${pos.firstVisibleItemIndex}:${pos.firstVisibleItemScrollOffset}")
            }
            encoded
        },
        restore = { encoded ->
            val restored = LinkedHashMap<String, FeedScrollPosition>(encoded.size)
            for (entry in encoded) {
                val parts = entry.split(':')
                if (parts.size == 3) {
                    val idx = parts[1].toIntOrNull() ?: 0
                    val off = parts[2].toIntOrNull() ?: 0
                    restored[parts[0]] = FeedScrollPosition(idx, off)
                }
            }
            restored
        },
    )

@Composable
fun rememberDiscoveryFeedScrollStateHolder(
    discoveryRepository: DiscoveryRepository,
): DiscoveryFeedScrollStateHolder {
    val saveablePositions = rememberSaveable(saver = SaveableFeedScrollMapSaver) {
        LinkedHashMap()
    }
    return remember(saveablePositions, discoveryRepository) {
        DiscoveryFeedScrollStateHolder(
            saveablePositions = saveablePositions,
            discoveryRepository = discoveryRepository,
        )
    }
}

/**
 * Policy-compliant, zero-placeholder interleaving cadence for in-feed Discovery (`最新` / `排行`)
 * inline adaptive banners:
 * - Page 1 (novels `#1..#10`) contains **zero** ad slots so cold-start preloading prioritizes
 *   `NovelDetailScreen` and initial browsing stays uncluttered.
 * - On pagination (Page 2+, starting at `#11`, `#21`, `#31`, ...), an inline banner is inserted
 *   as the **first item of the next page** if and only if a preloaded ad is already cached in
 *   memory (latched per page boundary so slots never pop in retroactively or render blank cards).
 */
object DiscoveryFeedAdCadence {
    const val ITEMS_PER_PAGE = 10

    const val PREFETCH_LOOKAHEAD_ITEMS = 4
}

sealed interface DiscoveryFeedEntry {
    data class Novel(
        val novelIndex: Int,
        val item: DiscoveryNovelCardItem,
    ) : DiscoveryFeedEntry

    data class InlineAd(
        val slotIndex: Int,
    ) : DiscoveryFeedEntry
}

fun buildDiscoveryFeedEntries(
    items: List<DiscoveryNovelCardItem>,
    adsEnabled: Boolean,
    enabledAdSlotIndices: Set<Int>? = null,
): List<DiscoveryFeedEntry> {
    if (items.isEmpty()) return emptyList()
    if (!adsEnabled) {
        return items.mapIndexed { index, item ->
            DiscoveryFeedEntry.Novel(novelIndex = index, item = item)
        }
    }
    return buildList(items.size + (items.size / DiscoveryFeedAdCadence.ITEMS_PER_PAGE)) {
        items.forEachIndexed { novelIndex, item ->
            if (novelIndex > 0 && novelIndex % DiscoveryFeedAdCadence.ITEMS_PER_PAGE == 0) {
                val slotIndex = (novelIndex / DiscoveryFeedAdCadence.ITEMS_PER_PAGE) - 1
                if (enabledAdSlotIndices == null || slotIndex in enabledAdSlotIndices) {
                    add(DiscoveryFeedEntry.InlineAd(slotIndex = slotIndex))
                }
            }
            add(DiscoveryFeedEntry.Novel(novelIndex = novelIndex, item = item))
        }
    }
}

/**
 * Returns slot indices for upcoming [DiscoveryFeedEntry.InlineAd] entries located within
 * `[lastVisibleIndex, lastVisibleIndex + lookaheadItems]`, enabling Google-recommended detached
 * lookahead prefetching without wasting requests at scroll top.
 */
fun findUpcomingInlineAdSlotsToPrefetch(
    entries: List<DiscoveryFeedEntry>,
    lastVisibleIndex: Int,
    lookaheadItems: Int = DiscoveryFeedAdCadence.PREFETCH_LOOKAHEAD_ITEMS,
): List<Int> {
    if (entries.isEmpty() || lastVisibleIndex < 0) return emptyList()
    val startIndex = lastVisibleIndex.coerceAtLeast(0)
    if (startIndex > entries.lastIndex) return emptyList()
    val endIndex = (startIndex + lookaheadItems.coerceAtLeast(0)).coerceAtMost(entries.lastIndex)
    val result = mutableListOf<Int>()
    for (idx in startIndex..endIndex) {
        val entry = entries[idx]
        if (entry is DiscoveryFeedEntry.InlineAd) {
            result.add(entry.slotIndex)
        }
    }
    return result
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PaginatedNovelFeedContent(
    feedState: PaginatedNovelFeedState,
    showRankNumbers: Boolean,
    onRefresh: () -> Unit,
    onLoadNextPage: () -> Unit,
    onNovelClicked: (DiscoveryNovelCardItem) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState = rememberLazyListState(),
    onScrollPositionChanged: ((firstVisibleItemIndex: Int, firstVisibleItemScrollOffset: Int) -> Unit)? = null,
    sharedTransitionOrigin: String = Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN,
    isNetworkAvailableForAdOverride: Boolean? = null,
    isGmsAvailableForAdOverride: Boolean? = null,
    canRequestAdsForAdOverride: Boolean? = null,
    enableLiveAdViewInflation: Boolean = true,
    hasPreloadedFeedAdOverride: Boolean? = null,
) {
    val isEink = LocalEinkMode.current
    val canRequestAds = canRequestAdsForAdOverride ?: org.mewx.wenku8.ui.detail.LocalAdConsentCanRequestAds.current
    val hasLoadedItems = feedState.items.isNotEmpty()
    val hasAdTestOverrides = isNetworkAvailableForAdOverride != null ||
        isGmsAvailableForAdOverride != null ||
        canRequestAdsForAdOverride != null ||
        !enableLiveAdViewInflation
    val sharedPool = if (hasAdTestOverrides) null else LocalDiscoveryFeedInlineAdPool.current
    val inlineAdPool = sharedPool ?: rememberDiscoveryFeedInlineAdPool(
        poolScopeKey = feedState.sortedBy,
        isNetworkAvailableOverride = isNetworkAvailableForAdOverride,
        isGmsAvailableOverride = isGmsAvailableForAdOverride,
        canRequestAdsOverride = canRequestAdsForAdOverride,
        enableLiveAdViewInflation = enableLiveAdViewInflation,
    )

    LaunchedEffect(feedState.sortedBy, listState, hasLoadedItems, onScrollPositionChanged) {
        val callback = onScrollPositionChanged ?: return@LaunchedEffect
        if (!hasLoadedItems) return@LaunchedEffect
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                callback(index, offset)
            }
    }

    PullToRefreshBox(
        isRefreshing = feedState.isRefreshing,
        onRefresh = {
            inlineAdPool.clearSort(feedState.sortedBy.name)
            onRefresh()
        },
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            (feedState.isInitialLoading || feedState.loadedPage == 0) &&
                feedState.errorKind == null &&
                feedState.items.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp, vertical = 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    if (isEink) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.65f))
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(0.65f))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.feed_loading_initial),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            feedState.errorKind != null && feedState.items.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    contentAlignment = Alignment.TopCenter,
                ) {
                    DiscoveryFeedErrorCard(
                        errorKind = feedState.errorKind,
                        errorServerCode = feedState.errorServerCode,
                        onRetry = onRefresh,
                        onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                    )
                }
            }

            else -> {
                // Keying LazyColumn by `feedState.sortedBy` + scoping item keys with `sortedBy.name`
                // guarantees Compose never anchors scroll position to an overlapping novel `aid`
                // when switching between 周榜 / 日榜 / 月榜 / 总榜.
                key(feedState.sortedBy) {
                    val context = LocalContext.current
                    val hostActivity = remember(context) { context.findHostActivity() }

                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val adSlotWidthDp = (maxWidth - 32.dp).value.toInt().coerceAtLeast(160)
                        val paginationBoundaryCount =
                            ((feedState.items.size - 1) / DiscoveryFeedAdCadence.ITEMS_PER_PAGE)
                                .coerceAtLeast(0)
                        val enabledAdSlotIndices = remember(
                            feedState.sortedBy,
                            paginationBoundaryCount,
                            inlineAdPool,
                            canRequestAds,
                            adSlotWidthDp,
                            hasPreloadedFeedAdOverride,
                        ) {
                            if (!inlineAdPool.isAdSlotEligible || paginationBoundaryCount <= 0) {
                                emptySet()
                            } else {
                                buildSet {
                                    for (slotIndex in 0 until paginationBoundaryCount) {
                                        val slotKey = "${feedState.sortedBy.name}_slot_$slotIndex"
                                        val showSlot = inlineAdPool.resolveOrLatchPaginationAd(
                                            slotKey = slotKey,
                                            hostContext = hostActivity ?: context,
                                            availableWidthDp = adSlotWidthDp,
                                            canRequestAds = canRequestAds,
                                            staticPreviewReady = hasPreloadedFeedAdOverride ?: true,
                                        )
                                        if (showSlot) {
                                            add(slotIndex)
                                        }
                                    }
                                }
                            }
                        }
                        val feedEntries = remember(
                            feedState.items,
                            inlineAdPool.isAdSlotEligible,
                            enabledAdSlotIndices,
                        ) {
                            buildDiscoveryFeedEntries(
                                items = feedState.items,
                                adsEnabled = inlineAdPool.isAdSlotEligible,
                                enabledAdSlotIndices = enabledAdSlotIndices,
                            )
                        }

                        val shouldTriggerLoadMore by remember(
                            listState,
                            feedEntries.size,
                            feedState.canLoadMorePages,
                        ) {
                            derivedStateOf {
                                if (!feedState.canLoadMorePages || feedEntries.isEmpty()) {
                                    false
                                } else {
                                    val lastVisibleIndex =
                                        listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                                    lastVisibleIndex >= (feedEntries.size - 3).coerceAtLeast(0)
                                }
                            }
                        }

                        LaunchedEffect(feedState.sortedBy, shouldTriggerLoadMore) {
                            if (shouldTriggerLoadMore) {
                                onLoadNextPage()
                            }
                        }

                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag(DiscoveryTestTags.FEED_LIST),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(
                                items = feedEntries,
                                key = { entry ->
                                    when (entry) {
                                        is DiscoveryFeedEntry.Novel -> "${feedState.sortedBy.name}_${entry.item.aid}"
                                        is DiscoveryFeedEntry.InlineAd -> "${feedState.sortedBy.name}_inline_ad_${entry.slotIndex}"
                                    }
                                },
                                contentType = { entry ->
                                    when (entry) {
                                        is DiscoveryFeedEntry.Novel -> "discovery_novel"
                                        is DiscoveryFeedEntry.InlineAd -> "discovery_inline_ad"
                                    }
                                },
                            ) { entry ->
                                when (entry) {
                                    is DiscoveryFeedEntry.Novel -> {
                                        DiscoveryNovelCard(
                                            item = entry.item,
                                            rankNumber = if (showRankNumbers) entry.novelIndex + 1 else null,
                                            onClick = { onNovelClicked(entry.item) },
                                            sharedTransitionOrigin = sharedTransitionOrigin,
                                        )
                                    }

                                    is DiscoveryFeedEntry.InlineAd -> {
                                        val slotKey = "${feedState.sortedBy.name}_slot_${entry.slotIndex}"
                                        val slotHolder = inlineAdPool.getOrNull(slotKey)
                                        if (slotHolder != null) {
                                            ServerSupportBannerAd(
                                                holder = slotHolder,
                                                cardTestTag = DiscoveryTestTags.FEED_AD_CARD_PREFIX + entry.slotIndex,
                                                slotTestTag = DiscoveryTestTags.FEED_AD_SLOT_PREFIX + entry.slotIndex,
                                                useElevatedTonalContainer = true,
                                                canRequestAdsOverride = canRequestAdsForAdOverride,
                                                modifier = Modifier.padding(vertical = 4.dp),
                                            )
                                        }
                                    }
                                }
                            }

                            if (feedState.isAppendingNextPage) {
                                item(key = "${feedState.sortedBy.name}_pagination_loading_footer") {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 14.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                    ) {
                                        if (isEink) {
                                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.5f))
                                        } else {
                                            LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(0.5f))
                                        }
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = stringResource(
                                                R.string.feed_loading_more,
                                                feedState.loadedPage + 1,
                                                feedState.totalPages,
                                            ),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            } else if (feedState.errorKind != null && feedState.items.isNotEmpty()) {
                                item(key = "${feedState.sortedBy.name}_pagination_retry_footer") {
                                    Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { onLoadNextPage() }
                                            .einkHairlineBorder(MaterialTheme.shapes.medium),
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Refresh,
                                                contentDescription = null,
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = stringResource(R.string.feed_append_failed_retry),
                                                style = MaterialTheme.typography.labelLarge,
                                            )
                                        }
                                    }
                                }
                            } else if (feedState.loadedPage >= feedState.totalPages && feedState.items.isNotEmpty()) {
                                item(key = "${feedState.sortedBy.name}_pagination_end_footer") {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 12.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = stringResource(R.string.feed_end_reached, feedState.items.size),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
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
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscoveryFeedErrorCard(
    errorKind: DiscoveryFeedErrorKind,
    errorServerCode: Int?,
    onRetry: () -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val messageText = when (errorKind) {
        DiscoveryFeedErrorKind.API_NOT_CONFIGURED ->
            stringResource(R.string.feed_error_api_unconfigured)
        DiscoveryFeedErrorKind.NETWORK_UNREACHABLE ->
            stringResource(R.string.feed_error_network)
        DiscoveryFeedErrorKind.SERVER_ERROR ->
            stringResource(R.string.feed_error_server_code, errorServerCode ?: 0)
        DiscoveryFeedErrorKind.PARSE_FAILURE ->
            stringResource(R.string.feed_error_parse)
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag(DiscoveryTestTags.FEED_ERROR_CARD)
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = messageText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onRetry,
                    modifier = Modifier.testTag(DiscoveryTestTags.FEED_RETRY_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.action_retry))
                }

                AssistChip(
                    onClick = onOpenNetworkDiagnostics,
                    label = { Text(text = stringResource(R.string.action_network_diagnostics)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.NetworkCheck,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.testTag(DiscoveryTestTags.FEED_DIAGNOSTICS_CHIP),
                )
            }
        }
    }
}
