# 07 · Authentic Material Design 3 & Material 3 Expressive UI Mockups (`v2.0`)

This document presents **6 high-DPI Material Design 3 & Material 3 Expressive (Android 16 / Material You) screen mockup boards** (`1240×1240` PNGs in [`docs/designs/v2/mockups/`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/docs/designs/v2/mockups/)) alongside an interactive browser prototype ([`interactive-prototype.html`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/docs/designs/v2/mockups/interactive-prototype.html)).

---

## Core Material Design 3 & M3 Expressive Components Used

1. **Official M3 HCT Tonal Surface Hierarchy (Zero 1px Industrial Borders on Filled Cards)**:
   - Uses `surface` (`#FAF8FF` light / `#111318` dark), `surfaceContainerLow` (`#F4F3FA` / `#191C20`), `surfaceContainer` (`#EEEDF4` / `#1D2024`), `surfaceContainerHigh` (`#E8E7EF` / `#282A2F`), `primaryContainer` (`#D4E3FF` / `#214876`), `secondaryContainer` (`#D8E3F8` / `#3C4758`), and `tertiaryContainer` (`#F6D9FF` / `#543F5E`).
2. **M3 Docked `SearchBar` & Fullscreen `SearchView`**:
   - Canonical `56dp` pill (`surfaceContainerHigh`, `28dp` corner radius) with leading `Search` vector icon and trailing `Avatar` (`primaryContainer`), expanding via shared-bounds container transform into a fullscreen M3 `SearchView`.
3. **M3 `HorizontalMultiBrowseCarousel`**:
   - Signature Material 3 Expressive carousel at the top of the Bookshelf (`Continue Reading`), pairing a wide Hero card (`24dp` radius) with medium and peeking squished cards that dynamically morph width on horizontal scroll.
4. **M3 `FilterChip`, `AssistChip`, `PrimaryTabRow`, and `SingleChoiceSegmentedButtonRow`**:
   - Canonical `8dp`-corner `FilterChip` with leading checkmark icon and `secondaryContainer` fill when selected; `PrimaryTabRow` with `3dp` rounded-top active indicator bar; `SegmentedButtonRow` with connected pill borders.
5. **M3 `NavigationBar` vs. M3 Expressive `HorizontalFloatingToolbar` + `ExtendedFloatingActionButton`**:
   - Board 01 compares the canonical full-width `80dp` M3 `NavigationBar` (`64×32dp` `secondaryContainer` active pill indicator) on the left with the M3 Expressive `HorizontalFloatingToolbar` + squircle `ExtendedFloatingActionButton` on the right.
6. **M3 Expressive `SplitButton`, `LinearWavyProgressIndicator`, and Android 16 Vertical-Handle `Slider`**:
   - `SplitButton` (`28dp` outer corners, `4dp` inner split corners) for primary "Continue Reading" + dropdown action.
   - `LinearWavyProgressIndicator` with sinusoidal active wave, track gap, flat inactive track, and trailing stop dot.
   - Android 16 M3 `Slider` (`16dp` thick track + vertical `4×44dp` thumb handle bar with side gaps + stop dot).
7. **M3 `ModalBottomSheet` (with `32×4dp` Drag Handle)**:
   - `28dp` top corners, `surfaceContainerLow` surface, and centered `32×4dp` drag handle pill for both the 4-Mode Offline Downloader and the Reader Typography Sheet.

---

## Visual Mockup Boards (`docs/designs/v2/mockups/`)

### Board 01 — Bookshelf: M3 Multi-Browse Carousel, Tonal Cards & Navigation Comparison
![Board 01 — Bookshelf M3 Light vs M3 Dark](mockups/01-bookshelf-day-and-3d-carousel.png)

### Board 02 — Explore Rankings (`PrimaryTabRow` + `SegmentedButton`) & Fullscreen M3 `SearchView`
![Board 02 — Explore Rankings & M3 SearchView](mockups/02-explore-rankings-and-search.png)

### Board 03 — Novel Detail (Dynamic Cover Material You Seed, `SplitButton` & `ModalBottomSheet`)
![Board 03 — Novel Detail & M3 ModalBottomSheet](mockups/03-novel-detail-and-volume-accordions.png)

### Board 04 — Unified Reader: Immersive 3D Page Turn & Android 16 Handle Sliders Sheet
![Board 04 — Unified Reader & M3 Typography BottomSheet](mockups/04-unified-reader-and-transparent-sheet.png)

### Board 05 — Community Reviews (M3 Cards & `OutlinedTextField` Reply Bar) & Material You Settings
![Board 05 — Community Reviews & Material You Settings](mockups/05-reviews-and-profile-studio.png)

### Board 06 — Adaptive Foldable & Tablet UX (`NavigationRail` + `ListDetailPaneScaffold` & Dual-Page Spread)
![Board 06 — Adaptive M3 NavigationRail & ListDetailPaneScaffold](mockups/06-foldable-split-pane-and-dual-page-spread.png)
