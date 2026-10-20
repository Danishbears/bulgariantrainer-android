# Offline personal-use acceptance

Read [BUILD_REPORT.md](BUILD_REPORT.md) for executed checks and the exact tested
revision. This checklist distinguishes JVM/native-SQLite evidence from device
acceptance. No connected Android device or emulator is available in this workspace;
there are currently no `androidTest` sources or configured instrumentation runner.
Instrumentation tests were **not executed**. Do not read a JVM pass as a phone,
IME, real process-kill, airplane-mode, voice or update-install pass.

## Implementation and evidence map

| Area | Existing automated coverage | Remaining device acceptance |
| --- | --- | --- |
| Vocabulary | `VocabularyRepositoryTest`, `VocabularyValidatorTest`, `WordEditorViewModelTest`: alternatives, bilingual/literal search, archive, counts, paging, transaction rollback, reopen | Create/edit/archive/restore; search a secondary translation; scroll a large real dictionary |
| Imports/exports | `ContentParserTest`, `ContentTransferRepositoryTest`, `TransferViewModelTest`: strict UTF-8/BOM, quoted multiline CSV/JSON, diagnostics, selected rows, duplicates/explicit append, progress isolation and round-trips | Android document providers, cancel picker, edit preview and confirm, share/reopen exports |
| Flashcards | `FlashcardRepositoryTest`, `FlashcardViewModelTest`: all directions, hidden answer, reveal awards nothing, one rating, early finish and durable recovery | Exercise BG→RU, RU→BG and mixed, repeat taps, force-stop at hidden/revealed/rated stages |
| Typing | `TypingRulesTest`, `TypingRepositoryTest`, `TypingViewModelTest`: distinct 30/15+15 unconstrained, shorter consent, alternatives/normalization, persisted text/results and idempotence | Cyrillic IME/Done, hide keyboard, final feedback, force-stop before/after submission and next |
| Sentences | `SentenceRepositoryTest`, `SentenceViewModelTest`: management, approved alternatives, pending self-assessment, one finalized attempt, reopen | Long multiline text, all three self-ratings, early completion and pending-answer process recovery |
| Progress/scheduling | `ReviewSchedulerTest`, `ReviewIntegrationTest`: literal web histories, four independent modes, failure/PARTIAL, category precedence, DST/UTC, duplicate updates | Advance a few real sessions; inspect independent due queues after reopening and clock/time-zone changes |
| Statistics | `StatisticsCalculatorTest`, `StatisticsViewModelTest`, integration tests: real counts/rates, AUTO/SELF, UTC activity and no replay duplication | Reconcile known answers and early/completed sessions against displayed totals |
| Backup/restore | `BackupFormatTest`, `BackupRepositoryTest`, `BackupViewModelTest`: all tables/IDs/settings, WAL consistency, staging validation, confirmation, rollback and restored schedules | SAF storage provider, cancellation, malformed file, replace confirmation, app reopening and recovery after restore |
| Offline/TTS | No network client/INTERNET permission; APK permission checks; `PronunciationTest`/`PronunciationLearningTest` use a fake adapter | Physical airplane-mode test; installed Bulgarian local voice, sound quality, missing voice and system setup |
| Rotation/layout | SavedStateHandle ViewModel recovery and lifecycle-aware state in code; scrollable/Paging Compose screens, IME padding | Rotation, background/resume, 320dp portrait, landscape, enlarged font, TalkBack, gesture/navigation insets |
| App updates | Room migrations 1→7 and future-schema rejection tests; v7 unchanged for code 2 | Real code-N→N+1 install signed by the same persistent release key, preserving all local data |

## Repeatable device run

Use a spare test profile or make a full backup first. Record device/Android
version, APK commit/versionCode/certificate, keyboard, screen/font settings and
each result. An empty first install must show zero real records; sample data is
imported only by your explicit action.

1. Add words with several Russian translations, punctuation and multi-word
   Bulgarian expressions. Search both languages and archive/restore one entry.
   Import a BOM UTF-8 file, quoted multiline CSV and a file with invalid/duplicate
   rows. Confirm preview alone changes nothing; confirm selected rows and explicitly
   append translations. Reimport and check no unintended duplicate or progress loss.
   Export, inspect and reimport content. Use the bundled sentence CSV only via
   the explicit preview button.
