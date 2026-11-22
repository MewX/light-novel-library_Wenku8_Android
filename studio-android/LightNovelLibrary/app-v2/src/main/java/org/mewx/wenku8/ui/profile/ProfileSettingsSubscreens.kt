package org.mewx.wenku8.ui.profile

import android.graphics.Typeface
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.R
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.data.migration.DocumentTreeSaveSource
import org.mewx.wenku8.data.migration.LegacyV1StorageMigrator
import org.mewx.wenku8.data.repository.BackupArchiveMode
import org.mewx.wenku8.data.repository.BackupArchiveRepository
import org.mewx.wenku8.data.repository.CustomAssetSlot
import org.mewx.wenku8.data.repository.DeepCleanProgress
import org.mewx.wenku8.data.repository.StorageAndCacheRepository
import org.mewx.wenku8.reader.model.ReaderColumnMode
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.io.File
import kotlin.math.roundToInt

/**
 * Drill-down M3 Expressive settings subscreens launched from `Tab 4 · 我的` segmented list groups.
 */
enum class ProfileSettingsSection(
    @param:StringRes val titleResId: Int,
) {
    APPEARANCE(R.string.profile_appearance_title),
    READER_TYPOGRAPHY(R.string.profile_reader_studio_title),
    STORAGE_AND_BACKUP(R.string.profile_storage_title),
    ABOUT_AND_FEEDBACK(R.string.profile_about_section_title),
}

private val SegmentedOuterCorner = 16.dp
private val SegmentedInnerCorner = 4.dp
private val SegmentedGroupGap = 2.dp
private val SettingsSubscreenMaxWidth = 760.dp

/**
 * Computes Material 3 Expressive segmented list item corner geometry (`16dp` outer / `4dp` inner).
 */
internal fun profileSegmentedItemShape(index: Int, count: Int): RoundedCornerShape {
    if (count <= 1) return RoundedCornerShape(SegmentedOuterCorner)
    return when (index) {
        0 -> RoundedCornerShape(
            topStart = SegmentedOuterCorner,
            topEnd = SegmentedOuterCorner,
            bottomStart = SegmentedInnerCorner,
            bottomEnd = SegmentedInnerCorner,
        )
        count - 1 -> RoundedCornerShape(
            topStart = SegmentedInnerCorner,
            topEnd = SegmentedInnerCorner,
            bottomStart = SegmentedOuterCorner,
            bottomEnd = SegmentedOuterCorner,
        )
        else -> RoundedCornerShape(SegmentedInnerCorner)
    }
}

/**
 * Material 3 Expressive grouped settings section with section header and `2dp` segmented item gaps.
 */
@Composable
internal fun ProfileSegmentedListSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 12.dp, bottom = 6.dp),
        )
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(SegmentedGroupGap),
            content = content,
        )
    }
}

/**
 * Material 3 Expressive segmented settings item row with tonal leading icon badge, title,
 * live state subtitle, optional trailing control/chevron, and optional stacked bottom content.
 */
@Composable
internal fun ProfileSegmentedSettingRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    highlighted: Boolean = false,
    modifier: Modifier = Modifier,
    trailingContent: (@Composable () -> Unit)? = null,
    bottomContent: (@Composable () -> Unit)? = null,
) {
    val shape = profileSegmentedItemShape(index = index, count = count)
    val containerColor = if (highlighted) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val contentColor = if (highlighted) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val badgeColor = if (highlighted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val badgeIconTint = if (highlighted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.primary
    }

    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                } else {
                    Modifier
                },
            )
            .einkHairlineBorder(shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(if (enabled) 1f else 0.55f)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(badgeColor)
                        .einkHairlineBorder(CircleShape),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = badgeIconTint,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (highlighted) {
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                when {
                    trailingContent != null -> {
                        Spacer(modifier = Modifier.width(10.dp))
                        trailingContent()
                    }
                    onClick != null -> {
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = null,
                            tint = if (highlighted) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }
            if (bottomContent != null) {
                Spacer(modifier = Modifier.height(10.dp))
                bottomContent()
            }
        }
    }
}

