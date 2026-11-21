package org.mewx.wenku8.ui.bookshelf

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.room.Room
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
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
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.data.local.db.entity.CloudBookcaseLedgerEntity
import org.mewx.wenku8.data.local.db.entity.NovelEntity
import org.mewx.wenku8.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.data.remote.api.OnlineSearchField
import org.mewx.wenku8.data.remote.api.WenkuApiClient
import org.mewx.wenku8.data.remote.api.WenkuApiResult
import org.mewx.wenku8.data.remote.api.WenkuCatalogGateway
import org.mewx.wenku8.data.remote.api.WenkuParamsFactory
import org.mewx.wenku8.data.remote.parser.ParsedCloudBookshelfEntry
import org.mewx.wenku8.data.remote.parser.ParsedNovelFullMeta
import org.mewx.wenku8.data.remote.parser.ParsedNovelListItem
import org.mewx.wenku8.data.remote.parser.ParsedNovelListPage
import org.mewx.wenku8.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.data.remote.transport.WenkuTransport
import org.mewx.wenku8.data.repository.AccountAuthGateway
import org.mewx.wenku8.data.repository.BookshelfFilter
import org.mewx.wenku8.data.repository.BookshelfRepository
import org.mewx.wenku8.data.repository.BookshelfSortMode
import org.mewx.wenku8.data.repository.BookshelfViewMode
import org.mewx.wenku8.data.repository.CloudBookshelfSyncOutcome
import org.mewx.wenku8.data.repository.LoginOutcome
import org.mewx.wenku8.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.data.repository.ParsedChapterToc
import org.mewx.wenku8.data.repository.ParsedVolumeToc
import org.mewx.wenku8.data.repository.StoredAccountCredentials
import org.mewx.wenku8.data.repository.UserSessionRepository
import org.mewx.wenku8.ui.theme.Wenku8ExpressiveTheme
import org.mewx.wenku8.ui.theme.Wenku8ThemeMode
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
            override suspend fun postSealedAction(
                rawActionQuery: String,
                isIdempotentReadOnly: Boolean,
            ): org.mewx.wenku8.data.remote.transport.RelayPostResult {
                val payload = if (rawActionQuery.contains("do=list")) {
                    """<?xml version="1.0" encoding="utf-8"?><metadata><package aid="101"/><package aid="202"/></metadata>"""
                        .toByteArray(StandardCharsets.UTF_8)
                } else {
                    // Malformed rich XML (unescaped raw '<' / broken tag)
                    "<package><item aid=\"101\"><name>Broken <Unclosed".toByteArray(StandardCharsets.UTF_8)
                }
                return org.mewx.wenku8.data.remote.transport.RelayPostResult.Success(payload)
            }
            override suspend fun getBytes(url: String): ByteArray? = null
            override fun clearSessionCookie() = Unit
        }
        val paramsFactory = object : WenkuParamsFactory {
            override fun baseUrlOrUnknown(): String = "https://relay.example.test/v2/api"
            override fun resolveCoverUrl(aid: Int): String = ""
            override fun novelListWithInfoQuery(sortedBy: Wenku8API.NovelSortedBy, page: Int, lang: Wenku8API.AppLanguage): String? = null
            override fun searchByNovelNameQuery(query: String, lang: Wenku8API.AppLanguage): String? = null
            override fun searchByAuthorNameQuery(query: String, lang: Wenku8API.AppLanguage): String? = null
            override fun novelShortInfoQuery(aid: Int, lang: Wenku8API.AppLanguage): String? = null
            override fun novelFullMetaQuery(aid: Int, lang: Wenku8API.AppLanguage): String? = null
            override fun novelFullIntroQuery(aid: Int, lang: Wenku8API.AppLanguage): String? = null
            override fun novelIndexQuery(aid: Int, lang: Wenku8API.AppLanguage): String? = null
            override fun novelContentQuery(aid: Int, cid: Int, lang: Wenku8API.AppLanguage): String? = null
            override fun bookshelfListQuery(lang: Wenku8API.AppLanguage): String = "action=bookcase"
            override fun bookshelfListAidQuery(lang: Wenku8API.AppLanguage): String = "action=bookcase&do=list"
        }
        val apiClient = WenkuApiClient(transport = transport, paramsFactory = paramsFactory)
        val fallbackRes = apiClient.fetchCloudBookshelf(Wenku8API.AppLanguage.SC)
        assertTrue(fallbackRes is WenkuApiResult.Success)
        assertEquals(listOf(101, 202), (fallbackRes as WenkuApiResult.Success).data.map { it.aid })

        // Part B: Verify Room MIGRATION_1_2 upgrades a physical SQLite v1 DB built from 1.json cleanly
        val dbFile = File(context.cacheDir, "mig_1_2_${System.nanoTime()}.db")
        try {
            val schemaV1File = sequenceOf(
                File("schemas/org.mewx.wenku8.data.local.db.Wenku8Database/1.json"),
                File("app-v2/schemas/org.mewx.wenku8.data.local.db.Wenku8Database/1.json"),
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

        // 1. Continue Reading carousel, flat reading progress + percentage labels, and 新章节 badge render cleanly
        composeTestRule.onNodeWithTag(BookshelfTestTags.CONTINUE_READING_CAROUSEL).assertIsDisplayed()
        composeTestRule.onAllNodesWithText("68%")[0].assertIsDisplayed()
        composeTestRule.onNodeWithTag(
            BookshelfTestTags.READING_PROGRESS_PERCENT_PREFIX + 5,
            useUnmergedTree = true,
        ).assertTextEquals("68%")
        composeTestRule.onNodeWithTag(
            BookshelfTestTags.READING_PROGRESS_PERCENT_PREFIX + 2906,
            useUnmergedTree = true,
        ).assertTextEquals("0%")
        composeTestRule.onNodeWithTag(BookshelfTestTags.NEW_CHAPTER_BADGE_PREFIX + 5).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.FLAT_READING_PROGRESS_PREFIX + 5).assertExists()
        composeTestRule.onNodeWithTag(BookshelfTestTags.ACTIVE_WAVY_PROGRESS_PREFIX + 5).assertDoesNotExist()

        // 1b. Folded top-right `云端同步 | ▾` split button displays Cloud Sync label + folded menu options
        composeTestRule.onNodeWithTag(BookshelfTestTags.CLOUD_SYNC_BUTTON).assertIsDisplayed()
        composeTestRule.onNodeWithText("云端同步").assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SMART_UPDATE_TRAILING_BUTTON).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SMART_UPDATE_MENU_ITEM).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.FORCE_CLOUD_SYNC_MENU_ITEM).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SMART_UPDATE_TRAILING_BUTTON).performClick()
        composeTestRule.waitForIdle()

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

        // 4. Switch across DETAIL_LIST and SPINE_3D view modes (3D view is never loaded in GRID/DETAIL_LIST)
        composeTestRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_CONTAINER).assertDoesNotExist()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE).assertDoesNotExist()

        composeTestRule.onNodeWithTag(
            BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + BookshelfViewMode.DETAIL_LIST.name,
        ).performClick()
        composeTestRule.waitForIdle()
        assertEquals(BookshelfViewMode.DETAIL_LIST, activeViewMode)
        composeTestRule.onNodeWithTag(BookshelfTestTags.CONTINUE_READING_CAROUSEL).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_CONTAINER).assertDoesNotExist()
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5).assertIsDisplayed()
        composeTestRule.onNodeWithTag(
            org.mewx.wenku8.ui.discovery.DiscoveryTestTags.BOOK_STATUS_BADGE_PREFIX + 5,
            useUnmergedTree = true,
        ).assertExists()

        composeTestRule.onNodeWithTag(
            BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + BookshelfViewMode.SPINE_3D.name,
        ).performClick()
        composeTestRule.waitForIdle()
        assertEquals(BookshelfViewMode.SPINE_3D, activeViewMode)
        composeTestRule.onNodeWithTag(BookshelfTestTags.CONTINUE_READING_CAROUSEL).assertDoesNotExist()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_CONTAINER).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_CONTINUE_BADGE).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5).assertIsDisplayed()

        // 5. Long-press multi-select summons HorizontalFloatingToolbar; long-pressing each small
        // icon button displays a descriptive tooltip without accidentally firing onClick!
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 5)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.MULTI_SELECT_TOOLBAR).assertIsDisplayed()

        listOf(
            BookshelfTestTags.BATCH_SELECT_ALL to "全选",
            BookshelfTestTags.BATCH_SMART_UPDATE to "智能更新",
            BookshelfTestTags.BATCH_PIN_TOP to "置顶",
            BookshelfTestTags.BATCH_CLEAR_CACHE to "清理缓存",
            BookshelfTestTags.BATCH_REMOVE to "移出书架",
            BookshelfTestTags.BATCH_DONE to "完成",
        ).forEach { (tag, expectedTooltip) ->
            composeTestRule.onNodeWithTag(tag).performTouchInput { longClick() }
            composeTestRule.waitForIdle()
            composeTestRule.onNodeWithText(expectedTooltip).assertIsDisplayed()
        }
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

        // 6. Switching away from SPINE_3D back to GRID immediately disposes the 3D view tree
        // and restores the Continue Reading carousel.
        composeTestRule.onNodeWithTag(
            BookshelfTestTags.VIEW_MODE_CHIP_PREFIX + BookshelfViewMode.GRID.name,
        ).performClick()
        composeTestRule.waitForIdle()
        assertEquals(BookshelfViewMode.GRID, activeViewMode)
        composeTestRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_CONTAINER).assertDoesNotExist()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SPINE_3D_STAGE).assertDoesNotExist()
        composeTestRule.onNodeWithTag(BookshelfTestTags.CONTINUE_READING_CAROUSEL).assertIsDisplayed()
        Unit
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

    @Test
    fun continueReadingCarousel_multipleBooksExpandFromLeftWithFlushCovers() = runBlocking {
        database.novelDao().upsertNovels(
            listOf(
                NovelEntity(
                    aid = 101,
                    title = "第一本最近阅读",
                    author = "作者A",
                    inBookshelf = true,
                    shelfSortOrder = 1,
                ),
                NovelEntity(
                    aid = 102,
                    title = "第二本未完全展开",
                    author = "作者B",
                    inBookshelf = true,
                    shelfSortOrder = 2,
                ),
                NovelEntity(
                    aid = 103,
                    title = "第三本折叠封面",
                    author = "作者C",
                    inBookshelf = true,
                    shelfSortOrder = 3,
                ),
            ),
        )
        listOf(
            Triple(101, 500, 3_000L),
            Triple(102, 350, 2_000L),
            Triple(103, 200, 1_000L),
        ).forEach { (aid, permille, updatedAt) ->
            database.readingProgressDao().upsertProgress(
                ReadingProgressEntity(
                    aid = aid,
                    vid = aid * 10,
                    cid = aid * 10 + 1,
                    volumeTitle = "第一卷",
                    chapterTitle = "第一话",
                    progressPermille = permille,
                    updatedAtMs = updatedAt,
                ),
            )
        }

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = FakeBookshelfCatalogGateway(),
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                BookshelfScreen(
                    bookshelfRepository = repository,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    viewMode = BookshelfViewMode.GRID,
                    onViewModeSelected = {},
                    sortMode = BookshelfSortMode.RECENT_READ,
                    onSortModeSelected = {},
                    onOpenSearch = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        val card1Bounds = composeTestRule
            .onNodeWithTag(BookshelfTestTags.CONTINUE_READING_ITEM_PREFIX + 101)
            .fetchSemanticsNode()
            .boundsInRoot
        val cover1Bounds = composeTestRule
            .onNodeWithTag(
                BookshelfTestTags.CONTINUE_READING_COVER_PREFIX + 101,
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot

        // 1. Focal book #1 cover is flush edge-to-edge (left, top, bottom) with the card (`贴边显示`)!
        assertTrue(
            "Expanded card cover must sit flush to card left/top/bottom edges (card=$card1Bounds, cover=$cover1Bounds)",
            kotlin.math.abs(cover1Bounds.left - card1Bounds.left) <= 1.5f &&
                kotlin.math.abs(cover1Bounds.top - card1Bounds.top) <= 1.5f &&
                kotlin.math.abs(cover1Bounds.bottom - card1Bounds.bottom) <= 1.5f,
        )

        val carouselBounds = composeTestRule
            .onNodeWithTag(BookshelfTestTags.CONTINUE_READING_CAROUSEL)
            .fetchSemanticsNode()
            .boundsInRoot

        // 2. Partially-collapsed book #2 (`102`) sits in the visible slot right after `card1 + 12dp`,
        // and its clipped cover (`cover2Bounds`) starts right at that slot's left edge and fills the
        // entire collapsed slot top-to-bottom (proving left-anchored expansion + flush cover, whereas
        // centered masking previously clipped `cover2Bounds` out completely)!
        val cover2Bounds = composeTestRule
            .onNodeWithTag(
                BookshelfTestTags.CONTINUE_READING_COVER_PREFIX + 102,
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot

        val expectedSlotLeft = card1Bounds.right + 12f * composeTestRule.density.density
        assertTrue(
            "Collapsed second book slot should be narrower than focal card (${cover2Bounds.width} vs ${card1Bounds.width})",
            cover2Bounds.width > 40f && cover2Bounds.width < card1Bounds.width - 40f,
        )
        assertTrue(
            "Partially-collapsed book card must expand from the left prioritizing its flush cover (expectedSlotLeft=$expectedSlotLeft, carousel=$carouselBounds, cover2=$cover2Bounds)",
            kotlin.math.abs(cover2Bounds.left - expectedSlotLeft) <= 2f &&
                kotlin.math.abs(cover2Bounds.right - carouselBounds.right) <= 2f &&
                kotlin.math.abs(cover2Bounds.top - card1Bounds.top) <= 1.5f &&
                kotlin.math.abs(cover2Bounds.bottom - card1Bounds.bottom) <= 1.5f,
        )
    }

    @Test
    fun continueReadingCarousel_singleBookSpansFullRowWidth() = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 201,
                title = "唯一在读轻小说",
                author = "作者Solo",
                inBookshelf = true,
                shelfSortOrder = 1,
            ),
        )
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 201,
                vid = 2010,
                cid = 2011,
                volumeTitle = "第一卷",
                chapterTitle = "序章",
                progressPermille = 420,
                updatedAtMs = 5_000L,
            ),
        )

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = FakeBookshelfCatalogGateway(),
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.CLASSIC_BLUE) {
                BookshelfScreen(
                    bookshelfRepository = repository,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    viewMode = BookshelfViewMode.GRID,
                    onViewModeSelected = {},
                    sortMode = BookshelfSortMode.RECENT_READ,
                    onSortModeSelected = {},
                    onOpenSearch = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        val carouselBounds = composeTestRule
            .onNodeWithTag(BookshelfTestTags.CONTINUE_READING_CAROUSEL)
            .fetchSemanticsNode()
            .boundsInRoot
        val cardBounds = composeTestRule
            .onNodeWithTag(BookshelfTestTags.CONTINUE_READING_ITEM_PREFIX + 201)
            .fetchSemanticsNode()
            .boundsInRoot
        val coverBounds = composeTestRule
            .onNodeWithTag(
                BookshelfTestTags.CONTINUE_READING_COVER_PREFIX + 201,
                useUnmergedTree = true,
            )
            .fetchSemanticsNode()
            .boundsInRoot

        assertTrue(
            "Single-book Continue Reading card must span 100% of the carousel row width (carousel=$carouselBounds, card=$cardBounds)",
            kotlin.math.abs(cardBounds.left - carouselBounds.left) <= 1.5f &&
                kotlin.math.abs(cardBounds.right - carouselBounds.right) <= 1.5f &&
                kotlin.math.abs(cardBounds.width - carouselBounds.width) <= 1.5f,
        )
        assertTrue(
            "Single-book flush cover must align with card left/top/bottom edges (card=$cardBounds, cover=$coverBounds)",
            kotlin.math.abs(coverBounds.left - cardBounds.left) <= 1.5f &&
                kotlin.math.abs(coverBounds.top - cardBounds.top) <= 1.5f &&
                kotlin.math.abs(coverBounds.bottom - cardBounds.bottom) <= 1.5f,
        )
    }

    @Test
    fun cloudBookcaseSync_combinatorialConflictMatrix_handlesDeleteReAddConcurrentAddAndRelogin() = runTest {
        val tempRoot = File(context.cacheDir, "combinatorial_matrix_${System.nanoTime()}").apply { mkdirs() }
        try {
            val authTransport = FakeAccountSwitchTransport().apply { activeServerUsername = "alice" }
            val authGateway = FakeAccountSwitchAuthGateway(authTransport)
            val sessionRepo = UserSessionRepository(
                transport = authTransport,
                assetRootsProvider = { listOf(tempRoot) },
                authGateway = authGateway,
            )
            assertTrue(sessionRepo.loginWithCredentials("alice", "pw123") is LoginOutcome.Success)

            val gateway = FakeBookshelfCatalogGateway().apply {
                cloudBookshelfEntries = listOf(
                    ParsedCloudBookshelfEntry(aid = 8001, lastUpdateDate = "2026-03-01", title = "在线双删后云端重加", latestChapterCid = 11),
                    ParsedCloudBookshelfEntry(aid = 8002, lastUpdateDate = "2026-03-01", title = "离线本地删_云端亦删_再云端重加", latestChapterCid = 21),
                    ParsedCloudBookshelfEntry(aid = 8003, lastUpdateDate = "2026-03-01", title = "离线未同步本地删_优先冲刷一次", latestChapterCid = 31),
                    ParsedCloudBookshelfEntry(aid = 8004, lastUpdateDate = "2026-03-01", title = "云端删除后再云端重加", latestChapterCid = 41),
                    ParsedCloudBookshelfEntry(aid = 8005, lastUpdateDate = "2026-03-01", title = "离线本地删后又本地加回", latestChapterCid = 51),
                )
            }
            val repository = BookshelfRepository(
                database = database,
                catalogGateway = gateway,
                novelTocSyncRepository = tocSyncRepository,
                userSessionRepository = sessionRepo,
            )

            // Baseline sync: 8001..8005 are all pulled & recorded as STATE_SYNCED under "alice".
            val baseline = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success
            assertEquals(5, baseline.pulledFromCloudCount)
            assertEquals(setOf(8001, 8002, 8003, 8004, 8005), database.novelDao().getBookshelfNovelIds().toSet())

            // Seed reading progress on 8001 & 8004 to verify progress survives delete -> re-add cycles!
            database.readingProgressDao().upsertProgress(
                ReadingProgressEntity(
                    aid = 8001,
                    vid = 80010,
                    cid = 80011,
                    volumeTitle = "第一卷",
                    chapterTitle = "第一话",
                    progressPermille = 640,
                    updatedAtMs = 50_000L,
                ),
            )
            database.readingProgressDao().upsertProgress(
                ReadingProgressEntity(
                    aid = 8004,
                    vid = 80040,
                    cid = 80041,
                    volumeTitle = "第一卷",
                    chapterTitle = "第一话",
                    progressPermille = 810,
                    updatedAtMs = 60_000L,
                ),
            )

            // -------------------------------------------------------------------------------------
            // Prepare Pass 1 mutations across all combinations:
            // - 8001 (Case A1): Online local delete (`do=del` succeeds immediately -> tombstone cleared)
            //   + Cloud also deleted, then Cloud re-adds BEFORE Pass 1!
            // - 8002 (Case A2): Offline local delete (`STATE_PENDING_DELETE`) + Cloud ALSO deleted
            //   (`do=del` returns `7 = SERVER_CODE_NOT_IN_BOOKSHELF` on Pass 1 -> tombstone cleared).
            // - 8003 (Case B): Offline un-synced local delete (`STATE_PENDING_DELETE`) while Cloud
            //   deleted & re-added BEFORE Pass 1 (wire-identical to untouched cloud entry -> un-synced
            //   local delete MUST win on Pass 1 and NEVER resurrect!).
            // - 8004 (Case C): Cloud deletes 8004 -> Pass 1 removes locally.
            // - 8005 (Case D): Offline local delete -> Offline local re-add (`STATE_PENDING_ADD`,
            //   category = "重温收藏") while Cloud still holds 8005 -> zero `do=del`, reconciles to SYNCED!
            // - 8006 (Case E1): Offline local add (`STATE_PENDING_ADD`, category = "新番原著") AND
            //   Cloud independently added 8006 -> reconciles to SYNCED without redundant `do=add`!
            // - 8007 (Case E2): Offline local add (`STATE_PENDING_ADD`), added on Cloud right after
            //   `action=bookcase` snapshot so `do=add` returns `5 = SERVER_CODE_ALREADY_IN_BOOKSHELF`!
            // - Session expiry (Case F): `fetchCloudBookshelf` returns `4 = SERVER_CODE_NOT_LOGGED_IN`
            //   once -> auto re-authenticates from `cert.wk8` transparently!
            // -------------------------------------------------------------------------------------

            // 8001: online delete succeeds immediately
            gateway.removeResponsesByAid[8001] = WenkuApiResult.Success(Unit)
            repository.removeNovelsFromBookshelf(listOf(8001))
            assertNull(database.cloudBookcaseLedgerDao().getEntry("alice", 8001))

            // 8002 & 8003 & 8005: user goes offline and deletes locally
            gateway.removeResponsesByAid[8002] = WenkuApiResult.NetworkError("offline")
            gateway.removeResponsesByAid[8003] = WenkuApiResult.NetworkError("offline")
            gateway.removeResponsesByAid[8005] = WenkuApiResult.NetworkError("offline")
            repository.removeNovelsFromBookshelf(listOf(8002, 8003, 8005))

            // 8005: user changes mind offline and re-adds 8005 with custom category
            gateway.addResponsesByAid[8005] = WenkuApiResult.NetworkError("offline")
            repository.addNovelToBookshelf(
                NovelEntity(aid = 8005, title = "离线本地删后又本地加回", shelfCategory = "重温收藏"),
            )
            assertEquals(
                CloudBookcaseLedgerEntity.STATE_PENDING_ADD,
                database.cloudBookcaseLedgerDao().getEntry("alice", 8005)?.syncState,
            )

            // 8006 & 8007: user adds brand-new novels while offline
            gateway.addResponsesByAid[8006] = WenkuApiResult.NetworkError("offline")
            gateway.addResponsesByAid[8007] = WenkuApiResult.NetworkError("offline")
            repository.addNovelToBookshelf(
                NovelEntity(aid = 8006, title = "双端同时新增作品", shelfCategory = "新番原著"),
            )
            repository.addNovelToBookshelf(
                NovelEntity(aid = 8007, title = "并发已存在返回码5作品", shelfCategory = "待看"),
            )

            // Come back online for Pass 1:
            gateway.removedAidsFromCloud.clear()
            gateway.pushedAidsToCloud.clear()
            // 8002 was already deleted on Cloud too -> server returns code 7 (NOT_IN_BOOKSHELF)
            gateway.removeResponsesByAid[8002] = WenkuApiResult.ServerErrorCode(
                numericCode = BookshelfRepository.SERVER_CODE_NOT_IN_BOOKSHELF,
            )
            // 8003 un-synced local delete flushes `do=del` -> succeeds
            gateway.removeResponsesByAid[8003] = WenkuApiResult.Success(Unit)
            // 8007 returns code 5 (ALREADY_IN_BOOKSHELF) on `do=add`
            gateway.addResponsesByAid[8007] = WenkuApiResult.ServerErrorCode(
                numericCode = BookshelfRepository.SERVER_CODE_ALREADY_IN_BOOKSHELF,
            )
            // Simulate PHPSESSID expiry once on Pass 1
            gateway.failNextFetchCloudNotLoggedIn = true
            // Cloud shelf at Pass 1 has:
            // - 8001 (re-added on Cloud after synced online local delete!)
            // - 8003 (still present / re-added before offline local delete ever synced -> must NOT resurrect!)
            // - 8005 (still on Cloud)
            // - 8006 (independently added on Cloud too!)
            // (8002 & 8004 are absent on Cloud)
            gateway.cloudBookshelfEntries = listOf(
                ParsedCloudBookshelfEntry(aid = 8001, lastUpdateDate = "2026-03-05", title = "在线双删后云端重加", latestChapterCid = 12),
                ParsedCloudBookshelfEntry(aid = 8003, lastUpdateDate = "2026-03-05", title = "离线未同步本地删_优先冲刷一次", latestChapterCid = 31),
                ParsedCloudBookshelfEntry(aid = 8005, lastUpdateDate = "2026-03-01", title = "离线本地删后又本地加回", latestChapterCid = 51),
                ParsedCloudBookshelfEntry(aid = 8006, lastUpdateDate = "2026-03-05", title = "双端同时新增作品", latestChapterCid = 61),
            )

            val pass1 = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success
            assertTrue("Session expiry should have triggered single re-login", authTransport.loginCallCount >= 2)

            // Verify Pass 1 exact state across all 7 novels:
            // - 8001: pulled back onto local shelf with reading progress 640 preserved!
            // - 8002: tombstone cleared via code 7, stays off shelf
            // - 8003: flushed `do=del` once (`deletedFromCloudCount == 1`), tombstone cleared, NOT resurrected!
            // - 8004: removed locally due to Cloud deletion (`removedRemotelyDeletedCount == 1`), progress 810 preserved!
            // - 8005: stayed on shelf with category "重温收藏", `8005 !in removedAidsFromCloud`, reconciled to SYNCED!
            // - 8006: reconciled directly to SYNCED (`8006 !in pushedAidsToCloud`), category "新番原著" preserved!
            // - 8007: pushed via `do=add` -> code 5 treated as SYNCED!
            assertEquals(1, pass1.deletedFromCloudCount)
            assertEquals(1, pass1.removedRemotelyDeletedCount)
            assertEquals(1, pass1.pulledFromCloudCount) // 8001
            assertEquals(1, pass1.pushedToCloudCount) // 8007
            assertEquals(setOf(8002, 8003), gateway.removedAidsFromCloud.toSet())
            assertEquals(listOf(8007), gateway.pushedAidsToCloud)

            val shelfAfterPass1 = database.novelDao().getBookshelfNovelsSnapshot().associateBy { it.aid }
            assertEquals(setOf(8001, 8005, 8006, 8007), shelfAfterPass1.keys)
            assertEquals(640, database.readingProgressDao().getProgressForNovel(8001)?.progressPermille)
            assertEquals(810, database.readingProgressDao().getProgressForNovel(8004)?.progressPermille)
            assertEquals("重温收藏", shelfAfterPass1.getValue(8005).shelfCategory)
            assertEquals("新番原著", shelfAfterPass1.getValue(8006).shelfCategory)
            assertTrue(shelfAfterPass1.values.all { it.cloudSynced })
            assertNull(database.cloudBookcaseLedgerDao().getEntry("alice", 8002))
            assertNull(database.cloudBookcaseLedgerDao().getEntry("alice", 8003))
            assertNull(database.cloudBookcaseLedgerDao().getEntry("alice", 8004))

            // -------------------------------------------------------------------------------------
            // Pass 2: Now that 8002, 8003, and 8004 have all had their deletions confirmed & synced,
            // user re-adds 8002, 8003, and 8004 on Wenku8 Web (`action=bookcase` lists them again).
            // All three MUST be pulled back onto the local shelf cleanly (with 8004's progress 810 intact)!
            // -------------------------------------------------------------------------------------
            gateway.cloudBookshelfEntries = listOf(
                ParsedCloudBookshelfEntry(aid = 8001, lastUpdateDate = "2026-03-05", title = "在线双删后云端重加", latestChapterCid = 12),
                ParsedCloudBookshelfEntry(aid = 8002, lastUpdateDate = "2026-03-06", title = "离线本地删_云端亦删_再云端重加", latestChapterCid = 22),
                ParsedCloudBookshelfEntry(aid = 8003, lastUpdateDate = "2026-03-06", title = "离线未同步本地删_优先冲刷一次", latestChapterCid = 32),
                ParsedCloudBookshelfEntry(aid = 8004, lastUpdateDate = "2026-03-06", title = "云端删除后再云端重加", latestChapterCid = 42),
                ParsedCloudBookshelfEntry(aid = 8005, lastUpdateDate = "2026-03-01", title = "离线本地删后又本地加回", latestChapterCid = 51),
                ParsedCloudBookshelfEntry(aid = 8006, lastUpdateDate = "2026-03-05", title = "双端同时新增作品", latestChapterCid = 61),
                ParsedCloudBookshelfEntry(aid = 8007, lastUpdateDate = "2026-03-05", title = "并发已存在返回码5作品", latestChapterCid = 71),
            )

            val pass2 = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC) as CloudBookshelfSyncOutcome.Success
            assertEquals(3, pass2.pulledFromCloudCount)
            assertEquals(
                setOf(8001, 8002, 8003, 8004, 8005, 8006, 8007),
                database.novelDao().getBookshelfNovelIds().toSet(),
            )
            assertEquals(810, database.readingProgressDao().getProgressForNovel(8004)?.progressPermille)
        } finally {
            tempRoot.deleteRecursively()
        }
    }

    @Test
    fun coldStartFirstEntryAutoSync_runsOnceForSignedInUser_skipsSignedOut_andDoesNotRetriggerOnReentry(): Unit = runBlocking {
        val signedOutRoot = Files.createTempDirectory("bookshelf_auto_sync_signed_out").toFile()
        val signedInRoot = Files.createTempDirectory("bookshelf_auto_sync_signed_in").toFile()
        try {
            val authTransport = FakeAccountSwitchTransport().apply { activeServerUsername = "alice" }
            val authGateway = FakeAccountSwitchAuthGateway(authTransport)

            // 1. Signed-out cold start: entering BookshelfScreen must NOT auto-sync or show login prompt.
            val signedOutSession = UserSessionRepository(
                transport = authTransport,
                assetRootsProvider = { listOf(signedOutRoot) },
                authGateway = authGateway,
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )
            val signedOutGateway = FakeBookshelfCatalogGateway().apply {
                cloudBookshelfEntries = listOf(
                    ParsedCloudBookshelfEntry(aid = 3100, title = "自动同步作品", latestChapterCid = 31001),
                )
            }
            val signedOutRepo = BookshelfRepository(
                database = database,
                catalogGateway = signedOutGateway,
                novelTocSyncRepository = tocSyncRepository,
                userSessionRepository = signedOutSession,
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )

            // Persist `saves/cert.wk8` for the signed-in cold-start simulation BEFORE `initializeSessionOnStartup()`.
            val seedSession = UserSessionRepository(
                transport = authTransport,
                assetRootsProvider = { listOf(signedInRoot) },
                authGateway = authGateway,
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )
            assertTrue(seedSession.loginWithCredentials("alice", "pw123") is LoginOutcome.Success)
            val loginCountAfterSeed = authTransport.loginCallCount

            val coldStartSignedInSession = UserSessionRepository(
                transport = authTransport,
                assetRootsProvider = { listOf(signedInRoot) },
                authGateway = authGateway,
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )
            val signedInGateway = FakeBookshelfCatalogGateway().apply {
                cloudBookshelfEntries = listOf(
                    ParsedCloudBookshelfEntry(
                        aid = 3100,
                        title = "冷启动自动同步作品",
                        lastUpdateDate = "2026-03-01",
                        latestChapterCid = 31001,
                        latestChapterName = "第一卷 序章",
                    ),
                )
            }
            val signedInRepo = BookshelfRepository(
                database = database,
                catalogGateway = signedInGateway,
                novelTocSyncRepository = tocSyncRepository,
                userSessionRepository = coldStartSignedInSession,
                ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
            )

            var activeRepo by mutableStateOf(signedOutRepo)
            var showBookshelf by mutableStateOf(true)

            composeTestRule.setContent {
                Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                    if (showBookshelf) {
                        BookshelfScreen(
                            bookshelfRepository = activeRepo,
                            appLanguage = Wenku8API.AppLanguage.SC,
                            viewMode = BookshelfViewMode.GRID,
                            onViewModeSelected = {},
                            sortMode = BookshelfSortMode.MANUAL_ORDER,
                            onSortModeSelected = {},
                        )
                    }
                }
            }
            composeTestRule.waitForIdle()

            assertEquals(0, signedOutGateway.fetchCloudBookshelfCallCount)
            composeTestRule.onNodeWithText("请先登录轻小说文库账号再同步云端书架").assertDoesNotExist()

            // 2. Switch to cold-start signed-in repository (before `initializeSessionOnStartup()` ran):
            //    first entry restores session once and triggers lightweight cloud sync automatically!
            activeRepo = signedInRepo
            composeTestRule.waitForIdle()

            assertEquals(1, signedInGateway.fetchCloudBookshelfCallCount)
            assertEquals(loginCountAfterSeed + 1, authTransport.loginCallCount)
            assertEquals("冷启动自动同步作品", database.novelDao().getNovelByAid(3100)?.title)

            // If background `initializeSessionOnStartup()` runs right afterward, it reuses the
            // already-restored SignedIn session with zero duplicate login calls.
            coldStartSignedInSession.initializeSessionOnStartup()
            assertEquals(loginCountAfterSeed + 1, authTransport.loginCallCount)

            // 3. Leaving BookshelfScreen (e.g. opening NovelDetail/Reader or switching tabs) and
            //    re-entering with the same repository must NOT re-trigger auto-sync!
            showBookshelf = false
            composeTestRule.waitForIdle()
            showBookshelf = true
            composeTestRule.waitForIdle()

            assertEquals(1, signedInGateway.fetchCloudBookshelfCallCount)
        } finally {
            signedOutRoot.deleteRecursively()
            signedInRoot.deleteRecursively()
        }
    }

    @Test
    fun foldedSyncSplitButton_runsSmartUpdate_confirmsForceRefresh_andEnforcesGlobalMutualExclusion(): Unit = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 4100,
                title = "本地已同步藏书",
                author = "作者甲",
                pressId = "电击文库",
                bookStatus = "连载中",
                lastUpdate = "2026-02-20",
                latestSectionCid = 41001,
                latestSectionName = "第一话",
                tocVersion = 1L,
                inBookshelf = true,
                shelfSortOrder = 1,
                cloudSynced = true,
            ),
        )
        database.chapterContentDao().upsertChapterContent(
            ChapterContentEntity(
                cid = 41001,
                aid = 4100,
                contentLang = "SC",
                contentText = "已缓存第一话",
                byteLength = 18,
                imageUrlsJson = "[]",
                imageCount = 0,
                sha256Checksum = "sha_41001",
                downloadedAtMs = 1_700_000_000_000L,
            ),
        )

        val gateway = FakeBookshelfCatalogGateway().apply {
            cloudBookshelfEntries = listOf(
                ParsedCloudBookshelfEntry(
                    aid = 4100,
                    title = "本地已同步藏书",
                    lastUpdateDate = "2026-02-20",
                    latestChapterCid = 41001,
                    latestChapterName = "第一话",
                ),
            )
            tocByAid[4100] = listOf(
                ParsedVolumeToc(
                    vid = 41000,
                    volumeTitle = "第一卷",
                    chapters = listOf(
                        ParsedChapterToc(cid = 41001, chapterTitle = "第一话"),
                        ParsedChapterToc(cid = 41002, chapterTitle = "第二话"),
                    ),
                ),
            )
        }
        val repository = BookshelfRepository(
            database = database,
            catalogGateway = gateway,
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                BookshelfScreen(
                    bookshelfRepository = repository,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    viewMode = BookshelfViewMode.GRID,
                    onViewModeSelected = {},
                    sortMode = BookshelfSortMode.MANUAL_ORDER,
                    onSortModeSelected = {},
                )
            }
        }
        composeTestRule.waitForIdle()

        // 1. Expand `▾` menu and trigger `智能更新` -> downloads missing chapter 41002 for cached book 4100.
        composeTestRule.onNodeWithTag(BookshelfTestTags.SMART_UPDATE_TRAILING_BUTTON).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SMART_UPDATE_MENU_ITEM).assertIsDisplayed()
        composeTestRule.onNodeWithTag(BookshelfTestTags.FORCE_CLOUD_SYNC_MENU_ITEM).assertIsDisplayed()

        composeTestRule.onNodeWithTag(BookshelfTestTags.SMART_UPDATE_MENU_ITEM).performClick()
        composeTestRule.waitForIdle()
        assertTrue(41002 in database.chapterContentDao().getCachedChapterIdsForNovel(4100))

        // 2. Expand `▾` menu and select `强制刷新全部云端书目`: must require confirmation before running!
        val metaCallsBeforeForceMenu = gateway.fetchedFullMetaCount
        composeTestRule.onNodeWithTag(BookshelfTestTags.SMART_UPDATE_TRAILING_BUTTON).performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.FORCE_CLOUD_SYNC_MENU_ITEM).performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithTag(BookshelfTestTags.CONFIRM_FORCE_SYNC_BUTTON).assertIsDisplayed()
        assertEquals(metaCallsBeforeForceMenu, gateway.fetchedFullMetaCount)

        composeTestRule.onNodeWithTag(BookshelfTestTags.CONFIRM_FORCE_SYNC_BUTTON).performClick()
        composeTestRule.waitForIdle()
        // Even though aid=4100 had matching latestSectionCid & lastUpdateDate, forceRefreshAll refreshed its metadata!
        assertEquals(metaCallsBeforeForceMenu + 1, gateway.fetchedFullMetaCount)

        // 3. Global mutual exclusion: enter multi-select first, then hold Cloud Sync in flight via gate.
        composeTestRule.onNodeWithTag(BookshelfTestTags.BOOK_CARD_PREFIX + 4100)
            .performTouchInput { longClick() }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(BookshelfTestTags.BATCH_SMART_UPDATE).assertIsEnabled()

        val syncGate = CompletableDeferred<Unit>()
        gateway.suspendFetchCloudGate = syncGate
        composeTestRule.mainClock.autoAdvance = false
        try {
            composeTestRule.onNodeWithTag(BookshelfTestTags.CLOUD_SYNC_BUTTON).performClick()
            composeTestRule.mainClock.advanceTimeBy(100)

            assertTrue(repository.isShelfTaskRunning)
            composeTestRule.onNodeWithTag(BookshelfTestTags.HEADER_ACTIVE_SYNC_PROGRESS).assertExists()
            composeTestRule.onNodeWithTag(BookshelfTestTags.CLOUD_SYNC_BUTTON).assertIsNotEnabled()
            composeTestRule.onNodeWithTag(BookshelfTestTags.SMART_UPDATE_TRAILING_BUTTON).assertIsNotEnabled()
            composeTestRule.onNodeWithTag(BookshelfTestTags.BATCH_SMART_UPDATE).assertIsNotEnabled()

            // Direct repository calls also enforce non-queuing mutual exclusion!
            val concurrentSync = repository.syncWithCloudBookcase(Wenku8API.AppLanguage.SC)
            assertEquals(CloudBookshelfSyncOutcome.AlreadyRunning, concurrentSync)

            val concurrentSmartUpdate = repository.smartUpdateShelfNovels(
                aids = listOf(4100),
                appLanguage = Wenku8API.AppLanguage.SC,
            )
            assertTrue(concurrentSmartUpdate.skippedBecauseBusy)
            assertEquals(0, concurrentSmartUpdate.updatedNovelCount)
        } finally {
            gateway.suspendFetchCloudGate = null
            syncGate.complete(Unit)
            composeTestRule.mainClock.autoAdvance = true
        }
        composeTestRule.waitForIdle()

        assertFalse(repository.isShelfTaskRunning)
        composeTestRule.onNodeWithTag(BookshelfTestTags.CLOUD_SYNC_BUTTON).assertIsEnabled()
        composeTestRule.onNodeWithTag(BookshelfTestTags.SMART_UPDATE_TRAILING_BUTTON).assertIsEnabled()
        composeTestRule.onNodeWithTag(BookshelfTestTags.BATCH_SMART_UPDATE).assertIsEnabled()
    }

    @Test
    fun multiVolumeNovel_finishingVolume1LastChapterShowsWholeBookPercentInsteadOf100(): Unit = runBlocking {
        database.novelDao().upsertNovel(
            NovelEntity(
                aid = 3000,
                title = "多卷进度验证作品",
                author = "测试作者",
                inBookshelf = true,
                shelfSortOrder = 1,
            ),
        )
        // 3 volumes * 5 chapters = 15 chapters total across the book
        tocSyncRepository.syncNovelIndexAtomically(
            aid = 3000,
            language = Wenku8API.AppLanguage.SC,
            requestStartedAtMs = 1_000L,
            parsedVolumes = listOf(
                ParsedVolumeToc(
                    vid = 10,
                    volumeTitle = "第一卷",
                    chapters = (1..5).map { idx -> ParsedChapterToc(cid = 100 + idx, chapterTitle = "第一卷 第${idx}章") },
                ),
                ParsedVolumeToc(
                    vid = 20,
                    volumeTitle = "第二卷",
                    chapters = (1..5).map { idx -> ParsedChapterToc(cid = 200 + idx, chapterTitle = "第二卷 第${idx}章") },
                ),
                ParsedVolumeToc(
                    vid = 30,
                    volumeTitle = "第三卷",
                    chapters = (1..5).map { idx -> ParsedChapterToc(cid = 300 + idx, chapterTitle = "第三卷 第${idx}章") },
                ),
            ),
        )
        // Reader finishes the LAST page of Volume 1's LAST chapter (`cid = 105, progressPermille = 1000`)
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 3000,
                vid = 10,
                cid = 105,
                volumeTitle = "第一卷",
                chapterTitle = "第一卷 第5章",
                progressPermille = 1000,
                updatedAtMs = 10_000L,
            ),
        )

        val repository = BookshelfRepository(
            database = database,
            catalogGateway = FakeBookshelfCatalogGateway(),
            novelTocSyncRepository = tocSyncRepository,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined,
        )

        val snapAfterVol1 = repository.observeBookshelfSnapshot().first { it.allShelfItems.size == 1 }
        val itemAfterVol1 = snapAfterVol1.allShelfItems.single()
        assertEquals(15, itemAfterVol1.totalChapterCount)
        // (4 + 1.0) / 15 = 333‰ -> 33% (NOT 100%!)
        assertEquals(333, itemAfterVol1.bookProgressPermille)
        assertEquals(33, itemAfterVol1.readingProgressPercent)

        composeTestRule.setContent {
            Wenku8ExpressiveTheme(themeMode = Wenku8ThemeMode.ARCHIVAL_DAY) {
                BookshelfScreen(
                    bookshelfRepository = repository,
                    appLanguage = Wenku8API.AppLanguage.SC,
                    viewMode = BookshelfViewMode.GRID,
                    onViewModeSelected = {},
                    sortMode = BookshelfSortMode.MANUAL_ORDER,
                    onSortModeSelected = {},
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(
            BookshelfTestTags.READING_PROGRESS_PERCENT_PREFIX + 3000,
            useUnmergedTree = true,
        ).assertTextEquals("33%")

        // Halfway through the final chapter (`cid = 305, progressPermille = 500`) -> 97% (< 100%!)
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 3000,
                vid = 30,
                cid = 305,
                volumeTitle = "第三卷",
                chapterTitle = "第三卷 第5章",
                progressPermille = 500,
                updatedAtMs = 20_000L,
            ),
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(
            BookshelfTestTags.READING_PROGRESS_PERCENT_PREFIX + 3000,
            useUnmergedTree = true,
        ).assertTextEquals("97%")

        // Final page of the final volume's final chapter (`cid = 305, progressPermille = 1000`) -> 100%!
        database.readingProgressDao().upsertProgress(
            ReadingProgressEntity(
                aid = 3000,
                vid = 30,
                cid = 305,
                volumeTitle = "第三卷",
                chapterTitle = "第三卷 第5章",
                progressPermille = 1000,
                updatedAtMs = 30_000L,
            ),
        )
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag(
            BookshelfTestTags.READING_PROGRESS_PERCENT_PREFIX + 3000,
            useUnmergedTree = true,
        ).assertTextEquals("100%")
    }

    private class FakeAccountSwitchTransport : WenkuTransport {
        override val isEndpointConfigured: Boolean = true
        var activeServerUsername: String = "alice"
        var loginCallCount: Int = 0

        override suspend fun postSealedAction(
            rawActionQuery: String,
            isIdempotentReadOnly: Boolean,
        ): org.mewx.wenku8.data.remote.transport.RelayPostResult {
            val payload = when (rawActionQuery) {
                "action=login" -> {
                    loginCallCount++
                    "1".toByteArray(StandardCharsets.UTF_8)
                }
                "action=logout" -> "1".toByteArray(StandardCharsets.UTF_8)
                "action=userinfo" -> """
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
            return org.mewx.wenku8.data.remote.transport.RelayPostResult.Success(payload)
        }

        override suspend fun getBytes(url: String): ByteArray? = null
        override fun clearSessionCookie() = Unit
    }

    private class FakeAccountSwitchAuthGateway(
        private val transport: FakeAccountSwitchTransport,
    ) : AccountAuthGateway {
        override val isAuthConfigured: Boolean = true
        override val baseUrlOrEmpty: String = "https://relay.example.test/v2/api"
        override val registerUrlOrEmpty: String = ""

        override fun loginQuery(identifier: String, password: String): String = "action=login"
        override fun logoutQuery(): String = "action=logout"
        override fun userInfoQuery(): String = "action=userinfo"
        override fun userSignQuery(): String = "action=sign"

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
        var failNextFetchCloudNotLoggedIn: Boolean = false
        var fetchCloudBookshelfCallCount: Int = 0
        var fetchedFullMetaCount: Int = 0
        var suspendFetchCloudGate: CompletableDeferred<Unit>? = null
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

        override suspend fun searchNovelsByField(
            query: String,
            searchField: OnlineSearchField,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<List<Int>> = WenkuApiResult.ApiNotConfigured

        override suspend fun fetchNovelFullMeta(
            aid: Int,
            appLanguage: Wenku8API.AppLanguage,
        ): WenkuApiResult<ParsedNovelFullMeta> {
            fetchedFullMetaCount++
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
        ): WenkuApiResult<List<ParsedCloudBookshelfEntry>> {
            fetchCloudBookshelfCallCount++
            suspendFetchCloudGate?.await()
            if (failNextFetchCloudNotLoggedIn) {
                failNextFetchCloudNotLoggedIn = false
                return WenkuApiResult.ServerErrorCode(BookshelfRepository.SERVER_CODE_NOT_LOGGED_IN)
            }
            return WenkuApiResult.Success(cloudBookshelfEntries)
        }

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
