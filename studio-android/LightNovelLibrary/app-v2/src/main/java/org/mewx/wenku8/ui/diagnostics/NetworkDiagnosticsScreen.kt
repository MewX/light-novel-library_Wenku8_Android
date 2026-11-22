package org.mewx.wenku8.ui.diagnostics

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.mewx.wenku8.R
import org.mewx.wenku8.data.remote.diagnostics.AutoRoutingModeState
import org.mewx.wenku8.data.remote.diagnostics.ComparisonAxis
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsLiveProgress
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsReport
import org.mewx.wenku8.data.remote.diagnostics.DiagnosticsStopReason
import org.mewx.wenku8.data.remote.diagnostics.NetworkSnapshot
import org.mewx.wenku8.data.remote.diagnostics.ProbeSampleResult
import org.mewx.wenku8.data.remote.diagnostics.RecordedCandidateSummary
import org.mewx.wenku8.data.remote.diagnostics.UnderlyingTransportKind
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.util.Locale

object NetworkDiagnosticsTestTags {
    const val SCREEN_ROOT = "network_diagnostics_screen_root"
    const val CONTENT_LIST = "network_diagnostics_content_list"
    const val BACK_BUTTON = "network_diagnostics_back_button"
    const val HEADER_COPY_BUTTON = "network_diagnostics_header_copy"
    const val HEADER_SHARE_BUTTON = "network_diagnostics_header_share"
    const val AUTO_OPTIMIZE_SWITCH = "network_diagnostics_auto_opt_switch"
    const val AUTO_OPTIMIZE_STATUS_TEXT = "network_diagnostics_auto_opt_status"
    const val SELECTED_IP_BANNER = "network_diagnostics_selected_ip_banner"
    const val SELECTED_IP_TEXT = "network_diagnostics_selected_ip_text"
    const val RECORDED_IP_BANNER = "network_diagnostics_recorded_ip_banner"
    const val RECORDED_IP_TEXT = "network_diagnostics_recorded_ip_text"
    const val RESET_REEVALUATE_BUTTON = "network_diagnostics_reset_reevaluate"
    const val START_QUICK_CHECK_BUTTON = "network_diagnostics_start_quick_check"
    const val STOP_ACTIVE_RUN_BUTTON = "network_diagnostics_stop_active_run"
    const val WAVY_PROGRESS_INDICATOR = "network_diagnostics_wavy_progress"
    const val FLAT_EINK_PROGRESS_INDICATOR = "network_diagnostics_flat_eink_progress"
    const val METRIC_SUCCESS_VALUE = "network_diagnostics_metric_success_value"
    const val METRIC_MEDIAN_FULL_VALUE = "network_diagnostics_metric_median_full_value"
    const val METRIC_MEDIAN_HEADERS_VALUE = "network_diagnostics_metric_median_headers_value"
    const val METRIC_MIN_MAX_VALUE = "network_diagnostics_metric_min_max_value"
    const val COPY_SUMMARY_BUTTON = "network_diagnostics_copy_summary_button"
    const val SHARE_REPORT_BUTTON = "network_diagnostics_share_report_button"
    const val RETEST_BUTTON = "network_diagnostics_retest_button"
    const val DETAILS_TOGGLE_ROW = "network_diagnostics_details_toggle"
    const val SAMPLE_ROW_PREFIX = "network_diagnostics_sample_row_"
    const val USAGE_GUIDE_CARD = "network_diagnostics_usage_guide_card"
    const val LAB_TOGGLE_ROW = "network_diagnostics_lab_toggle"
    const val LAB_AXIS_BUTTON_PREFIX = "network_diagnostics_lab_axis_"
    const val LAB_READ_ONLY_API_BUTTON = "network_diagnostics_lab_api_button"
    const val LAB_READ_ONLY_API_STATUS = "network_diagnostics_lab_api_status"
    const val PRE_SHARE_SHEET = "network_diagnostics_pre_share_sheet"
    const val PRE_SHARE_DISCLOSED_TEXT = "network_diagnostics_pre_share_disclosed"
    const val PRE_SHARE_NOTE_INPUT = "network_diagnostics_pre_share_note_input"
    const val PRE_SHARE_CONFIRM_BUTTON = "network_diagnostics_pre_share_confirm"
}

