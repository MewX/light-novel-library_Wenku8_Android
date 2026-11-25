package org.mewx.wenku8.data.media

import android.content.Context
import android.graphics.BitmapFactory
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Lightweight integrity & format validator for Wenku8 cover and chapter illustration assets.
 *
 * Prevents corrupt local cache deadlocks caused by:
 * 1. HTTP 200 non-image error responses (e.g. ISP captive portals, HTML error pages, or 8-byte GBK
 *    `"禁止访问"` text responses) being written permanently into `saves/imgs/` or `artbook_images/`.
 * 2. Truncated downloads (mid-stream TCP drops or OEM background worker kills) leaving partial
 *    JPEG/PNG/WebP/GIF files that fail decoding forever because offline-first fetchers blindly
 *    trust any non-empty file on disk.
 */
object WenkuImageValidator {

    private val PNG_SIGNATURE = byteArrayOf(
        0x89.toByte(),
        0x50,
        0x4E,
        0x47,
        0x0D,
        0x0A,
        0x1A,
        0x0A,
    )
    private val IEND_BYTES = byteArrayOf(0x49, 0x45, 0x4E, 0x44)
    private val AVIF_HEIF_BRANDS = setOf("avif", "avis", "heic", "heix", "hevc", "mif1", "msf1")
    private val REJECTED_CONTENT_TYPE_PREFIXES = listOf(
        "text/",
        "application/json",
        "application/xml",
        "application/xhtml",
        "application/javascript",
        "application/problem+",
    )
    private val WENKU_COVER_URL_REGEX = Regex("/image/\\d+/(\\d+)/\\1s?\\.jpg", RegexOption.IGNORE_CASE)

