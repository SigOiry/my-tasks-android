"""Tests for the verifier, not copies of either client's policy."""
import importlib.util
import json
from pathlib import Path
import unittest

HERE = Path(__file__).resolve().parent

class RunnerTests(unittest.TestCase):
    def load_runner(self):
        self.assertTrue((HERE / 'verify.py').is_file(), 'portable verifier has not been implemented')
        spec = importlib.util.spec_from_file_location('presentation_verify', HERE / 'verify.py')
        assert spec is not None and spec.loader is not None
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_checks_actual_results_against_pinned_expected(self):
        runner = self.load_runner()
        self.assertEqual(runner.differences({'palette': {'unicode': 3}}, {'palette': {'unicode': 3}}), [])
        self.assertTrue(runner.differences({'palette': {'unicode': 3}}, {'palette': {'unicode': 4}}))
        self.assertTrue(runner.differences({'palette': {'unicode': 3}}, {'palette': {}}))
        self.assertTrue(runner.differences({'palette': {}}, {'palette': {}, 'unexpected': 1}))

    def test_independent_oracle_checks_every_fixture(self):
        runner = self.load_runner()
        self.assertTrue(hasattr(runner, 'oracle'), 'independent oracle not implemented')
        vectors = json.loads((HERE / 'golden.json').read_text())
        self.assertEqual(runner.differences(runner.expected(vectors), runner.oracle(vectors)), [])

    def test_real_web_adapter_returns_every_case(self):
        runner = self.load_runner()
        self.assertTrue(hasattr(runner, 'run_web'), 'real web adapter not implemented')
        vectors = json.loads((HERE / 'golden.json').read_text())
        output = runner.run_web(HERE / 'golden.json', HERE.parent.parent / 'server/web/policy.mjs')
        for section, cases in runner.expected(vectors).items():
            self.assertEqual(set(output[section]), set(cases))
        self.assertEqual(output['palette'], runner.expected(vectors)['palette'])

    def test_real_android_adapter_returns_every_case(self):
        runner = self.load_runner()
        self.assertTrue(hasattr(runner, 'run_android'), 'real Android adapter not implemented')
        import os
        if not os.environ.get('JSON_JAR'):
            self.skipTest('Set JSON_JAR to a real JVM org.json library to exercise Android')
        output = runner.run_android(HERE / 'golden.json', Path(os.environ['JSON_JAR']), os.environ.get('JAVA_HOME'))
        vectors = json.loads((HERE / 'golden.json').read_text())
        for section, cases in runner.expected(vectors).items():
            self.assertEqual(set(output[section]), set(cases))
        self.assertEqual(output['palette'], runner.expected(vectors)['palette'])

    def test_fixture_case_names_unique_and_all_palette_slots_covered(self):
        vectors = json.loads((HERE / 'golden.json').read_text())
        for section in ('palette', 'scores', 'orders'):
            names = [case['name'] for case in vectors[section]]
            self.assertEqual(len(names), len(set(names)), section)
        self.assertEqual({case['slot'] for case in vectors['palette']}, {-1, 0, 1, 2, 3, 4, 5})

    def test_corrupt_expected_score_is_rejected_before_any_client(self):
        runner = self.load_runner()
        import contextlib
        import io
        import tempfile
        vectors = json.loads((HERE / 'golden.json').read_text())
        vectors['scores'][0]['expected'] = 999
        with tempfile.TemporaryDirectory(dir=HERE) as folder:
            path = Path(folder) / 'corrupt.json'
            path.write_text(json.dumps(vectors))
            with contextlib.redirect_stdout(io.StringIO()):
                self.assertEqual(runner.main(['--client', 'oracle', '--fixture', str(path)]), 2)

    def test_missing_android_dependency_is_failure_not_skip(self):
        runner = self.load_runner()
        import contextlib
        import io
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertEqual(runner.main(['--client', 'android', '--json-jar', str(HERE / 'does-not-exist.jar')]), 1)

if __name__ == '__main__':
    unittest.main()
