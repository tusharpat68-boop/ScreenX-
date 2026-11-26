#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo '=== ScreenX AI Phase 23 stability audit ==='
./scripts/validate-source.sh

python3 - <<'PY'
from pathlib import Path
import sys, xml.etree.ElementTree as ET

build = Path('app/build.gradle.kts').read_text()
validator = Path('app/src/main/java/com/screenx/ai/domain/RecordingConfigValidator.kt').read_text()
engine = Path('app/src/main/java/com/screenx/ai/recording/ScreenRecorderEngine.kt').read_text()
tests = Path('app/src/test/java/com/screenx/ai/domain/RecordingConfigValidatorTest.kt').read_text()
workflow = Path('.github/workflows/build.yml').read_text()
manifest = ET.parse('app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
app = manifest.find('application')
services = {s.attrib.get(ns+'name'): s for s in app.findall('service')}
recording = services.get('.service.RecordingService')
provider = app.find('provider')

checks = {
  'Phase 23 version metadata': 'versionCode = 13' in build and 'versionName = "0.23.0-phase23"' in build,
  'config validator checks resolution and fps': 'config.resolutionP !in RecordingConfig.RESOLUTIONS' in validator and 'config.fps !in RecordingConfig.FPS_OPTIONS' in validator,
  'config validator checks video and audio bitrate': 'config.bitrateKbps < 0' in validator and 'config.audioBitrateKbps !in RecordingConfig.AUDIO_BITRATES' in validator,
  'recording engine validates before resolving/allocating': engine.index('RecordingConfigValidator.errors(config)') < engine.index('ConfigResolver.resolve(context, config)'),
  'regression tests cover defaults and invalid values': all(x in tests for x in ('defaultConfigIsValid', 'rejectsUnsupportedResolutionAndFrameRate', 'rejectsNegativeAndExcessiveVideoBitrate', 'audioBitrateIsCheckedOnlyWhenAudioIsEnabled')),
  'CI runs unit tests and both lint variants': 'gradle :app:testDebugUnitTest' in workflow and 'gradle :app:lintDebug :app:lintRelease' in workflow,
  'CI assembles debug and release APKs': 'gradle :app:assembleDebug' in workflow and 'gradle :app:assembleRelease' in workflow,
  'recording service remains non-exported': recording is not None and recording.attrib.get(ns+'exported') == 'false',
  'FileProvider remains non-exported with URI grants': provider is not None and provider.attrib.get(ns+'exported') == 'false' and provider.attrib.get(ns+'grantUriPermissions') == 'true',
  'release signing is not hardcoded': 'SCREENX_KEYSTORE_PASSWORD' in build and 'if (hasReleaseSigning)' in build,
}
for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    sys.exit(1)
print('Phase 23 static stability audit passed. Kotlin/Gradle build and device tests are not implied.')
PY

for script in scripts/*.sh; do
  bash -n "$script"
  echo "PASS: shell syntax $script"
done
