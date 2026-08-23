package org.mewx.wenku8.global;

import android.content.ContentValues;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.nostra13.universalimageloader.cache.disc.impl.UnlimitedDiscCache;
import com.nostra13.universalimageloader.core.DisplayImageOptions;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.nostra13.universalimageloader.core.ImageLoaderConfiguration;
import com.nostra13.universalimageloader.core.display.FadeInBitmapDisplayer;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.api.Wenku8API;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.global.api.Wenku8Parser;
import org.mewx.wenku8.network.LightUserSession;
import org.mewx.wenku8.util.CrashReporter;
import org.mewx.wenku8.util.LightCache;
import org.mewx.wenku8.network.LightNetwork;
import org.mewx.wenku8.util.LightTool;
import org.mewx.wenku8.util.SaveFileMigration;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * Created by MewX on 2015/1/20.
 *
 * 全局的设置类，负责所有设置的事务，以及全局的变量获取。
 */
@SuppressWarnings({"UnusedDeclaration"})
public class GlobalConfig {

    // online arguments
    public static final String blogPageUrl = "https://wenku8.mewx.org/";
    public static final String versionCheckUrl = "https://wenku8.mewx.org/version";
    public static final String noticeCheckSc = "https://wenku8.mewx.org/args/notice_sc";
    public static final String noticeCheckTc = "https://wenku8.mewx.org/args/notice_tc";

    // constants
    public static final String saveFolderName = "saves";
    public static final String imgsSaveFolderName = "imgs";
    public static final String customFolderName = "custom";
    private static final String saveSearchHistoryFileName = "search_history.wk8";
    private static final String saveReadSavesFileName = "read_saves.wk8";
    private static final String saveReadSavesV1FileName = "read_saves_v1.wk8";
    private static final String saveLocalBookshelfFileName = "bookshelf_local.wk8";
    private static final String saveSetting = "settings.wk8";
    private static final String saveUserAccountFileName = "cert.wk8"; // certification file
    private static final String saveUserAvatarFileName = "avatar.jpg";
    private static final String saveNoticeString = "notice.wk8"; // the notice cache from online
    private static int maxSearchHistory = 20; // default

    // vars
    private static boolean isInBookshelf = false;
    private static boolean isInLatest = false;
    private static boolean doLoadImage = true;
    private static Wenku8API.AppLanguage currentLang = Wenku8API.AppLanguage.SC;
    public static String pathPickedSave; // dir picker save path

    // static variables
    private static ArrayList<String> searchHistory = null;
    private static ArrayList<ReadSaves> readSaves = null; // deprecated
    private static ArrayList<Integer> bookshelf = null;
    private static ArrayList<ReadSavesV1> readSavesV1 = null; // deprecated
    private static ContentValues allSetting = null;


    /** Structures */
    public static class ReadSaves { // deprecated
        public int cid;
        public int pos; // last time scroll Y pos
        public int height; // last time scroll Y height
    }

    public static class ReadSavesV1 { // deprecated
        public int aid;
        public int vid;
        public int cid;
        public int lineId;
        public int wordId;
    }

    public enum SettingItems {
        version, // (int) 1
        language,
        menu_bg_id, // (int) 1-5 by system, 0 for user
        menu_bg_path, // (String) for user custom
        reader_font_path, // (String) path to ttf, "0" means default
        reader_font_size, // (int) sp (8 - 32)
        reader_line_distance, // (int) dp (0 - 32)
        reader_paragraph_distance, // (int) dp (0 - 48)
        reader_paragraph_edge_distance, // (int) dp (0 - 32)
        reader_background_path, // (String) path to an image, day mode only, "0" means default
        eink_mode, // (int) 1 - ON; 0 - OFF.
    }

    // sets and gets
    public static void setCurrentLang(Wenku8API.AppLanguage l) {
        currentLang = l;
        // FIXME
        Wenku8API.CurrentLang = currentLang;
        setToAllSetting(SettingItems.language, currentLang.toString());
        CrashReporter.setKey(CrashReporter.Keys.LANGUAGE, currentLang.toString());
    }

