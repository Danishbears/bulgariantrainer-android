# Review scheduling and statistics increment — 2026-10-19

Target branch `codex/android-foundation-parity` started clean at
`84a466610e0cf7ee724f47c699c28dffcd2fc014`, matching GitHub integration.
Read-only web checkout remains `master` at
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`. Actual scheduler, statistics,
selection, entities, V6 migration and tests were inspected; both checkouts reused.
No target/ancestor AGENTS.md was found. Reference Java instructions were read and
do not replace the existing Android stack. No reference files were edited.

Added literal scheduler/selection ports, independent schedules, scoped recovery,
transactional schedule/counter/session changes, explicit legacy migration, real
statistics and lifecycle UI. New Room v6 preserves v5 attempts/counters/queues,
adding only scope and scheduling facts. REVIEW_AND_STATISTICS.md maps sources,
cases, migration policy, UTC/device clock handling and reference defects.
No applicationId, UI toolkit, JDK/SDK/Gradle/AGP version, backend, AI or signing change.

Local checks actually executed: `git diff --check` for app/docs changes,
`git diff HEAD^ HEAD --check -- . ':!parity/reference'`, `sh -n gradlew`, Python XML
resource checks (306 resolved references), SQLite migration/fixture execution
with FKs enabled and unchanged old attempts/counters, and static count **192 test
methods** (not a pass). `python3 parity/verify_web_parity.py` **actually passed**:
21 histories executed through the pinned Java scheduler/original record methods;
20 literal statistics expectations verified against pinned integration-test source.
Exact copied source hashes/paths are committed in parity/reference/PROVENANCE.json.
The staged full-tree whitespace check reports an upstream extra EOF blank line in
parity/reference/AttemptResult.java. That frozen file deliberately remains byte-exact;
app/docs whitespace checks exclude pinned source copies and pass. Source hashes
are checked by the executable parity verifier.

Environment observations: shell temporarily failed with exec-server transport
disconnected, then a local `pwd` succeeded. Both GitHub integrations work. Earlier
socket-denied networking was not retried. Runtime 21.0.12.1 has no javac executable
in PATH, but `java --list-modules` reveals jdk.compiler: the reference verifier
successfully uses `java -m jdk.compiler/com.sun.tools.javac.Main`. This corrects the
previous assumption that lack of javac in PATH meant no usable compiler module.
Android SDK/Gradle bootstrap remain unavailable locally; no local Android build
is claimed. Cloud HTTP policy reports enforced/unrestricted, without configured
credentials; that does not establish TCP access. Hosted CI uses its existing JDK17
and SDK35 plus a new pinned-web fixture guard before JVM tests.

### First hosted execution and exact compiler schema capture

Code `208b751400411bd86472cddb60ac90d15fc45018`,
[run 39307721934](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39307721934),
job `118249011872`, executed:

| Command/check | Actual result |
| --- | --- |
| `python3 parity/verify_web_parity.py` | 21 Java scheduler histories and 20 literal web statistics assertions verified |
| `./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest` | BUILD SUCCESSFUL in 3m 19s; **192 tests**, zero failures/errors/skipped |
| `./gradlew --no-daemon --stacktrace --console=plain assembleDebug` | BUILD SUCCESSFUL in 50s |
| `./gradlew --no-daemon --stacktrace --console=plain lintDebug` | BUILD SUCCESSFUL in 59s |
| Schema contract | Versions 1–5 verified; failed because generated `6.json` was not yet committed |

This first overall run failed the missing-schema guard; APK availability is not
workflow success. Captured the exact Room/KSP-generated v6 JSON, identity
`d539ac4e46ca9a1a6a5f1d8ad8ba0ed5`. Local Python/SQLite then compared all 17 old
table contracts: only the three session tables gain `study_scope DEFAULT 'MIXED'`;
two schedule tables are added. Executing the actual migration SQL against exported
v5 produces the same table/index/FK PRAGMAs as fresh compiler-generated v6.
The inspection script initially assumed optional `foreignKeys`/`indices` JSON
keys were always present; using optional-key reads corrected that script and the
comparison passed. No production migration change was needed.

### Final successful hosted execution

Tested code **`4392ab3043cd15bbc264d40d64bdfe70bc5ca8b8`**,
[successful run 39308758277](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39308758277),
job `118252573530`. GitHub API confirmed the completed run's `conclusion=success`
and exact `head_sha`; all required steps succeeded:

| Command/check | Actual final result |
| --- | --- |
| `python3 parity/verify_web_parity.py` | 21 identical Java scheduler histories; 20 literal web statistics assertions verified |
| `./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest` | BUILD SUCCESSFUL in 2m 47s; **192 tests**, zero failures/errors/skipped |
| `./gradlew --no-daemon --stacktrace --console=plain assembleDebug` | BUILD SUCCESSFUL in 1m 3s |
| `./gradlew --no-daemon --stacktrace --console=plain lintDebug` | BUILD SUCCESSFUL in 1m 10s |
| Schema guard | Committed Room schemas **1–6** match compiler exports |

All 165 previous tests and 27 new cases passed: eight scheduler/selection cases,
six statistics fixture/date/ranking cases, ten transactional scheduling/statistics
Room cases, one populated legacy-v5 upgrade case and two statistics ViewModel cases.
The 21 golden histories are assertions within those tests, not 21 extra test methods.

[Download APK ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39308758277/artifacts/12214608955)
(artifact `12214608955`, **10,839,804 bytes**, expires `2026-11-02T19:16:06Z`).
Extract `app-debug.apk` before installation; ANDROID_BUILDS.md covers installation
and why different cloud debug keys may prevent an in-place update. GitHub reports
ZIP artifact digest `sha256:2196ceb9866639a5b65801e6baac2e6acf1c6ababd3bef0235cf505c99de256e`;
this is not a separately measured extracted APK checksum. No local artifact download
or device installation was executed.

[JUnit, HTML test, lint and schema reports](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39308758277/artifacts/12214019699)
(artifact `12214019699`, 124,127 bytes, expires `2026-11-02T19:16:05Z`).
Final documentation-only follow-up records this evidence with `[skip ci]`;
application, workflow and build files remain identical to the tested commit.
The existing PR is updated without merging `main`. No APK, SDK path, credentials,
build output or private database is committed.
Device process-kill/time-zone/clock/DST/IME/accessibility checks remain unperformed.
Previous milestone evidence follows.

# Offline sentence increment — 2026-10-19

Target `codex/android-foundation-parity` started clean at
`3257b19570aa89b85e9e147a5794ced4bc72d63d`, matching GitHub integration.
Read-only reference remains `master` at
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`; both existing checkouts were reused.
No target/ancestor/nested AGENTS.md was found; reference instructions and actual
sentence management/practice source, DTOs, migrations, browser UI and tests were
read. Existing Kotlin/Compose/Room, applicationId and toolchain are preserved.

