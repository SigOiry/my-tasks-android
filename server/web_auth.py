"""Opaque web sessions; no bearer credential is stored in browser cookies or SQLite."""
import hashlib
import hmac
import os
import secrets
import sqlite3
import time
from contextlib import closing
from pathlib import Path
from fastapi import HTTPException

COOKIE = 'mytasks_session'
SESSION_SECONDS = 12 * 60 * 60

class Sessions:
    def __init__(self, path, token):
        self.path = Path(path)
        self.fingerprint = hashlib.sha256(token.encode()).hexdigest()
        self.path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        fd = os.open(self.path, os.O_CREAT | os.O_RDWR, 0o600)
        os.close(fd)
        with self.connect() as con:
            con.execute('CREATE TABLE IF NOT EXISTS sessions (id TEXT PRIMARY KEY, csrf TEXT NOT NULL, expires REAL NOT NULL, credential TEXT NOT NULL)')
            con.execute('CREATE TABLE IF NOT EXISTS attempts (ip TEXT NOT NULL, at REAL NOT NULL)')

    def connect(self):
        return closing(sqlite3.connect(self.path, timeout=10))

    def matches(self, token):
        return isinstance(token, str) and hmac.compare_digest(hashlib.sha256(token.encode()).hexdigest(), self.fingerprint)

    def attempt(self, ip):
        now = time.time()
        with self.connect() as con, con:
            con.execute('BEGIN IMMEDIATE')
            con.execute('DELETE FROM attempts WHERE at < ?', (now - 60,))
            total, local = con.execute('SELECT count(*), coalesce(sum(ip = ?),0) FROM attempts', (ip,)).fetchone()
            if total >= 30 or local >= 5:
                raise HTTPException(429, 'Too many login attempts; wait one minute', headers={'Retry-After':'60'})
            con.execute('INSERT INTO attempts VALUES (?,?)', (ip, now))

    def issue(self, previous=None):
        opaque, csrf = secrets.token_urlsafe(48), secrets.token_urlsafe(32)
        with self.connect() as con, con:
            con.execute('DELETE FROM sessions WHERE expires <= ? OR credential != ?', (time.time(),self.fingerprint))
            if previous:
                con.execute('DELETE FROM sessions WHERE id = ?', (self.digest(previous),))
            # Bound disk growth even with many successful logins.
            count = con.execute('SELECT count(*) FROM sessions').fetchone()[0]
            if count >= 100:
                con.execute('DELETE FROM sessions WHERE id IN (SELECT id FROM sessions ORDER BY expires LIMIT 1)')
            con.execute('INSERT INTO sessions VALUES (?,?,?,?)', (self.digest(opaque),csrf,time.time()+SESSION_SECONDS,self.fingerprint))
        return opaque, csrf

    @staticmethod
    def digest(value):
        return hashlib.sha256(value.encode()).hexdigest()

    def read(self, opaque):
        if not opaque or len(opaque) > 128:
            return None
        with self.connect() as con:
            row = con.execute('SELECT csrf, expires FROM sessions WHERE id=? AND expires>? AND credential=?',
                              (self.digest(opaque),time.time(),self.fingerprint)).fetchone()
        return {'authenticated':True,'csrf':row[0],'expires_at':row[1]} if row else None

    def revoke(self, opaque):
        with self.connect() as con, con:
            con.execute('DELETE FROM sessions WHERE id=?', (self.digest(opaque or ''),))
