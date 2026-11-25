package org.mewx.wenku8.tts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TtsDiscoveryAndFallbackTest {

    private fun sampleEngines(): List<TtsEngineDescriptor> = listOf(
        KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.PICO_TTS_PACKAGE, isSystemDefault = true),
        KnownTtsEngineCatalog.describe("com.xiaomi.mibrain.speech"),
        KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE),
        KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE),
        KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.MULTI_TTS_PACKAGE),
    )

    @Test
    fun fallbackOrder_prioritizesPreferredThenExplicitFallbackThenCatalogAndDemotesPico() {
        val order = TtsEngineDiscovery.buildFallbackOrder(
            installedEngines = sampleEngines(),
            preferredEnginePackage = "com.xiaomi.mibrain.speech",
            fallbackEnginePackage = KnownTtsEngineCatalog.MULTI_TTS_PACKAGE,
            autoFallbackEnabled = true,
        )

        assertEquals(
            listOf(
                "com.xiaomi.mibrain.speech",
                KnownTtsEngineCatalog.MULTI_TTS_PACKAGE,
                KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE,
                KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE,
                KnownTtsEngineCatalog.PICO_TTS_PACKAGE,
            ),
            order.map { it.packageName },
        )
    }

    @Test
    fun fallbackOrder_whenAutoFallbackDisabled_stopsAtPreferredEngine() {
        val order = TtsEngineDiscovery.buildFallbackOrder(
            installedEngines = sampleEngines(),
            preferredEnginePackage = KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE,
            fallbackEnginePackage = KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE,
            autoFallbackEnabled = false,
        )
        assertEquals(listOf(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE), order.map { it.packageName })

        // Missing preferred engine with auto-fallback OFF returns empty instead of silently switching.
        val missingOrder = TtsEngineDiscovery.buildFallbackOrder(
            installedEngines = sampleEngines(),
            preferredEnginePackage = "com.uninstalled.tts",
            autoFallbackEnabled = false,
        )
        assertTrue(missingOrder.isEmpty())
    }

    @Test
    fun fallbackOrder_excludesPreviouslyFailedPackagesToPreventLoops() {
        val order = TtsEngineDiscovery.buildFallbackOrder(
            installedEngines = sampleEngines(),
            preferredEnginePackage = KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE,
            autoFallbackEnabled = true,
            excludedPackages = setOf(
                KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE,
                KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE,
            ),
        )
        assertFalse(order.any { it.packageName == KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE })
        assertFalse(order.any { it.packageName == KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE })
        assertEquals(KnownTtsEngineCatalog.MULTI_TTS_PACKAGE, order.first().packageName)
        assertEquals(KnownTtsEngineCatalog.PICO_TTS_PACKAGE, order.last().packageName)
    }

    @Test
    fun voiceFormatter_formatsHumanReadableLabelsAndFallsBackOfflineWhenDisconnected() {
        val voices = listOf(
            TtsVoiceFormatter.describeVoiceFields("cmn-cn-x-ccd-network", "zh-CN", isNetworkRequired = true, qualityScore = 500),
            TtsVoiceFormatter.describeVoiceFields("cmn-cn-x-ccc-local", "zh-CN", isNetworkRequired = false, qualityScore = 400),
            TtsVoiceFormatter.describeVoiceFields("cmn-tw-x-ctc-local", "zh-TW", isNetworkRequired = false, qualityScore = 400),
            TtsVoiceFormatter.describeVoiceFields("en-us-x-sfg-local", "en-US", isNetworkRequired = false, qualityScore = 400),
        )

        assertTrue(voices[0].isChinese)
        assertTrue(voices[0].displayLabel.contains("简体中文"))
        assertTrue(voices[0].displayLabel.contains("在线"))
        assertTrue(voices[1].displayLabel.contains("离线"))
        assertTrue(voices[1].displayLabel.contains("ccc"))
        assertTrue(voices[2].displayLabel.contains("繁體中文"))
        assertFalse(voices[3].isChinese)

        // When online, user's preferred network voice is respected.
        val onlinePick = TtsVoiceFormatter.selectBestVoice(
            voices = voices,
            preferredVoiceName = "cmn-cn-x-ccd-network",
            appLanguage = Wenku8API.AppLanguage.SC,
            isNetworkAvailable = true,
        )
        assertEquals("cmn-cn-x-ccd-network", onlinePick?.name)

        // When offline, network-only preferred voice gracefully falls back to best offline Chinese voice!
        val offlinePick = TtsVoiceFormatter.selectBestVoice(
            voices = voices,
            preferredVoiceName = "cmn-cn-x-ccd-network",
            appLanguage = Wenku8API.AppLanguage.SC,
            isNetworkAvailable = false,
        )
        assertEquals("cmn-cn-x-ccc-local", offlinePick?.name)

        // Traditional Chinese prefers zh-TW offline voice.
        val tcPick = TtsVoiceFormatter.selectBestVoice(
            voices = voices,
            preferredVoiceName = "",
            appLanguage = Wenku8API.AppLanguage.TC,
            isNetworkAvailable = false,
        )
        assertEquals("cmn-tw-x-ctc-local", tcPick?.name)
    }

    @Test
    fun localeChain_providesOrderedTcAndScFallbacks() {
        val tcChain = TtsVoiceFormatter.preferredLocaleChain(Wenku8API.AppLanguage.TC)
        assertEquals("zh-TW", tcChain[0].toLanguageTag())
        assertEquals("zh-HK", tcChain[1].toLanguageTag())
        assertEquals("zh-CN", tcChain[2].toLanguageTag())

        val scChain = TtsVoiceFormatter.preferredLocaleChain(Wenku8API.AppLanguage.SC)
        assertEquals("zh-CN", scChain[0].toLanguageTag())
        assertEquals("zh", scChain[1].toLanguageTag())
    }

    @Test
    fun channelGuidance_playStoreHasZeroExternalSideloadUrlsAndGuidesToGoogleTts() {
        val noEnginesPlay = TtsSystemIntents.buildChannelGuidanceOptions(
            isPlayStoreChannel = true,
            installedEngines = emptyList(),
            diagnosticReason = TtsDiagnosticReason.NO_ENGINE_INSTALLED,
            manufacturer = "Xiaomi",
        )
        assertTrue(noEnginesPlay.isNotEmpty())
        assertTrue(
            "Play Store channel must never emit external sideload URLs",
            noEnginesPlay.none { it.action is TtsGuidanceAction.OpenExternalFreeUrl },
        )
        assertTrue(
            noEnginesPlay.any {
                it.action == TtsGuidanceAction.OpenPlayStoreApp(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE)
            },
        )

        // Once Google TTS is installed, Play Store card switches to Voice Data Download guidance.
        val withGooglePlay = TtsSystemIntents.buildChannelGuidanceOptions(
            isPlayStoreChannel = true,
            installedEngines = listOf(KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE)),
            diagnosticReason = TtsDiagnosticReason.MISSING_CHINESE_DATA,
            manufacturer = "Google",
        )
        assertTrue(
            withGooglePlay.any {
                it.action == TtsGuidanceAction.OpenInstallVoiceData(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE)
            },
        )
    }

    @Test
    fun channelGuidance_defaultChannelRecommendsOpenSourceOfflineFreeEnginesAndOemPaths() {
        val defaultItems = TtsSystemIntents.buildChannelGuidanceOptions(
            isPlayStoreChannel = false,
            installedEngines = emptyList(),
            diagnosticReason = TtsDiagnosticReason.NO_ENGINE_INSTALLED,
            manufacturer = "HUAWEI",
        )
        assertTrue(defaultItems.any { it.id == "default_sherpa_tts" })
        assertTrue(defaultItems.any { it.id == "default_vox_sherpa" })
        assertTrue(defaultItems.any { it.id == "default_tts_server" })
        assertTrue(defaultItems.any { it.action == TtsGuidanceAction.OpenSystemTtsSettings })

        val huaweiOem = TtsSystemIntents.buildOemGuidance("HUAWEI")
        assertTrue(huaweiOem.brandLabel.contains("华为"))
        assertNotNull(huaweiOem.backgroundKeepAliveHint)
        val xiaomiOem = TtsSystemIntents.buildOemGuidance("Xiaomi")
        assertTrue(xiaomiOem.brandLabel.contains("小米"))
    }
}
