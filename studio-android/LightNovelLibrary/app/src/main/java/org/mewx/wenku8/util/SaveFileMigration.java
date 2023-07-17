package org.mewx.wenku8.util;

import android.annotation.TargetApi;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import org.mewx.wenku8.MyApp;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.List;

/**
 * The utility class for migration save files from prior-API-30 to API 30+ (Android R) world.
 */
public class SaveFileMigration {
    private static final String TAG = SaveFileMigration.class.getSimpleName();
    private static final String SIGNAL_FILE_NAME = ".migration_completed";

    // Cached paths.
    private static String savedInternalPath = null;
    private static String savedExternalPath = null;
    // This Uri is needed because constructing Uri just from a path is hard. The path looks like: /tree/primary:wenku8.
    private static Uri overrideExternalPathUrl = null;

    public static void markMigrationCompleted() {
        LightCache.saveFile(getInternalSavePath(), SIGNAL_FILE_NAME, "".getBytes(), false);
    }

    public static void revertMigrationStatus() {
        LightCache.deleteFile(getInternalSavePath(), SIGNAL_FILE_NAME);
    }

    /**
     * Checks if the external storage contains the wenku8 directory.
     * @return true if eligible; otherwise false
     */
    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    public static boolean migrationEligible() {
        return LightCache.testFileExist(Environment.getExternalStorageDirectory() + File.separator + "wenku8" + File.separator, true);
    }

    public static boolean migrationCompleted() {
        boolean signalFileExists = LightCache.testFileExist(getInternalSavePath() + SIGNAL_FILE_NAME, true);
        Log.d(TAG, "migrationCompleted: " + signalFileExists);
        return signalFileExists;
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    public static List<Uri> generateMigrationPlan() {
        if (overrideExternalPathUrl != null) {
            return LightCache.listAllFilesInDirectory(DocumentFile.fromTreeUri(MyApp.getContext(), overrideExternalPathUrl));
        }
        return LightCache.listAllFilesInDirectory(new File(getExternalStoragePath()));
    }

    /**
     * Given an external file path, copy the file to the internal storage.
     * Although this is slow (because we are not caching the paths), it's acceptable for one-off effort.
     *
     * @param externalFilePath the file Uri in external storage
     * @return the internal absolute file path to the copied file
     */
    public static String migrateFile(Uri externalFilePath) throws FileNotFoundException {
        String internalFilePath = externalFilePath.getPath().replace(getExternalStoragePath(), getInternalSavePath());
        // The missing parent folders will also be created.
        if (overrideExternalPathUrl != null) {
            byte[] content = LightCache.loadStream(MyApp.getContext().getContentResolver().openInputStream(externalFilePath));
            LightCache.saveFile(internalFilePath, content, true);
        } else {
            LightCache.copyFile(externalFilePath.getPath(), internalFilePath, true);
        }
        return internalFilePath;
    }

    public static String getInternalSavePath() {
        if (savedInternalPath == null) {
            savedInternalPath = MyApp.getContext().getFilesDir() + File.separator;
        }
        return savedInternalPath;
    }

    public static void overrideExternalPath(Uri uri) {
        overrideExternalPathUrl = uri;
    }

    public static String getExternalStoragePath() {
        if (overrideExternalPathUrl != null) {
            return overrideExternalPathUrl.getPath();
        }
        if (savedExternalPath == null) {
            savedExternalPath = Environment.getExternalStorageDirectory() + File.separator + "wenku8" + File.separator;
        }
        return savedExternalPath;
    }
}
