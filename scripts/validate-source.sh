#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

python3 - <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

manifest = Path('app/src/main/AndroidManifest.xml')
ET.parse(manifest)
print('PASS: AndroidManifest.xml is well-formed XML')

build = Path('app/build.gradle.kts').read_text()
checks = {
    'namespace': 'namespace = "com.screenx.ai"' in build,
    'applicationId': 'applicationId = "com.screenx.ai"' in build,
    'Phase 23 version': 'versionName = "0.23.0-phase23"' in build and 'versionCode = 13' in build,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    sys.exit(1)

storage = Path('app/src/main/java/com/screenx/ai/storage/RecordingStorage.kt').read_text()
engine = Path('app/src/main/java/com/screenx/ai/recording/ScreenRecorderEngine.kt').read_text()
if 'if (updated <= 0)' not in storage or 'catch (e: IOException)' not in engine:
    print('FAIL: recording publish failure handling is missing')
    sys.exit(1)
print('PASS: recording publish failure guard is present')

phase18_checks = {
    'SAF document creation': 'DocumentsContract.createDocument' in storage,
    'SAF output descriptor': 'openFileDescriptor(documentUri, mode)' in storage,
    'SAF finalization validates non-empty file': 'Selected-folder recording is empty or unreadable' in storage and 'openInputStream(out.uri)' in storage,
    'SAF rollback deletes document': 'DocumentsContract.deleteDocument' in storage,
    'recording engine receives selected folder': 'customTreeUri = customSaveTreeUri' in Path('app/src/main/java/com/screenx/ai/recording/ScreenRecorderEngine.kt').read_text(),
    'service reads saved folder preference': 'settings.saveTreeUri.first()' in Path('app/src/main/java/com/screenx/ai/service/RecordingService.kt').read_text(),
}
for name, ok in phase18_checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(phase18_checks.values()):
    sys.exit(1)

exports = Path('app/src/main/java/com/screenx/ai/storage/ExportStorage.kt').read_text()
export_checks = {
    'empty export rejected': 'source.length() <= 0L' in exports,
    'MediaStore publish result checked': 'if (updated <= 0)' in exports,
    'failed MediaStore export rolled back': 'resolver.delete(uri, null, null)' in exports,
    'legacy export uses temporary file': 'File(dir, "$name.part")' in exports,
    'legacy export checks final rename': 'if (!part.renameTo(target))' in exports,
}
for name, ok in export_checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(export_checks.values()):
    sys.exit(1)
PY

python3 - <<'PY'
from pathlib import Path
import sys
exports = Path('app/src/main/java/com/screenx/ai/storage/ExportStorage.kt').read_text()
editor = Path('app/src/main/java/com/screenx/ai/presentation/editor/EditorViewModel.kt').read_text()
library = Path('app/src/main/java/com/screenx/ai/storage/MediaLibrary.kt').read_text()
vm = Path('app/src/main/java/com/screenx/ai/presentation/library/LibraryViewModel.kt').read_text()
checks = {
 'SAF editor video export': 'DocumentsContract.createDocument' in exports and 'customTreeUri' in exports,
 'SAF export rollback': 'DocumentsContract.deleteDocument' in exports,
 'watermark transformer overlay': 'OverlayEffect' in editor and 'createWatermarkOverlay' in editor,
 'watermark preference used': 'settings.watermarkEnabled.collect' in editor,
 'SAF library listing': 'querySafTree' in library and 'buildChildDocumentsUriUsingTree' in library,
 'library reads selected folder': 'settings.saveTreeUri.first()' in vm,
}
for name, ok in checks.items(): print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()): sys.exit(1)
PY

for script in scripts/*.sh; do
  bash -n "$script"
  echo "PASS: shell syntax $script"
done

echo 'Source validation passed. This does NOT compile Kotlin or build an APK.'
