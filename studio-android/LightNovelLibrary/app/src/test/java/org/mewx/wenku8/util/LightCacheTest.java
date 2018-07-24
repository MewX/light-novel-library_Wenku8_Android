package org.mewx.wenku8.util;

import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(PowerMockRunner.class)
@PrepareForTest({Log.class})
public class LightCacheTest {
    private static final String TEMP_FILE_PATH = "test/path";
    private static final String TEMP_FILE_NAME = "test.temp";
    private static final String TEMP_FILE_FULL_NAME_PATH = TEMP_FILE_PATH + File.separator + TEMP_FILE_NAME;

    @Before
    public void setUp() {
        // mock at the very beginning
        PowerMockito.mockStatic(Log.class);
        PowerMockito.doReturn(0).when(Log.class); // do nothing

        // reset test environment
        LightCache.deleteFile(TEMP_FILE_NAME); // single file
        LightCache.deleteFile(TEMP_FILE_FULL_NAME_PATH); // file with path
        LightCache.deleteFile(TEMP_FILE_PATH);
        LightCache.deleteFile("test");
    }

    /**
     * when file exists, return true
     */
    @Test
    public void testFileExist() {
        assertFalse(LightCache.testFileExist(TEMP_FILE_NAME));

        // create file
        LightCache.saveFile(TEMP_FILE_NAME, new byte[]{'a'}, false);

        assertTrue(LightCache.testFileExist(TEMP_FILE_NAME));
    }

    /**
     * when file exists but the file is empty, return false
     */
    @Test
    public void testFileExistEmpty() {
        assertFalse(LightCache.testFileExist(TEMP_FILE_NAME));

        // create file
        LightCache.saveFile(TEMP_FILE_NAME, new byte[]{}, false);

        assertFalse(LightCache.testFileExist(TEMP_FILE_NAME));
    }

    @Test
    public void loadFileNoFile() {
        assertNull(LightCache.loadFile(TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void loadFileEmptyFile() {
        LightCache.saveFile(TEMP_FILE_NAME, new byte[]{}, false);
        assertArrayEquals(new byte[0], LightCache.loadFile(TEMP_FILE_NAME));
    }

    @Test
    public void loadFileNormalFile() {
        LightCache.saveFile(TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false);
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, LightCache.loadFile(TEMP_FILE_NAME));
    }

    @Test
    public void saveFilePathAndFileName() {
        LightCache.saveFile(TEMP_FILE_PATH, TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false);
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, LightCache.loadFile(TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void saveFileFullPath() {
        LightCache.saveFile(TEMP_FILE_FULL_NAME_PATH, new byte[]{'a', 'b', 'c'}, false);
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, LightCache.loadFile(TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void saveFileNoUpdate() {
        LightCache.saveFile(TEMP_FILE_FULL_NAME_PATH, new byte[]{'a', 'b', 'c'}, false);
        LightCache.saveFile(TEMP_FILE_FULL_NAME_PATH, new byte[]{'d', 'e', 'f'}, false);
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, LightCache.loadFile(TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void saveFileForceUpdate() {
        LightCache.saveFile(TEMP_FILE_FULL_NAME_PATH, new byte[]{'a', 'b', 'c'}, false);
        LightCache.saveFile(TEMP_FILE_FULL_NAME_PATH, new byte[]{'d', 'e', 'f'}, true);
        assertArrayEquals(new byte[]{'d', 'e', 'f'}, LightCache.loadFile(TEMP_FILE_FULL_NAME_PATH));
    }

    /**
     * Target file is actually is a folder
     */
    @Test
    public void saveFileExistingFolder() {
        assertTrue(new File(TEMP_FILE_FULL_NAME_PATH).mkdirs()); // create as folder
        assertFalse(LightCache.saveFile(TEMP_FILE_FULL_NAME_PATH, new byte[]{'d', 'e', 'f'}, true));
    }

    @Test
    public void deleteFileNoFile() {
        assertFalse(LightCache.deleteFile(TEMP_FILE_PATH, TEMP_FILE_NAME));
    }

    @Test
    public void deleteFileNormal() {
        assertTrue(LightCache.saveFile(TEMP_FILE_FULL_NAME_PATH, new byte[]{'a', 'b', 'c'}, false));
        assertTrue(LightCache.deleteFile(TEMP_FILE_PATH, TEMP_FILE_NAME));
    }

    @Test
    public void deleteFolder() {
        assertTrue(new File(TEMP_FILE_FULL_NAME_PATH).mkdirs());
        assertTrue(LightCache.deleteFile(TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void deleteFolderNotEmpty() {
        assertTrue(new File(TEMP_FILE_FULL_NAME_PATH).mkdirs());
        assertFalse(LightCache.deleteFile(TEMP_FILE_PATH));
    }

    @Test
    public void copyFileNoSourceFile() {
        LightCache.copyFile(TEMP_FILE_NAME, TEMP_FILE_FULL_NAME_PATH, false);
        assertFalse(LightCache.testFileExist(TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void copyFileNormal() {
        assertTrue(LightCache.saveFile(TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false));
        LightCache.copyFile(TEMP_FILE_NAME, TEMP_FILE_FULL_NAME_PATH, false);
        assertTrue(LightCache.testFileExist(TEMP_FILE_FULL_NAME_PATH));
        assertTrue(LightCache.testFileExist(TEMP_FILE_NAME)); // original file still exists
    }

    @Test
    public void copyFileExistingNoForce() {
        assertTrue(LightCache.saveFile(TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false));
        assertTrue(LightCache.saveFile(TEMP_FILE_FULL_NAME_PATH, new byte[]{'d', 'e', 'f'}, false));
        LightCache.copyFile(TEMP_FILE_NAME, TEMP_FILE_FULL_NAME_PATH, false);
        assertTrue(LightCache.testFileExist(TEMP_FILE_FULL_NAME_PATH));
        assertArrayEquals(new byte[]{'d', 'e', 'f'}, LightCache.loadFile(TEMP_FILE_FULL_NAME_PATH));
    }

    @Test
    public void copyFileExistingForce() {
        assertTrue(LightCache.saveFile(TEMP_FILE_NAME, new byte[]{'a', 'b', 'c'}, false));
        assertTrue(LightCache.saveFile(TEMP_FILE_FULL_NAME_PATH, new byte[]{'d', 'e', 'f'}, false));
        LightCache.copyFile(TEMP_FILE_NAME, TEMP_FILE_FULL_NAME_PATH, true);
        assertTrue(LightCache.testFileExist(TEMP_FILE_FULL_NAME_PATH));
        assertArrayEquals(new byte[]{'a', 'b', 'c'}, LightCache.loadFile(TEMP_FILE_FULL_NAME_PATH));
    }

}
