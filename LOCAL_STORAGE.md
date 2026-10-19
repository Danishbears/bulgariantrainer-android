> Current schema is **v4**. Additive migration 3→4 adds independent typing
> sessions/questions/attempts and `(word,direction)` progress without changing
> existing content or flashcard tables. History-linked typing words also reject
> hard deletion; archiving remains available. See [TYPING.md](TYPING.md).

# Room vocabulary storage

Current database version is **3**. Flashcard sessions/cards/attempts and isolated
recognition counters are added through `MIGRATION_2_3`; see
[FLASHCARDS.md](FLASHCARDS.md). The prior import/export milestone used version 2. The import/export increment adds separate
sentence/alternative/receipt tables through an additive `MIGRATION_1_2`; the
original vocabulary schema and data remain intact. See [IMPORT_EXPORT.md](IMPORT_EXPORT.md)
for new tables, source formats, migration and operational retry rules. The
sections below describe the preserved vocabulary API and its original v1 contract.

## Scope and preserved baseline

This increment extends Android `66854f0e5c3a9606f81cfd6c11ebaea014282516` on
`codex/android-foundation-parity`. ApplicationId/namespace remain
`com.danishbears.bulgariantrainer`. Kotlin, Compose/Material 3, Room 2.7.2, Gradle
8.11.1, AGP 8.9.2, Kotlin 2.1.20 and SDK 35 are retained. The existing transactional
repository and version 1 schema are preserved, with dashboard, paged dictionary
and editor screens added. No existing user data needs a schema conversion.

Web source inspected at `de0a3e883d51561123ca54b7d5dd2e53757baf0a`:
`service/WordService.java`, `service/VocabularyText.java`,
`controller/WordController.java`, `dto/WordRequest.java`,
`dto/TranslationRequest.java`, `service/StatisticsService.java`, the vocabulary
entities/repositories/migrations, `static/js/vocabulary.js`, and
`VocabularyIntegrationTests.java` (Java paths use the package prefix documented
in WEB_PARITY). README was not used as the behavior contract. Full learning rules,
source mappings, discrepancies and suspected defects remain in WEB_PARITY.

## On-device workflow

- Главная shows real total/active/archived word counts and total stored translations
  (including archived translations). An empty database shows zero counts and an
  invitation to add a word. No attempt, mastery, due or accuracy values are shown.
- Словарь searches normalized Bulgarian and every Russian alternative, with a
  300 ms debounce and active/archive filters. Empty, loading and retryable load
  failures are explicit. Queries over 255 UTF-16 units show a field error.
- Добавить слово / Изменить opens a validated editor: Bulgarian, 1–50 separate
  Russian fields, one primary radio selection, optional part of speech/gender/notes.
  Commas/slashes are literal within each field. Duplicate Bulgarian errors mention
  both dictionary and archive. Save is guarded against repeat taps; background
  write failures retain the draft for retry. Back asks before discarding edits.
- В архив / Восстановить changes archive status transactionally. Records, translation
  IDs and creation timestamps remain. Per-row busy state prevents repeated taps.
  No hard-delete control is exposed by the UI.
- Search/filter and editor draft strings, translation keys and primary selection use
  SavedStateHandle. Rotation retains ViewModels; system-saved state can restore
  drafts after process recreation. Force-stop/clearing task data is not a durable
  draft backup, and database backup/export is not implemented.

## Files and usage

All Kotlin paths below are under
`app/src/main/java/com/danishbears/bulgariantrainer/`.

