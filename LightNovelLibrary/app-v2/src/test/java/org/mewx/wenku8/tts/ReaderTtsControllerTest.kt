package org.mewx.wenku8.tts

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.preferences.Wenku8UserPreferences
import org.mewx.wenku8.reader.engine.ChapterAst
import org.mewx.wenku8.reader.engine.WenkuChapterParser
import org.mewx.wenku8.ui.reader.ReaderTtsController
import org.mewx.wenku8.ui.reader.SpeechEngineAdapter

@OptIn(ExperimentalCoroutinesApi::class)
class ReaderTtsControllerTest {

    private fun sampleChapter(cid: Int = 101): ChapterAst = WenkuChapterParser.parse(
        rawChapterText = "第一章 晨风\n\n　　清晨的微风掠过窗台。\n\n　　少年合上了手中的轻小说。",
        aid = 1,
        vid = 10,
        cid = cid,
        volumeTitle = "第一卷",
        chapterTitle = "第一章 晨风",
    )

    @Test
    fun circuitBreaker_retriesOnceThenHotSwitchesToFallbackEngineAtSameUtterance() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val adapter = MultiEngineFakeSpeechAdapter(
            engines = listOf("com.broken.oem.tts", KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE),
        )
        val controller = ReaderTtsController(
            engineAdapter = adapter,
            scope = scope,
            initialConfig = TtsPlaybackConfig(
                preferredEnginePackage = "com.broken.oem.tts",
                fallbackEnginePackage = KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE,
                autoFallbackEnabled = true,
            ),
        )

        controller.startChapter(sampleChapter(), startFromBlockIndex = 0, appLanguage = Wenku8API.AppLanguage.SC)
        scope.runCurrent()

        assertEquals("com.broken.oem.tts", controller.uiState.value.activeEnginePackage)
        assertEquals(0, controller.uiState.value.activeUtteranceIndex)
        assertEquals(1, adapter.spokenHistory.size)

        // 1st utterance error -> retries once on the SAME engine & utterance.
        adapter.triggerErrorOnActive()
        scope.runCurrent()
        assertEquals("com.broken.oem.tts", controller.uiState.value.activeEnginePackage)
        assertEquals(2, adapter.spokenHistory.size)
        assertTrue(controller.uiState.value.isSpeaking)

        // 2nd consecutive error -> trips circuit breaker, excludes broken engine, hot-switches to Google TTS!
        adapter.triggerErrorOnActive()
        scope.runCurrent()
        assertEquals(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE, controller.uiState.value.activeEnginePackage)
        assertTrue(controller.uiState.value.isFallbackActive)
        assertNotNull(controller.uiState.value.fallbackNoticeMessage)
        assertTrue(controller.uiState.value.isSpeaking)
        // Resumes on the exact same utterance index 0 without losing progress!
        assertEquals(0, controller.uiState.value.activeUtteranceIndex)
        assertEquals(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE, adapter.spokenHistory.last().first)

