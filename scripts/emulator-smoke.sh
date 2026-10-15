#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
PACKAGE=com.nexvary.floatingnote
COMPONENT="$PACKAGE/.FloatingNoteService"
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
adb shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW allow
adb shell am start -W -n "$PACKAGE/.MainActivity"
sleep 3
adb exec-out screencap -p > screenshots/01-home.png
adb shell am start-foreground-service -n "$COMPONENT" -a "$PACKAGE.START"
sleep 3
adb exec-out screencap -p > screenshots/02-bubble.png
adb shell am start-foreground-service -n "$COMPONENT" -a "$PACKAGE.OPEN"
sleep 3
adb exec-out screencap -p > screenshots/03-editor.png
{
  echo "Android SDK:"
  adb shell getprop ro.build.version.sdk
  echo "Application process:"
  adb shell pidof "$PACKAGE"
  echo "Service status:"
  adb shell dumpsys activity services "$PACKAGE"
} > smoke-diagnostics.txt
test -s screenshots/01-home.png
test -s screenshots/02-bubble.png
test -s screenshots/03-editor.png
grep -q FloatingNoteService smoke-diagnostics.txt