    public static Wenku8API.AppLanguage getCurrentLang() {
        String temp = getFromAllSetting(SettingItems.language);
        if(temp == null) {
            setToAllSetting(SettingItems.language, currentLang.toString());
        }
        else if(!temp.equals(currentLang.toString())) {
            if(temp.equals(Wenku8API.AppLanguage.SC.toString()))
                currentLang = Wenku8API.AppLanguage.SC;
            else if(temp.equals(Wenku8API.AppLanguage.TC.toString()))
                currentLang = Wenku8API.AppLanguage.TC;
            else
                currentLang = Wenku8API.AppLanguage.SC;
        }
        // FIXME
        Wenku8API.CurrentLang = currentLang;
        return currentLang;
    }

    public static boolean isEinkModeEnabled() {
        String einkMode = getFromAllSetting(SettingItems.eink_mode);
        return einkMode != null && Integer.parseInt(einkMode) == 1;
    }

    public static void initImageLoader(Context context) {
        UnlimitedDiscCache localUnlimitedDiscCache = new UnlimitedDiscCache(
                new File(GlobalConfig.getDefaultStoragePath() + "cache"),
                // FIXME: these imgs folders are actually no in use.
                new File(context.getCacheDir() + File.separator + "imgs"));
        DisplayImageOptions localDisplayImageOptions = new DisplayImageOptions.Builder()
                .resetViewBeforeLoading(true)
                .cacheOnDisk(true)
                .cacheInMemory(true)
                .bitmapConfig(Bitmap.Config.RGB_565)
                .resetViewBeforeLoading(true)
                .displayer(new FadeInBitmapDisplayer(250)).build();
        ImageLoaderConfiguration localImageLoaderConfiguration = new ImageLoaderConfiguration.Builder(context)
                .diskCache(localUnlimitedDiscCache)
                .defaultDisplayImageOptions(localDisplayImageOptions).build();
        ImageLoader.getInstance().init(localImageLoaderConfiguration);
    }

