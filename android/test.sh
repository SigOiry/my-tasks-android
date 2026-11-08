#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
mkdir -p build/tests
"$JAVA_HOME/bin/javac" --release 8 -d build/tests src/com/simon/mytasks/Core.java tests/CoreTest.java
"$JAVA_HOME/bin/java" -cp build/tests com.simon.mytasks.CoreTest "${1:-all}"
