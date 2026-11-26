# ScreenX AI — Phase 22: Final Build & Runtime Readiness

## Version
- `versionName`: `0.22.0-phase22`
- `versionCode`: `12`
- Baseline: `ScreenX-AI-Phase21.zip`

## Scope and changes
Phase 22 prepares repeatable CI gates for source audit, JVM unit tests, Android Lint,
debug and release APK assembly, debug signature verification, and SHA-256 checksums.
It adds `scripts/phase22-build-audit.sh`, updates version metadata and source validation,
consolidates the primary CI workflow, and removes obsolete duplicate Phase 14/15 workflows.
CI artifact: `screenx-phase22-build-results`.

## Local build requirements
- JDK 17; Gradle 8.9; Android SDK Platform 35; Build Tools 35.0.0.

```bash
./scripts/phase22-build-audit.sh
gradle :app:testDebugUnitTest --stacktrace
gradle :app:lintDebug :app:lintRelease --stacktrace
gradle :app:assembleDebug --stacktrace
gradle :app:assembleRelease --stacktrace
./scripts/verify-apk.sh app/build/outputs/apk/debug/app-debug.apk
```

## Android device test gate
After downloading the debug APK from CI:
1. Install with `./scripts/install-debug-device.sh app/build/outputs/apk/debug/app-debug.apk`.
2. Test fresh install, first launch, and notification permission behavior.
3. Accept and deny MediaProjection consent; verify graceful recovery.
4. Record a 30-second clip with microphone; test internal audio only where supported.
5. Test pause/resume if exposed by the device/API path.
6. Record to default Movies/ScreenX and a user-selected SAF folder.
7. Confirm MP4 files are non-empty and play in a separate player.
8. Revoke overlay/camera permissions and confirm graceful recovery.
9. Test rename/share/delete for MediaStore and selected-folder items.
10. Test editor export with watermark off and on.
11. Test rotation, background/foreground, repeated start/stop, low storage, and process recreation.
12. Capture failure logs with `./scripts/capture-device-logs.sh`.

Record device model, Android version, CI run URL, APK SHA-256, and evidence in
`PHASE22-DEVICE-TEST-RESULTS.md`. Do not mark a test PASS unless it was performed.

## Packaging-environment validation
- Static source/build-readiness audit: executed during packaging.
- Gradle/Kotlin compilation, unit tests, Android Lint, APK assembly/signature verification,
  emulator tests, and physical-device tests: NOT RUN in this environment.
- This workspace has Java 21 but no Gradle executable, Android SDK, or ADB.
- Release signing remains opt-in; no keystore or credentials are included.

A static audit is not a build result. A successful CI run and completed device matrix are
required before describing the app as build-verified or device-verified.
