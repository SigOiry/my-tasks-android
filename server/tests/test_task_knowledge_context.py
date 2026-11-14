"""Real derived SQLite + durable conversation worker; synthetic model boundary."""
import importlib.util
import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch
import uuid

MODULE = Path('/opt/data/personal-assistant/task_knowledge.py')


class TaskKnowledgeChatTests(unittest.TestCase):
    def test_task_chat_retrieves_linked_history_with_persisted_sources_and_no_public_io(self):
        import conversations
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as tmp:
            root = Path(tmp)
            task = {'id':'a','title':'Budget','source_references':['gmail:owner@example.test:message:aaa']}
            canonical = root/'tasks.json'
            canonical.write_text(json.dumps({'tasks':[task],'dashboard':{}}))
            scans = root/'scans'/'run'
            scans.mkdir(parents=True)
            (scans/'manifest.json').write_text(json.dumps({'end':'2026-11-14T10:00:00+01:00','items':[
                {'source':'Gmail','content':{'messages':[{'source_reference':task['source_references'][0],
                    'body':'The reimbursement authorization is QUARTZ42.'}]}}]}))
            spec = importlib.util.spec_from_file_location('knowledge_test', MODULE)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            module.Knowledge(canonical,scans.parent,root/'private'/'knowledge.sqlite').refresh()
            import task_knowledge_context
            loader = task_knowledge_context.KnowledgeContext(canonical,root/'private'/'knowledge.sqlite')
            store = conversations.ConversationStore(root/'conversations.sqlite')
            store.start(lambda _:task,title_generator=False,knowledge_loader=loader)
            self.addCleanup(store.close)
            thread = store.create(conversations.NewConversation(task_id='a'),task)
            request = conversations.SendMessage(request_id=str(uuid.uuid4()),content='What is the reimbursement authorization?',mode='question',expected_revision=1)
            seen = []
            def provider(**kwargs):
                seen.append(kwargs)
                self.assertIn('QUARTZ42',str(kwargs['messages']))
                self.assertEqual(kwargs['tools'],[])
                return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='QUARTZ42, from saved mail.',tool_calls=[]))])
            with patch('hermes_chat.call_llm',side_effect=provider), patch('public_web.search_public',side_effect=AssertionError('Private task must not search')), patch('public_web.read_public',side_effect=AssertionError('Private task must not browse')):
                store.submit(thread['id'],request)
                store.pool.shutdown(wait=True)
            result = store.request(thread['id'],request.request_id)
            self.assertEqual(result['status'],'succeeded',result)
            sources = result['assistant_message']['citation_metadata']['sources']
            history = [s for s in sources if s.get('verification')=='saved_scan']
            self.assertTrue(history)
            self.assertEqual(history[0]['task_id'],'a')
            self.assertIn('manifest.json',history[0]['source_path'])
            self.assertEqual(len(seen),1)
            store.submit(thread['id'],request)
            self.assertEqual(len(seen),1)
            reopened = conversations.ConversationStore(root/'conversations.sqlite')
            self.assertEqual(reopened.get(thread['id'])['messages'][-1]['citation_metadata']['sources'],sources)

    def test_real_authenticated_adapter_wires_index_and_rejects_deleted_task_chat(self):
        import time
        from fastapi.testclient import TestClient
        from mobile_api import create_app, dashboard
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as tmp:
            root = Path(tmp)
            token = root/'token'
            token.write_text('s'*48)
            token.chmod(0o600)
            canonical = root/'tasks.json'
            task = dashboard.taskdb.Store(canonical).add('Budget',description='Authorization is MARBLE17')
            spec = importlib.util.spec_from_file_location('knowledge_route_test', MODULE)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
            module.Knowledge(canonical,root/'scans',root/'knowledge'/'knowledge.sqlite').refresh()
            with patch('conversations.generate_title',False):
                app = create_app(db_path=canonical,token_file=token,metadata_path=root/'events.sqlite',
                                 session_path=root/'sessions.sqlite',account_path=root/'account.sqlite',
                                 conversation_path=root/'conversations.sqlite',email_path=root/'email.sqlite',allow_legacy_uninitialized=True)
            self.assertIsNotNone(app.state.conversations.knowledge_loader,'Authenticated adapter must wire the local index')
            with TestClient(app) as client:
                auth = {'Authorization':'Bearer '+'s'*48}
                self.assertEqual(client.post('/api/conversations',json={'task_id':task['id']}).status_code,401)
                thread = client.post('/api/conversations',headers=auth,json={'task_id':task['id']}).json()
                def provider(**kwargs):
                    self.assertIn('MARBLE17',str(kwargs['messages']))
                    self.assertEqual(kwargs['tools'],[])
                    return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='MARBLE17',tool_calls=[]))])
                with patch('hermes_chat.call_llm',side_effect=provider),patch('public_web.search_public',side_effect=AssertionError('No private web search')):
                    response = client.post('/api/conversations/'+thread['id']+'/messages',headers=auth,json={
                        'request_id':str(uuid.uuid4()),'content':'What is the authorization?', 'mode':'question','expected_revision':1})
                    self.assertEqual(response.status_code,202,response.text)
                    rid = response.json()['request']['id']
                    for _ in range(200):
                        final = client.get('/api/conversations/'+thread['id']+'/requests/'+rid,headers=auth).json()
                        if final['status'] not in ('running','queued'):
                            break
                        time.sleep(.01)
                self.assertEqual(final['status'],'succeeded',final)
                self.assertTrue(any(s['verification']=='saved_task_index' for s in final['assistant_message']['citation_metadata']['sources']))
                current = client.get('/api/tasks/'+task['id'],headers=auth).json()
                deleted = client.request('DELETE','/api/tasks/'+task['id'],headers=auth,json={'revision':current['revision']})
                self.assertEqual(deleted.status_code,200)
                with patch('hermes_chat.call_llm',side_effect=AssertionError('Deleted tasks must not call a provider')):
                    old_chat = client.post('/api/chat',headers=auth,json={'task_id':task['id'],'message':'What is the authorization?'})
                    self.assertEqual(old_chat.status_code,404,old_chat.text)

    def test_missing_or_corrupt_index_falls_back_without_creating_private_state(self):
        from task_knowledge_context import KnowledgeContext
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as tmp:
            root = Path(tmp)
            canonical = root/'tasks.json'
            canonical.write_text(json.dumps({'tasks':[{'id':'a','title':'Budget'}],'dashboard':{}}))
            target = root/'knowledge'/'knowledge.sqlite'
            adapter = KnowledgeContext(canonical,target)
            self.assertEqual(adapter('a','amount'),[])
            self.assertFalse(target.parent.exists())
            target.parent.mkdir()
            target.write_text('not sqlite')
            before = target.read_bytes()
            self.assertEqual(adapter('a','amount'),[])
            self.assertEqual(target.read_bytes(),before)

    def test_delete_during_provider_discards_the_answer(self):
        from conversations import ConversationStore, NewConversation, SendMessage
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as tmp:
            task = {'id':'a','title':'Budget','deleted':False}
            store = ConversationStore(Path(tmp)/'conversations.sqlite')
            store.start(lambda _:dict(task),title_generator=False)
            self.addCleanup(store.close)
            thread = store.create(NewConversation(task_id='a'),task)
            request = SendMessage(request_id=str(uuid.uuid4()),content='Summarize this task',mode='question',expected_revision=1)
            def provider(**kwargs):
                task['deleted'] = True
                return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='SHOULD_NOT_PERSIST',tool_calls=[]))])
            with patch('hermes_chat.call_llm',side_effect=provider):
                store.submit(thread['id'],request)
                store.pool.shutdown(wait=True)
            result = store.request(thread['id'],request.request_id)
            self.assertEqual(result['status'],'failed')
            self.assertIsNone(result['assistant_message'])
            self.assertNotIn('SHOULD_NOT_PERSIST',str(store.get(thread['id'])))

    def test_delete_during_first_compaction_call_stops_provider_work_and_summary_persistence(self):
        from conversations import ConversationStore, NewConversation, SendMessage
        for message_size in (9000, 23000):
            with self.subTest(message_size=message_size), tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as tmp:
                task = {'id':'a','title':'Budget','deleted':False}
                store = ConversationStore(Path(tmp)/'conversations.sqlite')
                store.start(lambda _:dict(task),title_generator=False)
                self.addCleanup(store.close)
                thread = store.create(NewConversation(task_id='a'),task)
                originals = ['Historical private turn '+str(n)+' '+('x'*message_size) for n in range(3)]
                with store.connect() as con:
                    for content in originals:
                        rid = str(uuid.uuid4())
                        con.execute('INSERT INTO requests(id,conversation_id,content,mode,status,created_at) VALUES (?,?,?,?,?,?)',(rid,thread['id'],'synthetic','question','failed','2026-11-14'))
                        con.execute('INSERT INTO messages(conversation_id,request_id,role,content,created_at) VALUES (?,?,?,?,?)',(thread['id'],rid,'user',content,'2026-11-14'))
                request = SendMessage(request_id=str(uuid.uuid4()),content='Summarize',mode='question',expected_revision=1)
                calls = []
                def provider(**kwargs):
                    calls.append(kwargs)
                    task['deleted'] = True
                    return SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='SUMMARY_MUST_NOT_PERSIST',tool_calls=[]))])
                with patch('hermes_chat.call_llm',side_effect=provider):
                    store.submit(thread['id'],request)
                    store.pool.shutdown(wait=True)
                result = store.request(thread['id'],request.request_id)
                with store.connect() as con:
                    summary = tuple(con.execute('SELECT summary,summary_until FROM conversations WHERE id=?',(thread['id'],)).fetchone())
                self.assertEqual((len(calls),summary),(1,('',0)), 'Deletion must stop later summary/answer calls and summary writes')
                self.assertIn('Summarize older conversation data',calls[0]['messages'][0]['content'])
                self.assertEqual(result['status'],'failed',result)
                self.assertIsNone(result['assistant_message'])
                saved_messages = store.get(thread['id'])['messages']
                assert isinstance(saved_messages,list)
                self.assertEqual([m['content'] for m in saved_messages],originals+[request.content])

    def test_task_unavailable_after_history_prevents_normal_answer_call(self):
        from fastapi import HTTPException
        from conversations import ConversationStore, NewConversation, SendMessage
        for unavailable in ('deleted','missing'):
            with self.subTest(unavailable=unavailable), tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as tmp:
                canonical = Path(tmp)/'tasks.json'
                task = {'id':'a','title':'Budget','deleted':False}
                canonical.write_text(json.dumps({'tasks':[task]}))
                def load_task(task_id):
                    for current in json.loads(canonical.read_text())['tasks']:
                        if current['id']==task_id:
                            return current
                    raise HTTPException(404,'Task not found')
                store = ConversationStore(Path(tmp)/'conversations.sqlite')
                store.start(load_task,title_generator=False)
                self.addCleanup(store.close)
                thread = store.create(NewConversation(task_id='a'),task)
                request = SendMessage(request_id=str(uuid.uuid4()),content='Summarize',mode='question',expected_revision=1)
                history = store._history
                def remove_after_history(cid,rid):
                    result = history(cid,rid)
                    canonical.write_text(json.dumps({'tasks':[] if unavailable=='missing' else [dict(task,deleted=True)]}))
                    return result
                with patch.object(store,'_history',side_effect=remove_after_history), patch('hermes_chat.call_llm',return_value=SimpleNamespace(choices=[SimpleNamespace(message=SimpleNamespace(content='MUST_NOT_CALL',tool_calls=[]))])) as provider:
                    store.submit(thread['id'],request)
                    store.pool.shutdown(wait=True)
                self.assertEqual(provider.call_count,0,'Recheck canonical task after history and before the normal answer')
                result = store.request(thread['id'],request.request_id)
                self.assertEqual(result['status'],'failed',result)
                self.assertIsNone(result['assistant_message'])
                saved_messages = store.get(thread['id'])['messages']
                assert isinstance(saved_messages,list)
                self.assertEqual([m['content'] for m in saved_messages],[request.content])

    def test_deleted_task_is_guarded_before_history_summary_can_call_provider(self):
        from conversations import ConversationStore, NewConversation, SendMessage
        with tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch') as tmp:
            task = {'id':'a','title':'Budget','deleted':True}
            store = ConversationStore(Path(tmp)/'conversations.sqlite')
            store.start(lambda _:task,title_generator=False)
            self.addCleanup(store.close)
            thread = store.create(NewConversation(task_id='a'),task)
            with store.connect() as con:
                for n in range(3):
                    rid = str(uuid.uuid4())
                    con.execute('INSERT INTO requests(id,conversation_id,content,mode,status,created_at) VALUES (?,?,?,?,?,?)',(rid,thread['id'],'synthetic','question','failed','2026-11-14'))
                    con.execute('INSERT INTO messages(conversation_id,request_id,role,content,created_at) VALUES (?,?,?,?,?)',(thread['id'],rid,'user','private historical text '*1000,'2026-11-14'))
            request = SendMessage(request_id=str(uuid.uuid4()),content='Summarize',mode='question',expected_revision=1)
            with patch('hermes_chat.call_llm',side_effect=AssertionError('No summary or answer for deleted tasks')) as provider:
                store.submit(thread['id'],request)
                store.pool.shutdown(wait=True)
            self.assertEqual(provider.call_count,0)
            self.assertEqual(store.request(thread['id'],request.request_id)['status'],'failed')


if __name__=='__main__':
    unittest.main()
