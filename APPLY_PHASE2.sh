#!/usr/bin/env bash
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

echo "[1/4] Validating WA Al-othmany Phase 2 patch..."
python3 - <<'PY'
from pathlib import Path
import xml.etree.ElementTree as ET
import tomllib

required = [
    Path("app/src/main/AndroidManifest.xml"),
    Path("app/src/main/aidl/com/alothmany/wa/system/shizuku/ITurboUserService.aidl"),
    Path("app/src/main/java/com/alothmany/wa/system/shizuku/ShizukuController.kt"),
    Path("app/src/main/java/com/alothmany/wa/system/accessibility/WAAccessibilityService.kt"),
    Path("app/src/main/java/com/alothmany/wa/system/overlay/OverlayControlService.kt"),
    Path("app/src/main/java/com/alothmany/wa/system/whatsapp/WhatsAppSourceDetector.kt"),
]
missing = [str(p) for p in required if not p.exists()]
if missing:
    raise SystemExit("Missing patch files: " + ", ".join(missing))

for path in [
    "app/src/main/AndroidManifest.xml",
    "app/src/main/res/xml/accessibility_service_config.xml",
    "app/src/main/res/values/strings.xml",
    "app/src/main/res/values-ar/strings.xml",
]:
    ET.parse(path)

with open("gradle/libs.versions.toml", "rb") as f:
    versions = tomllib.load(f)["versions"]
assert versions.get("shizuku") == "13.1.5"

print("Phase 2 patch structure: OK")
PY

echo "[2/4] Staging only Phase 2 files..."
git add \
  PHASE_2_README.md \
  APPLY_PHASE2.sh \
  .github/workflows/android.yml \
  gradle/libs.versions.toml \
  app/build.gradle.kts \
  app/proguard-rules.pro \
  app/src/main/AndroidManifest.xml \
  app/src/main/aidl \
  app/src/main/java/com/alothmany/wa/MainActivity.kt \
  app/src/main/java/com/alothmany/wa/data/local/dao/Daos.kt \
  app/src/main/java/com/alothmany/wa/feature/dashboard \
  app/src/main/java/com/alothmany/wa/feature/settings \
  app/src/main/java/com/alothmany/wa/feature/diagnostics \
  app/src/main/java/com/alothmany/wa/system \
  app/src/main/res/values/strings.xml \
  app/src/main/res/values-ar/strings.xml \
  app/src/main/res/xml/accessibility_service_config.xml

if git diff --cached --quiet; then
  echo "No Phase 2 changes to commit."
  exit 0
fi

echo "[3/4] Creating commit..."
git commit -m "Phase 2 real system integration v0.2.0"

echo "[4/4] Pushing to GitHub main..."
git push origin main

echo "DONE: GitHub Actions should start automatically."
