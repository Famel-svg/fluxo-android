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
DPI=$(adb shell wm density | sed -n 's/.*Physical density: \([0-9]*\).*/\1/p')
NAV_Y=$(( H - 34 * DPI / 160 ))
for THEME in light dark; do
  adb shell am instrument -w -e seedDemo true -e seedTheme "$THEME" -e class "$PKG.SeedDemoDataTest" \
    "$PKG.test/androidx.test.runner.AndroidJUnitRunner" | tail -n 3
  adb shell am force-stop "$PKG"
  adb shell am start -W -n "$PKG/.MainActivity" > /dev/null
  sleep 8
  i=0
  for TAB in inicio registros planejar ajustes; do
    adb shell input tap $(( W * (2 * i + 1) / 8 )) "$NAV_Y"
    sleep 3
    adb exec-out screencap -p > "$OUT/$TAB-$THEME.png"
    adb shell input swipe $(( W / 2 )) $(( H * 3 / 4 )) $(( W / 2 )) $(( H / 4 )) 300
    sleep 1
    adb exec-out screencap -p > "$OUT/$TAB-$THEME-2.png"
    i=$(( i + 1 ))
  done
done
ls -la "$OUT"
