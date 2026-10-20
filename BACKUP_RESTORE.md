# Manual full backup and restore

Open **Главная → Полный бэкап и восстановление**. This is separate from vocabulary
and sentence CSV content export. No server, account, periodic job or automatic
desktop/mobile synchronization is involved. Android system document creation and
selection choose the destination/source; no broad storage permission or URI-to-path
conversion is used. A document provider can itself be local or cloud-backed at the
user's choice; the application performs only this explicit manual operation.

## Included data and settings

All 20 Room v7 application tables are included, with original IDs, references,
strings, nullable fields and numeric values:

| Content | Tables |
| --- | --- |
| Words and accepted translations | words, translations |
| Sentences, alternatives and original import diagnostics | sentences, sentence_alternatives |
| Import idempotency receipts | import_receipts |
| Flashcard queues/reveal/attempts and recognition counters | flashcard_sessions, flashcard_cards, flashcard_attempts, recognition_progress |
| Typing queues/directions/raw answers/acknowledgements and independent direction counters | typing_sessions, typing_questions, typing_attempts, typing_progress |
| Sentence queues/approved snapshots/raw answers/automatic result/manual assessment and counters | sentence_sessions, sentence_questions, sentence_attempts, sentence_progress |
| Independent review schedules, deadlines and inherited flags | word_review_schedules, sentence_review_schedules |
| Training defaults | trainer_preferences |

Session configuration, order, current position, revision/status, snapshots,
pending self-assessment and completion timestamps are retained. SQLite AUTOINCREMENT
high-water marks are included even when the highest row was deleted; new records
must not reuse earlier IDs. Import receipts prevent an old import from being
silently replayed after restore.

Room **v6→v7** adds only the initially empty `trainer_preferences` table. It stores
user-selected flashcard size/direction/scope, typing scope and sentence size/scope.
Training screens load these defaults on a fresh launch; explicit SavedStateHandle
drafts retain precedence during ordinary Activity recovery. Only valid size choices
are persisted. Old per-session configuration is untouched, and no preference row
or demo content is silently inserted on migration.

Unsubmitted drafts, navigation/search state and picker permissions are transient,
not learning records. Device TTS engine/voice data, system theme/time-zone, binaries,
credentials and Android's external settings are not app-owned backup data. TTS
availability must be checked again on the destination device. CSV files remain
content-only and do not contain this learning history.

## Version 1 format

One UTF-8 JSON file, suggested name `bulgarian-trainer-full-backup.json`. Export has
no BOM; restore also accepts a UTF-8 BOM. Numbers are exact signed 64-bit decimal
integers (not floating point or strings); booleans in relational rows use 0/1.
Timestamps in rows retain epoch milliseconds. JSON strings preserve Cyrillic,
quotes, newlines and the original submitted text.

The root has exactly these fields:

```json
{
  "format": "bulgarian-trainer-full-backup",
  "formatVersion": 1,
  "applicationId": "com.danishbears.bulgariantrainer",
  "schemaVersion": 7,
  "createdAt": "2026-10-20T12:30:00Z",
  "tables": { "...all 20 table names...": [ { "...all exact column names...": "values" } ] },
  "sequences": { "...all 13 AUTOINCREMENT table names...": 0 },
  "sha256": "...64 lowercase hexadecimal characters..."
}
```

The abbreviated table/column names above illustrate structure, not an importable
example. Actual column/type/nullability/PK/FK contracts are exported in
`app/schemas/com.danishbears.bulgariantrainer.data.TrainerDatabase/7.json`.
All tables are required, including empty arrays; every row must contain exactly its
known columns. Unknown tables/columns, duplicate object keys, invalid UTF-8, trailing
JSON, unpaired Unicode escapes, excessive nesting (including encoded answer JSON)
and unsupported versions are rejected. V1 currently accepts
only schema 7: later formats need an explicit implementation, not implicit coercion.

`sequences` contains words, translations, sentences, sentence_alternatives,
flashcard_sessions/cards/attempts, typing_sessions/questions/attempts and
sentence_sessions/questions/attempts, using their full table names. Missing SQLite
sequence entries export as zero. Each value must be at least that table's maximum ID.

Checksum: remove the root `sha256`; recursively sort object keys lexicographically;
preserve array order and string contents; serialize compact JSON using the v1
Kotlin JSON escaping (no Unicode normalization); hash its UTF-8 bytes with SHA-256.
Whitespace and property order do not matter. The checksum detects corruption; it
is not a signature or encryption. The file contains personal vocabulary/answers.
The app neither encrypts it nor uploads it to an application backend.

