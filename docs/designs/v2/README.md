# Wenku8 Android `v2.0` — Complete Material 3 Expressive (`M3e`), Motion & Architecture Design Suite

Welcome to the design and architectural documentation for **Wenku8 Android `v2.0`** (`minSdk = 23`), the generational leap from `studio-android v1.x` (2015 Material Design 1) to an authentic **Material 3 Expressive (`M3e`) + "Reading Archive" (纸页与档案)** native Android experience.

---

## Document Index (`docs/designs/v2/`)

| # | Document | Summary |
| :- | :--- | :--- |
| **01** | [`01-v1-codebase-and-feature-investigation.md`](./01-v1-codebase-and-feature-investigation.md) | Exhaustive audit of `studio-android` (`v1.31`), historical `0.5.x` ("典藏版"), all 15 Activities, 6 Fragments, both Reader engines, `:api` v1/v2 attested relay, `.wk8` storage, 4-mode offline downloader, `NovelInfoActivity` AdMob placement, and `1.x` UX pain points. |
| **02** | [`02-wenku8-flutter-comparative-analysis.md`](./02-wenku8-flutter-comparative-analysis.md) | Deep-dive benchmark of `mikufoxxx/Wenku8_Flutter` (`v0.5.6+2277`): its "Reading Archive" aesthetic, 3D Archive Carousel, `PaperCoverFlight`, `ReaderSpineTurn`, vertical CJK typesetting, live sheet preview, and full feature parity matrix adapted to native M3e & `Wenku8API`. |
| **03** | [`03-material3-expressive-and-motion-standards.md`](./03-material3-expressive-and-motion-standards.md) | **Material 3 Expressive (`M3e`)** visual system: 4 lighting modes (Archival Day `#F6F5EF`, Carbon Night `#111312`, Pure AMOLED, E-Ink `1.dp` High-Contrast), scoped `CoverThemeEngine`, `ShortNavigationBar` (`64.dp`) & `WideNavigationRail`, contextual `HorizontalFloatingToolbar`, reading-only `SplitButtonLayout`, active-only `LinearWavyProgressIndicator`, `MaterialShapes`, and **12 signature transitions**. |
| **04** | [`04-user-flow-and-information-architecture.md`](./04-user-flow-and-information-architecture.md) | User flows & information architecture: preserving `v1.31`'s familiar **4-Destination M3e Shell (`书架 · 最新 · 排行 · 我的`)** via `NavigationSuiteScaffold`, foldable `ListDetailPaneScaffold`, offline-first startup, 1-tap resume, 2D Ranking Matrix, `5s` auto-queued Search Studio, Account Auth, and `"自动优化连接"`. |
| **05** | [`05-screen-by-screen-ui-specifications.md`](./05-screen-by-screen-ui-specifications.md) | Detailed M3e wireframes, component hierarchies, production-clean localization rules, non-UX-harming inline `NovelDetailScreen` server-support Ad card (CLS = 0), opaque active-slider reflow capsule, and complete Account Login/Register/Logout specs. |
| **06** | [`06-technical-architecture-and-migration-plan.md`](./06-technical-architecture-and-migration-plan.md) | Technical blueprint (`minSdk = 23`) & 6-phase incremental roadmap: Kotlin Coroutines + Compose M3e ViewModels, unified Room SQLite (`wenku8_v2.db`), `RelayHttpTransport` (OkHttp `EventListener` + Coil 3 CDN isolation), and `api-stub` CI safety. |
| **07** | [`07-ui-mockups-and-interactive-prototype.md`](./07-ui-mockups-and-interactive-prototype.md) | **Visual UI Mockups & Interactive M3e Prototype**: 6 high-DPI screen mockup boards (`mockups/01..06.png`), M3e production refinement notes, and an interactive HTML5/CSS3/JS sandbox (`mockups/interactive-prototype.html`) featuring all 4 tabs and live Network Diagnostics. |
| **08** | [`08-api-grounded-ui-and-expressive-interactions.md`](./08-api-grounded-ui-and-expressive-interactions.md) | **Strict API-Grounded UI Mapping & Light Novel / Artbook Interactions**: 1:1 mapping of active `Wenku8API.java` endpoints (13 `NovelSortedBy` sorts across `最新` & `排行`, `do=meta`, `articlename\|author` search, `BookshelfListParser`, `ReviewList` 10s/7-char rules, `UserInfo` + `do=sign`), exclusion of dead stubs (`do=vote`, `getLibraryList`), and `api-stub` contracts. |
| **09** | [`09-room-database-and-backup-archive-design.md`](./09-room-database-and-backup-archive-design.md) | **Unified Room SQLite Database, Atomic TOC Versioning & Backup Archive (`wenku8_v2.db`)**: Full SQLite storage (including `chapter_contents` with 2 MB `CursorWindow` guards), **`@Upsert` + monotonic `toc_version` atomic TOC sync (zero `REPLACE` cascade data loss)**, `.wk8backup.zip` `VACUUM INTO` Export/Smart-Merge Import, and safe `v1.x` `.wk8` migration. |
| **10** | [`10-next-gen-reader-engine-architecture.md`](./10-next-gen-reader-engine-architecture.md) | **Next-Gen Reader & Page-Turning Engine (`Wenku8ReaderEngineV2`)**: Hardware-accelerated `StaticLayout` + CJK *Kinsoku Shori* (`LineBreakConfig` + API 23–32 fallback), 3-Chapter Sliding Ring Buffer, 6 Official-AndroidX Page-Turn Delegates, `BitmapRegionDecoder` Artbook `口绘` spreads, and Predictive-Back-safe edge zones. |
| **11** | [`11-network-diagnostics-and-auto-routing-ui.md`](./11-network-diagnostics-and-auto-routing-ui.md) | **Network Diagnostics (`网络检测`) & Automatic Connection Optimization (`自动优化连接`)**: Native `v2.0` (`minSdk = 23`) M3e UI & OkHttp `RelayHttpTransport` architecture—default-on candidate-IP promotion, per-hostname Relay v1/v2 isolation from image CDNs, 2-slot DNS stall protection, E-Ink-safe benchmark UI, and privacy-redacted `wenku8-network-report-v1` sharing. |

