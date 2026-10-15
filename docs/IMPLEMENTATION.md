# NEXVARY Floating Note — development status

## Version 0.3.0 deliverables (implementation; CI required)

| Requirement | State | Runtime evidence |
|---|---|---|
| Floating circle, edge docking and menu | Implemented | Previously observed in Android 15 emulator |
| Copy, paste, new, open, stop | Implemented | Full gesture matrix pending |
| Up to four floating editors, independent autosave | Implemented | Multi-window device test pending |
| Resizing, colors, pinning and text search | Implemented | Device test pending |
| Inexact reminders and reboot restoration | Implemented | Real device delivery test pending |
| v1→v2 SQLite migration without data loss | Implemented | Upgrade instrumentation pending |
| Plain JSON import/export | Implemented | JVM archive tests; device SAF pending |
| Encrypted backup AES-256-GCM, PBKDF2 210k | Implemented | JVM crypto tests + device SAF pending |
| Deduplicate repeat imports | Implemented | Database integration test pending |
| Arabic / RTL | Translated | RTL gesture and large font tests pending |
| CI (Kotlin unit tests, lint, APK) | Configured | Consult live GitHub Actions |

## Security controls

No internet or accessibility permission. Foreground service is not exported.
Backups encrypt note content with a per-file random salt and nonce and authenticate the
header. Passphrase required for decryption; no recovery. Legacy plain JSON export
remains a selectable unencrypted alternative. Password arrays are wiped after use.

## Pending acceptance gates

1. Physical Android devices: 8, 13, 15+: start/stop, rotation, overlay permission,
   all quick actions, editable keyboard, 4 simultaneous windows and independent save.
2. Database migration and import idempotence instrumented regression tests.
3. Real encrypted export → uninstall/reinstall → restore and wrong-password rejection.
4. Notification channel visibility, reminder delivery with Doze and battery optimizations.
5. UI polish (sizing, accessibility/TalkBack), full rich text, icon brand, screenshots.
6. Production keystore/signing, distribution review and repeatable release process.

### Emulator evidence

The Android 15 emulator previously displayed the home screen, circle and menu,
but unstable Pixel Launcher ANR prevented reliable completion of full gesture tests.
The emulator-smoke workflow is manual; do not claim it validates the full product.