Implemented sentence CRUD/archive/paging, optional metadata, approved alternatives,
multiline RU→BG practice, exact automatic matches, explicit pending self-assessment,
durable feedback/progress/summary/recovery and independent sentence counters.
No AI, generation, backend, startup import, vocabulary mastery or schedule added.
SENTENCES.md maps rules, representative cases, discrepancies and the v4→v5 migration.

Local commands/checks actually executed: `git diff --check`, `sh -n gradlew`,
Python XML/resource checks (268 resolved Kotlin string references) and static count
of 164 test methods (not a test execution). Python SQLite executed migration 4→5
with foreign keys enabled against exported v4 SQL: sentence/alternative IDs,
values and sequence high-water marks retained; nullable difficulty works. All new
migration fixture SQL statements were also checked against the actual v4 schema.
`java -version`: runtime 21.0.12.1. `shutil.which` finds no javac/gradle/sdkmanager/adb.
No local Android build is claimed. GitHub integration access was reconfirmed for
both repositories. Shell networking was previously blocked with socket operation
not permitted and was not repeatedly retried. Current cloud HTTP policy reports
enforced/unrestricted with no credentials; it is distinct from TCP/shell build
capability and the missing local tools.

### First hosted execution and exact compiler schema capture

Code `71315cdddc93a95adb31ee5e680842e7f5039e0b`,
[run 39301985450](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39301985450),
job `118229255441`, executed the following separate commands/checks:

| Command/check | Actual result |
| --- | --- |
| `./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest` | BUILD SUCCESSFUL in 2m 50s; **164 tests**, zero failures/errors/skipped |
| `./gradlew --no-daemon --stacktrace --console=plain assembleDebug` | BUILD SUCCESSFUL in 1m 1s; APK packaged/uploaded |
| `./gradlew --no-daemon --stacktrace --console=plain lintDebug` | BUILD SUCCESSFUL in 1m 4s |
| Schema contract | Versions 1–4 verified; failed because newly generated `5.json` was not yet committed |

Overall run **failed** only at the missing-schema guard. Its APK artifact is
`12210829843`, reports `12211033410`; APK availability alone is not workflow success.
All 128 existing tests and 36 new tests passed: 24 sentence repository cases,
ten sentence/editor/list ViewModel cases and two exported-v4 migration cases.
The exact compiler-generated v5 JSON was captured from the guard diagnostic and
committed; actual identity **`18a2b4a0ce8d53b6710e88643e1c2014`** came from Room/KSP.
Structural comparison confirms 12 previous table contracts are identical; the
sentence table changes only difficulty nullability, with four new practice tables.
Python SQLite migrated exported v4 and matched table/foreign-key/index PRAGMAs for
all 17 tables against fresh compiler-exported v5 contracts.

A code-path review also found the export ViewModel replaced every specific export
failure with WRITE_FAILED, hiding the new missing-difficulty explanation. The
follow-up preserves that specific error and adds a SAF/ViewModel regression;
other write failures retain existing handling. A normalization fixture now includes
Bulgarian й so NFD submissions actually exercise canonical equivalence. Static test
count becomes **165**; executed follow-up results are recorded below.

### Final hosted verification: successful

Tested code: **`f61ad8c6a17450ba9e76320f895e78ed2b98ae7c`**, branch
`codex/android-foundation-parity`.
[Run 39302954478](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39302954478),
job `118232618011`, completed **successfully** on 2026-10-19. Hosted runner used
Temurin JDK 17.0.20+1, committed Gradle 8.11.1, AGP 8.9.2, Android platform 35
and Build Tools 35.0.0. Separate commands/checks actually executed:

| Command/check | Actual result |
| --- | --- |
| `./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest` | BUILD SUCCESSFUL in 3m 5s; **165 tests**, zero failures/errors/skipped |
| `./gradlew --no-daemon --stacktrace --console=plain assembleDebug` | BUILD SUCCESSFUL in 1m 4s; debug APK packaged/uploaded |
| `./gradlew --no-daemon --stacktrace --console=plain lintDebug` | BUILD SUCCESSFUL in 1m 9s; report uploaded |
| Committed Room schema contracts | Versions **1, 2, 3, 4 and 5 verified** against compiler exports |

All 128 previous tests passed. The 37 new cases comprise 24 sentence repository
checks, ten sentence management/practice ViewModel checks, two real exported-v4
migration checks and one SAF/ViewModel export-message regression. Recovery tests
close file databases and create fresh ViewModels with empty SavedStateHandle;
they do not claim physical Android OS process termination. The subsequent commit
only records documentation and does not change the tested application/workflow
or represent another application build.

Artifacts from the successful run (GitHub login/access may be required):

- [bulgariantrainer-debug ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39302954478/artifacts/12212419200):
  10,715,264 bytes; extract `app-debug.apk` before installation.
- [android-storage-test-reports ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39302954478/artifacts/12210704611):
  106,430 bytes; JUnit XML/HTML, lint reports and schema contracts.

Both expire 2026-11-02 under the existing 14-day policy. GitHub-reported SHA-256
of uploaded **ZIPs** (not the extracted APK):

```text
APK ZIP:     5312a962eee2d144978edfba424e927cb0aeb693937f9e9c62edbb0c222a996f
Reports ZIP: 05357e2245c3a090dff5dba0cf69ac6576236b1faa82664e14fcd6bd8cb0f4e4
```

GitHub integration access and local checkouts work for both repositories. The
web checkout remains clean at the pinned reference. Shell networking was previously
blocked and not retried; missing local compiler/SDK tools remain independent of
GitHub authorization. All successful Android commands above ran on hosted Actions.
No local SDK paths, credentials, APK/build/private database outputs are committed;
Linux wrapper mode stays 100755. Existing import/export/flashcard/typing work and
application identity are preserved. Scheduling and word associations remain deferred;
sentence practice never awards vocabulary mastery. No AI or generation is added.

Remaining device/emulator checks: install/upgrade with a compatible debug key,
airplane-mode practice, real OS process-kill/recovery around submit/self-rating/next,
multiline keyboard/IME resizing, rotation/navigation, rapid touch, large text and
TalkBack. See ANDROID_BUILDS.md for installation and cloud debug-key differences;
new builds are not guaranteed to update older debug APKs in place. No release
signing is configured. Earlier milestone evidence follows.

# Offline typing increment — 2026-10-19

