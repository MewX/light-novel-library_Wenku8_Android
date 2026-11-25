package org.mewx.wenku8.tts

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import org.mewx.wenku8.api.Wenku8API
import java.util.Locale

/**
 * Discovers installed Android `TextToSpeechService` engines (including open-source plugins and
 * Chinese OEM engines) and computes deterministic fallback probe ordering.
 */
object TtsEngineDiscovery {

    private const val SECURE_TTS_DEFAULT_SYNTH = "tts_default_synth"

    fun getSystemDefaultEnginePackage(context: Context): String =
        runCatching {
            Settings.Secure.getString(context.contentResolver, SECURE_TTS_DEFAULT_SYNTH)
                .orEmpty()
                .trim()
        }.getOrDefault("")

    fun discoverInstalledEngines(context: Context): List<TtsEngineDescriptor> {
        val pm = context.packageManager
        val systemDefaultPkg = getSystemDefaultEnginePackage(context)
        val pluginV1Packages = queryPackagesForAction(pm, KnownTtsEngineCatalog.ACTION_WENKU8_TTS_PLUGIN_V1)

        val resolvedServices = queryTtsServices(pm)
        val seen = LinkedHashMap<String, TtsEngineDescriptor>()
        for (ri in resolvedServices) {
            val pkg = ri.serviceInfo?.packageName?.trim().orEmpty()
            if (pkg.isEmpty() || seen.containsKey(pkg)) continue
            val label = runCatching { ri.loadLabel(pm).toString() }.getOrDefault("")
            seen[pkg] = KnownTtsEngineCatalog.describe(
                packageName = pkg,
                systemLabel = label,
                isSystemDefault = pkg == systemDefaultPkg,
                supportsPluginV1 = pluginV1Packages.contains(pkg),
            )
        }

        // Also include system default package if it is installed but not returned by service query.
        if (systemDefaultPkg.isNotEmpty() && !seen.containsKey(systemDefaultPkg) && isPackageInstalled(pm, systemDefaultPkg)) {
            val appLabel = runCatching {
                val appInfo = pm.getApplicationInfo(systemDefaultPkg, 0)
                pm.getApplicationLabel(appInfo).toString()
            }.getOrDefault("")
            seen[systemDefaultPkg] = KnownTtsEngineCatalog.describe(
                packageName = systemDefaultPkg,
                systemLabel = appLabel,
                isSystemDefault = true,
                supportsPluginV1 = pluginV1Packages.contains(systemDefaultPkg),
            )
        }

        return seen.values.sortedWith(
            compareBy<TtsEngineDescriptor>(
                { it.priorityRank },
                { if (it.isSystemDefault) 0 else 1 },
                { it.displayName },
            ),
        )
    }

    /**
     * Computes deterministic candidate probe order:
     * 1. Explicit [preferredEnginePackage] (if installed & not excluded)
     * 2. Explicit [fallbackEnginePackage] (if [autoFallbackEnabled] and installed & not excluded)
     * 3. System default engine (if not Pico & not excluded)
     * 4. Remaining Chinese-capable engines ordered by [TtsEngineDescriptor.priorityRank]
     * 5. Unsupported-Chinese engines (e.g. Pico TTS) strictly last so callers can report accurate
     *    missing-Chinese diagnostics when nothing else exists.
     */
    fun buildFallbackOrder(
        installedEngines: List<TtsEngineDescriptor>,
        preferredEnginePackage: String = "",
        fallbackEnginePackage: String = "",
        autoFallbackEnabled: Boolean = true,
        excludedPackages: Set<String> = emptySet(),
    ): List<TtsEngineDescriptor> {
        val eligible = installedEngines.filterNot { it.packageName in excludedPackages }
        if (eligible.isEmpty()) return emptyList()

        val byPkg = eligible.associateBy { it.packageName }
        val result = ArrayList<TtsEngineDescriptor>(eligible.size)
        val added = HashSet<String>()

        fun addIfEligible(pkg: String) {
            val trimmed = pkg.trim()
            if (trimmed.isEmpty()) return
            val desc = byPkg[trimmed] ?: return
            if (added.add(desc.packageName)) {
                result.add(desc)
            }
        }

        val pref = preferredEnginePackage.trim()
        if (pref.isNotEmpty()) {
            addIfEligible(pref)
            if (!autoFallbackEnabled) {
                return result
            }
        }

        if (autoFallbackEnabled) {
            addIfEligible(fallbackEnginePackage)
        }

        // Prefer system default (if not known-unsupported like Pico) ahead of generic ordering
        // when the user has not pinned a specific preferred engine.
        val systemDefault = eligible.firstOrNull { it.isSystemDefault && !it.isKnownUnsupportedChinese }
        if (systemDefault != null && added.add(systemDefault.packageName)) {
            result.add(systemDefault)
            if (!autoFallbackEnabled) return result
        }

        val remainingSupported = eligible
            .filter { !it.isKnownUnsupportedChinese && it.packageName !in added }
            .sortedWith(compareBy<TtsEngineDescriptor>({ it.priorityRank }, { it.displayName }))
        if (!autoFallbackEnabled) {
            remainingSupported.firstOrNull()?.let { result.add(it) }
            if (result.isEmpty()) {
                eligible.firstOrNull()?.let { result.add(it) }
            }
            return result
        }

        result.addAll(remainingSupported)
        val remainingUnsupported = eligible.filter { it.isKnownUnsupportedChinese && it.packageName !in added }
        result.addAll(remainingUnsupported)
        return result
    }

