package org.mewx.wenku8.data.remote.parser

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.toUri
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.data.media.OfflineFirstWenkuImageFetcher
import org.mewx.wenku8.data.media.WenkuImageRequests
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

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
}
