# Review scheduling and real-record statistics

## Pinned implementation and fixtures

Reference: read-only `Danishbears/bulgariantrainer`, `master` at
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`. Actual source/tests/migrations and
existing Android v5 history were inspected. Java paths below are relative to
`src/main/java/com/example/bulgariantrainer/`; tests use `src/test/java/...`.

| Actual web source | Android implementation / verification |
| --- | --- |
| `service/SpacedRepetitionScheduler.java`, `entity/StudyProgress.java`, `SentenceProgress.java` | `review/ReviewScheduler.kt`, independent word/sentence schedule entities |
| `service/FlashcardService.start` | `ReviewSelection.flashcards`, scoped `FlashcardRepository.start` |
| `service/TypingService.eligibleIds/select` | `TypingPlanner.plan`, scoped `TypingRepository.start` |
| `service/SentencePracticeService.start/record` | `ReviewSelection.sentences`, transactional sentence scheduling |
| `service/StatisticsService.java`, `dto/StatisticsDtos.java` | `statistics/StatisticsCalculator.kt`, `StatisticsRepository.kt`, actual SQL projections |
| `service/SpacedRepetitionSchedulerTests.java` | `ReviewSchedulerTest`, `parity/fixtures/web_scheduler.json` |
| `StatisticsIntegrationTests.java` | `web_statistics.json`, pure fixture comparison and real Room projection tests |
| Flyway `V6__spaced_repetition.sql`, `MigrationPersistenceTests` | Non-destructive Room `MIGRATION_5_6`, `ReviewMigrationTest` |

`parity/reference/PROVENANCE.json` records exact original paths/commit/SHA-256.
Those copies are evidence in the Android target, not modifications to the web repo.
`python3 parity/verify_web_parity.py` verifies hashes and compiles the actual pinned
scheduler plus original entity record/getter methods. Only Spring wiring/annotations
are omitted for this small Java harness; the business methods remain unchanged.
It checks **21 identical histories** against committed goldens. It also checks
**20 literal statistics assertions** against the actual pinned integration-test
source. This is not a full Spring/Maven build. The same JSON histories/assertions
are tested against Kotlin. CI runs the reference harness before Android unit tests.

## Exact scheduling contract

Four independent schedules: FLASHCARD recognition per word, TYPING_BG_TO_RU per
word, TYPING_RU_TO_BG per word and SENTENCE per sentence. No sentence answer awards
word progress; recognition self-assessment does not certify typing mastery.

On correct review, intervals follow **1, 3, 7, 14, 28, 56, 112, 224, 365, 365...**
days. More precisely: prior streak 0→1 day, 1→3, 2→7, otherwise
`min(365, max(7, previousInterval) * 2)`. Incorrect and sentence PARTIAL both use
one day and reset the scheduling streak to zero, retaining lifetime successes and
attempts. Each finalized review increments attempts once; only CORRECT increments
successes. No ease factor, fuzzy grading, random intervals or replacement SRS is used.

Category precedence is exact:

1. Attempts=0 or no schedule: NEW.
2. Reviewed with missing next time, or next time ≤ now: DUE.
3. Otherwise streak ≥5 **and** interval ≥28 days: MASTERED.
4. Otherwise LEARNING.

Both mastery thresholds are constructor-injectable with web defaults 5/28. DUE
precedes mastery, so a mastered item becomes due at its exact deadline. Days mean
24-hour durations added to review instants, not a local calendar-midnight rule.

View/reveal/acknowledge, pending sentence self-assessment and skipped questions
never write schedules. An automatic sentence match is scheduled at submission;
a nonmatch is scheduled only when the same attempt receives final self-assessment.
Counters, attempt, scheduling and session position/status update in one Room
transaction. Exact retry guards precede writes. Identical late retries after
completion return authoritative state; changed answers/ratings conflict. A late SQL
failure rolls back schedule and counters along with the rest of the transition.

## NEW / DUE / MIXED and preserved selection behavior

Each training setup now exposes **Новые / К повторению / Смешанное**. Scope is
persisted in the session and included in UUID start-replay settings. Availability
uses real active trainable records and the same injected clock as selection.
Empty scoped queues have distinct errors; no unrelated records or repetitions
silently fill them. Existing unfinished sessions retain their exact snapshots and
order even when time advances or scopes change in the setup for a later session.

- Flashcards: filter by recognition category/scope; shuffle ties; stable sort by
  DUE, NEW, LEARNING, MASTERED; take requested size; then shuffle the selected queue.
  Mixed card translation directions still alternate with a random first side.
- Typing: independently filter/shuffle both direction lists, sort each by its
  category, direction exclusivity and opposite-direction due status; greedily pick
  the less-used direction (BG on ties), fallback to the other if empty; use each
  word once; finally shuffle pairs. Availability is the union of words eligible
  in either direction. Fewer than 30 still requires explicit shorter consent.
  **15/15 holds when eligibility allows it**, including all-new unconstrained
  sessions. A DUE pool can legitimately contain 30 questions in only one direction.
  Actual direction totals are shown in the session; UI no longer promises balance
  for every scoped pool. This preserves web selection instead of substituting a
  different constrained-assignment algorithm.
- Sentences: filter approved/unarchived category/scope; shuffle ties; category
  priority before linked-due-word tie break; take requested size and keep priority
  order. Android has no pre-existing sentence-word links and does not invent them;
  the selector supports that tie break, with an empty link set for current content.

Randomness remains constructor-injectable. Java and Kotlin generators do not have
identical seeded streams, so cross-platform fixtures compare business invariants,
not accidental identical permutations. The reference typing greedy selector can
choose BG/NEW at a tie even when the opposite RU direction is due; its universal
mixed-due-first documentation is too strong. This known behavior is explicit and
covered, rather than silently replaced. Scheduler/eligibility rules are unchanged.

## Existing history: explicit v5→v6 migration

No attempt, rating, original text, counter, old streak, timestamp, content row or
saved queue is removed, replayed or regraded. Old sessions gain scope MIXED, keeping
their contents/order. New schedule tables keep their own scheduling facts.

The web V6 upgrade strategy is applied conservatively to known Android v5 facts:

| Previous progress | New scheduling facts |
| --- | --- |
| Recognition | Copy attempts/known successes/known correct streak/last rating; unknown interval=0; next review=last rating |
| Each known typing direction | Copy that direction's actual attempts/correct/last submit; new scheduling streak=0, interval=0, next=last submit |
| Sentences | Copy finalized attempts/correct/last evaluation; new scheduling streak=0, interval=0, next=last evaluation |

Recognition follows the reference's retained streak behavior; rebuilt typing and
sentence scheduling follows its conservative zero streak. Original Android counter
streaks remain untouched and continue their prior history, distinct from scheduling
streaks. Past intervals are not fabricated. The scheduler's exact interval function
handles the zero prior interval on the next review. Known lifetime successes survive
failures. Reviewed legacy rows normally become due immediately; a previously saved
future timestamp remains future rather than being silently clamped.

Inherited schedule rows are tagged; the statistics screen displays an explanation
and count. Missing individual old attempts do not generate fake log entries or
accuracy. Unknown typing direction strings are retained in the old progress table
but never guessed into BG/RU schedules. The web V6 SQL CASE has an ELSE RU fallback;
Android deliberately filters to the two known directions to avoid inventing history.
Existing v1–v5 migrations remain registered; no destructive fallback or backup
reset is used. CSV exports remain content-only and are not progress backups.

## Statistics definitions

All values come from consistent Room projections of actual content, old counters,
schedules, sessions and attempts. No stored dashboard totals/demo statistics.

- Vocabulary total includes archive; active includes every nonarchived word.
  New/due/mastered apply to active words with translations. New means no historical
  word progress/finalized word attempt in any mode (sentence usage is excluded).
  Due means any current word schedule is DUE; mastered requires all three word
  schedules MASTERED. Count each word once. Independent mode category counts also
  include NEW/LEARNING and sentence categories for approved active sentences.
- Finalized CORRECT/INCORRECT/PARTIAL attempts count even in abandoned or currently
  active sessions. PENDING/SKIPPED do not count. Accuracy is correct/finalized ×100,
  Java Math.round to one decimal; no finalized attempts means undefined, not 0%.
  PARTIAL adds only to the denominator. Flashcards are SELF_ASSESSED, typing is
  AUTOMATIC, sentences retain AUTOMATIC versus SELF. Unknown source has its own
  classification and is never silently treated as automatic.
- Completed sessions count only COMPLETED, once per saved session. Recovery,
  summary reads and final acknowledgement add no session. Android has separate
  session ID sequences, so internal keys are `(mode,id)` to avoid collisions.
- Scheduled today counts individual active word-mode schedules and approved active
  sentence schedules whose **next time falls on today's UTC date**. Older overdue
  reviews still appear in DUE, but do not inflate scheduled-today.
- Activity uses original saved submission/attempt creation date, including a
  sentence finalized on a later day; the schedule uses its actual evaluation time.
  Completion uses completion date. UTC streak ends today or yesterday. Rolling
  seven/thirty-day series include today and explicit zero days.
- Difficult words: up to 20 by incorrect word attempts descending, last incorrect
  time descending, then word ID ascending; archive remains included. Details show
  up to 20 recent mistakes and **current** translations, not stale queue text.
  Android recognition has a saved boolean rating rather than a typed answer;
  its detail shows that self-rating without fabricating submitted text.
- Recent sessions: up to 10 by start time/ID descending. Rates derive from final
  attempts, not the requested queue length. Equal IDs across Android modes have
  a stable mode tie break; the web has one global session-ID sequence.

The web statistics fixture uses a dynamic `now-25h` review while expecting its
24h deadline to be on today; that assumption fails near UTC midnight. Android
keeps the actual UTC-date definition and anchors its parity fixture at 12:30 UTC.

## Clock, time zones and refreshing

Production uses one injected **Clock.systemUTC()**, reading the device wall clock.
The web uses a server UTC clock; offline Android has no trusted server time.
Changing device time changes current eligibility, without rewriting historical
instants. Device time-zone changes alone do not move deadlines or regroup UTC
history. No automatic clock correction or retrospective relabelling occurs.

Stored instants are epoch milliseconds. Review deadlines add exact 24h durations,
including DST changes; a local displayed hour can shift across DST. Statistics
calendar dates, streaks and scheduled-today remain UTC for parity. Human session
and mistake times are displayed in the current device zone with a zone label.
The UI explicitly labels UTC activity and the device time zone.

Eligibility/statistics reload on foreground resume and can be refreshed manually;
active collectors also tick once per minute, so dates/deadlines update without a
DB write. Selection always recomputes exact eligibility at start, regardless of
an availability label's refresh interval. Computation and database work run off
the main thread; rendering is bounded to 7/30 daily rows, 10 sessions, 20 difficult
words/mistakes. The calculator, like the web source, reads history linearly rather
than maintaining potentially inconsistent cached aggregates.

## Verification and remaining limits

`ReviewSchedulerTest`: pinned histories, cap/failure/partial/criteria, exact due
instant, category precedence, scopes, seeded selection and constrained typing.
`StatisticsCalculatorTest`: 20 pinned integration metrics (3/5=60.0%), undefined
accuracy, rounding, leap-day/UTC boundaries/streaks, due versus today, all-mode
mastery and difficult-item ranking/limits. `ReviewIntegrationTest`: actual Room
transactions, four-mode isolation, pending/final dates, retries/concurrency, SQL
rollback, recovery, real union projections and archived/current mistake detail.
`ReviewMigrationTest`: real exported-v5 data with incomplete old logs and unknown
direction; no fake attempts, old counters/queues preserved, inherited schedules
and post-upgrade continuation. `StatisticsViewModelTest`: data invalidation and
clock-only refresh without duplicate sessions. Older migration tests reach v6.
Actual executed commands/build/schema hashes and artifacts are in BUILD_REPORT.md.

Device/emulator checks remain: installation/upgrade, airplane mode, real process
termination/relaunch, foreground/background and time-zone/time changes, DST display,
large history responsiveness, navigation/rotation, rapid touch and TalkBack.
