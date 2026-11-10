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

def prepare(root, port, *, seed=False, copy_db=None, seed_conversations=False, account=False):
    root = Path(root).resolve()
    if not root.is_relative_to(SCRATCH) or root == SCRATCH:
        raise ValueError('QA root must be a private child directory in scratch')
    if seed_conversations and (not seed or copy_db):
        raise ValueError('Conversation fixtures require synthetic tasks, never a copied store')
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
    if seed_conversations:
        from conversations import ConversationStore,NewConversation,now
        from mobile_api import dashboard
        import uuid
        store = ConversationStore(root/'conversations.sqlite')
        if not store.list()['conversations']:
            tasks = dashboard.taskdb.Store(db).read()['tasks']
            task = tasks[0]
            for title,attached,count in [('QA · Plan de travail',task,3),('QA · Autre discussion',task,1),('QA · Questions libres',None,24)]:
                conversation = store.create(NewConversation(task_id=attached['id'] if attached else None,title=title),attached)
                with store.connect() as con:
                    for number in range(count):
                        rid = str(uuid.uuid4())
                        stamp = now()
                        con.execute('INSERT INTO requests(id,conversation_id,content,mode,status,created_at) VALUES(?,?,?,?,?,?)',
                                    (rid,conversation['id'],f'QA fixture question {number+1}','question','succeeded',stamp))
                        for role,text in [('user',f'QA fixture question {number+1}'),('assistant',f'Synthetic QA answer {number+1} — no provider was called.')]:
                            con.execute('INSERT INTO messages(conversation_id,request_id,role,content,created_at) VALUES(?,?,?,?,?)',(conversation['id'],rid,role,text,stamp))
                    con.execute('UPDATE conversations SET revision=?,updated_at=? WHERE id=?',(1+2*count,now(),conversation['id']))
    if account:
        from account_auth import AccountStore
        from account_admin import provision
        account_store = AccountStore(root/'account.sqlite')
        if not account_store.snapshot():
            password_file = root/'qa-password'
            import os
            fd = os.open(password_file,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
            with os.fdopen(fd,'w') as stream:
                stream.write(secrets.token_urlsafe(32))
            provision(db=root/'account.sqlite',activation_file=root/'qa-activation',origin=f'http://127.0.0.1:{port}',username='qa-owner',password_file=password_file)
    return dict(db_path=db,token_file=token,metadata_path=root/'events.sqlite',session_path=root/'sessions.sqlite',conversation_path=root/'conversations.sqlite',email_path=root/'email.sqlite',allow_legacy_uninitialized=not account,account_path=root/'account.sqlite',web_origin=f'http://127.0.0.1:{port}',cookie_secure=False)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--port',type=int,default=9138)
    parser.add_argument('--root',type=Path)
    parser.add_argument('--copy-db',type=Path,help='Copy this DB once; never writes to its source')
    parser.add_argument('--seed',action='store_true',help='Explicit synthetic scratch-only fixtures')
    parser.add_argument('--seed-conversations',action='store_true',help='Synthetic task/free conversation fixtures; requires --seed')
    parser.add_argument('--account',action='store_true',help='Private random scratch account and activation/password files (qa-owner)')
    parser.add_argument('--prepare-only',action='store_true')
    args = parser.parse_args()
    if args.seed and args.copy_db:
        parser.error('Choose synthetic fixtures OR a copied database')
    root = args.root or Path(tempfile.mkdtemp(prefix='mytasks-web-qa-',dir=SCRATCH))
    config = prepare(root,args.port,seed=args.seed,copy_db=args.copy_db,seed_conversations=args.seed_conversations,account=args.account)
    report = {'url':config['web_origin'],'scratch_root':str(root),'private_token_file':str(config['token_file'])}
    if args.account:
        report.update(username='qa-owner',private_password_file=str(root/'qa-password'),private_activation_file=str(root/'qa-activation'),account_db=str(root/'account.sqlite'))
    print(json.dumps(report),flush=True)
    if not args.prepare_only:
        import uvicorn
        from mobile_api import create_app
        uvicorn.run(create_app(**config),host='127.0.0.1',port=args.port,proxy_headers=False,access_log=False)

if __name__ == '__main__':
    main()
