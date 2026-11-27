package org.mewx.wenku8.ui.profile

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import org.mewx.wenku8.R
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.data.repository.CustomCacheBookItem
import org.mewx.wenku8.data.repository.CustomCacheCleanRequest
import org.mewx.wenku8.data.repository.CustomCacheInventory
import org.mewx.wenku8.data.repository.StorageAndCacheRepository
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder

internal data class CustomCleanSelectionSummary(
    val selectedCategoryCount: Int,
    val selectedChapterCount: Int,
    val estimatedReclaimedBytes: Long,
) {
    val hasAnySelection: Boolean
        get() = selectedCategoryCount > 0 || selectedChapterCount > 0
}

internal fun computeTriStateForIds(
    groupIds: Set<Int>,
    selectedIds: Set<Int>,
): ToggleableState {
    if (groupIds.isEmpty()) return ToggleableState.Off
    var matched = 0
    for (id in groupIds) {
        if (id in selectedIds) matched++
    }
    return when (matched) {
        0 -> ToggleableState.Off
        groupIds.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
}

internal fun toggleIdGroupSelection(
    currentSelected: Set<Int>,
    groupIds: Set<Int>,
): Set<Int> {
    if (groupIds.isEmpty()) return currentSelected
    val allSelected = groupIds.all { it in currentSelected }
    return if (allSelected) {
        currentSelected - groupIds
    } else {
        currentSelected + groupIds
    }
}

internal fun computeCustomCleanSelectionSummary(
    inventory: CustomCacheInventory,
    request: CustomCacheCleanRequest,
): CustomCleanSelectionSummary {
    var categoryCount = 0
    var bytes = 0L
    if (request.cleanTempCache) {
        categoryCount++
        bytes += inventory.tempCacheBytes
    }
    if (request.cleanNonBookshelfBrowse) {
        categoryCount++
        bytes += inventory.nonBookshelfBrowseBytes
    }
    if (request.cleanOrphanImages) {
        categoryCount++
        bytes += inventory.orphanImageBytes
    }

    val effectiveChapterCids = if (request.cleanNonBookshelfBrowse) {
        buildSet {
            addAll(request.selectedChapterCids)
            for (book in inventory.books) {
                if (!book.inBookshelf) {
                    addAll(book.allCids)
                }
            }
        }
    } else {
        request.selectedChapterCids
    }

    var chapterCount = 0
    for (book in inventory.books) {
        for (volume in book.volumes) {
            for (chapter in volume.chapters) {
                if (chapter.cid in effectiveChapterCids) {
                    chapterCount++
                    bytes += chapter.totalBytes
                }
            }
        }
    }
    return CustomCleanSelectionSummary(
        selectedCategoryCount = categoryCount,
        selectedChapterCount = chapterCount,
        estimatedReclaimedBytes = bytes,
    )
}

/**
 * Material 3 Expressive (`M3e`) Custom Cache Cleanup screen (`存储与备份 -> 自定义清理`, `#223`).
 *
 * Presents a single-level M3e expandable hierarchy (`Categories` + `Books (with covers) -> Volumes
 * -> Chapters`) paired with `TriStateCheckbox` selection and a pinned bottom summary/action bar so
 * readers can inspect storage usage and clean across categories, books, volumes, or chapters in a
 * single pass. Once all cached chapters of a book are cleaned, the entire book row disappears.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun CustomCacheCleanBody(
    horizontalPadding: Dp,
    storageAndCacheRepository: StorageAndCacheRepository?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isEinkMode = LocalEinkMode.current
    val coroutineScope = rememberCoroutineScope()

    var inventory by remember { mutableStateOf<CustomCacheInventory?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isCleaning by remember { mutableStateOf(false) }
    var statusMessage by rememberSaveable { mutableStateOf<String?>(null) }

    var selectTempCache by rememberSaveable { mutableStateOf(false) }
    var selectNonBookshelfBrowse by rememberSaveable { mutableStateOf(false) }
    var selectOrphanImages by rememberSaveable { mutableStateOf(false) }
    var selectedChapterCids by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    var expandedBookIds by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    var expandedVolumeIds by rememberSaveable { mutableStateOf<List<Int>>(emptyList()) }
    var showConfirmDialog by rememberSaveable { mutableStateOf(false) }

    suspend fun reloadInventory() {
        val repo = storageAndCacheRepository
        if (repo == null) {
            inventory = CustomCacheInventory(
                tempCacheBytes = 0L,
                tempCacheFileCount = 0,
                nonBookshelfBrowseBytes = 0L,
                nonBookshelfCoverCount = 0,
                nonBookshelfNovelCount = 0,
                orphanImageBytes = 0L,
                orphanImageFileCount = 0,
                books = emptyList(),
            )
            isLoading = false
            return
        }
        isLoading = true
        val loaded = repo.inspectCustomCacheInventory()
        val liveCids = loaded.books.flatMapTo(HashSet()) { it.allCids }
        val liveBookIds = loaded.books.mapTo(HashSet()) { it.aid }
        val liveVolumeIds = loaded.books.flatMapTo(HashSet()) { b -> b.volumes.map { it.vid } }
        selectedChapterCids = selectedChapterCids.filter { it in liveCids }
        expandedBookIds = expandedBookIds.filter { it in liveBookIds }
        expandedVolumeIds = expandedVolumeIds.filter { it in liveVolumeIds }
        if (loaded.tempCacheBytes == 0L && loaded.tempCacheFileCount == 0) selectTempCache = false
        if (loaded.nonBookshelfBrowseBytes == 0L && loaded.nonBookshelfCoverCount == 0 && loaded.nonBookshelfNovelCount == 0) {
            selectNonBookshelfBrowse = false
        }
        if (loaded.orphanImageBytes == 0L && loaded.orphanImageFileCount == 0) selectOrphanImages = false
        inventory = loaded
        isLoading = false
    }

    LaunchedEffect(storageAndCacheRepository) {
        reloadInventory()
    }

    val currentInventory = inventory
    val selectedCidSet = remember(selectedChapterCids) { selectedChapterCids.toSet() }
    val expandedBookSet = remember(expandedBookIds) { expandedBookIds.toSet() }
    val expandedVolumeSet = remember(expandedVolumeIds) { expandedVolumeIds.toSet() }

    val activeRequest = CustomCacheCleanRequest(
        cleanTempCache = selectTempCache,
        cleanNonBookshelfBrowse = selectNonBookshelfBrowse,
        cleanOrphanImages = selectOrphanImages,
        selectedChapterCids = selectedCidSet,
    )
    val selectionSummary = remember(currentInventory, activeRequest) {
        if (currentInventory == null) {
            CustomCleanSelectionSummary(0, 0, 0L)
        } else {
            computeCustomCleanSelectionSummary(currentInventory, activeRequest)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (isLoading && currentInventory == null) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                if (isEinkMode) {
                    CircularProgressIndicator()
                } else {
                    LoadingIndicator()
                }
            }
        } else if (currentInventory != null) {
            val regularCacheBytes = currentInventory.tempCacheBytes +
                currentInventory.nonBookshelfBrowseBytes +
                currentInventory.orphanImageBytes
            val canSelectTemp = currentInventory.tempCacheBytes > 0L || currentInventory.tempCacheFileCount > 0
            val canSelectBrowse = currentInventory.nonBookshelfBrowseBytes > 0L ||
                currentInventory.nonBookshelfCoverCount > 0 ||
                currentInventory.nonBookshelfNovelCount > 0
            val canSelectOrphan = currentInventory.orphanImageBytes > 0L || currentInventory.orphanImageFileCount > 0
            val hasAnyCleanableItem = canSelectTemp || canSelectBrowse || canSelectOrphan || currentInventory.books.isNotEmpty()

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag(ProfileStudioTestTags.CUSTOM_CLEAN_LIST),
                contentPadding = PaddingValues(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = 12.dp,
                    bottom = 20.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                if (statusMessage != null) {
                    item(key = "custom_clean_status_banner") {
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
                                text = statusMessage!!,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            )
                        }
                    }
                }

                // 1. M3e Expressive Storage Summary Hero Card
                item(key = "custom_clean_hero") {
                    val heroShape = RoundedCornerShape(24.dp)
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = heroShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .einkHairlineBorder(heroShape),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.profile_cache_custom_total_label),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                            Text(
                                text = formatByteSize(currentInventory.totalBytes),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = stringResource(
                                    R.string.profile_cache_custom_total_breakdown_format,
                                    formatByteSize(regularCacheBytes),
                                    currentInventory.books.size,
                                    currentInventory.totalCachedChapters,
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        selectTempCache = canSelectTemp
                                        selectNonBookshelfBrowse = canSelectBrowse
                                        selectOrphanImages = canSelectOrphan
                                        selectedChapterCids = currentInventory.books.flatMap { it.allCids }
                                    },
                                    enabled = hasAnyCleanableItem && !isCleaning,
                                    modifier = Modifier.testTag(ProfileStudioTestTags.CUSTOM_CLEAN_SELECT_ALL),
                                ) {
                                    Text(text = stringResource(R.string.profile_cache_custom_select_all))
                                }
                                OutlinedButton(
                                    onClick = {
                                        selectTempCache = false
                                        selectNonBookshelfBrowse = false
                                        selectOrphanImages = false
                                        selectedChapterCids = emptyList()
                                    },
                                    enabled = selectionSummary.hasAnySelection && !isCleaning,
                                    modifier = Modifier.testTag(ProfileStudioTestTags.CUSTOM_CLEAN_CLEAR_SELECTION),
                                ) {
                                    Text(text = stringResource(R.string.profile_cache_custom_clear_selection))
                                }
                            }
                        }
                    }
                }

                // 2. Category Segmented Section (`M3e`)
                item(key = "custom_clean_categories") {
                    ProfileSegmentedListSection(
                        title = stringResource(R.string.profile_cache_custom_categories_section),
                    ) {
                        CustomCacheCategoryRow(
                            index = 0,
                            count = 3,
                            icon = Icons.Outlined.CleaningServices,
                            title = stringResource(R.string.profile_cache_custom_cat_temp_title),
                            subtitle = stringResource(
                                R.string.profile_cache_custom_cat_temp_desc_format,
                                currentInventory.tempCacheFileCount,
                            ),
                            sizeLabel = formatByteSize(currentInventory.tempCacheBytes),
                            checked = selectTempCache,
                            enabled = canSelectTemp && !isCleaning,
                            onToggle = { selectTempCache = !selectTempCache },
                            modifier = Modifier.testTag(ProfileStudioTestTags.CUSTOM_CLEAN_CAT_TEMP),
                        )
                        CustomCacheCategoryRow(
                            index = 1,
                            count = 3,
                            icon = Icons.Outlined.PhotoLibrary,
                            title = stringResource(R.string.profile_cache_custom_cat_browse_title),
                            subtitle = stringResource(
                                R.string.profile_cache_custom_cat_browse_desc_format,
                                currentInventory.nonBookshelfCoverCount,
                                currentInventory.nonBookshelfNovelCount,
                            ),
                            sizeLabel = formatByteSize(currentInventory.nonBookshelfBrowseBytes),
                            checked = selectNonBookshelfBrowse,
                            enabled = canSelectBrowse && !isCleaning,
                            onToggle = { selectNonBookshelfBrowse = !selectNonBookshelfBrowse },
                            modifier = Modifier.testTag(ProfileStudioTestTags.CUSTOM_CLEAN_CAT_BROWSE),
                        )
                        CustomCacheCategoryRow(
                            index = 2,
                            count = 3,
                            icon = Icons.Outlined.BrokenImage,
                            title = stringResource(R.string.profile_cache_custom_cat_orphan_title),
                            subtitle = stringResource(
                                R.string.profile_cache_custom_cat_orphan_desc_format,
                                currentInventory.orphanImageFileCount,
                            ),
                            sizeLabel = formatByteSize(currentInventory.orphanImageBytes),
                            checked = selectOrphanImages,
                            enabled = canSelectOrphan && !isCleaning,
                            onToggle = { selectOrphanImages = !selectOrphanImages },
                            modifier = Modifier.testTag(ProfileStudioTestTags.CUSTOM_CLEAN_CAT_ORPHAN),
                        )
                    }
                }

                // 3. Offline Books Section Header
                item(key = "custom_clean_books_header") {
                    Text(
                        text = stringResource(R.string.profile_cache_custom_books_section),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }

                if (currentInventory.books.isEmpty()) {
                    item(key = "custom_clean_books_empty") {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.large,
                            modifier = Modifier
                                .fillMaxWidth()
                                .einkHairlineBorder(MaterialTheme.shapes.large),
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Text(
                                    text = stringResource(R.string.profile_cache_custom_books_empty),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    items(
                        items = currentInventory.books,
                        key = { book -> "custom_book_${book.aid}" },
                    ) { book ->
                        CustomCacheBookCard(
                            book = book,
                            selectedChapterCids = selectedCidSet,
                            isBookExpanded = book.aid in expandedBookSet,
                            expandedVolumeIds = expandedVolumeSet,
                            enabled = !isCleaning,
                            onToggleBookSelection = {
                                selectedChapterCids = toggleIdGroupSelection(
                                    currentSelected = selectedCidSet,
                                    groupIds = book.allCids,
                                ).toList()
                            },
                            onToggleBookExpand = {
                                expandedBookIds = if (book.aid in expandedBookSet) {
                                    expandedBookIds - book.aid
                                } else {
                                    expandedBookIds + book.aid
                                }
                            },
                            onToggleVolumeSelection = { volumeCids ->
                                selectedChapterCids = toggleIdGroupSelection(
                                    currentSelected = selectedCidSet,
                                    groupIds = volumeCids,
                                ).toList()
                            },
                            onToggleVolumeExpand = { vid ->
                                expandedVolumeIds = if (vid in expandedVolumeSet) {
                                    expandedVolumeIds - vid
                                } else {
                                    expandedVolumeIds + vid
                                }
                            },
                            onToggleChapterSelection = { cid ->
                                selectedChapterCids = if (cid in selectedCidSet) {
                                    selectedChapterCids - cid
                                } else {
                                    selectedChapterCids + cid
                                }
                            },
                        )
                    }
                }
            }

            // 4. Pinned M3e Bottom Summary & Action Bar
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = if (isEinkMode) 0.dp else 3.dp,
                shadowElevation = if (isEinkMode) 0.dp else 6.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = horizontalPadding, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(
                                    R.string.profile_cache_custom_bottom_estimate_format,
                                    formatByteSize(selectionSummary.estimatedReclaimedBytes),
                                ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (selectionSummary.hasAnySelection) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = if (selectionSummary.hasAnySelection) {
                                    stringResource(
                                        R.string.profile_cache_custom_bottom_selected_format,
                                        selectionSummary.selectedCategoryCount,
                                        selectionSummary.selectedChapterCount,
                                    )
                                } else {
                                    stringResource(R.string.profile_cache_custom_bottom_hint)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        Button(
                            onClick = { showConfirmDialog = true },
                            enabled = selectionSummary.hasAnySelection && !isCleaning && storageAndCacheRepository != null,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                            modifier = Modifier.testTag(ProfileStudioTestTags.CUSTOM_CLEAN_ACTION_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteSweep,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.profile_cache_custom_clean_button),
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showConfirmDialog && currentInventory != null) {
        AlertDialog(
            onDismissRequest = { if (!isCleaning) showConfirmDialog = false },
            modifier = Modifier.testTag(ProfileStudioTestTags.CUSTOM_CLEAN_CONFIRM_DIALOG),
            title = { Text(text = stringResource(R.string.profile_cache_custom_confirm_title)) },
            text = {
                Text(
                    text = stringResource(
                        R.string.profile_cache_custom_confirm_message_format,
                        selectionSummary.selectedCategoryCount,
                        selectionSummary.selectedChapterCount,
                        formatByteSize(selectionSummary.estimatedReclaimedBytes),
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val repo = storageAndCacheRepository ?: return@TextButton
                        showConfirmDialog = false
                        isCleaning = true
                        val snapshotRequest = activeRequest
                        coroutineScope.launch {
                            try {
                                val result = repo.runCustomCacheClean(snapshotRequest)
                                selectTempCache = false
                                selectNonBookshelfBrowse = false
                                selectOrphanImages = false
                                selectedChapterCids = emptyList()
                                statusMessage = context.getString(
                                    R.string.profile_cache_custom_result_format,
                                    result.deletedChaptersCount,
                                    result.deletedFileCount,
                                    formatByteSize(result.reclaimedBytes),
                                )
                                reloadInventory()
                            } finally {
                                isCleaning = false
                            }
                        }
                    },
                    modifier = Modifier.testTag(ProfileStudioTestTags.CUSTOM_CLEAN_CONFIRM_BUTTON),
                ) {
                    Text(
                        text = stringResource(R.string.profile_cache_custom_clean_button),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(text = stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun CustomCacheCategoryRow(
    index: Int,
    count: Int,
    icon: ImageVector,
    title: String,
    subtitle: String,
    sizeLabel: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = profileSegmentedItemShape(index, count)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = shape,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .clickable(enabled = enabled, onClick = onToggle)
            .einkHairlineBorder(shape)
            .alpha(if (enabled) 1f else 0.52f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 60.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Checkbox(
                checked = checked,
                onCheckedChange = null,
                enabled = enabled,
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = sizeLabel,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun CustomCacheBookCard(
    book: CustomCacheBookItem,
    selectedChapterCids: Set<Int>,
    isBookExpanded: Boolean,
    expandedVolumeIds: Set<Int>,
    enabled: Boolean,
    onToggleBookSelection: () -> Unit,
    onToggleBookExpand: () -> Unit,
    onToggleVolumeSelection: (Set<Int>) -> Unit,
    onToggleVolumeExpand: (Int) -> Unit,
    onToggleChapterSelection: (Int) -> Unit,
) {
    val bookState = computeTriStateForIds(book.allCids, selectedChapterCids)
    val cardShape = RoundedCornerShape(20.dp)
    val coverShape = MaterialTheme.shapes.small

    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        shape = cardShape,
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(cardShape)
            .testTag(ProfileStudioTestTags.CUSTOM_CLEAN_BOOK_PREFIX + book.aid),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Book Header Row with Cover Thumbnail + TriStateCheckbox + Expand Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled, onClick = onToggleBookExpand)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TriStateCheckbox(
                    state = bookState,
                    onClick = onToggleBookSelection,
                    enabled = enabled,
                    modifier = Modifier.testTag(ProfileStudioTestTags.CUSTOM_CLEAN_BOOK_CHECK_PREFIX + book.aid),
                )

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(width = 44.dp, height = 62.dp)
                        .clip(coverShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .einkHairlineBorder(coverShape),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                        modifier = Modifier.size(20.dp),
                    )
                    if (book.coverUrl.isNotBlank()) {
                        AsyncImage(
                            model = WenkuImageRequests.rememberCoverImageRequest(
                                aid = book.aid,
                                coverUrl = book.coverUrl,
                                enableCrossfade = false,
                            ),
                            contentDescription = book.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = book.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = stringResource(
                                R.string.profile_cache_custom_book_meta_format,
                                book.cachedChapterCount,
                                formatByteSize(book.totalBytes),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (book.inBookshelf) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            },
                            contentColor = if (book.inBookshelf) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        ) {
                            Text(
                                text = stringResource(
                                    if (book.inBookshelf) {
                                        R.string.profile_cache_custom_book_in_shelf
                                    } else {
                                        R.string.profile_cache_custom_book_not_in_shelf
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                Icon(
                    imageVector = if (isBookExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (isBookExpanded) {
                book.volumes.forEach { volume ->
                    val volCids = remember(volume.vid, volume.chapters) { volume.allCids }
                    val volState = computeTriStateForIds(volCids, selectedChapterCids)
                    val isVolumeExpanded = volume.vid in expandedVolumeIds

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled) { onToggleVolumeExpand(volume.vid) }
                            .padding(start = 24.dp, end = 14.dp, top = 8.dp, bottom = 8.dp)
                            .testTag(ProfileStudioTestTags.CUSTOM_CLEAN_VOLUME_PREFIX + volume.vid),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TriStateCheckbox(
                            state = volState,
                            onClick = { onToggleVolumeSelection(volCids) },
                            enabled = enabled,
                            modifier = Modifier.testTag(ProfileStudioTestTags.CUSTOM_CLEAN_VOLUME_CHECK_PREFIX + volume.vid),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = volume.volumeTitle,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.profile_cache_custom_volume_meta_format,
                                    volume.cachedChapterCount,
                                    formatByteSize(volume.totalBytes),
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                softWrap = false,
                            )
                        }
                        Icon(
                            imageVector = if (isVolumeExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }

                    if (isVolumeExpanded) {
                        volume.chapters.forEach { chapter ->
                            val isChecked = chapter.cid in selectedChapterCids
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                                modifier = Modifier.padding(start = 48.dp, end = 14.dp),
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = enabled) { onToggleChapterSelection(chapter.cid) }
                                    .padding(start = 40.dp, end = 16.dp, top = 8.dp, bottom = 8.dp)
                                    .testTag(ProfileStudioTestTags.CUSTOM_CLEAN_CHAPTER_PREFIX + chapter.cid),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Checkbox(
                                    checked = isChecked,
                                    onCheckedChange = null,
                                    enabled = enabled,
                                )
                                if (chapter.imageCount > 0) {
                                    Icon(
                                        imageVector = Icons.Outlined.Image,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.tertiary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Text(
                                    text = chapter.chapterTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = formatByteSize(chapter.totalBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
