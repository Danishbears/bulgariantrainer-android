# Personal signed APKs and updates

Application ID: **com.danishbears.bulgariantrainer** (unchanged).
Minimum Android: 8.0/API 26. The current candidate is **0.2.0, versionCode 2**,
incremented from 0.1.0/code 1 in `version.properties`. Room remains v7; changing
the application version does not recreate the database.

The [Personal signed release workflow](.github/workflows/release.yml) is ready,
but a configured workflow is not evidence of a signed release. No release key or
password has been generated for this repository. The GitHub integration cannot
inspect repository Secrets; signing is **incomplete/unverified until the owner
configures the values below and a real signed run passes**. Unsigned release
build checks and debug APKs do not establish release signing.

## Create one key on a trusted computer

Use a full JDK's `keytool`. Create the key **once**, outside the checkout. For
Linux/macOS, run locally (these commands were documented, not executed here):

```sh
umask 077
mkdir -p "$HOME/.android-signing"
keytool -genkeypair -storetype JKS \
  -keystore "$HOME/.android-signing/bulgarian-trainer-release.jks" \
  -alias bulgariantrainer -keyalg RSA -keysize 3072 \
  -sigalg SHA256withRSA -validity 10000
```

Answer the identity questions and interactive password prompts. Use strong unique
passwords stored in your password manager; retain both the keystore password and
the key password (they may be the same if you deliberately choose that). Do not
put passwords in command arguments, shell history, Git, screenshots or chat.
On Windows, use the same `keytool` options with a private folder outside the repo
and PowerShell line continuation, or put the command on one line.

Inspect the **public** certificate fingerprint; the password prompt is interactive:

```sh
keytool -list -v -keystore "$HOME/.android-signing/bulgarian-trainer-release.jks" -alias bulgariantrainer
```

Keep its `SHA256` certificate fingerprint. This is not the SHA-256 of the JKS
file or the APK. The workflow compares the final APK certificate with this value
to catch use of a different key. Certificate fingerprints are public information.

Before distributing the first release, back up the JKS in at least two separate
encrypted locations, including one offline copy. Keep passwords, alias,
applicationId and certificate fingerprint in a password manager with recovery
arrangements. Test that a backup opens with `keytool -list` and has the same
fingerprint. GitHub Secrets are a CI delivery mechanism, not your only key backup.
Loss of the private key prevents ordinary updates of installed personal APKs.
Do not generate a replacement key for each build or cloud runner.

## Exact GitHub configuration

Open this repository → **Settings → Secrets and variables → Actions → Secrets →
New repository secret**. Configure exactly these four repository Secrets:

| Secret | Value |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | Base64 of the complete persistent JKS file |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Existing alias, e.g. the alias chosen above |
| `ANDROID_KEY_PASSWORD` | Password for that alias's private key |

Under the same page's **Variables** tab add this required repository variable:

| Variable | Value |
| --- | --- |
| `ANDROID_SIGNING_CERT_SHA256` | Public certificate SHA-256 fingerprint from `keytool -list -v`; hex with or without colons |

Do not type secret values into workflow YAML. Restrict repository write access:
a person who can edit a secret-using workflow can attempt to access its secrets.
Only run the signing workflow on a branch whose code/workflow you trust. It has
no pull-request trigger, and its token needs only `contents: read`.

To prepare the base64 value without printing the private key to a terminal,
on your trusted Linux/macOS computer:

```sh
umask 077
python3 - <<'PY'
import base64
from pathlib import Path
folder = Path.home() / '.android-signing'
(folder / 'bulgarian-keystore.base64').write_bytes(
    base64.b64encode((folder / 'bulgarian-trainer-release.jks').read_bytes()))
PY
```

Paste the private base64 file's contents into the Secret UI using your local
editor, or, with an already authenticated GitHub CLI, run:

```sh
gh secret set ANDROID_KEYSTORE_BASE64 --repo Danishbears/bulgariantrainer-android < "$HOME/.android-signing/bulgarian-keystore.base64"
gh secret set ANDROID_KEYSTORE_PASSWORD --repo Danishbears/bulgariantrainer-android
gh secret set ANDROID_KEY_ALIAS --repo Danishbears/bulgariantrainer-android
gh secret set ANDROID_KEY_PASSWORD --repo Danishbears/bulgariantrainer-android
gh variable set ANDROID_SIGNING_CERT_SHA256 --repo Danishbears/bulgariantrainer-android
```

