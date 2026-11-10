"""Conversation integration tests use synthetic private scratch stores only."""
import json
import os
import subprocess
import sys
import tempfile
import time
import unittest
import uuid
from pathlib import Path
from fastapi.testclient import TestClient


class ConversationTests(unittest.TestCase):
    def setUp(self):
        from unittest.mock import patch
        title_patch = patch('conversations.generate_title', False)
        title_patch.start()
        self.addCleanup(title_patch.stop)
        self.tmp = tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        token = self.root/'token'
        token.write_text('s'*48)
        token.chmod(0o600)
        self.kwargs = dict(allow_legacy_uninitialized=True,account_path=self.root/'account.sqlite',db_path=self.root/'tasks.json', token_file=token,
                           metadata_path=self.root/'events.sqlite',session_path=self.root/'web-sessions.sqlite',conversation_path=self.root/'conversations.sqlite')
        from mobile_api import create_app
        self.app = create_app(**self.kwargs)
        self.addCleanup(self.app.state.conversations.close)
        self.client = TestClient(self.app)
        self.auth = {'Authorization':'Bearer '+'s'*48}

    def create(self, **fields):
        response = self.client.post('/api/conversations',headers=self.auth,json={'task_id':None,**fields})
        self.assertEqual(response.status_code,201,response.text)
        return response.json()

    def send(self, thread, content='A general question', **fields):
        payload = {'request_id':str(uuid.uuid4()),'content':content,'mode':'question','expected_revision':thread['revision'],**fields}
        return self.client.post('/api/conversations/'+thread['id']+'/messages',headers=self.auth,json=payload),payload

    def wait(self,cid,rid):
        for _ in range(300):
            response = self.client.get('/api/conversations/'+cid+'/requests/'+rid,headers=self.auth)
            self.assertEqual(response.status_code,200,response.text)
            request = response.json()
            if request['status'] not in {'queued','running'}:
                return request
            time.sleep(.01)
        self.fail('Request did not finish')

    def test_research_citations_progress_replay_and_reopen_are_durable(self):
        from unittest.mock import patch
        from types import SimpleNamespace as NS
        phases = []
        thread = self.create()
        store = self.app.state.conversations
        def provider(**kwargs):
            if kwargs['tools']:
                phases.append(store.get(thread['id'])['conversation']['latest_request'].get('web_phase'))
            return NS(choices=[NS(message=NS(content='Evidence-backed answer', tool_calls=[]))])
        source = 'https://www.python.org/downloads/'
        with patch('hermes_chat.call_llm', side_effect=provider) as model, patch('public_web.search_public', return_value=[{'url': source, 'title': 'Python', 'description': 'Public evidence'}]) as search:
            response, payload = self.send(thread, content='Search latest Python release')
            self.assertEqual(response.status_code, 202)
            final = self.wait(thread['id'], payload['request_id'])
            self.assertEqual(final['status'], 'succeeded')
            self.assertEqual(final['assistant_message']['content'], 'Evidence-backed answer')
            self.assertEqual(final['assistant_message']['citation_metadata']['sources'][0]['url'],source)
            self.assertIsNone(final['web_phase'])
            replay = self.client.post('/api/conversations/'+thread['id']+'/messages', headers=self.auth, json=payload)
            self.assertEqual(replay.json()['request']['assistant_message'], final['assistant_message'])
            self.assertEqual(search.call_count, 1)
            self.assertEqual(model.call_count, 2)
            self.assertEqual(phases, ['answering'])
        from conversations import ConversationStore
        reopened = ConversationStore(self.root/'conversations.sqlite')
        self.assertEqual(source, reopened.request(thread['id'], payload['request_id'])['assistant_message']['citation_metadata']['sources'][0]['url'])
        script = 'from conversations import ConversationStore; import sys,json; print(json.dumps(ConversationStore(sys.argv[1]).request(sys.argv[2],sys.argv[3])))'
        fresh = subprocess.run([sys.executable, '-c', script, str(self.root/'conversations.sqlite'), thread['id'], payload['request_id']], capture_output=True, text=True, check=True)
        self.assertEqual(json.loads(fresh.stdout)['assistant_message'], final['assistant_message'])

    def test_failed_private_turn_never_enters_current_public_research(self):
        from unittest.mock import patch
        from types import SimpleNamespace as NS
        from conversations import SendMessage
        private = 'The merger codename is Bluebird.'
        public = 'Who won the 2026 Nobel Prize in Physics?'
        thread = self.create()
        with patch('hermes_chat.call_llm', side_effect=RuntimeError('Injected provider failure')):
            response, failed_payload = self.send(thread, content=private)
            self.assertEqual(response.status_code, 202)
            self.assertEqual(self.wait(thread['id'], failed_payload['request_id'])['status'], 'failed')
        thread = self.app.state.conversations.get(thread['id'])['conversation']
        seen = []
        def provider(**kwargs):
            seen.append(kwargs)
            return NS(choices=[NS(message=NS(content='Public answer', tool_calls=[]))])
        with patch('hermes_chat.call_llm', side_effect=provider), patch('public_web.search_public', return_value=[{
                'url': 'https://www.python.org/', 'title': 'Python', 'description': 'Public release evidence'}]) as search:
            response, payload = self.send(thread, content=public)
            self.assertEqual(response.status_code, 202)
            final = self.wait(thread['id'], payload['request_id'])
            self.assertEqual(final['status'], 'succeeded')
            self.assertEqual([call.args[0] for call in search.call_args_list], [public])
            research = [call for call in seen if call['tools']]
            self.assertTrue(research)
            self.assertNotIn(private, json.dumps(research))
            self.assertIn(public, json.dumps(research))
            self.assertIn(private, json.dumps(seen[-1]['messages']))
            self.assertEqual(seen[-1]['tools'], [])
            replay = self.app.state.conversations.submit(thread['id'], SendMessage(**payload))
            self.assertEqual(replay['request']['assistant_message'], final['assistant_message'])
            self.assertEqual(search.call_count, 1)
            self.assertEqual(len(seen), 2)
        page = self.app.state.conversations.get(thread['id'])
        self.assertEqual([m['content'] for m in page['messages'] if m['role'] == 'user'], [private, public])

    def test_async_persist_before_provider_replay_and_history_after_fresh_process(self):
        from threading import Event
        from types import SimpleNamespace
        from unittest.mock import patch
        entered,release = Event(),Event()
        calls = []
        def provider(**kwargs):
            calls.append(kwargs)
            entered.set()
            self.assertTrue(release.wait(5))
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='General answer',tool_calls=[]))])
        free = self.create()
        other = self.create()
        self.addCleanup(release.set)
        with patch('hermes_chat.call_llm',side_effect=provider):
            response,payload = self.send(free,content='Explain stellar spectra')
            self.assertEqual(response.status_code,202,response.text)
            accepted = response.json()
            self.assertEqual(accepted['user_message']['content'],'Explain stellar spectra')
            self.assertEqual(accepted['conversation']['revision'],2)
            self.assertTrue(entered.wait(5))
            url = '/api/conversations/'+free['id']
            self.assertEqual(self.client.get(url,headers=self.auth).json()['messages'],[accepted['user_message']])
            exact = self.client.post(url+'/messages',headers=self.auth,json=payload)
            self.assertEqual(exact.status_code,202,exact.text)
            self.assertEqual(exact.json()['user_message'],accepted['user_message'])
            for changed in ({'content':'Different'},{'mode':'email_draft'}):
                self.assertEqual(self.client.post(url+'/messages',headers=self.auth,json={**payload,**changed}).status_code,409)
            self.assertEqual(self.client.post('/api/conversations/'+other['id']+'/messages',headers=self.auth,json=payload).status_code,409)
            self.assertEqual(self.client.get('/api/conversations/'+other['id']+'/requests/'+payload['request_id'],headers=self.auth).status_code,404)
            pending,_ = self.send({**free,'revision':2})
            self.assertEqual(pending.status_code,409)
            release.set()
            final = self.wait(free['id'],payload['request_id'])
            self.assertEqual(final['status'],'succeeded')
            self.assertEqual(final['assistant_message']['content'],'General answer')
            replay = self.client.post(url+'/messages',headers=self.auth,json=payload)
            self.assertEqual(replay.status_code,202)
            self.assertEqual(len(calls),1)
            self.assertEqual(calls[0]['tools'],[])
            self.assertIn('general',calls[0]['messages'][0]['content'].lower())
            self.assertNotIn('Answer only questions about',str(calls[0]['messages']))
            self.assertEqual(calls[0]['messages'][-1],{'role':'user','content':'Explain stellar spectra'})
        first = self.client.get(url+'?limit=1',headers=self.auth).json()
        self.assertEqual(first['messages'],[final['assistant_message']])
        self.assertTrue(first['has_more'])
        older = self.client.get(url,params={'limit':1,'before':first['before_cursor']},headers=self.auth).json()
        self.assertEqual(older['messages'],[accepted['user_message']])
        self.assertFalse(older['has_more'])
        script = 'from conversations import ConversationStore; import sys,json; print(json.dumps(ConversationStore(sys.argv[1]).get(sys.argv[2])))'
        result = subprocess.run([sys.executable,'-c',script,str(self.root/'conversations.sqlite'),free['id']],capture_output=True,text=True,check=True)
        self.assertEqual([m['content'] for m in json.loads(result.stdout)['messages']],['Explain stellar spectra','General answer'])

    def test_restart_interrupts_running_and_queued_requests_and_fences_old_workers(self):
        from threading import Event
        from unittest.mock import patch
        from types import SimpleNamespace
        from mobile_api import create_app
        entered,release = Event(),Event()
        calls = []
        def provider(**kwargs):
            calls.append(kwargs)
            entered.set()
            release.wait(8)
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Obsolete reply',tool_calls=[]))])
        self.addCleanup(release.set)
        threads = [self.create(title=str(i)) for i in range(3)]
        with patch('hermes_chat.call_llm',side_effect=provider):
            submitted = [self.send(t) for t in threads]
            self.assertTrue(entered.wait(5))
            script = ('from mobile_api import create_app; from pathlib import Path; import sys,json; '
                      'r=Path(sys.argv[1]); a=create_app(allow_legacy_uninitialized=True,account_path=r/"account.sqlite",db_path=r/"tasks.json",token_file=r/"token",metadata_path=r/"events.sqlite",session_path=r/"web-sessions.sqlite",conversation_path=r/"conversations.sqlite"); '
                      'print(json.dumps([c["latest_request"]["status"] for c in a.state.conversations.list()["conversations"]])); '
                      'a.state.conversations.close()')
            fresh = subprocess.run([sys.executable,'-c',script,str(self.root)],capture_output=True,text=True,check=True)
            self.assertEqual(json.loads(fresh.stdout),['interrupted']*3)
            restarted = create_app(**self.kwargs)
            self.addCleanup(restarted.state.conversations.close)
            client = TestClient(restarted)
            for thread,(response,payload) in zip(threads,submitted):
                self.assertEqual(response.status_code,202)
                status = client.get('/api/conversations/'+thread['id']+'/requests/'+payload['request_id'],headers=self.auth).json()
                self.assertEqual(status['status'],'interrupted')
                self.assertIsNone(status['assistant_message'])
                replay = client.post('/api/conversations/'+thread['id']+'/messages',headers=self.auth,json=payload)
                self.assertEqual(replay.status_code,202)
                self.assertEqual(replay.json()['request']['status'],'interrupted')
            release.set()
            self.app.state.conversations.close()
            self.assertLessEqual(len(calls),2,'Queued orphan must never reach provider')
            for thread,(_,payload) in zip(threads,submitted):
                page = client.get('/api/conversations/'+thread['id'],headers=self.auth).json()
                self.assertEqual(len(page['messages']),1)
                self.assertEqual(page['conversation']['latest_request']['status'],'interrupted')
            # Explicit new identity is the only retry path.
            self.client = client
            current = client.get('/api/conversations/'+threads[0]['id'],headers=self.auth).json()['conversation']
            response,payload = self.send(current)
            self.assertEqual(response.status_code,202)
            self.assertEqual(self.wait(current['id'],payload['request_id'])['status'],'succeeded')

    def test_bounded_workers_queue_and_concurrent_idempotency(self):
        from concurrent.futures import ThreadPoolExecutor
        from threading import Event, Lock
        from unittest.mock import patch
        from types import SimpleNamespace
        release = Event()
        calls = []
        lock = Lock()
        def provider(**kwargs):
            with lock:
                calls.append(kwargs)
            release.wait(10)
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Done',tool_calls=[]))])
        self.addCleanup(release.set)
        threads = [self.create(title=str(i)) for i in range(9)]
        with patch('hermes_chat.call_llm',side_effect=provider):
            payload = {'request_id':str(uuid.uuid4()),'content':'Exactly once','mode':'question','expected_revision':1}
            url = '/api/conversations/'+threads[0]['id']+'/messages'
            with ThreadPoolExecutor(max_workers=6) as pool:
                responses = list(pool.map(lambda _:self.client.post(url,headers=self.auth,json=payload),range(6)))
            self.assertEqual([r.status_code for r in responses],[202]*6)
            self.assertEqual(len({r.json()['user_message']['id'] for r in responses}),1)
            accepted = [(threads[0],payload)]
            for thread in threads[1:8]:
                response,sent = self.send(thread)
                self.assertEqual(response.status_code,202)
                accepted.append((thread,sent))
            refused,_ = self.send(threads[8])
            self.assertEqual(refused.status_code,429,refused.text)
            self.assertEqual(self.client.get('/api/conversations/'+threads[8]['id'],headers=self.auth).json()['messages'],[])
            self.assertLessEqual(len(calls),2)
            release.set()
            for thread,sent in accepted:
                self.assertEqual(self.wait(thread['id'],sent['request_id'])['status'],'succeeded')
            self.assertEqual(len(calls),8)

    def test_failed_provider_preserved_sanitized_replay_and_saved_task_context(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        from mobile_api import dashboard
        task = dashboard.taskdb.Store(self.root/'tasks.json').add('Saved context',description='Authoritative evidence')
        thread = self.create(task_id=task['id'])
        before = (self.root/'tasks.json').read_bytes()
        with patch('hermes_chat.call_llm',side_effect=RuntimeError('secret-provider-url PRIVATE')) as call:
            response,payload = self.send(thread,content='Keep this failed message')
            self.assertEqual(response.status_code,202)
            failed = self.wait(thread['id'],payload['request_id'])
            self.assertEqual(failed['status'],'failed')
            self.assertNotIn('PRIVATE',json.dumps(failed))
            self.assertIsNone(failed['assistant_message'])
            replay = self.client.post('/api/conversations/'+thread['id']+'/messages',headers=self.auth,json=payload)
            self.assertEqual(replay.json()['request']['status'],'failed')
            self.assertEqual(call.call_count,1)
            self.assertIn('Authoritative evidence',json.dumps(call.call_args.kwargs['messages']))
        current = self.client.get('/api/conversations/'+thread['id'],headers=self.auth).json()['conversation']
        result = SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Draft only',tool_calls=[]))])
        with patch('hermes_chat.call_llm',return_value=result) as call:
            response,payload = self.send(current,content='Try a different question')
            self.assertEqual(response.status_code,202)
            self.assertEqual(self.wait(thread['id'],payload['request_id'])['status'],'succeeded')
            self.assertIn('Keep this failed message',json.dumps(call.call_args.kwargs['messages']))
        self.assertEqual((self.root/'tasks.json').read_bytes(),before)

    def test_full_history_persisted_rolling_summary_bounds_context_across_reopen(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        thread = self.create()
        seen = []
        def provider(**kwargs):
            seen.append(kwargs)
            text = 'Persisted memory: first marker is ALPHA.' if kwargs['max_tokens']==1400 else 'R'*5000
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=text,tool_calls=[]))])
        expected = []
        with patch('hermes_chat.call_llm',side_effect=provider):
            for i in range(7):
                content = ('ALPHA' if i==0 else str(i))+'x'*7900
                response,payload = self.send(thread,content=content)
                self.assertEqual(response.status_code,202)
                self.assertEqual(self.wait(thread['id'],payload['request_id'])['status'],'succeeded')
                page = self.client.get('/api/conversations/'+thread['id'],headers=self.auth).json()
                thread = page['conversation']
                expected.extend([content,'R'*5000])
        self.assertTrue(page['context_compacted'])
        summaries = [call for call in seen if call['max_tokens']==1400]
        self.assertGreater(len(summaries),1)
        self.assertIn('ALPHA',json.dumps(summaries[0]['messages']))
        self.assertIn('Persisted memory',json.dumps(summaries[-1]['messages']))
        for call in seen:
            self.assertEqual(call['tools'],[])
            self.assertLessEqual(sum(len(m['content']) for m in call['messages']),44000)
        self.assertIn('Persisted memory',json.dumps(seen[-1]['messages']))
        messages = []
        cursor = None
        while True:
            params = {'limit':3}
            if cursor:
                params['before'] = cursor
            page = self.client.get('/api/conversations/'+thread['id'],headers=self.auth,params=params).json()
            messages = page['messages']+messages
            if not page['has_more']:
                break
            cursor = page['before_cursor']
        self.assertEqual([m['content'] for m in messages],expected)
        self.assertEqual(len({m['id'] for m in messages}),14)
        import sqlite3
        with self.app.state.conversations.connect() as con:
            row = con.execute('SELECT summary,summary_until FROM conversations WHERE id=?',(thread['id'],)).fetchone()
            self.assertEqual(row['summary'],'Persisted memory: first marker is ALPHA.')
            self.assertGreater(row['summary_until'],0)
        from mobile_api import create_app
        restarted = create_app(**self.kwargs)
        self.addCleanup(restarted.state.conversations.close)
        self.client = TestClient(restarted)
        with patch('hermes_chat.call_llm',side_effect=provider):
            response,payload = self.send(thread,content='What was the first marker?')
            self.assertEqual(response.status_code,202)
            self.assertEqual(self.wait(thread['id'],payload['request_id'])['status'],'succeeded')
            self.assertIn('Persisted memory',json.dumps(seen[-1]['messages']))

    def test_summary_failure_keeps_full_history_without_fabricated_memory(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        thread = self.create()
        result = SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Original answer '*3000,tool_calls=[]))])
        with patch('hermes_chat.call_llm',return_value=result):
            response,payload = self.send(thread,content='Original user')
            self.assertEqual(self.wait(thread['id'],payload['request_id'])['status'],'succeeded')
        page = self.client.get('/api/conversations/'+thread['id'],headers=self.auth).json()
        with patch('hermes_chat.call_llm',side_effect=RuntimeError('SECRET summary endpoint')) as call:
            response,payload = self.send(page['conversation'],content='Follow up')
            failed = self.wait(thread['id'],payload['request_id'])
            self.assertEqual(failed['status'],'failed')
            self.assertIn('summary',failed['error'])
            self.assertNotIn('SECRET',json.dumps(failed))
            self.assertEqual(call.call_count,1,'No answer call after summary failure')
        page = self.client.get('/api/conversations/'+thread['id'],headers=self.auth).json()
        self.assertFalse(page['context_compacted'])
        self.assertEqual([m['content'] for m in page['messages']],['Original user','Original answer '*3000,'Follow up'])
        with self.app.state.conversations.connect() as con:
            self.assertEqual(con.execute('SELECT summary FROM conversations').fetchone()[0],'')

    def test_rejects_surrogates_and_strict_messages_without_input_echo(self):
        thread = self.create()
        url = '/api/conversations/'+thread['id']
        create = self.client.post('/api/conversations',headers=self.auth,content=json.dumps({'task_id':None,'title':'private'+chr(0xD800)}),)
        self.assertEqual(create.status_code,422,create.text)
        for fields in ({'request_id':'bad'}, {'request_id':str(uuid.uuid4()).upper()}, {'content':' '}, {'content':'x'*8001}, {'content':7}, {'expected_revision':True}, {'expected_revision':1.0}, {'mode':'send'}, {'history':[]}, {'tools':[]}, {'provider_url':'private sentinel'}, {'content':'private'+chr(0xD800)}):
            payload = {'request_id':str(uuid.uuid4()),'content':'private sentinel','mode':'question','expected_revision':1,**fields}
            response = self.client.post(url+'/messages',headers=self.auth,content=json.dumps(payload))
            self.assertEqual(response.status_code,422,response.text)
            self.assertNotIn('private',response.text)
        self.assertEqual(self.client.get(url,headers=self.auth).json()['messages'],[])

    def test_static_conversations_module_is_explicitly_allowlisted(self):
        from unittest.mock import patch
        from mobile_api import create_app
        bundle = self.root/'web_dist'
        bundle.mkdir()
        (bundle/'conversations.mjs').write_text('export const version = 13;')
        (bundle/'credentials.mjs').write_text('private sentinel')
        with patch('mobile_api.__file__',str(self.root/'mobile_api.py')):
            app = create_app(**self.kwargs)
        self.addCleanup(app.state.conversations.close)
        client = TestClient(app)
        response = client.get('/assets/conversations.mjs')
        self.assertEqual(response.status_code,200,response.text)
        self.assertIn('javascript',response.headers['content-type'])
        self.assertEqual(client.get('/assets/credentials.mjs').status_code,404)
        (bundle/'conversations.mjs').unlink()
        (bundle/'conversations.mjs').symlink_to(bundle/'credentials.mjs')
        self.assertEqual(client.get('/assets/conversations.mjs').status_code,404)

    def test_conversation_metadata_rejects_other_databases_and_symlinks(self):
        from conversations import ConversationStore
        import sqlite3
        target = self.root/'unrelated.sqlite'
        from contextlib import closing
        with closing(sqlite3.connect(target)) as con, con:
            con.execute('CREATE TABLE private_other (content TEXT)')
        before = target.read_bytes()
        with self.assertRaises(ValueError):
            ConversationStore(target)
        self.assertEqual(target.read_bytes(),before)
        alias = self.root/'alias.sqlite'
        alias.symlink_to(target)
        with self.assertRaises(OSError):
            ConversationStore(alias)
        self.assertEqual(target.read_bytes(),before)
        with self.assertRaises(ValueError):
            ConversationStore(self.root/'state.db')
        self.assertFalse((self.root/'state.db').exists())

    def test_many_failed_short_messages_cannot_escape_context_bound(self):
        from conversations import now
        from types import SimpleNamespace
        from unittest.mock import patch
        thread = self.create()
        # Failed user-only history is a real supported outcome; separators and
        # per-turn overhead must count toward context, not only visible text.
        with self.app.state.conversations.connect() as con:
            for _ in range(2000):
                rid,stamp = str(uuid.uuid4()),now()
                con.execute('INSERT INTO requests(id,conversation_id,content,mode,status,created_at) VALUES(?,?,?,?,?,?)',(rid,thread['id'],'x','question','failed',stamp))
                con.execute('INSERT INTO messages(conversation_id,request_id,role,content,created_at) VALUES(?,?,?,?,?)',(thread['id'],rid,'user','x',stamp))
        seen = []
        def provider(**kwargs):
            seen.append(kwargs)
            return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Concise genuine provider summary or answer',tool_calls=[]))])
        with patch('hermes_chat.call_llm',side_effect=provider):
            response,payload = self.send(thread)
            self.assertEqual(response.status_code,202)
            self.assertEqual(self.wait(thread['id'],payload['request_id'])['status'],'succeeded')
        self.assertTrue(all(sum(len(m['content']) for m in call['messages'])<=44000 for call in seen))
        self.assertTrue(any(call['max_tokens']==1400 for call in seen))

    def test_shutdown_closes_admission_and_cookie_security_protects_conversations(self):
        from mobile_api import create_app
        app = create_app(**self.kwargs)
        client = TestClient(app,base_url='https://task.oirysimon.com')
        with client:
            login = client.post('/auth/login',headers={'Origin':'https://task.oirysimon.com'},json={'token':'s'*48})
            self.assertEqual(login.status_code,200)
            csrf = {'Origin':'https://task.oirysimon.com','X-CSRF-Token':login.json()['csrf']}
            self.assertEqual(client.post('/api/conversations',json={'task_id':None}).status_code,403)
            thread = client.post('/api/conversations',headers=csrf,json={'task_id':None}).json()
            url = '/api/conversations/'+thread['id']
            self.assertEqual(client.patch(url,json={'expected_revision':1,'archived':True}).status_code,403)
            payload = {'request_id':str(uuid.uuid4()),'content':'private sentinel','mode':'question','expected_revision':1}
            self.assertEqual(client.post(url+'/messages',json=payload).status_code,403)
            duplicate = [('Authorization','Bearer '+'s'*48),('authorization','Bearer wrong')]
            self.assertEqual(client.post(url+'/messages',headers=duplicate,json=payload).status_code,400)
            self.assertEqual(client.post(url+'/messages',headers=csrf,content=b'x'*65537).status_code,413)
            self.assertEqual(client.get(url).json()['messages'],[])
        # A stopped worker pool must reject a new admission without a phantom.
        response = TestClient(app).post(url+'/messages',headers=self.auth,json=payload)
        self.assertEqual(response.status_code,429,response.text)
        self.assertEqual(TestClient(app).get(url,headers=self.auth).json()['messages'],[])

    def test_latest_request_uses_admission_order_not_timestamp_or_random_id(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        result = SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='  Full reply\n',tool_calls=[]))])
        thread = self.create()
        with patch('conversations.now',return_value='2026-11-09T12:00:00.000000+00:00'),patch('hermes_chat.call_llm',return_value=result):
            for rid in ('ffffffff-ffff-ffff-ffff-ffffffffffff','00000000-0000-0000-0000-000000000001'):
                response,payload = self.send(thread,content='  Exact user message\n',request_id=rid)
                self.assertEqual(response.status_code,202)
                final = self.wait(thread['id'],rid)
                self.assertEqual(final['status'],'succeeded')
                page = self.client.get('/api/conversations/'+thread['id'],headers=self.auth).json()
                thread = page['conversation']
                self.assertEqual(thread['latest_request']['id'],rid)
                self.assertEqual(page['messages'][-2]['content'],'  Exact user message\n')
                self.assertEqual(page['messages'][-1]['content'],'  Full reply\n')

    def test_attached_email_draft_marks_bounded_saved_task_excerpt(self):
        from unittest.mock import patch
        from types import SimpleNamespace
        from mobile_api import dashboard
        task = dashboard.taskdb.Store(self.root/'tasks.json').add('Large saved task',description='Authoritative evidence '+'x'*30000)
        thread = self.create(task_id=task['id'])
        before = (self.root/'tasks.json').read_bytes()
        result = SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='Subject: Review\nDraft for human review only.',tool_calls=[]))])
        with patch('hermes_chat.call_llm',return_value=result) as call:
            response,payload = self.send(thread,content='Prepare a draft',mode='email_draft')
            self.assertEqual(response.status_code,202)
            self.assertEqual(self.wait(thread['id'],payload['request_id'])['status'],'succeeded')
            messages = call.call_args.kwargs['messages']
            self.assertIn('Mode: email_draft',messages[0]['content'])
            self.assertIn('excerpt; incomplete',messages[0]['content'])
            self.assertIn('Authoritative evidence',messages[0]['content'])
            self.assertLessEqual(sum(len(m['content']) for m in messages),44000)
            self.assertEqual(call.call_args.kwargs['tools'],[])
        self.assertEqual((self.root/'tasks.json').read_bytes(),before)

    def test_revision_rename_archive_recovery_and_validation(self):
        thread = self.create()
        url = '/api/conversations/'+thread['id']
        for payload in ({'title':'Renamed'}, {'expected_revision':True,'title':'Bad'}, {'expected_revision':1,'title':'  '}, {'expected_revision':1,'archived':'true'}, {'expected_revision':1,'provider':'https://evil'}):
            self.assertEqual(self.client.patch(url,headers=self.auth,json=payload).status_code,422)
        renamed = self.client.patch(url,headers=self.auth,json={'expected_revision':1,'title':'Renamed'}).json()
        self.assertEqual(renamed['revision'],2)
        self.assertEqual(renamed['title'],'Renamed')
        self.assertEqual(self.client.patch(url,headers=self.auth,json={'expected_revision':1,'title':'Stale'}).status_code,409)
        archived = self.client.patch(url,headers=self.auth,json={'expected_revision':2,'archived':True}).json()
        self.assertTrue(archived['archived'])
        self.assertEqual(self.client.get('/api/conversations',headers=self.auth).json()['conversations'],[])
        self.assertEqual(self.client.get('/api/conversations?archived=true',headers=self.auth).json()['conversations'][0]['id'],thread['id'])
        self.assertEqual(self.client.get(url,headers=self.auth).json()['conversation']['title'],'Renamed')
        restored = self.client.patch(url,headers=self.auth,json={'expected_revision':3,'archived':False}).json()
        self.assertFalse(restored['archived'])
        self.assertEqual(self.client.request('DELETE',url,headers=self.auth).status_code,405)
        for suffix in ('not-a-uuid',thread['id'].upper(),str(uuid.uuid4()).replace('-','')):
            self.assertEqual(self.client.get('/api/conversations/'+suffix,headers=self.auth).status_code,422)
        for query in ('scope=task','scope=unknown','limit=0','limit=101','cursor=bad','cursor=W10'):
            self.assertEqual(self.client.get('/api/conversations?'+query,headers=self.auth).status_code,422,query)
        self.assertEqual(self.client.get(url+'?before=bad',headers=self.auth).status_code,422)
        self.assertEqual(self.client.post('/api/conversations',headers=self.auth,json={'task_id':'missing','title':'private sentinel'}).status_code,404)
        for payload in ({'task_id':None,'title':''},{'task_id':None,'title':'x'*201},{'task_id':7},{'task_id':None,'history':[]},{'task_id':None,'title':'private sentinel','tools':[]}):
            response = self.client.post('/api/conversations',headers=self.auth,json=payload)
            self.assertEqual(response.status_code,422,response.text)
            self.assertNotIn('private sentinel',response.text)
        self.assertEqual(self.client.post('/api/conversations',json={'task_id':None}).status_code,401)

    def test_multiple_task_threads_free_pagination_and_durable_reopen(self):
        from mobile_api import dashboard
        store = dashboard.taskdb.Store(self.root/'tasks.json')
        task = store.add('Saved task')
        before = (self.root/'tasks.json').read_bytes()
        first = self.create(task_id=task['id'],title='First')
        second = self.create(task_id=task['id'],title='Second')
        free = self.create(title='General questions')
        self.assertEqual(first['task_title'],'Saved task')
        self.assertNotEqual(first['id'],second['id'])
        self.assertIsNone(free['task_id'])
        listing = self.client.get('/api/conversations?limit=2',headers=self.auth).json()
        self.assertEqual(len(listing['conversations']),2)
        tail = self.client.get('/api/conversations',params={'limit':2,'cursor':listing['next_cursor']},headers=self.auth).json()
        self.assertEqual(len(tail['conversations']),1)
        self.assertIsNone(tail['next_cursor'])
        self.assertEqual({c['id'] for c in listing['conversations']+tail['conversations']},{first['id'],second['id'],free['id']})
        self.assertEqual(len(self.client.get('/api/conversations',params={'scope':'task','task_id':task['id']},headers=self.auth).json()['conversations']),2)
        self.assertEqual([c['id'] for c in self.client.get('/api/conversations?scope=free',headers=self.auth).json()['conversations']],[free['id']])
        page = self.client.get('/api/conversations/'+first['id'],headers=self.auth).json()
        self.assertEqual(page['messages'],[])
        self.assertFalse(page['context_compacted'])
        script = 'from conversations import ConversationStore; import sys,json; print(json.dumps(ConversationStore(sys.argv[1]).get(sys.argv[2])))'
        result = subprocess.run([sys.executable,'-c',script,str(self.root/'conversations.sqlite'),first['id']],capture_output=True,text=True,check=True)
        self.assertEqual(json.loads(result.stdout)['conversation']['title'],'First')
        self.assertEqual((self.root/'tasks.json').read_bytes(),before)
        self.assertEqual((self.root/'conversations.sqlite').stat().st_mode & 0o077,0)
