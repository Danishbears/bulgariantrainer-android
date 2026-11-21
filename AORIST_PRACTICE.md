> **Completion:** see AORIST_INTEGRATION.md for separate persisted statistics,
> full-backupv5 compatibility/legacy replacement warnings and current0.8.0/code10
> verification. Earlier UI/practice build evidence below is historical.

# Offline aorist practice

Date: 2026-11-19; native UI update: 2026-11-21.

Native Kotlin/Compose/Room typed recall and multiple choice are implemented on
`codex/android-aorist-plan`, extending the content implementation at
`cb9a1a3ee569d9b8a40c701badd9b54119c58ae9`. ApplicationId remains
`com.danishbears.bulgariantrainer`; candidate0.8.0/code10 preserves dependency,
toolchain and signing-workflow versions. No backend/account/synchronization.

## Reference and scope

Available web refs were rechecked: master
`2792dc3e4cb5d4762783140729e2c37ccac95567` and codex/aspect-pairs
`be6a91ff2ae06b8b0eae2461d8c7a8bf910ce57a`. They still contain no located aorist
implementation. There is no claim of unverified aorist web parity. The inspected
web aspect practice/normalizer and common SpacedRepetitionScheduler guide shared
queue, exact matching and reveal semantics; aorist-specific behavior follows the
user requirements and audited attachment. The web repository is unchanged.

Actual Android source mapping (Kotlin paths relative to
app/src/main/java/com/danishbears/bulgariantrainer):

| Behavior | Files |
| --- | --- |
| Settings/defaults, choice/queue planners, public prompt/feedback | aorist/AoristPractice.kt |
| Transactions, grading/reveal/Next, recovery, progress and schedules | aorist/AoristPracticeRepository.kt |
| Exact normalization and source arrays | aorist/AoristContent.kt |
| Durable entities/DAO and additive10→11 migration | data/AoristPracticeEntities.kt, data/AoristPracticeDao.kt, data/AoristPracticeMigration.kt, data/TrainerDatabase.kt |
| Lifecycle state/draft persistence/retries and native UI | ui/AoristPracticeViewModel.kt, ui/AoristPracticeScreen.kt, ui/AoristScreen.kt, ui/TrainerApp.kt |
| Existing scheduler, unchanged | review/ReviewScheduler.kt |
| Full backupv5 and semantic validation | backup/BackupFormat.kt, backup/BackupValidation.kt, backup/BackupRepository.kt, ui/BackupScreen.kt |

The existing Russian UI/theme/bottom destinations remain. This stage introduces
no suffix-based conjugation, person-identification exercise, sentence grading,
aspect transfer or generated translations. Source data and content JSONv1 stay
unchanged; see AORIST_CONTENT.md for the40-verb/240-slot audit and hash.

## Start, configure and resume

Open **Главная → Аорист**. Explicitly import/confirm the starter dataset or local
JSON if necessary, then open **Практика аориста**. Fresh settings default to
**typed recall, аз (1sg),20 questions, mixed queue**. Choose any nonempty subset
of the six grammatical persons, or the all-persons/only-аз shortcuts. Filters
select aspect, studyGroup and optional datasetId. Groups are source models,
not CEFR or aspect-pair study stages. Session size is1–100.

The last started configuration is stored in aorist_preferences and backed up.
It does not force a later user choice back to the fresh default. UI configuration
drafts remain distinct from frozen session settings.

A target is one **verb + grammatical person**, represented by its stable form ID.
It appears at most once in one session, including when two persons share exactly
the same surface form. Different persons of one verb may be practiced. Archived
verbs are excluded from new targets and distractor sources. Insufficient pools
offer a shorter session with explicit consent; no duplicate padding or empty
session is created. Queue eligibility is recomputed transactionally at start.

New/due/mixed uses the existing scheduler for that form **and exercise type**:
new has no attempts; due has nextReview<=now; mixed prioritizes DUE, NEW,
LEARNING, MASTERED with injected random tie-breaking and shuffled final order.
Mixed deliberately also permits learning/mastered targets before their due
date, matching the existing shared policy. It does not mean mixed exercise types
or import legacy typing's30-question/direction quotas. One session has one type;
each item also freezes its type for durable validation.

Resume reads Room after navigation, Activity recreation, app restart or process
death. Back pauses and flushes the latest draft; it does not silently submit,
abandon or discard results. Pending feedback, including the last question, stays
current until explicit **Дальше**. The last Next completes the session. Early
completion retains answered events and marks untouched items skipped; skipped
items receive no attempt or schedule change.

## Prompts and exact answer rules

Every question explicitly shows the dictionary Bulgarian verb, its supplied
Russian meaning, the aorist tense (минало свършено време), and requested person.
3sg is labelled той/тя/то, not split into invented gender-dependent aorist forms.
The task always asks for that form, never to infer a person from an ambiguous
isolated form. Typed targets, alternatives and revealing notes are absent from
visible and accessibility trees until submission/reveal.

