#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/build"
CLASSES="$BUILD/classes"
TEST_CLASSES="$BUILD/test-classes"

"$ROOT/scripts/build.sh"

echo "==> Compiling tests"
mkdir -p "$TEST_CLASSES"
find "$ROOT/src/test/java" -name '*.java' -print > "$BUILD/test-sources.txt"
javac --release 17 -encoding UTF-8 -cp "$CLASSES" -d "$TEST_CLASSES" @"$BUILD/test-sources.txt"

echo "==> Running calculator test suite"
java -cp "$CLASSES:$TEST_CLASSES" com.arithmetic.CalculatorTest

echo "==> All tests passed"
