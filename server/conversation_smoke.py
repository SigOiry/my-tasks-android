"""Synthetic scratch-only API smoke. Real configured provider is opt-in."""
import argparse
import json
import tempfile
import time
import uuid
from contextlib import nullcontext
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

from fastapi.testclient import TestClient
from qa_server import SCRATCH, prepare
from mobile_api import create_app


def smoke(root,*,real_provider=False):
    config = prepare(root,9138,seed=True)
    app = create_app(**config)
    auth = {'Authorization':'Bearer '+Path(config['token_file']).read_text().strip()}
    response = SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Synthetic smoke answer; no provider called.',tool_calls=[]))])
    provider = nullcontext() if real_provider else patch('hermes_chat.call_llm',return_value=response)
    with provider, TestClient(app) as client:
        try:
            created = client.post('/api/conversations',headers=auth,json={'task_id':None,'title':'Scratch API smoke'})
            if created.status_code!=201:
                raise RuntimeError('Scratch conversation creation failed')
            conversation = created.json()
            url = '/api/conversations/'+conversation['id']
            rid = str(uuid.uuid4())
            accepted = client.post(url+'/messages',headers=auth,json={'request_id':rid,'content':'In one sentence, explain what a nebula is.','mode':'question','expected_revision':1})
            if accepted.status_code!=202:
                raise RuntimeError('Scratch message admission failed')
            deadline = time.monotonic()+240
            while True:
                status = client.get(url+'/requests/'+rid,headers=auth).json()
                if status['status'] not in {'queued','running'}:
                    break
                if time.monotonic()>deadline:
                    raise RuntimeError('Scratch provider still running; poll saved request rather than replaying')
                time.sleep(.05)
            page = client.get(url,headers=auth).json()
            return dict(status=status['status'],messages=len(page['messages']),real_provider=real_provider,
                        scratch_root=str(Path(root)),conversation_id=conversation['id'],request_id=rid)
        finally:
            app.state.conversations.close()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path)
    parser.add_argument('--real-provider',action='store_true',help='Explicitly call configured Hermès provider, with tools=[]; may incur cost')
    args = parser.parse_args()
    root = args.root or Path(tempfile.mkdtemp(prefix='mytasks-conversation-smoke-',dir=SCRATCH))
    report = smoke(root,real_provider=args.real_provider)
    print(json.dumps(report))
    raise SystemExit(0 if report['status']=='succeeded' else 1)


if __name__=='__main__':
    main()
