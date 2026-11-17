package org.mewx.wenku8.ui.navigation

import android.content.Intent
import android.net.Uri
import java.util.Locale

/**
 * Canonical URL builder and deep-link parsers for `https://share.wenku8.app/book/<aid>`.
 */
object Wenku8DeepLinks {
    const val SHARE_BOOK_HOST = "share.wenku8.app"
    const val BOOK_PATH_SEGMENT = "book"

    private val SHARE_BOOK_TEXT_REGEX = Regex(
        pattern = """(?:https?://)?share\.wenku8\.app/book/(\d+)(?:\.html?)?/?""",
        option = RegexOption.IGNORE_CASE,
    )

    fun buildShareBookUrl(aid: Int): String = "https://$SHARE_BOOK_HOST/$BOOK_PATH_SEGMENT/$aid"

    /**
     * Extracts a positive `aid` from an `Intent.ACTION_VIEW` deep link targeting
     * `http(s)://share.wenku8.app/book/<aid>`.
     */
    fun parseShareBookIntentAid(intent: Intent?): Int? {
        if (intent?.action != Intent.ACTION_VIEW) return null
        return parseShareBookUriAid(intent.data)
    }

    /**
     * Extracts a positive `aid` from `http(s)://share.wenku8.app/book/<aid>` (allowing optional
     * trailing `/` or `.htm` / `.html` suffix and ignoring query/fragment components).
     */
    fun parseShareBookUriAid(uri: Uri?): Int? {
        if (uri == null) return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        if (host != SHARE_BOOK_HOST) return null

        val segments = uri.pathSegments
        if (segments.size != 2) return null
        if (!segments[0].equals(BOOK_PATH_SEGMENT, ignoreCase = true)) return null

        val rawId = segments[1]
            .removeSuffix(".html")
            .removeSuffix(".htm")
            .trim()
        return rawId.toIntOrNull()?.takeIf { it > 0 }
    }

    /**
     * Extracts a positive `aid` when a user pastes a `share.wenku8.app/book/<aid>` URL or share
     * snippet into the search box.
     */
    fun parseShareBookTextAid(rawText: String): Int? {
        val match = SHARE_BOOK_TEXT_REGEX.find(rawText.trim()) ?: return null
        return match.groupValues.getOrNull(1)?.toIntOrNull()?.takeIf { it > 0 }
    }
}
