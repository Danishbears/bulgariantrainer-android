# Baseline and build evidence

## Access and checkouts

| Capability | Observed result |
| --- | --- |
| GitHub integration | Authenticated login `Danishbears`; both repositories readable; metadata reports pull/push permission. Exact source fetched through GitHub tools. |
| Existing local checkouts | None initially under `/workspace`. The mounted `/workspace/.git` was empty/read-only. Both complete shallow checkouts were reconstructed through integration blob/tree/commit reads and Git SHA checks. No shell clone was attempted. |
| Shell network | Earlier `gh api user --jq '{login: .login}'` failed before reaching GitHub: `proxyconnect tcp ... socket: operation not permitted`. Cloud reports unrestricted/enforced HTTP policy, but shell sockets remain restricted. This is not evidence of missing GitHub authorization. No repeated shell repository-access retries were made in this audit. |
| Local Android build | Blocked: no Android SDK/command-line tools, no cached Gradle distribution, and Java runtime without `javac`. The debug-build command failed at Gradle download before compilation. |

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
There are no Android unit test sources in this foundation; a NO-SOURCE test task
must not be reported as passing business-rule tests. Workflow execution status
will be recorded after the integration accepts the target branch changes and
GitHub reports an actual run. Configuration alone is not a passed check.

## Remaining evidence gaps

- Full web Java baseline, including database migrations, concurrency and scheduling.
- Successful Android dependency resolution, Kotlin/resource compilation, lint and APK packaging.
- Device/emulator startup, visual review, Cyrillic rendering, accessibility and airplane-mode checks.
- All future training features: intentionally not implemented by this task.

No Java integration test, Android build, device check, or training feature is
claimed successful on the basis of source inspection or configuration alone.