---

## Key Highlights of the `v2.0` Redesign

1. **Authentic Material 3 Expressive (`M3e`) + Familiar 4-Tab Structure**:
   - Uses M3e `ShortNavigationBar` (`64.dp`) on phones and spring-expanding `WideNavigationRail` on foldables/tablets while preserving `v1.31`'s exact 4 primary destinations (**`书架 · 最新 · 排行 · 我的`**), reserving `HorizontalFloatingToolbar` strictly for contextual Reader and batch-selection actions.
2. **Atomic Room SQLite (`wenku8_v2.db`) & Zero-Loss TOC Updates**:
   - Consolidates metadata, reading anchors, bookmarks, drafts, and offline chapter texts into WAL-mode SQLite; `CHAPTER_CONTENTS` and `READING_PROGRESS` are decoupled from volume/chapter cascade deletes and synchronized via single-`@Transaction` `@Upsert` + monotonic `toc_version` checks.
3. **Default-On `"自动优化连接"` & Transparent `网络检测`**:
   - Automatically screens and promotes verified faster Cloudflare ingress routes when foreground I/O is idle (and recovers blocked default routes within 20s), paired with a 1-tap `NetworkDiagnosticsScreen` and privacy-first report sharing.
4. **Sustainable, Non-UX-Harming Server Support**:
   - Retains a single zero-CLS, lazy-initialized inline banner slot exclusively on `NovelDetailScreen` between Synopsis and Volume Accordions (100% absent from the Reader and Bookshelf, collapsing cleanly offline or without Play Services).
