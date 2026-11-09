"""Storage collision rejection must be read-only, including path aliases."""
import itertools
import os
import tempfile
import unittest
from pathlib import Path

from account_auth import AccountStore
from mobile_api import create_app


class StoragePreflightTests(unittest.TestCase):
    def fixture(self, root):
        config = dict(db_path=root/'tasks.json',token_file=root/'token',
                      metadata_path=root/'events.sqlite',session_path=root/'sessions.sqlite',
                      conversation_path=root/'conversations.sqlite',account_path=root/'account.sqlite')
        config['token_file'].write_text('scratch-storage-token-'+'x'*48)
        config['token_file'].chmod(0o600)
        store = AccountStore(config['account_path'])
        store.initialize('scratch-owner','Synthetic initial password only')
        return config, store.snapshot()

    def alias(self, root, target, kind):
        if kind == 'direct':
            return target
        if kind == 'dotdot':
            (root/'subdir').mkdir()
            return root/'subdir'/'..'/target.name
        path = root/'alias'
        if kind == 'symlink':
            path.symlink_to(target)
        else:
            os.link(target,path)
        return path

    def test_session_alias_of_initialized_account_preserves_bytes_and_valid_reopen(self):
        for kind in ('direct','dotdot','symlink','hardlink'):
            with self.subTest(alias=kind), tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as name:
                root = Path(name)
                config, snapshot = self.fixture(root)
                before = config['account_path'].read_bytes()
                alias = self.alias(root,config['account_path'],kind)
                with self.assertRaises(ValueError):
                    create_app(**dict(config,session_path=alias))
                self.assertEqual(config['account_path'].read_bytes(),before)
                self.assertEqual(AccountStore(config['account_path']).snapshot(),snapshot)
                app = create_app(**config)
                app.state.conversations.close()
                self.assertEqual(app.state.accounts.snapshot(),snapshot)

    def test_every_storage_pair_collision_is_rejected_before_any_writes(self):
        fields = ('account_path','session_path','conversation_path','metadata_path','db_path','token_file')
        for left,right in itertools.combinations(fields,2):
            for kind in ('direct','dotdot','symlink','hardlink'):
                with self.subTest(left=left,right=right,alias=kind), tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as name:
                    root = Path(name)
                    config,_ = self.fixture(root)
                    # Preserve real initialized account/token stores; other targets are synthetic sentinels.
                    if not config[left].exists():
                        config[left].write_bytes(b'Synthetic storage sentinel: never modify')
                    config[right] = self.alias(root,config[left],kind)
                    before = {p:p.read_bytes() for p in root.iterdir() if p.is_file()}
                    entries = set(root.iterdir())
                    with self.assertRaises(ValueError):
                        create_app(**config)
                    self.assertEqual(set(root.iterdir()),entries)
                    self.assertEqual({p:p.read_bytes() for p in before},before)
