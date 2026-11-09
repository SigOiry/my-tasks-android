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

    def test_compact_native_composer_and_diff_rows(self):
        source = SOURCE.read_text()
        composer = source[source.index('LinearLayout composer='):source.index('void composerMenu()')]
        self.assertIn('a.ui.glassSurface()', composer)
        self.assertIn('input.setBackgroundColor(android.graphics.Color.TRANSPARENT)', composer)
        self.assertIn('TYPE_TEXT_FLAG_MULTI_LINE', composer)
        self.assertIn('ic_chat_send_up', composer)
        self.assertNotIn('composer.setBackground(a.ui.glass())', composer)
        self.assertIn('binding.equals(rowBindings.get(id))', source)
        self.assertIn('ChatRowPolicy.animate(own,bottom,first,prepended,a.ui.motionEnabled())', source)
        self.assertIn('setDuration(180)', source)
        self.assertNotIn('outgoing?"You":"Hermès"', source)

    def test_global_entry_does_not_retain_task_overflow_binding(self):
        source = SOURCE.read_text()
        global_route = source[source.index('void buildGlobal('):source.index('LinearLayout masterWith(')]
        self.assertIn('boundTask="";', global_route)

    def test_task_thread_owns_header_and_picker_is_not_permanent(self):
        source = SOURCE.read_text()
        task = source[source.index('void buildTask('):source.index('void taskPicker(')]
        self.assertNotIn('LinearLayout picker=', task)
        self.assertIn('if(!id.isEmpty()){buildThread(pane,id,true);return;}', task)
        self.assertIn('Choose saved conversation', task)
        # Creation is only inside explicit click/menu callbacks, not entering a task.
        self.assertIn('()->c.create(task,', task)
        self.assertNotIn('a.chatUtilityChrome.add', task)

    def test_search_and_capability_scope_are_disclosed(self):
        source = SOURCE.read_text()
        for token in ('New conversation', 'loaded titles', 'not message history',
                      'cannot create tasks or projects', 'updated_at'):
            self.assertIn(token, source)


if __name__ == '__main__':
    unittest.main()
