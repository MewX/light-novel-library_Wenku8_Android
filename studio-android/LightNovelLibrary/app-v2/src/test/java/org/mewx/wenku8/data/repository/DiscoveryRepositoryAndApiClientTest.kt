package org.mewx.wenku8.data.repository

import android.content.ContentValues
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.remote.api.OnlineSearchField
import org.mewx.wenku8.data.remote.api.WenkuApiClient
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import org.mewx.wenku8.domain.model.DiscoveryFeedErrorKind
import org.mewx.wenku8.domain.model.LatestFeedCategory
import org.mewx.wenku8.domain.model.RankingMetric
import org.mewx.wenku8.domain.model.RankingPeriod
import org.mewx.wenku8.domain.model.UnifiedSearchMode
import org.mewx.wenku8.domain.model.resolveRankingSortedBy
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DiscoveryRepositoryAndApiClientTest {

    private lateinit var database: Wenku8Database

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directExecutor = java.util.concurrent.Executor { command -> command.run() }
        database = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun latestAndRankingMappingsCoverAllThirteenServerSortModes() {
        val coveredModes = buildSet {
            LatestFeedCategory.entries.forEach { add(it.sortedBy) }
            RankingPeriod.entries.forEach { period ->
                add(resolveRankingSortedBy(RankingMetric.VISITS, period))
                add(resolveRankingSortedBy(RankingMetric.RECOMMENDATIONS, period))
            }
            add(resolveRankingSortedBy(RankingMetric.FAVORITES, RankingPeriod.ALL_TIME))
            add(resolveRankingSortedBy(RankingMetric.WORD_COUNT, RankingPeriod.ALL_TIME))
        }

        assertEquals(Wenku8API.NovelSortedBy.entries.toSet(), coveredModes)
        assertEquals(13, coveredModes.size)
    }

    @Test
    fun wenkuApiClientDistinguishesUnconfiguredNumericServerErrorParseFailureAndValidPage() = runTest {
        var nextPayloadBytes: ByteArray? = null
        var transportConfigured = false
        var activeEndpoint = "Unknown"

        val fakeTransport = object : WenkuTransport {
            override val isEndpointConfigured: Boolean
                get() = transportConfigured

            override suspend fun postForm(endpointUrl: String, formParams: ContentValues): ByteArray? {
                return nextPayloadBytes
            }
        }
        val jvmParamsFactory = object : org.mewx.wenku8.data.remote.api.WenkuParamsFactory {
            override fun baseUrlOrUnknown(): String = activeEndpoint
            override fun resolveCoverUrl(aid: Int): String = "https://img.example.test/$aid.jpg"
            override fun novelListWithInfo(
                sortedBy: Wenku8API.NovelSortedBy,
                page: Int,
                lang: Wenku8API.AppLanguage,
            ): ContentValues = ContentValues().apply {
                put("sort", sortedBy.name)
                put("page", page)
            }
            override fun searchByNovelName(query: String, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun searchByAuthorName(query: String, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun novelShortInfo(aid: Int, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun novelFullMeta(aid: Int, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun novelFullIntro(aid: Int, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun novelIndex(aid: Int, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun novelContent(aid: Int, cid: Int, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun bookshelfListParams(lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
        }

        // Also verify NativeWenkuParamsFactory never crashes on host JVM (with or without :api-stub).
        val nativeDefaultClient = WenkuApiClient(transport = fakeTransport)
        assertEquals(
            WenkuApiResult.ApiNotConfigured,
            nativeDefaultClient.fetchNovelListPage(
                sortedBy = Wenku8API.NovelSortedBy.lastUpdate,
                pageNumber = 1,
                appLanguage = Wenku8API.AppLanguage.SC,
            ),
        )

        val client = WenkuApiClient(
            transport = fakeTransport,
            paramsFactory = jvmParamsFactory,
        )
        assertEquals(
            WenkuApiResult.ApiNotConfigured,
            client.fetchNovelListPage(
                sortedBy = Wenku8API.NovelSortedBy.lastUpdate,
                pageNumber = 1,
                appLanguage = Wenku8API.AppLanguage.SC,
            ),
        )

        transportConfigured = true
        activeEndpoint = "https://relay.example.test/api.php"

        nextPayloadBytes = "11".toByteArray(Charsets.UTF_8)
        assertEquals(
            WenkuApiResult.ServerErrorCode(11),
            client.fetchNovelListPage(
                sortedBy = Wenku8API.NovelSortedBy.lastUpdate,
                pageNumber = 1,
                appLanguage = Wenku8API.AppLanguage.SC,
            ),
        )

        nextPayloadBytes = "<html>502 Bad Gateway</html>".toByteArray(Charsets.UTF_8)
        assertTrue(
            client.fetchNovelListPage(
                sortedBy = Wenku8API.NovelSortedBy.lastUpdate,
                pageNumber = 1,
                appLanguage = Wenku8API.AppLanguage.SC,
            ) is WenkuApiResult.ParseError,
        )

        nextPayloadBytes = """
            <?xml version="1.0" encoding="utf-8"?>
            <result>
                <page num="5"/>
                <item aid="2500">
                    <data name="Title"><![CDATA[86-不存在的战区-]]></data>
                    <data name="Author" value="安里アサト"/>
                    <data name="BookStatus" value="连载中"/>
                    <data name="LastUpdate" value="2026-10-01"/>
                    <data name="Tags" value="科幻 战争"/>
                    <data name="IntroPreview"><![CDATA[　　共和国前线战记。]]></data>
                </item>
            </result>
        """.trimIndent().toByteArray(Charsets.UTF_8)

        val validPage = client.fetchNovelListPage(
            sortedBy = Wenku8API.NovelSortedBy.lastUpdate,
            pageNumber = 1,
            appLanguage = Wenku8API.AppLanguage.SC,
        )
        assertTrue(validPage is WenkuApiResult.Success)
        val pageData = (validPage as WenkuApiResult.Success).data
        assertEquals(5, pageData.totalPages)
        assertEquals(1, pageData.items.size)
        assertEquals("86-不存在的战区-", pageData.items.first().title)
    }

    @Test
    fun paginatedFeedPreservesPageOnFailureAndMonotonicallyMergesSparseMetadata() = runTest {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 2500,
                title = "86-不存在的战区-",
                author = "安里アサト",
                inBookshelf = true,
            ),
        )

        val gateway = FakeCatalogGateway()
        gateway.listPages[1] = WenkuApiResult.Success(
            ParsedNovelListPage(
                totalPages = 3,
                items = listOf(
                    ParsedNovelListItem(
                        aid = 2500,
                        title = "86-不存在的战区-",
                        author = "安里アサト",
                        bookStatus = "连载中",
                        lastUpdate = "2026-10-01",
                        tagsCsv = "科幻 战争",
                        introShort = "共和国战记",
                    ),
                ),
            ),
        )
        gateway.listPages[2] = WenkuApiResult.NetworkError("timeout")

        val repository = DiscoveryRepository(database = database, apiClient = gateway)
        val sort = Wenku8API.NovelSortedBy.lastUpdate

        repository.loadInitialOrRefreshFeed(sort, Wenku8API.AppLanguage.SC)
        val pageOneState = repository.observeFeedState(sort).value
        assertEquals(1, pageOneState.loadedPage)
        assertEquals(3, pageOneState.totalPages)
        assertEquals(1, pageOneState.items.size)
        assertTrue(pageOneState.items[0].inLocalBookshelf)
        assertEquals(listOf("科幻", "战争"), pageOneState.items[0].tags)

        // Failing page 2 must NOT clear page 1 items or advance loadedPage.
        repository.loadNextFeedPage(sort, Wenku8API.AppLanguage.SC)
        val failedPageTwoState = repository.observeFeedState(sort).value
        assertEquals(1, failedPageTwoState.loadedPage)
        assertEquals(1, failedPageTwoState.items.size)
        assertEquals(DiscoveryFeedErrorKind.NETWORK_UNREACHABLE, failedPageTwoState.errorKind)

        // Recovering page 2 with a sparser record for 2500 (missing tagsCsv) + new item 2600 must
        // preserve 2500's cached tags while appending 2600 without duplicates.
        gateway.listPages[2] = WenkuApiResult.Success(
            ParsedNovelListPage(
                totalPages = 3,
                items = listOf(
                    ParsedNovelListItem(
                        aid = 2500,
                        title = "86-不存在的战区-",
                        author = "安里アサト",
                        bookStatus = "连载中",
                        lastUpdate = "2026-10-05",
                        tagsCsv = "",
                        introShort = "",
                    ),
                    ParsedNovelListItem(
                        aid = 2600,
                        title = "败犬女主太多了！",
                        author = "雨森焚火",
                        bookStatus = "连载中",
                        lastUpdate = "2026-10-06",
                        tagsCsv = "校园 恋爱",
                        introShort = "青春败犬喜剧",
                    ),
                ),
            ),
        )

        repository.loadNextFeedPage(sort, Wenku8API.AppLanguage.SC)
        val pageTwoState = repository.observeFeedState(sort).value
        assertEquals(2, pageTwoState.loadedPage)
        assertEquals(2, pageTwoState.items.size)
        assertEquals(listOf("科幻", "战争"), pageTwoState.items[0].tags)
        assertEquals("共和国战记", pageTwoState.items[0].introShort)
        assertEquals("2026-10-05", pageTwoState.items[0].lastUpdate)
        assertEquals(2600, pageTwoState.items[1].aid)
    }

    @Test
    fun onlineSearchAutoQueuesFiveSecondCooldownSupersedesIntermediateQueryAndBypassesForDirectAid() {
        val testDispatcher = StandardTestDispatcher()
        val testScope = TestScope(testDispatcher)

        testScope.runTest {
            database.novelDao().upsertNovel(
                NovelEntity(
                    aid = 1306,
                    title = "向森之魔物献上花束",
                    author = "小木君人",
                    tagsCsv = "奇幻 治愈",
                    inBookshelf = true,
                ),
            )

            val gateway = FakeCatalogGateway()
            gateway.searchResultsByQuery["魔物"] = listOf(1306)
            gateway.searchResultsByQuery["败犬"] = listOf(2600)
            gateway.shortInfoByAid[2600] = ParsedNovelListItem(
                aid = 2600,
                title = "败犬女主太多了！",
                author = "雨森焚火",
                bookStatus = "连载中",
                lastUpdate = "2026-10-06",
                introShort = "八奈见杏菜的青春日常",
            )
            gateway.fullMetaByAid[3001] = ParsedNovelFullMeta(
                aid = 3001,
                title = "义妹生活",
                author = "三河ごーすと",
                pressId = "MF文库J",
                bookStatus = "连载中",
                lastUpdate = "2026-09-12",
                latestSectionName = "第十二卷 后记",
            )

            var clockMs = 1_000L
            val repository = DiscoveryRepository(
                database = database,
                apiClient = gateway,
                monotonicClockMillis = { clockMs + testScheduler.currentTime },
            )

            // Instant < 1ms local Room filter works offline with zero network calls.
            val localMatches = repository.filterLocalLibraryInstant("魔物")
            assertEquals(1, localMatches.size)
            assertEquals(1306, localMatches[0].aid)
            assertEquals(0, gateway.dispatchedSearchQueries.size)

            // Search #1 at T = 0ms executes immediately.
            repository.submitOnlineSearch(
                scope = this,
                rawQuery = "魔物",
                mode = UnifiedSearchMode.NOVEL_TITLE,
                appLanguage = Wenku8API.AppLanguage.SC,
            )
            runCurrent()
            assertEquals(listOf("魔物"), gateway.dispatchedSearchQueries)
            assertEquals("向森之魔物献上花束", repository.onlineSearchState.value.results.single().title)

            // Advance 1,000ms (still 4,000ms left in cooldown), submit two rapid queries:
            // intermediate "废弃查询" must be superseded by "败犬".
            advanceTimeBy(1_000L)
            runCurrent()

            repository.submitOnlineSearch(
                scope = this,
                rawQuery = "废弃查询",
                mode = UnifiedSearchMode.NOVEL_TITLE,
                appLanguage = Wenku8API.AppLanguage.SC,
            )
            runCurrent()
            repository.submitOnlineSearch(
                scope = this,
                rawQuery = "败犬",
                mode = UnifiedSearchMode.NOVEL_TITLE,
                appLanguage = Wenku8API.AppLanguage.SC,
            )
            runCurrent()

            val queuedState = repository.onlineSearchState.value
            assertTrue(queuedState.isQueuedForCooldown)
            assertEquals("败犬", queuedState.submittedQuery)
            assertEquals(4_000L, queuedState.remainingCooldownMillis)
            assertEquals(listOf("魔物"), gateway.dispatchedSearchQueries)

            // Advance remaining 4,000ms -> "败犬" fires automatically and hydrates 2600!
            advanceTimeBy(4_000L)
            runCurrent()

            assertEquals(listOf("魔物", "败犬"), gateway.dispatchedSearchQueries)
            val completedState = repository.onlineSearchState.value
            assertFalse(completedState.isQueuedForCooldown)
            assertEquals("败犬女主太多了！", completedState.results.single().title)
            assertEquals("雨森焚火", completedState.results.single().author)

            // Immediately at T = 5,000ms (0ms elapsed since "败犬"), DIRECT_AID lookup bypasses
            // the 5s action=search cooldown completely via do=meta!
            repository.submitOnlineSearch(
                scope = this,
                rawQuery = "AID 3001",
                mode = UnifiedSearchMode.DIRECT_AID,
                appLanguage = Wenku8API.AppLanguage.SC,
            )
            runCurrent()
            assertEquals("义妹生活", repository.onlineSearchState.value.results.single().title)

            // Verify 20-item search history cap with monotonically increasing timestamps.
            for (index in 1..25) {
                clockMs += 10L
                repository.recordSearchHistory("query-$index")
            }
            val recentHistory = repository.observeSearchHistory().first()
            assertEquals(20, recentHistory.size)
            assertEquals("query-25", recentHistory.first())
        }
    }

    @Test
    fun wenkuApiClientClassifiesEmptyNullHtmlNumericAndValidChapterResponses() = runTest {
        var nextPayloadBytes: ByteArray? = null
        val fakeTransport = object : WenkuTransport {
            override val isEndpointConfigured: Boolean = true
            override suspend fun postForm(endpointUrl: String, formParams: ContentValues): ByteArray? = nextPayloadBytes
        }
        val jvmParamsFactory = object : org.mewx.wenku8.data.remote.api.WenkuParamsFactory {
            override fun baseUrlOrUnknown(): String = "https://relay.example.test/api.php"
            override fun resolveCoverUrl(aid: Int): String = "https://img.example.test/$aid.jpg"
            override fun novelListWithInfo(
                sortedBy: Wenku8API.NovelSortedBy,
                page: Int,
                lang: Wenku8API.AppLanguage,
            ): ContentValues = ContentValues()
            override fun searchByNovelName(query: String, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun searchByAuthorName(query: String, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun novelShortInfo(aid: Int, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun novelFullMeta(aid: Int, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun novelFullIntro(aid: Int, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun novelIndex(aid: Int, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun novelContent(aid: Int, cid: Int, lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
            override fun bookshelfListParams(lang: Wenku8API.AppLanguage): ContentValues = ContentValues()
        }
        val client = WenkuApiClient(transport = fakeTransport, paramsFactory = jvmParamsFactory)

        // 1. Upstream HTTP 200 with 0 bytes (reproduces aid=4358, cid=183108)
        nextPayloadBytes = ByteArray(0)
        assertEquals(
            WenkuApiResult.EmptyResponse,
            client.fetchChapterContent(aid = 4358, cid = 183108, appLanguage = Wenku8API.AppLanguage.SC),
        )

        // 2. UTF-8 BOM + ASCII/ideographic whitespace only -> EmptyResponse
        nextPayloadBytes = "\uFEFF \u3000\r\n\t ".toByteArray(Charsets.UTF_8)
        assertEquals(
            WenkuApiResult.EmptyResponse,
            client.fetchChapterContent(aid = 4358, cid = 183108, appLanguage = Wenku8API.AppLanguage.SC),
        )

        // 3. Wenku8 literal "null" sentinel for missing chapters/intros -> EmptyResponse
        nextPayloadBytes = "null".toByteArray(Charsets.UTF_8)
        assertEquals(
            WenkuApiResult.EmptyResponse,
            client.fetchChapterContent(aid = 4358, cid = 183107, appLanguage = Wenku8API.AppLanguage.SC),
        )
        nextPayloadBytes = "  NULL \r\n".toByteArray(Charsets.UTF_8)
        assertEquals(
            WenkuApiResult.EmptyResponse,
            client.fetchNovelFullIntro(aid = 4358, appLanguage = Wenku8API.AppLanguage.SC),
        )

        // 4. HTML gateway/welcome/WAF error pages -> ParseError (never cached as novel prose)
        nextPayloadBytes = "<html><body><h1>Welcome</h1></body></html>".toByteArray(Charsets.UTF_8)
        assertTrue(
            client.fetchChapterContent(aid = 4358, cid = 183108, appLanguage = Wenku8API.AppLanguage.SC)
                is WenkuApiResult.ParseError,
        )
        nextPayloadBytes = "\uFEFF  <!DOCTYPE html><html><title>Just a moment...</title></html>".toByteArray(Charsets.UTF_8)
        assertTrue(
            client.fetchChapterContent(aid = 4358, cid = 183108, appLanguage = Wenku8API.AppLanguage.SC)
                is WenkuApiResult.ParseError,
        )

        // 5. Numeric error code -> ServerErrorCode
        nextPayloadBytes = "4".toByteArray(Charsets.UTF_8)
        assertEquals(
            WenkuApiResult.ServerErrorCode(4),
            client.fetchChapterContent(aid = 4358, cid = 183108, appLanguage = Wenku8API.AppLanguage.SC),
        )

        // 6. Transport failure (null bytes) -> NetworkError
        nextPayloadBytes = null
        assertTrue(
            client.fetchChapterContent(aid = 4358, cid = 183108, appLanguage = Wenku8API.AppLanguage.SC)
                is WenkuApiResult.NetworkError,
        )

        // 7. Valid chapter prose and valid intro -> Success
        val validBody = "第三卷 下 9 橙色的魔法使\r\n\r\n　　三咲市的暮色沉入丘陵之中。"
        nextPayloadBytes = validBody.toByteArray(Charsets.UTF_8)
        assertEquals(
            WenkuApiResult.Success(validBody),
            client.fetchChapterContent(aid = 4358, cid = 183223, appLanguage = Wenku8API.AppLanguage.SC),
        )
        val validIntro = "1980年代后半，正是繁华与活力的时代的末期。"
        nextPayloadBytes = validIntro.toByteArray(Charsets.UTF_8)
        assertEquals(
            WenkuApiResult.Success(validIntro),
            client.fetchNovelFullIntro(aid = 4358, appLanguage = Wenku8API.AppLanguage.SC),
        )
    }

    @Test
    fun wenkuXmlParsersParseRealAid4358MetadataBookInfoAndVolumeToc() {
        val metaXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <metadata>
            <data name="Title" aid="4358"><![CDATA[魔法使之夜 小说]]></data>
            <data name="Author" value="奈须蘑菇"/>
            <data name="DayHitsCount" value="823"/>
            <data name="TotalHitsCount" value="32778"/>
            <data name="PushCount" value="183"/>
            <data name="FavCount" value="2525"/>
            <data name="PressId" value="讲谈社" sid="11"/>
            <data name="BookStatus" value="连载中"/>
            <data name="BookLength" value="402379"/>
            <data name="LastUpdate" value="2026-11-20"/>
            <data name="Tags" value="校园 奇幻 魔法 战斗 恋爱"/>
            <data name="LatestSection" cid="183630"><![CDATA[第三卷 下 11 青色的魔法]]></data>
            </metadata>
        """.trimIndent()

        val bookInfoXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <metadata>
            <data name="Title" aid="4358"><![CDATA[魔法使之夜 小说]]></data>
            <data name="Author" value="奈须蘑菇"/>
            <data name="BookStatus" value="连载中"/>
            <data name="LastUpdate" value="2026-11-20"/>
            <data name="Tags" value="校园 奇幻 魔法 战斗 恋爱"/>
            <data name="IntroPreview"><![CDATA[1980年代后半，正是繁华与活力的时代的末期。在...]]></data>
            </metadata>
        """.trimIndent()

        val listXml = """
            <?xml version="1.0" encoding="utf-8"?>
            <package>
            <volume vid="178541"><![CDATA[第一卷 上]]>
            <chapter cid="178542"><![CDATA[0 Avenue cafe（街边咖啡馆）]]></chapter>
            <chapter cid="178543"><![CDATA[1 不普通的人]]></chapter>
            <chapter cid="178544"><![CDATA[2 昨天，有过这样一件事]]></chapter>
            <chapter cid="178545"><![CDATA[2.5 此前的故事]]></chapter>
            <chapter cid="178546"><![CDATA[3 那一夜]]></chapter>
            <chapter cid="178547"><![CDATA[4 苍崎青子，其二]]></chapter>
            <chapter cid="178548"><![CDATA[5 魔法使之夜 前篇]]></chapter>
            <chapter cid="178549"><![CDATA[插图]]></chapter>
            </volume>
            <volume vid="181295"><![CDATA[第二卷 中]]>
            <chapter cid="181296"><![CDATA[5 魔法使之夜 后篇①]]></chapter>
            <chapter cid="181297"><![CDATA[5 魔法使之夜 后篇②]]></chapter>
            <chapter cid="181298"><![CDATA[6 比狗还简单]]></chapter>
            <chapter cid="181299"><![CDATA[7 哪有这种荒唐事啊 前篇]]></chapter>
            <chapter cid="181300"><![CDATA[7.5 玛莎]]></chapter>
            <chapter cid="181301"><![CDATA[8 真有这种荒唐事吗？后篇]]></chapter>
            <chapter cid="181302"><![CDATA[7.8 白板革命]]></chapter>
            <chapter cid="181303"><![CDATA[插图]]></chapter>
            </volume>
            <volume vid="183107"><![CDATA[第三卷 下]]>
            <chapter cid="183108"><![CDATA[8.5 鸭内基事件]]></chapter>
            <chapter cid="183223"><![CDATA[9 橙色的魔法使]]></chapter>
            <chapter cid="183290"><![CDATA[10 对月咆哮]]></chapter>
            <chapter cid="183630"><![CDATA[11 青色的魔法]]></chapter>
            </volume>
            </package>
        """.trimIndent()

        val meta = WenkuXmlParsers.parseNovelFullMeta(metaXml)
        assertTrue(meta != null)
        assertEquals(4358, meta!!.aid)
        assertEquals("魔法使之夜 小说", meta.title)
        assertEquals("奈须蘑菇", meta.author)
        assertEquals(183630, meta.latestSectionCid)

        val shortInfo = WenkuXmlParsers.parseNovelShortInfoUpdate(bookInfoXml)
        assertTrue(shortInfo != null)
        assertEquals(4358, shortInfo!!.aid)
        assertEquals("魔法使之夜 小说", shortInfo.title)

        val volumes = WenkuXmlParsers.parseVolumeTocList(listXml)
        assertEquals(3, volumes.size)
        assertEquals(20, volumes.sumOf { it.chapters.size })
        val volume3 = volumes[2]
        assertEquals(183107, volume3.vid)
        assertEquals("第三卷 下", volume3.volumeTitle)
        assertEquals(183108, volume3.chapters[0].cid)
        assertEquals("8.5 鸭内基事件", volume3.chapters[0].chapterTitle)
        assertEquals(183223, volume3.chapters[1].cid)
        assertEquals("9 橙色的魔法使", volume3.chapters[1].chapterTitle)
    }

    private class FakeCatalogGateway : WenkuCatalogGateway {
        override val isApiConfigured: Boolean = true

        val listPages = mutableMapOf<Int, WenkuApiResult<ParsedNovelListPage>>()
        val searchResultsByQuery = mutableMapOf<String, List<Int>>()
        val shortInfoByAid = mutableMapOf<Int, ParsedNovelListItem>()
        val fullMetaByAid = mutableMapOf<Int, ParsedNovelFullMeta>()
        val dispatchedSearchQueries = mutableListOf<String>()

        override fun resolveCoverUrl(aid: Int): String = "https://img.example.test/$aid.jpg"

        override suspend fun fetchNovelListPage(
            sortedBy: Wenku8API.NovelSortedBy,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListPage> {
            return listPages[pageNumber] ?: WenkuApiResult.NetworkError("missing page $pageNumber")
        }

        override suspend fun searchNovelsByField(
            query: String,
            searchField: OnlineSearchField,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<Int>> {
            dispatchedSearchQueries += query
            return WenkuApiResult.Success(searchResultsByQuery[query].orEmpty())
        }

        override suspend fun fetchNovelShortInfo(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListItem> {
            val item = shortInfoByAid[aid] ?: return WenkuApiResult.NetworkError("missing short info")
            return WenkuApiResult.Success(item)
        }

        override suspend fun fetchNovelFullMeta(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelFullMeta> {
            val meta = fullMetaByAid[aid] ?: return WenkuApiResult.NetworkError("missing meta")
            return WenkuApiResult.Success(meta)
        }

        override suspend fun fetchNovelFullIntro(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<String> = WenkuApiResult.NetworkError("unused")

        override suspend fun fetchNovelVolumeToc(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ) = WenkuApiResult.NetworkError("unused")

        override suspend fun fetchChapterContent(
            aid: Int,
            cid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<String> = WenkuApiResult.NetworkError("unused")

        override suspend fun fetchCloudBookshelf(
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.NetworkError("unused")
    }
}