Target branch `codex/android-foundation-parity` started clean at
`e8174ec0d667b27e65c87b5db0027011e01ca402`, matching GitHub integration.
Read-only reference `master` is unchanged at
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`. Existing checkouts were reused.
No target/ancestor/nested AGENTS.md was found; reference instructions and actual
planner, evaluator, service, DTOs, entity, migrations, browser UI and tests were
read. Web Java conventions do not replace Android Kotlin/Compose/Room.

Existing dictionary/content-transfer/flashcard functionality is preserved. Added
balanced typing planning, strict exact normalization, transactional durable
submissions/feedback, independent direction counters, Room v4 additive migration,
lifecycle-aware UI and parity/concurrency/recovery/migration tests. TYPING.md
records source mappings, representative cases, suspected web defects and deferred
scheduler behavior. No toolchain/dependency/app identity change or release signing.

Local checks actually executed: `git diff --check` for app/docs changes,
`git diff HEAD^ HEAD --check -- . ':!parity/reference'`, `sh -n gradlew`, Python XML
parsing/string reference resolution (217 Kotlin references), static test method
count **128** (not an execution result). `java -version` is runtime 21.0.12.1;
`shutil.which` finds no javac/gradle/sdkmanager/adb. No local Android build is
claimed. Shell Gradle networking was previously denied with socket operation not
permitted; no identical blocked download was retried. GitHub integration access
works for both repositories. Cloud HTTP policy observation is unknown/not enforced,
with no configured credentials; that does not establish shell network access.

### First hosted run: fixture failures and compiler schema capture

Code `073b241f3165f921ac076ff32f933788b4f5177f`,
[run 39296560879](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39296560879),
job `118210499749`, actually executed:

```sh
./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest
```

Result: **128 tests executed, 126 passed, two failed**, zero errors/skipped;
BUILD FAILED in 3m 5s. The failures occurred in new test fixture setup:
`TypingRepositoryTest.emptyOrTranslationlessPoolNeverCreatesSession` used a
nonexistent `normalized_bulgarian` column instead of `normalized_text`;
`TypingMigrationTest` omitted mandatory sentence source-ID/filename/row columns.
Both fixtures were corrected and their INSERTs executed locally using Python
SQLite against the actual exported-v3 CREATE statements. Production code did not
change for those failures. All six typing ViewModel tests, five pure rules tests
and the 96 existing tests passed. **APK assembly and lint were skipped**; this
run produced no APK. Its reports artifact is `12209501646`.

The schema guard also failed because the new compiler-generated `4.json` had not
yet been committed. The exact file was captured from its diagnostic, decoded and
committed; identity **`60b12add3ca435a9eeb8c7130f4f5ae4`** came from Room/KSP.
Structural comparison confirms all nine v3 table definitions are unchanged and
only four typing tables are added. Python SQLite also executed migration 3→4
against exported v3 and compared table/foreign-key/index PRAGMAs to a fresh v4
export: matching contracts. These local checks do not replace Room migration tests.
Versions 1/2/3 guards passed in the first run; final v4 verification is recorded below.

### Final hosted verification: successful

Tested code: **`45203c3898cda5a21eb0717c0720135f5a956c65`** on branch
`codex/android-foundation-parity`.
[Run 39297229919](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39297229919),
job `118212784621`, completed **successfully** on 2026-10-19. The runner used
Temurin JDK 17.0.20+1, committed Gradle 8.11.1, AGP 8.9.2, Android platform 35
and Build Tools 35.0.0. The following separate commands/checks actually executed:

| Command/check | Actual result |
| --- | --- |
| `./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest` | BUILD SUCCESSFUL in 2m 53s; **128 tests**, zero failures/errors/skipped |
| `./gradlew --no-daemon --stacktrace --console=plain assembleDebug` | BUILD SUCCESSFUL in 1m 7s; debug APK packaged/uploaded |
| `./gradlew --no-daemon --stacktrace --console=plain lintDebug` | BUILD SUCCESSFUL in 1m 2s; report uploaded |
| Committed Room schema contract | Versions **1, 2, 3 and 4 verified** against compiler exports |

All 96 existing tests passed. The 32 new tests comprise five pure typing rule
cases, 20 real Room repository cases, six ViewModel cases and the populated-v3
migration. Existing populated-v1/v2 tests now verify migration through to v4.
File reopen and fresh empty-SavedStateHandle tests demonstrate database recovery;
they do not claim a physical Android OS process-kill test. The following
commit records documentation only, with no application/workflow changes after
this tested code; it does not represent another application build.

Artifacts from this successful run (GitHub login/access may be required):

- [bulgariantrainer-debug ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39297229919/artifacts/12209895308):
  10,567,862 bytes; extract `app-debug.apk` before installation.
- [android-storage-test-reports ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39297229919/artifacts/12207609920):
  88,451 bytes; JUnit XML/HTML, lint reports and exported schema contracts.

Both artifacts expire on 2026-11-02 under the 14-day retention policy.
GitHub-reported SHA-256 digests of the uploaded **ZIPs**, not extracted APKs:

```text
APK ZIP:     c0cba1408d383f25fd92b342fcea47e855680ee8b27aa3be4d03fdd1acccdee3
Reports ZIP: e5a2d13fad33d6c4a1cad6e5492bae6c51e76faeee10148c74205fc8247e3600
```

GitHub integration access and local checkouts work for both repositories. The
read-only reference remains clean at the pinned commit. Shell networking remains
a previously observed independent blocker and was not retried. This workspace
still lacks a full local Android toolchain; all successful Android commands above
ran on GitHub Actions. No SDK paths, APK/build/database outputs or signing
credentials were committed; Linux wrapper mode remains 100755.

Remaining device/emulator checks: install/upgrade with compatible debug signing,
airplane mode, real OS process termination/relaunch around submit/acknowledge,
keyboard Done/IME resizing, rotation/navigation, rapid touch, large fonts and
TalkBack. See ANDROID_BUILDS.md for APK extraction/installation and debug-key
changes that can prevent updating an older cloud build in place. No release
signing or review scheduling is introduced.

The historical sections below preserve previous milestone evidence.

# Baseline and build evidence

## Durable flashcard increment — 2026-10-19

Starting target branch `codex/android-foundation-parity`, commit
`a3393a2c125a93955e5383ae1b835e3236c81d3d`, clean and matching GitHub. The read-only
reference remains branch `master`, commit `de0a3e883d51561123ca54b7d5dd2e53757baf0a`.
Existing checkouts, vocabulary/import/export work, applicationId, UI toolkit,
toolchain and workflows were preserved. No target/ancestor AGENTS.md was found;
web AGENTS.md and actual flashcard service/DTO/entity/JS/integration tests were read.
FLASHCARDS.md maps the exact sources and rules, including scheduling deferral.

Added Room v3 session/card/attempt/recognition tables with additive migration 2→3,
durable order/direction/answer snapshots/reveal/position/status, transactional
ratings/counters, replay/conflict protection, history-protected words and Compose
setup/training/recovery/summary. Recognition counters are shared across card
directions and isolated from future modes. No scheduler/NEW/DUE/mastery behavior,
other training mode, account/server, generated vocabulary or release signing added.

Local checks executed: `git diff --check`, staged whitespace checks, `sh -n gradlew`,
XML/string-reference verification (191 Kotlin resource references), and static
count of 96 test methods (not itself a test pass). `java -version` reports runtime
21.0.12.1; `shutil.which` finds no javac/gradle/sdkmanager/adb. No local JVM/Android
build is claimed and no previously blocked shell download was retried. GitHub
integration access was confirmed for both repositories and used for hosted builds.
Cloud environment HTTP policy currently reports state unknown, not enforced;
that setup observation is not evidence of shell networking or SDK availability.

### First hosted run

Code `fc63cbd823b7f3cfaab0c50fa5d41e723bb60999`,
[run 39271903831](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39271903831),
job `118126095384`, executed:

```sh
./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest
```

Actual result: **95 tests executed, 94 passed, one failed**; BUILD FAILED in
2m 29s. `FlashcardMigrationTest` failed while preparing its exported-v2 fixture,
before opening/migrating Room: the fixture reader assumed every entity contained
an `indices` array. Room omits it for index-free `import_receipts`. The reader was
fixed to treat the missing array as empty. The actual populated-v1→latest test,
20 repository parity/durability tests and six flashcard ViewModel tests passed.
APK assembly and lint were skipped, so this first run produced **no APK**.
The run's test-report artifact is `12195737851`. No success is claimed for it.

### Second hosted run and partial-pool selection fix

Code `937b011e6dc85d0fb8463d15c367b8203f398fb3`,
[run 39272656784](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39272656784),
job `118128744949`: 95 tests executed, 93 passed, two failed; BUILD FAILED in
2m 49s. The populated-v2 migration and all six ViewModel cases passed. Two
partial-pool tests failed with IllegalStateException in generated FlashcardDao
relation mapping (the initial run happened to pass them). Assembly/lint skipped.

The relation query previously combined SELECT * / RANDOM() / LIMIT. Room may reset
and re-execute the parent statement when building relation maps, allowing a
changed random subset on the second pass. Selection now fixes random IDs in a
scalar query and fetches word/translation relations in a separate deterministic
query inside the same transaction, preserving the sampled order. A new regression
repeats 3-of-12 selection twelve times and checks complete snapshots/relations.
Test logging now includes full exception traces, and schema verification also
runs after failures so compiler exports remain available for diagnosis; it still
fails on missing/mismatched committed schemas. Workflow permissions are unchanged.

### Third hosted run: code checks passed; schema capture required

Code `1768b17a50bcdc59564439af8a5773a251636e53`,
[run 39273484446](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39273484446),
job `118131609859`:

| Actual separate command/check | Result |
| --- | --- |
| `./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest` | BUILD SUCCESSFUL in 2m 25s; **96 tests**, zero failures/errors/skipped |
| `./gradlew --no-daemon --stacktrace --console=plain assembleDebug` | BUILD SUCCESSFUL in 49s; APK packaged/uploaded |
| `./gradlew --no-daemon --stacktrace --console=plain lintDebug` | BUILD SUCCESSFUL in 49s |
| Schema contract | Failed because compiler-generated `3.json` was not yet committed |

The overall run **failed** only at the missing-schema guard. The generated v3 JSON
was retrieved from its base64 diagnostic, decoded and committed without inventing
an identity hash. Its actual hash is `c096057d5434265d3679356753242cec`. Structural
comparison confirms all five v2 table definitions remain exactly unchanged; only
four learning tables are added. The guard remains strict and a follow-up run
checks generated v1/v2/v3 exports against the committed contracts.

### Final hosted verification: successful

Tested code: `7e0172d12ff1b95b05b174d19bd11bdd7a685249`.
[Run 39274478038](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39274478038),
job `118135077732`, completed **successfully** on 2026-10-19. The runner used
Temurin JDK 17.0.20+1, the existing Gradle 8.11.1 wrapper, Android platform 35 and
build-tools 35.0.0. The following commands/checks actually executed:

| Actual separate command/check | Result |
| --- | --- |
| `./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest` | BUILD SUCCESSFUL in 2m 58s; **96 tests**, zero failures/errors/skipped |
| `./gradlew --no-daemon --stacktrace --console=plain assembleDebug` | BUILD SUCCESSFUL in 1m 9s; debug APK packaged and uploaded |
| `./gradlew --no-daemon --stacktrace --console=plain lintDebug` | BUILD SUCCESSFUL in 1m; report uploaded |
| Exported Room schema contract | Versions **1, 2 and 3 verified** against committed JSON |

The 96 tests include 28 new flashcard cases: 21 repository parity/persistence
tests, six ViewModel tests and one populated-v2 migration test. All 68 existing
vocabulary/import/export/editor tests also passed, including the updated
populated-v1→latest migration test. The subsequent documentation-only commit
records these results; it does not represent another application build.

Artifacts from that successful run (GitHub login/access may be required):

- [bulgariantrainer-debug ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39274478038/artifacts/12198030314):
  10,486,221 bytes; extract `app-debug.apk` before installation.
- [android-storage-test-reports ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39274478038/artifacts/12197616131):
  78,002 bytes; JUnit XML/HTML, lint reports and schema contracts.

Both artifacts expire on 2026-11-02 under the existing 14-day policy.
GitHub-reported SHA-256 digests of the uploaded **ZIPs** (not extracted APKs):

```text
APK ZIP:     f258ff2eb6651e20be10cab706fc896436ddfc70eb9000733a3d18cf7b95f2ef
Reports ZIP: 7aadad5574c153acb7204aec5bc0cf894d27da80785a7cd136c9702192c77a3d
```

No APK/build output or private database was committed. Local Android build
capability remains unavailable in this workspace; the successful execution above
was on GitHub Actions. The read-only web checkout remains unchanged at its pinned
reference commit. Scheduling is intentionally deferred, not a build blocker.

Real device/emulator checks remain unperformed; FLASHCARDS.md lists process-kill,
airplane mode, reveal visibility/accessibility, rapid taps, rotation and install/
upgrade acceptance. File reopen/fresh-ViewModel tests are real persistence evidence
but not a claim of Android OS process termination testing.

The sections below preserve previous milestone evidence.

## Content import/export increment — 2026-10-19

Target starting revision: `6c3990df744ca294dc309d4287a617948d6d26b9`, branch
`codex/android-foundation-parity`, clean and matching GitHub. Read-only reference
remains `master` at `de0a3e883d51561123ca54b7d5dd2e53757baf0a`. Existing app, tests,
workflows and parity work were preserved. There is no target/ancestor AGENTS.md;
web instructions, actual parsers/writers/tests and both available datasets were
inspected. Exact source map, formats and differences are in IMPORT_EXPORT.md.

Added document-picker import/creation flows, editable source-preserving previews,
explicit append/selection/confirmation, strict UTF-8/TXT/CSV/JSON parsing, sentence
content/alternatives, transactional confirmation receipts and CSV content exports.
One explicit sample-preview button exposes the unchanged 100-sentence dataset;
no launch-time import exists. Existing applicationId/Compose/toolchain are retained.
Room v2 uses additive migration 1→2; vocabulary table definitions are unchanged.
No training/progress mode, backend, storage permission or release signing added.

Local checks actually executed:

- `git diff --check`, `git diff --cached --check`, `sh -n gradlew`: passed.
- Python XML parsing/resource-reference resolution: 149 Kotlin resource references
  resolve; static count is 68 test methods (not itself an execution result).
- `git ls-files -s gradlew`: mode 100755 retained. `git check-ignore` confirms SDK
  location files, APK outputs, private SQLite data and `.env` remain ignored.
- `sha256sum` on the reference/bundled sentence CSV: identical
  `403b2a0416491d1e02c3db16459bbb878806da8904b34c320e0f758610441d0a`.
- Python strict UTF-8/CSV/JSON inspection: actual sentence header, 100 rows,
  20 alternatives; original TXT 397 records including 10 blank records.
- Structural comparison of exported schemas: v2 `words` and `translations` are
  identical to v1. V2 identity `bcea5278b79bd932a6e247746e21b2dc` came from Room/KSP.
- `java -version`: runtime 21.0.12.1. Python `shutil.which` found no `javac`,
  `gradle`, `sdkmanager` or `adb`. No local Android task execution is claimed.

GitHub integration reads/writes work; exact checkouts were reused. The earlier
local Gradle bootstrap failed with `SocketException: Operation not permitted`.
No identical blocked shell network/download command was retried in this increment.
Cloud policy reports unrestricted/enforced HTTP, but no TCP destinations are
listed and no credentials are configured. That setup is separate from working
GitHub integration and full JDK/SDK availability on hosted Actions runners.

### Initial hosted execution and compiler schema capture

Code `ccd94674ec2e01fac9316760217e88bf9bcb0a5f`,
[run 39254155286](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39254155286),
job `118065118754`:

| Actual separate command/check | Result |
| --- | --- |
| `./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest` | BUILD SUCCESSFUL in 2m 22s; 67 tests, zero failures/errors/skipped |
| `./gradlew --no-daemon --stacktrace --console=plain assembleDebug` | BUILD SUCCESSFUL in 1m 2s; APK produced/uploaded |
| `./gradlew --no-daemon --stacktrace --console=plain lintDebug` | BUILD SUCCESSFUL in 54s |
| Room schema contract | Failed because the new compiler-generated `2.json` was not yet committed |

The overall initial run **failed**, despite tests/assembly/lint succeeding. The
schema guard was not bypassed: its diagnostic exported the generated v2 JSON,
which was retrieved from runner logs, decoded and committed for follow-up
verification. No hand-invented schema hash was used. Subsequent changes also
add an independent CSV/JSON quote-escaping round-trip case and refine preview UI.
Final verification is recorded separately below when executed.

### Final hosted verification: successful

Final tested code: `5defa7518f4956441cf83a4511d99400c1ea4142`.
[Run 39255088356](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39255088356),
job `118068247579`, completed **successfully** with full JDK 17 and Android SDK 35.
The following separate commands actually ran:

| Command/check | Actual result |
| --- | --- |
| `./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest` | BUILD SUCCESSFUL in 2m 22s; **68 tests**, zero failures/errors/skipped |
| `./gradlew --no-daemon --stacktrace --console=plain assembleDebug` | BUILD SUCCESSFUL in 59s; debug APK packaged |
| `./gradlew --no-daemon --stacktrace --console=plain lintDebug` | BUILD SUCCESSFUL in 54s |
| Schema contract | Both exported `1.json` and `2.json` structurally equal the committed files |

Executed suites: vocabulary repository 19, text normalization 5, validator 4,
content migration 1, content parser 10, content transfer repository 16, transfer
ViewModel/document provider 5, word editor ViewModel 8. The **32 added checks**
include no-write previews, actual dataset, original source ranges, malformed
UTF-8/CSV/JSON, append/duplicate/race/rollback safety, confirmation receipts after
reopen, format round-trips, provider streams and populated-v1 migration.

- [Download debug APK ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39255088356/artifacts/12187937054):
  `bulgariantrainer-debug`, artifact `12187937054`, 10,393,793 bytes.
  Extract `app-debug.apk` before installing. API-reported artifact ZIP SHA-256:
  `41d30bd57a1d835f682d3a006cb2ed420572f7103ad8582e3013ac7a654b1055`.
- [Download JUnit/lint reports and schemas](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39255088356/artifacts/12187837259):
  artifact `12187837259`, 62,012 bytes.

Artifacts expire 2026-11-02; GitHub sign-in may be required. Sizes/digests describe
ZIP artifacts, not a separately inspected extracted APK. The APK was not installed
or launched on a device. Follow-up documentation-only changes do not change this
tested application, schema, test code or workflow; they are marked `[skip ci]`.
The existing feature branch/PR #1 is updated; `main` is not merged by this task.

Device/emulator checks remain unperformed: actual picker/provider cancellation,
URI-grant failures, disk/provider write failure, IME/dialog layout, rotation and
OS process recreation, local-document airplane mode, accessibility and long
preview performance. Robolectric tests cover provider streams and ViewModel
state, not the OS picker or every DocumentsProvider. Content CSV is not a complete
progress/data backup; read its documented format limitations before relying on it.

The sections below retain historical milestones.

## Vocabulary UI and persistence increment — 2026-10-19

Starting target: branch `codex/android-foundation-parity`, commit
`66854f0e5c3a9606f81cfd6c11ebaea014282516`, initially clean and matching GitHub.
Reference: local branch `master` at `de0a3e883d51561123ca54b7d5dd2e53757baf0a`,
unchanged. No target/ancestor AGENTS.md exists; the web AGENTS.md and applicable
source/parity documentation were inspected. Existing checkouts were reused.

Already present: app identity, Compose/Material 3, compatible pinned toolchain,
Room v1 entities and transactional repository, normalization/search/archive,
20 real JVM/Robolectric tests, exported schema and Actions APK/report workflow.

Added: Russian dashboard with actual counts, bounded Room Paging dictionary with
bilingual search, active/archive filters, manual editor with alternatives and
metadata, archive/restore, shared field validation, lifecycle-aware ViewModels,
SavedStateHandle drafts, loading/empty/error states and 16 additional tests.
No schema/entity change, app identity change, demo records or training feature.

| Capability | This increment's observed result |
| --- | --- |
| GitHub integration | Repository read/write access works; used connected tools to update the existing PR #1 branch and trigger CI. |
| Local checkouts | Both repositories already present with real source and exact Git commits; no clone or project recreation. Reference remains clean. |
| Shell networking | Gradle distribution download failed with socket denial; not treated as missing GitHub authorization and not retried. |
| Local Android build | Java runtime only; no javac/sdkmanager/adb/standalone Gradle on PATH. Local tests and APK build did not reach Gradle task execution. |
| GitHub-hosted build | See the actual run result recorded below. Configuration alone is not counted as passing. |

Actual local command:

```sh
env GRADLE_USER_HOME=/tmp/bulgariantrainer-gradle ./gradlew --no-daemon testDebugUnitTest assembleDebug
```

Exit 1 while bootstrapping Gradle 8.11.1:
`java.net.SocketException: Operation not permitted`. No local JVM test or Kotlin
compilation is claimed. There was one attempt in this increment.

Executed static checks: `git diff --check`, `git diff --cached --check`,
`sh -n gradlew` and Python parsing of all four Android XML files passed.
All 63 Kotlin string-resource references resolve. These checks do not compile
Kotlin or substitute for AGP lint. `git ls-files -s gradlew` confirms Linux mode
100755. `git diff HEAD^ -- app/schemas .../TrainerDatabase.kt` was empty for the
implementation commit: Room remains version 1 with the original exported schema.

### Executed GitHub Actions result

Code commit: `95d4b6ae9650bfd709df3ce34e5ee56816c83d43`.
[Run 39247504111](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39247504111),
job `118042915140`, completed **successfully**. Runner used full Temurin JDK 17,
Android SDK 35/Build Tools 35.0.0, and the committed executable wrapper.
The following commands actually executed as separate steps:

| Command/check | Observed result |
| --- | --- |
| `./gradlew --no-daemon --stacktrace --console=plain testDebugUnitTest` | BUILD SUCCESSFUL in 2m 6s; 36 tests, zero failures/errors/skipped |
| `./gradlew --no-daemon --stacktrace --console=plain assembleDebug` | BUILD SUCCESSFUL in 1m 1s; debug APK packaged |
| `./gradlew --no-daemon --stacktrace --console=plain lintDebug` | BUILD SUCCESSFUL in 46s |
| JUnit XML summary | Repository 19, normalization 5, validator 4, ViewModel 8; total 36 |
| Exported Room schema contract | Generated v1 JSON structurally equals the committed schema |

The compiler reported a non-blocking deprecation for `Icons.Filled.List` in favor
of its AutoMirrored variant. Runner logs also warn about upstream action Node.js
runtime deprecations; setup, tests, packaging, lint and uploads still completed.
No claim of warning-free compilation or physical-device verification is made.

Artifacts (GitHub sign-in may be required; retention until 2026-11-02):

- [Debug APK ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39247504111/artifacts/12184760508),
  `bulgariantrainer-debug`, 10,117,648 bytes. Extract `app-debug.apk` before
  installing. API-reported ZIP SHA-256:
  `e7780e2f147e426cbcba96f2162b1500c405312c98b68a26bd09945c8c1f832d`.
- [JUnit/lint reports and exported schema](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39247504111/artifacts/12184645657),
  `android-storage-test-reports`, 48,360 bytes.

These are artifact ZIP digests/sizes, not an independently measured extracted
APK digest. Follow-up documentation-only changes do not alter the tested app,
dependencies, tests, schema or workflow. The existing PR #1 branch is updated;
`main` is not merged or replaced by this task. Debug signing is unchanged;
successive cloud-runner keys may differ, as explained in ANDROID_BUILDS.md.

### Device/emulator checks still required

No device or emulator was used. Verify fresh install is empty, manual Cyrillic
input, duplicate/length messages, adding/removing/selecting translations, edits,
archive/restore, search, restart persistence and airplane-mode operation. Check
rotation and process recreation with an unsaved long form, IME visibility, Back
confirmation, narrow screens, large font, dark mode, TalkBack and dictionary
scroll/search performance on the target phone. SavedStateHandle unit tests do
not establish Android OS process-kill behavior or recovery during an in-flight
write. No migration step is needed for this unchanged v1 schema; future schema
changes still require explicit preservation-tested migrations.

The following sections retain historical baseline evidence.

## Room vocabulary increment — 2026-10-19

Starting Android revision: `7043dedcbeea62945cecf954c62cd9a5c21ae8d9` on
`codex/android-foundation-parity`, clean locally and matching the connected GitHub
branch. The reference remains `master` at
`de0a3e883d51561123ca54b7d5dd2e53757baf0a`, with no tracked changes. GitHub tools
again confirmed authenticated login `Danishbears` and repository access. No clone
or project recreation was needed. There is no target/ancestor AGENTS.md; the
reference AGENTS.md was read, along with its actual normalization/word service,
entities, DTOs and normalization tests. The existing parity audit was retained.

Already implemented: Kotlin/Compose/Material 3 welcome screen, applicationId
`com.danishbears.bulgariantrainer`, official Gradle wrapper, compatible pinned
build configuration, complete parity audit and working Actions workflows.

Added: Room 2.7.2/KSP 2.1.20-1.0.32, an app-private v1 vocabulary database,
transactional validated repository, injected UTC clock, Unicode normalization,
search/archive/pagination/Flow support, and 20 JVM/Robolectric tests. The existing
welcome screen and applicationId are unchanged; no training modes or imports
were added. LOCAL_STORAGE.md describes the API, schema and limits.

Current local command results:

| Command | Observed result |
| --- | --- |
| `./gradlew assembleDebug` | Exit 1 before project evaluation: the default `/home/agent/.gradle` cache could not create its lock directory in the filesystem sandbox. |
| `env GRADLE_USER_HOME=/tmp/bulgariantrainer-gradle ./gradlew assembleDebug` | Exit 1 at distribution download: `java.net.SocketException: Operation not permitted`. No compilation/test tasks ran locally. After this socket denial, no further local Gradle/network retries were made. |
| `java -version`; `command -v javac sdkmanager adb gradle` | Java runtime 21.0.12.1 present; no javac, SDK commands, adb or standalone Gradle found. |
| `node src/test/js/pronunciation.test.mjs` from the read-only reference | 9 tests passed, zero failed/skipped; reference files unchanged. |
| `git diff --check`; `sh -n gradlew`; Python XML and workflow-YAML parsing | Passed. These are static checks, not a Kotlin/Room compilation result. |
| `sha256sum gradle/wrapper/gradle-wrapper.jar` and Python hashlib verification | Original official checksum unchanged. Wrapper/settings/SDK/AGP/Kotlin versions remain intact; only Room/KSP/test dependencies were added. |
| `git check-ignore local.properties app/build/outputs/apk/debug/app-debug.apk test.db test.db-wal .env` | All ignored; no local SDK paths, database contents, credentials or build outputs staged. Exported Room schema JSON is intentionally tracked as a migration contract. |

Initial Room CI execution:
[run 39228709486](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39228709486),
code commit `0079b9a4ca8b58648b66ed442946a28537e9b116`.
The existing workflow ran `./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug`
with full JDK 17 and SDK 35 and finished **BUILD SUCCESSFUL in 2m 30s**. KSP,
Kotlin compilation, the unit-test task, lint and APK packaging all executed.
This is new Room-code evidence, not the previous foundation's passing build.

The compiler-generated Room v1 schema has identity hash
`b3c43139a01c2d8d76ca272ad1d8982d` and was retrieved from the runner's export,
not hand-invented. It is now checked into `app/schemas/`. A follow-up CI check
compares the generated and committed schema structurally and summarizes executed
JUnit XML counts, preventing an empty NO-SOURCE task from appearing as a test pass.

- [Room debug APK ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39228709486/artifacts/12175712131):
  9,141,097 bytes; ZIP SHA-256
  `0b0de68368678e0034c756f72984f14aa2843a323403b2b26fc2634c15185101`.
- [Storage/lint reports and exported schema](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39228709486/artifacts/12175072676).

Physical-device/emulator checks and a local Android build remain unperformed or
blocked. The unchanged welcome screen does not yet expose vocabulary editing.

Final verified code revision: `623da1f3baa4a6ae034d5cab96c3c164a80f03fb`.
[Run 39229491461](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39229491461)
completed successfully after matching the actual web controller's search limits
(query max 255, part-of-speech max 100, default page size 20):

```text
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
BUILD SUCCESSFUL in 2m 43s
VocabularyRepositoryTest: 15 tests, 0 failures/errors/skipped
VocabularyTextTest:       5 tests, 0 failures/errors/skipped
STORAGE_TEST_TOTALS:     20 tests, 0 failures/errors/skipped
Room schema contract verified: app/schemas/com.danishbears.bulgariantrainer.data.TrainerDatabase/1.json
```

This includes actual Room/native-SQLite transaction, unique-key, foreign-key,
reopen durability, no-destructive-fallback and Flow-invalidation executions.
The intermediate run `39229370768` was cancelled when superseded by the final
code revision; no success is claimed for that cancelled run.

- [Latest Room debug APK ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39229491461/artifacts/12176357885):
  artifact `12176357885`, 9,141,200 bytes, retained until 2026-11-02;
  ZIP SHA-256 `e3f51c18c0ca9557e66a8c69806226dc576ae02b8c38204e3516894cc8c17709`.
- [Latest JUnit/lint reports and schema](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39229491461/artifacts/12176437766).

These digests describe artifact ZIPs, not separately downloaded APK bytes. Final
documentation-only changes do not alter the tested application or build workflow.
No further test/build rerun is needed for those documentation-only changes.

The sections below preserve the previous task's historical baseline evidence.

## Access and checkouts

| Capability | Observed result |
| --- | --- |
| GitHub integration | Authenticated login `Danishbears`; both repositories readable; metadata reports pull/push permission. Exact source fetched through GitHub tools. |
| Existing local checkouts | None initially under `/workspace`. The mounted `/workspace/.git` was empty/read-only. Both complete shallow checkouts were reconstructed through integration blob/tree/commit reads and Git SHA checks. No shell clone was attempted. |
| Shell network | Earlier `gh api user --jq '{login: .login}'` failed before reaching GitHub: `proxyconnect tcp ... socket: operation not permitted`. Cloud reports unrestricted/enforced HTTP policy, but shell sockets remain restricted. This is not evidence of missing GitHub authorization. No repeated shell repository-access retries were made in this audit. |
| Local Android build | Blocked: no Android SDK/command-line tools, no cached Gradle distribution, and Java runtime without `javac`. The debug-build command failed at Gradle download before compilation. |
| GitHub-hosted Android build | **Executed successfully** with full JDK 17, Android SDK 35 and Build Tools 35.0.0. Debug APK uploaded; lint passed. Unit-test task had NO-SOURCE. See the run below. |

Inspected source revisions:

```text
/workspace/bulgariantrainer
  branch: master
  commit: de0a3e883d51561123ca54b7d5dd2e53757baf0a
  root tree: 2aa0b62e59aa67acfe416341de99e28a39d9e605

