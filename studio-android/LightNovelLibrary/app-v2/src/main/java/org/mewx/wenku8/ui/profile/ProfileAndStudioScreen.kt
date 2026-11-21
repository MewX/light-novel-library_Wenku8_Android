package org.mewx.wenku8.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.FormatSize
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.SystemUpdateAlt
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.R
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.data.migration.LegacyV1StorageMigrator
import org.mewx.wenku8.data.remote.diagnostics.AutoRoutingModeState
import org.mewx.wenku8.data.remote.diagnostics.RecordedCandidateSummary
import org.mewx.wenku8.data.repository.AccountSessionState
import org.mewx.wenku8.data.repository.AppVersionCheckResult
import org.mewx.wenku8.data.repository.BackupArchiveRepository
import org.mewx.wenku8.data.repository.ServerNoticeAndUpdateRepository
import org.mewx.wenku8.data.repository.StorageAndCacheRepository
import org.mewx.wenku8.data.repository.UserSessionRepository
import org.mewx.wenku8.data.update.AppUpdateCoordinator
import org.mewx.wenku8.data.update.AppUpdateUiState
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.ui.navigation.MainScaffoldTestTags
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.util.Locale

/** Semantic UI test tags for Robolectric & instrumented `ProfileAndStudioScreen` tests. */
object ProfileStudioTestTags {
    const val LIST = "profile_studio_list"
    const val FOOTPRINT_CARD = "profile_footprint_card"
    const val OPEN_FOOTPRINT_STATS_BUTTON = "profile_open_footprint_stats_button"
    const val OPEN_FOOTPRINT_BOOKMARKS_BUTTON = "profile_open_footprint_bookmarks_button"
    const val AUTO_ROUTING_SWITCH = "profile_auto_routing_switch"
    const val SELECTED_IP_TEXT = "profile_selected_ip_text"
    const val RECORDED_IP_TEXT = "profile_recorded_ip_text"
    const val OPEN_DIAGNOSTICS_BUTTON = "profile_open_diagnostics_button"
    const val RESET_DEFAULT_ROUTE_BUTTON = "profile_reset_default_route_button"
    const val LANG_CHIP_SC = "profile_lang_chip_sc"
    const val LANG_CHIP_TC = "profile_lang_chip_tc"
    const val EINK_SWITCH = "profile_eink_switch"
    const val CONCISE_SWITCH = "profile_concise_switch"
    const val TYPOGRAPHY_PREVIEW_BOX = "profile_typography_preview_box"
    const val FONT_SIZE_SLIDER = "profile_font_size_slider"
    const val LINE_DIST_SLIDER = "profile_line_dist_slider"
    const val PARA_DIST_SLIDER = "profile_para_dist_slider"
    const val EDGE_DIST_SLIDER = "profile_edge_dist_slider"
    const val RESET_TYPOGRAPHY_BUTTON = "profile_reset_typography_button"
    const val VOLUME_PAGING_SWITCH = "profile_volume_paging_switch"
    const val FAST_CLEAN_BUTTON = "profile_fast_clean_button"
    const val DEEP_CLEAN_BUTTON = "profile_deep_clean_button"
    const val STORAGE_STATUS_BANNER = "profile_storage_status_banner"
    const val EXPORT_BACKUP_BUTTON = "profile_export_backup_button"
    const val IMPORT_BACKUP_BUTTON = "profile_import_backup_button"
    const val IMPORT_LEGACY_SAF_BUTTON = "profile_import_legacy_saf_button"
    const val OPEN_LICENSE_BUTTON = "profile_open_license_button"
    const val OPEN_ABOUT_BUTTON = "profile_open_about_button"
    const val REPORT_BUG_GITHUB_BUTTON = "profile_report_bug_github_button"
    const val FEEDBACK_EMAIL_BUTTON = "profile_feedback_email_button"
    const val ABOUT_VERSION_TAP_TARGET = "profile_about_version_tap_target"
    const val ABOUT_CHANNEL_TEXT = "profile_about_channel_text"
    const val ABOUT_HOMEPAGE_LINK = "profile_about_homepage_link"
    const val ABOUT_EASTER_EGG_TEXT = "profile_about_easter_egg_text"
    const val SUBSCREEN_ROOT = "profile_settings_subscreen_root"
    const val SUBSCREEN_LIST = "profile_settings_subscreen_list"
    const val SUBSCREEN_BACK_BUTTON = "profile_settings_subscreen_back"
    const val ENTRY_APPEARANCE = "profile_entry_appearance"
    const val ENTRY_READER_TYPOGRAPHY = "profile_entry_reader_typography"
    const val ENTRY_NETWORK = "profile_entry_network"
    const val ENTRY_STORAGE = "profile_entry_storage"
    const val ENTRY_UPDATE = "profile_entry_update"
    const val ENTRY_ABOUT = "profile_entry_about"
}

