package org.mewx.wenku8.v2.ui.discovery

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkAdded
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.mewx.wenku8.v2.R
import org.mewx.wenku8.v2.data.media.WenkuImageRequests
import org.mewx.wenku8.v2.domain.model.DiscoveryFeedErrorKind
import org.mewx.wenku8.v2.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.v2.domain.model.PaginatedNovelFeedState
import org.mewx.wenku8.v2.ui.theme.LocalEinkMode
import org.mewx.wenku8.v2.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.v2.ui.theme.einkHairlineBorder
import org.mewx.wenku8.v2.ui.theme.wenku8SharedNovelCover
import org.mewx.wenku8.v2.ui.theme.wenku8SharedNovelTitle
import org.mewx.wenku8.v2.ui.theme.wenku8SharedSearchBarBounds

object DiscoveryTestTags {
    const val PERSISTENT_SEARCH_PILL = "discovery_persistent_search_pill"
    const val LATEST_TAB_PREFIX = "latest_tab_"
    const val RANKING_METRIC_PREFIX = "ranking_metric_"
    const val RANKING_PERIOD_PREFIX = "ranking_period_"
    const val NOVEL_CARD_PREFIX = "novel_card_"
    const val FEED_ERROR_CARD = "discovery_feed_error_card"
    const val FEED_RETRY_BUTTON = "discovery_feed_retry_button"
    const val FEED_DIAGNOSTICS_CHIP = "discovery_feed_diagnostics_chip"
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // 2:3 Light Novel Book Cover with Offline-First Coil 3 + Shared-Element Morph
            Box(
                modifier = Modifier
                    .size(width = 76.dp, height = 110.dp)
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (item.author.isNotBlank()) {
                        Text(
                            text = item.author,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (item.bookStatus.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            shape = MaterialTheme.shapes.extraSmall,
                            modifier = Modifier.einkHairlineBorder(MaterialTheme.shapes.extraSmall),
                        ) {
                            Text(
                                text = item.bookStatus,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
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
                                )
                            }
                        }
                    }
                }

                if (item.tags.isNotEmpty() || item.lastUpdate.isNotBlank()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
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
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (item.lastUpdate.isNotBlank()) {
                            Text(
                                text = item.lastUpdate,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    sharedTransitionOrigin: String = Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN,
) {
    val isEink = LocalEinkMode.current
    val listState = rememberLazyListState()

    val shouldTriggerLoadMore by remember(feedState.items.size, feedState.canLoadMorePages) {
        derivedStateOf {
            if (!feedState.canLoadMorePages || feedState.items.isEmpty()) {
                false
            } else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleIndex >= (feedState.items.size - 3).coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(shouldTriggerLoadMore) {
        if (shouldTriggerLoadMore) {
            onLoadNextPage()
        }
    }

    PullToRefreshBox(
        isRefreshing = feedState.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        when {
            feedState.isInitialLoading && feedState.items.isEmpty() -> {
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
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    itemsIndexed(
                        items = feedState.items,
                        key = { _, item -> item.aid },
                    ) { index, item ->
                        DiscoveryNovelCard(
                            item = item,
                            rankNumber = if (showRankNumbers) index + 1 else null,
                            onClick = { onNovelClicked(item) },
                            sharedTransitionOrigin = sharedTransitionOrigin,
                        )
                    }

                    if (feedState.isAppendingNextPage) {
                        item(key = "pagination_loading_footer") {
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
                        item(key = "pagination_retry_footer") {
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
                        item(key = "pagination_end_footer") {
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