/workspace/bulgariantrainer-android
  initial branch: main
  initial commit: d18ad8814b391c60b0e1ec2099a5c2251978b2c6
  initial root tree: d5830f0ddcfae219f5c8af0b01298c7b4a93453f
```

The target initially contained only README.md. There were no baseline Android
tests or build files to execute or repair. No existing applicationId or UI was
replaced. Reference `AGENTS.md` was read; no target/ancestor instructions existed.
The reference working tree remains clean.

## Toolchain inspection and preparation

Executed `java -version`: OpenJDK **21.0.12.1**, runtime and VM present.
`command -v javac`, `gradle`, `sdkmanager`, and `adb`: none on PATH.
Inspection of `/usr/lib/jvm/java-21-openjdk-amd64/bin` confirmed runtime tools
only, without javac. `/opt/android-sdk`, `/opt/android`, `/usr/local/android-sdk`,
and `/usr/lib/android-sdk` were absent. JAVA_HOME, ANDROID_HOME and ANDROID_SDK_ROOT
were unset. These checks do not establish a usable full Android toolchain.

Prepared project versions: Gradle 8.11.1, AGP 8.9.2, Kotlin/Compose compiler 2.1.20,
Compose BOM 2025.04.01, Activity Compose 1.10.1, SDK 35/Build Tools 35.0.0, bytecode
target 17, minimum SDK 26. Recommended build JVM is a full JDK 17; Gradle also
supports a full JDK 21. Versions are pinned without machine-specific paths.

Official wrapper scripts/JAR were retrieved through the GitHub integration from
`gradle/gradle` tag `v8.11.1`. Python hashlib verified the JAR's Git blob hash and
official release SHA-256:
`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
The wrapper pins the official distribution SHA-256
`f397b287023acdba1e9f6fc5ea72d22dd63669d59ed4a289a29b1a76eee151c6`.
Both checksum values came from the Gradle 8.11.1 GitHub release body.

