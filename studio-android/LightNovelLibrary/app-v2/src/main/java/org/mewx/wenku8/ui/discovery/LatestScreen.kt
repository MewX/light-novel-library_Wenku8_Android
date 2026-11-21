package org.mewx.wenku8.ui.discovery

import androidx.annotation.StringRes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.R
import org.mewx.wenku8.data.repository.DiscoveryRepository
import org.mewx.wenku8.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.domain.model.LatestFeedCategory
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.Wenku8SharedElementKeys

/**
 * `Tab 2 · 最新` (`LatestScreen`) hosting Wenku8's three freshness feeds:
 * - `最近更新` (`lastUpdate`, default cold-start tab)
 * - `最新入库` (`postDate`)
 * - `完结精品` (`fullFlag`)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LatestScreen(
    discoveryRepository: DiscoveryRepository,
    appLanguage: Wenku8API.AppLanguage,
    onOpenSearch: () -> Unit,
    onNovelClicked: (DiscoveryNovelCardItem) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    reselectScrollToTopTrigger: Int = 0,
) {
    val isEink = LocalEinkMode.current
    val categories = LatestFeedCategory.entries
    val pagerState = rememberPagerState(
        initialPage = discoveryRepository.activeLatestCategory.ordinal.coerceIn(0, categories.lastIndex),
        pageCount = { categories.size },
    )
    val settledCategory = categories[pagerState.settledPage]
    LaunchedEffect(settledCategory) {
        discoveryRepository.activeLatestCategory = settledCategory
    }

    val feedScrollHolder = rememberDiscoveryFeedScrollStateHolder(discoveryRepository)
    val coroutineScope = rememberCoroutineScope()

    var handledReselectTrigger by remember { mutableIntStateOf(reselectScrollToTopTrigger) }
    LaunchedEffect(reselectScrollToTopTrigger) {
        if (reselectScrollToTopTrigger != handledReselectTrigger) {
            handledReselectTrigger = reselectScrollToTopTrigger
            feedScrollHolder.scrollToTop(categories[pagerState.currentPage].sortedBy, isEink)
        }
    }

    LaunchedEffect(settledCategory, appLanguage) {
        discoveryRepository.loadInitialOrRefreshFeed(
            sortedBy = settledCategory.sortedBy,
            appLanguage = appLanguage,
            forceRefresh = false,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        PersistentSearchEntryPill(
            onOpenSearch = onOpenSearch,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        val fontScale = LocalDensity.current.fontScale
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val useScrollableTabs = maxWidth < 360.dp || fontScale > 1.15f
            val selectedTabIndex = pagerState.currentPage
            val tabContent: @Composable () -> Unit = {
                categories.forEachIndexed { index, category ->
                    Tab(
                        selected = index == selectedTabIndex,
                        onClick = {
                            if (index == selectedTabIndex) {
                                val targetSort = category.sortedBy
                                coroutineScope.launch {
                                    feedScrollHolder.scrollToTop(targetSort, isEink)
                                }
                            } else {
                                discoveryRepository.activeLatestCategory = category
                                coroutineScope.launch {
                                    discoveryRepository.loadInitialOrRefreshFeed(
                                        sortedBy = category.sortedBy,
                                        appLanguage = appLanguage,
                                        forceRefresh = false,
                                    )
                                }
                                coroutineScope.launch {
                                    if (isEink) {
                                        pagerState.scrollToPage(index)
                                    } else {
                                        pagerState.animateScrollToPage(index)
                                    }
                                }
                            }
                        },
                        text = {
                            Text(
                                text = stringResource(category.labelResId()),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        modifier = Modifier.testTag(DiscoveryTestTags.LATEST_TAB_PREFIX + category.name),
                    )
                }
            }
            if (useScrollableTabs) {
                PrimaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    edgePadding = 16.dp,
                    modifier = Modifier.fillMaxWidth(),
                    tabs = tabContent,
                )
            } else {
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    modifier = Modifier.fillMaxWidth(),
                    tabs = tabContent,
                )
            }
        }

        val pagerFlingBehavior = if (isEink) {
            PagerDefaults.flingBehavior(
                state = pagerState,
                snapAnimationSpec = tween(durationMillis = 0),
            )
        } else {
            PagerDefaults.flingBehavior(state = pagerState)
        }

        HorizontalPager(
            state = pagerState,
            flingBehavior = pagerFlingBehavior,
            key = { pageIndex -> categories[pageIndex].name },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) { pageIndex ->
            val pageCategory = categories[pageIndex]
            val pageSortedBy = pageCategory.sortedBy
            val pageFeedState by discoveryRepository
                .observeFeedState(pageSortedBy)
                .collectAsStateWithLifecycle()
            val pageListState = feedScrollHolder.listStateFor(pageSortedBy)
            val sharedOrigin = if (pageIndex == pagerState.currentPage) {
                Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN
            } else {
                "${Wenku8SharedElementKeys.NOVEL_DEFAULT_ORIGIN}_${pageCategory.name}"
            }

            PaginatedNovelFeedContent(
                feedState = pageFeedState,
                showRankNumbers = false,
                listState = pageListState,
                onScrollPositionChanged = { index, offset ->
                    feedScrollHolder.recordScrollPosition(pageSortedBy, index, offset)
                },
                onRefresh = {
                    coroutineScope.launch {
                        discoveryRepository.loadInitialOrRefreshFeed(
                            sortedBy = pageSortedBy,
                            appLanguage = appLanguage,
                            forceRefresh = true,
                        )
                        feedScrollHolder.scrollToTop(pageSortedBy, isEink)
                    }
                },
                onLoadNextPage = {
                    coroutineScope.launch {
                        discoveryRepository.loadNextFeedPage(
                            sortedBy = pageSortedBy,
                            appLanguage = appLanguage,
                        )
                    }
                },
                onNovelClicked = onNovelClicked,
                onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                sharedTransitionOrigin = sharedOrigin,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@StringRes
private fun LatestFeedCategory.labelResId(): Int = when (this) {
    LatestFeedCategory.RECENT_UPDATES -> R.string.latest_tab_recent_updates
    LatestFeedCategory.NEW_ARRIVALS -> R.string.latest_tab_new_arrivals
    LatestFeedCategory.COMPLETED_GEMS -> R.string.latest_tab_completed_gems
}
