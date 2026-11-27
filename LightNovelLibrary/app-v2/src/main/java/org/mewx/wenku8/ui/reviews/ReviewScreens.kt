package org.mewx.wenku8.ui.reviews

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Reply
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.R
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.repository.AccountSessionState
import org.mewx.wenku8.data.repository.ReviewComposerValidation
import org.mewx.wenku8.data.repository.ReviewReplyFloorItem
import org.mewx.wenku8.data.repository.ReviewSubmitOutcome
import org.mewx.wenku8.data.repository.ReviewThreadCardItem
import org.mewx.wenku8.data.repository.ReviewsRepository
import org.mewx.wenku8.data.repository.UserSessionRepository
import org.mewx.wenku8.ui.components.ActionTooltipBox
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.components.horizontalScrollFadeEdge
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder

object ReviewScreensTestTags {
    const val REVIEW_LIST_SCREEN = "review_list_screen"
    const val REVIEW_LIST_BACK_BUTTON = "review_list_back_button"
    const val REVIEW_LIST_NEW_THREAD_FAB = "review_list_new_thread_fab"
    const val REVIEW_LIST_LOADING_INDICATOR = "review_list_loading_indicator"
    const val REVIEW_LIST_APPEND_ERROR = "review_list_append_error"
    const val REVIEW_THREAD_CARD_PREFIX = "review_thread_card_"

    const val NEW_THREAD_BOTTOM_SHEET = "review_new_thread_bottom_sheet"
    const val NEW_THREAD_TITLE_INPUT = "review_new_thread_title_input"
    const val NEW_THREAD_CONTENT_INPUT = "review_new_thread_content_input"
    const val NEW_THREAD_SUBMIT_BUTTON = "review_new_thread_submit_button"

    const val REVIEW_THREAD_SCREEN = "review_thread_screen"
    const val REVIEW_THREAD_BACK_BUTTON = "review_thread_back_button"
    const val REVIEW_THREAD_LOADING_INDICATOR = "review_thread_loading_indicator"
    const val REVIEW_FLOOR_CARD_PREFIX = "review_floor_card_"
    const val REVIEW_FLOOR_COPY_PREFIX = "review_floor_copy_"
    const val REVIEW_FLOOR_QUOTE_PREFIX = "review_floor_quote_"
    const val REVIEW_REPLY_INPUT = "review_reply_input"
    const val REVIEW_REPLY_SUBMIT_BUTTON = "review_reply_submit_button"
    const val REVIEW_LOAD_MORE_BUTTON = "review_load_more_button"

    const val COMPOSER_CHIP_LENGTH = "review_composer_chip_length"
    const val COMPOSER_CHIP_BAD_WORD = "review_composer_chip_bad_word"
    const val COMPOSER_CHIP_COOLDOWN = "review_composer_chip_cooldown"
    const val COMPOSER_LOGIN_BUTTON = "review_composer_login_button"
    const val COMPOSER_DRAFT_SAVED_BADGE = "review_composer_draft_saved_badge"
}

private const val DRAFT_AUTOSAVE_DEBOUNCE_MS = 400L
private const val MAX_AUTO_CATCHUP_PAGES_AFTER_REPLY = 5

private enum class ReviewLoadErrorType {
    NONE,
    API_UNAVAILABLE,
    NETWORK_OR_SERVER,
}

