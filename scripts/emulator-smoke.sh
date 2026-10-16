#!/usr/bin/env bash
set -euo pipefail
mkdir -p screenshots
PACKAGE=com.fgmachines.fgnote

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
sleep 6
# Cold emulator boots occasionally display a Pixel Launcher ANR dialog.
# Dismiss the SYSTEM dialog instead of altering our non-exported service.
# An AOSP emulator avoids Google Pixel Launcher ANR on hosted runners.
adb exec-out screencap -p > screenshots/01-home.png

# Back-navigation must ask for save confirmation, not silently exit.
adb shell input keyevent 4
sleep 2
adb shell uiautomator dump /sdcard/fg-note-back.xml
adb pull /sdcard/fg-note-back.xml /tmp/fg-note-back.xml >/dev/null
grep -q "Save notes before exit?" /tmp/fg-note-back.xml
tap_accessible "Keep editing"
sleep 2

# Start the non-exported foreground service through the app's own button.
if ! tap_accessible "Enable floating note"; then
  sleep 7
  tap_accessible "Enable floating note"
fi
sleep 3
adb exec-out screencap -p > screenshots/02-bubble.png

# Compute absolute tap positions from density. Windows are TYPE_APPLICATION_OVERLAY,
# and UIAutomator may omit their accessibility hierarchy.
density="$(adb shell wm density | awk '{print $3}' | tr -d '\r')"
python3 - "$density" <<'PY'
import subprocess
import sys
scale = int(sys.argv[1]) / 160.0
def tap(x_dp,y_dp):
    subprocess.run(["adb","shell","input","tap",
                    str(round(x_dp * scale)),str(round(y_dp * scale))],check=True)
tap(10 + 31,220 + 31)  # 62dp bubble center
PY
sleep 2
adb exec-out screencap -p > screenshots/03-menu.png
python3 - "$density" <<'PY'
import subprocess
import sys
scale = int(sys.argv[1]) / 160.0
# The first of five buttons in the horizontal quick-action menu.
subprocess.run(["adb","shell","input","tap",
                str(round((7 + 21.5) * scale)),
                str(round((220 + 67 + 7 + 21.5) * scale))],check=True)
PY
sleep 3
adb exec-out screencap -p > screenshots/04-editor.png

# Critical UX: bubble must remain above the note while an editor is open.
python3 - "$density" <<'PY'
import subprocess
import time
import sys
scale = int(sys.argv[1]) / 160.0
def tap(x, y):
    subprocess.run(["adb", "shell", "input", "tap",
                    str(round(x * scale)), str(round(y * scale))], check=True)
tap(41, 251)  # bubble above the floating editor
time.sleep(2)
with open("screenshots/05-menu-over-editor.png", "wb") as image:
    image.write(subprocess.check_output(["adb", "exec-out", "screencap", "-p"]))
tap(216.5, 315.5)  # stop button is the last of the five bubble controls
PY
sleep 4
adb shell dumpsys activity services "$PACKAGE" > closed-services.txt
if grep -q FloatingNoteService closed-services.txt; then
  echo "ERROR: close button did not stop the service" >&2
  exit 1
fi

# Restart after close so the permission-revocation scenario starts independently.
adb shell am force-stop "$PACKAGE"
adb shell am start -W -n "$PACKAGE/.MainActivity"
sleep 3
if ! tap_accessible "Enable floating note"; then
  # In the new notes-first layout this action moves below the saved note cards.
  adb shell input swipe 480 1250 480 300 420
  sleep 2
  adb exec-out screencap -p > screenshots/06-scrolled-restart.png
  tap_accessible "Enable floating note"
fi
sleep 3

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

# Verify runtime permission revocation stops the foreground service and overlays.
adb shell appops set "$PACKAGE" SYSTEM_ALERT_WINDOW deny
sleep 5
adb shell dumpsys activity services "$PACKAGE" > revoked-overlay-services.txt
if grep -q FloatingNoteService revoked-overlay-services.txt; then
  echo "ERROR: service remains active after overlay permission revocation" >&2
  exit 1
fi

