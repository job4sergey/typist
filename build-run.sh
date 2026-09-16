#!/usr/bin/env bash
set -euo pipefail

# JDK used to compile and start Typist (no Maven).
JDK_HOME="/home/lsa/distrib/dev/java/jdk-23.0.2"

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT"

JAVAC="$JDK_HOME/bin/javac"
JAVA="$JDK_HOME/bin/java"
SQLITE_JAR="$ROOT/lib/sqlite-jdbc-3.46.1.3.jar"
OUT="$ROOT/out"

if [[ ! -x "$JAVAC" || ! -x "$JAVA" ]]; then
  echo "JDK_HOME does not look like a JDK: $JDK_HOME" >&2
  exit 1
fi
if [[ ! -f "$SQLITE_JAR" ]]; then
  echo "Missing $SQLITE_JAR" >&2
  exit 1
fi

mkdir -p "$OUT"
mapfile -t SOURCES < <(find "$ROOT/src/main/java" -name '*.java' | sort)
"$JAVAC" --release 17 -encoding UTF-8 -d "$OUT" -cp "$SQLITE_JAR" "${SOURCES[@]}"

nohup "$JAVA" -cp "$OUT:$SQLITE_JAR" com.typist.TypistApp >/dev/null 2>&1 &
disown
