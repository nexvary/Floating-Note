# NEXVARY Floating Note

Native Android notes that stay accessible above other apps. Development happens on
the dev/foundation branch.

## Version 0.2.0 (development)

- Draggable circular bubble with edge docking, saved position and a 5-action menu.
- Floating note editor with resize handle, remembered size, color and autosave.
- Local SQLite storage with multiple notes, search, pinned sorting and five themes.
- User-created reminders, scheduled as inexact alarms without special exact-alarm access.
- Android document-picker export/import of JSON backups (merge-only).
- English and Arabic localization, no internet or accessibility permission.

## Build and download

Java 17, Gradle 8.13 and Android SDK 36:

    gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug

On successful GitHub Actions runs download the NEXVARY-Floating-Note-debug artifact
and extract app-debug.apk. Allow Display over other apps, then launch the floating
bubble from the app. Android 13+ notification permission is required for reminders.

Actions workflow: https://github.com/nexvary/Floating-Note/actions/workflows/android.yml

## Privacy

Notes stay in an on-device SQLite database. An exported JSON backup is PLAINTEXT;
only save it to a trusted location. Import merges rather than overwrites, and repeated
imports may duplicate notes. There is no server, networking permission or telemetry.

Reminders are not exact to the minute because the system may defer inexact alarms.
See docs/IMPLEMENTATION.md for the validation checklist and open limitations.

The reference APK was examined for product comparisons; the code here is original.