/**
 * Community Review Thread List Screen (`书评讨论`, Doc `05` Screen 6 & Doc `08` §5).
 *
 * Displays paginated review threads for [aid] with circular UID avatars (`avatar.wenku8.com`,
 * degrading gracefully to a tonal person icon on `:api-stub`), reply-count pills, infinite scroll,
 * and a `✍️ 发表书评` Extended FAB that opens [NewReviewThreadBottomSheet] with `400ms` auto-saved
 * drafts (`REVIEW_DRAFTS`).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewListScreen(
    aid: Int,
    novelTitle: String,
    reviewsRepository: ReviewsRepository,
    appLanguage: Wenku8API.AppLanguage,
    onNavigateBack: () -> Unit,
    onOpenThread: (ReviewThreadCardItem) -> Unit,
    modifier: Modifier = Modifier,
    userSessionRepository: UserSessionRepository? = null,
    onOpenLoginSheet: () -> Unit = {},
    onOpenNetworkDiagnostics: () -> Unit = {},
) {
    BackHandler(onBack = onNavigateBack)

    val isEinkMode = LocalEinkMode.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var threadItems by remember(aid, appLanguage) { mutableStateOf<List<ReviewThreadCardItem>>(emptyList()) }
    var currentPage by remember(aid, appLanguage) { mutableIntStateOf(0) }
    var totalPages by remember(aid, appLanguage) { mutableIntStateOf(1) }
    var isInitialOrRefreshLoading by remember(aid, appLanguage) { mutableStateOf(false) }
    var isLoadingNextPage by remember(aid, appLanguage) { mutableStateOf(false) }
    var errorType by remember(aid, appLanguage) { mutableStateOf(ReviewLoadErrorType.NONE) }
    var isNewThreadSheetOpen by rememberSaveable(aid) { mutableStateOf(false) }

    var activeLoadJob by remember { mutableStateOf<Job?>(null) }
    var activeLoadGeneration by remember { mutableIntStateOf(0) }

    val sessionState = if (userSessionRepository != null) {
        val state by userSessionRepository.sessionState.collectAsStateWithLifecycle()
        state
    } else {
        null
    }
    val canAttemptPosting = sessionState == null ||
        sessionState is AccountSessionState.SignedIn ||
        sessionState is AccountSessionState.OfflineWithSavedAccount ||
        sessionState is AccountSessionState.RestoringSession

    fun launchLoadPage(targetPage: Int, replaceExisting: Boolean) {
        if (replaceExisting) {
            activeLoadJob?.cancel()
        } else if (isInitialOrRefreshLoading || isLoadingNextPage) {
            return
        }

        val generation = activeLoadGeneration + 1
        activeLoadGeneration = generation
        if (replaceExisting) {
            isInitialOrRefreshLoading = true
            isLoadingNextPage = false
        } else {
            isLoadingNextPage = true
        }
        errorType = ReviewLoadErrorType.NONE

        activeLoadJob = coroutineScope.launch {
            try {
                when (
                    val res = reviewsRepository.fetchThreadListPage(
                        aid = aid,
                        pageNumber = targetPage,
                        appLanguage = appLanguage,
                    )
                ) {
                    is WenkuApiResult.Success -> {
                        if (activeLoadGeneration != generation) return@launch
                        currentPage = res.data.currentPage
                        totalPages = res.data.totalPages
                        threadItems = if (replaceExisting) {
                            res.data.items
                        } else {
                            val existingIds = threadItems.asSequence().map { it.rid }.toHashSet()
                            threadItems + res.data.items.filter { it.rid !in existingIds }
                        }
                    }
                    WenkuApiResult.ApiNotConfigured -> {
                        if (activeLoadGeneration != generation) return@launch
                        errorType = ReviewLoadErrorType.API_UNAVAILABLE
                    }
                    is WenkuApiResult.UpgradeRequired,
                    WenkuApiResult.AuthRejected,
                    is WenkuApiResult.NetworkError,
                    is WenkuApiResult.ServerErrorCode,
                    WenkuApiResult.EmptyResponse,
                    is WenkuApiResult.ParseError,
                    -> {
                        if (activeLoadGeneration != generation) return@launch
                        errorType = ReviewLoadErrorType.NETWORK_OR_SERVER
                    }
                }
            } finally {
                if (activeLoadGeneration == generation) {
                    isInitialOrRefreshLoading = false
                    isLoadingNextPage = false
                    activeLoadJob = null
                }
            }
        }
    }

    DisposableEffect(aid, appLanguage) {
        onDispose {
            activeLoadJob?.cancel()
            activeLoadJob = null
        }
    }

    LaunchedEffect(aid, appLanguage) {
        launchLoadPage(targetPage = 1, replaceExisting = true)
    }

    val shouldAppendNextPage by remember(listState, threadItems.size) {
        derivedStateOf {
            if (threadItems.isEmpty()) {
                false
            } else {
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                lastVisible >= (threadItems.size - 3).coerceAtLeast(0)
            }
        }
    }

    LaunchedEffect(
        aid,
        appLanguage,
        shouldAppendNextPage,
        currentPage,
        totalPages,
        isInitialOrRefreshLoading,
        isLoadingNextPage,
        errorType,
    ) {
        if (shouldAppendNextPage &&
            !isInitialOrRefreshLoading &&
            !isLoadingNextPage &&
            errorType == ReviewLoadErrorType.NONE &&
            currentPage in 1 until totalPages
        ) {
            launchLoadPage(targetPage = currentPage + 1, replaceExisting = false)
        }
    }

    val threadPublishedMsg = stringResource(R.string.review_submit_success_thread)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(ReviewScreensTestTags.REVIEW_LIST_SCREEN),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.review_list_title),
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (novelTitle.isNotBlank()) {
                            Text(
                                text = novelTitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    TooltipIconButton(
                        tooltipText = backLabel,
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag(ReviewScreensTestTags.REVIEW_LIST_BACK_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = backLabel,
                        )
                    }
                },
                actions = {
                    val refreshLabel = stringResource(R.string.review_refresh_action)
                    TooltipIconButton(
                        tooltipText = refreshLabel,
                        onClick = {
                            if (!isInitialOrRefreshLoading) {
                                launchLoadPage(targetPage = 1, replaceExisting = true)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = refreshLabel,
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            if (reviewsRepository.isReviewsApiConfigured) {
                ExtendedFloatingActionButton(
                    onClick = { isNewThreadSheetOpen = true },
                    icon = {
                        Icon(
                            imageVector = Icons.Outlined.EditNote,
                            contentDescription = null,
                        )
                    },
                    text = { Text(text = stringResource(R.string.review_fab_new_thread)) },
                    modifier = Modifier
                        .testTag(ReviewScreensTestTags.REVIEW_LIST_NEW_THREAD_FAB)
                        .einkHairlineBorder(MaterialTheme.shapes.large),
                )
            }
        },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = isInitialOrRefreshLoading && threadItems.isNotEmpty(),
            onRefresh = {
                launchLoadPage(targetPage = 1, replaceExisting = true)
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isInitialOrRefreshLoading || isLoadingNextPage) {
                    val progressModifier = Modifier
                        .fillMaxWidth()
                        .testTag(ReviewScreensTestTags.REVIEW_LIST_LOADING_INDICATOR)
                    if (isEinkMode) {
                        LinearProgressIndicator(modifier = progressModifier)
                    } else {
                        LinearWavyProgressIndicator(modifier = progressModifier)
                    }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 12.dp,
                        bottom = 96.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (errorType != ReviewLoadErrorType.NONE && threadItems.isEmpty() && !isInitialOrRefreshLoading) {
                        item(key = "review_list_error") {
                            ReviewErrorStateCard(
                                errorType = errorType,
                                onRetry = {
                                    launchLoadPage(targetPage = 1, replaceExisting = true)
                                },
                                onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                            )
                        }
                    } else if (threadItems.isEmpty() && !isInitialOrRefreshLoading) {
                        item(key = "review_list_empty") {
                            ReviewEmptyStateCard()
                        }
                    } else {
                        items(
                            items = threadItems,
                            key = { it.rid },
                        ) { thread ->
                            ReviewThreadSummaryCard(
                                thread = thread,
                                onClick = { onOpenThread(thread) },
                            )
                        }

                        if (errorType != ReviewLoadErrorType.NONE &&
                            !isInitialOrRefreshLoading &&
                            !isLoadingNextPage &&
                            currentPage in 1 until totalPages
                        ) {
                            item(key = "review_list_append_error") {
                                Box(
                                    modifier = Modifier.testTag(
                                        ReviewScreensTestTags.REVIEW_LIST_APPEND_ERROR,
                                    ),
                                ) {
                                    ReviewErrorStateCard(
                                        errorType = errorType,
                                        onRetry = {
                                            launchLoadPage(
                                                targetPage = currentPage + 1,
                                                replaceExisting = false,
                                            )
                                        },
                                        onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (isNewThreadSheetOpen) {
        NewReviewThreadBottomSheet(
            aid = aid,
            novelTitle = novelTitle,
            reviewsRepository = reviewsRepository,
            canAttemptPosting = canAttemptPosting,
            onRequestLogin = {
                isNewThreadSheetOpen = false
                onOpenLoginSheet()
            },
            onDismiss = { isNewThreadSheetOpen = false },
            onThreadPublished = {
                isNewThreadSheetOpen = false
                launchLoadPage(targetPage = 1, replaceExisting = true)
                coroutineScope.launch {
                    snackbarHostState.showSnackbar(threadPublishedMsg)
                }
            },
        )
    }
}

/**
 * Community Review Thread Detail & Reply Composer Screen (`书评详情`, Doc `05` Screen 6).
 *
 * Renders `1楼 · 楼主` + paginated floor replies (`2楼`, `3楼`...), real UID avatars, 1-tap
 * `[ 复制 ]` and `[ 回复 N楼 ]` quote insertion (`回复 N楼 @user：\n`), and a sticky bottom composer
 * with `400ms` auto-saved drafts (`REVIEW_DRAFTS` `rid > 0`) and live pre-submit status chips.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ReviewThreadScreen(
    aid: Int,
    novelTitle: String,
    thread: ReviewThreadCardItem,
    reviewsRepository: ReviewsRepository,
    appLanguage: Wenku8API.AppLanguage,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    userSessionRepository: UserSessionRepository? = null,
    onOpenLoginSheet: () -> Unit = {},
    onOpenNetworkDiagnostics: () -> Unit = {},
) {
    BackHandler(onBack = onNavigateBack)

    val context = LocalContext.current
    val isEinkMode = LocalEinkMode.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()

    var floorItems by remember(thread.rid, appLanguage) { mutableStateOf<List<ReviewReplyFloorItem>>(emptyList()) }
    var currentPage by remember(thread.rid, appLanguage) { mutableIntStateOf(0) }
    var totalPages by remember(thread.rid, appLanguage) { mutableIntStateOf(1) }
    var lastPageStartIndex by remember(thread.rid, appLanguage) { mutableIntStateOf(0) }
    var displayedReplyCount by remember(thread.rid) { mutableIntStateOf(thread.replyCount) }
    var isInitialOrRefreshLoading by remember(thread.rid, appLanguage) { mutableStateOf(false) }
    var isLoadingNextPage by remember(thread.rid, appLanguage) { mutableStateOf(false) }
    var errorType by remember(thread.rid, appLanguage) { mutableStateOf(ReviewLoadErrorType.NONE) }

    var activeLoadJob by remember { mutableStateOf<Job?>(null) }
    var activeLoadGeneration by remember { mutableIntStateOf(0) }

    var replyContent by rememberSaveable(aid, thread.rid) { mutableStateOf("") }
    var initialDraftHydrated by remember(aid, thread.rid) { mutableStateOf(false) }
    var lastPersistedDraftContent by remember(aid, thread.rid) { mutableStateOf("") }
    var draftSavedIndicatorVisible by remember(aid, thread.rid) { mutableStateOf(false) }
    var isSubmittingReply by remember(thread.rid) { mutableStateOf(false) }

    val sessionState = if (userSessionRepository != null) {
        val state by userSessionRepository.sessionState.collectAsStateWithLifecycle()
        state
    } else {
        null
    }
    val canAttemptPosting = sessionState == null ||
        sessionState is AccountSessionState.SignedIn ||
        sessionState is AccountSessionState.OfflineWithSavedAccount ||
        sessionState is AccountSessionState.RestoringSession

    // Hydrate saved reply draft for (aid, rid) before enabling 400ms auto-save.
    LaunchedEffect(aid, thread.rid) {
        val existingDraft = reviewsRepository.loadDraft(aid = aid, rid = thread.rid)
        if (existingDraft != null && replyContent.isEmpty()) {
            replyContent = existingDraft.draftContent
            lastPersistedDraftContent = existingDraft.draftContent
            draftSavedIndicatorVisible = existingDraft.draftContent.isNotBlank()
        }
        initialDraftHydrated = true
    }

    // 400ms debounced draft auto-save into Room `review_drafts(aid, rid)`.
    LaunchedEffect(initialDraftHydrated, replyContent) {
        if (!initialDraftHydrated) return@LaunchedEffect
        if (replyContent == lastPersistedDraftContent) return@LaunchedEffect
        delay(DRAFT_AUTOSAVE_DEBOUNCE_MS)
        reviewsRepository.saveOrClearDraft(
            aid = aid,
            novelTitleHint = novelTitle,
            rid = thread.rid,
            draftTitle = "",
            draftContent = replyContent,
        )
        lastPersistedDraftContent = replyContent
        draftSavedIndicatorVisible = replyContent.isNotBlank()
    }

    val cooldownUntilMs by reviewsRepository.cooldownUntilMonotonicMs.collectAsStateWithLifecycle()
    var cooldownTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(cooldownUntilMs) {
        while (reviewsRepository.remainingCooldownSeconds() > 0) {
            cooldownTick++
            delay(250L)
        }
        cooldownTick++
    }

    val liveValidation = remember(replyContent, cooldownUntilMs, cooldownTick) {
        reviewsRepository.evaluateComposer(
            title = "",
            content = replyContent,
            requireTitle = false,
        )
    }

    fun launchLoadFloorsPage(targetPage: Int, replaceExisting: Boolean) {
        if (replaceExisting) {
            activeLoadJob?.cancel()
        } else if (isInitialOrRefreshLoading || isLoadingNextPage) {
            return
        }

        val generation = activeLoadGeneration + 1
        activeLoadGeneration = generation
        if (replaceExisting) {
            isInitialOrRefreshLoading = true
            isLoadingNextPage = false
        } else {
            isLoadingNextPage = true
        }
        errorType = ReviewLoadErrorType.NONE

        val startingOffset = if (replaceExisting) 0 else floorItems.size
        activeLoadJob = coroutineScope.launch {
            try {
                when (
                    val res = reviewsRepository.fetchReplyPage(
                        rid = thread.rid,
                        pageNumber = targetPage,
                        startingFloorOffset = startingOffset,
                        appLanguage = appLanguage,
                    )
                ) {
                    is WenkuApiResult.Success -> {
                        if (activeLoadGeneration != generation) return@launch
                        currentPage = res.data.currentPage
                        totalPages = res.data.totalPages
                        if (replaceExisting) {
                            lastPageStartIndex = 0
                            floorItems = res.data.items
                        } else {
                            lastPageStartIndex = floorItems.size
                            floorItems = floorItems + res.data.items
                        }
                        if (currentPage == totalPages && floorItems.isNotEmpty()) {
                            displayedReplyCount = maxOf(
                                displayedReplyCount,
                                (floorItems.size - 1).coerceAtLeast(0),
                            )
                        }
                    }
                    WenkuApiResult.ApiNotConfigured -> {
                        if (activeLoadGeneration != generation) return@launch
                        errorType = ReviewLoadErrorType.API_UNAVAILABLE
                    }
                    is WenkuApiResult.UpgradeRequired,
                    WenkuApiResult.AuthRejected,
                    is WenkuApiResult.NetworkError,
                    is WenkuApiResult.ServerErrorCode,
                    WenkuApiResult.EmptyResponse,
                    is WenkuApiResult.ParseError,
                    -> {
                        if (activeLoadGeneration != generation) return@launch
                        errorType = ReviewLoadErrorType.NETWORK_OR_SERVER
                    }
                }
            } finally {
                if (activeLoadGeneration == generation) {
                    isInitialOrRefreshLoading = false
                    isLoadingNextPage = false
                    activeLoadJob = null
                }
            }
        }
    }

    /**
     * Refreshes the thread after a reply is published without discarding earlier loaded pages.
     * Because Wenku8 orders replies chronologically (oldest on page 1, newest on the last page),
     * re-fetches the tail page and automatically catches up through newly created trailing pages
     * (bounded by [MAX_AUTO_CATCHUP_PAGES_AFTER_REPLY]) before scrolling to the new floor.
     */
    fun launchRefreshAfterReply() {
        activeLoadJob?.cancel()
        val generation = activeLoadGeneration + 1
        activeLoadGeneration = generation

        val loadedPage = currentPage
        val knownTotalPages = totalPages.coerceAtLeast(1)
        val existingFloors = floorItems
        val tailStart = lastPageStartIndex.coerceIn(0, existingFloors.size)

        val startFromNextPage = loadedPage in 1 until knownTotalPages &&
            (knownTotalPages - loadedPage) <= MAX_AUTO_CATCHUP_PAGES_AFTER_REPLY
        val firstPageToFetch = when {
            loadedPage <= 0 -> 1
            startFromNextPage -> loadedPage + 1
            else -> loadedPage
        }
        val shouldReplaceExisting = firstPageToFetch == 1 && !startFromNextPage

        if (shouldReplaceExisting) {
            isInitialOrRefreshLoading = true
            isLoadingNextPage = false
        } else {
            isInitialOrRefreshLoading = false
            isLoadingNextPage = true
        }
        errorType = ReviewLoadErrorType.NONE

        activeLoadJob = coroutineScope.launch {
            try {
                var accumulatedFloors = when {
                    shouldReplaceExisting -> emptyList()
                    startFromNextPage -> existingFloors
                    else -> existingFloors.take(tailStart)
                }
                var pageToFetch = firstPageToFetch
                var fetchedCount = 0
                var reachedFinalPage = false

                while (fetchedCount < MAX_AUTO_CATCHUP_PAGES_AFTER_REPLY + 1) {
                    val pageStartOffset = accumulatedFloors.size
                    when (
                        val res = reviewsRepository.fetchReplyPage(
                            rid = thread.rid,
                            pageNumber = pageToFetch,
                            startingFloorOffset = pageStartOffset,
                            appLanguage = appLanguage,
                        )
                    ) {
                        is WenkuApiResult.Success -> {
                            if (activeLoadGeneration != generation) return@launch
                            fetchedCount++
                            accumulatedFloors = accumulatedFloors + res.data.items
                            currentPage = res.data.currentPage
                            totalPages = res.data.totalPages
                            lastPageStartIndex = pageStartOffset
                            floorItems = accumulatedFloors
                            if (currentPage == totalPages && accumulatedFloors.isNotEmpty()) {
                                displayedReplyCount = maxOf(
                                    displayedReplyCount,
                                    (accumulatedFloors.size - 1).coerceAtLeast(0),
                                )
                            }

                            val remainingPages = res.data.totalPages - res.data.currentPage
                            if (remainingPages <= 0) {
                                reachedFinalPage = true
                                break
                            }
                            if (remainingPages > MAX_AUTO_CATCHUP_PAGES_AFTER_REPLY) {
                                break
                            }
                            pageToFetch = res.data.currentPage + 1
                        }
                        WenkuApiResult.ApiNotConfigured -> {
                            if (activeLoadGeneration != generation) return@launch
                            errorType = ReviewLoadErrorType.API_UNAVAILABLE
                            return@launch
                        }
                        is WenkuApiResult.UpgradeRequired,
                        WenkuApiResult.AuthRejected,
                        is WenkuApiResult.NetworkError,
                        is WenkuApiResult.ServerErrorCode,
                        WenkuApiResult.EmptyResponse,
                        is WenkuApiResult.ParseError,
                        -> {
                            if (activeLoadGeneration != generation) return@launch
                            errorType = ReviewLoadErrorType.NETWORK_OR_SERVER
                            return@launch
                        }
                    }
                }

                if (reachedFinalPage && activeLoadGeneration == generation && floorItems.isNotEmpty()) {
                    listState.animateScrollToItem(floorItems.lastIndex)
                }
            } finally {
                if (activeLoadGeneration == generation) {
                    isInitialOrRefreshLoading = false
                    isLoadingNextPage = false
                    activeLoadJob = null
                }
            }
        }
    }

    DisposableEffect(thread.rid, appLanguage) {
        onDispose {
            activeLoadJob?.cancel()
            activeLoadJob = null
        }
    }

    LaunchedEffect(thread.rid, appLanguage) {
        launchLoadFloorsPage(targetPage = 1, replaceExisting = true)
    }

    val copiedMsg = stringResource(R.string.review_copied_snackbar)
    val replySuccessMsg = stringResource(R.string.review_submit_success_reply)
    val lockedOrCooldownMsg = stringResource(R.string.review_submit_locked_or_cooldown)
    val failedMsg = stringResource(R.string.review_submit_failed)
    val loginRequiredMsg = stringResource(R.string.review_login_required_hint)

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag(ReviewScreensTestTags.REVIEW_THREAD_SCREEN),
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = thread.title.ifBlank { stringResource(R.string.review_thread_title) },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = stringResource(R.string.review_reply_count_format, displayedReplyCount),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                },
                navigationIcon = {
                    val backLabel = stringResource(R.string.action_back)
                    TooltipIconButton(
                        tooltipText = backLabel,
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag(ReviewScreensTestTags.REVIEW_THREAD_BACK_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = backLabel,
                        )
                    }
                },
                actions = {
                    val refreshLabel = stringResource(R.string.review_refresh_action)
                    TooltipIconButton(
                        tooltipText = refreshLabel,
                        onClick = {
                            if (!isInitialOrRefreshLoading) {
                                launchLoadFloorsPage(targetPage = 1, replaceExisting = true)
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = refreshLabel,
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (reviewsRepository.isReviewsApiConfigured) {
                StickyReplyComposerBar(
                    replyContent = replyContent,
                    onReplyContentChange = { replyContent = it },
                    validation = liveValidation,
                    canAttemptPosting = canAttemptPosting,
                    draftSavedVisible = draftSavedIndicatorVisible,
                    isSubmitting = isSubmittingReply,
                    onRequestLogin = onOpenLoginSheet,
                    onSubmitReply = {
                        if (isSubmittingReply) return@StickyReplyComposerBar
                        coroutineScope.launch {
                            isSubmittingReply = true
                            val outcome = reviewsRepository.submitReply(
                                aid = aid,
                                novelTitleHint = novelTitle,
                                rid = thread.rid,
                                content = replyContent,
                            )
                            isSubmittingReply = false
                            when (outcome) {
                                ReviewSubmitOutcome.Success -> {
                                    replyContent = ""
                                    lastPersistedDraftContent = ""
                                    draftSavedIndicatorVisible = false
                                    displayedReplyCount += 1
                                    launchRefreshAfterReply()
                                    snackbarHostState.showSnackbar(replySuccessMsg)
                                }
                                ReviewSubmitOutcome.NotLoggedIn -> {
                                    snackbarHostState.showSnackbar(loginRequiredMsg)
                                    onOpenLoginSheet()
                                }
                                is ReviewSubmitOutcome.PostLockedOrCooldown -> {
                                    snackbarHostState.showSnackbar(lockedOrCooldownMsg)
                                }
                                is ReviewSubmitOutcome.ValidationFailed -> Unit
                                ReviewSubmitOutcome.ApiNotConfigured,
                                is ReviewSubmitOutcome.NetworkOrServerError,
                                -> {
                                    snackbarHostState.showSnackbar(failedMsg)
                                }
                            }
                        }
                    },
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (isInitialOrRefreshLoading || isLoadingNextPage) {
                val progressModifier = Modifier
                    .fillMaxWidth()
                    .testTag(ReviewScreensTestTags.REVIEW_THREAD_LOADING_INDICATOR)
                if (isEinkMode) {
                    LinearProgressIndicator(modifier = progressModifier)
                } else {
                    LinearWavyProgressIndicator(modifier = progressModifier)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (errorType != ReviewLoadErrorType.NONE && floorItems.isEmpty() && !isInitialOrRefreshLoading) {
                    item(key = "review_thread_error") {
                        ReviewErrorStateCard(
                            errorType = errorType,
                            onRetry = {
                                launchLoadFloorsPage(targetPage = 1, replaceExisting = true)
                            },
                            onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                        )
                    }
                } else {
                    items(
                        items = floorItems,
                        key = { floor -> floor.floorNumber },
                    ) { floor ->
                        ReviewReplyFloorCard(
                            floor = floor,
                            threadTitleOnOpFloor = if (floor.isOriginalPosterFloor) thread.title else null,
                            onCopyContent = {
                                copyPlainTextToClipboard(context, floor.content)
                                coroutineScope.launch {
                                    snackbarHostState.showSnackbar(copiedMsg)
                                }
                            },
                            onQuoteFloor = {
                                val quotePrefix = ReviewsRepository.formatFloorQuotePrefix(
                                    floorNumber = floor.floorNumber,
                                    userName = floor.userName,
                                )
                                replyContent = if (replyContent.isBlank()) {
                                    quotePrefix
                                } else if (replyContent.startsWith(quotePrefix)) {
                                    replyContent
                                } else {
                                    quotePrefix + replyContent
                                }
                            },
                        )
                    }

                    if (currentPage in 1 until totalPages) {
                        if (errorType != ReviewLoadErrorType.NONE &&
                            !isInitialOrRefreshLoading &&
                            !isLoadingNextPage
                        ) {
                            item(key = "review_thread_append_error") {
                                ReviewErrorStateCard(
                                    errorType = errorType,
                                    onRetry = {
                                        launchLoadFloorsPage(
                                            targetPage = currentPage + 1,
                                            replaceExisting = false,
                                        )
                                    },
                                    onOpenNetworkDiagnostics = onOpenNetworkDiagnostics,
                                )
                            }
                        } else {
                            item(key = "load_more_floors") {
                                OutlinedButton(
                                    onClick = {
                                        launchLoadFloorsPage(
                                            targetPage = currentPage + 1,
                                            replaceExisting = false,
                                        )
                                    },
                                    enabled = !isLoadingNextPage && !isInitialOrRefreshLoading,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(ReviewScreensTestTags.REVIEW_LOAD_MORE_BUTTON),
                                ) {
                                    Text(text = stringResource(R.string.review_load_more_action))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NewReviewThreadBottomSheet(
    aid: Int,
    novelTitle: String,
    reviewsRepository: ReviewsRepository,
    canAttemptPosting: Boolean,
    onRequestLogin: () -> Unit,
    onDismiss: () -> Unit,
    onThreadPublished: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    var draftTitle by rememberSaveable(aid) { mutableStateOf("") }
    var draftContent by rememberSaveable(aid) { mutableStateOf("") }
    var initialDraftHydrated by remember(aid) { mutableStateOf(false) }
    var lastPersistedPair by remember(aid) { mutableStateOf("" to "") }
    var draftSavedVisible by remember(aid) { mutableStateOf(false) }
    var isSubmitting by remember(aid) { mutableStateOf(false) }
    var inlineErrorBanner by remember(aid) { mutableStateOf<String?>(null) }

    LaunchedEffect(aid) {
        val saved = reviewsRepository.loadDraft(
            aid = aid,
            rid = ReviewsRepository.NEW_THREAD_DRAFT_RID,
        )
        if (saved != null && draftTitle.isEmpty() && draftContent.isEmpty()) {
            draftTitle = saved.draftTitle
            draftContent = saved.draftContent
            lastPersistedPair = saved.draftTitle to saved.draftContent
            draftSavedVisible = saved.draftTitle.isNotBlank() || saved.draftContent.isNotBlank()
        }
        initialDraftHydrated = true
    }

    LaunchedEffect(initialDraftHydrated, draftTitle, draftContent) {
        if (!initialDraftHydrated) return@LaunchedEffect
        val currentPair = draftTitle to draftContent
        if (currentPair == lastPersistedPair) return@LaunchedEffect
        delay(DRAFT_AUTOSAVE_DEBOUNCE_MS)
        reviewsRepository.saveOrClearDraft(
            aid = aid,
            novelTitleHint = novelTitle,
            rid = ReviewsRepository.NEW_THREAD_DRAFT_RID,
            draftTitle = draftTitle,
            draftContent = draftContent,
        )
        lastPersistedPair = currentPair
        draftSavedVisible = draftTitle.isNotBlank() || draftContent.isNotBlank()
    }

    val cooldownUntilMs by reviewsRepository.cooldownUntilMonotonicMs.collectAsStateWithLifecycle()
    var cooldownTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(cooldownUntilMs) {
        while (reviewsRepository.remainingCooldownSeconds() > 0) {
            cooldownTick++
            delay(250L)
        }
        cooldownTick++
    }

    val liveValidation = remember(draftTitle, draftContent, cooldownUntilMs, cooldownTick) {
        reviewsRepository.evaluateComposer(
            title = draftTitle,
            content = draftContent,
            requireTitle = true,
        )
    }

    val lockedMsg = stringResource(R.string.review_submit_locked_or_cooldown)
    val failedMsg = stringResource(R.string.review_submit_failed)
    val loginRequiredMsg = stringResource(R.string.review_login_required_hint)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ReviewScreensTestTags.NEW_THREAD_BOTTOM_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.review_new_thread_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (draftSavedVisible) {
                    Text(
                        text = stringResource(R.string.review_draft_autosaved_hint),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.testTag(ReviewScreensTestTags.COMPOSER_DRAFT_SAVED_BADGE),
                    )
                }
            }

            if (!canAttemptPosting) {
                ReviewLoginPromptBanner(onRequestLogin = onRequestLogin)
            }

            OutlinedTextField(
                value = draftTitle,
                onValueChange = {
                    draftTitle = it
                    inlineErrorBanner = null
                },
                label = {
                    Text(
                        text = stringResource(
                            R.string.review_title_field_label,
                            liveValidation.minRequiredBytes,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ReviewScreensTestTags.NEW_THREAD_TITLE_INPUT),
            )

            OutlinedTextField(
                value = draftContent,
                onValueChange = {
                    draftContent = it
                    inlineErrorBanner = null
                },
                label = {
                    Text(
                        text = stringResource(
                            R.string.review_content_field_label,
                            liveValidation.minRequiredBytes,
                        ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                minLines = 4,
                maxLines = 8,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ReviewScreensTestTags.NEW_THREAD_CONTENT_INPUT),
            )

            ReviewValidationChipsRow(validation = liveValidation)

            if (inlineErrorBanner != null) {
                Text(
                    text = inlineErrorBanner.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Button(
                onClick = {
                    if (isSubmitting) return@Button
                    coroutineScope.launch {
                        isSubmitting = true
                        inlineErrorBanner = null
                        val outcome = reviewsRepository.submitNewThread(
                            aid = aid,
                            novelTitleHint = novelTitle,
                            title = draftTitle,
                            content = draftContent,
                        )
                        isSubmitting = false
                        when (outcome) {
                            ReviewSubmitOutcome.Success -> {
                                draftTitle = ""
                                draftContent = ""
                                lastPersistedPair = "" to ""
                                draftSavedVisible = false
                                onThreadPublished()
                            }
                            ReviewSubmitOutcome.NotLoggedIn -> {
                                inlineErrorBanner = loginRequiredMsg
                                onRequestLogin()
                            }
                            is ReviewSubmitOutcome.PostLockedOrCooldown -> {
                                inlineErrorBanner = lockedMsg
                            }
                            is ReviewSubmitOutcome.ValidationFailed -> Unit
                            ReviewSubmitOutcome.ApiNotConfigured,
                            is ReviewSubmitOutcome.NetworkOrServerError,
                            -> {
                                inlineErrorBanner = failedMsg
                            }
                        }
                    }
                },
                enabled = canAttemptPosting && liveValidation.canSubmit && !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ReviewScreensTestTags.NEW_THREAD_SUBMIT_BUTTON),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Send,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.review_submit_thread_action),
                    maxLines = 1,
                    softWrap = false,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun StickyReplyComposerBar(
    replyContent: String,
    onReplyContentChange: (String) -> Unit,
    validation: ReviewComposerValidation,
    canAttemptPosting: Boolean,
    draftSavedVisible: Boolean,
    isSubmitting: Boolean,
    onRequestLogin: () -> Unit,
    onSubmitReply: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.extraSmall),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!canAttemptPosting) {
                ReviewLoginPromptBanner(onRequestLogin = onRequestLogin)
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val useCompactComposer = maxWidth < 348.dp || fontScale > 1.1f
                    val useScrollableChips = maxWidth < 348.dp || fontScale > 1.05f
                    val sendLabel = stringResource(R.string.review_submit_reply_action)

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            OutlinedTextField(
                                value = replyContent,
                                onValueChange = onReplyContentChange,
                                placeholder = {
                                    Text(
                                        text = stringResource(
                                            R.string.review_reply_field_placeholder,
                                            validation.minRequiredBytes,
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                maxLines = 4,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(ReviewScreensTestTags.REVIEW_REPLY_INPUT),
                            )
                            if (useCompactComposer) {
                                ActionTooltipBox(tooltipText = sendLabel) {
                                    FilledIconButton(
                                        onClick = onSubmitReply,
                                        enabled = validation.canSubmit && !isSubmitting,
                                        modifier = Modifier
                                            .size(48.dp)
                                            .testTag(ReviewScreensTestTags.REVIEW_REPLY_SUBMIT_BUTTON),
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.Send,
                                            contentDescription = sendLabel,
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }
                                }
                            } else {
                                Button(
                                    onClick = onSubmitReply,
                                    enabled = validation.canSubmit && !isSubmitting,
                                    modifier = Modifier.testTag(ReviewScreensTestTags.REVIEW_REPLY_SUBMIT_BUTTON),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Send,
                                        contentDescription = sendLabel,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = sendLabel,
                                        maxLines = 1,
                                        softWrap = false,
                                    )
                                }
                            }
                        }

                        if (useScrollableChips) {
                            val chipsScrollState = rememberScrollState()
                            val isEink = LocalEinkMode.current
                            val fadeColor = MaterialTheme.colorScheme.surfaceContainerLow
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScrollFadeEdge(
                                        scrollState = chipsScrollState,
                                        fadeColor = fadeColor,
                                        isEink = isEink,
                                    )
                                    .horizontalScroll(chipsScrollState),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ReviewValidationChipsContent(validation = validation)
                                if (draftSavedVisible) {
                                    Text(
                                        text = stringResource(R.string.review_draft_autosaved_hint),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.testTag(ReviewScreensTestTags.COMPOSER_DRAFT_SAVED_BADGE),
                                    )
                                }
                            }
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    ReviewValidationChipsRow(validation = validation)
                                }
                                if (draftSavedVisible) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(R.string.review_draft_autosaved_hint),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        softWrap = false,
                                        modifier = Modifier.testTag(ReviewScreensTestTags.COMPOSER_DRAFT_SAVED_BADGE),
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

@Composable
private fun ReviewLoginPromptBanner(onRequestLogin: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.review_login_required_hint),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilledTonalButton(
                onClick = onRequestLogin,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                modifier = Modifier.testTag(ReviewScreensTestTags.COMPOSER_LOGIN_BUTTON),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Login,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.review_login_action),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewValidationChipsRow(validation: ReviewComposerValidation) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ReviewValidationChipsContent(validation = validation)
    }
}

@Composable
private fun ReviewValidationChipsContent(validation: ReviewComposerValidation) {
    StatusPillChip(
        text = if (validation.lengthSatisfied) {
            stringResource(R.string.review_chip_length_ok_format, validation.minRequiredBytes)
        } else {
            stringResource(R.string.review_chip_length_pending_format, validation.minRequiredBytes)
        },
        isPositive = validation.lengthSatisfied,
        isError = false,
        testTag = ReviewScreensTestTags.COMPOSER_CHIP_LENGTH,
    )

    val badWord = validation.detectedBadWord
    StatusPillChip(
        text = if (badWord == null) {
            stringResource(R.string.review_chip_bad_word_ok)
        } else {
            stringResource(R.string.review_chip_bad_word_found_format, badWord)
        },
        isPositive = badWord == null,
        isError = badWord != null,
        testTag = ReviewScreensTestTags.COMPOSER_CHIP_BAD_WORD,
    )

    StatusPillChip(
        text = if (validation.cooldownReady) {
            stringResource(R.string.review_chip_cooldown_ok, validation.cooldownSeconds)
        } else {
            stringResource(R.string.review_chip_cooldown_wait_format, validation.cooldownRemainingSeconds)
        },
        isPositive = validation.cooldownReady,
        isError = false,
        testTag = ReviewScreensTestTags.COMPOSER_CHIP_COOLDOWN,
    )
}

@Composable
private fun StatusPillChip(
    text: String,
    isPositive: Boolean,
    isError: Boolean,
    testTag: String,
) {
    val containerColor = when {
        isError -> MaterialTheme.colorScheme.errorContainer
        isPositive -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = when {
        isError -> MaterialTheme.colorScheme.onErrorContainer
        isPositive -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier
            .semantics(mergeDescendants = true) {}
            .testTag(testTag)
            .einkHairlineBorder(MaterialTheme.shapes.small),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ReviewThreadSummaryCard(
    thread: ReviewThreadCardItem,
    onClick: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .testTag(ReviewScreensTestTags.REVIEW_THREAD_CARD_PREFIX + thread.rid)
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReviewUserAvatar(
                    avatarUrl = thread.avatarUrl,
                    sizeDp = 36,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = thread.userName.ifBlank { "UID ${thread.uid}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (thread.lastReplyTimeFormatted.isNotBlank()) {
                        Text(
                            text = stringResource(
                                R.string.review_last_reply_format,
                                thread.lastReplyTimeFormatted,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.einkHairlineBorder(MaterialTheme.shapes.small),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ChatBubbleOutline,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.review_reply_count_format, thread.replyCount),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
            }

            Text(
                text = thread.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewReplyFloorCard(
    floor: ReviewReplyFloorItem,
    threadTitleOnOpFloor: String?,
    onCopyContent: () -> Unit,
    onQuoteFloor: () -> Unit,
) {
    val cardColor = if (floor.isOriginalPosterFloor) {
        MaterialTheme.colorScheme.surfaceContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardColor),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ReviewScreensTestTags.REVIEW_FLOOR_CARD_PREFIX + floor.floorNumber)
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReviewUserAvatar(
                    avatarUrl = floor.avatarUrl,
                    sizeDp = 36,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = floor.userName.ifBlank { "UID ${floor.uid}" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (floor.replyTimeFormatted.isNotBlank()) {
                        Text(
                            text = floor.replyTimeFormatted,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    color = if (floor.isOriginalPosterFloor) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    contentColor = if (floor.isOriginalPosterFloor) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.einkHairlineBorder(MaterialTheme.shapes.small),
                ) {
                    Text(
                        text = if (floor.isOriginalPosterFloor) {
                            stringResource(R.string.review_floor_op_badge)
                        } else {
                            stringResource(R.string.review_floor_number_format, floor.floorNumber)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (floor.isOriginalPosterFloor) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            if (!threadTitleOnOpFloor.isNullOrBlank()) {
                Text(
                    text = threadTitleOnOpFloor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

            Text(
                text = floor.content,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                AssistChip(
                    onClick = onCopyContent,
                    label = {
                        Text(
                            text = stringResource(R.string.review_copy_action),
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.testTag(
                        ReviewScreensTestTags.REVIEW_FLOOR_COPY_PREFIX + floor.floorNumber,
                    ),
                )
                AssistChip(
                    onClick = onQuoteFloor,
                    label = {
                        Text(
                            text = stringResource(
                                R.string.review_quote_floor_format,
                                floor.floorNumber,
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Reply,
                            contentDescription = null,
                            modifier = Modifier.size(AssistChipDefaults.IconSize),
                        )
                    },
                    modifier = Modifier.testTag(
                        ReviewScreensTestTags.REVIEW_FLOOR_QUOTE_PREFIX + floor.floorNumber,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ReviewUserAvatar(
    avatarUrl: String,
    sizeDp: Int,
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .einkHairlineBorder(CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Person,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size((sizeDp * 0.56f).dp),
        )
        if (avatarUrl.isNotBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ReviewEmptyStateCard() {
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp),
            )
            Text(
                text = stringResource(R.string.review_list_empty_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.review_list_empty_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewErrorStateCard(
    errorType: ReviewLoadErrorType,
    onRetry: () -> Unit,
    onOpenNetworkDiagnostics: () -> Unit,
) {
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
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (errorType == ReviewLoadErrorType.API_UNAVAILABLE) {
                        stringResource(R.string.review_api_unavailable)
                    } else {
                        stringResource(R.string.review_load_error_title)
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            if (errorType == ReviewLoadErrorType.NETWORK_OR_SERVER) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.review_retry_action))
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
                    )
                }
            }
        }
    }
}

private fun copyPlainTextToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText("wenku8_review_floor", text))
}
