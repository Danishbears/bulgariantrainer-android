> **Manual full backup increment:** a separate Android versioned JSON format
> preserves all content, learning records and training defaults. It is not the
> web-compatible CSV format and provides no automatic synchronization. See
> BACKUP_RESTORE.md for exact validation, confirmation and atomic replacement.

> **Pronunciation increment:** Android TextToSpeech now follows the pinned web
> visibility helpers, with local Bulgarian-only discovery, optional audio,
> lifecycle cancellation and fake-adapter tests. See PRONUNCIATION.md and
> BUILD_REPORT.md for platform differences and actual verification.

# Web reference audit and Android plan

## Current Android review scheduling and statistics increment

Starting from `84a466610e0cf7ee724f47c699c28dffcd2fc014`, Android implements the
actual pinned scheduler (1/3/7 then doubling to 365; failure/PARTIAL=1 day;
5-streak/28-day mastery; due precedence), independent four-mode schedules,
persisted NEW/DUE/MIXED scopes and real-record statistics. Room v6 conservatively
copies known legacy scheduling facts without changing old counters or attempts.
The UI explains inherited data and scoped typing imbalances. UTC daily definitions
match web statistics; the offline device clock replaces server clock authority.

[REVIEW_AND_STATISTICS.md](REVIEW_AND_STATISTICS.md) maps source/tests, exact
migration policy, clock/DST boundaries, metrics and known web defects. A pinned
Java reference harness verifies 21 histories and 20 statistics assertions; Kotlin
parity tests consume the same fixtures. Current executed verification belongs in
BUILD_REPORT.md. Prior milestone sections below describe their historical scope.

## Current Android sentence increment

Building on `3257b19570aa89b85e9e147a5794ced4bc72d63d`, Android adds sentence
management and Russian→Bulgarian practice: approved alternatives, optional fields,
multiline answers, exact matches or explicitly labelled self-assessment, durable
order/submissions/ratings/status, recovery and full summary. Room v5 retains
existing data while allowing optional difficulty and adds separate sentence
practice tables. One pending answer is finalized in place, never counted twice;
no vocabulary/recognition/typing progress is awarded by sentence practice.

[SENTENCES.md](SENTENCES.md) maps actual web source/tests, representative cases,
non-destructive migration, CSV difficulty preflight and deliberate UI defect fixes.
Scheduling, vocabulary links and pronunciation remain deferred; no AI or generation
is added. Existing features and read-only reference commit are preserved.
Executed verification belongs in BUILD_REPORT.md.

## Current Android typing increment

Building on `e8174ec0d667b27e65c87b5db0027011e01ca402`, Android adds offline typing:
30 unique entries, exactly 15/15 directions and shuffled order; smaller sessions
require explicit consent and differ by at most one. Room v4 persists snapshots,
original answers/results, acknowledgement, order/position/status and independent
per-direction counters. Exact web NFC/Unicode-whitespace/ROOT-case matching
preserves meaningful spelling differences. Durable recovery works without a
SavedStateHandle pointer; duplicate/conflicting submissions cannot double-count.

[TYPING.md](TYPING.md) maps actual source/tests, representative cases, migration,
recovery and deliberate deviations. Current web scheduler scopes can prevent its
documented unconditional 15/15 balance; Android guarantees the requested balance
and continues to defer review schedules as a separate integration. Existing
vocabulary/import/export/flashcard work is preserved. Latest actual verification
is recorded in BUILD_REPORT.md.

## Current Android flashcard increment

Starting from `a3393a2c125a93955e5383ae1b835e3236c81d3d`, Android adds offline
flashcard recognition: both directions/mixed, bounded session sizes, hidden/revealed
answers, self-ratings, progress, early completion and summaries. Durable Room v3
session/card/attempt/recognition tables preserve order, snapshots, reveal state,
position and status across process recreation. Additive migration 2→3 retains
existing content; linked words are protected from hard deletion.

[FLASHCARDS.md](FLASHCARDS.md) maps actual web/Android source and representative
cases, recovery guarantees, tests and safety differences. Scheduling is deferred
as requested: Android offers direction selection and all-active-word sampling,
not NEW/DUE review scopes, intervals or mastery classification. Recognition
counters are isolated from future typing/sentence modes; reveal/view awards none.
The original source audit and earlier milestone sections below remain historical
context. Latest executed verification is at the top of BUILD_REPORT.md.

## Current Android import/export increment

Building on `6c3990df744ca294dc309d4287a617948d6d26b9`, Android adds document-picker
TXT/CSV vocabulary import, exact sentence CSV/JSON import, editable diagnostics,
selection, explicit transactional append/confirmation and CSV content exports.
`IMPORT_EXPORT.md` maps actual reference/Android files, precise formats, tested
examples, dataset provenance, schema migration 1→2 and deliberate safety fixes.
The pinned web code has no content-export endpoint: Android exports target its
actual import schemas. Content exports are not progress backups.

