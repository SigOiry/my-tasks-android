"""Utility route wiring guards; native screenshots/tests verify layout separately."""
from pathlib import Path
import unittest
SRC=Path(__file__).resolve().parents[1]/'src/com/simon/mytasks'
class UtilityRoutes(unittest.TestCase):
 def test_settings_route_keeps_privacy_and_os_controls(self):
  p=SRC/'SettingsUi.java'
  self.assertTrue(p.exists(),'dedicated grouped native Settings route')
  source=p.read_text()
  for real_action in ['a.trackDialog(dialog)','a.updateNotificationState()','a.disconnectAccount()','a.beginPasswordChange()','UpdateManager.get(a).check(true)','ACTION_APP_NOTIFICATION_SETTINGS','reduce_motion','reduce_transparency']:
   self.assertIn(real_action,source)
  self.assertNotIn('new WebView',source)
  self.assertIn('new SettingsUi(this).show()', (SRC/'MainActivity.java').read_text())
if __name__=='__main__':unittest.main()
