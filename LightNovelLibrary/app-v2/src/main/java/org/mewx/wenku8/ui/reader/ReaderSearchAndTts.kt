package org.mewx.wenku8.ui.reader

import android.content.Context
import android.media.AudioAttributes
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.mewx.wenku8.tts.KnownTtsEngineCatalog
import org.mewx.wenku8.tts.TtsDiagnosticReason
import org.mewx.wenku8.tts.TtsEngineDescriptor
import org.mewx.wenku8.tts.TtsEngineDiscovery
import org.mewx.wenku8.tts.TtsInitResult
import org.mewx.wenku8.tts.TtsPlaybackConfig
import org.mewx.wenku8.tts.TtsVoiceDescriptor
import org.mewx.wenku8.tts.TtsVoiceFormatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.R
import org.mewx.wenku8.data.local.db.dao.ChapterContentDao
import org.mewx.wenku8.data.local.db.dao.OrderedChapterWithVolume
import org.mewx.wenku8.reader.engine.ChapterAst
import org.mewx.wenku8.reader.engine.ChapterBlock
import org.mewx.wenku8.reader.engine.ChapterTextMatch
import org.mewx.wenku8.reader.engine.WenkuChapterParser
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.components.horizontalScrollFadeEdge
import org.mewx.wenku8.ui.theme.LocalEinkMode
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.util.Locale
import kotlin.coroutines.coroutineContext

// ================================================================================================
// Part 1: In-Book Search (`当前章节` & `已缓存全书`, CursorWindow-safe)
// ================================================================================================

enum class ReaderSearchScope {
    CURRENT_CHAPTER,
    ALL_CACHED_CHAPTERS,
}

data class ReaderInBookSearchResultItem(
    val cid: Int,
    val vid: Int,
    val volumeTitle: String,
    val chapterTitle: String,
    val match: ChapterTextMatch,
)

data class ReaderInBookSearchOutcome(
    val results: List<ReaderInBookSearchResultItem>,
    val scannedChapterCount: Int,
    val isTruncatedAtCap: Boolean,
)

private const val MAX_IN_BOOK_SEARCH_RESULTS = 500

/**
 * Executes an instant in-memory search on [activeChapterAst] (`CURRENT_CHAPTER`) or a cancellable,
 * `2 MB CursorWindow`-safe multi-chapter search across all offline-cached chapters in [orderedToc]
 * (`ALL_CACHED_CHAPTERS`).
 */
