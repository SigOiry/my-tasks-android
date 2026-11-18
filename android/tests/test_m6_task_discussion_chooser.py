"""Execute the actual empty-task chooser against a bounded vertical allocation seam.

Dimensions come from the compact, 2x-font Discuss capture. This models the
available-height constraint, not Android font rendering; confirm ink on-device.
"""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

ANDROID = Path(__file__).resolve().parents[1]


class TaskChooserTest(unittest.TestCase):
    def test_natural_action_height_and_route_ownership(self):
        source = (ANDROID / 'src/com/simon/mytasks/ConversationsUi.java').read_text()
        actual = source[source.index(' void buildTask('):source.index(' void emptyTaskMenu(')]
        template = (ANDROID / 'tests/TaskDiscussionChooserHarness.java').read_text()
        jdk = os.environ.get('JAVA_HOME')
        javac = str(Path(jdk) / 'bin/javac') if jdk else shutil.which('javac')
        self.assertTrue(javac, 'JDK required for chooser regression')
        with tempfile.TemporaryDirectory() as td:
            path = Path(td) / 'TaskDiscussionChooserHarness.java'
            path.write_text(template.replace('/* ACTUAL_BUILDER */', actual))
            subprocess.run([javac, '--release', '8', '-d', td, str(path)], check=True)
            subprocess.run([str(Path(javac).resolve().with_name('java')), '-cp', td,
                            'TaskDiscussionChooserHarness'], check=True)
