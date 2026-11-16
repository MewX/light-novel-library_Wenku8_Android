package org.mewx.wenku8.v2.data.repository

import androidx.room.withTransaction
import org.mewx.wenku8.api.Wenku8API
import org.mewx.wenku8.v2.data.local.db.Wenku8Database
import org.mewx.wenku8.v2.data.local.db.entity.ChapterEntity
import org.mewx.wenku8.v2.data.local.db.entity.NovelEntity
import org.mewx.wenku8.v2.data.local.db.entity.VolumeEntity
import org.mewx.wenku8.v2.util.DigestUtils

/**
 * Domain representation of a parsed volume and its ordered chapters from `do=list` XML.
 */
data class ParsedVolumeToc(
    val vid: Int,
    val volumeTitle: String,
    val chapters: List<ParsedChapterToc>,
)

data class ParsedChapterToc(
    val cid: Int,
    val chapterTitle: String,
)

/**
 * Result of an atomic novel TOC synchronization (`syncNovelIndexAtomically`).
 */
sealed interface TocSyncResult {
    val tocVersion: Long

    data class Updated(
        override val tocVersion: Long,
        val healedReadingProgressVid: Int?,
    ) : TocSyncResult

    data class Unchanged(override val tocVersion: Long) : TocSyncResult

    data class SkippedStaleResponse(override val tocVersion: Long) : TocSyncResult
}

/**
 * Coordinates atomic, race-free Table-of-Contents (`do=list`) updates in `wenku8_v2.db`
 * (`docs/designs/v2/09-room-database-and-backup-archive-design.md` §4).
 *
 * Key invariants enforced inside a single SQLite WAL transaction (`database.withTransaction`):
 * 1. **Stale Response Guard**: If an older slow network call finishes *after* a newer refresh has
 *    already committed (`current.metaSyncedAtMs > requestStartedAtMs`), the stale response is
 *    rejected so an older chapter list can never roll back newly added chapters.
 * 2. **Normalized Digest Fast-Path**: Computes a canonical SHA-256 over `(vid, volumeTitle, cid,
 *    chapterTitle)` tuples; if identical for the same `AppLanguage`, skips table writes and only
 *    touches `meta_synced_at_ms`.
 * 3. **Zero Cascade Loss**: Uses `@Upsert` (`ON CONFLICT DO UPDATE`) and targeted obsolete-ID
 *    pruning. Because `chapter_contents`, `reading_progress`, and `bookmarks_notes` reference
 *    `novels(aid)` rather than `volumes` or `chapters`, reorganizing chapters across volumes never
 *    deletes downloaded chapter text or user bookmarks.
 * 4. **Automatic `vid` Healing**: If the user's active reading position (`reading_progress.cid`)
 *    moved from a web-serialization volume into a newly compiled Bunkobon volume, automatically
 *    updates `reading_progress.vid` and `volume_title` to the new owner volume.
 */
