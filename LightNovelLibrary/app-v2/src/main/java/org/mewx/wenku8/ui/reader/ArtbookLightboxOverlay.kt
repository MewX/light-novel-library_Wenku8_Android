package org.mewx.wenku8.ui.reader

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.LruCache
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.automirrored.outlined.NavigateBefore
import androidx.compose.material.icons.automirrored.outlined.NavigateNext
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Rotate90DegreesCw
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Dns
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mewx.wenku8.R
import org.mewx.wenku8.Wenku8V2Application
import org.mewx.wenku8.data.media.OfflineFirstWenkuImageFetcher
import org.mewx.wenku8.data.media.WenkuHttpsRedirectUpgradeInterceptor
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.data.media.WenkuImageUrlPolicy
import org.mewx.wenku8.data.media.WenkuImageValidator
import org.mewx.wenku8.data.remote.diagnostics.IpAddressFamily
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.data.remote.transport.CloudflareAcceleratedCallFactory
import org.mewx.wenku8.data.remote.transport.MirrorAttemptTrace
import org.mewx.wenku8.ui.components.TooltipIconButton
import org.mewx.wenku8.ui.components.horizontalScrollFadeEdge
import org.mewx.wenku8.ui.theme.LocalEinkMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Semantic UI test tags for Robolectric & instrumented Compose tests on [ArtbookLightboxOverlay]. */
object ArtbookTestTags {
    const val OVERLAY = "artbook_lightbox_overlay"
    const val PAGER = "artbook_lightbox_pager"
    const val CANVAS_PREFIX = "artbook_canvas_"
    const val TOP_BAR = "artbook_top_bar"
    const val CLOSE_BUTTON = "artbook_close_button"
    const val ROTATE_BUTTON = "artbook_rotate_button"
    const val SAVE_BUTTON = "artbook_save_button"
    const val SHARE_BUTTON = "artbook_share_button"
    const val READER_MODE_CHIP = "artbook_reader_mode_chip"
    const val RETURN_TO_READING_CHIP = "artbook_return_to_reading_chip"
    const val PREV_CHAPTER_CHIP = "artbook_prev_chapter_chip"
    const val NEXT_CHAPTER_CHIP = "artbook_next_chapter_chip"
    const val SPREAD_TOGGLE_CHIP = "artbook_spread_toggle_chip"
    const val SPREAD_OFFSET_CHIP = "artbook_spread_offset_chip"
    const val DIRECTION_TOGGLE_CHIP = "artbook_direction_toggle_chip"
    const val PAGE_INDICATOR = "artbook_page_indicator"
    const val CAROUSEL = "artbook_thumbnail_carousel"
    const val THUMBNAIL_PREFIX = "artbook_thumbnail_"
    const val ERROR_MESSAGE = "artbook_error_message"
    const val RETRY_BUTTON = "artbook_retry_button"
    const val FAILURE_DETAILS_TOGGLE = "artbook_failure_details_toggle"
    const val FAILURE_DETAILS_PANEL = "artbook_failure_details_panel"
    const val FAILURE_DETAILS_COPY_BUTTON = "artbook_failure_details_copy_button"
}

/** High-level human-friendly illustration failure categories (no raw error codes shown by default). */
enum class ArtbookLoadFailureKind {
    OFFLINE_NOT_CACHED,
    TIMEOUT,
    NETWORK_ERROR,
    HTTP_ERROR,
    DECODE_FAILED,
}

/**
 * Structured, privacy-redacted failure context surfaced ONLY when the user expands `"排查信息"`
 * to share a clear bug report (never exposes raw IPs, cookies, or account data).
 */
data class ArtbookLoadFailure(
    val kind: ArtbookLoadFailureKind,
    val stageDetail: String,
    val routeLabel: String,
    val host: String?,
) {
    fun formatDiagnosticSummary(): String = buildString {
        append("阶段: ").append(stageDetail)
        append("\n线路: ").append(routeLabel)
        if (!host.isNullOrBlank()) {
            append("\n主机: ").append(host)
        }
    }
}

sealed interface ArtbookImageResolutionResult {
    data class Success(val file: File) : ArtbookImageResolutionResult
    data class Failure(val failure: ArtbookLoadFailure) : ArtbookImageResolutionResult
}

/**
 * Logical gallery item displayed inside [ArtbookLightboxOverlay].
 *
 * When `跨页拼合` (`口绘` double-page spread mode) is enabled, two consecutive illustrations are
 * paired side-by-side without any center gutter seam, ordered according to `isRtlSpread`:
 * - `isRtlSpread == true` (default Japanese `右开本` bunkobon): first image on the **right**, next
 *   image on the **left**.
 * - `isRtlSpread == false` (`左开本` LTR): first image on the **left**, next image on the **right**.
 */
sealed interface ArtbookEntry {
    val primaryImageIndex: Int
    val containedIndices: List<Int>

    data class Single(
        override val primaryImageIndex: Int,
        val imageUrl: String,
    ) : ArtbookEntry {
        override val containedIndices: List<Int> = listOf(primaryImageIndex)
    }

    data class DoubleSpread(
        override val primaryImageIndex: Int,
        val secondaryImageIndex: Int,
        val leftImageUrl: String,
        val rightImageUrl: String,
        val leftImageIndex: Int,
        val rightImageIndex: Int,
    ) : ArtbookEntry {
        override val containedIndices: List<Int> = listOf(primaryImageIndex, secondaryImageIndex)
    }
}

/**
 * Pure pairing builder for single-page vs `跨页拼合` (`口绘`) double-page spreads.
 *
 * @param firstPageSolo When `true` and [spreadEnabled] is `true`, keeps index `0` (typically a
 *   single-page cover or frontispiece) alone so `(1, 2)`, `(3, 4)`, ... align into true 2-page
 *   `口绘` spreads!
 */
fun buildArtbookEntries(
    imageUrls: List<String>,
    spreadEnabled: Boolean,
    firstPageSolo: Boolean = false,
    isRtlSpread: Boolean = true,
): List<ArtbookEntry> {
    if (imageUrls.isEmpty()) return emptyList()
    if (!spreadEnabled || imageUrls.size == 1) {
        return imageUrls.mapIndexed { idx, url ->
            ArtbookEntry.Single(primaryImageIndex = idx, imageUrl = url)
        }
    }

    val entries = ArrayList<ArtbookEntry>((imageUrls.size + 1) / 2 + 1)
    var index = 0
    if (firstPageSolo) {
        entries.add(ArtbookEntry.Single(primaryImageIndex = 0, imageUrl = imageUrls[0]))
        index = 1
    }
    while (index < imageUrls.size) {
        if (index + 1 < imageUrls.size) {
            val firstIdx = index
            val secondIdx = index + 1
            val firstUrl = imageUrls[firstIdx]
            val secondUrl = imageUrls[secondIdx]
            if (isRtlSpread) {
                entries.add(
                    ArtbookEntry.DoubleSpread(
                        primaryImageIndex = firstIdx,
                        secondaryImageIndex = secondIdx,
                        leftImageUrl = secondUrl,
                        rightImageUrl = firstUrl,
                        leftImageIndex = secondIdx,
                        rightImageIndex = firstIdx,
                    ),
                )
            } else {
                entries.add(
                    ArtbookEntry.DoubleSpread(
                        primaryImageIndex = firstIdx,
                        secondaryImageIndex = secondIdx,
                        leftImageUrl = firstUrl,
                        rightImageUrl = secondUrl,
                        leftImageIndex = firstIdx,
                        rightImageIndex = secondIdx,
                    ),
                )
            }
            index += 2
        } else {
            entries.add(ArtbookEntry.Single(primaryImageIndex = index, imageUrl = imageUrls[index]))
            index += 1
        }
    }
    return entries
}

fun findEntryIndexContainingImage(
    entries: List<ArtbookEntry>,
    targetImageIndex: Int,
): Int {
    val hit = entries.indexOfFirst { targetImageIndex in it.containedIndices }
    return if (hit >= 0) hit else 0
}

private val ARTBOOK_FILMSTRIP_ITEM_WIDTH = 68.dp
private val ARTBOOK_FILMSTRIP_ITEM_HEIGHT = 72.dp
private val ARTBOOK_FILMSTRIP_ITEM_SPACING = 8.dp
private val ARTBOOK_FILMSTRIP_HORIZONTAL_PADDING = 16.dp
private val ARTBOOK_FILMSTRIP_VERTICAL_PADDING = 10.dp

/**
 * Determines whether the bottom thumbnail filmstrip should be visible.
 *
 * Reader illustration lightboxes (`isSingleImagePreview == false`) show the filmstrip even for a
 * single illustration so readers can immediately tell how many images belong to the chapter,
 * whereas standalone cover previews (`isSingleImagePreview == true`) stay clean without a strip.
 */
internal fun shouldShowArtbookThumbnailStrip(
    imageCount: Int,
    isSingleImagePreview: Boolean,
): Boolean = imageCount > 1 || (imageCount == 1 && !isSingleImagePreview)

/**
 * Computes the `scrollOffset` passed to `LazyListState.scrollToItem` / `animateScrollToItem`
 * so the target thumbnail card is horizontally centered inside the filmstrip viewport.
 */
internal fun computeCenteredFilmstripScrollOffsetPx(
    viewportWidthPx: Int,
    itemWidthPx: Int,
    beforeContentPaddingPx: Int,
): Int {
    if (viewportWidthPx <= 0 || itemWidthPx <= 0) return 0
    val targetStartFromViewportLeft = (viewportWidthPx - itemWidthPx) / 2f
    val targetStartFromContentStart = targetStartFromViewportLeft - beforeContentPaddingPx
    return (-targetStartFromContentStart).roundToInt().coerceAtMost(0)
}

/**
 * Tile coordinate in full-resolution source bitmap pixel space for [BitmapRegionDecoder] rendering.
 */
data class HighResTileSpec(
    val partKey: String,
    val sourceRect: Rect,
    val sampleSize: Int,
)

/**
 * Computes the list of visible `512px`-grid source tiles needed for crisp high-DPI zoom rendering.
 */
