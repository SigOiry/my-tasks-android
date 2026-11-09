"""Loopback-only QA; synthetic fixtures or an explicit read-only database copy.
Never imports live credentials and never serves the fixture token over HTTP.
"""
import argparse
import json
import secrets
import shutil
import tempfile
from pathlib import Path

SCRATCH = Path('/opt/data/cache/scratch')

def prepare(root, port, *, seed=False, copy_db=None):
    root = Path(root).resolve()
    if not root.is_relative_to(SCRATCH) or root == SCRATCH:
        raise ValueError('QA root must be a private child directory in scratch')
    root.mkdir(mode=0o700,parents=True,exist_ok=True)
    root.chmod(0o700)
    token = root/'qa-token'
    if not token.exists():
        token.write_text(secrets.token_urlsafe(48))
    token.chmod(0o600)
    db = root/'tasks.json'
    if copy_db and not db.exists():
        shutil.copyfile(Path(copy_db),db)
        db.chmod(0o600)
    if seed and not db.exists():
        from mobile_api import dashboard
        from datetime import datetime,timedelta
        store = dashboard.taskdb.Store(db)
        today = datetime.now(dashboard.taskdb.ROME).date()
        store.add('Review the observatory proposal',project='Research',priority='high',due_date=today.isoformat(),description='QA fixture — isolated synthetic task. Review the methodology and prepare comments.',status='todo')
        source = store.add('Prepare a reply to the editorial team',project='Research',priority='medium',status='waiting',description='QA fixture — email drafting, never sending.')
        store.add('Book the team planning session',project='Operations',priority='medium',due_date=(today+timedelta(days=2)).isoformat(),status='in_progress')
        store.add('Collect workshop reading notes',project='Teaching',priority='low',status='inbox')
        store.add('Archive the project checklist',project='Operations',priority='low',status='completed')
        dashboard.initialize_notifications(db)
        store.edit(source['id'],description='QA fixture — New editorial context arrived. Please review the proposed timeline before drafting a response.')
    return dict(db_path=db,token_file=token,metadata_path=root/'events.sqlite',session_path=root/'sessions.sqlite',web_origin=f'http://127.0.0.1:{port}',cookie_secure=False)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port',type=int,default=9138)
    parser.add_argument('--root',type=Path)
    parser.add_argument('--copy-db',type=Path,help='Copy this DB once; never writes to its source')
    parser.add_argument('--seed',action='store_true',help='Explicit synthetic scratch-only fixtures')
    parser.add_argument('--prepare-only',action='store_true')
    args = parser.parse_args()
    if args.seed and args.copy_db:
        parser.error('Choose synthetic fixtures OR a copied database')
    root = args.root or Path(tempfile.mkdtemp(prefix='mytasks-web-qa-',dir=SCRATCH))
    config = prepare(root,args.port,seed=args.seed,copy_db=args.copy_db)
    print(json.dumps({'url':config['web_origin'],'scratch_root':str(root),'private_token_file':str(config['token_file'])}),flush=True)
    if not args.prepare_only:
        import uvicorn
        from mobile_api import create_app
        uvicorn.run(create_app(**config),host='127.0.0.1',port=args.port,proxy_headers=False,access_log=False)

if __name__ == '__main__':
    main()
