# 08 · Strict API-Grounded UI Mapping & Light Novel / Comic Expressive Interactions (`v2.0`)

This document maps **every active, verified endpoint, parameter, constraint, and parsed model** in [`Wenku8API.java`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java) and `org.mewx.wenku8.global.api.*` to its exact **Material Design 3 / Material 3 Expressive** UI surface in `v2.0`, explicitly documents **unused/unsupported stubs that MUST NOT appear in the UI**, and defines **where M3 Expressive interactions happen** for **Japanese Light Novel (Bunkobon 文库本) & Illustration (`<!--image-->` 口绘/插画) reading styles**.

---

## Part 0: Audit of Unused / Unsupported `Wenku8API.java` Stubs (Excluded from UI)

A codebase-wide audit of `studio-android/LightNovelLibrary/app/src/main/java/` confirmed that several helper methods present in [`Wenku8API.java`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java) are legacy dead stubs that are **never called in `studio-android` and not supported in the app**. `v2.0` strictly excludes these from all UI screens and mockups:

| Unused Stub / Unparsed Field | Why It Is Excluded from `v2.0` UI |
| :--- | :--- |
| **`Wenku8API.getVoteNovelParams(int aid)`** (`action=book&do=vote` — "推荐本作") | Dead stub in `Wenku8API.java` (lines 570–573); never called anywhere in `studio-android`. **Removed from Novel Detail (Board 03) and Foldable Detail (Board 06).** |
| **`Wenku8API.getLibraryList()` / `getNovelListByLibrary*()`** (`action=xml&item=sort` — "文库分类 1..14") | Dead stubs in `Wenku8API.java` (lines 485–520); never called anywhere in `studio-android`. **Removed from Explore Tabs & SearchView (Board 02).** Note: `NovelItemMeta.pressId` (e.g., `"电击文库"`) returned by `action=book&do=meta` is rendered as a static metadata badge on Novel Detail. |
| **Unparsed `TotalHitsCount` / `PushCount` / `FavCount` on Ranking List Rows** | [`NovelListWithInfoParser.java`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/NovelListWithInfoParser.java#L83-L105) only parses `aid`, `name` (`Title`), `author` (`Author`), `status` (`BookStatus`), `update` (`LastUpdate`), `tags` (`Tags`), and `intro_short` (`IntroPreview`). Hit/Push/Fav counters are only parsed by `NovelItemMeta` (`action=book&do=meta`) on the Novel Detail screen. |

---

## Part 1: Active & Supported `Wenku8API` → Material 3 UI Matrix

### 1. Bookshelf, Cloud Bookcase & Room SQLite Persistence
| API Method / Parser | Wire Action | Returned Fields & Constraints | `v2.0` Material 3 UI & Expressive Function |
| :--- | :--- | :--- | :--- |
| [`Wenku8API.getBookshelfListParams(l)`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L593-L635) + [`BookshelfListParser`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/BookshelfListParser.java) | `action=bookcase&t={0\|1}` | `<book aid="..." date="YYYY-MM-DD"><name>...</name><chapter cid="...">...</chapter></book>` | **Single-Request Cloud Update Diff**: Compares each cloud entry's `date` (`LastUpdate`) and `latestChapterCid` against local Room SQLite (`wenku8_v2.db` `novels` table). Books with newer `latestChapterCid` pop an **M3 Expressive `tertiaryContainer` badge (`新章节`)** and filter under the `有新章节` `FilterChip`. |
| [`Wenku8API.getAddToBookshelfParams(aid)`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L637-L645) / `getDelFromBookshelfParams(aid)` + [`BookshelfSync`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/BookshelfSync.java) | `action=bookcase&do={add\|del}&aid={aid}` | Cloud shelf limit (~60–100 books depending on user rank); local Room SQLite shelf (`novels.in_bookshelf = 1`) is unlimited | **Hybrid Cloud + Room SQLite Shelf**: Adding/removing a book morphs the M3 `FilledTonalButton` (`已在书架`) and syncs with cloud bookcase via `BookshelfSync`. |
| [`BookshelfFilter.filter(shelf, query)`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/BookshelfFilter.java) | Indexed Room SQLite query (`0ms`) | Matches `title`, `author`, and `latest_section_name` | **Instant Docked SearchBar Filter**: Typing in the Bookshelf `SearchBar` filters the local shelf in `< 1ms` per keystroke while offering 1-tap escalation to online Wenku8 search. |
| [`NovelDownloader`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/NovelDownloader.java) | `do=meta` + `do=intro` + `do=list` + `do=text` | 4 modes: *Smart Update*, *Update Only*, *Select Volumes*, *Overwrite All* | **Non-Blocking M3 `ModalBottomSheet` + `LinearWavyProgressIndicator`**: Writes downloaded chapters atomically into Room `chapter_contents` via background `WorkManager`. |

---

### 2. Explore & 13 Active `NovelSortedBy` Modes (`LatestFragment` + `RKListFragment`)
| API Method / Parser | Wire Action | Returned Fields & Constraints | `v2.0` Material 3 UI & Expressive Function |
| :--- | :--- | :--- | :--- |
| [`Wenku8API.NovelSortedBy`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L111-L155) (13 Active Sort Modes) | `action=mewx_articlelist` / `action=novellist` (`sort=...&page=...&t=...`) | **3 Feeds**: `lastUpdate` (最近更新), `postDate` (最新入库), `fullFlag` (完结列表)<br/>**10 Rankings**: `dayVisit`, `weekVisit`, `monthVisit`, `allVisit`, `dayVote`, `weekVote`, `monthVote`, `allVote`, `goodNum` (总收藏榜), `size` (字数排行) | **M3 `PrimaryTabRow` + 2D `SegmentedButton` / `FilterChip` Matrix**:<br/>- Top Tabs: `排行榜单 (RK)` · `最近更新` · `最新入库` · `完结列表`<br/>- Under `排行榜单`: Metric Segmented Row (`点击榜 Visit` \| `推荐榜 Vote` \| `总收藏榜` \| `字数排行`) × Period `FilterChip`s (`周榜 week` · `日榜 day` · `月榜 month` · `总榜 all`). |
| [`Wenku8API.getMewxNovelList`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L357-L460) & [`NovelListWithInfoParser`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/NovelListWithInfoParser.java) | `action=mewx_articlelist` & `action=novellist` | `aid`, `Title`, `Author`, `BookStatus`, `LastUpdate`, `Tags` (space-separated), `IntroPreview` | **Rich M3 Filled Novel Cards**: Every ranking/feed card surfaces `Title`, `Author`, `BookStatus`, `LastUpdate`, `Tags` chips, and a 2-line `IntroPreview` (`intro_short`). |

---

### 3. Online Search APIs (`articlename` & `author`), Local Filter & Room Search History
| API Method | Wire Action | Returned Fields & Constraints | `v2.0` Material 3 UI & Expressive Function |
| :--- | :--- | :--- | :--- |
| [`Wenku8API.searchNovelByNovelName(name, l)`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L308-L326) | `action=search&searchtype=articlename&searchkey=...` | Returns `<item aid='...'/>` list; hydrated via `do=bookinfo` (`NovelItemInfoUpdate`); upstream 5s search cooldown | **M3 Fullscreen `SearchView` with Exact Supported Scopes**:<br/>1. `按小说标题 (articlename)`<br/>2. `按作者名称 (author)`<br/>Includes a live **5s Cooldown Pill**, an instant **0ms `BookshelfFilter` Top Match Card**, and **Room `search_history` Chips**. |
| [`Wenku8API.searchNovelByAuthorName(author, l)`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L328-L334) | `action=search&searchtype=author&searchkey=...` | Returns all novels by `authorName` | Triggered from the SearchView OR by tapping the **Author name (`作者：支仓冻砂 ›`)** on any Novel Detail page! |

---

### 4. Novel Detail (`do=meta`, `do=intro`, `do=list`)
| API Method / Model | Wire Action | Returned Fields & Constraints | `v2.0` Material 3 UI & Expressive Function |
| :--- | :--- | :--- | :--- |
| [`Wenku8API.getNovelFullMeta(aid, l)`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L229-L249) ([`NovelItemMeta`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/NovelItemMeta.java)) + `getNovelShortInfoUpdate_CV` (`do=bookinfo`) | `action=book&do=meta` & `action=book&do=bookinfo` | `aid`, `Title`, `Author`, `DayHitsCount`, `TotalHitsCount`, `PushCount`, `FavCount`, `PressId` (string), `BookStatus`, `BookLength`, `LastUpdate`, `LatestSection` (`cid`), `Tags` | **Complete Metadata Header + 4-Column Tonal Stat Strip**:<br/>- Static `PressId` badge (`电击文库`) & `BookStatus` badge (`已完成`)<br/>- Clickable `Author` link (`作者：支仓冻砂 ›`)<br/>- `BookLength` (`全文 2,004,180 字`) & `LastUpdate`<br/>- 4-stat tonal card: `总点击 (日+DayHits)` · `总推荐 (PushCount)` · `总收藏 (FavCount)` · `最新章节 (LatestSection)`<br/>- Action row: **M3 `SplitButtonLayout` (`▶ 继续阅读 | ▾ 缓存`)** + **`✓ 已在书架`** + **`💬 书评`**. |
| [`Wenku8API.getNovelIndex(aid, l)`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L251-L286) ([`VolumeList`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/VolumeList.java) / [`ChapterInfo`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/ChapterInfo.java)) | `action=book&do=list&aid={aid}` | `<volume vid="..."><chapter cid="...">序章</chapter>...<chapter cid="...">插图</chapter></volume>` | **Inline Expandable Volume Cards + Dedicated `插图` Quick-Chips**: Detects `<chapter>插图</chapter>` in each volume and renders a `tertiaryContainer` **`🖼 插图章节` AssistChip** on the collapsed volume header and a rich **Artbook Preview Card** at the bottom of the expanded volume! |

---

### 5. Chapter Content (`do=text`), Reviews (`action=review`) & User Account (`action=userinfo` / `sign`)
| API Method / Model | Wire Action | Returned Fields & Constraints | `v2.0` Material 3 UI & Expressive Function |
| :--- | :--- | :--- | :--- |
| [`Wenku8API.getNovelContent(aid, cid, l)`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L288-L300) | `action=book&do=text&aid={aid}&cid={cid}` | Plain text paragraphs + `<!--image-->http://pic.wenku8.cn/pictures/...jpg<!--image-->` tags | **`Wenku8ReaderEngineV2` (`StaticLayout` + 3-Chapter Ring Buffer + 6 Page-Turn Delegates)**: Renders paragraphs with CJK *Kinsoku Shori* (`LineBreakConfig`) and inline `<!--image-->` Art Cards that expand via **Shared-Bounds Spring** into an **In-Reader Artbook/Spread Mode**. |
| [`Wenku8API.getCommentListParams`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L666-L700), `getCommentContentParams`, `getCommentNewThreadParams`, `getCommentReplyParams` | `action=review&do={list\|show\|post\|reply}` | `Review`: `rid`, `title`, `postTime`, `noReplies`, `lastReplyTime`, `userName`, `uid`<br/>`ReviewReply`: `replyTime`, `userName`, `uid`, `content`<br/>**Rules**: `MIN_REPLY_TEXT = 7`, `searchBadWords()`, `10s` post cooldown, GBK Base64 | **M3 Review Thread with Real UID Avatars & Live Pre-Submit Bar**:<br/>- Loads real user avatars via `Wenku8API.getAvatarURL(uid)`!<br/>- Reply composer displays live status chips for `>= 7 字`, `searchBadWords()` check, and a `10s` cooldown timer. |
| [`Wenku8API.getUserInfoParams()`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L549-L568) ([`UserInfo`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/UserInfo.java)) + `getUserSignParams()` + `getUserAvatar()` | `action=userinfo`, `action=block&do=sign`, `action=avatar` | `uname`, `nickname`, `uid`, `score` (现有积分), `experience` (经验值), `rank` (头衔); `do=sign` adds `+1 score/day` | **Profile, Material You & Room SQLite Backup Studio**: Displays avatar (`action=avatar`), `nickname`, `uname`, `uid`, `rank` badge, `score` & `experience` tonal cards, **M3 Check-In Button (`action=block&do=sign`)**, and **Room SQLite Export / Import Archive (`wenku8_v2.db`)**. |

---

## Part 2: Where Material 3 Expressive Interactions Happen for Light Novel & Comic/Illustration Styles

### 1. Inline `<!--image-->` Art Cards → Shared-Bounds Comic/Artbook Lightbox (`Wenku8ReaderEngineV2`)
- **In-Chapter Presentation**: When `WenkuChapterParser` parses `<!--image-->...<!--image-->`, the Reader renders an **M3 Expressive Art Card** (`20dp` rounded corners, subtle tonal frame, caption `插图 03`, and a floating pill `🖼 轻触展开插图画集`).
- **Expressive Shared-Bounds Expansion**: Tapping or pinching-out on any inline illustration triggers an official Compose **`SharedTransitionLayout` Spatial Spring (`MotionScheme.expressive().defaultSpatialSpec()`)** that expands the image seamlessly into the **In-Reader Artbook & Comic Spread Overlay** without launching a separate Activity or losing your reading place.
- **Double-Page Spread Mode (`跨页拼合` for Light Novel `口绘`)**: Light novel color frontispieces (`口绘 01–02`) are frequently split into two consecutive vertical JPEG URLs by Wenku8. A 1-tap **`跨页拼合: 开` (`FilterChip`)** stitches paired landscape/spread pages side-by-side with zero seam gap and 4× pinch-to-zoom!
- **M3 Multi-Browse Thumbnail Scrubber Strip**: At the bottom of the Artbook overlay, all illustrations in the current chapter/volume are displayed in an official **M3 `HorizontalMultiBrowseCarousel`** thumbnail strip.

### 2. Volume-Level `<chapter>插图</chapter>` Quick-Jump (`BookshelfScreen` & `NovelDetailScreen`)
- Because `Wenku8API.getNovelIndex` (`do=list`) places a dedicated `<chapter>插图</chapter>` at the end of almost every volume, `v2.0` automatically detects this chapter and surfaces a **`tertiaryContainer` `🖼 插图章节` AssistChip** right on the collapsed Volume row (Board 03) and on the Bookshelf detailed list card (Board 01 Right).

### 3. Six Official-Compose Page-Turn Modes (`Wenku8ReaderEngineV2`)
Inside the Reader's M3 `ModalBottomSheet` (Board 04 Right), readers can switch live between 6 page-turn delegates built 100% on official AndroidX primitives:
1. **`覆盖翻页` (`CoverSlideDelegate` — Default)**: Kindle/Legado-style cover slide built on official Compose `HorizontalPager` + `graphicsLayer` with spine shadow.
2. **`仿真卷曲` (`SimulationCurlDelegate`)**: 2D Bezier cylinder-projection + Android 13+ AGSL `RuntimeShader` realistic page curl.
3. **`平滑滑动` (`SpringSlideDelegate`)**: Official `HorizontalPager` with `MotionScheme.expressive().fastSpatialSpec()`.
4. **`上下卷轴` (`ContinuousScrollDelegate`)**: Seamless `LazyColumn` stream across chapters and full-width illustrations.
5. **`文库竖排` (`VerticalBunkobonDelegate`)**: Right-to-left vertical CJK columns matching physical Japanese Bunkobon editions.
6. **`墨水屏` (`EInkInstantDelegate`)**: 0ms frame swap + hardware volume-key page turns.
