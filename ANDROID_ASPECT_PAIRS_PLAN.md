> **Integration status:** separate statistics and backup compatibility review are
> now implemented; ASPECT_INTEGRATION.md supersedes the original future-stage
> proposal below. Room9/full-backupv3 remain compatible and unchanged in shape.

> **Current implementation status:** storage/management/import/export and typed
> practice are implemented. Room9 adds five practice tables to existing24, for29;
> full backup v3/schema9 includes19sequences and reads original v1/v2. The original
> planning proposal below is historical; ASPECT_PRACTICE_ANDROID.md and
> BUILD_REPORT.md document actual scope/checks. Separate aggregate aspect
> statistics are implemented; see ASPECT_INTEGRATION.md. Later instructions authorize
> the application changes; the original planning-only scope below is historical.

# Android Verb Aspect Pairs implementation plan

Planning date: 2026-11-01. **Documentation only; no application code, database,
build configuration, dependency or version change is authorized by this document.**
Feature label: **Видовые пары**. This is Bulgarian typed aspect recall, not
person conjugation, Russian-answer typing, or sentence translation.

## 1. Reference provenance, access and missing material

Authenticated GitHub integration access to both repositories was confirmed.
All remote heads and full recursive trees were inspected, not GitHub page summaries.

| Repository/ref inspected | Exact commit |
| --- | --- |
| Read-only web feature branch `codex/aspect-pairs` — reference used throughout this plan | `be6a91ff2ae06b8b0eae2461d8c7a8bf910ce57a` |
| Read-only web default branch `master` | `2792dc3e4cb5d4762783140729e2c37ccac95567` |
| Android implementation branch `codex/android-foundation-parity` — starting target | `c78efa013d6b97f9e3f7133b60d33ca470e74159` |
| Android default `main` — README baseline; do not merge | `d18ad8814b391c60b0e1ec2099a5c2251978b2c6` |

Web matching-refs lists those two heads and no tags/other refs. The master commit
has the feature commit as its second parent; their complete recursive tree entries
are identical. Thus this feature is actually merged into the current web default
branch; that was verified rather than assumed. Pin the feature commit above for
Android fixtures; do not advance the old legacy-mode parity pin silently.

Web `AGENTS.md` was read at the pinned commit (Russian UI, English docs, no
automatic imports, no personal-database tests, preserve architecture/data).
Its historical initial-milestone scope does not describe current feature absence.
No AGENTS.md is present anywhere in the Android recursive tree. The managed local
executor was still starting during inspection; shell/local checkout/ancestor
instruction checks were unavailable. Source inspection used actual pinned GitHub
file contents. This environment limitation does not imply missing GitHub access.

Found original learning material:
`learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json`,
Git blob `c29cd6d9aac696b37593a4b8d2f0f4f1443b80f4`.
The web review reports its SHA-256 as
`a6354b52aec47626a9eb32ffa9548bc79ddd67931b072364da9e68409d80e2b6`;
that SHA-256 was not independently recomputed in this planning task.

**Missing source:** `aspect_pairs_app_guide.md` is absent from both available web
ref trees and the Android tree. Web `ASPECT_PAIRS_PLAN.md` cites the original
`C:\Users\Danish\Downloads\aspect_pairs_app_guide.md`; that external attachment
is not accessible here. Its original instructions cannot be independently audited.
Do not describe that guide as read, copy a Downloads path into the app, or invent
its missing content. The completed implementation, tests, dataset and tracked docs
are sufficient for this source-based plan. Obtain the original guide before
claiming complete guide parity. The referenced relation-definition TXT/complete
original presentation are also not supplied here; no linguistic certification.

## 2. Actual source map

Web paths are relative to the pinned web commit. Java prefix below is
`src/main/java/com/example/bulgariantrainer/`; tests use the corresponding
`src/test/java/com/example/bulgariantrainer/`.

