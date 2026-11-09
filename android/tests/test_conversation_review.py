"""Discoverable entry point for host-only conversation integration regressions."""
from pathlib import Path
import subprocess
import sys
import unittest


class ConversationReviewIntegrationTest(unittest.TestCase):
    def test_production_policy_and_deferred_review_callback(self):
        runner = Path(__file__).with_name('run_conversation_review_regressions.py')
        result = subprocess.run([sys.executable, str(runner)], capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('assertions passed', result.stdout)


if __name__ == '__main__':
    unittest.main()
