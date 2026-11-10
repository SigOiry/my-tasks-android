"""Native Window exit resources; actual rendered dismissal is a separate runtime gate."""
import pathlib
import unittest
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'

class DialogExitTests(unittest.TestCase):
    def test_native_exit_is_bounded_and_has_motion_off_gate(self):
        resource = ROOT/'res/anim/sheet_exit.xml'
        self.assertTrue(resource.is_file(), 'Dismissal currently snaps: native window exit animation missing')
        animation = ET.parse(resource).getroot()
        self.assertEqual(animation.tag, 'set')
        self.assertEqual(animation.attrib[ANDROID+'duration'], '240')
        translate = animation.find('translate')
        self.assertIsNotNone(translate)
        assert translate is not None
        self.assertEqual(translate.attrib[ANDROID+'fromYDelta'], '0')
        self.assertEqual(translate.attrib[ANDROID+'toYDelta'], '16dp')
        style = ET.parse(ROOT/'res/values/styles.xml').getroot().find("style[@name='SheetWindowMotion']")
        self.assertIsNotNone(style)
        assert style is not None
        items = {x.attrib['name']:x.text for x in style}
        self.assertEqual(items['android:windowExitAnimation'], '@anim/sheet_exit')
        source=(ROOT/'src/com/simon/mytasks/WorkspaceUi.java').read_text()
        self.assertIn('window.setWindowAnimations(motionEnabled()?R.style.SheetWindowMotion:0)', source)

if __name__ == '__main__':
    unittest.main()
