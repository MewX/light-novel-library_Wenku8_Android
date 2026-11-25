package org.mewx.wenku8.tts

import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences

/**
 * Forward-compatible synthesis backend selector (`docs/designs/v2/tts/README.md`).
 *
 * Phase 1 runs on [SYSTEM] (including standard Android `TextToSpeechService` open-source plugins);
 * [FREE_REMOTE_OR_SELF_HOSTED] and [PLUGIN_STREAM] are reserved for Phases 2 and 3 so DataStore
 * preferences remain schema-stable without breaking migrations.
 */
enum class TtsBackendType {
    SYSTEM,
    FREE_REMOTE_OR_SELF_HOSTED,
    PLUGIN_STREAM,
}

/** Semantic category badge for discovered Android TTS engines. */
enum class TtsEngineCategory {
    OFFICIAL_PLUGIN,
    OFFICIAL_FREE,
    OPEN_SOURCE_OFFLINE,
    OPEN_SOURCE_BRIDGE,
    SYSTEM_OEM,
    THIRD_PARTY,
    UNSUPPORTED_CHINESE,
}

/** Structured diagnostic root cause surfaced when TTS cannot speak cleanly. */
enum class TtsDiagnosticReason {
    NONE,
    NO_ENGINE_INSTALLED,
    MISSING_CHINESE_DATA,
    ENGINE_BIND_FAILED,
    PLAYBACK_INTERRUPTED,
}

/** Discovered Android `TextToSpeechService` engine enriched with catalog metadata. */
data class TtsEngineDescriptor(
    val packageName: String,
    val displayName: String,
    val systemLabel: String,
    val category: TtsEngineCategory,
    val isSystemDefault: Boolean = false,
    val supportsPluginV1: Boolean = false,
    val priorityRank: Int = 100,
    val safeRateMin: Float = Wenku8UserPreferences.MIN_TTS_SPEECH_RATE,
    val safeRateMax: Float = Wenku8UserPreferences.MAX_TTS_SPEECH_RATE,
    val compatibilityHint: String? = null,
) {
    val isKnownUnsupportedChinese: Boolean
        get() = category == TtsEngineCategory.UNSUPPORTED_CHINESE
}

/** Human-readable voice metadata derived from `android.speech.tts.Voice`. */
data class TtsVoiceDescriptor(
    val name: String,
    val displayLabel: String,
    val localeTag: String,
    val isNetworkRequired: Boolean,
    val isChinese: Boolean,
    val qualityScore: Int = 300,
)

/** Immutable snapshot of TTS playback settings applied by controllers and preview cards. */
data class TtsPlaybackConfig(
    val backendType: TtsBackendType = TtsBackendType.SYSTEM,
    val preferredEnginePackage: String = "",
    val fallbackEnginePackage: String = "",
    val autoFallbackEnabled: Boolean = true,
    val preferredVoiceName: String = "",
    val speechRate: Float = Wenku8UserPreferences.DEFAULT_TTS_SPEECH_RATE,
    val pitch: Float = Wenku8UserPreferences.DEFAULT_TTS_PITCH,
    val defaultSleepTimerMinutes: Int = 0,
    val followReadingPage: Boolean = true,
    val pauseOnAudioFocusLoss: Boolean = true,
)

fun Wenku8UserPreferences.toTtsPlaybackConfig(): TtsPlaybackConfig = TtsPlaybackConfig(
    backendType = ttsBackendType,
    preferredEnginePackage = ttsPreferredEnginePackage,
    fallbackEnginePackage = ttsFallbackEnginePackage,
    autoFallbackEnabled = ttsAutoFallbackEnabled,
    preferredVoiceName = ttsPreferredVoiceName,
    speechRate = ttsSpeechRate,
    pitch = ttsPitch,
    defaultSleepTimerMinutes = ttsDefaultSleepTimerMinutes,
    followReadingPage = ttsFollowReadingPage,
    pauseOnAudioFocusLoss = ttsPauseOnAudioFocusLoss,
)

/** Structured result returned after initializing (and potentially falling back) a TTS engine. */
data class TtsInitResult(
    val success: Boolean,
    val activeEnginePackage: String = "",
    val activeEngineLabel: String = "",
    val activeVoiceName: String = "",
    val isFallbackActive: Boolean = false,
    val fallbackNoticeMessage: String? = null,
    val diagnosticReason: TtsDiagnosticReason = if (success) TtsDiagnosticReason.NONE else TtsDiagnosticReason.ENGINE_BIND_FAILED,
    val diagnosticMessage: String? = null,
    val availableVoices: List<TtsVoiceDescriptor> = emptyList(),
)

/**
 * Lightweight zero-dependency catalog recognizing well-known official, open-source, and Chinese OEM
 * Android TTS engines for friendly labeling, deterministic fallback priority, and rate safety.
 */
