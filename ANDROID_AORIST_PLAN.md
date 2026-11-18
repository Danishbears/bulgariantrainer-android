# Android aorist feature plan

Date: 2026-11-18.

Status: **planning only; implementation is not authorized by this document.**
This change adds this file only. Application code, Room schemas, dependencies,
application identity, versions and signing configuration remain unchanged.

## 1. Evidence, reference revisions and blockers

### Repositories and instructions

Authenticated GitHub integration read access to both repositories was verified.
The Android write target is `Danishbears/bulgariantrainer-android`; the web repository
`Danishbears/bulgariantrainer` was read only.

| Repository/ref inspected | Exact commit | Result |
| --- | --- | --- |
| Android `codex/android-foundation-parity` | `d38eacd75133e2fdf192c6aa9c592671da062849` | Existing offline application; implementation baseline for this plan. |
| Android default `main` | `d18ad8814b391c60b0e1ec2099a5c2251978b2c6` | README-only foundation; do not rebuild the project from this branch. |
| Web default `master` | `2792dc3e4cb5d4762783140729e2c37ccac95567` | Existing vocabulary/sentence/aspect functionality; no located aorist feature. |
| Web feature `codex/aspect-pairs` | `be6a91ff2ae06b8b0eae2461d8c7a8bf910ce57a` | Existing aspect implementation; no located aorist feature. |

The two available web heads have the same source tree,
`eab22e285cc0fea7df61694147069172258b713e`: the default head is the aspect-feature
merge, with no file changes relative to the feature head. Both complete recursive
tree listings were inspected (247 entries each, not truncated), as were branch refs,
tags (none returned), and an aorist PR search (no matches returned). Neither tree
contains `bulgarian_aorist_40.json`, aorist-named source/tests, or aorist documentation.
Actual web files read include `AGENTS.md`, `README.md`, `pom.xml`,
`src/main/resources/static/index.html`, and these Java paths under
`src/main/java/com/example/bulgariantrainer/`:

- `entity/TrainingMode.java`;
- `service/AspectAnswerNormalizer.java`;
- `service/AspectPracticeService.java`;
- `service/SpacedRepetitionScheduler.java`.

Also inspected:
`src/test/java/com/example/bulgariantrainer/AspectPracticeIntegrationTests.java`.
These are evidence for existing mechanisms, **not an aorist parity reference**.
An aorist implementation, migration, tests and dataset were not located on the
available refs. This does not establish that no private/unpublished/deleted
implementation exists. If another ref is supplied, inspect and pin it before
implementing its behavior. Do not claim aorist web parity from the aspect feature.

The web root `AGENTS.md` was read at the pinned default commit. Relevant guidance:
Russian visible text, UTF-8, minimal dependencies, explicit state/error handling,
no automatic imports, no deletion of personal data, and honest verification.
Its Spring/Java and Maven instructions govern that web repository, which is not
being changed; Android remains Kotlin/Compose/Room. No `AGENTS.md` appeared in the
Android recursive tree; no ancestor `/AGENTS.md` or `/workspace/AGENTS.md` was found.

No local checkout existed at the start of this task. One clone attempt failed:
`Failed to connect to proxy port 8080`. It was not repeatedly retried.
Pinned actual text files were retrieved through GitHub and placed in local
inspection mirrors. Those mirrors are not claimed to be complete Git checkouts
or a working Android build environment.

The documentation branch is `codex/android-aorist-plan`, based on the Android
implementation commit above. Keep it separate; do not merge it into default or
merge the existing application PR as part of planning.

### Attached dataset: unavailable, schema not verified

The request names an attached `bulgarian_aorist_40.json`, but this task exposes no
attachment/file ID or accessible uploaded bytes. Searches of available workspace
upload/material locations found no such file; it is absent from both available
web trees and the Android tree. A repeat attachment was requested while the
architecture investigation continued.

**No JSON from that dataset has been read or parsed.** Its root type, keys,
format marker/version, actual record count, external IDs, person representation,
forms, alternatives, meanings, examples and provenance are all unverified.
The filename is not evidence that it contains 40 valid verbs, six forms per verb,
or any particular JSON schema. No schema or linguistic discrepancy in this
unavailable file is confirmed.

The design below is an explicit Android proposal based on the user's requested
conjugation exercises and the inspected Android architecture. Logical entity
names are proposed internal fields, not alleged dataset keys. The first
implementation stage is blocked until the actual file is available.

Before freezing any content contract, append a dataset audit to this plan:

1. Preserve the original bytes as reviewed material; record SHA-256, byte size,
   encoding/BOM, supplied filename and source. Do not silently replace it with
   generated conjugations or another online file.
2. Parse strict UTF-8 JSON, reject duplicate object keys, malformed Unicode,
   trailing non-whitespace, unexpected structures and excessive sizes/depth.
3. Record actual root/entry/form/example fields, types, nullability and enums;
   give literal JSON paths and representative actual records, including accepted
   alternatives and reflexive verbs if present.
