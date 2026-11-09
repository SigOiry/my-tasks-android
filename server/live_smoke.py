"""Explicit opt-in live provider probe; only synthetic scratch task context."""
import tempfile
from pathlib import Path
from fastapi.testclient import TestClient
from mobile_api import create_app


def main():
    with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as name:
        root = Path(name)
        token = root/'token'
        token.write_text('live-smoke-'+'x'*48)
        token.chmod(0o600)
        client = TestClient(create_app(db_path=root/'tasks.json',token_file=token,metadata_path=root/'events.sqlite'))
        auth = {'Authorization':'Bearer '+token.read_text()}
        task = client.post('/api/tasks',headers=auth,json={'title':'Synthetic meeting reply','description':'The synthetic meeting is Monday at 10:00. This is test data, not a real task.'}).json()
        response = client.post('/api/chat',headers=auth,json={'task_id':task['id'],'mode':'email_draft','message':'Draft a one-sentence email confirming the meeting time, no invented recipient.'})
        print('HTTP',response.status_code)
        print(response.text)
        response.raise_for_status()


if __name__ == '__main__':
    main()
