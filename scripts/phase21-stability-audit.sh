#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo '=== ScreenX AI Phase 21 stability audit ==='
./scripts/validate-source.sh

python3 - <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

checks = {}
manifest = ET.parse('app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
permissions = {node.attrib.get(ns + 'name') for node in manifest.findall('uses-permission')}
required_permissions = {
    'android.permission.FOREGROUND_SERVICE',
    'android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION',
    'android.permission.RECORD_AUDIO',
    'android.permission.FOREGROUND_SERVICE_MICROPHONE',
    'android.permission.CAMERA',
    'android.permission.FOREGROUND_SERVICE_CAMERA',
}
checks['screen capture/audio/camera foreground permissions declared'] = required_permissions.issubset(permissions)
application = manifest.find('application')
services = {node.attrib.get(ns + 'name'): node for node in application.findall('service')}
recording = services.get('.service.RecordingService')
screenshot = services.get('.screenshot.ScreenshotService')
checks['recording service is non-exported'] = recording is not None and recording.attrib.get(ns + 'exported') == 'false'
checks['recording service declares required foreground types'] = recording is not None and set(recording.attrib.get(ns + 'foregroundServiceType', '').split('|')) == {'mediaProjection', 'microphone', 'camera'}
checks['screenshot service is non-exported'] = screenshot is not None and screenshot.attrib.get(ns + 'exported') == 'false'
provider = application.find('provider')
checks['FileProvider is non-exported and grants URI permissions'] = provider is not None and provider.attrib.get(ns + 'exported') == 'false' and provider.attrib.get(ns + 'grantUriPermissions') == 'true'
activity = application.find('activity')
checks['launcher activity is explicitly exported'] = activity is not None and activity.attrib.get(ns + 'exported') == 'true'
checks['debug and release build types exist'] = all(x in Path('app/build.gradle.kts').read_text() for x in ('debug {', 'release {'))
checks['Phase 21 CI workflow exists'] = Path('.github/workflows/build.yml').is_file() and 'phase21' in Path('.github/workflows/build.yml').read_text().lower()
checks['Phase 21 test notes exist'] = Path('PHASE21-STABILITY-AND-DEVICE-TESTING.md').is_file()
checks['version metadata is Phase 21'] = 'versionCode = 11' in Path('app/build.gradle.kts').read_text() and 'versionName = "0.21.0-phase21"' in Path('app/build.gradle.kts').read_text()
files = [p for p in Path('.').rglob('*') if p.is_file() and '.git' not in p.parts and 'build' not in p.parts]
checks['no private signing keystore included'] = not any(p.suffix.lower() in {'.jks', '.keystore', '.p12', '.pfx'} for p in files)
checks['no local Android SDK path included'] = not Path('local.properties').exists()
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    sys.exit(1)
print('Phase 21 static stability audit passed. Kotlin compilation and device runtime are separate release gates.')
PY

for script in scripts/*.sh; do
  bash -n "$script"
  echo "PASS: shell syntax $script"
done
