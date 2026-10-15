# Implementation tracker — NEXVARY Floating Note

## Requirements

| Requirement | Implementation | Verification |
|---|---|---|
| Circular floating icon | Native overlay ImageView, gradient background, dock to screen edges | Device test pending |
| Close action in floating menu | Service stop, cleans up overlays and foreground notification | Device test pending |
| Copy action in floating menu | Copy current/last note into Android ClipboardManager | Device test pending |
| Paste action in floating menu | Explicit clipboard read and insertion at caret in note | Device test pending |
| Open / new note in floating menu | Open most recent or new note | Device test pending |
| Floating note editor | Movable overlay EditText, collapse to bubble | Device test pending |
| Multiple notes | Local SQLite, create/list/update/delete | UI device test pending |
| Autosave | Debounced save after text edits and save on collapse | Device test pending |
| Arabic / RTL | Android localized strings and first-strong text direction | Device test pending |
| Accessibility | Labeled toolbar ImageViews, clickable buttons | TalkBack test pending |
| No excessive privileges | No AccessibilityService or internet permission | Manifest inspection pending |
| CI | GitHub Actions build, lint, unit tests, artifact | Verify CI run |
| Testable title / preview | Five JUnit unit tests | Verify CI run |

## Next increments (not claimed done)

- Text selection and richer formatting within the note.
- Color themes per note, resizing handles, pinning and locking.
- Reminder notifications and background scheduler.
- Optional encrypted export/import and recovery.
- Full TalkBack, keyboard, RTL, Android 8–16 and OEM battery-management testing.
- Device screen recordings and real screenshots.
- Production signing and store distribution.

## Manual acceptance tests

1. Grant/deny overlay access; app behaves safely in both cases.
2. Start bubble; drag across edges; dock and reopen menu.
3. Press each of the five quick actions and verify its effect.
4. Create two notes, switch notes, type and wait, kill/reopen application; text persists.
5. Paste with populated/empty clipboard; read failures show a graceful message.
6. Minimize and reopen while using a different app, in portrait and landscape.
7. Stop the service; overlay disappears and ongoing notification is removed.
8. Toggle Arabic and English device languages; verify RTL text and button hit targets.
9. Remove overlay permission while service is active; ensure no crash.
10. Test accessibility labels, screen reader navigation and low-memory recovery.
