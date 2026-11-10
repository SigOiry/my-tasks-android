"""Opt-in real provider, one exact public question, disposable SQLite only."""
import json
import logging
import re
import sys
import tempfile
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'server'))
from conversations import ConversationStore, NewConversation, SendMessage
from hermes_chat import call_llm
from public_web import search_public, read_public, research_decision

PROMPT = 'What are the opening hours of tabaccheria N 196, Via di Prosecco 15, Trieste?'
OUTPUT = Path(__file__).with_name('opening-hours-real-smoke.json')
logging.disable(logging.CRITICAL)
report = {'prompt': PROMPT, 'started_utc': datetime.now(timezone.utc).isoformat(),
          'decision': research_decision(PROMPT), 'scratch_only': True,
          'service_started': False, 'provider_calls': [], 'searches': [], 'reads': []}

def provider(**kwargs):
    route = {}
    entry = {'tools': [tool['function']['name'] for tool in kwargs['tools']]}
    report['provider_calls'].append(entry)
    started = time.monotonic()
    try:
        result = call_llm(**kwargs, route_info=route)
        entry['provider'] = route.get('provider', 'unspecified')
        message = result.choices[0].message
        entry['tool_calls'] = [{'name': t.function.name, 'arguments': t.function.arguments} for t in (message.tool_calls or [])]
        return result
    except Exception as exc:
        entry['error_type'] = type(exc).__name__
        raise
    finally:
        entry['seconds'] = round(time.monotonic() - started, 3)

def search(query, **kwargs):
    entry = {'query': query}
    report['searches'].append(entry)
    try:
        rows = search_public(query, **kwargs)
        entry['results'] = rows
        return rows
    except Exception as exc:
        entry['error_type'] = type(exc).__name__
        raise

def read(url, **kwargs):
    entry = {'url': url}
    report['reads'].append(entry)
    try:
        result = read_public(url, **kwargs)
        entry.update(result)
        return result
    except Exception as exc:
        entry['error_type'] = type(exc).__name__
        raise

try:
    with tempfile.TemporaryDirectory(prefix='opening-hours-', dir='/opt/data/cache/scratch') as tmp:
        path = Path(tmp) / 'scratch.sqlite'
        store = ConversationStore(path)
        store.start(lambda _: {})
        try:
            with patch('hermes_chat.call_llm', side_effect=provider), patch('public_web.search_public', side_effect=search), patch('public_web.read_public', side_effect=read):
                conversation = store.create(NewConversation(task_id=None, title='Isolated opening-hours QA'))
                payload = SendMessage(request_id=str(uuid.uuid4()), content=PROMPT, mode='question', expected_revision=1)
                started = time.monotonic()
                store.submit(conversation['id'], payload)
                phases = []
                while True:
                    request = store.request(conversation['id'], payload.request_id)
                    phase = request.get('web_phase')
                    if phase and (not phases or phases[-1] != phase):
                        phases.append(phase)
                    if request['status'] not in ('queued', 'running'):
                        break
                    if time.monotonic() - started > 200:
                        raise TimeoutError('Scratch smoke exceeded wall clock limit')
                    time.sleep(.03)
                report.update(status=request['status'], phases=phases,
                              seconds=round(time.monotonic() - started, 3),
                              reply=(request.get('assistant_message') or {}).get('content', ''),
                              error=request.get('error'))
                before = tuple(len(report[k]) for k in ('provider_calls', 'searches', 'reads'))
                store.submit(conversation['id'], payload)
                report['replay_no_new_calls'] = before == tuple(len(report[k]) for k in ('provider_calls', 'searches', 'reads'))
                reopened = ConversationStore(path).request(conversation['id'], payload.request_id)
                report['reopen_exact_reply'] = reopened.get('assistant_message') == request.get('assistant_message')
                report['source_urls'] = list(dict.fromkeys(re.findall(r'https?://[^\s)\]>]+', report['reply'])))
                assert report['status'] == 'succeeded', 'Actual configured provider request did not succeed'
                assert report['searches'], 'No actual search occurred'
                assert report['source_urls'], 'No actual source URLs returned'
                assert report['replay_no_new_calls'] and report['reopen_exact_reply']
        finally:
            store.close()
except Exception as exc:
    report['smoke_error_type'] = type(exc).__name__
    raise
finally:
    OUTPUT.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
    print(json.dumps(report, indent=2, ensure_ascii=False))
