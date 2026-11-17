package org.mewx.wenku8.ui.profile

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Campaign
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.EventAvailable
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlinx.coroutines.launch
import org.mewx.wenku8.R
import org.mewx.wenku8.data.repository.AccountSessionState
import org.mewx.wenku8.data.repository.AppVersionCheckResult
import org.mewx.wenku8.data.repository.DailySignOutcome
import org.mewx.wenku8.data.repository.LoginOutcome
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.data.repository.ParsedServerNotice
import org.mewx.wenku8.data.repository.UserSessionRepository
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.io.File

/** Semantic test tags for `P3.1` Account, `NoticeString`, and App Version Check UI. */
object ProfileAccountTestTags {
    const val SERVER_NOTICE_BANNER = "profile_server_notice_banner"
    const val SERVER_NOTICE_DISMISS_BUTTON = "profile_server_notice_dismiss"
    const val ACCOUNT_PROFILE_CARD = "profile_account_card"
    const val OPEN_LOGIN_SHEET_BUTTON = "profile_open_login_sheet"
    const val OPEN_REGISTER_DIALOG_BUTTON = "profile_open_register_dialog"
    const val DAILY_SIGN_BUTTON = "profile_daily_sign_button"
    const val DAILY_SIGN_FEEDBACK_TEXT = "profile_daily_sign_feedback"
    const val REFRESH_PROFILE_BUTTON = "profile_refresh_button"
    const val LOGOUT_BUTTON = "profile_logout_button"
    const val LOGOUT_CONFIRM_BUTTON = "profile_logout_confirm_button"

    const val LOGIN_BOTTOM_SHEET = "profile_login_bottom_sheet"
    const val LOGIN_IDENTIFIER_INPUT = "profile_login_identifier_input"
    const val LOGIN_PASSWORD_INPUT = "profile_login_password_input"
    const val LOGIN_SUBMIT_BUTTON = "profile_login_submit_button"
    const val LOGIN_ERROR_TEXT = "profile_login_error_text"

    const val CHECK_UPDATE_BUTTON = "profile_check_update_button"
    const val UPDATE_AVAILABLE_DIALOG = "profile_update_available_dialog"
}