| File | Responsibility |
| --- | --- |
| `BulgarianTrainerApplication.kt` | App-scoped lazy database/repository, using application context |
| `data/TrainerDatabase.kt` | Room database v1, private file `bulgariantrainer.db`, no seeding or destructive fallback |
| `data/VocabularyEntities.kt` | Word and owned translation entities, normalized keys, indices, foreign key and relation |
| `data/VocabularyDao.kt` | Bound SQL queries, consistent relation snapshots, reactive Flow queries |
| `data/Vocabulary.kt` | Plain input/result/filter models; duplicate and missing-word errors |
| `data/VocabularyText.kt` | NFC/Unicode whitespace/locale-independent keys and literal search escaping |
| `data/VocabularyRepository.kt` | Atomic writes, injected Clock, count Flow and bounded Pager |
| `data/VocabularyValidator.kt` | Shared typed field validation for UI and persistence |
| `ui/VocabularyViewModel.kt` | Lifecycle state, debounced queries, paging, counts, archive actions |
| `ui/WordEditorViewModel.kt` | Load/save states, saved draft, primary/removal rules, field errors |
| `ui/TrainerApp.kt` | Navigation and ViewModel ownership |
| `ui/DashboardScreen.kt`, `ui/VocabularyScreen.kt`, `ui/WordEditorScreen.kt` | Lifecycle-aware Compose screens, lazy lists and editor |

UI code can obtain the single app repository from
`(application as BulgarianTrainerApplication).vocabulary`. Call suspend operations
from a coroutine and collect `observe(filter)` with a lifecycle-aware consumer.
The app never inserts sample entries or imports files automatically.

```kotlin
val saved = repository.create(
    WordInput("къща", listOf(TranslationInput("дом"), TranslationInput("жилище"))),
)
val matches = repository.list(VocabularyFilter(query = "ЖИЛИЩЕ"))
val archived = repository.setArchived(saved.id, true)
```

`create`, `update`, `get`, `list`, `setArchived` and `delete` are suspend functions.
`observe` returns `Flow<List<VocabularyWord>>`. Pagination is zero-based, default
size 20, range 1–100, ascending word ID. Search query length is at most 255,
part-of-speech filter length at most 100, matching `controller/WordController.java`.
`observeCounts()` emits one aggregate result without joining words to translation
rows, so words are never multiplied by alternative answers. `pages(filter)` uses
Room PagingSource, 40-word pages, initial load 40, prefetch distance 8, no
placeholders and a 200-word cache bound. `LazyColumn` uses stable database IDs;
list cards show at most three translation previews. Only visible rows compose.
Search uses escaped substring LIKE; this is a scan within the archive filter,
not an FTS index. Large-device dictionary performance remains a device benchmark,
not a claimed measured result. Pages use ascending IDs and invalidate on changes
to either words or translations.

## Rules implemented from the inspected source

- Display text uses NFC, whitespace collapse and trim. Keys additionally use
  `Locale.ROOT` lowercase. The explicit Unicode White_Space set avoids depending
  on differences between JVM and Android regex flags; edge stripping uses
  `Character.isWhitespace`, matching the reference's `String.strip` behavior.
  Cyrillic/Latin lookalikes, ё/е, й/и, ѝ/и, Bulgarian spelling, accents and
  punctuation remain distinct. BOM stripping belongs to a future import parser,
  not to ordinary vocabulary normalization.
- Raw text lengths are checked before cleaning: Bulgarian/Russian fields ≤255
  UTF-16 code units, part of speech/gender ≤100, notes ≤4000. Required cleaned
  text cannot be empty. Optional cleaned blanks become null.
- A word has 1–50 normalized-distinct Russian translations. At most one can be
  explicitly primary; otherwise the first is selected. Results order primary
  first, then translation ID. `ехать, идти` stays one stored translation, not
  two implicit accepted answers. All stored values are available for later
  training implementations; no answer-evaluation mode is added now.
- Normalized Bulgarian is unique across active and archived words. Archive and
  restore preserve translations, IDs and creation time. Edits preserve existing
  translation IDs by normalized key, remove explicitly omitted translations,
  and do not reset archive state. Duplicate/invalid edits leave prior data intact.