fun computeVisibleHighResTiles(
    partKey: String,
    imageWidth: Int,
    imageHeight: Int,
    visibleSourceRect: Rect,
    effectiveZoomScale: Float,
    baseTileSizePx: Int = 512,
): List<HighResTileSpec> {
    if (imageWidth <= 0 || imageHeight <= 0 || effectiveZoomScale <= 1.15f) {
        return emptyList()
    }
    val clampedLeft = visibleSourceRect.left.coerceIn(0, imageWidth)
    val clampedTop = visibleSourceRect.top.coerceIn(0, imageHeight)
    val clampedRight = visibleSourceRect.right.coerceIn(0, imageWidth)
    val clampedBottom = visibleSourceRect.bottom.coerceIn(0, imageHeight)
    if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) {
        return emptyList()
    }

    val sampleSize = when {
        effectiveZoomScale >= 2.1f -> 1
        else -> 2
    }
    val stepPx = baseTileSizePx * sampleSize
    val firstCol = clampedLeft / stepPx
    val lastCol = (clampedRight - 1) / stepPx
    val firstRow = clampedTop / stepPx
    val lastRow = (clampedBottom - 1) / stepPx

    val specs = ArrayList<HighResTileSpec>((lastCol - firstCol + 1) * (lastRow - firstRow + 1))
    for (row in firstRow..lastRow) {
        for (col in firstCol..lastCol) {
            val left = col * stepPx
            val top = row * stepPx
            val right = min(imageWidth, left + stepPx)
            val bottom = min(imageHeight, top + stepPx)
            if (right > left && bottom > top) {
                specs.add(
                    HighResTileSpec(
                        partKey = partKey,
                        sourceRect = Rect(left, top, right, bottom),
                        sampleSize = sampleSize,
                    ),
                )
            }
        }
    }
    return specs
}

/**
 * Thread-safe wrapper around [BitmapRegionDecoder] + downsampled base preview [Bitmap] for one
 * local image file.
 */
internal class DecodedArtbookSource(
    val file: File,
    val intrinsicWidth: Int,
    val intrinsicHeight: Int,
    val previewBitmap: Bitmap,
    private val regionDecoder: BitmapRegionDecoder?,
) {
    private val decoderLock = Any()

    fun decodeTile(rect: Rect, sampleSize: Int): Bitmap? {
        val decoder = regionDecoder ?: return null
        val safeRect = Rect(
            rect.left.coerceIn(0, intrinsicWidth),
            rect.top.coerceIn(0, intrinsicHeight),
            rect.right.coerceIn(0, intrinsicWidth),
            rect.bottom.coerceIn(0, intrinsicHeight),
        )
        if (safeRect.width() <= 0 || safeRect.height() <= 0) return null

        return runCatching {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            synchronized(decoderLock) {
                if (decoder.isRecycled) null else decoder.decodeRegion(safeRect, options)
            }
        }.getOrNull()
    }

    fun close() {
        runCatching {
            synchronized(decoderLock) {
                if (regionDecoder != null && !regionDecoder.isRecycled) {
                    regionDecoder.recycle()
                }
            }
        }
    }

    companion object {
        private const val MAX_PREVIEW_EDGE_PX = 1920

        fun open(file: File): DecodedArtbookSource? {
            if (!file.isFile || file.length() <= 0L) return null
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, boundsOpts)
            val fullW = boundsOpts.outWidth
            val fullH = boundsOpts.outHeight
            if (fullW <= 0 || fullH <= 0) return null

            var previewSample = 1
            while (max(fullW / previewSample, fullH / previewSample) > MAX_PREVIEW_EDGE_PX) {
                previewSample *= 2
            }
            val previewOpts = BitmapFactory.Options().apply {
                inSampleSize = previewSample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val preview = BitmapFactory.decodeFile(file.absolutePath, previewOpts) ?: return null
            val regionDecoder = runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    BitmapRegionDecoder.newInstance(file.absolutePath)
                } else {
                    FileInputStream(file).use { stream ->
                        @Suppress("DEPRECATION")
                        BitmapRegionDecoder.newInstance(stream, false)
                    }
                }
            }.getOrNull()

            return DecodedArtbookSource(
                file = file,
                intrinsicWidth = fullW,
                intrinsicHeight = fullH,
                previewBitmap = preview,
                regionDecoder = regionDecoder,
            )
        }
    }
}

/**
 * Offline-first local file resolver for Wenku8 illustrations with corrupt-cache self-healing and
 * mid-body System-DNS fallback:
 * 1. Checks permanent offline storage (`filesDir/saves/imgs/` & `filesDir/imgs/`) and validates
 *    bitmap decodability — automatically deleting corrupt/truncated local files and healing via
 *    network instead of staying permanently stuck!
 * 2. Checks `cacheDir/artbook_images/` and Coil 3's `DiskCache` (validated before promotion).
 * 3. Downloads to a unique per-call `.tmp` file via `sharedDirectGetCallFactory`, verifying HTTP
 *    status, non-text `Content-Type`, exact `Content-Length`, and `BitmapFactory` bounds — and
 *    automatically retrying once over System DNS Happy Eyeballs if a pinned Cloudflare IP fails
 *    mid-body!
 */
object ArtbookImageFileResolver {

