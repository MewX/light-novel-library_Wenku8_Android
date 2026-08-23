# v1.30 Stability & Refactoring Plan

Status: draft, derived from a read-through of `app/` and `api/` at commit `5e00d42`.

**Progress:** "Testing strategy" step 1 (move device-independent tests to the JVM), **Phase 0
(instrumentation)**, **Phase 1 (items 1–8)** and **Phase 2 item 1 (Intent payloads)** are
implemented. Phase 0 has not yet *collected* anything — the ranking below is still inference from
reading the code, and stays that way until a release ships and reports come back. Phase 1 was
written to be safe to land without that data, and so was Phase 2.1: it fixes a size-dependent
crash that does not need crash counts to rank. **The rest of Phase 2 does need them** — 2.2 asks
which screen to migrate first, and that is exactly the question the data answers. Phases 3–4 are
proposed, not done.

**Standing priority: fill the test gap before writing more logical patches.** Where a remaining
item could be addressed either by changing behaviour or by covering it, cover it. The evidence for
this is in this document's own history: item 9 was found by writing a test, item 10 by using the
app, and neither was found by any amount of reading the code — while several rounds of careful
reading produced a ranking that is still, by its own admission, inference. The codebase's deficit
was never the number of fixes; it is that almost nothing above the storage layer can be verified
at all.

Two clarifications, because this rule is easy to over-apply:

- **It is not a freeze.** A live crash with a known cause still gets fixed — item 9 was a crash on
  the home screen and waiting for perfect coverage would have been absurd. The rule bites on
  *discretionary* changes: improvements, hardening, and anything whose value is "this looks
  fragile".
- **Refactoring to create a seam is test work, not a patch.** The two biggest gaps below cannot be
  covered without one, so extracting a testable unit counts as filling the gap even though it
  edits production code. What it must not do is change behaviour on the way past.

The gaps worth attacking, in order:

1. ~~**The reader flow.**~~ **Both decisions now have seams.** This was the largest gap in the
   codebase and it is closed.
   - *Which chapter to move to* — `ChapterNavigator` (12 JVM tests). The logic was copy-pasted
     four times inside `Wenku8ReaderActivityV1`: once each for the previous/next buttons and once
     each for paging off an end. Extracting it deleted 120 lines from the Activity and moved the
     boundary behaviour into the fast suite.
   - *Whether to load a chapter from cache or the network* — `ChapterContentLoader` (9 JVM tests).
     Both readers ran their own copy inside an `AsyncTask.doInBackground`, which is why item 10's
     fix shipped unverified. The I/O is injected, so the decision is testable with no filesystem
     and no network, and **item 12 is no longer blocked**.

   The outcome type is deliberately neutral rather than a `Wenku8Error.ErrorCode`: the two readers
   map failures differently — `Wenku8ReaderActivityV1` separates an empty response from a parse
   failure, `VerticalReaderActivity` collapses both to `-100` — and a shared seam must not quietly
   change either. It also keeps the seam and its tests free of any `api/` dependency.

   What is still uncovered is the *plumbing* around both: that the buttons are wired, the dialogs
   appear, and the Intent reopens the reader. The decisions are tested; the wiring is not.
2. **Lifecycle guards.** Phase 1 item 2 added guards to ~20 `onPostExecute` bodies against root
   cause 1, the largest single crash source, and not one of them is exercised by a test.
3. **`GlobalConfig`'s remaining save/load surface.** ~30 methods. **Partly closed:** the bookshelf
   and volume index already had device coverage, and reading positions (`ReadSavesV1Test`, 15),
   search history (`SearchHistoryTest`, 14) and the superseded V0 positions (`ReadSavesV0Test`, 11)
   now do too. Covering that last one found a live crash — see Phase 1 item 13. Still uncovered:
   `loadAllSetting` / `saveAllSetting`, the notice, and the user-account pair — the last of which
   nothing should test, for the reason given under "No test signs in".

   Reading positions were the priority of that group because they are the only thing in the save
   folder nobody can reconstruct: a bookshelf can be re-added and a chapter re-downloaded, but not
   where someone was on page 300. The file is also rewritten every time the reader leaves a
   chapter, so it is the one most often caught by item 11's truncating writer.

   **Search history was where the defects were, and there are two.** Neither is fixed; both are
   pinned by tests named `testKnownDefect…` so a later change has to state that it is changing
   them. The format wraps each term in brackets and escapes nothing, and the scanner never advances
   its cursor past a closing bracket — so a term containing `[` is re-scanned from inside itself and
   its tail reappears as a phantom search nobody made, growing the list by one entry per round trip
   until the cap evicts real searches. Separately, `addSearchHistory` and `deleteSearchHistory` both
   open with `if (searchHistory.contains("[")) return;`, which asks whether the *list* holds a term
   equal to `"["` — almost certainly meant as a check on the incoming `record`. One search for `[`
   puts exactly that term in the list, and from then on the guard fires on every call: the history
   stops recording and stops accepting deletions for the life of the install, with no way out but
   clearing it and no indication to the user.

   **Left unfixed on purpose, and this is the same judgement as item 11.** Both live in the
   serialization format of a subsystem under a standing freeze; a patch here is a bandaid on code
   scheduled for replacement, and the escaping fix in particular would need a migration for
   histories already on disk. The cost of leaving them is small and bounded — a search history is
   the one thing in the save folder a user can reconstruct by retyping. Recorded so the storage
   migration inherits them as requirements rather than rediscovering them.

Note this sits alongside the storage decision, which points the same way: `STORAGE_MIGRATION_PLAN.md`
declines incremental fixes to persistence in favour of replacing it properly. Both rules trade
short-term patching for work that compounds.

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

### Picking this up on a machine with an SDK

Everything above describes working *without* one. On a normal development machine none of it
applies, and there is work waiting that only that machine can do.

**Run what CI runs, plus the part CI cannot judge.**

```
./gradlew assembleAlpha testAlphaDebugUnitTest      # 114 JVM tests, seconds
./gradlew connectedAlphaDebugAndroidTest            # 65 tests, needs an awake, unlocked device
```

**Both have now been run on a real device** — a Pixel 10 Pro Fold on API 37, i.e. above the
API 33 CI emulator and above `targetSdk 36`. 114 JVM tests and 65 instrumented tests, no failures.
That is the first execution of the instrumented suite on hardware rather than an emulator, and it
says the storage tests hold on a current device as well as on API 33.

Getting there cost most of a session, almost none of it spent on the app. The traps below are
environmental, they are not obvious from the failure messages, and each one burned real time.

#### Device setup traps

**1. Two `adb` binaries fight over port 5037.** On Ubuntu, `/usr/bin/adb` is version 1.0.39 —
packaged from the Android 8.1 era — while the SDK ships 1.0.41 in `platform-tools`. They share
one daemon port, so whichever runs last kills the other's server and replaces it:
`adb server version (41) doesn't match this client (39); killing...`. Gradle always uses the SDK
one, so a stray `adb` from the shell silently downgrades the daemon underneath a build.
**Always invoke `~/Android/Sdk/platform-tools/adb` by absolute path, or put it ahead of
`/usr/bin` on `PATH`.** This was not the cause of the install failure below, but it is a real
hazard and it muddied the diagnosis for several runs.

**2. Installs stall because of Play Protect, not WSL2.** The symptom is
`com.android.ddmlib.InstallException: Failed to install-write all apks` from Gradle, or a plain
`adb install` that simply never returns. **This was misdiagnosed for several sessions** and the
earlier explanation in this document was wrong; what follows replaces it.

The cause is Google Play Protect's ADB install verification, controlled by the
`verifier_verify_adb_installs` global setting, which is `1` by default. It intercepts the install
on-device and can hang for many minutes before the APK is committed.

The measurement that settles it — splitting `adb install` into its two halves, which is also the
diagnostic worth reaching for first:

| Step | Time |
| --- | --- |
| `adb push` of the same 20 MB APK | **0.5 s** (59 MB/s) |
| `adb shell pm install` with the verifier on | hung; killed at 5 min and at 7 min |
| `adb shell pm install` with the verifier off | **11.6 s** |

If push is fast and install hangs, it is verification. It is never the cable.

```
adb shell settings get global verifier_verify_adb_installs   # save it, normally 1
adb shell settings put global verifier_verify_adb_installs 0
adb push <apk> /data/local/tmp/x.apk && adb shell pm install -r -t /data/local/tmp/x.apk
adb shell settings put global verifier_verify_adb_installs 1  # restore when finished
```

Note that `connectedAndroidTest` uninstalls both packages when it finishes, so a manual
pre-install does not survive into a subsequent Gradle run — use
`adb shell am instrument -w org.mewx.wenku8.test/androidx.test.runner.AndroidJUnitRunner`.

**3. That uninstall destroys the save data, and on 2026-08-22 it did.** The line above was already
in this document, describing the uninstall as an inconvenience. It is not. On any device past the
API 33 migration the save folder is `SaveFileMigration.getInternalSavePath()` —
`/data/data/org.mewx.wenku8/files/` — and `/sdcard/wenku8/` no longer exists. Uninstalling the
package deletes the bookshelf, `read_saves_v1.wk8`, `settings.wk8`, the search history, the saved
login and every downloaded chapter.

A `connectedAlphaDebugAndroidTest` run against the development phone hit the install failure in
trap 2, and AGP responded by uninstalling to recover:

```
11:48:07  Force stopping org.mewx.wenku8 ... deletePackageX
11:48:08  broadcast=ACTION_PACKAGE_FULLY_REMOVED pkg=org.mewx.wenku8
```

Recovery was attempted and mostly failed. Worth recording precisely, because it defines what this
mistake costs next time:

- **The bookshelf survives.** `FavFragment.AsyncLoadAllFromCloud` pushes local-only aids up with
  `getAddToBookshelfParams`, so a logged-in device has already mirrored its shelf to the account.
  Logging back in restores it.
- **Reading positions do not.** `read_saves_v1.wk8` has no cloud counterpart anywhere in the app.
  This is the one file a user cannot reconstruct, and it was lost.
- **Android Auto Backup did not save it.** `android:allowBackup="true"` is set with no exclusion
  rules and Backup Manager was enabled, but a forced `bmgr restore <token> org.mewx.wenku8`
  against the device's own set returned `restoreFinished: 0` with nothing written — most likely
  the app exceeds the 25 MB Auto Backup quota once chapters are cached, which makes the backup
  silently no-op. **Do not count on Auto Backup for this app.**