/**
 * Material 3 Expressive (`M3e`) `Tab 4 · 我的` Profile Hub & Segmented Settings Portal.
 *
 * Layout follows M3 Expressive "Personal Hub + Segmented Drill-Down Lists":
 * - **Upper Hub** (high-frequency personal status): offline banner, compact `NoticeString` banner,
 *   `AccountProfileCard` (`cert.wk8` / avatar / daily sign-in), and `ReadingFootprintCard`
 *   (`累计阅读` / `累计字数` / `书签笔记` + 1-tap Heatmap / History / Bookmarks).
 * - **Lower Segmented Settings** (3 M3e grouped lists with live current-value summaries):
 *   1. `偏好与阅读`: `外观、语言与启动页` & `阅读器默认排版与交互` (drill-down subscreens)
 *   2. `连接与数据`: `网络与中转连接` (Android Settings split master-switch row → `NetworkDiagnosticsScreen`)
 *      & `存储、缓存清理与存档管理` (drill-down subscreen)
 *   3. `应用与支持`: `检查新版本` (compact Play Flexible / Direct channel update row)
 *      & `反馈、开源许可与关于` (drill-down subscreen)
 */
@Composable
fun ProfileAndStudioScreen(
    preferences: Wenku8UserPreferences,
    onThemeModeSelected: (Wenku8ThemeMode) -> Unit,
    onDefaultHomeSelected: (DefaultHomeDestination) -> Unit,
    modifier: Modifier = Modifier,
    showOfflineFallbackBanner: Boolean = false,
    onDismissOfflineBanner: () -> Unit = {},
    totalReadingSeconds: Long = 0L,
    totalCharactersRead: Long = 0L,
    totalBookmarkNoteCount: Int = 0,
    onOpenReadingFootprint: (ReadingFootprintTab) -> Unit = {},
    autoRoutingState: AutoRoutingModeState = AutoRoutingModeState.EnabledUsingDefault,
    recordedCandidate: RecordedCandidateSummary? = null,
    onAutoOptimizeConnectionChanged: (Boolean) -> Unit = {},
    @Suppress("UNUSED_PARAMETER")
    onResetDefaultConnection: () -> Unit = {},
    onOpenNetworkDiagnostics: () -> Unit = {},
    onAppLanguageSelected: (Wenku8API.AppLanguage) -> Unit = {},
    onEinkModeChanged: (Boolean) -> Unit = {},
    onConciseModeChanged: (Boolean) -> Unit = {},
    onReaderTypographyChanged: (
        fontSizeSp: Int,
        lineDistanceDp: Int,
        paragraphDistanceDp: Int,
        pageEdgeDistanceDp: Int,
    ) -> Unit = { _, _, _, _ -> },
    onReaderCustomFontPathChanged: (String) -> Unit = {},
    onReaderCustomBackgroundPathChanged: (String) -> Unit = {},
    onVolumeKeyPagingChanged: (Boolean) -> Unit = {},
    userSessionRepository: UserSessionRepository? = null,
    serverNoticeAndUpdateRepository: ServerNoticeAndUpdateRepository? = null,
    appUpdateCoordinator: AppUpdateCoordinator? = null,
    storageAndCacheRepository: StorageAndCacheRepository? = null,
    backupArchiveRepository: BackupArchiveRepository? = null,
    legacyV1StorageMigrator: LegacyV1StorageMigrator? = null,
    onOpenLoginSheet: () -> Unit = {},
    onOpenSettingsSection: ((ProfileSettingsSection) -> Unit)? = null,
    reselectScrollToTopTrigger: Int = 0,
) {
    val coroutineScope = rememberCoroutineScope()
    val isEink = LocalEinkMode.current
    val isPlayStoreChannel = appUpdateCoordinator?.isPlayStoreChannel
        ?: (BuildConfig.DISTRIBUTION_CHANNEL == "playstore")

    var localOpenSection by rememberSaveable { mutableStateOf<ProfileSettingsSection?>(null) }
    val openSettingsSection: (ProfileSettingsSection) -> Unit = { section ->
        if (onOpenSettingsSection != null) {
            onOpenSettingsSection(section)
        } else {
            localOpenSection = section
        }
    }

    if (onOpenSettingsSection == null && localOpenSection != null) {
        ProfileSettingsSubscreen(
            section = localOpenSection!!,
            preferences = preferences,
            onNavigateBack = { localOpenSection = null },
            onThemeModeSelected = onThemeModeSelected,
            onDefaultHomeSelected = onDefaultHomeSelected,
            onAppLanguageSelected = onAppLanguageSelected,
            onEinkModeChanged = onEinkModeChanged,
            onConciseModeChanged = onConciseModeChanged,
            onReaderTypographyChanged = onReaderTypographyChanged,
            onReaderCustomFontPathChanged = onReaderCustomFontPathChanged,
            onReaderCustomBackgroundPathChanged = onReaderCustomBackgroundPathChanged,
            onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
            storageAndCacheRepository = storageAndCacheRepository,
            backupArchiveRepository = backupArchiveRepository,
            legacyV1StorageMigrator = legacyV1StorageMigrator,
            isPlayStoreChannel = isPlayStoreChannel,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    LaunchedEffect(serverNoticeAndUpdateRepository, preferences.appLanguage) {
        serverNoticeAndUpdateRepository?.loadAndRefreshNotice(preferences.appLanguage)
    }

    val activeNotice = if (serverNoticeAndUpdateRepository != null) {
        val state by serverNoticeAndUpdateRepository.activeNotice.collectAsStateWithLifecycle()
        state
    } else {
        null
    }
    val sessionState = if (userSessionRepository != null) {
        val state by userSessionRepository.sessionState.collectAsStateWithLifecycle()
        state
    } else {
        null
    }
    val appUpdateUiState: AppUpdateUiState? = if (appUpdateCoordinator != null) {
        val state by appUpdateCoordinator.uiState.collectAsStateWithLifecycle()
        state
    } else {
        null
    }

    val listState = rememberLazyListState()
    var handledReselectTrigger by remember { mutableIntStateOf(reselectScrollToTopTrigger) }
    LaunchedEffect(reselectScrollToTopTrigger) {
        if (reselectScrollToTopTrigger != handledReselectTrigger) {
            handledReselectTrigger = reselectScrollToTopTrigger
            if (isEink) {
                listState.scrollToItem(0)
            } else {
                listState.animateScrollToItem(0)
            }
        }
    }

    val density = LocalDensity.current
    val initialScreenWidthDp = LocalConfiguration.current.screenWidthDp.dp
    var listWidthDp by remember(initialScreenWidthDp) { mutableStateOf(initialScreenWidthDp) }
    val horizontalPadding = if (listWidthDp < 360.dp) 16.dp else 20.dp

    val appearanceSummary = buildAppearanceSummary(preferences)
    val readerSummary = buildReaderSummary(preferences)
    val isStubUnavailable = autoRoutingState == AutoRoutingModeState.UnavailableStub
    val networkSummary = buildNetworkSummary(
        autoRoutingState = autoRoutingState,
        recordedCandidate = recordedCandidate,
    )
    val showUpdateEntry = appUpdateUiState != null || serverNoticeAndUpdateRepository != null

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                if (size.width > 0) {
                    listWidthDp = with(density) { size.width.toDp() }
                }
            },
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showOfflineFallbackBanner) {
            item(key = "profile_offline_fallback_banner") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(MainScaffoldTestTags.OFFLINE_FALLBACK_BANNER)
                        .einkHairlineBorder(MaterialTheme.shapes.medium),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(imageVector = Icons.Outlined.CloudOff, contentDescription = null)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = stringResource(R.string.offline_fallback_bookshelf_banner),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        val dismissLabel = stringResource(R.string.action_dismiss_banner)
                        TooltipIconButton(
                            tooltipText = dismissLabel,
                            onClick = onDismissOfflineBanner,
                            modifier = Modifier.testTag(MainScaffoldTestTags.DISMISS_OFFLINE_BANNER_BUTTON),
                        ) {
                            Icon(imageVector = Icons.Outlined.Close, contentDescription = dismissLabel)
                        }
                    }
                }
            }
        }

        // 1. Server Announcement Banner (`NoticeString` SC / TC)
        if (activeNotice != null && serverNoticeAndUpdateRepository != null) {
            item(key = "profile_server_notice_banner") {
                ServerNoticeBannerCard(notice = activeNotice)
            }
        }

        // 2. Wenku8 Account Card (`cert.wk8` / Avatar / Daily Sign-In)
        if (userSessionRepository != null && sessionState != null) {
            item(key = "profile_account_card") {
                AccountProfileCard(
                    sessionState = sessionState,
                    registerPageUrl = userSessionRepository.registerPageUrl,
                    onOpenLoginSheet = onOpenLoginSheet,
                    onPerformDailySignIn = { userSessionRepository.performDailySignIn() },
                    onRefreshProfile = {
                        if (sessionState is AccountSessionState.OfflineWithSavedAccount) {
                            userSessionRepository.initializeSessionOnStartup()
                        } else {
                            userSessionRepository.refreshProfile()
                        }
                    },
                    onLogoutConfirmed = { userSessionRepository.logout() },
                )
            }
        }

        // 3. Reading Footprint & Bookmarks Summary Hub
        item(key = "profile_reading_footprint_card") {
            ReadingFootprintCard(
                totalReadingSeconds = totalReadingSeconds,
                totalCharactersRead = totalCharactersRead,
                totalBookmarkNoteCount = totalBookmarkNoteCount,
                onOpenReadingFootprint = onOpenReadingFootprint,
            )
        }

        // 4. MD3e Segmented Group 1: Preferences & Reader
        item(key = "profile_group_reading_preferences") {
            ProfileSegmentedListSection(
                title = stringResource(R.string.profile_group_reading_preferences),
            ) {
                ProfileSegmentedSettingRow(
                    index = 0,
                    count = 2,
                    icon = Icons.Outlined.Palette,
                    title = stringResource(R.string.profile_appearance_title),
                    subtitle = appearanceSummary,
                    onClick = { openSettingsSection(ProfileSettingsSection.APPEARANCE) },
                    modifier = Modifier.testTag(ProfileStudioTestTags.ENTRY_APPEARANCE),
                )
                ProfileSegmentedSettingRow(
                    index = 1,
                    count = 2,
                    icon = Icons.Outlined.FormatSize,
                    title = stringResource(R.string.profile_reader_studio_title),
                    subtitle = readerSummary,
                    onClick = { openSettingsSection(ProfileSettingsSection.READER_TYPOGRAPHY) },
                    modifier = Modifier.testTag(ProfileStudioTestTags.ENTRY_READER_TYPOGRAPHY),
                )
            }
        }

        // 5. MD3e Segmented Group 2: Connection & Data
        item(key = "profile_group_connection_data") {
            ProfileSegmentedListSection(
                title = stringResource(R.string.profile_group_connection_data),
            ) {
                ProfileSegmentedSwitchRow(
                    index = 0,
                    count = 2,
                    icon = Icons.Outlined.NetworkCheck,
                    title = stringResource(R.string.profile_network_card_title),
                    subtitle = networkSummary,
                    checked = preferences.autoOptimizeConnectionEnabled,
                    onCheckedChange = onAutoOptimizeConnectionChanged,
                    switchEnabled = !isStubUnavailable,
                    onRowClick = onOpenNetworkDiagnostics,
                    rowTestTag = ProfileStudioTestTags.OPEN_DIAGNOSTICS_BUTTON,
                    switchTestTag = ProfileStudioTestTags.AUTO_ROUTING_SWITCH,
                    modifier = Modifier.testTag(ProfileStudioTestTags.ENTRY_NETWORK),
                )
                ProfileSegmentedSettingRow(
                    index = 1,
                    count = 2,
                    icon = Icons.Outlined.Archive,
                    title = stringResource(R.string.profile_storage_title),
                    subtitle = stringResource(R.string.profile_summary_storage_default),
                    onClick = { openSettingsSection(ProfileSettingsSection.STORAGE_AND_BACKUP) },
                    modifier = Modifier.testTag(ProfileStudioTestTags.ENTRY_STORAGE),
                )
            }
        }

        // 6. MD3e Segmented Group 3: App Updates, Feedback & About
        item(key = "profile_group_app_support") {
            val groupCount = if (showUpdateEntry) 2 else 1
            ProfileSegmentedListSection(
                title = stringResource(R.string.profile_group_app_support),
            ) {
                if (showUpdateEntry) {
                    CompactAppUpdateSegmentedRow(
                        index = 0,
                        count = groupCount,
                        listWidthDp = listWidthDp,
                        appUpdateUiState = appUpdateUiState,
                        serverNoticeAndUpdateRepository = serverNoticeAndUpdateRepository,
                        isPlayStoreChannel = isPlayStoreChannel,
                        onManualCheckForUpdate = {
                            coroutineScope.launch {
                                appUpdateCoordinator?.checkForUpdate(isUserInitiated = true)
                            }
                        },
                        onCompleteAppUpdate = { appUpdateCoordinator?.completeUpdate() },
                    )
                }
                ProfileSegmentedSettingRow(
                    index = if (showUpdateEntry) 1 else 0,
                    count = groupCount,
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.profile_about_section_title),
                    subtitle = stringResource(
                        R.string.profile_summary_about_format,
                        BuildConfig.VERSION_NAME,
                    ),
                    onClick = { openSettingsSection(ProfileSettingsSection.ABOUT_AND_FEEDBACK) },
                    modifier = Modifier.testTag(ProfileStudioTestTags.ENTRY_ABOUT),
                )
            }
        }
    }
}