The last four commands prompt for their values. Clear the clipboard and remove
the temporary base64 file after configuration; base64 is encoding, not encryption.
Keep the original JKS and encrypted backups. Never commit either representation.

## Run, diagnose and download

1. Merge the reviewed Android project/workflows into the actual default branch
   `main` when ready. Until the workflow exists on `main`, GitHub's **Run workflow**
   button is unavailable; ordinary PR CI still builds the debug APK.
2. For **each new distributed update**, increment `VERSION_CODE` in
   `version.properties` above every code already installed/distributed. Update
   `VERSION_NAME`, commit and push. Do not change applicationId, signing key or
   certificate variable. Rebuilding one revision intentionally keeps its version;
   GitHub run numbers are not Android version codes.
3. Open https://github.com/Danishbears/bulgariantrainer-android/actions →
   **Personal signed release → Run workflow**, select the reviewed branch and run.
   A push changing `version.properties` on `main` also runs it automatically.
4. Check the exact branch/SHA and wait for a successful **release** job. Missing
   configuration fails the first step with **Release signing INCOMPLETE** and
   the missing names, without printing values. Compilation, tests, lint or a
   fingerprint mismatch also prevent uploading the signed APK. See the first
   failed step and download `personal-release-test-reports` if present.
5. Download **bulgariantrainer-signed-release-N** from the successful run's
   **Artifacts**. Sign in to GitHub if needed, extract the ZIP, and use
   **app-release.apk**, not the ZIP. `release-verification.txt` records the actual
   APK SHA-256, identity/version and public signing certificate digest. APKs
   expire after 30 days; keep your trusted downloaded releases privately.

The workflow sets up JDK 17/SDK 35/build-tools 35.0.0, runs pinned parity checks,
Python contract tests, JVM tests and release lint before decoding the key. Signing
uses environment values, a mode-0600 runner-temporary JKS, no Gradle daemon or
configuration cache, and no Gradle cache upload. It verifies APK ZIP integrity,
manifest identity/version/minSdk/debuggability/permissions and the real APK
signature and pinned certificate before artifact upload. The key is removed
on step exit and in an `always()` cleanup step. Artifacts contain only the APK
and public version/verification metadata; test reports exclude credentials.
Do not enable shell tracing, Gradle debug logging, signing reports or job debug
logging for signing runs.

Without signing environment values, local `assembleRelease` deliberately builds
an **unsigned** APK for validation. It is not installable and is never offered
as the personal release artifact. A partial environment fails rather than using
debug signing. Locally, the four Gradle environment names are
`ANDROID_RELEASE_KEYSTORE` (private file path), `ANDROID_KEYSTORE_PASSWORD`,
`ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`; keep values outside Git. CI derives
the path internally from the base64 Secret. The fingerprint variable is also
required by `scripts/verify_apk.py --signing release`.

## Install and preserve data when updating

On Android 8.0+, transfer/extract the APK, open it in Files, allow **Install unknown
apps** for that file manager when asked, then install. Revoke that permission
afterward if desired. Or enable USB debugging, authorize your computer, run
`adb devices`, then `adb install -r app-release.apk`.

For an update, keep the same applicationId and certificate and use a greater
versionCode. Install over the existing app **without uninstalling or clearing
storage**. Room migrations preserve content/history; this candidate leaves the
v7 schema unchanged. Make a **full JSON backup** through Главная → Полный бэкап
и восстановление before updating. CSV content export does not preserve progress.
Verify counts, history, pending sessions and settings afterward using
[PERSONAL_USE_CHECKLIST.md](PERSONAL_USE_CHECKLIST.md). A real signed APK-to-APK
update has not been verified until it is tested on a device.

Debug signing keys may differ between cloud runners, so successive debug builds
are **not guaranteed to update each other**. Debug and personal release signatures
also differ. On `INSTALL_FAILED_UPDATE_INCOMPATIBLE`, first verify the certificate
and artifact; do not erase valuable local data to bypass the error. When moving
from debug to release, export and retain a verified full JSON backup before
uninstalling the differently signed build, install release, then explicitly
restore it. Uninstalling deletes the app-private database. If the old build lacks
full backup, preserve its data before attempting that transition.

No Play Store publication, release account, automatic sync, key rotation or
signing credentials are created by this work.
