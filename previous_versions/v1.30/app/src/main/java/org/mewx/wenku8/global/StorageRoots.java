package org.mewx.wenku8.global;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.mewx.wenku8.util.SaveFileMigration;

/**
 * Which two directories the app persists into, and in what order it tries them.
 *
 * <p>Extracted from {@code GlobalConfig} as step 2 of the sequencing in
 * {@code STABILITY_PLAN.md}. The decision itself is unchanged — same two roots, same order, same
 * flags — and {@code GlobalConfig} still exposes the same methods, now delegating here, so no
 * caller changes. What is new is that the answer can be overridden, which nothing could do before.
 *
 * <p><b>Why that mattered enough to extract.</b> Every persisted file is written to whichever root
 * answers first, and the fallback to the second root only runs when the first fails. With the
 * decision buried in a static method reading two static flags, no test could make the first root
 * fail, so every fallback branch in the app was unreachable and untested — and a test that wanted
 * to know what a file parsed to had to write into the device owner's real save folder and put it
 * back afterwards. {@code VerticalReaderActivityLifecycleTest} holds the owner's real reading
 * positions in a field for exactly this reason.
 *
 * <p><b>Order, which is easy to get wrong.</b> The internal path wins whenever migration has
 * completed or external storage is unusable; otherwise external is primary. The backup is simply
 * the other one. Both the reader and the writer probe primary then backup, which is what lets a
 * file written under either be found again.
 */
public final class StorageRoots {

    private StorageRoots() {
    }

    /**
     * Set from {@code SaveFileMigration.migrationCompleted()} when settings load. Highest priority:
     * once the migration has run, external storage is not consulted at all.
     */
    private static boolean internalOnly = false;

    /** Cleared when the external root turns out to be unusable, e.g. unwritable on API 29+. */
    private static boolean externalAvailable = true;

    /** Non-null only under test. See {@link #overrideForTesting}. */
    @Nullable
    private static String overriddenPrimary;
    @Nullable
    private static String overriddenBackup;

    static void setInternalOnly(boolean value) {
        internalOnly = value;
    }

    static boolean isInternalOnly() {
        return internalOnly;
    }

    static void setExternalAvailable(boolean value) {
        externalAvailable = value;
    }

    static boolean isExternalAvailable() {
        return externalAvailable;
    }

    /** The root tried first. */
    @NonNull
    public static String primary() {
        if (overriddenPrimary != null) {
            return overriddenPrimary;
        }
        if (internalOnly || !externalAvailable) {
            return SaveFileMigration.getInternalSavePath();
        }
        return SaveFileMigration.getExternalStoragePath();
    }

    /** The root tried when {@link #primary} does not hold the file, or will not accept it. */
    @NonNull
    public static String backup() {
        if (overriddenBackup != null) {
            return overriddenBackup;
        }
        final String internal = SaveFileMigration.getInternalSavePath();
        return primary().equals(internal) ? SaveFileMigration.getExternalStoragePath() : internal;
    }

    /**
     * Points both roots somewhere else for the duration of a test.
     *
     * <p>This is the whole point of the extraction. Pass an unwritable or non-existent directory as
     * {@code primary} to exercise the fallback branches, which no test could reach before, or point
     * both at a scratch directory to test storage without touching the device owner's files.
     *
     * <p><b>Must be paired with {@link #clearOverride()} in an {@code @After}.</b> These are process
     * statics, so an override left in place follows the process into whatever runs next — the same
     * hazard that makes {@code loadAllSetting} dangerous to call from a test. Paths should end with
     * the platform separator, matching what {@code SaveFileMigration} returns.
     */
    @VisibleForTesting
    public static void overrideForTesting(@Nullable String primary, @Nullable String backup) {
        overriddenPrimary = primary;
        overriddenBackup = backup;
    }

    /** Restores real path resolution. Safe to call when no override is active. */
    @VisibleForTesting
    public static void clearOverride() {
        overriddenPrimary = null;
        overriddenBackup = null;
    }

    /** Whether an override is in force, so a test can assert it cleaned up after itself. */
    @VisibleForTesting
    public static boolean isOverridden() {
        return overriddenPrimary != null || overriddenBackup != null;
    }
}
