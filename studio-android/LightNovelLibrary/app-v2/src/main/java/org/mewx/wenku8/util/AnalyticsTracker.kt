package org.mewx.wenku8.util

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics

/**
 * GMS-safe, GA4-compliant Firebase Analytics tracker for `:app-v2`.
 *
 * Preserves event & parameter continuity with `v1.30` (same Firebase project `wenku8-ba1a9`):
 * - `screen_view` (`screen_name`, `screen_class`)
 * - `view_item` (`item_id`, `item_name`, `from`)
 * - `search` (`search_term`, plus v2 `search_mode`)
 * - `daily_check_in` (`effective_click`, plus v2 `outcome`)
 * - `save_migration_path_selection` (`valid_path`)
 *
 * And adds structured GA4 tracking for `:app-v2` features (reader sessions, bookshelf actions,
 * offline downloads, reviews, backup/restore, and diagnostics) while enforcing GA4 length caps
 * and zero-PII hygiene.
 */
object AnalyticsTracker {
    private const val TAG = "AnalyticsTracker"
    internal const val MAX_PARAM_VALUE_LENGTH = 100
    internal const val MAX_USER_PROPERTY_VALUE_LENGTH = 36

    object Events {
        const val SCREEN_VIEW = FirebaseAnalytics.Event.SCREEN_VIEW
        const val VIEW_ITEM = FirebaseAnalytics.Event.VIEW_ITEM
        const val SEARCH = FirebaseAnalytics.Event.SEARCH
        const val LOGIN = FirebaseAnalytics.Event.LOGIN
        const val SHARE = FirebaseAnalytics.Event.SHARE

        // Carried over from v1.30 for historical GA4 continuity
        const val DAILY_CHECK_IN = "daily_check_in"
        const val SAVE_MIGRATION_PATH_SELECTION = "save_migration_path_selection"

        // Structured v2 product events
        const val LOGOUT = "logout"
        const val BOOKSHELF_ADD = "bookshelf_add"
        const val BOOKSHELF_REMOVE = "bookshelf_remove"
        const val BOOKSHELF_CLOUD_SYNC = "bookshelf_cloud_sync"
        const val READER_OPEN = "reader_open"
        const val READER_SESSION_END = "reader_session_end"
        const val NOVEL_DOWNLOAD_START = "novel_download_start"
        const val NOVEL_DOWNLOAD_COMPLETE = "novel_download_complete"
        const val REVIEW_POST = "review_post"
        const val REVIEW_REPLY = "review_reply"
        const val V1_STORAGE_MIGRATION = "v1_storage_migration"
        const val BACKUP_EXPORT = "backup_export"
        const val BACKUP_IMPORT = "backup_import"
        const val NETWORK_DIAGNOSTICS_RUN = "network_diagnostics_run"
    }

    object Params {
        const val SCREEN_NAME = FirebaseAnalytics.Param.SCREEN_NAME
        const val SCREEN_CLASS = FirebaseAnalytics.Param.SCREEN_CLASS
        const val ITEM_ID = FirebaseAnalytics.Param.ITEM_ID
        const val ITEM_NAME = FirebaseAnalytics.Param.ITEM_NAME
        const val SEARCH_TERM = FirebaseAnalytics.Param.SEARCH_TERM
        const val METHOD = FirebaseAnalytics.Param.METHOD
        const val CONTENT_TYPE = FirebaseAnalytics.Param.CONTENT_TYPE

        const val FROM = "from"
        const val SEARCH_MODE = "search_mode"
        const val EFFECTIVE_CLICK = "effective_click"
        const val OUTCOME = "outcome"
        const val SUCCESS = "success"
        const val SOURCE = "source"
        const val COUNT = "count"
        const val TRIGGER = "trigger"
        const val STATUS = "status"
        const val CHAPTER_ID = "chapter_id"
        const val PAGE_TURN_MODE = "page_turn_mode"
        const val COLUMN_MODE = "column_mode"
        const val START_WITH_TTS = "start_with_tts"
        const val DURATION_SEC = "duration_sec"
        const val DOWNLOAD_MODE = "download_mode"
        const val CHAPTERS_DOWNLOADED = "chapters_downloaded"
        const val FAILED_CHAPTERS = "failed_chapters"
        const val VALID_PATH = "valid_path"
        const val NOVELS_COUNT = "novels_count"
        const val CHAPTERS_COUNT = "chapters_count"
        const val PROGRESS_COUNT = "progress_count"
        const val RESTORED_ACCOUNT = "restored_account"
        const val BACKUP_MODE = "backup_mode"
        const val AUTO_OPTIMIZE = "auto_optimize"
    }

