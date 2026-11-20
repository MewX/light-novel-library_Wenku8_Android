package org.mewx.wenku8.util;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.crashlytics.FirebaseCrashlytics;

/**
 * A thin wrapper over Crashlytics for non-fatal errors, breadcrumbs and custom keys.
 * <p>
 * Two reasons this exists rather than calling {@link FirebaseCrashlytics} directly:
 * <ol>
 *     <li>Crashlytics is not always there. Under the JVM unit test runtime the Firebase classes
 *     are stubs and {@code getInstance()} returns {@code null} (see
 *     {@code testOptions.unitTests.returnDefaultValues} in build.gradle), and a device without
 *     Google services can fail to initialise it at all. Every method here degrades to a logcat
 *     write instead of throwing, so call sites never need a guard.</li>
 *     <li>It keeps the stack trace in logcat. These calls replace {@code printStackTrace()}, and
 *     losing local visibility while debugging would be a bad trade.</li>
 * </ol>
 * All methods are safe to call from any thread.
 */
public final class CrashReporter {
    private static final String TAG = CrashReporter.class.getSimpleName();

    /**
     * Custom key names, kept in one place so that they stay consistent across call sites and
     * remain greppable. Crashlytics allows at most 64 custom keys per report, and silently drops
     * the rest, so this list is deliberately curated rather than exhaustive.
     */
    public static final class Keys {
        /** Simple name of the Activity or Fragment currently resumed. */
        public static final String SCREEN = "screen";
        /** Product flavor: alpha, baidu or playstore. */
        public static final String BUILD_FLAVOR = "build_flavor";
        /** Whether Google services resolved on this device; gates AdMob and Analytics. */
        public static final String GMS_AVAILABLE = "gms_available";
        /** SC or TC. Selects which API responses, and therefore which parsers, are exercised. */
        public static final String LANGUAGE = "language";
        /** "internal" or "external" -- which of the two storage roots saves are read from. */
        public static final String STORAGE_MODE = "storage_mode";
        /** Whether a wenku8 session is active. Never the account name; see setLoggedIn(). */
        public static final String LOGGED_IN = "logged_in";
        /** Novel id being viewed or read. */
        public static final String NOVEL_AID = "novel_aid";
        /** Chapter id being read. */
        public static final String CHAPTER_CID = "chapter_cid";
        /** "v1" (paginated) or "vertical" -- the two readers behave very differently. */
        public static final String READER_MODE = "reader_mode";

        private Keys() {
        }
    }

    /**
     * Resolved once. {@code null} means Crashlytics is unavailable in this runtime and every
     * method degrades to logcat. Volatile so the resolution is visible across threads.
     */
    @Nullable
    private static volatile FirebaseCrashlytics instance;
    private static volatile boolean resolved = false;

    private CrashReporter() {
    }

    @Nullable
    private static FirebaseCrashlytics get() {
        if (!resolved) {
            synchronized (CrashReporter.class) {
                if (!resolved) {
                    try {
                        // Returns null rather than throwing under the unit test runtime, so the
                        // null case below is a normal outcome and not only an error path.
                        instance = FirebaseCrashlytics.getInstance();
                    } catch (Throwable t) {
                        // Firebase was never initialised. Nothing to do but carry on without it.
                        instance = null;
                        Log.w(TAG, "Crashlytics unavailable; reporting to logcat only", t);
                    }
                    resolved = true;
                }
            }
        }
        return instance;
    }

    /**
     * Records a breadcrumb. Breadcrumbs are attached to whatever crash or non-fatal happens next,
     * which is what turns a bare stack trace into a sequence of events.
     */
    public static void log(@NonNull String message) {
        Log.d(TAG, message);
        FirebaseCrashlytics crashlytics = get();
        if (crashlytics != null) {
            crashlytics.log(message);
        }
    }

    /**
     * Reports a caught exception as a Crashlytics non-fatal.
     *
     * @param where a short stable label for the call site, e.g. "LightCache.loadStream". It is
     *              logged as a breadcrumb immediately before the report, because Crashlytics
     *              groups non-fatals by stack trace and several call sites here throw the same
     *              shape of IOException.
     */
    public static void recordException(@NonNull String where, @NonNull Throwable throwable) {
        Log.w(TAG, where, throwable);
        FirebaseCrashlytics crashlytics = get();
        if (crashlytics != null) {
            crashlytics.log(where);
            crashlytics.recordException(throwable);
        }
    }

    public static void setKey(@NonNull String key, @Nullable String value) {
        FirebaseCrashlytics crashlytics = get();
        if (crashlytics != null) {
            crashlytics.setCustomKey(key, value == null ? "" : value);
        }
    }

    public static void setKey(@NonNull String key, int value) {
        FirebaseCrashlytics crashlytics = get();
        if (crashlytics != null) {
            crashlytics.setCustomKey(key, value);
        }
    }

    public static void setKey(@NonNull String key, boolean value) {
        FirebaseCrashlytics crashlytics = get();
        if (crashlytics != null) {
            crashlytics.setCustomKey(key, value);
        }
    }

    /**
     * Records whether a session is active, deliberately as a boolean rather than via
     * {@code setUserId(username)}. The wenku8 account name identifies a real person to a third
     * party, and Crashlytics already counts affected users through its own installation id, so
     * sending it would add little beyond the privacy cost.
     */
    public static void setLoggedIn(boolean loggedIn) {
        setKey(Keys.LOGGED_IN, loggedIn);
    }

    /** Records the screen being entered, both as a breadcrumb and as a custom key. */
    public static void setScreen(@NonNull String screenName, @NonNull String lifecycleEvent) {
        setKey(Keys.SCREEN, screenName);
        log(screenName + "#" + lifecycleEvent);
    }
}
