"""Opt-in, read-only exact-source adapter probe. Prints no mail or identifiers."""
import hashlib
import importlib
import json
from pathlib import Path
import argparse


def run():
    providers=importlib.import_module('email_providers')
    adapter=providers.PrivateProviders()
    data=json.loads(Path('/opt/data/personal-assistant/tasks.json').read_text())
    tasks=sorted(data['tasks'],key=lambda t:t.get('updated_at',''),reverse=True)
    refs=[r for t in tasks for r in t.get('source_references',[]) if isinstance(r,str)]
    selected={'gmail':next(r for r in refs if r.startswith('gmail:') and ':message:' in r),
              'nantes':next(r for r in refs if r.startswith('nantes-imap://'))}
    result={}
    for kind,reference in selected.items():
        try:
            message=adapter.read(reference)
            result[kind]={'readable':True,'exact_reference_preserved':message['reference']==reference,
                          'has_rfc_message_id':bool(message.get('message_id')),
                          'thread_context_available':bool(message.get('thread')),
                          'account_identity_available':bool(message.get('account'))}
            assert result[kind]['exact_reference_preserved'] and result[kind]['has_rfc_message_id']
        except Exception as error:
            result[kind]={'readable':False,'error_type':type(error).__name__}
    result['scope']='Read-only exact messages, no mailbox flags changed, no model or send called'
    module_path=getattr(providers,'__file__',None)
    if not isinstance(module_path,str):raise RuntimeError('Provider module path unavailable')
    result['source_sha256']=hashlib.sha256(Path(module_path).read_bytes()).hexdigest()
    return result


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    result=run();args.output.write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
    if not all(result[kind].get('readable') for kind in ('gmail','nantes')):raise SystemExit(1)
