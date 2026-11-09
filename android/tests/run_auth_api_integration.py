#!/usr/bin/env python3
"""Real native protocol -> scratch-only initialized account. No production secrets."""
import importlib
import json
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import threading
import time
sys.dont_write_bytecode = True
ANDROID = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ANDROID.parent / 'server'))
import uvicorn
from mobile_api import create_app
from account_auth import AccountStore

def main():
    java = Path(os.environ.get('JAVA_HOME', '/opt/data/cache/android-build/jdk-17.0.2')) / 'bin'
    with tempfile.TemporaryDirectory(prefix='native-auth-http-', dir=os.environ.get('TMPDIR', '/opt/data/cache/scratch')) as temp:
        root = Path(temp)
        owner = 'scratch-native-proof-' + 'x' * 48
        (root / 'token').write_text(owner)
        (root / 'token').chmod(0o600)
        store = AccountStore(root / 'account.sqlite')
        store.initialize('oiry-s', 'Fixture initial phrase only')
        sock = socket.socket()
        sock.bind(('127.0.0.1', 0))
        address = f'http://127.0.0.1:{sock.getsockname()[1]}'
        app = create_app(db_path=root / 'tasks.json', token_file=root / 'token', metadata_path=root / 'events.sqlite', account_path=root / 'account.sqlite', session_path=root / 'web-sessions.sqlite', conversation_path=root / 'conversations.sqlite', web_origin='https://fixture.invalid')
        server = uvicorn.Server(uvicorn.Config(app, host='127.0.0.1', log_level='error', access_log=False))
        thread = threading.Thread(target=server.run, kwargs={'sockets': [sock]}, daemon=True)
        try:
            thread.start()
            deadline = time.monotonic() + 10
            while not server.started:
                if not thread.is_alive() or time.monotonic() > deadline:
                    raise RuntimeError('Scratch auth API failed to start')
                time.sleep(.02)
            classes = root / 'classes'
            classes.mkdir()
            jar = ANDROID / 'build/test-lib/json.jar'
            sources = [ANDROID / f'src/com/simon/mytasks/{name}.java' for name in ('AuthClient', 'AuthPolicy', 'ConversationClient')]
            subprocess.run([str(java / 'javac'), '--release', '17', '-cp', str(jar), '-d', str(classes), *map(str, sources), str(ANDROID / 'tests/AuthApiIntegrationTest.java')], check=True)
            result = subprocess.run([str(java / 'java'), '-cp', f'{classes}:{jar}', 'com.simon.mytasks.AuthApiIntegrationTest', address, owner], capture_output=True, text=True, timeout=90)
            if result.returncode:
                raise RuntimeError(result.stdout + result.stderr)
            report = json.loads(result.stdout.strip().splitlines()[-1])
            report['private_db_mode'] = oct((root / 'account.sqlite').stat().st_mode & 0o777)
            print(json.dumps(report, sort_keys=True))
        finally:
            server.should_exit = True
            thread.join(10)
            sock.close()
            app.state.conversations.close()
if __name__ == '__main__':
    main()
