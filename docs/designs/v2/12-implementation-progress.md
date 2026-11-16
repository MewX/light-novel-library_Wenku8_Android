# 12 · `v2.0` Autonomous Implementation Progress Tracker (`:app-v2`)

**Module:** `:app-v2` (Sibling module alongside `:app` and `:api` / `:api-stub`)  
**Dev Application ID:** `org.mewx.wenku8.v2dev` *(isolated from production `org.mewx.wenku8` during development; switches to `org.mewx.wenku8` at final cutover)*  
**Language & UI Stack:** 100% Kotlin 2.x · Jetpack Compose · Material 3 Expressive (`M3e` `1.5.0-rc01`) · Room SQLite (`WAL`) · DataStore · OkHttp 5.x · Coil 3  
**`:api` Submodule Rule:** Keep `:api` completely unchanged; surface any `:api` questions/blockers to maintainer immediately.

---

## Verification & Device Rules (Mandatory Per Incremental Commit)

1. **Dual-Variant Build & Test Gate**:
   - Real `:api` build: `./gradlew :app-v2:testAlphaDebugUnitTest :app-v2:assembleAlphaDebug`
   - Public CI `:api-stub` build: `./gradlew -DforceApiStub :app-v2:testAlphaDebugUnitTest :app-v2:assembleAlphaDebug`
   - Legacy `:app` regression check when root build files change: `./gradlew :app:assembleAlphaDebug`
2. **Connected Pixel 8 (`36121FDJH000PU`) Work-Profile Safety Rule**:
   - Always use SDK `adb` (`/home/mewx/Android/Sdk/platform-tools/adb`, v41).
   - Device has Personal Profile (`User 0`) and Work Profile (`User 10`).
   - **Always install, launch, and instrument strictly on `--user 0`** (`adb install --user 0`, `adb shell am start --user 0`, plus `installation { installOptions '--user', '0' }` in `app-v2/build.gradle`), and verify `adb shell pm list packages --user 10 org.mewx.wenku8.v2dev` stays empty!
3. **Code Readability Standard**:
   - Descriptive, self-documenting identifier names (no single-letter legacy abbreviations) and clear architectural comments on non-obvious concurrency, SQLite, parser, and gesture logic.

---

## Phase-by-Phase Checklist

### Phase 0: Foundation, M3e Theme, Atomic Room SQLite & Legacy `v1.31` Migrator
- [x] **`P0.1` — `:app-v2` Module Scaffold, M3 Expressive (`M3e`) Theme & 4-Tab Adaptive Shell**
  - Created `:app-v2` (`org.mewx.wenku8.v2dev`, `minSdk = 24` per official `androidx.compose.material3:1.5.0-rc01` M3e manifest floor, `--user 0` install option, `alpha`/`baidu`/`playstore` flavors).
  - Implemented `Wenku8ExpressiveTheme` (`MaterialExpressiveTheme`, `EinkZeroMotionScheme`, Archival Day `#F6F5EF`, Carbon Night `#111312`, Pure AMOLED, E-Ink `1.dp` `#111111` border + zero-animation mode, Monet dynamic color, scoped WCAG-AA `CoverThemeEngine`).
  - Implemented `AdaptiveMainScaffold` (`ShortNavigationBar` + `WideNavigationRail` for `书架 · 最新 · 排行 · 我的`, defaulting to `最新` with configurable `本地书架` home & smart offline fallback).
  - Verified 8/8 JVM + Robolectric Compose UI tests (`:api` + `-DforceApiStub`), `:app` regression build, and live install/launch/screenshot strictly on Pixel 8 `User 0` (`User 10` empty).
- [x] **`P0.2` — Unified Room SQLite (`wenku8_v2.db` WAL), Atomic `@Upsert` TOC Sync & Full Settings DataStore**
  - Implemented all 9 Room entities + exported `v1` schema JSON with decoupled FKs (`CHAPTER_CONTENTS`, `READING_PROGRESS`, `BOOKMARKS_NOTES` → `NOVELS(aid)`).
  - Implemented `NovelTocSyncRepository.syncNovelIndexAtomically` (`@Upsert`, stale-response guard, SHA-256 fast-path, monotonic `toc_version`, 400-ID chunked pruning, `READING_PROGRESS.vid` auto-healing, and `SUBSTR` `2 MB CursorWindow`-safe reader).
  - Implemented `UserPreferencesRepository` (`settings.preferences_pb`) preserving 100% of `v1.31` `SettingItems` + `DefaultHomeDestination` (`LATEST` default / `BOOKSHELF`) + `autoOptimizeConnectionEnabled` and wired `MainActivity`.
- [x] **`P0.3` — Legacy `v1.31` Internal-Storage & SAF Migrator (`LegacyV1StorageMigrator`) + XML Parsers** *(Completed: `WenkuXmlParsers` with mixed-CDATA volume titles, stray-tag recovery, BOM/whitespace sanitization, & cloud shelf empty-vs-invalid disambiguation; Coil 3 `OfflineFirstWenkuImageFetcher` serving `saves/imgs/` & `imgs/` in place without copying; idempotent `LegacyV1StorageMigrator` migrating all 11 settings, `bookshelf_local.wk8`, `intro/{aid}-*.xml`, `novel/{cid}.xml` in 50-chapter batches, `read_saves_v1.wk8` / `read_saves.wk8`, & `search_history.wk8` + SAF `importLegacyBackupTree`; verified 31/31 unit/Robolectric tests across `:api` & `-DforceApiStub` + Pixel 8 `User 0`)*
  - Parse `settings.wk8` (`||||` & `::::`), `bookshelf_local.wk8`, `intro/{aid}-{intro,introfull,volume}.xml`, `novel/{cid}.xml` (25-chapter WAL batches + `imageUrlsJson`), `read_saves_v1.wk8` (`:`), `read_saves.wk8` (`,,`), and `search_history.wk8` (`[0/1...]`).
  - `OfflineFirstWenkuImageFetcher` reading `filesDir/saves/imgs/` & `filesDir/imgs/` before network.