| Contract | Actual files inspected |
| --- | --- |
| Management/identity/validation | `service/AspectPairService.java`, `AspectPairValidation.java`; `entity/AspectDataset.java`, `AspectPair.java`, `AspectPairAnswer.java`; `dto/AspectPairDtos.java`; `controller/AspectPairController.java` |
| Strict JSON and transactional import | `service/AspectPairJson.java`, `AspectPairImportParser.java`, `AspectPairImportService.java`, `AspectPairImportStore.java`, `AspectPairImportWriter.java`; dataset/pair/receipt repositories |
| Evaluation/planning/lifecycle | `service/AspectAnswerNormalizer.java`, `AspectQuestionPlanner.java`, `AspectPracticeService.java`; `dto/AspectPracticeDtos.java`; session/question/attempt/progress entities and repositories |
| Independent metrics | `service/AspectStatisticsService.java`, `dto/AspectStatisticsDtos.java`, `repository/AspectAttemptRepository.java`, `AspectProgressRepository.java` |
| Schema/constraints | `src/main/resources/db/migration/V7__aspect_pair_management.sql`, `V8__aspect_pair_practice.sql` |
| Browser behavior | `src/main/resources/static/js/aspect-{common,management,import,practice,statistics}.js`; aspect page; no new aspect TTS |
| Documentation/material | `ASPECT_PAIRS.md`, `ASPECT_PRACTICE.md`, `ASPECT_PAIRS_PLAN.md`, `ASPECT_PAIRS_REVIEW.md`; learning-material directory/README/JSON |
| Tests | `AspectPairIntegrationTests.java`, `AspectPairPersistenceTests.java`, `AspectPracticeIntegrationTests.java`, `AspectPracticeRecoveryTests.java`, `AspectFeatureUpgradeTests.java`, `service/AspectQuestionPlannerTests.java`; browser/restart JS tests |

Android actual files inspected at the starting commit:
`WEB_PARITY.md`, `IMPORT_EXPORT.md`, `REVIEW_AND_STATISTICS.md`,
`BACKUP_RESTORE.md`, root/app Gradle files; and under
`app/src/main/java/com/danishbears/bulgariantrainer/`:
`data/{TrainerDatabase,ContentTransaction,TrainerPreferences,VocabularyRepository}.kt`,
`transfer/ContentTransferRepository.kt`, `typing/TypingRepository.kt`,
`statistics/StatisticsCalculator.kt`, `ui/TrainerApp.kt`,
`pronunciation/PronunciationPolicy.kt`, `backup/{BackupFormat,BackupValidation}.kt`.
These establish existing patterns; do not reconstruct the app.

## 3. Learning rules to preserve

- Pair identity is immutable **namespace + external ID**, with separate numeric
  internal IDs. Namespace matches `[a-z0-9][a-z0-9._-]{0,99}`; identity is
  case-sensitive and includes archived rows. Duplicate verb spellings alone are
  not an identity collision. No word rows, translations or implicit links created.
- Preserve both Bulgarian citation forms (first-person singular present), both
  Russian meanings, ordered accepted arrays, topic, independent stage 1–3 and
  relation core/resultative/bounded, note, provenance and optional paired examples.
  Meaning-specific resultative/bounded relationships are not universal synonyms.
- Search source/target forms, accepted alternatives, both meanings, topic and
  external ID with existing VocabularyText-style search normalization (accents
  significant), literal %, _ and ! escaping, combined namespace/stage/topic/
  relation/archive filters before bounded Paging. Default active, stable ID order.
- Manual create/edit/archive/restore; optimistic row version required for changes;
  unchanged save is a no-op. Reimport/edit must retain IDs, archive status, existing
  progress and old question snapshots. New meanings should receive a new identity,
  not a silent progress reset. No destructive delete in the first implementation.
- Fresh practice defaults: size20 (1–100), stages[1] only, MIXED direction and
  MIXED scope; optional exact topic/namespace. Explicit selection enables stages2/3.
  Canonicalize stage ordering; reject empty/repeated/out-of-range stages.
- Direction: IMPERFECTIVE_TO_PERFECTIVE or PERFECTIVE_TO_IMPERFECTIVE; MIXED plans
  actual directions, never a third progress direction. Prompt shows source
  Bulgarian, source Russian meaning, target Russian meaning and instruction.
- No repeated pair within a session. Availability reports distinct eligible pairs,
  forward/reverse counts and offered size. Require explicit shorter consent/new
  start UUID when insufficient; empty queues create no session.