/**
 * Dismissible M3e tonal announcement card rendering `Wenku8API.NoticeString` (`notice_sc` /
 * `notice_tc` + `saves/notice.wk8`), including safe clickable `<a href>` action chips.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ServerNoticeBannerCard(
    notice: ParsedServerNotice,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onExternalUrlClicked: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag(ProfileAccountTestTags.SERVER_NOTICE_BANNER)
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Campaign,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.notice_card_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                val dismissNoticeLabel = stringResource(R.string.notice_action_dismiss)
                TooltipIconButton(
                    tooltipText = dismissNoticeLabel,
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag(ProfileAccountTestTags.SERVER_NOTICE_DISMISS_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = dismissNoticeLabel,
                    )
                }
            }

            if (notice.plainDisplayText.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = notice.plainDisplayText,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (notice.links.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    notice.links.forEach { link ->
                        AssistChip(
                            onClick = {
                                if (onExternalUrlClicked != null) {
                                    onExternalUrlClicked(link.url)
                                } else {
                                    openSafeBrowserUrl(context, link.url)
                                }
                            },
                            label = { Text(text = link.label) },
                            trailingIcon = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

/**
 * M3e Account Profile & Daily Sign-In card for `Tab 4 · 我的`.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AccountProfileCard(
    sessionState: AccountSessionState,
    registerPageUrl: String,
    onOpenLoginSheet: () -> Unit,
    onPerformDailySignIn: suspend () -> DailySignOutcome,
    onRefreshProfile: suspend () -> Unit,
    onLogoutConfirmed: suspend () -> Unit,
    modifier: Modifier = Modifier,
    onOpenExternalUrl: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEink = LocalEinkMode.current

    var isBusy by remember { mutableStateOf(false) }
    var signFeedbackMessageResId by rememberSaveable { mutableStateOf<Int?>(null) }
    var showLogoutDialog by rememberSaveable { mutableStateOf(false) }
    var showRegisterDialog by rememberSaveable { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .testTag(ProfileAccountTestTags.ACCOUNT_PROFILE_CARD)
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            when (sessionState) {
                AccountSessionState.UnavailableStub -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatarCircle(avatarFile = null, cacheBusterMs = 0L)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auth_card_title_signed_out),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.auth_card_unavailable_stub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                AccountSessionState.SignedOut -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatarCircle(avatarFile = null, cacheBusterMs = 0L)
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.auth_card_title_signed_out),
                                style = MaterialTheme.typography.titleLarge,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.auth_card_subtitle_signed_out),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = onOpenLoginSheet,
                            modifier = Modifier.testTag(ProfileAccountTestTags.OPEN_LOGIN_SHEET_BUTTON),
                        ) {
                            Text(text = stringResource(R.string.auth_action_open_login))
                        }
                        if (registerPageUrl.isNotEmpty()) {
                            OutlinedButton(
                                onClick = { showRegisterDialog = true },
                                modifier = Modifier.testTag(ProfileAccountTestTags.OPEN_REGISTER_DIALOG_BUTTON),
                            ) {
                                Text(text = stringResource(R.string.auth_action_register_web))
                            }
                        }
                    }
                }

                is AccountSessionState.RestoringSession -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatarCircle(
                            avatarFile = sessionState.cachedAvatarFile,
                            cacheBusterMs = 0L,
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Text(
                            text = stringResource(
                                R.string.auth_restoring_format,
                                sessionState.identifierHint,
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    if (isEink) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }

                is AccountSessionState.OfflineWithSavedAccount -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatarCircle(
                            avatarFile = sessionState.cachedAvatarFile,
                            cacheBusterMs = 0L,
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CloudOff,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(
                                        R.string.auth_offline_saved_title_format,
                                        sessionState.identifierHint,
                                    ),
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.auth_offline_saved_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilledTonalButton(
                            enabled = !isBusy,
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    try {
                                        onRefreshProfile()
                                    } finally {
                                        isBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.testTag(ProfileAccountTestTags.REFRESH_PROFILE_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.action_refresh))
                        }
                        TextButton(
                            enabled = !isBusy,
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier.testTag(ProfileAccountTestTags.LOGOUT_BUTTON),
                        ) {
                            Text(text = stringResource(R.string.auth_action_logout))
                        }
                    }
                }

                is AccountSessionState.SignedIn -> {
                    val profile = sessionState.profile
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AccountAvatarCircle(
                            avatarFile = sessionState.cachedAvatarFile,
                            cacheBusterMs = sessionState.avatarCacheBusterMs,
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = profile.nickname.ifEmpty { profile.username },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                if (profile.rank.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        shape = MaterialTheme.shapes.small,
                                        modifier = Modifier.einkHairlineBorder(MaterialTheme.shapes.small),
                                    ) {
                                        Text(
                                            text = profile.rank,
                                            style = MaterialTheme.typography.labelMedium,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = stringResource(R.string.auth_username_format, profile.username),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ProfileMetricPill(
                            label = stringResource(R.string.auth_stat_uid),
                            value = profile.uid.takeIf { it > 0 }?.toString() ?: "—",
                            modifier = Modifier.weight(1f),
                        )
                        ProfileMetricPill(
                            label = stringResource(R.string.auth_stat_score),
                            value = profile.score.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        ProfileMetricPill(
                            label = stringResource(R.string.auth_stat_experience),
                            value = profile.experience.toString(),
                            modifier = Modifier.weight(1f),
                        )
                    }

                    signFeedbackMessageResId?.let { resId ->
                        Spacer(modifier = Modifier.height(12.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(ProfileAccountTestTags.DAILY_SIGN_FEEDBACK_TEXT)
                                .einkHairlineBorder(MaterialTheme.shapes.medium),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(resId),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            enabled = !isBusy,
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    try {
                                        signFeedbackMessageResId = when (onPerformDailySignIn()) {
                                            is DailySignOutcome.SignedAndRefreshed -> R.string.auth_daily_sign_success
                                            DailySignOutcome.AlreadySignedOrRefused -> R.string.auth_daily_sign_already
                                            DailySignOutcome.NotSignedIn,
                                            is DailySignOutcome.NetworkOrServerError,
                                            -> R.string.auth_daily_sign_failed
                                        }
                                    } finally {
                                        isBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.testTag(ProfileAccountTestTags.DAILY_SIGN_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.EventAvailable,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.auth_action_daily_sign))
                        }

                        FilledTonalButton(
                            enabled = !isBusy,
                            onClick = {
                                scope.launch {
                                    isBusy = true
                                    try {
                                        onRefreshProfile()
                                    } finally {
                                        isBusy = false
                                    }
                                }
                            },
                            modifier = Modifier.testTag(ProfileAccountTestTags.REFRESH_PROFILE_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.action_refresh))
                        }

                        TextButton(
                            enabled = !isBusy,
                            onClick = { showLogoutDialog = true },
                            modifier = Modifier.testTag(ProfileAccountTestTags.LOGOUT_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.Logout,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(R.string.auth_action_logout))
                        }
                    }
                }
            }
        }
    }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text(text = stringResource(R.string.auth_logout_confirm_title)) },
            text = { Text(text = stringResource(R.string.auth_logout_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutDialog = false
                        signFeedbackMessageResId = null
                        scope.launch {
                            isBusy = true
                            try {
                                onLogoutConfirmed()
                            } finally {
                                isBusy = false
                            }
                        }
                    },
                    modifier = Modifier.testTag(ProfileAccountTestTags.LOGOUT_CONFIRM_BUTTON),
                ) {
                    Text(text = stringResource(R.string.auth_logout_confirm_action))
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    if (showRegisterDialog && registerPageUrl.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { showRegisterDialog = false },
            title = { Text(text = stringResource(R.string.auth_register_dialog_title)) },
            text = { Text(text = stringResource(R.string.auth_register_dialog_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showRegisterDialog = false
                        if (onOpenExternalUrl != null) {
                            onOpenExternalUrl(registerPageUrl)
                        } else {
                            openSafeBrowserUrl(context, registerPageUrl)
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.auth_register_dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRegisterDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun AccountAvatarCircle(
    avatarFile: File?,
    cacheBusterMs: Long,
) {
    val context = LocalContext.current
    val isEink = LocalEinkMode.current
    val validFile = avatarFile?.takeIf { it.isFile && it.length() > 0L }

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(60.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .einkHairlineBorder(CircleShape),
    ) {
        Icon(
            imageVector = Icons.Outlined.AccountCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp),
        )
        if (validFile != null) {
            val request = remember(validFile.absolutePath, cacheBusterMs, isEink) {
                ImageRequest.Builder(context)
                    .data(validFile)
                    .memoryCacheKey("${validFile.absolutePath}:$cacheBusterMs")
                    .crossfade(!isEink)
                    .build()
            }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun ProfileMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * M3e `ModalBottomSheet` for username/email + password login (`<= 30` chars validation, inline
 * server status mapping, show/hide password toggle, and web registration shortcut).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountLoginBottomSheet(
    registerPageUrl: String,
    onSubmitLogin: suspend (identifier: String, password: String) -> LoginOutcome,
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit,
    onOpenExternalUrl: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var identifier by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    var isPasswordVisible by rememberSaveable { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    var errorResId by rememberSaveable { mutableStateOf<Int?>(null) }

    val triggerLogin = {
        if (!isSubmitting) {
            if (!UserSessionRepository.isValidCredentialLength(identifier, password)) {
                errorResId = R.string.auth_error_length
            } else {
                scope.launch {
                    isSubmitting = true
                    errorResId = null
                    try {
                        when (onSubmitLogin(identifier, password)) {
                            is LoginOutcome.Success -> onLoginSuccess()
                            LoginOutcome.InvalidCredentialLength -> errorResId = R.string.auth_error_length
                            LoginOutcome.UsernameNotFound -> errorResId = R.string.auth_error_username
                            LoginOutcome.IncorrectPassword -> errorResId = R.string.auth_error_password
                            LoginOutcome.ApiNotConfigured -> errorResId = R.string.auth_card_unavailable_stub
                            is LoginOutcome.NetworkOrServerError -> errorResId = R.string.auth_error_network
                        }
                    } finally {
                        isSubmitting = false
                    }
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
        modifier = Modifier.testTag(ProfileAccountTestTags.LOGIN_BOTTOM_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
                .imePadding()
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.auth_sheet_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = identifier,
                onValueChange = { next ->
                    identifier = next
                    if (errorResId != null) errorResId = null
                },
                label = { Text(text = stringResource(R.string.auth_field_identifier_label)) },
                supportingText = {
                    Text(text = "${identifier.trim().length}/${UserSessionRepository.MAX_CREDENTIAL_LENGTH}")
                },
                singleLine = true,
                enabled = !isSubmitting,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Next,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileAccountTestTags.LOGIN_IDENTIFIER_INPUT),
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { next ->
                    password = next
                    if (errorResId != null) errorResId = null
                },
                label = { Text(text = stringResource(R.string.auth_field_password_label)) },
                supportingText = {
                    Text(text = "${password.length}/${UserSessionRepository.MAX_CREDENTIAL_LENGTH}")
                },
                singleLine = true,
                enabled = !isSubmitting,
                visualTransformation = if (isPasswordVisible) {
                    VisualTransformation.None
                } else {
                    PasswordVisualTransformation()
                },
                trailingIcon = {
                    IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                        Icon(
                            imageVector = if (isPasswordVisible) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = stringResource(R.string.auth_toggle_show_password),
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { triggerLogin() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileAccountTestTags.LOGIN_PASSWORD_INPUT),
            )

            errorResId?.let { resId ->
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(resId),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.testTag(ProfileAccountTestTags.LOGIN_ERROR_TEXT),
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Button(
                onClick = { triggerLogin() },
                enabled = !isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ProfileAccountTestTags.LOGIN_SUBMIT_BUTTON),
            ) {
                Text(
                    text = if (isSubmitting) {
                        stringResource(R.string.auth_action_logging_in)
                    } else {
                        stringResource(R.string.auth_action_submit_login)
                    },
                )
            }

            if (registerPageUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(
                    enabled = !isSubmitting,
                    onClick = {
                        if (onOpenExternalUrl != null) {
                            onOpenExternalUrl(registerPageUrl)
                        } else {
                            openSafeBrowserUrl(context, registerPageUrl)
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text(text = stringResource(R.string.auth_action_register_web))
                }
            }
        }
    }
}

/**
 * Manual `CheckAppNewVersion` row/card + update confirmation dialog for `Tab 4 · 我的`.
 */