4. Count verbs, external IDs, available person/number slots, accepted variants,
   missing/duplicate/contradictory slots and normalized-answer collisions.
   Audit dictionary/citation forms, aspect labels if present, Russian meanings,
   particle placement and the aorist tense label.
5. Inspect any provided choice options: verify a unique correct normalized choice,
   stable identity, grammatical person and compatibility with accepted alternatives.
6. Record discrepancies separately as confirmed schema errors, suspected linguistic
   issues requiring review, or supported variants. Do not silently correct source
   material, infer missing forms or claim string inequality proves bad Bulgarian.
7. Freeze a field-to-Room mapping and versioned import/export contract that preserves
   all supported learning and source metadata. Revise this proposal if the actual
   schema needs additional relationships.

## 2. Scope and learning unit

“Аорист” teaches Bulgarian `минало свършено време` through:

- typed recall of a supplied aorist form for a specified verb and person/number;
- single-answer multiple choice for the same grammatical target.

It is conjugation, not aspect-pair recall or sentence translation.
`чета → четох` and `прочета → прочетох` are separate user-specified first-person
singular examples, belonging to two distinct verb records. Neither result may
update aspect-pair recall. Do not infer one verb's paradigm from another's aspect.

The proposed target is **one verb + one explicit person/number slot**, with
approved surface forms. Use stable internal person codes for 1SG, 2SG, 3SG,
1PL, 2PL and 3PL after mapping the actual dataset representation. If it uses
pronoun keys or separate gender labels, map and document those explicitly.
Do not manufacture absent slots. Identical surface forms across different
persons are valid separate grammatical targets, not duplicate verbs.

Proposed prompt:

> чета — читать  
> Напиши форму аориста: аз (1-е лицо, единственное число).

For choices: “Выбери форму аориста …”. Preserve the dictionary/citation wording
actually used by the dataset; Bulgarian citation forms must not be described
as Russian-style infinitives. Display tense and person explicitly, including in
TalkBack. Meanings come from imported content, never generated translations.

No backend, Spring Boot connection, accounts, WebView, automatic import or
desktop/mobile synchronization. Practice and local management work offline
after explicit import. No runtime network dependency is introduced.

## 3. Existing Android implementation to reuse

Paths below are actual inspected files at the pinned Android commit.
Kotlin paths are relative to
`app/src/main/java/com/danishbears/bulgariantrainer/`.

| Concern | Actual files | Existing behavior and extension point |
| --- | --- | --- |
| Identity/build | `app/build.gradle.kts`, root `build.gradle.kts`, wrapper properties, `version.properties`, manifest | ApplicationId `com.danishbears.bulgariantrainer`; 0.5.0/code 6; minSdk 26, compile/target 35; Kotlin 2.1.20, AGP 8.9.2, Gradle 8.11.1, JVM 17, Room 2.7.2, Compose Material 3. Preserve these for planning. |
| Database/migrations | `data/TrainerDatabase.kt`, `data/AspectEntities.kt`, `data/AspectMigration.kt`, `data/AspectPracticeEntities.kt`, `data/AspectPracticeMigration.kt` | Schema 9, 29 registered tables; exported schemas; additive 1→…→9 migration chain; dedicated aspect tables. |
| Content/repositories | `aspect/AspectContent.kt`, `aspect/AspectRepository.kt`, `data/AspectDao.kt` | Dedicated strict JSON, namespaces/external IDs, ordered answers, search/filter/25-row pages, versioned edits/archive, content hashes and source context. Do not reuse aspect entities as aorist rows. |
| SAF import/export | `aspect/AspectDocuments.kt`, `ui/AspectViewModel.kt`, `ui/AspectScreen.kt` | ContentResolver streams on IO; OpenDocument/CreateDocument; bounded bytes, immutable preview, explicit selection and updates, receipt/idempotency, dataset and record version checks. |
| Transactions | `data/ContentTransaction.kt`, `aspect/AspectRepository.kt` | Coroutine transactions and a contentEpoch guard against work queued before full replacement. Preserve epoch/stale-operation protection. |
| Durable practice | `aspect/AspectPractice.kt`, `aspect/AspectPracticeRepository.kt`, `data/AspectPracticeEntities.kt` | Persisted requests/settings/order/directions/content snapshots, attempts, reveal, acknowledgement/position/revision/status; atomic grading and scheduling; unique attempt per question; reload from Room. |
| UI lifecycle | `ui/AspectPracticeViewModel.kt`, `ui/AspectPracticeScreen.kt` | ViewModel/StateFlow/SavedStateHandle, lifecycle collection, retry/reload, explicit Next, early completion and review. SavedStateHandle input alone is not sufficient for arbitrary restart; see aorist draft storage below. |
| Scheduler | `review/ReviewScheduler.kt`, `review/ReviewSelection.kt` | Injected Clock, exact existing interval/category policy and new/due/mixed priorities. Reuse the algorithm, not word/aspect progress tables. |
| Statistics | `aspect/AspectStatisticsRepository.kt`, `data/AspectStatisticsDao.kt`, `statistics/StatisticsRepository.kt` | Aspect aggregates are already separate from legacy word/sentence metrics. Follow the separation for aorist and exercise types. |
| Settings | `data/TrainerPreferences.kt`, `aspect/AspectPractice.kt` | Legacy preferences are persisted in Room; aspect session settings are in session JSON, while configuration drafts live in SavedStateHandle. Add explicit aorist durable preferences rather than pretending existing global columns cover them. |
| TTS | `pronunciation/BulgarianPronunciation.kt`, `pronunciation/AndroidTtsAdapter.kt`, `pronunciation/PronunciationPolicy.kt`, `ui/PronunciationControls.kt` | Async Activity-owned adapter; installed offline Bulgarian voice only, explicit normal/slow playback, stop/replace/lifecycle ownership, no silent other-language fallback; approved text visibility gates. |
| Full backup | `backup/BackupFormat.kt`, `backup/BackupRepository.kt`, `backup/BackupValidation.kt`, `backup/BackupDocuments.kt`, `ui/BackupViewModel.kt`, `ui/BackupScreen.kt` | Full-backup v3/schema9, exact 29 tables/19 sequences; v1/schema7 and v2/schema8 readers; transactional logical SQL snapshot, isolated staging validation, immutable prepared restore, confirmation, replacement, foreign keys, semantic validation, rollback. |
| Navigation | `ui/TrainerApp.kt`, `ui/DashboardScreen.kt` | Five existing bottom destinations; aspect section reached from dashboard, with nested management/practice/help/statistics routes and hidden bottom bar. |
| Regression patterns | `app/src/test/java/com/danishbears/bulgariantrainer/aspect/AspectRepositoryTest.kt`, `aspect/AspectPracticeRepositoryTest.kt`, `backup/AspectBackupIntegrationTest.kt` | Native SQLite Robolectric tests cover migrations, actual content import, replay/race/reopen, snapshot edits, full backup, old versions, rollback and WAL. Existing tests must remain. |

