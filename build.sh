#!/usr/bin/env bash
set -e
BASE="$(cd "$(dirname "$0")" && pwd)"
BASE_WIN="$(cd "$(dirname "$0")" && pwd -W 2>/dev/null || pwd)"
LIBS="$BASE_WIN/../Bridge-Plugin/libs"
SRC="$BASE/src/main/java"
OUT="$BASE_WIN/build/classes"
RES="$BASE_WIN/build/resources"
JAR_DIR="$BASE_WIN/build/libs"

# ── Version ───────────────────────────────────────────────────────────────────
VER_FILE="$BASE/version.properties"
[[ ! -f "$VER_FILE" ]] && echo "version=1.0.0" > "$VER_FILE"
VERSION=$(grep 'version=' "$VER_FILE" | cut -d'=' -f2 | tr -d '[:space:]')
echo "PlayerShop version: $VERSION"

# ── Directories ───────────────────────────────────────────────────────────────
rm -rf "$BASE/build/classes" "$BASE/build/resources"
mkdir -p "$BASE/build/classes" "$BASE/build/resources" "$BASE/build/libs"

# ── Resources: stamp version into plugin.yml ──────────────────────────────────
cp -r "$BASE/src/main/resources/"* "$BASE/build/resources/"
sed -i.bak "s/\${pluginVersion}/$VERSION/g" "$BASE/build/resources/plugin.yml" \
    && rm "$BASE/build/resources/plugin.yml.bak"

# ── Classpath separator: ; on Windows (Git Bash/MSYS), : on macOS/Linux ───────
case "$(uname -s 2>/dev/null)" in
    MINGW*|CYGWIN*|MSYS*) SEP=";" ;;
    *) SEP=":" ;;
esac

CP="${LIBS}/paper-api.jar${SEP}${LIBS}/vault-api.jar${SEP}${LIBS}/adventure-api.jar${SEP}${LIBS}/adventure-key.jar${SEP}${LIBS}/jetbrains-annotations.jar${SEP}${LIBS}/guava.jar${SEP}${LIBS}/examination-api.jar${SEP}${LIBS}/bungeecord-chat.jar"

# ── Compile ───────────────────────────────────────────────────────────────────
find "$SRC" -name "*.java" \
    | sed 's|^/\([a-zA-Z]\)/|\1:/|' \
    > "$BASE/build/sources.txt"
javac --release 21 -cp "$CP" -d "$OUT" @"$BASE_WIN/build/sources.txt"

# ── Package ───────────────────────────────────────────────────────────────────
JAR="$JAR_DIR/PlayerShop-${VERSION}.jar"
cd "$BASE/build/classes" && jar cf "$JAR" .
cd "$BASE/build/resources" && jar uf "$JAR" .
cd "$BASE"

echo "Built: $JAR ($(du -sh "$JAR" | cut -f1))"
