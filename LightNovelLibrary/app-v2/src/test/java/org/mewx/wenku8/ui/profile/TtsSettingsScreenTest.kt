package org.mewx.wenku8.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.tts.KnownTtsEngineCatalog
import org.mewx.wenku8.tts.TtsDiagnosticReason
import org.mewx.wenku8.tts.TtsEngineDescriptor
import org.mewx.wenku8.tts.TtsGuidanceAction
import org.mewx.wenku8.tts.TtsInitResult
import org.mewx.wenku8.tts.TtsPlaybackConfig
import org.mewx.wenku8.tts.TtsVoiceDescriptor
import org.mewx.wenku8.tts.TtsVoiceFormatter
import org.mewx.wenku8.ui.reader.SpeechEngineAdapter
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class TtsSettingsScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun profileScreen_listsTtsUnderReadingPreferencesAndOpensTtsSubscreen() {
        var openedSection: ProfileSettingsSection? = null
        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                ProfileAndStudioScreen(
                    preferences = Wenku8UserPreferences(),
                    onThemeModeSelected = {},
                    onDefaultHomeSelected = {},
                    onOpenSettingsSection = { openedSection = it },
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.ENTRY_TTS).assertIsDisplayed().performClick()
        assertEquals(ProfileSettingsSection.TTS_SETTINGS, openedSection)
    }

    @Test
    fun ttsSettings_supportsLiveLightNovelPreviewCustomTextAndPerVoiceAudition() {
        val sampleEngines = listOf(
            KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE, isSystemDefault = true),
            KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE),
        )
        val sampleVoices = listOf(
            TtsVoiceFormatter.describeVoiceFields("cmn-cn-x-ccc-local", "zh-CN", isNetworkRequired = false, qualityScore = 400),
            TtsVoiceFormatter.describeVoiceFields("cmn-tw-x-ctc-local", "zh-TW", isNetworkRequired = false, qualityScore = 400),
        )
        val fakeAdapter = SettingsFakeSpeechAdapter(
            initResult = TtsInitResult(
                success = true,
                activeEnginePackage = KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE,
                activeEngineLabel = "Google 语音识别与合成",
                activeVoiceName = "cmn-cn-x-ccc-local",
                availableVoices = sampleVoices,
            ),
        )
        var prefs by mutableStateOf(Wenku8UserPreferences())

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                TtsSettingsBody(
                    preferences = prefs,
                    horizontalPadding = 16.dp,
                    isPlayStoreChannel = false,
                    ttsPreferencesActions = TtsPreferencesActions(
                        onPreferredEngineChanged = { pkg ->
                            prefs = prefs.copy(ttsPreferredEnginePackage = pkg, ttsPreferredVoiceName = "")
                        },
                        onPreferredVoiceChanged = { voice ->
                            prefs = prefs.copy(ttsPreferredVoiceName = voice)
                        },
                        onAutoFallbackChanged = { enabled ->
                            prefs = prefs.copy(ttsAutoFallbackEnabled = enabled)
                        },
                        onDefaultSleepTimerChanged = { min ->
                            prefs = prefs.copy(ttsDefaultSleepTimerMinutes = min)
                        },
                    ),
                    speechEngineAdapterFactory = { fakeAdapter },
                    installedEnginesProvider = { sampleEngines },
                    manufacturerOverride = "Xiaomi",
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Default light-novel preview play/stop works!
        composeTestRule.onNodeWithTag(TtsSettingsTestTags.PREVIEW_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag(TtsSettingsTestTags.PREVIEW_PLAY_STOP_BUTTON).performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, fakeAdapter.spokenCalls.size)
        assertTrue(fakeAdapter.spokenCalls.first().first.contains("轻小说"))

        // Stop preview
        composeTestRule.onNodeWithTag(TtsSettingsTestTags.PREVIEW_PLAY_STOP_BUTTON).performClick()
        composeTestRule.waitForIdle()
        assertTrue(fakeAdapter.stopCount >= 1)

        // 2. Edit custom preview text and audition it!
        composeTestRule.onNodeWithTag(TtsSettingsTestTags.PREVIEW_EDIT_TEXT_TOGGLE).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(TtsSettingsTestTags.PREVIEW_CUSTOM_TEXT_FIELD).performTextClearance()
        composeTestRule.onNodeWithTag(TtsSettingsTestTags.PREVIEW_CUSTOM_TEXT_FIELD).performTextInput("测试多音字一行重来")
        composeTestRule.onNodeWithTag(TtsSettingsTestTags.PREVIEW_PLAY_STOP_BUTTON).performClick()
        composeTestRule.waitForIdle()
        assertEquals("测试多音字一行重来", fakeAdapter.spokenCalls.last().first)

        // 3. Per-voice inline audition passes the specific voice override!
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
            .performScrollToNode(hasTestTag("${TtsSettingsTestTags.VOICE_PREVIEW_PREFIX}cmn-tw-x-ctc-local"))
        composeTestRule.onNodeWithTag("${TtsSettingsTestTags.VOICE_PREVIEW_PREFIX}cmn-tw-x-ctc-local").performClick()
        composeTestRule.waitForIdle()
        assertEquals("cmn-tw-x-ctc-local", fakeAdapter.spokenCalls.last().second)

        // 4. Switching engine updates preferences cleanly.
        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
            .performScrollToNode(hasTestTag("${TtsSettingsTestTags.ENGINE_ROW_PREFIX}${KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE}"))
        composeTestRule.onNodeWithTag("${TtsSettingsTestTags.ENGINE_ROW_PREFIX}${KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE}")
            .performClick()
        composeTestRule.waitForIdle()
        assertEquals(KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE, prefs.ttsPreferredEnginePackage)
    }

    @Test
    fun ttsSettings_playStoreChannelGuidesToGoogleSpeechRecognitionWithoutSideloadLinks() {
        val unavailableAdapter = SettingsFakeSpeechAdapter(
            initResult = TtsInitResult(
                success = false,
                diagnosticReason = TtsDiagnosticReason.NO_ENGINE_INSTALLED,
                diagnosticMessage = "未检测到支持中文的语音朗读引擎",
            ),
        )
        val clickedActions = ArrayList<TtsGuidanceAction>()

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                TtsSettingsBody(
                    preferences = Wenku8UserPreferences(),
                    horizontalPadding = 16.dp,
                    isPlayStoreChannel = true,
                    ttsPreferencesActions = TtsPreferencesActions(),
                    speechEngineAdapterFactory = { unavailableAdapter },
                    installedEnginesProvider = { emptyList() },
                    guidanceActionHandler = { _, action ->
                        clickedActions.add(action)
                        true
                    },
                    manufacturerOverride = "Xiaomi",
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
            .performScrollToNode(hasTestTag(TtsSettingsTestTags.ONBOARDING_CARD))
        composeTestRule.onNodeWithTag(TtsSettingsTestTags.ONBOARDING_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${TtsSettingsTestTags.ONBOARDING_ACTION_PREFIX}play_install_google_tts")
            .performClick()
        assertEquals(
            TtsGuidanceAction.OpenPlayStoreApp(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE),
            clickedActions.single(),
        )
        // Must NOT show external GitHub/sideload Sherpa card in Play Store flavor!
        assertEquals(
            0,
            composeTestRule
                .onAllNodesWithTag("${TtsSettingsTestTags.ONBOARDING_ITEM_PREFIX}default_sherpa_tts")
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun ttsSettings_defaultChannelGuidesToFreeOpenSourceOfflineEnginesAndOemHints() {
        val unavailableAdapter = SettingsFakeSpeechAdapter(
            initResult = TtsInitResult(
                success = false,
                diagnosticReason = TtsDiagnosticReason.NO_ENGINE_INSTALLED,
                diagnosticMessage = "未检测到支持中文的语音朗读引擎",
            ),
        )
        val clickedActions = ArrayList<TtsGuidanceAction>()

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.E_INK, einkModeOverride = true) {
                TtsSettingsBody(
                    preferences = Wenku8UserPreferences(einkModeEnabled = true),
                    horizontalPadding = 16.dp,
                    isPlayStoreChannel = false,
                    ttsPreferencesActions = TtsPreferencesActions(),
                    speechEngineAdapterFactory = { unavailableAdapter },
                    installedEnginesProvider = { emptyList() },
                    guidanceActionHandler = { _, action ->
                        clickedActions.add(action)
                        true
                    },
                    manufacturerOverride = "HUAWEI",
                )
            }
        }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
            .performScrollToNode(hasTestTag(TtsSettingsTestTags.ONBOARDING_CARD))
        composeTestRule.onNodeWithTag(TtsSettingsTestTags.ONBOARDING_CARD).assertIsDisplayed()
        composeTestRule.onNodeWithTag("${TtsSettingsTestTags.ONBOARDING_ACTION_PREFIX}default_sherpa_tts")
            .performClick()
        assertTrue(clickedActions.single() is TtsGuidanceAction.OpenExternalFreeUrl)

        composeTestRule.onNodeWithTag(ProfileStudioTestTags.SUBSCREEN_LIST)
            .performScrollToNode(hasTestTag(TtsSettingsTestTags.OEM_KEEPALIVE_CARD))
        composeTestRule.onNodeWithText("华为 / HarmonyOS 防断播提示").assertIsDisplayed()
    }
}

private class SettingsFakeSpeechAdapter(
    private val initResult: TtsInitResult,
) : SpeechEngineAdapter {
    val spokenCalls = ArrayList<Pair<String, String?>>()
    var stopCount = 0

    override fun initialize(
        appLanguage: Wenku8API.AppLanguage,
        onInitialized: (Boolean) -> Unit,
        onUtteranceStarted: (String) -> Unit,
        onUtteranceDone: (String) -> Unit,
        onUtteranceError: (String) -> Unit,
    ) {
        onInitialized(initResult.success)
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
        onInitialized(initResult)
    }

    override fun setSpeechRate(rate: Float) = Unit

    override fun speakFlush(text: String, utteranceId: String): Boolean {
        spokenCalls.add(text to null)
        return true
    }

    override fun speakWithVoiceOverride(
        text: String,
        utteranceId: String,
        voiceNameOverride: String?,
    ): Boolean {
        spokenCalls.add(text to voiceNameOverride)
        return true
    }

    override fun stop() {
        stopCount++
    }

    override fun shutdown() = Unit
}
