package org.mewx.wenku8.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.repository.BookshelfSortMode
import org.mewx.wenku8.data.repository.BookshelfViewMode
import org.mewx.wenku8.reader.model.ReaderColumnMode
import org.mewx.wenku8.reader.model.ReaderPageTurnMode
import org.mewx.wenku8.reader.model.ReaderPaperPreset
import org.mewx.wenku8.ui.navigation.DefaultHomeDestination
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
import java.io.IOException

/**
 * Immutable snapshot of all user-configurable Wenku8 `v2.0` settings (`settings.preferences_pb`).
 *
 * Covers legacy `v1.31` `GlobalConfig.SettingItems` (`settings.wk8`) plus `v2.0` additions:
 * - `language` (`SC` / `TC`) -> [appLanguage]
 * - `eink_mode` (`0` / `1`) -> [einkModeEnabled]
 * - `reader_font_size` (`8..36` sp, covering `v1.31`'s `8..32` sp) -> [readerFontSizeSp]
 * - `reader_line_distance` (`0..32` dp) -> [readerLineDistanceDp]
 * - `reader_paragraph_distance` (`0..48` dp) -> [readerParagraphDistanceDp]
 * - `reader_paragraph_edge_distance` (`0..32` dp) -> [readerPageEdgeDistanceDp]
 * - `reader_font_path` (`""` = system default) -> [readerCustomFontPath]
 * - `reader_background_path` (`""` = system default) -> [readerCustomBackgroundPath]
 * - Plus `v2.0`: [themeMode] (where [Wenku8ThemeMode.SYSTEM] unifies system Day/Night + Material You
 *   dynamic color), [defaultHomeDestination] (`LATEST` default / `BOOKSHELF`), [bookshelfViewMode],
 *   [bookshelfSortMode], [readerPageTurnMode], [readerColumnMode], [readerPaperPreset],
 *   [readerKeepScreenOn], [readerEdgeBrightnessDragEnabled], [volumeKeyPagingEnabled],
 *   [autoOptimizeConnectionEnabled], [conciseModeEnabled], and [legacyV1InternalMigrationVersion].
 */
data class Wenku8UserPreferences(
    val appLanguage: Wenku8API.AppLanguage = Wenku8API.AppLanguage.SC,
    val themeMode: Wenku8ThemeMode = Wenku8ThemeMode.CLASSIC_BLUE,
    val einkModeEnabled: Boolean = false,
    val conciseModeEnabled: Boolean = false,
    val defaultHomeDestination: DefaultHomeDestination = DefaultHomeDestination.LATEST,
    val bookshelfViewMode: BookshelfViewMode = BookshelfViewMode.GRID,
    val bookshelfSortMode: BookshelfSortMode = BookshelfSortMode.MANUAL_ORDER,
    val readerFontSizeSp: Int = DEFAULT_READER_FONT_SIZE_SP,
    val readerLineDistanceDp: Int = DEFAULT_READER_LINE_DISTANCE_DP,
    val readerParagraphDistanceDp: Int = DEFAULT_READER_PARAGRAPH_DISTANCE_DP,
    val readerPageEdgeDistanceDp: Int = DEFAULT_READER_PAGE_EDGE_DISTANCE_DP,
    val readerCustomFontPath: String = "",
    val readerCustomBackgroundPath: String = "",
    val readerPageTurnMode: ReaderPageTurnMode = ReaderPageTurnMode.COVER_SLIDE,
    val readerColumnMode: ReaderColumnMode = ReaderColumnMode.AUTO,
    val readerPaperPreset: ReaderPaperPreset = ReaderPaperPreset.PARCHMENT,
    val lastDayThemeMode: Wenku8ThemeMode = Wenku8ThemeMode.CLASSIC_BLUE,
    val lastDayPaperPreset: ReaderPaperPreset = ReaderPaperPreset.PARCHMENT,
    val readerKeepScreenOn: Boolean = false,
    val readerEdgeBrightnessDragEnabled: Boolean = false,
    val volumeKeyPagingEnabled: Boolean = true,
    val autoOptimizeConnectionEnabled: Boolean = false,
    val cachedNoticeText: String = "",
    val legacyV1InternalMigrationVersion: Int = 0,
    val lastAutoUpdateCheckEpochMs: Long = 0L,
    val dismissedUpdateVersionCode: Int = 0,
) {
    val isBuiltinSerifFont: Boolean
        get() = readerCustomFontPath == BUILTIN_SERIF_FONT_TOKEN

    val hasCustomFontFile: Boolean
        get() = readerCustomFontPath.isNotBlank() && readerCustomFontPath != BUILTIN_SERIF_FONT_TOKEN

    companion object {
        const val BUILTIN_SERIF_FONT_TOKEN = "builtin:serif"

        const val MIN_READER_FONT_SIZE_SP = 8
        const val MAX_READER_FONT_SIZE_SP = 36
        const val DEFAULT_READER_FONT_SIZE_SP = 18

        const val MIN_READER_LINE_DISTANCE_DP = 0
        const val MAX_READER_LINE_DISTANCE_DP = 32
        const val DEFAULT_READER_LINE_DISTANCE_DP = 16

        const val MIN_READER_PARAGRAPH_DISTANCE_DP = 0
        const val MAX_READER_PARAGRAPH_DISTANCE_DP = 48
        const val DEFAULT_READER_PARAGRAPH_DISTANCE_DP = 20

        const val MIN_READER_PAGE_EDGE_DISTANCE_DP = 0
        const val MAX_READER_PAGE_EDGE_DISTANCE_DP = 32
        const val DEFAULT_READER_PAGE_EDGE_DISTANCE_DP = 8
    }
}

