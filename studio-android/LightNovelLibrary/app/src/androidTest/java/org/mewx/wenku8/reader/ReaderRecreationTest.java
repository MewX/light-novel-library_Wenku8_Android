package org.mewx.wenku8.reader;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.reader.activity.Wenku8ReaderActivityV1;

import java.io.File;

/**
 * What happens to the reader when the system destroys and rebuilds it — manual case 6, and the
 * live risk Phase 2.1 introduced.
 *
 * <p>Phase 2.1 stopped passing a serialized {@code VolumeList} through the Intent, because a long
 * series overflowed the Binder transaction buffer, and made the reader rebuild the volume from
 * {@code aid} plus {@code vid} on every {@code onCreate}. That trade is what these tests check.
 * It buys survival across process death, since ints are restored where a large payload could not
 * be; it costs a dependency on the cached index still being there, and the index is deleted when a
 * novel leaves the bookshelf. So a reader the system restores long afterwards can find nothing
 * left to read, and must say so rather than crash on the path that opens a chapter.
 *
 * <p>Device-only, and not merely by convention: the whole point is the real Activity lifecycle
 * plus a real filesystem underneath it. The fixture plants both a volume index and the chapter
 * text so the reader opens without a network — which also keeps these tests from depending on
 * {@code api/}.
 *
 * <p><b>This writes into the app's real save folder</b>, using a sentinel aid far above any real
 * one, and deletes from both storage roots afterwards. See {@code VolumeIndexCacheTest} for why
 * both: which root a write lands in depends on runtime storage settings.
 */
@LargeTest
public class ReaderRecreationTest {

    // Every one of these has to be outside the real range, not just the aid. The chapter files
    // are named by cid alone -- saves/novel/<cid>.xml -- so a cid that a real novel could also
    // have would make the cleanup below delete a chapter the device owner had downloaded. Real
    // aids are four digits and real cids are five to six, so the 999_xxx_xxx range is clear of
    // both, and it is the same sentinel range VolumeIndexCacheTest uses.
    private static final int TEST_AID = 999_000_002;
    private static final int TEST_VID = 999_000_101;
    private static final int FIRST_CID = 999_000_102;
    private static final int SECOND_CID = 999_000_103;

    private static final String VOLUME_NAME = "第一卷 测试用卷";

    private static final String VOLUME_XML = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<package>\n"
            + "<volume vid=\"" + TEST_VID + "\"><![CDATA[" + VOLUME_NAME + "]]>\n"
            + "<chapter cid=\"" + FIRST_CID + "\"><![CDATA[第一章 开始]]></chapter>\n"
            + "<chapter cid=\"" + SECOND_CID + "\"><![CDATA[第二章 结束]]></chapter>\n"
            + "</volume>\n"
            + "</package>";

    /** Plain text is what the parser takes; the shape mirrors a real downloaded chapter. */
    private static final String CHAPTER_TEXT =
            "    测试用的章节内容，用来确认阅读器可以完全离线打开。  \r\n"
            + "  \r\n"
            + "    「这是第二段。」  \r\n"
            + "  \r\n"
            + "    这是第三段，稍微长一点，好让分页有东西可做。  ";

    /**
     * These tests need an awake, unlocked device, and say so rather than letting it look like an
     * app defect. See {@link InteractiveDevice} for why that distinction is worth the check.
     */
    // One @Before rather than two: JUnit 4 does not order them within a class, and the device
    // check has to be the thing that speaks first when it is the thing that is wrong.
    @Before
    public void requireAnInteractiveDeviceAndPlantFixture() {
        InteractiveDevice.require();

        // A previous run that died mid-test would otherwise decide these cases for the wrong
        // reason -- a leftover index makes a "missing index" case pass spuriously.
        deleteFixture();
        assertTrue("could not write the volume index",
                GlobalConfig.cacheVolumeIndex(TEST_AID, VOLUME_XML));
        assertTrue("could not write the chapter text",
                GlobalConfig.writeFullFileIntoSaveFolder("novel", FIRST_CID + ".xml", CHAPTER_TEXT));

        // If the fixture itself does not load, every assertion below would be meaningless.
        assertNotNull("fixture index did not load back",
                GlobalConfig.loadCachedVolume(TEST_AID, TEST_VID));
    }

    @After
    public void removeFixture() {
        deleteFixture();
    }

    private void deleteFixture() {
        deleteFromBothRoots("intro" + File.separator + GlobalConfig.getVolumeIndexFileName(TEST_AID));
        deleteFromBothRoots("novel" + File.separator + FIRST_CID + ".xml");
        deleteFromBothRoots("novel" + File.separator + SECOND_CID + ".xml");
    }

    private void deleteFromBothRoots(String relativeToSaveFolder) {
        final String relative = GlobalConfig.saveFolderName + File.separator + relativeToSaveFolder;
        new File(GlobalConfig.getDefaultStoragePath() + relative).delete();
        new File(GlobalConfig.getBackupStoragePath() + relative).delete();
    }

    private static void deleteCachedIndexOnly() {
        final String relative = GlobalConfig.saveFolderName + File.separator + "intro"
                + File.separator + GlobalConfig.getVolumeIndexFileName(TEST_AID);
        new File(GlobalConfig.getDefaultStoragePath() + relative).delete();
        new File(GlobalConfig.getBackupStoragePath() + relative).delete();
    }

