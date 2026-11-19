> **2026-11-19 practice-stage update:** Native typed/choice practice is implemented;
> see AORIST_PRACTICE.md. Current Room11/full-backupv5 adds durable sessions,
> snapshots/options/drafts/attempts, independent form/type progress/schedules and
> preferences (40tables/26sequences). Existing content JSONv1 is unchanged.
> The content-stage counts/version/checks below describe the prior milestone.

# Aorist content storage and transfer

Date: 2026-11-18.

Implemented on `codex/android-aorist-plan`, starting from
`61923c1c485cf446374b43fa478d66bb20c63bc5`. This stage adds learning content,
management and import/export; aorist practice/sessions/progress are a later stage.
Existing vocabulary, sentences, aspect pairs and their practice remain separate.
ApplicationId is `com.danishbears.bulgariantrainer`; candidate version 0.6.0/code7.
Runtime dependencies and the existing signing workflow are unchanged.

## Actual supplied dataset

The previously missing attachment was supplied and read directly in this task.
Original bytes are preserved in `learning-materials/aorist/bulgarian_aorist_40.json`
and identically in the explicit-preview app asset. SHA-256:

`af92873c06a36c81973206cc24808d01e1e9cc2cdacce66225f92f095c521e33`

It is 38,397 bytes, UTF-8, containing:

- `format`: `bulgariantrainer.aorist`; `formatVersion`: integer1;
- `datasetId`: `bg-aorist-core-40`; `datasetRevision`: integer1;
- `createdOn`: `2026-11-17`, `language`: `bg`, `translationLanguage`: `ru`;
- `tense`: `aorist`, `entryForm`: `first_person_singular_present_dictionary_form`;
- five root `notes` and forty `entries` with distinct IDs `bgaor-001`…`bgaor-040`.

Each entry has exactly these fields: `id`, `lemma`, `russian`, `aspect`, `tense`,
`reflexiveParticle`, `forms`, `note`, `source`, `studyGroup`.
`forms` has six keys `1sg`, `2sg`, `3sg`, `1pl`, `2pl`, `3pl`, each an array of
accepted strings. The attachment has 240 slots and one accepted string per slot;
the implementation also supports 1–50 ordered accepted answers per slot.
`source` has `title`, `url`, `section`, `checkedOn`, all preserved.

Actual examples:

| Entry | Lemma | 1sg | 2sg / 3sg | 1pl | 2pl | 3pl |
| --- | --- | --- | --- | --- | --- | --- |
| bgaor-021 | чета | четох | чете | четохме | четохте | четоха |
| bgaor-022 | прочета | прочетох | прочете | прочетохме | прочетохте | прочетоха |
| bgaor-029 | облека се | облякох се | облече се | облякохме се | облякохте се | облякоха се |

There are eleven imperfective and twenty-nine perfective verbs. Two entries
explicitly carry `се`, thirty-eight have a null particle. Equal surface forms
for different persons are allowed: they are different grammatical targets.
The studyGroup values are model labels `1_ah`, `2_ih`, `3_ah_stem`, `4_oh`,
`5_short_or_changed`, `6_yah`; these are not CEFR levels or aspect study stages.
Future content may use another nonblank studyGroup string.

No duplicate IDs, missing/nonempty-slot violations or malformed source structures
were found in this attachment. Its note that old application importers had not
been verified describes the original material, not this new dedicated importer.
No linguistic correction or generated form was silently substituted. Source
URLs were retained as provenance; this task does not claim an independent
dictionary/linguist verification of all conjugations.

No aorist web implementation has been located. The last inspected web refs remain
master `2792dc3e4cb5d4762783140729e2c37ccac95567` and aspect feature
`be6a91ff2ae06b8b0eae2461d8c7a8bf910ce57a`. Their existing code guided architecture
only; no aorist web parity is claimed. The web repository is unchanged.

## On-device workflow

1. Open **Главная → Аорист**.
2. Choose **Просмотреть стартовый набор: 40 глаголов**, or **Выбрать JSON аориста**
   to select a local file through Android OpenDocument.
3. Inspect the dataset metadata, diagnostics and additions/unchanged/updates.
   Open record details to inspect before/after forms, notes and sources.
4. Additions are initially selected. Changed and unchanged records require your
   own selection. Select updates explicitly; no hidden answer merging occurs.
5. Tap **Подтвердить импорт**, then **Сохранить выбранное** in the confirmation.

Opening the section, selecting a file and previewing the starter asset do not
write any content. No imports run at startup. A recreated ViewModel loses the
opaque preview and asks you to select again; it cannot silently confirm a file.

Management supports paginated25-row lists (repository limit1–100), Russian/
Bulgarian/accepted-form search, exact dataset/aspect/studyGroup/particle filters,
view/create/edit and archive/restore. The current filter fields use source
aspect codes `imperfective`/`perfective`; particle `all` means no restriction,
empty means no particle, `се`/`си` select those exact metadata values.

Each person editor accepts one approved answer per line; blank rows are errors.
No suffix algorithm fills an omitted slot. Notes support multiline text.
Manual entries receive a persisted UUID once, retained in the edit draft across
recreation. Existing external ID and dataset are read-only. Personal source
records may have an empty URL (rather than an invented citation); otherwise URLs
must be absolute HTTP(S), without embedded credentials. Title/section/date and
all six answer arrays are still required.

Export uses Android CreateDocument, selected datasetId and explicit archived
inclusion. It writes its own content JSON v1, preserving root/source metadata,
revision, IDs and all answers. Archive flags select which records to include but
are not new learning-format fields; reimporting content never unarchives an
existing row. Full backups preserve actual archive flags.
There are no broad-storage or network permissions. Local documents and all
management operations work offline. Choosing a cloud-backed document provider
can require that provider's connection; copy the file locally for airplane mode.

