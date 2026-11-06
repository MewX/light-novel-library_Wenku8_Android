# 05 — Screen-by-Screen UI/UX, Wireframe & Micro-Interaction Specifications (`v2.0`)

**Status:** Design Specification  
**Scope:** Complete visual layout, component hierarchy, shared-element keys, and gesture choreography for all 9 screens in Wenku8 Android `v2.0`.

---

## Screen 1: `AdaptiveMainScaffold` (Root Shell & Chrome)

### 1.1 Compact Portrait Layout (Phones `< 600.dp`)
```text
+-------------------------------------------------------------------+
| [Status Bar - Edge-to-Edge Transparent]                           |
| 01 / PERSONAL ARCHIVE                      [🔍 Search] [☁️] [⋮]  |
| 书架 Bookshelf (64)                                               |
| ~~~ Progressive Blur Header Underlay ~~~~~~~~~~~~~~~~~~~~~~~~~~~~ |
|                                                                   |
|   [ Active Tab Content: Bookshelf / Explore / Profile ]           |
|                                                                   |
| ~~~ Progressive Blur Bottom Underlay ~~~~~~~~~~~~~~~~~~~~~~~~~~~~ |
| +---------------------------------------------------------------+ |
| | 📥 正在智能更新《狼与香辛料》 (第17卷 4/12)   [~~~Wavy~~~] [×] | | <- Non-blocking Mini-Bar
| +---------------------------------------------------------------+ |
|      +------------------------------------------+   +--------+    |
|      |  [📚 书架]     [🧭 发现]     [👤 我的]   |   |  🔍+   |    | <- HorizontalFloatingToolbar + FAB
|      +------------------------------------------+   +--------+    |
| [Gesture Navigation Bar Inset]                                    |
+-------------------------------------------------------------------+
```

### 1.2 Medium / Expanded Layout (Foldables & Tablets `>= 600.dp`)
```text
+-----------------------------------------------------------------------------------+
| [NavRail] | [Master Pane: 42% Width]        || [Detail Pane: 58% Width]           |
|           | 01 / PERSONAL ARCHIVE   [🔍][☁️] || DETAIL / WK-00005        [⭐] [⬇️] |
|  [📚]     | +-----------------------------+ || +-------+  狼与香辛料               |
|  书架     | | Continue Reading Hero Card  | || | Cover |  支仓冻砂 · 电击文库      |
|           | +-----------------------------+ || | Glow  |  [奇幻] [旅行] [经商]     |
|  [🧭]     | [全部] [在读] [有更新] [已缓存] || +-------+                          |
|  发现     | +--------+ +--------+ +-------+ || [ ▶ 继续阅读 · 第17卷 (68%) | ▾ ]  |
|           | | Cover1 | | Cover2 | |Cover3 | || ---------------------------------- |
|  [👤]     | +--------+ +--------+ +-------+ || ▼ 第一卷 (11章 · 已缓存)           |
|  我的     | | Cover4 | | Cover5 | |Cover6 | || ▶ 第二卷 (12章 · 已缓存)           |
|           | +--------+ +--------+ +-------+ || ▶ 第三卷 (11章 · 未缓存)           |
|  -------  |                                 ||                                    |
|  [Mini    |                                 ||                                    |
|   Resume] |                                 ||                                    |
+-----------------------------------------------------------------------------------+
```

- **Scroll-Coupled Floating Chrome**:
  - Scrolling down hides the `HorizontalFloatingToolbar` with a spring slide (`translationY` + `fastSpatialSpec()`); scrolling up by `> 12.dp` or reaching the top springs it back into view.
  - Re-tapping the currently active tab icon smoothly scrolls the active list/grid back to item `0` (`animateScrollToItem(0)`).

---

## Screen 2: `BookshelfScreen` (`01 / PERSONAL ARCHIVE`)

### 2.1 Header & Filter Rail
- **Masthead**: `01 / PERSONAL ARCHIVE` monospace label above `书架` title with live book count badge.
- **Top Actions**:
  - `🔍 Search Pill`: Expands via `sharedBounds("search-bar")` into `UnifiedSearchScreen` (pre-focused on local shelf + online).
  - `☁️ Cloud Sync Button`: Triggers `BookshelfSync` (rotates icon `180°` with spring + shows non-blocking `LinearWavyProgressIndicator`).
  - `⬇️ Batch Update SplitButton`: Default action runs **Smart Update (智能更新)**; dropdown menu offers *Check Updates (检查更新)*, *Download All (下载全部)*, and *Force Update (覆盖下载)*.
  - `⊞ View Switcher`: Cycles through **Cover Grid (`2–4` cols)**, **Editorial List**, and **3D Perspective Spine Carousel** using `AnimatedContent` scale + fade.
