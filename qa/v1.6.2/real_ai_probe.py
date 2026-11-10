"""Opt-in real configured-model proof on synthetic mail and isolated stores.
Run inside the deployed container. It never calls any mail transport.
"""
import argparse
import hashlib
import importlib.util
import json
import logging
from pathlib import Path
import sys
import tempfile
import time
import uuid

HERE=Path(__file__).resolve().parent
REPO=HERE.parents[1]
# Caller sets PYTHONPATH to the exact maintained/deployed server under test.
import importlib
original_model=importlib.import_module('hermes_chat').call_llm
email_workspace=importlib.import_module('email_workspace')
conversations=importlib.import_module('conversations')
# Cache exact caller-selected deployed modules before importing QA helpers.
for module_name in ('mobile_api','email_routes','email_providers','citations','conversation_titles'):
    importlib.import_module(module_name)

def module_hash(module):
    location=getattr(module,'__file__',None)
    if not isinstance(location,str):raise RuntimeError('Module source unavailable')
    return hashlib.sha256(Path(location).read_bytes()).hexdigest()

spec=importlib.util.spec_from_file_location('email_qa_fixture',HERE/'fixture.py')
if spec is None or spec.loader is None:raise RuntimeError('Fixture unavailable')
fixture_module=importlib.util.module_from_spec(spec);spec.loader.exec_module(fixture_module)


def run():
    logging.disable(logging.CRITICAL)
    calls=[]
    def observed_model(**kwargs):
        assert kwargs.get('tools')==[]
        route={}
        result=original_model(**kwargs,route_info=route)
        calls.append({'provider':route.get('provider'),'tools':[]})
        return result
    with tempfile.TemporaryDirectory(prefix='email-real-model-proof-') as temp:
        with fixture_module.EmailFixture(Path(temp)) as fixture:
            fixture.app.state.email_workspace.call_llm=observed_model
            with fixture.client() as client:
                auth=fixture.login(client)
                task=next(t for t in client.get('/api/tasks',headers=auth).json()['tasks'] if t['title']=='Reply to the workshop update')
                source=client.get('/api/tasks/'+task['id']+'/email-sources',headers=auth).json()['sources'][0]
                response=client.post('/api/email/drafts',headers=auth,json={'request_id':str(uuid.uuid4()),'task_id':task['id'],'source_id':source['id'],'update_token':task['notification']['token'],'reply_all':False})
                response.raise_for_status();draft=response.json();base='/api/email/drafts/'+draft['id'];jobs=[]
                def assist(intent,instruction):
                    payload={'request_id':str(uuid.uuid4()),'expected_revision':draft['revision'],'intent':intent,'instruction':instruction}
                    admitted=client.post(base+'/assist',headers=auth,json=payload);admitted.raise_for_status()
                    deadline=time.monotonic()+110
                    while time.monotonic()<deadline:
                        status=client.get(base+'/assist/'+payload['request_id'],headers=auth).json()
                        if status['status'] not in ('queued','running'):break
                        time.sleep(.1)
                    else:raise TimeoutError('Model proof exceeded bounded wait')
                    assert status['status']=='succeeded',status.get('error')
                    before=len(calls)
                    replay=client.post(base+'/assist',headers=auth,json=payload);replay.raise_for_status()
                    assert replay.json()['id']==status['id'] and len(calls)==before
                    jobs.append({'intent':intent,'status':status['status'],'replay_no_new_work':True})
                    return status
                generated=assist('draft','Thank them for the schedule. Say I will review it before confirming availability; do not invent facts or commitments.')
                assert client.get(base,headers=auth).json()['body']==''
                p=generated['proposal']
                applied=client.post(base+'/proposals/'+p['id']+'/apply',headers=auth,json={'expected_revision':draft['revision']});applied.raise_for_status();draft=applied.json()
                assert draft['body'].strip()
                discussed=assist('discuss','Is this wording polite and does it avoid confirming availability prematurely?')
                assert 'proposal' not in discussed and client.get(base,headers=auth).json()['body']==draft['body']
                improved=assist('improve','Correct spelling and grammar while preserving language, meaning and tone. Return only the reply body.')
                newer=client.patch(base,headers=auth,json={'expected_revision':draft['revision'],'body':draft['body']+'\n\nAdditional human note.'});newer.raise_for_status();draft=newer.json()
                stale=client.post(base+'/proposals/'+improved['proposal']['id']+'/apply',headers=auth,json={'expected_revision':draft['revision']})
                assert stale.status_code==409 and client.get(base,headers=auth).json()['body']==draft['body']
                assert not fixture.mail.sent_file.exists()
                title=importlib.import_module('conversation_titles').generate_title({'messages':[{'role':'user','content':'Help me prepare a polite reply about a revised workshop schedule.'},{'role':'assistant','content':'Review the schedule and ask the coordinator to confirm the start time.'}]})
                assert title.strip() and len(title)<=90 and title.casefold() not in ('new conversation','ai chat')
                return {'synthetic_mail':True,'real_configured_model':True,'real_generated_title':title,'mail_transports_called':0,'jobs':jobs,'calls':calls,
                        'generated_text_was_preview_only':True,'explicit_apply_worked':True,'discussion_did_not_edit':True,'stale_improvement_rejected':True,
                        'source_sha256':{'email_workspace.py':module_hash(email_workspace),
                                         'conversations.py':module_hash(conversations)}}


if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--output',type=Path,required=True);args=parser.parse_args()
    result=run();args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(result,indent=2)+'\n')
    print('V162_REAL_MODEL_PROOF_BEGIN');print(json.dumps(result));print('V162_REAL_MODEL_PROOF_END')