        // Completing utterance advances cleanly and clears consecutive error count.
        adapter.completeActive()
        scope.runCurrent()
        assertEquals(1, controller.uiState.value.activeUtteranceIndex)
        controller.close()
    }

    @Test
    fun sleepTimer_endOfChapterStopsWithoutAdvancingToNextChapter() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val adapter = MultiEngineFakeSpeechAdapter(listOf(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE))
        var chapterFinishedCount = 0
        val controller = ReaderTtsController(
            engineAdapter = adapter,
            scope = scope,
            initialConfig = TtsPlaybackConfig(
                defaultSleepTimerMinutes = Wenku8UserPreferences.TTS_SLEEP_TIMER_END_OF_CHAPTER,
            ),
            onChapterFinished = { chapterFinishedCount++ },
        )

        controller.startChapter(sampleChapter(), startFromBlockIndex = 0, appLanguage = Wenku8API.AppLanguage.SC)
        scope.runCurrent()
        assertTrue(controller.uiState.value.stopAfterCurrentChapter)

        // Finish all 3 utterances in the chapter.
        repeat(3) {
            adapter.completeActive()
            scope.runCurrent()
        }

        assertEquals(0, chapterFinishedCount)
        assertFalse(controller.uiState.value.isSpeaking)
        assertFalse(controller.uiState.value.stopAfterCurrentChapter)
        controller.close()
    }

    @Test
    fun sleepTimer_minuteCountdownPausesPlaybackAtZero() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val adapter = MultiEngineFakeSpeechAdapter(listOf(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE))
        val controller = ReaderTtsController(
            engineAdapter = adapter,
            scope = scope,
        )

        controller.startChapter(sampleChapter(), startFromBlockIndex = 0, appLanguage = Wenku8API.AppLanguage.SC)
        scope.runCurrent()
        controller.setSleepTimer(1) // 1 minute = 60 seconds
        scope.runCurrent()

        assertTrue(controller.uiState.value.isSpeaking)
        assertEquals(60, controller.uiState.value.sleepTimerRemainingSec)

        scope.advanceTimeBy(30_000L)
        scope.runCurrent()
        assertTrue(controller.uiState.value.isSpeaking)
        assertEquals(30, controller.uiState.value.sleepTimerRemainingSec)

        scope.advanceTimeBy(31_000L)
        scope.runCurrent()
        assertFalse(controller.uiState.value.isSpeaking)
        assertEquals(0, controller.uiState.value.sleepTimerRemainingSec)
        controller.close()
    }

    @Test
    fun audioFocus_pausesOnTransientLossAndResumesOnGain_andStaysPausedOnPermanentLoss() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val adapter = MultiEngineFakeSpeechAdapter(listOf(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE))
        val focus = FakeAudioFocusController()
        val controller = ReaderTtsController(
            engineAdapter = adapter,
            scope = scope,
            audioFocusController = focus,
            initialConfig = TtsPlaybackConfig(pauseOnAudioFocusLoss = true),
        )

        controller.startChapter(sampleChapter(), startFromBlockIndex = 0, appLanguage = Wenku8API.AppLanguage.SC)
        scope.runCurrent()
        assertTrue(focus.hasFocus)
        assertTrue(controller.uiState.value.isSpeaking)

        // Incoming phone call / voice message -> transient loss pauses playback.
        focus.emit(TtsAudioFocusEvent.LOSS_TRANSIENT)
        scope.runCurrent()
        assertFalse(controller.uiState.value.isSpeaking)

        // Call ends -> gain automatically resumes playback!
        focus.emit(TtsAudioFocusEvent.GAIN)
        scope.runCurrent()
        assertTrue(controller.uiState.value.isSpeaking)

        // Permanent loss (user starts music player) -> pauses and does NOT auto-resume on later gain.
        focus.emit(TtsAudioFocusEvent.LOSS)
        scope.runCurrent()
        assertFalse(controller.uiState.value.isSpeaking)
        focus.emit(TtsAudioFocusEvent.GAIN)
        scope.runCurrent()
        assertFalse(controller.uiState.value.isSpeaking)

        controller.stopAndDismiss()
        assertFalse(focus.hasFocus)
        controller.close()
    }

    @Test
    fun updateConfig_appliesPitchAndSpeechRateAndRebindsWhenEngineChanges() {
        val dispatcher = StandardTestDispatcher()
        val scope = TestScope(dispatcher)
        val adapter = MultiEngineFakeSpeechAdapter(
            engines = listOf(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE, KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE),
        )
        val controller = ReaderTtsController(
            engineAdapter = adapter,
            scope = scope,
            initialConfig = TtsPlaybackConfig(
                preferredEnginePackage = KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE,
                speechRate = 1.25f,
                pitch = 1.1f,
            ),
        )

        controller.startChapter(sampleChapter(), startFromBlockIndex = 0, appLanguage = Wenku8API.AppLanguage.SC)
        scope.runCurrent()
        assertEquals(KnownTtsEngineCatalog.GOOGLE_TTS_PACKAGE, controller.uiState.value.activeEnginePackage)
        assertEquals(1.25f, adapter.lastRate, 0.001f)
        assertEquals(1.1f, adapter.lastPitch, 0.001f)

        // Switching engine mid-playback cleanly rebinds and continues speaking on the new engine!
        controller.updateConfig(
            newConfig = TtsPlaybackConfig(
                preferredEnginePackage = KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE,
                speechRate = 1.5f,
                pitch = 0.9f,
            ),
            appLanguage = Wenku8API.AppLanguage.SC,
        )
        scope.runCurrent()
        assertEquals(KnownTtsEngineCatalog.SHERPA_TTS_PACKAGE, controller.uiState.value.activeEnginePackage)
        assertEquals(1.5f, adapter.lastRate, 0.001f)
        assertEquals(0.9f, adapter.lastPitch, 0.001f)
        assertTrue(controller.uiState.value.isSpeaking)
        controller.close()
    }
}

