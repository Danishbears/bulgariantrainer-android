# Aorist completion and personal offline use

Date: 2026-11-21. Candidate **0.8.0 / versionCode 10**, above the inspected code9.
Branch: `codex/android-aorist-plan`; existing PR #2 remains unmerged.
ApplicationId: `com.danishbears.bulgariantrainer` (unchanged), Android8/API26+.
Kotlin/Compose/Room, dependency versions and the existing signing workflow remain.

The previous stages already supplied the original40-entry/240-person-slot content,
transactional imports, native management and both durable practice types, and full
backupv5/schema11. This step adds separate persisted aorist statistics, improves
backup preview counts/warnings, and verifies the complete integration. It does not
recreate the project or alter other modes' algorithms/progress.

## Import and start

1. Open **Главная → Аорист → Просмотреть стартовый набор: 40 глаголов**, or use
   **Выбрать JSON аориста** for a local document. The system picker needs no broad
   storage permission. No dataset is automatically imported on launch.
2. Inspect filename/dataset/revision, counts, forms/source and validation errors.
   New entries are selected; updates require individual selection. Tap
   **Подтвердить импорт → Сохранить выбранное**. Reimport preserves IDs, archive
   state and learning progress and does not duplicate verbs or the six targets.
3. Open **Практика аориста**. Fresh defaults: typed recall, **аз**,20 questions,
   mixed queue. Choose persons/all six, typed/choice, aspect, study group, size
   and new/due/mixed queue. A shortage offers fewer unique targets without repeats.
4. Each question specifies the exact verb/Russian meaning, аорист
   (**минало свършено време**) and requested person separately. Enter an isolated
   form; for example, облека се / аз expects **облякох се**, not a pronoun prepended
   to it. Help distinguishes the sentence **Аз се облякох**.
5. **Проверить** and **Показать ответ** are distinct. Choice selection alone does
   not submit. Notes/tables and all question audio remain hidden until feedback;
   approved forms can then be played explicitly, normally/slowly, with Bulgarian
   TTS. A missing offline voice does not prevent practice. **Дальше** is explicit.
6. Back saves the latest draft before leaving. Reopen practice to resume committed
   order/person/type/snapshots/options/input/selection/feedback/position/status.
   Early finish preserves attempts and skips untouched questions. Results include
   mistakes/reveals. Revealing resets that form/type streak and schedules a failure
   retry24 hours later; it never receives independent-recall credit.

All learning uses local Room data after import. No backend, accounts, sync,
conjugation guessing, generated translations or sentence-gap exercises.
AORIST_CONTENT.md, AORIST_PRACTICE.md and AORIST_UI_ANDROID.md cover exact formats,
normalization, accepted arrays and native UI behavior. In particular и/й/ѝ and
се/си remain distinct; approved alternatives belong only to the exact verb/person.

## Separate statistics

Open **Статистика аориста** from the collection or practice/results. This nested
route does not add a bottom tab. It has a dataset filter, loading/error/retry
states, manual/on-resume refresh, committed database invalidation and a60-second
clock refresh. A failed read retains the last confirmed counts, with an error;
no fabricated zero replaces known data. Empty real data displays zero explicitly.

| Metric | Definition |
| --- | --- |
| Answered | Count of persisted CORRECT + INCORRECT aorist attempts. |
| Correct / incorrect | Separate terminal automatic outcomes; no fuzzy or manual regrading. |
| Revealed | Separate REVEALED count, excluded from answered/correct; skips/viewing add nothing. |
| Due for review | Number of active `(verb, person, exercise)` schedules with `nextReview <= asOf`. |

One schedule key is `(formId, TYPED/CHOICE)`; formId uniquely identifies verb/person.
A verb can therefore contribute multiple due targets. The total is not a unique
verb/form count or a sum of overdue historical attempts. No new/unreviewed target
is considered due solely because it has been imported.

Breakdowns include all six persons, both exercise types, and all twelve person/type
combinations. Each group sums the same base cells, without multiplying attempts
through accepted-answer or option joins. History includes archived verbs and
unfinished/abandoned sessions. Archiving removes due eligibility, retaining all
historical outcomes/schedules; restoring content restores its original schedule.
Next, retries and resume never recount an existing attempt. Legacy word, sentence
and aspect statistics remain independent. Multiple-choice success never advances
typed-recall mastery.

