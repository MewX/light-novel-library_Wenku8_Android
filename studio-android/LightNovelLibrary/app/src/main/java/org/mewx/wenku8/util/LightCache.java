package org.mewx.wenku8.util;

import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

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
        File file = new File(path);
        if (file.exists()) {
            if (file.length() == 0)
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
            int fileSize = (int) file.length(); // get file size
            try {
                FileInputStream in = new FileInputStream(file);
                DataInputStream dis = new DataInputStream(in);

                // read all
                byte[] bs = new byte[fileSize];
                if (dis.read(bs, 0, fileSize) == -1)
                    return null; // error

                dis.close();
                in.close();
                return bs;
            } catch (IOException e) {
                e.printStackTrace();
            }
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
        File toFile = new File(to);
        if (!fromFile.exists() || !fromFile.isFile() || !fromFile.canRead() || toFile.exists() && !forceWrite)
            return;

        if (!toFile.getParentFile().exists() && !toFile.getParentFile().mkdirs())
            Log.d(TAG, "Failed to create parent dirs for target file: " + to);
        if (toFile.exists() && forceWrite && !toFile.delete())
            Log.d(TAG, "Failed to create or delete target file: " + to);

        try {
            java.io.FileInputStream fosFrom = new java.io.FileInputStream(fromFile);
            java.io.FileOutputStream fosTo = new FileOutputStream(toFile);

            byte bt[] = new byte[1024];
            int c;
            while ((c = fosFrom.read(bt)) > 0) fosTo.write(bt, 0, c);
            fosFrom.close();
            fosTo.close();
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