### Phase 1: Discovery Feeds (`最新` · `排行`) & Unified Search Studio (`5s` Cooldown Queue)
- [x] **`P1.1` — `:api` Coroutine Gateway (`WenkuApiClient`) & Paginated Feed Engine** *(Completed: pluggable `WenkuTransport` + `LegacyLightNetworkTransport`, stub- & JNI-safe `WenkuApiClient` / `WenkuCatalogGateway`, all 13 `NovelSortedBy` feed mappings, completeness-monotonic `DiscoveryRepository` card cache, `<result>` root gateway validation, instant local Room filter, & auto-queued `5s` online search cooldown coordinator; verified across real `:api` & `-DforceApiStub`)*
  - Clean Kotlin repository wrapping `Wenku8API` + `LightNetwork` / `RelayHttpTransport` + `Wenku8Parser` / `NovelListWithInfoParser` with `api-stub` safety.
- [~] **`P1.2` — `LatestScreen` (`最近更新 · 最新入库 · 完结精品`) & `RankingsScreen` (2D Metric × Period Matrix)**
  - Real data loading, pagination, pull-to-refresh, E-Ink card compliance, and contextual `[ 🩺 网络检测 ]` error chip.
- [ ] **`P1.3` — `UnifiedSearchScreen` (`0ms` Local Room Shelf Filter + Auto-Queued `5s` Online Search & History)**
  - Title (`articlename`), Author (`author`), and numeric `AID` jump + live `5s` server cooldown countdown queue + 20-item Room search history.

### Phase 2: `OkHttpProbeTransport`, `RelayHttpTransport`, `"自动优化连接"` & `NetworkDiagnosticsScreen`
- [ ] **`P2.1` — `IpLiteralParser`, `DiagnosticsEndpointConfig`, `OkHttpProbeTransport` & `RelayHttpTransport`**
  - DNS-free literal IPv4/IPv6 parser (`InetAddress.getByAddress`), `Accept-Encoding: identity` probe adapter, route-generation `ConnectionPool`s (`fastFallback(true)` default vs. `fastFallback(false)` pinned), and `EventListener.requestHeadersStart` fallback/replay rules.
- [ ] **`P2.2` — `DiagnosticsRunner`, `DiagnosticsNetworkMonitor`, `ManifestRepository` & `RelayRouteController`**
  - Shared 2-slot stalled-DNS bound, `<= 6`-call bootstrap recovery, `<= 18`-probe / `25s` compact idle tournament (`5+6+1` warm-reuse guard), and rolling 24h `120/60` budget store.
- [ ] **`P2.3` — `NetworkDiagnosticsScreen` M3e UI & Pre-Share Privacy `ModalBottomSheet` (`wenku8-network-report-v1`)**
  - Scoped `FLAG_KEEP_SCREEN_ON`, rotation survival, E-Ink static progress, `EventListener` attempt breakdown, and `FileProvider` read-only JSON export.

### Phase 3: Community Reviews 2.0, Profile, Account Auth, Settings Studio & `.wk8backup.zip`
- [ ] **`P3.1` — Account Auth Lifecycle (`UserSessionRepository`), Login Sheet, `每日签到` & `NoticeString`**
- [ ] **`P3.2` — `ProfileAndStudioScreen` (100% `v1.31` Settings UI, Header Wallpaper, Fast/Deep Cache Clean & `.wk8backup.zip` Export/Smart-Merge)**
- [ ] **`P3.3` — `ReviewListScreen` & `ReviewThreadScreen` (UID Avatars, Floor Quotes, `10s` Cooldown & Auto-Saved `REVIEW_DRAFTS`)**

### Phase 4: Bookshelf 2.0, `NovelDetailScreen`, Atomic 4-Mode Downloader & Server-Support Ad
- [ ] **`P4.1` — `BookshelfScreen` (Hero Resume Carousel, Grid/List/3D Spine, Local `#Tag`/Imprint Filters, Cloud Sync & Contextual Batch Toolbar)**
- [ ] **`P4.2` — `NovelDetailScreen` (`CoverThemeEngine`, Reading `SplitButtonLayout`, Inline Volume Accordions, Zero-CLS Lazy `AdView` & 4-Mode `WorkManager` Downloader)**

### Phase 5: `Wenku8ReaderEngineV2`, Artbook Spreads, TTS, Reading Stats & Cutover Prep
- [ ] **`P5.1` — `WenkuChapterParser` + `StaticLayoutPaginator` (CJK Kinsoku) + 3-Chapter `ChapterWindowBuffer`**
- [ ] **`P5.2` — `UnifiedReaderScreen` (6 Page-Turn Delegates, Opaque Active-Slider Reflow Capsule, `8–36 sp`, Custom Font/BG, Volume Keys)**
- [ ] **`P5.3` — `ArtbookLightboxOverlay` (`BitmapRegionDecoder` Tiling, `跨页拼合` Double-Page Spread, Save/Share), In-Book Search & System TTS**
- [ ] **`P5.4` — Reading Statistics Heatmap, Glance Home Widget & Final Polish / Cutover Checklist**
