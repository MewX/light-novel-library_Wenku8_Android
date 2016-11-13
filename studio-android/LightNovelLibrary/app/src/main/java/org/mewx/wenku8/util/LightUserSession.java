package org.mewx.wenku8.util;

import android.content.Intent;
import android.os.AsyncTask;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.widget.Toast;

import org.mewx.wenku8.MyApp;
import org.mewx.wenku8.R;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.global.api.Wenku8Error;
import org.mewx.wenku8.service.HeartbeatSessionKeeper;

import java.io.UnsupportedEncodingException;

/**
 * Created by MewX on 2015/5/17.
 * This file is secret. Not open-source.
 */
public class LightUserSession {
    // open part
    public static AsyncInitUserInfo aiui; // not exec

    // Secret part
    private static boolean logStatus = false; // true - logged in; false - not logged in.
    private static String username = null;
    private static String password = null;
    private static String SESSION = null;

    // no null returned
    public static String getLoggedAs() {
        return ( logStatus && SESSION != null && SESSION.length() != 0 && isUserInfoSet()) ? username : "";
    }

    public static String getUsername() {
        return username == null ? "" : username;
    }

    // no null returned, default is ""
    public static String getSession() {
        return SESSION == null ? "" : SESSION;
    }

    public static void setSession(String s) {
        if(s != null && s.length() != 0) SESSION = s;
    }

    public static boolean getLogStatus() {
        return logStatus;
    }

    public static boolean loadUserInfoSet() {
        byte[] bytes;
        if(LightCache.testFileExist(GlobalConfig.getFirstFullUserAccountSaveFilePath())) {
            bytes = LightCache.loadFile(GlobalConfig.getFirstFullUserAccountSaveFilePath());
        }
        else if(LightCache.testFileExist(GlobalConfig.getSecondFullUserAccountSaveFilePath())) {
            bytes = LightCache.loadFile(GlobalConfig.getSecondFullUserAccountSaveFilePath());
        }
        else {
            return false; // file read failed
        }

        try {
            //Log.e("MewX", new String(bytes, "UTF-8"));
            decAndSetUserFile(new String(bytes, "UTF-8"));
        } catch (Exception e) {
            e.printStackTrace();
            return false; // exception
        }

        return true;
    }

    public static boolean saveUserInfoSet() {
        LightCache.saveFile(GlobalConfig.getFirstFullUserAccountSaveFilePath(), encUserFile().getBytes(), true);
        if(!LightCache.testFileExist(GlobalConfig.getFirstFullUserAccountSaveFilePath())) {
            LightCache.saveFile(GlobalConfig.getSecondFullUserAccountSaveFilePath(), encUserFile().getBytes(), true);
            if(!LightCache.testFileExist(GlobalConfig.getSecondFullUserAccountSaveFilePath()))
                return false;
        }

        return true;
    }

