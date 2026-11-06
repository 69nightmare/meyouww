#!/usr/bin/env bash
# Installs the debug APK on a running emulator, walks through the first screens,
# takes screenshots and fails if the app crashed. Used by the "smoke" CI job.
set -u
APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
OUT="${2:-shots}"
PKG="com.meyouww.app"
mkdir -p "$OUT"

shot() { sleep "${2:-2}"; adb exec-out screencap -p > "$OUT/$1.png"; echo "shot $1"; }

# A freshly booted emulator sometimes shows "<system app> isn't responding"; press Wait.
dismiss_system_dialogs() {
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  if grep -q "t responding" /tmp/ui.xml 2>/dev/null; then
    echo "dismissing a system 'not responding' dialog"
    _tap_from_dump "Wait"
    sleep 2
  fi
}

_tap_from_dump() {
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle = sys.argv[1].lower()
root = ET.parse('/tmp/ui.xml').getroot()
for node in root.iter('node'):
    label = (node.get('text') or '') + ' ' + (node.get('content-desc') or '')
    if needle in label.lower():
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  [ -n "$xy" ] && adb shell input tap $xy
}

# Taps the centre of the first on-screen node whose text or content description contains $1.
tap_text() {
  dismiss_system_dialogs
  adb shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1
  adb pull /sdcard/ui.xml /tmp/ui.xml >/dev/null 2>&1
  local xy
  xy=$(python3 - "$1" <<'PY'
import re, sys, xml.etree.ElementTree as ET
needle = sys.argv[1].lower()
root = ET.parse('/tmp/ui.xml').getroot()
for node in root.iter('node'):
    label = (node.get('text') or '') + ' ' + (node.get('content-desc') or '')
    if needle in label.lower():
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds')))
        print((x1 + x2) // 2, (y1 + y2) // 2)
        break
PY
)
  if [ -z "$xy" ]; then echo "tap_text: '$1' not found"; return 1; fi
  adb shell input tap $xy
  echo "tapped '$1' at $xy"
}

# Closes the on-screen keyboard if it is showing (BACK would otherwise navigate).
hide_kb() {
  sleep 1
  if adb shell dumpsys input_method | grep -q "mInputShown=true"; then adb shell input keyevent 4; sleep 1; fi
}

launch() {
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" >/dev/null
}

adb install -r "$APK"
# Let the freshly booted emulator settle before driving the UI.
sleep 25
dismiss_system_dialogs
adb shell settings put global window_animation_scale 0
adb shell settings put global transition_animation_scale 0
adb shell settings put global animator_duration_scale 1
adb logcat -c

launch
sleep 4; dismiss_system_dialogs
shot 01_welcome 2
tap_text "Next" && shot 02_welcome_page2
tap_text "Next" && shot 03_welcome_page3
tap_text "Get started" && shot 04_age_gate
tap_text "For example 1999" && adb shell input text 1999 && hide_kb
tap_text "I am 18 or older" ; tap_text "I have read and agree"
shot 05_age_gate_filled 1
tap_text "Continue" && shot 06_sign_in
# Stop before "Send my code": this build talks to the real backend, and CI must not send emails.
tap_text "you@example.com" && adb shell input text "riya@example.com" && hide_kb && shot 07_sign_in_email 1
# Google's picker on an emulator with no Google account: expect the friendly "no account" message.
tap_text "Continue with Google" && shot 07b_google_no_account 6
adb shell input keyevent 4 >/dev/null 2>&1; sleep 1

# Underage path shows the adults-only message.
adb shell pm clear "$PKG" >/dev/null
launch; sleep 5
tap_text "Skip" && sleep 2
tap_text "For example 1999" && adb shell input text 2012 && hide_kb
tap_text "I am 18 or older" ; tap_text "I have read and agree"
tap_text "Continue" && shot 08_underage

# Dark theme and 200% font scale.
adb shell pm clear "$PKG" >/dev/null
adb shell cmd uimode night yes
launch; shot 09_dark_welcome 6
tap_text "Skip" && shot 10_dark_age_gate
adb shell cmd uimode night no
adb shell settings put system font_scale 2.0
launch; shot 11_font200_welcome 6
tap_text "Skip" && shot 12_font200_age_gate
adb shell settings put system font_scale 1.0

adb logcat -d > "$OUT/logcat.txt"
if grep -E "FATAL EXCEPTION" -A20 "$OUT/logcat.txt" | grep -q "$PKG"; then
  echo "The app crashed:"; grep -E "FATAL EXCEPTION" -A30 "$OUT/logcat.txt"
  exit 1
fi
echo "No crash detected."