Read companion docs `LOCAL_STORAGE.md`, `ASPECT_INTEGRATION.md`,
`BACKUP_RESTORE.md` and `REVIEW_AND_STATISTICS.md` alongside these sources.
Documentation is supporting evidence; it does not establish aorist semantics.

## 4. Proposed Room storage and migration

Use an isolated `aorist/` domain with repository/service boundaries, dedicated
DAOs in `data/`, ViewModels in `ui/`, and application-scoped construction through
the existing application object. Reuse existing coroutine/serialization/Room
dependencies. Do not introduce a generic learning-engine redesign.

Proposed entities, subject to the dataset audit:

| Table | Identity and content/relationships |
| --- | --- |
| `aorist_datasets` | Auto internal ID; unique stable namespace; supported source metadata and per-entry source context where needed; content revision/hash separately from formatVersion; optimistic version and timestamps. |
| `aorist_verbs` | Auto internal ID; FK dataset; unique (datasetId, externalId); dictionary Bulgarian form, Russian meaning(s), supported aspect/topic/stage/notes/provenance/examples if supplied, canonical original supported fields, content hash/search text, archive/version/timestamps. Different verbs remain separate even if related by aspect. |
| `aorist_forms` | Auto internal ID; FK verb; unique (verbId, personCode); primary approved reference, per-slot source/notes/examples if supplied, stable content identity/version/hash and archive state. |
| `aorist_answers` | Auto internal ID; FK form; ordered exact accepted strings and normalized keys; unique (formId, position) and (formId, normalizedKey); primary reference must belong to its accepted set. |
| `aorist_receipts` | UUID request PK; FK dataset; preview identity, file/selection hashes, summary and supported metadata; idempotent confirmed import. |
| `aorist_sessions` | Auto internal ID; unique start request UUID; exercise type/settings/shorter consent/planner version, total/current position/revision/status/start/end timestamps. |
| `aorist_questions` | Auto internal ID; FKs session and form; unique (sessionId, position) and (sessionId, formId); immutable verb/person/accepted/reference/meaning/metadata snapshot plus content and normalization-policy versions; type, ordered choice snapshots/IDs when applicable; durable input/selection draft, reveal timestamp and acknowledgement. |
| `aorist_attempts` | Auto internal ID; unique FK question; raw typed text or selected option ID and display snapshot, normalized typed value where applicable, CORRECT/INCORRECT/REVEALED outcome and timestamp. Exactly one terminal result. |
| `aorist_progress` | PK (formId, exerciseType); FK form; attempts/correct/incorrect/revealed and last-review instant. Separate typed recall and recognition. |
| `aorist_review_schedules` | Same composite PK/FK to progress; existing scheduler attempts/successes/streak/interval/last/next review fields. |
| `aorist_preferences` | Singleton dedicated durable last-used configuration with schema/version validation: exercise type, size, person filters, queue, namespace/topic/stage only when actually supported. Avoid arbitrary unvalidated settings blobs. |

