package org.mewx.wenku8.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.autoMirrored.filled.ArrowBack
import androidx.compose.material.icons.autoMirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mewx.wenku8.R
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.global.api.ReviewReplyList
import org.mewx.wenku8.network.LightUserSession
import org.mewx.wenku8.theme.*
import org.mewx.wenku8.viewmodel.ReviewReplyListUiState
import org.mewx.wenku8.viewmodel.ReviewReplyListViewModel
import org.mewx.wenku8.viewmodel.SendReplyUiState
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewReplyListScreen(
    rid: Int,
    title: String?,
    onBackClick: () -> Unit,
    viewModel: ReviewReplyListViewModel = viewModel()
) {
    LaunchedEffect(rid) {
        viewModel.init(rid)
    }

    val uiState by viewModel.uiState.collectAsState()
    val sendReplyState by viewModel.sendReplyState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(sendReplyState) {
        when (val state = sendReplyState) {
            is SendReplyUiState.Success -> {
                viewModel.resetSendReplyState()
            }
            is SendReplyUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.resetSendReplyState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title ?: stringResource(id = R.string.action_review_list),
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
             ReplyInputBar(
                 onSend = { content ->
                     if (!LightUserSession.getLogStatus()) {
                         Toast.makeText(context, R.string.system_not_logged_in, Toast.LENGTH_SHORT).show()
                         return@ReplyInputBar
                     }
                     val badWord = Wenku8API.searchBadWords(content)
                     if (badWord != null) {
                         Toast.makeText(context, context.getString(R.string.system_containing_bad_word, badWord), Toast.LENGTH_SHORT).show()
                     } else if (content.length < Wenku8API.MIN_REPLY_TEXT) {
                         Toast.makeText(context, R.string.system_review_too_short, Toast.LENGTH_SHORT).show()
                     } else {
                         viewModel.sendReply(content)
                     }
                 },
                 isSending = sendReplyState is SendReplyUiState.Sending,
                 resetTrigger = sendReplyState is SendReplyUiState.Success
             )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                is ReviewReplyListUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                is ReviewReplyListUiState.Error -> {
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
                is ReviewReplyListUiState.Success -> {
                    ReviewReplyListContent(
                        replies = state.replies,
                        onLoadMore = { viewModel.loadMore() }
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewReplyListContent(
    replies: List<ReviewReplyList.ReviewReply>,
    onLoadMore: () -> Unit
) {
    val listState = rememberLazyListState()

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

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        itemsIndexed(replies) { index, reply ->
            ReviewReplyItem(reply = reply, position = index + 1)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReviewReplyItem(
    reply: ReviewReplyList.ReviewReply,
    position: Int
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(reply.content))
                    Toast
                        .makeText(
                            context,
                            context.getString(R.string.system_copied_to_clipboard, reply.content),
                            Toast.LENGTH_SHORT
                        )
                        .show()
                }
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val titleColor = if (isSystemInDarkTheme()) Color(0xFFFF8A80) else Color(0xFFF44336)
                Text(
                    text = "[${reply.userName}]",
                    style = MaterialTheme.typography.labelMedium,
                    color = titleColor
                )
                Spacer(modifier = Modifier.width(8.dp))
                val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                val textColor = MaterialTheme.colorScheme.onSurfaceVariant
                Text(
                    text = dateFormat.format(reply.replyTime),
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "# $position",
                    style = MaterialTheme.typography.labelSmall,
                    color = textColor
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
            Text(
                text = reply.content,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplyInputBar(
    onSend: (String) -> Unit,
    isSending: Boolean,
    resetTrigger: Boolean
) {
    var text by remember { mutableStateOf("") }

    LaunchedEffect(resetTrigger) {
        if (resetTrigger) {
            text = ""
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp)
            .imePadding(), // Handle keyboard
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(id = R.string.action_review_reply)) },
            enabled = !isSending
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(
            onClick = { onSend(text) },
            enabled = !isSending && text.isNotBlank()
        ) {
            if (isSending) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
            }
        }
    }
}
