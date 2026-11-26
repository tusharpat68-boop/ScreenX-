#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
FAIL=0

check_cmd() {
  local name="$1" required="$2"
  if command -v "$name" >/dev/null 2>&1; then
    printf 'PASS command: %s (%s)\n' "$name" "$(command -v "$name")"
  else
    printf '%s command missing: %s\n' "$([[ "$required" == yes ]] && echo FAIL || echo WARN)" "$name"
    [[ "$required" == yes ]] && FAIL=1
  fi
}

check_cmd java yes
check_cmd gradle yes
check_cmd adb no
check_cmd unzip yes
check_cmd sha256sum no

if command -v java >/dev/null 2>&1; then
  JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
  echo "Java: $JAVA_VERSION"
  if ! java -version 2>&1 | grep -Eq 'version "(17|21)([.\"]|$)'; then
    echo "WARN: Project expects JDK 17; JDK 21 may work but is not the documented baseline."
  fi
fi

SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$SDK" ]]; then
  echo "FAIL: Set ANDROID_HOME or ANDROID_SDK_ROOT to the Android SDK directory."
  FAIL=1
else
  echo "Android SDK: $SDK"
  [[ -d "$SDK/platforms/android-35" ]] && echo 'PASS: Android SDK Platform 35' || { echo 'FAIL: Android SDK Platform 35 missing'; FAIL=1; }
  [[ -d "$SDK/build-tools/35.0.0" ]] && echo 'PASS: Build Tools 35.0.0' || { echo 'FAIL: Build Tools 35.0.0 missing'; FAIL=1; }
fi

if command -v gradle >/dev/null 2>&1; then
  GRADLE_VERSION="$(gradle --version 2>/dev/null | awk '/^Gradle / {print $2; exit}')"
  echo "Gradle: ${GRADLE_VERSION:-unknown}"
  [[ "$GRADLE_VERSION" == 8.9 ]] || { echo 'WARN: Documented Gradle version is 8.9.'; }
fi

if [[ "$FAIL" -ne 0 ]]; then
  echo 'Preflight FAILED. Fix the required items above before building.' >&2
  exit 1
fi
echo 'Preflight passed. You can run ./scripts/build-debug.sh.'