These are **logical internal columns, not the attached file schema**.
Approved examples/metadata may be canonical validated JSON where no relational
query is required, following the aspect implementation. Do not discard fields
merely because this table sketch omits a source-specific name.

Identity is stable: external IDs are not Room row IDs, filenames, list indexes,
timestamps or formatVersion. Persist an explicit dataset namespace; if the file
lacks one, the user chooses/confirms a stable namespace on first import and
reuses it. If it has no IDs, resolve and document a durable import identity
scheme before shipping; do not quietly manufacture new IDs on every import.
Manually created verbs get persisted UUID external IDs once.

Use a non-destructive **9→10** migration if schema9 is still current when
implementation starts. Re-inspect HEAD first; rebase the plan if intervening
changes have consumed that version. Add tables/indices/FKs, register entities
and migration, export schema10, and keep the entire 1→…→9 chain.
Under the proposed eleven-table design this means 40 tables and 26
autoincrement sequences; freeze the actual inventory from generated Room
schema, not from this planning count.

Do not alter/delete existing rows, IDs, sequences, settings or history.
No destructive fallback, database recreation, automatic seed/import, or content
inference from existing aspect pairs. Use soft archive for verbs/forms:
editing/archiving preserves session FKs and history. Removed source slots require
explicit preview handling; archive them or retain them rather than deleting
referenced rows. Old snapshots remain valid after edits.

All related writes are transactional on background coroutine dispatchers. Index
namespace/external identity, archive/search/filter fields, form/person, session
order/status, and schedule lookup/due fields. Use bounded pages/LazyColumn;
do not load every full conjugation graph to render the list.

## 5. Management and content import/export

Provide a searchable Russian/Bulgarian list, view one verb/paradigm, validated
create/edit, and archive/restore. Preserve source-approved variants and optional
fields. Search normalization may be convenient, but must never become grading.

Use separate `AoristDocuments`, parser/codec and content repository following
`AspectDocuments` and `AspectRepository`:

1. OpenDocument → ContentResolver → bounded immutable bytes on IO. UTF-8 with
   or without BOM; no hard-coded paths or broad storage permissions.
2. Validate the **entire** document with JSON paths/external IDs and diagnostic
   source information before any live write. No dropping malformed rows.
3. Preview additions, unchanged records, updates, archive consequences, warnings
   and errors. Updates are unselected until explicitly chosen; no hidden merge of
   accepted answers or silent overwrite of current content.
4. Require explicit selection and confirmation. Freeze parsed data/file digest,
   namespace, dataset and per-record versions, expiry and contentEpoch.
5. Recheck all dependencies at confirmation. A content edit, archive, concurrent
   import, changed choice dependencies or full restore makes the preview stale.
   Re-read/re-preview instead of applying another document at the same URI.
6. Apply all selected changes and receipt in one transaction. Retry returns the
   same receipt; changed selection with the same request is rejected.
   Reimporting identical source into the same namespace creates no duplicates.
   Updates keep internal verb/form IDs, attempts, progress and schedules.
   Never replay historical answers against new accepted sets.

Use the actual supplied format after audit. A possible marker
`bulgariantrainer.aorist` / formatVersion1 is **provisional**, not a claim about the
missing JSON. If it has another envelope or no version, document a strict
source adapter and a separate canonical export contract; do not silently rewrite
the original into an invented “web-compatible” format.

Validate actual required fields, IDs, supported tense/person/number enums,
nonblank approved forms, nonempty correctly typed answer arrays, source
relationships and example structures. Normalize for duplicate detection only;
keep original text. Require primary forms in accepted sets. Cross-person
homographs are allowed; duplicate slots or incompatible metadata are not
silently merged. Do not copy aspect-only `relationType` or studyStage1–3 rules
unless the aorist source independently specifies them. Stages, if present,
are not CEFR levels.

Export with CreateDocument and ContentResolver as a separate, versioned
**content-only** JSON. Preserve all supported learning/source fields, stable
namespace/IDs, form slots and accepted-answer order; define archived inclusion
explicitly. Validate exported bytes with the same codec and test round-trip.
It contains no sessions, attempts, progress or schedules and is not a progress
backup. Vocabulary CSV, sentence CSV, aspect-pair JSON and full backup must be
rejected by the aorist importer.

Only add the original dataset to `learning-materials/aorist/` after its bytes are
available and reviewed. App bundling is optional; if included, expose “Просмотреть
набор” followed by the same preview/confirmation. Never import at startup.

## 6. Answer rules: explicit Android proposal, not verified web parity

Keep an isolated, versioned `AoristAnswerNormalizer`. The proposed policy follows
the existing inspected aspect normalizer; confirm dataset stress conventions
before freezing it:

1. Unicode NFC.
2. Remove remaining combining stress marks U+0301 and U+0300.
3. Collapse Unicode whitespace and trim.
4. Lowercase using Locale.ROOT; final NFC.

