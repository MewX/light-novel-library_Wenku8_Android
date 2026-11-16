# 05 — Screen-by-Screen Material 3 Expressive (`M3e`) UI Specifications (`v2.0`)

**Status:** Screen Wireframes & Component Hierarchy  
**Target SDK / Design Language:** `minSdk = 23` · **Material 3 Expressive (`material3:1.4+`)** · Clean User-Facing Localization (`values`, `values-zh-rTW`, `values-zh-rHK` — **zero internal API/SQLite jargon in production UI strings**)

---

## Screen 1: `AdaptiveMainScaffold` (Root M3e Shell)

### 1.1 Compact Portrait Layout (Phones `< 600.dp`)
```text
+-------------------------------------------------------------------+
| [Status Bar - Edge-to-Edge Transparent]                           |
| +---------------------------------------------------------------+ |
| | 🔍 搜索本地书架、在线书名/作者 或 输入作品编号…        [Avatar]| | <- M3e Docked SearchBar (56dp)
| +---------------------------------------------------------------+ |
|                                                                   |
|   [ Active Tab: 1·书架 / 2·最新 / 3·排行 / 4·我的 ]               |
|                                                                   |
| +---------------------------------------------------------------+ |
| | 📥 正在智能更新《败犬女主》 第7卷 (8/12章)  [~~~Wavy~~~]  [×] | | <- Non-blocking Active Job Pill
| +---------------------------------------------------------------+ |
| +---------------------------------------------------------------+ |
| |    [ 📚 书架 ]     [ ⚡ 最新 ]     [ 🏆 排行 ]     [ 👤 我的 ]  | | <- M3e ShortNavigationBar (64dp)
| +---------------------------------------------------------------+ |
| [Gesture Navigation Bar Inset]                                    |
+-------------------------------------------------------------------+
```

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
  - Sort selector: `按最近阅读` *(updated only when opening a chapter in Reader, never on mere Detail inspection)* · `按更新时间` · `手动置顶排序` · `书名拼音`.
- **Progress Indicator Rule**: Resting shelf cards always use **flat** `LinearProgressIndicator` for reading progress; **`LinearWavyProgressIndicator`** appears **only** on books actively downloading or syncing!
- **Contextual Multi-Select Mode**: Long-pressing a book card morphs a contextual **`HorizontalFloatingToolbar`** into view (`全选 · 智能更新 · 清理缓存 · 移出书架 · 完成`).

---

## Screen 3A: `LatestScreen` (`Tab 2 · 最新`)

Replaces `v1.31`'s `LatestFragment` while surfacing the 3 non-ranking chronological feeds from `Wenku8API.NovelSortedBy`:
- **Top `SearchBar`** + **`PrimaryTabRow`**:
  1. **`最近更新`** (`NovelSortedBy.lastUpdate`)
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
| 🕒 最近搜索记录                                       [清空记录]  |
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

- **M3e `SplitButtonLayout` Separation**: Leading button resumes reading; trailing `▾` menu offers `"从头开始阅读"`, `"选择阅读模式"`, and `"朗读本章 (TTS)"`. `[ ✓ 已在书架 ]`, `[ ↓ 离线缓存 ]` (opening the 4-Mode `NovelDownloader` `ModalBottomSheet`), and `[ 💬 书评 ]` sit cleanly on their own row so `360.dp` screens and large CJK fonts never clip. *(Dead stub `投推荐票` is excluded.)*
- **Tap Title Dialog**: Tapping a long truncated title opens a clean copyable M3 dialog showing full `AID` and untruncated title (preserving `v1.31` `NovelInfoActivity` behavior).
- **Minimal, Non-UX-Harming Server-Support Ad Placement**:
  - **Single Inline Slot Only**: Appears *exclusively* on `NovelDetailScreen` in the natural pause between **Synopsis** and **Volume Directory**—never inside the Reader, never sticky/floating, and well separated from `继续阅读` and chapter rows to prevent accidental taps.
  - **Zero Layout Shift (CLS = 0) & Clean Degradation**: Fixed container height (`surfaceContainerLow`, `16.dp` radius) is pre-allocated *only* when online and `GoogleServicesHelper` confirms Play Services availability; collapses cleanly before layout when offline, on GMS-free ROMs, or on ad load failure, and initializes `MobileAds` lazily after the first UI frame.

---

## Screen 6: `ReviewListScreen` & `ReviewThreadScreen` (`书评讨论`)

- **`ReviewListScreen`**: Real user avatars via `Wenku8API.getAvatarURL(uid)` (`api-stub` returns `""` fallback icon), reply count pills, and bottom-right `✍️ 发表书评` FAB.
- **`ReviewThreadScreen`**: `1楼 (楼主)` pinned header + floor replies (`2楼`, `3楼`...) with 1-tap `复制` and `回复 N楼 @user` quote chips. Bottom composer shows live status chips (`✓ 字数 >= 7`, `✓ 无敏感词`, `✓ 10秒间隔就绪`) and `400ms` auto-saved drafts (`REVIEW_DRAFTS`).

