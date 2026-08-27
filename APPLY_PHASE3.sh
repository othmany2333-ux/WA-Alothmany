#!/usr/bin/env bash
set -euo pipefail

if [ ! -f "app/build.gradle.kts" ] || [ ! -d "app/src/main" ]; then
  echo "ERROR: Run this script from the WA-Alothmany repository root."
  exit 1
fi

python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET

required = [
    Path('app/src/main/java/com/alothmany/wa/feature/sync/SyncScreen.kt'),
    Path('app/src/main/java/com/alothmany/wa/feature/sync/SyncViewModel.kt'),
    Path('app/src/main/java/com/alothmany/wa/feature/sync/engine/SmartSyncEngine.kt'),
    Path('app/src/main/java/com/alothmany/wa/system/accessibility/WhatsAppUiBridge.kt'),
    Path('app/src/main/java/com/alothmany/wa/feature/sync/service/SmartSyncService.kt'),
]
missing = [str(p) for p in required if not p.exists()]
if missing:
    raise SystemExit('Missing Phase 3 files: ' + ', '.join(missing))

for xml in [
    Path('app/src/main/AndroidManifest.xml'),
    Path('app/src/main/res/values/sync_strings.xml'),
    Path('app/src/main/res/values-ar/sync_strings.xml'),
]:
    ET.parse(xml)

build = Path('app/build.gradle.kts').read_text()
if 'versionName = "0.3.0"' not in build or 'versionCode = 4' not in build:
    raise SystemExit('Phase 3 version metadata is not present')

database = Path('app/src/main/java/com/alothmany/wa/data/local/AppDatabase.kt').read_text()
if 'version = 2' not in database or 'GroupSyncMetaEntity::class' not in database:
    raise SystemExit('Phase 3 Room schema is not active')

manifest = Path('app/src/main/AndroidManifest.xml').read_text()
if 'android.permission.FOREGROUND_SERVICE_DATA_SYNC' not in manifest or 'SmartSyncService' not in manifest:
    raise SystemExit('Phase 3 foreground sync service is not registered')

engine = Path('app/src/main/java/com/alothmany/wa/feature/sync/engine/SmartSyncEngine.kt').read_text()
if 'rewindToTop' not in engine or 'END_CONFIRMATION_PASSES = 2' not in engine:
    raise SystemExit('Smart Sync completeness guards are missing')

# Keep the main dashboard truthful without replacing the user's v0.2.1 permission-center code.
dashboard = Path('app/src/main/java/com/alothmany/wa/feature/dashboard/DashboardScreen.kt')
if dashboard.exists():
    text = dashboard.read_text()
    text = text.replace('else R.string.phase_two_ready', 'else R.string.phase_three_ready')
    text = text.replace('stringResource(R.string.foundation_message)', 'stringResource(R.string.sync_foundation_message)')
    text = text.replace(
        'stringResource(R.string.sync),\n                stringResource(R.string.coming_next_phase),',
        'stringResource(R.string.sync),\n                stringResource(R.string.sync_ready_to_test),',
        1,
    )
    dashboard.write_text(text)

print('Phase 3 static checks: OK')
PY

git diff --check

git add \
  APPLY_PHASE3.sh \
  app/build.gradle.kts \
  app/src/main/AndroidManifest.xml \
  app/src/main/java/com/alothmany/wa/core/ui/WAAlOthmanyRoot.kt \
  app/src/main/java/com/alothmany/wa/data/local/AppDatabase.kt \
  app/src/main/java/com/alothmany/wa/data/local/entity/SyncEntities.kt \
  app/src/main/java/com/alothmany/wa/data/local/dao/SyncDaos.kt \
  app/src/main/java/com/alothmany/wa/di/AppModule.kt \
  app/src/main/java/com/alothmany/wa/system/accessibility/WAAccessibilityService.kt \
  app/src/main/java/com/alothmany/wa/system/accessibility/WhatsAppUiBridge.kt \
  app/src/main/java/com/alothmany/wa/system/overlay/OverlayControlService.kt \
  app/src/main/java/com/alothmany/wa/feature/sync \
  app/src/main/res/values/sync_strings.xml \
  app/src/main/res/values-ar/sync_strings.xml \
  app/src/main/java/com/alothmany/wa/feature/dashboard/DashboardScreen.kt \
  PHASE_3_README.md \
  PHASE_3_VALIDATION.md

if git diff --cached --quiet; then
  echo "No Phase 3 changes to commit."
  exit 0
fi

git commit -m "Add Smart Sync Engine v0.3.0"
git push origin main

echo "Phase 3 v0.3.0 pushed successfully. GitHub Actions will validate the Android build."
