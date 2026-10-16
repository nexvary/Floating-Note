# Floating Note — first requested change

The title is now an independent editable field above the note's content.
Both fields are committed atomically by a 350 ms debounced autosave and when
the window is minimized or stopped. Existing SQLite v1/v2 databases upgrade
to v3 without rewriting or deleting note bodies. The visual heading falls
back to the first nonempty body line only for legacy untitled notes.

Title metadata survives both JSON and password-encrypted backup round trips.
Previous JSON backups without a title remain readable. Search includes both
fields, and duplicate-import detection includes the independent title.

## Mail integration boundaries

- The Android "Share via email" control opens the system Sharesheet, where
  installed Gmail and Proton Mail apps can receive the note's title as the
  subject and its text as the message.
- This action is explicitly user-initiated; the app never asks for a mailbox
  password, obtains an inbox access token, or sends anything silently.
- **This is sharing, not automatic mailbox or cloud synchronisation.**
- Future optional Google account cloud sync should use Google Drive app data
  with narrow OAuth scopes rather than unrestricted Gmail access. Google
  OAuth app credentials and the user's consent would be required.
- Proton Mail does not officially integrate with third-party mail clients
  on Android. Proton Bridge is desktop-only. There is no direct Proton Mail
  login or automatic account sync in this release.

Original ColorNote APK is used as a UI/feature reference only; no proprietary
code is reused.

## Tests

- Migrate SQLite v1 and v2 to v3, preserving note bodies and IDs.
- Title/body atomic updates, restart persistence and title search.
- Plain JSON title round-trip and legacy title-less archive compatibility.
- Existing AES-GCM backup tests cover encrypted archive round trips.
- UI acceptance on emulator and physical Android devices remains necessary.
