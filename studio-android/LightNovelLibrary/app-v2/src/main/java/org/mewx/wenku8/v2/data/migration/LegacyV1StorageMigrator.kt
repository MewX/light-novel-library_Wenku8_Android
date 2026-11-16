package org.mewx.wenku8.v2.data.migration

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.network.LightUserSession
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.ChapterContentEntity
import org.mewx.wenku8.v2.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.db.entity.ReadingProgressEntity
import org.mewx.wenku8.v2.data.local.db.entity.SearchHistoryEntity
import org.mewx.wenku8.v2.data.local.db.entity.VolumeEntity
import org.mewx.wenku8.v2.data.local.preferences.UserPreferencesRepository
import org.mewx.wenku8.v2.data.remote.parser.WenkuXmlParsers
import org.mewx.wenku8.v2.data.repository.NovelTocSyncRepository
import org.mewx.wenku8.v2.util.DigestUtils
import java.io.File

/**
 * Abstraction over a legacy `v1.x` Wenku8 storage directory so the exact same tested parser and
 * WAL batch-import pipeline powers BOTH:
 * 1. Seamless zero-prompt Play Store internal upgrade (`context.filesDir` + legacy fallback).
 * 2. Manual SAF folder import (`ACTION_OPEN_DOCUMENT_TREE` on `/sdcard/wenku8`) across devices.
 */
interface LegacyV1SaveSource {
    fun readSavesRootFileUtf8(fileName: String): String?
    fun readSavesSubfolderFileUtf8(subFolder: String, fileName: String): String?
    fun listSavesSubfolderFileNames(subFolder: String): List<String>
    fun resolveCustomAssetPath(rawLegacyPath: String): String
}

/**
 * Probes `primaryInternalRoot` (`context.filesDir`) first and optional `fallbackExternalRoot`
 * (`/sdcard/wenku8/`) second, matching `v1.31`'s `StorageRoots.java` lookup precedence.
 */
class InternalFileSaveSource(
    private val primaryInternalRoot: File,
    private val fallbackExternalRoot: File? = null,
) : LegacyV1SaveSource {

    private val orderedRoots: List<File> = listOfNotNull(
        primaryInternalRoot,
        fallbackExternalRoot?.takeIf { it.exists() && it.canRead() && it != primaryInternalRoot },
    )

    override fun readSavesRootFileUtf8(fileName: String): String? {
        for (root in orderedRoots) {
            val candidate = File(root, "saves/$fileName")
            if (candidate.isFile && candidate.canRead()) {
                return runCatching { candidate.readText(Charsets.UTF_8) }.getOrNull()
            }
        }
        return null
    }

    override fun readSavesSubfolderFileUtf8(subFolder: String, fileName: String): String? {
        for (root in orderedRoots) {
            val candidate = File(root, "saves/$subFolder/$fileName")
            if (candidate.isFile && candidate.canRead()) {
                return runCatching { candidate.readText(Charsets.UTF_8) }.getOrNull()
            }
        }
        return null
    }

    override fun listSavesSubfolderFileNames(subFolder: String): List<String> {
        val mergedNames = LinkedHashSet<String>()
        for (root in orderedRoots) {
            val folder = File(root, "saves/$subFolder")
            folder.list()?.forEach { mergedNames.add(it) }
        }
        return mergedNames.toList()
    }

    /**
     * Re-anchors pre-Android-11 `/sdcard/wenku8/custom/{name}` paths in `settings.wk8` to
     * `File(primaryInternalRoot, "custom/{name}")` when the migrated file lives internally.
     */
    override fun resolveCustomAssetPath(rawLegacyPath: String): String {
        if (rawLegacyPath.isBlank() || rawLegacyPath == "0") return "0"
        val directFile = File(rawLegacyPath)
        if (directFile.isFile) return directFile.absolutePath

        val leafName = directFile.name
        if (leafName.isNotEmpty()) {
            val internalReanchored = File(primaryInternalRoot, "custom/$leafName")
            if (internalReanchored.isFile) {
                return internalReanchored.absolutePath
            }
        }
        return rawLegacyPath
    }
}

