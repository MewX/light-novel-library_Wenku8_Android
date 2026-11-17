package org.mewx.wenku8.ui.profile

import android.graphics.Typeface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.R
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.data.migration.DocumentTreeSaveSource
import org.mewx.wenku8.data.migration.LegacyV1StorageMigrator
import org.mewx.wenku8.data.remote.diagnostics.AutoRoutingModeState
import org.mewx.wenku8.data.repository.AccountSessionState
import org.mewx.wenku8.data.repository.BackupArchiveMode
import org.mewx.wenku8.data.repository.BackupArchiveRepository
import org.mewx.wenku8.data.repository.CustomAssetSlot
import org.mewx.wenku8.data.repository.DeepCleanProgress
import org.mewx.wenku8.data.repository.ServerNoticeAndUpdateRepository
import org.mewx.wenku8.data.repository.StorageAndCacheRepository
import org.mewx.wenku8.data.repository.UserSessionRepository
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.ui.navigation.MainScaffoldTestTags
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt

/** Semantic UI test tags for Robolectric & instrumented `ProfileAndStudioScreen` tests. */
object ProfileStudioTestTags {
    const val LIST = "profile_studio_list"
    const val FOOTPRINT_CARD = "profile_footprint_card"
    const val OPEN_FOOTPRINT_STATS_BUTTON = "profile_open_footprint_stats_button"
    const val OPEN_FOOTPRINT_BOOKMARKS_BUTTON = "profile_open_footprint_bookmarks_button"
    const val AUTO_ROUTING_SWITCH = "profile_auto_routing_switch"
    const val OPEN_DIAGNOSTICS_BUTTON = "profile_open_diagnostics_button"
    const val RESET_DEFAULT_ROUTE_BUTTON = "profile_reset_default_route_button"
    const val LANG_CHIP_SC = "profile_lang_chip_sc"
    const val LANG_CHIP_TC = "profile_lang_chip_tc"
    const val EINK_SWITCH = "profile_eink_switch"
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
    const val ABOUT_VERSION_TAP_TARGET = "profile_about_version_tap_target"
    const val ABOUT_HOMEPAGE_LINK = "profile_about_homepage_link"
    const val ABOUT_EASTER_EGG_TEXT = "profile_about_easter_egg_text"
}

