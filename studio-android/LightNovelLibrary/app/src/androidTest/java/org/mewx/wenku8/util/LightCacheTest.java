package org.mewx.wenku8.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.net.Uri;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;

@SmallTest
public class LightCacheTest {
    private static final String TEMP_FILE_NAME = "test.temp";

    private static String BASE = "";
    private static String BASE_TEMP_FILE_PATH;
    private static String BASE_TEMP_FILE_NAME;
    private static String BASE_TEMP_FILE_FULL_NAME_PATH;

    @Before
    public void setUp() {
        final String TEMP_FILE_PATH = "test/path";
        final String TEMP_FILE_FULL_NAME_PATH = TEMP_FILE_PATH + File.separator + TEMP_FILE_NAME;

        Context instrumentationCtx = InstrumentationRegistry.getInstrumentation().getTargetContext();
        BASE = instrumentationCtx.getFilesDir().getAbsolutePath() + File.separator;
        BASE_TEMP_FILE_PATH = BASE + TEMP_FILE_PATH;
        BASE_TEMP_FILE_NAME = BASE + TEMP_FILE_NAME;
        BASE_TEMP_FILE_FULL_NAME_PATH = BASE + TEMP_FILE_FULL_NAME_PATH;
    }

    @After
    public void cleanUp() {
        // reset test environment
        LightCache.deleteFile(BASE_TEMP_FILE_NAME); // single file
        LightCache.deleteFile(BASE_TEMP_FILE_FULL_NAME_PATH); // file with path
        LightCache.deleteFile(BASE_TEMP_FILE_PATH);
        LightCache.deleteFile(BASE + "test");
    }

