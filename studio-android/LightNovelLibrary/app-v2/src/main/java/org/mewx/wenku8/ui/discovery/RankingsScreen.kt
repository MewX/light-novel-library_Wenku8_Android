package org.mewx.wenku8.ui.discovery

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.R
import org.mewx.wenku8.data.repository.DiscoveryRepository
import org.mewx.wenku8.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.domain.model.RankingMetric
import org.mewx.wenku8.domain.model.RankingPeriod
import org.mewx.wenku8.domain.model.resolveRankingSortedBy
import org.mewx.wenku8.ui.theme.LocalEinkMode

/**
 * `Tab 3 · 排行` (`RankingsScreen`) replacing `v1.31`'s 12-tab horizontal strip with a 2D
 * **Metric × Period** M3e selector:
 * - Metric selector: `点击榜` · `推荐榜` · `总收藏` · `字数榜`
 * - Period filter chips (when metric supports time windows): `周榜` (default) · `日榜` · `月榜` · `总榜`
 * - Independently preserves scroll progress per `(Metric × Period)` ranking without cross-anchoring
 *   overlapping novel `aid`s, and scrolls back to top when re-tapping the active ranking button.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RankingsScreen(
    discoveryRepository: DiscoveryRepository,
    appLanguage: Wenku8API.AppLanguage,
    onOpenSearch: () -> Unit,
    onNovelClicked: (DiscoveryNovelCardItem) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    reselectScrollToTopTrigger: Int = 0,
) {
    val isEink = LocalEinkMode.current
    var selectedMetric by rememberSaveable {
        mutableStateOf(discoveryRepository.activeRankingMetric)
    }
    var selectedPeriod by rememberSaveable {
        mutableStateOf(discoveryRepository.activeRankingPeriod)
    }
    LaunchedEffect(selectedMetric, selectedPeriod) {
        discoveryRepository.activeRankingMetric = selectedMetric
        discoveryRepository.activeRankingPeriod = selectedPeriod
    }

    val activeSortedBy = resolveRankingSortedBy(selectedMetric, selectedPeriod)
    // Keying StateFlow collection on `activeSortedBy` guarantees `feedState` switches synchronously
    // on the exact same frame as `activeListState`, preventing a 1-frame `produceState` lag from
    // clamping or cross-coupling two different rankings' scroll states.
    val feedState by key(activeSortedBy) {
        discoveryRepository.observeFeedState(activeSortedBy).collectAsStateWithLifecycle()
    }
    val feedScrollHolder = rememberDiscoveryFeedScrollStateHolder(discoveryRepository)
    val activeListState = feedScrollHolder.listStateFor(feedState.sortedBy)

    val coroutineScope = rememberCoroutineScope()

    val scrollActiveRankingToTop = {
        val targetSort = activeSortedBy
        coroutineScope.launch {
            feedScrollHolder.scrollToTop(targetSort, isEink)
        }
    }

    var handledReselectTrigger by remember { mutableIntStateOf(reselectScrollToTopTrigger) }
    LaunchedEffect(reselectScrollToTopTrigger) {
        if (reselectScrollToTopTrigger != handledReselectTrigger) {
            handledReselectTrigger = reselectScrollToTopTrigger
            feedScrollHolder.scrollToTop(activeSortedBy, isEink)
        }
    }

    LaunchedEffect(activeSortedBy, appLanguage) {
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

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            val metrics = RankingMetric.entries
            metrics.forEachIndexed { index, metric ->
                SegmentedButton(
                    selected = metric == selectedMetric,
                    onClick = {
                        if (metric == selectedMetric) {
                            scrollActiveRankingToTop()
                        } else {
                            selectedMetric = metric
                            discoveryRepository.activeRankingMetric = metric
                        }
                    },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = metrics.size),
                    label = {
                        Text(
                            text = stringResource(metric.labelResId()),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    modifier = Modifier.testTag(DiscoveryTestTags.RANKING_METRIC_PREFIX + metric.name),
                )
            }
        }

        if (selectedMetric.supportsTimePeriod) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RankingPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = period == selectedPeriod,
                        onClick = {
                            if (period == selectedPeriod) {
                                scrollActiveRankingToTop()
                            } else {
                                selectedPeriod = period
                                discoveryRepository.activeRankingPeriod = period
                            }
                        },
                        label = { Text(text = stringResource(period.labelResId())) },
                        modifier = Modifier.testTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + period.name),
                    )
                }
            }
        }

        val currentFeedSort = feedState.sortedBy
        PaginatedNovelFeedContent(
            feedState = feedState,
            showRankNumbers = true,
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
private fun RankingMetric.labelResId(): Int = when (this) {
    RankingMetric.VISITS -> R.string.ranking_metric_visits
    RankingMetric.RECOMMENDATIONS -> R.string.ranking_metric_recommendations
    RankingMetric.FAVORITES -> R.string.ranking_metric_favorites
    RankingMetric.WORD_COUNT -> R.string.ranking_metric_word_count
}

@StringRes
private fun RankingPeriod.labelResId(): Int = when (this) {
    RankingPeriod.WEEK -> R.string.ranking_period_week
    RankingPeriod.DAY -> R.string.ranking_period_day
    RankingPeriod.MONTH -> R.string.ranking_period_month
    RankingPeriod.ALL_TIME -> R.string.ranking_period_all_time
}
