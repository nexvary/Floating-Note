# NEXVARY Floating Note — development status

## Version 0.3.1 deliverables (implementation; CI required)

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

## Verified Android emulator run (2026-10-15)

GitHub Actions AOSP Android 15 smoke run **38774157345** passed. The verified
artifact contains real PNGs for the home screen, floating bubble, expanded
quick-action menu and editor, plus a service diagnostics log. This confirms
app launch, a user-triggered overlay start, bubble action menu and note-editor
opening on the emulator. It does not cover every quick-action side effect,
four-window persistence, encrypted SAF restoration on a phone, or reminder
delivery in battery-saver mode.

Evidence: https://github.com/nexvary/Floating-Note/actions/runs/38774157345

The 0.3.1 follow-up refreshes the home page's running-state control and resumes
a pending note after the system overlay permission flow; its own CI must pass.

## 0.3.2 regression coverage

The emulator suite now runs real instrumentation tests of SQLite migration, persistence,
search and import idempotence, and Android-provider AES-GCM backup decryption. It also
checks that the floating foreground service ends after the overlay permission is revoked.
Android 8 (API 26) and Android 15 (API 35) run independently in GitHub Actions;
verify both job conclusions before claiming cross-version validation.
