# Native Compose aspect-pair interface

Current UI candidate:0.4.1/code5. Kotlin/Compose/Material3, existing applicationId
`com.danishbears.bulgariantrainer`; no WebView, JavaScript, server or account.
This increment preserves Room9, all schema1–9 migrations, full-backupv3 and the
existing typed-recall rules. It adds no learning mode or database migration.

## Navigation and screens

Главная → Видовые пары. The original five bottom destinations are unchanged;
the bottom bar is hidden inside the aspect section to give forms/questions room.
Each screen has a labelled Back button. Help is a separate native destination;
returning from help preserves the underlying editor/session. Practice → Back
saves its navigation entry; reopening practice restores the entry/draft or the
authoritative Room session. Back does not grade or abandon a session. Use the
explicit early-completion action to finish one. In-flight writes block repeated
actions; editor Back asks before discarding. Preview Back returns without writes.

Management provides bilingual search with IME Search, expandable filters for
namespace/exact topic/stage/relation/archive, reset, bounded25-row pages,
view/create/edit/archive/restore. Archiving the last item on a page moves to a
valid preceding page. Russian relation/provenance labels replace raw JSON.
The create/edit form offers separate primary forms/meanings, multiline accepted
arrays (one per line), stages1–3, relation, note, optional paired examples and
advanced identity/source fields. Validation marks fields; IDs and draft text use
SavedStateHandle. Existing identity is read-only. Success notices are not errors.

Import/export retain Android SAF OpenDocument/CreateDocument and ContentResolver.
An explicit bundled-material preview is available; nothing auto-imports. Preview
shows selected/new/update/unchanged counts, errors, selectable records and
expandable before/after learning fields. A separate native confirmation dialog
precedes transactional saving. Updates still require deliberate selection and
stale previews are rejected. Content export remains JSON content only, separate
from CSV and full progress backup. Local files/providers work offline; a chosen
cloud document provider can require its own connection before/after practice.

Practice retains size/topic/namespace/stages/direction/queue configuration,
explicit shorter consent, stage1 fresh default, durable recovery, results and
mistake/reveal review. A recovered session is labelled. Body content scrolls with
IME insets; controls use wrapping text and vertical arrangements. Shared audio
buttons now wrap to another line at narrow widths/large font sizes. Top-bar titles
use one-line visual ellipsis while retaining full accessibility text.

## Prompt and hidden-answer boundary

Example: **купувам — покупать** followed by
**Напиши совершенный вид со значением «купить».**
The contextual source говоря requests **«поговорить»**, not an unspecified
perfective synonym. Reverse direction requests the imperfective meaning.

Before submission/reveal, question UI state contains only the source Bulgarian
form, source Russian meaning, requested aspect/meaning and question identity.
Target forms, accepted arrays, notes and examples are not composed, not merely
visually hidden. They therefore have no visible/accessibility/audio nodes.
The source form has an explicit audio button. No automatic speech occurs.
The single-user management section remains accessible deliberately.

After submission/reveal, the saved snapshot supplies both forms/translations,
approved alternatives, notes and structured Bulgarian/Russian paired examples.
The original raw submission is labelled; revealed cards explicitly say no
independent answer was submitted. Feedback uses a polite live region and
headings; checkboxes expose one combined label/checked-state node and a48dp
minimum row. The action is **Дальше**, including after the final answer.
Skipped results keep their Bulgarian targets hidden. Recall/normalization/reveal
scheduling are unchanged; see ASPECT_PRACTICE_ANDROID.md.

## Pronunciation and help

The existing Activity-owned BulgarianPronunciation, AndroidTtsAdapter,
PronunciationHost/Playback/Settings are reused. Source audio is allowed
immediately in both directions; target/alternative audio is derived only from
feedback's approved answers. Learner input is never the example spoken.
Management can explicitly play approved forms/alternatives. Normal/slow playback
replaces the previous utterance. Question/route boundaries stop speech, including
when an old lazy row has left the viewport; the existing host releases resources.

This is an explicitly requested **native extension**: the pinned web aspect
browser has no aspect TTS. Existing voice discovery still requires an installed
offline Bulgarian voice; it never falls back to another language. Missing voices
show setup guidance and disable only audio, never answering/reveal/Next.

