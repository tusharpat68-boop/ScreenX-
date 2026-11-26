# ScreenX AI — Phase 12 QA checklist

## Automated checks

The GitHub Actions workflow runs:

1. JVM unit tests for the pure domain models.
2. Android Lint for the debug variant.
3. Debug APK assembly.
4. Unsigned release APK assembly (R8/resource shrinking enabled).

A green workflow proves these automated build steps passed on the GitHub runner. It does **not** prove that every recording path works on every Android device.

## Required real-device checks

Test on at least one Android 8.0 (API 26) device and one Android 14/15 (API 34/35) device where available.

- [ ] Fresh install and first launch.
- [ ] Start recording; accept system MediaProjection consent.
- [ ] Stop from app, floating control, notification, and Android system capture UI.
- [ ] Pause and resume; confirm timer excludes paused time.
- [ ] Rotate the device while recording.
- [ ] Record with no audio, microphone, internal audio, and both where the OS/device allows.
- [ ] Deny microphone, camera, notification, and overlay permissions; confirm graceful fallback.
- [ ] Test 480p/720p/1080p, 24/30/60 FPS and automatic/custom bitrate against device encoder support.
- [ ] Test face camera and drawing overlay independently and together.
- [ ] Fill storage close to the low-storage threshold; confirm safe stop and a playable file.
- [ ] Verify saved MP4s and screenshots in MediaStore, playback, share, rename, delete, and thumbnails.
- [ ] Verify settings survive process death and device restart.
- [ ] Verify custom folder and watermark controls are clearly described as preferences only until connected to the export pipeline.
- [ ] Run a 20-minute recording and inspect audio/video sync, thermal behavior, and memory use.

## Known limitations carried forward

- User-selected save-tree URI is persisted, but recording export remains MediaStore `Movies/ScreenX`.
- Watermark preference is persisted, but watermark rendering is not wired into export.
- Language selection currently localizes the Settings screen, not every screen.
- Camera overlay/device compatibility depends on runtime camera availability and permissions.
- No signing key is included. The release APK from CI is unsigned; sign it with the owner's private keystore before distribution.

## Local build

The project does not include the Gradle wrapper. Use Android Studio's Gradle integration, or install Gradle 8.9 and an Android SDK with platform 35/build-tools 35.0.0. In Termux, a compatible ARM64 Android SDK and Termux `aapt2` are required; the standard Google SDK build tools are generally x86_64 and will not execute natively on ARM Android.

```bash
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug
gradle :app:assembleRelease
```