    /**
     * when file exists, return true
     */
    @Test
    public void testFileExist() {
        assertFalse(LightCache.testFileExist(BASE_TEMP_FILE_NAME));

        // create file
        LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'a'}, false);

        assertTrue(LightCache.testFileExist(BASE_TEMP_FILE_NAME));
    }

    /**
     * when file exists but the file is empty, return false
     */
    @Test
    public void testFileExistEmpty() {
        assertFalse(LightCache.testFileExist(BASE_TEMP_FILE_NAME));

        // create file
        LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{}, false);

        assertFalse(LightCache.testFileExist(BASE_TEMP_FILE_NAME));
    }

    @Test
    public void loadFileNoFile() {
        assertNull(LightCache.loadFile(BASE_TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void loadFileEmptyFile() {
        LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{}, false);
        assertArrayEquals(new byte[0], LightCache.loadFile(BASE_TEMP_FILE_NAME));
    }

    @Test
    public void loadFileNormalFile() {
        LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false);
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, LightCache.loadFile(BASE_TEMP_FILE_NAME));
    }

    @Test
    public void saveFilePathAndFileName() {
        LightCache.saveFile(BASE_TEMP_FILE_PATH, TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false);
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, LightCache.loadFile(BASE_TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void saveFileFullPath() {
        LightCache.saveFile(BASE_TEMP_FILE_FULL_NAME_PATH, new byte[]{'a', 'b', 'c'}, false);
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, LightCache.loadFile(BASE_TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void saveFileNoUpdate() {
        LightCache.saveFile(BASE_TEMP_FILE_FULL_NAME_PATH, new byte[]{'a', 'b', 'c'}, false);
        LightCache.saveFile(BASE_TEMP_FILE_FULL_NAME_PATH, new byte[]{'d', 'e', 'f'}, false);
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, LightCache.loadFile(BASE_TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void saveFileForceUpdate() {
        LightCache.saveFile(BASE_TEMP_FILE_FULL_NAME_PATH, new byte[]{'a', 'b', 'c'}, false);
        LightCache.saveFile(BASE_TEMP_FILE_FULL_NAME_PATH, new byte[]{'d', 'e', 'f'}, true);
        assertArrayEquals(new byte[]{'d', 'e', 'f'}, LightCache.loadFile(BASE_TEMP_FILE_FULL_NAME_PATH));
    }

    /**
     * Target file is actually is a folder
     */
    @Test
    public void saveFileExistingFolder() {
        assertTrue(new File(BASE_TEMP_FILE_FULL_NAME_PATH).mkdirs()); // create as folder
        assertFalse(LightCache.saveFile(BASE_TEMP_FILE_FULL_NAME_PATH, new byte[]{'d', 'e', 'f'}, true));
    }

    @Test
    public void deleteFileNoFile() {
        assertFalse(LightCache.deleteFile(BASE_TEMP_FILE_PATH, TEMP_FILE_NAME));
    }

    @Test
    public void deleteFileNormal() {
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_FULL_NAME_PATH, new byte[]{'a', 'b', 'c'}, false));
        assertTrue(LightCache.deleteFile(BASE_TEMP_FILE_PATH, TEMP_FILE_NAME));
    }

    @Test
    public void deleteFolder() {
        assertTrue(new File(BASE_TEMP_FILE_FULL_NAME_PATH).mkdirs());
        assertTrue(LightCache.deleteFile(BASE_TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void deleteFolderNotEmpty() {
        assertTrue(new File(BASE_TEMP_FILE_FULL_NAME_PATH).mkdirs());
        assertFalse(LightCache.deleteFile(BASE_TEMP_FILE_PATH));
    }

    @Test
    public void copyFileNoSourceFile() {
        LightCache.copyFile(BASE_TEMP_FILE_NAME, BASE_TEMP_FILE_FULL_NAME_PATH, false);
        assertFalse(LightCache.testFileExist(BASE_TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void copyFileNoTargetFileParentFolder() {
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false));
        LightCache.copyFile(BASE_TEMP_FILE_NAME, BASE_TEMP_FILE_FULL_NAME_PATH, false);
        assertTrue(LightCache.testFileExist(BASE_TEMP_FILE_FULL_NAME_PATH));
        assertTrue(LightCache.testFileExist(BASE_TEMP_FILE_NAME)); // original file still exists
    }

    @Test
    public void copyFileNormal() {
        assertTrue(new File(BASE_TEMP_FILE_PATH).mkdirs());
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false));
        LightCache.copyFile(BASE_TEMP_FILE_NAME, BASE_TEMP_FILE_FULL_NAME_PATH, false);
        assertTrue(LightCache.testFileExist(BASE_TEMP_FILE_FULL_NAME_PATH));
        assertTrue(LightCache.testFileExist(BASE_TEMP_FILE_NAME)); // original file still exists
    }

    @Test
    public void copyFileExistingNoForce() {
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false));
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_FULL_NAME_PATH, new byte[]{'d', 'e', 'f'}, false));
        LightCache.copyFile(BASE_TEMP_FILE_NAME, BASE_TEMP_FILE_FULL_NAME_PATH, false);
        assertTrue(LightCache.testFileExist(BASE_TEMP_FILE_FULL_NAME_PATH));
        assertArrayEquals(new byte[]{'d', 'e', 'f'}, LightCache.loadFile(BASE_TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void copyFileExistingForce() {
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false));
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_FULL_NAME_PATH, new byte[]{'d', 'e', 'f'}, false));
        LightCache.copyFile(BASE_TEMP_FILE_NAME, BASE_TEMP_FILE_FULL_NAME_PATH, true);
        assertTrue(LightCache.testFileExist(BASE_TEMP_FILE_FULL_NAME_PATH));
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, LightCache.loadFile(BASE_TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void listAllFilesInDirectory_withFilePathAsInput() {
        String fileName1 = BASE + "file1";
        assertTrue(LightCache.saveFile(fileName1, new byte[]{'1'}, false));
        assertTrue(LightCache.listAllFilesInDirectory(new File(fileName1)).isEmpty());
    }

    @Test
    public void listAllFilesInDirectory() {
        String fileName1 = BASE + "file1";
        String fileName2 = BASE + "dir1/file2";
        assertTrue(LightCache.saveFile(fileName1, new byte[]{'1'}, false));
        assertTrue(LightCache.saveFile(fileName2, new byte[]{'2'}, false));

        assertTrue(LightCache.listAllFilesInDirectory(new File(BASE + "dir1")).contains(Uri.fromFile(new File(fileName2))));
        assertTrue(LightCache.listAllFilesInDirectory(new File(BASE))
                .containsAll(Arrays.asList(Uri.fromFile(new File(fileName1)), Uri.fromFile(new File(fileName2)))));
    }

    // ---- saveFile writes through a temp file and renames into place. ----
    // The property that matters -- an interrupted write leaves the previous content intact --
    // cannot be provoked from here without injecting a failure mid-write, so these pin the
    // observable invariants of the mechanism instead: nothing partial is visible under the real
    // name, and no temp file survives either outcome.

    @Test
    public void testOverwriteReplacesTheContentEntirely() {
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'a', 'b', 'c', 'd'}, true));

        // Shorter than what was there. A rename replaces the file wholesale; anything that
        // truncated and rewrote in place could leave the tail of the longer content behind.
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'x'}, true));

        assertArrayEquals(new byte[]{'x'}, LightCache.loadFile(BASE_TEMP_FILE_NAME));
    }

    @Test
    public void testSuccessfulWriteLeavesNoTempFileBehind() {
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'a'}, true));

        // A leaked temp file would accumulate one per save, and the novel cache is written on
        // every download.
        assertFalse(new File(BASE_TEMP_FILE_NAME + ".tmp").exists());
    }

    @Test
    public void testFailedWriteLeavesNoTempFileBehind() {
        // A directory standing where the file should go: createNewFile and rename both fail, so
        // this exercises the failure path without needing to interrupt anything.
        final File blocked = new File(BASE + "blocked");
        assertTrue(blocked.mkdirs());

        assertFalse(LightCache.saveFile(blocked.getPath(), new byte[]{'a'}, true));
        assertFalse(new File(blocked.getPath() + ".tmp").exists());

        //noinspection ResultOfMethodCallIgnored
        blocked.delete();
    }

    @Test
    public void testExistingFileIsUntouchedWhenNotForcing() {
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'a'}, true));

        // forceUpdate == false reports success without writing, which callers rely on to mean
        // "a copy is present" rather than "a copy was just written".
        assertTrue(LightCache.saveFile(BASE_TEMP_FILE_NAME, new byte[]{'z'}, false));

        assertArrayEquals(new byte[]{'a'}, LightCache.loadFile(BASE_TEMP_FILE_NAME));
        assertFalse(new File(BASE_TEMP_FILE_NAME + ".tmp").exists());
    }
}