So: never run `connectedAndroidTest` against a device holding real data. Use an emulator, or the
`am instrument` route above after a `pm install -r`, which updates in place and never uninstalls.

**That route is no longer theoretical — it was run end to end on 2026-08-23 and is now the way to
work on this project without an emulator.** `assembleAlphaDebug assembleAlphaDebugAndroidTest` to
build without installing, `adb push` both APKs to `/data/local/tmp`, md5-compare them on the device,
`pm install -r -t` each, then `am instrument -w -e class <FQCN>`. `NovelInfoCachedNovelTest` ran its
7 tests in **7.9 seconds**, against roughly ten minutes to learn the same thing from CI, and the app
data directory was intact afterwards. Delete the staged APKs when finished.

Two things make this safe rather than merely fast, and both are easy to skip. **Hash-check the
install**: a stalled install leaves the previous APK in place, so the tests exercise old code and
pass, which has caught people here more than once. **Name the class explicitly** — do not run the
suite unfiltered against a device holding real data, because `FavFragmentHostingTest` performs a
genuine bookshelf sync against the logged-in account. It is `RealApi`-guarded, so it skips on CI,
but locally the real `api/` submodule is present and it runs.
That reading positions have no cloud copy and no working backup is a product gap this incident
exposed rather than a testing one: the only copy of a user's reading history lives in one file in
app-private storage, which any uninstall removes. Nothing in this plan currently addresses it.
Candidates, none scheduled: sync positions to the account alongside the bookshelf, add an
export/import action, or set `android:fullBackupContent` rules that keep the small `.wk8` files
inside the Auto Backup quota by excluding the cached chapters.

*Two dead ends, recorded so they are not re-run.* Neither is worth revisiting:

- **WSL2 `usbipd` passthrough.** Inferred from the timing signature alone — every failure landing
  near ddmlib's four-minute timeout while successes took 24–29 s. Plausible and wrong; a 0.5 s
  push through the same USB link disproves it.
- **`adb logcat` as a keepalive.** Two early successes happened to have logcat streaming
  alongside. A third run with logcat still failed. Flaky, not causal.

**A stalled install leaves the previous APK in place**, so the next test run silently exercises
stale code and "passes". This has caused a wrong conclusion at least four times. Always confirm
by hash before trusting a result:

```
adb shell md5sum $(adb shell pm path org.mewx.wenku8 | sed 's/package://' | tr -d '\r')
md5sum app/build/outputs/apk/alpha/debug/app-alpha-debug.apk
```

*Addendum, and it matters because the documented fix does not always work:* on the Pixel 10 Pro
Fold, `settings put global verifier_verify_adb_installs 0` **silently fails** — the write is
accepted and the value still reads back `1`. The install then hangs exactly as described above.
What does work on that device is `settings put global package_verifier_user_consent -1`, after
which the same install finished in 2.1s having previously burned a ten-minute timeout. Always read
the setting back rather than assuming the write took, and always hash-check afterwards: the failed
install left the *previous* test APK in place, so the suite would have run and passed against code
that predated the change.

*Second addendum, and it supersedes the settings knobs as the first thing to try.* The consent
workaround above then failed too — `package_verifier_user_consent` read back `-1`, and
`adb install` still burned a full ten-minute timeout with neither APK landing. The split
diagnostic is what resolved it, and the result narrows the cause further than "verification":

| Step | Time |
| --- | --- |
| `adb install` of the 20 MB APK, verification already disabled | hung; killed at 10 min |
| `adb push` of the same APK | **0.33 s** (60 MB/s) |
| `adb shell pm install` of the pushed file | **Success**, immediate |

So the hang is not only on-device verification — it is in `adb install`'s own streaming
`install-write` path, which pushing and installing separately bypasses entirely. **Prefer
`push` + `pm install` unconditionally**; it is faster, it needs no global settings changed, and it
has not yet failed. Reach for the verifier knobs only if `pm install` itself is what hangs.

**3. Lifecycle tests need the screen awake and unlocked.** `ActivityScenario` cannot reach
`RESUMED` on a dozing device — the system parks activities at STOPPED — so every test using
`launch` or `recreate` fails with `Activity never becomes requested state "[RESUMED]" (last
lifecycle transition = "STOPPED")` after a **45-second** timeout. That message reads exactly like
the Activity failing to start, which is the trap: the first instinct is to debug `onCreate`, and
five tests failed this way before the cause was found.

Tell it apart from a real defect by the device, not the app: `dumpsys power` shows
`mWakefulness=Dozing`, `dumpsys window` shows `isKeyguardShowing=true`, and the `TaskInfo` in
logcat around the failure carries `isSleeping=true isInteractive=false`.

`adb shell input keyevent KEYCODE_WAKEUP` wakes the screen. `adb shell wm dismiss-keyguard` only
works with **no** secure lock; where `dumpsys trust` reports `deviceLocked=1` it must be unlocked
by hand. `ReaderRecreationTest` asserts both conditions in `@Before`, so this now fails in ~0.03s
with an actionable message instead of 45s of ambiguity — worth copying into any future lifecycle
test.

**4. CI builds against `api-stub`; your machine almost certainly does not.** `settings.gradle`
points `:api` at the private submodule when `api/build.gradle` exists and at the in-repo
`api-stub/` otherwise. CI never checks out the submodule. So **CI always builds against the stub
and any machine with the submodule never does** — a divergence that no local run can see.

Most stub methods `throw new UnsupportedOperationException("stub")`, which makes this sharp. The
symptom in a CI log is not a test failure at all:

```
Test run failed to complete. Instrumentation run failed due to Process crashed.
```

No assertion, no test name — because no test ever ran. Two throws did this, and the second was
far worse than the first:

- `Wenku8API.getNovelContent()`, reached from `Wenku8ReaderActivityV1.onCreate` **eagerly**,
  before the reader decides whether it needs the network at all. A chapter opened entirely from
  disk still went through the API and took the process down.
- `LightUserSession.getLogStatus()`, sampled by `BaseMaterialActivity.onResume` — the base class
  of every Activity in the app. While it threw, **no Activity in this project could reach RESUMED
  on a stub build**, so no instrumented test that launches a screen could ever pass on CI.

Reproduce CI's configuration locally instead of inferring it from a red build:

```
./gradlew -DforceApiStub assembleAlphaDebug testAlphaDebugUnitTest   # or WENKU8_FORCE_API_STUB=1
```

Added for exactly this reason. **Run it before pushing anything that touches an Activity.**

The rule for `api-stub`: where a stubbed method has a truthful failure value, return it — the
`LightNetwork` request methods are already `@Nullable` and now return `null`, which is genuinely
what "no server" means, so a stub build exercises the app's offline path rather than dying inside
an `AsyncTask`. Where there is no honest inert value — logging in, logging out, the user-file
crypto — keep throwing, so a path nobody has considered stays loud instead of quietly succeeding
against an invented answer. **Do not put fixture data in the stub**: test data belongs in each
test's own fixture, and data in the stub becomes global state every future test silently inherits.

*Recorded so it is not re-run:* three consecutive CI fixes were pushed on inference from job step
*durations*, because the logs were unreadable from the dev machine (403 on logs, 401 on
artifacts, no `gh`, no token). All three were wrong, and each cost a full CI cycle. The stack
trace was in the log the whole time. Ask for the log rather than theorising from timing.

**5. Do not run `adb tcpip`.** Switching `adbd` to TCP restarts it and drops the USB transport,
and under WSL2 the device does not come back on its own: it needs re-attaching with
`usbipd attach` from Windows. On Android 11+ this is a bad trade anyway, since wireless
debugging uses a random port and a pairing code that only the device screen shows, so
`adb connect <ip>:5555` will not reach it. Recovering costs a replug.

**6. An instrumented crash is blamed on whichever test was running when it landed, not the one
that caused it.** Background work outlives the test that started it, and a `Process crashed`
report names the current test. `UserLoginActivityLifecycleTest` produced exactly this:
`AsyncLoginTask.doInBackground` sleeps 500ms before it calls anything, so a tap in one test
crashed the run three tests later, and the report accused a test that taps nothing. The stack
trace is the reliable part — read it and find the test that reaches that code, rather than the
test named at the top. Per-test status codes make the sequence visible:

```bash
adb shell am instrument -w -r -e class <FQCN> org.mewx.wenku8.test/androidx.test.runner.AndroidJUnitRunner \
  | grep -E "INSTRUMENTATION_STATUS: test=|INSTRUMENTATION_STATUS_CODE:"
```

`0` is a pass, `-2` a failure; the culprit is usually the test that passed immediately before the
one that died. Note that JUnit 4 does not run methods in source order, so "before" means what the
log says, not what the file says.

**7. `setText` in a test is filtered like typing is.** `layout_user_login.xml` puts
`android:maxLength="30"` on both fields, so a test setting 31 characters silently gets 30 —
which is *valid* input, so it sailed past the Activity's own `length() > 30` guard and submitted
a real login attempt. On a stub build that surfaced as trap 6 above; on a real build it would
have gone to the wenku8 server. Two lessons: input-length guards behind a `maxLength` cannot be
reached from the view layer at all (test the filter instead, which is the thing actually holding
the line), and a test that fills a form is one bug away from submitting it.

**8. A save-file test does not read the same storage root the running app does.**
`GlobalConfig.getDefaultStoragePath()` returns the legacy external path
(`/storage/emulated/0/wenku8/saves/`) unless `lookupInternalStorageOnly` is set — and that flag is
assigned in exactly one place, `loadAllSetting()`. The app calls it during startup; an instrumented
test that only touches the save methods never does. So in a test process the default root is the
legacy external one, which is unwritable on API 29+, and **every write silently takes the fallback
branch into internal storage** while the app under normal use writes straight there.

The visible consequence is that assertions on where a file ends up, or on whether one exists at
all, hold for the environment rather than the contract — a test can look correct and be pinning the
fallback path. Assert what loads back, not what lands on disk; and if a test genuinely needs the
on-disk shape, resolve the path through `GlobalConfig` at assertion time rather than assuming.
`SaveFileFixture` probes both roots in the app's own order for this reason, and its `describe()`
prints both paths, which is how this was found.

A second-order effect worth knowing: because the first write always fails there,
`LightCache.saveFile` catches an `IOException` and files a `CrashReporter.recordException` on
essentially every save a test performs. Those reports are an artefact of the test environment, not
defects.

#### A packaging hazard found while diagnosing the above

