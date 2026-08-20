package org.mewx.wenku8.util;

import static org.junit.Assert.assertTrue;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;

import java.io.File;

/**
 * The internal save path, which everything the app persists is built on.
 *
 * <p>Deliberately blunt. The bug this guards against was not a wrong path, it was the literal
 * string {@code "null/"} — {@code getFilesDir()} came back null once, got concatenated, and was
 * cached in a static for the life of the process, so every save afterwards went to a relative
 * path that cannot be created. Asserting the path is really under the app's files dir catches
 * that, and catches it wherever it comes from: a context that is not ready yet, or a test that
 * left a mock in {@code MyApp}'s static and moved on.
 *
 * <p>That second one is why this runs on a device rather than being a unit test. It only fails
 * when the whole suite runs together in one process, which is exactly the condition it is here
 * to police.
 */
@SmallTest
public class SaveFileMigrationTest {

    @Test
    public void testInternalSavePathIsUnderTheAppFilesDir() {
        final String path = SaveFileMigration.getInternalSavePath();
        final String filesDir = InstrumentationRegistry.getInstrumentation().getTargetContext()
                .getFilesDir().getAbsolutePath();

        assertTrue("internal save path was \"" + path + "\", expected it under " + filesDir,
                path.startsWith(filesDir));
    }

    @Test
    public void testInternalSavePathEndsWithASeparator() {
        // Callers append a subfolder straight onto it, so a missing trailing separator would
        // silently produce sibling files like ".../filessaves" rather than ".../files/saves".
        final String path = SaveFileMigration.getInternalSavePath();

        assertTrue("internal save path was \"" + path + "\"", path.endsWith(File.separator));
    }
}