/**
 * Computes the synchronized global [Wenku8ThemeMode] when the user selects [preset] inside the
 * reader so switching Day/Night inside the reader updates global UI surfaces (HUD, bottom sheets,
 * snackbars, and outer app screens) without any bright white flash.
 */
fun resolveSyncedThemeForPaperPreset(
    preset: ReaderPaperPreset,
    currentThemeMode: Wenku8ThemeMode,
    lastDayThemeMode: Wenku8ThemeMode = Wenku8ThemeMode.CLASSIC_BLUE,
): Wenku8ThemeMode = when (preset) {
    ReaderPaperPreset.CARBON_NIGHT -> Wenku8ThemeMode.CARBON_NIGHT
    ReaderPaperPreset.PURE_AMOLED -> Wenku8ThemeMode.PURE_AMOLED
    ReaderPaperPreset.PARCHMENT,
    ReaderPaperPreset.PURE_WHITE,
    ReaderPaperPreset.EYE_CARE_GREEN,
    -> when (currentThemeMode) {
        Wenku8ThemeMode.CLASSIC_BLUE,
        Wenku8ThemeMode.ARCHIVAL_DAY,
        Wenku8ThemeMode.E_INK,
        -> currentThemeMode
        Wenku8ThemeMode.CARBON_NIGHT,
        Wenku8ThemeMode.PURE_AMOLED,
        Wenku8ThemeMode.SYSTEM,
        -> lastDayThemeMode.takeIf {
            it == Wenku8ThemeMode.CLASSIC_BLUE || it == Wenku8ThemeMode.ARCHIVAL_DAY
        } ?: Wenku8ThemeMode.CLASSIC_BLUE
    }
}

/**
 * Atomic, corruption-resistant Jetpack Preferences DataStore repository (`settings.preferences_pb`)
 * replacing `v1.31`'s unescaped `||||` / `::::` `SettingsStore`.
 */
class UserPreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) {

