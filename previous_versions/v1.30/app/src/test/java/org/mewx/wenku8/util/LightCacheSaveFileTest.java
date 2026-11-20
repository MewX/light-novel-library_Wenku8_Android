package org.mewx.wenku8.util;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JVM coverage for {@link LightCache#saveFile(String, byte[], boolean)}.
 *
 * <p>The write used to open the real file and truncate it, so anything that went wrong partway
 * through left a file that existed, opened, and held half a document. Downstream that reads as a
 * corrupt novel or -- per the comment in {@code GlobalConfig.loadLocalBookShelf} -- as a crash on
 * launch. It now stages beside the target and renames over it.
 *
 * <p>Plain JUnit rather than Robolectric: this path is java.io, and the only Android call on it is
 * a stubbed {@code Log}.
 */
public class LightCacheSaveFileTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private static final byte[] OLD = "the previous content".getBytes(StandardCharsets.UTF_8);
    private static final byte[] NEW = "the replacement".getBytes(StandardCharsets.UTF_8);

    private static byte[] read(File f) throws IOException {
        byte[] buf = new byte[(int) f.length()];
        try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
            assertEquals(buf.length, in.read(buf));
        }
        return buf;
    }

    private File target(String name) {
        return new File(folder.getRoot(), name);
    }

    private static File stagingOf(File target) {
        return new File(target.getPath() + ".tmp");
    }

    @Test
    public void writesTheContentToTheTargetPath() throws Exception {
        File f = target("novel.xml");

        assertTrue(LightCache.saveFile(f.getPath(), NEW, true));
        assertArrayEquals(NEW, read(f));
    }

    @Test
    public void createsMissingParentDirectories() throws Exception {
        File f = new File(folder.getRoot(), "a/b/c/novel.xml");

        assertTrue(LightCache.saveFile(f.getPath(), NEW, true));
        assertArrayEquals(NEW, read(f));
    }

    @Test
    public void replacesExistingContentWhenForced() throws Exception {
        File f = target("novel.xml");
        assertTrue(LightCache.saveFile(f.getPath(), OLD, true));

        assertTrue(LightCache.saveFile(f.getPath(), NEW, true));
        assertArrayEquals(NEW, read(f));
    }

    /** The old contract: an existing file is left alone, and that counts as success. */
    @Test
    public void leavesAnExistingFileAloneWhenNotForced() throws Exception {
        File f = target("novel.xml");
        assertTrue(LightCache.saveFile(f.getPath(), OLD, true));

        assertTrue(LightCache.saveFile(f.getPath(), NEW, false));
        assertArrayEquals(OLD, read(f));
    }

    @Test
    public void leavesNoStagingFileBehindOnSuccess() {
        File f = target("novel.xml");

        assertTrue(LightCache.saveFile(f.getPath(), NEW, true));
        assertFalse("the staging file must not outlive the write", stagingOf(f).exists());
    }

    @Test
    public void writesEmptyContent() throws Exception {
        File f = target("empty.xml");

        assertTrue(LightCache.saveFile(f.getPath(), new byte[0], true));
        assertTrue(f.exists());
        assertEquals(0, f.length());
    }

    /**
     * The reason this method was changed. A write that cannot complete must leave the file that
     * was already there untouched, rather than a truncated version of it.
     *
     * <p>The failure is forced by putting a directory where the staging file wants to go, so
     * opening it throws. What matters is not how it fails but what survives: with the previous
     * implementation the target had already been truncated before anything could go wrong.
     */
    @Test
    public void aFailedWriteLeavesThePreviousContentIntact() throws Exception {
        File f = target("novel.xml");
        assertTrue(LightCache.saveFile(f.getPath(), OLD, true));
        assertTrue(stagingOf(f).mkdir());

        assertFalse("a write that cannot be staged must report failure",
                LightCache.saveFile(f.getPath(), NEW, true));
        assertArrayEquals("the previous content must survive a failed write", OLD, read(f));
    }

    @Test
    public void refusesToWriteOverADirectory() throws Exception {
        File f = target("a-directory");
        assertTrue(f.mkdir());

        assertFalse(LightCache.saveFile(f.getPath(), NEW, true));
        assertTrue(f.isDirectory());
    }

    /**
     * A staging file left by an earlier interrupted write must not stop the next one, and must not
     * leak into the result.
     */
    @Test
    public void overwritesAStaleStagingFileFromAnInterruptedWrite() throws Exception {
        File f = target("novel.xml");
        File stale = stagingOf(f);
        try (FileOutputStream out = new FileOutputStream(stale)) {
            out.write("half a document".getBytes(StandardCharsets.UTF_8));
        }

        assertTrue(LightCache.saveFile(f.getPath(), NEW, true));
        assertArrayEquals(NEW, read(f));
        assertFalse(stale.exists());
    }
}