2. With at least 30 eligible distinct words, run mixed-scope typing and confirm
   30 unique entries/15+15. Try smaller and scoped queues. NEW/DUE scopes can
   constrain directions; the actual counts are displayed rather than repeating
   entries. Test each flashcard direction; answers and Bulgarian target speech
   remain hidden until reveal/submission. Tap rating/submit/next rapidly and
   verify only one attempt/schedule update per question.
3. Create/edit/archive a sentence; submit its approved alternative, then an
   unmatched variant. Check that references appear after submission and mismatch
   asks for clearly labelled self-assessment, not a claimed grammar error. Try
   correct/partial/incorrect; adding self-assessment must finalize the same answer.
4. For every mode, force-stop from Android App info while active, then reopen and
   return to that mode. With USB tools: `adb shell am force-stop
   com.danishbears.bulgariantrainer`, then launch from the phone. Check original
   order, direction, position, reveal, submitted text, feedback and pending sentence
   self-rating. Repeat after acknowledgement and early completion. Unsubmitted
   input drafts are not guaranteed after force-stop; saved submissions are. Removing
   an activity or enabling “Don't keep activities” alone does not prove process death.
5. Write down finalized responses in each mode and compare independent progress,
   due categories, completed sessions, attempts, AUTO versus SELF and difficult
   items. Reopen a resumed session twice; totals must not increase. Date labels
   use UTC days for web parity; displayed instants may use device local time.
   Restore the real device clock after testing clock/time-zone changes.
6. Export a **full JSON backup** to a durable location. Add a temporary word,
   select the backup and inspect its summary. Cancel once: the word must remain.
   Select a corrupt/unsupported file: existing data must remain. Confirm the valid
   replacement: verify content, alternatives, IDs/history, schedules, settings,
   active sessions and correct post-restore navigation. Keep a separate safe copy;
   full backups contain your learning history and are not encrypted by the app.
7. Download Bulgarian voice data in Android's Text-to-speech settings while online.
   Enable airplane mode and explicitly turn Wi-Fi off. Force-stop/reopen the app;
   try CRUD, imports/exports in local Files, all training, recovery, statistics and
   backup/restore. Cloud document providers may require network; choose local Files.
   Test normal/slow Bulgarian playback, rapid replay, stop, question change,
   backgrounding and missing-voice guidance. Training must work even when no local
   voice is installed. Engine metadata and fake tests cannot prove actual offline
   synthesis or pronunciation quality; see [PRONUNCIATION.md](PRONUNCIATION.md).
8. Repeat the important screens with the keyboard open, rotation, large fonts,
   small width/landscape and long answers/metadata. All controls should remain
   reachable by scrolling; input and submissions must not disappear or duplicate.
9. Test two consecutive **release-key-signed** builds with increasing versionCode.
   Before updating, record dictionary/archive counts, all-mode history/schedules,
   pending sessions and settings, and save a full backup. Install with `adb install -r`
   or Android's update prompt, keeping storage intact. Reconcile all recorded data.
   Debug signatures are unsuitable as a guarantee for successive cloud updates.

## Deliberate differences from the pinned web app

- Kotlin/Compose/Room with local device storage, not a browser, Spring server or
  H2. No account, network service, AI, sentence generation or synchronization.
- Web normalized matching, intervals, mastery, scopes and statistics definitions
  are retained. The device clock replaces the server clock; reviews use elapsed
  24-hour intervals and statistics UTC day boundaries. Changing the device clock
  can change which reviews are due.
- The actual web greedy scoped typing planner can produce a direction imbalance;
  Android documents/displays it. Unconstrained new queues are 15+15. See
  REVIEW_AND_STATISTICS.md for the due-priority defect and parity fixtures.
- Sentence-vocabulary associations/linked-word tie-breaks are not exposed or
  inferred. Sentence practice never awards vocabulary mastery.
- Android rejects preview validation gaps and serializes duplicate operations.
  Optional manual sentence difficulty is retained; web-compatible CSV export
  requires an explicit valid 1–5 value instead of inventing one.
- Android speech requires an installed Bulgarian voice advertised as local;
  browser speech can use network synthesis. Voice identity is selected by Android
  availability rather than a persisted browser voice preference.
- Durable queues recover from Room rather than browser localStorage/server IDs.
  Unconfirmed import previews are temporary. Full backup is a separate versioned
  Android JSON snapshot; web-compatible CSV exports contain content only.
