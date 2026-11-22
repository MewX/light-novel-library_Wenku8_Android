package org.mewx.wenku8.activity;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The vertical reader's startup and lifecycle paths.
 *
 * <p>This screen had no automated coverage at all — 0 of 185 lines — while being the reader that
 * Phase 1 item 13's crash was reachable from. The loader underneath it is now well tested by
 * {@code ReadSavesV0Test}; the screen that calls it was not tested at any level, which is the gap
 * these close.
 *
 * <p>Assertions here are deliberately structural — the Activity reaches RESUMED and does not
 * finish itself — for the same reason {@link NovelInfoActivityLifecycleTest} gives: what failure
 * looks like on screen is untranslated and inconsistent today, and pinning the current appearance
 * down would make fixing it harder rather than safer.
 *
 * <p>Sentinel ids sit far above any real novel so nothing here can collide with the device
 * owner's library, and every file this writes is removed afterwards.
 */
@LargeTest
public class VerticalReaderActivityLifecycleTest {

    /** Distinct from the ids {@code ReaderRecreationTest} uses, so the two cannot interfere. */
    private static final int TEST_AID = 999_000_005;
    private static final int TEST_CID = 999_000_201;

    private static final String CHAPTER_TEXT =
            "    垂直阅读器的测试章节，用来确认它可以完全离线打开。  \r\n"
            + "  \r\n"
            + "    「这是第二段。」  \r\n"
            + "  \r\n"
            + "    这是第三段，稍微长一点，好让排版有东西可做。  ";

    private static final String READ_SAVES_FILE = "read_saves.wk8";

    /**
     * The device owner's real reading positions, put back in {@link #restoreTheDeviceState()}.
     *
     * <p>These tests run against the app's real save folder because no seam exists to redirect it
     * and the storage layout is frozen. Restoring is therefore not optional: this file is the one
     * piece of save data a user cannot reconstruct.
     */
    private String realReadSaves;

    @Before
    public void requireAnInteractiveDeviceAndPlantFixture() throws IOException {
        InteractiveDevice.require();

        realReadSaves = readSaveFile();

        // A previous run that died mid-test would otherwise decide these cases for the wrong
        // reason, exactly as ReaderRecreationTest guards against.
        deleteChapterFixture();
        assertTrue("could not write the chapter text",
                GlobalConfig.writeFullFileIntoSaveFolder("novel", TEST_CID + ".xml", CHAPTER_TEXT));
    }

    @After
    public void restoreTheDeviceState() throws IOException {
        deleteChapterFixture();

        deleteSaveFile();
        if (realReadSaves != null) {
            writeSaveFile(realReadSaves);
        }

        // The parsed positions live in a static that outlives any one test, so leaving it holding
        // this test's fixture would follow the process into whatever runs next.
        GlobalConfig.loadReadSaves();
    }

    private static Intent readerIntent() {
        final Context context = ApplicationProvider.getApplicationContext();
        final Intent intent = new Intent(context, VerticalReaderActivity.class);
        intent.putExtra("aid", TEST_AID);
        intent.putExtra("cid", TEST_CID);
        intent.putExtra("from", "fav");
        return intent;
    }

