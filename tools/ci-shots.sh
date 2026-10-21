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
# Taps the app's own tab $1 (index $2), not the system nav bar. Looks it up by text in a UI dump; when the dump
# does not expose it, falls back to the tab bar position measured on a 1080x2400 Pixel 6 (centre at 91.5% height).
tap_tab() {
  local b="" try
  for try in 1 2 3; do
    adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1 || true
    # Every stage reads its whole input and never fails: an early exit would abort the script under pipefail.
    b=$(adb shell cat /sdcard/ui.xml 2>/dev/null | tr '>' '\n' | { grep "text=\"$1\"" || true; } \
      | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p' | sed -n 1p)
    [ -n "$b" ] && break
    sleep 2
  done
  if [ -n "$b" ]; then
    read -r x1 y1 x2 y2 <<< "$b"
    adb shell input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 ))
  else
    echo "tab '$1' not in the UI dump; tapping the tab bar position"
    adb shell input tap $(( W * (2 * $2 + 1) / 8 )) $(( H * 915 / 1000 ))
  fi
}
for THEME in light dark; do
  adb shell am instrument -w -e seedDemo true -e seedTheme "$THEME" -e class "$PKG.SeedDemoDataTest" \
    "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tail -n 3
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
  sleep 8
  i=0
  for PAIR in "Início:inicio" "Registros:registros" "Planejar:planejar" "Ajustes:ajustes"; do
    tap_tab "${PAIR%%:*}" "$i"
    i=$(( i + 1 ))
    sleep 3
    TAB=${PAIR##*:}
    adb exec-out screencap -p > "$OUT/$TAB-$THEME.png"
    adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 4 )) 300
    sleep 1
    adb exec-out screencap -p > "$OUT/$TAB-$THEME-2.png"
  done
done
ls -la "$OUT"
