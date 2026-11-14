"""Delayed actual citation callbacks with synthetic transport and host UI seams."""
import os
import pathlib
import re
import shutil
import subprocess
import tempfile
import unittest
from test_inline_citations import method, ROOT


class CitationEmailLifecycleTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        javac = str(pathlib.Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac:
            raise AssertionError('Set JAVA_HOME or put javac on PATH')
        cls.tmp = tempfile.TemporaryDirectory(prefix='citation-email-lifecycle-')
        cls.addClassCleanup(cls.tmp.cleanup)
        p = pathlib.Path(cls.tmp.name)
        source = (ROOT / 'src/com/simon/mytasks/CitationUi.java').read_text()
        fields = '\n'.join(re.findall(r'^ static final java\.util\.WeakHashMap[^\n]+', source, re.M))
        template = (ROOT / 'tests/CitationEmailLifecycleHarness.java').read_text()
        actual = '\n'.join(method('CitationUi.java', n) for n in ['panel', 'showDetail', 'taskSource'])
        harness = p / 'CitationEmailLifecycleHarness.java'
        harness.write_text(template.replace('// ACTUAL_METHODS', actual).replace('// ACTUAL_FIELDS', fields).replace('// ACTUAL_HIDE', method('ConversationController.java', 'hide')).replace('// ACTUAL_NAVIGATE', method('MainActivity.java', 'navigate')))
        jar = ROOT / 'build/test-lib/json.jar'
        if not jar.exists():
            raise AssertionError('Run android/test.sh to bootstrap build/test-lib/json.jar')
        files = [ROOT / 'src/com/simon/mytasks' / (n + '.java') for n in ['EmailReplyClient', 'ConversationClient', 'ConversationState', 'Core']]
        subprocess.run([javac, '--release', '8', '-cp', str(jar), '-d', str(p), str(harness), *map(str, files)], check=True)
        cls.command = [str(pathlib.Path(javac).parent / 'java'), '-cp', str(p) + os.pathsep + str(jar), 'com.simon.mytasks.CitationEmailLifecycleHarness']

    def scenario(self, name):
        subprocess.run(self.command + [name], check=True)

    def test_task_leave_success(self):
        self.scenario('task-leave-success')

    def test_delayed_navigation_completions(self):
        for transition in ['task-leave', 'task-switch', 'thread-switch', 'thread-leave', 'listener-replace', 'leave-return', 'account', 'private']:
            for result in ['success', 'error']:
                with self.subTest(transition=transition, result=result):
                    self.scenario(transition + '-' + result)

    def test_same_view_positive_controls(self):
        self.scenario('same-view-success')
        self.scenario('same-view-error')

    def test_new_source_request_supersedes_old_completion(self):
        self.scenario('superseded-success')

    def test_new_source_request_supersedes_old_error(self):
        self.scenario('superseded-error')

    def test_new_web_source_supersedes_email_completion(self):
        self.scenario('web-replaced-success')
        self.scenario('web-replaced-error')
