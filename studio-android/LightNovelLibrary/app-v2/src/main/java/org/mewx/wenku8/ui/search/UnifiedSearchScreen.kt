package org.mewx.wenku8.ui.search

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.R
import org.mewx.wenku8.data.repository.DiscoveryRepository
import org.mewx.wenku8.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.domain.model.OnlineSearchExecutionState
import org.mewx.wenku8.domain.model.UnifiedSearchMode
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.discovery.DiscoveryFeedErrorCard
import org.mewx.wenku8.ui.discovery.DiscoveryNovelCard
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.Wenku8SharedElementKeys
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import org.mewx.wenku8.ui.theme.wenku8SharedSearchBarBounds

object UnifiedSearchTestTags {
    const val SCREEN_ROOT = "unified_search_screen_root"
    const val BACK_BUTTON = "unified_search_back_button"
    const val QUERY_INPUT = "unified_search_query_input"
    const val CLEAR_QUERY_BUTTON = "unified_search_clear_query_button"
    const val SUBMIT_BUTTON = "unified_search_submit_button"
    const val MODE_BUTTON_PREFIX = "unified_search_mode_"
    const val LOCAL_SECTION_HEADER = "unified_search_local_section_header"
    const val HISTORY_SECTION_HEADER = "unified_search_history_section_header"
    const val CLEAR_HISTORY_BUTTON = "unified_search_clear_history_button"
    const val HISTORY_CHIP_PREFIX = "unified_search_history_chip_"
    const val DELETE_HISTORY_PREFIX = "unified_search_delete_history_"
    const val COOLDOWN_BANNER = "unified_search_cooldown_banner"
    const val CANCEL_COOLDOWN_BUTTON = "unified_search_cancel_cooldown_button"
    const val DIRECT_AID_ERROR = "unified_search_direct_aid_error"
    const val ONLINE_RESULTS_HEADER = "unified_search_online_results_header"
    const val ONLINE_EMPTY_TEXT = "unified_search_online_empty_text"
}

