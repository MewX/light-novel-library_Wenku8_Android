package org.mewx.wenku8.v2.ui.discovery

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
import androidx.compose.runtime.mutableStateOf
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
import org.mewx.wenku8.v2.R
import org.mewx.wenku8.v2.data.repository.DiscoveryRepository
import org.mewx.wenku8.v2.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.v2.domain.model.RankingMetric
import org.mewx.wenku8.v2.domain.model.RankingPeriod
import org.mewx.wenku8.v2.domain.model.resolveRankingSortedBy

/**
 * `Tab 3 · 排行` (`RankingsScreen`) replacing `v1.31`'s 12-tab horizontal strip with a 2D
 * **Metric × Period** M3e selector:
 * - Metric selector: `点击榜` · `推荐榜` · `总收藏` · `字数榜`
 * - Period filter chips (when metric supports time windows): `周榜` (default) · `日榜` · `月榜` · `总榜`
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
) {
    var selectedMetric by rememberSaveable { mutableStateOf(RankingMetric.VISITS) }
    var selectedPeriod by rememberSaveable { mutableStateOf(RankingPeriod.WEEK) }
    val activeSortedBy = resolveRankingSortedBy(selectedMetric, selectedPeriod)

    val coroutineScope = rememberCoroutineScope()
    val feedState by discoveryRepository.observeFeedState(activeSortedBy)
        .collectAsStateWithLifecycle()

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
                    onClick = { selectedMetric = metric },
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
                        onClick = { selectedPeriod = period },
                        label = { Text(text = stringResource(period.labelResId())) },
                        modifier = Modifier.testTag(DiscoveryTestTags.RANKING_PERIOD_PREFIX + period.name),
                    )
                }
            }
        }

        PaginatedNovelFeedContent(
            feedState = feedState,
            showRankNumbers = true,
            onRefresh = {
                coroutineScope.launch {
                    discoveryRepository.loadInitialOrRefreshFeed(
                        sortedBy = activeSortedBy,
                        appLanguage = appLanguage,
                        forceRefresh = true,
                    )
                }
            },
            onLoadNextPage = {
                coroutineScope.launch {
                    discoveryRepository.loadNextFeedPage(
                        sortedBy = activeSortedBy,
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
