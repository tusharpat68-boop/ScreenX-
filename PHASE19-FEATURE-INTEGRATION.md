# ScreenX AI — Phase 19: Export, Watermark & Library Integration

## Version
- versionCode: 9
- versionName: 0.19.0-phase19

## Implemented in source
- Edited video exports use the persisted Storage Access Framework folder when one is selected.
- Extracted audio remains in Music/ScreenX; it is not placed in the selected video folder.
- SAF export copies are checked for non-empty readable output; failed copies attempt to delete the partial document.
- The Editor reads the saved watermark preference. When enabled, a `ScreenX AI` text overlay is added to the Media3 Transformer video-effect chain for edited video exports.
- The watermark is not burned into live screen recordings in this phase.
- The Recordings library queries MP4 children in the selected SAF folder in addition to the default library. SAF entries support open/share through their document URI, rename, and delete when the document provider permits those operations.
- Provider failures in the custom folder query do not hide the default MediaStore library.

## Deliberate limitations
- The SAF library integration lists MP4 files directly in the selected folder; it does not recursively crawl subfolders.
- Cloud-backed SAF providers may omit size/date/metadata or delay availability. Rename/delete support varies by provider.
- Watermark appearance/position and export compatibility must be verified on real devices and representative video sizes.
- Live screen recordings are still not watermarked. Applying a watermark to live recording requires a compositor in the recording pipeline, not a UI label.
- Extracted audio continues to use Music/ScreenX, not the selected video folder.

## Validation performed
- Source-level assertions for SAF export, rollback, watermark effect wiring, SAF library query, and version metadata.
- Android Manifest XML parsing and shell script syntax checks.
- ZIP integrity check after packaging.

## Not performed
- Kotlin/Gradle compilation, unit tests, lint, APK build/signing, emulator testing, or physical-device testing.
- Do not claim the app compiles or that watermark/export works on a device until these checks pass.

## Device test checklist
1. Select a local internal-storage folder and record a short video; confirm it appears in Recordings.
2. Open that recording in Editor and export with watermark off; confirm no watermark is visible.
3. Enable watermark, export again, and verify `ScreenX AI` is visibly burned into the output near the lower-right corner.
4. Confirm edited video export appears in the selected folder and is also listed in Recordings.
5. Rename, share, play, and delete a custom-folder item.
6. Test an SD card or cloud provider if supported; check provider errors are reported safely.
7. Extract audio and confirm it remains in Music/ScreenX.
8. Clear the custom folder and confirm default Movies/ScreenX export behavior.
9. Confirm live screen recordings do not claim to have a burned-in watermark.
