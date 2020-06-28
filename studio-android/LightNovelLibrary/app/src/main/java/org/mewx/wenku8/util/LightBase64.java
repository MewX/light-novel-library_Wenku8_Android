package org.mewx.wenku8.util;

import androidx.annotation.NonNull;
import android.util.Base64;

import java.nio.charset.Charset;

/**
 * Light Base64
 * *
 * This class achieve the basic base64 en/decryption:
 * use "Base64.DEFAULT" to encrypt or decrypt text.
 */
public class LightBase64 {
    @NonNull
    static public String EncodeBase64(@NonNull byte[] b) {
        return Base64.encodeToString(b, Base64.DEFAULT).trim();
    }

    @NonNull
    static public String EncodeBase64(@NonNull String s) {
        return EncodeBase64(s.getBytes(Charset.forName("UTF-8")));
    }

    @NonNull
    static public byte[] DecodeBase64(@NonNull String s) {
        try {
            byte[] b;
            b = Base64.decode(s, Base64.DEFAULT);
            return b;
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    @NonNull
    static public String DecodeBase64String(@NonNull String s) {
        return new String(DecodeBase64(s), Charset.forName("UTF-8"));
    }
}
