"""Synthetic private mail fixtures only: no external transport calls."""
import hashlib
import tempfile
import unittest
from pathlib import Path
from fastapi import FastAPI, APIRouter, Depends, HTTPException
from fastapi.testclient import TestClient


class FixtureProvider:
    def __init__(self):
        self.sent = []
        self.generation = 1
        self.failure = False
    def resolve(self, reference):
        return [self.read(reference)]
    def read(self, reference):
        return dict(reference=reference, provider='gmail', account='owner@example.test',
                    identities=['owner@example.test','alias@example.test'], sender='Author <author@example.test>',
                    reply_to='Replies <reply@example.test>', to='owner@example.test, colleague@example.test',
                    cc='alias@example.test, copy@example.test', subject='Original subject', date='2026-11-10',
                    body='Original private source', message_id='<original@example.test>', references='<ancestor@example.test>',
                    thread_id='thread1', thread=[dict(sender='author@example.test',subject='Original subject',date='',body='context '+str(self.generation))])
    def send(self, message, raw):
        self.sent.append((message,raw))
        if self.failure: raise TimeoutError('private transport error')
        return {'id':'receipt1','message_id':message['outgoing_message_id']}
    def reconcile(self, message):
        return {'id':'receipt1','message_id':message['outgoing_message_id']} if self.sent and not self.failure else None


