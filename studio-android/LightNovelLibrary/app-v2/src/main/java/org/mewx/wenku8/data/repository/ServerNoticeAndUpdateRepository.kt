package org.mewx.wenku8.data.repository

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.BuildConfig
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

data class NoticeActionLink(
    val label: String,
    val url: String,
)

data class ParsedServerNotice(
    val rawHtml: String,
    val plainDisplayText: String,
    val links: List<NoticeActionLink>,
    val contentHash: String,
)

sealed interface AppVersionCheckResult {
    data class UpToDate(
        val currentVersionCode: Int,
        val latestVersionCode: Int,
    ) : AppVersionCheckResult

    data class UpdateAvailable(
        val currentVersionCode: Int,
        val latestVersionCode: Int,
        val downloadPageUrl: String = ServerNoticeAndUpdateRepository.BLOG_DOWNLOAD_URL,
    ) : AppVersionCheckResult

    data object NetworkOrParseFailure : AppVersionCheckResult
}

/**
 * Coroutine replacement for legacy `UpdateNotificationMessage` (`notice_sc` / `notice_tc` +
 * `saves/notice.wk8` + `Wenku8API.NoticeString`) and `CheckAppNewVersion` (`/version`).
 */
class ServerNoticeAndUpdateRepository(
    private val transport: WenkuTransport,
    private val assetRootsProvider: () -> List<File>,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    companion object {
        const val BLOG_DOWNLOAD_URL: String = "https://wenku8.mewx.org/"
        const val VERSION_CHECK_URL: String = "https://wenku8.mewx.org/version"
        const val NOTICE_URL_SC: String = "https://wenku8.mewx.org/args/notice_sc"
        const val NOTICE_URL_TC: String = "https://wenku8.mewx.org/args/notice_tc"

        const val SAVES_DIR_NAME: String = "saves"
        const val NOTICE_FILE_NAME: String = "notice.wk8"
        private const val DISMISSED_NOTICE_HASH_FILE: String = "notice_dismissed.sha256"

        private val ANCHOR_TAG_REGEX = Regex(
            pattern = """<a\s+[^>]*href\s*=\s*["']([^"']+)["'][^>]*>(.*?)</a>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
        private val LINE_BREAK_TAG_REGEX = Regex("""<\s*(br\s*/?|/p|/div)\s*>""", RegexOption.IGNORE_CASE)
        private val ANY_HTML_TAG_REGEX = Regex("""<[^>]+>""")

        /**
         * Parses a raw `NoticeString` payload (plain text or lightweight HTML containing `<a href>`)
         * into clean display text + safe `http`/`https` [NoticeActionLink]s. Returns `null` for
         * empty or `"Unknown"` stub values.
         */
        fun parseNoticeHtmlOrNull(rawNotice: String?): ParsedServerNotice? {
            val trimmed = rawNotice?.trim().orEmpty()
            if (trimmed.isEmpty() || trimmed.equals(Wenku8API.UNKNOWN, ignoreCase = true)) {
                return null
            }

            val extractedLinks = ArrayList<NoticeActionLink>()
            ANCHOR_TAG_REGEX.findAll(trimmed).forEach { match ->
                val rawHref = decodeBasicHtmlEntities(match.groupValues[1].trim())
                val rawLabel = decodeBasicHtmlEntities(
                    ANY_HTML_TAG_REGEX.replace(match.groupValues[2], "").trim(),
                )
                if (rawHref.startsWith("https://", ignoreCase = true) ||
                    rawHref.startsWith("http://", ignoreCase = true)
                ) {
                    extractedLinks.add(
                        NoticeActionLink(
                            label = rawLabel.ifEmpty { rawHref },
                            url = rawHref,
                        ),
                    )
                }
            }

            val withAnchorText = ANCHOR_TAG_REGEX.replace(trimmed) { match ->
                val inner = ANY_HTML_TAG_REGEX.replace(match.groupValues[2], "").trim()
                inner.ifEmpty { match.groupValues[1].trim() }
            }
            val withNewlines = LINE_BREAK_TAG_REGEX.replace(withAnchorText, "\n")
            val stripped = decodeBasicHtmlEntities(ANY_HTML_TAG_REGEX.replace(withNewlines, ""))
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")

            if (stripped.isEmpty() && extractedLinks.isEmpty()) return null

            return ParsedServerNotice(
                rawHtml = trimmed,
                plainDisplayText = stripped,
                links = extractedLinks.distinctBy { it.url },
                contentHash = sha256Hex(trimmed),
            )
        }

        private fun decodeBasicHtmlEntities(text: String): String {
            return text
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
        }

        private fun sha256Hex(input: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(input.toByteArray(StandardCharsets.UTF_8))
            return buildString(digest.size * 2) {
                for (b in digest) {
                    append(String.format("%02x", b))
                }
            }
        }
    }

    private val _activeNotice = MutableStateFlow<ParsedServerNotice?>(null)
    val activeNotice: StateFlow<ParsedServerNotice?> = _activeNotice.asStateFlow()

    private val _latestUpdateResult = MutableStateFlow<AppVersionCheckResult?>(null)
    val latestUpdateResult: StateFlow<AppVersionCheckResult?> = _latestUpdateResult.asStateFlow()

    /**
     * Loads any cached `saves/notice.wk8` immediately, then fetches the latest language-matched
     * notice (`notice_sc` vs `notice_tc`) over [transport]. Dismissed notices stay hidden until
     * the server notice content changes.
     */
    suspend fun loadAndRefreshNotice(appLanguage: Wenku8API.AppLanguage): ParsedServerNotice? =
        withContext(ioDispatcher) {
            val dismissedHash = readDismissedNoticeHash()
            val cachedRaw = readCachedNoticeText()
            parseNoticeHtmlOrNull(cachedRaw)?.let { cachedParsed ->
                syncLegacyNoticeStatic(cachedParsed.rawHtml)
                _activeNotice.value = cachedParsed.takeUnless { it.contentHash == dismissedHash }
            }

            val targetUrl = if (appLanguage == Wenku8API.AppLanguage.SC) {
                NOTICE_URL_SC
            } else {
                NOTICE_URL_TC
            }
            val downloadedBytes = transport.getBytes(targetUrl) ?: return@withContext _activeNotice.value
            val downloadedText = String(downloadedBytes, StandardCharsets.UTF_8).trim()
            val freshParsed = parseNoticeHtmlOrNull(downloadedText) ?: return@withContext _activeNotice.value

            writeCachedNoticeText(freshParsed.rawHtml)
            syncLegacyNoticeStatic(freshParsed.rawHtml)
            val currentDismissedHash = readDismissedNoticeHash()
            val visibleNotice = freshParsed.takeUnless { it.contentHash == currentDismissedHash }
            _activeNotice.value = visibleNotice
            visibleNotice
        }

    suspend fun dismissCurrentNotice() = withContext(ioDispatcher) {
        val current = _activeNotice.value ?: return@withContext
        val primaryRoot = assetRootsProvider().firstOrNull() ?: return@withContext
        val savesDir = File(primaryRoot, SAVES_DIR_NAME).apply { mkdirs() }
        runCatching {
            File(savesDir, DISMISSED_NOTICE_HASH_FILE)
                .writeText(current.contentHash, StandardCharsets.UTF_8)
        }
        _activeNotice.value = null
    }

    /**
     * Checks `https://wenku8.mewx.org/version` against [currentVersionCode].
     */
    suspend fun checkForAppUpdate(
        currentVersionCode: Int = BuildConfig.VERSION_CODE,
    ): AppVersionCheckResult = withContext(ioDispatcher) {
        val rawBytes = transport.getBytes(VERSION_CHECK_URL)
            ?: return@withContext AppVersionCheckResult.NetworkOrParseFailure.also {
                _latestUpdateResult.value = it
            }

        val rawText = String(rawBytes, StandardCharsets.UTF_8).trim()
        val remoteCode = rawText.toIntOrNull()
            ?: return@withContext AppVersionCheckResult.NetworkOrParseFailure.also {
                _latestUpdateResult.value = it
            }

        val result = if (remoteCode > currentVersionCode) {
            AppVersionCheckResult.UpdateAvailable(
                currentVersionCode = currentVersionCode,
                latestVersionCode = remoteCode,
            )
        } else {
            AppVersionCheckResult.UpToDate(
                currentVersionCode = currentVersionCode,
                latestVersionCode = remoteCode,
            )
        }
        _latestUpdateResult.value = result
        result
    }

    private fun readCachedNoticeText(): String? {
        for (root in assetRootsProvider()) {
            val file = File(File(root, SAVES_DIR_NAME), NOTICE_FILE_NAME)
            if (file.isFile && file.canRead()) {
                val content = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
                if (!content.isNullOrBlank()) return content
            }
        }
        return null
    }

    private fun writeCachedNoticeText(noticeText: String) {
        val primaryRoot = assetRootsProvider().firstOrNull() ?: return
        val savesDir = File(primaryRoot, SAVES_DIR_NAME).apply { mkdirs() }
        runCatching {
            File(savesDir, NOTICE_FILE_NAME).writeText(noticeText, StandardCharsets.UTF_8)
        }
    }

    private fun readDismissedNoticeHash(): String? {
        val primaryRoot = assetRootsProvider().firstOrNull() ?: return null
        val file = File(File(primaryRoot, SAVES_DIR_NAME), DISMISSED_NOTICE_HASH_FILE)
        if (!file.isFile) return null
        return runCatching { file.readText(StandardCharsets.UTF_8).trim() }.getOrNull()
    }

    private fun syncLegacyNoticeStatic(rawNotice: String) {
        runCatching {
            Wenku8API.NoticeString = rawNotice
        }
    }
}
