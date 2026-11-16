# 05 · Screen-by-Screen UI Specifications (`v2.0` M3 Expressive)

> **Localization & Production Copy Rule**: All user-facing UI strings must live in `res/values/strings.xml`, `res/values-zh-rTW/strings.xml`, and `res/values-zh-rHK/strings.xml` without developer annotations (`Room FTS`, `articlename`, `do=list`, `StaticLayout`, `<!--image-->`). Every interactive touch target enforces `>= 48 × 48.dp` (`Modifier.minimumInteractiveComponentSize()`).  
> **Settings Preservation Rule**: Every valid user setting from `v1.31` (`GlobalConfig.SettingItems`, `ConfigFragment`, `MenuBackgroundSelectorActivity`, and `WenkuReaderSettingV1`) is 100% preserved and expanded—never removed!

---

## Screen 1: Adaptive Root Shell (`AdaptiveMainScaffold` — 4 Destinations, Default Home = `最新`)

### 1.1 Compact Phone Portrait (`< 600.dp`)

```text
+-------------------------------------------------------------------+
| [Status Bar - Edge-to-Edge Transparent]                           |
| +---------------------------------------------------------------+ |
| | 🔍 搜索本地书架、在线书名/作者 或 输入作品编号…        [Avatar]| | <- M3e Docked SearchBar (56dp)
| +---------------------------------------------------------------+ |
|                                                                   |
|   [ Active Tab: Default 2·最新 (or user-configured 1·书架) ]      |
|                                                                   |
| +---------------------------------------------------------------+ |
| | 📥 正在智能更新《败犬女主》 第7卷 (8/12章)  [~~~Wavy~~~]  [×] | | <- Non-blocking Active Job Pill
| +---------------------------------------------------------------+ |
| +---------------------------------------------------------------+ |
| |    [ 📚 书架 ]    [ ⚡ 最新* ]    [ 🏆 排行 ]    [ 👤 我的 ]   | | <- M3e ShortNavigationBar (64dp)
| +---------------------------------------------------------------+ |
| [Gesture Navigation Bar Inset]                                    |
+-------------------------------------------------------------------+
```

- **Default Startup Home (`默认启动页`)**: Boots into **`Tab 2 · 最新` (`LatestScreen`)** by default (matching `v1.31`), while allowing users to switch their default home to **`Tab 1 · 书架` (`本地书架`)** in `我的 → 外观与启动设置`. When `默认启动页 == 最新` and the device starts completely offline, automatically opens `书架` with a subtle non-blocking hint (`当前离线，已自动打开本地书架`).

### 1.2 Medium / Expanded Layout (Foldables & Tablets `>= 600.dp`)
- Uses `NavigationSuiteScaffold`: `ShortNavigationBarMedium` (horizontal icon+label `40.dp` pills) on medium portrait, or spring-expandable **`WideNavigationRail`** (`WideNavigationRailState`) + **`ListDetailPaneScaffold` (`44% List | 56% NovelDetail`)** on unfolded foldables and tablets.
- Re-tapping the active destination smoothly scrolls its list to item `0`.

---

## Screen 2: `BookshelfScreen` (`Tab 1 · 书架`)

- **Header Actions**:
  - Top M3e `SearchBar` (filters local shelf in `< 1ms` per keystroke and expands via `sharedBounds` into `UnifiedSearchScreen`).
  - Shelf Header Row (`我的书架 (64)`): **Cloud Sync** (`☁️` icon button, separated from avatar to guarantee `48×48.dp` touch targets), **Batch Download `SplitButtonLayout`** (`智能更新 | ▾`), and **View Mode `ButtonGroup`** (`网格` · `详情列表` · `3D书脊`).
- **"Continue Reading" `HorizontalMultiBrowseCarousel`**:
  - Hero card displays cover backdrop, current volume/chapter, **flat** M3 `LinearProgressIndicator` (`68%`), and a 1-tap `▶ 继续阅读` pill triggering the **T2 3D Book-Cover Opening** straight into `UnifiedReaderScreen`.
