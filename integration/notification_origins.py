"""Version 1 reviewed email provenance; metadata only, never canonical fields."""
import copy
import re
from typing import NoReturn

VERSION = 1
MAX_ACTIONS = 128
MAX_ORIGINS_PER_ACTION = 32
MAX_TOTAL_ORIGINS = 512
MAX_REFERENCE_LENGTH = 2048
GMAIL = re.compile(r'gmail:([A-Za-z0-9._+\-]+@[A-Za-z0-9.-]+):message:([0-9a-fA-F]{1,64})')
NANTES = re.compile(r'nantes-imap://([A-Za-z0-9._ /+\-]{1,256});UIDVALIDITY=([1-9][0-9]{0,9});UID=([1-9][0-9]{0,9});Message-ID=<([A-Za-z0-9.!+_=\-]+@[A-Za-z0-9.\-]+)>')


def _fail(reason) -> NoReturn:
    raise ValueError('notification_origins: ' + reason)


def _reference_kind(reference):
    if (not isinstance(reference, str) or not 1 <= len(reference) <= MAX_REFERENCE_LENGTH
            or any(ord(char) < 32 or ord(char) == 127 for char in reference)):
        _fail('invalid exact email reference')
    gmail = GMAIL.fullmatch(reference)
    if gmail:
        return 'gmail', gmail.groups()
    nantes = NANTES.fullmatch(reference)
    if nantes:
        folder, validity, uid, mid = nantes.groups()
        if (folder.strip() != folder or any(part in {'.', '..', ''} for part in folder.split('/'))
                or int(validity) > 4294967295 or int(uid) > 4294967295):
            _fail('invalid Nantes UID reference')
        return 'nantes', nantes.groups()
    _fail('expected Gmail message or complete Nantes UID reference')


def _known_reference(item, reference, kind, parts):
    """Only collector-defined structured fields, never prose/evidence or item IDs."""
    content = item.get('content')
    if not isinstance(content, dict):
        return False
    if kind == 'gmail':
        messages = content.get('messages')
        return (item.get('source') == 'Gmail' and content.get('account') == parts[0]
                and isinstance(messages, list)
                and any(isinstance(message, dict) and message.get('source_reference') == reference
                        and message.get('id') == parts[1] for message in messages))
    return (item.get('source') == 'Nantes Université email'
            and content.get('source_reference') == reference
            and content.get('mailbox') == parts[0]
            and str(content.get('uidvalidity')) == parts[1] and str(content.get('uid')) == parts[2])


def validate_origins(review, manifest, candidates, dispositions):
    """Validate the optional v1 envelope before reviewed.json or any Store write."""
    if 'notification_origins' not in review:
        if ('notification_origins_version' in review
                and (type(review['notification_origins_version']) is not int
                     or review['notification_origins_version'] != VERSION)):
            _fail('unsupported version')
        return {}
    if (type(review.get('notification_origins_version')) is not int
            or review['notification_origins_version'] != VERSION):
        _fail('version 1 required')
    mapping = review['notification_origins']
    by_key = {candidate['action_key']: candidate for candidate in candidates}
    if (not isinstance(mapping, dict) or len(mapping) > MAX_ACTIONS
            or not set(mapping) <= set(by_key)):
        _fail('expected bounded candidate action-key mapping')
    items = {item['id']: item for item in manifest['items']}
    normalized, total = {}, 0
    for key, origins in mapping.items():
        if (not isinstance(key, str) or not 1 <= len(key) <= 256
                or not isinstance(origins, list) or not 1 <= len(origins) <= MAX_ORIGINS_PER_ACTION):
            _fail('invalid action key or bounded origin list')
        total += len(origins)
        if total > MAX_TOTAL_ORIGINS:
            _fail('total origin limit exceeded')
        candidate = by_key[key]
        refs = candidate.get('source_references', [])
        if not isinstance(refs, list) or any(not isinstance(ref, str) for ref in refs):
            _fail('candidate reference list is malformed')
        carried = refs + [candidate.get('source_reference')]
        seen = set()
        normalized[key] = []
        for origin in origins:
            if not isinstance(origin, dict) or set(origin) != {'item_id', 'reference', 'role'}:
                _fail('origins require exactly item_id, reference, role')
            item_id, reference, role = origin['item_id'], origin['reference'], origin['role']
            if (not isinstance(item_id, str) or not 1 <= len(item_id) <= MAX_REFERENCE_LENGTH
                    or not isinstance(role, str) or role not in {'trigger', 'context'}):
                _fail('invalid item identity or role')
            kind, parts = _reference_kind(reference)
            if reference in seen:
                _fail('duplicate or conflicting reference roles')
            seen.add(reference)
            item = items.get(item_id)
            disposition = dispositions.get(item_id)
            if (not item or not isinstance(disposition, dict)
                    or disposition.get('hash') != item['hash']
                    or disposition.get('decision') not in {'action', 'existing'}
                    or not isinstance(disposition.get('action_keys'), list)
                    or key not in disposition['action_keys']):
                _fail('origin requires matching source hash and candidate disposition link')
            if reference not in carried or not _known_reference(item, reference, kind, parts):
                _fail('reference must be carried by candidate and known in the same structured source item')
            normalized[key].append({'reference': reference, 'role': role, 'item_id': item_id,
                                    'item_hash': item['hash']})
    return normalized


def attach_origins(db, task_id, snapshot, prior, blocked, origins, run_id, manifest_hash):
    """Called only by LockedStore._write while the original Store lock is held."""
    if origins is not None and not blocked and prior != snapshot:
        db.setdefault('dashboard', {}).setdefault('notification_origins', {})[task_id] = {
            'snapshot': copy.deepcopy(snapshot), 'run_id': run_id,
            'manifest_hash': manifest_hash, 'origins': copy.deepcopy(origins)}