@Composable
private fun buildAppearanceSummary(preferences: Wenku8UserPreferences): String {
    val themeLabel = stringResource(preferences.themeMode.displayNameResId)
    val langLabel = stringResource(
        if (preferences.appLanguage == Wenku8API.AppLanguage.TC) {
            R.string.profile_lang_tc
        } else {
            R.string.profile_lang_sc
        },
    )
    val homeLabel = stringResource(preferences.defaultHomeDestination.optionLabelResId)
    val parts = buildList {
        add(themeLabel)
        add(langLabel)
        add(homeLabel)
        if (preferences.einkModeEnabled && preferences.themeMode != Wenku8ThemeMode.E_INK) {
            add(stringResource(R.string.profile_summary_eink_on))
        }
    }
    return parts.joinToString(" · ")
}

@Composable
private fun buildReaderSummary(preferences: Wenku8UserPreferences): String {
    val fontLabel = when (preferences.readerCustomFontPath) {
        "" -> stringResource(R.string.profile_reader_font_default)
        Wenku8UserPreferences.BUILTIN_SERIF_FONT_TOKEN -> stringResource(R.string.profile_reader_font_serif)
        else -> stringResource(R.string.profile_reader_font_custom_active)
    }
    val volumeLabel = stringResource(
        if (preferences.volumeKeyPagingEnabled) {
            R.string.profile_summary_volume_paging_on
        } else {
            R.string.profile_summary_volume_paging_off
        },
    )
    return stringResource(
        R.string.profile_summary_reader_format,
        preferences.readerFontSizeSp,
        fontLabel,
        volumeLabel,
    )
}