class NovelTocSyncRepository(
    private val database: Wenku8Database,
) {
    private val novelDao = database.novelDao()
    private val tocDao = database.tocDao()
    private val readingProgressDao = database.readingProgressDao()

    suspend fun syncNovelIndexAtomically(
        aid: Int,
        language: Wenku8API.AppLanguage,
        requestStartedAtMs: Long,
        parsedVolumes: List<ParsedVolumeToc>,
        fallbackNovelTitle: String = "AID #$aid",
    ): TocSyncResult {
        val normalizedDigest = computeNormalizedTocSha256(parsedVolumes)

        return database.withTransaction {
            val currentHeader = novelDao.getSyncHeader(aid)

            // 1. Reject out-of-order stale network responses.
            if (currentHeader != null && currentHeader.metaSyncedAtMs > requestStartedAtMs) {
                return@withTransaction TocSyncResult.SkippedStaleResponse(currentHeader.tocVersion)
            }

            // 2. Fast-path no-op when normalized TOC hash and script language match.
            if (currentHeader != null &&
                currentHeader.tocSha256.isNotEmpty() &&
                currentHeader.tocSha256 == normalizedDigest &&
                currentHeader.contentLang == language.name
            ) {
                novelDao.touchMetaSyncedAt(aid = aid, syncedAtMs = requestStartedAtMs)
                return@withTransaction TocSyncResult.Unchanged(currentHeader.tocVersion)
            }

            // Ensure parent NOVELS(aid) row exists so child VolumeEntity / ChapterEntity foreign keys
            // never fail if do=list finishes before do=meta.
            if (currentHeader == null) {
                novelDao.upsertNovel(
                    NovelEntity(
                        aid = aid,
                        contentLang = language.name,
                        title = fallbackNovelTitle,
                        metaSyncedAtMs = requestStartedAtMs,
                    ),
                )
            }

            // 3. Upsert all live volumes and chapters in place (NO DELETE+INSERT).
            val volumeEntities = parsedVolumes.mapIndexed { volumeOrder, volume ->
                VolumeEntity(
                    vid = volume.vid,
                    aid = aid,
                    volumeOrder = volumeOrder,
                    volumeTitle = volume.volumeTitle,
                )
            }
            val chapterEntities = parsedVolumes.flatMap { volume ->
                volume.chapters.mapIndexed { chapterOrder, chapter ->
                    ChapterEntity(
                        cid = chapter.cid,
                        vid = volume.vid,
                        aid = aid,
                        chapterOrder = chapterOrder,
                        chapterTitle = chapter.chapterTitle,
                    )
                }
            }

            tocDao.upsertVolumes(volumeEntities)
            tocDao.upsertChapters(chapterEntities)

            // 4. Prune chapters/volumes removed upstream (CHAPTER_CONTENTS stays intact via FK to NOVELS!).
            val liveVolumeIds = volumeEntities.mapTo( LinkedHashSet() ) { it.vid }
            val liveChapterIds = chapterEntities.mapTo( LinkedHashSet() ) { it.cid }
            tocDao.pruneObsoleteChapters(aid = aid, liveCids = liveChapterIds)
            tocDao.pruneObsoleteVolumes(aid = aid, liveVids = liveVolumeIds)

            // 5. Auto-heal READING_PROGRESS.vid if saved cid moved into a recompiled volume!
            var healedVid: Int? = null
            val savedProgress = readingProgressDao.getProgressForNovel(aid)
            if (savedProgress != null) {
                val updatedOwner = tocDao.findVolumeOwnerForChapter(aid = aid, cid = savedProgress.cid)
                if (updatedOwner != null &&
                    (updatedOwner.vid != savedProgress.vid ||
                        updatedOwner.volumeTitle != savedProgress.volumeTitle)
                ) {
                    readingProgressDao.healVolumeOwner(
                        aid = aid,
                        newVid = updatedOwner.vid,
                        newVolumeTitle = updatedOwner.volumeTitle,
                        newChapterTitle = updatedOwner.chapterTitle,
                    )
                    healedVid = updatedOwner.vid
                }
            }

            // 6. Bump monotonic toc_version.
            val nextTocVersion = (currentHeader?.tocVersion ?: 0L) + 1L
            novelDao.updateTocVersion(
                aid = aid,
                tocVersion = nextTocVersion,
                tocSha256 = normalizedDigest,
                contentLang = language.name,
                syncedAtMs = requestStartedAtMs,
            )

            TocSyncResult.Updated(
                tocVersion = nextTocVersion,
                healedReadingProgressVid = healedVid,
            )
        }
    }

    companion object {
        fun computeNormalizedTocSha256(parsedVolumes: List<ParsedVolumeToc>): String {
            val canonicalBuilder = StringBuilder()
            for (volume in parsedVolumes) {
                canonicalBuilder
                    .append("V:")
                    .append(volume.vid)
                    .append(':')
                    .append(volume.volumeTitle.trim())
                    .append('\n')
                for (chapter in volume.chapters) {
                    canonicalBuilder
                        .append("C:")
                        .append(chapter.cid)
                        .append(':')
                        .append(chapter.chapterTitle.trim())
                        .append('\n')
                }
            }
            return DigestUtils.sha256Hex(canonicalBuilder.toString())
        }
    }
}
