# ScreenX AI — Phase 15: Build & Device Validation Toolkit

## Version

- `versionCode`: 5
- `versionName`: `0.15.0-phase15`

## Phase 15 changes

- Added a build-environment preflight script for Java, Gradle, Android SDK Platform 35, and Build Tools 35.0.0.
- Added a USB-debugging install helper for an already-built debug APK.
- Added a device-log collection helper for diagnosing crashes and recording-service failures.
- Added this ordered build and device-validation procedure.
- Kept the existing Phase 14 build and APK verification scripts.

## Build procedure (Linux/macOS/Termux with a compatible Linux environment)

1. Install JDK 17, Gradle 8.9, Android SDK Platform 35, Build Tools 35.0.0, and platform-tools.
2. Set `ANDROID_HOME` or `ANDROID_SDK_ROOT` to the SDK path.
3. From the project root run:

```bash
./scripts/check-build-env.sh
./scripts/build-debug.sh
./scripts/verify-apk.sh app/build/outputs/apk/debug/app-debug.apk
```

Expected debug APK:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Install on a physical Android device

1. Enable Developer options and USB debugging on the phone.
2. Connect by USB and accept the debugging authorization prompt.
3. Run:

```bash
./scripts/install-debug-device.sh
```

The debug application ID is `com.screenx.ai.debug`. Launch it from the phone, or use:

```bash
adb shell monkey -p com.screenx.ai.debug 1
```

## Capture diagnostics after a failure

```bash
./scripts/capture-device-logs.sh
```

The command creates a timestamped text file in the current directory. Inspect it and remove private information before sharing it.

## Required physical-device checks

Record device model, Android version, test date, and PASS / FAIL / NOT TESTED for each item:

- Install, launch, and app restart
- Permission grant and denial paths
- Start, pause, resume, and stop recording
- Microphone audio and internal audio (where supported)
- Recording notification and its controls
- Screen rotation during recording
- Save location, playback, and sharing
- Floating overlay permission and controls
- Camera overlay permission and shutdown
- At least 10 minutes of continuous recording
- Low-storage handling
- Reboot and relaunch behavior

Do not mark any item PASS without a real device test. Attach sanitized logs for failures.

## Current verification status

- [x] Phase 14 ZIP extracted and project tree retained.
- [x] Version metadata updated for Phase 15.
- [x] Added preflight, install, and log-capture scripts.
- [x] Shell scripts passed `bash -n` syntax validation.
- [x] Android manifest XML parsed successfully.
- [ ] Gradle unit tests and lint executed (not run in this environment).
- [ ] Debug or release APK compiled (not run in this environment; Android SDK/Gradle unavailable).
- [ ] APK installed and exercised on a physical Android device.

## Known limitations retained from earlier phases

- Custom save-tree URI is stored, but recording export still uses `MediaStore` under `Movies/ScreenX`.
- Watermark preference is stored, but watermark rendering is not connected to exported recordings.
- Language selection localizes Settings only.
- Camera overlay behavior depends on device support and runtime permissions.
- Release signing requires the owner's private keystore and credentials.

Phase 15 is a validation toolkit, not a claim that the app is production-ready or that an APK has been built and tested.