    object UserProperties {
        const val APP_LANGUAGE = "app_language"
        const val THEME_MODE = "theme_mode"
        const val READER_TURN_MODE = "reader_turn_mode"
        const val READER_COLUMN_MODE = "reader_column_mode"
        const val EINK_MODE = "eink_mode"
        const val DISTRIBUTION_CHANNEL = "distribution_channel"
    }

    internal interface Backend {
        fun logEvent(eventName: String, params: Map<String, Any>)
        fun setUserProperty(name: String, value: String?)
    }

    private class FirebaseAnalyticsBackend(
        private val analytics: FirebaseAnalytics,
    ) : Backend {
        override fun logEvent(eventName: String, params: Map<String, Any>) {
            runCatching {
                val bundle = Bundle().apply {
                    for ((key, value) in params) {
                        when (value) {
                            is String -> putString(key, value)
                            is Int -> putLong(key, value.toLong())
                            is Long -> putLong(key, value)
                            is Double -> putDouble(key, value)
                            is Float -> putDouble(key, value.toDouble())
                            is Boolean -> putString(key, value.toString())
                            else -> putString(key, value.toString())
                        }
                    }
                }
                analytics.logEvent(eventName, bundle)
            }.onFailure { t ->
                runCatching { Log.w(TAG, "Failed to log GA4 event '$eventName'", t) }
            }
        }

        override fun setUserProperty(name: String, value: String?) {
            runCatching {
                analytics.setUserProperty(name, value)
            }.onFailure { t ->
                runCatching { Log.w(TAG, "Failed to set GA4 user property '$name'", t) }
            }
        }
    }

    @Volatile
    private var resolvedBackend: Backend? = null

    @Volatile
    private var isInitialized: Boolean = false

    @Volatile
    private var testBackend: Backend? = null

    /**
     * Initializes [FirebaseAnalytics] once when GMS is available on the device.
     * Safe to call multiple times or from non-GMS / Robolectric environments.
     */
    fun initialize(context: Context) {
        if (isInitialized || testBackend != null) return
        synchronized(this) {
            if (isInitialized || testBackend != null) return
            val appContext = context.applicationContext ?: context
            if (GoogleServicesHelper.isGmsAvailable(appContext)) {
                resolvedBackend = try {
                    val instance: FirebaseAnalytics? = FirebaseAnalytics.getInstance(appContext)
                    instance?.let(::FirebaseAnalyticsBackend)
                } catch (t: Throwable) {
                    runCatching { Log.w(TAG, "Firebase Analytics init failed", t) }
                    null
                }
            } else {
                runCatching { Log.d(TAG, "Skipping Firebase Analytics init: GMS not available") }
            }
            isInitialized = true
        }
    }

    private fun backend(): Backend? = testBackend ?: resolvedBackend

    internal fun logEvent(eventName: String, params: Map<String, Any> = emptyMap()) {
        val activeBackend = backend()
        if (activeBackend == null) {
            runCatching { Log.v(TAG, "Analytics event dropped (unavailable): $eventName") }
            return
        }
        val sanitized = LinkedHashMap<String, Any>(params.size)
        for ((key, value) in params) {
            sanitized[key] = when (value) {
                is String -> sanitizeParamValue(value)
                else -> value
            }
        }
        activeBackend.logEvent(eventName, sanitized)
    }

    fun setUserProperty(name: String, value: String?) {
        val activeBackend = backend() ?: return
        activeBackend.setUserProperty(name, value?.let(::sanitizeUserPropertyValue))
    }

