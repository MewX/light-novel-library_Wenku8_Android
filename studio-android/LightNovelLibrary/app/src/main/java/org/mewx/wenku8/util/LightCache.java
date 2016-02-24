/**
 *  Light Cache
 **
 *  This class provide straight file operation functions.
 *  Easy save file, read file and delete file.
 **/

package org.mewx.wenku8.util;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;

import android.util.Log;

public class LightCache {
	public static boolean testFileExist(String path) {
		File file = new File(path);
        if(file.exists()) {
            if(file.length() == 0)
                deleteFile(path); // delete empty file
            else
                return true;
        }
        return false;
	}

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
				if(dis.read(bs, 0, fileSize) == -1)
					return null;

				dis.close();
				in.close();
				return bs;
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return null;
	}

	public static boolean saveFile(String path, String fileName, byte[] bs, boolean forceUpdate) {
		// if forceUpdate == true then update the file
		File file = new File(path);
		file.mkdirs();

		file = new File(path + (path.charAt(path.length() - 1) != File.separatorChar ? File.separator : "") + fileName);
		Log.v("MewX-File",
				"Path: " + path + (path.charAt(path.length() - 1) != File.separatorChar ? File.separator : "") + fileName);
		if (!file.exists() || forceUpdate) {
			if (file.exists() && !file.isFile()) {
				Log.v("MewX-File", "Write failed0");
				return false; // is not a file
			}

			try {
				file.createNewFile(); // create file

				FileOutputStream out = new FileOutputStream(file); // trunc
				DataOutputStream dos = new DataOutputStream(out);

				// write all
				dos.write(bs);

				dos.close();
				out.close();
				Log.v("MewX-File", "Write successfully");
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}

		}
		return true; // say it successful
	}

	public static boolean saveFile(String filepath, byte[] bs, boolean forceUpdate) {
		// if forceUpdate == true then update the file
		File file = new File(filepath);
		Log.v("MewX-File", "Path: " + filepath);
		if (!file.exists() || forceUpdate) {
			if (file.exists() && !file.isFile()) {
				Log.v("MewX-File", "Write failed0");
				return false; // is not a file
			}

			try {
				file.createNewFile(); // create file

				FileOutputStream out = new FileOutputStream(file); // trunc
				if (out == null) {
					Log.v("MewX-File", "Write failed1");
					return false;
				}
				DataOutputStream dos = new DataOutputStream(out);
				if (dos == null) {
					Log.v("MewX-File", "Write failed2");
					return false;
				}

				// write all
				dos.write(bs);

				dos.close();
				out.close();
				Log.v("MewX-File", "Write successfully");
				return true;
			} catch (IOException e) {
				e.printStackTrace();
				return false;
			}

		}
		return true; // say it successful
	}

	public static boolean deleteFile(String path, String fileName) {
		Log.v("MewX-File",
				"Path: "
						+ path
						+ (path.charAt(path.length() - 1) != File.separatorChar ? File.separator
								: "") + fileName);
		File file = new File(
				path
						+ (path.charAt(path.length() - 1) != File.separatorChar ? File.separator
								: "") + fileName);

		if (file.delete()) {
			Log.v("MewX-File", "Delete successfully");
			return true;
		} else
			return false;
	}

	public static boolean deleteFile(String filepath) {
		Log.v("MewX-File", "Path: " + filepath);
		File file = new File(filepath);

		if (file.delete()) {
			Log.v("MewX-File", "Delete successfully");
			return true;
		} else
			return false;
	}

	public static void copyfile(String from, String to, Boolean forceWrite ) {
		File fromFile = new File(from);
		File toFile = new File(to);
		if (!fromFile.exists() || !fromFile.isFile() || !fromFile.canRead())
			return;

		if (!toFile.getParentFile().exists()) toFile.getParentFile().mkdirs();
		if (toFile.exists() && forceWrite) toFile.delete();

		try {
			java.io.FileInputStream fosfrom = new java.io.FileInputStream(fromFile);
			java.io.FileOutputStream fosto = new FileOutputStream(toFile);

			byte bt[] = new byte[1024];
			int c;
			while ((c = fosfrom.read(bt)) > 0) fosto.write(bt, 0, c);
			fosfrom.close();
			fosto.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}
}
