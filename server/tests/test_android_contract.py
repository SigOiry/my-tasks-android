"""Guard the native integration wiring alongside behavioral JVM tests."""
import re
import unittest
from pathlib import Path

SRC = Path(__file__).resolve().parents[2] / 'android/src/com/simon/mytasks'

class AndroidContractTests(unittest.TestCase):
    def test_conversation_chat_uses_durable_server_history_and_strict_admission_body(self):
        activity = (SRC/'MainActivity.java').read_text()
        self.assertIn('new ConversationController(',activity)
        self.assertIn('new ConversationsUi(',activity)
        self.assertNotIn('.callChat(',activity,'v1.3 UI must not send stateless client-selected history')
        state = (SRC/'ConversationState.java').read_text()
        self.assertIn('content.codePointCount(0,content.length())>8000',state)
        self.assertIn('new String[]{"request_id","content","mode","expected_revision"}',state)
        client = (SRC/'ConversationClient.java').read_text()
        self.assertIn('route(id)+"/messages"',client)
        self.assertIn('route(id)+"/requests/"',client)
        api = (SRC/'Api.java').read_text()
        self.assertIn('body.getBytes(StandardCharsets.UTF_8)',api)
        self.assertIn('method.equals("GET")?2:1',api,'unsafe admissions must never be automatically replayed')

    def test_poll_uses_explicit_tracking_and_current_title_fallback(self):
        poll = (SRC/'PollService.java').read_text()
        self.assertIn('Core.eventUnread(',poll)
        self.assertIn('instanceof Boolean',poll)
        self.assertIn('n.optBoolean("unread")?n.optString("headline"',poll)
        self.assertIn('summary.length()==0?task.optString("title"',poll)
        self.assertIn('MarkdownText.render(summary(n))',poll)
        self.assertIn('NotificationProse.markdown(n)',poll)
        self.assertNotIn('updates/read',poll)

    def test_async_conversation_transport_has_bounded_network_timeout(self):
        api = (SRC/'Api.java').read_text()
        timeout = re.search(r'setReadTimeout\((\d+)\)', api)
        self.assertIsNotNone(timeout, 'Keep async HTTP request timeout explicit')
        self.assertGreaterEqual(int(timeout[1]),15000)
        self.assertLessEqual(int(timeout[1]),60000)
        # Provider work is accepted with202 and polled, not held on this socket.
        client = (SRC/'ConversationClient.java').read_text()
        self.assertIn('route(id)+"/requests/"',client)
        self.assertIn('route(id)+"/messages"',client)

    def test_chat_error_copy_does_not_encourage_blind_replay(self):
        activity = (SRC/'MainActivity.java').read_text()
        self.assertNotIn('tap Send to retry',activity)
        self.assertNotIn('up to two minutes',activity)
        controller = (SRC/'ConversationController.java').read_text()
        self.assertIn('state.prepare(',controller)
        self.assertIn('Could not persist request identity. Nothing was sent.',controller)
        self.assertIn('"admission_rejected"',controller)
        self.assertIn('"status","rejected"',controller)
        self.assertIn('will not be automatically resent',controller)
        self.assertIn('Check status or explicitly retry the same request',controller)
        self.assertIn('state.body(id)',controller)
        api = (SRC/'Api.java').read_text()
        self.assertIn('413',api)
        # The server retains compatibility; new native code uses durable chats only.
        self.assertNotIn('/api/chat',api)
