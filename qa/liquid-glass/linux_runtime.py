"""Allowlisted Linux-local synthetic runtime bundle; never tunnels or copies signing keys."""
import argparse
from contextlib import closing
import hashlib
import json
from pathlib import Path
import sqlite3
import subprocess
import tarfile

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
SCRATCH = Path('/opt/data/cache/scratch')
HOST = 'simon@192.168.0.133'
REMOTE = '/home/simon/hermes-apks/qa-runtime'
SSH = ['ssh','-i','/opt/data/ssh/android-testing','-o','UserKnownHostsFile=/opt/data/ssh/android-known_hosts',
       '-o','StrictHostKeyChecking=yes','-o','BatchMode=yes',HOST]
SERVER = ('mobile_api.py','qa_server.py','account_auth.py','account_admin.py','account_routes.py',
          'web_auth.py','conversations.py','hermes_chat.py','public_web.py','event_feed.py','releases.py')
DATA = ('liquid-glass-synthetic.json','tasks.json','qa-cert.pem','qa-key.pem','qa-token','qa-password',
        'account.sqlite','conversations.sqlite')
# Only filesystem location literals change in generated copies. Fail closed on upstream drift.
RELOCATIONS = {
    'server/mobile_api.py': ("Path('/opt/data/plugins/personal-tasks/dashboard/plugin_api.py')", "Path(__file__).resolve().parents[1] / 'integration' / 'plugin_api.py'"),
    'server/qa_server.py': ("Path('/opt/data/cache/scratch')", "Path(__file__).resolve().parents[1] / 'scratch'"),
    'integration/plugin_api.py': ("Path('/opt/data/personal-assistant/taskdb.py')", "Path(__file__).resolve().with_name('taskdb.py')"),
}

def package(root, bundle):
    root, bundle = Path(root).resolve(), Path(bundle).resolve()
    if root == SCRATCH or not root.is_relative_to(SCRATCH):
        raise ValueError('Only an explicitly synthetic scratch fixture may be transferred')
    for name in DATA:
        if (root/name).is_symlink() or not (root/name).is_file():
            raise ValueError('Missing or symlink fixture member: '+name)
    if json.loads((root/'liquid-glass-synthetic.json').read_text()) != {'synthetic':True,'version':1}:
        raise ValueError('Invalid synthetic marker')
    tasks = json.loads((root/'tasks.json').read_text())['tasks']
    if not tasks or any(not t.get('description','').startswith('SYNTHETIC QA') for t in tasks):
        raise ValueError('Non-synthetic task fixture')
    bundle.mkdir(mode=0o700)
    def write(relative, content):
        target = bundle/relative
        target.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
        target.write_bytes(content)
        target.chmod(0o600)
    sources = {f'server/{name}':REPO/'server'/name for name in SERVER}
    sources.update({'integration/plugin_api.py':Path('/opt/data/plugins/personal-tasks/dashboard/plugin_api.py'),
                    'integration/taskdb.py':Path('/opt/data/personal-assistant/taskdb.py'),
                    'qa/liquid-glass/verify_linux.py':HERE/'verify_linux.py',
                    'qa/liquid-glass/runtime.py':HERE/'runtime.py',
                    'qa/liquid-glass/fixtures/public-source-replay.json':HERE/'fixtures/public-source-replay.json',
                    'qa/liquid-glass/live_mutate.py':HERE/'live_mutate.py',
                    'qa/liquid-glass/live_summary.py':HERE/'live_summary.py'})
    manifest = {}
    for relative, source in sources.items():
        content = source.read_bytes()
        manifest[relative] = {'source_sha256':hashlib.sha256(content).hexdigest()}
        if relative in RELOCATIONS:
            old,new = RELOCATIONS[relative]
            text = content.decode()
            if text.count(old) != 1:
                raise ValueError('Canonical path literal changed: '+relative)
            content = text.replace(old,new).encode()
        write(relative,content)
        manifest[relative]['bundle_sha256'] = hashlib.sha256(content).hexdigest()
    for name in DATA:
        target = bundle/'scratch/runtime'/name
        target.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
        if name.endswith('.sqlite'):
            # Consistent live DB snapshot, never copy WAL files or stored sessions.
            with closing(sqlite3.connect((root/name).as_uri()+'?mode=ro',uri=True)) as src:
                with closing(sqlite3.connect(target)) as dst:
                    src.backup(dst)
            target.chmod(0o600)
        else:
            write('scratch/runtime/'+name,(root/name).read_bytes())
    write('source-manifest.json',json.dumps(manifest,indent=2).encode())
    write('requirements.txt',b'fastapi==0.133.1\nhttpx==0.28.1\nuvicorn==0.54.0\npydantic==2.14.1\n')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,required=True)
    parser.add_argument('--bundle',type=Path,required=True,help='New private scratch directory')
    parser.add_argument('--deploy',action='store_true',help='Install code/data + isolated venv; does not start backend')
    args = parser.parse_args()
    if not args.bundle.resolve().is_relative_to(SCRATCH):
        parser.error('Bundle must live in private scratch')
    package(args.root,args.bundle)
    if args.deploy:
        archive = args.bundle.with_suffix('.tar.gz')
        if archive.exists():
            raise ValueError('Archive already exists')
        # Archive and destination are private before any synthetic credential is written.
        import os
        fd = os.open(archive,os.O_CREAT|os.O_EXCL|os.O_WRONLY,0o600)
        with os.fdopen(fd,'wb') as stream:
            with tarfile.open(fileobj=stream,mode='w:gz') as tar:
                for path in sorted(args.bundle.iterdir()):
                    tar.add(path,arcname=path.name)
        subprocess.run(SSH+['python3 --version; command -v python3; python3 -m pip --version'],check=True)
        subprocess.run(SSH+[f'umask 077; mkdir {REMOTE}'],check=True)
        with archive.open('rb') as stream:
            subprocess.run(SSH+[f'tar -xzf - -C {REMOTE}'],stdin=stream,check=True)
        subprocess.run(SSH+[f'cd {REMOTE} && umask 077 && python3 -m venv venv && venv/bin/python -m pip install -r requirements.txt && venv/bin/python -m pip check'],check=True)
        print('Installed only. Start using the README handoff command; do not use SSH forwarding.')
    print(json.dumps({'bundle':str(args.bundle),'remote':REMOTE,'username':'qa-owner'}))

if __name__ == '__main__': main()
