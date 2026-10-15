#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
PACKAGE=com.nexvary.floatingnote

tap_accessible() {
  local target="$1"
  adb shell uiautomator dump /sdcard/nexvary-window.xml
  adb pull /sdcard/nexvary-window.xml /tmp/nexvary-window.xml >/dev/null
  TARGET="$target" python3 - <<'PY'
import os
import re
import subprocess
import xml.etree.ElementTree as ET

target = os.environ["TARGET"]
root = ET.parse("/tmp/nexvary-window.xml").getroot()
for node in root.iter("node"):
    if node.attrib.get("text") != target and node.attrib.get("content-desc") != target:
        continue
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    if not match:
        continue
    x1, y1, x2, y2 = map(int, match.groups())
    if x2 <= x1 or y2 <= y1:
        continue
    subprocess.run(["adb", "shell", "input", "tap",
                    str((x1 + x2) // 2), str((y1 + y2) // 2)], check=True)
    print("Tapped accessible control:", target)
    break
else:
    raise SystemExit("Control not present in UI hierarchy: " + target)
PY
}

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell pm grant "$PACKAGE" android.permission.POST_NOTIFICATIONS || true
adb shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW allow
adb shell am start -W -n "$PACKAGE/.MainActivity"
sleep 4
adb exec-out screencap -p > screenshots/01-home.png

# Start the non-exported foreground service through the app's own button.
tap_accessible "Enable floating note"
sleep 3
adb exec-out screencap -p > screenshots/02-bubble.png

# Floating bubble has a deterministic default position: x=10dp, y=220dp.
adb shell input tap 105 660
sleep 2
adb exec-out screencap -p > screenshots/03-menu.png
# Android UiAutomator may omit TYPE_APPLICATION_OVERLAY windows from its hierarchy.
# Tap the first visible menu button in the fixed Pixel 6 emulator viewport.
adb shell input tap 72 798
sleep 3
adb exec-out screencap -p > screenshots/04-editor.png

{
  echo "Android SDK:"
  adb shell getprop ro.build.version.sdk
  echo "Application process:"
  adb shell pidof "$PACKAGE"
  echo "Service status:"
  adb shell dumpsys activity services "$PACKAGE"
} > smoke-diagnostics.txt
for image in screenshots/*.png; do
  test -s "$image"
done
grep -q FloatingNoteService smoke-diagnostics.txt
