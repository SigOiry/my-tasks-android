"""Private exact-message email boundary. No provider identifiers from clients."""
import hashlib
import re
from email.utils import getaddresses


def source_id(task_id, reference):
    return 'src_' + hashlib.sha256((task_id+'\0'+reference).encode('utf-8')).hexdigest()[:32]


def recipients(message, reply_all=False):
    if message.get('outgoing'):return [],[]
    own={x.casefold() for x in message['identities']}
    seen=set(own)
    def add(values):
        result=[]
        for _,address in getaddresses(values):
            key=address.casefold()
            if not address or key in seen: continue
            if not re.fullmatch(r'[^\s<>@,;]+@[^\s<>@,;]+',address):
                raise ValueError('Invalid recipient')
            seen.add(key)
            result.append(address)
        return result
    to=add([message.get('reply_to') or message['sender']])
    if reply_all:to.extend(add([message.get('to','')]))
    cc=add([message.get('cc','')]) if reply_all else []
    return to,cc


def build_reply(draft, message, outgoing_message_id):
    from email.message import EmailMessage
    from email.policy import SMTP
    from email.utils import formatdate
    out=EmailMessage(policy=SMTP)
    for key,value in [('From',draft['from']),('To',', '.join(draft['to'])),('Subject',draft['subject']),
                      ('Message-ID',outgoing_message_id),('In-Reply-To',message['message_id'])]:
        out[key]=value
    if draft['cc']:out['Cc']=', '.join(draft['cc'])
    refs=re.findall(r'<[^<>\r\n]+>',message.get('references',''))
    if not refs:
        fallback=re.findall(r'<[^<>\r\n]+>',message.get('in_reply_to',''))
        if len(fallback)==1:refs=fallback
    if message['message_id'] not in refs:refs.append(message['message_id'])
    out['References']=' '.join(refs)
    out['Date']=formatdate(localtime=False)
    out.set_content(draft['body'])
    return out.as_bytes()


def _google_service():
    import importlib.util
    import os
    import sys
    from pathlib import Path
    home=Path(os.environ.get('HERMES_HOME','/opt/data'))
    script=home/'skills/productivity/google-workspace/scripts/google_api.py'
    if not script.is_file():raise ValueError('Private Gmail integration unavailable')
    directory=str(script.parent)
    if directory not in sys.path:sys.path.insert(0,directory)
    spec=importlib.util.spec_from_file_location('_email_private_google',script)
    module=importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    service=module.build_service('gmail','v1')
    if hasattr(service,'_http'):service._http.timeout=30
    return service


def _gmail_reference(reference):
    match=re.fullmatch(r'gmail:([^:\s]+@[^:\s]+):(message|thread):([a-fA-F0-9]{1,128})',reference)
    if not match:raise ValueError('Unsupported Gmail reference')
    return match.groups()


def _html_text(value):
    from html.parser import HTMLParser
    class Text(HTMLParser):
        def __init__(self):super().__init__(convert_charrefs=True);self.parts=[];self.skip=0
        def handle_starttag(self,tag,attrs):
            if tag in ('script','style','template'):self.skip+=1
            if not self.skip and tag in ('p','div','br','li','tr'):self.parts.append('\n')
        def handle_endtag(self,tag):
            if tag in ('script','style','template') and self.skip:self.skip-=1
        def handle_data(self,data):
            if not self.skip:self.parts.append(data)
    parser=Text();parser.feed(value[:1000000]);return ''.join(parser.parts).strip()[:60001]