/**
 * Material 3 Expressive segmented row with a trailing [Switch]. When [onRowClick] is supplied,
 * follows Android Settings' master-switch split pattern: tapping the left body navigates to the
 * detail screen while the trailing switch toggles immediately.
 */
@Composable
internal fun ProfileSegmentedSwitchRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    switchEnabled: Boolean = true,
    onRowClick: (() -> Unit)? = null,
    rowTestTag: String? = null,
    switchTestTag: String? = null,
    modifier: Modifier = Modifier,
) {
    val shape = profileSegmentedItemShape(index = index, count = count)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .einkHairlineBorder(shape),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .then(if (rowTestTag != null) Modifier.testTag(rowTestTag) else Modifier)
                    .clickable(
                        enabled = onRowClick != null || switchEnabled,
                        onClick = {
                            if (onRowClick != null) {
                                onRowClick()
                            } else if (switchEnabled) {
                                onCheckedChange(!checked)
                            }
                        },
                    )
                    .padding(start = 16.dp, top = 14.dp, bottom = 14.dp, end = 12.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .einkHairlineBorder(CircleShape),
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            if (onRowClick != null) {
                VerticalDivider(
                    modifier = Modifier.height(28.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
            }
            Box(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                Switch(
                    checked = checked,
                    onCheckedChange = onCheckedChange,
                    enabled = switchEnabled,
                    modifier = if (switchTestTag != null) {
                        Modifier.testTag(switchTestTag)
                    } else {
                        Modifier
                    },
                )
            }
        }
    }
}

/**
 * Full-screen Material 3 Expressive drill-down settings subscreen for [ProfileSettingsSection].
 */
@Composable
fun ProfileSettingsSubscreen(
    section: ProfileSettingsSection,
    preferences: Wenku8UserPreferences,
    onNavigateBack: () -> Unit,
    onThemeModeSelected: (Wenku8ThemeMode) -> Unit,
    onDefaultHomeSelected: (DefaultHomeDestination) -> Unit,
    onAppLanguageSelected: (Wenku8API.AppLanguage) -> Unit,
    onEinkModeChanged: (Boolean) -> Unit,
    onReaderTypographyChanged: (
        fontSizeSp: Int,
        lineDistanceDp: Int,
        paragraphDistanceDp: Int,
        pageEdgeDistanceDp: Int,
    ) -> Unit,
    onReaderCustomFontPathChanged: (String) -> Unit,
    onReaderCustomBackgroundPathChanged: (String) -> Unit,
    onVolumeKeyPagingChanged: (Boolean) -> Unit,
    onReaderColumnModeChanged: (ReaderColumnMode) -> Unit = {},
    storageAndCacheRepository: StorageAndCacheRepository? = null,
    backupArchiveRepository: BackupArchiveRepository? = null,
    legacyV1StorageMigrator: LegacyV1StorageMigrator? = null,
    isPlayStoreChannel: Boolean = BuildConfig.DISTRIBUTION_CHANNEL == "playstore",
    modifier: Modifier = Modifier,
) {
    BackHandler(onBack = onNavigateBack)

    val backLabel = stringResource(R.string.action_back)
    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag(ProfileStudioTestTags.SUBSCREEN_ROOT),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(section.titleResId),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    TooltipIconButton(
                        tooltipText = backLabel,
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag(ProfileStudioTestTags.SUBSCREEN_BACK_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = backLabel,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        BoxWithConstraints(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val horizontalPadding = if (maxWidth < 360.dp) 16.dp else 20.dp
            val containerModifier = Modifier
                .fillMaxSize()
                .widthIn(max = SettingsSubscreenMaxWidth)

            when (section) {
                ProfileSettingsSection.APPEARANCE -> {
                    AppearanceSettingsBody(
                        preferences = preferences,
                        horizontalPadding = horizontalPadding,
                        onDefaultHomeSelected = onDefaultHomeSelected,
                        onAppLanguageSelected = onAppLanguageSelected,
                        onThemeModeSelected = onThemeModeSelected,
                        onEinkModeChanged = onEinkModeChanged,
                        modifier = containerModifier,
                    )
                }

                ProfileSettingsSection.READER_TYPOGRAPHY -> {
                    ReaderTypographySettingsBody(
                        preferences = preferences,
                        horizontalPadding = horizontalPadding,
                        pinPreviewAtTop = maxHeight >= 540.dp,
                        storageAndCacheRepository = storageAndCacheRepository,
                        onTypographyChanged = onReaderTypographyChanged,
                        onCustomFontPathChanged = onReaderCustomFontPathChanged,
                        onCustomBackgroundPathChanged = onReaderCustomBackgroundPathChanged,
                        onVolumeKeyPagingChanged = onVolumeKeyPagingChanged,
                        onReaderColumnModeChanged = onReaderColumnModeChanged,
                        modifier = containerModifier,
                    )
                }

                ProfileSettingsSection.STORAGE_AND_BACKUP -> {
                    StorageAndBackupSettingsBody(
                        horizontalPadding = horizontalPadding,
                        storageAndCacheRepository = storageAndCacheRepository,
                        backupArchiveRepository = backupArchiveRepository,
                        legacyV1StorageMigrator = legacyV1StorageMigrator,
                        modifier = containerModifier,
                    )
                }

                ProfileSettingsSection.ABOUT_AND_FEEDBACK -> {
                    AboutAndFeedbackSettingsBody(
                        horizontalPadding = horizontalPadding,
                        isPlayStoreChannel = isPlayStoreChannel,
                        modifier = containerModifier,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppearanceSettingsBody(
    preferences: Wenku8UserPreferences,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    onDefaultHomeSelected: (DefaultHomeDestination) -> Unit,
    onAppLanguageSelected: (Wenku8API.AppLanguage) -> Unit,
    onThemeModeSelected: (Wenku8ThemeMode) -> Unit,
    onEinkModeChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.testTag(ProfileStudioTestTags.SUBSCREEN_LIST),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(key = "appearance_home_and_language") {
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

                    HorizontalDivider()

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
                }
            }
        }

        item(key = "appearance_theme_modes") {
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
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
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
            }
        }

        item(key = "appearance_eink_switch") {
            ProfileSegmentedSwitchRow(
                index = 0,
                count = 1,
                icon = Icons.Outlined.Contrast,
                title = stringResource(R.string.profile_eink_switch),
                subtitle = stringResource(R.string.profile_eink_subtitle),
                checked = preferences.einkModeEnabled,
                onCheckedChange = onEinkModeChanged,
                switchTestTag = ProfileStudioTestTags.EINK_SWITCH,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReaderTypographySettingsBody(
    preferences: Wenku8UserPreferences,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    pinPreviewAtTop: Boolean,
    storageAndCacheRepository: StorageAndCacheRepository?,
    onTypographyChanged: (
        fontSizeSp: Int,
        lineDistanceDp: Int,
        paragraphDistanceDp: Int,
        pageEdgeDistanceDp: Int,
    ) -> Unit,
    onCustomFontPathChanged: (String) -> Unit,
    onCustomBackgroundPathChanged: (String) -> Unit,
    onVolumeKeyPagingChanged: (Boolean) -> Unit,
    onReaderColumnModeChanged: (ReaderColumnMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var assetErrorText by rememberSaveable { mutableStateOf<String?>(null) }
    val fontInvalidMessage = stringResource(R.string.profile_reader_font_invalid)

    val customReaderFontLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && storageAndCacheRepository != null) {
            coroutineScope.launch {
                assetErrorText = null
                storageAndCacheRepository.importCustomAsset(
                    contentResolver = context.contentResolver,
                    sourceUri = uri,
                    slot = CustomAssetSlot.READER_FONT,
                ).onSuccess { savedFile ->
                    onCustomFontPathChanged(savedFile.absolutePath)
                }.onFailure {
                    assetErrorText = fontInvalidMessage
                }
            }
        }
    }

    val customReaderBgLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null && storageAndCacheRepository != null) {
            coroutineScope.launch {
                assetErrorText = null
                storageAndCacheRepository.importCustomAsset(
                    contentResolver = context.contentResolver,
                    sourceUri = uri,
                    slot = CustomAssetSlot.READER_BACKGROUND,
                ).onSuccess { savedFile ->
                    onCustomBackgroundPathChanged(savedFile.absolutePath)
                }
            }
        }
    }

    val previewFontFamily = remember(preferences.readerCustomFontPath) {
        when (val rawPath = preferences.readerCustomFontPath) {
            "" -> FontFamily.SansSerif
            Wenku8UserPreferences.BUILTIN_SERIF_FONT_TOKEN -> FontFamily.Serif
            else -> runCatching {
                val file = File(rawPath)
                if (file.isFile && file.length() > 0L) {
                    FontFamily(Typeface.createFromFile(file))
                } else {
                    FontFamily.SansSerif
                }
            }.getOrDefault(FontFamily.SansSerif)
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

    val renderPreviewBox: @Composable (Modifier) -> Unit = { boxModifier ->
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            shape = MaterialTheme.shapes.large,
            modifier = boxModifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .einkHairlineBorder(MaterialTheme.shapes.large),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 220.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
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
    }

    Column(modifier = modifier) {
        if (pinPreviewAtTop) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = horizontalPadding, vertical = 10.dp),
            ) {
                renderPreviewBox(Modifier)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(ProfileStudioTestTags.SUBSCREEN_LIST),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (!pinPreviewAtTop) {
                item(key = "reader_preview_inline") {
                    renderPreviewBox(Modifier)
                }
            }

            item(key = "reader_typography_sliders") {
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
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            itemVerticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.profile_reader_typography_sliders_title),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            TextButton(
                                onClick = {
                                    onTypographyChanged(
                                        Wenku8UserPreferences.DEFAULT_READER_FONT_SIZE_SP,
                                        Wenku8UserPreferences.DEFAULT_READER_LINE_DISTANCE_DP,
                                        Wenku8UserPreferences.DEFAULT_READER_PARAGRAPH_DISTANCE_DP,
                                        Wenku8UserPreferences.DEFAULT_READER_PAGE_EDGE_DISTANCE_DP,
                                    )
                                },
                                modifier = Modifier.testTag(ProfileStudioTestTags.RESET_TYPOGRAPHY_BUTTON),
                            ) {
                                Text(
                                    text = stringResource(R.string.profile_reader_reset_typography),
                                    maxLines = 1,
                                    softWrap = false,
                                )
                            }
                        }

                        LabeledIntSliderRow(
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

                        LabeledIntSliderRow(
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

                        LabeledIntSliderRow(
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

                        LabeledIntSliderRow(
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
                    }
                }
            }

            item(key = "reader_column_layout") {
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.profile_reader_column_mode_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(R.string.profile_reader_column_mode_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            ReaderColumnMode.entries.forEach { mode ->
                                val labelRes = when (mode) {
                                    ReaderColumnMode.AUTO -> R.string.profile_reader_column_mode_auto
                                    ReaderColumnMode.SINGLE -> R.string.profile_reader_column_mode_single
                                    ReaderColumnMode.DUAL -> R.string.profile_reader_column_mode_dual
                                }
                                FilterChip(
                                    selected = preferences.readerColumnMode == mode,
                                    onClick = { onReaderColumnModeChanged(mode) },
                                    label = { Text(stringResource(labelRes)) },
                                )
                            }
                        }
                    }
                }
            }

            item(key = "reader_font_and_paper") {
                val isSansDefault = preferences.readerCustomFontPath.isBlank()
                val isBuiltinSerif = preferences.isBuiltinSerifFont
                val hasCustomFontFile = preferences.hasCustomFontFile
                val hasCustomBg = preferences.readerCustomBackgroundPath.isNotBlank()

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
                            text = stringResource(R.string.profile_reader_assets_section_title),
                            style = MaterialTheme.typography.titleMedium,
                        )

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
                                    selected = isSansDefault,
                                    onClick = {
                                        assetErrorText = null
                                        onCustomFontPathChanged("")
                                    },
                                    label = { Text(stringResource(R.string.profile_reader_font_default)) },
                                )
                                FilterChip(
                                    selected = isBuiltinSerif,
                                    onClick = {
                                        assetErrorText = null
                                        onCustomFontPathChanged(Wenku8UserPreferences.BUILTIN_SERIF_FONT_TOKEN)
                                    },
                                    label = { Text(stringResource(R.string.profile_reader_font_serif)) },
                                )
                                FilterChip(
                                    selected = hasCustomFontFile,
                                    onClick = { customReaderFontLauncher.launch(arrayOf("*/*")) },
                                    label = {
                                        Text(
                                            stringResource(
                                                if (hasCustomFontFile) {
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

                        HorizontalDivider()

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
                                    onClick = {
                                        assetErrorText = null
                                        onCustomBackgroundPathChanged("")
                                    },
                                    label = { Text(stringResource(R.string.profile_reader_bg_default)) },
                                )
                                FilterChip(
                                    selected = hasCustomBg,
                                    onClick = { customReaderBgLauncher.launch(arrayOf("image/*")) },
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
                                text = assetErrorText!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }

            item(key = "reader_volume_paging") {
                ProfileSegmentedSwitchRow(
                    index = 0,
                    count = 1,
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    title = stringResource(R.string.profile_reader_volume_paging),
                    subtitle = stringResource(R.string.profile_reader_volume_paging_desc),
                    checked = preferences.volumeKeyPagingEnabled,
                    onCheckedChange = onVolumeKeyPagingChanged,
                    switchTestTag = ProfileStudioTestTags.VOLUME_PAGING_SWITCH,
                )
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun LabeledIntSliderRow(
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

@Composable
private fun StorageAndBackupSettingsBody(
    horizontalPadding: androidx.compose.ui.unit.Dp,
    storageAndCacheRepository: StorageAndCacheRepository?,
    backupArchiveRepository: BackupArchiveRepository?,
    legacyV1StorageMigrator: LegacyV1StorageMigrator?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val backupFailedMessage = stringResource(R.string.profile_backup_failed)

    var storageStatusMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var deepCleanProgress by remember { mutableStateOf<DeepCleanProgress?>(null) }
    var activeDeepCleanJob by remember { mutableStateOf<Job?>(null) }
    var showBackupModePickerDialog by rememberSaveable { mutableStateOf(false) }
    var pendingExportMode by rememberSaveable { mutableStateOf(BackupArchiveMode.LIGHT_SAVE) }

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
                org.mewx.wenku8.util.AnalyticsTracker.logLegacyMigrationPathSelection(
                    validPath = treeRoot != null,
                )
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
                    org.mewx.wenku8.util.AnalyticsTracker.logLegacyMigration(
                        source = "manual_saf",
                        status = "completed",
                        novelsCount = report.migratedBookshelfNovelsCount,
                        chaptersCount = report.migratedChaptersCount,
                        progressCount = report.migratedReadingProgressCount,
                        restoredAccount = report.restoredAccountCredential,
                    )
                    storageStatusMessage = context.getString(
                        R.string.profile_legacy_saf_import_success_format,
                        report.migratedBookshelfNovelsCount,
                        report.migratedChaptersCount,
                        report.migratedReadingProgressCount,
                    )
                }.onFailure { error ->
                    org.mewx.wenku8.util.CrashReporter.recordException(
                        "ProfileSettings.importLegacySaf",
                        error,
                    )
                    org.mewx.wenku8.util.AnalyticsTracker.logLegacyMigration(
                        source = "manual_saf",
                        status = "failed",
                    )
                    storageStatusMessage = backupFailedMessage
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.testTag(ProfileStudioTestTags.SUBSCREEN_LIST),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        if (storageStatusMessage != null) {
            item(key = "storage_status_banner") {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(ProfileStudioTestTags.STORAGE_STATUS_BANNER)
                        .einkHairlineBorder(MaterialTheme.shapes.large),
                ) {
                    Text(
                        text = storageStatusMessage!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                }
            }
        }

        item(key = "storage_cache_group") {
            ProfileSegmentedListSection(title = stringResource(R.string.profile_storage_cache_section)) {
                ProfileSegmentedSettingRow(
                    index = 0,
                    count = 2,
                    icon = Icons.Outlined.CleaningServices,
                    title = stringResource(R.string.profile_cache_fast_action),
                    subtitle = stringResource(R.string.profile_cache_fast_desc),
                    enabled = storageAndCacheRepository != null,
                    onClick = {
                        val repo = storageAndCacheRepository ?: return@ProfileSegmentedSettingRow
                        coroutineScope.launch {
                            val summary = repo.runFastCacheClean()
                            storageStatusMessage = context.getString(
                                R.string.profile_cache_result_format,
                                summary.deletedFileCount,
                                formatByteSize(summary.reclaimedBytes),
                            )
                        }
                    },
                    modifier = Modifier.testTag(ProfileStudioTestTags.FAST_CLEAN_BUTTON),
                )
                ProfileSegmentedSettingRow(
                    index = 1,
                    count = 2,
                    icon = Icons.Outlined.CleaningServices,
                    title = stringResource(R.string.profile_cache_deep_action),
                    subtitle = stringResource(R.string.profile_cache_deep_desc),
                    enabled = storageAndCacheRepository != null,
                    onClick = {
                        val repo = storageAndCacheRepository ?: return@ProfileSegmentedSettingRow
                        if (activeDeepCleanJob?.isActive == true) return@ProfileSegmentedSettingRow
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
                    modifier = Modifier.testTag(ProfileStudioTestTags.DEEP_CLEAN_BUTTON),
                )
            }
        }

        item(key = "storage_backup_group") {
            ProfileSegmentedListSection(title = stringResource(R.string.profile_storage_backup_section)) {
                ProfileSegmentedSettingRow(
                    index = 0,
                    count = 3,
                    icon = Icons.Outlined.Archive,
                    title = stringResource(R.string.profile_backup_export_action),
                    subtitle = stringResource(R.string.profile_backup_export_desc),
                    enabled = backupArchiveRepository != null,
                    onClick = { showBackupModePickerDialog = true },
                    modifier = Modifier.testTag(ProfileStudioTestTags.EXPORT_BACKUP_BUTTON),
                )
                ProfileSegmentedSettingRow(
                    index = 1,
                    count = 3,
                    icon = Icons.Outlined.Unarchive,
                    title = stringResource(R.string.profile_backup_import_action),
                    subtitle = stringResource(R.string.profile_backup_import_desc),
                    enabled = backupArchiveRepository != null,
                    onClick = {
                        importBackupLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream", "*/*"),
                        )
                    },
                    modifier = Modifier.testTag(ProfileStudioTestTags.IMPORT_BACKUP_BUTTON),
                )
                ProfileSegmentedSettingRow(
                    index = 2,
                    count = 3,
                    icon = Icons.Outlined.FolderOpen,
                    title = stringResource(R.string.profile_legacy_saf_import_action),
                    subtitle = stringResource(R.string.profile_legacy_saf_import_desc),
                    enabled = legacyV1StorageMigrator != null,
                    onClick = { importLegacyTreeLauncher.launch(null) },
                    modifier = Modifier.testTag(ProfileStudioTestTags.IMPORT_LEGACY_SAF_BUTTON),
                )
            }
        }
    }

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

    if (showBackupModePickerDialog) {
        ProfileBackupExportModeDialog(
            onSelectMode = { selectedMode ->
                showBackupModePickerDialog = false
                pendingExportMode = selectedMode
                exportBackupLauncher.launch(BackupArchiveRepository.suggestedFileName(selectedMode))
            },
            onDismiss = { showBackupModePickerDialog = false },
        )
    }
}

@Composable
private fun AboutAndFeedbackSettingsBody(
    horizontalPadding: androidx.compose.ui.unit.Dp,
    isPlayStoreChannel: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val channelLabel = distributionChannelLabel(isPlayStoreChannel)
    val feedbackEmailSubject = stringResource(
        R.string.profile_feedback_email_subject_format,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE,
    )
    val feedbackEmailBody = stringResource(
        R.string.profile_feedback_email_body_format,
        BuildConfig.VERSION_NAME,
        BuildConfig.VERSION_CODE,
        channelLabel,
        Build.VERSION.RELEASE.orEmpty().ifEmpty { "Unknown" },
        Build.VERSION.SDK_INT,
        formatDeviceModel(),
    )

    var showLicenseDialog by rememberSaveable { mutableStateOf(false) }
    var showAboutDialog by rememberSaveable { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.testTag(ProfileStudioTestTags.SUBSCREEN_LIST),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item(key = "about_feedback_group") {
            ProfileSegmentedListSection(title = stringResource(R.string.profile_about_feedback_section)) {
                ProfileSegmentedSettingRow(
                    index = 0,
                    count = 2,
                    icon = Icons.Outlined.BugReport,
                    title = stringResource(R.string.profile_report_bug_github_action),
                    subtitle = stringResource(R.string.profile_report_bug_github_desc),
                    onClick = { openSafeBrowserUrl(context, GITHUB_ISSUES_URL) },
                    modifier = Modifier.testTag(ProfileStudioTestTags.REPORT_BUG_GITHUB_BUTTON),
                )
                ProfileSegmentedSettingRow(
                    index = 1,
                    count = 2,
                    icon = Icons.Outlined.Email,
                    title = stringResource(R.string.profile_feedback_email_action),
                    subtitle = stringResource(R.string.profile_feedback_email_desc),
                    onClick = {
                        openFeedbackEmail(
                            context = context,
                            subject = feedbackEmailSubject,
                            body = feedbackEmailBody,
                        )
                    },
                    modifier = Modifier.testTag(ProfileStudioTestTags.FEEDBACK_EMAIL_BUTTON),
                )
            }
        }

        item(key = "about_info_group") {
            ProfileSegmentedListSection(title = stringResource(R.string.profile_about_info_section)) {
                ProfileSegmentedSettingRow(
                    index = 0,
                    count = 2,
                    icon = Icons.Outlined.Description,
                    title = stringResource(R.string.profile_license_action),
                    subtitle = stringResource(R.string.profile_license_desc),
                    onClick = { showLicenseDialog = true },
                    modifier = Modifier.testTag(ProfileStudioTestTags.OPEN_LICENSE_BUTTON),
                )
                ProfileSegmentedSettingRow(
                    index = 1,
                    count = 2,
                    icon = Icons.Outlined.Info,
                    title = stringResource(R.string.profile_about_action),
                    subtitle = stringResource(
                        R.string.profile_about_desc_format,
                        BuildConfig.VERSION_NAME,
                        BuildConfig.VERSION_CODE,
                        channelLabel,
                    ),
                    onClick = { showAboutDialog = true },
                    modifier = Modifier.testTag(ProfileStudioTestTags.OPEN_ABOUT_BUTTON),
                )
            }
        }
    }

    if (showLicenseDialog) {
        ProfileOpenSourceLicenseDialog(onDismiss = { showLicenseDialog = false })
    }

    if (showAboutDialog) {
        ProfileAboutWenkuDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
internal fun ProfileBackupExportModeDialog(
    onSelectMode: (BackupArchiveMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.profile_backup_mode_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
internal fun ProfileOpenSourceLicenseDialog(onDismiss: () -> Unit) {
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
internal fun ProfileAboutWenkuDialog(onDismiss: () -> Unit) {
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
                ProfileAboutSectionCard(title = stringResource(R.string.about_wenku8_title)) {
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

                    Text(
                        text = stringResource(
                            R.string.about_channel_format,
                            distributionChannelLabel(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(ProfileStudioTestTags.ABOUT_CHANNEL_TEXT),
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

                ProfileAboutSectionCard(title = stringResource(R.string.about_software_arch)) {
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

                ProfileAboutSectionCard(title = stringResource(R.string.about_acknowledgement)) {
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
private fun ProfileAboutSectionCard(
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
