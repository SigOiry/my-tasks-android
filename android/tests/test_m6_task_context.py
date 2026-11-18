"""Current TasksUi bindings on JVM host Views; not Android layout/TalkBack proof.

Whole build/refresh/Rows bodies are extracted at execution, never copied formatters.
Activity/network/render/geometry collaborators are explicitly host-only seams.
"""
from pathlib import Path
import hashlib
import os
import shutil
import subprocess
import tempfile
import unittest
from test_tasks_anchor_methods import extract

ANDROID = Path(__file__).resolve().parents[1]
SRC = ANDROID / 'src/com/simon/mytasks'


class TaskContextTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        source = (SRC / 'TasksUi.java').read_text()
        patterns = [r'^ void build\(', r'^ void refresh\(',
                    r'^ private final class Rows ', r'^ private static final class Entry ',
                    r'^ private boolean sameFilters\(', r'^ private boolean deferRefresh\(',
                    r'^ boolean effectsAtRest\(', r'^ void resetAnchor\(',
                    r'^ static final class Anchor ', r'^ void pause\(']
        methods = '\n'.join(extract(source, pattern) for pattern in patterns)
        template = (ANDROID / 'tests/TaskContextHarness.java').read_text()
        javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac:
            raise AssertionError('Set JAVA_HOME or put javac on PATH')
        cls.java = str(Path(javac).resolve().parent / 'java')
        cls.jar = ANDROID / 'build/test-lib/json.jar'
        if not cls.jar.exists():
            raise AssertionError('Run android/test.sh for the verified host JSON jar')
        cls.directory = tempfile.TemporaryDirectory(prefix='m6-task-context-')
        cls.addClassCleanup(cls.directory.cleanup)
        cls.work = Path(cls.directory.name)
        harness = cls.work / 'TaskContextHarness.java'
        deferred = next(line for line in source.splitlines() if line.startswith(' private final Runnable deferredRefresh='))
        harness.write_text(template.replace('// PRODUCTION_METHODS', methods).replace('// PRODUCTION_DEFERRED_REFRESH', deferred))
        for label, data in [('TasksUi', source.encode()), ('extracted methods', methods.encode()),
                            ('harness', harness.read_bytes()), ('PresentationPolicy', (SRC / 'PresentationPolicy.java').read_bytes())]:
            print(label + ' sha256=' + hashlib.sha256(data).hexdigest(), flush=True)
        result = subprocess.run([javac, '--release', '8', '-cp', str(cls.jar), '-d', str(cls.work),
                                 str(SRC / 'Core.java'), str(SRC / 'PresentationPolicy.java'),
                                 str(SRC / 'TaskPresentation.java'), str(harness)], text=True, capture_output=True)
        if result.returncode:
            raise AssertionError('Host harness compilation failed:\n' + result.stdout + result.stderr)

    def run_case(self, name):
        result = subprocess.run([self.java, '-cp', str(self.work) + os.pathsep + str(self.jar),
                                 'com.simon.mytasks.TaskContextHarness', name], text=True, capture_output=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        print(result.stdout.strip())

    def test_context_attached_visible_wrapping_live_region(self):
        self.run_case('attachment')

    def test_context_counts_filters_search_directions_and_read_only(self):
        self.run_case('context')

    def test_online_empty_and_actual_clear_callback(self):
        self.run_case('online-empty')

    def test_offline_snapshot_empty_and_actual_settings_callback(self):
        self.run_case('offline-empty')

    def test_existing_refresh_deferral_retains_context_until_settled(self):
        self.run_case('preservation')

    def test_retained_rebuild_context_with_actual_pause_and_deferred_callback(self):
        self.run_case('retained-rebuild')

    def test_deferred_connection_transitions_keep_counts_and_read_only_truthful(self):
        self.run_case('connection-transitions')

    def test_changed_filters_and_blocked_first_build_preserve_binding_contract(self):
        self.run_case('changed-filter-controls')

    def test_bounded_slice_preserves_policy_rows_anchors_and_deferred_methods(self):
        # Supplemental byte-identity fence against the frozen M6 starting input.
        # Actual policy integration, row binding and deferral execute above.
        for name, expected in {
            'PresentationPolicy.java': 'e42dc9965159bd7aa1a118dc9e1701549e0670002596e2baf9e403dc1e67db24',
            'TaskPresentation.java': '4a1f18d7af5d2d2f775efbac31a0517e92e480b7bd58451cf4d58b85be3d6099',
            'TaskRowView.java': '8242a628974093c6bdf873628a03009c1d034a401a8f8e8cb8acdb17dd17f250',
        }.items():
            self.assertEqual(expected, hashlib.sha256((SRC / name).read_bytes()).hexdigest(), name)
        source = (SRC / 'TasksUi.java').read_text()
        patterns = [r'^ void capture\(', r'^ private void restoreAnchor\(',
                    r'^ void resetAnchor\(', r'^ void save\(', r'^ void restore\(',
                    r'^ private boolean deferRefresh\(', r'^ boolean effectsAtRest\(',
                    r'^ private boolean sameFilters\(', r'^ void acceptOwnChange\(',
                    r'^ void pause\(', r'^ void resume\(', r'^ static final class Anchor ']
        methods = '\n'.join(extract(source, pattern) for pattern in patterns)
        self.assertEqual('963f5f97ea500657aee24332ea2367d65a237f31265b21c7daad4226b7cb7b06',
                         hashlib.sha256(methods.encode()).hexdigest())


if __name__ == '__main__':
    unittest.main(verbosity=2)
