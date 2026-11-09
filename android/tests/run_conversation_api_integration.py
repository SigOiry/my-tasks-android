#!/usr/bin/env python3
"""Optional JVM client -> real loopback ASGI API; synthetic provider, scratch DB only.
No task token, deployed workspace, real provider or signing secret is used.
Requires the companion server's Python dependencies and JDK17.
"""
import json
import importlib
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile
import threading
import time
from types import SimpleNamespace
from unittest.mock import patch

sys.dont_write_bytecode = True
ANDROID = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ANDROID.parent / 'server'))
create_app = importlib.import_module("mobile_api").create_app
import uvicorn


def main():
    java = Path(os.environ.get('JAVA_HOME', '/opt/data/cache/android-build/jdk-17.0.2')) / 'bin'
    scratch = Path(os.environ.get('TMPDIR', '/opt/data/cache/scratch'))
    with tempfile.TemporaryDirectory(prefix='android-conversation-api-', dir=scratch) as directory:
        root = Path(directory)
        token = 'native-synthetic-integration-' + 'x' * 48
        (root / 'token').write_text(token)
        (root / 'token').chmod(0o600)
        AccountStore = importlib.import_module('account_auth').AccountStore
        accounts = AccountStore(root / 'account.sqlite')
        initial = 'Scratch conversation initial phrase'
        accounts.initialize('fixture-owner', initial)
        token, _ = accounts.change(accounts.snapshot(), initial, 'Distinct scratch conversation phrase!', 'native', {'_epoch': 0})
        config = dict(db_path=root / 'tasks.json', token_file=root / 'token', metadata_path=root / 'events.sqlite', account_path=root / 'account.sqlite', session_path=root / 'web-sessions.sqlite', conversation_path=root / 'conversations.sqlite')
        app = create_app(**config)
        calls = []

        def provider(**kwargs):
            assert kwargs['tools'] == [], 'Assistant must have no tools'
            calls.append(kwargs)
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(
                content='Synthetic integration fixture answer; no real provider called.', tool_calls=[]))])

        sock = socket.socket()
        sock.bind(('127.0.0.1', 0))
        address = f'http://127.0.0.1:{sock.getsockname()[1]}'
        server = uvicorn.Server(uvicorn.Config(app, host='127.0.0.1', log_level='error', access_log=False))
        thread = threading.Thread(target=server.run, kwargs={'sockets': [sock]}, daemon=True)
        with patch('hermes_chat.call_llm', side_effect=provider):
            try:
                thread.start()
                deadline = time.monotonic() + 10
                while not server.started:
                    if not thread.is_alive() or time.monotonic() > deadline:
                        raise RuntimeError('Scratch-only API did not start')
                    time.sleep(.02)
                classes = root / 'classes'
                classes.mkdir()
                jar = ANDROID / 'build/test-lib/json.jar'
                subprocess.run([str(java / 'javac'), '--release', '17', '-cp', str(jar), '-d', str(classes),
                                str(ANDROID / 'src/com/simon/mytasks/ConversationClient.java'),
                                str(ANDROID / 'src/com/simon/mytasks/ConversationState.java'),
                                str(ANDROID / 'tests/ConversationApiIntegrationTest.java')], check=True)
                result = subprocess.run([str(java / 'java'), '-cp', f'{classes}:{jar}',
                                         'com.simon.mytasks.ConversationApiIntegrationTest', address, token],
                                        capture_output=True, text=True, timeout=90)
                if result.returncode:
                    raise RuntimeError(result.stdout + result.stderr)
                report = json.loads(result.stdout.strip().splitlines()[-1])
                assert len(calls) == 21, 'Exact replay must not duplicate provider work'
                # Durable server reopen, with native client having loaded all 42 messages.
                ConversationStore = importlib.import_module("conversations").ConversationStore
                persisted = ConversationStore(root / 'conversations.sqlite').get(report['conversation_id'])
                assert len(persisted['messages']) == 40 and persisted['has_more']
                report.update(provider_calls=len(calls), tools_empty=True, scratch_only=True,
                              server_store_reopen=True, android_runtime=False, initialized_native_auth=True)
                print(json.dumps(report, sort_keys=True))
            finally:
                server.should_exit = True
                thread.join(10)
                sock.close()
                app.state.conversations.close()


if __name__ == '__main__':
    main()
