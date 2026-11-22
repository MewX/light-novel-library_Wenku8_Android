package org.mewx.wenku8.util;

import org.junit.Test;

/**
 * The point of these tests is the degradation path, not the reporting path.
 * <p>
 * Under the JVM test runtime Firebase is a stub and {@code FirebaseCrashlytics.getInstance()}
 * returns null, which is the same situation as a device where Firebase failed to initialise.
 * CrashReporter is called from catch blocks all over the app, so if it ever throws there it
 * converts a handled error into a crash -- strictly worse than the printStackTrace() it replaced.
 * These tests pin that it stays silent.
 */
public class CrashReporterTest {

    @Test
    public void logDoesNotThrowWithoutCrashlytics() {
        CrashReporter.log("breadcrumb");
    }

    @Test
    public void recordExceptionDoesNotThrowWithoutCrashlytics() {
        CrashReporter.recordException("CrashReporterTest.recordException",
                new IllegalStateException("expected"));
    }

    @Test
    public void recordExceptionToleratesAThrowableWithNoMessage() {
        CrashReporter.recordException("CrashReporterTest.noMessage", new NullPointerException());
    }

    @Test
    public void setKeyDoesNotThrowWithoutCrashlytics() {
        CrashReporter.setKey(CrashReporter.Keys.SCREEN, "MainActivity");
        CrashReporter.setKey(CrashReporter.Keys.NOVEL_AID, 1234);
        CrashReporter.setKey(CrashReporter.Keys.LOGGED_IN, true);
    }

    @Test
    public void setKeyToleratesANullStringValue() {
        // getStringExtra() and the GlobalConfig getters return null routinely, and a null here
        // would otherwise reach Crashlytics' non-null parameter.
        CrashReporter.setKey(CrashReporter.Keys.LANGUAGE, null);
    }

    @Test
    public void setScreenDoesNotThrowWithoutCrashlytics() {
        CrashReporter.setScreen("MainActivity", "onCreate");
    }

    @Test
    public void setLoggedInDoesNotThrowWithoutCrashlytics() {
        CrashReporter.setLoggedIn(true);
        CrashReporter.setLoggedIn(false);
    }
}
