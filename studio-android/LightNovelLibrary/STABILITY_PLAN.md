# v1.30 Stability & Refactoring Plan

Status: draft, derived from a read-through of `app/` and `api/` at commit `5e00d42`.

**Progress:** "Testing strategy" step 1 (move device-independent tests to the JVM), **Phase 0
(instrumentation)** and **Phase 1 (items 1–8)** are implemented. Phase 0 has not yet *collected*
anything — the ranking below is still inference from reading the code, and stays that way until a
release ships and reports come back. Phase 1 was written to be safe to land without that data;
Phase 2's ordering still needs it. Phases 2–4 are proposed, not done.

**The Phase 1 caveat is discharged.** Those commits were written with no Android SDK and no
emulator, so this section used to warn that `./gradlew assembleAlpha testAlphaDebugUnitTest` had
never been run against them and that CI would be the first real check. It has now run: CI is
green on `3bc1827` (the merge of #185) on both jobs — the build and the JVM suite, and the
instrumented tests, which confirms the emulator repair below as well. Items 1–7 are compiled and
tested code.

**What can still be checked without a device, and what cannot.** Nothing here builds without an
Android SDK, and the SDK is not always fetchable: `dl.google.com` is blocked in the sandbox these
changes are written in, which rules out Robolectric locally too — it needs
`androidx.test:monitor`, published only on Google's Maven, which redirects to the same blocked
host. `repo1.maven.org` and `maven.google.com` itself are reachable; the artifacts behind them
are not. The loop that does work for a change made without a device:

1. **Extract the pure-logic method into a standalone `javac`/`java` harness** with hand-written
   stubs for the Android calls it makes, and run the old and new implementations side by side on
   the same inputs. The read loop in item 3 was checked this way (8 tests pass; 6 fail against
   the previous implementation), `AsyncTaskTracker` against a stub `AsyncTask`, and item 8 below
   against nine inputs, three of which throw on the previous implementation.
2. **Open the PR and let CI build it.** CI now runs on *every* pull request rather than only
   ones based on master (see "Testing strategy"), so the first build of a change happens before
   it merges instead of after.

Guard clauses, anything touching a view, and the Robolectric tests still cannot be executed
anywhere but CI and a device.

## Diagnosis

The app is ~18k lines and structurally sound at the feature level. The instability is not
random rot — it comes from four specific decisions made early that the codebase has been
paying interest on ever since.

Recent git history supports this: `b43e8c7 Fixing NPE in WenkuReaderPaginator`,
`adfe0fc Fix resume crash in NovelInfoActivity`, `522c385 ... and fixing up NPE`,
`723e93d Fixing a bug where the LatestSection can stuck with "Loading..."`. These are
one-off patches to individual symptoms of the same root causes. Each fix is correct and
each leaves the generator intact.

### Root cause 1 — `AsyncTask` with no lifecycle awareness (biggest single source)

24 `AsyncTask` subclasses across 17 files. `AsyncTask` was deprecated in API 30 and has no
concept of the host's lifecycle: `onPostExecute` runs whether or not the Activity is still
alive. Most of them then touch views directly.

Guarded correctly today (4): `Wenku8ReaderActivityV1:436`, `NovelItemListFragment:330,407`,
`LatestFragment:243,277`, `NavigationDrawerFragment:226,234`, `CheckAppNewVersion:82`.

Unguarded and touching views (the rest), e.g. `NovelInfoActivity:672`:

```java
protected void onPostExecute(Integer integer) {
    isLoading = false;
    spb.setVisibility(View.INVISIBLE);   // no isFinishing()/isDestroyed() check
    ...
    tvNovelTitle.setText(mNovelItemMeta.title);
```

Rotate the device or background the app during a slow network call and this is an NPE or
`IllegalStateException`. This class of crash scales with how bad the user's network is,
which is why it looks like "gradually more unstable" — server latency drifted, not the code.

Related: `FavFragment:464` wraps `md.dismiss()` in a bare `try/catch (Exception)` with the
comment *"Ignore the NullPointerException ... or IllegalArgumentException due to View not
attached to window manager."* That is the root cause being acknowledged and suppressed
rather than fixed.

### Root cause 2 — mutable global static state

- `GlobalConfig` — everything is `static`, lazily initialised (`if (bookshelf == null) loadLocalBookShelf();`),
  with no synchronisation. Called from both UI and background threads.
- `WenkuReaderPageView:71-93` — `mLoader`, `mSetting`, `textPaint`, `typeface`, `screenSize`
  are `static`, i.e. shared across every page view instance. Two reader activities, or one
  recreated after rotation, stomp on each other's render state.
- `LightUserSession` — static credentials and session, mutated from `LightNetwork` on the
  network thread.

After process death Android restores the Activity stack but every static is back to its
initial value. The lazy re-init papers over some of this; anything that reads a static
without a null-guard crashes.

### Root cause 3 — `Serializable` model objects passed through `Intent`

`VolumeList implements Serializable` and carries an `ArrayList<ChapterInfo>`. It is passed
as an Intent extra in 6 places (`NovelInfoActivity:554,829,867`, `Wenku8ReaderActivityV1:703,735,843,881`).

Two failure modes:
1. **`TransactionTooLargeException`** — the Binder transaction buffer is ~1MB per process.
   A long-running novel with hundreds of chapters can exceed it. This crash is
   size-dependent, so it hits exactly the most-engaged users on the longest series.
2. **NPE on the receiving side** — `Wenku8ReaderActivityV1:100` reads the extra and
   `:120` dereferences it with no null check:

```java
volumeList = (VolumeList) getIntent().getSerializableExtra("volume");
...
getSupportActionBar().setTitle(volumeList.volumeName);   // NPE if extra is absent/undeserializable
```

`VerticalReaderActivity:75` has the same read.

### Root cause 4 — silent data corruption in the cache layer

`LightCache.loadStream` (`util/LightCache.java:84`):

```java
int fileSize = inputStream.available();
byte[] bs = new byte[fileSize];
if (dis.read(bs, 0, fileSize) == -1) return null;
```

`available()` is an estimate of what can be read *without blocking*, not the file length,
and a single `read()` is not guaranteed to fill the buffer. For local files this usually
works; for anything buffered or larger than the readahead window it silently returns a
truncated or zero-padded array. That array becomes novel XML, which then fails to parse —
surfacing as a downstream parse error or blank chapter rather than an I/O error, which is
why it would be hard to trace from a crash report.

`GlobalConfig.loadFullSaveFileContent` feeds directly off this.

---

## Plan

Ordered by (crash volume eliminated) ÷ (risk of introducing new bugs). Phases 0–2 are the
ones that matter; 3–4 are cleanup that can happen opportunistically.

### Phase 0 — See the problem before fixing it — **implemented**

Nothing else should be prioritised until real crash counts are visible. Before this, Crashlytics
caught only unhandled exceptions — there were **zero** `recordException`, `setCustomKey`, or
`setUserId` calls, so every report arrived with no breadcrumbs.

Everything routes through `util/CrashReporter.java`, which degrades to a logcat write when
Firebase is absent (JVM unit tests, non-GMS devices). That matters more than it sounds: these
calls sit in `catch` blocks, so a reporter that could throw would turn a handled error into a
crash. `CrashReporterTest` pins the degradation path.

1. **Custom keys.** `screen`, `build_flavor`, `gms_available`, `language`, `storage_mode`,
   `logged_in`, `novel_aid`, `chapter_cid`, `reader_mode`. Each is set where the value is
   resolved rather than at startup, so none of them can go stale.
2. **50 `printStackTrace()` calls → `CrashReporter.recordException(label, e)`,** across 23 files.
   (The plan previously said 28; the real count was 50.) Each label is `Class.method`, or
   `Class.AsyncTaskName` where a file holds several tasks — `NovelInfoActivity` alone has five,
   and "doInBackground" would not have distinguished them.
3. **Lifecycle breadcrumbs.** `BaseMaterialActivity` covers 13 of the 15 Activities;
   `VerticalReaderActivity` does it directly because it does not extend that base. Fragments are
   covered by one `FragmentLifecycleCallbacks` registration rather than per-Fragment overrides,
   which also catches child fragments and any Fragment added later. `onDetach` is recorded
   because "AsyncTask finished after the Fragment detached" is the crash shape being hunted.
4. **Two silent failures made visible,** since these produce no signal at all today and no amount
   of crash data would surface them:
   - `LightCache.loadStream` now reports a non-fatal when `read()` returns fewer bytes than
     `available()` promised (root cause 4). Behaviour is deliberately unchanged — this measures
     whether the truncation actually happens in the wild before Phase 1 item 3 rewrites the read.
   - Both readers log a breadcrumb when the `volume` Intent extra arrives null (root cause 3),
     which distinguishes "extra absent" from "deserialisation failed" in the NPE that follows.
     The guard itself is Phase 1 item 1, kept separate so before/after counts stay comparable.

**Deliberately not done: `setUserId`.** The wenku8 account name identifies a real person to a
third party, and Crashlytics already counts affected users via its own installation id, so
sending it would add little beyond the privacy cost. `logged_in` is recorded as a boolean instead.
Revisit only if a crash turns out to need per-account correlation.

Ship this as a point release and let it collect for a week. It converts the rest of this
plan from guesswork into a ranked list.

### Phase 1 — Stop the bleeding (small, surgical, low risk) — **implemented**

These are individually tiny and each kills a known crash class. None require architectural
change, so they can land incrementally without destabilising v1.30.

Four things turned out differently from the plan as written, and are worth recording:

- **`VerticalReaderActivity:75` did not need item 1's guard.** It reads the `volume` extra and
  never dereferences it — the screen renders from `aid`/`cid` alone — so finishing the Activity
  there would have broken a case that works today. The breadcrumb stays; the guard did not go in.
  Two reads elsewhere did need it and were not in the plan: `ViewImageDetailActivity:60`
  (`path.contains("/")` on the next line) and `NovelItemListFragment:87`.
- **Ordering inside the guarded callbacks matters more than the guards.** A bare
  `if (isFinishing()) return;` at the top of `onPostExecute` introduces two new bugs where it
  removes one. Loading flags have to clear *before* the guard, or a screen comes back stuck on
  "Loading..." — bug `723e93d` again. Progress dialogs have to be dismissed *before* it, because
  `ProgressDialogHelper.dismiss()` already tolerates a gone window, so guarding above it leaks
  the dialog rather than crashing on it. And `UserInfoActivity.AsyncLogout` does its session
  teardown before the guard: skipping the logout because the user navigated away would leave
  them logged in with credentials on disk, which is worse than the crash.
- **The `WeakReference<Activity>` tasks were only half safe.** The reference stays reachable for
  as long as anything else holds the Activity, so it hands back destroyed Activities;
  `LightTool.isAlive()` covers that. `NovelReviewReplyListActivity` was also missing its null
  check outright on one branch while having it on the branch next to it.
- **Item 7 makes `null` reachable for callers that could not previously see it.** Most already
  handled it. `FavFragment`'s local bookshelf load did not — it wrapped the parse in
  `Objects.requireNonNull()`, which would have turned a corrupt cached intro file into a crash.
  It now treats an unparseable intro as a missing one. *Any* future parser hardening needs the
  same caller audit.

1. **Null-guard every Intent extra read.** Specifically `Wenku8ReaderActivityV1:100` and
   `VerticalReaderActivity:75` — if `volumeList` is null, finish the Activity with a toast
   instead of dereferencing it. ~10 lines, removes a guaranteed NPE.
2. **Add lifecycle guards to the unguarded `onPostExecute` bodies.** Mechanical:
   `if (isFinishing() || isDestroyed()) return;` for Activities, `if (!isAdded() || getActivity() == null) return;`
   for Fragments. Matches the pattern already used correctly in `Wenku8ReaderActivityV1:436`.
   Then remove the suppressing `try/catch` in `FavFragment:464` so real failures surface.
3. **Fix `LightCache.loadStream`.** Read in a loop into a `ByteArrayOutputStream` until
   `read()` returns -1. Straightforward and unblocks trust in the whole cache layer.
   Worth a unit test — `LightCacheTest` already exists in `androidTest`.
4. **Close the `Cursor` in `LightCache.getFilePath:238`.** It is queried and never closed on
   any path, including the success path. Use try-with-resources.
5. **Replace `new Handler()`** (`NovelInfoActivity:212`, `VerticalReaderActivity:323`) with
   `new Handler(Looper.getMainLooper())`. The no-arg constructor is deprecated and throws if
   the calling thread has no Looper.
6. **Cancel in-flight tasks in `onDestroy`.** Only 4 of 24 tasks are ever cancelled.

   Implemented as `util/AsyncTaskTracker`, wired into the read-only fetch screens
   (`NovelInfoActivity`, both readers, `NovelItemListFragment`, `LatestFragment`). Two decisions
   are load-bearing and are commented at the call sites:

   - **Cancellation is non-interrupting** (`cancel(false)`). AsyncTask routes the result to
     `onCancelled` instead of `onPostExecute` either way, so the unsafe UI callback is dropped
     while background work finishes its writes. `cancel(true)` would interrupt mid-write and
     trade a crash for a corrupt cache file — the very thing item 3 exists to stop.
   - **Fragments cancel in `onDestroy`, not `onDestroyView`.** A Fragment outlives its view in a
     ViewPager, and its `isLoading` flag with it; cancelling on view destruction skips the
     `onPostExecute` that clears that flag and leaves the list stuck on "Loading...".

   Tasks that *change* something — downloading volumes, cloud bookshelf add/remove, logout, post
   submission — are deliberately not tracked: their callbacks are lifecycle-guarded already, and
   cancelling would abandon work the user asked for.
7. **Make the XML parsers validate instead of relying on the parser throwing.**
   Found while migrating the tests (see "Testing strategy"). `UserInfo.parseUserInfo`
   (`global/api/UserInfo.java:38`) builds a `UserInfo`, populates whatever fields it happens to
   recognise, and returns it — the `null` return only ever happens because
   `XmlPullParser.next()` throws on malformed input. Nothing checks that the expected fields
   were actually found.

   So if the server ever returns a *well-formed* non-response — an HTML maintenance page, a
   captive-portal or proxy interstitial, a CDN error page — the parser returns a blank but
   non-null `UserInfo`, and every caller's `!= null` check passes. That surfaces as a logged-in
   user with an empty username and zero score rather than as a clean error. The same shape
   applies to the other parsers in `global/api/`.

   Fix: assert that the required fields were populated before returning, and return `null`
   otherwise. Cheap, and it is exactly the kind of silent failure Phase 0 instrumentation
   cannot see.

8. **Stop `parseNovelItemList` throwing on a non-response.** Not in the original plan — found
   while auditing the list parsers item 7 deliberately left alone. They were left alone because
   "they already return empty collections, which callers distinguish", which is true of every
   list parser except this one: it is the only parser in `global/api/` with no `try`/`catch`
   around its body, so anything it throws reaches its caller.

   Its per-item log line read back the element just added — `list.get(list.size() - 1)` — from
   *outside* the branch that added it. On a quoted token that is not an integer nothing was
   added, so it indexed an empty list and threw `IndexOutOfBoundsException` out of a `@NonNull`
   method. `NovelItemListFragment.AsyncGetNovelItemList` calls it inside `doInBackground` and
   catches `UnsupportedEncodingException` alone, so the throw escaped the task and crashed the
   app instead of reaching the empty-list path that caller already handles.

   Reachable two ways. The expected one is root cause 4's server-side twin: any well-formed
   non-response carrying two or more single-quoted values with no integer among them — a
   captive-portal interstitial, an HTML error page. The unexpected one decided the fix: a
   **valid** novel list whose XML declaration uses single quotes rather than double,
   `<?xml version='1.0' encoding='utf-8'?>`, supplies two non-integer tokens ahead of the first
   `aid` and crashed on the second. Rejecting the response at the first non-integer token — the
   shape item 7 used for the single-object parsers — would have turned that good list into an
   empty one. Non-integer tokens are skipped instead, and only the log line moved inside the
   branch.

   Three tests were added, plus one characterization test recording a quirk found alongside it:
   the scan had no notion of *which* token was the page count, the caller simply takes element
   0, so a non-integer page number did not fail — it shifted, and the first novel silently
   dropped out of the list.

   **Then the scan was replaced outright**, in the follow-up PR, and that characterization test
   was the thing that made the replacement legible: `parseNovelItemList` reads `page/@num` and
   `item/@aid` by name through `XmlPullParser`, like every other parser in the file, so the
   shift quirk is gone rather than recorded and the test now asserts that the novel stays in
   the list. What decided it was not tidiness but a fragility the scan could not be patched out
   of: it took *any* single-quoted value in document order, so `<page num='166' cached='2'/>`
   yielded a phantom novel 2 and `<item type='9' aid='1143'/>` a phantom novel 9. One added
   attribute on either tag — a live possibility now the API sits behind a Cloudflare Worker
   relay rather than the site itself — would have injected non-existent novels into every list,
   failing one at a time rather than failing as a list.

   The scan survives as a private fallback, reached only when the XML parse recognises nothing
   at all. It is the one thing the scan does better: `XmlPullParser` stops at the first byte of
   anything that is not the document, so a response that is well-formed only after some leading
   noise — a PHP notice, a relay banner — parses to nothing, while the scan reads straight past
   it. There is no way to tell from here how often that happens, so the fallback records a
   breadcrumb when it fires. **Delete it, and the private method with it, if a release goes by
   without that breadcrumb appearing.** This is the same measure-then-act shape Phase 0 used for
   `loadStream`, applied to a tolerance nobody can currently justify or refute.

Expected outcome: this should remove the majority of crash *volume* without touching
architecture. Phase 0's data will confirm — and because Phase 0 shipped first, the before/after
comparison is actually available this time.

### Phase 2 — Remove the generators (the actual refactor)

Only worth doing once Phase 1 has landed and the crash rate has visibly dropped, so the
effect of each change is measurable.

1. **Pass IDs, not objects, through Intents.** Replace the `VolumeList` extra with `aid` +
   `vid` and re-read the volume from the local cache on the receiving side. Kills
   `TransactionTooLargeException` permanently and makes the reader survive process death for
   free, since ints in an Intent always restore. This is the highest-value item in the plan.

   **Check the cache assumption first — it does not currently hold.** "Re-read the volume from
   the local cache" assumes `intro/<aid>-volume.xml` is on disk by the time the reader starts.
   It is written only by `NovelInfoActivity.AsyncUpdateCacheTask`, the explicit download/refresh
   action. The ordinary online load (`FetchInfoAsyncTask`'s `volumeTask`) fetches exactly the
   same XML and never writes it, so for any novel the user has merely opened, the receiving
   Activity would find nothing to read and the reader would fail to open — a worse bug than the
   one being fixed. Persisting that response in the sender is a prerequisite, not a detail, and
   it is worth doing on its own: it is also what lets the reader rebuild itself after process
   death, which is Phase 3's problem for these screens.
2. **Retire `AsyncTask`.** Do not rewrite all 24 at once. Introduce one small helper
   (`ExecutorService` + main-thread `Handler`, or `androidx.lifecycle` if you are open to
   adding it) and migrate screen by screen, starting with whichever Phase 0 shows is
   crashiest — likely `NovelInfoActivity` (4 tasks) or `FavFragment` (2 tasks + a 3-thread pool).
   Each migration is independently shippable.
3. **De-static `WenkuReaderPageView`.** Move `mLoader`/`mSetting`/paint state to instance
   fields owned by the reader Activity. This is the fix for the recurring reader NPEs that
   `b43e8c7` and `522c385` patched individually.
4. **Give `GlobalConfig` an explicit `init(Context)`** called from `MyApp.onCreate`, rather
   than 20 independent lazy null-checks. Keeps the static API (so no call sites change) but
   makes initialisation ordering deterministic and thread-safe.

### Phase 3 — Process-death survival

No Activity implements `onSaveInstanceState` for its own state (only two custom Views do).
After Phase 2.1 the reader survives for free; the remaining screens need `aid`/`cid`/scroll
position persisted. Test with *Developer Options → Don't keep activities*, which reproduces
this class of bug deterministically and is worth doing as a routine pre-release check.

### Phase 4 — Opportunistic cleanup

- `universal-image-loader` (1.9.3) has been unmaintained since 2015 and is doing the bitmap
  work in the reader. A migration to Glide or Coil is a real project; worth scoping only if
  Phase 0 shows OOM/bitmap crashes are significant.
- `minSdkVersion 21` — the `Build.VERSION` branches for API < 23 are dead weight for
  approximately 0% of the install base. Raising to 23 or 24 deletes code paths that cannot
  be tested.
- See "Testing strategy" below — the issue is test *distribution*, not test count.

---

## Should we adopt modern architecture first?

Short answer: no, not as a prerequisite phase. Yes, as the *mechanism* of Phase 2.

**Why not architecture-first.** Three of the four root causes are architecture-agnostic. A
Compose + Hilt + Room + Coroutines rewrite still passes an oversized `Serializable` through an
Intent, and still sizes a buffer from `available()`, if that code is carried across — which it
would be, because nobody rewrites a working XML parser during an architecture migration. Only
root cause 1 (lifecycle) is genuinely fixed by adopting ViewModel, and ~90% of that crash
volume is reachable with 20 mechanical guard clauses in Phase 1.

The decisive argument is the interaction with testing. A rewrite is only safe when there is a
test suite to tell you the rewrite preserved behaviour. What exists covers the parsers and the
paginator — the pure-logic core — and nothing above it: no Activity, Fragment, lifecycle, or
storage-path coverage, which is precisely where a rewrite would do its damage. Rewriting first is
the standard route to a v2.0 that is *less* stable than v1.30 — twelve years of accumulated
edge-case handling (parser quirks against real server output, the storage-migration paths,
encoding fallbacks) is embedded in this code and is not written down anywhere else. A rewrite
silently drops it and the bugs come back one user report at a time.

For a single part-time maintainer there is also a stalling risk: a migration abandoned at 60%
leaves two architectures to maintain, which is strictly worse than one consistent old one.

**What is worth adopting, incrementally.** ViewModel + lifecycle-aware loading, because it is
the structural fix for root cause 1 rather than modernisation for its own sake. Adopt it one
screen at a time as part of Phase 2.2, starting with whichever screen Phase 0 data shows is
crashiest. Each screen is independently shippable, and if the effort stalls after two screens
you still keep the benefit of those two.

Compose, Room, and DI are not on the critical path for stability. Defer them.

**When this advice would flip:** if a ground-up feature rewrite were planned anyway, if there
were a team to absorb the migration cost, or if the app were unshippable today. None currently
apply.

## Testing strategy

The suite is 12 test classes in `app/` plus `api/src/test/.../Wenku8APITest.java` — more than it
appears at first glance. The problem was never the count, it was *where* they run:

| Location | Before | Now | Runs on |
|---|---|---|---|
| `app/src/test` | 3 files / 10 tests | **12 files / 68 tests** | JVM, seconds |
| `app/src/androidTest` | 8 files / 31 tests | **2 files / 23 tests** | emulator, minutes |
| `api/src/test` | 1 file | 1 file | JVM |

(Step 1 moved the JVM count from 10 to 37; `CrashReporterTest` took it to 44 in Phase 0; Phase 1
added the rest, mostly `LightCacheStreamTest` and `AsyncTaskTrackerTest`.)

**The emulator flakiness was not theoretical, and CI has been restructured because of it.** The
run for commit `2525b3b` failed in the emulator step, and because that one step ran
`assembleAlpha testAlphaDebugUnitTest connectedAlphaDebugAndroidTest` together, the 44 JVM tests
never reported at all. Four changes to `.github/workflows/android-ci.yml`:

1. **Split into two jobs.** JVM tests no longer depend on an emulator booting. This is the whole
   payoff of having moved those tests off the device: an emulator that will not start now costs
   the 23 instrumented tests instead of all 91.
2. **Enable KVM.** The failure was `ShellCommandUnresponsiveException` during `installCommit`,
   with the emulator console also failing to start — the signature of an emulator running
   unaccelerated. `android-emulator-runner` needs an explicit udev rule on `ubuntu-latest`;
   without it `pm install` of a debug APK this size (Firebase + play-services-ads, AOT-compiled
   by dex2oat at install time on old ART) runs past ddmlib's timeout.
3. **Emulator moved from API 21 to 33.** Counter-intuitive for a minSdk-21 app, but every
   `Build.VERSION` branch in the app targets Q (29) or TIRAMISU (33) and none target 21–22, so
   the API 21 emulator took the uninteresting side of every conditional while being the slowest
   and least reliable image to install onto. Neither remaining instrumented test is
   API-21-specific — `LightCacheTest` only uses `getFilesDir()`.
4. **Run on every pull request.** The trigger was `pull_request: branches: [master]`, but
   development lands on the release branch and only reaches master at release time — so a PR
   into `v1.30` ran no checks at all, and the post-merge push to `v1.30` was the first build of
   the change. #185 merged that way. For a project that cannot be built without an Android SDK,
   and whose contributors may not have one, a first check that arrives after the merge is the
   wrong order; the `pull_request` filter is now removed so the build gates the merge.

Note the API 21 → 33 move means the minSdk floor is no longer verified by CI. That is an accepted
trade for two test classes; if Phase 4 raises `minSdkVersion` anyway the question goes away.

**The codebase already contains the answer.** `WenkuReaderPaginatorTest` injects the
Android-dependent piece — text measurement — as a lambda:

```java
new WenkuReaderPaginator(XML_LOADER, text -> text.length() * 20, 400, 800, 30, 10, 20);
```

That single seam decouples pagination from Android entirely and makes the most intricate logic
in the app testable on the JVM with deterministic arithmetic. Propagating this one technique is
worth more than any framework adoption.

Recommended order:

1. ~~**Move device-independent tests to the JVM.**~~ **Done.** Six test classes moved from
   `androidTest` to `src/test`; JVM coverage went from 10 tests to 37, all passing, in seconds
   rather than an emulator boot. Only `LightCacheTest` (needs `InstrumentationRegistry` and real
   storage paths) and `MyAppTest` (mocks `Application` lifecycle) still require a device.

   **This step surfaced a real trap, worth recording.** The XML parsers use `XmlPullParser`,
   which is a no-op stub under the plain JVM runtime, so every parse silently returned `null`
   and the tests failed on migration. Adding a standalone `kxml2` made them run — but
   *inverted* the malformed-input cases: `UserInfo.parseUserInfo("garbage")` returns `null` on
   Android and a blank non-null object on kxml2. The suite would have been green while
   asserting behaviour the device does not have. The fix was Robolectric
   (`@RunWith(RobolectricTestRunner.class)`), which runs against real Android framework code.

   Rule of thumb this establishes: **when moving a test to the JVM, a passing result is not
   sufficient evidence — the negative/malformed-input cases are the ones that reveal whether
   the runtime is behaviour-faithful.**
2. **Write characterization tests, not aspirational ones.** For a 12-year-old parser handling
   messy real-world server output, current behaviour *is* the spec, quirks included. Capture
   real API responses as fixtures and assert what the code does today, before changing it.
3. **Prioritise silent failures over loud ones.** Crashlytics already reports crashes. It does
   not report `loadStream` truncating a chapter into unparseable XML — the user just sees a
   blank page and uninstalls. `LightCache`, `Wenku8Parser`, `OldNovelContentParser`, and
   `WenkuReaderPaginator` are the high-value targets, and all are pure logic.
4. **Let testability be the byproduct of each refactor,** not a prerequisite for it. Phase 2.3
   (de-static `WenkuReaderPageView`) makes that class testable as a side effect of fixing a
   crash. That is the pattern: fix a root cause, gain a seam, add tests through it, repeat.

**One trap to know about.** `app/build.gradle:94` sets `unitTests.returnDefaultValues = true`,
which makes un-stubbed Android framework calls silently return `0`/`null`/`false` instead of
throwing. A JVM test can therefore pass while the same code path NPEs on a device — this is
exactly what bit step 1 above. It is a reasonable setting for pure-logic tests, but do not trust
it for anything Android-coupled: prefer a real seam (as the paginator does), or use Robolectric,
which is now on the test classpath for that purpose.

**Do not add a standalone `kxml2` as a lighter alternative to Robolectric.** It makes the XML
tests run, but its malformed-input behaviour differs from Android's parser, which silently
inverts the negative test cases. See step 1.

---

## Suggested sequencing

| Ship | Contents | Risk |
|---|---|---|
| v1.30.0 | Phase 0 instrumentation only | none |
| v1.30.1 | Phase 1 items 1–7 — **implemented, CI green**; item 8 pending its first CI run | low, mechanical |
| v1.31 | Phase 2.1 (Intent payloads) + highest-crash screen from 2.2 | medium |
| v1.32+ | Remainder of Phase 2, Phase 3 | medium |

The important structural point: **Phase 0 before Phase 1**. Without crash data the ranking
above is inference from reading code, and inference will misallocate effort.
