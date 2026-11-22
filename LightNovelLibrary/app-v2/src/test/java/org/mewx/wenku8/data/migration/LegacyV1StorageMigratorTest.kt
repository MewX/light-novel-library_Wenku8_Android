package org.mewx.wenku8.data.migration

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.data.local.db.Wenku8Database
import org.mewx.wenku8.data.local.db.entity.SearchHistoryEntity
import org.mewx.wenku8.data.local.preferences.UserPreferencesRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LegacyV1StorageMigratorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: Wenku8Database

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, Wenku8Database::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun migratesCompleteV1InternalStorageFixtureAcrossMultiBatchChaptersWithoutDeletingSourceFiles() =
        runTest(UnconfinedTestDispatcher()) {
            val internalFilesDir = tempFolder.newFolder("internal_files")
            val savesDir = File(internalFilesDir, "saves").apply { mkdirs() }
            val introDir = File(savesDir, "intro").apply { mkdirs() }
            val novelDir = File(savesDir, "novel").apply { mkdirs() }
            val customDir = File(internalFilesDir, "custom").apply { mkdirs() }

            // Create migrated custom font inside internal custom/ while settings.wk8 still holds
            // an old pre-Android-11 /sdcard/wenku8/custom/LXGWWenKai.ttf path!
            val internalFontFile = File(customDir, "LXGWWenKai.ttf").apply {
                writeText("fake-ttf-bytes")
            }

            // 1. settings.wk8 (including a damaged partial record that must be skipped safely)
            val settingsFile = File(savesDir, "settings.wk8").apply {
                writeText(
                    listOf(
                        "version::::1",
                        "language::::TC",
                        "eink_mode::::1",
                        "reader_font_size::::24",
                        "reader_line_distance::::18",
                        "reader_font_path::::/sdcard/wenku8/custom/LXGWWenKai.ttf",
                        "corrupted_partial_record_without_delimiter",
                    ).joinToString("||||"),
                )
            }
            File(savesDir, "notice.wk8").writeText("旧版公告缓存")

            // 2. bookshelf_local.wk8: 1306 (full XML), 9999 (missing XML stub test!), bad_token, 1587 (full XML)
            val bookshelfFile = File(savesDir, "bookshelf_local.wk8").apply {
                writeText("1306||9999||damaged_token||1587")
            }

            // 3. intro XMLs for 1306 (30 chapters to span >1 25-chapter WAL batch!) and 1587 (1 chapter)
            File(introDir, "1306-intro.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <metadata>
                <data name="Title" aid="1306"><![CDATA[向森之魔物献上花束]]></data>
                <data name="Author" value="小木君人"/>
                <data name="BookStatus" value="已完成"/>
                <data name="LatestSection" cid="41030"><![CDATA[第一卷 第三十章]]></data>
                </metadata>
                """.trimIndent(),
            )
            File(introDir, "1306-introfull.xml").writeText("森林少女与青年的相遇物语。")

            val chapters1306Xml = (1..30).joinToString("\n") { idx ->
                val cid = 41000 + idx
                "<chapter cid=\"$cid\"><![CDATA[第${idx}章]]></chapter>"
            }
            File(introDir, "1306-volume.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <package>
                <volume vid="41000"><![CDATA[第一卷 正篇]]>
                $chapters1306Xml
                </volume>
                </package>
                """.trimIndent(),
            )

            File(introDir, "1587-intro.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <metadata>
                <data name="Title" aid="1587"><![CDATA[无职转生]]></data>
                <data name="Author" value="理不尽な孙の手"/>
                <data name="BookStatus" value="已完成"/>
                </metadata>
                """.trimIndent(),
            )
            File(introDir, "1587-volume.xml").writeText(
                """
                <?xml version="1.0" encoding="utf-8"?>
                <package>
                <volume vid="55000"><![CDATA[第一卷 幼年期]]>
                <chapter cid="55001"><![CDATA[序章]]></chapter>
                </volume>
                </package>
                """.trimIndent(),
            )

            // 4. Write all 30 offline chapters for 1306 (spanning batch 1 = 25 and batch 2 = 5)
            for (idx in 1..30) {
                val cid = 41000 + idx
                File(novelDir, "$cid.xml").writeText(
                    "第${idx}章正文\r\n<!--image-->https://pic.wenku8.com/pictures/1/1306/$cid/01.jpg<!--image-->",
                )
            }

            // 5. Dual read saves: V1 paged save for 1306; V0 vertical scroll save for 1587
            File(savesDir, "read_saves_v1.wk8").writeText("1306:41000:41012:8:3")
            File(savesDir, "read_saves.wk8").writeText("55001,,450,,1000||41012,,900,,1000")

            // 6. Search history with 0 (title) and 1 (author) prefixes
            File(savesDir, "search_history.wk8").writeText("[0向森之魔物][1小木君人]")

            val dataStoreFile = tempFolder.newFile("migrated_settings.preferences_pb")
            val prefsRepo = UserPreferencesRepository(
                PreferenceDataStoreFactory.create(
                    scope = backgroundScope,
                    produceFile = { dataStoreFile },
                ),
            )
            val migrator = LegacyV1StorageMigrator(database, prefsRepo)

            val observedProgressBatches = ArrayList<LegacyMigrationProgress>()
            val report = migrator.migrateFromSource(
                source = InternalFileSaveSource(primaryInternalRoot = internalFilesDir),
                migrationTimestampMs = 100_000L,
                onProgress = { observedProgressBatches.add(it) },
            )

            // Verify migration summary counts
            assertEquals(3, report.migratedBookshelfNovelsCount)
            assertEquals(3, report.migratedCachedNovelsCount)
            assertEquals(30, report.migratedChaptersCount)
            assertEquals(2, report.migratedReadingProgressCount)
            assertEquals(2, report.migratedSearchHistoryCount)

            // Verify settings & re-anchored internal font path
            val migratedPrefs = prefsRepo.preferencesFlow.first()
            assertEquals(Wenku8API.AppLanguage.TC, migratedPrefs.appLanguage)
            assertTrue(migratedPrefs.einkModeEnabled)
            assertEquals(24, migratedPrefs.readerFontSizeSp)
            assertEquals(18, migratedPrefs.readerLineDistanceDp)
            assertEquals(internalFontFile.absolutePath, migratedPrefs.readerCustomFontPath)
            assertEquals("旧版公告缓存", migratedPrefs.cachedNoticeText)

            // Verify bookshelf order & resilient stub for missing-XML novel 9999
            val shelfNovels = database.novelDao().observeBookshelfNovels().first()
            assertEquals(listOf(1306, 9999, 1587), shelfNovels.map { it.aid })
            assertEquals("AID #9999", shelfNovels[1].title)

            // Verify 30 offline chapters + pre-indexed imageUrlsJson for instant Deep Clean
            val cachedCids = database.chapterContentDao().getCachedChapterIdsForNovel(1306)
            assertEquals(30, cachedCids.size)
            val header41030 = database.chapterContentDao().getChapterHeader(41030)!!
            assertEquals(1, header41030.imageCount)
            assertTrue(header41030.imageUrlsJson.contains("41030/01.jpg"))

            // Verify V1 reading progress took precedence on 1306 while V0 scroll filled 1587
            val progress1306 = database.readingProgressDao().getProgressForNovel(1306)!!
            assertEquals(41012, progress1306.cid)
            assertEquals(8, progress1306.paragraphIndex)
            assertEquals(3, progress1306.charOffset)
            assertEquals(0, progress1306.legacyScrollPx)
            assertEquals("第一卷 正篇", progress1306.volumeTitle)

            val progress1587 = database.readingProgressDao().getProgressForNovel(1587)!!
            assertEquals(55001, progress1587.cid)
            assertEquals(55000, progress1587.vid)
            assertEquals(450, progress1587.legacyScrollPx)
            assertEquals(450, progress1587.progressPermille)
            assertEquals("序章", progress1587.chapterTitle)

            // Verify search history stripped '0' / '1' prefixes cleanly
            val history = database.searchHistoryDao().getAllHistory()
            assertEquals(2, history.size)
            assertEquals("向森之魔物", history[0].query)
            assertEquals(SearchHistoryEntity.SEARCH_TYPE_ARTICLE_NAME, history[0].searchType)
            assertEquals("小木君人", history[1].query)
            assertEquals(SearchHistoryEntity.SEARCH_TYPE_AUTHOR, history[1].searchType)

            // Verify non-destructive retention: original .wk8 and .xml files were NOT deleted!
            assertTrue(settingsFile.exists())
            assertTrue(bookshelfFile.exists())
            assertTrue(File(novelDir, "41001.xml").exists())
            assertTrue(observedProgressBatches.any { it.totalChapters == 30 })
        }
}
