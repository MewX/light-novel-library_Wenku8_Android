package android.util;

/**
 * Faking the Android API.
 * <p>
 * From: <a href="https://stackoverflow.com/a/60318356/4206925">How to mock Base64 in Android?</a>
 */
public class Base64 {

    public static String encodeToString(byte[] input, int flags) {
        return java.util.Base64.getEncoder().encodeToString(input);
    }

    public static byte[] decode(String str, int flags) {
        return java.util.Base64.getDecoder().decode(str);
    }

    // add other methods if required...
}