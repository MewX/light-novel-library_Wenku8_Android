package org.mewx.wenku8.network;

@SuppressWarnings("unused")
public class LightUserSession {
    public static String getUsernameOrEmail() {
        throw new UnsupportedOperationException("stub");
    }

    public static String getPassword() {
        throw new UnsupportedOperationException("stub");
    }

    public static void logOut(Runnable fileDeletionCallback) {
        throw new UnsupportedOperationException("stub");
    }

    public static boolean isUserInfoSet() {
        return false;
    }

    public static void setUserInfo(String username, String password) {
        throw new UnsupportedOperationException("stub");
    }

    public static void decAndSetUserFile(String raw) {
        throw new UnsupportedOperationException("stub");
    }

    public static String encUserFile() {
        throw new UnsupportedOperationException("stub");
    }
}
