# Aspect-pair integration and verification

Candidate0.5.0/code6; applicationId remains
`com.danishbears.bulgariantrainer`. Existing Kotlin/Compose/Room/dependency
versions, schema9 and signing workflow are preserved. No web changes or sync.

## Import, practice and statistics

Главная → Видовые пары. Choose a local JSON or **Предпросмотр набора из100пар**.
Use a stable namespace for the dataset; preview performs no writes. Review errors,
new/unchanged/update entries and the original editorial warning. Select updates
explicitly and confirm in the separate dialog. Reimport with the same namespace
and external IDs retains internal IDs/history and does not duplicate entries.
No import occurs automatically at startup.

Практика opens the saved session or its settings. A fresh setup selects stage1;
stages2/3 require explicit selection. Select direction, size, topic/namespace and
NEW/DUE/MIXED; a smaller eligible pool requires shorter-session confirmation.
Submit exact approved forms, keeping се/си and и/й/ѝ distinctions; then Дальше.
Reveal is terminal, gives no independent correct credit, resets the direction's
streak and schedules a retry after24hours. See ASPECT_PRACTICE_ANDROID.md.

The separate **Статистика видовых пар** screen reads only aspect tables:

| Metric | Persisted definition |
| --- | --- |
| Answered | CORRECT + INCORRECT attempts |
| Correct | CORRECT attempts |
| Incorrect | INCORRECT attempts |
| Due for review | Distinct non-archived pairs with at least one reviewed direction whose nextReview <= current device instant |

REVEALED is displayed separately and excluded from answered/correct/incorrect.
SKIPPED is not an attempt. Two due directions of one pair count1pair/2directions.
Per-direction details and optional namespace filtering match the web. History
includes active/abandoned/completed sessions and archived pairs; only due excludes
archive. Resume, Next, statistics reads and repeated submissions create no extra
attempts or schedule updates. Previous confirmed scoped values remain on errors;
unavailable results are not replaced by fabricated zeros. The screen refreshes
from Room invalidations, on resume, manually and every minute.

DAOs aggregate counts in SQLite; repositories read one consistent transaction and
one injectable Clock instant. No cached statistics table or cross-mode union is
added. The vocabulary/flashcard/typing/sentence StatisticsRepository, mode enum
and schedules remain unchanged. Device clock and millisecond instants replace
the web server clock; deadlines use exact24-hour durations, not local midnight or
DST. Clock corrections affect current due eligibility without rewriting history.

## Versioned full backup contract

The existing **full-backupv3/schema9** already holds all aspect learning state;
this increment retains that compatible format. Statistics are derived on restore,
so no new serialized field/table or artificial format/schema bump is needed.
The original source version is retained in the validated runtime preview, not
inserted into an older file or omitted from the warning.

| Aspect data | Full-backup tables |
| --- | --- |
| Namespace, root metadata/editorial/source definitions and content revision | aspect_datasets |
| Internal/external IDs, forms/meanings/topic/stage/relation/note/provenance/source context/examples/archive/version | aspect_pairs |
| Ordered accepted alternatives and normalized keys | aspect_answers |
| Durable content-import confirmations and source metadata | aspect_receipts |
| Request identity, confirmed settings, order position/revision/status/timestamps | aspect_sessions |
| Pair links, direction/order, immutable learning/accepted-answer snapshots/hash, reveal/acknowledgement | aspect_questions |
| Raw and normalized submission, outcome/time, unique question relationship | aspect_attempts |
| Independent pair/direction counters | aspect_progress |
| Independent direction review dates/intervals/streak/successes | aspect_review_schedules |

All29 application tables and19 AUTOINCREMENT high-water marks are included,
including old-mode content/history and trainer_preferences. Confirmed aspect
size/stages/topic/direction/scope/namespace are stored in each session and restored
with it; the latest recovered session supplies the next setup. Unsubmitted
editor/input/search/statistics-filter drafts and unconfirmed import previews use
platform UI saved state and are not full-backup learning records. Installed OS
TTS engines/voices are system setup, not part of a learning backup.

Full backup is a logical Room read transaction, including committed WAL data.
It never copies a live SQLite file without its WAL. Prepare checks the complete
envelope, original checksum, exact table/column sets/types, IDs/FKs/sequences,
metadata/answers/snapshots/session state/progress/schedules and integrity in an
isolated staging database. Preview is frozen; confirmation does not reread a
mutable document-provider URI. Summary and a second replacement confirmation
precede live changes. One transaction replaces/validates all rows; a failure
rolls back old content, settings, history, relationships and sequence values.
Restore invalidates waiting stale mutations and requires a fresh Activity task.

### Older and unsupported files