@Composable
fun AppUpdateCheckCard(
    onCheckForUpdate: suspend () -> AppVersionCheckResult,
    modifier: Modifier = Modifier,
    onOpenDownloadUrl: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isChecking by remember { mutableStateOf(false) }
    var statusLine by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUpdate by remember { mutableStateOf<AppVersionCheckResult.UpdateAvailable?>(null) }

    val checkingLabel = stringResource(R.string.update_checking)
    val failedLabel = stringResource(R.string.update_check_failed)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.SystemUpdateAlt,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.update_action_check),
                    style = MaterialTheme.typography.titleSmall,
                )
                statusLine?.let { line ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            OutlinedButton(
                enabled = !isChecking,
                onClick = {
                    scope.launch {
                        isChecking = true
                        statusLine = checkingLabel
                        try {
                            when (val res = onCheckForUpdate()) {
                                is AppVersionCheckResult.UpToDate -> {
                                    statusLine = context.getString(
                                        R.string.update_up_to_date_format,
                                        res.currentVersionCode,
                                    )
                                }
                                is AppVersionCheckResult.UpdateAvailable -> {
                                    statusLine = null
                                    pendingUpdate = res
                                }
                                AppVersionCheckResult.NetworkOrParseFailure -> {
                                    statusLine = failedLabel
                                }
                            }
                        } finally {
                            isChecking = false
                        }
                    }
                },
                modifier = Modifier.testTag(ProfileAccountTestTags.CHECK_UPDATE_BUTTON),
            ) {
                Text(text = stringResource(R.string.update_action_check))
            }
        }
    }

    pendingUpdate?.let { update ->
        AlertDialog(
            onDismissRequest = { pendingUpdate = null },
            modifier = Modifier.testTag(ProfileAccountTestTags.UPDATE_AVAILABLE_DIALOG),
            title = { Text(text = stringResource(R.string.update_available_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.update_available_body_format,
                        update.currentVersionCode,
                        update.latestVersionCode,
                    ),
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val url = update.downloadPageUrl
                        pendingUpdate = null
                        if (onOpenDownloadUrl != null) {
                            onOpenDownloadUrl(url)
                        } else {
                            openSafeBrowserUrl(context, url)
                        }
                    },
                ) {
                    Text(text = stringResource(R.string.update_action_download))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingUpdate = null }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

private fun openSafeBrowserUrl(context: Context, rawUrl: String) {
    val trimmed = rawUrl.trim()
    if (!trimmed.startsWith("https://", ignoreCase = true) &&
        !trimmed.startsWith("http://", ignoreCase = true)
    ) {
        return
    }
    runCatching {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
