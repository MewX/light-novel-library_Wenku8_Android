package org.mewx.wenku8.v2.ui.bookshelf

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.api.Wenku8Error
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.v2.data.local.db.entity.CloudBookcaseLedgerEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.v2.data.remote.api.OnlineSearchField
import org.mewx.wenku8.v2.data.remote.api.WenkuApiClient
import org.mewx.wenku8.v2.data.remote.api.WenkuApiResult
import org.mewx.wenku8.v2.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.v2.data.remote.api.WenkuParamsFactory
import org.mewx.wenku8.v2.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.v2.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.v2.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.v2.data.remote.transport.WenkuTransport
import org.mewx.wenku8.v2.data.repository.AccountAuthGateway
import org.mewx.wenku8.v2.data.repository.BookshelfFilter
import org.mewx.wenku8.v2.data.repository.BookshelfRepository
import org.mewx.wenku8.v2.data.repository.BookshelfSortMode
import org.mewx.wenku8.v2.data.repository.BookshelfViewMode
import org.mewx.wenku8.v2.data.repository.CloudBookshelfSyncOutcome
import org.mewx.wenku8.v2.data.repository.LoginOutcome
import org.mewx.wenku8.v2.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.v2.data.repository.ParsedChapterToc
import org.mewx.wenku8.v2.data.repository.ParsedVolumeToc
import org.mewx.wenku8.v2.data.repository.StoredAccountCredentials
import org.mewx.wenku8.v2.data.repository.UserSessionRepository
import org.mewx.wenku8.v2.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.v2.ui.theme.Wenku8ThemeMode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h892dp")
class BookshelfScreenAndSyncTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: Wenku8Database
    private lateinit var tocSyncRepository: NovelTocSyncRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val directExecutor = java.util.concurrent.Executor { it.run() }
        database = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .setQueryExecutor(directExecutor)
            .setTransactionExecutor(directExecutor)
            .build()
        tocSyncRepository = NovelTocSyncRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun cloudBookcaseSync_isNonDestructive_pushesLocalOnly_handlesShelfFull_andFlagsUnreadUpdates() = runTest {
        // Seed 3 local bookshelf novels:
        // - 1001: shared with cloud, stale latestSectionCid (10 -> 12 on cloud)
        // - 1002: local-only, succeeds when pushed to cloud
        // - 1003: local-only, hits SYSTEM_6_BOOKSHELF_FULL (must NEVER be deleted locally!)
        database.novelDao().upsertNovels(
            listOf(
                NovelEntity(
                    aid = 1001,
                    title = "狼与香辛料",
                    author = "支仓冻砂",
                    pressId = "电击文库",
                    bookStatus = "已完成",
                    lastUpdate = "2026-01-10",
                    latestSectionCid = 10,
                    latestSectionName = "第十六卷 后记",
                    tocVersion = 1L,
                    inBookshelf = true,
                    shelfSortOrder = 1,
                    cloudSynced = true,
                ),
                NovelEntity(
                    aid = 1002,
                    title = "奇诺之旅",
                    author = "时雨泽惠一",
                    pressId = "电击文库",
                    bookStatus = "连载中",
                    inBookshelf = true,
                    shelfSortOrder = 2,
                    cloudSynced = false,
                ),
                NovelEntity(
                    aid = 1003,
                    title = "文学少女",
                    author = "野村美月",
                    pressId = "Fami通文库",
                    bookStatus = "已完成",
                    inBookshelf = true,
                    shelfSortOrder = 3,
                    cloudSynced = false,
                ),
            ),
        )

        val gateway = FakeBookshelfCatalogGateway().apply {
            cloudBookshelfEntries = listOf(
                ParsedCloudBookshelfEntry(
                    aid = 1001,
                    lastUpdateDate = "2026-02-18",
                    title = "狼与香辛料",
                    latestChapterCid = 12,
                    latestChapterName = "第十七卷 尾声",
                ),
                ParsedCloudBookshelfEntry(
                    aid = 2001,
                    lastUpdateDate = "2026-02-20",
                    title = "败犬女主太多了！",
                    latestChapterCid = 88,
                    latestChapterName = "第七卷 插图",
                ),
            )
            addResponsesByAid[1002] = WenkuApiResult.Success(Unit)
            addResponsesByAid[1003] = WenkuApiResult.ServerErrorCode(
                numericCode = BookshelfRepository.SERVER_CODE_BOOKSHELF_FULL,
            )
        }

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
        )

        val outcome = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC)
        assertTrue(outcome is CloudBookshelfSyncOutcome.Success)
        val success = outcome as CloudBookshelfSyncOutcome.Success
        assertEquals(1, success.pulledFromCloudCount)
        assertEquals(1, success.pushedToCloudCount)
        assertEquals(1, success.refreshedStaleCount)
        assertTrue(success.cloudShelfFullReached)

        val shelfAfter = database.novelDao().getBookshelfNovelsSnapshot().associateBy { it.aid }
        // All 4 novels must remain on the local bookshelf (zero data loss on cloud shelf full!)
        assertEquals(setOf(1001, 1002, 1003, 2001), shelfAfter.keys)

        val updatedShared = shelfAfter.getValue(1001)
        assertTrue(updatedShared.hasUnreadUpdate)
        assertEquals(12, updatedShared.latestSectionCid)

        val pushedLocal = shelfAfter.getValue(1002)
        assertTrue(pushedLocal.cloudSynced)

        val overflowLocal = shelfAfter.getValue(1003)
        assertTrue(overflowLocal.inBookshelf)
        assertFalse(overflowLocal.cloudSynced)

        val pulledCloud = shelfAfter.getValue(2001)
        assertTrue(pulledCloud.inBookshelf)
        assertTrue(pulledCloud.cloudSynced)
        assertEquals("败犬女主太多了！", pulledCloud.title)
    }

    @Test
    fun offlineLocalDelete_plusCachePurge_replaysCloudDeleteAndNeverResurrectsDeletedNovel() = runTest {
        val gateway = FakeBookshelfCatalogGateway().apply {
            cloudBookshelfEntries = listOf(
                ParsedCloudBookshelfEntry(
                    aid = 3001,
                    lastUpdateDate = "2026-03-01",
                    title = "春物语",
                    latestChapterCid = 301,
                    latestChapterName = "第十四卷 后记",
                ),
            )
        }
        val repository = BookshelfRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
        )

        // 1. Initial sync pulls 3001 onto the local bookshelf
        val initialSync = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC)
        assertTrue(initialSync is CloudBookshelfSyncOutcome.Success)
        assertEquals(listOf(3001), database.novelDao().getBookshelfNovelIds())

        // 2. User goes offline, deletes 3001 locally, AND triggers browse-cache purge
        gateway.removeResponsesByAid[3001] = WenkuApiResult.NetworkError("offline")
        repository.removeNovelsFromBookshelf(listOf(3001))
        database.novelDao().deleteAllNonBookshelfNovels()
        assertNull(database.novelDao().getNovelByAid(3001))

        // Durable tombstone MUST survive deleteAllNonBookshelfNovels()!
        val tombstone = database.cloudBookcaseLedgerDao().getEntry(
            accountKey = CloudBookcaseLedgerEntity.DEFAULT_TEST_ACCOUNT_KEY,
            aid = 3001,
        )
        assertNotNull(tombstone)
        assertEquals(CloudBookcaseLedgerEntity.STATE_PENDING_DELETE, tombstone!!.syncState)

        // 3. First sync attempt while `do=del` STILL transiently fails, even though `fetchCloudBookshelf`
        //    succeeds and still lists 3001: 3001 MUST NOT resurrect onto the local shelf!
        val flakySync = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC)
        assertTrue(flakySync is CloudBookshelfSyncOutcome.Success)
        assertTrue(database.novelDao().getBookshelfNovelIds().isEmpty())
        assertEquals(
            CloudBookcaseLedgerEntity.STATE_PENDING_DELETE,
            database.cloudBookcaseLedgerDao().getEntry(
                CloudBookcaseLedgerEntity.DEFAULT_TEST_ACCOUNT_KEY,
                3001,
            )?.syncState,
        )

        // 4. Connection stabilizes -> `do=del` succeeds; tombstone clears & 3001 stays deleted forever!
        gateway.removeResponsesByAid[3001] = WenkuApiResult.Success(Unit)
        val cleanSync = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success
        assertEquals(1, cleanSync.deletedFromCloudCount)
        assertTrue(3001 in gateway.removedAidsFromCloud)
        assertTrue(database.novelDao().getBookshelfNovelIds().isEmpty())
        assertNull(
            database.cloudBookcaseLedgerDao().getEntry(
                CloudBookcaseLedgerEntity.DEFAULT_TEST_ACCOUNT_KEY,
                3001,
            ),
        )
    }

    @Test
    fun remoteCloudDeletion_removesSyncedNovelWithoutReuploading_whilePreservingProgressAndUnsyncedBooks() = runTest {
        val gateway = FakeBookshelfCatalogGateway().apply {
            cloudBookshelfEntries = listOf(
                ParsedCloudBookshelfEntry(aid = 4001, lastUpdateDate = "2026-03-05", title = "刀剑神域", latestChapterCid = 41),
                ParsedCloudBookshelfEntry(aid = 4002, lastUpdateDate = "2026-03-06", title = "加速世界", latestChapterCid = 42),
            )
        }
        val repository = BookshelfRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
        )

        // 1. Sync 4001 & 4002 down from cloud
        repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC)
        assertEquals(setOf(4001, 4002), database.novelDao().getBookshelfNovelIds().toSet())

        // Seed reading progress + cached chapter on 4001, and add offline local-only 4003
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 4001,
                vid = 40010,
                cid = 40011,
                volumeTitle = "第一卷",
                chapterTitle = "第一话",
                progressPermille = 750,
                updatedAtMs = 99_000L,
            ),
        )
        gateway.addResponsesByAid[4003] = WenkuApiResult.NetworkError("offline during add")
        repository.addNovelToBookshelf(
            NovelEntity(aid = 4003, title = "离线新加作品", author = "作者丙"),
            syncToCloudIfSignedIn = true,
        )
        gateway.pushedAidsToCloud.clear()
        gateway.addResponsesByAid[4003] = WenkuApiResult.Success(Unit)

        // 2. Simulate user deleting 4001 on Wenku8 Web (`action=bookcase` now returns only 4002)
        gateway.cloudBookshelfEntries = listOf(
            ParsedCloudBookshelfEntry(aid = 4002, lastUpdateDate = "2026-03-06", title = "加速世界", latestChapterCid = 42),
        )

        val syncAfterRemoteDelete =
            repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success

        // 4001 must be removed locally (`removedRemotelyDeletedCount == 1`) and NEVER re-uploaded!
        assertEquals(1, syncAfterRemoteDelete.removedRemotelyDeletedCount)
        assertEquals(1, syncAfterRemoteDelete.pushedToCloudCount)
        assertEquals(listOf(4003), gateway.pushedAidsToCloud)
        assertEquals(setOf(4002, 4003), database.novelDao().getBookshelfNovelIds().toSet())

        // Reading progress on remotely-removed 4001 must remain completely intact!
        val preservedProgress = database.readingProgressDao().getProgressForNovel(4001)
        assertNotNull(preservedProgress)
        assertEquals(750, preservedProgress!!.progressPermille)
    }

    @Test
    fun accountSwitchIsolation_keepsPreviousAccountLocalBooksWithoutPollutingNewAccountCloud() = runTest {
        val tempRoot = File(context.cacheDir, "acct_switch_test_${System.nanoTime()}").apply { mkdirs() }
        try {
            val fakeAuthTransport = FakeAccountSwitchTransport()
            val fakeAuthGateway = FakeAccountSwitchAuthGateway(fakeAuthTransport)
            val sessionRepo = UserSessionRepository(
                transport = fakeAuthTransport,
                assetRootsProvider = { listOf(tempRoot) },
                authGateway = fakeAuthGateway,
            )
            val gateway = FakeBookshelfCatalogGateway()
            val repository = BookshelfRepository(
                database = database,
                catalogGateway = gateway,
                novelTocSyncRepository = tocSyncRepository,
                userSessionRepository = sessionRepo,
            )

            // 1. Sign in as Alice (via email -> server resolves canonical username "alice") and sync 5001
            fakeAuthTransport.activeServerUsername = "alice"
            val aliceLogin = sessionRepo.loginWithCredentials("alice@example.com", "secret1")
            assertTrue(aliceLogin is LoginOutcome.Success)
            assertEquals("alice", sessionRepo.activeAccountKeyOrNull())

            gateway.cloudBookshelfEntries = listOf(
                ParsedCloudBookshelfEntry(aid = 5001, lastUpdateDate = "2026-03-10", title = "Alice专属藏书", latestChapterCid = 10),
            )
            val aliceSync = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success
            assertEquals(1, aliceSync.pulledFromCloudCount)
            assertEquals(listOf(5001), database.novelDao().getBookshelfNovelIds())

            // 2. Alice logs out; Bob signs in whose cloud bookcase has ONLY 6001
            sessionRepo.logout()
            fakeAuthTransport.activeServerUsername = "bob"
            val bobLogin = sessionRepo.loginWithCredentials("bob", "secret2")
            assertTrue(bobLogin is LoginOutcome.Success)
            assertEquals("bob", sessionRepo.activeAccountKeyOrNull())

            gateway.pushedAidsToCloud.clear()
            gateway.cloudBookshelfEntries = listOf(
                ParsedCloudBookshelfEntry(aid = 6001, lastUpdateDate = "2026-03-11", title = "Bob专属藏书", latestChapterCid = 20),
            )

            val bobSync = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success
            assertEquals(1, bobSync.pulledFromCloudCount)
            assertEquals(0, bobSync.pushedToCloudCount)
            assertEquals(0, bobSync.removedRemotelyDeletedCount)

            // Alice's 5001 is neither wiped locally nor silently uploaded to Bob's cloud bookcase!
            assertTrue(gateway.pushedAidsToCloud.isEmpty())
            assertEquals(setOf(5001, 6001), database.novelDao().getBookshelfNovelIds().toSet())
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun partialNetworkFailureAndReAdd_commitsAllOrNothing_hydratesTags_andClearsTombstoneOnReAdd() = runTest {
        val gateway = FakeBookshelfCatalogGateway().apply {
            cloudBookshelfEntries = listOf(
                ParsedCloudBookshelfEntry(aid = 7001, lastUpdateDate = "2026-03-12", title = "葬送的芙莉莲", latestChapterCid = 71),
                ParsedCloudBookshelfEntry(aid = 7002, lastUpdateDate = "2026-03-13", title = "迷宫饭", latestChapterCid = 72),
            )
            shortInfoTagsByAid[7001] = "奇幻 冒险 精灵"
            failTocForAids += 7002
        }
        val repository = BookshelfRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
        )

        // 1. First sync: 7001 succeeds (with tags hydrated!); 7002 TOC fails -> NOT committed as stub!
        val firstSync = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success
        assertEquals(1, firstSync.pulledFromCloudCount)
        assertEquals(1, firstSync.failedHydrationCount)
        assertEquals(listOf(7001), database.novelDao().getBookshelfNovelIds())
        assertEquals("奇幻 冒险 精灵", database.novelDao().getNovelByAid(7001)?.tagsCsv)
        assertNull(database.novelDao().getNovelByAid(7002))

        // 2. Network recovers -> second sync pulls 7002 cleanly
        gateway.failTocForAids.clear()
        val secondSync = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success
        assertEquals(1, secondSync.pulledFromCloudCount)
        assertEquals(0, secondSync.failedHydrationCount)
        assertEquals(setOf(7001, 7002), database.novelDao().getBookshelfNovelIds().toSet())

        // 3. Offline delete followed by offline re-add of 7001 clears tombstone -> pushes up when needed
        gateway.removeResponsesByAid[7001] = WenkuApiResult.NetworkError("offline")
        repository.removeNovelsFromBookshelf(listOf(7001))
        gateway.addResponsesByAid[7001] = WenkuApiResult.NetworkError("offline")
        repository.addNovelToBookshelf(NovelEntity(aid = 7001, title = "葬送的芙莉莲", shelfCategory = "最爱"))

        gateway.pushedAidsToCloud.clear()
        gateway.removedAidsFromCloud.clear()
        gateway.addResponsesByAid[7001] = WenkuApiResult.Success(Unit)
        // Simulate 7001 also absent on cloud before sync so re-add must push it up instead of deleting!
        gateway.cloudBookshelfEntries = listOf(
            ParsedCloudBookshelfEntry(aid = 7002, lastUpdateDate = "2026-03-13", title = "迷宫饭", latestChapterCid = 72),
        )
        val thirdSync = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success
        assertEquals(0, thirdSync.deletedFromCloudCount)
        assertEquals(1, thirdSync.pushedToCloudCount)
        assertEquals(listOf(7001), gateway.pushedAidsToCloud)
        assertEquals("最爱", database.novelDao().getNovelByAid(7001)?.shelfCategory)
    }

    @Test
    fun wenkuApiClient_fallsBackToBookshelfAidListOnRichXmlParseError_andMigration1To2PreservesData() = runTest {
        // Part A: Verify WenkuApiClient.fetchCloudBookshelf falls back to `do=list` when rich XML breaks
        val transport = object : WenkuTransport {
            override val isEndpointConfigured: Boolean = true
            override suspend fun postForm(
                endpointUrl: String,
                formParams: ContentValues,
            ): ByteArray {
                return if (formParams.getAsString("do") == "list") {
                    """<?xml version="1.0" encoding="utf-8"?><metadata><package aid="101"/><package aid="202"/></metadata>"""
                        .toByteArray(StandardCharsets.UTF_8)
                } else {
                    // Malformed rich XML (unescaped raw '<' / broken tag)
                    "<package><item aid=\"101\"><name>Broken <Unclosed".toByteArray(StandardCharsets.UTF_8)
                }
            }
            override suspend fun getBytes(url: String): ByteArray? = null
            override fun clearSessionCookie() = Unit
        }
        val paramsFactory = object : WenkuParamsFactory {
            override fun baseUrlOrUnknown(): String = "https://relay.example.test/android.php"
            override fun resolveCoverUrl(aid: Int): String = ""
            override fun novelListWithInfo(sortedBy: Wenku8API.NovelSortedBy, page: Int, lang: Wenku8API.AppLanguage): ContentValues? = null
            override fun searchByNovelName(query: String, lang: Wenku8API.AppLanguage): ContentValues? = null
            override fun searchByAuthorName(query: String, lang: Wenku8API.AppLanguage): ContentValues? = null
            override fun novelShortInfo(aid: Int, lang: Wenku8API.AppLanguage): ContentValues? = null
            override fun novelFullMeta(aid: Int, lang: Wenku8API.AppLanguage): ContentValues? = null
            override fun novelFullIntro(aid: Int, lang: Wenku8API.AppLanguage): ContentValues? = null
            override fun novelIndex(aid: Int, lang: Wenku8API.AppLanguage): ContentValues? = null
            override fun novelContent(aid: Int, cid: Int, lang: Wenku8API.AppLanguage): ContentValues? = null
            override fun bookshelfListParams(lang: Wenku8API.AppLanguage): ContentValues = ContentValues().apply {
                put("action", "bookcase")
            }
            override fun bookshelfListAidParams(lang: Wenku8API.AppLanguage): ContentValues = ContentValues().apply {
                put("action", "bookcase")
                put("do", "list")
            }
        }
        val apiClient = WenkuApiClient(transport = transport, paramsFactory = paramsFactory)
        val fallbackRes = apiClient.fetchCloudBookshelf(Wenku8API.AppLanguage.SC)
        assertTrue(fallbackRes is WenkuApiResult.Success)
        assertEquals(listOf(101, 202), (fallbackRes as WenkuApiResult.Success).data.map { it.aid })

        // Part B: Verify Room MIGRATION_1_2 upgrades a physical SQLite v1 DB built from 1.json cleanly
        val dbFile = File(context.cacheDir, "mig_1_2_${System.nanoTime()}.db")
        try {
            val schemaV1File = sequenceOf(
                File("schemas/org.mewx.wenku8.v2.data.local.db.Wenku8Database/1.json"),
                File("app-v2/schemas/org.mewx.wenku8.v2.data.local.db.Wenku8Database/1.json"),
            ).first { it.isFile }
            val dbJson = org.json.JSONObject(schemaV1File.readText(StandardCharsets.UTF_8))
                .getJSONObject("database")

            val rawV1 = SQLiteDatabase.openOrCreateDatabase(dbFile, null)
            val entities = dbJson.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entityObj = entities.getJSONObject(i)
                val tableName = entityObj.getString("tableName")
                rawV1.execSQL(entityObj.getString("createSql").replace("\${TABLE_NAME}", tableName))
                val indices = entityObj.optJSONArray("indices")
                if (indices != null) {
                    for (j in 0 until indices.length()) {
                        rawV1.execSQL(
                            indices.getJSONObject(j).getString("createSql")
                                .replace("\${TABLE_NAME}", tableName),
                        )
                    }
                }
            }
            val setupQueries = dbJson.optJSONArray("setupQueries")
            if (setupQueries != null) {
                for (i in 0 until setupQueries.length()) {
                    rawV1.execSQL(setupQueries.getString(i))
                }
            }
            rawV1.execSQL("INSERT INTO `novels` VALUES (888, 'SC', 1, 'sha', 100, '升级保留作品', '作者', '连载中', '电击', 1000, '2026-01-01', 1, '第一话', 0, 0, 0, 0, '', '', '', 1, '默认书架', 1, 0, 1, 100)")
            rawV1.version = 1
            rawV1.close()

            val directExecutor = java.util.concurrent.Executor { it.run() }
            val migratedDb = Room.databaseBuilder(context, Wenku8Database::class.java, dbFile.absolutePath)
                .openHelperFactory(FrameworkSQLiteOpenHelperFactory())
                .addMigrations(Wenku8Database.MIGRATION_1_2)
                .allowMainThreadQueries()
                .setQueryExecutor(directExecutor)
                .setTransactionExecutor(directExecutor)
                .build()
            try {
                assertEquals("升级保留作品", migratedDb.novelDao().getNovelByAid(888)?.title)
                migratedDb.cloudBookcaseLedgerDao().upsertEntry(
                    CloudBookcaseLedgerEntity("acct", 888, CloudBookcaseLedgerEntity.STATE_SYNCED, 200L),
                )
                assertEquals(
                    CloudBookcaseLedgerEntity.STATE_SYNCED,
                    migratedDb.cloudBookcaseLedgerDao().getEntry("acct", 888)?.syncState,
                )
            } finally {
                migratedDb.close()
            }
        } finally {
            dbFile.delete()
        }
    }

    @Test
    fun filterAndSortItems_recentReadIgnoresLastAccessedMs_andFiltersByTagAndCategory() = runTest {
        database.novelDao().upsertNovels(
            listOf(
                NovelEntity(
                    aid = 10,
                    title = "刚在详情页浏览但未读的作品",
                    author = "作者甲",
                    pressId = "电击文库",
                    bookStatus = "连载中",
                    tagsCsv = "奇幻 冒险",
                    inBookshelf = true,
                    shelfCategory = "待读清单",
                    shelfSortOrder = 1,
                    // Very high lastAccessedMs from opening NovelDetailScreen, but no chapter read!
                    lastAccessedMs = 999_999_999L,
                ),
                NovelEntity(
                    aid = 20,
                    title = "真正最近打开阅读器读过的作品",
                    author = "作者乙",
                    pressId = "GA文库",
                    bookStatus = "已完成",
                    tagsCsv = "恋爱 校园",
                    inBookshelf = true,
                    shelfCategory = NovelEntity.DEFAULT_SHELF_CATEGORY,
                    shelfSortOrder = 2,
                    lastAccessedMs = 100L,
                ),
            ),
        )
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 20,
                vid = 1,
                cid = 101,
                volumeTitle = "第一卷",
                chapterTitle = "第一章 春天的邂逅",
                progressPermille = 680,
                updatedAtMs = 500_000L,
            ),
        )

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = FakeBookshelfCatalogGateway(),
            novelTocSyncRepository = tocSyncRepository,
        )
        val snapshot = repository.observeBookshelfSnapshot().first { it.allShelfItems.size == 2 }

        val recentReadSorted = BookshelfRepository.filterAndSortItems(
            items = snapshot.allShelfItems,
            localQuery = "",
            activeFilter = BookshelfFilter.All,
            sortMode = BookshelfSortMode.RECENT_READ,
        )
        assertEquals(listOf(20, 10), recentReadSorted.map { it.aid })

        val tagFiltered = BookshelfRepository.filterAndSortItems(
            items = snapshot.allShelfItems,
            localQuery = "",
            activeFilter = BookshelfFilter.Tag("恋爱"),
            sortMode = BookshelfSortMode.MANUAL_ORDER,
        )
        assertEquals(listOf(20), tagFiltered.map { it.aid })

        val categoryFiltered = BookshelfRepository.filterAndSortItems(
            items = snapshot.allShelfItems,
            localQuery = "",
            activeFilter = BookshelfFilter.Category("待读清单"),
            sortMode = BookshelfSortMode.MANUAL_ORDER,
        )
        assertEquals(listOf(10), categoryFiltered.map { it.aid })
    }

    @Test
    fun smartUpdateShelfNovels_downloadsOnlyMissingChaptersForOfflineCachedNovels() = runTest {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 1749,
                title = "无职转生",
                inBookshelf = true,
            ),
        )
        // Seed chapter 501 as already cached offline; chapter 502 is new in TOC
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 501,
                aid = 1749,
                contentLang = "SC",
                contentText = "已缓存的第一章正文",
                byteLength = 27,
                imageUrlsJson = "[]",
                imageCount = 0,
                sha256Checksum = "abc",
                downloadedAtMs = 1_000L,
            ),
        )

        val gateway = FakeBookshelfCatalogGateway().apply {
            tocByAid[1749] = listOf(
                ParsedVolumeToc(
                    vid = 500,
                    volumeTitle = "第一卷 幼年期",
                    chapters = listOf(
                        ParsedChapterToc(cid = 501, chapterTitle = "第一话"),
                        ParsedChapterToc(cid = 502, chapterTitle = "第二话 新章节"),
                    ),
                ),
            )
            chapterTextByCid[502] = "新下载的第二话正文\n<!--image-->https://img.wenku8.com/image/1/1749/1749s.jpg<!--image-->"
        }

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
        )

        val summary = repository.smartUpdateShelfNovels(
            aids = listOf(1749),
            appLanguage = Wenku8API.AppLanguage.SC,
        )
        assertEquals(1, summary.updatedNovelCount)
        assertEquals(1, summary.newlyDownloadedChapterCount)
        assertEquals(listOf(502), gateway.fetchedChapterCids)
        val newlyCached = database.chapterContentDao().getChapterHeader(502)
        assertNotNull(newlyCached)
        assertEquals(1, newlyCached!!.imageCount)
    }

    @OptIn(ExperimentalTestApi::class)
    @Test
    fun bookshelfScreen_rendersCarousel_localSearch_viewModes_andMultiSelectToolbar() = runBlocking {
        database.novelDao().upsertNovels(
            listOf(
                NovelEntity(
                    aid = 5,
                    title = "狼与香辛料",
                    author = "支仓冻砂",
                    pressId = "电击文库",
                    bookStatus = "已完成",
                    tagsCsv = "奇幻 旅行 经商",
                    hasUnreadUpdate = true,
                    inBookshelf = true,
                    shelfSortOrder = 1,
                ),
                NovelEntity(
                    aid = 2906,
                    title = "败犬女主太多了！",
                    author = "雨森焚火",
                    pressId = "GAGAGA文库",
                    bookStatus = "连载中",
                    tagsCsv = "青春 恋爱 校园",
                    hasUnreadUpdate = false,
                    inBookshelf = true,
                    shelfSortOrder = 2,
                ),
            ),
        )
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 5,
                vid = 30,
                cid = 302,
                volumeTitle = "第三卷",
                chapterTitle = "第二话 真实与冒牌货",
                progressPermille = 680,
                updatedAtMs = 1_700_000_000_000L,
            ),
        )
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 302,
                aid = 5,
                contentLang = "SC",
                contentText = "离线正文内容",
                byteLength = 18,
                imageUrlsJson = "[]",
                imageCount = 0,
                sha256Checksum = "hash302",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = FakeBookshelfCatalogGateway(),
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )

        var activeViewMode by mutableStateOf(BookshelfViewMode.GRID)
        var activeSortMode by mutableStateOf(BookshelfSortMode.MANUAL_ORDER)
        var openedOnlineSearchCount = 0

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                BookshelfScreen(
                    bookshelfRepository = repository,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    viewMode = activeViewMode,
                    onViewModeSelected = { activeViewMode = it },
                    sortMode = activeSortMode,
                    onSortModeSelected = { activeSortMode = it },
                    onOpenSearch = { openedOnlineSearchCount++ },
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Continue Reading carousel, flat reading progress, and 新章节 badge render cleanly
        composeTestRule.onNodeWithTag(BookshelfTestTags.CONTINUE_READING_CAROUSEL).assertIsDisplayed()
        composeTestRule.onNodeWithText("68%").assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.NEW_CHAPTER_BADGE_PREFIX + 5).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.FLAT_READING_PROGRESS_PREFIX + 5).assertExists()
        composeTestRule.onNodeWithTag(BookshelfTestTags.ACTIVE_WAVY_PROGRESS_PREFIX + 5).assertDoesNotExist()

        // 1b. Long-pressing top-right Cloud Sync button shows its tooltip hint
        composeTestRule.onNodeWithTag(BookshelfTestTags.CLOUD_SYNC_BUTTON)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("云端同步").assertIsDisplayed()

        // 2. Online search button triggers UnifiedSearchScreen callback
        composeTestRule.onNodeWithTag(BookshelfTestTags.ONLINE_SEARCH_TRIGGER).performClick()
        assertEquals(1, openedOnlineSearchCount)

        // 3. Instant local shelf search filters down to matching book
        composeTestRule.onNodeWithTag(BookshelfTestTags.LOCAL_SEARCH_INPUT).performTextInput("败犬")
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 2906).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5).assertDoesNotExist()
        composeTestRule.onNodeWithTag(BookshelfTestTags.CLEAR_LOCAL_SEARCH_BUTTON).performClick()
        composeTestRule.waitForIdle()

        // 4. Switch across DETAIL_LIST and SPINE_3D view modes
        composeTestRule.onNodeWithTag(
            BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + BookshelfViewMode.DETAIL_LIST.name,
        ).performClick()
        composeTestRule.waitForIdle()
        assertEquals(BookshelfViewMode.DETAIL_LIST, activeViewMode)
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5).assertIsDisplayed()

        composeTestRule.onNodeWithTag(
            BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + BookshelfViewMode.SPINE_3D.name,
        ).performClick()
        composeTestRule.waitForIdle()
        assertEquals(BookshelfViewMode.SPINE_3D, activeViewMode)
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5).assertIsDisplayed()

        // 5. Long-press multi-select summons HorizontalFloatingToolbar; long-pressing each small
        // icon button displays a descriptive tooltip without accidentally firing onClick!
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.MULTI_SELECT_TOOLBAR).assertIsDisplayed()

        listOf(
            BookshelfTestTags.BATCH_SELECT_ALL to "全选",
            BookshelfTestTags.BATCH_PIN_TOP to "置顶",
            BookshelfTestTags.BATCH_CLEAR_CACHE to "清理缓存",
            BookshelfTestTags.BATCH_REMOVE to "移出书架",
            BookshelfTestTags.BATCH_DONE to "完成",
        ).forEach { (tag, expectedTooltip) ->
            composeTestRule.onNodeWithTag(tag).performTouchInput { longClick() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(expectedTooltip).assertIsDisplayed()
        }
        composeTestRule.onNodeWithTag(BookshelfTestTags.BATCH_SMART_UPDATE)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        assertTrue(
            composeTestRule.onAllNodes(androidx.compose.ui.test.hasText("智能更新"))
                .fetchSemanticsNodes().size >= 2,
        )
        // Multi-select toolbar remains open & remove confirmation dialog did NOT trigger on long-press
        composeTestRule.onNodeWithTag(BookshelfTestTags.MULTI_SELECT_TOOLBAR).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.CONFIRM_REMOVE_BUTTON).assertDoesNotExist()
        assertFalse(database.chapterContentDao().getCachedChapterIdsForNovel(5).isEmpty())

        composeTestRule.onNodeWithTag(BookshelfTestTags.BATCH_CLEAR_CACHE).performClick()
        composeTestRule.waitForIdle()
        assertTrue(database.chapterContentDao().getCachedChapterIdsForNovel(5).isEmpty())

        // Long-press aid=2906 and remove from bookshelf via confirmation dialog
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 2906)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.BATCH_REMOVE).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.CONFIRM_REMOVE_BUTTON).performClick()
        composeTestRule.waitForIdle()

        val remainingAids = database.novelDao().getBookshelfNovelsSnapshot().map { it.aid }
        assertEquals(listOf(5), remainingAids)
    }

    @Test
    fun coldStartOfflineDeleteBeforeSessionInitAndShortInfoTagsParseCleanly() = runBlocking {
        // 1. Verify `do=bookinfo` XML with `<data name="Tags">` populates `tagsCsv`.
        val parsedShortInfo = WenkuXmlParsers.parseNovelShortInfoUpdate(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <metadata>
                <data name="Title" aid="1305"><![CDATA[绝对双刃absolute duo]]></data>
                <data name="Author" value="柊★巧"/>
                <data name="BookStatus" value="连载中"/>
                <data name="LastUpdate" value="2020-04-01"/>
                <data name="Tags" value="校园 异能 战斗"/>
                <data name="IntroPreview"><![CDATA[　　「焰牙」——测试简介]]></data>
            </metadata>
            """.trimIndent(),
        )
        assertNotNull(parsedShortInfo)
        assertEquals("校园 异能 战斗", parsedShortInfo?.tagsCsv)

        // 2. Sign in via email ("alice@example.test" -> canonical server uname "alice") to write saves/cert.wk8 + saves/account_uname.wk8.
        val tempRoot = Files.createTempDirectory("cold_start_ledger_test").toFile()
        try {
            val authTransport = FakeAccountSwitchTransport().apply { activeServerUsername = "alice" }
            val authGateway = FakeAccountSwitchAuthGateway(authTransport)
            val initialSession = UserSessionRepository(
                transport = authTransport,
                assetRootsProvider = { listOf(tempRoot) },
                authGateway = authGateway,
            )
            initialSession.loginWithCredentials("alice@example.test", "secret12")
            assertEquals("alice", initialSession.activeAccountKeyOrNull())

            // Simulate a brand-new cold process start BEFORE `initializeSessionOnStartup()` executes on IO.
            val coldStartSession = UserSessionRepository(
                transport = authTransport,
                assetRootsProvider = { listOf(tempRoot) },
                authGateway = authGateway,
            )
            assertEquals("alice", coldStartSession.activeAccountKeyOrNull())
            assertEquals("alice@example.test", coldStartSession.rawSavedIdentifierKeyOrNull())

            val gateway = FakeBookshelfCatalogGateway().apply {
                removeResponsesByAid[1305] = WenkuApiResult.NetworkError("offline_cold_start")
            }
            val coldStartRepo = BookshelfRepository(
                database = database,
                catalogGateway = gateway,
                novelTocSyncRepository = NovelTocSyncRepository(database),
                userSessionRepository = coldStartSession,
            )
            database.novelDao().upsertNovel(
                NovelEntity(
                    aid = 1305,
                    title = "绝对双刃",
                    inBookshelf = true,
                    cloudSynced = true,
                ),
            )
            database.cloudBookcaseLedgerDao().upsertEntry(
                CloudBookcaseLedgerEntity(
                    accountKey = "alice",
                    aid = 1305,
                    syncState = CloudBookcaseLedgerEntity.STATE_SYNCED,
                    updatedAtMs = 100L,
                ),
            )

            // User immediately deletes novel on cold start while offline -> tombstone MUST be persisted!
            coldStartRepo.removeNovelsFromBookshelf(listOf(1305))
            assertEquals(
                CloudBookcaseLedgerEntity.STATE_PENDING_DELETE,
                database.cloudBookcaseLedgerDao().getEntry("alice", 1305)?.syncState,
            )
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    private class FakeAccountSwitchTransport : WenkuTransport {
        override val isEndpointConfigured: Boolean = true
        var activeServerUsername: String = "alice"

        override suspend fun postForm(
            endpointUrl: String,
            formParams: ContentValues,
        ): ByteArray {
            val action = formParams.getAsString("action")
            return when (action) {
                "login", "logout" -> "1".toByteArray(StandardCharsets.UTF_8)
                "userinfo" -> """
                    <?xml version="1.0" encoding="utf-8"?>
                    <metadata>
                        <item name="uid">100</item>
                        <item name="uname"><![CDATA[$activeServerUsername]]></item>
                        <item name="nickname"><![CDATA[$activeServerUsername]]></item>
                        <item name="score">10</item>
                        <item name="experience">20</item>
                        <item name="rank">新手</item>
                    </metadata>
                """.trimIndent().toByteArray(StandardCharsets.UTF_8)
                else -> "1".toByteArray(StandardCharsets.UTF_8)
            }
        }

        override suspend fun getBytes(url: String): ByteArray? = null
        override fun clearSessionCookie() = Unit
    }

    private class FakeAccountSwitchAuthGateway(
        private val transport: FakeAccountSwitchTransport,
    ) : AccountAuthGateway {
        override val isAuthConfigured: Boolean = true
        override val baseUrlOrEmpty: String = "https://relay.example.test/android.php"
        override val registerUrlOrEmpty: String = ""

        override fun loginParams(identifier: String, password: String): ContentValues =
            ContentValues().apply { put("action", "login") }

        override fun logoutParams(): ContentValues =
            ContentValues().apply { put("action", "logout") }

        override fun userInfoParams(): ContentValues =
            ContentValues().apply { put("action", "userinfo") }

        override fun userSignParams(): ContentValues =
            ContentValues().apply { put("action", "sign") }

        override fun avatarUrlForUid(uid: Int): String = ""

        override fun mapServerErrorCode(rawCode: Int): Wenku8Error.ErrorCode =
            if (rawCode == 1) Wenku8Error.ErrorCode.SYSTEM_1_SUCCEEDED else Wenku8Error.ErrorCode.ERROR_DEFAULT

        override fun decodeCertBlob(rawCertContent: String): StoredAccountCredentials? {
            val parts = rawCertContent.split(":", limit = 2)
            return if (parts.size == 2) StoredAccountCredentials(parts[0], parts[1]) else null
        }

        override fun encodeCertBlob(identifier: String, password: String): String = "$identifier:$password"
        override fun syncInMemoryCredentials(identifier: String, password: String) = Unit
        override fun clearInMemorySession() {
            transport.clearSessionCookie()
        }
    }

    private class FakeBookshelfCatalogGateway : WenkuCatalogGateway {
        override val isApiConfigured: Boolean = true

        var cloudBookshelfEntries: List<ParsedCloudBookshelfEntry> = emptyList()
        val addResponsesByAid = mutableMapOf<Int, WenkuApiResult<Unit>>()
        val removeResponsesByAid = mutableMapOf<Int, WenkuApiResult<Unit>>()
        val pushedAidsToCloud = mutableListOf<Int>()
        val removedAidsFromCloud = mutableListOf<Int>()
        val shortInfoTagsByAid = mutableMapOf<Int, String>()
        val failTocForAids = mutableSetOf<Int>()
        val tocByAid = mutableMapOf<Int, List<ParsedVolumeToc>>()
        val chapterTextByCid = mutableMapOf<Int, String>()
        val fetchedChapterCids = mutableListOf<Int>()

        override fun resolveCoverUrl(aid: Int): String = ""

        override suspend fun fetchNovelListPage(
            sortedBy: Wenku8API.NovelSortedBy,
            pageNumber: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListPage> = WenkuApiResult.ApiNotConfigured

        override suspend fun searchNovelsByField(
            query: String,
            searchField: OnlineSearchField,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<Int>> = WenkuApiResult.ApiNotConfigured

        override suspend fun fetchNovelShortInfo(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelListItem> {
            val tags = shortInfoTagsByAid[aid] ?: return WenkuApiResult.ApiNotConfigured
            return WenkuApiResult.Success(
                ParsedNovelListItem(
                    aid = aid,
                    title = "作品 $aid",
                    tagsCsv = tags,
                ),
            )
        }

        override suspend fun fetchNovelFullMeta(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelFullMeta> {
            val cloudEntry = cloudBookshelfEntries.firstOrNull { it.aid == aid }
            return WenkuApiResult.Success(
                ParsedNovelFullMeta(
                    aid = aid,
                    title = cloudEntry?.title ?: "作品 $aid",
                    author = "测试作者",
                    pressId = "电击文库",
                    bookStatus = "连载中",
                    lastUpdate = cloudEntry?.lastUpdateDate ?: "2026-02-20",
                    latestSectionCid = cloudEntry?.latestChapterCid ?: 1,
                    latestSectionName = cloudEntry?.latestChapterName ?: "第一话",
                ),
            )
        }

        override suspend fun fetchNovelFullIntro(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<String> = WenkuApiResult.Success("测试作品简介 $aid")

        override suspend fun fetchNovelVolumeToc(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<ParsedVolumeToc>> {
            if (aid in failTocForAids) {
                return WenkuApiResult.NetworkError("simulated toc failure for $aid")
            }
            val volumes = tocByAid[aid] ?: listOf(
                ParsedVolumeToc(
                    vid = aid * 10,
                    volumeTitle = "第一卷",
                    chapters = listOf(ParsedChapterToc(cid = aid * 10 + 1, chapterTitle = "第一话")),
                ),
            )
            return WenkuApiResult.Success(volumes)
        }

        override suspend fun fetchChapterContent(
            aid: Int,
            cid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<String> {
            fetchedChapterCids += cid
            val text = chapterTextByCid[cid] ?: "正文内容 $cid"
            return WenkuApiResult.Success(text)
        }

        override suspend fun fetchCloudBookshelf(
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> = WenkuApiResult.Success(cloudBookshelfEntries)

        override suspend fun addToCloudBookshelf(aid: Int): WenkuApiResult<Unit> {
            pushedAidsToCloud += aid
            return addResponsesByAid[aid] ?: WenkuApiResult.Success(Unit)
        }

        override suspend fun removeFromCloudBookshelf(aid: Int): WenkuApiResult<Unit> {
            removedAidsFromCloud += aid
            return removeResponsesByAid[aid] ?: WenkuApiResult.Success(Unit)
        }
    }
}