| Source | Explicit replacement behavior |
| --- | --- |
| v1/schema7 | Validate original20-table/13-sequence contract/checksum, stage empty aspect tables. Confirmed replacement restores the old content/history and removes all current aspect content/practice/progress. |
| v2/schema8 | Validate original24-table/16-sequence contract/checksum, stage empty aspect-practice tables. Confirmed replacement uses aspect content from that file and removes current aspect-practice history/progress. |
| v3/schema9 | Replace all local data with the full29-table/19-sequence snapshot, preserving its IDs/links/high-water marks. No merge/replay/regrading. |
| Other version/schema or relabelled/extra data | Reject before live changes; never drop unknown tables/fields to make a file appear compatible. |

The original v1/v2 version and specific loss warning appear in the preview and
replacement dialog. **Save a current v3 full backup before restoring an older
snapshot.** Original checksums are checked before expansion; existing v3 readers
continue to accept current exports. Unknown future versions are reported as
unsupported even if they introduce extra fields. A file claiming v2 while
carrying v3 practice tables is invalid, not silently trimmed.

Aspect content JSON remains separately
`format="bulgariantrainer.aspect-pairs", formatVersion=1`. It exports learning
content only, never sessions/attempts/progress/schedules. Vocabulary/sentence CSV
are also content only. None is a substitute for the full manual backup.

## Reference and verification map

Actual read-only web pin: `be6a91ff2ae06b8b0eae2461d8c7a8bf910ce57a`.
Statistics source: service/AspectStatisticsService.java (blob
`22db54fc463d1cedf4f775a58bee18899e3ae391`),
repository/AspectAttemptRepository.java (blob
`8331bddf20aa6cb8a228df61908d1080cd107c9f`), and
AspectPracticeIntegrationTests.separateStatisticsExcludeRevealsAndSkippedAnswersAndCountActiveDuePairsOnce.
Paths use the existing `src/main/java/com/example/bulgariantrainer` prefix and
corresponding test prefix. Native tests preserve its4answered/3correct/1incorrect/
1revealed/1due pair/2due directions fixture, using injected time rather than
rewriting web progress deadlines. The original external guide remains unavailable.

| Requirement | Executable coverage |
| --- | --- |
| Non-destructive upgrades and legacy history | Existing AspectRepositoryTest schema7 migration, AspectPracticeRepositoryTest schema8 upgrade and legacy mode migration suites |
|100-entry import/reimport/whole-file validation/content round-trip | AspectRepositoryTest, AspectContentTest, AspectBackupIntegrationTest |
| Both directions/mixed/alternatives/normalization/particles/letters | AspectPracticeRepositoryTest, AspectPlannerTest; frozen actual Java parity harness |
| No recall credit after reveal, duplicate/race protection, independent schedules/recovery | AspectPracticeRepositoryTest, AspectPracticeViewModelTest, AspectPronunciationTest |
| Separate real statistics, due boundaries/archive/namespaces/no old-mode pollution | AspectStatisticsRepositoryTest; pinned web fixture |
| Reactive counts and last-good/error behavior | AspectStatisticsViewModelTest |
| Full content/metadata/examples/snapshots/settings/progress restore/reopen, rollback/WAL | AspectBackupIntegrationTest and existing full-backup suites |
| Unsupported/relabelled/malformed files and original legacy checksum | AspectBackupIntegrationTest, BackupFormatTest |
| Explicit confirmation/old-version warnings/native routes | BackupViewModelTest, AspectComposeTest, AspectNavigationComposeTest |

Coverage descriptions are not execution claims; BUILD_REPORT.md records actual
commands, run SHAs, failures/corrections and results. Room9 entities/migrations are
unchanged: the new DAO/projections have no schema changes. All migration suites
and committed compiler schema checks remain required; no destructive fallback.

## Build, signing and physical acceptance

Use the existing Android baseline workflow for tests/lints/debug and unsigned
release verification. Use Personal signed release with the original persistent
key as documented in RELEASE_SIGNING.md; no replacement key is generated. A
debug APK is installable but is not a verified private-key signed release.
Debug keys may vary across cloud runners. In-place update needs the same
applicationId/key and a higher versionCode. Never uninstall valuable data just
to bypass a signature mismatch; preserve a verified full backup first.

Physical acceptance remains: local SAF import/export and full-backup confirmation,
airplane mode after import, actual Cyrillic IME/TalkBack/font/rotation/insets,
process termination before/after submission/reveal/Next, independent due queues
after device clock changes, installed offline Bulgarian voices/missing voice
guidance, and same-key code-N→code6 installation preserving all existing data.
Robolectric/database reopening/fake TTS are narrower evidence. If no connected
device/emulator exists, no instrumentation or physical-device pass is claimed.
