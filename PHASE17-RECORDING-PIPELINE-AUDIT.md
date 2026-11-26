# ScreenX AI — Phase 17: Recording Pipeline Audit & Export Integrity

## Version

- `versionCode`: 7
- `versionName`: `0.17.0-phase17`

## Source audit findings

The Phase 16 archive already guarded the recording finalization path: MediaStore publication must update at least one row, and legacy `.part` recordings must be renamed successfully. This phase reviewed the adjacent editor-export path and found it did not apply equivalent checks.

## Phase 17 changes

### Editor export integrity

`ExportStorage` now:

- Rejects a missing, empty, or unnamed source before creating a destination.
- Checks that a MediaStore copy wrote a positive number of bytes.
- Checks the `IS_PENDING = 0` publication update result.
- Deletes the pending MediaStore row if copying or publication fails.
- Writes Android 8–9 exports to a temporary `.part` file, flushes it to disk, checks that it is non-empty, and only then renames it to the final name.
- Deletes the temporary legacy export if finalization fails.

The existing editor API is preserved: `ExportStorage.save()` still returns `null` on failure and a `Saved` result on success.

### Static validation

`scripts/validate-source.sh` now verifies the Phase 17 version and checks for the new export guards in addition to the existing manifest, recording-publication, and shell syntax checks.

## Validation performed for this archive

- Phase 16 ZIP extracted successfully.
- `./scripts/validate-source.sh` completed successfully.
- All shell scripts passed `bash -n`.
- Android Manifest parsed as well-formed XML.
- Static checks confirmed empty-source rejection, MediaStore publication-result checking, rollback, and legacy temporary-file finalization.
- Build environment preflight found Java, but Gradle and ADB are missing; no Android SDK path is configured.

## Not performed

- Kotlin compilation, Gradle unit tests, Android Lint, APK generation, signing, or installation.
- Physical-device recording, audio synchronization, editor export playback, or Android 8–15 compatibility testing.

## Known limitations carried forward

- Custom save-tree URI is stored but recording export still uses `Movies/ScreenX`.
- Watermark preference is stored but watermark rendering is not connected to exported recordings.
- Language selection localizes Settings only.
- Camera overlay behavior depends on device support and runtime permissions.
- Release signing requires the owner's private keystore and credentials.

Phase 17 is a source-level export-integrity improvement, not a successful build or production-readiness certification.

## Recommended physical-device test order

1. Build and install the debug APK using the Phase 14/15 scripts.
2. Record a short silent video; stop and confirm it appears in `Movies/ScreenX` and plays.
3. Repeat with microphone audio and, on supported Android 10+ devices, internal audio.
4. Open a recording in the editor, export a trimmed copy, and confirm the copy opens and plays.
5. Extract audio from a source that has audio and confirm the resulting file plays.
6. Test low-storage and interrupted-export behavior; verify that failed exports are not shown as successful and no `.part` files remain.
7. Repeat on Android 8–9 if legacy support is required, then test on Android 10+ MediaStore behavior.

Record model, Android version, build fingerprint, test date, and PASS / FAIL / NOT TESTED for every item. Do not mark a test PASS without performing it on a device.
