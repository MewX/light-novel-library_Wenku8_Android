package org.mewx.wenku8.global;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

/**
 * The local bookshelf, which is stored as one line of {@code aid||aid||aid}.
 *
 * <p>This is the list the bookshelf tab renders, and it is reached lazily:
 * {@code getLocalBookshelfList} and {@code testInLocalBookshelf} both call
 * {@code loadLocalBookShelf} the first time anything asks. So whatever this file parses to is
 * what the app's home screen does on launch, which makes the malformed cases here worth as much
 * as the happy path.
 *
 * <p>Device-only because the whole thing is a filesystem round trip — the format has no seam to
 * inject, the reader and writer are private, and the state lives in a static.
 *
 * <p><b>This rewrites the real bookshelf file.</b> The original bytes are captured before each
 * test and put back afterwards, including the case where there was no file at all, and the
 * static is reloaded from the restored file so nothing downstream in the same process sees the
 * test's list. That last step is the {@code MyAppTest} lesson: a test that leaves a process-wide
 * static holding its own data breaks whatever runs next, somewhere else entirely.
 */
@SmallTest
public class LocalBookshelfTest {

    private static final String BOOKSHELF_FILE = "bookshelf_local.wk8";

    private byte[] originalContent;
    private File originalFile;

    private static File bookshelfFile(String storageRoot) {
        return new File(storageRoot + GlobalConfig.saveFolderName + File.separator
                + BOOKSHELF_FILE);
    }

    /**
     * The two candidate locations, in the order the app itself uses them. Which one a save lands
     * in is a runtime decision, not a constant: on API 29+ the default root is external storage
     * and is not writable, so the real file is usually under the backup (internal) root. Both the
     * reader and the writer try default first and fall back, and so does everything here.
     */
    private static File[] candidateFiles() {
        return new File[]{
                bookshelfFile(GlobalConfig.getDefaultStoragePath()),
                bookshelfFile(GlobalConfig.getBackupStoragePath()),
        };
    }

    @Before
    public void captureTheRealBookshelf() throws IOException {
        for (File candidate : candidateFiles()) {
            if (candidate.isFile()) {
                originalFile = candidate;
                originalContent = Files.readAllBytes(candidate.toPath());
                return;
            }
        }
        originalFile = null;
    }

    @After
    public void restoreTheRealBookshelf() throws IOException {
        deleteBookshelfFile();
        if (originalFile != null) {
            writeRaw(originalFile, originalContent);
        }
        // Resync the static with the file on disk, so the next test in this process does not
        // inherit a bookshelf that no longer exists on disk.
        GlobalConfig.loadLocalBookShelf();
    }

    private static void deleteBookshelfFile() {
        for (File candidate : candidateFiles()) {
            //noinspection ResultOfMethodCallIgnored
            candidate.delete();
        }
    }