---

## Screen 7: `UnifiedReaderScreen` (`沉浸阅读器 2.0`)

- **Contextual M3e `HorizontalFloatingToolbar` HUD**: Summoned on center tap (`目录 · 排版 · 模式 · 查找 · 书签 · 夜间`), paired with a chapter progress scrubber and 5-second `"↩ 还原至第 N 页"` undo pill.
- **Opaque Active-Slider Capsule Preview (`排版` Sheet)**: While dragging Font Size (`8–32sp`), Line/Paragraph Spacing, or Margins, inactive controls and the sheet backdrop fade to `0%` while **only the active slider row stays inside a crisp `92%`-opaque `surfaceContainerHighest` pill**, letting 90% of the live chapter reflow underneath with zero text-on-text overlap!
- **Predictive Back Safe Edge Zones**: Left-edge vertical brightness slide (optional) is inset to `24.dp..72.dp` so the outer `0..24.dp` bezel reliably triggers Android 15/16 Predictive Back.

---

## Screen 8: `ArtbookLightboxOverlay` (`插图画集与跨页模式`)

- Expands via `sharedBounds` from any inline `<!--image-->` Art Card or `<chapter>插图</chapter>` chip, featuring `BitmapRegionDecoder` 4× tiling zoom, **`跨页拼合` (`口绘` double-page spread stitching)**, `90°` spring rotation, `HorizontalMultiBrowseCarousel` filmstrip, SAF save/share, and vertical drag-to-dismiss.

---

## Screen 9: `ProfileAndStudioScreen` (`Tab 4 · 我的`) & `NetworkDiagnosticsScreen`

```text
+-------------------------------------------------------------------+
| 我的与设置                                            [ℹ️ 关于]   |
+-------------------------------------------------------------------+
| 📢 服务器公告 (Dismissible M3 Tonal Banner when NoticeString != "")|
+-------------------------------------------------------------------+
| +---------------------------------------------------------------+ |
| | [Logged In]  (Avatar) MewX · 轻国骑士          [🎉 每日签到]  | |
| |              UID: 12345 · 积分 1,284 · 经验 4,520  [退出登录] | |
| | [Logged Out] 登录轻小说文库账号以同步云端书架与发表书评       | |
| |              [ 🔑 登录账号 (用户名/邮箱) ]    [ 🌐 网页注册 ] | |
| +---------------------------------------------------------------+ |
|                                                                   |
| 📊 阅读足迹与书签                      [历史记录] [书签笔记 (18)] |
|                                                                   |
| 🌐 网络与中转连接 (See Doc 11 for full NetworkDiagnosticsScreen)  |
| ├─ 自动优化连接 (已优化当前网络连接 · cf-v4-02)          [ ON  ]  |
| └─ [ 🩺 网络检测与诊断报告 › ]                [ ↺ 恢复默认重测 ]  |
|                                                                   |
| 🎨 外观与主题工作室                                               |
| ├─ 深色模式: [ 跟随系统 ] [ 日间纸页 ] [ 夜间墨色 ] [ 纯黑省电 ]  |
| ├─ 墨水屏专享模式 (高对比 1dp 边框 · 全局零动画):        [ OFF ]  |
| ├─ 系统壁纸动态取色 (Monet) / 详情页封面氛围取色:        [ ON  ]  |
| ├─ 主题色板: (●经典蓝) (●雾松绿) (●胭脂红) (●麦穗金) (●鸢尾紫)    |
| └─ 文库语言切换: [ 简体中文 ]  [ 繁體中文 ]  (无重启即时生效)     |
|                                                                   |
| 💾 内置数据库与存档管理 (Room SQLite · wenku8_v2.db)              |
| ├─ [📤 导出存档 (.wk8backup)]      [📥 导入 / 智能合并存档]       |
| ├─ [📂 导入旧版 v1.x 文件夹]       [🧹 清理非书架小说与插图缓存]  |
| └─ 🔄 检查新版本 · 开源许可与免责声明 · 关于 v2.0 (5击彩蛋)       |
+-------------------------------------------------------------------+
```

- **Complete Account Sheet**: Tapping `[ 🔑 登录账号 ]` opens an M3e `ModalBottomSheet` supporting **Username or Email** (`<= 30` chars, routing automatically to `getUserLoginParams` vs. `getUserLoginEmailParams`), password visibility toggle, non-blocking loading state, and `REGISTER_URL` browser launch dialog. If startup re-auth (`cert.wk8`) fails, a clear `"登录状态已过期，请重新登录"` banner appears at the top of the card.
- **Full `NetworkDiagnosticsScreen` Specification**: See **[`11-network-diagnostics-and-auto-routing-ui.md`](./11-network-diagnostics-and-auto-routing-ui.md)**.
