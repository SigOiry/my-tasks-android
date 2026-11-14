"""Read-only, canonical-authorized search of the private derived evidence index.

No refresh, mailbox/network access, or writes to canonical/index state.
"""
from contextlib import closing
import hashlib
import json
from pathlib import Path
import re
import sqlite3


def encoded(value):
    return json.dumps(value,ensure_ascii=False,sort_keys=True)


def fingerprint(task):
    return hashlib.sha256(encoded(task).encode()).hexdigest()


def private_search(question):
    """Deterministic routing, before any public researcher or model sees data."""
    topic = re.sub(r'\b(?:email protocols?|email standards?)\b','public subject',question,flags=re.I)
    return bool(re.search(r'\b(?:saved|stored|indexed|database|mail|emails?|e-mails?|inbox|tasks?|task-linked|courriels?|mails?|t[âa]ches?|base de donn[ée]es|enregistr[ée]\w*)\b|\b(?:search|query|find|look up|cherche\w*|recherche\w*)\b[^.!?\n]{0,80}\b(?:index|knowledge|evidence|messages)\b',topic,re.I))


class KnowledgeContext:
    def __init__(self, canonical, index=None):
        self.canonical = Path(canonical)
        self.index = Path(index) if index else self.canonical.parent/'knowledge'/'knowledge.sqlite'

    def _tasks(self):
        data = json.loads(self.canonical.read_bytes())
        meta = data.get('dashboard',{}).get('tasks',{})
        return {task['id']:dict(task,notes=meta.get(task['id'],{}).get('notes',''))
                for task in data['tasks'] if not task.get('deleted') and not meta.get(task['id'],{}).get('deleted')}

    @staticmethod
    def _allowed(tasks, tid, path, ref, canonical_hash):
        task = tasks.get(tid)
        if task is None:
            return False
        return (canonical_hash==fingerprint(task) if path=='tasks.json'
                else ref in task.get('source_references',[]))

    def validate(self, rows):
        """Re-read canonical authority; do not trust a prior retrieval snapshot."""
        if not rows:
            return
        tasks = self._tasks()
        if not all(self._allowed(tasks,row['task_id'],row['source_path'],row['source_reference'],row['canonical_hash']) for row in rows):
            raise ValueError('Saved evidence authorization changed')

    def __call__(self, task_id, question):
        if not self.index.is_file() or self.index.is_symlink():
            return []
        try:
            tasks = self._tasks()
            if task_id is not None:
                tasks = {task_id:tasks[task_id]} if task_id in tasks else {}
            if not tasks:
                return []
            words = list(dict.fromkeys(re.findall(r'\w+',question.casefold())))[:24]
            expression = ' OR '.join('"'+word+'"' for word in words)
            with closing(sqlite3.connect(self.index.resolve().as_uri()+'?mode=ro',uri=True)) as con:
                if (con.execute('PRAGMA application_id').fetchone()[0] != 0x544B4E31
                        or con.execute('PRAGMA user_version').fetchone()[0] != 2):
                    return []
                con.row_factory = sqlite3.Row
                con.create_function('authorized',4,lambda *args:int(self._allowed(tasks,*args)))
                allowed = 'authorized(docs.task_id,docs.source_path,docs.source_reference,docs.canonical_hash)'
                rows = con.execute('SELECT docs.* FROM search JOIN docs ON docs.id=search.doc_id '
                                   'WHERE search MATCH ? AND '+allowed+' ORDER BY bm25(search),docs.captured_at DESC,docs.id LIMIT 64',
                                   (expression,)).fetchall() if expression else []
                # Only task-linked chat has a recent fallback. Global discovery
                # must not return unrelated private mail when nothing matches.
                if not rows and task_id is not None:
                    rows = con.execute('SELECT docs.* FROM docs WHERE '+allowed+' ORDER BY captured_at DESC,id LIMIT 12').fetchall()
            result,seen = [],set()
            for row in rows:
                entry = dict(row)
                key = (entry['task_id'],entry['source_reference'],entry['content_hash'],entry['chunk_start'])
                if key not in seen and len(result)<4 and len(encoded(result+[entry]))<=5000:
                    result.append(entry)
                    seen.add(key)
            return result
        except (OSError,ValueError,KeyError,TypeError,sqlite3.Error):
            return []


def knowledge_metadata(rows):
    from email_providers import source_id
    sources = []
    for row in rows:
        tid = row['task_id']
        mail = row['source_reference'].startswith(('gmail:','nantes-imap:'))
        sources.append({'id':'knowledge_'+row['id'][:32],
                        'kind':'saved_email' if mail else 'task',
                        'label':'Saved source excerpt' if row['source_path']!='tasks.json' else 'Saved task excerpt',
                        'resource_id':source_id(tid,row['source_reference']) if mail else tid,
                        'task_id':tid,'verification':'saved_scan' if row['source_path']!='tasks.json' else 'saved_task_index',
                        **{key:row[key] for key in ('source_path','source_timestamp','captured_at','content_hash','source_hash','chunk_start')}})
    return sources
