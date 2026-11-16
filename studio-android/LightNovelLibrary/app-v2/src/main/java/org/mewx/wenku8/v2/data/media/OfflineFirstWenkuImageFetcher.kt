package org.mewx.wenku8.v2.data.media

import coil3.ImageLoader
import coil3.Uri
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import org.mewx.wenku8.v2.data.remote.parser.WenkuXmlParsers
import java.io.File

/**
 * Offline-first Coil 3 [Fetcher] that resolves Wenku8 chapter illustrations (`pictures*.jpg`) and
 * novel covers (`{aid}s.jpg` / `{aid}.jpg`) directly from permanent local storage (`filesDir/saves/imgs/`
 * and `filesDir/imgs/`) BEFORE touching OkHttp or Coil's volatile LRU cache!
 *
 * Why this matters for seamless `v1.31 -> v2.0` upgrades (`09-room-database-and-backup-archive-design.md` §2):
 * Existing `v1.31` users have thousands of downloaded light novel illustrations inside
 * `filesDir/saves/imgs/pictures113054175950471.jpg`. Reading them in place means:
 * 1. **Zero byte duplication** during `v1.31 -> v2.0` upgrade.
 * 2. **Zero LRU cache eviction** — downloaded offline books keep their illustrations forever, even
 *    months later on an airplane without internet.
 */
class OfflineFirstWenkuImageFetcher(
    private val localFile: File,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val extension = localFile.extension.lowercase()
        val mimeType = when (extension) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            else -> "image/jpeg"
        }
        return SourceFetchResult(
            source = ImageSource(
                file = localFile.toOkioPath(),
                fileSystem = FileSystem.SYSTEM,
            ),
            mimeType = mimeType,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(
        private val assetSearchRootsProvider: () -> List<File>,
    ) : Fetcher.Factory<Uri> {

        override fun create(
            data: Uri,
            options: Options,
            imageLoader: ImageLoader,
        ): Fetcher? {
            val scheme = data.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") {
                return null
            }

            val rawUrl = data.toString()
            val resolvedFile = findExistingOfflineImageFile(
                imageUrl = rawUrl,
                storageRoots = assetSearchRootsProvider(),
            ) ?: return null

            return OfflineFirstWenkuImageFetcher(resolvedFile)
        }
    }

    companion object {
        private val WENKU_COVER_URL_REGEX = Regex("/image/\\d+/(\\d+)/\\1s\\.jpg", RegexOption.IGNORE_CASE)

        /**
         * Resolves a Wenku8 image URL against `saves/imgs/` and `imgs/` inside each candidate
         * storage root (`context.filesDir` first, optional legacy external root second).
         */
        fun findExistingOfflineImageFile(
            imageUrl: String,
            storageRoots: List<File>,
        ): File? {
            val legacyFileName = WenkuXmlParsers.generateLegacyImageFileName(imageUrl)
            val coverAid = WENKU_COVER_URL_REGEX.find(imageUrl)?.groupValues?.getOrNull(1)

            val relativeCandidates = buildList {
                if (legacyFileName.isNotEmpty()) {
                    add("saves/imgs/$legacyFileName")
                    add("imgs/$legacyFileName")
                }
                if (!coverAid.isNullOrEmpty()) {
                    add("imgs/$coverAid.jpg")
                    add("saves/imgs/$coverAid.jpg")
                }
            }

            for (root in storageRoots) {
                for (relativePath in relativeCandidates) {
                    val candidate = File(root, relativePath)
                    if (candidate.isFile && candidate.length() > 0L) {
                        return candidate
                    }
                }
            }
            return null
        }
    }
}
