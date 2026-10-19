# Offline bidirectional typing

## Reference and implementation map

Read-only reference: `Danishbears/bulgariantrainer`, `master` at
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`. Actual local source/tests were inspected.
Java paths below use `src/main/java/com/example/bulgariantrainer/`; test paths use
`src/test/java/com/example/bulgariantrainer/`.

| Web implementation/evidence | Android implementation |
| --- | --- |
| `service/TypingService.java` availability/select/start | `typing/TypingRepository.kt`, `typing/TypingModels.kt` / `TypingPlanner` |
| `service/TypingQuestionPlanner.java`; seeded planner integration test | `TypingPlanner(Random)`; all word selection, assignment and final order use injectable randomness |
| `service/VocabularyText.java`; `service/TypingNormalizationTests.java` | Existing shared `data/VocabularyText.kt`, exact `TypingEvaluator`; `typing/TypingRulesTest.kt` |
| `TypingService.answer/next/complete/results`, `dto/TypingDtos.java` | Transactional `TypingRepository.submit/next/complete/mistakes` |
| `entity/TypingQuestion.java`, `StudyAttempt.java`, `StudySession.java`; Flyway `V4__typing.sql` | Four independent typing entities, DAO, additive `MIGRATION_3_4`, Room v4 schema |
| `entity/StudyProgress.java`, direction modes in `TypingService.answer`, Flyway V6 | `typing_progress` composite `(word_id,direction)` counters; no schedule fields yet |
| `src/main/resources/static/js/typing.js` | `ui/TypingScreen.kt`, `TypingViewModel.kt`, fifth navigation tab |
| `TypingIntegrationTests.java` distribution/consent/alternatives/retry/feedback/strict spelling | `TypingRepositoryTest`, `TypingRulesTest`, `TypingViewModelTest` |

Existing vocabulary, sentence content transfer and flashcards remain intact.
ApplicationId, Compose Material 3, Kotlin/Room, JDK/SDK/AGP/Gradle versions and
workflow permissions are unchanged. No network/backend/storage permissions added.

## Learner flow

Open **Ввод**. Unfinished sessions are recovered from Room, without needing a
SavedStateHandle pointer. Otherwise the latest saved feedback/result is available.
For at least 30 eligible active entries with translations, start a standard
30-question session: **30 distinct entries, exactly 15 BG→RU and 15 RU→BG**,
shuffled as word/direction pairs. Archived/translationless words are excluded.
A word with several translations is still a single entry, used once.

For 1–29 entries, the UI explains the shortage and offers an explicitly labelled
**Начать сокращённое занятие (N)** button. Starting without explicit consent fails
with SHORTER_SESSION_REQUIRED and inserts nothing. A shorter queue uses every
eligible entry once; counts differ by at most one, BG→RU gets the odd extra (the
web selector chooses BG on ties). A single entry gives one BG→RU question.
Zero eligible entries offers a dictionary link and never creates a session.
Availability is advisory: the transaction rechecks the current pool before start.

Enter one translation and tap **Проверить** or the keyboard Done action. Feedback
shows correct/incorrect, the original submitted text and every accepted answer.
The question stays visible until **Следующий вопрос**. The final submission marks
COMPLETED but keeps final feedback visible until **Посмотреть результаты**.
The summary lists real answered/correct/incorrect/unanswered counts and only
submitted mistakes, with direction, source, raw answer and accepted translations.

Leaving the tab/app pauses rather than ends a session. **Завершить досрочно** asks
for confirmation, preserves attempts and shows ABANDONED; unanswered questions
create neither attempts nor progress. **Новое занятие** retains previous history.

## Exact answer contract and representative cases

Normalize with Unicode **NFC**, collapse Unicode White_Space runs to one ordinary
space, trim, then lowercase with Locale.ROOT. Do not apply NFKC, remove accents,
strip punctuation, substitute alphabets, stem words, or use edit distance/fuzzy
similarity to award correctness. BG→RU accepts any one full stored Russian
translation. RU→BG prompts all Russian alternatives and accepts only the stored
Bulgarian entry. Text/alternatives/directions are immutable session snapshots.

| Input/action | Expected result |
| --- | --- |
| 35 eligible entries | 30 unique words; 15/15, shuffled |
| Five eligible entries, no consent | SHORTER_SESSION_REQUIRED, zero new session rows |
| Five eligible entries, consent | Five unique words; three BG→RU, two RU→BG |
| ` \t МОИ\u0306\u00a0  ДОМ \n` versus `мой дом` | Correct |
| `Е\u0308ЛКА` versus `ёлка`; `И\u0300` versus `ѝ` | Correct, canonical composition preserved |
| `каща` vs `къща`, `мои` vs `мой`, `и` vs `ѝ`, `елка` vs `ёлка` | Incorrect |
| Latin `o` in `дoм` vs Cyrillic `дом`; `дом!` vs `дом` | Incorrect |
| Accepted `дом`, `моё жилище`; submit `МОЁ ЖИЛИЩЕ` | Correct |
| Same alternatives; submit `дом; моё жилище` or just `жилище` | Incorrect |
| Empty/Unicode-whitespace-only or more than 4000 UTF-16 characters | INVALID_REQUEST, no write |
| View question before submission | Prompt/direction only; no accepted-answer feedback or mistake results |
| Submit current question | One raw+normalized answer/result, one direction counter update, durable feedback |
| Retry same question with exactly identical raw text | Same authoritative state, no new attempt, including after closure |
| Retry with different raw text, even normalized-equivalent spacing | ANSWER_CONFLICT, previous answer unchanged |
| Future/foreign question; next before answer | STALE_QUESTION / ANSWER_REQUIRED, no write |
| New answer to an abandoned session | SESSION_CLOSED, no write |
| Last submit followed by reopen | COMPLETED with saved final feedback; acknowledge then saved summary |
| Edit/archive vocabulary while training | Original prompt/accepted answers remain; archive affects new sessions |

Reading/feedback acknowledgement never updates progress. Each successful submit
updates only `(word_id, BG_TO_RU)` or `(word_id, RU_TO_BG)`: attempts, correct,
consecutive-correct streak and last-submit timestamp. Incorrect answers reset that
direction's streak. `recognition_progress` and flashcard attempts are untouched.
There is no aggregate typing record shared between directions and no mastery claim.

## Persistence, concurrency and recovery

Room v4 adds `typing_sessions`, `typing_questions`, `typing_attempts` and
`typing_progress` through additive migration 3→4. All nine existing table
contracts are preserved. Version 1/2 migrations remain registered; no destructive
fallback or automatic imports exist. Exported v4 JSON is generated by Room/KSP
and verified against Git in CI; its actual identity is
`60b12add3ca435a9eeb8c7130f4f5ae4`. See BUILD_REPORT.md for actual verification.

Session rows keep start request UUID/shorter consent, total, current position,
revision, status and timestamps. Unique question constraints protect both
(session,position) and (session,word). Each question stores its own Bulgarian and
all Russian snapshots, direction and feedback acknowledgement. One unique attempt
per question preserves raw and normalized text, correctness and submission time.
Foreign keys restrict deletion of history-linked words; archiving stays available.

Start and answer writes are serialized Room transactions off the main thread.
Attempt insertion, direction progress and session revision/status commit together;
SQL failure rolls them all back. Exact UUID/settings start retries replay their
original queue. Rating/submission callbacks carry immutable question IDs and raw
payloads, so delayed taps cannot submit against a newly displayed question.
Raw equality is deliberately stricter than grading normalization for retries.

ViewModels collect Room state with lifecycle-aware Compose collection and block
concurrent actions. A persisted revision prevents queued old Flow emissions from
replacing newer feedback/acknowledgement. Uncertain writes retain the exact retry
payload while the ViewModel lives; users can also reload committed state.
On process recreation, the database finds the latest unfinished session (or last
terminal feedback/summary), so recovery does not depend on that in-memory payload,
queue or session pointer. Submitted text is durable; unsent drafts are only UI
saved state, not guaranteed disk backups. A committed answer with a lost UI response
restores as feedback and cannot double-count when replayed.

## Deliberate differences and suspected reference defects

- Current web scope/scheduler can assign more than 15 questions in one direction
  when eligibility differs; its TYPING.md unconditional balance promise is too
  broad. WEB_PARITY.md already flags this. Android guarantees requested 15/15 by
  sampling the all-active pool in both directions. Review scopes/due priorities,
  intervals and mastery remain deferred alongside flashcard scheduling.
- Web `TypingQuestionPlanner.directions()` has randomized odd-side assignment,
  but actual `TypingService.select()` does not call it and selects BG on ties.
  Android follows the actual selector's odd count and shuffles the final pairs.
- The reference TYPING.md says no schedules/shared TYPING progress; current service
  actually calls a scheduler and writes two independent modes. Android adopts
  direction isolation and keeps the future scheduler separate from counters.
- Web start UUID check-then-insert has a possible concurrent retry conflict;
  Android serializes lookup plus insert transactionally. No silent retry replaces
  a queue. A single injected Clock supplies all timestamps.
- Web stores Russian snapshots separated by newline; Android uses JSON arrays,
  compatible with existing multiline vocabulary imports and without losing text.
- No audio/TTS feature is introduced, so there is no pre-answer Bulgarian audio
  leakage for RU→BG. Before feedback, accepted Bulgarian spelling is not exposed
  through typing UI state. The dictionary remains intentionally accessible.

## Verification and limits

`TypingRulesTest`: 15/15 distribution, unique selection, seeded word/direction
order, every short size, Unicode whitespace/NFC/root case, spelling/confusables,
and whole-alternative matching. `TypingRepositoryTest`: actual Room planning,
consent/empty/archive, alternatives, acknowledgement, exact/conflicting concurrent
submissions, direction isolation, snapshot protection, Flow updates, SQL rollback,
constraints and reopened-file recovery including lost final responses.
`TypingViewModelTest`: explicit short consent, rapid/delayed actions, empty saved
state recovery, input/feedback/position restoration, uncertain write retry and
summary history. `TypingMigrationTest`: populated exported v3 upgrade preserves
vocabulary, sentences, import receipts, unfinished flashcards, attempts and
recognition, then allows both modes to continue. Existing v1/v2 migration tests
now validate the entire chain to v4.

Actual commands, results, tested commits and APK artifacts belong in BUILD_REPORT.md.
Static test counts/configuration are not execution evidence. Device/emulator
checks remain: install/upgrade with a compatible debug key, airplane mode, Android
OS process termination/relaunch before/after submit and acknowledgement, keyboard
Done/IME resizing, rotation/tab navigation, rapid touch, large fonts and TalkBack.
CSV content exports still exclude training sessions/progress and are not backups.