`app/build.gradle:11` sets `applicationId "org.mewx.wenku8"`, and **no flavor and neither build
type applies an `applicationIdSuffix`.** Alpha, baidu, playstore, debug and release therefore all
install under one package name. A debug build from this repo replaces an installed release copy
of the app, and `connectedAndroidTest` then uninstalls it on the way out — so running the
instrumented suite on a personal device removes the real app, and its local bookshelf with it.

The fix is a suffix on the debug build type, but it is not a one-liner: `app/google-services.json`
declares exactly one client, `org.mewx.wenku8`, and the `com.google.gms.google-services` plugin
fails the build for any package name missing from it. Adding a suffix means adding a matching
client to the Firebase project first. Worth doing — until then, back up before testing on a
device that carries a real install.

**Then the manual pass, which nothing automated covers.** The reader flow has no test above the
storage layer — not in the JVM suite, not in the instrumented one — so Phase 2.1 is compiled and
unit-tested but has never been *run*. On a device:

1. A novel **not** in the bookshelf: open it, read a chapter. This is the path that had no
   cached index before Phase 2.1 and the one most likely to be wrong.
2. Next and previous chapter, including across a volume boundary.
3. The resume dialog ("jump to last read").
4. The vertical reader, via the engine picker.
5. A bookshelf novel with the network off.
6. Developer Options → **Don't keep activities**, while reading. This is what Phase 2.1 was for,
   and it is the same switch Phase 3 needs, so it is worth leaving on for a while.

A long series matters for 1 and 2: the crash Phase 2.1 removes was size-dependent, so a novel
with hundreds of chapters is the one that used to fail.

**Status: informally exercised, not formally passed — and it already paid for itself.** The
maintainer has used the build on a device and reports it behaving normally, which rules out an
obviously broken reader. It also turned up Phase 1 item 10: a broken download made the next
chapter permanently unreachable and reported a server error for a server that was never
contacted. That bug was years old, sat in both readers, and no amount of test-writing had found
it — which is the argument for doing the rest of this list rather than a reason to consider it
done. It is not the list above.

#### Exactly what to run, and why each is manual

Being precise about this matters, because **almost none of it is genuinely un-automatable.** It is
un-automated, which is a different claim. What is actually missing is listed after each case.

| # | Steps | Result | Why not automated |
|---|---|---|---|
| 1 | A novel with hundreds of chapters **not** in the bookshelf — browse to it without favouriting, open a chapter. | **Pass.** Loads normally. | Needs a launched Activity driven through the UI. No Espresso dependency, and no seam to serve fixture data instead of the live server. |
| 2 | Page forward and back between chapters within a volume. | **Pass**, and the volume-boundary half of this case was **wrong to ask for** — see below. | Same as 1. |
| 3 | Reopen a previously read novel and accept "jump to last read". | **Pass.** | Same as 1, plus it depends on persisted state from an earlier run. |
| 4 | Vertical reader. | **Not applicable** — see below. | — |
| 5 | Network off, open a downloaded bookshelf novel. | **Pass.** Reads from cache. Uncached chapters report `NETWORK_ERROR`, which is correct. | Needs airplane mode toggled around a UI flow. |
| 6 | Developer Options → **Don't keep activities** on, then read, leave to another screen, and return. | **Pass**, on the reading that the option was enabled — "no difference" is exactly the pass condition, since the Activity is destroyed and rebuilt invisibly. | **Now automated.** `ReaderRecreationTest` covers the recreation half with `ActivityScenario.recreate()` and no new dependency, as predicted. Full process death still needs more. |
| 7 | Interrupt a chapter download, then open that chapter. | **Pass.** Refetches and reads instead of reporting `SERVER_RETURN_NOTHING` and closing. Verifies item 10. | **Now covered at the decision level** by `ChapterContentLoaderTest` — an empty and a truncated download both fall through to the network. Only the end-to-end flow is still manual: nothing today can force a partial write. |

**Phase 2.1 is therefore verified on a device.** Its caveat — "compiled and unit-tested but never
run" — is discharged. Case 1 was the specific risk it carried, a novel that reaches a reader
without any bookshelf path having written its index, and it loads.

**Two of these cases were based on features that do not exist**, which is worth recording because
both assumptions came from reading the code rather than using it:

- **Cross-volume navigation is not implemented, by design.** `gotoNextPage` (`:888`) and
  `gotoPreviousPage` (`:926`) iterate `volumeList.chapterList` — the *current* volume only — and
  toast `已是最后一章` / `已是第一章` at the ends. That is correct behaviour, not a bug, and it is
  consistent with Phase 2.1 giving the reader a single `vid`. Asking for a boundary crossing was
  asking for a feature.
- **The vertical reader has no chapter navigation at all.** It holds no chapter list and has no
  next/previous path; it renders one `cid`. That is why Phase 2.1 could delete its volume extra
  outright rather than migrating it.

A long series still matters for case 1: the crash Phase 2.1 removed was size-dependent, so a novel
with hundreds of chapters is the one that used to fail.

**One thing case 5 exposes.** An uncached chapter offline surfaces as the bare string
`NETWORK_ERROR`, because `onPostExecute` toasts `result.toString()` and the codes have no
localized strings. The behaviour is right and the message is not — same defect that showed
`SERVER_RETURN_NOTHING` to a user, and it is now the most visible thing left in the reader.

**What would replace this list.** Two of the three are done. `ChapterContentLoader` covers the
decision behind 1, 4 and 7 on the JVM, and `ReaderRecreationTest` covers 6 on a device. What
remains is an Espresso dependency plus a stubbable `LightNetwork` — the largest piece, and the
only one that reaches the wiring rather than the decisions.

**Case 2 is the first one struck off.** `ChapterNavigator` now covers the volume-boundary
decision — both `已是最后一章` and `已是第一章`, plus a single-chapter volume, which is both ends
at once — as JVM tests that run in milliseconds. What stays manual is the wiring around it: that
the buttons are connected, the confirm dialog appears, and the Intent reopens the reader. The
decision is tested; the plumbing is not. Until then the table above is the only
verification these paths get, which is the strongest available argument for the standing priority
at the top of this document. The scenarios that matter most are the ones ordinary use is
least likely to hit by accident — a long series that was never added to the bookshelf (1),
a volume boundary (2), and *Don't keep activities* (6) — and the first of those is precisely the
case Phase 2.1 identified as the one that could break it. Treat the list as outstanding until
those three are deliberately walked through.

**What that machine unblocks beyond this.** Phase 2.2 onwards wants crash data and so is still
gated on a release. But Phase 3 (`onSaveInstanceState`) and Phase 4's OOM/bitmap question are
both device work that no amount of CI substitutes for, and "Don't keep activities" is the tool
for the first of them.

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

   The scan is gone entirely, including as a fallback. It did do one thing better —
   `XmlPullParser` stops at the first byte of anything that is not the document, so a response
   that is well-formed only after some leading noise (a PHP notice, a relay banner) parses to
   nothing, while the scan read straight past it — and the first version of this change kept it
   for that case alone. Review caught the flaw: a fallback that returns the scan's output
   reintroduces both the phantom novels and the shift, on exactly the responses nobody can
   observe. The recovery is a **retry from the document start** instead, so the leading-noise
   path reads attributes by name like every other path, and there is one parsing implementation
   rather than two.

   The retry records a breadcrumb when it fires, because there is no way to tell from here
   whether leading noise is real. **Delete the retry and `indexOfDocumentStart` if a release
   goes by without that breadcrumb appearing** — the same measure-then-act shape Phase 0 used
   for `loadStream`, applied to a tolerance nobody can currently justify or refute.

9. **`loadLocalBookShelf` threw on a corrupt bookshelf file — fixed.** Found by writing
   `LocalBookshelfTest`, and the same shape as items 7 and 8 one layer down: a reader that
   assumes its input is well-formed because it usually is.

   The local bookshelf is one line of `aid||aid||aid`. `GlobalConfig.loadLocalBookShelf` split it
   and called `Integer.valueOf` on every token with no `try`/`catch`, so a single non-numeric
   token threw `NumberFormatException` out of the method. Empty tokens were already skipped, so a
   trailing or doubled separator was survivable — it was specifically a corrupt token that was
   fatal.

   What made it worth fixing rather than noting: nothing called it defensively.
   `getLocalBookshelfList` and `testInLocalBookshelf` both invoke it lazily on first use, with no
   catch anywhere up the stack, and the bookshelf tab is the app's home screen. So a bookshelf
   file damaged by a partial write — root cause 4's territory, and this file is rewritten on
   every add and remove — took out the screen that reads it, on launch, permanently, with no way
   for the user to recover except clearing app data.

   The fix matches item 7: the token that does not parse is dropped rather than the file being
   rejected, and a non-fatal is recorded so the frequency stays visible. Rejecting the whole file
   would silently empty a user's bookshelf, which is a worse outcome than losing the one novel
   whose id was corrupted. Note the trade this makes explicit: the failure was loud and is now
   quiet, which is why it reports rather than just swallowing.

   `LocalBookshelfTest` covers it from both directions — that the entries either side of a corrupt
   token survive, that a wholly corrupt file yields an empty shelf rather than a crash, and that
   a later rewrite does not resurrect the dropped entry or carry the bad token forward. The
   characterization test that pinned the old behaviour was inverted when the fix landed, which is
   what the testing strategy below means by capturing current behaviour *before* changing it.

   `moveBookToTheTopOfBookshelf` was fixed in the same change: it dereferenced the `bookshelf`
   static with no null check, unlike `addToLocalBookshelf`, `removeFromLocalBookshelf`,
   `getLocalBookshelfList` and `testInLocalBookshelf`, which all lazily load first. It was only
   reachable after something else had loaded the shelf, so it was latent rather than live — but
   it is root cause 2 in miniature, and one call from a new screen would have made it real.

   **A trap found alongside these, still present and worth knowing.** `getDefaultStoragePath()`
   returns the *external* root (`/storage/emulated/0/wenku8/`) on a current device, which is
   unwritable on API 29+, so every save silently falls through to the internal backup root.
   Everything works, but the names are backwards from what actually happens, and a test that
   writes to "the default path" writes somewhere the app never reads. It cost `LocalBookshelfTest`
   a full debug cycle — nine of its ten tests failed with `ENOENT` — and the fix was to mirror the
   app's own fallback rather than trust the name. Any future test that arranges save-folder state
   has to do the same.