Matching uses only the snapshotted accepted array for that exact entry/person:

| Target | Approved example | Rejected example |
| --- | --- | --- |
| чета / ние | четохме | прочетохме |
| прочета / ние | прочетохме | четохме |
| взема / аз | взех | вземах |
| вземам / аз | вземах | взех |
| видя / ние | видяхме | видехме |
| изляза / той | излезе | излязе |
| облека се / аз | облякох се | облякох, облякох си |

Normalization policy1 is explicit:

1. Unicode NFC first.
2. Strip remaining U+0301 acute and U+0300 grave combining marks.
3. Collapse Unicode whitespace (including NBSP and supported Unicode spaces), trim.
4. Lowercase with Locale.ROOT and final NFC.

**Order matters.** и+U+0300 canonically composes to the letterѝ before mark removal;
it remainsѝ. и+U+0306 becomesй. Precomposedѝ (andѐ) are not reduced to another
letter. Remaining acute marks, e.g. ЧЕ+U+0301+ТОХМЕ, are ignored. A grave onи is
therefore interpreted as canonicalѝ, not an optional spelling ofи. This preserves
и/й/ѝ distinctions instead of stripping every diacritic indiscriminately.
Invalid unpaired-surrogate input is rejected before SQLite writes.

се and си, prefixes, spelling and punctuation remain significant. There is no
substring, Latin-lookalike, transliteration, fuzzy or automatic suffix matching.
All explicitly approved alternatives for the requested slot are accepted, in
their stored order. The actual starter has one answer per slot; additional test
alternatives are labelled synthetic and do not modify the learning material.

Mismatches say **«Не совпало с ответами этой карточки»**. Feedback shows the raw
submission/selected option, approved reference/alternatives and original note.
String inequality is not presented as proof that a Bulgarian word cannot exist.

## Multiple choice

Options are sampled only from stored approved forms of active verbs within the
selected dataset/aspect/group filters. Other persons of the same verb are preferred;
other eligible stored verbs can supply additional distractors. Selected-person
filters determine targets, while the other stored persons remain useful sources.
No invented forms or external service is involved.

Exclude every normalized member of the target's entire accepted set from the
wrong pool. Deduplicate normalized distractors across persons, verbs, case/stress
variants and homographs. Include one approved reference and shuffle once with
injectable randomness. Exactly one displayed option matches the accepted set;
a valid alternative is never classified as wrong.

Option IDs are assigned after shuffling and do not encode correctness. IDs,
displayed texts and order are stored; membership and grading are checked inside
the repository, not trusted from arbitrary UI strings. Choosing a radio option
does not submit it; **Проверить** is explicit.

Four options are preferred. Two/three require explicit fewer-option consent at
start; their count is labelled. Fewer than two excludes that choice target and
offers switching to typed configuration. The switch is explicit and re-evaluates
that type's queue; there is no silent conversion. Availability does not consume
the injected RNG used for actual planning. Reopening never reshuffles options.

## Durable writes, drafts and independent progress

Room11 adds six tables, for40 total/26 autoincrement sequences:
aorist_sessions, aorist_questions, aorist_attempts, aorist_progress,
aorist_review_schedules and aorist_preferences. All previous34 entity contracts
remain. Additive MIGRATION_10_11 preserves content, IDs, high-water sequences and
all prior modes; no destructive fallback or automatic imports are used.

Session UUID/settings/consents, order, verb/form/person/type, full learning
snapshots/hash, options/order, raw input/selection, feedback, reveal timestamp,
acknowledgement/position/revision/status and preferences are persisted.
Content edits/import updates/archive after start never change an existing
question or its accepted answers. Historical outcomes are not regraded during
read/recovery/restore.

Drafts are stored asynchronously on background transactions with increasing
per-question draft versions. Late/older drafts cannot overwrite newer input or
submitted feedback. SavedStateHandle provides immediate recreation state, but
Room restores committed drafts even with an empty new SavedStateHandle. Leaving
flushes the latest write; failure blocks exit and retains input with an error.
An abrupt kill before a keystroke's transaction commits can lose that uncommitted
keystroke; committed selection/order/submissions/results are durable.

Submit/reveal inserts exactly one terminal attempt, updates progress/schedule and
session revision in one transaction. A unique question-attempt index and current
session/question checks protect double taps and races. Identical retries return
the saved result, including after completion; conflicting payloads, unknown
options and new answers to finished/skipped questions are rejected. Old UI
callbacks carry their session/question IDs. Explicit Next is independently
idempotent and never changes mastery/schedule again.

Progress and schedules use (formId, exercise), with formId uniquely identifying
(verbId, person). Choice success never advances typed recall, another person,
the related aspect verb or any old learning mode. Existing word/sentence/aspect
statistics and scheduler tables are untouched. Session results are labelled by
their own exercise type; no new outcomes are appended to legacy statistics.

