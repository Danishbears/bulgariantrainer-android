# Room vocabulary storage

## Scope and preserved baseline

This increment extends Android commit `7043dedcbeea62945cecf954c62cd9a5c21ae8d9`
on `codex/android-foundation-parity`. ApplicationId/namespace remain
`com.danishbears.bulgariantrainer`. Kotlin, Compose/Material 3, the welcome screen,
Gradle 8.11.1, AGP 8.9.2, Kotlin 2.1.20 and SDK 35 are retained.

Only vocabulary persistence is added. There is no vocabulary editor/import UI,
no training mode, no scheduler, and no statistics or pronunciation implementation.
The web reference is read-only at
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`; source mappings, broader learning rules,
and suspected web defects remain in WEB_PARITY.md.

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
| `data/VocabularyRepository.kt` | Validation and atomic create/edit/archive/delete operations; injected UTC Clock |

UI code can obtain the single app repository from
`(application as BulgarianTrainerApplication).vocabulary`. Call suspend operations
from a coroutine and collect `observe(filter)` with a lifecycle-aware consumer.
The current welcome screen does not create sample entries or expose an editor.

```kotlin
val saved = repository.create(
    WordInput("къща", listOf(TranslationInput("дом"), TranslationInput("жилище"))),
)
val matches = repository.list(VocabularyFilter(query = "ЖИЛИЩЕ"))
val archived = repository.setArchived(saved.id, true)
```

`create`, `update`, `get`, `list`, `setArchived` and `delete` are suspend functions.
`observe` returns `Flow<List<VocabularyWord>>`. Pagination is zero-based, size
1–100, ascending word ID. There is no total-count DTO or UI pagination yet.

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
- There are currently no sentence links, queues, history or progress tables.
  Deleting unused vocabulary is supported; **training-history deletion protection
  is not implemented yet**. Future reference/history tables must use restrictive
  foreign keys before introducing training. Do not infer full web delete parity
  from the current two-table database.
- Creation/update timestamps use one injected `Clock`, stored as UTC epoch
  milliseconds and returned as `Instant`. This is millisecond precision rather
  than H2's microseconds; no mixed clock sources or local-midnight scheduling
  are introduced. Archive requests update the modification timestamp as on the web.

## Schema and migration policy

Room 2.7.2 is generated with KSP `2.1.20-1.0.32`, matching Kotlin 2.1.20.
Database version 1 is the first Android database; the preceding app had no
database schema to migrate. Room exports its schema to
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

Tests live under `app/src/test/java/com/danishbears/bulgariantrainer/data/`.
`VocabularyTextTest` has five JVM golden-case tests. `VocabularyRepositoryTest`
has fifteen Robolectric/native-SQLite tests; each opens a unique disposable
database and deletes only that test file afterward.

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

The repository tests exercise real SQLite constraints/transactions under
Robolectric, not a fake/in-memory repository. They do not demonstrate installation,
device rendering, process-kill recovery during an in-flight write, or future
training persistence. Executed outcomes and CI links are in BUILD_REPORT.md.

## Next stages

Add a lifecycle-aware vocabulary editor/list UI and controlled file import;
introduce sentence/session/attempt/schedule schemas with explicit migrations
only as their features are requested. Resolve the suspected web selector/link
snapshot issues documented in WEB_PARITY before porting training behavior.