    fun isNetworkConnected(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun queryTtsServices(pm: PackageManager) = runCatching {
        val intent = Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentServices(intent, 0)
        }
    }.getOrDefault(emptyList())

    private fun queryPackagesForAction(pm: PackageManager, action: String): Set<String> {
        val out = HashSet<String>()
        val intent = Intent(action)
        runCatching {
            val services = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentServices(intent, 0)
            }
            services.mapNotNullTo(out) { it.serviceInfo?.packageName?.trim()?.takeIf(String::isNotEmpty) }
        }
        runCatching {
            val activities = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(intent, 0)
            }
            activities.mapNotNullTo(out) { it.activityInfo?.packageName?.trim()?.takeIf(String::isNotEmpty) }
        }
        return out
    }

    private fun isPackageInstalled(pm: PackageManager, pkg: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.getPackageInfo(pkg, 0)
        }
        true
    }.getOrDefault(false)
}

/**
 * Formats Android [Voice] identifiers into human-readable Chinese labels and implements
 * offline-aware voice fallback selection.
 */
object TtsVoiceFormatter {

    fun preferredLocaleChain(appLanguage: Wenku8API.AppLanguage): List<Locale> =
        if (appLanguage == Wenku8API.AppLanguage.TC) {
            listOf(
                Locale.TRADITIONAL_CHINESE,
                Locale.forLanguageTag("zh-HK"),
                Locale.SIMPLIFIED_CHINESE,
                Locale.CHINESE,
            )
        } else {
            listOf(
                Locale.SIMPLIFIED_CHINESE,
                Locale.CHINESE,
                Locale.TRADITIONAL_CHINESE,
            )
        }

    fun isChineseLocaleTag(localeTag: String): Boolean {
        val norm = localeTag.trim().lowercase(Locale.ROOT).replace('_', '-')
        return norm == "zh" || norm.startsWith("zh-") ||
            norm == "cmn" || norm.startsWith("cmn-") ||
            norm == "yue" || norm.startsWith("yue-")
    }

    fun describeVoice(voice: Voice): TtsVoiceDescriptor {
        val tag = runCatching { voice.locale?.toLanguageTag().orEmpty() }.getOrDefault("")
        val quality = runCatching { voice.quality }.getOrDefault(Voice.QUALITY_NORMAL)
        val networkRequired = runCatching { voice.isNetworkConnectionRequired }.getOrDefault(false)
        return describeVoiceFields(
            name = voice.name.orEmpty(),
            localeTag = tag,
            isNetworkRequired = networkRequired,
            qualityScore = quality,
        )
    }

    fun describeVoiceFields(
        name: String,
        localeTag: String,
        isNetworkRequired: Boolean,
        qualityScore: Int = 300,
    ): TtsVoiceDescriptor {
        val normTag = localeTag.trim().replace('_', '-')
        val lowerTag = normTag.lowercase(Locale.ROOT)
        val chinese = isChineseLocaleTag(normTag)
        val regionLabel = when {
            lowerTag.contains("tw") || lowerTag.contains("hant-tw") -> "繁體中文（台灣）"
            lowerTag.contains("hk") || lowerTag.startsWith("yue") -> "粵語 / 繁中（香港）"
            lowerTag.contains("cn") || lowerTag.contains("hans") || lowerTag.startsWith("cmn") -> "简体中文（大陆）"
            chinese -> "中文"
            normTag.isNotEmpty() -> runCatching {
                Locale.forLanguageTag(normTag).getDisplayName(Locale.SIMPLIFIED_CHINESE)
            }.getOrDefault(normTag).ifBlank { normTag }
            else -> "默认语言"
        }
        val shortCode = extractShortVoiceToken(name)
        val modeTag = if (isNetworkRequired) "在线" else "离线"
        val qualityTag = if (qualityScore >= 400) " · 高清" else ""
        val codeSuffix = if (shortCode.isNotEmpty()) " ($shortCode)" else ""
        val label = "$regionLabel$codeSuffix · $modeTag$qualityTag"
        return TtsVoiceDescriptor(
            name = name,
            displayLabel = label,
            localeTag = normTag,
            isNetworkRequired = isNetworkRequired,
            isChinese = chinese,
            qualityScore = qualityScore,
        )
    }

