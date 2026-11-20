package org.mewx.wenku8.util;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import org.mewx.wenku8.global.api.ChapterInfo;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.util.CrashReporter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

/**
 * Light Cache
 * *
 * This class provide straight file operation functions.
 * Easy save file, read file and delete file.
 */
public class LightCache {
    private static final String TAG = LightCache.class.getSimpleName();
    private static final int DEFAULT_READ_BUFFER_SIZE = 8192;

    /**
     * Test whether file exists
     *
     * @param path the full file path
     * @return true if file exist and not empty;
     * otherwise false, and if the file exists but it's empty, it will get removed
     */
    public static boolean testFileExist(String path) {
        return testFileExist(path, false);
    }

    public static boolean testFileExist(String path, boolean allowEmptyFile) {
        File file = new File(path);
        if (file.exists()) {
            if (!allowEmptyFile && file.length() == 0)
                deleteFile(path); // delete empty file and return false
            else
                return true;
        }
        return false;
    }

    /**
     * load file content
     *
     * @param path full file path (can be relative)
     * @return null if the file does not exist; otherwise the file content string, can be empty
     */
    public static byte[] loadFile(String path) {
        // if file not exist, then return null
        File file = new File(path);
        if (file.exists() && file.isFile()) {
            // load existing file
            try {
                return loadStream(new FileInputStream(file));
            } catch (FileNotFoundException e) {
                CrashReporter.recordException("LightCache.loadFile", e);
            }
        }
        return null;
    }

    /**
     * Read a stream to its end.
     *
     * <p>This used to size a single {@code read()} from {@link InputStream#available()}.
     * available() is an estimate of what can be read without blocking rather than the length
     * of the stream, and one read() is not obliged to fill the buffer it is given, so that
     * silently handed back a truncated, zero-padded array whenever the source was buffered or
     * larger than the readahead window. The array became novel XML and failed to parse much
     * later, which is why the failure was never traceable from a crash report.
     *
     * @param inputStream the stream to drain; closed before returning
     * @return the full stream content, or null if it could not be read
     */
    public static byte[] loadStream(InputStream inputStream) {
        try (InputStream in = inputStream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream(
                    Math.max(in.available(), DEFAULT_READ_BUFFER_SIZE));
            byte[] chunk = new byte[DEFAULT_READ_BUFFER_SIZE];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException e) {
            CrashReporter.recordException("LightCache.loadStream", e);
        }
        return null;
    }

    public static boolean saveFile(String path, String fileName, byte[] bs, boolean forceUpdate) {
        final String fullPath = path + (path.charAt(path.length() - 1) != File.separatorChar ? File.separator : "") + fileName;
        return saveFile(fullPath, bs, forceUpdate);
    }

    /**
     * Suffix of the file a write goes to before it is renamed over the real one.
     *
     * <p>Fixed rather than unique on purpose: a process killed mid-write leaves one of these
     * behind, and the next write to the same path overwrites it, so they cannot accumulate. It
     * does not collide with any name the app looks up, all of which end in {@code .xml}.
     */
    private static final String STAGING_SUFFIX = ".tmp";