/**
 * Full Material 3 Expressive (`M3e`) `Tab 4 · 我的` Profile, Settings Studio & Archive Hub.
 *
 * - Persistent `NoticeString` announcement banner + `AccountProfileCard` (`cert.wk8` / daily sign-in / avatar)
 * - Reading Footprint summary (`累计阅读` / `累计字数` / `书签笔记`) + 1-tap Heatmap / History / Bookmarks Hub
 * - Network & Auto-Routing quick controls (`自动优化连接` toggle + status + `网络检测` + `恢复默认连接`)
 * - Language (`SC` / `TC`), Startup Home (`最新` / `书架`), Theme (`6` modes, where `跟随系统` unifies
 *   system Day/Night + Android 12+ Material You wallpaper colors), and E-Ink high-contrast
 * - Reader default typography (`8..36 sp`, line `0..32 dp`, paragraph `0..48 dp`, edge `0..32 dp`,
 *   multi-paragraph live preview, custom `.ttf`/`.otf` font, custom day paper image, volume-key paging)
 * - Fast & cancellable Deep Orphan Cache Cleanup, `.wk8backup.zip` Light/Full Export & Smart-Merge
 *   Import, and external `v1.x` SAF folder import
 * - App Update Check (`CheckAppNewVersion`), Open-Source License (`R.raw.license`), and legacy-aligned
 *   About dialog with 5-tap version Easter egg.
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
    onAutoOptimizeConnectionChanged: (Boolean) -> Unit = {},
    onResetDefaultConnection: () -> Unit = {},
    onOpenNetworkDiagnostics: () -> Unit = {},
    onAppLanguageSelected: (Wenku8API.AppLanguage) -> Unit = {},
    onEinkModeChanged: (Boolean) -> Unit = {},
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
    storageAndCacheRepository: StorageAndCacheRepository? = null,
    backupArchiveRepository: BackupArchiveRepository? = null,
    legacyV1StorageMigrator: LegacyV1StorageMigrator? = null,
    onOpenLoginSheet: () -> Unit = {},
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

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

    var readerAssetErrorText by rememberSaveable { mutableStateOf<String?>(null) }
    var storageStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var deepCleanProgress by remember { mutableStateOf<DeepCleanProgress?>(null) }
    var activeDeepCleanJob by remember { mutableStateOf<Job?>(null) }

    var showBackupModePickerDialog by rememberSaveable { mutableStateOf(false) }
    var pendingExportMode by rememberSaveable { mutableStateOf(BackupArchiveMode.LIGHT_SAVE) }
    var showLicenseDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    val fontInvalidMessage = stringResource(R.string.profile_reader_font_invalid)
    val backupFailedMessage = stringResource(R.string.profile_backup_failed)

    // SAF Launchers for Custom Assets, .wk8backup.zip Export/Import & Legacy v1.x Tree Import

    val customReaderFontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && storageAndCacheRepository != null) {
            coroutineScope.launch {
                readerAssetErrorText = null
                storageAndCacheRepository.importCustomAsset(
                    contentResolver = context.contentResolver,
                    sourceUri = uri,
                    slot = CustomAssetSlot.READER_FONT,
                ).onSuccess { savedFile ->
                    onReaderCustomFontPathChanged(savedFile.absolutePath)
                }.onFailure {
                    readerAssetErrorText = fontInvalidMessage
                }
            }
        }
    }

    val customReaderBgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && storageAndCacheRepository != null) {
            coroutineScope.launch {
                readerAssetErrorText = null
                storageAndCacheRepository.importCustomAsset(
                    contentResolver = context.contentResolver,
                    sourceUri = uri,
                    slot = CustomAssetSlot.READER_BACKGROUND,
                ).onSuccess { savedFile ->
                    onReaderCustomBackgroundPathChanged(savedFile.absolutePath)
                }
            }
        }
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        if (uri != null && backupArchiveRepository != null) {
            val chosenMode = pendingExportMode
            coroutineScope.launch {
                val outStream = withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)
                }
                if (outStream == null) {
                    storageStatusMessage = backupFailedMessage
                    return@launch
                }
                outStream.use { stream ->
                    backupArchiveRepository.exportBackupArchive(
                        mode = chosenMode,
                        destinationStream = stream,
                    ).onSuccess { summary ->
                        storageStatusMessage = context.getString(
                            R.string.profile_backup_export_success_format,
                            summary.suggestedFileName,
                            summary.includedImageFiles,
                        )
                    }.onFailure {
                        storageStatusMessage = backupFailedMessage
                    }
                }
            }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && backupArchiveRepository != null) {
            coroutineScope.launch {
                val inStream = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                }
                if (inStream == null) {
                    storageStatusMessage = backupFailedMessage
                    return@launch
                }
                inStream.use { stream ->
                    backupArchiveRepository.importBackupArchiveSmartMerge(stream)
                        .onSuccess { summary ->
                            storageStatusMessage = context.getString(
                                R.string.profile_backup_import_success_format,
                                summary.mergedShelfNovels,
                                summary.mergedReadingProgress,
                                summary.mergedBookmarksNotes,
                                summary.mergedOfflineChapters,
                            )
                        }
                        .onFailure {
                            storageStatusMessage = backupFailedMessage
                        }
                }
            }
        }
    }

    val importLegacyTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { treeUri ->
        if (treeUri != null && legacyV1StorageMigrator != null) {
            coroutineScope.launch {
                val treeRoot = DocumentFile.fromTreeUri(context, treeUri)
                if (treeRoot == null) {
                    storageStatusMessage = backupFailedMessage
                    return@launch
                }
                runCatching {
                    val source = DocumentTreeSaveSource(
                        context = context,
                        treeRoot = treeRoot,
                        internalFilesDir = context.filesDir,
                    )
                    legacyV1StorageMigrator.migrateFromSource(source = source)
                }.onSuccess { report ->
                    storageStatusMessage = context.getString(
                        R.string.profile_legacy_saf_import_success_format,
                        report.migratedBookshelfNovelsCount,
                        report.migratedChaptersCount,
                        report.migratedReadingProgressCount,
                    )
                }.onFailure {
                    storageStatusMessage = backupFailedMessage
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
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
                ServerNoticeBannerCard(
                    notice = activeNotice,
                )
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

        // 3. Reading Footprint & Bookmarks Summary
        item(key = "profile_reading_footprint_card") {
            ReadingFootprintCard(
                totalReadingSeconds = totalReadingSeconds,
                totalCharactersRead = totalCharactersRead,
                totalBookmarkNoteCount = totalBookmarkNoteCount,
                onOpenReadingFootprint = onOpenReadingFootprint,
            )
        }

        // 4. Network & Auto-Optimize Connection Studio Card
        item(key = "profile_network_routing_card") {
            NetworkRoutingStudioCard(
                autoOptimizeEnabled = preferences.autoOptimizeConnectionEnabled,
                autoRoutingState = autoRoutingState,
                onAutoOptimizeChanged = onAutoOptimizeConnectionChanged,
                onOpenDiagnostics = onOpenNetworkDiagnostics,
                onResetDefaultConnection = onResetDefaultConnection,
            )
        }

        // 5. Appearance, Language & Startup Home Card
        item(key = "profile_appearance_and_startup_card") {
            AppearanceAndStartupStudioCard(
                preferences = preferences,
                onDefaultHomeSelected = onDefaultHomeSelected,
                onAppLanguageSelected = onAppLanguageSelected,
                onThemeModeSelected = onThemeModeSelected,
                onEinkModeChanged = onEinkModeChanged,
            )
        }

        // 7. Reader Default Typography, Custom Font/Paper & Volume Paging Card
        item(key = "profile_reader_typography_card") {
            ReaderDefaultTypographyCard(
                preferences = preferences,
                assetErrorText = readerAssetErrorText,
                onTypographyChanged = onReaderTypographyChanged,
                onResetTypography = {
                    onReaderTypographyChanged(
                        Wenku8UserPreferences.DEFAULT_READER_FONT_SIZE_SP,
                        Wenku8UserPreferences.DEFAULT_READER_LINE_DISTANCE_DP,
                        Wenku8UserPreferences.DEFAULT_READER_PARAGRAPH_DISTANCE_DP,
                        Wenku8UserPreferences.DEFAULT_READER_PAGE_EDGE_DISTANCE_DP,
                    )
                },
                onResetCustomFont = {
                    readerAssetErrorText = null
                    onReaderCustomFontPathChanged("")
                },
                onPickCustomFont = {
                    customReaderFontLauncher.launch(arrayOf("*/*"))
                },
                onResetCustomBackground = {
                    readerAssetErrorText = null
                    onReaderCustomBackgroundPathChanged("")
                },
                onPickCustomBackground = {
                    customReaderBgLauncher.launch(arrayOf("image/*"))
                },
                onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
            )
        }

        // 8. Storage, Two-Mode Cache Clean, .wk8backup.zip & Legacy v1.x SAF Import Card
        item(key = "profile_storage_and_backup_card") {
            StorageAndBackupStudioCard(
                statusMessage = storageStatusMessage,
                isCacheCleanAvailable = storageAndCacheRepository != null,
                isBackupAvailable = backupArchiveRepository != null,
                isLegacySafAvailable = legacyV1StorageMigrator != null,
                onRunFastClean = {
                    val repo = storageAndCacheRepository ?: return@StorageAndBackupStudioCard
                    coroutineScope.launch {
                        val summary = repo.runFastCacheClean()
                        storageStatusMessage = context.getString(
                            R.string.profile_cache_result_format,
                            summary.deletedFileCount,
                            formatByteSize(summary.reclaimedBytes),
                        )
                    }
                },
                onRunDeepClean = {
                    val repo = storageAndCacheRepository ?: return@StorageAndBackupStudioCard
                    if (activeDeepCleanJob?.isActive == true) return@StorageAndBackupStudioCard
                    val job = coroutineScope.launch {
                        try {
                            val summary = repo.runDeepOrphanCacheClean { progress ->
                                deepCleanProgress = progress
                            }
                            storageStatusMessage = context.getString(
                                R.string.profile_cache_result_format,
                                summary.deletedFileCount,
                                formatByteSize(summary.reclaimedBytes),
                            )
                        } finally {
                            deepCleanProgress = null
                            activeDeepCleanJob = null
                        }
                    }
                    activeDeepCleanJob = job
                },
                onRequestExportBackup = {
                    showBackupModePickerDialog = true
                },
                onRequestImportBackup = {
                    importBackupLauncher.launch(
                        arrayOf("application/zip", "application/octet-stream", "*/*"),
                    )
                },
                onRequestImportLegacySaf = {
                    importLegacyTreeLauncher.launch(null)
                },
            )
        }

        // 9. App Update Check (`CheckAppNewVersion`), Open-Source License & About Card
        item(key = "profile_about_and_license_card") {
            AboutAndLicenseSection(
                serverNoticeAndUpdateRepository = serverNoticeAndUpdateRepository,
                onOpenLicense = { showLicenseDialog = true },
                onOpenAbout = { showAboutDialog = true },
            )
        }
    }

    // Cancellable Deep Orphan Clean Progress Dialog
    val runningDeepProgress = deepCleanProgress
    if (runningDeepProgress != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text(text = stringResource(R.string.profile_cache_deep_running_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(
                            R.string.profile_cache_deep_progress_format,
                            runningDeepProgress.scannedFiles,
                            runningDeepProgress.totalFiles,
                            runningDeepProgress.deletedOrphanFiles,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    if (runningDeepProgress.totalFiles > 0) {
                        LinearProgressIndicator(
                            progress = {
                                (runningDeepProgress.scannedFiles.toFloat() / runningDeepProgress.totalFiles.toFloat())
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        activeDeepCleanJob?.cancel()
                        activeDeepCleanJob = null
                        deepCleanProgress = null
                    },
                ) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }

    // Backup Export Mode Selection Dialog (`LIGHT_SAVE` vs `FULL_ARCHIVE`)
    if (showBackupModePickerDialog) {
        BackupExportModeDialog(
            onSelectMode = { selectedMode ->
                showBackupModePickerDialog = false
                pendingExportMode = selectedMode
                exportBackupLauncher.launch(BackupArchiveRepository.suggestedFileName(selectedMode))
            },
            onDismiss = { showBackupModePickerDialog = false },
        )
    }

    // Open-Source License Dialog (`R.raw.license`)
    if (showLicenseDialog) {
        OpenSourceLicenseDialog(onDismiss = { showLicenseDialog = false })
    }

    // About Wenku8 v2 Dialog (with 5-tap Easter egg)
    if (showAboutDialog) {
        AboutWenkuDialog(onDismiss = { showAboutDialog = false })
    }
}

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FootprintMetricTile(
                    valueText = stringResource(R.string.profile_footprint_minutes_format, totalMinutes),
                    labelText = stringResource(R.string.profile_footprint_minutes_label),
                    modifier = Modifier.weight(1f),
                )
                FootprintMetricTile(
                    valueText = formatCompactCount(totalCharactersRead),
                    labelText = stringResource(R.string.profile_footprint_chars_label),
                    modifier = Modifier.weight(1f),
                )
                FootprintMetricTile(
                    valueText = totalBookmarkNoteCount.toString(),
                    labelText = stringResource(R.string.profile_footprint_notes_label),
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
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
                    Text(text = stringResource(R.string.profile_footprint_open_stats))
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
                    )
                }
            }
        }
    }
}