Order matters: NFC before stress removal preserves `ѝ`, including the
decomposed `и + U+0300` representation. Preserve distinctions between
`и`, `й`, `ѝ`, and between `е` and `ѐ`. Do not strip all combining marks,
remove particles, fold spelling, remove punctuation, transliterate, accept
Latin lookalikes, accept substrings or grade by fuzzy similarity.
`се` and `си` remain mandatory wherever part of the approved surface form.
Do not automatically append pronouns/particles or accept them in arbitrary
positions: match exactly the target slot's approved strings after normalization.

Grade typed text only against the snapshotted accepted answers for that exact
verb/person. Preserve raw submissions. Feedback on mismatch:
“Не совпало с ответами этой карточки”; show the approved reference/alternatives
without declaring the submitted expression nonexistent or grammatically wrong.

Representative **requirements/proposed test fixtures**, not extracted dataset rows:

| Input/target | Expected |
| --- | --- |
| чета, 1SG aorist → четох | Correct when четох is an approved answer for that target. |
| прочета, 1SG aorist → прочетох | Correct for the separate прочета target; no credit for чета or aspect recall. |
| `  ЧЕТОХ\t\n` vs approved четох | Match after case/whitespace normalization. |
| `че + U+0301 + тох` vs четох | Match after stress removal. |
| Any actual alternative for the target person | Match; an alternative from another person is not automatically accepted. |
| Approved full form containing се/си; submission omits or swaps it | Mismatch unless that exact variant is separately approved. |
| и / й / ѝ, Latin `e` vs Cyrillic `е`, full form vs substring | Remain distinct; no fuzzy/transliteration match. |
| Empty input or whitespace/stress-only normalized input | Validation error; no attempt or schedule change. |
| Typed primary reference after explicit reveal | No independently recalled correct credit. |

Use real dataset alternative/reflexive cases only after the audit. Synthetic
codec/normalizer fixtures must be labelled as such, not presented as linguistic
dataset validation.

## 7. Exercise configuration and planning

First version offers a **single exercise type per session**: TYPED or CHOICE.
“Mixed” queue means new/due scheduling, not an undocumented mix of exercise
types or aspect directions. Add mixed exercise sessions only with a separate
explicit requirement and persisted per-question type.

Proposed settings: type, size (default20, valid1–100), selected persons
(default all available), new/due/mixed queue, dataset, and actual supported
topic/stage filters. Do not invent aorist study stages. Persist last-used settings
in `aorist_preferences`, and freeze session settings independently.

Use injectable randomness and Clock. Select active verbs/forms only. Query
eligibility from the schedule for **that form and exercise type**.
Reuse existing category/priority rules: DUE before NEW before LEARNING before
MASTERED in mixed queue; restrict exactly for new or due queues.
Within equal priority, spread selected persons where possible and shuffle with
injected randomness. Do not force a balance that replaces actually due targets
with ineligible ones. Document chosen tie-breaking with fixtures.

A form/verb-person slot appears at most once per session. Different persons of
one verb may appear; this is deliberate conjugation practice, unlike “one aspect
pair once.” If forms are fewer than requested, preview and offer a shorter
session with explicit consent. Never pad by repeating targets or invent
missing forms. Zero eligible targets shows a clear empty state and no session.

### Typed recall

Before submission/reveal show dictionary Bulgarian, supplied Russian meaning,
requested tense/person and input; hide the target, accepted arrays and revealing
examples from both visible and accessibility UI. Explicit submit validates and
commits once, then shows raw answer, result, approved variants, notes/examples
and “Дальше”. No automatic advance from feedback.

### Multiple choice

Prefer reviewed distractors if actually provided by the source. Otherwise the
proposed offline planner may sample **existing approved aorist surface forms**
from other persons of the same verb. It must not fabricate conjugated strings.
If that is insufficient, use only explicitly documented/reviewed distractor
sources; do not silently introduce unrelated vocabulary.

Aim for four options. Remove every option whose normalized text is in the
target's accepted set before selecting distractors; deduplicate all displayed
normalized options. Cross-person identical forms are common and cannot be
used as “wrong” distractors. Include one target-approved option, shuffle once,
and store stable option IDs/texts/order plus which IDs are correct in the
private snapshot. The public pre-answer state must not expose correctness flags.

If four distinct options cannot be made, explicitly offer the available two
or three; if fewer than two, exclude that target from CHOICE and explain the
shortage. Do not secretly switch to typed practice or duplicate option strings.
Availability counts must reflect viable choice construction, not just form count.

Use accessible radio-button selection and a separate “Проверить” action.
Validate option membership serverlessly inside the repository; grade by the
persisted option ID/approved snapshot, not arbitrary client text or position.
Changing selection before submit is allowed; changing it after commit is not.

Seeing alternatives is intrinsic to CHOICE, so its correct answers count as
**recognition**, never typed recall. Explicit reveal records REVEALED for either
type. A choice's presence on screen is not itself a revealed attempt.

