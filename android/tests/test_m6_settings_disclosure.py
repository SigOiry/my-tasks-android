"""Literal-copy contracts for the bound Settings Privacy and speech notice.

This extracts the message passed by SettingsUi.show()'s actual row callback,
not a duplicate fixture or an arbitrary phrase elsewhere in the file. It is
source/text coverage only: no Android dialog, speech provider, encryption,
email delivery, or lifecycle behavior is executed or certified here.
"""
from pathlib import Path
import json
import re
import unittest


SRC = Path(__file__).resolve().parents[1] / "src/com/simon/mytasks"


class SettingsDisclosureCopy(unittest.TestCase):
    def setUp(self):
        source = (SRC / "SettingsUi.java").read_text(encoding="utf-8")
        # Deliberately narrow to the existing literal-only binding. A move to
        # resources or another callback requires updating this extraction seam.
        show = source.split(" void show(){", 1)[1]
        literal = r'"(?:[^"\\\r\n]|\\.)*"'
        bindings = re.findall(
            r'^\s*row\(\s*connection\s*,\s*"Privacy and speech"\s*,\s*""\s*,'
            r'\s*\(\s*\)\s*->\s*a\.notice\(\s*"Privacy"\s*,\s*('
            + literal + r')\s*\)\s*\)\s*;',
            show,
            re.MULTILINE,
        )
        self.assertEqual(len(bindings), 1, "Expected one bound Settings Privacy notice")
        # These ordinary Java literals use JSON-compatible escapes (e.g. \n).
        self.message = json.loads(bindings[0])

    def test_storage_copy_distinguishes_encryption_from_app_private_state(self):
        # Regression: the old notice wrongly claimed only the session is saved.
        self.assertNotIn("Only your issued session is saved", self.message)
        self.assertIn("Passwords are never saved.", self.message)
        self.assertIn(
            "Your issued session and email draft recovery are encrypted using Android Keystore.",
            self.message,
        )
        self.assertIn(
            "Task cache and conversation state are saved in app-private storage, not encrypted by the app.",
            self.message,
        )

    def test_capability_copy_separates_discussions_from_confirmed_email_sending(self):
        # Regression: denying email sending globally hides the separate workspace.
        self.assertNotIn("Hermès can discuss and draft, not send email", self.message)
        self.assertIn(
            "Hermès discussions support drafting and bounded public research where allowed, not email sending or unrestricted tools.",
            self.message,
        )
        self.assertIn(
            "The separate email workspace sends only after you review the exact reply and explicitly confirm sending.",
            self.message,
        )

    def test_speech_copy_keeps_provider_warning_and_editable_unsent_review(self):
        # Regression: losing provider disclosure or implying transcription sends.
        self.assertIn(
            "You can edit and review the transcript before sending; it is never sent automatically.",
            self.message,
        )
        self.assertIn(
            "Speech uses your chosen recognition app, which may send audio to its provider.",
            self.message,
        )


if __name__ == "__main__":
    unittest.main()
