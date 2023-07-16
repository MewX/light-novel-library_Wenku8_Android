package org.mewx.wenku8.util;

import android.os.Environment;
import android.util.Log;

import org.mewx.wenku8.MyApp;

import java.io.File;
import java.util.List;

/**
 * The utility class for migration save files from prior-API-30 to API 30+ (Android R) world.
 */
public class SaveFileMigration {
    private static final String TAG = SaveFileMigration.class.getSimpleName();
    private static final String SIGNAL_FILE_NAME = ".migration_completed";

    public static void markMigrationCompleted() {
        LightCache.saveFile(getInternalSavePath(), SIGNAL_FILE_NAME, "".getBytes(), false);
    }

    public static boolean migrationCompleted() {
        boolean signalFileExists = LightCache.testFileExist(getInternalSavePath() + SIGNAL_FILE_NAME, true);
        Log.d(TAG, "migrationCompleted: " + signalFileExists);
        // TODO: fix this.
//        return false;
        return signalFileExists;
    }

    public static List<String> generateMigrationPlan() {
        // TODO: do we need to check for permissions?
        return LightCache.listAllFilesInDirectory(new File(getExternalStoragePath()));
    }

    /**
     * Given an external file path, copy the file to the internal storage.
     * Although this is slow (because we are not caching the paths), it's acceptable for one-off effort.
     *
     * @param externalFilePath the absolute file path of the file in external storage
     * @return the internal absolute file path to the copied file
     */
    public static String migrateFile(String externalFilePath) {
        String internalFilePath = externalFilePath.replace(getExternalStoragePath(), getInternalSavePath());
        // The missing parent folders will also be created.
        LightCache.copyFile(externalFilePath, internalFilePath, true);
        return internalFilePath;
    }

    public static String getInternalSavePath() {
        return MyApp.getContext().getFilesDir() + File.separator;
    }

    public static String getExternalStoragePath() {
        return Environment.getExternalStorageDirectory() + File.separator + "wenku8" + File.separator;
    }
}
