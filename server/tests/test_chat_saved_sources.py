import unittest

class SavedSourcesTests(unittest.TestCase):
    def test_saved_sources_only_exact_context_no_model_links(self):
        from citations import saved_metadata
        import hashlib
        ref = 'gmail:owner@example.test:message:abc'
        task = {'id':'task-1','title':'Review budget','source_references':[ref, ref, 'https://evil.example/fake', 'gmail:bad'], 'notes':'model prose https://evil.example'}
        result = saved_metadata(task)
        self.assertEqual([s['kind'] for s in result['sources']], ['task','saved_email'])
        email = result['sources'][1]
        self.assertEqual(email['resource_id'],'src_'+hashlib.sha256(('task-1\0'+ref).encode()).hexdigest()[:32])
        self.assertEqual(email['task_id'],'task-1')
        self.assertEqual(email['verification'],'saved_context')
        self.assertNotIn('url',email)
        self.assertNotIn(ref,str(result))

    def test_only_context_actually_supplied_has_saved_receipts(self):
        import tempfile, uuid
        from pathlib import Path
        from unittest.mock import patch
        from conversations import ConversationStore, NewConversation, SendMessage
        task = {'id':'task-1','title':'Budget', 'notes':'x'*20000,
                'source_references':['gmail:owner@example.test:message:abc']}
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as tmp:
            store = ConversationStore(Path(tmp)/'chat.sqlite')
            store.start(lambda _:task,title_generator=False)
            try:
                t = store.create(NewConversation(task_id='task-1'),task)
                p = SendMessage(request_id=str(uuid.uuid4()),content='Summarize this task',mode='question',expected_revision=1)
                seen = []
                def model(messages,**kwargs):
                    seen.extend(messages)
                    return 'Exact answer'
                with patch('conversations.model_text',side_effect=model):
                    store.submit(t['id'],p)
                    store.pool.shutdown(wait=True)
                reply = store.request(t['id'],p.request_id)['assistant_message']
                emails = [s for s in reply['citation_metadata']['sources'] if s['kind']=='saved_email']
                for email in emails:
                    self.assertIn(task['source_references'][0],str(seen))
                self.assertTrue(emails, 'Bounded saved context should preserve exact source receipts')
            finally:
                store.close()
