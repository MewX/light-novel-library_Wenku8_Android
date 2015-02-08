package org.mewx.lightnovellibrary.global;

import android.content.Context;
import android.os.Environment;
import android.support.v4.app.Fragment;

import org.mewx.lightnovellibrary.global.api.Wenku8API;
import org.mewx.lightnovellibrary.util.LightCache;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * Created by MewX on 2015/1/20.
 */
public class GlobalConfig {

    private static final String saveFolderName = "saves";
    public static final String imgsSaveFolderName = "imgs";
    private static final String saveSearchHistoryFileName = "search_history.wk8";
    private static final String saveReadSavesFileName = "read_saves.wk8";
    private static final String saveLocalBookshelfFileName = "bookshelf_local.wk8";
    private static int maxSearchHistory = 10; // default

    // vars
    private static Wenku8API.LANG currentLang = Wenku8API.LANG.SC;
    private static Fragment currentFragment;
    private static Context context;


    // static variables
    private static ArrayList<String> searchHistory = null;
    //private static ArrayList<ReadSaves> readSaves = null;
    private static ArrayList<Integer> bookshelf = null;

    // debug info
    public static boolean inDebugMode() {
        return true; // set log out operation
    }

    // sets and gets
    public static Wenku8API.LANG getCurrentLang() {
        return currentLang;
    }

    public static void setCurrentLang(Wenku8API.LANG l) {
        currentLang = l;
        return;
    }

    public static void setCurrentFragment(Fragment f){
        currentFragment = f;
        return;
    }

    public static Fragment getCurrentFragment(){
        return currentFragment;
    }

    public static void setContext(Context c){
        context = c;
        return;
    }

    public static Context getContext(){
        return context;
    }

    public static String getFirstStoragePath() {
        return Environment.getExternalStorageDirectory() + File.separator
                + "wenku8" + File.separator;
    }

    public static String getSecondStoragePath() {
        return getContext().getFilesDir() + File.separator;
    }

    public static boolean doCacheImage() {
        return true; // when cache, cache images
    }

    public static int getShowTextSize() {
        return 18; // in "sp"
    }

    public static int getShowTextPaddingTop() {
        return 16; // in "dp"
    }

    public static int getShowTextPaddingLeft() {
        return 16; // in "dp"
    }

    public static int getShowTextPaddingRight() {
        return 16; // in "dp"
    }

    public static int getTextLoadWay() {
        // 0 - Always load from online, when WLAN available
        // 1 - Load locally first, then considerate online
        // 2 - In bookshelf do (1), else do (0)

        return 2;
    }

    public static String getFirstFullSaveFilePath() {
        return getFirstStoragePath() + saveFolderName + File.separator;
    }

    public static String getSecondFullSaveFilePath() {
        return getSecondStoragePath() + saveFolderName + File.separator;
    }

    public static String generateImageFileNameByURL(String url) {
        String[] s = url.split("/");
        String result = "";
        boolean canStart = false;
        for (String temp : s) {
            if (!canStart && temp.indexOf(".") != -1)
                canStart = true; // judge canStart first
            else if (canStart)
                result += temp;
        }
        return result;
    }

    private static String loadFullSaveFileContent(String FileName) {
        // get full file in file save path
        String h = "";
        if (LightCache.testFileExist(getFirstStoragePath() + saveFolderName
                + File.separator + FileName)) {
            try {
                h = new String(LightCache.loadFile(getFirstStoragePath()
                        + saveFolderName + File.separator + FileName), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        } else if (LightCache.testFileExist(getSecondStoragePath()
                + saveFolderName + File.separator + FileName)) {
            try {
                h = new String(LightCache.loadFile(getSecondStoragePath()
                        + saveFolderName + File.separator + FileName), "UTF-8");
            } catch (UnsupportedEncodingException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }

        } else {
            // so, there is no search history file, need to create
            // nothing need to put here
        }
        return h;
    }

    private static boolean writeFullSaveFileContent(String FileName, String s) {
        // process path and filename
        String tp = "", tf = FileName;
        if (FileName.indexOf(File.separator) != -1) {
            tp = FileName.substring(0, FileName.lastIndexOf(File.separator));
            tf = FileName.substring(FileName.lastIndexOf(File.separator)
                    + File.separator.length(), FileName.length());
        }

        // write save file in save path
        if (false == LightCache.saveFile(getFirstStoragePath() + saveFolderName
                + File.separator + tp, tf, s.getBytes(), true)) // if not exist
            return LightCache.saveFile(getSecondStoragePath() + saveFolderName
                    + File.separator + tp, tf, s.getBytes(), true);
        return true;
    }

    public static String loadFullFileFromSaveFolder(String subFolderName,
                                                    String fileName) {
        return loadFullSaveFileContent(subFolderName + File.separator
                + fileName);
    }

    public static boolean writeFullFileIntoSaveFolder(String subFolderName,
                                                      String fileName, String s) {
        // input no separator
        return writeFullSaveFileContent(subFolderName + File.separator
                + fileName, s);
    }
}
