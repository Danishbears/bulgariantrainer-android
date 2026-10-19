# Offline Russian-to-Bulgarian sentences

## Actual reference and mapping

Read-only web reference: `Danishbears/bulgariantrainer`, `master` at
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`. Actual checked-out code, migrations,
DTOs, browser UI and tests were inspected. Java paths below use
`src/main/java/com/example/bulgariantrainer/`; tests use the corresponding
`src/test/java/com/example/bulgariantrainer/` prefix.

| Web source/test | Android implementation |
| --- | --- |
| `service/SentenceService.java`, `dto/SentenceDtos.java`; management/unchanged-alternative tests | `sentences/SentenceRepository.kt`, `SentenceModels.kt`, `data/SentenceDao.kt` |
| `service/SentencePracticeService.java` start/answer/selfRate/next/complete/results | `SentencePracticeRepository.kt`, `SentencePracticeViewModel.kt` |
| `service/VocabularyText.java`, `service/TypingNormalizationTests.java` | Existing shared `data/VocabularyText.kt`; identical NFC/whitespace/ROOT-case keys |
| `entity/SentenceQuestion.java`, `StudyAttempt.java`, `SentenceProgress.java`; Flyway V5/V6 | Four sentence-practice tables; raw answer, immutable automatic match and optional manual rating; independent sentence counters |
| `repository/SentenceRepository.java` trainable query | Approved/unarchived selection, injected Random, bounded unique snapshot queue |
| `src/main/resources/static/js/sentences.js` | `SentenceManagementScreens.kt`, `SentencePracticeScreen.kt`, lifecycle ViewModels |
| `SentenceIntegrationTests.java` alternatives, PENDING/self-assessment, snapshots, retry and early completion | `sentences/SentenceRepositoryTest.kt`, `ui/SentenceViewModelTest.kt` |
| Prior Android imported sentence schema v4 | Non-destructive `MIGRATION_4_5`, exported v5 schema; `SentenceMigrationTest.kt` |

Android main paths use `app/src/main/java/com/danishbears/bulgariantrainer/`.
Existing dictionary/import/export/flashcard/typing work, applicationId, Kotlin,
Compose Material 3, Room and build tool versions are preserved. No backend, AI,
automatic sentence generation, startup import or additional permissions exist.

## Management and training

From **Главная**, open **Предложения**. The paged active/archive list supports
adding, editing, archiving and restoring sentences; **Перевод предложений** opens
training settings and recovers the last unfinished session from Room.
Russian source and Bulgarian reference are required, each at most 4000 characters.
Add up to 50 individually editable approved alternatives, optional explanation
(up to 4000), optional difficulty 1–5 and optional topic (up to 255). Saving
explicitly approves the reference and alternatives; it does not invent translations.
A normalized Russian duplicate, including an archived entry, is rejected. Duplicate
alternatives/main answers after normalization are rejected. Unchanged alternatives
keep their IDs on editing; imported source provenance, archive status and history
are preserved. Unsaved editor changes have a leave confirmation.

Training size defaults to 10 and supports 1–100. Select distinct active approved
sentences, shuffle with injectable randomness and snapshot prompt, accepted texts,
explanation and metadata. A shorter pool yields a clearly indicated smaller queue
without repetition; an empty pool inserts no session. Scheduling scopes and due
priorities are deferred consistently with other Android modes.

Enter a **multiline** Bulgarian answer and tap **Проверить**. Enter inserts a new
line rather than submitting. Before submission, feedback/accepted answers and
explanation are absent from the practice UI state. Comparison is exact after
Unicode NFC, Unicode White_Space collapse/trim and Locale.ROOT lowercase. Meaningful
Bulgarian differences, accents, punctuation and alphabet confusables stay distinct.
There is no fuzzy grammatical grading.

A match against the reference or any approved alternative is CORRECT/AUTOMATIC.
A non-match is **PENDING**, not automatically incorrect: show raw answer, reference,
alternatives and explanation, explain that a string difference does not prove a
grammatical error, and offer **Правильно / Частично правильно / Неправильно —
самооценка**. A manually correct answer stays labelled SELF; it is never presented
as an automatic match. Self-assessment does not approve a new alternative.

Final feedback remains until **Следующее предложение**, including the final
answer after automatic completion. Progress separately shows submitted and evaluated
counts. Summary includes correct/partial/incorrect/pending/unanswered counts and
all questions, preserving automatic-versus-self labels, original submissions,
references and explanations. Unanswered questions and unassessed answers remain
clearly distinct. No fabricated statistics are shown.

**Завершить досрочно** asks for confirmation. Pending submissions remain unassessed
and unanswered items remain skipped; neither awards progress. A closed pending
answer cannot subsequently be rated. Leaving the tab/app alone does not end a
session. Return through **Предложения → Перевод предложений** to restore it.

## Representative parity cases

| Input/action | Durable/visible result |
| --- | --- |
| 12 eligible sentences, requested 10 | Ten distinct shuffled entries |
| Three eligible, requested 10 | Three distinct entries, shortage explained |
| Empty/archived/unapproved-only pool | EMPTY_SENTENCES, no new session |
| ` МОЛЯ,\n ДОНЕСИ\u00a0 ЧАЙ. ` versus approved `Моля, донеси чай.` | CORRECT/AUTOMATIC; raw multiline text retained |
| Canonically decomposed `и` + breve versus `й` | Same NFC key |
| `каща` versus `къща`; Latin `o` versus Cyrillic `о`; changed punctuation | PENDING, no claim of a grammar error |
| One new unmatched answer | One attempt, automatic_match=false, self_rating=null, no progress |
| Rate that answer PARTIAL | Same attempt ID/raw text/automatic_match=false; manual PARTIAL and one sentence progress update |
| Rate it CORRECT | SELF/CORRECT, not AUTOMATIC |
| Replay identical raw submission or identical self-rating | Authoritative saved state; no duplicate attempt/progress, including after closure |
| Different raw text, even normalized-equivalent, or changed saved rating | ANSWER_CONFLICT / EVALUATION_CONFLICT |
| Manually rate an automatic match | EVALUATION_CONFLICT |
| Next before submission or while PENDING | EVALUATION_REQUIRED |
| Future/foreign question or new action after closure | Typed stale/closed error; no write |
| End early with one pending and two untouched | ABANDONED; submitted=1, evaluated=0, pending=1, unanswered=2; no progress |
| Edit/archive imported sentence during a session | Existing snapshots, answers and progress unchanged |
| Reopen file DB with empty SavedStateHandle | Recover order/position, raw pending/final feedback or terminal summary |

## Persistence and migration

Compiler-exported v5 identity: `18a2b4a0ce8d53b6710e88643e1c2014`.
Room v5 adds `sentence_sessions`, `sentence_questions`, `sentence_attempts` and
`sentence_progress`. A session saves request UUID/settings, actual total, current
position, revision, status and times. Questions enforce unique session/position
and session/sentence and keep immutable snapshots plus acknowledgement. One unique
attempt per question saves original/normalized text, immutable automatic-match
boolean, optional manual rating, submission/evaluation timestamps. Evaluation source
and result derive from these saved facts, avoiding contradictory duplicate fields.

One transaction inserts a submission and, only for an automatic match, finalizes
sentence progress. Self-rating updates that same pending attempt and records one
finalized progress event in a single transaction. Retry guards run before writes.
Counters are sentence-only: finalized attempts = correct + partial + incorrect;
partial/incorrect reset the correct streak. Pending/view/acknowledgement/skipped
items do not update counters. There are no vocabulary usage/mastery writes or
updates to recognition/typing progress. Review scheduling remains a separate future
integration; these counters do not classify mastery or invent due dates.

The previous imported sentence table required difficulty. To support truly optional
manual difficulty, migration 4→5 relaxes only that NOT NULL constraint. SQLite
requires a table rebuild: preserve alternative rows in a temporary table before
removing/replacing their parent, restore original alternative IDs and indexes,
and preserve both AUTOINCREMENT high-water marks (even when all old rows were
deleted). Foreign keys stay enabled; Room runs the migration atomically. Existing
sentences, provenance/timestamps, import receipts and flashcard/typing history are
retained. All prior migrations remain registered; no destructive fallback exists.

The web CSV importer still requires difficulty 1–5. Android preserves that format:
if any selected-for-export sentence lacks difficulty, fail before writing the CSV
header and ask the user to set it in the editor. Do not silently choose a level or
export an invalid web-import row. Exports remain content-only, not progress backups.

ViewModels recover the latest active session from the database without requiring
an in-memory queue or saved session pointer. A persisted revision prevents stale
Flow observations from replacing newer submit/rating/acknowledgement state. Busy
guards plus immutable rendered IDs/payloads protect rapid/delayed callbacks.
Uncertain writes retain an exact retry while the ViewModel lives; after process
recreation the committed database state is authoritative. Submitted text is durable;
unsent drafts use SavedStateHandle and are not a guaranteed disk backup.

## Discrepancies and deliberately deferred behavior

- Web V6 currently schedules sentences and prioritizes due vocabulary links;
  older docs saying no scheduler are stale. Android keeps the all-active approved
  pool and separate counters, consistent with its deferred scheduling stages.
- Web records linked-word usage independently, without changing word progress.
  Android has no existing sentence-word associations and introduces none in this
  task. It does not infer links from text or award vocabulary mastery.
- Web UI labels a SELF/CORRECT answer as matching an approved translation. Android
  fixes this presentation defect by explicitly labelling every manual rating SELF.
- Web start request check-then-insert can race; Android serializes transactional
  lookup/insertion. One injected Clock supplies timestamps.
- Source reference normalizes sentence content whitespace, but raw submitted text
  must stay untouched. Android does both accordingly, including multiline input.
- Pronunciation remains deferred; no pre-answer Bulgarian audio/text is exposed.

## Verification

New tests cover manual/optional fields, duplicate keys, unchanged alternatives,
paging/archive, transactional editing, CSV preflight/round-trip, approved alternatives,
normalization, automatic versus manual results, every rating, exact/concurrent
replays, stale/closed actions, early ending, reopened database and fresh-ViewModel
recovery, lost final responses, rollback, constraints, and isolation from word modes.
Populated exported-v4 and empty-with-deleted-ID migration fixtures check data and
sequence preservation. Existing v1/v2/v3 migrations now upgrade through v5.
Actual executed commands, schema identity and APK links are in BUILD_REPORT.md.
Static checks or configured workflows are not claims of passing builds.

Device/emulator checks remain: install/upgrade with a compatible debug key, airplane
mode, Android OS process-kill/relaunch around submit/self-rating/next, multiline
keyboard/IME behavior, rotation/navigation, rapid touch, large text and TalkBack.
