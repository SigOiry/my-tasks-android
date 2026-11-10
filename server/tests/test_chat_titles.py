"""Private scratch title lifecycle tests; no provider/network execution."""
import tempfile
import time
import unittest
import uuid
from pathlib import Path
from threading import Event
from unittest.mock import patch

from conversations import ConversationStore, NewConversation, SendMessage, EditConversation


class TitleTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir='/opt/data/cache/scratch')
        self.addCleanup(self.tmp.cleanup)
        self.store = ConversationStore(Path(self.tmp.name)/'chat.sqlite')
        self.calls = []
        self.store.start(lambda _: {}, title_generator=self.generate)
        self.addCleanup(self.store.close)

    def generate(self, context):
        self.calls.append(context)
        return 'Planning stellar observations'

    def exchange(self, thread, content='Plan observations of stellar spectra'):
        payload = SendMessage(request_id=str(uuid.uuid4()), content=content, mode='question', expected_revision=thread['revision'])
        with patch('conversations.model_text', return_value='A substantive answer about spectra.'):
            self.store.submit(thread['id'], payload)
            for _ in range(400):
                if self.store.request(thread['id'],payload.request_id)['status'] not in ('queued','running'):
                    break
                time.sleep(.005)
        return payload

    def settled(self, cid):
        for _ in range(400):
            result = self.store.get(cid)['conversation']
            if result['title_status'] not in ('queued','running'):
                return result
            time.sleep(.005)
        self.fail('Title never settled')

    def test_generated_title_durable_separate_revision_no_replay(self):
        t = self.store.create(NewConversation(task_id=None))
        p = self.exchange(t)
        result = self.settled(t['id'])
        self.assertEqual(result['title'], 'Planning stellar observations')
        self.assertEqual(result['title_origin'], 'automatic')
        self.assertEqual(result['title_revision'], 2)
        self.assertEqual(len(self.calls), 1)
        self.store.submit(t['id'],p)
        self.assertEqual(len(self.calls), 1)
        self.assertEqual(ConversationStore(self.store.path).get(t['id'])['conversation']['title'],result['title'])
        self.assertLessEqual(len(str(self.calls[0])), 9000)

    def test_manual_rename_fences_running_title(self):
        entered, release = Event(), Event()
        self.addCleanup(release.set)
        def slow(context):
            entered.set()
            release.wait(4)
            return 'Obsolete automatic title'
        self.store.title_generator = slow
        t = self.store.create(NewConversation(task_id=None))
        self.exchange(t)
        self.assertTrue(entered.wait(2))
        current = self.store.get(t['id'])['conversation']
        renamed = self.store.edit(t['id'],EditConversation(expected_revision=current['revision'],title='My permanent title'))
        release.set()
        self.store.title_pool.shutdown(wait=True)
        result = self.store.get(t['id'])['conversation']
        self.assertEqual(result['title'], 'My permanent title')
        self.assertEqual(result['title_origin'],'manual')
        self.assertEqual(result['title_revision'], renamed['title_revision'])

    def test_explicit_title_and_greeting_do_not_generate(self):
        manual = self.store.create(NewConversation(task_id=None,title='Owned subject'))
        self.exchange(manual)
        self.assertEqual(self.calls, [])
        self.assertEqual(self.settled(manual['id'])['title_origin'],'manual')
        free = self.store.create(NewConversation(task_id=None))
        self.exchange(free, 'Hello!')
        self.assertEqual(self.calls, [])

    def test_real_generator_boundary_is_toolfree_bounded_and_validated(self):
        from conversation_titles import generate_title
        from types import SimpleNamespace as NS
        with patch('hermes_chat.call_title_llm', create=True, return_value=NS(choices=[NS(message=NS(content='Stellar observation planning',tool_calls=[]))])) as model:
            self.assertEqual(generate_title([{'role':'user','content':'Plan stellar observation'}]), 'Stellar observation planning')
            self.assertEqual(model.call_args.kwargs['tools'], [])
            self.assertLessEqual(model.call_args.kwargs['timeout'],20)
            self.assertLessEqual(model.call_args.kwargs['max_tokens'],100)
        from hermes_chat import call_title_llm
        with patch('hermes_chat.subprocess.run', return_value=NS(returncode=0,stdout='{"ok":true,"content":"A title","tool_calls":[]}')) as run:
            call_title_llm(task='mobile_tasks', messages=[],tools=[],max_tokens=60,timeout=20)
        self.assertEqual(run.call_count,1)

    def test_provider_failure_keeps_meaningful_fallback_no_automatic_retry(self):
        def failed(context):
            self.calls.append(context)
            raise RuntimeError('provider unavailable')
        self.store.title_generator = failed
        t = self.store.create(NewConversation(task_id=None))
        p = self.exchange(t)
        result = self.settled(t['id'])
        self.assertEqual(result['title_status'], 'failed')
        self.assertEqual(result['title'],'Plan observations of stellar spectra')
        self.store.submit(t['id'],p)
        self.assertEqual(len(self.calls),1)

    def test_bounded_cadence_topic_change_and_followup_reply(self):
        t = self.store.create(NewConversation(task_id=None))
        self.exchange(t)
        t = self.settled(t['id'])
        for _ in range(4):
            self.exchange(t, 'Explain stellar spectra observations further')
            t = self.settled(t['id'])
        self.assertEqual(len(self.calls),1)
        self.exchange(t, 'Plan vegetable garden irrigation schedule')
        self.assertEqual(self.settled(t['id'])['title_status'],'succeeded')
        self.assertEqual(len(self.calls),2)

    def test_restart_fences_running_and_queued_titles_without_provider_replay(self):
        entered, release = Event(), Event()
        self.addCleanup(release.set)
        def slow(context):
            self.calls.append(context)
            entered.set()
            release.wait(5)
            return 'Obsolete title'
        self.store.title_generator = slow
        a = self.store.create(NewConversation(task_id=None))
        self.exchange(a)
        self.assertTrue(entered.wait(2))
        b = self.store.create(NewConversation(task_id=None))
        self.exchange(b)
        restarted = ConversationStore(self.store.path)
        restarted.start(lambda _: {},title_generator=lambda _: self.fail('Restart replayed title'))
        self.addCleanup(restarted.close)
        release.set()
        self.store.title_pool.shutdown(wait=True)
        for t in (a,b):
            result = restarted.get(t['id'])['conversation']
            self.assertEqual(result['title'],'New conversation')
            self.assertEqual(result['title_status'],'interrupted')
        self.assertEqual(len(self.calls),1)

    def test_archive_fences_generation_and_reopen_does_not_replay(self):
        entered, release = Event(), Event()
        self.addCleanup(release.set)
        def slow(context):
            entered.set()
            release.wait(4)
            return 'Obsolete title'
        self.store.title_generator = slow
        t = self.store.create(NewConversation(task_id=None))
        self.exchange(t)
        self.assertTrue(entered.wait(2))
        current = self.store.get(t['id'])['conversation']
        archived = self.store.edit(t['id'],EditConversation(expected_revision=current['revision'],archived=True))
        release.set()
        self.store.title_pool.shutdown(wait=True)
        result = self.store.edit(t['id'],EditConversation(expected_revision=archived['revision'],archived=False))
        self.assertEqual(result['title'],'New conversation')
        self.assertEqual(result['title_status'],'interrupted')

    def test_manual_rename_during_reply_completion_is_permanent(self):
        entered, release = Event(), Event()
        self.addCleanup(release.set)
        t = self.store.create(NewConversation(task_id=None))
        p = SendMessage(request_id=str(uuid.uuid4()),content='Plan stellar observations tonight',mode='question',expected_revision=1)
        def slow(*args,**kwargs):
            entered.set()
            release.wait(4)
            return 'Reply'
        with patch('conversations.model_text',side_effect=slow):
            self.store.submit(t['id'],p)
            self.assertTrue(entered.wait(2))
            current = self.store.get(t['id'])['conversation']
            self.store.edit(t['id'],EditConversation(expected_revision=current['revision'],title='Permanent manual subject'))
            release.set()
            self.store.pool.shutdown(wait=True)
        result = self.settled(t['id'])
        self.assertEqual(result['title'],'Permanent manual subject')
        self.assertEqual(self.calls,[])

    def test_stale_title_rejected_when_a_new_topic_is_accepted(self):
        entered, release = Event(), Event()
        self.addCleanup(release.set)
        def slow(context):
            entered.set()
            release.wait(4)
            return 'Obsolete title'
        self.store.title_generator = slow
        t = self.store.create(NewConversation(task_id=None))
        self.exchange(t)
        self.assertTrue(entered.wait(2))
        current = self.store.get(t['id'])['conversation']
        self.exchange(current,'Plan vegetable garden irrigation schedule')
        release.set()
        self.store.title_pool.shutdown(wait=True)
        result = self.store.get(t['id'])['conversation']
        self.assertNotEqual(result['title'],'Obsolete title')

    def test_cancelled_title_jobs_still_occupy_bounded_admission(self):
        entered, release = Event(), Event()
        self.addCleanup(release.set)
        def slow(context):
            entered.set()
            release.wait(5)
            return 'First title'
        self.store.title_generator = slow
        for _ in range(4):
            t = self.store.create(NewConversation(task_id=None))
            self.exchange(t)
            self.assertTrue(entered.wait(2))
            current = self.store.get(t['id'])['conversation']
            self.store.edit(t['id'],EditConversation(expected_revision=current['revision'],title='Manual'))
        overflow = self.store.create(NewConversation(task_id=None))
        self.exchange(overflow)
        self.assertEqual(self.store.get(overflow['id'])['conversation']['title_status'],'idle')
        release.set()
