# ScreenX AI — Phase 18: Custom Recording Folder Integration

## Version
- `versionCode`: 8
- `versionName`: `0.18.0-phase18`

## Implemented
- The Settings folder picker now persists the selected Storage Access Framework (SAF) tree permission only when the persistable permission call succeeds.
- The foreground recording service reads the selected tree URI at recording start and passes it into the recording engine.
- `RecordingStorage` creates a named `video/mp4` document inside the selected folder and opens a file descriptor for MediaRecorder / MediaMuxer.
- Finalization closes the writer, checks that the document is readable and non-empty, then reports the document URI as the saved recording.
- Failed creation/finalization paths delete the incomplete SAF document.
- Clearing the preference restores the default destination: MediaStore `Movies/ScreenX` on Android 10+, app-specific Movies/ScreenX on Android 8–9.
- If a selected provider/tree is unavailable or permission is revoked, recording fails explicitly. It does not silently save to a different folder.

## Deliberate limitations
- Editor exports and extracted-audio exports still use their existing `Movies/ScreenX` / `Music/ScreenX` destinations; the selected SAF folder is currently wired to new screen recordings only.
- The in-app MediaStore library is still scoped to its existing MediaStore paths and does not yet browse arbitrary SAF tree documents. A custom-folder recording can be opened from the selected folder in the system Files app.
- Watermark remains a stored preference only. This phase does not render/burn a watermark into video. Doing that correctly requires a video compositing/encoding stage; merely displaying a floating label over the app would not guarantee it appears in the captured output.
- Provider-specific SAF behavior varies. Real-device testing is required, especially for cloud-backed and removable-storage providers.

## Validation performed
- Source-level checks cover SAF document creation, descriptor opening, final non-empty validation, rollback deletion, service-to-engine URI handoff, and Phase 18 version metadata.
- Shell scripts are checked with `bash -n`.
- Android Manifest is parsed as XML.
- ZIP integrity is checked after packaging.

## Not performed
- Kotlin/Gradle compilation, unit tests, Android Lint, APK generation/signing, or physical-device tests.
- Do not claim production readiness until a real device records, closes, and plays a video in the chosen folder, with both audio-off and audio-on paths.

## Device test checklist
1. Choose an internal-storage folder using Settings > Preferred folder.
2. Record a short video with audio off; stop and open it from the Files app.
3. Repeat with microphone audio and verify playback.
4. Repeat on an SD card or another SAF provider if supported.
5. Revoke folder access or remove the storage and verify the app reports a failure rather than silently switching destinations.
6. Clear the folder preference and confirm the default Movies/ScreenX path is restored.
7. Confirm editor exports still use Movies/ScreenX and watermark is not yet applied.
