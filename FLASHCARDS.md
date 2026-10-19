> Review scheduling and NEW/DUE/MIXED scopes are now implemented. The original
> milestone details below are historical where they defer scheduling. Current
> selection, scoped balance limits, statistics, upgrade and clock rules are in
> [REVIEW_AND_STATISTICS.md](REVIEW_AND_STATISTICS.md).

# Offline flashcard recognition

## Reference and source mapping

Read-only web reference: `Danishbears/bulgariantrainer`, branch `master`, commit
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`. Source was inspected from the existing
checkout, not README/page summaries. Java paths below use
`src/main/java/com/example/bulgariantrainer/`; tests use the corresponding
`src/test/java/com/example/bulgariantrainer/` prefix.

| Web source | Android implementation |
| --- | --- |
| `service/FlashcardService.java`, `dto/FlashcardDtos.java` | `flashcards/FlashcardRepository.kt`, `FlashcardModels.kt` |
| `entity/FlashcardQuestion.java`, `StudySession.java`, `StudyAttempt.java` | `data/FlashcardEntities.kt` (durable sessions, cards, attempts) |
| `repository/WordRepository.java` trainable-word query | `data/FlashcardDao.kt` candidate/count queries |
| FLASHCARD counters in `entity/StudyProgress.java` | Dedicated `recognition_progress`, pure `RecognitionUpdates` |
| `src/main/resources/static/js/flashcards.js` | `ui/FlashcardViewModel.kt`, `FlashcardScreen.kt` |
| `FlashcardIntegrationTests.java` | `flashcards/FlashcardRepositoryTest.kt`, `ui/FlashcardViewModelTest.kt` |
| H2/Flyway flashcard/history schema | Additive `data/FlashcardMigrations.kt`, exported Room v3 schema; Flyway SQL is not run on Android |

Android Kotlin paths use `app/src/main/java/com/danishbears/bulgariantrainer/`.
The existing applicationId, Compose/Material 3, vocabulary/import functionality,
toolchain and CI workflow are retained. No account/server/network dependency,
automatic sample import, typing/sentence-training mode or release signing added.

## Learner workflow

Open **Карточки**. A saved unfinished session is loaded from Room. On a fresh
installation, or after **Новое занятие**, choose a direction and size (1–100,
default 20). The default direction is Bulgarian → Russian. The UI displays the
actual available active-word count; an empty dictionary offers a link to the
vocabulary screen. If fewer words are available, the session uses that smaller
count and explains it before starting; entries are never repeated to fill a quota.

For each card, recall the translation and press **Показать ответ**. Only then
are **Знаю** and **Не знаю** shown. They are self-assessments of recognition,
not automatic typed-answer/grammar grading. The progress bar counts rated cards.
After the last rating, the saved summary lists known, unknown and unrated counts
and all snapshotted card results.

Leaving the tab/app pauses the session; it does not abandon it. Return to
**Карточки** to restore the saved card/order/reveal/position. **Завершить досрочно**
asks for confirmation, saves ABANDONED and displays the summary. Unrated cards
show **Без оценки**, including a revealed-but-unrated card. No skipped attempts
or progress are fabricated. **Новое занятие** returns to setup without deleting
previous history; **Последнее сохранённое занятие** opens the saved result again.

A newly created process with no SavedStateHandle session ID queries the database:
most recent unfinished session first, otherwise the latest saved result. A valid
preferred unfinished ID remains usable. Normal UI does not silently replace an
active session with a new queue. The repository also supports independent session
IDs, as the web does; its transactions protect counters across those sessions.

## Implemented rules and representative cases

| Input/action | Persisted/visible result |
| --- | --- |
| Request 20 with only two eligible words | Two unique cards, requested size still 20; no repeats |
| Active word with several translations | One card; all translations snapshotted primary-first, then translation ID |
| Archived word or word without translations | Excluded from new-session pool |
| BG→RU before reveal | Bulgarian prompt; empty answer list |
| RU→BG before reveal | All accepted Russian translations as prompt; Bulgarian answer absent |
| MIXED direction | Alternating BG→RU/RU→BG, with a random first direction; odd queues differ by at most one |
| Read/reveal/reveal again | Same card; reveal timestamp set once; zero attempts and no recognition update |
| Rate an unrevealed/current/future/foreign card | Only current revealed card accepted; others receive typed hidden/stale errors |
| Know followed by identical retry, even after completion | One immutable attempt and one recognition update |
| Retry the same card with opposite rating | RATING_CONFLICT; saved rating remains unchanged |
| Two simultaneous identical starts with the same UUID/settings | One session/queue; same durable state returned |
| Same start UUID with changed size/direction | START_REQUEST_CHANGED, no replacement queue |
| Rating card N | Attempt + recognition counters + current position N+1 commit together |
| Last rating | Status COMPLETED, completion time and no current question |
| Early completion after one unknown of three | ABANDONED, answered=1, unknown=1, remaining=2; two SKIPPED results, one attempt only |
| Early completion with no ratings | No attempt/progress rows |
| Edit/archive a queued word | Current session keeps original text/translations/direction snapshots; archive affects future pools |
| Hard-delete a referenced word | Repository WordInUseException; restrictive database FK also prevents bypass |
| Append translations through content import after training | Live vocabulary changes; existing session snapshots and recognition progress remain unchanged |

Words may occur again in later sessions. The reference's no-repeat rule applies
within each session, not across all sessions. Candidate SQL uses one word row
with EXISTS translations, avoiding join duplication; it samples at most 100
eligible words without loading an entire dictionary into application memory.
Sampling first fixes a list of random IDs, then a deterministic Room relation query
loads words/translations by those IDs. Room can re-execute a parent query while
mapping relations; combining RANDOM() with that relation query can change the
sample between passes. A repeated partial-pool regression test protects this split.
No translation is generated, split on commas/slashes, or silently reversed.

## Scheduling boundary and deliberate differences

The current web service already applies NEW/DUE/MIXED study scopes and scheduler
priority DUE → NEW → LEARNING → MASTERED. This task explicitly defers scheduling.
Android therefore offers **direction selection only**, and samples all active
translated words; it does not claim web review-queue parity or expose fake due/
mastered counts. Mixed *direction* is not the web's mixed *study scope*.

`RecognitionUpdates` changes only attempts, known count, consecutive-known streak
and last-rating time. Both flashcard directions share this recognition record,
as the web's FLASHCARD mode does. Unknown resets the streak. Revealing/viewing
changes none of these fields. No mastery label, next-review date, interval or
cross-mode progress field is changed or invented.

Future review planning belongs in a separate component and schedule table keyed
to recognition progress/attempt identity. Integrate it within the same rating
transaction, after detecting an already-recorded rating, so retries cannot schedule
twice. Future typing directions and sentence training need independent progress
and schedules; they must not reuse `recognition_progress`. Vocabulary content
remains separate from all learning records.

Two source-inspection defects already noted in WEB_PARITY are avoided here:
concurrent first start retries are serialized by local Room transactions and a
unique UUID; all session/reveal/rating/completion timestamps use one injected
Clock. Android timestamps use UTC epoch milliseconds rather than H2 microseconds.
No general claim is made that the web scheduler/other suspected defects are fixed.

## Durable schema and atomicity

Room version **3**, compiler-exported identity hash
`c096057d5434265d3679356753242cec`, with explicit non-destructive `MIGRATION_2_3`; production
also registers `MIGRATION_1_2` for supported upgrade chains. Existing v1/v2 table
definitions are retained. New tables:

- `flashcard_sessions`: unique request UUID, requested direction/size, actual total,
  current 1-based position, status, start/completion timestamps.
- `flashcard_cards`: session/word IDs, order, actual direction, immutable Bulgarian
  and JSON Russian snapshots, reveal timestamp. Unique `(session_id, position)`
  and `(session_id, word_id)` enforce queue identity/no repeats.
- `flashcard_attempts`: immutable attempt ID, unique card reference, known/unknown
  rating and timestamp. Session, word and direction are available through the
  immutable card relationship; they are not copied into inconsistent columns.
- `recognition_progress`: word ID, attempts, known count, streak and last-rating
  time. This dedicated table cannot overwrite another training mode's records.

Foreign keys use RESTRICT for session/card/word history references. Only owned
vocabulary translations/sentence alternatives retain their earlier cascades.
There is no history deletion UI and no destructive fallback. `@Upsert` updates
recognition counters without replacing a vocabulary row. All DAO work is suspend/
Room Flow, on Room background executors; no main-thread query allowance exists.

Start creates session and all cards in one transaction. Reveal validates session
and current card before persisting its timestamp. Rating checks an existing
attempt before active/current/reveal checks, making an identical late retry safe
when the session has already advanced or completed. Counter update, attempt insert,
position advance and optional completion are one transaction. A late SQL failure
rolls them all back. Early completion is repeatable and does not mutate unrated cards.

Active UI state contains only the current prompt and an empty answer before
reveal; future-card snapshots/results are not exposed to Compose until summary.
Session counts/results derive from actual attempts, not fabricated statistics.
The UI uses lifecycle-aware collection, busy guards and callbacks carrying the
rendered session/card IDs. A queued tap cannot target a newly displayed card.
Failed writes retain the exact retry payload while the ViewModel lives; reload
can instead read the committed state. Process recovery relies on database rows,
not on an in-memory retry intention or queue.

## Recovery guarantees and limits

- Start committed but its UI response was lost: the next process finds the active
  session without requiring a SavedStateHandle/session-ID hint.
- Reveal committed: the restored answer remains revealed.
- Rating committed: the restored position/attempt/counters include it; replaying
  that old card ID cannot rate the next card or double-count.
- A transaction rolled back: no partial attempt/counter/position change is visible;
  the restored revealed card can still be rated.
- Last rating/early completion committed: the terminal status/summary survives
  reopening and no new skipped attempt is created.

Robolectric tests actually close file databases and create new repositories and
fresh ViewModels with empty SavedStateHandle. They do not claim to have killed
an Android OS process during a real disk write. Device checks remain listed below.

CSV exports from IMPORT_EXPORT.md still contain content only. They do not contain
flashcard sessions, snapshots, attempts, recognition counters or future schedules,
and are not progress backups. Uninstalling an app deletes its private database;
different cloud debug keys can require uninstalling, as ANDROID_BUILDS.md explains.

## Verification

`FlashcardRepositoryTest` covers parity, hidden/stale/foreign cards, defaults,
all directions, no repeats, reduced/empty queues, snapshots, exact start/rating
retries, opposing/concurrent ratings, shared recognition counters, isolation,
early completion, SQL rollback, constraints, Flow invalidation, file reopen,
lost responses and content-import preservation. `FlashcardViewModelTest` covers
rapid taps, fresh-process-style recovery, terminal summaries, exact failure retry
and empty/invalid setup. `FlashcardMigrationTest` opens a populated exported v2
fixture and preserves vocabulary, sentences, alternatives and import receipts;
the existing exported-v1 test now validates the full 1→2→3 upgrade chain.

Actual command results, tested commit, schema hashes and APK links are recorded
in BUILD_REPORT.md. Configuration/static source inspection is not a build pass.
The final [hosted run](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39274478038)
at `7e0172d12ff1b95b05b174d19bd11bdd7a685249` passed all 96 JVM tests,
`assembleDebug`, `lintDebug` and committed Room schema checks for versions 1–3.
Its [APK artifact](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39274478038/artifacts/12198030314)
is a ZIP containing `app-debug.apk`; installation instructions and debug-key
upgrade limitations are in ANDROID_BUILDS.md.

Still requires a device/emulator: install/upgrade with a compatible debug key,
airplane-mode training, real process termination/relaunch while hidden/revealed/
after rating, rotation and navigation, rapid touch/accessibility actions, screen
sizes, large font, dark theme and TalkBack hidden-answer visibility/focus. No
device/emulator was used in this increment.
