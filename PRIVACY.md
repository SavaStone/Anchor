# Data and privacy

A description of the current beta, not a promise of absolute security or a legal
assessment of compliance with any particular country's requirements.

## Local storage

Mood, medication, journal, appointments and settings are stored on the device.
There is no sign-up, advertising SDK or analytics. Android cloud backup
is disabled (`allowBackup=false`).

App lock uses device biometrics/credentials.
Screenshot protection can be enabled separately. The app has no encryption of
its own for the local database: it is stored in a plain Room database inside the
app's isolated storage. App lock restricts access through the interface
but does not encrypt the database. PBKDF2 + AES-GCM encryption applies to
encrypted backup files, not to the whole on-device database.

## When data can leave the app

- The user exports a backup file, unencrypted JSON or a PDF.
- The user configures their own WebDAV server and starts an upload or enables
  auto-sync. The chosen server's own terms also apply in that case.
- The user chooses a folder for auto-backup: it may belong to local
  storage or a third-party file provider.
- The user sends a technical report through a chosen app.
- The user opens the author's external links: they open in another
  app with its own data handling rules.

PDFs and JSON may contain sensitive data. When shared through other
apps, the recipients and the respective services get access to the files.

## Backups

Encrypted backups use PBKDF2 and AES-GCM. Backup passwords are not replaced by
the WebDAV server login password. The auto-backup password is stored on the device
in a protected Keystore envelope; losing the device key can make
the saved password setting unavailable.

Remember the password separately. After reinstalling you may need to
pick the file manually and grant folder access again. Backups and previously sent
files are not deleted automatically when records are deleted inside the app.
The local restore point after data deletion is not an independent
external backup.

## Technical reports

The report contains technical details about the build, the device/settings and,
when present, a sanitized copy of the latest crash. The recipient pre-filled
in the mail app is `savastone32@proton.me`.

There is no automatic submission: a person reviews the content and sends it.
Other sharing apps may ignore the recipient field.
URLs, emails and typical secrets are removed from exported logs by patterns,
but this does not guarantee removal of arbitrary personal data. Raw crash
logs stay local; review the text and attachments before sending.

## Contact

Privacy questions: `savastone32@proton.me`.
Do not send medical data or passwords to explain an ordinary error.