## Storage, identity and update safety

Room migration9→10 adds only:

- `aorist_datasets`: internal ID, unique external datasetId, declared revision,
  full supported root metadata, optimistic version and timestamps;
- `aorist_verbs`: dataset FK and unique external entry ID scoped to it; all
  lexical/source fields, content/search hashes, archive/version/timestamps;
- `aorist_forms`: six explicit persistent targets, unique (verbId, person);
- `aorist_answers`: ordered surface strings/normalized keys per form;
- `aorist_receipts`: confirmed request, preview/selection/file hashes and summary.

The content stage therefore has **34 tables and23 autoincrement sequences**, not
the eleven-table whole-feature proposal from the earlier plan. No future
session/progress placeholder tables or fabricated learning records were added.
All existing29 tables and their sequences remain. Exported schema10 is part of
the review. No destructive fallback or data recreation is used.

External datasetId, external entry ID, internal dataset/verb/form IDs,
datasetRevision, JSON formatVersion, Room schema version and optimistic versions
have different roles. Reimport identifies entries by (datasetId, entry ID), not
lemma, revision, filename or array index. Manual IDs are UUIDs. Current IDs use
1–100 ASCII letters/digits/periods/underscores/hyphens.

Updating preserves verb IDs, all six form IDs, archive state and creation time.
Accepted-answer rows may be replaced, but form targets are never deleted. Later
sessions must snapshot approved content and refer to stable form IDs, not answer
row IDs. There is no aorist learning history to overwrite in this content stage.
Other modes' progress is never touched. Content omitted from an imported file
is retained; it is neither deleted nor archived automatically.

Preview is bound to immutable parsed bytes/file hash, dataset and record versions,
thirty-minute expiry, and contentEpoch. Confirmation checks staleness inside a
coroutine transaction before writes. Invalid whole files produce diagnostics
and no selectable changes. Related content, metadata and receipt writes are one
transaction. Identical retries return the same receipt; conflicting selections
and stale previews fail. A full restore invalidates outstanding previews even
when numeric IDs happen to match.

Partial selected imports persist the explicitly previewed root metadata/revision.
That revision names the last selected source revision, not a claim that every
record in the source was imported. The preview warns about metadata changes.
Neither manual edits nor partial imports pretend to create a new publisher
revision. A revision change is not used as an excuse to reset learning.

Parser validation is strict, bounded1MiB/1000entries, supports UTF-8/BOM and rejects
unknown format/version/fields, duplicate keys/IDs, missing/wrong-typed required
fields, unsupported aspect/tense/particle, invalid dates/source, missing/empty
answer arrays and normalized duplicates within one person. Root metadata is
validated/preserved, not confused with entry fields. A different content format
or full backup is rejected. Unknown future data is never silently dropped.

Duplicate-answer keys use NFC → remove remaining U+0301/U+0300 → Unicode whitespace
collapse/trim → Locale.ROOT lowercase → NFC. This preserves и/й/ѝ and се/си.
This stage provides no answer-grading/practice UI. Future practice must retain
independent form/type progress and use snapshotted approved answers.

## Full backup extension

The same release writes **full backup v4/schema10** containing all34 tables and23
sequences, including aorist source metadata, all forms/answers/archive and import
receipts. Content JSON v1 is separate and is not a progress backup.

Original full backups v1/schema7, v2/schema8 and v3/schema9 still authenticate
their original exact inventory/checksum before staging expansion. Restore is
complete replacement: v1–v3 have no aorist data, so restoring one clears current
aorist content. The preview and confirmation dialog warn explicitly. Existing
v1/v2 aspect replacement rules also remain. An older app rejects v4; do not
relabel a new copy or trim new tables to make it look old.

Preparation validates all rows/identifiers/FKs/source/slots/answer order/search
and content hashes in an isolated Room database before live changes. Frozen
preview, explicit confirmation, transactional replacement/rollback, high-water
sequences, contentEpoch and logical committed-WAL snapshot are retained.

## Verification

New suites cover original40/240-slot parsing/hash, encodings/diagnostics/alternatives,
strict versions, content round-trip, stable reimports/IDs, explicit selected
updates, paging/search/archive, expiry/stale/replay/races and rollback. Migration
fixtures preserve every old table, active sessions/progress/settings and sequence
high-water marks. Full-backup tests cover aorist round-trip/reopen, originalv3
authentication/replacement, malformed relationships, late failure and WAL.
SAF ViewModel tests cover the real ContentResolver input/output flow, explicit
starter action, default update deselection, draft recreation and lost-preview
safety. Synthetic alternative forms in tests are labelled as synthetic, not
asserted dictionary facts.

[Successful Actions run44395871402](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/44395871402)
verified commitf3f0cb9642633be06d6015065416f230ee47da11: all349 JVM tests passed,
including all24 new aorist tests; both lints passed with0 errors/14 existing
advisories each; debug and unsigned release assembly, APK contracts and exported
Room schemas1–10 passed. BUILD_REPORT.md records exact commands, corrected older
fixture failures, artifacts/hashes and local toolchain limitations.

[Debug APK ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/44395871402/artifacts/14470602646)
contains app-debug.apk,0.6.0/code7/API26+, expires2026-12-02. This is a debug
signature, not a verified persistent-key release. Save a full backup before
updates; keep existing data/installation if signatures differ. No replacement
release key was generated. Existing signing workflow remains unchanged.
Robolectric/native SQLite are JVM tests, not instrumentation or real-device
verification. Physical checks still include file providers/cancellation, Cyrillic
keyboard, small screens/large fonts/TalkBack, rotation/editor recreation,
airplane mode, and in-place update with the same persistent signing key.