- **Local Filter & Sort Rail**:
  - `FilterChip`s: `全部`, `有新章节` (diffed via `BookshelfListParser`), `已缓存`, `连载中`, `已完成`, plus local cached `#标签` / `文库` chips and custom user shelf categories.
  - Sort selector: `按最近阅读` *(updated only when opening a chapter in Reader, never on mere Detail inspection)* · `按更新时间` · `手动置顶排序` *(preserves migrated `bookshelf_local.wk8` order)* · `书名拼音`.
- **Progress Indicator Rule**: Resting shelf cards always use **flat** `LinearProgressIndicator` for reading progress; **`LinearWavyProgressIndicator`** appears **only** on books actively downloading or syncing!
- **Contextual Multi-Select Mode**: Long-pressing a book card morphs a contextual **`HorizontalFloatingToolbar`** into view (`全选 · 智能更新 · 清理缓存 · 移出书架 · 完成`).

---

## Screen 3A: `LatestScreen` (`Tab 2 · 最新` — Default Home Screen)

Replaces `v1.31`'s `LatestFragment` while surfacing the 3 non-ranking chronological feeds from `Wenku8API.NovelSortedBy`:
- **Top `SearchBar`** + **`PrimaryTabRow`**:
  1. **`最近更新`** (`NovelSortedBy.lastUpdate` — initial sub-tab)
  2. **`最新入库`** (`NovelSortedBy.postDate`)
  3. **`完结精品`** (`NovelSortedBy.fullFlag`)
- **Card Content**: Renders `Title`, `Author`, `BookStatus`, `LastUpdate`, `Tags` chips, and 2-line `IntroPreview` (`NovelListWithInfoParser`) in borderless M3e Filled Cards (`1.dp` `#111111` outline in E-Ink mode).
- **Network Failure State**: Inline M3e error card with `[ ↻ 重试 ]` and **`[ 🩺 网络检测 ]`** (`AssistChip` navigating to `NetworkDiagnosticsScreen`).

---

## Screen 3B: `RankingsScreen` (`Tab 3 · 排行`)

Replaces `v1.31`'s 12-tab `RKListFragment` `ViewPager` with an instant 1-tap 2D M3 Expressive matrix:
- **Row 1 — Metric Connected `ButtonGroup`**:
  - `🔥 点击榜` (`Visit`) · `👍 推荐榜` (`Vote`) · `⭐ 总收藏` (`goodNum`) · `📏 字数榜` (`size`)
- **Row 2 — Period `FilterChip` Row** *(visible when `点击榜` or `推荐榜` is active)*:
  - `周榜` (`week`) · `日榜` (`day`) · `月榜` (`month`) · `总榜` (`all`)
- **Expressive Rank Badges**: Top 3 entries display `Sunny` / `Cookie9Sided` `MaterialShapes` badges (`#1`, `#2`, `#3`). *(Unparsed hit/push counters are omitted on list cards per `NovelListWithInfoParser`.)*

---

## Screen 4: `UnifiedSearchScreen` (Container-Transform Search Studio)

```text
+-------------------------------------------------------------------+
| [←]  🔍 输入小说标题、作者名称 或 作品编号 (如 1749)          [×] |
+-------------------------------------------------------------------+
| 在线搜索范围:  [ ✓ 按小说标题 ]    [ 按作者名称 ]    [ 编号直达 ] |
+-------------------------------------------------------------------+
| 📚 本地书架即时匹配 (2)                                           |
|   [ 狼与香辛料 · 已读至第17卷 68% ·                 [▶ 继续阅读] ]|
| 🌐 在线文库搜索结果                    [ ⏳ 冷却中，2s 后自动搜 ] |
|   [ Paginated Online Cards with Shared-Element Cover & Title ]    |
| 🕒 最近搜索记录 (上限 20 条，从 search_history.wk8 完整迁移) [清空]|
|   ( 支仓冻砂 )   ( 狼与香辛料 )   ( 败犬女主 )   ( 刀剑神域 )     |
+-------------------------------------------------------------------+
```

