#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "Usage: $0 /path/to/app.apk" >&2
  exit 2
fi
APK="$1"
if [[ ! -s "$APK" ]]; then
  echo "ERROR: APK does not exist or is empty: $APK" >&2
  exit 1
fi

if ! command -v unzip >/dev/null 2>&1; then
  echo "ERROR: unzip is required." >&2
  exit 1
fi
unzip -t "$APK" >/dev/null
echo "PASS: APK is a readable ZIP archive."

if command -v apksigner >/dev/null 2>&1; then
  echo "Checking APK signature..."
  apksigner verify --verbose --print-certs "$APK"
else
  echo "NOTE: apksigner not found; cryptographic signature was NOT verified."
  echo "Install Android SDK Build Tools and add its directory to PATH."
fi

if command -v sha256sum >/dev/null 2>&1; then
  sha256sum "$APK"
elif command -v shasum >/dev/null 2>&1; then
  shasum -a 256 "$APK"
fi
