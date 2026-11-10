import base64
import unittest
from email.message import EmailMessage
from email import policy


def gmail_item(mid='abc',sender='author@example.test',text='Original body'):
    headers=[{'name':k,'value':v} for k,v in {'From':sender,'To':'owner@example.test',
        'Reply-To':'reply@example.test','Subject':'Exact original','Message-ID':'<'+mid+'@example.test>',
        'References':'<ancestor@example.test>'}.items()]
    return dict(id=mid,threadId='deadbeef',labelIds=['INBOX'],payload=dict(headers=headers,mimeType='text/plain',body={'data':base64.urlsafe_b64encode(text.encode()).decode()}))

class Result:
    def __init__(self,value):self.value=value
    def execute(self,**kwargs):return self.value
class FakeGmail:
    def __init__(self):
        self.items={'abc':gmail_item(),'def':gmail_item('def'),'aaa':gmail_item('aaa','owner@example.test')}
        self.submissions=[]
    def users(self):return self
    def messages(self):return self
    def threads(self):return ThreadResource(self)
    def settings(self):return self
    def sendAs(self):return self
    def getProfile(self,**kwargs):return Result({'emailAddress':'owner@example.test'})
    def list(self,**kwargs):
        if 'q' in kwargs:return Result({'messages':[{'id':'fed'}]} if 'fed' in self.items else {})
        return Result({'sendAs':[{'sendAsEmail':'alias@example.test','verificationStatus':'accepted'}]})
    def get(self,id,**kwargs):return Result(self.items[id])
    def send(self,body,**kwargs):
        from email.parser import BytesParser
        self.submissions.append(body)
        m=BytesParser(policy=policy.default).parsebytes(base64.urlsafe_b64decode(body['raw']))
        item=gmail_item('fed',str(m['From']),m.get_body(preferencelist=('plain',)).get_content())
        item['labelIds']=['SENT']
        item['payload']['headers']=[{'name':k,'value':str(v)} for k,v in m.items()]
        self.items['fed']=item
        return Result({'id':'fed','threadId':'deadbeef'})
class ThreadResource:
    def __init__(self,g):self.g=g
    def get(self,**kwargs):return Result({'messages':[self.g.items[x] for x in ['abc','def','aaa']]})

class FakeIMAP:
    def __init__(self):
        self.validity='123';self.calls=[];self.saved={};self.box='INBOX'
        m=EmailMessage();m['From']='author@example.test';m['To']='owner@example.test';m['Subject']='Exact original';m['Message-ID']='<nantes1@example.test>';m['Reply-To']='reply@example.test';m.set_content('Original body')
        self.raw=m.as_bytes(policy=policy.SMTP)
    def login(self,*args):return 'OK',[]
    def logout(self):return 'BYE',[]
    def select(self,box,readonly=False):
        self.calls.append(('select',box,readonly));self.box=box.strip('"');return 'OK',[b'1']
    def response(self,key):return key,[self.validity.encode()]
    def list(self):return 'OK',[b'(\\HasNoChildren \\Sent) "/" "Sent"']
    def uid(self,command,*args):
        self.calls.append((command,args))
        if command=='search':return 'OK',[b'9' if self.box=='Sent' and self.saved else b'']
        uid=str(args[0])
        raw=self.saved.get(uid) if self.box=='Sent' else self.raw
        return 'OK',[(b'1 (BODY[] {10}',raw),b')']
    def append(self,box,flags,date,raw):self.saved['9']=raw;return 'OK',[b'[APPENDUID 123 9]']
class FakeSMTP:
    def __init__(self,imap):self.imap=imap;self.calls=[]
    def ehlo(self):pass
    def starttls(self,**kwargs):self.calls.append('starttls')
    def login(self,*args):self.calls.append('login')
    def sendmail(self,sender,recipients,raw):self.calls.append((sender,recipients,raw));return {}
    def quit(self):pass

