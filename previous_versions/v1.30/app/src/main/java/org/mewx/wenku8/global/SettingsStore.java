package org.mewx.wenku8.global;

import android.content.ContentValues;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.mewx.wenku8.util.CrashReporter;
import org.mewx.wenku8.util.SaveFileMigration;

/**
 * Reading and writing the app's settings file.
 *
 * <p>Extracted from {@code GlobalConfig} as step 4 of the sequencing in
 * {@code STABILITY_PLAN.md}. {@code GlobalConfig} keeps the same four public methods and delegates
 * here, so no caller changes; {@code SettingItems} deliberately stays on {@code GlobalConfig},
 * because moving it would touch nineteen call sites for no benefit.
 *
 * <p>This is also the class the platform-storage section would replace. Its step 2 puts settings on
 * DataStore first — smallest surface, no relational structure, and the least painful data to get a
 * migration wrong with. Having one named class to swap is most of what makes that contained.
 *
 * <p><b>The format has no escaping, and that is a live limitation rather than a tidy-up.</b>
 * Records are separated by {@code ||||} and each splits on {@code ::::}; a value containing either
 * produces a record that will not parse, and the loader drops it, so the setting silently vanishes
 * on the next load. {@code GlobalConfigSettingsTest} pins that behaviour. Fixing it means changing
 * the on-disk format, which needs a migration rather than a patch — see the platform-storage
 * section.
 */
final class SettingsStore {

    private SettingsStore() {
    }

    private static final String SETTINGS_FILE = "settings.wk8";
    private static final String RECORD_SEPARATOR = "\\|\\|\\|\\|";
    private static final String RECORD_JOINER = "||||";
    private static final String FIELD_SEPARATOR = "::::";

    private static ContentValues allSetting = null;

    /**
     * @param languageFallback what to report as the crash-report language when the settings file
     *                         holds none — the caller's current language, passed in rather than
     *                         read back through {@code getCurrentLang()}, which would write the
     *                         settings file back on first run
     */
    static void load(@NonNull String languageFallback) {
        // Which storage source to use. Highest priority, so it is decided before anything is read.
        StorageRoots.setInternalOnly(SaveFileMigration.migrationCompleted());

        allSetting = new ContentValues();
        final String raw = GlobalConfig.loadFullSaveFileContent(SETTINGS_FILE);

        for (String record : raw.split(RECORD_SEPARATOR)) {
            final String[] field = record.split(FIELD_SEPARATOR);
            // Anything that does not split into exactly two non-empty halves is skipped rather
            // than failing the load: a file damaged by a partial write must not cost the reader
            // every other setting.
            if (field.length != 2 || field[0] == null || field[0].isEmpty()
                    || field[1] == null || field[1].isEmpty()) {
                continue;
            }
            allSetting.put(field[0], field[1]);
        }

        final String version = get(GlobalConfig.SettingItems.version);
        if (version == null || version.isEmpty()) {
            set(GlobalConfig.SettingItems.version, "1");
        }
        // Else, reserved for future settings migration.

        // Crash report context. Both of these change which files get read and which parser path
        // runs, so a report without them is hard to reproduce.
        final String language = allSetting.getAsString(GlobalConfig.SettingItems.language.toString());
        CrashReporter.setKey(CrashReporter.Keys.LANGUAGE,
                language == null ? languageFallback : language);
        CrashReporter.setKey(CrashReporter.Keys.STORAGE_MODE,
                StorageRoots.isInternalOnly() || !StorageRoots.isExternalAvailable()
                        ? "internal" : "external");
        CrashReporter.log("GlobalConfig#loadAllSetting completed");
    }

    static void save() {
        if (allSetting == null) {
            GlobalConfig.loadAllSetting();
        }

        final StringBuilder result = new StringBuilder();
        for (String key : allSetting.keySet()) {
            if (result.length() != 0) {
                result.append(RECORD_JOINER);
            }
            result.append(key).append(FIELD_SEPARATOR).append(allSetting.getAsString(key));
        }
        GlobalConfig.writeFullSaveFileContent(SETTINGS_FILE, result.toString());
    }

    @Nullable
    static String get(GlobalConfig.SettingItems name) {
        if (allSetting == null) {
            GlobalConfig.loadAllSetting();
        }
        return allSetting.getAsString(name.toString());
    }

    static void set(GlobalConfig.SettingItems name, String value) {
        if (allSetting == null) {
            GlobalConfig.loadAllSetting();
        }
        if (name != null && value != null) {
            allSetting.remove(name.toString());
            allSetting.put(name.toString(), value);
            save();
        }
    }
}
