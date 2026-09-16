#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

JAR="$ROOT/target/typist-1.0.0.jar"
if [[ ! -f "$JAR" ]]; then
  mvn -q package -DskipTests
fi

nohup java -jar "$JAR" >/dev/null 2>&1 &
disown
