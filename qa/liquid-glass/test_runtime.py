"""Real canonical integration, all state in a fresh scratch child."""
import importlib.util
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent

class RuntimeTests(unittest.TestCase):
    def test_seed_is_canonical_private_and_has_representative_data(self):
        self.assertTrue((HERE/'runtime.py').exists(), 'isolated runtime not implemented')
        spec = importlib.util.spec_from_file_location('liquid_runtime', HERE/'runtime.py')
        runtime = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(runtime)
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as directory:
            root = Path(directory)/'fixture'
            with runtime.Fixture(root, 9147) as fixture:
                with fixture.client() as client:
                    self.assertEqual(client.get('/api/tasks').status_code, 401)
                    auth = fixture.login(client)
                    tasks = client.get('/api/tasks', headers=auth).json()['tasks']
                    self.assertGreaterEqual(len(tasks), 9)
                    self.assertEqual(len({t['project'] for t in tasks}), 2)
                    self.assertGreaterEqual(sum(t['notification']['unread'] for t in tasks), 2)
                    threads = client.get('/api/conversations', headers=auth).json()['conversations']
                    self.assertEqual(len(threads), 3)
                    free = next(t for t in threads if t['task_id'] is None)
                    page = client.get('/api/conversations/'+free['id'], headers=auth).json()
                    self.assertTrue(page['has_more'])
                    self.assertTrue(all('SYNTHETIC QA' in m['content'] for m in page['messages'] if m['role']=='assistant'))
                    self.assertEqual(client.get('/api/tasks', headers={'Authorization':'Bearer '+(root/'qa-token').read_text()}).status_code, 401)
                    task = next(t for t in tasks if t['notification']['unread'])
                    url = '/api/tasks/'+task['id']
                    token = task['notification']['token']
                    self.assertEqual(client.patch(url,headers=auth,json={'revision':'0'*64,'title':'must not apply'}).status_code,409)
                    fixture_task = runtime.dashboard.taskdb.Store(root/'tasks.json')
                    fixture_task.edit(task['id'],description='SYNTHETIC QA — a newer source update arrived during review.')
                    self.assertEqual(client.post(url+'/updates/read',headers=auth,json={'token':token}).status_code,409)
                    latest = client.get(url,headers=auth).json()
                    self.assertTrue(latest['notification']['unread'])
                    exact = latest['notification']['token']
                    reviewed = client.post(url+'/updates/read',headers=auth,json={'token':exact})
                    self.assertEqual(reviewed.status_code,200)
                    self.assertFalse(reviewed.json()['notification']['unread'])
                    current = client.get(url,headers=auth).json()
                    edit = client.patch(url,headers=auth,json={'revision':current['revision'],'notes':'SYNTHETIC QA — confirmed revision edit'})
                    self.assertEqual(edit.status_code,200)
                    self.assertEqual(client.get(url,headers=auth).json()['notes'],'SYNTHETIC QA — confirmed revision edit')
                    import time,uuid
                    route = '/api/conversations/'+free['id']
                    calls = fixture.provider_calls
                    payload = {'request_id':str(uuid.uuid4()),'content':'Synthetic idempotency probe','mode':'question','expected_revision':free['revision']}
                    admission = client.post(route+'/messages',headers=auth,json=payload)
                    self.assertEqual(admission.status_code,202)
                    self.assertEqual(client.post(route+'/messages',headers=auth,json={**payload,'content':'changed'}).status_code,409)
                    for _ in range(300):
                        state = client.get(route+'/requests/'+payload['request_id'],headers=auth).json()
                        if state['status'] not in {'queued','running'}: break
                        time.sleep(.01)
                    self.assertEqual(state['status'],'succeeded')
                    replay = client.post(route+'/messages',headers=auth,json=payload)
                    self.assertEqual(replay.status_code,202)
                    self.assertEqual(replay.json()['user_message']['id'],admission.json()['user_message']['id'])
                    self.assertEqual(fixture.provider_calls,calls+1)
                    persisted_id = payload['request_id']
                self.assertEqual((root/'qa-password').stat().st_mode & 0o777, 0o600)
                self.assertTrue((root/'qa-cert.pem').exists(), 'QA TLS certificate required')
                self.assertEqual((root/'qa-key.pem').stat().st_mode & 0o777, 0o600)
            with runtime.Fixture(root, 9147) as reopened:
                with reopened.client() as client:
                    auth = reopened.login(client)
                    self.assertEqual(len(client.get('/api/tasks',headers=auth).json()['tasks']),len(tasks))
                    self.assertEqual(len(client.get('/api/conversations',headers=auth).json()['conversations']),3)
                    state = client.get(route+'/requests/'+persisted_id,headers=auth).json()
                    self.assertEqual(state['status'],'succeeded')
                    self.assertIn('SYNTHETIC QA',state['assistant_message']['content'])
                    self.assertEqual(reopened.provider_calls,0,'restart must not replay provider work')

if __name__ == '__main__': unittest.main()
