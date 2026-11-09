"""Native remembered sessions must not stop unattended alerts twice daily."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch
from account_auth import AccountStore

NOW=1800000000
DAY=24*60*60

class SessionLifetimeTests(unittest.TestCase):
    def setUp(self):
        self.temp=tempfile.TemporaryDirectory(prefix='session-lifetime-',dir='/opt/data/cache/scratch')
        self.addCleanup(self.temp.cleanup)
        self.store=AccountStore(Path(self.temp.name)/'account.sqlite')
        self.initial='Synthetic temporary initial phrase'
        self.password='Synthetic final passphrase for device'
        self.store.initialize('fixture-owner',self.initial)

    def full(self,kind):
        with patch('account_auth.time.time',return_value=NOW):
            return self.store.change(self.store.snapshot(),self.initial,self.password,kind,{'_epoch':0})

    def test_native_full_remembers_device_for_thirty_days_but_remains_revocable(self):
        opaque,meta=self.full('native')
        self.assertEqual(meta['expires_at']-NOW,30*DAY)
        with patch('account_auth.time.time',return_value=NOW+13*60*60):
            self.assertIsNotNone(self.store.read(opaque,'native'))
            identity=self.store.read(opaque,'native')
            self.store.change(self.store.snapshot(),self.password,'Another distinct final device passphrase','native',identity)
            self.assertIsNone(self.store.read(opaque,'native'))

    def test_browser_full_keeps_twelve_hour_boundary(self):
        opaque,meta=self.full('browser')
        self.assertEqual(meta['expires_at']-NOW,12*60*60)
        with patch('account_auth.time.time',return_value=NOW+13*60*60):
            self.assertIsNone(self.store.read(opaque,'browser'))

    def test_bootstrap_native_never_receives_remembered_device_lifetime(self):
        with patch('account_auth.time.time',return_value=NOW):
            opaque,meta=self.store.issue(self.store.snapshot(),'native',True)
        self.assertTrue(meta['password_change_required'])
        self.assertEqual(meta['expires_at']-NOW,12*60*60)
        with patch('account_auth.time.time',return_value=NOW+13*60*60):
            self.assertIsNone(self.store.read(opaque,'native'))