Как заниматься explains aspect versus tense, Bulgarian dictionary citation forms
(first-person singular present), meaningful се/си and и/й/ѝ, contextual meanings,
study order1–3 (not CEFR), reveal consequences and offline/audio/backup limits.
Help deliberately contains no Bulgarian source/target pair or revealing example
that could disclose a live question's answer.

## Reference and source map

Read-only web commit: `be6a91ff2ae06b8b0eae2461d8c7a8bf910ce57a`.
Actual files inspected for this UI increment:
`src/main/resources/static/aspect-pairs.html` and
`static/js/aspect-{practice,management,import}.js` (relative to resources).
Web AGENTS.md was read; no Android/ancestor AGENTS.md is present.
The missing original `aspect_pairs_app_guide.md` remains unavailable as documented
in ANDROID_ASPECT_PAIRS_PLAN.md; no claim of inspecting it is made.

| Native surface/behavior | Implementation |
| --- | --- |
| Section routes / bottom bar / retained navigation entry | ui/TrainerApp.kt |
| Management, filters, forms, preview and export | ui/AspectScreen.kt, ui/AspectViewModel.kt; existing aspect repositories/documents |
| Structured learning content, headings, labelled checkboxes/top bars | ui/AspectComponents.kt |
| Prompt, conditional feedback, IME, result/mistake list | ui/AspectPracticeScreen.kt |
| Saved draft, recovery/action guards | ui/AspectPracticeViewModel.kt; existing Room practice repository |
| Approved-only audio / wrapping controls | pronunciation/PronunciationPolicy.kt, ui/PronunciationControls.kt |
| Native help | ui/AspectHelpScreen.kt; res/values/aspect_ui_strings.xml |

## Automated coverage and physical acceptance

The test-only Compose junit/manifest dependencies follow the existing Compose
BOM; no runtime library version was upgraded. Real Compose tests run on
Robolectric/API28 with native graphics and isolated data. They examine merged
and unmerged semantics, source/target/example visibility, both directions,
contextual instruction, reveal alternatives, explicit/fake audio, no-voice
answering, IME Done, busy controls,320dp/2x-font wrapping/scrolling, labelled
checkboxes, real-repository search/archive/restore, native import confirmation,
multiple-answer editing/discard, help and actual NavHost/System-Back transitions.
Additional ViewModel tests reconstruct SavedStateHandle to verify unsent drafts,
selected records and validation; fake-TTS integration verifies that listening
neither grades nor schedules and that only approved alternatives are spoken.
**Coverage descriptions are not pass claims:** BUILD_REPORT.md records which
commands and tests actually executed, failed/corrected runs and final results.

No physical device/emulator is available in this workspace. Before personal use:

1. Back up real data, install the same-key higher-version APK, import a local JSON
   through the system picker and confirm; check export using an on-device provider.
2. Enable airplane mode, relaunch, search/edit/archive/restore, configure every
   direction and explicit stages2/3; verify small-pool consent and saved results.
3. Use a Cyrillic keyboard/composition, Done, repeated taps and keyboard Back;
   scroll with IME open. Rotate during an unfinished input/editor draft and after
   feedback. Check320dp portrait/landscape, maximum font/display scale, light/dark
   theme and system gesture/navigation insets.
4. With TalkBack enabled, confirm source and requested meaning are clear, checkbox
   labels/states are announced once, feedback is announced, and hidden answers,
   alternatives, notes/examples/audio labels are absent before submit/reveal.
5. With an installed local Bulgarian voice, in airplane mode press normal/slow,
   replace playback repeatedly and change question/route. Then remove/disable
   Bulgarian voice data and verify text practice still works and setup is clear.
6. Force-stop before/after submit, after reveal and at final feedback; reopen and
   confirm Room order/status and explicit Next. Unsubmitted drafts use platform
   saved state and are not guaranteed across force-stop or loss of that state.
7. Restore a confirmed full backup and verify old/aspect history. Actual same-key
   update/data preservation, Android lifecycle/IME/TalkBack/voice/offline behavior
   are manual acceptance, not established by mocked/Robolectric tests.
