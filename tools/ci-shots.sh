#!/usr/bin/env bash
# CI counterpart of `check.ps1 shots`: seeds synthetic demo data and captures the four tabs per theme.
# Runs inside android-emulator-runner, after :app:assembleGithubDebug and :app:assembleGithubDebugAndroidTest.
set -euo pipefail
PKG=br.com.fluxo.app
OUT=${1:-shots}
mkdir -p "$OUT"
adb install -r app/build/outputs/apk/github/debug/app-github-debug.apk > /dev/null
adb install -r app/build/outputs/apk/androidTest/github/debug/app-github-debug-androidTest.apk > /dev/null
read -r W H < <(adb shell wm size | sed -n 's/.*Physical size: \([0-9]*\)x\([0-9]*\).*/\1 \2/p')
# Taps the centre of the on-screen node whose text is exactly $1 (the app's own tab bar, not the system nav bar).
tap_text() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null
  local b
  b=$(adb shell cat /sdcard/ui.xml | tr '>' '\n' | grep -m1 "text=\"$1\"" | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
  [ -n "$b" ] || { echo "tab '$1' not found"; return 1; }
  read -r x1 y1 x2 y2 <<< "$b"
  adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
}
for THEME in light dark; do
  adb shell am instrument -w -e seedDemo true -e seedTheme "$THEME" -e class "$PKG.SeedDemoDataTest" \
    "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tail -n 3
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
  sleep 8
  for PAIR in "Início:inicio" "Registros:registros" "Planejar:planejar" "Ajustes:ajustes"; do
    tap_text "${PAIR%%:*}"
    sleep 3
    TAB=${PAIR##*:}
    adb exec-out screencap -p > "$OUT/$TAB-$THEME.png"
    adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 4 )) 300
    sleep 1
    adb exec-out screencap -p > "$OUT/$TAB-$THEME-2.png"
  done
done
ls -la "$OUT"