    private static Intent readerIntent(int aid, int vid, int cid) {
        final Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = new Intent(context, Wenku8ReaderActivityV1.class);
        intent.putExtra("aid", aid);
        intent.putExtra("vid", vid);
        intent.putExtra("cid", cid);
        // "fav" is FromLocal, which is what makes the reader prefer the planted chapter text and
        // leaves the network out of these tests entirely.
        intent.putExtra("from", "fav");
        return intent;
    }

    /**
     * The core of case 6: destroy and rebuild, and the reader must come back with the same volume.
     * The title assertion is the meaningful part — it can only be right if {@code onCreate}
     * reloaded the volume from the cached index, which is exactly the Phase 2.1 contract.
     */
    @Test
    public void theReaderSurvivesRecreation() {
        try (ActivityScenario<Wenku8ReaderActivityV1> scenario =
                     ActivityScenario.launch(readerIntent(TEST_AID, TEST_VID, FIRST_CID))) {
            scenario.onActivity(activity -> {
                assertFalse("finished before it was recreated", activity.isFinishing());
                assertNotNull(activity.getSupportActionBar());
                assertEquals(VOLUME_NAME, String.valueOf(activity.getSupportActionBar().getTitle()));
            });

            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> {
                assertFalse("finished during recreation", activity.isFinishing());
                assertNotNull(activity.getSupportActionBar());
                assertEquals("the volume was not rebuilt from the cached index",
                        VOLUME_NAME, String.valueOf(activity.getSupportActionBar().getTitle()));
            });
        }
    }

    /**
     * Twice, because a rebuild that leaks or half-restores state often survives the first pass and
     * fails the second — and the static reader state this codebase carries is exactly that shape.
     */
    @Test
    public void theReaderSurvivesBeingRecreatedRepeatedly() {
        try (ActivityScenario<Wenku8ReaderActivityV1> scenario =
                     ActivityScenario.launch(readerIntent(TEST_AID, TEST_VID, FIRST_CID))) {
            scenario.recreate();
            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> {
                assertFalse(activity.isFinishing());
                assertEquals(VOLUME_NAME, String.valueOf(activity.getSupportActionBar().getTitle()));
            });
        }
    }

    /**
     * The failure Phase 2.1 actually risks: the novel leaves the bookshelf while the reader sits
     * in the background, taking the cached index with it, and then the system rebuilds the reader.
     * There is no volume to rebuild from, so it must close itself rather than dereference a null.
     *
     * <p><b>Why this is a fresh launch rather than {@code recreate()}.</b> Two reasons, and the
     * first is the interesting one. The reader takes every piece of this state from the Intent
     * extras and from disk — it never reads {@code savedInstanceState} for {@code aid},
     * {@code vid} or {@code cid} — so a launch carrying the same extras reproduces a system
     * restore exactly. That is a property of the Phase 2.1 design, not a convenience. The second
     * reason is mechanical: {@code recreate()} waits for RESUMED, which an Activity that finishes
     * itself in {@code onCreate} can never reach, so it could not express this case at all.
     */
    @Test
    public void aReaderRestoredWithoutItsIndexClosesInsteadOfCrashing() {
        deleteCachedIndexOnly();
        assertNull("the index should be gone before the rebuild",
                GlobalConfig.loadCachedVolume(TEST_AID, TEST_VID));

        // Launching at all is most of the point: an unguarded null in onCreate would surface as a
        // crashed instrumentation run, not as a quiet pass.
        try (ActivityScenario<Wenku8ReaderActivityV1> scenario =
                     ActivityScenario.launch(readerIntent(TEST_AID, TEST_VID, FIRST_CID))) {
            assertEquals("a reader with no index should have closed itself",
                    Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    /**
     * An index that exists but holds no such volume. Distinct from a missing file, and reachable:
     * a stale index refreshed from the network can drop a volume that used to be there.
     */
    @Test
    public void aReaderGivenAVidNotInTheIndexClosesInsteadOfCrashing() {
        try (ActivityScenario<Wenku8ReaderActivityV1> scenario =
                     ActivityScenario.launch(readerIntent(TEST_AID, TEST_VID + 999, FIRST_CID))) {
            assertEquals("a reader with no such volume should have closed itself",
                    Lifecycle.State.DESTROYED, scenario.getState());
        }
    }

    /**
     * A chapter listed in the index but never downloaded. `ChapterContentLoader` falls through to
     * the network; whether that succeeds depends on the device, and both endings are correct. What
     * would not be correct is a crash, and launching without one is the assertion.
     *
     * <p>Deliberately not asserting a specific state: pinning one would make the test pass or fail
     * on whether the device happens to have a network, which is not what it is here to check.
     */
    @Test
    public void aChapterWithNoDownloadedCopyDoesNotCrashTheReader() {
        // SECOND_CID is in the index but was never written to the novel folder.
        try (ActivityScenario<Wenku8ReaderActivityV1> scenario =
                     ActivityScenario.launch(readerIntent(TEST_AID, TEST_VID, SECOND_CID))) {
            assertNotNull(scenario.getState());
        }
    }

    /** The fixture the other tests depend on, asserted directly so a failure here is unambiguous. */
    @Test
    public void theFixtureDescribesTheVolumeTheTestsAssume() {
        final VolumeList volume = GlobalConfig.loadCachedVolume(TEST_AID, TEST_VID);

        assertNotNull(volume);
        assertEquals(TEST_VID, volume.vid);
        assertEquals(VOLUME_NAME, volume.volumeName);
        assertEquals(2, volume.chapterList.size());
        assertEquals(FIRST_CID, volume.chapterList.get(0).cid);
        assertEquals(SECOND_CID, volume.chapterList.get(1).cid);
    }
}
