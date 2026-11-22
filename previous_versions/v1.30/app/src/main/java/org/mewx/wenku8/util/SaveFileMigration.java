package org.mewx.wenku8.util;

import android.annotation.TargetApi;
import android.content.Context;
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
        String path = getInternalSavePath();
        if (path.isEmpty()) {
            // See getInternalSavePath: empty means the files dir was not resolvable yet. These
            // two are the only callers that hand the path straight to LightCache as a directory
            // rather than concatenating onto it, and those helpers index path.charAt(length - 1)
            // without checking, so an empty one throws StringIndexOutOfBoundsException. Skipping
            // is the honest outcome: there is nowhere to write the signal file yet, and the next
            // call resolves the path again.
            Log.d(TAG, "markMigrationCompleted: no internal save path yet, skipping");
            return;
        }
        LightCache.saveFile(path, SIGNAL_FILE_NAME, "".getBytes(), false);
    }

    public static void revertMigrationStatus() {
        String path = getInternalSavePath();
        if (path.isEmpty()) {
            Log.d(TAG, "revertMigrationStatus: no internal save path yet, skipping");
            return;
        }
        LightCache.deleteFile(path, SIGNAL_FILE_NAME);
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
            LightCache.copyFile(MyApp.getContext().getContentResolver().openInputStream(externalFilePath), internalFilePath, true);
        } else {
            LightCache.copyFile(externalFilePath.getPath(), internalFilePath, true);
        }
        return internalFilePath;
    }

    public static String getInternalSavePath() {
        if (savedInternalPath == null) {
            // Only cache a path that came from a real files dir. This used to be
            // MyApp.getContext().getFilesDir() + File.separator, which turns into the literal
            // string "null/" when either is null -- and then caches it for the life of the
            // process, so every save afterwards goes to a relative "null/..." path that cannot
            // be created. A transient null this early is enough to break storage permanently.
            Context context = MyApp.getContext();
            File filesDir = context == null ? null : context.getFilesDir();
            if (filesDir == null) {
                Log.d(TAG, "getInternalSavePath: no files dir yet, not caching");
                return "";
            }
            savedInternalPath = filesDir + File.separator;
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