    fun selectBestVoice(
        voices: List<TtsVoiceDescriptor>,
        preferredVoiceName: String,
        appLanguage: Wenku8API.AppLanguage,
        isNetworkAvailable: Boolean,
    ): TtsVoiceDescriptor? {
        if (voices.isEmpty()) return null
        val trimmedPref = preferredVoiceName.trim()
        if (trimmedPref.isNotEmpty()) {
            val exact = voices.firstOrNull { it.name == trimmedPref }
            if (exact != null && (!exact.isNetworkRequired || isNetworkAvailable)) {
                return exact
            }
        }

        val chineseVoices = voices.filter { it.isChinese }
        val pool = if (chineseVoices.isNotEmpty()) chineseVoices else voices
        val usablePool = if (isNetworkAvailable) {
            pool
        } else {
            pool.filter { !it.isNetworkRequired }.ifEmpty { pool }
        }

        return usablePool.sortedWith(
            compareBy<TtsVoiceDescriptor>(
                { localePreferenceRank(it.localeTag, appLanguage) },
                { if (it.isNetworkRequired) 1 else 0 },
                { -it.qualityScore },
                { it.name },
            ),
        ).firstOrNull()
    }

    private fun localePreferenceRank(localeTag: String, appLanguage: Wenku8API.AppLanguage): Int {
        val lower = localeTag.lowercase(Locale.ROOT).replace('_', '-')
        return if (appLanguage == Wenku8API.AppLanguage.TC) {
            when {
                lower.contains("tw") || lower.contains("hant") -> 0
                lower.contains("hk") -> 1
                lower.contains("cn") || lower.contains("hans") || lower.startsWith("cmn") -> 2
                isChineseLocaleTag(lower) -> 3
                else -> 10
            }
        } else {
            when {
                lower.contains("cn") || lower.contains("hans") || lower.startsWith("cmn-cn") -> 0
                lower == "zh" || lower == "cmn" -> 1
                lower.contains("tw") || lower.contains("hk") -> 2
                isChineseLocaleTag(lower) -> 3
                else -> 10
            }
        }
    }

    private fun extractShortVoiceToken(rawName: String): String {
        val cleaned = rawName.trim()
        if (cleaned.isEmpty()) return ""
        val tokens = cleaned.split('-', '_', '#').filter { it.isNotBlank() }
        // Google voices look like "cmn-cn-x-ccc-local" or "zh-CN-language"; pull the distinctive ID.
        val xIdx = tokens.indexOfFirst { it.equals("x", ignoreCase = true) }
        if (xIdx >= 0 && xIdx + 1 < tokens.size) {
            return tokens[xIdx + 1]
        }
        val filtered = tokens.filterNot { t ->
            t.equals("local", true) || t.equals("network", true) ||
                t.equals("language", true) || t.equals("zh", true) ||
                t.equals("cn", true) || t.equals("tw", true) ||
                t.equals("hk", true) || t.equals("cmn", true)
        }
        return filtered.lastOrNull()?.take(14).orEmpty()
    }
}

/** OEM-specific settings path & background keep-alive tips for mainland Chinese ROMs. */
data class TtsOemGuidance(
    val brandLabel: String,
    val settingsPathHint: String,
    val backgroundKeepAliveHint: String,
)

/** Result of 4-tier safe jump to Android system TTS settings. */
enum class TtsSettingsJumpTier {
    DIRECT_TTS_SETTINGS,
    ACCESSIBILITY_SETTINGS,
    LOCALE_SETTINGS,
    GENERAL_SETTINGS,
    FAILED,
}