- **Filter & Sort Chips Row**:
  - Connected `ToggleButton` chips: `全部 (All)`, `🔥 在读 (Reading)`, `✨ 有更新 (Updated)`, `💾 已缓存 (Cached)`, plus user-created category tags.
  - Trailing sort chip: `最近阅读` / `最近更新` / `手动排序` / `书名拼音`.

### 2.2 "Continue Reading" Hero Card (Pinned at Top of Shelf)
- Displays the most recently read novel with a subtle horizontal cover-derived gradient, cover thumbnail (`sharedElement("cover-image-{aid}")`), current volume & chapter title (`第三卷 · 第二话 命与心`), a `LinearWavyProgressIndicator` or pill bar showing chapter percentage (`68%`), and a 1-tap **`继续阅读 ▶`** button that triggers the **T2 3D Book-Cover Opening Transition** straight into `ReaderScreen`.

### 2.3 Three Switchable Shelf Views
1. **Cover Grid View (Default)**:
   - `3` columns on phones, `4–6` on foldables/tablets.
   - Each card displays:
     - Cover image (`aspectRatio(0.71f)`, `RoundedCornerShape(8.dp)`) with subtle inner border.
     - Top-right badge: Unread new-chapter dot (`✨ 有新章节`) or cached indicator (`💾`).
     - Bottom overlay gradient with reading progress bar (`0–100%`).
     - 2-line title (`titleSmall`) + 1-line latest read/published chapter (`bodySmall`).
2. **Editorial List View**:
   - Upgraded version of `1.x`'s `view_novel_item.xml`:
     - Cover (`72.dp × 100.dp`), Archive Code (`WK-01749`), Title, Author, Latest Chapter (`最新: 第十七卷 插图`), Last Update Date, and status pills (`已读至 第3卷 42%`, `已缓存 5/17 卷`).
     - **Swipe Actions (`SwipeToDismissBox`)**: Swipe right to **Continue Reading**; swipe left to open **Download / Clean Cache / Remove** sheet.
3. **3D Perspective Spine Carousel (`ArchiveSpineCarousel`)**:
   - Interactive horizontal 3D shelf (`graphicsLayer` with perspective camera).
   - Dragging horizontally scrubs through vertical book spines with `SEGMENT_FREQUENT_TICK` haptics; the centered spine pulls out and rotates open to `0°`, displaying a floating glass inspection card with `"继续阅读"` and `"作品详情"`.

### 2.4 Long-Press Batch Management Bar
- Long-pressing any book card morphs its corner badge into a `Cookie9Sided` checkmark and replaces the bottom floating toolbar with a **Batch Action Dock**:
  - `全选 (Select All)` · `更新所选 (Update)` · `清理缓存 (Clear Cache)` · `移出书架 (Remove)` · `完成 (Done)`.

---

## Screen 3: `ExploreScreen` (`02 / WENKU CATALOG`)

### 3.1 Unified Discovery Modes (Top Segmented Rail)
```text
+-------------------------------------------------------------------+
| 02 / WENKU CATALOG                           [🔍 搜索书名/作者/#标签] |
| 发现与榜单                                            [网格/列表] |
+-------------------------------------------------------------------+
| [⚡ 最近更新] [🏆 热门榜单] [🏛️ 文库分类] [🏷️ 题材标签] [✅ 完结精品] |
+-------------------------------------------------------------------+
| (When 🏆 热门榜单 is active:)                                      |
| 维度: [🔥 人气点击] [👍 读者推荐] [⭐ 收藏人数] [📏 总字数]         |
| 周期: ( 今日 Day )  ( 本周 Week )  ( 本月 Month )  ( 总榜 All )     |
+-------------------------------------------------------------------+
| #01 (Sunny Polygon Badge)                                         |
| +--------+  败犬女主太多了！(Make Heroine ga Oosugiru!)           |
| | Cover  |  雨森焚火 · 小学馆 · 连载中 · 2026-10-18               |
| | 104x146|  [校园] [恋爱] [喜剧] [青梅竹马]                       |
| +--------+  "明明是背景人物的我，为什么会被败犬女主们包围……"       |
+-------------------------------------------------------------------+
```

