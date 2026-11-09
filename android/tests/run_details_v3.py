#!/usr/bin/env python3
"""Focused behavior + complete SDK compilation; no repo build writes or install."""
from pathlib import Path
import os,subprocess,tempfile
ANDROID=Path(__file__).resolve().parents[1]
JAVA=Path(os.environ.get('JAVA_HOME','/opt/data/cache/android-build/jdk-17.0.2'))/'bin'
SDK=Path(os.environ.get('ANDROID_HOME','/opt/data/cache/android-build/sdk'))
TOOLS=SDK/'build-tools/35.0.0';JAR=SDK/'platforms/android-35/android.jar'
def run(args):subprocess.run(list(map(str,args)),check=True)
run(['python3',ANDROID/'tests/test_details_v3.py'])
run(['python3',ANDROID/'tests/run_redesign_regressions.py'])
with tempfile.TemporaryDirectory(prefix='details-v3-sdk-',dir=os.environ.get('TMPDIR','/opt/data/cache/scratch')) as d:
 p=Path(d);(p/'generated').mkdir();(p/'classes').mkdir()
 run([TOOLS/'aapt2','compile','--dir',ANDROID/'res','-o',p/'resources.zip'])
 run([TOOLS/'aapt2','link','-o',p/'resources.apk','--manifest',ANDROID/'AndroidManifest.xml','-I',JAR,'--java',p/'generated','--min-sdk-version','26','--target-sdk-version','35',p/'resources.zip'])
 sources=sorted((ANDROID/'src').rglob('*.java'))+sorted((p/'generated').rglob('*.java'))
 run([JAVA/'javac','--release','8','-classpath',JAR,'-d',p/'classes',*sources])
 print(f'PASS isolated SDK35 compile: {len(sources)} current Java sources + all current resources; scratch only')