/**
 * SAF `DocumentFile` implementation for cross-device `ACTION_OPEN_DOCUMENT_TREE` folder imports,
 * copying referenced `custom/` fonts or backgrounds into `internalFilesDir/custom/` so Compose and
 * `Typeface.createFromFile` can load them synchronously without expiring content URI grants.
 */
class DocumentTreeSaveSource(
    private val context: Context,
    private val treeRoot: DocumentFile,
    private val internalFilesDir: File = context.filesDir,
) : LegacyV1SaveSource {

    private val savesDir: DocumentFile? by lazy {
        if (treeRoot.name == "saves") treeRoot else treeRoot.findFile("saves")
    }

    override fun readSavesRootFileUtf8(fileName: String): String? {
        val target = savesDir?.findFile(fileName) ?: return null
        return readDocumentUtf8(target)
    }

    override fun readSavesSubfolderFileUtf8(subFolder: String, fileName: String): String? {
        val folder = savesDir?.findFile(subFolder) ?: return null
        val target = folder.findFile(fileName) ?: return null
        return readDocumentUtf8(target)
    }

    override fun listSavesSubfolderFileNames(subFolder: String): List<String> {
        val folder = savesDir?.findFile(subFolder) ?: return emptyList()
        return folder.listFiles().mapNotNull { it.name }
    }

    override fun resolveCustomAssetPath(rawLegacyPath: String): String {
        if (rawLegacyPath.isBlank() || rawLegacyPath == "0") return "0"
        val leafName = rawLegacyPath.substringAfterLast('/').substringAfterLast('\\')
        if (leafName.isEmpty()) return "0"

        val customDoc = treeRoot.findFile("custom")?.findFile(leafName) ?: return rawLegacyPath
        val destinationDir = File(internalFilesDir, "custom").apply { mkdirs() }
        val destinationFile = File(destinationDir, leafName)
        runCatching {
            context.contentResolver.openInputStream(customDoc.uri)?.use { input ->
                destinationFile.outputStream().use { output -> input.copyTo(output) }
            }
        }
        return if (destinationFile.isFile) destinationFile.absolutePath else rawLegacyPath
    }

    private fun readDocumentUtf8(doc: DocumentFile): String? {
        if (!doc.isFile) return null
        return runCatching {
            context.contentResolver.openInputStream(doc.uri)?.bufferedReader(Charsets.UTF_8)?.use {
                it.readText()
            }
        }.getOrNull()
    }
}

data class LegacyMigrationProgress(
    val stageDescription: String,
    val processedChapters: Int = 0,
    val totalChapters: Int = 0,
)

data class LegacyMigrationReport(
    val migratedSettingsCount: Int,
    val restoredAccountCredential: Boolean,
    val migratedBookshelfNovelsCount: Int,
    val migratedCachedNovelsCount: Int,
    val migratedChaptersCount: Int,
    val migratedReadingProgressCount: Int,
    val migratedSearchHistoryCount: Int,
)

/**
 * Seamless, non-destructive `v1.31 -> v2.0` storage migrator (`09-room-database-and-backup-archive-design.md` §5).
 *
 * Strictly obeys `STORAGE_MIGRATION_PLAN.md` Rule 1:
 * - **Zero-copy in-place retention**: All original `.wk8`, `saves/intro/{aid}-*.xml`,
 *   `saves/novel/{cid}.xml`, and `saves/imgs/` files are NEVER deleted during `v2.0` migration.
 * - **25-chapter WAL batching**: Imports large offline novel caches (`saves/novel/{cid}.xml`) in
 *   25-chapter transactions so even 3,000+ offline chapters migrate smoothly without OOM or ANR.
 */