    private val cdnHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(Dns.SYSTEM)
            .addNetworkInterceptor(WenkuHttpsRedirectUpgradeInterceptor.DEFAULT)
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(25, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun resolveToSeekableFile(
        context: Context,
        imageUrl: String,
        assetRoots: List<File> = listOf(context.filesDir),
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): File? {
        return (resolveWithDiagnostics(
            context = context,
            imageUrl = imageUrl,
            assetRoots = assetRoots,
            ioDispatcher = ioDispatcher,
        ) as? ArtbookImageResolutionResult.Success)?.file
    }

    suspend fun resolveWithDiagnostics(
        context: Context,
        imageUrl: String,
        assetRoots: List<File> = listOf(context.filesDir),
        forceNetworkRefresh: Boolean = false,
        preferSystemDns: Boolean = false,
        callFactoryOverride: Call.Factory? = null,
        offlineCheckOverride: (() -> Boolean)? = null,
        ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    ): ArtbookImageResolutionResult = withContext(ioDispatcher) {
        val trimmed = imageUrl.trim()
        if (trimmed.isEmpty()) {
            return@withContext ArtbookImageResolutionResult.Failure(
                ArtbookLoadFailure(
                    kind = ArtbookLoadFailureKind.DECODE_FAILED,
                    stageDetail = "空图片地址",
                    routeLabel = "本地",
                    host = null,
                ),
            )
        }

        // Direct local file path or file:// URI support (used in tests or imported local files).
        if (trimmed.startsWith("file://") || trimmed.startsWith("/")) {
            val local = File(trimmed.removePrefix("file://"))
            return@withContext if (WenkuImageValidator.isDecodableBitmapFile(local)) {
                ArtbookImageResolutionResult.Success(local)
            } else {
                ArtbookImageResolutionResult.Failure(
                    ArtbookLoadFailure(
                        kind = ArtbookLoadFailureKind.DECODE_FAILED,
                        stageDetail = if (local.isFile) "本地文件解码失败 (${local.length()} B)" else "本地文件不存在",
                        routeLabel = "本地文件",
                        host = null,
                    ),
                )
            }
        }

        val cacheDir = File(context.cacheDir, WenkuImageRequests.ARTBOOK_CACHE_DIR_NAME).apply {
            mkdirs()
        }
        val safeName = WenkuXmlParsers.generateLegacyImageFileName(trimmed).ifBlank {
            sha256Hex(trimmed) + ".jpg"
        }
        val cachedFile = File(cacheDir, safeName)
        var healedCorruptLocalCache = false

        if (forceNetworkRefresh) {
            WenkuImageValidator.purgeCachedImageForUrl(
                imageUrl = trimmed,
                storageRoots = assetRoots,
                cacheRoot = context.cacheDir,
                appContext = context.applicationContext,
                includePermanentOfflineFiles = true,
            )
        } else {
            // 1. Permanent offline storage (`saves/imgs/` & `imgs/`) + `artbook_images` cache.
            val existingOffline = OfflineFirstWenkuImageFetcher.findExistingOfflineImageFile(
                imageUrl = trimmed,
                storageRoots = assetRoots,
                cacheRoot = context.cacheDir,
                deleteCorruptFiles = true,
            )
            if (existingOffline != null) {
                if (WenkuImageValidator.isDecodableBitmapFile(existingOffline)) {
                    return@withContext ArtbookImageResolutionResult.Success(existingOffline)
                }
                // File passed basic header check yet failed Skia decode -> delete and heal via network!
                healedCorruptLocalCache = true
                runCatching { existingOffline.delete() }
            }

            if (cachedFile.isFile) {
                if (WenkuImageValidator.isDecodableBitmapFile(cachedFile)) {
                    return@withContext ArtbookImageResolutionResult.Success(cachedFile)
                }
                healedCorruptLocalCache = true
                runCatching { cachedFile.delete() }
            }

            // 2. Reuse Coil 3's persistent `DiskCache` ONLY if it decodes cleanly as a bitmap!
            val diskCache = runCatching { SingletonImageLoader.get(context).diskCache }.getOrNull()
            if (diskCache != null) {
                val candidateKeys = listOfNotNull(
                    WenkuImageRequests.canonicalIllustrationCacheKey(trimmed),
                    WenkuImageRequests.canonicalCacheKeyForUrl(trimmed),
                    trimmed,
                ).distinct()
                for (key in candidateKeys) {
                    val snapshot = runCatching { diskCache.openSnapshot(key) }.getOrNull() ?: continue
                    val promoted = snapshot.use { snap ->
                        val dataFile = snap.data.toFile()
                        if (WenkuImageValidator.isDecodableBitmapFile(dataFile)) {
                            val tmpCopy = File(cacheDir, "$safeName.${System.nanoTime()}.coil.tmp")
                            runCatching {
                                dataFile.copyTo(tmpCopy, overwrite = true)
                                if (!tmpCopy.renameTo(cachedFile)) {
                                    tmpCopy.copyTo(cachedFile, overwrite = true)
                                    tmpCopy.delete()
                                }
                                cachedFile.takeIf { it.isFile && it.length() > 0L }
                            }.onFailure {
                                tmpCopy.delete()
                            }.getOrNull()
                        } else {
                            healedCorruptLocalCache = true
                            null
                        }
                    }
                    if (promoted != null) {
                        return@withContext ArtbookImageResolutionResult.Success(promoted)
                    }
                    if (healedCorruptLocalCache) {
                        runCatching { diskCache.remove(key) }
                    }
                }
            }
        }

        // 3. Download from Wenku8 picture CDN using the app's Cloudflare-accelerated `sharedDirectGetCallFactory`.
        val requestUrl = WenkuImageUrlPolicy.upgradeToHttpsIfSupported(trimmed)
        val rawHost = extractHostSafely(requestUrl)
        val schemeAndHost = extractSchemeAndHostSafely(requestUrl) ?: rawHost
        if (!requestUrl.startsWith("http://", ignoreCase = true) &&
            !requestUrl.startsWith("https://", ignoreCase = true)
        ) {
            return@withContext ArtbookImageResolutionResult.Failure(
                ArtbookLoadFailure(
                    kind = ArtbookLoadFailureKind.DECODE_FAILED,
                    stageDetail = "不支持的链接格式",
                    routeLabel = "本地",
                    host = schemeAndHost,
                ),
            )
        }

        val isOffline = offlineCheckOverride?.invoke() ?: isDeviceDefinitelyOffline(context)
        if (isOffline) {
            return@withContext ArtbookImageResolutionResult.Failure(
                ArtbookLoadFailure(
                    kind = ArtbookLoadFailureKind.OFFLINE_NOT_CACHED,
                    stageDetail = if (healedCorruptLocalCache) "本地缓存损坏且当前离线" else "当前离线且未离线缓存",
                    routeLabel = "离线",
                    host = schemeAndHost,
                ),
            )
        }

        val httpCallFactory: Call.Factory = callFactoryOverride
            ?: (context.applicationContext as? Wenku8V2Application)
                ?.appContainer
                ?.sharedDirectGetCallFactory
            ?: cdnHttpClient

        val cfFactory = httpCallFactory as? CloudflareAcceleratedCallFactory
        val pinnedFamily = if (!preferSystemDns && rawHost != null) {
            cfFactory?.activePinnedFamilyForHostOrNull(rawHost)
        } else {
            null
        }

        val primaryFactory: Call.Factory = if (preferSystemDns && cfFactory != null) {
            Call.Factory { cfFactory.newDirectSystemDnsCall(it) }
        } else {
            httpCallFactory
        }
        val primaryRouteLabel = when {
            preferSystemDns -> "系统默认 DNS (手动重试)"
            pinnedFamily == IpAddressFamily.IPV6 -> "优选 IPv6"
            pinnedFamily == IpAddressFamily.IPV4 -> "优选 IPv4"
            else -> "系统默认 DNS"
        }

        val primaryAttempt = downloadAndValidateToFile(
            callFactory = primaryFactory,
            url = requestUrl,
            cacheDir = cacheDir,
            safeName = safeName,
            targetFile = cachedFile,
            routeLabel = primaryRouteLabel,
            fallbackRawHost = rawHost,
            fallbackSchemeAndHost = schemeAndHost,
        )
        if (primaryAttempt.result is ArtbookImageResolutionResult.Success) {
            if (forceNetworkRefresh || healedCorruptLocalCache) {
                WenkuImageValidator.evictCoilCachesForUrl(context.applicationContext, trimmed)
            }
            return@withContext primaryAttempt.result
        }

        // If a pinned Cloudflare edge received HTTP headers and then failed during body streaming,
        // truncation, non-image payload, or decode, report the body failure (immediately cooling
        // down flaky cellular IPv6 edges) and transparently retry ONCE over System DNS Happy Eyeballs!
        // Pre-header failures already fell back inside `CloudflareAcceleratedCallFactory`.
        if (cfFactory != null && pinnedFamily != null && primaryAttempt.sawResponseHeaders) {
            val failedRawHost = primaryAttempt.observedRawHost ?: rawHost
            if (!failedRawHost.isNullOrBlank()) {
                cfFactory.reportPinnedBodyReadFailure(failedRawHost, pinnedFamily)
            }
            val fallbackRouteLabel = "优选 ${pinnedFamily.wireLabel.uppercase(Locale.ROOT)} (已回退系统 DNS)"
            val fallbackAttempt = downloadAndValidateToFile(
                callFactory = Call.Factory { cfFactory.newDirectSystemDnsCall(it) },
                url = requestUrl,
                cacheDir = cacheDir,
                safeName = safeName,
                targetFile = cachedFile,
                routeLabel = fallbackRouteLabel,
                fallbackRawHost = failedRawHost,
                fallbackSchemeAndHost = schemeAndHost,
            )
            if (fallbackAttempt.result is ArtbookImageResolutionResult.Success) {
                if (forceNetworkRefresh || healedCorruptLocalCache) {
                    WenkuImageValidator.evictCoilCachesForUrl(context.applicationContext, trimmed)
                }
            }
            return@withContext fallbackAttempt.result
        }

        primaryAttempt.result
    }

    private data class DownloadAttemptReport(
        val result: ArtbookImageResolutionResult,
        val sawResponseHeaders: Boolean,
        val observedRawHost: String?,
    )

    private fun downloadAndValidateToFile(
        callFactory: Call.Factory,
        url: String,
        cacheDir: File,
        safeName: String,
        targetFile: File,
        routeLabel: String,
        fallbackRawHost: String?,
        fallbackSchemeAndHost: String?,
    ): DownloadAttemptReport {
        val request = try {
            Request.Builder().url(url).get().build()
        } catch (_: IllegalArgumentException) {
            return DownloadAttemptReport(
                result = ArtbookImageResolutionResult.Failure(
                    ArtbookLoadFailure(
                        kind = ArtbookLoadFailureKind.DECODE_FAILED,
                        stageDetail = "图片链接格式异常",
                        routeLabel = routeLabel,
                        host = fallbackSchemeAndHost,
                    ),
                ),
                sawResponseHeaders = false,
                observedRawHost = fallbackRawHost,
            )
        }

        val tempFile = File(cacheDir, "$safeName.${System.nanoTime()}.tmp")
        var observedRawHost = fallbackRawHost
        var observedSchemeAndHost = fallbackSchemeAndHost
        var sawResponseHeaders = false
        val call = callFactory.newCall(request)
        return try {
            call.execute().use { response ->
                sawResponseHeaders = true
                val finalUrl = response.request.url
                observedRawHost = finalUrl.host.takeIf { it.isNotBlank() } ?: fallbackRawHost
                observedSchemeAndHost = "${finalUrl.scheme}://${finalUrl.host}"
                val diagnosticHost = formatDiagnosticHostWithTrace(observedSchemeAndHost, call)
                if (!response.isSuccessful) {
                    return DownloadAttemptReport(
                        result = ArtbookImageResolutionResult.Failure(
                            ArtbookLoadFailure(
                                kind = ArtbookLoadFailureKind.HTTP_ERROR,
                                stageDetail = "HTTP ${response.code}",
                                routeLabel = routeLabel,
                                host = diagnosticHost,
                            ),
                        ),
                        sawResponseHeaders = true,
                        observedRawHost = observedRawHost,
                    )
                }

                val contentType = response.header("Content-Type")
                if (!WenkuImageValidator.isValidImageContentType(contentType)) {
                    val shortType = contentType?.substringBefore(';')?.trim().orEmpty().ifEmpty { "unknown" }
                    return DownloadAttemptReport(
                        result = ArtbookImageResolutionResult.Failure(
                            ArtbookLoadFailure(
                                kind = ArtbookLoadFailureKind.DECODE_FAILED,
                                stageDetail = "返回非图片内容 ($shortType)",
                                routeLabel = routeLabel,
                                host = diagnosticHost,
                            ),
                        ),
                        sawResponseHeaders = true,
                        observedRawHost = observedRawHost,
                    )
                }

                val body = response.body
                val expectedContentLength = body.contentLength()
                val writtenBytes = FileOutputStream(tempFile).use { out ->
                    val copied = body.byteStream().copyTo(out)
                    out.fd.sync()
                    copied
                }

                if (writtenBytes <= 0L) {
                    tempFile.delete()
                    return DownloadAttemptReport(
                        result = ArtbookImageResolutionResult.Failure(
                            ArtbookLoadFailure(
                                kind = ArtbookLoadFailureKind.DECODE_FAILED,
                                stageDetail = "空响应 (0 B)",
                                routeLabel = routeLabel,
                                host = diagnosticHost,
                            ),
                        ),
                        sawResponseHeaders = true,
                        observedRawHost = observedRawHost,
                    )
                }

                if (expectedContentLength > 0L && writtenBytes != expectedContentLength) {
                    tempFile.delete()
                    return DownloadAttemptReport(
                        result = ArtbookImageResolutionResult.Failure(
                            ArtbookLoadFailure(
                                kind = ArtbookLoadFailureKind.NETWORK_ERROR,
                                stageDetail = "传输中断 ($writtenBytes / $expectedContentLength B)",
                                routeLabel = routeLabel,
                                host = diagnosticHost,
                            ),
                        ),
                        sawResponseHeaders = true,
                        observedRawHost = observedRawHost,
                    )
                }

                if (!WenkuImageValidator.isDecodableBitmapFile(tempFile)) {
                    val badLen = tempFile.length()
                    tempFile.delete()
                    return DownloadAttemptReport(
                        result = ArtbookImageResolutionResult.Failure(
                            ArtbookLoadFailure(
                                kind = ArtbookLoadFailureKind.DECODE_FAILED,
                                stageDetail = "图片解码失败 ($badLen B)",
                                routeLabel = routeLabel,
                                host = diagnosticHost,
                            ),
                        ),
                        sawResponseHeaders = true,
                        observedRawHost = observedRawHost,
                    )
                }

                if (targetFile.exists()) {
                    targetFile.delete()
                }
                if (!tempFile.renameTo(targetFile)) {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                }
                DownloadAttemptReport(
                    result = ArtbookImageResolutionResult.Success(targetFile),
                    sawResponseHeaders = true,
                    observedRawHost = observedRawHost,
                )
            }
        } catch (ioError: IOException) {
            tempFile.delete()
            val diagnosticHost = formatDiagnosticHostWithTrace(observedSchemeAndHost, call)
            DownloadAttemptReport(
                result = classifyIoFailure(ioError, routeLabel, diagnosticHost),
                sawResponseHeaders = sawResponseHeaders,
                observedRawHost = observedRawHost,
            )
        } catch (_: Throwable) {
            tempFile.delete()
            val diagnosticHost = formatDiagnosticHostWithTrace(observedSchemeAndHost, call)
            DownloadAttemptReport(
                result = ArtbookImageResolutionResult.Failure(
                    ArtbookLoadFailure(
                        kind = ArtbookLoadFailureKind.NETWORK_ERROR,
                        stageDetail = "加载异常",
                        routeLabel = routeLabel,
                        host = diagnosticHost,
                    ),
                ),
                sawResponseHeaders = sawResponseHeaders,
                observedRawHost = observedRawHost,
            )
        }
    }

    private fun formatDiagnosticHostWithTrace(
        observedSchemeAndHost: String?,
        call: Call,
    ): String? {
        val attempted = call.tag(MirrorAttemptTrace::class)?.attemptedEndpoints.orEmpty()
        if (attempted.size <= 1) {
            return observedSchemeAndHost ?: attempted.firstOrNull()
        }
        val primary = attempted.first()
        val fallbacks = attempted.drop(1).map { it.substringAfter("://", it) }.distinct()
        return if (fallbacks.isNotEmpty()) {
            "$primary (已试 ${fallbacks.joinToString(", ")})"
        } else {
            primary
        }
    }

    private fun classifyIoFailure(
        error: IOException,
        routeLabel: String,
        host: String?,
    ): ArtbookImageResolutionResult.Failure {
        val shortReason = WenkuImageUrlPolicy.sanitizeIoExceptionReason(error)
        val reasonSuffix = shortReason?.let { ": $it" }.orEmpty()
        val parenReason = shortReason?.let { " ($it)" }.orEmpty()
        val failure = when (error) {
            is SocketTimeoutException -> ArtbookLoadFailure(
                kind = ArtbookLoadFailureKind.TIMEOUT,
                stageDetail = "网络读取超时$parenReason",
                routeLabel = routeLabel,
                host = host,
            )
            is UnknownHostException -> ArtbookLoadFailure(
                kind = ArtbookLoadFailureKind.NETWORK_ERROR,
                stageDetail = "域名解析失败",
                routeLabel = routeLabel,
                host = host,
            )
            is SSLException -> ArtbookLoadFailure(
                kind = ArtbookLoadFailureKind.NETWORK_ERROR,
                stageDetail = "TLS 握手失败 (${error.javaClass.simpleName}$reasonSuffix)",
                routeLabel = routeLabel,
                host = host,
            )
            is ConnectException -> ArtbookLoadFailure(
                kind = ArtbookLoadFailureKind.NETWORK_ERROR,
                stageDetail = "连接被拒绝或中断$parenReason",
                routeLabel = routeLabel,
                host = host,
            )
            else -> ArtbookLoadFailure(
                kind = ArtbookLoadFailureKind.NETWORK_ERROR,
                stageDetail = "网络传输中断 (${error.javaClass.simpleName}$reasonSuffix)",
                routeLabel = routeLabel,
                host = host,
            )
        }
        return ArtbookImageResolutionResult.Failure(failure)
    }

    private fun isDeviceDefinitelyOffline(context: Context): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return false
            val active = cm.activeNetwork ?: return true
            val caps = cm.getNetworkCapabilities(active) ?: return false
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    private fun extractHostSafely(url: String): String? {
        return runCatching { URI(url).host?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } }
            .getOrNull()
    }