Sentence content/alternatives and committed import receipts are separate Room
v2 tables. V1 vocabulary data is preserved through an additive migration. No
training mode or automatic seeding is added. The following vocabulary and
foundation sections retain earlier milestone context. Current executed checks
are recorded at the top of BUILD_REPORT.md.

## Current Android vocabulary increment

The original audit below is retained as historical evidence. Building on Android
`66854f0e5c3a9606f81cfd6c11ebaea014282516`, the app now exposes its existing Room
repository through Compose/Material 3 dashboard, dictionary and editor screens.
`LOCAL_STORAGE.md` maps Android files, matching web rules, example inputs/outputs,
tests and the future schema strategy. Dashboard values are actual vocabulary
counts, not simulated learning statistics. Search includes every Russian
alternative, uses bounded Room Paging, and separates active/archive lists.

Database version 1 and applicationId are preserved; existing vocabulary is not
recreated or seeded. At that vocabulary milestone no import, session, training, scheduler or
pronunciation feature was added. Import/export is implemented by the newer increment above. The initial foundation's welcome screen is replaced by working
vocabulary navigation while retaining the existing UI toolkit. Reference source
remains read-only at `de0a3e883d51561123ca54b7d5dd2e53757baf0a`.
Executed verification for this increment is recorded separately in BUILD_REPORT.

## Provenance and scope

