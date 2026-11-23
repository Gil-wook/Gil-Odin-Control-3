#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT
BASE=app/src/main/java/dev/gil/odincontrol3
javac -J-Dfile.encoding=UTF-8 -encoding UTF-8 -classpath "$OUT" -d "$OUT" "$BASE"/RemapPolicy.java "$BASE"/PhysicalInputRouter.java "$BASE"/RecentAppsPolicy.java "$BASE"/VisibilityPolicy.java "$BASE"/GameDetector.java "$BASE"/WindowClassifier.java tests/PolicyTest.java tests/RouterTest.java tests/ParseJava.java
java -Dfile.encoding=UTF-8 -cp "$OUT" dev.gil.odincontrol3.PolicyTest
java -Dfile.encoding=UTF-8 -cp "$OUT" dev.gil.odincontrol3.RouterTest
java -Dfile.encoding=UTF-8 -cp "$OUT" ParseJava "$BASE"/*.java
python - <<'PY'
from pathlib import Path
from xml.etree import ElementTree as ET
for f in Path('app/src/main').rglob('*.xml'):
    ET.parse(f)
print('Android XML parsed')
PY

python tests/Check-PublicApis.py
