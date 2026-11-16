# Working on the project

The repository is in private review by the author.
The project is licensed under the [MIT License](LICENSE).
The process for external contributions is not defined yet.

## Before making changes

- Do not add mandatory sign-up, a server, analytics or ads.
- Do not change dependencies, signing or the data format without a separate decision.
- Preserve import of old `OPORA1` backups and Room migrations (current schema v11).
- RU/EN texts must live in resources; do not hardcode Russian strings in the UI.
- Consider themes, large font and tablet navigation.
- Material 3 is currently 1.3.2: do not use unavailable Expressive APIs.

## Verification

You need JDK 17 and Android SDK 35. Build commands and paths are in the README.
For ordinary changes start with targeted JVM tests; before packaging use:

```sh
./gradlew :app:assembleRelease :app:bundleRelease :app:testDebugUnitTest :app:lintRelease
```

A signed official build requires the author's local key, which is
not in the repository. For your own development, use a debug build.

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
