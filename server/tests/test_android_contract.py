"""Guard the native integration wiring alongside behavioral JVM tests."""
import re
import unittest
from pathlib import Path

SRC = Path(__file__).resolve().parents[2] / 'android/src/com/simon/mytasks'

class AndroidContractTests(unittest.TestCase):
    def test_chat_sends_budgeted_body_without_reserialization(self):
        activity = (SRC/'MainActivity.java').read_text()
        self.assertTrue('Core.chatBody(' in activity,'sendChat must budget its complete wire body')
        self.assertTrue('.callChat(body)' in activity,'send the exact budgeted serialization')
        self.assertNotIn('chatDraft.length()>8000',activity,'count Unicode codepoints in the shared helper')
        api = (SRC/'Api.java').read_text()
        self.assertIn('body.getBytes(StandardCharsets.UTF_8)',api)

    def test_poll_uses_explicit_tracking_and_current_title_fallback(self):
        poll = (SRC/'PollService.java').read_text()
        self.assertIn('Core.eventUnread(',poll)
        self.assertIn('instanceof Boolean',poll)
        self.assertIn('n.optBoolean("unread")?n.optString("headline"',poll)
        self.assertIn('summary.isEmpty()?task.optString("title"',poll)
        self.assertNotIn('updates/read',poll)

    def test_chat_read_timeout_covers_server_contract(self):
        api = (SRC/'Api.java').read_text()
        timeout = re.search(r'path.equals\("/api/chat"\)\?(\d+):', api)
        self.assertIsNotNone(timeout, 'Keep chat timeout explicit in HttpURLConnection wiring')
        self.assertGreaterEqual(int(timeout[1]),180000)

    def test_chat_error_copy_does_not_encourage_blind_replay(self):
        activity = (SRC/'MainActivity.java').read_text()
        self.assertNotIn('tap Send to retry',activity)
        self.assertNotIn('up to two minutes',activity)
        self.assertTrue('if(body.equals(rejectedChatBody))' in activity,'HTTP 413 must block identical payload resubmission')
        self.assertIn('rejectedChatBody=body',activity)
        self.assertIn('Core.chatFailure(',activity)
        api = (SRC/'Api.java').read_text()
        self.assertIn('413',api)
        self.assertIn('request("POST","/api/chat",body)',api)