    private static void writeRaw(File file, byte[] content) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }

    /**
     * Arranges the file directly rather than through the writer, so only the reader is on trial —
     * but into whichever root actually accepts a write, so the reader finds it where it looks.
     */
    private static void givenBookshelfFileContains(String content) throws IOException {
        final byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
        deleteBookshelfFile();

        IOException lastFailure = null;
        for (File candidate : candidateFiles()) {
            try {
                writeRaw(candidate, bytes);
                GlobalConfig.loadLocalBookShelf();
                return;
            } catch (IOException e) {
                lastFailure = e;
            }
        }
        throw new IOException("no writable storage root for the bookshelf file", lastFailure);
    }

    @Test
    public void testStoredListLoadsBackInOrder() throws IOException {
        givenBookshelfFileContains("1306||41748||9");

        final List<Integer> shelf = GlobalConfig.getLocalBookshelfList();

        assertEquals(3, shelf.size());
        assertEquals(Integer.valueOf(1306), shelf.get(0));
        assertEquals(Integer.valueOf(41748), shelf.get(1));
        assertEquals(Integer.valueOf(9), shelf.get(2));
    }

    @Test
    public void testSingleEntryHasNoSeparator() throws IOException {
        givenBookshelfFileContains("1306");

        assertEquals(1, GlobalConfig.getLocalBookshelfList().size());
        assertTrue(GlobalConfig.testInLocalBookshelf(1306));
    }

    @Test
    public void testMissingFileLoadsAnEmptyShelf() {
        deleteBookshelfFile();
        GlobalConfig.loadLocalBookShelf();

        // A fresh install, and also a device whose storage could not be read. Neither may throw:
        // this runs before the bookshelf tab draws.
        assertTrue(GlobalConfig.getLocalBookshelfList().isEmpty());
        assertFalse(GlobalConfig.testInLocalBookshelf(1306));
    }

    @Test
    public void testEmptyFileLoadsAnEmptyShelf() throws IOException {
        givenBookshelfFileContains("");

        assertTrue(GlobalConfig.getLocalBookshelfList().isEmpty());
    }

    @Test
    public void testEmptyEntriesAreSkipped() throws IOException {
        // A trailing separator is what a removal down to zero entries can leave behind, and the
        // doubled one is what an interrupted write looks like.
        givenBookshelfFileContains("1306||||41748||");

        final List<Integer> shelf = GlobalConfig.getLocalBookshelfList();

        assertEquals(2, shelf.size());
        assertEquals(Integer.valueOf(1306), shelf.get(0));
        assertEquals(Integer.valueOf(41748), shelf.get(1));
    }

    @Test
    public void testAddedBookGoesToTheFrontAndSurvivesAReload() throws IOException {
        givenBookshelfFileContains("1306||41748");

        GlobalConfig.addToLocalBookshelf(9);
        // Reload from disk rather than trusting the in-memory list, so this covers the write.
        GlobalConfig.loadLocalBookShelf();

        final List<Integer> shelf = GlobalConfig.getLocalBookshelfList();
        assertEquals(3, shelf.size());
        assertEquals("a newly added book is meant to sort to the top",
                Integer.valueOf(9), shelf.get(0));
    }

    @Test
    public void testAddingAnExistingBookDoesNotDuplicateIt() throws IOException {
        givenBookshelfFileContains("1306||41748");

        GlobalConfig.addToLocalBookshelf(1306);
        GlobalConfig.loadLocalBookShelf();

        assertEquals(2, GlobalConfig.getLocalBookshelfList().size());
    }

    @Test
    public void testRemovedBookSurvivesAReload() throws IOException {
        givenBookshelfFileContains("1306||41748||9");

        GlobalConfig.removeFromLocalBookshelf(41748);
        GlobalConfig.loadLocalBookShelf();

        final List<Integer> shelf = GlobalConfig.getLocalBookshelfList();
        assertEquals(2, shelf.size());
        assertFalse(GlobalConfig.testInLocalBookshelf(41748));
        assertTrue(GlobalConfig.testInLocalBookshelf(1306));
        assertTrue(GlobalConfig.testInLocalBookshelf(9));
    }

    @Test
    public void testRemovingTheLastBookLeavesAnEmptyShelf() throws IOException {
        givenBookshelfFileContains("1306");

        GlobalConfig.removeFromLocalBookshelf(1306);
        GlobalConfig.loadLocalBookShelf();

        assertTrue(GlobalConfig.getLocalBookshelfList().isEmpty());
    }

    /**
     * This used to assert the opposite. A non-numeric entry threw {@link NumberFormatException}
     * out of {@code loadLocalBookShelf}, and since every caller reaches that method lazily with
     * no catch above it, a bookshelf file damaged by a partial write took out the app on the
     * first screen that read it. Phase 1 item 9 changed it to drop the entry.
     */
    @Test
    public void testNonNumericEntryIsSkippedRatherThanThrowing() throws IOException {
        givenBookshelfFileContains("1306||not-an-aid||41748");

        final List<Integer> shelf = GlobalConfig.getLocalBookshelfList();

        // The corrupt entry is the only casualty: the novels either side of it are still there.
        // Rejecting the whole file would have emptied the bookshelf instead.
        assertEquals(2, shelf.size());
        assertEquals(Integer.valueOf(1306), shelf.get(0));
        assertEquals(Integer.valueOf(41748), shelf.get(1));
    }

    @Test
    public void testAnEntirelyCorruptFileLoadsAnEmptyShelfWithoutThrowing() throws IOException {
        // What a truncated or overwritten-with-something-else file looks like. An empty shelf is
        // recoverable -- the user re-adds novels, or a cloud sync refills it. A crash on launch
        // was not.
        givenBookshelfFileContains("this is not a bookshelf at all");

        assertTrue(GlobalConfig.getLocalBookshelfList().isEmpty());
    }

    @Test
    public void testACorruptEntrySurvivesARewrite() throws IOException {
        // The dropped entry must not come back when the shelf is written out again, and the
        // rewrite must not carry the unparseable token with it.
        givenBookshelfFileContains("1306||not-an-aid||41748");

        GlobalConfig.addToLocalBookshelf(9);
        GlobalConfig.loadLocalBookShelf();

        final List<Integer> shelf = GlobalConfig.getLocalBookshelfList();
        assertEquals(3, shelf.size());
        assertEquals(Integer.valueOf(9), shelf.get(0));
        assertTrue(GlobalConfig.testInLocalBookshelf(1306));
        assertTrue(GlobalConfig.testInLocalBookshelf(41748));
    }
}