class LegacyV1StorageMigrator(
    private val database: Wenku8Database,
    private val userPreferencesRepository: UserPreferencesRepository,
) {

    suspend fun migrateFromSource(
        source: LegacyV1SaveSource,
        migrationTimestampMs: Long = System.currentTimeMillis(),
        onProgress: (LegacyMigrationProgress) -> Unit = {},
    ): LegacyMigrationReport {
        // 1. Parse settings.wk8 (|||| and ::::) + notice.wk8 -> DataStore.
        onProgress(LegacyMigrationProgress("正在迁移阅读与常规设置"))
        val rawSettings = source.readSavesRootFileUtf8(SETTINGS_FILE_NAME).orEmpty()
        val parsedSettings = parseLegacySettingsMap(rawSettings).toMutableMap()
        for (pathKey in listOf("menu_bg_path", "reader_font_path", "reader_background_path")) {
            parsedSettings[pathKey]?.let { rawPath ->
                parsedSettings[pathKey] = source.resolveCustomAssetPath(rawPath)
            }
        }
        val noticeText = source.readSavesRootFileUtf8(NOTICE_FILE_NAME)?.trim()
        val activeLang = if (parsedSettings["language"].equals("TC", ignoreCase = true)) {
            Wenku8API.AppLanguage.TC
        } else {
            Wenku8API.AppLanguage.SC
        }
        userPreferencesRepository.applyMigratedLegacyV1Settings(
            migratedSettings = parsedSettings,
            cachedNoticeText = noticeText,
        )

        // 2. Restore cert.wk8 into LightUserSession (guarded for :api-stub CI builds; never logged!).
        val restoredAccount = restoreLegacyUserSessionSafely(
            rawCert = source.readSavesRootFileUtf8(CERT_FILE_NAME),
        )

        // 3. Migrate bookshelf_local.wk8 + saves/intro/{aid}-{intro,introfull,volume}.xml.
        onProgress(LegacyMigrationProgress("正在迁移本地书架与章节目录"))
        val orderedBookshelfAids = parseBookshelfAidList(
            source.readSavesRootFileUtf8(BOOKSHELF_FILE_NAME).orEmpty(),
        )
        val bookshelfOrderMap = orderedBookshelfAids
            .withIndex()
            .associate { (index, aid) -> aid to index }

        val introFileNames = source.listSavesSubfolderFileNames(INTRO_SUBFOLDER)
        val discoveredIntroAids = LinkedHashSet<Int>().apply {
            addAll(orderedBookshelfAids)
            for (name in introFileNames) {
                val dashIndex = name.indexOf('-')
                if (dashIndex > 0) {
                    name.substring(0, dashIndex).toIntOrNull()?.takeIf { it > 0 }?.let { add(it) }
                }
            }
        }

        val chapterToNovelAid = HashMap<Int, Int>()
        val chapterToOwnerInfo = HashMap<Int, ChapterOwnerSnapshot>()
        var migratedCachedNovelsCount = 0

        database.withTransaction {
            for (aid in discoveredIntroAids) {
                val isBookshelfMember = aid in bookshelfOrderMap
                val shelfOrder = bookshelfOrderMap[aid] ?: 0

                val introXml = source.readSavesSubfolderFileUtf8(INTRO_SUBFOLDER, "$aid-intro.xml")
                val fullIntroText = source.readSavesSubfolderFileUtf8(
                    INTRO_SUBFOLDER,
                    "$aid-introfull.xml",
                )?.trim().orEmpty()
                val volumeXml = source.readSavesSubfolderFileUtf8(INTRO_SUBFOLDER, "$aid-volume.xml")

                val parsedMeta = WenkuXmlParsers.parseNovelFullMeta(introXml)
                val parsedVolumes = WenkuXmlParsers.parseVolumeTocList(volumeXml)

                // Even if an intro XML file was damaged or partially written in v1.31, NEVER drop a
                // novel from the user's bookshelf: insert a resilient stub queued for cloud refresh!
                if (parsedMeta == null && !isBookshelfMember && parsedVolumes.isEmpty()) {
                    continue
                }

                val tocDigest = if (parsedVolumes.isNotEmpty()) {
                    NovelTocSyncRepository.computeNormalizedTocSha256(parsedVolumes)
                } else {
                    ""
                }

                val novelEntity = NovelEntity(
                    aid = aid,
                    contentLang = activeLang.name,
                    tocVersion = if (parsedVolumes.isNotEmpty()) 1L else 0L,
                    tocSha256 = tocDigest,
                    metaSyncedAtMs = migrationTimestampMs,
                    title = parsedMeta?.title ?: "AID #$aid",
                    author = parsedMeta?.author.orEmpty(),
                    bookStatus = parsedMeta?.bookStatus.orEmpty(),
                    pressId = parsedMeta?.pressId.orEmpty(),
                    bookLength = parsedMeta?.bookLength ?: 0,
                    lastUpdate = parsedMeta?.lastUpdate.orEmpty(),
                    latestSectionCid = parsedMeta?.latestSectionCid ?: 0,
                    latestSectionName = parsedMeta?.latestSectionName.orEmpty(),
                    dayHits = parsedMeta?.dayHitsCount ?: 0,
                    totalHits = parsedMeta?.totalHitsCount ?: 0,
                    pushCount = parsedMeta?.pushCount ?: 0,
                    favCount = parsedMeta?.favCount ?: 0,
                    fullIntro = fullIntroText,
                    introShort = fullIntroText.lineSequence().firstOrNull()?.trim().orEmpty(),
                    inBookshelf = isBookshelfMember,
                    shelfSortOrder = shelfOrder,
                    lastAccessedMs = migrationTimestampMs - shelfOrder,
                )
                database.novelDao().upsertNovel(novelEntity)
                migratedCachedNovelsCount++

                if (parsedVolumes.isNotEmpty()) {
                    val volumeEntities = parsedVolumes.mapIndexed { volumeIndex, volume ->
                        VolumeEntity(
                            vid = volume.vid,
                            aid = aid,
                            volumeOrder = volumeIndex,
                            volumeTitle = volume.volumeTitle,
                        )
                    }
                    val chapterEntities = parsedVolumes.flatMap { volume ->
                        volume.chapters.mapIndexed { chapterIndex, chapter ->
                            chapterToNovelAid[chapter.cid] = aid
                            chapterToOwnerInfo[chapter.cid] = ChapterOwnerSnapshot(
                                aid = aid,
                                vid = volume.vid,
                                volumeTitle = volume.volumeTitle,
                                chapterTitle = chapter.chapterTitle,
                            )
                            ChapterEntity(
                                cid = chapter.cid,
                                vid = volume.vid,
                                aid = aid,
                                chapterOrder = chapterIndex,
                                chapterTitle = chapter.chapterTitle,
                            )
                        }
                    }
                    database.tocDao().upsertVolumes(volumeEntities)
                    database.tocDao().upsertChapters(chapterEntities)
                }
            }
        }

        // 4. Stream-import saves/novel/{cid}.xml in batches of 25 chapters per WAL transaction.
        val novelXmlFiles = source.listSavesSubfolderFileNames(NOVEL_SUBFOLDER)
            .filter { it.endsWith(".xml", ignoreCase = true) }
        val totalChapterFiles = novelXmlFiles.size
        var migratedChaptersCount = 0

        novelXmlFiles.chunked(CHAPTER_WAL_BATCH_SIZE).forEachIndexed { batchIndex, fileBatch ->
            val batchEntities = ArrayList<ChapterContentEntity>(fileBatch.size)
            for (fileName in fileBatch) {
                val cid = fileName.substringBeforeLast('.').toIntOrNull() ?: continue
                val ownerAid = chapterToNovelAid[cid] ?: continue
                val chapterText = source.readSavesSubfolderFileUtf8(NOVEL_SUBFOLDER, fileName)
                    ?.takeIf { it.isNotEmpty() }
                    ?: continue

                val imageUrls = WenkuXmlParsers.extractChapterImageUrls(chapterText)
                batchEntities.add(
                    ChapterContentEntity(
                        cid = cid,
                        aid = ownerAid,
                        contentLang = activeLang.name,
                        contentText = chapterText,
                        byteLength = chapterText.toByteArray(Charsets.UTF_8).size,
                        imageUrlsJson = Json.encodeToString(imageUrls),
                        imageCount = imageUrls.size,
                        sha256Checksum = DigestUtils.sha256Hex(chapterText),
                        downloadedAtMs = migrationTimestampMs,
                    ),
                )
            }
            if (batchEntities.isNotEmpty()) {
                database.chapterContentDao().upsertChapterContents(batchEntities)
                migratedChaptersCount += batchEntities.size
            }
            onProgress(
                LegacyMigrationProgress(
                    stageDescription = "正在迁移离线章节缓存",
                    processedChapters = minOf((batchIndex + 1) * CHAPTER_WAL_BATCH_SIZE, totalChapterFiles),
                    totalChapters = totalChapterFiles,
                ),
            )
        }

        // 5. Migrate dual reading progress files (read_saves_v1.wk8 prioritized, read_saves.wk8 fallback).
        val v1ProgressRecords = parseReadSavesV1(
            source.readSavesRootFileUtf8(READ_SAVES_V1_FILE_NAME).orEmpty(),
        )
        val v0ScrollRecords = parseReadSavesV0(
            source.readSavesRootFileUtf8(READ_SAVES_V0_FILE_NAME).orEmpty(),
        )

        val knownNovelIds = database.withTransaction {
            discoveredIntroAids.filter { database.novelDao().getSyncHeader(it) != null }.toSet()
        }
        val progressByAid = LinkedHashMap<Int, ReadingProgressEntity>()

        for (record in v1ProgressRecords) {
            if (record.aid !in knownNovelIds) continue
            val ownerSnapshot = chapterToOwnerInfo[record.cid]
            progressByAid[record.aid] = ReadingProgressEntity(
                aid = record.aid,
                vid = ownerSnapshot?.vid ?: record.vid,
                cid = record.cid,
                volumeTitle = ownerSnapshot?.volumeTitle.orEmpty(),
                chapterTitle = ownerSnapshot?.chapterTitle.orEmpty(),
                paragraphIndex = record.lineId.coerceAtLeast(0),
                charOffset = record.wordId.coerceAtLeast(0),
                legacyScrollPx = 0,
                progressPermille = 0,
                updatedAtMs = migrationTimestampMs,
            )
        }

        // V0 scroll saves (`cid,,pos,,height`) fill in any novel without a V1 paged-reader save.
        for (scrollRecord in v0ScrollRecords) {
            val owner = chapterToOwnerInfo[scrollRecord.cid] ?: continue
            if (owner.aid !in knownNovelIds || owner.aid in progressByAid) continue
            val estimatedPermille = if (scrollRecord.height > 0) {
                ((scrollRecord.pos.toLong() * 1000L) / scrollRecord.height.toLong())
                    .toInt()
                    .coerceIn(0, 1000)
            } else {
                0
            }
            progressByAid[owner.aid] = ReadingProgressEntity(
                aid = owner.aid,
                vid = owner.vid,
                cid = scrollRecord.cid,
                volumeTitle = owner.volumeTitle,
                chapterTitle = owner.chapterTitle,
                paragraphIndex = 0,
                charOffset = 0,
                legacyScrollPx = scrollRecord.pos.coerceAtLeast(0),
                progressPermille = estimatedPermille,
                updatedAtMs = migrationTimestampMs - 1_000L,
            )
        }
        if (progressByAid.isNotEmpty()) {
            database.readingProgressDao().upsertProgressList(progressByAid.values.toList())
        }

        // 6. Migrate search_history.wk8 (`[0keyword][1author]...`), stripping 0/1 prefix cleanly.
        val migratedHistory = parseSearchHistoryEntries(
            rawHistory = source.readSavesRootFileUtf8(SEARCH_HISTORY_FILE_NAME).orEmpty(),
            baseTimestampMs = migrationTimestampMs,
        )
        if (migratedHistory.isNotEmpty()) {
            database.searchHistoryDao().upsertHistoryEntries(migratedHistory)
        }

        return LegacyMigrationReport(
            migratedSettingsCount = parsedSettings.size,
            restoredAccountCredential = restoredAccount,
            migratedBookshelfNovelsCount = orderedBookshelfAids.size,
            migratedCachedNovelsCount = migratedCachedNovelsCount,
            migratedChaptersCount = migratedChaptersCount,
            migratedReadingProgressCount = progressByAid.size,
            migratedSearchHistoryCount = migratedHistory.size,
        )
    }

    private data class ChapterOwnerSnapshot(
        val aid: Int,
        val vid: Int,
        val volumeTitle: String,
        val chapterTitle: String,
    )

    data class LegacyReadSaveV1Record(
        val aid: Int,
        val vid: Int,
        val cid: Int,
        val lineId: Int,
        val wordId: Int,
    )

    data class LegacyReadSaveV0Record(
        val cid: Int,
        val pos: Int,
        val height: Int,
    )

    companion object {
        const val SETTINGS_FILE_NAME = "settings.wk8"
        const val CERT_FILE_NAME = "cert.wk8"
        const val NOTICE_FILE_NAME = "notice.wk8"
        const val BOOKSHELF_FILE_NAME = "bookshelf_local.wk8"
        const val READ_SAVES_V1_FILE_NAME = "read_saves_v1.wk8"
        const val READ_SAVES_V0_FILE_NAME = "read_saves.wk8"
        const val SEARCH_HISTORY_FILE_NAME = "search_history.wk8"
        const val INTRO_SUBFOLDER = "intro"
        const val NOVEL_SUBFOLDER = "novel"
        const val CHAPTER_WAL_BATCH_SIZE = 25

        private val SETTINGS_RECORD_DELIMITER = Regex("\\|\\|\\|\\|")
        private const val SETTINGS_FIELD_DELIMITER = "::::"
        private val DOUBLE_PIPE_DELIMITER = Regex("\\|\\|")

        /**
         * Parses `settings.wk8` (`key::::value||||key2::::value2`).
         * Skips damaged records rather than failing the entire file (matching `SettingsStore.java`).
         */
        fun parseLegacySettingsMap(rawContent: String): Map<String, String> {
            if (rawContent.isBlank()) return emptyMap()
            val settings = LinkedHashMap<String, String>()
            for (record in rawContent.split(SETTINGS_RECORD_DELIMITER)) {
                val fields = record.split(SETTINGS_FIELD_DELIMITER)
                if (fields.size != 2) continue
                val key = fields[0].trim()
                val value = fields[1]
                if (key.isEmpty() || value.isEmpty()) continue
                settings[key] = value
            }
            return settings
        }

        /**
         * Parses `bookshelf_local.wk8` (`aid1||aid2||aid3`), deduplicating while preserving exact
         * shelf order and dropping individual corrupted tokens without aborting.
         */
        fun parseBookshelfAidList(rawContent: String): List<Int> {
            if (rawContent.isBlank()) return emptyList()
            val orderedUniqueAids = LinkedHashSet<Int>()
            for (token in rawContent.split(DOUBLE_PIPE_DELIMITER)) {
                val parsedAid = token.trim().toIntOrNull()
                if (parsedAid != null && parsedAid > 0) {
                    orderedUniqueAids.add(parsedAid)
                }
            }
            return orderedUniqueAids.toList()
        }

        /**
         * Parses `read_saves_v1.wk8` (`aid:vid:cid:lineId:wordId||...`).
         */
        fun parseReadSavesV1(rawContent: String): List<LegacyReadSaveV1Record> {
            if (rawContent.isBlank()) return emptyList()
            val results = ArrayList<LegacyReadSaveV1Record>()
            for (record in rawContent.split(DOUBLE_PIPE_DELIMITER)) {
                val parts = record.trim().split(":")
                if (parts.size != 5) continue
                val aid = parts[0].toIntOrNull() ?: continue
                val vid = parts[1].toIntOrNull() ?: continue
                val cid = parts[2].toIntOrNull() ?: continue
                val lineId = parts[3].toIntOrNull() ?: continue
                val wordId = parts[4].toIntOrNull() ?: continue
                if (aid > 0 && cid > 0) {
                    results.add(LegacyReadSaveV1Record(aid, vid, cid, lineId, wordId))
                }
            }
            return results
        }

        /**
         * Parses `read_saves.wk8` (`cid,,pos,,height||...`).
         */
        fun parseReadSavesV0(rawContent: String): List<LegacyReadSaveV0Record> {
            if (rawContent.isBlank()) return emptyList()
            val results = ArrayList<LegacyReadSaveV0Record>()
            for (record in rawContent.split(DOUBLE_PIPE_DELIMITER)) {
                val parts = record.trim().split(",,")
                if (parts.size != 3) continue
                val cid = parts[0].toIntOrNull() ?: continue
                val pos = parts[1].toIntOrNull() ?: continue
                val height = parts[2].toIntOrNull() ?: continue
                if (cid > 0) {
                    results.add(LegacyReadSaveV0Record(cid, pos, height))
                }
            }
            return results
        }

        /**
         * Parses `search_history.wk8` (`[0keyword][1author]...`), stripping `'0'` (`articlename`)
         * and `'1'` (`author`) prefixes and assigning descending timestamps so index 0 stays newest.
         */
        fun parseSearchHistoryEntries(
            rawHistory: String,
            baseTimestampMs: Long,
        ): List<SearchHistoryEntity> {
            if (rawHistory.isBlank()) return emptyList()
            val entries = ArrayList<SearchHistoryEntity>()
            val seenQueries = HashSet<String>()
            var cursor = 0
            var rankOffset = 0
            while (true) {
                val openBracket = rawHistory.indexOf('[', cursor)
                if (openBracket == -1) break
                val closeBracket = rawHistory.indexOf(']', openBracket + 1)
                if (closeBracket == -1) break

                val rawToken = rawHistory.substring(openBracket + 1, closeBracket)
                cursor = closeBracket + 1
                if (rawToken.isBlank()) continue

                val (searchType, cleanQuery) = when {
                    rawToken.startsWith('1') && rawToken.length > 1 -> {
                        SearchHistoryEntity.SEARCH_TYPE_AUTHOR to rawToken.substring(1).trim()
                    }
                    rawToken.startsWith('0') && rawToken.length > 1 -> {
                        SearchHistoryEntity.SEARCH_TYPE_ARTICLE_NAME to rawToken.substring(1).trim()
                    }
                    else -> {
                        SearchHistoryEntity.SEARCH_TYPE_ARTICLE_NAME to rawToken.trim()
                    }
                }

                if (cleanQuery.isNotEmpty() && seenQueries.add(cleanQuery)) {
                    entries.add(
                        SearchHistoryEntity(
                            query = cleanQuery,
                            searchType = searchType,
                            searchedAtMs = baseTimestampMs - rankOffset,
                        ),
                    )
                    rankOffset++
                }
            }
            return entries
        }

        private fun restoreLegacyUserSessionSafely(rawCert: String?): Boolean {
            if (rawCert.isNullOrBlank()) return false
            return try {
                LightUserSession.decAndSetUserFile(rawCert)
                true
            } catch (_: UnsupportedOperationException) {
                // Public CI `:api-stub` throws UnsupportedOperationException("stub") on LightUserSession.
                false
            } catch (_: Throwable) {
                false
            }
        }
    }
}
