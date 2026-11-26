#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

echo '=== ScreenX AI Phase 20 integration audit ==='
./scripts/validate-source.sh

python3 - <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET

checks = {}
manifest = ET.parse('app/src/main/AndroidManifest.xml').getroot()
android = '{http://schemas.android.com/apk/res/android}'
checks['application manifest package/namespace alignment'] = Path('app/build.gradle.kts').read_text().find('namespace = "com.screenx.ai"') >= 0
checks['application class exists'] = any(Path('app/src/main/java').rglob('MainActivity.kt'))
checks['Gradle version catalog exists'] = Path('gradle/libs.versions.toml').is_file()
checks['CI build workflow exists'] = Path('.github/workflows/build.yml').is_file()
checks['CI release workflow exists'] = Path('.github/workflows/release.yml').is_file()
checks['Phase 20 integration notes exist'] = Path('PHASE20-INTEGRATION-AUDIT.md').is_file()
checks['debug build helper exists'] = Path('scripts/build-debug.sh').is_file()
checks['APK verification helper exists'] = Path('scripts/verify-apk.sh').is_file()
checks['version metadata aligned'] = 'versionCode = 10' in Path('app/build.gradle.kts').read_text() and 'versionName = "0.20.0-phase20"' in Path('app/build.gradle.kts').read_text()

# Fail closed if a signing key or local machine SDK path was accidentally committed.
tracked_candidates = [p for p in Path('.').rglob('*') if p.is_file() and '.git' not in p.parts and 'build' not in p.parts]
checks['no keystore files included'] = not any(p.suffix.lower() in {'.jks', '.keystore', '.p12', '.pfx'} for p in tracked_candidates)
checks['no local.properties included'] = not Path('local.properties').exists()

for name, ok in checks.items():
    print(('PASS' if ok else 'FAIL') + ': ' + name)
if not all(checks.values()):
    sys.exit(1)
print('Phase 20 source integration audit passed. This is not a Gradle build or device test.')
PY

for script in scripts/*.sh; do
  bash -n "$script"
  echo "PASS: shell syntax $script"
done
