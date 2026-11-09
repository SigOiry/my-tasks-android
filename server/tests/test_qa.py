import tempfile
import unittest
from pathlib import Path

class QaTests(unittest.TestCase):
    def test_qa_configuration_has_only_isolated_paths_and_loopback_origin(self):
        from qa_server import prepare
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as directory:
            root = Path(directory)
            config = prepare(root, 9138, seed=True)
            self.assertEqual(config['web_origin'],'http://127.0.0.1:9138')
            self.assertFalse(config['cookie_secure'])
            for key in ('db_path','token_file','metadata_path','session_path'):
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
