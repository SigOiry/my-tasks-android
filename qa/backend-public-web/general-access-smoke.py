"""Opt-in actual providers, maintained backend, disposable conversation DB only.

No production API, tasks, chats, configuration or deployment writes. Instrument
real functions (not fabricated search/model responses). Save partial failures too.
"""
import hashlib
import json
import logging
import sys
import tempfile
import time
import uuid
from pathlib import Path
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'server'))
from conversations import ConversationStore, NewConversation, SendMessage
from hermes_chat import call_llm
from public_web import search_public, read_public, research_decision

OUTPUT = Path(__file__).with_name('general-access-real-smoke.json')
report = {'scratch_only': True, 'network_mocked': False, 'service_started': False,
          'maintained_modules_sha256': {name: hashlib.sha256((ROOT / 'server' / name).read_bytes()).hexdigest()
                                       for name in ('public_web.py', 'conversations.py', 'hermes_chat.py')},
          'cases': []}
logging.disable(logging.CRITICAL)


def run():
    with tempfile.TemporaryDirectory(prefix='general-public-web-', dir='/opt/data/cache/scratch') as tmp:
        path = Path(tmp) / 'conversations.sqlite'
        store = ConversationStore(path)
        store.start(lambda _: {})
        try:
            for prompt in ('What is the capital of France?', 'Quelle est la capitale de l’Italie ?',
                           'Summarize https://www.python.org/'):
                case = {'prompt': prompt, 'decision': research_decision(prompt),
                        'provider_calls': [], 'searches': [], 'reads': []}
                report['cases'].append(case)
                def provider(**kwargs):
                    route = {}
                    entry = {'tools': [tool['function']['name'] for tool in kwargs['tools']]}
                    case['provider_calls'].append(entry)
                    result = call_llm(**kwargs, route_info=route)
                    entry['provider'] = route.get('provider', 'unspecified')
                    return result
                def search(query, **kwargs):
                    entry = {'query': query}
                    case['searches'].append(entry)
                    result = search_public(query, **kwargs)
                    entry['urls'] = [row['url'] for row in result]
                    return result
                def read(url, **kwargs):
                    entry = {'url': url}
                    case['reads'].append(entry)
                    try:
                        result = read_public(url, **kwargs)
                        entry.update(final_url=result['url'], characters=len(result['text']))
                        return result
                    except Exception as exc:
                        entry['error_type'] = type(exc).__name__
                        raise
                with patch('hermes_chat.call_llm', side_effect=provider), patch(
                        'public_web.search_public', side_effect=search), patch('public_web.read_public', side_effect=read):
                    thread = store.create(NewConversation(task_id=None, title='Isolated general-web smoke'))
                    request = SendMessage(request_id=str(uuid.uuid4()), content=prompt, mode='question', expected_revision=1)
                    started = time.monotonic()
                    store.submit(thread['id'], request)
                    while True:
                        result = store.request(thread['id'], request.request_id)
                        if result['status'] not in ('queued', 'running'):
                            break
                        if time.monotonic() - started > 200:
                            raise TimeoutError('Scratch smoke exceeded deadline')
                        time.sleep(.05)
                    case.update(status=result['status'], error=result['error'],
                                reply=(result.get('assistant_message') or {}).get('content', ''),
                                seconds=round(time.monotonic() - started, 3))
                    before = [len(case[k]) for k in ('provider_calls', 'searches', 'reads')]
                    store.submit(thread['id'], request)
                    case['replay_no_new_calls'] = before == [len(case[k]) for k in ('provider_calls', 'searches', 'reads')]
                    reopened = ConversationStore(path).request(thread['id'], request.request_id)
                    case['reopen_exact_reply'] = reopened.get('assistant_message') == result.get('assistant_message')
                OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
                assert case['status'] == 'succeeded', 'Real provider reply failed'
                assert case['replay_no_new_calls'] and case['reopen_exact_reply']
                if prompt.startswith('Summarize'):
                    assert not case['searches'], 'Direct URL disclosed to search'
                    assert any('characters' in entry for entry in case['reads']), 'No actual page read'
                    assert 'Page read:' in case['reply']
                else:
                    assert case['searches'] and case['searches'][0].get('urls'), 'No actual search results'
                    assert 'Sources:' in case['reply']
        finally:
            store.close()


if __name__ == '__main__':
    try:
        run()
    except Exception as exc:
        report['smoke_error_type'] = type(exc).__name__
        raise
    finally:
        OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
        print(json.dumps(report, ensure_ascii=False, indent=2))