No full JDK, Android SDK, AGP or Gradle distribution was installed locally:
shell download restrictions prevent completing that setup. Adding build files
and wrapper tooling does not establish that the Kotlin/Gradle configuration compiles.
README contains local Studio/setup commands. GitHub Actions has a separate JDK/SDK
setup workflow; its execution is reported separately below.

## Commands actually executed

Reference directory `/workspace/bulgariantrainer`:

| Command | Result |
| --- | --- |
| `git branch --show-current`; `git rev-parse HEAD`; `git rev-parse 'HEAD^{tree}'` | Exact branch/commit/tree listed above. Every fetched source blob independently matched its Git SHA before checkout. |
| `git status --porcelain`; `git diff --exit-code HEAD`; `git fsck --no-reflogs` | Exit 0, no changes or object errors. The reference is read-only for this task. |
| `node --test src/test/js/pronunciation.test.mjs` | Exit 0; Node v24.19.0 reported one passing file-level test. |
| `node --test --test-reporter=tap src/test/js/pronunciation.test.mjs` | Exit 0; same file-level result. |
| `node src/test/js/pronunciation.test.mjs` | Exit 0; all **9 named tests passed**, zero failed/skipped. This established case-level execution despite the test-runner reporting difference. |
| `timeout 20s env MAVEN_USER_HOME=/tmp/bulgariantrainer-maven sh ./mvnw -Dmaven.repo.local=/tmp/bulgariantrainer-m2 clean verify` | Exit **124**. Output: `Terminated` and `wget: Failed to fetch https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.16/apache-maven-3.9.16-bin.zip`. Maven bootstrap did not finish; **no JUnit/Spring/Flyway tests executed**. `sh` was used because upstream mvnw is not executable; upstream file mode was preserved. |
| Python csv/json inspection of `data/bulgarian_sentences_100.csv` | 100 records, 20 alternatives, difficulties 1/2. Source file unchanged. |

