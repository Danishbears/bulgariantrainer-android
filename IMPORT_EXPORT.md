> Manual sentences may now omit difficulty. The web CSV importer still requires
> 1–5: sentence export checks this before writing and asks users to set missing
> levels in the editor. No level is fabricated. Exports remain content-only and
> exclude every training session/progress record. See [SENTENCES.md](SENTENCES.md).

# Content import and export

## Source contract and scope

Read-only reference: `Danishbears/bulgariantrainer`, branch `master`, commit
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`. Actual inspected sources under
`src/main/java/com/example/bulgariantrainer/`:

- `service/VocabularyImportParser.java`: strict UTF-8, TXT pairs, CSV state
  machine, physical line ranges, warnings and limits.
- `service/VocabularyImportService.java`, `VocabularyImportWriter.java`,
  `WordService.java`: normalized duplicates, explicit append, transactions,
  target identity checks and retry semantics.
- `service/SentenceImportParser.java`, `SentenceImportService.java`,
  `SentenceImportWriter.java`, `SentenceService.java`, `dto/SentenceDtos.java`:
  exact seven-column CSV, JSON string arrays, validation and approval.
- `src/main/resources/static/js/import.js`, `sentence-import.js` and Java tests
  `service/VocabularyImportParserTests.java`, `ImportIntegrationTests.java`,
  `SentenceIntegrationTests.java`: preview, corrections and confirmation behavior.

Search of Java controllers/services and all static JS found **no vocabulary or
sentence content-export implementation**. JS `export` declarations belong to
pronunciation modules; they are not a content-export format. Android CSV exports
therefore target the actual web **import schemas**, not an invented export API.
The broader learning audit and known web defects remain in WEB_PARITY.md.

The import/export increment added no training mode. The later FLASHCARDS.md
increment adds recognition sessions separately. No generated translation, automatic language reversal, server,
account, broad storage permission or release signing is introduced.

## Use on Android

1. Open **Файлы**, choose **Словарь** or **Предложения**, then **Выбрать файл для
   предпросмотра**. Android's `OpenDocument` system picker grants access to that
   document only. Select a UTF-8 TXT/CSV for words or CSV for sentences.
2. Parsing and duplicate checks do not save content. The lazy preview retains
   every source record, its original text, record ID and physical line range.
   Header/empty records remain visible and cannot be selected. Malformed rows
   are not silently dropped. Initially only READY records are selected.
3. Open **Исправить запись** to edit fields. Each accepted translation/alternative
   has its own field; commas and slashes do not silently create alternatives.
   Invalid alternatives JSON stays visible and blocks confirmation until you
   explicitly replace that field and enter the intended variants.
4. Select/deselect records and choose an action. **Создать** skips normalized
   duplicates. **Добавить переводы** appends only missing Russian translations;
   existing primary translation, metadata, archive state, identities and learning
   data are retained. It can also append to a word created by an earlier selected
   row of the same confirmation. Its key must resolve to that word at commit time.
   An explicit bulk choice selects valid word rows and chooses append for duplicates;
   it still requires the final confirmation dialog. Inspect warnings before using it.
5. **Подтвердить импорт** shows the selection/append counts and approval details.
   Saving is one Room transaction. A bad selected record, changed append target,
   50-translation overflow or SQLite failure rolls back the entire operation.
   Unselected rows have no effect; a sentence duplicate is skipped, never overwritten.
   Summary counts include header and deselected records in skipped totals.
6. To export, choose the content type and whether to include archived entries,
   then **Экспортировать…**. Android's `CreateDocument("text/csv")` flow lets you
   name and place the document. Canceling either picker performs no read/write.
   Export never deletes or changes database content.

Changing the type controls the next import/export; an existing preview retains
its own filename/type. A new file replaces the preview only after successful
parsing. Corrections and selection live in the ViewModel across rotation; they
are intentionally not stored as a potentially 1 MiB SavedStateHandle bundle.
After process loss, select the file again and inspect duplicates. Pending picker
kind/export options are saved. The app never automatically replays an import.

## Vocabulary formats

Input is strict UTF-8, with or without one leading BOM. File extensions are
case-insensitive. Limits match the web: **1 MiB**, **2000 records** (CSV header
and empty records count). A terminal line ending adds no spurious empty record.
Invalid UTF-8 is rejected, not decoded using replacement characters or CP1251.

TXT supports the first `=`, a hyphen/en dash/em dash adjacent to whitespace,
or tabs when no such separator exists:

```text
горе-долу - более-менее
на колко години си = сколько тебе лет
отивам - ехать, идти
```

Internal hyphens and multi-word expressions remain intact. Multiple separators,
Latin/mixed alphabets, annotations, slashes and commas produce review warnings;
the parser does not translate, guess direction or split a comma/slash alternative.

CSV accepts exactly `bulgarian,russian` plus optional `partOfSpeech`; column
order can vary and names are normalized as in the web parser. Export always uses:

```csv
bulgarian,russian,partOfSpeech
отивам,"ехать, идти",глагол
отивам,отправляться,глагол
```

Each stored accepted Russian translation gets **one row**, primary first, then
translation ID; words are ordered by ID. Quoted commas, doubled quotes, CR/LF/CRLF
and multiline fields are parsed by a state machine, not by splitting commas.
Export doubles embedded quotes and uses CRLF with UTF-8 without BOM.

Repeated Bulgarian rows represent multiple translations in this interchange
format. To reconstruct them in Android, explicitly choose the combine/append
workflow. The web reader can preview the same file, but its CREATE action skips
later duplicates; to retain all alternatives there, create words first and then
explicitly append the remaining translation rows to the now-existing targets.
The web writer cannot append to an unknown same-file ID; Android's explicit
same-file append is a documented extension, not an assertion of existing web behavior.

Normalized Bulgarian remains unique across the archive. Russian keys must be
unique within a word. NFC, Unicode whitespace collapse/trim and Locale.ROOT
lowercase match the existing Android/web contract. Raw manual-correction limits
are Bulgarian/Russian 255, part of speech/gender 100, notes 4000, 1–50 translations.
Punctuation, spelling/diacritic differences and Latin/Cyrillic lookalikes remain
significant. Optional cleaned blanks become null.

## Sentence CSV and bundled source

The exact, case-sensitive header/order is:

```csv
id,russian,bulgarian,alternativeTranslations,grammaticalExplanation,difficulty,topic
1,Я здесь.,Тук съм.,"[""Аз съм тук.""]",Порядок слов.,1,Разговор
```

`alternativeTranslations` is a **JSON array of strings inside a CSV field**.
CSV decoding runs first; strict kotlinx.serialization JSON parsing runs next.
JSON is not split on commas and non-string elements are invalid. A valid empty
list is `[]`. Embedded JSON quotes/backslashes are encoded with JSON rules and
then escaped according to CSV rules.

- `id` is a required opaque source identifier, not a replacement database ID.
- Russian prompt and Bulgarian reference are required, each at most 4000 UTF-16
  units; up to 50 Bulgarian alternatives, each nonblank and at most 4000.
- Alternatives cannot duplicate one another or the primary reference after
  normalization. This is checked in preview **and** confirmation.
- Explanation is optional, at most 4000; topic optional, at most 255; import
  difficulty is required, integer 1–5, matching the web CSV importer.
- Explicit confirmation approves the imported sentence/alternatives. Russian
  normalized text is the duplicate key, including archived records. A duplicate
  never changes existing references, alternatives, metadata or progress.
- The stored sentence also retains source filename, source ID and first source
  line; alternatives have independent IDs/positions. Export preserves source ID,
  both texts, every alternative, explanation, difficulty and topic.

The actual reference file `data/bulgarian_sentences_100.csv` was inspected:
23,071 bytes, UTF-8 with BOM, 100 data rows, 20 alternatives, difficulties 1/2; its header is
exactly the seven columns above. The identical file is packaged at
`app/src/main/assets/bulgarian_sentences_100.csv`, SHA-256:
`403b2a0416491d1e02c3db16459bbb878806da8904b34c320e0f758610441d0a`.
**Предпросмотр 100 предложений из проекта** only opens a preview after a button
press. The Application/database has no seed callback or launch-time import.
Repeated explicit selection detects duplicates rather than reimporting silently.

The other available dataset, `data/BULGARIANFORME_original.txt`, was also read:
15,254 bytes, UTF-8 without BOM, 397 physical records including 10 blank records.
Its actual entries include `храна - продукты`, slash alternatives and grammatical
annotations such as `момиче - девочка (едно момиче средний род)`. These are preserved
as literal pairs with review warnings, not automatically corrected or split.
This personal vocabulary file is **not** bundled or automatically imported;
choose it through the document picker if you want to review it.

## Export limitations: this is not a progress backup

CSV transfers the content fields supported by the web schemas. It contains no
sessions, attempts, progress, review schedules or retry receipts. Vocabulary CSV
also cannot encode gender, notes, IDs, timestamps or archive status. Primary
translation is represented only by row ordering on a fresh create, not an explicit
CSV flag. Sentence CSV cannot encode archive/approval flags, timestamps or word
links; imported selected sentences are approved and active. Including the archive
exports its text, not its archived status. Export is therefore **not a complete
content/state backup or a safe prerequisite for uninstalling all local data**.

Exports stream the full selected collection in 100-word/sentence batches within
one consistent read transaction, without loading the entire dictionary. An export
can exceed the web import's 1 MiB/2000-record limits; split such a CSV using a
CSV-aware tool before reimport, preserving headers and quoted multiline records.
A failed provider write can leave an incomplete document; the UI tells you to
delete it and repeat export. Database contents stay unchanged.

## Android files and schema

Kotlin paths use `app/src/main/java/com/danishbears/bulgariantrainer/`:

| File | Responsibility |
| --- | --- |
| `transfer/CsvCodec.kt` | Strict source-preserving CSV state machine port and CSV writer |
| `transfer/ContentParser.kt`, `ImportModels.kt` | UTF-8/TXT/CSV/JSON parsing, diagnostics and typed editable rows |
| `transfer/ContentTransferRepository.kt` | Batched duplicate enrichment; transactional confirmation/append; snapshot exports |
| `transfer/DocumentGateway.kt` | Bounded document streams and provider metadata off the main thread |
| `data/SentenceEntities.kt`, `ContentDao.kt` | Sentence content/alternatives and committed import receipts |
| `data/ContentMigrations.kt` | Additive migration 1→2 preserving the original vocabulary tables |
| `ui/TransferViewModel.kt` | Async load/edit/confirm/export state and guarded retries |
| `ui/TransferScreen.kt`, `ImportRowEditor.kt`, `TransferLabels.kt` | System picker launchers, lazy preview, editing, selection and explicit confirmation |

The import/export migration 1→2 adds `sentences`, `sentence_alternatives`, `import_receipts`.
The compiler-exported v2 identity hash is `bcea5278b79bd932a6e247746e21b2dc`.
The v1 `words`/`translations` definitions, IDs and data remain unchanged. Migration
is registered on production open; no destructive fallback/main-thread queries.
Committed confirmation receipts store UUID, a length-prefixed SHA-256 request
fingerprint and result counts in the same transaction as content writes. Identical
retries return the same summary after reopening; changed retries are refused.
Receipts are operational import metadata, not learning attempts/progress.

## Deliberate safety differences from the web

- Web sentence preview can show READY for normalized duplicate alternatives that
  confirmation rejects. Android validates them consistently during both steps.
- Web sentence writer checks duplicates before validating other corrected fields.
  Android validates **all** selected changes first, so an invalid duplicate cannot
  conceal a malformed selection. No unrelated row can commit on an error.
- Unsupported sentence headers fail with an explicit file-level error, matching
  the web; content is not guessed. Vocabulary unsupported headers/rows remain
  individually visible for correction. HEADER/EMPTY rows cannot be selected in
  Android; a malformed selected row must be explicitly corrected first.
- Android also rejects disallowed control characters in sentence fields (except
  TAB/CR/LF), matching the vocabulary safety rule; the web sentence preview lacks
  this explicit check.
- Android uses one in-memory ViewModel preview rather than a server's five-entry,
  30-minute preview cache. Its committed receipt survives restart. No server is
  needed; process loss requires a fresh preview, not automatic confirmation.

## Executed checks and device acceptance

Parser, repository, migration and document/ViewModel tests are under
`app/src/test/java/.../transfer/` and `ui/TransferViewModelTest.kt`. They cover
strict encodings/limits, quoted/multiline/malformed CSV, JSON types, original
source ranges, no-write preview, archive duplicates, append identity/limits,
transaction rollback, retry/concurrency, frozen picker options, actual dataset,
export/import round-trips and a populated exported-v1 migration. See BUILD_REPORT
for complete commands and results. Code `5defa7518f4956441cf83a4511d99400c1ea4142`
passed **68 tests**, `assembleDebug`, `lintDebug` and both committed Room schema
checks on [run 39255088356](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39255088356).
This is an actual hosted execution, not a claim based on source inspection.

Still verify on Android: actual system picker/creation providers and cancellation,
USB/on-phone install, Cyrillic/IME input in dialogs, rotation and process loss
while a picker is open, denied/revoked URI grants, provider/disk failure, airplane
mode with local documents, TalkBack, large fonts, narrow screens and long previews.
Robolectric provider tests do not establish behavior of every DocumentsProvider.
