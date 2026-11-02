# Offline typed aspect-pair practice

Reference: read-only web `codex/aspect-pairs` commit
`be6a91ff2ae06b8b0eae2461d8c7a8bf910ce57a` (same tree as inspected master merge
`2792dc3e4cb5d4762783140729e2c37ccac95567`). Actual Java services, migrations,
tests and browser code guide this implementation; README alone is not evidence.
The missing original `aspect_pairs_app_guide.md` remains unavailable; see the
source map and discrepancies in ANDROID_ASPECT_PAIRS_PLAN.md.

## Use

Главная → Видовые пары → Практика. First explicitly create/import content;
the bundled 100-pair material is never imported automatically. Settings select
size1–100, optional exact topic/namespace, direction and NEW/DUE/MIXED queue.
Stage1 alone is selected on a fresh setup; stages2/3 require a deliberate check.
An existing session restores its original settings. Last-session settings are
reused for the next setup; unsaved configuration drafts use SavedStateHandle.
No server, accounts, network permission or automatic synchronization is involved.

Archived pairs cannot enter new sessions. Queues contain each pair at most once.
A shortage offers an explicitly labelled shorter start; an empty pool creates
no session. The aspect-specific bounded planner minimizes feasible directional
imbalance first, then total scheduler category priority; final order/ties are
random. DUE-only eligibility can force uneven directions. This is the web aspect
planner, not the older greedy word-typing planner.

The prompt shows Bulgarian source, source Russian meaning and target Russian
meaning. Target forms, accepted answers, notes, examples and provenance remain
absent from practice UI state until submission/reveal. The local single-user
content editor remains accessible. The current native UI adds explicit Bulgarian source audio and approved-answer
audio after feedback, reusing the existing TTS component. This user-requested
extension is absent from the pinned web aspect browser; see ASPECT_UI_ANDROID.md.
Hidden targets must not leak through TTS/accessibility.

## Answers and reveal policy

Forward checks `acceptedPerfective`; reverse checks `acceptedImperfective` from
the persisted question snapshot. Comparison is **NFC first**, remove remaining
U+0301/U+0300 combining stress, ROOT lowercase, trim/collapse Unicode whitespace.
The NFC-first order preserves и/й/ѝ, including decomposed equivalents, and also
precomposed grave letters such as ѐ. No punctuation stripping, substring matching,
Latin lookalikes, transliteration, fuzzy matching or removal of се/си is applied.

| Prompt / direction | Input | Result |
| --- | --- | --- |
| купувам → perfective | купя | CORRECT |
| прочета → imperfective | чета or прочитам | CORRECT |
| опитвам се → perfective | опитам | INCORRECT |
| седна → imperfective, approved сядам | седя | INCORRECT |
| стана → imperfective, approved ставам | стоя | INCORRECT |

Mismatch text is **«Не совпало с ответами этой карточки»**. It makes no claim
that the submitted word cannot exist in Bulgarian. Approved forms and the raw
original submission appear after grading. There is no sentence self-assessment.

Reveal is terminal **REVEALED**, not independently recalled success: no submitted
answer, no correct credit, one failed scheduling event. It resets the direction's
streak to0 and schedules a retry after24hours, retaining accumulated successes.
Typing a correct answer after reveal cannot change that outcome. Reveals are
counted separately and excluded from typed accuracy (`correct / typed`, web
one-decimal rounding). This differs intentionally from flashcard reveal.

Feedback remains until explicit **Далее**. The last outcome marks COMPLETED but
its feedback remains durable until acknowledged. Early completion marks ABANDONED
when unanswered items remain. Skipped items receive no attempt/progress and their
targets remain hidden in results. Results support mistake/reveal review and an
optional complete result list.

## Persistence, scheduling and compatibility

Room9 adds five tables through non-destructive `MIGRATION_8_9`:
`aspect_sessions`, `aspect_questions`, `aspect_attempts`, `aspect_progress`,
`aspect_review_schedules`. Existing24 tables and identifiers are unchanged.
There is no destructive fallback. Compiler-exported schema9 is committed.

Sessions store canonical settings/start UUID, order, pair IDs, actual directions,
full learning snapshots/hash, raw/normalized submissions, outcomes, reveal time,
acknowledgements, current position, revision and status. Editing/importing/
archiving content cannot change an existing question. Committed work recovers
from Room even with a new ViewModel and empty SavedStateHandle. Unsubmitted input
drafts are not guaranteed to survive a force-stop.

Submission/reveal, unique attempt, progress counters, shared scheduler update
and session status are one Room transaction. Unique question attempts, exact
raw-payload replay, start UUID replay, Next acknowledgements and captured
session/question IDs guard retries, double taps and delayed callbacks. A failed
transaction rolls back all changes; the UI retains the immutable failed action
for explicit retry or authoritative reload. Content-epoch protection rejects
operations queued behind full replacement; restore also opens a fresh Activity.

Progress/schedules use `(pairId, actualDirection)`; MIXED is never a progress
bucket. Existing word recognition, both word-typing directions and sentence
history/schedules/global statistics are untouched. This increment supplies real
per-session results, not the separate aggregate aspect-statistics screen proposed
for a later stage.

The existing `ReviewScheduler` is reused unchanged: success intervals
1,3,7,14,28,56,112,224,365days capped; failure/reveal1day and streak0;
mastery requires streak≥5 and interval≥28days. NEW is absent schedule; DUE is
`nextReview <= now`; MIXED prioritizes due/new/learning/mastered according to
the shared implementation after directional feasibility. Injected Clock uses
device instants; days are24hours, not local-midnight/DST boundaries. Availability
refreshes on resume, manual refresh and a minute timer. Device-clock corrections
may produce completion instants before start; original instants are preserved,
not silently rewritten to emulate the web server clock.

Full backup is now **v3/schema9**,29tables/19sequences, including aspect sessions,
snapshots, attempts, progress and schedules. Original v1/schema7 and v2/schema8
contracts/checksums are validated before staged expansion with empty missing
tables. Restoring an old backup replaces current data and contains no aspect
practice history; it never fabricates or silently merges progress. Old app
versions reject v3. Content JSON export still contains learning content only.
Keep a full backup before updating or replacing data.

## Evidence and acceptance

`parity/aspect-reference` contains exact pinned Java normalizer/planner/direction
blobs with provenance. `verify_aspect_parity.py` executes the actual Java21 code
without Spring wiring; Android stays on existing dependency versions/Java17.
Its fixture checks14 normalizations and28 planner objectives, not RNG-identical
orders across Java/Kotlin. Kotlin tests additionally exhaust small feasible
pools, check required linguistic fixtures, alternatives, both directions,
independent schedules, reveal/answer races, retries, SQL-trigger rollback,
Room reopen, edited snapshots, legacy-table isolation, migration, full backup
and fresh-ViewModel recovery. BUILD_REPORT.md records actual execution results.

Physical-device checks remain necessary: select/import locally then airplane
mode; Cyrillic keyboard/IME composition and held Enter; double submit/reveal;
navigation/rotation/small screens/large fonts/TalkBack; force-stop before answer,
after submit/reveal and at final feedback; resume order and explicit Next;
backup/restore confirmation and recovery; same-key higher-version APK update
preserving old and aspect history. Mocked/Robolectric tests do not establish these.