10. **A broken download made the next chapter unreachable, permanently — fixed.** Reported from
    manual use, which is worth recording on its own: this is root cause 4's most user-visible
    consequence, it had been in both readers for years, and no amount of test-writing had found
    it. The manual pass earns its place in this document.

    Both readers built the network request unconditionally but then ignored it when the novel came
    from the bookshelf: `if (from.equals(FromLocal)) xml = loadFullFileFromSaveFolder(...)`, with
    an `// or exist` comment marking the gap the original author already knew about. A chapter
    whose download was interrupted — or truncated by the short read in root cause 4 — leaves an
    empty or unparseable file, and the consequences were bad in three separate ways:

    - **The error blamed the wrong party.** An empty cached file produced
      `SERVER_RETURN_NOTHING`, for a server the app had not contacted. Anyone triaging that
      report, in Crashlytics or from a user, would have gone looking at the API.
    - **The reader closed itself.** `onPostExecute` toasts and calls `finish()`, so the failure
      presented as the app ejecting the user out of the book.
    - **It never recovered.** Nothing deleted or refetched the bad file, so every subsequent
      attempt at that chapter failed identically. A single interrupted download made one chapter
      of a bookshelf novel permanently unreachable, and reading forward through the novel hit it
      every time.

    The cache is now preferred but not required: an unusable cached chapter falls through to the
    network fetch that was already prepared, and `SERVER_RETURN_NOTHING` is reachable only when
    the server really did return nothing. A non-fatal is recorded when the fallback fires, because
    the refetch would otherwise hide how often downloads produce unusable files — the same
    measure-while-fixing shape as item 9.

    **Now covered.** This originally read "not covered by any test, and honestly not coverable as
    written" — both bodies were `doInBackground` inside an Activity inner class calling static
    storage and network helpers, with no seam to inject through, so the fix shipped on the
    strength of one manual scenario. `ChapterContentLoader` closed that: the empty-cache and
    truncated-cache fallthroughs are now JVM tests, including the case where the refetch itself
    fails, and the two causes are reported distinctly so they stay separable in Crashlytics.

    Worth noting how the order came out. The plan's own rule says seams should fall out of
    refactors rather than precede them, and this one did the opposite — the fix went first,
    unverified, and the seam was cut afterwards to cover it. That worked here because the
    extraction was behaviour-preserving and the fix was small, but it is the more expensive
    order: the fix spent a release unverified, and the manual pass that stood in for tests had to
    be run by hand.

    Two things deliberately left alone. A successful refetch is **not** written back to the cache,
    so a broken chapter refetches on every visit rather than repairing itself — correct but
    wasteful, and adding a write here means adding a write failure path to the reader's startup.
    And `onPostExecute` still toasts `result.toString()`, i.e. the raw enum name, which is how
    `SERVER_RETURN_NOTHING` reached a user as those words; the codes have no localized strings.

11. **`LightCache.saveFile` destroys the old file before writing the new one — KNOWN, DELIBERATELY
    NOT FIXED.** Item 10 fixed the reader's response to a corrupt cached chapter. This is the
    thing that *creates* one, and it is being left alone on purpose.

    The write opens a `FileOutputStream` directly on the destination, which truncates it on open.
    From that instant until the last byte lands there is no good copy of the file anywhere: an
    interrupted write — killed process, full disk, `IOException` partway through — leaves an empty
    or half-written file where working content had been. That is root cause 4's other half. Root
    cause 4 as originally written was about *reading* fewer bytes than expected; this is the
    writer manufacturing the same corruption directly, and it is the mechanism behind item 10's
    permanently unreadable chapter. Two smaller defects sit in the same eight lines: no `sync()`,
    so bytes can be in the page cache when the app reports success; and closes on the success path
    rather than in a `finally`, so a throwing `write()` leaks the descriptor.

    **A temp-file-and-rename fix was written, tested on a device, and then reverted.** It worked,
    but it is the wrong shape of change for this subsystem, and the reasoning generalises:

    - It is a partial fix to a component scheduled for replacement, so the work is thrown away
      twice — once when the store changes, and once more by anyone who has to reason about why
      `LightCache` had two write strategies in its history.
    - It carried its own new risk. An orphaned `.tmp` after a process death would be picked up by
      `LightCache.listAllFilesInDirectory`, which feeds the background selector — a new defect
      introduced into a system about to be rewritten.
    - Its tests could not prove the benefit. All four passed against the *old* implementation too,
      because that one never created a temp file, so they were regression protection for the new
      mechanism rather than evidence it was better.

    **The standing decision: today's storage logic is not to be touched incrementally.** Either it
    is replaced with a real structure, or it is left exactly as it is. Patching it in place buys
    small correctness gains at the cost of churn in the one part of the codebase where churn is
    most expensive, and where the plan's own thesis — fix generators, not symptoms — argues hardest
    against it. The defect above is therefore a *known accepted risk* until the migration lands,
    not an oversight. See "Should we move to platform storage?" below, and the separate storage
    migration plan for what replacing it involves.

12. **The cache is only consulted when the novel came from the bookshelf — PROPOSED, deferred.**
    Found by exercising item 10's fix on a device. Item 10 made an unusable cached chapter fall
    through to the network; this is the opposite gap, and it costs performance rather than
    correctness.

    Only `"fav"` takes the cached path. The launch sites send four different values —
    `FavFragment:116` sends `"fav"`, `NovelItemListFragment:178` sends `"list"`,
    `LatestFragment:171` sends `"latest"`, and `NovelInfoActivity:574` sends `"cloud"` — so a
    novel that is fully downloaded is re-fetched chapter by chapter whenever it is opened from
    search, a list, the latest feed, or cloud sync. `NovelInfoActivity:574` sends `"cloud"` even
    for a novel that *is* in the bookshelf, so the same book read by two routes behaves
    differently.

    **This applies to chapter content only, and the distinction is the whole design.** Content and
    metadata want opposite caching rules:

    - **Chapter text is effectively immutable** once published. A local copy is as good as a fresh
      fetch, so it should be preferred whenever it exists, regardless of entry point. That is the
      change proposed here, and it is what the `// or exist` comment meant.
    - **Metadata changes** — new chapters get published, titles get corrected — so it *should*
      follow whether this is a fresh read or a local copy, which is what it already does.
      `NovelInfoActivity` gates its info fetch on `fromLocal` (`:588`–`:628`) and rewrites the
      cached index whenever a fresh one arrives from the network (`:680`). The reader then reads
      that cache unconditionally (`Wenku8ReaderActivityV1:109`, `loadCachedVolume`), which is
      correct precisely *because* the freshness decision was already made upstream.

    So the fix is narrow: widen the condition at the content load from `from.equals(FromLocal)` to
    "a usable cached copy is present", making `from` irrelevant to **content** while leaving every
    metadata path untouched. Since item 10 the network fallthrough already exists, so this is a
    condition change rather than new machinery.

    **No longer blocked.** This waited on a seam, and `ChapterContentLoader` is that seam: the
    decision is now a parameter (`preferCache`) with the I/O injected, and the four-case table —
    cache good, cache empty, cache corrupt, no cache — is already written and passing on the JVM.

    What remains is one line at each of the two call sites: the readers pass
    `from.equals(FromLocal)` today, and the change is to pass `true` unconditionally so a usable
    downloaded copy is preferred whichever way the reader was opened. The fallthrough it depends
    on already exists, and the tests that would catch a mistake in it already run. Left for v1.31
    because it is a behaviour change rather than coverage work, so it should ship on its own
    rather than riding along with the refactor that made it safe.

13. **The V0 reading-position loader crashed the vertical reader on a corrupt file — fixed.**
    Item 9 again, in a second location, and found the same way: by covering the code rather than
    by reading it. `loadReadSaves` validated the field *count* of each record and then called
    `Integer.valueOf` on all three fields regardless, while `loadReadSavesV1` — doing the identical
    job fifty lines below — checks every field with `LightTool.isInteger` before parsing and skips
    the record if any fails. The older loader never got that treatment.

    **Reachable and fatal, on both directions of travel.** `VerticalReaderActivity` is the one
    reader still on this format: it reads the position from a scroll-restore runnable at `:66` and
    from an unguarded `onPostExecute` at `:363`, and writes it from `onPause` at `:381`. None of
    those catch, and every one of them reaches `loadReadSaves` lazily. So a single damaged record
    crashed that reader when opening a chapter *and* again when leaving one, permanently, with
    nothing in the app that lets a user delete the file to escape it. Item 11's truncating writer
    is the thing that produces such a record, and `onPause` is exactly when a write gets
    interrupted.

    The fix is item 9's: drop the record that cannot be parsed, keep every other chapter's
    position, and report the drop so the real frequency is visible now that it fails silently
    rather than loudly. `ReadSavesV0Test` covers it, and the failing case was written first — it
    reproduced as `NumberFormatException: For input string: "not-a-position"` before the fix and
    passes after, so the crash is evidence rather than inference.

    **Worth generalising.** Two of this document's crash findings are now the same defect: a parser
    that validates shape but not content, sitting under a lazy call reached from a screen with no
    catch above it. `loadAllSetting` splits on `::::` and stores whatever it finds without parsing,
    so it is safe by luck rather than design; it is also the last uncovered loader in the group.

Expected outcome: this should remove the majority of crash *volume* without touching
architecture. Phase 0's data will confirm — and because Phase 0 shipped first, the before/after
comparison is actually available this time.

### Phase 2 — Remove the generators (the actual refactor)

Only worth doing once Phase 1 has landed and the crash rate has visibly dropped, so the
effect of each change is measurable.

