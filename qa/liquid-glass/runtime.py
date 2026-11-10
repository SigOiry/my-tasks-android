"""Explicit synthetic QA, canonical Store + HTTP API + durable conversations.
Never use in a production process: fixture provider replaces the model boundary.
"""
import argparse
import json
import os
import secrets
import sys
import time
import uuid
from pathlib import Path
from types import SimpleNamespace
from unittest.mock import patch

SERVER = Path(__file__).resolve().parents[2]/'server'
sys.path.insert(0, str(SERVER))
from fastapi.testclient import TestClient
from mobile_api import create_app, dashboard
from qa_server import prepare, SCRATCH


class Fixture:
    def __init__(self, root, port=9147, *, email_provider=None, email_llm=None):
        self.root = Path(root).resolve()
        if not self.root.is_relative_to(SCRATCH) or self.root == SCRATCH:
            raise ValueError('Fresh private scratch child required')
        self.port = port
        self.provider_calls = 0
        self.email_provider = email_provider
        self.email_llm = email_llm

    def __enter__(self):
        marker = self.root/'liquid-glass-synthetic.json'
        if self.root.exists() and any(self.root.iterdir()) and not marker.exists():
            raise ValueError('Refusing to reuse an unmarked directory')
        config = prepare(self.root, self.port, seed=False, account=False)
        fresh = not marker.exists()
        config['web_origin'] = f'https://127.0.0.1:{self.port}'
        config['cookie_secure'] = True
        config.update(email_provider=self.email_provider, email_llm=self.email_llm)
        if fresh:
            import subprocess
            subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-sha256','-days','14','-nodes',
                '-keyout',str(self.root/'qa-key.pem'),'-out',str(self.root/'qa-cert.pem'),
                '-subj','/CN=Synthetic Liquid Glass QA', '-addext','subjectAltName=IP:127.0.0.1',
                '-addext','basicConstraints=critical,CA:TRUE'],check=True,capture_output=True)
            (self.root/'qa-key.pem').chmod(0o600)
            marker.write_text(json.dumps({'synthetic':True,'version':1}))
            from account_auth import AccountStore
            initial = secrets.token_urlsafe(40)
            AccountStore(config['account_path']).initialize('qa-owner', initial)
        config['allow_legacy_uninitialized'] = False
        self.provider = patch('hermes_chat.call_llm', side_effect=self.fixture_provider)
        self.provider.start()
        try:
            self.app = create_app(**config)
            if fresh:
                with self.client() as client:
                    auth = {'Authorization':'Bearer '+(self.root/'qa-token').read_text()}
                    response = client.post('/auth/native/login', headers=auth, json={'username':'qa-owner','password':initial})
                    response.raise_for_status()
                    auth = {'Authorization':'Bearer '+response.json()['session_token']}
                    password = secrets.token_urlsafe(40)
                    response = client.post('/auth/native/password', headers=auth, json={'current_password':initial,'new_password':password})
                    response.raise_for_status()
                    fd = os.open(self.root/'qa-password',os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
                    with os.fdopen(fd,'w') as stream: stream.write(password)
                    self.seed(client, {'Authorization':'Bearer '+response.json()['session_token']})
            return self
        except BaseException:
            self.provider.stop()
            raise

    def __exit__(self, *args):
        self.app.state.conversations.close()
        self.app.state.email_workspace.close()
        self.provider.stop()

    def client(self):
        # Do not enter ASGI lifespan: only Fixture/service owns worker lifetime.
        from contextlib import contextmanager
        @contextmanager
        def opened():
            client = TestClient(self.app)
            try:
                yield client
            finally:
                client.close()
        return opened()

    def login(self, client):
        response = client.post('/auth/native/login', json={'username':'qa-owner','password':(self.root/'qa-password').read_text()})
        response.raise_for_status()
        return {'Authorization':'Bearer '+response.json()['session_token']}

    def fixture_provider(self, **kwargs):
        if kwargs.get('tools') != []:
            raise RuntimeError('Fixture never permits tools')
        self.provider_calls += 1
        time.sleep(.03)
        content = kwargs['messages'][-1]['content']
        if '[fixture:fail]' in content:
            raise RuntimeError('Explicit synthetic provider failure')
        if '[fixture:slow]' in content:
            time.sleep(6)
        if '[fixture:links]' in content:
            replay=json.loads((Path(__file__).resolve().parent/'fixtures/public-source-replay.json').read_text())
            if replay.get('synthetic') is not True or not replay['reply'].startswith('SYNTHETIC QA'):
                raise RuntimeError('Explicit synthetic source replay required')
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=replay['reply'],tool_calls=[]))])
        text = ('SYNTHETIC QA — fixture provider, no model or tools called.\n\n'
                '1. Review the saved task context.\n2. Prepare the next step for human review.\n\n'
                'Nothing was sent, applied or changed by this response.')
        return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=text,tool_calls=[]))])

    def send(self, client, auth, thread, content):
        url = '/api/conversations/'+thread['id']
        payload = {'request_id':str(uuid.uuid4()),'content':content,'mode':'question','expected_revision':thread['revision']}
        response = client.post(url+'/messages',headers=auth,json=payload)
        response.raise_for_status()
        deadline = time.monotonic()+15
        while time.monotonic()<deadline:
            state = client.get(url+'/requests/'+payload['request_id'],headers=auth).json()
            if state['status'] not in {'queued','running'}:
                if state['status'] != 'succeeded': raise RuntimeError('Fixture seed reply failed')
                return client.get(url,headers=auth).json()['conversation']
            time.sleep(.01)
        raise RuntimeError('Fixture reply did not finish')

    def seed(self, client, auth):
        from datetime import datetime, timedelta
        today = datetime.now(dashboard.taskdb.ROME).date()
        store = dashboard.taskdb.Store(self.root/'tasks.json')
        rows = [
            ('Review the observatory proposal','Research','high','todo',0),
            ('Prepare a reply to the editorial team','Research','medium','waiting',1),
            ('Collect workshop reading notes','Research','low','inbox',None),
            ('Compare the long-term research plan with the updated workshop programme','Research','medium','in_progress',3),
            ('Read the instrumentation briefing','Research','low','todo',None),
            ('Book the team planning session','Operations','high','in_progress',0),
            ('Confirm room accessibility and equipment','Operations','medium','todo',2),
            ('Archive the project checklist','Operations','low','completed',-1),
            ('Review the cancelled catering request','Operations','low','cancelled',None),
            ('Remove the obsolete travel reminder','Operations','low','todo',None),
        ]
        tasks = []
        for title, project, priority, status, offset in rows:
            tasks.append(store.add(title,project=project,priority=priority,status=status,
                due_date=(today+timedelta(days=offset)).isoformat() if offset is not None else None,
                description='SYNTHETIC QA — isolated fictional context. No real mail, people or calendar data.'))
        dashboard.initialize_notifications(self.root/'tasks.json')
        for task in (tasks[0],tasks[5]):
            store.edit(task['id'],description='SYNTHETIC QA — Updated context: the review deadline moved. Confirm the revised plan before preparing a draft.')
        trash = client.get('/api/tasks/'+tasks[-1]['id'],headers=auth).json()
        response = client.request('DELETE','/api/tasks/'+trash['id'],headers=auth,json={'revision':trash['revision']})
        response.raise_for_status()
        for title, attached, count in [('QA · Observatory review',tasks[0],3),('QA · Planning draft',tasks[5],1),('QA · Free planning',None,24)]:
            response = client.post('/api/conversations',headers=auth,json={'task_id':attached['id'] if attached else None,'title':title})
            response.raise_for_status()
            thread = response.json()
            for number in range(count):
                thread = self.send(client, auth, thread, f'Synthetic QA question {number+1}: help plan the next review step.')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,required=True)
    parser.add_argument('--port',type=int,default=9147)
    parser.add_argument('--prepare-only',action='store_true')
    args = parser.parse_args()
    with Fixture(args.root,args.port) as fixture:
        print(json.dumps({'synthetic':True,'url':f'https://127.0.0.1:{args.port}','root':str(fixture.root),'username':'qa-owner','password_file':str(fixture.root/'qa-password')}),flush=True)
        if not args.prepare_only:
            import uvicorn
            uvicorn.run(fixture.app,host='127.0.0.1',port=args.port,proxy_headers=False,access_log=False,
                ssl_keyfile=str(fixture.root/'qa-key.pem'),ssl_certfile=str(fixture.root/'qa-cert.pem'))

if __name__ == '__main__': main()
