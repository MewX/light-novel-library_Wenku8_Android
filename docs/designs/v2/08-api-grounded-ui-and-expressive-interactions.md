# 08 · Strict `Wenku8API` UI Mapping, `api-stub` Contracts & Light Novel Expressive Interactions

This document ensures every screen, tab, chip, and action in Wenku8 Android `v2.0` maps **1:1 to real, active `Wenku8API` capabilities** in `studio-android`, enforces crash-free `api-stub` CI behavior, and details how **Material 3 Expressive (`M3e`)** elevates Light Novel and Artbook/Illustration reading.

---

## Part 0: Audit of Unused / Unsupported `Wenku8API.java` Stubs (Excluded from UI)

A codebase-wide audit of `studio-android/LightNovelLibrary/app/src/main/java/` confirmed that several helper methods in [`Wenku8API.java`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java) are legacy dead stubs that are **never called in `studio-android`**. `v2.0` strictly excludes these from online UI screens:

| Unused Stub / Unparsed Field | Why It Is Excluded from `v2.0` Online UI |
| :--- | :--- |
| **`Wenku8API.getVoteNovelParams(int aid)`** (`action=book&do=vote` — "推荐本作") | Dead stub in `Wenku8API.java`; never called anywhere in `studio-android`. **Excluded from Novel Detail and Foldable Detail.** |
| **`Wenku8API.getLibraryList()` / `getNovelListByLibrary*()`** (`action=xml&item=sort` — "文库分类 1..14") | Dead stubs in `Wenku8API.java`; never called anywhere in `studio-android`. **Excluded from Online Tabs & Online Search.** *(Note: `NovelItemMeta.pressId` returned by `do=meta` is rendered as a metadata badge on Novel Detail and can filter already-cached local Bookshelf entries in Room.)* |
| **Online `#Tag` Search** | `Wenku8API` supports only `searchtype=articlename` and `searchtype=author`. `#Tag` filtering is strictly scoped to **local Room Bookshelf filtering** (`NOVELS.tags_csv`). |
| **Unparsed `TotalHitsCount` / `PushCount` / `FavCount` on Feed/Ranking Rows** | [`NovelListWithInfoParser.java`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/NovelListWithInfoParser.java#L83-L105) parses only `aid`, `Title`, `Author`, `BookStatus`, `LastUpdate`, `Tags`, and `IntroPreview`. Hit/Push/Fav counters are parsed only by `NovelItemMeta` (`do=meta`) on `NovelDetailScreen`. |

---

## Part 1: Active `Wenku8API` & Relay Endpoints → Material 3 Expressive (`M3e`) Matrix

### 1. Bookshelf (`Tab 1 · 书架`), Cloud Bookcase & Atomic Room SQLite
| API Method / Parser | Wire Action | Returned Fields & Constraints | `v2.0` M3e UI & Expressive Function |
| :--- | :--- | :--- | :--- |
| [`Wenku8API.getBookshelfListParams(l)`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L593-L635) + [`BookshelfListParser`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/BookshelfListParser.java) | `action=bookcase&t={0\|1}` | `<book aid="..." date="YYYY-MM-DD"><name>...</name><chapter cid="...">...</chapter></book>` | **Single-Request Cloud Update Diff**: Compares cloud `date` and `latestChapterCid` against `wenku8_v2.db`. Books with newer `latestChapterCid` pop a `tertiaryContainer` badge (`新章节`) under the `有新章节` `FilterChip`. |
| [`Wenku8API.getAddToBookshelfParams(aid)`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L637-L645) / `getDelFromBookshelfParams(aid)` + [`BookshelfSync`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/BookshelfSync.java) | `action=bookcase&do={add\|del}&aid={aid}` | Cloud shelf limit (~60–100 books by rank); local Room shelf (`in_bookshelf = 1`) is unlimited | **Non-Destructive 2-Way Sync**: Local-only books are pushed up and never deleted locally. |
| [`NovelDownloader`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/NovelDownloader.java) | `do=meta` + `do=intro` + `do=list` + `do=text` | 4 modes: *Smart Update*, *Update Only*, *Select Volumes*, *Overwrite All* | **Non-Blocking `WorkManager` + Active-Only `LinearWavyProgressIndicator`**: Commits TOC updates via atomic `@Upsert` (`toc_version`, Doc `09` §4) and stores offline chapters in `chapter_contents`. |

---

### 2. `最新` (`Tab 2`) & `排行` (`Tab 3`) — 13 Active `NovelSortedBy` Modes
| API Method / Parser | Wire Action | Returned Fields & Constraints | `v2.0` M3e UI & Expressive Function |
| :--- | :--- | :--- | :--- |
| [`Wenku8API.NovelSortedBy`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L111-L155) + [`NovelListWithInfoParser`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/app/src/main/java/org/mewx/wenku8/global/api/NovelListWithInfoParser.java) | `action=mewx_articlelist` / `action=novellist` (`sort=...&page=...&t=...`) | **3 Feeds (`Tab 2 · 最新`)**: `lastUpdate` (最近更新), `postDate` (最新入库), `fullFlag` (完结精品)<br/>**10 Rankings (`Tab 3 · 排行`)**: `dayVisit`, `weekVisit`, `monthVisit`, `allVisit`, `dayVote`, `weekVote`, `monthVote`, `allVote`, `goodNum` (总收藏), `size` (字数榜) | **Familiar 4-Tab M3e Shell**:<br/>- `Tab 2 · 最新`: `PrimaryTabRow` (`最近更新 · 最新入库 · 完结精品`).<br/>- `Tab 3 · 排行`: Connected `ButtonGroup` (`点击榜 · 推荐榜 · 总收藏 · 字数榜`) × `FilterChip`s (`周榜 · 日榜 · 月榜 · 总榜`). |

---

### 3. Online Search (`articlename` & `author`), `5s` Cooldown Queue & Room Search History
| API Method | Wire Action | Returned Fields & Constraints | `v2.0` M3e UI & Expressive Function |
| :--- | :--- | :--- | :--- |
| [`Wenku8API.searchNovelByNovelName`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L308-L326) & `searchNovelByAuthorName` | `action=search&searchtype={articlename\|author}&searchkey=...` | Returns `<item aid='...'/>` hydrated via `do=bookinfo`; **5s upstream search cooldown** | **M3e Search Studio**: Instant `< 1ms` local Room shelf match card + online `按小说标题` / `按作者名称` / numeric `编号直达`, with an **auto-queued 5s cooldown countdown pill**. |

---

### 4. Novel Detail (`do=meta`, `do=intro`, `do=list`) & Non-Intrusive Support Ad
| API Method / Component | Wire Action | Returned Fields & Constraints | `v2.0` M3e UI & Expressive Function |
| :--- | :--- | :--- | :--- |
| [`Wenku8API.getNovelFullMeta`](file:///home/mewx/codes/light-novel-library_Wenku8_Android/studio-android/LightNovelLibrary/api/src/main/java/org/mewx/wenku8/api/Wenku8API.java#L229-L249) + `getNovelFullIntro` + `getNovelIndex` | `do=meta`, `do=intro`, `do=list` | Full stats, synopsis, `<volume vid="..."><chapter cid="...">` | **Scoped `CoverThemeEngine` Header + Reading-Only `SplitButtonLayout` (`▶ 继续阅读 | ▾`) + Balanced Secondary Action Row (`✓ 已在书架 · ↓ 离线缓存 · 💬 书评`) + Inline Volume Accordions**. |
| `AdView` (`play-services-ads`) | Single inline `BANNER` on `NovelDetailScreen` only | Lazy-initialized post-first-frame; pre-reserved height only when online + GMS present | **Zero-CLS Server-Support Card**: Placed quietly between Synopsis and Volume Accordions (never in Reader/Bookshelf, zero accidental tap proximity, collapses cleanly offline). |

---

### 5. Chapter Content, Reviews, Account Auth & Relay Diagnostics (`api-stub` Safe!)
| API Method / Endpoint | Wire Action | Constraints & `api-stub` Contract | `v2.0` M3e UI & Expressive Function |
| :--- | :--- | :--- | :--- |
| `getCommentListParams`, `getCommentContentParams`, `getCommentNewThreadParams`, `getCommentReplyParams`, `getAvatarURL(uid)`, `searchBadWords()` | `action=review&do={list\|show\|post\|reply}` | `>= 7` chars, `searchBadWords()`, `10s` cooldown, GBK Base64.<br/>**`api-stub` Rule**: `getAvatarURL` must return `""` and `searchBadWords` must return `null` (never throw `UnsupportedOperationException`). | **M3e Review Floors & Composer**: Real UID avatars (`avatar.wenku8.com`), floor quoting, `400ms` auto-saved `REVIEW_DRAFTS`, and live pre-submit status chips. |
| `getUserLoginParams`, `getUserLoginEmailParams`, `getUserLogoutParams`, `getUserInfoParams`, `getUserSignParams`, `NoticeString` | `login` (username / email `<= 30` chars), `logout`, `userinfo`, `do=sign`, `notice_sc/tc` | `REGISTER_URL` opens external browser; `NoticeString` parses real `<a href>` URLs | **`Tab 4 · 我的` Account & Notice Card**: Complete Login/Register/Logout sheet, expired-session banner, celebratory `每日签到 (+1)`, and dismissible server notice. |
| `POST /__diagnostics/latency` + `wenku8-ip-candidates-v1` | Session-free bounded JSON probe on configured relay | **`api-stub` Rule**: `BASE_URL == "Unknown"` renders `"此版本未配置中转检测服务"` with zero native/network calls ([Doc `11`](./11-network-diagnostics-and-auto-routing-ui.md)). | **Default-On `"自动优化连接"` + `NetworkDiagnosticsScreen`**: OkHttp `EventListener` telemetry, automatic candidate-IP promotion, and privacy-redacted report sharing. |

---

## Part 2: Material 3 Expressive Interactions for Light Novel & Artbook Reading

1. **Inline `<!--image-->` Art Cards → Shared-Bounds Artbook Overlay (`Wenku8ReaderEngineV2`)**:
   - Inline chapter illustrations expand via `SharedTransitionLayout` (`defaultSpatialSpec()`) into an in-reader Artbook overlay with `BitmapRegionDecoder` 4× tiling zoom, **`跨页拼合` (`口绘` double-page spread stitching)**, and an M3e `HorizontalMultiBrowseCarousel` thumbnail strip.
2. **Volume-Level `<chapter>插图</chapter>` Quick-Jump**:
   - Automatically detects `<chapter>插图</chapter>` in `do=list` and surfaces a `tertiaryContainer` **`🖼 插图章节` `AssistChip`** on collapsed volume rows.
3. **Six Official-Compose Page-Turn Modes**:
   - Live switching inside the Reader (`覆盖翻页 · 仿真卷曲 · 平滑滑动 · 上下卷轴 · 文库竖排 · 墨水屏`), with **opaque active-slider capsule preview** during typography adjustments.