1. **Pass IDs, not objects, through Intents — implemented and device-verified.** The
   `VolumeList` extra is gone: the readers take `aid` + `vid` and rebuild the volume from the
   cached novel index. Kills `TransactionTooLargeException` permanently and makes the reader
   survive process death for free, since ints in an Intent always restore.

   **The cache assumption did not hold, and a correction to an earlier note here.** That note
   said `intro/<aid>-volume.xml` was written "only by `AsyncUpdateCacheTask`, the explicit
   download action". Wrong — there are three writers: adding a novel to the bookshelf
   (`NovelInfoActivity:299`), the download/refresh task (`:958`), and bookshelf cloud sync
   (`FavFragment:421`). All three are bookshelf paths, so the file is present for anything in
   the user's bookshelf, which is most of what anyone reads. The real gap was narrower than
   stated: a novel *browsed and opened* from a list or search without ever being favourited.
   Narrower, but still fatal to this item, since that novel can reach a reader.

   `FetchInfoAsyncTask` now writes the index whenever one arrives from the network, after the
   parse rather than straight off the wire so an unparseable response cannot overwrite a good
   cached index with a broken one. That also refreshes a stale bookshelf copy, which previously
   only happened on explicit sync or download.

   The rest of the item:

   - `GlobalConfig.cacheVolumeIndex` / `loadCachedVolume` own the file, so the `"intro"` folder
     and `<aid>-volume.xml` filename stop being spelled out at each call site.
   - `Wenku8Parser.findVolumeByVid` is the lookup, and is pure logic with JVM tests. It skips
     null entries: `getVolumeList` appends on the closing tag, so a stray closing tag with no
     opening one leaves a null in the list, and the lookup must not be what turns a malformed
     index into a crash.
   - **`VolumeList` and `ChapterInfo` are no longer `Serializable`.** Nothing else serialized
     them. Dropping the interface is what makes this permanent rather than a convention — the
     shortcut is now a compile error, not a code review.
   - `VerticalReaderActivity` takes no volume at all. Phase 1 found it read the extra and never
     dereferenced it; the field and its Phase 1 breadcrumb are deleted rather than migrated.

   **One case this makes worse, deliberately.** A device whose storage cannot be written could
   previously still read a novel, because the volume travelled in the Intent. Now the reader
   cannot open at all. Every other write in the app fails silently the same way, so a failed
   cache write is recorded — without it there would be no way to tell that story apart from a
   reader that simply refuses to open. The alternative fixes are worse: re-fetching the index on
   a cache miss puts a network round trip in front of the reader's startup, and an in-memory
   handoff would be root cause 2 all over again.

   **What is tested, and what is not.** `findVolumeByVid` is pure and has JVM tests, and the
   cache either side of it now has device coverage — `VolumeIndexCacheTest`, 7 tests, written
   once the two storage bugs that were blocking it were fixed. It covers the round trip through
   a real filesystem, which is the part the JVM cannot check: that a written index reparses with
   its CJK volume and chapter names intact, that re-caching replaces the previous index rather
   than leaving a longer one behind it, and that every way the file can be absent, empty,
   truncated or unparseable yields null rather than an exception. That last group is the point —
   null is the documented ordinary outcome so the reader can show a message, and a throw would
   be a crash on the path that opens a chapter.

   **The reader flow above the cache still has no automated coverage at all.** It has now been
   walked manually instead — see the case table under "Picking this up on a machine with an SDK",
   all seven of which pass. Case 1 is the one that mattered here: a long novel opened without ever
   being favourited, which is exactly the path this item put at risk, since no bookshelf writer
   would have cached its index. It loads.

   So Phase 2.1 is verified, but by a method that does not repeat itself. Every future change to
   this path costs another manual pass until the reader has a seam — which is why that seam is the
   first item under the standing coverage priority at the top of this document.

   **Partly discharged since.** `ChapterNavigator` took the chapter-to-chapter navigation out of
   the Activity, so the boundary behaviour this item depends on — that a volume's last chapter
   has nothing after it — is now a JVM test rather than a tap on a device. The load-a-chapter
   decision is still manual-only, so the paragraph above stands for that half.
2. **Retire `AsyncTask`.** Do not rewrite all 24 at once. Introduce one small helper
   (`ExecutorService` + main-thread `Handler`, or `androidx.lifecycle` if you are open to
   adding it) and migrate screen by screen, starting with whichever Phase 0 shows is
   crashiest — likely `NovelInfoActivity` (4 tasks) or `FavFragment` (2 tasks + a 3-thread pool).
   Each migration is independently shippable.

   **Considered on 2026-08-22 and deliberately deferred: writing the tests first, then migrating
   behind them.** The proposal was to characterise each screen's async behaviour, swap the
   mechanism, and require the tests to stay green throughout. That is the right shape for this
   refactor and remains the intended route — it was judged too risky to *start* with a release
   pending, and coverage work was chosen over it. The analysis is recorded here so the next
   attempt begins from this point rather than re-deriving it:

   - **The tests must be mechanism-agnostic or they are worthless as a safety net.** Anything
     reaching for `ShadowAsyncTask`, calling `execute()` directly, or flushing AsyncTask's
     particular scheduler breaks the moment a screen migrates — which would mean editing tests
     and production code in the same commit, precisely the situation the tests exist to prevent.
     They must drive public entry points only (`onCreate`, `onResume`, a click, `onDestroy`),
     assert observable outcomes (did not crash, list populated, file written, dialog dismissed),
     and advance time through one shared helper, so a mechanism change lands in a single place.
     The proof that this was done right is that the migration commit needs *no* test edits; if it
     does, the remaining migrations are unprotected.
   - **`AsyncTaskTracker`'s generic bound is the coupling point,** and it had not been examined.
     `track(T extends AsyncTask<?, ?, ?>)` forces every migration site to change its tracker call,
     and it blocks the screen-by-screen approach this item asks for, because a half-migrated app
     needs both mechanisms tracked at once. Generalising it to a small `Cancellable` interface is
     small, safe and independently shippable, and should land before any screen moves.
   - **It depends on Robolectric being able to drive Activities and Fragments in this project,
     which is unproven.** `unitTests.includeAndroidResources` is not set, and these screens touch
     Firebase, ads and `api/` during startup. That has to be spiked before the approach can be
     costed at all.

   One number in this item is worth correcting while it is open: counting `onPostExecute` with
   grep double-counts, because each override also calls `super.onPostExecute(...)`. A raw count
   of 10 in `NovelInfoActivity` is 5 tasks, which agrees with the figure in Phase 1 item 5.
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
| `app/src/test` | 3 files / 10 tests | **16 files / 133 tests** | JVM, seconds |
| `app/src/androidTest` | 8 files / 31 tests | **23 files / 174 tests** | emulator or device, minutes |
| `api/src/test` | 1 file | 1 file | JVM |