    // async action
    public static Wenku8Error.ErrorCode doLoginFromFile() {
        // This function will read from file, if failed return false
        if(!isUserInfoSet()) loadUserInfoSet();
        if(!isUserInfoSet()) return Wenku8Error.ErrorCode.USER_INFO_EMPTY;

        byte[] b = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), Wenku8API.getUserLoginParams(username, password));
        if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
        try {
            String result = new String(b, "UTF-8");
            if(!LightTool.isInteger(result)) {
                return Wenku8Error.ErrorCode.RETURNED_VALUE_EXCEPTION;
            }

            if(Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(result)) == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED)
                logStatus = true;

            return Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(result)); // get excepted returned value
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return Wenku8Error.ErrorCode.BYTE_TO_STRING_EXCEPTION;
        }
    }

    // async action
    public static Wenku8Error.ErrorCode doLoginFromGiven(String name, String pwd) {
        // This function will test given name:pwd, if pass(receive '1'), save file, else return false

        byte[] b = LightNetwork.LightHttpPostConnection(Wenku8API.getBaseURL(), Wenku8API.getUserLoginParams(name, pwd));
        if(b == null) return Wenku8Error.ErrorCode.NETWORK_ERROR;
        try {
            String result = new String(b, "UTF-8");
            if(!LightTool.isInteger(result)) {
                return Wenku8Error.ErrorCode.RETURNED_VALUE_EXCEPTION;
            }

            if(Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(result)) == Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED) {
                logStatus = true;

                // save user info
                username = name;
                password = pwd;
                saveUserInfoSet();

                // TODO: activate session keeper

            }

            return Wenku8Error.getSystemDefinedErrorCode(Integer.valueOf(result)); // get excepted returned value
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return Wenku8Error.ErrorCode.BYTE_TO_STRING_EXCEPTION;
        }
    }

    public static void logOut() {
        logStatus = false;
        username = "";
        password = "";

        // delete files
        if(!LightCache.deleteFile(GlobalConfig.getFirstFullUserAccountSaveFilePath())) {
            LightCache.deleteFile(GlobalConfig.getSecondFullUserAccountSaveFilePath());
        }
        if(!LightCache.deleteFile(GlobalConfig.getFirstUserAvatarSaveFilePath())) {
            LightCache.deleteFile(GlobalConfig.getSecondUserAvatarSaveFilePath());
        }
    }

    // async action
    public static Wenku8Error.ErrorCode heartbeatLogin() {
        // call from HeartbeatSessionKeeper, send login operation
        Toast.makeText(MyApp.getContext(), "Heartbeat test", Toast.LENGTH_SHORT).show();
        return doLoginFromFile();
    }


    /**
     * This function will only judge whether the username and the password var is set.
     * Not judging what letters are they contains.
     * @return true is okay.
     */
    public static boolean isUserInfoSet() {
        if(username == null || password == null || username.length() == 0 || password.length() == 0)
            return false;
        else
            return true;
    }

    /**
     * Decrypt user file raw content, which is 0-F byte values, encoded in UTF-8.
     * @param raw UTF-8 Charset raw file content.
     */
    public static void decAndSetUserFile(String raw) {
        try {
            String[] a = raw.split("\\|"); // a[0]: username; a[1]: password;
            if (a.length != 2 || a[0].length() == 0 || a[1].length() == 0) {
                username = "";
                password = "";
                return; // fetch error to return
            }

            // dec once
            char[] temp_username = LightBase64.DecodeBase64String(a[0]).toCharArray();
            char[] temp_password = LightBase64.DecodeBase64String(a[1]).toCharArray();

            // reverse main part
            int equal_pos;
            String result = new String(temp_username);
            equal_pos = result.indexOf('=');
            for (int i = 0, j = equal_pos == -1 ? temp_username.length - 1 : equal_pos - 1; i < j; i++, j--) {
                char temp = temp_username[i];
                temp_username[i] = temp_username[j];
                temp_username[j] = temp;
            }

            result = new String(temp_password);
            equal_pos = result.indexOf('=');
            for (int i = 0, j = equal_pos == -1 ? temp_password.length - 1 : equal_pos - 1; i < j; i++, j--) {
                char temp = temp_password[i];
                temp_password[i] = temp_password[j];
                temp_password[j] = temp;
            }

            // dec twice
            temp_username = LightBase64.DecodeBase64String(new String(temp_username)).toCharArray();
            temp_password = LightBase64.DecodeBase64String(new String(temp_password)).toCharArray();

            // exchange caps and uncaps
            for (int i = 0; i < temp_username.length; i++) {
                if ('a' <= temp_username[i] && temp_username[i] <= 'z')
                    temp_username[i] -= ('a' - 'A');
                else if ('A' <= temp_username[i] && temp_username[i] <= 'Z')
                    temp_username[i] += ('a' - 'A');
            }
            for (int i = 0; i < temp_password.length; i++) {
                if ('a' <= temp_password[i] && temp_password[i] <= 'z')
                    temp_password[i] -= ('a' - 'A');
                else if ('A' <= temp_password[i] && temp_password[i] <= 'Z')
                    temp_password[i] += ('a' - 'A');
            }

            // dec three times
            username = LightBase64.DecodeBase64String(new String(temp_username));
            password = LightBase64.DecodeBase64String(new String(temp_password));
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Encrypt user file to raw content.
     * @return raw file content, fail to return "". (no null returned)
     */
    public static String encUserFile() {
        // judge available
        if(!isUserInfoSet())
            return ""; // empty, not null

        String result = "";

        // username, password enc to base64
        char[] temp_username = LightBase64.EncodeBase64(username).toCharArray();
        char[] temp_password = LightBase64.EncodeBase64(password).toCharArray();

        // cap to uncap, uncap to cap
        for(int i = 0; i < temp_username.length; i ++) {
            if('a' <= temp_username[i] && temp_username[i] <= 'z' )
                temp_username[i] -= ('a' - 'A');
            else if('A' <= temp_username[i] && temp_username[i] <= 'Z')
                temp_username[i] += ('a' - 'A');
        }
        for(int i = 0; i < temp_password.length; i ++) {
            if('a' <= temp_password[i] && temp_password[i] <= 'z' )
                temp_password[i] -= ('a' - 'A');
            else if('A' <= temp_password[i] && temp_password[i] <= 'Z')
                temp_password[i] += ('a' - 'A');
        }

        // twice base64, exchange char position, beg to end, end to beg
        int equal_pos;
        temp_username = LightBase64.EncodeBase64(new String(temp_username)).toCharArray();
        result = new String(temp_username);
        equal_pos = result.indexOf('=');
        for(int i = 0, j = equal_pos == -1 ? temp_username.length - 1 : equal_pos - 1; i < j; i ++, j --) {
            char temp = temp_username[i];
            temp_username[i] = temp_username[j];
            temp_username[j] = temp;
        }

        temp_password = LightBase64.EncodeBase64(new String(temp_password)).toCharArray();
        result = new String(temp_password);
        equal_pos = result.indexOf('=');
        for(int i = 0, j = equal_pos == -1 ? temp_password.length - 1 : equal_pos - 1; i < j; i ++, j --) {
            char temp = temp_password[i];
            temp_password[i] = temp_password[j];
            temp_password[j] = temp;
        }

        // three times base64
        result = LightBase64.EncodeBase64(new String(temp_username)) + "|" + LightBase64.EncodeBase64(new String(temp_password));

        // return value
        return result;
    }

    public static class AsyncInitUserInfo extends AsyncTask<Integer, Integer, Wenku8Error.ErrorCode> {
        @Override
        protected Wenku8Error.ErrorCode doInBackground(Integer... params) {
            loadUserInfoSet();
            return doLoginFromFile();
        }

        @Override
        protected void onPostExecute(Wenku8Error.ErrorCode e) {
            super.onPostExecute(e);

            // if error code == UN error or PWD error, clear cert & avatar
            if(e == Wenku8Error.ErrorCode.SYSTEM_2_ERROR_USERNAME || e == Wenku8Error.ErrorCode.SYSTEM_3_ERROR_PASSWORD) {
                if(!LightCache.deleteFile(GlobalConfig.getFirstFullUserAccountSaveFilePath()))
                    LightCache.deleteFile(GlobalConfig.getSecondFullUserAccountSaveFilePath());
                if(!LightCache.deleteFile(GlobalConfig.getFirstUserAvatarSaveFilePath()))
                    LightCache.deleteFile(GlobalConfig.getSecondUserAvatarSaveFilePath());

                username = "";
                password = "";
                Toast.makeText(MyApp.getContext(), MyApp.getContext().getResources().getString(R.string.system_log_info_outofdate), Toast.LENGTH_SHORT).show();
                return;
            }

            if(LightUserSession.logStatus) {
                // heart beat service
                // Toast.makeText(MyApp.getContext(), e.toString(), Toast.LENGTH_SHORT).show();
                Intent intent = new Intent(MyApp.getContext(),HeartbeatSessionKeeper.class);
                MyApp.getContext().startService(intent);
            }
            else {
//                 Toast.makeText(MyApp.getContext(), e.toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
