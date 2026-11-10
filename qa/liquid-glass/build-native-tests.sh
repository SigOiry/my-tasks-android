#!/usr/bin/env bash
set -euo pipefail
umask 077
HERE="$(cd "$(dirname "$0")" && pwd)"
RUNTIME="${1:?private synthetic runtime root}"
OUT="${2:?new scratch build output}"
export JAVA_HOME="${JAVA_HOME:-/opt/data/cache/android-build/jdk-17.0.2}"
export PATH="$JAVA_HOME/bin:$PATH"
SDK="${ANDROID_HOME:-/opt/data/cache/android-build/sdk}"
TOOLS="$SDK/build-tools/35.0.0"
JAR="$SDK/platforms/android-35/android.jar"
python3 - "$OUT" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]).resolve()
if not p.is_relative_to(Path('/opt/data/cache/scratch')):raise SystemExit('scratch only')
p.mkdir();(p/'classes').mkdir();(p/'dex').mkdir()
(p/'AndroidManifest.xml').write_text('''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.simon.mytasks.uiqa"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="35"/><application android:label="Synthetic UI acceptance"/><instrumentation android:name="com.simon.mytasks.uiqa.Acceptance" android:targetPackage="com.simon.mytasks.qa" android:functionalTest="true"/></manifest>''')
PY
"$TOOLS/aapt2" link -o "$OUT/resources.apk" --manifest "$OUT/AndroidManifest.xml" -I "$JAR"
"$JAVA_HOME/bin/javac" --release 8 -classpath "$JAR" -d "$OUT/classes" "$HERE/native/Acceptance.java"
python3 - "$OUT" <<'PY'
from pathlib import Path
import sys,zipfile
p=Path(sys.argv[1])
with zipfile.ZipFile(p/'classes.jar','w') as z:
 for f in (p/'classes').rglob('*.class'):z.write(f,str(f.relative_to(p/'classes')))
PY
"$TOOLS/d8" --lib "$JAR" --min-api 26 --output "$OUT/dex" "$OUT/classes.jar"
python3 - "$OUT" <<'PY'
import sys,shutil,zipfile
from pathlib import Path
p=Path(sys.argv[1]);shutil.copyfile(p/'resources.apk',p/'unsigned.apk')
with zipfile.ZipFile(p/'unsigned.apk','a') as z:
 for f in (p/'dex').glob('*.dex'):z.write(f,f.name)
PY
"$TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
"$TOOLS/apksigner" sign --ks "$RUNTIME/qa-signing.p12" --ks-key-alias synthetic-qa --ks-pass "file:$RUNTIME/qa-signing-password" --out "$OUT/ui-acceptance.apk" "$OUT/aligned.apk"
"$TOOLS/apksigner" verify "$OUT/ui-acceptance.apk"
printf '%s\n' "$OUT/ui-acceptance.apk"