(Step 1 moved the JVM count from 10 to 37; `CrashReporterTest` took it to 44 in Phase 0; Phase 1
added the rest, mostly `LightCacheStreamTest` and `AsyncTaskTrackerTest`; `ChapterNavigatorTest`
took it from 78 to 90 when the reader's chapter navigation gained a seam, and
`ChapterContentLoaderTest` to 99 when the cache-or-network decision gained one, and
`AccountInfoLoaderTest` to 114 when the account screen's load decision did.)

**Activity-level coverage was impossible on CI until recently, and not for a reason anyone would
guess.** `BaseMaterialActivity.onResume` samples `LightUserSession.getLogStatus()`, and
`MainActivity.onCreate` constructs `LightUserSession.AsyncInitUserInfo`. Both threw on a build
against `api-stub` — which is the only thing CI builds — so **no Activity in this project could
reach RESUMED there**, and any test that launched a screen crashed the instrumentation run rather
than failing. `ReaderRecreationTest` was the first to hit it. Once the stub answered instead of
throwing, the same tests became writable for the screens that matter most:

| Class | Covers |
|---|---|
| `ReaderRecreationTest` (6) | the reader across rebuilds, and the three ways its volume can be missing |
| `MainActivityLifecycleTest` (4) | the launcher: opening, rebuilding twice, backgrounding |
| `NovelInfoActivityLifecycleTest` (4) | the detail screen with nothing to show and no server |
| `UserLoginActivityLifecycleTest` (6) | the login form across rebuilds, and its two input guards |
| `UserInfoActivityLifecycleTest` (1) | the account screen starting up |

The launcher and detail tests are worth their runtime for a specific reason: those two screens do
the most work in `onCreate` and host the Fragments where, per the diagnosis above, most of the
lifecycle crashes actually live. The shared precondition check lives in `InteractiveDevice` rather
than being copied into each class — a dozing device makes all of them fail identically and
misleadingly, so it is worth having exactly one explanation of that.

Note what these deliberately do **not** assert: what a failed screen *displays*. Error text in
this app is inconsistent and partly untranslated, and freezing today's appearance into a test
would make fixing it harder. The contract they hold is structural — the Activity survives.

**The two account screens are uneven on purpose, and the imbalance marks the next seam to build.**
`UserLoginActivity` does nothing networked in `onCreate`, so it behaves the same on a stub build
and a real one and gets the full set. `UserInfoActivity` fires `AsyncGetUserInfo` immediately and
calls `finish()` on every failure, so on a stub build it is closing from the moment it opens:
`recreate()` and `moveToState(RESUMED)` both block until RESUMED, which an Activity that has
already finished never reaches, so they would not fail — they would hang for the full 45-second
timeout and report something that reads like a startup defect. Launching is the only move that
stays meaningful in both outcomes, and whether the screen *stays* open depends on a session and a
server, which differ between CI and a developer's device. Hence one test on the device.

**What that screen's logic needed was not a device test at all.** The interesting part of
`UserInfoActivity` was never its lifecycle — it was the decision tree inside
`AsyncGetUserInfo.doInBackground`: an optional daily sign-in, a fetch, a re-login-and-retry when
the server reports a lapsed session, and four distinct failures the screen maps differently. None
of it could be provoked on demand, because producing "the server says your session lapsed, then
accepts your stored credentials, then answers properly" requires a server that will do that on
cue. That decision now lives in `AccountInfoLoader` with its I/O injected, exactly as
`ChapterContentLoader` did for the reader, and is covered by 15 JVM tests that run in
milliseconds. The Activity keeps only what genuinely needs Android: decoding the avatar and
writing it to the disk cache.

One design point worth keeping, because it is the reason this seam works on CI at all:
`Wenku8Error.getSystemDefinedErrorCode` — turning a response integer into an `ErrorCode` — is
**injected rather than called**. It throws on `api-stub`, and it should: that mapping is wire
protocol belonging to the private `api/` module, and reproducing it in the public stub would
publish the thing the stub exists to keep out. Taking it as a dependency lets the decision be
tested under either configuration without either module knowing tests exist. Any future seam over
`api/` should do the same.

**No test signs in, and no test signs out.** A successful login has to reach the real wenku8
server, and an automated suite firing credentials at someone else's production service is not
worth the coverage. Logout is worse than useless to test: `AsyncLogout` deletes the stored account
and avatar files, so a test that reached it would destroy the credentials of whoever ran the suite
— the same landmine `AsyncInitUserInfo`'s failure callback carries, documented in the stub.

**The emulator flakiness was not theoretical, and CI has been restructured because of it.** The
run for commit `2525b3b` failed in the emulator step, and because that one step ran
`assembleAlpha testAlphaDebugUnitTest connectedAlphaDebugAndroidTest` together, the 44 JVM tests
never reported at all. Four changes to `.github/workflows/android-ci.yml`:

1. **Split into two jobs.** JVM tests no longer depend on an emulator booting. This is the whole
   payoff of having moved those tests off the device: an emulator that will not start now costs
   the 65 instrumented tests instead of all 179. The same split pays off again on a local device,
   where the emulator's flakiness is replaced by the install stall documented above.
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

**Coverage reporting is wired again, and it now measures all 307 tests rather than half of them.**
The README's Travis badge pointed at a service that no longer runs the build, and the Coveralls
badge was fed by a `kt3k` Gradle plugin hooked to `connectedAlphaDebugAndroidTest` that has been
commented out in `app/build.gradle` for years — neither could be revived as-is under AGP 9. The
build badge is now the GitHub Actions workflow, and coverage comes from two `JacocoReport` tasks,
uploaded by `coverallsapp/github-action`.

The first attempt covered the JVM suite only and reported about 5% of lines. That was honest but
badly misleading if read as "this app is 5% tested" — the JVM suite covers the pure-logic core,
while all the Activity, lifecycle and storage coverage lives in the instrumented suite. Measured
separately, the split is stark: **JVM 371/7010 lines (5.3%), instrumented 1875/7010 (26.7%).**
Reporting the first number alone understated the project by roughly a factor of five.

Both halves are now uploaded under Coveralls flags (`jvm` and `instrumented`) with `parallel: true`,
and a third job posts `parallel-finished` so the service publishes the union. Three things about
this are worth keeping in mind before anyone changes it:

- **Nothing is combined in Gradle.** Each job reports only its own suite; the merge is server-side.
  There is deliberately no combined Gradle task, so do not go looking for one.
- **Both reports must measure the same class set**, or the merged figure blends two different
  measurements and nobody can tell which half moved. That is why `applyCoverageScope` in
  `app/build.gradle` is shared rather than copied, and why AGP's own
  `createAlphaDebugAndroidTestCoverageReport` is *not* used: it is an AGP-internal
  `JacocoReportTask`, not a Gradle `JacocoReport`, so its excludes and source roots cannot be
  configured to match. Identical denominators (7010 lines, 19 packages) in both reports are the
  check that this still holds.
- **`carryforward: jvm,instrumented` on the finish job is load-bearing.** If the emulator job fails,
  only the `jvm` flag arrives, and without `carryforward` Coveralls would publish that alone — the
  figure would drop from the merged value to ~5% and read as a catastrophic coverage regression
  caused by whichever commit happened to be pushed.

`base-path: studio-android/LightNovelLibrary/app/src/main/java` is also required rather than
cosmetic. JaCoCo names files by package, so the XML says `org/mewx/wenku8/global/GlobalConfig.java`;
without the base path Coveralls cannot match a single file against the git tree.

`enableAndroidTestCoverage` instruments the app's classes, which was the risk in doing this: the
emulator job is the fragile one. It was gated on a local run before being pushed — all 105
instrumented tests pass with instrumentation active (18.2s), and `pm install` takes 7.3s. Neither is
close to the margin that broke this job before.

**Both coverage flags are scoped to the `debug` build type, and the release APK is provably
unaffected.** Measured on 2026-08-22, alpha flavour: debug **22.1 MB → 24.9 MB** once
`enableAndroidTestCoverage` is on, i.e. **+2.8 MB (+13%)**; release **10.8 MB** either way. Grepping
the packaged dex for `jacoco` gives **241 hits in debug and 0 in release**. (An earlier note in this
file put the debug baseline at "~20 MB" and the growth at 25%; that baseline was carried over from a
prior session rather than measured, and the figures here replace it.)

The flag is deliberately not hidden behind a CI-only property: that would mean CI building an
artifact nobody builds locally, which is exactly how the api-stub failures stayed invisible.

The uploads carry `fail-on-error: false` on purpose. Coverage is informational; a Coveralls outage
must not turn a green build red when the release is gated on tests passing. They deliberately do
*not* carry `continue-on-error` as well — together the two hid a genuinely broken upload behind a
green tick for two builds.

**Coverage audit, 2026-08-22.** Merged figure 34.5%, and the shape of what is left is more useful
than the number. The parser and API layer is effectively finished — `Wenku8Parser` 228/229,
`OldNovelContentParser` 65/66, `NovelItemInfoUpdate` 63/63, `ReviewList` 49/49, `UserInfo` 39/39,
`ChapterNavigator` 28/28, `CrashReporter` 41/41, `AccountInfoLoader` 40/40. **Not one of the twenty
largest uncovered classes is pure logic**, so there are no cheap JVM wins left; everything
remaining is Activity, Fragment or custom-view code.

Ranked by uncovered lines: `NovelInfoActivity` 644/817, `PagerSlidingTabStrip` 373 (vendored),
`Wenku8ReaderActivityV1` 314/493, `FavFragment` 275/280, `PageSlider` 217, `NovelItemListFragment`
206, `VerticalReaderActivity` 185, `NovelReviewReplyListActivity` 172, `OverlappedSlider` 169,
`ConfigFragment` 166/168, `MainActivity` 152/211, `GlobalConfig` 140/446.

**Re-measured 2026-08-23 on the physical device, all 150 instrumented tests plus the 133 JVM ones.
Two figures, and the gap between them is the point:**

| | lines | what it is |
|---|---|---|
| **local merged** | **3391/7029 — 48.2%** | every test, including the 25 `RealApi`-guarded ones |
| **CI merged** | **2614/7029 — 37.2%** | what Coveralls can publish; the guarded tests skip against api-stub |

The 11-point gap is roughly 780 lines of review, search, bookshelf and ranking screens that are
genuinely tested but only on a machine holding the private `api/` module. The published badge is a
floor, by decision — see `RealApi`. Do not quote the local figure as the project's coverage.

**Two measurement traps, both of which produced a confident wrong number before being caught.**

*One `.ec` for a whole run silently loses most of it.* Running the suite with a single
`-e coverageFile` produced a clean-looking 40.7% in which `NovelInfoActivity` showed **1 covered
line of 817** — while eleven passing tests assert on that screen's views — and `UserLoginActivity`
showed 0 of 68 against six passing tests. JaCoCo dumps its in-memory data once when instrumentation
ends, so everything accumulated before an app-process restart is gone, and several tests restart the
process by design (`recreate()`, backgrounding). This is why AGP writes one `.ec` per test class.
Run per class, one file each, and let the report task merge them.

*Per-class rows are still not individually reliable.* The merged totals above reproduce exactly
across forced clean regenerations, but single classes do not agree between methods —
`WenkuReaderPageView` reads 4/215 per-class and 109/215 from the single-file run. Treat the ranking
below as directional, and re-measure a specific class before committing effort to it.

Ranked by uncovered lines, 2026-08-23, approximate per the caveat above: `NovelInfoActivity`
565/817, `Wenku8ReaderActivityV1` 316/493, `PageSlider` 217 (vendored-ish custom animation),
`WenkuReaderPageView` 211/215, `FavFragment` 195/267, `OverlappedSlider` 169/192,
`PagerSlidingTabStrip` 153/373 (vendored), `MainActivity` 150/211, `ConfigFragment` 136/168,
`GlobalConfig` 123/446, `NavigationDrawerFragment` 120/208, `UserInfoActivity` 96/129.

**The older ranking below is kept for its notes, not its numbers, and goes stale silently — check
its date before trusting an entry.**
Several rows have since been closed and the numbers do not update themselves: `VerticalReaderActivity`
still reads 185 here and `ViewImageDetailActivity` 117, but both have had unguarded device tests
since this was written. Regenerating it needs a device or emulator, and the JaCoCo XML left in
`app/build/reports/` after a run is whatever that run covered — a partial run leaves a partial
report that looks exactly like a complete one. Note also that a merged figure cannot be obtained by
adding the two `<counter>` totals: a line covered by both suites would be counted twice, so a true
union has to be computed from the per-line `<line nr= mi= ci=/>` entries, which is what Coveralls
does server-side.

Two entries deserved naming. `FavFragment` is 275 uncovered of 280 and it is the bookshelf — the
first screen most users see, and it is now the largest untested thing in the app.

**Partly closed by extraction rather than by hosting the screen**, because hosting it performs a
real sync against the user's real account (see the false-guarantee note below). `BookshelfSync` now
holds the two pure pieces that were inline in `AsyncLoadAllFromCloud.doInBackground` — reading the
aid list out of the cloud response, and computing what to download against what to push up — and
`BookshelfSyncTest` covers them with 19 JVM tests. This is the same move that produced
`ChapterNavigator` and `ChapterContentLoader`, for the same reason: the logic was reachable only by
running the screen, and running the screen is the thing that carries the risk.

The case worth having is `aNovelOnlyOnTheDeviceIsPushedUpRatherThanDropped`. This document already
asserted that a device-only novel survives a sync; that claim rested on reading the code and
watching one sync by hand, and now has a test. Behaviour is unchanged, with one incidental
improvement: the original read `GlobalConfig.getLocalBookshelfList()` twice, once to build the
combined list and again to subtract it, so a shelf that changed between the two calls could produce
a plan matching neither state. It is one snapshot now.

**A parse that only worked when `aid` was the last quoted attribute on its line — now bounded, and
the reason is worth keeping.** The expression was `aid="(.*)"`. The capture is greedy and `.` does
not match a newline, so it runs to the last quote on the line: correct when nothing follows `aid`
there, wrong when anything does.

This was checked against the live server rather than argued from the regex, and the check is what
changed the decision. The endpoint the bookshelf actually calls, `action=bookcase&do=list`, returns
`<book aid="3988" />` one per line, and a real 66-entry shelf parsed **66 of 66 under either
form** — so the greedy version was never broken in production, and an earlier note here that
implied otherwise was wrong. But the sibling endpoint `action=bookcase` returns
`<book aid="3988" date="2026-08-23">`, and under the greedy form **none** of the same 66 entries
parse. Nothing calls it today: `Wenku8API.getBookshelfListParams` has no caller in the app.

Tightened to `aid="([^"]*)"` on that evidence. It is not a behaviour change on any path the app
takes — identical output on the endpoint in use, verified against a real response — and it removes
a trap that would have been reached by what looks like an optimisation, since the fuller listing
carries names and dates and would save one request per novel. The failure mode it prevents is the
bad kind: a silently empty listing, read as "the cloud has nothing", followed by a pointless
re-upload of the whole shelf. `BookshelfSyncTest` now covers both real response shapes.

The general lesson is the one worth carrying: the flaw was found by reading the code, but its
severity could only be settled by looking at what the server actually sends, and reading alone had
it pointing the wrong way.

**A defence that converts a missing argument into a crash one layer down.** Found while trying to
cover `NovelItemListFragment` and left unpatched on purpose. Its `onCreate` reads arguments
defensively — `listType = args == null ? "" : args.getString("type", "")` — which reads like a
Fragment that copes with having no arguments. It does not: `""` then reaches
`Wenku8API.getNovelSortedBy` inside `AsyncGetNovelItemList.doInBackground`, which throws
`IllegalStateException: Unknown NovelSortedBy:` from a background thread, so the app dies rather
than showing an empty list. The guard produces a value its own consumer rejects.

It is **not reachable today** — the framework retains `setArguments` across recreation, and both
callers (`RKListFragment`, `SearchResultActivity`) pass a real value — so under the standing
preference for coverage over logical patches it is recorded rather than fixed. Whoever does fix it
should make the empty type resolve to a default listing rather than adding a second guard further
down, since the existing one already reads as sufficient and is not.

Two other things came out of that same attempt. `NovelItemListFragment.onCreateView:142` resolves
its progress indicator with `getActivity().findViewById(R.id.spb)` — the *host Activity's* view
tree, not the `rootView` it just inflated — and `R.id.spb` exists in only two layouts. That is an
id-based coupling with no compile-time link, so a third host, or a layout that drops the indicator,
becomes an NPE with no warning. And the Fragment cannot be covered this way at all without a valid
`NovelSortedBy`, which comes from the private `api/` module that CI stubs; a test written around it
would pass locally and risk failing on CI, which is the api-stub trap. `ConfigFragmentHostingTest`
therefore covers settings only, and says so.

**Now covered, by `NovelItemListFragmentHostingTest` — 6 device tests.** The reasoning above was
right and is why the test took the shape it did: it passes a real list type instead of `""`, and
hosts a ranking list instead of `type=search`, so it constructs neither of the states that made the
first attempt crash. The api-stub objection is answered by `RealApi.require()`, which skips on CI
rather than dying there; it did not exist when that attempt was made. The listing genuinely loads —
the case that reaches `refreshPartialIdList` asserts a populated adapter and was confirmed not to
have been skipped by its `assumeTrue`.

**A third spelling trap the first attempt did not reach, and the sharpest of the three.** The two
API implementations disagree on how a list type is spelled. The real module maps
`NovelSortedBy.allVote` to `"allvote"` and back; `api-stub` implements the same pair as
`valueOf`/`name`, giving `"allVote"`. Each round-trips against itself, so `RKListFragment` works
under both — but a test with a literal `"allvote"` reaches `valueOf("allvote")` on CI and throws
`IllegalArgumentException` from a background thread, which takes the process rather than failing an
assertion. Derive the string through `Wenku8API` as the pager does; never write it out. This is the
same class of hazard as the api-stub trap but survives it: `RealApi.require()` would not have caught
this one, because the stub's `getNovelSortedBy` does not throw `UnsupportedOperationException`.

**A guarantee that was claimed and turned out to be false, kept here because it is the kind of
thing worth not repeating.** `FavFragmentHostingTest` originally documented that it did not modify
the device's bookshelf. It does. Hosting the Fragment runs its `onResume`, whose first pass always
takes the cloud branch, so a run on a logged-in device performs a real bookshelf sync — 50 entries
became 64 on the development device. There is no way to host the Fragment and avoid it, because the
branch is selected by a counter the Fragment owns.

It was allowed to stand rather than deleted, on two grounds that were checked rather than assumed:
`AsyncLoadAllFromCloud` unions the local and cloud lists instead of replacing one with the other,
so it cannot drop a device-only novel, and the result was verified to hold 64 unique ids with no
duplicates and no sentinel leakage. It is also precisely what the app does when a logged-in user
opens the bookshelf. The lesson is narrower than "do not test Fragments": **a test that hosts a real
screen inherits everything that screen does on startup, including its network writes**, so the
promise to make about such a test is what its effects are, not that it has none.

`VerticalReaderActivity` was the other, at 0/185, being the screen whose crash Phase 1 item 13
fixed — the loader beneath it was tested, the screen itself was not. **Closed:**
`VerticalReaderActivityLifecycleTest`, 6 device tests, covering startup from a cached chapter,
recreation once and twice, backgrounding (which is what runs `onPause`, where this reader writes
the position), a chapter with nothing cached, and a corrupt reading position.

That last one is worth reading before it is trusted, because it proves less than its name suggests.
`getReadSavesRecord` parses the file only while its static is still null, and that static outlives
any one test, so the test cannot rely on the Activity being what triggers the parse — it reloads
explicitly first. The parse guard stays owned by `ReadSavesV0Test`, which drives the loader
directly. What the screen-level test adds is the half the loader test cannot reach: that a damaged
record resolves to a position of 0 rather than to something the reader then tries to scroll to,
and that the screen opens normally with one present.

About **1000 lines will realistically never be covered** — the vendored `PagerSlidingTabStrip`
(373), the reader sliders `PageSlider`/`OverlappedSlider`/`SlidingLayout` (~540 lines of custom
animation), and three trivial adapters (~86). That is roughly 14 points of permanent ceiling, worth
knowing before anyone sets a coverage target. Excluding the vendored file alone is worth about
+1.9 points and is a one-line change to the exclusion list.

**A caution about how this ranking was produced, because the first attempt was wrong.** Ranking
crash risk by grepping for `isFinishing()`/`isDestroyed()`/`isAdded()` reported `ConfigFragment` and
the three review Activities as having no lifecycle guards at all. They are in fact guarded, via
`WeakReference` + a null check — an idiom that grep missed. Re-running with both patterns leaves
only three classes genuinely unguarded: `WenkuReaderPageView` (3 callbacks, 109/215 covered),
`NovelItemAdapterUpdate` (2, 52/109) and `UpdateNotificationMessage` (2). Check both idioms before
trusting any such ranking.

**The one test in `api/` does not run anywhere, and that is deliberate.** `api/src/test/.../
Wenku8APITest.java` fails when invoked on a machine that has the private submodule:
`testGetEncryptedMAP` calls through to `android.util.Base64.encodeToString`, and `api/build.gradle`
sets no `testOptions` at all, so the un-mocked framework call throws rather than returning a
default. It is the same trap `app` has, in reverse — `app` sets
`unitTests.returnDefaultValues = true`, `api` sets nothing.

Nobody noticed because CI cannot see it. CI has no submodule credentials, so it builds `api-stub`,
where the test does not exist; and its task list is `assembleAlpha testAlphaDebugUnitTest`, which
would not run `:api:` tests even if it could. So this test runs only for someone holding both the
submodule and the intent to invoke it.

**It is being left alone on purpose.** `api/` is a separate private repository, and CI must not
depend on its implementation — that is the whole reason the stub fallback exists. Fixing the test
means committing to another repo and would pull the public build toward needing the private one.
Recorded here so the gap is known rather than rediscovered.

For the record, `returnDefaultValues = true` would be the *wrong* fix if it is ever revisited:
`Base64.encodeToString` would return `null` and the test would assert against a vacuous result —
green while checking nothing, exactly the failure mode step 1 above describes. Robolectric is the
answer there, as it was for the XML parsers.

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

**A test used to break storage for every test that ran after it.** Worth knowing before writing
any instrumented test that touches saved files, because the symptom appears in the wrong place
entirely — the failing test is fine, and something that ran before it is not.

`MyApp.context` is a process-wide static. `MyAppTest` sets it to a Mockito mock in one method
and to `null` in the other, and used to leave it that way. `SaveFileMigration.getInternalSavePath`
then asked `MyApp` for a context, got the mock, called `getFilesDir()` on it — `null`, as Mockito
returns for an unstubbed method — and built the literal string `"null/"`, which it cached in a
static for the rest of the process. Every save after that went to a relative `null/...` path that
cannot be created, and the external fallback is unwritable on API 29+, so writes simply failed.

Fixed at both ends, because either alone leaves a hole. `MyAppTest` restores the real application
context in `@After`. `getInternalSavePath` no longer caches a path it could not resolve: it
returns `""` for that call and tries again next time. The second half matters beyond the tests —
a transient null context during startup would have had the same permanent effect on a real
device, silently, and nothing would have reported it. `SaveFileMigrationTest` pins the invariant
that the path really is under the app's files dir, and fails loudly if the ordering bug returns.

This is root cause 2 reaching the test suite, and a reminder that a static cache holding a value
derived from something that might not be ready yet is a trap wherever it appears.

---

## Suggested sequencing

| Ship | Contents | Risk |
|---|---|---|
| — | ~~Chapter-navigation seam~~ **done** (`ChapterNavigator`, 12 JVM tests) | shipped |
| — | ~~Load-a-chapter seam~~ **done** (`ChapterContentLoader`, 9 JVM tests, both readers) | shipped |
| — | ~~`ActivityScenario` recreation tests~~ **done** (`ReaderRecreationTest`, 6 device tests) | shipped |
| v1.30.0 | Phase 0 instrumentation + Phase 1 items 1–10 + Phase 2.1 | low, CI-green, device-tested, manual pass complete |
| v1.31 | Phase 1 item 12 (prefer cached content — **now unblocked**, one line per reader), then highest-crash screen from 2.2 | medium |
| v1.32+ | Remainder of Phase 2, Phase 3, storage migration step 0 | medium |

**The release now waits on coverage, by decision rather than by drift.** The original plan shipped
Phase 0 alone and immediately, on the reasoning that crash data should arrive before anything else
was ranked. That has been overtaken: Phases 1 and 2.1 are already implemented, CI-green and
device-tested, so v1.30.0 is no longer "instrumentation only" and there is nothing to gain by
splitting it. Meanwhile this session demonstrated twice that tests and use find defects that
reading does not — so coverage first is expected to catch issues *before* Crashlytics has to,
which is strictly cheaper than learning them from users. It also makes the storage migration
safer, since that work needs a regression net more than anything else in either document.

The cost is real and should be named: Phase 0 collects nothing while this work happens, so the
ranking below 2.1 stays inference for longer. That is now a considered trade rather than an
oversight.

The important structural point: **Phase 0 before Phase 1**. Without crash data the ranking
above is inference from reading code, and inference will misallocate effort.

---

## Should we move to platform storage? — proposed, not scheduled

Everything the app persists is hand-rolled: novel indexes and chapters as XML files under two
possible storage roots, the bookshelf as one line of `aid||aid||aid`, settings as a
`ContentValues` blob, and `LightCache` doing the byte-level I/O. The question is whether to
replace that with Room/SQLite plus DataStore.

**Partly yes — and the split is the whole answer.** "Migrate everything" and "migrate nothing"
are both worse than the line between structured records and bulk text.

### The case for moving the structured data

This is not modernisation for its own sake. Three of this document's findings are *generated* by
hand-rolled storage, and a database removes the generator rather than patching the symptom:

- **Root cause 4** exists because buffer sizing and short reads are hand-managed. SQLite does not
  have that failure mode.
- **Phase 1 item 9** — the bookshelf crash — existed only because a list of integers was stored as
  text that something had to re-parse. A table of ids cannot produce `NumberFormatException` on
  read.
- **Phase 1 item 10** — the unreachable chapter — is a non-atomic write surfacing later as a
  corrupt read. Transactions make a half-written record impossible rather than recoverable.

There is a testing argument too, and for this codebase it may be the stronger one. Room runs
against an in-memory database on the JVM, so storage logic that today can only be tested on a
device — everything this session added under `androidTest` — would move into the fast suite.
Migrations are versioned and independently testable, which is more than the current
`SaveFileMigration` can offer.

### The case against moving the chapter text

Chapter content is bulk text, sometimes large, written once and read sequentially. That is what
filesystems are for, and a database buys little for it beyond a transactional write — which can
be had far more cheaply by writing to a temporary file and renaming it into place. **Rename is
atomic on the same filesystem**, so a half-finished download cannot be observed as a valid file;
that is most of item 10's class for a fraction of the effort and risk.

The natural shape is therefore the conventional one: **records in the database, blobs on disk,
with the database holding the path and a validity marker.** A chapter is only readable once its
row says the file is complete.

### What makes this genuinely hard, and it is not the code

The migration is the project; the schema is the easy part. Twelve years of installed devices hold
data in the current format, across two storage roots, with `SaveFileMigration` already performing
one external-to-internal move. A migration that drops someone's bookshelf is worse than every bug
in this document combined — those are recoverable, and lost data is not.

Constraints any implementation should be held to:

1. **Never delete the old files in the same release that stops reading them.** Migrate, then run
   on the new store while the old one remains untouched, and reclaim the space a release later.
2. **The migration must be idempotent and resumable.** It will be interrupted — that is precisely
   the failure this document keeps finding — so a half-finished migration has to be safe to run
   again rather than something that needs detecting.
3. **Report on it.** A migration that silently drops entries is the worst possible version of
   root cause 4. Counts in, counts out, and a non-fatal on any mismatch, in the shape Phase 0
   established.
4. **Test it against real corrupt inputs**, not just clean ones — a truncated index, a bad
   bookshelf token, a partially downloaded chapter. Those all exist on real devices *today*, so
   the migration meets them on day one. The device tests written this session are the fixtures.

### The actual blocker, measured

An earlier draft of this section said the migration should wait for Phase 0's crash data. **That
was the wrong precondition and is withdrawn.** Crash counts answer "which screen is crashiest",
which is what Phase 2.2 needs. They are not needed here, because the evidence that storage is
defective is already in hand and did not come from crash data at all: root cause 4, Phase 1 items
9, 10 and 11, the `getInternalSavePath` caching bug, and the external-root trap. Six concrete
defects, all found by reading and testing. Nothing is waiting to be learned about *whether*
storage is a problem.

The real precondition is containment, and measuring it gives a discouraging number:

| | count |
|---|---|
| `LightCache.*` call sites in `app/` | ~104 |
| files touching `LightCache` directly, outside `GlobalConfig`/`SaveFileMigration` | 13 |
| worst single file (`NovelInfoActivity`) | 27 |

`GlobalConfig` looks like a storage facade and is not one. Thirteen files reach past it into
`LightCache` directly, so there is no single place a new backend could be installed. **Swapping
the store today means editing every one of those call sites while simultaneously changing the
storage format — a large, untestable change touching user data, which is the worst shape a change
can have.**

So the first step is not Room. It is making `GlobalConfig` genuinely the only door: move those 13
files onto its API, leaving `LightCache` an implementation detail. That is mechanical, individually
shippable, verifiable by the tests that already exist, and useful on its own — it is also what
Phase 2.4 wants. Only once the door is single does replacing what is behind it become a contained
change.

### Sequencing

0. **Funnel all storage access through `GlobalConfig`.** The 13 files above, one at a time. No
   behaviour change, no format change, no migration. This is the prerequisite everything else
   depends on, and the step most likely to be skipped.
1. **Atomic writes for chapter files.** Deliberately *not* done as a standalone patch — see Phase
   1 item 11 for why that was tried and reverted. It arrives as part of step 5, where the row
   recording a file's completeness and the atomic write that justifies it land together.
2. **Settings to DataStore.** Smallest surface, no relational structure, easiest migration to
   verify, and a genuine test of the migration machinery on data whose loss is an annoyance rather
   than a disaster.
3. **Bookshelf and reading positions to Room.** The highest-value records: small, structured,
   frequently written, and the source of items 9 and 10.
4. **Volume index to Room.** Larger and more relational; benefits most from the schema.
5. **Chapter text stays on disk**, gaining a row that records whether the file is complete. Item
   11 already supplied the atomic write this depends on.

Each step ships independently, and stalling after any of them still leaves the app better off —
the property this document has asked for throughout, and the reason step 0 is worth doing even if
steps 2–5 never happen.

**Still not scheduled, but no longer blocked on evidence.** What it is blocked on is step 0, which
can start whenever there is appetite, is low-risk, and needs no decision about databases at all.

## Making `GlobalConfig` maintainable — accepted 2026-08-23, in progress

The section above asks what should sit *behind* the storage door. This one asks what the door
itself should be, which is a separate problem that had not been written down: `GlobalConfig` is
**996 lines, 79 public static methods, ~10 mutable static fields, 24 caller files**, holding
twelve unrelated responsibilities — storage paths, file I/O, settings, search history, reading
positions, bookshelf, volume cache, images, credentials, the cached notice, transient UI flags,
and assorted helpers.

Three costs, none of them aesthetic:

1. **Static mutable state with no lifecycle.** `loadAllSetting()` assigns `lookupInternalStorageOnly`
   (line 829), which decides the storage root at line 197 — process-wide. One test calling it
   changes how every later test in the same process resolves paths.
2. **No storage-root seam.** Every storage test writes to the real save folder.
   `VerticalReaderActivityLifecycleTest` has to read the device owner's actual `read_saves.wk8`,
   hold it in a field and put it back in `@After`, because there is nowhere else to point the
   code. That is a standing tax on every storage test and a standing risk to real data.
3. **Unrelated things sharing a namespace.** `isInBookshelf`, a transient screen flag, sits beside
   `cert.wk8` credential handling.

**Approach: incremental extraction behind a delegating facade**, rejecting both a big-bang
Repository/DI/Room rewrite and doing nothing. Each step moves one responsibility into a focused
class and leaves `GlobalConfig` delegating, so **none of the 24 callers change**. The property
that matters is that it degrades well: stopping after two extractions leaves the codebase better,
not carrying two half-architectures. This is the same move already proven three times here —
`ChapterNavigator`, `ChapterContentLoader`, `BookshelfSync`.

**Two axes, and the order between them was initially proposed wrong.** Funnelling (step 0 above)
concerns the *external* surface; extraction concerns *internal* structure. They compose, but
funnelling comes first for a reason the section above does not state outright: **it reveals the
API the extracted classes actually need.** Designing `SettingsStore` before the thirteen files are
on `GlobalConfig`'s API means designing it blind and reopening it afterwards.

| # | step | axis | status |
|---|---|---|---|
| 1 | Cover the 123 untested `GlobalConfig` lines — settings writers, credentials, image cache | test | **done** 2026-08-23 — `GlobalConfigSettingsTest` (14) + `GlobalConfigMiscTest` (10), +57 lines, 323→380 of 446. The 66 left are credentials, the network download, one dead method, and second-root fallbacks needing step 2 |
| 2 | Extract `StorageRoots` with an injectable root | split | not started |
| 3 | Funnel the 13 `LightCache` callers onto `GlobalConfig` (step 0 above) | funnel | not started — 13 files, 27 in `NovelInfoActivity`, re-measured 2026-08-23 |
| 4 | `SettingsStore`, then `AccountStore`, then the already-covered stores | split | not started |
| 5 | Evict the transient UI flags | split | not started |
| — | Backend swap, steps 1–5 of the section above | backend | not started, unscheduled |

Step 2 deliberately precedes step 3 despite the ordering argument, as a narrow exception: it is
~40 lines, changes no public API, and makes every later test both safer and cheaper to write, so
it de-risks the funnelling rather than competing with it.

**What step 1 could not reach, which turns out to be the argument for step 2.** Of the 96 lines
still uncovered after `GlobalConfigSettingsTest`, only about a third are ordinary untested code —
`setCurrentLang`, `getOpensourceLicense`, `generateImageFileNameByURL`,
`moveBookToTheTopOfBookshelf` and `onSearchClicked`, roughly 40 lines that are cheap to cover and
simply have not been. The rest split into two groups that a test cannot honestly reach today:

- **Deliberately out of scope, ~35 lines.** `saveUserInfoSet` and the success path of
  `loadUserInfoSet` write and decode a real credential file through `LightUserSession`;
  `saveNovelContentImage` downloads over the network. Writing credentials on a real device is not
  something a test should do, and the download path belongs behind a `RealApi` guard where it
  would not count on CI anyway.
- **Unreachable without a storage seam.** Every second-root fallback — `getExistingNovelContentImagePath:937`,
  the write-failure branches in `writeFullSaveFileContent`, the alternate paths in
  `generateImageFileNameByURL` — only runs when the first root fails. There is no way to make the
  first root fail from a test, because the root is resolved internally with nothing to inject.

That second group is precisely what step 2 exists to fix, and it is worth noting that the ceiling
was found by measuring rather than assumed: step 1 was expected to close most of the 123 and closed
27, because the code is less testable than it looks from a line count.

**Step 3 is not on the coverage critical path.** It is the prerequisite for the backend migration,
which is unscheduled. If the release gate is what matters, steps 1, 2 and 4 buy more.

**Keep this table current.** It exists because prose spread across many commits cannot tell a later
reader what is already done, and this document has twice sent someone down a path that was closed
months earlier — the coverage ranking above, and the `NovelItemListFragment` note that claimed a
Fragment could not be tested after it had been.
