"""Private single-owner password/session storage; no canonical data access."""
import hashlib
import hmac
import os
import secrets
import sqlite3
import threading
import time
from contextlib import contextmanager
from pathlib import Path
from fastapi import HTTPException

N, R, P, MAXMEM = 32768, 8, 3, 128 * 1024 * 1024
SESSION_SECONDS = 43200
NATIVE_SESSION_SECONDS = 30 * 24 * 60 * 60
HASH_SLOTS = threading.BoundedSemaphore(2)
# Explicit obvious-password blocklist, not character-class/composition rules.
COMMON_PASSWORDS = {'password1234567','password123456789','password1234567890',
                    'changemechangeme','qwertyuiopasdfgh','letmeinletmeinletmein',
                    'welcome123456789','administrator123','correcthorsebatterystaple'}
COMMON_PASSWORDS.update(character*size for character in 'abcdefghijklmnopqrstuvwxyz0123456789! ' for size in range(15,129))
COMMON_PASSWORDS.update((sequence*13)[:size] for sequence in ('0123456789','1234567890','9876543210') for size in range(15,129))
COMMON_PASSWORDS.update(pattern*count for pattern in ('password','qwerty','letmein','welcome','abc','123') for count in range(2,43) if 15<=len(pattern)*count<=128)

def digest(value):
    return hashlib.sha256(value.encode('utf-8')).hexdigest()

def password_hash(password, salt, n=N, r=R, p=P):
    # Validate persisted parameters BEFORE handing them to the allocator.
    if (n,r,p) != (N,R,P) or not isinstance(salt,bytes) or len(salt) != 16:
        raise HTTPException(503,'Account service unavailable')
    if not HASH_SLOTS.acquire(blocking=False):
        raise HTTPException(429,'Authentication busy; retry later',headers={'Retry-After':'2'})
    try:
        return hashlib.scrypt(password.encode('utf-8'),salt=salt,n=n,r=r,p=p,maxmem=MAXMEM,dklen=32)
    finally:
        HASH_SLOTS.release()

