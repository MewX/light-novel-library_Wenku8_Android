# 13 — Production Cutover & Release Checklist (`org.mewx.wenku8.v2dev` → `org.mewx.wenku8`)

This checklist captures every configuration switch required when promoting `:app-v2` from parallel development (`org.mewx.wenku8.v2dev`) to the production `org.mewx.wenku8` release on Google Play Store, GitHub Releases (`alpha`), and `baidu`.

---

## 1. Application ID, Version & Signing Cutover
- [ ] **`app-v2/build.gradle` (`defaultConfig`)**:
  - Change `applicationId "org.mewx.wenku8.v2dev"` → `applicationId "org.mewx.wenku8"`.
  - Set production `versionCode` (strictly greater than the live `v1.3x` Play Store `versionCode`) and `versionName "2.0.0"`.
- [ ] **Localized Application Name**:
  - Update `app_name` in:
    - `app-v2/src/main/res/values/strings.xml`: `"轻小说文库 v2 Dev"` → `"轻小说文库"`
    - `app-v2/src/main/res/values-zh-rTW/strings.xml`: `"輕小說文庫 v2 Dev"` → `"輕小說文庫"`
    - `app-v2/src/main/res/values-zh-rHK/strings.xml`: `"輕小說文庫 v2 Dev"` → `"輕小說文庫"`
- [ ] **Release Signing Configuration**:
  - Attach the production `org.mewx.wenku8` upload/release keystore to `:app-v2`'s `signingConfigs.release` so existing `v1.x` users receive an in-place OS upgrade preserving `context.filesDir/saves/`.

## 2. Seamless In-Place `v1.x` → `v2.0` Upgrade Verification
- [ ] Install the signed `v1.31` APK (`org.mewx.wenku8`), populate bookshelf novels, reading progress (`saves/`), offline chapters & illustrations (`saves/imgs/`), `cert.wk8` login credentials, and `settings` (`GlobalConfig`).
- [ ] Install the signed `v2.0` APK (`org.mewx.wenku8`) directly over `v1.31` (`adb install -r --user 0`).
- [ ] Confirm `LegacyV1StorageMigrator` automatically imports internal `filesDir/saves/` into `wenku8_v2.db` + `settings.preferences_pb` on first cold launch, `UserSessionRepository` restores `saves/cert.wk8`, and `OfflineFirstWenkuImageFetcher` serves existing `saves/imgs/*.jpg` without re-downloading.

## 3. `:api` & `wenku8-relay` Protocol Version Registration
- [ ] Register `"2.0"` (or `"2.0.0"`) in `:api` (`TokenManager` / native `libcpp.so` if version-bound) and on `wenku8-relay`.
- [ ] Update `buildConfigField "String", "API_PROTOCOL_VERSION", "\"1.31\""` in `app-v2/build.gradle` to `"\"2.0\""`.
- [ ] Verify both `-DforceApiStub` (public CI) and real `:api` submodule builds pass all unit & instrumented tests.

## 4. Production AdMob App ID & Banner Unit ID Swap
While under `org.mewx.wenku8.v2dev`, `:app-v2` uses Google's official test AdMob IDs to prevent invalid-traffic policy flags during development. At cutover:
- [ ] **`app-v2/src/main/AndroidManifest.xml`**:
  - Replace `com.google.android.gms.ads.APPLICATION_ID` value `ca-app-pub-3940256099942544~3347511713` with production App ID `ca-app-pub-7333757578973883~9635263460`.
- [ ] **`app-v2/src/main/java/org/mewx/wenku8/v2/ui/detail/ServerSupportBannerAd.kt`**:
  - Replace `TEST_BANNER_AD_UNIT_ID` (`ca-app-pub-3940256099942544/6300978111`) in release builds (`!BuildConfig.DEBUG`) with the production `NovelDetailScreen` inline banner Ad Unit ID.

## 5. Android 15+ `16 KB` Page Size Alignment (Google Play `targetSdk 35+` Requirement)
- [ ] **`:api` Native Library (`libcpp.so`)**:
  - Rebuild `:api` with NDK r28+ (or pass `-Wl,-z,max-page-size=16384` in `api/CMakeLists.txt`) so all `.so` ELF `LOAD` segments are `16 KB`-aligned.
- [ ] **Jetpack DataStore (`libdatastore_shared_counter.so`)**:
  - Upgrade `androidx.datastore:datastore-preferences` to the latest stable `1.2.x+` release shipping `16 KB`-aligned `libdatastore_shared_counter.so`, and verify with `zipalign -c -P 16 -v 4 app-v2-playstore-release.apk` (and confirm zero `16 KB` compatibility dialog on Android 16/17 devices).

## 6. Legacy `:app` Module Retirement
- [ ] Once `v2.0` staged rollout reaches 100% stability on Play Store:
  - Update CI workflows (`.github/workflows/`) to build, test, and package `:app-v2` across `alpha`, `baidu`, and `playstore` flavors.
  - Remove `:app` from `settings.gradle` (or archive under legacy tag `v1.31-final`) and rename `:app-v2` → `:app` if desired.
