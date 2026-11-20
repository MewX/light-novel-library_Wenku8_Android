package org.mewx.wenku8.global;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.LargeTest;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.mewx.wenku8.InteractiveDevice;
import org.mewx.wenku8.util.LightCache;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The settings, notice and image-cache writers — the last large untested block of
 * {@code GlobalConfig}, and step 1 of the refactor sequenced in {@code STABILITY_PLAN.md}.
 *
 * <p>These are covered before anything is extracted, deliberately. The rest of {@code GlobalConfig}
 * already has 82 tests behind it, which is what makes moving those parts safe; this block had
 * none, so extracting it first would have been the one step with no safety net.
 *
 * <p><b>These tests write the device owner's real settings file</b>, because there is no seam to
 * redirect storage — that seam is step 2 of the same plan, and this class is part of the argument
 * for it. The original file is read in {@link #keepTheOwnersSettings()} and put back in
 * {@link #restoreTheOwnersSettings()}, together with a reload so the in-memory copy matches disk
 * again rather than carrying this test's values into whatever runs next.
 *
 * <p><b>Credentials are read, never written.</b> {@code saveUserInfoSet} is not exercised: it calls
 * {@code LightUserSession.encUserFile()} from the private {@code api/} module, and writing a
 * credential file on a real device is not something a test should do. Only the no-stored-account
 * path is asserted, and only when the device genuinely has no account, so nothing is overwritten
 * and no session state changes.
 */
@LargeTest
public class GlobalConfigSettingsTest {

    /** Mirrors {@code GlobalConfig.saveSetting}, which is private. */
    private static final String SETTINGS_FILE = "settings.wk8";
    /** Mirrors {@code GlobalConfig.saveNoticeString}. */
    private static final String NOTICE_FILE = "notice.wk8";

    private static final String SENTINEL_IMAGE = "999000901-cover.jpg";
    private static final byte[] SENTINEL_IMAGE_BYTES = "not really a jpeg".getBytes(StandardCharsets.UTF_8);

    /** The owner's real settings and notice, restored afterwards. Null when none existed. */
    private String realSettings;
    private String realNotice;

    @Before
    public void keepTheOwnersSettings() throws IOException {
        InteractiveDevice.require();

        realSettings = readSaveFile(SETTINGS_FILE);
        realNotice = readSaveFile(NOTICE_FILE);
        deleteImageFixture();
    }

    @After
    public void restoreTheOwnersSettings() throws IOException {
        deleteImageFixture();

        deleteSaveFile(SETTINGS_FILE);
        if (realSettings != null) {
            writeSaveFile(SETTINGS_FILE, realSettings);
        }

        deleteSaveFile(NOTICE_FILE);
        if (realNotice != null) {
            writeSaveFile(NOTICE_FILE, realNotice);
        }

        // The parsed settings live in a static that outlives one test. Leaving it holding this
        // test's values would follow the process into whatever runs next -- the same hazard
        // VerticalReaderActivityLifecycleTest guards against for reading positions.
        GlobalConfig.loadAllSetting();
    }

    // ---- settings ----------------------------------------------------------------------------

    @Test
    public void aSettingIsReadableBackAfterBeingSet() {
        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.reader_font_size, "23");

        assertEquals("23",
                GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.reader_font_size));
    }

    /**
     * The part that matters: a setting has to survive being written to disk and parsed back, not
     * merely survive in the in-memory {@code ContentValues}.
     */
    @Test
    public void aSettingSurvivesBeingReloadedFromDisk() {
        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.reader_line_distance, "17");

        GlobalConfig.loadAllSetting();

        assertEquals("17",
                GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.reader_line_distance));
    }

    /** Overwriting replaces rather than accumulating — {@code setToAllSetting} removes then puts. */
    @Test
    public void settingTheSameItemTwiceKeepsOnlyTheSecondValue() {
        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.reader_font_size, "11");
        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.reader_font_size, "29");

        GlobalConfig.loadAllSetting();

        assertEquals("29",
                GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.reader_font_size));
        final String raw = readSaveFileQuietly(SETTINGS_FILE);
        assertFalse("the replaced value is still on disk", raw.contains("::::11"));
    }

    /** Two settings share one file, separated by the record delimiter. */
    @Test
    public void severalSettingsShareTheFileInTheFormatTheLoaderExpects() {
        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.reader_font_size, "13");
        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.eink_mode, "0");

        final String raw = readSaveFileQuietly(SETTINGS_FILE);

        assertTrue("records should be separated by ||||", raw.contains("||||"));
        assertTrue(raw.contains("reader_font_size::::13"));
        assertTrue(raw.contains("eink_mode::::0"));
    }

    /**
     * A damaged record must not cost the reader every other setting.
     *
     * <p>The loader skips any entry that does not split into exactly two halves, which is what
     * makes a partially written file survivable.
     */
    @Test
    public void aMalformedRecordIsSkippedAndTheRestOfTheFileSurvives() throws IOException {
        deleteSaveFile(SETTINGS_FILE);
        writeSaveFile(SETTINGS_FILE,
                "reader_font_size::::15"
                        + "||||" + "this-record-has-no-separator"
                        + "||||" + "eink_mode::::1"
                        + "||||" + "::::"
                        + "||||" + "trailing::::");

        GlobalConfig.loadAllSetting();

        assertEquals("15", GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.reader_font_size));
        assertEquals("1", GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.eink_mode));
    }

    /** An empty settings file is a first run, not a failure. */
    @Test
    public void anAbsentSettingsFileLoadsAsAFreshInstall() throws IOException {
        deleteSaveFile(SETTINGS_FILE);

        GlobalConfig.loadAllSetting();

        // loadAllSetting stamps a version when it finds none, which is the marker of a fresh load.
        assertEquals("1", GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.version));
    }

    /**
     * <b>Current behaviour, not endorsed behaviour.</b> The format is delimiter-separated with no
     * escaping, so a value containing the field delimiter makes its own record unparseable and the
     * setting is silently dropped on the next load.
     *
     * <p>Reachable in principle rather than in practice: the three settings holding arbitrary text
     * are {@code menu_bg_path}, {@code reader_font_path} and {@code reader_background_path}, all
     * filesystem paths, and a path containing {@code ::::} is possible but not likely. Recorded
     * rather than fixed, per the standing preference for coverage over logical patches — and
     * because escaping would change the on-disk format, which needs a migration rather than a
     * patch. Pinned here so a later fix shows up as a deliberate change.
     */
    @Test
    public void aValueContainingTheFieldDelimiterIsLostOnReload() {
        GlobalConfig.setToAllSetting(GlobalConfig.SettingItems.menu_bg_path, "/sdcard/a::::b.png");

        GlobalConfig.loadAllSetting();

        assertNull("the record splits into three parts and is skipped by the loader",
                GlobalConfig.getFromAllSetting(GlobalConfig.SettingItems.menu_bg_path));
    }

    // ---- notice ------------------------------------------------------------------------------

    @Test
    public void theCachedNoticeSurvivesARoundTrip() {
        GlobalConfig.writeTheNotice("sentinel notice 999000901");

        assertEquals("sentinel notice 999000901", GlobalConfig.loadSavedNotice());
    }

    /** No cached notice is an empty string rather than null, which every caller relies on. */
    @Test
    public void anAbsentNoticeReadsAsEmptyRatherThanNull() throws IOException {
        deleteSaveFile(NOTICE_FILE);

        final String notice = GlobalConfig.loadSavedNotice();

        assertNotNull(notice);
        assertTrue(notice.isEmpty());
    }

    // ---- image cache -------------------------------------------------------------------------

    @Test
    public void aSavedCoverImageIsFoundAgainByName() {
        assertTrue("the cover image was not written",
                GlobalConfig.saveNovelCoverImage(SENTINEL_IMAGE, SENTINEL_IMAGE_BYTES));

        final String path = GlobalConfig.getExistingNovelContentImagePath(SENTINEL_IMAGE);

        assertNotNull("a written cover image should resolve to a path", path);
        assertTrue(new File(path).isFile());
    }

    @Test
    public void anImageThatWasNeverSavedResolvesToNull() {
        assertNull(GlobalConfig.getExistingNovelContentImagePath("999000902-never-written.jpg"));
    }

    /**
     * Saving an image that is already cached reports success without going near the network, which
     * is what makes the cover path usable offline.
     */
    @Test
    public void savingACoverImageThatAlreadyExistsSucceedsAgain() {
        assertTrue(GlobalConfig.saveNovelCoverImage(SENTINEL_IMAGE, SENTINEL_IMAGE_BYTES));

        assertTrue("an already-cached image should still report success",
                GlobalConfig.saveNovelCoverImage(SENTINEL_IMAGE, SENTINEL_IMAGE_BYTES));
    }

    // ---- credentials and connectivity --------------------------------------------------------

    /**
     * With no stored account, the load reports failure rather than throwing.
     *
     * <p>Skipped rather than run when the device does have an account: the alternative would be
     * moving or deleting the owner's credential file, and the success path calls into
     * {@code LightUserSession} and would change live session state.
     */
    @Test
    public void noStoredAccountReadsAsAFailedLoad() {
        Assume.assumeFalse("this device has a stored account; not touching it",
                LightCache.testFileExist(GlobalConfig.getFirstFullUserAccountSaveFilePath())
                        || LightCache.testFileExist(GlobalConfig.getSecondFullUserAccountSaveFilePath()));

        assertFalse(GlobalConfig.loadUserInfoSet());
    }

    /** Answers from the real ConnectivityManager without throwing, on either kind of device. */
    @Test
    public void connectivityIsReportedWithoutThrowing() {
        GlobalConfig.isNetworkAvailable(ApplicationProvider.getApplicationContext());
    }

    // ---- fixture helpers ---------------------------------------------------------------------
    //
    // Both save roots are probed in the app's own order, because a test process can resolve the
    // default root differently from the running app -- see the storage-root trap in
    // STABILITY_PLAN.md, and VerticalReaderActivityLifecycleTest, which does the same for
    // reading positions.

    private static File[] candidates(String fileName) {
        return new File[]{
                new File(GlobalConfig.getFirstFullSaveFilePath() + fileName),
                new File(GlobalConfig.getSecondFullSaveFilePath() + fileName),
        };
    }

    private static String readSaveFile(String fileName) throws IOException {
        for (File candidate : candidates(fileName)) {
            if (candidate.isFile()) {
                return new String(Files.readAllBytes(candidate.toPath()), StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    private static String readSaveFileQuietly(String fileName) {
        try {
            final String content = readSaveFile(fileName);
            return content == null ? "" : content;
        } catch (IOException e) {
            return "";
        }
    }

    private static void writeSaveFile(String fileName, String content) throws IOException {
        for (File candidate : candidates(fileName)) {
            final File parent = candidate.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                continue;
            }
            try {
                Files.write(candidate.toPath(), content.getBytes(StandardCharsets.UTF_8));
                return;
            } catch (IOException ignored) {
                // Try the other root; only both failing is worth reporting.
            }
        }
        throw new IOException("neither save root accepted " + fileName);
    }

    private static void deleteSaveFile(String fileName) {
        for (File candidate : candidates(fileName)) {
            //noinspection ResultOfMethodCallIgnored
            candidate.delete();
        }
    }

    private static void deleteImageFixture() {
        for (String root : new String[]{
                GlobalConfig.getFirstFullSaveFilePath(), GlobalConfig.getSecondFullSaveFilePath()}) {
            //noinspection ResultOfMethodCallIgnored
            new File(root + GlobalConfig.imgsSaveFolderName + File.separator + SENTINEL_IMAGE).delete();
        }
    }
}