- **1-Tap 2D Ranking Matrix**:
  - Replaces `1.x`'s 12-tab `RKListFragment` `ViewPager`. Selecting any Metric (`Visit`, `Vote`, `Fav`, `Size`) + Period (`Day`, `Week`, `Month`, `All`) fetches the corresponding `Wenku8API.NovelSortedBy` feed with morphing skeleton placeholders and a `35ms` staggered item cascade.
  - Top 3 items in any ranking display distinctive Material 3 Expressive polygon rank badges (`#01` Gold `Sunny`, `#02` Silver `Cookie9Sided`, `#03` Bronze `Clover4Leaf`).
- **Press / Imprint Browser (`🏛️ 文库分类`)**:
  - Visual grid of Japanese light novel publishers (`电击文库`, `MF文库J`, `角川Sneaker`, `富士见Fantasia`, `Fami通文库`, `GA文库`, `集英社Super Dash`, `讲谈社`, `HJ文库`, `小学馆Gagaga`, `一迅社`, `其他文库`).
- **Multi-Tag Intersection Explorer (`🏷️ 题材标签`)**:
  - Interactive tag cloud grouped by theme (*Genre*, *Protagonist*, *Setting*, *Tone*). Selecting multiple tags filters novels containing **all** chosen tags, with a long-press option to add any tag to **Blocked Tags (`屏蔽标签`)**.

---

## Screen 4: `UnifiedSearchScreen` (Container-Transform Search Studio)

```text
+-------------------------------------------------------------------+
| [←]  🔍 输入书名、作者、#标签 或 AID编号 (如 1749)           [×]  |
+-------------------------------------------------------------------+
| 搜索范围: [ 全部 ]  [ 📖 书名 ]  [ ✍️ 作者 ]  [ #️⃣ 标签 ]  [ 🔢 AID ] |
+-------------------------------------------------------------------+
| 🕒 历史搜索                                          [清空全部 🗑️] |
| ( 狼与香辛料 )  ( 支仓冻砂 )  ( #校园 #恋爱 )  ( 刀剑神域 )        |
+-------------------------------------------------------------------+
| 📚 本地书架匹配 (2)                                               |
|   [Compact Novel Row: 狼与香辛料 · 已读至第17卷 · 点击继续阅读]    |
| 🌐 在线文库搜索结果                                               |
|   [Paginated Online Novel Cards with Shared-Element Cover/Title]  |
+-------------------------------------------------------------------+
```

- **Fixes `1.x` Search History Bugs**:
  - Stored in Room (`search_history` table) instead of the bracket-delimited `search_history.wk8` file (eliminating the `[` phantom-entry and freeze bugs documented in `STABILITY_PLAN.md` lines 66–76!).
- **Dual Instant + Online Results**:
  - As the user types, local bookshelf matches (`BookshelfFilter.filter`) appear **instantly (< 5ms)** in a top collapsible section, while pressing Search (or pausing typing) queries `Wenku8API.searchNovelByNovelName` / `AuthorName`.
  - If the query is numeric (e.g. `1749`), a top card offers **1-tap direct jump to `AID 1749`**.

---

## Screen 5: `NovelDetailScreen` (`DETAIL / WK-{AID}`)