- Port the **aspect-specific bounded assignment planner**, not the Android legacy
  greedy typing planner: nearest feasible forward/reverse quotas first, then
  minimum total scheduler priority, random tie-break and final shuffle. Odd extra
  direction is random only when priorities tie. DUE-only eligibility may force
  imbalance. Inject Kotlin Random; preserve semantics, not Java RNG byte sequences.
  Translate Java21 Math.clamp to Kotlin coerceIn; no JDK/Android API upgrade needed.
- Before outcome hide target Bulgarian/accepted arrays, notes, examples, provenance,
  external IDs and remaining snapshots from practice UI state/accessibility/audio.
  Local Room can hold them internally; single-user content management remains open.
- Submit once; show original text, result, approved target answers and saved content.
  Incorrect feedback: **Не совпало с ответами этой карточки**. No claim of grammatical
  impossibility, fuzzy correctness, AI assessment or manual sentence self-rating.
- Reveal is terminal REVEALED: null submitted text, no correct credit, failure
  schedule in that direction. A correct answer typed after reveal cannot earn credit.
  This differs intentionally from flashcard reveal, which does not schedule by itself.
- Feedback remains until explicit Next. Last outcome completes the session but
  final feedback remains recoverable until acknowledgement. Early completion is
  ABANDONED if any unanswered questions remain; unanswered results are SKIPPED,
  with no attempts/progress changes and still-hidden Bulgarian targets.

## 4. Native UI and architecture

Add a **Видовые пары** dashboard entry and dedicated navigation graph without
overcrowding the existing bottom bar. Within it: Мои пары, Импорт/экспорт,
Практика, Статистика, Как заниматься. Compose/Material3, Russian text, paged lists,
loading/empty/error states and retries; wrap controls/scroll forms on small screens.

Use domain DTOs, DAOs, AspectPairRepository, AspectImportRepository,
AspectPracticeRepository and AspectStatisticsRepository, route-scoped ViewModels,
SavedStateHandle for UI drafts and collectAsStateWithLifecycle. Coroutines/Room
suspend work off the main thread. Reuse contentEpoch/withContentTransaction so
old operations cannot target overlapping IDs after a full restore. Validate filters
and editor drafts before transaction entry; recheck authoritative versions inside.

Persist confirmed training defaults separately; fresh stage1 is the fallback,
while resumed session settings are authoritative. Keep defaults in full backup.
Busy flags prevent rapid actions; repository identity/uniqueness/transactions
provide correctness even when delayed callbacks or multiple ViewModels race.
Capture rendered session/question IDs and exact submitted text; do not let an old
callback answer a later question. IME Done is submit only before feedback, Next
after feedback; do not treat held Enter or composition as multiple answers.

The web aspect section adds **no TTS**. Preserve that for v1; leave existing-mode
TTS and its visibility policy intact. If audio is separately approved later,
source Bulgarian may play immediately, target/examples only after outcome; only
approved snapshot text, installed local Bulgarian voice, stop on question/route/
background change, no progress effects. Do not silently add target pronunciation.

## 5. Additive Room migration proposal

Current Android Room schema is **7**, 20 tables; preserve applicationId
`com.danishbears.bulgariantrainer`, database name `bulgariantrainer.db` and all
existing rows/IDs/sequences/migrations. Web Flyway V7/V8 numbers are unrelated to
Android Room versions. Propose one exported **MIGRATION_7_8**, additive only:

| New table | Keys/data |
| --- | --- |
| aspect_datasets | Generated ID; unique namespace; original last-import root metadata JSON, raw-file revision hash, version, timestamps |
| aspect_pairs | Generated ID; dataset FK; unique(dataset_id, external_id); all content, archive/version/timestamps; content hash/search key; per-entry source-definition context for safe later export |
| aspect_pair_answers | Generated ID; pair FK; side, 1-based position, display text, normalized key; unique(pair,side,position) and unique(pair,side,key) |
| aspect_import_receipts | Request UUID PK, unique preview/import UUID, dataset FK, exact request hash, file hash, metadata and committed summary |
| aspect_sessions | Generated ID; unique start UUID; canonical settings, requested/actual size, position, durable revision, status and timestamps |
| aspect_questions | Generated ID; session/pair FKs; unique(session,position), unique(session,pair); actual direction, full immutable snapshot/hash, acknowledged/revealed state |
| aspect_attempts | Generated ID; unique question FK; original nullable answer, normalized answer where typed, CORRECT/INCORRECT/REVEALED, timestamp |
| aspect_progress | Composite(pair,direction) PK; actual direction only; total reviews including reveals, correct/lifetime-success/reveal counters; never legacy word progress |
| aspect_review_schedules | Composite(pair,direction) PK/FK to progress; scheduler attempts/successes/streak/interval and last/next instants; updated atomically with progress |
| aspect_preferences | Singleton typed settings defaults; no seeded records; validate enums/stages/size/filters |

