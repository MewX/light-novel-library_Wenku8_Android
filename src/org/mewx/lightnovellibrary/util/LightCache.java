/**
 *  Light Cache
 **
 *  This class provide straight file operation functions.
 *  Easy save file, read file and delete file.
 **/

package org.mewx.lightnovellibrary.util;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.util.Log;

public class LightCache {
	public static boolean testFileExist(String path) {
		File file = new File(path);
		return file.exists();
	}

	public static byte[] loadFile(String path) {
		// if file not exist, then return null
		File file = new File(path);
		if (file.exists() && file.isFile()) {
			// load existing file
			int fileSize = (int) file.length(); // get file size
			try {
				FileInputStream in = new FileInputStream(file);
				if (in == null)
					return null;
				DataInputStream dis = new DataInputStream(in);
				if (dis == null)
					return null;

				// read all
				byte[] bs = new byte[fileSize];
				dis.read(bs, 0, fileSize);

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

	public static boolean saveFile(String path, String fileName, byte[] bs,
			boolean forceUpdate) {
		// if forceUpdate == true then update the file
		File file = new File(path);
		file.mkdirs();

		file = new File(
				path
						+ (path.charAt(path.length() - 1) != File.separatorChar ? File.separator
								: "") + fileName);
		Log.v("MewX-File",
				"Path: "
						+ path
						+ (path.charAt(path.length() - 1) != File.separatorChar ? File.separator
								: "") + fileName);
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
				// TODO Auto-generated catch block
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
}