class AccountStore:
    def __init__(self,path):
        self.path = Path(path)
        if self.path.name == 'state.db':
            raise ValueError('Hermes state database is not account metadata')
        self.path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
        if self.path.is_symlink():
            raise ValueError('Account database must be a private regular file')
        fd = os.open(self.path,os.O_CREAT|os.O_RDWR|os.O_NOFOLLOW,0o600)
        os.close(fd)
        if not self.path.is_file() or self.path.stat().st_mode & 0o077:
            raise ValueError('Account database must be private (0600)')
        with self.connect() as con:
            tables = {row[0] for row in con.execute("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")}
            if tables - {'account','account_sessions','account_attempts'}:
                raise ValueError('Account metadata requires a dedicated database')
            con.executescript('''CREATE TABLE IF NOT EXISTS account (
                id INTEGER PRIMARY KEY CHECK(id=1), username TEXT NOT NULL,
                salt BLOB NOT NULL, hash BLOB NOT NULL, n INTEGER NOT NULL,r INTEGER NOT NULL,p INTEGER NOT NULL,
                epoch INTEGER NOT NULL, active INTEGER NOT NULL, initial_salt BLOB NOT NULL, initial_hash BLOB NOT NULL,
                activation_hash TEXT, activation_expires REAL);
                CREATE TABLE IF NOT EXISTS account_sessions (
                id TEXT PRIMARY KEY, kind TEXT NOT NULL, csrf TEXT NOT NULL, epoch INTEGER NOT NULL,
                restricted INTEGER NOT NULL, expires REAL NOT NULL);
                CREATE TABLE IF NOT EXISTS account_attempts (ip TEXT NOT NULL, at REAL NOT NULL);''')

    @contextmanager
    def connect(self):
        con = sqlite3.connect(self.path,timeout=10)
        con.row_factory = sqlite3.Row
        try:
            with con:
                yield con
        finally:
            con.close()

    def snapshot(self):
        with self.connect() as con:
            row = con.execute('SELECT * FROM account WHERE id=1').fetchone()
            return dict(row) if row else None

    def initialize(self,username,password):
        if not isinstance(username,str) or not 1 <= len(username) <= 128 or not username.isascii() or any(c.isspace() for c in username):
            raise ValueError('Invalid username')
        if not isinstance(password,str) or not 1 <= len(password) <= 128 or len(password.encode('utf-8')) > 1024:
            raise ValueError('Invalid initial password length')
        salt, code = secrets.token_bytes(16), secrets.token_hex(32)
        hashed = password_hash(password,salt)
        with self.connect() as con:
            con.execute('BEGIN IMMEDIATE')
            if con.execute('SELECT 1 FROM account').fetchone():
                raise ValueError('Account already initialized; refusing reset')
            con.execute('INSERT INTO account VALUES(1,?,?,?,?,?,?,0,0,?,?,?,?)',(username,salt,hashed,N,R,P,salt,hashed,digest(code),time.time()+7*86400))
        return code

    def attempt(self,ip):
        with self.connect() as con:
            con.execute('BEGIN IMMEDIATE')
            con.execute('DELETE FROM account_attempts WHERE at < ?',(time.time()-60,))
            total,local = con.execute('SELECT count(*),coalesce(sum(ip=?),0) FROM account_attempts',(ip,)).fetchone()
            if total >= 30 or local >= 5:
                raise HTTPException(429,'Too many authentication attempts; wait one minute',headers={'Retry-After':'60'})
            con.execute('INSERT INTO account_attempts VALUES(?,?)',(ip,time.time()))

    def verify(self,snapshot,password):
        return hmac.compare_digest(password_hash(password,snapshot['salt'],snapshot['n'],snapshot['r'],snapshot['p']),snapshot['hash'])

    def _issue(self,con,snapshot,kind,restricted,previous=None):
        opaque,csrf = secrets.token_hex(32),secrets.token_hex(32)
        # Remember fully authenticated phones for unattended task alerts; never extend bootstrap proof.
        lifetime = NATIVE_SESSION_SECONDS if kind == 'native' and not restricted else SESSION_SECONDS
        expires = time.time()+lifetime
        con.execute('DELETE FROM account_sessions WHERE expires<=? OR epoch!=?',(time.time(),snapshot['epoch']))
        if previous:
            con.execute('DELETE FROM account_sessions WHERE id=?',(digest(previous),))
        con.execute('DELETE FROM account_sessions WHERE id IN (SELECT id FROM account_sessions ORDER BY expires DESC LIMIT -1 OFFSET 99)')
        con.execute('INSERT INTO account_sessions VALUES(?,?,?,?,?,?)',(digest(opaque),kind,csrf,snapshot['epoch'],int(restricted),expires))
        meta = dict(authenticated=True,username=snapshot['username'],password_change_required=bool(restricted),expires_at=expires)
        meta['session_token' if kind=='native' else 'csrf'] = opaque if kind=='native' else csrf
        return opaque,meta

    def issue(self,snapshot,kind,restricted,previous=None):
        with self.connect() as con:
            con.execute('BEGIN IMMEDIATE')
            current = con.execute('SELECT epoch,active FROM account WHERE id=1').fetchone()
            if current['epoch'] != snapshot['epoch'] or current['active'] != snapshot['active']:
                raise HTTPException(401,'Credentials changed; sign in again')
            return self._issue(con,snapshot,kind,restricted,previous)

    def read(self,opaque,kind):
        if not opaque or len(opaque)>128:
            return None
        with self.connect() as con:
            row = con.execute('SELECT s.*,a.username FROM account_sessions s JOIN account a ON s.epoch=a.epoch WHERE s.id=? AND s.kind=? AND s.expires>?',(digest(opaque),kind,time.time())).fetchone()
        if not row:
            return None
        result = dict(authenticated=True,username=row['username'],password_change_required=bool(row['restricted']),expires_at=row['expires'],_epoch=row['epoch'])
        if kind=='browser': result['csrf'] = row['csrf']
        return result

    def revoke(self,opaque,kind):
        with self.connect() as con:
            con.execute('DELETE FROM account_sessions WHERE id=? AND kind=?',(digest(opaque or ''),kind))

    def activate(self,code,previous=None):
        with self.connect() as con:
            con.execute('BEGIN IMMEDIATE')
            snapshot = dict(con.execute('SELECT * FROM account WHERE id=1').fetchone())
            if snapshot['active'] or not snapshot['activation_hash'] or snapshot['activation_expires'] <= time.time() or not hmac.compare_digest(digest(code),snapshot['activation_hash']):
                raise HTTPException(401,'Invalid or expired activation')
            con.execute('UPDATE account SET activation_hash=NULL,activation_expires=NULL WHERE id=1')
            return self._issue(con,snapshot,'browser',True,previous)

    def change(self,snapshot,current,new,kind,identity):
        if identity['_epoch'] != snapshot['epoch']:
            raise HTTPException(401,'Credentials changed; sign in again')
        if not self.verify(snapshot,current):
            raise HTTPException(401,'Invalid current password')
        if not 15 <= len(new) <= 128 or len(new.encode('utf-8'))>1024 or new==current or new.casefold().strip() in COMMON_PASSWORDS:
            raise HTTPException(422,'Choose a different password of 15 to 128 characters')
        if hmac.compare_digest(password_hash(new,snapshot['initial_salt']),snapshot['initial_hash']):
            raise HTTPException(422,'Choose a different password of 15 to 128 characters')
        salt = secrets.token_bytes(16)
        hashed = password_hash(new,salt)
        with self.connect() as con:
            con.execute('BEGIN IMMEDIATE')
            changed = con.execute('UPDATE account SET salt=?,hash=?,n=?,r=?,p=?,epoch=epoch+1,active=1,activation_hash=NULL,activation_expires=NULL WHERE id=1 AND epoch=?',(salt,hashed,N,R,P,snapshot['epoch'])).rowcount
            if not changed:
                raise HTTPException(409,'Credentials changed; sign in again')
            con.execute('DELETE FROM account_sessions')
            snapshot = dict(snapshot,epoch=snapshot['epoch']+1,active=1)
            return self._issue(con,snapshot,kind,False)
