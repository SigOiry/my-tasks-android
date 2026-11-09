#!/usr/bin/env python3
"""Execute the actual TasksUi search callback on a draft/render host seam."""
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = Path(os.environ.get('JAVA_HOME', '/opt/data/cache/android-build/jdk-17.0.2')) / 'bin'


class SearchDraftCallbackTest(unittest.TestCase):
    def test_search_toggle_preserves_unsaved_editor_input(self):
        source = (ROOT / 'android/src/com/simon/mytasks/TasksUi.java').read_text()
        match = re.search(r'"Search tasks",\(\)->\{([^}]+)\}', source)
        assert match is not None, 'Search tasks callback missing'
        callback = match.group(1)
        program = '''public class SearchDraftCallbackHarness {
 static class Activity {
  boolean showSearch;
  String saved="saved title", editor="unsaved edited title", draft=saved;
  void captureDraft(){draft=editor;}
  void render(){editor=draft;}
 }
 public static void main(String[] args){
  Activity a=new Activity();
  Runnable search=()->{CALLBACK};
  search.run();
  if(!a.showSearch || !a.editor.equals("unsaved edited title"))
   throw new AssertionError("Opening search lost unsaved editor input: "+a.editor);
  a.editor="second unsaved edit";
  search.run();
  if(a.showSearch || !a.editor.equals("second unsaved edit") || !a.saved.equals("saved title"))
   throw new AssertionError("Closing search lost draft or saved it canonically");
 }
}'''.replace('CALLBACK', callback)
        with tempfile.TemporaryDirectory(prefix='search-draft-') as directory:
            path = Path(directory) / 'SearchDraftCallbackHarness.java'
            path.write_text(program)
            subprocess.run([str(JAVA / 'javac'), '--release', '8', str(path)], check=True)
            result = subprocess.run([str(JAVA / 'java'), '-cp', directory, path.stem], capture_output=True, text=True)
            self.assertEqual(result.returncode, 0, result.stderr)


if __name__ == '__main__':
    unittest.main()