    private fun extractSchemeAndHostSafely(url: String): String? {
        return runCatching {
            val uri = URI(url)
            val scheme = uri.scheme?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return@runCatching null
            val host = uri.host?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() } ?: return@runCatching null
            "$scheme://$host"
        }.getOrNull()
    }

    private fun sha256Hex(input: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            for (b in digest) {
                append(((b.toInt() and 0xFF) + 0x100).toString(16).substring(1))
            }
        }
    }
}

/**
 * Full-screen Material 3 Expressive (`M3e`) **In-Reader Artbook Lightbox (`插图画集与跨页模式`)** (`P5.3`).
 *
 * Capabilities (`05-screen-by-screen-ui-specifications.md` Screen 8 & `10-next-gen-reader-engine-architecture.md` §6):
 * - High-DPI **`BitmapRegionDecoder` 4× tiling zoom** (`512px` source-grid tiles decoded on
 *   `Dispatchers.IO` and cached in an `LruCache`) + focal pinch-to-zoom (`1.0×..4.0×`) + double-tap
 *   `1.0× <-> 2.5×` toggle.
 * - **`跨页拼合` (`口绘` double-page spread stitching)** with zero center gutter seam, `首张单页`
 *   frontispiece offset toggle, and `右起日漫 (RTL)` / `左起 (LTR)` reading order toggle.
 * - **`90°` spring rotation** (`MotionScheme.expressive().defaultSpatialSpec()`, instant snap in E-Ink).
 * - **1-tap `小说阅读` toggle** when auto-entered on a pure-illustration chapter so the reader can
 *   switch seamlessly between gallery browsing and Bunkobon paged/scroll novel reading.
 * - **M3e `HorizontalMultiBrowseCarousel` thumbnail filmstrip** for rapid browsing across gallery chapters.
 * - **One-tap save to `MediaStore` (`Pictures/Wenku8`)** and **read-only `FileProvider` share**
 *   (automatically stitching 2-page spreads or applying rotation when needed!).
 * - **Vertical drag-to-dismiss** when unzoomed (`zoomScale <= 1.02f`).
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun ArtbookLightboxOverlay(
    novelTitle: String,
    chapterTitle: String,
    imageUrls: List<String>,
    initialImageIndex: Int = 0,
    isAutoEnteredIllustrationChapter: Boolean = false,
    isTemporaryPeek: Boolean = false,
    subtitleOverride: String? = null,
    isSingleImagePreview: Boolean = false,
    saveFileNameSuffix: String? = null,
    onPrimaryImageIndexChanged: ((Int) -> Unit)? = null,
    onSwitchToNovelReadingMode: ((currentImageIndex: Int) -> Unit)? = null,
    onPreviousChapter: (() -> Unit)? = null,
    onNextChapter: (() -> Unit)? = null,
    assetSearchRoots: List<File>? = null,
    imageFileResolver: (suspend (String) -> File?)? = null,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (imageUrls.isEmpty()) return

    val context = LocalContext.current
    val density = LocalDensity.current
    val isEinkMode = LocalEinkMode.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val effectiveAssetRoots = remember(context, assetSearchRoots) {
        assetSearchRoots ?: (context.applicationContext as? Wenku8V2Application)
            ?.appContainer
            ?.resolveLocalAssetRoots()
            ?: listOf(context.filesDir)
    }

    val effectiveFileResolver: suspend (String) -> File? = remember(
        context,
        effectiveAssetRoots,
        imageFileResolver,
        ioDispatcher,
    ) {
        imageFileResolver ?: { url ->
            ArtbookImageFileResolver.resolveToSeekableFile(
                context = context,
                imageUrl = url,
                assetRoots = effectiveAssetRoots,
                ioDispatcher = ioDispatcher,
            )
        }
    }

    val effectiveDiagnosticResolver: suspend (String, Boolean) -> ArtbookImageResolutionResult = remember(
        context,
        effectiveAssetRoots,
        imageFileResolver,
        ioDispatcher,
    ) {
        if (imageFileResolver != null) {
            { url, _ ->
                val resolved = imageFileResolver(url)
                if (resolved != null) {
                    ArtbookImageResolutionResult.Success(resolved)
                } else {
                    ArtbookImageResolutionResult.Failure(
                        ArtbookLoadFailure(
                            kind = ArtbookLoadFailureKind.NETWORK_ERROR,
                            stageDetail = "图片获取失败",
                            routeLabel = "自定义加载器",
                            host = runCatching { URI(url).host }.getOrNull(),
                        ),
                    )
                }
            }
        } else {
            { url, isUserRetry ->
                ArtbookImageFileResolver.resolveWithDiagnostics(
                    context = context,
                    imageUrl = url,
                    assetRoots = effectiveAssetRoots,
                    forceNetworkRefresh = isUserRetry,
                    preferSystemDns = isUserRetry,
                    ioDispatcher = ioDispatcher,
                )
            }
        }
    }

    var thumbnailRefreshEpoch by remember { mutableIntStateOf(0) }

    var isChromeVisible by rememberSaveable { mutableStateOf(true) }
    var isSpreadModeEnabled by rememberSaveable { mutableStateOf(false) }
    var isFirstPageSolo by rememberSaveable { mutableStateOf(false) }
    var isRtlSpread by rememberSaveable { mutableStateOf(true) }
    var rotationQuarterTurns by rememberSaveable(imageUrls) { mutableIntStateOf(0) }
    var activePrimaryImageIndex by rememberSaveable(imageUrls, initialImageIndex) {
        mutableIntStateOf(initialImageIndex.coerceIn(0, imageUrls.lastIndex))
    }

    val entries = remember(imageUrls, isSpreadModeEnabled, isFirstPageSolo, isRtlSpread) {
        buildArtbookEntries(
            imageUrls = imageUrls,
            spreadEnabled = isSpreadModeEnabled,
            firstPageSolo = isFirstPageSolo,
            isRtlSpread = isRtlSpread,
        )
    }

    val initialEntryIdx = remember(entries) {
        findEntryIndexContainingImage(entries, activePrimaryImageIndex)
    }
    val pagerState = rememberPagerState(
        initialPage = initialEntryIdx.coerceIn(0, (entries.size - 1).coerceAtLeast(0)),
        pageCount = { entries.size },
    )

    // Keep pager aligned with the user's current illustration whenever `跨页拼合` or `首张单页` toggles.
    LaunchedEffect(entries) {
        val targetEntryIdx = findEntryIndexContainingImage(entries, activePrimaryImageIndex)
            .coerceIn(0, (entries.size - 1).coerceAtLeast(0))
        if (pagerState.currentPage != targetEntryIdx) {
            pagerState.scrollToPage(targetEntryIdx)
        }
    }

    LaunchedEffect(pagerState.settledPage, entries) {
        entries.getOrNull(pagerState.settledPage)?.let { settled ->
            activePrimaryImageIndex = settled.primaryImageIndex
            onPrimaryImageIndexChanged?.invoke(settled.primaryImageIndex)
        }
    }

    val showThumbnailStrip = shouldShowArtbookThumbnailStrip(
        imageCount = imageUrls.size,
        isSingleImagePreview = isSingleImagePreview,
    )
    // Hoist `LazyListState` outside `AnimatedVisibility` so toggling chrome preserves manual
    // scroll position, while main-pager / chapter changes smoothly keep the active thumb centered.
    val thumbnailListState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialImageIndex.coerceIn(0, imageUrls.lastIndex),
    )
    var lastSyncedFilmstripKey by remember { mutableStateOf<Pair<List<String>, Int>?>(null) }

    LaunchedEffect(showThumbnailStrip, isChromeVisible, imageUrls, activePrimaryImageIndex, isEinkMode) {
        if (!showThumbnailStrip || !isChromeVisible) return@LaunchedEffect
        val targetIndex = activePrimaryImageIndex.coerceIn(0, imageUrls.lastIndex)
        val syncKey = imageUrls to targetIndex
        if (lastSyncedFilmstripKey == syncKey) return@LaunchedEffect
        val viewportWidthPx = snapshotFlow { thumbnailListState.layoutInfo.viewportSize.width }
            .first { it > 0 }
        val itemWidthPx = with(density) { ARTBOOK_FILMSTRIP_ITEM_WIDTH.roundToPx() }
        val beforePaddingPx = with(density) { ARTBOOK_FILMSTRIP_HORIZONTAL_PADDING.roundToPx() }
        val centeredOffset = computeCenteredFilmstripScrollOffsetPx(
            viewportWidthPx = viewportWidthPx,
            itemWidthPx = itemWidthPx,
            beforeContentPaddingPx = beforePaddingPx,
        )
        val isSameGallery = lastSyncedFilmstripKey?.first == imageUrls
        if (!isSameGallery || isEinkMode) {
            thumbnailListState.scrollToItem(targetIndex, centeredOffset)
        } else {
            thumbnailListState.animateScrollToItem(targetIndex, centeredOffset)
        }
        lastSyncedFilmstripKey = syncKey
    }

    var currentPageZoom by remember { mutableFloatStateOf(1f) }
    var verticalDismissOffsetPx by remember { mutableFloatStateOf(0f) }
    val dismissThresholdPx = with(density) { 128.dp.toPx() }

    BackHandler(onBack = onDismiss)

    val spatialRotationSpec = if (isEinkMode) {
        snap<Float>()
    } else {
        MotionScheme.expressive().defaultSpatialSpec()
    }
    val animatedRotationDeg by animateFloatAsState(
        targetValue = (rotationQuarterTurns * 90).toFloat(),
        animationSpec = spatialRotationSpec,
        label = "artbookRotationDeg",
    )

    val dismissProgress = (abs(verticalDismissOffsetPx) / (dismissThresholdPx * 2.2f)).coerceIn(0f, 0.65f)
    val backdropAlpha = if (isEinkMode) 1f else (1.0f - dismissProgress).coerceIn(0.32f, 1.0f)

    val currentEntry = entries.getOrNull(pagerState.currentPage) ?: entries.first()
    val pageIndicatorLabel = when (currentEntry) {
        is ArtbookEntry.Single -> "${currentEntry.primaryImageIndex + 1} / ${imageUrls.size}"
        is ArtbookEntry.DoubleSpread -> {
            val a = currentEntry.primaryImageIndex + 1
            val b = currentEntry.secondaryImageIndex + 1
            "$a-$b / ${imageUrls.size}"
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0C10).copy(alpha = backdropAlpha))
            .pointerInput(currentPageZoom) {
                if (currentPageZoom <= 1.02f) {
                    detectVerticalDragGestures(
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            verticalDismissOffsetPx += dragAmount
                        },
                        onDragEnd = {
                            if (abs(verticalDismissOffsetPx) >= dismissThresholdPx) {
                                onDismiss()
                            } else {
                                verticalDismissOffsetPx = 0f
                            }
                        },
                        onDragCancel = {
                            verticalDismissOffsetPx = 0f
                        },
                    )
                }
            }
            .testTag(ArtbookTestTags.OVERLAY),
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = currentPageZoom <= 1.02f && abs(verticalDismissOffsetPx) < 8f,
            beyondViewportPageCount = 1,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = verticalDismissOffsetPx
                    val dragScale = 1f - (dismissProgress * 0.12f)
                    scaleX = dragScale
                    scaleY = dragScale
                }
                .testTag(ArtbookTestTags.PAGER),
        ) { entryIndex ->
            val entry = entries[entryIndex]
            ArtbookEntryViewport(
                entry = entry,
                entryIndex = entryIndex,
                isCurrentPage = entryIndex == pagerState.currentPage,
                rotationDegrees = animatedRotationDeg,
                diagnosticResolver = effectiveDiagnosticResolver,
                ioDispatcher = ioDispatcher,
                onZoomChanged = { zoom ->
                    if (entryIndex == pagerState.currentPage) {
                        currentPageZoom = zoom
                    }
                },
                onSingleTapToggleChrome = {
                    isChromeVisible = !isChromeVisible
                },
                onRetryRecovered = {
                    thumbnailRefreshEpoch += 1
                },
                onDiagnosticCopied = {
                    coroutineScope.launch {
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.artbook_diagnostics_copied),
                        )
                    }
                },
            )
        }

        // Top Chrome: Close, Title + Page Indicator, 90° Rotate, Save to MediaStore, Share, plus Spread Chips.
        AnimatedVisibility(
            visible = isChromeVisible,
            enter = if (isEinkMode) fadeIn(snap()) else fadeIn(),
            exit = if (isEinkMode) fadeOut(snap()) else fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                color = Color(0xD914171F),
                contentColor = Color.White,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(ArtbookTestTags.TOP_BAR),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        val closeOrBackLabel = when {
                            isTemporaryPeek -> stringResource(R.string.artbook_action_return_to_reading)
                            isAutoEnteredIllustrationChapter -> stringResource(R.string.action_back)
                            isSingleImagePreview -> stringResource(R.string.artbook_action_close_image)
                            else -> stringResource(R.string.artbook_action_close)
                        }
                        TooltipIconButton(
                            tooltipText = closeOrBackLabel,
                            onClick = onDismiss,
                            modifier = Modifier.testTag(ArtbookTestTags.CLOSE_BUTTON),
                        ) {
                            Icon(
                                imageVector = if (isTemporaryPeek || isAutoEnteredIllustrationChapter) {
                                    Icons.AutoMirrored.Outlined.ArrowBack
                                } else {
                                    Icons.Outlined.Close
                                },
                                contentDescription = closeOrBackLabel,
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 4.dp),
                        ) {
                            Text(
                                text = chapterTitle.ifBlank { novelTitle }.ifBlank { "插图画集" },
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = subtitleOverride?.takeIf { it.isNotBlank() } ?: pageIndicatorLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFFB8C0CC),
                                maxLines = 1,
                                softWrap = false,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.testTag(ArtbookTestTags.PAGE_INDICATOR),
                            )
                        }

                        val rotateLabel = stringResource(R.string.artbook_action_rotate_90)
                        TooltipIconButton(
                            tooltipText = rotateLabel,
                            onClick = { rotationQuarterTurns = (rotationQuarterTurns + 1) % 4 },
                            modifier = Modifier.testTag(ArtbookTestTags.ROTATE_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Rotate90DegreesCw,
                                contentDescription = rotateLabel,
                            )
                        }

                        val saveLabel = stringResource(
                            if (isSingleImagePreview) {
                                R.string.artbook_action_save_image
                            } else {
                                R.string.artbook_action_save
                            },
                        )
                        val saveSuccessSnackbar = stringResource(R.string.artbook_save_success_snackbar)
                        val saveFailedSnackbar = stringResource(R.string.artbook_save_failed_snackbar)
                        TooltipIconButton(
                            tooltipText = saveLabel,
                            onClick = {
                                coroutineScope.launch {
                                    val savedOk = saveArtbookEntryToMediaStore(
                                        context = context,
                                        novelTitle = novelTitle,
                                        entry = currentEntry,
                                        rotationQuarterTurns = rotationQuarterTurns,
                                        imageFileResolver = effectiveFileResolver,
                                        ioDispatcher = ioDispatcher,
                                        fileNameSuffixOverride = saveFileNameSuffix,
                                    )
                                    snackbarHostState.showSnackbar(
                                        if (savedOk) saveSuccessSnackbar else saveFailedSnackbar,
                                    )
                                }
                            },
                            modifier = Modifier.testTag(ArtbookTestTags.SAVE_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Download,
                                contentDescription = saveLabel,
                            )
                        }

                        val shareLabel = stringResource(
                            if (isSingleImagePreview) {
                                R.string.artbook_action_share_image
                            } else {
                                R.string.artbook_action_share
                            },
                        )
                        val shareFailedSnackbar = stringResource(R.string.artbook_share_failed_snackbar)
                        TooltipIconButton(
                            tooltipText = shareLabel,
                            onClick = {
                                coroutineScope.launch {
                                    val sharedOk = shareArtbookEntryViaFileProvider(
                                        context = context,
                                        novelTitle = novelTitle,
                                        entry = currentEntry,
                                        rotationQuarterTurns = rotationQuarterTurns,
                                        imageFileResolver = effectiveFileResolver,
                                        ioDispatcher = ioDispatcher,
                                        chooserTitle = shareLabel,
                                        fileNameTag = saveFileNameSuffix,
                                    )
                                    if (!sharedOk) {
                                        snackbarHostState.showSnackbar(shareFailedSnackbar)
                                    }
                                }
                            },
                            modifier = Modifier.testTag(ArtbookTestTags.SHARE_BUTTON),
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Share,
                                contentDescription = shareLabel,
                            )
                        }
                    }

                    val showActionChipRow = isTemporaryPeek ||
                        onSwitchToNovelReadingMode != null ||
                        imageUrls.size >= 2 ||
                        onPreviousChapter != null ||
                        onNextChapter != null
                    if (showActionChipRow) {
                        val chipRowScrollState = rememberScrollState()
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScrollFadeEdge(
                                    scrollState = chipRowScrollState,
                                    fadeColor = Color(0xD914171F),
                                    isEink = isEinkMode,
                                )
                                .horizontalScroll(chipRowScrollState)
                                .padding(horizontal = 8.dp, vertical = 2.dp),
                        ) {
                            if (isTemporaryPeek) {
                                FilterChip(
                                    selected = false,
                                    onClick = onDismiss,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = stringResource(R.string.artbook_action_return_to_reading),
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    },
                                    colors = artbookChipColors(),
                                    modifier = Modifier.testTag(ArtbookTestTags.RETURN_TO_READING_CHIP),
                                )
                            }

                            if (!isTemporaryPeek && onSwitchToNovelReadingMode != null) {
                                FilterChip(
                                    selected = false,
                                    onClick = { onSwitchToNovelReadingMode(activePrimaryImageIndex) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.AutoStories,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "小说阅读",
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    },
                                    colors = artbookChipColors(),
                                    modifier = Modifier.testTag(ArtbookTestTags.READER_MODE_CHIP),
                                )
                            }

                            if (imageUrls.size >= 2) {
                                FilterChip(
                                    selected = isSpreadModeEnabled,
                                    onClick = { isSpreadModeEnabled = !isSpreadModeEnabled },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "跨页拼合",
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    },
                                    colors = artbookChipColors(),
                                    modifier = Modifier.testTag(ArtbookTestTags.SPREAD_TOGGLE_CHIP),
                                )

                                if (isSpreadModeEnabled) {
                                    FilterChip(
                                        selected = isFirstPageSolo,
                                        onClick = { isFirstPageSolo = !isFirstPageSolo },
                                        label = {
                                            Text(
                                                text = "首张单页",
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        },
                                        colors = artbookChipColors(),
                                        modifier = Modifier.testTag(ArtbookTestTags.SPREAD_OFFSET_CHIP),
                                    )
                                    FilterChip(
                                        selected = isRtlSpread,
                                        onClick = { isRtlSpread = !isRtlSpread },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Outlined.SwapHoriz,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        },
                                        label = {
                                            Text(
                                                text = if (isRtlSpread) "右起日漫 (RTL)" else "左起 (LTR)",
                                                maxLines = 1,
                                                softWrap = false,
                                            )
                                        },
                                        colors = artbookChipColors(),
                                        modifier = Modifier.testTag(ArtbookTestTags.DIRECTION_TOGGLE_CHIP),
                                    )
                                }
                            }

                            if (!isTemporaryPeek && onPreviousChapter != null) {
                                FilterChip(
                                    selected = false,
                                    onClick = onPreviousChapter,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.NavigateBefore,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "上一章",
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    },
                                    colors = artbookChipColors(),
                                    modifier = Modifier.testTag(ArtbookTestTags.PREV_CHAPTER_CHIP),
                                )
                            }

                            if (!isTemporaryPeek && onNextChapter != null) {
                                FilterChip(
                                    selected = false,
                                    onClick = onNextChapter,
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Outlined.NavigateNext,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = "下一章",
                                            maxLines = 1,
                                            softWrap = false,
                                        )
                                    },
                                    colors = artbookChipColors(),
                                    modifier = Modifier.testTag(ArtbookTestTags.NEXT_CHAPTER_CHIP),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Chrome: Snackbar + Free-Scrolling Inertial LazyRow Thumbnail Filmstrip.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            AnimatedVisibility(
                visible = isChromeVisible && showThumbnailStrip,
                enter = if (isEinkMode) fadeIn(snap()) else fadeIn(),
                exit = if (isEinkMode) fadeOut(snap()) else fadeOut(),
            ) {
                Surface(
                    color = Color(0xD914171F),
                    contentColor = Color.White,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    LazyRow(
                        state = thumbnailListState,
                        contentPadding = PaddingValues(
                            horizontal = ARTBOOK_FILMSTRIP_HORIZONTAL_PADDING,
                            vertical = ARTBOOK_FILMSTRIP_VERTICAL_PADDING,
                        ),
                        horizontalArrangement = Arrangement.spacedBy(
                            space = ARTBOOK_FILMSTRIP_ITEM_SPACING,
                            alignment = Alignment.CenterHorizontally,
                        ),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp)
                            .testTag(ArtbookTestTags.CAROUSEL),
                    ) {
                        items(
                            count = imageUrls.size,
                            key = { thumbIdx -> "$thumbIdx:${imageUrls[thumbIdx]}" },
                        ) { thumbIdx ->
                            val isHighlighted = thumbIdx in currentEntry.containedIndices
                            val borderColor = if (isHighlighted) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                Color(0x55FFFFFF)
                            }
                            Box(
                                modifier = Modifier
                                    .size(
                                        width = ARTBOOK_FILMSTRIP_ITEM_WIDTH,
                                        height = ARTBOOK_FILMSTRIP_ITEM_HEIGHT,
                                    )
                                    .clip(MaterialTheme.shapes.small)
                                    .border(
                                        width = if (isHighlighted) 2.5.dp else 1.dp,
                                        color = borderColor,
                                        shape = MaterialTheme.shapes.small,
                                    )
                                    .clickable {
                                        activePrimaryImageIndex = thumbIdx
                                        onPrimaryImageIndexChanged?.invoke(thumbIdx)
                                        val targetEntry = findEntryIndexContainingImage(entries, thumbIdx)
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(targetEntry)
                                        }
                                    }
                                    .testTag("${ArtbookTestTags.THUMBNAIL_PREFIX}$thumbIdx"),
                            ) {
                                androidx.compose.runtime.key(thumbnailRefreshEpoch, imageUrls[thumbIdx]) {
                                    AsyncImage(
                                        model = WenkuImageRequests.rememberIllustrationImageRequest(imageUrls[thumbIdx]),
                                        contentDescription = "插图 ${thumbIdx + 1}",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                Surface(
                                    shape = CircleShape,
                                    color = Color(0xAA000000),
                                    contentColor = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(3.dp),
                                ) {
                                    Text(
                                        text = "${thumbIdx + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun artbookChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = Color(0x33FFFFFF),
    labelColor = Color.White,
    iconColor = Color.White,
    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
)

@Composable
private fun ArtbookEntryViewport(
    entry: ArtbookEntry,
    entryIndex: Int,
    isCurrentPage: Boolean,
    rotationDegrees: Float,
    diagnosticResolver: suspend (url: String, isUserRetry: Boolean) -> ArtbookImageResolutionResult,
    ioDispatcher: CoroutineDispatcher,
    onZoomChanged: (Float) -> Unit,
    onSingleTapToggleChrome: () -> Unit,
    onRetryRecovered: () -> Unit = {},
    onDiagnosticCopied: () -> Unit = {},
) {
    val context = LocalContext.current
    var leftSource by remember(entry) { mutableStateOf<DecodedArtbookSource?>(null) }
    var rightSource by remember(entry) { mutableStateOf<DecodedArtbookSource?>(null) }
    var isLoading by remember(entry) { mutableStateOf(true) }
    var loadFailed by remember(entry) { mutableStateOf(false) }
    var loadFailure by remember(entry) { mutableStateOf<ArtbookLoadFailure?>(null) }
    var retryNonce by remember(entry) { mutableIntStateOf(0) }
    var showFailureDetails by remember(entry, retryNonce) { mutableStateOf(false) }

    DisposableEffect(entry) {
        onDispose {
            leftSource?.close()
            rightSource?.close()
        }
    }

    LaunchedEffect(entry, retryNonce) {
        val isUserRetry = retryNonce > 0
        isLoading = true
        loadFailed = false
        loadFailure = null
        leftSource?.close()
        rightSource?.close()
        leftSource = null
        rightSource = null

        when (entry) {
            is ArtbookEntry.Single -> {
                val (decoded, failure) = withContext(ioDispatcher) {
                    when (val res = diagnosticResolver(entry.imageUrl, isUserRetry)) {
                        is ArtbookImageResolutionResult.Success -> {
                            val bmp = DecodedArtbookSource.open(res.file)
                            if (bmp != null) {
                                bmp to null
                            } else {
                                null to ArtbookLoadFailure(
                                    kind = ArtbookLoadFailureKind.DECODE_FAILED,
                                    stageDetail = "本地解码失败 (${res.file.length()} B)",
                                    routeLabel = "本地解码",
                                    host = runCatching { URI(entry.imageUrl).host }.getOrNull(),
                                )
                            }
                        }
                        is ArtbookImageResolutionResult.Failure -> null to res.failure
                    }
                }
                if (decoded != null && decoded.intrinsicWidth > 0 && decoded.intrinsicHeight > 0) {
                    ReaderIllustrationAspectRatioCache.put(
                        imageUrl = entry.imageUrl,
                        aspectRatio = decoded.intrinsicWidth.toFloat() / decoded.intrinsicHeight.toFloat(),
                    )
                }
                leftSource = decoded
                rightSource = null
                loadFailed = decoded == null
                loadFailure = failure
                if (isUserRetry && decoded != null) {
                    onRetryRecovered()
                }
            }
            is ArtbookEntry.DoubleSpread -> {
                val (decodedL, decodedR, failure) = withContext(ioDispatcher) {
                    val resL = diagnosticResolver(entry.leftImageUrl, isUserRetry)
                    val resR = diagnosticResolver(entry.rightImageUrl, isUserRetry)
                    val bmpL = (resL as? ArtbookImageResolutionResult.Success)?.file?.let { DecodedArtbookSource.open(it) }
                    val bmpR = (resR as? ArtbookImageResolutionResult.Success)?.file?.let { DecodedArtbookSource.open(it) }
                    val firstFail = (resL as? ArtbookImageResolutionResult.Failure)?.failure
                        ?: (resR as? ArtbookImageResolutionResult.Failure)?.failure
                        ?: if (bmpL == null && bmpR == null) {
                            ArtbookLoadFailure(
                                kind = ArtbookLoadFailureKind.DECODE_FAILED,
                                stageDetail = "跨页图片解码失败",
                                routeLabel = "本地解码",
                                host = runCatching { URI(entry.leftImageUrl).host }.getOrNull(),
                            )
                        } else {
                            null
                        }
                    Triple(bmpL, bmpR, firstFail)
                }
                if (decodedL != null && decodedL.intrinsicWidth > 0 && decodedL.intrinsicHeight > 0) {
                    ReaderIllustrationAspectRatioCache.put(
                        imageUrl = entry.leftImageUrl,
                        aspectRatio = decodedL.intrinsicWidth.toFloat() / decodedL.intrinsicHeight.toFloat(),
                    )
                }
                if (decodedR != null && decodedR.intrinsicWidth > 0 && decodedR.intrinsicHeight > 0) {
                    ReaderIllustrationAspectRatioCache.put(
                        imageUrl = entry.rightImageUrl,
                        aspectRatio = decodedR.intrinsicWidth.toFloat() / decodedR.intrinsicHeight.toFloat(),
                    )
                }
                leftSource = decodedL
                rightSource = decodedR
                loadFailed = decodedL == null && decodedR == null
                loadFailure = if (loadFailed) failure else null
                if (isUserRetry && !loadFailed) {
                    onRetryRecovered()
                }
            }
        }
        isLoading = false
    }

    var zoomScale by remember(entry) { mutableFloatStateOf(1f) }
    var panOffset by remember(entry) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(isCurrentPage) {
        if (!isCurrentPage && zoomScale != 1f) {
            zoomScale = 1f
            panOffset = Offset.Zero
        }
    }

    LaunchedEffect(zoomScale, isCurrentPage) {
        if (isCurrentPage) {
            onZoomChanged(zoomScale)
        }
    }

    // LRU cache of decoded high-DPI `BitmapRegionDecoder` tiles (capped at ~24 MB).
    val tileCache = remember(entry) {
        object : LruCache<String, Bitmap>(24 * 1024) {
            override fun sizeOf(key: String, value: Bitmap): Int {
                return (value.allocationByteCount / 1024).coerceAtLeast(1)
            }
        }
    }
    val decodedTileMap = remember(entry) { mutableStateMapOf<String, Bitmap>() }

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier = Modifier.fillMaxSize(),
    ) {
        val viewportW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

        val srcL = leftSource
        val srcR = rightSource

        if (isLoading) {
            CircularProgressIndicator(color = Color.White)
            return@BoxWithConstraints
        }
        if (loadFailed || srcL == null) {
            val activeFailure = loadFailure
            val friendlyMessageRes = when (activeFailure?.kind) {
                ArtbookLoadFailureKind.OFFLINE_NOT_CACHED -> R.string.artbook_error_offline_not_cached
                ArtbookLoadFailureKind.TIMEOUT -> R.string.artbook_error_timeout
                ArtbookLoadFailureKind.HTTP_ERROR -> R.string.artbook_error_http
                ArtbookLoadFailureKind.DECODE_FAILED -> R.string.artbook_error_decode
                else -> R.string.artbook_image_load_failed
            }
            val errorIcon = if (activeFailure?.kind == ArtbookLoadFailureKind.OFFLINE_NOT_CACHED) {
                Icons.Outlined.WifiOff
            } else {
                Icons.Outlined.BrokenImage
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp),
            ) {
                Icon(
                    imageVector = errorIcon,
                    contentDescription = null,
                    tint = Color(0xFFB8C0CC),
                    modifier = Modifier.size(36.dp),
                )
                Text(
                    text = stringResource(friendlyMessageRes),
                    color = Color(0xFFE0E5EE),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag(ArtbookTestTags.ERROR_MESSAGE),
                )
                FilledTonalButton(
                    onClick = { retryNonce += 1 },
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = Color(0x33FFFFFF),
                        contentColor = Color.White,
                    ),
                    modifier = Modifier.testTag(ArtbookTestTags.RETRY_BUTTON),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.artbook_action_retry))
                }
                if (activeFailure != null) {
                    TextButton(
                        onClick = { showFailureDetails = !showFailureDetails },
                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0x99D0D6E0)),
                        modifier = Modifier.testTag(ArtbookTestTags.FAILURE_DETAILS_TOGGLE),
                    ) {
                        Text(
                            text = stringResource(
                                if (showFailureDetails) {
                                    R.string.artbook_diagnostics_toggle_hide
                                } else {
                                    R.string.artbook_diagnostics_toggle_show
                                },
                            ),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = if (showFailureDetails) {
                                Icons.Outlined.ExpandLess
                            } else {
                                Icons.Outlined.ExpandMore
                            },
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    if (showFailureDetails) {
                        val summaryText = activeFailure.formatDiagnosticSummary()
                        Surface(
                            color = Color(0xCC161A22),
                            shape = MaterialTheme.shapes.medium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color(0x33FFFFFF), MaterialTheme.shapes.medium)
                                .testTag(ArtbookTestTags.FAILURE_DETAILS_PANEL),
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = summaryText,
                                    color = Color(0xFFC8D0DC),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    TextButton(
                                        onClick = {
                                            runCatching {
                                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                                clipboard?.setPrimaryClip(
                                                    ClipData.newPlainText("artbook_diagnostic", summaryText),
                                                )
                                                onDiagnosticCopied()
                                            }
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFD0E4FF)),
                                        modifier = Modifier.testTag(ArtbookTestTags.FAILURE_DETAILS_COPY_BUTTON),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.artbook_diagnostics_copy_action),
                                            style = MaterialTheme.typography.labelSmall,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            return@BoxWithConstraints
        }

        // Normalize spread height to the taller half so mismatched scan DPIs still align edge-to-edge!
        val virtualHeight = if (srcR != null) {
            max(srcL.intrinsicHeight, srcR.intrinsicHeight).toFloat()
        } else {
            srcL.intrinsicHeight.toFloat()
        }
        val leftVirtualWidth = srcL.intrinsicWidth.toFloat() * (virtualHeight / srcL.intrinsicHeight.toFloat())
        val rightVirtualWidth = if (srcR != null) {
            srcR.intrinsicWidth.toFloat() * (virtualHeight / srcR.intrinsicHeight.toFloat())
        } else {
            0f
        }
        val totalVirtualWidth = (leftVirtualWidth + rightVirtualWidth).coerceAtLeast(1f)

        // Account for 90° / 270° rotation when fitting inside the viewport.
        val normalizedQuarter = (((rotationDegrees / 90f).roundToInt()) % 4 + 4) % 4
        val isRotatedSideways = normalizedQuarter == 1 || normalizedQuarter == 3
        val fitContainerW = if (isRotatedSideways) viewportH else viewportW
        val fitContainerH = if (isRotatedSideways) viewportW else viewportH

        val baseFitScale = min(fitContainerW / totalVirtualWidth, fitContainerH / virtualHeight)
        val fittedWidth = totalVirtualWidth * baseFitScale
        val fittedHeight = virtualHeight * baseFitScale

        fun clampPan(candidate: Offset, targetZoom: Float): Offset {
            val drawnW = (if (isRotatedSideways) fittedHeight else fittedWidth) * targetZoom
            val drawnH = (if (isRotatedSideways) fittedWidth else fittedHeight) * targetZoom
            val maxPanX = max(0f, (drawnW - viewportW) / 2f)
            val maxPanY = max(0f, (drawnH - viewportH) / 2f)
            return Offset(
                x = candidate.x.coerceIn(-maxPanX, maxPanX),
                y = candidate.y.coerceIn(-maxPanY, maxPanY),
            )
        }

        // Compute visible full-resolution source rects when zoomed in (> 1.15x) so `BitmapRegionDecoder`
        // decodes only the visible 512px tiles at crisp 1:1 native detail!
        val visibleTileSpecs = remember(
            zoomScale,
            panOffset,
            viewportW,
            viewportH,
            srcL,
            srcR,
            normalizedQuarter,
        ) {
            if (zoomScale <= 1.15f) {
                emptyList()
            } else {
                // Map screen viewport bounds back into unzoomed virtual image coordinates.
                // When rotated, fall back conservatively to full virtual rect if zoomed so rotated
                // spreads still sharpen cleanly without clipping corners.
                val effectiveDisplayScale = baseFitScale * zoomScale
                val centerVirtualX = (totalVirtualWidth / 2f) -
                    if (isRotatedSideways) 0f else (panOffset.x / effectiveDisplayScale)
                val centerVirtualY = (virtualHeight / 2f) -
                    if (isRotatedSideways) 0f else (panOffset.y / effectiveDisplayScale)
                val halfSpanW = if (isRotatedSideways) {
                    totalVirtualWidth / 2f
                } else {
                    (viewportW / 2f) / effectiveDisplayScale
                }
                val halfSpanH = if (isRotatedSideways) {
                    virtualHeight / 2f
                } else {
                    (viewportH / 2f) / effectiveDisplayScale
                }

                val visVirtualLeft = (centerVirtualX - halfSpanW).coerceIn(0f, totalVirtualWidth)
                val visVirtualRight = (centerVirtualX + halfSpanW).coerceIn(0f, totalVirtualWidth)
                val visVirtualTop = (centerVirtualY - halfSpanH).coerceIn(0f, virtualHeight)
                val visVirtualBottom = (centerVirtualY + halfSpanH).coerceIn(0f, virtualHeight)

                buildList {
                    if (visVirtualLeft < leftVirtualWidth) {
                        val scaleToSrcL = srcL.intrinsicHeight.toFloat() / virtualHeight
                        val rectL = Rect(
                            (visVirtualLeft * scaleToSrcL).toInt(),
                            (visVirtualTop * scaleToSrcL).toInt(),
                            (min(visVirtualRight, leftVirtualWidth) * scaleToSrcL).toInt(),
                            (visVirtualBottom * scaleToSrcL).toInt(),
                        )
                        addAll(
                            computeVisibleHighResTiles(
                                partKey = "L",
                                imageWidth = srcL.intrinsicWidth,
                                imageHeight = srcL.intrinsicHeight,
                                visibleSourceRect = rectL,
                                effectiveZoomScale = zoomScale,
                            ),
                        )
                    }
                    if (srcR != null && visVirtualRight > leftVirtualWidth) {
                        val scaleToSrcR = srcR.intrinsicHeight.toFloat() / virtualHeight
                        val localLeft = max(0f, visVirtualLeft - leftVirtualWidth)
                        val localRight = max(0f, visVirtualRight - leftVirtualWidth)
                        val rectR = Rect(
                            (localLeft * scaleToSrcR).toInt(),
                            (visVirtualTop * scaleToSrcR).toInt(),
                            (localRight * scaleToSrcR).toInt(),
                            (visVirtualBottom * scaleToSrcR).toInt(),
                        )
                        addAll(
                            computeVisibleHighResTiles(
                                partKey = "R",
                                imageWidth = srcR.intrinsicWidth,
                                imageHeight = srcR.intrinsicHeight,
                                visibleSourceRect = rectR,
                                effectiveZoomScale = zoomScale,
                            ),
                        )
                    }
                }
            }
        }

        LaunchedEffect(visibleTileSpecs) {
            for (spec in visibleTileSpecs) {
                val cacheKey = "${spec.partKey}_${spec.sampleSize}_${spec.sourceRect.flattenToString()}"
                val existing = tileCache.get(cacheKey)
                if (existing != null) {
                    if (!decodedTileMap.containsKey(cacheKey)) {
                        decodedTileMap[cacheKey] = existing
                    }
                    continue
                }
                val source = if (spec.partKey == "L") srcL else srcR
                if (source != null) {
                    val bmp = withContext(ioDispatcher) {
                        source.decodeTile(spec.sourceRect, spec.sampleSize)
                    }
                    if (bmp != null) {
                        tileCache.put(cacheKey, bmp)
                        decodedTileMap[cacheKey] = bmp
                    }
                }
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(entry, isRotatedSideways) {
                    detectTapGestures(
                        onTap = { onSingleTapToggleChrome() },
                        onDoubleTap = { tapOffset ->
                            if (zoomScale > 1.08f) {
                                zoomScale = 1f
                                panOffset = Offset.Zero
                            } else {
                                val targetZoom = 2.5f
                                val center = Offset(viewportW / 2f, viewportH / 2f)
                                val focusDelta = (center - tapOffset) * (targetZoom - 1f)
                                zoomScale = targetZoom
                                panOffset = clampPan(focusDelta, targetZoom)
                            }
                        },
                    )
                }
                .pointerInput(entry, isRotatedSideways) {
                    // Custom multi-touch zoom + pan recognizer that ONLY consumes events when
                    // `pointerCount >= 2` OR `zoomScale > 1.02f` so 1-finger horizontal pager swipes
                    // and 1-finger vertical drag-to-dismiss pass through cleanly at 1.0× zoom!
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            val pressedCount = event.changes.count { it.pressed }
                            if (pressedCount >= 2 || zoomScale > 1.02f) {
                                val zoomChange = event.calculateZoom()
                                val panChange = event.calculatePan()
                                if (zoomChange != 1f || panChange != Offset.Zero) {
                                    val oldZoom = zoomScale
                                    val newZoom = (oldZoom * zoomChange).coerceIn(1f, 4f)
                                    val centroid = event.calculateCentroid(useCurrent = false)
                                    val screenCenter = Offset(viewportW / 2f, viewportH / 2f)
                                    val zoomRatio = newZoom / oldZoom
                                    val candidatePan = if (centroid != Offset.Unspecified && zoomChange != 1f) {
                                        (panOffset + (centroid - screenCenter) * (1f - zoomRatio)) + panChange
                                    } else {
                                        panOffset + panChange
                                    }
                                    zoomScale = newZoom
                                    panOffset = if (newZoom <= 1.01f) {
                                        Offset.Zero
                                    } else {
                                        clampPan(candidatePan, newZoom)
                                    }
                                    event.changes.forEach { change ->
                                        if (change.positionChanged()) {
                                            change.consume()
                                        }
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
                .graphicsLayer {
                    translationX = panOffset.x
                    translationY = panOffset.y
                    scaleX = zoomScale
                    scaleY = zoomScale
                    rotationZ = rotationDegrees
                }
                .testTag("${ArtbookTestTags.CANVAS_PREFIX}$entryIndex"),
        ) {
            val drawLeft = ((size.width - fittedWidth) / 2f).roundToInt()
            val drawTop = ((size.height - fittedHeight) / 2f).roundToInt()
            val leftDrawnWidth = (leftVirtualWidth * baseFitScale).roundToInt()
            val rightDrawnWidth = (rightVirtualWidth * baseFitScale).roundToInt()
            val totalDrawnHeight = fittedHeight.roundToInt()

            // 1. Base preview layer (instant 120fps pan/zoom with zero blank flashes).
            drawImage(
                image = srcL.previewBitmap.asImageBitmap(),
                dstOffset = IntOffset(drawLeft, drawTop),
                dstSize = IntSize(leftDrawnWidth.coerceAtLeast(1), totalDrawnHeight.coerceAtLeast(1)),
            )
            if (srcR != null && rightDrawnWidth > 0) {
                drawImage(
                    image = srcR.previewBitmap.asImageBitmap(),
                    dstOffset = IntOffset(drawLeft + leftDrawnWidth, drawTop),
                    dstSize = IntSize(rightDrawnWidth, totalDrawnHeight.coerceAtLeast(1)),
                )
            }

            // 2. High-DPI `BitmapRegionDecoder` tile overlay when zoomed in (> 1.15x).
            if (zoomScale > 1.15f) {
                for (spec in visibleTileSpecs) {
                    val cacheKey = "${spec.partKey}_${spec.sampleSize}_${spec.sourceRect.flattenToString()}"
                    val tileBmp = decodedTileMap[cacheKey] ?: continue
                    if (spec.partKey == "L") {
                        val scaleXToFitted = leftDrawnWidth.toFloat() / srcL.intrinsicWidth.toFloat()
                        val scaleYToFitted = totalDrawnHeight.toFloat() / srcL.intrinsicHeight.toFloat()
                        val dstX = drawLeft + (spec.sourceRect.left * scaleXToFitted).roundToInt()
                        val dstY = drawTop + (spec.sourceRect.top * scaleYToFitted).roundToInt()
                        val dstW = (spec.sourceRect.width() * scaleXToFitted).roundToInt().coerceAtLeast(1)
                        val dstH = (spec.sourceRect.height() * scaleYToFitted).roundToInt().coerceAtLeast(1)
                        drawImage(
                            image = tileBmp.asImageBitmap(),
                            dstOffset = IntOffset(dstX, dstY),
                            dstSize = IntSize(dstW, dstH),
                        )
                    } else if (srcR != null) {
                        val scaleXToFitted = rightDrawnWidth.toFloat() / srcR.intrinsicWidth.toFloat()
                        val scaleYToFitted = totalDrawnHeight.toFloat() / srcR.intrinsicHeight.toFloat()
                        val dstX = drawLeft + leftDrawnWidth + (spec.sourceRect.left * scaleXToFitted).roundToInt()
                        val dstY = drawTop + (spec.sourceRect.top * scaleYToFitted).roundToInt()
                        val dstW = (spec.sourceRect.width() * scaleXToFitted).roundToInt().coerceAtLeast(1)
                        val dstH = (spec.sourceRect.height() * scaleYToFitted).roundToInt().coerceAtLeast(1)
                        drawImage(
                            image = tileBmp.asImageBitmap(),
                            dstOffset = IntOffset(dstX, dstY),
                            dstSize = IntSize(dstW, dstH),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders an [ArtbookEntry] (single illustration or stitched 2-page `口绘` spread, including any
 * active `90°` rotation) into a JPEG file or copies the raw file directly when unrotated single-page.
 */