    /**
     * Writes {@code bs} to {@code filepath}, replacing what is there in one step.
     *
     * <p>This used to open the real file and truncate it, so a process killed partway through --
     * or a device out of space -- left a file that existed, was readable, and held half a
     * document. That is not hypothetical here: {@code GlobalConfig.loadLocalBookShelf} carries a
     * comment about a partially written bookshelf taking out the app on launch, and
     * {@code FavFragment} treats an unparseable novel intro as a truncated cache file. Both were
     * this method.
     *
     * <p>So the content goes to a sibling file first, is flushed to disk, and is then renamed
     * over the target. Rename within a directory is atomic, which means a reader sees either the
     * previous file or the new one, never a partial one. The fsync is what makes that true after
     * power loss rather than only after a process death, and costs a few milliseconds against
     * network requests that cost hundreds.
     *
     * <p>Callers keep the old contract: nothing is written when the file already exists and
     * {@code forceUpdate} is false, and that still counts as success.
     */
    public static boolean saveFile(String filepath, byte[] bs, boolean forceUpdate) {
        // create parent folder first when applicable
        File file = new File(filepath);
        if (file.getParentFile() != null && !file.getParentFile().exists() && !file.getParentFile().mkdirs())
            Log.d(TAG, "Failed to create dir: " + filepath);

        // if forceUpdate == true then update the file
        Log.d(TAG, "Path: " + filepath);
        if (!file.exists() || forceUpdate) {
            if (file.exists() && !file.isFile()) {
                Log.d(TAG, "Failed to write, which may caused by file is not a file");
                return false; // is not a file
            }

            final File staging = new File(filepath + STAGING_SUFFIX);
            try {
                FileOutputStream out = new FileOutputStream(staging); // truncate the staging file
                try {
                    out.write(bs);
                    out.flush();
                    out.getFD().sync();
                } finally {
                    out.close();
                }
            } catch (IOException e) {
                CrashReporter.recordException("LightCache.saveFile", e);
                deleteFile(staging.getPath());
                return false;
            }

            if (!staging.renameTo(file)) {
                // Renaming onto an existing file replaces it on the filesystems Android uses, so
                // this is not the ordinary path. Falling back to remove-then-rename reopens the
                // window this method exists to close, but losing the write outright is worse, and
                // the staging file is still removed either way.
                Log.d(TAG, "Rename failed, falling back to replace: " + filepath);
                if (!file.delete() || !staging.renameTo(file)) {
                    Log.d(TAG, "Failed to write: " + filepath);
                    deleteFile(staging.getPath());
                    return false;
                }
            }
            Log.d(TAG, "Write successfully");
        }
        return true; // say it successful
    }

    public static boolean deleteFile(String path, String fileName) {
        final String fullPath = path + (path.charAt(path.length() - 1) != File.separatorChar ? File.separator : "") + fileName;
        return deleteFile(fullPath);
    }

    public static boolean deleteFile(String filepath) {
        Log.d(TAG, "Deleting: " + filepath);
        return new File(filepath).delete();
    }

    /**
     * Copy file from one place to another place,
     * if target parent path does not exist, then create them
     *
     * @param from       full path
     * @param to         full path
     * @param forceWrite true if wanting to override
     */
    public static void copyFile(String from, String to, Boolean forceWrite) {
        File fromFile = new File(from);
        if (!fromFile.exists() || !fromFile.isFile() || !fromFile.canRead()) {
            return;
        }

        try {
            java.io.FileInputStream fosFrom = new java.io.FileInputStream(fromFile);
            copyFile(fosFrom, to, forceWrite);
        } catch (Exception ex) {
            CrashReporter.recordException("LightCache.copyFile", ex);
        }
    }

    public static void copyFile(InputStream from, String to, Boolean forceWrite) {
        File toFile = new File(to);
        if (toFile.exists() && !forceWrite)
            return;

        if (!toFile.getParentFile().exists() && !toFile.getParentFile().mkdirs())
            Log.d(TAG, "Failed to create parent dirs for target file: " + to);
        if (toFile.exists() && forceWrite && !toFile.delete())
            Log.d(TAG, "Failed to create or delete target file: " + to);

        try {
            java.io.FileOutputStream fosTo = new FileOutputStream(toFile);

            byte[] bt = new byte[1024];
            int c;
            while ((c = from.read(bt)) > 0) fosTo.write(bt, 0, c);
            from.close();
            fosTo.close();
        } catch (Exception ex) {
            CrashReporter.recordException("LightCache.copyFile", ex);
        }
    }