object KnownTtsEngineCatalog {
    const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    const val WENKU8_PLUGIN_PACKAGE = "org.mewx.wenku8.plugin.tts"
    const val SHERPA_TTS_PACKAGE = "org.woheller69.ttsengine"
    const val TTS_SERVER_PACKAGE = "com.github.jing332.tts_server_android"
    const val MULTI_TTS_PACKAGE = "org.nobody.multitts"
    const val PICO_TTS_PACKAGE = "com.svox.pico"
    const val SAMSUNG_TTS_PACKAGE = "com.samsung.SMT"
    const val ACTION_WENKU8_TTS_PLUGIN_V1 = "org.mewx.wenku8.action.TTS_PLUGIN_V1"

    private data class CatalogEntry(
        val friendlyName: String,
        val category: TtsEngineCategory,
        val priorityRank: Int,
        val safeRateMin: Float = Wenku8UserPreferences.MIN_TTS_SPEECH_RATE,
        val safeRateMax: Float = Wenku8UserPreferences.MAX_TTS_SPEECH_RATE,
        val hint: String? = null,
    )

    private val exactEntries: Map<String, CatalogEntry> = mapOf(
        WENKU8_PLUGIN_PACKAGE to CatalogEntry(
            friendlyName = "Wenku8 离线 AI 语音插件",
            category = TtsEngineCategory.OFFICIAL_PLUGIN,
            priorityRank = 10,
            hint = "支持插件内按需管理开源离线中文语音模型",
        ),
        GOOGLE_TTS_PACKAGE to CatalogEntry(
            friendlyName = "Google 语音识别与合成",
            category = TtsEngineCategory.OFFICIAL_FREE,
            priorityRank = 20,
            hint = "首次使用如无声音，请点击「下载中文语音包」获取离线中文数据",
        ),
        SHERPA_TTS_PACKAGE to CatalogEntry(
            friendlyName = "SherpaTTS（开源离线）",
            category = TtsEngineCategory.OPEN_SOURCE_OFFLINE,
            priorityRank = 30,
            hint = "纯离线开源神经网络语音引擎，需在 SherpaTTS 内先下载中文模型",
        ),
        TTS_SERVER_PACKAGE to CatalogEntry(
            friendlyName = "TTS Server",
            category = TtsEngineCategory.OPEN_SOURCE_BRIDGE,
            priorityRank = 35,
            hint = "建议在系统省电策略中设为无限制，避免锁屏后服务被回收",
        ),
        MULTI_TTS_PACKAGE to CatalogEntry(
            friendlyName = "MultiTTS",
            category = TtsEngineCategory.OPEN_SOURCE_BRIDGE,
            priorityRank = 40,
            hint = "建议在系统省电策略中设为无限制，避免后台朗读中断",
        ),
        "com.xiaomi.mibrain.speech" to CatalogEntry(
            friendlyName = "小米小爱语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 50,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
            hint = "若无声请检查是否开启了小爱语音唤醒占用音频通道",
        ),
        "com.miui.voiceassist" to CatalogEntry(
            friendlyName = "小米系统语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 51,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.huawei.hiai" to CatalogEntry(
            friendlyName = "华为语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 52,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.huawei.vassistant" to CatalogEntry(
            friendlyName = "华为小艺语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 52,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.huawei.geassistant" to CatalogEntry(
            friendlyName = "华为语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 52,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.huawei.android.tts" to CatalogEntry(
            friendlyName = "华为文字转语音",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 52,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.honor.tts.engine" to CatalogEntry(
            friendlyName = "荣耀语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 54,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        SAMSUNG_TTS_PACKAGE to CatalogEntry(
            friendlyName = "三星文字转语音",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 56,
        ),
        "com.heytap.speechassist" to CatalogEntry(
            friendlyName = "OPPO / ColorOS 语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 58,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.coloros.speechassist" to CatalogEntry(
            friendlyName = "ColorOS 语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 58,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.oppo.voicedecoder" to CatalogEntry(
            friendlyName = "OPPO 语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 58,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.vivo.aiengine" to CatalogEntry(
            friendlyName = "vivo / OriginOS 语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 60,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.vivo.multimedia.tts" to CatalogEntry(
            friendlyName = "vivo 文字转语音",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 60,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.vivo.ai.voicedecoder" to CatalogEntry(
            friendlyName = "vivo 语音引擎",
            category = TtsEngineCategory.SYSTEM_OEM,
            priorityRank = 60,
            safeRateMin = 0.6f,
            safeRateMax = 2.0f,
        ),
        "com.iflytek.speechsuite" to CatalogEntry(
            friendlyName = "讯飞语音引擎",
            category = TtsEngineCategory.THIRD_PARTY,
            priorityRank = 62,
        ),
        "com.iflytek.tts" to CatalogEntry(
            friendlyName = "讯飞语音合成",
            category = TtsEngineCategory.THIRD_PARTY,
            priorityRank = 63,
        ),
        "com.baidu.duersdk.opensdk" to CatalogEntry(
            friendlyName = "百度语音引擎",
            category = TtsEngineCategory.THIRD_PARTY,
            priorityRank = 64,
        ),
        PICO_TTS_PACKAGE to CatalogEntry(
            friendlyName = "Pico TTS（不支持中文）",
            category = TtsEngineCategory.UNSUPPORTED_CHINESE,
            priorityRank = 999,
            hint = "Android 原生基础引擎仅支持部分西语，不支持中文朗读",
        ),
    )

    fun describe(
        packageName: String,
        systemLabel: String = "",
        isSystemDefault: Boolean = false,
        supportsPluginV1: Boolean = false,
    ): TtsEngineDescriptor {
        val pkg = packageName.trim()
        val matched = exactEntries[pkg] ?: matchByHeuristic(pkg, supportsPluginV1)
        val display = when {
            matched != null -> matched.friendlyName
            systemLabel.isNotBlank() -> systemLabel.trim()
            else -> pkg
        }
        return TtsEngineDescriptor(
            packageName = pkg,
            displayName = display,
            systemLabel = systemLabel.trim().ifBlank { display },
            category = matched?.category ?: if (supportsPluginV1) {
                TtsEngineCategory.OFFICIAL_PLUGIN
            } else {
                TtsEngineCategory.THIRD_PARTY
            },
            isSystemDefault = isSystemDefault,
            supportsPluginV1 = supportsPluginV1,
            priorityRank = matched?.priorityRank ?: if (supportsPluginV1) 15 else 75,
            safeRateMin = matched?.safeRateMin ?: Wenku8UserPreferences.MIN_TTS_SPEECH_RATE,
            safeRateMax = matched?.safeRateMax ?: Wenku8UserPreferences.MAX_TTS_SPEECH_RATE,
            compatibilityHint = matched?.hint,
        )
    }

    fun clampSpeechRateForEngine(packageName: String, rawRate: Float): Float {
        val desc = describe(packageName)
        return rawRate.coerceIn(desc.safeRateMin, desc.safeRateMax)
    }

    fun defaultLightNovelSampleText(appLanguage: Wenku8API.AppLanguage): String =
        if (appLanguage == Wenku8API.AppLanguage.TC) {
            "清晨的微風掠過窗台，少年合上手中的輕小說，輕輕嘆了口氣：「今天的冒險，也終於要開始了嗎？」"
        } else {
            "清晨的微风掠过窗台，少年合上手中的轻小说，轻轻叹了口气：“今天的冒险，也终于要开始了吗？”"
        }

    private fun matchByHeuristic(packageName: String, supportsPluginV1: Boolean): CatalogEntry? {
        val lower = packageName.lowercase()
        return when {
            supportsPluginV1 -> CatalogEntry(
                friendlyName = "Wenku8 兼容语音插件",
                category = TtsEngineCategory.OFFICIAL_PLUGIN,
                priorityRank = 15,
            )
            lower.contains("voxsherpa") -> CatalogEntry(
                friendlyName = "VoxSherpa（开源离线）",
                category = TtsEngineCategory.OPEN_SOURCE_OFFLINE,
                priorityRank = 25,
                hint = "开源离线神经网络语音引擎，支持 Kokoro / Piper 中文模型",
            )
            lower.contains("sherpa") -> CatalogEntry(
                friendlyName = "Sherpa-ONNX 离线语音",
                category = TtsEngineCategory.OPEN_SOURCE_OFFLINE,
                priorityRank = 32,
            )
            lower.startsWith("com.xiaomi.") || lower.startsWith("com.miui.") -> CatalogEntry(
                friendlyName = "小米系统语音引擎",
                category = TtsEngineCategory.SYSTEM_OEM,
                priorityRank = 51,
                safeRateMin = 0.6f,
                safeRateMax = 2.0f,
            )
            lower.startsWith("com.huawei.") -> CatalogEntry(
                friendlyName = "华为语音引擎",
                category = TtsEngineCategory.SYSTEM_OEM,
                priorityRank = 53,
                safeRateMin = 0.6f,
                safeRateMax = 2.0f,
            )
            lower.startsWith("com.honor.") -> CatalogEntry(
                friendlyName = "荣耀语音引擎",
                category = TtsEngineCategory.SYSTEM_OEM,
                priorityRank = 55,
                safeRateMin = 0.6f,
                safeRateMax = 2.0f,
            )
            lower.startsWith("com.oppo.") || lower.startsWith("com.heytap.") || lower.startsWith("com.coloros.") ->
                CatalogEntry(
                    friendlyName = "OPPO / ColorOS 语音引擎",
                    category = TtsEngineCategory.SYSTEM_OEM,
                    priorityRank = 59,
                    safeRateMin = 0.6f,
                    safeRateMax = 2.0f,
                )
            lower.startsWith("com.vivo.") -> CatalogEntry(
                friendlyName = "vivo / OriginOS 语音引擎",
                category = TtsEngineCategory.SYSTEM_OEM,
                priorityRank = 61,
                safeRateMin = 0.6f,
                safeRateMax = 2.0f,
            )
            lower.startsWith("com.iflytek.") -> CatalogEntry(
                friendlyName = "讯飞语音引擎",
                category = TtsEngineCategory.THIRD_PARTY,
                priorityRank = 63,
            )
            else -> null
        }
    }
}
