#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if ! command -v gradle >/dev/null 2>&1; then
  echo "ERROR: Gradle 8.9 is required. Install Gradle 8.9 and retry." >&2
  exit 1
fi
if [[ -z "${ANDROID_HOME:-}${ANDROID_SDK_ROOT:-}" ]]; then
  echo "ERROR: Set ANDROID_HOME or ANDROID_SDK_ROOT to an installed Android SDK." >&2
  exit 1
fi

gradle --version
gradle :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --stacktrace
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
if [[ ! -s "$APK" ]]; then
  echo "ERROR: Build finished but debug APK was not found: $APK" >&2
  exit 1
fi
printf '\nDebug APK created: %s\n' "$APK"
sha256sum "$APK"