- **Auto-Queued `5s` Cooldown**: When refining an online query inside Wenku8's `5s` server cooldown, the query is queued automatically with a live countdown pill rather than failing with an error toast.

---

## Screen 5: `NovelDetailScreen` (`作品详情`)

```text
+-------------------------------------------------------------------+
| [←]  作品详情 · #0005                                 [🔗]  [⋮]  |
|                                                                   |
|   +------------+   [电击文库]  [已完成]                           |
|   |   Cover    |   狼与香辛料                                     |
|   | 120 x 168  |   作者：支仓冻砂 ›                               |
|   | (3D Shared |   全文 200.4 万字 · 更新于 2026-02-18            |
|   | + Glow)    |   (#奇幻) (#旅行) (#经商) (#贤狼)                |
|   +------------+                                                  |
| +---------------+---------------+---------------+---------------+ |
| | 总点击 (日+26)| 总推荐        | 总收藏        | 最新章节      | |
| | 231.6 万      | 15.3 万       | 14,416        | 第17卷 插图   | |
| +---------------+---------------+---------------+---------------+ |
|                                                                   |
| +-------------------------------------------------------+-------+ |
| |            ▶ 继续阅读 · 第三卷 第二话 (42%)           |   ▾   | | <- Reading-Only SplitButton!
| +-------------------------------------------------------+-------+ |
| +--------------------+ +--------------------+ +-----------------+ |
| |   ✓ 已在书架       | |   ↓ 离线缓存       | |  💬 书评 (142)  | | <- Balanced 3-Button Row
| +--------------------+ +--------------------+ +-----------------+ |
|                                                                   |
| 📖 内容简介                                          [展开全部 ▾] |
| 从教会都市出发的商人罗伦斯，在装满麦子的马车上发现了熟睡的贤狼……  |
|                                                                   |
| +---------------------------------------------------------------+ |
| | [赞助 · 支持中转服务器运营]     (Non-Intrusive Inline AdCard) | | <- Zero CLS · Collapses Offline
| +---------------------------------------------------------------+ |
|                                                                   |
| 📚 目录与分卷 (共 17 卷)                   [🔍 搜章节] [⇅ 正倒序] |
| +---------------------------------------------------------------+ |
| | ▶ 第一卷 旅途的开端                  [🖼 插图章节] [已缓存 8/8]| |
| +---------------------------------------------------------------+ |
| | ▼ 第三卷 狼与黄铁矿 (上次读到)                             ▲  | |
| |   ├─ 序幕 庆典中的北方城镇                             已读完 | |
| |   ├─ ▶ 第二话 真实与冒牌货                     正在阅读 · 42% | |
| |   └─ 🖼 本卷《插图》画集 (12 张彩页与黑白插画 · 支持跨页拼合) ›| |
| +---------------------------------------------------------------+ |
+-------------------------------------------------------------------+
```

- **M3e `SplitButtonLayout` Separation**: Leading button resumes reading; trailing `▾` menu offers `"从头开始阅读"`, `"选择阅读模式"`, and `"朗读本章 (TTS)"`. `[ ✓ 已在书架 ]`, `[ ↓ 离线缓存 ]` (opening the 4-Mode `NovelDownloader` `ModalBottomSheet`), and `[ 💬 书评 ]` sit cleanly on their own row.
- **Tap Title Dialog**: Tapping a long truncated title opens a copyable M3 dialog showing full `AID` and untruncated title (preserving `v1.31` `NovelInfoActivity` behavior).
- **Minimal, Non-UX-Harming Server-Support Ad Placement**: Appears *exclusively* on `NovelDetailScreen` between **Synopsis** and **Volume Directory** (fixed pre-reserved height when online + GMS available for **CLS = 0**, lazy post-frame `MobileAds` init, instant collapse offline or on GMS-free ROMs).

---