internal suspend fun exportArtbookEntryToJpegFile(
    targetFile: File,
    entry: ArtbookEntry,
    rotationQuarterTurns: Int,
    imageFileResolver: suspend (String) -> File?,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
): Boolean = withContext(ioDispatcher) {
    val normalizedQuarter = ((rotationQuarterTurns % 4) + 4) % 4
    if (entry is ArtbookEntry.Single && normalizedQuarter == 0) {
        val rawFile = imageFileResolver(entry.imageUrl) ?: return@withContext false
        return@withContext runCatching {
            targetFile.parentFile?.mkdirs()
            rawFile.copyTo(targetFile, overwrite = true)
            targetFile.length() > 0L
        }.getOrDefault(false)
    }

    val bitmap = when (entry) {
        is ArtbookEntry.Single -> {
            val file = imageFileResolver(entry.imageUrl) ?: return@withContext false
            decodeBoundedExportBitmap(file, maxEdgePx = 4096) ?: return@withContext false
        }
        is ArtbookEntry.DoubleSpread -> {
            val fileL = imageFileResolver(entry.leftImageUrl) ?: return@withContext false
            val fileR = imageFileResolver(entry.rightImageUrl) ?: return@withContext false
            val bmpL = decodeBoundedExportBitmap(fileL, maxEdgePx = 3072) ?: return@withContext false
            val bmpR = decodeBoundedExportBitmap(fileR, maxEdgePx = 3072) ?: return@withContext false
            val targetH = max(bmpL.height, bmpR.height).coerceAtLeast(1)
            val scaledWLeft = (bmpL.width.toFloat() * (targetH.toFloat() / bmpL.height.toFloat())).roundToInt()
                .coerceAtLeast(1)
            val scaledWRight = (bmpR.width.toFloat() * (targetH.toFloat() / bmpR.height.toFloat())).roundToInt()
                .coerceAtLeast(1)
            val stitched = Bitmap.createBitmap(
                scaledWLeft + scaledWRight,
                targetH,
                Bitmap.Config.ARGB_8888,
            )
            val canvas = AndroidCanvas(stitched)
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(bmpL, null, Rect(0, 0, scaledWLeft, targetH), paint)
            canvas.drawBitmap(
                bmpR,
                null,
                Rect(scaledWLeft, 0, scaledWLeft + scaledWRight, targetH),
                paint,
            )
            bmpL.recycle()
            bmpR.recycle()
            stitched
        }
    }

    val finalBitmap = if (normalizedQuarter == 0) {
        bitmap
    } else {
        val matrix = Matrix().apply { postRotate((normalizedQuarter * 90).toFloat()) }
        val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (rotated !== bitmap) {
            bitmap.recycle()
        }
        rotated
    }

    runCatching {
        targetFile.parentFile?.mkdirs()
        FileOutputStream(targetFile).use { out ->
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            out.fd.sync()
        }
        finalBitmap.recycle()
        targetFile.length() > 0L
    }.getOrDefault(false)
}

