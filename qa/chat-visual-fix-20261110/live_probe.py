"""Run the real deployed conversation/provider in an isolated temporary store.
No production conversations, tasks, sessions or provider config are modified.
Observation wrappers call the original search, read and provider functions.
"""
import argparse
import hashlib
import sys
import json
import logging
import tempfile
import time
import uuid
from pathlib import Path
from typing import cast
from unittest.mock import patch

from conversations import ConversationStore, NewConversation, SendMessage
from hermes_chat import call_llm
from public_web import read_public, research_decision, search_public

CASES = {
    'current': 'Who won the 2026 Nobel Prize in Physics?',
    'french': 'Quels sont les horaires du musée du Louvre aujourd’hui ?',
    'url': 'Read https://www.python.org/robots.txt and tell me what it says.',
    'greeting': 'Hello!',
    'smalltalk': 'How are you?',
    'hours': 'What are the opening hours of tabaccheria N 196, Via di Prosecco 15, Trieste?',
    'capability': 'Can you access the internet?',
    'no_browse': 'Do not browse. Write a friendly one-sentence greeting.',
}

def run(kind):
    logging.disable(logging.CRITICAL)
    prompt = CASES[kind]
    events = []
    def provider(**kwargs):
        route = {}
        response = call_llm(**kwargs, route_info=route)
        events.append({'kind': 'provider', 'provider': route.get('provider'),
                       'tools': [t['function']['name'] for t in kwargs.get('tools', [])]})
        return response
    def search(query, **kwargs):
        result = search_public(query, **kwargs)
        events.append({'kind': 'search', 'query': query, 'urls': [r['url'] for r in result]})
        return result
    def read(url, **kwargs):
        result = read_public(url, **kwargs)
        events.append({'kind': 'read', 'url': url, 'final_url': result['url'],
                       'characters': len(result['text'])})
        return result
    with tempfile.TemporaryDirectory(prefix='chat-live-fix-probe-') as tmp:
        store = ConversationStore(Path(tmp) / 'isolated.sqlite')
        store.start(lambda _: {})
        try:
            with patch('hermes_chat.call_llm', side_effect=provider), patch('public_web.search_public', side_effect=search), patch('public_web.read_public', side_effect=read):
                conversation = store.create(NewConversation(task_id=None, title='Isolated public-only verification'))
                payload = SendMessage(request_id=str(uuid.uuid4()), content=prompt, mode='question', expected_revision=1)
                store.submit(conversation['id'], payload)
                phases = []
                deadline = time.monotonic() + 230
                while True:
                    result = store.request(conversation['id'], payload.request_id)
                    phase = result.get('web_phase')
                    if phase and (not phases or phases[-1] != phase):
                        phases.append(phase)
                    if result['status'] not in ('queued', 'running'):
                        break
                    if time.monotonic() > deadline:
                        raise RuntimeError('Public-only verification exceeded deadline')
                    time.sleep(.1)
                before = len(events)
                store.submit(conversation['id'], payload)
                reopened = ConversationStore(Path(tmp) / 'isolated.sqlite')
                # A read-only Store has no started worker/admission to close.
                persisted = reopened.request(conversation['id'], payload.request_id)
                return {'case': kind, 'prompt': prompt, 'decision': research_decision(prompt),
                        'status': result['status'], 'reply': (result.get('assistant_message') or {}).get('content', ''),
                        'source_sha256': {name: hashlib.sha256(Path(cast(str, sys.modules[name].__file__)).read_bytes()).hexdigest() for name in ('public_web', 'conversations')},
                        'phases': phases, 'events': events,
                        'replay_no_new_work': len(events) == before,
                        'reopened_exact_reply': persisted.get('assistant_message') == result.get('assistant_message')}
        finally:
            store.close()

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('case', choices=CASES)
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    report = run(args.case)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2, ensure_ascii=False) + '\n')
    print('PUBLIC_CHAT_RECEIPT_BEGIN', flush=True)
    print(json.dumps(report, ensure_ascii=False), flush=True)
    print('PUBLIC_CHAT_RECEIPT_END', flush=True)