private class FakeAudioFocusController : TtsAudioFocusController {
    var hasFocus: Boolean = false
    private var listener: ((TtsAudioFocusEvent) -> Unit)? = null

    override fun requestFocus(onFocusChanged: (TtsAudioFocusEvent) -> Unit): Boolean {
        hasFocus = true
        listener = onFocusChanged
        return true
    }

    override fun abandonFocus() {
        hasFocus = false
        listener = null
    }

    fun emit(event: TtsAudioFocusEvent) {
        listener?.invoke(event)
    }
}

private class MultiEngineFakeSpeechAdapter(
    private val engines: List<String>,
) : SpeechEngineAdapter {
    var activePkg: String = ""
    var activeUtteranceId: String? = null
    var lastRate: Float = 1.0f
    var lastPitch: Float = 1.0f
    val spokenHistory = ArrayList<Pair<String, String>>()

    private var onDone: ((String) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

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
            onInitialized = { onInitialized(it.success) },
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
        onDone = onUtteranceDone
        onError = onUtteranceError
        val candidates = engines.filterNot { it in excludedEnginePackages }
        val preferred = config.preferredEnginePackage.trim()
        val chosen = when {
            preferred.isNotEmpty() && preferred in candidates -> preferred
            config.autoFallbackEnabled -> candidates.firstOrNull()
            else -> null
        }
        if (chosen == null) {
            activePkg = ""
            onInitialized(
                TtsInitResult(
                    success = false,
                    diagnosticReason = TtsDiagnosticReason.NO_ENGINE_INSTALLED,
                    diagnosticMessage = "无可用中文引擎",
                ),
            )
            return
        }
        activePkg = chosen
        lastRate = config.speechRate
        lastPitch = config.pitch
        val isFallback = preferred.isNotEmpty() && chosen != preferred
        val label = KnownTtsEngineCatalog.describe(chosen).displayName
        onInitialized(
            TtsInitResult(
                success = true,
                activeEnginePackage = chosen,
                activeEngineLabel = label,
                isFallbackActive = isFallback,
                fallbackNoticeMessage = if (isFallback) "已自动回退至 $label" else null,
            ),
        )
    }

    override fun setSpeechRate(rate: Float) {
        lastRate = rate
    }

    override fun setPitch(pitch: Float) {
        lastPitch = pitch
    }

    override fun activeEnginePackage(): String = activePkg

    override fun speakFlush(text: String, utteranceId: String): Boolean {
        activeUtteranceId = utteranceId
        spokenHistory.add(activePkg to text)
        return true
    }

    fun completeActive() {
        val id = activeUtteranceId ?: return
        onDone?.invoke(id)
    }

    fun triggerErrorOnActive() {
        val id = activeUtteranceId ?: return
        onError?.invoke(id)
    }

    override fun stop() {
        activeUtteranceId = null
    }

    override fun shutdown() {
        activeUtteranceId = null
        activePkg = ""
    }
}