@Composable
private fun buildNetworkSummary(
    autoRoutingState: AutoRoutingModeState,
    recordedCandidate: RecordedCandidateSummary?,
): String {
    val isStubUnavailable = autoRoutingState == AutoRoutingModeState.UnavailableStub
    val activeIpState = (autoRoutingState as? AutoRoutingModeState.OptimizedRouteActive)
        ?.takeIf { !isStubUnavailable && it.selectedIpLiteral.isNotBlank() }
    if (activeIpState != null) {
        val base = stringResource(
            R.string.profile_summary_network_ip_format,
            activeIpState.selectedIpLiteral,
            activeIpState.medianLatencyMs,
        )
        return if (!activeIpState.ingressColo.isNullOrBlank()) {
            "$base · ${activeIpState.ingressColo}"
        } else {
            base
        }
    }
    if (!isStubUnavailable && recordedCandidate != null) {
        val base = stringResource(
            R.string.profile_summary_network_recorded_format,
            recordedCandidate.ipLiteral,
            recordedCandidate.medianLatencyMs,
        )
        return if (!recordedCandidate.ingressColo.isNullOrBlank()) {
            "$base · ${recordedCandidate.ingressColo}"
        } else {
            base
        }
    }
    return when (autoRoutingState) {
        AutoRoutingModeState.UnavailableStub -> stringResource(R.string.diag_auto_status_unavailable_stub)
        AutoRoutingModeState.DisabledByUser -> stringResource(R.string.diag_auto_status_disabled)
        AutoRoutingModeState.EnabledUsingDefault -> stringResource(R.string.diag_auto_status_default)
        AutoRoutingModeState.ComparingConnections -> stringResource(R.string.diag_auto_status_comparing)
        is AutoRoutingModeState.OptimizedRouteActive -> {
            if (autoRoutingState.promotedOnReliability) {
                stringResource(
                    R.string.diag_auto_status_optimized_reliability,
                    autoRoutingState.candidateId,
                )
            } else {
                stringResource(
                    R.string.diag_auto_status_optimized_speed,
                    autoRoutingState.candidateId,
                    autoRoutingState.confirmedSampleCount,
                    autoRoutingState.reductionPercent ?: 0,
                )
            }
        }
        AutoRoutingModeState.RestoredDefaultAfterFallback -> stringResource(R.string.diag_auto_status_restored_default)
    }
}