    val preferencesFlow: Flow<Wenku8UserPreferences> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs -> mapPreferences(prefs) }

    suspend fun setAppLanguage(language: Wenku8API.AppLanguage) {
        try {
            Wenku8API.CurrentLang = language
        } catch (_: LinkageError) {
            // Ignored on host-JVM unit tests without Android JNI
        }
        dataStore.edit { it[Keys.APP_LANGUAGE] = language.name }
    }

    suspend fun setThemeMode(themeMode: Wenku8ThemeMode) {
        dataStore.edit { prefs ->
            val curPaperPreset = enumValueOrDefault(
                raw = prefs[Keys.READER_PAPER_PRESET],
                default = ReaderPaperPreset.PARCHMENT,
            )
            val savedDayPaper = enumValueOrDefault(
                raw = prefs[Keys.LAST_DAY_PAPER_PRESET],
                default = ReaderPaperPreset.PARCHMENT,
            ).takeIf { !it.isDark } ?: ReaderPaperPreset.PARCHMENT

            prefs[Keys.THEME_MODE] = themeMode.name
            when (themeMode) {
                Wenku8ThemeMode.CLASSIC_BLUE,
                Wenku8ThemeMode.ARCHIVAL_DAY,
                -> {
                    prefs[Keys.LAST_DAY_THEME_MODE] = themeMode.name
                    if (curPaperPreset.isDark) {
                        prefs[Keys.READER_PAPER_PRESET] = savedDayPaper.name
                    }
                }
                Wenku8ThemeMode.CARBON_NIGHT -> {
                    if (!curPaperPreset.isDark) {
                        prefs[Keys.LAST_DAY_PAPER_PRESET] = curPaperPreset.name
                    }
                    prefs[Keys.READER_PAPER_PRESET] = ReaderPaperPreset.CARBON_NIGHT.name
                }
                Wenku8ThemeMode.PURE_AMOLED -> {
                    if (!curPaperPreset.isDark) {
                        prefs[Keys.LAST_DAY_PAPER_PRESET] = curPaperPreset.name
                    }
                    prefs[Keys.READER_PAPER_PRESET] = ReaderPaperPreset.PURE_AMOLED.name
                }
                Wenku8ThemeMode.SYSTEM,
                Wenku8ThemeMode.E_INK,
                -> Unit
            }

            // Selecting E_INK directly in the theme switcher also syncs the legacy eink_mode flag
            // so Reader and Profile toggles stay 100% consistent.
            if (themeMode == Wenku8ThemeMode.E_INK) {
                prefs[Keys.EINK_MODE_ENABLED] = true
            } else if (prefs[Keys.EINK_MODE_ENABLED] == true) {
                prefs[Keys.EINK_MODE_ENABLED] = false
            }
        }
    }

    suspend fun setEinkModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.EINK_MODE_ENABLED] = enabled
            if (!enabled && prefs[Keys.THEME_MODE] == Wenku8ThemeMode.E_INK.name) {
                prefs[Keys.THEME_MODE] = Wenku8ThemeMode.CLASSIC_BLUE.name
            }
        }
    }

    suspend fun setConciseModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.CONCISE_MODE_ENABLED] = enabled
        }
    }

    suspend fun setDefaultHomeDestination(destination: DefaultHomeDestination) {
        dataStore.edit { it[Keys.DEFAULT_HOME_DESTINATION] = destination.name }
    }

    suspend fun setBookshelfViewMode(viewMode: BookshelfViewMode) {
        dataStore.edit { it[Keys.BOOKSHELF_VIEW_MODE] = viewMode.name }
    }

    suspend fun setBookshelfSortMode(sortMode: BookshelfSortMode) {
        dataStore.edit { it[Keys.BOOKSHELF_SORT_MODE] = sortMode.name }
    }

    suspend fun setReaderTypography(
        fontSizeSp: Int,
        lineDistanceDp: Int,
        paragraphDistanceDp: Int,
        pageEdgeDistanceDp: Int,
    ) {
        dataStore.edit { prefs ->
            prefs[Keys.READER_FONT_SIZE_SP] = fontSizeSp.coerceIn(
                Wenku8UserPreferences.MIN_READER_FONT_SIZE_SP,
                Wenku8UserPreferences.MAX_READER_FONT_SIZE_SP,
            )
            prefs[Keys.READER_LINE_DISTANCE_DP] = lineDistanceDp.coerceIn(
                Wenku8UserPreferences.MIN_READER_LINE_DISTANCE_DP,
                Wenku8UserPreferences.MAX_READER_LINE_DISTANCE_DP,
            )
            prefs[Keys.READER_PARAGRAPH_DISTANCE_DP] = paragraphDistanceDp.coerceIn(
                Wenku8UserPreferences.MIN_READER_PARAGRAPH_DISTANCE_DP,
                Wenku8UserPreferences.MAX_READER_PARAGRAPH_DISTANCE_DP,
            )
            prefs[Keys.READER_PAGE_EDGE_DISTANCE_DP] = pageEdgeDistanceDp.coerceIn(
                Wenku8UserPreferences.MIN_READER_PAGE_EDGE_DISTANCE_DP,
                Wenku8UserPreferences.MAX_READER_PAGE_EDGE_DISTANCE_DP,
            )
        }
    }

    suspend fun setReaderCustomFontPath(path: String) {
        val normalized = if (path == "0") "" else path.trim()
        dataStore.edit { it[Keys.READER_CUSTOM_FONT_PATH] = normalized }
    }

    suspend fun setReaderCustomBackgroundPath(path: String) {
        val normalized = if (path == "0") "" else path.trim()
        dataStore.edit { it[Keys.READER_CUSTOM_BG_PATH] = normalized }
    }

    suspend fun setReaderPageTurnMode(mode: ReaderPageTurnMode) {
        dataStore.edit { it[Keys.READER_PAGE_TURN_MODE] = mode.name }
    }

    suspend fun setReaderColumnMode(mode: ReaderColumnMode) {
        dataStore.edit { it[Keys.READER_COLUMN_MODE] = mode.name }
    }

    suspend fun setReaderPaperPreset(preset: ReaderPaperPreset) {
        dataStore.edit { prefs ->
            val curPaperPreset = enumValueOrDefault(
                raw = prefs[Keys.READER_PAPER_PRESET],
                default = ReaderPaperPreset.PARCHMENT,
            )
            val curTheme = enumValueOrDefault(
                raw = prefs[Keys.THEME_MODE],
                default = Wenku8ThemeMode.CLASSIC_BLUE,
            )
            val savedLastDayTheme = enumValueOrDefault(
                raw = prefs[Keys.LAST_DAY_THEME_MODE],
                default = Wenku8ThemeMode.CLASSIC_BLUE,
            )

            if (!curPaperPreset.isDark) {
                prefs[Keys.LAST_DAY_PAPER_PRESET] = curPaperPreset.name
            }
            if (!preset.isDark) {
                prefs[Keys.LAST_DAY_PAPER_PRESET] = preset.name
            }
            if (curTheme == Wenku8ThemeMode.CLASSIC_BLUE || curTheme == Wenku8ThemeMode.ARCHIVAL_DAY) {
                prefs[Keys.LAST_DAY_THEME_MODE] = curTheme.name
            }

            prefs[Keys.READER_PAPER_PRESET] = preset.name
            val syncedTheme = resolveSyncedThemeForPaperPreset(
                preset = preset,
                currentThemeMode = curTheme,
                lastDayThemeMode = savedLastDayTheme,
            )
            prefs[Keys.THEME_MODE] = syncedTheme.name
            if (preset.isDark && prefs[Keys.EINK_MODE_ENABLED] == true) {
                prefs[Keys.EINK_MODE_ENABLED] = false
            }
        }
    }

    suspend fun setReaderKeepScreenOn(enabled: Boolean) {
        dataStore.edit { it[Keys.READER_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setReaderEdgeBrightnessDragEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.READER_EDGE_BRIGHTNESS_DRAG] = enabled }
    }

    suspend fun setVolumeKeyPagingEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.VOLUME_KEY_PAGING_ENABLED] = enabled }
    }

    suspend fun setAutoOptimizeConnectionEnabled(enabled: Boolean) {
        dataStore.edit { it[Keys.AUTO_OPTIMIZE_CONNECTION_ENABLED] = enabled }
    }

    suspend fun setCachedNoticeText(noticeText: String) {
        dataStore.edit { it[Keys.CACHED_NOTICE_TEXT] = noticeText }
    }

    suspend fun recordAutoUpdateCheck(epochMs: Long) {
        dataStore.edit { it[Keys.LAST_AUTO_UPDATE_CHECK_EPOCH_MS] = epochMs.coerceAtLeast(0L) }
    }

    suspend fun setDismissedUpdateVersionCode(versionCode: Int) {
        dataStore.edit { it[Keys.DISMISSED_UPDATE_VERSION_CODE] = versionCode.coerceAtLeast(0) }
    }

    /**
     * Atomically writes all settings parsed from a legacy `v1.31` `settings.wk8` file (`P0.3`)
     * and stamps [migrationVersion] so internal-storage migration runs strictly once.
     */
    suspend fun applyMigratedLegacyV1Settings(
        migratedSettings: Map<String, String>,
        cachedNoticeText: String?,
        migrationVersion: Int = CURRENT_V1_MIGRATION_VERSION,
    ) {
        dataStore.edit { prefs ->
            migratedSettings["language"]?.let { rawLang ->
                val resolvedLang = if (rawLang.equals("TC", ignoreCase = true)) {
                    Wenku8API.AppLanguage.TC
                } else {
                    Wenku8API.AppLanguage.SC
                }
                try {
                    Wenku8API.CurrentLang = resolvedLang
                } catch (_: LinkageError) {
                    // Ignored on host-JVM unit tests without Android JNI
                }
                prefs[Keys.APP_LANGUAGE] = resolvedLang.name
            }

            migratedSettings["eink_mode"]?.toIntOrNull()?.let { rawEink ->
                val einkEnabled = rawEink == 1
                prefs[Keys.EINK_MODE_ENABLED] = einkEnabled
                if (einkEnabled) {
                    prefs[Keys.THEME_MODE] = Wenku8ThemeMode.E_INK.name
                }
            }

            migratedSettings["reader_font_size"]?.toIntOrNull()?.let { rawSize ->
                prefs[Keys.READER_FONT_SIZE_SP] = rawSize.coerceIn(
                    Wenku8UserPreferences.MIN_READER_FONT_SIZE_SP,
                    Wenku8UserPreferences.MAX_READER_FONT_SIZE_SP,
                )
            }
            migratedSettings["reader_line_distance"]?.toIntOrNull()?.let { rawLine ->
                prefs[Keys.READER_LINE_DISTANCE_DP] = rawLine.coerceIn(
                    Wenku8UserPreferences.MIN_READER_LINE_DISTANCE_DP,
                    Wenku8UserPreferences.MAX_READER_LINE_DISTANCE_DP,
                )
            }
            migratedSettings["reader_paragraph_distance"]?.toIntOrNull()?.let { rawPara ->
                prefs[Keys.READER_PARAGRAPH_DISTANCE_DP] = rawPara.coerceIn(
                    Wenku8UserPreferences.MIN_READER_PARAGRAPH_DISTANCE_DP,
                    Wenku8UserPreferences.MAX_READER_PARAGRAPH_DISTANCE_DP,
                )
            }
            migratedSettings["reader_paragraph_edge_distance"]?.toIntOrNull()?.let { rawEdge ->
                prefs[Keys.READER_PAGE_EDGE_DISTANCE_DP] = rawEdge.coerceIn(
                    Wenku8UserPreferences.MIN_READER_PAGE_EDGE_DISTANCE_DP,
                    Wenku8UserPreferences.MAX_READER_PAGE_EDGE_DISTANCE_DP,
                )
            }
            migratedSettings["reader_font_path"]?.let { rawFont ->
                prefs[Keys.READER_CUSTOM_FONT_PATH] = if (rawFont == "0") "" else rawFont
            }
            migratedSettings["reader_background_path"]?.let { rawReaderBg ->
                prefs[Keys.READER_CUSTOM_BG_PATH] = if (rawReaderBg == "0") "" else rawReaderBg
            }

            if (!cachedNoticeText.isNullOrBlank()) {
                prefs[Keys.CACHED_NOTICE_TEXT] = cachedNoticeText
            }
            prefs[Keys.LEGACY_V1_MIGRATION_VERSION] = migrationVersion
        }
    }

    /**
     * Atomically applies portable user preferences imported from a `.wk8backup.zip` archive.
     */
    suspend fun applyImportedPortablePreferences(imported: Wenku8UserPreferences) {
        try {
            Wenku8API.CurrentLang = imported.appLanguage
        } catch (_: LinkageError) {
            // Ignored on host-JVM unit tests without Android JNI
        }
        dataStore.edit { prefs ->
            prefs[Keys.APP_LANGUAGE] = imported.appLanguage.name
            prefs[Keys.THEME_MODE] = imported.themeMode.name
            prefs[Keys.EINK_MODE_ENABLED] = imported.einkModeEnabled
            prefs[Keys.CONCISE_MODE_ENABLED] = imported.conciseModeEnabled
            prefs[Keys.DEFAULT_HOME_DESTINATION] = imported.defaultHomeDestination.name
            prefs[Keys.BOOKSHELF_VIEW_MODE] = imported.bookshelfViewMode.name
            prefs[Keys.BOOKSHELF_SORT_MODE] = imported.bookshelfSortMode.name
            prefs[Keys.READER_FONT_SIZE_SP] = imported.readerFontSizeSp.coerceIn(
                Wenku8UserPreferences.MIN_READER_FONT_SIZE_SP,
                Wenku8UserPreferences.MAX_READER_FONT_SIZE_SP,
            )
            prefs[Keys.READER_LINE_DISTANCE_DP] = imported.readerLineDistanceDp.coerceIn(
                Wenku8UserPreferences.MIN_READER_LINE_DISTANCE_DP,
                Wenku8UserPreferences.MAX_READER_LINE_DISTANCE_DP,
            )
            prefs[Keys.READER_PARAGRAPH_DISTANCE_DP] = imported.readerParagraphDistanceDp.coerceIn(
                Wenku8UserPreferences.MIN_READER_PARAGRAPH_DISTANCE_DP,
                Wenku8UserPreferences.MAX_READER_PARAGRAPH_DISTANCE_DP,
            )
            prefs[Keys.READER_PAGE_EDGE_DISTANCE_DP] = imported.readerPageEdgeDistanceDp.coerceIn(
                Wenku8UserPreferences.MIN_READER_PAGE_EDGE_DISTANCE_DP,
                Wenku8UserPreferences.MAX_READER_PAGE_EDGE_DISTANCE_DP,
            )
            prefs[Keys.READER_CUSTOM_FONT_PATH] = imported.readerCustomFontPath
            prefs[Keys.READER_CUSTOM_BG_PATH] = imported.readerCustomBackgroundPath
            prefs[Keys.READER_PAGE_TURN_MODE] = imported.readerPageTurnMode.name
            prefs[Keys.READER_COLUMN_MODE] = imported.readerColumnMode.name
            prefs[Keys.READER_PAPER_PRESET] = imported.readerPaperPreset.name
            prefs[Keys.READER_KEEP_SCREEN_ON] = imported.readerKeepScreenOn
            prefs[Keys.READER_EDGE_BRIGHTNESS_DRAG] = imported.readerEdgeBrightnessDragEnabled
            prefs[Keys.VOLUME_KEY_PAGING_ENABLED] = imported.volumeKeyPagingEnabled
            prefs[Keys.AUTO_OPTIMIZE_CONNECTION_ENABLED] = imported.autoOptimizeConnectionEnabled
        }
    }

    private fun mapPreferences(prefs: Preferences): Wenku8UserPreferences {
        val language = enumValueOrDefault(
            raw = prefs[Keys.APP_LANGUAGE],
            default = Wenku8API.AppLanguage.SC,
        )
        val themeMode = enumValueOrDefault(
            raw = prefs[Keys.THEME_MODE],
            default = Wenku8ThemeMode.CLASSIC_BLUE,
        )
        val defaultHome = enumValueOrDefault(
            raw = prefs[Keys.DEFAULT_HOME_DESTINATION],
            default = DefaultHomeDestination.LATEST,
        )
        val bookshelfView = enumValueOrDefault(
            raw = prefs[Keys.BOOKSHELF_VIEW_MODE],
            default = BookshelfViewMode.GRID,
        )
        val bookshelfSort = enumValueOrDefault(
            raw = prefs[Keys.BOOKSHELF_SORT_MODE],
            default = BookshelfSortMode.MANUAL_ORDER,
        )
        val pageTurnMode = enumValueOrDefault(
            raw = prefs[Keys.READER_PAGE_TURN_MODE],
            default = ReaderPageTurnMode.COVER_SLIDE,
        )
        val columnMode = enumValueOrDefault(
            raw = prefs[Keys.READER_COLUMN_MODE],
            default = ReaderColumnMode.AUTO,
        )
        val paperPreset = enumValueOrDefault(
            raw = prefs[Keys.READER_PAPER_PRESET],
            default = ReaderPaperPreset.PARCHMENT,
        )
        val lastDayTheme = enumValueOrDefault(
            raw = prefs[Keys.LAST_DAY_THEME_MODE],
            default = Wenku8ThemeMode.CLASSIC_BLUE,
        ).takeIf {
            it == Wenku8ThemeMode.CLASSIC_BLUE || it == Wenku8ThemeMode.ARCHIVAL_DAY
        } ?: Wenku8ThemeMode.CLASSIC_BLUE
        val lastDayPaper = enumValueOrDefault(
            raw = prefs[Keys.LAST_DAY_PAPER_PRESET],
            default = ReaderPaperPreset.PARCHMENT,
        ).takeIf { !it.isDark } ?: ReaderPaperPreset.PARCHMENT
        val einkEnabled = prefs[Keys.EINK_MODE_ENABLED] ?: (themeMode == Wenku8ThemeMode.E_INK)

        return Wenku8UserPreferences(
            appLanguage = language,
            themeMode = themeMode,
            einkModeEnabled = einkEnabled,
            conciseModeEnabled = prefs[Keys.CONCISE_MODE_ENABLED] ?: false,
            defaultHomeDestination = defaultHome,
            bookshelfViewMode = bookshelfView,
            bookshelfSortMode = bookshelfSort,
            readerFontSizeSp = (prefs[Keys.READER_FONT_SIZE_SP]
                ?: Wenku8UserPreferences.DEFAULT_READER_FONT_SIZE_SP).coerceIn(
                Wenku8UserPreferences.MIN_READER_FONT_SIZE_SP,
                Wenku8UserPreferences.MAX_READER_FONT_SIZE_SP,
            ),
            readerLineDistanceDp = (prefs[Keys.READER_LINE_DISTANCE_DP]
                ?: Wenku8UserPreferences.DEFAULT_READER_LINE_DISTANCE_DP).coerceIn(
                Wenku8UserPreferences.MIN_READER_LINE_DISTANCE_DP,
                Wenku8UserPreferences.MAX_READER_LINE_DISTANCE_DP,
            ),
            readerParagraphDistanceDp = (prefs[Keys.READER_PARAGRAPH_DISTANCE_DP]
                ?: Wenku8UserPreferences.DEFAULT_READER_PARAGRAPH_DISTANCE_DP).coerceIn(
                Wenku8UserPreferences.MIN_READER_PARAGRAPH_DISTANCE_DP,
                Wenku8UserPreferences.MAX_READER_PARAGRAPH_DISTANCE_DP,
            ),
            readerPageEdgeDistanceDp = (prefs[Keys.READER_PAGE_EDGE_DISTANCE_DP]
                ?: Wenku8UserPreferences.DEFAULT_READER_PAGE_EDGE_DISTANCE_DP).coerceIn(
                Wenku8UserPreferences.MIN_READER_PAGE_EDGE_DISTANCE_DP,
                Wenku8UserPreferences.MAX_READER_PAGE_EDGE_DISTANCE_DP,
            ),
            readerCustomFontPath = prefs[Keys.READER_CUSTOM_FONT_PATH] ?: "",
            readerCustomBackgroundPath = prefs[Keys.READER_CUSTOM_BG_PATH] ?: "",
            readerPageTurnMode = pageTurnMode,
            readerColumnMode = columnMode,
            readerPaperPreset = paperPreset,
            lastDayThemeMode = lastDayTheme,
            lastDayPaperPreset = lastDayPaper,
            readerKeepScreenOn = prefs[Keys.READER_KEEP_SCREEN_ON] ?: false,
            readerEdgeBrightnessDragEnabled = prefs[Keys.READER_EDGE_BRIGHTNESS_DRAG] ?: false,
            volumeKeyPagingEnabled = prefs[Keys.VOLUME_KEY_PAGING_ENABLED] ?: true,
            autoOptimizeConnectionEnabled = prefs[Keys.AUTO_OPTIMIZE_CONNECTION_ENABLED] ?: false,
            cachedNoticeText = prefs[Keys.CACHED_NOTICE_TEXT] ?: "",
            legacyV1InternalMigrationVersion = prefs[Keys.LEGACY_V1_MIGRATION_VERSION] ?: 0,
            lastAutoUpdateCheckEpochMs = (prefs[Keys.LAST_AUTO_UPDATE_CHECK_EPOCH_MS] ?: 0L).coerceAtLeast(0L),
            dismissedUpdateVersionCode = (prefs[Keys.DISMISSED_UPDATE_VERSION_CODE] ?: 0).coerceAtLeast(0),
        )
    }

    private inline fun <reified E : Enum<E>> enumValueOrDefault(raw: String?, default: E): E {
        if (raw.isNullOrBlank()) return default
        return runCatching { enumValueOf<E>(raw) }.getOrDefault(default)
    }

    private object Keys {
        val APP_LANGUAGE = stringPreferencesKey("app_language")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val EINK_MODE_ENABLED = booleanPreferencesKey("eink_mode_enabled")
        val CONCISE_MODE_ENABLED = booleanPreferencesKey("concise_mode_enabled")
        val DEFAULT_HOME_DESTINATION = stringPreferencesKey("default_home_destination")
        val BOOKSHELF_VIEW_MODE = stringPreferencesKey("bookshelf_view_mode")
        val BOOKSHELF_SORT_MODE = stringPreferencesKey("bookshelf_sort_mode")
        val READER_FONT_SIZE_SP = intPreferencesKey("reader_font_size_sp")
        val READER_LINE_DISTANCE_DP = intPreferencesKey("reader_line_distance_dp")
        val READER_PARAGRAPH_DISTANCE_DP = intPreferencesKey("reader_paragraph_distance_dp")
        val READER_PAGE_EDGE_DISTANCE_DP = intPreferencesKey("reader_page_edge_distance_dp")
        val READER_CUSTOM_FONT_PATH = stringPreferencesKey("reader_custom_font_path")
        val READER_CUSTOM_BG_PATH = stringPreferencesKey("reader_custom_bg_path")
        val READER_PAGE_TURN_MODE = stringPreferencesKey("reader_page_turn_mode")
        val READER_COLUMN_MODE = stringPreferencesKey("reader_column_mode")
        val READER_PAPER_PRESET = stringPreferencesKey("reader_paper_preset")
        val LAST_DAY_THEME_MODE = stringPreferencesKey("last_day_theme_mode")
        val LAST_DAY_PAPER_PRESET = stringPreferencesKey("last_day_paper_preset")
        val READER_KEEP_SCREEN_ON = booleanPreferencesKey("reader_keep_screen_on")
        val READER_EDGE_BRIGHTNESS_DRAG = booleanPreferencesKey("reader_edge_brightness_drag")
        val VOLUME_KEY_PAGING_ENABLED = booleanPreferencesKey("volume_key_paging_enabled")
        val AUTO_OPTIMIZE_CONNECTION_ENABLED = booleanPreferencesKey("auto_optimize_connection_enabled")
        val CACHED_NOTICE_TEXT = stringPreferencesKey("cached_notice_text")
        val LEGACY_V1_MIGRATION_VERSION = intPreferencesKey("legacy_v1_internal_migrated_version")
        val LAST_AUTO_UPDATE_CHECK_EPOCH_MS = longPreferencesKey("last_auto_update_check_epoch_ms")
        val DISMISSED_UPDATE_VERSION_CODE = intPreferencesKey("dismissed_update_version_code")
    }

    companion object {
        const val SETTINGS_DATASTORE_NAME = "settings"
        const val CURRENT_V1_MIGRATION_VERSION = 2

        fun create(
            context: Context,
            scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
        ): UserPreferencesRepository {
            val appContext = context.applicationContext
            val store = PreferenceDataStoreFactory.create(
                scope = scope,
                produceFile = { appContext.preferencesDataStoreFile(SETTINGS_DATASTORE_NAME) },
            )
            return UserPreferencesRepository(store)
        }
    }
}
