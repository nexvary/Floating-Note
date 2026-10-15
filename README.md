# NEXVARY Floating Note

Offline-first Android floating notes, optimized for Arabic and English.

## Version 0.3.2 (development — dev/foundation)

- A draggable circular bubble with quick actions: open, create, copy, paste, stop.
- Up to four simultaneous floating note editors, independently autosaved and minimized.
- Resizable editors, five color themes, saved positions, pinned notes and search.
- Inexact reminders; exported reminders reschedule on restore when still in the future.
- User-selected **password-encrypted backup** (AES-256-GCM with PBKDF2-HMAC-SHA256,
  random salt and nonce, authenticated header) in .nfn format.
- Legacy plain JSON export/import remains optional. **JSON is not encrypted**.
- Transactional merge import now skips exact duplicate notes.
- All data stored locally; no INTERNET or accessibility permissions.

## Install and verification

Use the dev/foundation [Android CI workflow](https://github.com/nexvary/Floating-Note/actions/workflows/android.yml).
Download the debug APK artifact of a successful run. It is not a signed production release.
Build with JDK 17, Gradle 8.13, Android SDK 36:

    gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

On the phone grant "Display over other apps", then use the Enable floating note control.

## Security and limits

**Remember your encrypted backup password:** there is no password recovery.
Wrong passwords or changed ciphertext are rejected before importing any notes.
Encryption uses a passphrase derived key and authenticated AES-GCM; do not share
passwords through GitHub. Plain JSON backups are not encrypted.

Copy/paste act on note text and Android ClipboardManager; the app cannot inject
text into another app's input field. Reminders are inexact and Android may delay them.
Device testing is still mandatory, including focus/overlay behavior and Android 15+.

See [implementation tracker](docs/IMPLEMENTATION.md) for verified/pending status.

### On-device database tests

Android 15 AOSP CI executes `:app:connectedDebugAndroidTest` before the overlay smoke test.
The instrumentation suite validates a real SQLite v1-to-v2 migration, pinned note
ordering, persistence, Arabic search, merge deduplication and invalid-ID handling.
For local testing use an emulator and `gradle :app:connectedDebugAndroidTest`.
