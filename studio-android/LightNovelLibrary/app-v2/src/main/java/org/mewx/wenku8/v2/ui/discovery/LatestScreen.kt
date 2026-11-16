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
import androidx.compose.runtime.mutableStateOf
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
) {
    var selectedCategory by rememberSaveable {
        mutableStateOf(LatestFeedCategory.RECENT_UPDATES)
    }
    val coroutineScope = rememberCoroutineScope()
    val feedState by discoveryRepository.observeFeedState(selectedCategory.sortedBy)
        .collectAsStateWithLifecycle()

    LaunchedEffect(selectedCategory, appLanguage) {
        discoveryRepository.loadInitialOrRefreshFeed(
            sortedBy = selectedCategory.sortedBy,
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
                    onClick = { selectedCategory = category },
                    text = { Text(text = stringResource(category.labelResId())) },
                    modifier = Modifier.testTag(DiscoveryTestTags.LATEST_TAB_PREFIX + category.name),
                )
            }
        }

        PaginatedNovelFeedContent(
            feedState = feedState,
            showRankNumbers = false,
            onRefresh = {
                coroutineScope.launch {
                    discoveryRepository.loadInitialOrRefreshFeed(
                        sortedBy = selectedCategory.sortedBy,
                        appLanguage = appLanguage,
                        forceRefresh = true,
                    )
                }
            },
            onLoadNextPage = {
                coroutineScope.launch {
                    discoveryRepository.loadNextFeedPage(
                        sortedBy = selectedCategory.sortedBy,
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