/** Actionable free/official TTS onboarding item rendered in channel-aware cards. */
sealed interface TtsGuidanceAction {
    data class OpenPlayStoreApp(val packageName: String) : TtsGuidanceAction
    data class OpenInstallVoiceData(val enginePackage: String) : TtsGuidanceAction
    data object OpenSystemTtsSettings : TtsGuidanceAction
    data class OpenExternalFreeUrl(val url: String) : TtsGuidanceAction
}

data class TtsGuidanceItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val actionLabel: String,
    val action: TtsGuidanceAction,
)

/**
 * Channel-aware (`playstore` vs `default`) free/official TTS installation guidance and
 * 4-tier safe system intent launchers tailored to Chinese ROMs.
 */
object TtsSystemIntents {

    const val ACTION_SYSTEM_TTS_SETTINGS = "com.android.settings.TTS_SETTINGS"
    const val GOOGLE_TTS_PLAY_HTTPS_URL =
        "https://play.google.com/store/apps/details?id=${KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE}"
    const val SHERPA_TTS_RELEASES_URL = "https://github.com/woheller69/ttsEngine/releases"
    const val VOX_SHERPA_RELEASES_URL = "https://github.com/CodeBySonu95/VoxSherpa-TTS/releases"
    const val TTS_SERVER_GITHUB_URL = "https://github.com/jing332/tts-server-android"

    fun buildOemGuidance(manufacturer: String = Build.MANUFACTURER.orEmpty()): TtsOemGuidance {
        val m = manufacturer.trim().lowercase(Locale.ROOT)
        return when {
            m.contains("xiaomi") || m.contains("redmi") || m.contains("poco") -> TtsOemGuidance(
                brandLabel = "小米 / HyperOS / MIUI",
                settingsPathHint = "设置 → 更多设置 → 语言与输入法 → 文字转语音 (TTS) 输出",
                backgroundKeepAliveHint = "在应用信息中将轻小说文库及所用语音引擎的省电策略设为「无限制」，避免锁屏后朗读中断。",
            )
            m.contains("huawei") -> TtsOemGuidance(
                brandLabel = "华为 / HarmonyOS",
                settingsPathHint = "设置 → 系统和更新 → 语言和输入法 → 文字转语音（或直接在设置顶部搜「文字转语音」）",
                backgroundKeepAliveHint = "在「应用启动管理」中关闭自动管理，允许后台活动与关联启动，避免息屏断播。",
            )
            m.contains("honor") -> TtsOemGuidance(
                brandLabel = "荣耀 / MagicOS",
                settingsPathHint = "设置 → 辅助功能 / 系统和更新 → 文字转语音输出（可在设置顶部直接搜「TTS」）",
                backgroundKeepAliveHint = "在电池应用启动管理中为轻小说文库及语音引擎开启「允许后台活动」。",
            )
            m.contains("oppo") || m.contains("oneplus") || m.contains("realme") -> TtsOemGuidance(
                brandLabel = "OPPO / 一加 / ColorOS",
                settingsPathHint = "设置 → 其他设置 / 无障碍 → 语言与输入法 → 文字转语音输出",
                backgroundKeepAliveHint = "在电池设置中允许轻小说文库及语音引擎后台运行，避免息屏后音频焦点被回收。",
            )
            m.contains("vivo") || m.contains("iqoo") -> TtsOemGuidance(
                brandLabel = "vivo / iQOO / OriginOS",
                settingsPathHint = "设置 → 快捷与辅助 → 无障碍 → 文字转语音输出",
                backgroundKeepAliveHint = "在电池后台耗电管理中将轻小说文库及语音引擎设为「允许后台高耗电」。",
            )
            m.contains("samsung") -> TtsOemGuidance(
                brandLabel = "三星 / One UI",
                settingsPathHint = "设置 → 常规管理 → 文字转语音输出",
                backgroundKeepAliveHint = "可在首选项中将引擎切换为 Google 语音识别与合成或开源离线引擎以获得更自然的中文听感。",
            )
            else -> TtsOemGuidance(
                brandLabel = "Android 系统",
                settingsPathHint = "系统设置顶部搜索「文字转语音」或「TTS」进入输出设置",
                backgroundKeepAliveHint = "如锁屏后易中断，请在系统电池优化中将本应用和所选 TTS 引擎设为「不受限制」。",
            )
        }
    }