- Search matches normalized Bulgarian or any Russian translation. `%`, `_` and
  `!` are literal input, escaped for SQL LIKE. Part-of-speech filtering stores a
  separate normalized key because SQLite's built-in lowercase is ASCII-only;
  filtering Cyrillic is therefore independent of SQLite case folding.
- Every multi-row write uses `RoomDatabase.withTransaction`; default INSERT
  conflict behavior aborts rather than replaces existing rows. Unique database
  indices protect word and per-word translation keys, including concurrent callers.
  Foreign keys reject orphan translations and cascade only a deleted word's own
  translations. Length limits and exactly-one-primary are enforced by the
  repository, not falsely presented as SQLite CHECK/partial-index constraints.
- Sentence-word links and typing/sentence-training records remain absent. Durable
  flashcard queues/attempts and recognition progress now exist; sentence content
  and alternatives remain independent.
  Deleting unused vocabulary is supported; flashcard-linked vocabulary now has
  repository and restrictive-FK history protection. Future reference/history tables
  must retain restrictive foreign keys. Do not infer full web delete parity
  from the current two-table database.
- Creation/update timestamps use one injected `Clock`, stored as UTC epoch
  milliseconds and returned as `Instant`. This is millisecond precision rather
  than H2's microseconds; no mixed clock sources or local-midnight scheduling
  are introduced. Archive requests update the modification timestamp as on the web.

## Schema and migration policy

Room 2.7.2 is generated with KSP `2.1.20-1.0.32`, matching Kotlin 2.1.20.
Database version 1 is unchanged from the preceding storage increment. No new
entities/columns are needed for this UI, so no migration is invented or version
bumped. Existing populated databases reopen using the same identity hash
`b3c43139a01c2d8d76ca272ad1d8982d`. Room exports its schema to
`app/schemas/com.danishbears.bulgariantrainer.data.TrainerDatabase/1.json`.
This JSON is a reviewed source contract, not a generated APK/build cache.

Schema includes `words` (unique normalized text, archive/part index) and
`translations` (word FK with CASCADE, unique word/key, translation search index).
All writes pass through the repository; no DAO is exposed from the app container.
No `allowMainThreadQueries`, `fallbackToDestructiveMigration`, file copies from H2,
automatic import, user-account requirement or INTERNET permission is added.

Future schema changes must increment the version, check in the exported schema,
and supply an explicit Room migration with preservation tests. Unsupported schema
versions fail to open; the database is retained. Backup/export and training tables
remain separate stages. The existing `allowBackup=false` manifest policy remains.

## Tests and representative input/output

Tests live under `app/src/test/java/com/danishbears/bulgariantrainer/`.
`VocabularyTextTest` covers normalization golden cases; `VocabularyValidatorTest`
checks field-specific errors and raw limits. `VocabularyRepositoryTest` uses real
Robolectric/native SQLite, disposable file databases, constraints, transactions,
count invalidation and actual Room Paging loads. `ui/WordEditorViewModelTest`
uses a real Room database and coroutine Main dispatcher to test saves, duplicate
errors, archive preservation, missing records, counts and saved-draft restoration.
Fixture vocabulary exists only in test databases.

