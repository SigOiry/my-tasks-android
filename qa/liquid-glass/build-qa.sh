#!/usr/bin/env bash
# Generates/signs only a separate scratch QA APK; NEVER installs or updates release.
set -euo pipefail
umask 077
HERE="$(cd "$(dirname "$0")" && pwd)"
ANDROID="$(cd "$HERE/../../android" && pwd)"
RUNTIME="${1:?Usage: bash build-qa.sh PRIVATE_RUNTIME_ROOT NEW_SCRATCH_BUILD_DIR}"
OUT="${2:?A new scratch output directory is required}"
export JAVA_HOME="${JAVA_HOME:-/opt/data/cache/android-build/jdk-17.0.2}"
export ANDROID_HOME="${ANDROID_HOME:-/opt/data/cache/android-build/sdk}"
export PATH="$JAVA_HOME/bin:$PATH"
TOOLS="$ANDROID_HOME/build-tools/35.0.0"
JAR="$ANDROID_HOME/platforms/android-35/android.jar"
for file in "$JAVA_HOME/bin/javac" "$JAVA_HOME/bin/keytool" "$TOOLS/aapt2" "$TOOLS/d8" "$TOOLS/apksigner" "$TOOLS/zipalign" "$JAR"; do
 test -x "$file" || test "$file" = "$JAR" && test -f "$file" || { printf 'Missing build prerequisite: %s\n' "$file" >&2; exit 1; }
done
python3 "$HERE/overlay.py" --android "$ANDROID" --output "$OUT" --certificate "$RUNTIME/qa-cert.pem"
# Copy resources only, never .signing, preferences, tokens or production build outputs.
python3 - "$ANDROID" "$OUT" <<'PY'
import sys,shutil
from pathlib import Path
source,output=map(Path,sys.argv[1:])
shutil.copytree(source/'res',output/'res',dirs_exist_ok=True)
# Regenerate overlay resources after copies is unnecessary: release has neither QA name.
for p in ('classes','generated','dex'): (output/p).mkdir()
(output/'sources.txt').write_text('\n'.join(str(p) for p in (source/'src').rglob('*.java')))
PY
"$TOOLS/aapt2" compile --dir "$OUT/res" -o "$OUT/resources.zip"
"$TOOLS/aapt2" link -o "$OUT/resources.apk" --manifest "$OUT/AndroidManifest.xml" -I "$JAR" --java "$OUT/generated" --custom-package com.simon.mytasks --min-sdk-version 26 --target-sdk-version 35 "$OUT/resources.zip"
python3 - "$OUT" <<'PY'
import sys
from pathlib import Path
out=Path(sys.argv[1]);p=out/'sources.txt';p.write_text(p.read_text()+'\n'+'\n'.join(str(x) for x in (out/'generated').rglob('*.java')))
PY
javac --release 8 -classpath "$JAR" -d "$OUT/classes" "@$OUT/sources.txt"
python3 - "$OUT" <<'PY'
import sys,zipfile
from pathlib import Path
out=Path(sys.argv[1])
with zipfile.ZipFile(out/'classes.jar','w') as z:
 for p in (out/'classes').rglob('*.class'):z.write(p,str(p.relative_to(out/'classes')))
PY
"$TOOLS/d8" --lib "$JAR" --min-api 26 --output "$OUT/dex" "$OUT/classes.jar"
python3 - "$OUT" <<'PY'
import sys,shutil,zipfile
from pathlib import Path
out=Path(sys.argv[1]);shutil.copyfile(out/'resources.apk',out/'unsigned.apk')
with zipfile.ZipFile(out/'unsigned.apk','a') as z:
 for p in (out/'dex').glob('*.dex'):z.write(p,p.name)
PY
"$TOOLS/zipalign" -f 4 "$OUT/unsigned.apk" "$OUT/aligned.apk"
# Stable QA-only identity per private runtime, never the production identity/store.
if [[ ! -f "$RUNTIME/qa-signing.p12" ]]; then
 python3 - "$RUNTIME" <<'PY'
import sys,os,secrets
from pathlib import Path
path=Path(sys.argv[1])/'qa-signing-password'
fd=os.open(path,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
with os.fdopen(fd,'w') as f:f.write(secrets.token_urlsafe(40))
PY
 keytool -genkeypair -keystore "$RUNTIME/qa-signing.p12" -storetype PKCS12 -storepass:file "$RUNTIME/qa-signing-password" -alias synthetic-qa -keyalg RSA -keysize 2048 -validity 30 -dname 'CN=Synthetic QA Only' >/dev/null 2>&1
fi
"$TOOLS/apksigner" sign --ks "$RUNTIME/qa-signing.p12" --ks-key-alias synthetic-qa --ks-pass "file:$RUNTIME/qa-signing-password" --out "$OUT/my-tasks-qa.apk" "$OUT/aligned.apk"
"$TOOLS/apksigner" verify "$OUT/my-tasks-qa.apk"
"$TOOLS/zipalign" -c 4 "$OUT/my-tasks-qa.apk"
"$TOOLS/aapt2" dump badging "$OUT/my-tasks-qa.apk"
printf '\nQA-only APK: %s/my-tasks-qa.apk\n' "$OUT"
