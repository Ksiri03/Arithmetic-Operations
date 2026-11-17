#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAR="$ROOT/build/arithmetic-ops.jar"

if [ ! -f "$JAR" ]; then
  "$ROOT/scripts/build.sh"
fi

PORT="${PORT:-8080}"
echo "Starting Arithmetic Operations on http://localhost:$PORT"
exec java -jar "$JAR"