| Representative case | Expected result |
| --- | --- |
| `МОИ\u0306` + NBSP + `ДОМ` versus `мой дом` | One normalized Bulgarian key; second create rejected, even if first archived |
| `къща` vs `каща`, `дом` vs Latin-o `дoм`, `ѝ` vs `и` | Distinct keys |
| Two translations with no explicit primary | First primary; both retained |
| `Дом` and ` дом ` in one request | Rejected; no word/translation committed |
| Primary `отправляться`, alternative `ехать, идти` | Primary ordered first; comma text remains one alternative |
| Query `%_!` | Only literal matching text, not wildcard expansion |
| Cyrillic part `СЪЩЕСТВИТЕЛНО` queried as `съществително` | Match despite SQLite ASCII-only lower() |
| Edit retaining `жилище` as `ЖИЛИЩЕ` and adding `здание` | Existing translation ID retained, omitted translation removed atomically |
| SQLite trigger aborts insertion after word/translation edits | Whole transaction rolled back; prior content remains |
| Two simultaneous creates of normalized-equivalent words | One complete saved word, one duplicate error |
| Translation-only update while collecting Russian search Flow | New matching result delivered through Room invalidation |
| Close/reopen a file database | Exact committed Cyrillic data, archive state, IDs and timestamps retained |
| Unsupported higher `user_version` | Open fails without deleting stored vocabulary |
| Empty DB, then two words with three translations | Counts `(total=0, active=0, archived=0, translations=0)` then `(2,2,0,3)` |
| Archive one word; edit only its translations | Word totals stable; archive and translation counts update independently |
| 125-word fixture | Initial bounded page, then scrolling loads later pages in ascending ID order |
| Query finds a non-primary Russian translation | Same word returned once; changing/archive data invalidates Room pages |
| Save tapped twice | Exactly one word and its translations committed |
| Blank form or normalized duplicate translation | Field-specific errors; no partial DB write |
| Restore editor from copied SavedStateHandle | Same text, row keys, metadata, dirty state and selected primary |
| Edit an archived word and remove primary translation | Word remains archived; surviving translation ID preserved and becomes primary |

The repository tests exercise real SQLite constraints/transactions under
Robolectric, not a fake/in-memory repository. They do not demonstrate installation,
device rendering, process-kill recovery during an in-flight write, or future
training persistence. Executed outcomes and CI links are in BUILD_REPORT.md.

The preceding storage baseline passed 20 tests. This increment passed all
**36 tests** (19 repository, 5 normalizer, 4 validator, 8 ViewModel), lint,
`assembleDebug` and the unchanged exported-schema check on
[run 39247504111](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39247504111)
at code commit `95d4b6ae9650bfd709df3ce34e5ee56816c83d43`.
Exact commands, artifacts and device-only checks are recorded in BUILD_REPORT.md.

Local Gradle bootstrap remains blocked by shell networking.

## Design for later learning data

Keep vocabulary separate from learning records. Add these only alongside their
requested features, through explicit additive versioned Room migrations:

| Planned table family | Keys and preservation rules |
| --- | --- |
| Sessions and queued questions | Session UUID/request identity and settings; ordered queue with unique `(session_id, position)`; prompt/accepted-answer snapshots independent of later edits |
| Attempts | Immutable session/question/word references, raw answer, result/source, acknowledgement and timestamps; unique operation/question identity for idempotent retries |
| Word progress | Separate record from `words`; unique `(word_id, mode, direction)` with a non-null shared-direction sentinel for flashcards, so flashcards and each typing direction cannot overwrite each other |
| Review schedules | Next due instant, interval/streak, last finalized attempt; updated atomically with that attempt and its separate progress record |
| Sentences and their progress | Own identity, answer snapshots and independent schedule; vocabulary usage links never award word progress |

Reference foreign keys to `words` must use RESTRICT/NO ACTION rather than CASCADE;
archive remains safe and any repository hard-delete path must refuse history use.
Only owned translation rows cascade today. Attempts must retain answer snapshots
when a translation is edited/removed. Persist schedule instants in UTC epoch
milliseconds; define any day-boundary timezone explicitly. Add unique indices for
retry/progress keys and due-date indices before shipping selection queries.
Future migration tests must open every supported exported starting version, insert
representative vocabulary/history, migrate to latest and assert data, IDs, foreign
keys and schedules survive. There is no routine destructive fallback. This
milestone has no schema change, so a fabricated `1 -> 2` migration is unnecessary.

## Next stages

Controlled word/sentence imports and content exports are implemented. Requested
training session/attempt/progress/schedule features remain future migrations. Resolve the suspected web selector/link
snapshot defects in WEB_PARITY before porting those training behaviors. No
additional training mode, backend, account system or release signing is added.