Reference: [`Danishbears/bulgariantrainer`](https://github.com/Danishbears/bulgariantrainer),
branch `master`, commit **`de0a3e883d51561123ca54b7d5dd2e53757baf0a`**.
All reference paths below are relative to that repository at this exact commit,
not the Android repository. [Browse the pinned source](https://github.com/Danishbears/bulgariantrainer/tree/de0a3e883d51561123ca54b7d5dd2e53757baf0a).

Android baseline: branch `main`, commit
**`d18ad8814b391c60b0e1ec2099a5c2251978b2c6`**; only `README.md` existed.
No applicationId, Gradle/AGP versions, Kotlin code, UI, tests, or damaged build
files existed. This milestone introduces a minimal Kotlin/Compose/Material 3
welcome screen with applicationId `com.danishbears.bulgariantrainer` and build
tooling. **No training business rules are implemented in Android in this task.**
The rules below describe implemented **web** behavior and future Android acceptance
criteria. Read them with the discrepancies and suspected defects below.

Authenticated GitHub integration returned login `Danishbears` and repository
`pull`/`push` permissions for both repositories. Shell network restrictions did
not establish anything about GitHub authorization. Neither repository was initially
present under `/workspace`; `/workspace/.git` was an empty read-only mount.

Because shell Git networking was blocked, complete source files and Git trees
were retrieved through the GitHub integration. Local shallow checkouts were
reconstructed from those objects, including the exact original commit objects.
Every file's Git blob SHA, both root tree SHAs, and both commit SHAs were verified;
`git fsck --no-reflogs` passed for the reference. No GitHub page summary was used
as application behavior evidence. The reference checkout and remote were not
modified. Its `AGENTS.md` was read; no target or ancestor `AGENTS.md` was found.
Web-only Java/Spring conventions do not define the Android stack.

## Feature-to-source map

Java paths use `src/main/java/com/example/bulgariantrainer/`; Java test paths use
`src/test/java/com/example/bulgariantrainer/`. These prefixes are abbreviated as
`java/` and `test/` only in this table.

| Feature | Actual implementation files | Existing representative tests |
| --- | --- | --- |
| Vocabulary, translations, archive/search/delete protection | `java/service/WordService.java`, `java/service/VocabularyText.java`, `java/entity/Word.java`, `java/entity/Translation.java`, `java/repository/WordRepository.java`, `java/dto/WordRequest.java`, `java/dto/TranslationRequest.java`, `src/main/resources/static/js/vocabulary.js` | `test/VocabularyIntegrationTests.java` |
| TXT/CSV vocabulary import, preview, append/skip | `java/service/VocabularyImportParser.java`, `VocabularyImportService.java`, `VocabularyImportWriter.java`, `ImportPreviewStore.java` in the same service directory; `java/dto/ImportAction.java`; `src/main/resources/static/js/import.js` | `test/service/VocabularyImportParserTests.java`, `test/ImportIntegrationTests.java` |
| Flashcards | `java/service/FlashcardService.java`, `java/entity/FlashcardQuestion.java`, `java/dto/FlashcardDtos.java`, `src/main/resources/static/js/flashcards.js` | `test/FlashcardIntegrationTests.java` |
| Bidirectional typing and queue selection | `java/service/TypingService.java`, `java/service/TypingQuestionPlanner.java`, `java/entity/TypingQuestion.java`, `java/dto/TypingDtos.java`, `src/main/resources/static/js/typing.js` | `test/TypingIntegrationTests.java`, `test/service/TypingNormalizationTests.java` |
| Sentence management/import | `java/service/SentenceService.java`, `SentenceImportParser.java`, `SentenceImportService.java`, `SentenceImportWriter.java` in the same service directory; `java/entity/Sentence.java`; `src/main/resources/static/js/sentence-import.js` | `test/SentenceIntegrationTests.java` |
| Sentence translation and self-assessment | `java/service/SentencePracticeService.java`, `java/entity/SentenceQuestion.java`, `java/entity/SentenceAttemptWord.java`, `java/dto/SentenceDtos.java`, `src/main/resources/static/js/sentences.js` | `test/SentenceIntegrationTests.java` |
| Persistence/retries | `java/entity/StudySession.java`, `StudyAttempt.java`, the three question entities and repositories; `src/main/resources/application.properties`; the three training JS files | Session retry/snapshot/concurrency cases in the three integration suites; `test/MigrationPersistenceTests.java` |
| Review scheduling | `java/service/SpacedRepetitionScheduler.java`, `java/entity/StudyProgress.java`, `java/entity/SentenceProgress.java`; selection in the three training services | `test/service/SpacedRepetitionSchedulerTests.java`, scheduling cases in flashcard/typing tests |
| Statistics | `java/service/StatisticsService.java`, `java/dto/StatisticsDtos.java`, `java/repository/StudyAttemptRepository.java`, `StudyAttemptSnapshot.java` in the same repository directory; `src/main/resources/static/js/statistics.js` | `test/StatisticsIntegrationTests.java` |
| Pronunciation and answer visibility | `src/main/resources/static/js/pronunciation.js`, calls from `vocabulary.js`, `flashcards.js`, `typing.js`, `sentences.js` | `src/test/js/pronunciation.test.mjs` |

## Implemented web rules and representative cases

### Vocabulary and accepted translations

- A word has required Bulgarian text (max 255 UTF-16 code units), 1–50 Russian
  translations (each max 255), optional part of speech/gender (max 100 each),
  and notes (max 4000). Blank required fields are rejected. DTO constraints are
  applied before service normalization. Optional cleaned empty strings become null.
- Bulgarian uniqueness is by NFC, collapsed Unicode whitespace, trim, and
  `Locale.ROOT` lowercase. Archived entries still reserve their normalized key.
  Russian translation keys must be unique within a word. At most one explicitly
  primary translation is accepted; if none is specified, the first becomes primary.
  Responses order primary first, then translation ID.
- All stored Russian translations are independent accepted answers, including
  the non-primary ones. A comma or slash inside one translation is literal text,
  not an automatic list of accepted answers.
- Search matches normalized Bulgarian or Russian substrings, escapes `%`, `_`,
  and `!` as literal SQL search characters, can combine exact case-insensitive
  part-of-speech and archive filters, and paginates in ascending word ID.
  Page is nonnegative, default size 20, range 1–100; oversized offsets are rejected.
  `controller/WordController.java` additionally limits the raw search query to
  255 characters and the part-of-speech filter to 100; Android enforces these bounds.
- Archive/restore preserves translations and history. A hard delete is rejected
  when any progress, attempt, sentence link/usage, or queued word question exists.
  Viewing and archiving do not award progress. No automatic vocabulary seeding.

Existing cases: `VocabularyIntegrationTests.duplicateCreationUsesUnicodeCaseAndWhitespaceNormalization`,
`archivesAndRestoresWithoutLosingTranslationsOrStudyHistory`,
`combinesBilingualSearchPartFilterAndPagination`; parser case
`preservesHyphenatedMultiwordExpressionsAndCommas` preserves `ехать, идти` as one value.

### Normalization shared by vocabulary and answer evaluation

`service/VocabularyText.java` uses `Normalizer.Form.NFC`, the pattern `\\s+`
with `UNICODE_CHARACTER_CLASS`, replacement by one ASCII space, `.strip()`,
and `toLowerCase(Locale.ROOT)` for keys. Raw submitted answers are retained.
There is no fuzzy match, transliteration, accent removal, punctuation stripping,
or conversion between Cyrillic and Latin lookalikes.

Existing `service/TypingNormalizationTests` cases establish:

| Inputs | Expected comparison |
| --- | --- |
| `МОИ\u0306` + NBSP + repeated spaces + `ДОМ` versus `мой дом` | Equal after NFC/whitespace/case normalization |
| `Е\u0308ЛКА` versus `ёлка`; `И\u0300` versus `ѝ` | Equal |
| `къща` / `каща`; `мой` / `мои`; `ѝ` / `и`; `ёлка` / `елка` | Different |
| `дом` versus `дoм` with Latin `o` | Different |
| An answer with a different period or comma | Different; sentence mismatch proceeds to self-assessment |

Android should use this normalization contract, including Unicode whitespace
and locale-independent case, rather than Kotlin's default regex assumptions.

### Vocabulary import

- Only `.txt`/`.csv` (case-insensitive filename), strict UTF-8 with optional leading
  BOM, max 1 MiB and 2000 records. CSV headers and blank records count toward the
  record limit. Invalid UTF-8 is rejected, not replaced silently. Source lines,
  row IDs, and multiline ranges are retained; filenames are display names only.
- TXT splits at the first `=` or hyphen/en dash/em dash adjacent to whitespace;
  if none exists, tabs separate the pair. An internal hyphen in `горе-долу` is
  preserved. Multiple separators, Latin/no-Cyrillic text, slash alternatives,
  comma/list text, annotations and multiline CSV values produce review warnings.
  Missing values, overlong fields and disallowed control characters are invalid.
  There is no language-direction guessing or automatic alternative splitting.
- Vocabulary CSV is comma-delimited with exactly `bulgarian,russian` and optional
  `partOfSpeech`; header order can change and header names are normalized.
  Quoted commas, escaped double quotes, CR/LF/CRLF, and multiline fields work.
  Unsupported headers, column counts and malformed quotes remain visible as
  invalid rows. A terminal newline does not create an additional empty TXT row.
- Preview writes no vocabulary. Duplicate detection compares normalized Bulgarian
  text within the file and database, including archived words. Only `READY` rows
  are selected initially; `WARNING`, `INVALID`, `EMPTY`, and `HEADER` require
  attention/deselection. Vocabulary confirmation can accept explicitly corrected
  selected rows after complete validation.
- Explicit confirmation supports `CREATE`, `ADD_TRANSLATION`, `SKIP`.
  `CREATE` rechecks duplicates, including words created after preview or earlier
  in the same confirmation, and skips them. `ADD_TRANSLATION` checks the target
  ID still has the expected Bulgarian key, adds only a missing Russian value,
  honors the 50-translation limit, and preserves metadata, primary translation,
  archive state, and learning history. It never overwrites an existing word.
- Confirmation is transactional. Unknown/repeated row IDs or invalid selected
  values fail without partial writes. The same preview and exactly identical
  confirmation request return the cached summary; a changed request conflicts.
  Previews expire after 30 minutes, are limited to five in memory, and disappear
  after a server restart. Retry caching is only guaranteed while that preview lives.

Existing cases: `VocabularyImportParserTests.supportsObservedSeparators`,
`csvRetainsMultilineSourceAndRecoversAfterMalformedRecord`,
`malformedCsvNeverDiscardsUnclosedRecordsOrGuessesHeaders`;
`ImportIntegrationTests.skipsDuplicatesIncludingWordsCreatedAfterPreviewAndEarlierInSameImport`,
`explicitAppendPreservesMetadataPrimaryTranslationAndHistory`,
`invalidSelectedRowPreventsAllWritesButCanBeCorrectedAndRetried`,
`wrongAppendTargetRollsBackPreviouslyProcessedRows`.

### Flashcards

- Defaults: 20 cards, `BG_TO_RU`, `MIXED` study scope; requested size 1–100.
  Eligible words are unarchived with at least one translation. Scope filters
  NEW/DUE; mixed selection prioritizes DUE, NEW, LEARNING, MASTERED, shuffling
  within tiers. The selected queue is shuffled again, so display order need not
  show due cards first. Fewer available words produces a shorter queue.
- Each word occurs once. `RU_TO_BG` displays all Russian translations and
  reveals Bulgarian; `BG_TO_RU` reverses that. Mixed direction alternates with
  a random starting direction. Text/translations/direction are persisted snapshots.
- Before reveal the answer list is empty. A rating requires the current revealed
  card. Reveal/view creates no attempt. Known/unknown produces CORRECT/INCORRECT
  and updates only the word's shared FLASHCARD schedule, irrespective of direction.
- Request UUID and identical settings reuse a session; changed settings conflict.
  Repeated identical ratings create no additional attempt/schedule change even
  after completion; conflicting ratings and foreign/stale questions are rejected.
  Session/word locks and database uniqueness protect concurrent updates.
- Rating the last card completes the session. Early completion abandons it;
  unrated cards appear as SKIPPED in results but do not create skipped attempts.
  Results cannot be requested while active.

Existing cases: `FlashcardIntegrationTests.refusesHiddenStaleAndForeignQuestionsAndDoesNotExposeEarlyResults`,
`ratingRetriesAreIdempotentEvenAfterCompletionAndChangedRatingsConflict`,
`concurrentRatingsCannotDoubleCountAndProgressIsSafeAcrossSessions`,
`snapshotsSurviveVocabularyEditsAndSessionWordsCannotBeDeleted`,
`dueAndNewQueuesRespectScheduleAndRetryDoesNotScheduleTwice`.

### Bidirectional typing

- Standard size is 30 distinct active words; fewer requires explicit
  `allowShorter: true`, otherwise `SHORTER_SESSION_REQUIRED`. Empty queues do
  not create sessions. Availability counts distinct words eligible in either
  typing direction for the requested scope and offers `min(30, count)`.
- `TypingService.select` builds separate eligible BG→RU and RU→BG lists,
  shuffles, sorts each by category and direction exclusivity/opposite due state,
  then greedily chooses from the less-used direction (BG first when tied),
  falling back to the other if needed. It uses each word once and shuffles the
  final queue. Ordinary all-new sessions have 15/15 for 30 words and differ by
  at most one for shorter sessions. Scoped eligibility can prevent balance;
  see the documented defects below before porting this selector.
- BG→RU accepts any snapshotted Russian translation; RU→BG accepts only the
  snapshotted Bulgarian entry. Before submission only the prompt and null
  feedback are returned. Submission produces automatic CORRECT/INCORRECT,
  stores the original answer, and updates only the chosen direction's schedule.
- A repeated question ID and exactly identical raw answer returns saved state;
  even a differently spaced but normalized-equivalent replay conflicts. Feedback
  remains visible until explicit acknowledgement (`next`), including the last
  answer after automatic session completion. Results list incorrect submissions.

Existing cases: `TypingIntegrationTests.exactDistributionUniqueWordsAndDurableOrderWithoutAnswerLeaks`,
`shorterRequiresExplicitConsentAndIsBalancedWithoutRepetition`,
`acceptsMultipleTranslationsAndUnicodeButKeepsModesIndependent`,
`strictSpellingAndDirectionWithMistakeReview`,
`typingDirectionsHaveSeparateSchedulesAndDueQueueCanBeEmpty`,
`mixedTypingPrefersTheDirectionThatIsDue`, `concurrentSubmissionIsIdempotent`.

### Sentences, import, translation, and self-assessment

- Russian prompt and Bulgarian reference are required, max 4000 each. Russian
  normalized text is the duplicate key for new/edited sentences. Up to 50 distinct
  Bulgarian alternatives, excluding the normalized main answer; explanation max
  4000, topic max 255, optional difficulty 1–5. Linked word IDs must exist and be
  unique positive IDs. Creating/editing a sentence marks it approved. Archive
  preserves history; training requires approved and unarchived.
- Sentence import requires CSV and the exact seven-column, case-sensitive order:
  `id,russian,bulgarian,alternativeTranslations,grammaticalExplanation,difficulty,topic`.
  Limits: strict UTF-8/BOM, 1 MiB, shared CSV parser's 2000-record cap. Alternatives
  must be a JSON string array. Source ID and integer difficulty 1–5 are required
  by this importer, even though manual sentence difficulty is optional. Source ID
  is display provenance, not database identity. Only READY rows start selected.
- Preview can explicitly read `data/bulgarian_sentences_100.csv`; this never
  imports automatically. The file has 100 data rows, 20 alternatives, difficulties
  1/2. Confirmation uses corrected selected sentence objects, cannot select
  HEADER/EMPTY rows, skips normalized Russian duplicates without overwrite,
  is transactional and has the same 30-minute/five-preview in-memory retry limits.
- Practice defaults to 10 distinct sentences (1–100 requested), with shorter
  sessions allowed. Category priority precedes preference for links to due
  vocabulary within a tier. Russian text, main/alternative Bulgarian answers,
  and explanation are snapshotted. No answers/explanation appear before submission.
- A normalized approved match immediately records CORRECT/AUTOMATIC. A mismatch
  records PENDING, reveals references/explanation, and requires self-assessment
  CORRECT, PARTIAL, or INCORRECT with source SELF. It is not an automatic grammar
  judgment. PENDING creates no sentence progress; only finalized evaluation
  schedules the sentence. PARTIAL resets the streak and schedules a one-day retry.
- Linked vocabulary usage is recorded separately in `sentence_attempt_words`,
  including a pending response. It does not update word progress or mastery.
  Acknowledgement requires final evaluation. Exact answer/self-rating replays
  are idempotent, changed evaluations conflict. Early completion keeps pending
  responses as unevaluated; unanswered questions are displayed as skipped.

Existing cases: `SentenceIntegrationTests.suppliedCsvPreviewAndConfirmationAreControlledAndDuplicateSafe`,
`quotedCsvJsonAlternativesAndInvalidRowsRemainVisible`,
`unchangedAlternativeCanBeSavedAgain`,
`mismatchRequiresSelfAssessmentAndRecordsVocabularyUsageWithoutMastery`,
`manualCorrectAndIncorrectAreDistinctFromAutomatic`,
`emptyCollectionUniqueSessionQuestionsAndEarlyCompletion`,
`reviewVocabularySentenceIsPrioritizedWhenThereAreOtherChoices`.

### Durable sessions and review scheduling

- H2 file storage is `./data/bulgariantrainer` with `WRITE_DELAY=0`, Flyway enabled,
  Hibernate validation and Flyway cleaning disabled. Queues, snapshots, reveal,
  submissions, acknowledgement, evaluation source, counters and status survive
  database reopening. The browser stores session ID and an uncertain operation
  in `localStorage` and a `?session=ID` URL, then retries exactly that operation.
  Unsaved typing drafts are not persisted; previews are not durable sessions.
- One atomic transaction records the accepted review and its progress changes.
  Merely viewing/revealing, pending self-assessment, or abandoning unanswered
  questions awards nothing. The uniqueness constraints enforce one question
  position and target per session and one linked attempt per question.
- Independent schedules: FLASHCARD per word, TYPING_BG_TO_RU per word,
  TYPING_RU_TO_BG per word, and one per sentence. Legacy aggregate TYPING is
  retained but does not receive new direction-specific reviews.
- Correct streak intervals: 1, 3, 7, 14, 28, 56, 112, 224, 365, then 365 days.
  Incorrect or PARTIAL resets streak to zero and schedules 1 day, preserving
  lifetime successes and attempts. Days are exact 24-hour durations added to
  UTC review instants, not local-midnight dates. `ease_factor` is retained in
  persistence but is not used by this scheduler; this is not SM-2/Anki.
- Category precedence: NEW if attempts=0; otherwise DUE if next date is missing
  or `next <= now`; otherwise MASTERED with consecutive successes ≥5 and interval
  ≥28 days (both configurable); otherwise LEARNING. Mastered becomes due again.
  DUE/NEW scopes require that category; MIXED includes all four categories.

Migration evidence, all in `src/main/resources/db/migration/`:

| Migration | Actual addition / upgrade |
| --- | --- |
| `V1__create_vocabulary_and_study_tables.sql` | Words/translations/sentences/links; unique progress; sessions/attempts; constraints and indexes |
| `V2__archive_vocabulary.sql` | Word archive flag and archive/part index |
| `V3__flashcard_sessions.sql` | Start UUID/settings, unique snapshot queue and reveal state |
| `V4__typing.sql` | Typing start UUID/shorter consent, unique snapshot queue and acknowledgement |
| `V5__sentence_practice.sql` | Sentence normalized Russian/topic/approval/archive; alternatives/progress/snapshot queue/usage links; PENDING/PARTIAL and grading sources |
| `V6__spaced_repetition.sql` | Scope; separate typing-direction schedules; lifetime successes; sentence schedule/streak fields; existing reviewed records made due |

V6 retains aggregate typing progress, seeds direction rows only from recorded
direction-bearing CORRECT/INCORRECT typing attempts, sets unknown historical
direction streaks/intervals to zero, and uses last attempt as due date. It does
not fabricate direction history where individual attempts are missing.

Existing cases: `MigrationPersistenceTests.fileDatabaseMigrationPreservesDataAcrossReopenAndEnforcesConstraints`,
`upgradingExistingProgressPreservesHistoryAndMakesItDue`;
`SpacedRepetitionSchedulerTests.successfulReviewsUseOneThreeSevenThenDoubleAndBecomeDueAtExactInstant`,
`failureResetsStreakButPreservesLifetimeSuccessesAndSeparateModes`,
`sentencePartialAndIncorrectScheduleShortRetryWithoutSuccess`.

### Statistics

- Derived from real current records; no stored aggregate dashboard or demo data.
  Vocabulary total includes archived words. New/due/mastered use active trainable
  words. New means no historical word review/progress in any mode; sentence
  usage does not count. Due is any of the three current word schedules being due;
  mastered requires all three to be MASTERED. Each word is counted once.
- Finalized attempts are CORRECT/INCORRECT/PARTIAL, including abandoned sessions.
  PENDING/SKIPPED are excluded. Accuracy is correct/finalized ×100 rounded to
  one decimal, null for zero attempts. PARTIAL contributes only to denominator.
  Flashcards are self-assessed, typing automatic, sentences use AUTOMATIC/SELF;
  old missing-source sentence grades are unclassified.
- Completed-session count uses only COMPLETED, not active/abandoned. Scheduled
  today counts individual active word-mode and approved active sentence schedules
  whose next review falls on today's UTC date; older overdue schedules are excluded.
  Streak is consecutive UTC finalized-attempt dates ending today or yesterday.
  Rolling 7/30-day series include today and explicit zero days; completion is
  counted on completion date, attempts on their saved creation date.
- Difficult words: max 20, ranked by incorrect word attempts descending, last
  incorrect time descending, ID ascending, including archived words. Details
  show up to 20 recent incorrect answers and **current** translations. Recent
  sessions: max 10 by start time/ID descending; rates derive from finalized attempts.

Existing `StatisticsIntegrationTests` cases: `emptyDatabaseHasZeroCountsAndUndefinedAccuracy`,
`calculatesRealRetentionAccuracySourcesActivityAndMistakeReview` (3 correct out
of 5 finalized =60.0%; a pending answer excluded),
`repeatedFlashcardRatingCountsOnceInStatistics`,
`difficultWordsAreLimitedToTwentyAndRankedByIncorrectAttempts`.

### Pronunciation and visibility

- Web Speech synthesis accepts only voice languages matching `bg` or `bg-*`,
  case-insensitively; no Russian/English fallback. Voice discovery waits up to
  1800 ms by default, handles delayed `voiceschanged`, and supports retry.
  User-selected voice identity is stored locally; another Bulgarian voice is
  used if it disappears. Missing/unsupported/error states are explicit.
- Speech is user-triggered only. Normal rate=1, slow=0.5; starting another
  utterance cancels previous speech. Stop, page hide, question changes and
  controls disposal stop playback; stale engine callbacks cannot replace current
  state. Playback does not update learning progress.
- Vocabulary list: Bulgarian text is available immediately. Flashcards BG→RU:
  prompt audio immediately; RU→BG: Bulgarian only after reveal. Typing BG→RU:
  prompt audio immediately; RU→BG: accepted Bulgarian only after submission.
  Sentences: primary and approved alternatives only after feedback, including
  pending self-assessment. No hidden-answer pronunciation leaks.
- Web voice implementations can require remote synthesis; offline playback is
  not guaranteed by the reference. Android must check Bulgarian `TextToSpeech`
  voice availability and `isNetworkConnectionRequired == false`; show a missing
  local voice state if unavailable. Do not silently add cloud speech or a fallback
  language. This Android functionality is implemented; see PRONUNCIATION.md for engine checks,
  differences and remaining physical-device validation.

Existing `src/test/js/pronunciation.test.mjs`: nine tests cover saved Bulgarian
voice selection, delayed discovery/retry, missing/unsupported states, empty text,
rapid replay/cancellation, slow rate, engine failures/recovery, control disposal,
and `only exposes Bulgarian text at the correct stage in each mode`.

## Documentation discrepancies

1. `FLASHCARDS.md` says no mastery/scheduling and scheduling fields retain defaults;
   `TYPING.md` says only aggregate TYPING progress changes and no scheduling/mastery.
   Current services invoke `SpacedRepetitionScheduler`, and typing writes two
   direction-specific modes. README's later SRS section matches current code.
2. README's early persistence-model section lists only FLASHCARD/TYPING/SENTENCE
   progress modes and CORRECT/INCORRECT/SKIPPED results. V5/V6 and enums also
   include direction-specific typing progress, PENDING/PARTIAL, and grading source.
3. `SENTENCES.md` says linked words with no correct streak are prioritized. The
   actual `SentenceRepository.findDueLinkedIds` checks attempts>0 and due/missing
   next date, not streak=0. Sentence category has priority over linked-word due state.
4. `TYPING.md` promises exactly 15/15 (or difference≤1). This describes unconstrained
   new queues, but scoped eligibility can force a direction imbalance. README's
   universal mixed due-first statement is also weaker in typing's greedy selector.
5. `AGENTS.md` describes an initial setup-only web milestone. The pinned source
   already contains subsequent vocabulary/training/SRS/statistics implementations;
   do not infer feature absence from that historical milestone text.

## Suspected defects / decisions requiring explicit treatment

These are source-inspection findings. The existing 103-test Java baseline passed
on CI, but new regression fixtures for the specific suspected defects below were
not added or executed. Preserve intended behavior; do not silently replicate these risks.

1. **Typing due priority versus balancing.** `TypingService.select` chooses BG
   first on ties and balances direction before comparing the other direction's
   top category. With 30 words all NEW in BG→RU and DUE in RU→BG, it can spend
   roughly half the queue on NEW directions while consuming the same words'
   due-direction opportunities. In a DUE-only queue with all words eligible only
   in one direction, it necessarily cannot satisfy 15/15. Add fixtures for both;
   decide/document priority and achievable balance before implementing Android.
2. **Live sentence-word links versus snapshots.** `SentenceQuestion` snapshots
   answers/explanation, but `SentencePracticeService.answer` reads the sentence's
   current links. Editing links between start and answer changes vocabulary usage
   attribution for the old question. Decide whether to snapshot linked IDs too;
   cover edit-after-start attribution explicitly.
3. **Legacy sentence duplicate keys.** V5 adds nullable `normalized_russian` without
   backfilling existing rows and leaves old sentences unapproved. New creation
   checks only that normalized column, so a matching pre-V5 Russian sentence
   can be duplicated. Approval requiring review may be intentional; missing-key
   uniqueness needs an explicit migration/collision strategy, not invented history.
4. **Concurrent start UUID race.** Each service does find-by-request-ID before
   insert, with a unique constraint but no retry/reload around a concurrent insert.
   Two simultaneous first requests may return one session and one generic
   DATA_CONFLICT instead of both resolving to authoritative state. Existing
   concurrent rating/answer tests do not establish concurrent-start behavior.
   Android should serialize local creation and enforce UUID uniqueness atomically.
5. **Mixed clock sources.** Scheduling uses injectable `Clock`, while session and
   attempt timestamps use `Instant.now()`. Fixed-clock/date-boundary tests can
   disagree on schedule versus history days. Use one injected clock in Android
   repositories/use cases and specify UTC date handling; no background timer is
   required for computing due state on demand.
6. **Preview validation gaps.** Sentence preview does not reject normalized
   duplicate alternatives against the main answer; confirmation's SentenceService
   does. A READY row can therefore fail confirmation. Duplicate sentences are
   skipped before validating all other corrected fields. Keep transactionality,
   but add a preview/confirmation consistency fixture and explicit skip semantics.

## Proposed Android implementation stages

0. **Completed foundation audit.** Kotlin/Compose/Material 3 welcome
   screen, pinned wrapper/AGP/SDK configuration, local Studio instructions, CI
   build recipe, and this parity document. No training or persistence features.
   The debug build and lint passed on GitHub Actions; physical-device acceptance
   remains unverified. See BUILD_REPORT for the exact tested revision and artifact.
1. **Local domain/storage.** Room/SQLite schema for words, translations, sentences,
   links, snapshots, sessions, attempts and independent schedules; foreign keys,
   unique constraints and atomic writes. Vocabulary storage is implemented; later
   learning tables remain deferred. Add normalization goldens, injected clock,
   migration/reopen tests. H2/Flyway SQL is a contract reference, not executable
   Android migrations. No server or login. Decide backup/export separately.
2. **Vocabulary and controlled imports.** Dashboard, Russian vocabulary UI,
   create/edit/search/archive, controlled word/sentence imports and content exports
   are implemented. History-protected deletion remains future work. Storage Access
   Framework, strict parsers and preview/edit/
   confirm. Port transactional duplicate/append cases and ensure no automatic
   personal/sample import. Decide whether local preview drafts survive process death.
3. **Flashcards and durable session state — implemented.** Persisted queue/reveal/rating snapshots,
   UUID replay and completion atomically. Verify rotation/process-death/reopen,
   early exit and no award for viewing. Keep answer/pronunciation visibility aligned.
4. **Typing (implemented).** See TYPING.md: guaranteed balanced all-active
   selection, independent direction counters and durable feedback; scheduler
   eligibility/priorities remain deferred. Original stage proposal: bidirectional
   selection, exact normalized matching, original answers, feedback acknowledgement,
   shorter-session consent, snapshots and replay. Test constrained DUE/NEW queues.
5. **Sentence practice — implemented.** See SENTENCES.md. Approved sentence management/CSV, alternatives/explanations,
   automatic exact matches and pending self-assessment. Resolve link-snapshot policy;
   verify partial/incorrect grading, pending early exit and isolated usage/progress.
6. **Scheduling and statistics — implemented.** See REVIEW_AND_STATISTICS.md. Shared scheduler across modes,
   then derived dashboard using exact interval/category/UTC/rounding rules. Validate
   clock boundaries, due/mastered transition, direction isolation, replay safety,
   7/30-day series, null accuracy and archive behavior with deterministic fixtures.
7. **Offline pronunciation implemented; device acceptance remains.** Local Bulgarian TTS only,
   normal/slow/stop, voice discovery/errors and visibility rules. Verify airplane
   mode, missing-voice behavior, real device audio, Cyrillic input, TalkBack,
   font scaling, dark mode, narrow screens and process recovery. Build/install a
   debug APK; actual CI evidence is in BUILD_REPORT.md. Offline voice availability
   and quality still require the physical checks in PRONUNCIATION.md.

## Verification status

`node src/test/js/pronunciation.test.mjs`: **9 tests passed**. The documented
`node --test src/test/js/pronunciation.test.mjs` command also exited 0; this
runtime reported one passing file-level test, so direct execution was used to
confirm all nine named cases. Local Maven Java tests did not run because the Maven
distribution could not be downloaded. Local Android Gradle bootstrap failed before
test/build tasks ran. On GitHub Actions, revision
`d07a29ed142f0fa7e011bd2324df325237c677c2` successfully executed
`./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug` and uploaded
the debug APK. The Android unit-test task was NO-SOURCE, not a passing test suite.
No device/runtime check was performed. The pinned reference baseline also ran on
GitHub Actions: `sh ./mvnw --batch-mode clean verify` passed **103 tests** with zero
failures/errors/skipped; pronunciation passed **9 tests**, and a final
`git diff --exit-code` confirmed tracked source was unchanged. Exact commands,
run links and results are recorded in
[BUILD_REPORT.md](BUILD_REPORT.md).
