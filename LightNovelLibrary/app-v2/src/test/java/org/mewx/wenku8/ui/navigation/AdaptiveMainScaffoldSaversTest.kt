package org.mewx.wenku8.ui.navigation

import org.mewx.wenku8.domain.model.DiscoveryNovelCardItem
import org.mewx.wenku8.ui.detail.ReaderLaunchRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Round-trip tests for the scene-state save/restore codecs introduced so the reader scene and the
 * two-pane detail seed survive Activity recreations (split-screen entry / resize drags,
 * fold-unfold). `MainActivity` declares no `android:configChanges`, so every window-size change
 * recreates the Activity; before these Savers the reader scene silently unloaded back to the home
 * shell and the detail pane rendered a bare "AID #x" shell with no seed content.
 */
class AdaptiveMainScaffoldSaversTest {

    @Test
    fun `reader launch request round trips`() {
        val request = ReaderLaunchRequest(
            aid = 7,
            vid = 2,
            cid = 33,
            novelTitle = "玩玩的恋爱关系",
            volumeTitle = "第四卷",
            chapterTitle = "蜜瓜特典",
            startWithTts = true,
            initialParagraphIndex = 4,
            initialCharOffset = 9,
        )
        val saved = saveReaderLaunchRequest(request)
        assertEquals(9, saved.size)
        assertEquals(request, restoreReaderLaunchRequest(saved))
    }

    @Test
    fun `reader launch request with default tts and anchor round trips`() {
        val request = ReaderLaunchRequest(
            aid = 1,
            vid = 1,
            cid = 1,
            novelTitle = "t",
            volumeTitle = "v",
            chapterTitle = "c",
        )
        val saved = saveReaderLaunchRequest(request)
        assertEquals(9, saved.size)
        assertEquals(request, restoreReaderLaunchRequest(saved))
    }

    @Test
    fun `null reader request round trips as empty list`() {
        assertTrue(saveReaderLaunchRequest(null).isEmpty())
        assertEquals(null, restoreReaderLaunchRequest(emptyList()))
    }

    @Test
    fun `reader launch request payload positions are typed`() {
        // Guards against field-order drift: every positional index must map back to the same
        // typed value it was saved with.
        val saved = saveReaderLaunchRequest(
            ReaderLaunchRequest(
                aid = 10, vid = 20, cid = 30,
                novelTitle = "n", volumeTitle = "v", chapterTitle = "c",
                startWithTts = false, initialParagraphIndex = -1, initialCharOffset = 0,
            ),
        )
        assertEquals(10, saved[0])
        assertEquals(20, saved[1])
        assertEquals(30, saved[2])
        assertEquals("n", saved[3])
        assertEquals("v", saved[4])
        assertEquals("c", saved[5])
        assertEquals(false, saved[6])
        assertEquals(-1, saved[7])
        assertEquals(0, saved[8])
    }

    @Test
    fun `detail seed item round trips`() {
        val seed = DiscoveryNovelCardItem(
            aid = 128,
            title = "绝不选择你",
            author = "白鸟かおる",
            bookStatus = "连载",
            lastUpdate = "2026-11-22",
            tags = listOf("校园", "恋爱"),
            introShort = "简介一行",
            coverUrl = "https://img.example/128s.jpg",
            inLocalBookshelf = true,
            isHydratingMetadata = false,
        )
        val saved = saveDetailSeedItem(seed)
        assertEquals(10, saved.size)
        val restored = requireNotNull(restoreDetailSeedItem(saved))
        assertEquals(seed, restored)
        assertEquals(listOf("校园", "恋爱"), restored.tags)
    }

    @Test
    fun `null detail seed round trips as empty list`() {
        assertTrue(saveDetailSeedItem(null).isEmpty())
        assertEquals(null, restoreDetailSeedItem(emptyList()))
    }

    @Test
    fun `truncated legacy payloads restore with defaulted tail fields`() {
        // Android restores saved instance state across app updates: a bundle written by an older
        // version (before the optional tail fields existed) must restore via defaults, not crash.
        val legacyRequest = listOf<Any>(
            10, 20, 30, "n", "v", "c",
        )
        val restoredRequest = requireNotNull(restoreReaderLaunchRequest(legacyRequest))
        assertEquals(10, restoredRequest.aid)
        assertEquals(false, restoredRequest.startWithTts)
        assertEquals(-1, restoredRequest.initialParagraphIndex)
        assertEquals(0, restoredRequest.initialCharOffset)

        val legacySeed = listOf<Any>(
            5, "狼与香辛料", "支仓冻砂", "连载", "2026-11-01",
            ArrayList(listOf("奇幻")), "简介", "https://img.example/5s.jpg",
        )
        val restoredSeed = requireNotNull(restoreDetailSeedItem(legacySeed))
        assertEquals(5, restoredSeed.aid)
        assertEquals(false, restoredSeed.inLocalBookshelf)
        assertEquals(false, restoredSeed.isHydratingMetadata)
    }

    @Test
    fun `oversized payloads from future versions restore and ignore extra entries`() {
        val futureRequest = saveReaderLaunchRequest(
            ReaderLaunchRequest(
                aid = 1, vid = 2, cid = 3,
                novelTitle = "n", volumeTitle = "v", chapterTitle = "c",
            ),
        ) + listOf<Any>("future-field", 42)
        val restoredRequest = requireNotNull(restoreReaderLaunchRequest(futureRequest))
        assertEquals(3, restoredRequest.cid)
        assertEquals(false, restoredRequest.startWithTts)

        val futureSeed = saveDetailSeedItem(
            DiscoveryNovelCardItem(
                aid = 6, title = "t", author = "a", bookStatus = "s", lastUpdate = "u",
                tags = emptyList(), introShort = "i", coverUrl = "url",
            ),
        ) + listOf<Any>(true)
        val restoredSeed = requireNotNull(restoreDetailSeedItem(futureSeed))
        assertEquals(false, restoredSeed.inLocalBookshelf)
    }

    @Test
    fun `undersized payloads below the required prefix are rejected`() {
        // A corrupted or mismatched bundle must drop the scene instead of throwing during
        // restoration (which would crash on Activity recreate).
        assertEquals(null, restoreReaderLaunchRequest(listOf<Any>(1, 2, 3)))
        assertEquals(null, restoreDetailSeedItem(listOf<Any>(1, "t", "a")))
    }
}
