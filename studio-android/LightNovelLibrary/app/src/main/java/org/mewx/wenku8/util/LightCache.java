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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Light Cache
 * *
 * This class provide straight file operation functions.
 * Easy save file, read file and delete file.
 */
public class LightCache {
    private static final String TAG = LightCache.class.getSimpleName();

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
                e.printStackTrace();
            }
        }
        return null;
    }

    public static byte[] loadStream(InputStream inputStream) {
        try {
            // Hopefully to get the file size.
            int fileSize = inputStream.available();
            DataInputStream dis = new DataInputStream(inputStream);

            // read all
            byte[] bs = new byte[fileSize];
            if (dis.read(bs, 0, fileSize) == -1)
                return null;

            dis.close();
            inputStream.close();
            return bs;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean saveFile(String path, String fileName, byte[] bs, boolean forceUpdate) {
        final String fullPath = path + (path.charAt(path.length() - 1) != File.separatorChar ? File.separator : "") + fileName;
        return saveFile(fullPath, bs, forceUpdate);
    }

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

            try {
                // create file
                if (!file.createNewFile())
                    Log.d(TAG, "File existed or failed to create file: " + filepath);

                FileOutputStream out = new FileOutputStream(file); // truncate
                DataOutputStream dos = new DataOutputStream(out);

                // write all
                dos.write(bs);

                dos.close();
                out.close();
                Log.d(TAG, "Write successfully");
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
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
            ex.printStackTrace();
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

            byte bt[] = new byte[1024];
            int c;
            while ((c = from.read(bt)) > 0) fosTo.write(bt, 0, c);
            from.close();
            fosTo.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    // Copied from https://stackoverflow.com/a/36714242/4206925
    public static String getFilePath(Context context, Uri uri) {
        String selection = null;
        String[] selectionArgs = null;
        // Uri is different in versions after KITKAT (Android 4.4), we need to
        if (Build.VERSION.SDK_INT >= 19 && DocumentsContract.isDocumentUri(context.getApplicationContext(), uri)) {
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
            Cursor cursor;
            try {
                cursor = context.getContentResolver()
                        .query(uri, projection, selection, selectionArgs, null);
                int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                if (cursor.moveToFirst()) {
                    return cursor.getString(column_index);
                }
            } catch (Exception e) {
                e.printStackTrace();
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

        File[] list = fullDirectoryPath.listFiles();
        if (list == null) {
            return paths;
        }

        for (File f : list) {
            if (f.isDirectory()) {
                paths.addAll(listAllFilesInDirectory(f));
            } else {
                paths.add(Uri.fromFile(f));
            }
        }
        return paths;
    }

    public static List<Uri> listAllFilesInDirectory(DocumentFile fullDirectoryPath) {
        ArrayList<Uri> paths = new ArrayList<>();


        for (DocumentFile file : fullDirectoryPath.listFiles()) {
            if (file.isDirectory()) {
                paths.addAll(listAllFilesInDirectory(file));
            } else if (file.isFile()) {
                paths.add(file.getUri());
            }
        }
        return paths;
    }
}
