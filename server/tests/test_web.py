"""All writes use private scratch fixtures; never the canonical database."""
import tempfile
import unittest
from pathlib import Path
from fastapi.testclient import TestClient
from mobile_api import create_app

class WebTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.token = 'test-only-' + 's'*48
        secret = self.root/'token'
        secret.write_text(self.token)
        secret.chmod(0o600)
        self.kwargs = dict(db_path=self.root/'tasks.json', token_file=secret, metadata_path=self.root/'events.sqlite',
                           session_path=self.root/'sessions.sqlite', web_origin='https://testserver')
        self.client = TestClient(create_app(**self.kwargs), base_url='https://testserver')
        self.origin = {'Origin':'https://testserver'}

    def login(self):
        response = self.client.post('/auth/login',json={'token':self.token},headers=self.origin)
        self.assertEqual(response.status_code,200,response.text)
        return {'Origin':'https://testserver','X-CSRF-Token':response.json()['csrf']}

    def test_login_cookie_session_and_bearer_parity(self):
        self.assertFalse(self.client.get('/auth/session').json()['authenticated'])
        self.assertEqual(self.client.get('/api/tasks').status_code,401)
        csrf = self.login()
        cookie = self.client.cookies.get('mytasks_session')
        self.assertNotIn(self.token,cookie)
        self.assertGreaterEqual(len(cookie),40)
        response = self.client.get('/auth/session')
        self.assertTrue(response.json()['authenticated'])
        self.assertEqual(response.json()['csrf'],csrf['X-CSRF-Token'])
        self.assertNotIn(self.token,response.text)
        created = self.client.post('/api/tasks',json={'title':'Cookie task'},headers=csrf)
        self.assertEqual(created.status_code,201,created.text)
        self.assertEqual(self.client.get('/api/tasks').json()['tasks'][0]['title'],'Cookie task')
        bearer = TestClient(create_app(**self.kwargs),base_url='https://testserver')
        self.assertEqual(bearer.get('/api/tasks',headers={'Authorization':'Bearer '+self.token}).json(),self.client.get('/api/tasks').json())
        self.assertEqual(bearer.post('/api/tasks',headers={'Authorization':'Bearer '+self.token},json={'title':'Android unchanged'}).status_code,201)
        self.assertEqual(self.client.get('/api/tasks',headers={'Authorization':'Bearer wrong'}).status_code,401)

    def assert_safe_rejection(self, response):
        self.assertIn(response.status_code, (400, 403), response.text)
        self.assertEqual(response.headers['cache-control'], 'no-store')
        self.assertEqual(response.headers['x-content-type-options'], 'nosniff')
        self.assertIn("frame-ancestors 'none'", response.headers['content-security-policy'])
        self.assertEqual(response.headers['referrer-policy'], 'no-referrer')
        self.assertEqual(response.headers['x-frame-options'], 'DENY')
        self.assertIn('camera=()', response.headers['permissions-policy'])

    def fixture_bytes(self):
        return {str(p.relative_to(self.root)): p.read_bytes()
                for p in self.root.rglob('*') if p.is_file()}

    def test_repeated_security_headers_rejected_before_auth_selection(self):
        csrf = self.login()
        opaque = self.client.cookies.get('mytasks_session')
        bearer = 'Bearer ' + self.token
        cases = []
        for values in ((bearer, 'Bearer wrong'), ('Bearer wrong', bearer)):
            for cookie in (False, True):
                cases.append(('Authorization', values, cookie, False))
        for name, values in (('Origin', ('https://testserver', 'https://evil.example')),
                             ('X-CSRF-Token', (csrf['X-CSRF-Token'], 'wrong'))):
            for ordered in (values, values[::-1]):
                for use_bearer in (False, True):
                    cases.append((name, ordered, True, use_bearer))
        # Identical duplicates are ambiguous too, not just conflicting values.
        for name, value in (('Authorization', bearer), ('Origin', 'https://testserver'),
                            ('X-CSRF-Token', csrf['X-CSRF-Token'])):
            cases.append((name, (value, value), True, False))
        for name, values, cookie, use_bearer in cases:
            headers = [(key, value) for key, value in csrf.items() if key.lower() != name.lower()]
            if use_bearer:
                headers.append(('Authorization', bearer))
            headers.extend([(name.lower(), values[0]), (name.upper(), values[1])])
            self.client.cookies.clear()
            if cookie:
                self.client.cookies.set('mytasks_session', opaque)
            with self.subTest(name=name, values=values, cookie=cookie, bearer=use_bearer):
                before = self.fixture_bytes()
                response = self.client.post('/api/tasks', json={'title':'Must not exist'}, headers=headers)
                self.assert_safe_rejection(response)
                self.assertEqual(self.fixture_bytes(), before)
        # A rejected edit must leave an already-existing task byte-for-byte intact.
        self.client.cookies.set('mytasks_session', opaque)
        created = self.client.post('/api/tasks', json={'title':'Original'}, headers=csrf).json()
        for name, values, _, _ in cases:
            headers = [(key, value) for key, value in csrf.items() if key.lower() != name.lower()]
            headers.extend([(name.lower(), values[0]), (name.upper(), values[1])])
            before = self.fixture_bytes()
            response = self.client.patch('/api/tasks/'+created['id'],
                                         json={'title':'Must not change','revision':created['revision']}, headers=headers)
            self.assert_safe_rejection(response)
            self.assertEqual(self.fixture_bytes(), before)
        # Auth endpoints must reject duplicates before attempts/session writes too.
        for path, payload in (('/auth/login', {'token':self.token}), ('/auth/logout', None)):
            for name, values, _, _ in cases:
                headers = [(key, value) for key, value in csrf.items() if key.lower() != name.lower()]
                headers.extend([(name.lower(), values[0]), (name.upper(), values[1])])
                before = self.fixture_bytes()
                self.assert_safe_rejection(self.client.post(path, json=payload, headers=headers))
                self.assertEqual(self.fixture_bytes(), before)

    def test_non_ascii_csrf_byte_ff_is_safe_403(self):
        self.login()
        before = self.fixture_bytes()
        # Bytes preserve the exact malformed wire value; do not UTF-8 encode ÿ.
        headers = [(b'origin', b'https://testserver'), (b'x-csrf-token', b'\xff')]
        response = self.client.post('/api/tasks', json={'title':'Blocked'}, headers=headers)
        self.assertEqual(response.status_code, 403)
        self.assert_safe_rejection(response)
        self.assertEqual(self.fixture_bytes(), before)
        response = self.client.post('/auth/logout', headers=headers)
        self.assertEqual(response.status_code, 403)
        self.assert_safe_rejection(response)
        self.assertEqual(self.fixture_bytes(), before)
        self.assertTrue(self.client.get('/auth/session').json()['authenticated'])

    def test_csrf_logout_and_cookie_flags(self):
        response = self.client.post('/auth/login',json={'token':self.token},headers=self.origin)
        cookie = response.headers['set-cookie']
        for flag in ('HttpOnly','Secure','SameSite=strict','Max-Age=43200'):
            self.assertIn(flag,cookie)
        csrf = response.json()['csrf']
        for headers in ({},self.origin,{'Origin':'https://evil.example','X-CSRF-Token':csrf},{'Origin':'https://testserver','X-CSRF-Token':'wrong'}):
            self.assertEqual(self.client.post('/api/tasks',json={'title':'Blocked'},headers=headers).status_code,403)
        self.assertFalse((self.root/'tasks.json').exists())
        opaque = self.client.cookies.get('mytasks_session')
        self.assertEqual(self.client.post('/auth/logout',headers={**self.origin,'X-CSRF-Token':csrf}).status_code,200)
        self.client.cookies.set('mytasks_session',opaque)
        self.assertFalse(self.client.get('/auth/session').json()['authenticated'])
        self.assertEqual(self.client.get('/api/tasks').status_code,401)

    def test_login_rate_limit_origin_oversize_and_security_headers(self):
        self.assertEqual(self.client.post('/auth/login',json={'token':self.token}).status_code,403)
        for _ in range(5):
            self.assertEqual(self.client.post('/auth/login',json={'token':'bad'},headers=self.origin).status_code,401)
        self.assertEqual(self.client.post('/auth/login',json={'token':self.token},headers=self.origin).status_code,429)
        self.assertEqual(self.client.post('/auth/login',content=b'x'*65537,headers=self.origin).status_code,413)
        for path in ('/auth/session','/api/tasks','/health'):
            response = self.client.get(path)
            self.assertEqual(response.headers['cache-control'],'no-store')
            self.assertEqual(response.headers['x-content-type-options'],'nosniff')
            self.assertIn("default-src 'self'",response.headers['content-security-policy'])
            self.assertIn("frame-ancestors 'none'",response.headers['content-security-policy'])
            self.assertEqual(response.headers['referrer-policy'],'no-referrer')

    def test_session_expiry_restart_rotation_and_private_metadata(self):
        import sqlite3
        from contextlib import closing
        self.login()
        opaque = self.client.cookies.get('mytasks_session')
        restarted = TestClient(create_app(**self.kwargs),base_url='https://testserver')
        restarted.cookies.set('mytasks_session',opaque)
        self.assertTrue(restarted.get('/auth/session').json()['authenticated'])
        with closing(sqlite3.connect(self.root/'sessions.sqlite')) as con, con:
            dump = '\n'.join(con.iterdump())
            self.assertNotIn(opaque,dump)
            self.assertNotIn(self.token,dump)
            con.execute('UPDATE sessions SET expires=0')
        self.assertFalse(restarted.get('/auth/session').json()['authenticated'])
        self.login()
        opaque = self.client.cookies.get('mytasks_session')
        (self.root/'token').write_text('other-token-'+'z'*48)
        rotated = TestClient(create_app(**self.kwargs),base_url='https://testserver')
        rotated.cookies.set('mytasks_session',opaque)
        self.assertFalse(rotated.get('/auth/session').json()['authenticated'])
        self.assertEqual((self.root/'sessions.sqlite').stat().st_mode & 0o077,0)

    def test_release_status_is_authenticated_bounded_and_sanitized(self):
        import io
        import json
        from unittest.mock import patch
        self.assertEqual(self.client.get('/api/release-status').status_code,401)
        csrf = self.login()
        import releases
        releases.clear_cache()
        fixture={'tag_name':'v2.3.4','name':'My Tasks v2.3.4','published_at':'2026-11-09T10:00:00Z','html_url':'https://github.com/SigOiry/my-tasks-android/releases/tag/v2.3.4'}
        with patch('releases.urlopen',return_value=io.BytesIO(json.dumps(fixture).encode())) as call:
            response = self.client.get('/api/release-status')
            self.assertEqual(response.status_code,200,response.text)
            self.assertEqual(response.json()['tag'],'v2.3.4')
            self.assertEqual(response.json()['release_url'],fixture['html_url'])
            self.assertNotIn('authorization',{k.lower():v for k,v in call.call_args.args[0].headers.items()})
            self.assertEqual(call.call_args.kwargs['timeout'],10)
            self.client.get('/api/release-status')
            self.assertEqual(call.call_count,1)
        releases.clear_cache()
        with patch('releases.urlopen',side_effect=RuntimeError('private network error')):
            response = self.client.get('/api/release-status')
            self.assertEqual(response.status_code,503)
            self.assertNotIn('private network error',response.text)
        releases.clear_cache()

    def test_cookie_body_cap_and_corrupt_sessions_fail_closed(self):
        csrf = self.login()
        self.assertEqual(self.client.post('/api/tasks',headers=csrf,content=b'x'*65537).status_code,413)
        self.assertFalse((self.root/'tasks.json').exists())
        (self.root/'sessions.sqlite').write_bytes(b'not sqlite')
        response = self.client.get('/api/tasks')
        self.assertEqual(response.status_code,503)
        self.assertEqual(response.headers['cache-control'],'no-store')
        self.assertNotIn('sqlite',response.text.lower())

    def test_login_validation_never_echoes_credentials(self):
        for payload in ({'token':self.token,'extra':True},{'token':self.token*100},{'token':{'value':self.token}}):
            response = self.client.post('/auth/login',json=payload,headers=self.origin)
            self.assertEqual(response.status_code,422)
            self.assertNotIn(self.token,response.text)
        from mobile_api import create_app
        with self.assertRaises(ValueError):
            create_app(**{**self.kwargs,'web_origin':'https://remote.example','cookie_secure':False})

    def test_public_shell_assets_info_and_no_traversal(self):
        response = self.client.get('/')
        self.assertEqual(response.status_code,200)
        self.assertIn('My Tasks',response.text)
        self.assertIn('type="module"',response.text)
        self.assertNotIn('<script>',response.text)
        self.assertEqual(self.client.head('/').status_code,200)
        info = self.client.get('/app-info').json()
        self.assertEqual(set(info),{'version','android_release_url','default_server'})
        self.assertEqual(info['default_server'],'https://task.oirysimon.com')
        self.assertEqual(info['android_release_url'],'https://github.com/SigOiry/my-tasks-android/releases/latest')
        for name in ('app.js','policy.mjs','app.css'):
            asset = self.client.get('/assets/'+name)
            self.assertEqual(asset.status_code,200,name)
            self.assertNotIn(self.token,asset.text)
        for path in ('/assets/%2e%2e/mobile_api.py','/assets/.secrets/mobile_token','/assets/../web_auth.py','/assets/not-found.js','/mobile_api.py'):
            self.assertEqual(self.client.get(path).status_code,404,path)

if __name__ == '__main__':
    unittest.main()
