#!/usr/bin/env bash
# Self-checks for the parts of the mod that do not need Minecraft: the flight simulator, the glTF reader / mesh splitter,
# and the multi-missile lockstep test. Needs a JDK 21 and any gson 2.x jar.
#   tools/run-checks.sh [path-to-glb-folder-or-original-pack]
set -euo pipefail
cd "$(dirname "$0")/.."
GSON="${GSON:-$( (ls /usr/share/java/gson*.jar ~/.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson/*/*/gson-*.jar 2>/dev/null || true) | grep -v sources | head -1 || true)}"
[ -n "$GSON" ] || { echo "Set GSON=/path/to/gson-2.x.jar"; exit 2; }
GLBS="${1:-src/main/resources/assets/missilesnukes/models/glb}"
rm -rf build/checks && mkdir -p build/checks
javac -d build/checks -cp "$GSON" $(find src/main/java/dev/missilesnukes/sim src/main/java/dev/missilesnukes/glb tools/checks -name '*.java')
echo "=== flight simulation ===";  java -cp build/checks dev.missilesnukes.sim.SimChecks
echo; echo "=== 8 simultaneous missiles ==="; java -cp build/checks dev.missilesnukes.sim.MultiMissileCheck
echo; echo "=== glTF reader / stage splitter (on $GLBS) ==="; java -cp "build/checks:$GSON" dev.missilesnukes.glb.GlbChecks "$GLBS"
