"""Read-only extractive overviews; no model, network, cache or canonical writes.

Scan the entire source, keeping a constant-sized pool of representative excerpts.
This is selection, not semantic synthesis: source ordering and qualifiers within
ordinary sentences are retained. Long sentences use visibly elided excerpts.
"""
from functools import wraps
import html
import re
import unicodedata

SUMMARY_VERSION = 'extractive-v1'
MAX_SUMMARY_CHARS = 1600
MAX_ITEMS = 6
_EXCERPT_CHARS = 210
_MARKDOWN = re.compile(r'([\\`*_{}\[\]()#!|>~])')
_SENTENCE = re.compile(r'.+?(?:(?<=[.!?。！？])(?=\s)|$)')
_ACTION = re.compile(r'\b(?:send|submit|review|reply|prepare|confirm|complete|return|provide|must|need|please|action|deadline|due|before|until|by|envoyer|répondre|préparer|confirmer|avant|échéance|attendu|doit|faire)\b', re.I)
_DATE = re.compile(r'\b(?:\d{4}-\d{2}-\d{2}|\d{1,2}[/.]\d{1,2}(?:[/.]\d{2,4})?|monday|tuesday|wednesday|thursday|friday|saturday|sunday|lundi|mardi|mercredi|jeudi|vendredi|samedi|dimanche)\b', re.I)
_QUALIFIER = re.compile(r'\b(?:not|no|unless|only|optional|provisional|pending|if|ne|pas|sauf|uniquement|facultatif|si)\b', re.I)


def _safe(text):
    # Source formatting never becomes executable HTML, images or Markdown links.
    # URL destinations are not needed for a reading overview; source_links remain
    # the canonical navigation surface. No URL is fetched here.
    text = re.sub(r'!?\[([^\[\]\n]*)\]\([^\s()]*\)', r'\1', text)
    text = re.sub(r'(?:https?://|javascript:|data:)[^\s<>]*', '[link omitted]', text, flags=re.I)
    text = text.replace('**', '').replace('__', '').replace('`', '')
    text = ''.join(c for c in text if c >= ' ' and not ('\u007f' <= c <= '\u009f')
                   and c not in '\u202a\u202b\u202c\u202d\u202e\u2066\u2067\u2068\u2069')
    # Format-only text has no readable excerpt. Keep joiners in real words/emoji.
    if not any(not c.isspace() and unicodedata.category(c) != 'Cf' for c in text):
        return ''
    return _MARKDOWN.sub(r'\\\1', html.escape(text, quote=False))


