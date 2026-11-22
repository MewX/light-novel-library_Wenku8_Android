package org.mewx.wenku8.data.media

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import coil3.Uri
import coil3.key.Keyer
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.request.crossfade
import coil3.size.Size
import okhttp3.Interceptor
import okhttp3.Response
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.ui.theme.LocalEinkMode

/**
 * Centralized Wenku8 image cache-key normalizer, Coil 3 [Keyer], immutable CDN cache-header
 * interceptor, and Compose [ImageRequest] helpers.
 *
 * Goals:
 * 1. **Minimize Wenku8 Server Load**: Normalize novel covers (`wenku8_cover_{aid}`) and chapter
 *    illustrations (`wenku8_illus_{legacyFileName}`) across `http`/`https` and mirror hosts
 *    (`img.wenku8.com`, `pic.wenku8.com`, `pic.wenku8.cn`, relay endpoints) so any asset fetched
 *    once is reused across MemoryCache, DiskCache (`256 MB`), Artbook Lightbox, and Offline Downloads.
 * 2. **Eliminate Shared-Element Cover Flash**: Decode compact Wenku8 `{aid}s.jpg` covers at
 *    [Size.ORIGINAL] with matching `memoryCacheKey` + `placeholderMemoryCacheKey` and `crossfade(false)`
 *    so `DiscoveryNovelCard` / `Bookshelf` / `Search` -> `NovelDetailScreen` transitions render the
 *    cached bitmap synchronously on Frame 1 with zero blank frames.
 */
object WenkuImageRequests {

    const val COIL_DISK_CACHE_DIR_NAME: String = "coil_image_cache"
    const val ARTBOOK_CACHE_DIR_NAME: String = "artbook_images"
    const val COIL_DISK_CACHE_MAX_BYTES: Long = 256L * 1024L * 1024L // 256 MiB
    const val IMMUTABLE_CACHE_CONTROL_HEADER: String = "public, max-age=31536000, immutable"

    private val WENKU_COVER_URL_REGEX =
        Regex("/image/\\d+/(\\d+)/\\1s?\\.jpg", RegexOption.IGNORE_CASE)
    private val WENKU_AVATAR_URL_REGEX =
        Regex("/avatars?/([^?#]+)", RegexOption.IGNORE_CASE)

    fun canonicalCoverCacheKey(aid: Int): String = "wenku8_cover_$aid"

    fun extractCoverAidFromUrl(imageUrl: String): Int? {
        return WENKU_COVER_URL_REGEX.find(imageUrl)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?.takeIf { it > 0 }
    }

    fun canonicalIllustrationCacheKey(imageUrl: String): String? {
        val clean = imageUrl.substringBefore('?').trim()
        if (!clean.contains("/pictures/", ignoreCase = true)) return null
        val legacyFileName = WenkuXmlParsers.generateLegacyImageFileName(clean)
        return if (legacyFileName.isNotEmpty()) {
            "wenku8_illus_$legacyFileName"
        } else {
            null
        }
    }

    /**
     * Computes a deterministic, scheme- and mirror-independent cache key for Wenku8 image URLs.
     * Returns `null` for non-HTTP(S) URIs or unrecognized third-party URLs so Coil's default
     * keyer handles them normally.
     */
    fun canonicalCacheKeyForUrl(imageUrl: String): String? {
        val trimmed = imageUrl.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return null
        }

        extractCoverAidFromUrl(trimmed)?.let { aid ->
            return canonicalCoverCacheKey(aid)
        }

        canonicalIllustrationCacheKey(trimmed)?.let { illusKey ->
            return illusKey
        }

        WENKU_AVATAR_URL_REGEX.find(trimmed)?.groupValues?.getOrNull(1)?.let { avatarTail ->
            return "wenku8_avatar_${avatarTail.lowercase()}"
        }

