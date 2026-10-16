# FG Note — by FG Machines

Offline-first Android sticky notes with a colourful, quick-entry library inspired by ColorNote usability principles. No proprietary source or design assets are copied.

Development repository: nexvary/Floating-Note, branch dev/foundation. Planned final account: FGMachines; repository has not been moved yet.

## Android identity
- App label: FG Note
- Application ID and Kotlin package: com.fgmachines.fgnote
- Beta version: 0.4.0-beta1 (new versionCode 1 for new Android package)
- Android 8+; local storage, Arabic and English.

IMPORTANT MIGRATION: FG Note installs separately from old com.nexvary.floatingnote. Android will NOT transfer notes automatically. In the old app export an encrypted .nfn backup, save the password, then install FG Note and Restore encrypted. Keep the old application installed until you verify restoration.

## Editing
- Separate autosaved title and body, movable bubble and up to 4 floating notes.
- Selected-word or selected-text colouring, headings, bold, checklists and undo/redo.
- Word-by-word and grapheme-aware character deletion.
- Embedded pictures as compact inline JPEG within locally stored rich HTML.
- PDF export with pages, PNG image export for shorter notes, password-encrypted and JSON archives.
- Save-and-exit confirmation, pinned notes, reminders, search and share with installed email apps.

Rich-note backups include embedded pictures; a single archive is limited to 5 MiB. Long notes should use multi-page PDF rather than PNG. The current release is a beta requiring device QA.

## Privacy
No internet or accessibility permissions. No email credentials or automatic Gmail/Proton sync. The ColourNote reference APK was inspected for comparison only.

See docs/RELEASE_SIGNING.md for production signing; debug builds cannot upgrade an old differently signed package.