    // Copied from https://stackoverflow.com/a/36714242/4206925
    public static String getFilePath(Context context, Uri uri) {
        String selection = null;
        String[] selectionArgs = null;
        if (DocumentsContract.isDocumentUri(context.getApplicationContext(), uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                return Environment.getExternalStorageDirectory() + "/" + split[1];
            } else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                uri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
            } else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];
                if ("image".equals(type)) {
                    uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                } else if ("video".equals(type)) {
                    uri = MediaStore.Video.Media.EXTERNAL_CONTENT_URI;
                } else if ("audio".equals(type)) {
                    uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
                }
                selection = "_id=?";
                selectionArgs = new String[]{
                        split[1]
                };
            }
        }
        if ("content".equalsIgnoreCase(uri.getScheme())) {
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }

            String[] projection = {
                    MediaStore.Images.Media.DATA
            };
            // try-with-resources: the cursor was previously leaked on every path, including
            // the one that returns a result.
            try (Cursor cursor = context.getContentResolver()
                    .query(uri, projection, selection, selectionArgs, null)) {
                if (cursor != null) {
                    int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                    if (cursor.moveToFirst()) {
                        return cursor.getString(column_index);
                    }
                }
            } catch (Exception e) {
                CrashReporter.recordException("LightCache.getFilePath", e);
            }
        } else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    public static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    public static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    public static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    /**
     * Lists all files in the given directory recursively.
     * @param fullDirectoryPath the directory to look up
     * @return the list of absolute paths for all files inside
     */
    public static List<Uri> listAllFilesInDirectory(File fullDirectoryPath) {
        ArrayList<Uri> paths = new ArrayList<>();
        Queue<File> directoryQueue = new LinkedList<>();
        if (fullDirectoryPath.isDirectory()) {
            directoryQueue.add(fullDirectoryPath);
        }

        // BFS getting all file Uris.
        while (!directoryQueue.isEmpty()) {
            File currentDir = directoryQueue.remove();
            File[] fileList = currentDir.listFiles();
            if (fileList == null) {
                continue;
            }

            for (File file : fileList) {
                if (file.isDirectory()) {
                    directoryQueue.add(file);
                } else if (file.isFile()) {
                    paths.add(Uri.fromFile(file));
                }
            }
        }
        return paths;
    }

    public static List<Uri> listAllFilesInDirectory(DocumentFile fullDirectoryPath) {
        ArrayList<Uri> paths = new ArrayList<>();
        Queue<DocumentFile> directoryQueue = new LinkedList<>();
        if (fullDirectoryPath.isDirectory()) {
            directoryQueue.add(fullDirectoryPath);
        }

        // BFS getting all file Uris.
        while (!directoryQueue.isEmpty()) {
            DocumentFile currentDir = directoryQueue.remove();
            for (DocumentFile file : currentDir.listFiles()) {
                if (file.isDirectory()) {
                    directoryQueue.add(file);
                } else if (file.isFile()) {
                    paths.add(file.getUri());
                }
            }
        }
        return paths;
    }

    public static void cleanLocalCache(VolumeList volumeList) {
        for (ChapterInfo tempCi : volumeList.chapterList) {
            String xml = GlobalConfig.loadFullFileFromSaveFolder("novel", tempCi.cid + ".xml");
            if (xml.isEmpty()) {
                return;
            }
            List<OldNovelContentParser.NovelContent> nc = OldNovelContentParser.NovelContentParser_onlyImage(xml);
            for (int i = 0; i < nc.size(); i++) {
                if (nc.get(i).type == OldNovelContentParser.NovelContentType.IMAGE) {
                    String imgFileName = GlobalConfig.generateImageFileNameByURL(nc.get(i).content);
                    deleteFile(
                            GlobalConfig.getFirstFullSaveFilePath() +
                                    GlobalConfig.imgsSaveFolderName + File.separator + imgFileName);
                    deleteFile(
                            GlobalConfig.getSecondFullSaveFilePath() +
                                    GlobalConfig.imgsSaveFolderName + File.separator + imgFileName);
                }
            }
            deleteFile(GlobalConfig.getFirstFullSaveFilePath(), "novel" + File.separator + tempCi.cid + ".xml");
            deleteFile(GlobalConfig.getSecondFullSaveFilePath(), "novel" + File.separator + tempCi.cid + ".xml");
        }
        volumeList.inLocal = false;
    }
}