/**
 * Full-screen M3 Expressive Unified Search Studio (`05-screen-by-screen-ui-specifications.md` §Screen 4).
 *
 * Key UX guarantees:
 * 1. **Zero-Latency Local Room Shelf Filter (`< 1ms`)**: Every keystroke immediately queries the
 *    user's local bookshelf (`NOVELS` table) without network calls or cooldown delays.
 * 2. **Auto-Queued `5s` Server Cooldown**: When submitting a second online title/author search inside
 *    Wenku8's `5s` server rate-limit window, [DiscoveryRepository] queues the refined query with a
 *    live countdown banner and `[ 取消 ]` control rather than failing with an error toast.
 * 3. **Direct AID Lookup (`do=meta`)**: Bypasses the `5s` search rate limit completely.
 * 4. **20-Item Search History**: Preserves migrated `search_history.wk8` records with 1-tap re-run,
 *    per-item deletion, and full clear.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UnifiedSearchScreen(
    discoveryRepository: DiscoveryRepository,
    appLanguage: Wenku8API.AppLanguage,
    onNavigateBack: () -> Unit,
    onNovelClicked: (DiscoveryNovelCardItem) -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String = "",
    initialMode: UnifiedSearchMode = UnifiedSearchMode.NOVEL_TITLE,
    autoFocusInputOnEntry: Boolean = initialQuery.isBlank(),
    searchBarSharedOrigin: String = Wenku8SharedElementKeys.SEARCH_BAR_DEFAULT_ORIGIN,
    onNovelClickedWithOrigin: ((DiscoveryNovelCardItem, String) -> Unit)? = null,
) {
    BackHandler(onBack = onNavigateBack)

    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val isEink = LocalEinkMode.current

    val listState = rememberLazyListState()
    var queryText by rememberSaveable(initialQuery) { mutableStateOf(initialQuery) }
    var selectedMode by rememberSaveable(initialMode) { mutableStateOf(initialMode) }
    var hasDispatchedInitialQuery by rememberSaveable(initialQuery, initialMode) { mutableStateOf(false) }
    var hasRequestedInitialAutoFocus by rememberSaveable(initialQuery, initialMode) { mutableStateOf(false) }
    var showDirectAidValidationError by rememberSaveable { mutableStateOf(false) }
    var localShelfMatches by remember {
        mutableStateOf(discoveryRepository.latestLocalShelfMatchesFor(queryText))
    }

    val searchHistory by discoveryRepository
        .observeSearchHistory()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val onlineSearchState by discoveryRepository
        .onlineSearchState
        .collectAsStateWithLifecycle()

    // Instant <1ms local Room bookshelf filter updated on every keystroke.
    LaunchedEffect(queryText) {
        showDirectAidValidationError = false
        localShelfMatches = discoveryRepository.filterLocalLibraryInstant(queryText)
    }

    LaunchedEffect(autoFocusInputOnEntry) {
        if (autoFocusInputOnEntry && !hasRequestedInitialAutoFocus && queryText.isEmpty()) {
            hasRequestedInitialAutoFocus = true
            runCatching { focusRequester.requestFocus() }
        }
    }

    fun triggerOnlineSearch(targetQuery: String = queryText, mode: UnifiedSearchMode = selectedMode) {
        val trimmed = targetQuery.trim()
        if (trimmed.isEmpty()) return
        if (mode == UnifiedSearchMode.DIRECT_AID && DiscoveryRepository.parseDirectAidOrNull(trimmed) == null) {
            showDirectAidValidationError = true
            return
        }
        showDirectAidValidationError = false
        keyboardController?.hide()
        discoveryRepository.submitOnlineSearch(
            scope = coroutineScope,
            rawQuery = trimmed,
            mode = mode,
            appLanguage = appLanguage,
        )
    }

    LaunchedEffect(initialQuery, initialMode) {
        val trimmedInitial = initialQuery.trim()
        if (trimmedInitial.isNotEmpty() && !hasDispatchedInitialQuery) {
            hasDispatchedInitialQuery = true
            triggerOnlineSearch(targetQuery = trimmedInitial, mode = initialMode)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(UnifiedSearchTestTags.SCREEN_ROOT),
        contentWindowInsets = WindowInsets.safeDrawing,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            // Top Search Bar Header + Mode Selector
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val backLabel = stringResource(R.string.action_back)
                    TooltipIconButton(
                        tooltipText = backLabel,
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag(UnifiedSearchTestTags.BACK_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = backLabel,
                        )
                    }

                    val searchFieldShape = MaterialTheme.shapes.extraLarge
                    OutlinedTextField(
                        value = queryText,
                        onValueChange = { queryText = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search_input_placeholder),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Outlined.Search,
                                contentDescription = null,
                            )
                        },
                        trailingIcon = {
                            if (queryText.isNotEmpty()) {
                                val clearLabel = stringResource(R.string.action_clear)
                                TooltipIconButton(
                                    tooltipText = clearLabel,
                                    onClick = {
                                        queryText = ""
                                        showDirectAidValidationError = false
                                    },
                                    modifier = Modifier.testTag(UnifiedSearchTestTags.CLEAR_QUERY_BUTTON),
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Close,
                                        contentDescription = clearLabel,
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = searchFieldShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = { triggerOnlineSearch() },
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .wenku8SharedSearchBarBounds(
                                origin = searchBarSharedOrigin,
                                clipShape = searchFieldShape,
                            )
                            .focusRequester(focusRequester)
                            .testTag(UnifiedSearchTestTags.QUERY_INPUT),
                    )

                    Button(
                        onClick = { triggerOnlineSearch() },
                        enabled = queryText.isNotBlank(),
                        modifier = Modifier.testTag(UnifiedSearchTestTags.SUBMIT_BUTTON),
                    ) {
                        Text(
                            text = stringResource(R.string.search_action_submit),
                            maxLines = 1,
                        )
                    }
                }

                // Search Scope Connected Segmented Row: 按小说标题 · 按作者名称 · AID 编号直达
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    val modes = UnifiedSearchMode.entries
                    modes.forEachIndexed { index, mode ->
                        val labelText = when (mode) {
                            UnifiedSearchMode.NOVEL_TITLE -> stringResource(R.string.search_mode_title)
                            UnifiedSearchMode.AUTHOR_NAME -> stringResource(R.string.search_mode_author)
                            UnifiedSearchMode.DIRECT_AID -> stringResource(R.string.search_mode_direct_aid)
                        }
                        SegmentedButton(
                            selected = mode == selectedMode,
                            onClick = {
                                selectedMode = mode
                                showDirectAidValidationError = false
                            },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = modes.size,
                            ),
                            label = {
                                Text(
                                    text = labelText,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            modifier = Modifier.testTag(
                                UnifiedSearchTestTags.MODE_BUTTON_PREFIX + mode.name,
                            ),
                        )
                    }
                }

                if (showDirectAidValidationError) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = MaterialTheme.shapes.medium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(UnifiedSearchTestTags.DIRECT_AID_ERROR)
                            .einkHairlineBorder(MaterialTheme.shapes.medium),
                    ) {
                        Text(
                            text = stringResource(R.string.search_direct_aid_invalid),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        )
                    }
                }
            }

            HorizontalDivider()

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1. Auto-Queued 5s Cooldown Countdown Card (if active)
                if (onlineSearchState.isQueuedForCooldown) {
                    item(key = "online_search_cooldown_banner") {
                        CooldownQueuedCard(
                            state = onlineSearchState,
                            isEink = isEink,
                            onCancel = { discoveryRepository.cancelPendingOnlineSearch() },
                        )
                    }
                }

                // 2. Instant <1ms Local Room Bookshelf Matches
                if (queryText.isNotBlank() && localShelfMatches.isNotEmpty()) {
                    item(key = "local_shelf_section_header") {
                        Text(
                            text = stringResource(
                                R.string.search_local_shelf_section,
                                localShelfMatches.size,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.testTag(UnifiedSearchTestTags.LOCAL_SECTION_HEADER),
                        )
                    }
                    items(
                        items = localShelfMatches,
                        key = { "local_${it.aid}" },
                    ) { matchCard ->
                        val localOrigin = Wenku8SharedElementKeys.NOVEL_SEARCH_LOCAL_ORIGIN
                        DiscoveryNovelCard(
                            item = matchCard,
                            sharedTransitionOrigin = localOrigin,
                            onClick = {
                                onNovelClickedWithOrigin?.invoke(matchCard, localOrigin)
                                    ?: onNovelClicked(matchCard)
                            },
                        )
                    }
                }

                // 3. Recent Search History Chips (shown when input is blank or before submitting)
                if (searchHistory.isNotEmpty() &&
                    (queryText.isBlank() || (!onlineSearchState.isSearchingOnline && !onlineSearchState.hasCompletedSearch && !onlineSearchState.isQueuedForCooldown))
                ) {
                    item(key = "search_history_section") {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .einkHairlineBorder(MaterialTheme.shapes.large),
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Text(
                                            text = stringResource(R.string.search_history_section),
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.testTag(
                                                UnifiedSearchTestTags.HISTORY_SECTION_HEADER,
                                            ),
                                        )
                                    }

                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                discoveryRepository.clearAllSearchHistory()
                                            }
                                        },
                                        modifier = Modifier.testTag(
                                            UnifiedSearchTestTags.CLEAR_HISTORY_BUTTON,
                                        ),
                                    ) {
                                        Text(text = stringResource(R.string.action_clear))
                                    }
                                }

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    searchHistory.forEach { historyQuery ->
                                        InputChip(
                                            selected = false,
                                            onClick = {
                                                queryText = historyQuery
                                                val autoMode = if (
                                                    selectedMode == UnifiedSearchMode.DIRECT_AID &&
                                                    DiscoveryRepository.parseDirectAidOrNull(historyQuery) == null
                                                ) {
                                                    UnifiedSearchMode.NOVEL_TITLE.also { selectedMode = it }
                                                } else {
                                                    selectedMode
                                                }
                                                triggerOnlineSearch(historyQuery, autoMode)
                                            },
                                            label = {
                                                Text(
                                                    text = historyQuery,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            },
                                            trailingIcon = {
                                                IconButton(
                                                    onClick = {
                                                        coroutineScope.launch {
                                                            discoveryRepository.deleteSearchHistoryItem(historyQuery)
                                                        }
                                                    },
                                                    modifier = Modifier
                                                        .size(20.dp)
                                                        .testTag(
                                                            UnifiedSearchTestTags.DELETE_HISTORY_PREFIX + historyQuery,
                                                        ),
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Outlined.Close,
                                                        contentDescription = stringResource(R.string.action_clear),
                                                        modifier = Modifier.size(14.dp),
                                                    )
                                                }
                                            },
                                            modifier = Modifier.testTag(
                                                UnifiedSearchTestTags.HISTORY_CHIP_PREFIX + historyQuery,
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 4. Online Search Loading / Error / Results Section
                if (onlineSearchState.isSearchingOnline) {
                    item(key = "online_search_loading") {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (isEink) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                            } else {
                                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth(0.6f))
                            }
                            Text(
                                text = stringResource(R.string.search_online_loading),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                } else if (onlineSearchState.errorKind != null) {
                    item(key = "online_search_error") {
                        DiscoveryFeedErrorCard(
                            errorKind = onlineSearchState.errorKind!!,
                            errorServerCode = onlineSearchState.errorServerCode,
                            onRetry = {
                                triggerOnlineSearch(
                                    targetQuery = onlineSearchState.submittedQuery.ifBlank { queryText },
                                    mode = onlineSearchState.mode,
                                )
                            },
                            onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                        )
                    }
                } else if (onlineSearchState.hasCompletedSearch) {
                    item(key = "online_search_results_header") {
                        Text(
                            text = stringResource(
                                R.string.search_online_results_section,
                                onlineSearchState.results.size,
                            ),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag(UnifiedSearchTestTags.ONLINE_RESULTS_HEADER),
                        )
                    }

                    if (onlineSearchState.results.isEmpty()) {
                        item(key = "online_search_empty") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 28.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.search_online_empty,
                                        onlineSearchState.submittedQuery,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.testTag(UnifiedSearchTestTags.ONLINE_EMPTY_TEXT),
                                )
                            }
                        }
                    } else {
                        items(
                            items = onlineSearchState.results,
                            key = { "online_${it.aid}" },
                        ) { onlineItem ->
                            val onlineOrigin = Wenku8SharedElementKeys.NOVEL_SEARCH_ONLINE_ORIGIN
                            DiscoveryNovelCard(
                                item = onlineItem,
                                sharedTransitionOrigin = onlineOrigin,
                                onClick = {
                                    onNovelClickedWithOrigin?.invoke(onlineItem, onlineOrigin)
                                        ?: onNovelClicked(onlineItem)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun CooldownQueuedCard(
    state: OnlineSearchExecutionState,
    isEink: Boolean,
    onCancel: () -> Unit,
) {
    val remainingSeconds = state.remainingCooldownMillis.coerceAtLeast(0L) / 1000f
    val totalMillis = state.totalCooldownMillis.coerceAtLeast(1L)
    val elapsedProgress = (1f - (state.remainingCooldownMillis.toFloat() / totalMillis.toFloat()))
        .coerceIn(0f, 1f)

    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(UnifiedSearchTestTags.COOLDOWN_BANNER)
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.HourglassTop,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(
                        R.string.search_cooldown_queued_format,
                        remainingSeconds,
                        state.submittedQuery,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(
                    onClick = onCancel,
                    modifier = Modifier.testTag(UnifiedSearchTestTags.CANCEL_COOLDOWN_BUTTON),
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            }

            if (isEink) {
                LinearProgressIndicator(
                    progress = { elapsedProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearWavyProgressIndicator(
                    progress = { elapsedProgress },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
