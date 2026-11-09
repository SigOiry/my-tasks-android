"""Poison only synthetic scratch targets; helpers must not inherit their stores."""
import contextlib
import io
import os
import tempfile
import unittest
import uuid
from pathlib import Path
from unittest.mock import patch

from conversations import ConversationStore, NewConversation, now


class FixtureIsolationTests(unittest.TestCase):
    def test_http_fixture_and_live_smoke_leave_foreign_pending_database_untouched(self):
        from tests.test_account_auth import AccountHTTPTests
        import live_smoke

        for helper in ('account_http', 'live_smoke'):
            with self.subTest(helper=helper), tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as name:
                root = Path(name)
                foreign = ConversationStore(root/'foreign-conversations.sqlite')
                thread = foreign.create(NewConversation(task_id=None), None)
                rid = str(uuid.uuid4())
                with foreign.connect() as con:
                    con.execute('INSERT INTO requests(id,conversation_id,content,mode,status,created_at) VALUES(?,?,?,?,?,?)',
                                (rid,thread['id'],'Synthetic pending question','question','queued',now()))
                before = foreign.path.read_bytes()
                # Never inherit an actual environment target, including on the RED run.
                poison = {'MOBILE_CONVERSATIONS_DB':str(foreign.path),
                          'MOBILE_ACCOUNT_DB':str(root/'foreign-account.sqlite'),
                          'MOBILE_METADATA_DB':str(root/'foreign-events.sqlite'),
                          'MOBILE_TASK_DB':str(root/'foreign-tasks.json'),
                          'MOBILE_TOKEN_FILE':str(root/'foreign-token')}
                with patch.dict(os.environ,poison), contextlib.redirect_stdout(io.StringIO()):
                    if helper == 'account_http':
                        result = unittest.TestResult()
                        AccountHTTPTests('test_public_initial_password_never_grants_ownership').run(result)
                        self.assertTrue(result.wasSuccessful(),str(result.errors)+str(result.failures))
                    else:
                        # Exercise the real scratch HTTP helper without a live provider.
                        with patch('mobile_api.answer',return_value={'reply':'Synthetic fixture answer'}):
                            live_smoke.main()
                with foreign.connect() as con:
                    status = con.execute('SELECT status FROM requests WHERE id=?',(rid,)).fetchone()[0]
                self.assertEqual(status,'queued')
                self.assertEqual(foreign.path.read_bytes(),before)
                self.assertEqual({p.name for p in root.iterdir()},{foreign.path.name})
