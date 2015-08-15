package org.mewx.lightnovellibrary.component;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

import org.mewx.lightnovellibrary.api.Wenku8Interface;
import org.mewx.lightnovellibrary.util.LightCache;
import org.mewx.lightnovellibrary.util.LightNetwork;

import android.os.Environment;
import android.util.Log;

public class GlobalConfig {
	/** constances */
	private static final String saveFolderName = "saves";
	public static final String imgsSaveFolderName = "imgs";
	private static final String saveSearchHistoryFileName = "search_history.wk8";
	private static final String saveReadSavesFileName = "read_saves.wk8";
	private static final String saveLocalBookshelfFileName = "bookshelf_local.wk8";
	private static int maxSearchHistory = 10; // default

	/** static variables */
	private static ArrayList<String> searchHistory = null;
	private static ArrayList<ReadSaves> readSaves = null;
	private static ArrayList<Integer> bookshelf = null;

	/** Structures */
	public static class ReadSaves {
		public int cid;
		public int pos; // last time scroll Y pos
		public int height; // last time scroll Y height
	}

	/** Util */
	GlobalConfig() {
		return;
	}

	public static String getFirstStoragePath() {
		return Environment.getExternalStorageDirectory() + File.separator
				+ "wenku8" + File.separator;
	}

	public static String getSecondStoragePath() {
		return MyApp.getContext().getFilesDir() + File.separator;
	}

	public static boolean doCacheImage() {
		return true; // when cache, cache images
	}

