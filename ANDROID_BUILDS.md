# Android builds and APK installation

The **Android baseline** workflow uses Temurin JDK 17, the committed Gradle
8.11.1 wrapper, Android Platform 35 and Build Tools 35.0.0. These match AGP
8.9.2 and the project's Java/Kotlin 17 targets. The wrapper JAR and scripts are
committed; `gradlew` has Git mode `100755` for Linux execution. No system Gradle
installation or emulator is required for JVM/Robolectric tests.

It runs on pull requests, pushes to the actual default branch (`main`), and
manual `workflow_dispatch` runs. Its only token permission is `contents: read`;
checkout does not persist credentials. It runs `testDebugUnitTest`,
`assembleDebug`, debug/release lint, unsigned `assembleRelease`, final APK
identity/version/permission/signature checks and the Room schema contract check. Test failures
fail the job. Reports are uploaded even after failures when files exist; an APK
is uploaded if assembly produced it, even if a later lint/schema check fails.
An APK's presence alone does not mean the full workflow passed.

## 1. Open GitHub Actions

Open https://github.com/Danishbears/bulgariantrainer-android/actions and select
**Android baseline** in the left sidebar. Enable Actions if GitHub asks and you
have repository permission.

## 2. Run the workflow

For automatic runs, open/update a pull request or push a commit to `main`.
For a manual run, select **Run workflow**, choose the branch containing the
Android project, and click **Run workflow**. Manual dispatch is available only
after this workflow file exists on the default branch. At the time of this
change, the Android project lives on `codex/android-foundation-parity` and
`main` contains only a README; merge the Android branch through review to make
manual dispatch available. A pull request can exercise this workflow before
that merge.

Open the new run and wait for its final result. Check its branch and commit SHA
to ensure it built the intended revision. A configured workflow or an older
successful run does not prove a new revision passes.

## 3. Read build failures

Open the run, then the **debug** job. Expand the first failing step and read the
Gradle error above `BUILD FAILED`, including `What went wrong` and the stack
trace. The separate test, assembly and lint steps identify the failing phase.
Setup failures can prevent Gradle from producing any reports.

Under the run summary's **Artifacts**, download
`android-storage-test-reports` and extract the ZIP. Open the unit-test HTML
`index.html` from the `reports/tests/testDebugUnitTest` directory; JUnit XML
under `test-results/testDebugUnitTest` contains individual failures and traces.
Open `lint-results-debug.html` and `lint-results-release.html` for lint findings. The archive also includes
the Room schema. A missing report means that phase may not have run; consult
the logs. Reports and APKs expire after 14 days.

## 4. Download and extract the APK

Sign in to GitHub, open the completed run and scroll to **Artifacts**.
Click **bulgariantrainer-debug** to download a ZIP, then extract it using your
file manager's **Extract all** action (or `unzip bulgariantrainer-debug.zip`).
Inside is `app-debug.apk`. Install the extracted APK, not the ZIP.
If no artifact exists, inspect the assembly/setup logs. If it has expired,
run a new build.

## 5. Install on Android

Use an Android 8.0/API 26 or newer device.

- On the phone: transfer `app-debug.apk` to the device, open it from Files,
  allow **Install unknown apps** for that file manager when prompted, and tap
  **Install**. You can revoke that permission afterward.
- With Android SDK platform-tools: enable Developer options and USB debugging,
  connect the phone, accept its authorization prompt, check `adb devices`,
  then run `adb install -r app-debug.apk` from the extracted directory.

Debug APKs use automatically generated debug signing keys. **Debug signing keys
may differ between cloud runners, so successive debug builds are not guaranteed
to update each other**, or a locally built APK. A signature mismatch may appear
as `INSTALL_FAILED_UPDATE_INCOMPATIBLE` or “App not installed.” To install the new
build, uninstall the old app first; this deletes its app-private vocabulary and
other local data. Preserve any needed data before uninstalling. The application
ID is `com.danishbears.bulgariantrainer`.

The separate **Personal signed release** workflow supports a persistent personal
key, pinned public certificate and signed APK artifacts after owner configuration.
See [RELEASE_SIGNING.md](RELEASE_SIGNING.md) for the exact four Secrets, required
public variable, secure key creation/backup, versionCode increments and updates.
The unsigned release APK in baseline CI is validation only and is not uploaded
for installation. No successful signed release is implied by baseline CI.

APKs, AABs, keystores, signing
credentials, SDK installations and machine-specific `local.properties` must
stay out of Git. Downloaded debug APKs are for development/testing.