class ProviderCase(unittest.TestCase):
    def test_nantes_exact_uidvalidity_peek_smtp_and_readback(self):
        from email_providers import PrivateProviders,build_reply
        imap=FakeIMAP();smtp=FakeSMTP(imap)
        config={'email':'owner@example.test','imap':{'server':'imaps://imap.example.test:993','sasl':{'plain':{'username':'owner','password':{'fixture':'private'}}}},
                'smtp':{'server':'smtp://smtp.example.test:587','starttls':True,'sasl':{'plain':{'username':'owner','password':{'fixture':'private'}}}}}
        # Fixture passwords never enter production config; injected loader owns auth.
        provider=PrivateProviders(nantes_config=lambda:config,imap_factory=lambda config:imap,smtp_factory=lambda config:smtp)
        ref='nantes-imap://INBOX;UIDVALIDITY=123;UID=7;Message-ID=<nantes1@example.test>'
        m=provider.read(ref)
        self.assertEqual(m['message_id'],'<nantes1@example.test>')
        self.assertTrue(all(call[2] for call in imap.calls if call[0]=='select'))
        self.assertTrue(any(call[0]=='fetch' and 'BODY.PEEK[]' in str(call) for call in imap.calls))
        imap.validity='124'
        with self.assertRaises(ValueError):provider.read(ref)
        imap.validity='123'
        with self.assertRaises(ValueError):provider.read(ref.replace('nantes1','wrong'))
        d={'from':'owner@example.test','to':['reply@example.test'],'cc':[],'subject':m['subject'],'body':'Actual reply'}
        a=dict(m,outgoing_message_id='<smtp-one@example.test>',expected_body=d['body'],expected_to=d['to'],expected_cc=[])
        raw=build_reply(d,m,a['outgoing_message_id'])
        provider.send(a,raw)
        self.assertEqual(len([x for x in smtp.calls if isinstance(x,tuple)]),1)
        self.assertEqual(provider.reconcile(a)['message_id'],'<smtp-one@example.test>')

    def test_gmail_html_context_strips_active_content_and_large_sent_readback(self):
        from email_providers import PrivateProviders,build_reply
        g=FakeGmail();item=g.items['abc']
        item['payload']['mimeType']='text/html'
        item['payload']['body']['data']=base64.urlsafe_b64encode(b'<p>Visible context</p><script>untrusted()</script>').decode()
        provider=PrivateProviders(gmail_factory=lambda:g)
        m=provider.read('gmail:owner@example.test:message:abc')
        self.assertIn('Visible context',m['body'])
        self.assertNotIn('untrusted()',m['body'])
        d={'from':m['account'],'to':['reply@example.test'],'cc':[],'subject':m['subject'],'body':'x'*50000}
        a=dict(m,outgoing_message_id='<large@example.test>',expected_body=d['body'],expected_to=d['to'],expected_cc=[])
        receipt=provider.send(a,build_reply(d,m,a['outgoing_message_id']))
        self.assertIsNotNone(provider.reconcile(dict(a,transport_receipt=receipt)))

    def test_reply_references_falls_back_to_original_in_reply_to(self):
        from email_providers import build_reply
        from email.parser import BytesParser
        m=dict(message_id='<parent@example.test>',references='',in_reply_to='<ancestor@example.test>')
        d={'from':'owner@example.test','to':['reply@example.test'],'cc':[],'subject':'Original','body':'reply'}
        raw=build_reply(d,m,'<new@example.test>')
        parsed=BytesParser(policy=policy.default).parsebytes(raw)
        self.assertEqual(str(parsed['References']),'<ancestor@example.test> <parent@example.test>')

    def test_gmail_encoded_original_subject_is_decoded_without_losing_thread(self):
        from email_providers import PrivateProviders,build_reply
        g=FakeGmail()
        for h in g.items['abc']['payload']['headers']:
            if h['name']=='Subject':h['value']='=?utf-8?q?R=C3=A9union?='
        p=PrivateProviders(gmail_factory=lambda:g)
        m=p.read('gmail:owner@example.test:message:abc')
        self.assertEqual(m['subject'],'Réunion')
        d={'from':m['account'],'to':['reply@example.test'],'cc':[],'subject':m['subject'],'body':'Merci'}
        a=dict(m,outgoing_message_id='<unicode@example.test>',expected_body=d['body'],expected_to=d['to'],expected_cc=[])
        receipt=p.send(a,build_reply(d,m,a['outgoing_message_id']))
        self.assertIsNotNone(p.reconcile(dict(a,transport_receipt=receipt)))

    def test_gmail_exact_account_thread_expansion_send_readback(self):
        from email_providers import PrivateProviders,build_reply
        gmail=FakeGmail()
        provider=PrivateProviders(gmail_factory=lambda:gmail)
        expanded=provider.resolve('gmail:owner@example.test:thread:deadbeef')
        self.assertEqual([m['reference'] for m in expanded],['gmail:owner@example.test:message:abc','gmail:owner@example.test:message:def'])
        exact=provider.read('gmail:owner@example.test:message:abc')
        self.assertEqual(exact['message_id'],'<abc@example.test>')
        with self.assertRaises(ValueError):provider.read('gmail:other@example.test:message:abc')
        d={'from':'owner@example.test','to':['reply@example.test'],'cc':[],'subject':'Exact original','body':'Actual reply'}
        outgoing='<one-use@example.test>'
        raw=build_reply(d,exact,outgoing)
        attempt=dict(exact,outgoing_message_id=outgoing,expected_body=d['body'],expected_to=d['to'],expected_cc=[])
        receipt=provider.send(attempt,raw)
        self.assertEqual(gmail.submissions[0]['threadId'],'deadbeef')
        self.assertEqual(provider.reconcile(dict(attempt,transport_receipt=receipt))['id'],'fed')
        self.assertIsNone(provider.reconcile(dict(attempt,expected_body='WRONG')))
