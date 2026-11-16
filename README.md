# Anchor

A private Android companion for daily self-observation: mood, medication,
a journal and doctor visits. No ads, analytics, sign-up or mandatory cloud.

> The app does not diagnose, prescribe treatment or replace a doctor.
> In an emergency, contact your local emergency service.

**Status:** `1.0.0-beta` · Android 8.0+ · Russian and English interface.
The repository is prepared for private review by the author; public publication
and the choice of license are separate decisions.

## Features

| Section | What it contains |
|---|---|
| Today | Quick mood check-in, the day's medication doses, journal, next appointment and streak days. Blocks can be hidden and reordered. |
| Mood | Calendar, charts, weekly summaries; sleep, energy, anxiety, emotions, symptoms and notes. |
| Medication | Schedules by weekday, interval or as needed; several times, stock, history, reminders with actions. |
| Journal | Entries with mood, tags, pinning, search and autosave. |
| Doctor visits | Appointments, notes, reminders, completion marking and adding to the system calendar. |
| Doctor report | PDF for a chosen period; journal entries are optional. |
| Backups | Encrypted export/import, auto-backup to a chosen folder, optional WebDAV. |

"More" offers the profile name, reminder settings, themes and system colors,
left-handed mode, language, home and bottom menu setup, app lock with device
biometrics/PIN and screenshot protection. The same screen holds demo data,
data deletion with a local restore point, author information and the bug report.

The "Help" / "If things are hard" section and the built-in hotline numbers have been removed.
Legacy crisis-plan fields are kept only for database and backup compatibility.

## Installation

Use the **release APK** attached to the private
[prerelease](https://github.com/SavaStone/Anchor/releases/tag/v1.0.0-beta).
The file from the current local build: `app/build/outputs/apk/release/app-release.apk`.
It is about **3.70 MB**. The debug APK is much larger and intended for development.

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

An update preserves data only with a compatible signature. Release and debug builds
are signed with different keys; do not uninstall the app to swap one for the other
without a verified backup.

## Data and backups

- Primary data stays on the device; Android cloud backup is disabled.
- Encrypted backups use PBKDF2 + AES-GCM; the format is `OPORA1`.
- Auto-backup requires choosing a folder through the system dialog and setting a password.
  The 7 most recent files are kept, at most once an hour; an empty database is not copied.
- Files in the chosen folder may survive app removal. After reinstalling, folder access
  and settings must be granted again; the password must be remembered.
- WebDAV is optional: folder address, login and server access password.
  **The backup encryption password is set separately from the server password.**
  Auto-sync runs every 6 hours when the network is available.
- An unencrypted JSON export is also available. It is not password-protected.
- PDF, exports and bug reports are passed through an app chosen by the user.
  There is no automatic bug report submission.

Details and limitations: [PRIVACY.md](PRIVACY.md).

## Beta limitations

- Reminders need system permissions; Android and device-manufacturer restrictions
  may affect notification timeliness.
- App lock requires configured device biometrics or a screen lock.
- Removing personal data from technical logs is best effort:
  review the text and attachments before sending.
- Real WebDAV operation should be tested with your own server; the presence of the
  feature does not mean compatibility with every provider has been verified.
- Do not use the app as the only source of medical data
  or the only reminder for critical treatment.

## Building from source

You need JDK 17, Android SDK Platform 35 and access to dependency repositories.
The project can be opened in Android Studio. The local SDK path is set in
`local.properties` (`sdk.dir=...`); that file is not committed.

```sh
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest :app:lintDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`.
On Windows use `gradlew.bat`.

Optimized packaging:

```sh
./gradlew :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest :app:lintRelease
```

The official signing key is not part of the repository. Without its configuration
an APK signed by the author cannot be produced. For your own signed build, use your
own key and a local `keystore.properties` with the `storeFile`, `storePassword`,
`keyAlias` and `keyPassword` fields. Do not publish the values, the configuration
file or the key. A build with your own key will not update an installed official build.

Release outputs: APK — `app/build/outputs/apk/release/`, AAB —
`app/build/outputs/bundle/release/`, R8 mapping — `app/build/outputs/mapping/release/`.

Checks on the current build: **154 JVM tests**, **9 targeted Android bug-report tests**;
lintRelease — **0 errors, 24 warnings**. This is not a full re-run of all Android tests.
Details: [CHANGELOG.md](CHANGELOG.md).

## Project structure

App code lives in `app/src/main/java/app/opora/`:

```text
ui/             screens, navigation, components and theme
data/           Room, settings, backups and WebDAV
domain/         schedules and statistics
notifications/  notifications and alarms
work/           background jobs
reports/        PDF report
diagnostics/    technical report and local crash logs
security/       cryptography and protection
```

Texts: `app/src/main/res/values/strings*.xml` and `values-en/strings*.xml`.
Tests: `app/src/test/` and `app/src/androidTest/`.
Stack: Kotlin, Jetpack Compose, Material 3, Room, DataStore, WorkManager.
Room v11; minSdk 26, compile/targetSdk 35; Material 3 in the current BOM — 1.3.2.

## Feedback and author

- YouTube: [@SavaStone](https://www.youtube.com/@SavaStone)
- GitHub: [SavaStone](https://github.com/SavaStone)
- Bug reports: **savastone32@proton.me** or "More → Report a problem".
- Vulnerabilities: [SECURITY.md](SECURITY.md).
- Changes and safe verification: [CONTRIBUTING.md](CONTRIBUTING.md).

Do not attach personal journals, medical records, passwords,
keys or raw logs to reports.

## License

A license for distributing the source code has not been chosen yet.
Repository access does not grant permission to redistribute.
Before public publication the author must define the terms of use.