## 8. Durable session lifecycle and atomic actions

Mirror existing Room-backed aspect sessions, but use separate entities.
Persist selection/order, target/person/type, all learning/reference/alternative
snapshots, normalization/planner version, choices/order, raw submissions,
outcomes, reveal and acknowledgement, current position, revision and status.
Content edits/archives after start do not change existing questions or grading.

Persist typed/choice drafts per question on background work, guarded by
question/session identity and revision. SavedStateHandle may support immediate
recreation, but Room is the source after app restart/process death. Define and
test flushing on navigation/lifecycle and before submission; if debouncing,
document the possible last-keystroke loss under abrupt kill. Late draft callbacks
must not overwrite committed submissions or another question's input.

Start is idempotent by UUID plus canonical settings. In one transaction,
submit/reveal verifies contentEpoch, session status, current question, revision
and existing attempt, then writes exactly one attempt, progress and schedule,
and updates session revision/terminal status. Keep explicit Next/acknowledgement
as a separate idempotent transaction; it must never regrade or reschedule.

A unique question-attempt constraint is the final defence against races.
An identical retry returns the persisted result; a conflicting retry, unknown
choice, stale callback, later-question submission or completed-session answer
is rejected without extra writes. UI busy guards improve usability but cannot
replace DB constraints. Answer/reveal races have one terminal winner.

Explicit reveal is terminal and cannot later earn correct credit. Proposed
scheduling effect, matching the existing Android aspect policy rather than an
unavailable aorist web implementation: write REVEALED, increment review-event
and reveal counts, keep correct unchanged, record scheduler failure (streak0,
next review24 hours later) for that form/type only. Merely viewing a question
or playing visible source audio creates no attempt.

Normal completion shows results and mistake/reveal review after final feedback
and Next. Early finish requires explicit action, persists ABANDONED/end time,
keeps already answered attempts and leaves skipped items without progress updates.
Back/navigation pauses; it does not silently abandon or submit. Resume an
unfinished session from Room after navigation, Activity recreation, DB reopen,
restart and process death, including feedback waiting for Next.

Do not recompute historic outcomes when loading a session, importing new answers
or restoring a backup. Store/validate policy versions so a later normalizer
change cannot reinterpret past facts.

## 9. Independent schedules and statistics

Reuse `ReviewScheduler` unchanged, with injectable Clock:

- successful intervals: 1, 3, 7 days, then doubled from at least7, capped365;
- failure/reveal: 1 day, streak reset;
- mastery: at least5 consecutive correct and interval at least28 days;
- due: nextReview <= now; no schedule/zero attempts is NEW;
- “day” is Duration.ofDays(1), exactly24 hours, not local midnight.

Persist UTC instants. Device clock is authoritative offline; timezone is used
only to display and group local recent activity. Handle DST, exact due boundary,
timezone changes and backwards clock corrections without rewriting prior
timestamps. Distinguish these device-clock semantics from a web server clock.
No invented aorist-specific interval algorithm.

Schedule key: **formId + TYPED/CHOICE**. First-person typed success must not master
another person, the other exercise type, the related aspect verb, vocabulary,
flashcards, sentences or aspect pairs. No conversion of recognition into recall.

Add an aorist-only statistics DAO/repository/screen:

- answered = CORRECT + INCORRECT; show correct/incorrect/revealed separately;
- choice and typed counts, accuracy and due targets are separate, labelled;
- skipped/no answer and just viewing a question contribute no answered event;
- due targets count active verb/form schedules, with explicit per-type units;
  if an overall unique-form count is shown, deduplicate forms due in both types;
- historical attempts remain visible for archived verbs and unfinished/abandoned
  sessions; archive removes future eligibility/due visibility, not history;
- completed sessions are counted by distinct persisted session IDs;
- performance by person and difficult forms can use real persisted attempts;
  zero/loading/error must be distinct, with no fabricated totals.

Use DB invalidation and clock/manual/on-resume refresh for time-sensitive due
counts. Aggregation must not multiply attempts through answer/choice joins.
Do not append aorist rows to existing legacy statistics or mix CHOICE accuracy
into a label implying independent recall.

## 10. Native navigation, accessibility and TTS

Add a dashboard entry “Аорист”, with a dedicated section and routes for
management/detail/edit, import preview, help, practice and statistics.
Reuse the current section pattern and top Back; do not add a sixth bottom tab.
Include the aorist route prefix in the existing bottom-bar visibility policy.

Keep Russian UI, theme and lifecycle-aware StateFlow collection. Use scrolling
forms/question screens with IME insets, appropriate keyboard submit actions,
large-font layouts, TalkBack labels/live feedback, meaningful empty/loading/
retry states and accessible choice groups. Never reveal correctness through
test tags used as semantics, hidden text, content descriptions, TTS registration
or a pre-answer feedback model.

Help explains aorist vs present dictionary forms, person/number and the
distinction from aspect: tense conjugation of чета and прочета is not a transfer
between aspect partners. Do not teach the false rule that only perfective verbs
have aorist. Avoid guessed linguistic explanations when dataset content is absent.

