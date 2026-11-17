package org.mewx.wenku8.v2.ui.discovery

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.R
import org.mewx.wenku8.v2.data.repository.DiscoveryRepository
import org.mewx.wenku8.v2.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.v2.domain.model.LatestFeedCategory
import org.mewx.wenku8.v2.ui.theme.LocalEinkMode

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
    var selectedCategory by rememberSaveable {
        mutableStateOf(discoveryRepository.activeLatestCategory)
    }
    LaunchedEffect(selectedCategory) {
        discoveryRepository.activeLatestCategory = selectedCategory
    }

    val activeSortedBy = selectedCategory.sortedBy
    val feedState by key(activeSortedBy) {
        discoveryRepository.observeFeedState(activeSortedBy).collectAsStateWithLifecycle()
    }
    val feedScrollHolder = rememberDiscoveryFeedScrollStateHolder(discoveryRepository)
    val activeListState = feedScrollHolder.listStateFor(feedState.sortedBy)

    val coroutineScope = rememberCoroutineScope()

    var handledReselectTrigger by remember { mutableIntStateOf(reselectScrollToTopTrigger) }
    LaunchedEffect(reselectScrollToTopTrigger) {
        if (reselectScrollToTopTrigger != handledReselectTrigger) {
            handledReselectTrigger = reselectScrollToTopTrigger
            feedScrollHolder.scrollToTop(activeSortedBy, isEink)
        }
    }

    LaunchedEffect(selectedCategory, appLanguage) {
        discoveryRepository.loadInitialOrRefreshFeed(
            sortedBy = activeSortedBy,
            appLanguage = appLanguage,
            forceRefresh = false,
        )
    }

    Column(modifier = modifier.fillMaxSize()) {
        PersistentSearchEntryPill(
            onOpenSearch = onOpenSearch,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )

        PrimaryTabRow(
            selectedTabIndex = selectedCategory.ordinal,
            modifier = Modifier.fillMaxWidth(),
        ) {
            LatestFeedCategory.entries.forEach { category ->
                Tab(
                    selected = category == selectedCategory,
                    onClick = {
                        if (category == selectedCategory) {
                            val targetSort = activeSortedBy
                            coroutineScope.launch {
                                feedScrollHolder.scrollToTop(targetSort, isEink)
                            }
                        } else {
                            selectedCategory = category
                            discoveryRepository.activeLatestCategory = category
                        }
                    },
                    text = { Text(text = stringResource(category.labelResId())) },
                    modifier = Modifier.testTag(DiscoveryTestTags.LATEST_TAB_PREFIX + category.name),
                )
            }
        }

        val currentFeedSort = feedState.sortedBy
        PaginatedNovelFeedContent(
            feedState = feedState,
            showRankNumbers = false,
            listState = activeListState,
            onScrollPositionChanged = { index, offset ->
                feedScrollHolder.recordScrollPosition(currentFeedSort, index, offset)
            },
            onRefresh = {
                val targetSort = activeSortedBy
                coroutineScope.launch {
                    discoveryRepository.loadInitialOrRefreshFeed(
                        sortedBy = targetSort,
                        appLanguage = appLanguage,
                        forceRefresh = true,
                    )
                    feedScrollHolder.scrollToTop(targetSort, isEink)
                }
            },
            onLoadNextPage = {
                val targetSort = activeSortedBy
                coroutineScope.launch {
                    discoveryRepository.loadNextFeedPage(
                        sortedBy = targetSort,
                        appLanguage = appLanguage,
                    )
                }
            },
            onNovelClicked = onNovelClicked,
            onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
            modifier = Modifier.weight(1f),
        )
    }
}

@StringRes
private fun LatestFeedCategory.labelResId(): Int = when (this) {
    LatestFeedCategory.RECENT_UPDATES -> R.string.latest_tab_recent_updates
    LatestFeedCategory.NEW_ARRIVALS -> R.string.latest_tab_new_arrivals
    LatestFeedCategory.COMPLETED_GEMS -> R.string.latest_tab_completed_gems
}