This splits web AspectProgress's scheduling fields to match Android's existing
counter/schedule architecture. Expected total:30 tables; six new AUTOINCREMENT
tables (datasets, pairs, answers, sessions, questions, attempts), taking sequence
coverage from13 to19. Confirm those counts against the actual generated schema,
not a handwritten Room identity hash.

Add indexes for active dataset/stage/topic/id, answer pair/side, session recovery,
question/session FKs, attempt uniqueness, schedule direction/next-review.
Enforce positive IDs, positions, valid enums/flags/ranges and FK restrictions;
archive rather than cascading away history. Where Room lacks CHECK annotations,
apply invariant validation consistently in repositories, migration SQL and backup
validation; test raw constraint behavior. No destructive fallback, seeding or
rewriting old progress. Export schema8 through KSP and test old1→8/7→8 paths.

## 6. Import and content export

Use SAF OpenDocument/CreateDocument, no broad storage permission or fixed path.
Explicit namespace suggested for supplied material: bulgarian-aspect-pairs-100.
If bundled later, preserve original bytes/provenance and offer an explicit preview
button only; never import on startup or claim the file is proofread.

Separate aspect JSON parser from existing vocabulary TXT/CSV/sentence CSV:
strict UTF-8 with optional BOM; maximum1MiB; one JSON object; duplicate object keys,
trailing content, unknown fields, malformed Unicode/types and unsupported versions
reported with source filename and JSON path. Existing kotlinx.serialization-json
must be supplemented with duplicate-key/depth checks before tree construction;
do not assume ordinary parseToJsonElement rejects repeated keys. No new parser
dependency necessary. Require format=bulgariantrainer.aspect-pairs, integer
formatVersion=1, 1–1000 entries; optional bg/ru/citation entryForm constants,
ISO createdOn, sources/editorial metadata retained.

Mirror AspectPairValidation bounds: IDs/namespaces100, forms/answers255,
Russian meanings1000, topic255, note/example text4000, stages1–3, each side1–50
nonblank answers, no aspect-normalized duplicates and primary membership.
Presentation provenance requires sourceId/positive tableRow referencing a declared
source; added has no sourceId/tableRow. Non-null examples require both bg/ru sides.
Do not equate stage with relation or split semicolon-containing Russian meanings.

Whole-document validity gates confirmation: preserve invalid entries/paths for
diagnostics, but **no partial import of an invalid document**. Preview is read-only;
ADD defaults selected, UPDATE requires explicit old/new review and selection;
UNCHANGED may be explicitly included. Web preview is not editable: correct source
and re-preview, or clearly create a new revalidated preview if native editing is
later chosen. Never route through the vocabulary APPEND implementation.

Freeze preview content, selected IDs/expected versions and confirm UUID. In one
transaction validate dataset and selected-row versions, apply selected changes,
retain archive/progress/snapshots, and write durable receipt. Unknown/repeated
selection IDs, forged/stale versions or newly conflicting identities abort all.
Omitted entries survive. Exact UUID retry returns saved summary even after restart/
preview loss; changed payload conflicts. Uncommitted preview lifetime30minutes,
five-preview cap and process-loss reselect behavior are explicit.

Web has **no aspect export endpoint** (controller inspected). Native export is a
documented extension: one namespace per strict v1 JSON, original known root fields
plus entries in the same import format, explicit include-archived choice.
Do not emit internal IDs, namespace, archive flags, row versions or progress as
unknown v1 entry/root fields. Namespace is selected separately on reimport.
Export preserves all approved arrays/examples/source definitions and order.
Validate generated document with the same parser before writing. Empty or oversized
datasets cannot produce an allegedly importable empty/over-limit document; offer
explicit valid-size parts with metadata intact, or explain the limit. Content
export is **not** a full learning backup or byte-exact original-file reproduction.

## 7. Answer validation and parity fixtures

