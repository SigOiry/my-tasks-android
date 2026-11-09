"""Durable cursors and digests only; canonical JSON remains sole task store."""
import hashlib
import json
import os
import sqlite3
from contextlib import closing
from pathlib import Path


class EventFeed:
    def __init__(self, path, dashboard, db_path):
        self.path = Path(path)
        self.dashboard = dashboard
        self.db_path = db_path

    def poll(self, after, limit):
        d = self.dashboard
        self.path.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
        # Lock order canonical -> SQLite serializes snapshot capture across workers.
        with d.taskdb.Store(self.db_path)._lock():
            db = d.taskdb.Store(self.db_path)._read()
            fd = os.open(self.path, os.O_CREAT | os.O_RDWR, 0o600)
            os.close(fd)
            with closing(sqlite3.connect(self.path, timeout=15)) as con, con:
                con.execute('PRAGMA synchronous=FULL')
                con.execute('BEGIN IMMEDIATE')
                con.execute('CREATE TABLE IF NOT EXISTS state (task_id TEXT PRIMARY KEY, digest TEXT NOT NULL, deleted INTEGER NOT NULL)')
                con.execute('CREATE TABLE IF NOT EXISTS flags (name TEXT PRIMARY KEY)')
                con.execute('CREATE TABLE IF NOT EXISTS events (id INTEGER PRIMARY KEY AUTOINCREMENT, task_id TEXT NOT NULL, kind TEXT NOT NULL, token TEXT NOT NULL, created_at TEXT NOT NULL)')
                baseline = con.execute("SELECT 1 FROM flags WHERE name='initialized'").fetchone() is not None
                for task in db['tasks']:
                    enriched = d.enrich(task, db)
                    deleted = int(enriched['deleted'])
                    digest = hashlib.sha256(json.dumps([d.meaningful_snapshot(task),deleted],sort_keys=True,ensure_ascii=False).encode()).hexdigest()
                    previous = con.execute('SELECT digest,deleted FROM state WHERE task_id=?',(task['id'],)).fetchone()
                    if baseline and (previous is None or previous[0] != digest):
                        kind = 'created' if previous is None else ('deleted' if deleted and not previous[1] else ('restored' if previous[1] and not deleted else 'updated'))
                        con.execute('INSERT INTO events(task_id,kind,token,created_at) VALUES(?,?,?,?)',(task['id'],kind,enriched['notification']['token'],d.taskdb.now()))
                    con.execute('INSERT OR REPLACE INTO state VALUES(?,?,?)',(task['id'],digest,deleted))
                con.execute("INSERT OR IGNORE INTO flags VALUES('initialized')")
                con.row_factory = sqlite3.Row
                rows = con.execute('SELECT * FROM events WHERE id>? ORDER BY id LIMIT ?',(after,limit+1)).fetchall()
                events = [dict(row) for row in rows[:limit]]
                return {'events':events,'cursor':events[-1]['id'] if events else after,'has_more':len(rows)>limit,
                        'notification_tracking':d.notification_state(db) is not None}
