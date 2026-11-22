package org.mewx.wenku8.util

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * Thin, thread-safe wrapper over [FirebaseCrashlytics] for non-fatal errors, breadcrumbs, and
 * curated custom keys (adapted from `v1.30`'s `CrashReporter`).
 *
 * Design guarantees:
 * 1. **Always safe without Firebase**: Under host-JVM unit tests, Robolectric, or devices where
 *    FirebaseApp did not initialize, every call degrades cleanly to logcat instead of throwing.
 * 2. **Logcat visibility preserved**: Breadcrumbs and non-fatal exceptions remain visible locally.
 * 3. **Zero PII**: Session state is recorded strictly as a boolean ([setLoggedIn]) — never as a
 *    username, email, or account identifier.
 */
object CrashReporter {
    private const val TAG = "CrashReporter"

    /**
     * Curated custom keys (Crashlytics enforces a 64-key ceiling per report).
     */
    object Keys {
        /** Current active Compose destination or Activity screen name. */
        const val SCREEN = "screen"
        /** Distribution channel / flavor: `default` or `playstore`. */
        const val BUILD_FLAVOR = "build_flavor"
        /** Whether Google Play Services resolved on this device. */
        const val GMS_AVAILABLE = "gms_available"
        /** `SC` or `TC` — selects which API responses and XML parsers are exercised. */
        const val LANGUAGE = "language"
        /** Whether a Wenku8 session is active (boolean only; never account name). */
        const val LOGGED_IN = "logged_in"
        /** Currently viewed or read novel AID (`0` when not in novel/reader context). */
        const val NOVEL_AID = "novel_aid"
        /** Currently read chapter CID (`0` when not in reader context). */
        const val CHAPTER_CID = "chapter_cid"
        /** Active reader page-turn mode (`scroll`, `slide`, `curl`, `none`). */
        const val READER_MODE = "reader_mode"
        /** Active reader column layout mode (`auto`, `single`, `dual`). */
        const val READER_COLUMNS = "reader_columns"
        /** Active UI theme mode (`system`, `light`, `dark`, `e_ink`). */
        const val THEME_MODE = "theme_mode"
        /** Whether anti-blocking auto-optimize relay routing is enabled. */
        const val AUTO_ROUTING = "auto_routing"
    }

    internal interface Backend {
        fun log(message: String)
        fun recordException(throwable: Throwable)
        fun setCustomKey(key: String, value: String)
        fun setCustomKey(key: String, value: Int)
        fun setCustomKey(key: String, value: Boolean)
    }

    private class FirebaseCrashlyticsBackend(
        private val crashlytics: FirebaseCrashlytics,
    ) : Backend {
        override fun log(message: String) {
            runCatching { crashlytics.log(message) }
        }

        override fun recordException(throwable: Throwable) {
            runCatching { crashlytics.recordException(throwable) }
        }

        override fun setCustomKey(key: String, value: String) {
            runCatching { crashlytics.setCustomKey(key, value) }
        }

        override fun setCustomKey(key: String, value: Int) {
            runCatching { crashlytics.setCustomKey(key, value) }
        }

        override fun setCustomKey(key: String, value: Boolean) {
            runCatching { crashlytics.setCustomKey(key, value) }
        }
    }

    @Volatile
    private var resolvedBackend: Backend? = null

    @Volatile
    private var isResolved: Boolean = false

    @Volatile
    private var testBackend: Backend? = null

    private fun backend(): Backend? {
        testBackend?.let { return it }
        if (!isResolved) {
            synchronized(this) {
                testBackend?.let { return it }
                if (!isResolved) {
                    resolvedBackend = try {
                        val instance: FirebaseCrashlytics? = FirebaseCrashlytics.getInstance()
                        instance?.let(::FirebaseCrashlyticsBackend)
                    } catch (t: Throwable) {
                        runCatching {
                            Log.w(TAG, "Crashlytics unavailable; reporting to logcat only", t)
                        }
                        null
                    }
                    isResolved = true
                }
            }
        }
        return resolvedBackend
    }

    /** Records a breadcrumb attached to the next fatal or non-fatal Crashlytics report. */
    fun log(message: String) {
        runCatching { Log.d(TAG, message) }
        backend()?.log(message)
    }

    /**
     * Records a caught exception as a Crashlytics non-fatal.
     *
     * @param where stable call-site tag logged as a breadcrumb immediately before the exception so
     * similar `IOException` / `XmlPullParserException` stack traces remain separable in reports.
     */
    fun recordException(where: String, throwable: Throwable) {
        runCatching { Log.w(TAG, where, throwable) }
        backend()?.let {
            it.log(where)
            it.recordException(throwable)
        }
    }

    fun setKey(key: String, value: String?) {
        backend()?.setCustomKey(key, value ?: "")
    }

    fun setKey(key: String, value: Int) {
        backend()?.setCustomKey(key, value)
    }

    fun setKey(key: String, value: Boolean) {
        backend()?.setCustomKey(key, value)
    }

    /** Records whether an account session is active without ever transmitting user identifiers. */
    fun setLoggedIn(loggedIn: Boolean) {
        setKey(Keys.LOGGED_IN, loggedIn)
    }

    /** Records entering a screen both as a custom key and a breadcrumb. */
    fun setScreen(screenName: String, lifecycleEvent: String = "enter") {
        setKey(Keys.SCREEN, screenName)
        log("$screenName#$lifecycleEvent")
    }

    internal fun setTestBackend(backend: Backend?) {
        synchronized(this) {
            testBackend = backend
        }
    }

    internal fun resetForTests() {
        synchronized(this) {
            testBackend = null
            resolvedBackend = null
            isResolved = false
        }
    }
}
