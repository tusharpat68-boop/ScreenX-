# ScreenX AI — Phase 23: Stability, Compatibility & Bug Prevention

## Version
- versionName: `0.23.0-phase23`
- versionCode: `13`
- Baseline: `ScreenX-AI-Phase22.zip`

## Changes
1. Added `RecordingConfigValidator` to validate settings that may have been restored
   from old preferences or supplied by an outdated UI before encoder/storage setup.
2. Integrated validation at the beginning of `ScreenRecorderEngine.start`, so invalid
   resolution, frame-rate, bitrate, or enabled-audio bitrate values fail before any
   recording output or encoder resources are allocated.
3. Added JVM regression tests for defaults, supported boundaries, invalid settings,
   and audio bitrate behavior when audio is disabled/enabled.
4. Added `scripts/phase23-stability-audit.sh` for source-level regression and security
   boundary checks.
5. Updated CI to run the Phase 23 audit and Phase 22 build-readiness audit before unit
   tests, Android Lint, debug/release assembly, APK verification, and artifact upload.

## Validation
Run from the project root:

```bash
./scripts/phase23-stability-audit.sh
gradle :app:testDebugUnitTest --stacktrace
gradle :app:lintDebug :app:lintRelease --stacktrace
gradle :app:assembleDebug --stacktrace
gradle :app:assembleRelease --stacktrace
```

## Device compatibility matrix
Test at minimum one Android 8+ device (minSdk 26) and one recent Android 13+ device.
Prefer a second OEM/device for encoder and MediaProjection differences. Record model,
Android version, CI run URL, APK SHA-256, test evidence, and outcome.

Required scenarios: fresh install/update, MediaProjection accept/deny, microphone and
supported internal audio, repeated start/stop, pause/resume if available, rotation,
background/foreground, low storage, selected SAF destination, MP4 playback, rename,
share/delete, watermark export, overlay/camera permission denial and recovery.

## Honest status
Static source audit may be run in the packaging environment. Gradle/Kotlin compilation,
JVM test execution, Android Lint, APK assembly, emulator tests and physical-device
compatibility tests are not marked passed unless their CI/device results are available.
Release signing remains opt-in and no signing key is packaged.