        return null
    }

    fun isImmutableWenkuStaticAssetUrl(url: String): Boolean {
        return extractCoverAidFromUrl(url) != null ||
            canonicalIllustrationCacheKey(url) != null
    }

    /**
     *Builds a shared-element-safe cover [ImageRequest] guaranteed to share the exact same
     * [Size.ORIGINAL] `MemoryCache` bitmap across list thumbnails (`76x110.dp`) and
     * `NovelDetailScreen`'s hero cover (`116x164.dp`).
     */
    fun buildCoverImageRequest(
        context: Context,
        aid: Int,
        coverUrl: String,
        enableCrossfade: Boolean = false,
    ): ImageRequest? {
        val trimmedUrl = coverUrl.trim()
        if (trimmedUrl.isEmpty()) return null
        val resolvedAid = if (aid > 0) aid else (extractCoverAidFromUrl(trimmedUrl) ?: 0)
        val cacheKey = if (resolvedAid > 0) {
            canonicalCoverCacheKey(resolvedAid)
        } else {
            canonicalCacheKeyForUrl(trimmedUrl) ?: trimmedUrl
        }
        return ImageRequest.Builder(context)
            .data(trimmedUrl)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .placeholderMemoryCacheKey(cacheKey)
            .size(Size.ORIGINAL)
            .crossfade(enableCrossfade)
            .build()
    }

    @Composable
    fun rememberCoverImageRequest(
        aid: Int,
        coverUrl: String,
        enableCrossfade: Boolean = false,
    ): ImageRequest? {
        val context = LocalContext.current
        val isEink = LocalEinkMode.current
        val effectiveCrossfade = enableCrossfade && !isEink
        return remember(context, aid, coverUrl, effectiveCrossfade) {
            buildCoverImageRequest(
                context = context,
                aid = aid,
                coverUrl = coverUrl,
                enableCrossfade = effectiveCrossfade,
            )
        }
    }

    fun buildIllustrationImageRequest(
        context: Context,
        imageUrl: String,
        enableCrossfade: Boolean = false,
    ): ImageRequest? {
        val trimmedUrl = imageUrl.trim()
        if (trimmedUrl.isEmpty()) return null
        val cacheKey = canonicalIllustrationCacheKey(trimmedUrl)
            ?: canonicalCacheKeyForUrl(trimmedUrl)
        return ImageRequest.Builder(context)
            .data(trimmedUrl)
            .apply {
                if (cacheKey != null) {
                    memoryCacheKey(cacheKey)
                    diskCacheKey(cacheKey)
                    placeholderMemoryCacheKey(cacheKey)
                }
            }
            .crossfade(enableCrossfade)
            .build()
    }

    @Composable
    fun rememberIllustrationImageRequest(
        imageUrl: String,
        enableCrossfade: Boolean = false,
    ): ImageRequest? {
        val context = LocalContext.current
        val isEink = LocalEinkMode.current
        val effectiveCrossfade = enableCrossfade && !isEink
        return remember(context, imageUrl, effectiveCrossfade) {
            buildIllustrationImageRequest(
                context = context,
                imageUrl = imageUrl,
                enableCrossfade = effectiveCrossfade,
            )
        }
    }
}

/**
 * Coil 3 [Keyer] registered globally in `AppContainer.buildOfflineFirstImageLoader()` so every
 * Wenku8 cover, illustration, and avatar `Uri` maps to a canonical mirror-agnostic cache key.
 */
object WenkuCanonicalUriKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? {
        return WenkuImageRequests.canonicalCacheKeyForUrl(data.toString())
    }
}

/**
 * OkHttp network response interceptor that stamps long-lived `Cache-Control` headers onto static
 * Wenku8 cover & chapter illustration responses when origin servers omit or shorten `max-age`,
 * preventing Coil's `DiskCache` from issuing redundant revalidation requests.
 */
object WenkuImmutableImageCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (!response.isSuccessful) {
            return response
        }
        val urlString = request.url.toString()
        if (!WenkuImageRequests.isImmutableWenkuStaticAssetUrl(urlString)) {
            return response
        }
        return response.newBuilder()
            .removeHeader("Pragma")
            .removeHeader("Expires")
            .header("Cache-Control", WenkuImageRequests.IMMUTABLE_CACHE_CONTROL_HEADER)
            .build()
    }
}