Dedicated AspectAnswerNormalizer; do not change VocabularyText or sentence matching.
Exact order: NFC → remove remaining U+0301/U+0300 → Unicode White_Space collapse/
trim → Locale.ROOT lowercase → final NFC. Preserve display/original submitted text.
This is lowercase, not unrestricted Unicode case folding. Keep се/си, й/ѝ/и,
punctuation and meaningful spelling; no transliteration, Latin lookalikes,
substring/fuzzy matching, inferred synonyms or generated conjugations.

Only selected-direction accepted snapshot array is authoritative. Planned fixtures
come directly from AspectPracticeIntegrationTests (not newly executed tests):

| Pair/direction/input | Expected |
| --- | --- |
| bgasp-057 forward купя; reverse купувам | CORRECT |
| bgasp-043 reverse чета or прочитам | CORRECT |
| bgasp-010 reverse изпявам | CORRECT |
| bgasp-021 forward опитам / опитам се | INCORRECT / CORRECT |
| bgasp-066 forward спомня / спомня си | INCORRECT / CORRECT |
| bgasp-047 reverse седя; bgasp-050 reverse стоя | INCORRECT |
| bgasp-057 forward куп, Latin-k kупя, купя!, купувам | INCORRECT |
| bgasp-057 forward whitespace + КУ + combining acute + ПЯ + NBSP | CORRECT |
| ѝ vs и; й vs и | distinct; decomposed и+grave matches ѝ, и+breve matches й |

Blank/normalization-empty or >4000-character submission is validation failure,
not a scored mistake. Raw-text identity controls retry: a different raw string
conflicts even when it normalizes to the same accepted key.

## 8. Durable sessions, scheduling and statistics

Persist order, snapshot, direction, filter settings, current position/revision,
submitted original text/outcome/timestamp, reveal, acknowledgement and status in
Room. Recover without SavedStateHandle/session-ID memory (offer latest unfinished
session, plus unacknowledged final feedback); keep explicit resume rather than
starting a replacement. Pending mutation descriptors/retry identity survive where
needed; an unconfirmed preview is not a durable practice session.

Every start/outcome/Next/complete uses a content transaction. Unique request and
question-attempt constraints, canonical settings, exact replay checks and monotonic
revision stop duplicate taps/races. Answer-vs-reveal has exactly one winner;
completed/foreign/future questions cannot receive new outcomes. Changed content/
archive after start cannot change evaluation of the saved snapshot.

Reuse the pure existing ReviewScheduler with injected Clock: correct intervals
1,3,7,14,28,56,112,224,365, then365 days; incorrect AND REVEALED reset streak,
preserve lifetime successes and retry after1day. The next success restarts at1day.
Mastery streak≥5/interval≥28; NEW with zero reviews, then DUE at next<=now or
missing deadline, then mastery/learning. Direction independent; MIXED includes
due/new/learning/mastered, scoped NEW/DUE exact. Never extend word ReviewMode by
pretending pair IDs are word IDs. One outcome transaction updates attempt,
progress, schedule and session, exactly once. Viewing/reading feedback does nothing.

Use device Clock.systemUTC by default, exact24-hour deadline durations, UTC
statistics dates if additional daily displays are later approved; human timestamps
may display local zone. Offline device time replaces web server time. Refresh due
availability on resume/manual refresh and existing minute cadence.

Separate AspectStatistics screen/repository, optional namespace only, matching web:
answered=CORRECT+INCORRECT; correct/incorrect/revealed distinct durable counts;
SKIPPED is no attempt. Direction totals use actual direction, no MIXED bucket.
History includes active/abandoned sessions and archived pairs; due excludes archive.
dueForReview counts distinct pairs, dueDirections counts direction schedules, so
two due directions on one pair count1/2. No-data accuracy is undefined; session
accuracy=correct/typed, rounded to one decimal. Preserve previous confirmed values
on refresh error; no fabricated zeros on unavailable state. Do not add these
attempts/reveals to legacy vocabulary size, global accuracy/activity/streaks,
completed-session aggregates or difficult-word lists without a separate contract.
Do not derive typed accuracy from scheduler totalAttempts, which includes reveals.

## 9. Full backup compatibility — mandatory before feature release

Current BackupFormat is strict **formatVersion1/schemaVersion7**, exact20 tables,
13 sequence entries, 32MiB/250000rows. Adding entities without updating this whitelist
would silently omit new learning data: this must block release.