    /**
     * Builds strictly free/official onboarding recommendations per distribution channel:
     * - `playstore`: NEVER emits external/sideload URLs; only Play Store official listings and
     *   native Android system intents.
     * - `default`: prioritizes built-in OEM guidance + free open-source offline/bridge engines
     *   that work cleanly in mainland China without GMS/VPN.
     */
    fun buildChannelGuidanceOptions(
        isPlayStoreChannel: Boolean,
        installedEngines: List<TtsEngineDescriptor>,
        activeEnginePackage: String = "",
        diagnosticReason: TtsDiagnosticReason = TtsDiagnosticReason.NONE,
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
    ): List<TtsGuidanceItem> {
        val oem = buildOemGuidance(manufacturer)
        val hasGoogleTts = installedEngines.any { it.packageName == KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE }
        val items = ArrayList<TtsGuidanceItem>()

        if (isPlayStoreChannel) {
            if (!hasGoogleTts) {
                items.add(
                    TtsGuidanceItem(
                        id = "play_install_google_tts",
                        title = "安装 Speech Recognition & Synthesis",
                        subtitle = "Google 官方免费语音引擎，安装后返回本页即可自动识别并直接启用，无需更改全局默认。",
                        badge = "官方免费",
                        actionLabel = "前往 Play 商店",
                        action = TtsGuidanceAction.OpenPlayStoreApp(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE),
                    ),
                )
            } else {
                items.add(
                    TtsGuidanceItem(
                        id = "play_google_tts_data",
                        title = "下载 Google 中文离线语音包",
                        subtitle = "已检测到 Google 语音识别与合成；若提示缺少中文数据或无声，请点击下载中文语音包。",
                        badge = "语音数据",
                        actionLabel = "下载语音包",
                        action = TtsGuidanceAction.OpenInstallVoiceData(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE),
                    ),
                )
            }

            if (activeEnginePackage.isNotBlank() &&
                activeEnginePackage != KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE &&
                diagnosticReason == TtsDiagnosticReason.MISSING_CHINESE_DATA
            ) {
                items.add(
                    TtsGuidanceItem(
                        id = "play_active_engine_data",
                        title = "下载当前引擎中文语音数据",
                        subtitle = "唤起当前所选引擎的数据管理界面以补齐中文语音包。",
                        badge = "语音数据",
                        actionLabel = "下载数据",
                        action = TtsGuidanceAction.OpenInstallVoiceData(activeEnginePackage),
                    ),
                )
            }

            items.add(
                TtsGuidanceItem(
                    id = "play_system_tts_settings",
                    title = "打开系统文字转语音设置",
                    subtitle = "${oem.brandLabel}：${oem.settingsPathHint}",
                    badge = "系统设置",
                    actionLabel = "去设置",
                    action = TtsGuidanceAction.OpenSystemTtsSettings,
                ),
            )
            return items
        }

        // `default` (direct / GitHub / mainland China) channel: prioritize free offline & open-source engines.
        if (diagnosticReason == TtsDiagnosticReason.MISSING_CHINESE_DATA && activeEnginePackage.isNotBlank()) {
            items.add(
                TtsGuidanceItem(
                    id = "default_active_engine_data",
                    title = "下载当前引擎中文语音包",
                    subtitle = "当前已绑定引擎但缺少中文语音数据，可尝试直接拉起数据包下载界面。",
                    badge = "语音数据",
                    actionLabel = "下载数据",
                    action = TtsGuidanceAction.OpenInstallVoiceData(activeEnginePackage),
                ),
            )
        }

        val hasSherpa = installedEngines.any { it.packageName == KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE }
        if (!hasSherpa) {
            items.add(
                TtsGuidanceItem(
                    id = "default_sherpa_tts",
                    title = "SherpaTTS（开源纯离线 AI 语音）",
                    subtitle = "无需 GMS 与联网即可离线朗读中文，支持 F-Droid 与 GitHub Releases 免费获取，安装后在列表直接选用。",
                    badge = "开源离线",
                    actionLabel = "查看项目",
                    action = TtsGuidanceAction.OpenExternalFreeUrl(SHERPA_TTS_RELEASES_URL),
                ),
            )
        }

        val hasVoxSherpa = installedEngines.any { it.packageName.contains("voxsherpa", ignoreCase = true) }
        if (!hasVoxSherpa) {
            items.add(
                TtsGuidanceItem(
                    id = "default_vox_sherpa",
                    title = "VoxSherpa（开源 Kokoro / Piper 离线语音）",
                    subtitle = "基于 Sherpa-ONNX 的开源离线神经网络语音引擎，中文听感自然，安装后自动发现。",
                    badge = "开源离线",
                    actionLabel = "查看项目",
                    action = TtsGuidanceAction.OpenExternalFreeUrl(VOX_SHERPA_RELEASES_URL),
                ),
            )
        }

        val hasTtsServer = installedEngines.any { it.packageName == KnownTtsEngineCatalog.TTS_SERVER_PACKAGE }
        if (!hasTtsServer) {
            items.add(
                TtsGuidanceItem(
                    id = "default_tts_server",
                    title = "TTS Server（开源扩展系统 TTS 引擎）",
                    subtitle = "热门开源安卓 TTS 聚合引擎，支持自由对接开源自建或免费语音源，适合进阶听书用户。",
                    badge = "开源扩展",
                    actionLabel = "查看项目",
                    action = TtsGuidanceAction.OpenExternalFreeUrl(TTS_SERVER_GITHUB_URL),
                ),
            )
        }

        items.add(
            TtsGuidanceItem(
                id = "default_system_tts_settings",
                title = "检查系统自带文字转语音引擎",
                subtitle = "${oem.brandLabel}：${oem.settingsPathHint}",
                badge = "系统自带",
                actionLabel = "去设置",
                action = TtsGuidanceAction.OpenSystemTtsSettings,
            ),
        )

        if (!hasGoogleTts) {
            items.add(
                TtsGuidanceItem(
                    id = "default_google_tts_gms_note",
                    title = "Google Speech Recognition & Synthesis（GMS 环境）",
                    subtitle = "若设备具备完整 Google 服务与网络环境，也可安装官方免费 Google TTS 并下载中文离线包。",
                    badge = "GMS 备选",
                    actionLabel = "前往商店页",
                    action = TtsGuidanceAction.OpenExternalFreeUrl(GOOGLE_TTS_PLAY_HTTPS_URL),
                ),
            )
        }

        return items
    }