suspend fun executeInBookSearch(
    query: String,
    scope: ReaderSearchScope,
    activeChapterAst: ChapterAst?,
    orderedToc: List<OrderedChapterWithVolume>,
    cachedChapterIds: Set<Int>,
    chapterContentDao: ChapterContentDao,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): ReaderInBookSearchOutcome {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isEmpty()) {
        return ReaderInBookSearchOutcome(
            results = emptyList(),
            scannedChapterCount = 0,
            isTruncatedAtCap = false,
        )
    }

    if (scope == ReaderSearchScope.CURRENT_CHAPTER || orderedToc.isEmpty()) {
        val hits = activeChapterAst?.findOccurrences(trimmedQuery).orEmpty()
        val capped = hits.take(MAX_IN_BOOK_SEARCH_RESULTS).map { match ->
            ReaderInBookSearchResultItem(
                cid = activeChapterAst?.cid ?: match.cid,
                vid = activeChapterAst?.vid ?: 0,
                volumeTitle = activeChapterAst?.volumeTitle.orEmpty(),
                chapterTitle = activeChapterAst?.chapterTitle.orEmpty(),
                match = match,
            )
        }
        return ReaderInBookSearchOutcome(
            results = capped,
            scannedChapterCount = if (activeChapterAst != null) 1 else 0,
            isTruncatedAtCap = hits.size > MAX_IN_BOOK_SEARCH_RESULTS,
        )
    }

    return withContext(ioDispatcher) {
        val collected = ArrayList<ReaderInBookSearchResultItem>()
        var scannedChapters = 0
        var truncated = false
        val activeCid = activeChapterAst?.cid ?: -1

        // Search in canonical volume/chapter reading order; include the currently open chapter even
        // if it was streamed online and not yet permanently downloaded to `chapter_contents`.
        val candidateToc = orderedToc.filter { it.cid == activeCid || it.cid in cachedChapterIds }
        val chaptersToScan = if (candidateToc.isNotEmpty()) {
            candidateToc
        } else if (activeChapterAst != null) {
            listOf(
                OrderedChapterWithVolume(
                    cid = activeChapterAst.cid,
                    aid = activeChapterAst.aid,
                    vid = activeChapterAst.vid,
                    chapterTitle = activeChapterAst.chapterTitle,
                    chapterOrder = 0,
                    isIllustrationChapter = activeChapterAst.isDedicatedIllustrationChapter,
                    volumeTitle = activeChapterAst.volumeTitle,
                    volumeOrder = 0,
                ),
            )
        } else {
            emptyList()
        }

        for (entry in chaptersToScan) {
            coroutineContext.ensureActive()
            val ast = if (activeChapterAst != null && entry.cid == activeChapterAst.cid) {
                activeChapterAst
            } else {
                val rawText = chapterContentDao.loadChapterTextSafely(entry.cid) ?: continue
                WenkuChapterParser.parse(
                    rawChapterText = rawText,
                    aid = entry.aid,
                    vid = entry.vid,
                    cid = entry.cid,
                    volumeTitle = entry.volumeTitle,
                    chapterTitle = entry.chapterTitle,
                )
            }
            scannedChapters++
            val matches = ast.findOccurrences(trimmedQuery)
            for (match in matches) {
                if (collected.size >= MAX_IN_BOOK_SEARCH_RESULTS) {
                    truncated = true
                    break
                }
                collected.add(
                    ReaderInBookSearchResultItem(
                        cid = entry.cid,
                        vid = entry.vid,
                        volumeTitle = entry.volumeTitle,
                        chapterTitle = entry.chapterTitle,
                        match = match,
                    ),
                )
            }
            if (truncated) break
        }

        ReaderInBookSearchOutcome(
            results = collected,
            scannedChapterCount = scannedChapters,
            isTruncatedAtCap = truncated,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderInBookSearchSheet(
    activeChapterAst: ChapterAst?,
    orderedToc: List<OrderedChapterWithVolume>,
    cachedChapterIds: Set<Int>,
    chapterContentDao: ChapterContentDao,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    onJumpToResult: (ReaderInBookSearchResultItem) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    var searchScope by rememberSaveable { mutableStateOf(ReaderSearchScope.CURRENT_CHAPTER) }
    var isSearching by remember { mutableStateOf(false) }
    var outcome by remember {
        mutableStateOf(
            ReaderInBookSearchOutcome(
                results = emptyList(),
                scannedChapterCount = 0,
                isTruncatedAtCap = false,
            ),
        )
    }

    LaunchedEffect(query, searchScope, activeChapterAst, orderedToc, cachedChapterIds) {
        val clean = query.trim()
        if (clean.isEmpty()) {
            isSearching = false
            outcome = ReaderInBookSearchOutcome(emptyList(), 0, false)
            return@LaunchedEffect
        }
        // Small debounce only for multi-chapter disk scan; single-chapter AST scan is < 1ms.
        if (searchScope == ReaderSearchScope.ALL_CACHED_CHAPTERS) {
            isSearching = true
            delay(120L)
        }
        val computed = executeInBookSearch(
            query = clean,
            scope = searchScope,
            activeChapterAst = activeChapterAst,
            orderedToc = orderedToc,
            cachedChapterIds = cachedChapterIds,
            chapterContentDao = chapterContentDao,
            ioDispatcher = ioDispatcher,
        )
        outcome = computed
        isSearching = false
    }

    val cachedTotalForDisplay = remember(orderedToc, cachedChapterIds, activeChapterAst) {
        val activeCid = activeChapterAst?.cid ?: -1
        orderedToc.count { it.cid == activeCid || it.cid in cachedChapterIds }
            .coerceAtLeast(if (activeChapterAst != null) 1 else 0)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag(ReaderTestTags.SEARCH_SHEET),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 320.dp, max = 560.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                text = "书内搜索",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = {
                    Text(
                        text = "输入台词、人名或关键词…",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Search,
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        val clearLabel = stringResource(R.string.action_clear)
                        TooltipIconButton(
                            tooltipText = clearLabel,
                            onClick = { query = "" },
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Clear,
                                contentDescription = clearLabel,
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ReaderTestTags.SEARCH_INPUT),
            )

            Spacer(modifier = Modifier.height(8.dp))

            val scopeScrollState = rememberScrollState()
            val isEinkMode = LocalEinkMode.current
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScrollFadeEdge(
                        scrollState = scopeScrollState,
                        fadeColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        isEink = isEinkMode,
                    )
                    .horizontalScroll(scopeScrollState),
            ) {
                FilterChip(
                    selected = searchScope == ReaderSearchScope.CURRENT_CHAPTER,
                    onClick = { searchScope = ReaderSearchScope.CURRENT_CHAPTER },
                    label = {
                        Text(
                            text = "当前章节",
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    modifier = Modifier.testTag(ReaderTestTags.SEARCH_SCOPE_CURRENT_CHIP),
                )
                FilterChip(
                    selected = searchScope == ReaderSearchScope.ALL_CACHED_CHAPTERS,
                    onClick = { searchScope = ReaderSearchScope.ALL_CACHED_CHAPTERS },
                    label = {
                        Text(
                            text = "已缓存全书 ($cachedTotalForDisplay 章)",
                            maxLines = 1,
                            softWrap = false,
                        )
                    },
                    modifier = Modifier.testTag(ReaderTestTags.SEARCH_SCOPE_CACHED_CHIP),
                )
            }

            if (isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                )
            } else if (query.trim().isNotEmpty()) {
                val summary = buildString {
                    append("共找到 ${outcome.results.size}")
                    if (outcome.isTruncatedAtCap) append("+")
                    append(" 处匹配（已检索 ${outcome.scannedChapterCount} 章）")
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            if (query.trim().isEmpty()) {
                Text(
                    text = "支持在当前章节即时检索，或跨全书所有已离线缓存章节秒级定位原句。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else if (!isSearching && outcome.results.isEmpty()) {
                Text(
                    text = "未找到包含「${query.trim()}」的段落",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                val highlightColor = MaterialTheme.colorScheme.primary
                val highlightBg = MaterialTheme.colorScheme.primaryContainer
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    itemsIndexed(
                        items = outcome.results,
                        key = { idx, item ->
                            "${item.cid}_${item.match.blockIndex}_${item.match.startCharOffset}_$idx"
                        },
                    ) { idx, item ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                contentColor = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onJumpToResult(item) }
                                .testTag("${ReaderTestTags.SEARCH_RESULT_PREFIX}$idx"),
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = "${item.volumeTitle} · ${item.chapterTitle}".trim(' ', '·'),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(modifier = Modifier.height(3.dp))
                                Text(
                                    text = highlightMatchInSnippet(
                                        snippet = item.match.contextSnippet,
                                        query = query.trim(),
                                        highlightColor = highlightColor,
                                        highlightBackground = highlightBg,
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun highlightMatchInSnippet(
    snippet: String,
    query: String,
    highlightColor: androidx.compose.ui.graphics.Color,
    highlightBackground: androidx.compose.ui.graphics.Color,
): AnnotatedString {
    if (query.isEmpty()) return AnnotatedString(snippet)
    return buildAnnotatedString {
        var cursor = 0
        while (cursor < snippet.length) {
            val idx = snippet.indexOf(query, startIndex = cursor, ignoreCase = true)
            if (idx < 0) {
                append(snippet.substring(cursor))
                break
            }
            if (idx > cursor) {
                append(snippet.substring(cursor, idx))
            }
            val end = idx + query.length
            withStyle(
                SpanStyle(
                    color = highlightColor,
                    background = highlightBackground,
                    fontWeight = FontWeight.Bold,
                ),
            ) {
                append(snippet.substring(idx, end))
            }
            cursor = end
        }
    }
}

// ================================================================================================
// Part 2: System TextToSpeech (`ReaderTtsController` + `SpeechEngineAdapter`)
// ================================================================================================

/**
 * Single speakable utterance mapped back to a [ChapterBlock.blockIndex] so the reader viewport
 * stays synchronized with the spoken paragraph.
 */
data class TtsUtteranceItem(
    val utteranceIndex: Int,
    val blockIndex: Int,
    val text: String,
)

/**
 * Extracts speakable utterances from [ChapterAst], splitting any very long paragraph (`> 1200`
 * chars) at natural CJK sentence boundaries (`。！？!?`) so every chunk stays well below
 * `TextToSpeech.getMaxSpeechInputLength()` (`4000` chars).
 */
fun extractSpeakableUtterances(
    ast: ChapterAst,
    maxChunkLength: Int = 1200,
): List<TtsUtteranceItem> {
    val items = ArrayList<TtsUtteranceItem>(ast.blocks.size)
    for (block in ast.blocks) {
        val rawText = when (block) {
            is ChapterBlock.TitleBlock -> block.title.trim()
            is ChapterBlock.ParagraphBlock -> block.text.trim()
            is ChapterBlock.IllustrationBlock -> continue
        }
        if (rawText.isEmpty()) continue
        if (rawText.length <= maxChunkLength) {
            items.add(
                TtsUtteranceItem(
                    utteranceIndex = items.size,
                    blockIndex = block.blockIndex,
                    text = rawText,
                ),
            )
        } else {
            var start = 0
            while (start < rawText.length) {
                val tentativeEnd = (start + maxChunkLength).coerceAtMost(rawText.length)
                val splitAt = if (tentativeEnd == rawText.length) {
                    tentativeEnd
                } else {
                    val boundary = rawText.substring(start, tentativeEnd).indexOfLast { ch ->
                        ch == '。' || ch == '！' || ch == '？' || ch == '!' || ch == '?' || ch == '；'
                    }
                    if (boundary >= maxChunkLength / 3) start + boundary + 1 else tentativeEnd
                }
                val slice = rawText.substring(start, splitAt).trim()
                if (slice.isNotEmpty()) {
                    items.add(
                        TtsUtteranceItem(
                            utteranceIndex = items.size,
                            blockIndex = block.blockIndex,
                            text = slice,
                        ),
                    )
                }
                start = splitAt
            }
        }
    }
    return items
}

/**
 * Thin adapter over Android's [TextToSpeech] so both real hardware synthesis and deterministic
 * Robolectric unit tests run through the exact same [ReaderTtsController] state machine.
 */
interface SpeechEngineAdapter {
    fun initialize(
        appLanguage: Wenku8API.AppLanguage,
        onInitialized: (Boolean) -> Unit,
        onUtteranceStarted: (String) -> Unit,
        onUtteranceDone: (String) -> Unit,
        onUtteranceError: (String) -> Unit,
    )

    /**
     * Enhanced configuration-aware initialization with engine enumeration, strict Chinese language
     * verification, offline voice fallback, and multi-engine probe fallback (`docs/designs/v2/tts`).
     */
    fun initializeConfigured(
        appLanguage: Wenku8API.AppLanguage,
        config: TtsPlaybackConfig,
        excludedEnginePackages: Set<String> = emptySet(),
        onInitialized: (TtsInitResult) -> Unit,
        onUtteranceStarted: (String) -> Unit,
        onUtteranceDone: (String) -> Unit,
        onUtteranceError: (String) -> Unit,
    ) {
        initialize(
            appLanguage = appLanguage,
            onInitialized = { ok ->
                onInitialized(
                    TtsInitResult(
                        success = ok,
                        activeEnginePackage = activeEnginePackage(),
                        activeEngineLabel = activeEnginePackage().ifBlank { "系统语音引擎" },
                        diagnosticReason = if (ok) TtsDiagnosticReason.NONE else TtsDiagnosticReason.ENGINE_BIND_FAILED,
                        diagnosticMessage = if (ok) null else "系统语音朗读引擎不可用，请检查系统 TTS 设置",
                    ),
                )
            },
            onUtteranceStarted = onUtteranceStarted,
            onUtteranceDone = onUtteranceDone,
            onUtteranceError = onUtteranceError,
        )
    }

    fun setSpeechRate(rate: Float)

    fun setPitch(pitch: Float) {}

    fun speakFlush(text: String, utteranceId: String): Boolean

    fun speakWithVoiceOverride(
        text: String,
        utteranceId: String,
        voiceNameOverride: String?,
    ): Boolean = speakFlush(text, utteranceId)

    fun listInstalledEngines(): List<TtsEngineDescriptor> = emptyList()

    fun listVoicesForActiveEngine(appLanguage: Wenku8API.AppLanguage): List<TtsVoiceDescriptor> = emptyList()

    fun activeEnginePackage(): String = ""

    fun stop()

    fun shutdown()
}

class AndroidTextToSpeechEngineAdapter(
    private val appContext: Context,
    private val initTimeoutMs: Long = DEFAULT_INIT_TIMEOUT_MS,
) : SpeechEngineAdapter {

    private var tts: TextToSpeech? = null
    private var activePkg: String = ""
    private var activeVoiceName: String = ""
    private var cachedRawVoices: List<Voice> = emptyList()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var probeGeneration: Long = 0L

    override fun listInstalledEngines(): List<TtsEngineDescriptor> =
        TtsEngineDiscovery.discoverInstalledEngines(appContext)

    override fun listVoicesForActiveEngine(appLanguage: Wenku8API.AppLanguage): List<TtsVoiceDescriptor> {
        val raw = cachedRawVoices.ifEmpty {
            runCatching { tts?.voices?.toList().orEmpty() }.getOrDefault(emptyList())
        }
        return raw.map { TtsVoiceFormatter.describeVoice(it) }.sortedWith(
            compareBy<TtsVoiceDescriptor>(
                { if (it.isChinese) 0 else 1 },
                { if (it.isNetworkRequired) 1 else 0 },
                { -it.qualityScore },
                { it.displayLabel },
            ),
        )
    }

    override fun activeEnginePackage(): String = activePkg

    override fun initialize(
        appLanguage: Wenku8API.AppLanguage,
        onInitialized: (Boolean) -> Unit,
        onUtteranceStarted: (String) -> Unit,
        onUtteranceDone: (String) -> Unit,
        onUtteranceError: (String) -> Unit,
    ) {
        initializeConfigured(
            appLanguage = appLanguage,
            config = TtsPlaybackConfig(),
            excludedEnginePackages = emptySet(),
            onInitialized = { result -> onInitialized(result.success) },
            onUtteranceStarted = onUtteranceStarted,
            onUtteranceDone = onUtteranceDone,
            onUtteranceError = onUtteranceError,
        )
    }

    override fun initializeConfigured(
        appLanguage: Wenku8API.AppLanguage,
        config: TtsPlaybackConfig,
        excludedEnginePackages: Set<String>,
        onInitialized: (TtsInitResult) -> Unit,
        onUtteranceStarted: (String) -> Unit,
        onUtteranceDone: (String) -> Unit,
        onUtteranceError: (String) -> Unit,
    ) {
        shutdown()
        val myGen = ++probeGeneration
        val installed = TtsEngineDiscovery.discoverInstalledEngines(appContext)
        val candidates = TtsEngineDiscovery.buildFallbackOrder(
            installedEngines = installed,
            preferredEnginePackage = config.preferredEnginePackage,
            fallbackEnginePackage = config.fallbackEnginePackage,
            autoFallbackEnabled = config.autoFallbackEnabled,
            excludedPackages = excludedEnginePackages,
        )

        val preferredPkg = config.preferredEnginePackage.trim()
        val hasOnlyUnsupported = installed.isNotEmpty() && installed.all { it.isKnownUnsupportedChinese }

        if (candidates.isEmpty()) {
            // If PackageManager returned nothing (rare OEM/test quirk) and user hasn't excluded everything,
            // attempt one direct 2-arg system default bind before declaring NO_ENGINE_INSTALLED.
            if (installed.isEmpty() && excludedEnginePackages.isEmpty()) {
                probeSingleCandidate(
                    generation = myGen,
                    candidate = null,
                    remaining = emptyList(),
                    appLanguage = appLanguage,
                    config = config,
                    sawMissingChineseData = false,
                    sawBindFailure = false,
                    preferredWasAttemptedOrMissing = preferredPkg.isNotEmpty(),
                    hasOnlyUnsupported = false,
                    onInitialized = onInitialized,
                    onUtteranceStarted = onUtteranceStarted,
                    onUtteranceDone = onUtteranceDone,
                    onUtteranceError = onUtteranceError,
                )
            } else {
                val reason = if (installed.isEmpty() || hasOnlyUnsupported) {
                    TtsDiagnosticReason.NO_ENGINE_INSTALLED
                } else {
                    TtsDiagnosticReason.ENGINE_BIND_FAILED
                }
                onInitialized(
                    TtsInitResult(
                        success = false,
                        diagnosticReason = reason,
                        diagnosticMessage = diagnosticMessageFor(reason),
                    ),
                )
            }
            return
        }

        val preferredMissingFromDevice =
            preferredPkg.isNotEmpty() && installed.none { it.packageName == preferredPkg }

        probeSingleCandidate(
            generation = myGen,
            candidate = candidates.first(),
            remaining = candidates.drop(1),
            appLanguage = appLanguage,
            config = config,
            sawMissingChineseData = false,
            sawBindFailure = false,
            preferredWasAttemptedOrMissing = preferredMissingFromDevice,
            hasOnlyUnsupported = hasOnlyUnsupported,
            onInitialized = onInitialized,
            onUtteranceStarted = onUtteranceStarted,
            onUtteranceDone = onUtteranceDone,
            onUtteranceError = onUtteranceError,
        )
    }

    private fun probeSingleCandidate(
        generation: Long,
        candidate: TtsEngineDescriptor?,
        remaining: List<TtsEngineDescriptor>,
        appLanguage: Wenku8API.AppLanguage,
        config: TtsPlaybackConfig,
        sawMissingChineseData: Boolean,
        sawBindFailure: Boolean,
        preferredWasAttemptedOrMissing: Boolean,
        hasOnlyUnsupported: Boolean,
        onInitialized: (TtsInitResult) -> Unit,
        onUtteranceStarted: (String) -> Unit,
        onUtteranceDone: (String) -> Unit,
        onUtteranceError: (String) -> Unit,
    ) {
        if (generation != probeGeneration) return

        val ttsRef = AtomicReference<TextToSpeech?>(null)
        val settled = AtomicBoolean(false)
        val timeoutRunnable = Runnable {
            if (!settled.compareAndSet(false, true)) return@Runnable
            safeShutdownInstance(ttsRef.getAndSet(null))
            if (generation != probeGeneration) return@Runnable
            advanceOrFail(
                generation = generation,
                remaining = remaining,
                appLanguage = appLanguage,
                config = config,
                sawMissingChineseData = sawMissingChineseData,
                sawBindFailure = true,
                preferredWasAttemptedOrMissing = true,
                hasOnlyUnsupported = hasOnlyUnsupported,
                onInitialized = onInitialized,
                onUtteranceStarted = onUtteranceStarted,
                onUtteranceDone = onUtteranceDone,
                onUtteranceError = onUtteranceError,
            )
        }
        mainHandler.postDelayed(timeoutRunnable, initTimeoutMs)

        val listener = TextToSpeech.OnInitListener { status ->
            // Always dispatch onto the main looper after constructor assignment completes to
            // eliminate the synchronous-callback `ttsRef.get() == null` race condition.
            mainHandler.post {
                if (!settled.compareAndSet(false, true)) return@post
                mainHandler.removeCallbacks(timeoutRunnable)
                val engine = ttsRef.get()
                if (generation != probeGeneration) {
                    safeShutdownInstance(engine)
                    return@post
                }
                if (status != TextToSpeech.SUCCESS || engine == null) {
                    safeShutdownInstance(engine)
                    advanceOrFail(
                        generation = generation,
                        remaining = remaining,
                        appLanguage = appLanguage,
                        config = config,
                        sawMissingChineseData = sawMissingChineseData,
                        sawBindFailure = true,
                        preferredWasAttemptedOrMissing = true,
                        hasOnlyUnsupported = hasOnlyUnsupported,
                        onInitialized = onInitialized,
                        onUtteranceStarted = onUtteranceStarted,
                        onUtteranceDone = onUtteranceDone,
                        onUtteranceError = onUtteranceError,
                    )
                    return@post
                }

                // Strictly verify Chinese language & data availability across the SC/TC fallback chain.
                val langCheck = applyChineseLanguageChain(engine, appLanguage)
                if (!langCheck.supported) {
                    safeShutdownInstance(engine)
                    advanceOrFail(
                        generation = generation,
                        remaining = remaining,
                        appLanguage = appLanguage,
                        config = config,
                        sawMissingChineseData = sawMissingChineseData || langCheck.missingData,
                        sawBindFailure = sawBindFailure,
                        preferredWasAttemptedOrMissing = true,
                        hasOnlyUnsupported = hasOnlyUnsupported,
                        onInitialized = onInitialized,
                        onUtteranceStarted = onUtteranceStarted,
                        onUtteranceDone = onUtteranceDone,
                        onUtteranceError = onUtteranceError,
                    )
                    return@post
                }

                // Engine is healthy and speaks Chinese! Configure audio attributes, voice, rate & pitch.
                val resolvedPkg = candidate?.packageName
                    ?: runCatching { engine.defaultEngine.orEmpty() }.getOrDefault("")
                val resolvedDesc = candidate ?: KnownTtsEngineCatalog.describe(resolvedPkg)

                val rawVoices = runCatching { engine.voices?.toList().orEmpty() }.getOrDefault(emptyList())
                val descriptors = rawVoices.map { TtsVoiceFormatter.describeVoice(it) }
                val online = TtsEngineDiscovery.isNetworkConnected(appContext)
                val selectedVoiceDesc = TtsVoiceFormatter.selectBestVoice(
                    voices = descriptors,
                    preferredVoiceName = config.preferredVoiceName,
                    appLanguage = appLanguage,
                    isNetworkAvailable = online,
                )
                if (selectedVoiceDesc != null) {
                    rawVoices.firstOrNull { it.name == selectedVoiceDesc.name }?.let { rawVoice ->
                        runCatching { engine.voice = rawVoice }
                    }
                }

                runCatching {
                    engine.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build(),
                    )
                }
                val safeRate = KnownTtsEngineCatalog.clampSpeechRateForEngine(resolvedPkg, config.speechRate)
                runCatching { engine.setSpeechRate(safeRate) }
                runCatching {
                    engine.setPitch(
                        config.pitch.coerceIn(
                            org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences.MIN_TTS_PITCH,
                            org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences.MAX_TTS_PITCH,
                        ),
                    )
                }

                engine.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            if (utteranceId != null) onUtteranceStarted(utteranceId)
                        }

                        override fun onDone(utteranceId: String?) {
                            if (utteranceId != null) onUtteranceDone(utteranceId)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            if (utteranceId != null) onUtteranceError(utteranceId)
                        }

                        override fun onError(utteranceId: String?, errorCode: Int) {
                            if (utteranceId != null) onUtteranceError(utteranceId)
                        }
                    },
                )

                tts = engine
                activePkg = resolvedPkg
                activeVoiceName = selectedVoiceDesc?.name.orEmpty()
                cachedRawVoices = rawVoices

                val preferredPkg = config.preferredEnginePackage.trim()
                val isFallback = (preferredPkg.isNotEmpty() && resolvedPkg != preferredPkg) ||
                    preferredWasAttemptedOrMissing
                val notice = if (isFallback) {
                    "首选语音引擎暂不可用，已自动回退至 ${resolvedDesc.displayName}"
                } else {
                    null
                }
                onInitialized(
                    TtsInitResult(
                        success = true,
                        activeEnginePackage = resolvedPkg,
                        activeEngineLabel = resolvedDesc.displayName,
                        activeVoiceName = activeVoiceName,
                        isFallbackActive = isFallback,
                        fallbackNoticeMessage = notice,
                        diagnosticReason = TtsDiagnosticReason.NONE,
                        availableVoices = listVoicesForActiveEngine(appLanguage),
                    ),
                )
            }
        }

        val created = runCatching {
            if (candidate != null && candidate.packageName.isNotBlank()) {
                TextToSpeech(appContext, listener, candidate.packageName)
            } else {
                TextToSpeech(appContext, listener)
            }
        }.getOrNull()

        if (created == null) {
            if (settled.compareAndSet(false, true)) {
                mainHandler.removeCallbacks(timeoutRunnable)
                advanceOrFail(
                    generation = generation,
                    remaining = remaining,
                    appLanguage = appLanguage,
                    config = config,
                    sawMissingChineseData = sawMissingChineseData,
                    sawBindFailure = true,
                    preferredWasAttemptedOrMissing = true,
                    hasOnlyUnsupported = hasOnlyUnsupported,
                    onInitialized = onInitialized,
                    onUtteranceStarted = onUtteranceStarted,
                    onUtteranceDone = onUtteranceDone,
                    onUtteranceError = onUtteranceError,
                )
            }
            return
        }
        ttsRef.set(created)
    }

    private fun advanceOrFail(
        generation: Long,
        remaining: List<TtsEngineDescriptor>,
        appLanguage: Wenku8API.AppLanguage,
        config: TtsPlaybackConfig,
        sawMissingChineseData: Boolean,
        sawBindFailure: Boolean,
        preferredWasAttemptedOrMissing: Boolean,
        hasOnlyUnsupported: Boolean,
        onInitialized: (TtsInitResult) -> Unit,
        onUtteranceStarted: (String) -> Unit,
        onUtteranceDone: (String) -> Unit,
        onUtteranceError: (String) -> Unit,
    ) {
        if (generation != probeGeneration) return
        if (remaining.isNotEmpty()) {
            probeSingleCandidate(
                generation = generation,
                candidate = remaining.first(),
                remaining = remaining.drop(1),
                appLanguage = appLanguage,
                config = config,
                sawMissingChineseData = sawMissingChineseData,
                sawBindFailure = sawBindFailure,
                preferredWasAttemptedOrMissing = preferredWasAttemptedOrMissing,
                hasOnlyUnsupported = hasOnlyUnsupported,
                onInitialized = onInitialized,
                onUtteranceStarted = onUtteranceStarted,
                onUtteranceDone = onUtteranceDone,
                onUtteranceError = onUtteranceError,
            )
            return
        }

        val reason = when {
            sawMissingChineseData -> TtsDiagnosticReason.MISSING_CHINESE_DATA
            hasOnlyUnsupported -> TtsDiagnosticReason.NO_ENGINE_INSTALLED
            sawBindFailure -> TtsDiagnosticReason.ENGINE_BIND_FAILED
            else -> TtsDiagnosticReason.NO_ENGINE_INSTALLED
        }
        onInitialized(
            TtsInitResult(
                success = false,
                diagnosticReason = reason,
                diagnosticMessage = diagnosticMessageFor(reason),
            ),
        )
    }

    private data class LanguageCheckResult(
        val supported: Boolean,
        val missingData: Boolean,
    )

    private fun applyChineseLanguageChain(
        engine: TextToSpeech,
        appLanguage: Wenku8API.AppLanguage,
    ): LanguageCheckResult {
        var sawMissing = false
        for (locale in TtsVoiceFormatter.preferredLocaleChain(appLanguage)) {
            val res = runCatching { engine.setLanguage(locale) }
                .getOrDefault(TextToSpeech.LANG_NOT_SUPPORTED)
            if (res >= TextToSpeech.LANG_AVAILABLE) {
                return LanguageCheckResult(supported = true, missingData = false)
            }
            if (res == TextToSpeech.LANG_MISSING_DATA) {
                sawMissing = true
            }
        }
        return LanguageCheckResult(supported = false, missingData = sawMissing)
    }

    override fun setSpeechRate(rate: Float) {
        val clamped = KnownTtsEngineCatalog.clampSpeechRateForEngine(activePkg, rate)
        runCatching { tts?.setSpeechRate(clamped) }
    }

    override fun setPitch(pitch: Float) {
        val clamped = pitch.coerceIn(
            org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences.MIN_TTS_PITCH,
            org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences.MAX_TTS_PITCH,
        )
        runCatching { tts?.setPitch(clamped) }
    }

    override fun speakFlush(text: String, utteranceId: String): Boolean {
        val engine = tts ?: return false
        val res = runCatching {
            engine.speak(text, TextToSpeech.QUEUE_FLUSH, Bundle.EMPTY, utteranceId)
        }.getOrDefault(TextToSpeech.ERROR)
        return res == TextToSpeech.SUCCESS
    }

    override fun speakWithVoiceOverride(
        text: String,
        utteranceId: String,
        voiceNameOverride: String?,
    ): Boolean {
        val engine = tts ?: return false
        val targetVoiceName = voiceNameOverride?.trim().orEmpty()
        if (targetVoiceName.isNotEmpty()) {
            cachedRawVoices.firstOrNull { it.name == targetVoiceName }?.let { rawVoice ->
                runCatching { engine.voice = rawVoice }
            }
        } else if (activeVoiceName.isNotEmpty()) {
            cachedRawVoices.firstOrNull { it.name == activeVoiceName }?.let { rawVoice ->
                runCatching { engine.voice = rawVoice }
            }
        }
        return speakFlush(text, utteranceId)
    }

    override fun stop() {
        runCatching { tts?.stop() }
    }

    override fun shutdown() {
        probeGeneration++
        safeShutdownInstance(tts)
        tts = null
        activePkg = ""
        activeVoiceName = ""
        cachedRawVoices = emptyList()
    }

    private fun safeShutdownInstance(instance: TextToSpeech?) {
        if (instance == null) return
        runCatching { instance.stop() }
        runCatching { instance.shutdown() }
    }

    companion object {
        const val DEFAULT_INIT_TIMEOUT_MS = 4_000L

        fun diagnosticMessageFor(reason: TtsDiagnosticReason): String = when (reason) {
            TtsDiagnosticReason.NO_ENGINE_INSTALLED ->
                "未检测到支持中文的语音朗读引擎，点击查看免费安装引导"
            TtsDiagnosticReason.MISSING_CHINESE_DATA ->
                "当前语音引擎缺少中文离线语音包，点击下载语音数据或切换引擎"
            TtsDiagnosticReason.ENGINE_BIND_FAILED ->
                "系统语音引擎初始化失败或被拦截，点击切换引擎或检查系统 TTS 设置"
            TtsDiagnosticReason.PLAYBACK_INTERRUPTED ->
                "语音合成连续中断，点击重试或切换备用引擎"
            TtsDiagnosticReason.NONE -> ""
        }
    }
}

data class ReaderTtsUiState(
    val isSessionOpen: Boolean = false,
    val isSpeaking: Boolean = false,
    val isEngineReady: Boolean = false,
    val engineErrorMessage: String? = null,
    val activeCid: Int = 0,
    val activeUtteranceIndex: Int = 0,
    val totalUtterances: Int = 0,
    val activeBlockIndex: Int = 0,
    val currentSnippet: String = "",
    val speechRate: Float = 1.0f,
) {
    companion object {
        val SUPPORTED_SPEECH_RATES: List<Float> = listOf(0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    }
}

/**
 * Coordinates Android `TextToSpeech` playback across paragraphs and chapters inside
 * `UnifiedReaderScreen` (`P5.3`).
 */
class ReaderTtsController(
    private val engineAdapter: SpeechEngineAdapter,
    private val scope: CoroutineScope,
    private val onChapterFinished: (completedCid: Int) -> Unit = {},
) {
    private val _uiState = MutableStateFlow(ReaderTtsUiState())
    val uiState: StateFlow<ReaderTtsUiState> = _uiState.asStateFlow()

    private var utterances: List<TtsUtteranceItem> = emptyList()
    private var engineInitialized = false
    private var engineInitializing = false
    private var initializedLang: Wenku8API.AppLanguage? = null
    private var pendingSpeakAfterInit = false
    private var generationCounter = 0L

    fun startChapter(
        ast: ChapterAst,
        startFromBlockIndex: Int,
        appLanguage: Wenku8API.AppLanguage,
    ) {
        val extracted = extractSpeakableUtterances(ast)
        if (extracted.isEmpty()) {
            utterances = emptyList()
            _uiState.update {
                it.copy(
                    isSessionOpen = true,
                    isSpeaking = false,
                    activeCid = ast.cid,
                    activeUtteranceIndex = 0,
                    totalUtterances = 0,
                    currentSnippet = "",
                    engineErrorMessage = "本章为纯插图或空白章节，无可朗读正文",
                )
            }
            return
        }
        utterances = extracted
        val startIdx = extracted.indexOfFirst { it.blockIndex >= startFromBlockIndex }
            .let { if (it >= 0) it else 0 }
        val targetItem = extracted[startIdx]

        _uiState.update {
            it.copy(
                isSessionOpen = true,
                isSpeaking = true,
                engineErrorMessage = null,
                activeCid = ast.cid,
                activeUtteranceIndex = startIdx,
                totalUtterances = extracted.size,
                activeBlockIndex = targetItem.blockIndex,
                currentSnippet = targetItem.text.take(48),
            )
        }

        ensureEngineAndSpeakCurrent(appLanguage)
    }

    private fun ensureEngineAndSpeakCurrent(appLanguage: Wenku8API.AppLanguage) {
        if (engineInitialized && initializedLang == appLanguage) {
            speakCurrentUtteranceNow()
            return
        }
        pendingSpeakAfterInit = true
        if (engineInitializing && initializedLang == appLanguage) {
            return
        }
        engineInitializing = true
        initializedLang = appLanguage
        engineAdapter.initialize(
            appLanguage = appLanguage,
            onInitialized = { success ->
                scope.launch {
                    engineInitializing = false
                    engineInitialized = success
                    if (!success) {
                        pendingSpeakAfterInit = false
                        _uiState.update {
                            it.copy(
                                isSpeaking = false,
                                isEngineReady = false,
                                engineErrorMessage = "系统语音朗读引擎不可用，请检查系统 TTS 设置",
                            )
                        }
                    } else {
                        engineAdapter.setSpeechRate(_uiState.value.speechRate)
                        _uiState.update { it.copy(isEngineReady = true, engineErrorMessage = null) }
                        if (pendingSpeakAfterInit && _uiState.value.isSessionOpen) {
                            pendingSpeakAfterInit = false
                            speakCurrentUtteranceNow()
                        }
                    }
                }
            },
            onUtteranceStarted = { /* UI already updated optimistically on dispatch */ },
            onUtteranceDone = { utteranceId ->
                scope.launch {
                    handleUtteranceCompleted(utteranceId)
                }
            },
            onUtteranceError = { utteranceId ->
                scope.launch {
                    if (isCurrentGenerationUtterance(utteranceId)) {
                        _uiState.update {
                            it.copy(
                                isSpeaking = false,
                                engineErrorMessage = "语音合成中断，点击播放可继续",
                            )
                        }
                    }
                }
            },
        )
    }

    private fun buildUtteranceId(gen: Long, index: Int): String = "wk8_tts_${gen}_$index"

    private fun isCurrentGenerationUtterance(utteranceId: String): Boolean {
        val curState = _uiState.value
        val expected = buildUtteranceId(generationCounter, curState.activeUtteranceIndex)
        return utteranceId == expected
    }

    private fun speakCurrentUtteranceNow() {
        val state = _uiState.value
        val item = utterances.getOrNull(state.activeUtteranceIndex) ?: return
        generationCounter++
        val utteranceId = buildUtteranceId(generationCounter, item.utteranceIndex)
        engineAdapter.setSpeechRate(state.speechRate)
        val ok = engineAdapter.speakFlush(item.text, utteranceId)
        _uiState.update {
            it.copy(
                isSpeaking = ok,
                activeBlockIndex = item.blockIndex,
                currentSnippet = item.text.take(48),
                engineErrorMessage = if (ok) null else "启动系统朗读失败",
            )
        }
    }

    private fun handleUtteranceCompleted(utteranceId: String) {
        val state = _uiState.value
        if (!state.isSessionOpen || !state.isSpeaking) return
        if (!isCurrentGenerationUtterance(utteranceId)) return

        val nextIdx = state.activeUtteranceIndex + 1
        if (nextIdx < utterances.size) {
            val nextItem = utterances[nextIdx]
            _uiState.update {
                it.copy(
                    activeUtteranceIndex = nextIdx,
                    activeBlockIndex = nextItem.blockIndex,
                    currentSnippet = nextItem.text.take(48),
                )
            }
            speakCurrentUtteranceNow()
        } else {
            val completedCid = state.activeCid
            _uiState.update { it.copy(isSpeaking = false) }
            onChapterFinished(completedCid)
        }
    }

    fun togglePlayPause(appLanguage: Wenku8API.AppLanguage) {
        val state = _uiState.value
        if (!state.isSessionOpen) return
        if (state.isSpeaking) {
            generationCounter++
            engineAdapter.stop()
            _uiState.update { it.copy(isSpeaking = false) }
        } else if (utterances.isNotEmpty()) {
            _uiState.update { it.copy(isSpeaking = true, engineErrorMessage = null) }
            ensureEngineAndSpeakCurrent(appLanguage)
        }
    }

    fun skipPrevious(appLanguage: Wenku8API.AppLanguage) {
        if (utterances.isEmpty()) return
        val prevIdx = (_uiState.value.activeUtteranceIndex - 1).coerceAtLeast(0)
        val item = utterances[prevIdx]
        _uiState.update {
            it.copy(
                isSpeaking = true,
                activeUtteranceIndex = prevIdx,
                activeBlockIndex = item.blockIndex,
                currentSnippet = item.text.take(48),
            )
        }
        ensureEngineAndSpeakCurrent(appLanguage)
    }

    fun skipNext(appLanguage: Wenku8API.AppLanguage) {
        if (utterances.isEmpty()) return
        val nextIdx = (_uiState.value.activeUtteranceIndex + 1).coerceAtMost(utterances.lastIndex)
        val item = utterances[nextIdx]
        _uiState.update {
            it.copy(
                isSpeaking = true,
                activeUtteranceIndex = nextIdx,
                activeBlockIndex = item.blockIndex,
                currentSnippet = item.text.take(48),
            )
        }
        ensureEngineAndSpeakCurrent(appLanguage)
    }

    fun cycleSpeechRate(appLanguage: Wenku8API.AppLanguage) {
        val rates = ReaderTtsUiState.SUPPORTED_SPEECH_RATES
        val curIdx = rates.indexOfFirst { kotlin.math.abs(it - _uiState.value.speechRate) < 0.05f }
        val nextRate = rates[(curIdx + 1) % rates.size]
        _uiState.update { it.copy(speechRate = nextRate) }
        engineAdapter.setSpeechRate(nextRate)
        if (_uiState.value.isSpeaking) {
            ensureEngineAndSpeakCurrent(appLanguage)
        }
    }

    fun stopAndDismiss() {
        generationCounter++
        pendingSpeakAfterInit = false
        engineAdapter.stop()
        _uiState.update {
            it.copy(
                isSessionOpen = false,
                isSpeaking = false,
                engineErrorMessage = null,
            )
        }
    }

    fun close() {
        generationCounter++
        pendingSpeakAfterInit = false
        engineInitialized = false
        engineInitializing = false
        engineAdapter.shutdown()
    }
}

@Composable
fun ReaderTtsFloatingBar(
    state: ReaderTtsUiState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCycleSpeechRate: () -> Unit,
    onStopAndDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEinkMode = LocalEinkMode.current
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
            alpha = if (isEinkMode) 1f else 0.96f,
        ),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 5.dp,
        modifier = modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.large)
            .testTag(ReaderTestTags.TTS_CONTROLLER_BAR),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.GraphicEq,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = state.engineErrorMessage
                        ?: state.currentSnippet.ifBlank { "正在准备语音朗读…" },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.engineErrorMessage != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(6.dp))
                if (state.totalUtterances > 0) {
                    Text(
                        text = "${state.activeUtteranceIndex + 1}/${state.totalUtterances}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            val controlsScrollState = rememberScrollState()
            val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(
                alpha = if (isEinkMode) 1f else 0.96f,
            )
            val fontScale = LocalDensity.current.fontScale
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val useCompactTtsControls = maxWidth < 312.dp || fontScale > 1.1f
                val iconButtonSizeModifier = if (useCompactTtsControls) {
                    Modifier.size(40.dp)
                } else {
                    Modifier
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScrollFadeEdge(
                            scrollState = controlsScrollState,
                            fadeColor = surfaceColor,
                            isEink = isEinkMode,
                        )
                        .horizontalScroll(controlsScrollState),
                ) {
                    AssistChip(
                        onClick = onCycleSpeechRate,
                        label = {
                            Text(
                                text = "${state.speechRate}x",
                                maxLines = 1,
                                softWrap = false,
                            )
                        },
                        modifier = Modifier.testTag(ReaderTestTags.TTS_RATE_CHIP),
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val prevLabel = stringResource(R.string.reader_tts_prev_sentence)
                        TooltipIconButton(
                            tooltipText = prevLabel,
                            enabled = state.totalUtterances > 0 && state.activeUtteranceIndex > 0,
                            onClick = onSkipPrevious,
                            anchorPosition = TooltipAnchorPosition.Above,
                            modifier = iconButtonSizeModifier.testTag(ReaderTestTags.TTS_PREV_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipPrevious,
                                contentDescription = prevLabel,
                            )
                        }
                        val playPauseLabel = if (state.isSpeaking) {
                            stringResource(R.string.reader_tts_pause)
                        } else {
                            stringResource(R.string.reader_tts_resume)
                        }
                        TooltipIconButton(
                            tooltipText = playPauseLabel,
                            enabled = state.totalUtterances > 0,
                            onClick = onTogglePlayPause,
                            anchorPosition = TooltipAnchorPosition.Above,
                            modifier = iconButtonSizeModifier.testTag(ReaderTestTags.TTS_PLAY_PAUSE_BUTTON),
                        ) {
                            Icon(
                                imageVector = if (state.isSpeaking) {
                                    Icons.Outlined.Pause
                                } else {
                                    Icons.Outlined.PlayArrow
                                },
                                contentDescription = playPauseLabel,
                            )
                        }
                        val nextLabel = stringResource(R.string.reader_tts_next_sentence)
                        TooltipIconButton(
                            tooltipText = nextLabel,
                            enabled = state.totalUtterances > 0 &&
                                state.activeUtteranceIndex < state.totalUtterances - 1,
                            onClick = onSkipNext,
                            anchorPosition = TooltipAnchorPosition.Above,
                            modifier = iconButtonSizeModifier.testTag(ReaderTestTags.TTS_NEXT_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.SkipNext,
                                contentDescription = nextLabel,
                            )
                        }
                    }

                    val stopLabel = stringResource(R.string.reader_tts_stop)
                    TooltipIconButton(
                        tooltipText = stopLabel,
                        onClick = onStopAndDismiss,
                        anchorPosition = TooltipAnchorPosition.Above,
                        modifier = iconButtonSizeModifier.testTag(ReaderTestTags.TTS_STOP_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = stopLabel,
                        )
                    }
                }
            }
        }
    }
}
