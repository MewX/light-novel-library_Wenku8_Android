package org.mewx.wenku8.global;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;
import org.mewx.wenku8.util.SaveFileMigration;

import java.io.File;
import java.io.IOException;

/**
 * Which root the app writes to, and what happens when the first one refuses.
 *
 * <p>Step 2 of the sequencing in {@code STABILITY_PLAN.md}, and the reason it was worth doing.
 * The app has always fallen back to a second storage root when the first will not accept a file,
 * but that fallback could not be tested: the decision lived in a static method reading two static
 * flags, with nothing to inject, so there was no way to make the first root fail. Those branches
 * are exercised here for the first time.
 *
 * <p>Nothing about the resolution changed — same two roots, same order, same flags. What changed is
 * that {@link StorageRoots#overrideForTesting} exists.
 *
 * <p><b>Every test here restores global state.</b> The override, the two flags and the settings
 * static are all process-wide, so leaving any of them set would follow the process into whatever
 * runs next. {@link #restoreEverything()} puts all four back and asserts the override is gone.
 */
@LargeTest
public class StorageRootsTest {

    private File scratch;
    private File blockedParent;

    private boolean realInternalOnly;
    private boolean realExternalAvailable;

    @Before
    public void captureGlobalState() throws IOException {
        InteractiveDevice.require();

        realInternalOnly = StorageRoots.isInternalOnly();
        realExternalAvailable = StorageRoots.isExternalAvailable();

        final Context context = ApplicationProvider.getApplicationContext();
        scratch = new File(context.getCacheDir(), "storage-roots-test");
        deleteRecursively(scratch);
        assertTrue("could not create the scratch root", scratch.mkdirs());

        // A regular file standing where a directory would have to be created. Any attempt to
        // mkdirs beneath it fails, which is how the first root is made to refuse a write without
        // depending on permissions or on a path that might exist on some devices and not others.
        blockedParent = new File(context.getCacheDir(), "storage-roots-blocker");
        deleteRecursively(blockedParent);
        assertTrue("could not create the blocker file", blockedParent.createNewFile());
    }

    @After
    public void restoreEverything() {
        StorageRoots.clearOverride();
        StorageRoots.setInternalOnly(realInternalOnly);
        StorageRoots.setExternalAvailable(realExternalAvailable);
        GlobalConfig.loadAllSetting();

        deleteRecursively(scratch);
        deleteRecursively(blockedParent);

        assertFalse("an override was left in place for the next test", StorageRoots.isOverridden());
    }

    private static String asRoot(File dir) {
        return dir.getAbsolutePath() + File.separator;
    }

    /** The two roots must be different places, or the fallback is not a fallback. */
    @Test
    public void theTwoRootsAreDistinct() {
        assertNotEquals(StorageRoots.primary(), StorageRoots.backup());
    }

    @Test
    public void internalOnlySelectsTheInternalRoot() {
        StorageRoots.setInternalOnly(true);

        assertEquals(SaveFileMigration.getInternalSavePath(), StorageRoots.primary());
        assertEquals("the backup should then be the external one",
                SaveFileMigration.getExternalStoragePath(), StorageRoots.backup());
    }

    /** An unusable external root has the same effect as the migration having completed. */
    @Test
    public void anUnavailableExternalRootSelectsTheInternalOne() {
        StorageRoots.setInternalOnly(false);
        StorageRoots.setExternalAvailable(false);

        assertEquals(SaveFileMigration.getInternalSavePath(), StorageRoots.primary());
    }

    @Test
    public void anOverrideRedirectsBothRoots() {
        final File primary = new File(scratch, "p");
        final File backup = new File(scratch, "b");
        StorageRoots.overrideForTesting(asRoot(primary), asRoot(backup));

        assertEquals(asRoot(primary), StorageRoots.primary());
        assertEquals(asRoot(backup), StorageRoots.backup());
    }

    @Test
    public void clearingTheOverrideRestoresRealResolution() {
        final String realPrimary = StorageRoots.primary();
        StorageRoots.overrideForTesting(asRoot(new File(scratch, "p")), asRoot(new File(scratch, "b")));

        StorageRoots.clearOverride();

        assertEquals(realPrimary, StorageRoots.primary());
        assertFalse(StorageRoots.isOverridden());
    }

    // ---- the fallback, reachable for the first time -------------------------------------------

    /**
     * The first root refuses the write, so the file goes to the second.
     *
     * <p>This is the branch the extraction existed to make reachable. Until now the only way to
     * exercise it was to have a device on which the primary root genuinely failed.
     */
    @Test
    public void aWriteFallsBackToTheSecondRootWhenTheFirstRefuses() {
        final File backup = new File(scratch, "backup");
        StorageRoots.overrideForTesting(asRoot(new File(blockedParent, "unreachable")), asRoot(backup));

        final boolean written = GlobalConfig.writeFullFileIntoSaveFolder(
                "intro", "999000921-fallback.xml", "written to the backup root");

        assertTrue("the write should have succeeded via the second root", written);
        final File landed = new File(backup, GlobalConfig.saveFolderName + File.separator
                + "intro" + File.separator + "999000921-fallback.xml");
        assertTrue("nothing was written under the backup root: " + landed, landed.isFile());
    }

    /** And having fallen back, the reader finds it — the two halves have to agree. */
    @Test
    public void aFileWrittenToTheSecondRootIsFoundAgain() {
        final File backup = new File(scratch, "backup");
        StorageRoots.overrideForTesting(asRoot(new File(blockedParent, "unreachable")), asRoot(backup));

        assertTrue(GlobalConfig.writeFullFileIntoSaveFolder(
                "intro", "999000922-fallback.xml", "round trip via the backup root"));

        assertEquals("round trip via the backup root",
                GlobalConfig.loadFullFileFromSaveFolder("intro", "999000922-fallback.xml"));
    }

    /** With both roots unusable the write reports failure rather than throwing. */
    @Test
    public void aWriteWithNoUsableRootReportsFailure() {
        StorageRoots.overrideForTesting(
                asRoot(new File(blockedParent, "one")), asRoot(new File(blockedParent, "two")));

        assertFalse("a write with nowhere to go should report failure",
                GlobalConfig.writeFullFileIntoSaveFolder("intro", "999000923.xml", "nowhere"));
    }

    /** A file in neither root reads back as empty rather than null. */
    @Test
    public void anAbsentFileReadsAsEmpty() {
        StorageRoots.overrideForTesting(asRoot(new File(scratch, "p")), asRoot(new File(scratch, "b")));

        assertEquals("", GlobalConfig.loadFullFileFromSaveFolder("intro", "999000924-absent.xml"));
    }

    /**
     * Redirecting both roots keeps a test off the device owner's real files entirely — the property
     * that lets later storage tests stop borrowing and restoring real data.
     */
    @Test
    public void redirectingBothRootsKeepsWritesInsideTheScratchDirectory() {
        final File primary = new File(scratch, "only");
        StorageRoots.overrideForTesting(asRoot(primary), asRoot(new File(scratch, "unused")));

        assertTrue(GlobalConfig.writeFullFileIntoSaveFolder(
                "intro", "999000925-contained.xml", "contained"));

        final File landed = new File(primary, GlobalConfig.saveFolderName + File.separator
                + "intro" + File.separator + "999000925-contained.xml");
        assertTrue("the write escaped the scratch directory", landed.isFile());
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
