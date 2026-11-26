#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${1:-$ROOT/app/build/outputs/apk/debug/app-debug.apk}"
PACKAGE="com.screenx.ai.debug"

command -v adb >/dev/null 2>&1 || { echo 'ERROR: adb not found. Install Android SDK platform-tools.' >&2; exit 1; }
[[ -s "$APK" ]] || { echo "ERROR: APK not found: $APK" >&2; echo 'Build it first with ./scripts/build-debug.sh.' >&2; exit 1; }

echo 'Connected Android devices:'
adb devices -l
DEVICE_COUNT="$(adb devices | awk 'NR>1 && $2=="device" {n++} END {print n+0}')"
[[ "$DEVICE_COUNT" -gt 0 ]] || { echo 'ERROR: No authorized Android device. Enable USB debugging and accept the RSA prompt.' >&2; exit 1; }

./scripts/verify-apk.sh "$APK"
adb install -r "$APK"
echo "Installed. Launch manually, or run: adb shell monkey -p $PACKAGE 1"
