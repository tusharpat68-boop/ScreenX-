# ScreenX AI — Phase 20: Full Integration Audit & Final APK Preparation

## Version
- `versionCode`: 10
- `versionName`: `0.20.0-phase20`
- Baseline: Phase 19 source archive supplied for this phase.

## Phase 20 changes
- Advanced Android app version metadata to Phase 20.
- Added `scripts/phase20-audit.sh`, which runs the existing Phase 19 source checks and verifies key project/build/release files, version alignment, and absence of local signing/SDK secrets.
- Updated the primary GitHub Actions build workflow to run the Phase 20 audit before unit tests, Android Lint, debug APK build, and unsigned release APK build.
- Renamed the CI artifact to `screenx-phase20-build-results`.
- Added this integration audit and final-build checklist.

## Run locally
Required environment: JDK 17, Gradle 8.9, Android SDK Platform 35, Build Tools 35.0.0.

```bash
./scripts/phase20-audit.sh
gradle :app:testDebugUnitTest
gradle :app:lintDebug
gradle :app:assembleDebug
gradle :app:assembleRelease
```

Debug APK: `app/build/outputs/apk/debug/`
Release APK: `app/build/outputs/apk/release/` (unsigned unless all four `SCREENX_*` signing variables are securely configured).

## Validation performed while preparing Phase 20 archive
- Phase 19 baseline source validator passed before Phase 20 changes.
- Phase 20 source audit and shell syntax checks are run before packaging (see the delivered QA results).
- ZIP integrity is checked after packaging.

## Not claimed / release gates
- Kotlin/Gradle compilation, unit tests, Android Lint, APK generation, signing, emulator tests, and physical-device tests are not claimed unless a successful CI/local build log or device result is attached.
- The preparation environment may not have Gradle or Android SDK installed. A source audit cannot substitute for compilation.
- No release keystore or credentials are included.

## Required device validation
1. Install the debug APK on Android 8+ and confirm first launch and permission flows.
2. Start, pause/resume if supported, and stop screen recording; verify the MP4 plays with expected orientation and duration.
3. Test microphone audio and internal audio where Android/device policy permits; verify silent recording still works.
4. Test countdown, floating controls, facecam, and drawing overlay permissions and lifecycle.
5. Verify saved files in default `Movies/ScreenX` and a user-selected SAF folder.
6. Open a recording in the editor and export with watermark disabled and enabled; verify the actual output video.
7. Test Recordings library playback/open/share/rename/delete for MediaStore and selected SAF folder.
8. Extract audio and confirm its documented `Music/ScreenX` destination.
9. Rotate the device, background/foreground the app, deny/revoke permissions, and test low-storage/provider-failure behavior.
10. Confirm no crashes, leaked foreground notification, corrupted/zero-byte files, or silent fallback to an unintended storage destination.

## Phase 20 outcome
Phase 20 is an integration-audit and build-preparation increment. It does not assert production readiness. Mark the release ready only after CI passes all build/test/lint jobs and the device checklist is completed on representative Android devices/providers.
