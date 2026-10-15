# NEXVARY Floating Note

Native Android notes that stay accessible above other apps, built for Arabic and English.

**Development branch:** [dev/foundation](https://github.com/nexvary/Floating-Note/tree/dev/foundation)  
**Status:** Android 0.1.0 initial implementation, pending CI and device validation.

## Version 0.1.0 scope

- Draggable, circular floating bubble with edge docking and saved position.
- Bubble quick actions: open, new note, copy, paste, stop floating note.
- Movable floating editor with collapse, clipboard actions and autosave.
- Multiple notes in a local SQLite database; create, open, delete.
- Native Arabic/RTL and English strings.
- Runtime overlay-permission onboarding and foreground notification.
- No network access, advertisements, accessibility permission, or telemetry.

## Build

Requires JDK 17, Android SDK 36, and Gradle 8.13. Open with Android Studio or build with:

    gradle :app:assembleDebug :app:testDebugUnitTest :app:lintDebug

GitHub Actions builds and uploads the **debug APK** as a workflow artifact after checks pass.
On a phone running Android 8+, install the debug APK, grant **Display over other apps**,
then enable the floating icon from the app.

## Privacy and platform limitations

The clipboard is controlled by Android. Copy is local to a note; paste reads the system
clipboard only when the user presses Paste. Android can reject clipboard reads depending
on the currently focused window and OS version. This application cannot inject paste
into an arbitrary third-party app, and does not use accessibility privileges.

The foreground service is started from an activity as a direct user action. Android 15+
may refuse service launches initiated from the background. Persisting overlays and
background operation need validation on physical devices and OEM variants. The
special-use foreground-service declaration may require review for Google Play distribution.

This repository contains original code, not extracted code from the reference APK.

## Documentation

See [requirements & implementation tracker](docs/IMPLEMENTATION.md).