    // settings
    public static String getOpensourceLicense() {
        InputStream is = MyApp.getContext().getResources().openRawResource(R.raw.license);
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            CrashReporter.recordException("GlobalConfig.getOpensourceLicense", e);
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                CrashReporter.recordException("GlobalConfig.getOpensourceLicense", e);
            }
        }
        return sb.toString();
    }



    /**
     * 设置第一存储路径的合法性（第一路径可以只有设置）
     * @param available true-合法可以使用; false-不能使用，只能只用第二路径
     */
    public static void setExternalStoragePathAvailable(boolean available) {
        StorageRoots.setExternalAvailable(available);
    }

    public static String getDefaultStoragePath() {
        return StorageRoots.primary();
    }

    // TODO: get rid of this shortcut.
    public static String getBackupStoragePath() {
        return StorageRoots.backup();
    }

    public static boolean doCacheImage() {
        // for non-image mode
        return doLoadImage; // when cache, cache images
    }

    public static int getShowTextSize() {
        return 18; // in "sp"
    }

    public static int getShowTextPaddingTop() {
        return 48; // in "dp"
    }

    public static int getShowTextPaddingLeft() {
        return 32; // in "dp"
    }

    public static int getShowTextPaddingRight() {
        return 32; // in "dp"
    }

    public static int getTextLoadWay() {
        // 0 - Always load from online, when WLAN available
        // 1 - Load locally first, then considerate online
        // 2 - In bookshelf do (1), else do (0)

        return 2;
    }

    // TODO: get rid of those ugly shortcuts.
    public static String getFirstFullSaveFilePath() {
        return getDefaultStoragePath() + saveFolderName + File.separator;
    }

    public static String getSecondFullSaveFilePath() {
        return getBackupStoragePath() + saveFolderName + File.separator;
    }

    public static String getFirstFullUserAccountSaveFilePath() {
        return getFirstFullSaveFilePath() + saveUserAccountFileName;
    }

    public static String getSecondFullUserAccountSaveFilePath() {
        return getSecondFullSaveFilePath() + saveUserAccountFileName;
    }

    public static String getFirstUserAvatarSaveFilePath() {
        return getFirstFullSaveFilePath() + saveUserAvatarFileName;
    }

    public static String getSecondUserAvatarSaveFilePath() {
        return getSecondFullSaveFilePath() + saveUserAvatarFileName;
    }

    /**
     * Extract image name.
     * @param url <!--image-->http://pic.wenku8.cn/pictures/1/1305/41759/50471.jpg<!--image-->
     * @return pictures113054175950471.jpg
     */
    public static String generateImageFileNameByURL(String url) {
        String[] s = url.split("/");
        StringBuilder result = new StringBuilder();
        boolean canStart = false;
        for (String temp : s) {
            if (!canStart && temp.contains("."))
                canStart = true; // judge canStart first
            else if (canStart)
                result.append(temp);
        }
        return result.toString();
    }

    @NonNull
    private static String loadFullSaveFileContent(@NonNull String FileName) {
        // get full file in file save path
        String h = "";
        if (LightCache.testFileExist(getDefaultStoragePath() + saveFolderName + File.separator + FileName)) {
            try {
                byte[] b = LightCache.loadFile(getDefaultStoragePath() + saveFolderName + File.separator + FileName);
                if(b == null) return "";
                h = new String(b, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                CrashReporter.recordException("GlobalConfig.loadFullSaveFileContent", e);
            }

        } else if (LightCache.testFileExist(getBackupStoragePath() + saveFolderName + File.separator + FileName)) {
            try {
                byte[] b = LightCache.loadFile(getBackupStoragePath() + saveFolderName + File.separator + FileName);
                if(b == null) return "";
                h = new String(b, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                CrashReporter.recordException("GlobalConfig.loadFullSaveFileContent", e);
            }

        }
        return h;
    }

    private static boolean writeFullSaveFileContent(String FileName, @NonNull String s) {
        // process path and filename
        String path = "", fileName = FileName;
        if (FileName.contains(File.separator)) {
            path = FileName.substring(0, FileName.lastIndexOf(File.separator));
            fileName = FileName.substring(FileName.lastIndexOf(File.separator)
                    + File.separator.length(), FileName.length());
        }

        // write save file in save path
        if (!LightCache.saveFile(getDefaultStoragePath() + saveFolderName + File.separator + path, fileName, s.getBytes(), true)) // if not exist
            return LightCache.saveFile(getBackupStoragePath() + saveFolderName
                    + File.separator + path, fileName, s.getBytes(), true);
        return true;
    }

    @NonNull
    public static String loadFullFileFromSaveFolder(String subFolderName, String fileName) {
        return loadFullSaveFileContent(subFolderName + File.separator
                + fileName);
    }

    public static boolean writeFullFileIntoSaveFolder(String subFolderName, String fileName, String s) {
        // input no separator
        return writeFullSaveFileContent(subFolderName + File.separator
                + fileName, s);
    }

    /** Name of the cached chapter index for a novel, relative to the "intro" save subfolder. */
    @NonNull
    public static String getVolumeIndexFileName(int aid) {
        return aid + "-volume.xml";
    }

    /**
     * Caches the chapter index of a novel so a reader started later can rebuild it from
     * {@code aid} and {@code vid} alone.
     *
     * <p>The readers take the volume as ids rather than as a serialized object, which is what
     * keeps a long series from overflowing the Binder transaction buffer and what lets a reader
     * restore itself after process death. Both depend on this file being present, so it is
     * written whenever a novel index arrives from the network, not only when the novel is
     * added to the bookshelf or downloaded.
     */
    public static boolean cacheVolumeIndex(int aid, @NonNull String volumeXml) {
        return writeFullFileIntoSaveFolder("intro", getVolumeIndexFileName(aid), volumeXml);
    }

    /**
     * Loads one volume of a novel out of the index cached by {@link #cacheVolumeIndex}, or null
     * when the index is missing, unparseable, or holds no volume with that {@code vid}.
     *
     * <p>Null is an ordinary outcome here rather than an error: the cache is deleted when a
     * novel leaves the bookshelf, so a reader restored by the system long after the fact can
     * legitimately find nothing to read.
     */
    @Nullable
    public static VolumeList loadCachedVolume(int aid, int vid) {
        String xml = loadFullFileFromSaveFolder("intro", getVolumeIndexFileName(aid));
        if (xml.isEmpty()) {
            return null;
        }
        return Wenku8Parser.findVolumeByVid(Wenku8Parser.getVolumeList(xml), vid);
    }

    /** Book shelf */
    public static void loadLocalBookShelf() {
        // Format:
        // aid||aid||aid
        // the file just saves the aid list
        bookshelf = new ArrayList<>();

        String h = loadFullSaveFileContent(saveLocalBookshelfFileName);
        String[] p = h.split("\\|\\|"); // regular expression
        for (String t : p) {
            if (t.isEmpty())
                continue;

            // A token that does not parse is dropped rather than thrown on. This method is
            // reached lazily from getLocalBookshelfList and testInLocalBookshelf, with no catch
            // anywhere above it, and the bookshelf tab is the first screen -- so throwing here
            // meant a file damaged by a partial write took out the app on launch, with nothing
            // the user could do but clear app data. This file is rewritten on every add and
            // remove, so that is a reachable state rather than a hypothetical one.
            //
            // Dropping the entry rather than rejecting the file is deliberate: rejecting it
            // would silently empty the bookshelf, which is a worse outcome than losing the one
            // novel whose id was corrupted. Reported so the real frequency is visible, since
            // until now this failed as a crash and afterwards it would fail silently.
            try {
                bookshelf.add(Integer.valueOf(t));
            } catch (NumberFormatException e) {
                CrashReporter.recordException("GlobalConfig.loadLocalBookShelf", e);
            }
        }
    }

    public static void writeLocalBookShelf() {
        if (bookshelf == null)
            loadLocalBookShelf();

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < bookshelf.size(); i++) {
            if (i != 0)
                sb.append("||");
            sb.append(bookshelf.get(i));
        }
        writeFullSaveFileContent(saveLocalBookshelfFileName, sb.toString());
    }

    public static void addToLocalBookshelf(int aid) {
        if (bookshelf == null)
            loadLocalBookShelf();

        if (!bookshelf.contains(aid))
            bookshelf.add(0, aid); // add to the first place

        writeLocalBookShelf();
    }

    public static void removeFromLocalBookshelf(int aid) {
        if (bookshelf == null) {
            loadLocalBookShelf();
        }

        int i = bookshelf.indexOf(aid);
        if (i != -1) {
            bookshelf.remove(i);
        }

        writeLocalBookShelf();
    }

    public static ArrayList<Integer> getLocalBookshelfList() {
        if (bookshelf == null)
            loadLocalBookShelf();

        return bookshelf;
    }

    public static boolean testInLocalBookshelf(int aid) {
        if (bookshelf == null) {
            loadLocalBookShelf();
        }

        return bookshelf.contains(aid);
    }

    public static void moveBookToTheTopOfBookshelf(int aid) {
        // Every sibling loads lazily before touching the static; this one did not, so it was an
        // NPE waiting for a caller that reached it first. Latent rather than live, but the
        // cheapest possible fix for root cause 2 in miniature.
        if (bookshelf == null) {
            loadLocalBookShelf();
        }

        int i = bookshelf.indexOf(aid);
        if (i == -1) {
            return;
        }

        bookshelf.remove(i);
        bookshelf.add(0, aid);

        writeLocalBookShelf();
    }

    public static boolean testInBookshelf() {
        return isInBookshelf;
    }

    public static void EnterBookshelf() {
        isInBookshelf = true;
    }

    public static void LeaveBookshelf() {
        isInBookshelf = false;
    }
    public static boolean testInLatest() {
        return isInLatest;
    }

    public static void EnterLatest() {
        isInLatest = true;
    }

    public static void LeaveLatest() {
        isInLatest = false;
    }


    /** search history */
    public static void readSearchHistory() {
        // always initial empty
        searchHistory = new ArrayList<>();

        // read history from file, if not exist, create.
        String h = loadFullSaveFileContent(saveSearchHistoryFileName);

        // separate the read string
        int i = 0, temp;
        while (true) {
            temp = h.indexOf("[", i); // find '['
            if (temp == -1)
                break;

            i = temp + 1;
            temp = h.indexOf("]", i); // get ']'
            if (temp == -1)
                break;

            // ok, get a part
            searchHistory.add(h.substring(i, temp));
        }
    }

    public static void writeSearchHistory() {
        // [0what][1what]...
        StringBuilder temp = new StringBuilder();
        for (int i = 0; i < searchHistory.size(); i++) {
            temp.append("[").append(searchHistory.get(i)).append("]");
        }

        // write file
        writeFullSaveFileContent(saveSearchHistoryFileName, temp.toString());
    }

    public static ArrayList<String> getSearchHistory() {
        if (searchHistory == null)
            readSearchHistory();
        return searchHistory;
    }

    public static void addSearchHistory(String record) {
        // record begins with a number, which represent its type
        if (searchHistory == null)
            readSearchHistory();

        if (searchHistory.contains("["))
            return; // harmful

        // remove same thing
        while(true) {
            int pos = searchHistory.indexOf(record);

            if(pos < 0)
                break;
            else
                searchHistory.remove(pos);
        }

        while (searchHistory.size() >= maxSearchHistory)
            searchHistory.remove(maxSearchHistory - 1); // remove the last
        searchHistory.add(0, record); // add to the first place

        writeSearchHistory(); // save history file
    }
    public static void deleteSearchHistory(String record) {
        // record begins with a number, which represent its type
        if (searchHistory == null)
            readSearchHistory();

        if (searchHistory.contains("["))
            return; // harmful

        // remove same thing
        while(true) {
            int pos = searchHistory.indexOf(record);

            if(pos < 0)
                break;
            else
                searchHistory.remove(pos);
        }

        writeSearchHistory(); // save history file
    }

    @Deprecated
    public static void onSearchClicked(int index) {
        if (index >= searchHistory.size())
            return;

        String temp = searchHistory.get(index);
        searchHistory.remove(index);
        searchHistory.add(0, temp);

        writeSearchHistory(); // save history file
    }

    public static void clearSearchHistory() {
        searchHistory = new ArrayList<>();
        writeSearchHistory(); // save history file
    }

    public static int getMaxSearchHistory( ) {
        return maxSearchHistory;
    }

    public static void setMaxSearchHistory( int size ) {
        if(size > 0)
            maxSearchHistory = size;
    }


    /** Read Saves (Old) */
    public static void loadReadSaves() {
        // Format:
        // cid,,pos,,height||cid,,pos,,height
        // just use split function
        readSaves = new ArrayList<>();

        // read history from file, if not exist, create.
        String h = loadFullSaveFileContent(saveReadSavesFileName);

        // split string h
        String[] p = h.split("\\|\\|"); // regular expression
        OutLoop:
        for (String temp : p) {
            Log.v("MewX", temp);
            String[] parts = temp.split(",,");
            if (parts.length != 3)
                continue;

            // A record with an unparseable field is dropped rather than thrown on. This is item 9
            // in a second location, and this loader is the one that still had it: loadReadSavesV1
            // below does the identical job and validates every field before parsing, while this
            // one validated only the field count and then parsed regardless.
            //
            // It was reachable and it was fatal. getReadSavesRecord is called from
            // VerticalReaderActivity's scroll-restore runnable and from an unguarded
            // onPostExecute, and addReadSavesRecord from onPause -- none of which catch. So a
            // record damaged by a partial write crashed that reader when opening a chapter and
            // again when leaving one, every time, and nothing in the app lets a user delete the
            // file to escape it.
            //
            // Dropping the record rather than rejecting the file is the same trade item 9 settled
            // for the bookshelf: one chapter loses its position instead of every chapter losing
            // it. Reported so the real frequency is visible, since this used to fail as a crash
            // and would otherwise now fail silently.
            for (String str : parts) {
                if (!LightTool.isInteger(str)) {
                    CrashReporter.recordException("GlobalConfig.loadReadSaves",
                            new NumberFormatException("dropped unparseable record: " + temp));
                    continue OutLoop;
                }
            }

            ReadSaves rs = new ReadSaves();
            rs.cid = Integer.valueOf(parts[0]);
            rs.pos = Integer.valueOf(parts[1]);
            rs.height = Integer.valueOf(parts[2]);
            readSaves.add(rs);
        }
    }

    public static void writeReadSaves() {
        if (readSaves == null)
            loadReadSaves();

        StringBuilder t = new StringBuilder();
        for (int i = 0; i < readSaves.size(); i++) {
            if (i != 0)
                t.append("||");
            t.append(readSaves.get(i).cid).append(",,")
                    .append(readSaves.get(i).pos).append(",,")
                    .append(readSaves.get(i).height);
        }

        writeFullSaveFileContent(saveReadSavesFileName, t.toString());
    }

    public static void addReadSavesRecord(int c, int p, int h) {
        if (p < 100)
            return; // no necessary to save it

        if (readSaves == null)
            loadReadSaves();

        // judge if exist, and if legal, update it
        for (int i = 0; i < readSaves.size(); i++) {
            if (readSaves.get(i).cid == c) {
                // judge if need to update
                readSaves.get(i).pos = p;
                readSaves.get(i).height = h;

                writeReadSaves();
                return;
            }
        }

        // new record
        ReadSaves rs = new ReadSaves();
        rs.cid = c;
        rs.pos = p;
        rs.height = h;
        readSaves.add(rs);

        writeReadSaves();
    }

    public static int getReadSavesRecord(int c, int h) {
        if (readSaves == null)
            loadReadSaves();

        for (int i = 0; i < readSaves.size(); i++) {
            if (readSaves.get(i).cid == c) {
                // return h * readSaves.get(i).pos / readSaves.get(i).height;
                return readSaves.get(i).pos;
            }
        }

        // by default
        return 0;
    }


    /** Read Saves (V1) */
    public static void loadReadSavesV1() {
        // Format:
        // cid,,pos,,height||cid,,pos,,height
        // just use split function
        readSavesV1 = new ArrayList<>();

        // read history from file, if not exist, create.
        String h = loadFullSaveFileContent(saveReadSavesV1FileName);

        // split string h
        String[] p = h.split("\\|\\|"); // regular expression
        OutLoop:
        for (String temp : p) {
            Log.v("MewX", temp);
            String[] parts = temp.split(":"); // \\:
            if (parts.length != 5)
                continue;

            // judge legal
            for(String str : parts) if(!LightTool.isInteger(str)) continue OutLoop;

            // add to list
            ReadSavesV1 rs = new ReadSavesV1();
            rs.aid = Integer.valueOf(parts[0]);
            rs.vid = Integer.valueOf(parts[1]);
            rs.cid = Integer.valueOf(parts[2]);
            rs.lineId = Integer.valueOf(parts[3]);
            rs.wordId = Integer.valueOf(parts[4]);
            readSavesV1.add(rs);
        }
    }

    public static void writeReadSavesV1() {
        if (readSavesV1 == null)
            loadReadSavesV1();

        StringBuilder t = new StringBuilder();
        for (int i = 0; i < readSavesV1.size(); i++) {
            if (i != 0)
                t.append("||");
            t.append(readSavesV1.get(i).aid).append(":")
                    .append(readSavesV1.get(i).vid).append(":")
                    .append(readSavesV1.get(i).cid).append(":")
                    .append(readSavesV1.get(i).lineId).append(":")
                    .append(readSavesV1.get(i).wordId);
        }
        writeFullSaveFileContent(saveReadSavesV1FileName, t.toString());
    }

    public static void addReadSavesRecordV1(int aid, int vid, int cid, int lineId, int wordId) {
        if (readSavesV1 == null)
            loadReadSavesV1();

        // judge if exist, and if legal, update it
        for (int i = 0; i < readSavesV1.size(); i ++) {
            if (readSavesV1.get(i).aid == aid) {
                // need to update
                readSavesV1.get(i).vid = vid;
                readSavesV1.get(i).cid = cid;
                readSavesV1.get(i).lineId = lineId;
                readSavesV1.get(i).wordId = wordId;
                writeReadSavesV1();
                return;
            }
        }

        // new record
        ReadSavesV1 rs = new ReadSavesV1();
        rs.aid = aid;
        rs.vid = vid;
        rs.cid = cid;
        rs.lineId = lineId;
        rs.wordId = wordId;
        readSavesV1.add(rs);
        writeReadSavesV1();
    }

    public static void removeReadSavesRecordV1(int aid) {
        if (readSavesV1 == null)
            loadReadSavesV1();

        int i = 0;
        for( ; i < readSavesV1.size(); i ++) {
            if(readSavesV1.get(i).aid == aid) break;
        }
        if(i < readSavesV1.size()) readSavesV1.remove(i);
        writeReadSavesV1();
    }

    @Nullable
    public static ReadSavesV1 getReadSavesRecordV1(int aid) {
        if (readSavesV1 == null)
            loadReadSavesV1();

        for (int i = 0; i < readSavesV1.size(); i ++) {
            if (readSavesV1.get(i).aid == aid) return readSavesV1.get(i);
        }
        return null;
    }

    /** All settings */
    public static void loadAllSetting() {
        // Verify which storage source to user.
        StorageRoots.setInternalOnly(SaveFileMigration.migrationCompleted());

        // Loads all settings.
        allSetting = new ContentValues();
        String h = loadFullSaveFileContent(saveSetting);

        String[] sets = h.split("\\|\\|\\|\\|");
        for(String set : sets) {
            String[] temp = set.split("::::");
            if(temp.length != 2 || temp[0] == null || temp[0].isEmpty() || temp[1] == null || temp[1].isEmpty()) continue;

            allSetting.put(temp[0], temp[1]);
        }

        // Updates settings version.
        String version = getFromAllSetting(SettingItems.version);
        if(version == null || version.isEmpty()) {
            setToAllSetting(SettingItems.version, "1");
        }
        // Else, reserved for future settings migration.

        // Crash report context. Both of these change which files get read and which parser path
        // runs, so a report without them is hard to reproduce. Read straight out of allSetting
        // rather than via getCurrentLang(), which writes the settings file back on first run.
        String lang = allSetting.getAsString(SettingItems.language.toString());
        CrashReporter.setKey(CrashReporter.Keys.LANGUAGE, lang == null ? currentLang.toString() : lang);
        CrashReporter.setKey(CrashReporter.Keys.STORAGE_MODE,
                StorageRoots.isInternalOnly() || !StorageRoots.isExternalAvailable()
                        ? "internal" : "external");
        CrashReporter.log("GlobalConfig#loadAllSetting completed");
    }

    public static void saveAllSetting() {
        if(allSetting == null) loadAllSetting();

        StringBuilder result = new StringBuilder();
        for( String key : allSetting.keySet() ) {
            if(!result.toString().isEmpty()) result.append("||||");
            result.append(key).append("::::").append(allSetting.getAsString(key));
        }
        writeFullSaveFileContent(saveSetting, result.toString());
    }

    @Nullable
    public static String getFromAllSetting(SettingItems name) {
        if(allSetting == null) loadAllSetting();
        return allSetting.getAsString(name.toString());
    }

    public static void setToAllSetting(SettingItems name, String value) {
        if(allSetting == null) loadAllSetting();
        if(name != null && value != null) {
            allSetting.remove(name.toString());
            allSetting.put(name.toString(), value);
            saveAllSetting();
        }
    }


    /* Novel content */
    /**
     * Async gets image url and download to save folder's image folder.
     *
     * @param url full http url of target image
     * @return if file finally exist, if already exist before saving, still
     *          return true; if finally the file does not exist, return false.
     */
    public static boolean saveNovelContentImage(String url) {
        String imgFileName = generateImageFileNameByURL(url);
        String defaultFullPath = getFirstFullSaveFilePath() + imgsSaveFolderName + File.separator + imgFileName;
        String fallbackFullPath = getSecondFullSaveFilePath() + imgsSaveFolderName + File.separator + imgFileName;

        if (!LightCache.testFileExist(defaultFullPath) && !LightCache.testFileExist(fallbackFullPath)) {
            // neither of the file exist
            byte[] fileContent = LightNetwork.LightHttpDownload(url);
            if (fileContent == null)
                return false; // network error

            return LightCache.saveFile(defaultFullPath, fileContent, true)
                    || LightCache.saveFile(fallbackFullPath, fileContent, true);
        }
        return true; // file exist
    }

    public static boolean saveNovelCoverImage(@NonNull String fileName, @NonNull byte[] fileContent) {
        String defaultFullPath = getFirstFullSaveFilePath() + imgsSaveFolderName + File.separator + fileName;
        String fallbackFullPath = getSecondFullSaveFilePath() + imgsSaveFolderName + File.separator + fileName;

        if (!LightCache.testFileExist(defaultFullPath) && !LightCache.testFileExist(fallbackFullPath)) {
            // neither of the file exist
            return LightCache.saveFile(defaultFullPath, fileContent, true)
                    || LightCache.saveFile(fallbackFullPath, fileContent, true);
        }
        return true; // file exist
    }

    /**
     * Gets existing local saving path of target image.
     *
     * @param fileName just need the fileName
     * @return direct fileName or just null
     */
    public static String getExistingNovelContentImagePath(String fileName) {
        String defaultFullPath = getFirstFullSaveFilePath() + imgsSaveFolderName + File.separator + fileName;
        String fallbackFullPath = getSecondFullSaveFilePath() + imgsSaveFolderName + File.separator + fileName;

        if (LightCache.testFileExist(defaultFullPath)) {
            return defaultFullPath;
        } else if (LightCache.testFileExist(fallbackFullPath)) {
            return fallbackFullPath;
        } else {
            return null;
        }
    }


    public static boolean isNetworkAvailable(Context context) {
        ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm != null) {
            // connected
            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null;
        }
        return false; // not connected
    }

    /* notice */
    @NonNull
    public static String loadSavedNotice() {
        return loadFullSaveFileContent(saveNoticeString);
    }

    public static void writeTheNotice(@NonNull String noticeStr) {
        writeFullSaveFileContent(saveNoticeString, noticeStr);
    }

    public static boolean loadUserInfoSet() {
        byte[] bytes;
        if(LightCache.testFileExist(getFirstFullUserAccountSaveFilePath())) {
            bytes = LightCache.loadFile(getFirstFullUserAccountSaveFilePath());
        }
        else if(LightCache.testFileExist(getSecondFullUserAccountSaveFilePath())) {
            bytes = LightCache.loadFile(getSecondFullUserAccountSaveFilePath());
        }
        else {
            return false; // file read failed
        }

        try {
            Log.d("MewX", new String(bytes, "UTF-8"));
            // TODO: decouple
            LightUserSession.decAndSetUserFile(new String(bytes, "UTF-8"));
        } catch (Exception e) {
            CrashReporter.recordException("GlobalConfig.loadUserInfoSet", e);
            return false; // exception
        }

        return true;
    }

    public static boolean saveUserInfoSet() {
        LightCache.saveFile(getFirstFullUserAccountSaveFilePath(), LightUserSession.encUserFile().getBytes(), true);
        if (!LightCache.testFileExist(getFirstFullUserAccountSaveFilePath())) {
            LightCache.saveFile(getSecondFullUserAccountSaveFilePath(), LightUserSession.encUserFile().getBytes(), true);
            return LightCache.testFileExist(getSecondFullUserAccountSaveFilePath());
        }
        return true;
    }
}