    fun logScreenView(screenName: String, screenClass: String = screenName) {
        if (screenName.isBlank()) return
        logEvent(
            Events.SCREEN_VIEW,
            mapOf(
                Params.SCREEN_NAME to screenName,
                Params.SCREEN_CLASS to screenClass.ifBlank { screenName },
            ),
        )
    }

    fun logViewNovel(aid: Int, title: String, from: String) {
        if (aid <= 0) return
        logEvent(
            Events.VIEW_ITEM,
            mapOf(
                Params.ITEM_ID to aid.toString(),
                Params.ITEM_NAME to title.ifBlank { "AID #$aid" },
                Params.FROM to from.ifBlank { "default" },
            ),
        )
    }

    fun logSearch(searchTerm: String, mode: String) {
        val trimmed = searchTerm.trim()
        if (trimmed.isEmpty()) return
        logEvent(
            Events.SEARCH,
            mapOf(
                Params.SEARCH_TERM to trimmed,
                Params.SEARCH_MODE to mode,
            ),
        )
    }

    fun logDailyCheckIn(effectiveClick: Boolean, outcome: String) {
        logEvent(
            Events.DAILY_CHECK_IN,
            mapOf(
                Params.EFFECTIVE_CLICK to effectiveClick.toString(),
                Params.OUTCOME to outcome,
            ),
        )
    }

    fun logLogin(method: String, success: Boolean) {
        logEvent(
            Events.LOGIN,
            mapOf(
                Params.METHOD to method,
                Params.SUCCESS to success.toString(),
            ),
        )
    }

    fun logLogout() {
        logEvent(Events.LOGOUT)
    }

    fun logShareNovel(aid: Int) {
        if (aid <= 0) return
        logEvent(
            Events.SHARE,
            mapOf(
                Params.CONTENT_TYPE to "novel",
                Params.ITEM_ID to aid.toString(),
            ),
        )
    }

    fun logBookshelfAdd(aid: Int, source: String) {
        if (aid <= 0) return
        logEvent(
            Events.BOOKSHELF_ADD,
            mapOf(
                Params.ITEM_ID to aid.toString(),
                Params.SOURCE to source,
            ),
        )
    }

    fun logBookshelfRemove(count: Int, source: String) {
        if (count <= 0) return
        logEvent(
            Events.BOOKSHELF_REMOVE,
            mapOf(
                Params.COUNT to count,
                Params.SOURCE to source,
            ),
        )
    }

    fun logBookshelfCloudSync(trigger: String, status: String) {
        logEvent(
            Events.BOOKSHELF_CLOUD_SYNC,
            mapOf(
                Params.TRIGGER to trigger,
                Params.STATUS to status,
            ),
        )
    }

    fun logReaderOpen(
        aid: Int,
        cid: Int,
        pageTurnMode: String,
        columnMode: String,
        startWithTts: Boolean,
        source: String,
    ) {
        if (aid <= 0) return
        logEvent(
            Events.READER_OPEN,
            mapOf(
                Params.ITEM_ID to aid.toString(),
                Params.CHAPTER_ID to cid.toString(),
                Params.PAGE_TURN_MODE to pageTurnMode,
                Params.COLUMN_MODE to columnMode,
                Params.START_WITH_TTS to startWithTts.toString(),
                Params.SOURCE to source,
            ),
        )
    }

    fun logReaderSessionEnd(aid: Int, durationSec: Long) {
        if (aid <= 0 || durationSec <= 0L) return
        logEvent(
            Events.READER_SESSION_END,
            mapOf(
                Params.ITEM_ID to aid.toString(),
                Params.DURATION_SEC to durationSec,
            ),
        )
    }

    fun logNovelDownloadStart(aid: Int, mode: String) {
        if (aid <= 0) return
        logEvent(
            Events.NOVEL_DOWNLOAD_START,
            mapOf(
                Params.ITEM_ID to aid.toString(),
                Params.DOWNLOAD_MODE to mode,
            ),
        )
    }