## Screen 6: `ReviewListScreen` & `ReviewThreadScreen` (`书评讨论`)

- **`ReviewListScreen`**: Real user avatars via `Wenku8API.getAvatarURL(uid)` (`api-stub` returns `""` fallback icon), reply count pills, and bottom-right `✍️ 发表书评` FAB.
- **`ReviewThreadScreen`**: `1楼 (楼主)` pinned header + floor replies (`2楼`, `3楼`...) with 1-tap `复制` and `回复 N楼 @user` quote chips. Bottom composer shows live status chips (`✓ 字数 >= 7`, `✓ 无敏感词`, `✓ 10秒间隔就绪`) and `400ms` auto-saved drafts (`REVIEW_DRAFTS`).

---

## Screen 7: `UnifiedReaderScreen` (`沉浸阅读器 2.0` — 100% `v1.31` Customization Preserved)

- **Contextual M3e `HorizontalFloatingToolbar` HUD**: Summoned on center tap (`目录 · 排版 · 模式 · 查找 · 书签 · 夜间`), paired with a chapter progress scrubber, `[查看大图]` shortcut when the current page contains an illustration, and 5-second `"↩ 还原至第 N 页"` undo pill.
- **Full `WenkuReaderSettingV1` Parity + New M3e Controls (`排版` Sheet)**:
  - **Sliders with Opaque Active-Slider Capsule Preview**:
    - **字号大小 (`reader_font_size`)**: **`8–36 sp`** (default `18 sp`, covering `v1.31`'s full `8–32 sp` range without clamping!).
    - **行间距 (`reader_line_distance`)**: **`0–32 dp`** (default `16 dp`).
    - **段间距 (`reader_paragraph_distance`)**: **`0–48 dp`** (default `20 dp`).
    - **页边距 (`reader_paragraph_edge_distance`)**: **`0–32 dp`** (default `8 dp`).
    - *During any slider drag, non-active rows fade to `0%` while the active slider stays inside a `92%`-opaque `surfaceContainerHighest` pill so 90% of the live reflowing page stays legible underneath.*
  - **字体选择 (`reader_font_path`)**: `系统默认字体` or `选择自定义字体 (.ttf / .otf)` (migrated from `filesDir/custom/reader_font`).
  - **阅读背景 (`reader_background_path`)**: M3e presets (`日间羊皮纸` / `纯净白` / `护眼绿` / `暗夜黑` / `纯黑 AMOLED`) **plus `自定义日间背景图片`** (migrated directly from `v1.31` `filesDir/custom/reader_background`!).
  - **硬件交互与阅读辅助**: `音量键翻页` (`ON` by default, matching `Wenku8ReaderActivityV1`), `保持屏幕常亮`, and optional `左侧边缘上下滑动调节亮度` (inset to `24.dp..72.dp` so `0..24.dp` Predictive Back never collides).

---

## Screen 8: `ArtbookLightboxOverlay` (`插图画集与跨页模式`)

- Expands via `sharedBounds` from any inline `<!--image-->` Art Card or `<chapter>插图</chapter>` chip, featuring `BitmapRegionDecoder` 4× tiling zoom, **`跨页拼合` (`口绘` double-page spread stitching)**, `90°` spring rotation, `HorizontalMultiBrowseCarousel` filmstrip, MediaStore/SAF save & share, and vertical drag-to-dismiss.

---

## Screen 9: `ProfileAndStudioScreen` (`Tab 4 · 我的`) & Full `v1.31` Settings Preservation

```text
+-------------------------------------------------------------------+
| 我的与设置                                            [ℹ️ 关于]   |
+-------------------------------------------------------------------+
| 📢 服务器公告 (Dismissible M3 Tonal Banner when NoticeString != "")|
+-------------------------------------------------------------------+
| +---------------------------------------------------------------+ |
| | [Optional Header Wallpaper: M3e Tonal / Classic 1-5 / Custom] | | <- Preserves menu_bg_id & menu_bg_path!
| | [Logged In]  (Avatar) MewX · 轻国骑士          [🎉 每日签到]  | |
| |              UID: 12345 · 积分 1,284 · 经验 4,520  [退出登录] | |
| | [Logged Out] 登录轻小说文库账号以同步云端书架与发表书评       | |
| |              [ 🔑 登录账号 (用户名/邮箱) ]    [ 🌐 网页注册 ] | |
| +---------------------------------------------------------------+ |
|                                                                   |
| 📊 阅读足迹与书签                      [历史记录] [书签笔记 (18)] |
|                                                                   |
| 🌐 网络与中转连接 (See Doc 11 for full NetworkDiagnosticsScreen)  |
| ├─ 自动优化连接 (自动优化连接：开启 · 已优化当前网络连接) [ ON  ] |
| └─ [ 🩺 网络检测与诊断报告 › ]                [ ↺ 恢复默认重测 ]  |
|                                                                   |
| 🎨 外观、启动与阅读全局偏好                                       |
| ├─ 默认启动页:   [ ✓ 最新更新 (默认) ]     [ 本地书架 ]           | <- Configurable Home Tab!
| ├─ 文库语言切换: [ ✓ 简体中文 ]  [ 繁體中文 ]  (无重启即时生效)   |
| ├─ 深色模式:     [ 跟随系统 ] [ 日间纸页 ] [ 夜间墨色 ] [ 纯黑 ]  |
| ├─ 墨水屏模式 (高对比 1dp 边框 · 零动画 · 继承 v1):      [ OFF ]  |
| ├─ 动态取色 (系统壁纸 Monet / 详情页封面氛围色):         [ ON  ]  |
| ├─ 个人主页头图壁纸: [ M3e 纯净 / 经典预设 1-5 / 自定义图片 › ]   | <- Preserves MenuBackgroundSelector!
| └─ 阅读器默认排版 (字号/间距/自定义字体/自定义背景图片/音量翻页) ›|
|                                                                   |
| 💾 存储、缓存清理与存档管理 (Room SQLite · wenku8_v2.db)          |
| ├─ 清除缓存: [ ⚡ 快速清理 (临时与非书架封面) ] [ 🔍 深度清理 › ]  | <- Both v1.31 Cache Wipe Modes!
| ├─ 备份归档: [ 📤 导出存档 (.wk8backup) ] [ 📥 导入/智能合并 ]    |
| ├─ 外部迁移: [ 📂 从外部文件夹导入旧版 v1.x 存档 (SAF) ]          | <- Internal saves auto-migrate!
| └─ 🔄 检查新版本 · 📜 开源许可声明 · ℹ️ 关于轻小说文库 (5击彩蛋)  |
+-------------------------------------------------------------------+
```

- **100% `v1.31` Settings Parity**:
  - **Profile Header Wallpaper (`个人主页头图壁纸`)**: Preserves `menu_bg_id` (`"1"`..`"5"` classic `bg_avatar_01..05` artworks or `"0"` custom user image `filesDir/custom/menu_bg`) as an optional banner backdrop on the `我的` Account Card (and `WideNavigationRail` header), alongside a clean `"M3e 纯净卡片"` option.
  - **Two-Mode Cache Cleanup (`清除缓存` — Matching `ConfigFragment`)**:
    1. **`快速清理` (`Fast Clean`)**: Clears temporary Coil HTTP disk cache + non-bookshelf cover files (`imgs/{aid}.jpg` where `aid` is not in `BOOKSHELF`).
    2. **`深度清理` (`Deep Orphan Clean`, Cancellable)**: Runs Fast Clean **plus** scans all Room `CHAPTER_CONTENTS` `image_urls_json` entries and deletes orphaned illustration files inside `filesDir/saves/imgs/` not referenced by any saved chapter.
  - **About, Open-Source License & Update Check**: Preserves `CheckAppNewVersion`, the full `R.raw.license` Open-Source Statement dialog (`开源许可声明`), `NoticeString` with real `<a href>` link opening, and `About` screen.
