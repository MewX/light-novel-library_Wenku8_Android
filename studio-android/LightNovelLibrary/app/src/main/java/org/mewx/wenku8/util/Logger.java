package org.mewx.wenku8.util;

import org.mewx.wenku8.global.GlobalConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Created by MewX on 2015/3/23.
 * This log file should be open by GlobalConfig and close by it as well.
 */
public class Logger {

    public final static String LogFileName = "latestLog.log";
    public static FileOutputStream fos;

    public static void openLogger() {
        try {
            // 优先写入SDcard
            /*if(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
                File sdCardDir = Environment.getExternalStorageDirectory();//获取SDCard目录
                File saveFile = new File(sdCardDir, "a.txt");
            }*/

            fos = new FileOutputStream(new File(GlobalConfig.getDefaultStoragePath() + LogFileName), false); // overwrite

        } catch( FileNotFoundException e ) {
            e.printStackTrace();
        }
    }

    public static void closeLogger() {
        try {
            if(fos!=null)
                fos.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取Logger的状态
     * @return true-open; false-other
     */
    public static boolean getLoggerStatus( ) {
        return fos == null;
    }

    /**
     * 将字符串写入Log文件，加入时间戳标记和函数名标记
     * @param str 要写入的字符串
     * @return true-写入成功; false-写入失败
     */
    public static boolean writeLogger( String str ) {
        try {
            if(fos==null)
                openLogger(); // open first

            fos.write(str.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