def _decode_gmail(item):
    import base64
    payload=item.get('payload',{})
    headers={h['name'].lower():h['value'] for h in payload.get('headers',[])}
    from email.header import decode_header,make_header
    for name in ('subject','from','reply-to','to','cc'):
        if name in headers:
            value=re.sub(r'\r?\n[ \t]+',' ',headers[name])
            if '\r' in value or '\n' in value:raise ValueError('Malformed email header')
            headers[name]=str(make_header(decode_header(value)))
    def body(part):
        if part.get('mimeType') in ('text/plain','text/html') and part.get('body',{}).get('data'):
            value=part['body']['data']
            from email.message import Message
            content=Message()
            content['Content-Type']=next((h['value'] for h in part.get('headers',[]) if h['name'].lower()=='content-type'),'text/plain; charset=utf-8')
            charset=content.get_content_charset() or 'utf-8'
            data=base64.urlsafe_b64decode(value+'='*((-len(value))%4))
            try:text=data.decode(charset,'replace')
            except LookupError:text=data.decode('utf-8','replace')
            return (_html_text(text) if part.get('mimeType')=='text/html' else text)[:60001]
        parts=part.get('parts',[])[:30]
        if part.get('mimeType')=='multipart/alternative':
            plain=next((p for p in parts if p.get('mimeType')=='text/plain'),None)
            if plain:return body(plain)
        return '\n'.join(filter(None,(body(p) for p in parts if not p.get('filename'))))[:60001]
    return dict(sender=headers.get('from',''),reply_to=headers.get('reply-to',''),to=headers.get('to',''),cc=headers.get('cc',''),
                subject=headers.get('subject',''),date=headers.get('date',''),body=body(payload),
                message_id=headers.get('message-id',''),references=headers.get('references',''),in_reply_to=headers.get('in-reply-to',''),
                thread_id=item.get('threadId'),provider_message_id=item.get('id'),labels=item.get('labelIds',[]))


def _public_thread(message):
    return {k:message.get(k,'') for k in ('sender','subject','date','body')}


def _matches_sent(message,attempt):
    def addresses(value):return sorted(a.casefold() for _,a in getaddresses([value]) if a)
    normalize=lambda s:s.replace('\r\n','\n').rstrip('\n')
    return (message.get('message_id')==attempt['outgoing_message_id'] and
            addresses(message.get('sender',''))==[attempt['account'].casefold()] and
            addresses(message.get('to',''))==sorted(x.casefold() for x in attempt['expected_to']) and
            addresses(message.get('cc',''))==sorted(x.casefold() for x in attempt['expected_cc']) and
            message.get('subject')==attempt['subject'] and
            normalize(message.get('body',''))==normalize(attempt['expected_body']))


