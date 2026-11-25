package org.mewx.wenku8.ui.profile

import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.BatteryAlert
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import kotlinx.coroutines.launch
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.tts.KnownTtsEngineCatalog
import org.mewx.wenku8.tts.TtsDiagnosticReason
import org.mewx.wenku8.tts.TtsEngineCategory
import org.mewx.wenku8.tts.TtsEngineDescriptor
import org.mewx.wenku8.tts.TtsEngineDiscovery
import org.mewx.wenku8.tts.TtsGuidanceAction
import org.mewx.wenku8.tts.TtsGuidanceItem
import org.mewx.wenku8.tts.TtsInitResult
import org.mewx.wenku8.tts.TtsSettingsJumpTier
import org.mewx.wenku8.tts.TtsSystemIntents
import org.mewx.wenku8.tts.TtsVoiceDescriptor
import org.mewx.wenku8.tts.toTtsPlaybackConfig
import org.mewx.wenku8.ui.reader.AndroidTextToSpeechEngineAdapter
import org.mewx.wenku8.ui.reader.ReaderTtsUiState
import org.mewx.wenku8.ui.reader.SpeechEngineAdapter
import org.mewx.wenku8.ui.theme.einkHairlineBorder
import java.util.Locale
import kotlin.math.roundToInt

/** Grouped TTS preference write callbacks threaded cleanly from MainActivity into settings UI. */
data class TtsPreferencesActions(
    val onPreferredEngineChanged: (String) -> Unit = {},
    val onFallbackEngineChanged: (String) -> Unit = {},
    val onAutoFallbackChanged: (Boolean) -> Unit = {},
    val onPreferredVoiceChanged: (String) -> Unit = {},
    val onSpeechRateChanged: (Float) -> Unit = {},
    val onPitchChanged: (Float) -> Unit = {},
    val onDefaultSleepTimerChanged: (Int) -> Unit = {},
    val onFollowReadingPageChanged: (Boolean) -> Unit = {},
    val onPauseOnAudioFocusLossChanged: (Boolean) -> Unit = {},
    val onResetDefaults: () -> Unit = {},
)

/** Semantic UI test tags for Robolectric & instrumented TTS Settings screen tests. */
object TtsSettingsTestTags {
    const val PREVIEW_CARD = "tts_settings_preview_card"
    const val PREVIEW_PLAY_STOP_BUTTON = "tts_settings_preview_play_stop_button"
    const val PREVIEW_REFRESH_BUTTON = "tts_settings_preview_refresh_button"
    const val PREVIEW_EDIT_TEXT_TOGGLE = "tts_settings_preview_edit_text_toggle"
    const val PREVIEW_CUSTOM_TEXT_FIELD = "tts_settings_preview_custom_text_field"
    const val PREVIEW_RESET_TEXT_BUTTON = "tts_settings_preview_reset_text_button"
    const val FALLBACK_BANNER = "tts_settings_fallback_banner"
    const val ONBOARDING_CARD = "tts_settings_onboarding_card"
    const val ONBOARDING_ITEM_PREFIX = "tts_settings_onboarding_item_"
    const val ONBOARDING_ACTION_PREFIX = "tts_settings_onboarding_action_"
    const val ENGINE_AUTO_ROW = "tts_settings_engine_auto_row"
    const val ENGINE_ROW_PREFIX = "tts_settings_engine_row_"
    const val VOICE_CHINESE_ONLY_CHIP = "tts_settings_voice_chinese_only_chip"
    const val VOICE_OFFLINE_ONLY_CHIP = "tts_settings_voice_offline_only_chip"
    const val VOICE_AUTO_ROW = "tts_settings_voice_auto_row"
    const val VOICE_ROW_PREFIX = "tts_settings_voice_row_"
    const val VOICE_PREVIEW_PREFIX = "tts_settings_voice_preview_"
    const val AUTO_FALLBACK_SWITCH = "tts_settings_auto_fallback_switch"
    const val SPEECH_RATE_SLIDER = "tts_settings_speech_rate_slider"
    const val PITCH_SLIDER = "tts_settings_pitch_slider"
    const val SLEEP_TIMER_CHIP_PREFIX = "tts_settings_sleep_timer_"
    const val FOLLOW_PAGE_SWITCH = "tts_settings_follow_page_switch"
    const val AUDIO_FOCUS_SWITCH = "tts_settings_audio_focus_switch"
    const val OPEN_SYSTEM_SETTINGS_ROW = "tts_settings_open_system_settings_row"
    const val DOWNLOAD_VOICE_DATA_ROW = "tts_settings_download_voice_data_row"
    const val OEM_KEEPALIVE_CARD = "tts_settings_oem_keepalive_card"
    const val RESET_DEFAULTS_ROW = "tts_settings_reset_defaults_row"
}