Reuse the Activity-owned Bulgarian TTS coordinator and add aorist policy targets:
explicit playback for visible dictionary source immediately; target reference/
approved alternatives only after submit/reveal. In choice practice, keep audio
for the answer reference until feedback; no hidden correct-option audio cue.
Never speak a wrong raw submission as the approved example.
Normal/slow playback replaces current audio and stops on question/route changes.
Missing/offline-unavailable Bulgarian voices show existing setup guidance and
never block training. No autoplay or background network fallback.

## 11. Full backup compatibility

Content JSON export remains separate from the full backup.

The proposed Room10 release writes **full-backup v4/schema10**. Add every aorist
table and auto-ID sequence, including accepted answers, metadata/examples/source
context, receipts, preferences, session settings and snapshots, choices and
drafts, attempts, progress and schedules. Include any relationships introduced
after dataset audit. Update summary/UI to name aorist counts and source version.

Keep original contracts intact for v1/schema7, v2/schema8 and v3/schema9.
Authenticate each original exact table/sequence/column inventory and checksum
**before** expanding into a new staging representation. Never relabel a v4
document as v3 or ignore extra tables/fields. Reject unsupported combinations,
future versions, unknown policy versions and malformed relationships clearly.

Older backups contain no aorist data. Their restore remains **complete
replacement**, not merge:

| Original backup | Restored aorist state | Other existing behavior |
| --- | --- | --- |
| v1/schema7 | All aorist content/history/settings empty/default | Original vocabulary/sentence/older modes retained from backup; no aspect content or practice. |
| v2/schema8 | All aorist content/history/settings empty/default | Its aspect content restored, without aspect-practice history, as today. |
| v3/schema9 | All aorist content/history/settings empty/default | All existing aspect content/practice and legacy data restored. |
| v4/schema10 | Exact saved aorist graphs, schedules and settings | Exact full supported snapshot. |

Preview **and confirmation dialog** must explicitly warn that restoring v1–v3
replaces/removes current aorist data and unfinished aorist sessions. Show both
incoming counts and replacement consequences. Offer creating a full backup
before replacement; do not silently retain current aorist rows against an
older snapshot. Old app versions reject v4; document that backup downgrade is
unsupported rather than dropping aorist fields.

Preserve existing guarantees:

1. A coroutine transaction reads all live tables and sqlite_sequence via SQL,
   yielding one consistent logical snapshot including committed WAL data.
   Never copy only an open .db file.
2. Read/bound/freeze chosen bytes; validate format/version/application/checksum,
   rows/types/IDs/FKs/answer ordering/hashes/options/persons/enums/settings,
   session progression and counters in an isolated staging Room database.
   Validate original source version before expansion. No live mutations.
3. Show summary and explicit replacement confirmation using the frozen prepared
   document, not a second read of a changeable URI.
4. Restore parents/children and high-water sequences in one live transaction;
   run integrity/FK and semantic validation; any failure rolls back every mode.
   contentEpoch invalidates previously queued writes and UI refreshes safely.
5. Preserve IDs, timestamps, raw submissions, outcomes, snapshots and schedules;
   do not regrade/replay attempts to “rebuild” history. Validation checks consistency
   without awarding new credit.
6. Test late-table failure after old rows were deleted, cancellation, WAL
   concurrency and same-number IDs after replacement. Backup limits remain
   explicit; do not raise dependencies or size limits without measured need.

## 12. Verification and delivery stages

### Stage A — evidence gate

Obtain the actual attached JSON and complete section1's schema/content audit.
If web aorist source becomes available, pin the exact commit, inspect migrations,
planners/evaluators/controllers/UI/tests/dataset and map each intentional rule.
Document confirmed/suspected bugs and intentional Android differences.
Do not implement a parser or advertise parity while this gate is unresolved.

### Stage B — content and additive persistence

Implement dedicated content entities/DAOs/repository, migration and management.
Test real dataset count/IDs (expected40 only if actual inspection supports it),
all available slots/alternatives/metadata/examples, repeat import with stable IDs,
changed explicit selection, stale preview and namespace collisions.
Verify BOM/Cyrillic/multiline JSON strings, malformed UTF-8/JSON, duplicate keys/IDs,
unsupported version, unknown fields, wrong types, primary/accepted mismatch and
bad examples. Inject a failure in a late insert and prove full rollback.
Export/import into a fresh DB must preserve canonical learning content and identity,
without creating progress. Test edits/archive/restore with retained history.

Migration tests should create real exported schema9 databases containing
vocabulary, translations, sentences, all old modes, aspect content/active sessions/
attempts/progress/settings and sequence high-water marks; migrate to10 and reopen.
Compare all original rows/IDs/sequences, not just table existence. Exercise older
supported migration chains. Export/verify schema10 in version control.

### Stage C — practice, recovery and schedules

