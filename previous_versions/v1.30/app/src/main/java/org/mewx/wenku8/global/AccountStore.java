package org.mewx.wenku8.global;

import android.util.Log;

import org.mewx.wenku8.network.LightUserSession;
import org.mewx.wenku8.util.CrashReporter;
import org.mewx.wenku8.util.LightCache;

/**
 * The stored account file, {@code cert.wk8}.
 *
 * <p>Extracted from {@code GlobalConfig} as part of step 4 of the sequencing in
 * {@code STABILITY_PLAN.md}. {@code GlobalConfig} keeps both public methods and delegates, so no
 * caller changes. Small, but worth its own name: credential handling sat between the image cache
 * and the connectivity check purely because everything global ended up in one class, and anything
 * touching stored credentials is easier to review when it is not buried.
 *
 * <p><b>Untested on purpose, and the reason is worth stating.</b> {@code load} decodes through
 * {@code LightUserSession} and mutates live session state; {@code save} writes a real credential
 * file. Neither is something a test should do on a developer's own device, so
 * {@code GlobalConfigSettingsTest} asserts only the no-stored-account path and skips outright when
 * the device has an account rather than moving it aside. That leaves roughly 25 lines here
 * permanently uncovered, which is a deliberate trade rather than an oversight.
 *
 * <p><b>Note the debug log in {@link #load()}</b>, carried over unchanged: it writes the contents of
 * the account file to logcat on every load. That is the encrypted blob rather than a plaintext
 * password, and modern Android restricts who can read logcat, but writing credential material to a
 * log at all is a smell — and it lands in a bug report if one is ever captured. Recorded rather than
 * removed, per the standing preference for coverage over logical patches; it is a one-line deletion
 * whenever someone wants it.
 */
final class AccountStore {

    private AccountStore() {
    }

    /** @return whether a stored account was found and successfully decoded into the session */
    static boolean load() {
        final byte[] bytes;
        if (LightCache.testFileExist(GlobalConfig.getFirstFullUserAccountSaveFilePath())) {
            bytes = LightCache.loadFile(GlobalConfig.getFirstFullUserAccountSaveFilePath());
        } else if (LightCache.testFileExist(GlobalConfig.getSecondFullUserAccountSaveFilePath())) {
            bytes = LightCache.loadFile(GlobalConfig.getSecondFullUserAccountSaveFilePath());
        } else {
            return false; // no stored account
        }

        try {
            Log.d("MewX", new String(bytes, "UTF-8"));
            // TODO: decouple
            LightUserSession.decAndSetUserFile(new String(bytes, "UTF-8"));
        } catch (Exception e) {
            CrashReporter.recordException("GlobalConfig.loadUserInfoSet", e);
            return false; // exception
        }

        return true;
    }

    /** Writes the session's account to the first root, falling back to the second. */
    static boolean save() {
        LightCache.saveFile(GlobalConfig.getFirstFullUserAccountSaveFilePath(),
                LightUserSession.encUserFile().getBytes(), true);
        if (!LightCache.testFileExist(GlobalConfig.getFirstFullUserAccountSaveFilePath())) {
            LightCache.saveFile(GlobalConfig.getSecondFullUserAccountSaveFilePath(),
                    LightUserSession.encUserFile().getBytes(), true);
            return LightCache.testFileExist(GlobalConfig.getSecondFullUserAccountSaveFilePath());
        }
        return true;
    }
}
