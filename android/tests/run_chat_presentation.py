#!/usr/bin/env python3
"""Isolated host checks for chat presentation; never builds/signs an APK."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]


def main():
    home = os.environ.get('JAVA_HOME')
    javac = str(Path(home) / 'bin/javac') if home else shutil.which('javac')
    java = str(Path(home) / 'bin/java') if home else shutil.which('java')
    if not javac or not java:
        raise SystemExit('Set JAVA_HOME to a JDK, or put javac/java on PATH.')
    jar = ROOT / 'build/test-lib/json.jar'
    if not jar.exists():
        raise SystemExit('Run android/test.sh first to obtain its verified host JSON jar.')
    output = Path(os.environ.get('CHAT_TEST_BUILD_DIR', str(ROOT / 'build/chat-presentation')))
    output.mkdir(parents=True, exist_ok=True)
    subprocess.run([javac, '--release', '8', '-cp', str(jar), '-d', str(output),
                    str(ROOT / 'src/com/simon/mytasks/ChatPresentationPolicy.java'),
                    str(ROOT / 'tests/ChatPresentationPolicyTest.java')], check=True)
    subprocess.run([java, '-cp', str(output) + os.pathsep + str(jar),
                    'com.simon.mytasks.ChatPresentationPolicyTest'], check=True)
    subprocess.run([sys.executable, '-m', 'unittest', 'discover', '-s', str(ROOT / 'tests'),
                    '-p', 'test_chat_presentation_wiring.py'], check=True)


if __name__ == '__main__':
    main()