Test both TYPED and CHOICE for every available person category. Use the two
user-specified first-person examples and actual dataset alternatives after audit.
Verify exact normalization, stress ordering, particles, и/й/ѝ and Latin lookalikes,
no fuzzy/substrings and wrong-person answers.

For choices test deterministic randomness, distinct normalized options, a single
valid answer, homographs across persons, accepted alternatives not used as wrong
options, stable order/IDs across reopen, too-few-distractor messaging, tampered
option IDs and zero/short pools. Unique target slots; no duplicate padding.

Exercise rapid double taps, identical/conflicting retries, answer/reveal races,
stale Next, late draft writes, completed sessions, and injected transaction
failure. Assert one attempt and schedule update, with correct position/status.
Persist/reopen actual file-mode Room DB mid-question, draft, feedback and final
Next; edit/archive content while a session exists and verify original grading.
Test navigation/recreation and real process-kill separately.

Fixed/randomized Clock histories verify existing intervals, exact due boundary,
failure/reveal, DST/local grouping, backwards time and independence by verb,
person and exercise type. Existing modes' stored rows and metrics must remain
byte-for-byte/logically identical under aorist actions.

### Stage D — backup/statistics and UI integration

Test v4 round-trip with active typed and choice sessions, drafts, feedback,
snapshots, source metadata/examples, archived forms, preferences and all progress.
Close/reopen after restore; retry old actions without duplicate counts.
Test original v1/v2/v3 checksum validation and explicitly confirmed replacement,
future/relabelled/extra fields, broken FKs/options/hashes/counters and late restore
failure. Existing data must be unchanged after invalid preparation or failure.
Verify a consistent snapshot while another committed WAL transaction writes
related content/progress. Keep existing backup regression tests.

Statistics fixtures must independently count typed/choice correct/incorrect/
revealed/abandoned/resumed/archived events, shared-form due deduplication and exact
time boundaries. Test DB invalidation/loading/failure/retry without fake zeros.

Compose/Robolectric tests cover navigation/back/resume, fields, import confirmation,
choice selection, busy guards, help, semantic answer hiding and approved-only
TTS. Fake TTS tests cover policy/lifecycle, not actual voice quality.

### Commands to run during implementation (not executed by this planning task)

Use the actual project wrapper and existing CI rather than changing tool versions:

```sh
python3 parity/verify_web_parity.py
python3 parity/verify_aspect_parity.py
python3 -m unittest discover -s scripts -p 'test_*.py' -v
./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest
./gradlew --no-daemon --stacktrace --console=plain assembleDebug lintDebug
./gradlew --no-daemon --stacktrace --console=plain assembleRelease lintRelease
python3 scripts/verify_apk.py app/build/outputs/apk/debug/app-debug.apk --signing debug
python3 scripts/verify_apk.py app/build/outputs/apk/release/app-release-unsigned.apk --signing unsigned
```

New aorist suites should be part of testDebugUnitTest; do not replace or bypass
existing migration/parity tests. Verify the exported schema guard includes10.
Run instrumentation only with configured runner/tests and an available device/
emulator, e.g. connectedDebugAndroidTest after adding meaningful device tests.
No existing instrumentation setup or successful device run is assumed.
Use existing GitHub Actions if local Gradle/SDK networking cannot build.

Physical checks: real Bulgarian keyboard/IME, TalkBack pre-answer concealment,
large fonts/small screens, rotation/background/navigation, force-stop/restart
with unfinished typed and choice sessions, all actions/import/export/backup in
airplane mode with local documents, missing and installed offline Bulgarian TTS,
and in-place migration/update without loss. Fake adapters and Robolectric
cannot verify offline voice availability or pronunciation quality.

This plan does not deliver a new APK, change versionCode or generate keys.
A later installable update must exceed current code6 (and re-check repository
version first), preserve applicationId and use the existing persistent-key
release workflow. Preserve a full backup before update; do not recommend
uninstalling a valuable existing installation to bypass a signature mismatch.

## 13. Checks actually performed for this planning change

- Confirmed GitHub integration access and exact Android/web refs.
- Inspected full, non-truncated repository trees and actual pinned Kotlin,
  Compose, Room, backup, scheduler, statistics, TTS, Gradle and relevant test sources.
- Read web AGENTS and inspected actual web entry point, mode enum, aspect
  implementation/normalizer/scheduler and integration tests.
- Checked available feature refs/tags and an aorist PR search; no aorist source
  or dataset located on those refs.
- Checked local workspace availability; clone failed once at the shell proxy;
  continued via connected GitHub tools without treating it as an auth failure.
- Could not inspect/parse the named attachment: actual bytes are unavailable.
  No dataset schema/count/round-trip/parity claim is made.
- Only this documentation file is to be committed on the feature branch.
  No Android code/build/schema/version/signing/web changes, app build, unit/
  instrumentation run or physical-device check is claimed for this task.

Before implementation, resolve the dataset gate, refresh refs/AGENTS/current
Room and backup versions, and amend decisions where actual evidence differs.
