"""Provenance receipts. Model prose is never a source registry."""
import hashlib


def empty_metadata():
    return {'version': 1, 'sources': []}


class CitedText(str):
    """Keep legacy string rendering while carrying immutable original prose."""
    original_content: str
    citation_metadata: dict
    def __new__(cls, content, citation_metadata, rendered=None):
        obj = super().__new__(cls, content if rendered is None else rendered)
        obj.original_content = content
        obj.citation_metadata = citation_metadata
        return obj


def trim_url(value):
    value = value.rstrip('.,;:!?]}')
    while value.endswith(')') and value.count(')') > value.count('('):
        value = value[:-1]
    return value


def public_metadata(sources):
    return {'version': 1, 'sources': [
        {'id': 'web_'+hashlib.sha256(url.encode()).hexdigest()[:32],
         'kind': 'web', 'label': label, 'url': url,
         'verification': 'page_read' if label == 'Page read' else 'search_result'}
        for url, label in sources.items()]}


def saved_metadata(task):
    """Receipt for the exact saved context, not a claim of mailbox retrieval."""
    import re
    tid = task.get('id')
    if not isinstance(tid, str) or not tid:
        return empty_metadata()
    sources = [{'id': 'task_'+hashlib.sha256(tid.encode()).hexdigest()[:32],
                'kind': 'task', 'label': str(task.get('title') or 'Saved task')[:200],
                'task_id': tid, 'resource_id': tid, 'verification': 'saved_context'}]
    refs = task.get('source_references')
    if not isinstance(refs, list):
        return {'version': 1, 'sources': sources}
    for ref in dict.fromkeys(r for r in refs[:100] if isinstance(r, str)):
        gmail = re.fullmatch(r'gmail:[^:\s]+@[^:\s]+:(?:message|thread):[0-9a-fA-F]{1,128}', ref)
        nantes = re.fullmatch(r'nantes-imap://[^\r\n]+;UIDVALIDITY=[1-9][0-9]*;UID=[1-9][0-9]*;Message-ID=<[^<>\r\n]+>', ref)
        if not (gmail or nantes):
            continue
        from email_providers import source_id
        sid = source_id(tid, ref)
        sources.append({'id': sid, 'kind': 'saved_email', 'label': 'Saved email context',
                        'resource_id': sid, 'task_id': tid, 'verification': 'saved_context'})
    return {'version': 1, 'sources': sources}


def bounded_saved_context(task, limit=12000):
    """Serialize whole JSON and derive receipts only from that exact bounded view."""
    import json
    fields = ('id','title','description','status','priority','due_date','project','notes','evidence','source_references')
    view = {key: task.get(key) for key in fields}
    refs = view.get('source_references')
    if isinstance(refs,list):
        view['source_references'] = [r for r in refs[:100] if isinstance(r,str) and len(r)<=512]
    original = json.dumps(view,ensure_ascii=False)
    rendered = original
    while len(rendered)>limit:
        candidates = [k for k in fields if k not in ('id','title') and view[k]]
        if not candidates:
            raise ValueError('Saved context exceeds budget')
        key = max(candidates,key=lambda k:len(json.dumps(view[k],ensure_ascii=False)))
        value = view[key]
        if isinstance(value,str):
            view[key] = value[:len(value)//2]
        elif isinstance(value,list):
            view[key] = value[:len(value)//2]
        elif isinstance(value,dict):
            view[key] = dict(list(value.items())[:len(value)//2])
        else:
            view[key] = None
        rendered = json.dumps(view,ensure_ascii=False)
    return view, rendered, rendered != original
