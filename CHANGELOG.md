# Changelog

## 1.0.1-beta

- Home mood selection opens the editor with the selected level; autosave updates
  the same entry instead of creating duplicates while editing.
- Removed the repeated home mood question. Mood statistics show the check-in
  count once, with layout adjustments for narrow screens and large text.
- Replaced the launcher artwork with a small centered heart, including the
  monochrome icon.
- Clarified that records stay local by default, while user-configured backups,
  WebDAV and exports can transfer data.
- Added baseline Android CI, clearer build instructions and dependency notices.
- `versionCode=4`; application ID, Room schema and OPORA1 backup format unchanged.
  Updates from the new-ID code-3 release use the same signing key. No uninstall
  or data migration is required for that update.

### Verification

- Signed release APK: **3,747,943 bytes**, R8 + resource shrinking,
  non-debuggable; version `1.0.1-beta`, code `4`.
- APK SHA-256:
  `3a2cfe0934989f1090b716afc9bf589f3eaebc492712f335d62052175ffa7b94`.
- Signing certificate SHA-256 (unchanged):
  `84b3d3fc2e3ce30740441d80c52dfd590d6bb8bc1a965df8edce0112fcb0c7c7`.
  APK Signature Scheme v2/v3 and ZIP alignment verified.
- `:app:testDebugUnitTest :app:lintRelease :app:assembleDebug :app:assembleRelease`
  passed: **160 JVM tests**, no failures/errors/skips; lint **0 errors,
  25 warnings and 14 informational findings**. Lint findings concern target SDK,
  dependency updates, obsolete SDK checks, Compose state boxing, one unused
  resource and Kotlin extension suggestions; they remain open.
- **14 focused Android tests** passed on an isolated Android 15 emulator:
  mood-editor repeat saves, home mood selection/question removal and localized
  statistics layout. No failures/errors/skips. The original installed app was
  not replaced or uninstalled.
- Dependency notices cover 144 resolved release-runtime modules and are bundled
  in the APK; bundled bytes were checked against the source file.
- Gitleaks 8.30.1 found no secrets in reachable history or the intended working
  source snapshot. This is a pattern-based scan, not a guarantee of absence.
- The maintainer reports successful manual testing of the release.
  Automated on-device verification covers the 14 focused Android tests listed
  above, not the full Android test suite.
- No independent security audit or complete dependency-vulnerability assessment
  was performed.

## 1.0.0-beta — current Android application identity

- Android application ID: `io.github.savastone.anchor` (previously `app.opora`).
- This installs alongside the old app, rather than updating it. Transfer records
  with an encrypted backup and verify the import before removing the old app.
  Permissions, backup-folder access and configuration are not inherited automatically.
- Kotlin namespace, Room schema and OPORA1 backup format are unchanged.
- Includes the MIT license and six application-identity regression tests.

### Build verification
- Historical initial build evidence follows. The assets on `v1.0.0-beta` were
  later replaced by a maintenance build from `ab6ca6fcd3b735744a3379de751456eef04e4749`;
  that tag still points to the earlier source snapshot. The replacement APK was
  3,735,579 bytes, SHA-256
  `f937439f2675fb4b1ccf4431d21facbb049e369a3b5a7b20bdd44fa09c888e1b`.
  Use `v1.0.1-beta` for a release whose tag and APK source match.
- `versionCode=3`, `versionName=1.0.0-beta`.
- Release APK: 3,700,438 bytes, R8 + shrinkResources, non-debuggable.
- APK SHA-256:
  `017ade55871c70cf992dc2f7fd261272fbad8b7851a1eb4a65c367e05dd05276`.
- 160 JVM tests passed; lintRelease — 0 errors, 24 warnings.
- Signed release updated the existing new-ID installation in place and launched
  on the test emulator. The old `app.opora` APK and timestamps remained unchanged.
- Compiled manifest verified: application classes retain the original namespace,
  while provider authorities use the new application ID.
- Backup import and the full Android test suite were not rerun for this change.

## Earlier private snapshot — legacy `app.opora` package

### Features
- Mood, medication, journal, doctor visits and PDF report.
- Encrypted backups, auto-backup to a chosen folder and optional WebDAV.
- RU/EN, themes, home/bottom menu setup, app lock and screenshot protection.

### Recent changes
- Removed "Help" / "If things are hard" and the built-in help phone numbers.
- Added author information, YouTube/GitHub links and a repository link.
- Updated the adaptive and monochrome app icon.
- Bug reports now have the recipient `savastone32@proton.me`, handle share-menu
  launch failures, and sanitize exported logs by common patterns.
- Old data compatibility is preserved; raw logs are not modified
  when preparing a sanitized attachment.

### Build verification
- `versionCode=2`, `versionName=1.0.0-beta`.
- Release APK: 3,700,410 bytes, R8 + shrinkResources, non-debuggable.
- APK SHA-256:
  `0e5c2fe9ac3ea3db0c0a4dd1243101d76011a010ff5937884948c3e92d6461ab`.
- 154 JVM tests passed; lintRelease — 0 errors, 24 warnings.
- Direct targeted run of the Android bug-report tests: 9 tests passed.
- Manually verified the author screen, repository link, light/dark theme and
  large font. The full Android suite was not re-run.

This is a source snapshot of existing local development, not the history of all
prior commits. Signing keys, local plans, agent tooling,
personal artifacts and old packages are not intended for publication.
