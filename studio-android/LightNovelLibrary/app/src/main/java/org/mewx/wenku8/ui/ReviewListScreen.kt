package org.mewx.wenku8.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mewx.wenku8.R
import org.mewx.wenku8.global.api.ReviewList
import org.mewx.wenku8.viewmodel.ReviewListUiState
import org.mewx.wenku8.viewmodel.ReviewListViewModel
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewListScreen(
    aid: Int,
    onBackClick: () -> Unit,
    onItemClick: (ReviewList.Review) -> Unit,
    onNewPostClick: () -> Unit,
    viewModel: ReviewListViewModel = viewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // Refresh if needed, but viewModel.init handles id check.
                // Original app refreshed onResume.
                viewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(aid) {
        viewModel.init(aid)
    }

    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.action_review_list)) },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onNewPostClick) {
                        Icon(Icons.Default.Add, contentDescription = "New Post")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ReviewListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ReviewListUiState.Error -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = stringResource(id = R.string.system_parse_failed),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.refresh() }) {
                            Text(text = stringResource(id = R.string.task_retry))
                        }
                    }
                }
                is ReviewListUiState.Success -> {
                    ReviewListContent(
                        reviews = state.reviews,
                        onItemClick = onItemClick,
                        onLoadMore = { viewModel.loadMore() },
                        onRefresh = { viewModel.refresh() }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewListContent(
    reviews: List<ReviewList.Review>,
    onItemClick: (ReviewList.Review) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit
) {
    val listState = rememberLazyListState()
    val refreshState = rememberPullToRefreshState()

    // Infinite scroll detection
    val buffer = 2
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisibleItem >= totalItems - buffer - 1
        }
    }

    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            onLoadMore()
        }
    }

    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(reviews) { review ->
                ReviewItem(review = review, onClick = { onItemClick(review) })
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun ReviewItem(
    review: ReviewList.Review,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            val titleColor = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFF44336)
            Text(
                text = review.title,
                style = MaterialTheme.typography.titleMedium,
                color = titleColor
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                val textColor = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    text = dateFormat.format(review.postTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = review.userName,
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Comment,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = textColor
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = review.noReplies.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = textColor
                )
            }
        }
    }
}
