#!/usr/bin/env bash
set -euo pipefail

ROOT="$(git rev-parse --show-toplevel 2>/dev/null || true)"
if [ -z "$ROOT" ]; then
  echo "ERROR: Run this script inside the WA-Alothmany Git repository."
  exit 1
fi
cd "$ROOT"

python3 <<'PY'
from pathlib import Path

# 1) Remove Sync -> Extract/Publish/Join/Delete UI wiring completely.
screen = Path("app/src/main/java/com/alothmany/wa/feature/sync/SyncScreen.kt")
text = screen.read_text(encoding="utf-8")
marker = 'SectionTitle(stringResource(R.string.link_sync_operations))'
if marker in text:
    marker_pos = text.index(marker)
    start = text.rfind('            GlassCard(accent = Gold400) {', 0, marker_pos)
    end_marker = '            if (state.runtime.status != SyncEngineStatus.IDLE) {'
    end = text.find(end_marker, marker_pos)
    if start < 0 or end < 0:
        raise SystemExit("Could not safely locate linked-operations block in SyncScreen.kt")
    text = text[:start] + text[end:]
text = text.replace('import com.alothmany.wa.core.navigation.Destination\n', '')
screen.write_text(text, encoding="utf-8")

# 2) Bump app version.
build = Path("app/build.gradle.kts")
b = build.read_text(encoding="utf-8")
b = b.replace('versionCode = 4', 'versionCode = 5')
b = b.replace('versionName = "0.3.0"', 'versionName = "0.3.1"')
build.write_text(b, encoding="utf-8")

# 3) Give the CI artifact the correct version name.
workflow = Path(".github/workflows/android.yml")
w = workflow.read_text(encoding="utf-8")
w = w.replace('name: wa-al-othmany-debug-v0.2', 'name: wa-al-othmany-debug-v0.3.1')
workflow.write_text(w, encoding="utf-8")
PY

# Safety checks: no old group-filter automation and no Sync operation wiring.
if grep -q 'GROUP_FILTER_LABELS' app/src/main/java/com/alothmany/wa/feature/sync/engine/SmartSyncEngine.kt; then
  echo "ERROR: Old Groups-filter automation still exists."
  exit 1
fi
if grep -q 'prepareLinkedOperation' app/src/main/java/com/alothmany/wa/feature/sync/SyncViewModel.kt; then
  echo "ERROR: Old linked-operation wiring still exists."
  exit 1
fi
if grep -q 'link_sync_operations' app/src/main/java/com/alothmany/wa/feature/sync/SyncScreen.kt; then
  echo "ERROR: Linked operation card still exists in SyncScreen."
  exit 1
fi
if ! grep -q 'ARCHIVED_LABELS' app/src/main/java/com/alothmany/wa/feature/sync/engine/SmartSyncEngine.kt; then
  echo "ERROR: Archived scan support is missing."
  exit 1
fi
if ! grep -q 'scrollPrimaryListForward' app/src/main/java/com/alothmany/wa/feature/sync/engine/SmartSyncEngine.kt; then
  echo "ERROR: Primary chat-list scrolling is missing."
  exit 1
fi
if ! grep -q 'versionName = "0.3.1"' app/build.gradle.kts; then
  echo "ERROR: Version bump failed."
  exit 1
fi

# Stage only v0.3.1 changes.
git add \
  app/src/main/java/com/alothmany/wa/feature/sync/engine/SmartSyncEngine.kt \
  app/src/main/java/com/alothmany/wa/feature/sync/engine/WhatsAppGroupParser.kt \
  app/src/main/java/com/alothmany/wa/feature/sync/model/SyncModels.kt \
  app/src/main/java/com/alothmany/wa/feature/sync/SyncViewModel.kt \
  app/src/main/java/com/alothmany/wa/feature/sync/SyncScreen.kt \
  app/src/main/java/com/alothmany/wa/system/accessibility/WhatsAppUiBridge.kt \
  app/src/main/java/com/alothmany/wa/system/accessibility/WAAccessibilityService.kt \
  app/src/test/java/com/alothmany/wa/feature/sync/engine/WhatsAppGroupParserTest.kt \
  app/build.gradle.kts \
  .github/workflows/android.yml \
  APPLY_SYNC_V031.sh \
  V0.3.1_CHANGES_AR.txt

git diff --cached --check

if git diff --cached --quiet; then
  echo "No changes to commit. v0.3.1 may already be applied."
  exit 0
fi

git commit -m "Make Smart Sync independent and scan chats plus archived v0.3.1"
git push origin main

echo
echo "SUCCESS: Smart Sync v0.3.1 applied and pushed."
echo "GitHub Actions will build and run unit tests automatically."
