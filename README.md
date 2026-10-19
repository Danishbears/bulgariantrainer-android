# Bulgarian Trainer for Android

Foundation for a standalone, offline Android application for a Russian-speaking
learner of Bulgarian. Kotlin, Jetpack Compose, Material 3. This milestone contains
only a welcome screen and build configuration; training, imports, persistence,
scheduling, statistics, and pronunciation are not implemented.

The initial repository at `d18ad8814b391c60b0e1ec2099a5c2251978b2c6` contained
only a README. There was no Android configuration, applicationId, or UI to preserve,
and no corrupted build files to restore. The new applicationId and namespace are
`com.danishbears.bulgariantrainer`. Preserve this identity in subsequent work.

See [WEB_PARITY.md](WEB_PARITY.md) for the source-based reference audit and proposed
implementation stages, and [BUILD_REPORT.md](BUILD_REPORT.md) for executed checks
and environment limitations. The web reference repository remains unchanged.

## Open in Android Studio

1. Download this branch as ZIP and extract it, or check it out with Git.
2. Open the directory containing `settings.gradle.kts` in Android Studio
   Meerkat 2024.3.1 or newer with support for AGP 8.9.
3. Use a full JDK 17 for the Gradle JVM. A full JDK 21 is also compatible with
   this Gradle/AGP combination; the cloud image currently has only a Java runtime.
4. Install Android SDK Platform 35 and Build Tools 35.0.0 using SDK Manager.
5. Let Gradle sync. The first build needs internet for tools and dependencies.
6. Run on a physical Android device with Android 8.0/API 26 or newer and USB
   debugging enabled. An emulator is optional.

Android Studio may generate `local.properties` with your SDK location. It is
ignored and must stay local. No machine-specific SDK or JDK paths are configured
in the project.

## Reproducible tool versions

| Component | Version |
| --- | --- |
| Gradle wrapper | 8.11.1 |
| Android Gradle Plugin | 8.9.2 |
| Kotlin and Compose compiler plugin | 2.1.20 |
| Compose BOM | 2025.04.01 |
| Activity Compose | 1.10.1 |
| Compile / target SDK | 35 / 35 |
| Build Tools | 35.0.0 |
| Minimum SDK | 26 |
| Java / Kotlin bytecode target | 17 |

No version was upgraded from an existing Android project: this is its first
configuration. Gradle memory is limited to 1536 MiB with two workers to reduce
load on smaller machines; Android Studio itself still needs additional memory.

## Build and install

With a full JDK and Android command-line tools installed, set `JAVA_HOME` and
`ANDROID_HOME` to your own locations, then install SDK packages:

```sh
sdkmanager 'platforms;android-35' 'build-tools;35.0.0'
```

Accept the Android SDK licenses if prompted. From the repository root:

```sh
./gradlew --no-daemon testDebugUnitTest lintDebug assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows, use `.\gradlew.bat --no-daemon testDebugUnitTest lintDebug assembleDebug`.
No Android unit tests existed at baseline, and no training behavior tests are
introduced by this foundation. A `NO-SOURCE` test task is not evidence of passing
training tests. Device startup and visual behavior require an actual device check.

The [Android baseline workflow](.github/workflows/android.yml) prepares a JDK and
SDK on GitHub Actions, runs these checks, and uploads the debug APK as
`bulgariantrainer-debug`. A configured workflow is not a successful build; consult
its actual run and [BUILD_REPORT.md](BUILD_REPORT.md).

## Offline scope

The app has no server, WebView, sign-in, network client, or INTERNET permission.
The welcome screen uses only packaged resources. Future learning data will live
on the device; installation does not import sample or personal vocabulary.
Future offline pronunciation must use an installed Bulgarian voice that does
not require a network connection; availability cannot be assumed on every phone.

## Wrapper provenance

`gradlew`, `gradlew.bat`, and `gradle/wrapper/gradle-wrapper.jar` come from
[`gradle/gradle` tag `v8.11.1`](https://github.com/gradle/gradle/tree/v8.11.1).
The JAR's SHA-256 was checked against the official
[Gradle 8.11.1 release checksums](https://github.com/gradle/gradle-distributions/releases/tag/v8.11.1):
`2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046`.
The distribution SHA-256 is pinned in `gradle-wrapper.properties`.
The wrapper JAR is source-controlled build tooling; APKs, build directories,
credentials, keystores, and local SDK configuration are ignored.
