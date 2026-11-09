"""Host test helpers must work without a NAS-specific scratch directory."""
import ast
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

class TempPortabilityTests(unittest.TestCase):
    def test_helpers_use_platform_temp_discovery_when_tmpdir_is_unset(self):
        folder = Path(__file__).resolve().parent
        helpers = ['test_details_v3.py', 'test_glass_renderer.py',
                   'test_search_draft_callback.py', 'run_conversation_review_regressions.py']
        with tempfile.TemporaryDirectory(prefix='portable-temp-') as sandbox:
            clean_env = {key: value for key, value in os.environ.items() if key != 'TMPDIR'}
            with patch.dict(os.environ, clean_env, clear=True), patch.object(tempfile, 'tempdir', sandbox):
                for name in helpers:
                    tree = ast.parse((folder / name).read_text())
                    calls = [n for n in ast.walk(tree) if isinstance(n, ast.Call)
                             and isinstance(n.func, ast.Attribute) and n.func.attr == 'TemporaryDirectory']
                    self.assertTrue(calls, name)
                    for call in calls:
                        with self.subTest(helper=name, line=call.lineno):
                            self.assertFalse(call.args, 'named temporary directory options required')
                            kwargs = {}
                            for option in call.keywords:
                                value = option.value
                                if isinstance(value, ast.Call) and ast.unparse(value.func) == 'os.environ.get':
                                    kwargs[option.arg] = os.environ.get(*(ast.literal_eval(a) for a in value.args))
                                else:
                                    kwargs[option.arg] = ast.literal_eval(value)
                            with tempfile.TemporaryDirectory(**kwargs) as directory:
                                self.assertEqual(Path(directory).parent, Path(sandbox), name)

if __name__ == '__main__':
    unittest.main()
