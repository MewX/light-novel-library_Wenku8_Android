package org.mewx.wenku8.tts

import android.content.Context
import android.content.Intent
import android.speech.tts.TextToSpeech
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class TtsModelsAndPreferencesTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun knownEngineCatalog_classifiesOfficialOpenSourceOemAndUnsupportedEngines() {
        val plugin = KnownTtsEngineCatalog.describe(
            packageName = KnownTtsEngineCatalog.WENKU8_PLUGIN_PACKAGE,
            supportsPluginV1 = true,
        )
        val google = KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE)
        val sherpa = KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE)
        val voxSherpa = KnownTtsEngineCatalog.describe("com.codebysonu.voxsherpa")
        val ttsServer = KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.TTS_SERVER_PACKAGE)
        val multiTts = KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.MULTI_TTS_PACKAGE)
        val xiaomi = KnownTtsEngineCatalog.describe("com.xiaomi.mibrain.speech")
        val pico = KnownTtsEngineCatalog.describe(KnownTtsEngineCatalog.PICO_TTS_PACKAGE)

        assertEquals(TtsEngineCategory.OFFICIAL_PLUGIN, plugin.category)
        assertEquals(TtsEngineCategory.OFFICIAL_FREE, google.category)
        assertEquals(TtsEngineCategory.OPEN_SOURCE_OFFLINE, sherpa.category)
        assertEquals(TtsEngineCategory.OPEN_SOURCE_OFFLINE, voxSherpa.category)
        assertEquals(TtsEngineCategory.OPEN_SOURCE_BRIDGE, ttsServer.category)
        assertEquals(TtsEngineCategory.OPEN_SOURCE_BRIDGE, multiTts.category)
        assertEquals(TtsEngineCategory.SYSTEM_OEM, xiaomi.category)
        assertEquals(TtsEngineCategory.UNSUPPORTED_CHINESE, pico.category)
        assertTrue(pico.isKnownUnsupportedChinese)

        assertTrue(plugin.priorityRank < google.priorityRank)
        assertTrue(google.priorityRank < sherpa.priorityRank)
        assertTrue(sherpa.priorityRank < xiaomi.priorityRank)
        assertTrue(xiaomi.priorityRank < pico.priorityRank)

        // Xiaomi rate clamping protects against OEM extreme-rate glitches.
        assertEquals(0.6f, KnownTtsEngineCatalog.clampSpeechRateForEngine("com.xiaomi.mibrain.speech", 0.5f), 0.001f)
        assertEquals(2.0f, KnownTtsEngineCatalog.clampSpeechRateForEngine("com.xiaomi.mibrain.speech", 2.5f), 0.001f)
        assertEquals(2.5f, KnownTtsEngineCatalog.clampSpeechRateForEngine(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE, 2.5f), 0.001f)
    }

    @Test
    fun sampleText_adaptsToSimplifiedAndTraditionalChinese() {
        val sc = KnownTtsEngineCatalog.defaultLightNovelSampleText(Wenku8API.AppLanguage.SC)
        val tc = KnownTtsEngineCatalog.defaultLightNovelSampleText(Wenku8API.AppLanguage.TC)
        assertTrue(sc.contains("轻小说"))
        assertTrue(tc.contains("輕小說"))
    }

    @Test
    fun preferencesRepository_persistsClampsAndResetsTtsSettings() = runBlocking {
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        try {
            val file = File(tempFolder.newFolder("tts_prefs"), "settings.preferences_pb")
            val repo = UserPreferencesRepository(
                PreferenceDataStoreFactory.create(scope = scope) { file },
            )

            // Defaults match specification.
            val initial = repo.preferencesFlow.first()
            assertEquals(TtsBackendType.SYSTEM, initial.ttsBackendType)
            assertEquals("", initial.ttsPreferredEnginePackage)
            assertTrue(initial.ttsAutoFallbackEnabled)
            assertEquals(1.0f, initial.ttsSpeechRate, 0.001f)
            assertEquals(1.0f, initial.ttsPitch, 0.001f)
            assertEquals(0, initial.ttsDefaultSleepTimerMinutes)
            assertTrue(initial.ttsFollowReadingPage)
            assertTrue(initial.ttsPauseOnAudioFocusLoss)

            repo.setTtsPreferredEnginePackage(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE)
            repo.setTtsPreferredVoiceName("cmn-cn-x-ccc-local")
            repo.setTtsFallbackEnginePackage(KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE)
            repo.setTtsAutoFallbackEnabled(false)
            repo.setTtsSpeechRate(9.0f) // clamped to 2.5f
            repo.setTtsPitch(0.1f) // clamped to 0.5f
            repo.setTtsDefaultSleepTimerMinutes(Wenku8UserPreferences.TTS_SLEEP_TIMER_END_OF_CHAPTER)
            repo.setTtsFollowReadingPage(false)
            repo.setTtsPauseOnAudioFocusLoss(false)

            val updated = repo.preferencesFlow.first()
            assertEquals(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE, updated.ttsPreferredEnginePackage)
            assertEquals("cmn-cn-x-ccc-local", updated.ttsPreferredVoiceName)
            assertEquals(KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE, updated.ttsFallbackEnginePackage)
            assertFalse(updated.ttsAutoFallbackEnabled)
            assertEquals(Wenku8UserPreferences.MAX_TTS_SPEECH_RATE, updated.ttsSpeechRate, 0.001f)
            assertEquals(Wenku8UserPreferences.MIN_TTS_PITCH, updated.ttsPitch, 0.001f)
            assertEquals(
                Wenku8UserPreferences.TTS_SLEEP_TIMER_END_OF_CHAPTER,
                updated.ttsDefaultSleepTimerMinutes,
            )
            assertFalse(updated.ttsFollowReadingPage)
            assertFalse(updated.ttsPauseOnAudioFocusLoss)

            // Switching engine clears stale engine-specific voice override.
            repo.setTtsPreferredEnginePackage(KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE)
            val afterEngineSwitch = repo.preferencesFlow.first()
            assertEquals(KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE, afterEngineSwitch.ttsPreferredEnginePackage)
            assertEquals("", afterEngineSwitch.ttsPreferredVoiceName)

            // Reset restores all defaults cleanly.
            repo.resetTtsDefaults()
            val reset = repo.preferencesFlow.first()
            assertEquals("", reset.ttsPreferredEnginePackage)
            assertEquals("", reset.ttsFallbackEnginePackage)
            assertTrue(reset.ttsAutoFallbackEnabled)
            assertEquals(1.0f, reset.ttsSpeechRate, 0.001f)
            assertEquals(1.0f, reset.ttsPitch, 0.001f)
            assertEquals(0, reset.ttsDefaultSleepTimerMinutes)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun mergedManifest_declaresAndroid11TtsServiceQueries() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val pkgInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        assertNotNull(pkgInfo)
        // Verify source manifest declares all 4 required queries actions.
        val manifestFile = File("src/main/AndroidManifest.xml")
        assertTrue("Expected src/main/AndroidManifest.xml to exist", manifestFile.isFile)
        val xml = manifestFile.readText()
        assertTrue(xml.contains(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE))
        assertTrue(xml.contains(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA))
        assertTrue(xml.contains(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA))
        assertTrue(xml.contains(KnownTtsEngineCatalog.ACTION_WENKU8_TTS_PLUGIN_V1))
        assertEquals("android.intent.action.TTS_SERVICE", Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE).action)
    }
}