The repository reads outcomes and due schedules in one Room transaction with one
injected Clock instant. Due uses UTC epoch milliseconds, inclusive at the exact
deadline; it has no device-midnight reset. The unchanged scheduler uses24-hour
Duration days. Device time-zone/DST changes do not change instant deadlines;
manual clock changes affect current due eligibility but never rewrite history.
Statistics are derived, not stored in a second counter table or replayed on restore.

Source map relative to app/src/main/java/com/danishbears/bulgariantrainer:

| Behavior | Files |
| --- | --- |
| SQL aggregate reads and independent read-only model | data/AoristStatisticsDao.kt; aorist/AoristStatisticsRepository.kt |
| Lifecycle refresh/filter/error state and Russian native screen | ui/AoristStatisticsViewModel.kt; ui/AoristStatisticsScreen.kt |
| Collection/practice/results navigation and shared Clock | ui/TrainerApp.kt; ui/AoristScreen.kt; ui/AoristPracticeScreen.kt; BulgarianTrainerApplication.kt |
| Complete v5 inventory, summary and legacy decode | backup/BackupFormat.kt |
| Snapshot/staging/atomic replacement and semantic validation | backup/BackupRepository.kt; backup/BackupValidation.kt; aorist/AoristPracticeRepository.kt |
| Original-version preview/confirmation warnings and detailed counts | ui/BackupScreen.kt; ui/BackupViewModel.kt |

The available web refs were rechecked: master2792dc3e4cb5d4762783140729e2c37ccac95567
and codex/aspect-pairs be6a91ff2ae06b8b0eae2461d8c7a8bf910ce57a. No aorist feature
was located in those actual audited source trees; there is no invented aorist web
parity. Aorist-specific metric units follow the Android plan/user requirements.
The actual common scheduler/aspect rules remain pinned separately. Web files are
read only. The original attached JSON remains byte-identical (SHA-256
`af92873c06a36c81973206cc24808d01e1e9cc2cdacce66225f92f095c521e33`).

## Full backup format and replacement

Current full backup is explicitly **formatVersion5 / schemaVersion11**, with40
tables and26 AUTOINCREMENT high-water sequences. Content-only aorist JSON remains
`bulgariantrainer.aorist`, formatVersion1. Dataset revision, database version and
backup/content format versions are independent.

V5 already includes every required aorist row; this step adds a derived statistics
view and preview-only summary properties. Neither the persisted schema nor the
serialized backup contract changes, so bumping to a new wire/database version
would unnecessarily reject compatible backups. All11 Room schema exports remain
applicable; the existing additive9→10→11 migrations are verified with real old
schema fixtures, all prior table data and high-water IDs.

| Included aorist data | Tables |
| --- | --- |
| Namespace/revision/metadata/provenance/verbs/archive/six slots/accepted answers/import receipts | aorist_datasets, aorist_verbs, aorist_forms, aorist_answers, aorist_receipts |
| Session configuration/order/status/revision and immutable question snapshots, choice order, drafts/selection, reveal/acknowledgement/position | aorist_sessions, aorist_questions |
| Raw submissions/selected option/normalization/outcome/time | aorist_attempts |
| Independent counts and review dates by stable form/type | aorist_progress, aorist_review_schedules |
| Last started practice defaults | aorist_preferences |

All prior vocabulary/translations/sentences/settings/sessions/attempts/progress and
aspect data are included as before. No statistics cache is needed: the restored
records reproduce history; due naturally depends on the clock at viewing time.
Navigation/search/statistics-filter state, system picker grants and external TTS
voice data are not learning backup records.

Use the separate full backup controls under **Перенос**. Export is a logical SQL
snapshot inside one Room transaction, including committed WAL data; no open main
SQLite file is copied while ignoring WAL. Prepare validates the complete supported
version/checksum/exact table+column inventory, stable IDs/high-water marks/FKs,
content/snapshots/options, settings, outcomes/counters and schedule relationships
in an isolated database before any live write. Unsupported future versions or
mislabelled newer tables are rejected without trimming or reinterpreting them.

Preview identifies the original version and counts, including aorist verbs/sets,
slots/accepted answers, sessions/attempts, progress and schedules. Restore requires
an explicit replacement dialog. The validated bytes are frozen, not reopened from
a mutable document at confirmation. Related records are installed transactionally,
without replaying attempts or resetting IDs. A late failure rolls back the entire
replacement; current data and statistics remain intact.