/**
 * Full Material 3 Expressive TTS settings & audition screen (`docs/designs/v2/tts/README.md`).
 *
 * Includes:
 * 1. Live engine health + light-novel voice preview card (SC/TC default sample + custom text)
 * 2. Channel-aware (`playstore` vs `default`) free/official onboarding card when engines are missing
 * 3. Installed engine discovery (standard Android `TTS_SERVICE` + open-source plugins) & voice picker
 *    with per-voice inline audition
 * 4. Multi-level fallback configuration (toggle + backup engine)
 * 5. Playback preferences (rate, pitch, default sleep timer, page follow, audio focus)
 * 6. System tools (download voice pack, 4-tier safe jump to system TTS settings, OEM keep-alive tips)
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TtsSettingsBody(
    preferences: Wenku8UserPreferences,
    horizontalPadding: Dp,
    isPlayStoreChannel: Boolean,
    ttsPreferencesActions: TtsPreferencesActions,
    modifier: Modifier = Modifier,
    speechEngineAdapterFactory: ((Context) -> SpeechEngineAdapter)? = null,
    installedEnginesProvider: ((Context) -> List<TtsEngineDescriptor>)? = null,
    guidanceActionHandler: ((Context, TtsGuidanceAction) -> Boolean)? = null,
    manufacturerOverride: String? = null,
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val coroutineScope = rememberCoroutineScope()

    val adapter = remember(speechEngineAdapterFactory) {
        speechEngineAdapterFactory?.invoke(appContext) ?: AndroidTextToSpeechEngineAdapter(appContext)
    }
    DisposableEffect(adapter) {
        onDispose { adapter.shutdown() }
    }

    var refreshEpoch by remember { mutableIntStateOf(0) }
    var installedEngines by remember {
        mutableStateOf(
            installedEnginesProvider?.invoke(appContext)
                ?: TtsEngineDiscovery.discoverInstalledEngines(appContext),
        )
    }
    var isProbing by remember { mutableStateOf(true) }
    var initResult by remember { mutableStateOf<TtsInitResult?>(null) }
    var isPreviewSpeaking by remember { mutableStateOf(false) }
    var previewingVoiceName by remember { mutableStateOf<String?>(null) }
    var actionFeedbackMessage by remember { mutableStateOf<String?>(null) }

    val defaultSample = remember(preferences.appLanguage) {
        KnownTtsEngineCatalog.defaultLightNovelSampleText(preferences.appLanguage)
    }
    var customSampleText by rememberSaveable(preferences.appLanguage) {
        mutableStateOf(defaultSample)
    }
    var isEditingPreviewText by rememberSaveable { mutableStateOf(false) }
    var filterChineseOnly by rememberSaveable { mutableStateOf(true) }
    var filterOfflineOnly by rememberSaveable { mutableStateOf(false) }
    var showAllVoices by rememberSaveable { mutableStateOf(false) }
    var expandMoreEnginesCard by rememberSaveable { mutableStateOf(false) }
    var previewUtteranceSeq by remember { mutableIntStateOf(0) }

    // Automatically re-scan engines when returning from Play Store or System Settings!
    var hasPassedFirstResume by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (hasPassedFirstResume) {
            refreshEpoch++
        } else {
            hasPassedFirstResume = true
        }
    }

    val effectiveConfig = remember(preferences) { preferences.toTtsPlaybackConfig() }

    LaunchedEffect(
        refreshEpoch,
        preferences.appLanguage,
        preferences.ttsPreferredEnginePackage,
        preferences.ttsFallbackEnginePackage,
        preferences.ttsAutoFallbackEnabled,
        preferences.ttsPreferredVoiceName,
    ) {
        isProbing = true
        isPreviewSpeaking = false
        previewingVoiceName = null
        installedEngines = installedEnginesProvider?.invoke(appContext)
            ?: TtsEngineDiscovery.discoverInstalledEngines(appContext)
        adapter.initializeConfigured(
            appLanguage = preferences.appLanguage,
            config = effectiveConfig,
            excludedEnginePackages = emptySet(),
            onInitialized = { result ->
                coroutineScope.launch {
                    isProbing = false
                    initResult = result
                    if (result.success) {
                        adapter.setSpeechRate(preferences.ttsSpeechRate)
                        adapter.setPitch(preferences.ttsPitch)
                    }
                }
            },
            onUtteranceStarted = {
                coroutineScope.launch { isPreviewSpeaking = true }
            },
            onUtteranceDone = {
                coroutineScope.launch {
                    isPreviewSpeaking = false
                    previewingVoiceName = null
                }
            },
            onUtteranceError = {
                coroutineScope.launch {
                    isPreviewSpeaking = false
                    previewingVoiceName = null
                    actionFeedbackMessage = "试听播放失败，请尝试切换其他音色或引擎"
                }
            },
        )
    }

    // Apply live rate / pitch changes immediately to the bound preview adapter.
    LaunchedEffect(preferences.ttsSpeechRate, preferences.ttsPitch, initResult?.success) {
        if (initResult?.success == true) {
            adapter.setSpeechRate(preferences.ttsSpeechRate)
            adapter.setPitch(preferences.ttsPitch)
        }
    }

    fun startPreview(voiceOverride: String? = null) {
        if (initResult?.success != true) {
            actionFeedbackMessage = initResult?.diagnosticMessage ?: "当前暂无可用的中文语音引擎"
            return
        }
        val textToSpeak = customSampleText.trim().ifBlank { defaultSample }
        actionFeedbackMessage = null
        adapter.setSpeechRate(preferences.ttsSpeechRate)
        adapter.setPitch(preferences.ttsPitch)
        previewUtteranceSeq += 1
        val utteranceId = "wk8_tts_preview_$previewUtteranceSeq"
        val started = adapter.speakWithVoiceOverride(
            text = textToSpeak,
            utteranceId = utteranceId,
            voiceNameOverride = voiceOverride,
        )
        if (started) {
            isPreviewSpeaking = true
            previewingVoiceName = voiceOverride
        } else {
            isPreviewSpeaking = false
            previewingVoiceName = null
            actionFeedbackMessage = "启动试听失败，请检查所选语音引擎状态"
        }
    }

    fun stopPreview() {
        adapter.stop()
        isPreviewSpeaking = false
        previewingVoiceName = null
    }

    val manufacturer = manufacturerOverride ?: Build.MANUFACTURER.orEmpty()
    val oemGuidance = remember(manufacturer) { TtsSystemIntents.buildOemGuidance(manufacturer) }
    val currentDiagnostic = initResult?.diagnosticReason ?: TtsDiagnosticReason.NONE
    val isEngineReady = initResult?.success == true
    val activeEnginePkg = initResult?.activeEnginePackage.orEmpty()
    val guidanceItems = remember(
        isPlayStoreChannel,
        installedEngines,
        activeEnginePkg,
        currentDiagnostic,
        manufacturer,
    ) {
        TtsSystemIntents.buildChannelGuidanceOptions(
            isPlayStoreChannel = isPlayStoreChannel,
            installedEngines = installedEngines,
            activeEnginePackage = activeEnginePkg,
            diagnosticReason = currentDiagnostic,
            manufacturer = manufacturer,
        )
    }

    val handleGuidanceAction: (TtsGuidanceAction) -> Unit = { action ->
        val ok = guidanceActionHandler?.invoke(context, action)
            ?: TtsSystemIntents.executeGuidanceAction(context, action)
        if (!ok) {
            actionFeedbackMessage = "无法直接打开目标界面，请手动前往：${oemGuidance.settingsPathHint}"
        }
    }

    val allVoices = initResult?.availableVoices.orEmpty()
    val filteredVoices = remember(allVoices, filterChineseOnly, filterOfflineOnly) {
        allVoices.filter { voice ->
            (!filterChineseOnly || voice.isChinese) &&
                (!filterOfflineOnly || !voice.isNetworkRequired)
        }
    }
    val visibleVoices = if (showAllVoices) filteredVoices else filteredVoices.take(8)
    val backupCandidates = remember(installedEngines, preferences.ttsPreferredEnginePackage) {
        installedEngines.filter {
            !it.isKnownUnsupportedChinese &&
                it.packageName != preferences.ttsPreferredEnginePackage.trim()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag(ProfileStudioTestTags.SUBSCREEN_LIST),
        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        // 1. Top Live Engine Status & Light-Novel Voice Preview Card
        item(key = "tts_preview_card") {
            TtsStatusAndPreviewCard(
                isProbing = isProbing,
                initResult = initResult,
                isPreviewSpeaking = isPreviewSpeaking,
                sampleText = customSampleText,
                isEditingSampleText = isEditingPreviewText,
                speechRate = preferences.ttsSpeechRate,
                pitch = preferences.ttsPitch,
                feedbackMessage = actionFeedbackMessage,
                onToggleEditSample = { isEditingPreviewText = !isEditingPreviewText },
                onSampleTextChanged = { customSampleText = it },
                onResetSampleText = { customSampleText = defaultSample },
                onTogglePlayStop = {
                    if (isPreviewSpeaking) stopPreview() else startPreview(voiceOverride = null)
                },
                onRefresh = {
                    stopPreview()
                    actionFeedbackMessage = null
                    refreshEpoch++
                },
            )
        }

        // 2. Auto-surfaced Channel-Aware Free/Official Onboarding Card when TTS is unavailable
        if (!isProbing && !isEngineReady) {
            item(key = "tts_onboarding_urgent") {
                TtsChannelOnboardingCard(
                    isPlayStoreChannel = isPlayStoreChannel,
                    isUrgent = true,
                    items = guidanceItems,
                    onActionClicked = handleGuidanceAction,
                )
            }
        }

        // 3. Engine Selection & Fallback Section
        item(key = "tts_section_engines") {
            ProfileSegmentedListSection(title = "语音引擎与失效回退") {
                val engineRowCount = installedEngines.size + 1
                // Auto-select row
                val isAutoSelected = preferences.ttsPreferredEnginePackage.isBlank()
                ProfileSegmentedSettingRow(
                    index = 0,
                    count = engineRowCount,
                    icon = Icons.Outlined.AutoAwesome,
                    title = "自动优选可用中文引擎",
                    subtitle = if (isAutoSelected && isEngineReady) {
                        "当前自动匹配：${initResult?.activeEngineLabel.orEmpty()}"
                    } else {
                        "按官方插件 / Google / 开源离线 / 系统自带顺序智能匹配"
                    },
                    highlighted = isAutoSelected,
                    onClick = {
                        stopPreview()
                        ttsPreferencesActions.onPreferredEngineChanged("")
                    },
                    trailingContent = {
                        RadioButton(
                            selected = isAutoSelected,
                            onClick = {
                                stopPreview()
                                ttsPreferencesActions.onPreferredEngineChanged("")
                            },
                        )
                    },
                    modifier = Modifier.testTag(TtsSettingsTestTags.ENGINE_AUTO_ROW),
                )

                installedEngines.forEachIndexed { idx, engine ->
                    val isSelected = preferences.ttsPreferredEnginePackage == engine.packageName
                    val isCurrentlyActive = activeEnginePkg == engine.packageName && isEngineReady
                    val badgeText = categoryBadgeLabel(engine.category)
                    val subtitleParts = buildList {
                        add(badgeText)
                        if (engine.isSystemDefault) add("系统默认")
                        if (isCurrentlyActive) add("正在使用")
                        engine.compatibilityHint?.let { add(it) }
                    }
                    ProfileSegmentedSettingRow(
                        index = idx + 1,
                        count = engineRowCount,
                        icon = if (engine.isKnownUnsupportedChinese) {
                            Icons.Outlined.ErrorOutline
                        } else {
                            Icons.Outlined.GraphicEq
                        },
                        title = engine.displayName,
                        subtitle = subtitleParts.joinToString(" · "),
                        highlighted = isSelected,
                        onClick = {
                            stopPreview()
                            ttsPreferencesActions.onPreferredEngineChanged(engine.packageName)
                        },
                        trailingContent = {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    stopPreview()
                                    ttsPreferencesActions.onPreferredEngineChanged(engine.packageName)
                                },
                            )
                        },
                        modifier = Modifier.testTag("${TtsSettingsTestTags.ENGINE_ROW_PREFIX}${engine.packageName}"),
                    )
                }
            }
        }

        // 4. Fallback Controls (Auto-Fallback Toggle + Backup Engine Selector)
        item(key = "tts_section_fallback") {
            ProfileSegmentedListSection(title = "音源失效回退策略") {
                val hasBackupChoices = preferences.ttsAutoFallbackEnabled && backupCandidates.isNotEmpty()
                val totalRows = if (hasBackupChoices) 2 else 1
                ProfileSegmentedSwitchRow(
                    index = 0,
                    count = totalRows,
                    icon = Icons.Outlined.SwapHoriz,
                    title = "音源失效时自动回退",
                    subtitle = "首选引擎初始化失败、缺中文包或连续报错时，自动热切换至备用引擎并续播当前句",
                    checked = preferences.ttsAutoFallbackEnabled,
                    onCheckedChange = ttsPreferencesActions.onAutoFallbackChanged,
                    switchTestTag = TtsSettingsTestTags.AUTO_FALLBACK_SWITCH,
                )
                if (hasBackupChoices) {
                    ProfileSegmentedSettingRow(
                        index = 1,
                        count = totalRows,
                        icon = Icons.Outlined.Hearing,
                        title = "优先备用引擎",
                        subtitle = if (preferences.ttsFallbackEnginePackage.isBlank()) {
                            "自动按优先级尝试下一个可用中文引擎"
                        } else {
                            KnownTtsEngineCatalog.describe(preferences.ttsFallbackEnginePackage).displayName
                        },
                        bottomContent = {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilterChip(
                                    selected = preferences.ttsFallbackEnginePackage.isBlank(),
                                    onClick = { ttsPreferencesActions.onFallbackEngineChanged("") },
                                    label = { Text("自动轮询", maxLines = 1) },
                                )
                                backupCandidates.forEach { candidate ->
                                    FilterChip(
                                        selected = preferences.ttsFallbackEnginePackage == candidate.packageName,
                                        onClick = {
                                            ttsPreferencesActions.onFallbackEngineChanged(candidate.packageName)
                                        },
                                        label = {
                                            Text(
                                                text = candidate.displayName,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }

        // 5. Voice Picker with Per-Voice Inline Audition
        if (isEngineReady && allVoices.isNotEmpty()) {
            item(key = "tts_section_voices") {
                ProfileSegmentedListSection(title = "朗读音色（支持行内试听）") {
                    val rowCount = visibleVoices.size + 2 + if (filteredVoices.size > 8) 1 else 0
                    // Filter bar row
                    ProfileSegmentedSettingRow(
                        index = 0,
                        count = rowCount,
                        icon = Icons.Outlined.Tune,
                        title = "音色筛选",
                        subtitle = "共发现 ${allVoices.size} 个音色，当前匹配 ${filteredVoices.size} 个",
                        bottomContent = {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                FilterChip(
                                    selected = filterChineseOnly,
                                    onClick = { filterChineseOnly = !filterChineseOnly },
                                    label = { Text("仅中文音色") },
                                    modifier = Modifier.testTag(TtsSettingsTestTags.VOICE_CHINESE_ONLY_CHIP),
                                )
                                FilterChip(
                                    selected = filterOfflineOnly,
                                    onClick = { filterOfflineOnly = !filterOfflineOnly },
                                    label = { Text("仅离线可用") },
                                    modifier = Modifier.testTag(TtsSettingsTestTags.VOICE_OFFLINE_ONLY_CHIP),
                                )
                            }
                        },
                    )

                    // Auto voice row
                    val isAutoVoice = preferences.ttsPreferredVoiceName.isBlank()
                    ProfileSegmentedSettingRow(
                        index = 1,
                        count = rowCount,
                        icon = Icons.Outlined.CheckCircle,
                        title = "自动选择最佳中文音色",
                        subtitle = "跟随简繁语言自动匹配，断网时优先使用离线音色",
                        highlighted = isAutoVoice,
                        onClick = {
                            stopPreview()
                            ttsPreferencesActions.onPreferredVoiceChanged("")
                        },
                        trailingContent = {
                            RadioButton(
                                selected = isAutoVoice,
                                onClick = {
                                    stopPreview()
                                    ttsPreferencesActions.onPreferredVoiceChanged("")
                                },
                            )
                        },
                        modifier = Modifier.testTag(TtsSettingsTestTags.VOICE_AUTO_ROW),
                    )

                    visibleVoices.forEachIndexed { idx, voice ->
                        val isSelected = preferences.ttsPreferredVoiceName == voice.name
                        val isThisPreviewing = isPreviewSpeaking && previewingVoiceName == voice.name
                        ProfileSegmentedSettingRow(
                            index = idx + 2,
                            count = rowCount,
                            icon = Icons.AutoMirrored.Outlined.VolumeUp,
                            title = voice.displayLabel,
                            subtitle = voice.name,
                            highlighted = isSelected,
                            onClick = {
                                ttsPreferencesActions.onPreferredVoiceChanged(voice.name)
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = {
                                            if (isThisPreviewing) {
                                                stopPreview()
                                            } else {
                                                startPreview(voiceOverride = voice.name)
                                            }
                                        },
                                        modifier = Modifier.testTag(
                                            "${TtsSettingsTestTags.VOICE_PREVIEW_PREFIX}${voice.name}",
                                        ),
                                    ) {
                                        Icon(
                                            imageVector = if (isThisPreviewing) {
                                                Icons.Outlined.Stop
                                            } else {
                                                Icons.Outlined.PlayArrow
                                            },
                                            contentDescription = if (isThisPreviewing) "停止试听" else "试听该音色",
                                        )
                                    }
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = {
                                            ttsPreferencesActions.onPreferredVoiceChanged(voice.name)
                                        },
                                    )
                                }
                            },
                            modifier = Modifier.testTag("${TtsSettingsTestTags.VOICE_ROW_PREFIX}${voice.name}"),
                        )
                    }

                    if (filteredVoices.size > 8) {
                        ProfileSegmentedSettingRow(
                            index = rowCount - 1,
                            count = rowCount,
                            icon = Icons.Outlined.Tune,
                            title = if (showAllVoices) {
                                "收起部分音色"
                            } else {
                                "展开全部 ${filteredVoices.size} 个音色"
                            },
                            onClick = { showAllVoices = !showAllVoices },
                        )
                    }
                }
            }
        }

        // 6. Playback Parameters & Sleep Timer Section
        item(key = "tts_section_playback") {
            ProfileSegmentedListSection(title = "朗读参数与定时偏好") {
                val formattedRate = formatOneOrTwoDecimals(preferences.ttsSpeechRate)
                val formattedPitch = formatOneOrTwoDecimals(preferences.ttsPitch)

                ProfileSegmentedSettingRow(
                    index = 0,
                    count = 5,
                    icon = Icons.Outlined.Speed,
                    title = "默认朗读语速：${formattedRate}x",
                    subtitle = "部分国产引擎会自动钳制到稳定语速区间（0.6x–2.0x）",
                    bottomContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            @Suppress("DEPRECATION")
                            Slider(
                                value = preferences.ttsSpeechRate,
                                onValueChange = { raw ->
                                    val snapped = (raw * 20f).roundToInt() / 20f
                                    ttsPreferencesActions.onSpeechRateChanged(snapped)
                                },
                                valueRange = Wenku8UserPreferences.MIN_TTS_SPEECH_RATE..Wenku8UserPreferences.MAX_TTS_SPEECH_RATE,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(TtsSettingsTestTags.SPEECH_RATE_SLIDER),
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                ReaderTtsUiState.SUPPORTED_SPEECH_RATES.forEach { preset ->
                                    val selected = kotlin.math.abs(preferences.ttsSpeechRate - preset) < 0.03f
                                    FilterChip(
                                        selected = selected,
                                        onClick = { ttsPreferencesActions.onSpeechRateChanged(preset) },
                                        label = { Text("${formatOneOrTwoDecimals(preset)}x") },
                                    )
                                }
                            }
                        }
                    },
                )

                ProfileSegmentedSettingRow(
                    index = 1,
                    count = 5,
                    icon = Icons.Outlined.Tune,
                    title = "朗读音调：${formattedPitch}x",
                    subtitle = "调整人声高低，默认 1.0x 为原声音调",
                    trailingContent = {
                        if (kotlin.math.abs(preferences.ttsPitch - 1.0f) >= 0.03f) {
                            TextButton(
                                onClick = {
                                    ttsPreferencesActions.onPitchChanged(Wenku8UserPreferences.DEFAULT_TTS_PITCH)
                                },
                            ) {
                                Text("重置")
                            }
                        }
                    },
                    bottomContent = {
                        @Suppress("DEPRECATION")
                        Slider(
                            value = preferences.ttsPitch,
                            onValueChange = { raw ->
                                val snapped = (raw * 20f).roundToInt() / 20f
                                ttsPreferencesActions.onPitchChanged(snapped)
                            },
                            valueRange = Wenku8UserPreferences.MIN_TTS_PITCH..Wenku8UserPreferences.MAX_TTS_PITCH,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TtsSettingsTestTags.PITCH_SLIDER),
                        )
                    },
                )

                val sleepPresets = listOf(
                    Wenku8UserPreferences.TTS_SLEEP_TIMER_OFF to "关闭",
                    15 to "15 分钟",
                    30 to "30 分钟",
                    60 to "60 分钟",
                    Wenku8UserPreferences.TTS_SLEEP_TIMER_END_OF_CHAPTER to "读完本章",
                )
                ProfileSegmentedSettingRow(
                    index = 2,
                    count = 5,
                    icon = Icons.Outlined.Timer,
                    title = "默认定时关闭",
                    subtitle = "启动听书时自动生效，可在阅读器中随时调整",
                    bottomContent = {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            sleepPresets.forEach { (minutes, label) ->
                                FilterChip(
                                    selected = preferences.ttsDefaultSleepTimerMinutes == minutes,
                                    onClick = { ttsPreferencesActions.onDefaultSleepTimerChanged(minutes) },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("${TtsSettingsTestTags.SLEEP_TIMER_CHIP_PREFIX}$minutes"),
                                )
                            }
                        }
                    },
                )

                ProfileSegmentedSwitchRow(
                    index = 3,
                    count = 5,
                    icon = Icons.AutoMirrored.Outlined.MenuBook,
                    title = "朗读时自动跟随翻页",
                    subtitle = "跨页或滚动时自动对齐至当前朗读段落",
                    checked = preferences.ttsFollowReadingPage,
                    onCheckedChange = ttsPreferencesActions.onFollowReadingPageChanged,
                    switchTestTag = TtsSettingsTestTags.FOLLOW_PAGE_SWITCH,
                )

                ProfileSegmentedSwitchRow(
                    index = 4,
                    count = 5,
                    icon = Icons.AutoMirrored.Outlined.VolumeUp,
                    title = "来电或媒体播放时自动暂停",
                    subtitle = "管理 Android 音频焦点，来电结束后可自动恢复朗读",
                    checked = preferences.ttsPauseOnAudioFocusLoss,
                    onCheckedChange = ttsPreferencesActions.onPauseOnAudioFocusLossChanged,
                    switchTestTag = TtsSettingsTestTags.AUDIO_FOCUS_SWITCH,
                )
            }
        }

        // 7. System Tools, OEM Guidance, and Optional More Free Engines Section
        item(key = "tts_section_tools") {
            ProfileSegmentedListSection(title = "系统工具与防断播") {
                val toolCount = if (isEngineReady) 4 else 3
                var rowIdx = 0
                ProfileSegmentedSettingRow(
                    index = rowIdx++,
                    count = toolCount,
                    icon = Icons.Outlined.Download,
                    title = "下载 / 管理当前引擎语音包",
                    subtitle = "若中文朗读无声或提示缺失语音数据，点击拉起引擎语音包下载页",
                    onClick = {
                        val targetPkg = activeEnginePkg.ifBlank { preferences.ttsPreferredEnginePackage }
                        handleGuidanceAction(TtsGuidanceAction.OpenInstallVoiceData(targetPkg))
                    },
                    modifier = Modifier.testTag(TtsSettingsTestTags.DOWNLOAD_VOICE_DATA_ROW),
                )

                ProfileSegmentedSettingRow(
                    index = rowIdx++,
                    count = toolCount,
                    icon = Icons.Outlined.Settings,
                    title = "打开系统文字转语音 (TTS) 设置",
                    subtitle = "${oemGuidance.brandLabel}：${oemGuidance.settingsPathHint}",
                    onClick = {
                        val tier = TtsSystemIntents.openSystemTtsSettings(context)
                        if (tier != TtsSettingsJumpTier.DIRECT_TTS_SETTINGS) {
                            actionFeedbackMessage = "已为您打开系统设置，请前往：${oemGuidance.settingsPathHint}"
                        }
                    },
                    modifier = Modifier.testTag(TtsSettingsTestTags.OPEN_SYSTEM_SETTINGS_ROW),
                )

                if (isEngineReady) {
                    ProfileSegmentedSettingRow(
                        index = rowIdx++,
                        count = toolCount,
                        icon = Icons.Outlined.AutoAwesome,
                        title = if (expandMoreEnginesCard) "收起免费语音引擎推荐" else "获取更多官方 / 开源免费语音引擎",
                        subtitle = if (isPlayStoreChannel) {
                            "查看 Play 商店官方免费语音方案与语音包下载入口"
                        } else {
                            "支持 SherpaTTS、VoxSherpa、TTS Server、MultiTTS 等开源免费方案"
                        },
                        onClick = { expandMoreEnginesCard = !expandMoreEnginesCard },
                    )
                }

                ProfileSegmentedSettingRow(
                    index = rowIdx,
                    count = toolCount,
                    icon = Icons.Outlined.RestartAlt,
                    title = "恢复默认语音设置",
                    subtitle = "重置首选引擎为自动优选，语速与音调恢复 1.0x",
                    onClick = {
                        stopPreview()
                        ttsPreferencesActions.onResetDefaults()
                        actionFeedbackMessage = "已恢复默认语音朗读设置"
                    },
                    modifier = Modifier.testTag(TtsSettingsTestTags.RESET_DEFAULTS_ROW),
                )
            }
        }

        if (isEngineReady && expandMoreEnginesCard) {
            item(key = "tts_onboarding_optional") {
                TtsChannelOnboardingCard(
                    isPlayStoreChannel = isPlayStoreChannel,
                    isUrgent = false,
                    items = guidanceItems,
                    onActionClicked = handleGuidanceAction,
                )
            }
        }

        // 8. OEM Background Keep-Alive Hint Card
        item(key = "tts_oem_keepalive_card") {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkHairlineBorder(MaterialTheme.shapes.large)
                    .testTag(TtsSettingsTestTags.OEM_KEEPALIVE_CARD),
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.BatteryAlert,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${oemGuidance.brandLabel} 防断播提示",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = oemGuidance.backgroundKeepAliveHint,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TtsStatusAndPreviewCard(
    isProbing: Boolean,
    initResult: TtsInitResult?,
    isPreviewSpeaking: Boolean,
    sampleText: String,
    isEditingSampleText: Boolean,
    speechRate: Float,
    pitch: Float,
    feedbackMessage: String?,
    onToggleEditSample: () -> Unit,
    onSampleTextChanged: (String) -> Unit,
    onResetSampleText: () -> Unit,
    onTogglePlayStop: () -> Unit,
    onRefresh: () -> Unit,
) {
    val ready = initResult?.success == true
    val statusContainer = when {
        isProbing -> MaterialTheme.colorScheme.surfaceContainerHigh
        ready && initResult.isFallbackActive -> MaterialTheme.colorScheme.secondaryContainer
        ready -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.errorContainer
    }
    val statusOnContainer = when {
        isProbing -> MaterialTheme.colorScheme.onSurface
        ready && initResult.isFallbackActive -> MaterialTheme.colorScheme.onSecondaryContainer
        ready -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onErrorContainer
    }

    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.extraLarge)
            .testTag(TtsSettingsTestTags.PREVIEW_CARD),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Engine health status pill
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = statusContainer,
                contentColor = statusOnContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkHairlineBorder(MaterialTheme.shapes.medium),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    if (isProbing) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp),
                        )
                    } else {
                        Icon(
                            imageVector = if (ready) Icons.Outlined.CheckCircle else Icons.Outlined.ErrorOutline,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                isProbing -> "正在检测本机语音朗读引擎…"
                                ready -> initResult.activeEngineLabel.ifBlank { "语音引擎已就绪" }
                                else -> "当前无可用中文语音引擎"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val detailLine = when {
                            isProbing -> "正在校验中文语音数据包与可用音色"
                            ready -> buildString {
                                append("语速 ${formatOneOrTwoDecimals(speechRate)}x · 音调 ${formatOneOrTwoDecimals(pitch)}x")
                                val voiceName = initResult.activeVoiceName
                                if (voiceName.isNotBlank()) {
                                    val voiceLabel = initResult.availableVoices
                                        .firstOrNull { it.name == voiceName }
                                        ?.displayLabel
                                        ?: voiceName
                                    append(" · $voiceLabel")
                                }
                            }
                            else -> initResult?.diagnosticMessage ?: "请参考下方免费方案完成配置"
                        }
                        Text(
                            text = detailLine,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            // Fallback warning strip if currently degraded to backup engine
            val fallbackMsg = initResult?.fallbackNoticeMessage
            if (ready && initResult.isFallbackActive && !fallbackMsg.isNullOrBlank()) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(TtsSettingsTestTags.FALLBACK_BANNER),
                ) {
                    Text(
                        text = fallbackMsg,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }
            }

            // Light-novel sample text preview / editor
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier
                    .fillMaxWidth()
                    .einkHairlineBorder(MaterialTheme.shapes.medium),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "轻小说试听范文",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(
                            onClick = onToggleEditSample,
                            modifier = Modifier.testTag(TtsSettingsTestTags.PREVIEW_EDIT_TEXT_TOGGLE),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isEditingSampleText) "完成编辑" else "自定义文本")
                        }
                    }

                    if (isEditingSampleText) {
                        OutlinedTextField(
                            value = sampleText,
                            onValueChange = onSampleTextChanged,
                            minLines = 2,
                            maxLines = 4,
                            label = { Text("输入任意文本测试多音字或中英混读") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(TtsSettingsTestTags.PREVIEW_CUSTOM_TEXT_FIELD),
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TextButton(
                                onClick = onResetSampleText,
                                modifier = Modifier.testTag(TtsSettingsTestTags.PREVIEW_RESET_TEXT_BUTTON),
                            ) {
                                Text("恢复默认范文")
                            }
                        }
                    } else {
                        Text(
                            text = sampleText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            }

            if (!feedbackMessage.isNullOrBlank()) {
                Text(
                    text = feedbackMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            // Action buttons (FlowRow guarantees clean wrap on 320dp @ 1.3x font scale)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (isPreviewSpeaking) {
                    Button(
                        onClick = onTogglePlayStop,
                        modifier = Modifier.testTag(TtsSettingsTestTags.PREVIEW_PLAY_STOP_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("停止试听")
                    }
                } else {
                    FilledTonalButton(
                        onClick = onTogglePlayStop,
                        enabled = !isProbing && ready,
                        modifier = Modifier.testTag(TtsSettingsTestTags.PREVIEW_PLAY_STOP_BUTTON),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("试听朗读")
                    }
                }

                OutlinedButton(
                    onClick = onRefresh,
                    modifier = Modifier.testTag(TtsSettingsTestTags.PREVIEW_REFRESH_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("重新检测")
                }
            }
        }
    }
}

@Composable
private fun TtsChannelOnboardingCard(
    isPlayStoreChannel: Boolean,
    isUrgent: Boolean,
    items: List<TtsGuidanceItem>,
    onActionClicked: (TtsGuidanceAction) -> Unit,
) {
    Card(
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = if (isUrgent) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        modifier = Modifier
            .fillMaxWidth()
            .einkHairlineBorder(MaterialTheme.shapes.extraLarge)
            .testTag(TtsSettingsTestTags.ONBOARDING_CARD),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (isPlayStoreChannel) {
                    "官方免费语音引擎安装引导（Google Play）"
                } else {
                    "官方与开源免费语音方案引导"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (isPlayStoreChannel) {
                    "推荐安装 Google 官方免费的 Speech Recognition & Synthesis；安装完成后返回本页将自动检测并启用。"
                } else {
                    "国行机型如缺失系统中文语音引擎，优先推荐使用开源免费纯离线 AI 语音引擎（无需 Google 服务），安装后返回本页即可直接选用。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            items.forEachIndexed { idx, item ->
                if (idx > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("${TtsSettingsTestTags.ONBOARDING_ITEM_PREFIX}${item.id}"),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ) {
                            Text(
                                text = item.badge,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            )
                        }
                    }
                    Text(
                        text = item.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    FilledTonalButton(
                        onClick = { onActionClicked(item.action) },
                        modifier = Modifier.testTag("${TtsSettingsTestTags.ONBOARDING_ACTION_PREFIX}${item.id}"),
                    ) {
                        Text(item.actionLabel)
                    }
                }
            }
        }
    }
}

internal fun categoryBadgeLabel(category: TtsEngineCategory): String = when (category) {
    TtsEngineCategory.OFFICIAL_PLUGIN -> "官方插件"
    TtsEngineCategory.OFFICIAL_FREE -> "官方免费"
    TtsEngineCategory.OPEN_SOURCE_OFFLINE -> "开源离线"
    TtsEngineCategory.OPEN_SOURCE_BRIDGE -> "开源扩展"
    TtsEngineCategory.SYSTEM_OEM -> "系统自带"
    TtsEngineCategory.THIRD_PARTY -> "第三方引擎"
    TtsEngineCategory.UNSUPPORTED_CHINESE -> "不支持中文"
}

private fun formatOneOrTwoDecimals(value: Float): String {
    val rounded = (value * 100f).roundToInt() / 100f
    return if (kotlin.math.abs(rounded * 10f - (rounded * 10f).roundToInt()) < 0.01f) {
        String.format(Locale.US, "%.1f", rounded)
    } else {
        String.format(Locale.US, "%.2f", rounded)
    }
}
