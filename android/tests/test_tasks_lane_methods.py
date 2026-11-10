"""Execute actual TasksUi.refresh on JVM; UI/geometry are host seams, not device proof.
Android-only pinned headers/order; deliberately does not read shared web fixtures.
"""
from pathlib import Path
import os
import shutil
import subprocess
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
        scratch = Path(os.environ.get('TMPDIR', '/opt/data/cache/scratch'))
        with tempfile.TemporaryDirectory(prefix='tasks-lanes-', dir=scratch) as directory:
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


if __name__ == '__main__':
    unittest.main()
