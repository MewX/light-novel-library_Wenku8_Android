package org.mewx.wenku8.global;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.global.api.ChapterInfo;
import org.mewx.wenku8.global.api.VolumeList;

import java.io.File;

/**
 * The volume index cache the readers were rebuilt on top of in Phase 2.1.
 *
 * <p>Since the readers stopped taking a serialized {@code VolumeList} through the Intent and
 * started taking {@code aid} plus {@code vid}, this file is the only thing standing between a
 * chapter tap and an empty screen: {@code Wenku8ReaderActivityV1} calls
 * {@link GlobalConfig#loadCachedVolume} on startup and has nothing else to fall back on. That
 * moved a whole class of failure out of the Binder transaction and into storage, and storage is
 * the part that cannot be tested on the JVM.
 *
 * <p>The pieces either side of the file already have JVM coverage — {@code getVolumeList} and
 * {@code findVolumeByVid} in {@code Wenku8ParserTest}. What has none, and what this covers, is
 * the round trip through a real filesystem: that what was written comes back byte-identical
 * enough to reparse, and that every way it can be missing or broken yields null rather than an
 * exception. Null is the documented ordinary outcome here, so the reader can show a message; a
 * throw would be a crash on the path that opens a chapter.
 *
 * <p><b>This writes into the app's real save folder.</b> It uses a sentinel aid far above any
 * real one so it cannot collide with a novel the device owner actually has, and deletes the file
 * afterwards from both the default and backup storage roots, since which one a write lands in
 * depends on runtime storage settings.
 */
@SmallTest
public class VolumeIndexCacheTest {

    /** Real wenku8 aids are four digits; this cannot collide with a novel on the device. */
    private static final int TEST_AID = 999_000_001;

    private static final int FIRST_VID = 41748;
    private static final int SECOND_VID = 45090;

    /** Trimmed from the fixture in {@code Wenku8ParserTest}, so the shape is one the server sends. */
    private static final String VOLUME_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<package>\n"
            + "<volume vid=\"" + FIRST_VID + "\"><![CDATA[第一卷 告白于苍刻之夜]]>\n"
            + "<chapter cid=\"41749\"><![CDATA[序章]]></chapter>\n"
            + "<chapter cid=\"41750\"><![CDATA[第一章「去对我的『楯』说吧——」]]></chapter>\n"
            + "<chapter cid=\"41751\"><![CDATA[第二章「我真的对你非常感兴趣」]]></chapter>\n"
            + "</volume>\n"
            + "<volume vid=\"" + SECOND_VID + "\"><![CDATA[第二卷 谎言、真相与赤红]]>\n"
            + "<chapter cid=\"45091\"><![CDATA[序章]]></chapter>\n"
            + "</volume>\n"
            + "</package>";

    @Before
    public void removeAnyLeftoverIndex() {
        // A previous run that died mid-test would otherwise make the "missing" cases pass or
        // fail for the wrong reason.
        deleteCachedIndex();
    }

    @After
    public void removeTestIndex() {
        deleteCachedIndex();
    }

    private void deleteCachedIndex() {
        final String relative = GlobalConfig.saveFolderName + File.separator + "intro"
                + File.separator + GlobalConfig.getVolumeIndexFileName(TEST_AID);
        new File(GlobalConfig.getDefaultStoragePath() + relative).delete();
        new File(GlobalConfig.getBackupStoragePath() + relative).delete();
    }

    @Test
    public void testCachedIndexSurvivesTheRoundTripToStorage() {
        assertTrue("could not write the volume index",
                GlobalConfig.cacheVolumeIndex(TEST_AID, VOLUME_XML));

        final VolumeList volume = GlobalConfig.loadCachedVolume(TEST_AID, FIRST_VID);

        assertNotNull("the index was written but did not load back", volume);
        assertEquals(FIRST_VID, volume.vid);
        // Volume and chapter names are CJK and the write path calls String.getBytes() with no
        // explicit charset, so this is really asserting that the platform default round-trips.
        assertEquals("第一卷 告白于苍刻之夜", volume.volumeName);
        assertEquals(3, volume.chapterList.size());

        final ChapterInfo first = volume.chapterList.get(0);
        assertEquals(41749, first.cid);
        assertEquals("序章", first.chapterName);
    }

    @Test
    public void testEachVolumeInTheIndexIsAddressableByVid() {
        GlobalConfig.cacheVolumeIndex(TEST_AID, VOLUME_XML);

        final VolumeList second = GlobalConfig.loadCachedVolume(TEST_AID, SECOND_VID);

        assertNotNull(second);
        assertEquals(SECOND_VID, second.vid);
        assertEquals("第二卷 谎言、真相与赤红", second.volumeName);
    }

    @Test
    public void testMissingIndexReturnsNull() {
        // The case Phase 2.1 called out as the one that could break it: a novel browsed and
        // opened from a list or search that was never added to the bookshelf, so no bookshelf
        // path ever wrote its index.
        assertNull(GlobalConfig.loadCachedVolume(TEST_AID, FIRST_VID));
    }

    @Test
    public void testUnknownVidInAPresentIndexReturnsNull() {
        GlobalConfig.cacheVolumeIndex(TEST_AID, VOLUME_XML);

        assertNull(GlobalConfig.loadCachedVolume(TEST_AID, 1));
    }

    @Test
    public void testUnparseableIndexReturnsNullRatherThanThrowing() {
        // Root cause 4's shape reaching this file: a truncated or half-written cache entry. The
        // reader has to get null and show a message, not take an exception on the chapter-open
        // path.
        assertTrue(GlobalConfig.cacheVolumeIndex(TEST_AID, "<package><volume vid=\"41748\">"));

        assertNull(GlobalConfig.loadCachedVolume(TEST_AID, FIRST_VID));
    }

    @Test
    public void testEmptyIndexReturnsNull() {
        assertTrue(GlobalConfig.cacheVolumeIndex(TEST_AID, ""));

        assertNull(GlobalConfig.loadCachedVolume(TEST_AID, FIRST_VID));
    }

    @Test
    public void testRecachingReplacesTheEarlierIndex() {
        // FetchInfoAsyncTask rewrites the index whenever one arrives from the network, which is
        // what refreshes a stale bookshelf copy. If a rewrite appended, or left the old bytes
        // behind after a shorter one, the parse would drift.
        GlobalConfig.cacheVolumeIndex(TEST_AID, VOLUME_XML);

        final String shorter = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<package>\n"
                + "<volume vid=\"" + FIRST_VID + "\"><![CDATA[改名后的第一卷]]>\n"
                + "<chapter cid=\"41749\"><![CDATA[序章]]></chapter>\n"
                + "</volume>\n"
                + "</package>";
        assertTrue(GlobalConfig.cacheVolumeIndex(TEST_AID, shorter));

        final VolumeList volume = GlobalConfig.loadCachedVolume(TEST_AID, FIRST_VID);
        assertNotNull(volume);
        assertEquals("改名后的第一卷", volume.volumeName);
        assertEquals(1, volume.chapterList.size());

        // The volume the first write had and the second did not must be gone, not lingering.
        assertNull(GlobalConfig.loadCachedVolume(TEST_AID, SECOND_VID));
    }
}
