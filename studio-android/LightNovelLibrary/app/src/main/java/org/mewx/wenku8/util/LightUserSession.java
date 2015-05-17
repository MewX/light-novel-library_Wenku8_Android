package org.mewx.wenku8.util;

/**
 * Created by MewX on 2015/5/17.
 * This file is secret. Not open-source.
 */
public class LightUserSession {

    // Secret part
    private static String username;
    private static String password;

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
        String[] a = raw.split("|"); // a[0]: username; a[1]: password;
        if(a == null || a.length != 2 || a[0].length() == 0 || a[1].length() == 0) {
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
        for(int i = 0, j = equal_pos == -1 ? temp_username.length - 1 : equal_pos; i < j; i ++, j --) {
            char temp = temp_username[i];
            temp_username[i] = temp_username[j];
            temp_username[j] = temp;
        }

        result = new String(temp_password);
        equal_pos = result.indexOf('=');
        for(int i = 0, j = equal_pos == -1 ? temp_password.length - 1 : equal_pos; i < j; i ++, j --) {
            char temp = temp_password[i];
            temp_password[i] = temp_password[j];
            temp_password[j] = temp;
        }

        // dec twice
        temp_username = LightBase64.DecodeBase64String(new String(temp_username)).toCharArray();
        temp_password = LightBase64.DecodeBase64String(new String(temp_password)).toCharArray();

        // exchange caps and uncaps
        for(int i = 0; i < temp_username.length; i ++) {
            if('a' <= temp_username[i] && temp_username[i] <= 'z' )
                temp_username[i] -= 'a' - 'A';
            else if('A' <= temp_username[i] && temp_username[i] <= 'Z')
                temp_username[i] += 'a' - 'A';
            else
                continue;
        }
        for(int i = 0; i < temp_password.length; i ++) {
            if('a' <= temp_password[i] && temp_password[i] <= 'z' )
                temp_password[i] -= 'a' - 'A';
            else if('A' <= temp_password[i] && temp_password[i] <= 'Z')
                temp_password[i] += 'a' - 'A';
            else
                continue;
        }

        // dec three times
        username = LightBase64.DecodeBase64String(new String(temp_username));
        password = LightBase64.DecodeBase64String(new String(temp_password));

        return;
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
                temp_username[i] -= 'a' - 'A';
            else if('A' <= temp_username[i] && temp_username[i] <= 'Z')
                temp_username[i] += 'a' - 'A';
            else
                continue;
        }
        for(int i = 0; i < temp_password.length; i ++) {
            if('a' <= temp_password[i] && temp_password[i] <= 'z' )
                temp_password[i] -= 'a' - 'A';
            else if('A' <= temp_password[i] && temp_password[i] <= 'Z')
                temp_password[i] += 'a' - 'A';
            else
                continue;
        }

        // twice base64, exchange char position, beg to end, end to beg
        int equal_pos;
        result = new String(temp_username);
        equal_pos = result.indexOf('=');
        temp_username = LightBase64.EncodeBase64(result).toCharArray();
        for(int i = 0, j = equal_pos == -1 ? temp_username.length - 1 : equal_pos; i < j; i ++, j --) {
            char temp = temp_username[i];
            temp_username[i] = temp_username[j];
            temp_username[j] = temp;
        }

        result = new String(temp_password);
        equal_pos = result.indexOf('=');
        temp_password = LightBase64.EncodeBase64(result).toCharArray();
        for(int i = 0, j = equal_pos == -1 ? temp_password.length - 1 : equal_pos; i < j; i ++, j --) {
            char temp = temp_password[i];
            temp_password[i] = temp_password[j];
            temp_password[j] = temp;
        }

        // three times base64
        result = LightBase64.EncodeBase64(new String(temp_username));
        result += "|" + LightBase64.EncodeBase64(new String(temp_password));

        // return value
        return result;
    }


}
