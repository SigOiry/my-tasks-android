"""Private scratch auth fixtures; never production credentials or task stores."""
import tempfile
import unittest
from pathlib import Path
from fastapi.testclient import TestClient
from mobile_api import create_app

class AccountHTTPTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.secret = 'fixture-private-owner-' + 'x'*40
        self.initial = 'fixture initial password only'
        (self.root/'token').write_text(self.secret)
        (self.root/'token').chmod(0o600)
        self.config = dict(db_path=self.root/'tasks.json',token_file=self.root/'token',metadata_path=self.root/'events.sqlite',session_path=self.root/'web-sessions.sqlite',conversation_path=self.root/'conversations.sqlite',account_path=self.root/'account.sqlite',web_origin='https://testserver')
        from account_auth import AccountStore
        self.store = AccountStore(self.root/'account.sqlite')
        self.code = self.store.initialize('fixture-owner',self.initial)
        # Exercise actual Uvicorn HTTP/TCP, not just an ASGI in-process client.
        import httpx
        import socket
        import threading
        import time
        import uvicorn
        sock = socket.socket()
        sock.bind(('127.0.0.1',0))
        self.config['cookie_secure'] = False  # loopback QA only, never production
        app = create_app(**self.config)
        self.app = app
        server = uvicorn.Server(uvicorn.Config(app,log_level='critical',access_log=False,proxy_headers=False))
        thread = threading.Thread(target=lambda:server.run(sockets=[sock]),daemon=True)
        thread.start()
        def stop():
            server.should_exit = True
            thread.join(timeout=10)
            sock.close()
            self.assertFalse(thread.is_alive())
        self.addCleanup(stop)
        deadline = time.monotonic()+10
        while not server.started and time.monotonic()<deadline: time.sleep(.01)
        self.assertTrue(server.started)
        self.client = httpx.Client(base_url=f'http://127.0.0.1:{sock.getsockname()[1]}',timeout=15,trust_env=False)
        self.addCleanup(self.client.close)
        self.origin = {'Origin':'https://testserver'}

    def test_public_initial_password_never_grants_ownership(self):
        for path in ('/auth/login','/auth/native/login'):
            response = self.client.post(path,json={'username':'fixture-owner','password':self.initial},headers=self.origin)
            self.assertEqual(response.status_code,428,response.text)
            self.assertNotIn('set-cookie',response.headers)
            self.assertNotIn('session_token',response.text)
        self.assertEqual(self.client.get('/api/tasks',headers={'Authorization':'Bearer '+self.secret}).status_code,428)
        self.assertFalse(self.client.get('/auth/session').json()['authenticated'])

    def clear_attempts(self):
        with self.store.connect() as con:
            con.execute('DELETE FROM account_attempts')

    def activate(self):
        response = self.client.post('/auth/activate',json={'activation_code':self.code},headers=self.origin)
        self.assertEqual(response.status_code,200,response.text)
        return {**self.origin,'X-CSRF-Token':response.json()['csrf']}

    def change(self,headers,current=None,new='A durable secret phrase 2026!'):
        return self.client.post('/auth/password',json={'current_password':current or self.initial,'new_password':new},headers=headers)

    def test_activation_is_one_time_restricted_then_epoch_revokes_every_old_credential(self):
        from web_auth import Sessions,COOKIE
        legacy = Sessions(self.root/'web-sessions.sqlite',self.secret)
        old_cookie,_ = legacy.issue()
        headers = self.activate()
        restricted_cookie = self.client.cookies.get(COOKIE)
        self.assertTrue(self.client.get('/auth/session').json()['password_change_required'])
        self.assertEqual(self.client.get('/api/tasks').status_code,428)
        self.assertEqual(self.client.post('/auth/activate',json={'activation_code':self.code},headers=self.origin).status_code,401)
        native = self.client.post('/auth/native/login',json={'username':'fixture-owner','password':self.initial},headers={'Authorization':'Bearer '+self.secret})
        self.assertEqual(native.status_code,200,native.text)
        old_native = native.json()['session_token']
        self.assertEqual(self.client.get('/api/tasks',headers={'Authorization':'Bearer '+old_native}).status_code,428)
        self.clear_attempts()
        changed = self.change(headers)
        self.assertEqual(changed.status_code,200,changed.text)
        self.assertFalse(changed.json()['password_change_required'])
        self.assertEqual(self.client.get('/api/tasks').status_code,200)
        for opaque in (self.secret,old_native):
            self.assertEqual(self.client.get('/api/tasks',headers={'Authorization':'Bearer '+opaque}).status_code,401)
        for opaque in (old_cookie,restricted_cookie):
            self.client.cookies.clear()
            self.client.cookies.set(COOKIE,opaque)
            self.assertEqual(self.client.get('/api/tasks').status_code,401)
        self.assertEqual(self.client.post('/auth/activate',json={'activation_code':self.code},headers=self.origin).status_code,401)

    def test_password_database_has_only_slow_hashes_and_no_raw_credentials(self):
        headers = self.activate()
        self.assertEqual(self.change(headers).status_code,200)
        snapshot = self.store.snapshot()
        import hashlib
        self.assertNotEqual(snapshot['initial_hash'],hashlib.sha256(self.initial.encode()).hexdigest())
        with self.store.connect() as con:
            dump = '\n'.join(con.iterdump())
        for secret in (self.initial,self.code,self.secret,self.client.cookies.get('mytasks_session')):
            self.assertNotIn(secret,dump)
        self.assertEqual((self.root/'account.sqlite').stat().st_mode & 0o077,0)

    def test_csrf_origin_strict_fields_and_current_password(self):
        headers = self.activate()
        for supplied in ({},self.origin,{**headers,'Origin':'https://evil.example'},{**headers,'X-CSRF-Token':'wrong'}):
            self.clear_attempts()
            self.assertEqual(self.change(supplied).status_code,403)
        self.clear_attempts()
        self.assertEqual(self.change(headers,current='wrong').status_code,401)
        self.assertEqual(self.change(headers,new=self.initial).status_code,422)
        self.assertEqual(self.change(headers,new='passwordpassword').status_code,422)
        self.assertEqual(self.change(headers,new='password123456789').status_code,422)
        self.clear_attempts()
        self.assertEqual(self.change(headers,new='a'*15).status_code,422)
        self.assertEqual(self.change(headers,new='12345678901234567890').status_code,422)
        for path,payload in [('/auth/login',{'username':'fixture-owner','password':{'secret':self.initial}}),('/auth/activate',{'activation_code':self.code,'extra':self.initial}),('/auth/password',{'current_password':self.initial,'new_password':'short'})]:
            response = self.client.post(path,json=payload,headers=headers)
            self.assertEqual(response.status_code,422,response.text)
            self.assertNotIn(self.initial,response.text)
        self.assertEqual(self.client.post('/auth/native/login',json={'username':'fixture-owner','password':self.initial},headers={'Origin':'https://evil.example'}).status_code,403)
        self.assertEqual(self.client.post('/auth/login',content='{}',headers={**self.origin,'Content-Type':'text/plain'}).status_code,415)

    def test_duplicate_headers_and_bad_authorization_never_fallback(self):
        headers = self.activate()
        for path in ('/auth/login','/auth/password','/auth/session','/api/tasks'):
            supplied = {**headers,'Authorization':'Bearer bad'}
            if path=='/auth/session' or path=='/api/tasks':
                response = self.client.get(path,headers=supplied)
                self.assertEqual(response.status_code,200 if path=='/auth/session' else 401)
                if path=='/auth/session': self.assertFalse(response.json()['authenticated'])
            else:
                payload = {'username':'fixture-owner','password':self.initial} if path.endswith('login') else {'current_password':self.initial,'new_password':'A durable secret phrase 2026!'}
                self.assertEqual(self.client.post(path,json=payload,headers=supplied).status_code,401)
        for name,value in [('authorization','Bearer '+self.secret),('origin','https://testserver'),('x-csrf-token',headers['X-CSRF-Token'])]:
            supplied = [(k,v) for k,v in headers.items() if k.lower()!=name]+[(name,value),(name.upper(),value)]
            response = self.client.post('/auth/password',json={'current_password':self.initial,'new_password':'A durable secret phrase 2026!'},headers=supplied)
            self.assertEqual(response.status_code,400)

    def test_late_initialization_fails_closed_until_restart(self):
        import sqlite3
        other = self.root/'late.sqlite'
        kwargs = dict(self.config,account_path=other,allow_legacy_uninitialized=True)
        running = TestClient(create_app(**kwargs),base_url='https://testserver')
        self.assertEqual(running.get('/api/tasks',headers={'Authorization':'Bearer '+self.secret}).status_code,200)
        from account_auth import AccountStore
        AccountStore(other).initialize('late-owner',self.initial)
        self.assertEqual(running.get('/api/tasks',headers={'Authorization':'Bearer '+self.secret}).status_code,503)

    def test_native_cookie_isolation_expiry_logout_and_durable_restart(self):
        headers = self.activate()
        changed = self.change(headers)
        self.assertEqual(changed.status_code,200)
        password = 'A durable secret phrase 2026!'
        self.assertEqual(self.client.post('/auth/native/password',json={'current_password':password,'new_password':'Another durable phrase 2027!'}).status_code,401)
        self.assertEqual(self.client.post('/auth/native/logout').status_code,401)
        response = self.client.post('/auth/native/login',json={'username':'fixture-owner','password':password})
        self.assertEqual(response.status_code,200,response.text)
        self.assertNotIn('set-cookie',response.headers)
        self.assertNotIn('csrf',response.json())
        opaque = response.json()['session_token']
        self.assertRegex(opaque,r'^[0-9a-f]{64}$')
        native = {'Authorization':'Bearer '+opaque}
        self.clear_attempts()
        restarted = TestClient(create_app(**self.config),base_url='https://testserver')
        self.assertEqual(restarted.get('/api/tasks',headers=native).status_code,200)
        self.assertTrue(restarted.get('/auth/session',headers=native).json()['authenticated'])
        restarted.cookies.set('mytasks_session',opaque)
        self.assertEqual(restarted.get('/api/tasks').status_code,401)
        browser_cookie = self.client.cookies.get('mytasks_session')
        self.assertEqual(restarted.get('/api/tasks',headers={'Authorization':'Bearer '+browser_cookie}).status_code,401)
        self.assertEqual(restarted.post('/auth/native/logout',headers=native).status_code,200)
        self.assertEqual(restarted.get('/api/tasks',headers=native).status_code,401)
        response = restarted.post('/auth/native/login',json={'username':'fixture-owner','password':password})
        self.assertEqual(response.status_code,200)
        with self.store.connect() as con: con.execute('UPDATE account_sessions SET expires=0')
        self.assertFalse(self.client.get('/auth/session').json()['authenticated'])
        self.assertEqual(restarted.get('/api/tasks',headers={'Authorization':'Bearer '+response.json()['session_token']}).status_code,401)

    def test_login_rate_limit_and_hash_concurrency_parameter_validation(self):
        from unittest.mock import patch
        from account_auth import HASH_SLOTS
        owner = {'Authorization':'Bearer '+self.secret}
        for _ in range(5):
            self.assertEqual(self.client.post('/auth/native/login',json={'username':'fixture-owner','password':'bad'},headers=owner).status_code,401)
        with patch('account_auth.password_hash',side_effect=AssertionError('must rate-limit before hashing')):
            self.assertEqual(self.client.post('/auth/native/login',json={'username':'fixture-owner','password':self.initial},headers=owner).status_code,429)
        self.clear_attempts()
        HASH_SLOTS.acquire(); HASH_SLOTS.acquire()
        try:
            self.assertEqual(self.client.post('/auth/native/login',json={'username':'fixture-owner','password':self.initial},headers=owner).status_code,429)
        finally:
            HASH_SLOTS.release(); HASH_SLOTS.release()
        with self.store.connect() as con: con.execute('UPDATE account SET n=1073741824')
        with patch('account_auth.hashlib.scrypt',side_effect=AssertionError('must validate stored parameters before allocation')):
            response = self.client.post('/auth/native/login',json={'username':'fixture-owner','password':self.initial},headers=owner)
            self.assertEqual(response.status_code,503)
            self.assertNotIn('1073741824',response.text)

    def test_activation_expiry_and_concurrent_one_time_redemption(self):
        from concurrent.futures import ThreadPoolExecutor
        with ThreadPoolExecutor(2) as pool:
            results = list(pool.map(lambda _: self.client.post('/auth/activate',json={'activation_code':self.code},headers=self.origin).status_code,range(2)))
        self.assertEqual(sorted(results),[200,401])
        with self.store.connect() as con:
            from account_auth import digest
            con.execute('UPDATE account SET activation_hash=?,activation_expires=0',(digest(self.code),))
        self.assertEqual(self.client.post('/auth/activate',json={'activation_code':self.code},headers=self.origin).status_code,401)

    def test_simultaneous_password_change_exactly_one_wins_and_all_sessions_revoke(self):
        from concurrent.futures import ThreadPoolExecutor
        import threading
        from unittest.mock import patch
        headers = self.activate()
        snapshot = self.store.snapshot()
        identity = self.store.read(self.client.cookies.get('mytasks_session'),'browser')
        original = self.store.verify
        barrier = threading.Barrier(2)
        def synchronized(*args):
            value = original(*args)
            barrier.wait(timeout=10)
            return value
        def change(number):
            from fastapi import HTTPException
            try:
                self.store.change(snapshot,self.initial,f'A racing durable password {number}!','native',identity)
                return 200
            except HTTPException as error: return error.status_code
        with patch.object(self.store,'verify',side_effect=synchronized),ThreadPoolExecutor(2) as pool:
            results = list(pool.map(change,range(2)))
        self.assertEqual(sorted(results),[200,409])
        self.assertEqual(self.client.get('/api/tasks').status_code,401)
        self.assertEqual(self.store.snapshot()['epoch'],1)
        self.assertEqual(self.store.snapshot()['active'],1)

    def test_unicode_password_preserved_without_trimming_and_initial_reuse_rejected(self):
        headers = self.activate()
        new = '  Phrase de sécurité 🧭 文  '
        response = self.change(headers,new=new)
        self.assertEqual(response.status_code,200,response.text)
        headers = {**self.origin,'X-CSRF-Token':response.json()['csrf']}
        self.assertEqual(self.change(headers,current=new,new=self.initial).status_code,422)
        self.clear_attempts()
        self.assertEqual(self.client.post('/auth/login',json={'username':'fixture-owner','password':new.strip()},headers=self.origin).status_code,401)
        self.assertEqual(self.client.post('/auth/login',json={'username':'fixture-owner','password':new},headers=self.origin).status_code,200)
        self.assertEqual(self.client.post('/auth/login',content=b'{"username":"fixture-owner","password":"\\ud800"}',headers={**self.origin,'Content-Type':'application/json'}).status_code,422)

    def test_live_http_bootstrap_password_rotation_and_duplicate_headers(self):
        import http.client
        import json
        import socket
        import threading
        import time
        import uvicorn
        sock = socket.socket()
        sock.bind(('127.0.0.1',0))
        port = sock.getsockname()[1]
        origin = f'http://127.0.0.1:{port}'
        app = create_app(**dict(self.config,web_origin=origin,cookie_secure=False))
        server = uvicorn.Server(uvicorn.Config(app,log_level='critical',access_log=False,proxy_headers=False))
        thread = threading.Thread(target=lambda:server.run(sockets=[sock]),daemon=True)
        thread.start()
        try:
            deadline = time.monotonic()+10
            while not server.started and time.monotonic()<deadline: time.sleep(.01)
            self.assertTrue(server.started)
            connection = http.client.HTTPConnection('127.0.0.1',port,timeout=10)
            def request(method,path,payload=None,headers=None):
                body = json.dumps(payload) if payload is not None else None
                connection.request(method,path,body=body,headers={'Content-Type':'application/json',**(headers or {})})
                response = connection.getresponse()
                status,cookies = response.status,response.getheader('set-cookie')
                text = response.read().decode()
                return status,json.loads(text),cookies
            payload = {'username':'fixture-owner','password':self.initial}
            self.assertEqual(request('POST','/auth/login',payload,{'Origin':origin})[0],428)
            status,meta,cookie = request('POST','/auth/activate',{'activation_code':self.code},{'Origin':origin})
            self.assertEqual(status,200)
            cookie = cookie.split(';')[0]
            self.assertEqual(request('GET','/api/tasks',headers={'Cookie':cookie})[0],428)
            fields = {'current_password':self.initial,'new_password':'Live HTTP durable password 2026!'}
            status,full,new_cookie = request('POST','/auth/password',fields,{'Origin':origin,'Cookie':cookie,'X-CSRF-Token':meta['csrf']})
            self.assertEqual(status,200)
            self.assertFalse(full['password_change_required'])
            self.assertEqual(request('GET','/api/tasks',headers={'Cookie':new_cookie.split(';')[0]})[0],200)
            self.assertEqual(request('GET','/api/tasks',headers={'Authorization':'Bearer '+self.secret})[0],401)
            body = json.dumps(fields)
            connection.putrequest('POST','/auth/password')
            for key,value in [('Origin',origin),('Origin',origin),('Content-Type','application/json'),('Content-Length',str(len(body))),('Cookie',new_cookie.split(';')[0]),('X-CSRF-Token',full['csrf'])]: connection.putheader(key,value)
            connection.endheaders(body.encode())
            response = connection.getresponse()
            self.assertEqual(response.status,400)
            response.read()
            connection.close()
        finally:
            server.should_exit = True
            thread.join(timeout=10)
            sock.close()
            self.assertFalse(thread.is_alive())

    def test_account_path_collisions_refused_before_metadata_writes(self):
        for path in (self.root/'tasks.json',self.root/'events.sqlite',self.root/'web-sessions.sqlite',self.root/'conversations.sqlite',self.root/'token'):
            with self.subTest(path=path):
                with self.assertRaises(ValueError): create_app(**dict(self.config,account_path=path))

    def test_missing_initialized_account_fails_closed_without_explicit_fixture_override(self):
        with self.assertRaises(ValueError):
            create_app(**dict(self.config,account_path=self.root/'missing-account.sqlite'))

    def test_account_store_rejects_unrelated_databases_and_hermes_state(self):
        import sqlite3
        from account_auth import AccountStore
        unrelated = self.root/'unrelated.sqlite'
        from contextlib import closing
        with closing(sqlite3.connect(unrelated)) as con,con: con.execute('CREATE TABLE private_hermes_data(secret TEXT)')
        unrelated.chmod(0o600)
        before = unrelated.read_bytes()
        with self.assertRaises(ValueError): AccountStore(unrelated)
        self.assertEqual(unrelated.read_bytes(),before)
        with self.assertRaises(ValueError): AccountStore(self.root/'state.db')
        self.assertFalse((self.root/'state.db').exists())

    def test_fixed_font_and_license_are_public_assets_only(self):
        for name,media in [('inter-variable.woff2','font/woff2'),('INTER-LICENSE.txt','text/plain')]:
            response = self.client.get('/assets/'+name)
            self.assertEqual(response.status_code,200,response.text[:100])
            self.assertTrue(response.headers['content-type'].startswith(media))
            self.assertGreater(len(response.content),100)
        self.assertEqual(self.client.get('/assets/other-font.woff2').status_code,404)

    def test_real_http_concurrent_password_changes_and_login_cannot_resurrect_old_epoch(self):
        import threading
        import httpx
        from concurrent.futures import ThreadPoolExecutor
        from unittest.mock import patch
        headers = self.activate()
        restricted = self.client.cookies.get('mytasks_session')
        barrier = threading.Barrier(2)
        original = self.app.state.accounts.verify
        def verify(*args):
            valid = original(*args)
            barrier.wait(timeout=10)
            return valid
        def request(number):
            with httpx.Client(base_url=str(self.client.base_url),trust_env=False,timeout=20) as client:
                return client.post('/auth/password',json={'current_password':self.initial,'new_password':f'HTTP race durable phrase {number}!'},headers={**headers,'Cookie':'mytasks_session='+restricted})
        with patch.object(self.app.state.accounts,'verify',side_effect=verify),ThreadPoolExecutor(2) as pool:
            responses = list(pool.map(request,range(2)))
        self.assertEqual(sorted(r.status_code for r in responses),[200,409])
        self.assertEqual(self.client.get('/api/tasks').status_code,401)
        self.assertEqual(self.store.snapshot()['epoch'],1)
        # Login verification started against epoch1 must not issue after epoch2.
        winner = next(number for number,response in enumerate(responses) if response.status_code==200)
        password = f'HTTP race durable phrase {winner}!'
        cookie = responses[winner].cookies.get('mytasks_session')
        full_headers = {**self.origin,'X-CSRF-Token':responses[winner].json()['csrf'],'Cookie':'mytasks_session='+cookie}
        entered,release = threading.Event(),threading.Event()
        calls = 0
        lock = threading.Lock()
        def blocked_verify(*args):
            nonlocal calls
            value = original(*args)
            with lock:
                calls += 1
                block = calls==1
            if block:
                entered.set()
                if not release.wait(10): raise RuntimeError('test login release timed out')
            return value
        self.clear_attempts()
        def login():
            with httpx.Client(base_url=str(self.client.base_url),trust_env=False,timeout=20) as client:
                return client.post('/auth/login',json={'username':'fixture-owner','password':password},headers=self.origin)
        with patch.object(self.app.state.accounts,'verify',side_effect=blocked_verify),ThreadPoolExecutor(1) as pool:
            pending = pool.submit(login)
            try:
                self.assertTrue(entered.wait(5))
                changed = self.client.post('/auth/password',json={'current_password':password,'new_password':'Second HTTP epoch durable phrase!'},headers=full_headers)
                self.assertEqual(changed.status_code,200,changed.text)
            finally: release.set()
            stale = pending.result(timeout=15)
        self.assertEqual(stale.status_code,401,stale.text)
        self.assertNotIn('set-cookie',stale.headers)
        self.assertEqual(self.store.snapshot()['epoch'],2)

    def test_legacy_browser_proof_is_restricted_and_never_proves_native_ownership(self):
        from web_auth import Sessions
        legacy = Sessions(self.root/'web-sessions.sqlite',self.secret)
        opaque,csrf = legacy.issue()
        self.client.cookies.set('mytasks_session',opaque,domain=self.client.base_url.host)
        status = self.client.get('/auth/session').json()
        self.assertTrue(status['authenticated'])
        self.assertTrue(status['password_change_required'])
        self.assertEqual(status['csrf'],csrf)
        payload = {'username':'fixture-owner','password':self.initial}
        self.assertEqual(self.client.post('/auth/native/login',json=payload).status_code,428)
        response = self.client.post('/auth/login',json=payload,headers=self.origin)
        self.assertEqual(response.status_code,200,response.text)
        self.assertTrue(response.json()['password_change_required'])
        new_cookie = self.client.cookies.get('mytasks_session')
        self.assertNotEqual(new_cookie,opaque)
        self.client.cookies.clear()
        self.client.cookies.set('mytasks_session',opaque)
        self.assertFalse(self.client.get('/auth/session').json()['authenticated'])
        self.client.cookies.clear()
        self.client.cookies.set('mytasks_session',new_cookie)
        self.assertEqual(self.client.get('/api/conversations').status_code,428)
        self.assertEqual(self.client.post('/api/conversations',json={'task_id':None},headers={**self.origin,'X-CSRF-Token':response.json()['csrf']}).status_code,428)

    def test_native_bootstrap_direct_change_and_later_full_session_change(self):
        auth = {'Authorization':'Bearer '+self.secret}
        payload = {'current_password':self.initial,'new_password':'Native first durable phrase 2026!'}
        response = self.client.post('/auth/native/password',json=payload,headers=auth)
        self.assertEqual(response.status_code,200,response.text)
        self.assertNotIn('set-cookie',response.headers)
        self.assertFalse(response.json()['password_change_required'])
        first = response.json()['session_token']
        self.assertEqual(self.client.get('/api/tasks',headers={'Authorization':'Bearer '+self.secret}).status_code,401)
        auth = {'Authorization':'Bearer '+first}
        response = self.client.post('/auth/native/password',json={'current_password':payload['new_password'],'new_password':'Native second durable phrase 2027!'},headers=auth)
        self.assertEqual(response.status_code,200,response.text)
        self.assertEqual(self.client.get('/api/tasks',headers=auth).status_code,401)
        auth = {'Authorization':'Bearer '+response.json()['session_token']}
        self.assertEqual(self.client.get('/api/tasks',headers=auth).status_code,200)
        self.assertEqual(self.client.post('/api/tasks',json={'title':'Private native fixture'},headers=auth).status_code,201)
        with self.store.connect() as con:
            self.assertEqual(con.execute('SELECT count(*) FROM account_sessions').fetchone()[0],1)

    def test_existing_legacy_proof_is_preserved_without_new_length_normalization(self):
        large = 'private-long-fixture-proof-'+'x'*512
        (self.root/'token').write_text(large)
        restarted = TestClient(create_app(**self.config),base_url='https://testserver')
        response = restarted.post('/auth/native/login',json={'username':'fixture-owner','password':self.initial},headers={'Authorization':'Bearer '+large})
        self.assertEqual(response.status_code,200,response.text)
        self.assertTrue(response.json()['password_change_required'])

if __name__ == '__main__': unittest.main()