/**
 * Full-screen Material 3 Expressive (`M3e`) Network Diagnostics & `"自动优化连接"` studio
 * implementing `11-network-diagnostics-and-auto-routing-ui.md` §§3–5.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NetworkDiagnosticsScreen(
    viewModel: NetworkDiagnosticsViewModel,
    autoOptimizeEnabled: Boolean,
    onAutoOptimizeChanged: (Boolean) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    onExportReportOverride: ((DiagnosticsReport, String?) -> Unit)? = null,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val isEinkMode = LocalEinkMode.current
    val coroutineScope = rememberCoroutineScope()

    val networkSnapshot by viewModel.networkSnapshot.collectAsStateWithLifecycle()
    val runState by viewModel.runState.collectAsStateWithLifecycle()
    val autoRoutingState by viewModel.autoRoutingState.collectAsStateWithLifecycle()
    val recordedCandidate by viewModel.recordedCandidate.collectAsStateWithLifecycle()

    val isRunning = runState is DiagnosticsRunUiState.Running
    val finishedReport = (runState as? DiagnosticsRunUiState.Finished)?.report
    val displayedSamples: List<ProbeSampleResult> = when (val state = runState) {
        DiagnosticsRunUiState.Idle -> emptyList()
        is DiagnosticsRunUiState.Running -> state.progress.completedSamples
        is DiagnosticsRunUiState.Finished -> state.report.samples
    }

    var isSampleDetailsExpanded by rememberSaveable { mutableStateOf(false) }
    var isLabExpanded by rememberSaveable { mutableStateOf(false) }
    var isPreShareSheetOpen by rememberSaveable { mutableStateOf(false) }
    var shareUserNote by rememberSaveable { mutableStateOf("") }
    var readOnlyApiResultMessage by rememberSaveable { mutableStateOf<String?>(null) }

    BackHandler {
        if (isPreShareSheetOpen) {
            isPreShareSheetOpen = false
        } else {
            viewModel.stopActiveRunByUser()
            onNavigateBack()
        }
    }

    // Purge >24h temporary report files on screen entry so cache/diagnostics never accumulates stale files.
    LaunchedEffect(Unit) {
        runCatching {
            DiagnosticsReportShareHelper.purgeExpiredReportFiles(context.cacheDir)
        }
    }

    val hostActivity = context.findHostActivity()

    // Scoped FLAG_KEEP_SCREEN_ON strictly while a manual diagnostics run is active and visible.
    DisposableEffect(isRunning, hostActivity) {
        val window = hostActivity?.window
        val hadFlagBefore = runCatching {
            window != null &&
                (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        }.getOrDefault(false)
        if (isRunning && window != null && !hadFlagBefore) {
            runCatching { window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }
        onDispose {
            if (isRunning && window != null && !hadFlagBefore) {
                runCatching { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
        }
    }

    // Cancel active probes on backgrounding (Home / screen lock), while surviving rotation & Share Sheet.
    DisposableEffect(lifecycleOwner, hostActivity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && hostActivity?.isChangingConfigurations != true) {
                viewModel.onHostStoppedNotChangingConfigurations()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val copySummaryToClipboard: (DiagnosticsReport) -> Unit = { report ->
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(
            ClipData.newPlainText("Wenku8 Network Diagnostics", report.toPlainTextSummary()),
        )
        Toast.makeText(context, R.string.diag_copied_toast, Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(NetworkDiagnosticsTestTags.SCREEN_ROOT),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            DiagnosticsTopBar(
                finishedReport = finishedReport,
                onNavigateBack = {
                    viewModel.stopActiveRunByUser()
                    onNavigateBack()
                },
                onCopySummary = { finishedReport?.let(copySummaryToClipboard) },
                onOpenShareSheet = {
                    if (finishedReport != null) {
                        isPreShareSheetOpen = true
                    }
                },
            )
        },
    ) { innerPadding ->
        val density = LocalDensity.current
        val initialScreenWidthDp = LocalConfiguration.current.screenWidthDp.dp
        var listWidthDp by remember(initialScreenWidthDp) { mutableStateOf(initialScreenWidthDp) }
        val horizontalPadding = if (listWidthDp < 360.dp) 16.dp else 20.dp

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .onSizeChanged { size ->
                    if (size.width > 0) {
                        listWidthDp = with(density) { size.width.toDp() }
                    }
                }
                .testTag(NetworkDiagnosticsTestTags.CONTENT_LIST),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "network_and_auto_routing_card") {
                NetworkAndAutoRoutingCard(
                    networkSnapshot = networkSnapshot,
                    autoRoutingState = autoRoutingState,
                    recordedCandidate = recordedCandidate,
                    isServiceConfigured = viewModel.isServiceConfigured,
                    isRunning = isRunning,
                    autoOptimizeEnabled = autoOptimizeEnabled,
                    onAutoOptimizeChanged = { enabled ->
                        viewModel.onUserToggledAutoOptimize(enabled)
                        onAutoOptimizeChanged(enabled)
                    },
                    onResetAndReevaluate = { viewModel.resetDefaultAndReevaluateRoute() },
                )
            }

            item(key = "quick_check_card") {
                QuickCheckCard(
                    runState = runState,
                    isServiceConfigured = viewModel.isServiceConfigured,
                    isEinkMode = isEinkMode,
                    onStartQuickCheck = { viewModel.startQuickCheck() },
                    onStopActiveRun = { viewModel.stopActiveRunByUser() },
                    onCopySummary = { finishedReport?.let(copySummaryToClipboard) },
                    onOpenShareSheet = {
                        if (finishedReport != null) {
                            isPreShareSheetOpen = true
                        }
                    },
                )
            }

            if (displayedSamples.isNotEmpty()) {
                item(key = "sample_details_header") {
                    SampleDetailsExpandableHeader(
                        sampleCount = displayedSamples.size,
                        expanded = isSampleDetailsExpanded,
                        onToggle = { isSampleDetailsExpanded = !isSampleDetailsExpanded },
                    )
                }

                if (isSampleDetailsExpanded) {
                    items(
                        items = displayedSamples,
                        key = { sample -> "sample_${sample.sampleIndex}_${sample.armId}" },
                    ) { sample ->
                        SampleTelemetryRow(sample = sample)
                    }
                }
            }

            item(key = "advanced_comparison_lab") {
                AdvancedComparisonLabCard(
                    expanded = isLabExpanded,
                    onToggleExpanded = { isLabExpanded = !isLabExpanded },
                    isRunning = isRunning,
                    isServiceConfigured = viewModel.isServiceConfigured,
                    isReadOnlyApiSupported = viewModel.readOnlyApiCapability.isSupportedInCurrentBuild,
                    readOnlyApiResultMessage = readOnlyApiResultMessage,
                    onStartComparisonAxis = { axis -> viewModel.startComparisonLab(axis) },
                    onRunReadOnlyApiSample = {
                        coroutineScope.launch {
                            val result = viewModel.readOnlyApiCapability.runBoundedReadOnlyApiSample()
                            readOnlyApiResultMessage = result.fold(
                                onSuccess = { elapsedMs -> "OK · $elapsedMs ms" },
                                onFailure = { error -> error.message ?: "failed" },
                            )
                        }
                    },
                )
            }

            item(key = "usage_guide_card") {
                DiagnosticsUsageGuideCard()
            }
        }
    }

    if (isPreShareSheetOpen && finishedReport != null) {
        PreSharePrivacySheet(
            report = finishedReport,
            userNote = shareUserNote,
            onUserNoteChanged = { updated -> shareUserNote = updated.take(200) },
            onDismiss = { isPreShareSheetOpen = false },
            onConfirmExport = {
                val cleanNote = shareUserNote.trim().takeIf { it.isNotEmpty() }
                if (onExportReportOverride != null) {
                    onExportReportOverride(finishedReport, cleanNote)
                } else {
                    val chooserIntent = DiagnosticsReportShareHelper.createChooserShareIntent(
                        context = context,
                        report = finishedReport,
                        optionalUserNote = cleanNote,
                        chooserTitle = context.getString(R.string.diag_share_sheet_title),
                    )
                    context.startActivity(chooserIntent)
                }
                isPreShareSheetOpen = false
            },
        )
    }
}

@Composable
private fun DiagnosticsTopBar(
    finishedReport: DiagnosticsReport?,
    onNavigateBack: () -> Unit,
    onCopySummary: () -> Unit,
    onOpenShareSheet: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.extraSmall),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val backLabel = stringResource(R.string.action_back)
            TooltipIconButton(
                tooltipText = backLabel,
                onClick = onNavigateBack,
                modifier = Modifier.testTag(NetworkDiagnosticsTestTags.BACK_BUTTON),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = backLabel,
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(R.string.diag_title),
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (finishedReport != null) {
                val copySummaryLabel = stringResource(R.string.diag_action_copy_summary)
                TooltipIconButton(
                    tooltipText = copySummaryLabel,
                    onClick = onCopySummary,
                    modifier = Modifier.testTag(NetworkDiagnosticsTestTags.HEADER_COPY_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = copySummaryLabel,
                    )
                }
                val shareReportLabel = stringResource(R.string.diag_action_share_report)
                TooltipIconButton(
                    tooltipText = shareReportLabel,
                    onClick = onOpenShareSheet,
                    modifier = Modifier.testTag(NetworkDiagnosticsTestTags.HEADER_SHARE_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = shareReportLabel,
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkAndAutoRoutingCard(
    networkSnapshot: NetworkSnapshot,
    autoRoutingState: AutoRoutingModeState,
    recordedCandidate: RecordedCandidateSummary?,
    isServiceConfigured: Boolean,
    isRunning: Boolean,
    autoOptimizeEnabled: Boolean,
    onAutoOptimizeChanged: (Boolean) -> Unit,
    onResetAndReevaluate: () -> Unit,
) {
    val isStubUnavailable = !isServiceConfigured || autoRoutingState is AutoRoutingModeState.UnavailableStub
    val transportLabel = formatUnderlyingTransportLabel(networkSnapshot)
    val vpnLabel = if (networkSnapshot.isVpnActive) {
        stringResource(R.string.diag_vpn_active)
    } else {
        stringResource(R.string.diag_vpn_none)
    }
    val statusText = formatAutoRoutingStatusText(autoRoutingState, isServiceConfigured)

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.NetworkCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.diag_network_card_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                LabeledMetadataColumn(
                    label = stringResource(R.string.diag_label_transport),
                    value = transportLabel,
                    modifier = Modifier.weight(1f),
                )
                LabeledMetadataColumn(
                    label = stringResource(R.string.diag_label_vpn),
                    value = vpnLabel,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.diag_auto_opt_switch),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag(NetworkDiagnosticsTestTags.AUTO_OPTIMIZE_STATUS_TEXT),
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = autoOptimizeEnabled && !isStubUnavailable,
                    onCheckedChange = onAutoOptimizeChanged,
                    enabled = !isStubUnavailable,
                    modifier = Modifier.testTag(NetworkDiagnosticsTestTags.AUTO_OPTIMIZE_SWITCH),
                )
            }

            val activeIpState = (autoRoutingState as? AutoRoutingModeState.OptimizedRouteActive)
                ?.takeIf { !isStubUnavailable && it.selectedIpLiteral.isNotBlank() }
            if (activeIpState != null) {
                Spacer(modifier = Modifier.height(12.dp))
                RelayIpSelectionBanner(
                    isActive = true,
                    ipLiteral = activeIpState.selectedIpLiteral,
                    medianLatencyMs = activeIpState.medianLatencyMs,
                    ingressColo = activeIpState.ingressColo,
                )
            } else if (!isStubUnavailable && recordedCandidate != null) {
                Spacer(modifier = Modifier.height(12.dp))
                RelayIpSelectionBanner(
                    isActive = false,
                    ipLiteral = recordedCandidate.ipLiteral,
                    medianLatencyMs = recordedCandidate.medianLatencyMs,
                    ingressColo = recordedCandidate.ingressColo,
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = onResetAndReevaluate,
                enabled = !isRunning && !isStubUnavailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(NetworkDiagnosticsTestTags.RESET_REEVALUATE_BUTTON),
            ) {
                Icon(imageVector = Icons.Outlined.Restore, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.diag_action_reset_reevaluate))
            }
        }
    }
}

@Composable
private fun RelayIpSelectionBanner(
    isActive: Boolean,
    ipLiteral: String,
    medianLatencyMs: Long,
    ingressColo: String?,
) {
    val containerColor = if (isActive) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val contentColor = if (isActive) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val headline = if (isActive) {
        if (!ingressColo.isNullOrBlank()) {
            stringResource(
                R.string.diag_selected_ip_title_with_colo_format,
                ipLiteral,
                medianLatencyMs,
                ingressColo,
            )
        } else {
            stringResource(R.string.diag_selected_ip_title_format, ipLiteral, medianLatencyMs)
        }
    } else {
        if (!ingressColo.isNullOrBlank()) {
            stringResource(
                R.string.diag_recorded_ip_title_with_colo_format,
                ipLiteral,
                medianLatencyMs,
                ingressColo,
            )
        } else {
            stringResource(R.string.diag_recorded_ip_title_format, ipLiteral, medianLatencyMs)
        }
    }
    val subtitle = if (isActive) {
        stringResource(R.string.diag_selected_ip_scope_hint)
    } else {
        stringResource(R.string.diag_recorded_ip_toggle_hint)
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(
                if (isActive) {
                    NetworkDiagnosticsTestTags.SELECTED_IP_BANNER
                } else {
                    NetworkDiagnosticsTestTags.RECORDED_IP_BANNER
                },
            )
            .einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.testTag(
                    if (isActive) {
                        NetworkDiagnosticsTestTags.SELECTED_IP_TEXT
                    } else {
                        NetworkDiagnosticsTestTags.RECORDED_IP_TEXT
                    },
                ),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.85f),
            )
        }
    }
}

@Composable
private fun DiagnosticsUsageGuideCard() {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NetworkDiagnosticsTestTags.USAGE_GUIDE_CARD)
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.diag_usage_guide_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.diag_usage_guide_step1),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.diag_usage_guide_step2),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.diag_usage_guide_step3),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickCheckCard(
    runState: DiagnosticsRunUiState,
    isServiceConfigured: Boolean,
    isEinkMode: Boolean,
    onStartQuickCheck: () -> Unit,
    onStopActiveRun: () -> Unit,
    onCopySummary: () -> Unit,
    onOpenShareSheet: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.diag_quick_card_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(12.dp))

            when (runState) {
                DiagnosticsRunUiState.Idle -> {
                    Text(
                        text = stringResource(R.string.diag_quick_idle_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (!isServiceConfigured) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.diag_auto_status_unavailable_stub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onStartQuickCheck,
                        enabled = isServiceConfigured,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(NetworkDiagnosticsTestTags.START_QUICK_CHECK_BUTTON),
                    ) {
                        Icon(imageVector = Icons.Outlined.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(text = stringResource(R.string.diag_action_start_quick))
                    }
                }

                is DiagnosticsRunUiState.Running -> {
                    RunningProgressContent(
                        progress = runState.progress,
                        isEinkMode = isEinkMode,
                        onStopActiveRun = onStopActiveRun,
                    )
                }

                is DiagnosticsRunUiState.Finished -> {
                    FinishedSummaryContent(
                        report = runState.report,
                        isServiceConfigured = isServiceConfigured,
                        onCopySummary = onCopySummary,
                        onOpenShareSheet = onOpenShareSheet,
                        onRetest = onStartQuickCheck,
                    )
                }
            }
        }
    }
}

@Composable
private fun RunningProgressContent(
    progress: DiagnosticsLiveProgress,
    isEinkMode: Boolean,
    onStopActiveRun: () -> Unit,
) {
    val fraction = (progress.completedCount.toFloat() / progress.totalPlannedCount.coerceAtLeast(1))
        .coerceIn(0f, 1f)
    val currentFraction by rememberUpdatedState(fraction)

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(
                R.string.diag_running_progress_format,
                progress.completedCount,
                progress.totalPlannedCount,
                progress.succeededCount,
                progress.failedCount,
            ),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "${progress.elapsedMillis / 1_000L}s",
            style = MaterialTheme.typography.labelMedium,
            fontFamily = FontFamily.Monospace,
        )
    }

    Spacer(modifier = Modifier.height(12.dp))
    if (isEinkMode) {
        LinearProgressIndicator(
            progress = { currentFraction },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(NetworkDiagnosticsTestTags.FLAT_EINK_PROGRESS_INDICATOR),
        )
    } else {
        LinearWavyProgressIndicator(
            progress = { currentFraction },
            modifier = Modifier
                .fillMaxWidth()
                .testTag(NetworkDiagnosticsTestTags.WAVY_PROGRESS_INDICATOR),
        )
    }

    Spacer(modifier = Modifier.height(16.dp))
    FilledTonalButton(
        onClick = onStopActiveRun,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(NetworkDiagnosticsTestTags.STOP_ACTIVE_RUN_BUTTON),
    ) {
        Icon(imageVector = Icons.Outlined.Stop, contentDescription = null)
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(R.string.diag_action_stop_keep_partial))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FinishedSummaryContent(
    report: DiagnosticsReport,
    isServiceConfigured: Boolean,
    onCopySummary: () -> Unit,
    onOpenShareSheet: () -> Unit,
    onRetest: () -> Unit,
) {
    val summary = report.summary
    val fontScale = LocalDensity.current.fontScale

    Text(
        text = "${formatStopReasonHeadline(report.stopReason)} · ${report.baselineNetwork.wireTransportLabel()} · ${report.profileName}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
    )
    Spacer(modifier = Modifier.height(12.dp))

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val isCompactMetrics = maxWidth < 316.dp || fontScale > 1.1f
        val tilePadding = if (isCompactMetrics) 10.dp else 14.dp
        val minMaxFormatted = if (summary.minFullResponseMs == null || summary.maxFullResponseMs == null) {
            "—"
        } else if (isCompactMetrics) {
            "${summary.minFullResponseMs} / ${summary.maxFullResponseMs} ms"
        } else {
            "${summary.minFullResponseMs} ms / ${summary.maxFullResponseMs} ms"
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricTile(
                    label = stringResource(R.string.diag_metric_success_attempts),
                    value = stringResource(
                        R.string.diag_metric_success_value_format,
                        summary.succeededCount,
                        summary.attemptedCount,
                    ),
                    valueTestTag = NetworkDiagnosticsTestTags.METRIC_SUCCESS_VALUE,
                    contentPadding = tilePadding,
                    isCompact = isCompactMetrics,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                MetricTile(
                    label = stringResource(R.string.diag_metric_median_full),
                    value = DiagnosticsReport.formatNullableMillisForUi(summary.medianFullResponseMs),
                    valueTestTag = NetworkDiagnosticsTestTags.METRIC_MEDIAN_FULL_VALUE,
                    contentPadding = tilePadding,
                    isCompact = isCompactMetrics,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MetricTile(
                    label = stringResource(R.string.diag_metric_median_headers),
                    value = DiagnosticsReport.formatNullableMillisForUi(summary.medianHeadersStartMs),
                    valueTestTag = NetworkDiagnosticsTestTags.METRIC_MEDIAN_HEADERS_VALUE,
                    contentPadding = tilePadding,
                    isCompact = isCompactMetrics,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
                MetricTile(
                    label = stringResource(R.string.diag_metric_min_max),
                    value = minMaxFormatted,
                    valueTestTag = NetworkDiagnosticsTestTags.METRIC_MIN_MAX_VALUE,
                    contentPadding = tilePadding,
                    isCompact = isCompactMetrics,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(14.dp))
    Text(
        text = stringResource(R.string.diag_scope_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Spacer(modifier = Modifier.height(16.dp))
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = onCopySummary,
            modifier = Modifier.testTag(NetworkDiagnosticsTestTags.COPY_SUMMARY_BUTTON),
        ) {
            Icon(imageVector = Icons.Outlined.ContentCopy, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.diag_action_copy_summary))
        }
        FilledTonalButton(
            onClick = onOpenShareSheet,
            modifier = Modifier.testTag(NetworkDiagnosticsTestTags.SHARE_REPORT_BUTTON),
        ) {
            Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.diag_action_share_report))
        }
        Button(
            onClick = onRetest,
            enabled = isServiceConfigured,
            modifier = Modifier.testTag(NetworkDiagnosticsTestTags.RETEST_BUTTON),
        ) {
            Icon(imageVector = Icons.Outlined.Refresh, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text(text = stringResource(R.string.diag_action_retest))
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    valueTestTag: String,
    contentPadding: Dp = 14.dp,
    isCompact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = if (isCompact) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.testTag(valueTestTag),
            )
        }
    }
}

@Composable
private fun SampleDetailsExpandableHeader(
    sampleCount: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.medium)
            .clickable(onClick = onToggle)
            .testTag(NetworkDiagnosticsTestTags.DETAILS_TOGGLE_ROW),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.diag_details_toggle_format, sampleCount),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SampleTelemetryRow(sample: ProbeSampleResult) {
    val badgeText = when {
        !sample.succeeded -> stringResource(R.string.diag_sample_badge_failed)
        sample.connectionReused -> stringResource(R.string.diag_sample_badge_reused)
        else -> stringResource(R.string.diag_sample_badge_cold)
    }
    val badgeContainerColor = when {
        !sample.succeeded -> MaterialTheme.colorScheme.errorContainer
        sample.connectionReused -> MaterialTheme.colorScheme.secondaryContainer
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val badgeContentColor = when {
        !sample.succeeded -> MaterialTheme.colorScheme.onErrorContainer
        sample.connectionReused -> MaterialTheme.colorScheme.onSecondaryContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    val indexLabel = String.format(Locale.US, "#%02d", sample.sampleIndex)
    val primaryTimingText = if (sample.succeeded) {
        buildString {
            append("${sample.fullResponseElapsedMs} ms")
            sample.responseHeadersStartMs?.let { append(" (headers $it ms") }
            sample.dnsDurationMs?.let { append(" · DNS $it") }
            sample.tlsHandshakeDurationMs?.let { append(" · TLS $it") }
            if (sample.responseHeadersStartMs != null) append(")")
        }
    } else if (sample.httpStatusCode != null) {
        "— · ${sample.errorCategory?.wireName ?: "error"} (HTTP ${sample.httpStatusCode} · ${sample.fullResponseElapsedMs} ms)"
    } else {
        "— · ${sample.errorCategory?.wireName ?: "error"} (${sample.fullResponseElapsedMs} ms)"
    }

    val secondaryMetaParts = buildList {
        if (sample.armId != "default") add(sample.armId)
        sample.candidateId?.let { add(it) }
        sample.negotiatedProtocol?.let { add(it) }
        val localFam = sample.localSocketFamily?.wireLabel
        val observedFam = sample.serverPayload?.clientIpFamily?.wireLabel
        if (localFam != null || observedFam != null) {
            add("${localFam ?: "?"}/${observedFam ?: "?"}")
        }
        sample.serverPayload?.ingressColo?.let { colo ->
            add(stringResource(R.string.diag_sample_colo_format, colo))
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.medium)
            .testTag(NetworkDiagnosticsTestTags.SAMPLE_ROW_PREFIX + sample.sampleIndex),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                itemVerticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = indexLabel,
                    style = MaterialTheme.typography.labelLarge,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    softWrap = false,
                )
                Surface(
                    color = badgeContainerColor,
                    contentColor = badgeContentColor,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.einkHairlineBorder(MaterialTheme.shapes.small),
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
                Text(
                    text = primaryTimingText,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
            if (secondaryMetaParts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = secondaryMetaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun AdvancedComparisonLabCard(
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    isRunning: Boolean,
    isServiceConfigured: Boolean,
    isReadOnlyApiSupported: Boolean,
    readOnlyApiResultMessage: String?,
    onStartComparisonAxis: (ComparisonAxis) -> Unit,
    onRunReadOnlyApiSample: () -> Unit,
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
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
                    .testTag(NetworkDiagnosticsTestTags.LAB_TOGGLE_ROW),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Science,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.diag_lab_title),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                )
            }

            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val axisEnabled = !isRunning && isServiceConfigured
                    OutlinedButton(
                        onClick = { onStartComparisonAxis(ComparisonAxis.HAPPY_EYEBALLS_VS_SEQUENTIAL) },
                        enabled = axisEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                NetworkDiagnosticsTestTags.LAB_AXIS_BUTTON_PREFIX +
                                    ComparisonAxis.HAPPY_EYEBALLS_VS_SEQUENTIAL.name,
                            ),
                    ) {
                        Text(text = stringResource(R.string.diag_lab_happy_eyeballs))
                    }
                    OutlinedButton(
                        onClick = { onStartComparisonAxis(ComparisonAxis.IPV4_VS_IPV6) },
                        enabled = axisEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                NetworkDiagnosticsTestTags.LAB_AXIS_BUTTON_PREFIX +
                                    ComparisonAxis.IPV4_VS_IPV6.name,
                            ),
                    ) {
                        Text(text = stringResource(R.string.diag_lab_ipv4_ipv6))
                    }
                    OutlinedButton(
                        onClick = { onStartComparisonAxis(ComparisonAxis.HTTP2_VS_HTTP1_1) },
                        enabled = axisEnabled,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(
                                NetworkDiagnosticsTestTags.LAB_AXIS_BUTTON_PREFIX +
                                    ComparisonAxis.HTTP2_VS_HTTP1_1.name,
                            ),
                    ) {
                        Text(text = stringResource(R.string.diag_lab_h2_h1))
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    if (!isReadOnlyApiSupported) {
                        Text(
                            text = stringResource(R.string.diag_lab_api_unsupported_stub),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag(NetworkDiagnosticsTestTags.LAB_READ_ONLY_API_STATUS),
                        )
                    } else {
                        OutlinedButton(
                            onClick = onRunReadOnlyApiSample,
                            enabled = !isRunning,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(NetworkDiagnosticsTestTags.LAB_READ_ONLY_API_BUTTON),
                        ) {
                            Text(text = stringResource(R.string.diag_lab_api_check_action))
                        }
                        if (readOnlyApiResultMessage != null) {
                            Text(
                                text = readOnlyApiResultMessage,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.testTag(NetworkDiagnosticsTestTags.LAB_READ_ONLY_API_STATUS),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreSharePrivacySheet(
    report: DiagnosticsReport,
    userNote: String,
    onUserNoteChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirmExport: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val disclosedCountriesText = report.disclosedCountries.joinToString(", ").ifEmpty { "—" }
    val disclosedAsnsText = report.disclosedAsns.joinToString(", ") { "AS$it" }.ifEmpty { "—" }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.testTag(NetworkDiagnosticsTestTags.PRE_SHARE_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.diag_share_sheet_title),
                style = MaterialTheme.typography.titleLarge,
            )

            Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkHairlineBorder(MaterialTheme.shapes.medium),
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = stringResource(
                            R.string.diag_share_disclosed_network_format,
                            disclosedCountriesText,
                            disclosedAsnsText,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.testTag(NetworkDiagnosticsTestTags.PRE_SHARE_DISCLOSED_TEXT),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.diag_share_asn_notice),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Text(
                text = stringResource(R.string.diag_share_excluded_notice),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            OutlinedTextField(
                value = userNote,
                onValueChange = onUserNoteChanged,
                label = {
                    Text(
                        text = stringResource(R.string.diag_share_note_label),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(NetworkDiagnosticsTestTags.PRE_SHARE_NOTE_INPUT),
            )

            Button(
                onClick = onConfirmExport,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(NetworkDiagnosticsTestTags.PRE_SHARE_CONFIRM_BUTTON),
            ) {
                Icon(imageVector = Icons.Outlined.Share, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = stringResource(R.string.diag_share_confirm_export))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun LabeledMetadataColumn(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun formatUnderlyingTransportLabel(snapshot: NetworkSnapshot): String {
    if (!snapshot.isConnected) {
        return stringResource(R.string.diag_transport_offline)
    }
    if (snapshot.underlyingTransports.isEmpty()) {
        return stringResource(R.string.diag_transport_unknown)
    }
    val wifiLabel = if (snapshot.isMetered) {
        stringResource(R.string.diag_transport_wifi_metered)
    } else {
        stringResource(R.string.diag_transport_wifi_unmetered)
    }
    val cellularLabel = stringResource(R.string.diag_transport_cellular)
    val ethernetLabel = stringResource(R.string.diag_transport_ethernet)
    val unknownLabel = stringResource(R.string.diag_transport_unknown)

    return snapshot.underlyingTransports
        .map { kind ->
            when (kind) {
                UnderlyingTransportKind.WIFI -> wifiLabel
                UnderlyingTransportKind.CELLULAR -> cellularLabel
                UnderlyingTransportKind.ETHERNET -> ethernetLabel
                UnderlyingTransportKind.BLUETOOTH,
                UnderlyingTransportKind.OTHER,
                -> unknownLabel
            }
        }
        .distinct()
        .joinToString(" + ")
}

@Composable
private fun formatAutoRoutingStatusText(
    state: AutoRoutingModeState,
    isServiceConfigured: Boolean,
): String {
    if (!isServiceConfigured) {
        return stringResource(R.string.diag_auto_status_unavailable_stub)
    }
    return when (state) {
        AutoRoutingModeState.UnavailableStub ->
            stringResource(R.string.diag_auto_status_unavailable_stub)
        AutoRoutingModeState.DisabledByUser ->
            stringResource(R.string.diag_auto_status_disabled)
        AutoRoutingModeState.EnabledUsingDefault ->
            stringResource(R.string.diag_auto_status_default)
        AutoRoutingModeState.ComparingConnections ->
            stringResource(R.string.diag_auto_status_comparing)
        is AutoRoutingModeState.OptimizedRouteActive -> {
            if (state.promotedOnReliability || state.reductionPercent == null) {
                stringResource(R.string.diag_auto_status_optimized_reliability, state.candidateId)
            } else {
                stringResource(
                    R.string.diag_auto_status_optimized_speed,
                    state.candidateId,
                    state.confirmedSampleCount,
                    state.reductionPercent,
                )
            }
        }
        AutoRoutingModeState.RestoredDefaultAfterFallback ->
            stringResource(R.string.diag_auto_status_restored_default)
    }
}

private fun formatStopReasonHeadline(stopReason: DiagnosticsStopReason): String {
    return when (stopReason) {
        DiagnosticsStopReason.COMPLETED -> "Completed"
        DiagnosticsStopReason.USER_CANCELLED -> "Stopped (Partial)"
        DiagnosticsStopReason.BACKGROUNDED -> "Paused in Background"
        DiagnosticsStopReason.NETWORK_TRANSITION -> "Network Switched"
        DiagnosticsStopReason.RATE_LIMITED -> "Rate Limited"
        DiagnosticsStopReason.DIAGNOSTICS_DISABLED -> "Diagnostics Disabled"
        DiagnosticsStopReason.DEADLINE_EXCEEDED -> "Timed Out"
        DiagnosticsStopReason.OFFLINE -> "Offline"
    }
}

private tailrec fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}
