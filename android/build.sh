#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
export ANDROID_HOME="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}"
export PATH="$JAVA_HOME/bin:$PATH"
TOOLS="${ANDROID_BUILD_TOOLS:-$ANDROID_HOME/build-tools/35.0.0}"
JAR="$ANDROID_HOME/platforms/android-35/android.jar"
for f in "$JAVA_HOME/bin/javac" "$TOOLS/aapt2" "$TOOLS/d8" "$TOOLS/zipalign" "$TOOLS/apksigner" "$JAR"; do [[ -e "$f" ]] || { printf 'Missing tool: %s\n' "$f" >&2; exit 1; }; done
python3 -c 'from pathlib import Path;import shutil;[shutil.rmtree(Path("build")/p,ignore_errors=True) for p in ("classes","generated","dex","tests")];[p.unlink(missing_ok=True) for p in (Path("build/my-tasks.apk"),Path("build/my-tasks.apk.idsig"))]'
mkdir -p build/classes build/generated build/dex build/tests
bash test.sh
"$TOOLS/aapt2" compile --dir res -o build/resources.zip
"$TOOLS/aapt2" link -o build/resources.apk --manifest AndroidManifest.xml -I "$JAR" --java build/generated --min-sdk-version 26 --target-sdk-version 35 build/resources.zip
python3 -c 'from pathlib import Path; Path("build/sources.txt").write_text("\n".join(str(p) for root in ("src","build/generated") for p in Path(root).rglob("*.java")))'
javac --release 8 -classpath "$JAR" -d build/classes @build/sources.txt
python3 -c 'import zipfile; from pathlib import Path; z=zipfile.ZipFile("build/classes.jar","w"); [z.write(p,str(p.relative_to("build/classes"))) for p in Path("build/classes").rglob("*.class")]; z.close()'
"$TOOLS/d8" --lib "$JAR" --min-api 26 --output build/dex build/classes.jar
python3 -c 'import shutil,zipfile;from pathlib import Path;shutil.copyfile("build/resources.apk","build/unsigned.apk");z=zipfile.ZipFile("build/unsigned.apk","a");[z.write(p,p.name) for p in Path("build/dex").glob("*.dex")];z.close()'
"$TOOLS/zipalign" -f 4 build/unsigned.apk build/aligned.apk
# Upgrade safety: an existing private signing identity is required; never generate or replace it.
umask 077
KEYSTORE="${SIGNING_KEYSTORE:-$PWD/.signing/mytasks.p12}"
PASSFILE="${SIGNING_PASSWORD_FILE:-$PWD/.signing/password}"
ALIAS="${SIGNING_ALIAS:-mytasks}"
[[ -f "$KEYSTORE" ]] || { printf 'Existing signing keystore required; refusing to create a new identity\n' >&2; exit 1; }
[[ -r "$PASSFILE" ]] || { printf 'Signing password file missing\n' >&2; exit 1; }
"$TOOLS/apksigner" sign --ks "$KEYSTORE" --ks-key-alias "$ALIAS" --ks-pass "file:$PASSFILE" --out build/my-tasks.apk build/aligned.apk
"$TOOLS/apksigner" verify --verbose --print-certs build/my-tasks.apk
"$TOOLS/zipalign" -c 4 build/my-tasks.apk
"$TOOLS/aapt2" dump badging build/my-tasks.apk
printf '\nAPK: %s/build/my-tasks.apk\n' "$PWD"