Propose full-backup **v2/schema8** with all30 tables/19 sequences (confirm compiler
contract), aspect counters/settings/import receipts/source context and pending/
completed session snapshots included. Extend summary counts and full staging
semantic/FK/enum/JSON/session validation. Keep the existing logical transaction/WAL
snapshot and opaque validated preview/confirmation, atomic live replacement,
rollback and contentEpoch invalidation/fresh Activity task. Never copy an open DB.

Support existing v1/schema7 backups explicitly: validate their original checksum,
table/sequence sets and semantics under the old contract first; in isolated
staging migrate to8/add empty aspect tables/defaults without fabricated records;
then validate expanded data before showing the destructive-replacement summary.
Explain that restoring an old full backup replaces current data and contains
**no aspect history**; require confirmation, not silent retention/merge.
Preserve all legacy identifiers/high-water marks and settings. Unsupported future/
malformed backups fail before live mutation. Old application versions reject v2;
explain upgrade/downgrade limits and retain a pre-upgrade backup. Never relabel
v1 files or recalculate their checksum before authenticating their original payload.

## 10. Discrepancies and decisions; do not silently copy risks

1. **Missing original guide:** exact missing path and audited substitutes are in §1.
   Tracked web plan's quotations are secondary evidence, not the supplied guide.
2. **Review history is stale:** ASPECT_PAIRS_REVIEW says no remote push/default
   merge. Current Git refs/merge parents prove it is now merged. Preserve the
   reference SHA; do not infer current branch state from that historical report.
3. **Proposal vs final code:** ASPECT_PAIRS_PLAN lists proposed routes/metrics;
   actual controller/practice/statistics services and final docs take precedence.
   No web content-export endpoint or portable full-backup API exists. Android
   SAF export/full-backup work is explicit native adaptation.
4. **Accent wording:** broad “ignore stress” does not mean delete every accent.
   NFC-first preserves ѝ and also precomposed grave letters such as ѐ. Match
   implementation/tests; broader equivalence needs an explicit future decision.
   Search keeps accents even where answer comparison strips remaining stress marks.
5. **Dataset is not an exhaustive linguistic oracle:** audit found100 unique IDs,
   212 accepted strings, 16 paired examples, stages77/17/6, relations77/17/6,
   provenance56 presentation/44 added. Structural counts agree with final docs;
   all primaries occur in their accepted arrays and imported source references
   resolve. Stage/relation happen to correlate in this file; code does not enforce
   that correlation. Do not invent extra alternatives, certify grammar or
   automatically correct resultative/bounded relations. Independent teacher review
   remains necessary. No tested dataset/evaluator contradiction was found by the
   structural audit; this is not independent linguistic verification.
6. **Source-metadata retention risk (inspection-derived, not executed failure):**
   AspectPairImportWriter replaces dataset root metadata on each selected import
   while retaining omitted old pairs. A later file declaring only sourceB can
   leave an old presentation/sourceA pair without sourceA in current root metadata.
   Web has no export that demonstrates this failure. Reproduce with A→B import/
   omitted-old-row fixture before implementation. Android should keep each pair's
   source-definition context from its import plus original receipt metadata.
   Export merges compatible referenced source definitions, fails clearly on
   conflicting/missing definitions, and never fabricates or silently renames IDs.
7. **Known prior web UI creation-recovery defect is already fixed:** final browser
   code searches all matching active/archive pages after a lost POST response;
   review documents its regression. Native exact identity lookup/transactional UUID
   retry must not reintroduce a first-page-only duplicate check.
8. **Planner is different from legacy typing:** do not copy the documented older
   greedy typing imbalance/priority defect into this bounded aspect planner.
   Preserve closest feasible quotas before priority; test exhaustive small pools.

No newly executed web integration failure is claimed. Source-review risks above
are distinguished from the confirmed fixed defect and missing-source blockers.

## 11. Tests, device checks and implementation stages

Future automated checks (not executed by this planning-only task):

- Parser: actual100-entry fixture field-for-field round-trip; strict UTF-8/BOM,
  JSON object duplicate keys, trailing documents, unknown fields, empty/oversized
  arrays, numbers vs strings, invalid provenance/examples, stage/relation
  independence, primary membership, empty stress-only answers and bounds.
