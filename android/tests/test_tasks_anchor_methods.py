"""Execute current TasksUi capture/restore methods with host-only list geometry.
Not Android runtime proof. The ListView seam records actual selection requests;
header geometry is explicit, never inferred from production anchoring policy.
"""
from pathlib import Path
import os
import re
import shutil
import subprocess
import tempfile
import unittest

ANDROID = Path(__file__).resolve().parents[1]
SOURCE = ANDROID / 'src/com/simon/mytasks/TasksUi.java'


def extract(source, signature):
    match = re.search(signature, source, re.M)
    if not match:
        raise AssertionError('Missing production seam: ' + signature)
    start = source.index('{', match.start())
    depth = 0
    for token in re.finditer(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}]', source[start:]):
        if token.group() == '{':
            depth += 1
        elif token.group() == '}':
            depth -= 1
            if depth == 0:
                return source[match.start():start + token.end()]
    raise AssertionError('Unclosed production seam')


class TasksAnchorMethodsTest(unittest.TestCase):
    def test_actual_capture_and_restore_keep_visible_toolbar(self):
        source = SOURCE.read_text()
        methods = '\n'.join(extract(source, pattern) for pattern in [
            r'^ void capture\(', r'^ private void restoreAnchor\(',
            r'^ void resetAnchor\(', r'^ static final class Anchor ',
        ])
        template = (ANDROID / 'tests/TasksAnchorMethodsHarness.java').read_text()
        javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
        if not javac:
            self.fail('Set JAVA_HOME or put javac on PATH')
        java = Path(javac).resolve().parent / 'java'
        with tempfile.TemporaryDirectory(prefix='tasks-anchor-') as directory:
            work = Path(directory)
            harness = work / 'TasksAnchorMethodsHarness.java'
            harness.write_text(template.replace('// PRODUCTION_METHODS', methods))
            subprocess.run([javac, '--release', '8', '-d', str(work), str(harness)], check=True)
            result = subprocess.run([str(java), '-cp', str(work), 'TasksAnchorMethodsHarness'], text=True, capture_output=True)
            self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
            print(result.stdout.strip())


if __name__ == '__main__':
    unittest.main()
