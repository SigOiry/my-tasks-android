"""Native wiring invariants complement executable JVM policy/transport tests.
These are not Android Keystore, pixel, lifecycle or notification runtime tests.
"""
from pathlib import Path
import re
import unittest
ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'src/com/simon/mytasks'

class NativeAccountWiring(unittest.TestCase):
    def test_original_keystore_identity_and_origin_aad_are_unchanged(self):
        source = (SRC / 'Store.java').read_text()
        self.assertIn('ALIAS="mytasks.token.v1"', source)
        self.assertEqual(source.count('cipher.updateAAD(origin.getBytes(StandardCharsets.UTF_8))'), 2)
        self.assertNotIn('deleteEntry', source)

    def test_only_validated_issued_session_is_passed_to_secure_storage(self):
        store = (SRC / 'Store.java').read_text()
        ui = (SRC / 'AuthUi.java').read_text()
        self.assertIn('configure(origin,AuthPolicy.sessionToken(issued))', store)
        self.assertIn('store.session(origin,result.getString("session_token"))', ui)
        self.assertNotIn('store.configure(', ui)
        for source in (store, ui):
            self.assertNotRegex(source, r'putString\("(?:password|current_password|new_password|username)"')

    def test_no_manual_token_input_and_password_fields_are_not_saved(self):
        main = (SRC / 'MainActivity.java').read_text()
        ui = (SRC / 'AuthUi.java').read_text()
        self.assertNotIn('Server token', main)
        self.assertNotIn('Enter server token', main)
        self.assertIn('e.setSaveEnabled(false)', ui)
        self.assertIn('IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS', ui)
        self.assertIn('current.setText("")', ui)

    def test_mandatory_gate_precedes_private_workspace(self):
        source = (SRC / 'MainActivity.java').read_text()
        self.assertLess(source.index('new AuthUi(this).build(root)'), source.index('heading(destination,20)'))
        create = source[source.index('@Override public void onCreate'):source.index('@Override protected void onResume')]
        self.assertNotIn('store.cached()', create)
        self.assertIn('new ConversationController(store,null)', create)

    def test_private_dialogs_are_registered_and_dismissed_at_auth_boundary(self):
        source = (SRC / 'MainActivity.java').read_text()
        chats = (SRC / 'ConversationsUi.java').read_text()
        self.assertEqual(source.count('new AlertDialog.Builder(this)'), 1)
        self.assertNotIn('new AlertDialog.Builder(a)', chats)
        self.assertIn('trackDialog(picker).show()', source)
        block = source[source.index('void blockSession'):source.index('void maybeSummary')]
        self.assertIn('dismissPrivateDialogs()', block)
        self.assertIn('surfaces.invalidate()', source)
        self.assertIn('surfaces.current(generation)', source)
        self.assertIn('store.beginLogout()', source)
        self.assertIn('store.invalidateWork(account)', source)
        self.assertIn('root.removeAllViews()', source)

    def test_post_login_storage_diagnostic_does_not_use_legacy_network_claim(self):
        source = (SRC / 'AuthUi.java').read_text()
        self.assertIn('AuthPolicy.storageFailure(e)', source)
        self.assertNotIn('Core.credentialSaveFailure', source)
        self.assertNotIn('No token was sent', source)

    def test_notification_uses_shared_spanned_prose_and_no_source_actions(self):
        source = (SRC / 'PollService.java').read_text()
        self.assertIn('MarkdownText.render(summary(n))', source)
        self.assertIn('bigText(summary)', source)
        self.assertIn('NotificationProse.markdown(n)', source)
        self.assertIn('setAllowSystemGeneratedContextualActions(false)', source)
        native = (SRC / 'MainActivity.java').read_text()
        self.assertIn('markRead(id,token)', native)
        self.assertIn('void markRead(String id,String token)', native)
        self.assertNotIn('.addAction(', source)
        self.assertNotIn('sourceLinks(', source)

    def test_rendering_has_no_html_or_link_engines(self):
        source = (SRC / 'MarkdownText.java').read_text()
        self.assertNotIn('Html.fromHtml', source)
        self.assertNotIn('new URLSpan', source)
        self.assertNotIn('new ImageSpan', source)
        self.assertIn('new StyleSpan', source)
        self.assertIn('new TypefaceSpan', source)

    def test_inter_font_and_license_are_packaged(self):
        self.assertTrue((ROOT / 'res/font/inter_variable.ttf').is_file())
        self.assertIn('@font/inter_variable', (ROOT / 'res/values/styles.xml').read_text())
        self.assertIn('getFont(R.font.inter_variable)', (SRC / 'WorkspaceUi.java').read_text())
        self.assertIn('SIL OPEN FONT LICENSE', (ROOT / 'res/raw/inter_license.txt').read_text())

    def test_reduced_preferences_and_system_motion_are_consulted(self):
        source = (SRC / 'WorkspaceUi.java').read_text()
        for value in ('reduce_motion', 'reduce_transparency', 'areAnimatorsEnabled', 'TRANSITION_ANIMATION_SCALE', 'high_text_contrast_enabled'):
            self.assertIn(value, source)

if __name__ == '__main__':
    unittest.main()