| Original backup | Explicitly confirmed replacement behavior |
| --- | --- |
| v1/schema7, v2/schema8, v3/schema9 | No aorist exists in the original file. Current aorist content, sessions/snapshots/drafts, attempts, progress/schedules and practice defaults are cleared. Aorist statistics become zero. Other modes follow that version's documented inventory. |
| v4/schema10 | Restore saved aorist content/receipts/IDs, but clear current aorist practice/history/schedules/defaults because the original version had none. Aorist practice statistics become zero. |
| v5/schema11 | Restore full saved content, practice/settings and relationships; derive statistics from those records. |
| Unsupported version/schema | Reject before live changes. Never silently discard newer data. |

The first two consequences appear in both preview and confirmation. Preview alone
never clears current data. This is replacement, not merging or synchronization;
save and verify a current full backup before considering an older replacement.
Content JSON export includes only learning material and cannot replace a progress
backup. See BACKUP_RESTORE.md and RELEASE_SIGNING.md.

## Verification and delivery

[Verified Actions run44908377133](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/44908377133)
passed on code/test commit060639178b9b6086f0a28075b91673bca5b6bd26:
**392 JVM/Robolectric tests,0 failures/errors/skipped**, both builds/lints,
APK contracts, parity/Python checks and Room schemas1–11. Lint has0 errors and14
hosted advisories per variant (OldTargetApi +13 dependency updates); local full
checks also passed392, both builds/lints and APK verification, with13 dependency
advisories per lint variant. BUILD_REPORT.md records exact commands/checksums,
including the initial failed cloud UI-readiness assertion and its correction.

[Download debug APK ZIP —0.8.0/code10/API26+](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/44908377133/artifacts/14709108204)
(available until2026-12-05), then extract **app-debug.apk**.
[Test/lint/schema reports](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/44908377133/artifacts/14709212977)
are available alongside it. The hosted debug certificate differs from the previous
cloud0.7.1 build; see update limitations below.

Automated verification covers:

- Original40 entries/240 explicit slots, repeated import, content round-trip and
  stable IDs, malformed/unsupported input and import transaction rollback.
- Typed and choice accepted arrays, alternatives/Unicode/stress/particles, unique
  distractors, reveal/no independent credit, double taps/retries, separate schedules.
- File-database reopen with unfinished sessions, drafts/choice order/feedback,
  content edits after start and full-backup round-trip with restored statistics.
- Real schema9/10→11 preservation, original v1–v4 validation/replacement, v5 graphs,
  malformed snapshots/relationships/policies and late restore failures.
- WAL transactions, exact due boundary/time zones/backwards clock, six/two/twelve
  statistics groups, archived history, invalidation/error retention and old modes.
- Native320dp/large-font statistics navigation and old-backup warning/confirmation
  with test document streams. These are JVM/Robolectric checks, not device tests.

Local release signing variables are absent. The existing persistent-key signing
workflow is preserved; no release key or password is generated, replaced, read or logged.
Connected GitHub tools expose status/artifacts but no workflow-dispatch operation.
A verified signed release is not claimed. The baseline debug APK is labelled debug;
its cloud certificate may differ from earlier debug builds and the local certificate.

Download the current artifact ZIP from the verified run, extract **app-debug.apk**,
then open it in Android Files and allow installation for that file manager, or use
`adb install -r app-debug.apk` with an authorized device and matching certificate.
Save and verify a full backup before updating. If signatures differ, preserve the
existing installation and data; use the original key for an in-place update.
Do not uninstall to bypass a mismatch. ANDROID_BUILDS.md and RELEASE_SIGNING.md
explain installation and the existing signing setup.

No device/emulator is available here; adb cannot create its default read-only
`/home/agent/.android` directory. Instrumentation was not executed. On a phone,
still check actual Bulgarian keyboard/IME/insets, rotation/force-stop/restart,
TalkBack/largest fonts/small screens, local document providers and backup restore,
complete import→practice→resume→results/statistics in airplane mode, installed and
missing Bulgarian voices/normal+slow playback, and same-key in-place updates with
all existing modes/data preserved. Fake TTS does not verify offline voices/quality.
