package org.mewx.wenku8.util;

import java.util.Collections;
import java.util.List;

/**
 * The utility class for migration save files from prior-API-30 to API 30+ (Android R) world.
 */
public class SaveFileMigration {
    // TODO: need a method to mark migration completed.

    public static boolean migrationCompleted() {
        // TODO: create a file in internal storage that checks if the migration has completed or not.
        return false;
    }

    public static List<String> generateMigrationPlan() {
        // TODO: generate the list of files used for migration.
        return Collections.emptyList();
    }

    public static boolean migrateFile(String filePath) {
        // TODO
        return true;
    }
}
