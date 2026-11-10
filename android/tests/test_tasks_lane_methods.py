"""Execute actual TasksUi.refresh on JVM; UI/geometry are host seams, not device proof.
Android-only pinned headers/order; deliberately does not read shared web fixtures.
"""
from pathlib import Path
import os
import shutil
import subprocess
import sys
import tempfile
import unittest
from test_tasks_anchor_methods import extract

ANDROID = Path(__file__).resolve().parents[1]


class TasksLaneMethodsTest(unittest.TestCase):
    def test_actual_refresh_labels_lanes_and_preserves_project_groups(self):
        source = (ANDROID / 'src/com/simon/mytasks/TasksUi.java').read_text()
        methods = extract(source, r'^ void refresh\(')
        entry = extract(source, r'^ private static final class Entry ')
        template = (ANDROID / 'tests/TasksLaneMethodsHarness.java').read_text()
        javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac:
            self.fail('Set JAVA_HOME or put javac on PATH')
        jar = ANDROID / 'build/test-lib/json.jar'
        self.assertTrue(jar.exists(), 'Run android/test.sh for the verified host JSON jar')
        with tempfile.TemporaryDirectory(prefix='tasks-lanes-') as directory:
            work = Path(directory)
            harness = work / 'TasksLaneMethodsHarness.java'
            harness.write_text(template.replace('// PRODUCTION_METHODS', methods + '\n' + entry))
            subprocess.run([javac, '--release', '8', '-cp', str(jar), '-d', str(work),
                            str(ANDROID / 'src/com/simon/mytasks/Core.java'),
                            str(ANDROID / 'src/com/simon/mytasks/PresentationPolicy.java'), str(harness)], check=True)
            result = subprocess.run([str(Path(javac).resolve().parent / 'java'), '-cp', str(work) + os.pathsep + str(jar),
                                     'com.simon.mytasks.TasksLaneMethodsHarness'], text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            print(result.stdout.strip())

    def test_refresh_harness_uses_standard_temp_discovery_without_tmpdir(self):
        # Exercise the real JVM harness, not a substituted presentation policy.
        with tempfile.TemporaryDirectory(prefix='portable-lanes-') as portable:
            env = dict(os.environ)
            env.pop('TMPDIR', None)
            script = '''import pathlib, sys, tempfile, unittest
root = pathlib.Path(sys.argv[1])
tempfile.tempdir = str(root)
original = tempfile.TemporaryDirectory
created = []
class AuditedTemporaryDirectory(original):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        created.append(pathlib.Path(self.name).parent)
tempfile.TemporaryDirectory = AuditedTemporaryDirectory
sys.path.insert(0, sys.argv[2])
from test_tasks_lane_methods import TasksLaneMethodsTest
result = unittest.TextTestRunner().run(unittest.TestSuite([
    TasksLaneMethodsTest('test_actual_refresh_labels_lanes_and_preserves_project_groups')]))
assert result.wasSuccessful(), 'Actual JVM lane harness failed'
assert created and all(p == root for p in created), 'Harness bypassed standard temporary-directory discovery'
'''
            result = subprocess.run([sys.executable, '-c', script, portable, str(ANDROID/'tests')],
                                    env=env, text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)


if __name__ == '__main__':
    unittest.main()
