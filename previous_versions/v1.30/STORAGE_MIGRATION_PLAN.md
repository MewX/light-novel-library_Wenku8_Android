# Storage migration plan

Status: proposal. Nothing here is implemented.

Companion to `STABILITY_PLAN.md`, which covers crash-level stability. This one covers the single
subsystem that has been a source of defects for the life of the project, and which that document
deliberately stops short of: persistence.

**The governing decision, and the reason this document exists separately.** Today's storage logic
is not to be patched incrementally. Either it is replaced with a real structure, or it is left
exactly as it is. A temp-file-and-rename fix for `LightCache.saveFile` was written, device-tested
and then reverted under this rule — the reasoning is recorded as Phase 1 item 11 in
`STABILITY_PLAN.md`, and it generalises: partial fixes to a component scheduled for replacement
are thrown away twice, carry their own new risk, and are hard to prove better than what they
replace.

The consequence is that the defects listed below stay live until this plan lands. They are
**known accepted risks**, not oversights. That is a real cost and it should be paid knowingly:
the longer this sits unscheduled, the longer users keep losing chapters.

---

## What is wrong today

Six defects, all found by reading and testing rather than from crash reports, which is why this
work does not need Phase 0's data to justify it:

| # | Defect | Consequence |
|---|---|---|
| 1 | `loadStream` sized a read from `available()` | Silent truncation → unparseable XML (root cause 4) |
| 2 | `loadLocalBookShelf` parsed ints with no guard | Crash on launch from one corrupt token (item 9) |
| 3 | Readers treated a cached chapter as the only source | One bad download made a chapter permanently unreadable (item 10) |
| 4 | `saveFile` truncates the destination before writing | Interrupted write destroys good content (item 11, **unfixed**) |
| 5 | `getInternalSavePath` cached a path built from a null context | Every save went to a literal `null/` directory |
| 6 | `getDefaultStoragePath()` names the *external* root | Unwritable on API 29+; every save silently falls back |

1, 2, 3 and 5 are fixed. **4 is unfixed by policy.** 6 is a naming trap rather than a bug.

The pattern is the point: every one of these is a consequence of hand-managing bytes, paths and
formats that a storage engine manages as a matter of course. They are not six unrelated bugs, they
are one decision producing six symptoms.

## What is stored

| Data | Today | Shape |
|---|---|---|
| Settings | `ContentValues` blob in a file | Key/value |
| Bookshelf | `aid\|\|aid\|\|aid`, one line | Ordered list of ints |
| Reading positions | Save files, two generations (`loadReadSaves`, `loadReadSavesV1`) | Records keyed by novel |
| Search history | Save file | Ordered list of strings |
| Volume index | `intro/<aid>-volume.xml` | Relational: novel → volumes → chapters |
| Chapter text | `novel/<cid>.xml` | Bulk text |
| Images | Cover, avatar, background files | Binary blobs |
| User info | Save file | Single record |

## Target

**Records in a database, blobs on disk.** Room for the structured data, DataStore for settings,
and the filesystem for chapter text and images — with a row recording whether each file is
complete.

Chapter text is deliberately *not* moving into the database. It is bulk sequential text, which is
what filesystems are for. What it actually needs is an atomic write and a way to know a file is
whole, and both are cheaper as a row plus a rename than as a BLOB column.

**Why this fixes rather than relocates the problem:** defects 1 and 4 stop being possible because
nothing hand-manages buffers or truncation; 2 stops being possible because a list of integers is
never re-parsed from text; 3 becomes a query against a completeness flag rather than an inference
from file contents; 5 and 6 stop mattering because the database owns one location.

There is a testing dividend too, and for this codebase it may matter as much. Room runs against an
in-memory database on the JVM, so the storage tests that today can only run on a device —
`VolumeIndexCacheTest`, `LocalBookshelfTest`, much of `LightCacheTest` — move into the fast suite.

## The blocker: storage access is not contained

Measured on the current tree:

| | count |
|---|---|
| `LightCache.*` call sites in `app/` | ~104 |
| Files reaching past `GlobalConfig` into `LightCache` | 13 |
| Worst single file (`NovelInfoActivity`) | 27 |

`GlobalConfig` looks like a storage facade and is not one. Thirteen files reach around it, so
there is no single place a new backend can be installed. **Swapping the store today means editing
all of those call sites while simultaneously changing the on-disk format and migrating user
data — three hard things at once, in the area where mistakes are least recoverable.**

## Steps

Each ships independently. Stalling after any of them leaves the app better off than before it,
which is the property that makes this safe to start without committing to finish.

**Step 0 — Make `GlobalConfig` the only door.** Move the 13 files onto its API; `LightCache`
becomes an implementation detail. No behaviour change, no format change, no migration, no
database decision. Verifiable by the tests that already exist. This is the prerequisite for
everything below and the step most likely to be skipped.

**Step 1 — Define the schema and the migration harness.** Entities, DAOs, and the machinery for
"read old format, write new, verify, report". The harness is the deliverable here, not the data
move — it gets used four times.

**Step 2 — Settings to DataStore.** Smallest surface, no relational structure, and losing it is an
annoyance rather than a disaster. Which makes it the right place to find out whether the harness
works.

**Step 3 — Bookshelf and reading positions to Room.** The highest-value records: small,
structured, frequently written, and the source of defects 2 and 4. Note the two generations of
reading-position format — whatever migration runs has to handle both.

**Step 4 — Volume index to Room.** Larger and genuinely relational (novel → volume → chapter), so
it benefits most from a schema. Also what the readers depend on since Phase 2.1.

**Step 5 — Chapter files get a completeness row.** The file stays on disk; the database records
whether it is whole. The atomic write from item 11 lands here, alongside the row that justifies
it, instead of as a standalone patch.

**Step 6 — Reclaim.** Delete the old files, a release *after* the last reader of them is gone.

## Constraints on the migration itself

The schema is the easy part. The migration is the project, and these are not negotiable:

1. **Never delete old data in the same release that stops reading it.** Migrate, run on the new
   store while the old files sit untouched, reclaim a release later. Step 6 exists for this reason.
2. **Idempotent and resumable.** It *will* be interrupted — that is the failure this codebase keeps
   producing. A half-finished migration must be safe to re-run, not something that needs
   detecting.
3. **Counted and reported.** Records in, records out, and a non-fatal on any mismatch, in the shape
   Phase 0 established. A migration that silently drops entries is the worst possible version of
   root cause 4.
4. **Tested against corrupt inputs, not just clean ones.** A truncated index, a bad bookshelf
   token, a half-downloaded chapter. All of these exist on real devices *today*, so the migration
   meets them on day one. The device tests written for `VolumeIndexCacheTest` and
   `LocalBookshelfTest` are the fixtures.
5. **One-way, and versioned.** No dual-write period. A version marker decides which store is
   authoritative, so a downgrade is a known state rather than a surprise.

## Sequencing against `STABILITY_PLAN.md`

Step 0 can start immediately: it is mechanical, low-risk, individually shippable, and needs no
decision about databases. Steps 1–6 want Phase 2's seams first, because a storage layer behind an
interface is far easier to replace than one called from 104 places.

This does **not** wait on Phase 0 crash data. That data answers "which screen is crashiest", which
is what Phase 2.2 needs. Nothing is waiting to be learned about whether storage is defective —
that question is answered above, six times.
