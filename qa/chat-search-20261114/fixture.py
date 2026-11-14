"""Isolated native citation QA: synthetic provider and saved fictional mail only.
Bootstrap uses frozen v1.6.2 backend; refresh actual server sources after parent gate.
No production records, credentials, mail provider, or real model calls.
"""
import argparse
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace as NS

REPO = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('email_fixture', REPO/'qa/v1.6.2/fixture.py')
if spec is None or spec.loader is None:
    raise RuntimeError('Required canonical synthetic EmailFixture missing')
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)


class CitationFixture(base.EmailFixture):
    def fixture_provider(self, **kwargs):
        if kwargs.get('tools') != []:
            raise RuntimeError('Synthetic provider refuses all tools')
        self.provider_calls += 1
        text = ('SYNTHETIC QA — citation interaction fixture; no model, search or mail send.\n\n'
                'Read the [Example Domain reference](https://example.org/) for public context. '
                'A second [IANA reference](https://www.iana.org/domains/reserved) is also inline.\n\n'
                'Review the related saved workshop email and task context before acting. '
                'Nothing was sent or changed.\n\n'
                'Unsafe input remains text: [blocked scheme](javascript:alert(1)).')
        return NS(choices=[NS(message=NS(content=text, tool_calls=[]))])

    def seed(self, client, auth):
        super().seed(client, auth)
        store = base.base.dashboard.taskdb.Store(self.root/'tasks.json')
        tasks = store._read()['tasks']
        task = next(t for t in tasks if t.get('action_key') == 'synthetic:workshop:reply')
        response = client.post('/api/conversations', headers=auth,
                               json={'task_id': task['id'], 'title': 'SYNTHETIC QA · Inline citation review'})
        response.raise_for_status()
        self.send(client, auth, response.json(), 'Synthetic QA: explain the saved workshop context with inline public links.')
        (self.root/'citation-fixture.json').write_text(json.dumps({
            'synthetic': True, 'task_id': task['id'],
            'thread_id': response.json()['id'], 'provider': 'synthetic-no-tools-no-model',
            'bootstrap_backend': 'frozen-v162-not-final-release-proof'}))
        (self.root/'citation-fixture.json').chmod(0o600)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--port', type=int, default=9147)
    parser.add_argument('--prepare-only', action='store_true')
    args = parser.parse_args()
    with CitationFixture(args.root, args.port) as fixture:
        print(json.dumps({'synthetic': True, 'ready': True, 'root': str(fixture.root),
                          'port': args.port, 'provider': 'synthetic-no-tools-no-model'}), flush=True)
        if not args.prepare_only:
            import uvicorn
            uvicorn.run(fixture.app, host='127.0.0.1', port=args.port,
                        proxy_headers=False, access_log=False,
                        ssl_keyfile=str(fixture.root/'qa-key.pem'),
                        ssl_certfile=str(fixture.root/'qa-cert.pem'))


if __name__ == '__main__':
    main()
