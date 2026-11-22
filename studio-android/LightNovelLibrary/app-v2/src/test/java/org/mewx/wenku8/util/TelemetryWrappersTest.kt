package org.mewx.wenku8.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TelemetryWrappersTest {

    private class FakeCrashlyticsBackend : CrashReporter.Backend {
        val logs = mutableListOf<String>()
        val exceptions = mutableListOf<Throwable>()
        val stringKeys = mutableMapOf<String, String>()
        val intKeys = mutableMapOf<String, Int>()
        val boolKeys = mutableMapOf<String, Boolean>()

        override fun log(message: String) {
            logs.add(message)
        }

        override fun recordException(throwable: Throwable) {
            exceptions.add(throwable)
        }

        override fun setCustomKey(key: String, value: String) {
            stringKeys[key] = value
        }

        override fun setCustomKey(key: String, value: Int) {
            intKeys[key] = value
        }

        override fun setCustomKey(key: String, value: Boolean) {
            boolKeys[key] = value
        }
    }

    private class FakeAnalyticsBackend : AnalyticsTracker.Backend {
        data class RecordedEvent(val name: String, val params: Map<String, Any>)

        val events = mutableListOf<RecordedEvent>()
        val userProperties = mutableMapOf<String, String?>()

        override fun logEvent(eventName: String, params: Map<String, Any>) {
            events.add(RecordedEvent(eventName, params))
        }

        override fun setUserProperty(name: String, value: String?) {
            userProperties[name] = value
        }
    }

    private lateinit var crashBackend: FakeCrashlyticsBackend
    private lateinit var analyticsBackend: FakeAnalyticsBackend

    @Before
    fun setUp() {
        GoogleServicesHelper.resetForTests()
        CrashReporter.resetForTests()
        AnalyticsTracker.resetForTests()
        crashBackend = FakeCrashlyticsBackend()
        analyticsBackend = FakeAnalyticsBackend()
    }

    @After
    fun tearDown() {
        GoogleServicesHelper.resetForTests()
        CrashReporter.resetForTests()
        AnalyticsTracker.resetForTests()
    }

    @Test
    fun crashReporter_recordsBreadcrumbsKeysAndNonFatalExceptions() {
        CrashReporter.setTestBackend(crashBackend)

        CrashReporter.setScreen("NovelDetailScreen", "enter")
        CrashReporter.setKey(CrashReporter.Keys.NOVEL_AID, 1306)
        CrashReporter.setKey(CrashReporter.Keys.LANGUAGE, "SC")
        CrashReporter.setLoggedIn(true)
        val boom = IllegalStateException("malformed xml")
        CrashReporter.recordException("WenkuXmlParsers.parseNovelFullMeta", boom)

        assertEquals("NovelDetailScreen", crashBackend.stringKeys[CrashReporter.Keys.SCREEN])
        assertEquals("SC", crashBackend.stringKeys[CrashReporter.Keys.LANGUAGE])
        assertEquals(1306, crashBackend.intKeys[CrashReporter.Keys.NOVEL_AID])
        assertEquals(true, crashBackend.boolKeys[CrashReporter.Keys.LOGGED_IN])
        assertEquals(
            listOf("NovelDetailScreen#enter", "WenkuXmlParsers.parseNovelFullMeta"),
            crashBackend.logs,
        )
        assertEquals(listOf(boom), crashBackend.exceptions)
    }

    @Test
    fun crashReporter_degradesSafelyWhenFirebaseNotInitialized() {
        // Without a test backend and without FirebaseApp initialized, calls must never throw.
        CrashReporter.setScreen("LatestScreen", "enter")
        CrashReporter.setKey(CrashReporter.Keys.NOVEL_AID, 42)
        CrashReporter.setKey(CrashReporter.Keys.LANGUAGE, null)
        CrashReporter.setLoggedIn(false)
        CrashReporter.recordException("Test.noFirebase", RuntimeException("safe"))
    }

    @Test
    fun googleServicesHelper_cachesResultAndSetsCrashReporterGmsKey() {
        CrashReporter.setTestBackend(crashBackend)
        val context = ApplicationProvider.getApplicationContext<Context>()
        var checkInvocations = 0
        GoogleServicesHelper.setTestAvailabilityOverride {
            checkInvocations++
            true
        }

        assertTrue(GoogleServicesHelper.isGmsAvailable(context))
        assertTrue(GoogleServicesHelper.isGmsAvailable(context))
        assertEquals(1, checkInvocations)
        assertEquals(true, crashBackend.boolKeys[CrashReporter.Keys.GMS_AVAILABLE])
    }

    @Test
    fun analyticsTracker_logsV1CompatibleAndV2EventsWithGa4Sanitization() {
        AnalyticsTracker.setTestBackend(analyticsBackend)

        val longTitle = "A".repeat(150)
        AnalyticsTracker.logScreenView("BookshelfScreen")
        AnalyticsTracker.logViewNovel(aid = 1973, title = longTitle, from = "latest")
        AnalyticsTracker.logSearch(searchTerm = "  刀剑神域  ", mode = "title")
        AnalyticsTracker.logDailyCheckIn(effectiveClick = true, outcome = "success")
        AnalyticsTracker.logLogin(method = "email", success = true)
        AnalyticsTracker.logShareNovel(aid = 1973)
        AnalyticsTracker.logReaderOpen(
            aid = 1973,
            cid = 88001,
            pageTurnMode = "curl",
            columnMode = "dual",
            startWithTts = false,
            source = "detail",
        )
        AnalyticsTracker.logReaderSessionEnd(aid = 1973, durationSec = 720L)
        AnalyticsTracker.logLegacyMigrationPathSelection(validPath = true)
        AnalyticsTracker.syncUserPreferenceProperties(
            appLanguage = "TC",
            themeMode = "dark",
            readerTurnMode = "curl",
            readerColumnMode = "dual",
            einkModeEnabled = false,
            distributionChannel = "playstore",
        )

        assertEquals(9, analyticsBackend.events.size)

        val screenEvent = analyticsBackend.events[0]
        assertEquals(AnalyticsTracker.Events.SCREEN_VIEW, screenEvent.name)
        assertEquals("BookshelfScreen", screenEvent.params[AnalyticsTracker.Params.SCREEN_NAME])
        assertEquals("BookshelfScreen", screenEvent.params[AnalyticsTracker.Params.SCREEN_CLASS])

        val viewItem = analyticsBackend.events[1]
        assertEquals(AnalyticsTracker.Events.VIEW_ITEM, viewItem.name)
        assertEquals("1973", viewItem.params[AnalyticsTracker.Params.ITEM_ID])
        assertEquals(
            AnalyticsTracker.MAX_PARAM_VALUE_LENGTH,
            (viewItem.params[AnalyticsTracker.Params.ITEM_NAME] as String).length,
        )
        assertEquals("latest", viewItem.params[AnalyticsTracker.Params.FROM])

        val searchEvent = analyticsBackend.events[2]
        assertEquals(AnalyticsTracker.Events.SEARCH, searchEvent.name)
        assertEquals("刀剑神域", searchEvent.params[AnalyticsTracker.Params.SEARCH_TERM])
        assertEquals("title", searchEvent.params[AnalyticsTracker.Params.SEARCH_MODE])

        val checkInEvent = analyticsBackend.events[3]
        assertEquals(AnalyticsTracker.Events.DAILY_CHECK_IN, checkInEvent.name)
        assertEquals("true", checkInEvent.params[AnalyticsTracker.Params.EFFECTIVE_CLICK])
        assertEquals("success", checkInEvent.params[AnalyticsTracker.Params.OUTCOME])

        val loginEvent = analyticsBackend.events[4]
        assertEquals(AnalyticsTracker.Events.LOGIN, loginEvent.name)
        assertEquals("email", loginEvent.params[AnalyticsTracker.Params.METHOD])
        assertEquals("true", loginEvent.params[AnalyticsTracker.Params.SUCCESS])

        val shareEvent = analyticsBackend.events[5]
        assertEquals(AnalyticsTracker.Events.SHARE, shareEvent.name)
        assertEquals("novel", shareEvent.params[AnalyticsTracker.Params.CONTENT_TYPE])
        assertEquals("1973", shareEvent.params[AnalyticsTracker.Params.ITEM_ID])

        val readerOpen = analyticsBackend.events[6]
        assertEquals(AnalyticsTracker.Events.READER_OPEN, readerOpen.name)
        assertEquals("curl", readerOpen.params[AnalyticsTracker.Params.PAGE_TURN_MODE])
        assertEquals("dual", readerOpen.params[AnalyticsTracker.Params.COLUMN_MODE])

        val readerEnd = analyticsBackend.events[7]
        assertEquals(AnalyticsTracker.Events.READER_SESSION_END, readerEnd.name)
        assertEquals(720L, readerEnd.params[AnalyticsTracker.Params.DURATION_SEC])

        val migrationPath = analyticsBackend.events[8]
        assertEquals(AnalyticsTracker.Events.SAVE_MIGRATION_PATH_SELECTION, migrationPath.name)
        assertEquals("true", migrationPath.params[AnalyticsTracker.Params.VALID_PATH])

        assertEquals("TC", analyticsBackend.userProperties[AnalyticsTracker.UserProperties.APP_LANGUAGE])
        assertEquals("dark", analyticsBackend.userProperties[AnalyticsTracker.UserProperties.THEME_MODE])
        assertEquals("playstore", analyticsBackend.userProperties[AnalyticsTracker.UserProperties.DISTRIBUTION_CHANNEL])
    }

    @Test
    fun analyticsTracker_dropsInvalidIdsBlankInputsAndNoOpsWithoutGms() {
        AnalyticsTracker.setTestBackend(analyticsBackend)

        AnalyticsTracker.logScreenView("   ")
        AnalyticsTracker.logViewNovel(aid = 0, title = "Invalid", from = "latest")
        AnalyticsTracker.logSearch(searchTerm = "   ", mode = "title")
        AnalyticsTracker.logReaderSessionEnd(aid = 10, durationSec = 0L)
        AnalyticsTracker.logBookshelfRemove(count = 0, source = "bookshelf")
        assertTrue(analyticsBackend.events.isEmpty())

        // Without backend and with GMS disabled, initialize + log must safely no-op.
        AnalyticsTracker.resetForTests()
        val context = ApplicationProvider.getApplicationContext<Context>()
        GoogleServicesHelper.setTestAvailabilityOverride { false }
        assertFalse(GoogleServicesHelper.isGmsAvailable(context))
        AnalyticsTracker.initialize(context)
        AnalyticsTracker.logScreenView("LatestScreen")
        AnalyticsTracker.setUserProperty(AnalyticsTracker.UserProperties.THEME_MODE, "light")
    }
}
