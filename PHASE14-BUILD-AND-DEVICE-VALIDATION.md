# ScreenX AI — Phase 14: Build Automation & Device Validation

## What changed

- App version metadata is now `versionCode 4`, `versionName 0.14.0-phase14`.
- Added `scripts/build-debug.sh` to run unit tests, debug lint, and build a debug APK.
- Added `scripts/verify-apk.sh` to check APK ZIP integrity, verify signatures when `apksigner` is installed, and print a SHA-256 hash.
- Added `.github/workflows/phase14-validation.yml` to run tests, debug/release lint, assemble both APK variants, and upload APK/checksum/test/lint artifacts.
- Added a device test matrix below. These are instructions, not tests already performed.

## Requirements for a local build

- JDK 17
- Gradle 8.9
- Android SDK Platform 35
- Android SDK Build Tools 35.0.0
- `ANDROID_HOME` or `ANDROID_SDK_ROOT` pointing to the SDK

This archive does not include the Android SDK, Gradle installation, signing keystore, or credentials. The repository currently uses a system Gradle command; no Gradle Wrapper JAR is included.

## Build a debug APK (Linux/macOS)

From the extracted `screenx-ai` project directory:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$PATH:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0"
./scripts/build-debug.sh
```

Expected output path:

```text
app/build/outputs/apk/debug/app-debug.apk
```

To verify it:

```bash
./scripts/verify-apk.sh app/build/outputs/apk/debug/app-debug.apk
```

## Install on a test phone

Enable Developer options and USB debugging, connect the phone, then run:

```bash
adb devices
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If Android reports an installation conflict, uninstall the existing debug build first. The debug variant uses the `.debug` application ID suffix and can coexist with the normal release package.

## Device validation matrix

Record each result as PASS / FAIL / NOT TESTED, along with device model, Android version, and a screenshot or log for failures.

| Area | Test | Expected result |
|---|---|---|
| Launch | Install and open app | No crash; home screen loads |
| Permissions | Grant/deny required permissions | Clear explanation; no permission crash |
| Recording | Start, pause, resume, stop | State changes correctly; recording file is produced |
| Audio | Test microphone and internal audio separately where supported | Selected audio source is recorded; unsupported paths are explained |
| Notifications | Start recording and inspect notification | Persistent recording notification and working controls |
| Rotation | Rotate device during a recording | App/service remains stable; no lost recording |
| Storage | Save and open a completed recording | File exists and plays; actual destination is shown correctly |
| Playback | Open a saved recording | Video and available audio play correctly |
| Overlay | Enable floating controls if supported | Overlay appears only after permission and controls work |
| Camera | Enable camera overlay if supported | Camera preview appears and can be stopped safely |
| Settings | Change available preferences and restart app | Preferences persist and displayed state is accurate |
| Long run | Record for at least 10 minutes | No crash, severe overheating, or unexplained stop |
| Low storage | Test with limited free space | Graceful error; app does not report a false successful save |

Do not mark a row PASS without testing it on a physical device. Some recording/audio capabilities vary by Android version and device vendor.

## Release signing

Release signing remains optional and owner-controlled. Follow `PHASE13-RELEASE.md` to create and protect a private keystore. Never commit the keystore or passwords. A release APK built without signing credentials is not ready for distribution or updates to an already-installed signed app.

## Known limitations carried forward

- Custom save-tree URI is stored, but the recording export path still uses `MediaStore` under `Movies/ScreenX`.
- Watermark preference is stored, but watermark rendering is not wired into exported recordings.
- Language selection localizes Settings only.
- Camera overlay behavior depends on runtime support and permissions.
- No signing key is included.

These features must not be described as fully implemented until code and device tests confirm them.

## Phase 14 verification status for this archive

- [x] Phase 13 archive extracted successfully.
- [x] Project source/configuration retained.
- [x] Version metadata updated.
- [x] Build and APK verification scripts added and shell syntax checked.
- [x] CI validation workflow added and YAML parsed.
- [ ] Gradle unit tests/lint/build executed in this workspace (Gradle and Android SDK are unavailable here).
- [ ] APK signature verified (no APK or owner keystore generated here).
- [ ] Physical-device test matrix completed (requires user's Android device).