    fun logNovelDownloadComplete(
        aid: Int,
        mode: String,
        status: String,
        chaptersDownloaded: Int,
        failedChapters: Int,
    ) {
        if (aid <= 0) return
        logEvent(
            Events.NOVEL_DOWNLOAD_COMPLETE,
            mapOf(
                Params.ITEM_ID to aid.toString(),
                Params.DOWNLOAD_MODE to mode,
                Params.STATUS to status,
                Params.CHAPTERS_DOWNLOADED to chaptersDownloaded,
                Params.FAILED_CHAPTERS to failedChapters,
            ),
        )
    }

    fun logReviewPost(aid: Int, success: Boolean) {
        if (aid <= 0) return
        logEvent(
            Events.REVIEW_POST,
            mapOf(
                Params.ITEM_ID to aid.toString(),
                Params.SUCCESS to success.toString(),
            ),
        )
    }

    fun logReviewReply(aid: Int, success: Boolean) {
        if (aid <= 0) return
        logEvent(
            Events.REVIEW_REPLY,
            mapOf(
                Params.ITEM_ID to aid.toString(),
                Params.SUCCESS to success.toString(),
            ),
        )
    }

    fun logLegacyMigrationPathSelection(validPath: Boolean) {
        logEvent(
            Events.SAVE_MIGRATION_PATH_SELECTION,
            mapOf(Params.VALID_PATH to validPath.toString()),
        )
    }

    fun logLegacyMigration(
        source: String,
        status: String,
        novelsCount: Int = 0,
        chaptersCount: Int = 0,
        progressCount: Int = 0,
        restoredAccount: Boolean = false,
    ) {
        logEvent(
            Events.V1_STORAGE_MIGRATION,
            mapOf(
                Params.SOURCE to source,
                Params.STATUS to status,
                Params.NOVELS_COUNT to novelsCount,
                Params.CHAPTERS_COUNT to chaptersCount,
                Params.PROGRESS_COUNT to progressCount,
                Params.RESTORED_ACCOUNT to restoredAccount.toString(),
            ),
        )
    }

    fun logBackupExport(mode: String, status: String) {
        logEvent(
            Events.BACKUP_EXPORT,
            mapOf(
                Params.BACKUP_MODE to mode,
                Params.STATUS to status,
            ),
        )
    }

    fun logBackupImport(status: String) {
        logEvent(
            Events.BACKUP_IMPORT,
            mapOf(Params.STATUS to status),
        )
    }

    fun logNetworkDiagnosticsRun(autoOptimizeEnabled: Boolean) {
        logEvent(
            Events.NETWORK_DIAGNOSTICS_RUN,
            mapOf(Params.AUTO_OPTIMIZE to autoOptimizeEnabled.toString()),
        )
    }

    fun syncUserPreferenceProperties(
        appLanguage: String,
        themeMode: String,
        readerTurnMode: String,
        readerColumnMode: String,
        einkModeEnabled: Boolean,
        distributionChannel: String,
    ) {
        setUserProperty(UserProperties.APP_LANGUAGE, appLanguage)
        setUserProperty(UserProperties.THEME_MODE, themeMode)
        setUserProperty(UserProperties.READER_TURN_MODE, readerTurnMode)
        setUserProperty(UserProperties.READER_COLUMN_MODE, readerColumnMode)
        setUserProperty(UserProperties.EINK_MODE, einkModeEnabled.toString())
        setUserProperty(UserProperties.DISTRIBUTION_CHANNEL, distributionChannel)
    }

    internal fun sanitizeParamValue(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.length <= MAX_PARAM_VALUE_LENGTH) {
            trimmed
        } else {
            trimmed.substring(0, MAX_PARAM_VALUE_LENGTH)
        }
    }

    internal fun sanitizeUserPropertyValue(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.length <= MAX_USER_PROPERTY_VALUE_LENGTH) {
            trimmed
        } else {
            trimmed.substring(0, MAX_USER_PROPERTY_VALUE_LENGTH)
        }
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
            isInitialized = false
        }
    }
}
