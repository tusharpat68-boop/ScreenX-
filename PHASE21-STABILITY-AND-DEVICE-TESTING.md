# ScreenX AI — Phase 21: Stability, Regression & Real-Device Validation

## Version
- `versionCode`: 11
- `versionName`: `0.21.0-phase21`
- Baseline: Phase 20 project archive.

## Phase 21 changes
- Added a dedicated stability audit covering manifest declarations, foreground-service types, exported component boundaries, FileProvider URI grants, CI wiring, version alignment, and signing/SDK-secret hygiene.
- Expanded domain regression tests for `QualityOptions` safe defaults and initial recording-state invariants.
- Updated the primary CI workflow to run the Phase 21 audit, unit tests, Android Lint, debug APK build, and unsigned release APK build.
- CI artifact name: `screenx-phase21-build-results`.

## Run checks locally
Required baseline: JDK 17, Gradle 8.9, Android SDK Platform 35, Build Tools 35.0.0.

```bash
./scripts/phase21-stability-audit.sh
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug
gradle :app:assembleRelease
```

## Real-device regression matrix
1. Fresh install and first launch; verify notification permission behavior on Android 13+.
2. Start and stop screen capture after accepting MediaProjection consent; repeat after denying consent.
3. Record with no audio, microphone, internal audio (where supported), and combined audio (where supported).
4. Test pause/resume if exposed by the device/API path; verify duration and MP4 playback.
5. Enable/disable floating controls and facecam; revoke overlay/camera permissions and verify graceful recovery.
6. Change orientation during recording and while browsing the library/editor.
7. Save to default Movies/ScreenX and a user-selected SAF folder; test provider cancellation and unavailable-folder errors.
8. Export edited video with watermark on/off; compare the actual rendered output.
9. Rename, share, open, and delete MediaStore and SAF recordings.
10. Test low storage, app background/foreground, process recreation, and repeated start/stop cycles.
11. Confirm no zero-byte/corrupt files, stuck foreground notification, crash, or silent destination fallback.

## Validation status for this archive
- Phase 21 static audit and shell syntax checks are executed during packaging.
- Gradle/Kotlin compilation, unit test execution, Android Lint, APK generation/signature verification, emulator tests, and physical-device tests are not claimed unless produced by CI or a local Android build environment.
- No release keystore or signing credentials are included.

A successful static audit is not a production-readiness claim. Promote the app only after CI build/test/lint succeeds and the device matrix is completed.