Viewing/approved audio is not an attempt. **Show answer** records REVEALED once,
not CORRECT; independent credit cannot be earned by subsequently typing/selecting
that exposed answer. Reveals increment review-event/reveal counts, reset that
form/type streak and schedule a failure retry after24 hours. The answered/accuracy
denominator counts only CORRECT+INCORRECT; reveal is reported separately.

The unchanged ReviewScheduler uses successful intervals1,3,7,14,28,… capped365
days; failure/reveal is1 day, and mastery needs streak>=5 and interval>=28.
Clock is injectable. Dates are UTC instants; one day is a24-hour Duration, not
device midnight. Timezone/DST affect display, not interval length. Clock corrections
do not rewrite old instants; the last logical event is identified by attempt
identity, not the greatest wall-clock timestamp.

All question audio is withheld until feedback/reveal, including source-lemma
playback: a dictionary form can itself coincide with an approved aorist answer.
Approved reference audio is explicit only after feedback. Choice options have
no pre-answer audio cue. Wrong raw submissions are not registered as approved audio examples.
Existing installed-offline Bulgarian voice handling, normal/slow replacement,
question/route stopping and missing-data usability remain. Fake adapters prove
application policy only, not pronunciation quality/offline voice availability.

## Full backup compatibility

Full-backupv5/schema11 includes all six practice tables, snapshots, choices,
drafts, attempts, independent progress/schedules and last-started preferences.
Content-only JSON remains formatVersion1 and still has no practice/progress.

Original v1/schema7,v2/schema8,v3/schema9,v4/schema10 contracts/checksums are
validated before isolated expansion. v1–v3 have no aorist content or practice;
v4 retains aorist content but has no practice/preferences. Explicit full replacement
warns in preview **and confirmation** before clearing data absent from an old
copy. No merge, inferred progress or silent newer-table discard is performed.

Consistent logical SQL snapshots include committed WAL data. Frozen preparation
validates all graphs/IDs/FKs, policy/settings, snapshots/hashes, option uniqueness,
attempt/raw/reveal/position invariants, counters and schedule consistency before
live changes. Restore is transactional with high-water sequences and rollback.
Unsupported future versions/policies and mislabelled newer tables are rejected;
outcomes are preserved without replaying progress. See BUILD_REPORT.md for actual
round-trip/migration/rollback/WAL and version verification results.

## Native UI update

[AORIST_UI_ANDROID.md](AORIST_UI_ANDROID.md) documents the six-person table,
separate tense/person labels, editor validation, native help, stricter audio
visibility and the complete import/practice/resume/results flow. It supersedes
the initial UI-stage details below where noted; Room11/full-backupv5 are unchanged.

## Verification and device checks

Current native UI candidate0.8.0/code10 is verified by
[run44896058759](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/44896058759):
381 tests,0 failures/errors/skipped, both builds/lints and APK/schema checks.
[Download its debug APK ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/44896058759/artifacts/14704860022)
(expires2026-12-05); see AORIST_UI_ANDROID.md and BUILD_REPORT.md for instructions,
actual verification scope and signing status. The older evidence below remains
as implementation history.

Tests use the actual starter for the required conjugations. Native SQLite tests
exercise both types/all persons, alternatives/normalization, queue/shortage,
deduped distractors, reveal/retry/races, transaction rollback, file-database reopening,
content edits/archive, committed drafts/feedback, independent schedules/mastery,
exact deadlines/backwards clock, schema10 migration and v5/full-backup compatibility.
ViewModel and native Compose tests cover persisted settings/selection, rapid and
stale callbacks, exit-flush failure, hidden typed targets/audio and explicit choice
submission/Next at320dp. Existing regression suites remain.

Previous practice milestone (not verification of the later UI update):
code/test commitc753d47d881a1f8e6dd41483154a98fdd4055a88 passed
[Actions run44596272752](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/44596272752):
**376 tests,0 failures/errors/skipped**, both lints (0 errors/14 advisories each),
debug and unsigned release builds, actual APK contracts and Room schemas1–11.
Local verification also passed all376, both builds/lints (13 dependency advisories
per variant) and APK verification. BUILD_REPORT.md records exact commands, the
corrected progress-order fixture and older navigation synchronization failure.

[Debug APK ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/44596272752/artifacts/14566252156)
is0.7.0/code8/API26+, expires2026-12-03. Extract app-debug.apk. It is debug-signed,
not a verified persistent-key release. No private release key was generated. JVM/Robolectric/native SQLite verification
does not imply instrumentation or physical-device testing.

Still test on a phone: Bulgarian keyboard/IME, rotation/process force-stop/restart,
small screens/large fonts/TalkBack, local-document/backup/restore in airplane mode,
real offline Bulgarian voice availability and a same-key in-place update with
data preserved. Save a full manual backup before update. Cloud debug certificates
can differ; preserve the existing installation/data on a signature mismatch and
use the existing persistent-key workflow. No replacement release key is created.
