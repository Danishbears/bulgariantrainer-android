# Native aspect-pair content management

Reference: read-only web `codex/aspect-pairs` at
`be6a91ff2ae06b8b0eae2461d8c7a8bf910ce57a`. See
ANDROID_ASPECT_PAIRS_PLAN.md for the source/test audit and missing original guide.
Typed aspect practice and independent direction progress/scheduling are now
implemented: see ASPECT_PRACTICE_ANDROID.md. Separate aggregate aspect statistics
remain a later increment;
existing vocabulary, flashcard, typing, sentence, scheduling, statistics and TTS
features retain their own tables and behavior.

## Open and manage

Главная → Видовые пары. A fresh installation/migration contains no aspect entries.
Create a pair or explicitly preview a JSON file/the supplied 100-pair material.
The list has bounded 25-row pages and combined namespace, text, stage, exact topic,
relation and archive filters. Search includes both forms/meanings, every approved
alternative, topic and external ID; %, _ and ! are literal. A blank namespace
filter includes all local namespaces. No server, account, network or sync exists.

Open a row to view all learning fields, edit, archive or restore it. Manual
creation assigns a UUID external identifier in the saved editor draft; ordinary
ViewModel recreation retains it. Existing external ID/namespace cannot change.
Both accepted-answer boxes use one answer per line and must include the primary
form. Stage is1–3; relation is core/resultative/bounded, independently. Optional
examples require all four Bulgarian/Russian values. New manual entries default to
added provenance; presentation provenance refers to a source definition previously
imported into that namespace. Editor errors retain the draft. Archive never deletes
a pair or learning history.

Internal Room IDs and external content IDs are separate. Edits preserve internal
pair ID, creation time and archive state. Row versions and dataset versions guard
stale writes/previews; a no-op content write does not replace child answers.
Other modes' attempts, sessions, settings and progress are never rewritten.

## JSON import/export

Dedicated format `bulgariantrainer.aspect-pairs`, integer `formatVersion=1`;
separate from vocabulary TXT/CSV, sentence CSV and full JSON backup. UTF-8 with
optional BOM, at most1MiB and1–1000 entries. Unknown fields, malformed UTF-8/JSON,
duplicate JSON keys/IDs, trailing documents, invalid types, missing required text,
bad examples/provenance, invalid stage/relation/answers and absent primary
membership are diagnosed before any write. Empty note/absent examples are allowed.

Select a document through Android's system picker, or explicitly select
**Предпросмотр набора из 100 пар**. Pick a stable namespace: the supplied material's
suggested namespace is `bulgarian-aspect-pairs-100`. Preview itself saves nothing.
Any invalid row blocks the whole document. New rows start selected; updates require
explicit selection after Было/Будет comparison. Unchanged rows can be selected.
Correct invalid source content and select it again; no partial invalid-file import.

Confirmation rechecks dataset identity/version and selected pair IDs/versions and
commits selected content plus a durable receipt in one Room transaction. Reimport
retains internal IDs and archive/progress, does not restore archive, and does not
delete omitted rows. A changed dataset/edit/archive/new identity after preview
makes confirmation stale and rolls back everything. Same confirmation UUID and
selection returns its original durable summary after reopening; changed selection
under that UUID conflicts. Uncommitted preview lasts30minutes, is only held in its
ViewModel, and must be reselected after process loss. No startup import occurs.

The dataset content revision is SHA-256 of the original confirmed bytes; neither
formatVersion, application version, optimistic row version nor createdOn is a
dataset content revision. Last-import root metadata and receipt metadata are kept.
Each pair also retains its referenced source definition, so a later import which
omits old pairs/sources cannot erase their provenance. Export merges the current
root source definitions with per-pair context and rejects conflicting definitions.

Export uses Android CreateDocument/ContentResolver and the same validated JSON
schema, one namespace per file, with an explicit include-archive choice. Namespace
is chosen again on import; it is not invented as an unknown root field. Exports
contain learning content and original known metadata only, without internal IDs,
archive flags, versions, sessions, attempts, progress or receipts. They are **not
full backups** or guaranteed byte-for-byte original-file copies. No CSV conversion,
language reversal or generated alternatives is performed. Empty/over1000-entry or
over1MiB exports are rejected clearly rather than producing an unimportable file;
split large datasets explicitly via source documents. A failed provider write can
leave a partial destination; create a new file after the error.

Material at `learning-materials/aspect-pairs/bulgarian_aspect_pairs_100.json` and
its explicit-preview asset copy are byte-identical to reference Git blob
`c29cd6d9aac696b37593a4b8d2f0f4f1443b80f4`:100 pairs,212 accepted answers,
16 paired examples. No independent linguistic proofreading is claimed.

## Schema and full backup compatibility

ApplicationId remains `com.danishbears.bulgariantrainer`, DB name remains
`bulgariantrainer.db`. Room v8 adds **four content tables only**:
aspect_datasets, aspect_pairs, aspect_answers, aspect_receipts. It does not
pre-create the plan's later practice/progress/settings tables. Their future
migration remains additive. Existing20 entity contracts/migrations/rows/sequence
high-water marks stay unchanged. No destructive fallback. Exported schema8 is
captured from Room/KSP rather than hand-inventing an identity hash.

Full backups now use **v2/schema8**,24 tables and16 AUTOINCREMENT sequences.
They include aspect content, source context, archive/version data and receipts.
The original v1/schema7 exact-table/checksum contract is authenticated first;
a legacy backup is expanded only in isolated staging with empty new tables/
sequences, then all old/new semantic/FK checks run before replacement confirmation.
Restoring an old full backup replaces local data and contains **zero aspect pairs**;
it does not merge or keep current aspects. The preview displays aspect counts.

Restore remains a consistent logical Room/WAL snapshot and transactional
replacement, including rollback and old-command generation invalidation. Old apps
reject v2 backups; save a pre-upgrade backup before updating. CSV/content JSON cannot
replace a full backup. App candidate versionCode3/0.3.0 uses the existing signing
workflow; keep the same persistent signing key for in-place personal updates.
Cloud debug keys may differ; never uninstall valuable data before a verified full
backup. Same-key APK update/data preservation still needs physical-device testing.

## Verification and limits

New tests cover the actual material, encoding/schema/normalization failures,
round-trip export/import, explicit updates/omissions, namespaces/search/pagination,
archive/identity, stale previews, durable receipt replay, source-metadata retention,
transaction rollback, native SQLite populated exported-v7 migration and old/new
full backups. ViewModel/ContentResolver tests cover selection without writes,
duplicate confirmation, export, stable editor IDs and lost previews. The whole
existing suite remains required. Actual commands/run outcomes belong in
BUILD_REPORT.md, not this description of intended coverage.

Manual device acceptance remains: SAF provider behavior/cancellation/write failure,
long content and Cyrillic keyboard, rotation/font scaling/320dp layouts/TalkBack,
actual process termination/editor recovery, airplane mode, old-v1 restore and
same-key higher-version app update preserving all old data. No new training/audio
mode is delivered or claimed in this storage increment.

## Executed verification of this increment

Candidate `9f2a320ca706c327cbebc3c30f5b8738524b609a`,
[successful Actions run41336164726](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/41336164726):
**276 JVM tests** (257 existing+19 new methods), zero failures/errors/skipped;
debug and unsigned-release assembly, both lints, APK checks and exported schemas1–8
passed. Fourteen existing SDK/dependency advisories remain per variant; no errors.
This is actual hosted execution, not local/device testing. BUILD_REPORT.md records
all commands, failed test-compilation corrections, artifact links and manual limits.