class WorkspaceCase(unittest.TestCase):
    def setUp(self):
        from email_workspace import EmailWorkspace
        from email_routes import install_routes
        self.tmp = tempfile.TemporaryDirectory()
        self.path = Path(self.tmp.name)/'email.sqlite'
        self.task = dict(id='task1',title='Private task',description='task context',notes=['update note'],
                         source_references=['gmail:owner@example.test:message:abc'],notification={'token':'token1'})
        self.provider = FixtureProvider()
        self.workspace = EmailWorkspace(self.path,lambda tid: self.task if tid=='task1' else None,provider=self.provider)
        self.workspace.start()
        app=FastAPI()
        from fastapi.exceptions import RequestValidationError
        from fastapi.responses import JSONResponse
        @app.exception_handler(RequestValidationError)
        async def invalid(request,exc):return JSONResponse({'detail':'Invalid request fields'},status_code=422)
        def reject(): raise HTTPException(401,'Authentication required')
        public=APIRouter(prefix='/denied',dependencies=[Depends(reject)])
        install_routes(public,self.workspace)
        app.include_router(public)
        router=APIRouter(prefix='/api')
        install_routes(router,self.workspace)
        app.include_router(router)
        self.client=TestClient(app)
    def tearDown(self):
        self.workspace.close()
        self.client.close()
        self.tmp.cleanup()
    def create(self,**changes):
        source=self.client.get('/api/tasks/task1/email-sources').json()['sources'][0]
        payload=dict(request_id='create1',task_id='task1',source_id=source['id'],update_token='token1',reply_all=False)
        payload.update(changes)
        r=self.client.post('/api/email/drafts',json=payload)
        self.assertEqual(r.status_code,200,r.text)
        return r.json()
    def save(self,draft,body='Written by human'):
        r=self.client.patch('/api/email/drafts/'+draft['id'],json={'expected_revision':draft['revision'],'body':body})
        self.assertEqual(r.status_code,200,r.text)
        return r.json()
    def wait_job(self,did,rid):
        import time
        for _ in range(200):
            response=self.client.get('/api/email/drafts/'+did+'/assist/'+rid)
            self.assertEqual(response.status_code,200,response.text)
            job=response.json()
            if job['status'] not in ('queued','running'):return job
            time.sleep(.01)
        self.fail('AI job did not settle')
    def test_ai_proposals_chat_idempotency_stale_apply_and_reopen(self):
        from types import SimpleNamespace
        calls=[]
        def llm(**kwargs):
            calls.append(kwargs)
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Proposed text',tool_calls=[]))])
        self.workspace.call_llm=llm
        d=self.save(self.create())
        base='/api/email/drafts/'+d['id']
        p=dict(request_id='ai1',expected_revision=d['revision'],intent='improve',instruction='Make concise')
        r=self.client.post(base+'/assist',json=p)
        self.assertEqual(r.status_code,202,r.text)
        job=self.wait_job(d['id'],'ai1')
        self.assertEqual(job['status'],'succeeded')
        self.assertEqual(self.client.get(base).json()['body'],'Written by human')
        self.assertEqual(calls[0]['tools'],[])
        self.assertIn('Original private source',str(calls[0]['messages']))
        self.assertEqual(self.client.post(base+'/assist',json=p).json()['id'],job['id'])
        self.assertEqual(len(calls),1)
        proposal=job['proposal']
        newer=self.save(d,'Newer human writing')
        self.assertEqual(self.client.post(base+'/proposals/'+proposal['id']+'/apply',json={'expected_revision':newer['revision']}).status_code,409)
        self.assertEqual(self.client.post(base+'/proposals/'+proposal['id']+'/dismiss',json={}).json(),{'dismissed':True})
        p.update(request_id='ai2',expected_revision=newer['revision'],intent='draft')
        self.client.post(base+'/assist',json=p)
        proposal=self.wait_job(d['id'],'ai2')['proposal']
        applied=self.client.post(base+'/proposals/'+proposal['id']+'/apply',json={'expected_revision':newer['revision']})
        self.assertEqual(applied.status_code,200,applied.text)
        self.assertEqual(applied.json()['body'],'Proposed text')
        self.assertEqual(self.client.get(base+'/assist/ai2').json()['proposal']['status'],'applied')
        p.update(request_id='ai3',expected_revision=applied.json()['revision'],intent='discuss')
        self.client.post(base+'/assist',json=p)
        discussion=self.wait_job(d['id'],'ai3')
        self.assertNotIn('proposal',discussion)
        reopened=self.create(request_id='open-after-ai')
        self.assertEqual(reopened['body'],'Proposed text')
        self.assertEqual(len(reopened['ai_messages']),6)

    def wait_draft(self,did):
        import time
        for _ in range(200):
            d=self.client.get('/api/email/drafts/'+did).json()
            if d['status']!='sending':return d
            time.sleep(.01)
        self.fail('send did not settle')
    def test_confirmed_send_threading_replay_and_reconcile(self):
        from email import policy
        from email.parser import BytesParser
        d=self.save(self.create(reply_all=True))
        base='/api/email/drafts/'+d['id']
        preview=self.client.post(base+'/preview',json={'expected_revision':d['revision']})
        self.assertEqual(preview.status_code,200,preview.text)
        review=preview.json()
        self.provider.generation=2
        payload=dict(request_id='send1',expected_revision=d['revision'],confirmation_id=review['confirmation_id'],confirmed=True)
        self.assertEqual(self.client.post(base+'/send',json=payload).status_code,409)
        self.assertEqual(self.provider.sent,[])
        blocked=self.client.post(base+'/preview',json={'expected_revision':d['revision']})
        self.assertEqual(blocked.status_code,409,blocked.text)
        self.assertIn('Refresh context',blocked.json()['detail'])
        refreshed=self.client.post(base+'/refresh-context',json={'expected_revision':d['revision']})
        self.assertEqual(refreshed.status_code,200,refreshed.text)
        d=refreshed.json()
        self.assertEqual(d['thread'][0]['body'],'context 2')
        self.assertEqual(d['body'],'Written by human')
        review=self.client.post(base+'/preview',json={'expected_revision':d['revision']}).json()
        self.assertFalse(review['context_changed'])
        payload.update(expected_revision=d['revision'],confirmation_id=review['confirmation_id'])
        self.assertEqual(self.client.post(base+'/send',json=dict(payload,confirmed=False)).status_code,422)
        response=self.client.post(base+'/send',json=payload)
        self.assertEqual(response.status_code,200,response.text)
        sent=self.wait_draft(d['id'])
        self.assertEqual(sent['status'],'sent')
        self.assertIsNotNone(sent['send_result'])
        self.assertEqual(self.client.post(base+'/send',json=payload).json()['status'],'sent')
        self.assertEqual(len(self.provider.sent),1)
        mime=BytesParser(policy=policy.default).parsebytes(self.provider.sent[0][1])
        self.assertEqual(str(mime['Subject']),'Original subject')
        self.assertEqual(str(mime['In-Reply-To']),'<original@example.test>')
        self.assertEqual(str(mime['References']),'<ancestor@example.test> <original@example.test>')
        self.assertEqual(self.provider.sent[0][0]['thread_id'],'thread1')
        self.assertNotIn('owner@example.test',str(mime['To'])+str(mime['Cc']))
        self.assertEqual(self.client.request('DELETE',base,json={'expected_revision':sent['revision']}).status_code,409)
        self.assertEqual(self.client.post(base+'/reconcile',json={}).json()['status'],'sent')
    def test_unseen_context_requires_explicit_refresh_before_confirmation(self):
        d=self.save(self.create())
        base='/api/email/drafts/'+d['id']
        self.provider.generation=99
        blocked=self.client.post(base+'/preview',json={'expected_revision':d['revision']})
        self.assertEqual(blocked.status_code,409,blocked.text)
        self.assertIn('Refresh context',blocked.json()['detail'])
        self.assertEqual(self.client.get(base).json(),d)
        with self.workspace.db() as c:
            self.assertEqual(c.execute('SELECT COUNT(*) FROM confirmations').fetchone()[0],0)
        denied=self.client.post(base+'/send',json=dict(request_id='unseen',expected_revision=d['revision'],confirmation_id='confirm_unseen',confirmed=True))
        self.assertEqual(denied.status_code,409,denied.text)
        self.assertEqual(self.provider.sent,[])
        response=self.client.post(base+'/refresh-context',json={'expected_revision':d['revision']})
        self.assertEqual(response.status_code,200,response.text)
        fresh=response.json()
        self.assertEqual(fresh['body'],d['body'])
        self.assertEqual(fresh['thread'][0]['body'],'context 99')
        self.assertEqual(fresh['revision'],d['revision']+1)
        self.assertEqual(self.client.get(base).json(),fresh)
        self.assertEqual(self.client.post(base+'/preview',json={'expected_revision':d['revision']}).status_code,409)
        preview=self.client.post(base+'/preview',json={'expected_revision':fresh['revision']})
        self.assertEqual(preview.status_code,200,preview.text)
        self.assertFalse(preview.json()['context_changed'])
        self.provider.generation=100
        denied=self.client.post(base+'/send',json=dict(request_id='changed-again',expected_revision=fresh['revision'],confirmation_id=preview.json()['confirmation_id'],confirmed=True))
        self.assertEqual(denied.status_code,409,denied.text)
        self.assertEqual(self.provider.sent,[])

    def test_changed_task_context_requires_refresh_and_fences_old_confirmation(self):
        d=self.save(self.create())
        base='/api/email/drafts/'+d['id']
        old=self.client.post(base+'/preview',json={'expected_revision':d['revision']}).json()
        self.task['description']='New task context'
        self.task['notes'].append('New decision')
        self.task['notification']['token']='token2'
        blocked=self.client.post(base+'/preview',json={'expected_revision':d['revision']})
        self.assertEqual(blocked.status_code,409,blocked.text)
        self.assertIn('Refresh context',blocked.json()['detail'])
        with self.workspace.db() as c:
            self.assertEqual(c.execute('SELECT COUNT(*) FROM confirmations').fetchone()[0],1)
        response=self.client.post(base+'/refresh-context',json={'expected_revision':d['revision']})
        self.assertEqual(response.status_code,200,response.text)
        fresh=response.json()
        self.assertEqual(fresh['body'],d['body'])
        self.assertEqual(fresh['update_token'],'token2')
        self.assertEqual(fresh['revision'],d['revision']+1)
        with self.workspace.db() as c:
            stored=self.workspace.load(c,d['id'])
        self.assertEqual(stored['_task']['description'],'New task context')
        self.assertEqual(stored['_task']['notes'],['update note','New decision'])
        denied=self.client.post(base+'/send',json=dict(request_id='old-context',expected_revision=fresh['revision'],confirmation_id=old['confirmation_id'],confirmed=True))
        self.assertEqual(denied.status_code,409,denied.text)
        self.assertEqual(self.provider.sent,[])
        preview=self.client.post(base+'/preview',json={'expected_revision':fresh['revision']})
        self.assertEqual(preview.status_code,200,preview.text)
        sent=self.client.post(base+'/send',json=dict(request_id='refreshed-context',expected_revision=fresh['revision'],confirmation_id=preview.json()['confirmation_id'],confirmed=True))
        self.assertEqual(sent.status_code,200,sent.text)
        self.assertEqual(self.wait_draft(d['id'])['status'],'sent')
        self.assertEqual(len(self.provider.sent),1)
        self.assertEqual(self.provider.sent[0][0]['thread'][0]['body'],'context 1')

    def test_trigger_exact_selection_refresh_reply_all_and_discard(self):
        self.task['source_references'].append('gmail:owner@example.test:message:def')
        self.assertTrue(self.client.get('/api/tasks/task1/email-sources').json()['ambiguous'])
        self.task['email_origins']=[self.task['source_references'][0]]
        sources=self.client.get('/api/tasks/task1/email-sources').json()
        self.assertEqual(len(sources['sources']),1)
        self.assertEqual(sources['sources'][0]['origin'],'trigger')
        d=self.save(self.create())
        self.task['notification']['token']='token2'
        response=self.client.post('/api/email/drafts/'+d['id']+'/refresh-context',json={'expected_revision':d['revision'],'reply_all':True})
        self.assertEqual(response.status_code,200,response.text)
        refreshed=response.json()
        self.assertEqual(refreshed['update_token'],'token2')
        self.assertEqual(refreshed['body'],d['body'])
        self.assertEqual(refreshed['to'],['reply@example.test','colleague@example.test'])
        self.assertEqual(refreshed['cc'],['copy@example.test'])
        self.assertEqual(self.client.request('DELETE','/api/email/drafts/'+d['id'],json={'expected_revision':d['revision']}).status_code,409)
        self.assertEqual(self.client.request('DELETE','/api/email/drafts/'+d['id'],json={'expected_revision':refreshed['revision']}).json(),{'discarded':True})
        self.assertEqual(self.client.get('/api/email/drafts/'+d['id']).status_code,404)

    def test_restart_interrupts_ai_and_preserves_uncertain_send_identity(self):
        from email_workspace import EmailWorkspace
        from types import SimpleNamespace
        self.workspace.call_llm=lambda **kw:SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Draft',tool_calls=[]))])
        d=self.save(self.create())
        base='/api/email/drafts/'+d['id']
        self.client.post(base+'/assist',json=dict(request_id='crash-ai',expected_revision=d['revision'],intent='edit',instruction='Rewrite'))
        self.wait_job(d['id'],'crash-ai')
        # Recreate the exact persisted crash boundary, without inventing a receipt.
        with self.workspace.db() as c:
            import json
            row=c.execute('SELECT data FROM jobs WHERE request_id=?',('crash-ai',)).fetchone()
            j=json.loads(row[0]);j['status']='running'
            c.execute('UPDATE jobs SET data=?',(json.dumps(j),))
            saved=self.workspace.load(c,d['id']);saved['latest_ai_request']=self.workspace.public(j)
            saved['status']='sending';self.workspace.write(c,saved)
        self.workspace.close()
        other=EmailWorkspace(self.path,lambda tid:self.task,provider=self.provider)
        try:
            other.start()
            self.assertEqual(other.get(d['id'])['status'],'uncertain')
            self.assertEqual(other.job(d['id'],'crash-ai')['status'],'interrupted')
            self.assertEqual(other.get(d['id'])['latest_ai_request']['status'],'interrupted')
            self.assertEqual(self.provider.sent,[])
        finally:other.close()

    def test_explicit_confirmation_rejects_integer_true(self):
        d=self.save(self.create());base='/api/email/drafts/'+d['id']
        review=self.client.post(base+'/preview',json={'expected_revision':d['revision']}).json()
        payload=dict(request_id='numeric-confirm',expected_revision=d['revision'],confirmation_id=review['confirmation_id'],confirmed=1)
        self.assertEqual(self.client.post(base+'/send',json=payload).status_code,422)
        self.assertEqual(self.provider.sent,[])

    def test_exact_self_sent_source_is_not_replyable_even_with_external_reply_to(self):
        from email_providers import recipients
        message=FixtureProvider().read(self.task['source_references'][0])
        message['outgoing']=True
        self.assertEqual(recipients(message,True),([],[]))

    def test_private_store_rejects_foreign_database_and_symlink(self):
        from email_workspace import EmailWorkspace
        import sqlite3
        foreign=Path(self.tmp.name)/'foreign.sqlite'
        con=sqlite3.connect(foreign)
        try:
            con.execute('CREATE TABLE credentials(secret TEXT)');con.commit()
        finally:con.close()
        before=foreign.read_bytes()
        with self.assertRaises(ValueError):EmailWorkspace(foreign,lambda tid:self.task,provider=self.provider)
        self.assertEqual(foreign.read_bytes(),before)
        alias=Path(self.tmp.name)/'alias.sqlite';alias.symlink_to(self.path)
        with self.assertRaises((ValueError,OSError)):EmailWorkspace(alias,lambda tid:self.task,provider=self.provider)

    def test_invalid_unicode_and_shutdown_do_not_admit_work(self):
        d=self.create();base='/api/email/drafts/'+d['id']
        import json
        r=self.client.patch(base,content=json.dumps({'expected_revision':d['revision'],'body':'bad\ud800'}),headers={'content-type':'application/json'})
        self.assertEqual(r.status_code,422,r.text)
        self.workspace.close()
        r=self.client.patch(base,json={'expected_revision':d['revision'],'body':'After shutdown'})
        self.assertEqual(r.status_code,503,r.text)

    def test_latest_ai_request_stays_admission_order_when_workers_finish_reversed(self):
        import threading
        from types import SimpleNamespace
        gate=threading.Event();entered=threading.Event()
        def model(**kwargs):
            if kwargs['messages'][-1]['content']=='first':
                entered.set();gate.wait(3)
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='discussion',tool_calls=[]))])
        self.workspace.call_llm=model
        d=self.create();base='/api/email/drafts/'+d['id']
        p=dict(request_id='first',expected_revision=d['revision'],intent='discuss',instruction='first')
        try:
            self.client.post(base+'/assist',json=p);self.assertTrue(entered.wait(2))
            p.update(request_id='second',instruction='second')
            self.client.post(base+'/assist',json=p);self.wait_job(d['id'],'second')
        finally:gate.set()
        self.wait_job(d['id'],'first')
        self.assertEqual(self.client.get(base).json()['latest_ai_request']['id'],'second')

    def test_large_unicode_context_is_bounded_in_utf8_not_characters(self):
        from types import SimpleNamespace
        import json
        captured=[]
        self.task['description']='🔒'*30000
        self.workspace.call_llm=lambda **kwargs:(captured.append(kwargs) or SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='reply',tool_calls=[]))]))
        d=self.save(self.create(),'🔒'*12000)
        base='/api/email/drafts/'+d['id']
        self.client.post(base+'/assist',json=dict(request_id='bounded',expected_revision=d['revision'],intent='edit',instruction='🔒'*8000))
        self.wait_job(d['id'],'bounded')
        size=len(json.dumps(captured[0],ensure_ascii=False).encode('utf-8'))
        self.assertLessEqual(size,120000)

    def test_timeout_ai_duplicate_send_races_and_save_cas(self):
        import threading,time
        from concurrent.futures import ThreadPoolExecutor
        from types import SimpleNamespace
        gate=threading.Event();calls=[]
        self.workspace.assist_timeout=.03
        def slow(**kw):
            calls.append(kw);gate.wait(3)
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Too late',tool_calls=[]))])
        self.workspace.call_llm=slow
        d=self.create();base='/api/email/drafts/'+d['id']
        p=dict(request_id='timeout',expected_revision=d['revision'],intent='draft',instruction='Write')
        try:
            self.client.post(base+'/assist',json=p)
            self.assertEqual(self.wait_job(d['id'],'timeout')['status'],'failed')
            self.client.post(base+'/assist',json=p)
            self.assertEqual(len(calls),1)
        finally:gate.set()
        for w in self.workspace.workers:w.join(2)
        self.assertEqual(self.client.get(base).json()['body'],'')
        self.assertNotIn('Too late',str(self.client.get(base).json()['ai_messages']))
        def save(i):return self.client.patch(base,json={'expected_revision':d['revision'],'body':'Writer '+str(i)}).status_code
        with ThreadPoolExecutor(max_workers=6) as pool:codes=list(pool.map(save,range(6)))
        self.assertEqual(codes.count(200),1);self.assertEqual(codes.count(409),5)
        d=self.client.get(base).json()
        preview=self.client.post(base+'/preview',json={'expected_revision':d['revision']}).json()
        self.provider.failure=True
        payload=dict(request_id='duplicate',expected_revision=d['revision'],confirmation_id=preview['confirmation_id'],confirmed=True)
        with ThreadPoolExecutor(max_workers=6) as pool:results=list(pool.map(lambda _:self.client.post(base+'/send',json=payload).status_code,range(6)))
        self.assertEqual(results,[200]*6)
        uncertain=self.wait_draft(d['id']);self.assertEqual(uncertain['status'],'uncertain')
        self.assertNotIn('private transport',uncertain['error'])
        self.assertEqual(len(self.provider.sent),1)
        self.assertEqual(self.client.post(base+'/send',json=dict(payload,request_id='resend')).status_code,409)
        self.assertEqual(self.client.post(base+'/reconcile',json={}).json()['status'],'uncertain')
        self.provider.failure=False
        self.assertEqual(self.client.post(base+'/reconcile',json={}).json()['status'],'sent')
        self.assertEqual(len(self.provider.sent),1)

    def test_thread_candidates_never_infer_latest_and_revoked_membership_blocks_send(self):
        ref='gmail:owner@example.test:thread:deadbeef'
        self.task['source_references']=[ref]
        def expand(reference):
            if reference==ref:return [self.provider.read('gmail:owner@example.test:message:abc'),self.provider.read('gmail:owner@example.test:message:def')]
            return [self.provider.read(reference)]
        self.provider.resolve=expand
        listing=self.client.get('/api/tasks/task1/email-sources').json()
        self.assertTrue(listing['ambiguous']);self.assertEqual(len(listing['sources']),2)
        self.assertTrue(all(s['origin']=='candidate' for s in listing['sources']))
        source=listing['sources'][0]
        d=self.create(source_id=source['id'])
        d=self.save(d);base='/api/email/drafts/'+d['id']
        review=self.client.post(base+'/preview',json={'expected_revision':d['revision']}).json()
        self.task['source_references']=[]
        self.assertEqual(self.client.get('/api/tasks/task1/email-sources/'+source['id']).status_code,404)
        self.assertIn(self.client.post(base+'/send',json=dict(request_id='revoked',expected_revision=d['revision'],confirmation_id=review['confirmation_id'],confirmed=True)).status_code,(404,409))
        self.assertEqual(self.provider.sent,[])
        refreshed=self.client.post(base+'/refresh-context',json={'expected_revision':d['revision']}).json()
        self.assertFalse(refreshed['can_send']);self.assertEqual(refreshed['body'],d['body'])

    def test_local_smtp_wire_transport_only_after_confirmed_api(self):
        import socketserver,threading,smtplib
        from test_email_providers import FakeIMAP
        from email_providers import PrivateProviders
        received=[]
        class SMTPHandler(socketserver.StreamRequestHandler):
            def handle(self):
                self.wfile.write(b'220 loopback fixture\r\n');self.wfile.flush()
                while True:
                    line=self.rfile.readline()
                    if not line:return
                    verb=line.split()[0].upper()
                    if verb==b'DATA':
                        self.wfile.write(b'354 continue\r\n');self.wfile.flush()
                        chunks=[]
                        while True:
                            part=self.rfile.readline()
                            if part==b'.\r\n':break
                            if not part:return
                            chunks.append(part[1:] if part.startswith(b'..') else part)
                        received.append(b''.join(chunks));response=b'250 accepted\r\n'
                    elif verb==b'QUIT':
                        self.wfile.write(b'221 bye\r\n');self.wfile.flush();return
                    else:response=b'250 fixture\r\n'
                    self.wfile.write(response);self.wfile.flush()
        server=socketserver.ThreadingTCPServer(('127.0.0.1',0),SMTPHandler)
        thread=threading.Thread(target=server.serve_forever,daemon=True);thread.start()
        imap=FakeIMAP()
        config={'email':'owner@example.test'}
        self.workspace.provider=PrivateProviders(nantes_config=lambda:config,imap_factory=lambda _:imap,
            smtp_factory=lambda _:smtplib.SMTP('127.0.0.1',server.server_address[1],timeout=3))
        self.task['source_references']=['nantes-imap://INBOX;UIDVALIDITY=123;UID=7;Message-ID=<nantes1@example.test>']
        try:
            d=self.save(self.create());base='/api/email/drafts/'+d['id']
            self.assertEqual(received,[])
            review=self.client.post(base+'/preview',json={'expected_revision':d['revision']}).json()
            self.assertEqual(received,[])
            p=dict(request_id='wire-send',expected_revision=d['revision'],confirmation_id=review['confirmation_id'],confirmed=True)
            self.assertEqual(self.client.post(base+'/send',json=p).status_code,200)
            self.assertEqual(self.wait_draft(d['id'])['status'],'sent')
            self.assertEqual(len(received),1)
            self.assertIn(b'In-Reply-To: <nantes1@example.test>',received[0])
            self.client.post(base+'/send',json=p)
            self.assertEqual(len(received),1)
        finally:
            server.shutdown();server.server_close();thread.join(3)

    def test_transport_receipt_survives_failed_readback_for_reconcile(self):
        d=self.save(self.create());base='/api/email/drafts/'+d['id']
        def failed_readback(message):raise TimeoutError('PRIVATE readback failure')
        self.provider.reconcile=failed_readback
        preview=self.client.post(base+'/preview',json={'expected_revision':d['revision']}).json()
        payload=dict(request_id='receipt-durable',expected_revision=d['revision'],confirmation_id=preview['confirmation_id'],confirmed=True)
        self.client.post(base+'/send',json=payload)
        self.assertEqual(self.wait_draft(d['id'])['status'],'uncertain')
        with self.workspace.db() as c:stored=self.workspace.load(c,d['id'])
        self.assertEqual(stored['_attempt']['transport_receipt']['id'],'receipt1')
        from email_workspace import EmailWorkspace
        from email_routes import SendDraft
        other=EmailWorkspace(self.path,lambda tid:self.task,provider=self.provider)
        try:
            other.start()
            self.assertEqual(other.send(d['id'],SendDraft(**payload))['status'],'uncertain')
            self.assertEqual(len(self.provider.sent),1)
        finally:other.close()

    def test_unavailable_trigger_retains_provenance_without_rebinding(self):
        self.task['email_origins']=['nantes-imap://INBOX;UIDVALIDITY=123;UID=7;Message-ID=<missing@example.test>']
        def unavailable(reference):raise ValueError('PRIVATE mailbox details')
        self.provider.resolve=unavailable
        sources=self.client.get('/api/tasks/task1/email-sources').json()['sources']
        self.assertEqual(len(sources),1)
        self.assertEqual(sources[0]['origin'],'trigger')
        self.assertFalse(sources[0]['available']);self.assertFalse(sources[0]['replyable'])
        self.assertNotIn('PRIVATE',str(sources))
        self.assertTrue(sources[0]['reason'])

    def test_root_installer_prefix_and_manual_empty_sources(self):
        from email_routes import install_routes
        root=FastAPI()
        install_routes(root,self.workspace)
        self.task['source_references']=[]
        with TestClient(root) as client:
            response=client.get('/api/tasks/task1/email-sources')
            self.assertEqual(response.status_code,200,response.text)
            self.assertEqual(response.json()['sources'],[])

    def test_source_membership_durable_reopen_and_cas(self):
        ref=self.task['source_references'][0]
        listing=self.client.get('/api/tasks/task1/email-sources?update_token=token1').json()
        self.assertEqual(listing['sources'][0]['id'],'src_'+hashlib.sha256(('task1\0'+ref).encode()).hexdigest()[:32])
        self.assertEqual(listing['sources'][0]['origin'],'historical')
        self.assertEqual(self.client.get('/api/tasks/task1/email-sources?update_token=old').status_code,409)
        d=self.create()
        self.assertEqual(d['to'],['reply@example.test'])
        self.assertFalse(d['can_send'])
        d2=self.save(d)
        self.assertTrue(d2['can_send'])
        self.assertEqual(self.client.patch('/api/email/drafts/'+d['id'],json={'expected_revision':d['revision'],'body':'stale'}).status_code,409)
        self.assertEqual(self.create(request_id='reopen')['body'],'Written by human')
        from email_workspace import EmailWorkspace
        other=EmailWorkspace(self.path,lambda tid:self.task,provider=self.provider)
        try:self.assertEqual(other.get(d['id'])['body'],d2['body'])
        finally:other.close()
        self.assertEqual(self.client.post('/api/email/drafts',json=dict(request_id='bad',task_id='task1',source_id='src_'+'0'*32,update_token=None,reply_all=False)).status_code,404)
        self.assertEqual(self.client.get('/denied/tasks/task1/email-sources').status_code,401)
        self.assertEqual(self.client.patch('/api/email/drafts/'+d['id'],json={'expected_revision':d2['revision'],'body':'x','to':['attacker@test']}).status_code,422)
