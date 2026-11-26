#!/usr/bin/env bash
set -euo pipefail
OUT="${1:-screenx-device-logs-$(date +%Y%m%d-%H%M%S).txt}"
command -v adb >/dev/null 2>&1 || { echo 'ERROR: adb not found.' >&2; exit 1; }
DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')"
[[ "$DEVICE_COUNT" -gt 0 ]] || { echo 'ERROR: No authorized Android device.' >&2; exit 1; }
{
  echo '=== ScreenX AI device diagnostics ==='
  date -Is
  echo '\n=== Device ==='
  adb shell getprop ro.product.manufacturer
  adb shell getprop ro.product.model
  adb shell getprop ro.build.version.release
  adb shell getprop ro.build.version.sdk
  echo '\n=== Recent crash / app logs ==='
  adb logcat -d -t 2500 | grep -iE 'screenx|AndroidRuntime|FATAL EXCEPTION|Fatal signal|MediaProjection|ForegroundService' || true
} > "$OUT"
echo "Saved device diagnostics: $OUT"
echo 'Review the file and remove personal information before sharing it.'