Android directory `/workspace/bulgariantrainer-android`:

```sh
env GRADLE_USER_HOME=/tmp/bulgariantrainer-gradle \
  ./gradlew --no-daemon testDebugUnitTest assembleDebug
```

Exit **1**, with:

```text
Downloading https://services.gradle.org/distributions/gradle-8.11.1-bin.zip
Exception in thread "main" java.net.SocketException: Operation not permitted
```

This failed in GradleWrapperMain before Gradle project evaluation, dependency
resolution, test discovery, Android resource compilation, Kotlin compilation,
or APK packaging. Neither requested task executed. **No local debug APK exists.**
The tool cache was outside the repository; no local.properties or SDK paths were
created. A second attempt with the same shell network failure would add no evidence.

Additional executed checks:

- `sh -n gradlew`: exit 0, wrapper shell syntax valid.
- Python `xml.etree.ElementTree.parse` on all four Android XML files: parsed.
  This is an XML syntax check, not Android resource/manifest validation by AGP.
- Python `hashlib`: official wrapper checksum and Git blob SHA verified.
- `git diff --check`: exit 0; staged additions are also checked before delivery.
- `git check-ignore local.properties app/build/outputs/apk/debug/app-debug.apk .gradle/caches .env signing.keystore`:
  all ignored. No credentials, SDK paths, APKs or build outputs are committed.

