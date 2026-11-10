#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
TEST_BUILD_DIR="${TEST_BUILD_DIR:-build/tests-native}"
mkdir -p "$TEST_BUILD_DIR" build/test-lib
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
"$JAVA_HOME/bin/javac" --release 8 -cp build/test-lib/json.jar -d "$TEST_BUILD_DIR" src/com/simon/mytasks/{Core,PrivateSurfaces,AuthPolicy,AuthClient,SafeMarkdown,NotificationProse,ChatPresentationPolicy,UpdateEmphasis,UpdateReviewPolicy,TaskActionPolicy,UiReadGate,PresentationPolicy,NativeMotionPolicy,CompactPolicy,ConversationState,ConversationClient,UpdatePolicy,UpdateIO,UpdateRelease,UpdateNetwork,UpdateState}.java tests/{CoreTest,PrivateSurfacesTest,AuthPolicyTest,NotificationProseTest,ChatPresentationPolicyTest,UpdateEmphasisTest,UpdateReviewPolicyTest,TaskActionPolicyTest,UiReadGateTest,PresentationPolicyTest,CoordinatedPolicyTest,NativeMotionPolicyTest,CompactPolicyTest,ConversationStateTest,ConversationClientTest,UpdatePolicyTest,UpdateReleaseTest,UpdateNetworkTest,UpdateStateTest}.java
"$JAVA_HOME/bin/java" -cp "$TEST_BUILD_DIR" com.simon.mytasks.CoreTest "${1:-all}"
for test in PrivateSurfacesTest AuthPolicyTest NotificationProseTest ChatPresentationPolicyTest UpdateEmphasisTest UpdateReviewPolicyTest TaskActionPolicyTest UiReadGateTest PresentationPolicyTest CoordinatedPolicyTest NativeMotionPolicyTest CompactPolicyTest ConversationStateTest ConversationClientTest UpdatePolicyTest UpdateReleaseTest UpdateNetworkTest UpdateStateTest; do
 "$JAVA_HOME/bin/java" -cp "$TEST_BUILD_DIR":build/test-lib/json.jar "com.simon.mytasks.$test"
done
# Controller behavior with deterministic host-only storage/dispatch/transport seams.
# Never mix these Android/Store/Api fakes into APK classes or backend test outputs.
mkdir -p "$TEST_BUILD_DIR/controller"
"$JAVA_HOME/bin/javac" --release 8 -cp build/test-lib/json.jar -d "$TEST_BUILD_DIR/controller" tests/controller-stubs/android/{content,os}/*.java tests/controller-stubs/com/simon/mytasks/*.java src/com/simon/mytasks/{ConversationState,ConversationClient,ConversationController}.java tests/ConversationControllerTest.java
"$JAVA_HOME/bin/java" -cp "$TEST_BUILD_DIR/controller":build/test-lib/json.jar com.simon.mytasks.ConversationControllerTest
# Actual Api authorization gate, with host-only Account and injected transport.
mkdir -p "$TEST_BUILD_DIR/api"
"$JAVA_HOME/bin/javac" --release 8 -cp build/test-lib/json.jar -d "$TEST_BUILD_DIR/api" tests/api-stubs/com/simon/mytasks/Store.java src/com/simon/mytasks/{Api,Core,AuthPolicy,AuthClient}.java tests/ApiAuthGateTest.java
"$JAVA_HOME/bin/java" -cp "$TEST_BUILD_DIR/api":build/test-lib/json.jar com.simon.mytasks.ApiAuthGateTest
python3 -m unittest discover -s tests -p 'test_*.py'
# Actual Activity/presenter/poll methods with synthetic host seams, no Android runtime claim.
JAVA_HOME="$JAVA_HOME" python3 tests/run_redesign_regressions.py
# Actual reply draft/client/controller, citation/title and reported-hinge regressions.
JAVA_HOME="$JAVA_HOME" python3 tests/run_email_reply_tests.py