class PrivateProviders:
    """Fixed server-owned account adapters; injectable network transports for QA."""
    def __init__(self,gmail_factory=None,nantes_config=None,imap_factory=None,smtp_factory=None):
        self.gmail_factory=gmail_factory or _google_service
        self.nantes_config=nantes_config
        self.imap_factory=imap_factory
        self.smtp_factory=smtp_factory
    def config(self):
        if self.nantes_config:return self.nantes_config()
        import os
        import tomllib
        from pathlib import Path
        path=Path(os.environ.get('HIMALAYA_CONFIG','/opt/data/home/.config/himalaya/config.toml'))
        if not path.is_file():path=Path.home()/'.config/himalaya/config.toml'
        return tomllib.loads(path.read_text())['accounts']['univ-nantes']
    def password(self,section):
        # The installed private integration uses `cat PRIVATE_FILE`. Do not execute
        # arbitrary shell syntax or expose credentials to an LLM or subprocess.
        import shlex
        from pathlib import Path
        args=shlex.split(section['sasl']['plain']['password']['command'])
        if len(args)!=2 or args[0] not in ('cat','/bin/cat','/usr/bin/cat'):
            raise ValueError('Unsupported private password command')
        path=Path(args[1]).expanduser()
        if not path.is_absolute() or path.is_symlink() or path.stat().st_mode & 0o077:
            raise ValueError('Private credential file required')
        return path.read_text().strip()
    def imap(self,config):
        if self.imap_factory:return self.imap_factory(config)
        import imaplib
        import ssl
        from urllib.parse import urlsplit
        section=config['imap'];host=urlsplit(section['server'])
        if host.scheme!='imaps' or not host.hostname:raise ValueError('TLS IMAP required')
        conn=imaplib.IMAP4_SSL(host.hostname,host.port or 993,ssl_context=ssl.create_default_context(),timeout=30)
        try:conn.login(section['sasl']['plain']['username'],self.password(section))
        except BaseException:
            conn.logout()
            raise
        return conn
    def smtp(self,config):
        if self.smtp_factory:return self.smtp_factory(config)
        import smtplib
        import ssl
        from urllib.parse import urlsplit
        section=config['smtp'];host=urlsplit(section['server'])
        if host.scheme not in ('smtp','smtps') or not host.hostname:raise ValueError('Unsupported SMTP transport')
        if host.scheme=='smtps':
            conn=smtplib.SMTP_SSL(host.hostname,host.port or 465,timeout=30,context=ssl.create_default_context())
        else:
            if section.get('starttls') is not True:raise ValueError('TLS SMTP required')
            conn=smtplib.SMTP(host.hostname,host.port or 587,timeout=30)
        try:
            conn.ehlo()
            if host.scheme=='smtp':conn.starttls(context=ssl.create_default_context());conn.ehlo()
            conn.login(section['sasl']['plain']['username'],self.password(section))
        except BaseException:
            conn.close()
            raise
        return conn
    def select(self,conn,box):
        if not box or len(box)>512 or any(c in box for c in '\r\n\0'):raise ValueError('Invalid mailbox')
        quoted='"'+box.replace('\\','\\\\').replace('"','\\"')+'"'
        if conn.select(quoted,readonly=True)[0]!='OK':raise ValueError('Mailbox unavailable')
    def fetch(self,conn,uid):
        from email import policy
        from email.parser import BytesParser
        status,data=conn.uid('fetch',str(uid),'(BODY.PEEK[])')
        raw=next((x[1] for x in data if isinstance(x,tuple) and isinstance(x[1],bytes)),None)
        if status!='OK' or raw is None or len(raw)>4000000:raise ValueError('Message unavailable or too large')
        parsed=BytesParser(policy=policy.default).parsebytes(raw)
        body=parsed.get_body(preferencelist=('plain','html'))
        text=body.get_content() if body is not None else ''
        if body is not None and body.get_content_type()=='text/html':text=_html_text(text)
        return dict(sender=str(parsed.get('From','')),reply_to=str(parsed.get('Reply-To','')),to=str(parsed.get('To','')),
                    cc=str(parsed.get('Cc','')),subject=str(parsed.get('Subject','')),date=str(parsed.get('Date','')),
                    body=text[:60001],message_id=str(parsed.get('Message-ID','')),references=str(parsed.get('References','')),
                    in_reply_to=str(parsed.get('In-Reply-To','')))
    def search_header(self,conn,header,value):
        if not re.fullmatch(r'<[^<>\r\n"\\]+>',value):return []
        status,data=conn.uid('search',None,'HEADER',header,'"'+value+'"')
        if status!='OK':raise ValueError('Thread lookup unavailable')
        return data[0].split() if data and data[0] else []
    def sent_box(self,conn,config):
        status,rows=conn.list()
        if status!='OK':raise ValueError('Sent mailbox unavailable')
        boxes=[]
        for row in rows:
            if not isinstance(row,bytes):continue
            text=row.decode('utf-8','replace')
            match=re.search(r'\)\s+(?:"(?:[^"\\]|\\.)*"|NIL)\s+(?:"((?:[^"\\]|\\.)*)"|(.+))$',text)
            if not match:continue
            box=(match.group(1) if match.group(1) is not None else match.group(2)).replace('\\"','"').replace('\\\\','\\')
            boxes.append(box)
            if '\\sent' in text.split(')')[0].lower():return box
        alias=(config.get('mailbox',{}).get('alias',{}).get('sent'))
        if alias and alias in boxes:return alias
        # Resolve only an actually advertised standard name, never an invented path.
        candidates=[b for b in boxes if b.casefold() in ('sent','sent items','sent messages','inbox.sent')]
        if len(candidates)==1:return candidates[0]
        raise ValueError('Unambiguous Sent mailbox unavailable')
    def nantes_read(self,reference):
        match=re.fullmatch(r'nantes-imap://(.+);UIDVALIDITY=([1-9][0-9]*);UID=([1-9][0-9]*);Message-ID=(<[^<>\r\n]+>)',reference)
        if not match:raise ValueError('Exact Nantes UIDVALIDITY/UID/Message-ID required')
        box,validity,uid,mid=match.groups()
        config=self.config();conn=self.imap(config)
        try:
            self.select(conn,box)
            value=conn.response('UIDVALIDITY')[1][0]
            if value is None or value.decode()!=validity:raise ValueError('Mailbox UIDVALIDITY changed')
            message=self.fetch(conn,uid)
            if message['message_id']!=mid:raise ValueError('UID Message-ID mismatch')
            thread=[_public_thread(message)];members=[box+':'+uid]
            # Only RFC-linked messages, in the exact source mailbox and verified
            # Sent mailbox. Never infer a thread from subject or newest timestamp.
            ancestors=re.findall(r'<[^<>\r\n]+>',message['references'])[-10:]
            sent=self.sent_box(conn,config)
            for mailbox in dict.fromkeys([box,sent]):
                self.select(conn,mailbox)
                related=set(self.search_header(conn,'In-Reply-To',mid)+self.search_header(conn,'References',mid))
                for ancestor in ancestors:related.update(self.search_header(conn,'Message-ID',ancestor))
                if len(related)>30:raise ValueError('Related email context exceeds bound')
                for related_uid in sorted(related,key=int):
                    key=mailbox+':'+related_uid.decode()
                    if key in members:continue
                    item=self.fetch(conn,related_uid.decode())
                    # IMAP HEADER searches may be substring matches: revalidate exact chain.
                    refs=re.findall(r'<[^<>\r\n]+>',item.get('references','')+' '+item.get('in_reply_to',''))
                    if mid not in refs and item['message_id'] not in ancestors:continue
                    members.append(key);thread.append(_public_thread(item))
            message.update(reference=reference,provider='nantes',account=config['email'],identities=[config['email'].casefold()],
                           mailbox=box,uidvalidity=validity,uid=uid,thread=thread[-30:],thread_members=members,sent_mailbox=sent)
            if any(a.casefold()==config['email'].casefold() for _,a in getaddresses([message['sender']])):message['outgoing']=True
            return message
        finally:conn.logout()
    def nantes_send(self,message,raw):
        config=self.config()
        if config['email'].casefold()!=message['account'].casefold():raise ValueError('Nantes account changed')
        conn=self.imap(config)
        try:
            sent=self.sent_box(conn,config)
            if sent!=message['sent_mailbox']:raise ValueError('Sent mailbox changed')
            smtp=self.smtp(config)
            try:
                refused=smtp.sendmail(message['account'],message['expected_to']+message['expected_cc'],raw)
                if refused:raise ValueError('Some recipients refused; partial delivery uncertain')
            finally:smtp.quit()
            # This is part of the explicit confirmed send only. An APPEND failure
            # after SMTP acceptance is uncertain and is NEVER retried as a send.
            status,_=conn.append(sent,'(\\Seen)',None,raw)
            if status!='OK':raise ValueError('Sent copy unavailable after delivery')
            return dict(message_id=message['outgoing_message_id'])
        finally:conn.logout()
    def nantes_reconcile(self,message):
        config=self.config()
        if config['email'].casefold()!=message['account'].casefold():raise ValueError('Nantes account changed')
        conn=self.imap(config)
        try:
            sent=self.sent_box(conn,config)
            self.select(conn,sent)
            uids=self.search_header(conn,'Message-ID',message['outgoing_message_id'])
            if len(uids)>10:return None
            for uid in uids:
                item=self.fetch(conn,uid.decode())
                if _matches_sent(item,message):
                    return dict(provider='nantes',id=uid.decode(),message_id=message['outgoing_message_id'],verified=True)
            return None
        finally:conn.logout()
    def gmail(self,account):
        g=self.gmail_factory()
        profile=g.users().getProfile(userId='me').execute(num_retries=0)
        if profile['emailAddress'].casefold()!=account.casefold():raise ValueError('Gmail reference account mismatch')
        identities={profile['emailAddress'].casefold()}
        aliases=g.users().settings().sendAs().list(userId='me').execute(num_retries=0)
        for alias in aliases.get('sendAs',[]):
            if alias.get('verificationStatus')=='accepted' or alias.get('isPrimary'):
                identities.add(alias['sendAsEmail'].casefold())
        return g,sorted(identities)
    def resolve(self, reference):
        if reference.startswith('nantes-imap://'):return [self.nantes_read(reference)]
        account,kind,mid=_gmail_reference(reference)
        if kind=='message':return [self.read(reference)]
        g,identities=self.gmail(account)
        thread=g.users().threads().get(userId='me',id=mid,format='full').execute(num_retries=0)
        items=thread.get('messages',[])
        if len(items)>50:raise ValueError('Thread has too many candidates; exact message required')
        result=[]
        for item in items:
            decoded=_decode_gmail(item)
            if any(a.casefold() in identities for _,a in getaddresses([decoded['sender']])):continue
            exact='gmail:'+account+':message:'+item['id']
            result.append(self.gmail_message(exact,account,identities,item,thread))
        return result
    def gmail_message(self,reference,account,identities,item,thread):
        message=_decode_gmail(item)
        if not re.fullmatch(r'<[^<>\r\n]+>',message['message_id']):raise ValueError('Missing exact RFC Message-ID')
        message.update(reference=reference,provider='gmail',account=account,identities=identities,
                       thread=[_public_thread(_decode_gmail(m)) for m in thread.get('messages',[])[-30:]],
                       thread_members=[m['id'] for m in thread.get('messages',[])])
        if any(a.casefold() in identities for _,a in getaddresses([message['sender']])):message['outgoing']=True
        return message
    def read(self, reference):
        if reference.startswith('nantes-imap://'):return self.nantes_read(reference)
        account,kind,mid=_gmail_reference(reference)
        if kind!='message':raise ValueError('Select an exact message, not a thread')
        g,identities=self.gmail(account)
        item=g.users().messages().get(userId='me',id=mid,format='full').execute(num_retries=0)
        thread=g.users().threads().get(userId='me',id=item['threadId'],format='full').execute(num_retries=0)
        return self.gmail_message(reference,account,identities,item,thread)
    def send(self, message, raw):
        if message['provider']=='nantes':return self.nantes_send(message,raw)
        g,_=self.gmail(message['account'])
        import base64
        return g.users().messages().send(userId='me',body=dict(raw=base64.urlsafe_b64encode(raw).decode('ascii'),threadId=message['thread_id'])).execute(num_retries=0)
    def reconcile(self,message):
        if message['provider']=='nantes':return self.nantes_reconcile(message)
        g,_=self.gmail(message['account'])
        receipt=message.get('transport_receipt') or {}
        ids=[receipt['id']] if receipt.get('id') else []
        if not ids:
            found=g.users().messages().list(userId='me',q='in:sent rfc822msgid:'+message['outgoing_message_id'],maxResults=10).execute(num_retries=0)
            ids=[m['id'] for m in found.get('messages',[])]
        for mid in ids:
            item=g.users().messages().get(userId='me',id=mid,format='full').execute(num_retries=0)
            decoded=_decode_gmail(item)
            if 'SENT' in item.get('labelIds',[]) and item.get('threadId')==message['thread_id'] and _matches_sent(decoded,message):
                return dict(provider='gmail',id=item['id'],thread_id=item['threadId'],message_id=message['outgoing_message_id'],verified=True)
        return None
