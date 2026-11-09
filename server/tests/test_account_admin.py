"""Local provisioning tests: files and output must never disclose credentials."""
import os
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

class AccountAdminTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.password = 'private fixture provisioning password'
        self.input = self.root/'password'
        self.input.write_text(self.password)
        self.input.chmod(0o600)
        self.db,self.activation = self.root/'account.sqlite',self.root/'activation'

    def run_cli(self,*args):
        env = dict(os.environ,MOBILE_ACCOUNT_DB=str(self.root/'foreign-account.sqlite'),MOBILE_TASK_DB=str(self.root/'foreign-tasks.json'))
        return subprocess.run([sys.executable,str(Path(__file__).resolve().parents[1]/'account_admin.py'),*map(str,args)],capture_output=True,text=True,env=env)

    def init(self):
        return self.run_cli('init','--db',self.db,'--username','fixture-owner','--password-file',self.input,'--activation-file',self.activation,'--origin','https://testserver')

    def test_private_init_no_secret_output_and_no_reset(self):
        response = self.init()
        self.assertEqual(response.returncode,0,response.stderr)
        text = self.activation.read_text()
        self.assertRegex(text,r'^https://testserver/#activate=[0-9a-f]{64}\n$')
        self.assertNotIn(self.password,response.stdout+response.stderr)
        self.assertNotIn(text.strip(),response.stdout+response.stderr)
        self.assertEqual(self.activation.stat().st_mode & 0o077,0)
        before = self.db.read_bytes()
        other = self.root/'other-link'
        refused = self.run_cli('init','--db',self.db,'--username','other','--password-file',self.input,'--activation-file',other,'--origin','https://testserver')
        self.assertNotEqual(refused.returncode,0)
        self.assertFalse(other.exists())
        self.assertEqual(self.db.read_bytes(),before)

    def test_private_file_required_and_activation_only_rotation(self):
        self.input.chmod(0o644)
        response = self.init()
        self.assertNotEqual(response.returncode,0)
        self.assertFalse(self.db.exists())
        self.input.chmod(0o600)
        self.assertEqual(self.init().returncode,0)
        rotated = self.root/'rotated'
        response = self.run_cli('rotate-activation','--db',self.db,'--activation-file',rotated,'--origin','https://testserver')
        self.assertEqual(response.returncode,0,response.stderr)
        self.assertNotEqual(self.activation.read_text(),rotated.read_text())
        from account_auth import AccountStore
        store = AccountStore(self.db)
        with store.connect() as con: con.execute('UPDATE account SET active=1')
        forbidden = self.root/'forbidden'
        response = self.run_cli('rotate-activation','--db',self.db,'--activation-file',forbidden,'--origin','https://testserver')
        self.assertNotEqual(response.returncode,0)
        self.assertFalse(forbidden.exists())

if __name__ == '__main__': unittest.main()
