package org.mewx.wenku8.global;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * One file in the app's real save folder, borrowed for the duration of a test and put back
 * afterwards.
 *
 * <p><b>Why these tests write to real storage at all.</b> Everything in {@link GlobalConfig} that
 * persists goes through two private methods, {@code loadFullSaveFileContent} and
 * {@code writeFullSaveFileContent}, and the state itself lives in package statics. There is no
 * seam to inject and no way to point the class at a temporary directory, so a test that wants to
 * know what a given file parses to has to put that file where the app looks. Extracting a seam
 * would be the better answer, but not while today's storage logic is under a standing freeze —
 * these tests are the regression net that has to exist <i>before</i> that changes.
 *
 * <p><b>Which root.</b> Not a constant, and this is the part that is easy to get wrong. The app
 * tries the default root first and falls back to the backup one, and on API 29+ the default is
 * external storage and is not writable — so on a modern device the real file is under the backup
 * root, while on an older one it may be under either. Both the reader and the writer probe in that
 * order, so this does too: {@link #arrange} deletes every candidate before writing, which is what
 * stops a stale copy under the other root from being the one the reader actually finds.
 *
 * <p><b>Restoring is not optional.</b> These files are the user's bookshelf, reading positions and
 * settings on a developer's own device. {@link #restore} puts the captured bytes back, and deletes
 * the file outright when there was none to begin with, so a fresh install still looks fresh.
 * Callers must also resync whichever {@code GlobalConfig} static they disturbed — see
 * {@code MyAppTest} in the plan's testing section for what a leaked static does to whatever runs
 * next.
 *
 * <p>Deliberately not retrofitted onto {@code LocalBookshelfTest} and {@code VolumeIndexCacheTest},
 * which each carry their own copy of this. They pass; rewriting working tests to share code buys
 * no coverage and risks the restore path, which is the one thing here that must not break.
 */
final class SaveFileFixture {

    private final String fileName;

    private File originalFile;
    private byte[] originalContent;

    SaveFileFixture(String fileName) {
        this.fileName = fileName;
    }

    /** The two locations the app itself probes, in the order it probes them. */
    private File[] candidates() {
        return new File[]{
                new File(GlobalConfig.getFirstFullSaveFilePath() + fileName),
                new File(GlobalConfig.getSecondFullSaveFilePath() + fileName),
        };
    }

    /** Remembers the real file so {@link #restore} can put it back. Call from {@code @Before}. */
    void capture() throws IOException {
        for (File candidate : candidates()) {
            if (candidate.isFile()) {
                originalFile = candidate;
                originalContent = Files.readAllBytes(candidate.toPath());
                return;
            }
        }
        originalFile = null;
    }

    /** Puts the real file back, or leaves none if there was none. Call from {@code @After}. */
    void restore() throws IOException {
        deleteAll();
        if (originalFile != null) {
            writeRaw(originalFile, originalContent);
        }
    }

    void deleteAll() {
        for (File candidate : candidates()) {
            //noinspection ResultOfMethodCallIgnored
            candidate.delete();
        }
    }

    /**
     * Puts exact bytes on disk, bypassing the writer so that only the reader is on trial. Writes
     * into whichever root accepts, because that is the one the reader will find.
     */
    void arrange(String content) throws IOException {
        deleteAll();

        IOException lastFailure = null;
        for (File candidate : candidates()) {
            try {
                writeRaw(candidate, content.getBytes(StandardCharsets.UTF_8));
                return;
            } catch (IOException e) {
                lastFailure = e;
            }
        }
        throw new IOException("no writable storage root for " + fileName, lastFailure);
    }

    /** What is actually on disk now, for asserting on what the writer produced. */
    String readBack() throws IOException {
        for (File candidate : candidates()) {
            if (candidate.isFile()) {
                return new String(Files.readAllBytes(candidate.toPath()), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    /** Both candidate paths and whether each exists, for assertion messages. */
    String describe() {
        final StringBuilder sb = new StringBuilder("candidates: ");
        for (File candidate : candidates()) {
            sb.append(candidate.getAbsolutePath())
                    .append(candidate.isFile() ? " [file] " : " [absent] ");
        }
        return sb.toString();
    }

    private static void writeRaw(File file, byte[] content) throws IOException {
        //noinspection ResultOfMethodCallIgnored
        file.getParentFile().mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(content);
        }
    }
}