def _excerpts(text):
    """Bound escaped output, splitting long sentences at words where possible."""
    text = ' '.join(text.split())
    if not text:
        return
    safe_text = _safe(text)
    if not safe_text:
        return
    if len(safe_text) <= _EXCERPT_CHARS:
        yield text, safe_text
        return
    # Linear scan with fixed-sized chunks; no giant-token regex backtracking.
    start = 0
    while start < len(text):
        end = min(start + 160, len(text))
        if end < len(text):
            space = text.rfind(' ', start, end)
            if space > start:
                end = space
        while len(_safe(text[start:end])) > _EXCERPT_CHARS - 4:
            end = start + max(1, (end-start)//2)
        raw = text[start:end].strip()
        safe_raw = _safe(raw)
        if safe_raw:
            yield raw, ('… ' if start else '') + safe_raw + (' …' if end < len(text) else '')
        start = end
        while start < len(text) and text[start].isspace():
            start += 1


def _passages(description):
    """Keep actual headings across blank lines, including bodyless headings."""
    section = 0
    label = ''
    pending = None
    previous_end = 0
    for match in re.finditer(r'[^\r\n]+', description):
        line = match.group().strip()
        if not line:
            continue
        if description[previous_end:match.start()].count('\n') > 1 and pending is None:
            section += 1
            label = ''
        previous_end = match.end()
        # Match only the bounded prefix, then strip the suffix in linear scans.
        # A lazy body plus optional trailing hashes backtracks quadratically on
        # '# '+ '#'*N + 'x'. Keep one body character for hash-only headings.
        heading = re.match(r'#{1,6}\s+', line)
        heading_label = ''
        if heading and heading.end() < len(line):
            body = line[heading.end():]
            heading_label = body.rstrip('#').rstrip() or body[:1]
        standalone = re.fullmatch(r'\*\*(.{1,60}?)\*\*\s*:?', line)
        if heading_label or standalone or (len(line) <= 60 and line.endswith(':')):
            if pending is not None:
                yield pending
            section += 1
            label = heading_label or (standalone.group(1) if standalone else line[:-1])
            pending = (label, section, '', match.start())
            continue
        pending = None
        line = re.sub(r'^\s*(?:[-*+•]\s+|\d+[.)]\s+|>\s*)', '', line)
        line = re.sub(r'^\[[ xX]\]\s*', '', line)
        yield line, section, label, match.start()
    if pending is not None:
        yield pending


def description_summary(description):
    """Return safe Markdown selected from the whole source, at most 1600 chars.

    Pool memory is bounded independent of source length; scan time is linear.
    Six positional regions and up to twelve sections compete with action/date
    excerpts. Repetition is collapsed. Heuristics do not establish obligations.
    """
    pool = {}
    ordinal = 0
    first = last = None
    source_length = max(1, len(description))
    for line, section, label, position in _passages(description):
        for sentence_match in _SENTENCE.finditer(line):
            for raw, safe in _excerpts(sentence_match.group()):
                ordinal += 1
                score = (4 * bool(_DATE.search(raw)) + 2 * bool(_ACTION.search(raw))
                         + bool(_QUALIFIER.search(raw)))
                candidate = (ordinal, section, raw, safe, label, score)
                if first is None:
                    first = candidate
                last = candidate
                # Keep strongest and earliest in each full-source positional bin.
                region = min(5, (position+sentence_match.start()) * 6 // source_length)
                for key in (('region', region), ('section', section % 12)):
                    old = pool.get(key)
                    if old is None or score > old[5] or (key[0] == 'section' and old[1] != section):
                        pool[key] = candidate
                # Preserve late action/deadline as well as positional coverage.
                if score:
                    old = pool.get(('important', region))
                    if old is None or score >= old[5]:
                        pool[('important', region)] = candidate
    if first is None:
        return ''
    assert last is not None
    selected = [first]
    candidates = sorted(set(pool.values()) | {last}, key=lambda c: c[0])

    def add(candidate):
        if len(selected) < MAX_ITEMS and all(c[2].casefold() != candidate[2].casefold() for c in selected):
            selected.append(candidate)

    # At most two high-signal excerpts, including important late instructions.
    for candidate in sorted(candidates, key=lambda c: (c[5], c[0]), reverse=True)[:2]:
        if candidate[5]:
            add(candidate)
    # Cover distinct sections before filling unused positions.
    for candidate in sorted(candidates, key=lambda c: (-c[5], c[0])):
        if candidate[1] not in {c[1] for c in selected}:
            add(candidate)
    add(last)
    for candidate in sorted(candidates, key=lambda c: (-c[5], c[0])):
        add(candidate)
    bullets = []
    for _, _, raw, safe, heading, _ in sorted(selected):
        # Emphasize the entire source excerpt, not a stripped-out deadline or
        # imperative that could lose a nearby negation/condition.
        if _DATE.search(raw) or _ACTION.search(raw):
            safe = '**'+safe+'**'
        prefix = ''
        if heading:
            # Do not guess labels such as Action/Deadline from extracted prose.
            # Omit unusually long labels rather than cut through escaping/entities.
            safe_heading = _safe(heading)
            if safe_heading and len(safe_heading) <= 36:
                prefix = '**'+safe_heading+'**: '
        bullets.append('- '+prefix+safe)
    return '**Concise overview · selected excerpts**\n\n'+'\n'.join(bullets)


def with_description_summary(endpoint):
    """Decorate only the copied Task endpoints, after canonical enrichment."""
    def derived(task):
        return {**task, 'description_summary': description_summary(task.get('description', '')),
                'description_summary_version': SUMMARY_VERSION}

    @wraps(endpoint)
    def response(*args, **kwargs):
        result = endpoint(*args, **kwargs)
        if 'tasks' in result:
            return {**result, 'tasks': [derived(task) for task in result['tasks']]}
        return derived(result)
    return response
