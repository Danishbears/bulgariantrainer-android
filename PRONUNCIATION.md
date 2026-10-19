# Bulgarian pronunciation

Android TextToSpeech is optional. Training, grading, session recovery, schedules
and statistics remain usable with no engine, no Bulgarian data or audio errors.
The application does not download voices, request INTERNET/storage permissions,
use cloud speech or fall back to Russian/English.

## Source parity

Read-only web reference: `master` at
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`.

| Actual web source | Android implementation |
| --- | --- |
| `src/main/resources/static/js/pronunciation.js`: flashcardBulgarianText, typingBulgarianText, sentenceBulgarianTexts | `pronunciation/PronunciationPolicy.kt` |
| Same file: BulgarianPronunciation discovery, speak/stop/destroy, callback identity | `BulgarianPronunciation.kt`, `TtsAdapter.kt`, `AndroidTtsAdapter.kt` |
| `static/js/{vocabulary,flashcards,typing,sentences}.js`: control ownership and question/page transitions | `ui/PronunciationControls.kt` and the corresponding Android screens |
| `src/test/js/pronunciation.test.mjs`: nine behavioral cases | `PronunciationTest`, `PronunciationPolicyTest`, `PronunciationLearningTest` |

JavaScript paths above are relative to `src/main/resources/` where abbreviated.

Visibility follows the actual helper functions:

- Dictionary Bulgarian: immediately.
- Flashcard BG→RU source: immediately. RU→BG approved Bulgarian: only after reveal.
- Typing BG→RU source: immediately, including feedback; Russian feedback is not
  spoken. RU→BG: first accepted Bulgarian answer only after submission.
- Sentences: primary and every approved Bulgarian alternative only after
  submission, including a nonmatch awaiting self-assessment.
- No raw typed answer is passed to speech as a reference. No hidden target is
  registered with the speech coordinator. Audio does not reveal/rate/submit.
- Audio controls are on current questions, rather than unsubmitted/skipped
  summary items. Restored feedback/reveal state uses the same visibility rules.

Examples: hidden RU→BG `кошка → котка` has no playback target until reveal.
Typing submitted `кушта`, accepted `къща`: playback uses `къща`. Sentence submitted
`Аз тука`, accepted `Тук съм.` / `Аз съм тук.`: each accepted version can be played
while the single attempt remains pending. Playing it adds no assessment or review.

## Engine and lifecycle

`PronunciationHost` owns one coordinator per Activity composition, using application
context for Android binding. TextToSpeech initializes asynchronously; controls show
initialization state and never queue early taps. Initialization failure or a
10-second callback timeout is explicit and retryable; timed-out engines are shut
down and late callbacks ignored. Training initialization does not wait for TTS.

Discovery checks `isLanguageAvailable(bg-BG)` **and** `getVoices()`. Only Bulgarian
locales, `isNetworkConnectionRequired == false` and voices without the
`KEY_FEATURE_NOT_INSTALLED` flag qualify. Candidates sort by engine-reported quality,
latency and name. Rejected candidates can be replaced only by another local
Bulgarian candidate. `setVoice` success and the actual selected voice are checked.
Availability/selection is rechecked before playback. `setLanguage` is never used,
since engines may silently substitute another language.

Missing data, unsupported Bulgarian, network-only voices, initialization/playback
failure and readiness have Russian UI messages. Expand **Произношение** on a
training/dictionary screen for system settings, voice-data installation and retry.
Returning from setup retries unavailable states; nothing autoplays. If an OEM
omits the TTS settings/installer activity, general Android settings are offered;
if that also fails, a message gives the manual settings path. Installation is an
explicit external system flow and may need Internet.

Normal rate is `1.0`; slow rate is `0.5`, as in the web implementation. Each tap
stops the old utterance and uses `QUEUE_FLUSH`. Cancellation failure prevents a
new utterance. Utterance IDs/generations ignore stale start/end/error callbacks.
Stop, question/session/status/route change, control disposal (including paged rows),
Activity backgrounding and disposal cancel speech; disposal shuts down the engine.
Changing a control's text stops its old playback. Disposing an unrelated old
control cannot stop the current owner's utterance. No speech is restored or
started automatically after process death.

Android intentionally requires a local voice, whereas the browser can use remote
voices. Android automatically chooses an eligible local Bulgarian voice rather
than persisting the browser's voice identity. Its engine initialization timeout
is 10 seconds; the browser's voice-list discovery waits 1800 ms. These platform
differences do not change learning visibility or reference text.

A voice's flags are declarations by its engine. Selection and mocked tests cannot
prove that the engine works offline, honors rate, pronounces Bulgarian well or
never contacts a service. No physical voice availability/quality verification is
claimed. The app's lack of INTERNET permission does not restrict another app's
TTS service; validate the chosen engine physically in airplane mode.

## Physical-device acceptance checklist

1. Install the debug APK from the successful Actions artifact (extract
   `app-debug.apk` first; see ANDROID_BUILDS.md). Keep existing study data when
   upgrading with a compatible signing key. Different cloud debug keys can prevent
   an in-place update; do not uninstall a valued dictionary just to test.
2. While connected, expand **Произношение → Открыть настройки речи**. Select an
   engine offering Bulgarian / Български (`bg` or `bg-BG`) and download its local
   Bulgarian voice data. Menu names vary by manufacturer; some engines provide
   no Bulgarian offline voice. Return and press **Проверить голос снова**.
3. Enable airplane mode and explicitly turn **Wi-Fi and mobile data off** (Wi-Fi
   can remain enabled in airplane mode). Force-stop/reopen the trainer to avoid
   relying on an already running utterance. Do not force-stop/uninstall voice data.
4. With your own content, play `котка`, `къща`, `добър ден` and a full Bulgarian
   sentence that has not been played while connected, to reduce reliance on cached
   synthesis. Check both rates audibly. Normal/slow should use Bulgarian; report
   the device/Android/engine/voice versions and perceived pronunciation separately.
5. Rapidly tap normal/slow; playback must replace rather than accumulate. Stop;
   move to another question; switch tabs; background/lock the device; rotate;
   navigate back. Old speech must stop and disposal must not break a later replay.
6. Check both flashcard directions before/after reveal, both typing directions
   before/after submission and a sentence before/after a deliberately nonmatching
   submission. No hidden Bulgarian controls/audio; hear the approved reference,
   never the deliberate mistake. Audio must not rate or advance the question.
7. Restore an unfinished session after force-stop. Visibility must follow its
   saved reveal/submission stage; reopening must not speak automatically. Review
   counters/statistics must reflect ratings/submissions, not playback taps.
8. To test missing data, temporarily select an engine without Bulgarian (retain
   personal data). Check the explicit message, setup/retry and the ability to
   finish training without sound. Re-enable data/voice and retry. If a network-only
   voice is available, confirm it is rejected rather than substituted.
9. Check loudspeaker/headphones/media volume, incoming audio interruptions, large
   font, TalkBack labels and OEM settings fallbacks. These hardware/OEM/audio-focus
   behaviors are not established by JVM tests. Restore your preferred engine and
   connectivity settings afterward.

Record pass/fail and the exact build SHA for these checks. No emulator or physical
check above has been executed in this cloud task. Actual JVM/build results and
artifact links are recorded in BUILD_REPORT.md.

## Executed cloud checks

[Successful run 39315189594](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39315189594)
on `36b7649657daa635099d644ed352f6de62e67574`: **223 JVM tests** (31 new TTS
cases), `assembleDebug`, `lintDebug`, pinned scheduler/statistics guard and Room
schemas 1–6 passed. The actual web JS file separately executed nine named tests
locally. These are behavioral/code checks, not physical speech validation.

[APK ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39315189594/artifacts/12218061473)
and [reports](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39315189594/artifacts/12218201214)
expire 2026-11-02; extract `app-debug.apk`. Detailed commands, durations and the
superseded initial run are in BUILD_REPORT.md.