- Import: preview zero writes, whole-file rejection, explicit selected updates,
  namespaces/archived identity, no restore/delete of omitted rows; stale dataset/
  pair versions, exact UUID retry after preview loss, changed UUID payload rejection,
  concurrent confirm, SQL rollback and sourceA/sourceB metadata retention fixture.
- Planner/evaluator: fixtures in §7, seeded randomness, default stage1, all filters,
  no duplicates, explicit shortages, exhaustive small eligible pools vs brute-force
  optimum, size1/odd/100, forced direction, priority ties and infeasible15/15.
- Practice: answer/reveal/Next/complete retries and opposite races, completed/future/
  foreign question guards, one attempt/schedule update, immutable content after
  edit/import/archive, hidden unanswered/skipped UI state, reveal scheduling and
  exclusion from typed accuracy, final feedback acknowledgement.
- Persistence: real file database close/reopen, new repository/ViewModel without
  saved pointer, pending raw response recovery, all statuses/settings/order;
  legacy schemas1–7→8 with populated old modes/counters/queues unchanged and no
  seeded aspect content; compiler-exported schema8 contract.
- Scheduler/statistics: exact web histories, independent directions and legacy
  mode isolation, NEW/DUE/MIXED, one-day reveal/failure, UTC/DST boundaries with
  injectable Clock; real outcome aggregates, archive/history vs due1/2 counts,
  no resumed-session duplication, empty accuracy and refresh errors.
- Backup: v1→new staged restore, v2 round-trip with all entities/IDs/sequences/
  receipts/settings/progress, pending/final feedback recovery, unsupported future
  versions, corrupt checksums/JSON/FKs, failures after deletes and stale old UI
  commands leave live data intact or correctly rejected.
- Regression/CI: existing JVM suite plus new meaningful tests, pinned web
  normalizer/planner/scheduler fixtures, lintDebug/lintRelease, assembleDebug/
  unsigned assembleRelease and APK checks. Do not call a configured test a pass.

Physical/emulator acceptance later: SAF local/cloud-provider cancellation and
errors; Cyrillic IME/composition/held Enter; rapid submit/reveal taps; actual process
force-stop at hidden/outcome/final-feedback stages; airplane mode after explicit
local import; rotation,320dp/landscape/large fonts/TalkBack; no hidden answers via
UI/accessibility/audio; large dictionaries/history memory; failed/confirmed backup
replacement; same-key higher-version APK update preserving old and aspect data.
Existing global Bulgarian TTS device checks remain separate; no new aspect audio.

Suggested delivery gates, each reviewed/tested before proceeding:
1. Obtain missing guide if full guide parity is required; approve explicit risk
   resolutions, pin web fixtures and preserve a pre-upgrade full backup.
2. Implement additive storage/validation, management and strict import/content export.
3. Implement planner/evaluator/durable practice with isolated progress/schedules.
4. Add separate real statistics and persisted defaults.
5. Complete v2 backup/v1 compatibility before any aspect-containing build is used
   for personal data; run regression/lint/APK checks and device/update acceptance.
Preserve existing Kotlin2.1.20, Compose/Material3, Room2.7.2, AGP8.9.2,
Gradle8.11.1, Java17 targets and SDK35 unless actual evidence requires change.
Increment application versionCode for a subsequently distributed implementation,
using the existing persistent signing workflow; no key/credential work in this task.

## 12. Actual verification for this planning task

Executed through GitHub integration: repository permissions/default branches,
all available refs, full recursive trees, merge parents, exact source/docs/tests/
migration/dataset reads, Android source inspection. A JavaScript structural dataset
audit counted100 distinct IDs/212 answers/16 examples, verified normalized primary
membership, duplicate-answer absence and source-reference resolution; no issues
in those checks. This audit is not the Java/Kotlin evaluator test suite.

No Maven/Gradle, JVM unit/instrumentation tests, lint, APK build, live server,
browser or device checks were executed in this task. The web review's historical
165-test pass and the Android BUILD_REPORT's historical257-test pass were read,
not rerun and not evidence of a newly implemented aspect mode.
The managed shell environment was unavailable while starting, so no local checkout
or network/build success is claimed. Only this plan is to be added to the existing
Android feature branch. Do not change the web repository or merge either default.
