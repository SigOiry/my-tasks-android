"""Packaging tests: a signing key or live-store leak is a QA failure."""
import importlib.util
from contextlib import closing
import json
from pathlib import Path
import tempfile
import unittest

HERE = Path(__file__).resolve().parent

class LinuxPackagingTests(unittest.TestCase):
    def test_bundle_is_allowlisted_private_and_relocatable(self):
        spec = importlib.util.spec_from_file_location('linux_runtime', HERE/'linux_runtime.py')
        assert spec is not None and spec.origin is not None and spec.loader is not None
        self.assertTrue(Path(spec.origin).is_file(), 'Linux-local packaging helper is missing')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as directory:
            root = Path(directory)/'fixture'
            root.mkdir()
            (root/'liquid-glass-synthetic.json').write_text(json.dumps({'synthetic':True,'version':1}))
            (root/'tasks.json').write_text(json.dumps({'tasks':[{'description':'SYNTHETIC QA — test'}]}))
            for name in ('qa-token','qa-password','qa-cert.pem','qa-key.pem'):
                (root/name).write_text('synthetic')
            import sqlite3
            for name in ('account.sqlite','conversations.sqlite'):
                with closing(sqlite3.connect(root/name)) as db:
                    db.execute('CREATE TABLE fixture(value TEXT)')
                    db.execute("INSERT INTO fixture VALUES('synthetic')")
                    db.commit()
            for name in ('qa-signing.p12','qa-signing-password','sessions.sqlite','production.env'):
                (root/name).write_text('NEVER TRANSFER')
            bundle = Path(directory)/'bundle'
            module.package(root, bundle)
            files = {str(p.relative_to(bundle)) for p in bundle.rglob('*') if p.is_file()}
            self.assertFalse(any('signing' in f or 'sessions.sqlite' in f or '.env' in f for f in files))
            self.assertEqual((bundle/'scratch/runtime/qa-password').read_text(), 'synthetic')
            self.assertEqual((bundle/'scratch/runtime/qa-key.pem').stat().st_mode & 0o777, 0o600)
            self.assertEqual((bundle/'scratch/runtime').stat().st_mode & 0o777, 0o700)
            self.assertIn('qa/liquid-glass/verify_linux.py',files)
            for helper in ('live_mutate.py', 'live_summary.py'):
                relative = 'qa/liquid-glass/' + helper
                self.assertIn(relative, files, 'Fresh Linux deployment must include scenario helper')
                self.assertEqual((bundle/relative).read_bytes(), (HERE/helper).read_bytes())
                self.assertEqual((bundle/relative).stat().st_mode & 0o777, 0o600)
                self.assertIn(relative, json.loads((bundle/'source-manifest.json').read_text()))
            self.assertIn('integration/plugin_api.py',files)
            self.assertIn('integration/taskdb.py',files)
            self.assertNotIn('/opt/data/',(bundle/'server/mobile_api.py').read_text().split('def create_app')[0])
            self.assertIn("parents[1] / 'scratch'",(bundle/'server/qa_server.py').read_text())
            self.assertEqual((bundle/'qa/liquid-glass/runtime.py').read_bytes(),(HERE/'runtime.py').read_bytes())
            with self.assertRaises(ValueError):
                module.package(module.SCRATCH, Path(directory)/'scratch-root-rejected')
            with self.assertRaises(ValueError):
                module.package(HERE, Path(directory)/'outside-root-rejected')
            # Even an otherwise valid marked fixture must not follow member symlinks.
            (root/'qa-password').unlink()
            (root/'qa-password').symlink_to(root/'qa-token')
            with self.assertRaises(ValueError):
                module.package(root, Path(directory)/'symlink-rejected')
            (root/'qa-password').unlink()
            (root/'qa-password').write_text('synthetic')
            (root/'tasks.json').write_text(json.dumps({'tasks':[{'description':'real data'}]}))
            with self.assertRaises(ValueError):
                module.package(root,Path(directory)/'bad')

if __name__ == '__main__': unittest.main()
