"""Run ON Linux: real TLS/native-auth/read probe, never emits credentials."""
import argparse
import json
from pathlib import Path
import ssl
import urllib.error
import urllib.request


def verify(root):
    context = ssl.create_default_context(cafile=str(root/'qa-cert.pem'))
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=context))
    origin = 'https://127.0.0.1:9147'
    def request(path, payload=None, token=None):
        headers = {'Content-Type':'application/json'}
        if token:
            headers['Authorization'] = 'Bearer '+token
        body = None if payload is None else json.dumps(payload).encode()
        with opener.open(urllib.request.Request(origin+path,data=body,headers=headers),timeout=10) as response:
            return json.load(response)
    health = request('/health')
    try:
        request('/api/tasks')
        raise AssertionError('Anonymous task access was unexpectedly allowed')
    except urllib.error.HTTPError as error:
        if error.code != 401:
            raise
        error.close()
    session = request('/auth/native/login',{'username':'qa-owner','password':(root/'qa-password').read_text()})
    token = session['session_token']
    try:
        tasks = request('/api/tasks',token=token)
        threads = request('/api/conversations',token=token)
        task_rows = tasks if isinstance(tasks,list) else tasks['tasks']
        assert task_rows and all(t.get('description','').startswith('SYNTHETIC QA') for t in task_rows)
        print(json.dumps({'tls_verified':True,'health':health,'anonymous_tasks':401,
                          'native_login':'ok','tasks':len(task_rows),'conversations':len(threads['conversations'])}))
    finally:
        request('/auth/native/logout',{},token)

if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,required=True)
    verify(parser.parse_args().root)