```text
+-------------------------------------------------------------------+
| [←]  DETAIL / WK-00005                        [💬 书评] [🔗 分享] |
|                                                                   |
|   +------------+   狼与香辛料(狼与辛香料)                         |
|   |   Cover    |   ✍️ 支仓冻砂 [›]     🏛️ 电击文库 [›]             |
|   | 120 x 168  |   ✅ 已完结 · 200.4万字 · 更新于 2012-02-08      |
|   | (3D Shared |   🔥 总点击 39.4万 · 👍 推荐 2.29万 · ⭐ 收藏 6003|
|   | + Glow)    |                                                  |
|   +------------+   [❤️ 已在书架]  [👍 投推荐票]  [🖼️ 封面大图]    |
|                                                                   |
| +---------------------------------------------------------------+ |
| |        ▶ 继续阅读 · 第3卷 第2话 (42%)          |      ▾       | | <- SplitButtonLayout
| +---------------------------------------------------------------+ |
|                                                                   |
| 🏷️ 作品标签: (#奇幻) (#旅行) (#经商) (#兽耳) (#贤狼赫萝)          |
|                                                                   |
| 📖 内容简介                                          [展开全部 ▾] |
| 旅行于各地贩卖并收购物品的商人克拉福·罗伦斯，拜访帕斯罗村并离开   |
| 后，在自己的马车上发现了不知从哪里跑来的东西……                    |
|                                                                   |
| 💬 热门书评 (142条讨论)                              [查看全部 ›] |
| +---------------------------------------------------------------+ |
| | "十几年过去了，赫萝依然是最有魅力的女主角" — @MewX · 38回复   | |
| +---------------------------------------------------------------+ |
|                                                                   |
| 📚 分卷与章节目录 (共17卷)     [🔍 搜章节] [⇅ 正序/倒序] [⬇️ 缓存]|
| +---------------------------------------------------------------+ |
| | ▼ 第三卷 (共11章 · 已缓存 11/11)                   [🗑️] [✅]  | | <- Auto-expanded current volume
| |   ├─ 【序章】Prologue                                    ✓    | |
| |   ├─ 【第一话】First Story 妹妹和传说                    ✓    | |
| |   ├─ 👉 【第二话】Second Story 真实与冒牌货 (上次读到)   ✓    | |
| |   └─ 【插图】Illustrations                               🖼️   | |
| +---------------------------------------------------------------+ |
| | ▶ 第四卷 (共10章 · 未缓存)                         [⬇️ 下载本卷]| |
| +---------------------------------------------------------------+ |
+-------------------------------------------------------------------+
```

- **Key UX Upgrades over `1.x` `NovelInfoActivity`**:
  1. **Dynamic Cover Theme (`CoverThemeEngine`)**: Radial blurred cover backdrop (`CoverGlow`) and tonal surface containers smoothly adapt to the book cover's palette.
  2. **No More Right-Side Drawer Conflict**: Volumes expand inline as Material 3 Expressive accordions; the volume with saved reading progress auto-expands and highlights the active chapter.
  3. **`SplitButtonLayout` Primary CTA**: Main area resumes reading immediately; trailing split chevron opens `"从头开始阅读"`, `"切换阅读引擎 (仿真翻页 / 连续卷轴 / 竖排右翻)"`, or `"朗读本章 (TTS)"`.
  4. **Granular Per-Volume & 4-Mode Offline Caching**: Each volume card has a 1-tap download/delete icon (`CircularWavyProgressIndicator` while downloading), and the section header's `[⬇️ 缓存]` button opens the 4-mode download sheet (*Smart Update*, *Select Volumes with Checkboxes*, *Check Updates*, *Force Overwrite*).

---

## Screen 6: `ReviewListScreen` & `ReviewThreadScreen` (`COMMUNITY FORUM`)

- **`ReviewListScreen`**:
  - Header shows novel cover thumbnail, title, and total thread count.
  - Review cards (`MaterialCardView` with `sharedBounds("review-card-{rid}")`) show:
    - Thread Title (`titleMedium`), Author avatar/badge (`userName` + `uid`), Post Time & Last Reply Time, and a pill badge for Reply Count (`💬 38`).
  - Bottom-right `ExtendedFloatingActionButton` (`✍️ 发表书评`) morphs (`sharedBounds`) into the New Review Composer sheet/screen.
- **`ReviewThreadScreen` (Replaces `NovelReviewReplyListActivity`)**:
  - Top card (`01 / ORIGINAL POST`) is the expanded container of the tapped review card.
  - Floor replies (`#2`, `#3`...) cascade in with floor number badges, relative/exact timestamps, and 1-tap `复制 (Copy)` or `引用回复 (Quote #N)` actions.
  - **Bottom Docked Composer**:
    - Auto-saves draft text (`400ms` debounce) per `(aid, rid)`.
    - Live character counter (`>= 7 chars` required by Wenku8) and **live `Wenku8API.searchBadWords()` validation** (highlights restricted words inline with a soft warning chip before submission).

---

## Screen 7: `UnifiedReaderScreen` (`IMMERSIVE READER 2.0`)

