"""Private scratch SQLite/API/worker tests; only model IO is synthetic."""
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import time
import unittest
from unittest.mock import patch
import uuid


class DiscussionKnowledgeTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch')
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.canonical = self.root/'tasks.json'
        self.tasks = [{'id':'a','title':'Budget','source_references':['gmail:owner@example.test:message:aaa']},
                      {'id':'b','title':'Travel','source_references':['gmail:owner@example.test:message:bbb']}]
        self.save()
        scans = self.root/'scans'/'sync'/'run'
        scans.mkdir(parents=True)
        (scans/'manifest.json').write_text(json.dumps({'end':'2026-11-14','items':[
            {'source':'Gmail','content':{'messages':[
                {'source_reference':self.tasks[0]['source_references'][0],'body':'Reimbursement authorization QUARTZ42.'},
                {'source_reference':self.tasks[1]['source_references'][0],'body':'Travel approval BASALT19.'},
                {'source_reference':'gmail:owner@example.test:message:ccc','body':'Unlinked HIDDEN99.'}]}}]}))
        spec = importlib.util.spec_from_file_location('scratch_knowledge','/opt/data/personal-assistant/task_knowledge.py')
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        self.index = self.root/'knowledge'/'knowledge.sqlite'
        self.knowledge = module.Knowledge(self.canonical,scans.parent,self.index)
        self.knowledge.refresh()
        from task_knowledge_context import KnowledgeContext
        self.loader = KnowledgeContext(self.canonical,self.index)

    def save(self, dashboard=None):
        self.canonical.write_text(json.dumps({**getattr(self,'document',{}),'tasks':self.tasks,'dashboard':dashboard or {}}))

    def store(self):
        from conversations import ConversationStore, NewConversation
        store = ConversationStore(self.root/'conversations.sqlite')
        store.start(lambda tid:next(t for t in self.tasks if t['id']==tid),title_generator=False,knowledge_loader=self.loader)
        self.addCleanup(store.close)
        return store,store.create(NewConversation(task_id=None),None)

    def send(self,store,cid,content,revision=1):
        from conversations import SendMessage
        request = SendMessage(request_id=str(uuid.uuid4()),content=content,mode='question',expected_revision=revision)
        store.submit(cid,request)
        for _ in range(300):
            result = store.request(cid,request.request_id)
            if result['status'] not in ('queued','running'):
                return request,result
            time.sleep(.01)
        self.fail('worker did not finish')

    def response(self,text='QUARTZ42, from saved mail.'):
        return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content=text,tool_calls=[]))])

    def test_legacy_receipts_without_scope_still_fence_revoked_history(self):
        store,thread = self.store()
        with patch('hermes_chat.call_llm',return_value=self.response()):
            _,first = self.send(store,thread['id'],'Search saved mail reimbursement authorization')
        self.assertEqual(first['status'],'succeeded',first)
        # Migration fixture: original v1 receipts predate private_scope storage.
        with store.connect() as con:
            con.execute("UPDATE requests SET private_scope='[]' WHERE conversation_id=?",(thread['id'],))
        self.tasks[0]['source_references'] = []
        self.save()
        with patch('hermes_chat.call_llm',return_value=self.response()) as model:
            revision = store.get(thread['id'])['conversation']['revision']
            _,result = self.send(store,thread['id'],'Explain that',revision)
        self.assertEqual(model.call_count,0)
        self.assertEqual(result['status'],'failed',result)

    def test_title_generated_during_revocation_is_not_persisted(self):
        store,thread = self.store()
        def title(context):
            self.tasks[0]['source_references'] = []
            self.save()
            return 'REVOKED_TITLE'
        store.title_generator = title
        with patch('hermes_chat.call_llm',return_value=self.response()):
            _,result = self.send(store,thread['id'],'Search saved mail reimbursement authorization')
        self.assertEqual(result['status'],'succeeded',result)
        store.title_pool.shutdown(wait=True)
        self.assertNotEqual(store.get(thread['id'])['conversation']['title'],'REVOKED_TITLE')

    def test_queued_title_cannot_send_revoked_private_history_to_provider(self):
        from threading import Event
        store,thread = self.store()
        gate = Event()
        self.addCleanup(gate.set)
        store.title_pool.submit(lambda:gate.wait(5))
        seen = []
        store.title_generator = lambda context:seen.append(context) or 'MUST_NOT_PERSIST_TITLE'
        with patch('hermes_chat.call_llm',return_value=self.response()):
            _,result = self.send(store,thread['id'],'Search saved mail reimbursement authorization')
        self.assertEqual(result['status'],'succeeded',result)
        self.tasks[0]['source_references'] = []
        self.save()
        gate.set()
        store.title_pool.shutdown(wait=True)
        self.assertEqual(seen,[],'Title provider must obey the same saved-evidence fence')
        self.assertNotEqual(store.get(thread['id'])['conversation']['title'],'MUST_NOT_PERSIST_TITLE')

    def test_global_search_supplies_current_bounded_task_fields_with_receipts(self):
        self.tasks[0]['due_date'] = '2026-12-20'
        self.tasks[0]['status'] = 'waiting'
        self.tasks[0]['notes'] = 'Must not override dashboard notes'
        self.save()
        self.knowledge.refresh()
        store,thread = self.store()
        def provider(**kwargs):
            self.assertIn('2026-12-20',str(kwargs['messages']))
            self.assertIn('waiting',str(kwargs['messages']))
            self.assertEqual(kwargs['tools'],[])
            self.assertLessEqual(sum(len(m['content']) for m in kwargs['messages']),44000)
            return self.response('Budget is waiting until December 20.')
        with patch('hermes_chat.call_llm',side_effect=provider):
            _,result = self.send(store,thread['id'],'Find saved task Budget')
        self.assertEqual(result['status'],'succeeded',result)
        self.assertTrue(any(s['kind']=='task' and s['verification']=='saved_context' and s['task_id']=='a' for s in result['assistant_message']['citation_metadata']['sources']))

    def test_global_query_is_read_only_bounded_and_uses_current_exact_authority(self):
        self.tasks += [{'id':'old'+str(n),'title':'QUARTZ42'} for n in range(80)]
        self.save()
        self.knowledge.refresh()
        before = self.index.read_bytes()
        # Removed IDs and stale canonical text cannot crowd out current matches.
        self.tasks = self.tasks[:2]
        self.tasks[0]['title'] = 'Changed canonical title'
        self.save()
        rows = self.loader(None,'QUARTZ42 OR " * NEAR ( authorization )')
        self.assertTrue(rows)
        self.assertEqual({r['task_id'] for r in rows},{'a'})
        self.assertTrue(all(r['source_path']!='tasks.json' for r in rows))
        self.assertLessEqual(len(rows),4)
        self.assertLessEqual(len(json.dumps(rows,ensure_ascii=False,sort_keys=True)),5000)
        self.assertEqual(self.loader('b','QUARTZ42')[0]['task_id'],'b','Task-linked recent fallback stays isolated')
        self.assertEqual(self.loader(None,'HIDDEN99'),[],'No sibling/unlinked mail or global recent fallback')
        self.tasks[0]['source_references'] = ['gmail:owner@example.test:thread:aaa']
        self.save()
        self.assertEqual(self.loader(None,'QUARTZ42'),[],'Thread membership does not authorize message evidence')
        self.assertEqual(self.index.read_bytes(),before)
        self.assertFalse(Path(str(self.index)+'-wal').exists())

    def test_global_result_and_model_input_budgets_hold_across_many_tasks(self):
        self.tasks += [{'id':'many'+str(n),'title':'authorization '+('x'*2400)} for n in range(30)]
        self.save()
        self.knowledge.refresh()
        rows = self.loader(None,'authorization')
        self.assertTrue(rows)
        self.assertLessEqual(len(rows),4)
        self.assertLessEqual(len(json.dumps(rows,ensure_ascii=False,sort_keys=True)),5000)
        store,thread = self.store()
        def provider(**kwargs):
            self.assertLessEqual(sum(len(m['content']) for m in kwargs['messages']),44000)
            self.assertEqual(kwargs['tools'],[])
            return self.response()
        with patch('hermes_chat.call_llm',side_effect=provider):
            _,result = self.send(store,thread['id'],'Search saved evidence authorization')
        self.assertEqual(result['status'],'succeeded',result)
        supplied_ids = {s['id'] for s in result['assistant_message']['citation_metadata']['sources'] if s['id'].startswith('knowledge_')}
        self.assertEqual(supplied_ids,{'knowledge_'+r['id'][:32] for r in self.loader(None,'Search saved evidence authorization')})

    def test_public_discussion_keeps_existing_bounded_research_path(self):
        store,thread = self.store()
        prompt = 'What is the current Python version?'
        url = 'https://www.python.org/'
        with patch('hermes_chat.call_llm',return_value=self.response('The [Python release](https://www.python.org/) is available.')), patch('public_web.search_public',return_value=[{'url':url,'title':'Python','description':'Public release'}]) as search, patch.object(store,'knowledge_loader',side_effect=AssertionError('Public request must not retrieve private data')):
            _,result = self.send(store,thread['id'],prompt)
        self.assertEqual(result['status'],'succeeded',result)
        self.assertEqual(search.call_args.args,(prompt,))
        self.assertEqual([s['kind'] for s in result['assistant_message']['citation_metadata']['sources']],['web'])

    def test_no_match_or_unavailable_index_remains_private_without_fabricated_receipts(self):
        store,thread = self.store()
        self.index.write_bytes(b'corrupt scratch index')
        with patch('hermes_chat.call_llm',return_value=self.response('No saved evidence is available.')), patch('public_web.normal_answer',side_effect=AssertionError('Unavailable private index must never fall through to web')):
            _,result = self.send(store,thread['id'],'Search saved mail MISSING_INFORMATION')
        self.assertEqual(result['status'],'succeeded',result)
        self.assertEqual(result['assistant_message']['citation_metadata']['sources'],[])
        self.assertEqual(self.index.read_bytes(),b'corrupt scratch index')

    def test_task_context_reference_revocation_is_fenced_even_without_index(self):
        from conversations import ConversationStore, NewConversation
        self.index.unlink()  # Scratch index only: canonical context is the fallback.
        store = ConversationStore(self.root/'conversations.sqlite')
        store.start(lambda tid:self.tasks[0],title_generator=False,knowledge_loader=self.loader)
        self.addCleanup(store.close)
        thread = store.create(NewConversation(task_id='a'),self.tasks[0])
        def provider(**kwargs):
            self.assertIn('gmail:owner@example.test:message:aaa',str(kwargs['messages']))
            self.tasks[0]['source_references'] = []
            self.save()
            return self.response('REVOKED_CONTEXT')
        with patch('hermes_chat.call_llm',side_effect=provider):
            _,result = self.send(store,thread['id'],'Explain the task')
        self.assertEqual(result['status'],'failed',result)
        self.assertIsNone(result['assistant_message'])

    def test_authenticated_free_api_can_explicitly_choose_private_search(self):
        from fastapi.testclient import TestClient
        from mobile_api import create_app, dashboard
        template = dashboard.taskdb.Store(self.root/'seed.json').add('Synthetic fixture')
        self.document = json.loads((self.root/'seed.json').read_text())
        self.tasks = [dict(template,**task) for task in self.tasks]
        self.save()
        self.knowledge.refresh()
        token = self.root/'token'
        token.write_text('s'*48)
        token.chmod(0o600)
        with patch('conversations.generate_title',False):
            app = create_app(db_path=self.canonical,token_file=token,metadata_path=self.root/'events.sqlite',
                             session_path=self.root/'sessions.sqlite',account_path=self.root/'account.sqlite',
                             conversation_path=self.root/'conversations.sqlite',email_path=self.root/'email.sqlite',allow_legacy_uninitialized=True)
        with TestClient(app) as client:
            auth = {'Authorization':'Bearer '+'s'*48}
            self.assertEqual(client.post('/api/conversations',json={'task_id':None}).status_code,401)
            thread = client.post('/api/conversations',headers=auth,json={'task_id':None}).json()
            payload = {'request_id':str(uuid.uuid4()),'content':'What is the reimbursement authorization?',
                       'mode':'question','search_scope':'private','expected_revision':1}
            def provider(**kwargs):
                self.assertIn('QUARTZ42',str(kwargs['messages']))
                self.assertEqual(kwargs['tools'],[])
                return self.response()
            with patch('hermes_chat.call_llm',side_effect=provider) as model, patch('public_web.normal_answer',side_effect=AssertionError('private escaped')):
                response = client.post('/api/conversations/'+thread['id']+'/messages',headers=auth,json=payload)
                self.assertEqual(response.status_code,202,response.text)
                for _ in range(300):
                    result = client.get('/api/conversations/'+thread['id']+'/requests/'+payload['request_id'],headers=auth).json()
                    if result['status'] not in ('queued','running'):
                        break
                    time.sleep(.01)
                self.assertEqual(result['status'],'succeeded',result)
                self.assertTrue(result['assistant_message']['citation_metadata']['sources'])
                replay = client.post('/api/conversations/'+thread['id']+'/messages',headers=auth,json=payload)
                self.assertEqual(replay.status_code,202,replay.text)
                self.assertEqual(model.call_count,1)
                # Scope is part of request identity, not a mutable replay flag.
                changed = dict(payload,search_scope='auto')
                self.assertEqual(client.post('/api/conversations/'+thread['id']+'/messages',headers=auth,json=changed).status_code,409)

    def test_explicit_saved_search_wins_over_public_email_subject_exception(self):
        from task_knowledge_context import private_search
        for prompt in ('Search saved mail about email protocols', 'Search the existing index for reimbursement',
                       'Find saved evidence about current Python versions', 'Cherche dans les courriels enregistrés'):
            with self.subTest(prompt=prompt):
                self.assertTrue(private_search(prompt))
        for prompt in ('What are email protocols?', 'What is the current Python version?', 'What is the history of Rome?'):
            with self.subTest(prompt=prompt):
                self.assertFalse(private_search(prompt))

    def test_revocation_during_followup_without_new_matches_discards_answer(self):
        store,thread = self.store()
        with patch('hermes_chat.call_llm',return_value=self.response()):
            _,first = self.send(store,thread['id'],'Search saved mail reimbursement authorization')
        self.assertEqual(first['status'],'succeeded',first)
        def provider(**kwargs):
            self.tasks.clear()
            self.save()
            return self.response('MUST_NOT_SAVE_FOLLOWUP')
        revision = store.get(thread['id'])['conversation']['revision']
        with patch('hermes_chat.call_llm',side_effect=provider):
            _,result = self.send(store,thread['id'],'Explain that',revision)
        self.assertEqual(result['status'],'failed',result)
        self.assertIsNone(result['assistant_message'])

    def test_history_evidence_is_rechecked_before_answer_and_before_persistence(self):
        store,thread = self.store()
        with patch('hermes_chat.call_llm',return_value=self.response()):
            _,first = self.send(store,thread['id'],'Search saved mail reimbursement authorization')
        self.assertEqual(first['status'],'succeeded',first)
        original = store._history
        def history(cid,rid):
            value = original(cid,rid)
            self.tasks[0]['source_references'] = []
            self.save()
            return value
        revision = store.get(thread['id'])['conversation']['revision']
        with patch.object(store,'_history',side_effect=history), patch('hermes_chat.call_llm',return_value=self.response()) as model:
            _,result = self.send(store,thread['id'],'Explain that',revision)
        self.assertEqual(model.call_count,0,'Revalidate history authorization after compaction')
        self.assertEqual(result['status'],'failed',result)

    def test_delete_during_first_private_summary_stops_calls_and_summary_writes(self):
        store,thread = self.store()
        with patch('hermes_chat.call_llm',return_value=self.response()):
            _,first = self.send(store,thread['id'],'Search saved mail reimbursement authorization')
        self.assertEqual(first['status'],'succeeded',first)
        with store.connect() as con:
            for n in range(3):
                rid = str(uuid.uuid4())
                con.execute('INSERT INTO requests(id,conversation_id,content,mode,status,created_at) VALUES(?,?,?,?,?,?)',(rid,thread['id'],'history','question','failed','2026-11-14'))
                con.execute('INSERT INTO messages(conversation_id,request_id,role,content,created_at) VALUES(?,?,?,?,?)',(thread['id'],rid,'user','Private history '*2000,'2026-11-14'))
        def provider(**kwargs):
            self.save({'tasks':{'a':{'deleted':True}}})
            return self.response('MUST_NOT_SAVE_SUMMARY')
        with patch('hermes_chat.call_llm',side_effect=provider) as model:
            revision = store.get(thread['id'])['conversation']['revision']
            _,result = self.send(store,thread['id'],'Summarize the earlier answer',revision)
        self.assertEqual(model.call_count,1,'Deletion must stop subsequent summary and answer calls')
        self.assertEqual(result['status'],'failed',result)
        with store.connect() as con:
            self.assertEqual(tuple(con.execute('SELECT summary,summary_until FROM conversations WHERE id=?',(thread['id'],)).fetchone()),('',0))

    def test_revoked_private_history_blocks_compaction_before_any_model_work(self):
        store,thread = self.store()
        with patch('hermes_chat.call_llm',return_value=self.response()):
            _,first = self.send(store,thread['id'],'Search saved mail reimbursement authorization')
        self.assertEqual(first['status'],'succeeded',first)
        self.tasks[0]['source_references'] = []
        self.save()
        # Real immutable rows force the rolling-summary path if the guard fails.
        with store.connect() as con:
            for n in range(3):
                rid = str(uuid.uuid4())
                con.execute('INSERT INTO requests(id,conversation_id,content,mode,status,created_at) VALUES(?,?,?,?,?,?)',(rid,thread['id'],'history','question','failed','2026-11-14'))
                con.execute('INSERT INTO messages(conversation_id,request_id,role,content,created_at) VALUES(?,?,?,?,?)',(thread['id'],rid,'user','Private history '*2000,'2026-11-14'))
        with patch('hermes_chat.call_llm',return_value=self.response('FORBIDDEN_SUMMARY')) as provider:
            revision = store.get(thread['id'])['conversation']['revision']
            _,result = self.send(store,thread['id'],'Summarize the earlier answer',revision)
        self.assertEqual(provider.call_count,0,'Revocation must precede history model work')
        self.assertEqual(result['status'],'failed',result)
        with store.connect() as con:
            self.assertEqual(tuple(con.execute('SELECT summary,summary_until FROM conversations WHERE id=?',(thread['id'],)).fetchone()),('',0))

    def test_revoke_reference_during_provider_discards_private_answer(self):
        store,thread = self.store()
        def provider(**kwargs):
            self.assertIn('QUARTZ42',str(kwargs['messages']))
            self.tasks[0]['source_references'] = []
            self.save()
            return self.response('MUST_NOT_PERSIST')
        with patch('hermes_chat.call_llm',side_effect=provider):
            _,result = self.send(store,thread['id'],'Search saved mail reimbursement authorization')
        self.assertEqual(result['status'],'failed',result)
        self.assertIsNone(result['assistant_message'])
        self.assertNotIn('MUST_NOT_PERSIST',str(store.get(thread['id'])))

    def test_private_nonresearch_turns_fence_followup_public_io(self):
        from contextlib import ExitStack
        from conversations import NewConversation
        store,_ = self.store()
        # Supplied/ambiguous operands are private by class, not by recognizing
        # sensitive vocabulary; even innocuous prose must fence future searches.
        # Explicit opt-outs persist conservative local-only intent even when
        # their subject is innocuous public knowledge (not just for this turn).
        for prompt in ('Do not browse. The diagnosis is QUARTZ42.',
                       'Do not browse. What are email protocols?',
                       "Don't search. QUARTZ42 is lavender.",
                       'Sans internet. QUARTZ42 est lavande.',
                       'Ne fais aucune recherche. QUARTZ42 est lavande.',
                       "Rewrite: Alice's diagnosis is QUARTZ42.",
                       'Translate: The access code is QUARTZ42.',
                       'Rewrite: The moon is bright.',
                       'Rewrite: QUARTZ42 is lavender.',
                       'Translate QUARTZ42 is lavender.',
                       'Rewrite QUARTZ42 is lavender. https://www.python.org/',
                       'Can you help with rewriting QUARTZ42 is lavender?',
                       'Could you provide a brief summary of QUARTZ42 is lavender?',
                       'Please edit «QUARTZ42 is lavender».',
                       'Summarizing QUARTZ42 is lavender.',
                       'Reformule QUARTZ42 est lavande.',
                       'Peux-tu traduire QUARTZ42 est lavande?',
                       'Rédige une réponse disant QUARTZ42 est lavande.',
                       'Write a reply saying QUARTZ42 is lavender.',
                       'Réécris: QUARTZ42 est lavande.',
                       'Peux-tu réécrire QUARTZ42 est lavande?',
                       'Reecrire QUARTZ42 est lavande',
                       'My patient reference is QUARTZ42.',
                       'Rewrite: My patient reference is QUARTZ42.',
                       'Do not browse. My patient reference is QUARTZ42.',
                       'Translate: Our patient reference is QUARTZ42.',
                       'My question is: My patient reference is QUARTZ42.',
                       'Sans internet. Mon patient est QUARTZ42.',
                       'Do not browse. I use QUARTZ42.',
                       'Rewrite: alice@example.test uses QUARTZ42.',
                       'Rewrite: phone 202-555-0199 uses QUARTZ42.',
                       'Rewrite: token sk-synthetic-QUARTZ42.',
                       'Rewrite: confidential QUARTZ42.',
                       'Rewrite: saved evidence QUARTZ42.',
                       'Rewrite: supplied QUARTZ42.',
                       'Remember QUARTZ42.',
                       'The previous message was QUARTZ42.',
                       'Rewrite: https://example.com/?token=QUARTZ42',
                       'Do not browse. file:///QUARTZ42',
                       'Rewrite: '+('public prose '*40)+'QUARTZ42.'):
            with self.subTest(prompt=prompt), ExitStack() as stack:
                thread = store.create(NewConversation(task_id=None),None)
                stack.enter_context(patch('hermes_chat.call_llm',return_value=self.response('Local answer')))
                stack.enter_context(patch.object(store,'knowledge_loader',return_value=[]))
                boundaries = [stack.enter_context(patch(target,side_effect=AssertionError('Private history escaped: '+target)))
                              for target in ('public_web.search_public','public_web.read_public',
                                             'public_web.socket.getaddrinfo','public_web.socket.socket',
                                             'public_web.socket.create_connection','public_web.subprocess.run')]
                request,first = self.send(store,thread['id'],prompt)
                self.assertEqual(first['status'],'succeeded',first)
                revision = store.get(thread['id'])['conversation']['revision']
                _,second = self.send(store,thread['id'],'Search QUARTZ42 latest news',revision)
                self.assertEqual(second['status'],'succeeded',second)
                for boundary in boundaries:
                    boundary.assert_not_called()
                with store.connect() as con:
                    self.assertEqual(con.execute('SELECT private_search FROM requests WHERE id=?',(request.request_id,)).fetchone()[0],1)

    def test_public_nonresearch_turns_keep_followup_research_eligible(self):
        from conversations import NewConversation
        store,_ = self.store()
        prompt = 'What is the current Python version?'
        url = 'https://www.python.org/'
        for first_prompt in ('Hello!', 'Comment ça va?', 'Can you help me?',
                             "Peux-tu m’aider?", 'Write a poem about the moon',
                             # Supplied rewrites are deliberately NOT public controls:
                             # ordinary generation alone does not supply an operand.
                             'My question is: What is a public IP address?'):
            with self.subTest(prompt=first_prompt):
                thread = store.create(NewConversation(task_id=None),None)
                with patch('hermes_chat.call_llm',return_value=self.response('Public answer')), patch.object(
                        store,'knowledge_loader',side_effect=AssertionError('Public thread read private index')), patch(
                        'public_web.search_public',return_value=[{'url':url,'title':'Python','description':'Public release'}]) as search:
                    _,first = self.send(store,thread['id'],first_prompt)
                    self.assertEqual(first['status'],'succeeded',first)
                    search.reset_mock()
                    revision = store.get(thread['id'])['conversation']['revision']
                    _,second = self.send(store,thread['id'],prompt,revision)
                    self.assertEqual(second['status'],'succeeded',second)
                    self.assertEqual(search.call_count,1)
                    self.assertEqual(search.call_args.args,(prompt,))
                with store.connect() as con:
                    self.assertEqual([r[0] for r in con.execute('SELECT private_search FROM requests WHERE conversation_id=?',(thread['id'],))],[0,0])

    def test_nonresearch_private_routing_survives_restart(self):
        from conversations import ConversationStore
        store,thread = self.store()
        with patch('hermes_chat.call_llm',return_value=self.response('Local answer')):
            _,first = self.send(store,thread['id'],'Rewrite: My patient reference is QUARTZ42.')
        self.assertEqual(first['status'],'succeeded',first)
        store.close()
        reopened = ConversationStore(self.root/'conversations.sqlite')
        reopened.start(lambda tid:self.tasks[0],title_generator=False,knowledge_loader=self.loader)
        self.addCleanup(reopened.close)
        with patch('hermes_chat.call_llm',return_value=self.response('Local answer')), patch(
                'public_web.search_public',side_effect=AssertionError('Restart leaked private history')) as search, patch(
                'public_web.read_public',side_effect=AssertionError('Restart leaked private history')) as read:
            revision = reopened.get(thread['id'])['conversation']['revision']
            _,result = self.send(reopened,thread['id'],'Search QUARTZ42 latest news',revision)
        self.assertEqual(result['status'],'succeeded',result)
        search.assert_not_called()
        read.assert_not_called()

    def test_private_history_cannot_turn_into_a_public_followup_query(self):
        store,thread = self.store()
        with patch('hermes_chat.call_llm',return_value=self.response()), patch('public_web.normal_answer',side_effect=AssertionError('Private conversation reached public routing')):
            _,first = self.send(store,thread['id'],'Search saved mail for reimbursement authorization')
            self.assertEqual(first['status'],'succeeded',first)
            revision = store.get(thread['id'])['conversation']['revision']
            _,second = self.send(store,thread['id'],'Search QUARTZ42 current authorization news',revision)
            self.assertEqual(second['status'],'succeeded',second)
        # Routing survives reopen/restart, rather than an in-memory flag.
        store.close()
        from conversations import ConversationStore
        reopened = ConversationStore(self.root/'conversations.sqlite')
        reopened.start(lambda tid:self.tasks[0],title_generator=False,knowledge_loader=self.loader)
        self.addCleanup(reopened.close)
        with patch('hermes_chat.call_llm',return_value=self.response()), patch('public_web.normal_answer',side_effect=AssertionError('Restart lost private routing')):
            revision = reopened.get(thread['id'])['conversation']['revision']
            _,third = self.send(reopened,thread['id'],'What is the latest weather?',revision)
            self.assertEqual(third['status'],'succeeded',third)

    def test_free_discussion_searches_saved_mail_and_replays_immutable_receipt(self):
        store,thread = self.store()
        seen = []
        def provider(**kwargs):
            seen.append(kwargs)
            self.assertIn('QUARTZ42',str(kwargs['messages']))
            self.assertNotIn('HIDDEN99',str(kwargs['messages']))
            self.assertEqual(kwargs['tools'],[])
            return self.response()
        with patch('hermes_chat.call_llm',side_effect=provider), patch('public_web.search_public',side_effect=AssertionError('private search escaped')), patch('public_web.read_public',side_effect=AssertionError('private read escaped')):
            request,result = self.send(store,thread['id'],'Search saved mail for reimbursement authorization')
            self.assertEqual(result['status'],'succeeded',result)
            source = next(s for s in result['assistant_message']['citation_metadata']['sources'] if s['kind']=='saved_email')
            self.assertEqual(source['task_id'],'a')
            self.assertEqual(source['verification'],'saved_scan')
            self.tasks.clear()
            self.save()
            store.submit(thread['id'],request)
        self.assertEqual(len(seen),1)
        from conversations import ConversationStore
        reopened = ConversationStore(self.root/'conversations.sqlite')
        self.assertEqual(reopened.request(thread['id'],request.request_id),result)
        self.assertEqual(result['assistant_message']['content'],'QUARTZ42, from saved mail.')


if __name__=='__main__':
    unittest.main()