    /**
     * Returns `false` for explicitly non-image MIME types (`text/html`, `text/plain`, `application/xml`,
     * `application/json`, etc.) while allowing `image/` subtypes, `application/octet-stream`, or omitted headers.
     */
    fun isValidImageContentType(contentType: String?): Boolean {
        val normalized = contentType?.substringBefore(';')?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalized.isEmpty()) return true
        return REJECTED_CONTENT_TYPE_PREFIXES.none { prefix -> normalized.startsWith(prefix) }
    }

    /**
     * Validates magic header & structural trailer bytes of an in-memory image payload (`< 0.1 ms`).
     * Rejects empty buffers, HTML/text error bodies, and truncated JPEG/PNG/GIF/WebP payloads.
     */
    fun hasValidImageStructure(bytes: ByteArray): Boolean {
        val totalLength = bytes.size.toLong()
        if (totalLength < 4L) return false
        val headLen = minOf(bytes.size, 32)
        val tailLen = minOf(bytes.size, 32)
        val head = bytes.copyOfRange(0, headLen)
        val tail = bytes.copyOfRange(bytes.size - tailLen, bytes.size)
        return validateHeaderAndTrailer(head, headLen, tail, tailLen, totalLength)
    }

    /**
     * Constant-time (`O(1)`, <= 64 bytes read) file-level magic header & structural trailer check.
     * Safe to invoke on multi-megabyte illustrations without allocating large byte arrays.
     */
    fun hasValidImageStructure(file: File): Boolean {
        if (!file.isFile || !file.canRead()) return false
        val totalLength = file.length()
        if (totalLength < 4L) return false
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val headLen = minOf(totalLength, 32L).toInt()
                val head = ByteArray(headLen)
                raf.readFully(head)

                val tailLen = minOf(totalLength, 32L).toInt()
                val tail = if (tailLen == headLen && totalLength <= 32L) {
                    head
                } else {
                    val buf = ByteArray(tailLen)
                    raf.seek(totalLength - tailLen)
                    raf.readFully(buf)
                    buf
                }
                validateHeaderAndTrailer(head, headLen, tail, tailLen, totalLength)
            }
        }.getOrDefault(false)
    }

    /**
     * Runs [hasValidImageStructure] AND Android's `BitmapFactory` bounds decode (`inJustDecodeBounds`)
     * to guarantee the file is genuinely decodable by Skia before displaying or committing to cache.
     */
    fun isDecodableBitmapFile(file: File): Boolean {
        if (!hasValidImageStructure(file)) return false
        return runCatching {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            opts.outWidth > 0 && opts.outHeight > 0
        }.getOrDefault(false)
    }

    /**
     * Removes any cached local copies (permanent `saves/imgs/`, `imgs/`, temporary `artbook_images/`,
     * and Coil 3 `MemoryCache`/`DiskCache` entries) associated with [imageUrl] so subsequent loads
     * fetch a clean copy from the network.
     */
    fun purgeCachedImageForUrl(
        imageUrl: String,
        storageRoots: List<File>,
        cacheRoot: File? = null,
        appContext: Context? = null,
        includePermanentOfflineFiles: Boolean = true,
    ) {
        val trimmed = imageUrl.trim()
        if (trimmed.isEmpty()) return

        val legacyFileName = WenkuXmlParsers.generateLegacyImageFileName(trimmed)
        val coverAid = WENKU_COVER_URL_REGEX.find(trimmed)?.groupValues?.getOrNull(1)

        if (includePermanentOfflineFiles) {
            for (root in storageRoots) {
                if (legacyFileName.isNotEmpty()) {
                    runCatching { File(root, "saves/imgs/$legacyFileName").takeIf { it.isFile }?.delete() }
                    runCatching { File(root, "imgs/$legacyFileName").takeIf { it.isFile }?.delete() }
                }
                if (!coverAid.isNullOrEmpty()) {
                    runCatching { File(root, "saves/imgs/$coverAid.jpg").takeIf { it.isFile }?.delete() }
                    runCatching { File(root, "imgs/$coverAid.jpg").takeIf { it.isFile }?.delete() }
                }
            }
        }

        if (cacheRoot != null && legacyFileName.isNotEmpty()) {
            runCatching {
                File(File(cacheRoot, WenkuImageRequests.ARTBOOK_CACHE_DIR_NAME), legacyFileName)
                    .takeIf { it.isFile }
                    ?.delete()
            }
        }

        if (appContext != null) {
            evictCoilCachesForUrl(appContext, trimmed)
        }
    }

    /**
     * Evicts canonical and raw cache keys for [imageUrl] from Coil 3's `MemoryCache` and `DiskCache`.
     */
    fun evictCoilCachesForUrl(appContext: Context, imageUrl: String) {
        val trimmed = imageUrl.trim()
        if (trimmed.isEmpty()) return
        val keys = listOfNotNull(
            WenkuImageRequests.canonicalIllustrationCacheKey(trimmed),
            WenkuImageRequests.canonicalCacheKeyForUrl(trimmed),
            trimmed,
        ).distinct()
        runCatching {
            val loader = SingletonImageLoader.get(appContext)
            val memCache = loader.memoryCache
            val diskCache = loader.diskCache
            for (key in keys) {
                memCache?.remove(MemoryCache.Key(key))
                diskCache?.remove(key)
            }
        }
    }

    private fun validateHeaderAndTrailer(
        head: ByteArray,
        headLen: Int,
        tail: ByteArray,
        tailLen: Int,
        totalLength: Long,
    ): Boolean {
        // 1. JPEG: SOI (FF D8 FF) + EOI (FF D9) allowing minor trailing zero/whitespace padding
        if (headLen >= 3 &&
            (head[0].toInt() and 0xFF) == 0xFF &&
            (head[1].toInt() and 0xFF) == 0xD8 &&
            (head[2].toInt() and 0xFF) == 0xFF
        ) {
            return hasJpegEoiTrailer(tail, tailLen)
        }

        // 2. PNG: 8-byte magic + IEND chunk near EOF
        if (headLen >= 8 && matchesPrefix(head, PNG_SIGNATURE)) {
            return totalLength >= 20L && containsSubsequence(tail, tailLen, IEND_BYTES)
        }

        // 3. GIF: GIF87a / GIF89a + 0x3B trailer
        if (headLen >= 6) {
            val ascii6 = String(head, 0, 6, StandardCharsets.US_ASCII)
            if (ascii6 == "GIF87a" || ascii6 == "GIF89a") {
                return hasGifTrailer(tail, tailLen)
            }
        }

        // 4. WebP: RIFF <le32 size> WEBP and actual file length >= declared RIFF chunk size + 8
        if (headLen >= 12 &&
            head[0] == 'R'.code.toByte() &&
            head[1] == 'I'.code.toByte() &&
            head[2] == 'F'.code.toByte() &&
            head[3] == 'F'.code.toByte() &&
            head[8] == 'W'.code.toByte() &&
            head[9] == 'E'.code.toByte() &&
            head[10] == 'B'.code.toByte() &&
            head[11] == 'P'.code.toByte()
        ) {
            val riffPayloadSize = (head[4].toLong() and 0xFFL) or
                ((head[5].toLong() and 0xFFL) shl 8) or
                ((head[6].toLong() and 0xFFL) shl 16) or
                ((head[7].toLong() and 0xFFL) shl 24)
            return riffPayloadSize >= 4L && totalLength >= riffPayloadSize + 8L
        }

        // 5. BMP: "BM" + >= 14-byte header
        if (headLen >= 2 &&
            head[0] == 'B'.code.toByte() &&
            head[1] == 'M'.code.toByte()
        ) {
            return totalLength >= 14L
        }

        // 6. AVIF / HEIF: ISO BMFF `ftyp` box with known image brand
        if (headLen >= 12 &&
            head[4] == 'f'.code.toByte() &&
            head[5] == 't'.code.toByte() &&
            head[6] == 'y'.code.toByte() &&
            head[7] == 'p'.code.toByte()
        ) {
            val brand = String(head, 8, 4, StandardCharsets.US_ASCII).lowercase(Locale.ROOT)
            return totalLength >= 16L && brand in AVIF_HEIF_BRANDS
        }

        return false
    }

    private fun hasJpegEoiTrailer(tail: ByteArray, tailLen: Int): Boolean {
        var end = tailLen - 1
        var skippedPadding = 0
        while (end >= 1 && skippedPadding < 16 && isIgnorableTrailingByte(tail[end])) {
            end--
            skippedPadding++
        }
        if (end < 1) return false
        return (tail[end - 1].toInt() and 0xFF) == 0xFF &&
            (tail[end].toInt() and 0xFF) == 0xD9
    }

    private fun hasGifTrailer(tail: ByteArray, tailLen: Int): Boolean {
        var end = tailLen - 1
        var skippedPadding = 0
        while (end >= 0 && skippedPadding < 16 && isIgnorableTrailingByte(tail[end])) {
            end--
            skippedPadding++
        }
        return end >= 0 && (tail[end].toInt() and 0xFF) == 0x3B
    }

    private fun isIgnorableTrailingByte(b: Byte): Boolean {
        val v = b.toInt() and 0xFF
        return v == 0x00 || v == 0x09 || v == 0x0A || v == 0x0D || v == 0x20
    }

    private fun matchesPrefix(data: ByteArray, prefix: ByteArray): Boolean {
        if (data.size < prefix.size) return false
        for (i in prefix.indices) {
            if (data[i] != prefix[i]) return false
        }
        return true
    }

    private fun containsSubsequence(data: ByteArray, length: Int, needle: ByteArray): Boolean {
        if (needle.isEmpty() || length < needle.size) return false
        val limit = length - needle.size
        for (i in 0..limit) {
            var matched = true
            for (j in needle.indices) {
                if (data[i + j] != needle[j]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }
}