    /**
     * Opening the reader on a chapter that is already cached.
     *
     * <p>Launching at all is most of the assertion. This Activity builds its whole view tree from
     * the parsed chapter during startup, so a reader that cannot start is the failure worth
     * catching, and nothing exercised that before.
     */
    @Test
    public void theVerticalReaderOpensFromACachedChapter() {
        try (ActivityScenario<VerticalReaderActivity> scenario =
                     ActivityScenario.launch(readerIntent())) {
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(
                    "the reader closed itself on a chapter it could read",
                    activity.isFinishing()));
        }
    }

    /** Destroyed and rebuilt, which is when a half-set field or a stale static shows itself. */
    @Test
    public void theVerticalReaderSurvivesRecreation() {
        try (ActivityScenario<VerticalReaderActivity> scenario =
                     ActivityScenario.launch(readerIntent())) {
            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(
                    "the reader finished during recreation", activity.isFinishing()));
        }
    }

    /** Twice, because a rebuild that half-restores often survives one pass and fails the next. */
    @Test
    public void theVerticalReaderSurvivesRepeatedRecreation() {
        try (ActivityScenario<VerticalReaderActivity> scenario =
                     ActivityScenario.launch(readerIntent())) {
            scenario.recreate();
            scenario.recreate();

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    /**
     * Backgrounded and brought back.
     *
     * <p>Worth its own case here rather than only in the detail-screen tests: moving to CREATED
     * runs {@code onPause}, and this reader writes the reading position from {@code onPause}. That
     * is both the common path and the one Phase 1 item 11 identified as able to leave a truncated
     * record behind.
     */
    @Test
    public void theVerticalReaderSurvivesBeingBackgrounded() {
        try (ActivityScenario<VerticalReaderActivity> scenario =
                     ActivityScenario.launch(readerIntent())) {
            scenario.moveToState(Lifecycle.State.CREATED);
            scenario.moveToState(Lifecycle.State.RESUMED);

            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(activity.isFinishing()));
        }
    }

    /**
     * The reader opens on a chapter whose stored position is unreadable.
     *
     * <p>This is Phase 1 item 13 seen from the screen rather than from the loader: a damaged record
     * in {@code read_saves.wk8} used to throw {@code NumberFormatException} out of
     * {@code loadReadSaves}, and nothing between here and there catches it, so the reader crashed
     * on opening the chapter.
     *
     * <p><b>What this does and does not prove.</b> {@code getReadSavesRecord} parses the file only
     * when its static is still null, and that static outlives a single test, so this cannot rely on
     * the Activity being the thing that triggers the parse — it reloads explicitly first. The parse
     * guard itself is owned by {@code ReadSavesV0Test}, which drives the loader directly. What is
     * added here is the half that test cannot reach: that a corrupt record is dropped to a
     * position of 0 rather than to something the reader will then try to scroll to, and that the
     * screen opens normally with one present.
     */
    @Test
    public void theVerticalReaderOpensDespiteACorruptReadingPosition() throws IOException {
        deleteSaveFile();
        writeSaveFile(TEST_CID + ",,not-a-position,,18481");
        GlobalConfig.loadReadSaves();

        assertEquals("a record that cannot be parsed should read back as no position at all",
                0, GlobalConfig.getReadSavesRecord(TEST_CID, 1000));

        try (ActivityScenario<VerticalReaderActivity> scenario =
                     ActivityScenario.launch(readerIntent())) {
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(
                    "the reader closed itself over a damaged reading position",
                    activity.isFinishing()));
        }
    }

    /**
     * A chapter with nothing cached and no reachable server.
     *
     * <p>The reader reports the failure rather than closing, the same contract the detail screen
     * holds. This is the offline case and the case of a cache file deleted underneath the app.
     */
    @Test
    public void theVerticalReaderStaysOpenWithNoCachedChapter() {
        deleteChapterFixture();

        try (ActivityScenario<VerticalReaderActivity> scenario =
                     ActivityScenario.launch(readerIntent())) {
            assertEquals(Lifecycle.State.RESUMED, scenario.getState());
            scenario.onActivity(activity -> assertFalse(
                    "the reader closed itself instead of reporting the missing chapter",
                    activity.isFinishing()));
        }
    }

    // ---- fixture helpers -------------------------------------------------------------------
    //
    // These write through GlobalConfig's public path helpers rather than through the
    // SaveFileFixture the storage tests share, which is package-private to org.mewx.wenku8.global
    // and deliberately kept that way. Both save roots are probed in the app's own order, because a
    // test process resolves the default root differently from the running app -- see the
    // storage-root trap in STABILITY_PLAN.md.

    private static File[] saveFileCandidates() {
        return new File[]{
                new File(GlobalConfig.getFirstFullSaveFilePath() + READ_SAVES_FILE),
                new File(GlobalConfig.getSecondFullSaveFilePath() + READ_SAVES_FILE),
        };
    }

    private static String readSaveFile() throws IOException {
        for (File candidate : saveFileCandidates()) {
            if (candidate.isFile()) {
                return new String(Files.readAllBytes(candidate.toPath()), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static void writeSaveFile(String content) throws IOException {
        for (File candidate : saveFileCandidates()) {
            final File parent = candidate.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                continue;
            }
            try {
                Files.write(candidate.toPath(), content.getBytes(StandardCharsets.UTF_8));
                return;
            } catch (IOException ignored) {
                // Try the other root; only both failing is a problem worth reporting.
            }
        }
        throw new IOException("neither save root accepted " + READ_SAVES_FILE);
    }

    private static void deleteSaveFile() {
        for (File candidate : saveFileCandidates()) {
            //noinspection ResultOfMethodCallIgnored
            candidate.delete();
        }
    }

    private static void deleteChapterFixture() {
        final String relative = GlobalConfig.saveFolderName + File.separator + "novel"
                + File.separator + TEST_CID + ".xml";
        //noinspection ResultOfMethodCallIgnored
        new File(GlobalConfig.getDefaultStoragePath() + relative).delete();
        //noinspection ResultOfMethodCallIgnored
        new File(GlobalConfig.getBackupStoragePath() + relative).delete();
    }
}
