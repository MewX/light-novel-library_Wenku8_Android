package org.mewx.wenku8.data.remote.parser

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toUri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.data.media.OfflineFirstWenkuImageFetcher
import org.mewx.wenku8.data.media.WenkuHttpsRedirectUpgradeInterceptor
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.mewx.wenku8.data.media.WenkuImageUrlPolicy
import org.mewx.wenku8.data.media.WenkuImageValidator
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.net.SocketException
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WenkuXmlParsersAndImageFetcherTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun parseNovelFullMetaAndRejectNonMetadataXml() {
        val validXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <metadata>
            <data name="Title" aid="1306"><![CDATA[向森之魔物献上花束]]></data>
            <data name="Author" value="小木君人"/>
            <data name="DayHitsCount" value="26"/>
            <data name="TotalHitsCount" value="43984"/>
            <data name="PushCount" value="1735"/>
            <data name="FavCount" value="848"/>
            <data name="PressId" value="小学馆" sid="10"/>
            <data name="BookStatus" value="已完成"/>
            <data name="BookLength" value="105985"/>
            <data name="LastUpdate" value="2012-11-02"/>
            <data name="LatestSection" cid="41897"><![CDATA[第一卷 插图]]></data>
            </metadata>
        """.trimIndent()

        val parsed = WenkuXmlParsers.parseNovelFullMeta(validXml)
        assertNotNull(parsed)
        assertEquals(1306, parsed!!.aid)
        assertEquals("向森之魔物献上花束", parsed.title)
        assertEquals("小木君人", parsed.author)
        assertEquals("小学馆", parsed.pressId)
        assertEquals("已完成", parsed.bookStatus)
        assertEquals(41897, parsed.latestSectionCid)
        assertEquals("第一卷 插图", parsed.latestSectionName)

        // Well-formed XML that is NOT novel metadata must return null instead of a bogus stub.
        assertNull(WenkuXmlParsers.parseNovelFullMeta("<error code=\"404\">Not Found</error>"))
    }

    @Test
    fun parseVolumeTocListHandlesMixedCdataAndStrayClosingTags() {
        val rawVolumeXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <package>
            <volume vid="41748"><![CDATA[第一卷 告白于苍刻之夜]]>
            <chapter cid="41749"><![CDATA[序章]]></chapter>
            <chapter cid="41759"><![CDATA[插图]]></chapter>
            </volume>
            <volume vid="42000"><![CDATA[第二卷 终焉]]>
            <chapter cid="42001"><![CDATA[第一章]]></chapter>
            </volume>
            </package>
        """.trimIndent()

        val volumes = WenkuXmlParsers.parseVolumeTocList(rawVolumeXml)
        assertEquals(2, volumes.size)
        assertEquals(41748, volumes[0].vid)
        assertEquals("第一卷 告白于苍刻之夜", volumes[0].volumeTitle)
        assertEquals(2, volumes[0].chapters.size)
        assertEquals(41759, volumes[0].chapters[1].cid)
        assertEquals("插图", volumes[0].chapters[1].chapterTitle)
        assertEquals("第二卷 终焉", volumes[1].volumeTitle)

        // Malformed XML with leading BOM/whitespace and a stray closing </volume> tag must still
        // preserve all volumes parsed prior to the malformed tail (matching Wenku8Parser).
        val malformedTailXml = "\uFEFF  \n" + """
            <?xml version="1.0" encoding="utf-8"?>
            <package>
            <volume vid="41748"><![CDATA[第一卷 告白于苍刻之夜]]>
            <chapter cid="41749"><![CDATA[序章]]></chapter>
            </volume>
            </volume>
            </package>
        """.trimIndent()
        val recoveredVolumes = WenkuXmlParsers.parseVolumeTocList(malformedTailXml)
        assertEquals(1, recoveredVolumes.size)
        assertEquals(41748, recoveredVolumes[0].vid)
        assertEquals("第一卷 告白于苍刻之夜", recoveredVolumes[0].volumeTitle)
        assertEquals(1, recoveredVolumes[0].chapters.size)
    }

    @Test
    fun parseNovelListWithInfoAndCloudBookshelfDistinguishEmptyVsInvalid() {
        val listXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <result>
            <page num="42"/>
            <item aid="2500">
                <data name="Title"><![CDATA[86-不存在的战区-]]></data>
                <data name="Author" value="安里アサト"/>
                <data name="BookStatus" value="连载中"/>
                <data name="LastUpdate" value="2026-10-01"/>
                <data name="Tags" value="科幻 战争 机甲"/>
                <data name="IntroPreview"><![CDATA[　　圣玛格诺利亚共和国
                每天遭受邻国「帝国」无人兵器侵略。]]></data>
            </item>
            </result>
        """.trimIndent()

        val listPage = WenkuXmlParsers.parseNovelListWithInfo(listXml)
        assertNotNull(listPage)
        assertEquals(42, listPage!!.totalPages)
        assertEquals(1, listPage.items.size)
        assertEquals("86-不存在的战区-", listPage.items[0].title)
        assertEquals("圣玛格诺利亚共和国 每天遭受邻国「帝国」无人兵器侵略。", listPage.items[0].introShort)

        // Empty cloud shelf returns emptyList(), NOT null; non-bookshelf XML returns null.
        assertEquals(emptyList<ParsedCloudBookshelfEntry>(), WenkuXmlParsers.parseCloudBookshelfList("<metadata></metadata>"))
        assertNull(WenkuXmlParsers.parseCloudBookshelfList("<html>Gateway Error</html>"))
    }

    @Test
    fun offlineFirstWenkuImageFetcherServesLegacyIllustrationAndCoverInPlace() = runTest {
        val chapterImgUrl = "http://pic.wenku8.cn/pictures/1/1305/41759/50471.jpg"
        assertEquals(
            "pictures113054175950471.jpg",
            WenkuXmlParsers.generateLegacyImageFileName(chapterImgUrl),
        )

        val fakeFilesDir = tempFolder.newFolder("files")
        val savesImgsDir = File(fakeFilesDir, "saves/imgs").apply { mkdirs() }
        val coversDir = File(fakeFilesDir, "imgs").apply { mkdirs() }

        val savedIllustration = File(savesImgsDir, "pictures113054175950471.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        }
        val savedCover = File(coversDir, "1306.jpg").apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val imageLoader = ImageLoader.Builder(context).build()
        val factory = OfflineFirstWenkuImageFetcher.Factory { listOf(fakeFilesDir) }

        val illustrationFetcher = factory.create(
            data = chapterImgUrl.toUri(),
            options = Options(context),
            imageLoader = imageLoader,
        )
        assertNotNull(illustrationFetcher)
        val illustrationResult = illustrationFetcher!!.fetch() as SourceFetchResult
        assertEquals(DataSource.DISK, illustrationResult.dataSource)
        assertTrue(savedIllustration.exists())

        val coverFetcher = factory.create(
            data = "https://img.wenku8.com/image/1/1306/1306s.jpg".toUri(),
            options = Options(context),
            imageLoader = imageLoader,
        )
        assertNotNull(coverFetcher)
        val coverResult = coverFetcher!!.fetch() as SourceFetchResult
        assertEquals(DataSource.DISK, coverResult.dataSource)
        assertTrue(savedCover.exists())

        // Uncached URL returns null so Coil falls back cleanly to OkHttp network fetch.
        val missingFetcher = factory.create(
            data = "https://img.wenku8.com/image/9/9999/9999s.jpg".toUri(),
            options = Options(context),
            imageLoader = imageLoader,
        )
        assertNull(missingFetcher)

        // Artbook local cache (`cacheDir/artbook_images/`) is also shared directly with Coil
        // so viewing a gallery illustration in Reader or thumbnail strip never re-downloads!
        val fakeCacheDir = tempFolder.newFolder("cache")
        val artbookDir = File(fakeCacheDir, "artbook_images").apply { mkdirs() }
        val artbookIllustrationUrl = "http://pic.wenku8.cn/pictures/4/4526/9101/119999.jpg"
        val artbookFile = File(
            artbookDir,
            WenkuXmlParsers.generateLegacyImageFileName(artbookIllustrationUrl),
        ).apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()))
        }
        val crossCacheFactory = OfflineFirstWenkuImageFetcher.Factory(
            assetSearchRootsProvider = { listOf(fakeFilesDir) },
            cacheRootProvider = { fakeCacheDir },
        )
        val crossCacheFetcher = crossCacheFactory.create(
            data = "https://pic.wenku8.com/pictures/4/4526/9101/119999.jpg".toUri(),
            options = Options(context),
            imageLoader = imageLoader,
        )
        assertNotNull(crossCacheFetcher)
        val crossCacheResult = crossCacheFetcher!!.fetch() as SourceFetchResult
        assertEquals(DataSource.DISK, crossCacheResult.dataSource)
        assertTrue(artbookFile.exists())
    }

    @Test
    fun wenkuImageRequestsNormalizeCacheKeysAcrossSchemesAndMirrorsAndPreventCoverFlash() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        // HTTP vs HTTPS and .com vs .cn mirrors map to the exact same canonical cache keys!
        assertEquals(
            "wenku8_cover_1306",
            WenkuImageRequests.canonicalCacheKeyForUrl("http://img.wenku8.com/image/1/1306/1306s.jpg"),
        )
        assertEquals(
            "wenku8_cover_1306",
            WenkuImageRequests.canonicalCacheKeyForUrl("https://img.wenku8.cn/image/1/1306/1306s.jpg"),
        )
        assertEquals(
            WenkuImageRequests.canonicalCacheKeyForUrl("http://pic.wenku8.cn/pictures/1/1305/41759/50471.jpg"),
            WenkuImageRequests.canonicalCacheKeyForUrl("https://pic.wenku8.com/pictures/1/1305/41759/50471.jpg"),
        )

        // Cover requests lock Size.ORIGINAL + placeholderMemoryCacheKey so SharedElement transitions
        // between list cards and NovelDetailScreen hit MemoryCache synchronously on Frame 1!
        val listCoverReq = WenkuImageRequests.buildCoverImageRequest(
            context = context,
            aid = 1306,
            coverUrl = "https://img.wenku8.com/image/1/1306/1306s.jpg",
            enableCrossfade = false,
        )
        val detailCoverReq = WenkuImageRequests.buildCoverImageRequest(
            context = context,
            aid = 1306,
            coverUrl = "http://img.wenku8.com/image/1/1306/1306s.jpg",
            enableCrossfade = false,
        )
        assertNotNull(listCoverReq)
        assertNotNull(detailCoverReq)
        assertEquals("wenku8_cover_1306", listCoverReq!!.memoryCacheKey)
        assertEquals(listCoverReq.memoryCacheKey, detailCoverReq!!.memoryCacheKey)
        assertEquals("wenku8_cover_1306", detailCoverReq.placeholderMemoryCacheKey?.key)
        assertEquals("wenku8_cover_1306", detailCoverReq.diskCacheKey)
        assertNotNull(detailCoverReq.defined.sizeResolver)
    }

    @Test
    fun wenkuImageValidator_validatesHeadersTrailersAndRejectsCorruptOrTextPayloads() {
        // Content-Type filtering rejects text/HTML/XML/JSON while accepting image/* and octet-stream
        assertTrue(WenkuImageValidator.isValidImageContentType("image/jpeg"))
        assertTrue(WenkuImageValidator.isValidImageContentType("image/webp; charset=binary"))
        assertTrue(WenkuImageValidator.isValidImageContentType("application/octet-stream"))
        assertTrue(WenkuImageValidator.isValidImageContentType(null))
        assertTrue(WenkuImageValidator.isValidImageContentType(""))
        assertFalse(WenkuImageValidator.isValidImageContentType("text/html; charset=utf-8"))
        assertFalse(WenkuImageValidator.isValidImageContentType("text/plain"))
        assertFalse(WenkuImageValidator.isValidImageContentType("application/xml"))
        assertFalse(WenkuImageValidator.isValidImageContentType("application/json"))

        // Valid real compressed JPEG & PNG bitmaps
        val bmp = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)
        val jpegBytes = ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
        val pngBytes = ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
            out.toByteArray()
        }
        bmp.recycle()

        assertTrue(WenkuImageValidator.hasValidImageStructure(jpegBytes))
        assertTrue(WenkuImageValidator.hasValidImageStructure(jpegBytes + byteArrayOf(0x00, 0x0A)))
        assertTrue(WenkuImageValidator.hasValidImageStructure(pngBytes))
        assertTrue(
            "Minimal 4-byte SOI+EOI fixture must remain accepted",
            WenkuImageValidator.hasValidImageStructure(
                byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte()),
            ),
        )

        // Valid GIF89a, WebP, BMP, AVIF headers/trailers
        val validGif = "GIF89a\u0001\u0000\u0001\u0000".toByteArray(StandardCharsets.US_ASCII) + byteArrayOf(0x3B)
        assertTrue(WenkuImageValidator.hasValidImageStructure(validGif))

        val validWebp = byteArrayOf(
            'R'.code.toByte(), 'I'.code.toByte(), 'F'.code.toByte(), 'F'.code.toByte(),
            12, 0, 0, 0, // declared RIFF payload = 12 -> total file = 20 bytes
            'W'.code.toByte(), 'E'.code.toByte(), 'B'.code.toByte(), 'P'.code.toByte(),
            'V'.code.toByte(), 'P'.code.toByte(), '8'.code.toByte(), ' '.code.toByte(),
            0, 0, 0, 0,
        )
        assertTrue(WenkuImageValidator.hasValidImageStructure(validWebp))

        val validBmp = byteArrayOf('B'.code.toByte(), 'M'.code.toByte()) + ByteArray(14)
        assertTrue(WenkuImageValidator.hasValidImageStructure(validBmp))

        val validAvif = byteArrayOf(
            0, 0, 0, 16,
            'f'.code.toByte(), 't'.code.toByte(), 'y'.code.toByte(), 'p'.code.toByte(),
            'a'.code.toByte(), 'v'.code.toByte(), 'i'.code.toByte(), 'f'.code.toByte(),
            0, 0, 0, 0,
        )
        assertTrue(WenkuImageValidator.hasValidImageStructure(validAvif))

        // Invalid / truncated / non-image payloads must all be rejected
        assertFalse(WenkuImageValidator.hasValidImageStructure(ByteArray(0)))
        assertFalse(WenkuImageValidator.hasValidImageStructure(ByteArray(3)))
        assertFalse(WenkuImageValidator.hasValidImageStructure(ByteArray(4096)))
        assertFalse(
            WenkuImageValidator.hasValidImageStructure("<html><body>502 Bad Gateway</body></html>".toByteArray()),
        )
        assertFalse(
            WenkuImageValidator.hasValidImageStructure("禁止访问".toByteArray(Charset.forName("GBK"))),
        )
        // Truncated JPEG (missing FF D9 EOI)
        assertFalse(WenkuImageValidator.hasValidImageStructure(jpegBytes.copyOfRange(0, jpegBytes.size - 4)))
        // Truncated PNG (missing IEND)
        assertFalse(WenkuImageValidator.hasValidImageStructure(pngBytes.copyOfRange(0, pngBytes.size - 12)))
        // Truncated WebP (declared RIFF payload 12 -> needs 20 bytes, only 16 provided)
        assertFalse(WenkuImageValidator.hasValidImageStructure(validWebp.copyOfRange(0, 16)))

        // File-level validation & Skia decode check
        val realJpegFile = tempFolder.newFile("real.jpg").apply { writeBytes(jpegBytes) }
        val truncatedJpegFile = tempFolder.newFile("trunc.jpg").apply {
            writeBytes(jpegBytes.copyOfRange(0, jpegBytes.size - 4))
        }
        assertTrue(WenkuImageValidator.hasValidImageStructure(realJpegFile))
        assertTrue(WenkuImageValidator.isDecodableBitmapFile(realJpegFile))
        assertFalse(WenkuImageValidator.hasValidImageStructure(truncatedJpegFile))
        assertFalse(WenkuImageValidator.isDecodableBitmapFile(truncatedJpegFile))
    }

    @Test
    fun offlineFirstWenkuImageFetcher_autoDeletesCorruptFilesAndPurgeRemovesAllLocalCopies() {
        val chapterImgUrl = "http://pic.wenku8.cn/pictures/3/3770/141001/1.jpg"
        val legacyName = WenkuXmlParsers.generateLegacyImageFileName(chapterImgUrl)

        val fakeFilesDir = tempFolder.newFolder("heal_files")
        val fakeCacheDir = tempFolder.newFolder("heal_cache")
        val savesImgsDir = File(fakeFilesDir, "saves/imgs").apply { mkdirs() }
        val artbookDir = File(fakeCacheDir, WenkuImageRequests.ARTBOOK_CACHE_DIR_NAME).apply { mkdirs() }

        // Seed corrupt truncated JPEG in permanent saves/imgs/ AND HTML error page in artbook_images/
        val corruptSavesFile = File(savesImgsDir, legacyName).apply {
            writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10))
        }
        val corruptArtbookFile = File(artbookDir, legacyName).apply {
            writeText("<html>Access Denied</html>")
        }

        val context = ApplicationProvider.getApplicationContext<Context>()
        val imageLoader = ImageLoader.Builder(context).build()
        val factory = OfflineFirstWenkuImageFetcher.Factory(
            assetSearchRootsProvider = { listOf(fakeFilesDir) },
            cacheRootProvider = { fakeCacheDir },
        )

        // Fetcher must auto-delete BOTH corrupt copies and return null so Coil self-heals from network!
        val fetcher = factory.create(
            data = chapterImgUrl.toUri(),
            options = Options(context),
            imageLoader = imageLoader,
        )
        assertNull(fetcher)
        assertFalse("Corrupt saves/imgs file must be auto-deleted", corruptSavesFile.exists())
        assertFalse("Corrupt artbook_images file must be auto-deleted", corruptArtbookFile.exists())

        // Verify purgeCachedImageForUrl removes valid copies across saves/imgs/, imgs/, and artbook_images/
        val validMinimalJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xD9.toByte())
        val validSaves = File(savesImgsDir, legacyName).apply { writeBytes(validMinimalJpeg) }
        val validArtbook = File(artbookDir, legacyName).apply { writeBytes(validMinimalJpeg) }
        val coverUrl = "https://img.wenku8.com/image/3/3770/3770s.jpg"
        val imgsDir = File(fakeFilesDir, "imgs").apply { mkdirs() }
        val validCover = File(imgsDir, "3770.jpg").apply { writeBytes(validMinimalJpeg) }

        WenkuImageValidator.purgeCachedImageForUrl(
            imageUrl = chapterImgUrl,
            storageRoots = listOf(fakeFilesDir),
            cacheRoot = fakeCacheDir,
            includePermanentOfflineFiles = true,
        )
        WenkuImageValidator.purgeCachedImageForUrl(
            imageUrl = coverUrl,
            storageRoots = listOf(fakeFilesDir),
            cacheRoot = fakeCacheDir,
            includePermanentOfflineFiles = true,
        )
        assertFalse(validSaves.exists())
        assertFalse(validArtbook.exists())
        assertFalse(validCover.exists())
    }

    @Test
    fun wenkuImageUrlPolicy_upgradesDefaultPortHttpToHttps_ordersMirrors_andSanitizesIoReasons() {
        // 1. Default port 80 Wenku8 image hosts upgrade to HTTPS; custom ports & unrelated hosts stay untouched
        assertEquals(
            "https://pic.wenku8.com/pictures/3/3770/141001/1.jpg",
            WenkuImageUrlPolicy.upgradeToHttpsIfSupported("http://pic.wenku8.com/pictures/3/3770/141001/1.jpg"),
        )
        assertEquals(
            "https://pic.wenku8.cn/pictures/1/1305/41759/50471.jpg",
            WenkuImageUrlPolicy.upgradeToHttpsIfSupported("http://pic.wenku8.cn/pictures/1/1305/41759/50471.jpg"),
        )
        assertEquals(
            "https://pic.777743.xyz/3/3770/141001/1.jpg",
            WenkuImageUrlPolicy.upgradeToHttpsIfSupported("http://pic.777743.xyz/3/3770/141001/1.jpg"),
        )
        assertEquals(
            "https://img.wenku8.com/image/3/3770/3770s.jpg",
            WenkuImageUrlPolicy.upgradeToHttpsIfSupported("http://img.wenku8.com/image/3/3770/3770s.jpg"),
        )
        assertEquals(
            "https://avatar.wenku8.com/avatar.php?uid=1",
            WenkuImageUrlPolicy.upgradeToHttpsIfSupported("http://avatar.wenku8.com/avatar.php?uid=1"),
        )
        assertEquals(
            "http://pic.wenku8.com:48123/pictures/3/3770/1.jpg",
            WenkuImageUrlPolicy.upgradeToHttpsIfSupported("http://pic.wenku8.com:48123/pictures/3/3770/1.jpg"),
        )
        assertEquals(
            "http://example.com/pictures/1.jpg",
            WenkuImageUrlPolicy.upgradeToHttpsIfSupported("http://example.com/pictures/1.jpg"),
        )

        // Coil request builders also normalize default-port HTTP URLs to HTTPS with identical cache keys
        val context = ApplicationProvider.getApplicationContext<Context>()
        val illusReq = WenkuImageRequests.buildIllustrationImageRequest(
            context = context,
            imageUrl = "http://pic.wenku8.com/pictures/3/3770/141001/1.jpg",
        )
        assertNotNull(illusReq)
        assertEquals("https://pic.wenku8.com/pictures/3/3770/141001/1.jpg", illusReq!!.data)
        assertEquals("wenku8_illus_pictures337701410011.jpg", illusReq.diskCacheKey)
        assertEquals(
            "Direct pic.777743.xyz URL must normalize to identical canonical illustration cache key",
            "wenku8_illus_pictures337701410011.jpg",
            WenkuImageRequests.canonicalIllustrationCacheKey("https://pic.777743.xyz/3/3770/141001/1.jpg"),
        )

        // 2. Option A interchangeable illustration pool: pic.777743.xyz -> pic.wenku8.com (direct) -> pic.wenku8.cn (legacy last)
        val expectedOptionA = listOf(
            "https://pic.777743.xyz/3/3770/141001/1.jpg",
            "https://pic.wenku8.com/3/3770/141001/1.jpg",
            "https://pic.wenku8.cn/pictures/3/3770/141001/1.jpg",
        )
        assertEquals(
            expectedOptionA,
            WenkuImageUrlPolicy.buildOrderedIllustrationAttemptUrls(
                "https://pic.wenku8.com/pictures/3/3770/141001/1.jpg".toHttpUrl(),
            ).map { it.toString() },
        )
        assertEquals(
            "Legacy China-Mobile-blocked pic.wenku8.cn must be demoted to last automatically",
            expectedOptionA,
            WenkuImageUrlPolicy.buildOrderedIllustrationAttemptUrls(
                "https://pic.wenku8.cn/pictures/3/3770/141001/1.jpg".toHttpUrl(),
            ).map { it.toString() },
        )
        assertEquals(
            "Direct pic.777743.xyz input participates bidirectionally in mirror failover",
            expectedOptionA,
            WenkuImageUrlPolicy.buildOrderedIllustrationAttemptUrls(
                "https://pic.777743.xyz/3/3770/141001/1.jpg".toHttpUrl(),
            ).map { it.toString() },
        )
        assertEquals(
            listOf(
                "https://pic.wenku8.com/3/3770/141001/1.jpg",
                "https://pic.777743.xyz/3/3770/141001/1.jpg",
                "https://pic.wenku8.cn/pictures/3/3770/141001/1.jpg",
            ),
            WenkuImageUrlPolicy.buildOrderedIllustrationAttemptUrls(
                url = "https://pic.wenku8.com/pictures/3/3770/141001/1.jpg".toHttpUrl(),
                preferredMirror = WenkuImageUrlPolicy.MIRROR_PIC_WENKU8_COM,
            ).map { it.toString() },
        )

        // Dynamic `pm=` mirrors strictly reject IPs/localhost/ports/paths and prepend ahead of defaults
        val parsedDynamic = WenkuImageUrlPolicy.parseDynamicMirrorSpecs(
            listOf(
                "pic.newcdn.xyz",
                "127.0.0.1",
                "104.21.83.104",
                "localhost",
                "bad.local",
                "pic.evil.xyz:8443",
                "https://pic.evil.xyz",
                "pic.evil.xyz/admin",
                "cdn2.example.org/pictures",
            ),
        )
        assertEquals(
            listOf(
                WenkuImageUrlPolicy.PictureMirrorEndpoint("pic.newcdn.xyz", usesPicturesPrefix = false),
                WenkuImageUrlPolicy.PictureMirrorEndpoint("cdn2.example.org", usesPicturesPrefix = true),
            ),
            parsedDynamic,
        )
        assertEquals(
            "https://pic.newcdn.xyz/3/3770/141001/1.jpg",
            WenkuImageUrlPolicy.upgradeToHttpsIfSupported("http://pic.newcdn.xyz/3/3770/141001/1.jpg", parsedDynamic),
        )
        assertEquals(
            listOf(
                "https://pic.newcdn.xyz/3/3770/141001/1.jpg",
                "https://cdn2.example.org/pictures/3/3770/141001/1.jpg",
                "https://pic.777743.xyz/3/3770/141001/1.jpg",
                "https://pic.wenku8.com/3/3770/141001/1.jpg",
                "https://pic.wenku8.cn/pictures/3/3770/141001/1.jpg",
            ),
            WenkuImageUrlPolicy.buildOrderedIllustrationAttemptUrls(
                url = "https://pic.wenku8.com/pictures/3/3770/141001/1.jpg".toHttpUrl(),
                dynamicMirrors = parsedDynamic,
            ).map { it.toString() },
        )

        val coverUrl = "https://img.wenku8.com/image/3/3770/3770s.jpg".toHttpUrl()
        assertEquals(
            listOf("https://img.wenku8.com/image/3/3770/3770s.jpg"),
            WenkuImageUrlPolicy.buildOrderedIllustrationAttemptUrls(coverUrl, dynamicMirrors = parsedDynamic)
                .map { it.toString() },
        )

        // 3. Redirect Location header upgrade interceptor upgrades `http://pic.777743.xyz/...` -> `https://...`
        val redirectTestClient = OkHttpClient.Builder()
            .followRedirects(false)
            .followSslRedirects(false)
            .addInterceptor(WenkuHttpsRedirectUpgradeInterceptor.DEFAULT)
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_2)
                    .code(301)
                    .message("Moved Permanently")
                    .header("Location", "http://pic.777743.xyz/pictures/1/1305/41759/50471.jpg")
                    .body("".toResponseBody())
                    .build()
            }
            .build()
        redirectTestClient.newCall(
            Request.Builder()
                .url("https://pic.wenku8.cn/pictures/1/1305/41759/50471.jpg")
                .get()
                .build(),
        ).execute().use { upgradedRedirect ->
            assertEquals(
                "https://pic.777743.xyz/pictures/1/1305/41759/50471.jpg",
                upgradedRedirect.header("Location"),
            )
        }

        // 4. Redacted IO reason strips raw IPs & native pointers while keeping actionable OS reason
        val conscryptError = IOException(
            "Read error: ssl=0x7f1a2b3c4d: I/O error during system call, Connection reset by peer (104.21.83.104:443)",
        )
        val sanitized = WenkuImageUrlPolicy.sanitizeIoExceptionReason(conscryptError)
        assertEquals("Connection reset by peer", sanitized)
        assertEquals(
            "Connection reset",
            WenkuImageUrlPolicy.sanitizeIoExceptionReason(SocketException("Connection reset")),
        )
    }
}
