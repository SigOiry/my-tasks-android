"""Explicit synthetic mail/model transport for actual HTTP/native UI acceptance.
Never imports Google/Nantes credentials or transmits mail outside this fixture.
"""
import argparse
import copy
import importlib.util
import json
import sys
import time
from pathlib import Path
from types import SimpleNamespace as NS

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
sys.path.insert(0, str(REPO/'server'))
spec = importlib.util.spec_from_file_location('liquid_fixture', REPO/'qa/liquid-glass/runtime.py')
if spec is None or spec.loader is None:
    raise RuntimeError('Synthetic fixture module unavailable')
base = importlib.util.module_from_spec(spec)
spec.loader.exec_module(base)


class SyntheticMail:
    def __init__(self, root):
        self.root = Path(root)
        self.sent_file = self.root/'synthetic-sent.json'
        self.refs = ['gmail:owner@example.test:message:'+x for x in ('abcd','abce','abcf')]
    def resolve(self, reference):
        if reference == 'gmail:owner@example.test:thread:aaaa':
            return [self.read(r) for r in self.refs[:2]]
        return [self.read(reference)]
    def read(self, reference):
        if reference not in self.refs:
            raise ValueError('Synthetic source unavailable')
        number = self.refs.index(reference)
        return dict(reference=reference, provider='gmail', account='owner@example.test',
                    identities=['owner@example.test'], sender='Workshop team <team@example.test>',
                    reply_to='Coordinator <coordinator@example.test>', to='owner@example.test',
                    cc='colleague@example.test', subject='Workshop schedule — review',
                    date='2026-11-10T15:00:00+01:00',
                    body='SYNTHETIC QA email. Could you review the revised workshop schedule and confirm whether Friday works? '+str(number+1),
                    message_id='<workshop-'+str(number+1)+'@example.test>', references='<planning@example.test>',
                    thread_id='aaaa', outgoing=False,
                    thread=[dict(sender='Owner <owner@example.test>',subject='Workshop schedule — review',date='2026-11-09',
                                 body='SYNTHETIC QA outgoing style sample: Thanks for the update. I will review the details and get back to you. Best regards.'),
                            dict(sender='Workshop team <team@example.test>',subject='Workshop schedule — review',date='2026-11-10',
                                 body='SYNTHETIC QA incoming: Please confirm whether Friday works for the revised schedule.')])
    def send(self, message, raw):
        from email.parser import BytesParser
        from email import policy
        parsed = BytesParser(policy=policy.default).parsebytes(raw)
        assert parsed['In-Reply-To'] == message['message_id']
        assert parsed['Message-ID'] == message['outgoing_message_id']
        assert parsed['Subject'] == message['subject']
        rows = json.loads(self.sent_file.read_text()) if self.sent_file.exists() else []
        if any(r['message_id'] == message['outgoing_message_id'] for r in rows):
            raise AssertionError('Duplicate synthetic transport submission')
        receipt = dict(id='synthetic-receipt-'+str(len(rows)+1), message_id=message['outgoing_message_id'],
                       synthetic=True, subject=str(parsed['Subject']), body=parsed.get_content())
        rows.append(receipt)
        self.sent_file.write_text(json.dumps(rows, indent=2))
        self.sent_file.chmod(0o600)
        return receipt
    def reconcile(self, message):
        if not self.sent_file.exists():
            return None
        return next((r for r in json.loads(self.sent_file.read_text())
                     if r['message_id'] == message['outgoing_message_id']), None)


def synthetic_writing_model(**kwargs):
    assert kwargs.get('tools') == []
    time.sleep(.2)
    mode = kwargs['messages'][0]['content']
    if 'Intent: discuss' in mode:
        text = 'SYNTHETIC QA writing advice: The response is clear. A brief thank-you and one specific question would soften the tone without making a commitment.'
    elif 'Intent: improve' in mode:
        text = 'Hello,\n\nThank you for the revised schedule. I will review the details and respond shortly. Could you confirm the deadline?\n\nBest regards'
    else:
        text = 'Hello,\n\nThank you for sharing the revised workshop schedule. Friday works for me. Could you confirm the start time?\n\nBest regards'
    return NS(choices=[NS(message=NS(content=text, tool_calls=[]))])


class EmailFixture(base.Fixture):
    def __init__(self, root, port=9147):
        self.mail = SyntheticMail(root)
        super().__init__(root, port, email_provider=self.mail, email_llm=synthetic_writing_model)
    def seed(self, client, auth):
        super().seed(client, auth)
        store = base.dashboard.taskdb.Store(self.root/'tasks.json')
        candidates = [
            dict(action_key='synthetic:workshop:reply',title='Reply to the workshop update',source='Gmail',source_reference=self.mail.refs[0],
                 source_references=[self.mail.refs[0]],priority='high',project='Research',
                 description='SYNTHETIC QA — Review the workshop schedule and prepare a reply.',evidence=['SYNTHETIC QA revised workshop schedule']),
            dict(action_key='synthetic:workshop:choice',title='Choose which workshop message to answer',source='Gmail',source_reference=self.mail.refs[1],
                 source_references=self.mail.refs[1:],project='Operations',
                 description='SYNTHETIC QA — Multiple historical email references require explicit selection.',evidence=['SYNTHETIC QA multiple email sources']),
            dict(action_key='synthetic:workshop:missing',title='Unavailable source recovery',source='Gmail',source_reference='gmail:owner@example.test:message:ffff',
                 description='SYNTHETIC QA — This exact message is deliberately unavailable.',evidence=['SYNTHETIC QA unavailable email source'])]
        store.sync({'reviewed':True,'candidates':candidates})
        with store._lock():
            db = store._read()
            task = next(t for t in db['tasks'] if t.get('action_key') == 'synthetic:workshop:reply')
            snapshot = base.dashboard.meaningful_snapshot(task)
            db.setdefault('dashboard', {}).setdefault('notification_origins', {})[task['id']] = {
                'snapshot':snapshot,'run_id':'synthetic-qa','manifest_hash':'synthetic',
                'origins':[dict(reference=self.mail.refs[0],role='trigger',item_id='synthetic-qa',item_hash='synthetic')]}
            db['dashboard'].setdefault('notification_summaries', {})[task['id']] = {
                'snapshot':snapshot,'sections':dict(context='The team is preparing a workshop.',
                                                   update='The coordinator shared a **revised schedule** and asks whether Friday works.',
                                                   next_steps='Review the schedule, then reply when ready.')}
            store._write(db)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root',type=Path,required=True)
    parser.add_argument('--port',type=int,default=9147)
    parser.add_argument('--prepare-only',action='store_true')
    args = parser.parse_args()
    with EmailFixture(args.root,args.port) as fixture:
        print(json.dumps({'synthetic':True,'root':str(fixture.root),'port':args.port,'ready':True}),flush=True)
        if not args.prepare_only:
            import uvicorn
            uvicorn.run(fixture.app,host='127.0.0.1',port=args.port,proxy_headers=False,access_log=False,
                        ssl_keyfile=str(fixture.root/'qa-key.pem'),ssl_certfile=str(fixture.root/'qa-cert.pem'))
