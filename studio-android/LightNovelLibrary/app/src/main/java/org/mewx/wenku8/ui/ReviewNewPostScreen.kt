package org.mewx.wenku8.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mewx.wenku8.R
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.network.LightUserSession
import org.mewx.wenku8.theme.*
import org.mewx.wenku8.viewmodel.ReviewNewPostViewModel
import org.mewx.wenku8.viewmodel.SubmitPostUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewNewPostScreen(
    aid: Int,
    onBackClick: () -> Unit,
    onSuccess: () -> Unit,
    viewModel: ReviewNewPostViewModel = viewModel()
) {
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(uiState) {
        when (val state = uiState) {
            is SubmitPostUiState.Success -> {
                onSuccess()
            }
            is SubmitPostUiState.Error -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(id = R.string.system_warning)) },
            text = { Text(stringResource(id = R.string.system_review_draft_will_be_lost)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        onBackClick()
                    }
                ) {
                    Text(stringResource(id = R.string.dialog_positive_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(id = R.string.dialog_negative_preferno))
                }
            }
        )
    }

    BackHandler {
        if (title.isNotBlank() || content.isNotBlank()) {
            showDiscardDialog = true
        } else {
            onBackClick()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.action_review_new_post)) },
                navigationIcon = {
                    IconButton(onClick = {
                         if (title.isNotBlank() || content.isNotBlank()) {
                            showDiscardDialog = true
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val isSubmitting = uiState is SubmitPostUiState.Submitting
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                             if (!LightUserSession.getLogStatus()) {
                                Toast.makeText(context, R.string.system_not_logged_in, Toast.LENGTH_SHORT).show()
                                return@IconButton
                            }

                            if (title.isBlank() || content.isBlank()) {
                                // Maybe show toast? The original code checked length > MIN_REPLY_TEXT
                            }

                            val badWordTitle = Wenku8API.searchBadWords(title)
                            val badWordContent = Wenku8API.searchBadWords(content)

                            if (badWordTitle != null) {
                                Toast.makeText(context, context.getString(R.string.system_containing_bad_word, badWordTitle), Toast.LENGTH_SHORT).show()
                            } else if (badWordContent != null) {
                                Toast.makeText(context, context.getString(R.string.system_containing_bad_word, badWordContent), Toast.LENGTH_SHORT).show()
                            } else if (title.length < Wenku8API.MIN_REPLY_TEXT || content.length < Wenku8API.MIN_REPLY_TEXT) {
                                Toast.makeText(context, R.string.system_review_too_short, Toast.LENGTH_SHORT).show()
                            } else {
                                viewModel.submitPost(aid, title, content)
                            }
                        },
                        enabled = !isSubmitting
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(Icons.Default.Check, contentDescription = "Submit")
                        }
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(id = R.string.system_review_title)) },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = content,
                onValueChange = { content = it },
                label = { Text(stringResource(id = R.string.system_review_content)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                singleLine = false
            )
        }
    }
}
