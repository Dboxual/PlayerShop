#!/usr/bin/env bash
set -e
BASE="$(cd "$(dirname "$0")" && pwd)"
LIBS="$BASE/../WaypointSystem/libs"
SRC="$BASE/src/main/java"
OUT="$BASE/build/classes"
RES="$BASE/build/resources"
JAR_DIR="$BASE/build/libs"

# ── Version ───────────────────────────────────────────────────────────────────
VER_FILE="$BASE/version.properties"
[[ ! -f "$VER_FILE" ]] && echo "version=1.0.0" > "$VER_FILE"
VERSION=$(grep 'version=' "$VER_FILE" | cut -d'=' -f2 | tr -d '[:space:]')
echo "PlayerShop version: $VERSION"

# ── Directories ───────────────────────────────────────────────────────────────
rm -rf "$OUT" "$RES"
mkdir -p "$OUT" "$RES" "$JAR_DIR"

# ── Resources: stamp version into plugin.yml ──────────────────────────────────
cp -r "$BASE/src/main/resources/"* "$RES/"
sed -i.bak "s/\${pluginVersion}/$VERSION/g" "$RES/plugin.yml" && rm "$RES/plugin.yml.bak"

# ── Classpath ─────────────────────────────────────────────────────────────────
CP="$LIBS/paper-api.jar:$LIBS/vault-api.jar:$LIBS/adventure-api.jar:$LIBS/adventure-key.jar:$LIBS/jetbrains-annotations.jar:$LIBS/guava.jar:$LIBS/examination-api.jar:$LIBS/bungeecord-chat.jar"

# ── Compile ───────────────────────────────────────────────────────────────────
find "$SRC" -name "*.java" > "$BASE/build/sources.txt"
javac --release 21 -cp "$CP" -d "$OUT" @"$BASE/build/sources.txt"

# ── Package ───────────────────────────────────────────────────────────────────
JAR="$JAR_DIR/PlayerShop-${VERSION}.jar"
cd "$OUT" && jar cf "$JAR" .
cd "$RES" && jar uf "$JAR" .
cd "$BASE"

echo "Built: $JAR ($(du -sh "$JAR" | cut -f1))"