    /**
     * 4-tier safe navigation to Android's TTS settings, resilient to customized Chinese ROMs
     * where `com.android.settings.TTS_SETTINGS` throws [ActivityNotFoundException].
     */
    fun openSystemTtsSettings(context: Context): TtsSettingsJumpTier {
        val tiers = listOf(
            TtsSettingsJumpTier.DIRECT_TTS_SETTINGS to Intent(ACTION_SYSTEM_TTS_SETTINGS),
            TtsSettingsJumpTier.ACCESSIBILITY_SETTINGS to Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
            TtsSettingsJumpTier.LOCALE_SETTINGS to Intent(Settings.ACTION_LOCALE_SETTINGS),
            TtsSettingsJumpTier.GENERAL_SETTINGS to Intent(Settings.ACTION_SETTINGS),
        )
        for ((tier, baseIntent) in tiers) {
            val started = startActivitySafely(context, baseIntent)
            if (started) return tier
        }
        return TtsSettingsJumpTier.FAILED
    }

    fun openInstallTtsVoiceData(context: Context, enginePackage: String = ""): Boolean {
        val trimmed = enginePackage.trim()
        if (trimmed.isNotEmpty()) {
            val targeted = Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA).apply {
                setPackage(trimmed)
            }
            if (startActivitySafely(context, targeted)) return true
        }
        if (startActivitySafely(context, Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))) {
            return true
        }
        return openSystemTtsSettings(context) != TtsSettingsJumpTier.FAILED
    }

    fun openPlayStoreApp(context: Context, packageName: String): Boolean {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return false
        val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$pkg"))
        if (startActivitySafely(context, marketIntent)) return true
        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$pkg"),
        )
        return startActivitySafely(context, webIntent)
    }

    fun openExternalUrl(context: Context, url: String): Boolean {
        val trimmed = url.trim()
        if (!trimmed.startsWith("https://")) return false
        return startActivitySafely(context, Intent(Intent.ACTION_VIEW, Uri.parse(trimmed)))
    }

    fun executeGuidanceAction(context: Context, action: TtsGuidanceAction): Boolean = when (action) {
        is TtsGuidanceAction.OpenPlayStoreApp -> openPlayStoreApp(context, action.packageName)
        is TtsGuidanceAction.OpenInstallVoiceData -> openInstallTtsVoiceData(context, action.enginePackage)
        TtsGuidanceAction.OpenSystemTtsSettings -> openSystemTtsSettings(context) != TtsSettingsJumpTier.FAILED
        is TtsGuidanceAction.OpenExternalFreeUrl -> openExternalUrl(context, action.url)
    }

    private fun startActivitySafely(context: Context, intent: Intent): Boolean {
        val prepared = Intent(intent).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(prepared)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: RuntimeException) {
            false
        }
    }
}
