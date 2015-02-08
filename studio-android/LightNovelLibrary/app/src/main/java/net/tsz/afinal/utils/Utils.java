/**
 * Copyright (c) 2012-2013, Michael Yang 杨福海 (www.yangfuhai.com).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.tsz.afinal.utils;

import java.io.File;

import android.content.Context;
import android.graphics.Bitmap;
import android.os.Environment;
import android.os.StatFs;
import android.util.Log;

public class Utils {
	
	private static final String TAG = "BitmapCommonUtils";
	private static final long POLY64REV = 0x95AC9329AC4BC9B5L;
    private static final long INITIALCRC = 0xFFFFFFFFFFFFFFFFL;

    private static long[] sCrcTable = new long[256];
	/**
	 * 获取可以使用的缓存目录
	 * @param context
	 * @param uniqueName 目录名称
	 * @return
	 */
    public static File getDiskCacheDir(Context context, String uniqueName) {
        final String cachePath = Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ? 
                		getExternalCacheDir(context).getPath() : context.getCacheDir().getPath();

        return new File(cachePath + File.separator + uniqueName);
    }

  

    /**
     * 获取bitmap的字节大小
     * @param bitmap
     * @return
     */
    public static int getBitmapSize(Bitmap bitmap) {
        return bitmap.getRowBytes() * bitmap.getHeight();
    }


   /**
    * 获取程序外部的缓存目录
    * @param context
    * @return
    */
    public static File getExternalCacheDir(Context context) {
        final String cacheDir = "/Android/data/" + context.getPackageName() + "/cache/";
        return new File(Environment.getExternalStorageDirectory().getPath() + cacheDir);
    }

    /**
     * 获取文件路径空间大小
     * @param path
     * @return
     */
    public static long getUsableSpace(File path) {
    	try{
    		 final StatFs stats = new StatFs(path.getPath());
    	     return (long) stats.getBlockSize() * (long) stats.getAvailableBlocks();
    	}catch (Exception e) {
			Log.e(TAG, "获取 sdcard 缓存大小 出错，请查看AndroidManifest.xml 是否添加了sdcard的访问权限");
			e.printStackTrace();
			return -1;
		}
       
    }
    
    
    public static byte[] getBytes(String in) {
        byte[] result = new byte[in.length() * 2];
        int output = 0;
        for (char ch : in.toCharArray()) {
            result[output++] = (byte) (ch & 0xFF);
            result[output++] = (byte) (ch >> 8);
        }
        return result;
    }

    public static boolean isSameKey(byte[] key, byte[] buffer) {
        int n = key.length;
        if (buffer.length < n) {
            return false;
        }
        for (int i = 0; i < n; ++i) {
            if (key[i] != buffer[i]) {
                return false;
            }
        }
        return true;
    }
    
    public static byte[] copyOfRange(byte[] original, int from, int to) {
        int newLength = to - from;
        if (newLength < 0)
            throw new IllegalArgumentException(from + " > " + to);
        byte[] copy = new byte[newLength];
        System.arraycopy(original, from, copy, 0,Math.min(original.length - from, newLength));
        return copy;
    }
    
    
    
    static {
        //参考 http://bioinf.cs.ucl.ac.uk/downloads/crc64/crc64.c
        long part;
        for (int i = 0; i < 256; i++) {
            part = i;
            for (int j = 0; j < 8; j++) {
                long x = ((int) part & 1) != 0 ? POLY64REV : 0;
                part = (part >> 1) ^ x;
            }
            sCrcTable[i] = part;
        }
    }
    
    public static byte[] makeKey(String httpUrl) {
        return getBytes(httpUrl);
    }

    /**
     * A function thats returns a 64-bit crc for string
     *
     * @param in input string
     * @return a 64-bit crc value
     */
    public static final long crc64Long(String in) {
        if (in == null || in.length() == 0) {
            return 0;
        }
        return crc64Long(getBytes(in));
    }

    public static final long crc64Long(byte[] buffer) {
        long crc = INITIALCRC;
        for (int k = 0, n = buffer.length; k < n; ++k) {
            crc = sCrcTable[(((int) crc) ^ buffer[k]) & 0xff] ^ (crc >> 8);
        }
        return crc;
    }

}