### 7.1 Immersive Viewport & 9-Zone Customizable Tap Grid
```text
+-------------------------------------------------------------------+
| VOL.03  第三卷                                     第二话 命与心  | <- Top Marginalia Header
| +-------------------+-----------------------+-------------------+ |
| |                   |                       |                   | |
| |     PREV PAGE     |      PREV PAGE /      |     NEXT PAGE     | |
| |     (Zone 1)      |      HUD TOGGLE       |     (Zone 3)      | |
| |                   |       (Zone 2)        |                   | |
| +-------------------+-----------------------+-------------------+ |
| |                   |                       |                   | |
| |     PREV PAGE     |     TOGGLE READER     |     NEXT PAGE     | |
| |     (Zone 4)      |      FLOATING HUD     |     (Zone 6)      | |
| |                   |       (Zone 5)        |                   | |
| +-------------------+-----------------------+-------------------+ |
| |                   |                       |                   | |
| |     PREV PAGE     |      NEXT PAGE /      |     NEXT PAGE     | |
| |     (Zone 7)      |      HUD TOGGLE       |     (Zone 9)      | |
| |                   |       (Zone 8)        |                   | |
| +-------------------+-----------------------+-------------------+ |
| 🔋 84% · 14:20                                    第 12 / 34 页 (35%)| <- Bottom Status Footer
+-------------------------------------------------------------------+
```

### 7.2 Floating HUD Overlay (Summoned on Center Tap)
```text
+-------------------------------------------------------------------+
| [←]  狼与香辛料 · 第三卷 第二话             [🎧 听书] [🔖] [🖼️]  | <- Floating Top Pill Bar
+-------------------------------------------------------------------+
|                                                                   |
|                    ( Live Chapter Content )                       |
|                                                                   |
+-------------------------------------------------------------------+
|   [ ↩ 还原跳转位置 (第4页) ]                                      | <- Appears for 5s after scrubbing
| +---------------------------------------------------------------+ |
| | [⏮ 上一章]   ~~~~~~●~~~~~~~~~~~~~~~~~~~~ 35%       [下一章 ⏭] | | <- Wavy Chapter Scrubber
| +---------------------------------------------------------------+ |
| +---------------------------------------------------------------+ |
| |  [📑 目录]  [🎨 外观]  [⚙️ 模式]  [🔍 查找]  [✏️ 笔记]  [🌙 夜读] | | <- HorizontalFloatingToolbar
| +---------------------------------------------------------------+ |
+-------------------------------------------------------------------+
```

### 7.3 The 4 Unified Layout Engines & CJK Typography Polish
1. **Horizontal Paged Engine (`WenkuReaderPaginator` 2.0)**:
   - Upgrades `1.x`'s paginator with full **CJK Kinsoku Shori (禁则处理)**: closing punctuation (`，。！？；：”’）】》」』`) never wraps to the first character of a new line, and opening brackets (`“‘（【《「『`) never hang at the end of a line!
   - Supports **Dual-Page Spread** automatically on unfolded foldables and landscape tablets.
2. **3D Curved Page-Peel Engine (`AGSL RuntimeShader` / Mesh)**:
   - Interactive finger-tracking page curl with realistic paper backside bleed-through and cast shadow.
3. **Continuous Multi-Chapter Scroll Stream**:
   - Replaces legacy `VerticalReaderActivity` (`ScrollView` + `LinearLayout`) with a virtualized `LazyColumn` that streams paragraphs and high-res illustrations at 120fps and seamlessly loads the next/previous chapter across volume boundaries without stopping scroll momentum.
4. **Vertical Right-to-Left CJK Engine (`竖排右翻`)**:
   - Authentic traditional light-novel vertical typesetting with rotated CJK punctuation, Ruby furigana support, and right-to-left page turning.

### 7.4 Live Transparent Jelly Sheet (`🎨 外观 Appearance`)
- Contains:
  - **Paper Presets**: `奶白 (#F6F5EF)` · `羊皮纸 (#EDE2CA)` · `浅苔 (#E4EBDD)` · `夜读 (#111312)` · `纯黑 (#000000)` · `墨水屏 (#FFFFFF)` · `+ 自定义背景图`.
  - **Font Family**: `系统默认` · `方正书宋 (Bundled)` · `思源宋体` · `霞鹜文楷` · `+ 导入本地 TTF/OTF`.
  - **Sliders**: `字号 (8–32sp)` · `行距 (0–32dp)` · `段距 (0–48dp)` · `页边距 (0–32dp)` · `夜间对比度 (35–100%)`.
  - **Scope Toggle**: `应用到全局 (Global)` vs `仅当前作品 (This Book Only)`.