@Composable
private fun FootprintMetricTile(
    valueText: String,
    labelText: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = modifier.einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = valueText,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = labelText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NetworkRoutingStudioCard(
    autoOptimizeEnabled: Boolean,
    autoRoutingState: AutoRoutingModeState,
    onAutoOptimizeChanged: (Boolean) -> Unit,
    onOpenDiagnostics: () -> Unit,
    onResetDefaultConnection: () -> Unit,
) {
    val statusText = when (autoRoutingState) {
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

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_network_card_title),
                style = MaterialTheme.typography.titleMedium,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.diag_auto_opt_switch),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = autoOptimizeEnabled,
                    onCheckedChange = onAutoOptimizeChanged,
                    enabled = autoRoutingState != AutoRoutingModeState.UnavailableStub,
                    modifier = Modifier.testTag(ProfileStudioTestTags.AUTO_ROUTING_SWITCH),
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onOpenDiagnostics,
                    modifier = Modifier.testTag(ProfileStudioTestTags.OPEN_DIAGNOSTICS_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.NetworkCheck,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.profile_network_open_diag))
                }

                OutlinedButton(
                    onClick = onResetDefaultConnection,
                    enabled = autoRoutingState != AutoRoutingModeState.UnavailableStub,
                    modifier = Modifier.testTag(ProfileStudioTestTags.RESET_DEFAULT_ROUTE_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.RestartAlt,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.profile_network_reset_default))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceAndStartupStudioCard(
    preferences: Wenku8UserPreferences,
    onDefaultHomeSelected: (DefaultHomeDestination) -> Unit,
    onAppLanguageSelected: (Wenku8API.AppLanguage) -> Unit,
    onThemeModeSelected: (Wenku8ThemeMode) -> Unit,
    onEinkModeChanged: (Boolean) -> Unit,
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
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_appearance_title),
                style = MaterialTheme.typography.titleMedium,
            )

            // Default Home Destination (`LATEST` vs `BOOKSHELF`)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.setting_default_home_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DefaultHomeDestination.entries.forEach { option ->
                        FilterChip(
                            selected = option == preferences.defaultHomeDestination,
                            onClick = { onDefaultHomeSelected(option) },
                            label = { Text(stringResource(option.optionLabelResId)) },
                        )
                    }
                }
            }

            // Content & UI Language (`SC` / `TC`)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.profile_lang_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = preferences.appLanguage == Wenku8API.AppLanguage.SC,
                        onClick = { onAppLanguageSelected(Wenku8API.AppLanguage.SC) },
                        label = { Text(stringResource(R.string.profile_lang_sc)) },
                        modifier = Modifier.testTag(ProfileStudioTestTags.LANG_CHIP_SC),
                    )
                    FilterChip(
                        selected = preferences.appLanguage == Wenku8API.AppLanguage.TC,
                        onClick = { onAppLanguageSelected(Wenku8API.AppLanguage.TC) },
                        label = { Text(stringResource(R.string.profile_lang_tc)) },
                        modifier = Modifier.testTag(ProfileStudioTestTags.LANG_CHIP_TC),
                    )
                }
            }

            // Reading & App Theme Mode (6 M3e modes; SYSTEM unifies Day/Night + Material You dynamic color)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.theme_section_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Wenku8ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = mode == preferences.themeMode,
                            onClick = { onThemeModeSelected(mode) },
                            label = { Text(stringResource(mode.displayNameResId)) },
                        )
                    }
                }
            }

            HorizontalDivider()

            // E-Ink High-Contrast Switch (`eink_mode` parity)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.profile_eink_switch),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.profile_eink_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = preferences.einkModeEnabled,
                    onCheckedChange = onEinkModeChanged,
                    modifier = Modifier.testTag(ProfileStudioTestTags.EINK_SWITCH),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderDefaultTypographyCard(
    preferences: Wenku8UserPreferences,
    assetErrorText: String?,
    onTypographyChanged: (
        fontSizeSp: Int,
        lineDistanceDp: Int,
        paragraphDistanceDp: Int,
        pageEdgeDistanceDp: Int,
    ) -> Unit,
    onResetTypography: () -> Unit,
    onResetCustomFont: () -> Unit,
    onPickCustomFont: () -> Unit,
    onResetCustomBackground: () -> Unit,
    onPickCustomBackground: () -> Unit,
    onVolumeKeyPagingChanged: (Boolean) -> Unit,
) {
    val previewFontFamily = remember(preferences.readerCustomFontPath) {
        val rawPath = preferences.readerCustomFontPath
        if (rawPath.isBlank()) {
            FontFamily.Default
        } else {
            runCatching {
                val file = File(rawPath)
                if (file.isFile && file.length() > 0L) {
                    FontFamily(Typeface.createFromFile(file))
                } else {
                    FontFamily.Default
                }
            }.getOrDefault(FontFamily.Default)
        }
    }
    val customBgFile = remember(preferences.readerCustomBackgroundPath) {
        preferences.readerCustomBackgroundPath
            .takeIf { it.isNotBlank() }
            ?.let(::File)
            ?.takeIf { it.isFile }
    }
    val previewSampleText = stringResource(R.string.profile_reader_preview_sample)
    val previewParagraphs = remember(previewSampleText) {
        previewSampleText.lines().filter { it.isNotBlank() }
    }

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        ),
        shape = MaterialTheme.shapes.large,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.large),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.profile_reader_studio_title),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onResetTypography,
                    modifier = Modifier.testTag(ProfileStudioTestTags.RESET_TYPOGRAPHY_BUTTON),
                ) {
                    Text(text = stringResource(R.string.profile_reader_reset_typography))
                }
            }

            // Multi-Paragraph Live Typography Preview Box (previews font size, line height, paragraph spacing, and page margin)
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.medium)
                    .einkHairlineBorder(MaterialTheme.shapes.medium),
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (customBgFile != null) {
                        AsyncImage(
                            model = customBgFile,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alpha = 0.28f,
                            modifier = Modifier.matchParentSize(),
                        )
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(preferences.readerParagraphDistanceDp.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                horizontal = preferences.readerPageEdgeDistanceDp.coerceAtLeast(10).dp,
                                vertical = 14.dp,
                            )
                            .testTag(ProfileStudioTestTags.TYPOGRAPHY_PREVIEW_BOX),
                    ) {
                        previewParagraphs.forEach { paragraph ->
                            Text(
                                text = paragraph,
                                fontFamily = previewFontFamily,
                                fontSize = preferences.readerFontSizeSp.sp,
                                lineHeight = (preferences.readerFontSizeSp + preferences.readerLineDistanceDp).sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }

            // 1. Font Size (`8..36 sp`, covering `v1.31` `8..32 sp`)
            LabeledIntSlider(
                label = stringResource(R.string.profile_reader_font_size_format, preferences.readerFontSizeSp),
                value = preferences.readerFontSizeSp,
                valueRange = Wenku8UserPreferences.MIN_READER_FONT_SIZE_SP..Wenku8UserPreferences.MAX_READER_FONT_SIZE_SP,
                sliderTestTag = ProfileStudioTestTags.FONT_SIZE_SLIDER,
                onValueSelected = { newSize ->
                    onTypographyChanged(
                        newSize,
                        preferences.readerLineDistanceDp,
                        preferences.readerParagraphDistanceDp,
                        preferences.readerPageEdgeDistanceDp,
                    )
                },
            )

            // 2. Line Distance (`0..32 dp`)
            LabeledIntSlider(
                label = stringResource(R.string.profile_reader_line_dist_format, preferences.readerLineDistanceDp),
                value = preferences.readerLineDistanceDp,
                valueRange = Wenku8UserPreferences.MIN_READER_LINE_DISTANCE_DP..Wenku8UserPreferences.MAX_READER_LINE_DISTANCE_DP,
                sliderTestTag = ProfileStudioTestTags.LINE_DIST_SLIDER,
                onValueSelected = { newLine ->
                    onTypographyChanged(
                        preferences.readerFontSizeSp,
                        newLine,
                        preferences.readerParagraphDistanceDp,
                        preferences.readerPageEdgeDistanceDp,
                    )
                },
            )

            // 3. Paragraph Distance (`0..48 dp`)
            LabeledIntSlider(
                label = stringResource(R.string.profile_reader_para_dist_format, preferences.readerParagraphDistanceDp),
                value = preferences.readerParagraphDistanceDp,
                valueRange = Wenku8UserPreferences.MIN_READER_PARAGRAPH_DISTANCE_DP..Wenku8UserPreferences.MAX_READER_PARAGRAPH_DISTANCE_DP,
                sliderTestTag = ProfileStudioTestTags.PARA_DIST_SLIDER,
                onValueSelected = { newPara ->
                    onTypographyChanged(
                        preferences.readerFontSizeSp,
                        preferences.readerLineDistanceDp,
                        newPara,
                        preferences.readerPageEdgeDistanceDp,
                    )
                },
            )

            // 4. Page Edge Margin (`0..32 dp`)
            LabeledIntSlider(
                label = stringResource(R.string.profile_reader_edge_dist_format, preferences.readerPageEdgeDistanceDp),
                value = preferences.readerPageEdgeDistanceDp,
                valueRange = Wenku8UserPreferences.MIN_READER_PAGE_EDGE_DISTANCE_DP..Wenku8UserPreferences.MAX_READER_PAGE_EDGE_DISTANCE_DP,
                sliderTestTag = ProfileStudioTestTags.EDGE_DIST_SLIDER,
                onValueSelected = { newEdge ->
                    onTypographyChanged(
                        preferences.readerFontSizeSp,
                        preferences.readerLineDistanceDp,
                        preferences.readerParagraphDistanceDp,
                        newEdge,
                    )
                },
            )

            HorizontalDivider()

            // Custom Font Picker (`reader_font_path`)
            val hasCustomFont = preferences.readerCustomFontPath.isNotBlank()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.profile_reader_font_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !hasCustomFont,
                        onClick = onResetCustomFont,
                        label = { Text(stringResource(R.string.profile_reader_font_default)) },
                    )
                    FilterChip(
                        selected = hasCustomFont,
                        onClick = onPickCustomFont,
                        label = {
                            Text(
                                stringResource(
                                    if (hasCustomFont) {
                                        R.string.profile_reader_font_custom_active
                                    } else {
                                        R.string.profile_reader_font_pick
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            // Custom Day Paper Background (`reader_background_path`)
            val hasCustomBg = preferences.readerCustomBackgroundPath.isNotBlank()
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.profile_reader_bg_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(
                        selected = !hasCustomBg,
                        onClick = onResetCustomBackground,
                        label = { Text(stringResource(R.string.profile_reader_bg_default)) },
                    )
                    FilterChip(
                        selected = hasCustomBg,
                        onClick = onPickCustomBackground,
                        label = {
                            Text(
                                stringResource(
                                    if (hasCustomBg) {
                                        R.string.profile_reader_bg_custom_active
                                    } else {
                                        R.string.profile_reader_bg_pick
                                    },
                                ),
                            )
                        },
                    )
                }
            }

            if (assetErrorText != null) {
                Text(
                    text = assetErrorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            HorizontalDivider()

            // Volume Key Paging Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.profile_reader_volume_paging),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Switch(
                    checked = preferences.volumeKeyPagingEnabled,
                    onCheckedChange = onVolumeKeyPagingChanged,
                    modifier = Modifier.testTag(ProfileStudioTestTags.VOLUME_PAGING_SWITCH),
                )
            }
        }
    }
}

@Composable
private fun LabeledIntSlider(
    label: String,
    value: Int,
    valueRange: IntRange,
    sliderTestTag: String,
    onValueSelected: (Int) -> Unit,
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
        Slider(
            value = value.coerceIn(valueRange).toFloat(),
            onValueChange = { rawFloat ->
                val stepped = rawFloat.roundToInt().coerceIn(valueRange)
                if (stepped != value) {
                    onValueSelected(stepped)
                }
            },
            valueRange = valueRange.first.toFloat()..valueRange.last.toFloat(),
            steps = (valueRange.last - valueRange.first - 1).coerceAtLeast(0),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = label }
                .testTag(sliderTestTag),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StorageAndBackupStudioCard(
    statusMessage: String?,
    isCacheCleanAvailable: Boolean,
    isBackupAvailable: Boolean,
    isLegacySafAvailable: Boolean,
    onRunFastClean: () -> Unit,
    onRunDeepClean: () -> Unit,
    onRequestExportBackup: () -> Unit,
    onRequestImportBackup: () -> Unit,
    onRequestImportLegacySaf: () -> Unit,
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
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.profile_storage_title),
                style = MaterialTheme.typography.titleMedium,
            )

            // Fast & Deep Cache Clean
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${stringResource(R.string.profile_cache_fast_desc)} · ${stringResource(R.string.profile_cache_deep_desc)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilledTonalButton(
                        onClick = onRunFastClean,
                        enabled = isCacheCleanAvailable,
                        modifier = Modifier.testTag(ProfileStudioTestTags.FAST_CLEAN_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(R.string.profile_cache_fast_action))
                    }

                    OutlinedButton(
                        onClick = onRunDeepClean,
                        enabled = isCacheCleanAvailable,
                        modifier = Modifier.testTag(ProfileStudioTestTags.DEEP_CLEAN_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.CleaningServices,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(R.string.profile_cache_deep_action))
                    }
                }
            }

            HorizontalDivider()

            // .wk8backup.zip Export & Smart-Merge Import
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onRequestExportBackup,
                    enabled = isBackupAvailable,
                    modifier = Modifier.testTag(ProfileStudioTestTags.EXPORT_BACKUP_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Archive,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.profile_backup_export_action))
                }

                OutlinedButton(
                    onClick = onRequestImportBackup,
                    enabled = isBackupAvailable,
                    modifier = Modifier.testTag(ProfileStudioTestTags.IMPORT_BACKUP_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Unarchive,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.profile_backup_import_action))
                }
            }

            // Legacy v1.x External Folder SAF Import
            AssistChip(
                onClick = onRequestImportLegacySaf,
                enabled = isLegacySafAvailable,
                label = { Text(text = stringResource(R.string.profile_legacy_saf_import_action)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Outlined.FolderOpen, contentDescription = null)
                },
                modifier = Modifier.testTag(ProfileStudioTestTags.IMPORT_LEGACY_SAF_BUTTON),
            )

            if (statusMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ProfileStudioTestTags.STORAGE_STATUS_BANNER)
                        .einkHairlineBorder(MaterialTheme.shapes.medium),
                ) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AboutAndLicenseSection(
    serverNoticeAndUpdateRepository: ServerNoticeAndUpdateRepository?,
    onOpenLicense: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (serverNoticeAndUpdateRepository != null) {
            AppUpdateCheckCard(
                onCheckForUpdate = { serverNoticeAndUpdateRepository.checkForAppUpdate() },
            )
        }

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
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.profile_about_section_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenLicense,
                        modifier = Modifier.testTag(ProfileStudioTestTags.OPEN_LICENSE_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(R.string.profile_license_action))
                    }

                    OutlinedButton(
                        onClick = onOpenAbout,
                        modifier = Modifier.testTag(ProfileStudioTestTags.OPEN_ABOUT_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Info,
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(R.string.profile_about_action))
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupExportModeDialog(
    onSelectMode: (BackupArchiveMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.profile_backup_mode_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelectMode(BackupArchiveMode.LIGHT_SAVE) }
                        .einkHairlineBorder(MaterialTheme.shapes.medium),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.profile_backup_mode_light_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.profile_backup_mode_light_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.medium)
                        .clickable { onSelectMode(BackupArchiveMode.FULL_ARCHIVE) }
                        .einkHairlineBorder(MaterialTheme.shapes.medium),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.profile_backup_mode_full_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.profile_backup_mode_full_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_cancel))
            }
        },
    )
}

