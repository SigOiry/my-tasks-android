"""Private-copy integration tests; never import/write the live workflow state."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import shutil
import sys
import tempfile
import unittest

LIVE = Path('/opt/data/personal-assistant')
HERE = Path(__file__).resolve().parent
GMAIL = 'gmail:reviewer@example.com:message:1a23456789abcdef'
OLD = 'gmail:reviewer@example.com:message:1a23456789abcdee'
NANTES = 'nantes-imap://INBOX.Sent;UIDVALIDITY=1234;UID=42;Message-ID=<reply.42@example.com>'


class ProvenanceTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(dir=os.environ['TMPDIR'])
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        for name in ('sync_workflow.py', 'taskdb.py'):
            shutil.copyfile(LIVE/name, self.root/name)
        self.original = (self.root/'sync_workflow.py').read_bytes()
        sys.path.insert(0, str(HERE))
        self.addCleanup(lambda: sys.path.remove(str(HERE)))
        try:
            import install_notification_origins as installer
        except ImportError:
            self.fail('provenance installer is not implemented')
        self.installer = installer
        installer.install(self.root/'sync_workflow.py', apply=True)
        sys.path.insert(0, str(self.root))
        self.addCleanup(lambda: sys.path.remove(str(self.root)))
        for name in ('taskdb', 'notification_origins'):
            sys.modules.pop(name, None)
        spec = importlib.util.spec_from_file_location('private_workflow', self.root/'sync_workflow.py')
        self.module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.module)
        self.store = self.module.Store(self.root/'tasks.json')
        self.store.init()
        self.workflow = self.module.Workflow(self.store.path, self.root/'state')

    def candidate(self, **changes):
        return dict({'action_key': 'report:review', 'title': 'Review report', 'source': 'Gmail',
                     'source_reference': GMAIL, 'evidence': ['Please review version two.']}, **changes)

    def items(self):
        return [{'id': 'gmail:reviewer@example.com:thread:abcd', 'source': 'Gmail',
                 'content': {'account': 'reviewer@example.com', 'thread_id': 'abcd',
                             'messages': [{'id': OLD.rsplit(':', 1)[1], 'source_reference': OLD},
                                          {'id': GMAIL.rsplit(':', 1)[1], 'source_reference': GMAIL}]}},
                {'id': NANTES, 'source': 'Nantes Université email',
                 'content': {'source_reference': NANTES, 'mailbox': 'INBOX.Sent',
                             'uidvalidity': '1234', 'uid': '42'}}]

    def prepare(self, candidate=None, origins=True, items=None):
        items = self.items() if items is None else items
        run = self.workflow.prepare([lambda *a: {'items': items, 'coverage': {'complete': True, 'errors': []}}])
        manifest = json.loads((Path(run['run_dir'])/'manifest.json').read_text())
        candidate = self.candidate() if candidate is None else candidate
        review = {'reviewed': True, 'coverage_reviewed': True, 'reviewer': 'Test reviewer',
                  'run_id': run['run_id'], 'manifest_hash': self.module.digest(manifest),
                  'candidates': [candidate], 'dispositions': [
                      {'item_id': i['id'], 'hash': i['hash'], 'decision': 'action',
                       'reason': 'Read exact evidence', 'action_keys': [candidate['action_key']]}
                      for i in manifest['items']]}
        if origins:
            review.update(notification_origins_version=1, notification_origins={candidate['action_key']: [
                {'item_id': manifest['items'][0]['id'], 'reference': GMAIL, 'role': 'trigger'}]})
        return run, manifest, review

    def test_invalid_origins_fail_before_any_database_write(self):
        run, manifest, review = self.prepare()
        valid = review['notification_origins']['report:review'][0]
        cases = [
            ('null', None), ('list', []), ('unknown-key', {'other': [valid]}),
            ('not-list', {'report:review': valid}), ('empty-list', {'report:review': []}),
            ('not-object', {'report:review': ['bad']}),
            ('unknown-item', {'report:review': [dict(valid, item_id='unknown')]}),
            ('wrong-item', {'report:review': [dict(valid, item_id=NANTES)]}),
            ('bad-role', {'report:review': [dict(valid, role='latest')]}),
            ('extra-field', {'report:review': [dict(valid, extra=True)]}),
            ('missing-role', {'report:review': [{k: v for k, v in valid.items() if k != 'role'}]}),
            ('thread', {'report:review': [dict(valid, reference=manifest['items'][0]['id'])]}),
            ('not-carried', {'report:review': [dict(valid, reference=OLD)]}),
            ('unknown-message', {'report:review': [dict(valid, reference=GMAIL[:-1]+'0')]}),
            ('duplicate', {'report:review': [valid, valid]}),
            ('conflicting-role', {'report:review': [valid, dict(valid, role='context')]}),
            ('too-many', {'report:review': [valid] * 33}),
            ('object-ref', {'report:review': [dict(valid, reference={})]}),
        ]
        for bad in (GMAIL+'\r\nBcc: victim@example.com', 'javascript:alert(1)',
                    'nantes-imap://INBOX;UID=42', 'nantes-imap://../x;UIDVALIDITY=1;UID=2;Message-ID=<x@y>',
                    NANTES+';UID=99', GMAIL+';touch /x', 'x'*2049):
            cases.append(('malformed-'+repr(bad)[:70], {'report:review': [dict(valid, reference=bad)]}))
        before = self.store.path.read_bytes()
        for label, mapping in cases:
            with self.subTest(label=label):
                changed = copy.deepcopy(review)
                changed['notification_origins'] = mapping
                with self.assertRaisesRegex(ValueError, 'notification_origins'):
                    self.workflow.commit(run['run_id'], changed)
                self.assertEqual(self.store.path.read_bytes(), before)
                self.assertFalse((Path(run['run_dir'])/'reviewed.json').exists())
                self.assertIsNone(self.workflow.status()['checkpoint'])
        for version in (None, True, '1', 0, 2):
            with self.subTest(version=version):
                changed = copy.deepcopy(review)
                changed['notification_origins_version'] = version
                with self.assertRaisesRegex(ValueError, 'notification_origins'):
                    self.workflow.commit(run['run_id'], changed)
                self.assertEqual(self.store.path.read_bytes(), before)
        changed = copy.deepcopy(review)
        del changed['notification_origins_version']
        with self.assertRaisesRegex(ValueError, 'notification_origins'):
            self.workflow.commit(run['run_id'], changed)

    def test_omitted_origins_never_infers_or_replaces_historical_record(self):
        run, _, review = self.prepare(origins=False)
        self.workflow.commit(run['run_id'], review)
        self.assertIsNone(self.saved()[2])
        run, _, review = self.prepare(self.candidate(evidence=['A genuinely changed request.']))
        self.workflow.commit(run['run_id'], review)
        record = self.saved()[2]
        self.assertIsNotNone(record)
        run, _, review = self.prepare(self.candidate(evidence=['A third update, without explicit origins.']), origins=False)
        self.workflow.commit(run['run_id'], review)
        _, task, stale = self.saved()
        self.assertEqual(stale, record)
        self.assertNotEqual(stale['snapshot'], self.module.notification_snapshot(task))

    def test_unchanged_snapshot_cannot_replace_provenance(self):
        candidate = self.candidate(source_references=[OLD, GMAIL])
        run, _, review = self.prepare(candidate)
        self.workflow.commit(run['run_id'], review)
        record = self.saved()[2]
        run, _, review = self.prepare(candidate)
        review['notification_origins']['report:review'][0]['reference'] = OLD
        self.workflow.commit(run['run_id'], review)
        self.assertEqual(self.saved()[2], record)

    def test_manual_override_blocks_attachment_even_when_evidence_changes(self):
        self.store.sync({'reviewed': True, 'candidates': [self.candidate()]})
        task = self.store.read()['tasks'][0]
        self.store.edit(task['id'], priority='low')
        run, _, review = self.prepare(self.candidate(priority='high', evidence=['Changed evidence']))
        self.workflow.commit(run['run_id'], review)
        _, task, record = self.saved()
        self.assertEqual(task['priority'], 'low')
        self.assertIn('Changed evidence', task['evidence'])
        self.assertIsNone(record)

    def test_terminal_status_blocks_attachment(self):
        self.store.sync({'reviewed': True, 'candidates': [self.candidate()]})
        task = self.store.read()['tasks'][0]
        self.store.edit(task['id'], status='completed')
        run, _, review = self.prepare(self.candidate(status='inbox', evidence=['Changed evidence']))
        self.workflow.commit(run['run_id'], review)
        self.assertIsNone(self.saved()[2])

    def test_cross_source_update_existing_thread_preserves_explicit_roles(self):
        self.store.sync({'reviewed': True, 'candidates': [self.candidate(source_reference=OLD, evidence=['Old request'])]})
        original_id = self.store.read()['tasks'][0]['id']
        candidate = self.candidate(source_references=[OLD, GMAIL, NANTES])
        run, manifest, review = self.prepare(candidate)
        review['notification_origins']['report:review'].append(
            {'item_id': NANTES, 'reference': NANTES, 'role': 'context'})
        result = self.workflow.commit(run['run_id'], review)
        db, task, record = self.saved()
        self.assertEqual(result['result']['created'], 0)
        self.assertEqual(len(db['tasks']), 1)
        self.assertEqual(task['id'], original_id)
        self.assertEqual([(o['reference'], o['role']) for o in record['origins']],
                         [(GMAIL, 'trigger'), (NANTES, 'context')])
        self.assertEqual(record['origins'][1]['item_hash'], manifest['items'][1]['hash'])
        self.assertIn(OLD, task['source_references'])
        self.assertNotIn(OLD, [o['reference'] for o in record['origins']])

    def test_nantes_trigger_and_gmail_context_do_not_follow_primary_source(self):
        run, manifest, review = self.prepare(self.candidate(source_references=[GMAIL, NANTES]))
        review['notification_origins']['report:review'] = [
            {'item_id': NANTES, 'reference': NANTES, 'role': 'trigger'},
            {'item_id': manifest['items'][0]['id'], 'reference': GMAIL, 'role': 'context'}]
        self.workflow.commit(run['run_id'], review)
        self.assertEqual(self.saved()[2]['origins'][0]['reference'], NANTES)
        self.assertEqual(self.saved()[1]['source_reference'], GMAIL)

    def test_disposition_hash_link_and_semantic_decision_are_required(self):
        run, _, review = self.prepare()
        before = self.store.path.read_bytes()
        for changes in ({'hash': 'bad'}, {'action_keys': []}, {'decision': 'no_action'},
                        {'decision': 'excluded'}, {'decision': 'tombstone'}):
            with self.subTest(changes=changes):
                invalid = copy.deepcopy(review)
                invalid['dispositions'][0].update(changes)
                with self.assertRaises(ValueError):
                    self.workflow.commit(run['run_id'], invalid)
                self.assertEqual(self.store.path.read_bytes(), before)
        # A previously reviewed unchanged item may not silently reuse its old disposition.
        self.workflow.commit(run['run_id'], dict(review, candidates=[], dispositions=[
            dict(d, decision='no_action', action_keys=[]) for d in review['dispositions']],
            notification_origins={}))
        run, manifest, review = self.prepare()
        self.assertFalse(manifest['items'][0]['review_required'])
        review['dispositions'] = review['dispositions'][1:]
        before = self.store.path.read_bytes()
        with self.assertRaisesRegex(ValueError, 'notification_origins'):
            self.workflow.commit(run['run_id'], review)
        self.assertEqual(self.store.path.read_bytes(), before)

    def test_references_in_prose_or_other_items_are_not_structured_proof(self):
        items = self.items()
        items[0]['content']['messages'] = []
        items[0]['content']['body'] = 'Quoted source_reference: '+GMAIL
        items[1]['content']['messages'] = [{'id': GMAIL.rsplit(':', 1)[1], 'source_reference': GMAIL}]
        run, _, review = self.prepare(items=items)
        before = self.store.path.read_bytes()
        with self.assertRaisesRegex(ValueError, 'notification_origins'):
            self.workflow.commit(run['run_id'], review)
        self.assertEqual(self.store.path.read_bytes(), before)

    def test_deleted_candidate_is_suppressed_without_provenance(self):
        self.store.sync({'reviewed': True, 'candidates': [self.candidate()]})
        self.store.delete(self.store.read()['tasks'][0]['id'])
        run, _, review = self.prepare()
        result = self.workflow.commit(run['run_id'], review)
        self.assertEqual(result['suppressed'], ['report:review'])
        self.assertEqual(self.store.read()['tasks'], [])
        self.assertNotIn('notification_origins', self.store.read().get('dashboard', {}))

    def test_atomic_replace_failure_rolls_back_task_and_origins_then_recovers(self):
        from unittest.mock import patch
        run, _, review = self.prepare()
        before = self.store.path.read_bytes()
        original_replace = self.module.os.replace
        def fail_db(source, target):
            if Path(target) == self.store.path:
                raise OSError('simulated canonical replace failure')
            return original_replace(source, target)
        with patch.object(self.module.os, 'replace', side_effect=fail_db):
            with self.assertRaisesRegex(OSError, 'canonical replace failure'):
                self.workflow.commit(run['run_id'], review)
        self.assertEqual(self.store.path.read_bytes(), before)
        self.assertIsNone(self.workflow.status()['checkpoint'])
        self.workflow.commit(run['run_id'], review)
        self.assertIsNotNone(self.saved()[2])

    def test_receipt_crash_recovers_provenance_exactly_once(self):
        from unittest.mock import patch
        run, _, review = self.prepare()
        original_atomic = self.module.atomic_json
        def fail_receipt(path, data):
            if Path(path).name == 'result.json':
                raise OSError('simulated receipt crash')
            return original_atomic(path, data)
        with patch.object(self.module, 'atomic_json', side_effect=fail_receipt):
            with self.assertRaises(OSError):
                self.workflow.commit(run['run_id'], review)
        before = self.store.path.read_bytes()
        self.workflow.commit(run['run_id'], review)
        self.assertEqual(self.store.path.read_bytes(), before)
        self.assertEqual(self.store.read()['sync']['runs'], 1)
        self.assertIsNotNone(self.saved()[2])

    def test_installer_dry_run_idempotence_and_fail_closed(self):
        target = self.root/'sync_workflow.py'
        patched = target.read_bytes()
        self.assertFalse(self.installer.install(target, apply=True)['changed'])
        self.assertEqual(target.read_bytes(), patched)
        target.write_bytes(self.original)
        self.assertTrue(self.installer.install(target)['changed'])
        self.assertEqual(target.read_bytes(), self.original)
        for content in (self.original+b'\n# unexpected drift\n', patched+b'\n# unexpected drift\n',
                        self.original.replace(b'atomic_json', b'changed_json', 1)):
            target.write_bytes(content)
            with self.assertRaises(ValueError):
                self.installer.install(target, apply=True)
            self.assertEqual(target.read_bytes(), content)
        target.write_bytes(self.original)
        helper = target.with_name('notification_origins.py')
        helper.write_text('# incompatible helper')
        with self.assertRaises(ValueError):
            self.installer.install(target, apply=True)
        self.assertEqual(target.read_bytes(), self.original)

    def test_future_template_requires_explicit_exact_message_review(self):
        run, _, _ = self.prepare()
        template = json.loads((Path(run['run_dir'])/'review-template.json').read_text())
        self.assertEqual(template['notification_origins_version'], 1)
        self.assertEqual(template['notification_origins'], {})
        text = (Path(run['run_dir'])/'review.md').read_text()
        for phrase in ('notification_origins', 'trigger', 'context', 'never thread',
                       'Do not infer origins', 'historical source chooser'):
            self.assertIn(phrase, text)

    def test_structured_known_references_still_reject_encoded_injection(self):
        bad = GMAIL.replace('reviewer@', 'reviewer%0a@')
        items = self.items()
        items[0]['content']['account'] = 'reviewer%0a@example.com'
        items[0]['content']['messages'][1]['source_reference'] = bad
        run, _, review = self.prepare(self.candidate(source_reference=bad), items=items)
        review['notification_origins']['report:review'][0]['reference'] = bad
        before = self.store.path.read_bytes()
        with self.assertRaisesRegex(ValueError, 'notification_origins'):
            self.workflow.commit(run['run_id'], review)
        self.assertEqual(self.store.path.read_bytes(), before)

    def test_original_workflow_regression_suite_runs_on_private_patched_copy(self):
        import subprocess
        shutil.copyfile(LIVE/'test_sync_workflow.py', self.root/'test_sync_workflow.py')
        # CLI imports these before acquiring its lock; copy only read-only code,
        # never provider configuration, credentials, source dumps, or live state.
        for relative in ('scans/fetch_google.py', 'scans/nantes/scan.py'):
            destination = self.root/relative
            destination.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(LIVE/relative, destination)
        result = subprocess.run([sys.executable, '-m', 'unittest', 'test_sync_workflow', '-v'],
                                cwd=self.root, capture_output=True, text=True, timeout=60,
                                env=dict(os.environ, PYTHONDONTWRITEBYTECODE='1'))
        self.assertEqual(result.returncode, 0, result.stdout+result.stderr)
        self.assertIn('OK', result.stderr)
        print('Private patched workflow original regression results:\n'+result.stderr, file=sys.stderr)

    def test_installer_rejects_byte_drift_including_line_endings(self):
        target = self.root/'sync_workflow.py'
        crlf = self.original.replace(b'\n', b'\r\n')
        target.write_bytes(crlf)
        with self.assertRaisesRegex(ValueError, 'digest'):
            self.installer.install(target, apply=True)
        self.assertEqual(target.read_bytes(), crlf)

    def test_installer_symlinks_fail_and_clean_dry_run_creates_nothing(self):
        directory = self.root/'clean'
        directory.mkdir()
        target = directory/'sync_workflow.py'
        target.write_bytes(self.original)
        self.assertTrue(self.installer.install(target)['changed'])
        self.assertEqual(set(p.name for p in directory.iterdir()), {'sync_workflow.py'})
        target.unlink()
        target.symlink_to(self.root/'sync_workflow.py')
        with self.assertRaisesRegex(ValueError, 'symlink'):
            self.installer.install(target, apply=True)
        target.unlink()
        target.write_bytes(self.original)
        (directory/'notification_origins.py').symlink_to(self.root/'notification_origins.py')
        with self.assertRaisesRegex(ValueError, 'symlink'):
            self.installer.install(target, apply=True)
        self.assertEqual(target.read_bytes(), self.original)

    def test_bounded_action_and_total_origin_counts(self):
        refs = ['gmail:reviewer@example.com:message:'+format(i, '016x') for i in range(32)]
        items = self.items()
        items[0]['content']['messages'] = [
            {'id': ref.rsplit(':', 1)[1], 'source_reference': ref} for ref in refs]
        for action_count, origins_count in ((129, 1), (17, 32)):
            with self.subTest(action_count=action_count):
                run, manifest, review = self.prepare(items=items)
                candidates = [self.candidate(action_key='action:'+str(i), title='Action '+str(i),
                                             source_reference=refs[0], source_references=refs)
                              for i in range(action_count)]
                review['candidates'] = candidates
                for d in review['dispositions']:
                    d['action_keys'] = [c['action_key'] for c in candidates]
                review['notification_origins'] = {c['action_key']: [
                    {'item_id': manifest['items'][0]['id'], 'reference': ref, 'role': 'trigger'}
                    for ref in refs[:origins_count]] for c in candidates}
                before = self.store.path.read_bytes()
                with self.assertRaisesRegex(ValueError, 'notification_origins'):
                    self.workflow.commit(run['run_id'], review)
                self.assertEqual(self.store.path.read_bytes(), before)
                self.workflow.abort(run['run_id'], 'invalid private test fixture')

    def test_existing_disposition_can_explicitly_attribute_context_only(self):
        run, _, review = self.prepare()
        review['dispositions'][0]['decision'] = 'existing'
        review['notification_origins']['report:review'][0]['role'] = 'context'
        self.workflow.commit(run['run_id'], review)
        self.assertEqual([o['role'] for o in self.saved()[2]['origins']], ['context'])

    def test_explicit_older_message_is_not_replaced_by_latest_thread_message(self):
        run, _, review = self.prepare(self.candidate(source_reference=OLD, source_references=[OLD, GMAIL]))
        review['notification_origins']['report:review'][0]['reference'] = OLD
        self.workflow.commit(run['run_id'], review)
        self.assertEqual([o['reference'] for o in self.saved()[2]['origins']], [OLD])

    def saved(self):
        db = self.store.read()
        task = db['tasks'][0]
        return db, task, db.get('dashboard', {}).get('notification_origins', {}).get(task['id'])

    def test_exact_commit_atomic_backup_and_replay(self):
        before = self.store.path.read_bytes()
        run, manifest, review = self.prepare()
        result = self.workflow.commit(run['run_id'], review)
        db, task, record = self.saved()
        self.assertIsNotNone(record, 'explicit reviewed exact provenance must commit')
        self.assertEqual(record, {'snapshot': self.module.notification_snapshot(task), 'run_id': run['run_id'],
                                 'manifest_hash': review['manifest_hash'], 'origins': [
                                     {'reference': GMAIL, 'role': 'trigger', 'item_id': manifest['items'][0]['id'],
                                      'item_hash': manifest['items'][0]['hash']}]})
        self.assertNotIn('notification_origins', task)
        backups = list((self.root/'backups').glob('*.bak'))
        self.assertEqual(len(backups), 1)
        self.assertEqual(backups[0].read_bytes(), before)
        after = self.store.path.read_bytes()
        self.assertEqual(self.workflow.commit(run['run_id'], review), result)
        self.assertEqual(self.store.path.read_bytes(), after)


if __name__ == '__main__':
    unittest.main()
