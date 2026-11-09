#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
mkdir -p build/tests build/test-lib
# JVM-only JSON test dependency; Android supplies org.json in the APK runtime.
python3 - <<'PY'
from pathlib import Path
import hashlib, urllib.request
p=Path('build/test-lib/json.jar')
expected='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
if not p.exists():
    data=urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',timeout=30).read()
    if hashlib.sha256(data).hexdigest()!=expected: raise SystemExit('JSON dependency checksum mismatch')
    p.write_bytes(data)
if hashlib.sha256(p.read_bytes()).hexdigest()!=expected: raise SystemExit('JSON dependency checksum mismatch')
PY
"$JAVA_HOME/bin/javac" --release 8 -cp build/test-lib/json.jar -d build/tests src/com/simon/mytasks/{Core,PresentationPolicy,UpdatePolicy,UpdateIO,UpdateRelease,UpdateNetwork,UpdateState}.java tests/{CoreTest,PresentationPolicyTest,UpdatePolicyTest,UpdateReleaseTest,UpdateNetworkTest,UpdateStateTest}.java
"$JAVA_HOME/bin/java" -cp build/tests com.simon.mytasks.CoreTest "${1:-all}"
"$JAVA_HOME/bin/java" -cp build/tests:build/test-lib/json.jar com.simon.mytasks.PresentationPolicyTest
"$JAVA_HOME/bin/java" -cp build/tests com.simon.mytasks.UpdatePolicyTest
"$JAVA_HOME/bin/java" -cp build/tests:build/test-lib/json.jar com.simon.mytasks.UpdateReleaseTest
"$JAVA_HOME/bin/java" -cp build/tests com.simon.mytasks.UpdateNetworkTest
"$JAVA_HOME/bin/java" -cp build/tests com.simon.mytasks.UpdateStateTest
python3 -m unittest discover -s tests -p 'test_*.py'