	public static Wenku8Interface.LANG getFetchLanguage() {
		return Wenku8Interface.LANG.SC;
		// return Wenku8Interface.LANG.TC;
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

	/** search history */
	public static void readSearchHistory() {
		// always initial empty
		searchHistory = new ArrayList<String>();

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
		return;
	}

	public static void writeSearchHistory() {
		// [0what][1what]...
		String temp = "";
		for (int i = 0; i < searchHistory.size(); i++) {
			temp += "[" + searchHistory.get(i) + "]";
		}

		// write file
		writeFullSaveFileContent(saveSearchHistoryFileName, temp);

		return;
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

		if (searchHistory.indexOf("[") != -1)
			return; // harmful

		while (searchHistory.size() >= maxSearchHistory)
			searchHistory.remove(maxSearchHistory - 1); // remove the last
		searchHistory.add(0, record); // add to the first place

		writeSearchHistory(); // save history file
		return;
	}

	public static void onSearchClicked(int index) {
		if (index >= searchHistory.size())
			return;

		String temp = searchHistory.get(index);
		searchHistory.remove(index);
		searchHistory.add(0, temp);

		writeSearchHistory(); // save history file
		return;
	}

	public static void clearSearchHistory() {
		searchHistory = new ArrayList<String>();
		writeSearchHistory(); // save history file
		return;
	}

	/** Read Saves */
	public static void loadReadSaves() {
		// Format:
		// cid,,pos,,height||cid,,pos,,height
		// just use split function
		readSaves = new ArrayList<ReadSaves>();

		// read history from file, if not exist, create.
		String h = loadFullSaveFileContent(saveReadSavesFileName);

		// split string h
		String[] p = h.split("\\|\\|"); // regular expression
		for (String temp : p) {
			Log.v("MewX", temp);
			String[] parts = temp.split(",,");
			if (parts.length != 3)
				continue;

			ReadSaves rs = new ReadSaves();
			rs.cid = new Integer(parts[0]);
			rs.pos = new Integer(parts[1]);
			rs.height = new Integer(parts[2]);
			readSaves.add(rs);
		}
	}

	public static void writeReadSaves() {
		if (readSaves == null)
			loadReadSaves();

		String t = "";
		for (int i = 0; i < readSaves.size(); i++) {
			if (i != 0)
				t += "||";
			t += readSaves.get(i).cid + ",," + readSaves.get(i).pos + ",,"
					+ readSaves.get(i).height;
		}

		writeFullSaveFileContent(saveReadSavesFileName, t);
		return;
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
		return;
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

	/** Book shelf */
	public static void loadLocalBookShelf() {
		// Format:
		// aid||aid||aid
		// the file just saves the aid list
		bookshelf = new ArrayList<Integer>();

		String h = loadFullSaveFileContent(saveLocalBookshelfFileName);
		String[] p = h.split("\\|\\|"); // regular expression
		for (String t : p) {
			if (t.equals(""))
				continue;
			bookshelf.add(new Integer(t));
		}

		return;
	}

	public static void writeLocalBookShelf() {
		if (bookshelf == null)
			loadLocalBookShelf();

		String s = "";
		for (int i = 0; i < bookshelf.size(); i++) {
			if (i != 0)
				s += "||";
			s += bookshelf.get(i);
		}

		writeFullSaveFileContent(saveLocalBookshelfFileName, s);
		return;
	}

	public static void addToLocalBookshelf(int aid) {
		if (bookshelf == null)
			loadLocalBookShelf();

		if (bookshelf.indexOf(aid) == -1)
			bookshelf.add(0, aid); // add to the first place

		writeLocalBookShelf();
		return;
	}

	public static void removeFromLocalBookshelf(int aid) {
		if (bookshelf == null)
			loadLocalBookShelf();

		int i = bookshelf.indexOf(aid);
		if (i != -1)
			bookshelf.remove(i);

		writeLocalBookShelf();
		return;
	}

	public static ArrayList<Integer> getLocalBookshelfList() {
		if (bookshelf == null)
			loadLocalBookShelf();

		return bookshelf;
	}

	public static boolean testInLocalBookshelf(int aid) {
		if (bookshelf == null)
			loadLocalBookShelf();

		if (bookshelf.indexOf(aid) == -1)
			return false;
		else
			return true;
	}

	public static void accessToLocalBookshelf(int aid) {
		int temp = bookshelf.indexOf(aid);
		if (aid == -1)
			return;

		bookshelf.remove(temp);
		bookshelf.add(0, aid);

		writeLocalBookShelf();
		return;
	}

	/** Novel content */
	/**
	 * saveNovelContentImage:
	 * 
	 * get image url and download to save folder's image folder
	 * 
	 * @param url
	 *            : full http url of target image
	 * @return: if file finally exist, if already exist before saving, still
	 *          return true; if finally the file does not exist, return false.
	 */
	public static boolean saveNovelContentImage(String url) {
		String imgFileName = generateImageFileNameByURL(url);
		if (!LightCache.testFileExist(getFirstFullSaveFilePath()
				+ imgsSaveFolderName + File.separator + imgFileName)
				&& !LightCache.testFileExist(getSecondFullSaveFilePath()
						+ imgsSaveFolderName + File.separator + imgFileName)) {
			// neither of the file exist
			byte[] fileContent = LightNetwork.LightHttpDownload(url);
			if (fileContent == null)
				return false; // network error
			if (!LightCache.saveFile(getFirstFullSaveFilePath()
					+ imgsSaveFolderName + File.separator, imgFileName,
					fileContent, true))
				// fail to first path
				return LightCache.saveFile(getSecondFullSaveFilePath()
						+ imgsSaveFolderName + File.separator, imgFileName,
						fileContent, true);
			return true; // saved to first directory
		}
		return true; // file exist
	}

	/**
	 * removeNovelContentImage:
	 * 
	 * get image url and delete the corresponding local file
	 * 
	 * @param url
	 *            : full http url of target image
	 * @return: true if file deleted successfully.
	 */
	public static boolean removeNovelContentImage(String url) {
		String imgFileName = generateImageFileNameByURL(url);

		// in fact, one of them deleted is ok, so use "or"
		return LightCache.deleteFile(getFirstFullSaveFilePath()
				+ imgsSaveFolderName + File.separator, imgFileName)
				|| LightCache.deleteFile(getSecondFullSaveFilePath()
						+ imgsSaveFolderName + File.separator, imgFileName);
	}

	/**
	 * getAvailableNovolContentImagePath:
	 * 
	 * get available local saving path of target image.
	 * 
	 * @param fileName
	 *            : just need the fileName
	 * @return: direct fileName or just null
	 */
	public static String getAvailableNovolContentImagePath(String fileName) {
		if (LightCache.testFileExist(getFirstFullSaveFilePath()
				+ imgsSaveFolderName + File.separator + fileName)) {
			return getFirstFullSaveFilePath() + imgsSaveFolderName
					+ File.separator + fileName;
		} else if (LightCache.testFileExist(getSecondFullSaveFilePath()
				+ imgsSaveFolderName + File.separator + fileName)) {
			return getSecondFullSaveFilePath() + imgsSaveFolderName
					+ File.separator + fileName;
		} else
			return null;
	}
}
