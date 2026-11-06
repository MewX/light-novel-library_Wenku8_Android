# Wenku8 Android `v2.0` — Complete UI/UX, Motion & Architecture Design Suite

Welcome to the design and architectural documentation for **Wenku8 Android `v2.0`**, the generational leap from `studio-android v1.x` (2015 Material Design 1) to a modern **Material 3 Expressive + "Reading Archive" (纸页与档案)** native Android experience.

---

## Document Index (`docs/designs/v2/`)

| # | Document | Summary |
| :- | :--- | :--- |
| **01** | [`01-v1-codebase-and-feature-investigation.md`](./01-v1-codebase-and-feature-investigation.md) | Exhaustive audit of `studio-android` (`v1.31`), historical `0.5.x` ("典藏版"), all 15 Activities, 6 Fragments, both Reader engines, `:api` v1/v2 attested relay, `.wk8` storage, 4-mode offline downloader, prior MD3/Compose branches, and `1.x` UX pain points. |
| **02** | [`02-wenku8-flutter-comparative-analysis.md`](./02-wenku8-flutter-comparative-analysis.md) | Deep-dive benchmark of `mikufoxxx/Wenku8_Flutter` (`v0.5.6+2277`): its "Reading Archive" + Liquid Glass aesthetic, Monet + image loupe palette, 3D Archive Carousel, `PaperCoverFlight` 3D hero bend, `ReaderSpineTurn` 3D page peel, vertical CJK typesetting, live transparent sheet preview, and full feature parity matrix. |
| **03** | [`03-material3-expressive-and-motion-standards.md`](./03-material3-expressive-and-motion-standards.md) | Material 3 Expressive visual system: 4 lighting modes (Archival Day `#F6F5EF`, Carbon Night `#111312`, Pure AMOLED, E-Ink Zero-Motion), `CoverThemeEngine` dynamic book-cover palettes, `HorizontalFloatingToolbar`, `SplitButtonLayout`, `LinearWavyProgressIndicator`, `MaterialShapes` morphing, `MotionScheme.expressive()` springs, and **12 signature transitions**. |
| **04** | [`04-user-flow-and-information-architecture.md`](./04-user-flow-and-information-architecture.md) | Complete redesign of the app's user flows: replacing the 2015 Hamburger Drawer and disconnected Activities with a 3-pillar Adaptive Shell (`Bookshelf`, `Explore`, `Profile`), foldable `ListDetailPaneScaffold`, 1-tap reading resume, 2D Ranking Matrix, Unified Search Studio, and non-blocking `WorkManager` download queue. |
| **05** | [`05-screen-by-screen-ui-specifications.md`](./05-screen-by-screen-ui-specifications.md) | Detailed wireframes, component hierarchies, shared-element keys, and micro-interaction specs for all 9 screens (`AdaptiveMainScaffold`, `BookshelfScreen`, `ExploreScreen`, `UnifiedSearchScreen`, `NovelDetailScreen`, `ReviewScreens`, `UnifiedReaderScreen`, `ImageDetailScreen`, `ProfileAndStudioScreen`). |
| **06** | [`06-technical-architecture-and-migration-plan.md`](./06-technical-architecture-and-migration-plan.md) | Technical blueprint & 6-phase incremental roadmap: wrapping the 328-test pure-logic domain seams and `:api` submodule in Kotlin Coroutines + Compose ViewModels, executing `STORAGE_MIGRATION_PLAN.md` (Room SQLite + Preferences DataStore), and upgrading `WenkuReaderPaginator` with CJK *Kinsoku Shori*. |
| **07** | [`07-ui-mockups-and-interactive-prototype.md`](./07-ui-mockups-and-interactive-prototype.md) | **Visual UI Mockups & Interactive Prototype**: 6 high-DPI side-by-side Material Design 3 & M3 Expressive screen mockup boards (`mockups/01..06.png`) + an interactive clickable HTML5/CSS3/JS prototype (`mockups/interactive-prototype.html`). |
| **08** | [`08-api-grounded-ui-and-expressive-interactions.md`](./08-api-grounded-ui-and-expressive-interactions.md) | **API-Grounded UI Mapping & Light Novel / Comic Expressive Interactions**: Exhaustive 1:1 mapping of `Wenku8API.java` (13 `NovelSortedBy` sorts, 14 `getLibraryList` imprints, `do=meta` stats, `do=vote` daily push, `searchtype=articlename\|author`, `BookshelfListParser` diff, `ReviewList` 10s/7-char rules, `UserInfo` + `do=sign`) and M3 Expressive interactions for Light Novel & Comic/Illustration (`<!--image-->` Artbox, double-page `口绘` spread, volume `<chapter>插图</chapter>` quick-chips, Bunkobon vertical mode). |

---

## Key Highlights of the `v2.0` Redesign

1. **Material 3 Expressive + "Reading Archive" Visual Identity**:
   - Warm low-glare reading paper surfaces (`#F6F5EF` day / `#111312` night / `#000000` AMOLED / `#FFFFFF` E-Ink), Android 12+ Monet, 6 literary swatches, and **`CoverThemeEngine`** (automatic book-cover tonal extraction & ambient `CoverGlow` on Novel Detail).
2. **12 Signature Physics-Driven Transitions & Animations**:
   - **5-Element `SharedTransitionLayout`** (Card bounds, 3D arc cover flight, title, author, badge).
   - **3D Book-Cover Hinge Opening** into the Reader (`rotationY: 0° -> -115°` with moving spine shadow).
   - **Android 15/16 Predictive Back** scrubbing shared elements and 3D book covers in real time with finger progress.
   - **AGSL `RuntimeShader` 3D Curved Page Peel** tracking live `(touchX, touchY)` with back-of-page bleed-through.
   - **Live Transparent Jelly Sheet** (`88%` transparent while dragging typography sliders so chapter text reflows live underneath).
   - **3D Perspective Spine Carousel** on the Bookshelf with per-spine haptic detents.
   - **`MaterialShapes` Polygon Morphing** (`Sunny`, `Cookie9Sided`, `Clover4Leaf`) for rank badges, selection states, and check-in celebrations.
3. **Redesigned, Zero-Friction User Flow**:
   - **1-Tap Resume** from Bookshelf Hero Card; **Inline Collapsible Volume Accordions** on Novel Detail (eliminating the right-side drawer that conflicted with edge-back gestures).
   - **2D Ranking Matrix** (`4 Metrics × 4 Time Periods`) + **Press/Imprint Browser** + **Multi-Tag Intersection** replacing the 12-tab `ViewPager`.
   - **Unified Container-Transform Search Studio** searching Local Bookshelf (`< 5ms`) and Online Catalog (`Title`, `Author`, `#Tag`, `AID`) simultaneously.
   - **Non-Blocking Background Downloads** (`WorkManager` + `LinearWavyProgressIndicator`) preserving all 4 Wenku8 download modes (*Smart Update*, *Select Volumes*, *Check Updates*, *Force Overwrite*) without modal blocking dialogs.
   - **Unified Reader 2.0** allowing live in-reader switching between *Horizontal Paged*, *Foldable Dual-Page Spread*, *3D Page Peel*, *Continuous Multi-Chapter Scroll Stream*, and *Vertical Right-to-Left CJK* with CJK *Kinsoku Shori* punctuation rules and cross-volume navigation.
