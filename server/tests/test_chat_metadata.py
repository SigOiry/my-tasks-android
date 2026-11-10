import tempfile
import unittest
from pathlib import Path
from types import SimpleNamespace as NS
from unittest.mock import patch


class CitationTests(unittest.TestCase):
    def test_public_receipt_preserves_original_and_balanced_url(self):
        from public_web import normal_answer, request_urls
        url = 'https://en.wikipedia.org/wiki/Function_(mathematics)'
        prose = '  See [function]('+url+'). citeturn0search0\n'
        provider = lambda **kw: NS(choices=[NS(message=NS(content=prose, tool_calls=[]))])
        with patch('public_web.read_public', return_value={'url':url,'text':'Definition'}):
            result = normal_answer([{'role':'user','content':'Explain '+url}], provider, current_request='Explain '+url)
        self.assertEqual(request_urls('Read ('+url+').'), [url])
        self.assertEqual(result.original_content, prose)
        self.assertEqual(result.citation_metadata['sources'][0]['url'], url)
        self.assertEqual(result.citation_metadata['sources'][0]['verification'], 'page_read')
        self.assertNotIn('references', result.citation_metadata)

    def test_atomic_metadata_history_status_reopen(self):
        from conversations import ConversationStore, NewConversation, SendMessage
        from citations import CitedText
        import uuid
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as tmp:
            store = ConversationStore(Path(tmp)/'chat.sqlite')
            store.start(lambda _: {}, title_generator=False)
            try:
                thread = store.create(NewConversation(task_id=None))
                rid = str(uuid.uuid4())
                with patch('conversations.model_text', return_value=CitedText('exact\n', {'version':1,'sources':[]})):
                    accepted = store.submit(thread['id'], SendMessage(request_id=rid,content='Hi',mode='question',expected_revision=1))
                    store.pool.shutdown(wait=True)
                status = store.request(thread['id'],rid)['assistant_message']
                self.assertEqual(status['content'], 'exact\n')
                self.assertEqual(status['citation_metadata'], {'version':1,'sources':[]})
                self.assertEqual(store.get(thread['id'])['messages'][-1], status)
                self.assertEqual(ConversationStore(store.path).request(thread['id'],rid)['assistant_message'],status)
                self.assertEqual(store.submit(thread['id'],SendMessage(request_id=rid,content='Hi',mode='question',expected_revision=1))['request']['assistant_message'],status)
            finally:
                store.close()

    def test_private_guard_preserves_exact_model_prose(self):
        from public_web import normal_answer
        prose = '  Private answer. citeturn0search0\n'
        provider = lambda **kw: NS(choices=[NS(message=NS(content=prose,tool_calls=[]))])
        result = normal_answer([{'role':'user','content':'Search my private email'}],provider,current_request='Search my private email')
        self.assertEqual(result.original_content,prose)
        self.assertEqual(result.citation_metadata['sources'],[])
        self.assertTrue(result.citation_metadata['warnings'])

    def test_failed_metadata_serialization_rolls_back_reply_and_status(self):
        from conversations import ConversationStore,NewConversation,now
        from citations import CitedText
        import uuid
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as tmp:
            store = ConversationStore(Path(tmp)/'chat.sqlite')
            store.start(lambda _: {},title_generator=False)
            try:
                t = store.create(NewConversation(task_id=None))
                rid = str(uuid.uuid4())
                with store.connect() as con:
                    con.execute('INSERT INTO requests(id,conversation_id,content,mode,status,owner,created_at) VALUES(?,?,?,?,?,?,?)',(rid,t['id'],'Question','question','running',store.owner,now()))
                with self.assertRaises(TypeError):
                    store._finish(t['id'],rid,text=CitedText('Original',{'version':1,'sources':set()}))
                result = store.request(t['id'],rid)
                self.assertEqual(result['status'],'running')
                self.assertIsNone(result['assistant_message'])
                self.assertEqual(store.get(t['id'])['conversation']['revision'],1)
            finally:
                store.close()

    def test_unmapped_provider_markers_and_model_urls_never_become_sources(self):
        from public_web import normal_answer
        prose = 'See https://fabricated.example/path citeturn99search9'
        provider = lambda **kw: NS(choices=[NS(message=NS(content=prose,tool_calls=[]))])
        result = normal_answer([{'role':'user','content':'Write a poem'}],provider,current_request='Write a poem')
        self.assertEqual(result.original_content,prose)
        self.assertEqual(result.citation_metadata,{'version':1,'sources':[]})

    def test_public_probe_uses_receipts_not_rewritten_prose(self):
        import json
        import public_web_smoke
        url = 'https://www.python.org/downloads/'
        requested = [False]
        def model(**kwargs):
            calls = []
            text = 'ALPHA summary' if 'Summarize the following' in str(kwargs['messages']) else 'Answer without invented links.'
            if kwargs['tools'] and not requested[0]:
                requested[0] = True
                calls = [NS(id='page',function=NS(name='public_page',arguments=json.dumps({'url':url})))]
            return NS(choices=[NS(message=NS(content=text,tool_calls=calls))])
        with patch('public_web_smoke.call_llm',side_effect=model), patch('public_web_smoke.search_public',return_value=[{'url':url,'title':'Python','description':'Public'}]), patch('public_web_smoke.read_public',return_value={'url':url,'text':'Release evidence'}):
            result = public_web_smoke.smoke()
        self.assertEqual(result['cases'][0]['source_urls'],[url])
        self.assertTrue(result['cases'][2]['citation_metadata']['warnings'])
