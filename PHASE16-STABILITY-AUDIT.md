# ScreenX AI — Phase 16: Stability Audit & Recording Save Fix

## Version
- `versionCode`: 6
- `versionName`: `0.16.0-phase16`

## Source audit and fix

The Phase 15 recording save path published MediaStore output by setting `IS_PENDING` to `0`, but it ignored the update result and any exception. In that case the UI could report a saved recording while the item remained pending or was not published.

Phase 16 changes:
- Check the MediaStore update result when publishing a completed recording.
- Delete the pending MediaStore row and raise an I/O failure if publication fails.
- Treat a missing legacy output file or a failed `.part` rename as an I/O failure.
- Convert finalization I/O failures into a failed recording result instead of reporting success.
- Add `scripts/validate-source.sh` for XML, version metadata, critical save-path guard, and shell syntax checks.

## Validation

Run from the project root:

```bash
./scripts/validate-source.sh
./scripts/check-build-env.sh
./scripts/build-debug.sh
```

The source validation script is static validation only. It does not compile Kotlin, run Android lint/unit tests, or prove device behavior.

## Not verified in this environment

- Gradle compilation, unit tests, lint, APK generation, APK signature, and installation.
- Recording behavior on any physical Android device.
- Android 8 through Android 15 device/API-specific behavior.

## Known limitations retained

- Custom save-tree URI is stored, but recording export still uses `Movies/ScreenX`.
- Watermark preference is stored, but watermark rendering is not connected to exported recordings.
- Language selection localizes Settings only.
- Camera overlay behavior depends on device support and runtime permissions.
- Release signing requires the owner's private keystore and credentials.

Phase 16 is a source-level stability fix, not a production-readiness certification.
