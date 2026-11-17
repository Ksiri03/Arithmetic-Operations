#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"

rm -rf "$BUILD"
mkdir -p "$CLASSES"

echo "==> Compiling Java sources"
find "$ROOT/src/main/java" -name '*.java' -print > "$BUILD/sources.txt"
javac --release 17 -encoding UTF-8 -d "$CLASSES" @"$BUILD/sources.txt"

echo "==> Copying static resources"
cp -R "$ROOT/src/main/resources/." "$CLASSES/"

echo "==> Packaging executable jar"
jar --create --file "$BUILD/arithmetic-ops.jar" --main-class com.arithmetic.App -C "$CLASSES" .

echo "==> Built build/arithmetic-ops.jar"