private fun decodeBoundedExportBitmap(file: File, maxEdgePx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (max(bounds.outWidth / sample, bounds.outHeight / sample) > maxEdgePx) {
        sample *= 2
    }
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}

internal suspend fun saveArtbookEntryToMediaStore(
    context: Context,
    novelTitle: String,
    entry: ArtbookEntry,
    rotationQuarterTurns: Int,
    imageFileResolver: suspend (String) -> File?,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    fileNameSuffixOverride: String? = null,
): Boolean = withContext(ioDispatcher) {
    val tempExport = File(context.cacheDir, "artbook_share/save_tmp_${System.currentTimeMillis()}.jpg")
    try {
        val ok = exportArtbookEntryToJpegFile(
            targetFile = tempExport,
            entry = entry,
            rotationQuarterTurns = rotationQuarterTurns,
            imageFileResolver = imageFileResolver,
            ioDispatcher = ioDispatcher,
        )
        if (!ok) return@withContext false

        val sanitizedTitle = novelTitle.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_").take(24).ifBlank { "wenku8" }
        val sanitizedSuffix = fileNameSuffixOverride
            ?.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
            ?.take(24)
            ?.ifBlank { null }
        val pageSuffix = sanitizedSuffix ?: when (entry) {
            is ArtbookEntry.Single -> "p${entry.primaryImageIndex + 1}"
            is ArtbookEntry.DoubleSpread -> "spread_${entry.primaryImageIndex + 1}_${entry.secondaryImageIndex + 1}"
        }
        val displayName = "${sanitizedTitle}_${pageSuffix}_${System.currentTimeMillis()}.jpg"
        val resolver = context.contentResolver

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Wenku8")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }

        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return@withContext false

        val copied = runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(tempExport).use { input ->
                    input.copyTo(out)
                }
            } != null
        }.getOrDefault(false)

        if (!copied) {
            resolver.delete(uri, null, null)
            return@withContext false
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val doneValues = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, doneValues, null, null)
        }
        true
    } finally {
        tempExport.delete()
    }
}

