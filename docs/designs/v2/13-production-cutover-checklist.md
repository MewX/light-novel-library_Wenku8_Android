# 13 — Production Cutover & Release Checklist (`org.mewx.wenku8.v2dev` → `org.mewx.wenku8`)

This checklist captures every configuration switch required when promoting `:app-v2` from parallel development (`org.mewx.wenku8.v2dev`) to the production `org.mewx.wenku8` release on Google Play Store (`playstore`) and direct/GitHub Releases (`default`).

---

## 1. Application ID, Version & Signing Cutover
- [x] **`app-v2/build.gradle` (`defaultConfig`)**:
  - `applicationId` cut over to `"org.mewx.wenku8"`.
  - `versionCode 100` (strictly greater than `:app-v1`'s `74`), `versionName "2.0.0"` *(confirm whether final release should stay `"2.0.0"` or match `v1.31` 2-part `"2.0"`)*.
  - Launcher `<activity-alias>` (`org.mewx.wenku8.activity.MainActivity`) added in `app-v2/src/main/AndroidManifest.xml` to preserve v1 pinned home-screen shortcuts.
- [x] **Localized Application Name**:
  - `"v2 Dev"` removed across all 3 locales:
    - `app-v2/src/main/res/values/strings.xml`: `"轻小说文库"`
    - `app-v2/src/main/res/values-zh-rTW/strings.xml`: `"輕小說文庫"`
    - `app-v2/src/main/res/values-zh-rHK/strings.xml`: `"輕小說文庫"`
- [ ] **Release Signing Configuration**:
  - Sign release artifacts with the production `org.mewx.wenku8` upload/release keystore (via Android Studio signed bundle/APK flow or local signing config) so existing `v1.x` users receive an in-place OS upgrade preserving `context.filesDir/saves/`.

## 2. Seamless In-Place `v1.x` → `v2.0` Upgrade Verification
- [x] Automated unit-test coverage for `LegacyV1StorageMigrator`, `UserSessionRepository`, `OfflineFirstWenkuImageFetcher`, and `Wenku8V2Application` cold-start migration hook.
- [ ] Install the signed `v1.31` APK (`org.mewx.wenku8`), populate bookshelf novels, reading progress (`saves/`), offline chapters & illustrations (`saves/imgs/`), `cert.wk8` login credentials, and `settings` (`GlobalConfig`).
- [ ] Install the signed `v2.0` APK (`org.mewx.wenku8`) directly over `v1.31` (`adb install -r --user 0`).
- [ ] Confirm `LegacyV1StorageMigrator` automatically imports internal `filesDir/saves/` into `wenku8_v2.db` + `settings.preferences_pb` on first cold launch, `UserSessionRepository` restores `saves/cert.wk8`, and `OfflineFirstWenkuImageFetcher` serves existing `saves/imgs/*.jpg` without re-downloading.

## 3. `:api` & `wenku8-relay` Protocol Version Registration *(Deferred Until Publish — TODO in `app-v2/build.gradle`)*
- [ ] Register `"2.0"` in `:api` (`TokenManager` / native `native-lib.cpp`) and `wenku8-relay` (`v1_crypto.ts` `ALLOWED_SECURE_PREFIXES`; `sinkhole.ts` already defaults unlisted/newer releases to the `v2.0` policy).
- [ ] Update `buildConfigField "String", "API_PROTOCOL_VERSION", ""1.31""` in `app-v2/build.gradle` to `""2.0""`.
- [x] Verify both `-DforceApiStub` (public CI) and real `:api` submodule builds pass unit tests.

## 4. Production AdMob App ID & Banner Unit ID Swap
- [x] **`app-v2/src/main/AndroidManifest.xml`**:
  - `com.google.android.gms.ads.APPLICATION_ID` updated to production App ID `ca-app-pub-7333757578973883~9635263460`.
- [x] **`app-v2/src/main/java/org/mewx/wenku8/ui/detail/ServerSupportBannerAd.kt`**:
  - `ServerSupportAdConstants.PRODUCTION_BANNER_AD_UNIT_ID` (`ca-app-pub-7333757578973883/9658350163`) is used exclusively in release builds (`!BuildConfig.DEBUG`), with Google's demo ID retained only as a debug fallback (verified by `NovelDetailAndDownloadTest`).

## 5. Android 15+ `16 KB` Page Size Alignment (Google Play `targetSdk 35+` Requirement)
- [x] **`:api` Native Library (`libcpp.so`)**:
  - Built with `ndkVersion '30.0.15729638'`; `readelf -lW` confirms `Align = 0x4000` (`16 KB`) across all `LOAD` segments in `arm64-v8a` and `x86_64`.
- [x] **Jetpack DataStore (`libdatastore_shared_counter.so`) & APK Alignment**:
  - Upgraded to `androidx.datastore:datastore-preferences:1.2.1`; `readelf -lW` confirms `0x4000` alignment on all 64-bit `.so`s (`libcpp.so`, `libdatastore_shared_counter.so`, `libandroidx.graphics.path.so`) and `zipalign -c -P 16 -v 4` passes with `Verification successful`.

## 6. Build Flavors, CI Cutover & Legacy `:app-v1` Retirement
- [x] **Flavor Simplification & CI**:
  - `:app-v2` trimmed to `default` and `playstore` product flavors (dropping `baidu` and `alpha`).
  - `.github/workflows/android-ci.yml` runs `:app-v2:jacocoDefaultDebugUnitTestReport` and `:app-v2:assembleDefault` (CI tests & builds `default` only for `:app-v2`).
- [ ] Once `v2.0` staged rollout reaches 100% stability on Play Store:
  - Remove `:app-v1` from `settings.gradle` (or archive under legacy tag `v1.31-final`), remove `:app-v1` jobs from `.github/workflows/android-ci.yml`, and rename `:app-v2` → `:app` if desired.
