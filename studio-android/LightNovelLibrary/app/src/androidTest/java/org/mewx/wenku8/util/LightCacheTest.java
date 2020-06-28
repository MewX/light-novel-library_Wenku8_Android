package org.mewx.wenku8.util;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

}