Limits: 32 MiB and 250,000 rows across all tables. Export/read fail explicitly
rather than truncating content. This version builds a bounded in-memory document
and staging database; very large dictionaries/history need device memory testing.

## Consistency, validation and replacement

`BackupRepository.snapshot` reads every table and sequence inside a single Room
transaction, including committed WAL contents. It never copies an open `.db` file,
never ignores `.db-wal`, and does not depend on a successful manual checkpoint.
The complete snapshot is formed before opening the output stream. A provider write
failure can leave an incomplete external file; the UI reports failure and local
learning data remains unchanged. Create a new backup after such an error.

Selecting a restore file performs bounded read/strict decoding/checksum validation,
then loads **all rows** into an isolated in-memory Room database. Exact column types,
NOT NULL, PK/unique/FK constraints, ID high-water marks, booleans/enums/ranges,
normalized content keys, approved-answer JSON and contiguous session queues are
checked. Sentence alternative positions remain zero-based as stored by actual
manual/import code; session-question positions are one-based. Every session passes the actual repository graph-to-UI conversion; SQLite
foreign_key_check/integrity_check also run. Invalid data cannot delete live rows.

Historical counts need not equal the number of surviving logs. Unknown legacy
`typing_progress.direction` strings are retained as raw historical facts. Restore
does not fabricate missing attempts, replay reviews, change saved grading, compute
new deadlines, reinterpret self-assessment or merge duplicated content. Existing
recognition/scheduling streak differences from the earlier conservative migration
remain intact.

After full validation, the screen shows creation time, content counts, sessions,
unfinished sessions, attempts, progress/schedules and whether defaults were saved.
The user first chooses restore and then explicitly confirms **replacement of all
current data** in a dialog. A preview-specific token rejects stale confirmations;
cancelled previews and process recreation cannot silently restore. The validated
document is retained, rather than rereading a provider file that could change.

Replacement deletes children before parents, inserts exact saved IDs in dependency
order and restores sequences **inside one live Room transaction**, with foreign
keys enabled. Late SQL/validation failures roll back old content, history, settings
and sequences together. A database generation check rejects mutations queued before
the replacement. Navigation is blocked during restore and after success until
**Открыть восстановленные данные** creates a fresh Activity task, clearing stale
ViewModels, pending commands, monotonic feedback guards and draft/session pointers.
A process death reopens the last complete committed state (old or restored),
never a half-replaced database.
There is no automatic merge or background restore. Stored deadlines stay exact;
current due/activity views still use current device UTC time, not the backup date.

## Verification and physical checks

Tests cover full/empty round-trips, four-mode progress and pending recovery,
Cyrillic/multiline raw strings, settings, high-water IDs, WAL consistency, malformed
UTF-8/JSON/checksums/versions/columns/types, duplicate keys/IDs, broken foreign keys,
queues and invalid progress, old incomplete history, late transactional rollback,
preview/confirmation/recreation, provider failures and exported-v6 migration.
BUILD_REPORT.md records actual executed results and downloadable build artifacts.

On a physical device: create several rated sessions and one pending sentence;
record real counts; save a full backup with the picker; change/add content and
ratings; select the backup and verify that preview alone changes nothing; cancel
once, then reselect and confirm replacement. Reopen and check restored IDs/order,
feedback/raw text/self-assessment, both typing directions, schedules/statistics and
training defaults. Repeat with malformed/truncated files and confirm current data
survives. Test local and chosen cloud document providers, cancellation, low storage,
rotation/process termination, large histories and TalkBack. Run in airplane mode
with a local provider; TTS still depends on separately installed voice data.
No device/emulator/provider-cloud acceptance is claimed from JVM tests.

## Executed build evidence

[Successful Actions run 39381477249](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39381477249)
tested `d47a6acdbbda3a8b3c10f8bda986577746b3f56c`: **257 JVM tests** (34 new backup
cases), `assembleDebug`, `lintDebug`, the pinned web guard and Room schemas 1–7
passed. [APK ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39381477249/artifacts/12245269760)
and [reports](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39381477249/artifacts/12246105186)
expire 2026-11-03; extract `app-debug.apk`. BUILD_REPORT.md records the two initial
failures/fixes, actual commands, final results and remaining device limitations.
