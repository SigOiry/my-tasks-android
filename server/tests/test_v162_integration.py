"""Cross-module storage/security gates for the shared v1.6.2 surface."""
import tempfile
import unittest
from pathlib import Path


class EmailStorageIntegrationTests(unittest.TestCase):
    def test_shared_authenticated_manual_task_has_no_reply_sources(self):
        from fastapi.testclient import TestClient
        from mobile_api import create_app, dashboard
        with tempfile.TemporaryDirectory(prefix='email-api-boundary-') as directory:
            root = Path(directory)
            token = root/'token'
            token.write_text('s' * 48)
            token.chmod(0o600)
            app = create_app(db_path=root/'tasks.json', token_file=token,
                             metadata_path=root/'events.sqlite', account_path=root/'account.sqlite',
                             conversation_path=root/'conversations.sqlite', session_path=root/'sessions.sqlite',
                             email_path=root/'email.sqlite', allow_legacy_uninitialized=True)
            try:
                task = dashboard.taskdb.Store(root/'tasks.json').add('Synthetic manual-only task')
                before = (root/'tasks.json').read_bytes()
                with TestClient(app) as client:
                    path = '/api/tasks/' + task['id'] + '/email-sources'
                    self.assertEqual(client.get(path).status_code, 401)
                    response = client.get(path, headers={'Authorization': 'Bearer ' + 's' * 48})
                    self.assertEqual(response.status_code, 200, response.text)
                    self.assertEqual(response.json()['sources'], [])
                    self.assertEqual((root/'tasks.json').read_bytes(), before)
                self.assertTrue((root/'email.sqlite').is_file())
            finally:
                app.state.conversations.close()
                workspace = getattr(app.state, 'email_workspace', None)
                if workspace is not None:
                    workspace.close()

    def test_email_store_alias_is_rejected_before_any_store_constructor(self):
        from mobile_api import create_app
        import inspect
        self.assertIn('email_path', inspect.signature(create_app).parameters)
        with tempfile.TemporaryDirectory(prefix='email-path-boundary-') as directory:
            root = Path(directory)
            token = root/'token'
            token.write_text('s' * 48)
            token.chmod(0o600)
            task = root/'tasks.json'
            task.write_text('PRIVATE SENTINEL; NOT A VALID DATABASE')
            before = task.read_bytes()
            for same in (task, token, root/'events.sqlite', root/'conversations.sqlite', root/'account.sqlite'):
                with self.subTest(path=same.name), self.assertRaisesRegex(ValueError, 'distinct dedicated private paths'):
                    create_app(db_path=task, token_file=token, metadata_path=root/'events.sqlite',
                               conversation_path=root/'conversations.sqlite', account_path=root/'account.sqlite',
                               session_path=root/'sessions.sqlite', email_path=same,
                               allow_legacy_uninitialized=True)
                self.assertEqual(task.read_bytes(), before)
            self.assertEqual(sorted(p.name for p in root.iterdir()), ['tasks.json', 'token'])
