"""Local-only owner provisioning. No public registration or password reset."""
import argparse
import os
import secrets
import sqlite3
import stat
import time
from pathlib import Path
from urllib.parse import urlsplit
from account_auth import AccountStore,digest


def private_password(path):
    fd = os.open(path,os.O_RDONLY|os.O_NOFOLLOW)
    with os.fdopen(fd,'rb') as stream:
        mode = os.fstat(stream.fileno()).st_mode
        if not stat.S_ISREG(mode) or mode & 0o077:
            raise ValueError('Password input must be a private regular file (0600 or 0400)')
        raw = stream.read(1025)
    if len(raw)>1024:
        raise ValueError('Password input too long')
    return raw.decode('utf-8')  # Exact bytes: do not strip spaces or newlines.


def provision(*,db,activation_file,origin,username=None,password_file=None,rotate=False):
    db,activation_file = Path(db),Path(activation_file)
    parsed = urlsplit(origin)
    if parsed.scheme not in {'http','https'} or not parsed.netloc or parsed.username or parsed.password or parsed.path or parsed.query or parsed.fragment or any(c.isspace() for c in origin):
        raise ValueError('An exact HTTP(S) origin is required')
    if parsed.scheme=='http' and parsed.hostname not in {'localhost','127.0.0.1','::1','testserver'}:
        raise ValueError('Non-loopback activation requires HTTPS')
    if activation_file.resolve()==db.resolve() or (password_file and activation_file.resolve()==Path(password_file).resolve()):
        raise ValueError('Activation output requires a distinct private path')
    activation_file.parent.mkdir(parents=True,exist_ok=True,mode=0o700)
    if activation_file.parent.stat().st_mode & 0o077:
        raise ValueError('Activation output directory must be private (0700)')
    # Read and validate input before creating any account metadata.
    password = private_password(password_file) if not rotate else None
    if rotate and not db.is_file():
        raise ValueError('Account is not initialized')
    store = AccountStore(db)
    snapshot = store.snapshot()
    if not rotate and snapshot:
        raise ValueError('Account already initialized; refusing reset')
    if rotate and (not snapshot or snapshot['active']):
        raise ValueError('Activation can only rotate before initial password replacement')
    fd = os.open(activation_file,os.O_WRONLY|os.O_CREAT|os.O_EXCL|os.O_NOFOLLOW,0o600)
    try:
        with os.fdopen(fd,'w') as stream:
            if rotate:
                code = secrets.token_hex(32)
                with store.connect() as con:
                    con.execute('BEGIN IMMEDIATE')
                    changed = con.execute('UPDATE account SET activation_hash=?,activation_expires=?,epoch=epoch+1 WHERE id=1 AND active=0',(digest(code),time.time()+7*86400)).rowcount
                    if changed!=1:
                        raise ValueError('Activation can only rotate before initial password replacement')
                    con.execute('DELETE FROM account_sessions')
            else:
                code = store.initialize(username,password)
            stream.write(origin+'/#activate='+code+'\n')
            stream.flush()
            os.fsync(stream.fileno())
    except BaseException:
        activation_file.unlink(missing_ok=True)
        raise
    return {'account_db':str(db),'private_activation_file':str(activation_file)}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='command',required=True)
    for name in ('init','rotate-activation'):
        command = commands.add_parser(name)
        command.add_argument('--db',type=Path,required=True)
        command.add_argument('--activation-file',type=Path,required=True)
        command.add_argument('--origin',required=True)
        if name=='init':
            command.add_argument('--username',required=True)
            command.add_argument('--password-file',type=Path,required=True)
    args = parser.parse_args()
    try:
        result = provision(db=args.db,activation_file=args.activation_file,origin=args.origin,username=getattr(args,'username',None),password_file=getattr(args,'password_file',None),rotate=args.command=='rotate-activation')
    except (ValueError,OSError,sqlite3.Error,UnicodeError):
        parser.exit(1,'Provisioning refused; check private paths, input permissions, and uninitialized/bootstrap account state. No account reset performed.\n')
    import json
    print(json.dumps({'status':'activation_rotated' if args.command=='rotate-activation' else 'initialized',**result}))

if __name__=='__main__': main()