@Composable
private fun CompactAppUpdateSegmentedRow(
    index: Int,
    count: Int,
    listWidthDp: Dp,
    appUpdateUiState: AppUpdateUiState?,
    serverNoticeAndUpdateRepository: ServerNoticeAndUpdateRepository?,
    isPlayStoreChannel: Boolean,
    onManualCheckForUpdate: () -> Unit,
    onCompleteAppUpdate: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isEink = LocalEinkMode.current
    val fontScale = LocalDensity.current.fontScale
    val stackActionBelow = listWidthDp < 360.dp || fontScale > 1.15f

    if (appUpdateUiState != null) {
        val isHighlighted = appUpdateUiState is AppUpdateUiState.UpdateAvailable ||
            appUpdateUiState is AppUpdateUiState.ReadyToInstall
        val channelLabel = distributionChannelLabel(isPlayStoreChannel)
        val statusLine = when (appUpdateUiState) {
            AppUpdateUiState.Idle -> stringResource(
                R.string.update_idle_channel_format,
                BuildConfig.VERSION_NAME,
                BuildConfig.VERSION_CODE,
                channelLabel,
            )
            AppUpdateUiState.Checking -> stringResource(R.string.update_checking)
            is AppUpdateUiState.UpToDate -> stringResource(
                R.string.update_up_to_date_format,
                appUpdateUiState.currentVersionCode,
            )
            is AppUpdateUiState.UpdateAvailable -> stringResource(
                R.string.update_available_card_format,
                appUpdateUiState.targetVersionCode,
            )
            is AppUpdateUiState.Downloading -> {
                val fraction = appUpdateUiState.progressFraction
                if (fraction != null) {
                    stringResource(R.string.update_downloading_percent_format, (fraction * 100f).toInt())
                } else {
                    stringResource(R.string.update_downloading)
                }
            }
            AppUpdateUiState.ReadyToInstall -> stringResource(R.string.update_ready_to_install)
            is AppUpdateUiState.Failed -> if (appUpdateUiState.canOpenPlayStoreFallback) {
                stringResource(R.string.update_play_check_failed)
            } else {
                stringResource(R.string.update_check_failed)
            }
        }

        val triggerUpdateAction: () -> Unit = {
            when (appUpdateUiState) {
                AppUpdateUiState.ReadyToInstall -> onCompleteAppUpdate()
                is AppUpdateUiState.UpdateAvailable -> {
                    if (appUpdateUiState.isPlayStoreManaged) {
                        onManualCheckForUpdate()
                    } else {
                        val url = appUpdateUiState.downloadPageUrl.orEmpty()
                        if (url.isNotBlank()) {
                            openSafeBrowserUrl(context, url)
                        } else {
                            onManualCheckForUpdate()
                        }
                    }
                }
                AppUpdateUiState.Checking, is AppUpdateUiState.Downloading -> Unit
                else -> onManualCheckForUpdate()
            }
        }

        val hasInlinePrimaryAction = appUpdateUiState is AppUpdateUiState.ReadyToInstall ||
            appUpdateUiState is AppUpdateUiState.UpdateAvailable

        val primaryActionButton: @Composable () -> Unit = {
            when (appUpdateUiState) {
                AppUpdateUiState.ReadyToInstall -> {
                    Button(
                        onClick = onCompleteAppUpdate,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag(ProfileAccountTestTags.UPDATE_RESTART_INSTALL_BUTTON),
                    ) {
                        Text(
                            text = stringResource(R.string.update_action_restart_install),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                is AppUpdateUiState.UpdateAvailable -> {
                    Button(
                        onClick = triggerUpdateAction,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag(ProfileAccountTestTags.CHECK_UPDATE_BUTTON),
                    ) {
                        Text(
                            text = stringResource(
                                if (appUpdateUiState.isPlayStoreManaged) {
                                    R.string.update_action_update_now
                                } else {
                                    R.string.update_action_download
                                },
                            ),
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                else -> Unit
            }
        }

        val isRowEnabled = appUpdateUiState !is AppUpdateUiState.Checking &&
            appUpdateUiState !is AppUpdateUiState.Downloading

        val showBottomContent = (hasInlinePrimaryAction && stackActionBelow) ||
            appUpdateUiState is AppUpdateUiState.Downloading ||
            (appUpdateUiState is AppUpdateUiState.Failed && appUpdateUiState.canOpenPlayStoreFallback)

        ProfileSegmentedSettingRow(
            index = index,
            count = count,
            icon = Icons.Outlined.SystemUpdateAlt,
            title = stringResource(R.string.update_action_check),
            subtitle = statusLine,
            enabled = isRowEnabled,
            highlighted = isHighlighted && !isEink,
            onClick = triggerUpdateAction,
            modifier = Modifier.testTag(ProfileStudioTestTags.ENTRY_UPDATE),
            trailingContent = when {
                appUpdateUiState is AppUpdateUiState.Checking -> {
                    {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                hasInlinePrimaryAction && !stackActionBelow -> primaryActionButton
                appUpdateUiState is AppUpdateUiState.Downloading -> {
                    { Spacer(modifier = Modifier.size(1.dp)) }
                }
                else -> null
            },
            bottomContent = if (showBottomContent) {
                {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (hasInlinePrimaryAction && stackActionBelow) {
                            primaryActionButton()
                        }
                        if (appUpdateUiState is AppUpdateUiState.Downloading) {
                            val fraction = appUpdateUiState.progressFraction
                            if (fraction != null) {
                                LinearProgressIndicator(
                                    progress = { fraction },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(ProfileAccountTestTags.UPDATE_DOWNLOAD_PROGRESS),
                                )
                            } else {
                                LinearProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag(ProfileAccountTestTags.UPDATE_DOWNLOAD_PROGRESS),
                                )
                            }
                        }
                        if (appUpdateUiState is AppUpdateUiState.Failed &&
                            appUpdateUiState.canOpenPlayStoreFallback
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.End,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                TextButton(
                                    onClick = { openPlayStoreListing(context) },
                                    modifier = Modifier.testTag(ProfileAccountTestTags.UPDATE_PLAY_FALLBACK_BUTTON),
                                ) {
                                    Text(text = stringResource(R.string.update_action_open_play_store))
                                }
                            }
                        }
                    }
                }
            } else {
                null
            },
        )
        return
    }

    if (serverNoticeAndUpdateRepository != null) {
        var isChecking by remember { mutableStateOf(false) }
        var statusLine by rememberSaveable { mutableStateOf<String?>(null) }
        var pendingUpdate by remember { mutableStateOf<AppVersionCheckResult.UpdateAvailable?>(null) }
        val checkingLabel = stringResource(R.string.update_checking)
        val failedLabel = stringResource(R.string.update_check_failed)
        val defaultSubtitle = stringResource(
            R.string.update_idle_channel_format,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
            distributionChannelLabel(isPlayStoreChannel),
        )

        ProfileSegmentedSettingRow(
            index = index,
            count = count,
            icon = Icons.Outlined.SystemUpdateAlt,
            title = stringResource(R.string.update_action_check),
            subtitle = statusLine ?: defaultSubtitle,
            enabled = !isChecking,
            onClick = {
                scope.launch {
                    isChecking = true
                    statusLine = checkingLabel
                    try {
                        when (val res = serverNoticeAndUpdateRepository.checkForAppUpdate()) {
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
            modifier = Modifier.testTag(ProfileStudioTestTags.ENTRY_UPDATE),
            trailingContent = if (isChecking) {
                {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }
            } else {
                null
            },
        )

        pendingUpdate?.let { update ->
            DirectUpdateAvailableDialog(
                currentVersionCode = update.currentVersionCode,
                latestVersionCode = update.latestVersionCode,
                downloadPageUrl = update.downloadPageUrl,
                onDismiss = { pendingUpdate = null },
                onOpenDownloadUrl = { url ->
                    pendingUpdate = null
                    openSafeBrowserUrl(context, url)
                },
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReadingFootprintCard(
    totalReadingSeconds: Long,
    totalCharactersRead: Long,
    totalBookmarkNoteCount: Int,
    onOpenReadingFootprint: (ReadingFootprintTab) -> Unit,
) {
    val totalMinutes = (totalReadingSeconds / 60L).coerceAtLeast(0L)
    Card(
        onClick = { onOpenReadingFootprint(ReadingFootprintTab.STATS) },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(ProfileStudioTestTags.FOOTPRINT_CARD)
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = stringResource(R.string.profile_footprint_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(14.dp))
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val isCompactCard = maxWidth < 316.dp
                val tileHorizontalPadding = if (isCompactCard) 8.dp else 12.dp
                val useCompactWrappingActions = isCompactCard || LocalDensity.current.fontScale > 1.05f
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        FootprintMetricTile(
                            valueText = stringResource(R.string.profile_footprint_minutes_format, totalMinutes),
                            labelText = stringResource(R.string.profile_footprint_minutes_label),
                            horizontalPadding = tileHorizontalPadding,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        FootprintMetricTile(
                            valueText = formatCompactCount(totalCharactersRead),
                            labelText = stringResource(R.string.profile_footprint_chars_label),
                            horizontalPadding = tileHorizontalPadding,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        FootprintMetricTile(
                            valueText = totalBookmarkNoteCount.toString(),
                            labelText = stringResource(R.string.profile_footprint_notes_label),
                            horizontalPadding = tileHorizontalPadding,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    if (useCompactWrappingActions) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenReadingFootprint(ReadingFootprintTab.STATS) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.testTag(ProfileStudioTestTags.OPEN_FOOTPRINT_STATS_BUTTON),
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_footprint_open_stats),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            OutlinedButton(
                                onClick = { onOpenReadingFootprint(ReadingFootprintTab.BOOKMARKS) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                                modifier = Modifier.testTag(ProfileStudioTestTags.OPEN_FOOTPRINT_BOOKMARKS_BUTTON),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.profile_footprint_open_bookmarks_format,
                                        totalBookmarkNoteCount,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            FilledTonalButton(
                                onClick = { onOpenReadingFootprint(ReadingFootprintTab.STATS) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(ProfileStudioTestTags.OPEN_FOOTPRINT_STATS_BUTTON),
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_footprint_open_stats),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                            OutlinedButton(
                                onClick = { onOpenReadingFootprint(ReadingFootprintTab.BOOKMARKS) },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag(ProfileStudioTestTags.OPEN_FOOTPRINT_BOOKMARKS_BUTTON),
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.profile_footprint_open_bookmarks_format,
                                        totalBookmarkNoteCount,
                                    ),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FootprintMetricTile(
    valueText: String,
    labelText: String,
    horizontalPadding: Dp = 12.dp,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                textAlign = TextAlign.Center,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

internal fun formatByteSize(bytes: Long): String {
    val safe = bytes.coerceAtLeast(0L)
    if (safe < 1024L) return "$safe B"
    val kb = safe / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

internal fun formatCompactCount(count: Long): String {
    val safe = count.coerceAtLeast(0L)
    return if (safe >= 10_000L) {
        String.format(Locale.US, "%.1fw", safe / 10_000.0)
    } else {
        safe.toString()
    }
}
