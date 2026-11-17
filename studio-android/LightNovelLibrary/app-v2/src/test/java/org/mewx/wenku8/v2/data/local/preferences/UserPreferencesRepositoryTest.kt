package org.mewx.wenku8.v2.data.local.preferences

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode

@OptIn(ExperimentalCoroutinesApi::class)
class UserPreferencesRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private fun createRepository(scope: TestScope): UserPreferencesRepository {
        val testFile = temporaryFolder.newFile("test_settings.preferences_pb")
        val dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { testFile },
        )
        return UserPreferencesRepository(dataStore)
    }

    @Test
    fun defaultPreferencesMatchV1ParityAndDefaultLatestHome() = runTest(UnconfinedTestDispatcher()) {
        val repository = createRepository(this)
        val defaults = repository.preferencesFlow.first()

        assertEquals(Wenku8API.AppLanguage.SC, defaults.appLanguage)
        assertEquals(DefaultHomeDestination.LATEST, defaults.defaultHomeDestination)
        assertEquals(Wenku8ThemeMode.CLASSIC_BLUE, defaults.themeMode)
        assertFalse(defaults.einkModeEnabled)
        assertEquals(18, defaults.readerFontSizeSp)
        assertEquals(16, defaults.readerLineDistanceDp)
        assertEquals(20, defaults.readerParagraphDistanceDp)
        assertEquals(8, defaults.readerPageEdgeDistanceDp)
        assertTrue(defaults.volumeKeyPagingEnabled)
        assertTrue(defaults.autoOptimizeConnectionEnabled)
    }

    @Test
    fun legacyV1SettingsMigrationPreservesAllElevenSettingItemsAndClampsBounds() = runTest(UnconfinedTestDispatcher()) {
        val repository = createRepository(this)

        val legacyMap = mapOf(
            "version" to "1",
            "language" to "TC",
            "eink_mode" to "1",
            "menu_bg_id" to "0",
            "menu_bg_path" to "/data/user/0/org.mewx.wenku8/files/custom/banner.png",
            "reader_font_size" to "26",
            "reader_line_distance" to "22",
            "reader_paragraph_distance" to "34",
            "reader_paragraph_edge_distance" to "14",
            "reader_font_path" to "/data/user/0/org.mewx.wenku8/files/custom/LXGWWenKai.ttf",
            "reader_background_path" to "0",
        )

        repository.applyMigratedLegacyV1Settings(
            migratedSettings = legacyMap,
            cachedNoticeText = "文库公告测试",
        )
        repository.setDefaultHomeDestination(DefaultHomeDestination.BOOKSHELF)

        val migrated = repository.preferencesFlow.first()
        assertEquals(Wenku8API.AppLanguage.TC, migrated.appLanguage)
        assertTrue(migrated.einkModeEnabled)
        assertEquals(Wenku8ThemeMode.E_INK, migrated.themeMode)
        assertEquals(0, migrated.menuBackgroundPresetId)
        assertEquals("/data/user/0/org.mewx.wenku8/files/custom/banner.png", migrated.menuBackgroundCustomPath)
        assertEquals(26, migrated.readerFontSizeSp)
        assertEquals(22, migrated.readerLineDistanceDp)
        assertEquals(34, migrated.readerParagraphDistanceDp)
        assertEquals(14, migrated.readerPageEdgeDistanceDp)
        assertEquals("/data/user/0/org.mewx.wenku8/files/custom/LXGWWenKai.ttf", migrated.readerCustomFontPath)
        assertEquals("", migrated.readerCustomBackgroundPath)
        assertEquals("文库公告测试", migrated.cachedNoticeText)
        assertEquals(DefaultHomeDestination.BOOKSHELF, migrated.defaultHomeDestination)
        assertEquals(UserPreferencesRepository.CURRENT_V1_MIGRATION_VERSION, migrated.legacyV1InternalMigrationVersion)

        // Out-of-range typography inputs clamp cleanly to [8..36], [0..32], [0..48], [0..32].
        repository.setReaderTypography(
            fontSizeSp = 99,
            lineDistanceDp = -5,
            paragraphDistanceDp = 999,
            pageEdgeDistanceDp = 64,
        )
        val clamped = repository.preferencesFlow.first()
        assertEquals(36, clamped.readerFontSizeSp)
        assertEquals(0, clamped.readerLineDistanceDp)
        assertEquals(48, clamped.readerParagraphDistanceDp)
        assertEquals(32, clamped.readerPageEdgeDistanceDp)
    }

    @Test
    fun readerPaperPresetAndGlobalThemeModeStaySynchronizedBidirectionally() = runTest(UnconfinedTestDispatcher()) {
        val repository = createRepository(this)

        // Default starts on Classic Blue + Parchment
        val initial = repository.preferencesFlow.first()
        assertEquals(Wenku8ThemeMode.CLASSIC_BLUE, initial.themeMode)
        assertEquals(org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.PARCHMENT, initial.readerPaperPreset)

        // Pick Eye-Care Green day paper + Archival Day global theme
        repository.setReaderPaperPreset(org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.EYE_CARE_GREEN)
        repository.setThemeMode(Wenku8ThemeMode.ARCHIVAL_DAY)
        val dayCustom = repository.preferencesFlow.first()
        assertEquals(Wenku8ThemeMode.ARCHIVAL_DAY, dayCustom.themeMode)
        assertEquals(org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.EYE_CARE_GREEN, dayCustom.readerPaperPreset)
        assertEquals(org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.EYE_CARE_GREEN, dayCustom.lastDayPaperPreset)
        assertEquals(Wenku8ThemeMode.ARCHIVAL_DAY, dayCustom.lastDayThemeMode)

        // Switching to Carbon Night inside Reader updates global themeMode atomically
        repository.setReaderPaperPreset(org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.CARBON_NIGHT)
        val carbonNight = repository.preferencesFlow.first()
        assertEquals(Wenku8ThemeMode.CARBON_NIGHT, carbonNight.themeMode)
        assertEquals(org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.CARBON_NIGHT, carbonNight.readerPaperPreset)

        // Switching to Pure AMOLED inside Reader updates global themeMode to PURE_AMOLED
        repository.setReaderPaperPreset(org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.PURE_AMOLED)
        val pureAmoled = repository.preferencesFlow.first()
        assertEquals(Wenku8ThemeMode.PURE_AMOLED, pureAmoled.themeMode)
        assertEquals(org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.PURE_AMOLED, pureAmoled.readerPaperPreset)

        // Switching back to day paper restores remembered day global theme (ARCHIVAL_DAY)
        repository.setReaderPaperPreset(pureAmoled.lastDayPaperPreset)
        val restoredDay = repository.preferencesFlow.first()
        assertEquals(org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.EYE_CARE_GREEN, restoredDay.readerPaperPreset)
        assertEquals(Wenku8ThemeMode.ARCHIVAL_DAY, restoredDay.themeMode)

        // Switching global theme to CARBON_NIGHT and back to CLASSIC_BLUE also syncs reader paper preset
        repository.setThemeMode(Wenku8ThemeMode.CARBON_NIGHT)
        assertEquals(
            org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.CARBON_NIGHT,
            repository.preferencesFlow.first().readerPaperPreset,
        )
        repository.setThemeMode(Wenku8ThemeMode.CLASSIC_BLUE)
        val backToBlue = repository.preferencesFlow.first()
        assertEquals(Wenku8ThemeMode.CLASSIC_BLUE, backToBlue.themeMode)
        assertEquals(org.mewx.wenku8.v2.reader.model.ReaderPaperPreset.EYE_CARE_GREEN, backToBlue.readerPaperPreset)
    }
}