- **Signature Interaction**: While the user's finger drags any slider, the sheet background fades to `10%` opacity and hides other controls so the live page reflows directly beneath their thumb.

---

## Screen 8: `ImageDetailScreen` (`ILLUSTRATION LIGHTBOX`)

- **Seamless Entry**: Shared-element morph (`sharedElement("image-{urlOrPath}")`) from any inline chapter illustration or novel cover.
- **Floating Glass Controls**:
  - `🔄 旋转 90°`: Smooth spring-driven 90° rotation (`animateFloatAsState` with `defaultSpatialSpec()`).
  - `🖼️ 章节画廊 (1/8)`: Horizontal filmstrip thumbnail scrubber at the bottom to flip through all illustrations in the current volume without leaving the lightbox!
  - `💾 保存原图` & `🔗 分享`: One-tap save via Android SAF (`MediaStore` / `ACTION_CREATE_DOCUMENT`) or share sheet.
- **Gesture Dismiss**: Vertical drag scales and translates the image with finger movement while fading the dark backdrop scrim, snapping back into the exact page/detail slot on release.

---

## Screen 9: `ProfileAndStudioScreen` (`03 / READER STUDIO`)

```text
+-------------------------------------------------------------------+
| 03 / READER STUDIO                                    [⚙️] [ℹ️]   |
| 我的与设置                                                        |
+-------------------------------------------------------------------+
| +---------------------------------------------------------------+ |
| |  (Avatar)   MewX (UID: 12345 · 轻国骑士)       [🎉 每日签到]  | |
| |  Morphing   积分: 1,284 (+1)   经验: 4,520                    | |
| +---------------------------------------------------------------+ |
|                                                                   |
| 📊 阅读统计与足迹                                  [生成分享卡 ›] |
| +-------------------+ +-------------------+ +-------------------+ |
| | 今日阅读 48 分钟  | | 连续阅读 12 天 🔥 | | 已读完 26 卷 📚   | |
| +-------------------+ +-------------------+ +-------------------+ |
| [ 🕒 最近阅读历史 (32) ]              [ 🔖 书签与划线笔记 (18) ]  |
|                                                                   |
| 🎨 视觉与主题工作室 (Theme & Palette Studio)                      |
| ├─ 外观模式: ( 跟随系统 ) ( ☀️ 日间纸页 ) ( 🌙 夜间 ) ( 📱 纯黑 ) |
| ├─ 墨水屏专享模式 (E-Ink Zero-Animation):                  [ OFF ]|
| ├─ 动态取色 (Monet / 封面氛围光效):                        [ ON  ]|
| ├─ 主题色板: (● 经典蓝) (● 雾松绿) (● 胭脂红) (● 麦穗金) (+取色)  |
| └─ 简繁转换: ( 简体中文 SC )  ( 繁體中文 TC )                     |
|                                                                   |
| 💾 存储与缓存管理                                                 |
| ├─ 已用空间: 小说正文 42 MB · 插图与封面 185 MB                   |
| ├─ [⚡ 快速清理非书架图片缓存]    [🧹 深度清理非书架小说缓存]     |
| └─ [📦 导出备份 (.wk8backup)]     [📥 导入恢复备份]               |
|                                                                   |
| ℹ️ 关于轻小说文库 v2.0 (Tap 5x Easter Egg) · 检查更新 · 开源许可  |
+-------------------------------------------------------------------+
```

- **Solves Backup & Reading-Position Loss (`STABILITY_PLAN.md` line 222)**:
  - Adds a 1-tap **Export / Import Backup (`.wk8backup` ZIP bundle)** containing the user's bookshelf, reading positions (`read_saves_v1`), search history, bookmarks/notes, and settings via SAF, plus proper `android:fullBackupContent` / `dataExtractionRules` that include the small database/settings files while excluding multi-megabyte cached chapter images so Android Auto Backup never exceeds the 25MB quota!