@Composable
private fun OpenSourceLicenseDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val licenseText = remember {
        runCatching {
            context.resources.openRawResource(R.raw.license)
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
        }.getOrDefault("License resource unavailable.")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.profile_license_dialog_title)) },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = licenseText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun AboutWenkuDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var versionTapCount by rememberSaveable { mutableIntStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.profile_about_dialog_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 440.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                // Card 1: 轻小说文库 (Wenku8.com) — Version, Project Homepage, Disclaimer, QQ Group
                AboutSectionCard(title = stringResource(R.string.about_wenku8_title)) {
                    Text(
                        text = stringResource(
                            R.string.about_version_format,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { versionTapCount++ }
                            .padding(vertical = 6.dp)
                            .testTag(ProfileStudioTestTags.ABOUT_VERSION_TAP_TARGET),
                    )

                    if (versionTapCount >= 5) {
                        Text(
                            text = stringResource(R.string.profile_about_easter_egg),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag(ProfileStudioTestTags.ABOUT_EASTER_EGG_TEXT),
                        )
                    }

                    Text(
                        text = stringResource(R.string.about_project_homepage),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { openSafeBrowserUrl(context, "https://wenku8.app") }
                            .padding(vertical = 6.dp)
                            .testTag(ProfileStudioTestTags.ABOUT_HOMEPAGE_LINK),
                    )

                    Text(
                        text = stringResource(R.string.about_no_duty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                    )

                    Text(
                        text = stringResource(R.string.about_qq_group),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp, bottom = 2.dp),
                    )
                }

                // Card 2: 软件架构 — MewX
                AboutSectionCard(title = stringResource(R.string.about_software_arch)) {
                    Text(
                        text = stringResource(R.string.about_mewx),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { openSafeBrowserUrl(context, "https://mewx.org") }
                            .padding(vertical = 6.dp),
                    )
                }

                // Card 3: 致谢 — Yanix, Jack Liu, ZERO, 妻兔姬
                AboutSectionCard(title = stringResource(R.string.about_acknowledgement)) {
                    Text(
                        text = stringResource(R.string.about_ack_seraphroy),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { openSafeBrowserUrl(context, "https://github.com/SeraphRoy") }
                            .padding(vertical = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.about_ack_aaassseee),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.small)
                            .clickable { openSafeBrowserUrl(context, "https://github.com/aaassseee") }
                            .padding(vertical = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.about_ack_zero),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                    Text(
                        text = stringResource(R.string.about_ack_qituji),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.action_close))
            }
        },
    )
}

@Composable
private fun AboutSectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.medium),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            HorizontalDivider(modifier = Modifier.padding(bottom = 6.dp))
            content()
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
