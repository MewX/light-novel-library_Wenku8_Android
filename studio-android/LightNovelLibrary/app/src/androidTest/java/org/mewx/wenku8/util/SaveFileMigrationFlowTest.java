package org.mewx.wenku8.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.net.Uri;

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
 * The external-to-internal save migration: its completion flag, and the copy that moves one file.
 *
 * <p>Before this, the flow that touches <i>all</i> of a user's data on upgrade was covered by two
 * tests, both asserting the shape of the string {@code getInternalSavePath()} returns. Nothing
 * exercised the flag that decides whether the migration runs again, and nothing exercised the copy.
 *
 * <p><b>The completion flag is the dangerous part to test, and why every case here restores it.</b>
 * {@code .migration_completed} is a real file in the app's internal save directory, and
 * {@code GlobalConfig.loadAllSetting()} feeds {@code migrationCompleted()} straight into
 * {@code StorageRoots.setInternalOnly}. Flipping it does not just change this class's view of the
 * world — it changes which directory the whole app reads and writes, for the rest of the process
 * and for the next launch, because the flag is on disk. {@link #restoreMigrationState()} puts the
 * file back exactly as it was found and reloads settings so the in-memory decision matches.
 *
 * <p><b>What is deliberately not covered.</b> {@code generateMigrationPlan()} takes a different
 * branch when an external path override is set, resolving it through
 * {@code DocumentFile.fromTreeUri}, which needs a real Storage Access Framework tree Uri granted by
 * the user. A test cannot mint one, so that branch stays uncovered and is called out here rather
 * than papered over with a file Uri that would exercise something else. The unoverridden branch
 * only lists whatever is really at {@code /sdcard/wenku8/}, which is nothing on a migrated device.
 */
@LargeTest
public class SaveFileMigrationFlowTest {

    private static final String SIGNAL_FILE_NAME = ".migration_completed";
    private static final String SENTINEL_DIR = "999-migration-test";

    /** Whether the device had already completed migration, restored afterwards. */
    private boolean wasCompleted;

    private File scratchExternal;

    @Before
    public void captureMigrationState() {
        InteractiveDevice.require();

        wasCompleted = SaveFileMigration.migrationCompleted();

        scratchExternal = new File(
                ApplicationProvider.getApplicationContext().getCacheDir(), "migration-scratch");
        deleteRecursively(scratchExternal);
        assertTrue("could not create the scratch external root", scratchExternal.mkdirs());
    }

    @After
    public void restoreMigrationState() {
        // Order matters: put the flag back before reloading, so the reload sees the real state.
        if (wasCompleted) {
            SaveFileMigration.markMigrationCompleted();
        } else {
            SaveFileMigration.revertMigrationStatus();
        }
        SaveFileMigration.overrideExternalPath(null);

        // migrationCompleted() feeds StorageRoots.setInternalOnly through here. Without this the
        // process keeps whichever answer the last test left behind.
        GlobalConfig.loadAllSetting();

        deleteRecursively(scratchExternal);
        deleteRecursively(new File(SaveFileMigration.getInternalSavePath() + SENTINEL_DIR));

        assertEquals("the migration flag was not restored",
                wasCompleted, SaveFileMigration.migrationCompleted());
    }

    // ---- the completion flag -----------------------------------------------------------------

    /** The flag is what stops the migration running again on every launch. */
    @Test
    public void markingCompleteIsVisibleToTheCompletionCheck() {
        SaveFileMigration.revertMigrationStatus();
        assertFalse(SaveFileMigration.migrationCompleted());

        SaveFileMigration.markMigrationCompleted();

        assertTrue(SaveFileMigration.migrationCompleted());
    }

    @Test
    public void revertingClearsTheCompletionFlag() {
        SaveFileMigration.markMigrationCompleted();
        assertTrue(SaveFileMigration.migrationCompleted());

        SaveFileMigration.revertMigrationStatus();

        assertFalse(SaveFileMigration.migrationCompleted());
    }

    /** Marking twice must not fail or double up — the flow can reach it more than once. */
    @Test
    public void markingCompleteTwiceIsHarmless() {
        SaveFileMigration.revertMigrationStatus();

        SaveFileMigration.markMigrationCompleted();
        SaveFileMigration.markMigrationCompleted();

        assertTrue(SaveFileMigration.migrationCompleted());
    }

    /** Reverting when it was never set is a no-op rather than an error. */
    @Test
    public void revertingWhenNotCompletedIsHarmless() {
        SaveFileMigration.revertMigrationStatus();

        SaveFileMigration.revertMigrationStatus();

        assertFalse(SaveFileMigration.migrationCompleted());
    }

    /** The flag is a real file, which is what makes the decision survive a restart. */
    @Test
    public void theFlagIsAFileInTheInternalSaveDirectory() {
        SaveFileMigration.markMigrationCompleted();

        final File flag = new File(SaveFileMigration.getInternalSavePath() + SIGNAL_FILE_NAME);
        assertTrue("expected the signal file at " + flag, flag.isFile());
    }

    // ---- the external path override ----------------------------------------------------------

    @Test
    public void theExternalPathFollowsAnOverride() {
        SaveFileMigration.overrideExternalPath(Uri.fromFile(scratchExternal));

        assertEquals(scratchExternal.getAbsolutePath(), SaveFileMigration.getExternalStoragePath());
    }

    // ---- migrating one file ------------------------------------------------------------------

    /**
     * The copy itself, which is the step the whole flow repeats per file. Content fidelity is the
     * assertion that matters: a migration that produces a file of the right name and the wrong
     * bytes is worse than one that fails.
     */
    @Test
    public void migratingAFileReproducesItsContentInternally() throws IOException {
        SaveFileMigration.overrideExternalPath(Uri.fromFile(scratchExternal));
        final File source = new File(scratchExternal, SENTINEL_DIR + "/chapter.xml");
        writeFile(source, "sentinel chapter body 999000931");

        final String target = SaveFileMigration.migrateFile(Uri.fromFile(source));

        final File copied = new File(target);
        assertTrue("nothing was written to " + target, copied.isFile());
        assertEquals("sentinel chapter body 999000931",
                new String(Files.readAllBytes(copied.toPath()), StandardCharsets.UTF_8));
    }

    /** The destination mirrors the source's position under the root, not just its file name. */
    @Test
    public void migratingAFileKeepsItsPlaceUnderTheRoot() throws IOException {
        SaveFileMigration.overrideExternalPath(Uri.fromFile(scratchExternal));
        final File source = new File(scratchExternal, SENTINEL_DIR + "/nested/deep/file.xml");
        writeFile(source, "nested");

        final String target = SaveFileMigration.migrateFile(Uri.fromFile(source));

        assertTrue("the relative path was not preserved: " + target,
                target.endsWith(SENTINEL_DIR + File.separator + "nested"
                        + File.separator + "deep" + File.separator + "file.xml"));
        assertTrue(target.startsWith(SaveFileMigration.getInternalSavePath()));
    }

    /** Parent directories that do not exist internally yet are created by the copy. */
    @Test
    public void migratingAFileCreatesTheFoldersItNeeds() throws IOException {
        SaveFileMigration.overrideExternalPath(Uri.fromFile(scratchExternal));
        final File source = new File(scratchExternal, SENTINEL_DIR + "/brand/new/tree/file.xml");
        writeFile(source, "x");

        final File copied = new File(SaveFileMigration.migrateFile(Uri.fromFile(source)));

        assertTrue("the parent tree was not created", copied.getParentFile().isDirectory());
        assertTrue(copied.isFile());
    }

    /** An empty file is still a file, and must not be mistaken for a failed copy. */
    @Test
    public void anEmptyFileMigratesAsAnEmptyFile() throws IOException {
        SaveFileMigration.overrideExternalPath(Uri.fromFile(scratchExternal));
        final File source = new File(scratchExternal, SENTINEL_DIR + "/empty.wk8");
        writeFile(source, "");

        final File copied = new File(SaveFileMigration.migrateFile(Uri.fromFile(source)));

        assertTrue("an empty source produced no destination file", copied.isFile());
        assertEquals(0, copied.length());
    }

    /**
     * A source that is not there produces no destination file.
     *
     * <p>This is how {@code MainActivity} counts a failure — it calls {@code testFileExist} on the
     * returned path rather than relying on an exception — so the property worth pinning is that a
     * missing source cannot look like a success.
     */
    @Test
    public void aMissingSourceProducesNoDestinationFile() {
        SaveFileMigration.overrideExternalPath(Uri.fromFile(scratchExternal));
        final File absent = new File(scratchExternal, SENTINEL_DIR + "/never-written.xml");

        String target = null;
        try {
            target = SaveFileMigration.migrateFile(Uri.fromFile(absent));
        } catch (Exception expected) {
            return; // reporting by exception is equally acceptable here
        }

        assertFalse("a missing source must not leave a file that reads as migrated",
                new File(target).isFile());
    }

    // ---- helpers -----------------------------------------------------------------------------

    private static void writeFile(File file, String content) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        final File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
