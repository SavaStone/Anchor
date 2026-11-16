# Changelog

## 1.0.0-beta — current Android application identity

- Android application ID: `io.github.savastone.anchor` (previously `app.opora`).
- This installs alongside the old app, rather than updating it. Transfer records
  with an encrypted backup and verify the import before removing the old app.
  Permissions, backup-folder access and configuration are not inherited automatically.
- Kotlin namespace, Room schema and OPORA1 backup format are unchanged.
- Includes the MIT license and six application-identity regression tests.

### Build verification
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