## GitHub Actions execution

`.github/workflows/android.yml` defines full JDK 17 and Android SDK setup, then:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
```

It uploads `app/build/outputs/apk/debug/app-debug.apk` only if that file exists.

Observed runs:

1. [Run 39225697599](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39225697599),
   commit `74776d35dc47c58f4b8c7a71d4280d1526b7a16b`: failed during
   `android-actions/setup-android@v3` because its default package list requested
   obsolete SDK package `tools`. The Gradle build step was skipped. The workflow
   was corrected to explicitly install only `platform-tools`, followed by pinned
   platform/build-tools packages; checkout and setup-java were updated to v5.
2. [Run 39226090667](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39226090667),
   commit `d07a29ed142f0fa7e011bd2324df325237c677c2`: **success**.
   JDK Temurin 17.0.20+1 and SDK setup succeeded. Logs show `compileDebugKotlin`,
   `processDebugResources`, `packageDebug`, `assembleDebug`, and `lintDebug`
   executing, then **BUILD SUCCESSFUL in 2m 26s**. `testDebugUnitTest` and its
   compilation tasks reported **NO-SOURCE**; there are no Android test sources
   in this foundation and no passing training tests are claimed.

[Download the debug APK artifact ZIP](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39226090667/artifacts/12173967650)
(GitHub sign-in may be required). Artifact name `bulgariantrainer-debug`,
ID `12173967650`, ZIP size 8,849,662 bytes, retention until 2026-11-02.
The artifact ZIP SHA-256 reported by upload-artifact and the artifacts API is
`0d298ad7c059f58c36a61214cf7d428bf1a5b6b77106b4c78fdeafc8a51b11e3`.
This is the ZIP digest, not a separately measured APK digest. The APK has not
been installed or launched on a device in this task.

The subsequent commit `5ece22545da9f7b365ff93d251dfbff967a02c4d` adds a separate
[pinned reference baseline workflow](.github/workflows/web-reference.yml), with
read-only checkout of web commit `de0a3e883d51561123ca54b7d5dd2e53757baf0a`
under `web-reference`, JDK 21, `sh ./mvnw --batch-mode clean verify`,
`node src/test/js/pronunciation.test.mjs`, and `git diff --exit-code`.
It changes no Android app source/build version. Its execution outcome is recorded
below from actual runner logs.

### Pinned web reference baseline: executed

[Run 39226473668](https://github.com/Danishbears/bulgariantrainer-android/actions/runs/39226473668)
checked out the immutable web commit above with read-only credentials and used
Temurin JDK **21.0.12+1**. The job completed successfully:

| Executed command | Actual result |
| --- | --- |
| `sh ./mvnw --batch-mode clean verify` | **BUILD SUCCESS**, 103 tests, zero failures/errors/skipped; total Maven time 36.992 s. Included vocabulary, imports, flashcards, typing, sentence, scheduling, statistics, application and migration suites. |
| `node src/test/js/pronunciation.test.mjs` | **9 tests passed**, zero failed. |
| `git diff --exit-code` | Success; tracked reference source unchanged after verification. |

Surefire reports were uploaded as `pinned-web-reference-test-reports` on that run.
These are genuine baseline executions on GitHub-hosted infrastructure. They do
not remove the local shell/JDK/SDK limitations above. No reference code was edited
or pushed, and suspected uncovered defects in WEB_PARITY are not validated by the
existing suite passing.

## Remaining evidence gaps

- New regression cases for suspected defects listed in WEB_PARITY (the existing
  103-test Java baseline, including migrations/concurrency/scheduling, did pass).
- Local Android toolchain installation and local build remain blocked; the cloud
  runner did successfully resolve dependencies, compile Kotlin/resources, lint and package an APK.
- Device/emulator startup, visual review, Cyrillic rendering, accessibility and airplane-mode checks.
- All future training features: intentionally not implemented by this task.

The Android build claim is based on executed runner logs and an uploaded artifact.
No Java integration test, device check, or training feature is claimed successful
on the basis of source inspection or configuration alone.
