# Native Android aorist UI

Date: 2026-11-21. Candidate **0.7.1 / versionCode 9**, on
`codex/android-aorist-plan`; no default-branch merge. ApplicationId remains
`com.danishbears.bulgariantrainer`. Kotlin, Compose Material 3, Room 11,
full-backup v5, dependency versions and the existing signing workflow are preserved.
This UI stage adds no schema change and does not rewrite learning history.

## Open, import and manage

1. Open **Главная → Аорист**. The existing five bottom destinations are unchanged;
   nested aorist screens use the app's existing accessible Back arrow instead.
2. For the supplied set, tap **Просмотреть стартовый набор: 40 глаголов**.
   Alternatively choose **Выбрать JSON аориста** to use Android's document picker.
3. Inspect the filename, dataset ID/revision, counts and errors. New entries are
   selected; changed entries need individual selection. Expand a row to see its
   forms and provenance. No live content changes during preview.
4. Tap **Подтвердить импорт**, then **Сохранить выбранное**. Invalid files cannot
   be confirmed. Stale previews are rejected by the existing transaction service.
   Reimport does not duplicate identities or replace progress.
5. Search Bulgarian/Russian text, apply aspect/dataset/study-group/particle filters,
   or show **Только архив**. Listing remains paginated, 25 entries per page.
6. Open an entry for its six-person conjugation table, note and source. Rows wrap
   without fixed height. Labels and forms share accessible row semantics; they
   are table cells, not constructed Bulgarian sentences.
7. Edit the six explicit accepted-answer fields, one alternative per line. Field
   errors are marked in place; forms are never inferred from suffixes. Existing
   IDs are read-only. Archive/restore preserves identities and history.
8. **Экспортировать учебный JSON** creates a document for the selected dataset,
   optionally including archived content. This is content-only JSON v1, not a
   backup of sessions or progress. Full manual backup remains a separate feature.

Import and export use ContentResolver on background coroutines and system
OpenDocument/CreateDocument contracts. No broad storage permissions, server,
account, sync, automatic startup import or hard-coded user-file paths are added.
The bundled starter is the original attached 40-verb dataset, not generated data.

## Practice, pause and results

Open **Практика аориста**. Fresh settings select typed recall for **аз**; choose
other persons/all six, exercise type, aspect, study group, optional dataset,
size and new/due/mixed queue. A shortage offers a smaller session without padding.
Multiple choice explicitly offers fewer options or a switch to typing when needed.

Each question displays separate visible/accessibility text:

```text
чета — читать
Время: минало свършено време
Лицо: ние
```

The person is not part of the input field label. The learner supplies an isolated
form. For example, **облякох се** is the isolated answer for облека се / аз;
**Аз се облякох** is a full sentence with a subject. The app never constructs a
sentence by concatenating a pronoun and an isolated answer.

Typed input supports IME Done as **Проверить**. Choice radio selection only selects;
**Проверить** submits. **Показать ответ** is a different action and records a reveal,
not independent recall. Feedback is an accessible polite live region and shows
raw submitted text, expected form, separately labelled approved alternatives,
the supplied note and **Дальше**. Wrong submissions are not spoken as examples.

Before feedback, no question note, conjugation table, hidden target text or target
accessibility description is composed. Options are visible in choice mode without
correctness labels. **All aorist question audio is withheld before feedback**:
even the dictionary form can coincide with an approved answer, so playing it could
cue the target. After feedback, explicit normal/slow playback uses the existing
Bulgarian TTS component for approved forms only. No autoplay. Missing Bulgarian
voice data does not block answering; help provides the existing system setup flow.
Question/route changes stop playback.

Back flushes the current draft before leaving; a failed save keeps the screen
open with an error. Return to practice to resume the saved question/choice order,
draft, feedback or position. Next is explicit; it never creates another attempt.
Finish early requires confirmation and retains completed events. Results show
answered/correct/mismatched/revealed/skipped counts for this exercise and a
mistake/reveal review. Skipped items get no attempt or new schedule.

The existing Room engine guards rapid taps, retries and stale callbacks, freezes
question content and keeps progress independent by verb/person/exercise. Reveal
resets that target/type streak and schedules a failure retry after 24 hours.
Multiple-choice success never advances typed recall. See AORIST_PRACTICE.md for
normalization, exact accepted arrays, queue rules, durable drafts and backup policy.

**Справка об аористе** is reachable from the collection and practice. Opening it
from practice flushes the draft, and Back returns to that session. It explains
aorist versus aspect, coincident persons/tenses, се/си positioning, isolated forms,
conjugation groups rather than CEFR levels, exact matching and offline use.
There are no sentence-gap exercises in this release.

## Verification scope

Native Compose tests use Robolectric, the real local Room repository, the original
starter asset and a fake TTS adapter. They exercise 320 dp layouts, enlarged text,
separate time/person labels, hidden content/audio in unmerged semantics, IME submit,
reveal versus Check, explicit choice submission, alternatives and missing-voice
usability. Flow tests cover starter confirmation, draft Back/resume, typed and
choice results, mistake review, help return, search/table/edit/archive/restore,
and mocked system picker results through actual OpenDocument/CreateDocument
contracts with ContentResolver streams, including malformed JSON.

These are JVM tests, not instrumentation or physical-device verification. Exact
commands/results and any corrected test failures are recorded in BUILD_REPORT.md.
No emulator or physical device is available; adb also cannot create its default
read-only `/home/agent/.android` directory in this environment.

Before personal use, test on the phone:

- Import through the phone's actual document provider, confirm all 40 entries and
  reimport; export and import a copy. Check the archive and six editable slots.
- Enable airplane mode after import. Complete both exercise types, pause/resume,
  reveal, early finish and review results with Bulgarian keyboard and IME actions.
- Rotate with a draft/selected option/feedback; force-stop and restart after a
  committed draft/result. Check question/choice order and counters do not change.
- Use the largest font/display settings and TalkBack; check scrolling above the
  open keyboard, focus order, announced feedback, radio labels and system Back.
- Install an offline Bulgarian voice, then test normal/slow playback in airplane
  mode. Repeat with voice data missing. Mock tests do not verify voice quality.
- Create a full backup before updating; update with the same persistent signing
  key and check all modes/history, then test full restore on a safe copy of data.

See ANDROID_BUILDS.md and RELEASE_SIGNING.md for artifact download/extraction,
installation and the existing persistent-key release workflow. A debug APK is not
proof of release signing. Cloud debug certificates can differ; on a signature
mismatch preserve the existing app/data and use the original persistent key.
Do not uninstall an existing installation to bypass a signing mismatch.
