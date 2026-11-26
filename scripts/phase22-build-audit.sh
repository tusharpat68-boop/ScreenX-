#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"
echo '=== ScreenX AI Phase 22 build-readiness audit ==='
./scripts/validate-source.sh
python3 - <<'PY2'
from pathlib import Path
import sys, xml.etree.ElementTree as ET
checks={}
gradle=Path('app/build.gradle.kts').read_text(); catalog=Path('gradle/libs.versions.toml').read_text()
manifest=ET.parse('app/src/main/AndroidManifest.xml').getroot(); ns='{http://schemas.android.com/apk/res/android}'; app=manifest.find('application')
checks['Phase 22 versionCode/versionName aligned']='versionCode = 13' in gradle and 'versionName = "0.23.0-phase23"' in gradle
checks['compileSdk/minSdk/targetSdk declared']=all(x in gradle for x in ('compileSdk = 35','minSdk = 26','targetSdk = 35'))
checks['AGP/Kotlin/KSP versions pinned']=all(x in catalog for x in ('agp = "8.7.3"','kotlin = "2.0.21"','ksp = "2.0.21-1.0.28"'))
checks['Java and Kotlin target 17']='sourceCompatibility = JavaVersion.VERSION_17' in gradle and 'jvmTarget = "17"' in gradle
checks['unit test source exists']=Path('app/src/test/java/com/screenx/ai/domain/DomainModelTest.kt').is_file()
checks['debug and release build types configured']='debug {' in gradle and 'release {' in gradle
checks['release signing remains opt-in']='if (hasReleaseSigning)' in gradle and 'SCREENX_KEYSTORE_PATH' in gradle
checks['Phase 22 CI workflow exists']=Path('.github/workflows/build.yml').is_file() and 'Phase 22' in Path('.github/workflows/build.yml').read_text()
checks['APK verification helper exists']=Path('scripts/verify-apk.sh').is_file()
checks['device install helper exists']=Path('scripts/install-debug-device.sh').is_file()
services={n.attrib.get(ns+'name'):n for n in app.findall('service')}; rec=services.get('.service.RecordingService')
checks['recording service remains private']=rec is not None and rec.attrib.get(ns+'exported')=='false'
checks['MediaProjection foreground type remains declared']=rec is not None and 'mediaProjection' in rec.attrib.get(ns+'foregroundServiceType','')
provider=app.find('provider'); checks['FileProvider private and URI grants enabled']=provider is not None and provider.attrib.get(ns+'exported')=='false' and provider.attrib.get(ns+'grantUriPermissions')=='true'
checks['SAF recording and editor export integration present']='DocumentsContract.createDocument' in Path('app/src/main/java/com/screenx/ai/storage/RecordingStorage.kt').read_text() and 'DocumentsContract.createDocument' in Path('app/src/main/java/com/screenx/ai/storage/ExportStorage.kt').read_text()
checks['no local SDK path or signing keystore packaged']=not Path('local.properties').exists() and not any(p.suffix.lower() in {'.jks','.keystore','.p12','.pfx'} for p in Path('.').rglob('*') if p.is_file() and '.git' not in p.parts and 'build' not in p.parts)
for name,ok in checks.items(): print(('PASS' if ok else 'FAIL')+': '+name)
if not all(checks.values()): sys.exit(1)
print('Phase 22 static build-readiness audit passed. This does not compile Kotlin or create an APK.')
PY2
for script in scripts/*.sh; do bash -n "$script"; echo "PASS: shell syntax $script"; done
