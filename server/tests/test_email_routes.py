"""Real host middleware and scratch canonical Store, no external mail."""
import tempfile
import unittest
from pathlib import Path
from fastapi.testclient import TestClient
from test_email_workspace import FixtureProvider


class EmailAuthTests(unittest.TestCase):
    def setUp(self):
        from mobile_api import create_app,dashboard
        self.tmp=tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch')
        self.root=Path(self.tmp.name)
        self.token='fixture-'+'s'*48
        secret=self.root/'token';secret.write_text(self.token);secret.chmod(0o600)
        store=dashboard.taskdb.Store(self.root/'tasks.json')
        self.task=store.add('Email fixture',source='Gmail',source_references=['gmail:owner@example.test:message:abc'])
        self.provider=FixtureProvider()
        self.app=create_app(db_path=self.root/'tasks.json',token_file=secret,metadata_path=self.root/'events.sqlite',
                            account_path=self.root/'account.sqlite',session_path=self.root/'sessions.sqlite',
                            conversation_path=self.root/'conversations.sqlite',email_path=self.root/'email.sqlite',
                            email_provider=self.provider,allow_legacy_uninitialized=True,web_origin='https://testserver')
        self.client=TestClient(self.app,base_url='https://testserver')
    def tearDown(self):
        self.app.state.email_workspace.close()
        self.app.state.conversations.close()
        self.client.close();self.tmp.cleanup()
    def test_every_route_inherits_auth_csrf_and_private_no_store(self):
        routes=[('GET','/tasks/'+self.task['id']+'/email-sources',None),
                ('GET','/tasks/'+self.task['id']+'/email-sources/src_'+'0'*32,None),
                ('POST','/email/drafts',{}),('GET','/email/drafts/unknown',None),
                ('PATCH','/email/drafts/unknown',{}),('DELETE','/email/drafts/unknown',{}),
                ('POST','/email/drafts/unknown/refresh-context',{}),('POST','/email/drafts/unknown/assist',{}),
                ('GET','/email/drafts/unknown/assist/unknown',None),
                ('POST','/email/drafts/unknown/proposals/unknown/apply',{}),
                ('POST','/email/drafts/unknown/proposals/unknown/dismiss',{}),
                ('POST','/email/drafts/unknown/preview',{}),('POST','/email/drafts/unknown/send',{}),
                ('POST','/email/drafts/unknown/reconcile',{})]
        before=(self.root/'tasks.json').read_bytes()
        for method,path,payload in routes:
            with self.subTest(path=path,method=method):
                r=self.client.request(method,'/api'+path,json=payload)
                self.assertEqual(r.status_code,401,r.text)
                self.assertEqual(r.headers['cache-control'],'no-store')
        login=self.client.post('/auth/login',json={'token':self.token},headers={'Origin':'https://testserver'})
        self.assertEqual(login.status_code,200,login.text)
        csrf={'Origin':'https://testserver','X-CSRF-Token':login.json()['csrf']}
        sources=self.client.get('/api/tasks/'+self.task['id']+'/email-sources')
        self.assertEqual(sources.status_code,200,sources.text)
        payload=dict(request_id='fixture-create',task_id=self.task['id'],source_id=sources.json()['sources'][0]['id'],update_token=None,reply_all=False)
        self.assertEqual(self.client.post('/api/email/drafts',json=payload).status_code,403)
        self.assertEqual(self.client.post('/api/email/drafts',json=payload,headers=dict(csrf,Origin='https://evil.test')).status_code,403)
        draft=self.client.post('/api/email/drafts',json=payload,headers=csrf)
        self.assertEqual(draft.status_code,200,draft.text)
        base='/api/email/drafts/'+draft.json()['id']
        for method,suffix in [('PATCH',''),('DELETE',''),('POST','/refresh-context'),('POST','/assist'),
                              ('POST','/proposals/x/apply'),('POST','/proposals/x/dismiss'),('POST','/preview'),('POST','/send'),('POST','/reconcile')]:
            self.assertEqual(self.client.request(method,base+suffix,json={}).status_code,403)
        self.assertEqual((self.root/'tasks.json').read_bytes(),before)
        self.assertEqual(self.provider.sent,[])
