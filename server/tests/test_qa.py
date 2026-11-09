import tempfile
import unittest
from pathlib import Path

class QaTests(unittest.TestCase):
    def test_seed_conversations_and_smoke_are_scratch_only(self):
        from qa_server import prepare
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as directory:
            root = Path(directory)
            config = prepare(root,9138,seed=True,seed_conversations=True)
            self.assertEqual(config['conversation_path'],root/'conversations.sqlite')
            from fastapi.testclient import TestClient
            from mobile_api import create_app
            app = create_app(**config)
            self.addCleanup(app.state.conversations.close)
            client = TestClient(app)
            auth = {'Authorization':'Bearer '+Path(config['token_file']).read_text()}
            conversations = client.get('/api/conversations',headers=auth).json()['conversations']
            self.assertGreaterEqual(len(conversations),3)
            self.assertTrue(any(c['task_id'] is None for c in conversations))
            attached = [c['task_id'] for c in conversations if c['task_id']]
            self.assertLess(len(set(attached)),len(attached))
            prepare(root,9138,seed=True,seed_conversations=True)
            self.assertEqual(len(client.get('/api/conversations',headers=auth).json()['conversations']),len(conversations),'Seed must be idempotent')
            import subprocess,sys,json
            command = [sys.executable,'conversation_smoke.py','--root',str(root/'smoke')]
            result = subprocess.run(command,capture_output=True,text=True,check=True)
            report = json.loads(result.stdout)
            self.assertEqual(report['status'],'succeeded')
            self.assertEqual(report['messages'],2)
            self.assertFalse(report['real_provider'])
            self.assertNotIn(Path(config['token_file']).read_text(),result.stdout+result.stderr)
        with self.assertRaises(ValueError):
            prepare('/opt/data/never-qa-production',9138,seed=True,seed_conversations=True)

    def test_account_qa_is_private_and_ignores_production_account_environment(self):
        import os
        from unittest.mock import patch
        from qa_server import prepare
        from fastapi.testclient import TestClient
        from mobile_api import create_app
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as directory:
            root = Path(directory)
            with patch.dict(os.environ,{'MOBILE_ACCOUNT_DB':str(root/'foreign-account.sqlite')}):
                config = prepare(root,9138,seed=True,account=True)
                self.assertEqual(config['account_path'],root/'account.sqlite')
                client = TestClient(create_app(**config),base_url=config['web_origin'])
            password = (root/'qa-password').read_text()
            link = (root/'qa-activation').read_text().strip()
            self.assertEqual((root/'qa-password').stat().st_mode & 0o077,0)
            self.assertEqual((root/'qa-activation').stat().st_mode & 0o077,0)
            response = client.post('/auth/login',json={'username':'qa-owner','password':password},headers={'Origin':config['web_origin']})
            self.assertEqual(response.status_code,428)
            response = client.post('/auth/activate',json={'activation_code':link.split('#activate=')[1]},headers={'Origin':config['web_origin']})
            self.assertEqual(response.status_code,200,response.text)
            self.assertEqual(client.get('/api/tasks').status_code,428)
            prepare(root,9138,seed=True,account=True)
            self.assertEqual((root/'qa-password').read_text(),password)

    def test_qa_configuration_has_only_isolated_paths_and_loopback_origin(self):
        from qa_server import prepare
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as directory:
            root = Path(directory)
            config = prepare(root, 9138, seed=True)
            self.assertEqual(config['web_origin'],'http://127.0.0.1:9138')
            self.assertFalse(config['cookie_secure'])
            for key in ('db_path','token_file','metadata_path','session_path','account_path','conversation_path'):
                self.assertTrue(Path(config[key]).is_relative_to(root))
            self.assertEqual(Path(config['token_file']).stat().st_mode & 0o077,0)
            from fastapi.testclient import TestClient
            from mobile_api import create_app
            client = TestClient(create_app(**config),base_url=config['web_origin'])
            token = Path(config['token_file']).read_text()
            response = client.post('/auth/login',json={'token':token},headers={'Origin':config['web_origin']})
            self.assertEqual(response.status_code,200)
            self.assertEqual(len(client.get('/api/tasks').json()['tasks']),5)
            self.assertNotIn('Secure;',response.headers['set-cookie'])