internal suspend fun shareArtbookEntryViaFileProvider(
    context: Context,
    novelTitle: String,
    entry: ArtbookEntry,
    rotationQuarterTurns: Int,
    imageFileResolver: suspend (String) -> File?,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    chooserTitle: String? = null,
    fileNameTag: String? = null,
): Boolean {
    val shareDir = File(context.cacheDir, "artbook_share")
    // Opportunistically clean share files older than 24h so cache never accumulates.
    withContext(ioDispatcher) {
        val cutoff = System.currentTimeMillis() - 24L * 3600L * 1000L
        shareDir.listFiles()?.forEach { f ->
            if (f.lastModified() < cutoff) f.delete()
        }
    }
    val safeTag = fileNameTag
        ?.replace(Regex("[^\\p{L}\\p{N}_-]+"), "_")
        ?.take(24)
        ?.ifBlank { null }
        ?: "illustration"
    val shareFile = File(shareDir, "wenku8_${safeTag}_${System.currentTimeMillis()}.jpg")
    val exported = exportArtbookEntryToJpegFile(
        targetFile = shareFile,
        entry = entry,
        rotationQuarterTurns = rotationQuarterTurns,
        imageFileResolver = imageFileResolver,
        ioDispatcher = ioDispatcher,
    )
    if (!exported) return false

    return runCatching {
        val authority = "${context.packageName}.diagnostics.fileprovider"
        val contentUri = FileProvider.getUriForFile(context, authority, shareFile)
        val sendIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, contentUri)
            if (novelTitle.isNotBlank()) {
                putExtra(Intent.EXTRA_SUBJECT, novelTitle)
            }
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val effectiveChooserTitle = chooserTitle?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.artbook_action_share)
        val chooser = Intent.createChooser(sendIntent, effectiveChooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
        true
    }.getOrDefault(false)
}
