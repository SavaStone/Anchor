# Working on the project

This is an author-maintained beta.
The project is licensed under the [MIT License](LICENSE).
Before starting a large change, open an issue to discuss the scope.
External contributions may be reviewed as time permits; there is no promised response time.

## Before making changes

- Do not add mandatory sign-up, a server, analytics or ads.
- Do not change dependencies, signing or the data format without a separate decision.
- Preserve import of old `OPORA1` backups and Room migrations (current schema v11).
- RU/EN texts must live in resources; do not hardcode Russian strings in the UI.
- Consider themes, large font and tablet navigation.
- Material 3 is currently 1.3.2: do not use unavailable Expressive APIs.

## Verification

You need JDK 17 and Android SDK 35. Build commands and paths are in the README.
The checked-in Gradle wrapper downloads the required Gradle version; do not
replace it with an arbitrary system Gradle installation.

### Command-line setup

Install the [Android SDK command-line tools](https://developer.android.com/tools)
and set `ANDROID_HOME` to your SDK directory (for example,
`$HOME/Library/Android/sdk` on macOS or `$HOME/Android/Sdk` on Linux). Add
`$ANDROID_HOME/cmdline-tools/latest/bin` and `$ANDROID_HOME/platform-tools` to
`PATH`, then accept the Android SDK licenses and install:

```sh
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
java -version  # use JDK 17
./gradlew :app:assembleDebug
```

Android Studio may create an ignored `local.properties` pointing to the SDK
instead. Do not commit machine-specific SDK paths.

### Checks

For ordinary changes start with targeted JVM tests; before packaging use:

```sh
./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleRelease
```

A signed official build requires the author's local key, which is
not in the repository. Without a local signing configuration, `assembleRelease`
produces an unsigned APK: it is not an installable official update.
For your own development, use a debug build. If you need an Android App Bundle,
run `./gradlew :app:bundleRelease` separately.

The Android CI workflow runs JVM tests, lint and debug/unsigned-release builds.
It does not hold the official signing key or run instrumented device tests.

Do not run `connectedDebugAndroidTest` on a device with valuable data:
Gradle environment cleanup may remove the installed packages under test.
Use a separate test emulator and synthetic data.
Do not replace release with debug by uninstalling the app without a backup.

## What not to commit

Signing keys and configuration, `local.properties`, WebDAV credentials,
`.env` files, APKs/AABs and build outputs, databases, backups, PDFs with personal data,
logs, screenshots of real records and local agent sessions.

Describe behavior, verification and known limitations in changes.
Do not present planned features as implemented.

## Dependency notices

Release runtime dependency attribution is recorded in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md), with license texts bundled in
`app/src/main/assets/THIRD_PARTY_NOTICES.txt`. Refresh the inventory and applicable
copyright/notice text when changing dependencies. The project MIT license does
not replace dependency licenses.
