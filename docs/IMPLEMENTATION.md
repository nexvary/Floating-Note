# Implementation tracker — NEXVARY Floating Note

## 0.2.0 — development branch

| Requirement | Code | Validation |
|---|---|---|
| Circular bubble, docking and actions | implemented | physical device test pending |
| Copy, paste, create, open and stop | implemented | physical device test pending |
| Movable editor and collapse | implemented | physical device test pending |
| Resize editor; remember width and height | implemented | physical device test pending |
| Multiple notes and autosave | implemented | physical device test pending |
| Five themes and pinned note ordering | implemented | physical device test pending |
| Note text search | implemented | unit tests and device test pending |
| Reminder schedule, update, cancel and reboot restore | implemented | Doze/notification device test pending |
| SAF JSON backup export/import; transactional merge | implemented | JVM tests; device SAF test pending |
| SQLite schema migration from v1 to v2 | implemented | device upgrade test pending |
| Arabic RTL strings and first-strong editing | implemented | device localization test pending |
| CI unit tests, Android lint, APK artifact | implemented | see actual Actions runs |

### Important constraints

- JSON backups are plaintext, not encrypted. Export only to trusted storage.
- Import MERGES; it never deletes current notes. Reimport duplicates notes.
- Reminders are INEXACT. Android may delay them. Android 13+ requires notification permission.
- Pin denotes top of the list, not a locked editor window.
- Copy/paste operate on note content and the Android clipboard, not other apps' input fields.
- Android 8–16, OEM background restrictions, landscape and accessibility still need device verification.

## Future increments

1. Android emulator or physical-device UI acceptance tests and screenshots.
2. Test upgrade v1 to v2 and backup rollback, notification permission, RTL gestures and low-memory recovery.
3. Optional encrypted backup, rich-text note editor and simultaneously visible multiple editors.
4. Optional recurring reminders and import deduplication.
5. Production signing, store requirements and app icon design refinements.

## Hardware acceptance checklist

- Grant/deny overlay; test bubble launch, drag, docking and every quick action.
- Test copy/paste when different apps are in foreground; verify denied clipboard response.
- Create multiple notes, resize editor, rotate screen, minimize, reopen and verify autosave.
- Change colors/pinning; search by Arabic/English, delete and reopen.
- Import/export JSON and corrupt file; verify no partial changes.
- Upgrade real v1 database; preserve data and IDs.
- Reminders: schedule, change, cancel, reboot, battery saver and denied notifications.
- Android 8, 13 and 15+; narrow viewport, RTL, large font and TalkBack focus.
