"""Source integration guards; JVM policy tests exercise actual behavior.
These do not claim Android rendering or touch/IME verification.
"""
import pathlib
import unittest

SOURCE = pathlib.Path(__file__).resolve().parents[1] / 'src/com/simon/mytasks/ConversationsUi.java'


class ChatPresentationWiringTest(unittest.TestCase):
    def test_history_rebuild_is_message_content_only(self):
        source = SOURCE.read_text()
        self.assertIn('ChatPresentationPolicy.messageSignature(turns)', source)
        self.assertIn('if(historyChanged)', source)
        self.assertNotIn('messages.animate()', source)
        self.assertNotIn('messages.setAlpha', source)

    def test_viewport_uses_message_anchor_and_thread_guard(self):
        source = SOURCE.read_text()
        for token in ('anchor_id', 'anchor_offset', 'ChatPresentationPolicy.restoreY(',
                      'ChatPresentationPolicy.shouldFollow(', 'scroll!=target',
                      'ChatPresentationPolicy.countAfter('):
            self.assertIn(token, source)

    def test_bubbles_use_real_dates_grouping_and_copy_original(self):
        source = SOURCE.read_text()
        for token in ('ChatPresentationPolicy.grouped(', 'created_at', 'Copy original',
                      'setCustomSelectionActionModeCallback', 'Gravity.END', 'Gravity.START'):
            self.assertIn(token, source)

    def test_resize_preserves_reader_or_follows_prior_bottom(self):
        source = SOURCE.read_text()
        self.assertIn('onSizeChanged(int width,int height,int oldWidth,int oldHeight)', source)
        self.assertIn('oldHeight,a.dp(72)', source)
        self.assertIn('restoreViewport(wasBottom,anchor)', source)

    def test_search_and_capability_scope_are_disclosed(self):
        source = SOURCE.read_text()
        for token in ('New conversation', 'loaded titles', 'not message history',
                      'cannot create tasks or projects', 'updated_at'):
            self.assertIn(token, source)


if __name__ == '__main__':
    unittest.main()
